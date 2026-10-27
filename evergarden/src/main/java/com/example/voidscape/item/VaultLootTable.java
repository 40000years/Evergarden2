package com.example.voidscape.item;

/**
 * 10,000-ticket balanced loot table for Evergarden Vault (Long-term server economy):
 * 0..1499    (15.0%) : Limit Break Scrolls (14 abilities including Mace & Thorns)
 * 1500..2699 (12.0%) : Unique Enchant Scrolls (22 abilities)
 * 2700..3399  (7.0%) : Advance Magic Cores (14 elements)
 * 3400..3899  (5.0%) : Special Relic Tools (Rift Pickaxe, Smelter Pickaxe)
 * 3900..9749 (58.5%) : Mana Potions (Medium 60%, Good 30%, Supreme 10%)
 * 9750..9899  (1.5%) : Flying Staff
 * 9900..9949  (0.5%) : Mythic Core (Levitation / Solar Apocalypse / Chronos: Final Hour / Heaven's Judgment, equally likely)
 * 9950..9999  (0.5%) : Scroll of Eternity (Unbreakable 100% Mythic)
 */
public final class VaultLootTable {
    public enum WeaponReward { NONE, RARE, LEGENDARY }

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

    /** One bonus per Vault: T2 0.25% Rare only; T3-5 5/10/15%, split 80% Rare / 20% Legendary. */
    public static WeaponReward weaponReward(int tier, int ticket) {
        if (ticket < 0 || ticket >= 10000) throw new IllegalArgumentException("Vault weapon ticket must be 0..9999");
        int chance = switch (tier) {
            case 2 -> 25;
            case 3 -> 500;
            case 4 -> 1000;
            case 5 -> 1500;
            default -> 0;
        };
        if (ticket >= chance) return WeaponReward.NONE;
        if (tier == 2) return WeaponReward.RARE;
        return ticket < chance / 5 ? WeaponReward.LEGENDARY : WeaponReward.RARE;
    }

    /** One independent elemental staff attempt per opening, regardless of major reward count. */
    public static int staffChanceBps(int tier) {
        return switch (tier) {
            case 2 -> 25;
            case 3 -> 1000;
            case 4 -> 1500;
            case 5 -> 2000;
            default -> 0;
        };
    }

    /** Early-tier books replace part of the ordinary major reward pool. */
    public static boolean enchantBookReward(int tier, int ticket) {
        if (ticket < 0 || ticket >= 10000) throw new IllegalArgumentException("Vault book ticket must be 0..9999");
        return ticket < switch (tier) {
            case 1 -> 3500;
            case 2 -> 4500;
            default -> 0;
        };
    }

    /** Model-first phase: remove retired core tickets and retain relative odds of supported rewards. */
    public static Reward activeReward(int ticket){
        if(ticket<0||ticket>=9250)throw new IllegalArgumentException("Active Vault ticket must be 0..9249");
        int original=ticket>=2700?ticket+700:ticket;
        if(original>=9900)original+=50;
        return reward(original);
    }
    public static Reward reward(int ticket) {
        if (ticket < 0 || ticket >= 10000) throw new IllegalArgumentException("Vault ticket must be 0..9999");
        if (ticket < 1500) return Reward.LIMIT_BREAK;
        if (ticket < 2700) return Reward.UNIQUE_SCROLL;
        if (ticket < 3400) return Reward.CORE;
        if (ticket < 3900) return Reward.EQUIPMENT;
        if (ticket < 9750) return Reward.CONSUMABLE;
        if (ticket < 9900) return Reward.FLYING_STAFF;
        if (ticket < 9950) return Reward.MYTHIC_CORE;
        return Reward.SCROLL_ETERNITY;
    }
}
