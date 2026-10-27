package com.example.voidscape.pet;

import com.example.voidscape.VoidscapePlugin;
import com.example.voidscape.boss.JudgePackService;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import java.util.*;
import java.util.function.Predicate;

/**
 * Every pet body in the world, wild or companion. Each viewer receives packets only for the
 * pets it is shown: its own companion, plus the nearest few others within range. Hidden
 * entities are not tracked by that client at all, so crowds cost linear, not quadratic,
 * traffic. Also owns protection, click routing and cleanup for every part.
 */
public final class PetBodies implements Listener,AutoCloseable {
    interface Manager {
        /** A player right-clicked the body (Java receiver or Bedrock carrier). */
        void interact(PetBody body,Player player);
        /** The body's chunk or world is going away; forget it, the registry removes the parts. */
        void lost(PetBody body);
    }
    private static final class Viewer {String kind="none";final Set<PetBody> shown=new HashSet<>();}
    private final VoidscapePlugin plugin;
    private final PetAim aim;
    private final Map<PetBody,Manager> managers=new LinkedHashMap<>();
    private final Map<UUID,PetBody> parts=new HashMap<>();
    private final Map<UUID,Viewer> viewers=new HashMap<>();
    private final Map<UUID,Long> lastClick=new HashMap<>();
    /** Perched bodies by owner, and those whose client-side seat is due again next tick. */
    private final Map<UUID,PetBody> perched=new HashMap<>();
    private final Set<PetBody> reseat=new HashSet<>();
    private Predicate<UUID> hidesOthers=id->false;
    private final BukkitTask task;
    private long busy;
    /** The companion whose bite is being dealt right now; only that damage passes the protection. */
    private PetBody striking;

    PetBodies(VoidscapePlugin plugin){
        this.plugin=plugin;this.aim=new PetAim(plugin);
        Bukkit.getPluginManager().registerEvents(this,plugin);
        // Nonpersistent parts should never survive a previous unclean shutdown.
        for(World world:Bukkit.getWorlds())for(Chunk chunk:world.getLoadedChunks())cleanOrphans(Arrays.asList(chunk.getEntities()));
        task=Bukkit.getScheduler().runTaskTimer(plugin,this::refresh,10,10);
    }
    void hidesOthers(Predicate<UUID> rule){hidesOthers=rule;}
    void add(PetBody body,Manager manager){
        managers.put(body,manager);parts.put(body.shell.getUniqueId(),body);
        if(body.carrier!=null)parts.put(body.carrier.getUniqueId(),body);
        if(body.nametag!=null)parts.put(body.nametag.getUniqueId(),body);
        if(body.perched){
            for(ItemDisplay part:body.rig.displays())parts.put(part.getUniqueId(),body);
            if(body.owner!=null)perched.put(body.owner,body);
        }
    }
    void transfer(PetBody body,Manager manager){if(managers.containsKey(body))managers.put(body,manager);}
    void remove(PetBody body){
        managers.remove(body);parts.remove(body.shell.getUniqueId());
        if(body.carrier!=null)parts.remove(body.carrier.getUniqueId());
        if(body.nametag!=null)parts.remove(body.nametag.getUniqueId());
        if(body.perched){
            for(ItemDisplay part:body.rig.displays())parts.remove(part.getUniqueId());
            if(body.owner!=null)perched.remove(body.owner,body);
            reseat.remove(body);
        }
        for(Viewer viewer:viewers.values())viewer.shown.remove(body);
        body.remove();
    }
    PetBody find(Entity entity){
        if(entity==null)return null;
        PetBody body=parts.get(entity.getUniqueId());
        if(body!=null)return body;
        String receiver=entity.getPersistentDataContainer().get(plugin.key("wild_pet_visual"),PersistentDataType.STRING);
        return receiver==null?null:parts.get(PetStable.uuid(receiver));
    }
    /** Deals one assist-mode bite from {@code body}'s receiver. */
    void strike(PetBody body,Runnable hit){
        PetBody previous=striking;striking=body;
        try{hit.run();}finally{striking=previous;}
    }
    boolean striking(){return striking!=null;}
    int count(){return managers.size();}
    int pairs(){return viewers.values().stream().mapToInt(v->v.shown.size()).sum();}
    boolean shownTo(PetBody body,Player player){Viewer viewer=viewers.get(player.getUniqueId());return viewer!=null&&viewer.shown.contains(body);}
    /** Re-evaluates now, e.g. right after a companion appears beside its owner. */
    void refreshSoon(){Bukkit.getScheduler().runTask(plugin,this::refresh);}

    private int setting(String key,int fallback,int min,int max){return plugin.integer("pets.view."+key,fallback,min,max);}
    private String kind(Player player){
        if(JudgePackService.bedrock(player))return plugin.arkhaiaBedrock()!=null&&plugin.arkhaiaBedrock().ready()?"bedrock":"none";
        return plugin.judgePack()!=null&&plugin.judgePack().applied(player)?"java":"none";
    }
    long busy(){return busy;}
    void refresh(){
        long started=System.nanoTime();
        Set<UUID> online=new HashSet<>();
        for(Player player:Bukkit.getOnlinePlayers()){
            online.add(player.getUniqueId());
            Viewer viewer=viewers.computeIfAbsent(player.getUniqueId(),id->new Viewer());
            String kind=kind(player);
            if(!kind.equals(viewer.kind)){
                for(PetBody body:viewer.shown)hide(player,body);
                viewer.shown.clear();viewer.kind=kind;
            }
            Set<PetBody> wanted=kind.equals("none")?Set.of():select(player,viewer);
            for(Iterator<PetBody> it=viewer.shown.iterator();it.hasNext();){
                PetBody body=it.next();
                if(!wanted.contains(body)){hide(player,body);it.remove();}
            }
            for(PetBody body:wanted)if(viewer.shown.add(body))show(player,body,kind);
        }
        viewers.keySet().retainAll(online);
        busy+=System.nanoTime()-started;
    }
    private Set<PetBody> select(Player player,Viewer viewer){
        int max=setting("max-others",6,0,32);
        double near=setting("show-distance",56,16,128),far=Math.max(near+4,setting("hide-distance",64,20,160));
        Location at=player.getLocation();World world=player.getWorld();
        boolean onlyOwn=hidesOthers.test(player.getUniqueId());
        Set<PetBody> result=new HashSet<>();
        List<Map.Entry<PetBody,Double>> others=new ArrayList<>();
        for(PetBody body:managers.keySet()){
            if(!body.valid()||body.shell.getWorld()!=world)continue;
            double distance=body.location().distanceSquared(at);
            if(player.getUniqueId().equals(body.owner)){if(distance<=128*128)result.add(body);continue;}
            if(onlyOwn)continue;
            boolean shown=viewer.shown.contains(body);
            double limit=shown?far:near;
            // Pets already on screen rank as if 20% closer, so two pets near the cap do not swap back and forth.
            if(distance<=limit*limit)others.add(Map.entry(body,shown?distance*.64:distance));
        }
        others.sort(Map.Entry.comparingByValue());
        for(int i=0;i<Math.min(max,others.size());i++)result.add(others.get(i).getKey());
        return result;
    }
    private void show(Player player,PetBody body,String kind){
        if(kind.equals("java")){
            // The receiver carries the parts; a client must track it to place them. A perched
            // body's parts move on their own, and its receiver would only block the owner's clicks.
            if(!body.perched)player.showEntity(plugin,body.shell);
            for(ItemDisplay part:body.rig.displays())player.showEntity(plugin,part);
            if(body.nametag!=null&&!body.perched)player.showEntity(plugin,body.nametag);
            // Showing sent the parts already; the seat packet must follow them.
            if(body.perched)seat(body,player);
        }else if(kind.equals("bedrock")&&body.carrier!=null){
            player.showEntity(plugin,body.carrier);body.bedrockViewers.add(player.getUniqueId());
            // Geyser must create the entity before it accepts property updates.
            Bukkit.getScheduler().runTaskLater(plugin,()->{if(player.isOnline()&&body.bedrockViewers.contains(player.getUniqueId()))body.syncBedrock(player);},6);
        }
    }
    /** Seats a perched body on its owner for every Java viewer that is shown it. */
    void seat(PetBody body){
        for(var entry:viewers.entrySet()){
            Player viewer=entry.getValue().kind.equals("java")&&entry.getValue().shown.contains(body)?Bukkit.getPlayer(entry.getKey()):null;
            if(viewer!=null)seat(body,viewer);
        }
    }
    private void seat(PetBody body,Player viewer){
        Player owner=body.owner==null?null:Bukkit.getPlayer(body.owner);
        if(!body.perched||owner==null||!managers.containsKey(body)||owner.getWorld()!=viewer.getWorld()||viewer!=owner&&!viewer.canSee(owner))return;
        try{PetMount.seat(viewer,owner,body.rig.displays());}
        catch(RuntimeException|LinkageError error){plugin.getLogger().log(java.util.logging.Level.WARNING,"Cannot seat a perched pet for "+viewer.getName(),error);}
    }
    /**
     * A client that starts tracking a perched pet's owner or one of its parts may have dropped
     * the seat (an unknown vehicle is ignored); it is sent again on the next tick, after the
     * spawn packets this event precedes.
     */
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void track(io.papermc.paper.event.player.PlayerTrackEntityEvent event){
        Entity entity=event.getEntity();
        PetBody body=entity instanceof Player owner?perched.get(owner.getUniqueId()):parts.get(entity.getUniqueId());
        if(body==null||!body.perched)return;
        if(reseat.isEmpty())Bukkit.getScheduler().runTask(plugin,()->{
            List<PetBody> due=new ArrayList<>(reseat);reseat.clear();due.forEach(this::seat);
        });
        reseat.add(body);
    }
    private void hide(Player player,PetBody body){
        player.hideEntity(plugin,body.shell);
        for(ItemDisplay part:body.rig.displays())player.hideEntity(plugin,part);
        if(body.nametag!=null)player.hideEntity(plugin,body.nametag);
        if(body.carrier!=null)player.hideEntity(plugin,body.carrier);
        body.bedrockViewers.remove(player.getUniqueId());
    }

    // ---- protection and routing ----
    boolean part(Entity entity){
        if(entity==null)return false;
        var data=entity.getPersistentDataContainer();
        return data.has(plugin.key("pet_body"),PersistentDataType.BYTE)||data.has(plugin.key("wild_pet"),PersistentDataType.BYTE)
            ||data.has(plugin.key("wild_pet_visual"),PersistentDataType.STRING);
    }
    private void cleanOrphans(Collection<Entity> entities){
        for(Entity entity:entities)if(part(entity)&&!parts.containsKey(entity.getUniqueId())){
            String owner=entity.getPersistentDataContainer().get(plugin.key("wild_pet_visual"),PersistentDataType.STRING);
            PetBody body=owner==null?null:parts.get(PetStable.uuid(owner));
            if(body==null||(!body.rig.displays().contains(entity)&&body.nametag!=entity))entity.remove();
        }
    }
    @EventHandler public void loaded(EntitiesLoadEvent event){cleanOrphans(event.getEntities());}
    @EventHandler(ignoreCancelled=true) public void unload(ChunkUnloadEvent event){
        for(PetBody body:new ArrayList<>(managers.keySet())){
            Location at=body.location();
            if(at.getWorld()==event.getWorld()&&(at.getBlockX()>>4)==event.getChunk().getX()&&(at.getBlockZ()>>4)==event.getChunk().getZ())lose(body);
        }
    }
    @EventHandler public void unloadWorld(WorldUnloadEvent event){
        for(PetBody body:new ArrayList<>(managers.keySet()))if(body.shell.getWorld()==event.getWorld())lose(body);
    }
    private void lose(PetBody body){
        Manager manager=managers.get(body);
        try{if(manager!=null)manager.lost(body);}
        finally{remove(body);}
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void damage(EntityDamageEvent event){
        if(striking!=null&&event instanceof EntityDamageByEntityEvent bite&&bite.getDamager()==striking.shell&&!part(event.getEntity()))return;
        if(part(event.getEntity())||event instanceof EntityDamageByEntityEvent by&&part(by.getDamager()))event.setCancelled(true);
    }
    @EventHandler public void target(EntityTargetEvent event){if(part(event.getEntity())||part(event.getTarget()))event.setCancelled(true);}
    @EventHandler public void death(EntityDeathEvent event){if(part(event.getEntity())){event.getDrops().clear();event.setDroppedExp(0);}}
    @EventHandler public void manipulate(PlayerArmorStandManipulateEvent event){if(part(event.getRightClicked()))event.setCancelled(true);}
    /** Armor-stand carriers may only report the "at" variant, depending on the client. */
    @EventHandler(priority=EventPriority.LOW) public void interactAt(PlayerInteractAtEntityEvent event){route(event);}
    /** Right-clicking the Java receiver or the Bedrock carrier reaches the pet's manager once. */
    @EventHandler(priority=EventPriority.LOW) public void interact(PlayerInteractEntityEvent event){route(event);}
    private void route(PlayerInteractEntityEvent event){
        if(!part(event.getRightClicked()))return;
        event.setCancelled(true);
        if(event.getHand()!=EquipmentSlot.HAND)return;
        PetBody body=find(event.getRightClicked());Player player=event.getPlayer();
        if(!clickable(body,player))return;
        Location eye=player.getEyeLocation(),contact=aim.contact(body,eye);
        double distance=eye.distance(contact),reach=reach(player);
        if(distance>reach||!clear(eye,contact.toVector().subtract(eye.toVector()),distance))return;
        click(body,player);
    }
    /** A display sculpture has no client hitbox; right-clicking its drawn body still reaches it. */
    @EventHandler(priority=EventPriority.LOW)
    public void aim(PlayerInteractEvent event){
        if(event.getHand()!=EquipmentSlot.HAND||event.getAction()!=Action.RIGHT_CLICK_AIR&&event.getAction()!=Action.RIGHT_CLICK_BLOCK)return;
        Player player=event.getPlayer();Viewer viewer=viewers.get(player.getUniqueId());
        if(viewer==null||viewer.shown.isEmpty())return;
        // Tagged tools and eggs own their use action; ordinary food and empty-hand clicks can aim a pet.
        var hand=player.getInventory().getItemInMainHand();
        if(hand.hasItemMeta()&&!hand.getItemMeta().getPersistentDataContainer().isEmpty())return;
        Location eye=player.getEyeLocation();Vector direction=eye.getDirection();double nearest=reach(player);
        var wall=eye.getWorld().rayTraceBlocks(eye,direction,nearest,FluidCollisionMode.NEVER,true);
        if(wall!=null)nearest=Math.min(nearest,eye.toVector().distance(wall.getHitPosition()));
        PetBody selected=null;
        for(PetBody body:viewer.shown){
            if(!clickable(body,player))continue;
            double hit=aim.hit(body,eye,direction,nearest);
            if(hit<nearest){nearest=hit;selected=body;}
        }
        if(selected==null)return;
        event.setCancelled(true);click(selected,player);
    }
    private boolean clickable(PetBody body,Player player){
        if(body==null||body.perched||!body.valid()||!managers.containsKey(body)||!player.isOnline()||player.isDead()
            ||player.getGameMode()==GameMode.SPECTATOR||player.getWorld()!=body.shell.getWorld())return false;
        Viewer viewer=viewers.get(player.getUniqueId());
        return viewer!=null&&!viewer.kind.equals("none")&&viewer.shown.contains(body)
            &&player.canSee(viewer.kind.equals("bedrock")&&body.carrier!=null?body.carrier:body.shell);
    }
    private static double reach(Player player){
        var attribute=player.getAttribute(Attribute.ENTITY_INTERACTION_RANGE);
        return attribute==null?3:Math.max(0,attribute.getValue());
    }
    private static boolean clear(Location eye,Vector direction,double distance){
        return distance<.001||eye.getWorld().rayTraceBlocks(eye,direction.normalize(),distance,FluidCollisionMode.NEVER,true)==null;
    }
    private void click(PetBody body,Player player){
        long now=System.currentTimeMillis();UUID id=player.getUniqueId();
        if(now-lastClick.getOrDefault(id,0L)<200)return;
        Manager manager=managers.get(body);if(manager==null)return;
        lastClick.put(id,now);manager.interact(body,player);
    }
    @EventHandler public void quit(PlayerQuitEvent event){viewers.remove(event.getPlayer().getUniqueId());lastClick.remove(event.getPlayer().getUniqueId());}
    @Override public void close(){
        task.cancel();
        for(PetBody body:new ArrayList<>(managers.keySet()))lose(body);
        viewers.clear();HandlerList.unregisterAll(this);
    }
}
