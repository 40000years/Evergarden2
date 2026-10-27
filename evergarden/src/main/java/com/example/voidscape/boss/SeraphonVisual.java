package com.example.voidscape.boss;

import com.example.voidscape.VoidscapePlugin;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;
import java.util.*;

/** The existing six-wing rig, with one clock shared by its body, hitbox and both clients. */
final class SeraphonVisual implements AutoCloseable {
    private final VoidscapePlugin plugin;private final Slime anchor;
    private final ArkhaiaModelSupport.Rig rig;private final ArmorStand carrier;
    private final BossAura aura;private final ArkhaiaLook look=new ArkhaiaLook();
    private String clip="";private double time;
    SeraphonVisual(VoidscapePlugin plugin,Slime anchor){
        this.plugin=plugin;this.anchor=anchor;aura=new BossAura(plugin,BossAura.Style.SERAPHON);
        rig=plugin.arkhaiaModels()==null?null:plugin.arkhaiaModels().attachBoss(anchor,"seraphon",SeraphonRules.SCALE);
        var bridge=plugin.arkhaiaBedrock();
        carrier=bridge!=null&&bridge.ready()?anchor.getWorld().spawn(anchor.getLocation(),ArmorStand.class,e->{
            e.setPersistent(false);e.setGravity(false);e.setInvulnerable(true);e.setSilent(true);e.setVisibleByDefault(false);
            e.setVisible(true);e.setBasePlate(false);e.setCollidable(false);bridge.register(e.getUniqueId(),"seraphon");
            e.getPersistentDataContainer().set(plugin.key("world_boss_entity"),PersistentDataType.BYTE,(byte)1);
            for(var slot:EquipmentSlot.values())e.addEquipmentLock(slot,ArmorStand.LockType.ADDING_OR_CHANGING);
        }):null;
    }
    void frame(Location at,Player target,String next,double rate,boolean intense){
        if(!clip.equals(next)){clip=next;time=0;}else time+=rate/20;
        var handle=((org.bukkit.craftbukkit.entity.CraftEntity)anchor).getHandle();handle.setPos(at.getX(),at.getY(),at.getZ());anchor.setRotation(at.getYaw(),0);
        var gaze=look.update(at.getYaw(),target==null?0:target.getX()-at.getX(),target==null?0:target.getEyeLocation().getY()-at.getY()-6.9,
            target==null?0:target.getZ()-at.getZ(),target!=null,clip.equals("stun"));
        Set<UUID> accepted=new HashSet<>();List<Player> viewers=new ArrayList<>();
        for(Player p:at.getWorld().getPlayers()){
            boolean near=Math.hypot(p.getX()-at.getX(),p.getZ()-at.getZ())<110,bedrock=JudgePackService.bedrock(p);
            if(near)viewers.add(p);if(near&&!bedrock&&plugin.judgePack().applied(p))accepted.add(p.getUniqueId());
            if(carrier!=null){if(near&&bedrock){if(!p.canSee(carrier))p.showEntity(plugin,carrier);}else if(p.canSee(carrier))p.hideEntity(plugin,carrier);}
        }
        if(rig!=null){rig.viewers(accepted);rig.look(gaze.yaw(),gaze.pitch());rig.clip(clip,rate,at);}
        double length=switch(clip){case "idle"->3;case "walk"->1.6;case "hover"->1.2;case "spawn"->3;case "transition"->3.5;case "stun"->1.8;
            default->clip.startsWith("verdict")?(clip.endsWith("windup")?2:clip.endsWith("active")?1.2:1.4):clip.startsWith("smite")?(clip.endsWith("windup")?.8:clip.endsWith("active")?.5:.9):clip.startsWith("wings")?(clip.endsWith("windup")?.9:clip.endsWith("active")?.5:.8):(clip.endsWith("windup")?1:clip.endsWith("active")?.6:.8);};
        double sampled=Set.of("idle","walk","hover").contains(clip)?time%length:Math.min(time,length);
        if(carrier!=null){var body=((org.bukkit.craftbukkit.entity.CraftEntity)carrier).getHandle();body.setPos(at.getX(),at.getY(),at.getZ());carrier.setRotation(at.getYaw(),0);
            plugin.arkhaiaBedrock().riverFrame(carrier.getUniqueId(),clip,(float)sampled,(float)rate,gaze,viewers);}
        aura.frame(at,clip,sampled,Bukkit.getCurrentTick(),SeraphonRules.SCALE,intense);
    }
    String clip(){return clip.isEmpty()?"idle":clip;}
    double time(){return time;}
    boolean owns(Entity e){return e==carrier;}
    boolean valid(){return anchor.isValid()&&(carrier==null||carrier.isValid());}
    @Override public void close(){if(rig!=null)rig.close();aura.close();if(carrier!=null){plugin.arkhaiaBedrock().unregister(carrier.getUniqueId());carrier.remove();}}
}
