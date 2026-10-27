package com.example.voidscape.boss;

import com.example.voidscape.VoidscapePlugin;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.event.entity.*;
import org.bukkit.potion.*;
import java.util.*;
import java.util.function.*;

/** Encounter-owned soul anchors, reveal windows and temporary healing seals. */
final class BansheeSouls implements AutoCloseable {
    static final class State {
        Mob mob;boolean passive;long exposedUntil,transitionUntil;String transition="";
        final List<Anchor> anchors=new ArrayList<>();
        final Set<UUID> contributors=new HashSet<>();
    }
    private static final class Anchor {
        final State owner;final Slime receiver;final BlockDisplay crystal;final TextDisplay label;
        final Location at;
        double health=30;final Map<UUID,Long> hits=new HashMap<>();
        Anchor(State owner,Slime receiver,BlockDisplay crystal,TextDisplay label){this.owner=owner;this.receiver=receiver;this.crystal=crystal;this.label=label;this.at=receiver.getLocation();}
    }
    private static final class Seal {
        final State owner;final Player player;final long until;final PotionEffect previousHunger;
        double health,absorption;
        Seal(State owner,Player player,long until){this.owner=owner;this.player=player;this.until=until;health=player.getHealth();absorption=player.getAbsorptionAmount();previousHunger=player.getPotionEffect(PotionEffectType.HUNGER);}
    }
    private final VoidscapePlugin plugin;private final Location center;
    private final Predicate<Player> inside;private final LongSupplier clock;private final JudgeSigils sigils;
    private final List<State> states=new ArrayList<>();private final Map<UUID,Seal> seals=new HashMap<>();
    BansheeSouls(VoidscapePlugin plugin,Location center,Predicate<Player> inside,LongSupplier clock,JudgeSigils sigils){this.plugin=plugin;this.center=center;this.inside=inside;this.clock=clock;this.sigils=sigils;}
    State bind(State state,Mob mob,boolean passive){
        if(state==null){state=new State();states.add(state);}state.mob=mob;state.passive=passive;
        if(!passive&&state.anchors.isEmpty()&&state.exposedUntil<=clock.getAsLong())anchors(state);
        return state;
    }
    boolean shielded(State state){return state!=null&&!state.passive&&!state.anchors.isEmpty();}
    String transition(State state){return state!=null&&clock.getAsLong()<state.transitionUntil?state.transition:null;}
    String status(State state){
        if(state==null||state.passive)return "";
        return shielded(state)?" | SOUL ANCHORS "+state.anchors.size()+"/3":" | EXPOSED "+Math.max(0,(state.exposedUntil-clock.getAsLong()+19)/20)+"s";
    }
    private boolean space(Location at){
        if(Math.hypot(at.getX()-center.getX(),at.getZ()-center.getZ())>37||!at.clone().subtract(0,1,0).getBlock().getType().isSolid())return false;
        for(double x:new double[]{-.6,.6})for(double z:new double[]{-.6,.6})for(int y=0;y<3;y++)if(!at.clone().add(x,y,z).getBlock().isPassable())return false;
        return true;
    }
    private void anchors(State state){
        List<Location> spots=new ArrayList<>();Location origin=state.mob.getLocation();origin.setY(center.getY());
        for(double radius:new double[]{7,5,9})for(int i=0;i<12&&spots.size()<3;i++){
            int[] order={0,4,8,1,5,9,2,6,10,3,7,11};double angle=order[i]*Math.PI/6;Location at=origin.clone().add(Math.cos(angle)*radius,0,Math.sin(angle)*radius);
            if(space(at)&&spots.stream().allMatch(p->p.distanceSquared(at)>=6*6))spots.add(at);
        }
        if(spots.size()!=3){state.exposedUntil=clock.getAsLong()+240;return;} // Never create an unwinnable shield in obstructed terrain.
        for(Location ground:spots){
            Location at=ground.clone().add(0,.3,0);
            Slime receiver=at.getWorld().spawn(at,Slime.class,e->{
                e.setSize(2);e.setAI(false);e.setGravity(false);e.setSilent(true);e.setInvisible(true);e.setPersistent(false);e.setRemoveWhenFarAway(false);
                e.getAttribute(Attribute.KNOCKBACK_RESISTANCE).setBaseValue(1);
                e.getAttribute(Attribute.ATTACK_DAMAGE).setBaseValue(0);e.getAttribute(Attribute.MAX_HEALTH).setBaseValue(30);e.setHealth(30);
                e.customName(net.kyori.adventure.text.Component.text("Soul Anchor | 30 / 30"));e.setCustomNameVisible(true);
                e.addScoreboardTag("evergarden_unique");e.addScoreboardTag("banshee_soul_anchor");
                e.getPersistentDataContainer().set(plugin.key("world_boss_entity"),org.bukkit.persistence.PersistentDataType.BYTE,(byte)1);
                com.example.voidscape.compat.LevelledMobsCompat.tagMob(e,plugin,true);
            });
            BlockDisplay crystal=at.getWorld().spawn(at,BlockDisplay.class,e->{
                e.setPersistent(false);e.setInvulnerable(true);e.setBlock(Material.AMETHYST_BLOCK.createBlockData());
                e.setBrightness(new Display.Brightness(15,15));
                e.setTransformation(new org.bukkit.util.Transformation(new org.joml.Vector3f(-.52f,0,-.52f),new org.joml.Quaternionf(),new org.joml.Vector3f(1.04f),new org.joml.Quaternionf()));
            });
            TextDisplay label=at.getWorld().spawn(at.clone().add(0,1.6,0),TextDisplay.class,e->{e.setPersistent(false);e.setBillboard(Display.Billboard.CENTER);e.text(net.kyori.adventure.text.Component.text("Soul Anchor | 30 / 30"));});
            state.anchors.add(new Anchor(state,receiver,crystal,label));
            ground.getWorld().spawnParticle(Particle.SOUL,ground.clone().add(0,1,0),24,.5,.6,.5,.02);
        }
        for(Player p:state.mob.getWorld().getPlayers())if(inside.test(p))plugin.message(p,"\u00a73Banshee's veil is sealed by 3 Soul Anchors. Destroy the crystals!");
    }
    private Anchor anchor(Entity entity){for(State state:states)for(Anchor a:state.anchors)if(a.receiver==entity||a.crystal==entity||a.label==entity)return a;return null;}
    boolean owns(Entity entity){return anchor(entity)!=null;}
    boolean damage(EntityDamageEvent event,Player player){
        Anchor a=anchor(event.getEntity());if(a==null)return false;
        double damage=event.getFinalDamage();event.setCancelled(true);
        if(player==null||!inside.test(player)||!Double.isFinite(damage)||damage<=0)return true;
        if(event instanceof EntityDamageByEntityEvent by&&by.getDamager()==player){
            var start=player.getEyeLocation();var direction=start.getDirection();double reach=player.getAttribute(Attribute.ENTITY_INTERACTION_RANGE).getValue();
            var box=a.receiver.getBoundingBox().expand(.12);var contact=box.rayTrace(start.toVector(),direction,reach);
            if(contact==null||!clear(start,contact.getHitPosition().toLocation(start.getWorld())))return true;
        }
        long now=clock.getAsLong();if(now<a.hits.getOrDefault(player.getUniqueId(),0L))return true;a.hits.put(player.getUniqueId(),now+10);
        double before=a.health;a.health=Math.max(0,a.health-damage);
        ArkhaiaHitFeedback.confirm(player,a.receiver,event,before-a.health,false);
        a.label.text(net.kyori.adventure.text.Component.text("Soul Anchor | "+(int)Math.ceil(a.health)+" / 30"));
        a.owner.contributors.add(player.getUniqueId());
        a.receiver.customName(net.kyori.adventure.text.Component.text("Soul Anchor | "+(int)Math.ceil(a.health)+" / 30"));
        a.receiver.getWorld().playSound(a.receiver.getLocation(),Sound.BLOCK_AMETHYST_BLOCK_HIT,1,1.2f);
        if(a.health==0){
            State owner=a.owner;remove(a);owner.anchors.remove(a);
            if(owner.anchors.isEmpty()){
                owner.exposedUntil=now+240;owner.transition="reveal";owner.transitionUntil=now+20;
                owner.mob.getWorld().playSound(owner.mob.getLocation(),Sound.BLOCK_GLASS_BREAK,1.5f,.6f);
                owner.mob.getWorld().spawnParticle(Particle.SOUL,owner.mob.getLocation().add(0,4,0),90,2,3,2,.1);
                for(Player p:owner.mob.getWorld().getPlayers())if(inside.test(p))plugin.message(p,"\u00a7bBanshee revealed! Attack her true form for 12 seconds!");
            }
        }
        return true;
    }
    private boolean clear(Location from,Location to){var d=to.toVector().subtract(from.toVector());return d.lengthSquared()<.001||from.getWorld().rayTraceBlocks(from,d.clone().normalize(),d.length(),FluidCollisionMode.NEVER,true)==null;}
    void curse(State owner,Player player){
        Seal previous=seals.remove(player.getUniqueId());if(previous!=null)release(previous);
        Seal seal=new Seal(owner,player,clock.getAsLong()+200);seals.put(player.getUniqueId(),seal);
        player.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER,200,1,false,true,true));
        player.sendActionBar(net.kyori.adventure.text.Component.text("SOUL SEAL: healing blocked for 10 seconds"));
    }
    private boolean active(Seal seal){return seal!=null&&clock.getAsLong()<seal.until&&seal.player.isOnline()&&!seal.player.isDead()&&inside.test(seal.player)&&states.contains(seal.owner)&&seal.owner.mob.isValid();}
    boolean healing(EntityRegainHealthEvent event){Seal seal=seals.get(event.getEntity().getUniqueId());if(!active(seal))return false;event.setCancelled(true);return true;}
    boolean potion(EntityPotionEffectEvent event){
        Seal seal=seals.get(event.getEntity().getUniqueId());if(!active(seal)||event.getNewEffect()==null||event.getNewEffect().getType()!=PotionEffectType.ABSORPTION)return false;
        event.setCancelled(true);return true;
    }
    void departed(Player player){Seal seal=seals.remove(player.getUniqueId());if(seal!=null)release(seal);}
    private void release(Seal seal){
        PotionEffect current=seal.player.getPotionEffect(PotionEffectType.HUNGER);
        if(current!=null&&current.getAmplifier()==1&&current.getDuration()<=200){
            seal.player.removePotionEffect(PotionEffectType.HUNGER);
            if(seal.previousHunger!=null){int remaining=seal.previousHunger.getDuration()-(int)(clock.getAsLong()-(seal.until-200));if(remaining>0)seal.player.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER,remaining,seal.previousHunger.getAmplifier(),seal.previousHunger.isAmbient(),seal.previousHunger.hasParticles(),seal.previousHunger.hasIcon()));}
        }
    }
    void tick(){
        long now=clock.getAsLong();
        for(State state:new ArrayList<>(states))if(!state.passive&&state.mob.isValid()){
            if(state.anchors.isEmpty()&&now>=state.exposedUntil){anchors(state);if(!state.anchors.isEmpty()){state.transition="reform";state.transitionUntil=now+20;}}
            if(state.anchors.isEmpty()&&now%8==0)state.mob.getWorld().spawnParticle(Particle.END_ROD,state.mob.getLocation().add(0,4,0),8,1.5,2,1.5,.025);
            for(Anchor a:new ArrayList<>(state.anchors)){
                if(!a.receiver.isValid()||!a.crystal.isValid()||!a.label.isValid()){remove(a);state.anchors.remove(a);if(state.anchors.isEmpty())state.exposedUntil=now+240;continue;}
                a.receiver.setVelocity(new org.bukkit.util.Vector());if(a.receiver.getLocation().distanceSquared(a.at)>.0001)a.receiver.teleport(a.at);
                if(now%4==0){
                    Location from=a.receiver.getLocation().add(0,.52,0),to=state.mob.getLocation().add(0,5,0);
                    sigils.fastRay("soul-chain:"+a.receiver.getUniqueId(),"judge_ray",from,to,.09);
                    from.getWorld().spawnParticle(Particle.SOUL,from,3,.5,.5,.5,.01);
                    for(double x:new double[]{-.52,.52})for(double y:new double[]{-.52,.52})for(double z:new double[]{-.52,.52})from.getWorld().spawnParticle(Particle.END_ROD,from.clone().add(x,y,z),1,0,0,0,0,null,true);
                    if(now%12==0){var step=to.toVector().subtract(from.toVector());int count=(int)step.length();if(count>0)for(int i=0;i<=count;i++)from.getWorld().spawnParticle(Particle.SOUL,from.clone().add(step.clone().multiply((double)i/count)),1,0,0,0,0,null,true);}
                }
            }
        }
        for(var it=seals.values().iterator();it.hasNext();){Seal seal=it.next();
            if(!active(seal)){release(seal);it.remove();continue;}
            if(seal.player.getHealth()>seal.health)seal.player.setHealth(seal.health);else seal.health=seal.player.getHealth();
            if(seal.player.getAbsorptionAmount()>seal.absorption)seal.player.setAbsorptionAmount(seal.absorption);else seal.absorption=seal.player.getAbsorptionAmount();
            if(now%20==0)seal.player.sendActionBar(net.kyori.adventure.text.Component.text("SOUL SEAL: no healing | "+((seal.until-now+19)/20)+"s"));
        }
    }
    private void remove(Anchor a){sigils.remove("soul-chain:"+a.receiver.getUniqueId());a.receiver.getWorld().spawnParticle(Particle.SOUL,a.receiver.getLocation().add(0,.5,0),24,.5,.5,.5,.03);a.receiver.remove();a.crystal.remove();a.label.remove();}
    void remove(State state){if(state==null)return;state.anchors.forEach(this::remove);state.anchors.clear();states.remove(state);seals.values().removeIf(seal->{if(seal.owner!=state)return false;release(seal);return true;});}
    @Override public void close(){for(State state:new ArrayList<>(states))remove(state);seals.values().forEach(this::release);seals.clear();}
}
