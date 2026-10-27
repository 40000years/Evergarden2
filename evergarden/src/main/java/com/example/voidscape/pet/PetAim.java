package com.example.voidscape.pet;

import com.example.voidscape.VoidscapePlugin;
import com.google.gson.*;
import org.bukkit.Location;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Click geometry follows the authored sculpture, rather than the hidden rabbit at its feet. */
final class PetAim {
    private static final double PADDING=.16;
    private final VoidscapePlugin plugin;
    private final Map<String,List<BoundingBox>> models=new HashMap<>();

    PetAim(VoidscapePlugin plugin){this.plugin=plugin;}

    private static Vector3f vector(JsonArray value){return new Vector3f(value.get(0).getAsFloat(),value.get(1).getAsFloat(),value.get(2).getAsFloat());}
    private static Quaternionf rotation(Vector3f value){
        float radians=(float)(Math.PI/180);
        return new Quaternionf().rotationZYX(value.z*radians,value.y*radians,value.x*radians);
    }
    private List<BoundingBox> geometry(PetBody body){
        return models.computeIfAbsent(body.species.model(body.stage),id->{
            try(InputStream stream=plugin.getResource("modelengine/pets/"+id+".bbmodel")){
                if(stream==null)throw new IOException("missing model");
                JsonObject model=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();
                Map<String,JsonObject> cubes=new HashMap<>();
                for(JsonElement value:model.getAsJsonArray("elements")){
                    JsonObject cube=value.getAsJsonObject();cubes.put(cube.get("uuid").getAsString(),cube);
                }
                List<BoundingBox> result=new ArrayList<>();
                for(JsonElement group:model.getAsJsonArray("outliner"))collect(group,new Matrix4f(),cubes,result);
                if(result.isEmpty())throw new IOException("empty model");
                return List.copyOf(result);
            }catch(IOException|RuntimeException error){
                plugin.getLogger().warning("Using fallback pet click geometry for "+id+": "+error.getMessage());
                double width=switch(body.stage){case BABY->.9;case MEDIUM->1.4;case ADULT->1.8;};
                double height=switch(body.stage){case BABY->1.05;case MEDIUM->1.5;case ADULT->1.9;};
                return List.of(new BoundingBox(-width/2,0,-width/2,width/2,height,width/2));
            }
        });
    }
    private static boolean visible(JsonObject object){
        return (!object.has("visibility")||object.get("visibility").getAsBoolean())&&(!object.has("export")||object.get("export").getAsBoolean());
    }
    private static void collect(JsonElement value,Matrix4f parent,Map<String,JsonObject> cubes,List<BoundingBox> result){
        if(value.isJsonPrimitive()){
            JsonObject cube=cubes.get(value.getAsString());if(cube==null||!visible(cube))return;
            Vector3f from=vector(cube.getAsJsonArray("from")),to=vector(cube.getAsJsonArray("to"));
            Matrix4f pose=new Matrix4f(parent);
            if(cube.has("rotation")){
                Vector3f origin=vector(cube.getAsJsonArray("origin"));
                pose.translate(origin).rotate(rotation(vector(cube.getAsJsonArray("rotation")))).translate(new Vector3f(origin).negate());
            }
            BoundingBox box=null;
            for(int x=0;x<2;x++)for(int y=0;y<2;y++)for(int z=0;z<2;z++){
                Vector3f point=pose.transformPosition(new Vector3f(x==0?from.x:to.x,y==0?from.y:to.y,z==0?from.z:to.z)).div(16);
                if(box==null)box=new BoundingBox(point.x,point.y,point.z,point.x,point.y,point.z);
                else box.union(point.x,point.y,point.z);
            }
            result.add(box);return;
        }
        JsonObject group=value.getAsJsonObject();
        if(!visible(group)||group.get("name").getAsString().equals("hitbox"))return;
        Vector3f origin=group.has("origin")?vector(group.getAsJsonArray("origin")):new Vector3f();
        Vector3f angle=group.has("rotation")?vector(group.getAsJsonArray("rotation")):new Vector3f();
        Matrix4f pose=new Matrix4f(parent).translate(origin).rotate(rotation(angle)).translate(new Vector3f(origin).negate());
        for(JsonElement child:group.getAsJsonArray("children"))collect(child,pose,cubes,result);
    }
    private static float scale(PetBody body){return body.record==null?1:Math.clamp(body.record.size,.2f,1.3f);}
    private static Quaternionf facing(PetBody body){
        return new Quaternionf().rotationY((float)Math.toRadians(180-body.yaw))
            .rotateX((float)Math.toRadians(body.pitch)).rotateZ((float)Math.toRadians(body.roll));
    }
    private static Vector3f local(PetBody body,Location eye){
        Location at=body.location();
        Vector3f point=new Vector3f((float)(eye.getX()-at.getX()),(float)(eye.getY()-at.getY()),(float)(eye.getZ()-at.getZ()));
        return facing(body).conjugate().transform(point).div(scale(body));
    }
    /** First visible piece under the crosshair, in world blocks; infinity means a miss. */
    double hit(PetBody body,Location eye,Vector direction,double reach){
        float size=scale(body);Vector3f start=local(body,eye);
        Vector3f aim=facing(body).conjugate().transform(new Vector3f((float)direction.getX(),(float)direction.getY(),(float)direction.getZ()));
        Vector origin=new Vector(start.x,start.y,start.z),ray=new Vector(aim.x,aim.y,aim.z);
        double nearest=Double.POSITIVE_INFINITY;
        for(BoundingBox geometry:geometry(body)){
            BoundingBox box=geometry.clone().expand(PADDING/size);
            if(box.contains(origin))return 0;
            var hit=box.rayTrace(origin,ray,reach/size);
            if(hit!=null)nearest=Math.min(nearest,origin.distance(hit.getHitPosition())*size);
        }
        return nearest;
    }
    /** Contact nearest the eyes, used for native entity interactions including Bedrock touch. */
    Location contact(PetBody body,Location eye){
        float size=scale(body);Vector3f start=local(body,eye),chosen=null;float nearest=Float.POSITIVE_INFINITY;
        for(BoundingBox geometry:geometry(body)){
            BoundingBox box=geometry.clone().expand(PADDING/size);
            Vector3f point=new Vector3f((float)Math.clamp(start.x,box.getMinX(),box.getMaxX()),
                (float)Math.clamp(start.y,box.getMinY(),box.getMaxY()),(float)Math.clamp(start.z,box.getMinZ(),box.getMaxZ()));
            float distance=point.distanceSquared(start);if(distance<nearest){nearest=distance;chosen=point;}
        }
        facing(body).transform(chosen.mul(size));
        return body.location().add(chosen.x,chosen.y,chosen.z);
    }
}
