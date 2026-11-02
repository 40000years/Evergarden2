package com.example.voidscape.item;

/** One 10,000-ticket primary draw. Only diamond gear and books can award a second item. */
public final class VaultLootTable {
    public enum PrimaryReward {
        DIAMOND_GEAR, ENCHANT_BOOK, LIMIT_BREAK, UNIQUE_SCROLL, EQUIPMENT,
        RARE_WEAPON, LEGENDARY_WEAPON, ELEMENTAL_STAFF, FLYING_STAFF, SCROLL_ETERNITY,
        FLYING_STAFF_TIER2
    }
    private static final int[][] PRIMARY_WEIGHTS = {
        {6500,3000,300,180,20,0,0,0,0,0,0},
        {6125,3100,300,200,120,100,0,50,0,5,0},
        {5660,3000,350,300,200,300,30,100,50,10,0},
        {3380,2200,500,400,500,1700,400,400,200,20,300},
        {2670,1800,500,400,600,2200,600,500,200,30,500}
    };
    private static int index(int tier) { return Math.clamp(tier,1,5)-1; }
    private static void ticket(int ticket) {
        if(ticket<0||ticket>=10000)throw new IllegalArgumentException("Vault ticket must be 0..9999");
    }
    public static PrimaryReward primaryReward(int tier,int ticket) {
        ticket(ticket);
        int[] weights=PRIMARY_WEIGHTS[index(tier)];
        for(PrimaryReward reward:PrimaryReward.values())if((ticket-=weights[reward.ordinal()])<0)return reward;
        throw new IllegalStateException("Vault weights must total 10000");
    }
    public static int chanceBps(int tier,PrimaryReward reward) { return PRIMARY_WEIGHTS[index(tier)][reward.ordinal()]; }
    public static int doubleChanceBps(int tier) { return new int[]{0,1000,1500,2000,2500}[index(tier)]; }
    public static boolean canDouble(PrimaryReward reward) {
        return reward==PrimaryReward.DIAMOND_GEAR||reward==PrimaryReward.ENCHANT_BOOK;
    }
    public static PrimaryReward bonusReward(int ticket) {
        ticket(ticket);return ticket<6500?PrimaryReward.DIAMOND_GEAR:PrimaryReward.ENCHANT_BOOK;
    }
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

    /** Compatibility API for explicit bonus callers; normal openings use the unified primary draw. */
    public static WeaponReward weaponReward(int tier, int ticket) {
        if (ticket < 0 || ticket >= 10000) throw new IllegalArgumentException("Vault weapon ticket must be 0..9999");
        int legendary=chanceBps(tier,PrimaryReward.LEGENDARY_WEAPON);
        int chance=legendary+chanceBps(tier,PrimaryReward.RARE_WEAPON);
        if (ticket >= chance) return WeaponReward.NONE;
        if (tier == 2) return WeaponReward.RARE;
        return ticket < legendary ? WeaponReward.LEGENDARY : WeaponReward.RARE;
    }

    /** Elemental staff share of the primary pool; never rolled again during a normal opening. */
    public static int staffChanceBps(int tier) {
        return chanceBps(tier,PrimaryReward.ELEMENTAL_STAFF);
    }

    /** Compatibility helper for the current primary book category. */
    public static boolean enchantBookReward(int tier, int ticket) {
        if (ticket < 0 || ticket >= 10000) throw new IllegalArgumentException("Vault book ticket must be 0..9999");
        return primaryReward(tier,ticket)==PrimaryReward.ENCHANT_BOOK;
    }

    /** Historical table retained for binary compatibility; live Vaults use primaryReward. */
    @Deprecated
    public static Reward activeReward(int ticket){
        if(ticket<0||ticket>=9250)throw new IllegalArgumentException("Active Vault ticket must be 0..9249");
        int original=ticket>=2700?ticket+700:ticket;
        if(original>=9900)original+=50;
        return reward(original);
    }
    @Deprecated
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
