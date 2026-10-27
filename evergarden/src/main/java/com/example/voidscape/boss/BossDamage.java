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

/** Armor wear for encounter impacts whose damage type intentionally bypasses armor. */
final class BossDamage {
    private BossDamage(){}
    private static final ThreadLocal<Impact> CURRENT=new ThreadLocal<>();
    private static final class Impact {
        final Player player;final double amount;final DamageSource source;final boolean armor;
        boolean lethal,worn;
        boolean trueHealth;double armorAmount;int wearMultiplier=1;
        Impact(Player player,double amount,DamageSource source,boolean armor){this.player=player;this.amount=amount;this.source=source;this.armor=armor;}
    }
    static void register(JavaPlugin plugin){
        plugin.getServer().getPluginManager().registerEvents(new Listener(){
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
