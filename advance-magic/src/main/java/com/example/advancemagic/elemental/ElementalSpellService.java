package com.example.advancemagic.elemental;

import com.example.advancemagic.AdvanceMagicPlugin;
import com.example.advancemagic.api.ElementalCastEvent;
import com.example.advancemagic.effect.EffectEngine;
import com.example.advancemagic.item.ElementalStaffService.Element;
import com.example.advancemagic.spell.MagicContext;
import com.example.advancemagic.spell.ElementalVfx;
import org.bukkit.*;
import org.bukkit.attribute.*;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.*;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.function.BiPredicate;

/** Server authoritative elemental combat. All callbacks run on the server thread.
 * Multipliers describe the TOTAL per target, divided across a spell's pulses.
 * Paid channels stop on death/logout/world change; telegraphs also stop if the
 * caster moves over two blocks. Interrupted casts are spent, like missed shots.
 */
public final class ElementalSpellService implements Listener, AutoCloseable {
    private final AdvanceMagicPlugin plugin;
    private final MagicContext context;
    private final ElementalVfx visuals;
    private final Map<UUID, State> states = new HashMap<>();
    private final Map<String, EffectEngine.Effect> uniqueEffects = new HashMap<>();
    private final Set<EffectEngine.Effect> effects = new HashSet<>();
    private final Set<Wall> walls = new HashSet<>();
    private final Map<UUID, Shadow> shadows = new HashMap<>();
    private final Map<String, Long> healingPulses = new HashMap<>();
    private final NamespacedKey slowKey;
    private final NamespacedKey summonKey;
    private long clock;

    private static final Set<String> DIRECT_TARGET = Set.of("bubble_prison", "crystal_prison", "life_drain", "cursed_mark", "hellfire_chain", "lightning_chain");
    private static final Set<String> GROUND_TARGET = Set.of("burning_ground", "magma_eruption", "scorching_vortex", "meteor_rain", "healing_spring", "whirlpool", "ice_spike", "blizzard", "absolute_zero", "cyclone_trap", "earth_spike", "quicksand", "titans_judgment", "holy_sanctuary", "divine_judgment", "abyssal_collapse", "static_field", "wrath_of_raijin");
    private static final Set<String> CLEANSE = Set.of("burn", "wet", "frost", "shock", "curse", "curse_mark", "slow");
    private static final Set<PotionEffectType> CLEANSE_POTIONS = Set.of(PotionEffectType.POISON, PotionEffectType.WITHER, PotionEffectType.SLOWNESS, PotionEffectType.WEAKNESS, PotionEffectType.BLINDNESS);

    private static final class Mark {
        int stacks;
        long until;
        long nextTick;
        Cast source;
        Mark(int stacks, long until, Cast source) { this.stacks = stacks; this.until = until; this.source = source; }
    }
    private static final class State {
        final LivingEntity entity;
        final Map<String, Mark> marks = new HashMap<>();
        final Map<String, Long> reactions = new HashMap<>();
        final Map<String, Buff> buffs = new HashMap<>();
        Location anchor;
        double slow;
        State(LivingEntity entity) { this.entity = entity; }
    }
    private static final class Buff {
        long until;
        double value;
        Buff(long until, double value) { this.until = until; this.value = value; }
    }
    private final class Cast {
        final Player player;
        final ElementalSpell spell;
        final double power, healing, utilityPower;
        final Location origin;
        final ElementalVfx.Scene art;
        final Set<UUID> targets = new HashSet<>(), statusApplied = new HashSet<>();
        final Map<UUID, Double> bossSpent = new HashMap<>(), healed = new HashMap<>();
        final Map<String, Double> reactionSpent = new HashMap<>();
        Cast(Player player, ElementalSpell spell, double multiplier) { this(player, spell, multiplier, 1); }
        /** levelFactor scales damage, shields and healing by the imprinted spell level (V = 1). */
        Cast(Player player, ElementalSpell spell, double multiplier, double levelFactor) {
            this.player = player; this.spell = spell; origin = player.getLocation().clone();
            var stats = plugin.elementalStats().snapshot(player);
            utilityPower = bounded(stats.spellPower() * (1 + stats.elementBonus(spell.element())) * multiplier * levelFactor, 0, 2000);
            ItemStack staff = player.getInventory().getItemInMainHand();
            // Damage Cores (+3% per level, from sky-landmark treasure) scale offensive power only.
            power = bounded(utilityPower * plugin.staffSpells().affinityMultiplier(staff,spell) * plugin.wands().damageMultiplier(staff), 0, 3000);
            healing = bounded(stats.healingPower() * levelFactor, 0, 2000);
            art = visuals.begin(player, spell);
        }
        boolean alive() { return player.isOnline() && !player.isDead() && player.getWorld() == origin.getWorld(); }
        boolean allows(LivingEntity target) {
            if (!targets.contains(target.getUniqueId()) && targets.size() >= targetCap()) return false;
            targets.add(target.getUniqueId()); return true;
        }
    }
    private static final class Wall {
        final Player owner;
        final World world;
        final BoundingBox box;
        final List<BlockDisplay> displays = new ArrayList<>();
        Wall(Player owner, BoundingBox box) { this.owner = owner; world = owner.getWorld(); this.box = box; }
    }
    private static final class Shadow {
        final ArmorStand entity;
        final Cast cast;
        double remaining;
        Shadow(ArmorStand entity, Cast cast) { this.entity = entity; this.cast = cast; remaining = cast.power * 3; }
    }

    public ElementalSpellService(AdvanceMagicPlugin plugin) {
        this.plugin = plugin; context = plugin.context(); visuals = new ElementalVfx(context);
        slowKey = new NamespacedKey(plugin, "elemental_slow");
        summonKey = new NamespacedKey(plugin, "elemental_summon");
        for (World world : Bukkit.getWorlds()) for (LivingEntity entity : world.getLivingEntities()) {
            AttributeInstance movement = entity.getAttribute(Attribute.MOVEMENT_SPEED);
            if (movement != null) movement.removeModifier(slowKey);
        }
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }
    private static double bounded(double value, double min, double max) { return Double.isFinite(value) ? Math.max(min, Math.min(max, value)) : min; }
    private double configured(String path, double fallback, double min, double max) { return bounded(plugin.getConfig().getDouble("elemental-combat." + path, fallback), min, max); }
    private int targetCap() { return (int)configured("target-cap", 12, 1, 12); }
    private State state(LivingEntity entity) { return states.computeIfAbsent(entity.getUniqueId(), ignored -> new State(entity)); }
    private Mark mark(LivingEntity entity, String key) {
        State s = states.get(entity.getUniqueId()); Mark m = s == null ? null : s.marks.get(key);
        return m != null && m.until > clock ? m : null;
    }
    public int stacks(LivingEntity entity, String key) { Mark m = mark(entity, key); return m == null ? 0 : m.stacks; }
    public boolean hasStatus(LivingEntity entity, String key) { return mark(entity, key) != null; }
    public boolean isBoss(LivingEntity entity) {
        if (entity instanceof Boss || entity instanceof EnderDragon || entity instanceof Wither) return true;
        for (NamespacedKey key : entity.getPersistentDataContainer().getKeys()) {
            String k = key.getKey();
            if (Set.of("control_resistant", "world_boss_entity", "sanctuary_boss", "arkhaia_part", "boss", "is_boss").contains(k)) return true;
        }
        return entity.getScoreboardTags().stream().map(s -> s.toLowerCase(Locale.ROOT)).anyMatch(s -> s.equals("boss") || s.endsWith(":boss") || s.endsWith("_boss"));
    }
    private boolean permitted(Cast c, LivingEntity entity) { return c.alive() && context.enemy(c.player, entity) && c.allows(entity) && context.affect(c.player, entity, null); }
    private boolean visible(Player owner, Location from, Location to) { return context.clear(from, to) && !wallBlocks(owner, from, to); }
    private boolean wallBlocks(Player owner, Location from, Location to) {
        if (from.getWorld() != to.getWorld()) return true;
        Vector delta = to.toVector().subtract(from.toVector()); double distance = delta.length();
        if (distance < .001) return false;
        for (Wall wall : walls) if (wall.world == from.getWorld() && !context.ally(wall.owner, owner)
                && wall.box.rayTrace(from.toVector(), delta.clone().normalize(), distance) != null) return true;
        return false;
    }
    private List<LivingEntity> nearby(Cast c, Location at, double radius, boolean friendly) {
        return context.nearby(c.player, at, radius, friendly).stream()
                .filter(e -> visible(c.player, at.clone().add(0, .35, 0), e.getLocation().add(0, .7, 0)))
                .limit(targetCap()).toList();
    }
    private EffectEngine.Effect effect(Cast c, int duration, BiPredicate<EffectEngine.Effect, Integer> body) {
        EffectEngine.Effect e = plugin.effects().start(c.player, duration, (active, age) -> c.alive() && body.test(active, age));
        effects.add(e); e.onClose(() -> effects.remove(e)); return e;
    }
    private void unique(Cast c, String key, EffectEngine.Effect e) {
        String id = c.player.getUniqueId() + ":" + key;
        EffectEngine.Effect previous = uniqueEffects.put(id, e);
        if (previous != null && previous != e) previous.close();
        e.onClose(() -> uniqueEffects.remove(id, e));
    }

    public boolean cast(Player player) {
        if (plugin.casts() != null && !plugin.casts().canUseElemental(player)) { player.sendActionBar(ChatColor.RED + "ไม่สามารถร่ายเวทได้ในขณะนี้"); return false; }
        if (player.isPermissionSet("advance-magic.cast") && !player.hasPermission("advance-magic.cast")) { player.sendActionBar(ChatColor.RED + "คุณไม่มีสิทธิ์ร่ายเวท"); return false; }
        int staffSlot = player.getInventory().getHeldItemSlot();
        ItemStack staff = player.getInventory().getItemInMainHand();
        ElementalSpell spell = plugin.staffSpells().selected(staff);
        if (!player.isOnline() || player.isDead() || player.getGameMode() == GameMode.SPECTATOR) return false;
        if (spell == null) { player.sendActionBar(ChatColor.YELLOW + "คทายังไม่มีเวท · ใช้ /spell เพื่อประทับคัมภีร์"); return false; }
        if (spell.id().equals("phoenix_rebirth")) {
            int cost = Math.max(1, (int)Math.ceil(spell.mana() * (1 - bounded(plugin.elementalStats().snapshot(player).manaReduction(), 0, ElementalStats.MAX_REDUCTION))));
            player.sendActionBar(ChatColor.GOLD + "Phoenix Rebirth ทำงานอัตโนมัติเมื่อถือคทานี้ (ใช้ " + cost + " MP และต้องพ้น Cooldown)"); return false;
        }
        if (plugin.wands().usesLeft(staff) <= 0) { player.sendActionBar(ChatColor.RED + "คทาหมดความทนทาน · ซ่อมที่แท่นฟื้นฟู หรือใช้ Wand Repair Core"); return false; }
        if (!plugin.effects().hasCapacity()) { player.sendActionBar(ChatColor.RED + "มีเวททำงานอยู่มากเกินไป โปรดลองอีกครั้ง"); return false; }
        if (hasStatus(player, "frozen")) return false;
        LivingEntity target = DIRECT_TARGET.contains(spell.id()) ? target(player, 24) : null;
        if (DIRECT_TARGET.contains(spell.id()) && target == null) { player.sendActionBar(ChatColor.RED + "ไม่พบเป้าหมายในระยะหรือมีสิ่งกีดขวาง"); return false; }
        Location ground = GROUND_TARGET.contains(spell.id()) ? groundPoint(player, 24) : null;
        if (GROUND_TARGET.contains(spell.id()) && ground == null) { player.sendActionBar(ChatColor.RED + "ต้องเล็งพื้นภายใน 24 บล็อก"); return false; }
        Location dash = spell.id().endsWith("_dash") || spell.id().equals("gale_step") ? dashPoint(player, spell.id().equals("flame_dash") ? 6 : 7) : null;
        if ((spell.id().endsWith("_dash") || spell.id().equals("gale_step")) && dash == null) return false;
        ElementalCastEvent event = new ElementalCastEvent(player, spell); Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return false;
        var stats = plugin.elementalStats().snapshot(player);
        int level = plugin.staffSpells().level(staff, spell);
        int cost = Math.max(1, (int)Math.ceil(SpellLevels.mana(spell, level) * (1 - bounded(stats.manaReduction(), 0, ElementalStats.MAX_REDUCTION))));
        long now = System.currentTimeMillis();
        if (!plugin.mana().reserveElemental(player, spell, cost, now, plugin.wands().cooldownScale(staff), SpellLevels.cooldownFactor(spell, level))) {
            long remaining = Math.max(plugin.mana().elementalRemaining(player, spell, now), plugin.mana().gcdRemaining(player, now));
            if (spell.ultimate()) remaining = Math.max(remaining, plugin.mana().ultimateRemaining(player, now));
            player.sendActionBar(ChatColor.RED + (remaining > 0 ? "เวทกำลังพัก · " + String.format(Locale.ROOT, "%.1f", remaining / 1000.0) + "s" : "Mana ไม่พอ · ต้องใช้ " + cost + " MP"));
            return false;
        }
        Cast c = new Cast(player, spell, event.getDamageMultiplier(), SpellLevels.damageFactor(spell, level));
        try {
            if (!execute(c, target, ground, dash)) { c.art.close(); plugin.mana().rollbackElemental(player, spell, cost); return false; }
            c.art.startup();
            // One use per successful cast; failed or refunded casts never wear the staff.
            ItemStack held = player.getInventory().getItem(staffSlot);
            if (plugin.staffs().isStaff(held) && plugin.wands().consumeUse(held)) player.getInventory().setItem(staffSlot, held);
            player.sendActionBar(spell.element().color + SpellLevels.label(spell, level) + ChatColor.GRAY + " · " + cost + " MP");
            return true;
        } catch (RuntimeException ex) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Elemental cast " + spell.id() + " failed", ex);
            c.art.close();
            // No refund after a partially executed cast: never mint free projectiles or summons.
            return false;
        }
    }

    private boolean execute(Cast c, LivingEntity target, Location at, Location dash) {
        String id = c.spell.id(); Player p = c.player;
        switch (id) {
            case "fireball" -> projectile(c, 1.25, 24, 2.5);
            case "aqua_bullet", "frost_bolt", "stone_bullet", "radiant_bolt", "shadow_bolt", "spark_shot" -> projectile(c, 1.6, 24, 0);
            case "flame_burst" -> area(c, p.getLocation(), 4, 1, true, false);
            case "burning_ground" -> field(c, at, 3, 100, 5, 0, false, false, false);
            case "flame_dash", "gale_step", "thunder_dash" -> {
                if (!context.affect(p, p, null) || !p.teleport(dash, PlayerTeleportEvent.TeleportCause.PLUGIN)) return false;
                Location start = c.origin.clone().add(0, .8, 0), end = dash.clone().add(0, .8, 0);
                if (!id.equals("gale_step")) line(c, start, end, 1.2, targetCap(), 1, true);
                else beam(c, start, end);
                if (id.equals("flame_dash")) {
                    // Trail is the visual tell for the single 0.8x corridor hit, without extra unbudgeted damage.
                    effect(c, 40, (e, age) -> { if (age % 5 == 0) beam(c, start, end); return true; });
                }
                p.setFallDistance(0);
            }
            case "inferno_spear" -> aimedLine(c, 24, .8, 4, 1);
            case "water_blade" -> aimedLine(c, 20, 1, targetCap(), 1);
            case "wind_cutter" -> aimedLine(c, 18, .7, 3, 1);
            case "thunder_spear" -> aimedLine(c, 26, 1, targetCap(), 1);
            case "magma_eruption" -> telegraph(c, at, 16, 3.5, () -> area(c, at, 3.5, 1, false, true));
            case "hellfire_chain" -> chain(c, target, 5, 6);
            case "lightning_chain" -> chain(c, target, 4, 6);
            case "scorching_vortex" -> field(c, at, 4, 100, 5, .55, false, false, false);
            case "meteor_rain" -> telegraph(c, at, 50, 6, () -> meteors(c, at));
            case "healing_spring" -> field(c, at, 3.5, 100, 5, 0, true, false, false);
            case "bubble_prison" -> { if (!permitted(c, target)) return false; hit(c, target, 1, true); if (lastHitAccepted) { freeze(c, target, 40); bubble(target); } }
            case "tidal_push" -> cone(c, 7, .25, true);
            case "aqua_shield" -> { if (!context.affect(p, p, null)) return false; shield(c, p, 120, Math.min(c.utilityPower * 1.8, maxHealth(p) * .6)); ring(c, p.getLocation(), 1.2); }
            case "rain_blessing" -> field(c, p.getLocation(), 5, 160, 8, 0, true, true, false);
            case "whirlpool" -> field(c, at, 4, 80, 4, .5, false, false, false);
            case "tsunami" -> wave(c, 16, 5, 1, true);
            case "leviathans_wrath" -> repeatedWaves(c, 5, 24, 24, 4);
            case "ice_spike", "earth_spike" -> { pillar(c, at, 3.5); area(c, at, 2.5, 1, false, id.equals("earth_spike")); }
            case "frozen_armor" -> buff(c, p, "reduction", 140, .2);
            case "glacial_path" -> aimedLine(c, 8, 1.5, targetCap(), 1);
            case "crystal_prison" -> { if (!permitted(c, target)) return false; hit(c, target, 1, true); if (lastHitAccepted) { freeze(c, target, 50); bubble(target); } }
            case "blizzard" -> field(c, at, 6, 120, 6, 0, false, false, false);
            case "absolute_zero" -> telegraph(c, at, 40, 6, () -> absoluteZero(c, at));
            case "updraft" -> { area(c, p.getLocation(), 5, 1, false, false); for (LivingEntity e : nearby(c, p.getLocation(), 5, false)) if (c.statusApplied.contains(e.getUniqueId()) && permitted(c, e) && !isBoss(e)) context.velocity(e, new Vector(0, .8, 0)); }
            case "wind_barrier" -> buff(c, p, "projectile_reduction", 100, .35);
            case "cyclone_trap" -> field(c, at, 4, 80, 4, .5, false, false, false);
            case "thousand_wind_blades" -> effect(c, 37, (e, age) -> { if (age % 3 == 0 && age / 3 < 12) aimedLine(c, 22, .7, 3, 1.0 / 12); return true; });
            case "tempest_dominion" -> tempestDominion(c, p.getLocation().clone());
            case "stone_wall" -> { if (!stoneWall(c)) return false; }
            case "iron_skin" -> buff(c, p, "reduction", 140, .25);
            case "quicksand" -> field(c, at, 4, 100, 5, 0, false, false, false);
            case "seismic_slam" -> { pillar(c, p.getLocation(), 2); area(c, p.getLocation(), 5, 1, false, true); }
            case "golem_guardian" -> { if (!golem(c)) return false; }
            case "titans_judgment" -> telegraph(c, at, 40, 6, () -> titansJudgment(c, at));
            case "holy_sanctuary" -> field(c, at, 5, 120, 6, 0, true, false, true);
            case "purification" -> { for (LivingEntity e : nearby(c, p.getLocation(), 5, true)) if (e instanceof Player ally && context.affect(p, ally, null)) cleanse(ally); ring(c, p.getLocation(), 5); }
            case "divine_judgment" -> telegraph(c, at, 12, 3, () -> { pillar(c, at, 10); area(c, at, 3, 1, false, false); });
            case "heavens_descent" -> heavensDescent(c);
            case "life_drain" -> { if (!permitted(c, target)) return false; double actual = hit(c, target, 1, true); heal(c, p, Math.min(actual * .2, maxHealth(p) * (isBoss(target) ? .05 : .2))); beam(c, target.getEyeLocation(), p.getEyeLocation()); }
            case "cursed_mark" -> { if (!permitted(c, target)) return false; put(c, target, "curse_mark", 1, 120); bubble(target); }
            case "shadow_doppelganger" -> { if (!shadow(c)) return false; }
            case "abyssal_collapse" -> telegraph(c, at, 30, 6, () -> abyssalCollapse(c, at));
            case "static_field" -> field(c, at, 5, 100, 5, 0, false, false, false);
            case "wrath_of_raijin" -> telegraph(c, at, 30, 6, () -> storm(c, at));
            default -> { return false; }
        }
        return true;
    }

    private LivingEntity target(Player p, double range) {
        RayTraceResult trace = context.target(p, range);
        return trace != null && trace.getHitEntity() instanceof LivingEntity e
                && visible(p, p.getEyeLocation(), trace.getHitPosition().toLocation(p.getWorld())) ? e : null;
    }
    private Location endPoint(Player p, double range) {
        Location eye = p.getEyeLocation(); Vector dir = eye.getDirection(); double distance = 0;
        for (double d = .5; d <= range; d += .5) {
            Location next = eye.clone().add(dir.clone().multiply(d));
            if (!context.loaded(next) || wallBlocks(p, eye, next)) break;
            distance = d;
        }
        if (distance < .5) return eye;
        RayTraceResult hit = p.getWorld().rayTraceBlocks(eye, dir, distance, FluidCollisionMode.NEVER, true);
        return hit == null ? eye.clone().add(dir.multiply(distance)) : hit.getHitPosition().toLocation(p.getWorld());
    }
    private Location groundPoint(Player p, double range) {
        Location end = endPoint(p, range);
        // Project a visible point onto an exposed floor; never walk through or load chunks.
        for (int dy = 1; dy >= -6; dy--) {
            Location sample = end.clone().add(0, dy, 0);
            if (!context.loaded(sample)) return null;
            if (sample.getBlock().getType().isSolid()) {
                Location above = sample.getBlock().getLocation().add(.5, 1.05, .5);
                if (context.loaded(above) && above.getBlock().isPassable()
                        && visible(p, p.getEyeLocation(), above.clone().add(0, .15, 0))) return above;
                return null;
            }
        }
        return null;
    }
    private Location dashPoint(Player p, double distance) {
        Location start = p.getLocation(), best = null; Vector direction = start.getDirection().setY(0);
        if (direction.lengthSquared() < .001) return null;
        direction.normalize();
        for (double d = .35; d <= distance; d += .35) {
            Location next = start.clone().add(direction.clone().multiply(d));
            if (!context.safeBody(next) || wallBlocks(p, start.clone().add(0, .8, 0), next.clone().add(0, .8, 0))) break;
            if (d >= 1) best = next;
        }
        return best;
    }
    private void projectile(Cast c, double speed, double range, double explosion) {
        Location position = c.player.getEyeLocation().clone(); Vector direction = position.getDirection();
        EffectEngine.Effect flight = effect(c, (int)Math.ceil(range / speed) + 2, (e, age) -> {
            if (!context.loaded(position)) return false;
            double step = Math.min(speed, range - age * speed);
            if (step <= 0) return false;
            Location next = position.clone().add(direction.clone().multiply(step));
            if (!context.loaded(next) || wallBlocks(c.player, position, next)) { ring(c, position, .6); return false; }
            RayTraceResult hit = position.getWorld().rayTrace(position, direction, step, FluidCollisionMode.NEVER, true, .3,
                    entity -> context.enemy(c.player, entity));
            Location impact = hit == null ? next : hit.getHitPosition().toLocation(position.getWorld());
            if(c.art.enabled())c.art.projectile(position,impact);else beam(c, position, impact);
            position.setX(impact.getX()); position.setY(impact.getY()); position.setZ(impact.getZ());
            if (hit != null) {
                if (explosion > 0) area(c, impact, explosion, 1, false, false);
                else if (hit.getHitEntity() instanceof LivingEntity target) {
                    hit(c, target, 1, true);
                    if (lastHitAccepted && c.spell.id().equals("stone_bullet") && permitted(c, target) && !isBoss(target)) push(c, target, c.player.getLocation(), .65);
                }
                ring(c, impact, .5); return false;
            }
            return true;
        });
        flight.onClose(() -> c.art.clear("projectile"));
    }
    private void aimedLine(Cast c, double range, double width, int maximum, double fraction) {
        line(c, c.player.getEyeLocation(), endPoint(c.player, range), width, maximum, fraction, true);
    }
    private void line(Cast c, Location from, Location to, double width, int maximum, double fraction, boolean status) {
        beam(c, from, to);
        for (LivingEntity entity : lineTargets(c, from, to, width, maximum, false)) hit(c, entity, fraction, status);
    }
    /** The nearest visible enemies (or allied players other than the caster) touching the segment. */
    private List<LivingEntity> lineTargets(Cast c, Location from, Location to, double width, int maximum, boolean friendly) {
        Vector delta = to.toVector().subtract(from.toVector()); double distance = delta.length();
        if (distance < .01 || !context.loaded(from) || !context.loaded(to)) return List.of();
        Vector direction = delta.normalize();
        BoundingBox box = BoundingBox.of(from, to).expand(width + 1);
        List<LivingEntity> hits = new ArrayList<>();
        for (Entity e : from.getWorld().getNearbyEntities(box)) if (e instanceof LivingEntity living
                && (friendly ? living instanceof Player && living != c.player && context.ally(c.player, living) : context.enemy(c.player, e))) {
            BoundingBox body = living.getBoundingBox().expand(width);
            if (body.contains(from.toVector()) || body.rayTrace(from.toVector(), direction, distance) != null) hits.add(living);
        }
        hits.sort(Comparator.comparingDouble(e -> e.getLocation().distanceSquared(from)));
        List<LivingEntity> visible = new ArrayList<>();
        for (LivingEntity entity : hits) {
            if (visible.size() >= maximum) break;
            if (visible(c.player, from, entity.getEyeLocation())) visible.add(entity);
        }
        return visible;
    }
    private void cone(Cast c, double range, double cosine, boolean push) {
        Vector forward = c.player.getLocation().getDirection().setY(0);
        if (forward.lengthSquared() < .001) forward = new Vector(0, 0, 1);
        forward.normalize();
        if(c.art.enabled())c.art.cone(c.player.getLocation(),forward,range,cosine);else ring(c, c.player.getLocation(), range);
        for (LivingEntity target : nearby(c, c.player.getLocation(), range, false)) {
            Vector delta = target.getLocation().toVector().subtract(c.player.getLocation().toVector()).setY(0);
            if (delta.lengthSquared() > .01 && delta.normalize().dot(forward) < cosine) continue;
            hit(c, target, 1, true);
            if (lastHitAccepted && push && permitted(c, target) && !isBoss(target)) push(c, target, c.player.getLocation(), 1.1);
        }
    }
    private void area(Cast c, Location at, double radius, double fraction, boolean push, boolean stagger) {
        ring(c, at, radius);
        for (LivingEntity target : nearby(c, at, radius, false)) {
            hit(c, target, fraction, true);
            if (lastHitAccepted && push && permitted(c, target) && !isBoss(target)) push(c, target, at, .8);
            if (lastHitAccepted && stagger && permitted(c, target)) addStagger(c, target, 15);
        }
    }
    private void chain(Cast c, LivingEntity first, int count, double range) {
        Set<UUID> visited = new HashSet<>(); LivingEntity current = first; Location from = c.player.getEyeLocation();
        for (int i = 0; i < count && current != null; i++) {
            if (!visible(c.player, from, current.getEyeLocation())) break;
            beam(c, from, current.getEyeLocation()); hit(c, current, 1, true); visited.add(current.getUniqueId());
            from = current.getEyeLocation();
            current = nearby(c, current.getLocation(), range, false).stream().filter(e -> !visited.contains(e.getUniqueId())).findFirst().orElse(null);
        }
    }
    private void telegraph(Cast c, Location at, int delay, double radius, Runnable action) {
        boolean[] completed = {false};
        EffectEngine.Effect channel=effect(c, delay + 1, (e, age) -> {
            if (!context.loaded(at) || c.player.getLocation().distanceSquared(c.origin) > 4 || hasStatus(c.player, "frozen")) {
                c.player.sendActionBar(ChatColor.RED + "การร่ายถูกขัดจังหวะ"); return false;
            }
            c.art.telegraph(at,radius,age,delay);
            if (age % 4 == 0) outline(c, at, radius);
            if (legendarySeven(c) && age % 20 == 0) legendaryChargeSound(c, at, age);
            if (age == delay) {
                completed[0] = true; e.close();
                try { action.run(); }
                catch (RuntimeException failure) { if (legendarySeven(c)) c.art.close(); throw failure; }
                return false;
            }
            return true;
        });
        channel.onClose(()->{c.art.clear("telegraph");c.art.clear("boundary");if(legendarySeven(c)&&!completed[0])c.art.close();});
        if (legendarySeven(c)) unique(c, "legendary:" + c.spell.id(), channel);
    }

    private boolean legendarySeven(Cast c) {
        return switch(c.spell.id()) {
            case "meteor_rain", "leviathans_wrath", "absolute_zero", "tempest_dominion",
                 "titans_judgment", "abyssal_collapse", "wrath_of_raijin" -> true;
            default -> false;
        };
    }

    private void legendaryChargeSound(Cast c, Location at, int age) {
        switch(c.spell.id()) {
            case "meteor_rain" -> heavenlySound(at, Sound.ENTITY_BLAZE_SHOOT, .75f, .6f + age * .018f);
            case "absolute_zero" -> heavenlySound(at, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, .85f, .7f + age * .015f);
            case "titans_judgment" -> heavenlySound(at, Sound.BLOCK_NOTE_BLOCK_BASEDRUM, 1.1f, .5f + age * .008f);
            case "abyssal_collapse" -> heavenlySound(at, Sound.ENTITY_WARDEN_HEARTBEAT, .9f, .6f + age * .008f);
            case "wrath_of_raijin" -> heavenlySound(at, Sound.BLOCK_NOTE_BLOCK_BASEDRUM, 1.2f, .7f + age * .012f);
            default -> { }
        }
    }

    private void legendaryLifetime(Cast c, EffectEngine.Effect active) {
        active.onClose(c.art::close);
        unique(c, "legendary:" + c.spell.id(), active);
    }

    /** Damage fractions sum to one; frost stacks are consumed only by the shatter. */
    private void absoluteZero(Cast c, Location center) {
        EffectEngine.Effect active = effect(c, 80, (e, age) -> {
            if (!context.loaded(center)) return false;
            c.art.field(center, 6, age, 80);
            if (age % 4 == 0) outline(c, center, 6);
            if (age == 0 || age == 20 || age == 60) {
                double fraction = age == 60 ? .70 : .15;
                for (LivingEntity target : nearby(c, center, 6, false)) {
                    hit(c, target, fraction, age == 60);
                    if (!lastHitAccepted || !permitted(c, target)) continue;
                    if (age == 0 && !isBoss(target)) slow(c, target, .5, 45);
                    if (age == 20) freeze(c, target, 40);
                }
                heavenlySound(center, age == 60 ? Sound.BLOCK_GLASS_BREAK : Sound.BLOCK_GLASS_PLACE,
                        age == 60 ? 1.5f : .9f, age == 60 ? .65f : 1.25f);
                if (age == 60) pillar(c, center, 8);
            }
            return true;
        });
        legendaryLifetime(c, active);
    }

    /** Six budgeted hits with controlled orbital impulses rather than a permanent hard stun. */
    private void tempestDominion(Cast c, Location center) {
        Set<UUID> accepted = new HashSet<>();
        heavenlySound(center, Sound.ENTITY_PHANTOM_FLAP, 1.1f, .55f);
        EffectEngine.Effect active = effect(c, 120, (e, age) -> {
            if (!context.loaded(center)) return false;
            c.art.field(center, 7, age, 120);
            if (age % 4 == 0) outline(c, center, 7);
            if (age % 20 == 0) {
                accepted.clear();
                for (LivingEntity target : nearby(c, center, 7, false)) {
                    hit(c, target, 1.0 / 6, true);
                    if (lastHitAccepted && !target.isDead()) {
                        accepted.add(target.getUniqueId());
                        if (isBoss(target)) addStagger(c, target, 10);
                    }
                }
                heavenlySound(center, Sound.ENTITY_PLAYER_ATTACK_SWEEP, .85f, .6f + age * .006f);
            }
            if (age % 8 == 0) for (LivingEntity target : nearby(c, center, 7, false)) {
                if (!accepted.contains(target.getUniqueId()) || isBoss(target) || !permitted(c, target)) continue;
                Vector radial = target.getLocation().toVector().subtract(center.toVector()).setY(0);
                if (radial.lengthSquared() < .04) radial = new Vector(Math.cos(age), 0, Math.sin(age));
                radial.normalize();
                Vector tangent = new Vector(-radial.getZ(), 0, radial.getX()).multiply(.42);
                Vector velocity = tangent.subtract(radial.multiply(.22));
                velocity.setY(age % 24 == 0 ? Math.max(target.getVelocity().getY(), .35) : target.getVelocity().getY());
                context.velocity(target, velocity);
            }
            if (age == 116) heavenlySound(center, Sound.ENTITY_GENERIC_EXPLODE, 1.1f, 1.5f);
            return true;
        });
        legendaryLifetime(c, active);
    }

    private void titansJudgment(Cast c, Location center) {
        pillar(c, center, 8);
        ring(c, center, 6);
        for (LivingEntity target : nearby(c, center, 6, false)) {
            hit(c, target, 1, true);
            if (!lastHitAccepted || !permitted(c, target)) continue;
            if (isBoss(target)) addStagger(c, target, 35);
            else { slow(c, target, .5, 20); push(c, target, center, .55); }
        }
        heavenlySound(center, Sound.ENTITY_IRON_GOLEM_ATTACK, 1.5f, .5f);
        heavenlySound(center, Sound.ENTITY_GENERIC_EXPLODE, 1.6f, .65f);
        EffectEngine.Effect active = effect(c, 44, (e, age) -> context.loaded(center));
        legendaryLifetime(c, active);
    }

    /** The opening chains preserve Curse until the final implosion consumes it. */
    private void abyssalCollapse(Cast c, Location center) {
        EffectEngine.Effect active = effect(c, 100, (e, age) -> {
            if (!context.loaded(center)) return false;
            c.art.field(center, 6, age, 100);
            if (age % 4 == 0) outline(c, center, 6);
            if (age == 0 || age == 80) {
                for (LivingEntity target : nearby(c, center, 6, false)) {
                    hit(c, target, age == 0 ? .20 : .80, age == 80);
                    if (age != 0 || !lastHitAccepted || !permitted(c, target)) continue;
                    if (isBoss(target)) addStagger(c, target, 15);
                    else { freeze(c, target, 30); slow(c, target, .45, 80); }
                }
                heavenlySound(center, age == 0 ? Sound.ENTITY_WARDEN_HEARTBEAT : Sound.ENTITY_GENERIC_EXPLODE,
                        age == 0 ? 1.0f : 1.6f, .5f);
                if (age == 80) pillar(c, center, 4);
            }
            if (age == 40) heavenlySound(center, Sound.BLOCK_BEACON_AMBIENT, .65f, .5f);
            return true;
        });
        legendaryLifetime(c, active);
    }

    private static final int LANCE_CHARGE = 24, LANCE_PULSE = 5, LANCE_FINALE = 20;
    /**
     * Heaven's Descent: a golden judgment beam cannon. After a 24-tick charge the beam follows the
     * caster's aim at a bounded turn rate. Pulses every five ticks share 80% of the damage, the
     * closing blast at the last impact point 20%; allies in the beam are healed once a second.
     */
    private void heavensDescent(Cast c) {
        int firing = (int)configured("legendary-light.beam-seconds", 6, 3, 10) * 20;
        double range = configured("legendary-light.beam-range", 48, 16, 64);
        double turn = Math.toRadians(configured("legendary-light.turn-degrees-per-second", 120, 30, 360)) / 20;
        double fraction = .8 / ((firing + LANCE_PULSE - 1) / LANCE_PULSE);
        Vector[] aim = {c.player.getEyeLocation().getDirection()};
        Location[] end = {null};
        EffectEngine.Effect active = effect(c, LANCE_CHARGE + firing + LANCE_FINALE, (e, age) -> {
            if (hasStatus(c.player, "frozen")) {
                c.player.sendActionBar(ChatColor.RED + "การร่ายถูกขัดจังหวะ");
                return false;
            }
            Location eye = c.player.getEyeLocation();
            if (!context.loaded(eye)) return false;
            boolean firingNow = age >= LANCE_CHARGE && age < LANCE_CHARGE + firing;
            // The seal swings freely while charging; the firing beam is heavy and turns at a bounded rate.
            if (age < LANCE_CHARGE) aim[0] = eye.getDirection();
            else if (firingNow) aim[0] = steer(aim[0], eye.getDirection(), turn);
            Vector dir = aim[0];
            if (age < LANCE_CHARGE + firing || end[0] == null) end[0] = lanceEnd(c, eye, dir, range);
            Location muzzle = eye.clone().add(dir.clone().multiply(1.8)).add(0, -.3, 0);
            c.art.lance(muzzle, end[0], dir, age, LANCE_CHARGE, firing);
            if (age == 0) heavenlySound(muzzle, Sound.BLOCK_BEACON_POWER_SELECT, 1.0f, .6f);
            if (age == 8 || age == 16) heavenlySound(muzzle, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, .9f, .8f + age * .03f);
            if (age == LANCE_CHARGE - 2) heavenlySound(muzzle, Sound.BLOCK_BELL_RESONATE, 1.2f, 1.4f);
            if (age == LANCE_CHARGE) {
                heavenlySound(muzzle, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.3f, 1.5f);
                heavenlySound(muzzle, Sound.BLOCK_BEACON_ACTIVATE, 1.2f, 1.3f);
            }
            if (firingNow) {
                int fired = age - LANCE_CHARGE;
                if (fired > 0 && fired % 20 == 0) heavenlySound(muzzle, Sound.BLOCK_BEACON_AMBIENT, 1.2f, 1.6f);
                if (fired % 8 == 4) heavenlySound(end[0], Sound.ENTITY_GENERIC_EXPLODE, .45f, 1.7f);
                if (fired % LANCE_PULSE == 0) {
                    for (LivingEntity target : lineTargets(c, eye, end[0], 1.4, targetCap(), false)) {
                        hit(c, target, fraction, true);
                        if (lastHitAccepted && permitted(c, target) && !isBoss(target)) push(c, target, eye, .22);
                    }
                    if (fired % 20 == 0) for (LivingEntity ally : lineTargets(c, eye, end[0], 1.4, targetCap(), true))
                        heal(c, (Player)ally, c.healing * .8);
                }
            }
            if (age == LANCE_CHARGE + firing) {
                area(c, end[0], 4, .2, true, true);
                heavenlySound(end[0], Sound.ENTITY_GENERIC_EXPLODE, 1.6f, .8f);
                heavenlySound(muzzle, Sound.BLOCK_BEACON_DEACTIVATE, 1.0f, 1.2f);
            }
            return true;
        });
        legendaryLifetime(c, active);
    }

    /** Turns {@code current} toward {@code wanted} by at most {@code step} radians. */
    private static Vector steer(Vector current, Vector wanted, double step) {
        double angle = current.angle(wanted);
        if (!Double.isFinite(angle) || angle <= step) return wanted.clone();
        Vector axis = current.getCrossProduct(wanted);
        if (axis.lengthSquared() < 1e-9) axis = (Math.abs(current.getY()) < .9 ? new Vector(0, 1, 0) : new Vector(1, 0, 0)).crossProduct(current);
        return current.clone().rotateAroundAxis(axis.normalize(), step).normalize();
    }

    /** Where the beam stops: the first solid block, an elemental wall, unloaded terrain or its full range. */
    private Location lanceEnd(Cast c, Location eye, Vector dir, double range) {
        double distance = range;
        for (double d = 1; d <= range; d++) {
            Location next = eye.clone().add(dir.clone().multiply(d));
            if (!context.loaded(next) || wallBlocks(c.player, eye, next)) { distance = d - 1; break; }
        }
        if (distance < .5) return eye.clone().add(dir.clone().multiply(.5));
        RayTraceResult hit = eye.getWorld().rayTraceBlocks(eye, dir, distance, FluidCollisionMode.NEVER, true);
        return hit == null ? eye.clone().add(dir.clone().multiply(distance)) : hit.getHitPosition().toLocation(eye.getWorld());
    }

    private void heavenlySound(Location at, Sound tone, float volume, float pitch) {
        if (plugin.getConfig().getBoolean("elemental-visuals.sounds", true) && context.loaded(at))
            at.getWorld().playSound(at, tone, SoundCategory.PLAYERS, volume, pitch);
    }
    private void field(Cast c, Location at, double radius, int duration, int pulses, double pull, boolean healing, boolean follows, boolean sanctuary) {
        field(c, at, radius, duration, pulses, pull, healing, follows, sanctuary, 1);
    }
    private void field(Cast c, Location at, double radius, int duration, int pulses, double pull, boolean healing, boolean follows, boolean sanctuary, double fraction) {
        int interval = Math.max(1, duration / pulses);
        EffectEngine.Effect e = effect(c, duration, (active, age) -> {
            Location center = follows ? c.player.getLocation() : at;
            if (!context.loaded(center)) return false;
            c.art.field(center,radius,age,duration);
            if (age % 4 == 0) outline(c, center, radius);
            if (age % interval != 0 || age / interval >= pulses) return true;
            if (healing) healArea(c, center, radius, c.spell.multiplier() / pulses);
            else for (LivingEntity target : nearby(c, center, radius, false)) {
                hit(c, target, fraction / pulses, true);
                if (lastHitAccepted && permitted(c, target)) {
                    if (pull > 0 && !isBoss(target)) {
                        Vector delta = center.toVector().subtract(target.getLocation().toVector()).setY(.08);
                        if (delta.lengthSquared() > .04) context.velocity(target, delta.normalize().multiply(pull));
                    }
                    if (c.spell.id().equals("quicksand")) slow(c, target, .35, 25);
                    if (c.spell.id().equals("blizzard")) slow(c, target, .2, 25);
                    if (c.spell.id().equals("cyclone_trap")) spread(c, target);
                }
            }
            if (sanctuary) for (LivingEntity target : nearby(c, center, radius, true)) if (target instanceof Player ally) buff(c, ally, "reduction", 25, .1);
            return true;
        });
        e.onClose(()->{c.art.clear("field");c.art.clear("boundary");});
        if (healing) unique(c, "healing:" + c.spell.id(), e);
    }
    private void meteors(Cast c, Location center) {
        // Six smaller impacts share 60%; the final central meteor spends 40%.
        EffectEngine.Effect active = effect(c, 141, (e, age) -> {
            if (!context.loaded(center)) return false;
            c.art.meteors(center,age);
            if (age % 18 == 0 && age <= 90)
                heavenlySound(center, Sound.ENTITY_BLAZE_SHOOT, .85f, 1.1f + age * .004f);
            boolean small = age >= 10 && (age - 10) % 18 == 0 && age <= 100;
            if (!small && age != 120) return true;
            int index = small ? (age - 10) / 18 : 0;
            double angle = index * Math.PI / 3;
            Location at = small ? center.clone().add(Math.cos(angle) * 2.8, 0, Math.sin(angle) * 2.8) : center;
            if (!context.loaded(at)) return false;
            area(c, at, small ? 4 : 6, small ? .10 : .40, true, false);
            heavenlySound(at, Sound.ENTITY_GENERIC_EXPLODE, small ? .85f : 1.8f, small ? .95f : .55f);
            return true;
        });
        legendaryLifetime(c, active);
    }
    private void wave(Cast c, double distance, double width, double fraction, boolean knockback) {
        Location start = c.player.getLocation().clone().add(0, .7, 0);
        Vector forward = start.getDirection().setY(0); if (forward.lengthSquared() < .01) forward = new Vector(0, 0, 1); forward.normalize();
        Set<UUID> hit = new HashSet<>(); Vector direction = forward;
        effect(c, (int)distance + 1, (e, age) -> {
            Location front = start.clone().add(direction.clone().multiply(age));
            if (!context.loaded(front) || !visible(c.player, start, front)) return false;
            c.art.wave(front,direction,width,0);
            if(age%4==0)outline(c, front, width);
            for (LivingEntity target : nearby(c, front, width, false)) {
                if (!hit.add(target.getUniqueId())) continue;
                hit(c, target, fraction, true);
                if (lastHitAccepted && knockback && permitted(c, target) && !isBoss(target)) context.velocity(target, direction.clone().multiply(1.1).setY(.3));
            }
            return true;
        });
    }
    private void repeatedWaves(Cast c, int count, int interval, double distance, double width) {
        List<Set<UUID>> visited = new ArrayList<>();
        List<Location> starts = new ArrayList<>(); List<Vector> directions = new ArrayList<>();
        for (int i = 0; i < count; i++) { visited.add(new HashSet<>()); starts.add(null); directions.add(null); }
        heavenlySound(c.player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, .85f, 1.4f);
        EffectEngine.Effect active = effect(c, (count - 1) * interval + (int)distance + 1, (e, age) -> {
            for (int i = 0; i < count; i++) {
                int progress = age - i * interval;
                if (progress < 0 || progress > distance) continue;
                if (progress == 0) {
                    starts.set(i, c.player.getLocation().clone().add(0, .7, 0));
                    Vector direction = c.player.getLocation().getDirection().setY(0);
                    if (direction.lengthSquared() < .001) direction = new Vector(0, 0, 1);
                    directions.set(i, direction.normalize());
                    heavenlySound(starts.get(i), Sound.ENTITY_GENERIC_SPLASH, 1.1f, .75f + i * .1f);
                }
                Location front = starts.get(i).clone().add(directions.get(i).clone().multiply(progress));
                if (!context.loaded(front) || !visible(c.player, starts.get(i), front)) continue;
                c.art.wave(front,directions.get(i),width,i);
                if(progress%4==0)outline(c, front, width);
                for (LivingEntity target : nearby(c, front, width, false)) {
                    if (!visited.get(i).add(target.getUniqueId())) continue;
                    hit(c, target, 1.0 / count, true);
                    if (lastHitAccepted && permitted(c, target) && !isBoss(target)) {
                        context.velocity(target, directions.get(i).clone().multiply(.9).setY(.2));
                        slow(c, target, .25, 28);
                    }
                }
            }
            return true;
        });
        legendaryLifetime(c, active);
    }
    private void storm(Cast c, Location at) {
        EffectEngine.Effect active = effect(c, 121, (e, age) -> {
            if (!context.loaded(at)) return false;
            c.art.storm(at,age);
            if (age % 4 == 0) outline(c, at, 6);
            if (age <= 100 && age % 20 == 0) {
                List<LivingEntity> targets = nearby(c, at, 6, false);
                Location previous = at.clone().add(0, 8, 0);
                int chained = 0;
                for (LivingEntity target : targets) {
                    hit(c, target, age == 100 ? .4 : .6 / 5, true);
                    if (!lastHitAccepted || !permitted(c, target)) continue;
                    if (chained++ < 4) { beam(c, previous, target.getEyeLocation()); previous = target.getEyeLocation(); }
                    if (isBoss(target)) addStagger(c, target, 8);
                    else { freeze(c, target, 6); slow(c, target, .4, 8); }
                }
                heavenlySound(at, Sound.BLOCK_NOTE_BLOCK_BASEDRUM, 1.15f, .65f + age * .006f);
                if (age == 100) {
                    pillar(c, at, 12);
                    heavenlySound(at, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.7f, .7f);
                } else heavenlySound(at, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, .8f, 1.3f);
            }
            return true;
        });
        legendaryLifetime(c, active);
    }

    private static final class DamageBudget {
        final LivingEntity target;
        final double remaining;
        boolean accepted;
        DamageBudget(LivingEntity target, double remaining) { this.target = target; this.remaining = remaining; }
    }
    private DamageBudget activeBudget;
    private boolean damageAccepted, lastHitAccepted;
    private double rawDamage(Cast c, LivingEntity target, double amount) {
        damageAccepted = false;
        if (amount <= 0 || !permitted(c, target)) return 0;
        double remaining = Double.POSITIVE_INFINITY;
        if (isBoss(target)) {
            remaining = maxHealth(target) * configured("boss-damage-cap-percent", 5, .1, 25) / 100 - c.bossSpent.getOrDefault(target.getUniqueId(), 0.0);
            if (remaining <= .0001) return 0;
            amount = Math.min(amount, remaining);
        }
        DamageBudget previous = activeBudget, budget = new DamageBudget(target, remaining); activeBudget = budget;
        double dealt;
        try { dealt = context.damage(c.player, target, amount, DamageType.MAGIC); }
        finally { activeBudget = previous; damageAccepted = budget.accepted; }
        if (budget.accepted) { plugin.mana().markCombat(c.player); if (target instanceof Player p) plugin.mana().markCombat(p); if(dealt>0)c.art.impact(target); }
        if (isBoss(target)) c.bossSpent.merge(target.getUniqueId(), dealt, Double::sum);
        return dealt;
    }
    private double hit(Cast c, LivingEntity target, double fraction, boolean statuses) {
        lastHitAccepted = false;
        if (!permitted(c, target)) return 0;
        boolean first = statuses && !c.statusApplied.contains(target.getUniqueId());
        String id = c.spell.id(); double bonus = 0;
        if (first) {
            if ((id.equals("inferno_spear") || id.equals("magma_eruption")) && stacks(target, "burn") >= 5) bonus += .8;
            if (id.equals("divine_judgment") && hasStatus(target, "radiance")) bonus += .8;
            if (id.equals("abyssal_collapse")) bonus += Math.min(1, stacks(target, "curse") * .2);
            if (id.equals("absolute_zero")) bonus += Math.min(1, stacks(target, "frost") * .2);
            if (c.spell.element() == Element.WIND && stacks(target, "air") >= 3) bonus += .6;
        }
        double vulnerability = c.spell.element() == Element.ICE && hasStatus(target, "frost_vulnerability") ? 1.1 : 1;
        double dealt = rawDamage(c, target, c.power * (c.spell.multiplier() * fraction + bonus) * vulnerability);
        boolean accepted = damageAccepted;
        if (first && accepted && !target.isDead()) {
            c.statusApplied.add(target.getUniqueId());
            if ((id.equals("inferno_spear") || id.equals("magma_eruption")) && stacks(target, "burn") >= 5) remove(target, "burn");
            if (id.equals("divine_judgment")) remove(target, "radiance");
            if (id.equals("abyssal_collapse")) remove(target, "curse");
            if (id.equals("absolute_zero")) remove(target, "frost");
            if (c.spell.element() == Element.WIND && stacks(target, "air") >= 3) { remove(target, "air"); slow(c, target, .15, 40); }
            reaction(c, target);
            switch (c.spell.element()) {
                case FIRE -> add(c, target, "burn", 1, 5, id.equals("fireball") ? 60 : 80);
                case WATER -> put(c, target, "wet", 1, 100);
                case ICE -> { frost(c, target, 1); slow(c, target, .2, 60); }
                case WIND -> { if (Set.of("wind_cutter", "updraft", "cyclone_trap").contains(id)) add(c, target, "air", 1, 3, 120); }
                case EARTH -> { put(c, target, "earth", 1, 100); addStagger(c, target, id.equals("seismic_slam") ? 35 : 20); }
                case LIGHT -> { if (id.equals("radiant_bolt")) put(c, target, "radiance", 1, 120); }
                case DARK -> { if (!id.equals("abyssal_collapse")) add(c, target, "curse", 1, 5, 160); }
                case LIGHTNING -> {
                    add(c, target, "shock", 1, 3, 100);
                    if (stacks(target, "shock") >= 3 && reactionReady(target, "discharge")) {
                        remove(target, "shock"); rawDamage(c, target, c.power * .7);
                        if (isBoss(target)) addStagger(c, target, 25); else freeze(c, target, 10);
                    }
                }
            }
            Shadow shadow = shadows.get(c.player.getUniqueId());
            if (shadow != null && !c.spell.ultimate() && c.spell.multiplier() > 0
                    && !Set.of("shadow_doppelganger", "golem_guardian", "healing_spring", "rain_blessing", "holy_sanctuary", "aqua_shield").contains(id)
                    && shadow.remaining > 0 && context.loaded(shadow.entity.getLocation())
                    && visible(c.player, shadow.entity.getEyeLocation(), target.getEyeLocation())) {
                double echo = Math.min(shadow.remaining, c.power * c.spell.multiplier() * .35);
                shadow.remaining -= echo;
                beam(shadow.cast, shadow.entity.getEyeLocation(), target.getEyeLocation());
                // Uses its summon cast budget; raw damage never calls hit(), so echoes cannot echo or react.
                rawDamage(shadow.cast, target, echo);
            }
        }
        lastHitAccepted = accepted;
        return dealt;
    }
    private void put(Cast c, LivingEntity target, String key, int stacks, int duration) {
        State s = state(target); Mark previous = mark(target, key);
        Mark next = new Mark(stacks, clock + duration + (key.equals("burn") ? 1 : 0), c);
        if (key.equals("burn")) next.nextTick = previous == null ? clock + 20 : previous.nextTick;
        s.marks.put(key, next);
        c.art.status(target,key,stacks,duration,()->hasStatus(target,key));
    }
    private void add(Cast c, LivingEntity target, String key, int amount, int maximum, int duration) {
        put(c, target, key, Math.min(maximum, stacks(target, key) + amount), duration);
    }
    private void remove(LivingEntity target, String key) { State s = states.get(target.getUniqueId()); if (s != null) s.marks.remove(key); }
    private boolean reactionReady(LivingEntity target, String reaction) {
        State s = state(target);
        if (s.reactions.getOrDefault(reaction, 0L) > clock) return false;
        s.reactions.put(reaction, clock + 40); return true;
    }
    private void reaction(Cast c, LivingEntity target) {
        Element element = c.spell.element();
        if (element == Element.WIND && hasStatus(target, "burn") && reactionReady(target, "wildfire")) {
            rawDamage(c, target, c.power * .6); spread(c, target);
        }
        if (element == Element.LIGHTNING && hasStatus(target, "wet") && reactionReady(target, "electro_charged")) {
            int count = 0;
            for (LivingEntity e : nearby(c, target.getLocation(), 4, false)) {
                if (count++ >= 3) break;
                beam(c, target.getEyeLocation(), e.getEyeLocation()); rawDamage(c, e, c.power * .7);
            }
        }
        if (element == Element.ICE && hasStatus(target, "wet") && reactionReady(target, "flash_freeze")) {
            rawDamage(c, target, c.power * .4); frost(c, target, 2);
        }
        if (((element == Element.EARTH && hasStatus(target, "burn")) || (element == Element.FIRE && hasStatus(target, "earth"))) && reactionReady(target, "molten_core")) {
            Location center = target.getLocation().clone();
            if (plugin.effects().hasCapacity()) effect(c, 61, (e, age) -> {
                if (!context.loaded(center)) return false;
                if (age % 5 == 0) ring(c, center, 2.5);
                if (age % 20 == 0) for (LivingEntity enemy : nearby(c, center, 2.5, false)) {
                    String key = "molten:" + enemy.getUniqueId();
                    double spent = c.reactionSpent.getOrDefault(key, 0.0), remaining = Math.max(0, c.power * .8 - spent);
                    double amount = Math.min(remaining, c.power * .8 / 4);
                    if (amount > 0) { rawDamage(c, enemy, amount); if (damageAccepted) c.reactionSpent.put(key, spent + amount); }
                }
                return true;
            });
        }
        if (element == Element.LIGHT && hasStatus(target, "curse") && reactionReady(target, "eclipse")) {
            rawDamage(c, target, c.power); put(c, target, "healing_reduction", 1, 80); remove(target, "curse");
        }
        if (((element == Element.FIRE && hasStatus(target, "wet")) || (element == Element.WATER && hasStatus(target, "burn"))) && reactionReady(target, "steam")) {
            rawDamage(c, target, c.power * .3); remove(target, "burn"); remove(target, "wet"); context.particles(target.getEyeLocation(), Particle.CLOUD, 12, .4);
        }
        if (((element == Element.WIND && hasStatus(target, "shock")) || (element == Element.LIGHTNING && hasStatus(target, "air"))) && reactionReady(target, "thunderstorm")) {
            rawDamage(c, target, c.power * .4); addStagger(c, target, 15);
        }
        if (((element == Element.EARTH && stacks(target, "frost") > 0) || (element == Element.ICE && hasStatus(target, "earth"))) && reactionReady(target, "permafrost")) {
            rawDamage(c, target, c.power * .3); slow(c, target, .35, 60);
        }
    }
    private void spread(Cast c, LivingEntity source) {
        if (!reactionReady(source, "spread")) return;
        for (LivingEntity e : nearby(c, source.getLocation(), 3, false)) {
            if (e == source || !permitted(c, e)) continue;
            for (String key : List.of("burn", "wet", "shock")) {
                Mark m = mark(source, key);
                if (m != null) put(c, e, key, Math.max(stacks(e, key), m.stacks), (int)Math.min(80, m.until - clock));
            }
        }
    }
    private void frost(Cast c, LivingEntity target, int amount) {
        add(c, target, "frost", amount, 5, 120);
        if (stacks(target, "frost") < 5) return;
        remove(target, "frost");
        if (isBoss(target)) put(c, target, "frost_vulnerability", 1, 80);
        else freeze(c, target, 40);
    }
    private void freeze(Cast c, LivingEntity target, int duration) {
        if (!permitted(c, target)) return;
        if (isBoss(target)) { addStagger(c, target, 15); return; }
        if (hasStatus(target, "freeze_immune")) return;
        put(c, target, "frozen", 1, duration); put(c, target, "freeze_immune", 1, duration + 160);
        state(target).anchor = target.getLocation().clone();
        c.art.cage(target,duration,()->hasStatus(target,"frozen"));
        context.velocity(target, new Vector());
    }
    private void addStagger(Cast c, LivingEntity target, int amount) {
        if (!isBoss(target) || hasStatus(target, "stagger_immune")) return;
        add(c, target, "stagger", amount, 100, 200);
        if (stacks(target, "stagger") < 100) return;
        remove(target, "stagger"); put(c, target, "break", 1, 60); put(c, target, "stagger_immune", 1, 160);
        ring(c, target.getLocation(), 2);
    }
    private void slow(Cast c, LivingEntity target, double amount, int duration) {
        State s = state(target); Mark existing = mark(target, "slow");
        if (existing != null && s.slow > amount) return;
        put(c, target, "slow", 1, duration); s.slow = amount;
        AttributeInstance movement = target.getAttribute(Attribute.MOVEMENT_SPEED);
        if (movement == null) return;
        movement.removeModifier(slowKey);
        movement.addTransientModifier(new AttributeModifier(slowKey, -bounded(amount, 0, .8), AttributeModifier.Operation.MULTIPLY_SCALAR_1));
    }
    private void clearSlow(State s) {
        AttributeInstance movement = s.entity.getAttribute(Attribute.MOVEMENT_SPEED);
        if (movement != null) movement.removeModifier(slowKey);
        s.slow = 0;
    }
    private void push(Cast c, LivingEntity target, Location from, double force) {
        Vector direction = target.getLocation().toVector().subtract(from.toVector()).setY(.15);
        if (direction.lengthSquared() < .01) direction = c.player.getLocation().getDirection().setY(.2);
        context.velocity(target, direction.normalize().multiply(force));
    }

    private static double maxHealth(LivingEntity entity) {
        AttributeInstance attribute = entity.getAttribute(Attribute.MAX_HEALTH);
        return attribute == null ? 20 : Math.max(1, attribute.getValue());
    }
    private void buff(Cast c, LivingEntity target, String key, int duration, double amount) {
        if (!context.ally(c.player, target) || !context.affect(c.player, target, null)) return;
        State s = state(target); Buff previous = s.buffs.get(key);
        // A weaker application cannot extend the stronger value indefinitely.
        if (previous == null || previous.until <= clock || amount >= previous.value) {
            s.buffs.put(key, new Buff(clock + duration, amount));
            c.art.buff(target,key,duration,()->buffValue(target,key)>0);
        }
        ring(c, target.getLocation(), 1.2);
    }
    private void shield(Cast c, LivingEntity target, int duration, double amount) { buff(c, target, "shield", duration, amount); }
    private double buffValue(LivingEntity target, String key) {
        State s = states.get(target.getUniqueId()); Buff b = s == null ? null : s.buffs.get(key);
        return b != null && b.until > clock ? b.value : 0;
    }
    private void healArea(Cast c, Location center, double radius, double multiplier) {
        for (LivingEntity entity : nearby(c, center, radius, true)) {
            if (!(entity instanceof Player player) || !context.affect(c.player, player, null)) continue;
            String pulse = c.spell.id() + ":" + player.getUniqueId();
            int lock = (int)configured("healing-pulse-lock-ticks", 15, 1, 20);
            if (healingPulses.getOrDefault(pulse, Long.MIN_VALUE / 2) > clock - lock) continue;
            healingPulses.put(pulse, clock); heal(c, player, c.healing * multiplier);
        }
    }
    private void heal(Cast c, Player player, double amount) {
        if (!c.alive() || player.isDead() || !context.ally(c.player, player) || !context.affect(c.player, player, null)) return;
        double limit = maxHealth(player) * configured("healing-per-cast-percent", 35, 1, 100) / 100;
        double remaining = limit - c.healed.getOrDefault(player.getUniqueId(), 0.0);
        if (remaining <= 0) return;
        double before = player.getHealth(); context.heal(player, Math.min(remaining, Math.max(0, amount)));
        c.healed.merge(player.getUniqueId(), Math.max(0, player.getHealth() - before), Double::sum);
        if(player.getHealth()>before)c.art.heal(player);
    }
    private void cleanse(Player player) {
        State s = states.get(player.getUniqueId()); int remaining = 2;
        if (s != null) for (String key : List.of("burn", "curse_mark", "curse", "shock", "frost", "slow", "wet")) {
            if (remaining == 0) break;
            if (CLEANSE.contains(key) && s.marks.remove(key) != null) { if (key.equals("slow")) clearSlow(s); remaining--; }
        }
        for (PotionEffect effect : List.copyOf(player.getActivePotionEffects())) {
            if (remaining == 0) break;
            if (CLEANSE_POTIONS.contains(effect.getType())) { player.removePotionEffect(effect.getType()); remaining--; }
        }
        context.particles(player.getEyeLocation(), Particle.END_ROD, 20, .5);
    }
    private void beam(Cast c, Location from, Location to) {
        if(c.art.enabled()){c.art.beam(from,to);return;}
        if (from.getWorld() != to.getWorld()) return;
        Vector delta = to.toVector().subtract(from.toVector()); int count = Math.min(40, Math.max(1, (int)(delta.length() * 2)));
        for (int i = 0; i <= count; i++) {
            Location at = from.clone().add(delta.clone().multiply((double)i / count));
            if (!context.loaded(at)) break;
            at.getWorld().spawnParticle(Particle.DUST, at, 1, 0, 0, 0, 0, new Particle.DustOptions(Color.fromRGB(c.spell.element().rgb), 1.15f));
            if(i%3==0)accent(c,at);
        }
    }
    private void ring(Cast c, Location at, double radius) {
        if(c.art.enabled())c.art.area(at,radius);
        outline(c,at,radius);
    }
    private void outline(Cast c, Location at, double radius) {
        if(c.art.enabled()){c.art.boundary(at,radius);return;}
        if (!context.loaded(at)) return;
        for (int i = 0; i < 24; i++) {
            double angle = i * Math.PI / 12;
            Location point = at.clone().add(Math.cos(angle) * radius, .12, Math.sin(angle) * radius);
            if (context.loaded(point)) point.getWorld().spawnParticle(Particle.DUST, point, 1, 0, 0, 0, 0, new Particle.DustOptions(Color.fromRGB(c.spell.element().rgb), 1.1f));
            if(context.loaded(point)&&i%3==0)accent(c,point);
        }
    }
    /** Element-specific accents are all vanilla Bedrock-visible particles. */
    private void accent(Cast c,Location at){
        switch(c.spell.element()){
            case FIRE -> at.getWorld().spawnParticle(Particle.FLAME,at,1,.025,.025,.025,.005);
            case WATER -> at.getWorld().spawnParticle(Particle.SPLASH,at,2,.08,.06,.08,.01);
            case ICE -> at.getWorld().spawnParticle(Particle.SNOWFLAKE,at,1,.03,.03,.03,0);
            case WIND -> at.getWorld().spawnParticle(Particle.CLOUD,at,1,.04,.04,.04,.005);
            case EARTH -> at.getWorld().spawnParticle(Particle.BLOCK,at,2,.06,.04,.06,.03,Material.MOSS_BLOCK.createBlockData());
            case LIGHT -> at.getWorld().spawnParticle(Particle.END_ROD,at,1,.025,.025,.025,.005);
            case DARK -> at.getWorld().spawnParticle(Particle.REVERSE_PORTAL,at,2,.04,.04,.04,.01);
            case LIGHTNING -> at.getWorld().spawnParticle(Particle.ELECTRIC_SPARK,at,2,.04,.04,.04,.02);
        }
    }
    private void pillar(Cast c, Location at, double height) { if(c.art.enabled())c.art.pillar(at,height);else beam(c, at, at.clone().add(0, height, 0)); ring(c, at, 2); }
    private void sound(Location at, Sound sound, float volume) { if (context.loaded(at)) at.getWorld().playSound(at, sound, volume, 1); }
    private void bubble(LivingEntity target) { context.particles(target.getLocation().add(0, 1, 0), Particle.CLOUD, 18, .8); }

    private boolean stoneWall(Cast c) {
        Location base = c.player.getLocation().clone(); Vector forward = base.getDirection().setY(0);
        if (forward.lengthSquared() < .01) return false;
        forward.normalize(); base.add(forward.clone().multiply(3));
        // Axis aligned authored display wall, with independent player/projectile collision.
        boolean northSouth = Math.abs(forward.getZ()) >= Math.abs(forward.getX());
        double x = Math.floor(base.getX()), y = Math.floor(base.getY()), z = Math.floor(base.getZ());
        BoundingBox box = northSouth ? new BoundingBox(x - 2, y, z, x + 3, y + 3, z + 1)
                : new BoundingBox(x, y, z - 2, x + 1, y + 3, z + 3);
        Wall wall = new Wall(c.player, box);
        boolean nativeWall = c.art.enabled() && !plugin.getConfig().getString("elemental-visuals.style", "native").equalsIgnoreCase("authored");
        List<Location> cells = new ArrayList<>();
        for (int lateral = -2; lateral <= 2; lateral++) for (int rise = 0; rise < 3; rise++) {
            Location cell = new Location(base.getWorld(), x + (northSouth ? lateral : 0), y + rise, z + (northSouth ? 0 : lateral));
            if (!context.loaded(cell) || !cell.getBlock().isPassable()) return false;
            cells.add(cell);
        }
        // Do not create a barrier intersecting any living body.
        if (base.getWorld().getNearbyEntities(box).stream().anyMatch(e -> e instanceof LivingEntity && !(e instanceof ArmorStand))) return false;
        EffectEngine.Effect active = effect(c, 120, (e, age) -> {
            if (!context.loaded(base)) return false;
            if(age%4==0)c.art.wall(box);
            // Geyser does not render display entities. This visible outline is also
            // sent to Bedrock and must remain alongside the Java stone model.
            if (!nativeWall && age % 5 == 0) for (Location cell : cells) {
                for (double height : new double[]{.08, .92}) {
                    Location point = cell.clone().add(.5, height, .5);
                    point.getWorld().spawnParticle(Particle.DUST, point, 2, .42, .01, .42, 0, new Particle.DustOptions(Color.fromRGB(165, 154, 135), 1.6f));
                }
            }
            for (Entity entity : wall.world.getNearbyEntities(wall.box.clone().expand(4))) {
                if (!(entity instanceof Projectile projectile)) continue;
                if (projectile.getShooter() instanceof LivingEntity shooter && context.ally(c.player, shooter)) continue;
                Vector velocity = projectile.getVelocity(); double distance = velocity.length();
                boolean collision = wall.box.overlaps(projectile.getBoundingBox());
                if (!collision && distance > .001) collision = wall.box.rayTrace(projectile.getLocation().toVector(), velocity.clone().normalize(), distance + .3) != null;
                if (collision) { context.particles(projectile.getLocation(), Particle.SMOKE, 4, .1); projectile.remove(); }
            }
            return true;
        });
        active.onClose(() -> { walls.remove(wall); wall.displays.forEach(Entity::remove); c.art.clear("wall"); });
        walls.add(wall);
        if (nativeWall) c.art.wall(box);
        else for (Location cell : cells) {
            BlockDisplay display = active.track(cell.getWorld().spawn(cell, BlockDisplay.class, entity -> {
                entity.setBlock(Material.STONE_BRICKS.createBlockData()); entity.setPersistent(false); entity.setInvulnerable(true);
            }));
            wall.displays.add(display);
        }
        unique(c, "wall", active); sound(base, Sound.BLOCK_STONE_PLACE, .8f); return true;
    }
    private boolean golem(Cast c) {
        Location at = dashPoint(c.player, 2.5); if (at == null || !context.safeBody(at.clone().add(0, 1.2, 0))) return false;
        IronGolem guardian = at.getWorld().spawn(at, IronGolem.class, entity -> {
            entity.setAI(false); entity.setInvulnerable(true); entity.setSilent(true); entity.setCollidable(false);
            entity.setPersistent(false); entity.setRemoveWhenFarAway(false); entity.setPlayerCreated(true);
            entity.getPersistentDataContainer().set(summonKey, PersistentDataType.STRING, c.player.getUniqueId().toString());
            entity.getPersistentDataContainer().set(new NamespacedKey(plugin, "allied_vex"), PersistentDataType.STRING, c.player.getUniqueId().toString());
        });
        EffectEngine.Effect active = effect(c, 300, (e, age) -> {
            if (!guardian.isValid() || !context.loaded(guardian.getLocation())) return false;
            c.art.summon(guardian,age);
            double range = configured("summon-goal-range", 12, 4, 16);
            LivingEntity target = nearby(c, guardian.getLocation(), range, false).stream().findFirst().orElse(null);
            if (target == null) return true;
            if (age % 5 == 0 && guardian.getLocation().distanceSquared(target.getLocation()) > 6.25) {
                Vector step = target.getLocation().toVector().subtract(guardian.getLocation().toVector()).setY(0);
                if (step.lengthSquared() > .01) {
                    Location next = guardian.getLocation().add(step.normalize().multiply(.65));
                    next.setDirection(target.getLocation().toVector().subtract(next.toVector()));
                    if (context.safeBody(next) && context.safeBody(next.clone().add(0, 1.2, 0))) guardian.teleport(next);
                }
            }
            if (age % 20 == 0 && guardian.getLocation().distanceSquared(target.getLocation()) <= 9) {
                guardian.swingMainHand(); hit(c, target, 1.0 / 15, false); ring(c, guardian.getLocation(), 2);
            }
            return true;
        });
        active.track(guardian);active.onClose(()->c.art.clear("summon:"+guardian.getUniqueId())); unique(c, "golem", active); return true;
    }
    private boolean shadow(Cast c) {
        Location at = dashPoint(c.player, 1.8); if (at == null) return false;
        ArmorStand entity = at.getWorld().spawn(at, ArmorStand.class, stand -> {
            stand.setGravity(false); stand.setInvulnerable(true); stand.setVisible(true); stand.setArms(true); stand.setBasePlate(false);
            stand.setMarker(true); stand.setPersistent(false); stand.setSilent(true);
            stand.getEquipment().setHelmet(new ItemStack(Material.WITHER_SKELETON_SKULL));
            stand.getEquipment().setChestplate(new ItemStack(Material.LEATHER_CHESTPLATE));
            stand.getEquipment().setLeggings(new ItemStack(Material.LEATHER_LEGGINGS));
            stand.getEquipment().setBoots(new ItemStack(Material.LEATHER_BOOTS));
            stand.getEquipment().setItemInMainHand(new ItemStack(Material.BLAZE_ROD));
            stand.getPersistentDataContainer().set(summonKey, PersistentDataType.STRING, c.player.getUniqueId().toString());
        });
        Shadow shadow = new Shadow(entity, c); shadows.put(c.player.getUniqueId(), shadow);
        EffectEngine.Effect active = effect(c, 160, (e, age) -> {
            if (!entity.isValid() || !context.loaded(entity.getLocation())) return false;
            c.art.summon(entity,age);
            if (age % 5 == 0) {
                Location next = c.player.getLocation().clone().add(c.player.getLocation().getDirection().setY(0).multiply(-1.2));
                if (context.safeBody(next)) entity.teleport(next);
                context.particles(entity.getLocation().add(0, 1, 0), Particle.SMOKE, 3, .3);
            }
            return true;
        });
        active.track(entity); active.onClose(() -> {shadows.remove(c.player.getUniqueId(), shadow);c.art.clear("summon:"+entity.getUniqueId());}); unique(c, "shadow", active); return true;
    }

    /** Called once per server tick, separately from EffectEngine.tick(). */
    public void tick() {
        visuals.tick();
        clock++;
        for (State s : List.copyOf(states.values())) {
            LivingEntity entity = s.entity;
            if (!entity.isValid() || entity.isDead() || (entity instanceof Player p && !p.isOnline())) { clearSlow(s); states.remove(entity.getUniqueId()); continue; }
            s.marks.entrySet().removeIf(e -> e.getValue().until <= clock);
            s.buffs.entrySet().removeIf(e -> e.getValue().until <= clock || e.getValue().value <= 0);
            s.reactions.entrySet().removeIf(e -> e.getValue() <= clock);
            if (!s.marks.containsKey("slow") && s.slow > 0) clearSlow(s);
            if (hasStatus(entity, "frozen") && s.anchor != null && s.anchor.getWorld() == entity.getWorld()) {
                entity.setFallDistance(0);
                if (!(entity instanceof Player) && context.loaded(s.anchor) && context.safeBody(s.anchor)
                        && entity.getLocation().distanceSquared(s.anchor) > .01) entity.teleport(s.anchor);
                context.velocity(entity, new Vector());
            }
            Mark burn = mark(entity, "burn");
            if (burn != null && clock >= burn.nextTick && burn.source != null) {
                burn.nextTick = clock + 20;
                rawDamage(burn.source, entity, burn.source.power * .05 * burn.stacks);
            }
            if (s.marks.isEmpty() && s.buffs.isEmpty() && s.reactions.isEmpty()) states.remove(entity.getUniqueId());
        }
        if (clock % 200 == 0) healingPulses.entrySet().removeIf(e -> e.getValue() < clock - 40);
    }
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void damage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof LivingEntity target)) return;
        double multiplier = (hasStatus(target, "curse_mark") ? 1.08 : 1) * (hasStatus(target, "break") ? 1.1 : 1);
        double reduction = buffValue(target, "reduction");
        if (event instanceof EntityDamageByEntityEvent by && by.getDamager() instanceof Projectile) reduction = Math.max(reduction, buffValue(target, "projectile_reduction"));
        event.setDamage(event.getDamage() * multiplier * (1 - Math.min(.5, reduction)));
        double shield = buffValue(target, "shield"), finalDamage = event.getFinalDamage();
        if (shield > 0 && finalDamage > 0) {
            Buff b = state(target).buffs.get("shield");
            if (shield >= finalDamage) { b.value -= finalDamage; event.setCancelled(true); return; }
            double before = finalDamage;
            event.setDamage(event.getDamage() * Math.max(0, (finalDamage - shield) / finalDamage));
            b.value = Math.max(0, shield - Math.max(0, before - event.getFinalDamage()));
        }
        if (activeBudget != null && activeBudget.target == target && Double.isFinite(activeBudget.remaining)
                && event.getFinalDamage() > activeBudget.remaining && event.getFinalDamage() > 0) {
            event.setDamage(event.getDamage() * activeBudget.remaining / event.getFinalDamage());
        }
    }
    @EventHandler(priority = EventPriority.MONITOR)
    public void damageResult(EntityDamageEvent event) {
        if (activeBudget != null && activeBudget.target == event.getEntity()) activeBudget.accepted = !event.isCancelled();
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void phoenix(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || event.getFinalDamage() < player.getHealth()
                || event.getCause() == EntityDamageEvent.DamageCause.VOID || event.getCause() == EntityDamageEvent.DamageCause.KILL) return;
        if (plugin.casts() != null && !plugin.casts().canUseElemental(player)) return;
        if (player.isPermissionSet("advance-magic.cast") && !player.hasPermission("advance-magic.cast")) return;
        ElementalSpell phoenix = ElementalSpell.parse("phoenix_rebirth");
        ItemStack staff = player.getInventory().getItemInMainHand();
        if (!plugin.staffSpells().spells(staff).contains(phoenix) || plugin.wands().usesLeft(staff) <= 0 || !plugin.effects().hasCapacity()) return;
        ElementalCastEvent castEvent = new ElementalCastEvent(player, phoenix); Bukkit.getPluginManager().callEvent(castEvent);
        if (castEvent.isCancelled() || !context.affect(player, player, null)) return;
        int cost = Math.max(1, (int)Math.ceil(phoenix.mana() * (1 - bounded(plugin.elementalStats().snapshot(player).manaReduction(), 0, ElementalStats.MAX_REDUCTION))));
        if (!plugin.mana().reserveElementalPassive(player, phoenix, cost, System.currentTimeMillis(), plugin.wands().cooldownScale(staff))) return;
        if (plugin.wands().consumeUse(staff)) player.getInventory().setItemInMainHand(staff);
        event.setCancelled(true); player.setFireTicks(0); player.setFallDistance(0);
        player.setHealth(Math.min(1, player.getHealth()));
        context.heal(player, Math.max(0, maxHealth(player) * configured("phoenix-health-percent", 25, 1, 100) / 100 - player.getHealth()));
        Cast c = new Cast(player, phoenix, castEvent.getDamageMultiplier());
        area(c, player.getLocation(), 5, 1, true, false);
        c.art.phoenix();
        player.sendActionBar(ChatColor.GOLD + "Phoenix Rebirth · ฟื้นคืนชีพ!"); sound(player.getLocation(), Sound.ITEM_TOTEM_USE, 1);
    }
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void healing(EntityRegainHealthEvent event) { if (event.getEntity() instanceof LivingEntity living && hasStatus(living, "healing_reduction")) event.setAmount(event.getAmount() * .7); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void move(PlayerMoveEvent event) {
        if (event.getTo() == null || event instanceof PlayerTeleportEvent || event.getFrom().getWorld() != event.getTo().getWorld()) return;
        Player p = event.getPlayer();
        if (hasStatus(p, "frozen") && event.getFrom().distanceSquared(event.getTo()) > .000001) {
            Location fixed = event.getFrom().clone(); fixed.setYaw(event.getTo().getYaw()); fixed.setPitch(event.getTo().getPitch()); event.setTo(fixed); return;
        }
        Location from = event.getFrom().clone().add(0, .9, 0), to = event.getTo().clone().add(0, .9, 0);
        if (wallBlocks(p, from, to)) { Location fixed = event.getFrom().clone(); fixed.setYaw(event.getTo().getYaw()); fixed.setPitch(event.getTo().getPitch()); event.setTo(fixed); }
    }
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void frozenEscape(PlayerTeleportEvent event) {
        if (com.example.advancemagic.effect.StatusService.escapeTeleport(event.getCause()) && hasStatus(event.getPlayer(), "frozen")) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void protectSummon(PlayerArmorStandManipulateEvent event) { if (event.getRightClicked().getPersistentDataContainer().has(summonKey)) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void wallLaunch(ProjectileLaunchEvent event) {
        Projectile projectile = event.getEntity(); Vector velocity = projectile.getVelocity(); double distance = velocity.length();
        for (Wall wall : walls) {
            if (wall.world != projectile.getWorld()) continue;
            if (projectile.getShooter() instanceof LivingEntity shooter && context.ally(wall.owner, shooter)) continue;
            // Also catch unusually fast launched projectiles before their first
            // movement, rather than relying only on proximity in the wall tick.
            if (wall.box.contains(projectile.getLocation().toVector()) || (distance > .001
                    && wall.box.rayTrace(projectile.getLocation().toVector(), velocity.clone().normalize(), distance + .3) != null)) {
                event.setCancelled(true); context.particles(projectile.getLocation(), Particle.SMOKE, 4, .1); return;
            }
        }
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void summonTarget(EntityTargetLivingEntityEvent event) { if (event.getEntity().getPersistentDataContainer().has(summonKey)) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void summonDeath(EntityDeathEvent event) {
        if (event.getEntity().getPersistentDataContainer().has(summonKey)) { event.getDrops().clear(); event.setDroppedExp(0); }
        State s = states.remove(event.getEntity().getUniqueId()); if (s != null) clearSlow(s);
    }
    @EventHandler public void staleSummons(EntitiesLoadEvent event) {
        // Normally nonpersistent entities never reach disk; clean old tagged
        // entities as well if another plugin saved a chunk during a crash.
        for (Entity entity : event.getEntities()) {
            if (entity.getPersistentDataContainer().has(summonKey)) entity.remove();
            if (entity instanceof LivingEntity living) {
                AttributeInstance movement = living.getAttribute(Attribute.MOVEMENT_SPEED);
                if (movement != null) movement.removeModifier(slowKey);
            }
        }
    }
    @EventHandler public void quit(PlayerQuitEvent event) { cleanup(event.getPlayer()); }
    @EventHandler public void death(PlayerDeathEvent event) { cleanup(event.getEntity()); }
    @EventHandler public void changedWorld(PlayerChangedWorldEvent event) { cleanup(event.getPlayer()); }
    private void cleanup(Player player) {
        visuals.closeOwner(player.getUniqueId());
        plugin.effects().closeOwner(player.getUniqueId());
        State s = states.remove(player.getUniqueId()); if (s != null) clearSlow(s);
    }
    @Override public void close() {
        visuals.close();
        for (EffectEngine.Effect effect : List.copyOf(effects)) effect.close();
        states.values().forEach(this::clearSlow); states.clear(); healingPulses.clear(); uniqueEffects.clear(); shadows.clear(); walls.clear();
    }
}
