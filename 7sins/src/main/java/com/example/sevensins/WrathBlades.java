package com.example.sevensins;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;
import org.bukkit.util.EulerAngle;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.util.*;

/** Hidden Evoker fangs under original sword models; one hit per player per wave. */
final class WrathBlades {
    private static final class Spike {
        final Location floor;
        final int trigger;
        final EvokerFangs fang;
        final ItemDisplay sword;
        final ArmorStand fallback;
        int age;
        boolean struck;
        Spike(Location floor, int trigger, EvokerFangs fang, ItemDisplay sword, ArmorStand fallback) {
            this.floor=floor; this.trigger=trigger; this.fang=fang; this.sword=sword; this.fallback=fallback;
        }
        void remove() { fang.remove(); sword.remove(); fallback.remove(); }
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
            // The last two points surround the locked target position; no homing after warning.
            for(int side:new int[]{-1,1}) {
                Location point=target.clone().add(-delta.getZ()*side*1.6,0,delta.getX()*side*1.6);
                Location ground=ground(point); if(ground!=null)add(ground,windup+count*2);
            }
            for(Player p:Bukkit.getOnlinePlayers())refresh(p);
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
        EvokerFangs fang=null; ItemDisplay sword=null; ArmorStand fallback=null;
        try {
            fang=floor.getWorld().spawn(floor,EvokerFangs.class,e->{
                e.setOwner(boss.entity());e.setAttackDelay(Math.max(0,trigger-8));e.setVisibleByDefault(false);e.setPersistent(false);
                e.getPersistentDataContainer().set(plugin.entityKey(),PersistentDataType.STRING,"wrath-fang");
            });
            Location buried=floor.clone().add(0,-3,0);buried.setYaw(0);buried.setPitch(0);
            ItemStack item=new ItemStack(Material.PAPER);var meta=item.getItemMeta();
            meta.setItemModel(new NamespacedKey("sevensins","wrath/ground_sword"));item.setItemMeta(meta);
            sword=floor.getWorld().spawn(buried,ItemDisplay.class,e->{
                e.setItemStack(item);e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
                e.setPersistent(false);e.setVisibleByDefault(false);e.setInvulnerable(true);
                e.setTeleportDuration(2);e.setDisplayWidth(4);e.setDisplayHeight(5);
                e.setBrightness(new Display.Brightness(15,15));
                e.setTransformation(new Transformation(new Vector3f(0,0.7f,0),new Quaternionf(),new Vector3f(1.4f),new Quaternionf()));
                e.getPersistentDataContainer().set(plugin.entityKey(),PersistentDataType.STRING,"visual");
            });
            fallback=floor.getWorld().spawn(buried,ArmorStand.class,e->{
                e.setVisible(false);e.setMarker(true);e.setArms(true);e.setGravity(false);e.setInvulnerable(true);
                e.setVisibleByDefault(false);e.setPersistent(false);
                e.getEquipment().setItemInMainHand(new ItemStack(Material.NETHERITE_SWORD));
                e.setRightArmPose(new EulerAngle(-Math.PI,0,0));
                e.getPersistentDataContainer().set(plugin.entityKey(),PersistentDataType.STRING,"visual");
            });
            if(!fang.isValid()||!sword.isValid()||!fallback.isValid())throw new IllegalStateException("Ground sword spawn was rejected");
            spikes.add(new Spike(floor,trigger,fang,sword,fallback));
        } catch(RuntimeException error) {
            if(fang!=null)fang.remove();if(sword!=null)sword.remove();if(fallback!=null)fallback.remove();throw error;
        }
    }
    void refresh(Player p) {
        boolean custom=plugin.packs().loaded(p);
        for(Spike spike:spikes) {
            if(custom){p.showEntity(plugin,spike.sword);p.hideEntity(plugin,spike.fallback);}
            else{p.hideEntity(plugin,spike.sword);p.showEntity(plugin,spike.fallback);}
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
                            0,0,0,0,new Particle.DustOptions(Color.fromRGB(245,40,40),1.2f));
                }
            } else {
                double height=Math.min(1,(s.age-riseAt)/8.0);
                if(s.age>s.trigger+12)height=Math.max(0,1-(s.age-s.trigger-12)/8.0);
                height=height*height*(3-2*height);
                Location pose=s.floor.clone().add(0,-3+3*height,0);pose.setYaw(0);pose.setPitch(0);
                s.sword.teleport(pose);s.fallback.teleport(pose.clone().add(0,-0.6,0));
                if(!s.struck&&s.age>=s.trigger) {
                    s.struck=true;s.floor.getWorld().playSound(s.floor,Sound.ENTITY_EVOKER_FANGS_ATTACK,0.8f,0.6f);
                    s.floor.getWorld().spawnParticle(Particle.FLAME,s.floor.clone().add(0,0.2,0),8,0.3,0.1,0.3,0.02);
                    for(Player p:boss.bladeTargets()) {
                        Vector d=p.getLocation().toVector().subtract(s.floor.toVector());
                        if(d.getY()>-0.5&&d.getY()<2.8&&Math.hypot(d.getX(),d.getZ())<=1.25&&boss.clearSight(p,s.floor))
                            boss.hurt(p,24,0.35,hit);
                    }
                }
            }
            if(s.age>=s.trigger+22){s.remove();it.remove();}
        }
    }
    List<Entity> entities() {List<Entity> result=new ArrayList<>();for(Spike s:spikes){result.add(s.fang);result.add(s.sword);result.add(s.fallback);}return result;}
    void clear() {spikes.forEach(Spike::remove);spikes.clear();hit.clear();}
}
