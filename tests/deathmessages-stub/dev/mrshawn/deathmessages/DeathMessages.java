package dev.mrshawn.deathmessages;

import dev.mrshawn.deathmessages.api.events.BroadcastDeathMessageEvent;
import dev.mrshawn.deathmessages.enums.MessageType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

/**
 * Test stand-in for the Winds-Studio DeathMessages flow, written for Evergarden's probes (the real JAR is not run here).
 * Like the real plugin with its defaults: it blanks the vanilla message at HIGHEST, remembers the last living damager
 * for 7 seconds, picks a line at HIGH (only melee from a mob names the killer; other causes fall back to a natural line
 * without one), fires BroadcastDeathMessageEvent with {prefix, body}, and sends that array to the world at NORMAL.
 */
public final class DeathMessages extends JavaPlugin implements Listener {
    private record Hit(LivingEntity damager, long at) {}
    private final Map<UUID, Hit> last = new HashMap<>();

    @Override public void onEnable() { Bukkit.getPluginManager().registerEvents(this, this); }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void blankVanilla(PlayerDeathEvent event) { event.deathMessage(null); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void remember(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player player && event.getDamager() instanceof LivingEntity damager)
            last.put(player.getUniqueId(), new Hit(damager, System.currentTimeMillis()));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void death(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        Hit hit = last.remove(player.getUniqueId());
        LivingEntity killer = hit != null && System.currentTimeMillis() - hit.at() <= 7000 ? hit.damager() : null;
        EntityDamageEvent cause = player.getLastDamageCause();
        String reason = cause == null ? "CUSTOM" : cause.getCause().name();
        String body; MessageType type;
        if (killer != null && reason.equals("ENTITY_ATTACK")) { body = player.getName() + " was slain by " + killer.getName(); type = MessageType.MOB; }
        else if (killer != null) { body = player.getName() + " was killed by " + reason.toLowerCase(Locale.ROOT); type = MessageType.MOB; }
        else { body = player.getName() + " died"; type = MessageType.NATURAL; }
        TextComponent[] components = {Component.text("[DM] "), Component.text(body)};
        Bukkit.getPluginManager().callEvent(new BroadcastDeathMessageEvent(player, killer, type, components, List.of(player.getWorld()), false));
    }

    @EventHandler
    public void broadcast(BroadcastDeathMessageEvent event) {
        TextComponent[] components = event.getTextComponents();
        Component message = components[0].append(components[1]);
        for (World world : event.getBroadcastedWorlds()) for (Player other : world.getPlayers()) other.sendMessage(message);
    }
}
