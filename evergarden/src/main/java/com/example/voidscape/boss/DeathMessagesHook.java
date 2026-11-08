package com.example.voidscape.boss;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Array;
import java.lang.reflect.Method;

/**
 * Boss kills keep the boss's name when the DeathMessages plugin (Winds-Studio, 1.4.20+) broadcasts deaths.
 *
 * <p>DeathMessages blanks the vanilla death message and builds its own from templates chosen by the killer's entity
 * type and the damage cause. A boss hits through an invisible proxy (a Slime anchor, a Wither Skeleton carrier) with
 * causes such as INDIRECT_MAGIC, for which the default templates fall back to a natural-death line without a killer,
 * so naming the proxy is not enough. Its BroadcastDeathMessageEvent carries the line as {prefix, body}, and its own
 * broadcaster reads that same array at NORMAL priority; at LOWEST the body is replaced with the boss line. The prefix,
 * worlds, cooldowns and Discord forwarding stay DeathMessages'. Reflection only: Evergarden never depends on it.
 */
final class DeathMessagesHook {
    private static final String EVENT = "dev.mrshawn.deathmessages.api.events.BroadcastDeathMessageEvent";
    private static boolean hooked, warned;

    private DeathMessagesHook() {}

    static void register(JavaPlugin plugin) {
        hook(plugin);
        if (hooked) return;
        // DeathMessages may load after Evergarden: hook it when it enables.
        plugin.getServer().getPluginManager().registerEvents(new Listener() {
            @EventHandler public void enable(PluginEnableEvent event) {
                if (event.getPlugin().getName().equals("DeathMessages")) hook(plugin);
            }
        }, plugin);
    }

    private static void hook(JavaPlugin plugin) {
        if (hooked) return;
        Plugin deathMessages = Bukkit.getPluginManager().getPlugin("DeathMessages");
        if (deathMessages == null) return;
        try {
            Class<?> raw = Class.forName(EVENT, true, deathMessages.getClass().getClassLoader());
            if (!Event.class.isAssignableFrom(raw)) return;
            Method victim = raw.getMethod("getPlayer"), lines = raw.getMethod("getTextComponents");
            plugin.getServer().getPluginManager().registerEvent(raw.asSubclass(Event.class), new Listener() {}, EventPriority.LOWEST,
                (listener, event) -> {
                    if (!raw.isInstance(event)) return;
                    try {
                        if (!(victim.invoke(event) instanceof Player player)) return;
                        String boss = BossDamage.deathBoss(player);
                        if (boss == null) return;
                        Object components = lines.invoke(event);
                        if (components == null || !components.getClass().isArray() || Array.getLength(components) == 0) return;
                        // {prefix, body}; a single-element array is the whole line.
                        Array.set(components, Math.min(1, Array.getLength(components) - 1), BossDamage.deathMessage(player, boss));
                        BossDamage.announcedElsewhere(player);
                    } catch (ReflectiveOperationException | RuntimeException error) {
                        warn(plugin, error);
                    }
                }, plugin, false);
            hooked = true;
            plugin.getLogger().info("DeathMessages " + deathMessages.getPluginMeta().getVersion() + " found: boss kills are announced with the boss's name.");
        } catch (ReflectiveOperationException | LinkageError error) {
            warn(plugin, error);
        }
    }

    private static void warn(JavaPlugin plugin, Throwable error) {
        if (warned) return;
        warned = true;
        plugin.getLogger().warning("DeathMessages hook unavailable; boss kills keep DeathMessages' own text: " + error);
    }
}
