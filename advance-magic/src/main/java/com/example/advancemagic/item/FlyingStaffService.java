package com.example.advancemagic.item;

import com.example.advancemagic.AdvanceMagicPlugin;
import io.papermc.paper.entity.TeleportFlag;
import org.bukkit.*;
import org.bukkit.block.Block;
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
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.*;

/** One server-steered mount per player. The rider never gets Bukkit flight. */
public final class FlyingStaffService implements Listener, AutoCloseable {
    private static final String BASE_MODEL="flying_staff";
    private final AdvanceMagicPlugin plugin;
    private final NamespacedKey itemKey, entityKey, recipeKey;
    private final Map<UUID,Session> sessions=new HashMap<>();
    private final Map<UUID,Session> byEntity=new HashMap<>();

    private enum Phase { SUMMON, IDLE, FLIGHT, LANDING, DISMISS }
    private static final class Session {
        final UUID owner;
        final ArmorStand stand;
        final Location returnLocation;
        Phase phase=Phase.SUMMON;
        int age=0;
        boolean lowManaWarning;
        PermissionAttachment exemption;
        Session(Player player,ArmorStand stand){this.owner=player.getUniqueId();this.stand=stand;this.returnLocation=player.getLocation();}
    }

    public FlyingStaffService(AdvanceMagicPlugin plugin) {
        this.plugin=plugin;
        itemKey=new NamespacedKey(plugin,"flying_staff");
        entityKey=new NamespacedKey(plugin,"flying_staff_entity");
        recipeKey=new NamespacedKey(plugin,"flying_staff");
    }
    public ItemStack create() {
        ItemStack item=new ItemStack(Material.BLAZE_ROD);
        var meta=item.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD+"✦ "+ChatColor.AQUA+"ไม้เท้าบิน");
        meta.setLore(List.of(ChatColor.GRAY+"คลิกขวาเพื่อเรียกไม้เท้า",ChatColor.GRAY+"คลิกที่ไม้เท้าเพื่อขึ้นขี่ · ย่องเพื่อลง",
            ChatColor.GRAY+"กระโดดขึ้น · มองลงแล้วเดินหน้าเพื่อลงระดับ",ChatColor.AQUA+"ขณะขี่ใช้มานา 2 ต่อวินาที"));
        meta.setItemModel(new NamespacedKey("advance_magic",BASE_MODEL));
        meta.getPersistentDataContainer().set(itemKey,PersistentDataType.BYTE,(byte)1);
        item.setItemMeta(meta);
        return item;
    }
    public boolean isStaff(ItemStack item) {
        return item!=null&&item.getType()==Material.BLAZE_ROD&&item.hasItemMeta()&&
            item.getItemMeta().getPersistentDataContainer().has(itemKey,PersistentDataType.BYTE);
    }
    public void register() {
        Bukkit.removeRecipe(recipeKey);
        ShapedRecipe recipe=new ShapedRecipe(recipeKey,create());
        recipe.shape("GAG","BRB","GAG");
        recipe.setIngredient('G',Material.GOLD_INGOT);
        recipe.setIngredient('A',Material.AMETHYST_SHARD);
        recipe.setIngredient('B',Material.BLAZE_ROD);
        recipe.setIngredient('R',Material.HEART_OF_THE_SEA);
        Bukkit.addRecipe(recipe);
        for(Player player:Bukkit.getOnlinePlayers())discover(player);
    }
    public void discover(Player player) {
        if(player.hasPermission("advance-magic.flying-staff"))player.discoverRecipe(recipeKey);
    }
    public boolean isRiding(Player player) {
        Session session=sessions.get(player.getUniqueId());
        return session!=null&&player.getVehicle()==session.stand;
    }
    public boolean isDisplay(Entity entity) {return byEntity.containsKey(entity.getUniqueId());}
    private ItemStack image(String state,int frame) {
        ItemStack item=new ItemStack(Material.IRON_HELMET);
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
            player.getGameMode()==GameMode.SPECTATOR||player.isInsideVehicle())return;
        if(sessions.containsKey(player.getUniqueId())){
            player.sendMessage(ChatColor.YELLOW+"ไม้เท้าของคุณถูกเรียกอยู่แล้ว คลิกที่ไม้เท้าเพื่อขึ้นขี่");return;
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
        ArmorStand stand=target.getWorld().spawn(target,ArmorStand.class,a->{
            a.setVisible(false);a.setGravity(false);a.setBasePlate(false);a.setArms(false);
            a.setPersistent(false);a.setCollidable(false);
            a.getPersistentDataContainer().set(entityKey,PersistentDataType.STRING,player.getUniqueId().toString());
            a.getEquipment().setHelmet(image("summon",0),true);
            for(ArmorStand.LockType lock:ArmorStand.LockType.values())a.addEquipmentLock(EquipmentSlot.HEAD,lock);
        });
        Session session=new Session(player,stand);
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
            session.phase=Phase.FLIGHT;session.age=0;session.lowManaWarning=false;
            session.stand.getEquipment().setHelmet(image("flight",0),true);
            exempt(player,session);
            player.setFallDistance(0);
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
        if(!nearGround(session.stand.getLocation())&&event.isCancellable()){
            event.setCancelled(true);session.phase=Phase.LANDING;session.age=0;
            player.sendMessage(ChatColor.AQUA+"ไม้เท้ากำลังลงจอด");
            return;
        }
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING,100,0,true,false,false));
        unexempt(player,session);
        session.phase=Phase.IDLE;session.age=0;
        session.stand.getEquipment().setHelmet(image("idle",0),true);
    }
    @EventHandler public void quit(PlayerQuitEvent event){closeOwner(event.getPlayer());}
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
    private Vector input(Player player) {
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
        target.multiply(Math.clamp(plugin.getConfig().getDouble("flying-staff.horizontal-speed",.18),.03,.35));
        double vertical=Math.clamp(plugin.getConfig().getDouble("flying-staff.vertical-speed",.12),.03,.25);
        if(controls.isJump())target.setY(vertical);
        else if(controls.isForward()&&player.getLocation().getPitch()>35)target.setY(-vertical);
        return target;
    }
    public void tick() {
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
                    stand.getEquipment().setHelmet(image("summon",Math.min(6,session.age/2)),true);
                    if(session.age>=12){session.phase=Phase.IDLE;session.age=0;stand.getEquipment().setHelmet(image("idle",0),true);}
                }
                case IDLE -> {
                    stand.setVelocity(new Vector());
                    if(session.age%8==0)stand.getEquipment().setHelmet(image("idle",(session.age/8)%8),true);
                }
                case FLIGHT -> {
                    if(player.getVehicle()!=stand){unexempt(player,session);session.phase=Phase.IDLE;session.age=0;break;}
                    player.setFallDistance(0);
                    if(session.age%20==0){
                        var account=plugin.mana().account(player);
                        double cost=Math.clamp(plugin.getConfig().getDouble("flying-staff.mana-per-second",2),.1,20);
                        account.setMana(account.manaExact()-cost);plugin.mana().save(player);
                        plugin.casts().actionbar(player,"ไม้เท้าบิน");
                        if(account.manaExact()<=10&&!session.lowManaWarning){
                            session.lowManaWarning=true;
                            player.sendMessage(ChatColor.YELLOW+"มานาใกล้หมด เตรียมลงจอด");
                        }
                        if(account.manaExact()<=0){session.phase=Phase.LANDING;session.age=0;player.sendMessage(ChatColor.YELLOW+"มานาหมด ไม้เท้ากำลังลงจอด");break;}
                    }
                    Vector velocity=input(player);
                    if(velocity.lengthSquared()>0) {
                        Location next=stand.getLocation().add(velocity);
                        next.setYaw(player.getLocation().getYaw());next.setPitch(0);
                        if(clear(next))stand.teleport(next,PlayerTeleportEvent.TeleportCause.PLUGIN,TeleportFlag.EntityState.RETAIN_PASSENGERS);
                    } else stand.setRotation(player.getLocation().getYaw(),0);
                    if(session.age%4==0)stand.getEquipment().setHelmet(image("flight",(session.age/4)%8),true);
                    if(session.age%5==0)stand.getWorld().spawnParticle(Particle.END_ROD,stand.getLocation().add(0,.9,0),2,.1,.08,.1,.004);
                }
                case LANDING -> {
                    if(player.getVehicle()!=stand){unexempt(player,session);session.phase=Phase.IDLE;session.age=0;break;}
                    player.setFallDistance(0);
                    if(nearGround(stand.getLocation())){
                        unexempt(player,session);
                        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING,100,0,true,false,false));
                        session.phase=Phase.IDLE;session.age=0;
                        player.leaveVehicle();
                        stand.setVelocity(new Vector());
                        stand.getEquipment().setHelmet(image("idle",0),true);
                    } else if(session.age>200||stand.getLocation().getY()<stand.getWorld().getMinHeight()+3){
                        // A void or blocked landing must not leave the rider falling.
                        session.phase=Phase.IDLE;unexempt(player,session);player.leaveVehicle();
                        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING,100,0,true,false,false));
                        Location destination=clear(session.returnLocation)?session.returnLocation:stand.getWorld().getSpawnLocation();
                        player.teleport(destination);
                        closeSession(session);
                    } else {
                        Location next=stand.getLocation().add(0,-.1,0);
                        if(clear(next))stand.teleport(next,PlayerTeleportEvent.TeleportCause.PLUGIN,TeleportFlag.EntityState.RETAIN_PASSENGERS);
                    }
                }
                case DISMISS -> {
                    stand.setVelocity(new Vector());
                    stand.getEquipment().setHelmet(image("dismiss",Math.min(4,session.age/2)),true);
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
        for(Session session:new ArrayList<>(sessions.values()))closeSession(session);
        Bukkit.removeRecipe(recipeKey);
    }
}
