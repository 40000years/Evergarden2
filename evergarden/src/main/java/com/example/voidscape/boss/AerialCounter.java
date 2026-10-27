package com.example.voidscape.boss;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import java.util.*;

/** Committed, wall-blocked sky lances for the two otherwise ground-only fighters. */
final class AerialCounter {
    static final int WARNING=28,RECOVERY=8,COOLDOWN=100;
    static final double RADIUS=.65;
    static final class State {
        final Location from,aim;
        final long fireAt,recoverUntil;
        boolean fired;
        State(Location from,Location aim,long now){this.from=from;this.aim=aim;fireAt=now+WARNING;recoverUntil=fireAt+RECOVERY;}
    }
    private final ArkhaiaMinions host;
    private final Location center;
    private final JudgeSigils sigils;
    final Map<ArkhaiaMinions.Spirit,State> states=new IdentityHashMap<>();
    final Map<ArkhaiaMinions.Spirit,Long> next=new IdentityHashMap<>();
    AerialCounter(ArkhaiaMinions host,Location center,JudgeSigils sigils){this.host=host;this.center=center;this.sigils=sigils;}
    private String key(ArkhaiaMinions.Spirit s){return "aerial:"+s.mob.getUniqueId();}
    boolean step(ArkhaiaMinions.Spirit owner,Player target,List<Player> players,long now){
        if(owner.kind!=ArkhaiaWaves.Kind.ACHERON&&owner.kind!=ArkhaiaWaves.Kind.STYX)return false;
        if(now<owner.stunUntil){cancel(owner);return false;}
        State s=states.get(owner);
        if(s==null){
            if(now<next.getOrDefault(owner,0L)||!host.aerialReady(owner,now))return false;
            Player airborne=players.stream().filter(p->p.getY()>center.getY()+3)
                .min(Comparator.comparingDouble(p->p.getLocation().distanceSquared(owner.mob.getLocation()))).orElse(null);
            if(airborne==null)return false;
            Location from=owner.mob.getLocation().add(0,6,0),aim=airborne.getEyeLocation();
            if(!host.clearTrial(from,aim))return false;
            Vector direction=aim.toVector().subtract(from.toVector());if(direction.lengthSquared()<.01)return false;
            aim.add(direction.normalize().multiply(.9));
            s=new State(from,aim,now);states.put(owner,s);next.put(owner,now+COOLDOWN);
            airborne.sendActionBar(net.kyori.adventure.text.Component.text("⚠ หอกเวหา — ออกจากแนวแสง!"));
            airborne.playSound(airborne.getLocation(),Sound.BLOCK_NOTE_BLOCK_BELL,1,1.4f);
        }
        owner.mob.setAI(false);owner.mob.getPathfinder().stopPathfinding();
        if(now>=s.recoverUntil){cancel(owner);return false;}
        host.renderTrial(owner,target,now<s.fireAt?"cast_windup":now==s.fireAt?"cast_active":"cast_recover",now<s.fireAt?20.0/WARNING:1);
        if(now<s.fireAt){if(now%4==0)frame(owner,s,false);return true;}
        if(!s.fired){
            s.fired=true;Location end=endpoint(s);Vector delta=end.toVector().subtract(s.from.toVector());double length=delta.length();
            frame(owner,s,true);
            if(length>.01)for(Player p:players){
                var box=p.getBoundingBox().expand(RADIUS);
                if((box.contains(s.from.toVector())||box.rayTrace(s.from.toVector(),delta.clone().normalize(),length)!=null)&&host.clearTrial(s.from,p.getLocation().add(0,.9,0)))host.aerialHit(owner,p,now);
                if(!owner.mob.isValid()||owner.health<=0)return true;
            }
            s.from.getWorld().playSound(s.from,Sound.ENTITY_BLAZE_SHOOT,1,.65f);
        }
        return true;
    }
    private Location endpoint(State s){
        Vector delta=s.aim.toVector().subtract(s.from.toVector());double length=delta.length();if(length<.01)return s.from.clone();
        var wall=s.from.getWorld().rayTraceBlocks(s.from,delta.normalize(),length,FluidCollisionMode.NEVER,true);
        return wall==null?s.aim.clone():wall.getHitPosition().toLocation(s.from.getWorld());
    }
    private void frame(ArkhaiaMinions.Spirit owner,State s,boolean impact){
        Location end=endpoint(s);Vector delta=end.toVector().subtract(s.from.toVector());double length=delta.length();if(length<.01)return;
        sigils.fastRay(key(owner),"judge_ray",s.from,end,RADIUS*2);
        Color color=impact?ArkhaiaMinions.color(owner.kind):Color.YELLOW;
        int samples=(int)Math.clamp(Math.ceil(length/.6),2,96);
        for(int i=0;i<=samples;i++)s.from.getWorld().spawnParticle(Particle.DUST,s.from.clone().add(delta.clone().multiply(i/(double)samples)),1,0,0,0,0,new Particle.DustOptions(color,impact?2:1.1f),true);
        end.getWorld().spawnParticle(Particle.ELECTRIC_SPARK,end,impact?20:5,.3,.3,.3,.01,null,true);
    }
    void cancel(ArkhaiaMinions.Spirit owner){states.remove(owner);sigils.remove(key(owner));}
    void remove(ArkhaiaMinions.Spirit owner){cancel(owner);next.remove(owner);}
    void clear(){for(var owner:new ArrayList<>(states.keySet()))cancel(owner);next.clear();}
}
