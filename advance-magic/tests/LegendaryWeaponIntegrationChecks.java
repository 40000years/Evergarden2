import com.example.advancemagic.AdvanceMagicPlugin;
import com.example.advancemagic.item.LegendaryWeaponService.Weapon;
import com.ticxo.modelengine.api.ModelEngineAPI;
import com.ticxo.modelengine.api.animation.handler.IStateMachineHandler;
import org.bukkit.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.geysermc.geyser.registry.Registries;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.translator.item.CustomItemTranslator;
import org.geysermc.geyser.platform.spigot.shaded.net.kyori.adventure.key.Key;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.*;
import java.nio.file.*;
import java.util.*;

/** Actual installed Geyser matcher and ModelEngine 4.1.1 blueprint importer. */
public final class LegendaryWeaponIntegrationChecks extends JavaPlugin {
    static final class CaptureSession extends GeyserSession {
        List<org.cloudburstmc.protocol.bedrock.packet.BedrockPacket> packets;
        org.geysermc.geyser.session.cache.EntityCache testCache;
        org.geysermc.geyser.entity.type.player.SessionPlayerEntity testPlayer;
        io.netty.channel.EventLoop testLoop;
        private CaptureSession(){super(null,null,null);}
        List<org.geysermc.mcprotocollib.network.packet.Packet> downstream;
        @Override public boolean isSpawned(){return true;}
        @Override public void activateArmAnimationTicking(){}
        @Override public int getTicks(){return 100;}
        @Override public int getLastAirHitTick(){return 0;}
        @Override public int getArmAnimationTicks(){return 1;}
        @Override public java.util.concurrent.ScheduledFuture<?> scheduleInEventLoop(Runnable task,long delay,java.util.concurrent.TimeUnit unit){task.run();return null;}
        @Override public void sendDownstreamGamePacket(org.geysermc.mcprotocollib.network.packet.Packet packet){downstream.add(packet);}

        @Override public org.geysermc.geyser.entity.type.player.SessionPlayerEntity getPlayerEntity(){return testPlayer;}
        @Override public io.netty.channel.EventLoop getTickEventLoop(){return testLoop;}
        @Override public org.geysermc.geyser.session.cache.EntityCache getEntityCache(){return testCache;}
        @Override public org.geysermc.geyser.api.entity.EntityData entities(){return new org.geysermc.geyser.entity.GeyserEntityData(this);}
        @Override public void ensureInEventLoop(Runnable task){task.run();}
        void flushProperties(){
            for(var entity:List.copyOf(testCache.getDirtyEntities()))entity.updateBedrockEntityProperties();
            testCache.getDirtyEntities().clear();
        }
        @Override public void sendUpstreamPacket(org.cloudburstmc.protocol.bedrock.packet.BedrockPacket packet){packets.add(packet);}
        @Override public org.geysermc.geyser.GeyserImpl getGeyser(){return org.geysermc.geyser.GeyserImpl.getInstance();}
    }
    static final class MarkerEntity extends org.geysermc.geyser.entity.type.living.ArmorStandEntity {
        MarkerEntity(org.geysermc.geyser.entity.spawn.EntitySpawnContext context){super(context);}
        @Override protected org.cloudburstmc.math.vector.Vector3f spawnPosition(org.cloudburstmc.math.vector.Vector3f position){return position;}
    }

    static final class Capture extends net.minecraft.network.Connection {
        Capture(){super(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);channel=new io.netty.channel.embedded.EmbeddedChannel();address=new java.net.InetSocketAddress("127.0.0.1",1);}
        @Override public void send(net.minecraft.network.protocol.Packet<?> p){}
        @Override public void send(net.minecraft.network.protocol.Packet<?> p,io.netty.channel.ChannelFutureListener l){}
        @Override public void send(net.minecraft.network.protocol.Packet<?> p,io.netty.channel.ChannelFutureListener l,boolean f){}
        @Override public boolean isConnected(){return true;}
    }
    void punchReach(AdvanceMagicPlugin magic,CaptureSession session) throws Exception {
        session.downstream=new ArrayList<>();
        var animate=new org.cloudburstmc.protocol.bedrock.packet.AnimatePacket();
        animate.setAction(org.cloudburstmc.protocol.bedrock.packet.AnimatePacket.Action.SWING_ARM);
        new org.geysermc.geyser.translator.protocol.bedrock.BedrockAnimateTranslator().translate(session,animate);
        check(session.downstream.size()==1&&session.downstream.getFirst() instanceof org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundPunchPacket,"real Geyser air-swing emits a Java punch packet");
        var server=net.minecraft.server.MinecraftServer.getServer();var world=Bukkit.getWorlds().getFirst();
        var level=((org.bukkit.craftbukkit.CraftWorld)world).getHandle();world.getChunkAt(0,0).setForceLoaded(true);
        var profile=new com.mojang.authlib.GameProfile(UUID.randomUUID(),"BedrockReach");
        var handle=new net.minecraft.server.level.ServerPlayer(server,level,profile,net.minecraft.server.level.ClientInformation.createDefault());
        handle.connection=new net.minecraft.server.network.ServerGamePacketListenerImpl(server,new Capture(),handle,net.minecraft.server.network.CommonListenerCookie.createInitial(profile,false)){
            @Override public boolean hasClientLoaded(){return true;}
        };
        handle.setPos(.5,100,.5);server.getPlayerList().getPlayers().add(handle);server.getPlayerList().getPlayersByUUID().put(profile.id(),handle);level.addNewPlayer(handle);
        var player=handle.getBukkitEntity();player.setGameMode(GameMode.SURVIVAL);player.setGravity(false);player.teleport(new Location(world,.5,100,.5,0,0));
        var target=world.spawn(new Location(world,.5,100,6.5),org.bukkit.entity.IronGolem.class,g->{g.setAI(false);g.setGravity(false);});
        var ticker=net.minecraft.world.entity.LivingEntity.class.getDeclaredField("attackStrengthTicker");ticker.setAccessible(true);
        try{
            for(var material:new Material[]{Material.NETHERITE_SWORD,Material.TRIDENT,Material.MACE}){
                player.getInventory().setItemInMainHand(new ItemStack(material));handle.detectEquipmentUpdates();ticker.setInt(handle,100);
                double before=target.getHealth();handle.connection.handlePunch(net.minecraft.network.protocol.game.ServerboundPunchPacket.INSTANCE);
                check(target.getHealth()==before,"ordinary "+material+" cannot hit at six blocks through the same punch path");
            }
            player.getInventory().setItemInMainHand(magic.legendaryWeapons().create(Weapon.LONGINUS));handle.detectEquipmentUpdates();ticker.setInt(handle,100);
            double before=target.getHealth();handle.connection.handlePunch(net.minecraft.network.protocol.game.ServerboundPunchPacket.INSTANCE);
            check(target.getHealth()<before,"Geyser-style punch hits with Longinus at six blocks before cooldown reset");
            target.setNoDamageTicks(0);before=target.getHealth();handle.connection.handlePunch(net.minecraft.network.protocol.game.ServerboundPunchPacket.INSTANCE);
            check(target.getHealth()==before,"repeated Bedrock punches cannot bypass attack cooldown");
            target.teleport(new Location(world,.5,100,7.6));ticker.setInt(handle,100);target.setNoDamageTicks(0);before=target.getHealth();
            handle.connection.handlePunch(net.minecraft.network.protocol.game.ServerboundPunchPacket.INSTANCE);
            check(target.getHealth()<before,"Longinus punch reaches target hitbox just inside seven blocks");
            target.teleport(new Location(world,.5,100,8.6));ticker.setInt(handle,100);target.setNoDamageTicks(0);before=target.getHealth();
            handle.connection.handlePunch(net.minecraft.network.protocol.game.ServerboundPunchPacket.INSTANCE);
            check(target.getHealth()==before,"Longinus punch misses beyond seven blocks");
            target.teleport(new Location(world,.5,100,6.5));var wall=world.getBlockAt(0,101,3);wall.setType(Material.STONE);ticker.setInt(handle,100);before=target.getHealth();
            handle.connection.handlePunch(net.minecraft.network.protocol.game.ServerboundPunchPacket.INSTANCE);
            check(target.getHealth()==before,"Bedrock punch cannot hit through a solid wall");wall.setType(Material.AIR);
            for(Weapon w:new Weapon[]{Weapon.TYRFING,Weapon.LONGINUS,Weapon.MJOLNIR,Weapon.VAJRA,Weapon.EXCALIBUR}){
                player.setSneaking(true);player.getInventory().setItemInMainHand(magic.legendaryWeapons().create(w));handle.detectEquipmentUpdates();ticker.setInt(handle,100);
                player.getPersistentDataContainer().remove(new NamespacedKey(magic,"legendary_cd_"+w.id()));
                handle.connection.handlePunch(net.minecraft.network.protocol.game.ServerboundPunchPacket.INSTANCE);
                check(magic.legendaryWeapons().combat().cooldownRemaining(player,w)>179000,"actual Bedrock-style sneak punch activates "+w);
                magic.effects().closeOwner(player.getUniqueId());player.setSneaking(false);
            }

        }finally{target.remove();server.getPlayerList().getPlayers().remove(handle);server.getPlayerList().getPlayersByUUID().remove(profile.id());handle.discard();}
    }
    int checks,waited;
    void check(boolean ok,String label){if(!ok)throw new AssertionError(label);checks++;getLogger().info("PASS "+label);}
    @Override public void onEnable(){Bukkit.getScheduler().runTaskLater(this,this::await,100);}
    void await(){
        try{
            if(ModelEngineAPI.getBlueprint("legendary_vajra")==null&&waited<600){waited+=20;Bukkit.getScheduler().runTaskLater(this,this::await,20);return;}
            run();finish(null);
        }catch(Throwable e){finish(e);}
    }
    void run() throws Exception {
        check(Bukkit.getPluginManager().isPluginEnabled("ModelEngine"),"ModelEngine enabled");
        check(Bukkit.getPluginManager().getPlugin("ModelEngine").getDescription().getVersion().contains("4.1.1"),"ModelEngine R4.1.1");
        check(Bukkit.getPluginManager().isPluginEnabled("Geyser-Spigot"),"installed Geyser enabled");
        var magic=(AdvanceMagicPlugin)Bukkit.getPluginManager().getPlugin("advance-magic");
        check(magic.isEnabled()&&!Registries.ITEMS.get().isEmpty(),"weapon plugin and protocol registries ready");
        var unsafe=Class.forName("sun.misc.Unsafe");var field=unsafe.getDeclaredField("theUnsafe");field.setAccessible(true);
        var context=(CaptureSession)unsafe.getMethod("allocateInstance",Class.class).invoke(field.get(null),CaptureSession.class);context.packets=new ArrayList<>();context.testCache=new org.geysermc.geyser.session.cache.EntityCache(context);
        context.testPlayer=(org.geysermc.geyser.entity.type.player.SessionPlayerEntity)unsafe.getMethod("allocateInstance",Class.class)
            .invoke(field.get(null),org.geysermc.geyser.entity.type.player.SessionPlayerEntity.class);
        context.testPlayer.uuid(UUID.randomUUID());context.testLoop=new io.netty.channel.embedded.EmbeddedChannel().eventLoop();
        punchReach(magic,context);
        check(magic.legendaryWeapons().projectiles()==null,"Longinus projectile rendering system is disabled");
        for(Weapon weapon:Weapon.values()){
            String id="advance_magic:legendary_"+weapon.id();
            ItemStack item=magic.legendaryWeapons().create(weapon);
            var components=new DataComponents(new HashMap<>());
            components.put(DataComponentTypes.ITEM_MODEL,Key.key(item.getItemMeta().getItemModel().toString()));
            for(var table:Registries.ITEMS.get().values()){
                var base=table.getMapping(item.getType().getKey().toString());
                var mapped=CustomItemTranslator.getCustomItem(context,item.getAmount(),components,base);
                check(mapped!=null&&id.equals(mapped.getIdentifier()),"real Geyser translation "+id);
                if(weapon==Weapon.LONGINUS){
                    var data=((org.cloudburstmc.protocol.bedrock.data.definitions.SimpleItemDefinition)mapped).getComponentData();

                    var client=data.getCompound("components");
                    check(client.getCompound("item_properties").getInt("use_animation")!=6,"Bedrock no longer advertises spear charging");
                    check(!client.containsKey("minecraft:food"),"Bedrock no longer uses a fake food charge component");
                }

            }
        }
        for(Weapon weapon:Weapon.values()){
            var blueprint=ModelEngineAPI.getBlueprint("legendary_"+weapon.id());
            check(blueprint!=null,"ModelEngine imported "+weapon.id());
            check(blueprint.getAnimations().containsKey("idle"),"ModelEngine idle clip "+weapon.id());
            var active=ModelEngineAPI.createActiveModel("legendary_"+weapon.id(),null,ModelEngineAPI::createStateMachineHandler);
            var modelCarrier=Bukkit.getWorlds().getFirst().spawn(new Location(Bukkit.getWorlds().getFirst(),.5,100,.5),org.bukkit.entity.Slime.class,
                slime->{slime.setAI(false);slime.setGravity(false);slime.setSilent(true);slime.setPersistent(false);});
            var entity=ModelEngineAPI.createModeledEntity(modelCarrier);entity.setSaved(false);entity.addModel(active,false);
            if(active.getBones().isEmpty())active.generateModel();
            check(active!=null&&!active.getBones().isEmpty(),"ModelEngine creates articulated model "+weapon.id());
            var handler=(IStateMachineHandler)active.getAnimationHandler();
            handler.playAnimation(10,"idle",0,0,1,true);
            check(handler.isPlayingAnimation(10,"idle"),"ModelEngine plays idle clip "+weapon.id());
            ModelEngineAPI.removeModeledEntity(modelCarrier.getUniqueId());modelCarrier.remove();
        }
        Path installed=Path.of("plugins/Geyser-Spigot/packs/advance-magic-bedrock.mcpack");
        check(Arrays.equals(Files.readAllBytes(installed),Files.readAllBytes(Path.of("plugins/advance-magic/resource-packs/advance-magic-bedrock.mcpack"))),"installed Geyser pack matches bundled release");
        check(Files.exists(Path.of("plugins/ModelEngine/resource pack.zip")),"ModelEngine compiled editable source assets");
    }
    void finish(Throwable error){
        if(error!=null)getLogger().log(java.util.logging.Level.SEVERE,"WEAPON INTEGRATION FAILED after "+checks,error);
        try{Files.writeString(Path.of("legendary-weapon-integration-result.txt"),error==null?"PASS "+checks:"FAIL "+error);}catch(Exception ignored){}
        Bukkit.getScheduler().runTaskLater(this,Bukkit::shutdown,5);
    }
}
