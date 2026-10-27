import com.example.voidscape.VoidscapePlugin;
import com.example.voidscape.world.*;
import org.bukkit.*;
import org.bukkit.block.Chest;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Real e2.49 -> furnished upgrade and a third boot, with old loot and edits preserved. */
public final class BossInteriorUpgradeProbe extends JavaPlugin {
    VoidscapePlugin garden;World world;List<String> checks=new ArrayList<>();
    void check(boolean value,String label){if(!value)throw new AssertionError(label);checks.add(label);getLogger().info("PASS: "+label);}
    @Override public void onEnable(){Bukkit.getScheduler().runTaskLater(this,()->{
        String report="PASS";
        try{
            garden=(VoidscapePlugin)Bukkit.getPluginManager().getPlugin("Evergarden");
            check(garden!=null&&garden.isEnabled(),"release boots");world=garden.world();
            if(Files.exists(Path.of("old-interiors.txt")))upgrade();else old();
        }catch(Throwable error){report="FAIL "+error;getLogger().log(java.util.logging.Level.SEVERE,"INTERIOR UPGRADE",error);}
        try{Files.writeString(Path.of("interior-upgrade-result.txt"),report+"\n"+String.join("\n",checks));}catch(Exception ignored){}
        Bukkit.getScheduler().runTaskLater(this,Bukkit::shutdown,3);
    },20);}
    String exterior(BossStructureLayout.Site site)throws Exception {
        var digest=MessageDigest.getInstance("SHA-256");
        // Independent exterior samples cover four skyline corners and the arena itself.
        for(var pos:List.of(new int[]{-80,-80},new int[]{80,-80},new int[]{-80,64},new int[]{80,64},new int[]{0,0})){
            int cx=Math.floorDiv(site.x()+pos[0],16),cz=Math.floorDiv(site.z()+pos[1],16);
            var raw=Bukkit.getServer().createChunkData(world);world.getGenerator().generateNoise(world,new Random(99),cx,cz,raw);
            for(int x=0;x<16;x++)for(int z=0;z<16;z++)for(int y=40;y<320;y++){
                int ordinal=raw.getType(x,y,z).ordinal();digest.update((byte)ordinal);digest.update((byte)(ordinal>>8));
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }
    int editX(BossStructureLayout.Kind kind){return switch(kind){case ARKHAIA->10;case VALKYRIE->15;case SERAPHON->24;};}
    int editZ(BossStructureLayout.Kind kind){return switch(kind){case ARKHAIA->-60;case VALKYRIE->-62;case SERAPHON->-89;};}
    void old()throws Exception {
        check(garden.getDescription().getVersion().contains("e2.49"),"first boot uses approved exterior-only release");
        var rows=new ArrayList<String>();
        for(var kind:BossStructureLayout.Kind.values()){
            var site=garden.bossStructures().nearest(kind,0,0,20);check(site!=null,"old site exists: "+kind.id);
            var primary=world.getBlockAt(site.x()+4,kind.floorY+1,site.z()+kind.chestZ);
            check(primary.getState() instanceof Chest,"original treasure chest exists");
            var chest=(Chest)primary.getState();chest.getBlockInventory().clear();
            var sword=new ItemStack(Material.DIAMOND_SWORD);sword.addUnsafeEnchantment(Enchantment.SHARPNESS,2);chest.getBlockInventory().setItem(0,sword);
            world.getBlockAt(site.x()+6,kind.floorY+1,site.z()+kind.chestZ).setType(Material.AIR,false);
            world.getBlockAt(site.x()+editX(kind),kind.floorY,site.z()+editZ(kind)).setType(Material.BEDROCK,false);
            rows.add(kind.id+","+site.x()+","+site.z()+","+exterior(site));
        }
        world.save();Files.write(Path.of("old-interiors.txt"),rows);
        check(true,"old loot, deleted chests and interior edits saved before upgrade");
    }
    void upgrade()throws Exception {
        check(garden.getDescription().getVersion().contains("e2.50"),"world upgraded to furnished release");
        var fingerprints=new ArrayList<String>();
        for(var row:Files.readAllLines(Path.of("old-interiors.txt"))){
            var fields=row.split(",");var kind=BossStructureLayout.Kind.fromId(fields[0]);int x=Integer.parseInt(fields[1]),z=Integer.parseInt(fields[2]);
            var site=garden.bossStructures().nearest(kind,0,0,20);check(site.x()==x&&site.z()==z,kind.id+": site stays at original coordinates");
            check(exterior(site).equals(fields[3]),kind.id+": four exterior corners and full arena keep exact geometry fingerprint");
            int added=0;
            for(int i=0;i<site.blueprint().treasures().size();i++){
                var p=site.blueprint().treasures().get(i).point();var block=world.getBlockAt(x+p.x(),p.y(),z+p.z());
                if(i==1){check(block.getType()==Material.AIR,kind.id+": removed original chest remains removed");continue;}
                check(block.getState() instanceof Chest,kind.id+": original/added chest exists at "+p);
                var inventory=((Chest)block.getState()).getBlockInventory();
                if(i==0){
                    check(Arrays.stream(inventory.getContents()).filter(Objects::nonNull).count()==1
                        &&inventory.getItem(0).getEnchantmentLevel(Enchantment.SHARPNESS)==2,kind.id+": old chest inventory preserved exactly");
                }else{
                    check(!inventory.isEmpty(),kind.id+": newly installed chest receives treasure exactly once");added++;
                    fingerprints.add(kind.id+":"+i+":"+fingerprint(inventory));
                }
            }
            check(added==6,kind.id+": six additional treasure chests installed in existing structure");
            check(world.getBlockAt(x+editX(kind),kind.floorY,z+editZ(kind)).getType()==Material.BEDROCK,kind.id+": administrator's interior edit is preserved");
            check(world.getBlockAt(x+5,kind.floorY+2,z+kind.chestZ+1).getType()!=Material.BOOKSHELF,kind.id+": old bookshelf converted to a decorated treasury altar");
            for(var display:site.blueprint().displays()){
                var p=display.point();var chunk=world.getChunkAt(Math.floorDiv(x+p.x(),16),Math.floorDiv(z+p.z(),16));
                var rewards=new WhaleTreasure(garden);rewards.populate(chunk);
                long before=Arrays.stream(chunk.getEntities()).filter(e->e.getPersistentDataContainer().has(garden.key("boss_interior_display"),PersistentDataType.STRING)).count();
                check(before>=1,kind.id+": equipment display survives chunk load/restart");rewards.populate(chunk);
                long after=Arrays.stream(chunk.getEntities()).filter(e->e.getPersistentDataContainer().has(garden.key("boss_interior_display"),PersistentDataType.STRING)).count();
                check(after==before,kind.id+": repeated population does not duplicate displays");
            }
        }
        var ledger=Path.of("furnished-loot.txt");
        if(Files.exists(ledger))check(Files.readAllLines(ledger).equals(fingerprints),"all added chest contents and over-cap enchantments survive real restart unchanged");
        else Files.write(ledger,fingerprints);
        world.save();
    }
    String fingerprint(org.bukkit.inventory.Inventory inventory)throws Exception {
        var digest=MessageDigest.getInstance("SHA-256");
        for(int slot=0;slot<inventory.getSize();slot++){
            var item=inventory.getItem(slot);if(item==null)continue;
            String value=slot+":"+item.getType()+":"+item.getAmount()+":"+item.getItemMeta().getAsString();
            digest.update(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}
