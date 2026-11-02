import com.example.voidscape.VoidscapePlugin;
import com.example.voidscape.boss.RealmBossController;
import com.example.voidscape.boss.RealmSanctuaryEncounters;
import com.example.voidscape.world.DimensionalSanctuaries;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.server.network.*;
import org.bukkit.*;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.entity.*;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;
import java.net.InetSocketAddress;
import java.nio.file.*;
import java.lang.reflect.*;
import java.util.*;

/** Paper integration test: a tracked arrow's actual route reaches virtual boss HP once. */
public final class RealmProjectileProbe extends JavaPlugin {
    int checks; boolean done; final List<String> lines = new ArrayList<>();
    void check(boolean ok, String label) { if (!ok) throw new AssertionError(label); checks++; lines.add("PASS: " + label); }
    @Override public void onEnable() { Bukkit.getScheduler().runTaskLater(this, () -> { try { run(); } catch (Throwable e) { finish(e); } }, 100); }
    void run() throws Exception {
        VoidscapePlugin garden = (VoidscapePlugin) Bukkit.getPluginManager().getPlugin("Evergarden");
        World world = Bukkit.getWorlds().getFirst(); Location center = new Location(world, 256.5, 120, 256.5);
        for (int x=245;x<=267;x++) for (int z=245;z<=267;z++) world.getBlockAt(x,119,z).setType(Material.STONE,false);
        var server=MinecraftServer.getServer(); var level=((CraftWorld)world).getHandle(); var profile=new GameProfile(UUID.randomUUID(),"ArrowProbe");
        var actor=new ServerPlayer(server,level,profile,ClientInformation.createDefault()); var connection=new Connection(PacketFlow.SERVERBOUND);
        connection.channel=new EmbeddedChannel(); connection.address=new InetSocketAddress("127.0.0.1",1);
        actor.connection=new ServerGamePacketListenerImpl(server,connection,actor,CommonListenerCookie.createInitial(profile,false)){@Override public boolean hasClientLoaded(){return true;}};
        actor.setPos(256.5,120,250.5); server.getPlayerList().getPlayers().add(actor); server.getPlayerList().getPlayersByUUID().put(profile.id(),actor); level.addNewPlayer(actor);
        Player player=actor.getBukkitEntity(); player.setGameMode(GameMode.SURVIVAL); player.setGravity(false);
        var site=new DimensionalSanctuaries.Site(DimensionalSanctuaries.Kind.CROWN_FORTRESS,256,120,256,0,0,1);
        var arena=new DimensionalSanctuaries.Arena(256,120,256,30,20);
        var boss=new RealmBossController(garden,site,arena,center,p->true,d->{}); boss.spawnWaiting(1);
        RealmSanctuaryEncounters manager=garden.realmSanctuaryEncounters();
        Class<?> runType=Class.forName(RealmSanctuaryEncounters.class.getName()+"$Run");
        Constructor<?> runCtor=runType.getDeclaredConstructor(World.class,DimensionalSanctuaries.Site.class,DimensionalSanctuaries.Arena.class,Location.class); runCtor.setAccessible(true);
        Object run=runCtor.newInstance(world,site,arena,center); Field controller=runType.getDeclaredField("controller"); controller.setAccessible(true); controller.set(run,boss);
        Field runsField=RealmSanctuaryEncounters.class.getDeclaredField("runs"); runsField.setAccessible(true);
        @SuppressWarnings("unchecked") Map<String,Object> runs=(Map<String,Object>)runsField.get(manager); runs.put("probe",run);
        Class<?> shotType=Class.forName(RealmSanctuaryEncounters.class.getName()+"$TrackedShot");
        Constructor<?> shotCtor=shotType.getDeclaredConstructor(AbstractArrow.class,Location.class); shotCtor.setAccessible(true);
        Method hit=RealmSanctuaryEncounters.class.getDeclaredMethod("hitShot",shotType,Location.class); hit.setAccessible(true);
        Location start=new Location(world,256.5,121.5,252); Vector velocity=new Vector(0,0,3);
        check(Double.isFinite(boss.projectileRayDistance(start,velocity,3)),"segment crossing boss body intersects");
        check(!Double.isFinite(boss.projectileRayDistance(start,new Vector(1,0,0),3)),"segment aimed away misses boss");
        check(!Double.isFinite(boss.projectileRayDistance(start,velocity,.3)),"short segment stops before boss");
        world.getBlockAt(256,121,253).setType(Material.STONE,false);
        check(!Double.isFinite(boss.projectileRayDistance(start,velocity,3)),"solid wall before boss blocks collision");
        world.getBlockAt(256,121,253).setType(Material.AIR,false);
        Cow cow=world.spawn(new Location(world,256.5,120.5,253),Cow.class,c->c.setGravity(false));
        Arrow blocked=world.spawn(start,Arrow.class,a->{a.setShooter(player);a.setVelocity(velocity);a.setDamage(2);});
        double before=boss.health();
        double bodyDistance=boss.projectileRayDistance(start,velocity,3);
        var cowTrace=world.rayTraceEntities(start,velocity.clone().normalize(),bodyDistance,.1,e->e==cow);
        boolean cowAccepted=(boolean)hit.invoke(manager,shotCtor.newInstance(blocked,start),start.clone().add(velocity));
        check(!cowAccepted&&boss.health()==before,
            "a living entity before the boss blocks the synthetic arrow hit (bodyDistance="+bodyDistance+", cow="+cowTrace+", collidable="+cow.isCollidable()+")");
        blocked.remove();cow.remove();
        Field shotsField=RealmSanctuaryEncounters.class.getDeclaredField("shots"); shotsField.setAccessible(true);
        @SuppressWarnings("unchecked") Map<UUID,Object> shots=(Map<UUID,Object>)shotsField.get(manager);
        Trident trident=world.spawn(start,Trident.class,a->{a.setShooter(player);a.setVelocity(velocity);});
        manager.onProjectileLaunch(new ProjectileLaunchEvent(trident));
        check(!shots.containsKey(trident.getUniqueId()),"loyalty trident retains its native projectile path");
        trident.remove();
        Arrow piercing=world.spawn(start,Arrow.class,a->{a.setShooter(player);a.setPierceLevel(1);a.setVelocity(velocity);});
        manager.onProjectileLaunch(new ProjectileLaunchEvent(piercing));
        check(!shots.containsKey(piercing.getUniqueId()),"piercing arrow retains its native projectile path");
        piercing.remove();
        Arrow arrow=world.spawn(start,Arrow.class,a->{a.setShooter(player);a.setVelocity(velocity);a.setDamage(2);});
        manager.onProjectileLaunch(new ProjectileLaunchEvent(arrow));
        check(shots.containsKey(arrow.getUniqueId()),"ordinary player arrow is tracked on launch");
        NamespacedKey ringShot=garden.key("realm_ring_shot");
        check(Byte.valueOf((byte)1).equals(arrow.getPersistentDataContainer().get(ringShot,PersistentDataType.BYTE)),"an arrow loosed inside the ring is stamped inside");
        before=boss.health(); Method tickShots=RealmSanctuaryEncounters.class.getDeclaredMethod("tickShots");tickShots.setAccessible(true);tickShots.invoke(manager);
        check(boss.health()<before,"arrow routes projectile damage to encounter virtual HP on its next segment");
        check(!arrow.isValid(),"non-piercing arrow is consumed on accepted model hit");
        // The sanctuary ward (R=30): nothing loosed or swung from outside the drawn ring counts, though presence reaches 31.5
        Field wardedField=RealmSanctuaryEncounters.class.getDeclaredField("warded"); wardedField.setAccessible(true);
        @SuppressWarnings("unchecked") Map<UUID,Object> warded=(Map<UUID,Object>)wardedField.get(manager);
        player.teleport(new Location(world,256.5,120,287.5));
        Arrow far=world.spawn(new Location(world,256.5,121.5,286),Arrow.class,a->{a.setShooter(player);a.setVelocity(new Vector(0,0,-3));a.setDamage(2);});
        manager.onProjectileLaunch(new ProjectileLaunchEvent(far));
        check(Byte.valueOf((byte)0).equals(far.getPersistentDataContainer().get(ringShot,PersistentDataType.BYTE))&&!shots.containsKey(far.getUniqueId())&&warded.containsKey(far.getUniqueId()),
            "an arrow loosed just outside the ring is stamped outside and handed to the ward");
        before=boss.health();
        boss.carrier().damage(4,DamageSource.builder(DamageType.ARROW).withDirectEntity(far).withCausingEntity(player).build());
        check(boss.health()==before,"its hit is refused although the archer still counts as present");
        far.teleport(new Location(world,256.5,121.5,270));
        Method tickWarded=RealmSanctuaryEncounters.class.getDeclaredMethod("tickWarded");tickWarded.setAccessible(true);tickWarded.invoke(manager);
        check(!far.isValid()&&!warded.containsKey(far.getUniqueId()),"the ward dissolves the outside arrow where it crosses the ring");
        before=boss.health(); boss.carrier().damage(4,player);
        check(boss.health()==before,"a melee or spell hit from outside the ring is refused");
        player.teleport(new Location(world,256.5,120,250.5));
        Arrow loosed=world.spawn(start,Arrow.class,a->{a.setShooter(player);a.setVelocity(velocity);a.setDamage(2);});
        manager.onProjectileLaunch(new ProjectileLaunchEvent(loosed));
        player.teleport(new Location(world,256.5,120,287.5));
        before=boss.health();
        boss.carrier().damage(4,DamageSource.builder(DamageType.ARROW).withDirectEntity(loosed).withCausingEntity(player).build());
        check(boss.health()<before,"an arrow loosed inside the ring still lands after the archer steps back to the edge");
        loosed.remove();
        player.teleport(new Location(world,256.5,120,250.5));
        before=boss.health(); boss.carrier().damage(4,player);
        check(boss.health()<before,"the same scripted hit from inside the ring lands");
        runs.remove("probe"); boss.close(); finish(null);
    }
    void finish(Throwable e) { if(done)return; done=true; if(e!=null){e.printStackTrace();lines.add("ERROR: "+e);} lines.add(0,(e==null?"PASS":"FAIL")+": realm projectile / "+checks+" checks");try{Files.write(Path.of("model-engine-result.txt"),lines);}catch(Exception ignored){}Bukkit.getScheduler().runTaskLater(this,Bukkit::shutdown,5); }
}
