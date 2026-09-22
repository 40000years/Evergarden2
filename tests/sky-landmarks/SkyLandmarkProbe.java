import com.example.voidscape.VoidscapePlugin;
import com.example.voidscape.world.*;
import org.bukkit.*;
import org.bukkit.block.Chest;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import java.nio.file.Files;
import java.util.*;

/** Runs only in the disposable Paper instance created by run.py. */
public final class SkyLandmarkProbe extends JavaPlugin {
    private final List<String> checks=new ArrayList<>();
    private void check(boolean condition,String message){if(!condition)throw new AssertionError(message);checks.add(message);getLogger().info(message);}
    @Override public void onEnable(){
        Bukkit.getScheduler().runTask(this,()->{
            String result="PASS";
            try{
                var phase=getServer().getWorldContainer().toPath().resolve("landmark-probe-phase1");
                if(Files.exists(phase))verifyRestart();
                else{probe();Files.writeString(phase,"complete");}
            }catch(Throwable error){result="FAIL "+error;getLogger().log(java.util.logging.Level.SEVERE,"Landmark probe",error);}
            try{Files.writeString(getServer().getWorldContainer().toPath().resolve("landmark-probe-result.txt"),result+"\n"+String.join("\n",checks));}
            catch(Exception error){getLogger().severe(error.toString());}
            Bukkit.shutdown();
        });
    }
    private void probe(){
        VoidscapePlugin plugin=(VoidscapePlugin)Bukkit.getPluginManager().getPlugin("Evergarden");
        World world=plugin.world();world.setGameRule(GameRule.RANDOM_TICK_SPEED,0);
        // The same config must affect every structure. Keep only the guaranteed upgrade.
        plugin.getConfig().set("structures.treasure.second-chest-chance",1.0);
        plugin.getConfig().set("structures.treasure.crop-stacks",0);
        for(String key:List.of("key-shard","astral-dust","nether-star","limit-break","unique-scroll"))
            plugin.getConfig().set("structures.treasure.bonus-weights."+key,0);
        var generator=new VoidGenerator(world.getSeed(),plugin.layout(),plugin.skyWhales(),true,plugin.skyLandmarks(),true,true);
        var disabled=new VoidGenerator(world.getSeed(),plugin.layout(),plugin.skyWhales(),true,plugin.skyLandmarks(),false,false);
        var rewards=new WhaleTreasure(plugin);
        for(var kind:SkyLandmarkLayout.Kind.values()){
            var site=plugin.skyLandmarks().nearest(kind,0,0,12);check(site!=null,"Found "+kind+" "+site);
            var b=kind.blueprint();
            Map<Long,List<LandmarkBlueprint.Block>> byChunk=new TreeMap<>();
            for(var block:b.blocks()){
                int cx=Math.floorDiv(site.x()+block.x(),16),cz=Math.floorDiv(site.z()+block.z(),16);
                byChunk.computeIfAbsent(((long)cx<<32)|(cz&0xffffffffL),k->new ArrayList<>()).add(block);
            }
            int checked=0,air=0,restored=0;
            for(long key:byChunk.keySet()){
                int cx=(int)(key>>32),cz=(int)key;
                var chunk=world.getChunkAt(cx,cz);var snapshot=chunk.getChunkSnapshot(false,false,false);
                for(var block:byChunk.get(key)){
                    Material actual=snapshot.getBlockType(Math.floorMod(block.x(),16),block.y(),Math.floorMod(block.z(),16));
                    if(actual!=block.material())throw new AssertionError(kind+" mismatch "+block+" actual="+actual);
                    var data=snapshot.getBlockData(Math.floorMod(block.x(),16),block.y(),Math.floorMod(block.z(),16));
                    if(block.facing()!=null&&data instanceof org.bukkit.block.data.Directional direction&&direction.getFacing()!=block.facing())
                        throw new AssertionError("Incorrect stair direction "+block);
                    if(data instanceof org.bukkit.block.data.type.Leaves leaves&&!leaves.isPersistent())throw new AssertionError("Decaying foliage");
                    checked++;
                }
                // Independent chunk rendering checks reservation air, including chunk seams.
                var raw=Bukkit.getServer().createChunkData(world);
                generator.generateNoise(world,new Random(91),cx,cz,raw);
                for(int lx=0;lx<16;lx++)for(int lz=0;lz<16;lz++){
                    int x=cx*16+lx-site.x(),z=cz*16+lz-site.z();
                    if(!site.contains(cx*16+lx,cz*16+lz,0))continue;
                    if(disabled.surface(cx*16+lx,cz*16+lz).land())restored++;
                    for(int y=32;y<=195;y++){
                        if(raw.getType(lx,y,lz)!=b.at(x,y,z))throw new AssertionError("Noise/tree intrudes in "+kind+" at "+x+","+y+","+z);
                        if(b.at(x,y,z)==Material.AIR)air++;
                    }
                }
            }
            check(checked==b.blocks().size(),kind+": "+checked+" actual Paper-generated blocks match blueprint");
            check(air>100000&&restored>0,kind+": "+air+" reserved air cells; disabling restores terrain");
            for(var p:b.route()){
                checkRoute(world,site.x()+p.x(),p.y(),site.z()+p.z());
            }
            check(true,kind+": entire route has floors and headroom in generated world");
            var chunk=world.getChunkAt(Math.floorDiv(site.x()+4,16),Math.floorDiv(site.z()+kind.chestZ,16));
            for(int x:new int[]{4,6}){
                var block=world.getBlockAt(site.x()+x,kind.chestY,site.z()+kind.chestZ);
                check(block.getState() instanceof Chest,kind+": chest exists at "+x);
                Chest chest=(Chest)block.getState();
                var contents=Arrays.stream(chest.getBlockInventory().getContents()).filter(Objects::nonNull).toList();
                check(contents.size()==1&&contents.getFirst().getAmount()==1&&contents.getFirst().getItemMeta().getPersistentDataContainer()
                        .has(new NamespacedKey("advance_magic","wand_upgrade"),PersistentDataType.STRING),kind+": shared config yields one upgrade only");
                chest.getBlockInventory().clear();rewards.populate(chunk);
                check(chest.getBlockInventory().isEmpty(),kind+": empty chest cannot reroll");
                block.setType(Material.AIR,false);rewards.populate(chunk);
                check(block.getType()==Material.AIR,kind+": removed chest stays removed");
            }
            world.getBlockAt(site.x(),200,site.z()).setType(Material.GOLD_BLOCK,false);
        }
        var whale=plugin.skyWhales().nearest(0,0,12);
        for(int x:new int[]{-52,-50}){
            var state=world.getBlockAt(whale.x()+x,115,whale.z()+7).getState();
            check(state instanceof Chest,"Legacy whale chest still generates");
            var items=Arrays.stream(((Chest)state).getBlockInventory().getContents()).filter(Objects::nonNull).toList();
            check(items.size()==1&&items.getFirst().getItemMeta().getPersistentDataContainer()
                    .has(new NamespacedKey("advance_magic","wand_upgrade"),PersistentDataType.STRING),"Whale uses identical shared treasure config");
        }
        world.save();
    }
    private void verifyRestart(){
        VoidscapePlugin plugin=(VoidscapePlugin)Bukkit.getPluginManager().getPlugin("Evergarden");
        for(var kind:SkyLandmarkLayout.Kind.values()){
            var site=plugin.skyLandmarks().nearest(kind,0,0,12);
            for(int x:new int[]{4,6})check(plugin.world().getBlockAt(site.x()+x,kind.chestY,site.z()+kind.chestZ).getType()==Material.AIR,
                    kind+": removed treasure stays removed across real server restart");
            check(plugin.world().getBlockAt(site.x(),200,site.z()).getType()==Material.GOLD_BLOCK,kind+": player edits survive restart");
        }
    }
    private static void checkRoute(World world,int x,int y,int z){
        if(!world.getBlockAt(x,y,z).getType().isSolid()||!world.getBlockAt(x,y+1,z).getType().isAir()
                ||!world.getBlockAt(x,y+2,z).getType().isAir())throw new AssertionError("Blocked route "+x+","+y+","+z);
    }
}
