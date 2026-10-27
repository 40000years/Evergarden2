import com.google.gson.*;
import java.lang.reflect.*;
import java.nio.file.*;

/** Fast regressions for the shared steering contract, including angle wrap boundaries. */
public final class PetAttentionChecks {
    static final Class<?> TYPE;
    static {try{TYPE=Class.forName("com.example.voidscape.pet.PetAttention");}catch(Exception e){throw new RuntimeException(e);}}
    static int checks;
    static double call(String name,Object... args)throws Exception{
        for(Method method:TYPE.getDeclaredMethods())if(method.getName().equals(name)&&method.getParameterCount()==args.length){
            method.setAccessible(true);return ((Number)method.invoke(null,args)).doubleValue();
        }
        throw new NoSuchMethodException(name);
    }
    static void near(double actual,double expected,String label){if(Math.abs(actual-expected)>1e-5)throw new AssertionError(label+": "+actual+" expected "+expected);checks++;}
    static void check(boolean condition,String label){if(!condition)throw new AssertionError(label);checks++;}
    public static void main(String[] args)throws Exception{
        for(float whole:new float[]{-720,-360,0,360,720}){
            near(call("wrap",179f+whole),179,"wrap before 180");near(call("wrap",180f+whole),-180,"wrap at 180");
            near(call("wrap",181f+whole),-179,"wrap after 180");
            near(call("approachYaw",179f+whole,-179f,1f),-180,"short turn across positive boundary");
            near(call("approachYaw",-179f+whole,179f,1f),-180,"short turn across negative boundary");
            near(call("headYaw",-179f,179f+whole,false),2,"relative head yaw across wrap");
        }
        for(boolean moving:new boolean[]{false,true}){
            float limit=moving?45:60;
            near(call("headYaw",120f,0f,moving),limit,"right head anatomy limit");
            near(call("headYaw",-120f,0f,moving),-limit,"left head anatomy limit");
            near(call("headYaw",15f,0f,moving),15,"small right head turn");
            near(call("headYaw",-15f,0f,moving),-15,"small left head turn");
        }
        near(call("targetPitch",1d,Math.sqrt(3)),25,"positive pitch looks up and clamps");
        near(call("targetPitch",-1d,Math.sqrt(3)),-30,"negative pitch looks down and clamps");
        near(call("targetPitch",1d,10d),Math.toDegrees(Math.atan(.1)),"distant eye target angle");
        near(call("targetPitch",0d,0d),0,"coincident horizontal position stays finite");
        for(float angle:new float[]{0,15,30,45,60,89,90,135,179,180,270,359}){
            double actual=call("turnStride",angle),wrapped=Math.abs(call("wrap",angle));
            double expected=wrapped>=90?0:Math.pow(Math.cos(Math.toRadians(wrapped)),2);
            near(actual,expected,"turn stride "+angle);near(call("turnStride",-angle),actual,"symmetric turn stride "+angle);
            check(actual>=0&&actual<=1,"stride range");
        }
        float facing=179;double previousError=2;
        for(int i=0;i<4;i++){
            facing=(float)call("approachYaw",facing,-179f,.75f);double error=Math.abs(call("wrap",-179f-facing));
            check(error<=previousError,"body catches target without overshoot");previousError=error;
        }
        near(facing,-179,"body reaches a target across wrap");
        JsonObject report=new JsonObject();report.addProperty("result","PASS");report.addProperty("checks",checks);report.addProperty("actual_shared_helper",true);
        Files.writeString(Path.of(args[0]),new GsonBuilder().setPrettyPrinting().create().toJson(report)+"\n");
        System.out.println("PASS "+checks+" shared pet attention math checks");
    }
}
