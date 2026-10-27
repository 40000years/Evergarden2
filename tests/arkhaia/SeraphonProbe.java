import com.example.voidscape.VoidscapePlugin;
import com.example.voidscape.boss.*;
import com.example.voidscape.world.BossStructureLayout;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.server.network.*;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.damage.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import java.net.InetSocketAddress;
import java.nio.file.*;
import java.util.*;

/** Full production controller on isolated Paper, optionally with official ModelEngine and Geyser. */
@SuppressWarnings("unchecked")
public class SeraphonProbe extends ArkhaiaProbe {
    Object manager,run;Slime receiver;Location center;Class<?> runType;boolean cancelImpact;int impacts;
    Object call(Object o,String n,Class<?>[] t,Object... a)throws Exception{var m=o.getClass().getDeclaredMethod(n,t);m.setAccessible(true);return m.invoke(o,a);}
    long clock()throws Exception{return (long)get(manager,"tick");}
    double health()throws Exception{return (double)get(run,"health");}
    void at(double x,double y,double z){player.teleport(center.clone().add(x,y,z));player.setVelocity(new Vector());player.setNoDamageTicks(0);player.setHealth(200);}
    void face(Location to){Location at=player.getLocation();at.setDirection(to.toVector().subtract(player.getEyeLocation().toVector()));player.teleport(at);}
    void quiet()throws Exception{put(run,"skill",null);put(run,"introUntil",0L);put(run,"transitionUntil",0L);put(run,"stunUntil",0L);put(run,"next",Long.MAX_VALUE);((Map<?,?>)get(run,"hits")).clear();}
    void ticks(int n){try{for(int i=0;i<n;i++){player.setNoDamageTicks(0);call(manager,"tick",new Class<?>[]{});}}catch(Exception e){throw new RuntimeException(e);}}
    void begin(SeraphonRules.Skill skill)throws Exception{quiet();call(manager,"begin",new Class<?>[]{runType,SeraphonRules.Skill.class},run,skill);}
    @Override public void onEnable(){Bukkit.getPluginManager().registerEvents(this,this);Bukkit.getScheduler().runTaskLater(this,()->{try{run();}catch(Throwable e){finish(e);}},Files.exists(Path.of("seraphon-native.flag"))?220:20);}
    @EventHandler(priority=EventPriority.HIGH) public void impact(EntityDamageEvent e){if(e.getEntity()==player){impacts++;if(cancelImpact)e.setCancelled(true);}}
    @Override void run()throws Exception{
        garden=(VoidscapePlugin)Bukkit.getPluginManager().getPlugin("Evergarden");check(garden!=null&&garden.isEnabled(),"Seraphon release boots");
        manager=garden.seraphonBoss();check(manager!=null,"production plugin owns and schedules Seraphon service");
        world=garden.world();world.setDifficulty(Difficulty.NORMAL);site=garden.bossStructures().nearest(BossStructureLayout.Kind.SERAPHON,0,0,32,true);
        if(site==null)site=new BossStructureLayout.Site(BossStructureLayout.Kind.SERAPHON,16384,16384);
        check(site.kind()==BossStructureLayout.Kind.SERAPHON,"fixture uses the Supreme Sanctuary structure and combat-floor contract");center=new Location(world,site.x()+.5,site.kind().floorY+1,site.z()+.5);
        for(int x=-64;x<=64;x+=16)for(int z=-64;z<=64;z+=16)world.getChunkAt(center.getBlockX()+x>>4,center.getBlockZ()+z>>4).addPluginChunkTicket(this);
        for(int x=-6;x<=6;x++)for(int z=-6;z<=6;z++){
            world.getBlockAt(center.getBlockX()+x,center.getBlockY()-1,center.getBlockZ()+z).setType(Material.STONE,false);
            for(int y=0;y<16;y++)world.getBlockAt(center.getBlockX()+x,center.getBlockY()+y,center.getBlockZ()+z).setType(Material.AIR,false);
        }
        runType=Arrays.stream(manager.getClass().getDeclaredClasses()).filter(c->c.getSimpleName().equals("Run")).findFirst().orElseThrow();
        var constructor=runType.getDeclaredConstructor(VoidscapePlugin.class,BossStructureLayout.Site.class);constructor.setAccessible(true);
        Map<String,Object> runs=(Map<String,Object>)get(manager,"runs");String id=world.getUID()+"_"+site.x()+"_"+site.z();
        if(Files.exists(Path.of("seraphon-native.flag"))){
            // Pin one saved structure candidate in this isolated world's layout cache;
            // exercise the real loaded-chunk discovery rather than bypassing it.
            int spacing=garden.bossStructures().spacingChunks();long cell=com.example.voidscape.world.SkyPlacementHistory.key(Math.floorDiv(center.getBlockX()>>4,spacing),Math.floorDiv(center.getBlockZ()>>4,spacing));
            ((Map<Long,Object>)get(garden.bossStructures(),"cache")).put(cell,List.of(site));
            call(manager,"discover",new Class<?>[]{Chunk.class},center.getChunk());run=runs.get(id);
            check(run!=null&&get(run,"anchor")!=null,"loading an existing Supreme Sanctuary automatically spawns its real resident guardian");
        }else{
            run=constructor.newInstance(garden,site);Object old=runs.put(id,run);if(old!=null)call(manager,"remove",new Class<?>[]{runType},old);
            call(manager,"spawn",new Class<?>[]{runType},run);
        }
        receiver=(Slime)get(run,"anchor");
        var server=MinecraftServer.getServer();var level=((CraftWorld)world).getHandle();var profile=new GameProfile(UUID.randomUUID(),"HeavensChallenger");
        actor=new ServerPlayer(server,level,profile,ClientInformation.createDefault());var connection=new Connection(PacketFlow.SERVERBOUND);connection.channel=new EmbeddedChannel();connection.address=new InetSocketAddress("127.0.0.1",1);
        actor.connection=new ServerGamePacketListenerImpl(server,connection,actor,CommonListenerCookie.createInitial(profile,false)){@Override public boolean hasClientLoaded(){return true;}};
        actor.setPos(center.getX(),center.getY(),center.getZ()+3);server.getPlayerList().getPlayers().add(actor);server.getPlayerList().getPlayersByUUID().put(profile.id(),actor);level.addNewPlayer(actor);
        player=actor.getBukkitEntity();player.setGravity(false);player.setGameMode(GameMode.SURVIVAL);player.setInvulnerable(false);actor.getAbilities().invulnerable=false;
        player.getAttribute(Attribute.MAX_HEALTH).setBaseValue(200);player.setHealth(200);player.getInventory().clear();
        check(health()==7000&&(double)get(run,"maxHealth")==7000,"solo guardian starts with exactly 7000 virtual health");check(managerOwns(receiver),"native body belongs to the structure encounter");
        check(garden.getConfig().getInt("seraphon.balance-version")==2&&garden.getConfig().getDouble("seraphon.health")==7000,"published 10000 default migrates once to 7000 solo health");
        receiver.damage(100);check(health()==7000,"environmental damage cannot activate or chip guardian");
        player.damage(10,receiver);check(player.getHealth()==200,"unscheduled native slime touch is suppressed");
        at(.7,0,2.5);face(center.clone().add(.7,1,0));receiver.damage(20,player);
        check((boolean)get(run,"fighting")&&health()<7000,"real body melee activates the natural-site guardian and counts the first hit");
        check(((List<?>)get(run,"mirrors")).size()==3,"waking places three sun mirrors on the arena ring");
        check(!((Set<?>)get(run,"tickets")).isEmpty(),"active guardian holds arena chunks");double hp=health();receiver.damage(20,player);check(health()==hp,"intro cannot be damaged repeatedly");
        ticks(61);quiet();at(0,0,70);receiver.damage(20,player);check(health()==hp,"outside arena player cannot damage hidden receiver");
        at(0,180,8);check((boolean)call(manager,"inside",new Class<?>[]{runType,Player.class},run,player),"active arena retains aerial Wind Charge players");
        Object effects=get(run,"effects");call(effects,"frame",new Class<?>[]{Location.class},center);
        check(!((List<?>)get(get(effects,"aether"),"viewers")).isEmpty(),"high airborne combatants retain the visible spell-effect audience");
        at(0,0,10);begin(SeraphonRules.Skill.LANCES);ticks(SeraphonRules.Skill.LANCES.windup-1);
        check(((List<?>)get(run,"bolts")).isEmpty(),"lance windup shows gates without dealing invisible early damage");ticks(2);
        check(!((List<?>)get(run,"bolts")).isEmpty(),"lance contact emits actual travelling spell projectiles");
        check(player.getHealth()==200,"lance launch is not immediate target damage");
        for(int i=0;i<40&&player.getHealth()==200;i++)ticks(1);check(player.getHealth()<200,"swept visible lances collide with the committed target");
        call(get(run,"effects"),"clearAttack",new Class<?>[]{});((List<?>)get(run,"bolts")).clear();((List<?>)get(run,"pillars")).clear();
        at(0,0,12);begin(SeraphonRules.Skill.FEATHERS);ticks(SeraphonRules.Skill.FEATHERS.windup+1);
        check(((List<?>)get(run,"bolts")).size()>=13,"six-wing cast releases its feather fan from authored emitter bones");
        ((List<?>)get(run,"bolts")).clear();call(get(run,"effects"),"clearAttack",new Class<?>[]{});quiet();
        at(18,0,0);Location marked=player.getLocation().clone().add(0,.12,0);
        call(manager,"addPillar",new Class<?>[]{runType,Location.class,double.class,double.class,double.class,long.class,String.class},run,marked,3.8,center.getY()+40,40.0,clock()+10,"probe");
        ticks(9);check(player.getHealth()==200,"ground and sky seals warn before column impact");ticks(1);check(player.getHealth()<200,"visible column deals damage on its contact tick");
        double struck=player.getHealth();ticks(7);check(player.getHealth()==struck,"one column cannot repeat damage across its lingering frames");
        ((List<?>)get(run,"pillars")).clear();at(18,0,0);
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)world.getBlockAt(player.getLocation().getBlockX()+x,center.getBlockY()+5,player.getLocation().getBlockZ()+z).setType(Material.STONE,false);
        call(manager,"addPillar",new Class<?>[]{runType,Location.class,double.class,double.class,double.class,long.class,String.class},run,marked,3.8,center.getY()+40,40.0,clock()+1,"roof");ticks(2);
        check(player.getHealth()==200,"solid roof clips both heavenly beam and outgoing damage");
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)world.getBlockAt(player.getLocation().getBlockX()+x,center.getBlockY()+5,player.getLocation().getBlockZ()+z).setType(Material.AIR,false);
        ((List<?>)get(run,"pillars")).clear();quiet();cancelImpact=true;boolean accepted=(boolean)call(manager,"hit",new Class<?>[]{runType,Player.class,double.class},run,player,30.0);
        check(!accepted&&player.getHealth()==200,"other plugins can cancel native Seraphon spell impacts");cancelImpact=false;
        at(10,0,0);put(run,"petalOffset",0);((Map<?,?>)get(run,"beamHits")).clear();
        call(manager,"orbit",new Class<?>[]{runType,int.class,boolean.class},run,0,false);check(player.getHealth()==200,"rotating-beam warning is harmless");
        call(manager,"orbit",new Class<?>[]{runType,int.class,boolean.class},run,0,true);check(player.getHealth()<200,"active orbit beam hits its visible low lane");
        at(10,4,0);((Map<?,?>)get(run,"beamHits")).clear();call(manager,"orbit",new Class<?>[]{runType,int.class,boolean.class},run,0,true);
        check(player.getHealth()==200,"jumping completely above orbit beam avoids contact");
        at(10,0,0);((Map<?,?>)get(run,"beamHits")).clear();
        for(int y=0;y<4;y++)world.getBlockAt(center.getBlockX()+5,center.getBlockY()+y,center.getBlockZ()).setType(Material.STONE,false);
        call(manager,"orbit",new Class<?>[]{runType,int.class,boolean.class},run,0,true);check(player.getHealth()==200,"solid cover stops orbit ray and damage together");
        for(int y=0;y<4;y++)world.getBlockAt(center.getBlockX()+5,center.getBlockY()+y,center.getBlockZ()).setType(Material.AIR,false);
        player.getInventory().setItemInOffHand(new ItemStack(Material.TOTEM_OF_UNDYING));player.setHealth(5);player.setNoDamageTicks(0);
        call(manager,"hit",new Class<?>[]{runType,Player.class,double.class},run,player,80.0);
        check(!player.isDead()&&player.getInventory().getItemInOffHand().getType()==Material.AIR,"heavenly impacts retain native Totem resurrection");
        player.getActivePotionEffects().forEach(e->player.removePotionEffect(e.getType()));player.setAbsorptionAmount(0);at(10,0,0);
        quiet();begin(SeraphonRules.Skill.VERDICT);double beforeInterrupt=health();
        receiver.damage(500,DamageSource.builder(DamageType.INDIRECT_MAGIC).withCausingEntity(player).withDirectEntity(player).build());
        check(health()<beforeInterrupt,"ranged attributed magic damage is not incorrectly gated by melee reach");
        check(get(run,"skill")==SeraphonRules.Skill.VERDICT&&(long)get(run,"stunUntil")<=clock(),"burst damage no longer cancels the judgment");
        mechanics();
        quiet();put(run,"health",4500.0);ticks(1);check((int)get(run,"phase")==2&&(long)get(run,"transitionUntil")>clock(),"65-percent threshold plays a real transition into phase two");
        check((long)get(run,"choirDue")>clock(),"phase two schedules the Ophanim choir");
        quiet();put(run,"health",2000.0);ticks(1);check((int)get(run,"phase")==3&&(long)get(run,"wingsDue")>clock(),"30-percent threshold unlocks final heavenly phase and schedules Heaven's Gate");
        quiet();at(0,0,12);begin(SeraphonRules.Skill.VERDICT);int offset=(int)get(run,"petalOffset");ticks(SeraphonRules.Skill.VERDICT.windup+1);
        check(((List<?>)get(run,"pillars")).size()==27,"final judgment creates 27 individually warned light columns around safe petals");
        double safeAngle=offset*Math.PI/6;at(Math.cos(safeAngle)*30,0,Math.sin(safeAngle)*30);ticks(18);check(player.getHealth()==200,"cyan petal center remains safe through first ultimate impact");
        int unsafe=(offset+1)%12;double danger=unsafe*Math.PI/6;at(Math.cos(danger)*30,0,Math.sin(danger)*30);ticks(28);
        check(player.getHealth()<200,"final phase repeats judgment on the rotated second impact");
        wings();
        quiet();((List<?>)get(run,"pillars")).clear();call(get(run,"effects"),"clearAttack",new Class<?>[]{});at(.7,0,2.5);face(center.clone().add(.7,1,0));
        put(run,"position",center.clone());Object visual=get(run,"visual");call(visual,"frame",new Class<?>[]{Location.class,Player.class,String.class,double.class,boolean.class},center,null,"idle",1.0,false);
        if(Files.exists(Path.of("seraphon-native.flag"))){
            check(garden.arkhaiaModels().ready("seraphon"),"official ModelEngine imports the unchanged six-wing model");
            Object rig=get(visual,"rig");check(rig!=null,"real encounter attaches its official ModelEngine rig");
            for(String clip:List.of("cast_windup","cast_active","wings_active","smite_active","verdict_windup","verdict_active","transition","stun")){
                call(visual,"frame",new Class<?>[]{Location.class,Player.class,String.class,double.class,boolean.class},center,player,clip,1.0,true);
                check(get(rig,"clip").equals(clip),"native rig selects authored combat clip "+clip);
            }
            call(visual,"frame",new Class<?>[]{Location.class,Player.class,String.class,double.class,boolean.class},center,player,"idle",1.0,false);
        }
        quiet();put(run,"health",1000.0);at(70,0,0);ticks(SeraphonRules.REGROUP+1);
        check((boolean)get(run,"fighting")&&Math.abs(health()-7000*.30)<1e-6&&(int)get(run,"phase")==3,"a wiped party restarts the current phase instead of the fight");
        ticks(180*20);check(!(boolean)get(run,"fighting")&&health()==7000,"empty arena resets to full health after the three-minute return grace");
        receiver=(Slime)get(run,"anchor");at(.7,0,2.5);face(center.clone().add(.7,1,0));receiver.damage(20,player);quiet();put(run,"health",1.0);((Map<?,?>)get(run,"hits")).clear();receiver.damage(20,player);
        check((boolean)get(run,"dead")&&get(run,"anchor")==null,"victory removes the model and closes all attack entities");
        check((long)get(run,"readyAt")==world.getGameTime()+24000,"victory persists one full game day of structure cooldown");
        manager.getClass().getMethod("close").invoke(manager);check(((Map<?,?>)get(manager,"runs")).isEmpty(),"plugin shutdown removes every guardian run");finish(null);
    }
    void clearAttacks()throws Exception{call(get(run,"effects"),"clearAttack",new Class<?>[]{});((List<?>)get(run,"bolts")).clear();((List<?>)get(run,"pillars")).clear();((List<?>)get(run,"wheels")).clear();}
    void magicHit(double amount){receiver.damage(amount,DamageSource.builder(DamageType.INDIRECT_MAGIC).withCausingEntity(player).withDirectEntity(player).build());}
    void mechanics()throws Exception{
        var solar=SeraphonRules.Skill.SOLAR;var choir=SeraphonRules.Skill.CHOIR;
        quiet();clearAttacks();put(run,"position",center.clone());at(.7,0,2.5);face(center.clone().add(.7,1,0));put(run,"stunUntil",clock()+100);
        double hp=health();receiver.damage(20,player);check(Math.abs(hp-health()-40)<1e-6,"grounded guardian takes double damage");
        // Solar Lance without cover: tracks, locks, then a 60% max-health beam.
        quiet();clearAttacks();List<Location> mirrors=(List<Location>)get(run,"mirrors");mirrors.clear();put(run,"position",center.clone());
        at(0,0,14);begin(solar);ticks(solar.windup-2);check(player.getHealth()==200,"solar lance warns before it fires");ticks(3);
        check(player.getHealth()<=200-200*.6+1,"uncovered target takes the 60% max-health solar beam");
        // A sun mirror between the target and the orb reflects it and grounds her.
        quiet();clearAttacks();mirrors.clear();Location mirror=center.clone().add(0,SeraphonRules.MIRROR_RADIUS+.3,20);mirrors.add(mirror);put(run,"position",center.clone());
        at(0,0,22.5);begin(solar);ticks(solar.windup+1);
        check(player.getHealth()==200&&(long)get(run,"stunUntil")>clock()&&mirrors.isEmpty(),"standing behind a sun mirror reflects the lance and grounds her");
        ticks(1);check(get(get(run,"visual"),"clip").equals("stun"),"grounded guardian plays the stun clip");
        for(int i=0;i<205;i++){player.setHealth(200);ticks(1);}check(mirrors.size()==1,"a shattered mirror returns after ten seconds");
        // Ophanim choir: shielded, then broken by standing in every wheel's shadow.
        quiet();clearAttacks();put(run,"position",center.clone());at(0,0,10);begin(choir);
        List<?> wheels=(List<?>)get(run,"wheels");check(wheels.size()==2,"solo choir summons two Ophanim wheels");
        ticks(choir.windup);hp=health();magicHit(400);check(Math.abs(hp-health()-100)<1e-6,"choir shield reduces damage to a quarter");
        for(int i=0;i<600&&!wheels.isEmpty();i++){Object w=wheels.get(0);double a=(double)get(w,"angle")+(double)get(w,"turn"),radius=(double)get(w,"radius");
            player.teleport(center.clone().add(Math.cos(a)*radius,0,Math.sin(a)*radius));player.setHealth(200);player.setNoDamageTicks(0);ticks(1);}
        check(wheels.isEmpty()&&get(run,"skill")==null&&(long)get(run,"stunUntil")>clock(),"breaking every wheel grounds her for the long window");
        // Ignoring the wheels ends in the radiant burst.
        quiet();clearAttacks();put(run,"position",center.clone());at(0,0,10);begin(choir);
        for(int i=0;i<choir.windup+choir.active-1;i++){player.teleport(center.clone().add(0,0,40));player.setHealth(200);player.setNoDamageTicks(0);ticks(1);}
        ticks(2);check(player.getHealth()<=200-200*.4+1&&((List<?>)get(run,"wheels")).isEmpty()&&(long)get(run,"stunUntil")<=clock(),"timeout: radiant burst hits everyone outside the shadows and she stays airborne");
        quiet();clearAttacks();at(0,0,10);
    }
    void wings()throws Exception{
        var gate=SeraphonRules.Skill.WINGS;quiet();clearAttacks();put(run,"position",center.clone().add(9,0,4));at(0,0,8);begin(gate);
        for(Location end:List.of((Location)get(run,"wingsFrom"),(Location)get(run,"wingsTo")))
            check(Math.hypot(end.getX()-center.getX(),end.getZ()-center.getZ())<=SeraphonRules.RADIUS-20,"Heaven's Gate path stays well inside the sanctuary");
        double fastest=0,farthest=0;Location last=null;
        for(int i=0;i<gate.windup+gate.active+2;i++){Location body=((Location)get(run,"position")).clone();
            if(i>gate.windup&&last!=null)fastest=Math.max(fastest,Math.hypot(body.getX()-last.getX(),body.getZ()-last.getZ()));
            farthest=Math.max(farthest,Math.hypot(body.getX()-center.getX(),body.getZ()-center.getZ()));last=body;
            Location under=body.clone();under.setY(center.getY());
            player.teleport(under.add(1,0,1));player.setHealth(200);player.setNoDamageTicks(0);ticks(1);}
        check(fastest<=.215,"she drifts slower than walking speed while the gate counts down ("+fastest+" blocks/tick)");
        check(farthest<=SeraphonRules.RADIUS-20,"she never leaves the sanctuary during Heaven's Gate ("+farthest+")");
        check(!player.isDead()&&(long)get(run,"stunUntil")>clock(),"following under her wings survives Heaven's Gate and grounds her");
        quiet();clearAttacks();
    }
    boolean managerOwns(Entity e)throws Exception{return (boolean)manager.getClass().getMethod("owns",Entity.class).invoke(manager,e);}
}
