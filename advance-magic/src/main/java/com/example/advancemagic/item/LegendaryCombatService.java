package com.example.advancemagic.item;

import com.example.advancemagic.AdvanceMagicPlugin;
import com.example.advancemagic.item.LegendaryWeaponService.Weapon;
import com.example.advancemagic.spell.LegendaryWeaponVisuals;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.damage.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.*;
import org.bukkit.util.Vector;
import java.util.*;

/** Server-owned combat; never accepts damage or target lists from a client. */
public final class LegendaryCombatService implements Listener,AutoCloseable {
    private record Pair(UUID owner,UUID target){}
    private record Fury(double damage,long lastHit){}
    private record Root(Player owner,LivingEntity target,Location anchor,long until){}
    private record Attack(double strength,double fall,boolean critical,long tick){}
    private final AdvanceMagicPlugin plugin;
    private final LegendaryWeaponService weapons;
    private final Map<Pair,Fury> fury=new HashMap<>();
    private final Map<UUID,Root> roots=new HashMap<>();
    private final Map<UUID,Long> drain=new HashMap<>(),charging=new HashMap<>();
    private final Map<UUID,Long> notices=new HashMap<>();
    private final Map<UUID,Attack> attacks=new HashMap<>();
    private long tick;
    private int skillDepth;
    public LegendaryCombatService(AdvanceMagicPlugin plugin,LegendaryWeaponService weapons){
        this.plugin=plugin;this.weapons=weapons;
        var defaults=plugin.getConfig().getDefaults();boolean changed=false;
        var section=defaults==null?null:defaults.getConfigurationSection("legendary-weapons.combat");
        if(section!=null)for(String key:section.getKeys(true))if(!section.isConfigurationSection(key)){
            String path="legendary-weapons.combat."+key;
            if(!plugin.getConfig().contains(path,true)){plugin.getConfig().set(path,section.get(key));changed=true;}
        }
        if(changed)plugin.saveConfig();
    }
    public double value(String path,double fallback){return Math.max(0,plugin.getConfig().getDouble("legendary-weapons.combat."+path,fallback));}
    private int duration(String path,double seconds){return Math.max(1,(int)Math.round(value(path,seconds)*20));}
    private boolean enabled(){return plugin.getConfig().getBoolean("legendary-weapons.combat.enabled",true);}
    public double base(Weapon weapon){return value(weapon.id()+".damage",switch(weapon){case EXCALIBUR->50;case TYRFING->35;case LONGINUS->20;case MJOLNIR->15;case VAJRA->30;});}
    private NamespacedKey cooldown(Weapon w){return new NamespacedKey(plugin,"legendary_cd_"+w.id());}
    public long cooldownRemaining(Player p,Weapon w){return Math.max(0,p.getPersistentDataContainer().getOrDefault(cooldown(w),PersistentDataType.LONG,0L)-System.currentTimeMillis());}
    public boolean charging(Player p){return charging.containsKey(p.getUniqueId());}
    private boolean held(Player p,Weapon w){return weapons.identify(p.getInventory().getItemInMainHand())==w;}
    private void notice(Player p,String message){if(tick-notices.getOrDefault(p.getUniqueId(),-20L)<20)return;notices.put(p.getUniqueId(),tick);p.sendActionBar(Component.text(message));}
    /** Called from both air swings and entity attacks. Cooldown prevents duplicate activation. */
    public boolean activate(Player p,Weapon w){
        if(!enabled()||p.isDead()||p.getGameMode()==GameMode.SPECTATOR||!held(p,w))return false;
        if(charging(p))return true;
        long remaining=cooldownRemaining(p,w);
        if(remaining>0){notice(p,w.title+" · "+(remaining+999)/1000+"s");return false;}
        if(!plugin.effects().hasCapacity()){notice(p,"Too many active magic effects");return false;}
        var center=p.getLocation();
        if(w==Weapon.LONGINUS)center=rainCenter(p);
        p.getPersistentDataContainer().set(cooldown(w),PersistentDataType.LONG,
            System.currentTimeMillis()+(long)(value(w.id()+".cooldown-seconds",180)*1000));
        p.sendActionBar(Component.text(w.title+" · "+switch(w){case EXCALIBUR->"Sacred release — charging";case TYRFING->"Soul feast";case LONGINUS->"Gates of heaven";case MJOLNIR->"Thunder dominion";case VAJRA->"Divine shockwave";}));
        final int seconds=switch(w){case EXCALIBUR->duration("excalibur.charge-seconds",3);case TYRFING->duration("tyrfing.duration-seconds",10);case LONGINUS->duration("longinus.duration-seconds",10);case MJOLNIR->duration("mjolnir.duration-seconds",30);case VAJRA->20;};
        double radius=value(w.id()+".radius",8);
        if(w==Weapon.EXCALIBUR)charging.put(p.getUniqueId(),tick+seconds);
        if(w==Weapon.TYRFING)drain.put(p.getUniqueId(),tick+seconds);
        Set<UUID> waveHits=new HashSet<>();
        var effect=LegendaryWeaponVisuals.ritual(plugin,p,w,center,seconds+1,radius,(age,at)->{
            if(w==Weapon.EXCALIBUR){
                if(!held(p,w))return false;
                if(age==seconds){charging.remove(p.getUniqueId());release(p);}
            }else if(w==Weapon.LONGINUS||w==Weapon.MJOLNIR){
                int interval=duration("pulse-seconds",1);
                if(age<seconds&&age%interval==0)for(var target:enemies(p,at,radius)){
                    if(w==Weapon.LONGINUS&&!openSky(at,target))continue;
                    if(w==Weapon.MJOLNIR){at.getWorld().strikeLightningEffect(target.getLocation());}
                    damage(p,target,value(w.id()+".skill-damage",10),w==Weapon.MJOLNIR?DamageType.LIGHTNING_BOLT:DamageType.TRIDENT);
                }
            }else if(w==Weapon.VAJRA){
                double edge=radius*Math.min(1,age/16.0);
                for(var target:enemies(p,at,edge))if(waveHits.add(target.getUniqueId()))
                    damage(p,target,value("vajra.skill-damage",50),DamageType.PLAYER_ATTACK);
            }
            return true;
        });
        effect.onClose(()->{if(w==Weapon.EXCALIBUR)charging.remove(p.getUniqueId());if(w==Weapon.TYRFING)drain.remove(p.getUniqueId());});
        return true;
    }
    private List<LivingEntity> enemies(Player p,Location at,double radius){
        return at.getWorld().getNearbyLivingEntities(at,radius,radius,radius).stream()
            .filter(e->plugin.context().enemy(p,e)&&e.getLocation().distanceSquared(at)<=radius*radius&&p.hasLineOfSight(e)).toList();
    }
    private Location rainCenter(Player p){
        var eye=p.getEyeLocation();var hit=p.getWorld().rayTrace(eye,eye.getDirection(),value("longinus.cast-range",16),FluidCollisionMode.NEVER,true,.2,e->plugin.context().enemy(p,e));
        var at=hit==null?p.getLocation().add(eye.getDirection().setY(0).normalize().multiply(8)):hit.getHitPosition().toLocation(p.getWorld());
        if(!plugin.context().loaded(at))return p.getLocation();
        var floor=p.getWorld().rayTraceBlocks(at.clone().add(0,2,0),new Vector(0,-1,0),8,FluidCollisionMode.NEVER,true);
        if(floor!=null)at.setY(floor.getHitPosition().getY()+.08);
        return at;
    }
    private boolean openSky(Location center,LivingEntity target){
        var eye=target.getEyeLocation();var roof=eye.clone();roof.setY(center.getY()+12);
        if(roof.getY()<=eye.getY())return false;
        return eye.getWorld().rayTraceBlocks(eye,new Vector(0,1,0),roof.getY()-eye.getY(),FluidCollisionMode.NEVER,true)==null;
    }
    public double damage(Player p,LivingEntity target,double amount,DamageType type){
        if(!plugin.context().enemy(p,target))return 0;
        double before=target.getHealth();int previous=target.getNoDamageTicks();
        skillDepth++;
        try{target.setNoDamageTicks(0);target.damage(amount,DamageSource.builder(type).withDirectEntity(p).withCausingEntity(p).build());}
        finally{skillDepth--;if(target.getHealth()==before)target.setNoDamageTicks(previous);}
        return Math.max(0,before-target.getHealth());
    }
    private void release(Player p){
        var eye=p.getEyeLocation();Vector direction=eye.getDirection();double range=value("excalibur.range",16);
        var wall=p.getWorld().rayTraceBlocks(eye,direction,range,FluidCollisionMode.NEVER,true);
        if(wall!=null)range=eye.toVector().distance(wall.getHitPosition());
        double length=range;
        LegendaryWeaponVisuals.release(plugin,p,Weapon.EXCALIBUR,eye,direction,length);
        for(var target:p.getWorld().getNearbyLivingEntities(eye,length,length,length)){
            if(!plugin.context().enemy(p,target)||!p.hasLineOfSight(target))continue;
            Vector d=target.getBoundingBox().getCenter().subtract(eye.toVector());double along=d.dot(direction);
            if(along<0||along>length||d.clone().subtract(direction.clone().multiply(along)).length()>value("excalibur.wave-width",3)/2)continue;
            damage(p,target,value("excalibur.skill-damage",100),DamageType.SONIC_BOOM);
        }
    }
    private boolean melee(EntityDamageByEntityEvent e){return skillDepth==0&&!plugin.context().isMagicDamage()&&e.getCause()==EntityDamageEvent.DamageCause.ENTITY_ATTACK;}
    public double furyDamage(Player p,LivingEntity target){
        var state=fury.get(new Pair(p.getUniqueId(),target.getUniqueId()));double base=base(Weapon.TYRFING);if(state==null)return base;
        long idle=tick-state.lastHit();long delay=duration("tyrfing.decay-delay-seconds",10);
        double steps=Math.max(0,(idle-delay)/20);
        return Math.max(base,state.damage()-steps*value("tyrfing.decay-per-second",5));
    }
    @EventHandler(priority=EventPriority.LOWEST,ignoreCancelled=true)
    public void beforeAttack(io.papermc.paper.event.player.PrePlayerAttackEntityEvent e){
        var p=e.getPlayer();if(!enabled()||!e.willAttack())return;
        var w=weapons.identify(p.getInventory().getItemInMainHand());if(w==null)return;
        attacks.put(p.getUniqueId(),new Attack(p.getAttackCooldown(),p.getFallDistance(),
            p.getAttackCooldown()>.9&&p.getFallDistance()>0&&!p.isOnGround()&&!p.isInWater()&&!p.isInsideVehicle()&&!p.isSprinting()&&!p.hasPotionEffect(PotionEffectType.BLINDNESS),tick));
        if(charging(p)){e.setCancelled(true);return;}
        if(p.isSneaking()&&activate(p,w)&&w!=Weapon.TYRFING)e.setCancelled(true);
    }
    private Attack attack(Player p){var a=attacks.get(p.getUniqueId());return a!=null&&a.tick()==tick?a:new Attack(1,p.getFallDistance(),false,tick);}
    private double charge(Player p){double c=attack(p).strength();return .2+.8*c*c;}
    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true)
    public void meleeDamage(EntityDamageByEntityEvent e){
        if(!enabled()||!melee(e)||!(e.getDamager() instanceof Player p)||!(e.getEntity() instanceof LivingEntity target))return;
        var w=weapons.identify(p.getInventory().getItemInMainHand());if(w==null)return;
        double bonus=0;
        if(w==Weapon.TYRFING)bonus=furyDamage(p,target)-base(w);
        if(w==Weapon.LONGINUS){
            var point=p.getEyeLocation().toVector();var b=target.getBoundingBox();
            double distance=point.distance(new Vector(Math.clamp(point.getX(),b.getMinX(),b.getMaxX()),Math.clamp(point.getY(),b.getMinY(),b.getMaxY()),Math.clamp(point.getZ(),b.getMinZ(),b.getMaxZ())));
            bonus=(value("longinus.max-damage",30)-base(w))*Math.clamp(distance/7,0,1);
        }
        if(w==Weapon.MJOLNIR&&attack(p).fall()>1.5&&!p.isGliding()){
            double f=attack(p).fall();double fall=f<=3?4*f:f<=8?12+2*(f-3):22+(f-8);
            fall+=p.getInventory().getItemInMainHand().getEnchantmentLevel(org.bukkit.enchantments.Enchantment.DENSITY)*.5*f;
            e.setDamage(e.getDamage()+fall*(value("mjolnir.smash-multiplier",4)-1)*charge(p)*(attack(p).critical()?1.5:1));return;
        }
        boolean critical=attack(p).critical();
        if(bonus!=0)e.setDamage(e.getDamage()+bonus*charge(p)*(critical?1.5:1));
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void hit(EntityDamageByEntityEvent e){
        if(!enabled()||!melee(e)||e.getFinalDamage()<=0||!(e.getDamager() instanceof Player p)||!(e.getEntity() instanceof LivingEntity target))return;
        if(!plugin.context().enemy(p,target))return;
        var w=weapons.identify(p.getInventory().getItemInMainHand());if(w==null)return;
        if(w==Weapon.TYRFING){
            double next=Math.min(value("tyrfing.max-damage",60),furyDamage(p,target)+value("tyrfing.per-hit",5));
            fury.put(new Pair(p.getUniqueId(),target.getUniqueId()),new Fury(next,tick));
            if(drain.getOrDefault(p.getUniqueId(),0L)>tick){
                plugin.context().heal(p,Math.min(target.getHealth(),e.getFinalDamage()));
                LegendaryWeaponVisuals.soul(plugin,p,target);
            }
        }else if(w==Weapon.LONGINUS)target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,duration("longinus.slow-seconds",3),1,true,true,true));
        else if(w==Weapon.VAJRA)root(p,target);
        else if(w==Weapon.MJOLNIR&&p.getFallDistance()>1.5){
            target.getWorld().strikeLightningEffect(target.getLocation());LegendaryWeaponVisuals.impact(plugin,p,Weapon.MJOLNIR,target.getLocation(),5);
        }
    }
    private NamespacedKey rootCooldown(){return new NamespacedKey(plugin,"vajra_root_cd");}
    private NamespacedKey rootCharges(){return new NamespacedKey(plugin,"vajra_root_charges");}
    public int rootCharges(Player p){
        if(p.getPersistentDataContainer().getOrDefault(rootCooldown(),PersistentDataType.LONG,0L)>System.currentTimeMillis())return 0;
        return p.getPersistentDataContainer().getOrDefault(rootCharges(),PersistentDataType.INTEGER,10);
    }
    private void root(Player p,LivingEntity target){
        if(target.getPersistentDataContainer().has(new NamespacedKey("7sins","control_resistant"),PersistentDataType.BYTE))return;
        int charges=rootCharges(p);if(charges<=0)return;
        charges--;p.getPersistentDataContainer().set(rootCharges(),PersistentDataType.INTEGER,charges==0?10:charges);
        if(charges==0)p.getPersistentDataContainer().set(rootCooldown(),PersistentDataType.LONG,System.currentTimeMillis()+(long)(value("vajra.root-cooldown-seconds",120)*1000));
        roots.put(target.getUniqueId(),new Root(p,target,target.getLocation(),tick+duration("vajra.root-seconds",3)));
        LegendaryWeaponVisuals.impact(plugin,p,Weapon.VAJRA,target.getLocation(),1.5);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void fall(EntityDamageEvent e){if(enabled()&&e.getCause()==EntityDamageEvent.DamageCause.FALL&&e.getEntity() instanceof Player p&&held(p,Weapon.MJOLNIR)){e.setCancelled(true);LegendaryWeaponVisuals.impact(plugin,p,Weapon.MJOLNIR,p.getLocation(),4);}}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void move(PlayerMoveEvent e){var root=roots.get(e.getPlayer().getUniqueId());if(root==null||e instanceof PlayerTeleportEvent)return;var to=root.anchor().clone();to.setYaw(e.getTo().getYaw());to.setPitch(e.getTo().getPitch());e.setTo(to);}
    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true)
    public void escape(PlayerTeleportEvent e){if(com.example.advancemagic.effect.StatusService.escapeTeleport(e.getCause())&&roots.containsKey(e.getPlayer().getUniqueId()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void teleported(PlayerTeleportEvent e){roots.remove(e.getPlayer().getUniqueId());}
    @EventHandler public void quit(PlayerQuitEvent e){clear(e.getPlayer().getUniqueId());}
    @EventHandler public void death(PlayerDeathEvent e){clear(e.getEntity().getUniqueId());}
    private void clear(UUID id){attacks.remove(id);charging.remove(id);drain.remove(id);notices.remove(id);fury.keySet().removeIf(k->k.owner().equals(id)||k.target().equals(id));roots.values().removeIf(r->r.owner().getUniqueId().equals(id)||r.target().getUniqueId().equals(id));}
    public void tick(){
        tick++;
        roots.values().removeIf(r->tick>=r.until()||!r.owner().isOnline()||r.owner().isDead()||!r.target().isValid()||r.target().isDead()||r.owner().getWorld()!=r.anchor().getWorld()||r.target().getWorld()!=r.anchor().getWorld());
        for(var r:List.copyOf(roots.values())){r.target().setVelocity(new Vector());if(!(r.target() instanceof Player)&&r.target().getLocation().distanceSquared(r.anchor())>.001)r.target().teleport(r.anchor());}
        if(tick%20==0){
            drain.values().removeIf(end->end<=tick);
            fury.entrySet().removeIf(entry->tick-entry.getValue().lastHit()>duration("tyrfing.decay-delay-seconds",10)+400);
        }
        if(tick%4==0)for(Player p:Bukkit.getOnlinePlayers())if(held(p,Weapon.MJOLNIR)&&p.getFallDistance()>1.5)
            p.getWorld().spawnParticle(Particle.ELECTRIC_SPARK,p.getLocation().add(0,1,0),8,.3,.5,.3,.04);
    }
    @Override public void close(){attacks.clear();fury.clear();roots.clear();drain.clear();charging.clear();notices.clear();}
}
