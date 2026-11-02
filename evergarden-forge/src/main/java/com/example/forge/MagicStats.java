package com.example.forge;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicePriority;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Feeds a forged staff's affixes and gems into Advance Magic's cast stats while the staff is in the main
 * hand. Advance Magic asks one ElementalStats.Provider (ServicesManager) for every cast and mana tick;
 * this registers that provider by reflection, so the forge still loads without Advance Magic, and keeps
 * calling a provider that was registered before it.
 */
final class MagicStats {
    private static final String STATS = "com.example.advancemagic.elemental.ElementalStats";
    private static final String ELEMENT = "com.example.advancemagic.item.ElementalStaffService$Element";
    /** Gem element stats and the Advance Magic elements they raise (the water gem covers ice too). */
    private static final Map<Stat, List<String>> ELEMENTS = Map.of(
        Stat.ELEMENT_FIRE, List.of("FIRE"), Stat.ELEMENT_WATER, List.of("WATER", "ICE"),
        Stat.ELEMENT_WIND, List.of("WIND"), Stat.ELEMENT_EARTH, List.of("EARTH"),
        Stat.ELEMENT_LIGHTNING, List.of("LIGHTNING"), Stat.ELEMENT_LIGHT, List.of("LIGHT"), Stat.ELEMENT_DARK, List.of("DARK"));

    private final ForgePlugin plugin;
    private final Method stats, spellPower, healingPower, maxMana, manaReduction, manaRegenBonus, elementalBonuses;
    private final Constructor<?> snapshot;
    private final Class<? extends Enum> element;
    private final Object previous;

    @SuppressWarnings({"unchecked", "rawtypes"})
    private MagicStats(ForgePlugin plugin, Plugin magic) throws ReflectiveOperationException {
        this.plugin = plugin;
        ClassLoader loader = magic.getClass().getClassLoader();
        Class<?> provider = Class.forName(STATS + "$Provider", true, loader);
        Class<?> record = Class.forName(STATS + "$Snapshot", true, loader);
        element = (Class<? extends Enum>) Class.forName(ELEMENT, true, loader);
        stats = provider.getMethod("stats", Player.class, record);
        spellPower = record.getMethod("spellPower");
        healingPower = record.getMethod("healingPower");
        maxMana = record.getMethod("maxMana");
        manaReduction = record.getMethod("manaReduction");
        manaRegenBonus = record.getMethod("manaRegenBonus");
        elementalBonuses = record.getMethod("elementalBonuses");
        snapshot = record.getConstructor(double.class, double.class, double.class, double.class, double.class, Map.class);
        RegisteredServiceProvider<?> registered = plugin.getServer().getServicesManager().getRegistration(provider);
        previous = registered == null ? null : registered.getProvider();
        Object proxy = Proxy.newProxyInstance(loader, new Class<?>[]{provider}, (self, method, args) -> switch (method.getName()) {
            case "stats" -> stats((Player) args[0], args[1]);
            case "hashCode" -> System.identityHashCode(self);
            case "equals" -> self == args[0];
            default -> "EvergardenForge staff stats";
        });
        plugin.getServer().getServicesManager().register((Class) provider, proxy, plugin, ServicePriority.High);
    }

    /** Registers when Advance Magic is enabled; false (and a log line) otherwise. */
    static boolean register(ForgePlugin plugin) {
        Plugin magic = plugin.getServer().getPluginManager().getPlugin("advance-magic");
        if (magic == null || !magic.isEnabled()) return false;
        try {
            new MagicStats(plugin, magic);
            plugin.getLogger().info("Staff affixes and gems feed Advance Magic spell stats");
            return true;
        } catch (ReflectiveOperationException | LinkageError error) {
            plugin.getLogger().warning("Advance Magic stat hook unavailable (" + error + "); staff affixes show but have no effect");
            return false;
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Object stats(Player player, Object base) throws Throwable {
        Object result = base;
        try {
            if (previous != null) {
                Object chained = stats.invoke(previous, player, base);
                if (chained != null) result = chained;
            }
            ItemStack held = player.getInventory().getItemInMainHand();
            AffixService affixes = plugin.affixes();
            if (affixes == null || affixes.category(held) != LootCategory.STAFF || affixes.read(held) == null) return result;
            Map bonuses = new EnumMap(element);
            bonuses.putAll((Map) elementalBonuses.invoke(result));
            ELEMENTS.forEach((stat, names) -> {
                double add = affixes.total(held, stat);
                if (add != 0) for (String name : names) bonuses.merge(Enum.valueOf(element, name), add, (a, b) -> (Double) a + (Double) b);
            });
            return snapshot.newInstance(
                (Double) spellPower.invoke(result) * (1 + affixes.total(held, Stat.SPELL_POWER)),
                (Double) healingPower.invoke(result) * (1 + affixes.total(held, Stat.HEALING_POWER)),
                (Double) maxMana.invoke(result) + affixes.total(held, Stat.MAX_MANA),
                (Double) manaReduction.invoke(result) + affixes.total(held, Stat.MANA_COST),
                (Double) manaRegenBonus.invoke(result) + affixes.total(held, Stat.MANA_REGEN),
                bonuses);
        } catch (InvocationTargetException error) {
            throw error.getCause();
        }
    }
}
