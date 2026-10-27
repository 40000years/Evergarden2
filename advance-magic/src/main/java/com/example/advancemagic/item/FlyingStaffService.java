package com.example.advancemagic.item;

import com.example.advancemagic.AdvanceMagicPlugin;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.*;

/** One server-steered mount per player. The rider never gets Bukkit flight. */
public final class FlyingStaffService implements Listener, AutoCloseable {
    private static final String BASE_MODEL="flying_staff";
    private static final double REAR_SEAT_DISTANCE=1.35;
    private static final double MAX_HORIZONTAL_SPEED=2.4;
    private static final double MAX_VERTICAL_SPEED=1.4;
    private static final double MOVE_STEP=.25;
    private static final double MAX_TERRAIN_CLIMB=1.0;
    private static final double RIDER_CLEARANCE=3.3;
    private static final int USE_QUIET_TICKS=8;
    private static final int MAX_LANDING_TICKS=1200;
    private static final int SAFE_DISMOUNT_TICKS=1400;
    private final AdvanceMagicPlugin plugin;
    private final FlyingStaffDiagnostics diagnostics;
    private final FlyingStaffMovement movement;
    private final FlyingStaffBedrockInput bedrockInput;
    private final NamespacedKey itemKey, entityKey, recipeKey, tierKey;
    private final Map<UUID,Session> sessions=new HashMap<>();
    private final Map<UUID,Session> passengers=new HashMap<>();
    private final Map<UUID,Session> byEntity=new HashMap<>();
    private long inputTick;

    private enum Phase { SUMMON, IDLE, FLIGHT, LANDING, DISMISS }
    private static final class Session {
        final UUID owner;
        final ArmorStand stand;
        final Location returnLocation;
        final boolean bedrock;
        int tier=1;
        ArmorStand rearSeat;
        UUID passenger;
        Location passengerReturn;
        PermissionAttachment passengerExemption;
        Phase phase=Phase.SUMMON;
        int age=0;
        boolean lowManaWarning;
        boolean manualTurbo;
        boolean landingUnridden;
        boolean idleFramePending;
        Location dismountOrigin;
        int dismountTick;
        long lastUseTick;
        final Vector motion=new Vector();
        PermissionAttachment exemption;
        Session(Player player,ArmorStand stand,boolean bedrock){
            this.owner=player.getUniqueId();this.stand=stand;this.returnLocation=player.getLocation();this.bedrock=bedrock;
        }
    }

    public FlyingStaffService(AdvanceMagicPlugin plugin) {
        this.plugin=plugin;
        diagnostics=new FlyingStaffDiagnostics(plugin);
        movement=new FlyingStaffMovement();
        bedrockInput=new FlyingStaffBedrockInput(plugin);
        itemKey=new NamespacedKey(plugin,"flying_staff");
        entityKey=new NamespacedKey(plugin,"flying_staff_entity");
        recipeKey=new NamespacedKey(plugin,"flying_staff");
        tierKey=new NamespacedKey(plugin,"flying_staff_tier");
    }
    public static boolean upgradeSpeedConfig(FileConfiguration config) {
        if(config.getInt("flying-staff.speed-version",0)>=3)return false;
        double horizontal=config.getDouble("flying-staff.horizontal-speed",.27);
        double vertical=config.getDouble("flying-staff.vertical-speed",.16);
        if(Double.compare(horizontal,.18)==0||Double.compare(horizontal,.27)==0)
            config.set("flying-staff.horizontal-speed",.486);
        if(Double.compare(vertical,.12)==0||Double.compare(vertical,.16)==0)
            config.set("flying-staff.vertical-speed",.288);
        config.set("flying-staff.turbo-multiplier",4.0);
        config.set("flying-staff.speed-version",3);
        return true;
    }
    public ItemStack create() {
        return create(1);
    }
    public ItemStack create(int tier) {
        if(tier!=1&&tier!=2)throw new IllegalArgumentException("Flying staff tier must be 1 or 2");
        ItemStack item=new ItemStack(Material.BLAZE_ROD);
        var meta=item.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD+"✦ "+ChatColor.AQUA+"ไม้เท้าบิน");
        meta.setItemModel(new NamespacedKey("advance_magic",BASE_MODEL));
        if(tier==2){
            meta.setDisplayName(ChatColor.GOLD+"✦ "+ChatColor.AQUA+"ไม้เท้าบินระดับ 2 · อัครปักษา");
            meta.setItemModel(new NamespacedKey("advance_magic",BASE_MODEL+"_tier2"));
        }
        meta.getPersistentDataContainer().set(itemKey,PersistentDataType.BYTE,(byte)1);
        meta.getPersistentDataContainer().set(tierKey,PersistentDataType.INTEGER,tier);
        item.setItemMeta(meta);
        plugin.wands().migrate(item);
        return item;
    }
    public int tier(ItemStack item) {
        return isStaff(item)&&item.getItemMeta().getPersistentDataContainer().getOrDefault(tierKey,PersistentDataType.INTEGER,1)==2?2:1;
    }
    public boolean isStaff(ItemStack item) {
        return item!=null&&item.getType()==Material.BLAZE_ROD&&item.hasItemMeta()&&
            item.getItemMeta().getPersistentDataContainer().has(itemKey,PersistentDataType.BYTE);
    }
    public void register() {
        Bukkit.removeRecipe(recipeKey);
        for(Player player:Bukkit.getOnlinePlayers())removeLegacyRecipe(player);
    }
    public void removeLegacyRecipe(Player player) {
        // Clear saved recipe-book entries, including players joining after an upgrade.
        player.undiscoverRecipe(recipeKey);
    }
    public boolean isRiding(Player player) {
        Session session=sessions.get(player.getUniqueId());
        if(session!=null&&player.getVehicle()==session.stand)return true;
        session=passengers.get(player.getUniqueId());
        return session!=null&&player.getVehicle()==session.rearSeat;
    }
    public boolean toggleTurbo(Player player) {
        Session session=sessions.get(player.getUniqueId());
        if(session==null||player.getVehicle()!=session.stand)return false;
        session.manualTurbo=!session.manualTurbo;
        return session.manualTurbo;
    }
    public boolean isDisplay(Entity entity) {return byEntity.containsKey(entity.getUniqueId());}
    private ItemStack image(String state,int frame) {
        return image(1,state,frame);
    }
    private ItemStack image(Session session,String state,int frame) {return image(session.tier,state,frame);}
    private ItemStack image(int tier,String state,int frame) {
        // Armor models override item_model on an iron helmet. A carved pumpkin
        // renders its item model in the head slot, including on armor stands.
        ItemStack item=new ItemStack(Material.CARVED_PUMPKIN);
        var meta=item.getItemMeta();
        meta.setItemModel(new NamespacedKey("advance_magic",BASE_MODEL+(tier==2?"_tier2":"")+"_"+state));
        var data=meta.getCustomModelDataComponent();
        data.setFloats(List.of((float)frame));
        meta.setCustomModelDataComponent(data);
        item.setItemMeta(meta);
        return item;
    }
    @EventHandler(priority=EventPriority.HIGH)
    public void summon(PlayerInteractEvent event) {
        if(event.getHand()!=EquipmentSlot.HAND||!isStaff(event.getItem())||
            (event.getAction()!=Action.RIGHT_CLICK_AIR&&event.getAction()!=Action.RIGHT_CLICK_BLOCK))return;
        // Paper may pre-cancel right-click-air when there is no vanilla action.
        if(event.getAction()==Action.RIGHT_CLICK_BLOCK&&event.useItemInHand()==Event.Result.DENY)return;
        event.setCancelled(true);
        Player player=event.getPlayer();
        if(!plugin.getConfig().getBoolean("flying-staff.enabled",true)||!player.hasPermission("advance-magic.flying-staff")||
            player.getGameMode()==GameMode.SPECTATOR)return;
        Session previous=sessions.get(player.getUniqueId());
        if(previous!=null&&previous.stand.isValid()){
            long quiet=inputTick-previous.lastUseTick;
            previous.lastUseTick=inputTick;
            // Holding Use produces repeated interactions. Require a quiet interval
            // before treating another event as a new press, including after boarding.
            if(quiet<USE_QUIET_TICKS)return;
            if(previous.phase==Phase.SUMMON)return;
        }
        if(previous!=null&&player.getVehicle()==previous.stand) {
            player.sendMessage(ChatColor.AQUA+(toggleTurbo(player)?"เปิด Turbo ค้างแล้ว":"ปิด Turbo ค้างแล้ว"));
            return;
        }
        if(player.isInsideVehicle())return;
        if(previous!=null&&previous.tier==tier(event.getItem())&&previous.phase==Phase.IDLE&&previous.stand.isValid()&&
            previous.stand.getWorld()==player.getWorld()&&previous.stand.getLocation().distanceSquared(player.getLocation())<=9){
            board(player,previous);
            return;
        }
        ItemStack held=player.getInventory().getItemInMainHand();
        if(!isStaff(held))return;
        int tier=tier(held);
        plugin.wands().migrate(held);
        player.getInventory().setItemInMainHand(held);
        if(held.getAmount()!=1){
            player.sendMessage(ChatColor.YELLOW+"แยกไม้เท้าบินทีละอันก่อนเรียก");return;
        }
        if(plugin.wands().usesLeft(held)<=0){
            player.sendMessage(ChatColor.RED+"ไม้เท้าบินหมดความทนทานแล้ว ซ่อมด้วยคทาหรือแท่นฟื้นฟูก่อนเรียก");return;
        }
        Location origin=player.getLocation();
        Vector direction=origin.getDirection().setY(0);
        if(direction.lengthSquared()<.01)direction=new Vector(0,0,1);
        direction.normalize();
        Location target=origin.clone().add(direction.multiply(1.5));
        target.setYaw(origin.getYaw());target.setPitch(0);
        if(!clear(target,tier)){
            player.sendMessage(ChatColor.RED+"ไม่มีที่ว่างพอสำหรับเรียกไม้เท้า");return;
        }
        ArmorStand stand;
        if(previous!=null&&previous.tier!=tier){closeSession(previous);previous=null;}
        if(previous!=null&&previous.stand.isValid()){
            stand=previous.stand;
            if(!stand.getPassengers().isEmpty()||previous.passenger!=null){
                player.sendMessage(ChatColor.YELLOW+"ไม้เท้ายังมีผู้ขี่อยู่ จึงเรียกกลับไม่ได้");return;
            }
            if(!stand.teleport(target,PlayerTeleportEvent.TeleportCause.PLUGIN)){
                player.sendMessage(ChatColor.RED+"เรียกไม้เท้ากลับมาที่นี่ไม่ได้");return;
            }
            unexempt(player,previous);
            if(previous.rearSeat!=null){byEntity.remove(previous.rearSeat.getUniqueId());previous.rearSeat.remove();}
            stand.setVelocity(new Vector());
            stand.getEquipment().setHelmet(image(tier,"summon",0),true);
            player.sendMessage(ChatColor.AQUA+"เรียกไม้เท้าตัวเดิมกลับมาแล้ว คลิกที่ไม้เท้าเพื่อขึ้นขี่");
        }else{
            if(previous!=null)closeSession(previous);
            stand=target.getWorld().spawn(target,ArmorStand.class,a->{
                a.setVisible(false);a.setGravity(false);a.setBasePlate(false);a.setArms(false);
                a.setPersistent(false);a.setCollidable(false);
                a.getPersistentDataContainer().set(entityKey,PersistentDataType.STRING,player.getUniqueId().toString());
                a.getEquipment().setHelmet(image(tier,"summon",0),true);
                for(ArmorStand.LockType lock:ArmorStand.LockType.values())a.addEquipmentLock(EquipmentSlot.HEAD,lock);
            });
            if(!stand.isValid()||stand.isDead()){
                player.sendMessage(ChatColor.YELLOW+"เรียกไม้เท้าที่นี่ไม่ได้ — ยังไม่เสียความทนทาน");return;
            }
        }
        Session session=new Session(player,stand,plugin.packs().isBedrock(player));
        session.tier=tier;
        if(tier==2){
            session.rearSeat=target.getWorld().spawn(rearLocation(target),ArmorStand.class,a->{
                a.setVisible(false);a.setGravity(false);a.setBasePlate(false);a.setArms(false);
                a.setPersistent(false);a.setCollidable(false);
                a.getPersistentDataContainer().set(entityKey,PersistentDataType.STRING,player.getUniqueId().toString());
                for(EquipmentSlot slot:List.of(EquipmentSlot.HAND,EquipmentSlot.OFF_HAND,EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET))
                    for(ArmorStand.LockType lock:ArmorStand.LockType.values())a.addEquipmentLock(slot,lock);
            });
            if(!session.rearSeat.isValid()||session.rearSeat.isDead()){
                closeSession(session);return;
            }
            byEntity.put(session.rearSeat.getUniqueId(),session);
        }
        // Spend only after both seats successfully spawn. The final use may still be ridden.
        plugin.wands().consumeUse(held);
        player.getInventory().setItemInMainHand(held);
        player.saveData();
        session.lastUseTick=inputTick;
        sessions.put(player.getUniqueId(),session);byEntity.put(stand.getUniqueId(),session);
        target.getWorld().playSound(target,Sound.BLOCK_AMETHYST_BLOCK_RESONATE,.7f,1.5f);
        target.getWorld().spawnParticle(Particle.END_ROD,target.clone().add(0,1,0),8,.4,.3,.4,.01);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void mount(PlayerInteractEntityEvent event) {
        Session session=byEntity.get(event.getRightClicked().getUniqueId());
        if(session==null)return;
        event.setCancelled(true);
        if(event.getHand()!=EquipmentSlot.HAND)return;
        board(event.getPlayer(),session);
    }
    private void board(Player player,Session session) {
        if(!session.owner.equals(player.getUniqueId())){boardPassenger(player,session);return;}
        if(!session.owner.equals(player.getUniqueId())||session.phase!=Phase.IDLE||player.isInsideVehicle()||
            !plugin.getConfig().getBoolean("flying-staff.enabled",true)||
            player.getGameMode()==GameMode.SPECTATOR||!player.hasPermission("advance-magic.flying-staff"))return;
        if(!ownsItem(player,session.tier)){
            player.sendMessage(ChatColor.RED+"ต้องมีไม้เท้าของคุณอยู่ในตัวเพื่อขึ้นขี่");return;
        }
        if(plugin.mana().account(player).manaExact()<=0){player.sendMessage(ChatColor.RED+"มานาไม่พอสำหรับขี่ไม้เท้า");return;}
        if(!clear(session.stand.getLocation(),session.tier)){
            player.sendMessage(ChatColor.YELLOW+"พื้นที่เหนือไม้เท้าไม่พอสำหรับขึ้นขี่");return;
        }
        if(!player.hasLineOfSight(session.stand)){
            player.sendMessage(ChatColor.YELLOW+"มีสิ่งกีดขวางระหว่างคุณกับไม้เท้า");return;
        }
        if(session.stand.addPassenger(player)){
            diagnostics.mount(player,session.stand);
            session.phase=Phase.FLIGHT;session.age=0;session.lowManaWarning=false;session.manualTurbo=false;
            session.motion.zero();
            session.lastUseTick=inputTick;
            session.stand.getEquipment().setHelmet(image(session,"flight",0),true);
            exempt(player,session);
            if(session.bedrock)bedrockInput.mount(player);
            player.setFallDistance(0);
            player.sendMessage(ChatColor.AQUA+(session.bedrock?
                "ดันเดินหน้าแล้วเงย/ก้มกล้องเพื่อขึ้น/ลง · กระโดดเพื่อขึ้นตรง ๆ · Sprint: Turbo · ย่อง: ลงจากคทา":
                "Turbo: กด Sprint หรือใช้ไม้เท้าในมือเพื่อเปิดค้าง · กระโดด: ขึ้น · ก้มแล้วเดินหน้า: ลง"));
        }
    }
    private void boardPassenger(Player player,Session session) {
        Player driver=Bukkit.getPlayer(session.owner);
        if(session.tier!=2||session.phase!=Phase.FLIGHT||session.passenger!=null||session.rearSeat==null||
            !session.rearSeat.isValid()||driver==null||driver.getVehicle()!=session.stand||
            player.isInsideVehicle()||player.getGameMode()==GameMode.SPECTATOR||
            !player.hasPermission("advance-magic.flying-staff")||!plugin.getConfig().getBoolean("flying-staff.enabled",true))return;
        if(player.getWorld()!=session.stand.getWorld()||player.getLocation().distanceSquared(session.rearSeat.getLocation())>25||
            !player.hasLineOfSight(session.rearSeat)||!clear(session.stand.getLocation(),2))return;
        Location origin=player.getLocation();
        if(!session.rearSeat.addPassenger(player))return;
        session.passenger=player.getUniqueId();session.passengerReturn=origin;
        passengers.put(session.passenger,session);
        session.passengerExemption=attachment(player);
        player.setFallDistance(0);
        player.sendMessage(ChatColor.AQUA+"ขึ้นซ้อนไม้เท้าบินแล้ว · ผู้ขับควบคุมและใช้มานา · ย่องเพื่อลง");
    }
    private Location rearLocation(Location front) {
        double yaw=Math.toRadians(front.getYaw());
        return front.clone().add(Math.sin(yaw)*REAR_SEAT_DISTANCE,0,-Math.cos(yaw)*REAR_SEAT_DISTANCE);
    }
    private void syncRear(Session session) {
        if(session.rearSeat!=null&&session.rearSeat.isValid())movement.move(session.rearSeat,rearLocation(session.stand.getLocation()));
    }
    private void releasePassenger(Session session,boolean safeReturn) {
        UUID id=session.passenger;if(id==null)return;
        passengers.remove(id);session.passenger=null;
        if(session.passengerExemption!=null){try{session.passengerExemption.remove();}catch(IllegalArgumentException ignored){}session.passengerExemption=null;}
        Player guest=Bukkit.getPlayer(id);
        if(guest!=null){
            guest.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING,SAFE_DISMOUNT_TICKS,0,true,false,false));
            guest.setFallDistance(0);
            if(guest.getVehicle()==session.rearSeat)guest.leaveVehicle();
            if(safeReturn&&guest.isOnline()&&!guest.isDead()){
                Location destination=session.passengerReturn!=null&&clear(session.passengerReturn)?session.passengerReturn:session.returnLocation;
                guest.teleport(clear(destination)?destination:destination.getWorld().getSpawnLocation());
            }
        }
        session.passengerReturn=null;
    }
    @EventHandler(priority=EventPriority.HIGHEST)
    public void manipulate(PlayerArmorStandManipulateEvent event) {
        if(byEntity.containsKey(event.getRightClicked().getUniqueId()))event.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST)
    public void attack(EntityDamageByEntityEvent event) {
        Session session=byEntity.get(event.getEntity().getUniqueId());
        if(session==null)return;
        event.setCancelled(true);
        if(event.getDamager() instanceof Player player&&session.owner.equals(player.getUniqueId())&&player.getVehicle()!=session.stand){
            session.phase=Phase.DISMISS;session.age=0;
            releasePassenger(session,!groundBelow(session.stand.getLocation()));
            session.stand.getEquipment().setHelmet(image(session,"dismiss",0),true);
            session.stand.getWorld().playSound(session.stand.getLocation(),Sound.BLOCK_AMETHYST_BLOCK_BREAK,.6f,1.6f);
        }
    }
    @EventHandler(priority=EventPriority.HIGHEST)
    public void protect(EntityDamageEvent event) {
        if(byEntity.containsKey(event.getEntity().getUniqueId()))event.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST)
    public void dismount(EntityDismountEvent event) {
        if(!(event.getEntity() instanceof Player player))return;
        Session session=byEntity.get(event.getDismounted().getUniqueId());
        if(session!=null&&event.getDismounted()==session.rearSeat&&player.getUniqueId().equals(session.passenger)){
            boolean safe=!groundBelow(session.rearSeat.getLocation());
            // Leave the current dismount event to finish before a possible safe-return teleport.
            UUID id=session.passenger;
            if(safe)Bukkit.getScheduler().runTask(plugin,()->{if(id.equals(session.passenger))releasePassenger(session,true);});
            else releasePassenger(session,false);
            return;
        }
        if(session==null||!session.owner.equals(player.getUniqueId())||
            (session.phase!=Phase.FLIGHT&&session.phase!=Phase.LANDING))return;
        if(!nearGround(session.stand.getLocation())){
            if(!groundBelow(session.stand.getLocation())){
                // Nothing below can ever finish a landing; sinking into the void used to trap the rider.
                if(session.bedrock||!event.isCancellable()){
                    session.phase=Phase.IDLE;unexempt(player,session);
                    Bukkit.getScheduler().runTask(plugin,()->{if(sessions.get(session.owner)==session&&player.isOnline())returnSafely(player,session);});
                    player.sendMessage(ChatColor.YELLOW+"ด้านล่างเป็น Void · ส่งกลับจุดที่เรียกไม้เท้าเพื่อความปลอดภัย");
                    return;
                }
                event.setCancelled(true);
                if(session.phase==Phase.FLIGHT)plugin.casts().actionbar(player,"ด้านล่างเป็น Void ลงจอดไม่ได้ · บินไปเหนือพื้นแล้วกด Shift");
                return;
            }
            if(session.bedrock||!event.isCancellable()){
                // Bedrock already starts the camera dismount when this event arrives.
                // Cancelling it makes Geyser snap the rider back repeatedly.
                if(!session.landingUnridden){
                    session.landingUnridden=true;session.phase=Phase.LANDING;session.age=0;
                    session.motion.zero();session.manualTurbo=false;
                    unexempt(player,session);
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING,SAFE_DISMOUNT_TICKS,0,true,false,false));
                    player.setFallDistance(0);
                    player.sendMessage(ChatColor.AQUA+"ลงจากไม้เท้าแล้ว · Slow Falling ช่วยพาลงพื้น");
                }
                return;
            }
            event.setCancelled(true);
            if(session.phase==Phase.FLIGHT){
                session.phase=Phase.LANDING;session.age=0;
                player.sendMessage(ChatColor.AQUA+"ไม้เท้ากำลังลงจอด");
            }
            return;
        }
        releasePassenger(session,false);
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING,100,0,true,false,false));
        unexempt(player,session);
        session.phase=Phase.IDLE;session.age=0;session.manualTurbo=false;session.landingUnridden=false;session.motion.zero();
        showIdleAfterDismount(session);
    }
    @EventHandler public void quit(PlayerQuitEvent event){closeOwner(event.getPlayer());}
    @EventHandler(priority=EventPriority.MONITOR)
    public void traceDismount(EntityDismountEvent event){
        if(event.getEntity() instanceof Player player&&byEntity.containsKey(event.getDismounted().getUniqueId())){
            Session session=byEntity.get(event.getDismounted().getUniqueId());
            if(session.owner.equals(player.getUniqueId())&&session.bedrock&&!event.isCancelled()){
                session.dismountOrigin=player.getLocation();
                session.dismountTick=Bukkit.getCurrentTick();
            }
            diagnostics.dismount(player,event.isCancelled());
        }
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void localDismountTeleport(PlayerTeleportEvent event){
        if(event.getCause()!=PlayerTeleportEvent.TeleportCause.DISMOUNT)return;
        Session session=sessions.get(event.getPlayer().getUniqueId());
        if(session==null||session.dismountOrigin==null)return;
        Location origin=session.dismountOrigin;
        session.dismountOrigin=null;
        if(session.dismountTick!=Bukkit.getCurrentTick()||event.getTo()==null||
            event.getFrom().getWorld()!=origin.getWorld()||event.getTo().getWorld()!=origin.getWorld()||
            event.getFrom().distanceSquared(origin)>16||event.getTo().distanceSquared(origin)>16)return;
        // Paper already removed the passenger. Its synthetic exit teleport is
        // unnecessary for this collision-free mount and triggers arrival refreshes.
        // Staying at the checked seat position lets Bedrock dismount normally and
        // descend with Slow Falling. Real teleports and other vehicles are untouched.
        event.setCancelled(true);
    }
    @EventHandler public void death(PlayerDeathEvent event){closeOwner(event.getEntity());}
    @EventHandler public void world(PlayerChangedWorldEvent event){closeOwner(event.getPlayer());}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void teleport(PlayerTeleportEvent event) {
        if(event.getTo()==null||!event.getFrom().getWorld().equals(event.getTo().getWorld())||
            event.getFrom().distanceSquared(event.getTo())>16)closeOwner(event.getPlayer());
    }
    private boolean ownsItem(Player player) {
        for(ItemStack item:player.getInventory().getContents())if(isStaff(item))return true;
        return false;
    }
    private boolean ownsItem(Player player,int tier) {
        for(ItemStack item:player.getInventory().getContents())if(isStaff(item)&&tier(item)==tier)return true;
        return false;
    }
    private void exempt(Player player,Session session) {
        if(session.exemption==null)session.exemption=attachment(player);
    }
    private PermissionAttachment attachment(Player player) {
        PermissionAttachment result=null;
        var section=plugin.getConfig().getConfigurationSection("flying-staff.anticheat.bypass-permissions");
        if(section==null)return null;
        for(String name:section.getKeys(false)) {
            if(!Bukkit.getPluginManager().isPluginEnabled(name))continue;
            List<String> permissions=section.getStringList(name);
            if(permissions.isEmpty())continue;
            if(result==null)result=player.addAttachment(plugin);
            for(String permission:permissions)if(permission.matches("[a-zA-Z0-9_.-]+"))result.setPermission(permission,true);
        }
        return result;
    }
    private void unexempt(Player player,Session session) {
        bedrockInput.unmount(player);
        revokeExemption(session);
    }
    private void revokeExemption(Session session) {
        if(session.exemption==null)return;
        try {session.exemption.remove();}catch(IllegalArgumentException ignored){}
        session.exemption=null;
    }
    private void showIdleAfterDismount(Session session) {
        if(!session.bedrock){session.stand.getEquipment().setHelmet(image(session,"idle",0),true);return;}
        if(session.idleFramePending)return;
        session.idleFramePending=true;
        Bukkit.getScheduler().runTaskLater(plugin,()->{
            session.idleFramePending=false;
            if(sessions.get(session.owner)==session&&session.phase==Phase.IDLE&&
                session.stand.isValid()&&session.stand.getPassengers().isEmpty())
                session.stand.getEquipment().setHelmet(image(session,"idle",0),true);
        },2);
    }
    private boolean nearGround(Location loc) {
        World world=loc.getWorld();
        for(double y=.2;y<=2.2;y+=.5)
            if(!world.getBlockAt(loc.clone().add(0,-y,0)).isPassable())return true;
        return false;
    }
    /** Any solid support down to the world floor; floating islands may be far below. */
    private boolean groundBelow(Location loc) {
        return groundWithin(loc,loc.getBlockY()-loc.getWorld().getMinHeight()+1);
    }
    private boolean groundWithin(Location loc,int depth) {
        World world=loc.getWorld();int x=loc.getBlockX(),z=loc.getBlockZ();
        for(int y=loc.getBlockY();y>=Math.max(world.getMinHeight(),loc.getBlockY()-depth);y--)
            if(!world.getBlockAt(x,y,z).isPassable())return true;
        return false;
    }
    /** Ends a flight that has nowhere to land: back to the summon point (or spawn), never into the void. */
    private void returnSafely(Player player,Session session) {
        releasePassenger(session,true);
        session.phase=Phase.IDLE;unexempt(player,session);
        if(player.getVehicle()==session.stand)player.leaveVehicle();
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING,100,0,true,false,false));
        player.setFallDistance(0);
        player.teleport(clear(session.returnLocation)?session.returnLocation:session.stand.getWorld().getSpawnLocation());
        closeSession(session);
    }
    private boolean riding(Player player) {
        return isRiding(player);
    }
    /** Riders are exempt from anticheat movement checks, so they may not fight or mine meanwhile. */
    @EventHandler(priority=EventPriority.LOWEST,ignoreCancelled=true)
    public void riderAttack(EntityDamageByEntityEvent event) {
        Player rider=event.getDamager() instanceof Player p?p
            :event.getDamager() instanceof org.bukkit.entity.Projectile shot&&shot.getShooter() instanceof Player p?p:null;
        if(rider!=null&&riding(rider)){event.setCancelled(true);plugin.casts().actionbar(rider,"โจมตีไม่ได้ขณะขี่ไม้เท้าบิน");}
    }
    @EventHandler(priority=EventPriority.LOWEST,ignoreCancelled=true)
    public void riderShoot(org.bukkit.event.entity.ProjectileLaunchEvent event) {
        if(event.getEntity().getShooter() instanceof Player rider&&riding(rider))event.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.LOWEST,ignoreCancelled=true)
    public void riderBreak(org.bukkit.event.block.BlockBreakEvent event) {
        if(riding(event.getPlayer())){event.setCancelled(true);plugin.casts().actionbar(event.getPlayer(),"ขุดไม่ได้ขณะขี่ไม้เท้าบิน");}
    }
    private boolean clear(Location loc) {
        World world=loc.getWorld();
        if(loc.getY()<world.getMinHeight()+1||loc.getY()>world.getMaxHeight()-RIDER_CLEARANCE||
            !world.isChunkLoaded(loc.getBlockX()>>4,loc.getBlockZ()>>4)||!world.getWorldBorder().isInside(loc))return false;
        // The seated player's head is about 3.175 blocks above the stand's origin.
        for(double y:new double[]{.1,.9,1.8,2.2,2.9,RIDER_CLEARANCE})for(double x:new double[]{-.35,.35})for(double z:new double[]{-.35,.35}) {
            Block block=world.getBlockAt(loc.clone().add(x,y,z));
            if(!block.isPassable())return false;
        }
        return true;
    }
    private boolean clear(Location loc,int tier) {
        if(!clear(loc))return false;
        if(tier!=2)return true;
        // Check the rear rider and the space between both seats, even when empty.
        Location rear=rearLocation(loc);
        return clear(rear)&&clear(loc.clone().add(rear.toVector().subtract(loc.toVector()).multiply(.5)));
    }
    private boolean turbo(Player player,Session session) {
        var controls=player.getCurrentInput();
        var analog=session.bedrock?bedrockInput.sample(player):null;
        return session.manualTurbo||(analog!=null?analog.sprint():controls.isSprint()||player.isSprinting());
    }
    private double manaPerSecond(boolean turbo) {
        double normal=Math.clamp(plugin.getConfig().getDouble("flying-staff.mana-per-second",2),.1,20);
        return turbo?normal*Math.clamp(plugin.getConfig().getDouble("flying-staff.turbo-mana-multiplier",1.5),1,4):normal;
    }
    private Vector input(Player player,Session session) {
        var controls=player.getCurrentInput();
        double yaw=Math.toRadians(player.getLocation().getYaw());
        Vector forward=new Vector(-Math.sin(yaw),0,Math.cos(yaw));
        Vector side=new Vector(-forward.getZ(),0,forward.getX());
        double forwardInput=(controls.isForward()?1:0)-(controls.isBackward()?1:0);
        double rightInput=(controls.isRight()?1:0)-(controls.isLeft()?1:0);
        var analog=session.bedrock?bedrockInput.sample(player):null;
        if(analog!=null){forwardInput=analog.forward();rightInput=-analog.left();}
        Vector target=forward.multiply(forwardInput).add(side.multiply(rightInput));
        if(target.lengthSquared()>1)target.normalize();
        double multiplier=turbo(player,session)?Math.clamp(plugin.getConfig().getDouble("flying-staff.turbo-multiplier",4),1,4):1;
        target.multiply(Math.min(Math.clamp(plugin.getConfig().getDouble("flying-staff.horizontal-speed",.486),.03,.6)*multiplier,MAX_HORIZONTAL_SPEED));
        double vertical=Math.min(Math.clamp(plugin.getConfig().getDouble("flying-staff.vertical-speed",.288),.03,.35)*multiplier,MAX_VERTICAL_SPEED);
        if(analog!=null?analog.jump():controls.isJump())target.setY(vertical);
        else if(session.bedrock&&forwardInput>0){
            double pitch=player.getLocation().getPitch();
            double deadzone=Math.clamp(plugin.getConfig().getDouble("flying-staff.bedrock-controls.pitch-deadzone",12),0,45);
            double full=Math.clamp(plugin.getConfig().getDouble("flying-staff.bedrock-controls.pitch-full-speed",60),deadzone+1,90);
            double amount=Math.clamp((Math.abs(pitch)-deadzone)/(full-deadzone),0,1);
            target.setY(-Math.signum(pitch)*amount*forwardInput*vertical);
        }else if(!session.bedrock&&forwardInput>0&&player.getLocation().getPitch()>35)target.setY(-vertical);
        return target.multiply(session.tier==2?.5:1);
    }
    private void move(Session session,Player player,Vector motion) {
        ArmorStand stand=session.stand;
        Location start=stand.getLocation();
        Location position=start.clone();
        float yaw=player.getLocation().getYaw();
        if(session.tier==2){
            // Sweep the rear seat through a turn, rather than jumping through a wall.
            double turn=Math.IEEEremainder(yaw-start.getYaw(),360);
            int turns=Math.max(1,(int)Math.ceil(Math.abs(turn)/10));
            for(int i=1;i<=turns;i++){
                Location rotated=start.clone();rotated.setYaw((float)(start.getYaw()+turn*i/turns));
                if(!clear(rotated,2)){yaw=position.getYaw();break;}
                position.setYaw(rotated.getYaw());
            }
        }
        Vector requested=motion.clone();
        double terrainClimb=0;
        int steps=Math.max(1,(int)Math.ceil(Math.max(Math.max(Math.abs(motion.getX()),Math.abs(motion.getY())),
            Math.abs(motion.getZ()))/MOVE_STEP));
        Vector step=motion.clone().multiply(1.0/steps);
        for(int i=0;i<steps;i++) {
            Location full=position.clone().add(step);
            if(clear(full,session.tier)){position=full;continue;}
            // Low flight should follow a one-block rise in the ground instead
            // of repeatedly stopping against the edge of the next block.
            if(step.getY()==0&&(step.getX()!=0||step.getZ()!=0)&&nearGround(position)) {
                boolean steppedUp=false;
                for(double rise=MOVE_STEP;rise<=MAX_TERRAIN_CLIMB-terrainClimb+.0001;rise+=MOVE_STEP) {
                    Location above=position.clone().add(0,rise,0);
                    if(!clear(above,session.tier))break;
                    Location over=above.clone().add(step.getX(),0,step.getZ());
                    if(clear(over,session.tier)){
                        position=over;terrainClimb+=rise;steppedUp=true;break;
                    }
                }
                if(steppedUp)continue;
            }
            if(step.getX()!=0&&clear(position.clone().add(step.getX(),0,0),session.tier))position.add(step.getX(),0,0);
            if(step.getZ()!=0&&clear(position.clone().add(0,0,step.getZ()),session.tier))position.add(0,0,step.getZ());
            if(step.getY()!=0&&clear(position.clone().add(0,step.getY(),0),session.tier))position.add(0,step.getY(),0);
        }
        Vector actual=position.toVector().subtract(start.toVector());
        session.motion.setX(actual.getX());session.motion.setZ(actual.getZ());
        session.motion.setY(requested.getY()==0?0:actual.getY());
        if(actual.lengthSquared()>0) {
            position.setYaw(yaw);position.setPitch(0);
            movement.move(stand,position);
        } else stand.setRotation(yaw,0);
        syncRear(session);
    }
    public void tick() {
        inputTick++;
        diagnostics.tick();
        for(Session session:new ArrayList<>(sessions.values())) {
            ArmorStand stand=session.stand;
            Player player=Bukkit.getPlayer(session.owner);
            if(player==null||!player.isOnline()||player.isDead()||!stand.isValid()||
                player.getWorld()!=stand.getWorld()||!ownsItem(player,session.tier)||
                (session.tier==2&&(session.rearSeat==null||!session.rearSeat.isValid()))||
                !plugin.getConfig().getBoolean("flying-staff.enabled",true)||
                !player.hasPermission("advance-magic.flying-staff")||player.getGameMode()==GameMode.SPECTATOR){
                if(player!=null&&player.isOnline()&&!player.isDead()&&player.getWorld()==stand.getWorld()&&
                    player.getVehicle()==stand&&!groundBelow(stand.getLocation()))returnSafely(player,session);
                else closeSession(session);
                continue;
            }
            if(session.passenger!=null){
                Player guest=Bukkit.getPlayer(session.passenger);
                if(guest==null||!guest.isOnline()||guest.isDead()||guest.getVehicle()!=session.rearSeat||
                    guest.getGameMode()==GameMode.SPECTATOR||!guest.hasPermission("advance-magic.flying-staff"))
                    releasePassenger(session,guest!=null&&guest.isOnline()&&!guest.isDead()&&!groundBelow(session.rearSeat.getLocation()));
                else guest.setFallDistance(0);
            }
            session.age++;
            switch(session.phase) {
                case SUMMON -> {
                    if(!session.bedrock)stand.getEquipment().setHelmet(image(session,"summon",Math.min(6,session.age/2)),true);
                    if(session.age>=12){session.phase=Phase.IDLE;session.age=0;stand.getEquipment().setHelmet(image(session,"idle",0),true);}
                }
                case IDLE -> {
                    stand.setVelocity(new Vector());
                    if(!session.bedrock&&session.age%8==0)
                        stand.getEquipment().setHelmet(image(session,"idle",(session.age/8)%8),true);
                }
                case FLIGHT -> {
                    if(player.getVehicle()!=stand){releasePassenger(session,!groundBelow(stand.getLocation()));unexempt(player,session);session.phase=Phase.IDLE;session.age=0;session.motion.zero();break;}
                    player.setFallDistance(0);
                    var account=plugin.mana().account(player);
                    boolean turboActive=turbo(player,session);
                    // Charge only the time spent in each mode, including short Turbo bursts.
                    account.setMana(account.manaExact()-manaPerSecond(turboActive)*(session.tier==2?1.5:1)/20.0);
                    if(session.age%20==0){
                        plugin.mana().save(player);
                        plugin.casts().actionbar(player,turboActive?"ไม้เท้าบิน · TURBO":"ไม้เท้าบิน");
                        if(account.manaExact()<=10&&!session.lowManaWarning){
                            session.lowManaWarning=true;
                            player.sendMessage(ChatColor.YELLOW+"มานาใกล้หมด เตรียมลงจอด");
                        }
                    }
                    if(account.manaExact()<=0){
                        plugin.mana().save(player);
                        if(!groundBelow(stand.getLocation())){player.sendMessage(ChatColor.YELLOW+"มานาหมดเหนือ Void · ส่งกลับจุดที่เรียกไม้เท้า");returnSafely(player,session);break;}
                        session.phase=Phase.LANDING;session.age=0;player.sendMessage(ChatColor.YELLOW+"มานาหมด ไม้เท้ากำลังลงจอด");break;
                    }
                    Vector target=input(player,session);
                    // Ease abrupt key and joystick changes, then sweep the fast path in
                    // short steps so Turbo cannot skip a wall between endpoints.
                    double response=target.lengthSquared()>0?.4:.8;
                    session.motion.multiply(1-response).add(target.multiply(response));
                    if(session.motion.lengthSquared()<.0001)session.motion.zero();
                    move(session,player,session.motion);
                    // Bedrock animates this attachable locally. Re-equipping every
                    // four ticks restarts its animation and makes it flash.
                    if(!session.bedrock&&(session.passenger==null||!plugin.packs().isBedrock(Bukkit.getPlayer(session.passenger)))&&session.age%4==0)
                        stand.getEquipment().setHelmet(image(session,"flight",(session.age/4)%8),true);
                    if(session.age%5==0)stand.getWorld().spawnParticle(Particle.END_ROD,stand.getLocation().add(0,.9,0),2,.1,.08,.1,.004);
                }
                case LANDING -> {
                    if(!session.landingUnridden&&player.getVehicle()!=stand){
                        unexempt(player,session);session.phase=Phase.IDLE;session.age=0;session.motion.zero();break;
                    }
                    if(player.getVehicle()==stand)player.setFallDistance(0);
                    if(nearGround(stand.getLocation())){
                        releasePassenger(session,false);
                        if(player.getVehicle()==stand){
                            unexempt(player,session);
                            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING,100,0,true,false,false));
                            player.leaveVehicle();
                        }
                        session.phase=Phase.IDLE;session.age=0;session.manualTurbo=false;
                        session.landingUnridden=false;session.motion.zero();
                        stand.setVelocity(new Vector());
                        showIdleAfterDismount(session);
                    } else if(session.age>MAX_LANDING_TICKS||stand.getLocation().getY()<stand.getWorld().getMinHeight()+3){
                        // A void or blocked landing must not leave the rider falling.
                        session.phase=Phase.IDLE;unexempt(player,session);
                        if(player.getVehicle()==stand){
                            player.leaveVehicle();
                            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING,100,0,true,false,false));
                            Location destination=clear(session.returnLocation)?session.returnLocation:stand.getWorld().getSpawnLocation();
                            player.teleport(destination);
                        }
                        closeSession(session);
                    } else {
                        // Descend faster while the ground is still far away, then settle gently.
                        Location next=stand.getLocation().add(0,groundWithin(stand.getLocation(),8)?-.1:-.5,0);
                        if(clear(next,session.tier)){movement.move(stand,next);syncRear(session);}
                    }
                }
                case DISMISS -> {
                    stand.setVelocity(new Vector());
                    if(!session.bedrock)stand.getEquipment().setHelmet(image(session,"dismiss",Math.min(4,session.age/2)),true);
                    if(session.age>=8)closeSession(session);
                }
            }
        }
    }
    private void closeOwner(Player player) {
        Session session=sessions.get(player.getUniqueId());
        if(session!=null)closeSession(session);
        Session riding=passengers.get(player.getUniqueId());
        if(riding!=null)releasePassenger(riding,false);
    }
    private void closeSession(Session session) {
        sessions.remove(session.owner);byEntity.remove(session.stand.getUniqueId());
        session.phase=Phase.IDLE;
        releasePassenger(session,!groundBelow(session.stand.getLocation()));
        if(session.rearSeat!=null){byEntity.remove(session.rearSeat.getUniqueId());if(session.rearSeat.isValid())session.rearSeat.remove();}
        bedrockInput.detach(session.owner);
        revokeExemption(session);
        Player player=Bukkit.getPlayer(session.owner);
        if(player!=null){unexempt(player,session);if(player.getVehicle()==session.stand){
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING,SAFE_DISMOUNT_TICKS,0,true,false,false));
            player.leaveVehicle();player.setFallDistance(0);
        }}
        if(session.stand.isValid())session.stand.remove();
    }
    @Override public void close() {
        diagnostics.close();
        for(Session session:new ArrayList<>(sessions.values()))closeSession(session);
        bedrockInput.close();
        Bukkit.removeRecipe(recipeKey);
    }
}
