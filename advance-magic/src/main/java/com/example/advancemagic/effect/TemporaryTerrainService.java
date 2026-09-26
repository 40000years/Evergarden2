package com.example.advancemagic.effect;

import com.example.advancemagic.AdvanceMagicPlugin;
import com.example.advancemagic.spell.Spell;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.event.world.*;
import org.bukkit.util.BoundingBox;
import java.nio.file.*;
import java.util.*;

/** Bounded, non-flowing native liquids. Originals are journaled before any edit. */
public final class TemporaryTerrainService implements Listener, AutoCloseable {
    private record Pos(UUID world,int x,int y,int z) {
        static Pos of(Block b){return new Pos(b.getWorld().getUID(),b.getX(),b.getY(),b.getZ());}
        Block loadedBlock(){
            World w=Bukkit.getWorld(world);
            return w!=null&&w.isChunkLoaded(x>>4,z>>4)?w.getBlockAt(x,y,z):null;
        }
    }
    private static final class Cell {
        final BlockData original;
        final Set<Zone> reservations=new HashSet<>();
        final LinkedHashMap<Zone,BlockData> claims=new LinkedHashMap<>();
        BlockData expected;
        boolean restored;
        Cell(BlockData original){this.original=original.clone();}
        Zone top(){Zone top=null;for(Zone z:claims.keySet())top=z;return top;}
    }
    private final AdvanceMagicPlugin plugin;
    private final Path journal;
    private final Map<Pos,Cell> cells=new LinkedHashMap<>();
    private final Set<Zone> zones=new HashSet<>();
    private boolean available=true;

    public TemporaryTerrainService(AdvanceMagicPlugin plugin){
        this.plugin=plugin;journal=plugin.getDataFolder().toPath().resolve("mythic-terrain-recovery.yml");
        if(Files.exists(journal))try{
            var yaml=new YamlConfiguration();yaml.load(journal.toFile());
            for(Map<?,?> row:yaml.getMapList("blocks")){
                Pos pos=new Pos(UUID.fromString((String)row.get("world")),((Number)row.get("x")).intValue(),
                    ((Number)row.get("y")).intValue(),((Number)row.get("z")).intValue());
                cells.put(pos,new Cell(Bukkit.createBlockData((String)row.get("before"))));
            }
        }catch(Exception ex){
            available=false;
            plugin.getLogger().log(java.util.logging.Level.SEVERE,"Cannot read Mythic terrain recovery; terrain effects disabled to preserve the journal",ex);
        }
    }
    public int duration(){return Math.clamp(plugin.getConfig().getInt("mythic-terrain.duration-seconds",15),3,30)*20;}
    private boolean liquid(Material m){return m==Material.WATER||m==Material.LAVA;}
    private boolean save(){
        if(!available)return false;
        try{
            Files.createDirectories(journal.getParent());
            var rows=new ArrayList<Map<String,Object>>();
            cells.forEach((p,cell)->rows.add(Map.of("world",p.world.toString(),"x",p.x,"y",p.y,"z",p.z,"before",cell.original.getAsString())));
            var yaml=new YamlConfiguration();yaml.set("blocks",rows);
            Path staged=journal.resolveSibling(journal.getFileName()+".tmp");
            yaml.save(staged.toFile());
            try{Files.move(staged,journal,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
            catch(AtomicMoveNotSupportedException ex){Files.move(staged,journal,StandardCopyOption.REPLACE_EXISTING);}
            return true;
        }catch(Exception ex){
            plugin.getLogger().log(java.util.logging.Level.SEVERE,"Cannot save Mythic terrain recovery; no new terrain will be painted",ex);
            return false;
        }
    }
    private boolean surface(Block b){
        Material m=b.getType();
        if(!liquid(m)&&(!m.isOccluding()||m==Material.BEDROCK||m==Material.BARRIER||m==Material.END_PORTAL_FRAME))return false;
        if(b.getState() instanceof TileState||Tag.LOGS.isTagged(m)||Tag.LEAVES.isTagged(m))return false;
        Block above=b.getRelative(0,1,0);
        return above.getType().isAir()||liquid(above.getType());
    }
    public Zone open(Player owner,Spell spell,Location center,Material material){
        Zone zone=new Zone(owner,spell,center,material);
        if(!available)return zone;
        int radius=zone.radius;
        // Only one exposed surface per column; never load or generate a chunk.
        for(int x=-radius;x<=radius;x++)for(int z=-radius;z<=radius;z++){
            double distance=Math.hypot(x,z);if(distance>radius)continue;
            for(int y=3;y>=-8;y--){
                Location at=center.clone().add(x,y,z);
                if(!plugin.context().loaded(at)||!plugin.context().loaded(at.clone().add(0,1,0)))continue;
                Block b=at.getBlock();Pos pos=Pos.of(b);Cell current=cells.get(pos);
                if(current!=null&&current.reservations.isEmpty()){
                    if(!current.restored)recover(pos,current);
                }
                if(!surface(b)){
                    // Do not dig through an occupied top layer looking for another floor.
                    if(!b.isPassable())break;
                    continue;
                }
                // Completed casts keep their journal until the chunk has been saved.
                // A new cast snapshots eligible ground again, including later edits.
                Cell cell=current==null||current.reservations.isEmpty()?new Cell(b.getBlockData()):current;
                cells.put(pos,cell);
                cell.restored=false;
                cell.reservations.add(zone);zone.candidates.put(pos,distance);break;
            }
        }
        zones.add(zone);
        // A failed write leaves every block untouched.
        if(!save())zone.close();
        return zone;
    }
    public final class Zone implements AutoCloseable {
        public final Player owner;
        public final Spell spell;
        private final Location center;
        private final BlockData fluid;
        private final int radius;
        private final Map<Pos,Double> candidates=new LinkedHashMap<>();
        private final Set<Pos> active=new LinkedHashSet<>(),finished=new HashSet<>();
        private boolean closed;
        private Zone(Player owner,Spell spell,Location center,Material material){
            if(!liquid(material))throw new IllegalArgumentException("Mythic terrain must be water or lava");
            this.owner=owner;this.spell=spell;this.center=center.clone();fluid=material.createBlockData();
            radius=Math.clamp(plugin.getConfig().getInt("mythic-terrain.radius",16),6,18);
        }
        public void tick(int age,int lifetime){
            if(closed||age%5!=0)return;
            double extent=age<20?radius*(age+5)/20.0:age<lifetime/2?radius:
                radius*Math.max(0,(lifetime-age)/(lifetime*.5));
            for(var entry:candidates.entrySet()){
                Pos pos=entry.getKey();Cell cell=cells.get(pos);Block b=pos.loadedBlock();
                if(cell==null||b==null||!cell.reservations.contains(this)||finished.contains(pos))continue;
                boolean wanted=entry.getValue()<=extent;
                if(wanted&&!active.contains(pos)){
                    // Leave external edits alone; only managed liquids may replace one another.
                    if((cell.expected==null&&!b.getBlockData().equals(cell.original))
                        ||(cell.expected!=null&&b.getType()!=cell.expected.getMaterial())){
                        finished.add(pos);continue;
                    }
                    cell.claims.put(this,fluid);cell.expected=fluid;
                    b.setBlockData(fluid,false);active.add(pos);
                }else if(!wanted&&active.remove(pos)){
                    release(pos,cell,this);finished.add(pos);
                }
            }
        }
        public boolean touches(LivingEntity entity){return contact(entity)==this;}
        public List<Location> samples(int count,int offset){
            List<Location> result=new ArrayList<>();if(active.isEmpty())return result;
            List<Pos> positions=new ArrayList<>(active);
            int n=Math.min(count,positions.size());
            for(int i=0;i<n;i++){
                Pos pos=positions.get(Math.floorMod(offset+i*positions.size()/n,positions.size()));
                Cell cell=cells.get(pos);Block b=pos.loadedBlock();
                if(b!=null&&cell!=null&&cell.top()==this)result.add(b.getLocation().add(.5,1.05,.5));
            }
            return result;
        }
        @Override public void close(){
            if(closed)return;closed=true;
            for(Pos pos:candidates.keySet()){
                Cell cell=cells.get(pos);if(cell==null)continue;
                release(pos,cell,this);cell.reservations.remove(this);
                if(cell.reservations.isEmpty()&&pos.loadedBlock()!=null)cell.restored=true;
            }
            active.clear();zones.remove(this);if(available)save();
        }
    }
    private void release(Pos pos,Cell cell,Zone zone){
        if(cell.claims.remove(zone)==null)return;
        Block b=pos.loadedBlock();if(b==null)return;
        BlockData next=cell.original;for(BlockData data:cell.claims.values())next=data;
        if(cell.expected!=null&&b.getType()==cell.expected.getMaterial()){
            liftOccupants(b,next);b.setBlockData(next,false);
        }
        cell.expected=cell.claims.isEmpty()?null:next;
    }
    private void recover(Pos pos,Cell cell){
        Block b=pos.loadedBlock();if(b==null)return;
        if(liquid(b.getType())){liftOccupants(b,cell.original);b.setBlockData(cell.original,false);}
        cell.restored=true;
    }
    private void liftOccupants(Block block,BlockData floor){
        if(!floor.getMaterial().isOccluding())return;
        BoundingBox filling=new BoundingBox(block.getX(),block.getY(),block.getZ(),block.getX()+1,block.getY()+1,block.getZ()+1);
        for(Entity entity:block.getWorld().getNearbyEntities(filling)){
            if(!(entity instanceof LivingEntity||entity instanceof Vehicle||entity instanceof Item)
                ||!entity.isValid()||entity.isDead()||entity.isInsideVehicle())continue;
            Location above=entity.getLocation();above.setY(block.getY()+1.05);
            // A shallow pool must not leave a swimmer embedded in the returning floor.
            for(int rise=0;rise<=3;rise++){
                Location candidate=above.clone().add(0,rise,0);
                if(plugin.context().safeBody(candidate)){
                    entity.teleport(candidate,PlayerTeleportEvent.TeleportCause.PLUGIN);break;
                }
            }
        }
    }
    public void recoverLoaded(){
        if(!available)return;
        for(var entry:List.copyOf(cells.entrySet()))if(!entry.getValue().restored&&entry.getValue().reservations.isEmpty())recover(entry.getKey(),entry.getValue());
        save();
    }
    private boolean managed(Block block){
        if(block==null)return false;Cell cell=cells.get(Pos.of(block));return cell!=null&&!cell.claims.isEmpty();
    }
    private Zone contact(LivingEntity entity){
        var box=entity.getBoundingBox();World world=entity.getWorld();
        int y=(int)Math.floor(box.getMinY());
        for(int x=(int)Math.floor(box.getMinX());x<=Math.floor(box.getMaxX());x++)
            for(int z=(int)Math.floor(box.getMinZ());z<=Math.floor(box.getMaxZ());z++)for(int dy=0;dy<=1;dy++){
                Pos pos=new Pos(world.getUID(),x,y-dy,z);Cell cell=cells.get(pos);
                if(cell!=null&&cell.top()!=null&&box.getMinY()<=pos.y+1.2)return cell.top();
            }
        return null;
    }
    private boolean nearFluid(Block b,boolean lavaOnly){
        if(b==null)return false;
        for(int x=-1;x<=1;x++)for(int y=-1;y<=1;y++)for(int z=-1;z<=1;z++){
            int bx=b.getX()+x,bz=b.getZ()+z;
            if(!b.getWorld().isChunkLoaded(bx>>4,bz>>4))continue;
            Block next=b.getRelative(x,y,z);if((!lavaOnly||next.getType()==Material.LAVA)&&managed(next))return true;
        }
        return false;
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void flow(BlockFromToEvent e){if(managed(e.getBlock())||managed(e.getToBlock()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void physics(BlockPhysicsEvent e){if(managed(e.getBlock())||managed(e.getSourceBlock()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void form(BlockFormEvent e){if(managed(e.getBlock())||nearFluid(e.getBlock(),false))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void ignite(BlockIgniteEvent e){
        if(e.getCause()==BlockIgniteEvent.IgniteCause.LAVA){
            Block b=e.getBlock();
            // Vanilla lava can ignite above/away from its source, beyond adjacent blocks.
            for(int x=-4;x<=4;x++)for(int y=-4;y<=0;y++)for(int z=-4;z<=4;z++){
                int bx=b.getX()+x,bz=b.getZ()+z;
                if(!b.getWorld().isChunkLoaded(bx>>4,bz>>4))continue;
                Block source=b.getRelative(x,y,z);
                if(source.getType()==Material.LAVA&&managed(source)){e.setCancelled(true);return;}
            }
        }
        if(managed(e.getBlock())||nearFluid(e.getIgnitingBlock(),true))e.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void burn(BlockBurnEvent e){if(managed(e.getBlock())||nearFluid(e.getIgnitingBlock(),true))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void spread(BlockSpreadEvent e){if(managed(e.getBlock())||managed(e.getSource()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void breakBlock(BlockBreakEvent e){if(managed(e.getBlock()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void place(BlockPlaceEvent e){if(managed(e.getBlockPlaced()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void bucketFill(PlayerBucketFillEvent e){if(managed(e.getBlock())||managed(e.getBlockClicked()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void bucketEmpty(PlayerBucketEmptyEvent e){if(managed(e.getBlock())||managed(e.getBlockClicked()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void extend(BlockPistonExtendEvent e){if(managed(e.getBlock().getRelative(e.getDirection()))||e.getBlocks().stream().anyMatch(b->managed(b)||managed(b.getRelative(e.getDirection()))))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void retract(BlockPistonRetractEvent e){if(e.getBlocks().stream().anyMatch(b->managed(b)||managed(b.getRelative(e.getDirection()))))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void change(EntityChangeBlockEvent e){if(managed(e.getBlock()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void explode(EntityExplodeEvent e){e.blockList().removeIf(this::managed);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void explode(BlockExplodeEvent e){e.blockList().removeIf(this::managed);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void combust(EntityCombustEvent e){
        if(e.getEntity() instanceof LivingEntity living){
            Zone zone=contact(living);
            // Spell pulses own damage/fire and apply the normal protection/team/PVP rules.
            if(zone!=null&&zone.fluid.getMaterial()==Material.LAVA)e.setCancelled(true);
        }
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void damage(EntityDamageEvent e){
        if(e.getCause()!=EntityDamageEvent.DamageCause.LAVA)return;
        if(e.getEntity() instanceof LivingEntity living){Zone zone=contact(living);if(zone!=null&&zone.fluid.getMaterial()==Material.LAVA)e.setCancelled(true);}
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void unload(ChunkUnloadEvent e){
        if(!available)return;boolean changed=false;List<Pos> saved=new ArrayList<>();
        for(var entry:List.copyOf(cells.entrySet())){
            Pos p=entry.getKey();if(!p.world.equals(e.getWorld().getUID())||(p.x>>4)!=e.getChunk().getX()||(p.z>>4)!=e.getChunk().getZ())continue;
            Cell cell=entry.getValue();
            for(Zone zone:List.copyOf(cell.claims.keySet()))release(p,cell,zone);
            for(Zone zone:cell.reservations){zone.active.remove(p);zone.finished.add(p);}
            cell.reservations.clear();if(!cell.restored)recover(p,cell);changed=true;
            if(e.isSaveChunk())saved.add(p);else cell.restored=false;
        }
        if(changed)save();
        // ChunkUnloadEvent is fired before the chunk write; prune after that write returns.
        if(!saved.isEmpty())Bukkit.getScheduler().runTask(plugin,()->prune(saved));
    }
    @EventHandler(priority=EventPriority.MONITOR) public void load(ChunkLoadEvent e){
        if(!available)return;boolean changed=false;
        for(var entry:List.copyOf(cells.entrySet())){
            Pos p=entry.getKey();if(!entry.getValue().restored&&entry.getValue().reservations.isEmpty()&&p.world.equals(e.getWorld().getUID())
                &&(p.x>>4)==e.getChunk().getX()&&(p.z>>4)==e.getChunk().getZ()){recover(p,entry.getValue());changed=true;}
        }
        if(changed)save();
    }
    @EventHandler(priority=EventPriority.MONITOR) public void worldLoad(WorldLoadEvent e){recoverLoaded();}
    @EventHandler(priority=EventPriority.MONITOR) public void saved(WorldSaveEvent e){
        List<Pos> saved=cells.entrySet().stream().filter(entry->entry.getKey().world.equals(e.getWorld().getUID())
            &&entry.getValue().restored&&entry.getValue().reservations.isEmpty()&&entry.getKey().loadedBlock()!=null).map(Map.Entry::getKey).toList();
        if(!saved.isEmpty())Bukkit.getScheduler().runTask(plugin,()->prune(saved));
    }
    private void prune(List<Pos> positions){
        for(Pos pos:positions){Cell cell=cells.get(pos);if(cell!=null&&cell.restored&&cell.reservations.isEmpty())cells.remove(pos);}
        save();
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void worldUnload(WorldUnloadEvent e){
        for(Zone zone:List.copyOf(zones))if(zone.center.getWorld().equals(e.getWorld()))zone.close();
    }
    @Override public void close(){for(Zone zone:List.copyOf(zones))zone.close();recoverLoaded();}
}
