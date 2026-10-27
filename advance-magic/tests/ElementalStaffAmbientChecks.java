import com.example.advancemagic.AdvanceMagicPlugin;
import com.example.advancemagic.item.ElementalStaffService.Element;
import com.mojang.authlib.GameProfile;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.bukkit.*;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.session.GeyserSession;
import java.util.*;
import java.util.function.BiConsumer;

/** Called by the isolated Geyser integration probe; captures actual outgoing Paper dust packets. */
public final class ElementalStaffAmbientChecks {
    private static final class Session extends GeyserSession {
        private Session(){super(null,null,null);}
        @Override public GeyserImpl getGeyser(){return GeyserImpl.getInstance();}
        @Override protected void tick(){}
        @Override public boolean isClosed(){return true;}
    }
    private record Dust(List<Double> position,List<Float> color,float scale,int count,boolean force){}
    private static Object read(Object value,String modern,String previous){
        try{return value.getClass().getMethod(modern).invoke(value);}
        catch(NoSuchMethodException old){try{return value.getClass().getMethod(previous).invoke(value);}catch(Exception error){throw new IllegalStateException(error);}}
        catch(Exception error){throw new IllegalStateException(error);}
    }
    private static final class Capture extends Connection {
        final List<Dust> dust=new ArrayList<>();
        Capture(){super(PacketFlow.SERVERBOUND);channel=new EmbeddedChannel();address=new java.net.InetSocketAddress("127.0.0.1",1);}
        void capture(Packet<?> packet){
            if(!(packet instanceof ClientboundLevelParticlesPacket))return;
            if(!(read(packet,"particle","getParticle") instanceof DustParticleOptions options))return;
            var color=options.getColor();
            dust.add(new Dust(List.of(((Number)read(packet,"x","getX")).doubleValue(),((Number)read(packet,"y","getY")).doubleValue(),((Number)read(packet,"z","getZ")).doubleValue()),
                    List.of(color.x(),color.y(),color.z()),options.getScale(),((Number)read(packet,"count","getCount")).intValue(),(boolean)read(packet,"overrideLimiter","isOverrideLimiter")));
        }
        @Override public void send(Packet<?> packet){capture(packet);}
        @Override public void send(Packet<?> packet,ChannelFutureListener listener){capture(packet);}
        @Override public void send(Packet<?> packet,ChannelFutureListener listener,boolean flush){capture(packet);}
        @Override public boolean isConnected(){return true;}
    }
    private record Actor(ServerPlayer handle,Capture connection){Player player(){return handle.getBukkitEntity();}}
    private static Actor actor(World world,String name,double x){
        var server=MinecraftServer.getServer();var level=((CraftWorld)world).getHandle();
        var profile=new GameProfile(UUID.randomUUID(),name);var player=new ServerPlayer(server,level,profile,ClientInformation.createDefault());var capture=new Capture();
        player.connection=new ServerGamePacketListenerImpl(server,capture,player,CommonListenerCookie.createInitial(profile,false)){
            @Override public boolean hasClientLoaded(){return true;}
        };
        player.setPos(x,100,.5);server.getPlayerList().getPlayers().add(player);server.getPlayerList().getPlayersByUUID().put(profile.id(),player);level.addNewPlayer(player);
        player.getBukkitEntity().setGameMode(GameMode.SURVIVAL);player.getBukkitEntity().setGravity(false);return new Actor(player,capture);
    }
    private static void clear(List<Actor> actors){for(Actor actor:actors)actor.connection.dust.clear();}
    /** The original unfiltered Java visual is the packet-level control for this receiver-only change. */
    private static void originalAmbient(Player holder,Element element,int frame){
        var forward=holder.getLocation().getDirection().setY(0);if(forward.lengthSquared()<.01)forward.setZ(1);forward.normalize();
        var right=new org.bukkit.util.Vector(-forward.getZ(),0,forward.getX());
        Location crown=holder.getLocation().add(0,1.9,0).add(forward.multiply(.45)).add(right.multiply(holder.getMainHand()==org.bukkit.inventory.MainHand.LEFT?-.35:.35));
        double a=frame*.28;var dust=new Particle.DustOptions(Color.fromRGB(element.rgb),.55f);
        holder.getWorld().spawnParticle(Particle.DUST,crown.clone().add(Math.cos(a)*.11,Math.sin(a*.7)*.10,Math.sin(a)*.11),1,0,0,0,0,dust);
        holder.getWorld().spawnParticle(Particle.DUST,crown.clone().add(-Math.cos(a)*.09,-.12,-Math.sin(a)*.09),1,0,0,0,0,dust);
    }
    public static void run(AdvanceMagicPlugin magic,BiConsumer<Boolean,String> check) throws Exception {
        World world=Bukkit.getWorlds().getFirst();List<Actor> actors=new ArrayList<>();List<UUID> classified=new ArrayList<>();
        Object oldSetting=magic.getConfig().get("elemental-staffs.ambient-effects");
        var manager=GeyserImpl.getInstance().getSessionManager();
        try{
            Actor owner=actor(world,"DustJavaOwner",.5),observer=actor(world,"DustJavaView",3.5),bedrock=actor(world,"DustBedrock",2.5),far=actor(world,"DustFarJava",2048.5);
            actors.addAll(List.of(owner,observer,bedrock,far));
            var unsafe=Class.forName("sun.misc.Unsafe");var field=unsafe.getDeclaredField("theUnsafe");field.setAccessible(true);
            var session=(Session)unsafe.getMethod("allocateInstance",Class.class).invoke(field.get(null),Session.class);
            manager.addSession(bedrock.player().getUniqueId(),session);classified.add(bedrock.player().getUniqueId());
            check.accept(magic.packs().isBedrock(bedrock.player())&&!magic.packs().isBedrock(owner.player()),"actual Geyser session classifies Bedrock receiver and Java control");
            magic.getConfig().set("elemental-staffs.ambient-effects",true);
            owner.player().getInventory().setItemInMainHand(magic.staffs().create(Element.DARK));
            var frame=magic.staffs().getClass().getDeclaredField("frame");frame.setAccessible(true);
            clear(actors);originalAmbient(owner.player(),Element.DARK,frame.getInt(magic.staffs())+1);
            List<Dust> control=List.copyOf(owner.connection.dust);
            check.accept(control.size()==2&&bedrock.connection.dust.size()==2,"unfiltered original visual reaches Java and Bedrock packet controls");
            check.accept(far.connection.dust.isEmpty(),"original default particle distance excludes distant Java viewer");
            clear(actors);magic.staffs().tick();
            if(!owner.connection.dust.equals(control)){
                System.out.println("AMBIENT CONTROL "+control);
                System.out.println("AMBIENT ACTUAL "+owner.connection.dust);
            }
            check.accept(owner.connection.dust.equals(control),"Java owner keeps exact original dust positions colors sizes counts and force flag");
            check.accept(observer.connection.dust.equals(control),"nearby Java observer keeps exact original staff ambient packets");
            check.accept(bedrock.connection.dust.isEmpty(),"nearby Bedrock viewer receives no server staff dust");
            check.accept(far.connection.dust.isEmpty(),"filtered world emission preserves original default distance semantics");
            owner.player().getInventory().setItemInMainHand(new ItemStack(Material.AIR));bedrock.player().getInventory().setItemInMainHand(magic.staffs().create(Element.DARK));
            clear(actors);magic.staffs().tick();
            check.accept(owner.connection.dust.size()==2&&observer.connection.dust.size()==2,"Java observers still see ambient dust from a Bedrock-held staff");
            check.accept(bedrock.connection.dust.isEmpty(),"Bedrock staff owner receives no near-camera server dust");
            check.accept(far.connection.dust.isEmpty(),"Bedrock-held staff does not send particles to distant Java viewer");
            magic.getConfig().set("elemental-staffs.ambient-effects",false);clear(actors);magic.staffs().tick();
            check.accept(actors.stream().allMatch(actor->actor.connection.dust.isEmpty()),"ambient disabled setting still suppresses all staff dust");
        }finally{
            magic.getConfig().set("elemental-staffs.ambient-effects",oldSetting);
            for(UUID uuid:classified)manager.getSessions().remove(uuid);
            var server=MinecraftServer.getServer();
            for(Actor actor:actors){server.getPlayerList().getPlayers().remove(actor.handle);server.getPlayerList().getPlayersByUUID().remove(actor.handle.getUUID());actor.handle.discard();}
        }
    }
}
