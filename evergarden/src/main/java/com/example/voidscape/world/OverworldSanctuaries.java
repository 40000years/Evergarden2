package com.example.voidscape.world;

import com.example.voidscape.VoidscapePlugin;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.world.WorldInitEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.generator.*;
import org.bukkit.loot.LootTables;
import org.bukkit.loot.LootContext;
import org.bukkit.persistence.PersistentDataType;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPInputStream;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** Independent vanilla-Overworld generation. Never loads chunks from a populator.
 * Shipped immutable geometry is clipped to the chunk being populated. Placement,
 * biome sampling and old-region exclusions are seed-stable across restarts.
 */
public final class OverworldSanctuaries implements Listener, CommandExecutor, TabCompleter {
    public enum Kind {
        ACHERON("acheron",Biome.DARK_FOREST,80,5),
        PHLEGETHON("phlegethon",Biome.BADLANDS,94,2),
        COCYTUS("cocytus",Biome.SNOWY_PLAINS,80,6),
        BANSHEE("banshee",Biome.SWAMP,64,6),
        MALEFEASANCE("malefeasance",Biome.BIRCH_FOREST,80,7),
        STYX("styx",Biome.SAVANNA,80,7);
        public final String id; public final Biome biome; public final int floor;
        /** Default region size in cells for fresh worlds; rarer biomes get smaller regions. */
        public final int regionCells;
        Kind(String id,Biome biome,int floor,int regionCells){this.id=id;this.biome=biome;this.floor=floor;this.regionCells=regionCells;}
        public static Kind parse(String id){for(var k:values())if(k.id.equalsIgnoreCase(id))return k;return null;}
    }
    /** Region placement also accepts these close relatives at the same ground height (not plateaus or peaks). */
    private static final Map<Kind,Set<Biome>> FAMILIES=Map.of(
            Kind.ACHERON,Set.of(Biome.DARK_FOREST),
            Kind.PHLEGETHON,Set.of(Biome.BADLANDS,Biome.ERODED_BADLANDS),
            Kind.COCYTUS,Set.of(Biome.SNOWY_PLAINS,Biome.ICE_SPIKES),
            Kind.BANSHEE,Set.of(Biome.SWAMP,Biome.MANGROVE_SWAMP),
            Kind.MALEFEASANCE,Set.of(Biome.BIRCH_FOREST,Biome.OLD_GROWTH_BIRCH_FOREST),
            Kind.STYX,Set.of(Biome.SAVANNA));
    /** Rarer biomes claim a contested cell first. */
    private static final List<Kind> PRIORITY=List.of(Kind.PHLEGETHON,Kind.ACHERON,Kind.BANSHEE,Kind.MALEFEASANCE,Kind.COCYTUS,Kind.STYX);
    /** Offsets tried around a biome hit, nearest first, up to 96 blocks in 48-block steps. */
    private static final int[][] SLIDES=java.util.stream.IntStream.range(0,25).mapToObj(i->new int[]{(i%5-2)*48,(i/5-2)*48})
            .sorted(java.util.Comparator.comparingInt(o->o[0]*o[0]+o[1]*o[1])).toArray(int[][]::new);
    public record Voxel(int y,int palette){}
    public record Point(int x,int y,int z){}
    public record Site(Kind kind,int x,int z,long seed){}
    public static final class Geometry {
        public final BlockData[] palette;
        public final Map<Long,List<Voxel>> columns;
        public final List<Point> chests;
        public final int count;
        private Geometry(InputStream resource)throws IOException {
            if(resource==null)throw new IOException("Missing sanctuary asset");
            try(var in=new DataInputStream(new BufferedInputStream(new GZIPInputStream(resource),65536))){
                if(in.readInt()!=0x4f575331)throw new IOException("Unsupported sanctuary geometry");
                palette=new BlockData[in.readUnsignedShort()];
                for(int i=0;i<palette.length;i++)palette[i]=Bukkit.createBlockData(in.readUTF());
                int n=in.readInt();if(n<1||n>2_000_000)throw new IOException("Invalid voxel count");count=n;
                Map<Long,List<Voxel>> index=new HashMap<>();
                for(int i=0;i<n;i++){
                    int x=in.readShort(),y=in.readShort(),z=in.readShort(),p=in.readUnsignedShort();
                    if(Math.abs(x)>112||Math.abs(z)>112||y< -16||y>150||p>=palette.length)throw new IOException("Geometry outside bounds");
                    index.computeIfAbsent(key(x,z),ignored->new ArrayList<>()).add(new Voxel(y,p));
                }
                index.replaceAll((k,v)->v.stream().sorted(java.util.Comparator.comparingInt(Voxel::y)).toList());
                // Packed local coordinates have colliding Long hashes (x XOR z).
                // Keep HashMap's collision trees; Map.copyOf's linear-probe table
                // makes both asset loading and each chunk lookup quadratic here.
                columns=Collections.unmodifiableMap(index);
                List<Point> caches=new ArrayList<>();int size=in.readUnsignedShort();
                if(size!=8)throw new IOException("Expected eight treasure caches");
                for(int i=0;i<size;i++)caches.add(new Point(in.readShort(),in.readShort(),in.readShort()));
                chests=List.copyOf(caches);
            }
        }
    }
    private final VoidscapePlugin plugin;
    private final EnumMap<Kind,Geometry> geometry=new EnumMap<>(Kind.class);
    private final EnumMap<Kind,Geometry> legacyGeometry=new EnumMap<>(Kind.class);
    private final EnumMap<Kind,Geometry> previousGeometry=new EnumMap<>(Kind.class);
    private final EnumMap<Kind,Geometry> lastGeometry=new EnumMap<>(Kind.class);
    private final EnumMap<Kind,Geometry> recentGeometry=new EnumMap<>(Kind.class);
    private final EnumMap<Kind,Map<Long,List<Change>>> changes=new EnumMap<>(Kind.class);
    private final EnumMap<Kind,Map<Long,List<Change>>> previousChanges=new EnumMap<>(Kind.class);
    private final EnumMap<Kind,Map<Long,List<Change>>> lastChanges=new EnumMap<>(Kind.class);
    private final EnumMap<Kind,Map<Long,List<Change>>> recentChanges=new EnumMap<>(Kind.class);
    private record Change(int x,int y,int z,BlockData before,BlockData after){}
    private record Patch(Chunk chunk,Site site,List<Change> changes,int[] cursor){}
    private final ArrayDeque<Patch> patches=new ArrayDeque<>();
    private final Set<String> queued=new HashSet<>();
    private final Map<UUID,UUID> searches=new ConcurrentHashMap<>();
    private final Map<UUID,Placement> worlds=new HashMap<>();
    private final File ledger;
    private final YamlConfiguration saved;
    private final SanctuaryHabitat habitat;
    public SanctuaryHabitat habitat(){return habitat;}
    public static long key(int x,int z){return ((long)x<<32)|(z&0xffffffffL);}
    public OverworldSanctuaries(VoidscapePlugin plugin)throws IOException {
        this.plugin=plugin;
        ledger=new File(plugin.getDataFolder(),"overworld-sanctuaries.yml");
        saved=YamlConfiguration.loadConfiguration(ledger);
        for(Kind k:Kind.values()){
            geometry.put(k,new Geometry(plugin.getResource("overworld-sanctuaries/"+k.id+".ows")));
            legacyGeometry.put(k,new Geometry(plugin.getResource("overworld-sanctuaries/v1/"+k.id+".ows")));
            previousGeometry.put(k,new Geometry(plugin.getResource("overworld-sanctuaries/v2/"+k.id+".ows")));
            lastGeometry.put(k,new Geometry(plugin.getResource("overworld-sanctuaries/v3/"+k.id+".ows")));
            recentGeometry.put(k,new Geometry(plugin.getResource("overworld-sanctuaries/v4/"+k.id+".ows")));
            changes.put(k,diff(legacyGeometry.get(k),geometry.get(k)));
            previousChanges.put(k,diff(previousGeometry.get(k),geometry.get(k)));
            lastChanges.put(k,diff(lastGeometry.get(k),geometry.get(k)));
            recentChanges.put(k,diff(recentGeometry.get(k),geometry.get(k)));
        }
        habitat=new SanctuaryHabitat(plugin,this);
        plugin.getServer().getPluginManager().registerEvents(this,plugin);
        for(World world:Bukkit.getWorlds())attach(world);
        PluginCommand cmd=plugin.getCommand("sanctuary");
        if(cmd!=null){cmd.setExecutor(this);cmd.setTabCompleter(this);}
        Bukkit.getScheduler().runTaskTimer(plugin,this::patchTick,1,1);
        Bukkit.getScheduler().runTask(plugin,()->{for(World w:Bukkit.getWorlds())for(Chunk c:w.getLoadedChunks())settle(c);});
    }
    @EventHandler public void init(WorldInitEvent event){
        try{attach(event.getWorld());}catch(IOException ex){throw new IllegalStateException("Cannot preserve Overworld generation history",ex);}
    }
    private void attach(World world)throws IOException {
        if(world.getEnvironment()!=World.Environment.NORMAL||world.getGenerator()!=null||worlds.containsKey(world.getUID()))return;
        if(!plugin.getConfig().getBoolean("overworld-sanctuaries.enabled",true))return;
        var allowed=plugin.getConfig().getStringList("overworld-sanctuaries.worlds");
        if(!allowed.isEmpty()&&!allowed.contains(world.getName()))return;
        String root="worlds."+world.getUID();
        if(!saved.contains(root)){
            int spacing=Math.clamp(plugin.getConfig().getInt("overworld-sanctuaries.spacing-chunks",64),32,128);
            double chance=plugin.getConfig().getDouble("overworld-sanctuaries.chance",.65);
            if(!Double.isFinite(chance))chance=.65;
            Set<Long> old=new HashSet<>();
            Path folder=world.getWorldFolder().toPath();
            old.addAll(SkyPlacementHistory.capture(folder.resolve("region"),spacing));
            old.addAll(SkyPlacementHistory.capture(folder.resolve("dimensions/minecraft/overworld/region"),spacing));
            old.addAll(SkyPlacementHistory.captureWorld(Bukkit.getWorldContainer().toPath(),folder,world.getName(),spacing));
            saved.set(root+".name",world.getName());saved.set(root+".spacing-chunks",spacing);
            saved.set(root+".chance",Math.clamp(chance,0,1));
            saved.set(root+".geometry-version",5);
            saved.set(root+".excluded-cells",old.stream().sorted().toList());
            persist();
        }
        if(saved.getInt(root+".geometry-version")==1){
            int spacing=saved.getInt(root+".spacing-chunks");Path folder=world.getWorldFolder().toPath();Set<Long> legacy=new HashSet<>();
            legacy.addAll(SkyPlacementHistory.capture(folder.resolve("region"),spacing));
            legacy.addAll(SkyPlacementHistory.capture(folder.resolve("dimensions/minecraft/overworld/region"),spacing));
            legacy.addAll(SkyPlacementHistory.captureWorld(Bukkit.getWorldContainer().toPath(),folder,world.getName(),spacing));
            saved.set(root+".legacy-design-cells",legacy.stream().sorted().toList());
            saved.set(root+".geometry-version",2);persist();
        }
        if(saved.getInt(root+".geometry-version")==2){
            int spacing=saved.getInt(root+".spacing-chunks");Path folder=world.getWorldFolder().toPath();Set<Long> previous=new HashSet<>();
            previous.addAll(SkyPlacementHistory.capture(folder.resolve("region"),spacing));
            previous.addAll(SkyPlacementHistory.capture(folder.resolve("dimensions/minecraft/overworld/region"),spacing));
            previous.addAll(SkyPlacementHistory.captureWorld(Bukkit.getWorldContainer().toPath(),folder,world.getName(),spacing));
            saved.set(root+".previous-design-cells",previous.stream().sorted().toList());
            saved.set(root+".geometry-version",3);persist();
        }
        if(saved.getInt(root+".geometry-version")==3){
            int spacing=saved.getInt(root+".spacing-chunks");Path folder=world.getWorldFolder().toPath();Set<Long> last=new HashSet<>();
            last.addAll(SkyPlacementHistory.capture(folder.resolve("region"),spacing));
            last.addAll(SkyPlacementHistory.capture(folder.resolve("dimensions/minecraft/overworld/region"),spacing));
            last.addAll(SkyPlacementHistory.captureWorld(Bukkit.getWorldContainer().toPath(),folder,world.getName(),spacing));
            saved.set(root+".last-design-cells",last.stream().sorted().toList());
            saved.set(root+".geometry-version",4);persist();
        }
        if(saved.getInt(root+".geometry-version")==4){
            int spacing=saved.getInt(root+".spacing-chunks");Path folder=world.getWorldFolder().toPath();Set<Long> recent=new HashSet<>();
            recent.addAll(SkyPlacementHistory.capture(folder.resolve("region"),spacing));
            recent.addAll(SkyPlacementHistory.capture(folder.resolve("dimensions/minecraft/overworld/region"),spacing));
            recent.addAll(SkyPlacementHistory.captureWorld(Bukkit.getWorldContainer().toPath(),folder,world.getName(),spacing));
            saved.set(root+".recent-design-cells",recent.stream().sorted().toList());
            saved.set(root+".geometry-version",5);persist();
        }
        if(saved.getInt(root+".geometry-version")!=5)throw new IOException("Unsupported Overworld sanctuary version");
        if(saved.getInt(root+".placement-version",1)==1){
            // Cells that already have terrain keep the one-roll placement; only untouched cells use regions.
            int spacing=saved.getInt(root+".spacing-chunks");Path folder=world.getWorldFolder().toPath();Set<Long> classic=new HashSet<>();
            classic.addAll(SkyPlacementHistory.capture(folder.resolve("region"),spacing));
            classic.addAll(SkyPlacementHistory.capture(folder.resolve("dimensions/minecraft/overworld/region"),spacing));
            classic.addAll(SkyPlacementHistory.captureWorld(Bukkit.getWorldContainer().toPath(),folder,world.getName(),spacing));
            saved.set(root+".classic-placement-cells",classic.stream().sorted().toList());
            for(Kind k:Kind.values())saved.set(root+".region-cells."+k.id,Math.clamp(plugin.getConfig().getInt("overworld-sanctuaries.region-cells."+k.id,k.regionCells),2,8));
            saved.set(root+".placement-version",2);persist();
        }
        if(saved.getInt(root+".placement-version")!=2)throw new IOException("Unsupported Overworld sanctuary placement version");
        var placement=new Placement(world,saved.getInt(root+".spacing-chunks"),saved.getDouble(root+".chance"),new HashSet<>(saved.getLongList(root+".excluded-cells")),new HashSet<>(saved.getLongList(root+".legacy-design-cells")),new HashSet<>(saved.getLongList(root+".previous-design-cells")),new HashSet<>(saved.getLongList(root+".last-design-cells")),new HashSet<>(saved.getLongList(root+".recent-design-cells")),
                new HashSet<>(saved.getLongList(root+".classic-placement-cells")),regionCells(root));
        worlds.put(world.getUID(),placement);world.getPopulators().add(placement);
        plugin.getLogger().info("Six Rivers sanctuaries attached to "+world.getName()+"; excluded old cells: "+placement.excluded.size());
    }
    private Map<Kind,Integer> regionCells(String root){
        Map<Kind,Integer> cells=new EnumMap<>(Kind.class);
        for(Kind k:Kind.values())cells.put(k,Math.clamp(saved.getInt(root+".region-cells."+k.id,k.regionCells),2,8));
        return cells;
    }
    private void persist()throws IOException {
        Path target=ledger.toPath(),tmp=target.resolveSibling(target.getFileName()+".tmp");
        Files.writeString(tmp,saved.saveToString());
        try{Files.move(tmp,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
        catch(AtomicMoveNotSupportedException ex){Files.move(tmp,target,StandardCopyOption.REPLACE_EXISTING);}
    }
    private Map<Long,List<Change>> diff(Geometry before,Geometry after){
        Set<Long> columns=new HashSet<>(before.columns.keySet());columns.addAll(after.columns.keySet());
        Map<Long,List<Change>> result=new HashMap<>();BlockData air=Bukkit.createBlockData(Material.AIR);
        for(long key:columns){
            int x=(int)(key>>32),z=(int)key;BlockData[] a=new BlockData[168],b=new BlockData[168];
            Arrays.fill(a,air);Arrays.fill(b,air);
            for(Voxel v:before.columns.getOrDefault(key,List.of()))a[v.y+16]=before.palette[v.palette];
            for(Voxel v:after.columns.getOrDefault(key,List.of()))b[v.y+16]=after.palette[v.palette];
            for(int i=0;i<a.length;i++)if(!a[i].equals(b[i]))
                result.computeIfAbsent(key(Math.floorDiv(x,16),Math.floorDiv(z,16)),ignored->new ArrayList<>()).add(new Change(x,i-16,z,a[i],b[i]));
        }
        result.replaceAll((k,v)->List.copyOf(v));return Collections.unmodifiableMap(result);
    }
    private String identity(Chunk c){return c.getWorld().getUID()+":"+c.getX()+":"+c.getZ();}
    @EventHandler(priority=EventPriority.MONITOR) public void loaded(ChunkLoadEvent event){
        Chunk chunk=event.getChunk();Bukkit.getScheduler().runTask(plugin,()->{if(chunk.isLoaded())settle(chunk);});
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void opened(InventoryOpenEvent event){
        if(event.getInventory().getHolder() instanceof Chest chest)prepareLoot(chest);
    }
    @EventHandler public void quit(PlayerQuitEvent event){searches.remove(event.getPlayer().getUniqueId());}
    @EventHandler public void changed(PlayerChangedWorldEvent event){searches.remove(event.getPlayer().getUniqueId());}
    private void settle(Chunk chunk){
        Placement layout=worlds.get(chunk.getWorld().getUID());
        if(layout!=null){
            Site site=layout.cell(Math.floorDiv(chunk.getX(),layout.spacing),Math.floorDiv(chunk.getZ(),layout.spacing));
            if(site!=null&&layout.sourceVersion(site)<5){
                queueUpgrade(chunk,site,layout.sourceVersion(site));
                if(layout.legacy(site))convertLegacyLoot(chunk,site);
            }
            if(site!=null&&!queued.contains(identity(chunk)))habitat.settle(chunk,site);
        }
        prepareLoot(chunk);
    }
    public void convertLegacyLoot(Chunk chunk,Site site){
        var caches=legacyGeometry.get(site.kind).chests;
        for(int i=0;i<caches.size();i++){
            Point p=caches.get(i);int x=site.x+p.x,z=site.z+p.z;
            if(Math.floorDiv(x,16)!=chunk.getX()||Math.floorDiv(z,16)!=chunk.getZ())continue;
            if(!(chunk.getWorld().getBlockAt(x,site.kind.floor+p.y,z).getState() instanceof Chest chest))continue;
            Long seed=chest.getPersistentDataContainer().get(plugin.key("sanctuary_loot_seed"),PersistentDataType.LONG);
            if(seed==null||chest.getPersistentDataContainer().getOrDefault(plugin.key("sanctuary_loot_profile"),PersistentDataType.INTEGER,0)==2)continue;
            var context=new LootContext.Builder(chest.getLocation()).build();
            var previous=(i<6?LootTables.SIMPLE_DUNGEON:LootTables.ABANDONED_MINESHAFT).getLootTable();
            if(chest.getLootTable()!=null&&!chest.getLootTable().getKey().equals(previous.getKey()))continue;
            if(chest.getLootTable()==null){
                var expected=Bukkit.createInventory(null,27);previous.fillInventory(expected,new Random(seed),context);
                // No open-history exists in v1: migrate only a byte-for-byte untouched
                // seeded inventory. Empty, partially looted and edited caches stay intact.
                if(!Arrays.equals(expected.getContents(),chest.getBlockInventory().getContents()))continue;
            }
            var replacement=Bukkit.createInventory(null,27);
            LootTables.END_CITY_TREASURE.getLootTable().fillInventory(replacement,new Random(seed),context);
            if(!(chest.getBlock().getState() instanceof Chest current))continue;
            current.setLootTable(null);
            current.getPersistentDataContainer().set(plugin.key("sanctuary_loot_profile"),PersistentDataType.INTEGER,2);
            current.update(false,false);current.getBlockInventory().setContents(replacement.getContents());
            prepareLoot((Chest)current.getBlock().getState());
        }
    }
    public void prepareLoot(Chunk chunk){for(BlockState state:chunk.getTileEntities())if(state instanceof Chest chest)prepareLoot(chest);}
    private void prepareLoot(Chest initial){
        if(initial.getPersistentDataContainer().getOrDefault(plugin.key("sanctuary_loot_profile"),PersistentDataType.INTEGER,0)!=2)return;
        boolean awarded=initial.getPersistentDataContainer().getOrDefault(plugin.key("sanctuary_loot_awarded"),PersistentDataType.INTEGER,0)==2;
        if(awarded&&initial.getPersistentDataContainer().has(plugin.key("sanctuary_trim_profile"),PersistentDataType.INTEGER))return;
        var items=initial.getBlockInventory().getContents();
        if(!(initial.getBlock().getState() instanceof Chest chest))return;
        long seed=chest.getPersistentDataContainer().getOrDefault(plugin.key("sanctuary_loot_seed"),PersistentDataType.LONG,0L);
        chest.getPersistentDataContainer().set(plugin.key("sanctuary_loot_awarded"),PersistentDataType.INTEGER,2);
        chest.update(false,false);
        double chance=plugin.getConfig().getDouble("overworld-sanctuaries.treasure.over-cap-chance",.15);
        chance=Double.isFinite(chance)?Math.clamp(chance,0,1):.15;
        Random random=new Random(seed^0x454e444349545932L);
        var live=chest.getBlockInventory();
        if(awarded){
            // Only migrate old, wholly untouched caches. Player edits and empty caches survive.
            var expected=Bukkit.createInventory(null,27);
            LootTables.END_CITY_TREASURE.getLootTable().fillInventory(expected,new Random(seed),new LootContext.Builder(chest.getLocation()).build());
            var before=expected.getContents();
            for(int slot=0;slot<before.length;slot++)before[slot]=SanctuaryLoot.upgrade(before[slot],random,chance,plugin);
            if(Arrays.equals(items,before))SanctuaryLoot.replaceTrims(live,seed);
        }else{
            for(int slot=0;slot<items.length;slot++){
                var item=items[slot];var upgraded=SanctuaryLoot.upgrade(item,random,chance,plugin);
                if(upgraded!=item)live.setItem(slot,upgraded);
            }
            SanctuaryLoot.replaceTrims(live,seed);
            ElementalScrollTreasure.add(chest,"sanctuary");
            ElementalStaffTreasure.add(plugin,chest,"overworld-chest");
        }
        chest=(Chest)chest.getBlock().getState();
        chest.getPersistentDataContainer().set(plugin.key("sanctuary_trim_profile"),PersistentDataType.INTEGER,1);chest.update(false,false);
    }
    public void queueUpgrade(Chunk chunk,Site site){
        Placement layout=worlds.get(chunk.getWorld().getUID());
        queueUpgrade(chunk,site,layout==null?1:layout.sourceVersion(site));
    }
    public void queueUpgrade(Chunk chunk,Site site,int sourceVersion){
        int stamped=chunk.getPersistentDataContainer().getOrDefault(plugin.key("sanctuary_geometry"),PersistentDataType.INTEGER,0);
        if(stamped>=5||!queued.add(identity(chunk)))return;
        if(stamped>=2)sourceVersion=stamped;
        if(sourceVersion<1||sourceVersion>4){queued.remove(identity(chunk));return;}
        var delta=sourceVersion==1?changes:sourceVersion==2?previousChanges:sourceVersion==3?lastChanges:recentChanges;
        List<Change> selected=new ArrayList<>();int x1=chunk.getX()*16-site.x,z1=chunk.getZ()*16-site.z;
        for(int cx=Math.floorDiv(x1,16);cx<=Math.floorDiv(x1+15,16);cx++)
            for(int cz=Math.floorDiv(z1,16);cz<=Math.floorDiv(z1+15,16);cz++)
                for(Change c:delta.get(site.kind).getOrDefault(key(cx,cz),List.of()))
                    if(c.x>=x1&&c.x<=x1+15&&c.z>=z1&&c.z<=z1+15)selected.add(c);
        if(selected.isEmpty()){chunk.getPersistentDataContainer().set(plugin.key("sanctuary_geometry"),PersistentDataType.INTEGER,5);queued.remove(identity(chunk));return;}
        chunk.addPluginChunkTicket(plugin);patches.add(new Patch(chunk,site,selected,new int[]{0}));
    }
    public boolean upgrading(){return !patches.isEmpty();}
    public boolean upgrading(Chunk chunk){return queued.contains(identity(chunk));}
    private void patchTick(){
        int budget=5000;
        while(budget>0&&!patches.isEmpty()){
            Patch job=patches.peek();
            while(budget>0&&job.cursor[0]<job.changes.size()){
                Change change=job.changes.get(job.cursor[0]++);budget--;
                var block=job.chunk.getWorld().getBlockAt(job.site.x+change.x,job.site.kind.floor+change.y,job.site.z+change.z);
                if(block.getType()!=change.before.getMaterial()||!change.before.matches(block.getBlockData()))continue;
                // Treasure chests are an immutable save contract. Empty original
                // decorative containers can be removed with the old clutter;
                // a furnace/barrel holding player belongings always survives.
                if(block.getState() instanceof Container container
                    &&(container instanceof Chest||!container.getInventory().isEmpty()))continue;
                block.setBlockData(change.after,false);
            }
            if(job.cursor[0]==job.changes.size()){
                job.chunk.getPersistentDataContainer().set(plugin.key("sanctuary_geometry"),PersistentDataType.INTEGER,5);
                job.chunk.removePluginChunkTicket(plugin);queued.remove(identity(job.chunk));patches.remove();
                habitat.settle(job.chunk,job.site);
            }
        }
    }
    public Geometry geometry(Kind kind){return geometry.get(kind);}
    public Placement placement(World world){return worlds.get(world.getUID());}
    /** Main-thread snapshot of worlds that actually use this sanctuary placement. */
    public List<World> mapWorlds(){
        if(!Bukkit.isPrimaryThread())throw new IllegalStateException("Capture sanctuary map worlds on the main thread");
        return Bukkit.getWorlds().stream().filter(world->worlds.containsKey(world.getUID())).toList();
    }
    /** Recheck the complete authored structure against a live border before selling its map. */
    public boolean mapTargetInsideBorder(World world,Kind kind,int x,int z){
        if(!Bukkit.isPrimaryThread())throw new IllegalStateException("Check sanctuary map borders on the main thread");
        if(world==null||kind==null||!worlds.containsKey(world.getUID()))return false;
        MapBounds bounds=mapBounds(world,kind);
        return x>=bounds.left&&x<=bounds.right&&z>=bounds.top&&z<=bounds.bottom;
    }
    private record MapBounds(double left,double right,double top,double bottom){}
    private MapBounds mapBounds(World world,Kind kind){
        WorldBorder border=world.getWorldBorder();Location center=border.getCenter();double half=border.getSize()/2;
        Geometry shape=geometry.get(kind);int minX=0,maxX=0,minZ=0,maxZ=0;
        for(long column:shape.columns.keySet()){
            int x=(int)(column>>32),z=(int)column;
            minX=Math.min(minX,x);maxX=Math.max(maxX,x);minZ=Math.min(minZ,z);maxZ=Math.max(maxZ,z);
        }
        return new MapBounds(center.getX()-half-minX,center.getX()+half-maxX-1,
                center.getZ()-half-minZ,center.getZ()+half-maxZ-1);
    }
    /**
     * Capture live world/configuration state on the main thread; the returned search may
     * run asynchronously. The eligibility predicate must use immutable captured data.
     * Searching samples the same biome placement as generation and never loads chunks.
     */
    public Supplier<Site> mapSearch(World world,Kind kind,int originX,int originZ,
                                    Predicate<Site> eligible,BooleanSupplier cancelled){
        if(!Bukkit.isPrimaryThread())throw new IllegalStateException("Capture sanctuary map searches on the main thread");
        if(world==null||kind==null)return null;
        Placement layout=worlds.get(world.getUID());
        if(layout==null)return null;
        Objects.requireNonNull(eligible,"eligible");Objects.requireNonNull(cancelled,"cancelled");
        int limit=Math.clamp(plugin.getConfig().getInt("overworld-sanctuaries.search-max-cells",128),24,256);
        MapBounds bounds=mapBounds(world,kind);
        return ()->layout.nearestForMap(kind,originX,originZ,limit,eligible,cancelled,bounds.left,bounds.right,bounds.top,bounds.bottom);
    }
    public void close(){
        searches.clear();
        for(Patch job:patches)job.chunk.removePluginChunkTicket(plugin);
        patches.clear();queued.clear();
        for(var entry:worlds.entrySet()){
            // Shutdown can still have decoration workers iterating this list.
            World world=Bukkit.getWorld(entry.getKey());if(world!=null&&!Bukkit.isStopping())world.getPopulators().remove(entry.getValue());
        }
        worlds.clear();
    }
    public final class Placement extends BlockPopulator {
        private final WorldInfo info;
        public final int spacing;
        private final double chance;
        private final Set<Long> excluded;
        private final Set<Long> legacy;
        private final Set<Long> previous;
        private final Set<Long> last;
        private final Set<Long> recent;
        private final Map<Long,Optional<Site>> cache=Collections.synchronizedMap(new LinkedHashMap<>(128,.75f,true){
            @Override protected boolean removeEldestEntry(Map.Entry<Long,Optional<Site>> e){return size()>2048;}
        });
        private final BiomeProvider biomes;
        /** Cells with terrain before region placement existed; they keep {@link #candidate}. */
        private final Set<Long> classic;
        /** Regions per kind, in cells: each kind has its own grid and places at most one site per region. */
        private final Map<Kind,Integer> regionCells;
        /** Farthest a site centre may sit from its cell centre so the whole footprint stays inside the cell. */
        private final int reach;
        private final Map<String,Optional<Site>> regions=Collections.synchronizedMap(new LinkedHashMap<>(64,.75f,true){
            @Override protected boolean removeEldestEntry(Map.Entry<String,Optional<Site>> e){return size()>1024;}
        });
        Placement(WorldInfo info,int spacing,double chance,Set<Long> excluded,Set<Long> legacy,Set<Long> previous,Set<Long> last,Set<Long> recent,Set<Long> classic,Map<Kind,Integer> regionCells){
            this.info=info;this.spacing=spacing;this.chance=chance;this.excluded=Set.copyOf(excluded);this.legacy=Set.copyOf(legacy);this.previous=Set.copyOf(previous);this.last=Set.copyOf(last);this.recent=Set.copyOf(recent);this.biomes=info.vanillaBiomeProvider();
            this.classic=Set.copyOf(classic);this.regionCells=Map.copyOf(regionCells);
            int extent=0;
            for(var g:geometry.values())for(long column:g.columns.keySet())extent=Math.max(extent,Math.max(Math.abs((int)(column>>32)),Math.abs((int)column)));
            reach=Math.max(48,spacing*8-extent-16);
        }
        public Site cell(int gx,int gz){
            long cell=key(gx,gz);Optional<Site> old=cache.get(cell);if(old!=null)return old.orElse(null);
            Site found=classic.contains(cell)?candidate(gx,gz):regional(gx,gz);cache.put(cell,Optional.ofNullable(found));return found;
        }
        public int regionCells(Kind kind){return regionCells.get(kind);}
        public int reach(){return reach;}
        /** Region coordinates of a cell on a kind's grid of the given size; each kind's grid is shifted by a seeded offset. */
        public int[] regionAt(Kind kind,int cells,int gx,int gz){
            long h=gridOffset(kind);
            return new int[]{Math.floorDiv(gx-(int)Math.floorMod(h,cells),cells),Math.floorDiv(gz-(int)Math.floorMod(h>>>20,cells),cells)};
        }
        private long gridOffset(Kind kind){return DungeonLayout.mix(info.getSeed()^(kind.ordinal()+1)*0x632be59bd9b4e019L);}
        /** The kind's one site in a region of its grid, or null when the region has no room for it. */
        public Site region(Kind kind,int rx,int rz){
            String id=kind.ordinal()+":"+rx+":"+rz;Optional<Site> old=regions.get(id);if(old!=null)return old.orElse(null);
            Site found=pick(kind,regionCells.get(kind),rx,rz,null,true);regions.put(id,Optional.ofNullable(found));return found;
        }
        /** Probe: a region pick at any size, ignoring other kinds; stats = spots, centre in biome, footprint in biome, clear of settlements. */
        public Site simulate(Kind kind,int cells,int rx,int rz,int[] stats){return pick(kind,cells,rx,rz,stats,false);}
        private Site regional(int gx,int gz){
            if(excluded.contains(key(gx,gz))||biomes==null)return null;
            for(Kind kind:PRIORITY){
                int[] r=regionAt(kind,regionCells.get(kind),gx,gz);Site site=region(kind,r[0],r[1]);
                if(site!=null&&inCell(site,gx,gz))return site;
            }
            return null;
        }
        private boolean inCell(Site site,long gx,long gz){int size=spacing*16;return Math.floorDiv(site.x,size)==gx&&Math.floorDiv(site.z,size)==gz;}
        /** A cell already holding a site of a kind earlier in {@link #PRIORITY}. */
        private boolean claimed(Kind kind,long gx,long gz){
            for(Kind other:PRIORITY){
                if(other==kind)return false;
                int[] r=regionAt(other,regionCells.get(other),(int)gx,(int)gz);Site site=region(other,r[0],r[1]);
                if(site!=null&&inCell(site,gx,gz))return true;
            }
            return false;
        }
        private Site pick(Kind kind,int regionSize,int rx,int rz,int[] stats,boolean exclusive){
            if(biomes==null)return null;
            Set<Biome> family=FAMILIES.get(kind);int size=spacing*16;
            long hash=DungeonLayout.mix(info.getSeed()^rx*341873128712L^rz*132897987541L^(kind.ordinal()+1L)*0x5245474e53495445L);
            Random random=new Random(hash);
            long h=gridOffset(kind);
            int ox=(int)Math.floorMod(h,regionSize),oz=(int)Math.floorMod(h>>>20,regionSize);
            List<long[]> cells=new ArrayList<>();
            for(int dx=0;dx<regionSize;dx++)for(int dz=0;dz<regionSize;dz++){
                long gx=(long)rx*regionSize+ox+dx,gz=(long)rz*regionSize+oz+dz;long cell=key((int)gx,(int)gz);
                if(!classic.contains(cell)&&!excluded.contains(cell))cells.add(new long[]{gx,gz});
            }
            Collections.shuffle(cells,random);
            // A jittered 7 x 7 grid over the part of each cell the footprint may occupy, tried in random order.
            int steps=7;double stride=2.0*reach/(steps-1);
            for(long[] cell:cells){
                if(exclusive&&claimed(kind,cell[0],cell[1]))continue;
                List<int[]> spots=new ArrayList<>();
                for(int i=0;i<steps;i++)for(int j=0;j<steps;j++)spots.add(new int[]{i,j});
                Collections.shuffle(spots,random);
                long cx=cell[0]*size+size/2,cz=cell[1]*size+size/2;
                for(int[] s:spots){
                    long lx=cx-reach+Math.round(s[0]*stride)+random.nextInt(17)-8,lz=cz-reach+Math.round(s[1]*stride)+random.nextInt(17)-8;
                    lx=Math.clamp(lx,cx-reach,cx+reach);lz=Math.clamp(lz,cz-reach,cz+reach);
                    if(Math.abs(lx)>29_999_700||Math.abs(lz)>29_999_700||lx*lx+lz*lz<1024L*1024)continue;
                    int x=(int)lx,z=(int)lz;if(stats!=null)stats[0]++;
                    if(!family.contains(biomes.getBiome(info,x,80,z)))continue;
                    if(stats!=null)stats[1]++;
                    // A hit near the patch edge slides inwards until the whole footprint is in the biome.
                    for(int[] o:SLIDES){
                        long sx=Math.clamp(lx+o[0],cx-reach,cx+reach),sz=Math.clamp(lz+o[1],cz-reach,cz+reach);
                        if(sx*sx+sz*sz<1024L*1024)continue;
                        int px=(int)sx,pz=(int)sz;boolean inside=true;
                        for(int dx:new int[]{-96,0,96})for(int dz:new int[]{-88,0,88})
                            if(inside&&!family.contains(biomes.getBiome(info,px+dx,dx==0&&dz==0?80:kind.floor,pz+dz)))inside=false;
                        if(!inside)continue;
                        if(stats!=null)stats[2]++;
                        if(VanillaSettlements.near(info,biomes,px,pz,112))continue;
                        if(stats!=null)stats[3]++;
                        return new Site(kind,px,pz,DungeonLayout.mix(hash^sx*31^sz));
                    }
                }
            }
            return null;
        }
        private Site candidate(int gx,int gz){
            if(excluded.contains(key(gx,gz))||biomes==null)return null;
            long hash=DungeonLayout.mix(info.getSeed()^gx*341873128712L^gz*132897987541L^0x5349585249564552L);
            Random random=new Random(hash);if(random.nextDouble()>=chance)return null;
            int size=spacing*16;
            long lx=(long)gx*size+size/2+random.nextInt(97)-48,lz=(long)gz*size+size/2+random.nextInt(97)-48;
            if(Math.abs(lx)>29_999_700||Math.abs(lz)>29_999_700||lx*lx+lz*lz<1024L*1024)return null;
            int x=(int)lx,z=(int)lz;Biome biome=biomes.getBiome(info,x,80,z);Kind kind=null;
            for(var k:Kind.values())if(k.biome==biome){kind=k;break;}
            if(kind==null)return null;
            // Keep all nine sampled points in the correct biome; no chunk access.
            for(int dx:new int[]{-96,0,96})for(int dz:new int[]{-88,0,88})
                if(biomes.getBiome(info,x+dx,kind.floor,z+dz)!=biome)return null;
            return new Site(kind,x,z,hash);
        }
        public boolean legacy(Site site){return legacy.contains(key(Math.floorDiv(site.x,spacing*16),Math.floorDiv(site.z,spacing*16)));}
        public int sourceVersion(Site site){
            long cell=key(Math.floorDiv(site.x,spacing*16),Math.floorDiv(site.z,spacing*16));
            return legacy.contains(cell)?1:previous.contains(cell)?2:last.contains(cell)?3:recent.contains(cell)?4:5;
        }
        public Site nearest(Kind kind,int gx,int gz,int limit,IntConsumer progress,BooleanSupplier cancelled){
            for(int r=0;r<=limit;r++){
                if(cancelled.getAsBoolean())return null;
                if(r==24||r==48||r==96||r==192)progress.accept(r);
                for(int dx=-r;dx<=r;dx++){
                    Site a=cell(gx+dx,gz-r);if(a!=null&&a.kind==kind)return a;
                    if(r>0){Site b=cell(gx+dx,gz+r);if(b!=null&&b.kind==kind)return b;}
                }
                for(int dz=-r+1;dz<r;dz++){
                    Site a=cell(gx-r,gz+dz);if(a!=null&&a.kind==kind)return a;
                    Site b=cell(gx+r,gz+dz);if(b!=null&&b.kind==kind)return b;
                }
            }return null;
        }
        private Site nearestForMap(Kind kind,int originX,int originZ,int limit,Predicate<Site> eligible,
                                   BooleanSupplier cancelled,double left,double right,double top,double bottom){
            if(left>right||top>bottom)return null;
            int size=spacing*16,gx=Math.floorDiv(originX,size),gz=Math.floorDiv(originZ,size);
            Site best=null;long bestDistance=Long.MAX_VALUE;
            for(int r=0;r<=limit;r++){
                if(cancelled.getAsBoolean())return null;
                for(int dx=-r;dx<=r;dx++){
                    if(cancelled.getAsBoolean())return null;
                    Site a=mapCandidate(kind,gx+dx,gz-r,eligible,left,right,top,bottom);
                    if(a!=null){long d=distanceSquared(a,originX,originZ);if(d<bestDistance){best=a;bestDistance=d;}}
                    if(r>0){
                        Site b=mapCandidate(kind,gx+dx,gz+r,eligible,left,right,top,bottom);
                        if(b!=null){long d=distanceSquared(b,originX,originZ);if(d<bestDistance){best=b;bestDistance=d;}}
                    }
                }
                for(int dz=-r+1;dz<r;dz++){
                    if(cancelled.getAsBoolean())return null;
                    Site a=mapCandidate(kind,gx-r,gz+dz,eligible,left,right,top,bottom);
                    if(a!=null){long d=distanceSquared(a,originX,originZ);if(d<bestDistance){best=a;bestDistance=d;}}
                    Site b=mapCandidate(kind,gx+r,gz+dz,eligible,left,right,top,bottom);
                    if(b!=null){long d=distanceSquared(b,originX,originZ);if(d<bestDistance){best=b;bestDistance=d;}}
                }
                // Candidate centers are cell center +/-reach. Every unvisited cell is
                // outside one of these four bounds, so further rings cannot be closer.
                long unseen=Math.min(Math.min((long)originX-((long)(gx-r-1)*size+size/2+reach),
                        (long)(gx+r+1)*size+size/2-reach-originX),
                        Math.min((long)originZ-((long)(gz-r-1)*size+size/2+reach),
                        (long)(gz+r+1)*size+size/2-reach-originZ));
                if(best!=null&&unseen>0&&bestDistance<=unseen*unseen)return best;
            }
            return cancelled.getAsBoolean()?null:best;
        }
        private Site mapCandidate(Kind kind,int gx,int gz,Predicate<Site> eligible,
                                  double left,double right,double top,double bottom){
            int size=spacing*16;long centerX=(long)gx*size+size/2,centerZ=(long)gz*size+size/2;
            if(centerX+reach<left||centerX-reach>right||centerZ+reach<top||centerZ-reach>bottom)return null;
            Site site=cell(gx,gz);
            return site!=null&&site.kind==kind&&site.x>=left&&site.x<=right&&site.z>=top&&site.z<=bottom
                    &&eligible.test(site)?site:null;
        }
        private long distanceSquared(Site site,int x,int z){long dx=(long)site.x-x,dz=(long)site.z-z;return dx*dx+dz*dz;}
        @Override public void populate(WorldInfo world,Random ignored,int cx,int cz,LimitedRegion region){
            // Each footprint fits entirely within its own cell (classic sites: >96-block margins; regional: >=16).
            Site site=cell(Math.floorDiv(cx,spacing),Math.floorDiv(cz,spacing));if(site==null)return;
            int source=sourceVersion(site);
            paint(site,cx,cz,region,source==1?legacyGeometry.get(site.kind):source==2?previousGeometry.get(site.kind):source==3?lastGeometry.get(site.kind):source==4?recentGeometry.get(site.kind):geometry.get(site.kind));
        }
        public void paint(Site site,int cx,int cz,LimitedRegion region){
            paint(site,cx,cz,region,geometry.get(site.kind));
        }
        private void paint(Site site,int cx,int cz,LimitedRegion region,Geometry g){
            int floor=site.kind.floor;
            for(int x=cx*16;x<cx*16+16;x++)for(int z=cz*16;z<cz*16+16;z++){
                List<Voxel> column=g.columns.get(key(x-site.x,z-site.z));if(column==null)continue;
                int low=floor+column.getFirst().y;
                int high=region.getHighestBlockYAt(x,z,HeightMap.WORLD_SURFACE);
                // Clearing the current chunk only also creates the hollow authored rooms.
                for(int y=low;y<=Math.min(worldMax(region),high);y++)region.setType(x,y,z,Material.AIR);
                if(site.kind!=Kind.BANSHEE){
                    // Attached foundations reach the existing ground; never make floating pads.
                    int ground=low-1;
                    while(ground>info.getMinHeight()+1&&!region.getType(x,ground,z).isSolid())ground--;
                    for(int y=ground+1;y<low;y++)region.setType(x,y,z,site.kind==Kind.PHLEGETHON?Material.TERRACOTTA:Material.STONE);
                }
                for(Voxel v:column)region.setBlockData(x,floor+v.y,z,g.palette[v.palette]);
            }
            for(int i=0;i<g.chests.size();i++){
                Point p=g.chests.get(i);int x=site.x+p.x,z=site.z+p.z;
                if(Math.floorDiv(x,16)!=cx||Math.floorDiv(z,16)!=cz)continue;
                BlockState state=region.getBlockState(x,floor+p.y,z);
                if(!(state instanceof Chest chest))throw new IllegalStateException("Missing authored treasure chest");
                // Vanilla loot is lazily rolled once and persisted with the container.
                long lootSeed=DungeonLayout.mix(site.seed+i);
                chest.setLootTable(LootTables.END_CITY_TREASURE.getLootTable(),lootSeed);
                chest.getPersistentDataContainer().set(plugin.key("sanctuary_loot_seed"),PersistentDataType.LONG,lootSeed);
                chest.getPersistentDataContainer().set(plugin.key("sanctuary_loot_profile"),PersistentDataType.INTEGER,2);
                region.setBlockState(x,floor+p.y,z,chest);
            }
        }
        private int worldMax(LimitedRegion region){return info.getMaxHeight()-1;}
    }
    private boolean admin(CommandSender s){return s.isOp()||s.hasPermission("evergarden.admin");}
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
        if(!admin(sender)){sender.sendMessage("คำสั่งนี้สำหรับแอดมิน");return true;}
        if(!(sender instanceof Player player)){sender.sendMessage("Use this command in an Overworld.");return true;}
        Placement layout=worlds.get(player.getWorld().getUID());
        Kind kind=args.length>1?Kind.parse(args[1]):null;
        if(layout==null||kind==null||(args.length!=2&&args.length!=3)||!Set.of("locate","tp").contains(args[0].toLowerCase(Locale.ROOT))){
            sender.sendMessage("/sanctuary locate|tp acheron|phlegethon|cocytus|banshee|malefeasance|styx [24-256] (Overworld)");return true;
        }
        int requested=plugin.getConfig().getInt("overworld-sanctuaries.search-max-cells",128);
        if(args.length==3)try{requested=Integer.parseInt(args[2]);}catch(NumberFormatException ex){sender.sendMessage("ระยะค้นหาต้องเป็นจำนวนเซลล์ 24–256");return true;}
        final int limit=Math.clamp(requested,24,256);
        World world=player.getWorld();int gx=Math.floorDiv(player.getLocation().getBlockX(),layout.spacing*16),gz=Math.floorDiv(player.getLocation().getBlockZ(),layout.spacing*16);
        boolean teleport=args[0].equalsIgnoreCase("tp");
        UUID user=player.getUniqueId(),token=UUID.randomUUID();searches.put(user,token);
        sender.sendMessage("กำลังค้นหาสถานที่ในไบโอม "+kind.biome.getKey().getKey()+" สูงสุด "+limit+" เซลล์ …");
        Bukkit.getScheduler().runTaskAsynchronously(plugin,()->{
            Site found=layout.nearest(kind,gx,gz,limit,r->{
                if(token.equals(searches.get(user))&&plugin.isEnabled())Bukkit.getScheduler().runTask(plugin,()->{
                    if(player.isOnline()&&token.equals(searches.get(user)))player.sendMessage("กำลังขยายการค้นหาผ่านระยะ "+r+" เซลล์ …");
                });
            },()->!token.equals(searches.get(user))||!plugin.isEnabled());
            if(!token.equals(searches.get(user))||!plugin.isEnabled())return;
            Bukkit.getScheduler().runTask(plugin,()->{
                if(!searches.remove(user,token)||!player.isOnline()||player.getWorld()!=world)return;
                if(found==null){player.sendMessage("ไม่พบสถานที่ในระยะ "+limit+" เซลล์ · ลอง /sanctuary "+(teleport?"tp ":"locate ")+kind.id+" 256");return;}
                player.sendMessage(kind.id+" · "+world.getName()+" · X="+found.x+" Z="+found.z+" · 8 treasure caches");
                if(teleport&&admin(player)&&player.getWorld()==world)
                    player.teleportAsync(new Location(world,found.x+.5,kind.floor+(kind==Kind.BANSHEE?5:1),found.z+90.5,180,0))
                        .thenAccept(ok->{if(!ok&&player.isOnline())Bukkit.getScheduler().runTask(plugin,()->player.sendMessage("วาร์ปไม่สำเร็จ · ใช้พิกัดที่แสดงเพื่อลองอีกครั้ง"));});
            });
        });return true;
    }
    @Override public List<String> onTabComplete(CommandSender sender,Command cmd,String alias,String[] args){
        if(!admin(sender))return List.of();
        List<String> options=args.length==1?List.of("locate","tp"):args.length==2?Arrays.stream(Kind.values()).map(k->k.id).toList():List.of();
        String prefix=args.length==0?"":args[args.length-1].toLowerCase(Locale.ROOT);
        return options.stream().filter(s->s.startsWith(prefix)).toList();
    }
}
