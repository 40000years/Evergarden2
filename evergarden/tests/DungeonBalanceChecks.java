import com.example.voidscape.VoidscapePlugin;
import com.example.voidscape.dungeon.DungeonBalance;
import com.example.voidscape.dungeon.DungeonBalance.Species;
import com.example.voidscape.world.DungeonLayout;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;

/** Real spawned/equipped mobs and native player damage on disposable Paper. */
public final class DungeonBalanceChecks extends JavaPlugin implements Listener {
    VoidscapePlugin garden; World world; Player player;
    net.minecraft.server.level.ServerPlayer actor;
    Constructor<?> constructor; Method spawn, remove, skill;
    final List<Runnable> steps = new ArrayList<>();
    int checks, cursor, impacts; boolean cancel;
    Object encounter; Mob boss;
    void check(boolean ok, String label) { if (!ok) throw new AssertionError(label); checks++; }
    void near(double actual, double expected, String label) { check(Math.abs(actual-expected)<.01,label+": "+actual+" expected "+expected); }
    @Override public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this,this);
        Bukkit.getScheduler().runTaskLater(this,()->{try { setup(); next(); } catch(Throwable t) { finish(t); }},30);
    }
    @EventHandler(priority=EventPriority.LOWEST) public void count(EntityDamageEvent e) {
        if(e.getEntity()==player) { impacts++; if(cancel)e.setCancelled(true); }
    }
    Object encounter(int tier, DungeonLayout.Kind kind) throws Exception {
        return constructor.newInstance(new DungeonLayout.Site(kind,0,0,1,tier),5);
    }
    Mob spawn(Object enc, Species species, int players) throws Exception {
        Mob m=(Mob)spawn.invoke(garden.dungeons(),enc,species,new Location(world,.5,150,2.5),players);
        check(m!=null,"production spawn "+species); m.setAI(false); return m;
    }
    void setup() throws Exception {
        garden=(VoidscapePlugin)Bukkit.getPluginManager().getPlugin("Evergarden");
        check(garden!=null&&garden.isEnabled(),"plugin boots"); world=garden.world();
        world.setDifficulty(Difficulty.HARD); world.setTime(18000);
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)world.getChunkAt(x,z).addPluginChunkTicket(this);
        for(int x=-6;x<=6;x++)for(int z=-6;z<=6;z++) {
            world.getBlockAt(x,149,z).setType(Material.STONE);
            for(int y=150;y<156;y++)world.getBlockAt(x,y,z).setType(Material.AIR);
        }
        Class<?> enc=Class.forName("com.example.voidscape.dungeon.DungeonManager$Encounter");
        constructor=enc.getDeclaredConstructor(DungeonLayout.Site.class,int.class); constructor.setAccessible(true);
        spawn=garden.dungeons().getClass().getDeclaredMethod("spawn",enc,Species.class,Location.class,int.class);spawn.setAccessible(true);
        remove=garden.dungeons().getClass().getDeclaredMethod("remove",enc);remove.setAccessible(true);
        skill=garden.dungeons().getClass().getDeclaredMethod("skillHit",Player.class,double.class,Mob.class);skill.setAccessible(true);
        var server=net.minecraft.server.MinecraftServer.getServer();
        var level=((org.bukkit.craftbukkit.CraftWorld)world).getHandle();
        var profile=new com.mojang.authlib.GameProfile(UUID.randomUUID(),"BalanceTest");
        actor=new net.minecraft.server.level.ServerPlayer(server,level,profile,net.minecraft.server.level.ClientInformation.createDefault());
        var connection=new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        connection.channel=new io.netty.channel.embedded.EmbeddedChannel();connection.address=new java.net.InetSocketAddress("127.0.0.1",1);
        actor.connection=new net.minecraft.server.network.ServerGamePacketListenerImpl(server,connection,actor,
                net.minecraft.server.network.CommonListenerCookie.createInitial(profile,false)) {
            @Override public boolean hasClientLoaded(){return true;}
        };
        actor.setPos(.5,150,.5);server.getPlayerList().getPlayers().add(actor);
        server.getPlayerList().getPlayersByUUID().put(profile.id(),actor);level.addNewPlayer(actor);
        player=actor.getBukkitEntity();player.setGameMode(GameMode.SURVIVAL);
        player.getAttribute(Attribute.MAX_HEALTH).setBaseValue(200);player.setHealth(200);
        for(var kind:DungeonLayout.Kind.values())for(int tier=1;tier<=5;tier++)for(int team:new int[]{1,3,20}) {
            final int t=tier,n=team;
            steps.add(()->{try { encounter=encounter(t,kind);boss=spawn(encounter,Species.BOSS,n); }catch(Exception e){throw new RuntimeException(e);} });
            steps.add(()->{try {
                near(boss.getAttribute(Attribute.MAX_HEALTH).getValue(),DungeonBalance.stats(Species.BOSS,t,n).health(),"bounded party HP");
                near(boss.getAttribute(Attribute.ATTACK_DAMAGE).getBaseValue(),DungeonBalance.stats(Species.BOSS,t,1).damage(),"party does not inflate damage");
                near(boss.getAttribute(Attribute.ARMOR).getBaseValue(),DungeonBalance.armor(t),"authored armor after delayed cleanse");
                boss.setNoDamageTicks(0);double hp=boss.getHealth();boss.damage(20,player);double lost=hp-boss.getHealth();
                check(lost>0,"equipped boss accepts real player damage");
                double effective=hp*20/lost;
                check(effective<7000,"equipped boss EHP below solo world-boss HP: "+effective);
                if(n==1)getLogger().info(kind+" T"+t+" HP="+hp+" effective HP vs raw20="+effective);
                double remaining=boss.getHealth();
                boss.getAttribute(Attribute.ATTACK_DAMAGE).setBaseValue(500);
                boss.getAttribute(Attribute.ARMOR).setBaseValue(30);
                garden.dungeons().cleanseMobIfTagged(boss);
                near(boss.getAttribute(Attribute.ATTACK_DAMAGE).getBaseValue(),DungeonBalance.stats(Species.BOSS,t,n).damage(),"damage-only external inflation repaired");
                near(boss.getAttribute(Attribute.ARMOR).getBaseValue(),DungeonBalance.armor(t),"armor-only inflation repaired");
                near(boss.getHealth(),remaining,"cleanse never heals damage");
                remove.invoke(garden.dungeons(),encounter);
            }catch(Exception e){throw new RuntimeException(e);} });
        }
        steps.add(()->{try {
            for(int t=1;t<=5;t++)for(Species species:Species.values())if(species!=Species.BOSS) {
                Object enc2=encounter(t,DungeonLayout.Kind.SANCTUM_TIME);Mob m=spawn(enc2,species,20);
                check(m.getHealth()<=200,"wave mobs remain below 200 HP even in oversized teams");
                check(m.getAttribute(Attribute.ATTACK_DAMAGE).getBaseValue()<=16,"wave damage bounded");
                remove.invoke(garden.dungeons(),enc2);
            }
            encounter=encounter(5,DungeonLayout.Kind.SANCTUM_DARK);boss=spawn(encounter,Species.BOSS,20);
        }catch(Exception e){throw new RuntimeException(e);} });
        steps.add(()->{try {
            reset();int hits=curse();player.damage(100,boss);
            near(200-player.getHealth(),24,"one capped native contact hit, no extra damage");
            check(impacts==1,"contact creates one damage event");check(curse()==hits+1,"contact records one curse hit");
            reset();hits=curse();skill.invoke(garden.dungeons(),player,29.0,boss);
            near(200-player.getHealth(),29,"scripted skill retains its own budget");
            check(impacts==1,"skill creates one damage event");check(curse()==hits,"skill cannot stack contact curse");
            check(player.getActivePotionEffects().isEmpty(),"skill cannot apply contact debuffs");
            reset();hits=curse();cancel=true;player.damage(100,boss);cancel=false;
            near(player.getHealth(),200,"cancelled contact deals zero damage");check(curse()==hits,"cancelled contact no curse");
            check(player.getActivePotionEffects().isEmpty(),"cancelled contact no debuffs");
            reset();cancel=true;skill.invoke(garden.dungeons(),player,29.0,boss);cancel=false;
            near(player.getHealth(),200,"cancelled skill deals zero damage");
            reset();hits=curse();player.damage(10,boss);check(curse()==hits+1,"scripted impact marker cleared after cancellation");
            remove.invoke(garden.dungeons(),encounter);
        }catch(Exception e){throw new RuntimeException(e);} });
    }
    int curse(){return player.getPersistentDataContainer().getOrDefault(garden.key("true_death_hits"),PersistentDataType.INTEGER,0);}
    void reset(){for(var effect:player.getActivePotionEffects())player.removePotionEffect(effect.getType());player.setHealth(200);player.setNoDamageTicks(0);impacts=0;}
    void next(){if(cursor==steps.size()){finish(null);return;}try {steps.get(cursor++).run();Bukkit.getScheduler().runTaskLater(this,this::next,2);}catch(Throwable t){finish(t);}}
    void finish(Throwable error){
        if(error!=null)getLogger().log(java.util.logging.Level.SEVERE,"BALANCE CHECK FAILED",error);
        String report=(error==null?"PASS ":"FAIL ")+checks+" checks; equipped native mobs, party caps, real incoming/outgoing damage, skill isolation, cancellation, external inflation"+(error==null?"":": "+error);
        try {Files.writeString(Path.of("dungeon-balance-result.txt"),report);}catch(Exception e){throw new RuntimeException(e);}
        if(actor!=null){actor.discard();var list=net.minecraft.server.MinecraftServer.getServer().getPlayerList();list.getPlayers().remove(actor);list.getPlayersByUUID().remove(actor.getUUID());}
        getLogger().info(report);Bukkit.getScheduler().runTaskLater(this,Bukkit::shutdown,1);
    }
}
