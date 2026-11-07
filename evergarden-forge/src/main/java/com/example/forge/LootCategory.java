package com.example.forge;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/** Which affixes a piece of gear can roll, decided by its base material (Advance Magic staffs by their own keys). */
public enum LootCategory {
    MELEE(EquipmentSlotGroup.MAINHAND, Group.WEAPON),
    RANGED(EquipmentSlotGroup.MAINHAND, Group.RANGED),
    /** Either hand: shields usually sit in the off hand. */
    SHIELD(EquipmentSlotGroup.HAND, Group.SHIELD),
    TOOL(EquipmentSlotGroup.MAINHAND, Group.TOOL),
    HELMET(EquipmentSlotGroup.HEAD, Group.ARMOR),
    CHESTPLATE(EquipmentSlotGroup.CHEST, Group.ARMOR),
    LEGGINGS(EquipmentSlotGroup.LEGS, Group.ARMOR),
    BOOTS(EquipmentSlotGroup.FEET, Group.ARMOR),
    STAFF(EquipmentSlotGroup.MAINHAND, Group.STAFF);

    /** Gem effects are defined per group. */
    public enum Group {
        WEAPON("อาวุธประชิด"),
        RANGED("ธนู / หน้าไม้"),
        ARMOR("ชุดเกราะ"),
        SHIELD("โล่"),
        TOOL("เครื่องมือ / เบ็ด"),
        STAFF("คทาเวท");

        public final String label;

        Group(String thai) {
            this.label = thai;
        }
    }

    public final EquipmentSlotGroup slot;
    public final Group group;

    LootCategory(EquipmentSlotGroup slot, Group group) {
        this.slot = slot;
        this.group = group;
    }

    private static final NamespacedKey STAFF_ELEMENT = new NamespacedKey("advance_magic", "staff_element");
    private static final NamespacedKey STAFF_KIND = new NamespacedKey("advance_magic", "staff_kind");

    /** Advance Magic staffs (elemental or wooden) are carrot-on-a-stick items marked with the staff keys. */
    public static LootCategory of(ItemStack item) {
        if (item == null) return null;
        if (item.getType() == Material.CARROT_ON_A_STICK && item.hasItemMeta()) {
            var data = item.getItemMeta().getPersistentDataContainer();
            if (data.has(STAFF_ELEMENT, PersistentDataType.STRING) || "wood".equals(data.get(STAFF_KIND, PersistentDataType.STRING))) return STAFF;
        }
        return of(item.getType());
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
