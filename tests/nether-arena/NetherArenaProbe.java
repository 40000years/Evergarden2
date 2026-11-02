import com.example.voidscape.VoidscapePlugin;
import com.example.voidscape.world.NetherArenaWorldgen;
import org.bukkit.*;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.*;
import java.lang.reflect.Field;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.zip.GZIPInputStream;

/** Real vanilla Nether chunks, negative coordinates, two boots and an old-region sentinel. */
public final class NetherArenaProbe extends JavaPlugin {
    private final List<String> checks=new ArrayList<>();
    private final ArrayDeque<int[]> queue=new ArrayDeque<>();
    private World world;
    private NetherArenaWorldgen.Site site;
    private boolean restart;
    private void check(boolean ok,String what){if(!ok)throw new AssertionError(what);checks.add(what);getLogger().info("PASS "+what);}
    @Override public void onEnable(){Bukkit.getScheduler().runTaskLater(this,()->{
        try {
            restart=Files.exists(Path.of("nether-arena-checkpoint.txt"));
            var garden=(VoidscapePlugin)Bukkit.getPluginManager().getPlugin("Evergarden");
            check(garden!=null&&garden.isEnabled(),"Evergarden enabled");
            NetherArenaWorldgen gen=garden.netherArenaWorldgen();
            world=Bukkit.getWorlds().stream().filter(w->w.getEnvironment()==World.Environment.NETHER).findFirst().orElseThrow();
            check(gen!=null&&gen.enabled(world),"experimental generator attached to vanilla Nether");
            check(!gen.enabled(Bukkit.getWorlds().getFirst()),"not attached to Overworld");
            int before=world.getLoadedChunks().length;
            site=gen.findNearest(world,-5000,-5000,3);
            check(site!=null&&site.x()<0&&site.z()<0,"candidate in negative coordinates: "+site);
            check(site.equals(gen.findNearest(world,-5000,-5000,3)),"same seed gives same candidate on repeated search");
            check(before==world.getLoadedChunks().length,"search does not load chunks");
            // Validate the pre-existing region is captured and cannot obtain a site.
            BlockPopulator pop=world.getPopulators().stream().filter(p->p.getClass().getName().contains("NetherArenaWorldgen$Placement")).findFirst().orElseThrow();
            Field field=pop.getClass().getDeclaredField("excluded");field.setAccessible(true);
            @SuppressWarnings("unchecked") Set<Long> excluded=(Set<Long>)field.get(pop);
            long oldKey=((long)10<<32)|10L;
            check(excluded.contains(oldKey),"pre-existing region cell is excluded");
            var method=pop.getClass().getDeclaredMethod("site",int.class,int.class);method.setAccessible(true);
            check(method.invoke(pop,10,10)==null,"excluded cell cannot produce an arena");
            Path checkpoint=Path.of("nether-arena-checkpoint.txt");
            if(restart)check(Files.readString(checkpoint).trim().equals(site.x()+","+site.z()),"candidate persists across restart");
            int r=NetherArenaWorldgen.RADIUS;
            for(int cx=Math.floorDiv(site.x()-r-16,16);cx<=Math.floorDiv(site.x()+r+16,16);cx++)
                for(int cz=Math.floorDiv(site.z()-r-16,16);cz<=Math.floorDiv(site.z()+r+16,16);cz++)queue.add(new int[]{cx,cz});
            if(restart){List<int[]> list=new ArrayList<>(queue);Collections.reverse(list);queue.clear();queue.addAll(list);}
            world.setGameRule(GameRule.DO_MOB_SPAWNING,false);
            if(restart)next();
            else {
                // Edit one newly generated perimeter chunk before its neighbors
                // populate, and fire the normal place event before queued repairs.
                int x=site.x()+55,z=site.z()+55;
                world.getChunkAtAsync(Math.floorDiv(x,16),Math.floorDiv(z,16),true).whenComplete((chunk,error)->
                    Bukkit.getScheduler().runTaskLater(this,()->{
                        if(error!=null){finish(error);return;}
                        chunk.addPluginChunkTicket(this);
                        var block=world.getBlockAt(x,NetherArenaWorldgen.FLOOR+1,z);
                        var previous=block.getState();block.setType(Material.DIAMOND_BLOCK,false);
                        // Call this listener directly: there is no connected player in
                        // the fixture, and other plugins expect a non-null player.
                        gen.placed(new BlockPlaceEvent(block,previous,block,new ItemStack(Material.DIAMOND_BLOCK),null,true,EquipmentSlot.HAND));
                        next();
                    },1));
            }
        } catch(Throwable ex){finish(ex);}
    },5);}
    private void next(){
        if(queue.isEmpty()){
            Bukkit.getScheduler().runTaskLater(this,()->{
                if(restart){finishInspect();return;}
                try{
                    // Emulate late vanilla decoration while a freshly populated
                    // chunk unloads, then require load to requeue its repair.
                    int x=site.x()-29,z=site.z()+7,cx=Math.floorDiv(x,16),cz=Math.floorDiv(z,16);
                    world.getBlockAt(x,NetherArenaWorldgen.FLOOR+5,z).setType(Material.SHROOMLIGHT,false);
                    // A plugin ticket keeps surrounding chunks alive too.
                    world.removePluginChunkTickets(this);
                    world.unloadChunkRequest(cx,cz);
                    Bukkit.getScheduler().runTaskLater(this,()->{
                        try{
                            check(!world.isChunkLoaded(cx,cz),"fresh chunk unloaded and saved");
                            world.getChunkAtAsync(cx,cz,true).whenComplete((chunk,error)->Bukkit.getScheduler().runTask(this,()->{
                                if(error!=null){finish(error);return;}
                                chunk.addPluginChunkTicket(this);
                                Bukkit.getScheduler().runTaskLater(this,()->{
                                    try{check(world.getBlockAt(x,NetherArenaWorldgen.FLOOR+5,z).getType().isAir(),"reloaded fresh chunk is requeued for repair");finishInspect();}
                                    catch(Throwable ex){finish(ex);}
                                },120);
                            }));
                        }catch(Throwable ex){finish(ex);}
                    },100);
                }catch(Throwable ex){finish(ex);}
            },400);return;
        }
        List<CompletableFuture<Chunk>> batch=new ArrayList<>();
        for(int i=0;i<8&&!queue.isEmpty();i++){int[] c=queue.poll();batch.add(world.getChunkAtAsync(c[0],c[1],true));}
        CompletableFuture.allOf(batch.toArray(CompletableFuture[]::new)).whenComplete((v,error)->Bukkit.getScheduler().runTask(this,()->{
            if(error!=null)finish(error);else {for(var future:batch)future.join().addPluginChunkTicket(this);next();}
        }));
    }
    private void finishInspect(){try{inspect();finish(null);}catch(Throwable ex){finish(ex);}}
    private Material at(int dx,int y,int dz){return world.getBlockAt(site.x()+dx,y,site.z()+dz).getType();}
    private void inspect() throws Exception {
        var gen=((VoidscapePlugin)Bukkit.getPluginManager().getPlugin("Evergarden")).netherArenaWorldgen();
        for(String name:List.of("fresh","dirty","pending","repairs")){
            try{Field field=gen.getClass().getDeclaredField(name);field.setAccessible(true);Object value=field.get(gen);
                getLogger().info("DEBUG "+name+"="+(value instanceof Map<?,?> m?m.size():((Collection<?>)value).size())+" "+value);
            }catch(NoSuchFieldException ignored){}
        }
        getLogger().info("DEBUG target chunk loaded="+world.isChunkLoaded(Math.floorDiv(site.x()-29,16),Math.floorDiv(site.z()+7,16)));
        int f=NetherArenaWorldgen.FLOOR,r=NetherArenaWorldgen.RADIUS;
        for(int x=-30;x<=30;x++)for(int z=-30;z<=30;z++){
            if(!at(x,f,z).isSolid())throw new AssertionError("floor missing at "+x+","+z);
            for(int y=1;y<=25;y++){
                Material m=at(x,f+y,z);
                if(restart&&x==0&&y==1&&z==0)continue;
                if(!m.isAir())throw new AssertionError("center blocked or liquid at "+x+","+(f+y)+","+z+": "+m);
            }
        }
        check(true,"61 x 61 solid arena floor with 25 blocks clear headroom");
        try(var in=new DataInputStream(new BufferedInputStream(new GZIPInputStream(
                Objects.requireNonNull(Bukkit.getPluginManager().getPlugin("Evergarden").getResource("dimensional-sanctuaries/nether-arena.ows")))))){
            check(in.readInt()==0x4f575331,"asset signature");
            Material[] palette=new Material[in.readUnsignedShort()];
            for(int i=0;i<palette.length;i++)palette[i]=Bukkit.createBlockData(in.readUTF()).getMaterial();
            int count=in.readInt();
            for(int i=0;i<count;i++){
                int x=in.readShort(),y=in.readShort(),z=in.readShort(),p=in.readUnsignedShort();
                // This chunk deliberately contains the player edit above and is
                // exempt from the subsequent repair by design.
                if(Math.floorDiv(site.x()+x,16)==Math.floorDiv(site.x()+55,16)&&Math.floorDiv(site.z()+z,16)==Math.floorDiv(site.z()+55,16))continue;
                Material actual=at(x,f+y,z);
                if(actual!=palette[p]&&actual!=Material.BEDROCK)throw new AssertionError("asset voxel mismatch "+x+","+y+","+z+": "+actual+" expected "+palette[p]);
            }
            check(true,"authored block materials match in generated unedited chunks (asset has "+count+" voxels)");
        }
        for(int p=-r;p<=r;p++)for(int y=f-4;y<=f+48;y++){
            for(Material m:List.of(at(-r,y,p),at(r,y,p),at(p,y,-r),at(p,y,r)))
                if(m!=Material.POLISHED_BLACKSTONE_BRICKS&&m!=Material.BEDROCK)throw new AssertionError("unsealed boundary "+p+","+y+": "+m);
        }
        check(true,"boundary sealed after adjacent chunks and fluid ticks");
        for(int x=-63;x<=63;x+=3)for(int z=-63;z<=63;z+=3)for(int y=f-3;y<f+48;y++)
            if(at(x,y,z)==Material.LAVA)throw new AssertionError("lava inside shell "+x+","+y+","+z);
        check(true,"sampled chamber contains no lava after fluid ticks");
        check(at(55,f+1,55)==Material.DIAMOND_BLOCK,"player placement during generation survives neighboring chunks and restart");
        for(int x=-60;x<=60;x+=10)for(int z=-60;z<=60;z+=10)
            if(at(x,127,z)!=Material.BEDROCK)throw new AssertionError("Nether roof bedrock missing "+x+","+z);
        check(true,"vanilla ceiling bedrock preserved");
        if(restart)check(at(0,f+1,0)==Material.DIAMOND_BLOCK,"player edit persists across restart");
        else {
            world.getBlockAt(site.x(),f+1,site.z()).setType(Material.DIAMOND_BLOCK,false);
            Files.writeString(Path.of("nether-arena-checkpoint.txt"),site.x()+","+site.z()+"\n");
        }
    }
    private void finish(Throwable error){
        if(error!=null)getLogger().log(java.util.logging.Level.SEVERE,"NETHER ARENA PROBE",error);
        try{Files.writeString(Path.of("nether-arena-result-"+(restart?2:1)+".txt"),(error==null?"PASS":"FAIL "+error)+"\n"+String.join("\n",checks)+"\n");}catch(Exception ignored){}
        Bukkit.getScheduler().runTaskLater(this,Bukkit::shutdown,3);
    }
}
