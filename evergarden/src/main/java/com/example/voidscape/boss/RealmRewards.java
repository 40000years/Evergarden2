package com.example.voidscape.boss;

import com.example.voidscape.VoidscapePlugin;
import com.example.voidscape.item.AdventurerWeaponService.AdventurerWeapon;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import java.io.File;
import java.util.*;
import java.util.random.RandomGenerator;

/**
 * What each contributor takes home from a realm victory, rolled separately per player.
 * Nether bosses: a rare (Adventurer) weapon at 50% and four Netherite Ingots at 25%.
 * End bosses: at 50%, a real enchanted book whose levels sit two above the highest level
 * boss treasure awards (Protection 8 there makes Protection 10 here). It carries a boss
 * enchant entitlement, so the anvil applies it like any boss treasure.
 */
public final class RealmRewards {
    /** One equipment family from boss-treasure.yml: the enchantments a single item can carry. */
    record Family(Material material,int weight,List<Enchantment> enchants) {}
    // Over the anvil's 40-level ceiling a book would be useless; keep a fresh item's first combine affordable.
    static final int BOOK_ANVIL_BUDGET=30;

    private RealmRewards(){}

    static boolean nether(RealmBosses.Kind kind){return kind==RealmBosses.Kind.CINDER_COLOSSUS||kind==RealmBosses.Kind.SOUL_HOUND;}
    private static double chance(VoidscapePlugin plugin,String key,double fallback){
        double value=plugin.getConfig().getDouble("dimensional-sanctuaries.rewards."+key,fallback);
        return Double.isFinite(value)?Math.clamp(value,0,1):fallback;
    }

    public static List<ItemStack> roll(VoidscapePlugin plugin,RealmBosses.Kind kind,RandomGenerator random){
        List<ItemStack> spoils=new ArrayList<>();
        if(nether(kind)){
            var weapons=plugin.adventurerWeapons();
            if(random.nextDouble()<chance(plugin,"rare-weapon-chance",.5)&&weapons!=null){
                List<AdventurerWeapon> pool=AdventurerWeapon.enabledWeapons();
                spoils.add(weapons.create(pool.get(random.nextInt(pool.size()))));
            }
            if(random.nextDouble()<chance(plugin,"netherite-chance",.25))
                spoils.add(new ItemStack(Material.NETHERITE_INGOT,plugin.integer("dimensional-sanctuaries.rewards.netherite-amount",4,1,64)));
        }else if(random.nextDouble()<chance(plugin,"enchant-book-chance",.5)){
            spoils.add(book(plugin,kind,random));
        }
        return spoils;
    }

    /** Hands a contributor their spoils; anything that does not fit drops at their feet. */
    public static void grant(VoidscapePlugin plugin,Player player,RealmBosses.Kind kind,RandomGenerator random){
        List<ItemStack> spoils=roll(plugin,kind,random);
        if(spoils.isEmpty())return;
        var plain=PlainTextComponentSerializer.plainText();
        for(ItemStack item:spoils){
            for(ItemStack rest:player.getInventory().addItem(item.clone()).values())player.getWorld().dropItemNaturally(player.getLocation(),rest);
            String name=item.getItemMeta()!=null&&item.getItemMeta().hasDisplayName()?plain.serialize(item.getItemMeta().displayName()):describe(item);
            plugin.message(player,"§d✦ ของรางวัลพิเศษจาก "+kind.title+": §f"+name+(item.getAmount()>1?" ×"+item.getAmount():""));
        }
        player.playSound(player.getLocation(),Sound.ENTITY_PLAYER_LEVELUP,.8f,1.3f);
    }
    private static String describe(ItemStack item){
        if(item.getItemMeta() instanceof EnchantmentStorageMeta book){
            List<String> parts=new ArrayList<>();
            book.getStoredEnchants().forEach((e,l)->parts.add(e.getKey().getKey()+" "+l));
            return "สมุดเวทเกินขีดจำกัด ("+String.join(", ",parts)+")";
        }
        return item.getType()==Material.NETHERITE_INGOT?"Netherite Ingot":item.getType().getKey().getKey();
    }

    static ConfigurationSection treasure(VoidscapePlugin plugin){
        File file=new File(plugin.getDataFolder(),"boss-treasure.yml");
        if(!file.isFile())plugin.saveResource("boss-treasure.yml",false);
        return YamlConfiguration.loadConfiguration(file).getConfigurationSection("equipment.entries");
    }
    private static Enchantment enchant(String name){
        NamespacedKey key=NamespacedKey.fromString(name.contains(":")?name:"minecraft:"+name);
        return key==null?null:Registry.ENCHANTMENT.get(key);
    }
    /** The highest level boss treasure can award for each enchantment (gear and book profiles alike). */
    static Map<Enchantment,Integer> ceilings(ConfigurationSection entries){
        Map<Enchantment,Integer> best=new HashMap<>();
        if(entries!=null)for(String id:entries.getKeys(false)){
            ConfigurationSection levels=entries.getConfigurationSection(id+".enchantments");
            if(levels!=null)for(String name:levels.getKeys(false)){
                Enchantment e=enchant(name);ConfigurationSection range=levels.getConfigurationSection(name);
                if(e==null||range==null)continue;
                int min=Math.clamp(range.getInt("min",1),1,32);
                best.merge(e,Math.clamp(range.getInt("max",min),min,32),Math::max);
            }
        }
        return best;
    }
    /** Single-level enchants gain nothing from extra levels; Quick Charge past V stops a crossbow charging. */
    static boolean upgradable(Enchantment enchant){return enchant.getMaxLevel()>1&&!enchant.equals(Enchantment.QUICK_CHARGE);}
    static List<Family> families(ConfigurationSection entries){
        List<Family> families=new ArrayList<>();
        if(entries!=null)for(String id:entries.getKeys(false)){
            ConfigurationSection entry=entries.getConfigurationSection(id);if(entry==null)continue;
            Material material=Material.matchMaterial(entry.getString("material",""));
            int weight=Math.clamp(entry.getInt("weight",1),0,10000);
            ConfigurationSection levels=entry.getConfigurationSection("enchantments");
            if(material==null||material==Material.ENCHANTED_BOOK||weight==0||levels==null)continue;
            List<Enchantment> enchants=new ArrayList<>();
            for(String name:levels.getKeys(false)){Enchantment e=enchant(name);if(e!=null&&upgradable(e)&&!enchants.contains(e))enchants.add(e);}
            if(!enchants.isEmpty())families.add(new Family(material,weight,List.copyOf(enchants)));
        }
        return families;
    }
    static int bonusLevels(VoidscapePlugin plugin){return plugin.integer("dimensional-sanctuaries.rewards.enchant-book-bonus-levels",2,1,10);}
    /** XP the anvil charges to put this book's enchantments on an item that has none of them. */
    static int bookCost(Map<Enchantment,Integer> levels){
        int cost=0;for(var entry:levels.entrySet())cost+=Math.max(1,entry.getKey().getAnvilCost()/2)*entry.getValue();return cost;
    }

    /** One to three compatible enchantments of one equipment family, each two levels above boss treasure. */
    public static ItemStack book(VoidscapePlugin plugin,RealmBosses.Kind kind,RandomGenerator random){return book(plugin,kind,treasure(plugin),random);}
    static ItemStack book(VoidscapePlugin plugin,RealmBosses.Kind kind,ConfigurationSection entries,RandomGenerator random){
        List<Family> families=families(entries);Map<Enchantment,Integer> ceilings=ceilings(entries);
        if(families.isEmpty())throw new IllegalStateException("boss-treasure.yml has no upgradable equipment enchantments");
        int total=families.stream().mapToInt(Family::weight).sum(),ticket=random.nextInt(total);
        Family family=families.getLast();
        for(Family candidate:families)if((ticket-=candidate.weight())<0){family=candidate;break;}
        List<Enchantment> order=new ArrayList<>(family.enchants());
        for(int i=order.size()-1;i>0;i--)Collections.swap(order,i,random.nextInt(i+1));
        double draw=random.nextDouble();int wanted=draw<.5?1:draw<.85?2:3;
        int bonus=bonusLevels(plugin);
        Map<Enchantment,Integer> levels=new LinkedHashMap<>();
        for(Enchantment e:order){
            if(levels.size()>=wanted)break;
            if(levels.keySet().stream().anyMatch(e::conflictsWith))continue;
            int level=Math.min(32,ceilings.getOrDefault(e,e.getMaxLevel())+bonus);
            Map<Enchantment,Integer> next=new LinkedHashMap<>(levels);next.put(e,level);
            if(!levels.isEmpty()&&bookCost(next)>BOOK_ANVIL_BUDGET)continue;
            levels=next;
        }
        ItemStack item=new ItemStack(Material.ENCHANTED_BOOK);
        var meta=(EnchantmentStorageMeta)item.getItemMeta();
        levels.forEach((e,l)->meta.addStoredEnchant(e,l,true));
        meta.lore(List.of(
            Component.text("ตำราเกินขีดจำกัด · +"+bonus+" เลเวลเหนือสมบัติบอส",NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.ITALIC,false),
            Component.text("จาก "+kind.title+" · ตีกับทั่งได้",NamedTextColor.GRAY).decoration(TextDecoration.ITALIC,false)));
        plugin.bossEnchants().markGenerated(meta);
        item.setItemMeta(meta);
        return item;
    }
}
