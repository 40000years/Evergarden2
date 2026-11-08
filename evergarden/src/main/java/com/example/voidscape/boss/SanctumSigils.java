package com.example.voidscape.boss;

import com.example.voidscape.VoidscapePlugin;
import org.bukkit.Location;

/**
 * The textured spell seals (Malefeasance's azure / violet / crimson seals, the River bosses' circles) for the
 * Tier VI-VIII sanctum bosses: Java players with the Judge pack see the textured plane, Bedrock players get the
 * same plane through Geyser, everyone else coloured dust on the damage boundary.
 */
public final class SanctumSigils implements AutoCloseable {
    public static final String AZURE = "malef_cyan_seal", VIOLET = "malef_violet_seal", CRIMSON = "malef_crimson_seal";
    public static final String ACHERON = "river_acheron", COCYTUS = "river_cocytus", STYX = "river_styx";

    private final JudgeAether aether;
    private final JudgeSigils sigils;

    public SanctumSigils(VoidscapePlugin plugin) {
        aether = new JudgeAether(plugin);
        sigils = new JudgeSigils(plugin, aether);
    }

    /** Who sees the next seals: players within range of the shrine. Call before drawing. */
    public void frame(Location center, double range) {
        aether.frame(center, range);
    }

    /** A flat seal lying on the ground, turned by {@code turn} radians. */
    public void ground(String key, String model, Location at, double radius, double turn) {
        sigils.circle(key, model, at, radius, turn, false);
    }

    public void remove(String key) {
        sigils.remove(key);
    }

    public void removePrefix(String prefix) {
        sigils.removePrefix(prefix);
    }

    @Override
    public void close() {
        sigils.close();
    }
}
