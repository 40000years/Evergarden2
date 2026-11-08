package com.example.advancemagic.item;

import com.example.advancemagic.AdvanceMagicPlugin;
import com.example.advancemagic.item.ArmorSetService.ArmorSet;
import com.example.advancemagic.item.ArmorSetService.Piece;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.tag.DamageTypeTags;
import org.bukkit.util.Vector;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Dragon Conqueror set powers (AM 1.40.0); every power needs all four pieces of one set.
 * <ul>
 * <li>Any dragon piece: vanilla clamps armor at 30 and toughness at 20, so the part of the wearer's real totals above
 * those caps becomes extra reduction of armor-respecting damage (full set: 27 armor and 12 toughness over, 21%).</li>
 * <li>Infernal Drake: fire and lava immunity, more damage the lower the wearer's HP, blaze fireballs on hit, last stand.</li>
 * <li>Frostwyrm: freeze immunity, fast on snow and walks over powder snow, damage taken builds reduction stacks,
 * a 15% chance to stun the target for 3 s, last stand.</li>
 * <li>Tempest Drake: lightning immunity; a lightning strike charges +300% damage for 4 s and 5 golden hearts (20 s
 * cooldown, never stacking); hits may call lightning on the target or the wearer, and it slays a non-player,
 * non-boss target below 100 HP outright.</li>
 * </ul>
 * Bonus damage (fireballs, lightning) is dealt one tick or a flight later through a normal attributed damage event,
 * so protection plugins and Evergarden's virtual-HP bosses treat it as the wearer's hit.
 */
public final class DragonSetPowers implements Listener,AutoCloseable {
    public static final double ARMOR_CAP=30,TOUGHNESS_CAP=20,ARMOR_POINT=.006,TOUGHNESS_POINT=.004,OVERCAP_MAX=.25;
    public static final long LAST_STAND_COOLDOWN=40_000L;
    public static final double LAST_STAND_HEALTH=.3;
    public static final double FIRE_RAGE=.75,FIREBALL_CHANCE=.25,FIREBALL_RATIO=.35,FIREBALL_MIN=4,FIREBALL_MAX=60;
    public static final long FIREBALL_COOLDOWN=1_000L;
    public static final double STUN_CHANCE=.15,FROST_STACK=.03,SNOW_SPEED=.4;
    public static final int STUN_TICKS=60,FROST_STACKS=10;
    public static final long STUN_GRACE=3_000L,FROST_HOLD=6_000L,FROST_DECAY=1_000L,FROST_GAIN=400L,THAW_DELAY=1_500L;
    public static final double STORM_CHANCE=.2,STORM_MULTIPLIER=4,STORM_HEARTS=10,EXECUTE_HEALTH=100,BOLT_BASE=6,BOLT_RATIO=.4,BOLT_MAX=80;
    public static final long STORM_PROC_COOLDOWN=1_500L,STORM_DURATION=4_000L,STORM_COOLDOWN=20_000L,STORM_HEARTS_DURATION=20_000L;
    private static final Set<String> BOSS_MARKERS=Set.of("world_boss_entity","sanctuary_boss","arkhaia_part","arkhaia_minion",
        "arkhaia_boss","seraphon_boss","boss_entity","boss","is_boss","dragon_nest_boss","realm_sanctuary_boss");

    private static final class State {
        long fireballReady,boltReady,stormUntil,stormReady,heartsUntil,frostHit,frostGain;int frost;
    }
    private static final class Flight {
        final SmallFireball ball;final Player owner;final LivingEntity target;final Vector from;final int ticks;final double damage;
        Vector last;int age;
        Flight(SmallFireball ball,Player owner,LivingEntity target,Vector from,int ticks,double damage,Vector last){
            this.ball=ball;this.owner=owner;this.target=target;this.from=from;this.ticks=ticks;this.damage=damage;this.last=last;
        }
    }
    private record Stun(Mob mob,long until){}

    private final AdvanceMagicPlugin plugin;
    private final ArmorSetService sets;
    private final NamespacedKey lastStand,stunKey,fireballKey,snowSpeed,stormHearts;
    private final Map<UUID,State> states=new HashMap<>();
    private final Map<UUID,ArmorSet> wearing=new HashMap<>();
    private final List<Flight> flights=new ArrayList<>();
    private final List<Runnable> pending=new ArrayList<>();
    private final Map<UUID,Long> stunGrace=new HashMap<>();
    private final Map<UUID,Stun> stunned=new HashMap<>();
    private final Map<Location,Long> hardened=new HashMap<>();
    private int depth,ticks;
    // Probe hooks: a fixed roll replaces chance, a clock offset replaces waiting.
    private Double fixedRoll;private Boolean fixedSelf;private long clockOffset;

    public DragonSetPowers(AdvanceMagicPlugin plugin,ArmorSetService sets){
        this.plugin=plugin;this.sets=sets;
        lastStand=new NamespacedKey(plugin,"dragon_last_stand");stunKey=new NamespacedKey(plugin,"dragon_stun");
        fireballKey=new NamespacedKey(plugin,"dragon_fireball");
        snowSpeed=new NamespacedKey("advance_magic","frostwyrm_snow_speed");stormHearts=new NamespacedKey("advance_magic","tempest_hearts");
    }

    // --- probe hooks ----------------------------------------------------------------------------------
    public void testRolls(Double roll,Boolean self){fixedRoll=roll;fixedSelf=self;}
    public void advanceClock(long millis){clockOffset+=millis;}
    /** Runs queued strikes now instead of on the next tick. */
    public void settle(){List<Runnable> work=new ArrayList<>(pending);pending.clear();work.forEach(Runnable::run);}
    public int frostStacks(Player p){State s=states.get(p.getUniqueId());return s==null?0:frost(s,now());}
    public boolean stormActive(Player p){State s=states.get(p.getUniqueId());return s!=null&&s.stormUntil>now();}
    public int flying(){return flights.size();}
    public int hardenedBlocks(){return hardened.size();}

    private long now(){return System.currentTimeMillis()+clockOffset;}
    private double roll(){return fixedRoll!=null?fixedRoll:ThreadLocalRandom.current().nextDouble();}
    private State state(Player p){return states.computeIfAbsent(p.getUniqueId(),id->new State());}

    public ArmorSet fullSet(Player p){
        for(ArmorSet set:ArmorSet.dragons())if(sets.full(p,set))return set;
        return null;
    }
    private boolean dragonPiece(Player p){
        for(Piece piece:Piece.values()){ArmorSet set=sets.set(p.getInventory().getItem(piece.slot));if(set!=null&&set.dragon())return true;}
        return false;
    }
    static boolean boss(LivingEntity entity){
        if(entity instanceof Boss)return true;
        for(NamespacedKey key:entity.getPersistentDataContainer().getKeys()){
            String name=key.getKey();if(BOSS_MARKERS.contains(name)||name.endsWith("_boss"))return true;
        }
        for(String tag:entity.getScoreboardTags()){
            String marker=tag.toLowerCase(Locale.ROOT);
            if(BOSS_MARKERS.contains(marker)||marker.equals("custom-boss")||marker.endsWith(":boss")||marker.endsWith("_boss"))return true;
        }
        return false;
    }
    private static Player attacker(EntityDamageEvent event){
        if(event.getDamageSource().getCausingEntity() instanceof Player player)return player;
        if(event instanceof EntityDamageByEntityEvent by){
            if(by.getDamager() instanceof Player player)return player;
            if(by.getDamager() instanceof Projectile projectile&&projectile.getShooter() instanceof Player player)return player;
        }
        return null;
    }
    /** An attribute's total with every modifier but without vanilla's clamp. */
    static double uncapped(LivingEntity entity,Attribute attribute){
        AttributeInstance instance=entity.getAttribute(attribute);if(instance==null)return 0;
        double add=instance.getBaseValue(),scalar=0,multiply=1;
        for(AttributeModifier modifier:instance.getModifiers())switch(modifier.getOperation()){
            case ADD_NUMBER->add+=modifier.getAmount();
            case ADD_SCALAR->scalar+=modifier.getAmount();
            case MULTIPLY_SCALAR_1->multiply*=1+modifier.getAmount();
        }
        return add*(1+scalar)*multiply;
    }
    /** Extra reduction for the wearer's armor and toughness above vanilla's caps. */
    public double overcap(Player p){
        if(!dragonPiece(p))return 0;
        double over=Math.max(0,uncapped(p,Attribute.ARMOR)-ARMOR_CAP)*ARMOR_POINT+Math.max(0,uncapped(p,Attribute.ARMOR_TOUGHNESS)-TOUGHNESS_CAP)*TOUGHNESS_POINT;
        return Math.min(OVERCAP_MAX,over);
    }
    private static double rage(Player p){
        var max=p.getAttribute(Attribute.MAX_HEALTH);if(max==null||max.getValue()<=0)return 0;
        return Math.max(0,Math.min(1,1-p.getHealth()/max.getValue()));
    }
    private int frost(State s,long now){
        if(s.frost<=0)return 0;
        long idle=now-s.frostHit;if(idle<FROST_HOLD)return s.frost;
        return Math.max(0,s.frost-(int)((idle-FROST_HOLD)/FROST_DECAY)-1);
    }
    private double hurt(Player p,LivingEntity target,double amount,DamageType type){
        double before=target.getHealth();int previous=target.getNoDamageTicks();depth++;
        try{target.setNoDamageTicks(0);target.damage(amount,DamageSource.builder(type).withCausingEntity(p).withDirectEntity(p).build());}
        finally{depth--;if(target.isValid()&&target.getHealth()==before)target.setNoDamageTicks(previous);}
        return Math.max(0,before-target.getHealth());
    }
    private static void bar(Player p,String text,int color){p.sendActionBar(Component.text(text,TextColor.color(color)));}

    // --- immunities -----------------------------------------------------------------------------------
    @EventHandler(priority=EventPriority.LOW,ignoreCancelled=true)
    public void immune(EntityDamageEvent e){
        if(!(e.getEntity() instanceof Player p))return;
        ArmorSet set=fullSet(p);if(set==null)return;
        DamageType type=e.getDamageSource().getDamageType();
        if(set==ArmorSet.INFERNAL_DRAKE&&DamageTypeTags.IS_FIRE.isTagged(type)){e.setCancelled(true);p.setFireTicks(0);}
        else if(set==ArmorSet.FROSTWYRM&&DamageTypeTags.IS_FREEZING.isTagged(type)){e.setCancelled(true);p.setFreezeTicks(0);}
        else if(set==ArmorSet.TEMPEST_DRAKE&&DamageTypeTags.IS_LIGHTNING.isTagged(type)){e.setCancelled(true);p.setFireTicks(0);charge(p);}
    }
    @EventHandler(priority=EventPriority.LOW,ignoreCancelled=true)
    public void combust(EntityCombustEvent e){
        if(!(e.getEntity() instanceof Player p))return;
        ArmorSet set=fullSet(p);
        if(set==ArmorSet.INFERNAL_DRAKE||set==ArmorSet.TEMPEST_DRAKE&&e instanceof EntityCombustByEntityEvent by&&by.getCombuster() instanceof LightningStrike)
            e.setCancelled(true);
    }

    // --- damage dealt ---------------------------------------------------------------------------------
    // High runs before Evergarden's virtual-HP handlers read the final damage at Highest.
    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true)
    public void outgoing(EntityDamageEvent e){
        if(depth>0||!(e.getEntity() instanceof LivingEntity target)||target instanceof ArmorStand)return;
        Player p=attacker(e);
        if(p==null||p==target||!Double.isFinite(e.getDamage())||e.getDamage()<=0)return;
        ArmorSet set=fullSet(p);if(set==null)return;
        long now=now();State s=state(p);double multiplier=1;
        if(set==ArmorSet.INFERNAL_DRAKE)multiplier+=FIRE_RAGE*rage(p);
        if(set==ArmorSet.TEMPEST_DRAKE&&s.stormUntil>now)multiplier*=STORM_MULTIPLIER;
        if(multiplier!=1)e.setDamage(e.getDamage()*multiplier);
        if(e.getDamageSource().getDamageType()==DamageType.THORNS)return;
        double hit=e.getDamage();
        switch(set){
            case INFERNAL_DRAKE->{
                if(s.fireballReady<=now&&roll()<FIREBALL_CHANCE){
                    s.fireballReady=now+FIREBALL_COOLDOWN;
                    fireball(p,target,Math.max(FIREBALL_MIN,Math.min(FIREBALL_MAX,hit*FIREBALL_RATIO)));
                }
            }
            case FROSTWYRM->{if(roll()<STUN_CHANCE)stun(p,target);}
            case TEMPEST_DRAKE->{
                if(s.boltReady<=now&&roll()<STORM_CHANCE){
                    s.boltReady=now+STORM_PROC_COOLDOWN;
                    boolean self=fixedSelf!=null?fixedSelf:ThreadLocalRandom.current().nextBoolean();
                    pending.add(()->bolt(p,target,hit,self));
                }
            }
            default->{}
        }
    }

    // --- damage taken ---------------------------------------------------------------------------------
    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true)
    public void incoming(EntityDamageEvent e){
        if(!(e.getEntity() instanceof Player p)||!Double.isFinite(e.getDamage())||e.getDamage()<=0)return;
        double factor=1;
        if(!DamageTypeTags.BYPASSES_ARMOR.isTagged(e.getDamageSource().getDamageType()))factor*=1-overcap(p);
        if(sets.full(p,ArmorSet.FROSTWYRM)){State s=states.get(p.getUniqueId());if(s!=null)factor*=1-frost(s,now())*FROST_STACK;}
        if(factor<1)e.setDamage(e.getDamage()*factor);
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void taken(EntityDamageEvent e){
        if(!(e.getEntity() instanceof Player p)||!(e.getFinalDamage()>0)||!sets.full(p,ArmorSet.FROSTWYRM))return;
        State s=state(p);long now=now();
        s.frost=frost(s,now);s.frostHit=now;
        if(now<s.frostGain||s.frost>=FROST_STACKS)return;
        s.frost++;s.frostGain=now+FROST_GAIN;
        p.getWorld().spawnParticle(Particle.SNOWFLAKE,p.getLocation().add(0,1,0),6+s.frost*2,.4,.6,.4,.02);
        if(s.frost==FROST_STACKS)p.getWorld().playSound(p.getLocation(),Sound.BLOCK_AMETHYST_BLOCK_RESONATE,.8f,1.6f);
    }

    // Registered after every plugin is enabled, like the Revenant revive, so overflow protection has settled.
    public void fatal(EntityDamageEvent e){
        if(e.isCancelled()||!(e.getEntity() instanceof Player p))return;
        if(p.isDead()||p.getGameMode()!=GameMode.SURVIVAL&&p.getGameMode()!=GameMode.ADVENTURE)return;
        double damage=e.getFinalDamage();
        if(!Double.isFinite(damage)||damage<p.getHealth()||e.getDamage()>=1e30)return; // /kill deals Float.MAX_VALUE
        ArmorSet set=fullSet(p);if(set!=ArmorSet.INFERNAL_DRAKE&&set!=ArmorSet.FROSTWYRM)return;
        long now=now();
        if(p.getPersistentDataContainer().getOrDefault(lastStand,PersistentDataType.LONG,0L)>now)return;
        p.getPersistentDataContainer().set(lastStand,PersistentDataType.LONG,now+LAST_STAND_COOLDOWN);
        e.setCancelled(true);
        double max=p.getAttribute(Attribute.MAX_HEALTH).getValue();
        p.setHealth(Math.max(1,Math.min(max,max*LAST_STAND_HEALTH)));
        p.setFireTicks(0);p.setFreezeTicks(0);p.setNoDamageTicks(30);
        if(e.getCause()==EntityDamageEvent.DamageCause.VOID){
            Location safe=p.getRespawnLocation();if(safe==null)safe=p.getWorld().getSpawnLocation();
            p.teleport(safe);p.setFallDistance(0);p.setVelocity(new Vector());
        }
        p.saveData();
        Location at=p.getLocation().add(0,1,0);
        if(set==ArmorSet.INFERNAL_DRAKE){
            p.getWorld().spawnParticle(Particle.FLAME,at,60,.5,.9,.5,.12);
            p.getWorld().spawnParticle(Particle.LAVA,at,12,.4,.6,.4,0);
            p.getWorld().playSound(at,Sound.ENTITY_BLAZE_SHOOT,1f,.6f);
            bar(p,"🔥 อสูรมังกรเพลิง — ไม่ยอมตาย! (คูลดาวน์ 40 วินาที)",0xFF8A2A);
        }else{
            p.getWorld().spawnParticle(Particle.SNOWFLAKE,at,70,.5,.9,.5,.08);
            p.getWorld().spawnParticle(Particle.END_ROD,at,14,.4,.7,.4,.05);
            p.getWorld().playSound(at,Sound.BLOCK_GLASS_BREAK,1f,.7f);
            bar(p,"❄ มังกรน้ำแข็ง — ไม่ยอมตาย! (คูลดาวน์ 40 วินาที)",0x9BE6FF);
        }
        p.getWorld().playSound(at,Sound.ITEM_TOTEM_USE,.8f,1.2f);
    }
    public void registerSurvival(){
        Bukkit.getPluginManager().registerEvent(EntityDamageEvent.class,new Listener(){},EventPriority.HIGHEST,
            (listener,event)->fatal((EntityDamageEvent)event),plugin,true);
    }

    // --- Infernal Drake: blaze fireballs --------------------------------------------------------------
    private static Vector center(LivingEntity e){return e.getBoundingBox().getCenter();}
    private void fireball(Player p,LivingEntity target,double damage){
        Location eye=p.getEyeLocation();Vector look=eye.getDirection();
        Vector side=look.clone().crossProduct(new Vector(0,1,0));
        if(side.lengthSquared()<1e-6)side=new Vector(1,0,0);
        side.normalize().multiply(ThreadLocalRandom.current().nextBoolean()?.7:-.7);
        Location from=eye.clone().add(0,.8,0).add(side).subtract(look.clone().multiply(.3));
        Vector end=center(target);
        int flight=(int)Math.max(8,Math.min(30,Math.ceil(from.toVector().distance(end)/1.1)));
        SmallFireball ball=p.getWorld().spawn(from,SmallFireball.class,b->{
            b.setShooter(p);b.setIsIncendiary(false);b.setYield(0);b.setVelocity(new Vector());b.setAcceleration(new Vector());
            b.setPersistent(false);b.getPersistentDataContainer().set(fireballKey,PersistentDataType.BYTE,(byte)1);
        });
        flights.add(new Flight(ball,p,target,from.toVector(),flight,damage,end));
        p.getWorld().playSound(from,Sound.ENTITY_BLAZE_SHOOT,.9f,1.1f);
    }
    private static java.lang.reflect.Method handle,fireImmune;
    /** Native fire immunity (blazes, striders, Nether bosses); the API exposes none, so read the server entity. */
    static boolean fireImmune(Entity entity){
        try{
            if(handle==null)handle=Class.forName("org.bukkit.craftbukkit.entity.CraftEntity").getMethod("getHandle");
            Object nms=handle.invoke(entity);
            if(fireImmune==null)fireImmune=Class.forName("net.minecraft.world.entity.Entity").getMethod("fireImmune");
            return (boolean)fireImmune.invoke(nms);
        }catch(ReflectiveOperationException|RuntimeException error){return false;}
    }
    private boolean ours(Entity entity){return entity!=null&&entity.getPersistentDataContainer().has(fireballKey,PersistentDataType.BYTE);}
    @EventHandler(priority=EventPriority.LOWEST)
    public void fireballHit(ProjectileHitEvent e){if(ours(e.getEntity()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.LOWEST)
    public void fireballDamage(EntityDamageByEntityEvent e){if(depth==0&&ours(e.getDamager()))e.setCancelled(true);}
    private void fly(){
        for(Iterator<Flight> it=flights.iterator();it.hasNext();){
            Flight f=it.next();f.age++;
            if(!f.ball.isValid()){it.remove();continue;}
            boolean alive=f.target.isValid()&&!f.target.isDead()&&f.target.getWorld()==f.ball.getWorld();
            if(alive)f.last=center(f.target);
            double t=Math.min(1,f.age/(double)f.ticks);
            Vector at=f.from.clone().add(f.last.clone().subtract(f.from).multiply(t));
            at.setY(at.getY()+Math.sin(Math.PI*t)*Math.min(2.5,f.ticks*.08));
            Location to=at.toLocation(f.ball.getWorld());
            f.ball.teleport(to);
            f.ball.getWorld().spawnParticle(Particle.FLAME,to,3,.08,.08,.08,.01);
            if(f.age<f.ticks)continue;
            it.remove();f.ball.remove();
            to.getWorld().spawnParticle(Particle.FLAME,to,26,.45,.45,.45,.07);
            to.getWorld().spawnParticle(Particle.LAVA,to,5,.3,.3,.3,0);
            to.getWorld().playSound(to,Sound.ENTITY_GENERIC_BURN,.8f,1.3f);
            if(!alive||!f.owner.isOnline()||!plugin.context().enemy(f.owner,f.target))continue;
            // Fire-immune targets (Nether mobs) still take the blast.
            boolean immune=fireImmune(f.target)||f.target.hasPotionEffect(PotionEffectType.FIRE_RESISTANCE);
            hurt(f.owner,f.target,f.damage,immune?DamageType.PLAYER_EXPLOSION:DamageType.FIREBALL);
            if(!immune&&!boss(f.target)&&f.target.isValid())f.target.setFireTicks(Math.max(f.target.getFireTicks(),60));
        }
    }

    // --- Frostwyrm: stun, snow ------------------------------------------------------------------------
    private void stun(Player p,LivingEntity target){
        if(boss(target)||!plugin.context().enemy(p,target))return;
        long now=now();Long grace=stunGrace.get(target.getUniqueId());
        if(grace!=null&&grace>now)return;
        long until=now+STUN_TICKS*50L;
        stunGrace.put(target.getUniqueId(),until+STUN_GRACE);
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,STUN_TICKS,9,false,true,true));
        target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS,STUN_TICKS,9,false,true,true));
        target.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE,STUN_TICKS,2,false,false,true));
        if(target instanceof Mob mob&&mob.isAware()){
            mob.setAware(false);mob.getPersistentDataContainer().set(stunKey,PersistentDataType.LONG,until);
            stunned.put(mob.getUniqueId(),new Stun(mob,until));
        }
        Vector v=target.getVelocity();target.setVelocity(new Vector(0,Math.min(0,v.getY()),0));
        Location at=target.getLocation().add(0,target.getHeight()/2,0);
        target.getWorld().spawnParticle(Particle.SNOWFLAKE,at,40,target.getWidth()/2+.2,target.getHeight()/2,target.getWidth()/2+.2,.03);
        target.getWorld().spawnParticle(Particle.END_ROD,at,8,.3,.4,.3,.02);
        target.getWorld().playSound(at,Sound.BLOCK_GLASS_BREAK,1f,1.5f);
        target.getWorld().playSound(at,Sound.ENTITY_PLAYER_HURT_FREEZE,1f,.8f);
        if(target instanceof Player victim)bar(victim,"❄ ถูกแช่แข็งชะงัก 3 วินาที",0x9BE6FF);
    }
    public boolean stunned(LivingEntity target){return stunned.containsKey(target.getUniqueId());}
    private void thaw(Stun stun){
        Mob mob=stun.mob();if(!mob.isValid())return;
        mob.setAware(true);mob.getPersistentDataContainer().remove(stunKey);
    }
    @EventHandler
    public void loaded(EntitiesLoadEvent e){
        // A stunned mob saved with its chunk wakes up when it loads again.
        for(Entity entity:e.getEntities())if(entity instanceof Mob mob&&mob.getPersistentDataContainer().has(stunKey,PersistentDataType.LONG)){
            mob.setAware(true);mob.getPersistentDataContainer().remove(stunKey);
        }
    }
    private static boolean snowy(Material type){return type==Material.SNOW||type==Material.SNOW_BLOCK||type==Material.POWDER_SNOW;}
    private static boolean onSnow(Location at){
        Material feet=at.getBlock().getType();
        return feet==Material.SNOW||feet==Material.POWDER_SNOW||snowy(at.clone().subtract(0,.2,0).getBlock().getType());
    }
    private void snowSpeed(Player p,boolean on){
        AttributeInstance speed=p.getAttribute(Attribute.MOVEMENT_SPEED);if(speed==null)return;
        boolean has=speed.getModifier(snowSpeed)!=null;
        if(on&&!has)speed.addTransientModifier(new AttributeModifier(snowSpeed,SNOW_SPEED,AttributeModifier.Operation.MULTIPLY_SCALAR_1));
        else if(!on&&has)speed.removeModifier(snowSpeed);
    }
    /** Powder snow under and around a Frostwyrm wearer firms up into snow until they leave; sneaking sinks as usual. */
    public void harden(Player p,Location at){
        if(p.isSneaking())return;
        World world=at.getWorld();int top=(int)Math.floor(at.getY()-.95),cx=at.getBlockX(),cz=at.getBlockZ();long now=now();
        for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++){
            if(!world.isChunkLoaded((cx+dx)>>4,(cz+dz)>>4))continue;
            for(int y=top-1;y<=top;y++){
                Block block=world.getBlockAt(cx+dx,y,cz+dz);Location key=block.getLocation();
                if(block.getType()==Material.POWDER_SNOW){block.setType(Material.SNOW_BLOCK,false);hardened.put(key,now);}
                else if(hardened.containsKey(key))hardened.put(key,now);
            }
        }
    }
    private void restore(Location key){
        Block block=key.getBlock();if(block.getType()==Material.SNOW_BLOCK)block.setType(Material.POWDER_SNOW,false);
    }
    private void thawSnow(long now){
        List<Player> frost=new ArrayList<>();
        for(Player p:Bukkit.getOnlinePlayers())if(wearing.get(p.getUniqueId())==ArmorSet.FROSTWYRM)frost.add(p);
        for(Iterator<Map.Entry<Location,Long>> it=hardened.entrySet().iterator();it.hasNext();){
            var entry=it.next();Location key=entry.getKey();World world=key.getWorld();
            if(world==null||!world.isChunkLoaded(key.getBlockX()>>4,key.getBlockZ()>>4)){it.remove();continue;}
            if(key.getBlock().getType()!=Material.SNOW_BLOCK){it.remove();continue;}
            boolean near=false;
            for(Player p:frost){
                Location at=p.getLocation();
                if(at.getWorld()==world&&Math.abs(at.getX()-key.getX()-.5)<3.5&&Math.abs(at.getZ()-key.getZ()-.5)<3.5&&Math.abs(at.getY()-key.getY())<4){near=true;break;}
            }
            if(near)entry.setValue(now);
            else if(now-entry.getValue()>=THAW_DELAY){restore(key);it.remove();}
        }
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void move(PlayerMoveEvent e){
        Player p=e.getPlayer();if(wearing.get(p.getUniqueId())!=ArmorSet.FROSTWYRM)return;
        Location to=e.getTo();
        harden(p,to);
        Vector step=to.toVector().subtract(e.getFrom().toVector());step.setY(0);
        if(step.lengthSquared()>1e-4)harden(p,to.clone().add(step.normalize().multiply(2.5)));
        boolean snow=onSnow(to);snowSpeed(p,snow);
        if(snow&&p.isSprinting()&&(ticks&1)==0)p.getWorld().spawnParticle(Particle.SNOWFLAKE,to.clone().add(0,.1,0),2,.2,.05,.2,.01);
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void broken(BlockBreakEvent e){
        // A firmed-up block is still powder snow underneath: it never drops snowballs.
        if(hardened.remove(e.getBlock().getLocation())!=null){e.setDropItems(false);e.setExpToDrop(0);}
    }
    @EventHandler
    public void unload(ChunkUnloadEvent e){
        for(Iterator<Location> it=hardened.keySet().iterator();it.hasNext();){
            Location key=it.next();
            if(key.getWorld()==e.getWorld()&&key.getBlockX()>>4==e.getChunk().getX()&&key.getBlockZ()>>4==e.getChunk().getZ()){restore(key);it.remove();}
        }
    }

    // --- Tempest Drake: lightning ---------------------------------------------------------------------
    /** A lightning strike on the wearer: +300% damage for 4 s and 5 golden hearts; never stacks, 20 s cooldown. */
    public boolean charge(Player p){
        State s=state(p);long now=now();
        if(s.stormReady>now)return false;
        s.stormReady=now+STORM_COOLDOWN;s.stormUntil=now+STORM_DURATION;s.heartsUntil=now+STORM_HEARTS_DURATION;
        AttributeInstance max=p.getAttribute(Attribute.MAX_ABSORPTION);
        if(max!=null&&max.getModifier(stormHearts)==null)max.addTransientModifier(new AttributeModifier(stormHearts,STORM_HEARTS,AttributeModifier.Operation.ADD_NUMBER));
        p.setAbsorptionAmount(Math.max(p.getAbsorptionAmount(),STORM_HEARTS));
        Location at=p.getLocation().add(0,1,0);
        p.getWorld().spawnParticle(Particle.ELECTRIC_SPARK,at,50,.5,.9,.5,.25);
        p.getWorld().spawnParticle(Particle.END_ROD,at,18,.4,.8,.4,.08);
        p.getWorld().playSound(at,Sound.BLOCK_BEACON_POWER_SELECT,1f,1.8f);
        bar(p,"⚡ พลังอัสนี! ดาเมจ x4 (+300%) 4 วินาที · หัวใจทอง 5 ดวง",0xFFD36B);
        return true;
    }
    private void bolt(Player p,LivingEntity target,double hit,boolean self){
        if(!p.isOnline()||p.isDead())return;
        if(self){p.getWorld().strikeLightningEffect(p.getLocation());charge(p);return;}
        if(!target.isValid()||target.isDead()||target.getWorld()!=p.getWorld())return;
        target.getWorld().strikeLightningEffect(target.getLocation());
        if(!plugin.context().enemy(p,target))return;
        // Below 100 HP a struck monster dies outright; players and bosses only take the bolt.
        if(!(target instanceof Player)&&!boss(target)&&!target.isInvulnerable()&&target.getHealth()<EXECUTE_HEALTH){
            hurt(p,target,target.getHealth()+target.getAbsorptionAmount()+1,DamageType.GENERIC_KILL);
            target.getWorld().spawnParticle(Particle.ELECTRIC_SPARK,target.getLocation().add(0,target.getHeight()/2,0),40,.4,.6,.4,.3);
            return;
        }
        hurt(p,target,Math.min(BOLT_MAX,BOLT_BASE+hit*BOLT_RATIO),DamageType.LIGHTNING_BOLT);
    }
    private void clearHearts(Player p){
        AttributeInstance max=p.getAttribute(Attribute.MAX_ABSORPTION);
        if(max==null||max.getModifier(stormHearts)==null)return;
        max.removeModifier(stormHearts);
        // Paper only clamps absorption to the new maximum on the entity's next tick; do it now.
        p.setAbsorptionAmount(Math.min(p.getAbsorptionAmount(),max.getValue()));
    }

    // --- ticking --------------------------------------------------------------------------------------
    /** Every server tick: fireball flights and queued strikes; every fifth tick: worn sets, auras, stuns, snow. */
    public void tick(){
        fly();settle();
        if(++ticks%5!=0)return;
        long now=now();
        for(Player p:Bukkit.getOnlinePlayers()){
            ArmorSet set=fullSet(p);
            if(set==null)wearing.remove(p.getUniqueId());else wearing.put(p.getUniqueId(),set);
            if(set==ArmorSet.INFERNAL_DRAKE&&p.getFireTicks()>0)p.setFireTicks(0);
            if(set==ArmorSet.FROSTWYRM&&p.getFreezeTicks()>0)p.setFreezeTicks(0);
            snowSpeed(p,set==ArmorSet.FROSTWYRM&&onSnow(p.getLocation()));
            State s=states.get(p.getUniqueId());if(s==null)continue;
            if(s.heartsUntil<=now)clearHearts(p);
            if(s.stormUntil>now&&ticks%10==0)p.getWorld().spawnParticle(Particle.ELECTRIC_SPARK,p.getLocation().add(0,1,0),8,.35,.6,.35,.15);
        }
        for(Iterator<Stun> it=stunned.values().iterator();it.hasNext();){
            Stun stun=it.next();if(stun.until()<=now||!stun.mob().isValid()){thaw(stun);it.remove();}
        }
        stunGrace.values().removeIf(until->until<=now);
        thawSnow(now);
    }
    @EventHandler
    public void quit(PlayerQuitEvent e){
        Player p=e.getPlayer();wearing.remove(p.getUniqueId());snowSpeed(p,false);clearHearts(p);
    }
    @Override public void close(){
        for(Flight f:flights)if(f.ball.isValid())f.ball.remove();
        flights.clear();pending.clear();
        stunned.values().forEach(this::thaw);stunned.clear();
        hardened.keySet().forEach(this::restore);hardened.clear();
        for(Player p:Bukkit.getOnlinePlayers()){snowSpeed(p,false);clearHearts(p);}
        wearing.clear();states.clear();
    }
}
