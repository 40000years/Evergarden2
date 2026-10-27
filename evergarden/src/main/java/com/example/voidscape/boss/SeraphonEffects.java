package com.example.voidscape.boss;

import com.example.voidscape.VoidscapePlugin;
import org.bukkit.*;
import org.bukkit.util.Vector;

/** Golden seals reuse the archived Heaven's Judgment rune artwork. */
final class SeraphonEffects implements AutoCloseable {
    static final Color GOLD=Color.fromRGB(255,218,102),CYAN=Color.fromRGB(112,235,255);
    private final JudgeAether aether;final JudgeSigils sigils;
    SeraphonEffects(VoidscapePlugin plugin){aether=new JudgeAether(plugin);sigils=new JudgeSigils(plugin,aether);}
    void frame(Location center){
        double range=140;
        for(var p:center.getWorld().getPlayers())if(Math.hypot(p.getX()-center.getX(),p.getZ()-center.getZ())<110)
            range=Math.max(range,p.getLocation().distance(center)+24);
        aether.frame(center,range);
    }
    void circle(String key,Location at,double radius,double turn,boolean safe){sigils.circle(key,safe?"seraphon_sanctuary":"seraphon_seal",at,radius,turn,false);}
    void beam(String key,Location from,Location to,double width){sigils.fastRay(key,"seraphon_ray",from,to,width);}
    void feather(String key,Location from,Location to){sigils.fastRay(key,"seraphon_feather",from,to,.55);}
    void remove(String key){sigils.remove(key);}
    void pulse(Location at,double radius,long age,boolean safe){
        Color color=safe?CYAN:GOLD;int count=72;
        for(int i=0;i<count;i++){double a=i*Math.PI*2/count;aether.dust(at.clone().add(Math.cos(a)*radius,.12,Math.sin(a)*radius),color,1.8f);}
        at.getWorld().spawnParticle(Particle.END_ROD,at.clone().add(0,.6,0),18,radius*.35,.4,radius*.35,.025);
    }
    void halo(String key,Location at,double radius,double angle,boolean tilted){
        sigils.plane(key,"seraphon_orbit",at,new Vector(1,0,0),tilted?new Vector(0,.5,Math.sqrt(.75)):new Vector(0,0,1),radius*2.14,radius*2.14,angle);
    }
    void sky(Location center,double y,long age,int phase){
        Location sky=center.clone();sky.setY(y);
        circle("attack:heaven:seal",sky,38,age*.009,false);
        halo("attack:heaven:orbit1",sky.clone().add(0,1,0),43,-age*.013,true);
        halo("attack:heaven:orbit2",sky.clone().add(0,3,0),32,age*.018,true);
        if(phase==3)halo("attack:heaven:crown",sky.clone().add(0,6,0),47,-age*.006,false);
    }
    void ambient(Location center,Location body,long tick,int phase,boolean fighting){
        if(tick%4!=0)return;
        halo("ambient:halo",body.clone().add(0,.18,0),fighting?4.5:3.4,tick*.008,false);
        if(fighting){halo("ambient:arena",center.clone().add(0,.08,0),SeraphonRules.RADIUS,tick*.0015,false);
            if(phase>1)halo("ambient:armillary",center.clone().add(0,19,0),phase==3?43:32,-tick*.003,true);}
    }
    /** Upright sun mirror turned toward the guardian, so the reflecting face is the one players hide behind. */
    void mirror(String key,Location at,Location toward,long tick){
        Vector normal=toward.toVector().subtract(at.toVector()).setY(0);if(normal.lengthSquared()<.01)normal=new Vector(0,0,1);normal.normalize();
        Vector right=new Vector(-normal.getZ(),0,normal.getX()),up=new Vector(0,1,0);double size=SeraphonRules.MIRROR_RADIUS*2.2;
        sigils.plane(key,"seraphon_sanctuary",at,right,up,size,size,tick*.01);
        sigils.plane(key+":rim","seraphon_orbit",at,right,up,size*1.2,size*1.2,-tick*.02);
        if(tick%8==0)aether.dust(at.clone().add(0,SeraphonRules.MIRROR_RADIUS+.4,0),CYAN,1.6f);
    }
    /** Ophanim: two crossed golden rings turning in place. */
    void wheel(String key,Location at,long tick){
        double a=tick*.05;
        sigils.plane(key+":a","seraphon_orbit",at,new Vector(Math.cos(a),0,Math.sin(a)),new Vector(0,1,0),5.2,5.2,tick*.08);
        sigils.plane(key+":b","seraphon_orbit",at,new Vector(-Math.sin(a),0,Math.cos(a)),new Vector(0,1,0),4.4,4.4,-tick*.1);
        sigils.plane(key+":c","seraphon_seal",at,new Vector(1,0,0),new Vector(0,0,1),2.6,2.6,tick*.05);
    }
    /** The wheel's floor shadow; the filled arc shows how close it is to shattering. */
    void shadow(String key,Location at,double radius,double progress,long tick){
        sigils.circle(key,"seraphon_sanctuary",at,radius,tick*.02,false);
        int samples=(int)Math.clamp(progress*40,0,40);
        for(int i=0;i<samples;i++){double a=i*Math.PI*2/40;aether.dust(at.clone().add(Math.cos(a)*(radius+.3),.2,Math.sin(a)*(radius+.3)),CYAN,1.5f);}
    }
    void clearAttack(){sigils.clearAttack();}
    void removePrefix(String prefix){sigils.removePrefix(prefix);}
    void finishCast(){for(String prefix:new String[]{"attack:gate:","attack:heaven:","attack:safe:","attack:orbit:","attack:transition","attack:solar","attack:wheel","attack:shelter"})sigils.removePrefix(prefix);}
    @Override public void close(){sigils.close();}
}
