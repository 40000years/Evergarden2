import com.example.advancemagic.AdvanceMagicPlugin;
import com.example.advancemagic.elemental.ElementalSpell;
import com.example.advancemagic.spell.ElementalVfx;
import com.example.advancemagic.spell.MagicContext;
import com.google.gson.JsonParser;
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
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.entity.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.SpawnParticleEffectPacket;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.session.GeyserSession;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;

/** Actual Geyser session lookup and production bridge packets; no GPU/client claim. */
public final class HeavensDescentBedrockChecks extends JavaPlugin {
    static final String PREFIX="advance_magic:elemental_legendary_light_";
    static final class CaptureSession extends GeyserSession {
        List<SpawnParticleEffectPacket> particles;
        Map<Integer,Integer> counts;
        Map<SpawnParticleEffectPacket,Integer> packetTicks;
        boolean budget;
        UUID id;
        org.geysermc.geyser.session.cache.WorldCache worldCache;
        io.netty.channel.EventLoop loop;
        private CaptureSession(){super(null,null,null);}
        @Override public UUID playerUuid(){return id;}
        @Override public UUID javaUuid(){return id;}
        @Override public org.geysermc.geyser.level.JavaDimension getDimensionType(){return null;}
        @Override public org.geysermc.geyser.session.cache.WorldCache getWorldCache(){return worldCache;}
        @Override public org.geysermc.geyser.GeyserImpl getGeyser(){return GeyserImpl.getInstance();}
        @Override public io.netty.channel.EventLoop getTickEventLoop(){return loop;}
        @Override public void ensureInEventLoop(Runnable task){task.run();}
        @Override public void sendUpstreamPacket(BedrockPacket packet){
            if(packet instanceof SpawnParticleEffectPacket particle){particles.add(particle);packetTicks.put(particle,Bukkit.getCurrentTick());if(budget)counts.merge(Bukkit.getCurrentTick(),1,Integer::sum);}
        }
    }
    static final class Capture extends Connection {
        Capture(){super(PacketFlow.SERVERBOUND);channel=new EmbeddedChannel();address=new java.net.InetSocketAddress("127.0.0.1",1);}
        @Override public void send(Packet<?> packet){}
        @Override public void send(Packet<?> packet,ChannelFutureListener listener){}
        @Override public void send(Packet<?> packet,ChannelFutureListener listener,boolean flush){}
        @Override public boolean isConnected(){return true;}
    }
    AdvanceMagicPlugin magic;
    World world;
    Player player;
    ServerPlayer actor;
    CaptureSession session;
    ElementalVfx renderer;
    int checks,age;
    void check(boolean value,String label){if(!value)throw new AssertionError(label);checks++;getLogger().info("PASS "+label);}
    @Override public void onEnable(){Bukkit.getScheduler().runTaskLater(this,()->{try{start();}catch(Throwable error){finish(error);}},80);}
    Location stage(){return new Location(world,.5,100,4.5);}
    void start() throws Exception {
        check(Bukkit.getPluginManager().isPluginEnabled("Geyser-Spigot"),"installed Geyser starts with the fresh release");
        magic=(AdvanceMagicPlugin)Bukkit.getPluginManager().getPlugin("advance-magic");check(magic!=null&&magic.isEnabled(),"production plugin enabled before Geyser probe");
        world=Bukkit.getWorlds().getFirst();for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)world.getChunkAt(x,z).setForceLoaded(true);
        var server=MinecraftServer.getServer();var profile=new GameProfile(UUID.randomUUID(),"BedrockLight");
        actor=new ServerPlayer(server,((CraftWorld)world).getHandle(),profile,ClientInformation.createDefault());
        actor.connection=new ServerGamePacketListenerImpl(server,new Capture(),actor,CommonListenerCookie.createInitial(profile,false)){
            @Override public boolean hasClientLoaded(){return true;}
        };
        actor.setPos(.5,100,.5);server.getPlayerList().getPlayers().add(actor);server.getPlayerList().getPlayersByUUID().put(profile.id(),actor);((CraftWorld)world).getHandle().addNewPlayer(actor);
        player=actor.getBukkitEntity();player.setGravity(false);player.teleport(new Location(world,.5,100,.5));
        var unsafe=Class.forName("sun.misc.Unsafe");var field=unsafe.getDeclaredField("theUnsafe");field.setAccessible(true);
        session=(CaptureSession)unsafe.getMethod("allocateInstance",Class.class).invoke(field.get(null),CaptureSession.class);
        session.id=player.getUniqueId();session.particles=new ArrayList<>();session.counts=new TreeMap<>();session.packetTicks=new IdentityHashMap<>();
        session.loop=new EmbeddedChannel().eventLoop();session.worldCache=new org.geysermc.geyser.session.cache.WorldCache(session);
        GeyserImpl.getInstance().getSessionManager().getSessions().put(session.id,session);
        check(GeyserImpl.getInstance().connectionByUuid(session.id)==session,"actual Geyser UUID lookup resolves the captured Bedrock session");
        pack();
        magic.getConfig().set("elemental-visuals.style","native");magic.getConfig().set("elemental-visuals.quality","balanced");
        magic.getConfig().set("elemental-visuals.bedrock-packets-per-viewer-tick",12);magic.getConfig().set("elemental-visuals.legendary-light.enabled",true);
        renderer=new ElementalVfx(magic.context());session.budget=true;
        for(int i=0;i<8;i++)renderer.begin(player,ElementalSpell.HEAVENS_DESCENT).pillar(stage(),12);
        Bukkit.getScheduler().runTaskTimer(this,task->{try{
            renderer.tick();if(++age<96)return;task.cancel();sceneResults();
        }catch(Throwable error){task.cancel();finish(error);}},1,1);
    }
    void pack() throws Exception {
        Path installed=Path.of("plugins/Geyser-Spigot/packs/advance-magic-bedrock.mcpack");
        byte[] expected=Files.readAllBytes(Path.of("plugins/advance-magic/resource-packs/advance-magic-bedrock.mcpack"));
        check(Files.isRegularFile(installed)&&Arrays.equals(Files.readAllBytes(installed),expected),"Geyser receives exactly the bundled new mcpack through automatic install");
        try(var archive=new ZipFile(installed.toFile())){
            var manifest=JsonParser.parseString(new String(archive.getInputStream(archive.getEntry("manifest.json")).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            check(manifest.getAsJsonObject("header").getAsJsonArray("version").toString().equals("[2,28,0]"),"installed Geyser mcpack version is 2.28.0");
            for(var module:manifest.getAsJsonArray("modules"))if(!module.getAsJsonObject().getAsJsonArray("version").toString().equals("[2,28,0]"))throw new AssertionError("Bedrock module cache version differs");
            Set<String> found=new HashSet<>();
            for(var entry:Collections.list(archive.entries()))if(entry.getName().startsWith("particles/")&&entry.getName().endsWith(".json")){
                var body=JsonParser.parseString(new String(archive.getInputStream(entry).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                if(body.has("particle_effect")){
                    var effect=body.getAsJsonObject("particle_effect");
                    String id=effect.getAsJsonObject("description").get("identifier").getAsString();if(id.startsWith(PREFIX))found.add(id);
                    if(Set.of(PREFIX+"sigil_flat",PREFIX+"halo_flat").contains(id)){
                        var components=effect.getAsJsonObject("components");
                        check(components.getAsJsonObject("minecraft:particle_appearance_billboard").get("facing_camera_mode").getAsString().equals("emitter_transform_xz"),id+" uses a horizontal native Bedrock plane");
                        var spin=components.getAsJsonObject("minecraft:particle_initial_spin");
                        check(spin.get("rotation_rate").getAsString().contains("variable.line_rotation_rate"),id+" continues rotating between server packets on the client");
                    }
                    if(Set.of(PREFIX+"beacon",PREFIX+"shot").contains(id)){
                        var billboard=effect.getAsJsonObject("components").getAsJsonObject("minecraft:particle_appearance_billboard");
                        var direction=billboard.getAsJsonObject("direction");
                        check(billboard.get("facing_camera_mode").getAsString().equals("direction_y")&&direction.get("mode").getAsString().equals("custom")
                                &&direction.getAsJsonArray("custom_direction").toString().contains("variable.line_normal_"),id+" aligns its long axis with the supplied beam direction");
                    }
                    if(id.equals(PREFIX+"motes")){
                        var components=effect.getAsJsonObject("components");
                        check(components.has("minecraft:emitter_rate_instant")&&components.has("minecraft:particle_motion_parametric")
                                &&components.getAsJsonObject("minecraft:particle_appearance_billboard").get("facing_camera_mode").getAsString().equals("lookat_xyz"),
                                "native Bedrock light motes drift as small camera-facing particles instead of painted mist sheets");
                    }
                }
            }
            check(found.equals(expectedIds()),"all nine preserved and new light bridge particle identifiers exist in the installed mcpack");
        }
    }
    Set<String> expectedIds(){return Set.of(PREFIX+"sigil",PREFIX+"sigil_flat",PREFIX+"halo",PREFIX+"halo_flat",PREFIX+"mist",PREFIX+"mist_flat",PREFIX+"beacon",PREFIX+"shot",PREFIX+"motes");}
    void sceneResults() throws Exception {
        session.budget=false;
        check(session.counts.size()>=20&&session.counts.values().stream().allMatch(n->n<=12),"eight simultaneous scenes including orbital volleys and motes emit at most twelve packets per viewer on every real server tick");
        check(world.getEntitiesByClass(ItemDisplay.class).isEmpty(),"Bedrock-only viewer receives native emitters without invisible display entities");
        Set<String> ids=new HashSet<>();for(var packet:session.particles)ids.add(packet.getIdentifier());
        check(ids.equals(Set.of(PREFIX+"sigil_flat",PREFIX+"halo_flat",PREFIX+"beacon",PREFIX+"shot",PREFIX+"motes")),
                "live scene emits horizontal circles, a beacon, helper shots and light motes without standing circles or painted mist");
        List<SpawnParticleEffectPacket> sigils=new ArrayList<>(),halos=new ArrayList<>();
        double minY=Double.POSITIVE_INFINITY,maxY=Double.NEGATIVE_INFINITY;
        for(var packet:session.particles){
            checkPacket(packet);
            if(!Set.of(PREFIX+"sigil_flat",PREFIX+"halo_flat").contains(packet.getIdentifier()))continue;
            var values=values(packet);double y=packet.getPosition().getY()+values.get("variable.line_offset_y");
            if(packet.getIdentifier().equals(PREFIX+"sigil_flat")){minY=Math.min(minY,y);maxY=Math.max(maxY,y);}
            if(y<stage().getY()+1){if(packet.getIdentifier().equals(PREFIX+"sigil_flat"))sigils.add(packet);else halos.add(packet);}
            if(Math.abs(values.get("variable.line_normal_y"))<.9999)throw new AssertionError("native scene contains a tilted circle");
            if(!values.containsKey("variable.line_rotation_rate")||Math.abs(values.get("variable.line_rotation_rate"))<10)throw new AssertionError("native rotating circle lacks a meaningful client angular velocity");
        }
        check(maxY-minY>=31&&maxY-minY<=33,"real Bedrock packet centers preserve the 32-block floor-to-sky gap after nearby-emitter offsets");
        double sigilTurn=turn(sigils),haloTurn=turn(halos);
        double sigilRate=values(sigils.getFirst()).get("variable.line_rotation_rate"),haloRate=values(halos.getFirst()).get("variable.line_rotation_rate");
        check(Math.abs(sigilTurn)>20&&Math.abs(haloTurn)>15&&sigilTurn*haloTurn<0&&sigilRate*haloRate<0
                &&sigilTurn*sigilRate>0&&haloTurn*haloRate>0,
                "observed floor sigil and halo phases counter-rotate visibly while finite client angular velocities preserve their opposite directions");
        var beacons=session.particles.stream().filter(p->p.getIdentifier().equals(PREFIX+"beacon")).toList();
        var first=beacons.getFirst();var last=beacons.getLast();
        check(endpoint(first,true).getY()>stage().getY()+20&&endpoint(last,true).getY()<stage().getY()+1
                &&endpoint(first,false).distance(endpoint(last,false))<.01,
                "native beacon packets visibly descend from a fixed sky origin until the shaft reaches the floor");
        check(beacons.size()>=20&&values(last).get("variable.line_height")>31&&values(last).get("variable.line_width")>4,
                "the full-height native beacon stays visibly present throughout sustained orbital firing");
        Set<String> shotOrigins=new HashSet<>();Set<Integer> shotHeights=new HashSet<>();
        for(var packet:session.particles)if(packet.getIdentifier().equals(PREFIX+"shot")){
            var from=endpoint(packet,false);var to=endpoint(packet,true);int tick=session.packetTicks.get(packet);
            boolean anchored=session.particles.stream().filter(p->p.getIdentifier().equals(PREFIX+"sigil_flat")
                    &&session.packetTicks.get(p)==tick).map(this::center).filter(p->p.getY()>stage().getY()+1&&p.getY()<stage().getY()+28)
                    .anyMatch(p->p.distance(from)<2.5);
            if(!anchored||to.getY()>stage().getY()+1||from.distance(to)<10||Math.hypot(to.getX()-stage().getX(),to.getZ()-stage().getZ())<15)
                throw new AssertionError("Bedrock helper shot is detached or has an incorrect downward/outward endpoint: "+from+" -> "+to);
            shotOrigins.add(Math.round(from.getX()*1000)+":"+Math.round(from.getY()*1000)+":"+Math.round(from.getZ()*1000));shotHeights.add((int)Math.round(from.getY()*10));
        }
        check(shotOrigins.size()>=5&&shotHeights.size()==4,"all four native helper seals fire anchored cosmetic beams and discharges recur during the sampled ninety-six real ticks");
        check(session.particles.stream().anyMatch(p->p.getIdentifier().equals(PREFIX+"motes"))
                &&session.particles.stream().anyMatch(p->Math.abs(values(p).get("variable.line_offset_y"))>10),
                "native drifting motes share the budget and distant beacon artwork retains finite viewer-near offsets");
        renderer.close();
        check(world.getEntitiesByClass(ItemDisplay.class).isEmpty(),"closing the Bedrock scene leaves no display entities");
        Files.writeString(Path.of("heavens-descent-bedrock-packets.txt"),"packets="+session.particles.size()+"\nper_tick="+session.counts+"\nidentifiers="+new TreeSet<>(ids)+"\n");
        finish(null);
    }
    Map<String,Double> values(SpawnParticleEffectPacket packet){
        var rows=JsonParser.parseString(packet.getMolangVariablesJson().orElseThrow()).getAsJsonArray();Map<String,Double> values=new HashMap<>();
        for(var row:rows){var entry=row.getAsJsonObject();double value=entry.getAsJsonObject("value").get("value").getAsDouble();if(!Double.isFinite(value))throw new AssertionError("nonfinite Molang");values.put(entry.get("name").getAsString(),value);}
        return values;
    }
    Vector center(SpawnParticleEffectPacket packet){var values=values(packet);var at=packet.getPosition();return new Vector(at.getX()+values.get("variable.line_offset_x"),at.getY()+values.get("variable.line_offset_y"),at.getZ()+values.get("variable.line_offset_z"));}
    Vector endpoint(SpawnParticleEffectPacket packet,boolean end){var values=values(packet);return center(packet).add(new Vector(values.get("variable.line_normal_x"),values.get("variable.line_normal_y"),values.get("variable.line_normal_z")).multiply(values.get("variable.line_height")*.5*(end?1:-1)));}
    double turn(List<SpawnParticleEffectPacket> packets){
        if(packets.size()<2)throw new AssertionError("insufficient repeated circle packets");
        double previous=values(packets.getFirst()).get("variable.line_rotation"),sum=0;
        for(var packet:packets){double next=values(packet).get("variable.line_rotation");sum+=Math.toDegrees(Math.atan2(Math.sin(Math.toRadians(next-previous)),Math.cos(Math.toRadians(next-previous))));previous=next;}
        return sum;
    }
    void checkPacket(SpawnParticleEffectPacket packet){
        if(!packet.getIdentifier().startsWith(PREFIX)||packet.getDimensionId()!=0)throw new AssertionError("wrong native particle identity or dimension");
        var values=values(packet);
        boolean mote=packet.getIdentifier().equals(PREFIX+"motes");
        if((mote?values.size()!=7:values.size()<9||values.size()>10)||values.getOrDefault("variable.line_width",0d)<=0||values.getOrDefault("variable.line_height",0d)<=0)throw new AssertionError("missing geometry variables");
        if(mote){if(values.getOrDefault("variable.light_intensity",0d)<=0||values.get("variable.light_intensity")>1||values.getOrDefault("variable.line_age",-1d)<0)throw new AssertionError("invalid native mote lifetime/strength");}
        else {double normal=0;for(String axis:List.of("x","y","z"))normal+=Math.pow(values.get("variable.line_normal_"+axis),2);if(Math.abs(normal-1)>.0001)throw new AssertionError("nonunit plane basis");}
        var position=packet.getPosition();if(!Float.isFinite(position.getX()+position.getY()+position.getZ()))throw new AssertionError("nonfinite emitter position");
        var eye=player.getEyeLocation();double distance=Math.sqrt(Math.pow(position.getX()-eye.getX(),2)+Math.pow(position.getY()-eye.getY(),2)+Math.pow(position.getZ()-eye.getZ(),2));
        if(distance>12.001)throw new AssertionError("emitter exceeds nearby range: "+distance);
    }
    void finish(Throwable error){
        if(error!=null)getLogger().log(java.util.logging.Level.SEVERE,"HEAVENS BEDROCK FAILED after "+checks,error);
        if(renderer!=null)renderer.close();if(session!=null)GeyserImpl.getInstance().getSessionManager().getSessions().remove(session.id);
        if(actor!=null){MinecraftServer.getServer().getPlayerList().getPlayers().remove(actor);MinecraftServer.getServer().getPlayerList().getPlayersByUUID().remove(actor.getUUID());actor.discard();}
        try{Files.writeString(Path.of("heavens-descent-bedrock-result.txt"),error==null?"PASS "+checks+" actual Geyser packet checks; no GPU client test":"FAIL after "+checks+": "+error);}catch(Exception ignored){}
        Bukkit.getScheduler().runTaskLater(this,Bukkit::shutdown,2);
    }
}
