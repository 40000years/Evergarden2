package com.example.advancemagic.spell;

import org.bukkit.Material;
import java.util.Locale;

/** Spell costs are shared by casting, recipes, help and the pack generator. */
public enum Spell {
    LIGHTNING_STRIKE("Lightning Strike", Material.LIGHTNING_ROD, 60, 8, 0xFFE578),
    FROST_NOVA("Frost Nova", Material.BLUE_ICE, 50, 12, 0x8DEAFF),
    SHADOW_STEP("Shadow Step", Material.ENDER_PEARL, 45, 6, 0x8E69D4),
    NATURES_BLOOM("Nature's Bloom", Material.ENCHANTED_GOLDEN_APPLE, 70, 25, 0x8DEF81),
    EARTH_WALL("Earth Wall", Material.REINFORCED_DEEPSLATE, 40, 10, 0xAD9877),
    DRAGONS_BREATH("Dragon's Breath", Material.DRAGON_BREATH, 75, 18, 0xE07FFF),
    VOID_PULL("Void Pull", Material.LODESTONE, 65, 14, 0x6658C9),
    SONIC_BOOM("Sonic Boom", Material.ECHO_SHARD, 65, 12, 0x0BE8DA),
    BLAZE_BARRAGE("Blaze Barrage", Material.BLAZE_ROD, 55, 8, 0xFFA500),
    WITHER_RAY("Wither Ray", Material.NETHER_STAR, 85, 12, 0x827A91),
    SHULKER_LEVITATION("Shulker Levitation", Material.SHULKER_SHELL, 95, 35, 0x110822),
    METEOR_STRIKE("Meteor Strike", Material.MAGMA_BLOCK, 90, 20, 0xFF8546),
    IRON_ARMOR("Iron Armor", Material.IRON_BLOCK, 60, 35, 0xCCD8E0),
    VEX_LEGION("Vex Legion", Material.TOTEM_OF_UNDYING, 80, 25, 0xB4D8E7),
    GUARDIAN_BEAM("Guardian Beam", Material.PRISMARINE_SHARD, 60, 10, 0x56E0B5);

    public final String title;
    public final Material core;
    public final int mana, cooldown, color;
    Spell(String title, Material core, int mana, int cooldown, int color) {
        this.title=title; this.core=core; this.mana=mana; this.cooldown=cooldown; this.color=color;
    }
    public String id() { return name().toLowerCase(Locale.ROOT); }
    public static Spell parse(String text) {
        if(text==null) return null;
        String upper=text.toUpperCase(Locale.ROOT).trim();
        if(upper.contains("INVISIBILITY")||upper.equals("SHROUD")) return SONIC_BOOM;
        if(upper.contains("POISON")||upper.contains("SPORES")) return BLAZE_BARRAGE;
        if(upper.contains("SOUL")||upper.equals("SOULS")||upper.contains("DRAIN")) return GUARDIAN_BEAM;
        if(upper.contains("TIME")||upper.contains("DILATION")) return VEX_LEGION;
        try { return valueOf(upper); }
        catch (IllegalArgumentException ex) { return null; }
    }
}
