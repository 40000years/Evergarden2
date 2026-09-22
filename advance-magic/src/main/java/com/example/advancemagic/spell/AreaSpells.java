package com.example.advancemagic.spell;

import com.example.advancemagic.effect.Geometry;
import org.bukkit.*;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.*;
import org.bukkit.util.Vector;
import java.util.*;

public final class AreaSpells implements Listener {
    private final MagicContext c;
    private final Set<UUID> wallBlocks=new HashSet<>();
    private final Map<UUID,BoundingBox> walls=new HashMap<>();
    private final Map<UUID,World> wallWorlds=new HashMap<>();
    private final NamespacedKey vexKey;
    private final List<Vex> activeVexes=new ArrayList<>();
    private final Map<UUID, Map<UUID, Long>> summonerAttackers = new java.util.concurrent.ConcurrentHashMap<>();
    public AreaSpells(MagicContext c){
        this.c=c;
        this.vexKey=new NamespacedKey(c.plugin,"allied_vex");
    }
    public boolean lightning(Player p) {
        Location at=c.targetPoint(p,30);if(at==null)return false;
        // The vanilla effect sends native lightning packets without uncontrolled fire or extra damage.
        at.getWorld().strikeLightningEffect(at);
        for(var e:c.nearby(p,at,5,false))if(c.affect(p,e,Spell.LIGHTNING_STRIKE)) {
            c.damage(p,e,c.configuredDamage("damage.lightning",90),DamageType.LIGHTNING_BOLT);
            c.potion(e,PotionEffectType.SLOWNESS,40,2);
        }
        c.ring(at,5,Spell.LIGHTNING_STRIKE);
        // Stage 2: Static Overcharge Wave 10 ticks later (0.5s delay)
        c.plugin.effects().start(p,15,(effect,age)->{
            if(!c.loaded(at))return false;
            if(age==10) {
                at.getWorld().playSound(at,Sound.ENTITY_LIGHTNING_BOLT_IMPACT,1.3f,1.8f);
                c.ring(at,7,Spell.LIGHTNING_STRIKE);
                c.particles(at.clone().add(0,0.5,0),Particle.ELECTRIC_SPARK,45,2.2);
                c.echo(p,at,Spell.LIGHTNING_STRIKE,14,7,72);
                for(var e:c.nearby(p,at,7,false))if(c.affect(p,e,Spell.LIGHTNING_STRIKE)) {
                    c.damage(p,e,c.configuredDamage("damage.lightning-secondary",45),DamageType.LIGHTNING_BOLT);
                    c.potion(e,PotionEffectType.BLINDNESS,40,0);
                    c.potion(e,PotionEffectType.NAUSEA,60,0);
                }
            }
            return true;
        });
        return true;
    }
    public boolean frost(Player p) {
        Location center=p.getLocation();
        // Stage 1: Flash Freeze
        for(var e:c.nearby(p,center,7,false))if(c.affect(p,e,Spell.FROST_NOVA)) {
            c.potion(e,PotionEffectType.SLOWNESS,120,3);c.plugin.statuses().freeze(p,e);
        }
        c.plugin.effects().start(p,21,(effect,age)->{
            if(age%2==0)c.ring(center,Math.min(7,0.7+age*0.35),Spell.FROST_NOVA);
            c.particles(center.clone().add(0,0.5,0),Particle.SNOWFLAKE,8,2);
            // Stage 2: Glacial Shatter Detonation at culmination (tick 20)
            if(age==20) {
                center.getWorld().playSound(center,Sound.BLOCK_GLASS_BREAK,1.3f,0.6f);
                center.getWorld().playSound(center,Sound.ENTITY_PLAYER_HURT_FREEZE,1.2f,0.9f);
                c.ring(center,7.5,Spell.FROST_NOVA);
                c.particles(center.clone().add(0,0.5,0),Particle.SNOWFLAKE,40,2.5);
                c.echo(p,center,Spell.FROST_NOVA,14,7.5,100);
                for(var e:c.nearby(p,center,7.5,false))if(c.affect(p,e,Spell.FROST_NOVA)) {
                    c.damage(p,e,c.configuredDamage("damage.frost-shatter",52.5),DamageType.FREEZE);
                    Vector push=e.getLocation().toVector().subtract(center.toVector()).setY(0);
                    if(push.lengthSquared()>0.01)c.velocity(e,push.normalize().multiply(0.6).setY(0.25));
                }
            }
            return true;
        });return true;
    }
    public boolean wall(Player p) {
        Vector facing=p.getLocation().getDirection().setY(0);
        if(facing.lengthSquared()<0.01)facing=new Vector(0,0,1);else facing.normalize();
        Location center=p.getLocation().add(facing.clone().multiply(3));
        boolean xNormal=Math.abs(facing.getX())>Math.abs(facing.getZ());
        int nx = xNormal ? (facing.getX() > 0 ? 1 : -1) : 0;
        int nz = xNormal ? 0 : (facing.getZ() > 0 ? 1 : -1);
        int cx=center.getBlockX(),cy=center.getBlockY(),cz=center.getBlockZ();
        List<Location> cells=new ArrayList<>();
        // 7 blocks wide, 4 blocks high, 2 blocks thick double-layer fortress
        double minX=Double.MAX_VALUE,minY=Double.MAX_VALUE,minZ=Double.MAX_VALUE;
        double maxX=-Double.MAX_VALUE,maxY=-Double.MAX_VALUE,maxZ=-Double.MAX_VALUE;
        for(int layer=0;layer<2;layer++)for(int side=-3;side<=3;side++)for(int y=0;y<4;y++) {
            int bx = cx + (xNormal ? layer*nx : side);
            int by = cy + y;
            int bz = cz + (xNormal ? side : layer*nz);
            Location cell=new Location(p.getWorld(),bx,by,bz);
            if(!c.loaded(cell))return false;
            if(cell.getBlock().isPassable())cells.add(cell.add(0.5,0,0.5));
            minX=Math.min(minX,bx);minY=Math.min(minY,by);minZ=Math.min(minZ,bz);
            maxX=Math.max(maxX,bx+1);maxY=Math.max(maxY,by+1);maxZ=Math.max(maxZ,bz+1);
        }
        if(cells.isEmpty())return false;
        BoundingBox box=new BoundingBox(minX,minY,minZ,maxX,maxY,maxZ);
        UUID id=UUID.randomUUID();World world=p.getWorld();
        List<FallingBlock> blocks=new ArrayList<>();
        var effect=c.plugin.effects().start(p,100,(scope,age)->{
            if(!c.loaded(center))return false;
            for(FallingBlock block:blocks)if(block.isValid()){block.setVelocity(new Vector());block.setTicksLived(1);}
            // Look ahead across the entire movement segment, including high-speed arrows.
            for(Entity entity:world.getNearbyEntities(center.clone().add(0,2.0,0),8,8,8))
                if(entity instanceof Projectile projectile&&blocksProjectile(world,projectile.getLocation().toVector(),projectile.getLocation().toVector().add(projectile.getVelocity())))projectile.remove();
            if(age%10==0)c.particles(center.clone().add(0,2,0),Particle.CLOUD,12,2.0);
            return true;
        });
        walls.put(id,box);wallWorlds.put(id,world);
        // Stage 2: Fortress crumble burst on wall collapse
        effect.onClose(()->{
            walls.remove(id);wallWorlds.remove(id);blocks.forEach(b->wallBlocks.remove(b.getUniqueId()));
            if(c.loaded(center)) {
                world.playSound(center,Sound.BLOCK_DEEPSLATE_BREAK,1.2f,0.8f);
                c.particles(center.clone().add(0,1.5,0),Particle.CAMPFIRE_COSY_SMOKE,30,2.0);
                for(var e:c.nearby(p,center,4.5,false))if(c.affect(p,e,Spell.EARTH_WALL)) {
                    Vector push=e.getLocation().toVector().subtract(center.toVector()).setY(0);
                    if(push.lengthSquared()>0.01)c.velocity(e,push.normalize().multiply(0.5).setY(0.2));
                }
            }
        });
        try {
            for(Location cell:cells) {
                FallingBlock block=effect.track(world.spawnFallingBlock(cell,Material.DEEPSLATE_BRICKS.createBlockData()));
                block.setGravity(false);block.setDropItem(false);block.setHurtEntities(false);block.setInvulnerable(true);
                wallBlocks.add(block.getUniqueId());blocks.add(block);
            }
            // Stage 1: Tectonic Rupture Shockwave upon creation
            Location ruptureCenter=center.clone().add(facing.clone().multiply(1.5));
            world.playSound(ruptureCenter,Sound.ENTITY_IRON_GOLEM_ATTACK,1.2f,0.6f);
            world.playSound(ruptureCenter,Sound.BLOCK_STONE_BREAK,1.5f,0.8f);
            c.particles(ruptureCenter,Particle.CAMPFIRE_COSY_SMOKE,25,1.5);
            c.particles(ruptureCenter,Particle.EXPLOSION,2,0.8);
            for(var e:c.nearby(p,ruptureCenter,4.5,false))if(c.affect(p,e,Spell.EARTH_WALL)) {
                c.damage(p,e,c.configuredDamage("damage.earth-wall-rupture",37.5),DamageType.MOB_ATTACK);
                c.potion(e,PotionEffectType.SLOWNESS,60,3);
                Vector knock=e.getLocation().toVector().subtract(center.toVector()).setY(0);
                if(knock.lengthSquared()<0.01)knock=facing.clone();
                c.velocity(e,knock.normalize().multiply(0.8).setY(0.35));
            }
            c.echo(p,ruptureCenter,Spell.EARTH_WALL,100,5,100);
        }catch(RuntimeException ex){effect.close();throw ex;}
        return true;
    }
    public boolean blocksProjectile(World world,Vector from,Vector to) {
        for(var entry:walls.entrySet())if(wallWorlds.get(entry.getKey())==world&&Geometry.intersects(entry.getValue().clone().expand(0.2),from,to))return true;
        return false;
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void land(EntityChangeBlockEvent e){if(wallBlocks.contains(e.getEntity().getUniqueId()))e.setCancelled(true);}
    public boolean sonicBoom(Player p) {
        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        World world = eye.getWorld();
        world.playSound(eye, Sound.ENTITY_WARDEN_SONIC_BOOM, 2.0f, 1.0f);

        Set<LivingEntity> hitEnemies = new HashSet<>();
        for(double d = 1.0; d <= 25.0; d += 1.0) {
            Location point = eye.clone().add(dir.clone().multiply(d));
            if(!c.loaded(point)) break;
            world.spawnParticle(Particle.SONIC_BOOM, point, 1, 0, 0, 0, 0);
            for(Entity entity : world.getNearbyEntities(point, 1.8, 1.8, 1.8)) {
                if(entity instanceof LivingEntity living && !entity.equals(p) && !(living instanceof ArmorStand) && c.enemy(p, living)) {
                    if(hitEnemies.add(living) && c.affect(p, living, Spell.SONIC_BOOM)) {
                        c.damage(p, living, c.configuredDamage("damage.sonic-boom", 112.5), DamageType.SONIC_BOOM);
                        Vector knock = dir.clone().multiply(1.8).setY(0.4);
                        c.velocity(living, knock);
                        c.potion(living, PotionEffectType.DARKNESS, 60, 0);
                        living.getWorld().playSound(living.getLocation(), Sound.ENTITY_WARDEN_ATTACK_IMPACT, 1.2f, 1.0f);
                    }
                }
            }
        }
        Location endPoint = eye.clone().add(dir.clone().multiply(15.0));
        c.echo(p, endPoint, Spell.SONIC_BOOM, 14, 5.0, 100.0);
        return true;
    }

    public boolean vexLegion(Player p) {
        Location center = p.getLocation();
        World world = center.getWorld();
        if(!c.loaded(center)) return false;

        world.playSound(center, Sound.ENTITY_EVOKER_PREPARE_SUMMON, 1.4f, 1.0f);
        world.playSound(center, Sound.ENTITY_VEX_CHARGE, 1.2f, 1.2f);
        c.particles(center.clone().add(0, 1, 0), Particle.ENCHANT, 35, 1.5);
        c.particles(center.clone().add(0, 1, 0), Particle.SOUL, 25, 1.2);
        c.ring(center, 3.5, Spell.VEX_LEGION);

        List<Vex> summoned = new ArrayList<>();
        for(int i = 0; i < 3; i++) {
            double angle = i * (2 * Math.PI / 3);
            Location spawnLoc = center.clone().add(Math.cos(angle) * 2.0, 1.0, Math.sin(angle) * 2.0);
            if(!c.loaded(spawnLoc)) spawnLoc = center.clone().add(0, 1.0, 0);
            Vex vex = world.spawn(spawnLoc, Vex.class, v -> {
                v.setCustomName(ChatColor.AQUA + "✦ " + p.getName() + "'s Spirit Vex");
                v.setCustomNameVisible(true);
                v.getPersistentDataContainer().set(vexKey, org.bukkit.persistence.PersistentDataType.STRING, p.getUniqueId().toString());
                v.getEquipment().setItemInMainHand(new org.bukkit.inventory.ItemStack(Material.IRON_SWORD));
                v.getEquipment().setItemInMainHandDropChance(0.0f);
                v.setCanPickupItems(false);
            });
            summoned.add(vex);
            activeVexes.add(vex);
        }

        c.plugin.effects().start(p, 300, (effect, age) -> {
            if(!p.isOnline() || p.isDead()) {
                summoned.forEach(v -> { if(v.isValid()) v.remove(); });
                summonerAttackers.remove(p.getUniqueId());
                return false;
            }
            if(age % 10 == 0) {
                Map<UUID, Long> attackers = summonerAttackers.get(p.getUniqueId());
                LivingEntity validAttacker = null;
                if(attackers != null && !attackers.isEmpty()) {
                    long now = System.currentTimeMillis();
                    attackers.entrySet().removeIf(entry -> entry.getValue() < now);
                    for(UUID attId : attackers.keySet()) {
                        Entity ent = Bukkit.getEntity(attId);
                        if(ent instanceof LivingEntity living && living.isValid() && !living.isDead() && living.getWorld().equals(p.getWorld())) {
                            if(living.getLocation().distanceSquared(p.getLocation()) <= 625.0 && c.enemy(p, living)) { // within 25 blocks
                                validAttacker = living;
                                break;
                            }
                        }
                    }
                }

                for(Vex vex : summoned) {
                    if(!vex.isValid() || vex.isDead()) continue;
                    if(validAttacker != null) {
                        vex.setTarget(validAttacker);
                        vex.setCharging(true);
                    } else {
                        // Guarding mode: no attacker found, remain peaceful and hover near summoner
                        vex.setTarget(null);
                        vex.setCharging(false);
                        if(vex.getLocation().distanceSquared(p.getLocation()) > 81.0) { // > 9 blocks away
                            vex.teleport(p.getLocation().add((Math.random() - 0.5) * 3.0, 1.2, (Math.random() - 0.5) * 3.0));
                        }
                    }
                    if(age % 20 == 0) {
                        vex.getWorld().spawnParticle(Particle.SOUL, vex.getLocation().add(0, 0.4, 0), 3, 0.2, 0.2, 0.2, 0.02);
                    }
                }
            }
            if(age == 300) {
                for(Vex vex : summoned) {
                    if(vex.isValid()) {
                        vex.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, vex.getLocation(), 15, 0.3, 0.3, 0.3, 0.05);
                        vex.getWorld().playSound(vex.getLocation(), Sound.ENTITY_VEX_DEATH, 0.8f, 1.2f);
                        vex.remove();
                    }
                }
                summonerAttackers.remove(p.getUniqueId());
            }
            return true;
        });

        c.echo(p, center, Spell.VEX_LEGION, 20, 6.0, 25.0);
        return true;
    }

    @EventHandler(ignoreCancelled = true)
    public void onVexTarget(org.bukkit.event.entity.EntityTargetLivingEntityEvent e) {
        if(e.getEntity() instanceof Vex vex && vex.getPersistentDataContainer().has(vexKey, org.bukkit.persistence.PersistentDataType.STRING)) {
            String ownerId = vex.getPersistentDataContainer().get(vexKey, org.bukkit.persistence.PersistentDataType.STRING);
            if(ownerId != null && e.getTarget() != null) {
                UUID ownerUUID = UUID.fromString(ownerId);
                LivingEntity target = e.getTarget();

                // Never target the owner or ally
                if(target.getUniqueId().equals(ownerUUID)) {
                    e.setCancelled(true);
                    return;
                }
                Player owner = Bukkit.getPlayer(ownerUUID);
                if(owner != null && c.ally(owner, target)) {
                    e.setCancelled(true);
                    return;
                }

                // Defensive Bodyguard AI: Only allow targeting entities that attacked the summoner
                Map<UUID, Long> attackers = summonerAttackers.get(ownerUUID);
                if(attackers == null || !attackers.containsKey(target.getUniqueId()) || attackers.get(target.getUniqueId()) < System.currentTimeMillis()) {
                    e.setCancelled(true);
                }
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onVexDamage(org.bukkit.event.entity.EntityDamageByEntityEvent e) {
        // 1. Defend Vex from owner or allies
        if(e.getEntity() instanceof Vex vex && vex.getPersistentDataContainer().has(vexKey, org.bukkit.persistence.PersistentDataType.STRING)) {
            String ownerId = vex.getPersistentDataContainer().get(vexKey, org.bukkit.persistence.PersistentDataType.STRING);
            Entity damager = e.getDamager();
            if(damager instanceof Projectile proj && proj.getShooter() instanceof Entity shooter) damager = shooter;
            if(damager != null && ownerId != null) {
                if(damager.getUniqueId().toString().equals(ownerId)) {
                    e.setCancelled(true);
                    return;
                }
                if(damager instanceof Player pl && c.ally(pl, vex)) {
                    e.setCancelled(true);
                    return;
                }
            }
        }

        // 2. Prevent Vex from damaging owner or allies
        Entity damager = e.getDamager();
        if(damager instanceof Projectile proj && proj.getShooter() instanceof Entity shooter) damager = shooter;
        if(damager instanceof Vex vex && vex.getPersistentDataContainer().has(vexKey, org.bukkit.persistence.PersistentDataType.STRING)) {
            String ownerId = vex.getPersistentDataContainer().get(vexKey, org.bukkit.persistence.PersistentDataType.STRING);
            if(ownerId != null && e.getEntity() instanceof LivingEntity victim) {
                if(victim.getUniqueId().toString().equals(ownerId)) {
                    e.setCancelled(true);
                    return;
                }
                Player owner = Bukkit.getPlayer(UUID.fromString(ownerId));
                if(owner != null && c.ally(owner, victim)) {
                    e.setCancelled(true);
                    return;
                }
            }
        }

        // 3. Track when a summoner/player is damaged by an enemy entity to trigger Vex counter-attack
        if(e.getEntity() instanceof Player victimPlayer) {
            if(damager instanceof LivingEntity attacker && !attacker.getUniqueId().equals(victimPlayer.getUniqueId())) {
                if(c.enemy(victimPlayer, attacker)) {
                    summonerAttackers.computeIfAbsent(victimPlayer.getUniqueId(), k -> new java.util.concurrent.ConcurrentHashMap<>())
                        .put(attacker.getUniqueId(), System.currentTimeMillis() + 20000L);

                    // Immediately command summoner's active vexes to retaliate
                    String victimIdStr = victimPlayer.getUniqueId().toString();
                    for(Vex activeVex : activeVexes) {
                        if(!activeVex.isValid() || activeVex.isDead()) continue;
                        String ownerStr = activeVex.getPersistentDataContainer().get(vexKey, org.bukkit.persistence.PersistentDataType.STRING);
                        if(ownerStr != null && ownerStr.equals(victimIdStr)) {
                            activeVex.setTarget(attacker);
                            activeVex.setCharging(true);
                        }
                    }
                }
            }
        }
    }

    public void tick() {
        activeVexes.removeIf(v -> !v.isValid());
    }

    public void close() {
        for(Vex vex : activeVexes) if(vex.isValid()) vex.remove();
        activeVexes.clear();
        summonerAttackers.clear();
        walls.clear();
        wallWorlds.clear();
        wallBlocks.clear();
    }
}
