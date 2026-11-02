package com.example.voidscape.item;

import com.example.voidscape.VoidscapePlugin;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import java.io.File;
import java.util.*;
import java.util.random.RandomGenerator;

/** Reuses configured boss treasure profiles, awarding only one enchantment per Vault item. */
public final class VaultEnchantRewards {
    private record Enchant(Enchantment type,int min,int max,double weight) {}
    private record Profile(Material material,int weight,List<Enchant> enchants) {}
    private static final Set<Material> DIAMOND_ITEMS=EnumSet.of(Material.DIAMOND_HELMET,
        Material.DIAMOND_CHESTPLATE,Material.DIAMOND_LEGGINGS,Material.DIAMOND_BOOTS,
        Material.DIAMOND_SWORD,Material.DIAMOND_PICKAXE,Material.DIAMOND_AXE,Material.DIAMOND_SHOVEL,Material.BOW);
    private final VoidscapePlugin plugin;
    private final List<Profile> gear,books;

    public VaultEnchantRewards(VoidscapePlugin plugin) { this(plugin,entries(plugin)); }
    /** Explicit sections allow configuration validation without mutating the live server's file. */
    public VaultEnchantRewards(VoidscapePlugin plugin,ConfigurationSection entries) {
        this.plugin=plugin;
        List<Profile> gear=new ArrayList<>(),books=new ArrayList<>();
        if(entries!=null)for(String id:entries.getKeys(false)) {
            ConfigurationSection entry=entries.getConfigurationSection(id);
            if(entry==null)continue;
            Material material=Material.matchMaterial(entry.getString("material",""));
            boolean book=material==Material.ENCHANTED_BOOK;
            int weight=Math.clamp(entry.getInt("weight",1),0,10000);
            if(weight==0||material==null||!book&&!DIAMOND_ITEMS.contains(material))continue;
            ConfigurationSection levels=entry.getConfigurationSection("enchantments");
            List<Enchant> enchants=new ArrayList<>();
            if(levels!=null)for(String name:levels.getKeys(false)) {
                ConfigurationSection range=levels.getConfigurationSection(name);
                NamespacedKey key=NamespacedKey.fromString(name.contains(":")?name:"minecraft:"+name);
                Enchantment enchant=key==null?null:Registry.ENCHANTMENT.get(key);
                if(range==null||enchant==null)continue;
                double chance=range.getDouble("chance",1);
                if(!Double.isFinite(chance)||chance<=0)continue;
                int min=Math.clamp(range.getInt("min",1),1,32);
                int max=Math.clamp(range.getInt("max",min),min,32);
                if(!book) {
                    if(!enchant.canEnchantItem(new ItemStack(material)))continue;
                    if(Math.max(min,enchant.getMaxLevel()+1)>max)continue;
                }
                enchants.add(new Enchant(enchant,min,max,Math.min(1,chance)));
            }
            if(!enchants.isEmpty())(book?books:gear).add(new Profile(material,weight,List.copyOf(enchants)));
        }
        if(gear.isEmpty()||books.isEmpty())throw new IllegalArgumentException(
            "boss-treasure.yml equipment.entries needs diamond equipment with a compatible over-cap enchant and at least one enchanted book profile");
        this.gear=List.copyOf(gear);this.books=List.copyOf(books);
    }
    private static ConfigurationSection entries(VoidscapePlugin plugin) {
        File file=new File(plugin.getDataFolder(),"boss-treasure.yml");
        if(!file.isFile())plugin.saveResource("boss-treasure.yml",false);
        return YamlConfiguration.loadConfiguration(file).getConfigurationSection("equipment.entries");
    }
    public int gearProfiles() { return gear.size(); }
    public int bookProfiles() { return books.size(); }
    /** Compatibility callers retain the original configured, over-cap gear range. */
    public ItemStack gear(RandomGenerator random) { return item(gear,random,null); }
    /** Compatibility callers retain each book's complete configured range. */
    public ItemStack book(RandomGenerator random) { return item(books,random,null); }
    public ItemStack gear(int tier,RandomGenerator random) { return item(gear,random,tier); }
    public ItemStack book(int tier,RandomGenerator random) { return item(books,random,tier); }
    private ItemStack item(List<Profile> profiles,RandomGenerator random,Integer tier) {
        int total=profiles.stream().mapToInt(Profile::weight).sum(),ticket=random.nextInt(total);
        Profile selected=profiles.getLast();
        for(Profile profile:profiles)if((ticket-=profile.weight)<0){selected=profile;break;}
        double totalEnchant=selected.enchants.stream().mapToDouble(Enchant::weight).sum();
        double draw=random.nextDouble()*totalEnchant;
        Enchant enchant=selected.enchants.getLast();
        for(Enchant candidate:selected.enchants)if((draw-=candidate.weight)<0){enchant=candidate;break;}
        VaultProgression.LevelRange range=tier==null
            ?new VaultProgression.LevelRange(selected.material==Material.ENCHANTED_BOOK
                ?enchant.min:Math.max(enchant.min,enchant.type.getMaxLevel()+1),enchant.max)
            :VaultProgression.range(enchant.type,enchant.min,enchant.max,tier);
        int level=range.min()+random.nextInt(range.max()-range.min()+1);
        ItemStack item=new ItemStack(selected.material,1);
        var meta=item.getItemMeta();
        if(meta instanceof EnchantmentStorageMeta book)book.addStoredEnchant(enchant.type,level,true);
        else meta.addEnchant(enchant.type,level,true);
        plugin.bossEnchants().markGenerated(meta);
        item.setItemMeta(meta);
        return item;
    }
}
