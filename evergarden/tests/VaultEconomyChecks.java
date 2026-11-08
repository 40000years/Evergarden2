import com.example.advancemagic.AdvanceMagicPlugin;
import com.example.voidscape.VoidscapePlugin;
import com.example.voidscape.enchant.BossEnchantService;
import com.example.voidscape.enchant.LimitBreakType;
import com.example.voidscape.item.RemovedRelics;
import com.example.voidscape.item.VaultEnchantRewards;
import com.example.voidscape.item.VaultLootTable;
import com.example.voidscape.item.VaultLootTable.PrimaryReward;
import com.example.voidscape.item.VaultProgression;
import com.example.voidscape.world.DungeonLayout;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** A real Paper contract test: native registry compatibility, factories and survival transactions. */
public final class VaultEconomyChecks extends JavaPlugin {
    private static final int[][] WEIGHTS={
        {},{6500,3000,300,180,20,0,0,0,0,0,0},
        {6125,3100,300,200,120,100,0,50,0,5,0},
        {5660,3000,350,300,200,300,30,100,50,10,0},
        {3380,2200,500,400,500,1700,400,400,200,20,300},
        {2670,1800,500,400,600,2200,600,500,200,30,500}};
    private static final int[] DOUBLE={0,0,1000,1500,2000,2500};
    private static final Set<Material> GEAR=Set.of(Material.DIAMOND_HELMET,Material.DIAMOND_CHESTPLATE,
        Material.DIAMOND_LEGGINGS,Material.DIAMOND_BOOTS,Material.DIAMOND_SWORD,Material.DIAMOND_PICKAXE,
        Material.DIAMOND_AXE,Material.DIAMOND_SHOVEL,Material.BOW);
    private VoidscapePlugin garden;
    private AdvanceMagicPlugin magic;
    private int passed;
    private final StringBuilder audit=new StringBuilder();
    private final List<ItemStack> gearSamples=new ArrayList<>();
    private final Map<Enchantment,ItemStack> bookSamples=new LinkedHashMap<>();
    private record TieredBook(int tier,ItemStack item){}
    private final List<TieredBook> tierBookSamples=new ArrayList<>();
    private List<Award> gearPool,bookPool;
    private net.minecraft.server.level.ServerPlayer actor;
    private net.minecraft.server.level.ServerLevel level;

    @Override public void onEnable(){Bukkit.getScheduler().runTaskLater(this,()->{
        try{run();finish(null);}catch(Throwable error){finish(error);}
    },20);}
    private void check(boolean condition,String message){if(!condition)throw new AssertionError(message);passed++;}
    private void finish(Throwable error){
        if(error!=null)getLogger().log(java.util.logging.Level.SEVERE,"VAULT ECONOMY CHECK FAILED",error);
        String report=(error==null?"PASS ":"FAIL ")+passed+" checks; actual Paper Vault economy; "+
            (error==null?"exact primary odds, tier enchant quality, graded Limit Breaks, zero mana, two-item cap, native upgrades and GUI transactions":error.toString());
        try{Files.writeString(Path.of("vault-economy-result.txt"),report+"\n"+audit);}
        catch(Exception failure){throw new RuntimeException(failure);}
        getLogger().info(report);Bukkit.getScheduler().runTaskLater(this,Bukkit::shutdown,1);
    }
    private void run()throws Exception{
        garden=(VoidscapePlugin)Bukkit.getPluginManager().getPlugin("Evergarden");
        magic=(AdvanceMagicPlugin)Bukkit.getPluginManager().getPlugin("advance-magic");
        check(garden!=null&&garden.isEnabled(),"Evergarden enabled");
        check(magic!=null&&magic.isEnabled(),"Advance Magic enabled");
        byte[] configured=Files.readAllBytes(garden.getDataFolder().toPath().resolve("boss-treasure.yml"));
        exactTables();configuredPools();knownTierBands();configuredEndpoints();gearFactories();bookFactories();customConfiguration();gradedLimitBreaks();productionFactories();nativeAnvils();optionalIntegrations();
        check(Arrays.equals(configured,Files.readAllBytes(garden.getDataFolder().toPath().resolve("boss-treasure.yml"))),"factories and custom configuration tests leave configured pool file unchanged");
    }
    private static DungeonLayout.Site site(DungeonLayout.Kind kind,int tier){return new DungeonLayout.Site(kind,500,500,1L,tier);}
    private static boolean ordinary(PrimaryReward value){return value==PrimaryReward.DIAMOND_GEAR||value==PrimaryReward.ENCHANT_BOOK;}
    private static int start(int tier,int category){int result=0;for(int i=0;i<category;i++)result+=WEIGHTS[tier][i];return result;}
    private void exactTables(){
        check(PrimaryReward.values().length==11,"unified table contains both flying staff tiers and no mana category");
        check(PrimaryReward.FLYING_STAFF_TIER2.ordinal()==10,"two-seat flying staff appended without shifting existing reward ordinals");
        for(int tier=1;tier<=5;tier++){
            check(WEIGHTS[tier].length==PrimaryReward.values().length,"expected table has one weight per category T"+tier);
            check(Arrays.stream(WEIGHTS[tier]).sum()==10000,"expected primary table totals 10000 tickets T"+tier);
            int[] counts=new int[PrimaryReward.values().length];
            for(int ticket=0;ticket<10000;ticket++){
                int expected=0,edge=WEIGHTS[tier][0];
                while(ticket>=edge)edge+=WEIGHTS[tier][++expected];
                var actual=VaultLootTable.primaryReward(tier,ticket);
                if(actual.ordinal()!=expected)throw new AssertionError("T"+tier+" primary ticket "+ticket+" expected "+PrimaryReward.values()[expected]+" got "+actual);
                counts[actual.ordinal()]++;
                var bonus=VaultLootTable.bonusReward(ticket);
                if(bonus!=(ticket<6500?PrimaryReward.DIAMOND_GEAR:PrimaryReward.ENCHANT_BOOK))
                    throw new AssertionError("bonus ticket "+ticket+" creates a rare item or wrong 65/35 split");
            }
            check(Arrays.equals(counts,WEIGHTS[tier]),"all 10000 primary tickets counted exactly T"+tier);
            for(var category:PrimaryReward.values()){
                check(VaultLootTable.chanceBps(tier,category)==WEIGHTS[tier][category.ordinal()],"published chance equals exact ticket count T"+tier+" "+category);
                check(VaultLootTable.canDouble(category)==ordinary(category),"only diamond gear and books allow a second reward "+category);
            }
            check(VaultLootTable.chanceBps(tier,PrimaryReward.FLYING_STAFF)==new int[]{0,0,0,50,200,200}[tier],"ordinary flying staff tier progression T"+tier);
            check(VaultLootTable.chanceBps(tier,PrimaryReward.FLYING_STAFF_TIER2)==new int[]{0,0,0,0,300,500}[tier],"two-seat flying staff limited to T4/T5 at 3.00/5.00 percent T"+tier);
            check(VaultLootTable.chanceBps(tier,PrimaryReward.SCROLL_ETERNITY)==new int[]{0,0,5,10,20,30}[tier],"Eternity remains a chase reward across all tiers T"+tier);
            check(VaultLootTable.doubleChanceBps(tier)==DOUBLE[tier],"exact double chance T"+tier);
            audit.append("T").append(tier).append(" primary counts ").append(Arrays.toString(counts)).append(" double=").append(DOUBLE[tier]).append("/10000\n");
        }
        for(int ticket:new int[]{-1,10000,Integer.MIN_VALUE,Integer.MAX_VALUE}){
            try{VaultLootTable.primaryReward(3,ticket);throw new AssertionError("invalid primary ticket accepted");}catch(IllegalArgumentException expected){passed++;}
            try{VaultLootTable.bonusReward(ticket);throw new AssertionError("invalid bonus ticket accepted");}catch(IllegalArgumentException expected){passed++;}
        }
        for(int tier:new int[]{2,3}){
            int first=start(tier,PrimaryReward.SCROLL_ETERNITY.ordinal());
            check(VaultLootTable.primaryReward(tier,first-1)!=PrimaryReward.SCROLL_ETERNITY&&VaultLootTable.primaryReward(tier,first)==PrimaryReward.SCROLL_ETERNITY&&VaultLootTable.primaryReward(tier,9999)==PrimaryReward.SCROLL_ETERNITY,"lower-tier Eternity category has exact reduced final ticket band T"+tier);
        }
    }
    private record Award(Material material,Enchantment enchant,int low,int high){
        boolean accepts(ItemStack item){int n=BossEnchantService.enchants(item).getOrDefault(enchant,0);return item.getType()==material&&n>=low&&n<=high;}
        boolean accepts(ItemStack item,int tier){int n=BossEnchantService.enchants(item).getOrDefault(enchant,0);var band=expectedBand(enchant,low,high,tier);return item.getType()==material&&n>=band.low&&n<=band.high;}
        String pair(){return material+":"+enchant.getKey();}
    }
    private void configuredPools(){
        var yaml=YamlConfiguration.loadConfiguration(new java.io.File(garden.getDataFolder(),"boss-treasure.yml"));
        var entries=yaml.getConfigurationSection("equipment.entries");
        check(entries!=null,"configured boss treasure pool available");
        gearPool=readPool(entries,false);bookPool=readPool(entries,true);
        check(gearPool.stream().map(Award::material).collect(java.util.stream.Collectors.toSet()).equals(GEAR),"configured gear covers requested nine materials");
        check(bookPool.stream().map(Award::enchant).collect(java.util.stream.Collectors.toSet()).equals(Set.of(
            Enchantment.SHARPNESS,Enchantment.PROTECTION,Enchantment.FORTUNE,Enchantment.EFFICIENCY,Enchantment.POWER,Enchantment.MENDING)),"default configured six-book pool including Mending I");
    }
    /** Read the fixture as an independent contract, using Paper's own compatibility registry. */
    private List<Award> readPool(ConfigurationSection entries,boolean books){
        var result=new ArrayList<Award>();
        for(String id:entries.getKeys(false)){
            var entry=entries.getConfigurationSection(id);if(entry==null||entry.getDouble("weight",1)<=0)continue;
            var material=Material.matchMaterial(entry.getString("material",""));
            if(books?material!=Material.ENCHANTED_BOOK:!GEAR.contains(material))continue;
            var enchants=entry.getConfigurationSection("enchantments");if(enchants==null)continue;
            for(String key:enchants.getKeys(false)){
                var enchant=org.bukkit.Registry.ENCHANTMENT.get(NamespacedKey.minecraft(key));var range=enchants.getConfigurationSection(key);
                if(enchant==null||range==null||range.getDouble("chance",1)<=0)continue;
                if(!books&&!enchant.canEnchantItem(new ItemStack(material)))continue;
                int low=range.getInt("min",1),high=range.getInt("max",low);
                if(!books&&Math.max(low,enchant.getMaxLevel()+1)>high)continue;
                if(high>=low)result.add(new Award(material,enchant,low,high));
            }
        }
        return result;
    }
    private void oneEnchant(ItemStack item,boolean gear,String label){
        check(item!=null&&item.getAmount()==1,label+" amount one");
        var levels=BossEnchantService.enchants(item);
        check(levels.size()==1,label+" exactly one real vanilla enchant");
        var entry=levels.entrySet().iterator().next();
        if(gear){
            check(GEAR.contains(item.getType()),label+" requested diamond/bow material");
            check(entry.getKey().canEnchantItem(new ItemStack(item.getType())),label+" native registry accepts enchant on material");
        }else check(item.getType()==Material.ENCHANTED_BOOK&&item.getItemMeta() instanceof EnchantmentStorageMeta,label+" actual stored enchant book");
        if(entry.getValue()>entry.getKey().getMaxLevel()){
            check(garden.bossEnchants().authorized(item).equals(levels),label+" entitlement limited to actual one enchant");
            check(garden.bossEnchants().authorized(ItemStack.deserializeBytes(item.serializeAsBytes())).equals(levels),label+" entitlement survives serialization");
        }else check(garden.bossEnchants().authorized(item).isEmpty(),label+" ordinary level receives no overcap entitlement");
        check(!RemovedRelics.matches(item),label+" no retired relic");
    }
    private record Band(int low,int high){}
    private static Band expectedBand(Enchantment enchant,int configuredMin,int configuredMax,int tier){
        int first=Math.max(1,Math.min(32,configuredMin)),last=Math.max(first,Math.min(32,configuredMax));
        if(last<=enchant.getMaxLevel())return new Band(first,last);
        int base=Math.min(first,enchant.getMaxLevel());
        int quarter=base+(int)Math.ceil((last-base)*.25),middle=base+(int)Math.ceil((last-base)*.5);
        return switch(Math.max(1,Math.min(5,tier))){case 1->new Band(base,quarter);case 2->new Band(quarter,quarter);case 3->new Band(quarter,middle);case 4->new Band(middle,last);default->new Band(last,last);};
    }
    private void fixedBands(Enchantment enchant,int min,int max,int[][] expected){
        for(int tier=1;tier<=5;tier++){
            var actual=VaultProgression.range(enchant,min,max,tier);
            check(actual.min()==expected[tier-1][0]&&actual.max()==expected[tier-1][1],"explicit tier range "+enchant.getKey()+" configured "+min+".."+max+" T"+tier);
            check(expectedBand(enchant,min,max,tier).equals(new Band(expected[tier-1][0],expected[tier-1][1])),"independent range oracle agrees with fixed example");
        }
    }
    private void knownTierBands(){
        fixedBands(Enchantment.SHARPNESS,6,8,new int[][]{{5,6},{6,6},{6,7},{7,8},{8,8}});
        fixedBands(Enchantment.PROTECTION,6,8,new int[][]{{4,5},{5,5},{5,6},{6,8},{8,8}});
        fixedBands(Enchantment.FORTUNE,4,5,new int[][]{{3,4},{4,4},{4,4},{4,5},{5,5}});
        fixedBands(Enchantment.FEATHER_FALLING,5,7,new int[][]{{4,5},{5,5},{5,6},{6,7},{7,7}});
        fixedBands(Enchantment.FIRE_ASPECT,2,3,new int[][]{{2,3},{3,3},{3,3},{3,3},{3,3}});
        fixedBands(Enchantment.SHARPNESS,2,8,new int[][]{{2,4},{4,4},{4,5},{5,8},{8,8}});
        fixedBands(Enchantment.SHARPNESS,2,5,new int[][]{{2,5},{2,5},{2,5},{2,5},{2,5}});
        fixedBands(Enchantment.MENDING,1,1,new int[][]{{1,1},{1,1},{1,1},{1,1},{1,1}});
        check(VaultProgression.range(Enchantment.SHARPNESS,6,8,-9).equals(VaultProgression.range(Enchantment.SHARPNESS,6,8,1)),"quality tiers below I clamp to I");
        check(VaultProgression.range(Enchantment.SHARPNESS,6,8,99).equals(VaultProgression.range(Enchantment.SHARPNESS,6,8,5)),"quality tiers above V clamp to V");
    }
    private static final class EndpointRandom extends Random{
        final boolean high;EndpointRandom(boolean high){this.high=high;}
        @Override public int nextInt(int bound){return high?bound-1:0;}
        @Override public double nextDouble(){return 0;}
    }
    private void configuredEndpoints(){
        for(boolean books:new boolean[]{false,true})for(var award:books?bookPool:gearPool){
            var fixture=new YamlConfiguration();
            fixture(fixture,"gear",books?Material.DIAMOND_SWORD:award.material,1,books?Map.of("sharpness",8):Map.of(award.enchant.getKey().getKey(),award.low));
            fixture(fixture,"book",Material.ENCHANTED_BOOK,1,books?Map.of(award.enchant.getKey().getKey(),award.low):Map.of("mending",1));
            fixture.set((books?"book":"gear")+".enchantments."+award.enchant.getKey().getKey()+".max",award.high);
            var helper=new VaultEnchantRewards(garden,fixture);
            int previousLow=0,previousHigh=0;
            for(int tier=1;tier<=5;tier++){
                var band=expectedBand(award.enchant,award.low,award.high,tier);
                for(boolean high:new boolean[]{false,true}){
                    var item=books?helper.book(tier,new EndpointRandom(high)):helper.gear(tier,new EndpointRandom(high));
                    if(books)tierBookSamples.add(new TieredBook(tier,item));
                    oneEnchant(item,!books,"exact configured endpoint");
                    check(BossEnchantService.enchants(item).equals(Map.of(award.enchant,high?band.high:band.low)),"factory reaches selected tier's exact "+(high?"maximum":"minimum")+" "+award.pair()+" T"+tier);
                }
                check(band.low>=previousLow&&band.high>=previousHigh,"configured min/max never decrease across tiers "+award.pair());
                previousLow=band.low;previousHigh=band.high;
            }
            for(boolean high:new boolean[]{false,true}){
                var item=books?helper.book(new EndpointRandom(high)):helper.gear(new EndpointRandom(high));
                int legacyLow=books?award.low:Math.max(award.low,award.enchant.getMaxLevel()+1);
                check(BossEnchantService.enchants(item).equals(Map.of(award.enchant,high?award.high:legacyLow)),"legacy no-tier helper preserves original configured endpoint "+award.pair());
            }
        }
    }
    private void gearFactories(){
        Set<String> found=new HashSet<>();var samples=new LinkedHashMap<String,ItemStack>();boolean featherSix=false;
        for(int tier=1;tier<=5;tier++)for(int seed=0;seed<1024;seed++){
            var item=garden.relics().createVaultDiamondGear(tier,new Random(0xA127L+seed*313L));
            oneEnchant(item,true,"configured gear T"+tier);
            final int selectedTier=tier;
            check(gearPool.stream().anyMatch(a->a.accepts(item,selectedTier)),"gear enchant/level comes from selected tier of configured compatible pool");
            var enchant=BossEnchantService.enchants(item).keySet().iterator().next();String pair=item.getType()+":"+enchant.getKey();found.add(pair);
            featherSix|=item.getEnchantmentLevel(Enchantment.FEATHER_FALLING)==6;
            var previous=samples.get(pair);
            if(previous==null||BossEnchantService.enchants(previous).get(enchant)<BossEnchantService.enchants(item).get(enchant))samples.put(pair,item);
            if(tier>1){
                var lower=garden.relics().createVaultDiamondGear(tier-1,new Random(0xA127L+seed*313L));var lowerEnchants=BossEnchantService.enchants(lower);
                check(lower.getType()==item.getType()&&lowerEnchants.keySet().equals(BossEnchantService.enchants(item).keySet()),"paired seed preserves gear/enchant selection across tier quality");
                check(item.getEnchantmentLevel(enchant)>=lowerEnchants.get(enchant),"paired seed gear quality never decreases across tiers");
            }
        }
        check(found.equals(gearPool.stream().map(Award::pair).collect(java.util.stream.Collectors.toSet())),"all configured compatible gear/enchant pairs obtainable");
        gearSamples.addAll(samples.values());
        check(gearSamples.stream().anyMatch(i->i.getEnchantmentLevel(Enchantment.UNBREAKING)==5),"Unbreaking V attainable");
        check(featherSix,"Feather Falling VI attainable");
    }
    private void bookFactories(){
        for(int tier=1;tier<=5;tier++)for(int seed=0;seed<256;seed++){
            ItemStack book=garden.relics().createVaultEnchantBook(tier,new Random(0x47A11L+seed*313L));
            oneEnchant(book,false,"configured book T"+tier);
            final int selectedTier=tier;
            check(bookPool.stream().anyMatch(a->a.accepts(book,selectedTier)),"book enchant and level range match selected configured tier");
            var enchant=BossEnchantService.enchants(book).keySet().iterator().next();
            ItemStack previous=bookSamples.get(enchant);
            if(previous==null||BossEnchantService.enchants(previous).get(enchant)<BossEnchantService.enchants(book).get(enchant))bookSamples.put(enchant,book);
            if(tier>1){
                var lower=BossEnchantService.enchants(garden.relics().createVaultEnchantBook(tier-1,new Random(0x47A11L+seed*313L)));
                check(lower.keySet().equals(BossEnchantService.enchants(book).keySet()),"paired seed preserves book enchant selection across tiers");
                check(BossEnchantService.enchants(book).get(enchant)>=lower.get(enchant),"paired seed book quality never decreases across tiers");
            }
        }
        check(bookSamples.keySet().equals(bookPool.stream().map(Award::enchant).collect(java.util.stream.Collectors.toSet())),"every configured book enchant obtainable");
        audit.append("Book coverage ").append(bookSamples.entrySet().stream().map(e->e.getKey().getKey()+"="+BossEnchantService.enchants(e.getValue()).get(e.getKey())).toList()).append('\n');
    }
    private static void fixture(ConfigurationSection config,String id,Material material,double weight,Map<String,Integer> enchants){
        config.set(id+".material",material.name());config.set(id+".weight",weight);
        for(var entry:enchants.entrySet()){
            String path=id+".enchantments."+entry.getKey();config.set(path+".min",entry.getValue());config.set(path+".max",entry.getValue());config.set(path+".chance",1);
        }
    }
    private void customConfiguration(){
        var fixture=new YamlConfiguration();
        fixture(fixture,"boots",Material.DIAMOND_BOOTS,1,Map.of("feather_falling",6,"unbreaking",5,"sharpness",9,"mending",1));
        fixture(fixture,"sword",Material.DIAMOND_SWORD,1,Map.of("sharpness",7,"unbreaking",5));
        fixture(fixture,"excluded-hoe",Material.DIAMOND_HOE,50,Map.of("efficiency",9));
        fixture(fixture,"ordinary-only-helmet",Material.DIAMOND_HELMET,50,Map.of("protection",4));
        fixture(fixture,"looting",Material.ENCHANTED_BOOK,1,Map.of("looting",5));
        fixture(fixture,"fortune",Material.ENCHANTED_BOOK,1,Map.of("fortune",5));
        fixture(fixture,"mixed-book",Material.ENCHANTED_BOOK,1,Map.of("sharpness",7,"unbreaking",5));
        fixture(fixture,"disabled-book",Material.ENCHANTED_BOOK,0,Map.of("efficiency",12));
        fixture.set("mixed-book.enchantments.protection.min",9);fixture.set("mixed-book.enchantments.protection.max",9);fixture.set("mixed-book.enchantments.protection.chance",0);
        var helper=new VaultEnchantRewards(garden,fixture);Set<String> gearSeen=new HashSet<>();Set<Enchantment> bookSeen=new HashSet<>();
        for(int seed=0;seed<1024;seed++){
            var gear=helper.gear(new Random(0xF177EL+seed*313));oneEnchant(gear,true,"custom configured gear");
            var entry=BossEnchantService.enchants(gear).entrySet().iterator().next();
            check(entry.getValue()>entry.getKey().getMaxLevel(),"legacy no-tier custom gear remains strictly overcap");
            String pair=gear.getType()+":"+entry.getKey().getKey();gearSeen.add(pair);
            check(gear.getType()==Material.DIAMOND_BOOTS||gear.getType()==Material.DIAMOND_SWORD,"custom gear excludes unsupported and normal-only profiles");
            check(entry.getKey()!=Enchantment.MENDING&&entry.getKey()!=Enchantment.EFFICIENCY,"custom gear excludes normal-only/unsupported enchantment");
            if(gear.getType()==Material.DIAMOND_BOOTS)check(entry.getKey()!=Enchantment.SHARPNESS,"native incompatible enchant removed from custom boots");
            var book=helper.book(new Random(0xB007L+seed*313));oneEnchant(book,false,"custom configured book");
            var award=BossEnchantService.enchants(book).entrySet().iterator().next();bookSeen.add(award.getKey());
            check(Map.of(Enchantment.LOOTING,5,Enchantment.FORTUNE,5,Enchantment.SHARPNESS,7,Enchantment.UNBREAKING,5).getOrDefault(award.getKey(),-1)==award.getValue(),"custom books follow fixed configured levels and omit disabled entries");
            bookSamples.put(award.getKey(),book);
        }
        check(gearSeen.size()==4,"all four custom compatible gear/enchant choices obtainable");
        check(bookSeen.equals(Set.of(Enchantment.LOOTING,Enchantment.FORTUNE,Enchantment.SHARPNESS,Enchantment.UNBREAKING)),"custom Looting V and Fortune V pool authoritative; each book has one enchant");
    }
    private static int[] expectedCaps(LimitBreakType type){
        return switch(type){
            case SHARPNESS,POWER,EFFICIENCY,SMITE->new int[]{6,6,7,8,8};
            case PROTECTION->new int[]{5,5,6,8,8};
            case FORTUNE,LOOTING,UNBREAKING,SWEEPING_EDGE,THORNS,DEPTH_STRIDER->new int[]{4,4,4,5,5};
            case FEATHER_FALLING->new int[]{5,5,6,7,7};
            case DENSITY->new int[]{6,6,6,7,7};
            case BREACH->new int[]{5,5,5,5,5};
        };
    }
    private static Material limitBreakMaterial(LimitBreakType type){
        return switch(type){
            case PROTECTION,THORNS->Material.DIAMOND_CHESTPLATE;
            case POWER->Material.BOW;
            case EFFICIENCY,FORTUNE->Material.DIAMOND_PICKAXE;
            case FEATHER_FALLING,DEPTH_STRIDER->Material.DIAMOND_BOOTS;
            case DENSITY,BREACH->Material.MACE;
            default->Material.DIAMOND_SWORD;
        };
    }
    private ItemStack limitBreakTarget(LimitBreakType type,int level){
        var item=new ItemStack(limitBreakMaterial(type));
        var meta=item.getItemMeta();meta.addEnchant(type.enchantment(),level,true);
        meta.getPersistentDataContainer().set(new NamespacedKey(this,"target_marker"),PersistentDataType.STRING,"unchanged");
        item.setItemMeta(meta);return item;
    }
    private void gradedLimitBreaks(){
        check(LimitBreakType.values().length==14,"all fourteen Limit Break types covered by explicit cap matrix");
        for(var type:LimitBreakType.values()){
            var legacy=garden.relics().createScrollLimitBreak(type);byte[] legacyBefore=legacy.serializeAsBytes();
            check(garden.relics().getLimitBreakTier(legacy)==0&&garden.relics().getLimitBreakCap(legacy)==type.maxLevel(),"unmarked legacy scroll retains original maximum "+type);
            var legacyTarget=limitBreakTarget(type,type.maxLevel()-1);
            var legacyResult=garden.relics().evaluateScrollCraft(legacy,legacyTarget);
            check(legacyResult!=null&&garden.relics().getLimitBreakUpgradeLevel(legacyResult,type)==type.maxLevel(),"legacy scroll still reaches original maximum "+type);
            check(garden.relics().evaluateScrollCraft(legacy,legacyResult)==null,"legacy scroll cannot exceed original maximum "+type);
            check(Arrays.equals(legacyBefore,legacy.serializeAsBytes()),"legacy scroll template remains unchanged "+type);
            ItemStack previous=null;
            for(int tier=1;tier<=5;tier++){
                int cap=expectedCaps(type)[tier-1];var scroll=garden.relics().createScrollLimitBreak(type,tier);
                byte[] scrollBefore=scroll.serializeAsBytes();
                check(garden.relics().getLimitBreakType(scroll)==type&&garden.relics().getLimitBreakTier(scroll)==tier,"typed scroll carries selected tier "+type+" T"+tier);
                check(garden.relics().getLimitBreakCap(scroll)==cap,"typed scroll has explicit expected cap "+type+" T"+tier);
                check(VaultProgression.limitBreakCap(type.enchantment(),type.maxLevel(),tier)==cap,"progression helper agrees with fixed cap matrix "+type+" T"+tier);
                var restored=ItemStack.deserializeBytes(scroll.serializeAsBytes());
                check(garden.relics().getLimitBreakTier(restored)==tier&&garden.relics().getLimitBreakCap(restored)==cap,"grade and cap survive item serialization "+type+" T"+tier);
                if(previous!=null)check(!previous.isSimilar(scroll),"different scroll grades cannot stack even when their numeric caps match "+type+" T"+tier);
                check(!legacy.isSimilar(scroll),"new graded scroll cannot merge with a legacy scroll "+type+" T"+tier);
                var target=limitBreakTarget(type,cap-1);byte[] targetBefore=target.serializeAsBytes();
                var result=garden.relics().evaluateScrollCraft(scroll,target);
                check(result!=null&&garden.relics().getLimitBreakUpgradeLevel(result,type)==cap,"graded scroll applies exactly +1 up to its own cap "+type+" T"+tier);
                check(result.getType()==target.getType()&&result.getAmount()==1&&"unchanged".equals(result.getItemMeta().getPersistentDataContainer().get(new NamespacedKey(this,"target_marker"),PersistentDataType.STRING)),"graded upgrade preserves equipment identity and unrelated metadata");
                check(garden.relics().evaluateScrollCraft(scroll,result)==null,"graded scroll cannot upgrade its own capped result again "+type+" T"+tier);
                check(garden.relics().evaluateScrollCraft(scroll,limitBreakTarget(type,cap))==null,"native overcap target at source grade cap is rejected "+type+" T"+tier);
                check(garden.relics().evaluateScrollCraft(scroll,limitBreakTarget(type,Math.max(cap+1,type.maxLevel())))==null,"low-grade scroll cannot upgrade stronger existing gear "+type+" T"+tier);
                check(garden.relics().evaluateScrollCraft(scroll,new ItemStack(limitBreakMaterial(type)))==null,"scroll requires the original enchantment "+type);
                check(garden.relics().evaluateScrollCraft(scroll,new ItemStack(Material.DIRT))==null,"scroll rejects incompatible equipment "+type);
                check(Arrays.equals(scrollBefore,scroll.serializeAsBytes())&&Arrays.equals(targetBefore,target.serializeAsBytes()),"evaluating successful and rejected upgrades never mutates source scroll or target "+type+" T"+tier);
                previous=scroll;
            }
            var valid=garden.relics().createScrollLimitBreak(type,1);
            for(int malformed=0;malformed<9;malformed++){
                var scroll=valid.clone();var meta=scroll.getItemMeta();var data=meta.getPersistentDataContainer();
                switch(malformed){
                    case 0->data.remove(garden.key("limit_break_cap"));
                    case 1->data.remove(garden.key("limit_break_tier"));
                    case 2->data.set(garden.key("limit_break_tier"),PersistentDataType.INTEGER,0);
                    // Grades run I..VIII since the Tier VI-VIII sanctum hoard (e2.194); IX is out of range.
                    case 3->data.set(garden.key("limit_break_tier"),PersistentDataType.INTEGER,9);
                    case 4->data.set(garden.key("limit_break_tier"),PersistentDataType.STRING,"1");
                    case 5->data.set(garden.key("limit_break_cap"),PersistentDataType.STRING,"32");
                    case 6->data.set(garden.key("limit_break_cap"),PersistentDataType.INTEGER,0);
                    case 7->data.set(garden.key("limit_break_cap"),PersistentDataType.INTEGER,-1);
                    case 8->data.set(garden.key("limit_break_type"),PersistentDataType.STRING,"INVALID");
                }
                scroll.setItemMeta(meta);
                check(garden.relics().getLimitBreakCap(scroll)==0,"malformed graded scroll fails closed "+type+" case "+malformed);
                check(garden.relics().evaluateScrollCraft(scroll,limitBreakTarget(type,1))==null,"malformed graded scroll cannot apply "+type+" case "+malformed);
            }
            var forged=valid.clone();var meta=forged.getItemMeta();meta.getPersistentDataContainer().set(garden.key("limit_break_cap"),PersistentDataType.INTEGER,32);forged.setItemMeta(meta);
            check(garden.relics().getLimitBreakCap(forged)==expectedCaps(type)[0],"forged stored cap cannot exceed the tier's calculated ceiling "+type);
            check(garden.relics().evaluateScrollCraft(forged,limitBreakTarget(type,expectedCaps(type)[0]))==null,"forged cap cannot bypass grade restriction "+type);
        }
        check(garden.relics().getLimitBreakCap(new ItemStack(Material.PAPER))==0,"untyped paper has no Limit Break entitlement");
        for(int tier:new int[]{0,9,-1})try{   // I..VIII are valid since the Tier VI-VIII sanctum hoard (e2.194)
            garden.relics().createScrollLimitBreak(LimitBreakType.SHARPNESS,tier);throw new AssertionError("invalid Limit Break grade accepted");
        }catch(IllegalArgumentException expected){passed++;}
        audit.append("Limit Break: all 14 types x 5 grades; explicit ceilings, serialization, non-stacking, +1/cap enforcement, legacy maxima and malformed metadata\n");
    }
    /** Force an exact primary ticket, then miss all upper-level and double rolls. */
    private static final class HeadTicket extends Random{
        final int head,tail;int large;
        HeadTicket(int head,int tail){this.head=head;this.tail=tail;}
        @Override public int nextInt(int bound){if(bound==10000)return large++==0?head:tail;return bound==100?99:0;}
        @Override public boolean nextBoolean(){return false;}
        @Override public double nextDouble(){return .99;}
    }
    private static final class DoubleTicket extends Random{
        final int primary,gate,bonus;int large;
        DoubleTicket(int primary,int gate,int bonus){this.primary=primary;this.gate=gate;this.bonus=bonus;}
        @Override public int nextInt(int bound){if(bound!=10000)return bound==100?99:0;return switch(large++){case 0->primary;case 1->gate;case 2->bonus;default->throw new AssertionError("unexpected extra chance draw");};}
        @Override public double nextDouble(){return .99;}
    }
    private PrimaryReward classify(ItemStack item){
        if(magic.legendaryWeapons().identify(item)!=null)return PrimaryReward.LEGENDARY_WEAPON;
        if(garden.adventurerWeapons().identify(item)!=null)return PrimaryReward.RARE_WEAPON;
        if(magic.flyingStaff().isStaff(item))return magic.flyingStaff().tier(item)==2?PrimaryReward.FLYING_STAFF_TIER2:PrimaryReward.FLYING_STAFF;
        if(magic.staffs().isStaff(item))return PrimaryReward.ELEMENTAL_STAFF;
        if(item.getType()==Material.ENCHANTED_BOOK)return PrimaryReward.ENCHANT_BOOK;
        if(garden.relics().getLimitBreakType(item)!=null)return PrimaryReward.LIMIT_BREAK;
        if(garden.relics().getUniqueEnchant(item)!=null)return PrimaryReward.UNIQUE_SCROLL;
        if(garden.relics().isScrollEternity(item))return PrimaryReward.SCROLL_ETERNITY;
        var relic=garden.relics().type(item);
        if(relic==com.example.voidscape.item.RelicService.Relic.RIFT_PICKAXE||relic==com.example.voidscape.item.RelicService.Relic.SMELTER_PICKAXE)return PrimaryReward.EQUIPMENT;
        if(GEAR.contains(item.getType())&&BossEnchantService.enchants(item).size()==1)return PrimaryReward.DIAMOND_GEAR;
        throw new AssertionError("unexpected production reward "+item);
    }
    private void productionItem(ItemStack item){
        check(item!=null&&item.getAmount()==1,"production reward is one item");
        check(item.getType()!=Material.POTION&&item.getType()!=Material.SPLASH_POTION&&item.getType()!=Material.LINGERING_POTION&&magic.manaPotions().quality(item)==null,"no mana potion in any Vault reward");
        check(!RemovedRelics.matches(item),"no retired relic in production list");
        var keys=item.getItemMeta().getPersistentDataContainer().getKeys();
        check(keys.stream().noneMatch(k->Set.of("core","magic_core","mythic_core").contains(k.getKey())),"no retired magic core in production list");
        if(magic.flyingStaff().isStaff(item)){
            int tier=magic.flyingStaff().tier(item);var meta=item.getItemMeta();var data=meta.getPersistentDataContainer();
            check(data.get(new NamespacedKey(magic,"flying_staff"),PersistentDataType.BYTE)==(byte)1,"flying staff carries the usable staff identity");
            check(data.getOrDefault(new NamespacedKey(magic,"flying_staff_tier"),PersistentDataType.INTEGER,0)==tier,"flying staff explicitly stores its selected tier");
            check(new NamespacedKey("advance_magic",tier==2?"flying_staff_tier2":"flying_staff").equals(meta.getItemModel()),"flying staff has its correct tier-specific item model");
            var restored=ItemStack.deserializeBytes(item.serializeAsBytes());
            check(magic.flyingStaff().isStaff(restored)&&magic.flyingStaff().tier(restored)==tier,"flying staff identity and seating tier survive item serialization");
        }
    }
    private void productionTier(ItemStack item,int tier){
        var category=classify(item);
        if(ordinary(category)){
            var pool=category==PrimaryReward.DIAMOND_GEAR?gearPool:bookPool;
            check(pool.stream().anyMatch(a->a.accepts(item,tier)),"production ordinary reward follows selected tier quality T"+tier);
        }
        if(category==PrimaryReward.LIMIT_BREAK){
            var type=garden.relics().getLimitBreakType(item);
            check(garden.relics().getLimitBreakTier(item)==tier,"production Limit Break grade matches selected Vault tier T"+tier);
            check(garden.relics().getLimitBreakCap(item)==expectedCaps(type)[tier-1],"production Limit Break has grade-specific cap T"+tier+" "+type);
        }
    }
    private void productionFactories(){
        var kind=DungeonLayout.Kind.values()[0];
        for(int tier=1;tier<=5;tier++){
            int[] counts=new int[PrimaryReward.values().length];
            for(int ticket=0;ticket<10000;ticket++){
                var items=garden.relics().rollVaultRewards(site(kind,tier),new HeadTicket(ticket,9999));
                if(items.size()!=1)throw new AssertionError("missed doubles must give exactly one item T"+tier+" ticket "+ticket+" got "+items.size());
                var item=items.getFirst();var category=classify(item);counts[category.ordinal()]++;
                productionItem(item);
                productionTier(item,tier);
                if(ordinary(category))oneEnchant(item,category==PrimaryReward.DIAMOND_GEAR,"production primary");
            }
            check(Arrays.equals(counts,WEIGHTS[tier]),"actual factory categories match exact 10000-ticket table T"+tier);
        }
        for(var theme:DungeonLayout.Kind.values())for(int tier=1;tier<=5;tier++)for(int category=0;category<PrimaryReward.values().length;category++){
            if(WEIGHTS[tier][category]==0)continue;
            int low=start(tier,category),high=low+WEIGHTS[tier][category]-1;
            for(int primary:new int[]{low,high})for(int tail:new int[]{0,9999}){
                var items=garden.relics().rollVaultRewards(site(theme,tier),new HeadTicket(primary,tail));
                var expected=PrimaryReward.values()[category];
                int amount=tier>=2&&ordinary(expected)&&tail==0?2:1;
                check(items.size()==amount,"exact one/two item cap and ordinary-only double "+theme+" T"+tier+" "+expected);
                check(classify(items.getFirst())==expected,"thematic factory preserves selected category");
                for(var item:items){productionItem(item);productionTier(item,tier);}
                if(items.size()==2)check(ordinary(classify(items.getLast())),"bonus cannot repeat rare staff or special equipment");
            }
        }
        for(int tier=2;tier<=5;tier++)for(int primary:new int[]{0,WEIGHTS[tier][0]})for(int gate:new int[]{0,DOUBLE[tier]-1,DOUBLE[tier],9999})for(int bonus:new int[]{6499,6500}){
            var random=new DoubleTicket(primary,gate,bonus);var items=garden.relics().rollVaultRewards(site(kind,tier),random);
            boolean wins=gate<DOUBLE[tier];
            check(items.size()==(wins?2:1),"production double uses exact ticket boundary T"+tier+" ticket "+gate);
            check(random.large==(wins?3:2),"one double attempt and at most one bonus ticket");
            if(wins)check(classify(items.getLast())==(bonus==6499?PrimaryReward.DIAMOND_GEAR:PrimaryReward.ENCHANT_BOOK),"production extra uses exact 65/35 equipment/book boundary");
        }
        check(classify(garden.relics().rollVaultReward(new HeadTicket(0,9999)))==PrimaryReward.DIAMOND_GEAR,"admin preview uses same Tier 1 equipment pool");
        check(classify(garden.relics().rollVaultReward(new HeadTicket(6500,9999)))==PrimaryReward.ENCHANT_BOOK,"admin preview no longer gives mana in book band");
    }
    private void optionalIntegrations(){
        boolean staffEnabled=garden.getConfig().getBoolean("staff-loot.enabled",true);
        garden.getConfig().set("staff-loot.enabled",false);
        try{
            for(int tier=2;tier<=5;tier++){
                var rewards=garden.relics().rollVaultRewards(site(DungeonLayout.Kind.values()[0],tier),new HeadTicket(start(tier,7),0));
                check(rewards.size()==1&&classify(rewards.getFirst())==PrimaryReward.ENCHANT_BOOK,"disabled staff category falls back to one configured book without bonus T"+tier);
            }
        }finally{garden.getConfig().set("staff-loot.enabled",staffEnabled);}
        Bukkit.getPluginManager().disablePlugin(magic);
        check(!magic.isEnabled(),"Advance Magic disabled for optional integration fallback");
        for(int tier=2;tier<=5;tier++)for(var category:new PrimaryReward[]{PrimaryReward.RARE_WEAPON,PrimaryReward.LEGENDARY_WEAPON,PrimaryReward.ELEMENTAL_STAFF,PrimaryReward.FLYING_STAFF,PrimaryReward.FLYING_STAFF_TIER2}){
            if(WEIGHTS[tier][category.ordinal()]==0)continue;
            var rewards=garden.relics().rollVaultRewards(site(DungeonLayout.Kind.values()[0],tier),new HeadTicket(start(tier,category.ordinal()),0));
            var expected=category==PrimaryReward.RARE_WEAPON||category==PrimaryReward.LEGENDARY_WEAPON?PrimaryReward.RARE_WEAPON:PrimaryReward.ENCHANT_BOOK;
            check(rewards.size()==1&&classify(rewards.getFirst())==expected,"absent Advance Magic preserves one supported fallback T"+tier+" category "+category);
            productionItem(rewards.getFirst());
        }
    }
    private net.minecraft.world.inventory.AnvilMenu menu(ItemStack left,ItemStack right){
        var menu=new net.minecraft.world.inventory.AnvilMenu(1,actor.getInventory(),net.minecraft.world.inventory.ContainerLevelAccess.create(level,new net.minecraft.core.BlockPos(2,100,0)));
        menu.checkReachable=false;actor.containerMenu=menu;
        menu.getSlot(0).set(org.bukkit.craftbukkit.inventory.CraftItemStack.asNMSCopy(left));
        menu.getSlot(1).set(org.bukkit.craftbukkit.inventory.CraftItemStack.asNMSCopy(right));
        menu.slotsChanged(menu.getSlot(0).container);return menu;
    }
    private ItemStack result(net.minecraft.world.inventory.AnvilMenu menu){var raw=menu.getSlot(2).getItem();return raw.isEmpty()?null:org.bukkit.craftbukkit.inventory.CraftItemStack.asBukkitCopy(raw);}
    private void take(net.minecraft.world.inventory.AnvilMenu menu,Enchantment enchant,int n){
        var player=actor.getBukkitEntity();int xp=player.getLevel(),cost=menu.getCost();
        check(cost>0&&cost<40,"survival XP cost remains payable");
        check(!menu.bypassEnchantmentLevelRestriction,"anvil bypass reset after scoped calculation");
        menu.clicked(2,0,net.minecraft.world.inventory.ContainerInput.PICKUP,actor);
        check(player.getLevel()==xp-cost&&menu.getSlot(0).getItem().isEmpty()&&menu.getSlot(1).getItem().isEmpty(),"actual survival click pays XP and consumes inputs");
        var item=org.bukkit.craftbukkit.inventory.CraftItemStack.asBukkitCopy(menu.getCarried());
        check(BossEnchantService.enchants(item).equals(Map.of(enchant,n)),"survival output retains exactly awarded enchant and full level");
        menu.clicked(2,0,net.minecraft.world.inventory.ContainerInput.PICKUP,actor);
        check(player.getLevel()==xp-cost,"result cannot be replayed for another reward");menu.setCarried(net.minecraft.world.item.ItemStack.EMPTY);
    }
    private Material target(Enchantment enchant){return GEAR.stream().filter(m->enchant.canEnchantItem(new ItemStack(m))).findFirst().orElseThrow(()->new AssertionError("book has no applicable target "+enchant));}
    private void nativeAnvils()throws Exception{
        check(garden.bossEnchants().anvilEnabled(),"scoped anvil transfers enabled");
        var server=net.minecraft.server.MinecraftServer.getServer();var world=Bukkit.getWorlds().getFirst();
        level=((org.bukkit.craftbukkit.CraftWorld)world).getHandle();
        var profile=new com.mojang.authlib.GameProfile(UUID.randomUUID(),"VaultEconomyTester");
        actor=new net.minecraft.server.level.ServerPlayer(server,level,profile,net.minecraft.server.level.ClientInformation.createDefault());
        var connection=new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        connection.channel=new io.netty.channel.embedded.EmbeddedChannel();connection.address=new java.net.InetSocketAddress("127.0.0.1",1);
        actor.connection=new net.minecraft.server.network.ServerGamePacketListenerImpl(server,connection,actor,net.minecraft.server.network.CommonListenerCookie.createInitial(profile,false)){
            @Override public boolean hasClientLoaded(){return true;}
        };
        actor.setPos(.5,100,.5);server.getPlayerList().getPlayers().add(actor);server.getPlayerList().getPlayersByUUID().put(profile.id(),actor);level.addNewPlayer(actor);
        var player=actor.getBukkitEntity();player.setGravity(false);player.setGameMode(org.bukkit.GameMode.SURVIVAL);player.setLevel(100000);
        world.getBlockAt(0,99,0).setType(Material.STONE);world.getBlockAt(2,100,0).setType(Material.ANVIL);
        var existing=new ItemStack(Material.DIAMOND_SWORD);existing.addUnsafeEnchantment(Enchantment.SHARPNESS,9);existing.addEnchantment(Enchantment.UNBREAKING,3);
        player.getInventory().setItem(8,existing);byte[] before=player.getInventory().getItem(8).serializeAsBytes();
        try{
            for(var entry:bookSamples.entrySet()){
                var enchant=entry.getKey();var book=entry.getValue();int n=BossEnchantService.enchants(book).get(enchant);
                var anvil=menu(new ItemStack(target(enchant)),book);var output=result(anvil);
                check(output!=null&&BossEnchantService.enchants(output).equals(Map.of(enchant,n)),"native anvil transfers configured book "+enchant.getKey()+" "+n);
                if(n>enchant.getMaxLevel())check(garden.bossEnchants().authorized(output).equals(Map.of(enchant,n)),"book result retains scoped entitlement");
                take(anvil,enchant,n);
                if(n>enchant.getMaxLevel()){
                    var equal=result(menu(output,book));check(equal!=null&&BossEnchantService.enchants(equal).get(enchant)==n,"equal overcap book cannot grow another level");
                }
            }
            for(var sample:tierBookSamples){
                var enchantment=BossEnchantService.enchants(sample.item).entrySet().iterator().next();
                var anvil=menu(new ItemStack(target(enchantment.getKey())),sample.item);var output=result(anvil);
                check(output!=null&&BossEnchantService.enchants(output).equals(Map.of(enchantment.getKey(),enchantment.getValue())),"native anvil transfers exact tier book endpoint T"+sample.tier+" "+enchantment.getKey().getKey());
                if(enchantment.getValue()<=enchantment.getKey().getMaxLevel())
                    check(garden.bossEnchants().authorized(output).isEmpty(),"ordinary tier book never grants overcap entitlement after native anvil transfer");
                take(anvil,enchantment.getKey(),enchantment.getValue());
            }
            for(var gear:gearSamples){
                var entry=BossEnchantService.enchants(gear).entrySet().iterator().next();var damaged=gear.clone();var meta=(Damageable)damaged.getItemMeta();meta.setDamage(100);damaged.setItemMeta(meta);
                var anvil=menu(damaged,new ItemStack(gear.getType()));var output=result(anvil);
                check(output!=null&&BossEnchantService.enchants(output).equals(Map.of(entry.getKey(),entry.getValue())),"native repair preserves one enchant "+gear.getType());
                check(garden.bossEnchants().authorized(output).equals(BossEnchantService.enchants(output)),"native repair preserves exact entitlement");
                take(anvil,entry.getKey(),entry.getValue());
            }
            var incompatible=bookSamples.entrySet().stream().filter(e->!e.getKey().canEnchantItem(new ItemStack(Material.DIAMOND_SWORD))).findFirst();
            check(incompatible.isPresent(),"book pool includes armor/tool enchant for incompatible anvil check");
            check(result(menu(new ItemStack(Material.DIAMOND_SWORD),incompatible.get().getValue()))==null,"native anvil still rejects incompatible book");
            for(int i=0;i<100;i++)garden.relics().rollVaultRewards(site(DungeonLayout.Kind.values()[0],5),new Random(i));
            check(Arrays.equals(before,player.getInventory().getItem(8).serializeAsBytes()),"reward generation and anvil operations never mutate existing player item");
            check(garden.bossEnchants().authorized(player.getInventory().getItem(8)).isEmpty(),"ordinary player overcap item is never granted loot entitlement");
            nativeLimitBreakTransactions(player);nativeVaultGui(player);
        }finally{
            garden.bossEnchantEffects().clear(player);actor.discard();server.getPlayerList().getPlayers().remove(actor);server.getPlayerList().getPlayersByUUID().remove(profile.id());
        }
    }
    private InventoryClickEvent guiClick(org.bukkit.entity.Player player,int rawSlot,ClickType click,InventoryAction action){
        var event=new InventoryClickEvent(player.getOpenInventory(),InventoryType.SlotType.CONTAINER,rawSlot,click,action);
        Bukkit.getPluginManager().callEvent(event);
        check(event.isCancelled(),"registered GUI listener cancels "+click+" raw slot "+rawSlot);
        return event;
    }
    private void nativeLimitBreakTransactions(org.bukkit.entity.Player player)throws Exception{
        var saved=storageSnapshot(player);var savedCursor=player.getItemOnCursor().clone();
        java.lang.reflect.Field floodgateApiField=null;Object originalFloodgateApi=null;
        boolean mockedFormTransport=false;int[] sentForms={0};
        try{
            var type=LimitBreakType.SHARPNESS;
            var low=garden.relics().createScrollLimitBreak(type,1);low.setAmount(3);
            var high=garden.relics().createScrollLimitBreak(type,5);high.setAmount(2);
            var rename=menu(low,null);rename.setItemName("Renamed graded scroll");var renamed=result(rename);
            check(renamed!=null&&garden.relics().getLimitBreakTier(renamed)==1&&garden.relics().getLimitBreakCap(renamed)==6&&garden.relics().getLimitBreakType(renamed)==type,"real native anvil rename preserves graded scroll entitlement");
            check(garden.relics().evaluateScrollCraft(renamed,limitBreakTarget(type,6))==null,"renaming a low-grade scroll cannot bypass its upgrade ceiling");
            player.closeInventory();player.getInventory().clear();player.setItemOnCursor(null);
            for(boolean reverse:new boolean[]{false,true})for(int tier:new int[]{1,5}){
                int cap=expectedCaps(type)[tier-1];var scroll=garden.relics().createScrollLimitBreak(type,tier);scroll.setAmount(2);
                for(boolean capped:new boolean[]{false,true}){
                    player.getInventory().clear();player.setItemOnCursor(null);player.closeInventory();
                    var equipment=limitBreakTarget(type,capped?cap:cap-1);
                    player.getInventory().setItem(9,reverse?scroll.clone():equipment.clone());player.setItemOnCursor(reverse?equipment.clone():scroll.clone());
                    var beforeSlot=player.getInventory().getItem(9).clone();var beforeCursor=player.getItemOnCursor().clone();
                    var view=player.getOpenInventory();int raw=-1;
                    for(int candidate=0;candidate<view.countSlots();candidate++)if(player.getInventory().equals(view.getInventory(candidate))&&view.convertSlot(candidate)==9){raw=candidate;break;}
                    check(raw>=0,"native player inventory exposes upgrade target slot");
                    var event=new InventoryClickEvent(view,view.getSlotType(raw),raw,ClickType.LEFT,InventoryAction.SWAP_WITH_CURSOR);
                    // Each independent transaction uses a fresh listener, representing a separate tick.
                    new com.example.voidscape.enchant.EnchantApplyListener(garden,garden.relics()).onInventoryClick(event);
                    check(event.isCancelled(),"native cursor grade transaction cancels ordinary inventory movement");
                    if(capped)check(beforeSlot.equals(player.getInventory().getItem(9))&&beforeCursor.equals(player.getItemOnCursor()),"capped cursor transaction preserves both scroll count and equipment in either direction T"+tier);
                    else{
                        var output=reverse?player.getItemOnCursor():player.getInventory().getItem(9);
                        var remaining=reverse?player.getInventory().getItem(9):player.getItemOnCursor();
                        check(garden.relics().getLimitBreakUpgradeLevel(output,type)==cap,"native cursor transaction applies grade-limited +1 in either direction T"+tier);
                        check(remaining.getAmount()==1&&garden.relics().getLimitBreakTier(remaining)==tier,"native cursor transaction consumes exactly one scroll of selected grade");
                    }
                }
            }
            player.setItemOnCursor(null);player.getInventory().clear();player.closeInventory();
            com.example.voidscape.gui.ChestUpgradeGui.open(garden,player);var top=player.getOpenInventory().getTopInventory();
            top.setItem(com.example.voidscape.gui.ChestUpgradeGui.SLOT_EQUIP,limitBreakTarget(type,6));top.setItem(com.example.voidscape.gui.ChestUpgradeGui.SLOT_SCROLL,low.clone());
            var blockedEquip=top.getItem(11).clone();var blockedScroll=top.getItem(15).clone();
            guiClick(player,13,ClickType.LEFT,InventoryAction.PICKUP_ALL);
            check(blockedEquip.equals(top.getItem(11))&&blockedScroll.equals(top.getItem(15)),"Java upgrade GUI rejects a capped grade without consuming it");
            top.setItem(15,high.clone());guiClick(player,13,ClickType.LEFT,InventoryAction.PICKUP_ALL);
            check(garden.relics().getLimitBreakUpgradeLevel(top.getItem(11),type)==7&&top.getItem(15).getAmount()==1,"Java upgrade GUI consumes one eligible higher-grade scroll");
            top.clear();player.closeInventory();player.getInventory().clear();
            var form=Class.forName("com.example.voidscape.gui.FloodgateUpgradeForm",true,garden.getClass().getClassLoader());
            // API-only fixtures have no connected Bedrock transport. Replace only form delivery,
            // retaining the production form builder, inventory selection and transaction methods.
            var apiType=Class.forName("org.geysermc.floodgate.api.FloodgateApi",true,form.getClassLoader());
            var holder=Class.forName("org.geysermc.floodgate.api.InstanceHolder",true,apiType.getClassLoader());
            floodgateApiField=holder.getDeclaredField("api");floodgateApiField.setAccessible(true);
            originalFloodgateApi=floodgateApiField.get(null);
            if(originalFloodgateApi==null){
                Object transport=java.lang.reflect.Proxy.newProxyInstance(apiType.getClassLoader(),new Class<?>[]{apiType},(proxy,method,args)->switch(method.getName()){
                    case "sendForm"->{sentForms[0]++;yield true;}
                    case "isFloodgatePlayer","isFloodgateId","closeForm","transferPlayer"->false;
                    case "getPlayerCount"->0;
                    case "getPlayers"->List.of();
                    case "getPlayerPrefix"->".";
                    case "getPlayer"->null;
                    case "toString"->"Vault probe inert form transport";
                    case "hashCode"->System.identityHashCode(proxy);
                    case "equals"->proxy==args[0];
                    default->throw new UnsupportedOperationException("Unexpected mocked Floodgate operation: "+method.getName());
                });
                floodgateApiField.set(null,transport);mockedFormTransport=true;
            }
            var apply=form.getDeclaredMethod("applyLimitBreak",VoidscapePlugin.class,org.bukkit.entity.Player.class,int.class,LimitBreakType.class);apply.setAccessible(true);
            var guarded=form.getDeclaredMethod("applyLimitBreak",VoidscapePlugin.class,org.bukkit.entity.Player.class,int.class,LimitBreakType.class,ItemStack.class);guarded.setAccessible(true);
            var find=form.getDeclaredMethod("findLimitBreakSlot",VoidscapePlugin.class,org.bukkit.entity.Player.class,LimitBreakType.class,ItemStack.class);find.setAccessible(true);
            var count=form.getDeclaredMethod("countUsableLimitBreak",VoidscapePlugin.class,org.bukkit.entity.Player.class,LimitBreakType.class,ItemStack.class);count.setAccessible(true);
            player.getInventory().setItem(9,limitBreakTarget(type,6));player.getInventory().setItem(0,low.clone());player.getInventory().setItem(1,high.clone());
            check((int)find.invoke(null,garden,player,type,player.getInventory().getItem(9))==1&&(int)count.invoke(null,garden,player,type,player.getInventory().getItem(9))==2,"Bedrock form excludes capped lower grades when finding/counting usable scrolls");
            apply.invoke(null,garden,player,9,type);
            check(garden.relics().getLimitBreakUpgradeLevel(player.getInventory().getItem(9),type)==7&&player.getInventory().getItem(0).equals(low)&&player.getInventory().getItem(1).getAmount()==1,"Bedrock form consumes the eligible high-grade stack only");
            player.getInventory().clear();player.getInventory().setItem(9,limitBreakTarget(type,5));player.getInventory().setItem(0,high.clone());player.getInventory().setItem(1,low.clone());
            var expected=player.getInventory().getItem(9).clone();
            check((int)find.invoke(null,garden,player,type,expected)==1&&(int)count.invoke(null,garden,player,type,expected)==5,"Bedrock form prefers the lowest usable cap regardless of inventory order");
            guarded.invoke(null,garden,player,9,type,expected);
            check(garden.relics().getLimitBreakUpgradeLevel(player.getInventory().getItem(9),type)==6&&player.getInventory().getItem(0).equals(high)&&player.getInventory().getItem(1).getAmount()==2,"Bedrock form preserves high-grade scrolls when lower grade suffices");
            var completed=storageSnapshot(player);guarded.invoke(null,garden,player,9,type,expected);
            check(Arrays.equals(completed,storageSnapshot(player)),"replayed Bedrock form callback cannot upgrade or consume again");
            player.getInventory().clear();player.getInventory().setItem(9,limitBreakTarget(type,6));player.getInventory().setItem(0,low.clone());
            var capped=storageSnapshot(player);apply.invoke(null,garden,player,9,type);
            check(Arrays.equals(capped,storageSnapshot(player)),"Bedrock form with only capped scrolls leaves all items untouched");
            player.getInventory().setItem(9,limitBreakTarget(LimitBreakType.POWER,5));var incompatible=storageSnapshot(player);apply.invoke(null,garden,player,9,type);
            check(Arrays.equals(incompatible,storageSnapshot(player)),"Bedrock form rejects incompatible equipment without spending a scroll");
            player.getInventory().clear();player.getInventory().setItem(9,limitBreakTarget(type,6));player.getInventory().setItem(0,high.clone());
            var changed=storageSnapshot(player);guarded.invoke(null,garden,player,9,type,limitBreakTarget(type,5));
            check(Arrays.equals(changed,storageSnapshot(player)),"stale Bedrock form target cannot apply to replaced equipment");
            check(!mockedFormTransport||sentForms[0]>0,"API-only fixture exercises inert sendForm transport while retaining actual upgrade transactions");
            audit.append("Native Limit Break: anvil rename; cursor both directions at grades I/V; Java GUI and Bedrock mixed-grade selection, exact consumption and stale/replayed callbacks\n");
            audit.append("Bedrock form transport: ").append(mockedFormTransport?"mocked sendForm only; no connected Bedrock client":"installed Floodgate API; no connected Bedrock client").append('\n');
        }finally{
            if(floodgateApiField!=null)floodgateApiField.set(null,originalFloodgateApi);
            player.setItemOnCursor(null);player.closeInventory();player.getInventory().setStorageContents(saved);player.setItemOnCursor(savedCursor);
        }
    }
    private static ItemStack[] storageSnapshot(org.bukkit.entity.Player player){
        return Arrays.stream(player.getInventory().getStorageContents()).map(item->item==null?null:item.clone()).toArray(ItemStack[]::new);
    }
    private static Set<UUID> droppedItems(org.bukkit.entity.Player player){
        return player.getWorld().getEntitiesByClass(org.bukkit.entity.Item.class).stream().map(org.bukkit.entity.Item::getUniqueId).collect(java.util.stream.Collectors.toSet());
    }
    private int[] inventoryRewardCounts(org.bukkit.entity.Player player){
        int[] result=new int[PrimaryReward.values().length];
        for(var item:player.getInventory().getStorageContents())if(item!=null&&!item.getType().isAir()&&item.getType()!=Material.BARRIER&&!garden.relics().isVoidKey(item))
            result[classify(item).ordinal()]+=item.getAmount();
        return result;
    }
    private void checkVaultStats(org.bukkit.inventory.Inventory top,int tier,int openings,int[] counts){
        var icon=top.getItem(34);check(icon!=null&&icon.getType()==Material.WRITABLE_BOOK,"Vault statistics icon available after committed rolls");
        var lore=icon.getItemMeta().getLore().stream().map(org.bukkit.ChatColor::stripColor).toList();String text=String.join("\n",lore);
        String roman=new String[]{"I","II","III","IV","V"}[tier-1];
        check(lore.stream().anyMatch(line->line.equals("Tier "+roman+" · เปิดสำเร็จ "+openings+" รอบ")),"statistics show exact successfully committed opening count for selected tier "+tier);
        check(lore.contains("ได้รับจริง "+Arrays.stream(counts).sum()+" ชิ้น"),"statistics count all delivered batch items including ordinary bonuses");
        String[] labels={"อุปกรณ์","สมุด","Limit Break","Unique","เครื่องมือโบราณ","Rare","ตำนาน","คทาธาตุ","คทาบินธรรมดา","Eternity","คทาบินสองคน"};
        for(var category:PrimaryReward.values()){
            String expected=labels[category.ordinal()]+" "+counts[category.ordinal()];
            check(java.util.regex.Pattern.compile(java.util.regex.Pattern.quote(expected)+"(?![0-9])").matcher(text).find(),"statistics match actual delivered "+category+" count "+counts[category.ordinal()]);
        }
        check(lore.contains("รุ่นปลั๊กอิน "+garden.getPluginMeta().getVersion()),"statistics identify actual loaded plugin version");
        check(lore.contains("ภาพตัวอย่างแสดงเฉพาะรอบสุดท้าย"),"statistics explain final-opening-only preview");
    }
    /** Real inventory views and registered click/drag handlers, without consuming an actual Vault. */
    private void nativeVaultGui(org.bukkit.entity.Player player)throws Exception{
        var gui=garden.testGui();check(gui!=null,"registered admin test GUI available");
        var saved=storageSnapshot(player);var drops=droppedItems(player);
        var ledgerPath=garden.getDataFolder().toPath().resolve("dungeons.yml");
        byte[] ledgerBytes=Files.exists(ledgerPath)?Files.readAllBytes(ledgerPath):null;
        var ledgerField=garden.dungeons().getClass().getDeclaredField("ledger");ledgerField.setAccessible(true);
        var ledger=(YamlConfiguration)ledgerField.get(garden.dungeons());String ledgerBefore=ledger.saveToString();
        var permission=player.addAttachment(this,"evergarden.admin",true);
        var key=garden.relics().createVoidKey(5);key.setAmount(7);
        try{
            check(!player.isOp()&&player.hasPermission("evergarden.admin"),"GUI probe uses explicit admin permission on a non-OP player");
            gui.open(player);
            check(player.getOpenInventory().getTopInventory().getItem(27).getType()==Material.VAULT,"main test kit exposes dedicated Vault submenu button");
            for(var theme:DungeonLayout.Kind.values())for(int tier=1;tier<=5;tier++)for(var click:new ClickType[]{ClickType.LEFT,ClickType.RIGHT,ClickType.SHIFT_LEFT,ClickType.SHIFT_RIGHT}){
                player.getInventory().clear();player.getInventory().setItem(35,key.clone());
                gui.openVaultTests(player,theme);var top=player.getOpenInventory().getTopInventory();
                check(top.getSize()==45,"Vault submenu has expected size for "+theme);
                check(top.getItem(13).getItemMeta().getLore().stream().anyMatch(line->line.endsWith(garden.getPluginMeta().getVersion())),"Vault info identifies actual loaded Evergarden version");
                check(top.getItem(13).getItemMeta().getLore().stream().anyMatch(line->line.contains("Advance Magic")&&line.endsWith(magic.getPluginMeta().getVersion())),"Vault info identifies enabled Advance Magic version");
                check(top.getItem(34).getItemMeta().getLore().stream().anyMatch(line->line.contains("ยังไม่มีรอบที่เปิดสำเร็จ")),"new Vault test view starts with no committed opening statistics");
                var button=top.getItem(19+tier);
                check(button!=null&&button.getType()==Material.VAULT&&button.getAmount()==tier,"tier button identifies its selected tier "+tier);
                String odds=String.format(Locale.ROOT,"%.2f%%",WEIGHTS[tier][PrimaryReward.FLYING_STAFF_TIER2.ordinal()]/100.0);
                check(button.getItemMeta().getLore().stream().anyMatch(line->line.contains("สองคน")&&line.endsWith(odds)),"tier button shows exact two-seat staff odds "+tier);
                String ordinaryOdds=String.format(Locale.ROOT,"%.2f%%",WEIGHTS[tier][PrimaryReward.FLYING_STAFF.ordinal()]/100.0);
                check(button.getItemMeta().getLore().stream().anyMatch(line->line.contains("ธรรมดา")&&line.endsWith(ordinaryOdds)),"tier button shows rebalanced ordinary flying staff odds "+tier);
                String legendaryOdds=String.format(Locale.ROOT,"%.2f%%",WEIGHTS[tier][PrimaryReward.LEGENDARY_WEAPON.ordinal()]/100.0);
                check(button.getItemMeta().getLore().stream().anyMatch(line->line.contains("ตำนาน")&&line.endsWith(legendaryOdds)),"tier button shows rebalanced legendary weapon odds "+tier);
                String eternityOdds=String.format(Locale.ROOT,"%.2f%%",WEIGHTS[tier][PrimaryReward.SCROLL_ETERNITY.ordinal()]/100.0);
                check(button.getItemMeta().getLore().stream().anyMatch(line->line.contains("Eternity (Unbreakable)")&&line.endsWith(eternityOdds)),"tier button shows exact Unbreakable scroll odds "+tier);
                String grade="Tier "+new String[]{"I","II","III","IV","V"}[tier-1];
                check(button.getItemMeta().getLore().stream().anyMatch(line->line.contains("Limit Break")&&line.endsWith(grade)),"tier button identifies its earned Limit Break grade "+tier);
                int openings=click.isShiftClick()?10:1;
                guiClick(player,19+tier,click,click.isShiftClick()?InventoryAction.MOVE_TO_OTHER_INVENTORY:InventoryAction.PICKUP_ALL);
                int received=0;
                for(var stack:player.getInventory().getStorageContents()){
                    if(stack==null||stack.getType().isAir())continue;
                    if(garden.relics().isVoidKey(stack)){check(stack.equals(key),"GUI rolls preserve the held Vault key count and metadata");continue;}
                    received+=stack.getAmount();var item=stack.clone();item.setAmount(1);productionItem(item);productionTier(item,tier);
                    var category=classify(item);
                    check(WEIGHTS[tier][category.ordinal()]>0,"GUI reward is obtainable from selected tier "+tier+" "+category);
                }
                check(received>=openings&&received<=openings*2,"GUI grants complete one/ten-opening batch for "+theme+" T"+tier+" "+click);
                checkVaultStats(top,tier,openings,inventoryRewardCounts(player));
                if(tier==1)check(received==openings,"Tier I GUI never awards an extra item");
                check(top.getItem(30)!=null&&top.getItem(30).getAmount()==1,"last opening displays one primary preview");
                if(top.getItem(31)!=null)check(ordinary(classify(top.getItem(30)))&&ordinary(classify(top.getItem(31))),"two preview items obey ordinary-only bonus rule");
                var granted=storageSnapshot(player);
                var publishedStats=top.getItem(34).clone();
                for(int slot:new int[]{30,31,34,45})guiClick(player,slot,ClickType.LEFT,InventoryAction.PICKUP_ALL);
                for(var blocked:new ClickType[]{ClickType.NUMBER_KEY,ClickType.MIDDLE,ClickType.DOUBLE_CLICK,ClickType.DROP})
                    guiClick(player,19+tier,blocked,blocked==ClickType.NUMBER_KEY?InventoryAction.HOTBAR_SWAP:InventoryAction.NOTHING);
                check(Arrays.equals(granted,storageSnapshot(player)),"preview, bottom-inventory and unsupported clicks cannot grant or withdraw items");
                check(publishedStats.equals(top.getItem(34)),"statistics icon and unsupported clicks cannot increment committed counts");
            }
            gui.openVaultTests(player,DungeonLayout.Kind.SANCTUM_DARK);
            var accumulatedTop=player.getOpenInventory().getTopInventory();int[] committed=new int[5];int[][] delivered=new int[5][PrimaryReward.values().length];
            int[] selectedTiers={5,4,5,1,4},selectedOpenings={1,10,10,1,1};
            for(int roll=0;roll<selectedTiers.length;roll++){
                int tier=selectedTiers[roll],openings=selectedOpenings[roll];
                player.getInventory().clear();player.getInventory().setItem(35,key.clone());
                guiClick(player,19+tier,openings==10?ClickType.SHIFT_LEFT:ClickType.LEFT,openings==10?InventoryAction.MOVE_TO_OTHER_INVENTORY:InventoryAction.PICKUP_ALL);
                var actual=inventoryRewardCounts(player);committed[tier-1]+=openings;
                for(int category=0;category<actual.length;category++)delivered[tier-1][category]+=actual[category];
                checkVaultStats(accumulatedTop,tier,committed[tier-1],delivered[tier-1]);
            }
            var priorStats=accumulatedTop.getItem(34).clone();
            var priorPreview=accumulatedTop.getItem(30).clone();
            var priorBonus=accumulatedTop.getItem(31)==null?null:accumulatedTop.getItem(31).clone();
            for(int slot=0;slot<36;slot++)player.getInventory().setItem(slot,new ItemStack(Material.BARRIER,64));
            var full=storageSnapshot(player);
            guiClick(player,24,ClickType.SHIFT_LEFT,InventoryAction.MOVE_TO_OTHER_INVENTORY);
            check(Arrays.equals(full,storageSnapshot(player)),"full-inventory batch fails atomically without replacing existing items");
            check(priorPreview.equals(accumulatedTop.getItem(30))&&Objects.equals(priorBonus,accumulatedTop.getItem(31)),"rejected full-inventory batch preserves the last successful preview");
            check(priorStats.equals(accumulatedTop.getItem(34)),"rejected full-inventory batch never increments committed opening or category totals");
            player.getInventory().setItem(0,null);var nearFull=storageSnapshot(player);
            guiClick(player,20,ClickType.SHIFT_RIGHT,InventoryAction.MOVE_TO_OTHER_INVENTORY);
            var afterNearFull=storageSnapshot(player);
            if(!Arrays.equals(nearFull,afterNearFull)){
                int received=0;for(var item:afterNearFull)if(item!=null&&item.getType()!=Material.BARRIER)received+=item.getAmount();
                check(received==10,"near-full inventory receives the complete batch if it fits, never a partial batch");
                var actual=inventoryRewardCounts(player);committed[0]+=10;
                for(int category=0;category<actual.length;category++)delivered[0][category]+=actual[category];
                checkVaultStats(accumulatedTop,1,committed[0],delivered[0]);
            }else{
                check(true,"near-full inventory rejects a batch without retaining partially inserted rewards");
                check(priorStats.equals(accumulatedTop.getItem(34)),"near-full rejection never increments batch counters");
            }
            var drag=new InventoryDragEvent(player.getOpenInventory(),new ItemStack(Material.DIAMOND),new ItemStack(Material.DIAMOND,2),false,Map.of(30,new ItemStack(Material.DIAMOND)));
            Bukkit.getPluginManager().callEvent(drag);
            check(drag.isCancelled()&&Arrays.equals(afterNearFull,storageSnapshot(player)),"registered drag handler protects Vault preview without altering storage");
            player.removeAttachment(permission);permission=null;player.recalculatePermissions();
            check(!player.isOp()&&!player.hasPermission("evergarden.admin")&&!player.hasPermission("voidscape.admin"),"admin permission revoked while Vault submenu remains open");
            var denied=storageSnapshot(player);
            guiClick(player,20,ClickType.LEFT,InventoryAction.PICKUP_ALL);
            check(Arrays.equals(denied,storageSnapshot(player)),"non-OP cannot roll rewards through a stale admin Vault view");
            gui.openVaultTests(player,DungeonLayout.Kind.SANCTUM_DARK);
            check(player.getOpenInventory().getTopInventory().getSize()!=45,"non-admin cannot reopen the Vault test submenu");
            check(drops.equals(droppedItems(player)),"Vault GUI rolls and full-inventory failures never drop items into the world");
            check(ledgerBefore.equals(ledger.saveToString()),"test rolls never alter in-memory claims or dungeon cooldowns");
            check(Arrays.equals(ledgerBytes,Files.exists(ledgerPath)?Files.readAllBytes(ledgerPath):null),"test rolls never write a real Vault claim or cooldown to disk");
            audit.append("GUI coverage: registered click/drag events; 3 themes x 5 tiers x left/right/shift-left/shift-right; actual-version labels, per-tier committed batch/category counts matched against inventory; inert stats/previews, atomic full-inventory and permission guards\n");
        }finally{
            player.closeInventory();if(permission!=null)player.removeAttachment(permission);player.getInventory().setStorageContents(saved);
        }
    }
}
