package com.example.voidscape.item;

/**
 * 10,000-ticket balanced loot table for Evergarden Vault (Long-term server economy):
 * 0..1499    (15.0%) : Limit Break Scrolls (14 abilities including Mace & Thorns)
 * 1500..2699 (12.0%) : Unique Enchant Scrolls (22 abilities)
 * 2700..3399  (7.0%) : Advance Magic Cores (14 elements)
 * 3400..3899  (5.0%) : Special Relic Equipment (Rift Pickaxe, Smelter Pickaxe, Storm Bow, Nova Bow, Rift Blade, Eternal Aegis)
 * 3900..9799 (59.0%) : Useful Reagents & Elixirs (Key Shard, Repair Stone, Void Elixir, Echo Shard)
 * 9800..9899  (1.0%) : Flying Staff
 * 9900..9949  (0.5%) : Mythic Core (Shulker Levitation)
 * 9950..9999  (0.5%) : Scroll of Eternity (Unbreakable 100% Mythic)
 */
public final class VaultLootTable {
    public enum Reward {
        LIMIT_BREAK,
        UNIQUE_SCROLL,
        CORE,
        EQUIPMENT,
        CONSUMABLE,
        MYTHIC_CORE,
        SCROLL_ETERNITY,
        FLYING_STAFF
    }

    private VaultLootTable() {}

    public static Reward reward(int ticket) {
        if (ticket < 0 || ticket >= 10000) throw new IllegalArgumentException("Vault ticket must be 0..9999");
        if (ticket < 1500) return Reward.LIMIT_BREAK;
        if (ticket < 2700) return Reward.UNIQUE_SCROLL;
        if (ticket < 3400) return Reward.CORE;
        if (ticket < 3900) return Reward.EQUIPMENT;
        if (ticket < 9800) return Reward.CONSUMABLE;
        if (ticket < 9900) return Reward.FLYING_STAFF;
        if (ticket < 9950) return Reward.MYTHIC_CORE;
        return Reward.SCROLL_ETERNITY;
    }
}
