import com.example.advancemagic.AdvanceMagicPlugin;
import com.example.advancemagic.spell.Spell;
import com.example.voidscape.VoidscapePlugin;
import com.example.voidscape.crop.CropType;
import com.example.voidscape.dungeon.GuardianAppearance;
import com.example.voidscape.item.RelicService.Relic;
import com.example.voidscape.world.DungeonLayout;
import org.bukkit.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.geysermc.geyser.registry.Registries;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.translator.item.CustomItemTranslator;
import org.geysermc.geyser.platform.spigot.shaded.net.kyori.adventure.key.Key;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.*;
import java.nio.file.*;
import java.util.*;

/** Real Bukkit factories + the installed Geyser registry/matcher, without a client connection. */
public final class BedrockItemChecks extends JavaPlugin {
    int checks;
    GeyserSession context;
    final Set<String> seen=new HashSet<>();

    @Override public void onEnable(){
        Bukkit.getScheduler().runTaskLater(this,()->{
            try {
                var garden=(VoidscapePlugin)Bukkit.getPluginManager().getPlugin("Evergarden");
                var magic=(AdvanceMagicPlugin)Bukkit.getPluginManager().getPlugin("advance-magic");
                if(!garden.isEnabled()||!magic.isEnabled()||Registries.ITEMS.get().isEmpty())throw new AssertionError("Plugins/registry unavailable");
                // Only custom-model predicates are evaluated; their suppliers read item
                // components, never world/session state. An unconnected context is enough.
                var unsafeType=Class.forName("sun.misc.Unsafe");
                var field=unsafeType.getDeclaredField("theUnsafe");field.setAccessible(true);
                context=(GeyserSession)unsafeType.getMethod("allocateInstance",Class.class).invoke(field.get(null),GeyserSession.class);
                verify(garden.relics().createVoidElixir(1),"voidscape:void_elixir");
                // Existing elixirs are migrated to the same direct model, preserving count.
                var legacy=garden.relics().createVoidElixir(4);
                var meta=legacy.getItemMeta();meta.setItemModel(null);legacy.setItemMeta(meta);
                if(!garden.relics().migrate(legacy)||legacy.getAmount()!=4)throw new AssertionError("Legacy elixir migration");
                verify(legacy,"voidscape:void_elixir");
                for(var relic:Relic.values())verify(garden.relics().create(relic,1),"voidscape:"+relic.id());
                for(var crop:CropType.values()){
                    verify(garden.crops().factory().createSeed(crop,1),"voidscape:seed_"+crop.id);
                    verify(garden.crops().factory().createFood(crop,1),"voidscape:crop_"+crop.id);
                    for(int stage=0;stage<3;stage++)verify(garden.crops().factory().createPlantDisplay(crop,stage),"voidscape:crop_"+crop.id+"_stage_"+stage);
                }
                for(var kind:DungeonLayout.Kind.values())for(boolean boss:new boolean[]{false,true}){
                    var item=GuardianAppearance.mask(kind,boss);
                    verify(item,item.getItemMeta().getCustomModelDataComponent().getStrings().getFirst());
                }
                for(var spell:Spell.values()){
                    verify(magic.wands().create(spell),"advance_magic:"+spell.id());
                    verify(garden.relics().createMagicCore(spell.id()),"advance_magic:core_"+spell.id());
                }
                verify(magic.flyingStaff().create(),"advance_magic:flying_staff");
                verify(magic.restoration().createCore(),"advance_magic:restoration_core");
                verify(magic.restoration().createRepairWand(),"advance_magic:restoration_wand");
                var upgrades=new com.example.voidscape.world.WhaleTreasure(garden);
                var makeUpgrade=upgrades.getClass().getDeclaredMethod("createWandUpgrade",Random.class);makeUpgrade.setAccessible(true);
                Set<String> upgradeIds=new HashSet<>();
                for(int seed=0;seed<100&&upgradeIds.size()<3;seed++){
                    var item=(ItemStack)makeUpgrade.invoke(upgrades,new Random(seed));
                    String id=item.getItemMeta().getItemModel().toString();
                    if(upgradeIds.add(id))verify(item,id);
                }
                var makeImage=magic.flyingStaff().getClass().getDeclaredMethod("image",String.class,int.class);makeImage.setAccessible(true);
                for(String phase:List.of("summon","idle","flight","dismiss"))
                    verify((ItemStack)makeImage.invoke(magic.flyingStaff(),phase,0),"advance_magic:flying_staff_"+phase);
                verify(new ItemStack(Material.HONEY_BOTTLE),null);
                // These displays are constructed inline by their services, with a
                // vanilla carrier plus a custom_model_data string and no item_model.
                for(String id:List.of("azure_portal","restoration_altar_whale","restoration_altar_garden","restoration_altar_observatory")){
                    var item=new ItemStack(Material.IRON_HELMET);var displayMeta=item.getItemMeta();
                    var data=displayMeta.getCustomModelDataComponent();data.setStrings(List.of("voidscape:"+id));
                    displayMeta.setCustomModelDataComponent(data);item.setItemMeta(displayMeta);
                    verify(item,"voidscape:"+id);
                }
                Set<String> expectedIds=new HashSet<>();
                for(var owner:List.of(garden,magic)){
                    var json=com.google.gson.JsonParser.parseString(Files.readString(owner.getDataFolder().toPath().resolve("resource-packs/geyser-mappings.json"))).getAsJsonObject();
                    for(var group:json.getAsJsonObject("items").entrySet())for(var entry:group.getValue().getAsJsonArray())
                        expectedIds.add(entry.getAsJsonObject().get("bedrock_identifier").getAsString());
                }
                expectedIds.removeAll(seen);
                if(!expectedIds.isEmpty())throw new AssertionError("Missing coverage: "+expectedIds);
                MythicSpellChecks.run(this,magic,garden);
                Files.writeString(Path.of("bedrock-items-result.txt"),"PASS "+checks+" translations / "+seen.size()+" custom identifiers / "+Registries.ITEMS.get().size()+" Bedrock protocol tables; vanilla honey bottle unchanged");
            }catch(Throwable error){
                getLogger().log(java.util.logging.Level.SEVERE,"BEDROCK ITEM CHECK FAILED",error);
                try{Files.writeString(Path.of("bedrock-items-result.txt"),"FAIL after "+checks+": "+error);}catch(Exception ignored){}
            }finally{Bukkit.getScheduler().runTaskLater(this,Bukkit::shutdown,5);}
        },40);
    }
    void verify(ItemStack item,String expected){
        var components=new DataComponents(new HashMap<>());
        var meta=item.getItemMeta();
        String model=meta!=null&&meta.getItemModel()!=null?meta.getItemModel().toString():item.getType().getKey().toString();
        components.put(DataComponentTypes.ITEM_MODEL,Key.key(model));
        if(meta!=null){
            var data=meta.getCustomModelDataComponent();
            components.put(DataComponentTypes.CUSTOM_MODEL_DATA,new CustomModelData(data.getFloats(),data.getFlags(),data.getStrings(),List.of()));
        }
        for(var mappings:Registries.ITEMS.get().values()){
            var mapping=mappings.getMapping(item.getType().getKey().toString());
            var result=CustomItemTranslator.getCustomItem(context,item.getAmount(),components,mapping);
            if(expected==null?result!=null:result==null||!expected.equals(result.getIdentifier()))
                throw new AssertionError("Wrong Bedrock item: "+item.getType()+" model="+model+" expected="+expected+" actual="+(result==null?"vanilla":result.getIdentifier()));
            checks++;
        }
        if(expected!=null)seen.add(expected);
    }
}
