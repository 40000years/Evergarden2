import com.example.voidscape.VoidscapePlugin;
import com.example.voidscape.world.*;
import com.example.voidscape.world.OverworldSanctuaries.*;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.server.network.*;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.entity.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import java.net.InetSocketAddress;
import java.nio.file.*;
import java.util.*;

/** Native cage ticks with an actual test player and LevelledMobs; local boundaries and restart. */
public final class SanctuaryHabitatProbe extends JavaPlugin {
    VoidscapePlugin garden;OverworldSanctuaries service;SanctuaryHabitat habitat;
    World world;Site site;Player player;ServerPlayer actor;boolean restart;
    final List<String> checks=new ArrayList<>();final List<CreatureSpawner> cages=new ArrayList<>();
    void check(boolean ok,String label){if(!ok)throw new AssertionError(label);checks.add(label);getLogger().info("PASS "+label);}
    @Override public void onEnable(){Bukkit.getScheduler().runTaskLater(this,()->{
        try{
            garden=(VoidscapePlugin)Bukkit.getPluginManager().getPlugin("Evergarden");service=garden.overworldSanctuaries();habitat=service.habitat();
            world=Bukkit.getWorlds().getFirst();world.setDifficulty(Difficulty.NORMAL);world.setGameRule(GameRule.RANDOM_TICK_SPEED,0);
            restart=Files.exists(Path.of("habitat-checkpoint.txt"));
            site=service.placement(world).nearest(Kind.ACHERON,0,0,128,r->{},()->false);check(site!=null,"real natural site located");
            var queue=new ArrayList<java.util.concurrent.CompletableFuture<?>>();
            // All eighteen positions use actual current geometry and usable room headroom.
            for(Kind k:Kind.values()){
                Site s=service.placement(world).nearest(k,0,0,128,r->{},()->false);
                check(s!=null,"natural candidate "+k);
                for(Point p:habitat.cages(k))queue.add(world.getChunkAtAsync((s.x()+p.x())>>4,(s.z()+p.z())>>4,true).thenAccept(chunk->
                    Bukkit.getScheduler().runTask(this,()->chunk.addPluginChunkTicket(this))));
            }
            java.util.concurrent.CompletableFuture.allOf(queue.toArray(java.util.concurrent.CompletableFuture[]::new)).whenComplete((v,e)->Bukkit.getScheduler().runTaskLater(this,()->{
                try{if(e!=null)throw new RuntimeException(e);inspect();}catch(Throwable t){finish(t);}
            },400));
        }catch(Throwable t){finish(t);}
    },10);}
    Location at(Point p){return new Location(world,site.x()+p.x()+.5,site.kind().floor+p.y(),site.z()+p.z()+.5);}
    void inspect()throws Exception {
        for(Kind k:Kind.values()){
            Site s=service.placement(world).nearest(k,0,0,128,r->{},()->false);
            for(Point p:habitat.cages(k)){
                Block block=world.getBlockAt(s.x()+p.x(),k.floor+p.y(),s.z()+p.z());
                check(block.getState() instanceof CreatureSpawner,"guard post exists "+k+" "+p+" actual="+block.getType());
            }
        }
        for(Point p:habitat.cages(site.kind())){
            var spawner=(CreatureSpawner)at(p).getBlock().getState();spawner.getChunk().addPluginChunkTicket(this);cages.add(spawner);
            check(spawner.getSpawnCount()==1&&spawner.getMaxNearbyEntities()==3,"cage rate and population capped");
            var entry=spawner.getPotentialSpawns().getFirst();
            check(entry.getSpawnRule().getMaxBlockLight()==15,"decorative lighting does not prevent guards");
            LivingEntity template=(LivingEntity)entry.getSnapshot().createEntity(world);
            check(template.getEquipment().getHelmet()!=null&&template.getEquipment().getChestplate()!=null,"native template already armored "+template.getType());
            check(!com.example.voidscape.compat.LevelledMobsCompat.isEvergardenMob(template),"guard has no LevelledMobs exclusion");
            if(template instanceof Piglin pig)check(pig.isImmuneToZombification()&&!pig.isBaby(),"adult piglin cannot zombify in Overworld");
        }
        createPlayer();boundaries();canopy();loot();protection();
        if(restart){
            check(Files.readString(Path.of("cage-template.txt")).equals(cages.getFirst().getSpawnedEntity().getAsString()),"cage template persists exactly across restart");
        }else Files.writeString(Path.of("cage-template.txt"),cages.getFirst().getSpawnedEntity().getAsString());
        tickCage(0,0);
    }
    void boundaries(){
        for(Kind k:Kind.values()){
            Site s=service.placement(world).nearest(k,0,0,128,r->{},()->false);var g=service.geometry(k);
            int checked=0;
            for(long packed:g.columns.keySet())if((checked++%137)==0){
                int x=s.x()+(int)(packed>>32),z=s.z()+(int)packed;
                int bottom=g.columns.get(packed).getFirst().y();
                check(habitat.contains(s,x,k.floor+bottom+1,z),"inside footprint "+k);
            }
            check(!habitat.contains(s,s.x(),k.floor-20,s.z()),"underground unaffected "+k);
            check(!habitat.contains(s,s.x()+113,k.floor+5,s.z()),"outside footprint unaffected "+k);
        }
        Location in=at(habitat.cages(site.kind()).getFirst()).add(1,0,0);
        Zombie z=world.createEntity(in,Zombie.class);
        var natural=new CreatureSpawnEvent(z,CreatureSpawnEvent.SpawnReason.NATURAL);Bukkit.getPluginManager().callEvent(natural);
        check(natural.isCancelled(),"natural hostile blocked inside structure");
        var spawned=new CreatureSpawnEvent(z,CreatureSpawnEvent.SpawnReason.SPAWNER);Bukkit.getPluginManager().callEvent(spawned);
        check(spawned.isCancelled(),"unowned spawner mob blocked inside");
        z.getPersistentDataContainer().set(garden.key("sanctuary_guard"),PersistentDataType.BYTE,(byte)1);
        spawned=new CreatureSpawnEvent(z,CreatureSpawnEvent.SpawnReason.SPAWNER);Bukkit.getPluginManager().callEvent(spawned);
        check(!spawned.isCancelled(),"authored cage guard allowed inside");
        Location out=new Location(world,site.x()+113,site.kind().floor+5,site.z());
        Zombie outside=world.createEntity(out,Zombie.class);var external=new CreatureSpawnEvent(outside,CreatureSpawnEvent.SpawnReason.NATURAL);Bukkit.getPluginManager().callEvent(external);
        check(!external.isCancelled(),"natural hostile allowed one block outside authored bounds");
        var cow=new CreatureSpawnEvent(world.createEntity(in,Cow.class),CreatureSpawnEvent.SpawnReason.NATURAL);Bukkit.getPluginManager().callEvent(cow);
        check(cow.isCancelled(),"ordinary natural animals suppressed inside structure");
    }
    void canopy(){
        int x=site.x()-10,z=site.z()-70,y=world.getMaxHeight()-10;Block stray=world.getBlockAt(x,y,z),kept=world.getBlockAt(x+1,y,z);
        Leaves leaves=(Leaves)Bukkit.createBlockData(Material.OAK_LEAVES);leaves.setPersistent(false);stray.setBlockData(leaves,false);
        leaves.setPersistent(true);kept.setBlockData(leaves,false);
        Block outside=world.getBlockAt(site.x()+113,y,z);leaves.setPersistent(false);outside.setBlockData(leaves,false);
        habitat.cleanCanopy(stray.getChunk(),site);
        check(stray.getType().isAir(),"stray natural leaves cleared even above tallest tower");
        check(kept.getType()==Material.OAK_LEAVES,"player persistent leaves preserved");
        check(outside.getType()==Material.OAK_LEAVES,"external forest canopy preserved");
        kept.setType(Material.AIR,false);outside.setType(Material.AIR,false);
    }
    void loot(){
        var inv=Bukkit.createInventory(null,27);inv.setItem(0,new ItemStack(Material.SPIRE_ARMOR_TRIM_SMITHING_TEMPLATE));
        var sword=new ItemStack(Material.DIAMOND_SWORD);sword.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.SHARPNESS,6);inv.setItem(2,sword);
        SanctuaryLoot.replaceTrims(inv,123L);var magic=(com.example.advancemagic.AdvanceMagicPlugin)Bukkit.getPluginManager().getPlugin("advance-magic");
        check(magic.manaPotions().quality(inv.getItem(0))!=null,"armor trim becomes a real consumable mana potion");
        check(inv.getItem(2).equals(sword),"gear and cap+1 preserved during trim replacement");
        byte[] first=inv.getItem(0).serializeAsBytes();SanctuaryLoot.replaceTrims(inv,123L);
        check(Arrays.equals(first,inv.getItem(0).serializeAsBytes()),"mana replacement is idempotent");
        // Actual new cache pipeline, plus an edited old cache which must be preserved.
        Point p=service.geometry(site.kind()).chests.getFirst();Block b=at(p).getBlock();
        Chest c=(Chest)b.getState();service.prepareLoot(b.getChunk());c=(Chest)b.getState();
        if(!restart)for(ItemStack item:c.getBlockInventory().getContents())check(!SanctuaryLoot.armorTrim(item),"cache contains no armor trim");
        else check(c.getBlockInventory().getItem(0).getType()==Material.SPIRE_ARMOR_TRIM_SMITHING_TEMPLATE,"player-added trim survives restart untouched");
        long seed=0;org.bukkit.inventory.Inventory original=Bukkit.createInventory(null,27);
        do{
            original.clear();org.bukkit.loot.LootTables.END_CITY_TREASURE.getLootTable().fillInventory(original,new Random(++seed),new org.bukkit.loot.LootContext.Builder(b.getLocation()).build());
        }while(Arrays.stream(original.getContents()).noneMatch(SanctuaryLoot::armorTrim));
        Random upgrades=new Random(seed^0x454e444349545932L);var contents=original.getContents();
        for(int slot=0;slot<contents.length;slot++)contents[slot]=SanctuaryLoot.upgrade(contents[slot],upgrades,.15,garden);
        c=(Chest)b.getState();c.setLootTable(null);c.getPersistentDataContainer().set(garden.key("sanctuary_loot_seed"),PersistentDataType.LONG,seed);
        c.getPersistentDataContainer().remove(garden.key("sanctuary_trim_profile"));c.update(false,false);c.getBlockInventory().setContents(contents);
        service.prepareLoot(b.getChunk());c=(Chest)b.getState();
        check(Arrays.stream(c.getBlockInventory().getContents()).noneMatch(SanctuaryLoot::armorTrim),"untouched old cache loses its seeded trim");
        check(Arrays.stream(c.getBlockInventory().getContents()).anyMatch(item->magic.manaPotions().quality(item)!=null),"untouched old cache gains mana without rerolling");
        for(int slot=0;slot<contents.length;slot++)if(!SanctuaryLoot.armorTrim(contents[slot]))check(Objects.equals(contents[slot],c.getBlockInventory().getItem(slot)),"old equipment and resources preserved slot "+slot);
        var live=c.getBlockInventory();live.setItem(0,new ItemStack(Material.SPIRE_ARMOR_TRIM_SMITHING_TEMPLATE));
        c=(Chest)b.getState();c.getPersistentDataContainer().remove(garden.key("sanctuary_trim_profile"));c.update(false,false);
        service.prepareLoot(b.getChunk());check(live.getItem(0).getType()==Material.SPIRE_ARMOR_TRIM_SMITHING_TEMPLATE,"player-edited old inventory is preserved");
    }
    void protection(){
        Block block=cages.getFirst().getBlock();
        var broken=new BlockBreakEvent(block,player);Bukkit.getPluginManager().callEvent(broken);check(broken.isCancelled()&&broken.getExpToDrop()==0,"cage cannot be mined, including Silk Touch");
        Block other=block.getRelative(0,-1,0);var ordinary=new BlockBreakEvent(other,player);Bukkit.getPluginManager().callEvent(ordinary);check(!ordinary.isCancelled(),"ordinary structure blocks remain mineable");
        var blast=new BlockExplodeEvent(other,other.getState(),new ArrayList<>(List.of(block,other)),1,ExplosionResult.DESTROY);
        Bukkit.getPluginManager().callEvent(blast);check(!blast.blockList().contains(block)&&blast.blockList().contains(other),"explosion protects only marked cage");
        var piston=new BlockPistonExtendEvent(other,List.of(block),BlockFace.UP);Bukkit.getPluginManager().callEvent(piston);check(piston.isCancelled(),"piston cannot relocate cage");
        var use=new PlayerInteractEvent(player,Action.RIGHT_CLICK_BLOCK,new ItemStack(Material.COW_SPAWN_EGG),block,BlockFace.UP,EquipmentSlot.HAND);
        Bukkit.getPluginManager().callEvent(use);check(use.isCancelled(),"spawn egg cannot change cage species");
    }
    void tickCage(int index,int attempts){
        if(index==3){try{Files.writeString(Path.of("habitat-checkpoint.txt"),"passed");finish(null);}catch(Throwable t){finish(t);}return;}
        try{
            var cage=(CreatureSpawner)cages.get(index).getBlock().getState();Location dest=cage.getLocation().add(1.5,0,.5);
            actor.setPos(dest.getX(),dest.getY(),dest.getZ());
            if(attempts==0){cage.setDelay(1);cage.update(false,false);}
            Bukkit.getScheduler().runTaskLater(this,()->{
                try{
                    var found=world.getNearbyEntities(cage.getLocation(),12,8,12).stream().filter(e->e.getType()==cage.getSpawnedType()&&e.getPersistentDataContainer().has(garden.key("sanctuary_guard"))).findFirst();
                    if(found.isEmpty()){if(attempts>12)throw new AssertionError("native spawner failed "+cage.getSpawnedType());tickCage(index,attempts+1);return;}
                    LivingEntity guard=(LivingEntity)found.get();
                    check(guard.getEntitySpawnReason()==CreatureSpawnEvent.SpawnReason.SPAWNER,"native spawner really spawned "+guard.getType());
                    check(guard.getEquipment().getHelmet()!=null&&guard.getEquipment().getChestplate()!=null,"spawned guard retains armor");
                    var api=io.github.arcaneplugins.levelledmobs.LevelledMobs.getInstance().getLevelInterface();
                    check(api.isLevelled(guard),"LevelledMobs automatically levels native guard "+guard.getType()+" level="+api.getLevelOfMob(guard));
                    guard.remove();tickCage(index+1,0);
                }catch(Throwable t){finish(t);}
            },20);
        }catch(Throwable t){finish(t);}
    }
    void createPlayer(){
        var server=MinecraftServer.getServer();var level=((CraftWorld)world).getHandle();
        var profile=new GameProfile(UUID.randomUUID(),"HabitatTester");actor=new ServerPlayer(server,level,profile,ClientInformation.createDefault());
        var connection=new Connection(PacketFlow.SERVERBOUND);connection.channel=new EmbeddedChannel();connection.address=new InetSocketAddress("127.0.0.1",1);
        actor.connection=new ServerGamePacketListenerImpl(server,connection,actor,CommonListenerCookie.createInitial(profile,false)){
            @Override public boolean hasClientLoaded(){return true;}
        };
        actor.setPos(site.x(),site.kind().floor+10,site.z());server.getPlayerList().getPlayers().add(actor);server.getPlayerList().getPlayersByUUID().put(profile.id(),actor);level.addNewPlayer(actor);
        player=actor.getBukkitEntity();player.setGravity(false);player.setInvulnerable(true);player.setGameMode(GameMode.SURVIVAL);
    }
    void finish(Throwable error){
        if(error!=null)getLogger().log(java.util.logging.Level.SEVERE,"HABITAT PROBE",error);
        if(actor!=null){actor.discard();var list=MinecraftServer.getServer().getPlayerList();list.getPlayers().remove(actor);list.getPlayersByUUID().remove(actor.getUUID());}
        try{Files.writeString(Path.of("habitat-result-"+(restart?2:1)+".txt"),(error==null?"PASS":"FAIL "+error)+"\n"+String.join("\n",checks));}catch(Exception ignored){}
        Bukkit.getScheduler().runTaskLater(this,Bukkit::shutdown,3);
    }
}
