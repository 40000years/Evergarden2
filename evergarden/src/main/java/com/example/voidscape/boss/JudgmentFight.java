package com.example.voidscape.boss;

import java.util.*;

/** Server-tick encounter rules, independent of entities and rendering. Health stays in solo units. */
public final class JudgmentFight {
    public enum Part { LEFT, RIGHT, CORE }
    public record Hit(double damage, boolean broke, boolean transition, boolean won) {}
    private final double coreMax,handMax,extraPlayer;
    private final int exposureTicks,maxPlayers;
    private final Set<UUID> roster=new LinkedHashSet<>();
    private final Map<UUID,Double> contribution=new HashMap<>();
    private double core,left,right;
    private int phase=1;
    private long exposedUntil,nextOpening;
    public JudgmentFight(double coreHp,double handHp,double extraPlayer,int exposureTicks,int maxPlayers) {
        this.coreMax=coreHp;this.handMax=handHp;this.extraPlayer=extraPlayer;
        this.exposureTicks=exposureTicks;this.maxPlayers=maxPlayers;
        core=coreMax;left=right=handMax;
    }
    public boolean join(UUID player){return roster.contains(player)||roster.size()<maxPlayers&&roster.add(player);}
    public Set<UUID> roster(){return Set.copyOf(roster);}
    public double scale(){return 1+extraPlayer*Math.max(0,roster.size()-1);}
    public double core(){return core;} public double coreMax(){return coreMax;}
    public double hand(Part part){return part==Part.LEFT?left:right;}
    public double contribution(UUID player){return contribution.getOrDefault(player,0.0);}
    public int phase(){return phase;} public boolean won(){return core<=0;}
    public boolean exposed(long now){return now<exposedUntil;}
    public long exposedUntil(){return exposedUntil;}
    public long openingIn(long now){return phase==3&&!exposed(now)?Math.max(0,nextOpening-now):0;}
    public boolean tick(long now){
        if(won())return false;
        if(exposedUntil>0&&now>=exposedUntil){
            exposedUntil=0;
            if(phase<3){if(left<=0)left=handMax*.45;if(right<=0)right=handMax*.45;}
            else nextOpening=now+200;
        }
        if(phase==3&&!exposed(now)&&now>=nextOpening){exposedUntil=now+exposureTicks;return true;}
        return false;
    }
    public Hit hit(UUID player,Part part,double incoming,long now){
        if(!Double.isFinite(incoming)||incoming<=0||won()||!join(player))return new Hit(0,false,false,false);
        double amount=incoming/scale(),lost=0;boolean broke=false,transition=false;
        if(part!=Part.CORE){
            if(phase==3)return new Hit(0,false,false,false);
            double hp=hand(part);lost=Math.min(hp,amount);
            if(part==Part.LEFT)left-=lost;else right-=lost;
            broke=hp>0&&hand(part)<=0;
            if(broke)exposedUntil=Math.min(now+exposureTicks+120,Math.max(now+exposureTicks,exposedUntil+80));
        }else if(exposed(now)){
            double floor=phase==1?coreMax*.70:phase==2?coreMax*.35:0;
            lost=Math.min(amount,Math.max(0,core-floor));core-=lost;
            if(core<=floor&&phase<3){
                phase++;transition=true;exposedUntil=0;left=right=handMax;
                if(phase==3){left=right=0;nextOpening=now+160;}
            }
        }
        contribution.merge(player,lost,Double::sum);
        return new Hit(lost,broke,transition,won());
    }
    /** Four sanctuaries; choose the nearest, independent of altitude and client particles. */
    public static boolean sanctuary(double x,double z,double ax,double az,double radius){
        return Math.hypot(x-ax,z-az)<=radius||Math.hypot(x+ax,z+az)<=radius
            ||Math.hypot(x+az,z-ax)<=radius||Math.hypot(x-az,z+ax)<=radius;
    }
}
