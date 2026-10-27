import com.example.voidscape.VoidscapePlugin;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.BundlePacket;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent.Status;
import org.bukkit.plugin.java.JavaPlugin;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/**
 * One load with real Paper players. A player who joins while ModelEngine is still importing, and while the
 * public copies are still being checked, waits, then gets Magic, Evergarden and Judge in one batch, each from
 * the public copy (run.py --pack-mirror). A broken batch (Judge FAILED_DOWNLOAD, the others DISCARDED) is
 * recovered one pack at a time, Judge from this server's own address. With --pack-unreachable that address
 * goes nowhere, as behind a tunnel that carries only Minecraft: everything still loads from the copies, and
 * only a second Judge failure, with no address left, disconnects.
 */
public final class PackOrderProbe extends JavaPlugin implements Listener {
    static final UUID MAGIC=UUID.fromString("3e8e5b71-0600-4a42-a678-483a7cce5fb0");
    static final UUID GARDEN=UUID.fromString("c8f2b94e-4a35-4d1b-9b67-0d2a6ef4f821");
    static final UUID JUDGE=UUID.fromString("b198c85c-c302-4c62-a365-a358fa544509");
    VoidscapePlugin garden;
    JavaPlugin magic;
    final Map<ServerPlayer,List<ClientboundResourcePackPushPacket>> pushes=new HashMap<>();
    final Map<ServerPlayer,List<Integer>> ticks=new HashMap<>();
    final Map<Player,Integer> kicks=new HashMap<>();
    int checks;
    @Override public void onEnable(){Bukkit.getPluginManager().registerEvents(this,this);later(this::start,40);}
    void later(Runnable r,long delay){Bukkit.getScheduler().runTaskLater(this,()->{try{r.run();}catch(Throwable e){finish(e);}},delay);}
    @EventHandler public void kick(PlayerKickEvent e){kicks.merge(e.getPlayer(),1,Integer::sum);e.setCancelled(true);}
    ServerPlayer join(String name){
        var server=MinecraftServer.getServer();var level=((CraftWorld)Bukkit.getWorlds().getFirst()).getHandle();
        var profile=new GameProfile(UUID.randomUUID(),name);
        var actor=new ServerPlayer(server,level,profile,ClientInformation.createDefault());
        var connection=new Connection(PacketFlow.SERVERBOUND);connection.channel=new EmbeddedChannel();
        connection.address=new InetSocketAddress("127.0.0.1",1);
        pushes.put(actor,new ArrayList<>());ticks.put(actor,new ArrayList<>());
        actor.connection=new ServerGamePacketListenerImpl(server,connection,actor,CommonListenerCookie.createInitial(profile,false)){
            @Override public boolean hasClientLoaded(){return true;}
            @Override public void send(Packet<?> packet){capture(actor,packet);}
        };
        level.getChunk(0,0);actor.setPos(.5,200,.5);
        server.getPlayerList().getPlayers().add(actor);server.getPlayerList().getPlayersByUUID().put(profile.id(),actor);level.addNewPlayer(actor);
        Bukkit.getPluginManager().callEvent(new PlayerJoinEvent(actor.getBukkitEntity(),""));
        return actor;
    }
    void capture(ServerPlayer actor,Packet<?> packet){
        if(packet instanceof ClientboundResourcePackPushPacket push){pushes.get(actor).add(push);ticks.get(actor).add(Bukkit.getCurrentTick());}
        else if(packet instanceof BundlePacket<?> bundle)for(Packet<?> child:bundle.subPackets())capture(actor,child);
    }
    List<UUID> ids(ServerPlayer actor){return pushes.get(actor).stream().map(ClientboundResourcePackPushPacket::id).toList();}
    ClientboundResourcePackPushPacket last(ServerPlayer actor){return pushes.get(actor).getLast();}
    void status(ServerPlayer actor,UUID id,Status status){Bukkit.getPluginManager().callEvent(new PlayerResourcePackStatusEvent(actor.getBukkitEntity(),id,status));}
    Object field(Object owner,String name){
        try{var f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}catch(ReflectiveOperationException e){throw new RuntimeException(e);}
    }
    Object magicPacks(){
        try{var f=magic.getClass().getDeclaredField("packs");f.setAccessible(true);return f.get(magic);}
        catch(ReflectiveOperationException e){
            for(var f:magic.getClass().getDeclaredFields())if(f.getType().getSimpleName().equals("ResourcePackService"))try{f.setAccessible(true);return f.get(magic);}catch(ReflectiveOperationException ignored){}
            throw new RuntimeException("Advance Magic pack service not found",e);
        }
    }
    void await(String what,java.util.function.BooleanSupplier ready,int limit,Runnable then){
        if(ready.getAsBoolean()){then.run();return;}
        if(limit<=0)throw new AssertionError("timed out waiting for "+what);
        later(()->await(what,ready,limit-1,then),10);
    }

    /** The verified public copy behind a pack service's MirrorLookup, or null. */
    Object copy(Object service){Object lookup=field(service,"mirror");return lookup==null?null:field(lookup,"copy");}
    boolean unreachable(){return Files.exists(Path.of("pack-unreachable.flag"));}
    String mirrorHost(){
        var templates=garden.getConfig().getStringList("resource-pack.mirrors");
        return templates.isEmpty()?"":URI.create(templates.getFirst().replace("{pack}","p").replace("{fingerprint}","f")).getAuthority();
    }

    void start(){
        garden=(VoidscapePlugin)Bukkit.getPluginManager().getPlugin("Evergarden");
        magic=(JavaPlugin)Bukkit.getPluginManager().getPlugin("advance-magic");
        // Unreachable mode hands out an address nothing listens on, like a tunnel that carries only Minecraft.
        String own=unreachable()?"127.0.0.2":"127.0.0.1";
        for(var cfg:List.of(garden.getConfig(),magic.getConfig()))cfg.set("resource-pack.host.public-host",own);
        boolean importing=!(boolean)field(garden.judgePack(),"modelEngineIncluded");
        ServerPlayer early=join("EarlyProbe");
        later(()->{
            if(importing)check(pushes.get(early).isEmpty(),"a player joining during ModelEngine's import waits for Judge: "+ids(early));
            await("ModelEngine rig in the Judge pack",()->(boolean)field(garden.judgePack(),"modelEngineIncluded")&&!pushes.get(early).isEmpty(),240,()->later(()->{
                check(ids(early).equals(List.of(MAGIC,GARDEN,JUDGE)),"the waiting player gets all three packs: "+ids(early));
                check(new HashSet<>(ticks.get(early)).size()==1,"in one batch, so the client reloads once");
                if(!mirrorHost().isEmpty())for(var push:pushes.get(early))
                    check(URI.create(push.url()).getAuthority().equals(mirrorHost()),"even right after ModelEngine's import, "+push.id()+" comes from the public copy: "+push.url());
                for(var push:pushes.get(early))verifyHttp(push);
                for(UUID id:List.of(MAGIC,GARDEN,JUDGE))status(early,id,Status.SUCCESSFULLY_LOADED);
                await("verified public copies",()->copy(magicPacks())!=null&&copy(garden.packs())!=null&&copy(garden.judgePack())!=null,240,this::mirrored);
            },4));
        },4);
    }
    void mirrored(){
        String mirrorHost=mirrorHost();
        ServerPlayer player=join("MirrorProbe");
        later(()->{
            check(ids(player).equals(List.of(MAGIC,GARDEN,JUDGE))&&new HashSet<>(ticks.get(player)).size()==1,"a later player gets every pack in one batch: "+ids(player));
            for(var push:pushes.get(player)){
                check(URI.create(push.url()).getAuthority().equals(mirrorHost),"pack "+push.id()+" comes from the public copy: "+push.url());
                verifyHttp(push);
            }
            status(player,MAGIC,Status.ACCEPTED);status(player,GARDEN,Status.ACCEPTED);status(player,JUDGE,Status.ACCEPTED);
            status(player,JUDGE,Status.FAILED_DOWNLOAD);status(player,MAGIC,Status.DISCARDED);status(player,GARDEN,Status.DISCARDED);
            later(()->{
                check(kicks.getOrDefault(player.getBukkitEntity(),0)==0,"a broken batch does not disconnect");
                check(pushes.get(player).size()==4&&last(player).id().equals(MAGIC),"Magic is sent again on its own first: "+ids(player));
                verifyHttp(last(player));
                status(player,MAGIC,Status.SUCCESSFULLY_LOADED);
                later(()->{
                    check(pushes.get(player).size()==5&&last(player).id().equals(GARDEN),"then Evergarden: "+ids(player));
                    verifyHttp(last(player));
                    status(player,GARDEN,Status.SUCCESSFULLY_LOADED);
                    later(()->{
                        var judge=last(player);
                        check(pushes.get(player).size()==6&&judge.id().equals(JUDGE)&&!URI.create(judge.url()).getAuthority().equals(mirrorHost),
                            "then Judge from this server's own address: "+judge.url());
                        if(unreachable()){
                            check(!reachable(judge),"this server's own address really is unreachable: "+judge.url());
                            status(player,JUDGE,Status.FAILED_DOWNLOAD);
                            later(()->{
                                // The probe cancels kicks, so the watchdog repeats one every second.
                                check(kicks.getOrDefault(player.getBukkitEntity(),0)>0,"with no address left, a required Judge failure disconnects");
                                check(pushes.get(player).size()==6,"no further addresses are tried");
                                finish(null);
                            },40);
                            return;
                        }
                        verifyHttp(judge);
                        status(player,JUDGE,Status.SUCCESSFULLY_LOADED);
                        later(()->{
                            check(kicks.getOrDefault(player.getBukkitEntity(),0)==0,"the recovered player stays connected");
                            check(pushes.get(player).size()==6,"nothing more is sent once every pack has loaded");
                            finish(null);
                        },40);
                    },4);
                },4);
            },4);
        },4);
    }
    boolean reachable(ClientboundResourcePackPushPacket push){
        try{
            var client=HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(3)).build();
            return client.send(HttpRequest.newBuilder(URI.create(push.url())).GET().build(),HttpResponse.BodyHandlers.discarding()).statusCode()==200;
        }catch(Exception e){return false;}
    }
    void verifyHttp(ClientboundResourcePackPushPacket push){
        try{
            var client=HttpClient.newHttpClient();var uri=URI.create(push.url());
            var result=client.send(HttpRequest.newBuilder(uri).GET().build(),HttpResponse.BodyHandlers.ofByteArray());
            String digest=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(result.body()));
            check(result.statusCode()==200&&digest.equals(push.hash()),"served bytes match the pushed hash for "+push.id()+" from "+uri.getAuthority());
        }catch(Exception e){throw new RuntimeException(e);}
    }
    void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);getLogger().info("PASS: "+message);}
    void finish(Throwable error){
        try{Files.writeString(Path.of("arkhaia-result.txt"),error==null?"PASS "+checks+" pack batch checks":"FAIL "+error);}catch(Exception ignored){}
        if(error!=null)getLogger().log(java.util.logging.Level.SEVERE,"Pack batch probe failed",error);
        Bukkit.getScheduler().runTask(this,()->Bukkit.shutdown());
    }
}
