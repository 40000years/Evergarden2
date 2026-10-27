package com.example.advancemagic.elemental;

import com.example.advancemagic.elemental.ElementalSpell.Rarity;

/**
 * Spell levels I–VII for every non-Ultimate spell. The published spells are level V; each level down
 * deals ×0.8 and each level up ×1.25, while cooldown runs +0.25 of the level-VII cooldown per level
 * below VII (VII ×0.67 … V ×1 … I ×1.67 of today's). Ultimates have a single level.
 */
public final class SpellLevels {
    public static final int MIN = 1, MAX = 7, BASE = 5;
    private static final String[] ROMAN = {"", "I", "II", "III", "IV", "V", "VI", "VII"};

    private SpellLevels() {}

    public static boolean leveled(ElementalSpell spell) {return !spell.ultimate();}
    public static int clamp(ElementalSpell spell, int level) {return leveled(spell) ? Math.clamp(level, MIN, MAX) : BASE;}
    public static String roman(int level) {return ROMAN[Math.clamp(level, MIN, MAX)];}

    /** Damage and healing relative to level V. */
    public static double damage(int level) {return Math.pow(.8, BASE - Math.clamp(level, MIN, MAX));}
    /** Cooldown relative to level V. */
    public static double cooldown(int level) {return (1 + .25 * (MAX - Math.clamp(level, MIN, MAX))) / (1 + .25 * (MAX - BASE));}
    /** Mana relative to level V: 0.6 at I up to 1.2 at VII. */
    public static double mana(int level) {return 1 + .1 * (Math.clamp(level, MIN, MAX) - BASE);}

    public static int mana(ElementalSpell spell, int level) {return leveled(spell) ? Math.max(1, (int) Math.round(spell.mana() * mana(level))) : spell.mana();}
    public static double cooldown(ElementalSpell spell, int level) {return leveled(spell) ? spell.cooldown() * cooldown(level) : spell.cooldown();}
    public static double multiplier(ElementalSpell spell, int level) {return leveled(spell) ? spell.multiplier() * damage(level) : spell.multiplier();}
    public static double damageFactor(ElementalSpell spell, int level) {return leveled(spell) ? damage(level) : 1;}
    public static double cooldownFactor(ElementalSpell spell, int level) {return leveled(spell) ? cooldown(level) : 1;}

    /**
     * Every spell's level VII is Epic. Level V keeps the spell's published rarity, so existing staffs
     * accept exactly what they accepted before; lower levels drop into the rarities below.
     */
    public static Rarity rarity(ElementalSpell spell, int level) {
        if (!leveled(spell)) return spell.rarity();
        int l = Math.clamp(level, MIN, MAX);
        return switch (spell.rarity()) {
            case BASIC -> l <= 5 ? Rarity.BASIC : l == 6 ? Rarity.RARE : Rarity.EPIC;
            case RARE -> l <= 2 ? Rarity.BASIC : l <= 6 ? Rarity.RARE : Rarity.EPIC;
            default -> l == 1 ? Rarity.BASIC : l <= 4 ? Rarity.RARE : Rarity.EPIC;
        };
    }

    /** "Fireball VII", or the plain title for an Ultimate. */
    public static String label(ElementalSpell spell, int level) {return leveled(spell) ? spell.title() + " " + roman(level) : spell.title();}
}
