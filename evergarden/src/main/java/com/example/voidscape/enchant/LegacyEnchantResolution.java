package com.example.voidscape.enchant;

import com.example.voidscape.VoidscapePlugin;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Owner-controlled recovery of scrolls from equipment made before compatibility rules. */
public final class LegacyEnchantResolution {
    private LegacyEnchantResolution() {}

    public static boolean resolveHeld(VoidscapePlugin plugin, Player player, ItemStack expected, String keep) {
        return resolveSlot(plugin,player,player.getInventory().getHeldItemSlot(),expected,keep);
    }

    public static boolean resolveSlot(VoidscapePlugin plugin, Player player, int slot, ItemStack expected, String keep) {
        ItemStack held=player.getInventory().getItem(slot);
        if (held == null || !held.equals(expected) || held.getAmount() != 1) {
            plugin.message(player,"อุปกรณ์ที่เลือกเปลี่ยนไปแล้ว · เปิด /upgrade อีกครั้ง");
            return false;
        }
        UniqueCompatibility.Resolution result=UniqueCompatibility.resolve(held,keep,plugin.relics());
        if (result == null) {
            plugin.message(player,"ตัวเลือกนี้ใช้กับอุปกรณ์ในมือไม่ได้แล้ว · เปิด /upgrade อีกครั้ง");
            return false;
        }
        ItemStack[] storage=player.getInventory().getStorageContents();
        List<Integer> free=new ArrayList<>();
        for(int i=0;i<storage.length;i++)if(storage[i]==null||storage[i].getType().isAir())free.add(i);
        if(free.size()<result.refunds().size()) {
            plugin.message(player,"ต้องมีช่องว่างในกระเป๋าอย่างน้อย "+result.refunds().size()+" ช่องเพื่อรับคัมภีร์คืน");
            return false;
        }
        player.getInventory().setItem(slot,result.item());
        for(int i=0;i<result.refunds().size();i++)
            player.getInventory().setItem(free.get(i),result.refunds().get(i));
        player.updateInventory();
        plugin.message(player,"เก็บพลังที่เลือกไว้แล้ว · คืนคัมภีร์ "+result.refunds().size()+" เล่มเข้ากระเป๋า");
        return true;
    }
}
