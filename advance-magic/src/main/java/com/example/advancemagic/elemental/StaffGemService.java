package com.example.advancemagic.elemental;

import com.example.advancemagic.AdvanceMagicPlugin;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import java.util.*;

/** Two removable physical gems per staff. All transfers commit only after inventory preflight. */
public final class StaffGemService {
    public enum Gem {
        DAMAGE("damage", "Damage Gem", "ดาเมจเวท +15%", Material.BLAZE_POWDER, "wand_damage"),
        COOLDOWN("cooldown", "Cooldown Gem", "คูลดาวน์ −15%", Material.AMETHYST_SHARD, "wand_cooldown"),
        MAX("max", "Max Gem", "ความทนทานสูงสุด +250 ครั้งร่าย", Material.PRISMARINE_SHARD, "wand_repair");
        public final String id,title,effect,model;
        public final Material material;
        Gem(String id,String title,String effect,Material material,String model){this.id=id;this.title=title;this.effect=effect;this.material=material;this.model=model;}
        static Gem parse(String id){for(Gem gem:values())if(gem.id.equals(id))return gem;return null;}
    }
    public static final int SOCKETS=2;
    public static final int CAPACITY_PER_GEM=250;
    private static final NamespacedKey ITEM=new NamespacedKey("advance_magic","staff_gem"),
        SLOTS=new NamespacedKey("advance_magic","staff_gems_v1"),
        LEGACY=new NamespacedKey("advance_magic","wand_upgrade");
    private final AdvanceMagicPlugin plugin;
    public StaffGemService(AdvanceMagicPlugin plugin){this.plugin=plugin;}

    public static ItemStack create(Gem gem){
        Objects.requireNonNull(gem);
        ItemStack item=new ItemStack(gem.material);var meta=item.getItemMeta();
        meta.setDisplayName((gem==Gem.DAMAGE?ChatColor.RED:ChatColor.AQUA)+gem.title);
        List<String> lore=new ArrayList<>(List.of(ChatColor.GRAY+gem.effect,ChatColor.YELLOW+"ใส่หรือถอดที่ /spell · คทามี Gem 2 ช่อง",
            ChatColor.GRAY+"ใส่ชนิดเดียวกันซ้ำได้ · ถอดแล้วได้ Gem คืน"));
        if(gem==Gem.MAX)lore.addAll(List.of(ChatColor.GRAY+"เพิ่มเพดานเท่านั้น · ใช้ระบบซ่อมเพื่อเติมจำนวนครั้ง",
            ChatColor.GRAY+"ใช้กับคทาบิน: เพิ่ม Max +5 ต่อขั้น (สูงสุด +10)"));
        meta.setLore(lore);
        meta.setItemModel(new NamespacedKey("advance_magic",gem.model));
        var model=meta.getCustomModelDataComponent();model.setStrings(List.of("advance_magic:"+gem.model));meta.setCustomModelDataComponent(model);
        meta.getPersistentDataContainer().set(ITEM,PersistentDataType.STRING,gem.id);
        item.setItemMeta(meta);return item;
    }
    /** Old loose upgrade Cores can be socketed; they no longer increment combat staff ranks. */
    public static Gem identify(ItemStack item){
        if(item==null||item.getAmount()<1||!item.hasItemMeta())return null;
        var data=item.getItemMeta().getPersistentDataContainer();
        String id=data.get(ITEM,PersistentDataType.STRING);Gem gem=Gem.parse(id);
        if(gem==null&&id==null){
            String old=data.get(LEGACY,PersistentDataType.STRING);
            if("wand_damage".equals(old))gem=Gem.DAMAGE;
            if("wand_cooldown".equals(old))gem=Gem.COOLDOWN;
            if("wand_repair".equals(old))gem=Gem.MAX;
        }
        return gem!=null&&item.getType()==gem.material?gem:null;
    }
    public static Gem[] sockets(ItemStack staff){
        Gem[] result=new Gem[SOCKETS];
        if(staff==null||!staff.hasItemMeta())return result;
        String raw=staff.getItemMeta().getPersistentDataContainer().get(SLOTS,PersistentDataType.STRING);
        if(raw==null)return result;
        String[] rows=raw.split(",",-1);if(rows.length!=SOCKETS)return result;
        for(int i=0;i<SOCKETS;i++){
            if(!rows[i].isEmpty()&&Gem.parse(rows[i])==null)return new Gem[SOCKETS];
            result[i]=Gem.parse(rows[i]);
        }
        return result;
    }
    public static int count(ItemStack staff,Gem gem){int count=0;for(Gem value:sockets(staff))if(value==gem)count++;return count;}
    public static int damagePercent(ItemStack staff){return count(staff,Gem.DAMAGE)*15;}
    public static int cooldownPercent(ItemStack staff){return count(staff,Gem.COOLDOWN)*15;}
    public static int capacityBonus(ItemStack staff){return count(staff,Gem.MAX)*CAPACITY_PER_GEM;}
    public static String summary(ItemStack staff){
        return "Gem: Damage +"+damagePercent(staff)+"% · Cooldown −"+cooldownPercent(staff)+"% · Max +"+capacityBonus(staff);
    }
    private boolean valid(Player player,int staffSlot,ItemStack snapshot,int socket){
        return socket>=0&&socket<SOCKETS&&staffSlot>=0&&staffSlot<36
            &&player.getInventory().getHeldItemSlot()==staffSlot&&snapshot!=null&&snapshot.getAmount()==1
            &&plugin.staffSpells().tier(snapshot)!=null&&Objects.equals(snapshot,player.getInventory().getItem(staffSlot));
    }
    private static ItemStack[] copy(Player player){
        return Arrays.stream(player.getInventory().getStorageContents()).map(item->item==null?null:item.clone()).toArray(ItemStack[]::new);
    }
    private static boolean give(ItemStack[] contents,ItemStack item,int excluded){
        for(int slot=0;slot<contents.length;slot++){
            if(slot==excluded)continue;var stack=contents[slot];
            if(stack!=null&&stack.isSimilar(item)&&stack.getAmount()<Math.min(stack.getMaxStackSize(),item.getMaxStackSize())){
                stack.setAmount(stack.getAmount()+1);return true;
            }
        }
        for(int slot=0;slot<contents.length;slot++)if(slot!=excluded&&(contents[slot]==null||contents[slot].getType().isAir())){
            contents[slot]=item;return true;
        }
        return false;
    }
    private void write(ItemStack staff,Gem[] gems){
        // Snapshot BEFORE changing capacity. Installing/swapping a Gem never repairs the staff.
        int remaining=plugin.wands().usesLeft(staff);
        var meta=staff.getItemMeta();var data=meta.getPersistentDataContainer();
        data.set(SLOTS,PersistentDataType.STRING,(gems[0]==null?"":gems[0].id)+","+(gems[1]==null?"":gems[1].id));
        for(String old:List.of("damage_level","cooldown_level","durability_level"))data.remove(new NamespacedKey(plugin,old));
        staff.setItemMeta(meta);
        int maximum=plugin.wands().maxUses(staff);
        meta=staff.getItemMeta();meta.getPersistentDataContainer().set(new NamespacedKey(plugin,"durability"),PersistentDataType.INTEGER,Math.min(remaining,maximum));
        staff.setItemMeta(meta);plugin.staffSpells().refresh(staff);
    }
    private static void serverThread(){if(!Bukkit.isPrimaryThread())throw new IllegalStateException("Gem transfers require the server thread");}
    public StaffSpellService.Result insert(Player player,int staffSlot,ItemStack staff,int gemSlot,ItemStack gemSnapshot,int socket){
        serverThread();
        if(!valid(player,staffSlot,staff,socket)||gemSlot<0||gemSlot>=36||gemSlot==staffSlot
            ||!Objects.equals(gemSnapshot,player.getInventory().getItem(gemSlot)))return StaffSpellService.Result.fail("ไอเทมเปลี่ยนแล้ว กรุณาเลือก Gem ใหม่");
        Gem gem=identify(gemSnapshot);if(gem==null)return StaffSpellService.Result.fail("เลือก Damage / Cooldown / Max Gem ในกระเป๋าก่อน");
        Gem[] gems=sockets(staff);Gem old=gems[socket];
        if(old==gem)return StaffSpellService.Result.fail("ช่องนี้มี "+gem.title+" อยู่แล้ว");
        ItemStack[] contents=copy(player);ItemStack source=contents[gemSlot];
        if(source.getAmount()==1)contents[gemSlot]=null;else source.setAmount(source.getAmount()-1);
        if(old!=null&&!give(contents,create(old),staffSlot))return StaffSpellService.Result.fail("กระเป๋าเต็ม กรุณาเว้นที่รับ Gem เดิมคืน");
        gems[socket]=gem;ItemStack updated=staff.clone();write(updated,gems);contents[staffSlot]=updated;
        player.getInventory().setStorageContents(contents);player.saveData();
        return StaffSpellService.Result.ok("ใส่ "+gem.title+" ช่อง "+(socket+1)+(old==null?"":" · ได้ "+old.title+" คืน"));
    }
    public StaffSpellService.Result remove(Player player,int staffSlot,ItemStack staff,int socket){
        serverThread();if(!valid(player,staffSlot,staff,socket))return StaffSpellService.Result.fail("คทาเปลี่ยนแล้ว กรุณาเปิด /spell ใหม่");
        Gem[] gems=sockets(staff);Gem gem=gems[socket];if(gem==null)return StaffSpellService.Result.fail("ช่อง Gem นี้ว่างอยู่");
        ItemStack[] contents=copy(player);if(!give(contents,create(gem),staffSlot))return StaffSpellService.Result.fail("กระเป๋าเต็ม กรุณาเว้นที่รับ Gem คืน");
        gems[socket]=null;ItemStack updated=staff.clone();write(updated,gems);contents[staffSlot]=updated;
        player.getInventory().setStorageContents(contents);player.saveData();return StaffSpellService.Result.ok("ถอด "+gem.title+" คืนกระเป๋าแล้ว");
    }
}
