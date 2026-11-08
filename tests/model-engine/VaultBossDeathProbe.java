import com.example.voidscape.VoidscapePlugin;
import com.example.voidscape.dungeon.DungeonManager;
import com.example.voidscape.world.DungeonLayout;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.server.network.*;
import org.bukkit.*;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffectType;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.nio.file.*;
import java.util.*;

/** Actual Paper deaths through each Vault boss and the delayed True Death curse. */
public final class VaultBossDeathProbe extends JavaPlugin implements Listener {
    private final List<String> results=new ArrayList<>();
    private final Map<UUID,String> deaths=new HashMap<>();
    private int checks;
    private VoidscapePlugin garden;
    private World world;
    private DungeonManager dungeons;
    private Player pulse,execution,totem;
    private final List<Player> actors=new ArrayList<>();

    private void check(boolean ok,String text){if(!ok)throw new AssertionError(text);checks++;results.add("PASS: "+text);}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void death(PlayerDeathEvent e){deaths.put(e.getEntity().getUniqueId(),e.deathMessage()==null?"":PlainTextComponentSerializer.plainText().serialize(e.deathMessage()));}
    @Override public void onEnable(){Bukkit.getPluginManager().registerEvents(this,this);Bukkit.getScheduler().runTaskLater(this,()->{try{run();}catch(Throwable e){finish(e);}},100);}
    private Player actor(String name,double x){
        var server=MinecraftServer.getServer();var level=((CraftWorld)world).getHandle();var profile=new GameProfile(UUID.randomUUID(),name);
        var p=new ServerPlayer(server,level,profile,ClientInformation.createDefault());var conn=new Connection(PacketFlow.SERVERBOUND);
        conn.channel=new EmbeddedChannel();conn.address=new InetSocketAddress("127.0.0.1",1);
        p.connection=new ServerGamePacketListenerImpl(server,conn,p,CommonListenerCookie.createInitial(profile,false)){
            @Override public boolean hasClientLoaded(){return true;}
        };
        p.setPos(x,120,252);server.getPlayerList().getPlayers().add(p);server.getPlayerList().getPlayersByUUID().put(profile.id(),p);level.addNewPlayer(p);
        Player out=p.getBukkitEntity();out.setGameMode(GameMode.SURVIVAL);out.setGravity(false);out.setInvulnerable(false);actors.add(out);return out;
    }
    private void run() throws Exception {
        garden=(VoidscapePlugin)Bukkit.getPluginManager().getPlugin("Evergarden");dungeons=garden.dungeons();world=garden.world();
        world.getChunkAt(16,16).load();world.getChunkAt(16,16).addPluginChunkTicket(this);
        String[] names={"Shadow Overlord","Astral Archon","Chronos Vanguard"};
        DungeonLayout.Kind[] kinds={DungeonLayout.Kind.SANCTUM_DARK,DungeonLayout.Kind.SANCTUM_ASTRAL,DungeonLayout.Kind.SANCTUM_TIME};
        Mob dark=null;
        for(int i=0;i<kinds.length;i++){
            Mob boss=(Mob)dungeons.spawnTestBoss(new Location(world,257+i*2,120,257),kinds[i]);
            check(boss!=null,"Vault boss spawns: "+names[i]);boss.setAI(false);if(i==0)dark=boss;
            Player player=actor("VaultDirect"+i,257+i*2);player.setHealth(1);player.setNoDamageTicks(0);player.damage(50,boss);
            check(player.isDead()&&deaths.getOrDefault(player.getUniqueId(),"").contains(names[i]),"direct death names "+names[i]);
        }
        Player wither=actor("VaultWither",257);wither.setNoDamageTicks(0);wither.damage(1,dark);
        check(wither.hasPotionEffect(PotionEffectType.WITHER),"dark boss applies its Wither");
        wither.setHealth(1);wither.setNoDamageTicks(0);wither.damage(10,DamageSource.builder(DamageType.WITHER).build());
        check(wither.isDead()&&deaths.getOrDefault(wither.getUniqueId(),"").contains("Shadow Overlord"),"boss Wither death names Shadow Overlord");
        Method curse=DungeonManager.class.getDeclaredMethod("recordTrueDeathHit",Player.class,String.class);curse.setAccessible(true);
        garden.getConfig().set("combat.true-death.hits-per-level",1);
        garden.getConfig().set("combat.true-death.max-level",2);
        pulse=actor("VaultPulse",257);pulse.setHealth(1);curse.invoke(dungeons,pulse,"Shadow Overlord");
        totem=actor("VaultTotem",257);totem.setHealth(1);totem.getInventory().setItemInOffHand(new ItemStack(Material.TOTEM_OF_UNDYING));curse.invoke(dungeons,totem,"Astral Archon");
        garden.getConfig().set("combat.true-death.max-level",1);
        execution=actor("VaultExecute",257);execution.getInventory().setItemInOffHand(new ItemStack(Material.TOTEM_OF_UNDYING));curse.invoke(dungeons,execution,"Chronos Vanguard");
        Bukkit.getScheduler().runTaskLater(this,()->{try{
            check(pulse.isDead()&&deaths.getOrDefault(pulse.getUniqueId(),"").contains("Shadow Overlord"),"delayed curse pulse names its boss");
            check(!totem.isDead()&&!deaths.containsKey(totem.getUniqueId()),"Totem still saves a normal curse pulse");
            check(execution.isDead()&&deaths.getOrDefault(execution.getUniqueId(),"").contains("Chronos Vanguard"),"final True Death names its boss and still bypasses Totem");
            finish(null);
        }catch(Throwable e){finish(e);}},5);
    }
    private void finish(Throwable error){
        if(error!=null){error.printStackTrace();results.add("ERROR: "+error);}
        results.add(0,(error==null?"PASS":"FAIL")+": Vault boss deaths / "+checks+" checks");
        try{Files.write(Path.of("model-engine-result.txt"),results);}catch(Exception ignored){}
        Bukkit.getScheduler().runTaskLater(this,Bukkit::shutdown,5);
    }
}
