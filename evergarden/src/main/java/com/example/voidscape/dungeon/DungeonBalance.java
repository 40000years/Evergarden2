package com.example.voidscape.dungeon;

/** Sanctums sit below world bosses. Party size adds bounded HP, never damage. Tier 6-8 scale the Tier 5 numbers and add a rend. */
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
    /** Boss skill damage: Tier 1-5 ramp to the Tier 5 numbers; Tier 6-8 scale them like basic hits. */
    public static double skillMultiplier(int tier) { return tier >= 6 ? damageMultiplier(tier) : new double[]{.67,.75,.83,.90,1.0}[index(tier)]; }
    /** Tier 6-8 health builds on Tier 5: VI +20%, VII +5% on VI, VIII +5% on VII. */
    public static double healthMultiplier(int tier) { return tier >= 8 ? 1.2 * 1.05 * 1.05 : tier == 7 ? 1.2 * 1.05 : tier == 6 ? 1.2 : 1.0; }
    /**
     * Tier 6-8 hit far harder than Tier 5 (e2.193: the players who reach them wear late-game, Limit Break armor):
     * VI x1.45, VII x1.6, VIII x1.75 on every basic hit and boss skill.
     */
    public static double damageMultiplier(int tier) { return tier >= 8 ? 1.75 : tier == 7 ? 1.6 : tier == 6 ? 1.45 : 1.0; }
    /**
     * Share of each Tier 6-8 hit that lands again as a sonic rend, ignoring armor and Protection (Resistance,
     * absorption and Totems still apply), so enchanted armor cannot shrug these sanctums off: VI 22%, VII 28%, VIII 34%.
     */
    public static double rendRatio(int tier) { return tier >= 8 ? 0.34 : tier == 7 ? 0.28 : tier == 6 ? 0.22 : 0.0; }
    /** Tier 6-8 bosses are tougher still, on top of healthMultiplier: VI x1.5, VII x1.6, VIII x1.75. */
    public static double bossHealthMultiplier(int tier) { return tier >= 8 ? 1.75 : tier == 7 ? 1.6 : tier == 6 ? 1.5 : 1.0; }
    /** Damage the encounter's mobs take from a player's melee, arrows/thrown weapons and spells at a tier. */
    public static double meleeTaken(int tier) { return tier == 7 ? 0.30 : 1.0; }
    public static double rangedTaken(int tier) { return tier >= 8 ? 0.50 : 1.0; }
    public static double magicTaken(int tier) { return tier >= 8 ? 0.50 : 1.0; }
}
