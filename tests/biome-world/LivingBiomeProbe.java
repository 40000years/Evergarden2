import com.example.voidscape.VoidscapePlugin;
import com.example.voidscape.world.*;
import org.bukkit.*;
import org.bukkit.block.Chest;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.generator.ChunkGenerator.ChunkData;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import java.nio.file.*;
import java.util.*;

/** Checks real generated destinations, native enchants, chunk seams and a second server boot. */
public final class LivingBiomeProbe extends JavaPlugin {
    private VoidscapePlugin plugin;private World world;private VoidGenerator generator;private int checks;
    private final List<String> inaccessible=new ArrayList<>();
    private void check(boolean condition,String text){if(!condition)throw new AssertionError(text);checks++;getLogger().info("PASS "+text);}
    @Override public void onEnable(){Bukkit.getScheduler().runTaskLater(this,()->{
        try{
            plugin=(VoidscapePlugin)Bukkit.getPluginManager().getPlugin("Evergarden");world=plugin.world();generator=(VoidGenerator)world.getGenerator();
            world.setGameRule(GameRule.RANDOM_TICK_SPEED,0);
            if(Files.exists(Path.of("living-export-only")))exportOnly();
            else if(Files.exists(Path.of("living-checkpoint.txt")))restart();else first();
            Files.writeString(Path.of("living-result.txt"),"PASS "+checks+" living biome checks\n");
        }catch(Throwable error){getLogger().log(java.util.logging.Level.SEVERE,"LIVING BIOMES FAILED",error);
            try{Files.writeString(Path.of("living-result.txt"),"FAIL "+error);}catch(Exception ignored){}}
        finally{Bukkit.getScheduler().runTaskLater(this,Bukkit::shutdown,5);}
    },40);}
    private ChunkData raw(int cx,int cz,long random){var data=Bukkit.getServer().createChunkData(world);generator.generateNoise(world,new Random(random),cx,cz,data);return data;}
    private Material sample(Map<Long,ChunkData> chunks,int x,int y,int z){
        int cx=Math.floorDiv(x,16),cz=Math.floorDiv(z,16);var data=chunks.computeIfAbsent(SkyPlacementHistory.key(cx,cz),k->raw(cx,cz,1));
        return data.getType(Math.floorMod(x,16),y,Math.floorMod(z,16));
    }
    private String loot(Chest chest){
        var result=new ArrayList<String>();for(var item:chest.getBlockInventory().getContents())
            result.add(item==null?"empty":Base64.getEncoder().encodeToString(item.serializeAsBytes()));return String.join(";",result);
    }
    private boolean gear(ItemStack item){return item.hasItemMeta()&&item.getItemMeta().getPersistentDataContainer().has(plugin.key("exploration_gear_v2"),PersistentDataType.STRING);}
    private static boolean passable(Material material){return material.isAir()||material.name().endsWith("_CARPET")||material.name().endsWith("_DOOR")||material==Material.RAIL
        ||Set.of(Material.FERN,Material.SHORT_GRASS,Material.ALLIUM,Material.PINK_TULIP,Material.WHITE_TULIP,Material.BLUE_ORCHID).contains(material);}
    private org.bukkit.block.data.BlockData state(Map<Long,ChunkData> chunks,int x,int y,int z){
        var data=chunks.computeIfAbsent(SkyPlacementHistory.key(Math.floorDiv(x,16),Math.floorDiv(z,16)),k->raw(Math.floorDiv(x,16),Math.floorDiv(z,16),1));
        return data.getBlockData(Math.floorMod(x,16),y,Math.floorMod(z,16));
    }
    private void doorsAndBeds(Map<Long,ChunkData> chunks,ExplorationLayout.Site site){
        int doors=0,beds=0;
        for(var v:ExplorationBlueprint.blocks(site.kind())){
            int[] p=site.transform(v.x(),v.z());int y=site.floorY()+v.y();var data=state(chunks,p[0],y,p[1]);
            if(data instanceof org.bukkit.block.data.type.Door door&&door.getHalf()==org.bukkit.block.data.Bisected.Half.BOTTOM){
                var other=state(chunks,p[0],y+1,p[1]);
                if(!(other instanceof org.bukkit.block.data.type.Door top)||top.getHalf()!=org.bukkit.block.data.Bisected.Half.TOP||top.getFacing()!=door.getFacing())throw new AssertionError("Incomplete rotated door "+site.kind()+" "+v);
                for(int sign:new int[]{-1,1}){
                    int x=p[0]+sign*door.getFacing().getModX(),z=p[1]+sign*door.getFacing().getModZ();
                    if(!passable(sample(chunks,x,y,z))||!passable(sample(chunks,x,y+1,z)))inaccessible.add(site.kind().id+": obstructed doorway "+v);
                }
                var lowerBlock=world.getBlockAt(p[0],y,p[1]);var upperBlock=world.getBlockAt(p[0],y+1,p[1]);
                var live=(org.bukkit.block.data.type.Door)lowerBlock.getBlockData();var liveTop=(org.bukkit.block.data.type.Door)upperBlock.getBlockData();
                live.setOpen(true);liveTop.setOpen(true);lowerBlock.setBlockData(live,false);upperBlock.setBlockData(liveTop,false);
                if(!((org.bukkit.block.data.type.Door)lowerBlock.getBlockData()).isOpen())throw new AssertionError("Door cannot open");
                live.setOpen(false);liveTop.setOpen(false);lowerBlock.setBlockData(live,false);upperBlock.setBlockData(liveTop,false);doors++;
            }
            if(data instanceof org.bukkit.block.data.type.Bed bed&&bed.getPart()==org.bukkit.block.data.type.Bed.Part.FOOT){
                var other=state(chunks,p[0]+bed.getFacing().getModX(),y,p[1]+bed.getFacing().getModZ());
                if(!(other instanceof org.bukkit.block.data.type.Bed head)||head.getPart()!=org.bukkit.block.data.type.Bed.Part.HEAD||head.getFacing()!=bed.getFacing())throw new AssertionError("Incomplete rotated bed "+site.kind());beds++;
            }
        }
        check(true,site.kind().id+": "+doors+" real doors open and close; "+beds+" beds have matching rotated halves");
    }
    private void reachable(Map<Long,ChunkData> chunks,ExplorationLayout.Site site){
        record Floor(int x,int y,int z){}
        var queue=new ArrayDeque<Floor>();var visited=new HashSet<Floor>();int[] entry=site.transform(0,site.entryZ());
        queue.add(new Floor(entry[0],site.floorY(),entry[1]));
        while(!queue.isEmpty()){
            var p=queue.remove();if(!visited.add(p))continue;
            for(int[] d:new int[][]{{1,0},{-1,0},{0,1},{0,-1}})for(int dy=-1;dy<=1;dy++){
                int x=p.x()+d[0],y=p.y()+dy,z=p.z()+d[1];
                if(Math.abs(x-site.x())>site.radius()+1||Math.abs(z-site.z())>site.radius()+1||y<site.floorY()-6||y>site.floorY()+site.kind().height())continue;
                var floor=sample(chunks,x,y,z);var n=new Floor(x,y,z);
                if(visited.contains(n)||!floor.isSolid()||floor==Material.CHEST||floor==Material.CAMPFIRE
                    ||floor.name().endsWith("_FENCE")||floor.name().endsWith("_WALL")||floor.name().endsWith("_LEAVES")||floor.name().endsWith("_DOOR")||floor.name().endsWith("_BED"))continue;
                if(passable(sample(chunks,x,y+1,z))&&passable(sample(chunks,x,y+2,z)))queue.add(n);
            }
        }
        for(var spot:site.chestLocations()){
            int[] chest=site.transform(spot.x(),spot.z());boolean reached=false;
            for(int[] d:new int[][]{{1,0},{-1,0},{0,1},{0,-1}})reached|=visited.contains(new Floor(chest[0]+d[0],site.floorY()+spot.y()-1,chest[1]+d[1]));
            String description=site.kind().id+": treasure at "+spot+" is reachable on foot, including raised floors";
            if(!reached)inaccessible.add(description);else check(true,description);
        }
        if(site.kind().medium()){
            var usable=Set.of(Material.CRAFTING_TABLE,Material.SMITHING_TABLE,Material.SMOKER,Material.FURNACE,
                Material.BLAST_FURNACE,Material.CAULDRON,Material.LECTERN,Material.CARTOGRAPHY_TABLE,
                Material.FLETCHING_TABLE,Material.LOOM,Material.STONECUTTER,Material.BREWING_STAND);
            int workstations=0;
            for(var voxel:ExplorationBlueprint.blocks(site.kind())){
                if(!usable.contains(voxel.material()))continue;
                // A bucket suspended from the quarry crane is scenery, not an occupied wash station.
                if(voxel.material()==Material.CAULDRON&&ExplorationBlueprint.blocks(site.kind()).stream().anyMatch(v->
                    v.x()==voxel.x()&&v.z()==voxel.z()&&v.y()==voxel.y()+1&&v.material()==Material.IRON_CHAIN))continue;
                int[] p=site.transform(voxel.x(),voxel.z());boolean reached=false;
                for(int[] d:new int[][]{{1,0},{-1,0},{0,1},{0,-1}})
                    reached|=visited.contains(new Floor(p[0]+d[0],site.floorY()+voxel.y()-1,p[1]+d[1]));
                if(!reached)inaccessible.add(site.kind().id+": unreachable work area "+voxel);
                workstations++;
            }
            check(true,site.kind().id+": inspected "+workstations+" furnished workstations for access from the entrance");
        }
    }
    private void first()throws Exception{
        check(ExplorationLayout.Kind.values().length==32,"twelve small and twenty medium destinations");
        for(var biome:BiomeTerrain.Kind.values())check(ExplorationLayout.Kind.inBiome(biome).size()==8&&ExplorationLayout.Kind.inBiome(biome).stream().filter(ExplorationLayout.Kind::medium).count()==5,biome+": exactly three small and five medium designs");
        for(var kind:ExplorationLayout.Kind.values()){
            var blocks=new HashMap<String,Material>();
            for(var b:ExplorationBlueprint.blocks(kind))blocks.put(b.x()+","+b.y()+","+b.z(),b.material());
            int chestCount=0;
            for(var b:ExplorationBlueprint.blocks(kind)){
                if(b.material()==Material.CHEST)chestCount++;
                if(b.material()!=Material.WATER)continue;
                for(int[] d:new int[][]{{0,-1,0},{1,0,0},{-1,0,0},{0,0,1},{0,0,-1}}){
                    var side=blocks.getOrDefault((b.x()+d[0])+","+(b.y()+d[1])+","+(b.z()+d[2]),Material.AIR);
                    if(side!=Material.WATER&&!side.isSolid())throw new AssertionError("Structure leaking water "+kind+" at "+b+" toward "+Arrays.toString(d)+" ("+side+")");
                }
            }
            check(chestCount==ExplorationBlueprint.chests(kind,5).size()&&chestCount>=(kind.medium()?4:2)&&chestCount<=(kind.medium()?6:3),kind.id+": expected chest count and contained water");
        }
        var sites=new EnumMap<ExplorationLayout.Kind,ExplorationLayout.Site>(ExplorationLayout.Kind.class);
        // Enumerate once, with disabled major structures only in the separate pure placement fixture.
        for(int ring=0;ring<=128&&sites.size()<32;ring++)for(int gx=-ring;gx<=ring;gx++)for(int gz=-ring;gz<=ring;gz++){
            if(ring>0&&Math.abs(gx)!=ring&&Math.abs(gz)!=ring)continue;
            var site=plugin.exploration().cell(gx,gz);if(site!=null)sites.putIfAbsent(site.kind(),site);
        }
        check(sites.size()==32,"production grid finds all thirty-two kinds around positive/negative coordinates: "+sites.keySet());
        Files.writeString(Path.of("preview-sites.txt"),sites.values().stream().map(s->s.kind().id+","+s.x()+","+s.floorY()+","+s.z()+","+s.rotation()).collect(java.util.stream.Collectors.joining("\n")));
        var checkpoint=new ArrayList<String>();Path exports=Path.of("small-structures");Files.createDirectories(exports);
        for(var kind:ExplorationLayout.Kind.values()){
            var s=sites.get(kind);var chunks=new HashMap<Long,ChunkData>();
            int reach=s.radius()+2;
            for(int cx=Math.floorDiv(s.x()-reach,16);cx<=Math.floorDiv(s.x()+reach,16);cx++)
                for(int cz=Math.floorDiv(s.z()-reach,16);cz<=Math.floorDiv(s.z()+reach,16);cz++){
                    var expected=Bukkit.getServer().createChunkData(world);ExplorationBlueprint.render(generator,expected,cx,cz,s);
                    var actual=raw(cx,cz,19);chunks.put(SkyPlacementHistory.key(cx,cz),actual);
                    for(int x=0;x<16;x++)for(int z=0;z<16;z++)for(int y=s.floorY()-7;y<=s.floorY()+s.kind().height();y++){
                        var m=expected.getType(x,y,z);if(m.isAir())continue;
                        if(actual.getType(x,y,z)!=m)throw new AssertionError("Structure overwritten "+kind+" "+cx+","+y+","+cz);
                    }
                    var reverse=raw(cx,cz,987);
                    for(int x=0;x<16;x++)for(int z=0;z<16;z++)for(int y=-64;y<320;y++)
                        if(actual.getType(x,y,z)!=reverse.getType(x,y,z))throw new AssertionError("Order/random dependent "+kind);
                }
            check(true,kind.id+": rotated blocks, foundations and neighboring chunks are deterministic");
            for(int z=s.entryZ();z<=s.radius();z++){
                int[] p=s.transform(0,z);int floor=s.floorY();
                if(z>=s.rampZ()){int[] e=s.transform(0,s.radius());floor+=(int)Math.round((generator.surface(e[0],e[1]).top()-s.floorY())*(z-s.rampZ())/(double)(s.radius()-s.rampZ()));}
                if(!sample(chunks,p[0],floor,p[1]).isSolid()||!sample(chunks,p[0],floor+1,p[1]).isAir()
                    ||!sample(chunks,p[0],floor+2,p[1]).isAir())throw new AssertionError("Blocked entrance "+kind+" step="+z);
            }
            reachable(chunks,s);
            doorsAndBeds(chunks,s);
            for(int chestIndex=0;chestIndex<s.chestLocations().size();chestIndex++){
            var spot=s.chestLocations().get(chestIndex);int[] p=s.transform(spot.x(),spot.z());int chestY=s.floorY()+spot.y();
            var block=world.getBlockAt(p[0],chestY,p[1]);
            check(block.getState() instanceof Chest,kind.id+": live chest exists and can be opened");
            var chest=(Chest)block.getState();check(chest.getBlockInventory().getContents().length==27,"single chest inventory");
            var items=Arrays.stream(chest.getBlockInventory().getContents()).filter(Objects::nonNull).toList();
            long equipment=items.stream().filter(this::gear).count();
            check(equipment<=1&&items.size()>=4&&items.size()<=10,kind.id+": useful supplies with at most one optional equipment item");
            check(!world.getBlockAt(p[0],chestY+1,p[1]).getType().isSolid(),kind.id+": chest lid stays clear");
            var rewards=new ExplorationTreasure(plugin,plugin.exploration());String before=loot(chest);rewards.populate(block.getChunk());
            check(before.equals(loot(chest)),kind.id+": repeated population keeps exact rewards");
            if(kind.ordinal()==0&&chestIndex==0){chest.getBlockInventory().clear();rewards.populate(block.getChunk());check(chest.getBlockInventory().isEmpty(),"empty chest never refills");}
            boolean removed=kind.ordinal()==1&&chestIndex==1;
            if(removed){block.setType(Material.AIR,false);rewards.populate(block.getChunk());check(block.getType().isAir(),"removed secondary chest never returns");}
            checkpoint.add(kind.id+","+s.x()+","+s.floorY()+","+s.z()+","+s.rotation()+","+chestIndex+","+(removed?"removed":loot(chest)));
            }
            export(chunks,s,exports.resolve(kind.id+".csv"));
        }
        if(!inaccessible.isEmpty())throw new AssertionError(String.join("; ",inaccessible));
        // Run many native item rolls, including all three material families and armor slots.
        var rewards=new ExplorationTreasure(plugin,plugin.exploration());var types=new HashSet<Material>();var random=new Random(19471);
        for(int i=0;i<1000;i++)for(var biome:BiomeTerrain.Kind.values()){
            var small=rewards.roll(new Random(i),biome,false);var medium=rewards.roll(new Random(i),biome,true);
            if(small.size()!=medium.size())throw new AssertionError("Medium loot changed item odds");
            boolean more=false;
            for(int slot=0;slot<small.size();slot++){
                var a=small.get(slot);var b=medium.get(slot);
                if(!a.isSimilar(b)||b.getAmount()<a.getAmount())throw new AssertionError("Medium loot changed item quality, enchant or special-drop odds");
                if(gear(a)&&a.getAmount()!=b.getAmount())throw new AssertionError("Medium loot multiplied equipment");
                more|=b.getAmount()>a.getAmount();
            }
            if(!more)throw new AssertionError("Medium chest has no extra supplies");
        }
        check(true,"4000 paired rolls give medium chests more supplies with identical equipment, enchants and rare-drop odds");
        int equipped=0,onlySupplies=0,armorCount=0,toolCount=0,rareCount=0,golden=0,enchantedApple=0,rockets=0;
        for(int i=0;i<5000;i++)for(var biome:BiomeTerrain.Kind.values()){
            var items=rewards.roll(random,biome);var equipment=items.stream().filter(this::gear).toList();
            if(items.stream().noneMatch(item->Set.of(Material.BREAD,Material.BAKED_POTATO,Material.COOKED_BEEF,Material.COOKED_CHICKEN,Material.GOLDEN_CARROT).contains(item.getType())&&item.getAmount()>=2))throw new AssertionError("Missing guaranteed food");
            if(items.stream().noneMatch(item->item.getType().isBlock()&&item.getAmount()>=12))throw new AssertionError("Missing usable building stack");
            for(var item:items){
                if(item.getType()==Material.GOLDEN_APPLE)golden++;
                if(item.getType()==Material.ENCHANTED_GOLDEN_APPLE)enchantedApple++;
                if(item.getType()==Material.FIREWORK_ROCKET){
                    rockets++;var flight=item.getData(io.papermc.paper.datacomponent.DataComponentTypes.FIREWORKS);
                    if(flight==null||flight.flightDuration()!=1||!flight.effects().isEmpty()||item.getAmount()<2||item.getAmount()>6)throw new AssertionError("Invalid flight rocket components");
                }
            }
            if(equipment.isEmpty()){onlySupplies++;continue;}
            if(equipment.size()!=1)throw new AssertionError("Too much equipment");
            var armor=equipment.getFirst();types.add(armor.getType());equipped++;
            if(armor.getType().name().endsWith("HELMET")||armor.getType().name().endsWith("CHESTPLATE")||armor.getType().name().endsWith("LEGGINGS")||armor.getType().name().endsWith("BOOTS"))armorCount++;else toolCount++;
            if(armor.getType().name().startsWith("DIAMOND_")||armor.getType().name().startsWith("NETHERITE_"))throw new AssertionError("Equipment above iron");
            var authorized=plugin.bossEnchants().authorized(armor);if(!authorized.isEmpty())rareCount++;
            if(authorized.size()>1)throw new AssertionError("More than one rare over-cap enchant");
            for(var e:armor.getEnchantments().entrySet()){
                if(!e.getKey().canEnchantItem(armor)||e.getValue()>e.getKey().getMaxLevel()+2)throw new AssertionError("Invalid exploration enchant");
                if(e.getValue()>e.getKey().getMaxLevel()&&authorized.getOrDefault(e.getKey(),0)!=e.getValue())throw new AssertionError("Over-cap enchant missing its actual awarded entitlement");
            }
            for(var a:armor.getEnchantments().keySet())for(var b:armor.getEnchantments().keySet())
                if(a!=b&&(a.conflictsWith(b)||b.conflictsWith(a)))throw new AssertionError("Enchant conflict");
        }
        check(types.size()>=24&&equipped>12000&&equipped<14000&&onlySupplies>6000&&armorCount>4000&&toolCount>7000,
            "20000 rolls mix useful supplies, armor, weapons and tools at or below iron");
        check(rareCount>350&&rareCount<700&&rareCount<equipped*.06,"rare over-cap enchants remain scarce and carry native entitlements: "+rareCount+"/"+equipped);
        check(golden>1100&&golden<1700&&enchantedApple>20&&enchantedApple<85&&rockets>1700&&rockets<2300,"golden apples, very rare enchanted apples and small flight-rocket bundles: "+golden+"/"+enchantedApple+"/"+rockets);
        var config=plugin.getConfig();double gearChance=config.getDouble("structures.exploration.gear-chance",.65),rareChance=config.getDouble("structures.exploration.rare-enchant-chance",.04);
        try{
            config.set("structures.exploration.gear-chance",1.0);config.set("structures.exploration.rare-enchant-chance",0.0);
            for(int i=0;i<256;i++)for(var item:rewards.roll(random,BiomeTerrain.Kind.GARDENS))if(gear(item)&&!plugin.bossEnchants().authorized(item).isEmpty())throw new AssertionError("Disabled rare enchant still awarded");
            check(true,"zero rare-enchant chance awards only ordinary enchant levels");
            config.set("structures.exploration.rare-enchant-chance",1.0);
            for(int i=0;i<256;i++)for(var item:rewards.roll(random,BiomeTerrain.Kind.GARDENS))if(gear(item)&&plugin.bossEnchants().authorized(item).size()!=1)throw new AssertionError("Enabled rare enchant missing");
            check(true,"rare equipment actually exceeds the native ceiling with exactly one scoped entitlement");
        }finally{config.set("structures.exploration.gear-chance",gearChance);config.set("structures.exploration.rare-enchant-chance",rareChance);}
        Files.writeString(Path.of("exploration-loot-survey.txt"),"chests=20000\nequipment="+equipped+"\nsupplies-only="+onlySupplies+"\nrare-enchants="+rareCount+"\ngolden-apples="+golden+"\nenchanted-apples="+enchantedApple+"\nrocket-bundles="+rockets+"\n");
        var legacy=new BiomeTerrain(world.getSeed(),3072,Set.of(),Set.of(SkyPlacementHistory.key(-5,6)));
        check(!legacy.richScenery(-2300,3300,32)&&!legacy.richScenery(-2040,3300,0),"existing scenery regions and their transition apron reject new features");
        check(generator.terrain().richScenery(17000,-14000,24),"fresh remote land enables new scenery");
        if(Files.exists(Path.of("baseline.jar")))comparePreviousRelease();
        var profile=new org.bukkit.configuration.file.YamlConfiguration();profile.load(plugin.getDataFolder().toPath().resolve("world-layout.yml").toFile());
        Files.writeString(Path.of("living-history.txt"),profile.getLongList("scenery.legacy-regions").toString());
        world.save();Files.writeString(Path.of("living-checkpoint.txt"),String.join("\n",checkpoint));
    }
    private void export(Map<Long,ChunkData> chunks,ExplorationLayout.Site s,Path file)throws Exception{
        Path statesFile=file.resolveSibling(file.getFileName().toString().replace(".csv",".states.json"));
        try(var out=Files.newBufferedWriter(file);var states=Files.newBufferedWriter(statesFile)){
            states.write("{");boolean first=true;
            int reach=s.radius()+2;
            for(int x=s.x()-reach;x<=s.x()+reach;x++)for(int z=s.z()-reach;z<=s.z()+reach;z++)for(int y=s.floorY()-9;y<=s.floorY()+s.kind().height();y++){
                var m=sample(chunks,x,y,z);if(m.isAir())continue;
                boolean edge=x==s.x()-reach||x==s.x()+reach||z==s.z()-reach||z==s.z()+reach||y==s.floorY()-9||y==s.floorY()+s.kind().height();
                if(!edge)edge=sample(chunks,x-1,y,z).isAir()||sample(chunks,x+1,y,z).isAir()||sample(chunks,x,y-1,z).isAir()
                    ||sample(chunks,x,y+1,z).isAir()||sample(chunks,x,y,z-1).isAir()||sample(chunks,x,y,z+1).isAir();
                if(edge){
                    String point=(x-s.x())+","+(y-s.floorY())+","+(z-s.z());out.write(point+","+m+"\n");
                    var chunk=chunks.get(SkyPlacementHistory.key(Math.floorDiv(x,16),Math.floorDiv(z,16)));
                    if(!first)states.write(",");first=false;
                    states.write("\""+point+"\":\""+chunk.getBlockData(Math.floorMod(x,16),y,Math.floorMod(z,16)).getAsString()+"\"");
                }
            }
            states.write("}");
        }
    }
    private void restart()throws Exception{
        check(plugin.exploration().chance()==.35,"saved exploration rarity survives edited configuration");
        for(String line:Files.readAllLines(Path.of("living-checkpoint.txt"))){
            var p=line.split(",",7);var kind=ExplorationLayout.Kind.fromId(p[0]);int x=Integer.parseInt(p[1]),y=Integer.parseInt(p[2]),z=Integer.parseInt(p[3]);
            var site=plugin.exploration().cell(Math.floorDiv(x,256),Math.floorDiv(z,256));
            check(site!=null&&site.kind()==kind&&site.x()==x&&site.z()==z&&site.floorY()==y&&site.rotation()==Integer.parseInt(p[4]),kind.id+": placement and rotation survive restart");
            var spot=site.chestLocations().get(Integer.parseInt(p[5]));int[] c=site.transform(spot.x(),spot.z());var block=world.getBlockAt(c[0],site.floorY()+spot.y(),c[1]);
            if(p[6].equals("removed"))check(block.getType().isAir(),"deleted chest survives restart");
            else check(block.getState() instanceof Chest&&loot((Chest)block.getState()).equals(p[6]),kind.id+": inventory and enchantments survive restart exactly");
        }
        var saved=new org.bukkit.configuration.file.YamlConfiguration();saved.load(plugin.getDataFolder().toPath().resolve("world-layout.yml").toFile());
        check(saved.getLongList("scenery.legacy-regions").toString().equals(Files.readString(Path.of("living-history.txt"))),"newly explored regions never become legacy scenery on later boots");
    }
    private void exportOnly()throws Exception{
        Path exports=Path.of("small-structures");Files.createDirectories(exports);
        for(String line:Files.readAllLines(Path.of("living-checkpoint.txt"))){
            var p=line.split(",",6);int x=Integer.parseInt(p[1]),z=Integer.parseInt(p[3]);
            var site=plugin.exploration().cell(Math.floorDiv(x,256),Math.floorDiv(z,256));
            check(site!=null&&site.kind()==ExplorationLayout.Kind.fromId(p[0]),"preview exports the verified site "+p[0]);
            export(new HashMap<>(),site,exports.resolve(site.kind().id+".csv"));
        }
    }
    private void comparePreviousRelease()throws Exception{
        // Both releases share Paper's live registries; only the previous world package is isolated.
        try(var loader=new java.net.URLClassLoader(new java.net.URL[]{Path.of("baseline.jar").toUri().toURL()},plugin.getClass().getClassLoader()){
            @Override protected Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException{
                if(!name.startsWith("com.example.voidscape.world."))return super.loadClass(name,resolve);
                synchronized(getClassLoadingLock(name)){
                    var c=findLoadedClass(name);if(c==null)c=findClass(name);if(resolve)resolveClass(c);return c;
                }
            }
        }){
            String prefix="com.example.voidscape.world.";long seed=world.getSeed();
            var dungeonClass=loader.loadClass(prefix+"DungeonLayout");
            var whaleClass=loader.loadClass(prefix+"SkyWhaleLayout");
            var landmarkClass=loader.loadClass(prefix+"SkyLandmarkLayout");
            var oldDungeons=dungeonClass.getConstructor(long.class,int.class,double.class).newInstance(seed,32,0.0);
            var oldWhales=whaleClass.getConstructor(long.class,dungeonClass,int.class,double.class).newInstance(seed,oldDungeons,32,0.0);
            var oldLandmarks=landmarkClass.getConstructor(long.class,dungeonClass,whaleClass).newInstance(seed,oldDungeons,oldWhales);
            var oldTerrain=loader.loadClass(prefix+"BiomeTerrain").getConstructor(long.class,int.class,Set.class).newInstance(seed,3072,Set.of());
            var ctor=Arrays.stream(loader.loadClass(prefix+"VoidGenerator").getConstructors()).filter(c->c.getParameterCount()==14).findFirst().orElseThrow();
            var old=(org.bukkit.generator.ChunkGenerator)ctor.newInstance(seed,oldDungeons,oldWhales,false,oldLandmarks,false,false,null,null,false,null,oldTerrain,false,.35);
            var dungeons=new DungeonLayout(seed,32,0);var whales=new SkyWhaleLayout(seed,dungeons,32,0);var landmarks=new SkyLandmarkLayout(seed,dungeons,whales);
            for(var kind:BiomeTerrain.Kind.values()){
                var center=generator.terrain().locate(kind,0,0);
                var preserved=new BiomeTerrain(seed,3072,Set.of(),Set.of(),Set.of(SkyPlacementHistory.key(Math.floorDiv(center.x(),512),Math.floorDiv(center.z(),512))));
                var current=new VoidGenerator(seed,dungeons,whales,false,landmarks,false,false,null,null,false,null,preserved,false,.35);
                int cx=Math.floorDiv(center.x(),16),cz=Math.floorDiv(center.z(),16);
                for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++){
                    var before=Bukkit.getServer().createChunkData(world);var after=Bukkit.getServer().createChunkData(world);
                    old.generateNoise(world,new Random(1),cx+dx,cz+dz,before);current.generateNoise(world,new Random(987),cx+dx,cz+dz,after);
                    for(int x=0;x<16;x++)for(int z=0;z<16;z++)for(int y=-64;y<320;y++)
                        if(before.getType(x,y,z)!=after.getType(x,y,z))throw new AssertionError("Changed pre-upgrade scenery "+kind+" "+(cx+dx)+","+y+","+(cz+dz));
                }
                check(true,kind+": nine legacy-region chunks match the previous release exactly");
            }
            var previousKind=loader.loadClass(prefix+"ExplorationLayout$Kind");var previousSite=loader.loadClass(prefix+"ExplorationLayout$Site");
            var render=loader.loadClass(prefix+"ExplorationBlueprint").getMethod("render",loader.loadClass(prefix+"VoidGenerator"),ChunkData.class,int.class,int.class,previousSite);
            var currentTerrain=new BiomeTerrain(seed,3072,Set.of());
            var current=new VoidGenerator(seed,dungeons,whales,false,landmarks,false,false,null,null,false,null,currentTerrain,false,.35);
            for(var kind:ExplorationLayout.Kind.values()){
                if(kind.medium())continue;
                var site=plugin.exploration().nearest(kind,0,0,64);var oldKind=Arrays.stream(previousKind.getEnumConstants()).filter(v->v.toString().equals(kind.name())).findFirst().orElseThrow();
                var oldSite=previousSite.getConstructors()[0].newInstance(oldKind,site.x(),site.floorY(),site.z(),site.rotation(),site.variant());
                var legacy=new ExplorationLayout.Site(kind,site.x(),site.floorY(),site.z(),site.rotation(),site.variant(),true);
                for(int cx=Math.floorDiv(site.x()-18,16);cx<=Math.floorDiv(site.x()+18,16);cx++)
                    for(int cz=Math.floorDiv(site.z()-18,16);cz<=Math.floorDiv(site.z()+18,16);cz++){
                        var before=Bukkit.getServer().createChunkData(world);var after=Bukkit.getServer().createChunkData(world);
                        render.invoke(null,old,before,cx,cz,oldSite);ExplorationBlueprint.render(current,after,cx,cz,legacy);
                        for(int x=0;x<16;x++)for(int z=0;z<16;z++)for(int y=site.floorY()-7;y<=site.floorY()+27;y++)
                            if(!before.getBlockData(x,y,z).getAsString().equals(after.getBlockData(x,y,z).getAsString()))throw new AssertionError("Changed legacy architecture "+kind);
                    }
                check(true,kind.id+": partially generated old sites retain exact previous block states");
            }
        }
    }
}
