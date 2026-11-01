package com.example.forge;

import org.bukkit.Material;
import org.bukkit.inventory.EquipmentSlotGroup;

/** Which affixes a piece of gear can roll, decided by its base material. */
public enum LootCategory {
    MELEE(EquipmentSlotGroup.MAINHAND, Group.WEAPON),
    RANGED(EquipmentSlotGroup.MAINHAND, Group.RANGED),
    /** Either hand: shields usually sit in the off hand. */
    SHIELD(EquipmentSlotGroup.HAND, Group.SHIELD),
    TOOL(EquipmentSlotGroup.MAINHAND, Group.TOOL),
    HELMET(EquipmentSlotGroup.HEAD, Group.ARMOR),
    CHESTPLATE(EquipmentSlotGroup.CHEST, Group.ARMOR),
    LEGGINGS(EquipmentSlotGroup.LEGS, Group.ARMOR),
    BOOTS(EquipmentSlotGroup.FEET, Group.ARMOR);

    /** Gem effects are defined per group. */
    public enum Group {
        WEAPON("อาวุธประชิด"),
        RANGED("ธนู / หน้าไม้"),
        ARMOR("ชุดเกราะ"),
        SHIELD("โล่"),
        TOOL("เครื่องมือ / เบ็ด");

        public final String thai;

        Group(String thai) {
            this.thai = thai;
        }
    }

    public final EquipmentSlotGroup slot;
    public final Group group;

    LootCategory(EquipmentSlotGroup slot, Group group) {
        this.slot = slot;
        this.group = group;
    }

    public static LootCategory of(Material material) {
        if (material == null) return null;
        String name = material.name();
        if (name.endsWith("_SWORD") || name.endsWith("_AXE") || name.endsWith("_SPEAR")
            || material == Material.MACE || material == Material.TRIDENT) return MELEE;
        if (material == Material.BOW || material == Material.CROSSBOW) return RANGED;
        if (material == Material.SHIELD) return SHIELD;
        if (name.endsWith("_PICKAXE") || name.endsWith("_SHOVEL") || name.endsWith("_HOE") || material == Material.FISHING_ROD) return TOOL;
        if (name.endsWith("_HELMET")) return HELMET;
        if (name.endsWith("_CHESTPLATE") || material == Material.ELYTRA) return CHESTPLATE;
        if (name.endsWith("_LEGGINGS")) return LEGGINGS;
        if (name.endsWith("_BOOTS")) return BOOTS;
        return null;
    }
}
