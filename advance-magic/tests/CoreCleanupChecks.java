import com.example.advancemagic.AdvanceMagicPlugin;
import com.example.advancemagic.item.WandService;
import com.example.advancemagic.spell.Spell;
import com.example.voidscape.VoidscapePlugin;
import com.example.voidscape.boss.BossArmorCores;
import com.example.voidscape.boss.SoulOrbs;
import com.example.voidscape.world.WhaleTreasure;
import org.bukkit.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import java.lang.reflect.Method;
import java.nio.file.*;
import java.util.*;

/** Real Bukkit item components, migration, recipe registry and active utility factories. */
public final class CoreCleanupChecks extends JavaPlugin {
    private AdvanceMagicPlugin magic;
    private VoidscapePlugin garden;
    private int passed;
    @Override public void onEnable(){Bukkit.getScheduler().runTaskLater(this,()->{
        try{run();finish(null);}catch(Throwable error){finish(error);}
    },20);}
    private void check(boolean value,String message){if(!value)throw new AssertionError(message);passed++;getLogger().info("PASS "+message);}
    private void vanillaCore(ItemStack item,Spell spell,String source){
        check(item.getType()==Material.HEART_OF_THE_SEA,source+" retains vanilla core base "+spell.id());
        var meta=item.getItemMeta();
        check(!meta.hasItemModel()&&!meta.hasCustomModelData()&&meta.getCustomModelDataComponent().getStrings().isEmpty(),source+" has no retired visual routes "+spell.id());
        check(magic.wands().coreSpell(item)==spell&&magic.restoration().isFuel(item),source+" retains identity and restoration fuel compatibility "+spell.id());
    }
    private void run()throws Exception {
        magic=(AdvanceMagicPlugin)Bukkit.getPluginManager().getPlugin("advance-magic");garden=(VoidscapePlugin)Bukkit.getPluginManager().getPlugin("Evergarden");
        check(magic!=null&&magic.isEnabled()&&garden!=null&&garden.isEnabled(),"both current production plugins enabled");
        check(Spell.values().length==21&&com.example.voidscape.item.RelicService.MAGIC_CORES.size()==21,"all 21 historical identities remain available for legacy data");
        Inventory legacyInventory=Bukkit.createInventory(null,27);int slot=0;
        NamespacedKey fixture=new NamespacedKey(this,"fixture_value");
        for(Spell spell:Spell.values()){
            ItemStack created=magic.wands().createCore(spell);vanillaCore(created,spell,"Advance Magic factory");
            ItemStack queued=garden.relics().createMagicCore(spell.id());vanillaCore(queued,spell,"Evergarden queued-reward factory");
            ItemStack old=created.clone();old.setAmount(17);var meta=old.getItemMeta();
            NamespacedKey retired=new NamespacedKey("advance_magic","core_"+spell.id());meta.setItemModel(retired);
            var cmd=meta.getCustomModelDataComponent();cmd.setStrings(List.of(retired.toString()));meta.setCustomModelDataComponent(cmd);
            meta.getPersistentDataContainer().set(fixture,PersistentDataType.INTEGER,7821);old.setItemMeta(meta);
            Set<NamespacedKey> beforeKeys=Set.copyOf(old.getItemMeta().getPersistentDataContainer().getKeys());
            check(magic.wands().migrate(old),"old modeled core migrates "+spell.id());vanillaCore(old,spell,"Migrated item");
            check(old.getAmount()==17&&old.getItemMeta().getPersistentDataContainer().getKeys().equals(beforeKeys),"migration preserves amount and every PDC key "+spell.id());
            check(old.getItemMeta().getPersistentDataContainer().get(fixture,PersistentDataType.INTEGER)==7821,"migration preserves unrelated PDC value "+spell.id());
            for(NamespacedKey key:beforeKeys)if(!key.equals(fixture))check(spell.id().equals(old.getItemMeta().getPersistentDataContainer().get(key,PersistentDataType.STRING)),"migration preserves legacy identity value "+key+" "+spell.id());
            ItemStack once=old.clone();check(!magic.wands().migrate(old)&&old.equals(once),"core migration is idempotent "+spell.id());
            ItemStack serialized=ItemStack.deserializeBytes(old.serializeAsBytes());vanillaCore(serialized,spell,"Serialized legacy item");
            // Inventory migration must reach items that were never held by a player.
            meta=serialized.getItemMeta();meta.setItemModel(retired);cmd=meta.getCustomModelDataComponent();cmd.setStrings(List.of(retired.toString()));meta.setCustomModelDataComponent(cmd);serialized.setItemMeta(meta);
            legacyInventory.setItem(slot++,serialized);
            ItemStack[] matrix=new ItemStack[9];Arrays.fill(matrix,new ItemStack(Material.NETHERITE_INGOT));matrix[4]=old;
            check(magic.wands().containsCore(matrix)&&magic.wands().craftingSpell(matrix)==null,"legacy core remains protected and cannot craft retired wand "+spell.id());
        }
        magic.wands().migrate(legacyInventory);slot=0;
        for(Spell spell:Spell.values())vanillaCore(legacyInventory.getItem(slot++),spell,"Inventory migration");
        recipes();activeItems();
        check(magic.wands().coreSpell(new ItemStack(Material.HEART_OF_THE_SEA))==null&&!magic.restoration().isFuel(new ItemStack(Material.HEART_OF_THE_SEA)),"ordinary hearts cannot become free restoration fuel");
    }
    private void recipes(){
        for(String suffix:List.of("ni_c","ni_b","ns_c","ns_b"))check(Bukkit.getRecipe(new NamespacedKey(magic,"wand_gen_"+suffix))==null,"generic crafting recipe remains retired "+suffix);
        for(Spell spell:Spell.values())for(String suffix:List.of("","_bottom","_ni_c","_ni_b","_ns_c","_ns_b"))
            check(Bukkit.getRecipe(new NamespacedKey(magic,spell.id()+suffix))==null,"per-spell crafting recipe remains retired "+spell.id()+suffix);
    }
    private void keepsModel(ItemStack item,NamespacedKey model,String name){
        check(model.equals(item.getItemMeta().getItemModel())&&item.getItemMeta().getCustomModelDataComponent().getStrings().equals(List.of(model.toString())),name+" retains its active modeled appearance");
        magic.wands().migrate(item);
        check(model.equals(item.getItemMeta().getItemModel())&&magic.wands().coreSpell(item)==null,name+" cannot be mistaken for retired crafting core");
    }
    private void activeItems()throws Exception {
        ItemStack restoration=magic.restoration().createCore();keepsModel(restoration,new NamespacedKey("advance_magic","restoration_core"),"Core of Restoration");
        check(magic.restoration().isCore(restoration)&&magic.restoration().isFuel(restoration),"active restoration core remains usable fuel");
        ItemStack wand=magic.restoration().createRepairWand();keepsModel(wand,new NamespacedKey("advance_magic","restoration_wand"),"Restoration wand");
        check(magic.restoration().charges(wand)==5,"restoration wand retains five charges");
        ItemStack legend=SoulOrbs.legendCore(garden);keepsModel(legend,SoulOrbs.LEGEND_MODEL,"Core of Legend");check(SoulOrbs.isLegendCore(garden,legend),"Core of Legend keeps exchange identity");
        for(var core:BossArmorCores.Core.values()){
            ItemStack item=BossArmorCores.create(garden,core);keepsModel(item,core.model(),core.title);check(BossArmorCores.identify(garden,item)==core,"armor exchange core retains identity "+core);
        }
        for(var orb:SoulOrbs.Orb.values()){
            ItemStack item=SoulOrbs.orb(garden,orb);keepsModel(item,new NamespacedKey("voidscape","boss_orb_"+orb.id()),"Soul Orb "+orb.id());check(SoulOrbs.identify(garden,item)==orb,"Soul Orb retains exchange identity "+orb);
        }
        WhaleTreasure treasure=new WhaleTreasure(garden);Method factory=WhaleTreasure.class.getDeclaredMethod("createWandUpgrade",Random.class);factory.setAccessible(true);
        String[] ids={"wand_repair","wand_damage","wand_cooldown"};
        for(int index=0;index<ids.length;index++){
            final int draw=index;ItemStack item=(ItemStack)factory.invoke(treasure,new Random(){@Override public int nextInt(int bound){return draw;}});
            keepsModel(item,new NamespacedKey("advance_magic",ids[index]),"Active upgrade "+ids[index]);
            check(magic.wands().upgrade(item)==WandService.Upgrade.values()[index],"upgrade retains functional identity "+ids[index]);
        }
    }
    private void finish(Throwable error){
        if(error!=null)getLogger().log(java.util.logging.Level.SEVERE,"CORE CLEANUP FAILED",error);
        String result=(error==null?"PASS ":"FAIL ")+passed+" core cleanup checks; actual Paper factories, legacy visual migration, PDC/amount/idempotence, fuel, retired recipes and active cores"+(error==null?"":"; "+error);
        try{Files.writeString(Path.of("core-cleanup-result.txt"),result);}catch(Exception failure){getLogger().log(java.util.logging.Level.SEVERE,"Could not save result",failure);}
        getLogger().info(result);Bukkit.getScheduler().runTaskLater(this,Bukkit::shutdown,1);
    }
}
