package com.example.voidscape.compat;

import com.example.voidscape.VoidscapePlugin;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.potion.*;
import org.bukkit.util.Vector;
import java.util.*;

/** Server-owned movement only; never changes anticheat permissions or exemptions. */
public final class CropMovement implements Listener, AutoCloseable {
    private final VoidscapePlugin plugin;
    private final NamespacedKey stepKey;
    private final Map<UUID,Long> jumps=new HashMap<>(), steps=new HashMap<>(), gliders=new HashMap<>(), rescues=new HashMap<>(), rescueRetry=new HashMap<>();
    private final Set<UUID> flightOwned=new HashSet<>(), jumpReady=new HashSet<>();
    public CropMovement(VoidscapePlugin plugin){this.plugin=plugin;stepKey=plugin.key("crop_step_height");}
    public boolean enhanced(){return "enhanced".equalsIgnoreCase(plugin.getConfig().getString("compatibility.crop-movement","vanilla"));}
    private boolean survival(Player p){return p.getGameMode()==GameMode.SURVIVAL||p.getGameMode()==GameMode.ADVENTURE;}
    private boolean active(Map<UUID,Long> map,UUID id,long now){return map.getOrDefault(id,0L)>now;}

    private boolean isBedrock(Player p) {
        try {
            Class<?> api = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            Object inst = api.getMethod("getInstance").invoke(null);
            return (boolean) api.getMethod("isFloodgatePlayer", UUID.class).invoke(inst, p.getUniqueId());
        } catch (Throwable ignored) {
            return false;
        }
    }

    public void fairy(Player p){
        if(!enhanced()||isBedrock(p)||!survival(p)||p.getAllowFlight()&&!flightOwned.contains(p.getUniqueId())) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST,3600,1));
            p.sendMessage(ChatColor.LIGHT_PURPLE+"✦ Fairy Mushroom: Jump Boost II (3 นาที)");return;
        }
        UUID id=p.getUniqueId();jumps.put(id,System.currentTimeMillis()+180_000L);
        flightOwned.add(id);jumpReady.add(id);p.setAllowFlight(true);
        p.sendMessage(ChatColor.LIGHT_PURPLE+"✦ Fairy Mushroom: Double Jump (3 นาที)");
    }
    public void bamboo(Player p){
        if(!enhanced()||isBedrock(p)) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST,6000,0));
            p.sendMessage(ChatColor.GOLD+"✦ Mountain Walker: Jump Boost I (5 นาที)");return;
        }
        var attr=p.getAttribute(Attribute.STEP_HEIGHT);
        if(attr==null){
            p.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST,6000,0));
            p.sendMessage(ChatColor.GOLD+"✦ Mountain Walker: Jump Boost I (5 นาที)");return;
        }
        attr.removeModifier(stepKey);
        double extra=Math.max(0,1.0-attr.getBaseValue());
        attr.addTransientModifier(new AttributeModifier(stepKey,extra,AttributeModifier.Operation.ADD_NUMBER));
        steps.put(p.getUniqueId(),System.currentTimeMillis()+300_000L);
        p.sendMessage(ChatColor.GOLD+"✦ Mountain Walker: Step Assist (5 นาที)");
    }
    public void glide(Player p){gliders.put(p.getUniqueId(),System.currentTimeMillis()+180_000L);}
    public void rescue(Player p){rescues.put(p.getUniqueId(),System.currentTimeMillis()+300_000L);}
    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true)
    public void flight(PlayerToggleFlightEvent event){
        Player p=event.getPlayer();UUID id=p.getUniqueId();
        if(!flightOwned.contains(id)||!survival(p))return;
        event.setCancelled(true);
        if(!active(jumps,id,System.currentTimeMillis())){releaseFlight(p);return;}
        if(!jumpReady.remove(id)||p.isInsideVehicle()||p.isGliding()||p.isInWater())return;
        p.setFlying(false);p.setAllowFlight(false);
        p.setFallDistance(0);
        Vector impulse=p.getLocation().getDirection().setY(0);
        if(impulse.lengthSquared()>0.0001)impulse.normalize().multiply(0.5);
        impulse.setY(0.58);p.setVelocity(impulse);
        p.playSound(p.getLocation(),Sound.ENTITY_BAT_TAKEOFF,0.8f,1.2f);
    }
    @EventHandler(priority=EventPriority.LOW,ignoreCancelled=true)
    public void onGroundContact(PlayerMoveEvent e){
        Player p=e.getPlayer();UUID id=p.getUniqueId();
        if(!flightOwned.contains(id)||!survival(p)||!active(jumps,id,System.currentTimeMillis()))return;
        if(!jumpReady.contains(id)&&p.isOnGround()&&!p.isInsideVehicle()&&!p.isGliding()&&!p.isInWater()){
            jumpReady.add(id);p.setAllowFlight(true);
        }
    }
    /** Called once per 10 server ticks, never per incoming movement packet. */
    public void tick(){
        long now=System.currentTimeMillis();
        Set<UUID> ids=new HashSet<>();ids.addAll(jumps.keySet());ids.addAll(steps.keySet());ids.addAll(gliders.keySet());ids.addAll(rescues.keySet());
        for(UUID id:ids){
            Player p=Bukkit.getPlayer(id);if(p==null)continue;
            if(p.isDead()||!survival(p)){clear(p);continue;}
            if(jumps.containsKey(id)) {
                if(!active(jumps,id,now)||!enhanced())releaseFlight(p);
                else if(p.isOnGround()&&!p.isInsideVehicle()&&!p.isGliding()&&!p.isInWater()){
                    jumpReady.add(id);p.setAllowFlight(true);
                }
            }
            if(steps.containsKey(id)&&(!active(steps,id,now)||!enhanced()))releaseStep(p);
            if(!active(gliders,id,now))gliders.remove(id);
            else if(p.isSneaking()&&!p.isOnGround()&&!p.isInsideVehicle()&&!p.isGliding()&&!p.isInWater())
                p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING,15,0,false,false,true));
            if(!active(rescues,id,now)){rescues.remove(id);rescueRetry.remove(id);}
            else if(rescueRetry.getOrDefault(id,0L)<=now&&
                    (p.getLocation().getY()<p.getWorld().getMinHeight()-5||p.getWorld().equals(plugin.world())&&p.getLocation().getY()<0)) {
                rescueRetry.put(id,now+1000L);
                Location target=safeGround(p.getLocation());
                if(target==null)target=safeGround(p.getWorld().getSpawnLocation());
                if(target!=null&&p.teleport(target,PlayerTeleportEvent.TeleportCause.PLUGIN)) {
                    p.setFallDistance(0);p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING,100,0));
                }
            }
        }
    }
    private Location safeGround(Location near){
        World w=near.getWorld();if(w==null||!w.isChunkLoaded(near.getBlockX()>>4,near.getBlockZ()>>4))return null;
        var floor=w.getHighestBlockAt(near);Material m=floor.getType();
        if(!m.isSolid()||m==Material.MAGMA_BLOCK||m==Material.CACTUS||m==Material.CAMPFIRE||m==Material.SOUL_CAMPFIRE)return null;
        Location target=floor.getLocation().add(0.5,1,0.5);
        if(target.getY()+1>=w.getMaxHeight()||!w.getWorldBorder().isInside(target))return null;
        if(!target.getBlock().isPassable()||target.getBlock().isLiquid()||!target.clone().add(0,1,0).getBlock().isPassable())return null;
        return target;
    }
    private void releaseFlight(Player p){
        UUID id=p.getUniqueId();jumps.remove(id);jumpReady.remove(id);
        if(flightOwned.remove(id)&&survival(p)){p.setFlying(false);p.setAllowFlight(false);}
    }
    private void releaseStep(Player p){steps.remove(p.getUniqueId());var attr=p.getAttribute(Attribute.STEP_HEIGHT);if(attr!=null)attr.removeModifier(stepKey);}
    public void clear(Player p){
        releaseFlight(p);releaseStep(p);gliders.remove(p.getUniqueId());rescues.remove(p.getUniqueId());rescueRetry.remove(p.getUniqueId());
        PlayerImpulse.clear(p.getUniqueId());
    }
    @EventHandler public void quit(PlayerQuitEvent e){clear(e.getPlayer());}
    @EventHandler public void death(PlayerDeathEvent e){clear(e.getEntity());}
    @EventHandler public void world(PlayerChangedWorldEvent e){clear(e.getPlayer());}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void mode(PlayerGameModeChangeEvent e){clear(e.getPlayer());}
    @Override public void close(){for(Player p:Bukkit.getOnlinePlayers())clear(p);jumps.clear();steps.clear();gliders.clear();rescues.clear();flightOwned.clear();jumpReady.clear();rescueRetry.clear();}
}
