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
    private static final double MAX_HORIZONTAL_SPEED=2.4;
    private static final double MAX_VERTICAL_SPEED=1.4;
    private static final double MOVE_STEP=.25;
    private static final double MAX_TERRAIN_CLIMB=1.0;
    private static final int MAX_LANDING_TICKS=1200;
    private static final int SAFE_DISMOUNT_TICKS=1400;
    private final AdvanceMagicPlugin plugin;
    private final FlyingStaffDiagnostics diagnostics;
    private final FlyingStaffMovement movement;
    private final NamespacedKey itemKey, entityKey, recipeKey;
    private final Map<UUID,Session> sessions=new HashMap<>();
    private final Map<UUID,Session> byEntity=new HashMap<>();

    private enum Phase { SUMMON, IDLE, FLIGHT, LANDING, DISMISS }
    private static final class Session {
        final UUID owner;
        final ArmorStand stand;
        final Location returnLocation;
        final boolean bedrock;
        Phase phase=Phase.SUMMON;
        int age=0;
        boolean lowManaWarning;
        boolean manualTurbo;
        boolean landingUnridden;
        boolean idleFramePending;
        Location dismountOrigin;
        int dismountTick;
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
        itemKey=new NamespacedKey(plugin,"flying_staff");
        entityKey=new NamespacedKey(plugin,"flying_staff_entity");
        recipeKey=new NamespacedKey(plugin,"flying_staff");
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
        ItemStack item=new ItemStack(Material.BLAZE_ROD);
        var meta=item.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD+"✦ "+ChatColor.AQUA+"ไม้เท้าบิน");
        meta.setItemModel(new NamespacedKey("advance_magic",BASE_MODEL));
        meta.getPersistentDataContainer().set(itemKey,PersistentDataType.BYTE,(byte)1);
        item.setItemMeta(meta);
        plugin.wands().migrate(item);
        return item;
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
        return session!=null&&player.getVehicle()==session.stand;
    }
    public boolean toggleTurbo(Player player) {
        Session session=sessions.get(player.getUniqueId());
        if(session==null||player.getVehicle()!=session.stand)return false;
        session.manualTurbo=!session.manualTurbo;
        return session.manualTurbo;
    }
    public boolean isDisplay(Entity entity) {return byEntity.containsKey(entity.getUniqueId());}
    private ItemStack image(String state,int frame) {
        // Armor models override item_model on an iron helmet. A carved pumpkin
        // renders its item model in the head slot, including on armor stands.
        ItemStack item=new ItemStack(Material.CARVED_PUMPKIN);
        var meta=item.getItemMeta();
        meta.setItemModel(new NamespacedKey("advance_magic","flying_staff_"+state));
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
        if(isRiding(player)) {
            player.sendMessage(ChatColor.AQUA+(toggleTurbo(player)?"เปิด Turbo ค้างแล้ว":"ปิด Turbo ค้างแล้ว"));
            return;
        }
        if(player.isInsideVehicle())return;
        ItemStack held=player.getInventory().getItemInMainHand();
        if(!isStaff(held))return;
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
        if(!clear(target)){
            player.sendMessage(ChatColor.RED+"ไม่มีที่ว่างพอสำหรับเรียกไม้เท้า");return;
        }
        Session previous=sessions.get(player.getUniqueId());
        ArmorStand stand;
        if(previous!=null&&previous.stand.isValid()){
            stand=previous.stand;
            if(!stand.getPassengers().isEmpty()){
                player.sendMessage(ChatColor.YELLOW+"ไม้เท้ายังมีผู้ขี่อยู่ จึงเรียกกลับไม่ได้");return;
            }
            if(!stand.teleport(target,PlayerTeleportEvent.TeleportCause.PLUGIN)){
                player.sendMessage(ChatColor.RED+"เรียกไม้เท้ากลับมาที่นี่ไม่ได้");return;
            }
            unexempt(player,previous);
            stand.setVelocity(new Vector());
            stand.getEquipment().setHelmet(image("summon",0),true);
            player.sendMessage(ChatColor.AQUA+"เรียกไม้เท้าตัวเดิมกลับมาแล้ว คลิกที่ไม้เท้าเพื่อขึ้นขี่");
        }else{
            if(previous!=null)closeSession(previous);
            stand=target.getWorld().spawn(target,ArmorStand.class,a->{
                a.setVisible(false);a.setGravity(false);a.setBasePlate(false);a.setArms(false);
                a.setPersistent(false);a.setCollidable(false);
                a.getPersistentDataContainer().set(entityKey,PersistentDataType.STRING,player.getUniqueId().toString());
                a.getEquipment().setHelmet(image("summon",0),true);
                for(ArmorStand.LockType lock:ArmorStand.LockType.values())a.addEquipmentLock(EquipmentSlot.HEAD,lock);
            });
            if(!stand.isValid()||stand.isDead()){
                player.sendMessage(ChatColor.YELLOW+"เรียกไม้เท้าที่นี่ไม่ได้ — ยังไม่เสียความทนทาน");return;
            }
        }
        // Spend only after a successful spawn/recall. The final use may still be ridden.
        plugin.wands().consumeUse(held);
        player.getInventory().setItemInMainHand(held);
        player.saveData();
        Session session=new Session(player,stand,plugin.packs().isBedrock(player));
        sessions.put(player.getUniqueId(),session);byEntity.put(stand.getUniqueId(),session);
        target.getWorld().playSound(target,Sound.BLOCK_AMETHYST_BLOCK_RESONATE,.7f,1.5f);
        target.getWorld().spawnParticle(Particle.END_ROD,target.clone().add(0,1,0),8,.4,.3,.4,.01);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void mount(PlayerInteractEntityEvent event) {
        Session session=byEntity.get(event.getRightClicked().getUniqueId());
        if(session==null)return;
        event.setCancelled(true);
        Player player=event.getPlayer();
        if(!session.owner.equals(player.getUniqueId())||session.phase!=Phase.IDLE||player.isInsideVehicle()||
            !player.hasPermission("advance-magic.flying-staff"))return;
        if(!ownsItem(player)){
            player.sendMessage(ChatColor.RED+"ต้องมีไม้เท้าของคุณอยู่ในตัวเพื่อขึ้นขี่");return;
        }
        if(plugin.mana().account(player).manaExact()<=0){player.sendMessage(ChatColor.RED+"มานาไม่พอสำหรับขี่ไม้เท้า");return;}
        if(session.stand.addPassenger(player)){
            diagnostics.mount(player,session.stand);
            session.phase=Phase.FLIGHT;session.age=0;session.lowManaWarning=false;session.manualTurbo=false;
            session.motion.zero();
            session.stand.getEquipment().setHelmet(image("flight",0),true);
            exempt(player,session);
            player.setFallDistance(0);
            player.sendMessage(ChatColor.AQUA+"Turbo: กด Sprint (Java: Ctrl, Bedrock: ปุ่มวิ่ง) หรือคลิกขวาไม้เท้าในมือเพื่อเปิดค้าง");
        }
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
            session.stand.getEquipment().setHelmet(image("dismiss",0),true);
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
        if(session==null||!session.owner.equals(player.getUniqueId())||
            (session.phase!=Phase.FLIGHT&&session.phase!=Phase.LANDING))return;
        if(!nearGround(session.stand.getLocation())){
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
    private void exempt(Player player,Session session) {
        var section=plugin.getConfig().getConfigurationSection("flying-staff.anticheat.bypass-permissions");
        if(section==null)return;
        for(String name:section.getKeys(false)) {
            if(!Bukkit.getPluginManager().isPluginEnabled(name))continue;
            List<String> permissions=section.getStringList(name);
            if(permissions.isEmpty())continue;
            if(session.exemption==null)session.exemption=player.addAttachment(plugin);
            for(String permission:permissions)if(permission.matches("[a-zA-Z0-9_.-]+"))session.exemption.setPermission(permission,true);
        }
    }
    private void unexempt(Player player,Session session) {
        if(session.exemption==null)return;
        try {player.removeAttachment(session.exemption);}catch(IllegalArgumentException ignored){}
        session.exemption=null;
    }
    private void showIdleAfterDismount(Session session) {
        if(!session.bedrock){session.stand.getEquipment().setHelmet(image("idle",0),true);return;}
        if(session.idleFramePending)return;
        session.idleFramePending=true;
        Bukkit.getScheduler().runTaskLater(plugin,()->{
            session.idleFramePending=false;
            if(sessions.get(session.owner)==session&&session.phase==Phase.IDLE&&
                session.stand.isValid()&&session.stand.getPassengers().isEmpty())
                session.stand.getEquipment().setHelmet(image("idle",0),true);
        },2);
    }
    private boolean nearGround(Location loc) {
        World world=loc.getWorld();
        for(double y=.2;y<=2.2;y+=.5)
            if(!world.getBlockAt(loc.clone().add(0,-y,0)).isPassable())return true;
        return false;
    }
    private boolean clear(Location loc) {
        World world=loc.getWorld();
        if(loc.getY()<world.getMinHeight()+1||loc.getY()>world.getMaxHeight()-3||
            !world.isChunkLoaded(loc.getBlockX()>>4,loc.getBlockZ()>>4)||!world.getWorldBorder().isInside(loc))return false;
        for(double y:new double[]{.1,.9,1.8,2.2})for(double x:new double[]{-.35,.35})for(double z:new double[]{-.35,.35}) {
            Block block=world.getBlockAt(loc.clone().add(x,y,z));
            if(!block.isPassable())return false;
        }
        return true;
    }
    private boolean turbo(Player player,Session session) {
        var controls=player.getCurrentInput();
        return session.manualTurbo||controls.isSprint()||player.isSprinting();
    }
    private double manaPerSecond(boolean turbo) {
        double normal=Math.clamp(plugin.getConfig().getDouble("flying-staff.mana-per-second",2),.1,20);
        return turbo?normal*Math.clamp(plugin.getConfig().getDouble("flying-staff.turbo-mana-multiplier",1.5),1,4):normal;
    }
    private Vector input(Player player,Session session) {
        var controls=player.getCurrentInput();
        double yaw=Math.toRadians(player.getLocation().getYaw());
        Vector forward=new Vector(-Math.sin(yaw),0,Math.cos(yaw));
        Vector side=new Vector(forward.getZ(),0,-forward.getX());
        Vector target=new Vector();
        if(controls.isForward())target.add(forward);
        if(controls.isBackward())target.subtract(forward);
        if(controls.isRight())target.add(side);
        if(controls.isLeft())target.subtract(side);
        if(target.lengthSquared()>1)target.normalize();
        double multiplier=turbo(player,session)?Math.clamp(plugin.getConfig().getDouble("flying-staff.turbo-multiplier",4),1,4):1;
        target.multiply(Math.min(Math.clamp(plugin.getConfig().getDouble("flying-staff.horizontal-speed",.486),.03,.6)*multiplier,MAX_HORIZONTAL_SPEED));
        double vertical=Math.min(Math.clamp(plugin.getConfig().getDouble("flying-staff.vertical-speed",.288),.03,.35)*multiplier,MAX_VERTICAL_SPEED);
        if(controls.isJump())target.setY(vertical);
        else if(controls.isForward()&&player.getLocation().getPitch()>35)target.setY(-vertical);
        return target;
    }
    private void move(Session session,Player player,Vector motion) {
        ArmorStand stand=session.stand;
        Location start=stand.getLocation();
        Location position=start.clone();
        Vector requested=motion.clone();
        double terrainClimb=0;
        int steps=Math.max(1,(int)Math.ceil(Math.max(Math.max(Math.abs(motion.getX()),Math.abs(motion.getY())),
            Math.abs(motion.getZ()))/MOVE_STEP));
        Vector step=motion.clone().multiply(1.0/steps);
        for(int i=0;i<steps;i++) {
            Location full=position.clone().add(step);
            if(clear(full)){position=full;continue;}
            // Low flight should follow a one-block rise in the ground instead
            // of repeatedly stopping against the edge of the next block.
            if(step.getY()==0&&(step.getX()!=0||step.getZ()!=0)&&nearGround(position)) {
                boolean steppedUp=false;
                for(double rise=MOVE_STEP;rise<=MAX_TERRAIN_CLIMB-terrainClimb+.0001;rise+=MOVE_STEP) {
                    Location above=position.clone().add(0,rise,0);
                    if(!clear(above))break;
                    Location over=above.clone().add(step.getX(),0,step.getZ());
                    if(clear(over)){
                        position=over;terrainClimb+=rise;steppedUp=true;break;
                    }
                }
                if(steppedUp)continue;
            }
            if(step.getX()!=0&&clear(position.clone().add(step.getX(),0,0)))position.add(step.getX(),0,0);
            if(step.getZ()!=0&&clear(position.clone().add(0,0,step.getZ())))position.add(0,0,step.getZ());
            if(step.getY()!=0&&clear(position.clone().add(0,step.getY(),0)))position.add(0,step.getY(),0);
        }
        Vector actual=position.toVector().subtract(start.toVector());
        session.motion.setX(actual.getX());session.motion.setZ(actual.getZ());
        session.motion.setY(requested.getY()==0?0:actual.getY());
        if(actual.lengthSquared()>0) {
            position.setYaw(player.getLocation().getYaw());position.setPitch(0);
            movement.move(stand,position);
        } else stand.setRotation(player.getLocation().getYaw(),0);
    }
    public void tick() {
        diagnostics.tick();
        for(Session session:new ArrayList<>(sessions.values())) {
            ArmorStand stand=session.stand;
            Player player=Bukkit.getPlayer(session.owner);
            if(player==null||!player.isOnline()||player.isDead()||!stand.isValid()||
                player.getWorld()!=stand.getWorld()||!ownsItem(player)){
                closeSession(session);continue;
            }
            session.age++;
            switch(session.phase) {
                case SUMMON -> {
                    if(!session.bedrock)stand.getEquipment().setHelmet(image("summon",Math.min(6,session.age/2)),true);
                    if(session.age>=12){session.phase=Phase.IDLE;session.age=0;stand.getEquipment().setHelmet(image("idle",0),true);}
                }
                case IDLE -> {
                    stand.setVelocity(new Vector());
                    if(!session.bedrock&&session.age%8==0)
                        stand.getEquipment().setHelmet(image("idle",(session.age/8)%8),true);
                }
                case FLIGHT -> {
                    if(player.getVehicle()!=stand){unexempt(player,session);session.phase=Phase.IDLE;session.age=0;session.motion.zero();break;}
                    player.setFallDistance(0);
                    var account=plugin.mana().account(player);
                    boolean turboActive=turbo(player,session);
                    // Charge only the time spent in each mode, including short Turbo bursts.
                    account.setMana(account.manaExact()-manaPerSecond(turboActive)/20.0);
                    if(session.age%20==0){
                        plugin.mana().save(player);
                        plugin.casts().actionbar(player,turboActive?"ไม้เท้าบิน · TURBO":"ไม้เท้าบิน");
                        if(account.manaExact()<=10&&!session.lowManaWarning){
                            session.lowManaWarning=true;
                            player.sendMessage(ChatColor.YELLOW+"มานาใกล้หมด เตรียมลงจอด");
                        }
                    }
                    if(account.manaExact()<=0){plugin.mana().save(player);session.phase=Phase.LANDING;session.age=0;player.sendMessage(ChatColor.YELLOW+"มานาหมด ไม้เท้ากำลังลงจอด");break;}
                    Vector target=input(player,session);
                    // Ease abrupt key and joystick changes, then sweep the fast path in
                    // short steps so Turbo cannot skip a wall between endpoints.
                    double response=target.lengthSquared()>0?.4:.8;
                    session.motion.multiply(1-response).add(target.multiply(response));
                    if(session.motion.lengthSquared()<.0001)session.motion.zero();
                    move(session,player,session.motion);
                    // Bedrock animates this attachable locally. Re-equipping every
                    // four ticks restarts its animation and makes it flash.
                    if(!session.bedrock&&session.age%4==0)
                        stand.getEquipment().setHelmet(image("flight",(session.age/4)%8),true);
                    if(session.age%5==0)stand.getWorld().spawnParticle(Particle.END_ROD,stand.getLocation().add(0,.9,0),2,.1,.08,.1,.004);
                }
                case LANDING -> {
                    if(!session.landingUnridden&&player.getVehicle()!=stand){
                        unexempt(player,session);session.phase=Phase.IDLE;session.age=0;session.motion.zero();break;
                    }
                    if(player.getVehicle()==stand)player.setFallDistance(0);
                    if(nearGround(stand.getLocation())){
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
                        Location next=stand.getLocation().add(0,-.1,0);
                        if(clear(next))movement.move(stand,next);
                    }
                }
                case DISMISS -> {
                    stand.setVelocity(new Vector());
                    if(!session.bedrock)stand.getEquipment().setHelmet(image("dismiss",Math.min(4,session.age/2)),true);
                    if(session.age>=8)closeSession(session);
                }
            }
        }
    }
    private void closeOwner(Player player) {
        Session session=sessions.get(player.getUniqueId());
        if(session!=null)closeSession(session);
    }
    private void closeSession(Session session) {
        sessions.remove(session.owner);byEntity.remove(session.stand.getUniqueId());
        Player player=Bukkit.getPlayer(session.owner);
        if(player!=null){unexempt(player,session);if(player.getVehicle()==session.stand){
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING,100,0,true,false,false));
            player.leaveVehicle();player.setFallDistance(0);
        }}
        if(session.stand.isValid())session.stand.remove();
    }
    @Override public void close() {
        diagnostics.close();
        for(Session session:new ArrayList<>(sessions.values()))closeSession(session);
        Bukkit.removeRecipe(recipeKey);
    }
}
