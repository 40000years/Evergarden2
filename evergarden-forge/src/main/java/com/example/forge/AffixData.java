package com.example.forge;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;

/**
 * Rarity, affix roll levels (0..1) and socket count. Values come from the current config, so
 * retuning an affix range changes existing items without rewriting them. The first affix names the item.
 */
public record AffixData(Rarity rarity, Map<AffixType, Double> levels, int sockets) {
    public AffixData {
        // Rounded to what serialize() stores, so values computed now match values read back later.
        Map<AffixType, Double> rounded = new LinkedHashMap<>();
        levels.forEach((type, level) -> rounded.put(type, Math.round(Math.clamp(level, 0, 1) * 10000) / 10000.0));
        levels = Collections.unmodifiableMap(rounded);
        sockets = Math.clamp(sockets, 0, GemService.MAX_SOCKETS);
    }

    public AffixData(Rarity rarity, Map<AffixType, Double> levels) {
        this(rarity, levels, 0);
    }

    public String serialize() {
        StringJoiner joiner = new StringJoiner(";");
        levels.forEach((type, level) -> joiner.add(type.id() + "=" + String.format(Locale.ROOT, "%.4f", level)));
        return joiner.toString();
    }

    /** Unknown affix ids (removed in a later version) are skipped, never fatal. */
    public static AffixData parse(Rarity rarity, String text, int sockets) {
        Map<AffixType, Double> levels = new LinkedHashMap<>();
        if (text != null && !text.isBlank()) {
            for (String part : text.split(";")) {
                String[] pair = part.split("=", 2);
                if (pair.length != 2) continue;
                AffixType type = AffixType.parse(pair[0]);
                if (type == null) continue;
                try {
                    levels.put(type, Math.clamp(Double.parseDouble(pair[1]), 0, 1));
                } catch (NumberFormatException ignored) {
                    // a hand-edited value; drop the line
                }
            }
        }
        return new AffixData(rarity, levels, sockets);
    }
}
