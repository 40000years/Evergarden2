import com.example.voidscape.VoidscapePlugin;
import com.example.voidscape.boss.*;
import com.example.voidscape.world.BossStructureLayout;
import com.ticxo.modelengine.api.ModelEngineAPI;
import com.ticxo.modelengine.api.model.*;
import com.ticxo.modelengine.api.animation.handler.IStateMachineHandler;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.server.network.*;
import org.bukkit.*;
import org.bukkit.block.BlockFace;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.entity.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.io.*;
import java.util.zip.*;

/** Boots actual ME Free, LM and Geyser; exercises the production summon and rig. */
public class ArkhaiaModelEngineProbe extends JavaPlugin {
    VoidscapePlugin garden;World world;Player player;ServerPlayer actor;Slime shell;
    ArkhaiaModelSupport.Rig rig;ModeledEntity modeled;ActiveModel model;IStateMachineHandler handler;
    final List<net.minecraft.network.protocol.Packet<?>> sentPackets=new ArrayList<>();
    long hitSounds(){return sentPackets.stream().filter(p->p instanceof net.minecraft.network.protocol.game.ClientboundSoundPacket).count();}
    Object run;int checks,waitTicks;String originalPack;final List<String> lines=new ArrayList<>();
    void check(boolean ok,String label){if(!ok)throw new AssertionError(label);checks++;lines.add("PASS: "+label);getLogger().info("PASS: "+label);}
    void later(long ticks,Runnable work){Bukkit.getScheduler().runTaskLater(this,()->{try{work.run();}catch(Throwable e){finish(e);}},ticks);}
    @Override public void onEnable(){later(1,()->{
        garden=(VoidscapePlugin)Bukkit.getPluginManager().getPlugin("Evergarden");
        originalPack=garden.judgePack().url(null);awaitImport();
    });}
    void awaitImport(){
        if(garden.arkhaiaModels().ready()){prepare();return;}
        if((waitTicks+=10)>1800)throw new AssertionError("ME import/pack did not become ready");
        later(10,this::awaitImport);
    }
    Object field(Object object,String name)throws Exception{var f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}
    void prepare(){try{
        for(String name:List.of("ModelEngine","Evergarden","advance-magic","LevelledMobs","Geyser-Spigot","floodgate"))
            check(Bukkit.getPluginManager().isPluginEnabled(name),name+" enabled on "+Bukkit.getVersion());
        check(garden.arkhaiaBedrock().ready(),"native Bedrock entity registered before Geyser startup");
        var bedrockDefinition=org.geysermc.geyser.registry.Registries.BEDROCK_ENTITY_DEFINITIONS.get(org.geysermc.geyser.api.util.Identifier.of("voidscape:arkhaia"));
        check(bedrockDefinition!=null,"Arkhaia has its own Bedrock entity definition");
        var blueprint=ModelEngineAPI.getBlueprint("arkhaia");check(blueprint!=null,"native Arkhaia blueprint imported by official free jar");
        check(blueprint.getAnimations().size()==25&&blueprint.getAnimations().containsKey("aura"),"all 24 worldboss/locomotion clips plus the aura loop imported");
        var seraphonBlueprint=ModelEngineAPI.getBlueprint("seraphon");
        check(seraphonBlueprint!=null&&seraphonBlueprint.getAnimations().size()==19,"Seraphon blueprint imported with 18 clips plus aura");
        check(org.geysermc.geyser.registry.Registries.BEDROCK_ENTITY_DEFINITIONS.get(org.geysermc.geyser.api.util.Identifier.of("voidscape:seraphon"))!=null,"Seraphon has its own Bedrock entity definition");
        try(var bundled=garden.getResource("modelengine/arkhaia.bbmodel")){
            check(Arrays.equals(bundled.readAllBytes(),Files.readAllBytes(Path.of("plugins/ModelEngine/blueprints/arkhaia.bbmodel"))),
                "active blueprint matches the rig shipped in the running JAR");
        }
        check(Files.readString(garden.getDataFolder().toPath().resolve("modelengine/arkhaia.sha256")).trim().length()==64,"managed blueprint ownership recorded for safe upgrades");
        String pack=garden.judgePack().url(null);check(!pack.equals(originalPack),"merged pack receives a new content hash");
        byte[] bytes=URI.create(pack).toURL().openStream().readAllBytes();Set<String> entries=new HashSet<>();
        try(var zip=new ZipInputStream(new ByteArrayInputStream(bytes))){for(ZipEntry e;(e=zip.getNextEntry())!=null;)entries.add(e.getName());}
        check(entries.stream().anyMatch(s->s.startsWith("assets/modelengine/")&&s.contains("arkhaia")),"served pack contains articulated Arkhaia assets");
        check(entries.contains("assets/voidscape/items/arkhaia_orbit.json"),"original violet effects remain in merged pack");
        check(URI.create(originalPack).toURL().openConnection().getInputStream().read()>-1,"previous pack hash still downloads during transition");
        check(entries.stream().noneMatch(s->s.startsWith("assets/minecraft/shaders/")),"no unrelated player shaders injected");
        world=garden.world();var site=garden.bossStructures().nearest(BossStructureLayout.Kind.ARKHAIA,0,0,40);
        check(site!=null,"production Arkhaia arena found");
        for(int x=(site.x()-16)>>4;x<=(site.x()+16)>>4;x++)for(int z=(site.z()-16)>>4;z<=(site.z()+16)>>4;z++)world.addPluginChunkTicket(x,z,this);
        // Pursuit/charge tests travel beyond the small GUI staging pad. Keep
        // their whole route level rather than depending on a random arena seed.
        for(int x=-40;x<=40;x++)for(int z=-40;z<=40;z++){
            world.getBlockAt(site.x()+x,108,site.z()+z).setType(Material.RED_GLAZED_TERRACOTTA,false);
            for(int y=109;y<=122;y++)world.getBlockAt(site.x()+x,y,site.z()+z).setType(Material.AIR,false);
        }
        var server=MinecraftServer.getServer();var level=((CraftWorld)world).getHandle();var profile=new GameProfile(UUID.randomUUID(),"ArkhaiaRigTest");
        actor=new ServerPlayer(server,level,profile,ClientInformation.createDefault());
        var connection=new Connection(PacketFlow.SERVERBOUND);connection.channel=new EmbeddedChannel();connection.address=new InetSocketAddress("127.0.0.1",1);
        actor.connection=new ServerGamePacketListenerImpl(server,connection,actor,CommonListenerCookie.createInitial(profile,false)){
            @Override public boolean hasClientLoaded(){return true;}
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet){sentPackets.add(packet);super.send(packet);}
        };
        actor.setPos(site.x()+.5,109,site.z()+6.5);server.getPlayerList().getPlayers().add(actor);server.getPlayerList().getPlayersByUUID().put(profile.id(),actor);level.addNewPlayer(actor);
        player=actor.getBukkitEntity();player.setGravity(false);player.setInvulnerable(true);player.setGameMode(GameMode.SURVIVAL);
        Bukkit.getPluginManager().callEvent(new PlayerResourcePackStatusEvent(player,JudgePackService.PACK_ID,PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED));
        player.getInventory().setItemInMainHand(new ItemStack(Material.NETHERITE_BLOCK));
        Bukkit.getPluginManager().callEvent(new PlayerInteractEvent(player,Action.RIGHT_CLICK_BLOCK,player.getInventory().getItemInMainHand(),world.getBlockAt(site.x(),108,site.z()),BlockFace.UP,EquipmentSlot.HAND));
        Object boss=field(garden.worldBoss(),"arkhaia");run=((Map<?,?>)field(boss,"active")).values().iterator().next();
        check((double)field(run,"health")==7000,"production summon retains 7000 HP");
        shell=(Slime)field(run,"target");check(shell.getScoreboardTags().contains("evergarden_unique"),"unique shell retained for LevelledMobs exclusion");
        later(25,()->inspect(boss));
    }catch(Exception e){throw new RuntimeException(e);}}
    void inspect(Object boss){try{
        rig=(ArkhaiaModelSupport.Rig)field(run,"rig");check(rig!=null,"real encounter attached Model Engine rig");
        modeled=ModelEngineAPI.getModeledEntity(shell.getUniqueId());check(modeled!=null,"modeled entity registered with ME");
        model=modeled.getModel("arkhaia").orElseThrow();handler=(IStateMachineHandler)model.getAnimationHandler();
        check(model.getBones().size()>=24,"all limb, wing and sword marker bones generated");
        check(Math.abs(model.getScale().x()-3.2)<.001,"enlarged boss scale retained");
        check(!player.canSee((Entity)field(run,"bedrock"))&&((List<?>)field(run,"parts")).stream().noneMatch(ItemDisplay.class::isInstance),"Java sees only the rig; no legacy static item model part remains");
        check(handler.isPlayingAnimation(1,"aura"),"aura loop runs on its own lane under the combat clip");
        check(shell.getSize()==8,"ME does not replace encounter hitbox size");
        check(shell.isCollidable(),"real ME shell stays pickable for projectile collisions");
        double before=(double)field(run,"health");Location origin=shell.getLocation().clone().add(0,1.5,10);player.teleport(origin.clone().subtract(0,1.5,0));
        var velocity=shell.getLocation().clone().add(0,1,0).toVector().subtract(origin.toVector()).normalize();
        var arrow=world.spawnArrow(origin,velocity,3,0);arrow.setShooter(player);arrow.setGravity(false);
        for(int i=0;i<8&&arrow.isValid();i++)((org.bukkit.craftbukkit.entity.CraftArrow)arrow).getHandle().tick();arrow.remove();
        check((double)field(run,"health")<before,"moving arrow damages boss with Model Engine and real LevelledMobs installed");
        Object minions=field(run,"minions");var spirits=(Map<?,?>)field(minions,"spirits");check(spirits.size()==1,"real plugin stack summons one spirit for solo player");
        for(Object spirit:spirits.values()){
            Mob mob=(Mob)field(spirit,"mob");check(mob.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue()==800
                &&com.example.voidscape.compat.LevelledMobsCompat.isEvergardenMob(mob),"actual LevelledMobs preserves unique minion at 800 HP");
        }

        check(!modeled.shouldBeSaved(),"temporary encounter is not saved by ME");
        check((boolean)field(run,"bedrockNative"),"real summon selects native Bedrock rig");
        var carrier=(ArmorStand)field(run,"bedrock");
        var helmet=carrier.getEquipment().getHelmet();
        lines.add("Native Bedrock visible="+carrier.isVisible()+" helmet="+helmet);
        check(carrier.isVisible()&&(helmet==null||helmet.isEmpty()),"native Bedrock carrier avoids helmet frame swaps and invisible model flags");
        check(model.getBones().get("gaze").hasBoneBehaviors(),"head tracking behavior imported on gaze pivot");
        var gaze=(ArkhaiaLook.Gaze)field(run,"gaze");check(gaze.pitch()>10,"production gaze looks down toward player eye height");
        bedrockSpawn(carrier);
        ((org.bukkit.scheduler.BukkitTask)field(boss,"driver")).cancel();
        Object brain=field(run,"brain");
        for(var change:Map.of("state",ArkhaiaBrain.State.ORBIT,"action",ArkhaiaBrain.Action.NONE,"duration",0,"nextDecision",Long.MAX_VALUE).entrySet()){
            var f=brain.getClass().getDeclaredField(change.getKey());f.setAccessible(true);f.set(brain,change.getValue());
        }
        Location center=((Location)field(run,"center")).clone();
        for(int heading:List.of(180,270,0,90)){
            double angle=Math.toRadians(heading);player.teleport(center.clone().add(-Math.sin(angle)*9,0,Math.cos(angle)*9));
            for(int n=0;n<16;n++)garden.worldBoss().tickArkhaia();
            Location body=(Location)field(run,"position");
            var desired=player.getLocation().toVector().subtract(body.toVector()).setY(0).normalize();
            check(body.getDirection().setY(0).normalize().dot(desired)>.98,"production AI turns toward heading "+heading+" within 16 ticks");
            check(Math.abs(Math.IEEEremainder(model.getYBodyRot()-body.getYaw(),360))<.01,"rendered body matches production AI heading "+heading);
            check(carrier.getLocation().distanceSquared(body)<1e-6&&Math.abs(Math.IEEEremainder(carrier.getLocation().getYaw()-body.getYaw(),360))<.01,
                "native Bedrock carrier follows combat position and yaw at heading "+heading);
        }
        // No real client acknowledges chunks in this test. Supply a viewer
        // fixture on ME's worker, before it evaluates bones. The production
        // backend still uses normal tracking and per-player culling.
        modeled.registerTickTask(ModeledEntity.Phase.PRE_MODEL_TICK,(java.util.function.Consumer<ModeledEntity>) e->
            e.getBase().getData().getMutableTracking().put(player.getUniqueId(),com.ticxo.modelengine.api.entity.CullType.NO_CULL));
        heading(0);
    }catch(Exception e){throw new RuntimeException(e);}}
    void heading(int index){
        if(index==4){checkGaze(0);return;}
        Location pose=shell.getLocation();pose.setYaw(index*90);
        rig.frame(new ArkhaiaBrain.Frame(ArkhaiaBrain.State.IDLE,ArkhaiaBrain.Action.NONE,0,0,false),0,false,pose);
        check(Math.abs(Math.IEEEremainder(model.getYBodyRot()-pose.getYaw(),360))<.01,"effective ME body follows combat yaw "+pose.getYaw());
        later(6,()->{
            Location front=model.getBones().get("facing").getLocation();
            var direction=front.toVector().subtract(shell.getLocation().toVector()).setY(0).normalize();
            var expected=pose.getDirection().setY(0).normalize();
            lines.add("Facing "+pose.getYaw()+": "+direction+" dot="+direction.dot(expected));
            check(direction.dot(expected)>.98,"rendered north-facing model points along Bukkit forward at "+pose.getYaw());
            heading(index+1);
        });
    }
    void checkGaze(int index){
        float[][] poses={{-45,30},{45,30},{0,-20},{0,0}};
        if(index==poses.length){rig.look(0,0);beginSwing();return;}
        float yaw=poses[index][0],pitch=poses[index][1];Location at=shell.getLocation();at.setYaw(0);
        rig.look(yaw,pitch);rig.frame(new ArkhaiaBrain.Frame(ArkhaiaBrain.State.IDLE,ArkhaiaBrain.Action.NONE,0,0,false),0,false,at);
        later(6,()->{
            var head=model.getBones().get("head").getLocation();
            var direction=model.getBones().get("head_forward").getLocation().toVector().subtract(head.toVector()).normalize();
            var expected=new Location(world,0,0,0,yaw,pitch).getDirection();
            lines.add("Gaze "+yaw+"/"+pitch+" direction="+direction+" dot="+direction.dot(expected));
            check(direction.dot(expected)>.97,"rendered head looks toward yaw/pitch "+yaw+"/"+pitch);
            check(Math.abs(model.getYBodyRot())<.01,"looking around leaves the attack body direction unchanged");
            checkGaze(index+1);
        });
    }
    void bedrockSpawn(ArmorStand carrier)throws Exception{
        var api=org.geysermc.geyser.api.GeyserApi.api();
        class Spawn extends org.geysermc.geyser.api.event.java.ServerSpawnEntityEvent {
            final UUID id;boolean cancelled;org.geysermc.geyser.api.entity.definition.GeyserEntityDefinition definition;
            java.util.function.Consumer<org.geysermc.geyser.api.entity.type.GeyserEntity> consumer;
            Spawn(UUID id){super(null);this.id=id;}
            public UUID uuid(){return id;}public int entityId(){return carrier.getEntityId();}
            public org.geysermc.geyser.api.entity.definition.JavaEntityType entityType(){return null;}
            public org.geysermc.geyser.api.entity.definition.GeyserEntityDefinition definition(){return definition;}
            public void definition(org.geysermc.geyser.api.entity.definition.GeyserEntityDefinition value){definition=value;}
            public void preSpawnConsumer(java.util.function.Consumer<org.geysermc.geyser.api.entity.type.GeyserEntity> value){consumer=value;}
            public boolean isCancelled(){return cancelled;}public void setCancelled(boolean value){cancelled=value;}
        }
        Spawn other=new Spawn(UUID.randomUUID());api.eventBus().fire(other);
        check(other.definition==null&&other.consumer==null,"Geyser replacement leaves unrelated entities unchanged");
        Spawn event=new Spawn(carrier.getUniqueId());api.eventBus().fire(event);
        check(event.definition!=null&&event.definition.identifier().toString().equals("voidscape:arkhaia")&&event.consumer!=null,"real Geyser spawn event selects native boss entity");
        var values=new HashMap<Object,Object>();var data=new HashMap<Object,Object>();
        var entity=(org.geysermc.geyser.api.entity.type.GeyserEntity)java.lang.reflect.Proxy.newProxyInstance(getClassLoader(),
            new Class<?>[]{org.geysermc.geyser.api.entity.type.GeyserEntity.class},(proxy,method,args)->{
                if(method.getName().equals("override")&&args.length==2){data.put(args[0],args[1]);return null;}
                if(method.getName().equals("updatePropertiesBatched")){
                    ((java.util.function.Consumer<org.geysermc.geyser.api.entity.property.BatchPropertyUpdater>)args[0]).accept(new org.geysermc.geyser.api.entity.property.BatchPropertyUpdater(){
                        public <T>void update(org.geysermc.geyser.api.entity.property.GeyserEntityProperty<T> property,T value){values.put(property,value);}
                    });return null;
                }return null;
            });
        event.consumer.accept(entity);
        check(values.size()==5,"pre-spawn snapshot sends all animation and independent gaze properties in one batch");
        check(data.get(org.geysermc.geyser.api.entity.data.GeyserEntityDataTypes.VERTICAL_OFFSET).equals(0f),"Bedrock rig is aligned to encounter feet");
        check(data.get(org.geysermc.geyser.api.entity.data.GeyserEntityDataTypes.WIDTH).equals(0f),"visual carrier retains the real slime as damage receiver");
        try(var pack=new ZipFile(garden.getDataFolder().toPath().resolve("resource-packs/evergarden-bedrock.mcpack").toFile())){
            check(pack.getEntry("entity/arkhaia_native.entity.json")!=null&&pack.getEntry("animations/arkhaia_native.animation.json")!=null,"delivered Bedrock pack contains native entity and skeletal clips");
        }
    }
    void beginSwing(){
        int ticks=ArkhaiaCombatTiming.of(ArkhaiaBrain.Action.HEAVY).windup();
        rig.frame(new ArkhaiaBrain.Frame(ArkhaiaBrain.State.WINDUP,ArkhaiaBrain.Action.HEAVY,0,ticks,false),0,false,shell.getLocation());
        check(handler.isPlayingAnimation(10,"heavy_windup"),"heavy windup selects skeletal clip");
        later(ticks+2,this::swing);
    }
    void swing(){
        lines.add("ME tracking: "+modeled.getBase().getData().getTracking()+" active="+modeled.getBase().getData().hasTracking());
        check(modeled.getBase().getData().hasTracking(),"headless viewer fixture enables normal bone evaluation");
        lines.add("Windup playback seconds: "+handler.getAnimation(10,"heavy_windup").getTime());
        var bone=model.getBones().get("arm_r");var before=new org.joml.Quaternionf(bone.getGlobalTransform().getSafeLeftQuaternion());
        var halo=model.getBones().get("dread_halo");var haloBefore=new org.joml.Quaternionf(halo.getGlobalTransform().getSafeLeftQuaternion());
        Location tip=rig.swordTip();check(tip!=null&&Double.isFinite(tip.getY()),"sword tip location resolves for attached effects");
        var forward=shell.getLocation().getDirection().setY(0).normalize();
        var relative=tip.toVector().subtract(shell.getLocation().toVector());
        lines.add("Windup sword relative="+relative+" forward="+relative.dot(forward));
        check(relative.dot(forward)<-1,"backswing holds the blade behind the boss before release");
        check(relative.getY()>5,"windup raises the blade above the chest");
        int ticks=ArkhaiaCombatTiming.of(ArkhaiaBrain.Action.HEAVY).active();
        rig.frame(new ArkhaiaBrain.Frame(ArkhaiaBrain.State.ACTIVE,ArkhaiaBrain.Action.HEAVY,0,ticks,false),0,false,shell.getLocation());
        check(handler.isPlayingAnimation(10,"heavy_active"),"windup transitions to heavy release");
        later(ticks+2,()->{
            check(Math.abs(before.dot(bone.getGlobalTransform().getSafeLeftQuaternion()))<.999,"bone transforms change over actual server ticks");
            check(Math.abs(haloBefore.dot(halo.getGlobalTransform().getSafeLeftQuaternion()))<.9999,"dread halo keeps turning on the aura lane during the swing");
            check(tip.distance(rig.swordTip())>.1,"sword tip moves with the articulated swing");
            var release=rig.swordTip().toVector().subtract(shell.getLocation().toVector());
            lines.add("Contact sword relative="+release+" forward="+release.dot(forward));
            check(release.dot(forward)>3,"released sword reaches forward toward the player");
            check(release.getY()>.8&&release.getY()<2.5,"released blade drops to standing player height");
            check(Math.abs(release.dot(new org.bukkit.util.Vector(forward.getZ(),0,-forward.getX())))<.6,"released blade stays near the forward target center");
            checkCasting(0);
        });
    }
    void checkCasting(int index){
        if(index==4){finishClips();return;}
        var action=index<2?ArkhaiaBrain.Action.RIFT:ArkhaiaBrain.Action.NOVA;
        var state=index%2==0?ArkhaiaBrain.State.WINDUP:ArkhaiaBrain.State.ACTIVE;
        var clock=ArkhaiaCombatTiming.of(action);int ticks=state==ArkhaiaBrain.State.WINDUP?clock.windup():clock.active();
        rig.frame(new ArkhaiaBrain.Frame(state,action,0,ticks,false),0,false,shell.getLocation());
        later(ticks+3,()->{
            var hand=model.getBones().get("hand_l").getLocation().toVector().subtract(shell.getLocation().toVector());
            var forward=shell.getLocation().getDirection().setY(0).normalize();
            lines.add(action+"/"+state+" hand relative="+hand+" forward="+hand.dot(forward));
            check(hand.dot(forward)>1.2,"casting hand stays in front during "+action+"/"+state);
            check(hand.getY()>3,"casting hand is raised to casting height during "+action+"/"+state);
            checkCasting(index+1);
        });
    }
    void finishClips(){
            for(ArkhaiaBrain.Action a:List.of(ArkhaiaBrain.Action.LIGHT,ArkhaiaBrain.Action.HEAVY,ArkhaiaBrain.Action.RIFT,ArkhaiaBrain.Action.NOVA,ArkhaiaBrain.Action.PORTALS,ArkhaiaBrain.Action.ECLIPSE))
                for(ArkhaiaBrain.State s:List.of(ArkhaiaBrain.State.WINDUP,ArkhaiaBrain.State.ACTIVE,ArkhaiaBrain.State.RECOVER)){
                    rig.frame(new ArkhaiaBrain.Frame(s,a,0,20,false),0,false,shell.getLocation());
                    check(handler.isPlayingAnimation(10,a.name().toLowerCase()+"_"+s.name().toLowerCase()),"state selects "+a+"/"+s);
                }
            rig.frame(new ArkhaiaBrain.Frame(ArkhaiaBrain.State.STUN,ArkhaiaBrain.Action.NONE,0,14,false),0,false,shell.getLocation());
            check(handler.isPlayingAnimation(10,"stun"),"heavy hit selects stun clip");
            rig.frame(new ArkhaiaBrain.Frame(ArkhaiaBrain.State.ORBIT,ArkhaiaBrain.Action.NONE,0,0,false),.1,true,shell.getLocation());
            check(handler.isPlayingAnimation(10,"hover"),"airborne locomotion selects wing animation");
            showcaseTests();
    }
    Map<?,?> shows()throws Exception{
        var all=(Map<?,?>)field(field(garden.worldBoss(),"showcase"),"active");var mine=(Map<?,?>)all.get(player.getUniqueId());
        return mine==null?Map.of():mine;
    }
    void showcaseTests(){try{
        player.setOp(false);check(!garden.worldBoss().spawnBossShowcase(player),"non-admin cannot spawn main-boss showcases");
        player.setOp(true);Location center=((Location)field(run,"center")).clone();
        Location stand=center.clone().add(-18,0,0);stand.setYaw(90);stand.setPitch(0);player.teleport(stand);
        check(garden.worldBoss().spawnBossShowcase(player),"admin spawns Arkhaia and Seraphon showcases side by side");
        check(shows().size()==2,"one Arkhaia and one Seraphon showcase owned by the admin");
        for(Object show:shows().values()){
            Slime anchor=(Slime)field(show,"anchor");Object kind=field(show,"kind");String id=(String)field(kind,"model");
            var m=ModelEngineAPI.getModeledEntity(anchor.getUniqueId()).getModel(id).orElseThrow();
            check(Math.abs(m.getScale().x()-3.2)<.001&&anchor.isInvisible()&&!anchor.hasAI()&&anchor.isInvulnerable(),"harmless invisible anchor carries the "+id+" rig at scale 3.2");
            check(((IStateMachineHandler)m.getAnimationHandler()).isPlayingAnimation(1,"aura"),id+" aura lane plays");
            ModelEngineAPI.getModeledEntity(anchor.getUniqueId()).registerTickTask(ModeledEntity.Phase.PRE_MODEL_TICK,(java.util.function.Consumer<ModeledEntity>)
                e->e.getBase().getData().getMutableTracking().put(player.getUniqueId(),com.ticxo.modelengine.api.entity.CullType.NO_CULL));
            var carrier=(ArmorStand)field(show,"carrier");check(carrier!=null&&garden.arkhaiaBedrock().registered(carrier.getUniqueId()),id+" native Bedrock carrier registered");
            var actor=(((Map<?,?>)field(garden.arkhaiaBedrock(),"actors")).get(carrier.getUniqueId()));check(field(actor,"model").equals(id),"Bedrock actor maps "+id);
        }
        later(8,this::showcaseMotion);
    }catch(Exception e){throw new RuntimeException(e);}}
    void showcaseMotion(){try{
        Map<String,org.joml.Quaternionf> aura=new HashMap<>();
        for(Object show:shows().values()){
            Slime anchor=(Slime)field(show,"anchor");String id=(String)field(field(show,"kind"),"model");
            var m=ModelEngineAPI.getModeledEntity(anchor.getUniqueId()).getModel(id).orElseThrow();var h=(IStateMachineHandler)m.getAnimationHandler();
            String first=id.equals("arkhaia")?"idle":"spawn";
            check(h.isPlayingAnimation(10,first),"showcase program opens with "+first+" for "+id);
            aura.put(id,new org.joml.Quaternionf(m.getBones().get(id.equals("arkhaia")?"dread_halo":"halo_ring").getGlobalTransform().getSafeLeftQuaternion()));
            check(m.getBones().containsKey("fx_core")&&m.getBones().containsKey("head_forward"),"aura emitter and gaze markers imported for "+id);
            var carrier=(ArmorStand)field(show,"carrier");
            var snapshot=(ArkhaiaBedrockSupport.Snapshot)field(((Map<?,?>)field(garden.arkhaiaBedrock(),"actors")).get(carrier.getUniqueId()),"snapshot");
            check(snapshot.clip()==ArkhaiaBedrockSupport.clips(id).indexOf(first),"Bedrock snapshot follows the showcase clip for "+id);
        }
        garden.testGui().openMainBosses(player);
        check(player.getOpenInventory().getTopInventory().getHolder().getClass().getSimpleName().equals("MainBossHolder"),"main-boss control menu opens");
        clickGui(24);
        later(4,()->{try{
            for(Object show:shows().values()){
                Slime anchor=(Slime)field(show,"anchor");String id=(String)field(field(show,"kind"),"model");
                var m=ModelEngineAPI.getModeledEntity(anchor.getUniqueId()).getModel(id).orElseThrow();
                String ultimate=id.equals("arkhaia")?"eclipse_windup":"verdict_windup";
                check(((IStateMachineHandler)m.getAnimationHandler()).isPlayingAnimation(10,ultimate),"GUI ultimate button plays "+ultimate+" on "+id);
                var now=m.getBones().get(id.equals("arkhaia")?"dread_halo":"halo_ring").getGlobalTransform().getSafeLeftQuaternion();
                check(Math.abs(aura.get(id).dot(now))<.9999,id+" aura bone keeps moving across clip changes");
            }
            Object seraphon=null;
            for(Object show:shows().values())if(field(field(show,"kind"),"model").equals("seraphon"))seraphon=show;
            bedrockBossSpawn((ArmorStand)field(seraphon,"carrier"),"seraphon");
            List<UUID> anchors=new ArrayList<>();for(Object show:shows().values())anchors.add(((Slime)field(show,"anchor")).getUniqueId());
            garden.testGui().openMainBosses(player);clickGui(16);
            later(10,()->{try{
                check(shows().isEmpty(),"GUI remove clears both showcases");
                for(UUID anchor:anchors)check(Bukkit.getEntity(anchor)==null&&ModelEngineAPI.getModeledEntity(anchor)==null,"showcase anchor and ME renderer removed");
                riverGuiTests();
            }catch(Exception e){throw new RuntimeException(e);}});
        }catch(Exception e){throw new RuntimeException(e);}});
    }catch(Exception e){throw new RuntimeException(e);}}
    void bedrockBossSpawn(ArmorStand carrier,String id)throws Exception{
        class Spawn extends org.geysermc.geyser.api.event.java.ServerSpawnEntityEvent {
            org.geysermc.geyser.api.entity.definition.GeyserEntityDefinition definition;
            java.util.function.Consumer<org.geysermc.geyser.api.entity.type.GeyserEntity> consumer;
            Spawn(){super(null);}
            public UUID uuid(){return carrier.getUniqueId();}public int entityId(){return carrier.getEntityId();}
            public org.geysermc.geyser.api.entity.definition.JavaEntityType entityType(){return null;}
            public org.geysermc.geyser.api.entity.definition.GeyserEntityDefinition definition(){return definition;}
            public void definition(org.geysermc.geyser.api.entity.definition.GeyserEntityDefinition value){definition=value;}
            public void preSpawnConsumer(java.util.function.Consumer<org.geysermc.geyser.api.entity.type.GeyserEntity> value){consumer=value;}
            public boolean isCancelled(){return false;}public void setCancelled(boolean value){}
        }
        Spawn event=new Spawn();org.geysermc.geyser.api.GeyserApi.api().eventBus().fire(event);
        check(event.definition!=null&&event.definition.identifier().toString().equals("voidscape:"+id),"real Geyser spawn selects the native "+id+" entity");
        var values=new HashMap<Object,Object>();
        var proxy=(org.geysermc.geyser.api.entity.type.GeyserEntity)java.lang.reflect.Proxy.newProxyInstance(getClassLoader(),new Class<?>[]{org.geysermc.geyser.api.entity.type.GeyserEntity.class},(instance,method,args)->{
            if(method.getName().equals("updatePropertiesBatched")){((java.util.function.Consumer<org.geysermc.geyser.api.entity.property.BatchPropertyUpdater>)args[0]).accept(new org.geysermc.geyser.api.entity.property.BatchPropertyUpdater(){public <T>void update(org.geysermc.geyser.api.entity.property.GeyserEntityProperty<T> property,T value){values.put(property,value);}});}return null;
        });event.consumer.accept(proxy);check(values.size()==5,"native "+id+" spawn batches clip, clock and gaze properties");
    }
    void clickGui(int slot){Bukkit.getPluginManager().callEvent(new org.bukkit.event.inventory.InventoryClickEvent(player.getOpenInventory(),org.bukkit.event.inventory.InventoryType.SlotType.CONTAINER,slot,org.bukkit.event.inventory.ClickType.LEFT,org.bukkit.event.inventory.InventoryAction.PICKUP_ALL));}
    Object riverController()throws Exception{var demos=(Map<?,?>)field(field(garden.worldBoss(),"arkhaia"),"demos");return field(demos.get(player.getUniqueId()),"minions");}
    Map<?,?> riverSpirits()throws Exception{return (Map<?,?>)field(riverController(),"spirits");}
    void stepRivers(int ticks){for(int i=0;i<ticks;i++)garden.worldBoss().tickArkhaia();}
    void riverGuiTests(){try{
        for(var kind:ArkhaiaWaves.Kind.values()){
            String id="arkhaia_"+kind.name().toLowerCase(Locale.ROOT);var bp=ModelEngineAPI.getBlueprint(id);
            // The Bedrock clip table is the contract each river blueprint must import completely;
            // an optional 'aura' loop plays on its own lane and is not part of the table.
            var table=ArkhaiaBedrockSupport.riverClips(id);
            check(bp!=null&&bp.getAnimations().keySet().containsAll(table)
                &&bp.getAnimations().size()==table.size()+(bp.getAnimations().containsKey("aura")?1:0),"R4.1.1 imports full native river blueprint "+kind);
            check(org.geysermc.geyser.registry.Registries.BEDROCK_ENTITY_DEFINITIONS.get(org.geysermc.geyser.api.util.Identifier.of("voidscape:"+id))!=null,"Geyser registers native river definition "+kind);
        }
        player.setOp(true);player.setGameMode(GameMode.CREATIVE);Location center=((Location)field(run,"center")).clone();player.teleport(center.clone().add(0,0,-5));player.setRotation(0,0);
        garden.testGui().open(player);check(player.getOpenInventory().getTopInventory().getItem(3).getType()==Material.ECHO_SHARD,"evergarden test root exposes Six Rivers entry");clickGui(3);
        later(2,()->{check(player.getOpenInventory().getTopInventory().getHolder().getClass().getSimpleName().equals("RiverTestHolder"),"actual GUI click opens minion submenu");clickGui(28);later(3,this::checkRiverPreviews);});
    }catch(Exception e){throw new RuntimeException(e);}}
    void checkRiverPreviews(){try{
        stepRivers(20);check(riverSpirits().size()==6,"GUI preview-all spawns all six unique models");
        for(var e:riverSpirits().entrySet()){
            var kind=(ArkhaiaWaves.Kind)e.getKey();Object spirit=e.getValue();Mob mob=(Mob)field(spirit,"mob");Object visual=field(spirit,"visual");
            check(visual!=null&&mob.isInvisible()&&ModelEngineAPI.getModeledEntity(mob.getUniqueId()).getModel("arkhaia_"+kind.name().toLowerCase(Locale.ROOT)).isPresent(),"live native model replaces vanilla appearance "+kind);
            check(mob.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue()==(kind==ArkhaiaWaves.Kind.PHLEGETHON?1000:kind==ArkhaiaWaves.Kind.BANSHEE_WAIL?500:kind==ArkhaiaWaves.Kind.COCYTUS?700:kind==ArkhaiaWaves.Kind.MALEFEASANCE?600:kind==ArkhaiaWaves.Kind.STYX?900:800)&&!mob.hasAI(),"harmless preview retains species HP and suppresses hostile AI "+kind);
            var nativeModel=ModelEngineAPI.getModeledEntity(mob.getUniqueId()).getModel("arkhaia_"+kind.name().toLowerCase(Locale.ROOT)).orElseThrow();
            check(Math.abs(nativeModel.getScale().x()-4)<.001,"river native render scale is fourfold "+kind);
            check(Math.abs(mob.getAttribute(org.bukkit.attribute.Attribute.SCALE).getValue()-4)<.001&&mob.getBoundingBox().getHeight()>7,"fourfold damage receiver matches enlarged river model "+kind);
            check((double)field(visual,"eyeHeight")>7,"head tracking uses enlarged eye position "+kind);
            check(nativeModel.getBones().get("gaze").hasBoneBehaviors(),"native head tracking behavior imported "+kind);
            check(Math.abs(Math.IEEEremainder(nativeModel.getYBodyRot()-mob.getLocation().getYaw(),360))<.01,"model body yaw matches movement receiver "+kind);
            var carrier=(ArmorStand)field(visual,"carrier");check(carrier!=null&&carrier.isValid(),"native Bedrock carrier exists "+kind);
            check(((org.bukkit.boss.BossBar)field(spirit,"bar")).getPlayers().contains(player),"creative admin sees preview health bar "+kind);
            Mob m=mob;m.damage(500,player);check((double)field(spirit,"health")== (kind==ArkhaiaWaves.Kind.PHLEGETHON?1000:kind==ArkhaiaWaves.Kind.BANSHEE_WAIL?500:kind==ArkhaiaWaves.Kind.COCYTUS?700:kind==ArkhaiaWaves.Kind.MALEFEASANCE?600:kind==ArkhaiaWaves.Kind.STYX?600:800),"preview ignores incoming combat "+kind);
            bedrockRiverSpawn(carrier,kind);
        }
        Object sigils=field(riverController(),"sigils");var halos=(Map<?,?>)field(sigils,"planes");
        check(halos.size()==6,"six overhead individual ritual seals rendered without floor duplicates");
        for(var entry:riverSpirits().entrySet()){
            Mob receiver=(Mob)field(entry.getValue(),"mob");var halo=(ItemDisplay)halos.get("identity:"+entry.getKey());
            var transform=halo.getTransformation();
            check(halo.getLocation().getY()+transform.getTranslation().y-receiver.getY()>10,"personal seal floats above enlarged head "+entry.getKey());
            check(Math.hypot(halo.getX()-receiver.getX(),halo.getZ()-receiver.getZ())<.01,"overhead seal remains centered on minion "+entry.getKey());
            double drawnDiameter=transform.getScale().x*331.0/384;
            double modelWidth=switch((ArkhaiaWaves.Kind)entry.getKey()){case ACHERON->5.69;case PHLEGETHON->6.71;case COCYTUS->5.75;case BANSHEE_WAIL->6.91;case MALEFEASANCE->7.52;case STYX->6.97;};
            check(drawnDiameter>modelWidth*1.1&&drawnDiameter<modelWidth*1.2,"overhead seal is slightly wider than fourfold model "+entry.getKey());
            var normal=new org.joml.Vector3f(0,0,1).rotate(transform.getLeftRotation());
            check(Math.abs(normal.y)>.99,"halo stays horizontal above head "+entry.getKey());
        }
        Object first=riverSpirits().values().iterator().next();var damagedCarrier=(ArmorStand)field(field(first,"visual"),"carrier");damagedCarrier.remove();stepRivers(1);
        check(field(first,"visual")==null,"missing visual releases old ME renderer before retry");
        later(12,()->{try{stepRivers(12);check(field(first,"visual")!=null&&((Mob)field(first,"mob")).isValid(),"missing minion carrier recovers without deleting combat entity");startRiverPoses();}catch(Exception e){throw new RuntimeException(e);}});
    }catch(Exception e){throw new RuntimeException(e);}}
    void startRiverPoses(){
        garden.testGui().openRiverTests(player);clickGui(39);later(3,()->{checkRiverPose("walk");garden.testGui().openRiverTests(player);clickGui(40);later(3,()->{checkRiverPose("cast_windup");garden.testGui().openRiverTests(player);clickGui(41);later(3,()->{checkRiverPose("stun");checkRiverPermissions();});});});
    }
    void checkRiverPose(String clip){try{
        stepRivers(3);
        for(var e:riverSpirits().entrySet()){
            Mob mob=(Mob)field(e.getValue(),"mob");String id="arkhaia_"+((ArkhaiaWaves.Kind)e.getKey()).name().toLowerCase(Locale.ROOT);var m=ModelEngineAPI.getModeledEntity(mob.getUniqueId()).getModel(id).orElseThrow();
            String expected=clip.equals("walk")&&e.getKey()==ArkhaiaWaves.Kind.BANSHEE_WAIL?"hover":clip;
            check(((IStateMachineHandler)m.getAnimationHandler()).isPlayingAnimation(10,expected),"GUI pose selects native "+expected+" / "+id);
            if(e.getKey()==ArkhaiaWaves.Kind.STYX){
                ModelEngineAPI.getModeledEntity(mob.getUniqueId()).registerTickTask(ModeledEntity.Phase.PRE_MODEL_TICK,(java.util.function.Consumer<ModeledEntity>) en->en.getBase().getData().getMutableTracking().put(player.getUniqueId(),com.ticxo.modelengine.api.entity.CullType.NO_CULL));
            }
        }
    }catch(Exception e){throw new RuntimeException(e);}}
    void bedrockRiverSpawn(ArmorStand carrier,ArkhaiaWaves.Kind kind)throws Exception{
        class Spawn extends org.geysermc.geyser.api.event.java.ServerSpawnEntityEvent {
            org.geysermc.geyser.api.entity.definition.GeyserEntityDefinition definition;
            java.util.function.Consumer<org.geysermc.geyser.api.entity.type.GeyserEntity> consumer;
            Spawn(){super(null);}
            public UUID uuid(){return carrier.getUniqueId();}public int entityId(){return carrier.getEntityId();}
            public org.geysermc.geyser.api.entity.definition.JavaEntityType entityType(){return null;}
            public org.geysermc.geyser.api.entity.definition.GeyserEntityDefinition definition(){return definition;}
            public void definition(org.geysermc.geyser.api.entity.definition.GeyserEntityDefinition value){definition=value;}
            public void preSpawnConsumer(java.util.function.Consumer<org.geysermc.geyser.api.entity.type.GeyserEntity> value){consumer=value;}
            public boolean isCancelled(){return false;}public void setCancelled(boolean value){}
        }
        Spawn event=new Spawn();org.geysermc.geyser.api.GeyserApi.api().eventBus().fire(event);
        check(event.definition!=null&&event.definition.identifier().toString().equals("voidscape:arkhaia_"+kind.name().toLowerCase(Locale.ROOT)),"real Geyser spawn uses correct minion definition "+kind);
        var values=new HashMap<Object,Object>();
        var proxy=(org.geysermc.geyser.api.entity.type.GeyserEntity)java.lang.reflect.Proxy.newProxyInstance(getClassLoader(),new Class<?>[]{org.geysermc.geyser.api.entity.type.GeyserEntity.class},(instance,method,args)->{
            if(method.getName().equals("updatePropertiesBatched")){((java.util.function.Consumer<org.geysermc.geyser.api.entity.property.BatchPropertyUpdater>)args[0]).accept(new org.geysermc.geyser.api.entity.property.BatchPropertyUpdater(){public <T>void update(org.geysermc.geyser.api.entity.property.GeyserEntityProperty<T> property,T value){values.put(property,value);}});}return null;
        });event.consumer.accept(proxy);check(values.size()==5,"native minion spawn batches clock and independent head properties "+kind);
        Object bridge=field(garden.arkhaiaBedrock(),"bridge");var defs=(Map<?,?>)field(bridge,"definitions");
        var definition=defs.get("arkhaia_"+kind.name().toLowerCase(Locale.ROOT));check(definition!=null,"registered definition selected by minion actor "+kind);
        var actor=(((Map<?,?>)field(garden.arkhaiaBedrock(),"actors")).get(carrier.getUniqueId()));
        check(((ArkhaiaBedrockSupport.Snapshot)field(actor,"snapshot")).pitch()>0,"Bedrock head looks down toward player eyes "+kind);
        check(field(actor,"model").equals("arkhaia_"+kind.name().toLowerCase(Locale.ROOT)),"Bedrock actor maps correct model "+kind);
    }
    void checkRiverPermissions(){try{
        player.setOp(false);check(!garden.worldBoss().riverTest(player,0,false)&&riverSpirits().size()==6,"non-admin cannot replace river tests");player.setOp(true);
        garden.testGui().openRiverTests(player);clickGui(32);later(10,()->{try{
            check(((Map<?,?>)field(field(garden.worldBoss(),"arkhaia"),"demos")).isEmpty(),"GUI clear removes test group");
            player.setGameMode(GameMode.SURVIVAL);garden.testGui().openRiverTests(player);clickGui(19);later(3,this::checkRiverCombat);
        }catch(Exception e){throw new RuntimeException(e);}});
    }catch(Exception e){throw new RuntimeException(e);}}
    void checkRiverCombat(){try{
        stepRivers(3);check(riverSpirits().size()==1,"GUI combat button summons selected single minion");Object spirit=riverSpirits().values().iterator().next();Mob mob=(Mob)field(spirit,"mob");
        // Skill-first Acheron may already have committed a windup at the GUI
        // spawn distance. Isolate pursuit here; skill behavior is exercised below.
        setRiverField(spirit,"releaseAt",0L);setRiverField(spirit,"castActiveUntil",0L);setRiverField(spirit,"spinUntil",0L);setRiverField(spirit,"snareAt",Long.MAX_VALUE);setRiverField(spirit,"castAt",Long.MAX_VALUE);
        Location home=mob.getLocation();player.teleport(home.clone().add(60,0,0));long silent=hitSounds();mob.damage(100,player);check((double)field(spirit,"health")==800,"outside shooter cannot damage test minion");
        check(hitSounds()==silent,"rejected outside damage produces no hit-confirmation sound");
        player.teleport(home.clone().add(24,0,0));stepRivers(10);
        // The invulnerable probe player cannot become a vanilla combat target;
        // the encounter controller must still issue a real pursuit path to it.
        check(mob.hasAI()&&mob.getPathfinder().hasPath(),"minion paths to medium-range player beyond old 18-block boundary; ai="+mob.hasAI()+" release="+field(spirit,"releaseAt")+" attack="+field(spirit,"attackStarted"));
        check(mob.getAttribute(org.bukkit.attribute.Attribute.FOLLOW_RANGE).getBaseValue()==84,"vanilla pursuit range spans full arena");
        mob.teleport(home.clone().add(24,0,0));stepRivers(1);
        check(mob.getX()>home.getX()+23,"minion is not leashed back at former 16-block limit");mob.teleport(home);
        Location melee=home.clone().add(0,0,2.5);melee.setDirection(home.clone().add(.7,1,0).toVector().subtract(melee.toVector().add(new org.bukkit.util.Vector(0,player.getEyeHeight(),0))));
        player.teleport(melee);long meleeSounds=hitSounds();mob.damage(100,player);check((double)field(spirit,"health")<800,"combat test accepts inside player damage");
        check(hitSounds()>meleeSounds,"accepted melee damage sends direct hit-confirmation sound");
        long throttledSounds=hitSounds();mob.damage(100,player);check(hitSounds()==throttledSounds,"damage cooldown does not emit false hit confirmation");
        stepRivers(11);long arrowSounds=hitSounds();double hpBefore=(double)field(spirit,"health");Location launch=home.clone().add(0,1.4,4);player.teleport(home.clone().add(0,0,4));
        var direction=home.clone().add(0,1.2,0).toVector().subtract(launch.toVector()).normalize();var arrow=world.spawnArrow(launch,direction,2,0);arrow.setShooter(player);arrow.setGravity(false);
        for(int n=0;n<5&&arrow.isValid();n++)((org.bukkit.craftbukkit.entity.CraftArrow)arrow).getHandle().tick();arrow.remove();
        check((double)field(spirit,"health")<hpBefore,"actual moving arrow hits invisible native minion receiver");
        check(hitSounds()>arrowSounds,"accepted moving arrow sends hit sound to shooter");
        // Cocytus' frost-arrow volley became the arrow-rain skill (e2.9x); tests/arkhaia/CocytusProbe.java covers it.
        player.teleport(home.clone().add(0,0,-6));checkRiverCharge();
        garden.worldBoss().clearRiverTest(player);garden.worldBoss().close();check(!shell.isValid(),"fight shutdown removes real hitbox");
        check(((Map<?,?>)field(garden.arkhaiaBedrock(),"actors")).isEmpty(),"shutdown unregisters boss and all minion Bedrock actors");
        later(8,()->{check(ModelEngineAPI.getModeledEntity(shell.getUniqueId())==null,"ME worker unregisters encounter and destroys renderer");finish(null);});
    }catch(Exception e){throw new RuntimeException(e);}}
    void setRiverField(Object object,String name,Object value)throws Exception{var f=object.getClass().getDeclaredField(name);f.setAccessible(true);f.set(object,value);}
    void checkRiverCharge()throws Exception{
        check(garden.worldBoss().riverTest(player,ArkhaiaWaves.Kind.ACHERON.ordinal(),true),"Acheron moving-spin test starts");
        Object controller=riverController(),spirit=riverSpirits().values().iterator().next();Mob mob=(Mob)field(spirit,"mob");
        Location origin=mob.getLocation();player.teleport(origin.clone().add(0,0,8));
        setRiverField(spirit,"snareAt",Long.MAX_VALUE);setRiverField(spirit,"castAt",0L);setRiverField(spirit,"attackAt",0L);stepRivers(25);
        check((long)field(spirit,"spinUntil")>(long)field(controller,"clock"),"spin release owns a moving active window");
        for(int i=0;i<24;i++){
            Location before=mob.getLocation();stepRivers(1);
            check(before.distance(mob.getLocation())<=.381,"spin moves receiver in bounded collision steps "+i);
            if(i==0){
                var planes=(Map<?,?>)field(field(controller,"sigils"),"planes");
                var quills=planes.entrySet().stream().filter(e->e.getKey().toString().startsWith("quill:")).map(e->(ItemDisplay)e.getValue()).toList();
                check(quills.size()==22,"real ModelEngine spin renders twenty-two owned quill rays");
                check(quills.stream().allMatch(e->e.getInterpolationDuration()==1&&e.getBrightness().getBlockLight()==15&&player.canSee(e)),"native spin rays are full-bright, visible and follow one-tick interpolation");
            }
        }
        check(((Map<?,?>)field(field(controller,"sigils"),"planes")).keySet().stream().noneMatch(k->k.toString().startsWith("quill:")),"native spin recovery removes all quill planes");
        check(origin.distance(mob.getLocation())>5,"Acheron travels forward while the spin clip plays");
        check(((Set<?>)field(spirit,"spinVictims")).size()==1,"moving spin registers each player once");
        stepRivers(15);setRiverField(spirit,"castAt",Long.MAX_VALUE);setRiverField(spirit,"attackAt",0L);
        player.teleport(mob.getLocation().add(0,0,2));stepRivers(5);
        check((long)field(spirit,"attackStarted")==-1,"Acheron waits for skills instead of reverting to walking melee");
        player.teleport(mob.getLocation().add(0,0,8));setRiverField(spirit,"castAt",0L);stepRivers(1);
        Location wallOrigin=mob.getLocation();List<org.bukkit.block.Block> wall=new ArrayList<>();
        for(int x=-4;x<=4;x++)for(int y=0;y<12;y++){
            var b=wallOrigin.clone().add(x,y,3).getBlock();b.setType(Material.STONE,false);wall.add(b);
        }
        stepRivers(48);check(wallOrigin.distance(mob.getLocation())<3,"solid wall stops moving spin without tunneling");
        for(var b:wall)b.setType(Material.AIR,false);
        garden.worldBoss().clearRiverTest(player);
        check(!mob.isValid()&&((Map<?,?>)field(controller,"spirits")).isEmpty(),"moving skill cleanup removes receiver and state");
    }
    void checkRiverChoreography()throws Exception{
        // A real moving arrow must leave the ranged attack, hit exactly once and be owned/cleaned.
        // Keep this arrow lane away from the still-live main boss hitbox.
        player.teleport(((Location)field(riverController(),"center")).clone().add(26,0,-6));
        check(garden.worldBoss().riverTest(player,ArkhaiaWaves.Kind.COCYTUS.ordinal(),true),"Cocytus combat test starts");
        Object controller=riverController(),spirit=riverSpirits().values().iterator().next();Mob archer=(Mob)field(spirit,"mob");
        player.teleport(archer.getLocation().clone().add(0,0,-8));
        setRiverField(spirit,"castAt",Long.MAX_VALUE);setRiverField(spirit,"attackAt",0L);
        Object trials=field(controller,"trials");var stateMethod=trials.getClass().getDeclaredMethod("state",spirit.getClass(),long.class);stateMethod.setAccessible(true);
        Object trial=stateMethod.invoke(trials,spirit,(long)field(controller,"clock"));setRiverField(trial,"next",(long)field(controller,"clock")+1);
        setRiverField(trial,"turns",2); // Cocytus cycles Blizzard, Fissure, Volley: select the volley turn.
        stepRivers(1);check(field(trial,"move").toString().equals("VOLLEY"),"Cocytus commits its new three-shot volley eight blocks away");
        check(!archer.hasAI()&&!archer.getPathfinder().hasPath(),"archer holds range during draw");
        stepRivers(21);check(((Map<?,?>)field(controller,"frostArrows")).isEmpty(),"no arrow before bow release contact");
        stepRivers(1);Map<?,?> shots=(Map<?,?>)field(controller,"frostArrows");check(shots.size()==1,"bow contact creates one real frost arrow");
        Object shot=shots.values().iterator().next();var accessor=shot.getClass().getDeclaredMethod("arrow");accessor.setAccessible(true);
        Arrow arrow=(Arrow)accessor.invoke(shot);check(arrow.getShooter()==archer&&arrow.getPickupStatus()==AbstractArrow.PickupStatus.DISALLOWED,"frost arrow has encounter owner and cannot be picked up");
        lines.add("Frost launch="+arrow.getLocation()+" target="+player.getLocation());
        Bukkit.getPluginManager().registerEvent(org.bukkit.event.entity.EntityDamageByEntityEvent.class,new org.bukkit.event.Listener(){},org.bukkit.event.EventPriority.MONITOR,(listener,event)->{
            var damage=(org.bukkit.event.entity.EntityDamageByEntityEvent)event;
            if(damage.getDamager()==arrow)lines.add("Frost collision target="+damage.getEntity().getType()+" cancelled="+damage.isCancelled());
        },this,false);
        player.setInvulnerable(false);actor.getAbilities().invulnerable=false;player.setNoDamageTicks(0);player.setHealth(20);
        player.getInventory().setArmorContents(new ItemStack[4]);
        for(int i=0;i<15&&arrow.isValid();i++)((org.bukkit.craftbukkit.entity.CraftArrow)arrow).getHandle().tick();
        lines.add("Frost result valid="+arrow.isValid()+" tracked="+shots.size()+" hp="+player.getHealth()+" final="+arrow.getLocation());
        check(player.getHealth()<20&&((Map<?,?>)field(trials,"cold")).containsKey(player.getUniqueId()),"physical frost arrow damages and adds a cold stack to an inside player");
        check(shots.isEmpty()&&!arrow.isValid(),"frost impact consumes tracked arrow exactly once");
        player.setInvulnerable(true);actor.getAbilities().invulnerable=true;
        // Force another shot, then verify encounter cleanup also removes an airborne arrow.
        stepRivers(34);
        check(!shots.isEmpty(),"second frost arrow can be in flight");
        garden.worldBoss().clearRiverTest(player);check(shots.isEmpty(),"closing encounter clears all frost projectiles");
    }
    void finish(Throwable error){
        if(error!=null){error.printStackTrace();lines.add("ERROR: "+error);}
        try{Files.writeString(Path.of("model-engine-result.txt"),(error==null?"PASS":"FAIL")+": Arkhaia Model Engine / "+checks+" checks\n"+String.join("\n",lines)+"\n");}
        catch(Exception e){e.printStackTrace();}
        if(actor!=null){var server=MinecraftServer.getServer();server.getPlayerList().getPlayers().remove(actor);server.getPlayerList().getPlayersByUUID().remove(actor.getUUID());actor.discard();}
        Bukkit.shutdown();
    }
}
