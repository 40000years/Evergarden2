package com.example.sevensins;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.boss.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;
import java.util.*;

/** A bounded encounter driven by one shared server task; no block edits or delayed attacks. */
public final class WrathBoss {
    public enum State { ARRIVAL, CHASE, WINDUP, CHARGE, RECOVERY, STAGGER, TRANSITION, REMOVED }
    public enum Attack { SWEEP, SLAM, CHARGE, RING, BLADES }
    private final SevenSinsPlugin plugin;
    private final Husk base;
    private final Location home;
    private final WrathModel model;
    private final WrathBlades blades;
    private final BossBar bar;
    private final double maxHealth, nativeMaxHealth, radius, damageMultiplier, incomingMultiplier;
    private final int idleLimit;
    private final Set<UUID> hit = new HashSet<>();
    private State state = State.ARRIVAL;
    private Attack attack = Attack.SWEEP;
    private Location anchor;
    private Vector facing = new Vector(0, 0, 1);
    private int ticks, remaining = 80, total = 80, cooldown = 40, idle, attackIndex, rage;
    private boolean enraged;
    private UUID pendingHit;
    private boolean hitAccepted;
    private double acceptedDamage;

    WrathBoss(SevenSinsPlugin plugin, Location home) {
        this.plugin = plugin; this.home = home.clone(); anchor = home.clone();
        maxHealth = bounded(plugin.getConfig().getDouble("wrath.health", 1800), 40, 100000, 1800);
        // Minecraft clamps living-entity health to 1024. Scale damage while showing encounter HP.
        nativeMaxHealth = Math.min(maxHealth, 1024);
        incomingMultiplier = bounded(plugin.getConfig().getDouble("wrath.incoming-damage-multiplier", 0.7), 0.1, 2, 0.7);
        radius = bounded(plugin.getConfig().getDouble("wrath.arena-radius", 28), 12, 64, 28);
        damageMultiplier = bounded(plugin.getConfig().getDouble("wrath.damage-multiplier", 1), 0.1, 10, 1);
        idleLimit = Math.max(20, Math.min(3600, plugin.getConfig().getInt("wrath.idle-despawn-seconds", 120))) * 20;
        base = home.getWorld().spawn(home, Husk.class, e -> {
            e.setPersistent(false); e.setRemoveWhenFarAway(false); e.setSilent(true); e.setInvisible(true);
            e.setCanPickupItems(false); e.setAdult(); e.setConversionTime(-1); e.setAI(false);
            e.getAttribute(Attribute.MAX_HEALTH).setBaseValue(nativeMaxHealth); e.setHealth(nativeMaxHealth);
            e.getAttribute(Attribute.SCALE).setBaseValue(1.65);
            e.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(0.25);
            e.getAttribute(Attribute.KNOCKBACK_RESISTANCE).setBaseValue(0.9);
            e.getAttribute(Attribute.FOLLOW_RANGE).setBaseValue(radius);
            e.getAttribute(Attribute.ATTACK_DAMAGE).setBaseValue(0);
            e.customName(Component.text("WRATH · THE ASHEN EXECUTIONER", NamedTextColor.DARK_RED));
            e.setCustomNameVisible(false);
            e.getPersistentDataContainer().set(plugin.entityKey(), PersistentDataType.STRING, "wrath");
        });
        if (!base.isValid()) throw new IllegalStateException("Boss spawn was rejected by the server.");
        WrathModel created;
        try { created = new WrathModel(plugin, home); }
        catch (RuntimeException error) { base.remove(); throw error; }
        model = created;
        blades = new WrathBlades(plugin, this);
        bar = Bukkit.createBossBar("WRATH · THE ASHEN EXECUTIONER", BarColor.RED, BarStyle.SEGMENTED_10);
        model.animate(home, 0, state, 0, false, false);
        sound(Sound.ENTITY_WITHER_SPAWN, 1.4f, 0.5f);
    }

    private static double bounded(double value, double min, double max, double fallback) {
        return Double.isFinite(value) ? Math.max(min, Math.min(max, value)) : fallback;
    }
    public Husk entity() { return base; }
    public double health() { return base.getHealth() * maxHealth / nativeMaxHealth; }
    public State state() { return state; }
    public boolean enraged() { return enraged; }
    public int rage() { return rage; }
    public Location home() { return home.clone(); }
    List<Entity> visuals() { return model.entities(); }
    void refresh(Player player) { model.refresh(player); blades.refresh(player); }

    public void tick() {
        if (state == State.REMOVED) return;
        if (!base.isValid() || base.isDead() || !base.getWorld().isChunkLoaded(base.getLocation().getBlockX() >> 4, base.getLocation().getBlockZ() >> 4)) {
            remove(); return;
        }
        ticks += 2;
        blades.tick();
        List<Player> players = participants();
        Set<UUID> nearby = new HashSet<>();
        for (Player p : base.getWorld().getPlayers()) if (p.getLocation().distanceSquared(home) <= (radius + 12) * (radius + 12)) {
            nearby.add(p.getUniqueId()); if (!bar.getPlayers().contains(p)) bar.addPlayer(p);
        }
        for (Player p : List.copyOf(bar.getPlayers())) if (!nearby.contains(p.getUniqueId())) bar.removePlayer(p);
        bar.setProgress(Math.max(0, Math.min(1, base.getHealth() / nativeMaxHealth)));
        bar.setTitle("WRATH · " + (enraged ? "UNBOUND" : "THE ASHEN EXECUTIONER") + "  |  RAGE " + rage + "%");
        if (ticks % 20 == 0) rage = Math.max(0, rage - (state == State.STAGGER ? 8 : 2));
        if (players.isEmpty()) idle += 2; else idle = 0;
        if (idle >= idleLimit) { remove(); return; }
        if (base.getLocation().distanceSquared(home) > radius * radius || base.getLocation().getY() < home.getY() - 6) {
            reset(); return;
        }
        if (players.isEmpty() && state != State.ARRIVAL && state != State.TRANSITION) {
            blades.clear();
            change(State.CHASE, 0); halt(); cooldown = 40;
            if (idle == 100) reset();
        }
        if (!enraged && base.getHealth() <= nativeMaxHealth * 0.5 + 0.0001) {
            enraged = true; rage = 0; change(State.TRANSITION, 60); halt();
            blades.clear();
            announce("เกราะแตกแล้ว! WRATH เข้าสู่เฟสคลั่ง", NamedTextColor.RED);
            sound(Sound.ENTITY_WITHER_DEATH, 1.5f, 0.6f);
        }
        Player target = players.stream().min(Comparator.comparingDouble(p -> p.getLocation().distanceSquared(base.getLocation()))).orElse(null);
        switch (state) {
            case ARRIVAL, TRANSITION -> {
                halt(); ring(base.getLocation(), 2.5, Color.fromRGB(255, 85, 25));
                if ((remaining -= 2) <= 0) { change(State.CHASE, 0); cooldown = 30; }
            }
            case CHASE -> {
                if (target != null) {
                    if (ticks % 10 == 0) base.getPathfinder().moveTo(target.getLocation(), enraged ? 1.3 : 1.0);
                    face(target.getLocation());
                    cooldown -= 2;
                    double distance = target.getLocation().distanceSquared(base.getLocation());
                    if (cooldown <= 0) {
                        Player distant = players.stream().filter(p -> p.getLocation().distanceSquared(base.getLocation()) > 64)
                                .max(Comparator.comparingDouble(p -> p.getLocation().distanceSquared(base.getLocation()))).orElse(null);
                        if (distant != null && (distance > 64 || attackIndex % 3 == 2)) startAttack(Attack.BLADES, distant.getLocation());
                        else if (enraged && attackIndex % 4 == 3) startAttack(Attack.RING, target.getLocation());
                        else if (distance > 36) startAttack(Attack.CHARGE, target.getLocation());
                        else startAttack(attackIndex % 3 == 1 ? Attack.SLAM : Attack.SWEEP, target.getLocation());
                        attackIndex++;
                    }
                }
            }
            case WINDUP -> {
                halt(); warning();
                if ((remaining -= 2) <= 0) {
                    if (attack == Attack.CHARGE) change(State.CHARGE, 26);
                    else { executeImpact(); change(State.RECOVERY, enraged ? 22 : 32); }
                }
            }
            case CHARGE -> charge(players);
            case STAGGER, RECOVERY -> {
                halt(); if (state == State.STAGGER && ticks % 4 == 0) dust(base.getLocation().add(0, 2, 0), Color.fromRGB(255, 220, 150), 8);
                if ((remaining -= 2) <= 0) { change(State.CHASE, 0); cooldown = Math.max(12, (enraged ? 32 : 50) - rage / 5); }
            }
            default -> {}
        }
        if (ticks % 6 == 0) {
            base.getWorld().spawnParticle(Particle.SMOKE, base.getLocation().add(0, 2.4, 0), 3, 0.4, 0.2, 0.4, 0.01);
            dust(base.getLocation().add(0, 2.0, 0), enraged ? Color.fromRGB(255, 170, 35) : Color.fromRGB(225, 25, 40), 3);
        }
        model.animate(base.getLocation(), ticks, state, total == 0 ? 0 : 1.0 - (double) remaining / total,
                enraged, state == State.CHASE && target != null || state == State.CHARGE);
    }

    private List<Player> participants() {
        return base.getWorld().getPlayers().stream().filter(p -> p.isValid() && !p.isDead()
                && (p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE)
                && p.getLocation().distanceSquared(home) <= radius * radius).toList();
    }

    void startAttack(Attack next, Location target) {
        attack = next; anchor = base.getLocation().clone();
        facing = target.toVector().subtract(anchor.toVector()).setY(0);
        if (facing.lengthSquared() < 0.001) facing = new Vector(0, 0, 1); else facing.normalize();
        face(target); hit.clear(); halt();
        int duration = switch (attack) { case SWEEP -> 28; case SLAM -> 44; case CHARGE -> 36; case RING -> 52; case BLADES -> 36; };
        change(State.WINDUP, enraged ? duration - 6 : duration);
        if (attack == Attack.BLADES) blades.cast(anchor, target, remaining);
        String cue = switch (attack) {
            case SWEEP -> "ฟันกวาด — ถอยออกหรืออ้อมหลัง!";
            case SLAM -> "ทุบพื้น — กระโดดตอนค้อนลง หรือออกนอกวง!";
            case CHARGE -> "พุ่งชน — หลบด้านข้าง หรือล่อให้ชนกำแพง!";
            case RING -> "วงไฟ — เข้าวงใน หรือหนีออกนอกวง!";
            case BLADES -> "ดาบประหารจากพื้น — ออกจากรอยแดง!";
        };
        announce(cue, NamedTextColor.GOLD); sound(Sound.ENTITY_IRON_GOLEM_REPAIR, 1, 0.6f);
    }

    private void warning() {
        if (ticks % 4 != 0) return;
        Color color = Color.fromRGB(255, 55, 35);
        switch (attack) {
            case SLAM -> { ring(anchor, 6.5, color); ring(anchor, 3.25, color); }
            case RING -> { ring(anchor, 3, Color.fromRGB(90, 220, 180)); ring(anchor, 9, color); ring(anchor, 6, color); }
            case SWEEP -> {
                double center = Math.atan2(facing.getZ(), facing.getX());
                for (int a = -75; a <= 75; a += 5) {
                    double angle = center + Math.toRadians(a);
                    dust(anchor.clone().add(Math.cos(angle) * 5, 0.12, Math.sin(angle) * 5), color, 1);
                }
                for (int a : new int[]{-75, 75}) for (double r = 1; r <= 5; r += 0.5) {
                    double angle = center + Math.toRadians(a);
                    dust(anchor.clone().add(Math.cos(angle) * r, 0.12, Math.sin(angle) * r), color, 1);
                }
            }
            case CHARGE -> {
                for (double i = 1; i <= 12; i += 0.6) for (int sign : new int[]{-1, 1})
                    dust(anchor.clone().add(facing.clone().multiply(i)).add(-facing.getZ() * sign * 1.5, 0.12, facing.getX() * sign * 1.5), color, 1);
            }
            case BLADES -> {} // Each locked ground point draws its own warning.
        }
    }

    private void executeImpact() {
        if (attack == Attack.BLADES) return;
        sound(attack == Attack.SWEEP ? Sound.ENTITY_PLAYER_ATTACK_SWEEP : Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 0.55f);
        base.getWorld().spawnParticle(Particle.FLAME, anchor.clone().add(0, 0.3, 0), 70, 2, 0.15, 2, 0.06);
        for (Player p : participants()) {
            Vector relative = p.getLocation().toVector().subtract(anchor.toVector());
            double y = relative.getY();
            boolean inside = switch (attack) {
                case SWEEP -> y > -2 && y < 3.5 && WrathCombat.inSweep(relative.getX(), relative.getZ(), facing.getX(), facing.getZ());
                case SLAM -> WrathCombat.inSlam(relative.getX(), relative.getZ(), y);
                case RING -> WrathCombat.inRing(relative.getX(), relative.getZ(), y);
                default -> false;
            };
            if (inside && clearSight(p, anchor)) hurt(p, attack == Attack.SWEEP ? 18 : attack == Attack.SLAM ? 24 : 22, attack == Attack.SLAM ? 0.45 : 0.2);
        }
        if (attack == Attack.SLAM) ring(anchor, 6.5, Color.fromRGB(255, 190, 75));
        if (attack == Attack.RING) { ring(anchor, 3, Color.fromRGB(255, 150, 40)); ring(anchor, 9, Color.fromRGB(255, 150, 40)); }
    }

    private void charge(List<Player> players) {
        Location at = base.getLocation();
        // Check the next swept step at feet/torso/head, so even a one-block wall staggers Wrath.
        boolean wall = false;
        for (double y : new double[]{0.3, 1.4, 2.7}) {
            if (base.getWorld().rayTraceBlocks(at.clone().add(0, y, 0), facing, 1.8, FluidCollisionMode.NEVER, true) != null) wall = true;
        }
        if (wall) { stagger(); return; }
        Vector traveled = at.toVector().subtract(anchor.toVector());
        if (traveled.lengthSquared() > 144 || (remaining -= 2) <= 0) { halt(); change(State.RECOVERY, 30); return; }
        for (Player p : players) {
            Vector relative = p.getLocation().toVector().subtract(at.toVector());
            if (relative.getY() > -2 && relative.getY() < 3.5
                    && WrathCombat.inCharge(relative.getX(), relative.getZ(), facing.getX(), facing.getZ(), 1.8)
                    && clearSight(p, at)) hurt(p, 26, 0.4);
        }
        base.setVelocity(facing.clone().multiply(0.72).setY(base.getVelocity().getY()));
        base.getWorld().spawnParticle(Particle.SMOKE, at.clone().add(0, 0.2, 0), 5, 0.6, 0.1, 0.6, 0.03);
    }

    boolean clearSight(Player p, Location from) {
        Location start = from.clone().add(0, 1.5, 0);
        Vector delta = p.getLocation().add(0, 1, 0).toVector().subtract(start.toVector());
        return delta.lengthSquared() < 0.01 || base.getWorld().rayTraceBlocks(start, delta.clone().normalize(), delta.length(), FluidCollisionMode.NEVER, true) == null;
    }

    private void hurt(Player player, double damage, double lift) {
        hurt(player, damage, lift, hit);
    }
    void hurt(Player player, double damage, double lift, Set<UUID> castHits) {
        if (!castHits.add(player.getUniqueId())) return;
        double before = player.getHealth() + player.getAbsorptionAmount();
        WrathArmorBreak.Trial trial = plugin.armorBreak().begin(player);
        pendingHit = player.getUniqueId(); hitAccepted = false; acceptedDamage = 0;
        boolean landed = false;
        try {
            player.damage(damage * damageMultiplier * (enraged ? 1.2 : 1) * (1 + rage / 400.0), base);
            landed = hitAccepted && (acceptedDamage > 0 || player.getHealth() + player.getAbsorptionAmount() < before);
        } finally {
            pendingHit = null;
            plugin.armorBreak().finish(trial, landed);
        }
        // Do not bypass protection plugins or knock back a player whose damage was blocked.
        if (landed && !player.isDead()) {
            Vector push = player.getLocation().toVector().subtract(base.getLocation().toVector()).setY(0);
            if (push.lengthSquared() > 0.001) player.setVelocity(push.normalize().multiply(0.65).setY(lift));
        }
    }
    void observeHit(Player player, boolean accepted, double damage) {
        if (player.getUniqueId().equals(pendingHit)) { hitAccepted = accepted; acceptedDamage = damage; }
    }
    List<Player> bladeTargets() { return participants(); }

    void stagger() {
        halt(); rage = 0; change(State.STAGGER, 80);
        announce("WRATH เสียหลัก! โจมตีแกนอก — ดาเมจเพิ่ม 50%", NamedTextColor.AQUA);
        sound(Sound.BLOCK_ANVIL_LAND, 1.4f, 0.6f);
    }
    double damageScale() { return state == State.ARRIVAL || state == State.TRANSITION ? 0 : incomingMultiplier * nativeMaxHealth / maxHealth * (state == State.STAGGER ? 1.5 : 1); }
    double phaseOneDamageLimit() { return enraged ? Double.POSITIVE_INFINITY : Math.max(0, base.getHealth() - nativeMaxHealth * 0.5); }
    void attacked(double damage) { if (damage > 0 && state != State.STAGGER) rage = Math.min(100, rage + Math.max(1, (int) Math.ceil(damage * maxHealth / nativeMaxHealth / 3))); }

    private void face(Location target) {
        Vector d = target.toVector().subtract(base.getLocation().toVector()).setY(0);
        if (d.lengthSquared() > 0.001) {
            float yaw = (float) Math.toDegrees(Math.atan2(-d.getX(), d.getZ()));
            base.setRotation(yaw, 0); base.setBodyYaw(yaw);
        }
    }
    private void halt() { base.getPathfinder().stopPathfinding(); base.setVelocity(new Vector(0, base.getVelocity().getY(), 0)); }
    private void change(State next, int duration) {
        state = next; remaining = duration; total = duration;
        // Native walking goals cannot drift the boss away from its telegraphed attack.
        base.setAI(next == State.CHASE);
    }
    private void reset() {
        blades.clear();
        halt(); base.teleport(home); base.setHealth(nativeMaxHealth); enraged = false; rage = 0;
        change(State.ARRIVAL, 60); cooldown = 40; hit.clear();
    }
    private void announce(String text, NamedTextColor color) {
        for (Player p : bar.getPlayers()) p.sendActionBar(Component.text(text, color));
    }
    private void sound(Sound sound, float volume, float pitch) { base.getWorld().playSound(base.getLocation(), sound, volume, pitch); }
    private void dust(Location at, Color color, int count) { at.getWorld().spawnParticle(Particle.DUST, at, count, 0, 0, 0, 0, new Particle.DustOptions(color, 1.2f)); }
    private void ring(Location at, double radius, Color color) {
        for (int i = 0; i < 48; i++) {
            double angle = i * Math.PI / 24;
            dust(at.clone().add(Math.cos(angle) * radius, 0.12, Math.sin(angle) * radius), color, 1);
        }
    }

    void killed(Player killer) {
        if (killer != null) {
            ItemStack reward = new ItemStack(Material.NETHER_STAR);
            var meta = reward.getItemMeta();
            meta.displayName(Component.text("หัวใจแห่งโทสะ · Wrath Heart", NamedTextColor.RED));
            meta.lore(List.of(Component.text("หลักฐานชัยชนะเหนือ The Ashen Executioner", NamedTextColor.GRAY),
                    Component.text("วัตถุดิบของ 7sins — ยังไม่มีสูตรคราฟต์", NamedTextColor.DARK_GRAY)));
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "wrath_heart"), PersistentDataType.BYTE, (byte) 1);
            reward.setItemMeta(meta); base.getWorld().dropItemNaturally(base.getLocation(), reward);
            sound(Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.2f, 0.8f);
        }
        base.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, base.getLocation().add(0, 1.5, 0), 80, 0.8, 1, 0.8, 0.06);
        remove();
    }
    public void remove() {
        if (state == State.REMOVED) return;
        state = State.REMOVED; bar.removeAll(); blades.clear(); model.remove(); base.remove();
    }
}
