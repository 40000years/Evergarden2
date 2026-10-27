import com.mojang.authlib.GameProfile;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import java.net.InetSocketAddress;
import java.nio.file.*;
import java.util.*;

/** Real Paper deaths and respawns; all actors/world data are disposable fixtures. */
public final class AfterdeathSecurityProbe extends JavaPlugin implements Listener {
    static final class TestConnection extends Connection {
        TestConnection(){super(PacketFlow.SERVERBOUND);channel=new EmbeddedChannel();address=new InetSocketAddress("127.0.0.1",1);}
        @Override public void send(Packet<?> p){}
        @Override public void send(Packet<?> p,ChannelFutureListener l){}
        @Override public void send(Packet<?> p,ChannelFutureListener l,boolean f){}
        @Override public boolean isConnected(){return true;}
    }
    record Case(String attack,boolean full,boolean keep,String policy){}
    final List<Case> cases=new ArrayList<>();
    final List<String> failures=new ArrayList<>();
    final List<Item> spawned=new ArrayList<>();
    final Map<String,Integer> expected=new TreeMap<>();
    ServerPlayer handle,attacker;Player player;World world;NamespacedKey token;
    int passed,index;Case current;boolean hasAfterdeath;final boolean soulbound=Boolean.getBoolean("probe.soulbound");
    @Override public void onEnable(){
        Bukkit.getPluginManager().registerEvents(this,this);
        Bukkit.getScheduler().runTaskLater(this,()->guard(()->{
            hasAfterdeath=Bukkit.getPluginManager().isPluginEnabled("AfterDeath");
            token=new NamespacedKey(this,"fixture");world=Bukkit.getWorlds().getFirst();
            world.getChunkAt(0,0).addPluginChunkTicket(this);world.getBlockAt(0,99,0).setType(Material.STONE);
            for(String attack:List.of("environment","melee","arrow"))
                for(boolean full:new boolean[]{false,true})for(boolean keep:new boolean[]{false,true})
                    for(String policy:List.of("native","plugin-keep","plugin-keep-list"))cases.add(new Case(attack,full,keep,policy));
            cases.add(new Case("melee",true,false,"broken-plugin-keep-drops"));
            cases.add(new Case("environment",true,true,"manual-clock"));
            next();
        }),30);
    }
    void guard(Runnable work){try{work.run();}catch(Throwable t){getLogger().log(java.util.logging.Level.SEVERE,"Fatal probe error",t);failures.add(current+": "+t);finish();}}
    void check(boolean ok,String label){if(!ok){String f=current+": "+label;failures.add(f);getLogger().warning("FAIL "+f);}else{passed++;getLogger().info("PASS "+current+": "+label);}}
    @EventHandler(priority=EventPriority.HIGHEST) public void deathFixture(PlayerDeathEvent e){
        if(e.getEntity()!=player)return;
        if(current.policy().equals("plugin-keep")){e.setKeepInventory(true);e.getDrops().clear();}
        if(current.policy().equals("plugin-keep-list")&&!e.getKeepInventory()){
            e.getItemsToKeep().addAll(e.getDrops());e.getDrops().clear();
        }
        // Positive control: deliberately violate the documented Paper contract.
        if(current.policy().equals("broken-plugin-keep-drops"))e.setKeepInventory(true);
    }
    @EventHandler(priority=EventPriority.MONITOR) public void spawn(ItemSpawnEvent e){spawned.add(e.getEntity());}
    ItemStack marked(Material material,int amount,String id){
        ItemStack i=new ItemStack(material,amount);var m=i.getItemMeta();m.setUnbreakable(true);m.getPersistentDataContainer().set(token,PersistentDataType.STRING,id);if(soulbound&&id.equals("helmet"))m.getPersistentDataContainer().set(new NamespacedKey("evergarden","ue_soulbound"),PersistentDataType.BYTE,(byte)1);i.setItemMeta(m);expected.put(id,amount);return i;
    }
    String id(ItemStack item){return item!=null&&item.hasItemMeta()?item.getItemMeta().getPersistentDataContainer().get(token,PersistentDataType.STRING):null;}
    Map<String,Integer> counts(boolean includeDrops){
        Map<String,Integer> found=new TreeMap<>();
        for(ItemStack item:player.getInventory().getContents())add(found,item);
        add(found,player.getItemOnCursor());
        if(includeDrops)for(Item entity:spawned)if(entity.isValid())add(found,entity.getItemStack());
        return found;
    }
    void add(Map<String,Integer> counts,ItemStack item){String id=id(item);if(id!=null)counts.merge(id,item.getAmount(),Integer::sum);}
    int clocks(){
        int count=0;NamespacedKey clockKey=new NamespacedKey("afterdeath","is_death_clock");
        for(ItemStack i:player.getInventory().getContents())if(i!=null&&i.hasItemMeta()&&i.getItemMeta().getPersistentDataContainer().has(clockKey,PersistentDataType.BYTE))count+=i.getAmount();
        return count;
    }
    ServerPlayer actor(String name,double x){
        var server=MinecraftServer.getServer();var level=((org.bukkit.craftbukkit.CraftWorld)world).getHandle();
        var profile=new GameProfile(UUID.randomUUID(),name+index);
        var h=new ServerPlayer(server,level,profile,ClientInformation.createDefault());
        h.connection=new ServerGamePacketListenerImpl(server,new TestConnection(),h,CommonListenerCookie.createInitial(profile,false)){@Override public boolean hasClientLoaded(){return true;}};
        h.setPos(x,100,.5);server.getPlayerList().getPlayers().add(h);server.getPlayerList().getPlayersByUUID().put(profile.id(),h);level.addNewPlayer(h);
        var p=h.getBukkitEntity();p.setGravity(false);p.setOp(false);p.setGameMode(GameMode.SURVIVAL);return h;
    }
    void next(){
        if(index>=cases.size()){finish();return;}current=cases.get(index++);getLogger().info("CASE "+current);
        spawned.clear();world.getEntitiesByClass(Item.class).forEach(Entity::remove);expected.clear();
        world.setGameRule(GameRule.KEEP_INVENTORY,current.keep());handle=actor("DeathAudit",.5);attacker=actor("Attacker",10.5);player=handle.getBukkitEntity();
        if(current.full())for(int slot=0;slot<36;slot++)player.getInventory().setItem(slot,marked(Material.STONE,64,"slot-"+slot));
        else player.getInventory().setItem(0,marked(Material.DIAMOND,32,"slot-0"));
        player.getInventory().setHelmet(marked(Material.DIAMOND_HELMET,1,"helmet"));
        player.getInventory().setItemInOffHand(marked(Material.SHIELD,1,"offhand"));
        if(current.policy().equals("manual-clock")){
            if(hasAfterdeath){
                var after=Bukkit.getPluginManager().getPlugin("AfterDeath");
                try{var listenerClass=Class.forName("com.example.geyserplugin.DeathClockListener",true,after.getClass().getClassLoader());var listener=listenerClass.getConstructors()[0].newInstance(after);Object result=listenerClass.getMethod("giveDeathClock",Player.class).invoke(listener,player);check(Boolean.FALSE.equals(result),"full inventory rejects manual clock");}
                catch(ReflectiveOperationException e){throw new RuntimeException(e);}
            }
            check(counts(true).equals(expected),"manual clock preserves every existing stack");cleanup();next();return;
        }
        var level=((org.bukkit.craftbukkit.CraftWorld)world).getHandle();
        var sources=handle.damageSources();
        var source=switch(current.attack()){
            case "melee"->sources.playerAttack(attacker);
            case "arrow"->sources.arrow(new net.minecraft.world.entity.projectile.arrow.Arrow(net.minecraft.world.entity.EntityTypes.ARROW,level),attacker);
            default->sources.generic();
        };
        handle.hurtServer(level,source,Float.MAX_VALUE);check(player.isDead(),"native death occurred");
        boolean broken=current.policy().equals("broken-plugin-keep-drops");
        check(broken?!counts(true).equals(expected):counts(true).equals(expected),broken?"positive control detects duplicated kept drops":"death preserves exact total across inventory and ground");
        Bukkit.getScheduler().runTaskLater(this,()->guard(()->{
            handle=MinecraftServer.getServer().getPlayerList().respawn(handle,false,net.minecraft.world.entity.Entity.RemovalReason.KILLED,PlayerRespawnEvent.RespawnReason.DEATH);player=handle.getBukkitEntity();player.setGravity(false);
            Bukkit.getScheduler().runTaskLater(this,()->guard(()->{
                boolean retained=current.keep()||!current.policy().equals("native");
                check(broken?!counts(true).equals(expected):counts(true).equals(expected),broken?"positive control still detects duplication after respawn":"respawn preserves exact total across inventory and ground");
                Map<String,Integer> expectedInventory=retained?expected:soulbound?Map.of("helmet",1):Map.of();
                check(counts(false).equals(expectedInventory),"inventory follows retention policy");
                boolean clockExpected=hasAfterdeath&&current.attack().equals("environment")&&!retained;
                check(clocks()==(clockExpected?1:0),"clock count matches death policy: "+clocks());
                getLogger().info("OUTCOME inventory="+counts(false)+" total="+counts(true)+" clocks="+clocks());cleanup();next();
            }),3);
        }),3);
    }
    void removeActor(ServerPlayer h){if(h==null)return;var list=MinecraftServer.getServer().getPlayerList();list.getPlayers().remove(h);list.getPlayersByUUID().remove(h.getUUID());h.discard();}
    void cleanup(){removeActor(handle);removeActor(attacker);handle=null;attacker=null;}
    void finish(){try{Files.writeString(Path.of("afterdeath-security-result.txt"),(failures.isEmpty()?"PASS":"FAIL")+" "+passed+" assertions, "+failures.size()+" failures; "+cases.size()+" cases; AfterDeath="+hasAfterdeath+"; Soulbound="+soulbound+"\n"+String.join("\n",failures));}catch(Exception e){getLogger().warning(e.toString());}cleanup();Bukkit.shutdown();}
}
