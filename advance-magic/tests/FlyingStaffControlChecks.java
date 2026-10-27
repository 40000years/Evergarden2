import com.example.advancemagic.AdvanceMagicPlugin;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiConsumer;

/** Exercises the actual service's input calculation on an isolated Paper server. */
public final class FlyingStaffControlChecks {
    private static final int FORWARD=1, BACKWARD=2, LEFT=4, RIGHT=8;
    private static final double HORIZONTAL=.486, VERTICAL=.288, EPSILON=1e-9;
    private static final float[] YAWS={0,45,90,135,180,225,270,315,
        -45,-90,-135,-180,-225,-270,-315,-360,360,405,720};
    private static final String HORIZONTAL_KEY="flying-staff.horizontal-speed";
    private static final String VERTICAL_KEY="flying-staff.vertical-speed";
    private static final String TURBO_KEY="flying-staff.turbo-multiplier";
    private static final String PITCH_DEADZONE_KEY="flying-staff.bedrock-controls.pitch-deadzone";
    private static final String PITCH_FULL_KEY="flying-staff.bedrock-controls.pitch-full-speed";

    private FlyingStaffControlChecks(){}

    /** The supplied rider must already own a real mounted flying-staff session. */
    public static void run(AdvanceMagicPlugin plugin,Player rider,
                           BiConsumer<Boolean,String> check) throws Exception {
        Object service=plugin.flyingStaff();
        Field sessionsField=service.getClass().getDeclaredField("sessions");
        sessionsField.setAccessible(true);
        Map<?,?> sessions=(Map<?,?>)sessionsField.get(service);
        Object session=sessions.get(rider.getUniqueId());
        if(session==null||!plugin.flyingStaff().isRiding(rider))
            throw new IllegalStateException("Control checks require a mounted flying-staff rider");
        Field manualTurbo=session.getClass().getDeclaredField("manualTurbo");
        manualTurbo.setAccessible(true);
        boolean originalTurbo=manualTurbo.getBoolean(session);
        Field bedrock=session.getClass().getDeclaredField("bedrock");
        bedrock.setAccessible(true);
        boolean originalBedrock=bedrock.getBoolean(session);
        FileConfiguration config=plugin.getConfig();
        Map<String,Object> originalConfig=new LinkedHashMap<>();
        // An explicit null default preserves missing keys rather than materializing defaults.
        for(String key:List.of(HORIZONTAL_KEY,VERTICAL_KEY,TURBO_KEY,PITCH_DEADZONE_KEY,PITCH_FULL_KEY))
            originalConfig.put(key,config.get(key,null));
        Fixture fixture=new Fixture(service,session,manualTurbo,bedrock,rider.getLocation());
        List<Group> groups=new ArrayList<>();
        try {
            config.set(HORIZONTAL_KEY,HORIZONTAL);
            config.set(VERTICAL_KEY,VERTICAL);
            config.set(TURBO_KEY,4.0);
            config.set(PITCH_DEADZONE_KEY,12);
            config.set(PITCH_FULL_KEY,60);

            Group strafe=group(groups,"left/right follow the rider at cardinal, diagonal and wrapped yaw");
            Group longitudinal=group(groups,"forward/backward follow the rider at every tested yaw");
            Group combinations=group(groups,"all direction combinations cancel opposites and normalize diagonals");
            for(boolean bedrockMode:new boolean[]{false,true}){
                fixture.mode(bedrockMode);
                for(float yaw:YAWS){
                    for(int keys:new int[]{LEFT,RIGHT})
                        strafe.vector(fixture.input(yaw,0,keys,false,false,false,false,false),
                            expectedHorizontal(yaw,keys,HORIZONTAL),"bedrock="+bedrockMode+" "+label(yaw,0,keys));
                    for(int keys:new int[]{FORWARD,BACKWARD})
                        longitudinal.vector(fixture.input(yaw,0,keys,false,false,false,false,false),
                            expectedHorizontal(yaw,keys,HORIZONTAL),"bedrock="+bedrockMode+" "+label(yaw,0,keys));
                    for(int keys=0;keys<16;keys++)
                        combinations.vector(fixture.input(yaw,0,keys,false,false,false,false,false),
                            expectedHorizontal(yaw,keys,HORIZONTAL),"bedrock="+bedrockMode+" "+label(yaw,0,keys));
                }
            }

            Group vertical=group(groups,"Java keeps 35-degree descent; Bedrock smoothly steers height from 12 to 60 degrees");
            float[] pitches={-90,-75,-60,Math.nextUp(-60.0f),-48,-36,-24,
                Math.nextDown(-12.0f),-12,Math.nextUp(-12.0f),0,
                Math.nextDown(12.0f),12,Math.nextUp(12.0f),24,
                Math.nextDown(35.0f),35,Math.nextUp(35.0f),36,48,
                Math.nextDown(60.0f),60,75,90};
            for(boolean bedrockMode:new boolean[]{false,true}){
                fixture.mode(bedrockMode);
                for(float pitch:pitches)for(int keys=0;keys<16;keys++)
                    for(boolean jump:new boolean[]{false,true})for(boolean sneak:new boolean[]{false,true}){
                        // Include backward/strafe-only pitch, cancelled forward/backward and sneak.
                        // Sneak dismount events are exercised by the lifecycle probe separately.
                        vertical.y(fixture.input(0,pitch,keys,jump,sneak,false,false,false),
                            expectedVertical(bedrockMode,pitch,keys,jump,VERTICAL),
                            "bedrock="+bedrockMode+" "+label(0,pitch,keys)+" jump="+jump+" sneak="+sneak);
                    }
            }

            Group hover=group(groups,"releasing movement hovers even when looking down, sneaking or using Turbo");
            for(boolean bedrockMode:new boolean[]{false,true}){
                fixture.mode(bedrockMode);
                for(float yaw:YAWS)for(float pitch:new float[]{-90,0,35,90})
                    for(int flags=0;flags<16;flags++){
                        boolean sneak=(flags&1)!=0,inputSprint=(flags&2)!=0;
                        boolean manual=(flags&4)!=0,serverSprint=(flags&8)!=0;
                        hover.vector(fixture.input(yaw,pitch,0,false,sneak,inputSprint,manual,serverSprint),
                            new Vector(),"bedrock="+bedrockMode+" "+label(yaw,pitch,0)+" flags="+flags);
                    }
            }

            Group turbo=group(groups,"manual, input Sprint and player Sprint independently enable Turbo without stacking");
            for(boolean bedrockMode:new boolean[]{false,true}){
                fixture.mode(bedrockMode);
                for(int flags=0;flags<8;flags++){
                    boolean manual=(flags&1)!=0,inputSprint=(flags&2)!=0,serverSprint=(flags&4)!=0;
                    double multiplier=flags==0?1:4;
                    for(float yaw:new float[]{0,45,90,180,270})for(int keys=0;keys<16;keys++)
                        for(int mode=0;mode<5;mode++){
                            boolean jump=mode==1;
                            float pitch=modePitch(mode);
                            Vector expected=expectedHorizontal(yaw,keys,HORIZONTAL*multiplier);
                            expected.setY(expectedVertical(bedrockMode,pitch,keys,jump,VERTICAL*multiplier));
                            turbo.vector(fixture.input(yaw,pitch,keys,jump,false,inputSprint,manual,serverSprint),
                                expected,"bedrock="+bedrockMode+" "+label(yaw,pitch,keys)+" jump="+jump+" turboFlags="+flags);
                        }
                }
            }

            Group analog=group(groups,"real Bedrock packets preserve stick proportions and control height, Jump and Sprint");
            Group stale=group(groups,"expired gamepad input stays neutral and Java ignores Bedrock bindings");
            analogChecks(service,fixture,analog,stale);

            Group clamps=group(groups,"configured speeds and Turbo multiplier honor lower/upper limits and combined caps");
            // Expected values are explicit boundary examples, independent of the production clamp expression.
            double[][] horizontalExamples={{-10,.03},{0,.03},{.02,.03},{.03,.03},
                {.2,.2},{.486,.486},{.6,.6},{.61,.6},{100,.6}};
            double[][] verticalExamples={{-10,.03},{0,.03},{.02,.03},{.03,.03},
                {.2,.2},{.288,.288},{.35,.35},{.36,.35},{100,.35}};
            double[][] turboExamples={{-10,1},{0,1},{.5,1},{1,1},{2.5,2.5},{4,4},{4.01,4},{100,4}};
            for(boolean bedrockMode:new boolean[]{false,true}){
                fixture.mode(bedrockMode);
                for(double[] horizontal:horizontalExamples)for(double[] up:verticalExamples)
                    for(double[] boost:turboExamples){
                        config.set(HORIZONTAL_KEY,horizontal[0]);
                        config.set(VERTICAL_KEY,up[0]);
                        config.set(TURBO_KEY,boost[0]);
                        for(boolean boosted:new boolean[]{false,true})for(int mode=0;mode<5;mode++){
                            double multiplier=boosted?boost[1]:1;
                            double horizontalSpeed=horizontal[1]*multiplier,verticalSpeed=up[1]*multiplier;
                            boolean jump=mode==1;
                            float pitch=modePitch(mode);
                            Vector expected=new Vector(0,
                                expectedVertical(bedrockMode,pitch,FORWARD,jump,verticalSpeed),horizontalSpeed);
                            clamps.vector(fixture.input(0,pitch,FORWARD,jump,false,false,boosted,false),expected,
                                "bedrock="+bedrockMode+" horizontal="+horizontal[0]+" vertical="+up[0]+" multiplier="+boost[0]+
                                    " turbo="+boosted+" mode="+mode);
                        }
                    }
            }
            config.set(HORIZONTAL_KEY,HORIZONTAL);
            config.set(VERTICAL_KEY,VERTICAL);
            config.set(TURBO_KEY,4.0);
            fixture.mode(true);
            // raw deadzone, raw full-speed angle, expected deadzone, expected full-speed angle
            double[][] pitchExamples={{-10,60,0,60},{0,60,0,60},{12,60,12,60},
                {45,60,45,60},{100,60,45,60},{12,-10,12,13},{12,0,12,13},
                {12,12,12,13},{12,13,12,13},{12,90,12,90},{12,100,12,90},
                {45,30,45,46},{45,100,45,90}};
            for(double[] angles:pitchExamples){
                config.set(PITCH_DEADZONE_KEY,angles[0]);config.set(PITCH_FULL_KEY,angles[1]);
                for(float pitch:new float[]{-90,(float)-angles[3],(float)-(angles[2]+angles[3])/2,
                    (float)-angles[2],0,(float)angles[2],(float)(angles[2]+angles[3])/2,(float)angles[3],90}){
                    double amount=Math.abs(pitch)<=angles[2]?0:
                        Math.abs(pitch)>=angles[3]?1:(Math.abs(pitch)-angles[2])/(angles[3]-angles[2]);
                    double expectedY=pitch<0?VERTICAL*amount:-VERTICAL*amount;
                    clamps.y(fixture.input(0,pitch,FORWARD,false,false,false,false,false),expectedY,
                        "pitch="+pitch+" configuredDeadzone="+angles[0]+" configuredFull="+angles[1]);
                }
            }
        } finally {
            for(Map.Entry<String,Object> entry:originalConfig.entrySet())config.set(entry.getKey(),entry.getValue());
            manualTurbo.setBoolean(session,originalTurbo);
            bedrock.setBoolean(session,originalBedrock);
        }
        Group restoration=group(groups,"control matrix restores the live session and configuration");
        restoration.condition(manualTurbo.getBoolean(session)==originalTurbo,"manual Turbo restored");
        restoration.condition(bedrock.getBoolean(session)==originalBedrock,"Bedrock session flag restored");
        for(Map.Entry<String,Object> entry:originalConfig.entrySet())
            restoration.condition(Objects.equals(config.get(entry.getKey(),null),entry.getValue()),entry.getKey()+" restored");
        // Compute every matrix before reporting, so a fail-fast consumer cannot skip calculation or cleanup.
        for(Group group:groups)check.accept(group.failures==0,group.summary());
    }

    private static Group group(List<Group> groups,String name){
        Group group=new Group(name);groups.add(group);return group;
    }

    private static Vector expectedHorizontal(float yaw,int keys,double speed){
        int ahead=((keys&FORWARD)!=0?1:0)-((keys&BACKWARD)!=0?1:0);
        int right=((keys&RIGHT)!=0?1:0)-((keys&LEFT)!=0?1:0);
        // Bukkit yaw increases when turning RIGHT. Obtain the right heading independently
        // by adding 90 degrees, rather than reproducing the service's perpendicular vector.
        double aheadYaw=Math.toRadians(yaw),rightYaw=Math.toRadians((double)yaw+90);
        double scale=speed/Math.max(1,Math.hypot(ahead,right));
        return new Vector((-Math.sin(aheadYaw)*ahead-Math.sin(rightYaw)*right)*scale,0,
            (Math.cos(aheadYaw)*ahead+Math.cos(rightYaw)*right)*scale);
    }

    private static String label(float yaw,float pitch,int keys){
        return "yaw="+yaw+" pitch="+pitch+" directions="+Integer.toBinaryString(keys);
    }

    private static float modePitch(int mode){
        return switch(mode){case 2 -> 45;case 3 -> -45;case 4 -> 12;default -> 0;};
    }

    private static double expectedVertical(boolean bedrock,float pitch,int keys,boolean jump,double speed){
        if(jump)return speed;
        int netForward=((keys&FORWARD)!=0?1:0)-((keys&BACKWARD)!=0?1:0);
        if(netForward<=0)return 0;
        if(!bedrock)return pitch>35?-speed:0;
        // Flight contract: level within +/-12 degrees, half speed at +/-36,
        // and full speed at/beyond +/-60. The same magnitude applies up and down.
        double angle=Math.abs(pitch);
        if(angle<=12)return 0;
        double fraction=angle>=60?1:(angle-12)/48;
        return pitch<0?speed*fraction:-speed*fraction;
    }

    private static void analogChecks(Object service,Fixture fixture,Group analog,Group stale) throws Exception {
        Field bridgeField=service.getClass().getDeclaredField("bedrockInput");bridgeField.setAccessible(true);
        Object bridge=bridgeField.get(service);
        Method bind=Class.forName("GeyserGamepadChecks").getMethod("bindAnalog",bridge.getClass(),
            UUID.class,double.class,double.class,boolean.class,boolean.class,long.class);
        // raw left/forward followed by independently expected post-deadzone left/forward.
        // A raw .575 vector has half magnitude after the .15 radial deadzone; .3625 is quarter.
        double q=Math.sqrt(.5);
        double[][] examples={{0,0,0,0},{0,.149,0,0},{0,.15,0,0},
            {0,.3625,0,.25},{0,.575,0,.5},{0,1,0,1},{0,-.575,0,-.5},
            {.575,0,.5,0},{-.575,0,-.5,0},{1,0,1,0},{-1,0,-1,0},
            {.345,.46,.3,.4},{-.345,.46,-.3,.4},{q,q,q,q}};
        fixture.mode(true);
        for(double[] example:examples)for(int flags=0;flags<4;flags++){
            boolean jump=(flags&1)!=0,sprint=(flags&2)!=0;
            try(AutoCloseable binding=bind(bind,bridge,fixture.proxyId,example[0],example[1],jump,sprint,0)){
                for(float yaw:YAWS)for(float pitch:new float[]{-60,-36,-12,0,12,36,60})
                    for(boolean manual:new boolean[]{false,true}){
                        double multiplier=manual||sprint?4:1;
                        Vector expected=expectedAnalogHorizontal(yaw,example[2],example[3],HORIZONTAL*multiplier);
                        double vertical=jump?VERTICAL*multiplier:
                            expectedVertical(true,pitch,FORWARD,false,VERTICAL*multiplier)*Math.max(0,example[3]);
                        expected.setY(vertical);
                        // Deliberately contradictory Java buttons prove the raw snapshot wins.
                        Vector actual=fixture.input(yaw,pitch,FORWARD|RIGHT,!jump,false,!sprint,manual,!sprint);
                        analog.vector(actual,expected,"raw="+example[0]+","+example[1]+" yaw="+yaw+
                            " pitch="+pitch+" jump="+jump+" sprint="+sprint+" manual="+manual,1e-6);
                    }
                for(boolean manual:new boolean[]{false,true}){
                    fixture.input(0,0,0,false,false,!sprint,manual,!sprint);
                    analog.condition(fixture.turbo()==(manual||sprint),
                        "raw Sprint overrides stale Java Sprint; sprint="+sprint+" manual="+manual);
                }
            }
        }
        try(AutoCloseable binding=bind(bind,bridge,fixture.proxyId,.345,.46,true,true,1_000_000_000L)){
            for(float pitch:new float[]{-90,0,90}){
                stale.vector(fixture.input(0,pitch,FORWARD|RIGHT,true,false,true,false,true),new Vector(),
                    "expired raw stick/Jump ignores held Java movement at pitch="+pitch);
                stale.condition(!fixture.turbo(),"expired raw Sprint ignores held Java Sprint");
            }
            fixture.input(0,0,FORWARD,true,false,true,true,true);
            stale.condition(fixture.turbo(),"manual Turbo remains an intentional override when raw input expires");
        }
        try(AutoCloseable binding=bind(bind,bridge,fixture.proxyId,1,-1,false,false,0)){
            fixture.mode(false);
            Vector expected=expectedHorizontal(90,FORWARD|RIGHT,HORIZONTAL*4);
            expected.setY(VERTICAL*4);
            stale.vector(fixture.input(90,0,FORWARD|RIGHT,true,false,true,false,true),expected,
                "Java uses its own digital movement/Jump/Sprint even with a matching Bedrock binding");
        }
    }

    private static AutoCloseable bind(Method bind,Object bridge,UUID owner,double left,double forward,
                                      boolean jump,boolean sprint,long age) throws Exception {
        try{return (AutoCloseable)bind.invoke(null,bridge,owner,left,forward,jump,sprint,age);}
        catch(InvocationTargetException error){
            if(error.getCause() instanceof Exception cause)throw cause;
            if(error.getCause() instanceof Error cause)throw cause;
            throw new IllegalStateException(error.getCause());
        }
    }

    private static Vector expectedAnalogHorizontal(float yaw,double left,double forward,double speed){
        double aheadYaw=Math.toRadians(yaw),rightYaw=Math.toRadians((double)yaw+90);
        return new Vector((-Math.sin(aheadYaw)*forward+Math.sin(rightYaw)*left)*speed,0,
            (Math.cos(aheadYaw)*forward-Math.cos(rightYaw)*left)*speed);
    }

    private static final class Fixture {
        final Object service,session,controls;
        final Method input,turbo;
        final Field manualTurbo,bedrock;
        final Location position;
        final Player player;
        final UUID proxyId=UUID.randomUUID();
        int keys;
        boolean jump,sneak,inputSprint,serverSprint;

        Fixture(Object service,Object session,Field manualTurbo,Field bedrock,Location origin) throws Exception {
            this.service=service;this.session=session;this.manualTurbo=manualTurbo;this.bedrock=bedrock;position=origin.clone();
            input=service.getClass().getDeclaredMethod("input",Player.class,session.getClass());
            input.setAccessible(true);
            turbo=service.getClass().getDeclaredMethod("turbo",Player.class,session.getClass());
            turbo.setAccessible(true);
            Class<?> inputType=Player.class.getMethod("getCurrentInput").getReturnType();
            if(!inputType.isInterface())throw new IllegalStateException("Paper Input must be an interface");
            controls=Proxy.newProxyInstance(inputType.getClassLoader(),new Class<?>[]{inputType},(proxy,method,args)->{
                if(method.getDeclaringClass()==Object.class)return objectMethod(proxy,method,args);
                return switch(method.getName()){
                    case "isForward" -> (keys&FORWARD)!=0;
                    case "isBackward" -> (keys&BACKWARD)!=0;
                    case "isLeft" -> (keys&LEFT)!=0;
                    case "isRight" -> (keys&RIGHT)!=0;
                    case "isJump" -> jump;
                    case "isSneak" -> sneak;
                    case "isSprint" -> inputSprint;
                    default -> throw new UnsupportedOperationException("Unexpected Input method: "+method);
                };
            });
            player=(Player)Proxy.newProxyInstance(Player.class.getClassLoader(),new Class<?>[]{Player.class},(proxy,method,args)->{
                if(method.getDeclaringClass()==Object.class)return objectMethod(proxy,method,args);
                return switch(method.getName()){
                    case "getCurrentInput" -> controls;
                    // This UUID has no live Geyser binding, exercising the digital fallback.
                    case "getUniqueId" -> proxyId;
                    case "getLocation" -> position.clone();
                    case "isSprinting" -> serverSprint;
                    default -> throw new UnsupportedOperationException("Unexpected Player method: "+method);
                };
            });
        }

        void mode(boolean bedrockMode) throws IllegalAccessException {bedrock.setBoolean(session,bedrockMode);}

        boolean turbo() throws Exception {return (boolean)turbo.invoke(service,player,session);}

        Vector input(float yaw,float pitch,int keys,boolean jump,boolean sneak,
                     boolean inputSprint,boolean manual,boolean serverSprint) throws Exception {
            position.setYaw(yaw);position.setPitch(pitch);this.keys=keys;this.jump=jump;
            this.sneak=sneak;this.inputSprint=inputSprint;this.serverSprint=serverSprint;
            manualTurbo.setBoolean(session,manual);
            try{return (Vector)input.invoke(service,player,session);}
            catch(InvocationTargetException error){
                if(error.getCause() instanceof Exception cause)throw cause;
                if(error.getCause() instanceof Error cause)throw cause;
                throw new IllegalStateException(error.getCause());
            }
        }
    }

    private static Object objectMethod(Object proxy,Method method,Object[] args){
        return switch(method.getName()){
            case "equals" -> proxy==args[0];
            case "hashCode" -> System.identityHashCode(proxy);
            case "toString" -> "FlyingStaffControlChecks proxy";
            default -> throw new UnsupportedOperationException(method.toString());
        };
    }

    private static final class Group {
        final String name;
        final List<String> examples=new ArrayList<>();
        int cases,failures;
        Group(String name){this.name=name;}
        void vector(Vector actual,Vector expected,String label){
            vector(actual,expected,label,EPSILON);
        }
        void vector(Vector actual,Vector expected,String label,double tolerance){
            condition(finite(actual)&&actual.distanceSquared(expected)<=tolerance*tolerance,
                label+" expected="+format(expected)+" actual="+format(actual));
        }
        void y(Vector actual,double expected,String label){
            condition(finite(actual)&&Math.abs(actual.getY()-expected)<=EPSILON,
                label+" expectedY="+expected+" actualY="+actual.getY());
        }
        void condition(boolean passed,String label){
            cases++;
            if(!passed){failures++;if(examples.size()<3)examples.add(label);}
        }
        String summary(){
            return name+" ("+cases+" cases"+(failures==0?"":", "+failures+" failed: "+String.join("; ",examples))+")";
        }
        static boolean finite(Vector vector){
            return Double.isFinite(vector.getX())&&Double.isFinite(vector.getY())&&Double.isFinite(vector.getZ());
        }
        static String format(Vector vector){
            return String.format(Locale.ROOT,"(%.6f,%.6f,%.6f)",vector.getX(),vector.getY(),vector.getZ());
        }
    }
}
