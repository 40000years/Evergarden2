import com.example.advancemagic.AdvanceMagicPlugin;
import com.mojang.authlib.GameProfile;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.player.Input;
import org.bukkit.*;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Runs only on an isolated Paper server with allow-flight=false; never shipped. */
public final class FlyingStaffChecks extends JavaPlugin {
    private static final class TestConnection extends Connection {
        TestConnection(){super(PacketFlow.SERVERBOUND);channel=new EmbeddedChannel();address=new InetSocketAddress("127.0.0.1",1);}
        @Override public void send(Packet<?> packet){}
        @Override public void send(Packet<?> packet,ChannelFutureListener listener){}
        @Override public void send(Packet<?> packet,ChannelFutureListener listener,boolean flush){}
        @Override public boolean isConnected(){return true;}
    }
    AdvanceMagicPlugin magic;
    Player player;
    ServerPlayer handle;
    ArmorStand stand;
    int checks;
    void check(boolean value,String label){if(!value)throw new AssertionError(label);checks++;getLogger().info("PASS "+label);}
    @Override public void onEnable(){Bukkit.getScheduler().runTaskLater(this,()->run(this::begin),20);}
    void run(Runnable task){try{task.run();}catch(Throwable error){finish(error);}}
    void later(int ticks,Runnable task){Bukkit.getScheduler().runTaskLater(this,()->run(task),ticks);}
    void begin(){
        magic=(AdvanceMagicPlugin)Bukkit.getPluginManager().getPlugin("advance-magic");
        check(magic!=null&&magic.isEnabled(),"Advance Magic boots");
        var legacyConfig=new org.bukkit.configuration.file.YamlConfiguration();
        legacyConfig.set("flying-staff.horizontal-speed",.18);
        legacyConfig.set("flying-staff.vertical-speed",.12);
        check(com.example.advancemagic.item.FlyingStaffService.upgradeSpeedConfig(legacyConfig)
            &&legacyConfig.getDouble("flying-staff.horizontal-speed")==.486
            &&legacyConfig.getDouble("flying-staff.vertical-speed")==.288
            &&legacyConfig.getDouble("flying-staff.turbo-multiplier")==4.0,"previous default speeds migrate once");
        check(!com.example.advancemagic.item.FlyingStaffService.upgradeSpeedConfig(legacyConfig),"speed migration does not repeat");
        var customConfig=new org.bukkit.configuration.file.YamlConfiguration();
        customConfig.set("flying-staff.horizontal-speed",.31);
        customConfig.set("flying-staff.vertical-speed",.20);
        com.example.advancemagic.item.FlyingStaffService.upgradeSpeedConfig(customConfig);
        check(customConfig.getDouble("flying-staff.horizontal-speed")==.31
            &&customConfig.getDouble("flying-staff.vertical-speed")==.20,"custom speeds survive migration");
        try{check(Files.readString(Path.of("server.properties")).contains("allow-flight=false"),"server flight stays disabled");}
        catch(Exception error){throw new RuntimeException(error);}
        World world=Bukkit.getWorlds().getFirst();
        for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)world.getChunkAt(x,z).setForceLoaded(true);
        for(int x=-24;x<=24;x++)for(int z=-24;z<=24;z++){
            world.getBlockAt(x,99,z).setType(Material.STONE);
            for(int y=100;y<107;y++)world.getBlockAt(x,y,z).setType(Material.AIR);
        }
        var server=MinecraftServer.getServer();var level=((CraftWorld)world).getHandle();
        GameProfile profile=new GameProfile(UUID.randomUUID(),"StaffTestActor");
        handle=new ServerPlayer(server,level,profile,ClientInformation.createDefault());
        handle.connection=new ServerGamePacketListenerImpl(server,new TestConnection(),handle,CommonListenerCookie.createInitial(profile,false));
        handle.setPos(.5,100,.5);
        server.getPlayerList().getPlayers().add(handle);server.getPlayerList().getPlayersByUUID().put(profile.id(),handle);
        level.addNewPlayer(handle);player=handle.getBukkitEntity();player.setGameMode(GameMode.SURVIVAL);
        player.setOp(false);player.setGravity(false);
        player.teleport(new Location(world,.5,100,.5,0,0));
        check(!player.getAllowFlight(),"rider starts without Bukkit flight");
        try {
            magic.getConfig().set("resource-pack.host.public-host","127.0.0.1");
            var url=magic.packs().url(player);
            byte[] served=java.net.URI.create(url).toURL().openStream().readAllBytes();
            byte[] bundled=Files.readAllBytes(magic.getDataFolder().toPath().resolve("resource-packs/advance-magic-java.zip"));
            check(Arrays.equals(served,bundled),"pack URL serves the exact new bundled ZIP");
            check(java.security.MessageDigest.isEqual(java.security.MessageDigest.getInstance("SHA-1").digest(served),
                HexFormat.of().parseHex(com.google.gson.JsonParser.parseString(Files.readString(magic.getDataFolder().toPath().resolve("resource-packs/pack-hashes.json")))
                    .getAsJsonObject().get("advance-magic-java.zip").getAsString())),"served pack SHA-1 matches manifest");
        }catch(Exception error){throw new RuntimeException(error);}
        var item=magic.flyingStaff().create();
        check(magic.flyingStaff().isStaff(item)&&!magic.flyingStaff().isStaff(new org.bukkit.inventory.ItemStack(Material.BLAZE_ROD)),"only tagged staff works");
        check(Bukkit.getRecipe(new NamespacedKey(magic,"flying_staff"))!=null,"ordinary player recipe exists");
        player.getInventory().setItemInMainHand(item);
        magic.mana().account(player).setMana(100);
        Bukkit.getPluginManager().callEvent(new PlayerInteractEvent(player,Action.RIGHT_CLICK_AIR,item,null,null,EquipmentSlot.HAND));
        stand=world.getEntitiesByClass(ArmorStand.class).stream().filter(magic.flyingStaff()::isDisplay).findFirst().orElse(null);
        check(stand!=null&&stand.isValid(),"right click summons staff display");
        var display=stand.getEquipment().getHelmet();
        check(display!=null&&display.getType()==Material.CARVED_PUMPKIN
            &&display.getItemMeta().getItemModel().equals(new NamespacedKey("advance_magic","flying_staff_summon")),
            "display uses a head item model instead of an armor model");
        Bukkit.getPluginManager().callEvent(new PlayerInteractEvent(player,Action.RIGHT_CLICK_AIR,item,null,null,EquipmentSlot.HAND));
        check(world.getEntitiesByClass(ArmorStand.class).stream().filter(magic.flyingStaff()::isDisplay).count()==1,"second right click cannot duplicate staff");
        later(14,this::board);
    }
    void board(){
        Bukkit.getPluginManager().callEvent(new PlayerInteractEntityEvent(player,stand,EquipmentSlot.HAND));
        check(magic.flyingStaff().isRiding(player),"click seats rider on staff");
        check(!player.getAllowFlight()&&!player.isFlying(),"mount works with allow-flight=false and never grants flight");
        check(player.hasPermission("grim.disabled"),"temporary Grim permission is active while riding");
        Location before=stand.getLocation();
        handle.connection.handlePlayerInput(new ServerboundPlayerInputPacket(new Input(true,false,false,false,false,false,false)));
        later(4,()->{
            double normal=stand.getLocation().getZ()-before.getZ();
            check(normal>.7,"mounted forward input moves at the increased base speed");
            Location turboStart=stand.getLocation();
            handle.connection.handlePlayerInput(new ServerboundPlayerInputPacket(new Input(true,false,false,false,false,false,true)));
            later(4,()->{
                double sprint=stand.getLocation().getZ()-turboStart.getZ();
                check(sprint>normal*1.5,"Sprint input accelerates the mounted staff");
                Location manualStart=stand.getLocation();
                handle.connection.handlePlayerInput(new ServerboundPlayerInputPacket(new Input(true,false,false,false,false,false,false)));
                check(Bukkit.dispatchCommand(player,"magic turbo"),"rider can toggle Turbo by command");
                later(4,()->{
                    check(stand.getLocation().getZ()-manualStart.getZ()>normal*1.5,
                        "manual Turbo works without Sprint input");
                    check(Bukkit.dispatchCommand(player,"magic turbo"),"rider can turn manual Turbo off");
                    handle.connection.handlePlayerInput(new ServerboundPlayerInputPacket(new Input(false,false,false,false,false,false,false)));
                });
            });
        });
        later(24,this::drain);
    }
    void drain(){
        check(magic.mana().account(player).manaExact()==98,"exact mana drain with regeneration paused");
        check(stand.isValid()&&player.getVehicle()==stand,"mount stays valid during hover");
        check(!player.getAllowFlight(),"flight flag remains disabled after moving");
        player.leaveVehicle();
        check(!player.hasPermission("grim.disabled"),"anti-cheat exemption is removed on dismount");
        Bukkit.getPluginManager().callEvent(new org.bukkit.event.entity.EntityDamageByEntityEvent(player,stand,org.bukkit.event.entity.EntityDamageEvent.DamageCause.ENTITY_ATTACK,1.0));
        later(12,()->{
            check(!stand.isValid(),"dismiss removes temporary entity");
            check(magic.flyingStaff().isStaff(player.getInventory().getItemInMainHand()),"original item remains unique");
            var item=player.getInventory().getItemInMainHand();
            Bukkit.getPluginManager().callEvent(new PlayerInteractEvent(player,Action.RIGHT_CLICK_AIR,item,null,null,EquipmentSlot.HAND));
            stand=player.getWorld().getEntitiesByClass(ArmorStand.class).stream().filter(magic.flyingStaff()::isDisplay).findFirst().orElse(null);
            check(stand!=null,"second summon works after dismiss");
            later(14,()->{
                Bukkit.getPluginManager().callEvent(new PlayerInteractEntityEvent(player,stand,EquipmentSlot.HAND));
                check(magic.flyingStaff().isRiding(player),"remount works");
                magic.mana().account(player).setMana(2);
                later(50,()->{
                    check(!magic.flyingStaff().isRiding(player),"empty mana causes controlled landing");
                    check(!player.hasPermission("grim.disabled"),"landing removes anti-cheat exemption");
                    check(!player.getAllowFlight(),"landing leaves server flight disabled");
                    finish(null);
                });
            });
        });
    }
    void finish(Throwable error){
        try{Files.writeString(Path.of("flying-staff-result.txt"),error==null?"PASS "+checks:"FAIL "+error);}
        catch(Exception ignored){}
        if(error!=null)getLogger().severe("FLYING STAFF FAILED after "+checks+" checks: "+error);
        else getLogger().info("FLYING STAFF COMPLETE: "+checks+" checks");
        if(stand!=null&&stand.isValid())stand.remove();
        if(handle!=null){MinecraftServer.getServer().getPlayerList().getPlayers().remove(handle);MinecraftServer.getServer().getPlayerList().getPlayersByUUID().remove(handle.getUUID());handle.discard();}
        Bukkit.getScheduler().runTaskLater(this,Bukkit::shutdown,5);
    }
}
