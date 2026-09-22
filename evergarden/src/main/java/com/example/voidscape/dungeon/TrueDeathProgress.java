package com.example.voidscape.dungeon;

/** Pure state transition for the boss's True Death curse. */
public record TrueDeathProgress(int hits, int level, boolean triggered, boolean finalDeath) {
    public static TrueDeathProgress hit(int hits, int level, int hitsPerLevel, int maxLevel) {
        int safeHitsPerLevel=Math.max(1,hitsPerLevel),safeMaxLevel=Math.max(1,maxLevel);
        int nextHits=Math.max(0,hits)+1,nextLevel=Math.max(0,Math.min(level,safeMaxLevel));
        if(nextHits<safeHitsPerLevel)return new TrueDeathProgress(nextHits,nextLevel,false,false);
        nextLevel=Math.min(safeMaxLevel,nextLevel+1);
        return new TrueDeathProgress(0,nextLevel,true,nextLevel>=safeMaxLevel);
    }
}
