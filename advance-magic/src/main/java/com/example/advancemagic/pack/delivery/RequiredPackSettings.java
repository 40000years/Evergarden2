package com.example.advancemagic.pack.delivery;

import org.bukkit.configuration.file.FileConfiguration;

/** Upgrade existing optional installs once, without changing their download hosts. */
public final class RequiredPackSettings {
    private RequiredPackSettings() {}

    public static boolean upgrade(FileConfiguration config, boolean includeJudge) {
        if (config.getInt("internal.required-java-packs-version", 0) >= 1) return false;
        config.set("resource-pack.enabled", true);
        config.set("resource-pack.required", true);
        if (includeJudge) {
            config.set("world-boss.visuals.pack.enabled", true);
            config.set("world-boss.visuals.pack.required", true);
        }
        config.set("internal.required-java-packs-version", 1);
        return true;
    }
}
