package com.example.voidscape.world;

import com.example.voidscape.VoidscapePlugin;
import com.example.voidscape.world.OverworldSanctuaries.*;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.block.spawner.SpawnRule;
import org.bukkit.block.spawner.SpawnerEntry;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkPopulateEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Local habitat rules. No world gamerules, forced chunk loads or external mob exclusions. */
public final class SanctuaryHabitat implements Listener {
    private final VoidscapePlugin plugin;
    private final OverworldSanctuaries service;
    private final EnumMap<Kind,List<Point>> cages=new EnumMap<>(Kind.class);
    private final EnumMap<Kind,Integer> ceilings=new EnumMap<>(Kind.class);
    private final ArrayDeque<Runnable> maintenance=new ArrayDeque<>();
    private final Set<String> pending=new HashSet<>();
    private final Map<String,Integer> fresh=new HashMap<>();
    private final Map<String,Set<Point>> playerEdits=new HashMap<>();
    private static final EntityType[] TYPES={EntityType.WITHER_SKELETON,EntityType.PIGLIN,EntityType.SKELETON};
    private static final Set<CreatureSpawnEvent.SpawnReason> WILD=EnumSet.of(
        CreatureSpawnEvent.SpawnReason.NATURAL,CreatureSpawnEvent.SpawnReason.CHUNK_GEN,
        CreatureSpawnEvent.SpawnReason.PATROL,CreatureSpawnEvent.SpawnReason.REINFORCEMENTS);

    public SanctuaryHabitat(VoidscapePlugin plugin,OverworldSanctuaries service)throws IOException {
        this.plugin=plugin;this.service=service;
        try(var in=plugin.getResource("overworld-sanctuaries/guards.yml")){
            if(in==null)throw new IOException("Missing sanctuary guard locations");
            var yaml=YamlConfiguration.loadConfiguration(new InputStreamReader(in,StandardCharsets.UTF_8));
            for(Kind kind:Kind.values()){
                List<Point> points=new ArrayList<>();
                for(String line:yaml.getStringList(kind.id)){
                    var v=line.split(",");points.add(new Point(Integer.parseInt(v[0]),Integer.parseInt(v[1]),Integer.parseInt(v[2])));
                }
                if(points.size()!=3)throw new IOException("Expected three guard posts: "+kind.id);
                cages.put(kind,List.copyOf(points));
                ceilings.put(kind,service.geometry(kind).columns.values().stream().flatMap(Collection::stream).mapToInt(Voxel::y).max().orElse(0)+8);
            }
        }
        plugin.getServer().getPluginManager().registerEvents(this,plugin);
        Bukkit.getScheduler().runTaskTimer(plugin,()->{if(!maintenance.isEmpty())maintenance.remove().run();},1,1);
    }
    public List<Point> cages(Kind kind){return cages.get(kind);}
    private String id(Chunk chunk){return chunk.getWorld().getUID()+":"+chunk.getX()+":"+chunk.getZ();}
    private boolean fresh(Chunk chunk){return fresh.getOrDefault(id(chunk),Integer.MIN_VALUE)>Bukkit.getCurrentTick()-12000;}
    public Site site(Location at){
        var layout=service.placement(at.getWorld());if(layout==null)return null;
        Site site=layout.cell(Math.floorDiv(at.getBlockX(),layout.spacing*16),Math.floorDiv(at.getBlockZ(),layout.spacing*16));
        return site!=null&&contains(site,at.getBlockX(),at.getBlockY(),at.getBlockZ())?site:null;
    }
    public boolean contains(Site site,int x,int y,int z){
        var column=service.geometry(site.kind()).columns.get(OverworldSanctuaries.key(x-site.x(),z-site.z()));
        // Exact authored pad silhouette, above its foundation only. Caves below are unaffected.
        return column!=null&&y>=site.kind().floor+column.getFirst().y()&&y<=site.kind().floor+ceilings.get(site.kind());
    }
    /** Reserve the foundation and headroom as well as the visible authored blocks. */
    public boolean protectedAt(Location at){
        var layout=service.placement(at.getWorld());if(layout==null)return false;
        Site s=layout.cell(Math.floorDiv(at.getBlockX(),layout.spacing*16),Math.floorDiv(at.getBlockZ(),layout.spacing*16));
        return s!=null&&service.geometry(s.kind()).columns.containsKey(OverworldSanctuaries.key(at.getBlockX()-s.x(),at.getBlockZ()-s.z()))
            &&at.getY()>=s.kind().floor-16&&at.getY()<=s.kind().floor+ceilings.get(s.kind())+16;
    }
    @EventHandler(priority=EventPriority.LOWEST,ignoreCancelled=true)
    public void wild(CreatureSpawnEvent event){
        if(plugin.getConfig().getBoolean("overworld-sanctuaries.habitat.suppress-natural-hostiles",true)
            &&event.getEntity() instanceof Mob&&protectedAt(event.getLocation())
            &&!event.getEntity().getPersistentDataContainer().has(plugin.key("world_boss_entity"))
            &&!event.getEntity().getPersistentDataContainer().has(plugin.key("pet_model_preview"),PersistentDataType.BYTE)
            &&!event.getEntity().getPersistentDataContainer().has(plugin.key("sanctuary_guard"))
            &&(event.getEntity() instanceof Enemy||WILD.contains(event.getSpawnReason())))event.setCancelled(true);
    }
    /** Called after authored upgrades, and on load. The chunk is never loaded here. */
    public void settle(Chunk chunk,Site site){
        int x=chunk.getX()*16-site.x(),z=chunk.getZ()*16-site.z();
        if(x>112||x+15< -112||z>112||z+15< -112)return;
        String id=id(chunk);
        if(!pending.add(id))return;
        maintenance.add(()->{
            pending.remove(id);if(!chunk.isLoaded()||service.upgrading(chunk))return;
            boolean repair=false;
            if(fresh(chunk))for(Point p:cages.get(site.kind())){
                int px=site.x()+p.x(),pz=site.z()+p.z();
                if(Math.floorDiv(px,16)==chunk.getX()&&Math.floorDiv(pz,16)==chunk.getZ()){
                    Material live=chunk.getWorld().getBlockAt(px,site.kind().floor+p.y(),pz).getType();
                    if(Tag.LOGS.isTagged(live)||Tag.LEAVES.isTagged(live))repair=true;
                }
            }
            cleanCanopy(chunk,site);
            if(chunk.getPersistentDataContainer().getOrDefault(plugin.key("sanctuary_habitat"),PersistentDataType.INTEGER,0)<2){
                if(plugin.getConfig().getBoolean("overworld-sanctuaries.habitat.suppress-natural-hostiles",true))
                    for(Entity entity:chunk.getEntities())if(entity instanceof Mob mob&&protectedAt(entity.getLocation())
                        &&(mob instanceof Enemy||WILD.contains(entity.getEntitySpawnReason()))
                        &&!(mob instanceof Tameable pet&&pet.isTamed())
                        &&!mob.getPersistentDataContainer().has(plugin.key("world_boss_entity"))
                        &&!mob.getPersistentDataContainer().has(plugin.key("pet_model_preview"),PersistentDataType.BYTE)
                        &&!mob.getPersistentDataContainer().has(plugin.key("sanctuary_guard")))entity.remove();
                repairArena(chunk,site);
                installCages(chunk,site);
                chunk.getPersistentDataContainer().set(plugin.key("sanctuary_habitat"),PersistentDataType.INTEGER,2);
            }else if(repair)installCages(chunk,site);
        });
    }
    /** One migration repairs old pits/pillars only in the reserved combat cylinder. */
    private void repairArena(Chunk chunk,Site site){
        var arena=new SanctuaryArena(site,chunk.getWorld());Location c=arena.center();var g=service.geometry(site.kind());
        for(int x=chunk.getX()*16;x<chunk.getX()*16+16;x++)for(int z=chunk.getZ()*16;z<chunk.getZ()*16+16;z++){
            if(Math.hypot(x+.5-c.getX(),z+.5-c.getZ())>SanctuaryArena.RADIUS)continue;
            var column=g.columns.get(OverworldSanctuaries.key(x-site.x(),z-site.z()));if(column==null)continue;
            Map<Integer,Voxel> authored=new HashMap<>();for(Voxel v:column)authored.put(site.kind().floor+v.y(),v);
            for(int y=c.getBlockY()-4;y<=c.getBlockY()+13;y++){
                Voxel v=authored.get(y);Block b=chunk.getWorld().getBlockAt(x,y,z);
                if(v!=null){if(!b.getBlockData().equals(g.palette[v.palette()]))b.setBlockData(g.palette[v.palette()],false);}
                else if(y>=c.getBlockY()&&!b.isEmpty())b.setType(Material.AIR,false);
            }
        }
    }
    @EventHandler(priority=EventPriority.MONITOR)
    public void populated(ChunkPopulateEvent event){
        // Neighbour decorators can place tree crowns after this pad was already cleared.
        World w=event.getWorld();int cx=event.getChunk().getX(),cz=event.getChunk().getZ();
        var placement=service.placement(w);
        if(placement==null)return;
        Site candidate=placement.cell(Math.floorDiv(cx,placement.spacing),Math.floorDiv(cz,placement.spacing));
        if(candidate==null||Math.abs(cx*16-candidate.x())>144||Math.abs(cz*16-candidate.z())>144)return;
        fresh.put(id(event.getChunk()),Bukkit.getCurrentTick());
        fresh.entrySet().removeIf(e->{if(e.getValue()>Bukkit.getCurrentTick()-12000)return false;playerEdits.remove(e.getKey());return true;});
        Bukkit.getScheduler().runTask(plugin,()->{
            for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)if(w.isChunkLoaded(cx+dx,cz+dz)){
                var layout=service.placement(w);if(layout==null)continue;
                Site s=layout.cell(Math.floorDiv(cx+dx,layout.spacing),Math.floorDiv(cz+dz,layout.spacing));
                if(s!=null)settle(w.getChunkAt(cx+dx,cz+dz),s);
            }
        });
    }
    public void cleanCanopy(Chunk chunk,Site site){
        if(!plugin.getConfig().getBoolean("overworld-sanctuaries.habitat.clear-stray-leaves",true))return;
        var g=service.geometry(site.kind());World w=chunk.getWorld();
        var snapshot=chunk.getChunkSnapshot(false,false,false);
        boolean newChunk=fresh(chunk);var edited=playerEdits.getOrDefault(id(chunk),Set.of());
        for(int x=chunk.getX()*16;x<chunk.getX()*16+16;x++)for(int z=chunk.getZ()*16;z<chunk.getZ()*16+16;z++){
            var column=g.columns.get(OverworldSanctuaries.key(x-site.x(),z-site.z()));if(column==null)continue;
            Map<Integer,Voxel> authored=new HashMap<>();for(Voxel voxel:column)authored.put(voxel.y()+site.kind().floor,voxel);
            // Heightmap can be stale during decoration; inspect all heights, but only natural leaves.
            for(int y=Math.max(w.getMinHeight(),site.kind().floor);y<w.getMaxHeight();y++){
                Material material=snapshot.getBlockType(x&15,y,z&15);
                if(!Tag.LEAVES.isTagged(material)&&!(newChunk&&Tag.LOGS.isTagged(material)))continue;
                if(edited.contains(new Point(x,y,z)))continue;
                Block block=w.getBlockAt(x,y,z);
                boolean natural=Tag.LOGS.isTagged(material)||(block.getBlockData() instanceof Leaves leaves&&!leaves.isPersistent());
                if(!natural)continue;
                Voxel voxel=authored.get(y);
                if(voxel==null)block.setType(Material.AIR,false);
                else if(newChunk&&!block.getBlockData().equals(g.palette[voxel.palette()]))block.setBlockData(g.palette[voxel.palette()],false);
            }
        }
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void placed(BlockPlaceEvent event){
        if(!fresh(event.getBlock().getChunk()))return;
        var b=event.getBlock();playerEdits.computeIfAbsent(id(b.getChunk()),ignored->new HashSet<>()).add(new Point(b.getX(),b.getY(),b.getZ()));
    }
    public void installCages(Chunk chunk,Site site){
        if(!plugin.getConfig().getBoolean("overworld-sanctuaries.habitat.guard-spawners",true))return;
        var points=cages.get(site.kind());World w=chunk.getWorld();
        for(int i=0;i<points.size();i++){
            Point p=points.get(i);int x=site.x()+p.x(),y=site.kind().floor+p.y(),z=site.z()+p.z();
            if(Math.floorDiv(x,16)!=chunk.getX()||Math.floorDiv(z,16)!=chunk.getZ())continue;
            Block block=w.getBlockAt(x,y,z);
            // Never restore mined cages or replace a player's build during an upgrade.
            if(!block.getType().isAir()||!block.getRelative(BlockFace.DOWN).getType().isSolid())continue;
            block.setType(Material.SPAWNER,false);CreatureSpawner spawner=(CreatureSpawner)block.getState();
            LivingEntity template=(LivingEntity)w.createEntity(block.getLocation(),TYPES[i].getEntityClass());
            equip(template);
            var entry=new SpawnerEntry(template.createSnapshot(),1,new SpawnRule(0,15,0,15));
            spawner.setSpawnedEntity(entry);spawner.setPotentialSpawns(List.of(entry));
            spawner.setSpawnCount(1);spawner.setMaxNearbyEntities(3);spawner.setRequiredPlayerRange(14);spawner.setSpawnRange(3);
            spawner.setMinSpawnDelay(500);spawner.setMaxSpawnDelay(900);spawner.setDelay(200);
            spawner.getPersistentDataContainer().set(plugin.key("sanctuary_guard_cage"),PersistentDataType.INTEGER,i);
            spawner.update(true,false);
        }
    }
    private void equip(LivingEntity entity){
        entity.getPersistentDataContainer().set(plugin.key("sanctuary_guard"),PersistentDataType.BYTE,(byte)1);
        if(entity instanceof Piglin piglin){piglin.setImmuneToZombification(true);piglin.setBaby(false);}
        if(entity instanceof Mob mob){mob.setCanPickupItems(false);mob.setRemoveWhenFarAway(true);}
        var gear=entity.getEquipment();if(gear==null)return;
        boolean pig=entity.getType()==EntityType.PIGLIN;
        gear.setHelmet(new ItemStack(pig?Material.GOLDEN_HELMET:Material.IRON_HELMET));
        gear.setChestplate(new ItemStack(pig?Material.GOLDEN_CHESTPLATE:Material.CHAINMAIL_CHESTPLATE));
        gear.setLeggings(new ItemStack(Material.CHAINMAIL_LEGGINGS));gear.setBoots(new ItemStack(Material.IRON_BOOTS));
        gear.setItemInMainHand(new ItemStack(entity.getType()==EntityType.SKELETON?Material.BOW:pig?Material.GOLDEN_SWORD:Material.STONE_SWORD));
        gear.setHelmetDropChance(0);gear.setChestplateDropChance(0);gear.setLeggingsDropChance(0);gear.setBootsDropChance(0);gear.setItemInMainHandDropChance(0);
        // Deliberately no Evergarden/no-level tags: native SPAWNER events reach LevelledMobs.
    }
    private boolean cage(Block block){
        return block.getState() instanceof CreatureSpawner s&&s.getPersistentDataContainer().has(plugin.key("sanctuary_guard_cage"),PersistentDataType.INTEGER);
    }
    @EventHandler(priority=EventPriority.LOWEST,ignoreCancelled=true)
    public void guards(SpawnerSpawnEvent event){
        if(!cage(event.getSpawner().getBlock()))return;
        Location at=event.getLocation();Site s=site(event.getSpawner().getLocation());
        if(s==null||!contains(s,at.getBlockX(),at.getBlockY(),at.getBlockZ())){event.setCancelled(true);return;}
        long nearby=event.getLocation().getWorld().getNearbyEntities(event.getLocation(),24,12,24).stream()
            .filter(e->e.getPersistentDataContainer().has(plugin.key("sanctuary_guard"))).count();
        if(nearby>=6){event.setCancelled(true);return;}
        // Template already carries armor before CreatureSpawnEvent / LevelledMobs sees it.
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void breakCage(BlockBreakEvent event){if(cage(event.getBlock())){event.setCancelled(true);event.setDropItems(false);event.setExpToDrop(0);}}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void entityExplosion(EntityExplodeEvent event){event.blockList().removeIf(this::cage);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void blockExplosion(BlockExplodeEvent event){event.blockList().removeIf(this::cage);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void extend(BlockPistonExtendEvent event){if(event.getBlocks().stream().anyMatch(this::cage))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void retract(BlockPistonRetractEvent event){if(event.getBlocks().stream().anyMatch(this::cage))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void change(EntityChangeBlockEvent event){if(cage(event.getBlock()))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void interact(PlayerInteractEvent event){
        if(event.getClickedBlock()!=null&&event.getAction()==Action.RIGHT_CLICK_BLOCK&&cage(event.getClickedBlock()))event.setCancelled(true);
    }
}
