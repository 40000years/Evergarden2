package com.example.voidscape.compat;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.List;
import java.util.logging.Level;

public final class LevelledMobsCompat {
    private static final NamespacedKey EVERGARDEN_MOB_KEY = new NamespacedKey("voidscape", "dungeon_mob");
    private static final NamespacedKey LM_NO_LEVEL = new NamespacedKey("levelledmobs", "no-level");
    private static final NamespacedKey LM_IGNORE = new NamespacedKey("levelledmobs", "ignore");
    private static final NamespacedKey LM_CUSTOM_LEVEL = new NamespacedKey("levelledmobs", "custom-level");
    private static final NamespacedKey LM_SKIP = new NamespacedKey("levelledmobs", "skip");

    private LevelledMobsCompat() {}

    /**
     * Dynamically registers reflection-based event listeners for LevelledMobs events
     * so that LevelledMobs does not process or level up any Evergarden mobs.
     */
    public static void register(Plugin plugin) {
        List<String> eventClassNames = List.of(
            "me.lokka30.levelledmobs.events.PreMobLevelUpEvent",
            "me.lokka30.levelledmobs.events.MobLevelUpEvent"
        );

        for (String className : eventClassNames) {
            try {
                Class<?> rawClass = Class.forName(className);
                if (org.bukkit.event.Event.class.isAssignableFrom(rawClass)) {
                    Class<? extends org.bukkit.event.Event> eventClass = rawClass.asSubclass(org.bukkit.event.Event.class);
                    plugin.getServer().getPluginManager().registerEvent(
                        eventClass,
                        new Listener() {},
                        EventPriority.LOWEST,
                        (listener, event) -> handleLevelMobEvent(event),
                        plugin,
                        true
                    );
                    plugin.getLogger().info("LevelledMobs compatibility: Hooked " + className + " successfully.");
                }
            } catch (ClassNotFoundException ignored) {
                // LevelledMobs not installed or different package structure
            } catch (Throwable t) {
                plugin.getLogger().log(Level.WARNING, "LevelledMobs compatibility: Could not hook " + className, t);
            }
        }
    }

    private static void handleLevelMobEvent(org.bukkit.event.Event event) {
        try {
            Method getEntityMethod = event.getClass().getMethod("getEntity");
            Object obj = getEntityMethod.invoke(event);
            if (obj instanceof Entity entity && isEvergardenMob(entity)) {
                Method setCancelledMethod = event.getClass().getMethod("setCancelled", boolean.class);
                setCancelledMethod.invoke(event, true);
            }
        } catch (Exception ignored) {}
    }

    /**
     * Checks if an entity is an Evergarden mob.
     */
    public static boolean isEvergardenMob(Entity entity) {
        if (entity == null) return false;
        if (entity.getScoreboardTags().contains("evergarden_mob")
            || entity.getScoreboardTags().contains("no-level")
            || entity.getScoreboardTags().contains("levelledmobs:ignore")) {
            return true;
        }
        var pdc = entity.getPersistentDataContainer();
        return pdc.has(EVERGARDEN_MOB_KEY, PersistentDataType.BYTE)
            || pdc.has(LM_NO_LEVEL, PersistentDataType.BYTE)
            || pdc.has(LM_IGNORE, PersistentDataType.BYTE);
    }

    /**
     * Tags a mob with all known LevelledMobs ignore PDC keys, scoreboard tags, and metadata
     * to prevent external levelling plugins from modifying attributes or healing the mob.
     */
    public static void tagMob(Mob mob, Plugin plugin, boolean isBoss) {
        if (mob == null || !mob.isValid() || mob.isDead()) return;

        // Scoreboard tags
        mob.addScoreboardTag("evergarden_mob");
        mob.addScoreboardTag("no-level");
        mob.addScoreboardTag("levelledmobs:ignore");
        mob.addScoreboardTag("levelledmobs_ignore");
        mob.addScoreboardTag("lm-ignore");
        mob.addScoreboardTag("custom");
        if (isBoss) {
            mob.addScoreboardTag("boss");
            mob.addScoreboardTag("custom-boss");
        }

        // PDC tags
        var pdc = mob.getPersistentDataContainer();
        pdc.set(LM_NO_LEVEL, PersistentDataType.BYTE, (byte) 1);
        pdc.set(LM_IGNORE, PersistentDataType.BYTE, (byte) 1);
        pdc.set(LM_CUSTOM_LEVEL, PersistentDataType.INTEGER, 1);
        pdc.set(LM_SKIP, PersistentDataType.BYTE, (byte) 1);

        // Metadata
        mob.setMetadata("levelledmobs:ignore", new FixedMetadataValue(plugin, true));
        mob.setMetadata("no-level", new FixedMetadataValue(plugin, true));
        mob.setMetadata("lm:ignore", new FixedMetadataValue(plugin, true));
    }
}
