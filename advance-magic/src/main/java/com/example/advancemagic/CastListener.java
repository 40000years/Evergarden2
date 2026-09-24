package com.example.advancemagic;

import com.example.advancemagic.api.MagicCastEvent;
import com.example.advancemagic.spell.Spell;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import java.util.*;

public final class CastListener implements Listener {
    private final AdvanceMagicPlugin plugin;
    private final Map<UUID,Long> lastInput=new HashMap<>();
    private final Set<UUID> casting=new HashSet<>();
    private final Set<UUID> repairing=new HashSet<>();
    public void repairing(Player player,boolean active){if(active)repairing.add(player.getUniqueId());else repairing.remove(player.getUniqueId());}
    public CastListener(AdvanceMagicPlugin plugin){this.plugin=plugin;}
    public void actionbar(Player p,String message) {
        var a=plugin.mana().account(p);
        String curStr=(a.manaExact()==(long)a.manaExact())?String.format(Locale.ROOT,"%d",(long)a.manaExact()):String.format(Locale.ROOT,"%.1f",a.manaExact());
        String maxStr=(a.maxMana()==(long)a.maxMana())?String.format(Locale.ROOT,"%d",(long)a.maxMana()):String.format(Locale.ROOT,"%.1f",a.maxMana());
        p.spigot().sendMessage(ChatMessageType.ACTION_BAR,TextComponent.fromLegacyText(ChatColor.AQUA+"Mana "+curStr+"/"+maxStr+"  "+ChatColor.WHITE+message));
    }
    private ItemStack heldItem(Player p,EquipmentSlot hand) {
        if(hand==null)return null;
        // A main-hand wand takes priority, avoiding duplicate casts from the offhand event.
        if(hand==EquipmentSlot.OFF_HAND&&plugin.wands().spell(p.getInventory().getItemInMainHand())!=null)return null;
        return hand==EquipmentSlot.HAND?p.getInventory().getItemInMainHand():p.getInventory().getItemInOffHand();
    }
    private Spell held(Player p,EquipmentSlot hand) {
        return plugin.wands().spell(heldItem(p,hand));
    }
    @EventHandler(priority=EventPriority.HIGH) public void interact(PlayerInteractEvent e) {
        Action action = e.getAction();
        boolean validAction = action == Action.RIGHT_CLICK_AIR 
            || action == Action.RIGHT_CLICK_BLOCK 
            || action == Action.LEFT_CLICK_AIR
            || (e.getPlayer().isSneaking() && action == Action.LEFT_CLICK_BLOCK);
        if(!validAction) return;

        if(e.useItemInHand() == Event.Result.DENY) return;
        ItemStack wandItem = heldItem(e.getPlayer(), e.getHand());
        Spell spell = plugin.wands().spell(wandItem);
        if(spell == null) return;
        e.setCancelled(true);
        cast(e.getPlayer(), spell, wandItem);
    }

    @EventHandler(priority=EventPriority.HIGH)
    public void onAnimation(PlayerAnimationEvent e) {
        // Bedrock Mobile: Screen tap in air sends ARM_SWING packet
        if(e.getAnimationType() != PlayerAnimationType.ARM_SWING) return;
        Player p = e.getPlayer();
        ItemStack wandItem = heldItem(p, EquipmentSlot.HAND);
        if(plugin.wands().spell(wandItem) == null) {
            wandItem = heldItem(p, EquipmentSlot.OFF_HAND);
        }
        Spell spell = plugin.wands().spell(wandItem);
        if(spell == null) return;
        cast(p, spell, wandItem);
    }

    @EventHandler(priority=EventPriority.HIGH, ignoreCancelled=true)
    public void entity(PlayerInteractEntityEvent e) {
        ItemStack wandItem = heldItem(e.getPlayer(), e.getHand());
        Spell spell = plugin.wands().spell(wandItem);
        if(spell == null) return;
        e.setCancelled(true);
        cast(e.getPlayer(), spell, wandItem);
    }

    @EventHandler(priority=EventPriority.HIGH, ignoreCancelled=true)
    public void onAttackEntity(org.bukkit.event.entity.EntityDamageByEntityEvent e) {
        if(plugin.context().isMagicDamage()) return;
        if(e.getCause() != org.bukkit.event.entity.EntityDamageEvent.DamageCause.ENTITY_ATTACK) return;
        // Bedrock Mobile: Tapping an enemy monster directly with the wand in hand
        if(!(e.getDamager() instanceof Player p)) return;
        ItemStack wandItem = heldItem(p, EquipmentSlot.HAND);
        if(plugin.wands().spell(wandItem) == null) {
            wandItem = heldItem(p, EquipmentSlot.OFF_HAND);
        }
        Spell spell = plugin.wands().spell(wandItem);
        if(spell == null) return;
        e.setCancelled(true);
        cast(p, spell, wandItem);
    }

    @EventHandler(priority=EventPriority.HIGH)
    public void onSwapHand(PlayerSwapHandItemsEvent e) {
        // Bedrock Mobile: Dedicated on-screen shortcut button (swap offhand) to cast wand
        Player p = e.getPlayer();
        ItemStack main = p.getInventory().getItemInMainHand();
        Spell mainSpell = plugin.wands().spell(main);
        if(mainSpell != null) {
            e.setCancelled(true);
            cast(p, mainSpell, main);
            return;
        }
        ItemStack off = p.getInventory().getItemInOffHand();
        Spell offSpell = plugin.wands().spell(off);
        if(offSpell != null) {
            e.setCancelled(true);
            cast(p, offSpell, off);
        }
    }
    public boolean cast(Player p,Spell spell) {
        ItemStack item=plugin.wands().spell(p.getInventory().getItemInMainHand())==spell?p.getInventory().getItemInMainHand()
            :plugin.wands().spell(p.getInventory().getItemInOffHand())==spell?p.getInventory().getItemInOffHand():null;
        return cast(p,spell,item);
    }
    public boolean canCast(Player player) {
        if(player==null) return true;
        if(player.isOp()) return true;
        return !player.isPermissionSet("advance-magic.cast") || player.hasPermission("advance-magic.cast");
    }
    public boolean cast(Player p,Spell spell,ItemStack wandItem) {
        if(repairing.contains(p.getUniqueId()))return false;
        long now=System.currentTimeMillis();UUID id=p.getUniqueId();
        if(!canCast(p)){actionbar(p,"You cannot cast spells.");return false;}
        if(wandItem!=null&&plugin.wands().usesLeft(wandItem)<=0){actionbar(p,"Wand durability is depleted.");return false;}
        if(!p.isOnline()||p.isDead()||p.getGameMode()==GameMode.SPECTATOR)return false;
        if(casting.contains(id)||now-lastInput.getOrDefault(id,0L)<150)return false;
        lastInput.put(id,now);casting.add(id);
        try {
            MagicCastEvent event=new MagicCastEvent(p,spell);Bukkit.getPluginManager().callEvent(event);
            if(event.isCancelled()){actionbar(p,"Magic is blocked here.");return false;}

            var account=plugin.mana().account(p);
            long remaining=account.remaining(spell.id(),now);
            if(remaining>0){actionbar(p,spell.title+": "+String.format(Locale.ROOT,"%.1fs",remaining/1000.0));return false;}

            if(account.mana()<spell.mana) {
                if(event.isBloodCast()) {
                    double missing = spell.mana - account.mana();
                    double hpCost = Math.max(1.0, missing * 0.1);
                    if(p.getHealth() > hpCost) {
                        p.setHealth(p.getHealth() - hpCost);
                        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_PLAYER_HURT, 0.8f, 1.4f);
                        p.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, p.getLocation().add(0, 1, 0), 4, 0.2, 0.2, 0.2, 0.05);
                        account.setMana(spell.mana);
                    } else {
                        actionbar(p, "Need " + spell.mana + " mana (insufficient HP for Blood Cast).");
                        return false;
                    }
                } else {
                    actionbar(p,"Need "+spell.mana+" mana.");return false;
                }
            }
            if(!plugin.effects().hasCapacity()){actionbar(p,"Too many active spells. Try again shortly.");return false;}

            double effectiveCd=wandItem!=null?plugin.wands().getEffectiveCooldown(wandItem,spell):spell.cooldown;
            effectiveCd=Math.max(0.1, effectiveCd * event.getCooldownMultiplier());
            if(!account.reserve(spell.id(),spell.mana,effectiveCd,now))return false;
            boolean success=false;
            plugin.context().setCastVelocityMultiplier(p.getUniqueId(), event.getVelocityMultiplier());
            plugin.context().setCastDamageMultiplier(p.getUniqueId(),wandItem==null?1.0:plugin.wands().damageMultiplier(wandItem));
            try { success=plugin.spells().cast(p,spell); }
            catch(RuntimeException ex){plugin.getLogger().log(java.util.logging.Level.SEVERE,"Cast failed: "+spell,ex);}
            finally { plugin.context().clearCastVelocityMultiplier(p.getUniqueId());plugin.context().clearCastDamageMultiplier(p.getUniqueId()); }
            if(!success){account.refund(spell.id(),spell.mana);actionbar(p,"No valid target or safe destination.");}
            else {
                Bukkit.getPluginManager().callEvent(new com.example.advancemagic.api.MagicCastSuccessEvent(p, spell));
                if(wandItem!=null)plugin.wands().consumeUse(wandItem);
                int casts=wandItem!=null?plugin.wands().recordCast(wandItem,spell):0;
                String cdStr=String.format(Locale.ROOT,"%.1f",effectiveCd);
                actionbar(p,spell.title+" | CD "+cdStr+"s"+(casts>0?" ("+casts+" casts)":""));

                int extra = event.getExtraCasts();
                if(extra > 0 && p.isOnline()) {
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if(p.isOnline() && !p.isDead()) {
                            try {
                                plugin.context().setCastVelocityMultiplier(p.getUniqueId(), event.getVelocityMultiplier());
                                plugin.context().setCastDamageMultiplier(p.getUniqueId(),wandItem==null?1.0:plugin.wands().damageMultiplier(wandItem));
                                try {
                                    plugin.spells().cast(p, spell);
                                } finally {
                                    plugin.context().clearCastVelocityMultiplier(p.getUniqueId());
                                    plugin.context().clearCastDamageMultiplier(p.getUniqueId());
                                }
                                p.getWorld().playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.8f, 1.6f);
                                p.getWorld().spawnParticle(Particle.WITCH, p.getLocation().add(0, 1, 0), 15, 0.3, 0.4, 0.3, 0.05);
                            } catch (Throwable ignored) {}
                        }
                    }, 5L);
                }
            }
            plugin.mana().save(p);return success;
        } finally {casting.remove(id);}
    }
    public void quit(Player p){lastInput.remove(p.getUniqueId());casting.remove(p.getUniqueId());repairing.remove(p.getUniqueId());}
}
