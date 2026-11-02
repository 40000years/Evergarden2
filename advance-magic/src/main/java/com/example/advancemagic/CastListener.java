package com.example.advancemagic;

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
    public boolean canCast(Player player) {
        if(player==null) return true;
        if(player.isOp()) return true;
        return !player.isPermissionSet("advance-magic.cast") || player.hasPermission("advance-magic.cast");
    }
    public boolean canUseElemental(Player player){return canCast(player)&&!repairing.contains(player.getUniqueId());}
    /** Compatibility entry points: the retired wand spells no longer cast; a held staff shows its loadout. */
    public boolean cast(Player p,Spell spell) {
        return castInput(p,spell,p==null?null:p.getInventory().getItemInMainHand());
    }
    public boolean cast(Player p,Spell spell,ItemStack wandItem) {
        return castInput(p,spell,wandItem);
    }
    public boolean castInput(Player p,Spell spell,ItemStack wandItem) {
        if(p!=null&&wandItem!=null)preview(p,wandItem);
        return false;
    }
    public void quit(Player p){lastInput.remove(p.getUniqueId());staffInputTick.remove(p.getUniqueId());repairing.remove(p.getUniqueId());}
}
