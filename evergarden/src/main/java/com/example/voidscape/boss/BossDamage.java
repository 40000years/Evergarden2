package com.example.voidscape.boss;

import org.bukkit.GameMode;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.Player;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.craftbukkit.damage.CraftDamageSource;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import net.kyori.adventure.text.Component;
import java.util.*;

/** Armor wear for encounter impacts whose damage type intentionally bypasses armor. */
public final class BossDamage {
    private BossDamage(){}
    private record Attribution(UUID player,String name) {}
    private record Lingering(UUID world,String name,long until) {}
    private static final ThreadLocal<Attribution> SCOPED=new ThreadLocal<>();
    private static final ThreadLocal<Attribution> EFFECT=new ThreadLocal<>();
    private static final Map<UUID,Lingering> WITHER=new HashMap<>(),FIRE=new HashMap<>();
    private static NamespacedKey bossNameKey;

    /** Tag an actual boss or its attack proxy; ordinary mobs are never inferred from proximity. */
    public static void tag(Entity entity,String name){
        if(entity==null||name==null||name.isBlank())return;
        if(bossNameKey!=null)entity.getPersistentDataContainer().set(bossNameKey,PersistentDataType.STRING,name);
        // Keep a real entity name as a fallback for death-message plugins that read the
        // Bukkit killer's display name instead of our persistent boss attribution tag.
        // These proxy names stay hidden above the entity; explicitly authored names
        // (for example summoned minions) are left intact.
        if(entity.customName()==null){
            entity.customName(BossNames.styled(name));
            entity.setCustomNameVisible(false);
        }
    }
    private static String name(Entity entity){
        if(entity==null||bossNameKey==null)return null;
        String result=entity.getPersistentDataContainer().get(bossNameKey,PersistentDataType.STRING);
        if(result==null&&entity instanceof Projectile shot&&shot.getShooter() instanceof Entity shooter)
            result=shooter.getPersistentDataContainer().get(bossNameKey,PersistentDataType.STRING);
        return result;
    }
    /** Attach a boss name to exactly this synchronous damage/death operation. */
    public static void scoped(Player player,String name,Runnable action){
        Attribution previous=SCOPED.get();SCOPED.set(new Attribution(player.getUniqueId(),name));
        try{action.run();}finally{if(previous==null)SCOPED.remove();else SCOPED.set(previous);}
    }
    /** Apply a status normally while recording the source if Paper accepts a Wither effect. */
    public static boolean effect(Player player,PotionEffect effect,String name){
        Attribution previous=EFFECT.get();EFFECT.set(new Attribution(player.getUniqueId(),name));
        try{return player.addPotionEffect(effect);}finally{if(previous==null)EFFECT.remove();else EFFECT.set(previous);}
    }
    /** Record boss fire only when it did not start as unrelated existing fire. */
    public static void ignite(Player player,int ticks,String name){
        int before=player.getFireTicks();Lingering old=FIRE.get(player.getUniqueId());
        boolean ours=old!=null&&old.world.equals(player.getWorld().getUID())&&old.until>=Bukkit.getCurrentTick()&&old.name.equals(name);
        player.setFireTicks(Math.max(before,ticks));
        if(before<=0||ours)FIRE.put(player.getUniqueId(),new Lingering(player.getWorld().getUID(),name,Bukkit.getCurrentTick()+Math.max(ticks,player.getFireTicks())+2L));
        else FIRE.remove(player.getUniqueId());
    }
    private static final ThreadLocal<Impact> CURRENT=new ThreadLocal<>();
    private static final class Impact {
        final Player player;final double amount;final DamageSource source;final boolean armor;
        boolean lethal,worn;
        boolean trueHealth;double armorAmount;int wearMultiplier=1;
        Impact(Player player,double amount,DamageSource source,boolean armor){this.player=player;this.amount=amount;this.source=source;this.armor=armor;}
    }
    static void register(JavaPlugin plugin){
        bossNameKey=new NamespacedKey(plugin,"boss_death_name");WITHER.clear();FIRE.clear();
        plugin.getServer().getPluginManager().registerEvents(new Listener(){
            @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
            public void potion(EntityPotionEffectEvent event){
                if(!(event.getEntity() instanceof Player player)||event.getModifiedType()!=PotionEffectType.WITHER)return;
                UUID id=player.getUniqueId();WITHER.remove(id);
                PotionEffect effect=event.getNewEffect();if(effect==null||(event.getOldEffect()!=null&&!event.isOverride()))return;
                Attribution requested=EFFECT.get();String source=requested!=null&&requested.player.equals(id)?requested.name:name(event.getSource());
                if(source==null||source.isBlank())return;
                long duration=effect.getDuration()==PotionEffect.INFINITE_DURATION?Integer.MAX_VALUE:Math.max(0,effect.getDuration());
                WITHER.put(id,new Lingering(player.getWorld().getUID(),source,Bukkit.getCurrentTick()+duration+2L));
            }
            @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
            public void combust(EntityCombustEvent event){if(event.getEntity() instanceof Player p)FIRE.remove(p.getUniqueId());}
            @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
            public void deathName(PlayerDeathEvent event){
                Player player=event.getEntity();UUID id=player.getUniqueId();Attribution scoped=SCOPED.get();
                String boss=scoped!=null&&scoped.player.equals(id)?scoped.name:null;
                if(boss==null){boss=name(event.getDamageSource().getCausingEntity());if(boss==null)boss=name(event.getDamageSource().getDirectEntity());}
                if(boss==null){
                    var type=event.getDamageSource().getDamageType();
                    Lingering effect=type==org.bukkit.damage.DamageType.WITHER?WITHER.get(id):type==org.bukkit.damage.DamageType.ON_FIRE?FIRE.get(id):null;
                    if(effect!=null&&effect.until>=Bukkit.getCurrentTick()&&effect.world.equals(player.getWorld().getUID())
                            &&(type!=org.bukkit.damage.DamageType.WITHER||player.hasPotionEffect(PotionEffectType.WITHER))
                            &&(type!=org.bukkit.damage.DamageType.ON_FIRE||player.getFireTicks()>0))boss=effect.name;
                }
                if(boss!=null&&!boss.isBlank()){
                    Component message=Component.text(player.getName()+" ถูกสังหารโดย ",net.kyori.adventure.text.format.NamedTextColor.GRAY)
                        .append(BossNames.styled(boss));
                    if(event.deathMessage()!=null)event.deathMessage(message);
                    event.deathScreenMessageOverride(message);
                }
            }
            @EventHandler(priority=EventPriority.MONITOR) public void deathCleanup(PlayerDeathEvent e){WITHER.remove(e.getEntity().getUniqueId());FIRE.remove(e.getEntity().getUniqueId());}
            @EventHandler public void quit(PlayerQuitEvent e){WITHER.remove(e.getPlayer().getUniqueId());FIRE.remove(e.getPlayer().getUniqueId());}
            @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
            public void soulFixed(org.bukkit.event.entity.EntityDamageEvent event){
                Impact impact=CURRENT.get();
                if(impact!=null&&event.getEntity()==impact.player&&impact.source.getDamageType().equals(org.bukkit.damage.DamageType.GENERIC_KILL)&&!impact.trueHealth)event.setDamage(5);
            }
            @SuppressWarnings("deprecation")
            @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
            public void heartDamage(org.bukkit.event.entity.EntityDamageEvent event){
                Impact impact=CURRENT.get();if(impact==null||!impact.trueHealth||event.getEntity()!=impact.player)return;
                event.setDamage(impact.amount);
                for(var modifier:org.bukkit.event.entity.EntityDamageEvent.DamageModifier.values())
                    if(modifier!=org.bukkit.event.entity.EntityDamageEvent.DamageModifier.BASE&&event.isApplicable(modifier))event.setDamage(modifier,0);
            }
            // generic_kill skips resurrection entirely, including its pre-drop
            // wear hook. Charge accepted lethal soul impacts before gear copies.
            @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
            public void soulLethal(org.bukkit.event.entity.EntityDamageEvent event){
                Impact impact=CURRENT.get();
                if(impact!=null&&event.getEntity()==impact.player&&impact.source.getDamageType().equals(org.bukkit.damage.DamageType.GENERIC_KILL)
                        &&event.getFinalDamage()>=impact.player.getHealth()){
                    impact.lethal=true;wear(impact);
                }
            }
            // Also fired cancelled when there is no Totem. The lethal hit has
            // already been accepted, but death drops have not copied armor yet.
            @EventHandler(priority=EventPriority.MONITOR)
            public void lethal(EntityResurrectEvent event){
                Impact impact=CURRENT.get();
                if(impact!=null&&event.getEntity()==impact.player){impact.lethal=true;wear(impact);}
            }
        },plugin);
    }
    static boolean hit(Player player,double amount,DamageSource source,boolean armorImpact){
        if(!Double.isFinite(amount)||amount<=0)return false;
        double before=player.getHealth()+player.getAbsorptionAmount();
        Impact impact=new Impact(player,amount,source,armorImpact),previous=CURRENT.get();
        CURRENT.set(impact);
        try{player.damage(amount,source);}finally{if(previous==null)CURRENT.remove();else CURRENT.set(previous);}
        boolean landed=impact.lethal||player.getHealth()+player.getAbsorptionAmount()<before;
        if(landed)wear(impact);
        return landed;
    }
    static boolean soul(Player player,org.bukkit.entity.Entity source){
        if(player.isInvulnerable()||player.getGameMode()!=GameMode.SURVIVAL&&player.getGameMode()!=GameMode.ADVENTURE)return false;
        // Native generic_kill bypasses armor, enchantments, Resistance and Totem rescue.
        return hit(player,5,DamageSource.builder(org.bukkit.damage.DamageType.GENERIC_KILL).withCausingEntity(source).withDirectEntity(source).build(),true);
    }
    static boolean styx(Player player,double amount,org.bukkit.entity.Entity source,boolean execution){
        if(player.isInvulnerable()||player.isDead()||player.getGameMode()!=GameMode.SURVIVAL&&player.getGameMode()!=GameMode.ADVENTURE)return false;
        var type=execution?org.bukkit.damage.DamageType.GENERIC_KILL:org.bukkit.damage.DamageType.SONIC_BOOM;
        double hitAmount=execution?player.getHealth()+1:amount;
        var damage=DamageSource.builder(type).withCausingEntity(source).withDirectEntity(source).build();
        Impact impact=new Impact(player,hitAmount,damage,true),previous=CURRENT.get();
        impact.trueHealth=true;impact.armorAmount=amount;impact.wearMultiplier=8;
        double before=player.getHealth();CURRENT.set(impact);
        try{player.damage(hitAmount,damage);}finally{if(previous==null)CURRENT.remove();else CURRENT.set(previous);}
        boolean landed=impact.lethal||player.getHealth()<before;if(landed)wear(impact);return landed;
    }
    private static void breakShield(Player player){
        var handle=((CraftPlayer)player).getHandle();
        if(!handle.isUsingItem()||handle.getUseItem().getItem()!=net.minecraft.world.item.Items.SHIELD)return;
        boolean off=handle.getUsedItemHand()==net.minecraft.world.InteractionHand.OFF_HAND;
        var item=off?player.getInventory().getItemInOffHand():player.getInventory().getItemInMainHand();
        var broken=item.clone();handle.stopUsingItem();
        if(off)player.getInventory().setItemInOffHand(null);else player.getInventory().setItemInMainHand(null);
        org.bukkit.Bukkit.getPluginManager().callEvent(new org.bukkit.event.player.PlayerItemBreakEvent(player,broken));
        player.getWorld().playSound(player.getLocation(),org.bukkit.Sound.ITEM_SHIELD_BREAK,1,1);
    }
    private static void wear(Impact impact){
        Player player=impact.player;
        if(impact.worn||!impact.armor||!(player.getGameMode()==GameMode.SURVIVAL||player.getGameMode()==GameMode.ADVENTURE))return;
        impact.worn=true;
        if(impact.trueHealth)breakShield(player);
        int wear=(int)Math.max(1,Math.min(Integer.MAX_VALUE,(int)Math.max(1,(impact.trueHealth?impact.armorAmount:impact.amount)/4)*(double)impact.wearMultiplier));
        var owner=((CraftPlayer)player).getHandle();
        var nativeSource=((CraftDamageSource)impact.source).getHandle();
        if(!nativeSource.is(net.minecraft.tags.DamageTypeTags.BYPASSES_ARMOR))return;
        for(var slot:new EquipmentSlot[]{EquipmentSlot.FEET,EquipmentSlot.LEGS,EquipmentSlot.CHEST,EquipmentSlot.HEAD}){
                var item=owner.getItemBySlot(slot);
                var equippable=item.get(DataComponents.EQUIPPABLE);
                if(equippable!=null&&equippable.damageOnHurt()&&item.isDamageableItem()&&item.canBeHurtBy(nativeSource))
                    // Native path owns Unbreaking, unbreakable items, item damage
                    // cancellation (including Eternity), break events and slot sync.
                    item.hurtAndBreak(wear,owner,slot);
        }
    }
}
