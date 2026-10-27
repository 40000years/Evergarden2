package com.example.voidscape.trade;

import com.example.voidscape.VoidscapePlugin;
import com.example.voidscape.world.BossStructureLayout;
import com.example.voidscape.world.OverworldSanctuaries;
import com.example.voidscape.world.WorldBossTemple;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.server.MapInitializeEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.*;
import org.bukkit.persistence.PersistentDataType;
import io.papermc.paper.event.player.PlayerPurchaseEvent;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/** Personal, fixed-destination explorer maps sold by the Seraph. Searches never generate chunks. */
public final class SanctuaryMaps implements Listener, AutoCloseable {
    public enum Destination {
        ACHERON("Acheron", OverworldSanctuaries.Kind.ACHERON, null),
        PHLEGETHON("Phlegethon", OverworldSanctuaries.Kind.PHLEGETHON, null),
        COCYTUS("Cocytus", OverworldSanctuaries.Kind.COCYTUS, null),
        BANSHEE("Banshee", OverworldSanctuaries.Kind.BANSHEE, null),
        MALEFEASANCE("Malefeasance", OverworldSanctuaries.Kind.MALEFEASANCE, null),
        STYX("Styx", OverworldSanctuaries.Kind.STYX, null),
        JUDGE("Bodiless Judge", null, null),
        ARKHAIA("Arkhaia", null, BossStructureLayout.Kind.ARKHAIA),
        SERAPHON("Seraphon", null, BossStructureLayout.Kind.SERAPHON);
        public final String title;
        private final OverworldSanctuaries.Kind sanctuary;
        private final BossStructureLayout.Kind boss;
        Destination(String title, OverworldSanctuaries.Kind sanctuary, BossStructureLayout.Kind boss) {
            this.title=title; this.sanctuary=sanctuary; this.boss=boss;
        }
        public boolean overworld() { return sanctuary!=null; }
    }
    public record MapTarget(UUID world, Destination destination, int x, int z) {}
    private record StoredMap(MapTarget target, UUID owner, boolean reached, boolean purchased) {}
    private record Request(InventoryView origin, CompletableFuture<Merchant> result, Map<Destination,String> reasons) {}
    private record PreparedTrade(Destination destination, MapTarget target, int mapId, int cost) {}
    private static final class PendingPurchase {
        final StoredMap before;
        int remaining;
        boolean confirmed;
        PendingPurchase(StoredMap before){this.before=before;}
    }
    private static final class TradeSession {
        final Merchant merchant;
        final List<PreparedTrade> offers;
        Set<MapTarget> ownedBefore=Set.of();
        int clickTick=-1;
        TradeSession(Merchant merchant,List<PreparedTrade> offers){this.merchant=merchant;this.offers=List.copyOf(offers);}
    }
    private final VoidscapePlugin plugin;
    private final Path file;
    private final Map<Integer,StoredMap> maps=new LinkedHashMap<>();
    private final Map<UUID,Request> pending=new ConcurrentHashMap<>();
    private final Map<UUID,TradeSession> sessions=new HashMap<>();
    private final Map<PlayerPurchaseEvent,Integer> transitions=new IdentityHashMap<>();
    private final Map<Integer,PendingPurchase> purchases=new HashMap<>();
    private boolean healthy=true;
    private volatile boolean closed;

    public SanctuaryMaps(VoidscapePlugin plugin) {
        this.plugin=plugin;
        file=plugin.getDataFolder().toPath().resolve("seraph-sanctuary-maps.yml");
        load();
        Bukkit.getPluginManager().registerEvents(this,plugin);
        for(int id:maps.keySet()) {
            MapView view=Bukkit.getMap(id);
            if(view!=null)restore(view);
        }
    }
    public int price(Destination destination) {
        return plugin.integer("seraph-trader.maps."+(destination.overworld()?"overworld-feathers":"evergarden-golden-feathers"),
            destination.overworld()?30:10,1,64);
    }
    private static Component text(String value, NamedTextColor color) {
        return Component.text(value,color).decoration(TextDecoration.ITALIC,false);
    }
    /** The same native villager trading screen as the Seraph's other goods, on both clients. */
    public void open(Player player) { openTrades(player); }

    /** Resolve personalized real map results before opening; browsing never requires or spends feathers. */
    public CompletableFuture<Merchant> openTrades(Player player) {
        if(!Bukkit.isPrimaryThread())throw new IllegalStateException("Open map trades on the server thread");
        if(player==null||!player.isOnline()||player.isDead()||closed)return CompletableFuture.completedFuture(null);
        if(!healthy){plugin.message(player,"ร้านแผนที่ยังไม่พร้อม กรุณาแจ้งแอดมิน");return CompletableFuture.completedFuture(null);}
        UUID owner=player.getUniqueId();Request existing=pending.get(owner);
        if(existing!=null)return existing.result;
        TradeSession current=sessions.get(owner);
        if(current!=null&&player.getOpenInventory().getTopInventory() instanceof MerchantInventory inventory
                &&inventory.getMerchant()==current.merchant)return CompletableFuture.completedFuture(current.merchant);
        Request request=new Request(player.getOpenInventory(),new CompletableFuture<>(),new ConcurrentHashMap<>());
        pending.put(owner,request);
        Set<String> visited=new HashSet<>();
        for(StoredMap stored:maps.values())if(stored.owner.equals(owner)&&stored.purchased&&stored.reached)
            visited.add(targetKey(stored.target));
        EnumMap<Destination,Supplier<MapTarget>> searches=new EnumMap<>(Destination.class);
        List<World> overworlds=plugin.overworldSanctuaries().mapWorlds();
        World normal=overworlds.contains(player.getWorld())?player.getWorld():overworlds.stream().findFirst().orElse(null);
        for(Destination destination:Destination.values()) {
            World world=destination.overworld()?normal:plugin.world();
            if(world==null){request.reasons.put(destination,"ไม่พบโลกของสถานที่นี้");searches.put(destination,()->null);continue;}
            if(!destination.overworld()&&!bossEnabled(destination)){request.reasons.put(destination,"สถานที่นี้ปิดใช้งานอยู่");searches.put(destination,()->null);continue;}
            MapTarget cached=null;
            // A remembered target is reused only while it is still new to this player; boss revisits come from the search.
            for(StoredMap stored:maps.values())if(stored.owner.equals(owner)&&stored.target.destination==destination
                    &&stored.target.world.equals(world.getUID())&&validTarget(owner,stored.target)&&!reached(owner,stored.target)){cached=stored.target;break;}
            if(cached!=null){MapTarget fixed=cached;searches.put(destination,()->fixed);continue;}
            Location origin=player.getWorld()==world?player.getLocation():world.getSpawnLocation();
            if(destination.overworld()) {
                UUID worldId=world.getUID();
                Supplier<OverworldSanctuaries.Site> lookup=plugin.overworldSanctuaries().mapSearch(world,destination.sanctuary,
                    origin.getBlockX(),origin.getBlockZ(),site->!visited.contains(targetKey(new MapTarget(worldId,destination,site.x(),site.z()))),
                    ()->pending.get(owner)!=request);
                searches.put(destination,lookup==null?()->null:()->{var site=lookup.get();return site==null?null:new MapTarget(worldId,destination,site.x(),site.z());});
            } else searches.put(destination,bossSearch(world,destination,origin,owner,request,Set.copyOf(visited),player.getWorld()!=world));
        }
        plugin.message(player,"กำลังเตรียมแผนที่สำหรับหน้าแลกเปลี่ยน...");
        Bukkit.getScheduler().runTaskAsynchronously(plugin,()->{
            EnumMap<Destination,MapTarget> selected=new EnumMap<>(Destination.class);Throwable failure=null;
            try {
                for(var entry:searches.entrySet()) {
                    if(pending.get(owner)!=request)break;
                    MapTarget target=entry.getValue().get();if(target!=null)selected.put(entry.getKey(),target);
                    else request.reasons.putIfAbsent(entry.getKey(),"ยังไม่พบสถานที่ใหม่ที่เปิดใช้งานในระยะค้นหา");
                }
            } catch(RuntimeException|LinkageError error){failure=error;}
            Throwable error=failure;
            if(closed||!plugin.isEnabled()){request.result.complete(null);return;}
            Bukkit.getScheduler().runTask(plugin,()->finishOpen(player,request,selected,error));
        });
        return request.result;
    }
    private void finishOpen(Player player,Request request,Map<Destination,MapTarget> selected,Throwable error) {
        if(pending.get(player.getUniqueId())!=request){request.result.complete(null);return;}
        pending.remove(player.getUniqueId(),request);
        if(closed||!healthy||!player.isOnline()||player.isDead()||player.getOpenInventory()!=request.origin){request.result.complete(null);return;}
        if(error!=null){plugin.getLogger().warning("Map shop lookup failed: "+error);plugin.message(player,"เตรียมแผนที่ไม่สำเร็จ กรุณาลองอีกครั้ง");request.result.complete(null);return;}
        try {
            Merchant merchant=Bukkit.createMerchant(text("✦ Seraph · ร้านแผนที่",NamedTextColor.GOLD));
            List<MerchantRecipe> recipes=new ArrayList<>();List<PreparedTrade> offers=new ArrayList<>();
            Set<MapTarget> owned=ownedTargets(player);
            for(Destination destination:Destination.values()) {
                MapTarget target=selected.get(destination);
                if(target!=null&&!validTarget(player.getUniqueId(),target))target=null;
                ItemStack result;int id=-1;
                if(target!=null) {
                    id=prepareMap(player.getUniqueId(),target);
                    result=mapItem(id,target,reached(player.getUniqueId(),target));
                } else {
                    result=new ItemStack(Material.MAP);var meta=result.getItemMeta();
                    meta.displayName(text("แผนที่ · "+destination.title,NamedTextColor.GOLD));
                    meta.lore(List.of(text(request.reasons.getOrDefault(destination,"ยังไม่พบสถานที่ใหม่ที่เปิดใช้งานในระยะค้นหา"),NamedTextColor.GRAY)));
                    result.setItemMeta(meta);
                }
                int cost=price(destination);
                MerchantRecipe recipe=new MerchantRecipe(result,0,target!=null&&!owned.contains(target)?1:0,false,0,0,0,0,true);
                recipe.addIngredient(destination.overworld()?SeraphFeathers.feather(plugin,cost):SeraphFeathers.golden(plugin,cost));
                recipes.add(recipe);offers.add(new PreparedTrade(destination,target,id,cost));
            }
            // Map IDs/markers are durable before the native merchant can deliver any result.
            if(!save()){plugin.message(player,"บันทึกแผนที่ไม่สำเร็จ · ยังไม่ใช้ขนนก");request.result.complete(null);return;}
            merchant.setRecipes(recipes);
            player.openMerchant(merchant,true);
            if(!(player.getOpenInventory().getTopInventory() instanceof MerchantInventory inventory)||inventory.getMerchant()!=merchant){request.result.complete(null);return;}
            sessions.put(player.getUniqueId(),new TradeSession(merchant,offers));
            request.result.complete(merchant);
        } catch(RuntimeException|LinkageError failure) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE,"Cannot open map trades",failure);request.result.complete(null);
        }
    }
    private int prepareMap(UUID owner,MapTarget target) {
        for(var entry:maps.entrySet())if(entry.getValue().owner.equals(owner)&&entry.getValue().target.equals(target)) {
            MapView view=Bukkit.getMap(entry.getKey());
            if(view!=null){restore(view);return entry.getKey();}
        }
        World world=Objects.requireNonNull(Bukkit.getWorld(target.world));
        MapView view=Bukkit.createMap(world);
        maps.put(view.getId(),new StoredMap(target,owner,false,false));restore(view);return view.getId();
    }
    private ItemStack mapItem(int id,MapTarget target,boolean revisit) {
        World world=Objects.requireNonNull(Bukkit.getWorld(target.world));
        ItemStack item=new ItemStack(Material.FILLED_MAP);MapMeta meta=(MapMeta)item.getItemMeta();
        meta.setMapView(Objects.requireNonNull(Bukkit.getMap(id)));
        meta.displayName(text("แผนที่ · "+target.destination.title,NamedTextColor.GOLD));
        List<Component> lore=new ArrayList<>(List.of(text(target.destination.overworld()?"Sanctuary · Overworld":"บอสใหญ่ · Evergarden",NamedTextColor.AQUA),
            text("โลก: "+world.getName(),NamedTextColor.GRAY),text("เครื่องหมาย X คือจุดหมาย · ถือแผนที่เพื่อดูทิศทาง",NamedTextColor.GRAY),
            text("แผนที่นี้ผูกกับสถานที่เดิม",NamedTextColor.GRAY)));
        if(revisit)lore.add(text("คุณเคยไปถึงที่นี่แล้ว · ไม่มีที่ใหม่ในระยะ แต่บอสเกิดใหม่ได้",NamedTextColor.YELLOW));
        meta.lore(lore);
        meta.getPersistentDataContainer().set(plugin.key("sanctuary_map"),PersistentDataType.BYTE,(byte)1);
        item.setItemMeta(meta);return item;
    }
    private Set<MapTarget> ownedTargets(Player player) {
        Set<MapTarget> targets=new HashSet<>();
        for(ItemStack item:player.getInventory().getContents()){MapTarget target=target(item);if(target!=null)targets.add(target);}
        MapTarget cursor=target(player.getItemOnCursor());if(cursor!=null)targets.add(cursor);
        return Set.copyOf(targets);
    }
    /** Capture before Paper places a pickup result on the cursor or a number-key result in the hotbar. */
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void click(InventoryClickEvent event) {
        if(!(event.getWhoClicked() instanceof Player player)||event.getRawSlot()!=2)return;
        TradeSession session=sessions.get(player.getUniqueId());
        if(session==null||!(event.getInventory() instanceof MerchantInventory inventory)||inventory.getMerchant()!=session.merchant)return;
        session.ownedBefore=ownedTargets(player);session.clickTick=Bukkit.getCurrentTick();
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void purchase(PlayerPurchaseEvent event) {
        Player player=event.getPlayer();TradeSession session=sessions.get(player.getUniqueId());
        if(session==null||event.getMerchant()!=session.merchant)return;
        int index=session.merchant.getRecipes().indexOf(event.getTrade());
        if(index<0&&player.getOpenInventory().getTopInventory() instanceof MerchantInventory inventory)index=inventory.getSelectedRecipeIndex();
        if(index<0||index>=session.offers.size()){event.setCancelled(true);return;}
        PreparedTrade offer=session.offers.get(index);StoredMap stored=maps.get(offer.mapId);
        ItemStack expected=offer.destination.overworld()?SeraphFeathers.feather(plugin,offer.cost):SeraphFeathers.golden(plugin,offer.cost);
        List<ItemStack> ingredients=event.getTrade().getIngredients();
        ItemStack result=event.getTrade().getResult();
        int resultId=result.getItemMeta() instanceof MapMeta meta&&meta.hasMapView()&&meta.getMapView()!=null?meta.getMapView().getId():-1;
        if(closed||!healthy||!player.isOnline()||player.isDead()||session.clickTick!=Bukkit.getCurrentTick()
                ||offer.target==null||stored==null||!stored.owner.equals(player.getUniqueId())
                ||result.getAmount()!=1||resultId!=offer.mapId||!offer.target.equals(target(result))||session.ownedBefore.contains(offer.target)
                ||ingredients.size()!=1||!ingredients.getFirst().equals(expected)||event.getTrade().getSpecialPrice()!=0
                ||!event.getTrade().getAdjustedIngredient1().equals(expected)
                ||!validTarget(player.getUniqueId(),offer.target)) {
            event.setCancelled(true);return;
        }
        event.setIncreaseTradeUses(true);event.setRewardExp(false);
        if(!stored.purchased) {
            maps.put(offer.mapId,new StoredMap(stored.target,stored.owner,stored.reached,true));
            if(!save()){maps.put(offer.mapId,stored);event.setCancelled(true);return;}
        }
        // Several native attempts can occur in one tick. A cancelled attempt must not undo a later paid retry.
        purchases.computeIfAbsent(offer.mapId,ignored->new PendingPurchase(stored)).remaining++;
        transitions.put(event,offer.mapId);
        plugin.getLogger().info("[Seraph-Map] native trade player="+player.getUniqueId()+" destination="+offer.destination+" map="+offer.mapId+" price="+offer.cost);
    }
    @EventHandler(priority=EventPriority.MONITOR)
    public void purchased(PlayerPurchaseEvent event) {
        Integer mapId=transitions.remove(event);
        if(mapId==null)return;
        Bukkit.getScheduler().runTask(plugin,()->{
            PendingPurchase purchase=purchases.get(mapId);
            if(purchase!=null) {
                purchase.confirmed|=!event.isCancelled();
                if(--purchase.remaining==0) {
                    purchases.remove(mapId);
                    if(!purchase.confirmed&&!purchase.before.purchased){maps.put(mapId,purchase.before);save();}
                }
            }
            if(event.isCancelled())return;
            Player player=event.getPlayer();
            if(player.isOnline()){player.saveData();player.playSound(player.getLocation(),Sound.BLOCK_AMETHYST_BLOCK_CHIME,1,1.25f);}
        });
    }
    @EventHandler public void closeInventory(InventoryCloseEvent event) {
        if(!(event.getPlayer() instanceof Player player))return;
        Request request=pending.get(player.getUniqueId());
        if(request!=null&&event.getView()==request.origin)cancel(player.getUniqueId());
        TradeSession session=sessions.get(player.getUniqueId());
        if(session!=null&&event.getInventory() instanceof MerchantInventory inventory&&inventory.getMerchant()==session.merchant)
            sessions.remove(player.getUniqueId(),session);
    }
    private void cancel(UUID owner) {
        Request request=pending.remove(owner);if(request!=null)request.result.complete(null);
    }
    private boolean bossEnabled(Destination destination) {
        if(destination==Destination.JUDGE)
            return plugin.bossTemples()!=null&&plugin.getConfig().getBoolean("world-boss.enabled",true)
                &&plugin.getConfig().getBoolean("structures.world-boss-temple.enabled",true);
        if(destination==Destination.SERAPHON&&!plugin.getConfig().getBoolean("seraphon.enabled",true))return false;
        return destination.boss!=null&&plugin.bossStructures()!=null&&plugin.bossStructures().enabled(destination.boss)
            &&plugin.getConfig().getBoolean("structures.boss-structures."+destination.boss.id+".enabled",true);
    }
    /** Capture world/configuration on the server thread; inspect only deterministic layout cells off-thread. */
    private Supplier<MapTarget> bossSearch(World world,Destination destination,Location origin,UUID owner,Request request,Set<String> visited,boolean fromSpawn) {
        var ancient=plugin.bossTemples();var structures=plugin.bossStructures();UUID worldId=world.getUID();
        int radius=plugin.integer("seraph-trader.maps.evergarden-search-radius",12000,512,30000);
        int size=(destination==Destination.JUDGE?ancient.spacingChunks():structures.spacingChunks())*16;
        int x=origin.getBlockX(),z=origin.getBlockZ(),gx=Math.floorDiv(x,size),gz=Math.floorDiv(z,size);
        int cells=(radius+size-1)/size,footprint=destination==Destination.JUDGE?WorldBossTemple.RADIUS:destination.boss.radius;
        WorldBorder border=world.getWorldBorder();
        double half=border.getSize()/2,centerX=border.getCenter().getX(),centerZ=border.getCenter().getZ();
        return ()->{
            MapTarget best=null,revisit=null;double distance=Double.POSITIVE_INFINITY,revisitDistance=Double.POSITIVE_INFINITY;
            int outside=0;
            for(int dx=-cells;dx<=cells;dx++)for(int dz=-cells;dz<=cells;dz++) {
                if(pending.get(owner)!=request)return null;
                List<MapTarget> candidates=new ArrayList<>(1);
                if(destination==Destination.JUDGE) {
                    var site=ancient.cell(gx+dx,gz+dz);
                    if(site!=null)candidates.add(new MapTarget(worldId,destination,site.x(),site.z()));
                } else for(var site:structures.cell(gx+dx,gz+dz))
                    if(site.kind()==destination.boss&&!site.legacyDesign())candidates.add(new MapTarget(worldId,destination,site.x(),site.z()));
                for(MapTarget target:candidates) {
                    double d=Math.hypot((double)target.x-x,(double)target.z-z);
                    if(d>radius)continue;
                    if(Math.abs(target.x-centerX)+footprint>=half||Math.abs(target.z-centerZ)+footprint>=half){outside++;continue;}
                    if(visited.contains(targetKey(target))){if(d<revisitDistance){revisit=target;revisitDistance=d;}continue;}
                    if(d<distance){best=target;distance=d;}
                }
            }
            if(best!=null)return best;
            // Bosses respawn, so a temple already reached beats offering nothing.
            if(revisit!=null)return revisit;
            String from=fromSpawn?" (นับจากจุดเกิดของโลก Evergarden)":"";
            request.reasons.put(destination,outside>0?"วิหาร "+destination.title+" ในระยะอยู่นอกขอบโลก"+from
                :"ไม่มีวิหาร "+destination.title+" แบบใหม่ในระยะ "+String.format(Locale.US,"%,d",radius)+" บล็อก"+from);
            return null;
        };
    }
    private boolean validTarget(UUID owner,MapTarget target) {
        if(!healthy)return false;
        Destination destination=target.destination;
        World world=Bukkit.getWorld(target.world);
        if(world==null||!world.getWorldBorder().isInside(new Location(world,target.x,world.getMinHeight(),target.z)))
            return false;
        if(destination.overworld()) {
            if(!plugin.overworldSanctuaries().mapTargetInsideBorder(world,destination.sanctuary,target.x,target.z))
                return false;
        } else {
            if(world!=plugin.world()||!bossEnabled(destination))return false;
            WorldBorder border=world.getWorldBorder();
            int footprint=destination==Destination.JUDGE?WorldBossTemple.RADIUS:destination.boss.radius;
            if(Math.abs(target.x-border.getCenter().getX())+footprint>=border.getSize()/2
                    ||Math.abs(target.z-border.getCenter().getZ())+footprint>=border.getSize()/2)return false;
            if(destination==Destination.JUDGE) {
                var site=plugin.bossTemples().at(target.x,target.z,0);
                if(site==null||site.x()!=target.x||site.z()!=target.z)return false;
            } else {
                var site=plugin.bossStructures().at(target.x,target.z,0);
                if(site==null||site.kind()!=destination.boss||site.legacyDesign()||site.x()!=target.x||site.z()!=target.z)return false;
            }
        }
        return !destination.overworld()||!reached(owner,target);
    }
    private boolean reached(UUID owner,MapTarget target) {
        for(StoredMap stored:maps.values())if(stored.owner.equals(owner)&&stored.reached&&stored.target.equals(target))return true;
        return false;
    }
    public MapTarget target(ItemStack item) {
        if(item==null||item.getType()!=Material.FILLED_MAP||!(item.getItemMeta() instanceof MapMeta meta)
            ||!meta.getPersistentDataContainer().has(plugin.key("sanctuary_map"),PersistentDataType.BYTE)||!meta.hasMapView())return null;
        MapView view=meta.getMapView();StoredMap stored=view==null?null:maps.get(view.getId());
        return stored==null?null:stored.target;
    }
    private static String targetKey(MapTarget target) {return target.world+":"+target.destination+":"+target.x+":"+target.z;}
    public void restore(MapView view) {
        StoredMap stored=maps.get(view.getId());
        if(stored==null||view.getWorld()==null||!view.getWorld().getUID().equals(stored.target.world))return;
        view.setCenterX(stored.target.x);view.setCenterZ(stored.target.z);view.setScale(MapView.Scale.FAR);
        view.setTrackingPosition(true);view.setUnlimitedTracking(true);
        for(MapRenderer renderer:List.copyOf(view.getRenderers()))if(renderer instanceof TargetRenderer)view.removeRenderer(renderer);
        view.addRenderer(new TargetRenderer(stored.target.destination.title));
    }
    private static final class TargetRenderer extends MapRenderer {
        private final String title;
        TargetRenderer(String title){super(false);this.title=title;}
        @Override public void render(MapView view,MapCanvas canvas,Player player) {
            if(canvas.getCursors().size()==0) {
                MapCursor cursor=new MapCursor((byte)0,(byte)0,(byte)0,MapCursor.Type.RED_X,true);
                cursor.setCaption(title);canvas.getCursors().addCursor(cursor);
            }
        }
    }
    @EventHandler public void initialize(MapInitializeEvent event){restore(event.getMap());}
    @EventHandler public void loadWorld(WorldLoadEvent event){for(int id:maps.keySet()){MapView view=Bukkit.getMap(id);if(view!=null)restore(view);}}
    /** Scaling or locking mints a new vanilla map ID and would discard the saved destination marker. */
    @EventHandler public void cartography(com.destroystokyo.paper.event.inventory.PrepareResultEvent event) {
        if(!(event.getInventory() instanceof CartographyInventory inventory)||target(inventory.getItem(0))==null)return;
        ItemStack ingredient=inventory.getItem(1);
        if(ingredient!=null&&(ingredient.getType()==Material.PAPER||ingredient.getType()==Material.GLASS_PANE))event.setResult(null);
    }
    @EventHandler public void craft(org.bukkit.event.inventory.PrepareItemCraftEvent event) {
        boolean map=false,paper=false;
        for(ItemStack item:event.getInventory().getMatrix())if(item!=null) {
            map|=target(item)!=null;paper|=item.getType()==Material.PAPER;
        }
        if(map&&paper)event.getInventory().setResult(null);
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void move(PlayerMoveEvent event) {
        if((event.getFrom().getBlockX()>>4)==(event.getTo().getBlockX()>>4)
                &&(event.getFrom().getBlockZ()>>4)==(event.getTo().getBlockZ()>>4))return;
        reached(event.getPlayer(),event.getTo());
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void teleport(PlayerTeleportEvent event){reached(event.getPlayer(),event.getTo());}
    @EventHandler public void join(PlayerJoinEvent event){reached(event.getPlayer(),event.getPlayer().getLocation());}
    private void reached(Player player,Location at) {
        boolean changed=false;
        for(var entry:maps.entrySet()) {
            StoredMap stored=entry.getValue();MapTarget target=stored.target;
            if(stored.purchased&&!stored.reached&&stored.owner.equals(player.getUniqueId())
                    &&at.getWorld()!=null&&at.getWorld().getUID().equals(target.world)
                    &&Math.hypot(at.getX()-target.x,at.getZ()-target.z)<=144) {
                entry.setValue(new StoredMap(target,stored.owner,true,true));changed=true;
            }
        }
        if(changed)save();
    }
    @EventHandler public void quit(PlayerQuitEvent event) {cancel(event.getPlayer().getUniqueId());sessions.remove(event.getPlayer().getUniqueId());}
    private void load() {
        if(!Files.isRegularFile(file))return;
        try {
            YamlConfiguration data=new YamlConfiguration();data.load(file.toFile());
            if(data.getInt("version")!=1)throw new IOException("Unknown map registry version");
            var section=data.getConfigurationSection("maps");if(section==null)return;
            for(String key:section.getKeys(false)) {
                String base="maps."+key;
                MapTarget target=new MapTarget(UUID.fromString(data.getString(base+".world")),
                    Destination.valueOf(data.getString(base+".destination")),data.getInt(base+".x"),data.getInt(base+".z"));
                maps.put(Integer.parseInt(key),new StoredMap(target,UUID.fromString(data.getString(base+".owner")),data.getBoolean(base+".reached"),data.getBoolean(base+".purchased",true)));
            }
        } catch(Exception error) {healthy=false;maps.clear();plugin.getLogger().log(java.util.logging.Level.SEVERE,"Cannot read Seraph map registry; map exchanges paused",error);}
    }
    private boolean save() {
        if(!healthy)return false;
        YamlConfiguration data=new YamlConfiguration();data.set("version",1);
        for(var entry:maps.entrySet()) {
            String base="maps."+entry.getKey();StoredMap stored=entry.getValue();MapTarget target=stored.target;
            data.set(base+".world",target.world.toString());data.set(base+".destination",target.destination.name());
            data.set(base+".x",target.x);data.set(base+".z",target.z);data.set(base+".owner",stored.owner.toString());data.set(base+".reached",stored.reached);data.set(base+".purchased",stored.purchased);
        }
        try {
            Files.createDirectories(file.getParent());Path temporary=file.resolveSibling(file.getFileName()+".tmp");
            Files.writeString(temporary,data.saveToString());
            try{Files.move(temporary,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            catch(AtomicMoveNotSupportedException unsupported){Files.move(temporary,file,StandardCopyOption.REPLACE_EXISTING);}
            return true;
        } catch(IOException error) {healthy=false;plugin.getLogger().log(java.util.logging.Level.SEVERE,"Cannot save Seraph map registry; map exchanges paused",error);return false;}
    }
    @Override public void close() {
        closed=true;
        for(Request request:pending.values())request.result.complete(null);
        pending.clear();sessions.clear();transitions.clear();purchases.clear();HandlerList.unregisterAll(this);
    }
}
