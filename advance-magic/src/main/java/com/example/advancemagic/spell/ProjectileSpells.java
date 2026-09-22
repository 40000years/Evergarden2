package com.example.advancemagic.spell;

import com.example.advancemagic.effect.EffectEngine;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.entity.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.*;
import org.bukkit.util.Vector;
import java.util.*;
import java.util.function.*;

public final class ProjectileSpells implements Listener {
    private record Shot(Player owner,Spell spell,Projectile projectile,Consumer<Location> impact) {}
    private final MagicContext c;
    private final NamespacedKey managed;
    private final Map<UUID,Shot> shots=new HashMap<>();
    public ProjectileSpells(MagicContext c){this.c=c;managed=new NamespacedKey(c.plugin,"projectile");}
    private <T extends Projectile> T launch(EffectEngine.Effect scope,Spell spell,Class<T> type,Vector velocity,Consumer<T> setup,Consumer<Location> impact) {
        double mult = c.getCastVelocityMultiplier(scope.owner.getUniqueId());
        Vector finalVel = mult != 1.0 ? velocity.clone().multiply(mult) : velocity;
        T projectile=scope.track(scope.owner.launchProjectile(type,finalVel));
        projectile.getPersistentDataContainer().set(managed,PersistentDataType.BYTE,(byte)1);
        setup.accept(projectile);
        shots.put(projectile.getUniqueId(),new Shot(scope.owner,spell,projectile,impact));
        scope.onClose(()->shots.remove(projectile.getUniqueId()));return projectile;
    }
    private boolean flight(EffectEngine.Effect effect,int age) {
        for(Shot shot:List.copyOf(shots.values()))if(shot.owner.equals(effect.owner)) {
            Projectile p=shot.projectile;
            if(!p.isValid()||!c.loaded(p.getLocation())||p.getLocation().distanceSquared(shot.owner.getLocation())>4096) {
                p.remove();shots.remove(p.getUniqueId());continue;
            }
            Vector from=p.getLocation().toVector();
            if(c.plugin.areas().blocksProjectile(p.getWorld(),from,from.clone().add(p.getVelocity()))) {
                p.remove();shots.remove(p.getUniqueId());continue;
            }
            if(age%3==0)c.particles(p.getLocation(),shot.spell==Spell.BLAZE_BARRAGE?Particle.FLAME:Particle.PORTAL,2,0.06);
        }
        return true;
    }
    public boolean blaze(Player p) {
        c.plugin.effects().start(p,60,(effect,age)->{
            if(age==0||age==4||age==8||age==12) {
                boolean finisher=(age==12);
                Vector dir=p.getEyeLocation().getDirection();
                int idx=age/4;
                Vector spread=dir.clone().add(new Vector(
                    (idx%2==0?0.03:-0.03)*idx,
                    (idx%3==0?0.02:-0.02),
                    (idx%2!=0?0.03:-0.03)*idx
                )).normalize().multiply(1.4);

                p.getWorld().playSound(p.getLocation(),Sound.ENTITY_BLAZE_SHOOT,1.0f,1.0f+idx*0.1f);
                launch(effect,Spell.BLAZE_BARRAGE,SmallFireball.class,spread,ball->{
                    ball.setIsIncendiary(false);
                },at->{
                    at.getWorld().playSound(at,Sound.ITEM_FIRECHARGE_USE,1.2f,1.0f);
                    at.getWorld().playSound(at,Sound.ENTITY_BLAZE_HURT,0.8f,1.2f);
                    c.particles(at,Particle.FLAME,25,0.8);
                    c.particles(at,Particle.EXPLOSION,1,0.2);
                    if(finisher)c.echo(p,at,Spell.BLAZE_BARRAGE,14,4.5,80);
                    for(var e:c.nearby(p,at,3.5,false))if(c.affect(p,e,Spell.BLAZE_BARRAGE)) {
                        c.damage(p,e,c.configuredDamage("damage.blaze-barrage",37.5),DamageType.MAGIC);
                        e.setFireTicks(Math.max(e.getFireTicks(),100));
                    }
                });
            }
            return flight(effect,age);
        });return true;
    }
    public boolean voidPull(Player p) {
        var effect=c.plugin.effects().start(p,60,this::flight);
        launch(effect,Spell.VOID_PULL,Snowball.class,p.getEyeLocation().getDirection().multiply(1.1),ball->{ball.setGravity(false);ball.setItem(new org.bukkit.inventory.ItemStack(Material.ENDER_PEARL));},at->gravity(p,at));
        return true;
    }
    private void gravity(Player p,Location at) {
        if(!c.plugin.effects().hasCapacity())return;
        c.plugin.effects().start(p,42,(effect,age)->{
            if(!c.loaded(at))return false;
            if(age%5==0)c.ring(at,8,Spell.VOID_PULL);
            c.particles(at,Particle.REVERSE_PORTAL,12,0.4);
            if(age%6==0)for(var e:c.nearby(p,at,8,false))if(c.affect(p,e,Spell.VOID_PULL)) {
                if(age>=30||e.getLocation().distanceSquared(at)<2.25)c.plugin.statuses().root(p,e);
                else {
                    Vector velocity=at.clone().add(0,0.5,0).toVector().subtract(e.getLocation().toVector());
                    if(velocity.lengthSquared()>0.01)c.velocity(e,velocity.normalize().multiply(0.65));
                    if(e instanceof Player player) c.potion(player, PotionEffectType.SLOWNESS, 20, 2);
                }
            }
            // Stage 2: Event Horizon Collapse at age 38
            if(age==38) {
                at.getWorld().playSound(at,Sound.ENTITY_WARDEN_SONIC_BOOM,1.1f,1.3f);
                c.ring(at,9,Spell.VOID_PULL);
                c.particles(at,Particle.REVERSE_PORTAL,45,1.2);
                c.particles(at,Particle.DRAGON_BREATH,25,0.8);
                c.echo(p,at,Spell.VOID_PULL,14,6,80);
                for(var e:c.nearby(p,at,5.5,false))if(c.affect(p,e,Spell.VOID_PULL)) {
                    c.damage(p,e,c.configuredDamage("damage.void-collapse",67.5),DamageType.MAGIC);
                    c.velocity(e,new Vector(0,1.1,0));
                }
            }
            return true;
        });
    }
    public boolean wither(Player p) {
        c.plugin.effects().start(p,100,(effect,age)->{
            if(age==0||age==4||age==8||age==12||age==16||age==20) {
                boolean finisher=(age==20);
                Vector dir=p.getEyeLocation().getDirection();
                int idx=age/4;
                Vector spread=dir.clone().add(new Vector(
                    (idx%2==0?0.025:-0.025)*idx,
                    (idx%3==0?0.02:-0.015),
                    (idx%2!=0?0.025:-0.025)*idx
                )).normalize().multiply(1.25);
                launch(effect,Spell.WITHER_RAY,WitherSkull.class,spread,skull->{
                    skull.setYield(0);skull.setIsIncendiary(false);skull.setCharged(finisher);
                },at->{
                    c.particles(at,Particle.EXPLOSION,finisher?3:1,finisher?0.5:0);
                    at.getWorld().playSound(at,Sound.ENTITY_GENERIC_EXPLODE,finisher?1.0f:0.5f,finisher?0.8f:1.4f);
                    double dmg=c.configuredDamage("damage.wither-skull",60)*(finisher?1.5:1.0);
                    if(finisher)c.echo(p,at,Spell.WITHER_RAY,14,4.5,60);
                    for(var e:c.nearby(p,at,finisher?4.5:3.0,false))if(c.affect(p,e,Spell.WITHER_RAY)) {
                        c.damage(p,e,dmg,DamageType.EXPLOSION);
                        c.potion(e,PotionEffectType.WITHER,finisher?160:100,finisher?2:1);
                    }
                });
            }
            return flight(effect,age);
        });return true;
    }
    public boolean shulker(Player p) {
        LivingEntity targetedMob=c.targetEntity(p,30);
        Location center;
        if(targetedMob!=null) {
            center=targetedMob.getLocation().clone().add(0,1.0,0);
        } else {
            Location hitBlock=c.targetPoint(p,30);
            if(hitBlock!=null) center=hitBlock.clone().add(0,1.5,0);
            else center=p.getEyeLocation().add(p.getEyeLocation().getDirection().multiply(15));
        }
        World world=p.getWorld();
        if(!c.loaded(center))return false;

        // 1. Weather: Unleash thunderstorm
        world.setStorm(true);
        world.setThundering(true);
        world.setThunderDuration(6000);
        world.strikeLightningEffect(center.clone().add(5,0,5));
        world.strikeLightningEffect(center.clone().add(-5,0,-5));

        // 2. Ender Dragon Death sound & initial roar
        world.playSound(center,Sound.ENTITY_ENDER_DRAGON_DEATH,3.0f,0.9f);
        world.playSound(center,Sound.ENTITY_ENDER_DRAGON_GROWL,2.5f,0.6f);

        // 3. Stage 1: Gravitational Singularity & Dragon Death Ray Vortex (70 ticks = 3.5s)
        c.plugin.effects().start(p,71,(eff,age)->{
            if(!c.loaded(center))return false;
            // Combat happens only on natural completion, never during logout/death cleanup.
            if(age==70){detonateSingularity(p,center);return false;}

            // Ascending Ender Dragon death rays straight up into the sky
            for(int h=0;h<36;h+=3) {
                world.spawnParticle(Particle.END_ROD,center.clone().add(0,h,0),2,0.4,0.6,0.4,0.03);
                world.spawnParticle(Particle.DRAGON_BREATH,center.clone().add(0,h*0.6,0),3,0.5,0.5,0.5,0.02,1.0f);
            }
            // Radial light beam flashes (like dragon dying)
            for(int i=0;i<8;i++) {
                double angle=i*(Math.PI/4)+age*0.15;
                double rayDist=Math.min(10.0,age*0.25);
                world.spawnParticle(Particle.FLASH,center.clone().add(Math.cos(angle)*rayDist,Math.sin(angle*2)*0.5,Math.sin(angle)*rayDist),1,0,0,0,0,Color.WHITE);
            }

            // Swirling black hole singularity ring
            double ringR=Math.max(1.2,6.0-(age*0.07));
            for(int i=0;i<12;i++) {
                double a=i*(Math.PI/6)+age*0.25;
                world.spawnParticle(Particle.REVERSE_PORTAL,center.clone().add(Math.cos(a)*ringR,Math.sin(a*3)*0.4,Math.sin(a)*ringR),3,0,0,0,0.02);
                world.spawnParticle(Particle.PORTAL,center.clone().add(Math.cos(a)*ringR*0.7,0,Math.sin(a)*ringR*0.7),2,0.1,0.1,0.1,0.05);
            }
            world.spawnParticle(Particle.SQUID_INK,center,8,0.4,0.4,0.4,0.05);

            // Gravitational suction sound
            if(age%15==0)world.playSound(center,Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE,1.5f,0.5f);
            if(age%20==0)world.playSound(center,Sound.ENTITY_WARDEN_HEARTBEAT,2.0f,1.3f);

            // Pull only eligible enemies, through the same protection and target limits as damage.
            if(age%5==0) {
                for(var target:c.nearby(p,center,22,false))if(c.affect(p,target,Spell.SHULKER_LEVITATION)) {
                    Vector pull=center.toVector().subtract(target.getLocation().toVector());
                    double dist=pull.length();
                    if(dist>0.8)c.velocity(target,pull.normalize().multiply(Math.min(1.1,0.35+dist*0.04))
                        .setY(Math.min(0.7,(center.getY()-target.getLocation().getY())*0.2+0.15)));
                    c.potion(target,PotionEffectType.LEVITATION,40,1);
                }
            }
            if(age%10==0) {
                for(var target:c.nearby(p,center,22,false))if(c.affect(p,target,Spell.SHULKER_LEVITATION)) {
                    c.damage(p,target,45,DamageType.MAGIC);
                }
            }
            return true;
        });

        return true;
    }

    private void detonateSingularity(Player p,Location center) {
        World world=center.getWorld();
            if(!c.loaded(center))return;
            // Massive explosion and sonic boom
            world.spawnParticle(Particle.EXPLOSION_EMITTER,center,10,2.0,2.0,2.0,0.1);
            world.spawnParticle(Particle.FLASH,center,8,1.0,1.0,1.0,0,Color.WHITE);
            world.spawnParticle(Particle.DRAGON_BREATH,center,150,4.0,3.0,4.0,0.2,1.0f);
            world.playSound(center,Sound.ENTITY_GENERIC_EXPLODE,3.0f,0.5f);
            world.playSound(center,Sound.ENTITY_WARDEN_SONIC_BOOM,2.0f,0.7f);
            world.strikeLightningEffect(center);

            // Huge burst damage to all caught enemies in 12 blocks
            for(Entity e:world.getNearbyEntities(center,12,12,12)) {
                if(e instanceof LivingEntity living&&!e.equals(p)&&!(living instanceof ArmorStand)&&c.enemy(p,living)) {
                    if(c.affect(p,living,Spell.SHULKER_LEVITATION)) {
                        c.damage(p,living,c.configuredDamage("damage.shulker-singularity-burst",180),DamageType.EXPLOSION);
                        Vector knock=living.getLocation().toVector().subtract(center.toVector()).normalize().multiply(1.8).setY(0.7);
                        c.velocity(living,knock);
                    }
                }
            }

            // 5. Sculk Corruption Zone (radius 7 blocks on surface)
            if(c.plugin.effects().hasCapacity())createSculkWitherZone(p,center);
            c.echo(p,center,Spell.SHULKER_LEVITATION,14,12,80);
    }

    private void createSculkWitherZone(Player p, Location center) {
        World world=center.getWorld();
        int cx=center.getBlockX(),cy=center.getBlockY(),cz=center.getBlockZ();
        Map<Block,org.bukkit.block.data.BlockData> fakeBlocks=new HashMap<>();
        org.bukkit.block.data.BlockData catalystData=Material.SCULK_CATALYST.createBlockData();
        org.bukkit.block.data.BlockData sculkData=Material.SCULK.createBlockData();

        for(int dx=-7;dx<=7;dx++) {
            for(int dz=-7;dz<=7;dz++) {
                if(dx*dx+dz*dz>49)continue;
                int bx=cx+dx;
                int bz=cz+dz;
                Block surface=null;
                for(int dy=3;dy>=-5;dy--) {
                    Block b=world.getBlockAt(bx,cy+dy,bz);
                    if(b.getType().isSolid()&&b.getType()!=Material.BEDROCK&&b.getType()!=Material.BARRIER&&b.getType()!=Material.NETHER_PORTAL) {
                        surface=b;
                        break;
                    }
                }
                if(surface!=null&&!surface.getType().isAir()) {
                    fakeBlocks.put(surface,(Math.abs(dx)<=1&&Math.abs(dz)<=1)?catalystData:sculkData);
                }
            }
        }

        // Send visual block changes to nearby players without corrupting server blocks or containers
        for(Player viewer:world.getPlayers()) {
            if(viewer.getLocation().distanceSquared(center)<=2500) {
                for(Map.Entry<Block,org.bukkit.block.data.BlockData> entry:fakeBlocks.entrySet()) {
                    viewer.sendBlockChange(entry.getKey().getLocation(),entry.getValue());
                }
            }
        }

        var sculkEffect=c.plugin.effects().start(p,300,(eff,age)->{
            if(!c.loaded(center))return false;
            // Particles rising from the sculk floor
            if(age%6==0) {
                for(int i=0;i<8;i++) {
                    double rx=(Math.random()-0.5)*14.0;
                    double rz=(Math.random()-0.5)*14.0;
                    if(rx*rx+rz*rz<=49.0) {
                        Location partLoc=center.clone().add(rx,0.2,rz);
                        world.spawnParticle(Particle.SCULK_SOUL,partLoc,1,0.1,0.2,0.1,0.02);
                        world.spawnParticle(Particle.SCULK_CHARGE_POP,partLoc,1,0,0,0,0);
                    }
                }
            }
            if(age%25==0) {
                world.playSound(center,Sound.BLOCK_SCULK_CATALYST_BLOOM,1.2f,0.8f);
            }
            // Continuous heavy Wither III to all enemies stepping inside
            if(age%10==0) {
                for(Entity e:world.getNearbyEntities(center,7.5,4.0,7.5)) {
                    if(e.equals(p))continue;
                    if(e instanceof LivingEntity living && !(living instanceof ArmorStand) && c.enemy(p, living)) {
                        if(c.affect(p,living,Spell.SHULKER_LEVITATION)) {
                            living.addPotionEffect(new PotionEffect(PotionEffectType.WITHER,100,2));
                            living.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,60,1));
                            c.damage(p,living,7.5,DamageType.MAGIC);
                            if(age%20==0) {
                                living.getWorld().playSound(living.getLocation(),Sound.BLOCK_SCULK_SHRIEKER_SHRIEK,0.7f,1.3f);
                            }
                        }
                    }
                }
            }
            return true;
        });

        sculkEffect.onClose(()->{
            // Restore visual blocks for all players
            for(Player viewer:world.getPlayers()) {
                if(viewer.getLocation().distanceSquared(center)<=2500) {
                    for(Block b:fakeBlocks.keySet()) {
                        viewer.sendBlockChange(b.getLocation(),b.getBlockData());
                    }
                }
            }
            if(c.loaded(center)) {
                world.playSound(center,Sound.BLOCK_SCULK_BREAK,1.5f,0.8f);
                world.spawnParticle(Particle.BLOCK,center,40,2.0,0.5,2.0,Material.SCULK.createBlockData());
            }
        });
    }
    public boolean dragon(Player p) {
        Location start=p.getEyeLocation().add(p.getEyeLocation().getDirection());Vector dir=p.getEyeLocation().getDirection();
        if(!c.loaded(start))return false;
        AreaEffectCloud[] cloud=new AreaEffectCloud[1];
        boolean[] settled={false};
        Map<UUID,Integer> nextDamage=new HashMap<>();
        var effect=c.plugin.effects().start(p,120,(scope,age)->{
            AreaEffectCloud entity=cloud[0];if(entity==null||!entity.isValid()||!c.loaded(entity.getLocation()))return false;
            Location at=entity.getLocation();
            if(age>=20)settled[0]=true;
            if(!settled[0]&&age<20) {
                var hit=at.getWorld().rayTraceBlocks(at,dir,0.7,FluidCollisionMode.NEVER,true);
                if(hit!=null)settled[0]=true;
                else {Location next=at.clone().add(dir.clone().multiply(0.7));if(!c.loaded(next))return false;entity.teleport(next);at=next;}
            }
            if(age%5==0)for(var target:c.nearby(p,at,4,false))
                if(age>=nextDamage.getOrDefault(target.getUniqueId(),0)&&c.affect(p,target,Spell.DRAGONS_BREATH)) {
                    c.damage(p,target,c.configuredDamage("damage.dragon-per-second",45),DamageType.MAGIC);
                    nextDamage.put(target.getUniqueId(),age+20);
                }
            // Stage 2: Lingering Dragonfire Corrosive Miasma
            if(settled[0]&&age%20==0) {
                c.particles(at,Particle.DRAGON_BREATH,25,1.5);
                for(var target:c.nearby(p,at,4,false))if(c.affect(p,target,Spell.DRAGONS_BREATH)) {
                    c.potion(target,PotionEffectType.WEAKNESS,60,1);
                    c.potion(target,PotionEffectType.WITHER,60,1);
                }
            }
            // Wait beyond the last cloud hit's immunity window before the final burst.
            if(age==119)c.echo(p,at,Spell.DRAGONS_BREATH,14,5,120);
            return true;
        });
        try {
            cloud[0]=effect.track(p.getWorld().spawn(start,AreaEffectCloud.class));
            cloud[0].setSource(p);cloud[0].setRadius(4);cloud[0].setRadiusPerTick(0);cloud[0].setRadiusOnUse(0);
            cloud[0].setDuration(125);cloud[0].setWaitTime(0);
            if(Particle.DRAGON_BREATH.getDataType()==Float.class)cloud[0].setParticle(Particle.DRAGON_BREATH,1.0f);
            else cloud[0].setParticle(Particle.DRAGON_BREATH);
            cloud[0].clearCustomEffects();
        }catch(RuntimeException ex){effect.close();throw ex;}
        return true;
    }
    public boolean meteor(Player p) {
        var hit=c.target(p,30);if(hit==null)return false;
        Location marker=hit.getHitPosition().toLocation(p.getWorld());
        var down=marker.getWorld().rayTraceBlocks(marker.clone().add(0,0.1,0),new Vector(0,-1,0),8,FluidCollisionMode.NEVER,true);
        if(hit.getHitBlock()!=null)marker=hit.getHitBlock().getLocation().add(0.5,1,0.5);
        else if(down!=null)marker=down.getHitPosition().toLocation(p.getWorld()).add(0,0.05,0);
        else return false;
        final Location at=marker;
        Location sky=at.clone().add(0,18,0);if(!c.loaded(sky))return false;
        Location[] targets=new Location[]{at,at.clone().add(2.5,0,1.5),at.clone().add(-2.0,0,-2.2)};
        int[] spawnAges=new int[]{30,42,54};
        List<LargeFireball> meteors=new ArrayList<>();
        c.plugin.effects().start(p,120,(effect,age)->{
            if(!c.loaded(at))return false;
            for(int i=0;i<targets.length;i++) {
                if(age<=spawnAges[i]&&age%5==0&&c.loaded(targets[i]))c.ring(targets[i],Math.max(2.0,5.0-i*0.5),Spell.METEOR_STRIKE);
                if(age==spawnAges[i]) {
                    Location target=targets[i];
                    Location dropSky=target.clone().add(0,18,0);
                    if(c.loaded(dropSky)) {
                        LargeFireball fb=effect.track(dropSky.getWorld().spawn(dropSky,LargeFireball.class));
                        fb.setShooter(p);fb.setYield(0);fb.setIsIncendiary(false);
                        fb.setDirection(new Vector(0,-1,0));fb.setVelocity(new Vector(0,-1.2,0));
                        fb.getPersistentDataContainer().set(managed,PersistentDataType.BYTE,(byte)1);
                        UUID id=fb.getUniqueId();
                        shots.put(id,new Shot(p,Spell.METEOR_STRIKE,fb,impact->meteorImpact(p,impact)));
                        effect.onClose(()->shots.remove(id));
                        meteors.add(fb);
                    }
                }
            }
            for(LargeFireball fb:meteors)if(fb!=null&&fb.isValid()&&c.loaded(fb.getLocation())) {
                if(age%2==0)c.particles(fb.getLocation(),Particle.FLAME,10,0.6);
            }
            return flight(effect,age);
        });return true;
    }
    private void meteorImpact(Player p,Location at) {
        c.echo(p,at,Spell.METEOR_STRIKE,14,6,60);
        c.particles(at,Particle.EXPLOSION_EMITTER,1,0);c.ring(at,6,Spell.METEOR_STRIKE);
        at.getWorld().playSound(at,Sound.ENTITY_GENERIC_EXPLODE,1.5f,0.6f);
        for(var e:c.nearby(p,at,6,false))if(c.affect(p,e,Spell.METEOR_STRIKE)) {
            c.damage(p,e,c.configuredDamage("meteor.damage",135),DamageType.EXPLOSION);
            var ignite=new EntityCombustByEntityEvent(p,e,4.0f);Bukkit.getPluginManager().callEvent(ignite);
            if(!ignite.isCancelled())e.setFireTicks(Math.max(e.getFireTicks(),(int)(ignite.getDuration()*20)));
        }
        if(!c.plugin.getConfig().getBoolean("meteor.ignite-terrain",true))return;
        for(int x=-5;x<=5;x++)for(int z=-5;z<=5;z++) {
            if(x*x+z*z>30||(x+z)%3!=0)continue;
            for(int y=1;y>=-2;y--) {
                Location pos=at.clone().add(x,y,z);if(!c.loaded(pos)||!c.loaded(pos.clone().add(0,-1,0)))continue;
                Block b=pos.getBlock();if(!b.getType().isAir()||!b.getRelative(0,-1,0).getType().isOccluding())continue;
                var ignite=new BlockIgniteEvent(b,BlockIgniteEvent.IgniteCause.FIREBALL,p);
                Bukkit.getPluginManager().callEvent(ignite);
                if(!ignite.isCancelled()&&b.getType().isAir())b.setType(Material.FIRE);
                break;
            }
        }
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void hit(ProjectileHitEvent e) {
        Shot shot=shots.remove(e.getEntity().getUniqueId());if(shot==null)return;
        boolean denied=e.isCancelled();e.setCancelled(true);
        Location at=e.getEntity().getLocation();e.getEntity().remove();
        if(denied||!shot.owner.isOnline()||shot.owner.isDead()||shot.owner.getWorld()!=at.getWorld()||!c.loaded(at))return;
        if(shot.spell==Spell.SHULKER_LEVITATION) {
            if(e.getHitEntity() instanceof LivingEntity target&&c.enemy(shot.owner,target)&&c.affect(shot.owner,target,shot.spell)) {
                c.damage(shot.owner,target,c.configuredDamage("damage.shulker-impact",30),DamageType.MAGIC);
                c.potion(target,PotionEffectType.LEVITATION,80,1);
            }
        } else shot.impact.accept(at);
    }
    private boolean managed(Entity e){return e.getPersistentDataContainer().has(managed,PersistentDataType.BYTE);}
    @EventHandler(priority=EventPriority.HIGHEST) public void nativeDamage(EntityDamageByEntityEvent e){if(managed(e.getDamager()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST) public void nativeExplosion(EntityExplodeEvent e){if(managed(e.getEntity()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST) public void nativeIgnite(BlockIgniteEvent e){if(e.getIgnitingEntity()!=null&&managed(e.getIgnitingEntity()))e.setCancelled(true);}
    public void close(){shots.values().forEach(s->s.projectile.remove());shots.clear();}
}
