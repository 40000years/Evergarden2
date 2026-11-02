package com.example.voidscape.pet;

import com.example.voidscape.VoidscapePlugin;
import com.example.voidscape.boss.JudgePackService;
import org.bukkit.*;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.*;
import org.bukkit.util.Vector;
import java.lang.reflect.Method;
import java.util.*;

/** Owner-only mythical mounts, including Fenrir's grounded sprint and jump. No mana is involved. */
final class PetRiding implements Listener,AutoCloseable {
    private final VoidscapePlugin plugin;
    private final PetCompanions companions;
    private final Map<UUID,Session> sessions=new HashMap<>();
    private final Map<UUID,Location> exits=new HashMap<>();
    private final Analog analog=new Analog();
    private final PetMountBlessing blessing;
    private boolean closed;
    private static final class Session {
        final PetBody body;final ArmorStand seat;final Location origin;final boolean bedrock;
        final Vector motion=new Vector();final double lift,attachment;
        double vertical;boolean grounded,jumpHeld,jump;
        int ticks;
        PermissionAttachment exemption;
        Session(PetBody body,ArmorStand seat,Location origin,boolean bedrock,double lift,double attachment){
            this.body=body;this.seat=seat;this.origin=origin;this.bedrock=bedrock;this.lift=lift;this.attachment=attachment;
        }
    }
    PetRiding(VoidscapePlugin plugin,PetCompanions companions){
        this.plugin=plugin;this.companions=companions;
        blessing=new PetMountBlessing(plugin);
        Bukkit.getPluginManager().registerEvents(this,plugin);
        for(World world:Bukkit.getWorlds())for(Entity entity:world.getEntities())
            if(entity.getPersistentDataContainer().has(plugin.key("pet_ride_seat"),PersistentDataType.BYTE))entity.remove();
    }
    boolean riding(UUID owner){return sessions.containsKey(owner);}
    private double number(String key,double fallback,double min,double max){
        double value=plugin.getConfig().getDouble("pets.riding."+key,fallback);
        return Double.isFinite(value)?Math.clamp(value,min,max):fallback;
    }
    private double lift(PetBody body){
        double height=switch(body.species){case AETHERIUS->1.05;case IGNIS_DRACONIS->1.25;
            case ZEPHYROS->1.05;case SOLARIS->.9;case FENRIR->1.05;default->1;};
        return number("seat-height."+body.species.id,height,.4,2.5)*body.record.size;
    }
    String start(Player player,UUID id){
        if(closed||!plugin.getConfig().getBoolean("pets.riding.enabled",true))return "ระบบขี่สัตว์ปิดอยู่";
        if(!player.isOnline()||player.isDead()||player.getGameMode()==GameMode.SPECTATOR)return "ขี่สัตว์ในสถานะนี้ไม่ได้";
        if(riding(player.getUniqueId()))return "คุณขี่สัตว์อยู่แล้ว · ใช้ /pet dismount เพื่อลง";
        PetStable stable=companions.stable(player);PetRecord record=stable.get(id);
        if(stable.locked)return "อ่านข้อมูลคอกสัตว์ไม่ได้ · แจ้งแอดมิน";
        if(record==null||!record.species.mythical())return "ขี่ได้เฉพาะสัตว์เลี้ยงระดับตำนานของคุณ";
        if(plugin.getConfig().getBoolean("pets.riding.adult-only",true)&&record.stage!=PetCatalog.Stage.ADULT)return "เลี้ยงสัตว์ตำนานให้โตเต็มวัยก่อนขึ้นขี่";
        if(stable.nesting(id))return "สัตว์ตัวนี้อยู่ในรังผสมพันธุ์";
        if(companions.faintLeft(record)>0)return "สัตว์ตัวนี้ยังพักฟื้นอยู่";
        String blocked=companions.rideBlocker(player);if(blocked!=null)return "ขี่ไม่ได้ · "+blocked;
        if(player.isInsideVehicle())return "ลงจากพาหนะเดิมก่อนขึ้นขี่สัตว์";
        if(!id.equals(stable.active)||!stable.out||companions.body(player.getUniqueId())==null){
            String problem=companions.summon(player,id);if(problem!=null)return problem;
        }
        // A full-size body is necessary for a saddle; the head-perch mode remains available afterwards.
        if(record.mode.equals("perch"))companions.mode(player,id,"follow");
        PetBody body=companions.body(player.getUniqueId());
        if(body==null||!body.valid()||body.location().getWorld()!=player.getWorld()
            ||body.location().distanceSquared(player.getLocation())>36)return "สัตว์ยังไม่พร้อมอยู่ใกล้คุณ · เรียกออกมาในที่โล่งก่อน";
        if(!player.hasLineOfSight(body.shell))return "มีสิ่งกีดขวางระหว่างคุณกับสัตว์ · เข้าไปใกล้ก่อนขึ้นขี่";
        double lift=lift(body);Location at=body.location();
        if(!clear(at,lift,player))return "พื้นที่เหนือสัตว์ไม่พอสำหรับขึ้นขี่ · ย้ายไปที่โล่งก่อน";
        Location origin=player.getLocation();ArmorStand seat=null;
        try{
            seat=at.getWorld().spawn(at,ArmorStand.class,CreatureSpawnEvent.SpawnReason.CUSTOM,e->{
                e.setVisible(false);e.setGravity(false);e.setBasePlate(false);e.setArms(false);
                e.setPersistent(false);e.setCollidable(false);e.setInvulnerable(true);e.setSilent(true);
                e.getPersistentDataContainer().set(plugin.key("pet_ride_seat"),PersistentDataType.BYTE,(byte)1);
                e.getPersistentDataContainer().set(plugin.key("pet_body"),PersistentDataType.BYTE,(byte)1);
                for(EquipmentSlot slot:List.of(EquipmentSlot.HAND,EquipmentSlot.OFF_HAND,EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET))
                    for(ArmorStand.LockType lock:ArmorStand.LockType.values())e.addEquipmentLock(slot,lock);
            });
            if(!seat.isValid()||seat.isDead()){seat.remove();return "สร้างที่นั่งสัตว์ไม่ได้ · ระบบอื่นอาจกันการเกิด";}
            var handle=((CraftEntity)seat).getHandle();
            var rider=((CraftEntity)player).getHandle();
            double attachment=handle.getPassengerRidingPosition(rider).y-rider.getVehicleAttachmentPoint(handle).y-handle.getY();
            moveEntity(seat,at.clone().add(0,lift-attachment,0));
            Session session=new Session(body,seat,origin,JudgePackService.bedrock(player),lift,attachment);
            if(!seat.addPassenger(player)){seat.remove();return "ขึ้นขี่สัตว์ไม่ได้ · ลองอีกครั้งในที่โล่ง";}
            sessions.put(player.getUniqueId(),session);body.ridden=true;body.airborne=body.species!=PetCatalog.Species.FENRIR;body.target=null;
            companions.rideSeat(body,seat,true);
            body.speed=0;body.pitch=body.roll=body.headYaw=body.headPitch=0;
            session.exemption=exemption(player);
            if(session.bedrock)analog.mount(player);
            player.setFallDistance(0);player.closeInventory();
            if(body.species==PetCatalog.Species.AETHERIUS)blessing.tick(player);
            plugin.message(player,"ขึ้นขี่ "+record.name()+" แล้ว · ไม่ใช้มานา · "+(body.species==PetCatalog.Species.FENRIR
                ?"เดินเพื่อวิ่ง · กระโดดสูง · Sprint เร่ง · บินไม่ได้":"เดินเพื่อบิน · กระโดดขึ้น · ก้มแล้วเดินหน้าลง · Sprint เร่ง")+" · ย่องลงจากสัตว์");
            return null;
        }catch(RuntimeException error){
            if(riding(player.getUniqueId()))stop(player.getUniqueId(),true);
            else{body.ridden=false;body.airborne=WildPetSpawns.flies(body.species);if(seat!=null)seat.remove();}
            plugin.getLogger().log(java.util.logging.Level.WARNING,"Cannot mount mythical pet",error);
            return "ขึ้นขี่สัตว์ไม่ได้ · แจ้งแอดมินตรวจ console";
        }
    }
    boolean tick(Player player,PetBody body){
        Session session=sessions.get(player.getUniqueId());if(session==null)return false;
        if(session.body!=body||!body.valid()||!session.seat.isValid()||player.isDead()
            ||player.getGameMode()==GameMode.SPECTATOR||!plugin.getConfig().getBoolean("pets.riding.enabled",true)){
            stop(player.getUniqueId(),true);return false;
        }
        if(player.getWorld()!=session.seat.getWorld()){stop(player.getUniqueId(),false);return false;}
        if(player.getVehicle()!=session.seat){stop(player.getUniqueId(),true);return false;}
        Vector target=input(player,session);
        if(target.lengthSquared()>1e-6||session.jump)companions.ridingActivity(player);
        if(body.species==PetCatalog.Species.FENRIR)return groundTick(player,session,target);
        session.motion.multiply(.6).add(target.multiply(.4));
        if(session.motion.lengthSquared()<1e-6)session.motion.zero();
        Location from=body.location(),to=from.clone();
        int steps=Math.max(1,(int)Math.ceil(session.motion.length()/.2));
        Vector step=session.motion.clone().multiply(1d/steps);
        for(int i=0;i<steps;i++){
            Location full=to.clone().add(step);
            if(clear(full,session.lift,player)){to=full;continue;}
            for(int axis=0;axis<3;axis++){
                Vector part=new Vector(axis==0?step.getX():0,axis==1?step.getY():0,axis==2?step.getZ():0);
                if(clear(to.clone().add(part),session.lift,player))to.add(part);
            }
        }
        session.motion.copy(to.toVector().subtract(from.toVector()));
        body.yaw=body.targetYaw=player.getLocation().getYaw();body.pitch+=(float)(-session.motion.getY()*18-body.pitch)*.15;
        body.roll*=.8f;body.airborne=true;
        to.setYaw(body.yaw);to.setPitch(0);body.move(to);
        moveEntity(session.seat,to.clone().add(0,session.lift-session.attachment,0));
        body.present("fly");player.setFallDistance(0);
        if(body.species==PetCatalog.Species.AETHERIUS&&++session.ticks%20==0)blessing.tick(player);
        return true;
    }
    private boolean groundTick(Player player,Session session,Vector target){
        PetBody body=session.body;Location from=body.location(),to=from.clone();
        session.grounded=!clear(from.clone().subtract(0,.08,0),session.lift,player);
        if(session.grounded&&session.vertical<=0)session.vertical=0;
        if(session.jump&&!session.jumpHeld&&session.grounded)session.vertical=number("fenrir.jump-velocity",1.0,.5,1.2);
        session.jumpHeld=session.jump;
        session.motion.multiply(.6).add(target.multiply(.4));session.motion.setY(0);
        double dy=session.vertical;session.vertical=Math.max(-1.5,session.vertical-.08);
        Vector movement=session.motion.clone().setY(dy);
        int steps=Math.max(1,(int)Math.ceil(movement.length()/.15));Vector step=movement.multiply(1d/steps);
        for(int i=0;i<steps;i++){
            Location horizontal=to.clone().add(step.getX(),0,step.getZ());
            if(clear(horizontal,session.lift,player))to=horizontal;
            else if(session.grounded&&session.vertical<=0&&clear(horizontal.clone().add(0,1,0),session.lift,player))to=horizontal.add(0,1,0);
            else for(int axis=0;axis<2;axis++){
                Location slide=to.clone().add(axis==0?step.getX():0,0,axis==1?step.getZ():0);
                if(clear(slide,session.lift,player))to=slide;
            }
            Location vertical=to.clone().add(0,step.getY(),0);
            if(clear(vertical,session.lift,player))to=vertical;
            else{
                // Resolve the last fraction to the floor/ceiling instead of hovering above it.
                double low=0,high=1;
                for(int n=0;n<8;n++){double mid=(low+high)/2;if(clear(to.clone().add(0,step.getY()*mid,0),session.lift,player))low=mid;else high=mid;}
                to.add(0,step.getY()*low,0);session.vertical=0;step.setY(0);
            }
        }
        session.motion.copy(to.toVector().subtract(from.toVector())).setY(0);
        session.grounded=!clear(to.clone().subtract(0,.08,0),session.lift,player);
        // Unloaded terrain, hazards and the bottom of the world end a fall safely.
        if(to.getY()<=to.getWorld().getMinHeight()+1.1){stop(player.getUniqueId(),true);return false;}
        body.yaw=body.targetYaw=player.getLocation().getYaw();body.pitch+=(float)(Math.clamp(-session.vertical*10,-9,12)-body.pitch)*.2;
        body.roll=0;body.airborne=!session.grounded;to.setYaw(body.yaw);to.setPitch(0);body.move(to);
        moveEntity(session.seat,to.clone().add(0,session.lift-session.attachment,0));
        double speed=session.motion.length();body.present(speed>.015||!session.grounded?"walk":"idle",(float)Math.clamp(speed/.42,.7,2));
        player.setFallDistance(0);return true;
    }
    private Vector input(Player player,Session session){
        var controls=player.getCurrentInput();double f=(controls.isForward()?1:0)-(controls.isBackward()?1:0);
        double r=(controls.isRight()?1:0)-(controls.isLeft()?1:0);
        boolean jump=controls.isJump(),sprint=controls.isSprint()||player.isSprinting();
        double[] sampled=session.bedrock?analog.sample(player):null;
        if(sampled!=null){f=sampled[0];r=sampled[1];jump=sampled[2]>0;sprint=sampled[3]>0;}
        session.jump=jump;
        double yaw=Math.toRadians(player.getLocation().getYaw());
        Vector target=new Vector(-Math.sin(yaw)*f-Math.cos(yaw)*r,0,Math.cos(yaw)*f-Math.sin(yaw)*r);
        if(target.lengthSquared()>1)target.normalize();
        double boost=sprint?number("sprint-multiplier",1.5,1,2):1;
        if(session.body.species==PetCatalog.Species.FENRIR){
            target.multiply(number("fenrir.horizontal-speed",.7,.3,1)*boost);return target;
        }
        target.multiply(number("horizontal-speed",.486,.03,.8)*boost);
        double vertical=number("vertical-speed",.288,.03,.5)*boost;
        if(jump)target.setY(vertical);
        else if(f>0){
            double pitch=player.getLocation().getPitch();
            if(session.bedrock){
                double amount=Math.clamp((Math.abs(pitch)-12)/48,0,1);
                target.setY(-Math.signum(pitch)*amount*f*vertical);
            }else if(pitch>35)target.setY(-vertical);
        }
        return target;
    }
    private boolean clear(Location at,double lift,Player rider){
        World world=at.getWorld();double height=Math.max(2,lift+rider.getHeight()+.2);
        if(world==null||!Double.isFinite(at.getX())||!Double.isFinite(at.getY())||!Double.isFinite(at.getZ())
            ||at.getY()<world.getMinHeight()+1||at.getY()+height>=world.getMaxHeight()
            ||!PetTerrain.loadedFootprint(world,at.getBlockX(),at.getBlockZ()))return false;
        for(double x:new double[]{-.65,0,.65})for(double z:new double[]{-.65,0,.65}){
            Location corner=at.clone().add(x,0,z);if(!world.getWorldBorder().isInside(corner))return false;
            for(double y=.05;y<=height+.2;y+=.3){
                var block=world.getBlockAt(corner.clone().add(0,Math.min(y,height),0));
                if(!block.isPassable()||block.isLiquid()||PetTerrain.HAZARDS.contains(block.getType()))return false;
            }
        }
        return true;
    }
    private static void moveEntity(Entity entity,Location to){
        to.checkFinite();var handle=((CraftEntity)entity).getHandle();
        handle.setPos(to.getX(),to.getY(),to.getZ());entity.setRotation(to.getYaw(),to.getPitch());
    }
    void stop(UUID owner,boolean safe){
        Session session=sessions.remove(owner);if(session==null)return;
        Player player=Bukkit.getPlayer(owner);session.body.ridden=false;
        companions.rideSeat(session.body,session.seat,false);
        session.body.airborne=WildPetSpawns.flies(session.body.species);session.body.pitch=session.body.roll=0;
        if(session.exemption!=null){try{session.exemption.remove();}catch(IllegalArgumentException ignored){}}
        if(player!=null){
            blessing.stop(player);
            analog.unmount(player);
            Location exit=player.getLocation();exits.put(owner,exit);
            if(player.getVehicle()==session.seat)session.seat.removePassenger(player);
            if(safe&&player.isOnline()&&!player.isDead()){
                double floor=PetTerrain.groundBelow(exit,128);
                if(Double.isNaN(floor)){
                    Location to=session.origin;
                    if(Double.isNaN(PetTerrain.groundBelow(to,8))||!playerSpace(to))to=to.getWorld().getSpawnLocation();
                    player.teleport(to);
                    plugin.message(player,"ด้านล่างเป็น Void · ส่งกลับจุดขึ้นขี่อย่างปลอดภัยแล้ว");
                }else player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING,1200,0,true,false,false));
                player.setFallDistance(0);
            }
            if(!closed)Bukkit.getScheduler().runTaskLater(plugin,()->exits.remove(owner,exit),2);
        }
        session.seat.remove();
    }
    private boolean playerSpace(Location at){
        return at.getWorld().isChunkLoaded(at.getBlockX()>>4,at.getBlockZ()>>4)
            &&at.getWorld().getBlockAt(at).isPassable()&&at.getWorld().getBlockAt(at.clone().add(0,1,0)).isPassable();
    }
    private PermissionAttachment exemption(Player player){
        var section=plugin.getConfig().getConfigurationSection("pets.riding.anticheat.bypass-permissions");
        PermissionAttachment attachment=null;if(section==null)return null;
        for(String name:section.getKeys(false))if(Bukkit.getPluginManager().isPluginEnabled(name))
            for(String permission:section.getStringList(name))if(permission.matches("[a-zA-Z0-9_.-]+")){
                if(attachment==null)attachment=player.addAttachment(plugin);attachment.setPermission(permission,true);
            }
        return attachment;
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void dismount(EntityDismountEvent event){
        if(!(event.getEntity() instanceof Player player))return;
        Session session=sessions.get(player.getUniqueId());
        if(session==null||session.seat!=event.getDismounted())return;
        Location at=player.getLocation();exits.put(player.getUniqueId(),at);
        Bukkit.getScheduler().runTask(plugin,()->{
            if(sessions.get(player.getUniqueId())==session&&player.getVehicle()!=session.seat)stop(player.getUniqueId(),true);
        });
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void localExit(PlayerTeleportEvent event){
        if(event.getCause()!=PlayerTeleportEvent.TeleportCause.DISMOUNT||event.getTo()==null)return;
        Location at=exits.get(event.getPlayer().getUniqueId());
        if(at!=null&&event.getFrom().getWorld()==at.getWorld()&&event.getTo().getWorld()==at.getWorld()
            &&event.getFrom().distanceSquared(at)<16&&event.getTo().distanceSquared(at)<16)event.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void teleport(PlayerTeleportEvent event){
        if(event.getCause()!=PlayerTeleportEvent.TeleportCause.DISMOUNT)stop(event.getPlayer().getUniqueId(),false);
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void protect(EntityDamageEvent event){
        if(event.getEntity().getPersistentDataContainer().has(plugin.key("pet_ride_seat"),PersistentDataType.BYTE))event.setCancelled(true);
    }
    @EventHandler public void quit(PlayerQuitEvent event){stop(event.getPlayer().getUniqueId(),true);exits.remove(event.getPlayer().getUniqueId());}
    @EventHandler public void death(PlayerDeathEvent event){stop(event.getEntity().getUniqueId(),false);}
    @Override public void close(){closed=true;for(UUID owner:new ArrayList<>(sessions.keySet()))stop(owner,true);blessing.close();analog.close();exits.clear();HandlerList.unregisterAll(this);}

    /** Reuse the staff's optional Bedrock analog observer without requiring Advance Magic to be installed. */
    private static final class Analog {
        Object observer;Method mount,unmount,sample,forward,left,jump,sprint;
        boolean initialized;
        void mount(Player player){
            try{
                if(!initialized){
                    initialized=true;var magic=Bukkit.getPluginManager().getPlugin("advance-magic");
                    if(magic==null)return;
                    Class<?> type=Class.forName("com.example.advancemagic.item.FlyingStaffBedrockInput",true,magic.getClass().getClassLoader());
                    observer=type.getConstructor(magic.getClass()).newInstance(magic);
                    mount=type.getMethod("mount",Player.class);unmount=type.getMethod("unmount",Player.class);sample=type.getMethod("sample",Player.class);
                    Class<?> snapshot=sample.getReturnType();forward=snapshot.getMethod("forward");left=snapshot.getMethod("left");
                    jump=snapshot.getMethod("jump");sprint=snapshot.getMethod("sprint");
                }
                if(observer!=null)mount.invoke(observer,player);
            }catch(ReflectiveOperationException|LinkageError ignored){observer=null;}
        }
        double[] sample(Player player){try{
            if(observer==null)return null;Object value=sample.invoke(observer,player);if(value==null)return null;
            return new double[]{(double)forward.invoke(value),-(double)left.invoke(value),(boolean)jump.invoke(value)?1:0,(boolean)sprint.invoke(value)?1:0};
        }catch(ReflectiveOperationException ignored){return null;}}
        void unmount(Player player){try{if(observer!=null)unmount.invoke(observer,player);}catch(ReflectiveOperationException ignored){}}
        void close(){try{if(observer instanceof AutoCloseable closeable)closeable.close();}catch(Exception ignored){}}
    }
}
