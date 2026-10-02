package com.example.sevensins;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;
import org.bukkit.util.RayTraceResult;
import java.util.*;

/** Vanilla Evoker fang visuals; encounter damage is applied once per player per wave. */
final class WrathBlades {
    private static final class Spike {
        final Location floor;
        final int trigger;
        final EvokerFangs fang;
        int age;
        boolean struck;
        Spike(Location floor, int trigger, EvokerFangs fang) {
            this.floor=floor; this.trigger=trigger; this.fang=fang;
        }
        void remove() { fang.remove(); }
    }
    private final SevenSinsPlugin plugin;
    private final WrathBoss boss;
    private final List<Spike> spikes = new ArrayList<>();
    private final Set<UUID> hit = new HashSet<>();
    WrathBlades(SevenSinsPlugin plugin, WrathBoss boss) { this.plugin=plugin; this.boss=boss; }

    void cast(Location from, Location target, int windup) {
        clear(); hit.clear();
        Vector delta=target.toVector().subtract(from.toVector()).setY(0);
        double distance=delta.length(); if(distance<0.1)return; delta.normalize();
        int count=Math.min(14, Math.max(3,(int)Math.ceil(distance/1.7)));
        try {
            for(int i=0;i<count;i++) {
                double along=distance*(i+1)/count;
                Location point=from.clone().add(delta.clone().multiply(along));
                point.setY(from.getY()+(target.getY()-from.getY())*(i+1)/count);
                Location ground=ground(point);
                if(ground!=null) add(ground,windup+i*2);
            }
            // A fork at the locked target and two retreat points punish constant backpedaling.
            // Every floor point is fixed before its warning; strafing remains a counter.
            for(int side:new int[]{-1,1}) {
                Location point=target.clone().add(-delta.getZ()*side*1.8,0,delta.getX()*side*1.8);
                Location ground=ground(point); if(ground!=null)add(ground,windup+count*2);
            }
            for(int step=1;step<=2;step++) {
                Location retreat=target.clone().add(delta.clone().multiply(step*3));
                for(int side:new int[]{-1,0,1}) {
                    Location point=retreat.clone().add(-delta.getZ()*side*1.8,0,delta.getX()*side*1.8);
                    Location ground=ground(point); if(ground!=null)add(ground,windup+count*2+step*6);
                }
            }
        } catch(RuntimeException error) {clear();throw error;}
    }
    private Location ground(Location point) {
        Location start=point.clone().add(0,4,0);
        RayTraceResult result=point.getWorld().rayTraceBlocks(start,new Vector(0,-1,0),9,FluidCollisionMode.NEVER,true);
        if(result==null||result.getHitBlock()==null)return null;
        Location floor=result.getHitPosition().toLocation(point.getWorld()).add(0,0.02,0);
        if(Math.abs(floor.getY()-point.getY())>4)return null;
        if(!floor.clone().add(0,1,0).getBlock().isPassable()||!floor.clone().add(0,2,0).getBlock().isPassable())return null;
        return floor;
    }
    private void add(Location floor,int trigger) {
        EvokerFangs fang=null;
        try {
            fang=floor.getWorld().spawn(floor,EvokerFangs.class,e->{
                e.setOwner(boss.entity());e.setAttackDelay(Math.max(0,trigger-8));e.setPersistent(false);
                e.getPersistentDataContainer().set(plugin.entityKey(),PersistentDataType.STRING,boss.type().id()+"-fang");
            });
            if(!fang.isValid())throw new IllegalStateException("Evoker fang spawn was rejected");
            spikes.add(new Spike(floor,trigger,fang));
        } catch(RuntimeException error) {
            if(fang!=null)fang.remove();throw error;
        }
    }
    void tick() {
        for(Iterator<Spike> it=spikes.iterator();it.hasNext();) {
            Spike s=it.next();s.age+=2;
            int riseAt=s.trigger-8;
            if(s.age<riseAt) {
                if(s.age%4==0) for(int i=0;i<16;i++) {
                    double angle=i*Math.PI/8;
                    s.floor.getWorld().spawnParticle(Particle.DUST,s.floor.clone().add(Math.cos(angle)*1.25,0.1,Math.sin(angle)*1.25),1,
                            0,0,0,0,new Particle.DustOptions(boss.type().color(),1.2f));
                }
            } else {
                if(!s.struck&&s.age>=s.trigger) {
                    s.struck=true;
                    for(Player p:boss.bladeTargets()) {
                        Vector d=p.getLocation().toVector().subtract(s.floor.toVector());
                        if(d.getY()>-0.5&&d.getY()<2.8&&Math.hypot(d.getX(),d.getZ())<=1.25&&boss.clearSight(p,s.floor))
                            boss.hurt(p,32,0.35,hit);
                    }
                }
            }
            if(s.age>=s.trigger+22){s.remove();it.remove();}
        }
    }
    List<Entity> entities() {List<Entity> result=new ArrayList<>();for(Spike s:spikes)result.add(s.fang);return result;}
    boolean active() { return !spikes.isEmpty(); }
    void clear() {spikes.forEach(Spike::remove);spikes.clear();hit.clear();}
}
