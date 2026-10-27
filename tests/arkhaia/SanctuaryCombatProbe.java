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
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.damage.*;
import org.bukkit.entity.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import java.net.InetSocketAddress;
import java.util.*;

/** Six actual generated sites, idle activation, full boundaries and terrain exploits. */
public class SanctuaryCombatProbe extends ArkhaiaProbe {
    Object manager;OverworldSanctuaries service;final Map<Kind,OverworldSanctuaries.Site> sites=new EnumMap<>(Kind.class);
    Object call(Object o,String n,Class<?>[] t,Object... a)throws Exception{var m=o.getClass().getDeclaredMethod(n,t);m.setAccessible(true);return m.invoke(o,a);}
    Map<?,?> runs()throws Exception{return (Map<?,?>)get(manager,"runs");}
    Object run(Kind k)throws Exception{for(Object r:runs().values())if(((SanctuaryArena)get(r,"arena")).site().equals(sites.get(k)))return r;throw new AssertionError("Missing waiting boss "+k);}
    Object controller(Object r)throws Exception{return get(r,"minions");}
    Object unit(Object r)throws Exception{return ((Map<?,?>)get(controller(r),"spirits")).values().iterator().next();}
    Mob mob(Object r)throws Exception{return (Mob)get(unit(r),"mob");}
    double health(Object r)throws Exception{return (double)get(unit(r),"health");}
    void arenaTicks(int n)throws Exception{for(int i=0;i<n;i++){player.setNoDamageTicks(0);call(manager,"tick",new Class<?>[]{});}}
    SanctuaryArena arena(Kind k){return new SanctuaryArena(sites.get(k),world);}
    Arrow arrow(Location at){Arrow a=world.spawnArrow(at,new org.bukkit.util.Vector(0,0,1),1,0);a.setShooter(player);Bukkit.getPluginManager().callEvent(new ProjectileLaunchEvent(a));return a;}
    void shoot(Object r,double damage){try{Arrow a=arrow(player.getEyeLocation());mob(r).damage(damage,DamageSource.builder(DamageType.ARROW).withCausingEntity(player).withDirectEntity(a).build());a.remove();}catch(Exception e){throw new RuntimeException(e);}}
    @Override void run()throws Exception{
        garden=(VoidscapePlugin)Bukkit.getPluginManager().getPlugin("Evergarden");service=garden.overworldSanctuaries();manager=garden.sanctuaryEncounters();world=Bukkit.getWorlds().getFirst();world.setDifficulty(Difficulty.NORMAL);
        check(manager!=null,"release owns sanctuary encounter service");var placement=service.placement(world);check(placement!=null,"vanilla Overworld has a saved sanctuary layout");
        List<java.util.concurrent.CompletableFuture<?>> pending=new ArrayList<>();
        for(Kind k:Kind.values()){
            var s=placement.nearest(k,0,0,128,r->{},()->false);check(s!=null,"natural structure found "+k);sites.put(k,s);Location center=arena(k).center();
            for(int x=(center.getBlockX()-4)>>4;x<=(center.getBlockX()+4)>>4;x++)for(int z=(center.getBlockZ()-4)>>4;z<=(center.getBlockZ()+4)>>4;z++){
                int cx=x,cz=z;pending.add(world.getChunkAtAsync(cx,cz,true).thenAccept(chunk->Bukkit.getScheduler().runTask(this,()->chunk.addPluginChunkTicket(this))));
            }
        }
        java.util.concurrent.CompletableFuture.allOf(pending.toArray(java.util.concurrent.CompletableFuture[]::new)).whenComplete((v,error)->Bukkit.getScheduler().runTaskLater(this,()->{
            try{if(error!=null)throw new RuntimeException(error);inspect();finish(null);}catch(Throwable t){finish(t);}
        },240));
    }
    void createActor(){
        var server=MinecraftServer.getServer();var level=((CraftWorld)world).getHandle();var profile=new GameProfile(UUID.randomUUID(),"SanctuaryTester");
        actor=new ServerPlayer(server,level,profile,ClientInformation.createDefault());var connection=new Connection(PacketFlow.SERVERBOUND);connection.channel=new EmbeddedChannel();connection.address=new InetSocketAddress("127.0.0.1",1);
        actor.connection=new ServerGamePacketListenerImpl(server,connection,actor,CommonListenerCookie.createInitial(profile,false)){@Override public boolean hasClientLoaded(){return true;}};
        Location c=arena(Kind.ACHERON).center();actor.setPos(c.getX(),c.getY(),c.getZ()+5);server.getPlayerList().getPlayers().add(actor);server.getPlayerList().getPlayersByUUID().put(profile.id(),actor);level.addNewPlayer(actor);
        player=actor.getBukkitEntity();player.setGravity(false);player.setGameMode(GameMode.SURVIVAL);player.setInvulnerable(false);actor.getAbilities().invulnerable=false;player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).setBaseValue(200);player.setHealth(200);
    }
    void inspect()throws Exception{
        for(Kind k:Kind.values()){
            Location c=arena(k).center();call(manager,"discover",new Class<?>[]{Chunk.class},c.getChunk());Object r=run(k);
            check(!(boolean)get(r,"fighting")&&mob(r).isValid(),"one waiting boss stands in generated "+k+" arena");
            check(mob(r).getLocation().distanceSquared(c)<.01,"boss is on actual combat floor "+k);
            check(!(boolean)get(controller(r),"closed")&&!mob(r).hasAI(),"waiting boss cannot chase or attack "+k);
            for(int i=0;i<3;i++)call(manager,"discover",new Class<?>[]{Chunk.class},c.getChunk());
            long count=runs().values().stream().filter(other->{try{return ((SanctuaryArena)get(other,"arena")).site().equals(sites.get(k));}catch(Exception e){throw new RuntimeException(e);}}).count();check(count==1,"repeat chunk discovery cannot duplicate "+k);
            var a=arena(k);check(a.contains(c.clone().add(34,0,0))&&!a.contains(c.clone().add(34.01,0,0)),"exact radial encounter boundary "+k);
            check(!a.contains(c.clone().add(0,-1,0))&&a.contains(c.clone().add(0,k==Kind.COCYTUS?13:4,0))&&!a.containsActivation(c.clone().add(0,k==Kind.COCYTUS?13:4,0)),"active airborne membership preserves ground-only dormant activation boundary "+k);
            check(garden.structureProtection().protects(c.clone().subtract(0,1,0).getBlock()),"arena floor protected in Overworld "+k);
            check(garden.structureProtection().protects(c.clone().add(0,12,0).getBlock()),"empty headroom protected against builds "+k);
        }
        createActor();new SkillRewardChecks(this).inspect();new ArmorSanctuaryChecks(this).inspect();Object r=run(Kind.ACHERON);Location c=arena(Kind.ACHERON).center();Mob original=mob(r);double max=health(r);
        arenaTicks(130);check(player.getHealth()==200&&!(boolean)get(r,"fighting"),"standing next to waiting boss remains harmless indefinitely");
        original.damage(100);check(health(r)==max&&!(boolean)get(r,"fighting"),"environmental damage cannot wake or chip waiting boss");
        player.teleport(c.clone().add(35,0,0));shoot(r,100);check(health(r)==max&&!(boolean)get(r,"fighting"),"outside archer cannot start fight");
        Arrow outside=arrow(player.getEyeLocation());player.teleport(c.clone().add(0,0,8));original.damage(100,DamageSource.builder(DamageType.ARROW).withCausingEntity(player).withDirectEntity(outside).build());outside.remove();
        check(health(r)==max&&!(boolean)get(r,"fighting"),"outside-launched arrow stays invalid after shooter enters");
        player.teleport(c.clone().add(0,4,8));shoot(r,100);check(health(r)==max&&!(boolean)get(r,"fighting"),"elevated platform cannot attack boss");
        player.teleport(c.clone().add(0,0,8));shoot(r,50);double wounded=health(r);check((boolean)get(r,"fighting")&&wounded<max&&wounded>=max-50,"valid first arrow wakes boss and applies its native mitigated damage once; hp="+wounded+" fighting="+get(r,"fighting"));
        check(!((Set<?>)get(r,"tickets")).isEmpty(),"active encounter holds its own loaded arena chunks");
        player.teleport(c.clone().add(0,-1,8));shoot(r,100);check(health(r)==wounded,"trench damage is rejected during active fight");
        player.teleport(c.clone().add(35,0,0));arenaTicks(299);check((boolean)get(r,"fighting"),"leaving provides a 15-second return grace");arenaTicks(2);
        check(!(boolean)get(r,"fighting")&&health(r)==max&&mob(r).isValid(),"empty arena resets health and returns waiting sculpture after fifteen seconds");
        check(((Set<?>)get(r,"tickets")).isEmpty(),"reset releases every fight chunk ticket");
        player.teleport(c.clone().add(0,0,8));shoot(r,50);original=mob(r);original.teleport(c.clone().add(0,-5,0));arenaTicks(1);
        check(Math.abs(mob(r).getY()-c.getY())<.5,"pit displacement recovers boss onto arena floor");
        double beforeLava=health(r);original.damage(500,DamageSource.builder(DamageType.LAVA).build());check(health(r)==beforeLava,"lava cannot deal free environmental boss damage");
        player.teleport(c.clone().add(0,0,8));arenaTicks(11);
        var tree=((com.example.advancemagic.AdvanceMagicPlugin)Bukkit.getPluginManager().getPlugin("advance-magic")).skillTree();
        int rewardBefore=tree.points(player);shoot(r,5000);long deadline=(long)get(r,"readyAt");arenaTicks(1);
        check(!(boolean)get(r,"fighting")&&(boolean)get(r,"dead")&&(boolean)get(controller(r),"closed"),"defeat leaves an empty arena and closes all combat hazards");
        check(deadline==world.getGameTime()+24000,"defeat persists exactly one game day: 24000 ticks");
        check(tree.points(player)==rewardBefore+1,"real sanctuary victory grants one direct skill point");
        check(world.getNearbyEntities(c,3,3,3).stream().anyMatch(e->e instanceof Item i&&"acheron".equals(i.getItemStack().getItemMeta().getPersistentDataContainer().get(garden.key("boss_orb"),PersistentDataType.STRING))),"victory drops the actual crimson Acheron soul orb");
        arenaTicks(100);check((boolean)get(r,"dead")&&tree.points(player)==rewardBefore+1,"cooldown polling cannot duplicate victory rewards");
        ((CraftWorld)world).getHandle().serverLevelData.setGameTime(deadline-1);arenaTicks(1);check(world.getGameTime()==deadline-1&&(boolean)get(r,"dead"),"no sculpture appears one tick before the full game day expires");
        ((CraftWorld)world).getHandle().serverLevelData.setGameTime(deadline);arenaTicks(1);
        check(!(boolean)get(r,"dead")&&!((boolean)get(r,"fighting"))&&mob(r).isValid()&&health(r)==max,"one full game day returns a fresh waiting boss");
        // All mutation paths share the exact same protected footprint.
        Block floor=c.clone().subtract(0,1,0).getBlock();Material material=floor.getType();
        BlockBreakEvent broken=new BlockBreakEvent(floor,player);Bukkit.getPluginManager().callEvent(broken);check(broken.isCancelled()&&floor.getType()==material,"mining cannot dig a boss pit");
        BlockPlaceEvent placed=new BlockPlaceEvent(c.getBlock(),c.getBlock().getState(),floor,new ItemStack(Material.COBBLESTONE),player,true,org.bukkit.inventory.EquipmentSlot.HAND);Bukkit.getPluginManager().callEvent(placed);check(placed.isCancelled(),"placing pillars and traps is blocked");
        TNTPrimed tnt=world.spawn(c,TNTPrimed.class,e->e.setFuseTicks(1000));
        var explosion=new EntityExplodeEvent(tnt,c,new ArrayList<>(List.of(floor)),1,org.bukkit.ExplosionResult.DESTROY);Bukkit.getPluginManager().callEvent(explosion);check(explosion.blockList().isEmpty(),"explosions cannot excavate arena");tnt.remove();
        var piston=new BlockPistonExtendEvent(c.clone().add(40,0,0).getBlock(),List.of(floor),BlockFace.EAST);Bukkit.getPluginManager().callEvent(piston);check(piston.isCancelled(),"external piston cannot move arena blocks");
        var flow=new BlockFromToEvent(c.clone().add(0,1,0).getBlock(),c.getBlock());Bukkit.getPluginManager().callEvent(flow);check(flow.isCancelled(),"water and lava flow cannot enter arena");
        var change=new EntityChangeBlockEvent(mob(r),floor,Material.AIR.createBlockData());Bukkit.getPluginManager().callEvent(change);check(change.isCancelled(),"entity and falling-block terrain changes are blocked");
        Vehicle cart=(Vehicle)world.spawnEntity(c,EntityType.MINECART);
        var collision=new org.bukkit.event.vehicle.VehicleEntityCollisionEvent(cart,mob(r));Bukkit.getPluginManager().callEvent(collision);check(collision.isCancelled(),"vehicle pushing cannot trap the boss without disabling arrow collision");
        var mounting=new EntityMountEvent(mob(r),cart);Bukkit.getPluginManager().callEvent(mounting);check(mounting.isCancelled(),"boss cannot be captured in a vehicle");cart.remove();
        var cow=new CreatureSpawnEvent(world.createEntity(c,Cow.class),CreatureSpawnEvent.SpawnReason.NATURAL);Bukkit.getPluginManager().callEvent(cow);check(cow.isCancelled(),"ordinary natural animals do not spawn on protected site");
        for(var reason:List.of(CreatureSpawnEvent.SpawnReason.NATURAL,CreatureSpawnEvent.SpawnReason.SPAWNER,CreatureSpawnEvent.SpawnReason.SPAWNER_EGG,CreatureSpawnEvent.SpawnReason.CUSTOM)){
            var spawn=new CreatureSpawnEvent(world.createEntity(c,Zombie.class),reason);Bukkit.getPluginManager().callEvent(spawn);check(spawn.isCancelled(),"unowned mob spawn rejected: "+reason);
        }
        Zombie guard=world.createEntity(c,Zombie.class);guard.getPersistentDataContainer().set(garden.key("sanctuary_guard"),PersistentDataType.BYTE,(byte)1);
        var cage=new CreatureSpawnEvent(guard,CreatureSpawnEvent.SpawnReason.SPAWNER);Bukkit.getPluginManager().callEvent(cage);check(!cage.isCancelled(),"authored cage guard is permitted");
        Location outsideSite=new Location(world,sites.get(Kind.ACHERON).x()+114,c.getY(),sites.get(Kind.ACHERON).z());var spawnOutside=new CreatureSpawnEvent(world.createEntity(outsideSite,Zombie.class),CreatureSpawnEvent.SpawnReason.NATURAL);Bukkit.getPluginManager().callEvent(spawnOutside);check(!spawnOutside.isCancelled(),"ordinary spawning outside site is preserved");
        // Target attacks cannot be redirected towards spectators or nonplayers.
        var target=new EntityTargetLivingEntityEvent(mob(r),guard,org.bukkit.event.entity.EntityTargetEvent.TargetReason.CLOSEST_ENTITY);Bukkit.getPluginManager().callEvent(target);check(target.isCancelled(),"waiting boss never acquires ordinary mobs as targets");
        call(manager,"reset",new Class<?>[]{r.getClass()},r);player.teleport(c.clone().add(0,0,8));shoot(r,50);
        Object previousController=controller(r);call(manager,"reset",new Class<?>[]{r.getClass()},r);check((boolean)get(previousController,"closed")&&health(r)==max,"reset closes old controller rather than retaining hazards");
        // Returning within the 15-second countdown resumes the fight without resetting.
        player.teleport(c.clone().add(0,0,8));shoot(r,20);check((boolean)get(r,"fighting"),"fresh attack can restart a reset encounter");
        player.teleport(c.clone().add(35,0,0));arenaTicks(1);player.teleport(c.clone().add(0,0,8));arenaTicks(1);shoot(r,20);
        check((boolean)get(r,"fighting")&&(long)get(r,"emptySince")<0&&health(r)<max,"re-entering within 15s cancels empty countdown and continues the fight");
        call(manager,"reset",new Class<?>[]{r.getClass()},r);
        for(Kind kind:Kind.values()){
            Object other=run(kind);put(other,"readyAt",0L);Location at=arena(kind).center();double foot=kind==Kind.BANSHEE?.95:.7;player.teleport(at.clone().add(foot,0,2.5));
            Location look=player.getLocation();look.setDirection(at.clone().add(foot,1,kind==Kind.BANSHEE?.55:0).toVector().subtract(player.getEyeLocation().toVector()));player.teleport(look);
            mob(other).damage(10,player);
            // The redesigned Banshee has raised hands and a floating torso. Sample
            // its silhouette from ground level; production still enforces normal reach.
            double[] zs=kind==Kind.BANSHEE?new double[]{2,1.5,1,0,-1,-1.5,-2}:new double[]{2,1.5};
            double[] xs=kind==Kind.BANSHEE?new double[]{-2,-1.5,-.95,0,.95,1.5,2}:new double[]{-.95,.95};
            double[] ys=kind==Kind.BANSHEE?new double[]{3,3.4,3.8,4.2,4.6,5}:new double[]{.7,1.2,1.8,2.4};
            if(!(boolean)get(other,"fighting"))for(double z:zs)for(double x:xs)for(double y:ys){
                if((boolean)get(other,"fighting"))break;
                Location from=at.clone().add(x,0,z);from.setDirection(at.clone().add(x,y,0).toVector().subtract(from.toVector().add(new org.bukkit.util.Vector(0,player.getEyeHeight(),0))));player.teleport(from);mob(other).damage(10,player);
            }
            check((boolean)get(other,"fighting"),"ordinary aimed melee starts sanctuary fight "+kind);
            if(kind==Kind.BANSHEE){Object soul=get(unit(other),"soul");check(((List<?>)get(soul,"anchors")).size()==3,"Banshee creates its soul anchors only on awakening");}
            call(manager,"reset",new Class<?>[]{other.getClass()},other);
        }
        rewardVictories(tree);
        garden.sanctuaryEncounters().close();check(runs().isEmpty(),"service shutdown removes all six encounters and waiting sculptures");
        manager=new com.example.voidscape.boss.SanctuaryEncounters(garden);call(manager,"discover",new Class<?>[]{Chunk.class},c.getChunk());
        Object restored=run(Kind.ACHERON);check((long)get(restored,"readyAt")==world.getGameTime()+24000&&(boolean)get(restored,"dead")&&(boolean)get(controller(restored),"closed"),"fresh service restores the game-day cooldown without spawning a waiting sculpture");
        ((com.example.voidscape.boss.SanctuaryEncounters)manager).close();
    }
    void rewardVictories(com.example.advancemagic.elemental.SkillTreeService tree)throws Exception{
        player.setInvulnerable(true);actor.getAbilities().invulnerable=true;
        for(Kind kind:Kind.values()){
            Object r=run(kind);Location c=arena(kind).center();player.teleport(c.clone().add(0,0,8));shoot(r,20);
            if(kind==Kind.BANSHEE){
                for(Object anchor:new ArrayList<>((List<?>)get(get(unit(r),"soul"),"anchors"))){Arrow shot=arrow(player.getEyeLocation());((LivingEntity)get(anchor,"receiver")).damage(1000,DamageSource.builder(DamageType.ARROW).withCausingEntity(player).withDirectEntity(shot).build());shot.remove();}
                check(((List<?>)get(get(unit(r),"soul"),"anchors")).isEmpty(),"reward test destroys all three actual Banshee anchors");
            }
            int before=tree.points(player);long items=orbs(c);Object unit=unit(r);put(unit,"health",1.0);((Map<?,?>)get(unit,"hits")).clear();
            if(kind==Kind.COCYTUS)((com.example.advancemagic.AdvanceMagicPlugin)Bukkit.getPluginManager().getPlugin("advance-magic")).context().damage(player,mob(r),10000,DamageType.MAGIC);
            else shoot(r,10000);
            if(kind==Kind.MALEFEASANCE){
                check(!(boolean)get(r,"dead")&&tree.points(player)==before&&orbs(c)==items,"Malefeasance's first death grants neither orb nor skill point");
                put(unit,"revivalUntil",0L);((Map<?,?>)get(unit,"hits")).clear();shoot(r,10000);
            }
            check((boolean)get(r,"dead")&&tree.points(player)==before+1&&orbs(c)==items+1,"one real final defeat gives exactly one orb and point: "+kind);
            String id=kind.id;
            Item orb=(Item)world.getNearbyEntities(c,3,3,3).stream().filter(e->e instanceof Item i&&id.equals(i.getItemStack().getItemMeta().getPersistentDataContainer().get(garden.key("boss_orb"),PersistentDataType.STRING))).findFirst().orElseThrow();
            check(orb.getItemStack().getItemMeta().getItemModel().equals(new NamespacedKey("voidscape","boss_orb_"+id)),"dropped trophy routes to its dedicated Java/Bedrock texture: "+kind);
            arenaTicks(1);check((boolean)get(controller(r),"closed")&&((Set<?>)get(r,"tickets")).isEmpty(),"defeat clears hazards and releases chunk tickets: "+kind);
        }
    }
    long orbs(Location c){return world.getNearbyEntities(c,3,3,3).stream().filter(e->e instanceof Item i&&i.getItemStack().getItemMeta().getPersistentDataContainer().has(garden.key("boss_orb"))).count();}
}
