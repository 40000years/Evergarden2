package com.example.forge;

import org.bukkit.configuration.ConfigurationSection;

/** One-time migration of the previous shipped combat defaults; all distinct admin values survive. */
final class CombatBalance {
    private CombatBalance() {}

    private record Change(String key, double oldMin, double oldMax, double newMin, double newMax) {}
    private static final Change[] CHANGES = {
        new Change("affixes.sharp", .5, 3., .75, 5.),
        new Change("affixes.vigor", .5, 2.5, 1., 5.),
        new Change("affixes.arrow_power", .05, .25, .08, .40),
        new Change("affixes.arcane", .03, .15, .05, .25),
        new Change("affixes.mending", .04, .20, .06, .30)
    };

    static boolean upgrade(ConfigurationSection config) {
        if (config.getInt("combat-balance-version", 0) >= 2) return false;
        for (Change change : CHANGES) {
            String min=change.key+".min", max=change.key+".max";
            boolean hasMin=config.contains(min), hasMax=config.contains(max);
            boolean custom=(hasMin && Double.compare(config.getDouble(min),change.oldMin)!=0)
                || (hasMax && Double.compare(config.getDouble(max),change.oldMax)!=0);
            if(custom){
                // Keep the former missing bound too: a changed default could invert a custom range.
                if(!hasMin)config.set(min,change.oldMin);
                if(!hasMax)config.set(max,change.oldMax);
            }else{
                if(hasMin)config.set(min,change.newMin);
                if(hasMax)config.set(max,change.newMax);
            }
        }
        config.set("combat-balance-version", 2);
        return true;
    }

    static void copyChanges(ConfigurationSection from, ConfigurationSection to) {
        for (Change change : CHANGES) for(String suffix : new String[]{".min",".max"}){
            String key=change.key+suffix;
            if(from.contains(key))to.set(key,from.get(key));
        }
        to.set("combat-balance-version", 2);
    }
}
