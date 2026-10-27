package com.example.advancemagic.spell;

import org.bukkit.configuration.ConfigurationSection;

/** Stable pricing families, with wand-specific paths and timing. */
public record BasicSpellProfile(Shape shape, int mana, double cooldown, double damage,
                                double range, double radius, double speed) {
    public enum Shape { BOLT, BEAM, PULSE }
    public enum Pattern { BOLT, BURST, FAN, SLASH, CONE, GROUND, NOVA, ORB, LOB, RAY, WAVE, CHANNEL, CHARGE, COLUMN }
    public static final String COOLDOWN_KEY = "basic_attack";

    public static Pattern pattern(Spell spell) {
        return switch(spell) {
            case LIGHTNING_STRIKE -> Pattern.RAY;
            case FROST_NOVA -> Pattern.FAN;
            case SHADOW_STEP -> Pattern.SLASH;
            case NATURES_BLOOM, WITHER_RAY, CRIMSON_OBLIVION -> Pattern.BOLT;
            case EARTH_WALL -> Pattern.GROUND;
            case DRAGONS_BREATH -> Pattern.CONE;
            case VOID_PULL, SHULKER_LEVITATION -> Pattern.ORB;
            case SONIC_BOOM -> Pattern.WAVE;
            case BLAZE_BARRAGE, VEX_LEGION, CHRONOS_FINAL_HOUR, VERDANT_SOVEREIGN, LUNAR_REQUIEM -> Pattern.BURST;
            case METEOR_STRIKE -> Pattern.LOB;
            case IRON_ARMOR -> Pattern.NOVA;
            case GUARDIAN_BEAM -> Pattern.CHANNEL;
            case SOLAR_APOCALYPSE -> Pattern.CHARGE;
            case HEAVENS_JUDGMENT -> Pattern.COLUMN;
        };
    }

    public static String description(Spell spell) {
        return switch(spell) {
            case LIGHTNING_STRIKE -> "สายฟ้าคดเป็นแฉก";
            case FROST_NOVA -> "สะเก็ดน้ำแข็งแผ่ 3 ทาง";
            case SHADOW_STEP -> "คมเงากวาดด้านหน้า";
            case NATURES_BLOOM -> "เมล็ดพฤกษาพุ่งโค้ง";
            case EARTH_WALL -> "แนวหินปะทุไปข้างหน้า";
            case DRAGONS_BREATH -> "ลมหายใจมังกรเป็นกรวย";
            case VOID_PULL -> "ลูกมิติยุบแล้วระเบิด";
            case SONIC_BOOM -> "วงเสียงเคลื่อนทะลวงแนว";
            case BLAZE_BARRAGE -> "ลูกไฟ 3 นัดเป็นชุด";
            case WITHER_RAY -> "กระสุนวิญญาณหมุนเกลียว";
            case SHULKER_LEVITATION -> "ลูกวังวนมิติ";
            case METEOR_STRIKE -> "ดาวไฟวิถีโค้งตกกระแทก";
            case IRON_ARMOR -> "คลื่นเหล็กแผ่รอบตัว";
            case VEX_LEGION -> "วิญญาณคู่โค้งเข้าหาเป้าหมาย";
            case GUARDIAN_BEAM -> "ล็อกลำแสง 3 จังหวะ";
            case SOLAR_APOCALYPSE -> "ชาร์จสุริยันแล้วปล่อยลำแสง";
            case CHRONOS_FINAL_HOUR -> "ประตูนาฬิกาและกระสุนย้อนซ้ำ";
            case HEAVENS_JUDGMENT -> "วงพิพากษาเหนือเป้า ยิงลงพื้น";
            case VERDANT_SOVEREIGN -> "ประตูมังกรพ่นคลื่นมรกต 3 ระลอก";
            case LUNAR_REQUIEM -> "ประตูจันทร์ยิงเข็มเงิน 3 จังหวะ";
            case CRIMSON_OBLIVION -> "ผลึกแดง 3 ชิ้นหมุนรวมกระแทก";
        };
    }

    public static BasicSpellProfile defaults(Spell spell) {
        Shape shape = switch (spell) {
            case LIGHTNING_STRIKE, GUARDIAN_BEAM, SONIC_BOOM, SOLAR_APOCALYPSE,
                    HEAVENS_JUDGMENT, VERDANT_SOVEREIGN, LUNAR_REQUIEM -> Shape.BEAM;
            case EARTH_WALL, IRON_ARMOR -> Shape.PULSE;
            case FROST_NOVA, SHADOW_STEP, NATURES_BLOOM, DRAGONS_BREATH, VOID_PULL,
                    BLAZE_BARRAGE, WITHER_RAY, SHULKER_LEVITATION, METEOR_STRIKE,
                    VEX_LEGION, CHRONOS_FINAL_HOUR, CRIMSON_OBLIVION -> Shape.BOLT;
        };
        if (spell.isLegendary()) return new BasicSpellProfile(shape, 7, 2.25, 24, 24, .35, 1.5);
        if (spell.isMythic()) return new BasicSpellProfile(shape, 5, 1.75, 18, 24, .30, 1.35);
        return switch (shape) {
            case BOLT -> new BasicSpellProfile(shape, 3, 1.4, 14, 22, .25, 1.2);
            case BEAM -> new BasicSpellProfile(shape, 3, 1.25, 12, 20, .20, 1.2);
            case PULSE -> new BasicSpellProfile(shape, 4, 1.75, 10, 3, 3, 1.2);
        };
    }

    public static BasicSpellProfile configured(ConfigurationSection config, Spell spell) {
        BasicSpellProfile base = defaults(spell);
        String family = spell.isLegendary() ? "legendary" : spell.isMythic() ? "mythic"
                : base.shape().name().toLowerCase(java.util.Locale.ROOT);
        String path = "basic-casting.profiles." + family + ".";
        return new BasicSpellProfile(base.shape(),
                (int) Math.round(value(config, path + "mana", base.mana(), 1, 30)),
                value(config, path + "cooldown-seconds", base.cooldown(), .75, 10),
                value(config, path + "damage", base.damage(), 0, 60),
                value(config, path + "range", base.range(), 1, base.shape() == Shape.PULSE ? 5 : 32),
                base.radius(), base.speed());
    }

    private static double value(ConfigurationSection config, String path, double fallback, double min, double max) {
        double value = config.getDouble(path, fallback);
        return Double.isFinite(value) ? Math.clamp(value, min, max) : fallback;
    }
}
