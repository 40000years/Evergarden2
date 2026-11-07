package com.example.forge;

import net.kyori.adventure.text.format.TextColor;

import java.util.List;
import java.util.Locale;

/**
 * The 27 sanctum charms (0.7.1 set: every charm adds something the player could not do before). Each costs
 * 1-3 of the player's charm slots; the grade (Worn..Divine) moves the primary value from {@link #min} to
 * {@link #max}. Strong ones carry cooldowns; the fixed numbers live in {@link CharmEffects}.
 */
public enum Charm {
    // ---- Sanctum of Darkness: the shadow hunter -------------------------------------------------
    SHADOW_STEP(Temple.DARK, 2, "Shadow Step", 8, 4, "s", "ย่อ 2 ครั้งเร็วๆ → พุ่งไปข้างหน้า 8 บล็อก", "ทะลุมอนและฟันทุกตัวที่ผ่าน", "คูลดาวน์ {v}"),
    CRIMSON_WAVE(Temple.DARK, 2, "Crimson Wave", 4, 9, "", "ตอนเลือดเต็ม ทุกการฟันปล่อยคลื่นเลือด", "พุ่งไกล 7 บล็อก · ดาเมจ {v}"),
    SOUL_LEGION(Temple.DARK, 3, "Soul Legion", 2, 4, "i", "ฆ่ามอนครบ 6 ตัว → เรียกวิญญาณ {v} ดวง", "ไล่ฟาดมอนรอบตัว 15 วินาที", "คูลดาวน์ 40 วินาที"),
    HEX_BRAND(Temple.DARK, 2, "Hex Brand", 4, 9, "", "ตีมอนแล้วติดตราสาป 8 วินาที", "มอนที่ติดตราตาย → ระเบิด ดาเมจ {v}", "แล้วตราลามไปตัวข้างๆ"),
    REAPER_MARK(Temple.DARK, 1, "Reaper's Mark", 15, 25, "%", "มอนที่เลือดต่ำกว่า {v} มีหัวกะโหลกลอย", "ตีทีเดียวตาย แล้ววิญญาณพุ่งชนตัวถัดไป", "ไม่มีผลกับบอส"),
    BAT_FORM(Temple.DARK, 2, "Bat Form", 90, 50, "s", "เลือดต่ำกว่า 30% → แตกร่างเป็นฝูงค้างคาว", "บินหนีได้ 3 วินาที ไม่มีอะไรทำร้ายได้", "คูลดาวน์ {v}"),
    NIGHTFALL_VEIL(Temple.DARK, 1, "Nightfall Veil", 60, 150, "%", "ไม่ได้ต่อสู้ 5 วินาที → ล่องหน", "การโจมตีแรกจากความมืด ดาเมจ +{v}"),
    EYE_OF_ABYSS(Temple.DARK, 1, "Eye of the Abyss", 20, 36, "b", "มองเห็นหีบสมบัติที่ยังไม่มีใครเปิด", "ลำแสงเหนือหีบ + บอกทางหีบใกล้สุด · ระยะ {v}"),
    CURSED_COIN(Temple.DARK, 1, "Cursed Coin", 40, 80, "%", "Elite เกิดรอบตัวคุณบ่อยขึ้น ×2", "ฆ่า Elite มีโอกาส {v} ได้อุปกรณ์เพิ่ม 1 ชิ้น"),

    // ---- Sanctum of Stars: the sky walker --------------------------------------------------------
    STARWALKER(Temple.ASTRAL, 2, "Starwalker", 1, 2, "i", "กระโดดกลางอากาศได้อีก {v} ครั้ง", "(กดกระโดดซ้ำกลางอากาศ) · รีเซ็ตเมื่อแตะพื้น"),
    SKY_GLIDER(Temple.ASTRAL, 1, "Sky Glider", 7, 12, "", "ย่อค้างกลางอากาศ → ร่อนโดยไม่ต้องใช้ Elytra", "ความเร็ว {v} บล็อก/วินาที"),
    METEOR_FALL(Temple.ASTRAL, 2, "Meteor Fall", 0.6, 1.2, "", "ตกจากที่สูงไม่เสียเลือด", "กระแทกพื้นระเบิด ดาเมจ {v} ต่อบล็อกที่ตก", "คูลดาวน์ระเบิด 4 วินาที"),
    STAR_FAMILIAR(Temple.ASTRAL, 2, "Star Familiar", 2, 5, "", "ภูตดาวลอยข้างตัว", "ยิงลำแสงใส่มอนใกล้สุดทุก 1.5 วินาที · ดาเมจ {v}"),
    CELESTIAL_CASCADE(Temple.ASTRAL, 3, "Celestial Cascade", 3, 7, "", "ร่ายเวททุกครั้ง ดาวตก 3 ลูกตามลงใส่ศัตรูรอบตัว", "ดาเมจลูกละ {v}"),
    ORBIT(Temple.ASTRAL, 2, "Orbit", 1.5, 4, "", "ดาว 3 ดวงโคจรรอบตัว", "กันลูกธนูดวงละ 1 ครั้ง (ชาร์จ 10 วินาที)", "ชนมอนที่เข้าใกล้ ดาเมจ {v}"),
    BLACK_HOLE(Temple.ASTRAL, 3, "Black Hole", 20, 12, "s", "ทุก {v} การโจมตีครั้งถัดไปเปิดหลุมดำ", "ดูดมอนในระยะ 6 บล็อกมาบดรวมกัน 3 วินาที"),
    LODESTAR(Temple.ASTRAL, 1, "Lodestar", 6, 12, "b", "ดูดไอเท็มและ XP ในระยะ {v} เข้าหาตัว"),
    LUCKY_STAR(Temple.ASTRAL, 1, "Lucky Star", 10, 30, "%", "เปิดหีบสมบัติหรือฆ่า Elite", "มีโอกาส {v} ดาวนำโชคตกลงมา (Gem หรือวัสดุ)"),

    // ---- Sanctum of Time: the time keeper --------------------------------------------------------
    TIME_STOP(Temple.TIME, 3, "Time Stop", 120, 60, "s", "ย่อค้างบนพื้น 1.5 วินาที ตอนมีมอนใกล้ตัว", "→ หยุดเวลามอนในระยะ 12 บล็อก 4 วินาที", "บอสแค่ช้าลง · คูลดาวน์ {v}"),
    HOURGLASS(Temple.TIME, 3, "Hourglass", 420, 240, "s", "โดนโจมตีถึงตาย → ย้อนเวลา 5 วินาที", "กลับไปตำแหน่งและเลือดเมื่อ 5 วินาทีก่อน", "คูลดาวน์ {v}"),
    CHRONO_ANCHOR(Temple.TIME, 1, "Chrono Anchor", 60, 30, "s", "ตกเหว ตกลาวา หรือตกสูงเกิน 10 บล็อก", "→ ย้อนกลับไปยืนจุดปลอดภัยเมื่อ 3 วินาทีก่อน", "คูลดาวน์ {v}"),
    ECHO_STRIKE(Temple.TIME, 2, "Echo Strike", 40, 70, "%", "ทุกการฟัน เงาฟันซ้ำตามมา", "ดาเมจ {v} ของครั้งแรก"),
    STASIS_TRAP(Temple.TIME, 2, "Stasis Trap", 14, 8, "s", "ทุก {v} การฟันครั้งถัดไปขังมอนในฟองเวลา 3 วินาที", "ดาเมจที่ทำระหว่างนั้นระเบิดซ้ำ 50% ตอนฟองแตก", "บอสแค่ช้าลง"),
    OVERCLOCK(Temple.TIME, 1, "Overclock", 5, 9, "s", "ฆ่าต่อเนื่องครบ 5 ตัว (ห่างกันไม่เกิน 4 วินาที)", "→ เร่งเวลา {v}: ตีเร็ว วิ่งเร็ว ขุดเร็ว", "คูลดาวน์ 20 วินาทีหลังหมดเวลา"),
    TIMELESS(Temple.TIME, 2, "Timeless", 30, 15, "m", "ตายแล้วของในฮอตบาร์ มือรอง และ XP ไม่หาย", "ได้คืนเมื่อเกิดใหม่ในโลกเดิม", "คูลดาวน์ {v}"),
    VEIN_OF_AGES(Temple.TIME, 1, "Vein of Ages", 8, 24, "", "ขุดแร่ 1 ก้อน แร่ชนิดเดียวกันที่ติดกันแตกตาม", "สูงสุด {v} ก้อน · ย่อตัวขุดเพื่อขุดก้อนเดียว"),
    LOST_HOURS(Temple.TIME, 1, "Lost Hours", 25, 60, "%", "เปิดหีบสมบัติใน Evergarden", "มีโอกาส {v} ได้ของ Forge เพิ่มอีกรอบ");

    public enum Temple {
        DARK("วิหารความมืด", "Shadow", 0xB566FF, 0x7A2BD1),
        ASTRAL("วิหารดวงดาว", "Astral", 0x7FD3FF, 0x2F7BE0),
        TIME("วิหารกาลเวลา", "Chrono", 0xF2C35B, 0xC9852A);

        public final String label, tag;
        public final TextColor color, deep;

        Temple(String label, String tag, int color, int deep) {
            this.label = label;
            this.tag = tag;
            this.color = TextColor.color(color);
            this.deep = TextColor.color(deep);
        }

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public List<Charm> charms() {
            return java.util.Arrays.stream(Charm.values()).filter(c -> c.temple == this).toList();
        }
    }

    public final Temple temple;
    public final int cost;
    public final String title;
    /** Primary value at Worn and at Divine (a cooldown goes down). */
    public final double min, max;
    public final String unit;
    private final String[] lines;

    Charm(Temple temple, int cost, String title, double min, double max, String unit, String... lines) {
        this.temple = temple;
        this.cost = cost;
        this.title = title;
        this.min = min;
        this.max = max;
        this.unit = unit;
        this.lines = lines;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Primary value of this charm at a grade. */
    public double value(Rarity grade) {
        return min + (max - min) * grade.ordinal() / (Rarity.values().length - 1);
    }

    public int intValue(Rarity grade) {
        return (int) Math.round(value(grade));
    }

    public String format(Rarity grade) {
        double value = unit.equals("i") ? intValue(grade) : value(grade);
        String number = Math.abs(value - Math.rint(value)) < 0.05 ? Long.toString(Math.round(value)) : String.format(Locale.ROOT, "%.1f", value);
        return switch (unit) {
            case "s" -> number + " วินาที";
            case "m" -> number + " นาที";
            case "b" -> number + " บล็อก";
            case "i" -> number;
            default -> number + unit;
        };
    }

    /** Effect lines with this grade's value filled in. */
    public List<String> describe(Rarity grade) {
        String value = format(grade);
        return java.util.Arrays.stream(lines).map(line -> line.replace("{v}", value)).toList();
    }

    /** Effect lines with the Worn → Divine range, for the codex. */
    public List<String> describeRange() {
        String range = format(Rarity.COMMON) + "→" + format(Rarity.MYTHIC);
        return java.util.Arrays.stream(lines).map(line -> line.replace("{v}", range)).toList();
    }

    public static Charm parse(String value) {
        if (value == null) return null;
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
