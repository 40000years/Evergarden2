package com.example.voidscape.enchant;

import com.example.voidscape.VoidscapePlugin;
import com.example.voidscape.world.BossStructureLayout;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.*;
import org.bukkit.persistence.PersistentDataType;
import java.io.*;
import java.util.*;

/** An entitlement belongs to one enchantment at its awarded level, never to a whole item. */
public final class BossEnchantService {
    private final VoidscapePlugin plugin;
    private final NamespacedKey entitlement,origin;
    private final YamlConfiguration config;
    private final Map<Material,Map<Enchantment,int[]>> legacy=new EnumMap<>(Material.class);
    public BossEnchantService(VoidscapePlugin plugin){
        this.plugin=plugin;entitlement=plugin.key("boss_enchant_entitlements");origin=plugin.key("boss_treasure_v3");
        var file=new File(plugin.getDataFolder(),"boss-treasure.yml");
        if(!file.isFile())plugin.saveResource("boss-treasure.yml",false);
        config=YamlConfiguration.loadConfiguration(file);boolean changed=false;
        var defaults=Map.<String,Object>of("anvil-transfer",true,"protection-extra-point-reduction",.04,
            "depth-strider-extra-level-speed",.10,"swift-sneak-overflow",true);
        for(var entry:defaults.entrySet())if(!config.contains("mechanics."+entry.getKey())){
            config.set("mechanics."+entry.getKey(),entry.getValue());changed=true;
        }
        if(changed)try{config.save(file);}catch(IOException error){plugin.getLogger().warning("Cannot save boss enchant defaults: "+error.getMessage());}
        var entries=config.getConfigurationSection("equipment.entries");
        if(entries!=null)for(var id:entries.getKeys(false)){
            var entry=entries.getConfigurationSection(id);if(entry==null)continue;
            var material=Material.matchMaterial(entry.getString("material",""));var levels=entry.getConfigurationSection("enchantments");
            if(material==null||levels==null)continue;
            var allowed=legacy.computeIfAbsent(material,m->new HashMap<>());
            for(var name:levels.getKeys(false)){
                var enchant=Registry.ENCHANTMENT.get(NamespacedKey.minecraft(name));var range=levels.getConfigurationSection(name);
                if(enchant==null||range==null)continue;
                int low=Math.clamp(range.getInt("min",1),1,32),high=Math.clamp(range.getInt("max",low),low,32);
                allowed.merge(enchant,new int[]{low,high},(a,b)->new int[]{Math.min(a[0],b[0]),Math.max(a[1],b[1])});
            }
        }
    }
    public boolean anvilEnabled(){return config.getBoolean("mechanics.anvil-transfer",true);}
    public double protectionRate(){return finite("protection-extra-point-reduction",.04,.25);}
    public double depthSpeed(){return finite("depth-strider-extra-level-speed",.10,.5);}
    public boolean swiftOverflow(){return config.getBoolean("mechanics.swift-sneak-overflow",true);}
    private double finite(String path,double fallback,double max){double value=config.getDouble("mechanics."+path,fallback);return Double.isFinite(value)?Math.clamp(value,0,max):fallback;}
    public static Map<Enchantment,Integer> enchants(ItemStack item){
        if(item==null||!item.hasItemMeta())return Map.of();return enchants(item.getItemMeta());
    }
    private static Map<Enchantment,Integer> enchants(ItemMeta meta){return meta instanceof EnchantmentStorageMeta book?book.getStoredEnchants():meta.getEnchants();}
    /** Called at award time, after all actual native enchants have been written. */
    public void markGenerated(ItemMeta meta){write(meta,enchants(meta));}
    public void write(ItemMeta meta,Map<Enchantment,Integer> levels){
        var rows=new TreeMap<String,Integer>();
        for(var e:levels.entrySet())if(e.getValue()>e.getKey().getMaxLevel())rows.put(e.getKey().getKey().toString(),Math.clamp(e.getValue(),1,32));
        // An empty stamp is intentional: it prevents re-importing a derived item as legacy loot.
        meta.getPersistentDataContainer().set(entitlement,PersistentDataType.STRING,
            rows.entrySet().stream().map(e->e.getKey()+"="+e.getValue()).collect(java.util.stream.Collectors.joining(";")));
    }
    public Map<Enchantment,Integer> authorized(ItemStack item){
        if(item==null||!item.hasItemMeta())return Map.of();var meta=item.getItemMeta();var actual=enchants(meta);
        String raw=meta.getPersistentDataContainer().get(entitlement,PersistentDataType.STRING);var result=new HashMap<Enchantment,Integer>();
        if(raw!=null){
            for(var row:raw.split(";")){
                int split=row.indexOf('=');if(split<0)continue;var key=NamespacedKey.fromString(row.substring(0,split));if(key==null)continue;
                var e=Registry.ENCHANTMENT.get(key);if(e==null)continue;
                try{int level=Math.min(actual.getOrDefault(e,0),Math.clamp(Integer.parseInt(row.substring(split+1)),0,32));
                    if(level>e.getMaxLevel())result.put(e,level);
                }catch(NumberFormatException ignored){}
            }
        }else{
            String kind=meta.getPersistentDataContainer().get(origin,PersistentDataType.STRING);
            if(kind==null||BossStructureLayout.Kind.fromId(kind)==null)return Map.of();
            var allowed=legacy.getOrDefault(item.getType(),Map.of());
            for(var e:actual.entrySet()){
                var range=allowed.get(e.getKey());int level=e.getValue();
                if(range!=null&&level>e.getKey().getMaxLevel()&&level>=range[0]&&level<=range[1])result.put(e.getKey(),level);
            }
        }
        return Map.copyOf(result);
    }
    public int level(ItemStack item,Enchantment enchant){return authorized(item).getOrDefault(enchant,0);}
}
