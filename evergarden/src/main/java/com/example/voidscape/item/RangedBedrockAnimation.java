package com.example.voidscape.item;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Geyser registers custom bow/crossbow definitions with minecraft:shooter but no use_animation, so a
 * Bedrock client does not treat them as drawn like a bow or charged like a crossbow. Once Geyser has
 * built its item tables, add the animation exactly as Geyser itself writes it for a consumable with a
 * bow/crossbow animation (item_properties.use_animation 4 or 9 plus minecraft:use_animation), without
 * the food component that path would also add. The player arm poses come from the resource pack's
 * player animation controller. No Geyser classes are linked; a missing or different Geyser leaves the
 * definitions untouched.
 */
final class RangedBedrockAnimation {
    /** Bedrock identifier -> {use_animation name, network id} (Geyser's BEDROCK_ANIMATIONS: BOW 4, CROSSBOW 9). */
    static final Map<String, Object[]> ANIMATIONS = Map.of(
            "voidscape:adventurer_thornwhisper", new Object[]{"bow", 4},
            "voidscape:adventurer_runelock", new Object[]{"crossbow", 9},
            "voidscape:adventurer_runelock_loaded_arrow", new Object[]{"crossbow", 9},
            "voidscape:adventurer_runelock_loaded_rocket", new Object[]{"crossbow", 9});
    /** Geyser-Spigot builds its item tables as it starts listening, at the end of server startup: poll every tick for two minutes. */
    private static final int ATTEMPTS = 2400;

    private RangedBedrockAnimation() {}

    static void schedule(Plugin plugin) {
        int[] attempts = {0};
        Consumer<BukkitTask> attempt = task -> {
            int patched = apply(plugin.getLogger());
            if (patched == 0 && ++attempts[0] < ATTEMPTS) return;
            task.cancel();
            if (patched > 0) plugin.getLogger().info("Bedrock bow/crossbow draw animation added to " + patched + " Geyser definitions");
            else if (patched == 0) plugin.getLogger().warning("Geyser has no Thornwhisper/Runelock definitions; Bedrock draw animation not added");
        };
        Bukkit.getScheduler().runTaskTimer(plugin, attempt, 1L, 1L);
    }

    /** Returns how many protocol definitions were updated now, 0 if none were ready, or -1 if Geyser is absent or unreadable. */
    static int apply(Logger logger) {
        var geyser = Bukkit.getPluginManager().getPlugin("Geyser-Spigot");
        if (geyser == null || !geyser.isEnabled()) return -1;
        int patched = 0;
        try {
            ClassLoader loader = geyser.getClass().getClassLoader();
            Object registry = loader.loadClass("org.geysermc.geyser.registry.Registries").getField("ITEMS").get(null);
            Map<?, ?> protocols = (Map<?, ?>) registry.getClass().getMethod("get").invoke(registry);
            Class<?> definitionType = loader.loadClass("org.cloudburstmc.protocol.bedrock.data.definitions.SimpleItemDefinition");
            Class<?> nbtType = loader.loadClass("org.cloudburstmc.nbt.NbtMap");
            Field data = definitionType.getDeclaredField("componentData");
            data.setAccessible(true);
            Method identifier = definitionType.getMethod("getIdentifier"), builder = nbtType.getMethod("builder"), toBuilder = nbtType.getMethod("toBuilder"),
                    compound = nbtType.getMethod("getCompound", String.class), contains = nbtType.getMethod("containsKey", Object.class);
            Class<?> builderType = builder.getReturnType();
            Method putCompound = builderType.getMethod("putCompound", String.class, nbtType), putInt = builderType.getMethod("putInt", String.class, int.class),
                    putString = builderType.getMethod("putString", String.class, String.class), build = builderType.getMethod("build");
            for (Object mappings : protocols.values()) {
                // The same objects Geyser sends in each session's ItemComponentPacket.
                Map<?, ?> definitions = (Map<?, ?>) mappings.getClass().getMethod("getItemDefinitions").invoke(mappings);
                for (Object definition : definitions.values()) {
                    if (!definitionType.isInstance(definition)) continue;
                    Object[] animation = ANIMATIONS.get(identifier.invoke(definition));
                    Object root = animation == null ? null : data.get(definition);
                    if (root == null) continue;
                    Object components = compound.invoke(root, "components");
                    if ((boolean) contains.invoke(components, "minecraft:use_animation")) continue;
                    Object properties = toBuilder.invoke(compound.invoke(components, "item_properties"));
                    putInt.invoke(properties, "use_animation", (int) animation[1]);
                    Object use = builder.invoke(null);
                    putString.invoke(use, "value", (String) animation[0]);
                    Object newComponents = toBuilder.invoke(components);
                    putCompound.invoke(newComponents, "item_properties", build.invoke(properties));
                    putCompound.invoke(newComponents, "minecraft:use_animation", build.invoke(use));
                    Object newRoot = toBuilder.invoke(root);
                    putCompound.invoke(newRoot, "components", build.invoke(newComponents));
                    data.set(definition, build.invoke(newRoot));
                    patched++;
                }
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            logger.warning("Bedrock bow/crossbow draw animation unavailable: " + error);
            return -1;
        }
        return patched;
    }
}
