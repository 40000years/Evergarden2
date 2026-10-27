package com.example.voidscape.world;

import com.example.voidscape.VoidscapePlugin;
import org.bukkit.*;
import org.bukkit.block.Chest;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.*;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;
import java.util.*;

/** Useful themed supplies and occasional equipment, with a durable marker for every chest. */
public final class ExplorationTreasure implements Listener {
    private final VoidscapePlugin plugin;
    private final ExplorationLayout layout;
    public ExplorationTreasure(VoidscapePlugin plugin,ExplorationLayout layout){
        this.plugin=plugin;this.layout=layout;
    }
    @EventHandler public void load(ChunkLoadEvent event){populate(event.getChunk());}
    public void populate(Chunk chunk){
        if(chunk.getWorld()!=plugin.world()||!layout.enabled())return;
        for(var site:layout.nearby(chunk.getX()*16+8,chunk.getZ()*16+8,16)){
            var locations=site.chestLocations();
            for(int index=0;index<locations.size();index++){
                var spot=locations.get(index);int[] p=site.transform(spot.x(),spot.z());
                if(Math.floorDiv(p[0],16)!=chunk.getX()||Math.floorDiv(p[1],16)!=chunk.getZ())continue;
                var oldMarker=plugin.key("exploration_treasure_v1");var persistent=chunk.getPersistentDataContainer();
                var marker=plugin.key("exploration_chest_"+p[0]+"_"+(site.floorY()+spot.y())+"_"+p[1]);
                if(persistent.has(marker,PersistentDataType.STRING)
                    ||(index==0&&persistent.has(oldMarker,PersistentDataType.STRING)))continue;
                // Record an attempt even when a chest was removed, obstructed or filled by a player.
                String identity=site.kind().id+":"+site.x()+":"+site.z()+":"+index;
                persistent.set(marker,PersistentDataType.STRING,identity);
                if(index==0)persistent.set(oldMarker,PersistentDataType.STRING,identity);
                int x=Math.floorMod(p[0],16),z=Math.floorMod(p[1],16),y=site.floorY()+spot.y();
                if(!(chunk.getBlock(x,y,z).getState() instanceof Chest chest)
                    ||chunk.getBlock(x,y-1,z).getType()!=Material.CHISELED_STONE_BRICKS
                    ||!chunk.getBlock(x,y+1,z).getType().isAir()||!chest.getBlockInventory().isEmpty())continue;
                var random=new Random(DungeonLayout.mix(chunk.getWorld().getSeed()^site.variant()^0x534d414c4c4c4f54L^index*0x9e3779b97f4a7c15L));
                var loot=roll(random,site.kind().biome(),site.kind().medium());var slots=new ArrayList<Integer>();
                for(int slot=0;slot<27;slot++)slots.add(slot);Collections.shuffle(slots,random);
                for(int i=0;i<loot.size();i++)chest.getBlockInventory().setItem(slots.get(i),loot.get(i));
                ElementalScrollTreasure.add(chest,"exploration");
            }
        }
    }
    private record Supply(Material material,int min,int max,int weight){}
    private static final List<Supply> FOOD=List.of(new Supply(Material.BREAD,4,8,4),new Supply(Material.BAKED_POTATO,5,10,3),
        new Supply(Material.COOKED_BEEF,3,6,2),new Supply(Material.COOKED_CHICKEN,4,7,2),new Supply(Material.GOLDEN_CARROT,2,4,1));
    private static final List<Supply> UTILITY=List.of(new Supply(Material.TORCH,8,18,5),new Supply(Material.COAL,3,7,4),
        new Supply(Material.IRON_INGOT,1,3,3),new Supply(Material.ARROW,6,12,3),new Supply(Material.STRING,3,8,2),
        new Supply(Material.LEATHER,2,4,2),new Supply(Material.COPPER_INGOT,2,6,2),new Supply(Material.GOLD_INGOT,1,2,1));
    private static ItemStack supply(Random random,List<Supply> pool){
        int roll=random.nextInt(pool.stream().mapToInt(Supply::weight).sum());
        for(var entry:pool)if((roll-=entry.weight())<0)return new ItemStack(entry.material(),entry.min()+random.nextInt(entry.max()-entry.min()+1));
        throw new IllegalStateException("Empty exploration supplies");
    }
    private double probability(String key,double fallback){
        double chance=plugin.getConfig().getDouble("structures.exploration."+key,fallback);
        return Double.isFinite(chance)?Math.clamp(chance,0,1):fallback;
    }
    public List<ItemStack> roll(Random random,BiomeTerrain.Kind biome,boolean medium){
        var loot=roll(random,biome);
        if(medium)for(var item:loot)if(!item.getItemMeta().getPersistentDataContainer().has(plugin.key("exploration_gear_v2"),PersistentDataType.STRING)
            &&item.getType()!=Material.GOLDEN_APPLE&&item.getType()!=Material.ENCHANTED_GOLDEN_APPLE&&item.getType()!=Material.FIREWORK_ROCKET)
            item.setAmount(Math.min(item.getMaxStackSize(),(int)Math.ceil(item.getAmount()*1.5)));
        return loot;
    }
    public List<ItemStack> roll(Random random,BiomeTerrain.Kind biome){
        var result=new ArrayList<ItemStack>();
        // Every chest has a meal, a usable building stack and general supplies.
        result.add(supply(random,FOOD));
        Material[] blocks=switch(biome){
            case GARDENS->new Material[]{Material.OAK_PLANKS,Material.CHERRY_PLANKS,Material.STONE_BRICKS,Material.CALCITE,Material.SCAFFOLDING};
            case GLACIER->new Material[]{Material.SPRUCE_PLANKS,Material.COBBLESTONE,Material.STONE_BRICKS,Material.WHITE_WOOL,Material.PACKED_ICE};
            case ANCIENT_FOREST->new Material[]{Material.DARK_OAK_PLANKS,Material.OAK_PLANKS,Material.STONE_BRICKS,Material.MOSS_BLOCK,Material.COARSE_DIRT};
            case LUNAR_CITY->new Material[]{Material.CALCITE,Material.QUARTZ_BRICKS,Material.ANDESITE,Material.STONE_BRICKS,Material.WHITE_WOOL};
        };
        result.add(new ItemStack(blocks[random.nextInt(blocks.length)],12+random.nextInt(21)));
        for(int i=0,count=2+random.nextInt(2);i<count;i++)result.add(supply(random,UTILITY));
        Material[] themed=switch(biome){
            case GARDENS->new Material[]{Material.CARROT,Material.WHEAT_SEEDS,Material.BONE_MEAL,Material.APPLE};
            case GLACIER->new Material[]{Material.COAL,Material.SNOWBALL,Material.LEATHER,Material.COOKED_SALMON};
            case ANCIENT_FOREST->new Material[]{Material.BONE_MEAL,Material.BROWN_MUSHROOM,Material.SWEET_BERRIES,Material.PUMPKIN_SEEDS};
            case LUNAR_CITY->new Material[]{Material.AMETHYST_SHARD,Material.PAPER,Material.LAPIS_LAZULI,Material.GLOW_INK_SAC};
        };
        if(random.nextDouble()<.55)result.add(new ItemStack(themed[random.nextInt(themed.length)],3+random.nextInt(6)));
        if(random.nextDouble()<probability("gear-chance",.65))result.add(gear(random,biome));
        double apple=random.nextDouble();
        if(apple<probability("enchanted-apple-chance",.0025))result.add(new ItemStack(Material.ENCHANTED_GOLDEN_APPLE));
        else if(apple<probability("enchanted-apple-chance",.0025)+probability("golden-apple-chance",.07))result.add(new ItemStack(Material.GOLDEN_APPLE,1+random.nextInt(2)));
        if(random.nextDouble()<.10){
            var rocket=new ItemStack(Material.FIREWORK_ROCKET,2+random.nextInt(5));
            var meta=(org.bukkit.inventory.meta.FireworkMeta)rocket.getItemMeta();meta.setPower(1);rocket.setItemMeta(meta);result.add(rocket);
        }
        return result;
    }
    private ItemStack gear(Random random,BiomeTerrain.Kind biome){
        boolean armor=random.nextInt(100)<35;
        String material;
        if(armor){
            int family=random.nextInt(100);String prefix=family<30?"LEATHER_":family<65?"CHAINMAIL_":family<80?"GOLDEN_":"IRON_";
            String[] parts={"HELMET","CHESTPLATE","LEGGINGS","BOOTS"};material=prefix+parts[random.nextInt(parts.length)];
        }else{
            String[] tools={"AXE","SWORD","PICKAXE","PICKAXE","SHOVEL","HOE"};
            material=(random.nextInt(100)<30?"STONE_":"IRON_")+tools[random.nextInt(tools.length)];
        }
        var item=new ItemStack(Material.valueOf(material));var meta=item.getItemMeta();
        boolean rare=random.nextDouble()<probability("rare-enchant-chance",.04);
        if(rare||random.nextDouble()<.85){
            Enchantment primary;
            if(armor){
                primary=Enchantment.PROTECTION;enchant(item,meta,primary,2+random.nextInt(3),false);
                if(material.endsWith("BOOTS")&&random.nextDouble()<.4)enchant(item,meta,Enchantment.FEATHER_FALLING,2+random.nextInt(3),false);
                if(material.endsWith("HELMET")&&random.nextDouble()<.3)enchant(item,meta,Enchantment.RESPIRATION,1+random.nextInt(3),false);
            }else if(material.endsWith("SWORD")){
                primary=Enchantment.SHARPNESS;enchant(item,meta,primary,2+random.nextInt(3),false);
                if(random.nextDouble()<.2)enchant(item,meta,Enchantment.LOOTING,1+random.nextInt(2),false);
            }else{
                primary=Enchantment.EFFICIENCY;enchant(item,meta,primary,2+random.nextInt(3),false);
                if(!material.endsWith("AXE")&&random.nextDouble()<.2)enchant(item,meta,Enchantment.FORTUNE,1+random.nextInt(2),false);
            }
            if(random.nextDouble()<.65)enchant(item,meta,Enchantment.UNBREAKING,1+random.nextInt(3),false);
            if(rare)enchant(item,meta,primary,primary.getMaxLevel()+1+random.nextInt(2),true);
        }
        if(meta instanceof LeatherArmorMeta leather)leather.setColor(Color.fromRGB(switch(biome){
            case GARDENS->0xc785a1;case GLACIER->0x91c9df;case ANCIENT_FOREST->0x69805a;case LUNAR_CITY->0xa59cc7;}));
        meta.getPersistentDataContainer().set(plugin.key("exploration_gear_v2"),PersistentDataType.STRING,biome.id);
        plugin.bossEnchants().markGenerated(meta);
        item.setItemMeta(meta);return item;
    }
    private static void enchant(ItemStack item,org.bukkit.inventory.meta.ItemMeta meta,Enchantment enchant,int level,boolean overCap){
        if(!enchant.canEnchantItem(item)||meta.getEnchants().keySet().stream().anyMatch(old->old!=enchant&&(old.conflictsWith(enchant)||enchant.conflictsWith(old))))return;
        meta.addEnchant(enchant,Math.clamp(level,1,overCap?enchant.getMaxLevel()+2:enchant.getMaxLevel()),overCap);
    }
}
