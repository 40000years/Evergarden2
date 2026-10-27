import com.google.gson.*;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.joml.Vector3f;

import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;

/** Checks the actual display geometry, including the client's implicit item Y180. */
public final class NativePetGazeChecks {
    static int cases,variants,heldCases,descendantParts,unaffectedParts,headingCases;
    static long corners;
    static double maxError;
    static final float[][] LOOKS={{30,0},{-30,0},{0,20},{0,-20},{45,22},{-45,-20}};
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    static double distance(Vector3f a,double[] b){return Math.max(Math.abs(a.x-b[0]),Math.max(Math.abs(a.y-b[1]),Math.abs(a.z-b[2])));}
    static float yaw(float value){return Float.isFinite(value)?Math.clamp(value,-60,60):0;}
    static float pitch(float value){return Float.isFinite(value)?Math.clamp(value,-25,35):0;}
    static float time(JsonObject clip,String animation,int atTick,int period){
        float elapsed=atTick/20f+period/20f,length=clip.get("length").getAsFloat();
        return Set.of("sit","greet").contains(animation)?Math.min(elapsed,length):elapsed%length;
    }
    static void verify(Object model,List<NativePetOrientationChecks.Geometry> geometry,JsonArray bones,JsonObject clips,
                       String id,String animation,float heading,float requestedYaw,float requestedPitch)throws Exception{
        var f=NativePetOrientationChecks.fixture(model,bones,1,true);
        float headYaw=yaw(requestedYaw),headPitch=pitch(requestedPitch);
        NativePetOrientationChecks.emit(f,animation,7,heading,9,-6,1,requestedYaw,requestedPitch,true);
        JsonObject clip=clips.getAsJsonObject(animation);
        // Every fresh fixture turns from NaN yaw; decision-to-decision timing owns interpolation.
        int expectedPeriod=Math.clamp(7,2,Math.min(NativePetOrientationChecks.period(clip),5));
        float time=time(clip,animation,7,expectedPeriod);
        boolean moved=false;
        for(var g:geometry){
            boolean affected=NativePetOrientationChecks.headDescendant(bones,g.bone());
            var part=f.parts().get(f.indices().indexOf(g.bone()));
            check(part.period==expectedPeriod,"head response interpolation: "+id+" "+animation);
            for(int corner=0;corner<g.item().size();corner++){
                Vector3f actual=NativePetOrientationChecks.rendered(f,g,corner);
                double[] wanted=NativePetOrientationChecks.expected(g.original().get(corner),g.bone(),bones,clip,time,heading,9,-6,1,headYaw,headPitch);
                double error=distance(actual,wanted);maxError=Math.max(maxError,error);corners++;
                check(error<3e-5,"emitted gaze geometry: "+id+" "+animation+" heading="+heading+" look="+requestedYaw+","+requestedPitch+" bone="+bones.get(g.bone()).getAsJsonObject().get("name")+" error="+error);
                double[] neutral=NativePetOrientationChecks.expected(g.original().get(corner),g.bone(),bones,clip,time,heading,9,-6,1,0,0);
                if(affected)moved|=distance(actual,neutral)>.001;
                else check(distance(actual,neutral)<3e-5,"attention moves a non-head branch: "+id);
            }
            if(affected)descendantParts++;else unaffectedParts++;
        }
        check((headYaw==0&&headPitch==0)||moved,"visible head never turns: "+id);
        cases++;
    }
    static void heldAttention(Object model,JsonArray bones,String id)throws Exception{
        var f=NativePetOrientationChecks.fixture(model,bones,1,true);
        NativePetOrientationChecks.emit(f,"sit",100,0,0,0,1,0,0,true);
        NativePetOrientationChecks.set(f.rig(),"settled",true);
        int[] writes=f.parts().stream().mapToInt(p->p.writes).toArray();
        Method tick=NativePetOrientationChecks.RIG.getDeclaredMethod("tick",String.class,float.class,float.class,float.class,float.class,float.class,float.class);
        NativePetOrientationChecks.tick=101;
        tick.invoke(f.rig(),"sit",0f,0f,0f,1f,30f,15f);
        int changed=0;
        for(int i=0;i<writes.length;i++){
            boolean affected=NativePetOrientationChecks.headDescendant(bones,f.indices().get(i));
            if(f.parts().get(i).writes>writes[i]){check(affected,"settled gaze resends body: "+id);changed++;}
        }
        check(changed>0,"settled pose ignores new gaze: "+id);
        writes=f.parts().stream().mapToInt(p->p.writes).toArray();
        NativePetOrientationChecks.tick=102;
        tick.invoke(f.rig(),"sit",0f,0f,0f,1f,30f,15f);
        check(Arrays.equals(writes,f.parts().stream().mapToInt(p->p.writes).toArray()),"unchanged held gaze resends transforms: "+id);
        heldCases++;
    }
    static void wrapEquivalent(Object model,List<NativePetOrientationChecks.Geometry> geometry,JsonArray bones,String id)throws Exception{
        for(float[] pair:new float[][]{{359,-1},{179,-181},{0,360}}){
            var a=NativePetOrientationChecks.fixture(model,bones,1,false);
            var b=NativePetOrientationChecks.fixture(model,bones,1,false);
            NativePetOrientationChecks.emit(a,"idle",7,pair[0],0,0,1,30,15,true);
            NativePetOrientationChecks.emit(b,"idle",7,pair[1],0,0,1,30,15,true);
            for(var g:geometry)for(int c=0;c<g.item().size();c++){
                Vector3f p=NativePetOrientationChecks.rendered(a,g,c),q=NativePetOrientationChecks.rendered(b,g,c);
                check(p.distance(q)<3e-5,"world yaw wrap changes gaze geometry: "+id);
            }
            headingCases++;
        }
    }
    static void facingDirection()throws Exception{
        // A head, its eye child, two Cerberus sibling heads, and an unrelated body branch.
        JsonObject definition=JsonParser.parseString("""
            {"bones":[
              {"name":"body","parent":-1,"origin":[0,0,0],"rotation":[0,0,0],"scale":1,"item":"test/body"},
              {"name":"head","parent":0,"origin":[0,1,-1],"rotation":[0,0,0],"scale":1,"item":"test/head"},
              {"name":"eye","parent":1,"origin":[0.2,1,-1.5],"rotation":[0,0,0],"scale":1,"item":"test/eye"},
              {"name":"head_l","parent":0,"origin":[1,1,-1],"rotation":[0,0,0],"scale":1,"item":"test/left"},
              {"name":"head_r","parent":0,"origin":[-1,1,-1],"rotation":[0,0,0],"scale":1,"item":"test/right"}],
             "clips":{"idle":{"length":1,"bones":{}}}}
            """).getAsJsonObject();
        JsonArray bones=definition.getAsJsonArray("bones");Object model=NativePetOrientationChecks.model(definition);
        for(float heading:NativePetOrientationChecks.HEADINGS)for(float headYaw:new float[]{-45,0,45})for(float headPitch:new float[]{-20,0,20}){
            var f=NativePetOrientationChecks.fixture(model,bones,1,false);
            NativePetOrientationChecks.emit(f,"idle",0,heading,0,0,1,headYaw,headPitch,true);
            double h=Math.toRadians(heading+headYaw),p=Math.toRadians(headPitch);
            Vector3f wanted=new Vector3f((float)(-Math.sin(h)*Math.cos(p)),(float)Math.sin(p),(float)(Math.cos(h)*Math.cos(p)));
            for(int index:new int[]{1,2,3,4}){
                Vector3f actual=NativePetOrientationChecks.client(f.parts().get(index).pose).transformDirection(new Vector3f(0,0,-1)).normalize();
                check(actual.distance(wanted)<1e-5,"head looks away from requested world direction: heading="+heading+" look="+headYaw+","+headPitch+" bone="+index+" actual="+actual+" wanted="+wanted);
            }
            Vector3f body=NativePetOrientationChecks.client(f.parts().get(0).pose).transformDirection(new Vector3f(0,0,-1)).normalize();
            Vector3f expectedBody=new Vector3f((float)-Math.sin(Math.toRadians(heading)),0,(float)Math.cos(Math.toRadians(heading)));
            check(body.distance(expectedBody)<1e-5,"body follows head overlay");
            headingCases++;
        }
    }
    public static void main(String[] args)throws Exception{
        Path root=Path.of(args[0]),resources=root.resolve("evergarden/src/main/resources");
        Field server=Bukkit.class.getDeclaredField("server");server.setAccessible(true);
        server.set(null,NativePetOrientationChecks.proxy(Server.class,(p,m,a)->m.getName().equals("getCurrentTick")?NativePetOrientationChecks.tick:NativePetOrientationChecks.defaultValue(m.getReturnType())));
        facingDirection();
        JsonObject models=NativePetOrientationChecks.json(resources.resolve("native-pets.json")).getAsJsonObject("models");
        try(ZipFile pack=new ZipFile(resources.resolve("native-pets.zip").toFile())){
            for(var entry:models.entrySet()){
                String id=entry.getKey();JsonObject definition=entry.getValue().getAsJsonObject(),clips=definition.getAsJsonObject("clips");JsonArray bones=definition.getAsJsonArray("bones");
                var geometry=NativePetOrientationChecks.geometry(NativePetOrientationChecks.json(resources.resolve("modelengine/pets/everpet_"+id+".bbmodel")),bones,pack);
                Object model=NativePetOrientationChecks.model(definition);
                for(String animation:clips.keySet())for(float heading:NativePetOrientationChecks.HEADINGS)for(float[] look:LOOKS)verify(model,geometry,bones,clips,id,animation,heading,look[0],look[1]);
                for(float[] look:new float[][]{{180,90},{-180,-90},{Float.NaN,Float.NaN},{Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY}})verify(model,geometry,bones,clips,id,"idle",23,look[0],look[1]);
                heldAttention(model,bones,id);wrapEquivalent(model,geometry,bones,id);variants++;
            }
        }
        check(variants==36,"all 36 variants must be covered");
        JsonObject report=new JsonObject();report.addProperty("result","PASS");report.addProperty("variants",variants);report.addProperty("gaze_pose_cases",cases);report.addProperty("source_cube_corners",corners);report.addProperty("maximum_error_blocks",maxError);
        report.addProperty("affected_head_branch_checks",descendantParts);report.addProperty("unaffected_body_branch_checks",unaffectedParts);report.addProperty("held_attention_cases",heldCases);report.addProperty("world_heading_cases",headingCases);report.addProperty("actual_rig_emit",true);report.addProperty("connected_client_playtest",false);
        Files.writeString(Path.of(args[1]),new GsonBuilder().setPrettyPrinting().create().toJson(report)+"\n");
        System.out.println("PASS "+variants+" gaze variants; "+cases+" emitted gaze poses; "+corners+" source cube corners; "+heldCases+" held attention cases; "+headingCases+" world heading cases");
    }
}
