import com.example.voidscape.VoidscapePlugin;
import com.example.voidscape.item.*;
import org.bukkit.*;
import org.bukkit.block.ShulkerBox;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.*;
import org.bukkit.persistence.PersistentDataType;
import java.util.*;

/** Real Bukkit serialization, nested storage and the production 10,000-ticket reward table. */
final class RetiredRelicChecks {
    static int passed;
    static void check(boolean ok,String label){if(!ok)throw new AssertionError("retired relic cleanup: "+label);passed++;}
    static ItemStack old(String id,Material material,String namespace,String tag){
        ItemStack item=new ItemStack(material,3);var meta=item.getItemMeta();
        if(tag.equals("model"))meta.setItemModel(new NamespacedKey(namespace,id));
        else if(tag.equals("selector")){var cmd=meta.getCustomModelDataComponent();cmd.setStrings(List.of(namespace+":"+id));meta.setCustomModelDataComponent(cmd);}
        else if(tag.equals("relic")||tag.equals("relic_v2"))meta.getPersistentDataContainer().set(new NamespacedKey(namespace,tag),PersistentDataType.STRING,id.toUpperCase(Locale.ROOT));
        else meta.getPersistentDataContainer().set(new NamespacedKey(namespace,id),PersistentDataType.BYTE,(byte)1);
        item.setItemMeta(meta);return item;
    }
    static void run(VoidscapePlugin garden){
        String[] ids={"key_shard","repair_stone","void_elixir"};
        Material[] materials={Material.PRISMARINE_SHARD,Material.FLINT,Material.HONEY_BOTTLE};
        for(int index=0;index<ids.length;index++){
            String id=ids[index];Material material=materials[index];
            check(!RemovedRelics.matches(new ItemStack(material)),"vanilla "+material+" is preserved");
            check(!RemovedRelics.matches(old(id,material,"another_plugin","legacy")),"another plugin's tag is preserved");
            for(String namespace:List.of("voidscape","evergarden"))for(String tag:List.of("relic","relic_v2","legacy","model","selector")){
                ItemStack item=old(id,material,namespace,tag);
                item=ItemStack.deserializeBytes(item.serializeAsBytes());
                check(RemovedRelics.matches(item),"serialized legacy identifier "+namespace+":"+id+" "+tag);
                check(RemovedRelics.clean(item)&&item.getAmount()==0,"removed stack and metadata "+namespace+":"+id+" "+tag);
            }
        }
        Inventory inventory=Bukkit.createInventory(null,27);
        inventory.setItem(0,old(ids[0],materials[0],"voidscape","relic_v2"));inventory.setItem(1,new ItemStack(Material.DIAMOND,7));
        ItemStack shulker=new ItemStack(Material.SHULKER_BOX);BlockStateMeta state=(BlockStateMeta)shulker.getItemMeta();
        ShulkerBox box=(ShulkerBox)state.getBlockState();box.getInventory().setItem(0,old(ids[1],materials[1],"evergarden","legacy"));box.getInventory().setItem(1,new ItemStack(Material.EMERALD,5));state.setBlockState(box);shulker.setItemMeta(state);inventory.setItem(2,shulker);
        ItemStack bundle=new ItemStack(Material.BUNDLE);BundleMeta bundleMeta=(BundleMeta)bundle.getItemMeta();bundleMeta.setItems(List.of(old(ids[2],materials[2],"voidscape","model"),new ItemStack(Material.DIAMOND,2)));bundle.setItemMeta(bundleMeta);inventory.setItem(3,bundle);
        check(RemovedRelics.clean(inventory)==3,"inventory cleanup reaches direct, shulker and bundle stacks");
        check(inventory.getItem(0)==null&&inventory.getItem(1).getAmount()==7,"unrelated stacks survive cleanup");
        var cleanedBox=(ShulkerBox)((BlockStateMeta)inventory.getItem(2).getItemMeta()).getBlockState();
        check(cleanedBox.getInventory().getItem(0)==null&&cleanedBox.getInventory().getItem(1).getAmount()==5,"shulker content persists without retired item");
        check(((BundleMeta)inventory.getItem(3).getItemMeta()).getItems().size()==1,"bundle content persists without retired item");
        check(RemovedRelics.clean(inventory)==0,"cleanup is idempotent");
        for(String namespace:List.of("evergarden","voidscape"))for(String id:RemovedRelics.RECIPES)
            check(Bukkit.getRecipe(new NamespacedKey(namespace,id))==null,"old recipe unregistered "+id);
        for(var relic:RelicService.Relic.values())check(!Set.of(ids).contains(relic.id()),"active enum excludes retired identifiers");
        int manaRewards=0;
        for(int ticket=0;ticket<10000;ticket++){
            final int draw=ticket;
            var reward=garden.relics().rollVaultReward(new Random(){@Override public int nextInt(int bound){return bound==10000?draw:0;}});
            check(!RemovedRelics.matches(reward),"production Vault ticket "+ticket+" has no retired reward");
            if(VaultLootTable.reward(ticket)==VaultLootTable.Reward.CONSUMABLE){
                check(reward.getType()==Material.POTION,"common Vault reward is a mana potion");
                var magic=(com.example.advancemagic.AdvanceMagicPlugin)Bukkit.getPluginManager().getPlugin("advance-magic");
                var quality=magic.manaPotions().quality(reward);
                check(quality==com.example.advancemagic.mana.ManaPotionService.Quality.MEDIUM,"fixed-seed common rewards produce Medium mana potions");
                manaRewards++;
            }
        }
        check(manaRewards==5850,"other Vault category weights are preserved");
        for(var page:com.example.voidscape.guide.GuideData.RELICS_PAGES)
            check(!page.content().contains("Key Shard")&&!page.content().contains("Void Walker Elixir")&&!page.content().contains("Vault Repair Stone"),"guide contains no retired crafting/use instructions");
        garden.getLogger().info("PASS "+passed+" retired relic cleanup and Vault checks");
    }
}
