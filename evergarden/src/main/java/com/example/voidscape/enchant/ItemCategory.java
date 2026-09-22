package com.example.voidscape.enchant;

import org.bukkit.Material;

public enum ItemCategory {
    BOW, PICKAXE, HOE, AXE, TOOL, SWORD, MELEE, MACE, TRIDENT, CHESTPLATE, BOOTS, LEGGINGS, HELMET, ARMOR, ANY;

    public boolean matches(Material mat) {
        if (mat == null) return false;
        String name = mat.name();
        return switch (this) {
            case BOW -> mat == Material.BOW || mat == Material.CROSSBOW;
            case PICKAXE -> name.endsWith("_PICKAXE");
            case HOE -> name.endsWith("_HOE");
            case AXE -> name.endsWith("_AXE");
            case TOOL -> name.endsWith("_PICKAXE") || name.endsWith("_AXE") || name.endsWith("_SHOVEL") || name.endsWith("_HOE");
            case SWORD -> name.endsWith("_SWORD");
            case MELEE -> name.endsWith("_SWORD") || name.endsWith("_AXE") || mat == Material.TRIDENT || mat == Material.MACE;
            case MACE -> mat == Material.MACE;
            case TRIDENT -> mat == Material.TRIDENT;
            case HELMET -> name.endsWith("_HELMET") || mat == Material.TURTLE_HELMET;
            case CHESTPLATE -> name.endsWith("_CHESTPLATE") || mat == Material.ELYTRA;
            case LEGGINGS -> name.endsWith("_LEGGINGS");
            case BOOTS -> name.endsWith("_BOOTS");
            case ARMOR -> name.endsWith("_HELMET") || name.endsWith("_CHESTPLATE") || name.endsWith("_LEGGINGS") || name.endsWith("_BOOTS") || mat == Material.TURTLE_HELMET;
            case ANY -> mat.getMaxDurability() > 0;
        };
    }
}
