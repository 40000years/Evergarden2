package com.example.forge;

import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier.Operation;

/**
 * Native bonuses affect the mechanics governed by each attribute. The two arrow stats are
 * applied when the bow fires; attack damage is not a spell-damage multiplier: spells use the staff stats.
 * Percent stats use MULTIPLY_SCALAR_1 (multiply total) so "+10% attack speed" is 10% of the weapon's speed.
 */
public enum Stat {
    ATTACK_DAMAGE(Attribute.ATTACK_DAMAGE, Operation.ADD_NUMBER, false, "ดาเมจโจมตี"),
    ATTACK_DAMAGE_PERCENT(Attribute.ATTACK_DAMAGE, Operation.MULTIPLY_SCALAR_1, true, "ดาเมจโจมตี"),
    ATTACK_SPEED(Attribute.ATTACK_SPEED, Operation.MULTIPLY_SCALAR_1, true, "ความเร็วโจมตี"),
    KNOCKBACK(Attribute.ATTACK_KNOCKBACK, Operation.ADD_NUMBER, false, "แรงกระแทก"),
    SWEEP(Attribute.SWEEPING_DAMAGE_RATIO, Operation.ADD_NUMBER, true, "ดาเมจกวาด"),
    MAX_HEALTH(Attribute.MAX_HEALTH, Operation.ADD_NUMBER, false, "พลังชีวิตสูงสุด"),
    MOVE_SPEED(Attribute.MOVEMENT_SPEED, Operation.MULTIPLY_SCALAR_1, true, "ความเร็วเคลื่อนที่"),
    LUCK(Attribute.LUCK, Operation.ADD_NUMBER, false, "โชค"),
    ARROW_DAMAGE(null, Operation.MULTIPLY_SCALAR_1, true, "ดาเมจลูกธนู"),
    ARROW_SPEED(null, Operation.MULTIPLY_SCALAR_1, true, "ความเร็วลูกธนู"),
    ARMOR(Attribute.ARMOR, Operation.ADD_NUMBER, false, "เกราะ"),
    TOUGHNESS(Attribute.ARMOR_TOUGHNESS, Operation.ADD_NUMBER, false, "ความแกร่งเกราะ"),
    KNOCKBACK_RESIST(Attribute.KNOCKBACK_RESISTANCE, Operation.ADD_NUMBER, true, "ต้านแรงกระเด็น"),
    BURN_TIME(Attribute.BURNING_TIME, Operation.ADD_SCALAR, true, "ระยะเวลาติดไฟ"),
    SAFE_FALL(Attribute.SAFE_FALL_DISTANCE, Operation.ADD_NUMBER, false, "ระยะตกปลอดภัย"),
    EXPLOSION_RESIST(Attribute.EXPLOSION_KNOCKBACK_RESISTANCE, Operation.ADD_NUMBER, true, "ต้านแรงกระเด็นจากระเบิด"),
    OXYGEN(Attribute.OXYGEN_BONUS, Operation.ADD_NUMBER, false, "ลมหายใจใต้น้ำ"),
    BREAK_SPEED(Attribute.BLOCK_BREAK_SPEED, Operation.MULTIPLY_SCALAR_1, true, "ความเร็วทุบบล็อก"),
    MINING_EFFICIENCY(Attribute.MINING_EFFICIENCY, Operation.ADD_NUMBER, false, "ประสิทธิภาพการขุด"),
    SUBMERGED_MINING(Attribute.SUBMERGED_MINING_SPEED, Operation.ADD_NUMBER, true, "ความเร็วขุดใต้น้ำ"),
    WATER_MOVEMENT(Attribute.WATER_MOVEMENT_EFFICIENCY, Operation.ADD_NUMBER, true, "ความคล่องในน้ำ"),
    // Staff stats feed Advance Magic's cast stats (MagicStats) while the staff is in the main hand.
    SPELL_POWER(null, Operation.ADD_NUMBER, true, "พลังเวท"),
    HEALING_POWER(null, Operation.ADD_NUMBER, true, "พลังรักษา"),
    MAX_MANA(null, Operation.ADD_NUMBER, false, "มานาสูงสุด"),
    MANA_COST(null, Operation.ADD_NUMBER, true, "ประหยัดมานา"),
    MANA_REGEN(null, Operation.ADD_NUMBER, true, "ฟื้นฟูมานา"),
    ELEMENT_FIRE(null, Operation.ADD_NUMBER, true, "พลังธาตุไฟ"),
    ELEMENT_WATER(null, Operation.ADD_NUMBER, true, "พลังธาตุน้ำและน้ำแข็ง"),
    ELEMENT_WIND(null, Operation.ADD_NUMBER, true, "พลังธาตุลม"),
    ELEMENT_EARTH(null, Operation.ADD_NUMBER, true, "พลังธาตุดิน"),
    ELEMENT_LIGHTNING(null, Operation.ADD_NUMBER, true, "พลังธาตุสายฟ้า"),
    ELEMENT_LIGHT(null, Operation.ADD_NUMBER, true, "พลังธาตุแสง"),
    ELEMENT_DARK(null, Operation.ADD_NUMBER, true, "พลังธาตุมืด");

    /** Null for the arrow and staff stats. */
    public final Attribute attribute;
    public final Operation operation;
    public final boolean percent;
    public final String label;

    Stat(Attribute attribute, Operation operation, boolean percent, String label) {
        this.attribute = attribute;
        this.operation = operation;
        this.percent = percent;
        this.label = label;
    }

    public String format(double value) {
        double shown = percent ? value * 100 : value;
        String number = String.format(java.util.Locale.ROOT, "%.1f", Math.abs(shown));
        if (number.endsWith(".0")) number = number.substring(0, number.length() - 2);
        return (value < 0 ? "-" : "+") + number + (percent ? "%" : "") + " " + label;
    }
}
