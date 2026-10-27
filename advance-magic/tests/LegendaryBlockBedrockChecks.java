import com.example.advancemagic.AdvanceMagicPlugin;
import com.example.advancemagic.elemental.ElementalSpell;
import com.example.advancemagic.item.ElementalStaffService.Element;
import com.mojang.authlib.GameProfile;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.server.network.*;
import org.bukkit.*;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.block.data.CraftBlockData;
import org.bukkit.entity.*;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.data.definitions.BlockDefinition;
import org.cloudburstmc.protocol.bedrock.data.entity.*;
import org.cloudburstmc.protocol.bedrock.packet.*;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.registry.BlockRegistries;
import org.geysermc.geyser.registry.type.BlockMappings;
import org.geysermc.geyser.session.GeyserSession;
import java.util.*;

/** Real staff casts and outgoing custom cube poses, including full display quaternions and interpolation. */
public final class LegendaryBlockBedrockChecks extends LegendarySpectacleChecks {
    static final class Session extends GeyserSession {
        UUID id;List<BedrockPacket> packets;BlockMappings blocks;
        org.geysermc.geyser.session.cache.EntityCache cache;
        org.geysermc.geyser.session.cache.WorldCache worldCache;
        io.netty.channel.EventLoop loop;
        private Session(){super(null,null,null);}
        @Override public UUID playerUuid(){return id;}
        @Override public UUID javaUuid(){return id;}
        @Override public boolean isSpawned(){return false;}
        @Override public org.geysermc.geyser.level.JavaDimension getDimensionType(){return null;}
        @Override public org.geysermc.geyser.session.cache.WorldCache getWorldCache(){return worldCache;}
        @Override public org.geysermc.geyser.session.cache.EntityCache getEntityCache(){return cache;}
        @Override public BlockMappings getBlockMappings(){return blocks;}
        @Override public GeyserImpl getGeyser(){return GeyserImpl.getInstance();}
        @Override public io.netty.channel.EventLoop getTickEventLoop(){return loop;}
        @Override public void ensureInEventLoop(Runnable work){work.run();}
        @Override public void sendUpstreamPacket(BedrockPacket packet){if(packet instanceof AddEntityPacket||packet instanceof MoveEntityAbsolutePacket||packet instanceof SetEntityDataPacket||packet instanceof RemoveEntityPacket)packets.add(packet);}
    }
    static final class Body {Vector3f position;final float[] pose=new float[17];int material,revision;float yaw;}
    final Map<Long,Body> bodies=new HashMap<>();final Set<Long> ids=new HashSet<>();final Set<Integer> materials=new HashSet<>();
    final List<Map<String,Object>> geometryCapture=new ArrayList<>();
    final Map<Integer,Integer> floatChannels=new HashMap<>();int materialIndex,revisionIndex;
    String currentCast;
    @Override void prepare(Element element,ElementalSpell spell){currentCast=spell.id();super.prepare(element,spell);}
    Session session;ServerPlayer observer;long compared,spawned,moved,resized,removed;boolean compare=true;
    @Override void run()throws Exception{
        magic=(AdvanceMagicPlugin)Bukkit.getPluginManager().getPlugin("advance-magic");world=Bukkit.getWorlds().getFirst();
        check(Bukkit.getPluginManager().isPluginEnabled("Geyser-Spigot"),"actual Geyser is enabled for native block packet checks");
        check(magic.blockModels().ready(),"full-pose Legendary cube rig and all entity properties register during Geyser startup");
        var schema=org.geysermc.geyser.registry.Registries.BEDROCK_ENTITY_DEFINITIONS.get().get(org.geysermc.geyser.api.util.Identifier.of("advance_magic:legendary_block")).registeredProperties();
        materialIndex=schema.getPropertyIndex("advance_magic:material");revisionIndex=schema.getPropertyIndex("advance_magic:revision");
        int index=0;for(String end:List.of("from_","to_"))for(String channel:com.example.advancemagic.spell.BedrockBlockModels.CHANNELS)floatChannels.put(schema.getPropertyIndex("advance_magic:"+end+channel),index++);
        floatChannels.put(schema.getPropertyIndex("advance_magic:duration"),16);
        var server=MinecraftServer.getServer();var level=((CraftWorld)world).getHandle();world.getChunkAt(0,0).setForceLoaded(true);
        var profile=new GameProfile(UUID.randomUUID(),"BedrockBlocks");observer=new ServerPlayer(server,level,profile,ClientInformation.createDefault());
        observer.connection=new ServerGamePacketListenerImpl(server,new Capture(),observer,CommonListenerCookie.createInitial(profile,false)){
            @Override public boolean hasClientLoaded(){return true;}
        };
        observer.setPos(3.5,100,.5);server.getPlayerList().getPlayers().add(observer);server.getPlayerList().getPlayersByUUID().put(profile.id(),observer);level.addNewPlayer(observer);
        observer.getBukkitEntity().setGameMode(GameMode.SPECTATOR);observer.getBukkitEntity().setGravity(false);
        var unsafe=Class.forName("sun.misc.Unsafe");var field=unsafe.getDeclaredField("theUnsafe");field.setAccessible(true);
        session=(Session)unsafe.getMethod("allocateInstance",Class.class).invoke(field.get(null),Session.class);
        session.id=profile.id();session.packets=new ArrayList<>();session.blocks=BlockRegistries.BLOCKS.get().values().iterator().next();
        session.cache=new org.geysermc.geyser.session.cache.EntityCache(session);session.worldCache=new org.geysermc.geyser.session.cache.WorldCache(session);
        session.loop=new io.netty.channel.embedded.EmbeddedChannel().eventLoop();GeyserImpl.getInstance().getSessionManager().getSessions().put(session.id,session);
        try{
            super.run();
            check(compared>10000&&spawned>100&&moved>100&&resized>0&&removed==spawned,"all eight real Legendary casts and low quality have native block geometry, movement, resizing and complete cleanup: poses="+compared+", spawns="+spawned+", moves="+moved+", resize="+resized+", removed="+removed);
            check(materials.size()>=12,"native packets retain distinct block materials across all eight elements: "+materials.size());
            java.nio.file.Files.writeString(java.nio.file.Path.of("bedrock-block-geometry.json"),new com.google.gson.Gson().toJson(geometryCapture));
            visibilityLifecycle();
        }finally{
            try{magic.elemental().close();magic.effects().close();drain();}finally{
                GeyserImpl.getInstance().getSessionManager().getSessions().remove(session.id);
                server.getPlayerList().getPlayers().remove(observer);server.getPlayerList().getPlayersByUUID().remove(profile.id());observer.discard();
            }
        }
    }
    void drain(){
        var pending=List.copyOf(session.packets);session.packets.clear();
        for(BedrockPacket packet:pending){
            if(packet instanceof AddEntityPacket add){
                if(!add.getIdentifier().equals("advance_magic:legendary_block"))throw new AssertionError("unsupported Bedrock prop actor: "+add.getIdentifier());
                if(!ids.add(add.getRuntimeEntityId()))throw new AssertionError("duplicate native block runtime ID");
                Body body=new Body();body.position=add.getPosition();body.yaw=add.getRotation().getY();properties(body,add.getProperties());
                // Bedrock maps bone space to the world as (x, y, -z) at yaw 0; the rig's Molang needs the yaw-180 frame.
                if(Math.abs(add.getRotation().getY()-180)>1e-4||Math.abs(add.getHeadRotation()-180)>1e-4||Math.abs(add.getBodyRotation()-180)>1e-4)
                    throw new AssertionError("block rig must face 180 degrees (yaw/head/body): "+add.getRotation()+" "+add.getHeadRotation()+" "+add.getBodyRotation());
                if(body.pose[15]<=0||!Float.isFinite(body.pose[15])||body.revision<=0
                    ||add.getMetadata().getFlag(EntityFlag.HAS_GRAVITY)||add.getMetadata().getFlag(EntityFlag.HAS_COLLISION)||!add.getMetadata().getFlag(EntityFlag.NO_AI))throw new AssertionError("block rig lacks a valid full pose or cosmetic-only flags: "+add);
                bodies.put(add.getRuntimeEntityId(),body);materials.add(body.material);spawned++;
            }else if(packet instanceof MoveEntityAbsolutePacket move){
                Body body=bodies.get(move.getRuntimeEntityId());if(body!=null){body.position=move.getPosition();moved++;}
            }else if(packet instanceof SetEntityDataPacket update){
                Body body=bodies.get(update.getRuntimeEntityId());if(body!=null){
                    float[] last=body.pose.clone();float size=body.pose[15],x=body.pose[8],y=body.pose[9],z=body.pose[10];int revision=body.revision;properties(body,update.getProperties());
                    if(body.revision!=revision)for(int i=0;i<8;i++)if(Math.abs(last[8+i]-body.pose[i])>.00001)throw new AssertionError("interpolation lost the previous Java block pose");
                    if(body.pose[15]!=size)resized++;
                    if(body.pose[8]!=x||body.pose[9]!=y||body.pose[10]!=z)moved++;
                }
            }else if(packet instanceof RemoveEntityPacket remove){if(bodies.remove(remove.getUniqueEntityId())!=null)removed++;}
        }
        session.packets.clear();
    }
    void properties(Body body,EntityProperties properties){
        for(var value:properties.getFloatProperties())body.pose[floatChannels.get(value.getIndex())]=value.getValue();
        for(var value:properties.getIntProperties())if(value.getIndex()==materialIndex)body.material=value.getValue();else if(value.getIndex()==revisionIndex)body.revision=value.getValue();
    }
    @Override void frameCaptured(Frame frame){
        drain();if(!compare)return;
        int visible=0;
        for(var display:frame.displays()){
            String model=(String)display.get("model");if(!model.startsWith("block:"))continue;
            double size=num(display,"scale",0);if(size<.05)continue;visible++;
            Material material=Material.matchMaterial(model.substring(6));
            List<String> catalog;
            try(var reader=new java.io.InputStreamReader(magic.getResource("legendary-blocks.json"))){catalog=List.of(new com.google.gson.Gson().fromJson(reader,String[].class));}catch(Exception failure){throw new AssertionError(failure);}
            int expected=catalog.indexOf(material.getKey().getKey());
            Body match=bodies.values().stream().filter(body->body.material==expected&&Math.abs(body.pose[15]-size)<.002
                &&Math.abs(body.position.getX()+body.pose[8]-num(display,"pos",0))<.002&&Math.abs(body.position.getZ()+body.pose[10]-num(display,"pos",2))<.002
                &&Math.abs(body.position.getY()+body.pose[9]-num(display,"pos",1))<.002).findFirst().orElse(null);
            if(match==null)throw new AssertionError("no full-pose Bedrock block matches Java "+model+" size="+size+" at tick "+frame.tick()+"; actors="+bodies.size());
            double dot=0;for(int i=0;i<4;i++)dot+=match.pose[11+i]*num(display,"rot",i);
            if(Math.abs(Math.abs(dot)-1)>.00001)throw new AssertionError("Bedrock block lost Java's pitch/roll: "+model);
            if(match.revision>1&&Math.abs(match.pose[16]-((Number)display.get("ticks")).intValue()/20.0)>.00001)throw new AssertionError("native interpolation duration differs from Java");
            geometryCapture.add(Map.of("cast",currentCast,"quality",magic.getConfig().getString("elemental-visuals.quality"),"tick",frame.tick(),"java",display,"anchor",List.of(match.position.getX(),match.position.getY(),match.position.getZ()),"yaw",match.yaw,"pose",match.pose.clone(),"revision",match.revision));
            compared++;
        }
        if(bodies.size()!=visible)throw new AssertionError("native actor lifecycle differs from Java props: "+bodies.size()+" versus "+visible+" tick="+frame.tick());
    }
    @Override void parity(String name,List<Frame> frames){
        super.parity(name,frames);drain();check(bodies.isEmpty(),name+" native block actors all despawn after their cast");
    }
    void visibilityLifecycle()throws Exception{
        prepare(Element.LIGHT,ElementalSpell.HEAVENS_DESCENT);check(magic.elemental().cast(caster),"observer lifecycle uses a real Heaven's Descent cast");
        int[] peak={0},particles={0};record(12,peak,particles);check(!bodies.isEmpty(),"nearby Bedrock observer receives golden block actors");
        compare=false;observer.setPos(1000,100,.5);record(20,peak,particles);check(bodies.isEmpty(),"leaving visual range despawns every observer block actor");
        observer.setPos(3.5,100,.5);record(20,peak,particles);check(!bodies.isEmpty(),"returning observer receives fresh native actors during the same cast");
        compare=true;magic.elemental().close();magic.effects().close();drain();check(bodies.isEmpty(),"interruption removes native blocks immediately");
    }
}
