import com.example.advancemagic.AdvanceMagicPlugin;
import com.example.advancemagic.item.ArmorSetService;
import com.example.advancemagic.item.ArmorSetService.*;
import com.example.voidscape.VoidscapePlugin;
import com.example.voidscape.boss.BossArmorCores;
import com.example.voidscape.boss.BossArmorCores.Core;
import com.example.voidscape.boss.SoulOrbs;
import com.example.voidscape.world.BossStructureLayout;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.network.Connection;
import net.minecraft.network.HashedStack;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.server.network.*;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MerchantMenu;
import org.bukkit.*;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.inventory.*;
import org.bukkit.damage.DamageType;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.*;
import java.net.InetSocketAddress;
import java.nio.file.*;
import java.util.*;

/** Actual Paper boss victories, native merchant payments and inventory transactions. */
@SuppressWarnings("unchecked")
public final class BossArmorExchangeProbe extends BossArmorWearProbe {
    AdvanceMagicPlugin magic;ArmorSetService armor;Object shop;
    final List<Item> trophies=new ArrayList<>();
    @Override public void onEnable(){Bukkit.getScheduler().runTaskLater(this,()->{try{run();}catch(Throwable e){finish(e);}},20);}
    @org.bukkit.event.EventHandler(priority=org.bukkit.event.EventPriority.MONITOR,ignoreCancelled=true)
    public void trophy(org.bukkit.event.entity.ItemSpawnEvent event){if(garden!=null&&BossArmorCores.identify(garden,event.getEntity().getItemStack())!=null)trophies.add(event.getEntity());}
    @Override void run()throws Exception{
        garden=(VoidscapePlugin)Bukkit.getPluginManager().getPlugin("Evergarden");
        magic=(AdvanceMagicPlugin)Bukkit.getPluginManager().getPlugin("advance-magic");
        check(garden.isEnabled()&&magic.isEnabled(),"both plugins boot with the new exchange");
        armor=new ArmorSetService(magic);shop=garden.seraphTrader().shop();world=garden.world();world.setDifficulty(Difficulty.NORMAL);
        Location at=world.getSpawnLocation().clone();at.setY(200);world.getChunkAt(at).load();
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)world.addPluginChunkTicket((at.getBlockX()>>4)+x,(at.getBlockZ()>>4)+z,this);
        for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++)world.getBlockAt(at.getBlockX()+x,199,at.getBlockZ()+z).setType(Material.STONE,false);
        var server=MinecraftServer.getServer();var level=((CraftWorld)world).getHandle();var profile=new GameProfile(UUID.randomUUID(),"ArmorExchange");
        actor=new ServerPlayer(server,level,profile,ClientInformation.createDefault());
        var connection=new Connection(PacketFlow.SERVERBOUND);connection.channel=new EmbeddedChannel();connection.address=new InetSocketAddress("127.0.0.1",1);
        actor.connection=new ServerGamePacketListenerImpl(server,connection,actor,CommonListenerCookie.createInitial(profile,false)){@Override public boolean hasClientLoaded(){return true;}};
        actor.setPos(at.getX(),at.getY(),at.getZ());server.getPlayerList().getPlayers().add(actor);server.getPlayerList().getPlayersByUUID().put(profile.id(),actor);level.addNewPlayer(actor);
        player=actor.getBukkitEntity();player.setGravity(false);player.setInvulnerable(true);player.setGameMode(GameMode.SURVIVAL);
        Bukkit.getPluginManager().registerEvents(this,this);
        exchange();crafting();
        // Let the fake player's chunk tickets enter the native entity-ticking set before combat.
        Bukkit.getScheduler().runTaskLater(this,()->{try{bosses(at);finish(null);}catch(Throwable e){finish(e);}},5);
    }
    void open()throws Exception{player.closeInventory();player.getInventory().clear();player.setItemOnCursor(null);call(shop,"openTrader",new Class<?>[]{Player.class,boolean.class},player,true);}
    MerchantInventory inventory(){return (MerchantInventory)player.getOpenInventory().getTopInventory();}
    boolean pays(MerchantRecipe recipe,ItemStack item){
        return CraftMerchantRecipe.fromBukkit(recipe).toMinecraft().satisfiedBy(CraftItemStack.asNMSCopy(item),net.minecraft.world.item.ItemStack.EMPTY);
    }
    void select(int index){((MerchantMenu)actor.containerMenu).setSelectionHint(index);}
    int armorIndex(int ordinal){
        ItemStack expected=armor.create(ordinal<4?ArmorSet.SERAPH:ArmorSet.REVENANT,Piece.values()[ordinal%4]);
        for(int i=0;i<inventory().getMerchant().getRecipeCount();i++)if(inventory().getMerchant().getRecipe(i).getResult().isSimilar(expected))return i;
        throw new AssertionError("Missing armor offer "+ordinal);
    }
    void clickResult(ContainerInput type){
        var menu=actor.containerMenu;
        actor.connection.handleContainerClick(new ServerboundContainerClickPacket(menu.containerId,menu.getStateId(),(short)2,(byte)0,type,new Int2ObjectOpenHashMap<HashedStack>(),HashedStack.EMPTY));
    }
    long armorCount(){return Arrays.stream(player.getInventory().getStorageContents()).filter(i->armor.set(i)!=null).mapToLong(ItemStack::getAmount).sum();}
    void exchange()throws Exception{
        call(shop,"hub",new Class<?>[]{Player.class},player);
        Object hub=player.getOpenInventory().getTopInventory().getHolder();
        var actions=(Map<Integer,Runnable>)get(hub,"actions");
        check(actions.containsKey(15)&&!actions.containsKey(22)&&actions.size()==2,"NPC keeps just magic and legends, without a separate armor category");
        actions.get(15).run();
        check(inventory().getMerchant().getRecipes().stream().filter(r->armor.set(r.getResult())!=null).count()==8,"legend shop includes all four slots of both armor sets");
        check(inventory().getMerchant().getRecipes().stream().anyMatch(r->SoulOrbs.isLegendCore(garden,r.getResult())),"existing Core of Legend offers remain in the same shop");
        for(int index=0;index<8;index++){
            open();int selected=armorIndex(index);MerchantRecipe recipe=inventory().getMerchant().getRecipe(selected);
            Core core=index<4?Core.SERAPH:Core.REVENANT;ArmorSet set=index<4?ArmorSet.SERAPH:ArmorSet.REVENANT;Piece piece=Piece.values()[index%4];
            ItemStack expected=armor.create(set,piece),currency=BossArmorCores.create(garden,core);
            check(recipe.getResult().isSimilar(expected),set+" "+piece+" is the existing modeled Combat armor with native stats");
            check(recipe.getIngredients().size()==1&&recipe.getIngredients().getFirst().getAmount()==1&&recipe.getSpecialPrice()==0,set+" "+piece+" costs exactly one core without extra fees or discounts");
            check(pays(recipe,currency),set+" "+piece+" accepts its authentic core");
            ItemStack vanilla=new ItemStack(currency.getType());var meta=vanilla.getItemMeta();meta.displayName(currency.getItemMeta().displayName());vanilla.setItemMeta(meta);
            check(!pays(recipe,vanilla)&&BossArmorCores.identify(garden,vanilla)==null,set+" "+piece+" rejects renamed vanilla currency");
            check(!pays(recipe,BossArmorCores.create(garden,core==Core.SERAPH?Core.REVENANT:Core.SERAPH))&&!pays(recipe,SoulOrbs.legendCore(garden)),set+" "+piece+" rejects the other set and Core of Legend");
            check(BossArmorCores.identify(garden,ItemStack.deserializeBytes(currency.serializeAsBytes()))==core,set+" core keeps identity through native serialization");
            check(core.model().equals(currency.getItemMeta().getItemModel())&&currency.getItemMeta().getCustomModelDataComponent().getStrings().equals(List.of(core.model().toString())),set+" core selects its dedicated Java and Bedrock texture");
            select(selected);inventory().setItem(0,currency);
            check(inventory().getItem(2)!=null&&inventory().getItem(2).isSimilar(expected),set+" "+piece+" selection previews the chosen item");
            clickResult(ContainerInput.PICKUP);
            check(player.getItemOnCursor().isSimilar(expected)&&(inventory().getItem(0)==null||inventory().getItem(0).isEmpty()),set+" "+piece+" real native purchase consumes one core and delivers one item");
            player.setItemOnCursor(null);clickResult(ContainerInput.PICKUP);
            check(player.getItemOnCursor().isEmpty(),set+" "+piece+" cannot repeat a purchase with no core");
        }
        for(Core core:Core.values()){
            player.closeInventory();player.getInventory().clear();ItemStack old=BossArmorCores.create(garden,core);old.setAmount(3);
            var meta=old.getItemMeta();meta.setItemModel(null);meta.setCustomModelDataComponent(null);old.setItemMeta(meta);player.getInventory().setItem(0,old);
            call(shop,"openTrader",new Class<?>[]{Player.class,boolean.class},player,true);ItemStack migrated=player.getInventory().getItem(0);
            check(migrated.getAmount()==3&&core.model().equals(migrated.getItemMeta().getItemModel()),core+" previously earned cores gain artwork without losing quantity");
            check(pays(inventory().getMerchant().getRecipe(armorIndex(core==Core.SERAPH?0:4)),migrated),core+" legacy cores remain spendable after texture migration");
        }
        open();ItemStack four=BossArmorCores.create(garden,Core.REVENANT);four.setAmount(4);select(armorIndex(4));inventory().setItem(0,four);clickResult(ContainerInput.QUICK_MOVE);
        check(armorCount()==4&&(inventory().getItem(0)==null||inventory().getItem(0).isEmpty()),"shift-click buys four pieces for four cores without daily or weekly stock limits");
        open();for(int slot=0;slot<36;slot++)player.getInventory().setItem(slot,new ItemStack(Material.STONE,64));
        select(armorIndex(0));inventory().setItem(0,BossArmorCores.create(garden,Core.SERAPH));clickResult(ContainerInput.QUICK_MOVE);
        check(inventory().getItem(0)!=null&&inventory().getItem(0).getAmount()==1&&armorCount()==0,"full inventory refuses shift-click without consuming the core");
        player.getInventory().setItem(0,null);player.closeInventory();
        check(Arrays.stream(player.getInventory().getStorageContents()).filter(i->BossArmorCores.identify(garden,i)==Core.SERAPH).mapToInt(ItemStack::getAmount).sum()==1,"closing a failed exchange returns the one unspent core");
        player.getInventory().clear();
    }
    void crafting(){
        var view=player.openWorkbench(null,true);CraftingInventory crafting=(CraftingInventory)view.getTopInventory();
        for(Core core:Core.values()){
            ItemStack[] matrix=new ItemStack[9];matrix[4]=BossArmorCores.create(garden,core);crafting.setMatrix(matrix);crafting.setResult(new ItemStack(Material.BEACON));
            Bukkit.getPluginManager().callEvent(new PrepareItemCraftEvent(crafting,view,false));
            check(crafting.getResult()==null||crafting.getResult().isEmpty(),core+" cannot be consumed as a native crafting ingredient");
        }
        crafting.setMatrix(new ItemStack[]{new ItemStack(Material.GLASS),null,null,null,null,null,null,null,null});crafting.setResult(new ItemStack(Material.GLASS_BOTTLE));
        Bukkit.getPluginManager().callEvent(new PrepareItemCraftEvent(crafting,view,false));
        check(crafting.getResult()!=null&&!crafting.getResult().isEmpty(),"ordinary crafting remains available");player.closeInventory();
    }
    List<Item> drops(Core core){return trophies.stream().filter(i->BossArmorCores.identify(garden,i.getItemStack())==core).toList();}
    /** Dispatch a confirmed server hit; native same-tick hurt throttling is outside this reward fixture. */
    void confirmedHit(Slime target,double damage){
        Bukkit.getPluginManager().callEvent(new EntityDamageByEntityEvent(player,target,DamageCause.MAGIC,
            DamageSource.builder(DamageType.INDIRECT_MAGIC).withCausingEntity(player).withDirectEntity(player).build(),damage));
    }
    Object arkhaia(Object manager,Location at,String id)throws Exception{
        var type=Class.forName("com.example.voidscape.boss.ArkhaiaBoss$Run");var ctor=type.getDeclaredConstructor(VoidscapePlugin.class,Location.class,long.class);ctor.setAccessible(true);
        Object run=ctor.newInstance(garden,at,0L);Slime target=world.spawn(at,Slime.class,s->{
            s.setAI(false);s.setGravity(false);s.setNoDamageTicks(0);
            s.getPersistentDataContainer().set(garden.key("world_boss_entity"),org.bukkit.persistence.PersistentDataType.BYTE,(byte)1);
            s.getPersistentDataContainer().set(garden.key("arkhaia_boss"),org.bukkit.persistence.PersistentDataType.BYTE,(byte)1);
            s.addScoreboardTag("evergarden_unique");
        });
        put(run,"target",target);((List<Entity>)get(run,"parts")).add(target);((Map<String,Object>)get(manager,"active")).put(id,run);return run;
    }
    void bosses(Location at)throws Exception{
        player.teleport(at.clone().add(0,0,5));Object ark=get(garden.worldBoss(),"arkhaia"),run=arkhaia(ark,at,"armor_exchange_victory");Slime target=(Slime)get(run,"target");
        check(drops(Core.REVENANT).isEmpty()&&drops(Core.SERAPH).isEmpty(),"summoning and shop previews do not produce cores");
        magic.context().damage(player,target,1000000,DamageType.INDIRECT_MAGIC);
        check((double)get(run,"health")==3500&&drops(Core.REVENANT).isEmpty(),"Arkhaia phase-one defeat does not drop a core; hp="+get(run,"health")+", targetValid="+target.isValid()+", targetDead="+target.isDead()+", playerOnline="+player.isOnline());
        ((Map<?,?>)get(run,"hits")).clear();confirmedHit(target,1000000);
        check(drops(Core.REVENANT).size()==1&&drops(Core.REVENANT).getFirst().getItemStack().getAmount()==1,"Arkhaia final defeat physically drops exactly one Core of Revenant; hp="+get(run,"health")+", drops="+drops(Core.REVENANT).size()+", valid="+target.isValid());
        check(((Map<?,?>)get(ark,"active")).isEmpty(),"defeated Arkhaia run is removed before another hit can reward it");
        arkhaia(ark,at,"armor_exchange_abort");call(ark,"close",new Class<?>[]{});
        check(drops(Core.REVENANT).size()==1,"aborting or shutting down an Arkhaia encounter produces no extra core");
        Object seraph=garden.seraphonBoss();var type=Class.forName("com.example.voidscape.boss.SeraphonBoss$Run");
        var ctor=type.getDeclaredConstructor(VoidscapePlugin.class,BossStructureLayout.Site.class);ctor.setAccessible(true);
        Object heavenly=ctor.newInstance(garden,new BossStructureLayout.Site(BossStructureLayout.Kind.SERAPHON,at.getBlockX(),at.getBlockZ()));
        Location center=(Location)get(heavenly,"center");center.setY(at.getY());put(heavenly,"position",center.clone());put(heavenly,"fighting",true);put(heavenly,"health",1.0);
        ((Map<String,Object>)get(seraph,"runs")).put((String)get(heavenly,"id"),heavenly);
        call(seraph,"spawn",new Class<?>[]{type},heavenly);Slime receiver=(Slime)get(heavenly,"anchor");player.teleport(center.clone().add(0,0,5));
        confirmedHit(receiver,100);
        check((boolean)get(heavenly,"dead")&&drops(Core.SERAPH).size()==1&&drops(Core.SERAPH).getFirst().getItemStack().getAmount()==1,"Seraphon final defeat physically drops exactly one Core of Seraph; hp="+get(heavenly,"health")+", dead="+get(heavenly,"dead"));
        call(seraph,"defeat",new Class<?>[]{type},heavenly);call(seraph,"close",new Class<?>[]{});
        check(drops(Core.SERAPH).size()==1&&drops(Core.REVENANT).size()==1,"duplicate defeat callbacks and cleanup never multiply either shared boss drop");
        for(Core core:Core.values())check(drops(core).getFirst().isGlowing()&&drops(core).getFirst().isInvulnerable(),core+" trophy is visible and protected at the arena center");
    }
    @Override void finish(Throwable error){
        if(error!=null)error.printStackTrace();
        try{if(player!=null)player.closeInventory();Files.writeString(Path.of("arkhaia-result.txt"),(error==null?"PASS ":"FAIL ")+checks+" boss armor exchange checks"+(error==null?"":"\n"+error)+"\n");}catch(Exception e){e.printStackTrace();}
        if(actor!=null){var server=MinecraftServer.getServer();server.getPlayerList().getPlayers().remove(actor);server.getPlayerList().getPlayersByUUID().remove(actor.getUUID());actor.discard();}
        Bukkit.shutdown();
    }
}
