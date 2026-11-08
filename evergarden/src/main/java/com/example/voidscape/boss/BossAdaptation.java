package com.example.voidscape.boss;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Trident;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Phlegethon's weapon adaptation, which every boss also uses in phase three (config {@code boss-phase-three}).
 * Each accepted hit from the same weapon family halves the previous one (10, 5, 2.5 ...). A different family
 * resets the chain and lands in full, and returning to an earlier family starts at full damage again. One chain
 * per boss, shared by every attacker, so a party that mixes weapons keeps hitting hard. Misses and rejected hits
 * never reach it. Each boss picks {@code full}, {@code half} (stops at 50%) or {@code off}; see {@link #maxHalvings}.
 * In phase three the boss's own attacks also deal {@code damage-multiplier} times their damage.
 */
final class BossAdaptation {
    /** Config ids for {@code boss-phase-three.damage-multiplier.<id>}. */
    static final String ARKHAIA = "arkhaia", SERAPHON = "seraphon", JUDGE = "judge", DRAGONS = "dragons", REALM = "realm";

    private static final Map<String, String> NAMES = Map.of(
        "SWORD", "ดาบ", "AXE", "ขวาน", "PICKAXE", "อีเตอร์", "SHOVEL", "พลั่ว", "HOE", "จอบ",
        "BOW", "ธนู", "CROSSBOW", "หน้าไม้", "TRIDENT", "ตรีศูล", "MACE", "ค้อน", "FIST", "มือเปล่า");

    private String weapon = "";
    private int chain;

    String weapon() { return weapon; }
    int chain() { return chain; }

    void reset() {
        weapon = "";
        chain = 0;
    }

    /** One accepted hit under Phlegethon's rule; {@code cap} limits how many times it halves. */
    double adapt(String family, double damage, int cap) {
        if (!weapon.equals(family)) {
            weapon = family;
            chain = 0;
        }
        double adapted = Math.scalb(damage, -Math.min(chain, cap));
        chain = Math.min(1023, chain + 1);
        return adapted;
    }

    /** A phase-three hit: adapts it, tells the attacker how much got through and marks the boss. */
    double phaseThree(Plugin plugin, EntityDamageEvent event, Player attacker, double damage, String id, String boss, Location mark) {
        int cap = maxHalvings(plugin, id);
        if (cap <= 0 || attacker == null) return damage;
        String family = family(event, attacker);
        double adapted = adapt(family, damage, cap);
        String name = NAMES.getOrDefault(family, family);
        int halvings = Math.min(chain - 1, cap);
        attacker.sendActionBar(chain <= 1
            ? Component.text("⚔ " + boss + " ปรับตัวต้าน" + name + " — ตีซ้ำด้วยอาวุธเดิมจะเบาลง"
                + (cap == 1 ? "เหลือครึ่งหนึ่ง" : "ครึ่งหนึ่งทุกครั้ง"), NamedTextColor.GOLD)
            : Component.text("⚔ " + boss + " ปรับตัวต้าน" + name + " ×" + fraction(halvings) + " — สลับอาวุธเพื่อตีเต็มแรง", NamedTextColor.RED));
        if (mark != null && mark.getWorld() != null) mark.getWorld().spawnParticle(Particle.ENCHANT, mark, 18, 1.5, 2, 1.5, .3);
        return adapted;
    }

    /**
     * Adaptation mode of a boss ({@code boss-phase-three.adaptation.<id>}): {@code full} halves every repeat
     * without a floor (Phlegethon), {@code half} stops at 50% (Phlegethon's ritual rule), {@code off}. The Judge
     * defaults to {@code half}: its final phase already accepts melee only. A plain boolean for the whole key
     * (older files) turns every boss full or off. Returns the number of halvings allowed.
     */
    static int maxHalvings(Plugin plugin, String id) {
        var config = plugin.getConfig();
        if (config.isBoolean("boss-phase-three.adaptation")) return config.getBoolean("boss-phase-three.adaptation") ? 1023 : 0;
        String mode = config.getString("boss-phase-three.adaptation." + id, JUDGE.equals(id) ? "half" : "full");
        return switch (mode == null ? "full" : mode.trim().toLowerCase(Locale.ROOT)) {
            case "off", "false", "none" -> 0;
            case "half" -> 1;
            default -> 1023;
        };
    }

    /** How many times its normal damage a boss deals in phase three. */
    static double damageMultiplier(Plugin plugin, String boss) {
        double value = plugin.getConfig().getDouble("boss-phase-three.damage-multiplier." + boss, 2.0);
        return Double.isFinite(value) ? Math.clamp(value, 0.1, 10.0) : 2.0;
    }

    /**
     * Phase-three power for a blow sized as a share of the victim's max HP (Arkhaia's sweeps and Execute,
     * Seraphon's solar beam and radiant burst, the Judge's red spells). Multiplying such a blow kills from full
     * health whatever the gear, so the multiplied blow stops at {@code boss-phase-three.percent-blow-cap} of max HP
     * (default 0.95: a full-health player survives one, barely), yet it is never weaker than outside phase three.
     * A cap of 0 multiplies plainly. Flat blows use {@link #damageMultiplier} directly.
     */
    static double percentBlow(Plugin plugin, String boss, double amount, double maxHealth) {
        double multiplied = amount * damageMultiplier(plugin, boss);
        double cap = plugin.getConfig().getDouble("boss-phase-three.percent-blow-cap", 0.95);
        if (!Double.isFinite(cap) || cap <= 0 || !Double.isFinite(maxHealth) || maxHealth <= 0) return multiplied;
        return Math.max(amount, Math.min(multiplied, maxHealth * Math.min(cap, 10.0)));
    }

    private static String fraction(int halvings) {
        double value = Math.scalb(1.0, -Math.min(halvings, 1023));
        return value < 0.01 ? "<0.01" : String.format(Locale.ROOT, "%.3f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    /**
     * Weapon family of a hit. Swords, axes, pickaxes, shovels and hoes group by family regardless of material.
     * Arrows use their stored launch weapon (swapping held items before impact cannot trick it); tridents,
     * other projectiles, fists and every other held item are their own family.
     */
    static String family(EntityDamageEvent event, Player attacker) {
        if (event instanceof EntityDamageByEntityEvent by) {
            if (by.getDamager() instanceof Trident) return "TRIDENT";
            if (by.getDamager() instanceof AbstractArrow arrow) {
                ItemStack weapon = arrow.getWeapon();
                return weapon == null ? (arrow.isShotFromCrossbow() ? "CROSSBOW" : "BOW") : weapon.getType().name();
            }
            if (by.getDamager() instanceof Projectile projectile) return projectile.getType().name();
        }
        String type = attacker.getInventory().getItemInMainHand().getType().name();
        for (String family : List.of("SWORD", "PICKAXE", "AXE", "SHOVEL", "HOE")) if (type.endsWith("_" + family)) return family;
        return type.equals("AIR") ? "FIST" : type;
    }
}
