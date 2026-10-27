import com.example.voidscape.VoidscapePlugin;
import com.example.voidscape.world.OverworldSanctuaries;
import com.example.voidscape.world.OverworldSanctuaries.*;
import org.bukkit.*;
import org.bukkit.block.Chest;
import org.bukkit.generator.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.persistence.PersistentDataType;
import com.example.voidscape.world.SanctuaryLoot;
import org.bukkit.loot.*;
import org.bukkit.enchantments.Enchantment;
import java.nio.file.*;
import java.util.*;

/** Real Paper generation, all shipped voxels, loot state and two-boot preservation. */
public final class SanctuaryProbe extends JavaPlugin {
    OverworldSanctuaries service;OverworldSanctuaries.Placement placement;
    World fixture;final List<String> checks=new ArrayList<>();
    final ArrayDeque<int[]> queue=new ArrayDeque<>();
    boolean restart;
    void check(boolean ok,String label){if(!ok)throw new AssertionError(label);checks.add(label);getLogger().info("PASS "+label);}
    @Override public void onEnable(){Bukkit.getScheduler().runTaskLater(this,()->{
        try{
            var garden=(VoidscapePlugin)Bukkit.getPluginManager().getPlugin("Evergarden");
            check(garden!=null&&garden.isEnabled(),"full release enabled");service=garden.overworldSanctuaries();
            var primary=Bukkit.getWorlds().getFirst();placement=service.placement(primary);
            check(placement!=null,"attached to the default vanilla Overworld");
            check(service.placement(garden.world())==null,"Evergarden generator excluded");
            restart=Files.exists(Path.of("sanctuary-checkpoint.txt"));
            fixture=new WorldCreator("sanctuary_fixture").generator(new ChunkGenerator(){
                @Override public void generateNoise(WorldInfo w,Random r,int cx,int cz,ChunkData data){data.setRegion(0,59,0,16,64,16,Material.STONE);}
                @Override public List<BlockPopulator> getDefaultPopulators(World w){return List.of(new BlockPopulator(){
                    @Override public void populate(WorldInfo info,Random r,int cx,int cz,LimitedRegion region){
                        int n=Math.floorDiv(cx,32);if(n<0||n>=Kind.values().length)return;
                        Kind kind=Kind.values()[n];placement.paint(new Site(kind,n*512+256,256,123456L+n),cx,cz,region);
                    }
                });}
            }).createWorld();
            fixture.setGameRule(GameRule.RANDOM_TICK_SPEED,0);fixture.setGameRule(GameRule.DO_MOB_SPAWNING,false);
            check(service.placement(fixture)==null,"custom generator excluded from automatic placement");
            if(!restart){placementChecks();lootChecks(garden);}
            // Reverse chunk order for alternating structures, including negative local coords.
            for(int i=0;i<6;i++){
                List<int[]> chunks=new ArrayList<>();
                for(int x=i*32+9;x<=i*32+22;x++)for(int z=9;z<=22;z++)chunks.add(new int[]{x,z});
                if(i%2==1)Collections.reverse(chunks);queue.addAll(chunks);
            }
            next();
        }catch(Throwable ex){finish(ex);}
    },5);}
    void placementChecks(){
        int accepted=0;Set<Kind> kinds=new HashSet<>();
        for(int gx=-48;gx<=48;gx++)for(int gz=-48;gz<=48;gz++){
            Site s=placement.cell(gx,gz);checkStable(s,placement.cell(gx,gz));
            if(s!=null){
                accepted++;kinds.add(s.kind());
                check(s.kind().biome==Bukkit.getWorlds().getFirst().vanillaBiomeProvider().getBiome(Bukkit.getWorlds().getFirst(),s.x(),s.kind().floor,s.z()),"biome matches "+s.kind().id);
            }
        }
        check(accepted>0,"seed-stable natural candidates exist: "+accepted+"; kinds="+kinds);
        check(placement.cell(0,0)==null,"initial spawn / existing regions excluded");
        int loaded=Bukkit.getWorlds().getFirst().getLoadedChunks().length;
        for(Kind kind:Kind.values()){
            var progress=new ArrayList<Integer>();
            Site found=placement.nearest(kind,0,0,128,progress::add,()->false);
            check(found!=null,"expanded search finds "+kind.id+" at "+found);
            check(found.equals(placement.cell(Math.floorDiv(found.x(),placement.spacing*16),Math.floorDiv(found.z(),placement.spacing*16))),"search uses real production candidate "+kind.id);
        }
        check(placement.nearest(Kind.ACHERON,0,0,128,r->{},()->true)==null,"cancelled searches stop");
        check(loaded==Bukkit.getWorlds().getFirst().getLoadedChunks().length,"locating never generates chunks");
    }
    void lootChecks(VoidscapePlugin garden){
        Random random=new Random(20261015L);int raised=0,normal=0,gear=0;
        for(int i=0;i<1500;i++){
            var items=LootTables.END_CITY_TREASURE.getLootTable().populateLoot(random,new LootContext.Builder(Bukkit.getWorlds().getFirst().getSpawnLocation()).build());
            for(var original:items){
                var changed=SanctuaryLoot.upgrade(original,random,.15,garden);
                if(SanctuaryLoot.equipment(original))gear++;
                int extras=0;
                for(var entry:changed.getEnchantments().entrySet()){
                    if(entry.getValue()>entry.getKey().getMaxLevel()){
                        if(entry.getValue()!=entry.getKey().getMaxLevel()+1)throw new AssertionError("loot exceeds cap+1");extras++;
                        if(garden.bossEnchants().level(changed,entry.getKey())!=entry.getValue())throw new AssertionError("missing anvil entitlement");
                    }
                }
                if(extras>1)throw new AssertionError("more than one raised enchant");
                if(changed!=original)raised++;else normal++;
                if(!SanctuaryLoot.equipment(original)&&!original.equals(changed))throw new AssertionError("resource item changed");
            }
        }
        check(gear>100&&raised>20&&normal>raised,"1500 real End City rolls: gear="+gear+", rare cap+1="+raised+", ordinary="+normal);
        var sword=new ItemStack(Material.DIAMOND_SWORD);sword.addEnchantment(Enchantment.SHARPNESS,3);
        check(SanctuaryLoot.upgrade(sword,new Random(1),0,garden).getEnchantmentLevel(Enchantment.SHARPNESS)==3,"zero chance never raises gear");
        check(SanctuaryLoot.upgrade(sword,new Random(1),1,garden).getEnchantmentLevel(Enchantment.SHARPNESS)==6,"raised Sharpness is VI, not current-level +1");
    }
    void checkStable(Site a,Site b){if(!Objects.equals(a,b))throw new AssertionError("unstable candidate");}
    void next(){
        if(queue.size()%100==0)getLogger().info("Chunks remaining: "+queue.size());
        if(queue.isEmpty()){try{inspect();finish(null);}catch(Throwable ex){finish(ex);}return;}
        List<java.util.concurrent.CompletableFuture<?>> batch=new ArrayList<>();
        for(int i=0;i<8&&!queue.isEmpty();i++){
            int[] c=queue.poll();batch.add(fixture.getChunkAtAsync(c[0],c[1],true));
        }
        java.util.concurrent.CompletableFuture.allOf(batch.toArray(java.util.concurrent.CompletableFuture[]::new)).whenComplete((chunk,error)->{
            Bukkit.getScheduler().runTask(this,()->{if(error!=null)finish(error);else next();});
        });
    }
    void inspect()throws Exception {
        long compared=0;int joinedFaces=0;
        List<String> fingerprints=new ArrayList<>();
        List<String> previous=restart?Files.readAllLines(Path.of("sanctuary-inventories.txt")):List.of();
        for(int i=0;i<6;i++){
            Kind kind=Kind.values()[i];Geometry g=service.geometry(kind);int x0=i*512+256,z0=256;
            for(var entry:g.columns.entrySet()){
                long k=entry.getKey();int x=x0+(int)(k>>32),z=z0+(int)k;
                for(Voxel v:entry.getValue()){
                    Material expected=g.palette[v.palette()].getMaterial();
                    Material actual=fixture.getBlockAt(x,kind.floor+v.y(),z).getType();
                    // An intentionally emptied chest is preserved on restart.
                    if(restart&&i==0&&expected==Material.CHEST&&x==x0+g.chests.getFirst().x()&&z==z0+g.chests.getFirst().z())continue;
                    if(actual!=expected)throw new AssertionError(kind+" "+x+","+(kind.floor+v.y())+","+z+" expected "+expected+" got "+actual);
                    var authored=g.palette[v.palette()];
                    if(authored instanceof org.bukkit.block.data.MultipleFacing rails&&expected.name().endsWith("_FENCE")){
                        var live=(org.bukkit.block.data.MultipleFacing)fixture.getBlockAt(x,kind.floor+v.y(),z).getBlockData();
                        for(var face:rails.getFaces()){
                            if(!live.hasFace(face))throw new AssertionError("Missing authored fence rail "+kind+" "+x+","+v.y()+","+z+" "+face);
                            joinedFaces++;
                        }
                    }
                    if(authored instanceof org.bukkit.block.data.type.Wall wall){
                        var live=(org.bukkit.block.data.type.Wall)fixture.getBlockAt(x,kind.floor+v.y(),z).getBlockData();
                        for(var face:List.of(org.bukkit.block.BlockFace.NORTH,org.bukkit.block.BlockFace.SOUTH,org.bukkit.block.BlockFace.EAST,org.bukkit.block.BlockFace.WEST))
                            if(wall.getHeight(face)!=org.bukkit.block.data.type.Wall.Height.NONE){
                                if(live.getHeight(face)==org.bukkit.block.data.type.Wall.Height.NONE)throw new AssertionError("Missing authored wall join "+kind);
                                joinedFaces++;
                            }
                    }
                    compared++;
                }
            }
            check(true,kind.id+" all "+g.count+" authored voxels match real chunks");
            for(int j=0;j<8;j++){
                Point p=g.chests.get(j);var block=fixture.getBlockAt(x0+p.x(),kind.floor+p.y(),z0+p.z());
                check(block.getState() instanceof Chest,kind.id+" chest "+j);
                Chest chest=(Chest)block.getState();
                service.prepareLoot(block.getChunk());chest=(Chest)block.getState();
                if(restart&&i==0&&j==0){check(chest.getBlockInventory().isEmpty()&&chest.getLootTable()==null,"emptied chest stays empty after restart");}
                else {
                    long expected=com.example.voidscape.world.DungeonLayout.mix(123456L+i+j);
                    check(Objects.equals(chest.getPersistentDataContainer().get(new NamespacedKey("voidscape","sanctuary_loot_seed"),PersistentDataType.LONG),expected),kind.id+" persisted loot seed "+j);
                    if(chest.getLootTable()!=null)check(chest.getSeed()==expected,kind.id+" unopened loot table seed "+j);
                    check(!chest.getBlockInventory().isEmpty(),kind.id+" generated treasure "+j);
                    check(chest.getPersistentDataContainer().getOrDefault(new NamespacedKey("voidscape","sanctuary_loot_awarded"),PersistentDataType.INTEGER,0)==2,kind.id+" one-time End City upgrade processed "+j);
                }
                String fingerprint=Arrays.stream(chest.getBlockInventory().getContents()).map(item->item==null?"-":Base64.getEncoder().encodeToString(item.serializeAsBytes())).reduce("",(a,b)->a+";"+b);
                fingerprints.add(fingerprint);
                if(restart&&!(i==0&&j==0)){
                    var old=previous.get(i*8+j).split(";",-1);var now=fingerprint.split(";",-1);boolean equal=old.length==now.length;
                    // Component map order can change across a potion's native disk round-trip.
                    // Compare the complete decoded stacks, including lore, PDC and enchantments.
                    for(int s=1;equal&&s<old.length;s++)equal=Objects.equals(
                        old[s].equals("-")?null:ItemStack.deserializeBytes(Base64.getDecoder().decode(old[s])),
                        now[s].equals("-")?null:ItemStack.deserializeBytes(Base64.getDecoder().decode(now[s])));
                    check(equal,kind.id+" exact inventory survives restart "+j);
                }
                check(block.getRelative(0,1,0).getType().isAir(),kind.id+" lid headroom "+j);
            }
            int floor=kind.floor+(kind==Kind.BANSHEE?4:0);
            for(int x=-30;x<=30;x++)for(int z=-30;z<=30;z++)if(x*x+z*z<=900){
                checkFloor(fixture.getBlockAt(x0+x,floor,z0+27+z).getType(),kind);
                for(int y=1;y<=28;y++)if(!fixture.getBlockAt(x0+x,floor+y,z0+27+z).getType().isAir())throw new AssertionError("arena blocked "+kind);
            }
            check(true,kind.id+" 60-wide arena and 28-block headroom");
        }
        if(!restart){
            Files.write(Path.of("sanctuary-inventories.txt"),fingerprints);
            Point p=service.geometry(Kind.ACHERON).chests.getFirst();Chest chest=(Chest)fixture.getBlockAt(256+p.x(),80+p.y(),256+p.z()).getState();
            chest.setLootTable(null);chest.update(true,false);
            // Mutate the live inventory after applying the BlockState snapshot;
            // updating the old snapshot after clearing would restore its old items.
            ((Chest)fixture.getBlockAt(256+p.x(),80+p.y(),256+p.z()).getState()).getBlockInventory().clear();
            fixture.getBlockAt(256,81,283).setType(Material.DIAMOND_BLOCK,false);
            // Keep the arena clear for the restart test; use the entrance as an edit sentinel.
            fixture.getBlockAt(256,81,283).setType(Material.AIR,false);
            fixture.getBlockAt(256,81,350).setType(Material.DIAMOND_BLOCK,false);
            Files.writeString(Path.of("sanctuary-checkpoint.txt"),"first boot passed\n");
        }else check(fixture.getBlockAt(256,81,350).getType()==Material.DIAMOND_BLOCK,"player edit survives restart");
        check(true,"total geometry comparisons "+compared);
        check(joinedFaces>100,"authored fence and wall joins verified in real chunks: "+joinedFaces);
    }
    void checkFloor(Material m,Kind k){if(m.isAir())throw new AssertionError("arena floor missing "+k);}
    void finish(Throwable ex){
        if(ex!=null)getLogger().log(java.util.logging.Level.SEVERE,"SANCTUARY PROBE",ex);
        try{Files.writeString(Path.of("sanctuary-result-"+(restart?2:1)+".txt"),(ex==null?"PASS":"FAIL "+ex)+"\n"+String.join("\n",checks));}catch(Exception ignored){}
        Bukkit.getScheduler().runTaskLater(this,Bukkit::shutdown,3);
    }
}
