import com.example.advancemagic.AdvanceMagicPlugin;
import com.example.advancemagic.item.FlyingStaffService;
import com.mojang.authlib.GameProfile;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.server.network.*;
import net.minecraft.world.entity.player.Input;
import org.bukkit.*;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import java.nio.file.*;
import java.util.*;

/** Real Paper passenger movement, resource identity, mana, lifecycle and /evergarden test. */
public final class FlyingStaffTierTwoChecks extends JavaPlugin implements Listener {
    static final class Capture extends Connection {
        Capture(){super(PacketFlow.SERVERBOUND);channel=new EmbeddedChannel();address=new java.net.InetSocketAddress("127.0.0.1",1);}
        @Override public void send(Packet<?> p){}
        @Override public void send(Packet<?> p,ChannelFutureListener l){}
        @Override public void send(Packet<?> p,ChannelFutureListener l,boolean f){}
        @Override public boolean isConnected(){return true;}
    }
    AdvanceMagicPlugin magic;FlyingStaffService staff;World world;Player driver,guest,third;
    Map<UUID,ServerPlayer> actors=new HashMap<>();int checks,teleports;boolean finished;
    void check(boolean ok,String label){if(!ok)throw new AssertionError(label);checks++;}
    void near(double got,double want,String label){check(Math.abs(got-want)<.00001,label+": "+got+" expected "+want);}
    static Object field(Object o,String n)throws Exception{var f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o);}
    static void set(Object o,String n,Object v)throws Exception{var f=o.getClass().getDeclaredField(n);f.setAccessible(true);f.set(o,v);}
    Object invoke(String name,Class<?>[] types,Object...args)throws Exception{var m=staff.getClass().getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(staff,args);}
    Object session()throws Exception{return ((Map<?,?>)field(staff,"sessions")).get(driver.getUniqueId());}
    ArmorStand front()throws Exception{return (ArmorStand)field(session(),"stand");}
    ArmorStand rear()throws Exception{return (ArmorStand)field(session(),"rearSeat");}
    Location at(double x,double y,double z){return new Location(world,x,y,z,0,0);}
    void tick(int n){for(int i=0;i<n;i++)staff.tick();}
    void later(Runnable r){Bukkit.getScheduler().runTaskLater(this,()->{try{r.run();}catch(Throwable e){finish(e);}},4);}
    @Override public void onEnable(){Bukkit.getPluginManager().registerEvents(this,this);later(()->{try{begin();}catch(Throwable e){finish(e);}});}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)public void teleport(PlayerTeleportEvent e){if(staff!=null&&staff.isRiding(e.getPlayer()))teleports++;}
    Player actor(String name,Location loc){
        var server=MinecraftServer.getServer();var level=((CraftWorld)world).getHandle();var profile=new GameProfile(UUID.randomUUID(),name);
        var h=new ServerPlayer(server,level,profile,ClientInformation.createDefault());
        h.connection=new ServerGamePacketListenerImpl(server,new Capture(),h,CommonListenerCookie.createInitial(profile,false)){@Override public boolean hasClientLoaded(){return true;}};
        h.setPos(loc.getX(),loc.getY(),loc.getZ());server.getPlayerList().getPlayers().add(h);server.getPlayerList().getPlayersByUUID().put(profile.id(),h);level.addNewPlayer(h);
        Player p=h.getBukkitEntity();actors.put(p.getUniqueId(),h);p.setGameMode(GameMode.SURVIVAL);p.setGravity(false);p.teleport(loc);return p;
    }
    void input(Player p,int keys,boolean jump,boolean turbo){actors.get(p.getUniqueId()).connection.handlePlayerInput(new ServerboundPlayerInputPacket(new Input((keys&1)!=0,(keys&2)!=0,(keys&4)!=0,(keys&8)!=0,jump,false,turbo)));p.setSprinting(false);}
    void mount(Player p,ArmorStand a){Bukkit.getPluginManager().callEvent(new PlayerInteractEntityEvent(p,a,EquipmentSlot.HAND));}
    void prepare(boolean bedrock)throws Exception{
        invoke("closeOwner",new Class<?>[]{Player.class},driver);
        driver.getInventory().clear();guest.getInventory().clear();third.getInventory().clear();
        driver.teleport(at(.5,100,.5));guest.teleport(at(.5,100,.65));third.teleport(at(1.5,100,.65));
        input(driver,0,false,false);input(guest,0,false,false);driver.getInventory().setItemInMainHand(staff.create(2));
        magic.mana().account(driver).setMana(100);magic.mana().account(guest).setMana(77);
        var use=new PlayerInteractEvent(driver,Action.RIGHT_CLICK_AIR,driver.getInventory().getItemInMainHand(),null,null,EquipmentSlot.HAND);Bukkit.getPluginManager().callEvent(use);
        check(session()!=null,"tier two summons");set(session(),"bedrock",bedrock);tick(12);
        mount(guest,rear());check(!staff.isRiding(guest),"passenger waits for driver");
        mount(driver,front());check(staff.isRiding(driver),"driver boards tier two");
    }
    void begin()throws Exception{
        magic=(AdvanceMagicPlugin)Bukkit.getPluginManager().getPlugin("advance-magic");staff=magic.flyingStaff();
        Bukkit.getScheduler().cancelTasks(magic);world=Bukkit.getWorlds().getFirst();
        for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++)world.getChunkAt(x,z).addPluginChunkTicket(this);
        for(int x=-35;x<=35;x++)for(int z=-35;z<=35;z++){world.getBlockAt(x,99,z).setType(Material.STONE,false);for(int y=100;y<=119;y++)world.getBlockAt(x,y,z).setType(Material.AIR,false);}
        driver=actor("TandemPilot",at(.5,100,.5));guest=actor("TandemGuest",at(.5,100,.65));third=actor("TandemThird",at(1.5,100,.65));
        magic.getConfig().set("flying-staff.anticheat.bypass-permissions.FlyingStaffTierTwoChecks",List.of("tandemtest.bypass"));
        check(staff.tier(staff.create())==1,"legacy factory remains tier one");
        var item=staff.create(2);check(staff.tier(ItemStack.deserializeBytes(item.serializeAsBytes()))==2,"tier persists through serialization");
        check(item.getItemMeta().getItemModel().getKey().equals("flying_staff_tier2"),"separate held model");
        int uses=magic.wands().usesLeft(item);check(magic.wands().consumeUse(item)&&magic.wands().usesLeft(item)==uses-1,"tier two shares durability service");
        check(item.getItemMeta().getLore().stream().anyMatch(l->l.contains("50%")),"durability refresh keeps tandem lore");
        check(magic.wands().restore(item),"tier two accepts restoration");
        for(boolean bedrock:new boolean[]{false,true}){
            prepare(bedrock);ArmorStand f=front(),r=rear();
            check(f!=r&&!r.isVisible()&&staff.isDisplay(r),"distinct invisible protected rear seat");
            check(!driver.getAllowFlight()&&!guest.getAllowFlight(),"neither role gets Bukkit flight");
            near(f.getLocation().distance(r.getLocation()),1.35,"seat separation");
            var deny=guest.addAttachment(this,"advance-magic.flying-staff",false);mount(guest,r);check(!staff.isRiding(guest),"passenger permission checked");guest.removeAttachment(deny);
            guest.setGameMode(GameMode.SPECTATOR);mount(guest,r);check(!staff.isRiding(guest),"spectator rejected");guest.setGameMode(GameMode.SURVIVAL);
            mount(guest,f);check(staff.isRiding(guest)&&guest.getVehicle()==r,"click visible staff boards rear without an item");
            check(driver.hasPermission("tandemtest.bypass")&&guest.hasPermission("tandemtest.bypass"),"both roles receive movement exemption");
            mount(third,r);check(!staff.isRiding(third)&&r.getPassengers().size()==1,"third player cannot board");
            check(!staff.toggleTurbo(guest),"passenger cannot toggle pilot Turbo");
            var attack=new EntityDamageByEntityEvent(guest,third,EntityDamageEvent.DamageCause.ENTITY_ATTACK,1);Bukkit.getPluginManager().callEvent(attack);check(attack.isCancelled(),"passenger cannot attack");
            var damage=new EntityDamageEvent(r,EntityDamageEvent.DamageCause.FIRE,10);Bukkit.getPluginManager().callEvent(damage);check(damage.isCancelled(),"rear seat protected");
            speedMatrix();input(driver,0,false,false);actors.get(driver.getUniqueId()).setYRot(0);actors.get(driver.getUniqueId()).setYHeadRot(0);actors.get(driver.getUniqueId()).setXRot(0);
            double mana=magic.mana().account(driver).manaExact();tick(20);near(mana-magic.mana().account(driver).manaExact(),3,"tier two normal mana/sec");
            near(magic.mana().account(guest).manaExact(),77,"passenger mana untouched");
            check(staff.toggleTurbo(driver),"driver controls Turbo");mana=magic.mana().account(driver).manaExact();tick(20);near(mana-magic.mana().account(driver).manaExact(),4.5,"tier two Turbo mana/sec");staff.toggleTurbo(driver);
            int tp=teleports;input(driver,1,false,false);Location before=f.getLocation();tick(12);check(f.getLocation().distance(before)>1,"driver moves front seat");near(f.getLocation().distance(r.getLocation()),1.35,"rear follows without drift");check(teleports==tp,"movement never teleports either player");input(driver,0,false,false);tick(5);
            Location back=r.getLocation();var roof=world.getBlockAt(back.getBlockX(),back.getBlockY()+3,back.getBlockZ());roof.setType(Material.STONE,false);
            check((boolean)invoke("clear",new Class<?>[]{Location.class},f.getLocation()),"roof only intersects rear");
            check(!(boolean)invoke("clear",new Class<?>[]{Location.class,int.class},f.getLocation(),2),"rear head clearance rejects low roof");roof.setType(Material.AIR,false);
            guest.leaveVehicle();check(!staff.isRiding(guest)&&!guest.hasPermission("tandemtest.bypass")&&guest.hasPotionEffect(PotionEffectType.SLOW_FALLING),"passenger exits safely and revokes exemption");
            check(staff.isRiding(driver),"guest exit leaves driver flying");guest.teleport(rear().getLocation());mount(guest,r);check(staff.isRiding(guest),"rear can be occupied again");
            driver.getInventory().setItemInMainHand(staff.create());tick(1);check(session()==null&&!f.isValid()&&!r.isValid(),"tier one item cannot sustain tier two mount");
            check(!staff.isRiding(guest)&&!guest.hasPermission("tandemtest.bypass"),"owner loss releases passenger");
        }
        prepare(false);mount(guest,rear());
        Object turnSession=session();
        invoke("move",new Class<?>[]{turnSession.getClass(),Player.class,Vector.class},turnSession,driver,new Vector(0,5,0));
        var turnWall=world.getBlockAt(1,108,1);turnWall.setType(Material.STONE,false);
        actors.get(driver.getUniqueId()).setYRot(90);actors.get(driver.getUniqueId()).setYHeadRot(90);
        invoke("move",new Class<?>[]{turnSession.getClass(),Player.class,Vector.class},turnSession,driver,new Vector());
        check(rear().getLocation().getX()<1,"turn sweep stops rear seat before wall");
        turnWall.setType(Material.AIR,false);
        invoke("move",new Class<?>[]{turnSession.getClass(),Player.class,Vector.class},turnSession,driver,new Vector());
        near(rear().getLocation().getX(),1.85,"rear seat follows unobstructed turn");
        actors.get(driver.getUniqueId()).setYRot(0);actors.get(driver.getUniqueId()).setYHeadRot(0);
        for(int x=-2;x<=2;x++)for(int y=105;y<=109;y++)world.getBlockAt(x,y,-2).setType(Material.STONE,false);
        invoke("move",new Class<?>[]{turnSession.getClass(),Player.class,Vector.class},turnSession,driver,new Vector(0,0,-4));
        check(rear().getLocation().getZ()>=-.65001,"fast reverse sweep cannot carry rear rider through wall");
        for(int x=-2;x<=2;x++)for(int y=105;y<=109;y++)world.getBlockAt(x,y,-2).setType(Material.AIR,false);
        prepare(false);mount(guest,rear());ArmorStand f=front(),r=rear();
        Bukkit.getPluginManager().callEvent(new PlayerQuitEvent(guest,"probe",PlayerQuitEvent.QuitReason.DISCONNECTED));
        check(staff.isRiding(driver)&&!staff.isRiding(guest)&&!guest.hasPermission("tandemtest.bypass"),"passenger quit leaves flight intact");
        guest.teleport(r.getLocation());mount(guest,r);Bukkit.getPluginManager().callEvent(new PlayerQuitEvent(driver,"probe",PlayerQuitEvent.QuitReason.DISCONNECTED));
        check(session()==null&&!f.isValid()&&!r.isValid()&&!staff.isRiding(guest),"driver quit cleans both seats");
        // Remove the entire column below both seats, then exercise exhausted-mana safe return.
        prepare(false);mount(guest,rear());f=front();r=rear();
        for(int x=-1;x<=1;x++)for(int z=-1;z<=3;z++)for(int y=world.getMinHeight();y<100;y++)world.getBlockAt(x,y,z).setType(Material.AIR,false);
        // A safe summon/boarding origin is outside those removed columns.
        set(session(),"passengerReturn",at(6.5,100,6.5));((Location)field(session(),"returnLocation")).setX(5.5);
        magic.mana().account(driver).setMana(.01);tick(1);
        check(session()==null&&!f.isValid()&&!r.isValid(),"void mana exhaustion closes whole mount");
        check(driver.getLocation().getX()>4&&guest.getLocation().getX()>4&&!staff.isRiding(guest),"both riders returned from void safely");
        // Allow real Paper passenger ticks before observing actual rider bounds.
        for(int x=-1;x<=1;x++)for(int z=-1;z<=3;z++)world.getBlockAt(x,99,z).setType(Material.STONE,false);
        prepare(true);mount(guest,rear());later(this::realSeatAndGui);
    }
    void speedMatrix()throws Exception{
        Object s=session();
        for(float yaw:new float[]{-180,-135,-90,-45,0,45,90,135,180})for(int keys=0;keys<16;keys++)for(boolean turbo:new boolean[]{false,true}){
            actors.get(driver.getUniqueId()).setYRot(yaw);actors.get(driver.getUniqueId()).setYHeadRot(yaw);actors.get(driver.getUniqueId()).setXRot(0);input(driver,keys,true,turbo);
            Vector actual=(Vector)invoke("input",new Class<?>[]{Player.class,s.getClass()},driver,s);
            double a=((keys&1)!=0?1:0)-((keys&2)!=0?1:0),b=((keys&8)!=0?1:0)-((keys&4)!=0?1:0);double theta=Math.toRadians(yaw);
            Vector expected=new Vector(-Math.sin(theta)*a-Math.cos(theta)*b,0,Math.cos(theta)*a-Math.sin(theta)*b);
            if(expected.lengthSquared()>1)expected.normalize();expected.multiply(.243*(turbo?4:1)).setY(.144*(turbo?4:1));
            near(actual.distance(expected),0,"tier two input matrix half speed");
        }
        input(driver,0,false,false);
    }
    void realSeatAndGui(){try{
        near(front().getLocation().distance(rear().getLocation()),1.35,"real server tick retains seat separation");
        check(driver.getVehicle()==front()&&guest.getVehicle()==rear(),"real Paper tick retains both passengers");
        check(driver.getBoundingBox().getMaxY()<=front().getLocation().getY()+3.3&&guest.getBoundingBox().getMaxY()<=rear().getLocation().getY()+3.3,"collision clearance covers actual seated heads");
        invoke("closeOwner",new Class<?>[]{Player.class},driver);driver.setOp(true);driver.getInventory().clear();
        check(Bukkit.dispatchCommand(driver,"evergarden test"),"evergarden test command opens");
        var top=driver.getOpenInventory().getTopInventory();check(top.getSize()==54&&top.getItem(15).getItemMeta().getDisplayName().contains("1–2"),"Evergarden test advertises both flight tiers");
        click(15);later(()->{try{
            var menu=driver.getOpenInventory().getTopInventory();check(menu.getSize()==27&&staff.tier(menu.getItem(20))==2,"test navigation opens tandem staff tile");
            check(staff.isStaff(menu.getItem(26))&&staff.tier(menu.getItem(26))==1,"original flying staff tile retained");
            click(20);check(Arrays.stream(driver.getInventory().getContents()).anyMatch(i->staff.isStaff(i)&&staff.tier(i)==2),"admin click grants usable tier two staff");
            finish(null);
        }catch(Throwable e){finish(e);}});
    }catch(Throwable e){finish(e);}}
    void click(int slot){var v=driver.getOpenInventory();Bukkit.getPluginManager().callEvent(new InventoryClickEvent(v,InventoryType.SlotType.CONTAINER,slot,ClickType.LEFT,InventoryAction.PICKUP_ALL));}
    void finish(Throwable error){if(finished)return;finished=true;if(error!=null)getLogger().log(java.util.logging.Level.SEVERE,"TANDEM CHECK FAILED",error);
        try{Files.writeString(Path.of("flying-staff-tier2-result.txt"),(error==null?"PASS ":"FAIL ")+checks+" checks; tandem seats, half-speed matrix, 150% mana, collision, lifecycle, void return and Evergarden test"+(error==null?"":"; "+error));}catch(Exception ignored){}
        Bukkit.getScheduler().runTaskLater(this,Bukkit::shutdown,2);
    }
}
