package com.example.advancemagic.pack.delivery;

import org.bukkit.configuration.file.FileConfiguration;

/** Remove the old mandatory-pack policy while preserving each pack's enabled state and host. */
public final class RequiredPackSettings {
    private RequiredPackSettings() {}

    public static boolean upgrade(FileConfiguration config, boolean includeJudge) {
        boolean changed = config.getBoolean("resource-pack.required", true);
        if (changed) config.set("resource-pack.required", false);
        if (includeJudge && config.getBoolean("world-boss.visuals.pack.required", true)) {
            config.set("world-boss.visuals.pack.required", false);
            changed = true;
        }
        return changed;
    }
}
