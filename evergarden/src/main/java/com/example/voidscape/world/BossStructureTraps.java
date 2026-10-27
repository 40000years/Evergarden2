package com.example.voidscape.world;

import com.example.voidscape.VoidscapePlugin;
import org.bukkit.*;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.*;
import org.bukkit.potion.*;
import java.util.*;

/** Arkhaia's visible ward plates work while the castle remains protected from edits. */
public final class BossStructureTraps implements Listener {
    private final VoidscapePlugin plugin;
    private final Map<UUID,Long> cooldown=new HashMap<>();
    public BossStructureTraps(VoidscapePlugin plugin){this.plugin=plugin;}
    @EventHandler(priority=EventPriority.NORMAL,ignoreCancelled=true)
    public void step(PlayerInteractEvent event){
        var block=event.getClickedBlock();var player=event.getPlayer();
        if(event.getAction()!=Action.PHYSICAL||block==null||block.getWorld()!=plugin.world()
                ||block.getType()!=Material.POLISHED_BLACKSTONE_PRESSURE_PLATE
                ||player.getGameMode()!=GameMode.SURVIVAL&&player.getGameMode()!=GameMode.ADVENTURE
                ||!plugin.getConfig().getBoolean("structures.boss-structures.arkhaia-castle.traps.enabled",true)
                ||!plugin.getConfig().getBoolean("structures.boss-structures.arkhaia-castle.enabled",true))return;
        var site=plugin.bossStructures().at(block.getX(),block.getZ(),0);
        if(site==null||site.kind()!=BossStructureLayout.Kind.ARKHAIA||!plugin.bossStructures().enabled(site.kind()))return;
        var point=new BossStructure.Point(block.getX()-site.x(),block.getY(),block.getZ()-site.z());
        if(!site.blueprint().traps().contains(point))return;
        event.setCancelled(true);
        long now=System.nanoTime();
        Long last=cooldown.get(player.getUniqueId());
        if(last!=null&&now-last<2_000_000_000L)return;
        cooldown.put(player.getUniqueId(),now);
        double damage=plugin.getConfig().getDouble("structures.boss-structures.arkhaia-castle.traps.damage",4);
        if(!Double.isFinite(damage))damage=4;
        player.damage(Math.clamp(damage,0,20));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,40,0));
        var location=block.getLocation().add(.5,.3,.5);
        block.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME,location,16,.5,.25,.5,.025);
        block.getWorld().playSound(location,Sound.BLOCK_DISPENSER_LAUNCH,1,.65f);
    }
    @EventHandler public void quit(PlayerQuitEvent event){cooldown.remove(event.getPlayer().getUniqueId());}
}
