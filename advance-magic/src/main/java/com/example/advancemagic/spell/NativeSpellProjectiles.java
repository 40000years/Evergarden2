package com.example.advancemagic.spell;

import com.example.advancemagic.elemental.ElementalSpell;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.*;

/** Vanilla projectile bodies following the elemental service's authoritative ray traces.
 * These entities provide appearance and interpolation only; they never resolve a hit.
 */
public final class NativeSpellProjectiles implements Listener, AutoCloseable {
    private final MagicContext context;
    private final NamespacedKey key;
    private final Set<Track> tracks = new HashSet<>();
    private final Map<UUID, Integer> worldCounts = new HashMap<>();
    private long tick;
    private boolean listening = true;

    public NativeSpellProjectiles(MagicContext context) {
        this.context = context;
        key = new NamespacedKey(context.plugin, "native_spell_visual");
        Bukkit.getPluginManager().registerEvents(this, context.plugin);
    }

    public Track begin(Player owner, ElementalSpell spell) {
        // The existing elemental API permits close() to clear effects and then
        // reuses the service. Reinstall guards before any new visual can spawn.
        if (!listening) {
            Bukkit.getPluginManager().registerEvents(this, context.plugin);
            listening = true;
        }
        Track track = new Track(owner, spell);
        tracks.add(track);
        return track;
    }

    public void tick() {
        tick++;
        for (Track track : List.copyOf(tracks)) track.advance();
    }

    @Override public void close() {
        for (Track track : List.copyOf(tracks)) track.close();
        HandlerList.unregisterAll(this);
        listening = false;
    }

    private boolean managed(Entity entity) {
        return entity != null && entity.getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }

    private final class Body {
        final Fireball entity;
        final UUID world;
        long touched = tick;
        Body(Fireball entity) { this.entity = entity; world = entity.getWorld().getUID(); }
        void close() {
            entity.remove();
            worldCounts.computeIfPresent(world, (id, count) -> count <= 1 ? null : count - 1);
        }
    }

    public final class Track implements AutoCloseable {
        private final Player owner;
        private final ElementalSpell spell;
        private final World world;
        private final Map<String, Body> bodies = new HashMap<>();
        private boolean closed;
        private Track(Player owner, ElementalSpell spell) {
            this.owner = owner; this.spell = spell; world = owner.getWorld();
        }
        private boolean alive() {
            return !closed && owner.isOnline() && !owner.isDead() && owner.getWorld() == world;
        }
        private void move(String id, Location at, Vector velocity, boolean large) {
            if (!alive() || at.getWorld() != world || !context.loaded(at)) { remove(id); return; }
            Body body = bodies.get(id);
            if (body != null && !body.entity.isValid()) { remove(id); body = null; }
            if (body == null) {
                int cap = Math.clamp(context.plugin.getConfig().getInt("elemental-visuals.max-native-projectiles-per-world", 48), 0, 128);
                if (worldCounts.getOrDefault(world.getUID(), 0) >= cap) return;
                Class<? extends Fireball> type = large ? LargeFireball.class : SmallFireball.class;
                Fireball entity = world.spawn(at, type, visual -> {
                    visual.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte)1);
                    visual.setShooter(owner);
                    visual.setPersistent(false);
                    visual.setGravity(false);
                    visual.setSilent(true);
                    visual.setInvulnerable(true);
                    visual.setIsIncendiary(false);
                    visual.setYield(0);
                    visual.setAcceleration(new Vector());
                    visual.setVelocity(velocity);
                });
                body = new Body(entity); bodies.put(id, body);
                worldCounts.merge(world.getUID(), 1, Integer::sum);
            } else {
                body.entity.teleport(at);
                body.entity.setAcceleration(new Vector());
                body.entity.setVelocity(velocity);
            }
            body.touched = tick;
        }
        public void projectile(Location from, Location to) {
            if (spell != ElementalSpell.FIREBALL) return;
            // Spawn/move at the current authoritative position; velocity lets the client
            // interpolate toward the next step without deciding combat collisions.
            move("projectile", from, to.toVector().subtract(from.toVector()), false);
        }
        private void meteor(Location center, int index, double progress) {
            if (index > 5) return;
            double angle = index * Math.PI / 3;
            Location at = center.clone().add(Math.cos(angle) * 2.3, 12 * (1 - progress) + .7, Math.sin(angle) * 2.3);
            move("meteor:" + index, at, new Vector(0, -1.2, 0), true);
        }
        public void telegraph(Location center, int age, int duration) {
            if (spell != ElementalSpell.METEOR_RAIN || duration - age > 12) return;
            meteor(center, 0, Math.clamp((12.0 - (duration - age)) / 12, 0, 1));
        }
        public void meteors(Location center, int age) {
            if (spell != ElementalSpell.METEOR_RAIN || age >= 50) return;
            meteor(center, age / 10 + 1, (age % 10) / 10.0);
        }
        public void impact(Location at) {
            if (spell != ElementalSpell.METEOR_RAIN) return;
            for (String id : List.copyOf(bodies.keySet())) {
                Location body = bodies.get(id).entity.getLocation();
                if (Math.hypot(body.getX() - at.getX(), body.getZ() - at.getZ()) < .5) remove(id);
            }
        }
        public void clear(String prefix) {
            if (prefix.equals("telegraph")) remove("meteor:0");
            for (String id : List.copyOf(bodies.keySet()))
                if (id.equals(prefix) || id.startsWith(prefix + ":")) remove(id);
        }
        private void remove(String id) { Body body = bodies.remove(id); if (body != null) body.close(); }
        private void advance() {
            if (!alive()) { close(); return; }
            for (String id : List.copyOf(bodies.keySet())) {
                Body body = bodies.get(id);
                if (!body.entity.isValid() || tick - body.touched > 3 || !context.loaded(body.entity.getLocation())) remove(id);
            }
        }
        @Override public void close() {
            if (closed) return;
            closed = true;
            for (String id : List.copyOf(bodies.keySet())) remove(id);
            tracks.remove(this);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST) public void hit(ProjectileHitEvent event) {
        if (managed(event.getEntity())) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST) public void damage(EntityDamageEvent event) {
        if (managed(event.getEntity()) || event instanceof EntityDamageByEntityEvent by && managed(by.getDamager())) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST) public void combust(EntityCombustByEntityEvent event) {
        if (managed(event.getCombuster())) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST) public void prime(ExplosionPrimeEvent event) {
        if (managed(event.getEntity())) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST) public void explode(EntityExplodeEvent event) {
        if (managed(event.getEntity())) { event.setCancelled(true); event.blockList().clear(); }
    }
    @EventHandler(priority = EventPriority.HIGHEST) public void ignite(BlockIgniteEvent event) {
        if (managed(event.getIgnitingEntity())) event.setCancelled(true);
    }
    @EventHandler public void loaded(EntitiesLoadEvent event) {
        for (Entity entity : event.getEntities()) if (managed(entity)) entity.remove();
    }
}
