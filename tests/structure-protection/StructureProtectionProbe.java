import com.example.voidscape.VoidscapePlugin;
import com.example.voidscape.world.*;
import com.example.advancemagic.AdvanceMagicPlugin;
import com.example.advancemagic.api.MagicTerrainEvent;
import com.example.advancemagic.spell.Spell;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.server.network.*;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import java.net.InetSocketAddress;
import java.nio.file.*;
import java.util.*;

/** Real Paper event dispatch in a disposable world, including a second server boot. */
public final class StructureProtectionProbe extends JavaPlugin {
    static final class MiningAudit implements Listener {
        int calls;
        @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true)
        public void ability(BlockBreakEvent e){calls++;}
    }
    int checks;VoidscapePlugin garden;AdvanceMagicPlugin magic;StructureProtection protection;
    Player player;ServerPlayer handle;MiningAudit audit=new MiningAudit();
    void check(boolean value,String label){if(!value)throw new AssertionError(label);checks++;getLogger().info("PASS Structure: "+label);}
    @Override public void onEnable(){Bukkit.getScheduler().runTaskLater(this,()->{
        try{run();Files.writeString(Path.of("structure-protection-result.txt"),"PASS "+checks+" checks: generated landmarks, mining, bounds, other worlds, far coordinates, abilities, admin bypass, magic and restart");}
        catch(Throwable error){getLogger().log(java.util.logging.Level.SEVERE,"STRUCTURE CHECK FAILED",error);try{Files.writeString(Path.of("structure-protection-result.txt"),"FAIL "+error);}catch(Exception ignored){}}
        finally{cleanup();Bukkit.getScheduler().runTaskLater(this,Bukkit::shutdown,5);}
    },40);}
    BlockBreakEvent mine(Block block){var event=new BlockBreakEvent(block,player);Bukkit.getPluginManager().callEvent(event);return event;}
    void run()throws Exception {
        garden=(VoidscapePlugin)Bukkit.getPluginManager().getPlugin("Evergarden");
        magic=(AdvanceMagicPlugin)Bukkit.getPluginManager().getPlugin("advance-magic");
        check(garden!=null&&garden.isEnabled()&&magic!=null&&magic.isEnabled(),"plugins boot with structure listeners");
        protection=garden.structureProtection();var world=garden.world();createPlayer(world);
        Bukkit.getPluginManager().registerEvents(audit,this);
        List<String> locations=new ArrayList<>();
        var whale=garden.skyWhales().nearest(0,0,12);check(whale!=null,"locates a generated whale");
        locations.add("whale:"+whale.x()+","+whale.z());
        for(var b:SkyWhale.blocks())if(!protection.protects(world.getBlockAt(whale.x()+b.x(),b.y(),whale.z()+b.z())))
            throw new AssertionError("Unprotected whale blueprint block "+b);
        check(true,"every whale blueprint block lies within protection");
        var wb=SkyWhale.blocks().stream().filter(b->b.material().isSolid())
            .min(Comparator.comparingInt(b->b.x()*b.x()+b.z()*b.z())).orElseThrow();
        var whaleBlock=world.getBlockAt(whale.x()+wb.x(),wb.y(),whale.z()+wb.z());
        check(whaleBlock.getType()==wb.material(),"whale sample matches actual generated blocks");
        verify("whale",whaleBlock,"structures.sky-whale.enabled",whale.x(),whale.z(),SkyWhale.MIN_Y,SkyWhale.MAX_Y,116);
        chest("whale",world.getBlockAt(whale.x()-52,115,whale.z()+7),world.getBlockAt(whale.x()-50,115,whale.z()+7));
        for(var kind:SkyLandmarkLayout.Kind.values()){
            var site=garden.skyLandmarks().nearest(kind,0,0,12);check(site!=null,"locates "+kind.id);
            locations.add(kind.id+":"+site.x()+","+site.z());
            for(var b:kind.blueprint().blocks())if(!protection.protects(world.getBlockAt(site.x()+b.x(),b.y(),site.z()+b.z())))
                throw new AssertionError("Unprotected "+kind.id+" blueprint block "+b);
            check(true,"every "+kind.id+" blueprint block lies within protection");
            var b=kind.blueprint().blocks().stream().filter(v->v.material().isSolid())
                .min(Comparator.comparingInt(v->v.x()*v.x()+v.z()*v.z())).orElseThrow();
            var inside=world.getBlockAt(site.x()+b.x(),b.y(),site.z()+b.z());
            check(inside.getType()==b.material(),kind.id+" sample matches actual generated blocks");
            verify(kind.id,inside,"structures."+kind.id+".enabled",site.x(),site.z(),LandmarkBlueprint.MIN_Y,LandmarkBlueprint.MAX_Y,kind.radius+1);
            chest(kind.id,world.getBlockAt(site.x()+4,kind.chestY,site.z()+kind.chestZ),world.getBlockAt(site.x()+6,kind.chestY,site.z()+kind.chestZ));
        }
        var boss=garden.bossTemples().nearest(0,0,12);check(boss!=null,"locates a world boss temple");
        locations.add("boss:"+boss.x()+","+boss.z());
        verify("boss temple",world.getBlockAt(boss.x(),100,boss.z()),"structures.world-boss-temple.enabled",boss.x(),boss.z(),WorldBossTemple.MIN_Y,WorldBossTemple.MAX_Y+24,WorldBossTemple.RADIUS+1);
        for(var site:DungeonLayout.STARTER_SITES){
            var floor=world.getBlockAt(site.x(),100,site.z());
            check(mine(floor).isCancelled(),"small temple remains protected: "+site.kind());
            check(!mine(world.getBlockAt(site.x(),93,site.z())).isCancelled(),"below small temple remains mineable: "+site.kind());
        }
        check(mine(world.getBlockAt(0,97,0)).isCancelled(),"spawn protection remains enabled");
        var web=world.getBlockAt(0,105,-250);web.setType(Material.COBWEB);
        var webEvent=mine(web);check(!webEvent.isCancelled()&&!webEvent.isDropItems(),"small-temple combat webs remain breakable without drops");
        web.setType(Material.AIR);
        for(int x:new int[]{-499999,-450001,-100001,100001,450001,499999})for(int z:new int[]{-490001,-77777,77777,490001}){
            var ordinary=world.getBlockAt(x,16,z);
            check(!garden.dungeons().isSpawnIsland(ordinary)&&!mine(ordinary).isCancelled(),"ordinary distant terrain remains mineable at "+x+","+z);
        }
        boolean negativeWhale=false;Set<SkyLandmarkLayout.Kind> negativeLandmarks=EnumSet.noneOf(SkyLandmarkLayout.Kind.class);
        for(int gx=-8;gx<=-1;gx++)for(int gz=-8;gz<=-1;gz++){
            var site=garden.skyWhales().cell(gx,gz);
            if(site!=null&&!negativeWhale){check(mine(world.getBlockAt(site.x(),100,site.z())).isCancelled(),"negative-coordinate whale is protected");negativeWhale=true;}
            for(var landmark:garden.skyLandmarks().cell(gx,gz))if(negativeLandmarks.add(landmark.kind()))
                check(mine(world.getBlockAt(landmark.x(),100,landmark.z())).isCancelled(),"negative-coordinate "+landmark.kind().id+" is protected");
        }
        check(negativeWhale&&negativeLandmarks.size()==2,"negative-coordinate samples cover all three sky landmark kinds");
        legacyGarden();
        var checkpoint=Path.of("structure-protection-checkpoint.txt");String sites=String.join("\n",locations);
        if(Files.exists(checkpoint))check(Files.readString(checkpoint).equals(sites),"restart preserves placement and re-registers protection for existing structures");
        Files.writeString(checkpoint,sites);world.save();
    }
    void verify(String name,Block inside,String setting,int x,int z,int minY,int maxY,int outsideOffset){
        var world=inside.getWorld();player.setOp(false);player.setGameMode(GameMode.SURVIVAL);
        int calls=audit.calls;
        check(mine(inside).isCancelled(),name+": Survival cannot mine");
        check(audit.calls==calls,name+": cancellation occurs before mining abilities");
        player.getInventory().setItemInMainHand(new ItemStack(Material.DIAMOND_PICKAXE));
        player.teleport(inside.getLocation().add(.5,1,.5));var original=inside.getBlockData();
        check(inside.getType().isSolid()&&!player.breakBlock(inside)&&inside.getBlockData().equals(original),name+": actual Survival mining leaves the solid block intact");
        var damage=new BlockDamageEvent(player,inside,new ItemStack(Material.DIAMOND_PICKAXE),true);
        Bukkit.getPluginManager().callEvent(damage);check(damage.isCancelled(),name+": instant mining is blocked");
        var place=new BlockPlaceEvent(inside.getRelative(BlockFace.UP),inside.getRelative(BlockFace.UP).getState(),inside,new ItemStack(Material.STONE),player,true);
        Bukkit.getPluginManager().callEvent(place);check(place.isCancelled(),name+": placement is blocked");
        check(!mine(world.getBlockAt(x+outsideOffset,100,z)).isCancelled(),name+": horizontal exterior is mineable");
        check(protection.protects(world.getBlockAt(x,minY,z))&&protection.protects(world.getBlockAt(x,maxY,z)),name+": vertical bounds are inclusive");
        check(!mine(world.getBlockAt(x,minY-1,z)).isCancelled()&&!mine(world.getBlockAt(x,maxY+1,z)).isCancelled(),name+": above and below are mineable");
        check(!mine(Bukkit.getWorlds().getFirst().getBlockAt(inside.getX(),inside.getY(),inside.getZ())).isCancelled(),name+": matching coordinates in another world are mineable");
        var outside=world.getBlockAt(x+outsideOffset,100,z);
        var blast=new BlockExplodeEvent(outside,outside.getState(),new ArrayList<>(List.of(inside,outside)),0,ExplosionResult.DESTROY);
        Bukkit.getPluginManager().callEvent(blast);check(!blast.blockList().contains(inside)&&blast.blockList().contains(outside),name+": explosions preserve only protected blocks");
        var piston=new BlockPistonExtendEvent(outside,List.of(inside),BlockFace.EAST);
        Bukkit.getPluginManager().callEvent(piston);check(piston.isCancelled(),name+": pistons cannot move structure blocks");
        var flow=new BlockFromToEvent(outside,inside);Bukkit.getPluginManager().callEvent(flow);check(flow.isCancelled(),name+": outside liquids cannot enter");
        var bucket=new PlayerBucketEmptyEvent(player,inside,inside,BlockFace.UP,Material.WATER_BUCKET,new ItemStack(Material.BUCKET),EquipmentSlot.HAND);
        Bukkit.getPluginManager().callEvent(bucket);check(bucket.isCancelled(),name+": buckets cannot modify protected blocks");
        var tool=new PlayerInteractEvent(player,Action.RIGHT_CLICK_BLOCK,new ItemStack(Material.DIAMOND_AXE),inside,BlockFace.UP,EquipmentSlot.HAND);
        Bukkit.getPluginManager().callEvent(tool);check(tool.useInteractedBlock()==Event.Result.DENY,name+": tool modification is blocked");
        var terrain=new MagicTerrainEvent(player,Spell.SOLAR_APOCALYPSE,inside,Material.LAVA);
        Bukkit.getPluginManager().callEvent(terrain);check(terrain.isCancelled(),name+": magic terrain cannot replace structure blocks");
        player.setGameMode(GameMode.CREATIVE);check(mine(inside).isCancelled(),name+": ordinary Creative player cannot bypass");
        player.setOp(true);check(!mine(inside).isCancelled(),name+": Creative admin can maintain blocks");
        player.setGameMode(GameMode.SURVIVAL);check(mine(inside).isCancelled(),name+": Survival operator cannot bypass");player.setOp(false);
        Listener uncancel=new Listener(){@EventHandler(priority=EventPriority.NORMAL) public void change(BlockBreakEvent e){e.setCancelled(false);}};
        Bukkit.getPluginManager().registerEvents(uncancel,this);
        try{check(mine(inside).isCancelled(),name+": protection is enforced again after other listeners");}finally{HandlerList.unregisterAll(uncancel);}
        garden.getConfig().set(setting,false);
        try{check(!mine(inside).isCancelled(),name+": disabling this structure disables its protection");}finally{garden.getConfig().set(setting,true);}
    }
    void chest(String name,Block... candidates){
        var block=Arrays.stream(candidates).filter(b->b.getType()==Material.CHEST).findFirst().orElseThrow(()->new AssertionError("Missing generated "+name+" chest"));
        var event=new PlayerInteractEvent(player,Action.RIGHT_CLICK_BLOCK,null,block,BlockFace.UP,EquipmentSlot.HAND);
        Bukkit.getPluginManager().callEvent(event);
        check(event.useInteractedBlock()!=Event.Result.DENY,name+": ordinary chest interaction remains allowed");
    }
    // Reproduce the paired placement algorithm shipped before independent landmark placement.
    SkyLandmarkLayout.Site legacyGardenSite(int startX){
        var whales=new SkyWhaleLayout(garden.world().getSeed(),garden.layout(),garden.skyWhales().spacingChunks(),1);
        int spacing=whales.spacingChunks();
        for(int gx=startX;gx<12;gx++)for(int gz=3;gz<12;gz++){
            var whale=whales.cell(gx,gz);if(whale==null)continue;
            List<SkyLandmarkLayout.Site> candidates=new ArrayList<>();
            for(int x=5;x<spacing-5;x++)for(int z=5;z<spacing-5;z++){
                int bx=(gx*spacing+x)*16,bz=(gz*spacing+z)*16;
                if(Math.hypot(bx,bz)<480)continue;
                double wx=Math.max(0,Math.abs(bx-whale.x())-72)/115.0,wz=Math.max(0,Math.abs(bz-whale.z())-72)/65.0;
                if(wx*wx+wz*wz<=1)continue;
                boolean clear=true;
                for(var temple:garden.layout().nearby(bx,bz))if(Math.abs(bx-temple.x())<104&&Math.abs(bz-temple.z())<104){clear=false;break;}
                if(clear)candidates.add(new SkyLandmarkLayout.Site(SkyLandmarkLayout.Kind.OBSERVATORY,bx,bz));
            }
            Collections.shuffle(candidates,new Random(DungeonLayout.mix(garden.world().getSeed()^0x534b59474152444eL^(long)gx*341873128712L^(long)gz*132897987541L)));
            SkyLandmarkLayout.Site old=null;
            outer:for(var first:candidates)for(var second:candidates)if(Math.abs(first.x()-second.x())>124&&Math.abs(first.z()-second.z())>124){
                old=new SkyLandmarkLayout.Site(SkyLandmarkLayout.Kind.HANGING_GARDEN,second.x(),second.z());break outer;
            }
            if(old!=null&&garden.skyLandmarks().at(old.x(),old.z(),0)==null&&garden.bossTemples().at(old.x(),old.z(),0)==null)return old;
        }
        throw new AssertionError("Missing historical garden fixture");
    }
    void legacyGarden(){
        var site=legacyGardenSite(3);var world=garden.world();var blueprint=HangingGarden.blueprint();
        boolean existingFixture=Files.exists(Path.of("legacy-garden-fixture.txt"));
        // Preserve the old garden's central pavilion across the upgrade/restart.
        var marker=world.getBlockAt(site.x(),160,site.z());
        if(!existingFixture){
            for(var b:blueprint.blocks())if(Math.abs(b.x())<=8&&Math.abs(b.z())<=8)
                world.getBlockAt(site.x()+b.x(),b.y(),site.z()+b.z()).setType(b.material(),false);
            try{Files.writeString(Path.of("legacy-garden-fixture.txt"),site.x()+","+site.z());}catch(Exception e){throw new RuntimeException(e);}
        }
        check(marker.getType().isSolid()&&marker.getType()==blueprint.at(0,160,0),"historical garden pavilion exists outside current layout at "+site.x()+","+site.z());
        player.setOp(false);player.setGameMode(GameMode.SURVIVAL);player.getInventory().setItemInMainHand(new ItemStack(Material.DIAMOND_PICKAXE));
        var control=world.getBlockAt(site.x()+65,160,site.z());control.setType(Material.STONE,false);
        player.teleport(control.getLocation().add(.5,1,.5));
        check(player.breakBlock(control)&&control.getType()==Material.AIR,"actual Survival mining succeeds outside the historical garden");
        player.teleport(marker.getLocation().add(.5,1,.5));var before=marker.getBlockData();
        check(!player.breakBlock(marker)&&marker.getBlockData().equals(before),"actual Survival mining cannot remove a historical garden block");
        check(Files.isRegularFile(garden.getDataFolder().toPath().resolve("protected-structures.yml")),"recovered historical garden region is saved independently of layout rolls");
        var floor=world.getBlockAt(site.x()+20,160,site.z());floor.setType(Material.SMOOTH_QUARTZ,false);
        player.teleport(floor.getLocation().add(.5,1,.5));
        check(!player.breakBlock(floor)&&floor.getType()==Material.SMOOTH_QUARTZ,"recovered protection covers the entire historical garden footprint");
        check(!mine(world.getBlockAt(site.x(),LandmarkBlueprint.MIN_Y-1,site.z())).isCancelled(),"historical garden recovery does not protect terrain below its height bounds");
        var look=marker.getLocation().add(.5,1,.5);look.setPitch(90);player.teleport(look);
        check(Bukkit.dispatchCommand(player,"evergarden protection"),"non-operator can inspect the looked-at block's protection and installed version");
        if(!existingFixture){
            // Persistence must retain the region even after an administrator changes its original pavilion.
            for(int[] p:new int[][]{{-7,163,-7},{7,163,7},{-7,165,-7},{7,170,7},{-7,170,7},{7,165,-7},
                {0,182,0},{0,178,0},{0,185,0},{0,171,0},{0,173,0},{1,180,1}})
                world.getBlockAt(site.x()+p[0],p[1],site.z()+p[2]).setType(Material.AIR,false);
        }else check(world.getBlockAt(site.x(),185,site.z()).getType()==Material.AIR,
            "restart preserves saved protection even after its identifying architecture changes");
        var empty=legacyGardenSite(4);var ordinary=world.getBlockAt(empty.x(),160,empty.z());
        ordinary.setType(Material.SMOOTH_QUARTZ,false);player.teleport(ordinary.getLocation().add(.5,1,.5));
        check(!protection.protects(ordinary)&&player.breakBlock(ordinary)&&ordinary.getType()==Material.AIR,
            "an unbuilt historical candidate with one matching block stays mineable");
    }
    void createPlayer(World world){
        var server=MinecraftServer.getServer();var level=((CraftWorld)world).getHandle();
        var profile=new GameProfile(UUID.randomUUID(),"StructureActor");handle=new ServerPlayer(server,level,profile,ClientInformation.createDefault());
        var connection=new Connection(PacketFlow.SERVERBOUND);connection.channel=new EmbeddedChannel();connection.address=new InetSocketAddress("127.0.0.1",1);
        handle.connection=new ServerGamePacketListenerImpl(server,connection,handle,CommonListenerCookie.createInitial(profile,false));
        handle.setPos(.5,101,.5);server.getPlayerList().getPlayers().add(handle);server.getPlayerList().getPlayersByUUID().put(profile.id(),handle);
        level.addNewPlayer(handle);player=handle.getBukkitEntity();player.setGravity(false);player.setInvulnerable(true);
    }
    void cleanup(){if(handle==null)return;player.setOp(false);var server=MinecraftServer.getServer();server.getPlayerList().getPlayers().remove(handle);server.getPlayerList().getPlayersByUUID().remove(handle.getUUID());handle.discard();}
}
