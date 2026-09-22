package com.example.voidscape.compat;

import org.bukkit.GameMode;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerImpulse {
    private static final Map<UUID, Long> playerCooldowns = new ConcurrentHashMap<>();

    private PlayerImpulse(){}

    public static void apply(Plugin plugin, Entity target, Vector requested){
        Vector velocity = requested.clone();
        velocity.checkFinite();
        if (target instanceof Player player) {
            if (!plugin.getConfig().getBoolean("compatibility.player-impulses", true)
                || player.isInsideVehicle()
                || player.getGameMode() == GameMode.CREATIVE
                || player.getGameMode() == GameMode.SPECTATOR) return;

            long now = System.currentTimeMillis();
            Long next = playerCooldowns.get(player.getUniqueId());
            if (next != null && now < next) return; // Prevent velocity spam
            playerCooldowns.put(player.getUniqueId(), now + 250L);

            double maxHorizontal = plugin.getConfig().getDouble("compatibility.max-impulse-horizontal", 1.2);
            double maxVertical = plugin.getConfig().getDouble("compatibility.max-impulse-vertical", 1.0);

            double horizontal = Math.hypot(velocity.getX(), velocity.getZ());
            if (horizontal > maxHorizontal) {
                velocity.setX(velocity.getX() * maxHorizontal / horizontal);
                velocity.setZ(velocity.getZ() * maxHorizontal / horizontal);
            }
            velocity.setY(Math.max(-maxVertical, Math.min(maxVertical, velocity.getY())));
        }
        target.setVelocity(velocity);
    }

    public static void clear(UUID playerId) {
        playerCooldowns.remove(playerId);
    }
}
