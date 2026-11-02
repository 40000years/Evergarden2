package com.example.voidscape.boss;

import com.example.voidscape.VoidscapePlugin;
import org.bukkit.*;
import org.bukkit.util.Vector;
import java.util.*;

/** Ambient aura for the main-boss rigs, anchored to their invisible emitter bones.
 *  Colored glints go through JudgeAether (Geyser drops vanilla dust tint); the rest
 *  are vanilla particles that both clients render. Evaluated every other tick. */
final class BossAura implements AutoCloseable {
    enum Style {
        ARKHAIA("arkhaia"), SERAPHON("seraphon"), COLOSSUS("cinder_colossus"), HOUND("soul_hound"), MOTH("crystal_moth"), SUN("unlit_sun");
        final String model;
        Style(String model){this.model=model;}
    }
    private static final Color GHOST=Color.fromRGB(176,96,255),EMBER=Color.fromRGB(226,40,80),CORE=Color.fromRGB(236,200,255),
        GOLD=Color.fromRGB(255,214,120),SKY=Color.fromRGB(127,227,255),MOLTEN=Color.fromRGB(255,140,30),HEART=Color.fromRGB(255,95,127),
        SOULFIRE=Color.fromRGB(74,216,242),LILAC=Color.fromRGB(196,150,255),ROSE=Color.fromRGB(240,110,210),VOID=Color.fromRGB(154,79,240);
    private static final Set<String> ARKHAIA=Set.of("flame_r","flame_l","fx_core","fx_eyes","fx_blade","sword_tip",
        "fx_wing_r","fx_wing_l","fx_fan_r","fx_fan_l","dread_halo");
    private static final Set<String> SERAPHON=Set.of("halo","orb","fx_core","fx_palm","fx_upper_r","fx_upper_l",
        "fx_middle_r","fx_middle_l","fx_lower_r","fx_lower_l");
    private static final Set<String> COLOSSUS=Set.of("fx_core","fx_eye","fx_crown","fx_mouth","fx_fist_r","fx_fist_l");
    private static final Set<String> HOUND=Set.of("fx_eyes","fx_mouth","fx_heart","fx_tail","fx_paw_r","fx_paw_l");
    private static final Set<String> MOTH=Set.of("fx_heart","fx_eyes","fx_stinger","fx_wing_r","fx_wing_l");
    private static final Set<String> SUN=Set.of("fx_eye","fx_core","fx_corona");
    private final JudgeAether aether;
    private final Style style;
    private final Random random=new Random();
    BossAura(VoidscapePlugin plugin,Style style){aether=new JudgeAether(plugin);this.style=style;}

    /** at: model origin and body yaw; clip/time: the playing clip; scale: model scale. */
    void frame(Location at,String clip,double time,long tick,double scale,boolean intense){
        if(tick%2!=0||at.getWorld()==null)return;
        aether.frame(at,64);
        Set<String> markers=switch(style){case ARKHAIA->ARKHAIA;case SERAPHON->SERAPHON;case COLOSSUS->COLOSSUS;case HOUND->HOUND;case MOTH->MOTH;case SUN->SUN;};
        var points=BossHitbox.bonePoints(style.model,clip,time,at,scale,markers);
        switch(style){
            case ARKHAIA->arkhaia(at,points,tick,scale,intense);
            case SERAPHON->seraphon(at,points,tick,scale,intense);
            case COLOSSUS->colossus(at,points,clip,tick,scale,intense);
            case HOUND->hound(at,points,tick,intense);
            case MOTH->moth(at,points,tick,intense);
            case SUN->sun(at,points,clip,tick,scale,intense);
        }
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
    /** Cinder Colossus: lava dripping from the fists, flame boiling off the crown, the heartcrystal flaring when bared. */
    private void colossus(Location at,Map<String,Location> p,String clip,long tick,double scale,boolean intense){
        World world=at.getWorld();
        for(String key:List.of("fx_fist_r","fx_fist_l")){
            Location fist=p.get(key);if(fist==null)continue;
            if(tick%4==0)world.spawnParticle(Particle.DRIPPING_LAVA,jitter(fist,.5,.2),1,0,0,0,0);
            if(intense&&tick%4==0)world.spawnParticle(Particle.LAVA,fist,2,.4,.2,.4,0);
        }
        Location crown=p.get("fx_crown");
        if(crown!=null){
            world.spawnParticle(Particle.FLAME,jitter(crown,.9,.3),intense?3:1,.05,.08,.05,.01);
            if(tick%8==0)world.spawnParticle(Particle.LARGE_SMOKE,crown.clone().add(0,.8,0),1,.25,.1,.25,.01);
        }
        Location eye=p.get("fx_eye");
        if(eye!=null&&tick%6==0)aether.dust(eye,MOLTEN,1.5f);
        Location mouth=p.get("fx_mouth");
        if(mouth!=null&&intense&&tick%4==0)world.spawnParticle(Particle.FLAME,mouth,2,.2,.1,.2,.02);
        Location core=p.get("fx_core");
        if(core!=null&&clip.startsWith("ultimate")&&tick%2==0){aether.dust(jitter(core,.7,.7),HEART,2.0f);world.spawnParticle(Particle.FLAME,core,1,.3,.3,.3,.01);}
        if(tick%6==0){
            double a=random.nextDouble()*Math.PI*2,r=(3.4+random.nextDouble()*2.4)*scale/3.6;
            world.spawnParticle(Particle.LAVA,at.clone().add(Math.cos(a)*r,.15,Math.sin(a)*r),1,0,0,0,0);
        }
        if(tick%10==0)world.spawnParticle(Particle.ASH,at.clone().add(0,4*scale/3.6,0),6,2.4,2.4,2.4,0);
    }
    /** Soul-Eater Hound: soul fire on every paw, the heart flickering through the ribs, eyes trailing light. */
    private void hound(Location at,Map<String,Location> p,long tick,boolean intense){
        World world=at.getWorld();
        for(String key:List.of("fx_paw_r","fx_paw_l")){
            Location paw=p.get(key);if(paw!=null&&tick%3==0)world.spawnParticle(Particle.SOUL_FIRE_FLAME,jitter(paw,.3,.2),1,.02,.04,.02,.01);
        }
        Location heart=p.get("fx_heart");
        if(heart!=null){
            if(tick%4==0)aether.dust(jitter(heart,.5,.5),SOULFIRE,1.6f);
            if(tick%8==0||intense)world.spawnParticle(Particle.SOUL_FIRE_FLAME,jitter(heart,.4,.3),1,.03,.06,.03,.01);
        }
        Location eyes=p.get("fx_eyes");
        if(eyes!=null&&tick%6==0)aether.dust(eyes,SOULFIRE,1.1f);
        Location mouth=p.get("fx_mouth");
        if(mouth!=null&&(intense||tick%10==0))world.spawnParticle(Particle.SOUL,mouth,1,.2,.1,.2,.02);
        Location tail=p.get("fx_tail");
        if(tail!=null&&tick%4==0)world.spawnParticle(Particle.SOUL_FIRE_FLAME,tail,1,.1,.1,.1,.01);
        if(tick%8==0){
            double a=random.nextDouble()*Math.PI*2,r=2+random.nextDouble()*2.5;
            world.spawnParticle(Particle.SOUL,at.clone().add(Math.cos(a)*r,.2,Math.sin(a)*r),1,0,.05,0,.01);
        }
    }
    /** Crystal Moth Queen: glittering scales shed from the wingtips, a pulse from the heartcrystal. */
    private void moth(Location at,Map<String,Location> p,long tick,boolean intense){
        World world=at.getWorld();
        for(String key:List.of("fx_wing_r","fx_wing_l")){
            Location tip=p.get(key);if(tip==null)continue;
            if(tick%(intense?2:4)==0)world.spawnParticle(Particle.END_ROD,jitter(tip,.6,.6),1,0,-.02,0,.01);
            if(tick%6==0)aether.dust(jitter(tip,.8,.8),LILAC,1.4f);
        }
        Location heart=p.get("fx_heart");
        if(heart!=null&&tick%4==0)aether.dust(jitter(heart,.4,.4),ROSE,1.6f);
        Location eyes=p.get("fx_eyes");
        if(eyes!=null&&tick%8==0)aether.dust(eyes,LILAC,1.0f);
        Location stinger=p.get("fx_stinger");
        if(stinger!=null&&tick%6==0)world.spawnParticle(Particle.END_ROD,stinger,1,.05,.05,.05,0);
        if(tick%10==0)world.spawnParticle(Particle.REVERSE_PORTAL,at.clone().add(0,.3,0),4,2.2,.1,2.2,.01);
    }
    /** Unlit Sun: light crawling around the corona, a gold glare from the opened eye, void drifting below. */
    private void sun(Location at,Map<String,Location> p,String clip,long tick,double scale,boolean intense){
        World world=at.getWorld();
        Location core=p.get("fx_core");
        if(core!=null){
            Vector right=right(at.getYaw());double r=16.5*scale/16,a=(tick*.05)%(Math.PI*2);
            for(int k=0;k<(intense?4:2);k++){
                double angle=a+k*Math.PI*2/(intense?4:2);
                world.spawnParticle(Particle.END_ROD,core.clone().add(right.clone().multiply(Math.cos(angle)*r)).add(0,Math.sin(angle)*r,0),1,0,0,0,0);
            }
            if(tick%4==0)world.spawnParticle(Particle.REVERSE_PORTAL,core,3,1.6,1.6,1.6,.02);
            if(clip.startsWith("ultimate")&&tick%2==0)aether.dust(jitter(core,1.4,1.4),GOLD,2.4f);
            if(tick%6==0)world.spawnParticle(Particle.PORTAL,core.clone().add(0,-14*scale/16,0),4,.6,.4,.6,.2);
        }
        Location eye=p.get("fx_eye");
        if(eye!=null&&(clip.startsWith("attack")||clip.startsWith("ultimate"))&&tick%2==0)aether.dust(jitter(eye,.3,.6),GOLD,1.8f);
        if(tick%8==0)aether.dust(jitter(at.clone().add(0,.2,0),2.6,.1),VOID,1.0f);
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
