import com.example.advancemagic.AdvanceMagicPlugin;
import com.example.advancemagic.spell.Spell;
import com.example.voidscape.VoidscapePlugin;
import com.example.voidscape.enchant.UniqueEnchant;
import com.example.voidscape.gui.ChestUpgradeGui;
import com.mojang.authlib.GameProfile;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.embedded.EmbeddedChannel;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.network.Connection;
import net.minecraft.network.HashedStack;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.inventory.ContainerInput;
import org.bukkit.*;
import org.bukkit.block.Chest;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import java.net.InetSocketAddress;
import java.nio.file.*;
import java.util.*;

/** Native death, inventory packets and respawn on a disposable Paper server. */
public final class SoulboundSecurityProbe extends JavaPlugin implements Listener {
    static final class TestConnection extends Connection {
        TestConnection(){super(PacketFlow.SERVERBOUND);channel=new EmbeddedChannel();address=new InetSocketAddress("127.0.0.1",1);}
        @Override public void send(Packet<?> packet){}
        @Override public void send(Packet<?> packet,ChannelFutureListener listener){}
        @Override public void send(Packet<?> packet,ChannelFutureListener listener,boolean flush){}
        @Override public boolean isConnected(){return true;}
    }
    record Case(String label,boolean keep,Runnable arrange){}
    final List<Case> cases=new ArrayList<>();
    final List<String> failures=new ArrayList<>();
    final List<Item> spawned=new ArrayList<>();
    AdvanceMagicPlugin magic;VoidscapePlugin garden;ServerPlayer handle;Player player;World world;Inventory chest;
    NamespacedKey token;int passed,index;String current="";boolean cancelDeath;boolean preRetain;
    @Override public void onEnable(){Bukkit.getPluginManager().registerEvents(this,this);Bukkit.getScheduler().runTaskLater(this,()->guard(()->{begin();next();}),30);}
    void guard(Runnable work){try{work.run();}catch(Throwable t){getLogger().log(java.util.logging.Level.SEVERE,"Fatal probe error",t);failures.add(current+": "+t);finish();}}
    void check(boolean value,String label){if(!value)throw new AssertionError(current+": "+label);passed++;getLogger().info("PASS "+current+": "+label);}
    void verify(Runnable work){try{work.run();}catch(Throwable t){failures.add(current+": "+t);getLogger().warning(failures.getLast());}}
    @EventHandler(priority=EventPriority.LOWEST) public void deathFixture(PlayerDeathEvent e){
        if(e.getEntity()!=player)return;
        if(cancelDeath){e.setCancelled(true);e.setReviveHealth(10);}
        if(preRetain){var iterator=e.getDrops().iterator();while(iterator.hasNext()){ItemStack item=iterator.next();if(isBound(item)){e.getItemsToKeep().add(item);iterator.remove();break;}}}
    }
    @EventHandler(priority=EventPriority.MONITOR) public void spawn(ItemSpawnEvent e){spawned.add(e.getEntity());}
    boolean isBound(ItemStack item){return item!=null&&com.example.voidscape.enchant.EnchantApplyListener.hasUnique(item,UniqueEnchant.SOULBOUND);}
    ItemStack marked(ItemStack item,String id){var meta=item.getItemMeta();meta.getPersistentDataContainer().set(token,PersistentDataType.STRING,id);item.setItemMeta(meta);return item;}
    ItemStack bound(ItemStack item,String id){ItemStack result=garden.relics().evaluateScrollCraft(garden.relics().createScrollUnique(UniqueEnchant.SOULBOUND),marked(item,id));if(result==null)throw new AssertionError("Cannot create Soulbound fixture");return result;}
    ItemStack wand(String id){return bound(magic.wands().create(Spell.FROST_NOVA),id);}
    int amount(ItemStack item,String id){return item!=null&&item.hasItemMeta()&&id.equals(item.getItemMeta().getPersistentDataContainer().get(token,PersistentDataType.STRING))?item.getAmount():0;}
    int inventory(String id){return Arrays.stream(player.getInventory().getContents()).mapToInt(i->amount(i,id)).sum();}
    int drops(String id){return spawned.stream().filter(Entity::isValid).map(Item::getItemStack).mapToInt(i->amount(i,id)).sum();}
    int owned(String id){return inventory(id)+amount(player.getItemOnCursor(),id);}
    void packet(int slot,int button,ContainerInput input){var menu=handle.containerMenu;handle.connection.handleContainerClick(new ServerboundContainerClickPacket(menu.containerId,menu.getStateId(),(short)slot,(byte)button,input,new Int2ObjectOpenHashMap<HashedStack>(),HashedStack.EMPTY));}
    void actor(){
        var server=MinecraftServer.getServer();var level=((org.bukkit.craftbukkit.CraftWorld)world).getHandle();
        var profile=new GameProfile(UUID.randomUUID(),"SoulAudit"+index);
        handle=new ServerPlayer(server,level,profile,ClientInformation.createDefault());
        handle.connection=new ServerGamePacketListenerImpl(server,new TestConnection(),handle,CommonListenerCookie.createInitial(profile,false)){@Override public boolean hasClientLoaded(){return true;}};
        handle.setPos(.5,100,.5);server.getPlayerList().getPlayers().add(handle);server.getPlayerList().getPlayersByUUID().put(profile.id(),handle);level.addNewPlayer(handle);
        player=handle.getBukkitEntity();player.setGravity(false);player.setOp(false);player.setGameMode(GameMode.SURVIVAL);
    }
    void begin(){
        magic=(AdvanceMagicPlugin)Bukkit.getPluginManager().getPlugin("advance-magic");garden=(VoidscapePlugin)Bukkit.getPluginManager().getPlugin("Evergarden");
        token=new NamespacedKey(this,"probe_item");world=Bukkit.getWorlds().getFirst();world.getChunkAt(0,0).addPluginChunkTicket(this);
        world.getBlockAt(0,99,0).setType(Material.STONE);world.getBlockAt(3,100,0).setType(Material.CHEST);chest=((Chest)world.getBlockAt(3,100,0).getState()).getBlockInventory();
        for(boolean keep:new boolean[]{false,true}){
            cases.add(new Case("main hand keep="+keep,keep,()->player.getInventory().setItem(0,wand("bound"))));
            cases.add(new Case("offhand keep="+keep,keep,()->player.getInventory().setItemInOffHand(wand("bound"))));
            cases.add(new Case("armor keep="+keep,keep,()->player.getInventory().setHelmet(bound(new ItemStack(Material.DIAMOND_HELMET),"bound"))));
            cases.add(new Case("identical pair keep="+keep,keep,()->{ItemStack w=wand("bound");player.getInventory().setItem(0,w);player.getInventory().setItem(1,w.clone());}));
            cases.add(new Case("full inventory keep="+keep,keep,()->{for(int s=0;s<36;s++)player.getInventory().setItem(s,new ItemStack(Material.STONE,64));player.getInventory().setItem(0,wand("bound"));}));
            cases.add(new Case("vanishing keep="+keep,keep,()->{ItemStack w=wand("bound");w.addUnsafeEnchantment(Enchantment.VANISHING_CURSE,1);player.getInventory().setItem(0,w);}));
            cases.add(new Case("cancelled death keep="+keep,keep,()->{cancelDeath=true;player.getInventory().setItem(0,wand("bound"));}));
            cases.add(new Case("earlier retaining plugin keep="+keep,keep,()->{preRetain=true;player.getInventory().setItem(0,wand("bound"));}));
            cases.add(new Case("cursor player inventory keep="+keep,keep,()->{player.getInventory().setItem(0,wand("bound"));packet(36,0,ContainerInput.PICKUP);}));
            cases.add(new Case("cursor chest open keep="+keep,keep,()->{player.getInventory().setItem(0,wand("bound"));player.openInventory(chest);packet(54,0,ContainerInput.PICKUP);}));
            cases.add(new Case("crafting input keep="+keep,keep,()->{player.openWorkbench(player.getLocation(),true);((CraftingInventory)player.getOpenInventory().getTopInventory()).setMatrix(new ItemStack[]{wand("bound"),null,null,null,null,null,null,null,null});}));
            cases.add(new Case("upgrade escrow keep="+keep,keep,()->{ChestUpgradeGui.open(garden,player);player.getOpenInventory().getTopInventory().setItem(ChestUpgradeGui.SLOT_EQUIP,wand("bound"));}));
        }
        cases.add(new Case("fresh Soulbound apply then native move",false,()->{player.getInventory().setItem(0,marked(magic.wands().create(Spell.FROST_NOVA),"bound"));player.setItemOnCursor(garden.relics().createScrollUnique(UniqueEnchant.SOULBOUND));packet(36,0,ContainerInput.PICKUP);packet(36,0,ContainerInput.PICKUP);packet(37,0,ContainerInput.PICKUP);}));
    }
    void next(){
        if(index>=cases.size()){finish();return;}Case c=cases.get(index++);current=c.label();getLogger().info("CASE "+current);
        spawned.clear();world.getEntitiesByClass(Item.class).forEach(Entity::remove);chest.clear();cancelDeath=false;preRetain=false;world.setGameRule(GameRule.KEEP_INVENTORY,c.keep());actor();c.arrange().run();
        int expected=current.startsWith("identical pair")?2:1;
        player.setHealth(0);
        verify(()->check(inventory("bound")+amount(player.getItemOnCursor(),"bound")+drops("bound")<=expected,"death cannot increase total bound items"));
        Bukkit.getScheduler().runTaskLater(this,()->guard(()->{
            if(!cancelDeath){handle=MinecraftServer.getServer().getPlayerList().respawn(handle,false,net.minecraft.world.entity.Entity.RemovalReason.KILLED,PlayerRespawnEvent.RespawnReason.DEATH);player=handle.getBukkitEntity();player.setGravity(false);}
            Bukkit.getScheduler().runTaskLater(this,()->guard(()->{
                getLogger().info("OUTCOME "+current+" inventory="+inventory("bound")+" cursor="+amount(player.getItemOnCursor(),"bound")+" dropped="+drops("bound"));
                verify(()->check(owned("bound")+drops("bound")<=expected,"respawn cannot increase total bound items"));
                verify(()->check(owned("bound")==expected&&drops("bound")==0,"Soulbound retains all owned equipment exactly once"));
                if(current.equals("main hand keep=false")){
                    for(int repeat=0;repeat<8;repeat++){player.setHealth(0);handle=MinecraftServer.getServer().getPlayerList().respawn(handle,false,net.minecraft.world.entity.Entity.RemovalReason.KILLED,PlayerRespawnEvent.RespawnReason.DEATH);player=handle.getBukkitEntity();check(inventory("bound")==1&&drops("bound")==0,"repeated native death "+repeat+" retains one item");}
                }
                cleanup();next();
            }),3);
        }),3);
    }
    void cleanup(){if(handle==null)return;player.setItemOnCursor(null);player.closeInventory();var list=MinecraftServer.getServer().getPlayerList();list.getPlayers().remove(handle);list.getPlayersByUUID().remove(handle.getUUID());handle.discard();handle=null;}
    void finish(){try{Files.writeString(Path.of("soulbound-security-result.txt"),(failures.isEmpty()?"PASS":"FAIL")+" "+passed+" assertions, "+failures.size()+" failures\n"+String.join("\n",failures));}catch(Exception ignored){}cleanup();Bukkit.shutdown();}
}
