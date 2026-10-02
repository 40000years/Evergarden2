package com.example.sevensins;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import java.util.UUID;

/** The 7sins pack is paused; bosses use their vanilla armor and weapon visuals. */
public final class BossPacks implements Listener, AutoCloseable {
    public static final UUID PACK_ID = UUID.fromString("c67c5645-89c9-4a41-a8ef-bd93e55280c5");
    private final SevenSinsPlugin plugin;

    BossPacks(SevenSinsPlugin plugin) { this.plugin = plugin; }

    void start() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.removeResourcePack(PACK_ID);
            plugin.refresh(player);
        }
        plugin.getLogger().info("7sins resource pack paused; using vanilla boss visuals.");
    }

    public boolean loaded(Player player) { return false; }
    public String status(Player player) { return "ปิด resource pack ของ 7sins ชั่วคราว; ใช้ร่างเกราะสำรอง"; }
    public void send(Player player) { player.sendMessage("§6[7sins] " + status(player)); }

    @EventHandler public void join(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!event.getPlayer().isOnline()) return;
            plugin.refresh(event.getPlayer());
        }, 40);
    }

    @Override public void close() {
        for (Player player : Bukkit.getOnlinePlayers()) player.removeResourcePack(PACK_ID);
    }
}
