package com.example.voidscape.item;

import com.example.voidscape.VoidscapePlugin;
import com.example.voidscape.enchant.UniqueAbilityListener;
import com.example.voidscape.item.AdventurerWeaponService.AdventurerWeapon;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The Rare Adventurer weapons' own powers (Bramblepike has none). Bonus hits go through
 * {@link UniqueAbilityListener#abilityDamage} so they keep the player's kill credit without firing
 * Limit Break / Unique on-hit powers, and are skipped here so they never chain into more procs
 * (Voidreel's own chain is explicit). Melee procs need a full swing; spear "charge" means the
 * right-click kinetic hit, mace "smash" means vanilla's falling MACE_SMASH hit.
 */
final class AdventurerWeaponAbilities implements Listener {
    /** Lore lines per weapon, shown under the base stats. */
    static final Map<AdventurerWeapon, List<String>> LORE = new EnumMap<>(AdventurerWeapon.class);
    static {
        LORE.put(AdventurerWeapon.EMBERFANG, List.of("ตีแล้วศัตรูติดไฟ", "ตีศัตรูที่ติดไฟอยู่ แรงขึ้น 2 เท่า"));
        LORE.put(AdventurerWeapon.STORMPIERCER, List.of("ตีมีโอกาส 25% เรียกฟ้าผ่าใส่ศัตรู", "แทงพุ่ง (คลิกขวา) โดนศัตรู: Strength I 10 วินาที"));
        LORE.put(AdventurerWeapon.IRONBLOOM, List.of("ทุบตอนตกลงมา (Mace Smash): หัวใจทอง 3 ดวง 5 วินาที (ไม่ซ้อน)"));
        LORE.put(AdventurerWeapon.FROSTVEIL, List.of("ตีแล้วศัตรูช้าลง", "โอกาส 25%: น้ำแข็งกัด + ขุดช้า 4 วินาที"));
        LORE.put(AdventurerWeapon.SUNFLARE, List.of("ถือแล้วกันไฟ", "ทุบตอนตกลงมา: ศัตรูติดไฟและช้าลง"));
        LORE.put(AdventurerWeapon.MOONRIFT, List.of("โอกาส 30%: ฟันซ้ำอีกครั้ง แรงกว่าเดิม 50%", "(ครั้งที่ซ้ำไม่เบิ้ลต่อ)"));
        LORE.put(AdventurerWeapon.TIDEHOOK, List.of("ชักออกมาถือ: Speed IV 10 วินาที (คูลดาวน์ 30 วินาที)", "ถือแล้วว่ายน้ำไว"));
        LORE.put(AdventurerWeapon.ASHCLEAVER, List.of("ตีครบ 3 ครั้ง ครั้งถัดไปศัตรูติดไฟ", "ตีศัตรูที่ติดไฟ: หัวใจทอง +ครึ่งดวง (สูงสุด 5 ดวง ขณะถือ)"));
        LORE.put(AdventurerWeapon.GLIMMERMAUL, List.of("ทุกการตีเต็มแรงเป็นการทุบแบบ Mace ไม่ต้องกระโดด", "ดาเมจทุบครึ่งหนึ่งของ Mace · ตีอันเดดเต็มแรง"));
        LORE.put(AdventurerWeapon.THORNWHISPER, List.of("ยิงโดน: พิษร้าย 10 ดาเมจใน 4 วินาที (คูลดาวน์ 30 วินาที)", "มี Infinity และ Mending ในตัว"));
        LORE.put(AdventurerWeapon.RUNELOCK, List.of("ยิงเข้า 25% ของเลือดสูงสุดทันที", "บอส: 10% เฉพาะนัดแรก · ทะลุเกราะแบบ Breach"));
        LORE.put(AdventurerWeapon.MISTCALLER, List.of("แทงพุ่ง (คลิกขวา) โดน: ล็อกเป้าลำแสงแบบ Guardian", "ตีปกติ: ฟื้นเลือด 25% ของดาเมจที่ทำ"));
        LORE.put(AdventurerWeapon.SILVERSHIV, List.of("ตีอันเดดแรงขึ้น 30%", "ตีจากด้านหลัง แรงขึ้น 2 เท่า · ถือแล้ว Speed I"));
        LORE.put(AdventurerWeapon.STONEHEART, List.of("ทุบตอนตกลงมา: อมตะ 1 วินาที", "จากนั้น 5 วินาที รับดาเมจ 2 เท่า"));
        LORE.put(AdventurerWeapon.NIGHTBLOOM, List.of("ตีมีโอกาส 10%: Wither I 10 วินาที", "แทงพุ่ง (คลิกขวา) โดน: ดูดเลือดเท่าดาเมจที่ทำ"));
        LORE.put(AdventurerWeapon.IRONWAVE, List.of("ตีมีโอกาส 30%: คลื่นรอบตัว 50% ของพลังโจมตี", "แทงพุ่ง (คลิกขวา) โดน: Regeneration I 10 วินาที (คูลดาวน์ 40 วินาที)"));
        LORE.put(AdventurerWeapon.STARFANG, List.of("โอกาส 60%: ศัตรูโดนดาเมจซ้ำอีก 3 ครั้ง", "โอกาส 10%: ศัตรูชะงัก 3 วินาที"));
        LORE.put(AdventurerWeapon.ROOTSPIRE, List.of("ถือแล้ว Regeneration II ตลอด", "พันธมิตรรอบตัว 8 บล็อก: Regeneration I + Absorption"));
        LORE.put(AdventurerWeapon.VOIDREEL, List.of("ทุกการตีเป็นคริติคอล", "โอกาส 50%: ตีซ้ำ และซ้ำต่อได้อีกทีละ 50%"));
    }

    private static final double STORM_CHANCE = .25, STORM_DAMAGE = 6.0;
    private static final double FROST_CHANCE = .25;
    private static final double MOON_CHANCE = .30, MOON_ECHO = 1.5;
    private static final double GLIMMER_SMASH = 6.0, GLIMMER_SMASH_UNDEAD = 12.0; // half / full of a 3-block mace smash
    private static final double THORN_POISON = 10.0;
    private static final double RUNE_SHARE = .25, RUNE_BOSS_SHARE = .10, RUNE_ARMOR_KEPT = .4; // Breach IV keeps 40% of armor
    private static final double MIST_HEAL = .25, MIST_BEAM = 6.0;
    private static final double SILVER_UNDEAD = 1.3, SILVER_BACKSTAB = 2.0;
    private static final double NIGHT_WITHER_CHANCE = .10;
    private static final double WAVE_CHANCE = .30, WAVE_SHARE = .5, WAVE_RADIUS = 4.0;
    private static final double STAR_CHANCE = .60, STAR_STUN_CHANCE = .10;
    private static final int STAR_REPEATS = 3;
    private static final double VOID_CHAIN = .50;
    private static final int VOID_CHAIN_CAP = 8;
    private static final double ASH_HEARTS_MAX = 10.0;
    private static final long TIDE_COOLDOWN = 30_000, THORN_COOLDOWN = 30_000, WAVE_REGEN_COOLDOWN = 40_000, MIST_BEAM_COOLDOWN = 4_000;
    private static final long STONE_IMMUNE = 1_000, STONE_VULNERABLE = 5_000;

    private final VoidscapePlugin plugin;
    private final AdventurerWeaponService weapons;
    private final NamespacedKey projectileKey, ashModifier;
    private final Map<UUID, Float> swingStrength = new HashMap<>();
    private final Map<UUID, AdventurerWeapon> held = new HashMap<>();
    private final Map<String, Long> cooldowns = new HashMap<>();
    private final Map<UUID, Integer> ashHits = new HashMap<>();
    private final Map<UUID, Double> ashHearts = new HashMap<>();
    private final Map<UUID, long[]> stoneheart = new HashMap<>(); // {immune until, vulnerable until}
    private final Map<UUID, Set<UUID>> runeBossHits = new HashMap<>();
    private final Set<UUID> frozen = new HashSet<>(), stunned = new HashSet<>();
    private boolean generating;

    AdventurerWeaponAbilities(VoidscapePlugin plugin, AdventurerWeaponService weapons, NamespacedKey projectileKey) {
        this.plugin = plugin;
        this.weapons = weapons;
        this.projectileKey = projectileKey;
        this.ashModifier = new NamespacedKey("voidscape", "ashcleaver_hearts");
        Bukkit.getScheduler().runTaskTimer(plugin, this::heldTick, 5L, 5L);
    }

    // -- helpers ---------------------------------------------------------------

    private static boolean roll(double chance) {
        return ThreadLocalRandom.current().nextDouble() < chance;
    }

    private boolean ready(UUID player, String what, long cooldown) {
        long now = System.currentTimeMillis();
        String key = player + what;
        if (cooldowns.getOrDefault(key, 0L) > now) return false;
        cooldowns.put(key, now + cooldown);
        return true;
    }

    /** Vanilla bosses, world boss parts, Arkhaia and sanctuary encounters (as the pet system tells them apart). */
    private boolean boss(Entity entity) {
        var tags = entity.getScoreboardTags();
        var data = entity.getPersistentDataContainer();
        return entity instanceof Boss || tags.contains("boss") || tags.contains("arkhaia")
                || data.has(plugin.key("sanctuary_boss"), PersistentDataType.BYTE)
                || data.has(plugin.key("world_boss_entity"), PersistentDataType.BYTE);
    }

    private static boolean undead(LivingEntity entity) {
        return Tag.ENTITY_TYPES_UNDEAD.isTagged(entity.getType());
    }

    private static double maxHealth(LivingEntity entity) {
        AttributeInstance max = entity.getAttribute(Attribute.MAX_HEALTH);
        return max == null ? 20.0 : max.getValue();
    }

    /** Hostile targets an area power may hit; never players, villagers, pets or the attacker. */
    private static boolean foe(Entity entity, Player attacker) {
        return entity instanceof LivingEntity living && entity != attacker && !living.isDead() && !entity.isInvulnerable()
                && entity instanceof Enemy && !(entity instanceof Tameable tame && tame.isTamed());
    }

    private static boolean ally(Entity entity, Player holder) {
        if (entity == holder || !(entity instanceof LivingEntity living) || living.isDead()) return false;
        if (entity instanceof Player other) return other.getGameMode() != GameMode.SPECTATOR;
        return entity instanceof AbstractVillager || entity instanceof Tameable tame && tame.isTamed();
    }

    /** A bonus hit credited to the player that does not trigger any on-hit power. */
    private void bonusHit(LivingEntity target, double amount, Player source) {
        if (!target.isValid() || target.isDead() || !source.isOnline() || amount <= 0) return;
        boolean previous = generating;
        generating = true;
        try {
            target.setNoDamageTicks(0);
            UniqueAbilityListener.abilityDamage(target, amount, source);
        } finally {
            generating = previous;
        }
    }

    private void later(long ticks, Runnable task) {
        Bukkit.getScheduler().runTaskLater(plugin, task, ticks);
    }

    private static void effect(LivingEntity entity, PotionEffectType type, int ticks, int amplifier) {
        entity.addPotionEffect(new PotionEffect(type, ticks, amplifier, false, true, true));
    }

    /** Keeps a held-weapon effect running without restarting it every tick (so Regeneration still heals). */
    private static void sustain(LivingEntity entity, PotionEffectType type, int amplifier) {
        PotionEffect current = entity.getPotionEffect(type);
        if (current != null && (current.getAmplifier() > amplifier || current.getAmplifier() == amplifier && current.getDuration() > 20)) return;
        entity.addPotionEffect(new PotionEffect(type, 70, amplifier, true, false, true));
    }

    /** Golden hearts that do not stack: refills to {@code amount} and lasts {@code ticks}; a stronger shield is kept. */
    private static void goldenHearts(Player player, double amount, int ticks) {
        int amplifier = (int) Math.ceil(amount / 4.0) - 1;
        PotionEffect current = player.getPotionEffect(PotionEffectType.ABSORPTION);
        if (current != null && current.getAmplifier() > amplifier) return;
        double cap = 4.0 * (amplifier + 1), before = Math.min(player.getAbsorptionAmount(), cap);
        player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, ticks, amplifier, false, true, true));
        player.setAbsorptionAmount(Math.max(before, amount));
    }

    private static void burst(Entity at, Particle particle, int count) {
        at.getWorld().spawnParticle(particle, at.getLocation().add(0, at.getHeight() * .55, 0), count, .3, .35, .3, .02);
    }

    // -- melee -----------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSwing(PrePlayerAttackEntityEvent event) {
        swingStrength.put(event.getPlayer().getUniqueId(), event.getPlayer().getCooledAttackStrength(0f));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMelee(EntityDamageByEntityEvent event) {
        if (generating || !(event.getDamager() instanceof Player player) || !(event.getEntity() instanceof LivingEntity target)) return;
        if (event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK || target instanceof ArmorStand) return;
        AdventurerWeapon weapon = weapons.identify(player.getInventory().getItemInMainHand());
        if (weapon == null || weapon.isRanged()) return;
        UUID id = player.getUniqueId();
        boolean charge = player.isHandRaised() && player.getActiveItemHand() == EquipmentSlot.HAND && player.getActiveItem().getType() == weapon.baseMaterial();
        Float strength = swingStrength.remove(id);
        boolean full = charge || strength == null || strength >= .9f;
        boolean smash = event.getDamageSource().getDamageType() == DamageType.MACE_SMASH;
        boolean burning = target.getFireTicks() > 0;
        switch (weapon) {
            case EMBERFANG -> {
                if (burning) {
                    event.setDamage(event.getDamage() * 2);
                    burst(target, Particle.FLAME, 14);
                }
                target.setFireTicks(Math.max(target.getFireTicks(), 100));
            }
            case STORMPIERCER -> {
                if (charge && ready(id, "storm-strength", 1_000)) effect(player, PotionEffectType.STRENGTH, 200, 0);
                if (full && roll(STORM_CHANCE) && ready(id, "storm-bolt", 1_000)) {
                    target.getWorld().strikeLightningEffect(target.getLocation());
                    later(2, () -> bonusHit(target, STORM_DAMAGE, player));
                }
            }
            case IRONBLOOM -> {
                if (smash) {
                    goldenHearts(player, 6.0, 100);
                    burst(player, Particle.HAPPY_VILLAGER, 12);
                }
            }
            case FROSTVEIL -> {
                effect(target, PotionEffectType.SLOWNESS, 60, 1);
                if (full && roll(FROST_CHANCE)) frostbite(target);
            }
            case SUNFLARE -> {
                if (smash) {
                    target.setFireTicks(Math.max(target.getFireTicks(), 100));
                    effect(target, PotionEffectType.SLOWNESS, 80, 1);
                    burst(target, Particle.FLAME, 20);
                }
            }
            case MOONRIFT -> {
                if (full && roll(MOON_CHANCE)) {
                    double echo = event.getDamage() * MOON_ECHO;
                    later(6, () -> {
                        bonusHit(target, echo, player);
                        burst(target, Particle.REVERSE_PORTAL, 18);
                        target.getWorld().playSound(target.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, .8f, .6f);
                    });
                }
            }
            case ASHCLEAVER -> {
                if (burning) addAshHeart(player);
                if (!full) break;
                int hits = ashHits.merge(id, 1, Integer::sum);
                if (hits > 3) {
                    target.setFireTicks(Math.max(target.getFireTicks(), 100));
                    burst(target, Particle.FLAME, 16);
                    ashHits.put(id, 0);
                }
            }
            case GLIMMERMAUL -> {
                if (full && !smash && player.getFallDistance() <= 1.5f) groundSmash(event, player, target);
            }
            case MISTCALLER -> {
                if (charge) {
                    if (ready(id, "mist-beam", MIST_BEAM_COOLDOWN)) guardianBeam(player, target);
                } else if (full) {
                    double heal = event.getFinalDamage() * MIST_HEAL;
                    later(1, () -> heal(player, heal));
                }
            }
            case SILVERSHIV -> {
                double damage = event.getDamage();
                if (undead(target)) damage *= SILVER_UNDEAD;
                Vector facing = target.getLocation().getDirection().setY(0);
                Vector toAttacker = player.getLocation().toVector().subtract(target.getLocation().toVector()).setY(0);
                if (facing.lengthSquared() > 1e-6 && toAttacker.lengthSquared() > 1e-6 && facing.normalize().dot(toAttacker.normalize()) < -.5) {
                    damage *= SILVER_BACKSTAB;
                    burst(target, Particle.CRIT, 16);
                    player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, .9f, 1.4f);
                }
                event.setDamage(damage);
            }
            case STONEHEART -> {
                long now = System.currentTimeMillis();
                long[] state = stoneheart.get(id);
                if (smash && (state == null || now >= state[1])) {
                    stoneheart.put(id, new long[]{now + STONE_IMMUNE, now + STONE_IMMUNE + STONE_VULNERABLE});
                    player.getWorld().spawnParticle(Particle.BLOCK, player.getLocation().add(0, 1, 0), 30, .4, .6, .4, Material.STONE.createBlockData());
                    player.getWorld().playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, .6f, .7f);
                    player.sendActionBar(Component.text("✦ หัวใจศิลา: อมตะ 1 วินาที แล้วรับดาเมจ 2 เท่า 5 วินาที", NamedTextColor.GOLD));
                }
            }
            case NIGHTBLOOM -> {
                if (roll(NIGHT_WITHER_CHANCE)) effect(target, PotionEffectType.WITHER, 200, 0);
                if (charge) {
                    double heal = event.getFinalDamage();
                    later(1, () -> heal(player, heal));
                    burst(player, Particle.DAMAGE_INDICATOR, 6);
                }
            }
            case IRONWAVE -> {
                if (charge && ready(id, "wave-regen", WAVE_REGEN_COOLDOWN)) effect(player, PotionEffectType.REGENERATION, 200, 0);
                if (!charge && full && roll(WAVE_CHANCE)) shockwave(player, target, weapon.attackDamage() * WAVE_SHARE);
            }
            case STARFANG -> {
                if (!full) break;
                if (roll(STAR_CHANCE)) {
                    double repeat = event.getDamage();
                    for (int i = 1; i <= STAR_REPEATS; i++) {
                        later(4L * i, () -> {
                            bonusHit(target, repeat, player);
                            burst(target, Particle.END_ROD, 8);
                        });
                    }
                }
                if (roll(STAR_STUN_CHANCE)) stun(target);
            }
            case VOIDREEL -> {
                if (!event.isCritical()) {
                    event.setDamage(event.getDamage() * 1.5);
                    burst(target, Particle.CRIT, 12);
                    target.getWorld().playSound(target.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 1f);
                }
                if (full) voidChain(player, target, event.getDamage(), 1);
            }
            default -> { }
        }
    }

    private void heal(Player player, double amount) {
        if (!player.isOnline() || player.isDead() || amount <= 0) return;
        player.setHealth(Math.min(maxHealth(player), player.getHealth() + amount));
    }

    private void frostbite(LivingEntity target) {
        effect(target, PotionEffectType.MINING_FATIGUE, 80, 1);
        target.setFreezeTicks(target.getMaxFreezeTicks() + 40);
        target.lockFreezeTicks(true);
        frozen.add(target.getUniqueId());
        burst(target, Particle.SNOWFLAKE, 20);
        later(80, () -> {
            if (frozen.remove(target.getUniqueId()) && target.isValid()) target.lockFreezeTicks(false);
        });
    }

    private void addAshHeart(Player player) {
        UUID id = player.getUniqueId();
        double stacks = Math.min(ashHearts.getOrDefault(id, 0.0), player.getAbsorptionAmount());
        if (stacks >= ASH_HEARTS_MAX) return;
        ashHearts.put(id, stacks + 1.0);
        player.setAbsorptionAmount(player.getAbsorptionAmount() + 1.0);
    }

    private void groundSmash(EntityDamageByEntityEvent event, Player player, LivingEntity target) {
        event.setDamage(event.getDamage() + (undead(target) ? GLIMMER_SMASH_UNDEAD : GLIMMER_SMASH));
        Location at = target.getLocation();
        for (Entity near : target.getNearbyEntities(3.5, 2.0, 3.5)) {
            if (!foe(near, player)) continue;
            Vector push = near.getLocation().toVector().subtract(at.toVector()).setY(0);
            push = push.lengthSquared() < 1e-6 ? new Vector(0, 0, 0) : push.normalize().multiply(.8);
            near.setVelocity(near.getVelocity().add(push.setY(.45)));
        }
        at.getWorld().spawnParticle(Particle.DUST_PILLAR, at, 40, .8, .1, .8, Material.GOLD_BLOCK.createBlockData());
        at.getWorld().playSound(at, Sound.ITEM_MACE_SMASH_GROUND, 1f, 1.1f);
    }

    private void guardianBeam(Player player, LivingEntity target) {
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_GUARDIAN_ATTACK, 1f, 1f);
        new org.bukkit.scheduler.BukkitRunnable() {
            int tick;
            @Override public void run() {
                if (!player.isOnline() || !target.isValid() || target.isDead() || player.getWorld() != target.getWorld()
                        || player.getLocation().distanceSquared(target.getLocation()) > 16 * 16 || !player.hasLineOfSight(target)) {
                    cancel();
                    return;
                }
                double progress = tick / 30.0;
                Color colour = Color.fromRGB((int) (64 + 191 * progress), (int) (96 + 159 * progress), (int) (255 - 120 * progress));
                Location from = player.getEyeLocation().subtract(0, .25, 0);
                Vector line = target.getLocation().add(0, target.getHeight() * .55, 0).toVector().subtract(from.toVector());
                int points = (int) Math.max(4, line.length() * 3);
                for (int i = 0; i <= points; i++) {
                    Location point = from.clone().add(line.clone().multiply(i / (double) points));
                    point.getWorld().spawnParticle(Particle.DUST, point, 1, 0, 0, 0, 0, new Particle.DustOptions(colour, 1.1f));
                }
                if (tick % 6 == 0) target.getWorld().spawnParticle(Particle.BUBBLE_POP, target.getLocation().add(0, 1, 0), 6, .3, .3, .3, .02);
                if ((tick += 2) >= 30) {
                    bonusHit(target, MIST_BEAM, player);
                    target.getWorld().playSound(target.getLocation(), Sound.ENTITY_GUARDIAN_HURT, 1f, 1.2f);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    private void shockwave(Player player, LivingEntity target, double damage) {
        Location at = player.getLocation();
        for (int i = 0; i < 24; i++) {
            double angle = Math.PI * 2 * i / 24;
            at.getWorld().spawnParticle(Particle.SPLASH, at.clone().add(Math.cos(angle) * WAVE_RADIUS * .8, .3, Math.sin(angle) * WAVE_RADIUS * .8), 4, .1, .05, .1, 0);
        }
        at.getWorld().playSound(at, Sound.ENTITY_PLAYER_SPLASH_HIGH_SPEED, .9f, .8f);
        List<LivingEntity> hit = new ArrayList<>();
        for (Entity near : player.getNearbyEntities(WAVE_RADIUS, 2.0, WAVE_RADIUS)) {
            if (near != target && foe(near, player)) hit.add((LivingEntity) near);
        }
        later(1, () -> hit.forEach(entity -> bonusHit(entity, damage, player)));
    }

    private void stun(LivingEntity target) {
        if (boss(target)) return;
        target.getWorld().spawnParticle(Particle.FLASH, target.getLocation().add(0, target.getHeight() + .3, 0), 1, 0, 0, 0, 0, Color.WHITE);
        if (target instanceof Mob mob) {
            if (!mob.isAware() || !stunned.add(mob.getUniqueId())) return;
            mob.setAware(false);
            later(60, () -> {
                if (stunned.remove(mob.getUniqueId()) && mob.isValid()) mob.setAware(true);
            });
        } else {
            effect(target, PotionEffectType.SLOWNESS, 60, 9);
            effect(target, PotionEffectType.WEAKNESS, 60, 1);
        }
    }

    private void voidChain(Player player, LivingEntity target, double damage, int depth) {
        if (depth > VOID_CHAIN_CAP || !roll(VOID_CHAIN)) return;
        later(4, () -> {
            if (!target.isValid() || target.isDead()) return;
            bonusHit(target, damage, player);
            burst(target, Particle.PORTAL, 14);
            voidChain(player, target, damage, depth + 1);
        });
    }

    // -- ranged ----------------------------------------------------------------

    /** Called by the service for adventurer projectiles after its +25% bonus. */
    void onProjectileHit(EntityDamageByEntityEvent event, AdventurerWeapon weapon, Projectile projectile) {
        if (!(event.getEntity() instanceof LivingEntity target) || !(projectile.getShooter() instanceof Player player)) return;
        if (weapon == AdventurerWeapon.THORNWHISPER) {
            if (!ready(player.getUniqueId(), "thorn-venom", THORN_COOLDOWN)) return;
            // Exactly 10 venom damage: a vanilla Poison effect would add its own ticks on top.
            Color venom = Color.fromRGB(0x4E9331);
            for (int second = 1; second <= 4; second++) {
                later(20L * second, () -> {
                    bonusHit(target, THORN_POISON / 4, player);
                    target.getWorld().spawnParticle(Particle.ENTITY_EFFECT, target.getLocation().add(0, 1, 0), 12, .3, .45, .3, 1, venom);
                });
            }
            player.sendActionBar(Component.text("☠ พิษพงหนาม! (คูลดาวน์ 30 วินาที)", NamedTextColor.GREEN));
        } else if (weapon == AdventurerWeapon.RUNELOCK) {
            double bonus;
            if (boss(target)) {
                boolean first = runeBossHits.computeIfAbsent(target.getUniqueId(), key -> new HashSet<>()).add(player.getUniqueId());
                bonus = first ? maxHealth(target) * RUNE_BOSS_SHARE : 0;
            } else {
                bonus = maxHealth(target) * RUNE_SHARE;
            }
            if (bonus > 0) {
                event.setDamage(event.getDamage() + bonus);
                burst(target, Particle.ENCHANTED_HIT, 18);
            }
            if (event.isApplicable(EntityDamageEvent.DamageModifier.ARMOR) && event.getDamage(EntityDamageEvent.DamageModifier.ARMOR) < 0) {
                event.setDamage(EntityDamageEvent.DamageModifier.ARMOR, event.getDamage(EntityDamageEvent.DamageModifier.ARMOR) * RUNE_ARMOR_KEPT);
            }
        }
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        runeBossHits.remove(event.getEntity().getUniqueId());
    }

    // -- the holder ------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHolderHurt(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        long[] state = stoneheart.get(player.getUniqueId());
        if (state == null) return;
        long now = System.currentTimeMillis();
        if (now < state[0]) {
            event.setCancelled(true);
        } else if (now < state[1]) {
            event.setDamage(event.getDamage() * 2);
        } else {
            stoneheart.remove(player.getUniqueId());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        leaveAshcleaver(player);
        UUID id = player.getUniqueId();
        held.remove(id);
        swingStrength.remove(id);
        ashHits.remove(id);
    }

    private void heldTick() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.isDead() || player.getGameMode() == GameMode.SPECTATOR) continue;
            ItemStack hand = player.getInventory().getItemInMainHand();
            AdventurerWeapon weapon = weapons.identify(hand);
            if (weapon != null && weapons.outdated(hand)) {
                weapons.applyWeaponMeta(hand, weapon);
                player.getInventory().setItemInMainHand(hand);
            }
            AdventurerWeapon before = held.put(player.getUniqueId(), weapon);
            if (before == AdventurerWeapon.ASHCLEAVER && weapon != AdventurerWeapon.ASHCLEAVER) leaveAshcleaver(player);
            if (weapon == null) continue;
            switch (weapon) {
                case TIDEHOOK -> {
                    if (before != AdventurerWeapon.TIDEHOOK && ready(player.getUniqueId(), "tide-speed", TIDE_COOLDOWN)) {
                        effect(player, PotionEffectType.SPEED, 200, 3);
                        player.getWorld().playSound(player.getLocation(), Sound.ITEM_TRIDENT_RIPTIDE_1, .7f, 1.3f);
                    }
                    sustain(player, PotionEffectType.DOLPHINS_GRACE, 0);
                }
                case SUNFLARE -> sustain(player, PotionEffectType.FIRE_RESISTANCE, 0);
                case SILVERSHIV -> sustain(player, PotionEffectType.SPEED, 0);
                case ASHCLEAVER -> {
                    AttributeInstance cap = player.getAttribute(Attribute.MAX_ABSORPTION);
                    if (cap != null && cap.getModifier(ashModifier) == null) {
                        cap.addTransientModifier(new AttributeModifier(ashModifier, ASH_HEARTS_MAX, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.ANY));
                    }
                }
                case ROOTSPIRE -> {
                    sustain(player, PotionEffectType.REGENERATION, 1);
                    for (Entity near : player.getNearbyEntities(8, 4, 8)) {
                        if (!ally(near, player)) continue;
                        sustain((LivingEntity) near, PotionEffectType.REGENERATION, 0);
                        sustain((LivingEntity) near, PotionEffectType.ABSORPTION, 0);
                    }
                }
                default -> { }
            }
        }
    }

    private void leaveAshcleaver(Player player) {
        Double stacks = ashHearts.remove(player.getUniqueId());
        if (stacks != null) player.setAbsorptionAmount(Math.max(0, player.getAbsorptionAmount() - stacks));
        AttributeInstance cap = player.getAttribute(Attribute.MAX_ABSORPTION);
        if (cap != null && cap.getModifier(ashModifier) != null) cap.removeModifier(ashModifier);
    }

    @EventHandler
    public void onDisable(org.bukkit.event.server.PluginDisableEvent event) {
        if (event.getPlugin() == plugin) shutdown();
    }

    /** Restores mobs and freeze locks this class changed. */
    private void shutdown() {
        for (World world : Bukkit.getWorlds()) {
            for (LivingEntity entity : world.getLivingEntities()) {
                if (stunned.remove(entity.getUniqueId()) && entity instanceof Mob mob) mob.setAware(true);
                if (frozen.remove(entity.getUniqueId())) entity.lockFreezeTicks(false);
            }
        }
        Bukkit.getOnlinePlayers().forEach(this::leaveAshcleaver);
    }
}
