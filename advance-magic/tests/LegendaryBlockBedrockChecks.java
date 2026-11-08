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
public class LegendaryBlockBedrockChecks extends LegendarySpectacleChecks {
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
        @Override public void sendUpstreamPacket(BedrockPacket packet){if(packet instanceof AddEntityPacket||packet instanceof MoveEntityAbsolutePacket||packet instanceof SetEntityDataPacket||packet instanceof RemoveEntityPacket||packet instanceof SpawnParticleEffectPacket)packets.add(packet);}
    }
    // Pose layout: from_<channel> at [0,N), to_<channel> at [N,2N), duration at 2N.
    static final int N=com.example.advancemagic.spell.BedrockBlockModels.CHANNELS.size(),TO=N,DURATION=2*N,SIZE=7;
    static final class Body {Vector3f position;final float[] pose=new float[2*N+1];int material,revision;float yaw;}
    final Map<Long,Body> bodies=new HashMap<>();final Set<Long> ids=new HashSet<>();final Set<Integer> materials=new HashSet<>();
    final List<Map<String,Object>> geometryCapture=new ArrayList<>();
    final Map<Integer,Integer> floatChannels=new HashMap<>();int materialIndex,revisionIndex;
    String currentCast;
    @Override void prepare(Element element,ElementalSpell spell){currentCast=spell.id();super.prepare(element,spell);}
    Session session;ServerPlayer observer;long compared,spawned,moved,resized,removed;boolean compare=true;
    final Capture observerCapture=new Capture();final Map<String,Integer> bedrockSprites=new TreeMap<>();
    /** Java particle types the native renderer may send: each is a single particle on Bedrock (Geyser mappings). */
    static final Set<String> BEDROCK_SAFE=Set.of("flame","small_flame","soul_fire_flame","lava","snowflake","end_rod","electric_spark","splash",
        "bubble_pop","dripping_water","soul","sculk_soul","reverse_portal","squid_ink","happy_villager","heart","dust","falling_dust",
        "enchant","glow","totem_of_undying","current_down","scrape");
    @Override void run()throws Exception{
        magic=(AdvanceMagicPlugin)Bukkit.getPluginManager().getPlugin("advance-magic");world=Bukkit.getWorlds().getFirst();
        check(Bukkit.getPluginManager().isPluginEnabled("Geyser-Spigot"),"actual Geyser is enabled for native block packet checks");
        check(magic.blockModels().ready(),"full-pose Legendary cube rig and all entity properties register during Geyser startup");
        var schema=org.geysermc.geyser.registry.Registries.BEDROCK_ENTITY_DEFINITIONS.get().get(org.geysermc.geyser.api.util.Identifier.of("advance_magic:legendary_block")).registeredProperties();
        materialIndex=schema.getPropertyIndex("advance_magic:material");revisionIndex=schema.getPropertyIndex("advance_magic:revision");
        int index=0;for(String end:List.of("from_","to_"))for(String channel:com.example.advancemagic.spell.BedrockBlockModels.CHANNELS)floatChannels.put(schema.getPropertyIndex("advance_magic:"+end+channel),index++);
        floatChannels.put(schema.getPropertyIndex("advance_magic:duration"),DURATION);
        var server=MinecraftServer.getServer();var level=((CraftWorld)world).getHandle();world.getChunkAt(0,0).setForceLoaded(true);
        var profile=new GameProfile(UUID.randomUUID(),"BedrockBlocks");observer=new ServerPlayer(server,level,profile,ClientInformation.createDefault());
        observer.connection=new ServerGamePacketListenerImpl(server,observerCapture,observer,CommonListenerCookie.createInitial(profile,false)){
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
                if(body.pose[TO+SIZE]<=0||!Float.isFinite(body.pose[TO+SIZE])||body.revision<=0
                    ||add.getMetadata().getFlag(EntityFlag.HAS_GRAVITY)||add.getMetadata().getFlag(EntityFlag.HAS_COLLISION)||!add.getMetadata().getFlag(EntityFlag.NO_AI))throw new AssertionError("block rig lacks a valid full pose or cosmetic-only flags: "+add);
                bodies.put(add.getRuntimeEntityId(),body);materials.add(body.material);spawned++;
            }else if(packet instanceof MoveEntityAbsolutePacket move){
                Body body=bodies.get(move.getRuntimeEntityId());if(body!=null){body.position=move.getPosition();moved++;}
            }else if(packet instanceof SetEntityDataPacket update){
                Body body=bodies.get(update.getRuntimeEntityId());if(body!=null){
                    float[] last=body.pose.clone();float size=body.pose[TO+SIZE],x=body.pose[TO],y=body.pose[TO+1],z=body.pose[TO+2];int revision=body.revision;properties(body,update.getProperties());
                    if(body.revision!=revision)for(int i=0;i<N;i++)if(Math.abs(last[TO+i]-body.pose[i])>.00001)throw new AssertionError("interpolation lost the previous Java block pose");
                    if(body.pose[TO+SIZE]!=size)resized++;
                    if(body.pose[TO]!=x||body.pose[TO+1]!=y||body.pose[TO+2]!=z)moved++;
                }
            }else if(packet instanceof RemoveEntityPacket remove){if(bodies.remove(remove.getUniqueEntityId())!=null)removed++;}
            else if(packet instanceof SpawnParticleEffectPacket effect)bedrockSprites.merge(effect.getIdentifier(),1,Integer::sum);
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
            Body match=bodies.values().stream().filter(body->body.material==expected&&Math.abs(body.pose[TO+SIZE]-size)<.002
                &&Math.abs(body.position.getX()+body.pose[TO]-num(display,"pos",0))<.002&&Math.abs(body.position.getZ()+body.pose[TO+2]-num(display,"pos",2))<.002
                &&Math.abs(body.position.getY()+body.pose[TO+1]-num(display,"pos",1))<.002).findFirst().orElse(null);
            if(match==null)throw new AssertionError("no full-pose Bedrock block matches Java "+model+" size="+size+" at tick "+frame.tick()+"; actors="+bodies.size());
            double dot=0;for(int i=0;i<4;i++)dot+=match.pose[TO+3+i]*num(display,"rot",i);
            if(Math.abs(Math.abs(dot)-1)>.00001)throw new AssertionError("Bedrock block lost Java's pitch/roll: "+model);
            if(match.revision>1&&Math.abs(match.pose[DURATION]-((Number)display.get("ticks")).intValue()/20.0)>.00001)throw new AssertionError("native interpolation duration differs from Java");
            geometryCapture.add(Map.of("cast",currentCast,"quality",magic.getConfig().getString("elemental-visuals.quality"),"tick",frame.tick(),"java",display,"anchor",List.of(match.position.getX(),match.position.getY(),match.position.getZ()),"yaw",match.yaw,"pose",match.pose.clone(),"revision",match.revision));
            compared++;
        }
        if(bodies.size()!=visible)throw new AssertionError("native actor lifecycle differs from Java props: "+bodies.size()+" versus "+visible+" tick="+frame.tick());
    }
    /**
     * Ordinary (native-style) spells: every Java BlockDisplay prop must have a Bedrock cube with the same material,
     * centre, rotation and per-axis size in the same tick, and every cube must despawn with its spell.
     */
    @Override void afterLegendary()throws Exception{
        // Geyser plays each BLOCK-family particle as a whole block-break burst: the Bedrock dirt flood and stall.
        Set<String> breakBursts=Set.of("block","block_crumble","dust_pillar","dust_plume");
        long bursts=observerCapture.raw.stream().filter(p->breakBursts.contains(String.valueOf(p[0]).replace("minecraft:",""))).count();
        check(observerCapture.raw.size()>100&&bursts==0,"eight Legendary spectacles send no block-break particles to the Bedrock observer: "+bursts+" of "+observerCapture.raw.size());
        compare=false;long nativeCompared=0;Set<Integer> nativeMaterials=new HashSet<>();
        // lowQuality() leaves the config on "low", which draws particles instead of props.
        magic.getConfig().set("elemental-visuals.quality","balanced");
        List<String> catalog;
        try(var reader=new java.io.InputStreamReader(magic.getResource("legendary-blocks.json"))){catalog=List.of(new com.google.gson.Gson().fromJson(reader,String[].class));}
        Set<ElementalSpell> direct=EnumSet.of(ElementalSpell.CRYSTAL_PRISON,ElementalSpell.BUBBLE_PRISON);
        for(ElementalSpell spell:List.of(ElementalSpell.ICE_SPIKE,ElementalSpell.EARTH_SPIKE,ElementalSpell.STONE_WALL,ElementalSpell.GLACIAL_PATH,
                ElementalSpell.CRYSTAL_PRISON,ElementalSpell.FROST_BOLT,ElementalSpell.STONE_BULLET,ElementalSpell.AQUA_BULLET,ElementalSpell.RADIANT_BOLT,
                ElementalSpell.FROZEN_ARMOR,ElementalSpell.IRON_SKIN,ElementalSpell.AQUA_SHIELD,ElementalSpell.TIDAL_PUSH,ElementalSpell.FLAME_BURST,
                ElementalSpell.MAGMA_ERUPTION,ElementalSpell.HEALING_SPRING,ElementalSpell.QUICKSAND,ElementalSpell.SEISMIC_SLAM,ElementalSpell.INFERNO_SPEAR,
                ElementalSpell.TSUNAMI,ElementalSpell.FLAME_DASH)){
            currentCast=spell.id();prepare(spell.element(),spell);
            if(direct.contains(spell)||spell==ElementalSpell.FROST_BOLT||spell==ElementalSpell.STONE_BULLET||spell==ElementalSpell.AQUA_BULLET
                    ||spell==ElementalSpell.RADIANT_BOLT||spell==ElementalSpell.INFERNO_SPEAR)target(stage());
            check(magic.elemental().cast(caster),"native "+spell.id()+" casts with a Bedrock observer attached");
            long before=nativeCompared;int peak=0;
            for(int tick=0;tick<120;tick++){
                observerCapture.raw.clear();
                simulatedTick++;magic.effects().tick();magic.elemental().tick();
                long directPackets=session.packets.stream().filter(p->p instanceof SpawnParticleEffectPacket).count();
                drain();
                // Kit emitters and one-shot sprites are direct packets with their own per-viewer cap.
                if(directPackets>40)throw new AssertionError(spell.id()+" sends "+directPackets+" direct particle packets to one Bedrock viewer in a tick (cap 40)");
                int sent=0;
                for(Object[] particle:observerCapture.raw){
                    String type=String.valueOf(particle[0]).replace("minecraft:","");
                    if(!BEDROCK_SAFE.contains(type))throw new AssertionError(spell.id()+" sends "+type+" to a Bedrock viewer; Geyser turns it into an emitter");
                    sent+=Math.max(1,(int)particle[4]);
                }
                peak=Math.max(peak,sent+(int)directPackets);
                if(sent>140)throw new AssertionError(spell.id()+" sends "+sent+" particles to one Bedrock viewer in a tick (budget 140)");
                var props=world.getEntitiesByClass(BlockDisplay.class);
                if(props.size()!=bodies.size())throw new AssertionError(spell.id()+": Bedrock cubes ("+bodies.size()+") differ from Java props ("+props.size()+") at tick "+tick);
                for(BlockDisplay prop:props){
                    var t=prop.getTransformation();var q=t.getLeftRotation();var s3=t.getScale();var at=prop.getLocation();
                    int expected=catalog.indexOf(prop.getBlock().getMaterial().getKey().getKey());
                    if(expected<0)throw new AssertionError(spell.id()+" uses a prop material missing from the Bedrock catalog: "+prop.getBlock().getMaterial());
                    Body match=bodies.values().stream().filter(body->body.material==expected
                        &&Math.abs(body.position.getX()+body.pose[TO]-at.getX())<.002&&Math.abs(body.position.getY()+body.pose[TO+1]-at.getY())<.002&&Math.abs(body.position.getZ()+body.pose[TO+2]-at.getZ())<.002
                        &&Math.abs(body.pose[TO+SIZE+1]*body.pose[TO+SIZE]-s3.x)<.002&&Math.abs(body.pose[TO+SIZE+2]*body.pose[TO+SIZE]-s3.y)<.002&&Math.abs(body.pose[TO+SIZE+3]*body.pose[TO+SIZE]-s3.z)<.002)
                        .findFirst().orElse(null);
                    if(match==null)throw new AssertionError(spell.id()+": no Bedrock cube matches Java "+prop.getBlock().getMaterial()+" scale="+s3+" at "+at.toVector()+" tick "+tick);
                    double dot=match.pose[TO+3]*q.x+match.pose[TO+4]*q.y+match.pose[TO+5]*q.z+match.pose[TO+6]*q.w;
                    if(Math.abs(Math.abs(dot)-1)>.0001)throw new AssertionError(spell.id()+": Bedrock cube rotation differs from Java");
                    nativeMaterials.add(match.material);nativeCompared++;
                }
            }
            check(nativeCompared>before,spell.id()+" shows 3D props on both clients ("+(nativeCompared-before)+" matched poses)");
            check(peak>0,spell.id()+" stays within the Bedrock particle budget (peak "+peak+" per tick)");
            magic.elemental().close();magic.effects().close();drain();
            check(bodies.isEmpty()&&world.getEntitiesByClass(BlockDisplay.class).isEmpty(),spell.id()+" props despawn on both clients");
        }
        check(nativeMaterials.size()>=10,"ordinary spells use distinct prop materials on Bedrock: "+nativeMaterials.size());
        nativeSpriteBudgets();
        // Every sprite Bedrock is asked to draw must exist in the shipped Bedrock pack.
        Set<String> defined=new HashSet<>();
        try(var zip=new java.util.zip.ZipInputStream(magic.getResource("resource-packs/advance-magic-bedrock.mcpack"))){
            for(var entry=zip.getNextEntry();entry!=null;entry=zip.getNextEntry())if(entry.getName().startsWith("particles/")){
                var json=com.google.gson.JsonParser.parseString(new String(zip.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                defined.add(json.getAsJsonObject("particle_effect").getAsJsonObject("description").get("identifier").getAsString());
            }
        }
        var kits=bedrockSprites.keySet().stream().filter(id->id.startsWith("advance_magic:kit_")).toList();
        var shots=bedrockSprites.keySet().stream().filter(id->id.startsWith("advance_magic:fxs_")).toList();
        check(kits.size()>=20&&shots.size()>=10,"ordinary spells draw kit emitters and animated sprites on Bedrock: "+kits.size()+" kit and "+shots.size()+" sprite kinds");
        var missing=bedrockSprites.keySet().stream().filter(id->id.startsWith("advance_magic:")&&!defined.contains(id)).toList();
        check(missing.isEmpty(),"every Bedrock sprite identifier exists in the pack: missing "+missing);
        compare=true;
    }
    /** Count both the vanilla Java packets Geyser translates and the direct Bedrock sprite packets. */
    void nativeSpriteBudgets(){
        String[] keys={"max-native-particles-per-world-tick","max-native-particles-per-viewer-tick",
            "max-native-particles-per-bedrock-viewer-tick","max-native-packets-per-viewer-tick","max-native-displays-per-world",
            "max-bedrock-fx-packets-per-viewer-tick"};
        Object[] previous=Arrays.stream(keys).map(key->magic.getConfig().get("elemental-visuals."+key)).toArray();
        try{
            for(int mode=0;mode<3;mode++){
                magic.getConfig().set("elemental-visuals."+keys[0],mode==2?64:8000);
                magic.getConfig().set("elemental-visuals."+keys[1],2000);
                magic.getConfig().set("elemental-visuals."+keys[2],mode==0?16:2000);
                magic.getConfig().set("elemental-visuals."+keys[3],mode==1?16:512);
                magic.getConfig().set("elemental-visuals."+keys[4],0);
                magic.getConfig().set("elemental-visuals."+keys[5],8);
                observerCapture.raw.clear();drain();
                var renderer=new com.example.advancemagic.spell.NativeElementalVfx(magic.context());
                try{
                    // Twenty-four simultaneous casts each request sprites, dust, flame and lava.
                    for(int i=0;i<24;i++){
                        var scene=renderer.begin(caster,ElementalSpell.FIREBALL);
                        scene.startup();scene.area(stage(),3);
                    }
                    int vanilla=observerCapture.raw.stream().mapToInt(p->Math.max(1,(int)p[4])).sum();
                    long sprites=session.packets.stream().filter(p->p instanceof SpawnParticleEffectPacket).count();
                    long packets=observerCapture.raw.size()+sprites,total=vanilla+sprites;
                    String budget=mode==0?"viewer particle":mode==1?"viewer packet":"world particle";
                    check(vanilla>0&&sprites>0,"concurrent native "+budget+" pressure includes vanilla particles and direct Bedrock sprites");
                    // Translated vanilla particles keep the native caps; kit packets add at most their own cap (8 here).
                    check(sprites<=8&&(mode==1?observerCapture.raw.size()<=4:vanilla<=(mode==0?16:64)),
                        "shared native "+budget+" cap bounds mixed Bedrock traffic: particles="+total+", packets="+packets);
                    // Advancing scene time inside one Bukkit tick cannot reset either sender's quota.
                    for(int i=0;i<6;i++)renderer.tick();
                    long after=observerCapture.raw.size()+session.packets.stream().filter(p->p instanceof SpawnParticleEffectPacket).count();
                    check(after==packets,"manual redraws cannot bypass the shared native "+budget+" cap");
                }finally{renderer.close();drain();observerCapture.raw.clear();}
            }
        }finally{for(int i=0;i<keys.length;i++)magic.getConfig().set("elemental-visuals."+keys[i],previous[i]);}
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
