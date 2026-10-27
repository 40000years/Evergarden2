package com.example.voidscape.dungeon;

/** Sanctums sit below world bosses. Party size adds bounded HP, never damage. */
public final class DungeonBalance {
    private DungeonBalance() {}
    public enum Species { MINION, CASTER, STALKER, BOSS, VEX }
    public record Stats(double health, double damage) {}
    private static int index(int tier) { return Math.clamp(tier, 1, 5) - 1; }
    public static Stats stats(Species species, int tier, int players) {
        int i = index(tier), extra = Math.clamp(players - 1, 0, 2);
        return switch (species) {
            case BOSS -> new Stats(new double[]{360,440,520,620,740}[i] + extra * new double[]{50,60,70,80,90}[i],
                    new double[]{14,16,18,21,24}[i]);
            case MINION -> new Stats(new double[]{60,80,100,125,150}[i] + extra * 10,
                    new double[]{7,8,10,12,14}[i]);
            case CASTER -> new Stats(new double[]{50,65,85,105,125}[i] + extra * 8,
                    new double[]{6,7,8,10,12}[i]);
            case STALKER -> new Stats(new double[]{70,95,120,145,175}[i] + extra * 12,
                    new double[]{8,10,12,14,16}[i]);
            case VEX -> new Stats(new double[]{8,9,10,12,14}[i], new double[]{6,7,8,9,10}[i]);
        };
    }
    public static double armor(int tier) { return new double[]{6,7,8,9,10}[index(tier)]; }
    public static double toughness(int tier) { return new double[]{2,3,4,5,6}[index(tier)]; }
    public static double incomingMultiplier(int tier) { return new double[]{.95,.93,.90,.87,.85}[index(tier)]; }
    public static double skillMultiplier(int tier) { return new double[]{.67,.75,.83,.90,1.0}[index(tier)]; }
}
