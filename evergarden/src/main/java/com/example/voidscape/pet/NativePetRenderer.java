package com.example.voidscape.pet;

import com.example.voidscape.VoidscapePlugin;
import com.google.gson.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Original rare pet sculptures rendered by vanilla display entities, without ME slots. */
public final class NativePetRenderer {
    private record Track(float[][] position,float[][] rotation) {}
    /** period: ticks between authored keyframes, so streamed poses follow the clip as it was keyed. */
    private record Clip(float length,Map<Integer,Track> tracks,int period) {}
    private record Bone(String name,int parent,Vector3f origin,Vector3f rotation,float scale,String item) {}
    private record Model(List<Bone> bones,Map<String,Clip> clips) {}
    private static final Set<String> HOLD=Set.of("sit","greet");
    /** Initial interpolation; each clip then streams at its own keyframe spacing (2-10 ticks). */
    static final int MOTION_PERIOD=5;
    /** A carried pet's seat is the owner's hitbox top (1.8); the drawn head reaches about 1.875. */
    static final float PERCH_LIFT=.07f;
    /** Carried parts are moved on the server only once they drift this far (squared blocks) from the seat. */
    private static final double CARRIED_DRIFT=2*2;
    private final VoidscapePlugin plugin;
    private final Map<String,Model> models=new HashMap<>();
    private String failure="";
    private long transforms;

    public NativePetRenderer(VoidscapePlugin plugin){
        this.plugin=plugin;
        try(InputStream stream=plugin.getResource("native-pets.json")){
            if(stream==null)throw new IOException("native-pets.json is missing");
            JsonObject root=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();
            for(var entry:root.getAsJsonObject("models").entrySet()){
                JsonObject definition=entry.getValue().getAsJsonObject();
                List<Bone> bones=new ArrayList<>();
                for(var value:definition.getAsJsonArray("bones")){
                    JsonObject bone=value.getAsJsonObject();
                    int parent=bone.get("parent").getAsInt();
                    if(parent>=bones.size()||parent< -1)throw new IOException("Invalid joint parent: "+entry.getKey());
                    bones.add(new Bone(bone.get("name").getAsString(),parent,vector(bone.getAsJsonArray("origin")),
                        vector(bone.getAsJsonArray("rotation")),bone.get("scale").getAsFloat(),bone.get("item").getAsString()));
                }
                Map<String,Clip> clips=new HashMap<>();
                for(var clipEntry:definition.getAsJsonObject("clips").entrySet()){
                    var clip=clipEntry.getValue().getAsJsonObject();
                    Map<Integer,Track> tracks=new HashMap<>();
                    for(var trackEntry:clip.getAsJsonObject("bones").entrySet()){
                        var track=trackEntry.getValue().getAsJsonObject();
                        tracks.put(Integer.parseInt(trackEntry.getKey()),new Track(frames(track.getAsJsonArray("position")),frames(track.getAsJsonArray("rotation"))));
                    }
                    float length=clip.get("length").getAsFloat();
                    if(!(length>0))throw new IOException("Invalid animation duration");
                    int keys=1;
                    for(Track track:tracks.values())keys=Math.max(keys,Math.max(track.position.length,track.rotation.length));
                    int period=Math.clamp(Math.round(length*20/Math.max(1,keys-1)),2,10);
                    clips.put(clipEntry.getKey(),new Clip(length,Map.copyOf(tracks),period));
                }
                if(bones.stream().noneMatch(b->!b.item.isEmpty())||!clips.keySet().containsAll(PetCatalog.ANIMATIONS))
                    throw new IOException("Incomplete native pet: "+entry.getKey());
                models.put(entry.getKey(),new Model(List.copyOf(bones),Map.copyOf(clips)));
            }
            int expected=PetCatalog.wildSpecies()*PetCatalog.Stage.values().length;
            if(models.size()!=expected)throw new IOException("Expected "+expected+" non-mythical pet variants; found "+models.size());
        }catch(IOException|RuntimeException error){
            models.clear();failure=error.getMessage();plugin.getLogger().warning("Native pet renderer unavailable: "+failure);
        }
    }
    public boolean ready(){return !models.isEmpty()&&plugin.judgePack()!=null&&plugin.judgePack().nativePetsIncluded();}
    public boolean hasModel(PetCatalog.Species species,PetCatalog.Stage stage){return species!=null&&stage!=null&&models.containsKey(species.id+"_"+stage.id);}
    public String status(){return !failure.isEmpty()?failure:models.size()+" native pet variants | pack "+(ready()?"ready":"unavailable");}
    /** Display transforms actually sent since startup; unchanged poses are never resent. */
    public long transformsSent(){return transforms;}
    public Rig attach(Mob entity,PetCatalog.Species species,PetCatalog.Stage stage){return attach(entity,species,stage,1);}
    public Rig attach(Mob entity,PetCatalog.Species species,PetCatalog.Stage stage,float size){return attach(entity,species,stage,size,true);}
    /** {@code ride=false} keeps the parts free of the receiver; {@link Rig#place} moves them, or {@link Rig#carry} hands them to the clients (a pet perched on a head). */
    public Rig attach(Mob entity,PetCatalog.Species species,PetCatalog.Stage stage,float size,boolean ride){
        if(entity==null||!entity.isValid()||!ready()||!hasModel(species,stage))return null;
        Model model=models.get(species.id+"_"+stage.id);
        float scale=Float.isFinite(size)?Math.clamp(size,.2f,1.3f):1;
        try{return new Rig(entity,model,scale,ride);}
        catch(RuntimeException|LinkageError error){plugin.getLogger().warning("Cannot attach native pet "+species.id+"/"+stage.id+": "+error.getMessage());return null;}
    }
    private static Vector3f vector(JsonArray values){return new Vector3f(values.get(0).getAsFloat(),values.get(1).getAsFloat(),values.get(2).getAsFloat());}
    private static float[][] frames(JsonArray values){
        float[][] result=new float[values.size()][4];
        for(int i=0;i<values.size();i++)for(int a=0;a<4;a++)result[i][a]=values.get(i).getAsJsonArray().get(a).getAsFloat();
        return result;
    }
    private static Vector3f sample(float[][] frames,float time){
        if(frames.length==0)return new Vector3f();
        if(time<=frames[0][0])return new Vector3f(frames[0][1],frames[0][2],frames[0][3]);
        for(int i=1;i<frames.length;i++)if(time<=frames[i][0]){
            float[] a=frames[i-1],b=frames[i];float factor=(time-a[0])/(b[0]-a[0]);
            return new Vector3f(a[1],a[2],a[3]).lerp(new Vector3f(b[1],b[2],b[3]),factor);
        }
        float[] last=frames[frames.length-1];return new Vector3f(last[1],last[2],last[3]);
    }
    private static Quaternionf rotation(Vector3f degrees){
        float radians=(float)(Math.PI/180);
        return new Quaternionf().rotationZYX(degrees.z*radians,degrees.y*radians,degrees.x*radians);
    }

    private static float wrap(float degrees){degrees%=360;if(degrees>=180)degrees-=360;if(degrees<-180)degrees+=360;return degrees;}
    // Plain field arithmetic: the server's bundled JOML may predate newer convenience methods.
    private static float gap(Vector3f a,Vector3f b){float x=a.x-b.x,y=a.y-b.y,z=a.z-b.z;return x*x+y*y+z*z;}
    private static boolean same(Transformation a,Transformation b){
        Quaternionf p=a.getLeftRotation(),q=b.getLeftRotation();
        return gap(a.getTranslation(),b.getTranslation())<1e-6f&&gap(a.getScale(),b.getScale())<1e-8f
            &&Math.abs(p.x*q.x+p.y*q.y+p.z*q.z+p.w*q.w)>1-1e-6f;
    }

    /**
     * One pet's animated sculpture. Parts ride the hidden receiver as passengers, so clients
     * move them with it and no per-part position packets exist. A transform is only sent when
     * the pose actually changes; held poses and frozen pets cost nothing. Visibility per viewer
     * is owned by {@link PetBodies}.
     */
    public final class Rig implements AutoCloseable {
        private final Mob entity;
        private final Model model;
        private final List<ItemDisplay> displays=new ArrayList<>();
        private final List<Integer> displayBones=new ArrayList<>();
        private final List<Transformation> sent=new ArrayList<>();
        private final boolean wasInvisible;
        private final float size,ride;
        private boolean riding=true;
        /** Seated on a player by each viewer's client ({@link PetMount}); the server only keeps the parts in range. */
        private boolean carried;
        private int lastAdvance,lastEmit=Integer.MIN_VALUE/2;
        private float clipTime,lastYaw=Float.NaN,lastPitch,lastRoll,lastHeadYaw,lastHeadPitch;
        private String animation="idle";
        private boolean closed,settled;

        private Rig(Mob entity,Model model,float size,boolean mount){
            this.entity=entity;this.model=model;this.size=size;this.wasInvisible=entity.isInvisible();riding=mount;
            // Display passengers sit at the receiver's passenger attachment, its full height.
            ride=(float)entity.getHeight();
            lastAdvance=Bukkit.getCurrentTick();
            // The rig's transform owns world facing; a display must not inherit the receiver's yaw.
            Location origin=entity.getLocation();origin.setYaw(0);origin.setPitch(0);
            try{
                for(int index=0;index<model.bones.size();index++){
                    Bone bone=model.bones.get(index);if(bone.item.isEmpty())continue;
                    ItemStack item=new ItemStack(Material.PAPER);var meta=item.getItemMeta();
                    meta.setItemModel(new NamespacedKey("evergarden",bone.item));item.setItemMeta(meta);
                    ItemDisplay display=entity.getWorld().spawn(origin,ItemDisplay.class,e->{
                        e.setPersistent(false);e.setGravity(false);e.setInvulnerable(true);e.setSilent(true);
                        e.setVisibleByDefault(false);e.setItemStack(item);e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                        e.setInterpolationDuration(MOTION_PERIOD);e.setInterpolationDelay(0);e.setTeleportDuration(mount?MOTION_PERIOD:2);
                        e.setDisplayWidth(20);e.setDisplayHeight(20);e.setViewRange(1);e.setShadowRadius(0);
                        e.setBrightness(new Display.Brightness(12,15));
                        e.getPersistentDataContainer().set(plugin.key("wild_pet_visual"),PersistentDataType.STRING,entity.getUniqueId().toString());
                    });
                    displays.add(display);displayBones.add(index);sent.add(null);
                    // A cancelled mount falls back to following by teleport, without the ride offset.
                    if(riding&&!entity.addPassenger(display))riding=false;
                }
                if(displays.isEmpty())throw new IllegalStateException("No display bones");
                if(!riding)for(ItemDisplay display:displays)entity.removePassenger(display);
                // Base entities are hidden only after every visible part exists.
                emit("idle",entity.getLocation().getYaw(),0,0,1,0,0,true);entity.setInvisible(true);
            }catch(RuntimeException|LinkageError error){close();throw error;}
        }
        public int partCount(){return displays.size();}
        public String animation(){return animation;}
        public boolean riding(){return riding;}
        public boolean carried(){return carried;}
        /** Free parts only: from now on clients carry them, so poses no longer move them on the server. */
        void carry(){if(!riding)carried=true;}
        public List<ItemDisplay> displays(){return List.copyOf(displays);}
        /** Free parts follow the receiver here; riding parts move with it already; carried parts only keep up. */
        public void place(Location at){
            if(riding||closed)return;
            Location to=at.clone();to.setYaw(0);to.setPitch(0);
            for(ItemDisplay display:displays)
                if(display.isValid()&&(!carried||display.getWorld()!=to.getWorld()||display.getLocation().distanceSquared(to)>CARRIED_DRIFT))display.teleport(to);
        }
        public void tick(boolean walking,boolean flying,float yaw){emit(flying?"fly":walking?"walk":"idle",yaw,0,0,1,0,0,true);}
        /** Plays one authored clip; sit and greet are one-shot poses that hold their last frame. */
        public void tick(String requested,float yaw){emit(requested,yaw,0,0,1,0,0,false);}
        /**
         * Same, with body pitch and roll in degrees (flyers climb, dive and bank) and a playback
         * rate so legs and wings keep pace with the actual speed.
         */
        public void tick(String requested,float yaw,float pitch,float roll,float rate){emit(requested,yaw,pitch,roll,rate,0,0,false);}
        /** Additive attention: headYaw is relative Bukkit yaw; headPitch is positive looking up. */
        public void tick(String requested,float yaw,float pitch,float roll,float rate,float headYaw,float headPitch){
            emit(requested,yaw,pitch,roll,rate,headYaw,headPitch,false);
        }
        private void emit(String requested,float yaw,float pitch,float roll,float rate,float headYaw,float headPitch,boolean force){
            if(closed)return;
            if(!entity.isValid()||entity.isDead()){close();return;}
            String next=model.clips.containsKey(requested)?requested:"idle";
            int now=Bukkit.getCurrentTick();
            headYaw=Float.isFinite(headYaw)?Math.clamp(headYaw,-60,60):0;
            headPitch=Float.isFinite(headPitch)?Math.clamp(headPitch,-25,35):0;
            boolean looking=Math.abs(headYaw-lastHeadYaw)>.5f||Math.abs(headPitch-lastHeadPitch)>.5f;
            boolean switched=!next.equals(animation),turned=Float.isNaN(lastYaw)||Math.abs(wrap(yaw-lastYaw))>.5f
                ||Math.abs(pitch-lastPitch)>.5f||Math.abs(roll-lastRoll)>.5f||looking;
            boolean hold=HOLD.contains(next);
            float speed=hold||!Float.isFinite(rate)?1:Math.clamp(rate,.25f,3f);
            if(switched){animation=next;clipTime=0;settled=false;}
            else clipTime+=(now-lastAdvance)/20f*speed;
            lastAdvance=now;
            Clip clip=model.clips.get(animation);
            // Follow the cadence of the brain (two ticks for companions, five for wild pets),
            // so a turning idle pet keeps interpolating instead of stopping between updates.
            int period=turned?Math.clamp(now-lastEmit,2,Math.min(clip.period,5)):clip.period;
            if(!force&&!switched&&!turned&&(settled||now-lastEmit<period))return;
            if(hold&&clipTime>=clip.length)settled=true;
            // Send the pose due when this interpolation ends, so the client lands on the keyframe in time.
            float ahead=clipTime+period/20f*speed;
            float time=hold?Math.min(ahead,clip.length):ahead%clip.length;
            Matrix4f[] poses=new Matrix4f[model.bones.size()];
            for(int i=0;i<poses.length;i++){
                Bone bone=model.bones.get(i);Track track=clip.tracks.get(i);
                Vector3f position=track==null?new Vector3f():sample(track.position,time);
                Vector3f angle=track==null?new Vector3f():sample(track.rotation,time);
                angle.add(bone.rotation);
                if(bone.name.equals("head")||bone.name.equals("head_l")||bone.name.equals("head_r")){
                    angle.y-=headYaw;angle.x+=headPitch;
                }
                Matrix4f local=new Matrix4f().translation(position).translate(bone.origin).rotate(rotation(angle)).translate(new Vector3f(bone.origin).negate());
                poses[i]=bone.parent<0?local:new Matrix4f(poses[bone.parent]).mul(local);
            }
            Quaternionf facing=new Quaternionf().rotationY((float)Math.toRadians(180-yaw))
                .rotateX((float)Math.toRadians(pitch)).rotateZ((float)Math.toRadians(roll));
            Location at=riding||carried?null:entity.getLocation();
            if(at!=null){at.setYaw(0);at.setPitch(0);}
            for(int piece=0;piece<displays.size();piece++){
                ItemDisplay display=displays.get(piece);if(!display.isValid())continue;
                if(at!=null)display.teleport(at);
                int index=displayBones.get(piece);Bone bone=model.bones.get(index);
                Vector3f offset=poses[index].transformPosition(new Vector3f(bone.origin));facing.transform(offset);
                offset.mul(size);if(riding)offset.y-=ride;if(carried)offset.y+=PERCH_LIFT;
                Quaternionf orient=new Quaternionf(facing).mul(poses[index].getUnnormalizedRotation(new Quaternionf()));
                // ItemDisplayRenderer adds Y(pi) even in NONE context. Cancel it in item-local
                // space so each mesh faces the same way as its authored joint and animation.
                Transformation pose=new Transformation(offset,orient,new Vector3f(bone.scale*size),
                    new Quaternionf().rotationY((float)Math.PI));
                Transformation previous=sent.get(piece);
                if(previous!=null&&same(previous,pose))continue;
                if(display.getInterpolationDuration()!=period)display.setInterpolationDuration(period);
                display.setInterpolationDelay(0);display.setTransformation(pose);sent.set(piece,pose);transforms++;
            }
            lastEmit=now;lastYaw=yaw;lastPitch=pitch;lastRoll=roll;lastHeadYaw=headYaw;lastHeadPitch=headPitch;
        }
        @Override public void close(){
            if(closed)return;closed=true;
            for(ItemDisplay display:displays)display.remove();
            if(entity.isValid())entity.setInvisible(wasInvisible);
        }
    }
}
