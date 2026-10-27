package com.example.voidscape.item;

import org.bukkit.NamespacedKey;
import org.bukkit.block.Container;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.*;
import org.bukkit.persistence.PersistentDataType;
import java.util.*;

/** Tombstone identifiers only: removed items never have a factory or active gameplay path. */
public final class RemovedRelics implements Listener {
    private static final Set<String> IDS=Set.of("key_shard","repair_stone","void_elixir","rift_blade","storm_bow","nova_bow","eternal_aegis");
    private static final Set<String> NAMESPACES=Set.of("evergarden","voidscape");
    public static final List<String> RECIPES=List.of("craft_void_key","craft_void_key_shapeless","craft_repair_stone","craft_void_elixir");
    public static boolean matches(ItemStack item){
        if(item==null||!item.hasItemMeta())return false;
        var meta=item.getItemMeta();var data=meta.getPersistentDataContainer();
        for(String namespace:NAMESPACES){
            for(String id:IDS)if(data.has(new NamespacedKey(namespace,id)))return true;
            for(String tag:List.of("relic","relic_v2")){
                String id=data.get(new NamespacedKey(namespace,tag),PersistentDataType.STRING);
                if(id!=null&&IDS.contains(id.trim().toLowerCase(Locale.ROOT)))return true;
            }
        }
        NamespacedKey model=meta.getItemModel();
        if(model!=null&&NAMESPACES.contains(model.getNamespace())&&IDS.contains(model.getKey()))return true;
        for(String selector:meta.getCustomModelDataComponent().getStrings()){
            NamespacedKey key=NamespacedKey.fromString(selector.toLowerCase(Locale.ROOT));
            if(key!=null&&NAMESPACES.contains(key.getNamespace())&&IDS.contains(key.getKey()))return true;
        }
        return false;
    }
    public static boolean clean(ItemStack item){return clean(item,0);}
    private static boolean clean(ItemStack item,int depth){
        if(item==null||item.getType().isAir())return false;
        if(matches(item)){item.setAmount(0);return true;}
        if(depth>=32||!item.hasItemMeta())return false;
        ItemMeta meta=item.getItemMeta();boolean changed=false;
        if(meta instanceof BlockStateMeta state&&state.getBlockState() instanceof Container container){
            changed=clean(container.getInventory(),depth+1)>0;
            if(changed)state.setBlockState(container);
        }
        if(meta instanceof BundleMeta bundle){
            List<ItemStack> contents=new ArrayList<>();
            for(ItemStack content:bundle.getItems()){
                changed|=clean(content,depth+1);
                if(!content.getType().isAir()&&content.getAmount()>0)contents.add(content);
            }
            if(changed)bundle.setItems(contents);
        }
        if(changed)item.setItemMeta(meta);return changed;
    }
    public static int clean(Inventory inventory){return clean(inventory,0);}
    private static int clean(Inventory inventory,int depth){
        int changed=0;
        for(int slot=0;slot<inventory.getSize();slot++){
            ItemStack item=inventory.getItem(slot);
            if(clean(item,depth)){inventory.setItem(slot,item.getAmount()==0?null:item);changed++;}
        }
        return changed;
    }
    public static void player(Player player){
        clean(player.getInventory());clean(player.getEnderChest());
        ItemStack cursor=player.getItemOnCursor();if(clean(cursor))player.setItemOnCursor(cursor.getAmount()==0?null:cursor);
        for(String namespace:NAMESPACES){
            player.undiscoverRecipes(RECIPES.stream().map(id->new NamespacedKey(namespace,id)).toList());
            for(String id:IDS){player.getPersistentDataContainer().remove(new NamespacedKey(namespace,id));player.getPersistentDataContainer().remove(new NamespacedKey(namespace,"cd_"+id));}
        }
    }
    public static void entity(Entity entity){
        if(entity instanceof Item dropped){var item=dropped.getItemStack();if(matches(item))dropped.remove();else if(clean(item))dropped.setItemStack(item);}
        else if(entity instanceof ItemFrame frame){var item=frame.getItem();if(clean(item))frame.setItem(item.getAmount()==0?null:item);}
        if(entity instanceof InventoryHolder holder)clean(holder.getInventory());
        if(entity instanceof LivingEntity living&&living.getEquipment()!=null){
            var equipment=living.getEquipment();
            ItemStack[] armor=equipment.getArmorContents();for(ItemStack item:armor)clean(item);equipment.setArmorContents(armor);
            var main=equipment.getItemInMainHand();if(clean(main))equipment.setItemInMainHand(main.getAmount()==0?null:main);
            var off=equipment.getItemInOffHand();if(clean(off))equipment.setItemInOffHand(off.getAmount()==0?null:off);
        }
    }
    public static void chunk(org.bukkit.Chunk chunk){
        for(var state:chunk.getTileEntities())if(state instanceof Container container)clean(container.getInventory());
        for(Entity entity:chunk.getEntities())entity(entity);
    }
    @EventHandler public void load(ChunkLoadEvent event){chunk(event.getChunk());}
    @EventHandler public void join(org.bukkit.event.player.PlayerJoinEvent event){player(event.getPlayer());}
}
