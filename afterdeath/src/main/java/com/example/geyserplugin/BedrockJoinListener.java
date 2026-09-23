package com.example.geyserplugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class BedrockJoinListener implements Listener {

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        GeyserExamplePlugin plugin = GeyserExamplePlugin.getInstance();

        if (plugin.isBedrockPlayer(event.getPlayer().getUniqueId())) {
            // ปรับพฤติกรรมเฉพาะผู้เล่น Bedrock เช่น ส่งข้อความต้อนรับ
            event.getPlayer().sendMessage(
                Component.text("ยินดีต้อนรับผู้เล่น Bedrock Edition!", NamedTextColor.LIGHT_PURPLE)
            );
        }
    }
}
