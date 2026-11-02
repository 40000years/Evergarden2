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
        // A summoned guardian or shadow fights for its owner and the owner's team.
        String summoner=e.getPersistentDataContainer().get(new NamespacedKey(plugin,"elemental_summon"),org.bukkit.persistence.PersistentDataType.STRING);
        if(summoner!=null){
            if(summoner.equals(p.getUniqueId().toString()))return true;
            Player owner;
            try{owner=Bukkit.getPlayer(UUID.fromString(summoner));}catch(IllegalArgumentException malformed){return false;}
            return owner!=null&&team(p,owner);
        }
        return team(p,e);
    }
    private boolean team(Player p,LivingEntity e){
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
        Location eye=p.getEyeLocation();Vector dir=eye.getDirection();double distance=0;
        for(double d=0.5;d<=range;d+=0.5){if(!loaded(eye.clone().add(dir.clone().multiply(d))))break;distance=d;}
        return distance==0?null:p.getWorld().rayTrace(eye,dir,distance,FluidCollisionMode.NEVER,true,0.35,e->enemy(p,e));
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
            e.damage(Math.max(0,amount),DamageSource.builder(type).withCausingEntity(p).withDirectEntity(p).build());
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
