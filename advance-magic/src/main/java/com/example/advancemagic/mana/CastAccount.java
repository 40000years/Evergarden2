package com.example.advancemagic.mana;

import java.util.HashMap;
import java.util.Map;

/** Pure accounting; milliseconds are supplied by the caller for deterministic tests. */
public final class CastAccount {
    private double mana;
    private double maxMana = 100.0;
    private double regenRate = 2.0;
    private final Map<String, Long> cooldowns = new HashMap<>();

    public CastAccount(int mana) {
        this((double)mana, 100.0, 2.0);
    }

    public CastAccount(double mana) {
        this(mana, 100.0, 2.0);
    }

    public CastAccount(double mana, double maxMana, double regenRate) {
        this.maxMana = Math.max(10.0, Math.min(300.0, maxMana));
        this.regenRate = Math.max(0.1, regenRate);
        this.mana = Math.max(0.0, Math.min(this.maxMana, mana));
    }

    public int mana() { return (int)Math.round(mana); }
    public double manaExact() { return mana; }
    public int maxManaInt() { return (int)Math.round(maxMana); }
    public double maxMana() { return maxMana; }
    public double regenRate() { return regenRate; }

    public void setMaxMana(double max) {
        this.maxMana = Math.max(10.0, Math.min(300.0, max));
        this.mana = Math.min(this.maxMana, this.mana);
    }

    public void setRegenRate(double rate) {
        this.regenRate = Math.max(0.1, rate);
    }

    public void setMana(double mana) {
        this.mana = Math.max(0.0, Math.min(this.maxMana, mana));
    }

    public boolean regenerate() {
        if (mana >= maxMana) return false;
        mana = Math.min(maxMana, mana + regenRate);
        return true;
    }

    public long remaining(String spell, long now) {
        return Math.max(0, cooldowns.getOrDefault(spell, 0L) - now);
    }

    public boolean reserve(String spell, int cost, double seconds, long now) {
        if (cost < 0 || seconds < 0 || mana < cost || remaining(spell, now) > 0) return false;
        mana -= cost;
        cooldowns.put(spell, now + (long)(seconds * 1000.0));
        return true;
    }

    public boolean reserve(String spell, int cost, int seconds, long now) {
        return reserve(spell, cost, (double)seconds, now);
    }

    public void refund(String spell, int cost) {
        mana = Math.min(maxMana, mana + cost);
        cooldowns.remove(spell);
    }

    public void restore(String spell, long end) {
        cooldowns.put(spell, end);
    }

    public long end(String spell) {
        return cooldowns.getOrDefault(spell, 0L);
    }
}
