import com.example.voidscape.VoidscapePlugin;
import com.example.voidscape.world.BossLootTable;
import com.example.voidscape.world.BossStructureLayout;
import com.example.voidscape.world.BossTreasure;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.plugin.java.JavaPlugin;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Boss structure chests honour structure-level-caps from the server's boss-treasure.yml.
 * Without the caps (the previous release) it only records a fingerprint of every chest's item
 * types, amounts and enchant types: the runner compares both, so capping changes levels only.
 * Vault rewards must keep their full Tier V ranges from the same file.
 */
public class StructureEnchantCapChecks extends JavaPlugin {
    static final int CHESTS=3000;
    final List<String> lines=new ArrayList<>();
    int checks,failures;

    void check(boolean ok,String label){checks++;if(!ok)failures++;lines.add((ok?"PASS ":"FAIL ")+label);}

    @Override public void onEnable(){Bukkit.getScheduler().runTaskLater(this,()->{
        try{run();}catch(Throwable error){failures++;lines.add("ERROR "+error);for(var e:error.getStackTrace())lines.add("  at "+e);}
        boolean capped=hasCaps();
        lines.addFirst((failures==0?"PASS ":"FAIL ")+(capped?"candidate":"baseline")+" checks="+checks+" failures="+failures);
        try{Files.write(Path.of("structure-caps-result.txt"),lines);}catch(Exception ignored){}
        Bukkit.shutdown();
    },20);}

    static boolean hasCaps(){try{BossLootTable.class.getDeclaredField("caps");return true;}catch(NoSuchFieldException absent){return false;}}

    static String describe(ItemStack item){
        var meta=item.getItemMeta();
        var enchants=meta instanceof EnchantmentStorageMeta book?book.getStoredEnchants():meta.getEnchants();
        var types=new TreeSet<String>();enchants.keySet().forEach(e->types.add(e.getKey().getKey()));
        return item.getType()+"x"+item.getAmount()+types;
    }
    static Map<Enchantment,Integer> enchants(ItemStack item){
        var meta=item.getItemMeta();return meta instanceof EnchantmentStorageMeta book?book.getStoredEnchants():meta.getEnchants();
    }

    void run()throws Exception{
        var garden=(VoidscapePlugin)Bukkit.getPluginManager().getPlugin("Evergarden");
        Object whale=null;
        for(var listener:HandlerList.getRegisteredListeners(garden))
            if(listener.getListener().getClass().getSimpleName().equals("WhaleTreasure"))whale=listener.getListener();
        if(whale==null)throw new IllegalStateException("WhaleTreasure listener not registered");
        var field=whale.getClass().getDeclaredField("bossTreasure");field.setAccessible(true);
        BossLootTable loot=((BossTreasure)field.get(whale)).loot();
        boolean capped=hasCaps();

        var file=YamlConfiguration.loadConfiguration(garden.getDataFolder().toPath().resolve("boss-treasure.yml").toFile());
        // Enchants that are some profile's first (main) enchant; every other type may only reach vanilla + others.
        var mains=new HashSet<String>();var entries=file.getConfigurationSection("equipment.entries");
        for(var id:entries.getKeys(false)){
            var section=entries.getConfigurationSection(id+".enchantments");
            if(section!=null&&!section.getKeys(false).isEmpty())mains.add(section.getKeys(false).iterator().next());
        }

        List<String> fingerprint=new ArrayList<>();
        for(var kind:BossStructureLayout.Kind.values()){
            Map<Enchantment,Integer> highest=new HashMap<>();int items=0,overVanilla=0,breaches=0;
            int main=file.getInt("structure-level-caps."+kind.id+".main",-1),others=file.getInt("structure-level-caps."+kind.id+".others",-1);
            for(int seed=0;seed<CHESTS;seed++){
                var chest=loot.roll(new Random(seed*1_000_003L+kind.ordinal()),kind);
                fingerprint.add(kind.id+" "+seed+" "+chest.stream().map(StructureEnchantCapChecks::describe).toList());
                for(var item:chest){
                    if(!item.hasItemMeta())continue;
                    var levels=enchants(item);if(levels.isEmpty())continue;items++;
                    boolean over=false;
                    for(var e:levels.entrySet()){
                        highest.merge(e.getKey(),e.getValue(),Math::max);
                        int vanilla=e.getKey().getMaxLevel();over|=e.getValue()>vanilla;
                        if(!capped)continue;
                        boolean isMain=mains.contains(e.getKey().getKey().getKey());
                        int ceiling=vanilla+(isMain?Math.max(main,others):others);
                        if(e.getValue()>ceiling){breaches++;if(breaches<=5)lines.add("  "+kind.id+" seed "+seed+" "+item.getType()+" "+e.getKey().getKey().getKey()+" "+e.getValue()+" > "+ceiling);}
                    }
                    if(over)overVanilla++;
                }
            }
            String summary=kind.id+": "+items+" enchanted items, "+overVanilla+" above vanilla; highest "+new TreeMap<>(highest.entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(e->e.getKey().getKey().getKey(),Map.Entry::getValue)));
            lines.add("  "+summary);
            if(!capped)continue;
            check(main>=0&&others>=0,kind.id+" caps are present in the server file (main="+main+", others="+others+")");
            check(breaches==0,kind.id+" no enchant exceeds vanilla + cap ("+breaches+" breaches)");
            // The ceiling is actually reached, so chests are not under-generated.
            check(highest.getOrDefault(Enchantment.SHARPNESS,0)==Math.min(8,5+main),kind.id+" Sharpness tops out at "+Math.min(8,5+main));
            check(highest.getOrDefault(Enchantment.PROTECTION,0)==Math.min(8,4+main),kind.id+" Protection tops out at "+Math.min(8,4+main));
            check(highest.getOrDefault(Enchantment.POWER,0)==Math.min(8,5+main),kind.id+" Power tops out at "+Math.min(8,5+main));
            check(highest.getOrDefault(Enchantment.LOOTING,0)==Math.min(5,3+others),kind.id+" Looting tops out at "+Math.min(5,3+others));
        }
        Files.write(Path.of("structure-caps-fingerprint-"+(capped?"candidate":"baseline")+".txt"),fingerprint);
        if(!capped)return;

        // Vault Tier V still awards the configured top level from the very same equipment.entries.
        var rng=new java.util.SplittableRandom(7);int gearTop=0,bookTop=0;
        for(int i=0;i<3000;i++){
            for(int v:enchants(garden.relics().createVaultDiamondGear(5,rng)).values())gearTop=Math.max(gearTop,v);
            for(int v:enchants(garden.relics().createVaultEnchantBook(5,rng)).values())bookTop=Math.max(bookTop,v);
        }
        check(gearTop==8,"Vault Tier V equipment still reaches level VIII ("+gearTop+")");
        check(bookTop==8,"Vault Tier V books still reach level VIII ("+bookTop+")");
        // The upgrade only adds a section: every original pool survives the rewrite.
        check(entries.getKeys(false).size()==29,"29 equipment profiles kept ("+entries.getKeys(false).size()+")");
        check(file.getConfigurationSection("resources.entries").getKeys(false).size()==12,"12 resource entries kept");
        check(file.getConfigurationSection("rare.entries").getKeys(false).size()==9,"9 rare entries kept");
        check(file.getInt("equipment.rolls.max")==2&&file.getInt("resources.rolls.max")==4&&file.getDouble("rare.chance")==.15,"roll settings kept");
        check(file.getBoolean("mechanics.anvil-transfer",false),"mechanics kept");
        check(file.getInt("equipment.entries.diamond-sword.enchantments.sharpness.max")==8,"sword profile still lists Sharpness up to VIII");
    }
}
