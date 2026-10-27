import com.example.voidscape.VoidscapePlugin;
import com.example.voidscape.item.AdventurerWeaponService.AdventurerWeapon;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.plugin.java.JavaPlugin;
import java.nio.file.*;
import java.util.*;

/** What Geyser actually registers for the bow/crossbow definitions: the Bedrock client's item components. */
public class RangedGeyserProbe extends JavaPlugin {
    int checks;
    void check(boolean ok,String label){if(!ok)throw new AssertionError(label);checks++;getLogger().info("PASS: "+label);}
    @Override public void onEnable(){Bukkit.getScheduler().runTaskLater(this,()->{try{run();finish(null);}catch(Throwable e){finish(e);}},220);}
    void run()throws Exception{
        var garden=(VoidscapePlugin)Bukkit.getPluginManager().getPlugin("Evergarden");check(garden!=null&&garden.isEnabled(),"Evergarden boots");
        var weapons=garden.adventurerWeapons();
        for(var weapon:List.of(AdventurerWeapon.THORNWHISPER,AdventurerWeapon.RUNELOCK)){
            check(AdventurerWeapon.enabledWeapons().contains(weapon),weapon.id()+": enabled and listed for the test menu and vaults");
            var item=weapons.create(weapon);var meta=item.getItemMeta();
            check(item.getType()==(weapon==AdventurerWeapon.THORNWHISPER?Material.BOW:Material.CROSSBOW),weapon.id()+": native base item");
            check(meta.getItemModel()!=null&&meta.getItemModel().toString().equals("voidscape:"+weapon.modelKey()),weapon.id()+": item model key "+meta.getItemModel());
            check(weapons.identify(item)==weapon,weapon.id()+": identified");
        }
        var geyser=Bukkit.getPluginManager().getPlugin("Geyser-Spigot");check(geyser!=null&&geyser.isEnabled(),"Geyser enabled");
        var registries=Class.forName("org.geysermc.geyser.registry.Registries",true,geyser.getClass().getClassLoader());
        Object registry=registries.getField("ITEMS").get(null);
        Map<?,?> protocols=(Map<?,?>)registry.getClass().getMethod("get").invoke(registry);
        check(!protocols.isEmpty(),"Geyser item tables registered");
        Map<String,Object> dump=new TreeMap<>();
        for(var protocol:protocols.entrySet()){
            Object items=protocol.getValue();var lookup=items.getClass().getMethod("getDefinition",String.class);
            for(String id:List.of("voidscape:adventurer_thornwhisper","voidscape:adventurer_runelock","voidscape:adventurer_runelock_loaded_arrow","voidscape:adventurer_runelock_loaded_rocket")){
                Object definition=lookup.invoke(items,id);
                check(definition!=null,protocol.getKey()+": "+id+" registered");
                Object data=definition.getClass().getMethod("getComponentData").invoke(definition);
                dump.put(protocol.getKey()+" "+id,String.valueOf(data));
                Map<?,?> components=(Map<?,?>)((Map<?,?>)data).get("components");
                Map<?,?> properties=(Map<?,?>)components.get("item_properties");
                check(components.containsKey("minecraft:shooter"),protocol.getKey()+": "+id+" has minecraft:shooter (client treats it as a drawn ranged item)");
                check(components.containsKey("minecraft:use_modifiers"),protocol.getKey()+": "+id+" has use_modifiers (use duration for the draw frame)");
                Map<?,?> icon=(Map<?,?>)properties.get("minecraft:icon");Map<?,?> textures=(Map<?,?>)icon.get("textures");
                check(String.valueOf(textures.get("default")).startsWith("voidscape.adventurer_"),protocol.getKey()+": "+id+" icon "+textures.get("default"));
                boolean bow=id.endsWith("thornwhisper");
                Map<?,?> use=(Map<?,?>)components.get("minecraft:use_animation");
                check(use!=null&&(bow?"bow":"crossbow").equals(use.get("value")),protocol.getKey()+": "+id+" minecraft:use_animation "+(use==null?null:use.get("value")));
                check(Integer.valueOf(bow?4:9).equals(properties.get("use_animation")),protocol.getKey()+": "+id+" item_properties.use_animation "+properties.get("use_animation"));
                check(!components.containsKey("minecraft:food")&&!properties.containsKey("can_always_eat"),protocol.getKey()+": "+id+" is not food");
                Map<?,?> sent=(Map<?,?>)items.getClass().getMethod("getItemDefinitions").invoke(items);
                check(sent.values().stream().anyMatch(d->d==definition),protocol.getKey()+": "+id+" patched definition is the one each session's ItemComponentPacket sends");
            }
        }
        Files.writeString(Path.of("ranged-geyser-components.txt"),String.join("\n",dump.entrySet().stream().map(e->e.getKey()+" = "+e.getValue()).toList()));
    }
    void finish(Throwable error){
        try{Files.writeString(Path.of("arkhaia-result.txt"),error==null?"PASS "+checks+" Paper checks":"FAIL "+error);}catch(Exception ignored){}
        if(error!=null)getLogger().log(java.util.logging.Level.SEVERE,"Ranged Geyser probe failed",error);
        Bukkit.getScheduler().runTaskLater(this,Bukkit::shutdown,5);
    }
}
