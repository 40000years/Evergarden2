import com.example.advancemagic.AdvanceMagicPlugin;
import com.example.advancemagic.item.FlyingStaffService;
import com.google.gson.GsonBuilder;
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
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import java.lang.reflect.*;
import java.net.InetSocketAddress;
import java.nio.file.*;
import java.util.*;

/** Disposable real-Paper probe; no actual Bedrock login or physical gamepad. */
public final class FlyingStaffRuntimeChecks extends JavaPlugin implements Listener {
    static final class Capture extends Connection {
        Capture(){super(PacketFlow.SERVERBOUND);channel=new EmbeddedChannel();address=new InetSocketAddress("127.0.0.1",1);}
        @Override public void send(Packet<?> packet){}
        @Override public void send(Packet<?> packet,ChannelFutureListener listener){}
        @Override public void send(Packet<?> packet,ChannelFutureListener listener,boolean flush){}
        @Override public boolean isConnected(){return true;}
    }
    AdvanceMagicPlugin magic;
    FlyingStaffService staff;
    World world;
    Player player,other;
    final Map<UUID,ServerPlayer> actors=new HashMap<>();
    final List<Map<String,Object>> results=new ArrayList<>();
    final List<String> failures=new ArrayList<>(),observations=new ArrayList<>();
    int flightTeleports;
    int inputMatrixCases;
    boolean finished;
    @Override public void onEnable(){Bukkit.getPluginManager().registerEvents(this,this);later(25,this::begin);}
    void check(boolean value,String label){results.add(Map.of("passed",value,"case",label));if(!value)failures.add(label);getLogger().info((value?"PASS ":"FAIL ")+label);}
    void observe(String label){observations.add(label);getLogger().info("OBSERVED "+label);}
    void later(int ticks,Runnable task){Bukkit.getScheduler().runTaskLater(this,()->{try{task.run();}catch(Throwable failure){failure.printStackTrace();failures.add("Harness exception: "+failure);finish();}},ticks);}
    static Object field(Object object,String name)throws Exception{var f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}
    static void set(Object object,String name,Object value)throws Exception{var f=object.getClass().getDeclaredField(name);f.setAccessible(true);f.set(object,value);}
    Object session(Player owner)throws Exception{return ((Map<?,?>)field(staff,"sessions")).get(owner.getUniqueId());}
    ArmorStand stand(Player owner)throws Exception{Object s=session(owner);return s==null?null:(ArmorStand)field(s,"stand");}
    Object invoke(String name,Class<?>[] types,Object...args)throws Exception{var m=staff.getClass().getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(staff,args);}
    void tick(int count){for(int i=0;i<count;i++)staff.tick();}
    Location at(double x,double y,double z){return new Location(world,x,y,z,0,0);}
    Player actor(String name,Location location){
        var server=MinecraftServer.getServer();var level=((CraftWorld)world).getHandle();
        var profile=new GameProfile(UUID.randomUUID(),name);var handle=new ServerPlayer(server,level,profile,ClientInformation.createDefault());
        handle.connection=new ServerGamePacketListenerImpl(server,new Capture(),handle,CommonListenerCookie.createInitial(profile,false)){
            @Override public boolean hasClientLoaded(){return true;}
        };
        handle.setPos(location.getX(),location.getY(),location.getZ());
        server.getPlayerList().getPlayers().add(handle);server.getPlayerList().getPlayersByUUID().put(profile.id(),handle);level.addNewPlayer(handle);
        Player result=handle.getBukkitEntity();actors.put(result.getUniqueId(),handle);result.setOp(false);result.setGameMode(GameMode.SURVIVAL);result.setGravity(false);result.teleport(location);return result;
    }
    void input(boolean f,boolean b,boolean l,boolean r,boolean j,boolean s,boolean sprint){
        actors.get(player.getUniqueId()).connection.handlePlayerInput(new ServerboundPlayerInputPacket(new Input(f,b,l,r,j,s,sprint)));
    }
    void neutral(){input(false,false,false,false,false,false,false);player.setSprinting(false);}
    void close()throws Exception{invoke("closeOwner",new Class<?>[]{Player.class},player);neutral();}
    void prepare(Location origin)throws Exception{
        close();player.getInventory().clear();player.getInventory().setItemInMainHand(staff.create());player.teleport(origin);
        player.removePotionEffect(PotionEffectType.SLOW_FALLING);magic.mana().account(player).setMana(100);
    }
    PlayerInteractEvent use(Action action,EquipmentSlot hand){
        var event=new PlayerInteractEvent(player,action,player.getInventory().getItemInMainHand(),action==Action.RIGHT_CLICK_BLOCK?world.getBlockAt(0,99,0):null,org.bukkit.block.BlockFace.UP,hand);
        Bukkit.getPluginManager().callEvent(event);return event;
    }
    void use(){use(Action.RIGHT_CLICK_AIR,EquipmentSlot.HAND);}
    void mount(Player rider,ArmorStand mount){Bukkit.getPluginManager().callEvent(new PlayerInteractEntityEvent(rider,mount,EquipmentSlot.HAND));}
    ArmorStand flight(Location origin,boolean bedrock)throws Exception{
        prepare(origin);use();Object s=session(player);if(s==null)throw new IllegalStateException("Fixture summon failed at "+origin);
        set(s,"bedrock",bedrock);tick(12);ArmorStand mount=stand(player);mount(player,mount);
        if(!staff.isRiding(player))throw new IllegalStateException("Fixture mount failed");return mount;
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void teleport(PlayerTeleportEvent event){if(player!=null&&event.getPlayer()==player&&staff!=null&&staff.isRiding(player))flightTeleports++;}
    void begin(){try{
        magic=(AdvanceMagicPlugin)Bukkit.getPluginManager().getPlugin("advance-magic");staff=magic.flyingStaff();
        check(magic.isEnabled(),"Advance Magic boots on Paper "+Bukkit.getMinecraftVersion());
        // Drive production tick methods explicitly so regen/scheduled effects cannot skew assertions.
        Bukkit.getScheduler().cancelTasks(magic);
        world=Bukkit.getWorlds().getFirst();world.setGameRule(GameRule.DO_MOB_SPAWNING,false);
        for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)world.getChunkAt(x,z).addPluginChunkTicket(this);
        for(int x=-24;x<=24;x++)for(int z=-24;z<=24;z++){
            world.getBlockAt(x,99,z).setType(Material.STONE,false);
            for(int y=100;y<=112;y++)world.getBlockAt(x,y,z).setType(Material.AIR,false);
        }
        player=actor("FlightActor",at(.5,100,.5));other=actor("FlightOther",at(4.5,100,.5));
        check(!player.getAllowFlight(),"allow-flight=false; actor has no Bukkit flight permission");
        GeyserGamepadChecks.run(this::check);
        summonCases();
        ArmorStand mount=flight(at(.5,105,.5),true);
        FlyingStaffControlChecks.run(magic,player,(ok,label)->{
            check(ok,label);var count=java.util.regex.Pattern.compile("\\((\\d+) cases").matcher(label);
            if(count.find())inputMatrixCases+=Integer.parseInt(count.group(1));
        });
        packetMovementCases();collisionCases();manaCases();dismountCases();
        FlyingStaffAdditionalChecks.run(magic,player,other,this::check,this::observe);
        flight(at(10.5,110,.5),true);player.leaveVehicle();
        check(player.getVehicle()==null,"Bedrock void dismount releases passenger before safe-return task");
        later(2,this::afterVoidReturn);
    }catch(Throwable failure){failure.printStackTrace();failures.add("Harness exception: "+failure);finish();}}
    void summonCases()throws Exception{
        prepare(at(.5,100,.5));var item=player.getInventory().getItemInMainHand();int initial=magic.wands().usesLeft(item);
        use(Action.RIGHT_CLICK_AIR,EquipmentSlot.OFF_HAND);check(session(player)==null,"offhand use does not summon");
        var permission=player.addAttachment(this,"advance-magic.flying-staff",false);use();check(session(player)==null,"missing permission blocks summon");player.removeAttachment(permission);
        player.setGameMode(GameMode.SPECTATOR);use();check(session(player)==null,"spectator cannot summon");player.setGameMode(GameMode.SURVIVAL);
        item.setAmount(2);player.getInventory().setItemInMainHand(item);use();check(session(player)==null,"stacked staff cannot summon");item.setAmount(1);player.getInventory().setItemInMainHand(item);
        var denied=new PlayerInteractEvent(player,Action.RIGHT_CLICK_BLOCK,item,world.getBlockAt(0,99,0),org.bukkit.block.BlockFace.UP,EquipmentSlot.HAND);denied.setUseItemInHand(Event.Result.DENY);Bukkit.getPluginManager().callEvent(denied);
        check(session(player)==null,"protected block interaction DENY does not summon");
        world.getBlockAt(0,100,2).setType(Material.STONE);use();check(session(player)==null&&magic.wands().usesLeft(player.getInventory().getItemInMainHand())==initial,"obstructed summon costs no durability");world.getBlockAt(0,100,2).setType(Material.AIR);
        var air=new PlayerInteractEvent(player,Action.RIGHT_CLICK_AIR,item,null,null,EquipmentSlot.HAND);air.setCancelled(true);Bukkit.getPluginManager().callEvent(air);
        ArmorStand mount=stand(player);check(mount!=null,"pre-cancelled vanilla air-use still summons custom staff");
        UUID id=mount.getUniqueId();check(magic.wands().usesLeft(player.getInventory().getItemInMainHand())==initial-1,"successful summon costs exactly one use");
        mount(player,mount);check(!staff.isRiding(player),"cannot mount during 12-tick summon animation");
        for(int i=0;i<4;i++){tick(2);use();}
        check(stand(player).getUniqueId().equals(id),"repeated use recalls same entity without duplication");
        int repeatUses=magic.wands().usesLeft(player.getInventory().getItemInMainHand());
        check(repeatUses==initial-1&&((Number)field(session(player),"age")).intValue()==8,"repeated summon input neither consumes durability nor resets animation");
        mount(player,mount);check(!staff.isRiding(player),"mounting waits for the original summon animation to settle");tick(12);
        mount(other,mount);check(other.getVehicle()==null,"other player cannot mount owned staff");
        var attack=new EntityDamageByEntityEvent(other,mount,EntityDamageEvent.DamageCause.ENTITY_ATTACK,1);Bukkit.getPluginManager().callEvent(attack);check(attack.isCancelled()&&mount.isValid(),"other player cannot damage or dismiss staff");
        var saved=player.getInventory().getItemInMainHand();player.getInventory().clear();mount(player,mount);check(!staff.isRiding(player),"mounting requires staff still in inventory");player.getInventory().setItemInMainHand(saved);
        magic.mana().account(player).setMana(0);mount(player,mount);check(!staff.isRiding(player),"zero mana blocks mounting");magic.mana().account(player).setMana(100);
        mount(player,mount);check(staff.isRiding(player)&&player.hasPermission("grim.disabled"),"owner mounts with temporary anti-cheat permission");
        tick(8);boolean old=(boolean)field(session(player),"manualTurbo");use();check((boolean)field(session(player),"manualTurbo")!=old,"fresh riding use toggles Turbo rather than recalling");
        for(int repeat=0;repeat<5;repeat++){tick(4);use();}
        check((boolean)field(session(player),"manualTurbo")!=old,"held Use repeats do not oscillate Turbo");
        tick(8);use();check((boolean)field(session(player),"manualTurbo")==old,"released then pressed Use can toggle Turbo off");
        var blockedAttack=new EntityDamageByEntityEvent(player,other,EntityDamageEvent.DamageCause.ENTITY_ATTACK,1);Bukkit.getPluginManager().callEvent(blockedAttack);check(blockedAttack.isCancelled(),"rider cannot attack while movement exemption is active");
        close();prepare(at(.5,100,.5));item=player.getInventory().getItemInMainHand();while(magic.wands().usesLeft(item)>1)magic.wands().consumeUse(item);player.getInventory().setItemInMainHand(item);use();tick(12);mount(player,stand(player));
        check(staff.isRiding(player)&&magic.wands().usesLeft(player.getInventory().getItemInMainHand())==0,"last durability use still permits mounting the summoned staff");
        close();use();check(session(player)==null,"zero-durability staff cannot summon again");
        prepare(at(.5,100,.5));use();tick(12);int beforeBoard=magic.wands().usesLeft(player.getInventory().getItemInMainHand());
        for(int y=100;y<=103;y++)world.getBlockAt(0,y,1).setType(Material.STONE,false);
        use();check(!staff.isRiding(player)&&magic.wands().usesLeft(player.getInventory().getItemInMainHand())==beforeBoard,"nearby air-use cannot board through an intervening wall or charge durability");
        for(int y=100;y<=103;y++)world.getBlockAt(0,y,1).setType(Material.AIR,false);
        tick(8);use();
        check(staff.isRiding(player)&&magic.wands().usesLeft(player.getInventory().getItemInMainHand())==beforeBoard,"nearby air-use boards without aiming or spending another durability use");
        prepare(at(.5,100,.5));world.getWorldBorder().setCenter(0,0);world.getWorldBorder().setSize(2);use();check(session(player)==null,"summon outside world border is denied");world.getWorldBorder().setSize(59999968);
        prepare(at(.5,world.getMaxHeight()-2,.5));use();check(session(player)==null,"summon above build-height clearance is denied");
    }
    void packetMovementCases()throws Exception{
        for(int yaw:new int[]{0,90,180,270})for(boolean right:new boolean[]{false,true}){
            ArmorStand mount=flight(at(.5,105,.5),true);var handle=actors.get(player.getUniqueId());handle.setYRot(yaw);handle.setYHeadRot(yaw);handle.setXRot(0);
            check(Math.abs(Math.IEEEremainder(player.getLocation().getYaw()-yaw,360))<.01,"packet movement fixture sets camera yaw "+yaw);
            Location start=mount.getLocation();input(false,false,!right,right,false,false,false);tick(5);Vector delta=mount.getLocation().toVector().subtract(start.toVector());
            double angle=Math.toRadians(yaw+(right?90:-90));Vector expected=new Vector(-Math.sin(angle),0,Math.cos(angle));
            check(delta.dot(expected)>.5,"actual input packets move "+(right?"right":"left")+" at yaw "+yaw);
        }
        ArmorStand mount=flight(at(.5,105,.5),true);Location start=mount.getLocation();input(true,false,false,false,false,false,false);tick(10);
        double normal=mount.getLocation().getZ()-start.getZ();check(normal>3&&normal<4.9,"forward input ramps toward normal speed");
        neutral();tick(8);Location stop=mount.getLocation();tick(8);check(mount.getLocation().distanceSquared(stop)<.0001,"release stops and hovers without persistent drift");
        input(false,false,false,false,true,false,false);double beforeY=mount.getLocation().getY();tick(6);check(mount.getLocation().getY()>beforeY+.7,"jump packets raise staff");
        neutral();tick(5);actors.get(player.getUniqueId()).setXRot(36);input(true,false,false,false,false,false,false);beforeY=mount.getLocation().getY();tick(6);check(mount.getLocation().getY()<beforeY-.35,"forward plus downward camera smoothly lowers Bedrock staff");
        neutral();tick(5);actors.get(player.getUniqueId()).setXRot(-36);input(true,false,false,false,false,false,false);beforeY=mount.getLocation().getY();tick(6);check(mount.getLocation().getY()>beforeY+.35,"forward plus upward camera raises Bedrock staff without Jump");
        check(flightTeleports==0,"ordinary flight never fires rider teleport events");check(!player.getAllowFlight()&&!player.isFlying(),"movement does not grant Bukkit free flight");
    }
    void move(ArmorStand mount,Vector requested)throws Exception{Object s=session(player);invoke("move",new Class<?>[]{s.getClass(),Player.class,Vector.class},s,player,requested);}
    void collisionCases()throws Exception{
        ArmorStand mount=flight(at(.5,105,.5),true);for(int y=104;y<=109;y++)for(int x=-2;x<=2;x++)world.getBlockAt(x,y,4).setType(Material.STONE,false);
        move(mount,new Vector(0,0,2.4));check(mount.getLocation().getZ()<3.66,"maximum-speed sweep cannot skip solid wall");
        Location before=mount.getLocation();move(mount,new Vector(.7,0,.7));check(mount.getLocation().getX()>before.getX()+.3&&mount.getLocation().getZ()<3.66,"blocked movement slides along wall");
        for(int y=104;y<=109;y++)for(int x=-2;x<=2;x++)world.getBlockAt(x,y,4).setType(Material.AIR,false);
        mount=flight(at(.5,100,.5),true);world.getBlockAt(0,100,3).setType(Material.STONE,false);move(mount,new Vector(0,0,1.5));check(mount.getLocation().getY()>100.5&&mount.getLocation().getZ()>3,"one-block terrain rise is climbed");world.getBlockAt(0,100,3).setType(Material.AIR,false);
        mount=flight(at(.5,100,.5),true);for(int y=100;y<=102;y++)world.getBlockAt(0,y,3).setType(Material.STONE,false);move(mount,new Vector(0,0,1.5));check(mount.getLocation().getZ()<2.66,"tall obstacle stops low flight");for(int y=100;y<=102;y++)world.getBlockAt(0,y,3).setType(Material.AIR,false);
        mount=flight(at(.5,105,.5),true);world.getWorldBorder().setCenter(0,0);world.getWorldBorder().setSize(6);move(mount,new Vector(0,0,2.4));check(mount.getLocation().getZ()<=3,"movement stops at world border");world.getWorldBorder().setSize(59999968);
        boolean unloaded=(boolean)invoke("clear",new Class<?>[]{Location.class},at(100000,105,100000));check(!unloaded&&!world.isChunkLoaded(6250,6250),"unloaded destinations are rejected without loading chunks");
    }
    void manaCases()throws Exception{
        flight(at(.5,105,.5),true);double before=magic.mana().account(player).manaExact();tick(20);check(Math.abs(before-magic.mana().account(player).manaExact()-2)<.0001,"hover costs 2 mana per 20 ticks");
        input(false,false,false,false,false,false,true);before=magic.mana().account(player).manaExact();tick(20);check(Math.abs(before-magic.mana().account(player).manaExact()-3)<.0001,"Sprint Turbo costs 3 mana per 20 ticks");
        neutral();staff.toggleTurbo(player);before=magic.mana().account(player).manaExact();tick(3);check(Math.abs(before-magic.mana().account(player).manaExact()-.45)<.0001,"short manual Turbo burst charges proportional mana");
        staff.toggleTurbo(player);magic.mana().account(player).setMana(.05);tick(1);check(field(session(player),"phase").toString().equals("LANDING"),"empty mana starts automatic landing above ground");tick(200);check(!staff.isRiding(player)&&!player.hasPermission("grim.disabled"),"automatic landing releases rider and anti-cheat permission");
    }
    void dismountCases()throws Exception{
        ArmorStand mount=flight(at(.5,110,.5),true);player.leaveVehicle();Object s=session(player);
        check(!staff.isRiding(player)&&field(s,"phase").toString().equals("LANDING")&&(boolean)field(s,"landingUnridden"),"Bedrock high dismount releases rider and lands staff separately");
        check(player.hasPotionEffect(PotionEffectType.SLOW_FALLING)&&player.getPotionEffect(PotionEffectType.SLOW_FALLING).getDuration()>=1398,"Bedrock high dismount grants long Slow Falling");check(!player.hasPermission("grim.disabled"),"Bedrock dismount removes anti-cheat exemption immediately");
        mount=flight(at(.5,110,.5),false);player.leaveVehicle();check(staff.isRiding(player)&&field(session(player),"phase").toString().equals("LANDING"),"Java high dismount keeps rider seated during controlled landing");
        mount=flight(at(.5,100,.5),true);player.leaveVehicle();check(player.getVehicle()==null&&field(session(player),"phase").toString().equals("IDLE"),"near-ground Bedrock dismount releases rider normally");
        // Remove one support column down to the world floor to exercise actual void checks.
        for(int y=world.getMinHeight();y<=100;y++)world.getBlockAt(10,y,2).setType(Material.AIR,false);
        mount=flight(at(10.5,110,.5),false);var event=new EntityDismountEvent(player,mount);Bukkit.getPluginManager().callEvent(event);check(event.isCancelled()&&staff.isRiding(player),"Java void dismount is blocked while still mounted");
        set(session(player),"bedrock",true);magic.mana().account(player).setMana(.01);tick(1);check(session(player)==null&&player.getVehicle()==null&&player.getLocation().distanceSquared(at(10.5,110,.5))<1,"empty mana above void returns to summon point and removes mount");
        flight(at(10.5,110,.5),true);player.getInventory().clear();tick(1);
        check(session(player)==null&&player.getVehicle()==null&&player.getLocation().distanceSquared(at(10.5,110,.5))<1,"losing staff above void returns rider safely instead of leaving a slow fall into void");
        flight(at(10.5,110,.5),true);magic.getConfig().set("flying-staff.enabled",false);
        try{tick(1);check(session(player)==null&&player.getLocation().distanceSquared(at(10.5,110,.5))<1,"disabling feature mid-flight above void returns rider safely");}
        finally{magic.getConfig().set("flying-staff.enabled",true);}
    }
    void afterVoidReturn(){try{
        check(session(player)==null&&player.getLocation().distanceSquared(at(10.5,110,.5))<1,"Bedrock void dismount returns to summon point on next scheduler tick");
        // Let Paper's real passenger tick run before examining seat and camera bounds.
        flight(at(.5,100,.5),true);later(3,this::passengerAndLifecycleCases);
    }catch(Exception failure){throw new RuntimeException(failure);}}
    void passengerAndLifecycleCases(){try{
        ArmorStand mount=stand(player);double seat=player.getBoundingBox().getMaxY()-mount.getLocation().getY();observe("Actual passenger bounding-box top relative to stand: "+seat+" blocks; clear() checks to +3.3");
        check(player.getVehicle()==mount&&player.getLocation().distanceSquared(mount.getLocation())<9,"real Paper passenger tick retains the rider on mount");
        var roof=world.getBlockAt(mount.getLocation().getBlockX(),103,mount.getLocation().getBlockZ());roof.setType(Material.STONE,false);
        boolean bodyOverlap=player.getBoundingBox().overlaps(roof.getBoundingBox());
        boolean accepted=(boolean)invoke("clear",new Class<?>[]{Location.class},mount.getLocation());
        check(!bodyOverlap||!accepted,"mount collision clearance rejects a roof intersecting the seated rider's head");
        observe("Roof at mount Y+3: actual rider overlaps="+bodyOverlap+", production clear() accepts="+accepted);roof.setType(Material.AIR,false);
        player.getInventory().clear();tick(1);check(session(player)==null&&!mount.isValid()&&player.hasPotionEffect(PotionEffectType.SLOW_FALLING)&&player.getPotionEffect(PotionEffectType.SLOW_FALLING).getDuration()>=1398,"losing staff cleans mount and grants long Slow Falling");
        mount=flight(at(.5,100,.5),true);player.teleport(at(8.5,100,.5));check(session(player)==null&&!mount.isValid(),"real long-distance teleport cleans owned mount");
        mount=flight(at(.5,100,.5),true);Bukkit.getPluginManager().callEvent(new PlayerQuitEvent(player,"probe",PlayerQuitEvent.QuitReason.DISCONNECTED));check(session(player)==null&&!mount.isValid(),"quit cleans owned mount");
        mount=flight(at(.5,100,.5),true);
        var bridge=(com.example.advancemagic.item.FlyingStaffBedrockInput)field(staff,"bedrockInput");
        var offlineHandle=actors.get(player.getUniqueId());var players=MinecraftServer.getServer().getPlayerList();
        try(var binding=GeyserGamepadChecks.bindAnalog(bridge,player.getUniqueId(),0,.5,false,false,0)){
            players.getPlayersByUUID().remove(player.getUniqueId());players.getPlayers().remove(offlineHandle);
            try{tick(1);check(session(player)==null&&!mount.isValid()&&bridge.sample(player)==null&&!player.hasPermission("grim.disabled"),"offline-owner cleanup removes analog input and permissions without a Bukkit player");}
            finally{players.getPlayersByUUID().put(player.getUniqueId(),offlineHandle);players.getPlayers().add(offlineHandle);}
        }
        mount=flight(at(.5,100,.5),true);staff.close();check(session(player)==null&&!mount.isValid()&&!player.hasPermission("grim.disabled"),"plugin shutdown closes mount and permission attachment");
        finish();
    }catch(Throwable failure){failure.printStackTrace();failures.add("Harness exception: "+failure);finish();}}
    void finish(){if(finished)return;finished=true;try{
        var report=new LinkedHashMap<String,Object>();report.put("paper",Bukkit.getVersion());report.put("plugin",magic==null?"unavailable":magic.getPluginMeta().getVersion());
        report.put("checks",results.size());report.put("input_matrix_cases",inputMatrixCases);report.put("passed",results.stream().filter(r->Boolean.TRUE.equals(r.get("passed"))).count());report.put("failures",failures);report.put("observations",observations);report.put("results",results);
        report.put("limits",List.of("No physical controller, Bedrock rendered client, network latency simulation, or production server access.","Bedrock lifecycle branch selected explicitly for a simulated Paper actor; Geyser conversion tested independently.","Production ticks driven explicitly for deterministic mana and movement measurements."));
        Files.writeString(Path.of("flying-staff-controls-result.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));
    }catch(Exception failure){failure.printStackTrace();}finally{Bukkit.shutdown();}}
}
