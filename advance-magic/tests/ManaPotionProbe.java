import com.example.advancemagic.AdvanceMagicPlugin;
import com.mojang.authlib.GameProfile;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import java.nio.file.*;
import java.util.*;

/** Real Paper host for ManaPotionChecks: one connected survival player on a stone floor. */
public final class ManaPotionProbe extends JavaPlugin {
    static final class TestConnection extends Connection {
        TestConnection(){super(PacketFlow.SERVERBOUND);channel=new EmbeddedChannel();address=new java.net.InetSocketAddress("127.0.0.1",1);}
        @Override public void send(Packet<?> packet){}
        @Override public void send(Packet<?> packet,ChannelFutureListener listener){}
        @Override public void send(Packet<?> packet,ChannelFutureListener listener,boolean flush){}
        @Override public boolean isConnected(){return true;}
    }
    AdvanceMagicPlugin magic;
    World world;
    Player caster;
    ServerPlayer actor;
    int passed;

    @Override public void onEnable(){Bukkit.getScheduler().runTaskLater(this,()->{
        try{begin();finish(null);}catch(Throwable error){finish(error);}
    },20);}
    void check(boolean value,String label){if(!value)throw new AssertionError(label);passed++;getLogger().info("PASS "+label);}
    void begin(){
        magic=(AdvanceMagicPlugin)Bukkit.getPluginManager().getPlugin("advance-magic");
        check(magic!=null&&magic.isEnabled(),"release boots on Paper 26.2");
        world=Bukkit.getWorlds().getFirst();
        for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++)world.getChunkAt(x,z).setForceLoaded(true);
        for(int x=-32;x<=32;x++)for(int z=-32;z<=32;z++)world.getBlockAt(x,99,z).setType(Material.STONE);
        caster=actor("ManaCaster",new Location(world,.5,100,.5,0,0));
        ManaPotionChecks.run(this);
    }
    Player actor(String name,Location at){
        var server=MinecraftServer.getServer();var level=((CraftWorld)world).getHandle();
        GameProfile profile=new GameProfile(UUID.randomUUID(),name);
        ServerPlayer handle=new ServerPlayer(server,level,profile,ClientInformation.createDefault());
        handle.connection=new ServerGamePacketListenerImpl(server,new TestConnection(),handle,CommonListenerCookie.createInitial(profile,false)){
            @Override public boolean hasClientLoaded(){return true;}
        };
        handle.setPos(at.getX(),at.getY(),at.getZ());
        server.getPlayerList().getPlayers().add(handle);server.getPlayerList().getPlayersByUUID().put(profile.id(),handle);level.addNewPlayer(handle);actor=handle;
        Player player=handle.getBukkitEntity();player.setGameMode(GameMode.SURVIVAL);player.setOp(true);player.setGravity(false);player.teleport(at);
        player.getAttribute(Attribute.MAX_HEALTH).setBaseValue(1000);player.setHealth(1000);player.setNoDamageTicks(0);player.setMaximumNoDamageTicks(0);return player;
    }
    void finish(Throwable error){
        if(error!=null)getLogger().log(java.util.logging.Level.SEVERE,"MANA POTION CHECKS FAILED after "+passed,error);
        try{Files.writeString(Path.of("mana-potion-result.txt"),error==null?"PASS "+passed:"FAIL "+error);}catch(Exception ignored){}
        if(actor!=null){MinecraftServer.getServer().getPlayerList().getPlayers().remove(actor);MinecraftServer.getServer().getPlayerList().getPlayersByUUID().remove(actor.getUUID());actor.discard();}
        Bukkit.getScheduler().runTaskLater(this,Bukkit::shutdown,5);
    }
}
