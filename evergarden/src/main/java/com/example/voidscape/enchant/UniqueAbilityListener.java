package com.example.voidscape.enchant;

import com.example.voidscape.VoidscapePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.*;

public final class UniqueAbilityListener implements Listener {
    private final VoidscapePlugin plugin;
    private final NamespacedKey arrowUniqueKey, arrowShooterKey;
    private final Map<UUID, Integer> thunderHits = new HashMap<>();
    private final Map<UUID, UUID> thunderLastTarget = new HashMap<>();
    private final Map<UUID, Long> phoenixCooldown = new HashMap<>();
    private final Map<UUID, Long> shadowStepCooldown = new HashMap<>();
    private final Map<UUID, Long> lastSneakTime = new HashMap<>();
    private final Map<UUID, Long> bladeVortexCooldown = new HashMap<>();
    private final Map<UUID, Integer> grantedMiningHaste = new HashMap<>();
    private final Set<UUID> recursiveBreaking = new HashSet<>();

    public UniqueAbilityListener(VoidscapePlugin plugin) {
        this.plugin = plugin;
        this.arrowUniqueKey = new NamespacedKey("evergarden", "shot_unique");
        this.arrowShooterKey = new NamespacedKey("evergarden", "shot_shooter");
    }

    // ==========================================
    // 🏹 1. RANGED & BOW ENCHANTS
    // ==========================================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBowShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        ItemStack bow = event.getBow();
        if (bow == null || !bow.hasItemMeta()) return;

        Entity proj = event.getProjectile();
        if (!(proj instanceof AbstractArrow arrow)) return;

        List<String> activeEnchants = new ArrayList<>();
        for (UniqueEnchant ue : List.of(
            UniqueEnchant.COLOSSUS_SLAYER, UniqueEnchant.RICOCHET,
            UniqueEnchant.KINETIC_GRAPPLE, UniqueEnchant.ABSOLUTE_ZERO,
            UniqueEnchant.SINGULARITY, UniqueEnchant.METEOR_ARROW
        )) {
            if (EnchantApplyListener.hasUnique(bow, ue)) {
                activeEnchants.add(ue.name());
            }
        }
        if (!activeEnchants.isEmpty()) {
            arrow.getPersistentDataContainer().set(arrowUniqueKey, PersistentDataType.STRING, String.join(",", activeEnchants));
            arrow.getPersistentDataContainer().set(arrowShooterKey, PersistentDataType.STRING, player.getUniqueId().toString());
        }

        int lbPower = plugin.relics().getLimitBreakLevel(bow, LimitBreakType.POWER);
        if (lbPower > 5) {
            arrow.getPersistentDataContainer().set(plugin.key("arrow_lb_power"), PersistentDataType.INTEGER, lbPower);
        }
    }

    private boolean arrowHasUnique(AbstractArrow arrow, UniqueEnchant ue) {
        if (arrow == null) return false;
        String raw = arrow.getPersistentDataContainer().get(arrowUniqueKey, PersistentDataType.STRING);
        if (raw == null) return false;
        for (String part : raw.split(",")) {
            if (part.trim().equalsIgnoreCase(ue.name())) return true;
        }
        return false;
    }

    private boolean canDamage(Player damager, Entity victim) {
        if (!(victim instanceof LivingEntity) || victim instanceof ArmorStand || !victim.isValid() || victim.isDead()) return false;
        if (damager != null && damager.equals(victim)) return false;
        if (victim instanceof Player p) {
            if (damager == null) return false;
            return damager.getWorld().getPVP() && plugin.getConfig().getBoolean("relics.allow-pvp", false)
                && p.getGameMode() != GameMode.CREATIVE && p.getGameMode() != GameMode.SPECTATOR;
        }
        return true;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof AbstractArrow arrow)) return;
        String rawUe = arrow.getPersistentDataContainer().get(arrowUniqueKey, PersistentDataType.STRING);
        if (rawUe == null) return;

        String rawShooter = arrow.getPersistentDataContainer().get(arrowShooterKey, PersistentDataType.STRING);
        Player shooter = rawShooter != null ? Bukkit.getPlayer(UUID.fromString(rawShooter)) : null;
        if (shooter == null || !shooter.isOnline()) return;

        Location at = arrow.getLocation();

        for (String part : rawUe.split(",")) {
            UniqueEnchant ue;
            try { ue = UniqueEnchant.valueOf(part.trim().toUpperCase(Locale.ROOT)); } catch (Exception ignored) { continue; }

            switch (ue) {
                case KINETIC_GRAPPLE -> {
                    Location targetLoc = event.getHitBlock() != null ? event.getHitBlock().getLocation().add(0.5, 1.0, 0.5)
                        : event.getHitEntity() != null ? event.getHitEntity().getLocation() : at;
                    Vector pull = targetLoc.toVector().subtract(shooter.getLocation().toVector());
                    double dist = pull.length();
                    if (dist > 2.0) {
                        pull.normalize().multiply(Math.min(1.8, 0.8 + dist * 0.05));
                        pull.setY(Math.min(0.9, Math.max(0.35, pull.getY() + 0.25)));
                        com.example.voidscape.compat.PlayerImpulse.apply(plugin,shooter,pull);
                        shooter.setFallDistance(0);
                        shooter.playSound(shooter.getLocation(), Sound.ENTITY_WIND_CHARGE_WIND_BURST, 1.0f, 1.2f);
                        shooter.getWorld().spawnParticle(Particle.CLOUD, shooter.getLocation(), 15, 0.3, 0.3, 0.3, 0.05);
                    }
                }
                case ABSOLUTE_ZERO -> {
                    at.getWorld().spawnParticle(Particle.SNOWFLAKE, at, 50, 1.5, 1.5, 1.5, 0.1);
                    at.getWorld().playSound(at, Sound.BLOCK_GLASS_BREAK, 1.0f, 0.8f);
                    for (Entity e : at.getNearbyEntities(5, 5, 5)) {
                        if (canDamage(shooter, e) && e instanceof LivingEntity target) {
                            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 70, 6)); // Slowness VII
                            target.setFreezeTicks(300);
                            target.damage(10, shooter);
                        }
                    }
                }
                case SINGULARITY -> {
                    at.getWorld().playSound(at, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 1.0f, 0.6f);
                    new org.bukkit.scheduler.BukkitRunnable() {
                        int ticks = 0;
                        @Override
                        public void run() {
                            if (++ticks > 12 || !shooter.isOnline()) { cancel(); return; }
                            at.getWorld().spawnParticle(Particle.PORTAL, at, 40, 0.8, 0.8, 0.8, 0.5);
                            at.getWorld().spawnParticle(Particle.REVERSE_PORTAL, at, 25, 0.5, 0.5, 0.5, 0.2);
                            for (Entity e : at.getNearbyEntities(6.5, 4.0, 6.5)) {
                                if (canDamage(shooter, e) && e instanceof LivingEntity m) {
                                    Vector v = at.toVector().subtract(m.getLocation().toVector()).normalize().multiply(0.45);
                                    com.example.voidscape.compat.PlayerImpulse.apply(plugin,m,v);
                                }
                            }
                        }
                    }.runTaskTimer(plugin, 1L, 5L);
                }
                case METEOR_ARROW -> {
                    at.getWorld().spawnParticle(Particle.FLAME, at, 20, 0.5, 0.1, 0.5, 0.05);
                    at.getWorld().playSound(at, Sound.ENTITY_BLAZE_SHOOT, 1.0f, 0.7f);
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        at.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, at, 2);
                        at.getWorld().playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.8f);
                        for (Entity e : at.getNearbyEntities(5.0, 3.0, 5.0)) {
                            if (canDamage(shooter, e) && e instanceof LivingEntity m) {
                                m.damage(32.0, shooter);
                                m.setFireTicks(100);
                            }
                        }
                    }, 20L);
                }
                case RICOCHET -> {
                    if (event.getHitEntity() instanceof LivingEntity victim) {
                        List<LivingEntity> nearby = new ArrayList<>();
                        for (Entity e : victim.getNearbyEntities(7, 4, 7)) {
                            if (canDamage(shooter, e) && !e.equals(victim) && e instanceof LivingEntity target) {
                                nearby.add(target);
                            }
                        }
                        int chained = 0;
                        for (LivingEntity next : nearby) {
                            if (++chained > 3) break;
                            next.getWorld().strikeLightningEffect(next.getLocation());
                            next.damage(14.0, shooter);
                            next.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, next.getLocation().add(0, 1, 0), 15, 0.3, 0.3, 0.3, 0.1);
                        }
                        if (chained > 0) {
                            victim.getWorld().playSound(victim.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.8f, 1.6f);
                        }
                    }
                }
                default -> {}
            }
        }
    }

    // ==========================================
    // ⚔️ 2. MELEE COMBAT & COLOSSUS SLAYER
    // ==========================================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        // Virtual Thorns Retaliation (Levels 4-10) when player is attacked
        if (event.getEntity() instanceof Player victimPlayer && !IN_THORNS_REFLECTION.get()) {
            handleVirtualThorns(event, victimPlayer);
        }

        // Colossus Slayer bonus damage calculation and Virtual Power
        if (event.getDamager() instanceof AbstractArrow arrow) {
            int effectivePower = 0;
            Integer arrowPower = arrow.getPersistentDataContainer().get(plugin.key("arrow_lb_power"), PersistentDataType.INTEGER);
            if (arrowPower != null && arrowPower > 5) {
                effectivePower = arrowPower;
            } else if (arrow.getShooter() instanceof Player shooter) {
                ItemStack bow = shooter.getInventory().getItemInMainHand();
                if (bow.getType() != Material.BOW && bow.getType() != Material.CROSSBOW) {
                    bow = shooter.getInventory().getItemInOffHand();
                }
                effectivePower = plugin.relics().getLimitBreakLevel(bow, LimitBreakType.POWER);
            }
            if (effectivePower > 5) {
                event.setDamage(event.getDamage() + (effectivePower - 5) * 2.5);
            }

            if (arrowHasUnique(arrow, UniqueEnchant.COLOSSUS_SLAYER) && event.getEntity() instanceof LivingEntity victim) {
                var maxHpAttr = victim.getAttribute(Attribute.MAX_HEALTH);
                double maxHp = maxHpAttr != null ? maxHpAttr.getValue() : 20.0;
                double bonus = Math.min(Math.max(12.0, maxHp * 0.08), 350.0);
                event.setDamage(event.getDamage() + bonus);
                victim.getWorld().playSound(victim.getLocation(), Sound.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, 0.8f, 1.4f);
                victim.getWorld().spawnParticle(Particle.CRIT, victim.getLocation().add(0, 1, 0), 20, 0.3, 0.3, 0.3, 0.2);
                if (arrow.getShooter() instanceof Player shooter) {
                    shooter.sendActionBar(Component.text("✦ ล่าไททัน! โบนัส +" + (int) bonus + " ดาเมจ (% Max HP)", NamedTextColor.GOLD));
                }
            }
            return;
        }

        if (!(event.getDamager() instanceof Player player) || !(event.getEntity() instanceof LivingEntity victim)) return;
        if (victim instanceof ArmorStand) return;
        ItemStack weapon = player.getInventory().getItemInMainHand();
        if (weapon == null || !weapon.hasItemMeta()) return;

        // Virtual Sharpness Bonus (Levels 6-10)
        int lbSharp = plugin.relics().getLimitBreakLevel(weapon, LimitBreakType.SHARPNESS);
        if (lbSharp > 5) {
            double bonusSharp = (lbSharp - 5) * 1.5;
            event.setDamage(event.getDamage() + bonusSharp);
        }

        // Virtual Smite Bonus (Levels 6-10) against undead
        int lbSmite = plugin.relics().getLimitBreakLevel(weapon, LimitBreakType.SMITE);
        if (lbSmite > 5 && isUndead(victim)) {
            double bonusSmite = (lbSmite - 5) * 2.5;
            event.setDamage(event.getDamage() + bonusSmite);
            victim.getWorld().spawnParticle(Particle.CRIT, victim.getLocation().add(0, 1, 0), 6, 0.2, 0.3, 0.2, 0.1);
        }

        // Virtual Sweeping Edge Bonus (Levels 4-8)
        int lbSweep = plugin.relics().getLimitBreakLevel(weapon, LimitBreakType.SWEEPING_EDGE);
        if (lbSweep > 3 && weapon.getType().name().endsWith("_SWORD")) {
            double sweepBonus = (lbSweep - 3) * 1.5;
            for (Entity nearby : victim.getNearbyEntities(2.5, 2.5, 2.5)) {
                if (nearby instanceof LivingEntity nearbyLiving && nearbyLiving != victim && nearbyLiving != player && canDamage(player, nearbyLiving)) {
                    nearbyLiving.damage(Math.min(event.getDamage() * 0.5 + sweepBonus, 40.0), player);
                    nearbyLiving.getWorld().spawnParticle(Particle.SWEEP_ATTACK, nearbyLiving.getLocation().add(0, 1, 0), 1);
                }
            }
        }

        // Virtual Mace Density & Breach Bonuses (Mace)
        if (weapon.getType() == Material.MACE) {
            int lbDensity = plugin.relics().getLimitBreakLevel(weapon, LimitBreakType.DENSITY);
            if (lbDensity > 5) {
                float fallDist = player.getFallDistance();
                if (fallDist > 1.5f) {
                    double extraDmg = Math.min(80.0, fallDist * (lbDensity - 5) * 0.5);
                    event.setDamage(event.getDamage() + extraDmg);
                    victim.getWorld().spawnParticle(Particle.CRIT, victim.getLocation().add(0, 1, 0), 12, 0.3, 0.3, 0.3, 0.2);
                }
            }

            int lbBreach = plugin.relics().getLimitBreakLevel(weapon, LimitBreakType.BREACH);
            if (lbBreach > 4) {
                var armorAttr = victim.getAttribute(Attribute.ARMOR);
                if (armorAttr != null && armorAttr.getValue() > 0) {
                    double extraPen = (lbBreach - 4) * 0.10;
                    double bonusArmorDmg = Math.min(30.0, armorAttr.getValue() * extraPen);
                    event.setDamage(event.getDamage() + bonusArmorDmg);
                    victim.getWorld().playSound(victim.getLocation(), Sound.ITEM_ARMOR_EQUIP_CHAIN, 0.8f, 1.4f);
                }
            }
        }

        // Titan Breach (Armor breach + True Damage on Axe attacks)
        if (EnchantApplyListener.hasUnique(weapon, UniqueEnchant.TITAN_BREACH) && canDamage(player, victim)) {
            var armorAttr = victim.getAttribute(Attribute.ARMOR);
            double armorVal = armorAttr != null ? armorAttr.getValue() : 0.0;
            double trueDamage = 10.0 + (armorVal * 0.35); // Breaches high armor targets
            
            victim.setNoDamageTicks(0);
            double oldHp = victim.getHealth();
            double newHp = Math.max(0.0, oldHp - trueDamage);
            victim.setHealth(newHp);
            if (newHp <= 0.001) {
                victim.damage(1.0, player);
            }
            
            victim.getWorld().playSound(victim.getLocation(), Sound.ITEM_ARMOR_EQUIP_CHAIN, 1.0f, 0.6f);
            victim.getWorld().playSound(victim.getLocation(), Sound.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, 0.8f, 1.8f);
            victim.getWorld().spawnParticle(Particle.CRIT, victim.getLocation().add(0, 1, 0), 20, 0.3, 0.5, 0.3, 0.15);
            victim.getWorld().spawnParticle(Particle.BLOCK, victim.getLocation().add(0, 1, 0), 15, 0.3, 0.3, 0.3, Bukkit.createBlockData(Material.IRON_BLOCK));
            player.sendActionBar(Component.text("✦ ขวานเจาะเกราะ! (Titan Breach True Damage +" + (int) trueDamage + ")", NamedTextColor.GOLD));
        }

        // Guillotine (Execute mobs under 15% HP)
        if (EnchantApplyListener.hasUnique(weapon, UniqueEnchant.GUILLOTINE)) {
            var maxHpAttr = victim.getAttribute(Attribute.MAX_HEALTH);
            double maxHp = maxHpAttr != null ? maxHpAttr.getValue() : 20.0;
            if (!(victim instanceof Player) && (victim.getHealth() / maxHp) <= 0.15) {
                victim.setNoDamageTicks(0);
                event.setDamage(Math.max(victim.getHealth() * 25.0, 5000.0));
                victim.getWorld().playSound(victim.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.2f, 0.5f);
                victim.getWorld().spawnParticle(Particle.BLOCK, victim.getLocation().add(0, 1, 0), 25, 0.3, 0.5, 0.3, Bukkit.createBlockData(Material.REDSTONE_BLOCK));
                player.sendActionBar(Component.text("✦ กิโยตินปลิดชีพ! (Execute สังหารทันที)", NamedTextColor.RED));
                return;
            }
        }

        // Echo Strike (35% chance to hit twice with 100% damage)
        if (EnchantApplyListener.hasUnique(weapon, UniqueEnchant.ECHO_STRIKE) && canDamage(player, victim)) {
            if (Math.random() < 0.35) {
                double damage = event.getFinalDamage();
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (victim.isValid() && !victim.isDead() && canDamage(player, victim)) {
                        victim.setNoDamageTicks(0); // Critical: bypass invulnerability tick so 100% damage registers!
                        victim.damage(damage, player);
                        victim.getWorld().spawnParticle(Particle.SWEEP_ATTACK, victim.getLocation().add(0, 1, 0), 1);
                        victim.getWorld().playSound(victim.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.8f, 1.6f);
                        player.sendActionBar(Component.text("✦ เงาดาบซ้ำสอง! (Echo Strike 100%)", NamedTextColor.LIGHT_PURPLE));
                    }
                }, 4L);
            }
        }

        // Thunderlord (Every 3rd hit deals True Damage lightning)
        if (EnchantApplyListener.hasUnique(weapon, UniqueEnchant.THUNDERLORD) && canDamage(player, victim)) {
            UUID prevTarget = thunderLastTarget.get(player.getUniqueId());
            int hits = (prevTarget != null && prevTarget.equals(victim.getUniqueId())) ? thunderHits.getOrDefault(player.getUniqueId(), 0) + 1 : 1;
            thunderLastTarget.put(player.getUniqueId(), victim.getUniqueId());
            if (hits >= 3) {
                thunderHits.remove(player.getUniqueId());
                victim.getWorld().strikeLightningEffect(victim.getLocation());
                victim.setNoDamageTicks(0);
                double trueDamage = 25.0;
                double oldHp = victim.getHealth();
                double newHp = Math.max(0.0, oldHp - trueDamage);
                victim.setHealth(newHp);
                if (newHp <= 0.001) {
                    victim.damage(1.0, player);
                }
                player.playSound(player.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.8f, 1.2f);
                player.sendActionBar(Component.text("✦ สายฟ้าทัณฑ์สวรรค์! (True Damage เจาะเกราะ)", NamedTextColor.AQUA));
            } else {
                thunderHits.put(player.getUniqueId(), hits);
            }
        }

        // Soul Harvest (Lifesteal 5% on hit)
        if (EnchantApplyListener.hasUnique(weapon, UniqueEnchant.SOUL_HARVEST)) {
            double heal = Math.max(0.5, Math.min(5.0, event.getFinalDamage() * 0.05));
            var playerMaxHp = player.getAttribute(Attribute.MAX_HEALTH);
            double max = playerMaxHp != null ? playerMaxHp.getValue() : 20.0;
            player.setHealth(Math.min(max, player.getHealth() + heal));
            player.getWorld().spawnParticle(Particle.SOUL, player.getLocation().add(0, 1.2, 0), 4, 0.15, 0.15, 0.15, 0.02);
        }

        // Vampiric (Lifesteal 15% of damage)
        if (EnchantApplyListener.hasUnique(weapon, UniqueEnchant.VAMPIRIC)) {
            double heal = Math.min(6.0, event.getFinalDamage() * 0.15);
            if (heal > 0.5) {
                var playerMaxHp = player.getAttribute(Attribute.MAX_HEALTH);
                double max = playerMaxHp != null ? playerMaxHp.getValue() : 20.0;
                player.setHealth(Math.min(max, player.getHealth() + heal));
                player.getWorld().spawnParticle(Particle.HEART, player.getLocation().add(0, 1.8, 0), 2, 0.2, 0.2, 0.2, 0.05);
            }
        }

        // Blade Vortex trigger on melee hit
        triggerBladeVortex(player);
    }

    // Blade Vortex (Sweeping air blade projectile on swing or hit)
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerSwing(PlayerInteractEvent event) {
        if (!event.getAction().isLeftClick()) return;
        triggerBladeVortex(event.getPlayer());
    }

    private void triggerBladeVortex(Player player) {
        ItemStack weapon = player.getInventory().getItemInMainHand();
        if (weapon == null || !weapon.hasItemMeta()) return;

        if (EnchantApplyListener.hasUnique(weapon, UniqueEnchant.BLADE_VORTEX)) {
            long now = System.currentTimeMillis();
            if (bladeVortexCooldown.getOrDefault(player.getUniqueId(), 0L) > now) return;
            bladeVortexCooldown.put(player.getUniqueId(), now + 3000L); // 3.0s cooldown

            Vector dir = player.getLocation().getDirection().normalize();
            Location start = player.getEyeLocation();
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 0.7f);

            Set<UUID> hitTargets = new HashSet<>();
            for (double d = 1.0; d <= 7.0; d += 1.0) {
                Location point = start.clone().add(dir.clone().multiply(d));
                if (point.getBlock().getType().isSolid()) {
                    point.getWorld().spawnParticle(Particle.BLOCK, point, 8, 0.2, 0.2, 0.2, Bukkit.createBlockData(point.getBlock().getType()));
                    break; // Solid block obstruction: air blade does not phase through solid walls!
                }
                point.getWorld().spawnParticle(Particle.SWEEP_ATTACK, point, 1);
                for (Entity e : point.getNearbyEntities(1.5, 1.5, 1.5)) {
                    if (e instanceof LivingEntity m && canDamage(player, m) && hitTargets.add(m.getUniqueId())) {
                        m.damage(18.0, player);
                        com.example.voidscape.compat.PlayerImpulse.apply(plugin, m, dir.clone().multiply(0.4).setY(0.2));
                    }
                }
            }
            player.sendActionBar(Component.text("✦ คลื่นดาบสุญญากาศ! (Blade Vortex)", NamedTextColor.AQUA));
        }
    }

    // Soul Harvest (Gain soul stacks on monster kill)
    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) return;
        ItemStack weapon = killer.getInventory().getItemInMainHand();
        if (weapon == null || !weapon.hasItemMeta()) return;

        if (EnchantApplyListener.hasUnique(weapon, UniqueEnchant.SOUL_HARVEST)) {
            killer.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 15, 1));
            var killerMaxHp = killer.getAttribute(Attribute.MAX_HEALTH);
            double max = killerMaxHp != null ? killerMaxHp.getValue() : 20.0;
            killer.setHealth(Math.min(max, killer.getHealth() + 3.0));
            killer.playSound(killer.getLocation(), Sound.ENTITY_VEX_CHARGE, 0.7f, 1.6f);
            killer.getWorld().spawnParticle(Particle.SOUL, killer.getLocation().add(0, 1, 0), 12, 0.3, 0.3, 0.3, 0.05);
            killer.sendActionBar(Component.text("✦ เกี่ยววิญญาณสำเร็จ! (+Speed & ฟื้นฟูพลังชีวิต)", NamedTextColor.DARK_PURPLE));
        }
    }

    // ==========================================
    // ⛏️ 3. MINING & TOOLS ENCHANTS
    // ==========================================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockDamage(org.bukkit.event.block.BlockDamageEvent event) {
        Player player = event.getPlayer();
        clearMiningHaste(player);
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (tool == null || !tool.hasItemMeta()) return;
        if (plugin.relics().migrate(tool)) player.getInventory().setItemInMainHand(tool);

        // Virtual Efficiency Bonus (Levels 6-10: Haste I at 6-7, Haste II at 8-9, Haste III at 10)
        int lbEff = plugin.relics().getLimitBreakLevel(tool, LimitBreakType.EFFICIENCY);
        boolean ownedEfficiency = tool.getItemMeta().getPersistentDataContainer()
            .has(plugin.key("lb_efficiency"), PersistentDataType.INTEGER);
        boolean correctBlock = isCorrectMiningTool(tool.getType(), event.getBlock().getType());
        if (ownedEfficiency && correctBlock && lbEff >= 9) {
            Block block = event.getBlock();
            if (isPickaxeInstaMineable(block.getType())) {
                event.setInstaBreak(true);
            }
        }
        if (ownedEfficiency && correctBlock && lbEff > 5) {
            int amp = lbEff >= 10 ? 2 : (lbEff >= 8 ? 1 : 0);
            grantMiningHaste(player, amp);
        }

        // Advance Tool (Haste boost for ultra-fast digging on all platforms & Bedrock)
        if (EnchantApplyListener.hasUnique(tool, UniqueEnchant.ADVANCE_TOOL)) {
            grantMiningHaste(player, 1);
        }
    }

    private void grantMiningHaste(Player player, int amplifier) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 60, amplifier, true, false, false));
        PotionEffect active = player.getPotionEffect(PotionEffectType.HASTE);
        if (active != null && active.getAmplifier() == amplifier && active.isAmbient()
            && !active.hasParticles() && !active.hasIcon() && active.getDuration() <= 60) {
            grantedMiningHaste.put(player.getUniqueId(), amplifier);
        }
    }

    private void clearMiningHaste(Player player) {
        Integer amplifier = grantedMiningHaste.remove(player.getUniqueId());
        if (amplifier == null) return;
        PotionEffect active = player.getPotionEffect(PotionEffectType.HASTE);
        if (active != null && active.getAmplifier() == amplifier && active.isAmbient()
            && !active.hasParticles() && !active.hasIcon() && active.getDuration() <= 60) {
            player.removePotionEffect(PotionEffectType.HASTE);
        }
    }

    @EventHandler
    public void onHeldItemChange(PlayerItemHeldEvent event) {
        clearMiningHaste(event.getPlayer());
    }

    @EventHandler
    public void onMiningHasteQuit(PlayerQuitEvent event) {
        clearMiningHaste(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() != GameMode.SURVIVAL) return;
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (tool == null || !tool.hasItemMeta()) return;
        if (recursiveBreaking.contains(player.getUniqueId())) return;

        Block origin = event.getBlock();

        // 1. Demeter's Scythe (9x9 auto harvest & replant for Hoes)
        boolean isEvergardenCrop = plugin.crops() != null && (plugin.crops().getCropAt(origin.getLocation()) != null || plugin.crops().getCropAt(origin.getRelative(org.bukkit.block.BlockFace.UP).getLocation()) != null);
        if (EnchantApplyListener.hasUnique(tool, UniqueEnchant.DEMETER_SCYTHE) && (origin.getBlockData() instanceof Ageable || isEvergardenCrop)) {
            event.setCancelled(true);
            harvestCropsArea(player, origin);
            return;
        }

        // 2. Timber Titan / Titan Breach (Fell whole tree for Axes)
        if (EnchantApplyListener.hasUnique(tool, UniqueEnchant.TITAN_BREACH) && Tag.LOGS.isTagged(origin.getType())) {
            fellTree(player, origin);
            return;
        }

        // 3. Vein Smelter (Vein miner + auto smelt + fortune into inventory)
        if (EnchantApplyListener.hasUnique(tool, UniqueEnchant.VEIN_SMELTER) && isOre(origin.getType())) {
            event.setDropItems(false);
            mineVeinSmelt(player, origin);
            return;
        }

        // 4. Seismic Slam (3x3 mining for Pickaxes)
        if (EnchantApplyListener.hasUnique(tool, UniqueEnchant.SEISMIC_SLAM) && !player.isSneaking() && Tag.MINEABLE_PICKAXE.isTagged(origin.getType())) {
            RayTraceResult ray = player.rayTraceBlocks(5.5);
            var face = ray != null ? ray.getHitBlockFace() : null;
            if (face == null) {
                face = getMiningFace(player);
            }
            mine3x3Area(player, origin, face, tool);
        }
    }

    // Telepathy (Drops warp directly into player's inventory)
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockDropItem(BlockDropItemEvent event) {
        Player player = event.getPlayer();
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (tool == null || !tool.hasItemMeta()) return;

        // Virtual Fortune Bonus (Levels 4-10: uncapped scaling +1..+2 at lvl 4 up to +4..+8 at lvl 10)
        int lbFortune = plugin.relics().getLimitBreakLevel(tool, LimitBreakType.FORTUNE);
        if (lbFortune > 3) {
            int extra = lbFortune - 3;
            int minBonus = Math.max(1, (extra + 1) / 2);
            int maxBonus = extra + 1;
            boolean bonusGiven = false;
            for (Item itemEntity : event.getItems()) {
                ItemStack dropStack = itemEntity.getItemStack();
                if (dropStack.getMaxStackSize() > 1 && isFortuneDrop(dropStack.getType())) {
                    int add = minBonus + (int) (Math.random() * (maxBonus - minBonus + 1));
                    dropStack.setAmount(Math.min(dropStack.getMaxStackSize(), dropStack.getAmount() + add));
                    itemEntity.setItemStack(dropStack);
                    bonusGiven = true;
                }
            }
            if (bonusGiven && Math.random() < 0.35) {
                player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, player.getLocation().add(0, 1, 0), 5, 0.3, 0.3, 0.3, 0.1);
            }
        }

        // Vein Smelter: Auto-smelt drops
        if (EnchantApplyListener.hasUnique(tool, UniqueEnchant.VEIN_SMELTER)) {
            for (Item itemEntity : event.getItems()) {
                ItemStack dropStack = itemEntity.getItemStack();
                ItemStack smelted = smeltItem(dropStack);
                if (smelted != null) {
                    itemEntity.setItemStack(smelted);
                }
            }
        }

        if (EnchantApplyListener.hasUnique(tool, UniqueEnchant.TELEPATHY)) {
            Iterator<Item> it = event.getItems().iterator();
            while (it.hasNext()) {
                Item drop = it.next();
                var leftover = player.getInventory().addItem(drop.getItemStack());
                if (leftover.isEmpty()) {
                    drop.remove();
                    it.remove();
                } else {
                    drop.setItemStack(leftover.values().iterator().next());
                }
            }
        }
    }

    private boolean isFortuneDrop(Material mat) {
        String name = mat.name();
        return name.contains("RAW_") || name.endsWith("_INGOT") || name.equals("DIAMOND")
            || name.equals("EMERALD") || name.equals("COAL") || name.equals("REDSTONE")
            || name.equals("LAPIS_LAZULI") || name.equals("NETHER_QUARTZ") || name.equals("AMETHYST_SHARD")
            || name.equals("GLOWSTONE_DUST") || name.equals("CLAY_BALL") || name.equals("COPPER_INGOT")
            || name.equals("CARROT") || name.equals("POTATO") || name.equals("WHEAT_SEEDS")
            || name.equals("BEETROOT_SEEDS") || name.equals("NETHER_WART") || name.equals("MELON_SLICE")
            || name.equals("PRISMARINE_CRYSTALS") || name.equals("PRISMARINE_SHARD");
    }

    private boolean isPickaxeInstaMineable(Material mat) {
        if (mat == null) return false;
        String name = mat.name();
        return name.contains("DEEPSLATE") || name.contains("STONE") || name.contains("ORE")
            || name.contains("COBBLE") || name.contains("ANDESITE") || name.contains("DIORITE")
            || name.contains("GRANITE") || name.contains("TUFF") || name.contains("BASALT")
            || name.contains("BLACKSTONE") || name.contains("NETHERRACK") || name.contains("SANDSTONE")
            || name.contains("BRICK") || name.contains("PRISMARINE");
    }

    private boolean isCorrectMiningTool(Material tool, Material block) {
        String name=tool.name();
        if(name.endsWith("_PICKAXE"))return Tag.MINEABLE_PICKAXE.isTagged(block);
        if(name.endsWith("_AXE"))return Tag.MINEABLE_AXE.isTagged(block);
        if(name.endsWith("_SHOVEL"))return Tag.MINEABLE_SHOVEL.isTagged(block);
        if(name.endsWith("_HOE"))return Tag.MINEABLE_HOE.isTagged(block);
        return false;
    }

    private org.bukkit.block.BlockFace getMiningFace(Player player) {
        float pitch = player.getLocation().getPitch();
        if (pitch > 45) return org.bukkit.block.BlockFace.UP;
        if (pitch < -45) return org.bukkit.block.BlockFace.DOWN;
        float yaw = (player.getLocation().getYaw() % 360 + 360) % 360;
        if (yaw >= 45 && yaw < 135) return org.bukkit.block.BlockFace.WEST;
        if (yaw >= 135 && yaw < 225) return org.bukkit.block.BlockFace.NORTH;
        if (yaw >= 225 && yaw < 315) return org.bukkit.block.BlockFace.EAST;
        return org.bukkit.block.BlockFace.SOUTH;
    }

    public void harvestCropsArea(Player player, Block origin) {
        recursiveBreaking.add(player.getUniqueId());
        try {
            ItemStack tool = player.getInventory().getItemInMainHand();
            boolean hasTelepathy = EnchantApplyListener.hasUnique(tool, UniqueEnchant.TELEPATHY);
            int count = 0;
            for (int x = -4; x <= 4; x++) {
                for (int z = -4; z <= 4; z++) {
                    Block b = origin.getRelative(x, 0, z);
                    if (!b.getWorld().getWorldBorder().isInside(b.getLocation())) continue;
                    if (b.getState() instanceof org.bukkit.inventory.InventoryHolder) continue;
                    if (b.getBlockData() instanceof Ageable ageable) {
                        if (ageable.getAge() >= ageable.getMaximumAge()) {
                            count++;
                            // Drop mature crops with x2 multiplier
                            for (ItemStack drop : b.getDrops(tool, player)) {
                                drop.setAmount(drop.getAmount() * 2);
                                if (hasTelepathy) {
                                    var leftover = player.getInventory().addItem(drop);
                                    leftover.values().forEach(rem -> b.getWorld().dropItemNaturally(b.getLocation().add(0.5, 0.3, 0.5), rem));
                                } else {
                                    b.getWorld().dropItemNaturally(b.getLocation().add(0.5, 0.3, 0.5), drop);
                                }
                            }
                            ageable.setAge(0); // Replant
                            b.setBlockData(ageable);
                            b.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, b.getLocation().add(0.5, 0.5, 0.5), 2);
                        }
                    } else if (plugin.crops() != null) {
                        com.example.voidscape.crop.PlantedCrop customCrop = plugin.crops().getCropAt(b.getLocation());
                        if (customCrop == null) {
                            customCrop = plugin.crops().getCropAt(b.getRelative(org.bukkit.block.BlockFace.UP).getLocation());
                        }
                        if (customCrop != null && customCrop.isMature()) {
                            plugin.crops().harvest(customCrop, player, false);
                            count++;
                            b.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, customCrop.getLocation().add(0.5, 0.5, 0.5), 2);
                        }
                    }
                }
            }
            if (count > 0) {
                damageTool(player, tool, Math.max(1, count / 4));
                player.playSound(origin.getLocation(), Sound.BLOCK_CROP_BREAK, 1.0f, 1.2f);
                player.sendActionBar(Component.text("✦ เคียวเทพกสิกรรม! (เก็บเกี่ยวและปลูกคืน 9×9 อัตโนมัติ " + count + " แปลง)", NamedTextColor.GREEN));
            }
        } finally {
            recursiveBreaking.remove(player.getUniqueId());
        }
    }

    private void fellTree(Player player, Block origin) {
        recursiveBreaking.add(player.getUniqueId());
        try {
            Queue<Block> queue = new LinkedList<>();
            Set<Block> visited = new HashSet<>();
            queue.add(origin);
            visited.add(origin);
            int count = 0;

            while (!queue.isEmpty() && count < 256) {
                Block curr = queue.poll();
                if (curr.getState() instanceof org.bukkit.inventory.InventoryHolder) continue;
                if (!curr.getWorld().getWorldBorder().isInside(curr.getLocation())) continue;
                count++;
                player.breakBlock(curr);

                for (int x = -2; x <= 2; x++) {
                    for (int y = -1; y <= 2; y++) {
                        for (int z = -2; z <= 2; z++) {
                            Block next = curr.getRelative(x, y, z);
                            if (!visited.contains(next)) {
                                if (Tag.LOGS.isTagged(next.getType()) || Tag.LEAVES.isTagged(next.getType())) {
                                    visited.add(next);
                                    queue.add(next);
                                }
                            }
                        }
                    }
                }
            }
            player.playSound(origin.getLocation(), Sound.BLOCK_WOOD_BREAK, 1.0f, 0.8f);
            player.sendActionBar(Component.text("✦ โค่นทั้งป่า! (Timber Titan โค่นและเก็บเกี่ยว " + count + " บล็อก)", NamedTextColor.GOLD));
        } finally {
            recursiveBreaking.remove(player.getUniqueId());
        }
    }

    private void mineVeinSmelt(Player player, Block origin) {
        recursiveBreaking.add(player.getUniqueId());
        try {
            Material type = origin.getType();
            ItemStack tool = player.getInventory().getItemInMainHand();
            boolean hasTelepathy = EnchantApplyListener.hasUnique(tool, UniqueEnchant.TELEPATHY);
            Queue<Block> queue = new LinkedList<>();
            Set<Block> visited = new HashSet<>();
            queue.add(origin);
            visited.add(origin);
            int count = 0;
            int totalExp = 0;

            while (!queue.isEmpty() && count < 32) {
                Block curr = queue.poll();
                if (curr.getState() instanceof org.bukkit.inventory.InventoryHolder) continue;
                if (!curr.getWorld().getWorldBorder().isInside(curr.getLocation())) continue;
                if (count > 0 && !curr.equals(origin)) {
                    // Check protection plugins / anti-cheat
                    BlockBreakEvent testEvent = new BlockBreakEvent(curr, player);
                    Bukkit.getPluginManager().callEvent(testEvent);
                    if (testEvent.isCancelled()) continue;
                }
                count++;

                // Retrieve drops taking player's tool and Fortune enchant into account
                Collection<ItemStack> drops = curr.getDrops(tool, player);
                if (drops == null || drops.isEmpty()) {
                    ItemStack fallback = getSmeltedProduct(curr.getType());
                    if (fallback != null) drops = List.of(fallback);
                } else {
                    List<ItemStack> converted = new ArrayList<>();
                    for (ItemStack item : drops) {
                        converted.add(smeltItem(item));
                    }
                    drops = converted;
                }

                int exp = getOreExp(curr.getType());
                if (exp > 0) totalExp += exp;

                curr.setType(Material.AIR);

                if (drops != null) {
                    for (ItemStack drop : drops) {
                        if (drop == null || drop.getType() == Material.AIR || drop.getAmount() <= 0) continue;
                        if (hasTelepathy) {
                            var leftover = player.getInventory().addItem(drop);
                            leftover.values().forEach(rem -> curr.getWorld().dropItemNaturally(curr.getLocation().add(0.5, 0.5, 0.5), rem));
                        } else {
                            curr.getWorld().dropItemNaturally(curr.getLocation().add(0.5, 0.5, 0.5), drop);
                        }
                    }
                }
                curr.getWorld().spawnParticle(Particle.FLAME, curr.getLocation().add(0.5, 0.5, 0.5), 4, 0.2, 0.2, 0.2, 0.02);

                // Tool durability damage (if not unbreakable / eternity)
                if (count > 1 && player.getGameMode() == GameMode.SURVIVAL && tool.hasItemMeta() && !plugin.relics().isEternityItem(tool)) {
                    if (tool.getItemMeta() instanceof org.bukkit.inventory.meta.Damageable dmg) {
                        int unbreaking = plugin.relics().getLimitBreakLevel(tool, LimitBreakType.UNBREAKING);
                        if (Math.random() < (1.0 / (unbreaking + 1))) {
                            dmg.setDamage(dmg.getDamage() + 1);
                            tool.setItemMeta(dmg);
                            if (dmg.getDamage() >= tool.getType().getMaxDurability()) {
                                tool.setAmount(0);
                                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
                                break;
                            }
                        }
                    }
                }

                for (int x = -1; x <= 1; x++) {
                    for (int y = -1; y <= 1; y++) {
                        for (int z = -1; z <= 1; z++) {
                            Block next = curr.getRelative(x, y, z);
                            if (!visited.contains(next) && next.getType() == type) {
                                visited.add(next);
                                queue.add(next);
                            }
                        }
                    }
                }
            }

            if (totalExp > 0) {
                player.giveExp(totalExp);
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.2f);
            }

            player.playSound(origin.getLocation(), Sound.BLOCK_FURNACE_FIRE_CRACKLE, 1.0f, 1.2f);
            player.sendActionBar(Component.text("✦ หลอมสายแร่คู่! (ขุดและหลอม " + count + " ก้อน)", NamedTextColor.GOLD));
        } finally {
            recursiveBreaking.remove(player.getUniqueId());
        }
    }

    private void mine3x3Area(Player player, Block origin, org.bukkit.block.BlockFace face, ItemStack tool) {
        recursiveBreaking.add(player.getUniqueId());
        try {
            for (int a = -1; a <= 1; a++) {
                for (int b = -1; b <= 1; b++) {
                    if (a == 0 && b == 0) continue;
                    Block block = face.getModY() != 0 ? origin.getRelative(a, 0, b)
                        : face.getModX() != 0 ? origin.getRelative(0, a, b) : origin.getRelative(a, b, 0);
                    if (block.getState() instanceof org.bukkit.inventory.InventoryHolder) continue;
                    if (!block.getWorld().getWorldBorder().isInside(block.getLocation())) continue;
                    if (!Tag.MINEABLE_PICKAXE.isTagged(block.getType()) || block.getType().getHardness() < 0) continue;
                    player.breakBlock(block);
                }
            }
        } finally {
            recursiveBreaking.remove(player.getUniqueId());
        }
    }

    private void damageTool(Player player, ItemStack tool, int amount) {
        if (tool == null || !tool.hasItemMeta() || player.getGameMode() != GameMode.SURVIVAL) return;
        if (plugin.relics().isEternityItem(tool)) return;
        if (tool.getItemMeta() instanceof org.bukkit.inventory.meta.Damageable dmg) {
            int unbreaking = plugin.relics().getLimitBreakLevel(tool, LimitBreakType.UNBREAKING);
            int finalDmg = 0;
            for (int i = 0; i < amount; i++) {
                if (Math.random() < (1.0 / (unbreaking + 1))) {
                    finalDmg++;
                }
            }
            if (finalDmg > 0) {
                dmg.setDamage(dmg.getDamage() + finalDmg);
                tool.setItemMeta(dmg);
                if (dmg.getDamage() >= tool.getType().getMaxDurability()) {
                    tool.setAmount(0);
                    player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
                }
            }
        }
    }

    private boolean isOre(Material m) {
        String n = m.name();
        return n.contains("_ORE") || m == Material.ANCIENT_DEBRIS || n.contains("RAW_");
    }

    private ItemStack smeltItem(ItemStack raw) {
        if (raw == null) return null;
        int amount = raw.getAmount();
        return switch (raw.getType()) {
            case RAW_IRON, IRON_ORE, DEEPSLATE_IRON_ORE -> new ItemStack(Material.IRON_INGOT, amount);
            case RAW_GOLD, GOLD_ORE, DEEPSLATE_GOLD_ORE, NETHER_GOLD_ORE -> new ItemStack(Material.GOLD_INGOT, amount);
            case RAW_COPPER, COPPER_ORE, DEEPSLATE_COPPER_ORE -> new ItemStack(Material.COPPER_INGOT, amount);
            case RAW_IRON_BLOCK -> new ItemStack(Material.IRON_INGOT, amount * 9);
            case RAW_GOLD_BLOCK -> new ItemStack(Material.GOLD_INGOT, amount * 9);
            case RAW_COPPER_BLOCK -> new ItemStack(Material.COPPER_INGOT, amount * 9);
            case ANCIENT_DEBRIS -> new ItemStack(Material.NETHERITE_SCRAP, amount);
            case COBBLESTONE -> new ItemStack(Material.STONE, amount);
            case COBBLED_DEEPSLATE -> new ItemStack(Material.DEEPSLATE, amount);
            case SAND, RED_SAND -> new ItemStack(Material.GLASS, amount);
            case CLAY_BALL -> new ItemStack(Material.BRICK, amount);
            default -> raw.clone(); // Natural gems/minerals (Coal, Diamond, Emerald, Lapis, Redstone, Quartz) remain intact!
        };
    }

    private ItemStack getSmeltedProduct(Material m) {
        return switch (m) {
            case IRON_ORE, DEEPSLATE_IRON_ORE, RAW_IRON_BLOCK -> new ItemStack(Material.IRON_INGOT, m == Material.RAW_IRON_BLOCK ? 9 : 1);
            case GOLD_ORE, DEEPSLATE_GOLD_ORE, NETHER_GOLD_ORE, RAW_GOLD_BLOCK -> new ItemStack(Material.GOLD_INGOT, m == Material.RAW_GOLD_BLOCK ? 9 : 1);
            case COPPER_ORE, DEEPSLATE_COPPER_ORE, RAW_COPPER_BLOCK -> new ItemStack(Material.COPPER_INGOT, m == Material.RAW_COPPER_BLOCK ? 9 : 1);
            case ANCIENT_DEBRIS -> new ItemStack(Material.NETHERITE_SCRAP, 1);
            case COAL_ORE, DEEPSLATE_COAL_ORE -> new ItemStack(Material.COAL, 1);
            case DIAMOND_ORE, DEEPSLATE_DIAMOND_ORE -> new ItemStack(Material.DIAMOND, 1);
            case EMERALD_ORE, DEEPSLATE_EMERALD_ORE -> new ItemStack(Material.EMERALD, 1);
            case LAPIS_ORE, DEEPSLATE_LAPIS_ORE -> new ItemStack(Material.LAPIS_LAZULI, 6);
            case REDSTONE_ORE, DEEPSLATE_REDSTONE_ORE -> new ItemStack(Material.REDSTONE, 5);
            case NETHER_QUARTZ_ORE -> new ItemStack(Material.QUARTZ, 1);
            default -> null;
        };
    }

    private int getOreExp(Material m) {
        return switch (m) {
            case COAL_ORE, DEEPSLATE_COAL_ORE -> (int) (Math.random() * 3);
            case DIAMOND_ORE, DEEPSLATE_DIAMOND_ORE, EMERALD_ORE, DEEPSLATE_EMERALD_ORE -> (int) (Math.random() * 5) + 3;
            case LAPIS_ORE, DEEPSLATE_LAPIS_ORE, NETHER_QUARTZ_ORE -> (int) (Math.random() * 4) + 2;
            case REDSTONE_ORE, DEEPSLATE_REDSTONE_ORE -> (int) (Math.random() * 4) + 1;
            case NETHER_GOLD_ORE -> (int) (Math.random() * 2);
            default -> 1;
        };
    }

    // ==========================================
    // 🛡️ 4. ARMOR & SURVIVAL ENCHANTS
    // ==========================================

    // Phoenix Rebirth (Fatal damage cancel + 50% HP heal)
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onFatalDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        ItemStack chest = player.getInventory().getChestplate();
        if (chest == null || !chest.hasItemMeta()) return;

        if (EnchantApplyListener.hasUnique(chest, UniqueEnchant.PHOENIX_REBIRTH)) {
            if (player.getHealth() - event.getFinalDamage() <= 0) {
                long now = System.currentTimeMillis();
                long cd = phoenixCooldown.getOrDefault(player.getUniqueId(), 0L);
                if (now >= cd) {
                    event.setCancelled(true);
                    phoenixCooldown.put(player.getUniqueId(), now + (10 * 60 * 1000L)); // 10 minutes

                    var maxHpAttr = player.getAttribute(Attribute.MAX_HEALTH);
                    double maxHp = maxHpAttr != null ? maxHpAttr.getValue() : 20.0;
                    player.setHealth(maxHp * 0.5);
                    player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 20 * 60, 0));
                    player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 20 * 30, 1));

                    if (event.getCause() == EntityDamageEvent.DamageCause.VOID) {
                        Location safeLoc = player.getRespawnLocation();
                        if (safeLoc == null) {
                            safeLoc = player.getWorld().getSpawnLocation();
                        }
                        player.teleport(safeLoc, PlayerTeleportEvent.TeleportCause.PLUGIN);
                        player.setVelocity(new Vector(0, 0, 0));
                        player.setFallDistance(0);
                    }

                    // Fire knockback wave
                    Location pLoc = player.getLocation();
                    pLoc.getWorld().spawnParticle(Particle.FLAME, pLoc.add(0, 1, 0), 80, 2.0, 1.0, 2.0, 0.15);
                    pLoc.getWorld().playSound(pLoc, Sound.ITEM_TOTEM_USE, 1.0f, 0.8f);
                    pLoc.getWorld().playSound(pLoc, Sound.ENTITY_BLAZE_SHOOT, 1.0f, 0.6f);

                    for (Entity e : player.getNearbyEntities(8.0, 4.0, 8.0)) {
                        if (e instanceof LivingEntity m && !e.equals(player) && !(e instanceof ArmorStand)) {
                            Vector push = m.getLocation().toVector().subtract(player.getLocation().toVector()).normalize().multiply(1.8).setY(0.5);
                            com.example.voidscape.compat.PlayerImpulse.apply(plugin,m,push);
                            m.setFireTicks(120);
                        }
                    }

                    player.sendActionBar(Component.text("✦ ฟีนิกซ์คืนชีพ! ปลดปล่อยเปลวเพลิงคุ้มครองชีวิต!", NamedTextColor.GOLD));
                    player.sendMessage(Component.text("✦ [Phoenix Rebirth] คุณรอดพ้นจากความตาย! คูลดาวน์ 10 นาที", NamedTextColor.GOLD));
                }
            }
        }

        // Titan Stance (Reduce explosion damage by 40%)
        ItemStack legs = player.getInventory().getLeggings();
        if (legs != null && legs.hasItemMeta() && EnchantApplyListener.hasUnique(legs, UniqueEnchant.TITAN_STANCE)) {
            if (event.getCause() == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION || event.getCause() == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION) {
                event.setDamage(event.getDamage() * 0.6);
            }
        }
    }

    // Titan Stance (Knockback immunity)
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTitanKnockback(io.papermc.paper.event.entity.EntityKnockbackEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        ItemStack legs = player.getInventory().getLeggings();
        if (legs != null && legs.hasItemMeta() && EnchantApplyListener.hasUnique(legs, UniqueEnchant.TITAN_STANCE)) {
            event.setCancelled(true);
        }
    }

    // Shadow Step (Double sneak to blink 6 blocks)
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerSneak(PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) return;
        Player player = event.getPlayer();
        ItemStack boots = player.getInventory().getBoots();
        if (boots == null || !boots.hasItemMeta()) return;

        if (EnchantApplyListener.hasUnique(boots, UniqueEnchant.SHADOW_STEP)) {
            long now = System.currentTimeMillis();
            long last = lastSneakTime.getOrDefault(player.getUniqueId(), 0L);
            lastSneakTime.put(player.getUniqueId(), now);

            if (now - last <= 550L) { // Double tap within 550ms (friendly for mobile touchscreen and ping)
                long cd = shadowStepCooldown.getOrDefault(player.getUniqueId(), 0L);
                if (now < cd) {
                    long left = (cd - now + 999) / 1000;
                    player.sendActionBar(Component.text("ก้าวพริบตา คูลดาวน์ " + left + " วิ", NamedTextColor.GRAY));
                    return;
                }
                shadowStepCooldown.put(player.getUniqueId(), now + 4000L); // 4s cooldown

                Location start = player.getLocation();
                Vector dir = start.getDirection().setY(0);
                if (dir.lengthSquared() < 0.001) dir = new Vector(0, 0, 1);
                else dir.normalize();

                double maxDist = 6.0;
                var ray = player.getWorld().rayTraceBlocks(start.clone().add(0, 1.0, 0), dir, maxDist, FluidCollisionMode.NEVER, true);
                if (ray != null && ray.getHitBlock() != null) {
                    maxDist = Math.max(0.5, ray.getHitPosition().distance(start.toVector()) - 0.6);
                }

                Location target = start.clone().add(dir.clone().multiply(maxDist));
                target.setY(start.getY());

                if (target.getBlock().isPassable() && target.clone().add(0, 1, 0).getBlock().isPassable()) {
                    player.teleport(target, PlayerTeleportEvent.TeleportCause.PLUGIN);
                    player.setFallDistance(0);
                    player.playSound(target, Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.2f);
                    player.getWorld().spawnParticle(Particle.PORTAL, start.clone().add(0, 1, 0), 20, 0.3, 0.5, 0.3, 0.1);
                    player.getWorld().spawnParticle(Particle.REVERSE_PORTAL, target.clone().add(0, 1, 0), 20, 0.3, 0.5, 0.3, 0.1);
                    player.sendActionBar(Component.text("✦ ก้าวพริบตา! (Shadow Step)", NamedTextColor.LIGHT_PURPLE));
                }
            }
        }
    }

    // Soulbound (Prevent gear drop on death natively via Paper getItemsToKeep)
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        int savedCount = 0;
        Iterator<ItemStack> it = event.getDrops().iterator();

        while (it.hasNext()) {
            ItemStack drop = it.next();
            if (EnchantApplyListener.hasUnique(drop, UniqueEnchant.SOULBOUND)) {
                event.getItemsToKeep().add(drop);
                it.remove();
                savedCount++;
            }
        }

        if (savedCount > 0) {
            player.sendMessage(Component.text("✦ [Soulbound] ไอเทมวิญญาณสถิต " + savedCount + " ชิ้นได้รับการคุ้มครองข้ามความตาย", NamedTextColor.LIGHT_PURPLE));
        }
    }

    // Virtual Protection & Feather Falling Bonus (Levels 5-10)
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerDamageProtection(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        // Virtual Feather Falling Bonus (Levels 5-10) on Boots
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            ItemStack boots = player.getInventory().getBoots();
            if (boots != null && !boots.getType().isAir()) {
                int lbFeather = plugin.relics().getLimitBreakLevel(boots, LimitBreakType.FEATHER_FALLING);
                if (lbFeather > 4) {
                    double reduction = Math.min(0.80, (lbFeather - 4) * 0.08);
                    event.setDamage(event.getDamage() * (1.0 - reduction));
                }
            }
        }

        int totalExtraProt = 0;
        if (player.getInventory().getArmorContents() != null) {
            for (ItemStack armor : player.getInventory().getArmorContents()) {
                if (armor != null && !armor.getType().isAir()) {
                    int lbProt = plugin.relics().getLimitBreakLevel(armor, LimitBreakType.PROTECTION);
                    if (lbProt > 4) {
                        totalExtraProt += (lbProt - 4);
                    }
                }
            }
        }
        if (totalExtraProt > 0) {
            double reduction = Math.min(0.50, totalExtraProt * 0.04);
            event.setDamage(event.getDamage() * (1.0 - reduction));
        }
    }

    // Virtual Looting Bonus (Levels 4-10: uncapped multiplier + rare drop booster)
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDeathLooting(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) return;
        ItemStack weapon = killer.getInventory().getItemInMainHand();
        int lbLoot = plugin.relics().getLimitBreakLevel(weapon, LimitBreakType.LOOTING);
        if (lbLoot > 3) {
            int extra = lbLoot - 3;
            // Guaranteed uncapped scaling per drop:
            // Level 4 (extra=1): +1..+2 items
            // Level 6 (extra=3): +2..+4 items
            // Level 8 (extra=5): +3..+6 items
            // Level 10 (extra=7): +4..+8 items
            int minBonus = Math.max(1, (extra + 1) / 2);
            int maxBonus = extra + 1;
            boolean bonusAdded = false;

            for (ItemStack drop : event.getDrops()) {
                if (drop != null && drop.getMaxStackSize() > 1) {
                    int add = minBonus + (int) (Math.random() * (maxBonus - minBonus + 1));
                    drop.setAmount(Math.min(drop.getMaxStackSize(), drop.getAmount() + add));
                    bonusAdded = true;
                }
            }

            // Rare Drops Booster (Wither Skeleton Skull, Shulker Shells)
            LivingEntity victim = event.getEntity();
            if (victim instanceof WitherSkeleton) {
                boolean hasSkull = event.getDrops().stream().anyMatch(d -> d != null && d.getType() == Material.WITHER_SKELETON_SKULL);
                if (!hasSkull) {
                    double extraSkullChance = extra * 0.02; // +2% per level above 3 (+14% at lvl 10)
                    if (Math.random() < extraSkullChance) {
                        event.getDrops().add(new ItemStack(Material.WITHER_SKELETON_SKULL, 1));
                        bonusAdded = true;
                    }
                }
            } else if (victim instanceof Shulker) {
                boolean hasShell = event.getDrops().stream().anyMatch(d -> d != null && d.getType() == Material.SHULKER_SHELL);
                if (!hasShell) {
                    double extraShellChance = Math.min(1.0, 0.20 + extra * 0.12);
                    if (Math.random() < extraShellChance) {
                        event.getDrops().add(new ItemStack(Material.SHULKER_SHELL, 1));
                        bonusAdded = true;
                    }
                }
            }

            if (bonusAdded) {
                killer.getWorld().playSound(killer.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.3f);
            }
        }
    }

    // Virtual Unbreaking Bonus (Levels 4-10)
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerItemDamage(PlayerItemDamageEvent event) {
        ItemStack item = event.getItem();
        if (item == null || !item.hasItemMeta()) return;
        int lbUnbreaking = plugin.relics().getLimitBreakLevel(item, LimitBreakType.UNBREAKING);
        if (lbUnbreaking > 3) {
            boolean isArmor = ItemCategory.ARMOR.matches(item.getType());
            double p3 = isArmor ? (0.6 + 0.4 / 4.0) : (1.0 / 4.0);
            double pN = isArmor ? (0.6 + 0.4 / (lbUnbreaking + 1.0)) : (1.0 / (lbUnbreaking + 1.0));
            double cancelChance = 1.0 - (pN / p3);
            if (java.util.concurrent.ThreadLocalRandom.current().nextDouble() < cancelChance) {
                event.setCancelled(true);
            }
        }
    }

    // Virtual Depth Strider Bonus (Levels 4-5) on Boots
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMoveDepthStrider(PlayerMoveEvent event) {
        if (event.getFrom().getBlockX() == event.getTo().getBlockX() &&
            event.getFrom().getBlockY() == event.getTo().getBlockY() &&
            event.getFrom().getBlockZ() == event.getTo().getBlockZ()) return;
        Player player = event.getPlayer();
        if (player.isInWater()) {
            ItemStack boots = player.getInventory().getBoots();
            if (boots != null && !boots.getType().isAir()) {
                int lbDepth = plugin.relics().getLimitBreakLevel(boots, LimitBreakType.DEPTH_STRIDER);
                if (lbDepth > 3) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.DOLPHINS_GRACE, 40, lbDepth - 4, true, false, false));
                }
            }
        }
    }

    private static final ThreadLocal<Boolean> IN_THORNS_REFLECTION = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private void handleVirtualThorns(EntityDamageByEntityEvent event, Player player) {
        if (player.getInventory().getArmorContents() == null) return;
        int maxThorns = 0;
        for (ItemStack armor : player.getInventory().getArmorContents()) {
            if (armor != null && !armor.getType().isAir()) {
                int lbThorns = plugin.relics().getLimitBreakLevel(armor, LimitBreakType.THORNS);
                if (lbThorns > maxThorns) {
                    maxThorns = lbThorns;
                }
            }
        }
        if (maxThorns > 3) {
            Entity damager = event.getDamager();
            LivingEntity attacker = null;
            if (damager instanceof LivingEntity living) {
                attacker = living;
            } else if (damager instanceof Projectile proj && proj.getShooter() instanceof LivingEntity livingShooter) {
                attacker = livingShooter;
            }

            if (attacker != null && !attacker.equals(player) && !(attacker instanceof ArmorStand) && canDamage(player, attacker)) {
                // Vanilla Thorns III is 45% chance for 1-4 dmg.
                // Limit Break Thorns (4..10) scales chance up to 95% and reflect damage up to 12.5
                double chance = Math.min(0.95, 0.45 + (maxThorns - 3) * 0.08);
                if (java.util.concurrent.ThreadLocalRandom.current().nextDouble() < chance) {
                    double reflectDmg = 2.0 + (maxThorns - 3) * 1.5;
                    IN_THORNS_REFLECTION.set(Boolean.TRUE);
                    try {
                        attacker.damage(reflectDmg, player);
                        attacker.getWorld().playSound(attacker.getLocation(), Sound.ENCHANT_THORNS_HIT, 1.0f, 1.2f);
                        attacker.getWorld().spawnParticle(Particle.CRIT, attacker.getLocation().add(0, 1, 0), 10, 0.2, 0.3, 0.2, 0.1);
                    } finally {
                        IN_THORNS_REFLECTION.set(Boolean.FALSE);
                    }
                }
            }
        }
    }

    @SuppressWarnings({"deprecation", "removal"})
    private boolean isUndead(LivingEntity entity) {
        if (entity == null) return false;
        try {
            return entity.getCategory() == EntityCategory.UNDEAD;
        } catch (Throwable ignored) {
            return entity instanceof Zombie || entity instanceof Skeleton || entity instanceof Wither
                || entity instanceof Phantom;
        }
    }
}
