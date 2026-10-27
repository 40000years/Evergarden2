import com.example.voidscape.VoidscapePlugin;
import com.example.voidscape.boss.ArkhaiaWaves;
import com.ticxo.modelengine.api.ModelEngineAPI;
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

/** Translate actual boss reward items using the installed Geyser registry on every protocol. */
public final class BossOrbsNativeProbe extends JavaPlugin {
    int checks,waited;
    void check(boolean ok,String label){if(!ok)throw new AssertionError(label);checks++;getLogger().info("PASS: "+label);}
    @Override public void onEnable(){Bukkit.getScheduler().runTaskLater(this,this::inspect,160);}
    void inspect(){try{
        if(ModelEngineAPI.getBlueprint("arkhaia_styx")==null){if((waited+=20)<1200){Bukkit.getScheduler().runTaskLater(this,this::inspect,20);return;}throw new AssertionError("Model Engine import did not finish");}
        var garden=(VoidscapePlugin)Bukkit.getPluginManager().getPlugin("Evergarden");
        check(garden!=null&&garden.isEnabled(),"reward release boots with Model Engine and Geyser");
        check(!Registries.ITEMS.get().isEmpty(),"native Geyser protocol item tables loaded");
        var unsafeType=Class.forName("sun.misc.Unsafe");var field=unsafeType.getDeclaredField("theUnsafe");field.setAccessible(true);
        GeyserSession context=(GeyserSession)unsafeType.getMethod("allocateInstance",Class.class).invoke(field.get(null),GeyserSession.class);
        Class<?> rewards=Class.forName("com.example.voidscape.boss.BossRewards");var factory=rewards.getDeclaredMethod("orb",VoidscapePlugin.class,ArkhaiaWaves.Kind.class);factory.setAccessible(true);
        for(var kind:ArkhaiaWaves.Kind.values()){
            String id=kind==ArkhaiaWaves.Kind.BANSHEE_WAIL?"banshee":kind.name().toLowerCase(Locale.ROOT);
            ItemStack item=(ItemStack)factory.invoke(null,garden,kind);var meta=item.getItemMeta();
            check(meta.getItemModel().toString().equals("voidscape:boss_orb_"+id),"actual trophy factory uses its own item model: "+id);
            check(ModelEngineAPI.getBlueprint("arkhaia_"+(id.equals("banshee")?"banshee_wail":id))!=null,"corresponding native boss rig still imports: "+id);
            var components=new DataComponents(new HashMap<>());
            components.put(DataComponentTypes.ITEM_MODEL,Key.key(meta.getItemModel().toString()));
            var data=meta.getCustomModelDataComponent();components.put(DataComponentTypes.CUSTOM_MODEL_DATA,new CustomModelData(data.getFloats(),data.getFlags(),data.getStrings(),List.of()));
            for(var table:Registries.ITEMS.get().values()){
                var mapping=table.getMapping(item.getType().getKey().toString());var result=CustomItemTranslator.getCustomItem(context,1,components,mapping);
                check(result!=null&&result.getIdentifier().equals("voidscape:boss_orb_"+id),"Geyser translates actual trophy to the correct Bedrock orb: "+id);
            }
        }
        Files.writeString(Path.of("model-engine-result.txt"),"PASS: boss orbs / "+checks+" native Model Engine and Geyser checks\n");Bukkit.shutdown();
    }catch(Throwable error){getLogger().log(java.util.logging.Level.SEVERE,"Boss orb native probe",error);try{Files.writeString(Path.of("model-engine-result.txt"),"FAIL: "+error);}catch(Exception ignored){}Bukkit.shutdown();}}
}
