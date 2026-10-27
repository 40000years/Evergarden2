import com.example.voidscape.VoidscapePlugin;
import com.example.voidscape.world.OverworldSanctuaries;
import com.example.voidscape.world.OverworldSanctuaries.*;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.generator.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import java.nio.file.*;
import java.util.*;

/** Runs first against the delivered v1 JAR, then twice against the new release. */
public final class SanctuaryUpgradeProbe extends JavaPlugin {
    OverworldSanctuaries service;OverworldSanctuaries.Placement layout;World world;int phase;
    final List<String> checks=new ArrayList<>();final ArrayDeque<int[]> queue=new ArrayDeque<>();
    void check(boolean ok,String label){if(!ok)throw new AssertionError(label);checks.add(label);getLogger().info("PASS "+label);}
    Site site(int i){return new Site(Kind.values()[i],i*512+256,256,123456L+i);}
    int baselineVersion(){return Integer.getInteger("sanctuary.baseline.version",2);}
    org.bukkit.block.Block minedBlock()throws Exception {
        var p=Files.readString(Path.of("mine-sentinel.txt")).split(",");Site s=site(5);
        return world.getBlockAt(s.x()+Integer.parseInt(p[0]),s.kind().floor+Integer.parseInt(p[1]),s.z()+Integer.parseInt(p[2]));
    }
    org.bukkit.block.Block containerBlock(int index)throws Exception {
        var p=Files.readAllLines(Path.of("container-sentinels.txt")).get(index).split(",");Site s=site(Integer.parseInt(p[0]));
        return world.getBlockAt(s.x()+Integer.parseInt(p[1]),s.kind().floor+Integer.parseInt(p[2]),s.z()+Integer.parseInt(p[3]));
    }
    Chest chest(int i,int j){Site s=site(i);Point p=service.geometry(s.kind()).chests.get(j);return (Chest)world.getBlockAt(s.x()+p.x(),s.kind().floor+p.y(),s.z()+p.z()).getState();}
    String inventory(Chest chest){return Arrays.stream(chest.getBlockInventory().getContents()).map(i->i==null?"-":Base64.getEncoder().encodeToString(i.serializeAsBytes())).reduce("",(a,b)->a+";"+b);}
    @Override public void onEnable(){Bukkit.getScheduler().runTaskLater(this,()->{try{
        phase=Files.exists(Path.of("upgrade-phase.txt"))?Integer.parseInt(Files.readString(Path.of("upgrade-phase.txt")).trim())+1:1;
        var garden=(VoidscapePlugin)Bukkit.getPluginManager().getPlugin("Evergarden");check(garden!=null&&garden.isEnabled(),"release boots in phase "+phase);
        service=garden.overworldSanctuaries();layout=service.placement(Bukkit.getWorlds().getFirst());
        world=new WorldCreator("sanctuary_upgrade_fixture").generator(new ChunkGenerator(){
            @Override public void generateNoise(WorldInfo w,Random r,int cx,int cz,ChunkData d){d.setRegion(0,59,0,16,64,16,Material.STONE);}
            @Override public List<BlockPopulator> getDefaultPopulators(World w){return List.of(new BlockPopulator(){
                @Override public void populate(WorldInfo info,Random r,int cx,int cz,LimitedRegion region){int i=Math.floorDiv(cx,32);if(i>=0&&i<6)layout.paint(site(i),cx,cz,region);}
            });}
        }).createWorld();world.setGameRule(GameRule.RANDOM_TICK_SPEED,0);world.setGameRule(GameRule.DO_MOB_SPAWNING,false);
        for(int i=0;i<6;i++)for(int x=i*32+9;x<=i*32+22;x++)for(int z=9;z<=22;z++)queue.add(new int[]{x,z});
        next();
    }catch(Throwable ex){finish(ex);}},5);}
    void next(){
        if(queue.isEmpty()){try{if(phase==1)baseline();else upgrade();}catch(Throwable ex){finish(ex);}return;}
        var futures=new ArrayList<java.util.concurrent.CompletableFuture<?>>();
        for(int i=0;i<8&&!queue.isEmpty();i++){int[] c=queue.poll();futures.add(world.getChunkAtAsync(c[0],c[1],true));}
        java.util.concurrent.CompletableFuture.allOf(futures.toArray(java.util.concurrent.CompletableFuture[]::new)).whenComplete((ok,ex)->Bukkit.getScheduler().runTask(this,()->{if(ex!=null)finish(ex);else next();}));
    }
    void baseline()throws Exception {
        world.getBlockAt(266,112,174).setType(Material.EMERALD_BLOCK,false);
        var mined=minedBlock();
        check(!mined.getType().isAir(),"mine sentinel originally is an authored solid block");
        mined.setType(Material.AIR,false);
        ((Container)containerBlock(0).getState()).getInventory().setItem(0,new ItemStack(Material.DIAMOND,3));
        check(((Container)containerBlock(1).getState()).getInventory().isEmpty(),"original decorative barrel is empty");
        Chest empty=chest(0,0);empty.setLootTable(null);empty.update(false,false);empty.getBlockInventory().clear();
        Chest partial=chest(0,1);var inv=partial.getBlockInventory();for(int i=0;i<27;i++)if(inv.getItem(i)!=null){inv.setItem(i,null);break;}
        chest(0,2).getBlockInventory().setItem(0,new ItemStack(Material.DIAMOND_BLOCK,13));
        List<String> protectedItems=new ArrayList<>();for(int j=0;j<3;j++)protectedItems.add(inventory(chest(0,j)));
        Files.write(Path.of("protected-inventories.txt"),protectedItems);
        check(((Chest)chest(0,0).getBlock().getState()).getBlockInventory().isEmpty(),"empty cache saved before upgrade");
        check(chest(0,3).getPersistentDataContainer().getOrDefault(new NamespacedKey("voidscape","sanctuary_loot_profile"),PersistentDataType.INTEGER,0)==(baselineVersion()==1?0:2),"baseline uses the expected delivered loot profile");
        if(baselineVersion()>=2){
            var all=new ArrayList<String>();for(int i=0;i<6;i++)for(int j=0;j<8;j++)all.add(inventory(chest(i,j)));
            Files.write(Path.of("original-v2-inventories.txt"),all);
            // Exercise both native old chunks and chunks stamped by an earlier
            // selective upgrade, matching real worlds upgraded through releases.
            for(Chunk c:world.getLoadedChunks())if((c.getX()&1)==0)
                c.getPersistentDataContainer().set(new NamespacedKey("voidscape","sanctuary_geometry"),PersistentDataType.INTEGER,baselineVersion());
        }
        finish(null);
    }
    void upgrade(){
        for(int i=0;i<6;i++)for(int x=i*32+9;x<=i*32+22;x++)for(int z=9;z<=22;z++){
            Chunk c=world.getChunkAt(x,z);service.queueUpgrade(c,site(i),baselineVersion());service.convertLegacyLoot(c,site(i));
        }
        waitForUpgrade(0);
    }
    void waitForUpgrade(int ticks){
        if(ticks>1800){finish(new AssertionError("upgrade queue did not finish"));return;}
        if(service.upgrading()){Bukkit.getScheduler().runTaskLater(this,()->waitForUpgrade(ticks+1),1);return;}
        try{
            check(world.getBlockAt(266,112,174).getType()==Material.EMERALD_BLOCK,"administrator edit survives decoration");
            for(String line:Files.readAllLines(Path.of("upgrade-samples.txt"))){
                String[] fields=line.split(",");int i=Integer.parseInt(fields[0]);Site s=site(i);
                int x=Integer.parseInt(fields[1]),y=Integer.parseInt(fields[2]),z=Integer.parseInt(fields[3]);
                check(world.getBlockAt(s.x()+x,s.kind().floor+y,s.z()+z).getType()==Material.valueOf(fields[4]),s.kind().id+" architectural revision at "+x+","+y+","+z);
            }
            check(minedBlock().getType().isAir(),"mined decoration stays mined");
            check(containerBlock(0).getState() instanceof Container c&&c.getInventory().containsAtLeast(new ItemStack(Material.DIAMOND),3),"player belongings in original decorative barrel survive");
            var emptyExpected=Files.readAllLines(Path.of("container-sentinels.txt")).get(1).split(",")[4];
            check(containerBlock(1).getType()==Material.valueOf(emptyExpected),"empty decorative container follows target design");
            List<String> protectedItems=Files.readAllLines(Path.of("protected-inventories.txt"));
            for(int j=0;j<3;j++)check(protectedItems.get(j).equals(inventory(chest(0,j))),"looted/edited cache "+j+" preserved exactly");
            int converted=0;List<String> fingerprints=new ArrayList<>();
            for(int i=0;i<6;i++)for(int j=0;j<8;j++){
                Chest c=chest(i,j);fingerprints.add(inventory(c));
                if(!(i==0&&j<3)){
                    check(c.getPersistentDataContainer().getOrDefault(new NamespacedKey("voidscape","sanctuary_loot_profile"),PersistentDataType.INTEGER,0)==2,"End City profile retained "+i+":"+j);
                    converted++;
                }
            }
            check(converted==45,"all 45 untouched caches migrated once");
            if(baselineVersion()>=2)check(Files.readAllLines(Path.of("original-v2-inventories.txt")).equals(fingerprints),"all 48 accepted inventories preserved byte-for-byte");
            if(phase==2)Files.write(Path.of("upgraded-inventories.txt"),fingerprints);
            else check(Files.readAllLines(Path.of("upgraded-inventories.txt")).equals(fingerprints),"all upgraded inventories remain byte-identical on third boot");
            finish(null);
        }catch(Throwable ex){finish(ex);}
    }
    void finish(Throwable ex){
        if(ex!=null)getLogger().log(java.util.logging.Level.SEVERE,"UPGRADE PROBE",ex);
        try{Files.writeString(Path.of("upgrade-result-"+phase+".txt"),(ex==null?"PASS":"FAIL "+ex)+"\n"+String.join("\n",checks));if(ex==null)Files.writeString(Path.of("upgrade-phase.txt"),String.valueOf(phase));}catch(Exception ignored){}
        Bukkit.getScheduler().runTaskLater(this,Bukkit::shutdown,3);
    }
}
