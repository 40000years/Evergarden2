import com.example.voidscape.boss.RealmBosses;
import com.example.voidscape.boss.RealmBossController;
import com.example.voidscape.VoidscapePlugin;
import com.example.voidscape.world.DimensionalSanctuaries;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffectType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;

/** Exercises the shipped helper against actual Paper entities, geometry and block ray tracing. */
public final class NetherAntiAirNativeProbe extends JavaPlugin {
    final List<String> lines = new ArrayList<>();
    int checks;
    World world;
    Player player;
    Location center, high;
    Class<?> helperClass;
    Constructor<?> constructor;
    Method tick, reset;
    final List<Double> damage = new ArrayList<>();

    @Override public void onEnable() {
        Bukkit.getScheduler().runTaskLater(this, () -> {
            try { run(); finish(null); }
            catch (Throwable e) { finish(e); }
        }, 2);
    }
    void check(boolean ok, String what) {
        if (!ok) throw new AssertionError(what);
        checks++; lines.add("PASS: " + what);
    }
    void run() throws Exception {
        world = Bukkit.getWorlds().getFirst();
        for (int x=248; x<=275; x++) for (int z=248; z<=265; z++) {
            world.addPluginChunkTicket(x >> 4,z >> 4,this);
            world.getBlockAt(x,119,z).setType(Material.STONE,false);
            for (int y=120;y<=132;y++) world.getBlockAt(x,y,z).setType(Material.AIR,false);
        }
        center = new Location(world,256.5,120,256.5);
        high = new Location(world,266.5,126,256.5);
        player = fake(high).getBukkitEntity();
        player.setGameMode(GameMode.SURVIVAL);
        helperClass = Class.forName("com.example.voidscape.boss.NetherAntiAir",true,
            Bukkit.getPluginManager().getPlugin("Evergarden").getClass().getClassLoader());
        constructor=helperClass.getDeclaredConstructor(RealmBosses.Kind.class,BiConsumer.class);
        constructor.setAccessible(true);
        tick=helperClass.getDeclaredMethod("tick",List.class,Location.class,Location.class,boolean.class); tick.setAccessible(true);
        reset=helperClass.getDeclaredMethod("reset"); reset.setAccessible(true);
        for (var kind : List.of(RealmBosses.Kind.CINDER_COLOSSUS, RealmBosses.Kind.SOUL_HOUND)) verify(kind);
        verifyController();
    }
    Object helper(RealmBosses.Kind kind) throws Exception {
        return constructor.newInstance(kind,(BiConsumer<Player,Double>)(p,n)->damage.add(n));
    }
    boolean step(Object h, boolean available) throws Exception {
        return (Boolean)tick.invoke(h,List.of(player),center,center,available);
    }
    void reset(Object h) throws Exception { reset.invoke(h); damage.clear(); player.setFireTicks(0); player.removePotionEffect(PotionEffectType.WEAKNESS); }
    void high() { player.teleport(high); }
    void start(Object h, String name) throws Exception {
        for (int i=0; i<13; i++) check(!step(h,true),name+": no premature cast "+i);
        check(step(h,true),name+": starts warning after sustained altitude");
    }
    void finishWarning(Object h, String name) throws Exception {
        for (int i=0;i<29;i++) check(step(h,true),name+": warning holds "+i);
        check(!step(h,true),name+": warning completes");
    }
    void verify(RealmBosses.Kind kind) throws Exception {
        String name=kind.name();
        Object h=helper(kind);
        player.teleport(new Location(world,266.5,122,256.5));
        for (int i=0;i<20;i++) check(!step(h,true),name+": ordinary low jump cannot trigger");
        high(); start(h,name); check(damage.isEmpty(),name+": no damage during warning"); finishWarning(h,name);
        check(damage.size()==1,name+": stationary player is hit once");
        check(damage.getFirst()==(kind==RealmBosses.Kind.CINDER_COLOSSUS?16.5:14.5),name+": expected scaled attack input");
        check(kind==RealmBosses.Kind.CINDER_COLOSSUS ? player.getFireTicks()>=60 : player.hasPotionEffect(PotionEffectType.WEAKNESS),
            name+": distinct fire or weakening consequence");
        for(int i=0;i<80;i++) check(!step(h,true),name+": cooldown prevents repeated casts");
        check(damage.size()==1,name+": cooldown has no repeated damage");

        reset(h); high(); start(h,name); player.teleport(high.clone().add(0,0,4)); finishWarning(h,name);
        check(damage.isEmpty(),name+": moving away from locked location dodges attack");
        reset(h); high(); start(h,name); check(!step(h,false),name+": stun/transition interrupts warning");
        for(int i=0;i<40;i++) step(h,true);
        check(damage.isEmpty(),name+": interrupted warning cannot damage later");

        reset(h); high();
        for(int z=255;z<=257;z++)for(int y=120;y<=130;y++)world.getBlockAt(261,y,z).setType(Material.STONE,false);
        start(h,name); finishWarning(h,name);
        check(damage.isEmpty(),name+": solid wall blocks spell ray");
        for(int z=255;z<=257;z++)for(int y=120;y<=130;y++)world.getBlockAt(261,y,z).setType(Material.AIR,false);
    }
    void verifyController() throws Exception {
        var plugin=(VoidscapePlugin)Bukkit.getPluginManager().getPlugin("Evergarden");
        var site=new DimensionalSanctuaries.Site(DimensionalSanctuaries.Kind.CROWN_FORTRESS,256,120,256,0,0,1);
        var arena=new DimensionalSanctuaries.Arena(256,120,256,20,20);
        var boss=new RealmBossController(plugin,site,arena,center,p->p.getUniqueId().equals(player.getUniqueId()),d->{});
        var action=RealmBossController.class.getDeclaredField("action"); action.setAccessible(true);
        var anti=RealmBossController.class.getDeclaredField("netherAntiAir"); anti.setAccessible(true);
        var locked=helperClass.getDeclaredField("locked"); locked.setAccessible(true);
        var cooldown=RealmBossController.class.getDeclaredField("cooldownTicks"); cooldown.setAccessible(true);
        var health=RealmBossController.class.getDeclaredField("health"); health.setAccessible(true);
        high(); player.setInvulnerable(true);
        boss.spawnWaiting(1); boss.awaken(List.of(player));
        for (int i=1;i<=33;i++) boss.tick(i);
        check(action.get(boss).toString().equals("NONE") && locked.get(anti.get(boss))!=null,
            "real controller starts anti-air after spawn completes");
        cooldown.setInt(boss,0);
        for (int i=34;i<=42;i++) boss.tick(i);
        check(action.get(boss).toString().equals("NONE") && locked.get(anti.get(boss))!=null,
            "real controller holds normal attacks throughout warning");
        boss.triggerStun(20,1.75,null); boss.tick(43);
        check(action.get(boss).toString().equals("STUN") && locked.get(anti.get(boss))==null,
            "real controller cancels warning on stun");
        boss.spawnWaiting(44); boss.awaken(List.of(player));
        for (int i=45;i<=77;i++) boss.tick(i);
        check(locked.get(anti.get(boss))!=null,"real controller can start another warning in a fresh encounter");
        health.setDouble(boss,boss.maxHealth()*0.5); boss.tick(78);
        check(boss.isPhaseTwo() && action.get(boss).toString().equals("TRANSITION") && locked.get(anti.get(boss))==null,
            "real controller cancels warning on phase transition");
        boss.close();
    }
    ServerPlayer fake(Location at) {
        var server=MinecraftServer.getServer(); var level=((CraftWorld)world).getHandle();
        var profile=new GameProfile(UUID.randomUUID(),"AntiAirTester");
        var handle=new ServerPlayer(server,level,profile,ClientInformation.createDefault());
        var connection=new Connection(PacketFlow.SERVERBOUND); connection.channel=new EmbeddedChannel(); connection.address=new InetSocketAddress("127.0.0.1",1);
        handle.connection=new ServerGamePacketListenerImpl(server,connection,handle,CommonListenerCookie.createInitial(profile,false)) {
            @Override public boolean hasClientLoaded() { return true; }
        };
        handle.setPos(at.getX(),at.getY(),at.getZ());
        server.getPlayerList().getPlayers().add(handle); server.getPlayerList().getPlayersByUUID().put(profile.id(),handle); level.addNewPlayer(handle);
        handle.getBukkitEntity().setGravity(false);
        return handle;
    }
    void finish(Throwable error) {
        if(error!=null) { error.printStackTrace(); lines.add("ERROR: "+error); }
        lines.add(0,(error==null?"PASS":"FAIL")+": Nether anti-air Paper probe / "+checks+" checks");
        try { Files.write(Path.of("model-engine-result.txt"),lines); } catch(Exception e) { getLogger().warning(e.toString()); }
        Bukkit.getScheduler().runTaskLater(this,Bukkit::shutdown,5);
    }
}
