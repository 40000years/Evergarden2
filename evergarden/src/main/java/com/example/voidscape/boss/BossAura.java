package com.example.voidscape.boss;

import com.example.voidscape.VoidscapePlugin;
import org.bukkit.*;
import org.bukkit.util.Vector;
import java.util.*;

/** Ambient aura for the main-boss rigs, anchored to their invisible emitter bones.
 *  Colored glints go through JudgeAether (Geyser drops vanilla dust tint); the rest
 *  are vanilla particles that both clients render. Evaluated every other tick. */
final class BossAura implements AutoCloseable {
    enum Style { ARKHAIA, SERAPHON }
    private static final Color GHOST=Color.fromRGB(176,96,255),EMBER=Color.fromRGB(226,40,80),CORE=Color.fromRGB(236,200,255),
        GOLD=Color.fromRGB(255,214,120),SKY=Color.fromRGB(127,227,255);
    private static final Set<String> ARKHAIA=Set.of("flame_r","flame_l","fx_core","fx_eyes","fx_blade","sword_tip",
        "fx_wing_r","fx_wing_l","fx_fan_r","fx_fan_l","dread_halo");
    private static final Set<String> SERAPHON=Set.of("halo","orb","fx_core","fx_palm","fx_upper_r","fx_upper_l",
        "fx_middle_r","fx_middle_l","fx_lower_r","fx_lower_l");
    private final JudgeAether aether;
    private final Style style;
    private final Random random=new Random();
    BossAura(VoidscapePlugin plugin,Style style){aether=new JudgeAether(plugin);this.style=style;}

    /** at: model origin and body yaw; clip/time: the playing clip; scale: model scale. */
    void frame(Location at,String clip,double time,long tick,double scale,boolean intense){
        if(tick%2!=0||at.getWorld()==null)return;
        aether.frame(at,64);
        var points=BossHitbox.bonePoints(style==Style.ARKHAIA?"arkhaia":"seraphon",clip,time,at,scale,style==Style.ARKHAIA?ARKHAIA:SERAPHON);
        if(style==Style.ARKHAIA)arkhaia(at,points,tick,scale,intense);else seraphon(at,points,tick,scale,intense);
    }
    private void arkhaia(Location at,Map<String,Location> p,long tick,double scale,boolean intense){
        World world=at.getWorld();
        for(String side:List.of("r","l")){
            Location vent=p.get("flame_"+side);if(vent==null)continue;
            // Ghostflame rising from the pauldron vents.
            world.spawnParticle(Particle.REVERSE_PORTAL,vent.clone().add(0,.45,0),intense?4:2,.22,.3,.22,.02);
            if(tick%4==0)aether.dust(jitter(vent,.35,.7).add(0,.4,0),GHOST,1.7f);
        }
        Location blade=p.get("fx_blade"),tip=p.get("sword_tip");
        if(blade!=null&&tip!=null&&tick%4==0)aether.dust(lerp(blade,tip,random.nextDouble()),GHOST,1.1f);
        if(tick%6==0)for(String key:List.of("fx_wing_r","fx_wing_l","fx_fan_r","fx_fan_l")){
            Location ember=p.get(key);if(ember!=null&&random.nextInt(intense?1:3)==0)aether.dust(jitter(ember,.5,.5),EMBER,1.3f);
        }
        Location core=p.get("fx_core");
        if(core!=null&&tick%8==0)aether.dust(core,CORE,2.2f);
        Location halo=p.get("dread_halo");
        if(halo!=null&&tick%6==0){
            Vector right=right(at.getYaw());double a=random.nextDouble()*Math.PI*2,r=6.3*scale/16;
            Location rim=halo.clone().add(right.multiply(Math.cos(a)*r)).add(0,Math.sin(a)*r,0);
            world.spawnParticle(Particle.WITCH,rim,1,.05,.05,.05,0);
        }
        if(tick%10==0){
            double a=random.nextDouble()*Math.PI*2,r=.8+random.nextDouble()*1.6;
            world.spawnParticle(Particle.LARGE_SMOKE,at.clone().add(Math.cos(a)*r,.15,Math.sin(a)*r),1,.1,0,.1,.005);
        }
    }
    private void seraphon(Location at,Map<String,Location> p,long tick,double scale,boolean intense){
        World world=at.getWorld();
        Location halo=p.get("halo");
        if(halo!=null){
            // Light running around the sun halo.
            Vector right=right(at.getYaw());double a=(tick*.09)%(Math.PI*2),r=6.0*scale/16;
            for(int k=0;k<(intense?3:1);k++){
                double angle=a+k*Math.PI*2/3;
                world.spawnParticle(Particle.END_ROD,halo.clone().add(right.clone().multiply(Math.cos(angle)*r)).add(0,Math.sin(angle)*r,0),1,0,0,0,0);
            }
        }
        Location core=p.get("fx_core");
        if(core!=null&&tick%4==0)aether.dust(jitter(core,1.0,1.6),GOLD,1.2f);
        Location orb=p.get("orb");
        if(orb!=null&&tick%4==0){aether.dust(jitter(orb,.3,.3),GOLD,1.4f);world.spawnParticle(Particle.WAX_ON,orb,1,.25,.25,.25,0);}
        Location palm=p.get("fx_palm");
        if(palm!=null&&intense&&tick%2==0)world.spawnParticle(Particle.ELECTRIC_SPARK,palm,2,.2,.2,.2,.02);
        if(tick%(intense?4:8)==0){
            // Feathers shed from the six wingtips drift down.
            List<String> tips=List.of("fx_upper_r","fx_upper_l","fx_middle_r","fx_middle_l","fx_lower_r","fx_lower_l");
            Location feather=p.get(tips.get(random.nextInt(tips.size())));
            if(feather!=null)world.spawnParticle(Particle.SNOWFLAKE,jitter(feather,.4,.4),1,.05,.05,.05,.01);
        }
        if(tick%8==0)aether.dust(jitter(at.clone().add(0,.2,0),2.4,.1),SKY,1.0f);
        if(tick%10==0){
            double a=random.nextDouble()*Math.PI*2,r=6.4*scale/16;
            world.spawnParticle(Particle.WAX_ON,at.clone().add(Math.cos(a)*r,.2,Math.sin(a)*r),1,0,.05,0,0);
        }
    }
    private Location jitter(Location at,double horizontal,double vertical){
        return at.clone().add((random.nextDouble()-.5)*2*horizontal,(random.nextDouble()-.5)*2*vertical,(random.nextDouble()-.5)*2*horizontal);
    }
    private static Location lerp(Location a,Location b,double t){
        return a.clone().add(b.toVector().subtract(a.toVector()).multiply(t));
    }
    /** Body right vector; the native front (-Z) faces Bukkit forward at this yaw. */
    private static Vector right(float yaw){double r=Math.toRadians(yaw);return new Vector(-Math.cos(r),0,-Math.sin(r));}
    @Override public void close(){}
}
