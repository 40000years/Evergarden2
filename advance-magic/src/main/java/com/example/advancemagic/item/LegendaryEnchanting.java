package com.example.advancemagic.item;

import com.example.advancemagic.item.LegendaryWeaponService.Weapon;
import org.bukkit.*;
import org.bukkit.enchantments.*;
import org.bukkit.event.*;
import org.bukkit.event.enchantment.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.*;
import java.util.*;

/** Longinus stays a nonthrowable item, but draws its enchantments from vanilla tridents. */
public final class LegendaryEnchanting implements Listener {
    private final LegendaryWeaponService weapons;
    public LegendaryEnchanting(LegendaryWeaponService weapons){this.weapons=weapons;}
    public Map<Enchantment,Integer> roll(int seed,int button,int cost){
        return new ItemStack(Material.TRIDENT).enchantWithLevels(cost,false,new Random((long)seed+button)).getEnchantments();
    }
    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true)
    public void offers(PrepareItemEnchantEvent e){
        if(weapons.identify(e.getItem())!=Weapon.LONGINUS)return;
        var offers=e.getOffers();
        for(int i=0;i<offers.length;i++)if(offers[i]!=null){
            var rolled=roll(e.getView().getEnchantmentSeed(),i,offers[i].getCost());
            if(rolled.isEmpty()){offers[i]=null;continue;}
            var first=rolled.entrySet().iterator().next();offers[i]=new EnchantmentOffer(first.getKey(),first.getValue(),offers[i].getCost());
        }
    }
    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true)
    public void enchant(EnchantItemEvent e){
        if(weapons.identify(e.getItem())!=Weapon.LONGINUS)return;
        var view=(org.bukkit.inventory.view.EnchantmentView)e.getView();
        var rolled=roll(view.getEnchantmentSeed(),e.whichButton(),e.getExpLevelCost());
        if(rolled.isEmpty()){e.setCancelled(true);return;}
        e.getEnchantsToAdd().clear();e.getEnchantsToAdd().putAll(rolled);
    }
    public ItemStack combine(ItemStack first,ItemStack second){
        if(weapons.identify(first)!=Weapon.LONGINUS||second==null)return null;
        Map<Enchantment,Integer> incoming;
        if(second.getItemMeta() instanceof EnchantmentStorageMeta book)incoming=book.getStoredEnchants();
        else if(weapons.identify(second)==Weapon.LONGINUS)incoming=second.getEnchantments();
        else return null;
        var out=first.clone();out.setAmount(1);boolean changed=false;var trident=new ItemStack(Material.TRIDENT);
        for(var row:incoming.entrySet()){
            var enchant=row.getKey();if(!enchant.canEnchantItem(trident))continue;
            if(out.getEnchantments().keySet().stream().anyMatch(old->old!=enchant&&old.conflictsWith(enchant)))continue;
            int current=out.getEnchantmentLevel(enchant);int level=Math.min(enchant.getMaxLevel(),current==row.getValue()?current+1:Math.max(current,row.getValue()));
            if(level<=current)continue;out.addUnsafeEnchantment(enchant,level);changed=true;
        }
        return changed?out:null;
    }
    @EventHandler(priority=EventPriority.HIGH)
    public void anvil(PrepareAnvilEvent e){
        var first=e.getInventory().getFirstItem();if(weapons.identify(first)!=Weapon.LONGINUS)return;
        var second=e.getInventory().getSecondItem();if(second==null||second.getType().isAir())return; // normal rename path
        var out=combine(first,second);if(out==null){e.setResult(null);return;}
        int prior=first.getItemMeta() instanceof Repairable r?r.getRepairCost():0;
        int other=second.getItemMeta() instanceof Repairable r?r.getRepairCost():0;
        int cost=prior+other;
        for(var row:out.getEnchantments().entrySet())if(row.getValue()>first.getEnchantmentLevel(row.getKey()))cost+=Math.max(1,row.getKey().getAnvilCost()/(second.getType()==Material.ENCHANTED_BOOK?2:1))*row.getValue();
        var meta=out.getItemMeta();String rename=e.getView().getRenameText();
        if(rename!=null&&!rename.isBlank()&&!rename.equals(first.getItemMeta().hasDisplayName()?ChatColor.stripColor(first.getItemMeta().getDisplayName()):first.getType().name())){meta.setDisplayName(rename);cost++;}
        if(meta instanceof Repairable r)r.setRepairCost(Math.max(prior,other)*2+1);
        out.setItemMeta(meta);e.setResult(out);e.getView().setRepairCost(Math.max(1,cost));e.getView().setRepairItemCountCost(1);
    }
}
