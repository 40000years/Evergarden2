import com.example.voidscape.VoidscapePlugin;
import com.example.voidscape.trade.SanctuaryMaps;
import com.example.voidscape.trade.SanctuaryMaps.Destination;
import com.example.voidscape.trade.SanctuaryMaps.MapTarget;
import com.example.voidscape.trade.SeraphFeathers;
import com.example.voidscape.world.WorldBossTempleLayout;
import com.example.voidscape.world.BossStructureLayout;
import com.example.voidscape.world.SkyPlacementHistory;
import com.example.voidscape.world.OverworldSanctuaries;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.network.Connection;
import net.minecraft.network.HashedStack;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MerchantMenu;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.craftbukkit.inventory.CraftMerchantRecipe;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import io.papermc.paper.event.player.PlayerPurchaseEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.CartographyInventory;
import org.bukkit.inventory.Merchant;
import org.bukkit.inventory.MerchantInventory;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.*;
import org.bukkit.plugin.java.JavaPlugin;
import java.lang.reflect.*;
import java.net.InetSocketAddress;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

/** Real CraftPlayer inventories, native map objects and asynchronous production transactions. */
public final class SanctuaryMapChecks extends JavaPlugin {
    private VoidscapePlugin garden;
    private SanctuaryMaps maps;
    private World overworld;
    private Player buyer;
    private int passed;
    private boolean finished;
    private boolean reloadPhase;
    private final List<ServerPlayer> actors=new ArrayList<>();
    private final EnumMap<Destination,MapTarget> acquired=new EnumMap<>(Destination.class);
    private final EnumMap<Destination,byte[]> savedItems=new EnumMap<>(Destination.class);
    private final EnumMap<Destination,byte[]> previewItems=new EnumMap<>(Destination.class);
    private final EnumMap<Destination,MapTarget> previewTargets=new EnumMap<>(Destination.class);
    private final EnumMap<Destination,OverworldSanctuaries.Site> fixed=new EnumMap<>(Destination.class);
    private Map<Long,Optional<OverworldSanctuaries.Site>> cachedCells;
    private Map<Long,Optional<WorldBossTempleLayout.Site>> judgeCells;
    private Map<Long,List<BossStructureLayout.Site>> bossCells;
    private final EnumMap<Destination,MapTarget> fixedEver=new EnumMap<>(Destination.class);
    @Override public void onEnable(){Bukkit.getScheduler().runTaskLater(this,()->{
        try{reloadPhase=Boolean.getBoolean("sanctuary.maps.reload");
            if(reloadPhase){reload();finish(null);}else{setup();run().whenComplete((ignored,error)->main(()->finish(error)));}}
        catch(Throwable error){finish(error);}
    },30);Bukkit.getScheduler().runTaskLater(this,()->{if(!finished)finish(new AssertionError("map checks timed out"));},4000);}
    private void main(Runnable task){if(Bukkit.isPrimaryThread())task.run();else Bukkit.getScheduler().runTask(this,task);}
    private void check(boolean value,String label){if(!value)throw new AssertionError(label);passed++;getLogger().info("PASS "+label);}
    private static Object field(Object object,String name)throws Exception {Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}
    private static final class Capture extends Connection {
        Capture(){super(PacketFlow.SERVERBOUND);channel=new EmbeddedChannel();address=new InetSocketAddress("127.0.0.1",1);}
        @Override public void send(net.minecraft.network.protocol.Packet<?> packet){}
        @Override public void send(net.minecraft.network.protocol.Packet<?> packet,io.netty.channel.ChannelFutureListener listener){}
        @Override public void send(net.minecraft.network.protocol.Packet<?> packet,io.netty.channel.ChannelFutureListener listener,boolean flush){}
        @Override public boolean isConnected(){return true;}
    }
    private Player actor(String name,World world){
        var server=MinecraftServer.getServer();var level=((CraftWorld)world).getHandle();
        var profile=new GameProfile(UUID.randomUUID(),name);
        var handle=new ServerPlayer(server,level,profile,ClientInformation.createDefault());
        handle.connection=new ServerGamePacketListenerImpl(server,new Capture(),handle,CommonListenerCookie.createInitial(profile,false)){
            @Override public boolean hasClientLoaded(){return true;}
        };
        handle.setPos(7.5,100,.5);server.getPlayerList().getPlayers().add(handle);
        server.getPlayerList().getPlayersByUUID().put(profile.id(),handle);level.addNewPlayer(handle);actors.add(handle);
        Player player=handle.getBukkitEntity();player.setGravity(false);player.setGameMode(GameMode.SURVIVAL);return player;
    }
    @SuppressWarnings("unchecked") private void setup()throws Exception {
        garden=(VoidscapePlugin)Bukkit.getPluginManager().getPlugin("Evergarden");
        check(garden!=null&&garden.isEnabled(),"Evergarden enabled");
        maps=garden.seraphTrader().shop().maps();
        check(maps!=null,"Seraph exposes production map shop");
        overworld=Bukkit.getWorlds().getFirst();
        check(garden.overworldSanctuaries().placement(overworld)!=null,"normal world has sanctuary placement");
        // Only the fixture's cached cells are changed. The real async locator still runs.
        var placement=garden.overworldSanctuaries().placement(overworld);
        Map<Long,Optional<OverworldSanctuaries.Site>> cache=(Map<Long,Optional<OverworldSanctuaries.Site>>)field(placement,"cache");
        cachedCells=cache;
        for(int x=-8;x<=8;x++)for(int z=-8;z<=8;z++)cache.put(OverworldSanctuaries.key(x,z),Optional.empty());
        int index=0,size=placement.spacing*16;
        for(var destination:Destination.values())if(destination.overworld()){
            int gx=1+index%3,gz=index<3?1:2;
            var site=new OverworldSanctuaries.Site(OverworldSanctuaries.Kind.valueOf(destination.name()),gx*size+size/2,gz*size+size/2,100L+index);
            cache.put(OverworldSanctuaries.key(gx,gz),Optional.of(site));fixed.put(destination,site);index++;
        }
        buyer=actor("MapBuyer",garden.world());
        judgeCells=(Map<Long,Optional<WorldBossTempleLayout.Site>>)field(garden.bossTemples(),"cache");
        bossCells=(Map<Long,List<BossStructureLayout.Site>>)field(garden.bossStructures(),"cache");
        for(int x=-26;x<=26;x++)for(int z=-26;z<=26;z++){
            judgeCells.put(SkyPlacementHistory.key(x,z),Optional.empty());bossCells.put(SkyPlacementHistory.key(x,z),List.of());
        }
        int judgeSize=garden.bossTemples().spacingChunks()*16,bossSize=garden.bossStructures().spacingChunks()*16;
        var judge=new WorldBossTempleLayout.Site(3*judgeSize+judgeSize/2,3*judgeSize+judgeSize/2);
        judgeCells.put(SkyPlacementHistory.key(3,3),Optional.of(judge));fixedEver.put(Destination.JUDGE,new MapTarget(garden.world().getUID(),Destination.JUDGE,judge.x(),judge.z()));
        for(var destination:new Destination[]{Destination.ARKHAIA,Destination.SERAPHON}){
            int gx=destination==Destination.ARKHAIA?4:5;
            var kind=destination==Destination.ARKHAIA?BossStructureLayout.Kind.ARKHAIA:BossStructureLayout.Kind.SERAPHON;
            var site=new BossStructureLayout.Site(kind,gx*bossSize+bossSize/2,3*bossSize+bossSize/2);
            bossCells.put(SkyPlacementHistory.key(gx,3),List.of(site));fixedEver.put(destination,new MapTarget(garden.world().getUID(),destination,site.x(),site.z()));
        }
        // A nearer Valkyrie must never be confused with the Seraphon main boss.
        bossCells.put(SkyPlacementHistory.key(1,1),List.of(new BossStructureLayout.Site(BossStructureLayout.Kind.VALKYRIE,bossSize+bossSize/2,bossSize+bossSize/2)));
        check(Destination.values().length==9&&fixed.size()==6,"nine destinations with six Overworld sanctuaries");
        for(var destination:Destination.values())check(maps.price(destination)==(destination.overworld()?30:10),"exact default price "+destination);
        check(Arrays.stream(Destination.values()).filter(destination->!destination.overworld()).collect(java.util.stream.Collectors.toSet()).equals(Set.of(Destination.JUDGE,Destination.ARKHAIA,Destination.SERAPHON)),"Evergarden choices are exactly Judge, Arkhaia and Seraphon, without Tier selector or Valkyrie");
        check(maps.target(new ItemStack(Material.FILLED_MAP))==null,"ordinary map cannot impersonate purchased target");
    }
    private void seedFixedTargets(){
        // A NO_TARGET search can exhaust the whole 49x49 search area and evict the
        // bounded production LRU's fixtures. Refill before later transaction checks.
        cachedCells.clear();
        for(int x=-8;x<=8;x++)for(int z=-8;z<=8;z++)cachedCells.put(OverworldSanctuaries.key(x,z),Optional.empty());
        int size=garden.overworldSanctuaries().placement(overworld).spacing*16;
        for(var site:fixed.values())cachedCells.put(OverworldSanctuaries.key(Math.floorDiv(site.x(),size),Math.floorDiv(site.z(),size)),Optional.of(site));
    }
    private void clear(Player player){player.getInventory().clear();player.setItemOnCursor(null);}
    private void funds(Player player,boolean golden,int amount){clear(player);player.getInventory().setItem(0,golden?SeraphFeathers.golden(garden,amount):SeraphFeathers.feather(garden,amount));}
    private int feathers(Player player,boolean golden){int total=0;for(ItemStack item:player.getInventory().getStorageContents())if(golden?SeraphFeathers.isGolden(garden,item):SeraphFeathers.isFeather(garden,item))total+=item.getAmount();return total;}
    private List<ItemStack> owned(Player player){return Arrays.stream(player.getInventory().getStorageContents()).filter(Objects::nonNull).filter(item->maps.target(item)!=null).toList();}
    private MerchantInventory inventory(Player player){return (MerchantInventory)player.getOpenInventory().getTopInventory();}
    private MerchantRecipe recipe(Player player,Destination destination){return inventory(player).getMerchant().getRecipe(destination.ordinal());}
    private void reset(Player player){player.closeInventory();clear(player);}
    private void select(Player player,Destination destination){((MerchantMenu)((CraftPlayer)player).getHandle().containerMenu).setSelectionHint(destination.ordinal());}
    private void clickResult(Player player,ContainerInput input){
        ServerPlayer handle=((CraftPlayer)player).getHandle();var menu=handle.containerMenu;
        handle.connection.handleContainerClick(new ServerboundContainerClickPacket(menu.containerId,menu.getStateId(),(short)2,(byte)0,input,new Int2ObjectOpenHashMap<HashedStack>(),HashedStack.EMPTY));
    }
    private boolean empty(ItemStack item){return item==null||item.isEmpty();}
    private boolean pays(MerchantRecipe recipe,ItemStack currency){return CraftMerchantRecipe.fromBukkit(recipe).toMinecraft().satisfiedBy(CraftItemStack.asNMSCopy(currency),net.minecraft.world.item.ItemStack.EMPTY);}
    private CompletableFuture<Void> opened(Player player,Runnable after){
        CompletableFuture<Void> result=new CompletableFuture<>();maps.openTrades(player).whenComplete((merchant,error)->main(()->{
            try{if(error!=null)throw new CompletionException(error);check(merchant!=null&&player.getOpenInventory().getTopInventory() instanceof MerchantInventory,"native villager trade screen opens");
                check(inventory(player).getMerchant()==merchant&&merchant.getRecipeCount()==9,"one native merchant contains all nine destinations");after.run();result.complete(null);}
            catch(Throwable failure){result.completeExceptionally(failure);}
        }));return result;
    }
    private CompletableFuture<Void> nativePurchase(Player player,Destination destination,Runnable after){
        reset(player);int price=maps.price(destination),amount=price*2;boolean golden=!destination.overworld();funds(player,golden,amount);
        return opened(player,()->{
            check(feathers(player,golden)==amount,"opening map offers does not charge currency "+destination);
            MerchantRecipe offer=recipe(player,destination);MapTarget expected=maps.target(offer.getResult());
            String title=player.getOpenInventory().getTitle();String itemName=net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(offer.getResult().getItemMeta().displayName());
            check(title.contains("แผนที่")&&!title.contains("???")&&itemName.contains("แผนที่")&&!itemName.contains("???"),"native merchant and actual map retain readable Thai labels "+destination);
            check(expected!=null&&expected.destination()==destination&&offer.getResult().getType()==Material.FILLED_MAP,"native offer displays actual bound map "+destination);
            check(offer.getMaxUses()==1&&offer.getUses()==0,"target can be purchased once in this native merchant "+destination);
            ItemStack currency=golden?SeraphFeathers.golden(garden,amount):SeraphFeathers.feather(garden,amount);
            check(offer.getIngredients().size()==1&&offer.getIngredients().getFirst().getAmount()==price&&offer.getSpecialPrice()==0,"native offer shows exact single currency price "+destination);
            check(pays(offer,currency)&&!pays(offer,golden?SeraphFeathers.feather(garden,amount):SeraphFeathers.golden(garden,amount)),"native price accepts exact currency kind "+destination);
            player.getInventory().setItem(0,null);select(player,destination);inventory(player).setItem(0,currency);
            check(expected.equals(maps.target(inventory(player).getItem(2))),"putting currency previews the same real map "+destination);
            clickResult(player,ContainerInput.PICKUP);ItemStack purchased=player.getItemOnCursor();
            check(expected.equals(maps.target(purchased)),"native result click delivers chosen map on cursor "+destination);
            check(inventory(player).getItem(0)!=null&&inventory(player).getItem(0).getAmount()==price,"native click consumes exactly one price "+destination);
            ItemStack delivered=purchased.clone();player.setItemOnCursor(null);player.getInventory().addItem(delivered);
            clickResult(player,ContainerInput.PICKUP);
            check(empty(player.getItemOnCursor())&&inventory(player).getItem(0).getAmount()==price,"same native offer cannot sell a second target map "+destination);
            player.closeInventory();check(feathers(player,golden)==price&&owned(player).size()==1,"closing returns unspent price and keeps exactly one bought map "+destination);
            after.run();
        });
    }
    private CompletableFuture<Void> step(Supplier<CompletableFuture<Void>> action){try{return action.get();}catch(Throwable error){return CompletableFuture.failedFuture(error);}}
    private CompletableFuture<Void> run(){
        CompletableFuture<Void> chain=CompletableFuture.completedFuture(null);
        chain=chain.thenCompose(ignored->step(this::disabledDestinations));
        chain=chain.thenCompose(ignored->step(this::poorPlayersAndPreviews));
        chain=chain.thenCompose(ignored->step(this::noTarget));
        chain=chain.thenCompose(ignored->step(this::concurrentAndCancelled));
        chain=chain.thenCompose(ignored->step(this::invalidCurrency));
        chain=chain.thenCompose(ignored->step(this::inventoryCapacity));
        for(var destination:Destination.values())chain=chain.thenCompose(ignored->step(()->nativePurchase(buyer,destination,()->verifyMap(buyer,destination))));
        chain=chain.thenCompose(ignored->step(this::alreadyOwned));
        chain=chain.thenCompose(ignored->step(this::lateCancelAndRetry));
        chain=chain.thenCompose(ignored->step(this::visitedEverMainBoss));
        chain=chain.thenCompose(ignored->step(this::revisitAndReason));
        chain=chain.thenCompose(ignored->step(this::visitedOverworld));
        return chain.thenRun(()->{check(acquired.size()==9,"all nine destination maps purchased and verified");cartography();
            check(Files.isRegularFile(garden.getDataFolder().toPath().resolve("seraph-sanctuary-maps.yml")),"map registry persisted before successful delivery");
            SanctuaryMaps reloaded=new SanctuaryMaps(garden);
            try{for(var destination:Destination.values()){
                ItemStack restored=ItemStack.deserializeBytes(savedItems.get(destination));
                check(acquired.get(destination).equals(reloaded.target(restored)),"fresh service loads fixed target from persisted registry "+destination);
                MapView view=((MapMeta)restored.getItemMeta()).getMapView();
                for(var renderer:List.copyOf(view.getRenderers()))if(renderer.getClass().getEnclosingClass()==SanctuaryMaps.class)view.removeRenderer(renderer);
                reloaded.restore(view);
                check(view.getRenderers().stream().anyMatch(renderer->renderer.getClass().getEnclosingClass()==SanctuaryMaps.class),"fresh service restores saved map overlay "+destination);
            }}finally{reloaded.close();}
            saveReloadState();
        });
    }
    private CompletableFuture<Void> poorPlayersAndPreviews(){
        Player previewer=actor("MapPreviewer",garden.world());reset(previewer);
        return opened(previewer,()->{
            for(var destination:Destination.values()){
                MerchantRecipe offer=recipe(previewer,destination);MapTarget target=maps.target(offer.getResult());
                check(target!=null&&target.destination()==destination&&offer.getMaxUses()==1,"poor player sees real purchasable map result "+destination);
                check(offer.getIngredients().getFirst().getAmount()==maps.price(destination),"poor player sees real currency ingredient count "+destination);
                previewItems.put(destination,offer.getResult().serializeAsBytes());previewTargets.put(destination,target);
            }
            select(previewer,Destination.ACHERON);clickResult(previewer,ContainerInput.PICKUP);
            check(empty(previewer.getItemOnCursor())&&owned(previewer).isEmpty(),"clicking a real map offer without funds cannot take result");
            MapTarget first=previewTargets.get(Destination.ACHERON);
            maps.teleport(new PlayerTeleportEvent(previewer,previewer.getLocation(),new Location(overworld,first.x(),100,first.z()),PlayerTeleportEvent.TeleportCause.PLUGIN));previewer.closeInventory();
        }).thenCompose(ignored->opened(previewer,()->{
            for(var destination:Destination.values()){
                ItemStack original=ItemStack.deserializeBytes(previewItems.get(destination));
                check(((MapMeta)original.getItemMeta()).getMapView().getId()==((MapMeta)recipe(previewer,destination).getResult().getItemMeta()).getMapView().getId(),"reopening unbought preview reuses fixed map ID "+destination);
            }
            check(previewTargets.get(Destination.ACHERON).equals(maps.target(recipe(previewer,Destination.ACHERON).getResult())),"arriving at an unbought preview never advances target");previewer.closeInventory();
        }));
    }
    private CompletableFuture<Void> invalidCurrency(){
        reset(buyer);return opened(buyer,()->{
            ItemStack fake=new ItemStack(Material.FEATHER,30);var meta=fake.getItemMeta();meta.displayName(SeraphFeathers.feather(garden,1).getItemMeta().displayName());fake.setItemMeta(meta);
            ItemStack[] invalid={SeraphFeathers.feather(garden,29),SeraphFeathers.golden(garden,30),fake};
            for(ItemStack currency:invalid){select(buyer,Destination.COCYTUS);inventory(buyer).setItem(0,currency);clickResult(buyer,ContainerInput.PICKUP);
                check(empty(buyer.getItemOnCursor())&&empty(inventory(buyer).getItem(2))&&inventory(buyer).getItem(0).getAmount()==currency.getAmount(),"native trade rejects insufficient, wrong-kind or untagged normal currency");inventory(buyer).setItem(0,null);}
            select(buyer,Destination.JUDGE);inventory(buyer).setItem(0,SeraphFeathers.feather(garden,30));clickResult(buyer,ContainerInput.PICKUP);
            check(empty(buyer.getItemOnCursor())&&empty(inventory(buyer).getItem(2))&&inventory(buyer).getItem(0).getAmount()==30,"normal feathers cannot substitute for golden native price");
            buyer.closeInventory();check(feathers(buyer,false)==30,"closing native merchant returns all unspent wrong currency");
        });
    }
    private CompletableFuture<Void> disabledDestinations(){
        String[] paths={"world-boss.enabled","structures.world-boss-temple.enabled","seraphon.enabled","structures.boss-structures.arkhaia-castle.enabled"};
        Destination[] destinations={Destination.JUDGE,Destination.JUDGE,Destination.SERAPHON,Destination.ARKHAIA};
        CompletableFuture<Void> chain=CompletableFuture.completedFuture(null);
        for(int index=0;index<paths.length;index++){
            String path=paths[index];Destination destination=destinations[index];
            chain=chain.thenCompose(ignored->{reset(buyer);Object previous=garden.getConfig().get(path);garden.getConfig().set(path,false);funds(buyer,true,10);
                return opened(buyer,()->{MerchantRecipe offer=recipe(buyer,destination);
                    check(offer.getMaxUses()==0&&maps.target(offer.getResult())==null,"disabled destination remains a disabled native row "+path);
                    check(feathers(buyer,true)==10&&owned(buyer).isEmpty(),"disabled destination opening does not charge golden feathers "+path);buyer.closeInventory();garden.getConfig().set(path,previous);
                });});
        }
        return chain;
    }
    private CompletableFuture<Void> noTarget(){
        reset(buyer);funds(buyer,false,30);WorldBorder border=overworld.getWorldBorder();double old=border.getSize();Location center=border.getCenter();border.setCenter(0,0);border.setSize(128);
        return opened(buyer,()->{check(recipe(buyer,Destination.STYX).getMaxUses()==0&&maps.target(recipe(buyer,Destination.STYX).getResult())==null,"no target displays an unavailable native row");check(feathers(buyer,false)==30&&owned(buyer).isEmpty(),"no eligible target takes no payment");buyer.closeInventory();border.setCenter(center);border.setSize(old);seedFixedTargets();});
    }
    private CompletableFuture<Void> concurrentAndCancelled(){
        reset(buyer);CompletableFuture<Merchant> first=maps.openTrades(buyer),second=maps.openTrades(buyer);
        check(first==second,"simultaneous opens share one pending lookup");
        return first.thenAccept(merchant->{check(merchant!=null&&inventory(buyer).getMerchant()==merchant,"shared lookup opens only one native merchant");buyer.closeInventory();})
            .thenCompose(ignored->{Merchant previous=Bukkit.createMerchant("Previous merchant");buyer.openMerchant(previous,true);
                CompletableFuture<Merchant> pending=maps.openTrades(buyer);buyer.closeInventory();
                return pending.thenCompose(merchant->{check(merchant==null,"closing source inventory cancels pending native map lookup");
                    CompletableFuture<Void> waited=new CompletableFuture<>();Bukkit.getScheduler().runTaskLater(this,()->{
                        try{check(!(buyer.getOpenInventory().getTopInventory() instanceof MerchantInventory),"cancelled async lookup never reopens map GUI later");waited.complete(null);}catch(Throwable error){waited.completeExceptionally(error);}
                    },5);return waited;});
            });
    }
    private CompletableFuture<Void> alreadyOwned(){
        // The most recent normal purchase is Seraphon, still carried in storage.
        return opened(buyer,()->{check(recipe(buyer,Destination.SERAPHON).getMaxUses()==0,"already-owned target disables its native offer without another charge");buyer.closeInventory();
            ItemStack map=owned(buyer).getFirst();for(int slot=0;slot<36;slot++)if(map.equals(buyer.getInventory().getItem(slot))){buyer.getInventory().setItem(slot,null);break;}buyer.setItemOnCursor(map);
        }).thenCompose(ignored->opened(buyer,()->{check(recipe(buyer,Destination.SERAPHON).getMaxUses()==0,"map held on cursor also disables duplicate offer");buyer.closeInventory();}));
    }
    private CompletableFuture<Void> inventoryCapacity(){
        reset(buyer);for(int slot=0;slot<36;slot++)buyer.getInventory().setItem(slot,new ItemStack(Material.COBBLESTONE,64));
        return opened(buyer,()->{select(buyer,Destination.PHLEGETHON);inventory(buyer).setItem(0,SeraphFeathers.feather(garden,30));clickResult(buyer,ContainerInput.QUICK_MOVE);
            check(inventory(buyer).getItem(0)!=null&&inventory(buyer).getItem(0).getAmount()==30&&owned(buyer).isEmpty()&&empty(buyer.getItemOnCursor()),"full inventory refuses native shift-click without consuming feathers");
            buyer.getInventory().setItem(5,null);buyer.closeInventory();check(feathers(buyer,false)==30&&owned(buyer).isEmpty(),"closing full-inventory exchange returns all unspent native input");
        });
    }
    private static final class CancelOne implements Listener {
        private final UUID player;private int remaining=1;
        CancelOne(Player player){this.player=player.getUniqueId();}
        @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=false)
        public void purchase(PlayerPurchaseEvent event){if(remaining>0&&event.getPlayer().getUniqueId().equals(player)){remaining--;event.setCancelled(true);}}
    }
    private CompletableFuture<Void> ticks(int amount){CompletableFuture<Void> done=new CompletableFuture<>();Bukkit.getScheduler().runTaskLater(this,()->done.complete(null),amount);return done;}
    private CompletableFuture<Void> lateCancelAndRetry(){
        Player cancelled=actor("MapCancelled",garden.world());reset(cancelled);
        final ItemStack[] firstPreview=new ItemStack[1];
        return opened(cancelled,()->{
            firstPreview[0]=recipe(cancelled,Destination.BANSHEE).getResult();select(cancelled,Destination.BANSHEE);inventory(cancelled).setItem(0,SeraphFeathers.feather(garden,30));
            CancelOne listener=new CancelOne(cancelled);Bukkit.getPluginManager().registerEvents(listener,this);
            try{clickResult(cancelled,ContainerInput.PICKUP);}finally{HandlerList.unregisterAll(listener);}
            check(empty(cancelled.getItemOnCursor())&&inventory(cancelled).getItem(0).getAmount()==30,"late MONITOR cancellation leaves native result unbought and input unchanged");cancelled.closeInventory();
        }).thenCompose(ignored->ticks(2)).thenCompose(ignored->{
            MapTarget target=maps.target(firstPreview[0]);maps.teleport(new PlayerTeleportEvent(cancelled,cancelled.getLocation(),new Location(overworld,target.x(),100,target.z()),PlayerTeleportEvent.TeleportCause.PLUGIN));
            return opened(cancelled,()->{check(target.equals(maps.target(recipe(cancelled,Destination.BANSHEE).getResult()))&&recipe(cancelled,Destination.BANSHEE).getMaxUses()==1,"cancelled preview stays unpurchased and arriving never advances it");
                check(feathers(cancelled,false)==30,"cancelled native purchase returns all thirty feathers on close");cancelled.closeInventory();});
        }).thenCompose(ignored->{
            Player retry=actor("MapRetryBuyer",garden.world());reset(retry);final MapTarget[] paidTarget=new MapTarget[1];
            return opened(retry,()->{
                paidTarget[0]=maps.target(recipe(retry,Destination.BANSHEE).getResult());select(retry,Destination.BANSHEE);inventory(retry).setItem(0,SeraphFeathers.feather(garden,60));
                CancelOne listener=new CancelOne(retry);Bukkit.getPluginManager().registerEvents(listener,this);
                try{
                    clickResult(retry,ContainerInput.PICKUP);check(empty(retry.getItemOnCursor())&&inventory(retry).getItem(0).getAmount()==60,"first same-tick attempt is cancelled without payment");
                    clickResult(retry,ContainerInput.PICKUP);check(paidTarget[0].equals(maps.target(retry.getItemOnCursor()))&&inventory(retry).getItem(0).getAmount()==30,"same-tick retry really pays once and receives its map");
                    ItemStack bought=retry.getItemOnCursor().clone();retry.setItemOnCursor(null);retry.getInventory().addItem(bought);
                }finally{HandlerList.unregisterAll(listener);}
                retry.closeInventory();check(feathers(retry,false)==30&&owned(retry).size()==1,"same-tick cancelled then paid retry produces one map for thirty feathers");
            }).thenCompose(done->ticks(2)).thenCompose(done->{
                MapTarget target=paidTarget[0];maps.teleport(new PlayerTeleportEvent(retry,retry.getLocation(),new Location(overworld,target.x(),100,target.z()),PlayerTeleportEvent.TeleportCause.PLUGIN));
                int size=garden.overworldSanctuaries().placement(overworld).spacing*16;
                cachedCells.put(OverworldSanctuaries.key(5,2),Optional.of(new OverworldSanctuaries.Site(OverworldSanctuaries.Kind.BANSHEE,5*size+size/2,2*size+size/2,777L)));
                reset(retry);return opened(retry,()->{MapTarget next=maps.target(recipe(retry,Destination.BANSHEE).getResult());
                    check(next!=null&&!next.equals(target),"deferred rollback cannot erase a paid retry: visiting its map advances to next target");retry.closeInventory();});
            });
        });
    }
    private CompletableFuture<Void> visitedEverMainBoss(){
        try{
            MapTarget previous=acquired.get(Destination.JUDGE);
            ItemStack original=ItemStack.deserializeBytes(savedItems.get(Destination.JUDGE));
            maps.teleport(new PlayerTeleportEvent(buyer,buyer.getLocation(),new Location(garden.world(),previous.x(),100,previous.z()),PlayerTeleportEvent.TeleportCause.PLUGIN));
            int size=garden.bossTemples().spacingChunks()*16;
            var next=new WorldBossTempleLayout.Site(6*size+size/2,3*size+size/2);
            judgeCells.put(SkyPlacementHistory.key(6,3),Optional.of(next));
            fixedEver.put(Destination.JUDGE,new MapTarget(garden.world().getUID(),Destination.JUDGE,next.x(),next.z()));
            return nativePurchase(buyer,Destination.JUDGE,()->{verifyMap(buyer,Destination.JUDGE);MapTarget selected=acquired.get(Destination.JUDGE);
                check(selected.x()!=previous.x()||selected.z()!=previous.z(),"reached Evergarden main boss is skipped for the buyer's next map");
                check(previous.equals(maps.target(original)),"visiting Evergarden main boss never retargets original map");})
                .thenCompose(ignored->{Player other=actor("OtherMapBuyer",garden.world());
                    return nativePurchase(other,Destination.JUDGE,()->{MapTarget target=maps.target(owned(other).getFirst());check(target.equals(previous),"another player can still buy the unvisited first main boss");});});
        }catch(Exception error){return CompletableFuture.failedFuture(error);}
    }
    private String lore(ItemStack item){
        var plain=net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText();
        return String.join("\n",item.getItemMeta().lore().stream().map(plain::serialize).toList());
    }
    /** A reached boss temple is offered again when it is the only one in range; an empty search says why. */
    private CompletableFuture<Void> revisitAndReason(){
        MapTarget seraphon=acquired.get(Destination.SERAPHON);
        reset(buyer);
        maps.teleport(new PlayerTeleportEvent(buyer,buyer.getLocation(),new Location(garden.world(),seraphon.x(),100,seraphon.z()),PlayerTeleportEvent.TeleportCause.PLUGIN));
        return ticks(2).thenCompose(ignored->opened(buyer,()->{
            MerchantRecipe offer=recipe(buyer,Destination.SERAPHON);
            check(seraphon.equals(maps.target(offer.getResult()))&&offer.getMaxUses()==1,"the only Seraphon in range is offered again after it was reached");
            check(lore(offer.getResult()).contains("เคยไปถึงที่นี่แล้ว"),"a revisit map says the player has been there");
            buyer.closeInventory();
        })).thenCompose(ignored->{
            long key=SkyPlacementHistory.key(5,3);List<BossStructureLayout.Site> saved=bossCells.get(key);bossCells.put(key,List.of());
            return opened(buyer,()->{
                MerchantRecipe offer=recipe(buyer,Destination.SERAPHON);
                check(offer.getMaxUses()==0&&maps.target(offer.getResult())==null,"no Seraphon in range leaves an unavailable row");
                check(lore(offer.getResult()).contains("ไม่มีวิหาร Seraphon แบบใหม่ในระยะ"),"the unavailable row says no new Seraphon temple is in range: "+lore(offer.getResult()));
                buyer.closeInventory();bossCells.put(key,saved);
            });
        });
    }
    @SuppressWarnings("unchecked") private CompletableFuture<Void> visitedOverworld(){
        try{
            MapTarget previous=acquired.get(Destination.ACHERON);
            ItemStack original=ItemStack.deserializeBytes(savedItems.get(Destination.ACHERON));
            maps.teleport(new PlayerTeleportEvent(buyer,buyer.getLocation(),new Location(overworld,previous.x(),100,previous.z()),PlayerTeleportEvent.TeleportCause.PLUGIN));
            var placement=garden.overworldSanctuaries().placement(overworld);int size=placement.spacing*16;
            Map<Long,Optional<OverworldSanctuaries.Site>> cache=(Map<Long,Optional<OverworldSanctuaries.Site>>)field(placement,"cache");
            var next=new OverworldSanctuaries.Site(OverworldSanctuaries.Kind.ACHERON,4*size+size/2,size+size/2,999L);
            cache.put(OverworldSanctuaries.key(4,1),Optional.of(next));fixed.put(Destination.ACHERON,next);
            return nativePurchase(buyer,Destination.ACHERON,()->{
                verifyMap(buyer,Destination.ACHERON);MapTarget selected=acquired.get(Destination.ACHERON);
                check(selected.x()!=previous.x()||selected.z()!=previous.z(),"reached Overworld destination is skipped for next map");
                check(previous.equals(maps.target(original)),"reaching destination never retargets the original map");
            });
        }catch(Exception error){return CompletableFuture.failedFuture(error);}
    }
    private void verifyMap(Player player,Destination destination){
        check(owned(player).size()==1,"exactly one bound map in inventory "+destination);ItemStack item=owned(player).getFirst();MapTarget target=maps.target(item);
        check(item.getType()==Material.FILLED_MAP&&target.destination()==destination,"native filled map has correct destination "+destination);
        check(target.world().equals(destination.overworld()?overworld.getUID():garden.world().getUID()),"correct target world "+destination);
        if(destination.overworld()){var expected=fixed.get(destination);check(target.x()==expected.x()&&target.z()==expected.z(),"async locator selects fixed correct sanctuary "+destination);}
        else check(target.equals(fixedEver.get(destination)),"async locator selects the exact requested Evergarden main boss "+destination);
        MapView view=((MapMeta)item.getItemMeta()).getMapView();
        check(view!=null&&view.getWorld()!=null&&view.getWorld().getUID().equals(target.world()),"MapView world matches bound target "+destination);
        check(view.getCenterX()==target.x()&&view.getCenterZ()==target.z()&&view.getScale()==MapView.Scale.FAR,"native map uses exact fixed center and FAR scale "+destination);
        check(view.isTrackingPosition()&&view.isUnlimitedTracking(),"native map tracks player at long range "+destination);
        check(view.getRenderers().stream().anyMatch(renderer->renderer.getClass().getEnclosingClass()!=SanctuaryMaps.class),"vanilla terrain renderer retained "+destination);
        var overlays=view.getRenderers().stream().filter(renderer->renderer.getClass().getEnclosingClass()==SanctuaryMaps.class).toList();
        check(!overlays.isEmpty(),"target overlay renderer attached "+destination);
        MapCursorCollection cursors=new MapCursorCollection();
        MapCanvas canvas=(MapCanvas)Proxy.newProxyInstance(getClassLoader(),new Class<?>[]{MapCanvas.class},(proxy,method,args)->{
            return switch(method.getName()){case "getCursors"->cursors;case "getMapView"->view;case "getPixel","getBasePixel"->(byte)0;default->null;};
        });
        for(var overlay:overlays)overlay.render(view,canvas,player);
        boolean redX=false;for(int index=0;index<cursors.size();index++)if(cursors.getCursor(index).getType()==MapCursor.Type.RED_X)redX=true;
        check(redX,"native red X target marker rendered "+destination);
        ItemStack restored=ItemStack.deserializeBytes(item.serializeAsBytes());
        check(target.equals(maps.target(restored))&&((MapMeta)restored.getItemMeta()).getMapView().getId()==view.getId(),"serialized map preserves target and map ID "+destination);
        for(var overlay:overlays)view.removeRenderer(overlay);maps.restore(view);
        check(view.getRenderers().stream().anyMatch(renderer->renderer.getClass().getEnclosingClass()==SanctuaryMaps.class),"saved target overlay restores without retargeting "+destination);
        check(target.equals(maps.target(item))&&view.getCenterX()==target.x()&&view.getCenterZ()==target.z(),"restore keeps fixed destination "+destination);
        acquired.put(destination,target);savedItems.put(destination,item.serializeAsBytes());
    }
    private void saveReloadState(){
        try{
            Path directory=Path.of("sanctuary-map-state");Files.createDirectories(directory);YamlConfiguration expected=new YamlConfiguration();
            for(var destination:Destination.values()){
                byte[] bytes=savedItems.get(destination);Files.write(directory.resolve(destination.name()+".item"),bytes);
                MapTarget target=acquired.get(destination);ItemStack item=ItemStack.deserializeBytes(bytes);
                String path=destination.name();expected.set(path+".world",target.world().toString());expected.set(path+".x",target.x());expected.set(path+".z",target.z());
                expected.set(path+".map-id",((MapMeta)item.getItemMeta()).getMapView().getId());
                byte[] preview=previewItems.get(destination);Files.write(directory.resolve("PREVIEW_"+destination.name()+".item"),preview);
                MapTarget previewTarget=previewTargets.get(destination);ItemStack previewItem=ItemStack.deserializeBytes(preview);String previewPath="PREVIEW_"+destination.name();
                expected.set(previewPath+".world",previewTarget.world().toString());expected.set(previewPath+".x",previewTarget.x());expected.set(previewPath+".z",previewTarget.z());
                expected.set(previewPath+".map-id",((MapMeta)previewItem.getItemMeta()).getMapView().getId());
            }
            expected.save(directory.resolve("expected.yml").toFile());check(previewItems.size()==9,"nine purchased and nine unbought preview maps saved for actual restart");
        }catch(Exception error){throw new CompletionException(error);}
    }
    private void cartography(){
        ItemStack map=ItemStack.deserializeBytes(savedItems.get(Destination.ACHERON));
        var view=buyer.openCartographyTable(null,true);check(view!=null&&view.getTopInventory() instanceof CartographyInventory,"real cartography view opens for bound map checks");
        var inventory=(CartographyInventory)view.getTopInventory();
        try{
            inventory.setItem(0,map.clone());
            for(var ingredient:new Material[]{Material.PAPER,Material.GLASS_PANE}){
                inventory.setItem(1,new ItemStack(ingredient));
                var event=new com.destroystokyo.paper.event.inventory.PrepareResultEvent(view,map.clone());maps.cartography(event);
                check(event.getResult()==null,"bound map rejects cartography "+ingredient+" transformation");
            }
            inventory.setItem(1,new ItemStack(Material.MAP));
            var copy=new com.destroystokyo.paper.event.inventory.PrepareResultEvent(view,map.clone());maps.cartography(copy);
            check(copy.getResult()!=null,"bound map permits ordinary same-ID copying");
            inventory.setItem(0,new ItemStack(Material.FILLED_MAP));inventory.setItem(1,new ItemStack(Material.PAPER));
            var ordinary=new com.destroystokyo.paper.event.inventory.PrepareResultEvent(view,new ItemStack(Material.FILLED_MAP));maps.cartography(ordinary);
            check(ordinary.getResult()!=null,"ordinary maps keep vanilla scaling behavior");
        }finally{inventory.clear();buyer.closeInventory();}
    }
    private void reload()throws Exception {
        garden=(VoidscapePlugin)Bukkit.getPluginManager().getPlugin("Evergarden");check(garden!=null&&garden.isEnabled(),"Evergarden enabled after actual restart");
        maps=garden.seraphTrader().shop().maps();check(maps!=null,"map shop reconstructed after actual restart");
        buyer=actor("MapReloadBuyer",garden.world());
        Path directory=Path.of("sanctuary-map-state");YamlConfiguration expected=new YamlConfiguration();expected.load(directory.resolve("expected.yml").toFile());
        for(boolean preview:new boolean[]{false,true})for(var destination:Destination.values()){
            String path=(preview?"PREVIEW_":"")+destination.name();ItemStack item=ItemStack.deserializeBytes(Files.readAllBytes(directory.resolve(path+".item")));
            MapTarget target=new MapTarget(UUID.fromString(expected.getString(path+".world")),destination,expected.getInt(path+".x"),expected.getInt(path+".z"));
            check(target.equals(maps.target(item)),"actual restart retains fixed registry destination "+destination);
            check(item.getType()==Material.FILLED_MAP&&item.getItemMeta() instanceof MapMeta,"actual restart retains native filled map "+destination);
            MapView view=((MapMeta)item.getItemMeta()).getMapView();
            check(view!=null&&view.getId()==expected.getInt(path+".map-id")&&Bukkit.getMap(view.getId())!=null,"actual restart loads persisted vanilla map ID "+destination);
            check(view.getWorld()!=null&&view.getWorld().getUID().equals(target.world()),"actual restart map belongs to correct world "+destination);
            check(view.getCenterX()==target.x()&&view.getCenterZ()==target.z()&&view.getScale()==MapView.Scale.FAR,"actual restart keeps native map center and scale "+destination);
            check(view.isTrackingPosition()&&view.isUnlimitedTracking(),"actual restart retains tracking "+destination);
            check(view.getRenderers().stream().anyMatch(renderer->renderer.getClass().getEnclosingClass()!=SanctuaryMaps.class),"actual restart retains terrain renderer "+destination);
            var overlays=view.getRenderers().stream().filter(renderer->renderer.getClass().getEnclosingClass()==SanctuaryMaps.class).toList();
            check(overlays.size()==1,"actual restart automatically restores one target overlay "+destination);
            MapCursorCollection cursors=new MapCursorCollection();
            MapCanvas canvas=(MapCanvas)Proxy.newProxyInstance(getClassLoader(),new Class<?>[]{MapCanvas.class},(proxy,method,args)->switch(method.getName()){
                case "getCursors"->cursors;case "getMapView"->view;case "getPixel","getBasePixel"->(byte)0;default->null;});
            overlays.getFirst().render(view,canvas,buyer);boolean redX=false;
            for(int index=0;index<cursors.size();index++)if(cursors.getCursor(index).getType()==MapCursor.Type.RED_X)redX=true;
            check(redX,"actual restart renders native red X without repurchase "+destination);
        }
    }
    private void finish(Throwable error){
        if(finished)return;finished=true;if(error!=null)getLogger().log(java.util.logging.Level.SEVERE,"SANCTUARY MAP CHECK FAILED",error);
        String report=(error==null?"PASS ":"FAIL ")+passed+" checks; actual Paper Seraph native trades "+(reloadPhase?"after process restart":"transactions")+"; "+(error==null?(reloadPhase?"nine purchased and nine unbought preview native map IDs/worlds/centers, automatic overlays and red X markers":"all nine native merchant offers and packet purchases, exact currency, poor-player previews, async cancellation, native fixed maps and personal visited exclusions"):error);
        try{Files.writeString(Path.of(reloadPhase?"sanctuary-map-reload-result.txt":"sanctuary-map-result.txt"),report);}catch(Exception failure){getLogger().log(java.util.logging.Level.SEVERE,"Could not save report",failure);}
        for(var actor:actors){MinecraftServer.getServer().getPlayerList().getPlayers().remove(actor);MinecraftServer.getServer().getPlayerList().getPlayersByUUID().remove(actor.getUUID());actor.discard();}
        getLogger().info(report);Bukkit.getScheduler().runTaskLater(this,Bukkit::shutdown,1);
    }
}
