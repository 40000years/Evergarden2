package com.example.advancemagic.effect;

import com.example.advancemagic.AdvanceMagicPlugin;
import org.bukkit.*;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import java.util.*;

public final class StatusService implements Listener {
    private record Status(Player caster,LivingEntity target,Location anchor,long end) {}
    private final AdvanceMagicPlugin plugin;
    private final Map<UUID,Status> roots=new HashMap<>(),frozen=new HashMap<>(),armor=new HashMap<>();
    private long tick;
    public StatusService(AdvanceMagicPlugin plugin){this.plugin=plugin;}
    private Status status(Player p,LivingEntity e,int ticks){return new Status(p,e,e.getLocation(),tick+ticks);}
    public void freeze(Player p,LivingEntity e){frozen.put(e.getUniqueId(),status(p,e,120));}
    public void root(Player p,LivingEntity e){
        if(e instanceof Player&&!plugin.getConfig().getBoolean("compatibility.hard-player-roots",false)) {
            plugin.context().potion(e,PotionEffectType.SLOWNESS,30,4);return;
        }
        roots.put(e.getUniqueId(),status(p,e,30));plugin.context().potion(e,PotionEffectType.SLOWNESS,30,127);
    }
    public void armor(Player p){armor.put(p.getUniqueId(),status(p,p,1200));}
    public boolean armored(Player p){return armor.containsKey(p.getUniqueId());}

    public void joined(Player p){}
    private boolean expired(Status s){return tick>=s.end||!s.caster.isOnline()||s.caster.isDead()||!s.target.isValid()||s.target.isDead()||s.caster.getWorld()!=s.anchor.getWorld()||s.target.getWorld()!=s.anchor.getWorld();}
    public void tick() {
        tick++;
        if(roots.isEmpty()&&frozen.isEmpty()&&armor.isEmpty())return;

        roots.values().removeIf(this::expired);
        for(Status s:roots.values()) {
            if(!(s.target instanceof Player)) {
                s.target.setVelocity(new Vector());
            } else if(!s.target.isOnGround()) {
                Vector v = s.target.getVelocity();
                if(Math.hypot(v.getX(), v.getZ()) > 0.05) s.target.setVelocity(new Vector(0, v.getY(), 0));
            }
        }
        frozen.values().removeIf(this::expired);
        for(Status s:frozen.values())s.target.setFreezeTicks(Math.max(0,s.target.getMaxFreezeTicks()-1));
        armor.values().removeIf(this::expired);
    }
    public void clear(Player p) {
        roots.values().removeIf(s->s.caster.equals(p)||s.target.equals(p));
        frozen.values().removeIf(s->s.caster.equals(p)||s.target.equals(p));
        armor.remove(p.getUniqueId());
        plugin.context().clearPlayerVelocity(p.getUniqueId());
    }
    public void close(){roots.clear();frozen.clear();armor.clear();}
    @EventHandler(ignoreCancelled=true) public void move(PlayerMoveEvent e) {
        Status s=roots.get(e.getPlayer().getUniqueId());Location to=e.getTo();
        if(s==null||to==null||e instanceof PlayerTeleportEvent||to.getWorld()!=s.anchor.getWorld())return;
        if(to.distanceSquared(s.anchor) > 0.04) {
            Location fixed=s.anchor.clone();fixed.setYaw(to.getYaw());fixed.setPitch(to.getPitch());e.setTo(fixed);
        }
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void teleport(PlayerTeleportEvent e){roots.remove(e.getPlayer().getUniqueId());}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void reflect(EntityDamageByEntityEvent e) {
        if(!(e.getEntity() instanceof Player p)||!armored(p)||!(e.getDamager() instanceof LivingEntity attacker))return;
        double amount=Math.max(4.0,e.getFinalDamage()*1.0);
        if(amount<=0||!plugin.context().enemy(p,attacker))return;
        Bukkit.getScheduler().runTask(plugin,()->{
            if(p.isOnline()&&!p.isDead()&&attacker.isValid()&&!attacker.isDead()&&p.getWorld()==attacker.getWorld()
                &&plugin.context().affect(p,attacker,com.example.advancemagic.spell.Spell.IRON_ARMOR)) {
                plugin.context().damage(p,attacker,amount,DamageType.THORNS);
                attacker.getWorld().playSound(attacker.getLocation(),Sound.BLOCK_ANVIL_LAND,0.8f,1.5f);
                Vector push=attacker.getLocation().toVector().subtract(p.getLocation().toVector()).setY(0);
                if(push.lengthSquared()>0.01)plugin.context().velocity(attacker,push.normalize().multiply(0.6).setY(0.2));
            }
        });
    }
}
