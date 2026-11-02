package com.example.forge;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

import static com.example.forge.LootCategory.*;

/** Stat affixes; value ranges come from config.yml (affixes.<id>.min/max). */
public enum AffixType {
    SHARP(Stat.ATTACK_DAMAGE, "คมกริบ", "ความคม", MELEE),
    SWIFT(Stat.ATTACK_SPEED, "ว่องไว", "ความว่องไว", MELEE),
    HEAVY(Stat.KNOCKBACK, "หนักหน่วง", "แรงทลาย", MELEE),
    SWEEP(Stat.SWEEP, "กวาดล้าง", "พายุคมดาบ", MELEE),
    VIGOR(Stat.MAX_HEALTH, "ทรหด", "ชีวิต", MELEE, RANGED, SHIELD, TOOL, HELMET, CHESTPLATE, LEGGINGS, BOOTS, STAFF),
    FLEET(Stat.MOVE_SPEED, "ปราดเปรียว", "สายลม", MELEE, RANGED, SHIELD, TOOL, LEGGINGS, BOOTS, STAFF),
    LUCKY(Stat.LUCK, "นำโชค", "โชคลาภ", MELEE, RANGED, TOOL, HELMET),
    ARROW_POWER(Stat.ARROW_DAMAGE, "ทะลวง", "การทะลวง", RANGED),
    ARROW_SPEED(Stat.ARROW_SPEED, "พุ่งทะยาน", "ดาวตก", RANGED),
    ARMORED(Stat.ARMOR, "แข็งแกร่ง", "ปราการ", SHIELD, HELMET, CHESTPLATE, LEGGINGS, BOOTS),
    TOUGH(Stat.TOUGHNESS, "ทนทาน", "หินผา", SHIELD, HELMET, CHESTPLATE, LEGGINGS, BOOTS),
    STEADFAST(Stat.KNOCKBACK_RESIST, "มั่นคง", "ขุนเขา", SHIELD, CHESTPLATE, LEGGINGS),
    FIREWARD(Stat.BURN_TIME, "ทนไฟ", "เปลวเพลิง", HELMET, CHESTPLATE),
    FEATHER(Stat.SAFE_FALL, "เบาหวิว", "ขนนก", BOOTS),
    BLASTGUARD(Stat.EXPLOSION_RESIST, "ต้านระเบิด", "ป้อมปราการ", SHIELD, CHESTPLATE, LEGGINGS),
    BREATH(Stat.OXYGEN, "ใต้สมุทร", "ห้วงสมุทร", HELMET),
    SWIM(Stat.WATER_MOVEMENT, "ว่ายคล่อง", "กระแสธาร", LEGGINGS, BOOTS),
    HASTE(Stat.BREAK_SPEED, "เร่งขุด", "ความเร่งรีบ", TOOL),
    DELVE(Stat.MINING_EFFICIENCY, "เจาะลึก", "ใต้พิภพ", TOOL),
    TIDE(Stat.SUBMERGED_MINING, "ดำดิ่ง", "ท้องทะเล", TOOL),
    ARCANE(Stat.SPELL_POWER, "อาคม", "เวทมนตร์", STAFF),
    MENDING(Stat.HEALING_POWER, "เยียวยา", "การเยียวยา", STAFF),
    WELLSPRING(Stat.MAX_MANA, "ล้นมานา", "ตาน้ำมานา", STAFF),
    FRUGAL(Stat.MANA_COST, "ประหยัด", "ความสมถะ", STAFF),
    FLOW(Stat.MANA_REGEN, "ไหลเวียน", "สายธารมานา", STAFF);

    public final Stat stat;
    /** Adjective after the base name ("ดาบเพชร คมกริบ") and noun for the second title ("แห่งสายลม"). */
    public final String adjective;
    public final String noun;
    public final Set<LootCategory> categories;

    AffixType(Stat stat, String adjective, String noun, LootCategory first, LootCategory... rest) {
        this.stat = stat;
        this.adjective = adjective;
        this.noun = noun;
        this.categories = EnumSet.of(first, rest);
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static AffixType parse(String id) {
        try {
            return valueOf(id.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
