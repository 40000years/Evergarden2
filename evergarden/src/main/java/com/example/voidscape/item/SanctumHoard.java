package com.example.voidscape.item;

/**
 * The Tier VI-VIII Vault ("sanctum hoard"), e2.194. Those sanctums hit like realm bosses, so their Vault no longer
 * hands out the Tier V table's diamond gear, spare pickaxes and apprentice staffs. Each opening draws twice from this
 * table, then adds Golden Seraph Feathers, a gem of the sanctum's element with gem dust, and a charm chance. Eight
 * openings in a row without a jackpot make the ninth carry one. Tiers I-V keep {@link VaultLootTable} unchanged.
 */
public final class SanctumHoard {
    private SanctumHoard() {}

    public enum Reward {
        /** Vault enchanted gear upgraded to netherite, with Forge affixes (Rare/Epic at VI, Epic/Mythic at VII-VIII). */
        NETHERITE_GEAR,
        ENCHANT_BOOK,
        /** A Limit Break scroll whose ceiling passes VIII: IX at VI, X at VII and VIII. */
        LIMIT_BREAK,
        UNIQUE_SCROLL,
        /** Two Forge gems of purity III/IV/V. */
        GEMS,
        RARE_WEAPON,
        LEGENDARY_WEAPON,
        /** An Arcane elemental staff (Mythic: 30% at VIII). */
        STAFF,
        FLYING_STAFF_TIER2,
        SCROLL_ETERNITY
    }

    private static final int[][] WEIGHTS = {
        {2600, 1700, 1300, 600, 900, 1500, 1000, 300, 80, 20},
        {2300, 1500, 1500, 700, 900, 1400, 1300, 300, 70, 30},
        {2000, 1400, 1700, 800, 900, 1200, 1600, 300, 60, 40}
    };
    /** Draws per opening, replacing the old Tier V roll plus the lower-tier bonus roll. */
    public static final int ROLLS = 2;
    /** Openings in a row without a jackpot after which the next one carries one. */
    public static final int PITY = 8;

    private static int index(int tier) { return Math.clamp(tier, 6, 8) - 6; }

    public static Reward reward(int tier, int ticket) {
        if (ticket < 0 || ticket >= 10000) throw new IllegalArgumentException("Hoard ticket must be 0..9999");
        int[] weights = WEIGHTS[index(tier)];
        for (Reward reward : Reward.values()) if ((ticket -= weights[reward.ordinal()]) < 0) return reward;
        throw new IllegalStateException("Hoard weights must total 10000");
    }

    public static int chanceBps(int tier, Reward reward) { return WEIGHTS[index(tier)][reward.ordinal()]; }

    /** Forge rarity of hoard gear: VI Rare 60 / Epic 40, VII Epic 85 / Mythic 15, VIII Epic 60 / Mythic 40. */
    public static String gearRarity(int tier, int percent) {
        return switch (index(tier)) {
            case 0 -> percent < 60 ? "RARE" : "EPIC";
            case 1 -> percent < 85 ? "EPIC" : "MYTHIC";
            default -> percent < 60 ? "EPIC" : "MYTHIC";
        };
    }

    public static int goldenFeathers(int tier) { return index(tier) + 1; }
    /** Purity of the guaranteed element gem: II / III / IV. */
    public static int themedGemPurity(int tier) { return index(tier) + 2; }
    public static int gemDust(int tier) { return 4 + index(tier) * 2; }
    /** Purity of the GEMS draw: III / IV / V. */
    public static int gemPurity(int tier) { return index(tier) + 3; }
    /** Charm chance per opening: 10% / 15% / 22%. */
    public static int charmChanceBps(int tier) { return new int[]{1000, 1500, 2200}[index(tier)]; }
    public static boolean jackpot(Reward reward) {
        return reward == Reward.LEGENDARY_WEAPON || reward == Reward.FLYING_STAFF_TIER2 || reward == Reward.SCROLL_ETERNITY;
    }
}
