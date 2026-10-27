import com.example.voidscape.VoidscapePlugin;
import com.example.voidscape.world.*;
import org.bukkit.*;
import org.bukkit.plugin.java.JavaPlugin;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Boot the published v1 build, save partial structures, then boot v2 over that world. */
public final class BossArchitectureUpgradeProbe extends JavaPlugin {
    VoidscapePlugin garden;World world;List<String> checks=new ArrayList<>();
    void check(boolean value,String label){if(!value)throw new AssertionError(label);checks.add(label);getLogger().info("PASS: "+label);}
    @Override public void onEnable(){Bukkit.getScheduler().runTaskLater(this,()->{
        String report="PASS";
        try{
            garden=(VoidscapePlugin)Bukkit.getPluginManager().getPlugin("Evergarden");
            check(garden!=null&&garden.isEnabled(),"published release boots");world=garden.world();
            if(Files.exists(Path.of("old-architecture.txt")))upgrade();else old();
        }catch(Throwable error){report="FAIL "+error;getLogger().log(java.util.logging.Level.SEVERE,"ARCHITECTURE UPGRADE",error);}
        try{Files.writeString(Path.of("architecture-upgrade-result.txt"),report+"\n"+String.join("\n",checks));}catch(Exception ignored){}
        Bukkit.shutdown();
    },20);}
    String hash(int cx,int cz)throws Exception {
        var raw=Bukkit.getServer().createChunkData(world);
        world.getGenerator().generateNoise(world,new Random(91),cx,cz,raw);
        var digest=MessageDigest.getInstance("SHA-256");
        for(int x=0;x<16;x++)for(int z=0;z<16;z++)for(int y=40;y<=319;y++){
            int ordinal=raw.getType(x,y,z).ordinal();digest.update((byte)ordinal);digest.update((byte)(ordinal>>8));
        }
        return HexFormat.of().formatHex(digest.digest());
    }
    void old()throws Exception {
        check(garden.getDescription().getVersion().contains("e2.48"),"warm world uses previous architecture release");
        var rows=new ArrayList<String>();
        for(var kind:BossStructureLayout.Kind.values()){
            var site=garden.bossStructures().nearest(kind,0,0,20);check(site!=null,"v1 candidate exists: "+kind.id);
            int cx=Math.floorDiv(site.x()+80,16),cz=Math.floorDiv(site.z()-80,16);
            String fingerprint=hash(cx,cz);
            world.getBlockAt(site.x(),kind.floorY+20,site.z()).setType(Material.GOLD_BLOCK,false);
            rows.add(kind.id+","+site.x()+","+site.z()+","+cx+","+cz+","+fingerprint);
        }
        world.save();Files.write(Path.of("old-architecture.txt"),rows);
        check(true,"partial v1 structures and player edits saved before upgrade");
    }
    void upgrade()throws Exception {
        check(garden.getDescription().getVersion().contains("e2.49"),"world upgraded to redesigned release");
        for(var row:Files.readAllLines(Path.of("old-architecture.txt"))){
            var fields=row.split(",");var kind=BossStructureLayout.Kind.fromId(fields[0]);
            int x=Integer.parseInt(fields[1]),z=Integer.parseInt(fields[2]);
            var site=garden.bossStructures().nearest(kind,0,0,20);
            check(site.x()==x&&site.z()==z&&site.legacyDesign(),kind.id+": migration preserves old candidate and geometry version");
            check(world.getBlockAt(x,kind.floorY+20,z).getType()==Material.GOLD_BLOCK,kind.id+": existing chunks and player edits survive upgrade");
            check(hash(Integer.parseInt(fields[3]),Integer.parseInt(fields[4])).equals(fields[5]),kind.id+": previously ungenerated corner retains exact v1 geometry");
            var fresh=garden.bossStructures().nearest(kind,0,0,32,true);
            check(fresh!=null&&!fresh.legacyDesign(),kind.id+": fresh cells select redesigned architecture");
            var floor=fresh.blueprint().at(0,kind.floorY,kind.arrivalZ);
            check(world.getBlockAt(fresh.x(),kind.floorY,fresh.z()+kind.arrivalZ).getType()==floor,kind.id+": redesigned arrival generates correctly after upgrade");
        }
    }
}
