package com.example.forge;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.World;
import org.bukkit.entity.EntityType;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.logging.Logger;

/** Snapshot of config.yml; rebuilt on /forge reload. */
public final class ForgeSettings {
    public record Grade(int weight, int affixes, double levelLow, double levelHigh, int socketsMin, int socketsMax,
                        int reforgeMaterials, int reforgeLevels, int salvageMin, int salvageMax) {}

    public record Range(boolean enabled, double min, double max) {
        public double at(double level) {
            return min + (max - min) * level;
        }
    }

    public record EliteGrade(double health, double damage, double speed, double knockbackResist,
                             List<String> armor, double armorChance,
                             int gear, double gemChance, int materialsMin, int materialsMax, int xp) {}

    public final boolean commandAnywhere;
    public final org.bukkit.Material stationBlock;
    public final String iconNamespace;
    public final Map<Rarity, Grade> grades = new EnumMap<>(Rarity.class);
    public final int[] choiceMultipliers = {1, 2, 3};
    public final Rarity maxReforgeRarity;
    public final double salvagePlainChance;
    public final Map<AffixType, Range> affixes = new EnumMap<>(AffixType.class);
    public final Set<String> protectedKeys = new HashSet<>();
    public final List<String> protectedPrefixes = new ArrayList<>();

    public final double[] gemTierMultipliers = {1.0, 1.5, 2.1, 2.8, 3.6};
    public final int[] cutDust = {2, 4, 8, 12};
    public final int[] cutMaterials = {1, 1, 2, 2};
    public final int[] gemSalvageDust = {1, 2, 4, 7, 11};
    private final Map<Rarity, int[]> gemTierRanges = new EnumMap<>(Rarity.class);

    public final Map<String, Integer> gearKinds = new LinkedHashMap<>();
    /** Base material families by world environment, then rarity. */
    public final Map<World.Environment, Map<Rarity, List<String>>> gearMaterials = new EnumMap<>(World.Environment.class);
    public final double gemMobDropChance;
    /** False (default): netherite in any gear or elite armor list is read as diamond, whatever an old config says. */
    public final boolean allowNetherite;

    public final boolean elitesEnabled;
    public final double eliteChance;
    public final int elitesPerWorld;
    public final Set<String> eliteBlockedWorlds = new HashSet<>();
    public final Set<EntityType> eliteTypes = EnumSet.noneOf(EntityType.class);
    public final Set<String> eliteSkipNamespaces = new HashSet<>();
    /** Spawn reasons that may produce elites, with a multiplier on elites.chance. */
    public final Map<org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason, Double> eliteReasonChance =
        new EnumMap<>(org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.class);
    /** Types that gain health but not damage as elites. */
    public final Set<EntityType> eliteDamageExempt = EnumSet.noneOf(EntityType.class);
    public final Map<World.Environment, Map<Rarity, Integer>> eliteWeights = new EnumMap<>(World.Environment.class);
    public final Map<Rarity, EliteGrade> eliteGrades = new EnumMap<>(Rarity.class);

    public final boolean evergardenChestsEnabled, evergardenEndCityOutsideEnd;
    public final List<Pattern> evergardenChunkMarkers = new ArrayList<>();
    public final double evergardenGemChance, evergardenGemExtra, evergardenGearChance, evergardenGearExtra;
    public final int evergardenGemMax, evergardenGearMax;
    public final Map<Rarity, Integer> evergardenWeights = new EnumMap<>(Rarity.class);

    public final boolean oreGemsEnabled;
    public final Set<String> oreGemBlockedWorlds = new HashSet<>();
    /** Chance per mined block; only listed blocks roll. */
    public final Map<org.bukkit.Material, Double> oreGemChances = new EnumMap<>(org.bukkit.Material.class);
    /** Purity I, II... weights for ore gems. */
    public final int[] oreGemTierWeights;

    public final boolean farmGuardEnabled;
    public final int farmAreaRadius;
    public final double farmFreeKills, farmFalloff;
    public final double farmHalfLifeMillis;
    public final Set<org.bukkit.event.entity.EntityDamageEvent.DamageCause> farmTaintCauses =
        EnumSet.noneOf(org.bukkit.event.entity.EntityDamageEvent.DamageCause.class);
    public final int farmOrdinaryGemsPerHour;

    public final boolean lootEnabled;
    public final Set<String> lootBlockedWorlds = new HashSet<>();
    public final double convertChance, bonusGearChance, materialChance, lootGemChance;
    public final int materialMin, materialMax;
    public final List<String> richTables = new ArrayList<>();
    public final Map<Rarity, Integer> richWeights = new EnumMap<>(Rarity.class);

    public ForgeSettings(FileConfiguration config, Logger logger) {
        commandAnywhere = config.getBoolean("command-opens-anywhere", true);
        org.bukkit.Material station = org.bukkit.Material.matchMaterial(config.getString("station-block", "SMITHING_TABLE"));
        stationBlock = station == null ? org.bukkit.Material.SMITHING_TABLE : station;
        iconNamespace = config.getString("gui.icon-namespace", "advance_magic");

        for (Rarity rarity : Rarity.values()) {
            String path = "rarities." + rarity.name() + ".";
            List<Double> level = config.getDoubleList(path + "level");
            int[] salvage = pair(config.getIntegerList(path + "salvage"), 1, 1, 0, 64);
            int[] sockets = pair(config.getIntegerList(path + "sockets"), 0, 0, 0, GemService.MAX_SOCKETS);
            double low = level.size() == 2 ? Math.clamp(level.get(0), 0, 1) : 0;
            double high = level.size() == 2 ? Math.clamp(level.get(1), low, 1) : 1;
            grades.put(rarity, new Grade(
                Math.max(0, config.getInt(path + "weight", 1)),
                Math.clamp(config.getInt(path + "affixes", 1), 1, 6),
                low, high, sockets[0], sockets[1],
                Math.max(1, config.getInt(path + "reforge.materials", 2)),
                Math.max(0, config.getInt(path + "reforge.levels", 5)),
                salvage[0], salvage[1]));
        }
        List<Integer> multipliers = config.getIntegerList("reforge.choice-multipliers");
        for (int i = 0; i < 3 && i < multipliers.size(); i++) choiceMultipliers[i] = Math.max(1, multipliers.get(i));
        Rarity max = Rarity.parse(config.getString("reforge.max-rarity", "MYTHIC"));
        maxReforgeRarity = max == null ? Rarity.MYTHIC : max;
        salvagePlainChance = Math.clamp(config.getDouble("salvage-plain-chance", 0.5), 0, 1);

        for (AffixType type : AffixType.values()) {
            String path = "affixes." + type.id() + ".";
            if (!config.isConfigurationSection("affixes." + type.id())) logger.warning("No config for affix " + type.id() + "; it stays disabled");
            affixes.put(type, new Range(config.getBoolean(path + "enabled", false),
                config.getDouble(path + "min", 0), config.getDouble(path + "max", 0)));
        }
        for (String key : config.getStringList("protected-keys")) {
            String value = key.trim().toLowerCase(Locale.ROOT);
            // Eternity marks a player-applied upgrade, not the identity of a special relic.
            if (value.equals("evergarden:relic_eternity") || value.equals("voidscape:relic_eternity")) continue;
            if (value.endsWith("*")) protectedPrefixes.add(value.substring(0, value.length() - 1));
            else protectedKeys.add(value);
            if(value.startsWith("evergarden:") || value.startsWith("voidscape:")) {
                String alias=(value.startsWith("evergarden:")?"voidscape:":"evergarden:")+value.substring(value.indexOf(':')+1);
                if(alias.endsWith("*"))protectedPrefixes.add(alias.substring(0,alias.length()-1));else protectedKeys.add(alias);
            }
        }

        fill(gemTierMultipliers, config.getDoubleList("gems.tier-multipliers"));
        fill(cutDust, config.getIntegerList("gems.cut-dust"));
        fill(cutMaterials, config.getIntegerList("gems.cut-materials"));
        fill(gemSalvageDust, config.getIntegerList("gems.salvage-dust"));
        for (Rarity rarity : Rarity.values()) {
            gemTierRanges.put(rarity, pair(config.getIntegerList("gems.tier-by-rarity." + rarity.name()), 1, 1, 1, GemService.MAX_TIER));
        }

        ConfigurationSection kinds = config.getConfigurationSection("gear.kinds");
        if (kinds != null) {
            for (String kind : kinds.getKeys(false)) {
                if (GearFactory.knows(kind)) gearKinds.put(kind, Math.max(0, kinds.getInt(kind)));
                else logger.warning("gear.kinds: unknown kind " + kind);
            }
        }
        if (gearKinds.isEmpty()) gearKinds.put("sword", 1);
        allowNetherite = config.getBoolean("gear.allow-netherite", false);
        for (World.Environment environment : World.Environment.values()) {
            Map<Rarity, List<String>> byRarity = new EnumMap<>(Rarity.class);
            for (Rarity rarity : Rarity.values()) {
                List<String> materials = families(config.getStringList("gear.materials." + environment.name() + "." + rarity.name()));
                if (!materials.isEmpty()) byRarity.put(rarity, materials);
            }
            if (!byRarity.isEmpty()) gearMaterials.put(environment, byRarity);
        }
        gemMobDropChance = Math.clamp(config.getDouble("gems.mob-drop-chance", 0.01), 0, 1);

        elitesEnabled = config.getBoolean("elites.enabled", true);
        eliteChance = Math.clamp(config.getDouble("elites.chance", 0.025), 0, 1);
        elitesPerWorld = Math.max(0, config.getInt("elites.max-per-world", 12));
        eliteBlockedWorlds.addAll(config.getStringList("elites.blocked-worlds"));
        ConfigurationSection reasons = config.getConfigurationSection("elites.spawn-reasons");
        if (reasons == null) {
            eliteReasonChance.put(org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.NATURAL, 1.0);
        } else {
            for (String name : reasons.getKeys(false)) {
                try {
                    eliteReasonChance.put(org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.valueOf(name.toUpperCase(Locale.ROOT)),
                        Math.clamp(reasons.getDouble(name), 0, 10));
                } catch (IllegalArgumentException e) {
                    logger.warning("elites.spawn-reasons: unknown reason " + name);
                }
            }
        }
        for (String name : config.getStringList("elites.damage-exempt")) {
            try {
                eliteDamageExempt.add(EntityType.valueOf(name.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                logger.warning("elites.damage-exempt: unknown entity " + name);
            }
        }
        for (String name : config.getStringList("elites.types")) {
            try {
                eliteTypes.add(EntityType.valueOf(name.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                logger.warning("elites.types: unknown entity " + name);
            }
        }
        for (String namespace : config.getStringList("elites.skip-namespaces")) eliteSkipNamespaces.add(namespace.toLowerCase(Locale.ROOT));
        for (Rarity rarity : Rarity.values()) {
            String path = "elites.rarities." + rarity.name() + ".";
            if (!config.isConfigurationSection("elites.rarities." + rarity.name())) continue;
            int[] materials = pair(config.getIntegerList(path + "materials"), 0, 1, 0, 64);
            eliteGrades.put(rarity, new EliteGrade(
                Math.max(1, config.getDouble(path + "health", 1.5)),
                Math.max(1, config.getDouble(path + "damage", 1.2)),
                Math.clamp(config.getDouble(path + "speed", 0.05), 0, 1),
                Math.clamp(config.getDouble(path + "knockback-resist", 0.2), 0, 1),
                families(config.getStringList(path + "armor")),
                Math.clamp(config.getDouble(path + "armor-chance", 0.5), 0, 1),
                Math.clamp(config.getInt(path + "gear", 1), 0, 5),
                Math.clamp(config.getDouble(path + "gem-chance", 0.2), 0, 1),
                materials[0], materials[1],
                Math.max(1, config.getInt(path + "xp", 3))));
        }

        for (World.Environment environment : World.Environment.values()) {
            ConfigurationSection weights = config.getConfigurationSection("elites.weights." + environment.name());
            if (weights == null) continue;
            Map<Rarity, Integer> byRarity = new EnumMap<>(Rarity.class);
            for (Rarity rarity : Rarity.values()) byRarity.put(rarity, Math.max(0, weights.getInt(rarity.name(), 0)));
            eliteWeights.put(environment, byRarity);
        }

        evergardenChestsEnabled = config.getBoolean("evergarden-chests.enabled", true);
        evergardenEndCityOutsideEnd = config.getBoolean("evergarden-chests.end-city-outside-end", true);
        for (String marker : config.getStringList("evergarden-chests.chunk-markers")) {
            // Glob: * matches anything; everything else is literal.
            StringBuilder regex = new StringBuilder();
            for (String part : marker.trim().toLowerCase(Locale.ROOT).split("\\*", -1)) {
                if (!regex.isEmpty()) regex.append(".*");
                regex.append(Pattern.quote(part));
            }
            evergardenChunkMarkers.add(Pattern.compile(regex.toString()));
        }
        evergardenGemChance = Math.clamp(config.getDouble("evergarden-chests.gem.chance", 0.6), 0, 1);
        evergardenGemExtra = Math.clamp(config.getDouble("evergarden-chests.gem.extra-chance", 0.25), 0, 1);
        evergardenGemMax = Math.clamp(config.getInt("evergarden-chests.gem.max", 3), 0, 9);
        evergardenGearChance = Math.clamp(config.getDouble("evergarden-chests.gear.chance", 0.4), 0, 1);
        evergardenGearExtra = Math.clamp(config.getDouble("evergarden-chests.gear.extra-chance", 0.2), 0, 1);
        evergardenGearMax = Math.clamp(config.getInt("evergarden-chests.gear.max", 2), 0, 9);
        for (Rarity rarity : Rarity.values()) {
            evergardenWeights.put(rarity, Math.max(0, config.getInt("evergarden-chests.weights." + rarity.name(), 0)));
        }

        oreGemsEnabled = config.getBoolean("ore-gems.enabled", true);
        oreGemBlockedWorlds.addAll(config.getStringList("ore-gems.blocked-worlds"));
        ConfigurationSection ores = config.getConfigurationSection("ore-gems.chances");
        if (ores != null) {
            for (String name : ores.getKeys(false)) {
                org.bukkit.Material material = org.bukkit.Material.matchMaterial(name);
                if (material == null || !material.isBlock()) logger.warning("ore-gems.chances: unknown block " + name);
                else oreGemChances.put(material, Math.clamp(ores.getDouble(name), 0, 1));
            }
        }
        List<Integer> tierWeights = config.getIntegerList("ore-gems.tier-weights");
        oreGemTierWeights = new int[Math.clamp(tierWeights.size(), 1, GemService.MAX_TIER)];
        for (int i = 0; i < oreGemTierWeights.length; i++) oreGemTierWeights[i] = tierWeights.isEmpty() ? 1 : Math.max(0, tierWeights.get(i));

        farmGuardEnabled = config.getBoolean("farm-guard.enabled", true);
        farmAreaRadius = Math.clamp(config.getInt("farm-guard.area-radius-chunks", 1), 0, 4);
        farmFreeKills = Math.max(0, config.getDouble("farm-guard.free-kills", 5));
        farmFalloff = Math.max(0.5, config.getDouble("farm-guard.falloff", 10));
        farmHalfLifeMillis = Math.max(0.1, config.getDouble("farm-guard.half-life-minutes", 5)) * 60_000;
        for (String name : config.getStringList("farm-guard.environmental-damage")) {
            try {
                farmTaintCauses.add(org.bukkit.event.entity.EntityDamageEvent.DamageCause.valueOf(name.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                logger.warning("farm-guard.environmental-damage: unknown cause " + name);
            }
        }
        farmOrdinaryGemsPerHour = config.getInt("farm-guard.ordinary-gems-per-hour", -1);

        lootEnabled = config.getBoolean("loot.enabled", true);
        lootBlockedWorlds.addAll(config.getStringList("loot.blocked-worlds"));
        convertChance = Math.clamp(config.getDouble("loot.convert-chance", 0.6), 0, 1);
        bonusGearChance = Math.clamp(config.getDouble("loot.bonus-gear-chance", 0.15), 0, 1);
        materialChance = Math.clamp(config.getDouble("loot.material-chance", 0.2), 0, 1);
        lootGemChance = Math.clamp(config.getDouble("loot.gem-chance", 0.1), 0, 1);
        int[] amount = pair(config.getIntegerList("loot.material-amount"), 1, 2, 1, 64);
        materialMin = amount[0];
        materialMax = amount[1];
        richTables.addAll(config.getStringList("loot.rich-tables"));
        ConfigurationSection rich = config.getConfigurationSection("loot.rich-weights");
        for (Rarity rarity : Rarity.values()) {
            richWeights.put(rarity, rich == null ? grades.get(rarity).weight() : Math.max(0, rich.getInt(rarity.name(), 0)));
        }
    }

    /** [min, max] from a two-element list, clamped, with max ≥ min. */
    /** Gear and armor stop at diamond unless gear.allow-netherite is set. */
    private List<String> families(List<String> families) {
        if (allowNetherite) return families;
        return families.stream().map(family -> family.trim().equalsIgnoreCase("NETHERITE") ? "DIAMOND" : family).toList();
    }

    private static int[] pair(List<Integer> values, int defaultMin, int defaultMax, int floor, int ceiling) {
        int min = values.size() == 2 ? values.get(0) : defaultMin;
        int max = values.size() == 2 ? values.get(1) : defaultMax;
        min = Math.clamp(min, floor, ceiling);
        return new int[]{min, Math.clamp(max, min, ceiling)};
    }

    private static void fill(double[] target, List<Double> values) {
        for (int i = 0; i < target.length && i < values.size(); i++) target[i] = Math.max(0, values.get(i));
    }

    private static void fill(int[] target, List<Integer> values) {
        for (int i = 0; i < target.length && i < values.size(); i++) target[i] = Math.max(0, values.get(i));
    }

    public Grade grade(Rarity rarity) {
        return grades.get(rarity);
    }

    public Map<Rarity, Integer> weights() {
        Map<Rarity, Integer> weights = new EnumMap<>(Rarity.class);
        grades.forEach((rarity, grade) -> weights.put(rarity, grade.weight()));
        return weights;
    }

    public double gemTierMultiplier(int tier) {
        return gemTierMultipliers[Math.clamp(tier, 1, GemService.MAX_TIER) - 1];
    }

    /** Material families for gear of a rarity in a world; worlds without their own table use the Overworld's. */
    public List<String> gearMaterials(World.Environment environment, Rarity rarity) {
        Map<Rarity, List<String>> byRarity = gearMaterials.getOrDefault(environment, gearMaterials.get(World.Environment.NORMAL));
        List<String> materials = byRarity == null ? null : byRarity.get(rarity);
        return materials == null || materials.isEmpty() ? List.of("IRON") : materials;
    }

    /** Elite rarity weights of a world; null when elites never appear there. */
    public Map<Rarity, Integer> eliteWeights(World.Environment environment) {
        return eliteWeights.get(environment);
    }

    public int[] gemTierRange(Rarity rarity) {
        return gemTierRanges.get(rarity);
    }

    public boolean isProtectedKey(String key) {
        if (protectedKeys.contains(key)) return true;
        for (String prefix : protectedPrefixes) if (key.startsWith(prefix)) return true;
        return false;
    }
}
