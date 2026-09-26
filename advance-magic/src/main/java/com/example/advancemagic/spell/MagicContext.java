package com.example.advancemagic.spell;

import com.example.advancemagic.AdvanceMagicPlugin;
import com.example.advancemagic.api.MagicAffectEvent;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.damage.*;
import org.bukkit.entity.*;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.potion.*;
import org.bukkit.util.*;
import org.bukkit.util.Vector;
import java.util.*;

public final class MagicContext {
    public final AdvanceMagicPlugin plugin;
    private final Map<UUID, Long> playerVelocityCooldown = new java.util.concurrent.ConcurrentHashMap<>();
    private final ThreadLocal<Boolean> magicDamageContext = ThreadLocal.withInitial(() -> Boolean.FALSE);

    public boolean isMagicDamage() {
        return magicDamageContext.get();
    }

    public MagicContext(AdvanceMagicPlugin plugin){this.plugin=plugin;}
    /** Server-issued impulse. Owners can disable player displacement without disabling damage. */
    public void velocity(Entity target,Vector velocity){
        velocity.checkFinite();
        if(target instanceof Player player){
            if(!plugin.getConfig().getBoolean("compatibility.player-spell-velocity",true)||player.isInsideVehicle()
                ||player.getGameMode()==GameMode.CREATIVE||player.getGameMode()==GameMode.SPECTATOR)return;

            long now = System.currentTimeMillis();
            Long next = playerVelocityCooldown.get(player.getUniqueId());
            if(next != null && now < next) return;
            playerVelocityCooldown.put(player.getUniqueId(), now + 300L);

            velocity=velocity.clone();
            double maxH = plugin.getConfig().getDouble("compatibility.max-player-horizontal-velocity", 1.1);
            double maxV = plugin.getConfig().getDouble("compatibility.max-player-vertical-velocity", 0.9);
            double horizontal=Math.hypot(velocity.getX(),velocity.getZ());
            if(horizontal>maxH){velocity.setX(velocity.getX()*maxH/horizontal);velocity.setZ(velocity.getZ()*maxH/horizontal);}
            velocity.setY(Math.max(-maxV,Math.min(maxV,velocity.getY())));
        }
        velocity.checkFinite();target.setVelocity(velocity);
    }

    public void clearPlayerVelocity(UUID uuid) {
        playerVelocityCooldown.remove(uuid);
    }
    public boolean loaded(Location at) {
        World w=at.getWorld();
        return w!=null&&at.getY()>=w.getMinHeight()&&at.getY()<w.getMaxHeight()
            &&w.isChunkLoaded(at.getBlockX()>>4,at.getBlockZ()>>4)&&w.getWorldBorder().isInside(at);
    }
    public boolean ally(Player p,LivingEntity e) {
        if(p.getUniqueId().equals(e.getUniqueId()))return true;
        if(e instanceof Tameable t&&t.getOwner()!=null&&t.getOwner().getUniqueId().equals(p.getUniqueId()))return true;
        var pdc = e.getPersistentDataContainer();
        NamespacedKey vexKey = new NamespacedKey(plugin, "allied_vex");
        if(pdc.has(vexKey, org.bukkit.persistence.PersistentDataType.STRING)) {
            String owner = pdc.get(vexKey, org.bukkit.persistence.PersistentDataType.STRING);
            if(p.getUniqueId().toString().equals(owner)) return true;
        }
        var board=Bukkit.getScoreboardManager().getMainScoreboard();
        var a=board.getEntryTeam(p.getName());
        String entry=e instanceof Player other?other.getName():e.getUniqueId().toString();
        return a!=null&&a.equals(board.getEntryTeam(entry));
    }
    public boolean enemy(Player p,Entity entity) {
        if(!(entity instanceof LivingEntity e)||e instanceof ArmorStand||!e.isValid()||e.isDead()||ally(p,e))return false;
        if(e instanceof Player target) {
            return plugin.getConfig().getBoolean("pvp",true)&&p.getWorld().getPVP()
                &&target.getGameMode()!=GameMode.CREATIVE&&target.getGameMode()!=GameMode.SPECTATOR;
        }
        return true;
    }
    private final Map<UUID, Double> castVelocityMultipliers = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<UUID, Double> castDamageMultipliers = new java.util.concurrent.ConcurrentHashMap<>();
    public void setCastDamageMultiplier(UUID uuid,double multiplier){if(multiplier<=1.0)castDamageMultipliers.remove(uuid);else castDamageMultipliers.put(uuid,multiplier);}
    public void clearCastDamageMultiplier(UUID uuid){castDamageMultipliers.remove(uuid);}
    public double getCastDamageMultiplier(UUID uuid){return castDamageMultipliers.getOrDefault(uuid,1.0);}

    public void setCastVelocityMultiplier(UUID uuid, double mult) {
        if (mult <= 1.0) castVelocityMultipliers.remove(uuid);
        else castVelocityMultipliers.put(uuid, mult);
    }
    public double getCastVelocityMultiplier(UUID uuid) {
        return castVelocityMultipliers.getOrDefault(uuid, 1.0);
    }
    public void clearCastVelocityMultiplier(UUID uuid) {
        castVelocityMultipliers.remove(uuid);
    }

    public boolean affect(Player p,LivingEntity target,Spell spell) {
        if(!target.isValid()||target.isDead()||target.getWorld()!=p.getWorld())return false;
        MagicAffectEvent event=new MagicAffectEvent(p,target,spell);
        Bukkit.getPluginManager().callEvent(event);return !event.isCancelled();
    }
    public List<LivingEntity> nearby(Player p,Location at,double radius,boolean friendly) {
        if(!loaded(at))return List.of();
        List<LivingEntity> result=new ArrayList<>();
        for(Entity e:at.getWorld().getNearbyEntities(at,radius,radius,radius))
            if(e instanceof LivingEntity living&&living.isValid()&&!living.isDead()&&!(e instanceof ArmorStand)
                &&e.getLocation().distanceSquared(at)<=radius*radius
                &&(friendly?ally(p,living):enemy(p,e)))result.add(living);
        result.sort(Comparator.comparingDouble(e->e.getLocation().distanceSquared(at)));
        int limit=Math.max(1,Math.min(128,plugin.getConfig().getInt("max-targets-per-effect",32)));
        return result.subList(0,Math.min(limit,result.size()));
    }
    public boolean clear(Location from,Location to) {
        if(from.getWorld()!=to.getWorld()||!loaded(from)||!loaded(to))return false;
        Vector v=to.toVector().subtract(from.toVector());double d=v.length();
        return d<0.2||from.getWorld().rayTraceBlocks(from,v,d-0.1,FluidCollisionMode.NEVER,true)==null;
    }
    public RayTraceResult target(Player p,double range) {
        // Trim to loaded chunks before tracing so casts never generate terrain.
        double effectiveRange = range * getCastVelocityMultiplier(p.getUniqueId());
        Location eye=p.getEyeLocation();Vector dir=eye.getDirection();double distance=0;
        for(double d=0.5;d<=effectiveRange;d+=0.5){if(!loaded(eye.clone().add(dir.clone().multiply(d))))break;distance=d;}
        return distance==0?null:p.getWorld().rayTrace(eye,dir,distance,FluidCollisionMode.NEVER,true,0.35,e->enemy(p,e));
    }
    public Location targetPoint(Player p,double range) {
        var hit=target(p,range);return hit==null?null:hit.getHitPosition().toLocation(p.getWorld());
    }
    public LivingEntity targetEntity(Player p,double range) {
        var hit=target(p,range);return hit!=null&&hit.getHitEntity() instanceof LivingEntity e?e:null;
    }
    public void potion(LivingEntity e,PotionEffectType type,int ticks,int amplifier) {
        e.addPotionEffect(new PotionEffect(type,ticks,amplifier,true,false,true));
    }
    /** Returns actual lost health, so absorption, resistance and cancellation cannot create free healing. */
    public double damage(Player p,LivingEntity e,double amount,DamageType type) {
        if(!enemy(p,e))return 0;
        double before=e.getHealth();
        e.setNoDamageTicks(0);
        magicDamageContext.set(Boolean.TRUE);
        try {
            e.damage(Math.max(0,amount*castDamageMultipliers.getOrDefault(p.getUniqueId(),1.0)),DamageSource.builder(type).withCausingEntity(p).withDirectEntity(p).build());
        } finally {
            magicDamageContext.set(Boolean.FALSE);
        }
        return Math.max(0,before-e.getHealth());
    }
    public void heal(Player p,double amount) {
        if(amount<=0||p.isDead())return;
        var event=new EntityRegainHealthEvent(p,amount,EntityRegainHealthEvent.RegainReason.MAGIC);
        Bukkit.getPluginManager().callEvent(event);
        if(!event.isCancelled())p.setHealth(Math.min(p.getAttribute(Attribute.MAX_HEALTH).getValue(),p.getHealth()+Math.max(0,event.getAmount())));
    }
    public void particles(Location at,Particle type,int count,double spread) {
        if(!loaded(at))return;
        if(type.getDataType()==Float.class)at.getWorld().spawnParticle(type,at,count,spread,spread,spread,0.02,1.0f);
        else if(type.getDataType()==Color.class)at.getWorld().spawnParticle(type,at,count,spread,spread,spread,0.02,Color.WHITE);
        else at.getWorld().spawnParticle(type,at,count,spread,spread,spread,0.02);
    }
    public void ring(Location at,double radius,Spell spell) {
        if(!loaded(at))return;
        for(int i=0;i<32;i++) {
            double angle=i*Math.PI/16;
            Location point=at.clone().add(Math.cos(angle)*radius,0.15,Math.sin(angle)*radius);
            if(loaded(point))point.getWorld().spawnParticle(Particle.DUST,point,1,0,0,0,0,new Particle.DustOptions(Color.fromRGB(spell.color),1.2f));
        }
    }
    public void beam(Location from,Location to,Particle type) {
        if(from.getWorld()!=to.getWorld())return;
        Vector delta=to.toVector().subtract(from.toVector());int n=Math.min(60,Math.max(1,(int)(delta.length()*2)));
        for(int i=0;i<=n;i++)particles(from.clone().add(delta.clone().multiply((double)i/n)),type,1,0);
    }
    public double configuredDamage(String key,double fallback) {
        double n=plugin.getConfig().getDouble(key,fallback);return Double.isFinite(n)?Math.max(0,Math.min(2000,n)):Math.max(0,Math.min(2000,fallback));
    }
    /** Automatic final pulse, owned by the original caster and cancelled with their effects. */
    public void echo(Player p,Location center,Spell spell,int delay,double radius,double baseDamage) {
        if(!plugin.effects().hasCapacity()||!loaded(center))return;
        Location at=center.clone();
        plugin.effects().start(p,delay+1,(effect,age)->{
            if(!loaded(at))return false;
            if(age==delay) {
                ring(at,radius,spell);
                particles(at.clone().add(0,0.5,0),Particle.ENCHANT,24,radius/3);
                at.getWorld().playSound(at,Sound.BLOCK_AMETHYST_BLOCK_RESONATE,0.8f,1.2f);
                for(var enemy:nearby(p,at,radius,false))if(affect(p,enemy,spell)) {
                    damage(p,enemy,configuredDamage("follow-up.damage."+spell.id(),baseDamage),DamageType.MAGIC);
                    potion(enemy,PotionEffectType.SLOWNESS,40,1);
                }
            }
            return true;
        });
    }
    public boolean safeBody(Location at) {
        if(!loaded(at)||!loaded(at.clone().add(0,1.8,0)))return false;
        BoundingBox body=new BoundingBox(at.getX()-0.3,at.getY()+0.01,at.getZ()-0.3,at.getX()+0.3,at.getY()+1.8,at.getZ()+0.3);
        for(int x=(int)Math.floor(body.getMinX());x<=Math.floor(body.getMaxX());x++)
            for(int y=(int)Math.floor(body.getMinY());y<=Math.floor(body.getMaxY());y++)
                for(int z=(int)Math.floor(body.getMinZ());z<=Math.floor(body.getMaxZ());z++) {
                    Location cell=new Location(at.getWorld(),x,y,z);if(!loaded(cell))return false;
                    var block=cell.getBlock();
                    if(!block.isPassable()&&block.getBoundingBox().overlaps(body))return false;
                    if(block.getType()==Material.LAVA||block.getType()==Material.FIRE)return false;
                }
        return true;
    }
}
