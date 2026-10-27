package com.example.voidscape.boss;

import java.util.*;

/** Server-tick encounter rules, independent of entities and rendering. Health stays in solo units. */
public final class JudgmentFight {
    public enum Part { LEFT, RIGHT, CORE }
    public enum DamageKind { MELEE, PROJECTILE, MAGIC, OTHER }
    public record Hit(double damage, boolean broke, boolean transition, boolean won) {}
    private final double coreMax,handMax,extraPlayer;
    private final int exposureTicks,maxPlayers;
    private final Set<UUID> roster=new LinkedHashSet<>();
    private final Map<UUID,Double> contribution=new HashMap<>();
    private double core,left,right;
    private int phase=1;
    private long exposedUntil,ruptureUntil,stunnedUntil;
    private final int[] sealHits=new int[2];
    private final Map<UUID,Long> lastSealHit=new HashMap<>();
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
    public boolean accepts(DamageKind kind){return switch(kind){
        case PROJECTILE -> phase<2;
        case MAGIC -> phase<3;
        default -> true;
    };}
    /** Hand bonuses in phases 1/2; interrupting the final charge grants triple damage. */
    public double coreMultiplier(long now){return phase==3?stunned(now)?3:1:exposed(now)?1+(left<=0?1:0)+(right<=0?1:0):1;}
    public boolean exposed(long now){return phase==3||now<exposedUntil;}
    public long exposedUntil(){return exposedUntil;}
    public long openingIn(long now){return 0;}
    public boolean stunned(long now){return phase==3&&now<stunnedUntil;}
    public long stunnedUntil(){return stunnedUntil;}
    public boolean ruptureCharging(long now){return phase==3&&ruptureUntil>now;}
    public int sealHits(int index){return sealHits[index];}
    public void beginRupture(long now){
        if(phase!=3||won())return;
        ruptureUntil=now+160;stunnedUntil=0;Arrays.fill(sealHits,0);lastSealHit.clear();
    }
    public void finishRupture(){ruptureUntil=0;Arrays.fill(sealHits,0);lastSealHit.clear();}
    /** Three direct melee strikes per seal, independent of weapon DPS and party scaling. */
    public boolean strikeSeal(UUID player,int index,DamageKind kind,double damage,long now){
        if(index<0||index>=2||kind!=DamageKind.MELEE||!Double.isFinite(damage)||damage<=0||won()
            ||!ruptureCharging(now)||sealHits[index]>=3||now-lastSealHit.getOrDefault(player,now-8)<8||!join(player))return false;
        lastSealHit.put(player,now);sealHits[index]++;
        if(sealHits[0]==3&&sealHits[1]==3){ruptureUntil=0;stunnedUntil=now+140;return true;}
        return false;
    }
    /** Called only by the manager's permission-checked, reward-free test encounter startup. */
    public void prepareTestPhase(int wanted){
        if(wanted<1||wanted>3)throw new IllegalArgumentException("phase must be 1..3");
        phase=wanted;core=coreMax*(wanted==1?1:wanted==2?.70:.35);left=right=wanted==3?0:handMax;
        exposedUntil=stunnedUntil=0;finishRupture();contribution.clear();
    }
    public boolean tick(long now){
        if(won()||phase==3)return false;
        if(exposedUntil>0&&now>=exposedUntil){
            exposedUntil=0;
            if(left<=0)left=handMax*.45;if(right<=0)right=handMax*.45;
        }
        return false;
    }
    /** Credit the player who lured the hand into its rune, independent of their weapon DPS. */
    public Hit baitHand(UUID player,Part part,long now){
        if(phase!=2||part==Part.CORE||hand(part)<=0||won()||!join(player))return new Hit(0,false,false,false);
        double lost=hand(part);if(part==Part.LEFT)left=0;else right=0;
        extendOpening(now);contribution.merge(player,lost,Double::sum);
        return new Hit(lost,true,false,false);
    }
    private void extendOpening(long now){exposedUntil=Math.min(now+exposureTicks+120,Math.max(now+exposureTicks,exposedUntil+80));}
    public Hit hit(UUID player,Part part,double incoming,long now){
        if(!Double.isFinite(incoming)||incoming<=0||won()||!join(player))return new Hit(0,false,false,false);
        double amount=incoming/scale(),lost=0;boolean broke=false,transition=false;
        if(part!=Part.CORE){
            if(phase==3)return new Hit(0,false,false,false);
            double hp=hand(part);lost=Math.min(hp,amount);
            if(part==Part.LEFT)left-=lost;else right-=lost;
            broke=hp>0&&hand(part)<=0;
            if(broke)extendOpening(now);
        }else if(exposed(now)){
            double floor=phase==1?coreMax*.70:phase==2?coreMax*.35:0;
            lost=Math.min(amount*coreMultiplier(now),Math.max(0,core-floor));core-=lost;
            if(core<=floor&&phase<3){
                phase++;transition=true;exposedUntil=0;left=right=handMax;
                if(phase==3){left=right=0;stunnedUntil=0;finishRupture();}
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
