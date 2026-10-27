import com.example.voidscape.VoidscapePlugin;
import com.example.voidscape.world.*;
import org.bukkit.*;
import org.bukkit.block.Chest;
import org.bukkit.generator.ChunkGenerator.ChunkData;
import org.bukkit.plugin.java.JavaPlugin;
import java.nio.file.*;
import java.util.*;

/** Boots the published v1, then v2 twice: coordinates, ungenerated fragments and player edits survive. */
public final class SmallStructureUpgradeProbe extends JavaPlugin {
    VoidscapePlugin plugin;World world;int checks;
    void check(boolean ok,String text){if(!ok)throw new AssertionError(text);checks++;getLogger().info("PASS "+text);}
    @Override public void onEnable(){Bukkit.getScheduler().runTaskLater(this,()->{
        try{
            plugin=(VoidscapePlugin)Bukkit.getPluginManager().getPlugin("Evergarden");world=plugin.world();
            if(Files.exists(Path.of("upgrade-sites.txt")))verify();else prepare();
            Files.writeString(Path.of("small-upgrade-result.txt"),"PASS "+checks+" real upgrade checks\n");
        }catch(Throwable error){getLogger().log(java.util.logging.Level.SEVERE,"SMALL STRUCTURE UPGRADE FAILED",error);
            try{Files.writeString(Path.of("small-upgrade-result.txt"),"FAIL "+error);}catch(Exception ignored){}}
        finally{Bukkit.getScheduler().runTaskLater(this,Bukkit::shutdown,5);}
    },40);}
    String inventory(Chest chest){
        var result=new ArrayList<String>();for(var item:chest.getBlockInventory().getContents())
            result.add(item==null?"empty":Base64.getEncoder().encodeToString(item.serializeAsBytes()));return String.join(";",result);
    }
    long fingerprint(int cx,int cz){
        ChunkData data=Bukkit.getServer().createChunkData(world);world.getGenerator().generateNoise(world,new Random(17),cx,cz,data);
        long hash=0xcbf29ce484222325L;
        for(int x=0;x<16;x++)for(int z=0;z<16;z++)for(int y=-64;y<320;y++){
            hash^=data.getBlockData(x,y,z).getAsString().hashCode();hash*=0x100000001b3L;
        }
        return hash;
    }
    void prepare()throws Exception{
        int oldVersion=plugin.getPluginMeta().getVersion().contains("e2.54")?1:plugin.getPluginMeta().getVersion().contains("e2.55")?2:plugin.getPluginMeta().getVersion().contains("e2.56")?3:4;
        check(plugin.getPluginMeta().getVersion().contains("e2.54")||plugin.getPluginMeta().getVersion().contains("e2.55")||plugin.getPluginMeta().getVersion().contains("e2.56")||plugin.getPluginMeta().getVersion().contains("e2.57"),"baseline boots a previously delivered architecture");
        Files.writeString(Path.of("upgrade-design-version.txt"),Integer.toString(oldVersion));
        var rows=new ArrayList<String>();
        for(var kind:ExplorationLayout.Kind.values()){
            var s=plugin.exploration().nearest(kind,0,0,64);check(s!=null,"old site found "+kind.id);
            var stored=new ArrayList<String>();int chestCount=oldVersion>=3?s.chestLocations().size():1;
            for(int index=0;index<chestCount;index++){
                int[] p;int y;
                if(oldVersion>=3){var point=s.chestLocations().get(index);p=s.transform(point.x(),point.z());y=s.floorY()+point.y();}
                else{p=s.chest();y=oldVersion==1?s.floorY()+1:s.chestY();}
                var block=world.getBlockAt(p[0],y,p[1]);check(block.getState() instanceof Chest,"old live chest "+index+" exists "+kind.id);
                String content=inventory((Chest)block.getState());if(kind.ordinal()==1&&index==chestCount-1){block.setType(Material.AIR,false);content="removed";}stored.add(content);
            }
            String loot=String.join("#",stored);
            world.getBlockAt(s.x(),s.floorY()+20,s.z()).setType(Material.BEDROCK,false);
            int cx=Math.floorDiv(s.x()-17,16),cz=Math.floorDiv(s.z()-17,16);
            rows.add(kind.id+","+s.x()+","+s.floorY()+","+s.z()+","+s.rotation()+","+cx+","+cz+","+fingerprint(cx,cz)+","+loot);
        }
        world.save();Files.writeString(Path.of("upgrade-sites.txt"),String.join("\n",rows));
    }
    void verify()throws Exception{
        int version=Integer.parseInt(Files.readString(Path.of("upgrade-design-version.txt")));
        check(plugin.getPluginMeta().getVersion().contains("e2.58"),"upgraded release boots on the same world");
        for(String line:Files.readAllLines(Path.of("upgrade-sites.txt"))){
            var row=line.split(",",9);var kind=ExplorationLayout.Kind.fromId(row[0]);
            int x=Integer.parseInt(row[1]),y=Integer.parseInt(row[2]),z=Integer.parseInt(row[3]);
            var s=plugin.exploration().cell(Math.floorDiv(x,256),Math.floorDiv(z,256));
            var contents=row[8].split("#");
            check(s!=null&&s.kind()==kind&&s.x()==x&&s.z()==z&&s.floorY()==y&&s.rotation()==Integer.parseInt(row[4])&&s.designVersion()==version&&s.chestLocations().size()==contents.length,kind.id+": exact old placement resolves to the preserved design without extra chests");
            check(world.getBlockAt(x,y+20,z).getType()==Material.BEDROCK,kind.id+": administrator edit survives upgrade/restart");
            check(fingerprint(Integer.parseInt(row[5]),Integer.parseInt(row[6]))==Long.parseLong(row[7]),kind.id+": old corner retains exact generator block states");
            for(int index=0;index<contents.length;index++){
                var point=s.chestLocations().get(index);int[] p=s.transform(point.x(),point.z());var block=world.getBlockAt(p[0],s.floorY()+point.y(),p[1]);
                if(contents[index].equals("removed"))check(block.getType().isAir(),kind.id+": deleted chest stays absent");
                else check(block.getState() instanceof Chest&&inventory((Chest)block.getState()).equals(contents[index]),kind.id+": existing loot "+index+" stays exact");
            }
            var fresh=plugin.exploration().nearest(kind,0,0,kind.medium()?96:64,true);
            check(fresh!=null&&fresh.designVersion()==5&&fresh.chestLocations().size()>=2,kind.id+": exploration command finds latest architecture beyond explored cells");
        }
    }
}
