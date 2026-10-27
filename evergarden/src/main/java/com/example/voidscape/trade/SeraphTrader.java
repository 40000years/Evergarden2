package com.example.voidscape.trade;

import com.example.voidscape.VoidscapePlugin;
import com.example.voidscape.boss.JudgePackService;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * The winged Seraph at the Evergarden spawn who runs the {@link SeraphShop}.
 * Same rig as the approved Seraph preview: Java players with the World Boss pack see an item
 * display, Bedrock players a helmet-model armor stand. An interaction box takes clicks for
 * everyone. Nothing is saved to the world; the parts are rebuilt whenever the chunk loads.
 */
public final class SeraphTrader implements Listener,AutoCloseable {
    private final VoidscapePlugin plugin;
    private final SeraphShop shop;
    private final SeraphFeathers feathers;
    private final File file;
    private YamlConfiguration placement;
    private Interaction hitbox;
    private ItemDisplay java;
    private ArmorStand bedrock;
    private TextDisplay label;
    private final Map<UUID,String> viewers=new HashMap<>();
    private final BukkitTask task;
    private long clock,castUntil;
    private float yaw;
    private String pose="";
    private boolean closed;

    public SeraphTrader(VoidscapePlugin plugin){
        this.plugin=plugin;feathers=new SeraphFeathers(plugin);shop=new SeraphShop(plugin,this,feathers);
        file=new File(plugin.getDataFolder(),"seraph-trader.yml");placement=YamlConfiguration.loadConfiguration(file);
        Bukkit.getPluginManager().registerEvents(this,plugin);Bukkit.getPluginManager().registerEvents(shop,plugin);
        Bukkit.getPluginManager().registerEvents(new com.example.voidscape.boss.BossArmorCores(plugin),plugin);
        task=Bukkit.getScheduler().runTaskTimer(plugin,this::tick,40,2);
    }
    public SeraphShop shop(){return shop;}
    public SeraphFeathers feathers(){return feathers;}
    private boolean enabled(){return plugin.getConfig().getBoolean("seraph-trader.enabled",true);}
    /** The admin's {@code /evergarden seraph here} position wins over config defaults. */
    public Location anchor(){
        World world=plugin.world();if(world==null)return null;
        var config=plugin.getConfig();
        double x=placement.getDouble("x",config.getDouble("seraph-trader.x",7.5));
        double y=placement.getDouble("y",config.getDouble("seraph-trader.y",97));
        double z=placement.getDouble("z",config.getDouble("seraph-trader.z",.5));
        float facing=(float)placement.getDouble("yaw",config.getDouble("seraph-trader.yaw",90));
        if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z)||!Float.isFinite(facing))return null;
        return new Location(world,x,y,z,facing,0);
    }
    public void reload(){placement=YamlConfiguration.loadConfiguration(file);despawn();}
    private boolean spawned(){return hitbox!=null&&hitbox.isValid();}
    public boolean near(Player player){
        return spawned()&&player.getWorld()==hitbox.getWorld()&&player.getLocation().distanceSquared(hitbox.getLocation())<=12*12;
    }
    private static ItemStack model(String pose){
        ItemStack item=new ItemStack(Material.IRON_HELMET);var meta=item.getItemMeta();
        meta.setItemModel(new NamespacedKey("voidscape","judge_seraph_"+pose));item.setItemMeta(meta);return item;
    }
    private void tag(Entity e,boolean visible){
        e.setPersistent(false);e.setVisibleByDefault(visible);e.setGravity(false);e.setInvulnerable(true);e.setSilent(true);
        e.getPersistentDataContainer().set(plugin.key("seraph_trader"),PersistentDataType.BYTE,(byte)1);
    }
    private boolean ours(Entity e){return e!=null&&e.getPersistentDataContainer().has(plugin.key("seraph_trader"),PersistentDataType.BYTE);}
    private boolean mine(Entity e){return e==hitbox||e==java||e==bedrock||e==label;}
    private void spawn(Location at){
        World world=at.getWorld();yaw=at.getYaw();pose="idle";
        try{
            hitbox=world.spawn(at,Interaction.class,e->{tag(e,true);e.setInteractionWidth(1.6f);e.setInteractionHeight(3.4f);e.setResponsive(true);});
            java=world.spawn(at,ItemDisplay.class,e->{
                tag(e,false);e.setItemStack(model("idle"));e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
                e.setInterpolationDuration(4);e.setTeleportDuration(4);e.setViewRange(2);
                e.setDisplayWidth(7);e.setDisplayHeight(7);e.setShadowRadius(0);e.setBrightness(new Display.Brightness(14,15));
                e.setTransformation(new Transformation(new Vector3f(0,2,0),new Quaternionf(),new Vector3f(1.6f),new Quaternionf()));
                e.setRotation(at.getYaw(),0);
            });
            bedrock=world.spawn(at.clone().add(0,.5,0),ArmorStand.class,e->{
                tag(e,false);e.setVisible(false);e.setMarker(false);e.setBasePlate(false);e.setArms(false);e.setCollidable(false);
                e.getEquipment().setHelmet(model("idle"));e.setRotation(at.getYaw(),0);
                for(EquipmentSlot slot:List.of(EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET,EquipmentSlot.HAND,EquipmentSlot.OFF_HAND))
                    e.addEquipmentLock(slot,ArmorStand.LockType.ADDING_OR_CHANGING);
            });
            double height=plugin.getConfig().getDouble("seraph-trader.label-height",4.6);
            label=world.spawn(at.clone().add(0,Double.isFinite(height)?height:4.6,0),TextDisplay.class,e->{
                tag(e,true);
                e.text(Component.text("✦ Seraph ✦",NamedTextColor.GOLD).decoration(TextDecoration.BOLD,true)
                    .append(Component.newline()).append(Component.text("ผู้แลกเปลี่ยนแห่งสรวงสวรรค์",NamedTextColor.WHITE).decoration(TextDecoration.BOLD,false))
                    .append(Component.newline()).append(Component.text("คลิกขวาเพื่อแลกของ · รับพรประจำวัน",NamedTextColor.AQUA).decoration(TextDecoration.BOLD,false)));
                e.setBillboard(Display.Billboard.CENTER);e.setAlignment(TextDisplay.TextAlignment.CENTER);
                e.setShadowed(true);e.setSeeThrough(false);e.setBackgroundColor(Color.fromARGB(96,12,10,24));
                e.setViewRange(.5f);e.setLineWidth(220);
            });
            if(!hitbox.isValid()||!java.isValid()||!bedrock.isValid()||!label.isValid())throw new IllegalStateException("a part was cancelled");
            viewers.clear();
        }catch(RuntimeException|LinkageError error){
            despawn();plugin.getLogger().warning("Cannot place the Seraph trader: "+error);
        }
    }
    private void despawn(){
        for(Entity e:new Entity[]{hitbox,java,bedrock,label})if(e!=null)e.remove();
        hitbox=null;java=null;bedrock=null;label=null;viewers.clear();
    }
    private void tick(){
        clock+=2;
        if(closed)return;
        if(clock%200==0)shop.announce();
        Location at=anchor();
        if(!enabled()||at==null){if(spawned())despawn();return;}
        if(!spawned()){
            if(clock%20==0&&at.getWorld().isChunkLoaded(at.getBlockX()>>4,at.getBlockZ()>>4))spawn(at);
            return;
        }
        if(!java.isValid()||!bedrock.isValid()||!label.isValid()){despawn();return;}
        List<Player> players=at.getWorld().getPlayers();
        if(clock%10==0)refreshViewers(players);
        Player nearest=null;double best=Double.MAX_VALUE;
        for(Player p:players){
            if(p.getGameMode()==GameMode.SPECTATOR)continue;
            double distance=p.getLocation().distanceSquared(at);if(distance<best){best=distance;nearest=p;}
        }
        // Nobody within 48 blocks: hold still and send nothing.
        if(best>48*48||clock%4!=0)return;
        float target=at.getYaw();
        if(nearest!=null&&best<12*12)target=(float)Math.toDegrees(Math.atan2(-(nearest.getX()-at.getX()),nearest.getZ()-at.getZ()));
        float turn=wrap(target-yaw);yaw=wrap(yaw+Math.clamp(turn,-10f,10f));
        double bob=Math.sin(clock*.045)*.12;
        String next=clock<castUntil?"cast":"idle";
        if(!next.equals(pose)){pose=next;java.setItemStack(model(pose));bedrock.getEquipment().setHelmet(model(pose));}
        Location javaAt=at.clone().add(0,bob,0);javaAt.setYaw(yaw);java.teleport(javaAt);
        Location bedrockAt=at.clone().add(0,.5+bob,0);bedrockAt.setYaw(yaw);bedrock.teleport(bedrockAt);
        World world=at.getWorld();
        if(clock%40==0)world.spawnParticle(Particle.END_ROD,at.clone().add(0,4,0),2,.35,.15,.35,.005);
        if(clock<castUntil&&clock%8==0){
            Location hands=at.clone().add(0,2.3+bob,0).add(new Location(world,0,0,0,yaw,0).getDirection().multiply(.9));
            world.spawnParticle(Particle.END_ROD,hands,6,.35,.45,.35,.015);
        }
    }
    private static float wrap(float degrees){degrees%=360;if(degrees>=180)degrees-=360;if(degrees<-180)degrees+=360;return degrees;}
    private void refreshViewers(List<Player> players){
        Set<UUID> present=new HashSet<>();
        for(Player viewer:players){
            present.add(viewer.getUniqueId());
            boolean bedrockPlayer=JudgePackService.bedrock(viewer),accepted=plugin.judgePack()!=null&&plugin.judgePack().applied(viewer);
            String kind=bedrockPlayer?"bedrock":accepted?"java":"none";
            if(kind.equals(viewers.put(viewer.getUniqueId(),kind)))continue;
            if(kind.equals("java"))viewer.showEntity(plugin,java);else viewer.hideEntity(plugin,java);
            if(bedrockPlayer)viewer.showEntity(plugin,bedrock);else viewer.hideEntity(plugin,bedrock);
        }
        viewers.keySet().retainAll(present);
    }
    /** A short casting pose and a burst of light after every completed exchange. */
    void celebrate(Player player,boolean grand){
        if(!spawned())return;
        castUntil=clock+40;
        Location at=hitbox.getLocation();World world=at.getWorld();
        world.spawnParticle(Particle.END_ROD,at.clone().add(0,2.5,0),grand?60:20,.7,.9,.7,.06);
        if(grand)world.spawnParticle(Particle.TOTEM_OF_UNDYING,player.getLocation().add(0,1,0),40,.5,.8,.5,.3);
    }

    // ---- interaction ----
    private void use(Player player,Entity clicked){
        if(!ours(clicked))return;
        if(!enabled()){plugin.message(player,"Seraph ปิดรับแลกเปลี่ยนชั่วคราว");return;}
        shop.open(player);
    }
    @EventHandler(priority=EventPriority.LOW)
    public void interact(PlayerInteractEntityEvent event){
        if(!ours(event.getRightClicked()))return;
        event.setCancelled(true);
        if(event.getHand()==EquipmentSlot.HAND)use(event.getPlayer(),event.getRightClicked());
    }
    @EventHandler(priority=EventPriority.LOW)
    public void interactAt(PlayerInteractAtEntityEvent event){
        if(!ours(event.getRightClicked()))return;
        event.setCancelled(true);
        if(event.getHand()==EquipmentSlot.HAND)use(event.getPlayer(),event.getRightClicked());
    }
    /** Touch screens often tap to attack; hitting the Seraph opens the shop instead. */
    @EventHandler(priority=EventPriority.LOW)
    public void attack(PrePlayerAttackEntityEvent event){
        if(!ours(event.getAttacked()))return;
        event.setCancelled(true);use(event.getPlayer(),event.getAttacked());
    }
    @EventHandler(priority=EventPriority.HIGHEST)
    public void damage(EntityDamageEvent event){if(ours(event.getEntity()))event.setCancelled(true);}
    @EventHandler public void manipulate(PlayerArmorStandManipulateEvent event){if(ours(event.getRightClicked()))event.setCancelled(true);}
    @EventHandler public void loaded(EntitiesLoadEvent event){for(Entity e:event.getEntities())if(ours(e)&&!mine(e))e.remove();}
    @EventHandler(ignoreCancelled=true) public void unload(ChunkUnloadEvent event){
        if(!spawned()||event.getWorld()!=hitbox.getWorld())return;
        Location at=hitbox.getLocation();
        if((at.getBlockX()>>4)==event.getChunk().getX()&&(at.getBlockZ()>>4)==event.getChunk().getZ())despawn();
    }
    @EventHandler public void unloadWorld(WorldUnloadEvent event){if(spawned()&&event.getWorld()==hitbox.getWorld())despawn();}
    @EventHandler public void quit(PlayerQuitEvent event){viewers.remove(event.getPlayer().getUniqueId());}

    // ---- commands ----
    private boolean admin(CommandSender s){return s.isOp()||s.hasPermission("evergarden.admin")||s.hasPermission("voidscape.admin");}
    public String status(){
        Location at=anchor();
        return (enabled()?"เปิด":"ปิด")+" · "+(spawned()?"อยู่ในโลก":"รอ chunk โหลด")+(at==null?"":" · "+at.getWorld().getName()
            +" "+String.format(Locale.ROOT,"%.1f %.1f %.1f yaw %.0f",at.getX(),at.getY(),at.getZ(),at.getYaw()));
    }
    /** {@code /evergarden shop} near the Seraph, and admin {@code /evergarden seraph status|here|reset|respawn}. */
    public boolean command(CommandSender sender,String[] args){
        if(args[0].equalsIgnoreCase("shop")){
            if(!(sender instanceof Player player)){plugin.message(sender,"ใช้คำสั่งนี้ในเกม");return true;}
            if(!near(player)&&!admin(player)){plugin.message(player,"เดินไปหา Seraph ที่จุดเกิด Evergarden แล้วคลิกขวาที่ Seraph");return true;}
            shop.open(player);return true;
        }
        if(!admin(sender)){plugin.message(sender,"ไม่มีสิทธิ์แอดมิน");return true;}
        String action=args.length>1?args[1].toLowerCase(Locale.ROOT):"status";
        switch(action){
            case "here"->{
                if(!(sender instanceof Player player)||player.getWorld()!=plugin.world()){plugin.message(sender,"ยืนในโลก Evergarden ตรงจุดที่ต้องการ");return true;}
                Location at=player.getLocation();
                placement.set("x",Math.round(at.getX()*2)/2.0);placement.set("y",Math.floor(at.getY()));placement.set("z",Math.round(at.getZ()*2)/2.0);
                placement.set("yaw",(double)Math.round(at.getYaw()/15)*15);
                if(!save())plugin.message(sender,"บันทึก seraph-trader.yml ไม่สำเร็จ");
                despawn();plugin.message(sender,"ย้าย Seraph มาที่นี่แล้ว · "+status());
            }
            case "reset"->{placement=new YamlConfiguration();save();despawn();plugin.message(sender,"คืนตำแหน่งตาม config.yml · "+status());}
            case "respawn"->{despawn();plugin.message(sender,"สร้าง Seraph ใหม่ในอีกครู่");}
            default->plugin.message(sender,"Seraph · "+status()+" · /evergarden seraph status|here|reset|respawn");
        }
        return true;
    }
    private boolean save(){
        try{placement.save(file);return true;}
        catch(IOException error){plugin.getLogger().warning("Cannot save seraph-trader.yml: "+error);return false;}
    }
    @Override public void close(){closed=true;shop.close();task.cancel();despawn();HandlerList.unregisterAll(this);HandlerList.unregisterAll(shop);feathers.close();}
}
