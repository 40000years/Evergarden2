package com.example.voidscape.gui;

import com.example.voidscape.VoidscapePlugin;
import com.example.voidscape.guide.BedrockGuideService;
import org.bukkit.entity.Player;

public final class UpgradeMenuService {
    private final VoidscapePlugin plugin;

    public UpgradeMenuService(VoidscapePlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Opens the optimal upgrade interface depending on the player's platform.
     * Bedrock players get a native touchscreen SimpleForm, while Java players get a Chest GUI.
     */
    public static void open(VoidscapePlugin plugin, Player player) {
        if (player == null || !player.isOnline()) return;

        if (BedrockGuideService.isBedrock(player)) {
            try {
                if (FloodgateUpgradeForm.open(plugin, player, -1)) {
                    return;
                }
            } catch (Throwable t) {
                plugin.getLogger().warning("Could not open Bedrock upgrade form for " + player.getName() + ": " + t.getMessage());
            }
        }

        // Fallback to chest GUI
        ChestUpgradeGui.open(plugin, player);
    }
}
