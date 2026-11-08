package com.example.forge;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.MagmaCube;
import org.bukkit.entity.Phantom;
import org.bukkit.entity.Slime;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;

/** Optional LM3/LM4 integration; Forge elites own their stats, name and rewards. */
final class LevelledMobsCompat {
    private static final NamespacedKey NO_LEVEL = new NamespacedKey("levelledmobs", "nolevel");
    private static final NamespacedKey DENY_NAMETAG = new NamespacedKey("levelledmobs", "denylm_nametag");
    private static final NamespacedKey LEVEL = new NamespacedKey("levelledmobs", "level");
    private static final NamespacedKey NATIVE_STATS = new NamespacedKey("evergardenforge", "elite_native_stats");
    private final ForgePlugin plugin;
    private final Map<MobVariant, Map<Attribute, Double>> vanillaBases = new HashMap<>();
    private Object levelManager;
    private MethodHandle removeLevel;
    private boolean warned;

    LevelledMobsCompat(ForgePlugin plugin) {
        this.plugin = plugin;
    }

    void register() {
        Plugin lm = plugin.getServer().getPluginManager().getPlugin("LevelledMobs");
        if (lm == null || !lm.isEnabled()) return;
        try {
            // Class.getMethod enumerates LM's other signatures too, which can resolve optional
            // PlaceholderAPI types even when PAPI is absent. Look up only these exact signatures.
            Class<?> api = Class.forName(lm.getClass().getPackageName() + ".LevelInterface2", true, lm.getClass().getClassLoader());
            levelManager = MethodHandles.publicLookup().findVirtual(lm.getClass(), "getLevelInterface", MethodType.methodType(api))
                .invoke(lm);
            removeLevel = MethodHandles.publicLookup().findVirtual(api, "removeLevel", MethodType.methodType(void.class, LivingEntity.class));
        } catch (Throwable error) {
            if (error instanceof VirtualMachineError fatal) throw fatal;
            warn("Could not access LevelledMobs removal API; using persistent data and attribute cleanup", error);
        }
        int hooked = 0;
        for (String prefix : List.of("io.github.arcaneplugins.levelledmobs", "me.lokka30.levelledmobs")) {
            for (String name : List.of("MobPreLevelEvent", "SummonedMobPreLevelEvent")) {
                try {
                    Class<?> raw = Class.forName(prefix + ".events." + name, true, lm.getClass().getClassLoader());
                    if (!Event.class.isAssignableFrom(raw) || !Cancellable.class.isAssignableFrom(raw)) continue;
                    Method getEntity = raw.getMethod("getEntity");
                    plugin.getServer().getPluginManager().registerEvent(raw.asSubclass(Event.class), new Listener() {},
                        EventPriority.HIGHEST, (listener, event) -> {
                            try {
                                if (getEntity.invoke(event) instanceof Entity entity && plugin.elites().rarity(entity) != null)
                                    ((Cancellable) event).setCancelled(true);
                            } catch (ReflectiveOperationException error) {
                                warn("Could not check LevelledMobs pre-level event", error);
                            }
                        }, plugin, true);
                    hooked++;
                } catch (ClassNotFoundException ignored) {
                    // Different LM major version, or no summoned event in that version.
                } catch (ReflectiveOperationException | LinkageError error) {
                    warn("Could not hook LevelledMobs " + name, error);
                }
            }
        }
        plugin.getLogger().info("LevelledMobs: Forge elites excluded (" + hooked + " pre-level hooks).");
    }

    boolean protect(LivingEntity entity) {
        return protect(entity, false);
    }

    /**
     * Block queued/future levelling first, then remove any scaling applied before conversion.
     * @param keepBases leave base values alone: a hosted elite's owner sets them (EliteService).
     */
    boolean protect(LivingEntity entity, boolean keepBases) {
        var pdc = entity.getPersistentDataContainer();
        boolean levelled = pdc.has(LEVEL);
        boolean contaminated = levelled;
        pdc.set(NO_LEVEL, PersistentDataType.STRING, "EvergardenForge elite");
        pdc.set(DENY_NAMETAG, PersistentDataType.INTEGER, 1);
        entity.addScoreboardTag("evergardenforge_elite");

        // LM removeLevel also deletes unrelated non-vanilla modifiers. Preserve those, including
        // Forge modifiers on an elite saved by an older release. Modern LM uses the attribute's
        // own minecraft key for its modifier; LM3 uses GENERIC_MAX_HEALTH etc. as the name.
        List<KeptModifier> kept = new ArrayList<>();
        for (Attribute attribute : Registry.ATTRIBUTE) {
            AttributeInstance instance = entity.getAttribute(attribute);
            if (instance == null) continue;
            for (AttributeModifier modifier : instance.getModifiers()) {
                if (isLevelledModifier(attribute, modifier)) contaminated = true;
                else kept.add(new KeptModifier(instance, modifier));
            }
        }
        try {
            if (levelled && removeLevel != null) removeLevel.invoke(levelManager, entity);
        } catch (Throwable error) {
            if (error instanceof VirtualMachineError fatal) throw fatal;
            warn("Could not remove existing LevelledMobs level; using attribute cleanup", error);
        } finally {
            for (KeptModifier entry : kept) {
                if (entry.instance().getModifier(entry.modifier().getKey()) == null)
                    entry.instance().addModifier(entry.modifier());
            }
        }
        for (Attribute attribute : Registry.ATTRIBUTE) {
            AttributeInstance instance = entity.getAttribute(attribute);
            if (instance == null) continue;
            for (AttributeModifier modifier : List.copyOf(instance.getModifiers())) {
                if (isLevelledModifier(attribute, modifier)) instance.removeModifier(modifier);
            }
        }
        if (contaminated) pdc.set(NATIVE_STATS, PersistentDataType.BYTE, (byte) 1);
        if (!keepBases && pdc.has(NATIVE_STATS, PersistentDataType.BYTE)) {
            // LM rules can replace base values as well as modifiers. Attribute.getDefaultValue
            // is a global default (20 HP), so use an unspawned type template (Enderman: 40 HP).
            // Keep enforcing these bases: LM may already have queued a synchronous base-only
            // write when the original level marker is removed. Later writes need no level key.
            int size = entity instanceof Slime slime ? slime.getSize() : entity instanceof MagmaCube cube ? cube.getSize()
                : entity instanceof Phantom phantom ? phantom.getSize() : 0;
            boolean baby = entity instanceof Ageable ageable && !ageable.isAdult();
            MobVariant variant = new MobVariant(entity.getType(), size, baby);
            Map<Attribute, Double> bases = vanillaBases.computeIfAbsent(variant, type -> {
                Map<Attribute, Double> values = new HashMap<>();
                Entity template = entity.getWorld().createEntity(entity.getLocation(), type.type().getEntityClass());
                // Initialize size-dependent attributes. MagmaCube is a separate API type on
                // newer Paper versions, so it must be handled explicitly alongside Slime.
                if (template instanceof Slime slime) {
                    slime.setSize(size == 1 ? 2 : 1);
                    slime.setSize(size);
                }
                if (template instanceof MagmaCube cube) {
                    cube.setSize(size == 1 ? 2 : 1);
                    cube.setSize(size);
                }
                if (template instanceof Phantom phantom) {
                    phantom.setSize(size == 0 ? 1 : 0);
                    phantom.setSize(size);
                }
                if (template instanceof Ageable ageable) {
                    if (baby) ageable.setBaby(); else ageable.setAdult();
                }
                if (template instanceof LivingEntity living) {
                    for (Attribute attribute : Registry.ATTRIBUTE) {
                        AttributeInstance instance = living.getAttribute(attribute);
                        if (instance != null) values.put(attribute, instance.getBaseValue());
                    }
                }
                return values;
            });
            bases.forEach((attribute, base) -> {
                AttributeInstance instance = entity.getAttribute(attribute);
                if (instance != null) instance.setBaseValue(base);
            });
        }
        // Remove the level and cached damage/drop/name settings, so LM's damage/death listeners
        // also treat this as unlevelled. Retain only LM's actual exclusion keys.
        for (NamespacedKey key : List.copyOf(pdc.getKeys())) {
            if (key.getNamespace().equals("levelledmobs") && !key.equals(NO_LEVEL) && !key.equals(DENY_NAMETAG))
                pdc.remove(key);
        }
        return levelled;
    }

    @SuppressWarnings("deprecation")
    private static boolean isLevelledModifier(Attribute attribute, AttributeModifier modifier) {
        String name = modifier.getName();
        String legacy = attribute.getKey().getKey().replace('.', '_').toUpperCase(Locale.ROOT);
        return modifier.getKey().equals(attribute.getKey())
            || modifier.getKey().getNamespace().equals("levelledmobs")
            || name.equals(legacy) || name.equals("GENERIC_" + legacy);
    }

    private void warn(String message, Throwable error) {
        if (warned) return;
        warned = true;
        plugin.getLogger().log(Level.WARNING, message, error);
    }

    private record KeptModifier(AttributeInstance instance, AttributeModifier modifier) {}
    private record MobVariant(EntityType type, int size, boolean baby) {}
}
