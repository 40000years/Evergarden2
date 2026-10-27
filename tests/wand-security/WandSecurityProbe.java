import com.example.advancemagic.AdvanceMagicPlugin;
import com.example.advancemagic.spell.Spell;
import com.example.voidscape.VoidscapePlugin;
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
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.entity.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import java.net.InetSocketAddress;
import java.nio.file.*;
import java.util.*;

/** Disposable Paper 26.2 integration probe. Never install on a player server. */
public final class WandSecurityProbe extends JavaPlugin {
    static final class TestConnection extends Connection {
        TestConnection(){super(PacketFlow.SERVERBOUND);channel=new EmbeddedChannel();address=new InetSocketAddress("127.0.0.1",1);}
        @Override public void send(Packet<?> packet){}
        @Override public void send(Packet<?> packet,ChannelFutureListener listener){}
        @Override public void send(Packet<?> packet,ChannelFutureListener listener,boolean flush){}
        @Override public boolean isConnected(){return true;}
    }
    record Case(String name,Runnable arrange,Runnable verify){}
    final List<Case> cases=new ArrayList<>();
    final List<String> failures=new ArrayList<>();
    int passed,index;
    AdvanceMagicPlugin magic; VoidscapePlugin garden;
    Player actor; ServerPlayer handle; World world;
    Inventory chestA,chestB;
    Object altar;
    com.example.voidscape.world.RestorationLayout.Site altarSite;
    @Override public void onEnable(){Bukkit.getScheduler().runTaskLater(this,()->guard(()->{begin();next();}),25);}
    void guard(Runnable work){try{work.run();}catch(Throwable t){getLogger().log(java.util.logging.Level.SEVERE,"PROBE FATAL",t);failures.add("fatal: "+t);finish();}}
    void later(Runnable work){Bukkit.getScheduler().runTaskLater(this,()->guard(work),3);}
    void check(boolean value,String label){if(!value)throw new AssertionError(label);passed++;getLogger().info("PASS "+label);}
    void test(String label,Runnable arrange,Runnable verify){cases.add(new Case(label,arrange,verify));}
    ItemStack wand(){return magic.wands().create(Spell.FROST_NOVA);}
    ItemStack scroll(){return garden.relics().createScrollEternity();}
    int amount(ItemStack item){return item==null||item.getType().isAir()?0:item.getAmount();}
    boolean eternity(ItemStack item){return item!=null&&garden.relics().isEternityItem(item);}
    int count(Inventory inv){return Arrays.stream(inv.getContents()).filter(i->magic.wands().spell(i)!=null).mapToInt(ItemStack::getAmount).sum();}
    int allWands(){return count(actor.getInventory())+count(chestA)+count(chestB)+(magic.wands().spell(actor.getItemOnCursor())!=null?amount(actor.getItemOnCursor()):0)+world.getEntitiesByClass(Item.class).stream().map(Item::getItemStack).filter(i->magic.wands().spell(i)!=null).mapToInt(ItemStack::getAmount).sum();}
    int scrolls(){return Arrays.stream(actor.getInventory().getContents()).filter(garden.relics()::isScrollEternity).mapToInt(ItemStack::getAmount).sum()+(garden.relics().isScrollEternity(actor.getItemOnCursor())?amount(actor.getItemOnCursor()):0);}
    void click(int raw){click(raw,0,ContainerInput.PICKUP);}
    void click(int raw,int button,ContainerInput input){
        var menu=handle.containerMenu;
        handle.connection.handleContainerClick(new ServerboundContainerClickPacket(menu.containerId,menu.getStateId(),(short)raw,(byte)button,input,new Int2ObjectOpenHashMap<HashedStack>(),HashedStack.EMPTY));
    }
    void move(int source,int destination){click(source);click(destination);}
    void nativeDrag(int... raws){click(-999,0,ContainerInput.QUICK_CRAFT);for(int raw:raws)click(raw,1,ContainerInput.QUICK_CRAFT);click(-999,2,ContainerInput.QUICK_CRAFT);}
    void compatibilityDrag(int raw){
        // A translated/custom drag may target occupied equipment. Paper restores the
        // old cursor after a cancelled InventoryDragEvent; model that real contract.
        ItemStack old=actor.getItemOnCursor().clone();
        var event=new InventoryDragEvent(actor.getOpenInventory(),null,old,false,Map.of(raw,old));
        Bukkit.getPluginManager().callEvent(event);
        if(event.isCancelled())actor.setItemOnCursor(old);
    }
    void altarCall(String name,Class<?>[] types,Object... values){try{var method=altar.getClass().getDeclaredMethod(name,types);method.setAccessible(true);method.invoke(altar,values);}catch(Exception e){throw new RuntimeException(e);}}
    void altarBegin(){
        try{
            var field=VoidscapePlugin.class.getDeclaredField("restorationAltars");field.setAccessible(true);altar=field.get(garden);
        }catch(Exception e){throw new RuntimeException(e);}
        altarSite=garden.restorationLayout().nearestAltar(0,0,8);
        check(altar!=null&&altarSite!=null,"real generated restoration altar is available");
        World repairWorld=garden.world();repairWorld.getChunkAt(Math.floorDiv(altarSite.x(),16),Math.floorDiv(altarSite.z(),16)).addPluginChunkTicket(this);
        repairWorld.getBlockAt(altarSite.x(),altarSite.y(),altarSite.z()).setType(Material.CHISELED_QUARTZ_BLOCK);
        repairWorld.getBlockAt(altarSite.x(),altarSite.y()+1,altarSite.z()).setType(com.example.voidscape.world.RestorationShrine.marker(altarSite));
        actor.teleport(new Location(repairWorld,altarSite.x()+.5,altarSite.y()+1.5,altarSite.z()+.5));
        ItemStack fuel=magic.restoration().createCore();fuel.setAmount(2);actor.getInventory().setItem(0,fuel);actor.getInventory().setHeldItemSlot(0);
        altarCall("use",new Class<?>[]{Player.class,altarSite.getClass()},actor,altarSite);
        ItemStack damaged=wand();magic.wands().consumeUse(damaged);actor.getInventory().setItem(1,damaged);actor.getInventory().setHeldItemSlot(1);
        altarCall("start",new Class<?>[]{Player.class},actor);
    }
    void altarAdvance(){for(int i=0;i<45;i++)altarCall("tick",new Class<?>[]{});}
    InventoryClickEvent synthetic(int raw,InventoryType.SlotType slotType,boolean cancel){
        var event=new InventoryClickEvent(actor.getOpenInventory(),slotType,raw,ClickType.LEFT,InventoryAction.SWAP_WITH_CURSOR);
        event.setCancelled(cancel);Bukkit.getPluginManager().callEvent(event);return event;
    }
    void hand(){Bukkit.getPluginManager().callEvent(new PlayerInteractEvent(actor,Action.RIGHT_CLICK_AIR,actor.getInventory().getItemInMainHand(),null,org.bukkit.block.BlockFace.SELF,EquipmentSlot.HAND));}
    void reset(){
        actor.setItemOnCursor(null);actor.closeInventory();actor.getInventory().clear();actor.getInventory().setHeldItemSlot(0);
        chestA.clear();chestB.clear();for(var item:world.getEntitiesByClass(Item.class))item.remove();
        actor.setGameMode(GameMode.SURVIVAL);actor.setOp(false);
    }
    void begin(){
        magic=(AdvanceMagicPlugin)Bukkit.getPluginManager().getPlugin("advance-magic");
        garden=(VoidscapePlugin)Bukkit.getPluginManager().getPlugin("Evergarden");
        check(magic!=null&&magic.isEnabled()&&garden!=null&&garden.isEnabled(),"release plugins boot");
        world=Bukkit.getWorlds().get(0);world.getChunkAt(0,0).addPluginChunkTicket(this);
        world.getBlockAt(0,99,0).setType(Material.STONE);world.getBlockAt(3,100,0).setType(Material.CHEST);world.getBlockAt(6,100,0).setType(Material.CHEST);
        chestA=((Chest)world.getBlockAt(3,100,0).getState()).getBlockInventory();chestB=((Chest)world.getBlockAt(6,100,0).getState()).getBlockInventory();
        var server=MinecraftServer.getServer();var level=((CraftWorld)world).getHandle();
        var profile=new GameProfile(UUID.randomUUID(),"WandSecurityActor");
        handle=new ServerPlayer(server,level,profile,ClientInformation.createDefault());
        handle.connection=new ServerGamePacketListenerImpl(server,new TestConnection(),handle,CommonListenerCookie.createInitial(profile,false));
        handle.setPos(.5,100,.5);server.getPlayerList().getPlayers().add(handle);server.getPlayerList().getPlayersByUUID().put(profile.id(),handle);
        level.addNewPlayer(handle);actor=handle.getBukkitEntity();actor.setGravity(false);actor.setGameMode(GameMode.SURVIVAL);actor.setOp(false);
        check(actor.isOnline()&&actor.isValid(),"real server actor in Survival without operator permissions");
        test("scroll on wand then native packet move",()->{actor.getInventory().setItem(0,wand());actor.setItemOnCursor(scroll());click(36);move(36,37);},()->{check(allWands()==1,"native move keeps one wand; observed "+allWands());check(scrolls()==0,"native move paid one scroll");check(eternity(actor.getInventory().getItem(1)),"native move keeps upgraded result");});
        test("reverse touch click then native cursor placement",()->{actor.getInventory().setItem(0,scroll());actor.setItemOnCursor(wand());click(36);click(37);},()->{check(allWands()==1,"reverse touch placement keeps one wand; observed "+allWands());check(scrolls()==0,"reverse touch placement paid scroll");check(eternity(actor.getInventory().getItem(1)),"reverse touch result remains in placed slot");});
        test("reverse touch click then native cursor drop",()->{actor.getInventory().setItem(0,scroll());actor.setItemOnCursor(wand());click(36);click(-999);},()->{check(allWands()==1,"native cursor drop keeps one wand; observed "+allWands());check(amount(actor.getItemOnCursor())==0,"dropped wand is not recreated on cursor");});
        test("reverse touch click then close inventory",()->{actor.openInventory(chestA);chestA.setItem(0,scroll());actor.setItemOnCursor(wand());click(0);actor.closeInventory();},()->check(allWands()==1,"close inventory keeps one wand; observed "+allWands()));
        test("offhand scroll then held slot change",()->{actor.getInventory().setItem(0,wand());actor.getInventory().setItem(1,new ItemStack(Material.DIAMOND));actor.getInventory().setItemInOffHand(scroll());hand();actor.getInventory().setHeldItemSlot(1);},()->{check(allWands()==1,"hotbar selection keeps one wand; observed "+allWands());check(actor.getInventory().getItem(1)!=null&&actor.getInventory().getItem(1).getType()==Material.DIAMOND,"hotbar selection preserves unrelated slot");check(eternity(actor.getInventory().getItem(0)),"original main hand receives enchant");});
        test("main hand scroll then server offhand transfer",()->{actor.getInventory().setItem(0,scroll());actor.getInventory().setItemInOffHand(wand());hand();ItemStack enhanced=actor.getInventory().getItemInOffHand();actor.getInventory().setItemInOffHand(null);actor.getInventory().setItem(5,enhanced);},()->check(allWands()==1,"offhand server transfer keeps one wand; observed "+allWands()));
        test("world chest scroll then native transfer and close",()->{actor.openInventory(chestA);chestA.setItem(0,wand());actor.setItemOnCursor(scroll());click(0);move(0,1);actor.closeInventory();},()->{check(allWands()==1,"world chest movement keeps one wand; observed "+allWands());check(eternity(chestA.getItem(1)),"world chest movement keeps upgraded result");});
        test("single drag event then chest view switch",()->{actor.openInventory(chestA);chestA.setItem(0,wand());actor.setItemOnCursor(scroll());compatibilityDrag(0);click(0,1,ContainerInput.SWAP);actor.closeInventory();actor.openInventory(chestB);chestB.setItem(0,new ItemStack(Material.DIAMOND));},()->{check(allWands()==1,"drag view switch keeps one wand; observed "+allWands());check(chestB.getItem(0)!=null&&chestB.getItem(0).getType()==Material.DIAMOND,"drag does not overwrite a newly opened chest");});
        test("native quick-craft incompatible occupied target",()->{actor.getInventory().setItem(0,wand());actor.setItemOnCursor(scroll());nativeDrag(36);},()->{check(allWands()==1&&!eternity(actor.getInventory().getItem(0)),"native incompatible drag cannot alter wand");check(scrolls()==1,"native incompatible drag preserves scroll");});
        test("native quick-craft multi-slot cancellation",()->{ItemStack source=scroll();source.setAmount(2);actor.setItemOnCursor(source);nativeDrag(36,37);},()->{check(scrolls()==2&&amount(actor.getItemOnCursor())==2,"native cancelled drag retains exact old cursor");check(amount(actor.getInventory().getItem(0))==0&&amount(actor.getInventory().getItem(1))==0,"native cancelled drag creates no slot items");});
        test("compatibility drag pays after Paper cursor restoration",()->{actor.getInventory().setItem(0,wand());actor.setItemOnCursor(scroll());compatibilityDrag(36);},()->{check(allWands()==1&&eternity(actor.getInventory().getItem(0)),"compatibility drag commits one enchanted wand");check(scrolls()==0,"compatibility drag consumes scroll despite Paper restoring old cursor");});
        test("same-tick duplicate compatibility drag",()->{actor.getInventory().setItem(0,wand());actor.setItemOnCursor(scroll());compatibilityDrag(36);compatibilityDrag(36);},()->{check(allWands()==1&&eternity(actor.getInventory().getItem(0)),"duplicate drag commits only one enchanted wand");check(scrolls()==0,"duplicate drag charges exactly one scroll");});
        test("compatibility drag target moves before commit",()->{actor.getInventory().setItem(0,wand());actor.setItemOnCursor(scroll());compatibilityDrag(36);click(36,1,ContainerInput.SWAP);},()->{check(allWands()==1,"changed drag target cannot be replayed; observed "+allWands());check(!eternity(actor.getInventory().getItem(1))&&scrolls()==1,"changed drag target aborts without enchanting or payment");});
        test("compatibility drag cursor changes before commit",()->{actor.getInventory().setItem(0,wand());actor.setItemOnCursor(scroll());compatibilityDrag(36);actor.getInventory().setItem(5,actor.getItemOnCursor());actor.setItemOnCursor(new ItemStack(Material.DIAMOND));},()->{check(!eternity(actor.getInventory().getItem(0))&&scrolls()==1,"changed drag cursor aborts without enchanting or payment");check(actor.getItemOnCursor().getType()==Material.DIAMOND,"changed drag cursor remains untouched");});
        test("native result slot scroll forbidden",()->{actor.openWorkbench(actor.getLocation(),true);((CraftingInventory)actor.getOpenInventory().getTopInventory()).setResult(wand());actor.setItemOnCursor(scroll());click(0);},()->{check(!eternity(actor.getOpenInventory().getItem(0)),"native result slot remains an unenchantable preview");check(scrolls()==1,"native result click preserves scroll payment");check(count(actor.getInventory())==0,"native result click cannot extract a preview wand");});
        test("native public magic menu decoration scroll forbidden",()->{magic.itemMenu().open(actor,false);actor.setItemOnCursor(scroll());click(0);},()->{check(!eternity(actor.getOpenInventory().getItem(0)),"native public magic menu decoration stays untouched");check(scrolls()==1,"native public magic menu click preserves scroll");check(count(actor.getInventory())==0,"native public menu creates no unpaid wand");});
        test("native cancelled menu player inventory scroll forbidden",()->{actor.getInventory().setItem(0,wand());magic.itemMenu().open(actor,false);actor.setItemOnCursor(scroll());click(63);},()->{check(!eternity(actor.getInventory().getItem(0)),"cancelled menu player slot stays untouched");check(scrolls()==1,"cancelled menu player slot preserves scroll");});
        test("stacked equipment scroll forbidden",()->{ItemStack stacked=wand();stacked.setAmount(2);actor.getInventory().setItem(0,stacked);actor.setItemOnCursor(scroll());synthetic(36,InventoryType.SlotType.QUICKBAR,false);},()->{check(allWands()==2&&!eternity(actor.getInventory().getItem(0)),"overstacked equipment cannot receive one-scroll enchant");check(scrolls()==1,"overstacked equipment preserves scroll");});
        test("cancelled inventory scroll event",()->{actor.getInventory().setItem(0,wand());actor.setItemOnCursor(scroll());synthetic(36,InventoryType.SlotType.QUICKBAR,true);},()->{check(!eternity(actor.getInventory().getItem(0)),"cancelled click does not enchant");check(scrolls()==1,"cancelled click does not consume scroll");});
        test("craft result scroll forbidden",()->{actor.openWorkbench(actor.getLocation(),true);((CraftingInventory)actor.getOpenInventory().getTopInventory()).setResult(wand());actor.setItemOnCursor(scroll());synthetic(0,InventoryType.SlotType.RESULT,false);},()->{check(!eternity(actor.getOpenInventory().getItem(0)),"craft result preview cannot be enchanted");check(scrolls()==1,"craft result preview does not consume scroll");});
        test("showcase decoration scroll forbidden",()->{garden.wandGui().open(actor);actor.setItemOnCursor(scroll());synthetic(0,InventoryType.SlotType.CONTAINER,false);},()->{check(!eternity(actor.getOpenInventory().getItem(0)),"showcase decoration cannot be enchanted");check(scrolls()==1,"showcase decoration does not consume scroll");check(count(actor.getInventory())==0,"non-admin showcase grants no wand");});
        test("number key scroll bypass forbidden",()->{actor.getInventory().setItem(0,wand());actor.setItemOnCursor(scroll());var event=new InventoryClickEvent(actor.getOpenInventory(),InventoryType.SlotType.QUICKBAR,36,ClickType.NUMBER_KEY,InventoryAction.HOTBAR_SWAP,1);Bukkit.getPluginManager().callEvent(event);},()->{check(!eternity(actor.getInventory().getItem(0)),"number-key event cannot enchant");check(scrolls()==1,"number-key event preserves scroll");});
        test("multi-slot drag cancellation",()->{actor.getInventory().setItem(0,wand());actor.getInventory().setItem(1,wand());actor.setItemOnCursor(scroll());var event=new InventoryDragEvent(actor.getOpenInventory(),null,scroll(),false,Map.of(36,scroll(),37,scroll()));Bukkit.getPluginManager().callEvent(event);check(event.isCancelled(),"multi-slot scroll drag is cancelled");},()->{check(allWands()==2,"multi-slot drag preserves original wands");check(scrolls()==1,"multi-slot drag preserves scroll");});
        test("wand core upgrade then native packet movement",()->{actor.getInventory().setItem(0,wand());ItemStack core=new ItemStack(Material.PRISMARINE_SHARD);var meta=core.getItemMeta();meta.getPersistentDataContainer().set(new NamespacedKey("advance_magic","wand_upgrade"),PersistentDataType.STRING,"wand_repair");core.setItemMeta(meta);actor.setItemOnCursor(core);click(36);move(36,37);},()->{check(allWands()==1,"wand repair core keeps one moved wand");check(magic.wands().durabilityLevel(actor.getInventory().getItem(1))==1,"wand repair core upgrades exactly one level");check(amount(actor.getItemOnCursor())==0,"wand repair core consumed once");});
        test("atomic menu payment and repeated attempt",()->{actor.getInventory().setItem(0,magic.wands().createCore(Spell.FROST_NOVA));actor.getInventory().setItem(1,new ItemStack(Material.NETHERITE_INGOT,3));actor.getInventory().setItem(2,new ItemStack(Material.NETHER_STAR,5));check(magic.itemMenu().craft(actor,Spell.FROST_NOVA)==null,"menu craft accepts mixed premium payment");check(magic.itemMenu().craft(actor,Spell.FROST_NOVA)!=null,"menu repeated attempt requires another payment");},()->{check(allWands()==1,"menu grants exactly one paid wand");check(Arrays.stream(actor.getInventory().getContents()).filter(Objects::nonNull).noneMatch(i->i.getType()==Material.NETHER_STAR||i.getType()==Material.NETHERITE_INGOT||magic.wands().coreSpell(i)!=null),"menu consumes exact core and eight premium items");});
        test("menu insufficient payment preserves inventory",()->{actor.getInventory().setItem(0,magic.wands().createCore(Spell.FROST_NOVA));actor.getInventory().setItem(1,new ItemStack(Material.NETHER_STAR,7));check(magic.itemMenu().craft(actor,Spell.FROST_NOVA)!=null,"menu rejects insufficient payment");},()->{check(allWands()==0,"unpaid menu creates no wand");check(amount(actor.getInventory().getItem(1))==7&&magic.wands().coreSpell(actor.getInventory().getItem(0))==Spell.FROST_NOVA,"failed menu leaves original ingredients");});
        test("menu full inventory preserves payment",()->{for(int i=0;i<36;i++)actor.getInventory().setItem(i,new ItemStack(Material.COBBLESTONE,64));ItemStack core=magic.wands().createCore(Spell.FROST_NOVA);core.setAmount(2);actor.getInventory().setItem(0,core);actor.getInventory().setItem(1,new ItemStack(Material.NETHER_STAR,9));check(magic.itemMenu().craft(actor,Spell.FROST_NOVA)!=null,"menu requires room for output");},()->{check(allWands()==0,"full inventory menu creates no wand");check(amount(actor.getInventory().getItem(0))==2&&amount(actor.getInventory().getItem(1))==9,"full inventory menu preserves payment");});
        test("all native recipes exact payment",()->{for(Spell spell:Spell.values()){for(int coreSlot:new int[]{4,7}){ItemStack[] grid=new ItemStack[9];for(int i=0;i<9;i++)grid[i]=i==coreSlot?garden.relics().createMagicCore(spell.id()):new ItemStack(i%2==0?Material.NETHER_STAR:Material.NETHERITE_INGOT);var result=Bukkit.craftItemResult(grid,world,actor);check(magic.wands().spell(result.getResult())==spell,"native recipe output "+spell.id()+" core slot "+coreSlot);check(Arrays.stream(result.getResultingMatrix()).allMatch(i->amount(i)==0),"native recipe exact payment "+spell.id()+" core slot "+coreSlot);}}},()->{});
        test("restoration stack and upgrade invariants",()->{ItemStack item=wand();magic.wands().consumeUse(item);int amount=amount(item);check(magic.wands().restore(item)&&amount(item)==amount,"restoration modifies existing item without creating another");item.setAmount(2);magic.wands().consumeUse(item);check(!magic.wands().restore(item),"restoration refuses stacked wands");check(!magic.wands().restore(magic.restoration().createRepairWand()),"restoration cannot recharge restoration wand");},()->{});
        test("restoration ritual exact inventory commit",()->{altarBegin();altarAdvance();},()->{check(count(actor.getInventory())==1,"ritual retains exactly one wand");check(amount(actor.getInventory().getItem(0))==1,"ritual consumes exactly one restoration core");check(magic.wands().usesLeft(actor.getInventory().getItem(1))==magic.wands().maxUses(actor.getInventory().getItem(1)),"ritual restores existing wand to full");altarAdvance();check(amount(actor.getInventory().getItem(0))==1&&count(actor.getInventory())==1,"completed ritual cannot replay fuel or output");});
        test("restoration ritual moved target cancels",()->{altarBegin();ItemStack damaged=actor.getInventory().getItem(1);actor.getInventory().setItem(1,null);actor.getInventory().setItem(5,damaged);altarAdvance();},()->{check(count(actor.getInventory())==1&&amount(actor.getInventory().getItem(0))==2,"moved ritual target preserves wand and fuel");check(magic.wands().usesLeft(actor.getInventory().getItem(5))==29,"moved ritual target does not receive stale repair");});
    }
    void next(){
        if(index>=cases.size()){finish();return;}
        reset();Case c=cases.get(index++);getLogger().info("CASE "+c.name());
        try{c.arrange().run();}catch(Throwable t){failures.add(c.name()+" arrange: "+t);getLogger().warning("FAIL "+failures.getLast());}
        later(()->{try{c.verify().run();}catch(Throwable t){failures.add(c.name()+": "+t);getLogger().warning("FAIL "+failures.getLast());}next();});
    }
    void finish(){
        try{Files.writeString(Path.of("wand-security-result.txt"),(failures.isEmpty()?"PASS":"FAIL")+" "+passed+" assertions, "+failures.size()+" failures\n"+String.join("\n",failures)+"\n");}catch(Exception e){getLogger().log(java.util.logging.Level.SEVERE,"Cannot write results",e);}
        if(handle!=null){actor.setItemOnCursor(null);actor.closeInventory();MinecraftServer.getServer().getPlayerList().getPlayers().remove(handle);MinecraftServer.getServer().getPlayerList().getPlayersByUUID().remove(handle.getUUID());handle.discard();}
        Bukkit.shutdown();
    }
}
