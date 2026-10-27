import com.example.voidscape.VoidscapePlugin;
import com.example.voidscape.world.SanctuaryArena;
import com.example.voidscape.world.OverworldSanctuaries;
import com.example.voidscape.world.BossStructureLayout;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.server.network.*;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.damage.*;
import org.bukkit.entity.*;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.util.Vector;
import java.net.InetSocketAddress;
import java.util.*;

/** Isolated real sanctuary event routing for the shared encounter regression suites. */
final class SanctuaryProbeFixture {
    private static Object call(Object target,String name,Class<?>[] types,Object... args)throws Exception{
        var method=target.getClass().getDeclaredMethod(name,types);method.setAccessible(true);return method.invoke(target,args);
    }
    @SuppressWarnings({"unchecked","rawtypes"})
    static void open(BossContactProbe probe,OverworldSanctuaries.Kind kind)throws Exception{
        probe.garden=(VoidscapePlugin)Bukkit.getPluginManager().getPlugin("Evergarden");
        probe.check(probe.garden!=null&&probe.garden.isEnabled(),"release boots for sanctuary encounter mechanics");
        probe.world=Bukkit.getWorlds().stream().filter(w->w.getEnvironment()==World.Environment.NORMAL&&probe.garden.overworldSanctuaries().placement(w)!=null).findFirst().orElseThrow();
        probe.world.setDifficulty(Difficulty.NORMAL);Object manager=probe.garden.sanctuaryEncounters();
        probe.check(manager!=null,"mechanics fixture uses the actual sanctuary encounter service");
        SanctuaryArena arena=new SanctuaryArena(new OverworldSanctuaries.Site(kind,0,0,0),probe.world);probe.pad=arena.center();
        probe.site=new BossStructureLayout.Site(BossStructureLayout.Kind.ARKHAIA,0,0);
        for(int x=-36;x<=36;x++)for(int z=-36;z<=36;z++){
            probe.world.getBlockAt(probe.pad.getBlockX()+x,probe.pad.getBlockY()-1,probe.pad.getBlockZ()+z).setType(Material.STONE,false);
            for(int y=0;y<=14;y++)probe.world.getBlockAt(probe.pad.getBlockX()+x,probe.pad.getBlockY()+y,probe.pad.getBlockZ()+z).setType(Material.AIR,false);
        }
        Class<?> runType=Arrays.stream(manager.getClass().getDeclaredClasses()).filter(c->c.getSimpleName().equals("Run")).findFirst().orElseThrow();
        var constructor=runType.getDeclaredConstructor(SanctuaryArena.class);constructor.setAccessible(true);Object run=constructor.newInstance(arena);
        String id=(String)call(manager,"id",new Class<?>[]{SanctuaryArena.class},arena);Map runs=(Map)probe.get(manager,"runs");
        Object previous=runs.put(id,run);if(previous!=null)probe.invoke(probe.get(previous,"minions"),"close",new Class<?>[]{});
        probe.invoke(manager,"spawn",new Class<?>[]{runType},run);probe.controller=probe.get(run,"minions");
        probe.put(run,"fighting",true);probe.invoke(probe.controller,"awaken",new Class<?>[]{});probe.now=(long)probe.get(probe.controller,"clock");
        var server=MinecraftServer.getServer();var level=((CraftWorld)probe.world).getHandle();var profile=new GameProfile(UUID.randomUUID(),"SanctuaryChecks");
        probe.actor=new ServerPlayer(server,level,profile,ClientInformation.createDefault());var connection=new Connection(PacketFlow.SERVERBOUND);
        connection.channel=new EmbeddedChannel();connection.address=new InetSocketAddress("127.0.0.1",1);
        probe.actor.connection=new ServerGamePacketListenerImpl(server,connection,probe.actor,CommonListenerCookie.createInitial(profile,false)){
            @Override public boolean hasClientLoaded(){return true;}
        };
        probe.actor.setPos(probe.pad.getX(),probe.pad.getY(),probe.pad.getZ()+5);server.getPlayerList().getPlayers().add(probe.actor);
        server.getPlayerList().getPlayersByUUID().put(profile.id(),probe.actor);level.addNewPlayer(probe.actor);probe.player=probe.actor.getBukkitEntity();
        probe.player.setGravity(false);probe.player.setGameMode(GameMode.SURVIVAL);probe.player.setInvulnerable(false);probe.actor.getAbilities().invulnerable=false;
        probe.player.getAttribute(Attribute.MAX_HEALTH).setBaseValue(200);probe.player.setHealth(200);
        probe.check((boolean)call(probe.controller,"accepts",new Class<?>[]{Player.class},probe.player),"real sanctuary predicate accepts the mechanics actor");
    }
    static void wound(BossContactProbe probe,Mob victim,double amount){
        Arrow arrow=probe.world.spawnArrow(probe.player.getEyeLocation(),new Vector(0,0,1),1,0);arrow.setShooter(probe.player);
        Bukkit.getPluginManager().callEvent(new ProjectileLaunchEvent(arrow));
        try{victim.damage(amount,DamageSource.builder(DamageType.INDIRECT_MAGIC).withCausingEntity(probe.player).withDirectEntity(arrow).build());}
        finally{arrow.remove();}
    }
}
