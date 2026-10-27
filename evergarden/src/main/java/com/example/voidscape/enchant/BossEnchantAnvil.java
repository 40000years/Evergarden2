package com.example.voidscape.enchant;

import org.bukkit.craftbukkit.enchantments.CraftEnchantment;
import org.bukkit.craftbukkit.inventory.view.CraftAnvilView;
import org.bukkit.event.*;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import java.util.*;

/** Keep native compatibility, repair, XP payment and item consumption, with a scoped level bypass. */
public final class BossEnchantAnvil implements Listener {
    private final BossEnchantService service;
    private final Set<Object> rebuilding=Collections.newSetFromMap(new IdentityHashMap<>());
    private final Map<Object,Integer> limits=new IdentityHashMap<>();
    public BossEnchantAnvil(BossEnchantService service){this.service=service;}
    @EventHandler(priority=EventPriority.HIGHEST)
    public void prepare(PrepareAnvilEvent event){
        if(!service.anvilEnabled())return;
        var left=event.getInventory().getFirstItem();var right=event.getInventory().getSecondItem();
        var a=service.authorized(left);var b=service.authorized(right);
        if(a.isEmpty()&&b.isEmpty())return;
        if(!(event.getView() instanceof CraftAnvilView view))return;
        var handle=(net.minecraft.world.inventory.AnvilMenu)view.getHandle();
        if(!rebuilding.contains(handle)){
            // Recompute once so Minecraft charges for the actual high levels. No global cap changes.
            boolean previous=view.bypassesEnchantmentLevelRestriction();int limit=view.getMaximumRepairCost();rebuilding.add(handle);limits.put(handle,limit);
            try{
                // Compute the preserved level before evaluating the native XP ceiling.
                view.setMaximumRepairCost(Integer.MAX_VALUE);
                view.bypassEnchantmentLevelRestriction(true);handle.createResult();
                // 26.2 dispatches prepare events from the combiner update, after createResult.
                org.bukkit.craftbukkit.event.CraftEventFactory.callPrepareResultEvent(handle,2);
                var output=event.getInventory().getResult();event.setResult(output==null?null:output.clone());
            }finally{view.bypassEnchantmentLevelRestriction(previous);view.setMaximumRepairCost(limit);rebuilding.remove(handle);limits.remove(handle);}
            return;
        }
        ItemStack output=event.getResult();if(output==null||output.getType().isAir())return;
        output=output.clone();var meta=output.getItemMeta();var caps=new HashMap<>(a);b.forEach((e,l)->caps.merge(e,l,Math::max));
        var incoming=BossEnchantService.enchants(right);int discount=0;
        for(var entry:BossEnchantService.enchants(output).entrySet()){
            var enchant=entry.getKey();int old=entry.getValue();
            // Equal high levels do not create a new level. Ordinary enchants keep their vanilla ceiling.
            int next=Math.min(old,Math.max(enchant.getMaxLevel(),caps.getOrDefault(enchant,0)));
            if(old==next)continue;
            if(meta instanceof EnchantmentStorageMeta book)book.addStoredEnchant(enchant,next,true);else meta.addEnchant(enchant,next,true);
            if(incoming.containsKey(enchant)){
                int multiplier=CraftEnchantment.bukkitToMinecraftHolder(enchant).value().getAnvilCost();
                if(right.getType()==org.bukkit.Material.ENCHANTED_BOOK)multiplier=Math.max(1,multiplier/2);
                discount+=multiplier*(old-next);
            }
        }
        // A legacy Limit Break already supplies a virtual bonus. Never layer native loot on it.
        for(var type:LimitBreakType.values()){
            var key=new org.bukkit.NamespacedKey("voidscape","lb_"+type.name().toLowerCase(Locale.ROOT));
            Integer virtual=meta.getPersistentDataContainer().get(key,org.bukkit.persistence.PersistentDataType.INTEGER);
            int nativeLevel=meta.getEnchantLevel(type.enchantment());
            if(virtual==null||nativeLevel<=type.enchantment().getMaxLevel())continue;
            if(virtual>=nativeLevel){meta.addEnchant(type.enchantment(),type.enchantment().getMaxLevel(),true);caps.remove(type.enchantment());}
            else{
                meta.getPersistentDataContainer().remove(key);
                if(type==LimitBreakType.EFFICIENCY)meta.setTool(null);
                if(meta.hasLore()){
                    var lore=new ArrayList<>(meta.lore());lore.removeIf(line->net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(line).contains(type.thaiTitle()));meta.lore(lore);
                }
            }
        }
        var granted=new HashMap<org.bukkit.enchantments.Enchantment,Integer>();
        var finalLevels=meta instanceof EnchantmentStorageMeta book?book.getStoredEnchants():meta.getEnchants();
        caps.forEach((e,l)->{int actual=finalLevels.getOrDefault(e,0);if(actual>e.getMaxLevel())granted.put(e,Math.min(actual,l));});
        service.write(meta,granted);output.setItemMeta(meta);event.setResult(output);
        if(discount>0)view.setRepairCost(Math.max(1,view.getRepairCost()-discount));
        if(view.getRepairCost()>=limits.getOrDefault(handle,40)&&view.getPlayer().getGameMode()!=org.bukkit.GameMode.CREATIVE)event.setResult(null);
    }
}
