package com.example.voidscape.world;

import com.example.voidscape.VoidscapePlugin;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import java.io.File;
import java.util.*;

/** End City-inspired, configurable native equipment with explicit over-cap enchantments. */
public final class BossLootTable {
    private record Enchant(Enchantment type,int min,int max,double chance){}
    private record Entry(Material material,int weight,int min,int max,List<Enchant> enchants){}
    private final VoidscapePlugin plugin;
    private final YamlConfiguration config;
    private final List<Entry> gear,resources,rare;
    public BossLootTable(VoidscapePlugin plugin){
        this.plugin=plugin;var file=new File(plugin.getDataFolder(),"boss-treasure.yml");
        if(!file.isFile())plugin.saveResource("boss-treasure.yml",false);
        config=YamlConfiguration.loadConfiguration(file);
        gear=read("equipment.entries",true);resources=read("resources.entries",false);rare=read("rare.entries",false);
        if(gear.isEmpty())throw new IllegalArgumentException("boss-treasure.yml needs at least one equipment entry");
    }
    private List<Entry> read(String path,boolean enchanted){
        var section=config.getConfigurationSection(path);var result=new ArrayList<Entry>();if(section==null)return List.of();
        for(var id:section.getKeys(false)){
            var entry=section.getConfigurationSection(id);if(entry==null)continue;
            var material=Material.matchMaterial(entry.getString("material",""));int weight=Math.clamp(entry.getInt("weight",1),0,10000);
            if(material==null||material.isAir()||!material.isItem()||weight==0)continue;
            int min=Math.clamp(entry.getInt("min",1),1,material.getMaxStackSize()),max=Math.clamp(entry.getInt("max",min),min,material.getMaxStackSize());
            var enchants=new ArrayList<Enchant>();var levels=entry.getConfigurationSection("enchantments");
            if(enchanted&&levels!=null)for(var enchantId:levels.getKeys(false)){
                var e=Registry.ENCHANTMENT.get(NamespacedKey.minecraft(enchantId));var values=levels.getConfigurationSection(enchantId);
                if(e==null||values==null){plugin.getLogger().warning("Unknown boss treasure enchantment: "+enchantId);continue;}
                int low=Math.clamp(values.getInt("min",1),1,32),high=Math.clamp(values.getInt("max",low),low,32);
                enchants.add(new Enchant(e,low,high,probability(values.getDouble("chance",1),1)));
            }
            result.add(new Entry(material,weight,min,max,List.copyOf(enchants)));
        }
        return List.copyOf(result);
    }
    private static double probability(double value,double fallback){return Double.isFinite(value)?Math.clamp(value,0,1):fallback;}
    private int rolls(String path,int fallback,int cap,Random random){
        int min=Math.clamp(config.getInt(path+".min",fallback),0,cap),max=Math.clamp(config.getInt(path+".max",min),min,cap);
        return min+random.nextInt(max-min+1);
    }
    public List<ItemStack> roll(Random random,BossStructureLayout.Kind kind){
        var result=new ArrayList<ItemStack>();
        int count=Math.max(1,rolls("equipment.rolls",1,3,random));
        for(int i=0;i<count;i++)result.add(item(pick(gear,random),random,kind));
        if(!resources.isEmpty())for(int i=0,n=rolls("resources.rolls",3,6,random);i<n;i++)result.add(item(pick(resources,random),random,kind));
        if(!rare.isEmpty()&&random.nextDouble()<probability(config.getDouble("rare.chance",.15),.15))result.add(item(pick(rare,random),random,kind));
        return result;
    }
    private static Entry pick(List<Entry> entries,Random random){
        int total=entries.stream().mapToInt(Entry::weight).sum(),roll=random.nextInt(total);
        for(var entry:entries){roll-=entry.weight;if(roll<0)return entry;}
        throw new IllegalStateException("Empty weighted loot pool");
    }
    private ItemStack item(Entry entry,Random random,BossStructureLayout.Kind kind){
        var item=new ItemStack(entry.material,entry.min+random.nextInt(entry.max-entry.min+1));var meta=item.getItemMeta();
        var chosen=new ArrayList<Enchantment>();
        for(var enchant:entry.enchants){
            if(random.nextDouble()>=enchant.chance)continue;
            if(!(meta instanceof EnchantmentStorageMeta)&&!enchant.type.canEnchantItem(item))continue;
            if(chosen.stream().anyMatch(old->old.conflictsWith(enchant.type)||enchant.type.conflictsWith(old)))continue;
            int level=enchant.min+random.nextInt(enchant.max-enchant.min+1);
            // Explicitly ignore the vanilla level ceiling, while keeping compatibility checks above.
            if(meta instanceof EnchantmentStorageMeta book)book.addStoredEnchant(enchant.type,level,true);
            else meta.addEnchant(enchant.type,level,true);
            chosen.add(enchant.type);
        }
        meta.getPersistentDataContainer().set(plugin.key("boss_treasure_v3"),org.bukkit.persistence.PersistentDataType.STRING,kind.id);
        plugin.bossEnchants().markGenerated(meta);
        item.setItemMeta(meta);return item;
    }
    public int equipmentProfiles(){return gear.size();}
}
