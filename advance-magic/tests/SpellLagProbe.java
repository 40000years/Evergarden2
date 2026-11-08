import com.example.advancemagic.elemental.ElementalSpell;
import com.mojang.authlib.GameProfile;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.bukkit.*;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.entity.*;
import org.cloudburstmc.protocol.bedrock.packet.*;
import java.util.*;

/**
 * Lag survey: casts every elemental spell from a real Mythic staff with a Java watcher and a Bedrock (Geyser)
 * observer in range, and records what each tick costs the server and each client. Bedrock cost is the packets
 * the renderer sends that session directly plus one packet per vanilla particle Geyser would translate.
 * Writes spell-lag-report.json; budget assertions live in the gate probes.
 */
public final class SpellLagProbe extends LegendaryBlockBedrockChecks {
    static final class Counter extends Connection {
        int packets,particles;
        Counter(){super(PacketFlow.SERVERBOUND);channel=new EmbeddedChannel();address=new java.net.InetSocketAddress("127.0.0.1",2);}
        void count(Packet<?> packet){
            if(packet instanceof ClientboundBundlePacket bundle){for(var sub:bundle.subPackets())count(sub);return;}
            packets++;
            if(packet instanceof ClientboundLevelParticlesPacket p)particles+=Math.max(1,(int)Capture.sample(p,"count","getCount"));
        }
        @Override public void send(Packet<?> packet){count(packet);}
        @Override public void send(Packet<?> packet,ChannelFutureListener listener){count(packet);}
        @Override public void send(Packet<?> packet,ChannelFutureListener listener,boolean flush){count(packet);}
        @Override public boolean isConnected(){return true;}
    }
    static final Set<String> DIRECT=Set.of("bubble_prison","crystal_prison","life_drain","cursed_mark","hellfire_chain","lightning_chain");

    @Override void afterLegendary()throws Exception{
        super.afterLegendary();
        survey();
    }

    @SuppressWarnings("unchecked") void survey()throws Exception{
        var server=MinecraftServer.getServer();var level=((CraftWorld)world).getHandle();
        var profile=new GameProfile(UUID.randomUUID(),"JavaWatcher");var watcher=new ServerPlayer(server,level,profile,ClientInformation.createDefault());
        Counter javaView=new Counter();
        watcher.connection=new ServerGamePacketListenerImpl(server,javaView,watcher,CommonListenerCookie.createInitial(profile,false)){
            @Override public boolean hasClientLoaded(){return true;}
        };
        watcher.setPos(-3.5,100,3.5);server.getPlayerList().getPlayers().add(watcher);server.getPlayerList().getPlayersByUUID().put(profile.id(),watcher);level.addNewPlayer(watcher);
        watcher.getBukkitEntity().setGameMode(GameMode.SPECTATOR);watcher.getBukkitEntity().setGravity(false);
        var status=magic.packs().getClass().getDeclaredField("statuses");status.setAccessible(true);
        ((Map<UUID,String>)status.get(magic.packs())).put(profile.id(),"SUCCESSFULLY_LOADED");
        List<Map<String,Object>> rows=new ArrayList<>();
        try{
            for(ElementalSpell spell:ElementalSpell.values()){
                if(spell==ElementalSpell.PHOENIX_REBIRTH)continue;
                prepare(spell.element(),spell);
                Location stage=stage();
                target(stage);target(stage.clone().add(2,0,1));target(stage.clone().add(-2,0,1));
                drain();javaView.packets=javaView.particles=0;observerCapture.raw.clear();
                long castStart=System.nanoTime();
                boolean cast=magic.elemental().cast(caster);
                long castNanos=System.nanoTime()-castStart;
                int javaPeak=0,javaTotal=0,javaParticlePeak=0,javaParticleTotal=0,bedrockPeak=0,bedrockTotal=0,bedrockParticlePeak=0,bedrockParticleTotal=0;
                int displayPeak=0,ticks=0,quiet=0;long serverTotal=castNanos,serverPeak=castNanos;
                Map<String,Integer> bedrockKinds=new TreeMap<>();
                while(cast&&ticks<320){
                    // The cast tick's own packets count toward the first measured tick.
                    long start=System.nanoTime();
                    simulatedTick++;magic.effects().tick();magic.elemental().tick();
                    long spent=System.nanoTime()-start;serverTotal+=spent;serverPeak=Math.max(serverPeak,spent);
                    int direct=0;
                    for(BedrockPacket packet:session.packets){direct++;bedrockKinds.merge(packet.getClass().getSimpleName(),1,Integer::sum);}
                    int translated=0;for(Object[] p:observerCapture.raw)translated+=Math.max(1,(int)p[4]);
                    drain();observerCapture.raw.clear();
                    int bedrock=direct+translated;
                    bedrockPeak=Math.max(bedrockPeak,bedrock);bedrockTotal+=bedrock;
                    bedrockParticlePeak=Math.max(bedrockParticlePeak,translated);bedrockParticleTotal+=translated;
                    javaPeak=Math.max(javaPeak,javaView.packets);javaTotal+=javaView.packets;
                    javaParticlePeak=Math.max(javaParticlePeak,javaView.particles);javaParticleTotal+=javaView.particles;
                    int displays=world.getEntitiesByClass(Display.class).size();displayPeak=Math.max(displayPeak,displays);
                    boolean active=magic.effects().size()>0||displays>0||bedrock>0||javaView.particles>0;
                    javaView.packets=javaView.particles=0;ticks++;
                    quiet=active?0:quiet+1;
                    if(quiet>=20)break;
                }
                magic.elemental().close();magic.effects().close();drain();javaView.packets=javaView.particles=0;
                Map<String,Object> row=new LinkedHashMap<>();
                row.put("spell",spell.id());row.put("element",spell.element().id());row.put("rarity",spell.rarity().name());row.put("cast",cast);
                row.put("ticks",ticks);
                row.put("server_us_avg",ticks==0?0:serverTotal/1000/Math.max(1,ticks));row.put("server_us_peak",serverPeak/1000);
                row.put("java_packets_peak",javaPeak);row.put("java_packets_total",javaTotal);
                row.put("java_particles_peak",javaParticlePeak);row.put("java_particles_total",javaParticleTotal);
                row.put("bedrock_packets_peak",bedrockPeak);row.put("bedrock_packets_total",bedrockTotal);
                row.put("bedrock_translated_particles_peak",bedrockParticlePeak);row.put("bedrock_translated_particles_total",bedrockParticleTotal);
                row.put("bedrock_direct_kinds",bedrockKinds);row.put("displays_peak",displayPeak);
                rows.add(row);
                getLogger().info(String.format(Locale.ROOT,"LAG %-22s cast=%s ticks=%3d server avg/peak %5d/%6d us | java pk %4d/%6d pt %5d/%7d | bedrock pk %4d/%6d | displays %3d",
                    spell.id(),cast,ticks,(long)row.get("server_us_avg"),serverPeak/1000,javaPeak,javaTotal,javaParticlePeak,javaParticleTotal,bedrockPeak,bedrockTotal,displayPeak));
            }
        }finally{
            server.getPlayerList().getPlayers().remove(watcher);server.getPlayerList().getPlayersByUUID().remove(profile.id());watcher.discard();
        }
        java.nio.file.Files.writeString(java.nio.file.Path.of("spell-lag-report.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(rows));
        check(rows.size()==ElementalSpell.values().length-1,"lag survey covers every castable elemental spell");
    }
}
