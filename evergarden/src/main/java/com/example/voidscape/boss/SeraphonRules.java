package com.example.voidscape.boss;

import java.util.*;
import java.util.random.RandomGenerator;

/** Shared encounter timings and geometry; damage and visible warnings use these values. */
public final class SeraphonRules {
    public static final double HEALTH=7000,HEALTH_PER_EXTRA=1500,HEALTH_CAP=10000,RADIUS=54,SCALE=3.2;
    public static final int INTRO=60,TRANSITION=70,REGROUP=200,RESPAWN=24000;
    public static final int GROUNDED=120,GROUNDED_LONG=160,CHOIR_INTERVAL=1200,WINGS_INTERVAL=2000;
    public static final double MIRROR_RADIUS=2.0,MIRROR_RING=24,WHEEL_SHADOW=3.2,WINGS_SAFE=7;
    /** Heaven's Gate crosses from WINGS_REACH on one side of the center to WINGS_REACH on the other, drifting WINGS_SIDE sideways. */
    public static final double WINGS_REACH=14,WINGS_SIDE=8;
    public static final int WHEEL_PROGRESS=60;
    public enum Skill {
        LANCES("cast",36,30,24),FEATHERS("wings",30,30,32),SMITE("smite",36,48,30),
        ORBIT("cast",44,110,36),VERDICT("verdict",64,60,64),
        /** Tracks for 60 ticks, locks for 20, then fires; a mirror between the target and the orb reflects it. */
        SOLAR("cast",80,16,30),
        /** Ascend, then break every Ophanim wheel by standing in its shadow. */
        CHOIR("cast",40,700,0),
        /** Stand under her wings when the gate opens: everyone else is erased. */
        WINGS("verdict",40,160,0);
        public final String clip;public final int windup,active,recover;
        Skill(String clip,int windup,int active,int recover){this.clip=clip;this.windup=windup;this.active=active;this.recover=recover;}
        public int duration(){return windup+active+recover;}
        public String clip(int age){
            if(this==CHOIR&&age>=windup)return "hover";
            return clip+(age<windup?"_windup":age<windup+active?"_active":"_recover");
        }
        public double rate(int age){
            double[] lengths=switch(this){case LANCES,ORBIT,SOLAR,CHOIR->new double[]{1,.6,.8};case FEATHERS->new double[]{.9,.5,.8};case SMITE->new double[]{.8,.5,.9};case VERDICT,WINGS->new double[]{2,1.2,1.4};};
            if(this==CHOIR&&age>=windup)return 1;
            return age<windup?lengths[0]*20/windup:age<windup+active?lengths[1]*20/Math.max(1,active):lengths[2]*20/Math.max(1,recover);
        }
    }
    public static double health(double base,double perExtra,double cap,int players){return Math.min(Math.max(base,cap),base+perExtra*Math.max(0,players-1));}
    public static int phase(double fraction){return fraction<=.30?3:fraction<=.65?2:1;}
    public static int cooldown(Skill skill,int phase){
        return switch(skill){
            case VERDICT -> phase==3?400:phase==2?500:600;
            case SOLAR -> phase==3?320:phase==2?400:360;
            case ORBIT -> 300;
            case SMITE -> 120;
            default -> 0;
        };
    }
    /** Weighted, never the same skill twice in a row; scripted skills (choir, wings) are scheduled by the encounter. */
    public static Skill choose(int phase,Skill last,Map<Skill,Long> ready,long now,RandomGenerator random){
        var weights=new EnumMap<Skill,Double>(Skill.class);
        weights.put(Skill.LANCES,3.0);weights.put(Skill.FEATHERS,3.0);weights.put(Skill.SMITE,3.0);
        weights.put(Skill.SOLAR,phase==1?2.5:2.0);weights.put(Skill.VERDICT,phase==3?2.5:1.5);
        if(phase>1)weights.put(Skill.ORBIT,2.0);
        weights.keySet().removeIf(skill->now<ready.getOrDefault(skill,0L));
        if(weights.size()>1)weights.remove(last);
        if(weights.isEmpty())return Skill.LANCES;
        double total=weights.values().stream().mapToDouble(Double::doubleValue).sum(),roll=random.nextDouble(total);
        for(var entry:weights.entrySet()){roll-=entry.getValue();if(roll<0)return entry.getKey();}
        return weights.keySet().iterator().next();
    }
    public static boolean inside(double x,double y,double z,double floor){return y>=floor-.25&&x*x+z*z<=RADIUS*RADIUS;}
    public static boolean safePetal(int petal,int offset){return Math.floorMod(petal-offset,4)==0;}
    public static double damage(Skill skill,int phase){return switch(skill){case LANCES->18+phase*3;case FEATHERS->16+phase*2;case SMITE->34+phase*4;case ORBIT->22+phase*3;case VERDICT->50+phase*6;default->0;};}
    /** A mirror shields the target when it sits on the beam, nearer the target than the orb, within reach of the target. */
    public static boolean mirrored(double fx,double fy,double fz,double tx,double ty,double tz,double mx,double my,double mz){
        double dx=tx-fx,dy=ty-fy,dz=tz-fz,length=dx*dx+dy*dy+dz*dz;if(length<1e-6)return false;
        double t=((mx-fx)*dx+(my-fy)*dy+(mz-fz)*dz)/length;if(t<.35||t>1.02)return false;
        double px=fx+dx*t-mx,py=fy+dy*t-my,pz=fz+dz*t-mz;
        return px*px+py*py+pz*pz<=MIRROR_RADIUS*MIRROR_RADIUS&&Math.hypot(tx-mx,tz-mz)<=8;
    }
    public static boolean sheltered(double px,double pz,double bx,double bz){return Math.hypot(px-bx,pz-bz)<=WINGS_SAFE;}
    public static int wheels(int players){return Math.clamp(players,1,3)+1;}
    private SeraphonRules(){}
}
