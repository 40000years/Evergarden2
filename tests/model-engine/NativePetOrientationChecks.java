import com.example.voidscape.pet.NativePetRenderer;
import com.google.gson.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.util.Transformation;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import sun.misc.Unsafe;

import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;

/** Runs the real Rig.emit with Bukkit interface doubles and the server's real JOML. */
public final class NativePetOrientationChecks {
    static final Class<?> BONE=nested("Bone"),TRACK=nested("Track"),CLIP=nested("Clip"),MODEL=nested("Model");
    static final Class<?> RIG=NativePetRenderer.Rig.class;
    static final Unsafe UNSAFE=unsafe();
    static final float[] HEADINGS={0,90,180,270,179,-179,359,-1,-181};
    static int tick,cases,variants;
    static long corners;
    static double maxError;
    record Geometry(int bone,List<Vector3f> item,List<double[]> original){}
    record Group(JsonObject value,int parent){}
    static Class<?> nested(String name){try{return Class.forName(NativePetRenderer.class.getName()+"$"+name);}catch(Exception e){throw new RuntimeException(e);}}
    static Unsafe unsafe(){try{Field f=Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);return (Unsafe)f.get(null);}catch(Exception e){throw new RuntimeException(e);}}
    static void set(Object object,String name,Object value)throws Exception{Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);f.set(object,value);}
    static Object get(Object object,String name)throws Exception{Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}
    static Object record(Class<?> type,Object... values)throws Exception{Constructor<?> c=type.getDeclaredConstructors()[0];c.setAccessible(true);return c.newInstance(values);}
    static Object namedRecord(Class<?> type,Map<String,Object> values)throws Exception{
        return record(type,Arrays.stream(type.getRecordComponents()).map(c->values.get(c.getName())).toArray());
    }
    static boolean fieldExists(String name){try{RIG.getDeclaredField(name);return true;}catch(NoSuchFieldException e){return false;}}
    static double[] vector(JsonArray a){return a==null?new double[3]:new double[]{a.get(0).getAsDouble(),a.get(1).getAsDouble(),a.get(2).getAsDouble()};}
    static Vector3f joml(JsonArray a){double[] v=vector(a);return new Vector3f((float)v[0],(float)v[1],(float)v[2]);}
    static float[][] frames(JsonArray a){float[][] f=new float[a.size()][4];for(int i=0;i<f.length;i++)for(int j=0;j<4;j++)f[i][j]=a.get(i).getAsJsonArray().get(j).getAsFloat();return f;}
    static JsonObject json(Path path)throws Exception{return JsonParser.parseString(Files.readString(path)).getAsJsonObject();}
    static Object model(JsonObject definition)throws Exception{
        List<Object> bones=new ArrayList<>();Map<String,Object> clips=new HashMap<>();
        for(JsonElement e:definition.getAsJsonArray("bones")){JsonObject b=e.getAsJsonObject();bones.add(record(BONE,b.get("name").getAsString(),b.get("parent").getAsInt(),joml(b.getAsJsonArray("origin")),joml(b.getAsJsonArray("rotation")),b.get("scale").getAsFloat(),b.get("item").getAsString()));}
        for(var e:definition.getAsJsonObject("clips").entrySet()){
            JsonObject clip=e.getValue().getAsJsonObject();Map<Integer,Object> tracks=new HashMap<>();
            for(var b:clip.getAsJsonObject("bones").entrySet()){JsonObject t=b.getValue().getAsJsonObject();tracks.put(Integer.parseInt(b.getKey()),record(TRACK,frames(t.getAsJsonArray("position")),frames(t.getAsJsonArray("rotation"))));}
            clips.put(e.getKey(),namedRecord(CLIP,Map.of("length",clip.get("length").getAsFloat(),"tracks",tracks,"period",period(clip))));
        }
        return record(MODEL,bones,clips);
    }
    static Object defaultValue(Class<?> type){if(!type.isPrimitive())return null;if(type==boolean.class)return false;if(type==double.class)return 0d;if(type==float.class)return 0f;if(type==long.class)return 0L;if(type==char.class)return '\0';return 0;}
    @SuppressWarnings("unchecked") static <T>T proxy(Class<T> type,InvocationHandler handler){return (T)Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},handler);}
    static final class Part implements InvocationHandler {
        Transformation pose;Location at=new Location(null,0,0,0);int period=5,writes;
        public Object invoke(Object proxy,Method method,Object[] args){return switch(method.getName()){
            case "isValid" -> true;
            case "getInterpolationDuration" -> period;
            case "setInterpolationDuration" -> {period=(int)args[0];yield null;}
            case "setTransformation" -> {pose=(Transformation)args[0];writes++;yield null;}
            case "teleport" -> {at=((Location)args[0]).clone();yield true;}
            default -> defaultValue(method.getReturnType());
        };}
    }
    record Fixture(Object rig,List<Part> parts,List<Integer> indices,float size,boolean riding){}
    static Fixture fixture(Object model,JsonArray bones,float size,boolean riding)throws Exception{
        NativePetRenderer renderer=(NativePetRenderer)UNSAFE.allocateInstance(NativePetRenderer.class);
        Object rig=UNSAFE.allocateInstance(RIG);List<ItemDisplay> displays=new ArrayList<>();List<Integer> indices=new ArrayList<>();List<Part> parts=new ArrayList<>();
        for(int i=0;i<bones.size();i++)if(!bones.get(i).getAsJsonObject().get("item").getAsString().isEmpty()){Part part=new Part();parts.add(part);displays.add(proxy(ItemDisplay.class,part));indices.add(i);}
        Mob mob=proxy(Mob.class,(p,m,a)->switch(m.getName()){
            case "isValid" -> true;
            case "isDead" -> false;
            // Deliberately unrelated shell yaw and pitch must not enter fallback display rotation.
            case "getLocation" -> new Location(null,0,0,0,117,32);
            default -> defaultValue(m.getReturnType());
        });
        set(rig,"this$0",renderer);set(rig,"entity",mob);set(rig,"model",model);set(rig,"displays",displays);set(rig,"displayBones",indices);
        set(rig,"sent",new ArrayList<>(Collections.nCopies(parts.size(),null)));set(rig,"size",size);set(rig,"ride",.7f);set(rig,"riding",riding);set(rig,"animation","idle");set(rig,"lastYaw",Float.NaN);
        return new Fixture(rig,parts,indices,size,riding);
    }
    static void groups(JsonObject group,int parent,List<Group> out){
        if(group.get("name").getAsString().equals("hitbox")||(group.has("visibility")&&!group.get("visibility").getAsBoolean()))return;
        int index=out.size();out.add(new Group(group,parent));
        for(JsonElement child:group.getAsJsonArray("children"))if(child.isJsonObject())groups(child.getAsJsonObject(),index,out);
    }
    // Scalar Rz * Ry * Rx, independent of the renderer's JOML matrix construction.
    static double[] rotate(double[] p,double[] degrees){
        double x=Math.toRadians(degrees[0]),y=Math.toRadians(degrees[1]),z=Math.toRadians(degrees[2]);
        double a=p[0],b=p[1]*Math.cos(x)-p[2]*Math.sin(x),c=p[1]*Math.sin(x)+p[2]*Math.cos(x);
        double d=a*Math.cos(y)+c*Math.sin(y),e=-a*Math.sin(y)+c*Math.cos(y);
        return new double[]{d*Math.cos(z)-b*Math.sin(z),d*Math.sin(z)+b*Math.cos(z),e};
    }
    static double[] corner(JsonObject cube,int bits,boolean exported){
        double[] p=new double[3];for(int a=0;a<3;a++)p[a]=cube.getAsJsonArray((bits&(1<<a))==0?"from":"to").get(a).getAsDouble();
        if(cube.has("rotation")){
            double[] angle,origin;
            if(exported){JsonObject r=cube.getAsJsonObject("rotation");angle=new double[]{r.get("x").getAsDouble(),r.get("y").getAsDouble(),r.get("z").getAsDouble()};origin=vector(r.getAsJsonArray("origin"));}
            else{angle=vector(cube.getAsJsonArray("rotation"));origin=vector(cube.getAsJsonArray("origin"));}
            for(int a=0;a<3;a++)p[a]-=origin[a];p=rotate(p,angle);for(int a=0;a<3;a++)p[a]+=origin[a];
        }
        for(int a=0;a<3;a++)p[a]=(p[a]-(exported?8:0))/16;
        return p;
    }
    static List<Geometry> geometry(JsonObject source,JsonArray bones,ZipFile pack)throws Exception{
        Map<String,JsonObject> cubes=new HashMap<>();for(JsonElement e:source.getAsJsonArray("elements")){JsonObject c=e.getAsJsonObject();cubes.put(c.get("uuid").getAsString(),c);}
        List<Group> groups=new ArrayList<>();for(JsonElement g:source.getAsJsonArray("outliner"))if(g.isJsonObject())groups(g.getAsJsonObject(),-1,groups);
        if(groups.size()!=bones.size())throw new AssertionError("source bone count");
        List<Geometry> result=new ArrayList<>();
        for(int i=0;i<bones.size();i++){
            JsonObject b=bones.get(i).getAsJsonObject();String item=b.get("item").getAsString();if(item.isEmpty())continue;
            JsonObject exported;try(var input=pack.getInputStream(pack.getEntry("assets/evergarden/models/"+item+".json"))){exported=JsonParser.parseString(new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();}
            List<JsonObject> originals=new ArrayList<>();for(JsonElement child:groups.get(i).value.getAsJsonArray("children"))if(child.isJsonPrimitive()){
                JsonObject cube=cubes.get(child.getAsString());if((!cube.has("visibility")||cube.get("visibility").getAsBoolean())&&(!cube.has("export")||cube.get("export").getAsBoolean()))originals.add(cube);
            }
            JsonArray elements=exported.getAsJsonArray("elements");if(elements.size()!=originals.size())throw new AssertionError("source cube count");
            List<Vector3f> points=new ArrayList<>();List<double[]> expected=new ArrayList<>();
            for(int c=0;c<originals.size();c++)for(int bits=0;bits<8;bits++){double[] p=corner(elements.get(c).getAsJsonObject(),bits,true);points.add(new Vector3f((float)p[0],(float)p[1],(float)p[2]));expected.add(corner(originals.get(c),bits,false));}
            result.add(new Geometry(i,points,expected));
        }
        return result;
    }
    static double[] sample(JsonArray frames,float time){
        if(frames.isEmpty())return new double[3];JsonArray a=frames.get(0).getAsJsonArray();
        for(int i=1;i<frames.size()&&time>a.get(0).getAsFloat();i++){
            JsonArray b=frames.get(i).getAsJsonArray();if(time<=b.get(0).getAsFloat()){
                double f=(time-a.get(0).getAsDouble())/(b.get(0).getAsDouble()-a.get(0).getAsDouble());double[] v=new double[3];for(int j=0;j<3;j++)v[j]=a.get(j+1).getAsDouble()*(1-f)+b.get(j+1).getAsDouble()*f;return v;
            }a=b;
        }
        return new double[]{a.get(1).getAsDouble(),a.get(2).getAsDouble(),a.get(3).getAsDouble()};
    }
    static int period(JsonObject clip){
        int keys=1;for(var entry:clip.getAsJsonObject("bones").entrySet()){
            JsonObject track=entry.getValue().getAsJsonObject();
            keys=Math.max(keys,Math.max(track.getAsJsonArray("position").size(),track.getAsJsonArray("rotation").size()));
        }
        return Math.clamp(Math.round(clip.get("length").getAsFloat()*20/Math.max(1,keys-1)),2,10);
    }
    static float sampleTime(JsonObject clip,String animation,int atTick,float rate){
        return sampleTime(clip,animation,atTick,rate,period(clip));
    }
    static float sampleTime(JsonObject clip,String animation,int atTick,float rate,int interpolationPeriod){
        boolean hold=Set.of("sit","greet").contains(animation);float speed=hold?1:Math.clamp(rate,.25f,3f);
        float elapsed=atTick/20f;
        // Current rigs stream the pose at the end of the display interpolation window.
        if(fieldExists("clipTime"))elapsed+=interpolationPeriod/20f*speed;
        return hold?Math.min(elapsed,clip.get("length").getAsFloat()):elapsed%clip.get("length").getAsFloat();
    }
    static boolean head(String name){return Set.of("head","head_l","head_r").contains(name);}
    static boolean headDescendant(JsonArray bones,int index){
        for(int i=index;i>=0;i=bones.get(i).getAsJsonObject().get("parent").getAsInt())if(head(bones.get(i).getAsJsonObject().get("name").getAsString()))return true;
        return false;
    }
    static float wrap(float value){value%=360;if(value>=180)value-=360;if(value< -180)value+=360;return value;}
    static int interpolationPeriod(Fixture f,JsonObject clip,int atTick,float yaw,float pitch,float roll,float headYaw,float headPitch)throws Exception{
        int period=period(clip);if(!fieldExists("lastHeadYaw"))return period;
        float lastYaw=(float)get(f.rig,"lastYaw");
        boolean turned=Float.isNaN(lastYaw)||Math.abs(wrap(yaw-lastYaw))>.5f
            ||Math.abs(pitch-(float)get(f.rig,"lastPitch"))>.5f||Math.abs(roll-(float)get(f.rig,"lastRoll"))>.5f
            ||Math.abs(headYaw-(float)get(f.rig,"lastHeadYaw"))>.5f||Math.abs(headPitch-(float)get(f.rig,"lastHeadPitch"))>.5f;
        return turned?Math.clamp(atTick-(int)get(f.rig,"lastEmit"),2,Math.min(period,5)):period;
    }
    static double[] expected(double[] original,int index,JsonArray bones,JsonObject clip,float time,float yaw,float size){
        return expected(original,index,bones,clip,time,yaw,0,0,size,0,0);
    }
    static double[] expected(double[] original,int index,JsonArray bones,JsonObject clip,float time,float yaw,float pitch,float roll,float size,float headYaw,float headPitch){
        double[] p=original.clone();JsonObject tracks=clip.getAsJsonObject("bones");
        for(int i=index;i>=0;i=bones.get(i).getAsJsonObject().get("parent").getAsInt()){
            JsonObject bone=bones.get(i).getAsJsonObject(),track=tracks.getAsJsonObject(Integer.toString(i));double[] origin=vector(bone.getAsJsonArray("origin")),angle=vector(bone.getAsJsonArray("rotation"));
            double[] position=track==null?new double[3]:sample(track.getAsJsonArray("position"),time),motion=track==null?new double[3]:sample(track.getAsJsonArray("rotation"),time);
            if(head(bone.get("name").getAsString())){angle[0]+=headPitch;angle[1]-=headYaw;}
            for(int a=0;a<3;a++){p[a]-=origin[a];angle[a]+=motion[a];}p=rotate(p,angle);for(int a=0;a<3;a++)p[a]+=origin[a]+position[a];
        }
        p=rotate(p,new double[]{0,0,roll});p=rotate(p,new double[]{pitch,0,0});
        p=rotate(p,new double[]{0,180-yaw,0});for(int a=0;a<3;a++)p[a]*=size;return p;
    }
    static void emit(Fixture f,String animation,int atTick,float yaw,float pitch,float roll,float rate,float headYaw,float headPitch,boolean force)throws Exception{
        tick=atTick;set(f.rig,"animation",animation);
        if(fieldExists("clipTime")){set(f.rig,"clipTime",atTick/20f);set(f.rig,"lastAdvance",atTick);}
        else set(f.rig,"clipStartedTick",0);
        Method emit=Arrays.stream(RIG.getDeclaredMethods()).filter(m->m.getName().equals("emit")).max(Comparator.comparingInt(Method::getParameterCount)).orElseThrow();
        emit.setAccessible(true);
        switch(emit.getParameterCount()){
            case 8 -> emit.invoke(f.rig,animation,yaw,pitch,roll,rate,headYaw,headPitch,force);
            case 6 -> {if(headYaw!=0||headPitch!=0)throw new AssertionError("Rig.emit lacks head attention");emit.invoke(f.rig,animation,yaw,pitch,roll,rate,force);}
            case 3 -> {if(headYaw!=0||headPitch!=0)throw new AssertionError("Rig.emit lacks head attention");emit.invoke(f.rig,animation,yaw,force);}
            default -> throw new AssertionError("Unknown Rig.emit signature: "+emit);
        }
    }
    static Matrix4f client(Transformation t){
        // ItemDisplayRenderer applies Y180 even with ItemDisplayTransform.NONE.
        return new Matrix4f().translation(t.getTranslation()).rotate(t.getLeftRotation()).scale(t.getScale()).rotate(t.getRightRotation()).rotateY((float)Math.PI);
    }
    static Vector3f rendered(Fixture f,Geometry g,int corner){
        Transformation t=f.parts.get(f.indices.indexOf(g.bone)).pose;if(t==null)throw new AssertionError("missing emitted transform");
        Vector3f point=client(t).transformPosition(new Vector3f(g.item.get(corner)));if(f.riding)point.y+=.7f;return point;
    }
    static void verify(Fixture f,List<Geometry> geometry,JsonArray bones,JsonObject clips,String id,String animation,int atTick,float yaw)throws Exception{
        JsonObject clip=clips.getAsJsonObject(animation);int period=interpolationPeriod(f,clip,atTick,yaw,0,0,0,0);
        // Sample each geometry pose exactly; unchanged-packet culling has separate held-pose checks.
        set(f.rig,"sent",new ArrayList<>(Collections.nCopies(f.parts.size(),null)));
        emit(f,animation,atTick,yaw,0,0,1,0,0,true);
        float time=sampleTime(clip,animation,atTick,1,period);
        for(Geometry g:geometry){
            int piece=f.indices.indexOf(g.bone);Part part=f.parts.get(piece);Transformation t=part.pose;if(t==null)throw new AssertionError("missing emitted transform");
            if(part.at.getYaw()!=0||part.at.getPitch()!=0)throw new AssertionError("fallback display retains shell rotation");
            // ItemDisplayRenderer always applies Y180, including ItemDisplayTransform.NONE.
            Matrix4f client=client(t);
            for(int i=0;i<g.item.size();i++){
                Vector3f actual=client.transformPosition(new Vector3f(g.item.get(i)));if(f.riding)actual.y+=.7f;
                double[] wanted=expected(g.original.get(i),g.bone,bones,clip,time,yaw,f.size);
                double error=Math.max(Math.abs(actual.x-wanted[0]),Math.max(Math.abs(actual.y-wanted[1]),Math.abs(actual.z-wanted[2])));maxError=Math.max(maxError,error);corners++;
                if(error>3e-5)throw new AssertionError("cube corner orientation: "+id+" "+animation+" tick="+atTick+" yaw="+yaw+" bone="+bones.get(g.bone).getAsJsonObject().get("name")+" expected="+Arrays.toString(wanted)+" actual="+actual+" error="+error);
            }
        }cases++;
    }
    public static void main(String[] args)throws Exception{
        Path root=Path.of(args[0]),resources=root.resolve("evergarden/src/main/resources");
        Field server=Bukkit.class.getDeclaredField("server");server.setAccessible(true);server.set(null,proxy(Server.class,(p,m,a)->m.getName().equals("getCurrentTick")?tick:defaultValue(m.getReturnType())));
        JsonObject models=json(resources.resolve("native-pets.json")).getAsJsonObject("models");
        try(ZipFile pack=new ZipFile(resources.resolve("native-pets.zip").toFile())){
            for(var entry:models.entrySet()){
                String id=entry.getKey();JsonObject definition=entry.getValue().getAsJsonObject(),clips=definition.getAsJsonObject("clips");JsonArray bones=definition.getAsJsonArray("bones");
                List<Geometry> points=geometry(json(resources.resolve("modelengine/pets/everpet_"+id+".bbmodel")),bones,pack);Object model=model(definition);
                Fixture base=fixture(model,bones,1,true);
                for(String animation:clips.keySet())for(int at:new int[]{0,7,100})for(float yaw:HEADINGS)verify(base,points,bones,clips,id,animation,at,yaw);
                // Both passenger and cancelled-mount teleport paths preserve scaling and heading.
                for(boolean riding:new boolean[]{true,false})for(float size:new float[]{.7f,1.3f})verify(fixture(model,bones,size,riding),points,bones,clips,id,"walk",7,23);
                variants++;
            }
        }
        if(variants!=36)throw new AssertionError("expected all 36 native variants");
        JsonObject report=new JsonObject();report.addProperty("result","PASS");report.addProperty("variants",variants);report.addProperty("pose_cases",cases);report.addProperty("source_cube_corners",corners);report.addProperty("maximum_error_blocks",maxError);report.addProperty("actual_rig_emit",true);report.addProperty("client_implicit_y180",true);report.addProperty("connected_client_playtest",false);
        Files.writeString(Path.of(args[1]),new GsonBuilder().setPrettyPrinting().create().toJson(report)+"\n");
        System.out.println("PASS "+variants+" variants; "+cases+" actual emitted poses; "+corners+" source cube corners; maximum error "+maxError+" blocks");
    }
}
