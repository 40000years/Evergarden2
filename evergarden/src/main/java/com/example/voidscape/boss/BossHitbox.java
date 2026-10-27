package com.example.voidscape.boss;

import com.google.gson.*;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.joml.Matrix4d;
import org.joml.Vector3d;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Body volumes evaluated from the same Blockbench bones as both client rigs. */
final class BossHitbox {
    private record Bone(String id,String name,double[] pivot,List<Bone> children,BoundingBox body,BoundingBox weapon) {}
    private record Key(double time,double[] value) {}
    private record Track(List<Key> rotation,List<Key> position) {}
    private record Clip(double length,boolean loop,Map<String,Track> tracks) {}
    private record Model(List<Bone> roots,Map<String,Clip> clips,boolean flipPositionX) {}
    private static final Map<String,Model> MODELS=new HashMap<>();
    private static final double AIM_PADDING=.25;
    private record Attack(Player player,Entity receiver) {}
    private static Attack fallback;
    private static final Set<String> BODY=Set.of("pelvis","chest","head","arm_r","arm_l","forearm_r","forearm_l",
        "neck","jaw","collar","shoulder_r","shoulder_l","sash_r","sash_l","hand_r","hand_l","leg_r","leg_l","shin_r","shin_l","foot_r","foot_l",
        "sleeve_r","sleeve_l","tabard","tabard_tip","skirt_front","skirt_front_tip","skirt_r","skirt_r_tip","skirt_back","skirt_back_tip","skirt_l","skirt_l_tip",
        "skirt_tip_r","skirt_tip_l","sash_tip_r","sash_tip_l");
    private static double[] xyz(JsonArray a){return new double[]{a.get(0).getAsDouble(),a.get(1).getAsDouble(),a.get(2).getAsDouble()};}
    private static Bone bone(JsonObject g,Map<String,JsonObject> cubes){
        List<Bone> children=new ArrayList<>();BoundingBox bounds=null;String name=g.get("name").getAsString();
        for(JsonElement child:g.getAsJsonArray("children")){
            if(child.isJsonObject()){children.add(bone(child.getAsJsonObject(),cubes));continue;}
            if(!BODY.contains(name)&&!name.equals("sword"))continue;
            JsonObject cube=cubes.get(child.getAsString());double[] a=xyz(cube.getAsJsonArray("from")),b=xyz(cube.getAsJsonArray("to"));
            Matrix4d rotation=new Matrix4d();
            if(cube.has("rotation")){
                double[] p=cube.has("origin")?xyz(cube.getAsJsonArray("origin")):new double[3],r=xyz(cube.getAsJsonArray("rotation"));
                rotation.translate(p[0],p[1],p[2]).rotateZ(Math.toRadians(r[2])).rotateY(Math.toRadians(r[1])).rotateX(Math.toRadians(r[0])).translate(-p[0],-p[1],-p[2]);
            }
            for(double x:new double[]{a[0],b[0]})for(double y:new double[]{a[1],b[1]})for(double z:new double[]{a[2],b[2]}){
                Vector3d p=rotation.transformPosition(new Vector3d(x,y,z));BoundingBox point=new BoundingBox(p.x,p.y,p.z,p.x,p.y,p.z);
                bounds=bounds==null?point:bounds.union(point);
            }
        }
        return new Bone(g.get("uuid").getAsString(),name,xyz(g.getAsJsonArray("origin")),children,BODY.contains(name)?bounds:null,name.equals("sword")?bounds:null);
    }
    private static Model model(String name){return MODELS.computeIfAbsent(name,key->{
        String path="/modelengine/"+(key.equals("arkhaia")||key.equals("seraphon")?key:"rivers/"+key)+".bbmodel";
        try(var input=BossHitbox.class.getResourceAsStream(path)){
            if(input==null)throw new IOException(path);
            JsonObject json=JsonParser.parseReader(new InputStreamReader(input,StandardCharsets.UTF_8)).getAsJsonObject();
            Map<String,JsonObject> cubes=new HashMap<>();Map<String,Clip> clips=new HashMap<>();
            json.getAsJsonArray("elements").forEach(e->cubes.put(e.getAsJsonObject().get("uuid").getAsString(),e.getAsJsonObject()));
            json.getAsJsonArray("animations").forEach(e->{
                JsonObject clip=e.getAsJsonObject();Map<String,Track> tracks=new HashMap<>();
                for(var animator:clip.getAsJsonObject("animators").entrySet()){
                    List<Key> rotation=new ArrayList<>(),position=new ArrayList<>();
                    for(JsonElement entry:animator.getValue().getAsJsonObject().getAsJsonArray("keyframes")){
                        JsonObject frame=entry.getAsJsonObject();String channel=frame.get("channel").getAsString();
                        if(!channel.equals("rotation")&&!channel.equals("position"))continue;
                        JsonObject point=frame.getAsJsonArray("data_points").get(0).getAsJsonObject();
                        double[] value={point.get("x").getAsDouble(),point.get("y").getAsDouble(),point.get("z").getAsDouble()};
                        (channel.equals("rotation")?rotation:position).add(new Key(frame.get("time").getAsDouble(),value));
                    }
                    rotation.sort(Comparator.comparingDouble(Key::time));position.sort(Comparator.comparingDouble(Key::time));
                    tracks.put(animator.getKey(),new Track(rotation,position));
                }
                clips.put(clip.get("name").getAsString(),new Clip(clip.get("length").getAsDouble(),clip.has("loop")&&clip.get("loop").getAsString().equals("loop"),tracks));
            });
            List<Bone> roots=new ArrayList<>();json.getAsJsonArray("outliner").forEach(e->{if(!e.getAsJsonObject().get("name").getAsString().equals("hitbox"))roots.add(bone(e.getAsJsonObject(),cubes));});
            boolean flip=json.has("meta")&&json.getAsJsonObject("meta").has("evergarden_animation_position_x");
            return new Model(roots,clips,flip);
        }catch(IOException error){throw new IllegalStateException("Missing boss body geometry",error);}
    });}
    private static double[] sample(List<Key> keys,double time){
        if(keys==null||keys.isEmpty())return new double[3];int low=0,high=keys.size()-1;
        while(low<high){int mid=(low+high)/2;if(keys.get(mid).time()<time)low=mid+1;else high=mid;}
        Key right=keys.get(low),left=keys.get(Math.max(0,low-1));double f=right.time()>left.time()?Math.clamp((time-left.time())/(right.time()-left.time()),0,1):0;
        double[] value=new double[3];for(int i=0;i<3;i++)value[i]=left.value()[i]*(1-f)+right.value()[i]*f;
        return value;
    }
    private static Matrix4d transform(Bone bone,Matrix4d parent,Map<String,Track> animators,double time,boolean flip){
        Track a=animators.get(bone.id());double[] r=sample(a==null?null:a.rotation(),time),p=sample(a==null?null:a.position(),time),o=bone.pivot();
        return new Matrix4d(parent).translate(o[0]+(flip?-p[0]:p[0]),o[1]+p[1],o[2]+p[2])
            .rotateZ(Math.toRadians(r[2])).rotateY(Math.toRadians(-r[1])).rotateX(Math.toRadians(-r[0])).translate(-o[0],-o[1],-o[2]);
    }
    private static void visit(Bone bone,Matrix4d parent,Map<String,Track> animators,double time,boolean flip,boolean weapon,List<Map.Entry<BoundingBox,Matrix4d>> result){
        Matrix4d matrix=transform(bone,parent,animators,time,flip);
        BoundingBox bounds=weapon?bone.weapon():bone.body();if(bounds!=null)result.add(Map.entry(bounds,matrix));
        bone.children().forEach(child->visit(child,matrix,animators,time,flip,weapon,result));
    }
    private static List<Map.Entry<BoundingBox,Matrix4d>> boxes(String name,String clip,double time,Location at,double scale){
        return boxes(name,clip,time,at,scale,false);
    }
    private static List<Map.Entry<BoundingBox,Matrix4d>> boxes(String name,String clip,double time,Location at,double scale,boolean weapon){
        Model model=model(name);Clip animation=model.clips().getOrDefault(clip,model.clips().get("idle"));
        double length=animation.length();time=animation.loop()&&length>0?Math.max(0,time)%length:Math.clamp(time,0,length);
        Matrix4d world=new Matrix4d().translate(at.getX(),at.getY(),at.getZ()).rotateY(-Math.toRadians(at.getYaw())).scale(-scale/16,scale/16,-scale/16);
        List<Map.Entry<BoundingBox,Matrix4d>> result=new ArrayList<>();
        for(Bone root:model.roots())visit(root,world,animation.tracks(),time,model.flipPositionX(),weapon,result);
        return result;
    }
    static Map<String,Location> bonePoints(String name,String clip,double time,Location at,double scale,Set<String> names){
        Model model=model(name);Clip animation=model.clips().getOrDefault(clip,model.clips().get("idle"));Map<String,Location> points=new HashMap<>();
        Matrix4d world=new Matrix4d().translate(at.getX(),at.getY(),at.getZ()).rotateY(-Math.toRadians(at.getYaw())).scale(-scale/16,scale/16,-scale/16);
        double length=animation.length();time=animation.loop()&&length>0?Math.floorMod((long)Math.floor(time*1000),(long)Math.max(1,Math.round(length*1000)))/1000.0:Math.clamp(time,0,length);
        for(Bone root:model.roots())points(root,world,animation.tracks(),time,model.flipPositionX(),at.getWorld(),names,points);
        return points;
    }
    private static void points(Bone bone,Matrix4d parent,Map<String,Track> tracks,double time,boolean flip,World world,Set<String> names,Map<String,Location> result){
        Matrix4d matrix=transform(bone,parent,tracks,time,flip);
        if(names.contains(bone.name())){
            double[] o=bone.pivot();Vector3d p=matrix.transformPosition(new Vector3d(o[0],o[1],o[2]));result.put(bone.name(),new Location(world,p.x,p.y,p.z));
        }
        for(Bone child:bone.children())points(child,matrix,tracks,time,flip,world,names,result);
    }
    static double ray(Player player,String name,String clip,double time,Location at,double scale,double reach){
        Vector eye=player.getEyeLocation().toVector(),direction=player.getEyeLocation().getDirection();double nearest=Double.POSITIVE_INFINITY;
        var wall=player.getWorld().rayTraceBlocks(player.getEyeLocation(),direction,reach,FluidCollisionMode.NEVER,true);
        if(wall!=null)reach=Math.min(reach,eye.distance(wall.getHitPosition()));
        for(var box:boxes(name,clip,time,at,scale)){
            Matrix4d inverse=new Matrix4d(box.getValue()).invert();Vector3d a=inverse.transformPosition(new Vector3d(eye.getX(),eye.getY(),eye.getZ()));
            Vector3d b=inverse.transformDirection(new Vector3d(direction.getX(),direction.getY(),direction.getZ()));double ratio=b.length();
            BoundingBox bounds=box.getKey().clone().expand(AIM_PADDING*16/scale);
            if(bounds.contains(a.x,a.y,a.z))return 0;
            var hit=bounds.rayTrace(new Vector(a.x,a.y,a.z),new Vector(b.x,b.y,b.z),reach*ratio);
            if(hit!=null)nearest=Math.min(nearest,new Vector(a.x,a.y,a.z).distance(hit.getHitPosition())/ratio);
        }
        return nearest;
    }
    static boolean melee(Player player,String name,String clip,double time,Location at,double scale,boolean sweep){
        return sweep?reachable(player,name,clip,time,at,scale)&&player.hasLineOfSight(at):
            Double.isFinite(ray(player,name,clip,time,at,scale,reach(player)));
    }
    static double reach(Player player){return player.getAttribute(Attribute.ENTITY_INTERACTION_RANGE).getValue()+.15;}
    static boolean confirmed(Player player,Entity receiver){return fallback!=null&&fallback.player()==player&&fallback.receiver()==receiver;}
    /** Dispatch a body selection made at swing time, before the next pose/look update. */
    static void attack(Player player,LivingEntity receiver,Location eye,double distance){
        if(!player.isOnline()||player.isDead()||(player.getGameMode()!=GameMode.SURVIVAL&&player.getGameMode()!=GameMode.ADVENTURE)
                ||!receiver.isValid()||receiver.isDead()
                ||player.getWorld()!=eye.getWorld()||receiver.getWorld()!=eye.getWorld()
                ||player.getEyeLocation().distanceSquared(eye)>.75*.75)return;
        if(distance>0&&eye.getWorld().rayTraceBlocks(eye,eye.getDirection(),distance,FluidCollisionMode.NEVER,true)!=null)return;
        Attack previous=fallback;fallback=new Attack(player,receiver);
        try{player.attack(receiver);}finally{fallback=previous;}
    }
    static boolean reachable(Player player,String name,String clip,double time,Location at,double scale){
        double reach=reach(player);
        Vector eye=player.getEyeLocation().toVector();
        for(var box:boxes(name,clip,time,at,scale)){
            Vector3d p=new Matrix4d(box.getValue()).invert().transformPosition(new Vector3d(eye.getX(),eye.getY(),eye.getZ()));BoundingBox b=box.getKey().clone().expand(AIM_PADDING*16/scale);
            double dx=p.x-Math.clamp(p.x,b.getMinX(),b.getMaxX()),dy=p.y-Math.clamp(p.y,b.getMinY(),b.getMaxY()),dz=p.z-Math.clamp(p.z,b.getMinZ(),b.getMaxZ());
            if((dx*dx+dy*dy+dz*dz)*scale*scale/256<=reach*reach)return true;
        }
        return false;
    }
    static boolean swordContact(Player player,String clip,double time,Location at){
        // Sweep the previous server frame too; fast blades cannot tunnel through
        // a player between two visible poses. Test all separating axes of the OBBs.
        BoundingBox body=player.getBoundingBox().expand(.12);
        for(int i=0;i<=4;i++)for(var blade:boxes("arkhaia",clip,Math.max(0,time-i/80.0),at,3.2,true)){
            BoundingBox b=blade.getKey();Matrix4d m=blade.getValue();
            Vector3d center=m.transformPosition(new Vector3d(b.getCenterX(),b.getCenterY(),b.getCenterZ()));
            Vector3d[] axes={m.transformDirection(new Vector3d(1,0,0)).normalize(),m.transformDirection(new Vector3d(0,1,0)).normalize(),m.transformDirection(new Vector3d(0,0,1)).normalize()};
            double[] extents={b.getWidthX()*.1,b.getHeight()*.1,b.getWidthZ()*.1};
            Vector3d delta=new Vector3d(body.getCenterX()-center.x,body.getCenterY()-center.y,body.getCenterZ()-center.z);
            Vector3d[] world={new Vector3d(1,0,0),new Vector3d(0,1,0),new Vector3d(0,0,1)};
            List<Vector3d> tests=new ArrayList<>(List.of(axes));tests.addAll(List.of(world));
            for(Vector3d a:axes)for(Vector3d axis:world)tests.add(new Vector3d(a).cross(axis));
            boolean overlaps=true;
            for(Vector3d axis:tests){
                double r=body.getWidthX()*.5*Math.abs(axis.x)+body.getHeight()*.5*Math.abs(axis.y)+body.getWidthZ()*.5*Math.abs(axis.z);
                for(int k=0;k<3;k++)r+=extents[k]*Math.abs(axes[k].dot(axis));
                if(Math.abs(delta.dot(axis))>r+1e-8){overlaps=false;break;}
            }
            if(overlaps)return true;
        }
        return false;
    }
    private BossHitbox(){}
}
