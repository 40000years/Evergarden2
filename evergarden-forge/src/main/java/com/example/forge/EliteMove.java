package com.example.forge;

import org.bukkit.entity.EntityType;

import java.util.List;

/** Special moves an elite uses while it is hostile to a player (config: elites.abilities.<key>). */
public enum EliteMove {
    /** Yanks the target to the elite, off a boat, pet or staff too. */
    HOOK("hook", "กระชาก"),
    /** Throws nearby players back, never toward a spot without ground. */
    SHOCKWAVE("shockwave", "แรงผลัก"),
    /** Drags every player in the radius toward the elite for a moment. */
    VORTEX("vortex", "หลุมดูด");

    public final String key;
    public final String label;

    EliteMove(String key, String label) {
        this.key = key;
        this.label = label;
    }

    /**
     * Moves a mob type can use. Flyers and shulkers only push: pulling a player toward a ghast over a lava
     * sea, or toward a shulker on an End City wall, would drop them into lava or the void.
     */
    public static List<EliteMove> pool(EntityType type) {
        return switch (type) {
            case GHAST, PHANTOM, BLAZE, BREEZE, SHULKER -> List.of(SHOCKWAVE);
            default -> List.of(HOOK, SHOCKWAVE, VORTEX);
        };
    }

    public static EliteMove parse(String name) {
        if (name == null) return null;
        for (EliteMove move : values()) if (move.name().equalsIgnoreCase(name.trim()) || move.key.equalsIgnoreCase(name.trim())) return move;
        return null;
    }
}
