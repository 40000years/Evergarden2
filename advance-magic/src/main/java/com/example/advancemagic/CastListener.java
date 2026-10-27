package com.example.advancemagic;

import com.example.advancemagic.api.MagicCastEvent;
import com.example.advancemagic.spell.Spell;
import com.example.advancemagic.spell.BasicSpellProfile;
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
    private final Map<UUID,Integer> staffInputTick=new HashMap<>();
    public void repairing(Player player,boolean active){if(active)repairing.add(player.getUniqueId());else repairing.remove(player.getUniqueId());}
    public CastListener(AdvanceMagicPlugin plugin){this.plugin=plugin;}
    public void actionbar(Player p,String message) {
        var a=plugin.mana().account(p);
        String curStr=(a.manaExact()==(long)a.manaExact())?String.format(Locale.ROOT,"%d",(long)a.manaExact()):String.format(Locale.ROOT,"%.1f",a.manaExact());
        String maxStr=(a.maxMana()==(long)a.maxMana())?String.format(Locale.ROOT,"%d",(long)a.maxMana()):String.format(Locale.ROOT,"%.1f",a.maxMana());
        p.spigot().sendMessage(ChatMessageType.ACTION_BAR,TextComponent.fromLegacyText(ChatColor.AQUA+"Mana "+curStr+"/"+maxStr+"  "+ChatColor.WHITE+message));
    }
    private boolean preview(Player p,ItemStack item){
        if(!plugin.staffs().isRetiredOrStaff(item))return false;
        plugin.staffs().migrate(item);
        long now=System.currentTimeMillis();
        if(now-lastInput.getOrDefault(p.getUniqueId(),0L)>1500){
            lastInput.put(p.getUniqueId(),now);
            var selected=plugin.staffSpells().selected(item);
            actionbar(p,selected==null?"คทายังไม่มีเวท · /spell imprint":com.example.advancemagic.elemental.SpellLevels.label(selected,plugin.staffSpells().level(item,selected))+" · /spell");
        }
        return true;
    }
    private boolean staffInput(Player p){int tick=Bukkit.getCurrentTick();Integer last=staffInputTick.get(p.getUniqueId());if(last!=null&&tick-last<4)return false;staffInputTick.put(p.getUniqueId(),tick);return !repairing.contains(p.getUniqueId());}
    @EventHandler(priority=EventPriority.HIGHEST)
    public void interact(PlayerInteractEvent e){
        if(e.getAction()!=Action.RIGHT_CLICK_AIR&&e.getAction()!=Action.RIGHT_CLICK_BLOCK)return;
        ItemStack item=e.getHand()==EquipmentSlot.OFF_HAND?e.getPlayer().getInventory().getItemInOffHand():e.getPlayer().getInventory().getItemInMainHand();
        if(!plugin.staffs().isRetiredOrStaff(item))return;
        boolean denied=e.useItemInHand()==Event.Result.DENY;
        e.setCancelled(true);
        if(denied||e.getHand()!=EquipmentSlot.HAND||!staffInput(e.getPlayer()))return;
        plugin.staffs().migrate(item);plugin.staffSpells().migrate(item);
        if(e.getPlayer().isSneaking())plugin.staffSpells().cycle(e.getPlayer());else plugin.elemental().cast(e.getPlayer());
    }
    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true)
    public void entity(PlayerInteractEntityEvent e){
        if(plugin.flyingStaff().isDisplay(e.getRightClicked()))return;
        ItemStack item=e.getHand()==EquipmentSlot.OFF_HAND?e.getPlayer().getInventory().getItemInOffHand():e.getPlayer().getInventory().getItemInMainHand();
        if(plugin.staffs().isRetiredOrStaff(item)){
            e.setCancelled(true);
            if(e.getHand()==EquipmentSlot.HAND&&staffInput(e.getPlayer())){plugin.staffs().migrate(item);plugin.staffSpells().migrate(item);if(e.getPlayer().isSneaking())plugin.staffSpells().cycle(e.getPlayer());else plugin.elemental().cast(e.getPlayer());}
        }
    }
    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true)
    public void onAttackEntity(org.bukkit.event.entity.EntityDamageByEntityEvent e){
        if(plugin.context().isMagicDamage()||!(e.getDamager() instanceof Player p))return;
        var cause=e.getCause();
        if(cause!=org.bukkit.event.entity.EntityDamageEvent.DamageCause.ENTITY_ATTACK
                &&cause!=org.bukkit.event.entity.EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK)return;
        if(preview(p,p.getInventory().getItemInMainHand()))e.setCancelled(true);
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
    public boolean canUseElemental(Player player){return canCast(player)&&!repairing.contains(player.getUniqueId());}
    public boolean cast(Player p,Spell spell,ItemStack wandItem) {
        return cast(p,spell,wandItem,false);
    }
    /** Compatibility entry point: player-owned staff models do not invoke retired spells. */
    public boolean castInput(Player p,Spell spell,ItemStack wandItem) {
        if(p!=null&&wandItem!=null)preview(p,wandItem);
        return false;
    }
    private boolean cast(Player p,Spell spell,ItemStack wandItem,boolean basic) {
        if(spell==null||p==null)return false;
        if(wandItem!=null&&plugin.staffs().isRetiredOrStaff(wandItem)){preview(p,wandItem);return false;}
        if(repairing.contains(p.getUniqueId()))return false;
        long now=System.currentTimeMillis();UUID id=p.getUniqueId();
        if(!canCast(p)){actionbar(p,"You cannot cast spells.");return false;}
        if(wandItem!=null&&plugin.wands().usesLeft(wandItem)<=0){actionbar(p,"Wand durability is depleted.");return false;}
        if(!p.isOnline()||p.isDead()||p.getGameMode()==GameMode.SPECTATOR)return false;
        if(casting.contains(id)||now-lastInput.getOrDefault(id,0L)<150)return false;
        lastInput.put(id,now);casting.add(id);
        try {
            MagicCastEvent event=new MagicCastEvent(p,spell,basic);Bukkit.getPluginManager().callEvent(event);
            if(event.isCancelled()){actionbar(p,"Magic is blocked here.");return false;}

            var account=plugin.mana().account(p);
            BasicSpellProfile profile=plugin.spells().basic().profile(spell);
            int cost=basic?profile.mana():spell.mana;
            String cooldownKey=basic?BasicSpellProfile.COOLDOWN_KEY:spell.id();
            long remaining=account.remaining(cooldownKey,now);
            if(remaining>0){actionbar(p,spell.title+": "+String.format(Locale.ROOT,"%.1fs",remaining/1000.0));return false;}
            if(!plugin.effects().hasCapacity()||(basic&&!plugin.spells().basic().hasCapacity(p))){actionbar(p,"Too many active spells. Try again shortly.");return false;}

            double manaBefore=account.manaExact(),bloodCost=0;
            if(account.manaExact()<cost) {
                if(event.isBloodCast()) {
                    double missing = cost - account.manaExact();
                    double hpCost = Math.max(1.0, missing * 0.1);
                    if(p.getHealth() > hpCost) {
                        p.setHealth(p.getHealth() - hpCost);
                        bloodCost=hpCost;
                        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_PLAYER_HURT, 0.8f, 1.4f);
                        p.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, p.getLocation().add(0, 1, 0), 4, 0.2, 0.2, 0.2, 0.05);
                        account.setMana(cost);
                    } else {
                        actionbar(p, "Need " + cost + " mana (insufficient HP for Blood Cast).");
                        return false;
                    }
                } else {
                    actionbar(p,"Need "+cost+" mana.");return false;
                }
            }
            double effectiveCd=basic?profile.cooldown():wandItem!=null?plugin.wands().getEffectiveCooldown(wandItem,spell):spell.cooldown;
            effectiveCd=Math.max(basic?.75:.1, effectiveCd * event.getCooldownMultiplier());
            if(!account.reserve(cooldownKey,cost,effectiveCd,now)){
                if(bloodCost>0){account.setMana(manaBefore);p.setHealth(Math.min(p.getMaxHealth(),p.getHealth()+bloodCost));}
                return false;
            }
            boolean success=false;
            plugin.context().setCastVelocityMultiplier(p.getUniqueId(), event.getVelocityMultiplier());
            plugin.context().setCastDamageMultiplier(p.getUniqueId(),wandItem==null?1.0:plugin.wands().damageMultiplier(wandItem));
            try { success=basic?plugin.spells().basic().cast(p,spell,profile):plugin.spells().cast(p,spell); }
            catch(RuntimeException ex){plugin.getLogger().log(java.util.logging.Level.SEVERE,"Cast failed: "+spell,ex);}
            finally { plugin.context().clearCastVelocityMultiplier(p.getUniqueId());plugin.context().clearCastDamageMultiplier(p.getUniqueId()); }
            if(!success){
                account.refund(cooldownKey,cost);
                if(bloodCost>0){account.setMana(manaBefore);p.setHealth(Math.min(p.getMaxHealth(),p.getHealth()+bloodCost));}
                actionbar(p,"No valid target or safe destination.");
            }
            else {
                Bukkit.getPluginManager().callEvent(new com.example.advancemagic.api.MagicCastSuccessEvent(p, spell,basic));
                if(!basic&&wandItem!=null)plugin.wands().consumeUse(wandItem);
                int casts=!basic&&wandItem!=null?plugin.wands().recordCast(wandItem,spell):0;
                String cdStr=String.format(Locale.ROOT,"%.1f",effectiveCd);
                actionbar(p,spell.title+(basic?" · Basic":" · Main")+" | CD "+cdStr+"s"+(casts>0?" ("+casts+" casts)":""));

                int extra = event.getExtraCasts();
                if(!basic&&extra > 0 && p.isOnline()) {
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
    public void quit(Player p){lastInput.remove(p.getUniqueId());staffInputTick.remove(p.getUniqueId());casting.remove(p.getUniqueId());repairing.remove(p.getUniqueId());}
}
