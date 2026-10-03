import com.example.voidscape.VoidscapePlugin;
import com.example.voidscape.world.*;
import com.example.advancemagic.AdvanceMagicPlugin;
import com.example.advancemagic.spell.Spell;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.server.network.*;
import org.bukkit.*;
import org.bukkit.block.BlockFace;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.entity.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.plugin.java.JavaPlugin;
import java.net.InetSocketAddress;
import java.nio.file.*;
import java.util.*;

/** Real generated chunks, event protection and persisted placement; never shipped. */
public final class WorldBossTempleProbe extends JavaPlugin {
    static final class MiningAudit implements org.bukkit.event.Listener {
        int calls;
        @org.bukkit.event.EventHandler(priority=org.bukkit.event.EventPriority.HIGH,ignoreCancelled=true)
        public void beforeAbilities(BlockBreakEvent event){calls++;}
    }
    int checks;VoidscapePlugin garden;AdvanceMagicPlugin magic;Player player;ServerPlayer handle;
    void check(boolean value,String label){if(!value)throw new AssertionError(label);checks++;getLogger().info("PASS Temple: "+label);}
    @Override public void onEnable(){Bukkit.getScheduler().runTaskLater(this,()->{
        try{run();Files.writeString(Path.of("world-boss-temple-result.txt"),"PASS "+checks+" checks: actual generation, empty arena, protection, magic terrain, placement and restart stability");}
        catch(Throwable error){getLogger().log(java.util.logging.Level.SEVERE,"TEMPLE CHECK FAILED",error);try{Files.writeString(Path.of("world-boss-temple-result.txt"),"FAIL "+error);}catch(Exception ignored){}}
        finally{cleanup();Bukkit.getScheduler().runTaskLater(this,Bukkit::shutdown,5);}
    },40);}
    void run()throws Exception {
        garden=(VoidscapePlugin)Bukkit.getPluginManager().getPlugin("Evergarden");
        magic=(AdvanceMagicPlugin)Bukkit.getPluginManager().getPlugin("advance-magic");
        check(garden!=null&&garden.isEnabled()&&magic!=null&&magic.isEnabled(),"plugins boot and register protection");
        var world=garden.world();var layout=garden.bossTemples();var b=WorldBossTemple.blueprint();
        check(layout.chance()==1&&layout.spacingChunks()==garden.skyLandmarks().spacingChunks(),"placement inherits the Garden grid and persists its initial configured rate");
        var site=layout.nearest(0,0,12);check(site!=null,"locates an ancient temple "+site);
        Path checkpoint=Path.of("world-boss-temple-checkpoint.txt");
        String location=site.x()+","+site.z();
        if(Files.exists(checkpoint)){
            check(Files.readString(checkpoint).equals(location),"restart retains the same site after config spacing/chance edits");
            check(garden.getConfig().getDouble("structures.world-boss-temple.chance")==0,"restart actually loaded the edited config");
        }
        check(WorldBossTemple.RADIUS*2+1>57*4,"full temple width exceeds four times the old sanctuary bounds");
        check(WorldBossTemple.ARENA_RADIUS*2+1>37*4,"combat disc exceeds four times the old 37-block arena diameter");
        check(b.boxes().stream().noneMatch(box->Set.of(Material.CHEST,Material.VAULT,Material.LODESTONE,Material.TRIAL_SPAWNER,Material.SPAWNER).contains(box.material())),"boss arena has no encounter trigger, spawner or reward chest");
        int minX=b.boxes().stream().mapToInt(WorldBossTemple.Box::x1).min().orElseThrow();
        int maxX=b.boxes().stream().mapToInt(WorldBossTemple.Box::x2).max().orElseThrow();
        check(maxX-minX+1==241,"actual block geometry spans 241 blocks");
        var oldWhales=new SkyWhaleLayout(world.getSeed(),garden.layout(),garden.skyWhales().spacingChunks(),garden.skyWhales().chance());
        var oldLandmarks=new SkyLandmarkLayout(world.getSeed(),garden.layout(),garden.skyWhales());
        int gx=Math.floorDiv(site.x(),layout.spacingChunks()*16),gz=Math.floorDiv(site.z(),layout.spacingChunks()*16);
        check(garden.skyLandmarks().cell(gx,gz).equals(oldLandmarks.cell(gx,gz)),"adding a boss temple leaves Garden/Observatory candidates unchanged");
        var excluded=new WorldBossTempleLayout(world.getSeed(),garden.layout(),garden.skyWhales(),garden.skyLandmarks(),layout.spacingChunks(),1,Set.of(((long)gx<<32)|(gz&0xffffffffL)));
        check(excluded.cell(gx,gz)==null,"migration excludes explored cells and prevents partial structures");
        var zero=new WorldBossTempleLayout(world.getSeed(),garden.layout(),garden.skyWhales(),garden.skyLandmarks(),32,0,Set.of());
        check(zero.nearest(0,0,8)==null,"zero chance creates no temple candidates");
        int sites=0,negative=0;
        for(int a=-10;a<=10;a++)for(int c=-10;c<=10;c++){
            var candidate=layout.cell(a,c);if(candidate==null)continue;sites++;if(candidate.x()<0||candidate.z()<0)negative++;
            check(layout.at(candidate.x()-120,candidate.z(),0)==candidate&&layout.at(candidate.x()+120,candidate.z(),0)==candidate,"both far edges are reserved, including negative coordinates");
            for(var temple:garden.layout().nearby(candidate.x(),candidate.z()))
                check(Math.abs(candidate.x()-temple.x())>=188||Math.abs(candidate.z()-temple.z())>=188,"ancient temple keeps clearance from regular temples");
        }
        check(sites>0&&negative>0,"placement remains available after collision checks, including negative coordinates");
        var enabled=new VoidGenerator(world.getSeed(),garden.layout(),garden.skyWhales(),true,garden.skyLandmarks(),true,true,null,layout,true);
        var disabled=new VoidGenerator(world.getSeed(),garden.layout(),garden.skyWhales(),true,garden.skyLandmarks(),true,true,null,layout,false);
        int solids=0,air=0,restored=0;
        long started=System.nanoTime();
        // Reverse chunk order exercises seam safety rather than relying on central chunks first.
        for(int dx=7;dx>=-8;dx--)for(int dz=7;dz>=-8;dz--){
            int cx=Math.floorDiv(site.x(),16)+dx,cz=Math.floorDiv(site.z(),16)+dz;
            var snapshot=world.getChunkAt(cx,cz).getChunkSnapshot(false,false,false);
            var expected=Bukkit.getServer().createChunkData(world);b.render(expected,cx,cz,site.x(),site.z());
            var raw=Bukkit.getServer().createChunkData(world);enabled.generateNoise(world,new Random(9),cx,cz,raw);
            for(int x=0;x<16;x++)for(int z=0;z<16;z++){
                int wx=cx*16+x,wz=cz*16+z;
                if(!site.contains(wx,wz,0))continue;
                if(disabled.surface(wx,wz).land())restored++;
                for(int y=WorldBossTemple.MIN_Y;y<=WorldBossTemple.MAX_Y+24;y++){
                    var wanted=expected.getType(x,y,z);
                    if(snapshot.getBlockType(x,y,z)!=wanted||raw.getType(x,y,z)!=wanted)
                        throw new AssertionError("Chunk seam/terrain intrusion at "+wx+","+y+","+wz+" expected="+wanted+" actual="+snapshot.getBlockType(x,y,z));
                    if(wanted.isAir())air++;else solids++;
                    if(wanted.name().endsWith("_LEAVES")&&!((org.bukkit.block.data.type.Leaves)snapshot.getBlockData(x,y,z)).isPersistent())throw new AssertionError("Decaying generated foliage");
                }
            }
        }
        check(solids>500000&&air>1000000,"all generated blocks match the blueprint; "+solids+" solid blocks / "+air+" reserved air cells");
        check(restored>0,"disabled temple generator restores the natural terrain reservation");
        getLogger().info("Generated and checked temple in "+(System.nanoTime()-started)/1e9+" seconds");
        for(int x=-80;x<=80;x++)for(int z=-80;z<=80;z++)if(x*x+z*z<=6400){
            if(!world.getBlockAt(site.x()+x,100,site.z()+z).getType().isSolid())throw new AssertionError("Missing combat floor "+x+","+z);
            for(int y=101;y<150;y++)if(!world.getBlockAt(site.x()+x,y,site.z()+z).getType().isAir())throw new AssertionError("Combat headroom obstruction "+x+","+y+","+z);
        }
        check(true,"entire 161-block arena has a continuous floor and 49 blocks of clear vertical headroom");
        for(BlockFace face:List.of(BlockFace.NORTH,BlockFace.SOUTH,BlockFace.EAST,BlockFace.WEST)){
            int previous=100;
            for(int distance=0;distance<=108;distance++){
                int x=face.getModX()*distance,z=face.getModZ()*distance,top=100;
                while(top<110&&!world.getBlockAt(site.x()+x,top+1,site.z()+z).getType().isAir())top++;
                check(top-previous<=1&&top>=previous,"cardinal entrance is walkable without jumping two-block ledges");previous=top;
                check(world.getBlockAt(site.x()+x,top,site.z()+z).getType().isSolid()&&world.getBlockAt(site.x()+x,top+2,site.z()+z).getType().isAir(),"approach has a solid floor and headroom");
            }
        }
        createPlayer(world,site);
        var floor=world.getBlockAt(site.x(),100,site.z());var outside=world.getBlockAt(site.x()+121,100,site.z());
        player.setOp(false);player.setGameMode(GameMode.SURVIVAL);
        var audit=new MiningAudit();Bukkit.getPluginManager().registerEvents(audit,this);
        var mine=new BlockBreakEvent(floor,player);Bukkit.getPluginManager().callEvent(mine);check(mine.isCancelled(),"survival player cannot mine the arena");
        check(audit.calls==0,"mining is cancelled before enchanted mining abilities can run");
        var build=new BlockPlaceEvent(floor.getRelative(BlockFace.UP),floor.getRelative(BlockFace.UP).getState(),floor,new org.bukkit.inventory.ItemStack(Material.STONE),player,true);
        Bukkit.getPluginManager().callEvent(build);check(build.isCancelled(),"combat headroom cannot be blocked by player building");
        var beyond=new BlockBreakEvent(outside,player);Bukkit.getPluginManager().callEvent(beyond);check(!beyond.isCancelled(),"normal mining outside the temple is unaffected");
        var flow=new BlockFromToEvent(outside,floor);Bukkit.getPluginManager().callEvent(flow);check(flow.isCancelled(),"liquids cannot enter the arena");
        var piston=new BlockPistonExtendEvent(outside,List.of(floor),BlockFace.EAST);Bukkit.getPluginManager().callEvent(piston);check(piston.isCancelled(),"pistons cannot move arena blocks");
        var burn=new BlockBurnEvent(floor);Bukkit.getPluginManager().callEvent(burn);check(burn.isCancelled(),"fire cannot consume protected architecture");
        var list=new ArrayList<org.bukkit.block.Block>(List.of(floor,outside));
        var explosion=new BlockExplodeEvent(outside,outside.getState(),list,0,ExplosionResult.DESTROY);Bukkit.getPluginManager().callEvent(explosion);
        check(!explosion.blockList().contains(floor)&&explosion.blockList().contains(outside),"explosions preserve temple blocks without cancelling unrelated damage");
        player.setOp(true);player.setGameMode(GameMode.CREATIVE);
        mine=new BlockBreakEvent(floor,player);Bukkit.getPluginManager().callEvent(mine);check(!mine.isCancelled(),"creative admins retain maintenance access");
        player.setGameMode(GameMode.SURVIVAL);
        mine=new BlockBreakEvent(floor,player);Bukkit.getPluginManager().callEvent(mine);check(mine.isCancelled(),"operator survival mode still cannot mine the temple");
        try(var zone=magic.terrain().open(player,Spell.SOLAR_APOCALYPSE,floor.getLocation(),Material.LAVA)){
            zone.tick(20,300);check(floor.getType()!=Material.LAVA&&zone.samples(20,0).isEmpty(),"Advance Magic terrain cannot replace the protected boss floor");
        }
        check(world.getNearbyEntities(new org.bukkit.util.BoundingBox(site.x()-120,40,site.z()-120,site.x()+120,206,site.z()+120)).stream().allMatch(e->e==player),"no bosses, guards, display entities or encounter state were spawned");
        player.teleport(new Location(Bukkit.getWorlds().getFirst(),.5,100,.5));
        check(Bukkit.dispatchCommand(player,"evergarden tp boss-temple")&&player.getWorld()==world&&player.getLocation().getBlockZ()==site.z()+108,"admin teleport reaches the verified safe temple entrance");
        Files.writeString(checkpoint,location);world.save();
        garden.getConfig().set("structures.world-boss-temple.chance",0);garden.getConfig().set("structures.world-boss-temple.spacing-chunks",64);garden.saveConfig();
    }
    void createPlayer(World world,WorldBossTempleLayout.Site site){
        var server=MinecraftServer.getServer();var level=((CraftWorld)world).getHandle();
        var profile=new GameProfile(UUID.randomUUID(),"TempleTestActor");
        handle=new ServerPlayer(server,level,profile,ClientInformation.createDefault());
        var connection=new Connection(PacketFlow.SERVERBOUND);connection.channel=new EmbeddedChannel();connection.address=new InetSocketAddress("127.0.0.1",1);
        handle.connection=new ServerGamePacketListenerImpl(server,connection,handle,CommonListenerCookie.createInitial(profile,false));
        handle.setPos(site.x()+.5,101,site.z()+.5);server.getPlayerList().getPlayers().add(handle);server.getPlayerList().getPlayersByUUID().put(profile.id(),handle);
        level.addNewPlayer(handle);player=handle.getBukkitEntity();player.setGravity(false);player.setInvulnerable(true);
    }
    void cleanup(){if(handle==null)return;var server=MinecraftServer.getServer();server.getPlayerList().getPlayers().remove(handle);server.getPlayerList().getPlayersByUUID().remove(handle.getUUID());handle.discard();}
}
