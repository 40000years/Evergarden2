package com.example.voidscape.boss;

import com.example.voidscape.VoidscapePlugin;
import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.util.Vector;
import java.util.*;

/** Reused textured planes on Java and matching world-space emitters on Bedrock; one render pass every four ticks. */
final class ArkhaiaEffects implements AutoCloseable {
    static final Color VIOLET=Color.fromRGB(175,90,255),GOLD=Color.fromRGB(255,206,84),CRIMSON=Color.fromRGB(255,48,93);
    private final JudgeAether aether;
    private final JudgeSigils sigils;
    private final Set<String> previous=new HashSet<>(),used=new HashSet<>();
    private Location lastTip;
    private World world;
    ArkhaiaEffects(VoidscapePlugin plugin){aether=new JudgeAether(plugin);sigils=new JudgeSigils(plugin,aether);}
    boolean owns(Entity entity){return sigils.owns(entity);}
    /** Starts a render pass; planes not drawn again before {@link #end()} are removed. */
    boolean begin(Location body,long tick){
        world=body.getWorld();if(tick%4!=0)return false;
        aether.frame(body,90);used.clear();return true;
    }
    void end(){for(String key:previous)if(!used.contains(key))sigils.remove(key);previous.clear();previous.addAll(used);}
    void circle(String key,String model,Location at,double radius,double angle,boolean upright){used.add(key);sigils.circle(key,model,at,radius,angle,upright);}
    void plane(String key,String model,Location at,Vector right,Vector up,double width,double height,double angle){used.add(key);sigils.plane(key,model,at,right,up,width,height,angle);}
    void ray(String key,String model,Location from,Location to,double width){used.add(key);sigils.ray(key,model,from,to,width);}
    void dust(Location at,Color color,float size){aether.dust(at,color,size);}
    /** A flat floor ray from an origin along a world angle. */
    private void floorRay(String key,String model,Location origin,double angle,double length,double width){
        Vector along=new Vector(Math.cos(angle),0,Math.sin(angle)),across=new Vector(-Math.sin(angle),0,Math.cos(angle));
        plane(key,model,origin.clone().add(along.clone().multiply(length/2)),across,along,width,length,0);
    }
    private void arc(Location origin,double radius,double from,double to,Color color){
        int samples=(int)Math.clamp(Math.abs(to-from)*radius/1.1,6,40);
        for(int i=0;i<=samples;i++){double a=from+(to-from)*i/samples;dust(origin.clone().add(Math.cos(a)*radius,.15,Math.sin(a)*radius),color,1.5f);}
    }
    /** Warning geometry, then the strike itself while firing. */
    void hazard(String key,ArkhaiaPattern.Hazard h,double floor,boolean firing,boolean portal,long tick){
        Location at=new Location(world,h.x(),floor+.12,h.z());
        switch(h.shape()){
            case CIRCLE -> {
                circle(key,"arkhaia_orbit",at,h.radius(),tick*.014,false);
                circle(key+":petals","arkhaia_rose",at.clone().add(0,.04,0),h.radius()*.8,-tick*.018,false);
                if(firing)ray(key+":beam","arkhaia_ray",at.clone().add(0,12,0),at,Math.max(.8,h.radius()*.5));
            }
            case RING -> {
                circle(key+":outer","arkhaia_orbit",at,h.radius()+h.halfWidth(),tick*.01,false);
                circle(key+":inner","arkhaia_orbit",at.clone().add(0,.04,0),Math.max(.5,h.radius()-h.halfWidth()),-tick*.01,false);
            }
            case LANE -> {
                Vector along=new Vector(Math.cos(h.angle()),0,Math.sin(h.angle())),across=new Vector(-Math.sin(h.angle()),0,Math.cos(h.angle()));
                plane(key,"arkhaia_fracture",at,across,along,h.halfWidth()*2,h.radius()*2,0);
                if(portal){
                    Location start=at.clone().subtract(along.clone().multiply(h.radius())).add(0,2.1,0);
                    plane(key+":portal","arkhaia_gate",start,across,new Vector(0,1,0),4.6,4.6,-tick*.025);
                    plane(key+":portal-rim","arkhaia_crown",start.clone().subtract(along.clone().multiply(.08)),across,new Vector(0,1,0),5.2,5.2,tick*.02);
                    if(firing)ray(key+":shot","arkhaia_ray",start,at.clone().add(along.multiply(h.radius())).add(0,1.4,0),1.1);
                }else if(firing)ray(key+":rise","arkhaia_fracture",at,at.clone().add(0,firing?7:0,0),h.halfWidth()*2);
            }
            case SECTOR -> {
                int rays=(int)Math.ceil(h.halfWidth()*2/Math.toRadians(16))+1;
                for(int i=0;i<rays;i++){double a=h.angle()-h.halfWidth()+h.halfWidth()*2*i/(rays-1);floorRay(key+":"+i,"arkhaia_fracture",at,a,h.radius(),firing?1.8:1.1);}
                arc(at,h.radius(),h.angle()-h.halfWidth(),h.angle()+h.halfWidth(),firing?GOLD:CRIMSON);
            }
            case HALF -> {
                double side=h.angle()+Math.signum(h.halfWidth())*Math.PI/2;
                for(int i=0;i<=8;i++)floorRay(key+":"+i,"arkhaia_fracture",at,side-Math.PI/2+Math.PI*i/8,h.radius(),firing?2:1.3);
                arc(at,h.radius(),side-Math.PI/2,side+Math.PI/2,firing?GOLD:CRIMSON);
                for(double d=-h.radius();d<=h.radius();d+=1.5)dust(at.clone().add(Math.cos(h.angle())*d,.2,Math.sin(h.angle())*d),CRIMSON,1.7f);
            }
        }
    }
    /** Rear weak point, the gold parry flash, and the Broken knee. */
    void body(Location body,double floor,long tick,boolean broken,double flash){
        double yaw=Math.toRadians(body.getYaw());Vector forward=new Vector(-Math.sin(yaw),0,Math.cos(yaw)),right=new Vector(Math.cos(yaw),0,Math.sin(yaw)),up=new Vector(0,1,0);
        Location rear=body.clone().subtract(forward.clone().multiply(2.1)).add(0,3.2,0);
        plane("fx:rear","arkhaia_weakpoint",rear,right,up,broken?4.2:2.3,broken?4.2:2.3,0);
        if(broken){
            plane("fx:broken-front","arkhaia_weakpoint",body.clone().add(forward.clone().multiply(2.2)).add(0,3,0),right,up,4.2,4.2,tick*.02);
            circle("fx:broken-floor","arkhaia_orbit",new Location(body.getWorld(),body.getX(),floor+.12,body.getZ()),4,tick*.02,false);
            for(int i=0;i<6;i++)dust(rear.clone().add(right.clone().multiply(Math.sin(i+tick*.2)*.8)).add(0,Math.cos(i)*.6,0),Color.fromRGB(235,180,255),1.4f);
        }
        if(flash>0){
            double size=3+flash*3.5;Location seal=body.clone().add(forward.clone().multiply(2.6)).add(0,4.2,0);
            plane("fx:parry","malef_gold_seal",seal,right,up,size,size,tick*.05);
            for(int i=0;i<10;i++){double a=i*Math.PI/5+tick*.1;dust(seal.clone().add(right.clone().multiply(Math.cos(a)*size*.5)).add(0,Math.sin(a)*size*.5,0),GOLD,1.8f);}
        }
    }
    void transition(Location body,double floor,double progress,long tick){
        double yaw=Math.toRadians(body.getYaw());Vector forward=new Vector(-Math.sin(yaw),0,Math.cos(yaw)),right=new Vector(Math.cos(yaw),0,Math.sin(yaw));
        circle("attack:transition-floor","arkhaia_crown",new Location(body.getWorld(),body.getX(),floor+.12,body.getZ()),2+progress*20,tick*.015,false);
        plane("attack:transition-gate","arkhaia_gate",body.clone().subtract(forward.multiply(2)).add(0,9,0),right,new Vector(0,1,0),17,17,-tick*.012);
    }
    /** Casting gate and crown above the body for spells; larger for Eclipse. */
    void casting(Location body,double floor,boolean ultimate,long tick){
        double yaw=Math.toRadians(body.getYaw());Vector forward=new Vector(-Math.sin(yaw),0,Math.cos(yaw)),right=new Vector(Math.cos(yaw),0,Math.sin(yaw)),up=new Vector(0,1,0);
        double diameter=ultimate?23:14;Location gate=body.clone().subtract(forward.clone().multiply(2.8)).add(0,ultimate?13:9,0);
        plane("attack:main-gate","arkhaia_gate",gate,right,up,diameter,diameter,tick*.009);
        plane("attack:crown","arkhaia_crown",gate.clone().subtract(forward.clone().multiply(.1)),right,up,diameter*1.2,diameter*1.2,-tick*.012);
        circle("attack:body-floor","arkhaia_rose",new Location(body.getWorld(),body.getX(),floor+.08,body.getZ()),ultimate?12:7,-tick*.01,false);
        if(ultimate){
            circle("attack:sky-crown","arkhaia_crown",body.clone().add(0,15,0),10,-tick*.014,false);
            for(int i=-1;i<=1;i++)ray("attack:charge:"+i,"arkhaia_ray",body.clone().add(right.clone().multiply(i*2)).add(0,1.5,0),body.clone().add(right.clone().multiply(i*6)).add(0,15,0),.35);
        }
    }
    /** Rose barrier while the river ritual protects the body. */
    void barrier(Location body,double floor,long tick){
        for(int i=0;i<3;i++){double a=i*Math.PI/3+tick*.01;
            plane("attack:barrier:"+i,"arkhaia_orbit",body.clone().add(0,4.4,0),new Vector(Math.cos(a),0,Math.sin(a)),new Vector(0,1,0),10,10,tick*.02*(i%2==0?1:-1));}
        circle("attack:barrier-floor","arkhaia_rose",new Location(body.getWorld(),body.getX(),floor+.1,body.getZ()),6,-tick*.012,false);
    }
    void tether(Location from,Location to,Color color){
        Vector delta=to.toVector().subtract(from.toVector());double length=delta.length();if(length<.5||length>80)return;
        int samples=(int)Math.clamp(length/1.4,4,40);
        for(int i=0;i<=samples;i++)dust(from.clone().add(delta.clone().multiply(i/(double)samples)).add(0,Math.sin(i*.7)*.25,0),color,1.4f);
    }
    void crown(String key,Location head,long tick){circle(key,"arkhaia_crown",head,1.15,tick*.04,false);}
    void thornRing(Location center,double radius,long tick){
        circle("attack:duel-ring","arkhaia_crown",center,radius,tick*.01,false);
        circle("attack:duel-ring-inner","arkhaia_rose",center.clone().add(0,.04,0),radius-.6,-tick*.016,false);
    }
    void thorns(String key,Location at,double radius,long tick){circle(key,"arkhaia_rose",at,radius,tick*.03,false);}
    void shockwave(String key,Location center,double radius,long tick){
        circle(key,"arkhaia_orbit",center,radius,tick*.03,false);
        int samples=(int)Math.clamp(radius*2.2,12,64);
        for(int i=0;i<samples;i++){double a=i*Math.PI*2/samples;dust(center.clone().add(Math.cos(a)*radius,.25,Math.sin(a)*radius),VIOLET,1.6f);}
    }
    void pulse(Location at,boolean portal){
        if(world==null)return;
        world.playSound(at,portal?Sound.ENTITY_EVOKER_CAST_SPELL:Sound.ENTITY_GENERIC_EXPLODE,.8f,.65f);
        world.spawnParticle(Particle.REVERSE_PORTAL,at,32,1.5,.8,1.5,.05);
    }
    void impact(Location at,boolean heavy){
        at.getWorld().playSound(at,heavy?Sound.ENTITY_GENERIC_EXPLODE:Sound.ENTITY_PLAYER_ATTACK_SWEEP,heavy?1.2f:1.1f,heavy?.55f:.7f);
        at.getWorld().spawnParticle(heavy?Particle.EXPLOSION:Particle.SWEEP_ATTACK,at.clone().add(0,1.2,0),heavy?3:4,1,.5,1,0);
    }
    void swordTrail(boolean swinging,Location tip,long tick){
        if(tip!=null)world=tip.getWorld();
        if(tip==null||!swinging){lastTip=null;return;}
        if(tick%2!=0)return;
        if(lastTip!=null&&tip.getWorld()==lastTip.getWorld()&&tip.distance(lastTip)<10){
            int samples=(int)Math.clamp(Math.ceil(tip.distance(lastTip)/.35),1,12);
            for(int i=1;i<=samples;i++)aether.dust(lastTip.clone().add(tip.toVector().subtract(lastTip.toVector()).multiply(i/(double)samples)),VIOLET,1.2f);
        }lastTip=tip.clone();
    }
    void clear(){sigils.close();previous.clear();used.clear();lastTip=null;}
    @Override public void close(){clear();}
}
