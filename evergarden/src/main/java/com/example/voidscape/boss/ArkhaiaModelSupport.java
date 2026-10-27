package com.example.voidscape.boss;

import com.example.voidscape.VoidscapePlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Slime;
import org.bukkit.entity.Mob;
import java.util.Set;
import java.util.UUID;

/** Optional visual backend; the combat and Bedrock bridge do not depend on ME. */
public final class ArkhaiaModelSupport implements AutoCloseable {
    public interface Rig extends AutoCloseable {
        void frame(ArkhaiaBrain.Frame frame,double speed,boolean airborne,Location at);
        default void look(float yaw,float pitch){}
        default void clip(String name,double rate,Location at){}
        void viewers(Set<UUID> acceptedJavaPlayers);
        Location swordTip();
        default Location boneOrigin(String name){return null;}
        @Override void close();
    }
    interface Backend extends AutoCloseable {
        boolean ready();
        boolean ready(String model);
        String modelInfo();
        Rig attach(Slime target);
        Rig attachRiver(Mob target,String model);
        Rig attachBoss(Mob target,String model,double scale);
        @Override void close();
    }
    private Backend backend;
    public ArkhaiaModelSupport(VoidscapePlugin plugin){
        if(!plugin.getConfig().getBoolean("arkhaia.model-engine.enabled",true)
            ||!Bukkit.getPluginManager().isPluginEnabled("ModelEngine"))return;
        try{backend=new ModelEngineArkhaia(plugin);}
        catch(Exception|LinkageError error){plugin.getLogger().log(java.util.logging.Level.WARNING,
            "Arkhaia Model Engine backend unavailable; retaining compatible poses",error);}
    }
    /** Every managed blueprint imported (boss, showcase and all river minions). */
    public boolean ready(){return backend!=null&&backend.ready();}
    /** This blueprint imported and the merged pack is published. */
    public boolean ready(String model){return backend!=null&&backend.ready(model);}
    public String modelInfo(){return backend==null?"Model Engine backend unavailable":backend.modelInfo();}
    public Rig attach(Slime target){return ready("arkhaia")?backend.attach(target):null;}
    public Rig attachRiver(Mob target,String model){return ready(model)?backend.attachRiver(target,model):null;}
    /** Main-boss rig (Arkhaia or Seraphon) on any mob anchor, with its aura lane running. */
    public Rig attachBoss(Mob target,String model,double scale){return ready(model)?backend.attachBoss(target,model,scale):null;}
    public static String clip(ArkhaiaBrain.Frame frame,double speed,boolean airborne){
        return switch(frame.state()){
            case WINDUP,ACTIVE -> frame.action().name().toLowerCase(java.util.Locale.ROOT)+"_"+frame.state().name().toLowerCase(java.util.Locale.ROOT);
            case RECOVER -> frame.action()==ArkhaiaBrain.Action.NONE?"idle":frame.action().name().toLowerCase(java.util.Locale.ROOT)+"_recover";
            case STUN -> "stun";
            case TRANSITION -> "transition";
            case BACKSTEP,RETREAT -> "backstep";
            default -> airborne?"hover":speed>.008?"walk":"idle";
        };
    }
    @Override public void close(){if(backend!=null){backend.close();backend=null;}}
}
