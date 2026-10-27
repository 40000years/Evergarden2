import com.example.advancemagic.AdvanceMagicPlugin;
import com.example.advancemagic.elemental.ElementalSpell;
import com.example.advancemagic.elemental.StaffSpellService;
import com.example.advancemagic.item.ElementalStaffService.Element;
import com.example.voidscape.VoidscapePlugin;
import com.example.voidscape.item.RelicService;
import com.example.voidscape.item.VaultLootTable;
import com.example.voidscape.world.*;
import org.bukkit.*;
import org.bukkit.block.Chest;
import org.bukkit.inventory.*;
import org.bukkit.loot.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;

/** Real placed chests, chunk PDC, vanilla loot tables and both enabled production plugins. */
public final class ElementalTreasureChecks extends JavaPlugin {
    private AdvanceMagicPlugin magic;
    private VoidscapePlugin garden;
    private int passed;
    private World world;
    private static final List<String> PROFILES=List.of("exploration","landmark","boss","sanctuary");
    @Override public void onEnable(){Bukkit.getScheduler().runTaskLater(this,()->{
        try{run();finish(null);}catch(Throwable error){finish(error);}
    },20);}
    private void check(boolean value,String label){if(!value)throw new AssertionError(label);passed++;getLogger().info("PASS "+label);}
    private void finish(Throwable error){
        if(error!=null)getLogger().log(java.util.logging.Level.SEVERE,"ELEMENTAL TREASURE FAILED",error);
        try{Files.writeString(Path.of("elemental-treasure-result.txt"),(error==null?"PASS ":"FAIL ")+passed+" checks; actual Paper, Advance Magic and Evergarden; "+(error==null?"58-spell pools, exact staff chances, all eight blank staff types, Vault rewards, deterministic one-time chests, three sky structures and lazy sanctuary loot":error));}
        catch(Exception failure){getLogger().log(java.util.logging.Level.SEVERE,"Could not write result",failure);}
        Bukkit.getScheduler().runTaskLater(this,Bukkit::shutdown,1);
    }
    private void run()throws Exception {
        magic=(AdvanceMagicPlugin)Bukkit.getPluginManager().getPlugin("advance-magic");
        garden=(VoidscapePlugin)Bukkit.getPluginManager().getPlugin("Evergarden");
        check(magic!=null&&magic.isEnabled()&&garden!=null&&garden.isEnabled(),"both production plugins enabled together");
        if(Boolean.getBoolean("elemental.compat"))for(String dependency:List.of("Geyser-Spigot","floodgate")){
            var plugin=Bukkit.getPluginManager().getPlugin(dependency);check(plugin!=null&&plugin.isEnabled(),dependency+" enabled alongside both changed plugins");
        }
        world=Bukkit.getWorlds().getFirst();
        for(String profile:PROFILES)magic.getConfig().set("elemental-system.scroll-loot."+profile+".chance",1.0);
        pools();
        helper();
        exploration();
        staffRolls();
        staffChests();
        vault();
        skyStaffs();
        sanctuary();
    }
    private static final class Draw extends Random {
        private final double rarity;private final int index;private int calls;
        Draw(double rarity,int index){this.rarity=rarity;this.index=index;}
        @Override public double nextDouble(){return calls++==0?0:rarity;}
        @Override public int nextInt(int bound){if(index<0||index>=bound)throw new AssertionError("spell pool index outside bound");return index;}
    }
    private void pools(){
        Set<ElementalSpell> all=EnumSet.noneOf(ElementalSpell.class);
        for(String profile:PROFILES){
            double[] fallback=profile.equals("boss")||profile.equals("sanctuary")?new double[]{25,35,30,10}:profile.equals("landmark")?new double[]{45,35,17,3}:new double[]{65,28,6,1};
            double total=0;double[] weights=new double[4];
            for(int index=0;index<4;index++){
                var rarity=ElementalSpell.Rarity.values()[index];
                weights[index]=magic.getConfig().getDouble("elemental-system.scroll-loot."+profile+".rarity-weights."+rarity.name().toLowerCase(Locale.ROOT),fallback[index]);
                check(weights[index]>0&&Double.isFinite(weights[index]),profile+" permits "+rarity+" scrolls");total+=weights[index];
            }
            double cumulative=0;Set<ElementalSpell> obtained=EnumSet.noneOf(ElementalSpell.class);
            for(int index=0;index<4;index++){
                var rarity=ElementalSpell.Rarity.values()[index];var pool=Arrays.stream(ElementalSpell.values()).filter(spell->spell.rarity()==rarity).toList();
                double sample=(cumulative+weights[index]/2)/total;cumulative+=weights[index];
                for(int spellIndex=0;spellIndex<pool.size();spellIndex++){
                    var item=magic.acquisition().rollScroll(profile,new Draw(sample,spellIndex));
                    check(magic.staffSpells().scroll(item)==pool.get(spellIndex),profile+" obtains "+pool.get(spellIndex).id()+" via weighted loot API");
                    obtained.add(pool.get(spellIndex));
                }
            }
            check(obtained.size()==58,profile+" can yield all 58 distinct spells");all.addAll(obtained);
            ItemStack first=magic.acquisition().rollScroll(profile,new Random(928147));
            check(Objects.equals(first,magic.acquisition().rollScroll(profile,new Random(928147))),profile+" fixed-seed rolls are deterministic");
        }
        check(all.size()==58,"all eight elements obtainable through structure treasure API");
        String chance="elemental-system.scroll-loot.exploration.chance";
        magic.getConfig().set(chance,0.0);check(magic.acquisition().rollScroll("exploration",new Random(1))==null,"zero chance yields no scroll");
        magic.getConfig().set(chance,Double.NaN);check(magic.acquisition().rollScroll("exploration",new Random(1))==null,"non-finite chance is safely rejected");
        magic.getConfig().set(chance,1.0);
    }
    private Chest chest(World target,int x,int y,int z){
        target.getBlockAt(x,y,z).setType(Material.AIR,false);target.getBlockAt(x,y,z).setType(Material.CHEST,false);
        Chest chest=(Chest)target.getBlockAt(x,y,z).getState();chest.setLootTable(null);chest.update(false,false);chest.getBlockInventory().clear();return chest;
    }
    private NamespacedKey marker(Chest chest){return new NamespacedKey("evergarden","elemental_scroll_"+chest.getX()+"_"+chest.getY()+"_"+chest.getZ());}
    private long seed(Chest chest){return chest.getWorld().getSeed()^((long)chest.getX()*341873128712L)^((long)chest.getZ()*132897987541L)^((long)chest.getY()*7193)^0x5343524f4c4cL;}
    private long count(Inventory inventory){return Arrays.stream(inventory.getContents()).filter(magic.staffSpells()::isScroll).count();}
    private long staffCount(Inventory inventory){return Arrays.stream(inventory.getContents()).filter(magic.staffs()::isStaff).count();}
    private NamespacedKey staffMarker(Chest chest){return new NamespacedKey("evergarden","elemental_staff_"+chest.getX()+"_"+chest.getY()+"_"+chest.getZ());}
    private long staffSeed(Chest chest,String source){return DungeonLayout.mix(chest.getWorld().getSeed()^((long)chest.getX()*341873128712L)^((long)chest.getZ()*132897987541L)^((long)chest.getY()*7193L)^0x53544146464cL^source.hashCode());}
    private void blankStaff(ItemStack item,Element element,String label){
        check(item!=null&&item.getAmount()==1&&magic.staffs().element(item)==element,label+" has the selected element and amount one");
        check(magic.staffSpells().tier(item)==StaffSpellService.Tier.APPRENTICE&&magic.staffSpells().tier(item).slots()==3,label+" starts with three Apprentice slots");
        check(magic.staffSpells().spells(item).isEmpty()&&magic.staffSpells().selected(item)==null,label+" has no pre-imprinted spells");
        check(item.getItemMeta().getPersistentDataContainer().has(new NamespacedKey("advance_magic","spell_staff_id"),PersistentDataType.STRING),label+" has a persistent staff identity");
    }
    /** Deliberate branch samples, not statistical estimates of rare drop chances. */
    private static final class SourceRandom extends Random {
        private final double chance;private final int[] draws;private int index;
        SourceRandom(double chance,int... draws){this.chance=chance;this.draws=draws;}
        @Override public double nextDouble(){return chance;}
        @Override public int nextInt(int bound){if(index>=draws.length)throw new AssertionError("Unexpected RNG draw with bound "+bound);int value=draws[index++];if(value<0||value>=bound)throw new AssertionError("RNG draw "+value+" outside "+bound);return value;}
    }
    private void staffRolls()throws Exception {
        String[] sources={"vault","sky-chest","overworld-chest"};double[] chances={.10,.03,.01};
        for(int index=0;index<sources.length;index++){
            String source=sources[index],path="staff-loot."+source+"-chance";double chance=chances[index];
            check(Double.compare(garden.getConfig().getDouble(path),chance)==0,source+" defaults to the requested "+chance+" probability");
            for(Element element:Element.values())blankStaff(ElementalStaffTreasure.roll(garden,source,new SourceRandom(Math.nextDown(chance),element.ordinal())),element,source+" uniform pool "+element);
            check(ElementalStaffTreasure.roll(garden,source,new SourceRandom(chance))==null,source+" excludes the exact chance boundary");
            check(ElementalStaffTreasure.roll(garden,source,new SourceRandom(.999999))==null,source+" rejects rolls above its chance");
            garden.getConfig().set(path,0.0);check(ElementalStaffTreasure.roll(garden,source,new SourceRandom(0))==null,source+" zero chance disables rewards");
            garden.getConfig().set(path,1.0);blankStaff(ElementalStaffTreasure.roll(garden,source,new SourceRandom(.999999,7)),Element.DARK,source+" configurable full chance");
            garden.getConfig().set(path,Double.NaN);check(ElementalStaffTreasure.roll(garden,source,new SourceRandom(chance))==null,source+" invalid chance falls back to its safe default");
            garden.getConfig().set(path,chance);
        }
        garden.getConfig().set("staff-loot.enabled",false);
        check(ElementalStaffTreasure.roll(garden,"vault",new SourceRandom(0))==null,"global staff loot switch disables the reward branch");
        garden.getConfig().set("staff-loot.enabled",true);
        boolean invalid=false;try{ElementalStaffTreasure.roll(garden,"ordinary-player-chest",new SourceRandom(0));}catch(IllegalArgumentException expected){invalid=true;}
        check(invalid,"unsupported chest sources cannot silently obtain staff rolls");
        Field factory=AdvanceMagicPlugin.class.getDeclaredField("staffSpells");factory.setAccessible(true);Object original=factory.get(magic);
        try{factory.set(magic,null);check(ElementalStaffTreasure.roll(garden,"vault",new SourceRandom(0))==null,"missing staff factory safely declines staff rewards");}
        finally{factory.set(magic,original);}
    }
    private void staffChests()throws Exception {
        garden.getConfig().set("staff-loot.overworld-chest-chance",1.0);garden.getConfig().set("staff-loot.sky-chest-chance",1.0);
        Chest first=chest(world,62,100,30);ItemStack original=new ItemStack(Material.DIAMOND,7);first.getBlockInventory().setItem(0,original);
        ItemStack expected=ElementalStaffTreasure.roll(garden,"overworld-chest",new Random(staffSeed(first,"overworld-chest")));
        ElementalStaffTreasure.add(garden,first,"overworld-chest");
        check(first.getBlockInventory().getItem(0).equals(original),"staff insertion preserves existing occupied slots");
        blankStaff(first.getBlockInventory().getItem(1),magic.staffs().element(expected),"seeded normal-world chest staff");
        check(staffCount(first.getBlockInventory())==1&&first.getChunk().getPersistentDataContainer().has(staffMarker(first),PersistentDataType.BYTE),"staff insertion records one durable chunk attempt");
        ItemStack[] once=first.getBlockInventory().getContents();ElementalStaffTreasure.add(garden,first,"overworld-chest");
        check(Arrays.equals(once,first.getBlockInventory().getContents()),"repeated staff hook never rerolls or duplicates");
        first.getBlockInventory().clear();ElementalStaffTreasure.add(garden,first,"overworld-chest");check(first.getBlockInventory().isEmpty(),"looted staff chest stays empty");
        Chest replaced=chest(world,62,100,30);ElementalStaffTreasure.add(garden,replaced,"overworld-chest");check(replaced.getBlockInventory().isEmpty(),"replaced chest cannot reset its staff attempt");
        Chest full=chest(world,66,100,30);for(int slot=0;slot<27;slot++)full.getBlockInventory().setItem(slot,new ItemStack(Material.COBBLESTONE,64));
        ItemStack[] fullBefore=full.getBlockInventory().getContents();ElementalStaffTreasure.add(garden,full,"overworld-chest");
        check(Arrays.equals(fullBefore,full.getBlockInventory().getContents()),"full chests never lose loot to a staff insertion");
        full.getBlockInventory().setItem(0,null);ElementalStaffTreasure.add(garden,full,"overworld-chest");check(full.getBlockInventory().getItem(0)==null,"freeing a slot cannot claim a deferred staff");
        Chest unlucky=chest(world,70,100,30);garden.getConfig().set("staff-loot.overworld-chest-chance",0.0);ElementalStaffTreasure.add(garden,unlucky,"overworld-chest");garden.getConfig().set("staff-loot.overworld-chest-chance",1.0);ElementalStaffTreasure.add(garden,unlucky,"overworld-chest");
        check(unlucky.getBlockInventory().isEmpty(),"failed staff roll remains final when chance is increased later");
        Chest disabled=chest(world,74,100,30);garden.getConfig().set("staff-loot.enabled",false);ElementalStaffTreasure.add(garden,disabled,"overworld-chest");garden.getConfig().set("staff-loot.enabled",true);ElementalStaffTreasure.add(garden,disabled,"overworld-chest");
        check(disabled.getBlockInventory().isEmpty(),"chests authored while staff loot is disabled are not backfilled later");
        Chest wrongSky=chest(world,78,100,30);ElementalStaffTreasure.add(garden,wrongSky,"sky-chest");
        check(wrongSky.getBlockInventory().isEmpty()&&!wrongSky.getChunk().getPersistentDataContainer().has(staffMarker(wrongSky)),"sky staff hook ignores normal-world chests without stamping them");
        Chest wrongNormal=chest(garden.world(),82,100,30);ElementalStaffTreasure.add(garden,wrongNormal,"overworld-chest");
        check(wrongNormal.getBlockInventory().isEmpty()&&!wrongNormal.getChunk().getPersistentDataContainer().has(staffMarker(wrongNormal)),"normal-world staff hook excludes the Evergarden dimension");
        Field factory=AdvanceMagicPlugin.class.getDeclaredField("staffSpells");factory.setAccessible(true);Object originalFactory=factory.get(magic);Chest absent=chest(world,86,100,30);
        try{factory.set(magic,null);ElementalStaffTreasure.add(garden,absent,"overworld-chest");}
        finally{factory.set(magic,originalFactory);}
        ElementalStaffTreasure.add(garden,absent,"overworld-chest");check(absent.getBlockInventory().isEmpty(),"authoring with an unavailable factory does not create deferred treasure");
        garden.getConfig().set("staff-loot.overworld-chest-chance",.01);garden.getConfig().set("staff-loot.sky-chest-chance",.03);
    }
    private void vault()throws Exception {
        for(Element element:Element.values())blankStaff(garden.relics().rollVaultReward(new SourceRandom(Math.nextDown(.10),element.ordinal())),element,"actual Vault staff reward "+element);
        int[] tickets={0,1499,1500,2699,2700,3199,3200,9049,9050,9199,9200,9249};
        for(int ticket:tickets){
            ItemStack reward=garden.relics().rollVaultReward(new SourceRandom(.10,ticket,0));
            check(reward!=null&&!magic.staffs().isStaff(reward),"Vault misses staff at exact 10% boundary for original ticket "+ticket);
            var category=VaultLootTable.activeReward(ticket);
            boolean matches=switch(category){
                case LIMIT_BREAK -> garden.relics().type(reward)==RelicService.Relic.SCROLL_LIMIT_BREAK;
                case UNIQUE_SCROLL -> garden.relics().type(reward)==RelicService.Relic.SCROLL_UNIQUE;
                case EQUIPMENT -> garden.relics().type(reward)==RelicService.Relic.RIFT_PICKAXE;
                case CONSUMABLE -> magic.manaPotions().quality(reward)==com.example.advancemagic.mana.ManaPotionService.Quality.MEDIUM;
                case FLYING_STAFF -> magic.flyingStaff().isStaff(reward);
                case SCROLL_ETERNITY -> garden.relics().type(reward)==RelicService.Relic.SCROLL_ETERNITY;
                default -> false;
            };
            check(matches,"staff miss retains original Vault category "+category+" at ticket "+ticket);
        }
        garden.getConfig().set("staff-loot.enabled",false);ItemStack disabled=garden.relics().rollVaultReward(new SourceRandom(0,0,0));garden.getConfig().set("staff-loot.enabled",true);
        check(garden.relics().type(disabled)==RelicService.Relic.SCROLL_LIMIT_BREAK,"disabled staff loot preserves the useful original Vault reward");
        Field factory=AdvanceMagicPlugin.class.getDeclaredField("staffSpells");factory.setAccessible(true);Object original=factory.get(magic);ItemStack absent;
        try{factory.set(magic,null);absent=garden.relics().rollVaultReward(new SourceRandom(0,0,0));}finally{factory.set(magic,original);}
        check(garden.relics().type(absent)==RelicService.Relic.SCROLL_LIMIT_BREAK,"missing staff factory falls back to the original Vault reward");
    }
    @SuppressWarnings("unchecked") private void skyStaffs()throws Exception {
        World target=garden.world();WhaleTreasure production=new WhaleTreasure(garden);
        Field whaleCacheField=SkyWhaleLayout.class.getDeclaredField("cache");whaleCacheField.setAccessible(true);
        Map<Long,Optional<SkyWhaleLayout.Site>> whaleCache=(Map<Long,Optional<SkyWhaleLayout.Site>>)whaleCacheField.get(garden.skyWhales());
        Field landmarkCacheField=SkyLandmarkLayout.class.getDeclaredField("cache");landmarkCacheField.setAccessible(true);
        Map<Long,List<SkyLandmarkLayout.Site>> landmarkCache=(Map<Long,List<SkyLandmarkLayout.Site>>)landmarkCacheField.get(garden.skyLandmarks());
        Method roll=WhaleTreasure.class.getDeclaredMethod("roll",Random.class);roll.setAccessible(true);
        garden.getConfig().set("staff-loot.sky-chest-chance",1.0);garden.getConfig().set("structures.treasure.second-chest-chance",0.0);
        for(String kind:List.of("whale","observatory","hanging-garden"))garden.getConfig().set("structures.treasure.flying-staff-chances."+kind,0.0);
        for(int test=0;test<3;test++){
            boolean whale=test==0;SkyLandmarkLayout.Kind kind=test==1?SkyLandmarkLayout.Kind.OBSERVATORY:SkyLandmarkLayout.Kind.HANGING_GARDEN;
            String name=whale?"whale":kind.id;int x=256+test*64,z=256+test*64,y=whale?115:kind.chestY,dz=whale?7:kind.chestZ;
            int[] offsets=whale?new int[]{-52,-50}:new int[]{4,6};
            Chunk chunk=target.getChunkAt(Math.floorDiv(x+offsets[0],16),Math.floorDiv(z+dz,16));
            for(int offset:offsets){
                target.getBlockAt(x+offset,y,z+dz).setType(Material.AIR,false);target.getBlockAt(x+offset,y+1,z+dz).setType(Material.AIR,false);
                target.getBlockAt(x+offset,y-1,z+dz).setType(Material.SPRUCE_PLANKS,false);target.getBlockAt(x+offset,y,z+dz+1).setType(Material.BOOKSHELF,false);
            }
            long whaleCell=SkyPlacementHistory.key(Math.floorDiv(chunk.getX(),garden.skyWhales().spacingChunks()),Math.floorDiv(chunk.getZ(),garden.skyWhales().spacingChunks()));
            long landmarkCell=SkyPlacementHistory.key(Math.floorDiv(chunk.getX(),garden.skyLandmarks().spacingChunks()),Math.floorDiv(chunk.getZ(),garden.skyLandmarks().spacingChunks()));
            whaleCache.put(whaleCell,whale?Optional.of(new SkyWhaleLayout.Site(x,z)):Optional.empty());
            landmarkCache.put(landmarkCell,whale?List.of():List.of(new SkyLandmarkLayout.Site(kind,x,z)));
            chunk.getPersistentDataContainer().remove(garden.key(name+"_treasure_v1"));
            if(whale)chunk.getPersistentDataContainer().remove(garden.key("whale_wand_upgrades_v2"));
            long seed=whale?DungeonLayout.mix(target.getSeed()^((long)x*341873128712L)^((long)z*132897987541L)^0x5748414c454cL)
                    :DungeonLayout.mix(target.getSeed()^((long)x*341873128712L)^((long)z*132897987541L)^0x5452454153555245L^kind.ordinal());
            Random random=new Random(seed);random.nextInt(100);int firstOffset=random.nextBoolean()?offsets[0]:offsets[1];
            List<ItemStack> originalLoot=(List<ItemStack>)roll.invoke(production,random);List<Integer> slots=new ArrayList<>();for(int slot=0;slot<27;slot++)slots.add(slot);Collections.shuffle(slots,random);
            Inventory expected=Bukkit.createInventory(null,27);for(int index=0;index<originalLoot.size();index++)expected.setItem(slots.get(index),originalLoot.get(index));
            production.populate(chunk);
            check(target.getBlockAt(x+firstOffset,y,z+dz).getState() instanceof Chest,"actual "+name+" authoring places its expected first chest");
            Chest generated=(Chest)target.getBlockAt(x+firstOffset,y,z+dz).getState();
            check(staffCount(generated.getBlockInventory())==1,"actual "+name+" newly authored chest calls staff drop integration");
            ItemStack expectedStaff=ElementalStaffTreasure.roll(garden,"sky-chest",new Random(staffSeed(generated,"sky-chest")));
            ItemStack actualStaff=Arrays.stream(generated.getBlockInventory().getContents()).filter(magic.staffs()::isStaff).findFirst().orElseThrow();
            blankStaff(actualStaff,magic.staffs().element(expectedStaff),"actual "+name+" seeded staff");
            boolean originalPreserved=true;for(int slot=0;slot<27;slot++)if(expected.getItem(slot)!=null&&!expected.getItem(slot).equals(generated.getBlockInventory().getItem(slot)))originalPreserved=false;
            check(originalPreserved,"actual "+name+" staff hook preserves original loot RNG and occupied slot positions");
            generated.getBlockInventory().clear();production.populate(chunk);check(generated.getBlockInventory().isEmpty(),"actual "+name+" emptied chest is never refilled with staffs");
            generated.getChunk().getPersistentDataContainer().remove(staffMarker(generated));production.populate(chunk);
            check(generated.getBlockInventory().isEmpty()&&!generated.getChunk().getPersistentDataContainer().has(staffMarker(generated)),"previously awarded "+name+" chest never receives a first-time staff backfill");
        }
        garden.getConfig().set("staff-loot.sky-chest-chance",.03);
    }
    private void helper(){
        Chest first=chest(world,30,100,30);ItemStack original=new ItemStack(Material.DIAMOND,7);first.getBlockInventory().setItem(0,original);
        ItemStack expected=magic.acquisition().rollScroll("boss",new Random(seed(first)));
        ElementalScrollTreasure.add(first,"boss");
        check(first.getBlockInventory().getItem(0).equals(original),"scroll insertion preserves occupied loot slots");
        check(Objects.equals(first.getBlockInventory().getItem(1),expected),"chest position and world seed choose the expected deterministic scroll");
        check(count(first.getBlockInventory())==1&&first.getChunk().getPersistentDataContainer().has(marker(first),PersistentDataType.BYTE),"scroll attempt durably stamped on chunk");
        ItemStack[] once=first.getBlockInventory().getContents();ElementalScrollTreasure.add(first,"sanctuary");
        check(Arrays.equals(once,first.getBlockInventory().getContents()),"repeated hook with another profile does not reroll or duplicate");
        first.getBlockInventory().clear();ElementalScrollTreasure.add(first,"boss");
        check(first.getBlockInventory().isEmpty(),"looted chest stays empty on repeated hook");
        Chest replaced=chest(world,30,100,30);ElementalScrollTreasure.add(replaced,"boss");
        check(replaced.getBlockInventory().isEmpty(),"removed and replaced chest cannot defeat chunk marker");
        Chest full=chest(world,34,100,30);for(int slot=0;slot<27;slot++)full.getBlockInventory().setItem(slot,new ItemStack(Material.COBBLESTONE,64));
        ItemStack[] before=full.getBlockInventory().getContents();ElementalScrollTreasure.add(full,"boss");
        check(Arrays.equals(before,full.getBlockInventory().getContents()),"full inventory loot is never overwritten");
        full.getBlockInventory().setItem(0,null);ElementalScrollTreasure.add(full,"boss");
        check(full.getBlockInventory().getItem(0)==null,"making room after full-chest attempt does not create deferred rewards");
        Chest unlucky=chest(world,38,100,30);String path="elemental-system.scroll-loot.exploration.chance";
        magic.getConfig().set(path,0.0);ElementalScrollTreasure.add(unlucky,"exploration");magic.getConfig().set(path,1.0);ElementalScrollTreasure.add(unlucky,"exploration");
        check(unlucky.getBlockInventory().isEmpty(),"unsuccessful chance roll is final for that chest");
    }
    @SuppressWarnings("unchecked") private void exploration()throws Exception {
        var layout=garden.exploration();World target=garden.world();
        var site=new ExplorationLayout.Site(ExplorationLayout.Kind.BLOSSOM_PAVILION,48,100,48,0,834276L);
        Field cacheField=ExplorationLayout.class.getDeclaredField("cache");cacheField.setAccessible(true);
        Map<Long,Optional<ExplorationLayout.Site>> cache=(Map<Long,Optional<ExplorationLayout.Site>>)cacheField.get(layout);
        long cell=SkyPlacementHistory.key(Math.floorDiv(site.x(),256),Math.floorDiv(site.z(),256));cache.put(cell,Optional.of(site));
        var point=site.chestLocations().getFirst();var xz=site.transform(point.x(),point.z());int y=site.floorY()+point.y();
        Chest chest=chest(target,xz[0],y,xz[1]);chest.getBlock().getRelative(0,-1,0).setType(Material.CHISELED_STONE_BRICKS,false);chest.getBlock().getRelative(0,1,0).setType(Material.AIR,false);
        Chunk chunk=chest.getChunk();chunk.getPersistentDataContainer().remove(garden.key("exploration_treasure_v1"));
        chunk.getPersistentDataContainer().remove(garden.key("exploration_chest_"+xz[0]+"_"+y+"_"+xz[1]));chunk.getPersistentDataContainer().remove(marker(chest));
        ExplorationTreasure production=new ExplorationTreasure(garden,layout);
        var random=new Random(DungeonLayout.mix(target.getSeed()^site.variant()^0x534d414c4c4c4f54L));
        var loot=production.roll(random,site.kind().biome(),site.kind().medium());
        List<Integer> slots=new ArrayList<>();for(int slot=0;slot<27;slot++)slots.add(slot);Collections.shuffle(slots,random);
        Inventory expected=Bukkit.createInventory(null,27);for(int index=0;index<loot.size();index++)expected.setItem(slots.get(index),loot.get(index));
        production.populate(chunk);
        check(count(chest.getBlockInventory())==1,"actual ExplorationTreasure.populate calls elemental chest integration");
        check(staffCount(chest.getBlockInventory())==0,"ordinary Evergarden exploration chest is outside the requested sky staff sources");
        ItemStack[] actual=chest.getBlockInventory().getContents();for(int slot=0;slot<actual.length;slot++)if(magic.staffSpells().isScroll(actual[slot]))actual[slot]=null;
        check(Arrays.equals(expected.getContents(),actual),"exploration supply RNG and original slot order are unchanged");
        chest.getBlockInventory().clear();production.populate(chunk);check(chest.getBlockInventory().isEmpty(),"actual exploration fill marker prevents repeat treasure");
    }
    private void sanctuary()throws Exception {
        garden.getConfig().set("staff-loot.overworld-chest-chance",1.0);
        var prepare=OverworldSanctuaries.class.getDeclaredMethod("prepareLoot",Chest.class);prepare.setAccessible(true);
        Chest cache=chest(world,42,100,30);long lootSeed=835749331L;
        cache.setLootTable(LootTables.END_CITY_TREASURE.getLootTable(),lootSeed);
        cache.getPersistentDataContainer().set(garden.key("sanctuary_loot_profile"),PersistentDataType.INTEGER,2);
        cache.getPersistentDataContainer().set(garden.key("sanctuary_loot_seed"),PersistentDataType.LONG,lootSeed);cache.update(false,false);
        garden.getConfig().set("overworld-sanctuaries.treasure.over-cap-chance",0.0);
        Inventory expected=Bukkit.createInventory(null,27);LootTables.END_CITY_TREASURE.getLootTable().fillInventory(expected,new Random(lootSeed),new LootContext.Builder(cache.getLocation()).build());SanctuaryLoot.replaceTrims(expected,lootSeed);
        prepare.invoke(garden.overworldSanctuaries(),(Chest)cache.getBlock().getState());
        Chest filled=(Chest)cache.getBlock().getState();ItemStack[] actual=filled.getBlockInventory().getContents();
        check(!filled.getBlockInventory().isEmpty(),"sanctuary lazy vanilla loot is materialized");
        check(count(filled.getBlockInventory())==1,"first sanctuary preparation adds one elemental scroll");
        check(staffCount(filled.getBlockInventory())==1,"first normal-world sanctuary preparation calls staff chest integration");
        ItemStack expectedStaff=ElementalStaffTreasure.roll(garden,"overworld-chest",new Random(staffSeed(filled,"overworld-chest")));
        blankStaff(Arrays.stream(actual).filter(magic.staffs()::isStaff).findFirst().orElseThrow(),magic.staffs().element(expectedStaff),"actual sanctuary staff");
        for(int slot=0;slot<actual.length;slot++)if(magic.staffSpells().isScroll(actual[slot])||magic.staffs().isStaff(actual[slot]))actual[slot]=null;
        check(Arrays.equals(expected.getContents(),actual),"sanctuary vanilla seeded loot and trim replacement remain intact");
        ItemStack[] once=filled.getBlockInventory().getContents();prepare.invoke(garden.overworldSanctuaries(),filled);
        check(Arrays.equals(once,filled.getBlockInventory().getContents()),"sanctuary opening twice never rerolls contents");
        filled.getBlockInventory().clear();prepare.invoke(garden.overworldSanctuaries(),(Chest)filled.getBlock().getState());
        check(filled.getBlockInventory().isEmpty(),"looted sanctuary cache is not refilled");
        Chest old=chest(world,46,100,30);old.getBlockInventory().setItem(0,new ItemStack(Material.STICK,3));
        old.getPersistentDataContainer().set(garden.key("sanctuary_loot_profile"),PersistentDataType.INTEGER,2);
        old.getPersistentDataContainer().set(garden.key("sanctuary_loot_awarded"),PersistentDataType.INTEGER,2);
        old.getPersistentDataContainer().set(garden.key("sanctuary_trim_profile"),PersistentDataType.INTEGER,1);old.update(false,false);
        ItemStack[] oldContents=old.getBlockInventory().getContents();prepare.invoke(garden.overworldSanctuaries(),(Chest)old.getBlock().getState());
        check(Arrays.equals(oldContents,old.getBlockInventory().getContents())&&count(old.getBlockInventory())==0&&staffCount(old.getBlockInventory())==0,"previously awarded sanctuary player contents receive no scroll or staff backfill");
        garden.getConfig().set("staff-loot.overworld-chest-chance",.01);
    }
}
