package com.example.voidscape.enchant;

import com.example.voidscape.VoidscapePlugin;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.bukkit.*;
import org.bukkit.attribute.*;
import org.bukkit.craftbukkit.damage.CraftDamageSource;
import org.bukkit.craftbukkit.entity.CraftLivingEntity;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Extend only the runtime mechanics that actually saturate; native effects remain native. */
public final class BossEnchantEffects implements Listener,AutoCloseable {
    private final BossEnchantService service;
    private final NamespacedKey depthKey,swiftKey;
    public BossEnchantEffects(VoidscapePlugin plugin,BossEnchantService service){
        this.service=service;depthKey=plugin.key("boss_depth_speed");swiftKey=plugin.key("boss_swift_speed");
        Bukkit.getScheduler().runTaskTimer(plugin,()->Bukkit.getOnlinePlayers().forEach(this::movement),1,5);
    }
    @SuppressWarnings("deprecation")
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void protection(EntityDamageEvent event){
        if(!(event.getEntity() instanceof CraftLivingEntity entity)||!(event.getDamageSource() instanceof CraftDamageSource source)
            ||!event.isApplicable(EntityDamageEvent.DamageModifier.MAGIC))return;
        var damage=source.getHandle();if(damage.is(DamageTypeTags.BYPASSES_ENCHANTMENTS))return;
        var gear=entity.getEquipment();if(gear==null)return;double ownedExtra=0;
        for(var item:gear.getArmorContents()){
            int p=service.level(item,Enchantment.PROTECTION);if(p>4)ownedExtra+=p-4;
            if(damage.is(DamageTypeTags.IS_FALL)){
                int f=service.level(item,Enchantment.FEATHER_FALLING);if(f>4)ownedExtra+=3*(f-4);
            }
        }
        if(ownedExtra<=0)return;
        var handle=entity.getHandle();
        double total=EnchantmentHelper.getDamageProtection((ServerLevel)handle.level(),handle,damage);
        double extra=Math.max(0,total-20)-Math.max(0,total-ownedExtra-20);if(extra<=0)return;
        double remaining=event.getFinalDamage();
        if(event.isApplicable(EntityDamageEvent.DamageModifier.ABSORPTION))remaining-=event.getDamage(EntityDamageEvent.DamageModifier.ABSORPTION);
        double factor=protectionFactor(extra);
        event.setDamage(EntityDamageEvent.DamageModifier.MAGIC,event.getDamage(EntityDamageEvent.DamageModifier.MAGIC)-Math.max(0,remaining)*(1-factor));
    }
    public double protectionFactor(double extra){return Math.pow(1-service.protectionRate(),Math.max(0,extra));}
    public double depthMultiplier(int level){return level<=3?1:1+(level-3)*service.depthSpeed();}
    public double swiftMultiplier(int level){double desired=.3+.15*level;return service.swiftOverflow()&&level>3?Math.max(1,desired/Math.min(1,desired)):1;}
    public void movement(Player player){
        int depth=service.level(player.getInventory().getBoots(),Enchantment.DEPTH_STRIDER);
        int swift=service.level(player.getInventory().getLeggings(),Enchantment.SWIFT_SNEAK);
        boolean active=!player.isFlying()&&!player.isGliding()&&!player.isInsideVehicle();
        modifier(player,depthKey,active&&player.isInWater()?depthMultiplier(depth)-1:0);
        modifier(player,swiftKey,active&&player.isSneaking()?swiftMultiplier(swift)-1:0);
    }
    private void modifier(Player player,NamespacedKey key,double amount){
        var speed=player.getAttribute(Attribute.MOVEMENT_SPEED);if(speed==null)return;
        var old=speed.getModifier(key);if(old!=null&&Math.abs(old.getAmount()-amount)<1e-9)return;
        if(old!=null)speed.removeModifier(old);
        if(amount>1e-9)speed.addTransientModifier(new AttributeModifier(key,amount,AttributeModifier.Operation.MULTIPLY_SCALAR_1));
    }
    @EventHandler public void quit(PlayerQuitEvent event){clear(event.getPlayer());}
    public void clear(Player player){modifier(player,depthKey,0);modifier(player,swiftKey,0);}
    @Override public void close(){Bukkit.getOnlinePlayers().forEach(this::clear);}
}
