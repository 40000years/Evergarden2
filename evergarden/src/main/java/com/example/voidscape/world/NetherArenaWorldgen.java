package com.example.voidscape.world;

import com.example.voidscape.VoidscapePlugin;
import org.bukkit.*;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.*;
import org.bukkit.event.world.WorldInitEvent;
import org.bukkit.event.world.ChunkPopulateEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.block.Block;
import org.bukkit.generator.*;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.GZIPInputStream;

/** Experimental, buried Nether arena. Generation only touches the chunk being populated. */
public final class NetherArenaWorldgen implements Listener {
    public static final int FLOOR = 48, SPACING = 64, RADIUS = 65;
    public record Site(int x, int z) {}
    private record Voxel(int y, int palette) {}
    private final VoidscapePlugin plugin;
    private final Map<Long,List<Voxel>> columns = new HashMap<>();
    private final Map<UUID,Placement> worlds = new HashMap<>();
    private final BlockData[] palette;
    private final BlockData air = Bukkit.createBlockData(Material.AIR);
    private final BlockData shell = Bukkit.createBlockData(Material.POLISHED_BLACKSTONE_BRICKS);
    private record Repair(World world,int cx,int cz,Site site) {}
    private final Map<String,Integer> fresh=new HashMap<>();
    private final Set<String> dirty=new HashSet<>(),pending=new HashSet<>();
    private final ArrayDeque<Repair> repairs=new ArrayDeque<>();
    private static final int FRESH_TICKS=12000;
    private final File ledger;
    private final YamlConfiguration saved;

    public NetherArenaWorldgen(VoidscapePlugin plugin) throws IOException {
        this.plugin = plugin;
        InputStream resource=plugin.getResource("dimensional-sanctuaries/nether-arena.ows");
        if(resource==null)throw new IOException("Missing Nether arena geometry");
        try(var in=new DataInputStream(new BufferedInputStream(new GZIPInputStream(resource),65536))){
            if(in.readInt()!=0x4f575331)throw new IOException("Unsupported Nether arena geometry");
            int size=in.readUnsignedShort();if(size<1)throw new IOException("Empty palette");
            palette=new BlockData[size];
            for(int i=0;i<size;i++)palette[i]=Bukkit.createBlockData(in.readUTF());
            int n=in.readInt();if(n<1||n>2_000_000)throw new IOException("Invalid voxel count");
            for(int i=0;i<n;i++){
                int x=in.readShort(),y=in.readShort(),z=in.readShort(),p=in.readUnsignedShort();
                if(Math.abs(x)>64||Math.abs(z)>64||y< -4||y>47||p>=size)throw new IOException("Nether arena geometry outside bounds");
                columns.computeIfAbsent(key(x,z),ignored->new ArrayList<>()).add(new Voxel(y,p));
            }
            if(in.readUnsignedShort()!=0)throw new IOException("Unexpected Nether arena chests");
        }
        ledger=new File(plugin.getDataFolder(),"nether-arenas.yml");
        saved=YamlConfiguration.loadConfiguration(ledger);
        plugin.getServer().getPluginManager().registerEvents(this,plugin);
        for(World world:Bukkit.getWorlds())attach(world);
        Bukkit.getScheduler().runTaskTimer(plugin,this::repairTick,1,1);
    }
    private static long key(int x,int z){return ((long)x<<32)|(z&0xffffffffL);}
    @EventHandler public void init(WorldInitEvent event){
        try{attach(event.getWorld());}catch(IOException ex){throw new IllegalStateException("Cannot preserve Nether arena generation history",ex);}
    }
    private void attach(World world)throws IOException {
        if(!plugin.getConfig().getBoolean("nether-arena.enabled",false)||world.getEnvironment()!=World.Environment.NETHER||world.getGenerator()!=null||worlds.containsKey(world.getUID()))return;
        var allowed=plugin.getConfig().getStringList("nether-arena.worlds");
        if(!allowed.isEmpty()&&!allowed.contains(world.getName()))return;
        if(world.getMinHeight()>FLOOR-4||world.getMaxHeight()<=FLOOR+48){plugin.getLogger().warning("Nether arena disabled in "+world.getName()+": unsupported world height");return;}
        String root="worlds."+world.getUID();
        if(!saved.contains(root)){
            Path folder=world.getWorldFolder().toPath();
            Set<Long> old=new HashSet<>();
            old.addAll(SkyPlacementHistory.capture(folder.resolve("region"),SPACING));
            old.addAll(SkyPlacementHistory.capture(folder.resolve("DIM-1/region"),SPACING));
            old.addAll(SkyPlacementHistory.capture(folder.resolve("dimensions/minecraft/the_nether/region"),SPACING));
            old.addAll(SkyPlacementHistory.captureWorld(Bukkit.getWorldContainer().toPath(),folder,world.getName(),SPACING));
            // Paper and Bukkit may report either a dimension folder or a world root.
            Path parent=folder.toAbsolutePath().normalize();
            for(int depth=0;depth<6&&parent!=null;depth++,parent=parent.getParent()){
                old.addAll(SkyPlacementHistory.capture(parent.resolve("DIM-1/region"),SPACING));
                old.addAll(SkyPlacementHistory.capture(parent.resolve("dimensions/minecraft/the_nether/region"),SPACING));
            }
            for(Chunk loaded:world.getLoadedChunks())old.add(key(Math.floorDiv(loaded.getX(),SPACING),Math.floorDiv(loaded.getZ(),SPACING)));
            saved.set(root+".name",world.getName());saved.set(root+".version",1);
            saved.set(root+".excluded-cells",old.stream().sorted().toList());persist();
        }
        if(saved.getInt(root+".version")!=1)throw new IOException("Unsupported Nether arena placement version");
        Placement placement=new Placement(world.getSeed(),new HashSet<>(saved.getLongList(root+".excluded-cells")));
        worlds.put(world.getUID(),placement);world.getPopulators().add(placement);
        plugin.getLogger().info("Experimental Nether arena attached to "+world.getName()+"; excluded old cells: "+placement.excluded.size());
    }
    private void persist() throws IOException {
        Path target=ledger.toPath(),tmp=target.resolveSibling(target.getFileName()+".tmp");
        Files.writeString(tmp,saved.saveToString());
        try{Files.move(tmp,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
        catch(AtomicMoveNotSupportedException ex){Files.move(tmp,target,StandardCopyOption.REPLACE_EXISTING);}
    }
    public boolean enabled(World world){return world!=null&&worlds.containsKey(world.getUID());}
    /** Pure search over the exact same placement used by the populator. */
    public Site findNearest(World world,int x,int z,int limit){
        Placement p=world==null?null:worlds.get(world.getUID());if(p==null)return null;
        int gx=Math.floorDiv(x,SPACING*16),gz=Math.floorDiv(z,SPACING*16);
        Site best=null;long bestDistance=Long.MAX_VALUE;
        for(int dx=-limit;dx<=limit;dx++)for(int dz=-limit;dz<=limit;dz++){
            Site s=p.site(gx+dx,gz+dz);if(s==null)continue;
            long ox=(long)s.x-x,oz=(long)s.z-z,d=ox*ox+oz*oz;
            if(d<bestDistance){best=s;bestDistance=d;}
        }
        return best;
    }
    private String chunkKey(World world,int cx,int cz){return world.getUID()+":"+cx+":"+cz;}
    private boolean intersects(Site s,int cx,int cz){
        int x=cx*16-s.x,z=cz*16-s.z;
        return x<=RADIUS&&x+15>=-RADIUS&&z<=RADIUS&&z+15>=-RADIUS;
    }
    /** Nearby vanilla decorators can write into a chunk after its own populator. */
    @EventHandler(priority=EventPriority.MONITOR)
    public void populated(ChunkPopulateEvent event){
        World world=event.getWorld();Placement p=worlds.get(world.getUID());if(p==null)return;
        int cx=event.getChunk().getX(),cz=event.getChunk().getZ();
        Site site=p.site(Math.floorDiv(cx,SPACING),Math.floorDiv(cz,SPACING));
        if(site==null||Math.abs(cx*16-site.x)>RADIUS+32||Math.abs(cz*16-site.z)>RADIUS+32)return;
        if(intersects(site,cx,cz))fresh.put(chunkKey(world,cx,cz),Bukkit.getCurrentTick());
        Bukkit.getScheduler().runTask(plugin,()->{
            for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++){
                int x=cx+dx,z=cz+dz;String id=chunkKey(world,x,z);
                Integer tick=fresh.get(id);
                if(tick==null||Bukkit.getCurrentTick()-tick>FRESH_TICKS||dirty.contains(id)||!world.isChunkLoaded(x,z)||!intersects(site,x,z))continue;
                if(pending.add(id))repairs.add(new Repair(world,x,z,site));
            }
        });
    }
    @EventHandler(priority=EventPriority.MONITOR)
    public void loaded(ChunkLoadEvent event){
        World world=event.getWorld();int cx=event.getChunk().getX(),cz=event.getChunk().getZ();
        String id=chunkKey(world,cx,cz);Integer tick=fresh.get(id);
        if(tick==null||Bukkit.getCurrentTick()-tick>FRESH_TICKS||dirty.contains(id))return;
        Placement p=worlds.get(world.getUID());if(p==null)return;
        Site site=p.site(Math.floorDiv(cx,SPACING),Math.floorDiv(cz,SPACING));
        if(site!=null&&intersects(site,cx,cz)&&pending.add(id))repairs.add(new Repair(world,cx,cz,site));
    }
    /** Settle the freshly generated destination before an administrator enters. */
    public void settleDestination(World world,int cx,int cz){
        String id=chunkKey(world,cx,cz);Integer tick=fresh.get(id);
        if(tick==null||Bukkit.getCurrentTick()-tick>FRESH_TICKS||dirty.contains(id)||!world.isChunkLoaded(cx,cz))return;
        Placement p=worlds.get(world.getUID());if(p==null)return;
        Site site=p.site(Math.floorDiv(cx,SPACING),Math.floorDiv(cz,SPACING));
        if(site!=null&&intersects(site,cx,cz))repair(new Repair(world,cx,cz,site));
    }
    private void repairTick(){
        for(int budget=0;budget<1&&!repairs.isEmpty();budget++){
            Repair job=repairs.remove();String id=chunkKey(job.world,job.cx,job.cz);pending.remove(id);
            Integer tick=fresh.get(id);
            if(tick==null||Bukkit.getCurrentTick()-tick>FRESH_TICKS||dirty.contains(id)||!job.world.isChunkLoaded(job.cx,job.cz))continue;
            repair(job);
        }
        if(Bukkit.getCurrentTick()%1200==0){
            int now=Bukkit.getCurrentTick();
            fresh.entrySet().removeIf(e->{if(now-e.getValue()<=FRESH_TICKS)return false;dirty.remove(e.getKey());return true;});
        }
    }
    private void repair(Repair job){
        World world=job.world;Site site=job.site;
        for(int x=job.cx*16;x<job.cx*16+16;x++)for(int z=job.cz*16;z<job.cz*16+16;z++){
            int dx=x-site.x,dz=z-site.z;if(Math.abs(dx)>RADIUS||Math.abs(dz)>RADIUS)continue;
            boolean wall=Math.abs(dx)==RADIUS||Math.abs(dz)==RADIUS;
            BlockData[] expected=new BlockData[53];
            for(int i=0;i<expected.length;i++)expected[i]=wall||i==0||i==52?shell:air;
            for(Voxel v:columns.getOrDefault(key(dx,dz),List.of()))expected[v.y+4]=palette[v.palette];
            for(int i=0;i<expected.length;i++){
                Block block=world.getBlockAt(x,FLOOR-4+i,z);
                if(block.getType()!=Material.BEDROCK&&!block.getBlockData().equals(expected[i]))block.setBlockData(expected[i],false);
            }
        }
    }
    private void changed(Block block){
        String id=chunkKey(block.getWorld(),block.getX()>>4,block.getZ()>>4);
        if(fresh.containsKey(id))dirty.add(id);
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void placed(BlockPlaceEvent e){changed(e.getBlock());}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void broken(BlockBreakEvent e){changed(e.getBlock());}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void bucketEmpty(PlayerBucketEmptyEvent e){changed(e.getBlockClicked().getRelative(e.getBlockFace()));}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void bucketFill(PlayerBucketFillEvent e){changed(e.getBlockClicked());}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void pistonExtend(BlockPistonExtendEvent e){for(Block b:e.getBlocks()){changed(b);changed(b.getRelative(e.getDirection()));}}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void pistonRetract(BlockPistonRetractEvent e){for(Block b:e.getBlocks()){changed(b);changed(b.getRelative(e.getDirection()));}}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void explosion(EntityExplodeEvent e){e.blockList().forEach(this::changed);}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void explosion(BlockExplodeEvent e){e.blockList().forEach(this::changed);}
    public void close(){
        for(var entry:worlds.entrySet()){
            World world=Bukkit.getWorld(entry.getKey());if(world!=null&&!Bukkit.isStopping())world.getPopulators().remove(entry.getValue());
        }
        worlds.clear();fresh.clear();dirty.clear();pending.clear();repairs.clear();
    }
    private final class Placement extends BlockPopulator {
        private final long seed;private final Set<Long> excluded;
        Placement(long seed,Set<Long> excluded){this.seed=seed;this.excluded=Set.copyOf(excluded);}
        Site site(int gx,int gz){
            if(excluded.contains(key(gx,gz)))return null;
            Random random=new Random(seed ^ ((long)gx*341873128712L) ^ ((long)gz*132897987541L) ^ 0x4e45544845524131L);
            if(random.nextDouble()>=.65)return null;
            long cx=(long)gx*SPACING+SPACING/2+random.nextInt(25)-12;
            long cz=(long)gz*SPACING+SPACING/2+random.nextInt(25)-12;
            long x=cx*16+8,z=cz*16+8;
            if(x<-29999800L||x>29999800L||z<-29999800L||z>29999800L)return null;
            return new Site((int)x,(int)z);
        }
        @Override public void populate(WorldInfo world,Random ignored,int cx,int cz,LimitedRegion region){
            int gx=Math.floorDiv(cx,SPACING),gz=Math.floorDiv(cz,SPACING);
            Site site=site(gx,gz);if(site==null)return;
            int x0=cx*16,z0=cz*16;
            for(int x=x0;x<x0+16;x++)for(int z=z0;z<z0+16;z++){
                int dx=x-site.x,dz=z-site.z;if(Math.abs(dx)>RADIUS||Math.abs(dz)>RADIUS)continue;
                boolean wall=Math.abs(dx)==RADIUS||Math.abs(dz)==RADIUS;
                for(int y=FLOOR-4;y<=FLOOR+48;y++){
                    // Keep even unusual bedrock deposits intact.
                    if(region.getType(x,y,z)==Material.BEDROCK)continue;
                    region.setBlockData(x,y,z,wall||y==FLOOR-4||y==FLOOR+48?shell:air);
                }
                for(Voxel voxel:columns.getOrDefault(key(dx,dz),List.of())){
                    int y=FLOOR+voxel.y;
                    if(region.getType(x,y,z)!=Material.BEDROCK)region.setBlockData(x,y,z,palette[voxel.palette]);
                }
            }
        }
    }
}
