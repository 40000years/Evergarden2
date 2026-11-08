import com.example.voidscape.VoidscapePlugin;
import com.example.voidscape.boss.RealmBossController;
import com.example.voidscape.world.DimensionalSanctuaries;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.server.network.*;
import org.bukkit.*;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.util.Vector;
import org.bukkit.plugin.java.JavaPlugin;
import java.net.InetSocketAddress;
import java.nio.file.*;
import java.util.*;

/** Exercises real Paper melee attacks against the same virtual HP receiver as realm encounters. */
public final class RealmMeleeProbe extends JavaPlugin implements Listener {
    VoidscapePlugin garden; RealmBossController boss; Player player, secondPlayer; ServerPlayer actor, secondActor; int checks; boolean done,cancelNext;
    java.lang.reflect.Field ticker;
    double lastNativeDamage; org.bukkit.damage.DamageType lastNativeType;
    final List<String> lines=new ArrayList<>();
    void check(boolean ok,String label){if(!ok)throw new AssertionError(label);checks++;lines.add("PASS: "+label);getLogger().info("PASS: "+label);}
    @Override public void onEnable(){Bukkit.getPluginManager().registerEvents(this,this); Bukkit.getScheduler().runTaskLater(this,()->{try{run();}catch(Throwable e){finish(e);}},100);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void hit(EntityDamageEvent e){if(boss!=null&&e.getEntity()==boss.carrier()){getLogger().info("hit cause="+e.getCause()+" source="+e.getDamageSource().getDamageType()+" damage="+e.getFinalDamage()+" attacker="+attacker(e));lastNativeDamage=e.getFinalDamage();lastNativeType=e.getDamageSource().getDamageType();boss.handleIncomingDamage(e,attacker(e));}}
    @EventHandler(priority=EventPriority.LOWEST,ignoreCancelled=true)
    public void reject(EntityDamageEvent e){if(boss!=null&&e.getEntity()==boss.carrier()){
        if(cancelNext){cancelNext=false;e.setCancelled(true);return;}
        if(boss.rejectRapidMelee(e,attacker(e)))e.setCancelled(true);
    }}
    Player attacker(EntityDamageEvent e){return e.getDamageSource().getCausingEntity() instanceof Player p?p:null;}
    void later(int ticks,Runnable action){Bukkit.getScheduler().runTaskLater(this,()->{try{action.run();}catch(Throwable e){finish(e);}},ticks);}
    void ready(){try{ticker.setInt(actor,100);}catch(ReflectiveOperationException e){throw new RuntimeException(e);}}
    void run() throws Exception {
        garden=(VoidscapePlugin)Bukkit.getPluginManager().getPlugin("Evergarden");
        World world=Bukkit.getWorlds().getFirst(); Location at=new Location(world,256.5,120,256.5);
        for(int x=15;x<=16;x++)for(int z=15;z<=16;z++)world.addPluginChunkTicket(x,z,this);
        for(int x=250;x<=263;x++)for(int z=250;z<=263;z++)world.getBlockAt(x,119,z).setType(Material.STONE,false);
        var server=MinecraftServer.getServer();var level=((CraftWorld)world).getHandle();var profile=new GameProfile(UUID.randomUUID(),"RealmMelee");
        actor=new ServerPlayer(server,level,profile,ClientInformation.createDefault());var connection=new Connection(PacketFlow.SERVERBOUND);
        connection.channel=new EmbeddedChannel();connection.address=new InetSocketAddress("127.0.0.1",1);
        actor.connection=new ServerGamePacketListenerImpl(server,connection,actor,CommonListenerCookie.createInitial(profile,false)){@Override public boolean hasClientLoaded(){return true;}};
        actor.setPos(256.5,120,253.5);server.getPlayerList().getPlayers().add(actor);server.getPlayerList().getPlayersByUUID().put(profile.id(),actor);level.addNewPlayer(actor);
        player=actor.getBukkitEntity();player.setGameMode(GameMode.SURVIVAL);player.setGravity(false);
        var secondProfile=new GameProfile(UUID.randomUUID(),"RealmMelee2");secondActor=new ServerPlayer(server,level,secondProfile,ClientInformation.createDefault());
        var secondConnection=new Connection(PacketFlow.SERVERBOUND);secondConnection.channel=new EmbeddedChannel();secondConnection.address=new InetSocketAddress("127.0.0.1",2);
        secondActor.connection=new ServerGamePacketListenerImpl(server,secondConnection,secondActor,CommonListenerCookie.createInitial(secondProfile,false)){@Override public boolean hasClientLoaded(){return true;}};
        secondActor.setPos(258.5,120,253.5);server.getPlayerList().getPlayers().add(secondActor);server.getPlayerList().getPlayersByUUID().put(secondProfile.id(),secondActor);level.addNewPlayer(secondActor);
        secondPlayer=secondActor.getBukkitEntity();secondPlayer.setGameMode(GameMode.SURVIVAL);secondPlayer.setGravity(false);secondPlayer.getInventory().setItemInMainHand(new ItemStack(Material.DIAMOND_SWORD));secondActor.detectEquipmentUpdates();
        var site=new DimensionalSanctuaries.Site(DimensionalSanctuaries.Kind.CROWN_FORTRESS,256,120,256,0,0,1);
        boss=new RealmBossController(garden,site,new DimensionalSanctuaries.Arena(256,120,256,30,20),at,p->true,d->{});boss.spawnWaiting(1);boss.awaken(List.of(player,secondPlayer));
        player.getInventory().setItemInMainHand(new ItemStack(Material.DIAMOND_SWORD));
        actor.detectEquipmentUpdates();
        ticker=null;
        for(Class<?> type=actor.getClass();type!=null&&ticker==null;type=type.getSuperclass())for(var f:type.getDeclaredFields())if(f.getName().equals("attackStrengthTicker")){ticker=f;f.setAccessible(true);}
        if(ticker==null)throw new AssertionError("Paper attack cooldown field missing");
        ready();double before=boss.health();cancelNext=true;player.attack(boss.carrier());
        check(boss.health()==before,"cancelled hit leaves boss HP unchanged");
        ready();before=boss.health();player.attack(boss.carrier());double full=before-boss.health();
        check(full>0,"cancelled hit does not consume the player's melee window");
        before=boss.health();player.attack(boss.carrier());double fast=before-boss.health();
        check(full>0&&fast==0,"second same-tick sword hit is blocked, first hit="+full);
        ticker.setInt(secondActor,100);before=boss.health();secondPlayer.attack(boss.carrier());
        check(boss.health()<before,"different players have independent melee windows");
        before=boss.health();boss.carrier().damage(4,player);
        check(boss.health()<before,"player-attributed scripted damage is not blocked by melee window");
        later(10,()->{
            try{ticker.setInt(actor,7);}catch(ReflectiveOperationException e){throw new RuntimeException(e);}
            double b=boss.health();player.attack(boss.carrier());double weak=b-boss.health();
            check(weak>0&&weak<full,"hit after 10 ticks is accepted at the weapon's current strength: "+weak);
            later(10,()->limitBreak(world,full));
        });
    }
    void limitBreak(World world,double normalFull){
        ItemStack special=new ItemStack(Material.DIAMOND_SWORD);var meta=special.getItemMeta();meta.getPersistentDataContainer().set(garden.key("lb_sharpness"),PersistentDataType.INTEGER,10);special.setItemMeta(meta);player.getInventory().setItemInMainHand(special);actor.detectEquipmentUpdates();
        Zombie target=world.spawn(player.getLocation().clone().add(0,0,2),Zombie.class,z->{z.setAI(false);z.setMaximumNoDamageTicks(0);z.setNoDamageTicks(0);z.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).setBaseValue(200);z.setHealth(200);});
        ready();double start=target.getHealth();player.attack(target);double full=start-target.getHealth();
        target.setNoDamageTicks(0);start=target.getHealth();player.attack(target);double rapid=start-target.getHealth();
        check(full>normalFull&&rapid<full*.35,"Limit Break Sharpness X scales with cooldown on ordinary mobs: full="+full+" rapid="+rapid);
        target.remove();fastWeapon(world);
    }
    void fastWeapon(World world){
        player.getInventory().setItemInMainHand(new ItemStack(Material.DIAMOND_SWORD));
        player.getAttribute(org.bukkit.attribute.Attribute.ATTACK_SPEED).setBaseValue(40);actor.detectEquipmentUpdates();
        Zombie target=world.spawn(player.getLocation().clone().add(0,0,2),Zombie.class,z->{z.setAI(false);z.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).setBaseValue(200);z.setHealth(200);});
        ready();player.attack(target);double afterFirst=target.getHealth();ready();player.attack(target);
        boolean vanillaBlocked=target.getHealth()==afterFirst;
        double bossBefore=boss.health();ready();player.attack(boss.carrier());afterFirst=boss.health();ready();player.attack(boss.carrier());
        check(vanillaBlocked&&afterFirst<bossBefore&&boss.health()==afterFirst,"rapid full-strength attacks with high attack speed respect the same hurt window as a vanilla mob");
        target.remove();player.getAttribute(org.bukkit.attribute.Attribute.ATTACK_SPEED).setBaseValue(4);later(10,()->mace());
    }
    double capped(double value){return Math.min(60,Math.min(180,value)) + Math.max(0,Math.min(180,value)-60)*0.25;}
    void mace(){
        ItemStack mace=new ItemStack(Material.MACE);mace.addUnsafeEnchantment(Enchantment.WIND_BURST,1);player.getInventory().setItemInMainHand(mace);actor.detectEquipmentUpdates();ready();
        boss.triggerStun(200,2.0,"test weak point");
        Object perch=seedPerch();
        actor.setOnGround(false);player.setFallDistance(5f);player.setVelocity(new Vector(0,-0.5,0));double before=boss.health();
        player.attack(boss.carrier());
        check(lastNativeType==org.bukkit.damage.DamageType.MACE_SMASH&&lastNativeDamage<60&&Math.abs(before-boss.health()-lastNativeDamage*2)<.01,"small mace smash keeps full damage before the stun multiplier");
        check(player.getVelocity().getY()>0,"Wind Burst rebounds after accepted boss smash: y="+player.getVelocity().getY());
        check(player.getFallDistance()==0,"successful boss smash resets fall distance");
        checkSkyfall(perch);
        later(10,()->{
            ready();actor.setOnGround(false);player.setFallDistance(100);player.setVelocity(new Vector(0,-.5,0));double hp=boss.health();player.attack(boss.carrier());
            check(lastNativeType==org.bukkit.damage.DamageType.MACE_SMASH&&lastNativeDamage>60&&Math.abs(hp-boss.health()-capped(lastNativeDamage)*2)<.01,"large smash compresses only damage above 60 before the stun multiplier; native="+lastNativeDamage+" dealt="+(hp-boss.health()));
            check(player.getVelocity().getY()>0&&player.getFallDistance()==0,"high smash still rebounds and resets fall distance");
            Zombie target=player.getWorld().spawn(player.getLocation().clone().add(0,0,2),Zombie.class,z->{z.setAI(false);z.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).setBaseValue(1024);z.setHealth(1024);});
            ready();actor.setOnGround(false);player.setFallDistance(100);player.setVelocity(new Vector(0,-.5,0));player.attack(target);
            check(1024-target.getHealth()>90,"high Mace smash on ordinary mobs retains vanilla damage");target.remove();
            later(10,()->{
                try{var health=RealmBossController.class.getDeclaredField("health");health.setAccessible(true);health.setDouble(boss,1);for(String f:new String[]{"phaseTwo","phaseThree"}){var flag=RealmBossController.class.getDeclaredField(f);flag.setAccessible(true);flag.setBoolean(boss,true);}}catch(ReflectiveOperationException e){throw new RuntimeException(e);}
                ready();actor.setOnGround(false);player.setFallDistance(5);player.setVelocity(new Vector(0,-.5,0));player.attack(boss.carrier());
                check(boss.health()==0,"lethal mace smash consumes the remaining virtual HP");
                check(player.getVelocity().getY()>0&&player.getFallDistance()==0,"lethal smash still rebounds and resets fall damage");
                later(2,()->{check(boss.isDead(),"lethal smash completes defeat after the native post-hit effects");boss.close();finish(null);});
            });
        });
    }
    /** Perch time and a Realm Brand on the diver, as if they had just jumped off a ledge. */
    Object seedPerch(){
        try{
            var huntField=RealmBossController.class.getDeclaredField("perchHunt");huntField.setAccessible(true);Object hunt=huntField.get(boss);
            var map=hunt.getClass().getDeclaredField("perches");map.setAccessible(true);
            var type=Class.forName("com.example.voidscape.boss.PerchHunt$Perch");var ctor=type.getDeclaredConstructor();ctor.setAccessible(true);Object perch=ctor.newInstance();
            var heat=type.getDeclaredField("heat");heat.setAccessible(true);heat.setInt(perch,40);
            @SuppressWarnings("unchecked") var perches=(Map<UUID,Object>)map.get(hunt);perches.put(player.getUniqueId(),perch);
            var brand=RealmBossController.class.getDeclaredMethod("addBrand",Player.class);brand.setAccessible(true);brand.invoke(boss,player);
            return perch;
        }catch(ReflectiveOperationException e){throw new RuntimeException(e);}
    }
    void checkSkyfall(Object perch){
        try{
            var heat=perch.getClass().getDeclaredField("heat");heat.setAccessible(true);
            var brands=RealmBossController.class.getDeclaredField("brands");brands.setAccessible(true);
            check(heat.getInt(perch)==0&&!((Map<?,?>)brands.get(boss)).containsKey(player.getUniqueId()),"Skyfall: a real Mace smash after perch time ends the hunt and clears the Realm Brand");
        }catch(ReflectiveOperationException e){throw new RuntimeException(e);}
    }
    void finish(Throwable e){if(done)return;done=true;if(e!=null){e.printStackTrace();lines.add("ERROR: "+e);}lines.add(0,(e==null?"PASS":"FAIL")+": realm melee / "+checks+" checks");try{Files.write(Path.of("model-engine-result.txt"),lines);}catch(Exception ignored){}Bukkit.getScheduler().runTaskLater(this,Bukkit::shutdown,5);}
}
