package com.example.voidscape.guide;

import com.example.voidscape.VoidscapePlugin;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

public final class BedrockGuideService {
    private static boolean floodgateAvailable = false;

    static {
        try {
            Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            Class.forName("org.geysermc.cumulus.form.SimpleForm");
            floodgateAvailable = true;
        } catch (Throwable ignored) {
            floodgateAvailable = false;
        }
    }

    private BedrockGuideService() {}

    public static boolean isBedrock(Player player) {
        if (player == null) return false;
        if (player.getName().startsWith(".")) return true;
        UUID uuid = player.getUniqueId();
        if (floodgateAvailable) {
            try {
                if (org.geysermc.floodgate.api.FloodgateApi.getInstance().isFloodgatePlayer(uuid)) {
                    return true;
                }
            } catch (Throwable ignored) {}
        }
        for (String name : List.of("org.geysermc.floodgate.api.FloodgateApi", "org.geysermc.geyser.api.GeyserApi")) {
            try {
                Class<?> type = Class.forName(name);
                boolean floodgate = name.contains("floodgate");
                Object api = type.getMethod(floodgate ? "getInstance" : "api").invoke(null);
                if ((boolean) type.getMethod(floodgate ? "isFloodgatePlayer" : "isBedrockPlayer", UUID.class).invoke(api, uuid)) {
                    return true;
                }
            } catch (Throwable ignored) {}
        }
        return false;
    }

    public static void openMenu(VoidscapePlugin plugin, Player player) {
        if (player == null || !player.isOnline()) return;

        if (isBedrock(player) && floodgateAvailable) {
            try {
                if (FloodgateGuideForm.sendBookSelector(plugin, player)) {
                    player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.8f, 1.0f);
                    return;
                }
            } catch (Throwable t) {
                plugin.getLogger().warning("Could not send Floodgate book selector to " + player.getName() + ": " + t.getMessage());
            }
        }

        plugin.guideMenu().open(player);
    }

    public static void openGuide(VoidscapePlugin plugin, Player player, GuideBookType type, int pageIndex) {
        if (player == null || !player.isOnline()) return;
        if (type == null) type = GuideBookType.CROPS;

        if (isBedrock(player)) {
            if (floodgateAvailable) {
                try {
                    if (FloodgateGuideForm.sendPage(plugin, player, type, pageIndex)) {
                        player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.8f, 1.0f);
                        return;
                    }
                } catch (Throwable t) {
                    plugin.getLogger().warning("Could not send Floodgate guide form to " + player.getName() + ": " + t.getMessage());
                }
            }
            // Fallback for Bedrock without Floodgate forms
            ChestGuideGui.open(plugin, player, type, pageIndex);
        } else {
            // Java player: native virtual book
            player.openBook(plugin.relics().createGuideBook(type));
            player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.8f, 1.0f);
        }
    }

    public static void openGuide(VoidscapePlugin plugin, Player player, int pageIndex) {
        openGuide(plugin, player, GuideBookType.CROPS, pageIndex);
    }

    public static void openIndex(VoidscapePlugin plugin, Player player, GuideBookType type, int returnPageIndex) {
        if (player == null || !player.isOnline()) return;
        if (type == null) type = GuideBookType.CROPS;

        if (isBedrock(player)) {
            if (floodgateAvailable) {
                try {
                    if (FloodgateGuideForm.sendIndex(plugin, player, type, returnPageIndex)) {
                        player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.8f, 1.0f);
                        return;
                    }
                } catch (Throwable t) {
                    plugin.getLogger().warning("Could not send Floodgate guide index to " + player.getName() + ": " + t.getMessage());
                }
            }
            ChestGuideGui.openIndex(plugin, player, type, returnPageIndex);
        } else {
            player.openBook(plugin.relics().createGuideBook(type));
            player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.8f, 1.0f);
        }
    }

    public static void openIndex(VoidscapePlugin plugin, Player player, int returnPageIndex) {
        openIndex(plugin, player, GuideBookType.CROPS, returnPageIndex);
    }
}
