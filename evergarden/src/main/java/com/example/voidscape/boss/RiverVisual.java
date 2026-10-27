package com.example.voidscape.boss;

import com.example.voidscape.VoidscapePlugin;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;
import java.util.*;

/** One Java ME model and one native Bedrock carrier share body/gaze/animation clocks. */
final class RiverVisual implements AutoCloseable {
    static final double SCALE=4.0;
    static double scale(ArkhaiaWaves.Kind kind){
        return kind == ArkhaiaWaves.Kind.MALEFEASANCE ? 2.0 : SCALE;
    }
    static double scale(String model){
        return (model != null && model.contains("malefeasance")) ? 2.0 : SCALE;
    }
    // Sized from the approved rigs at scale 1; leave air above crowns and horns.
    static double haloHeight(ArkhaiaWaves.Kind kind){
        return scale(kind)*switch(kind){
            case ACHERON,MALEFEASANCE->2.725;case PHLEGETHON->2.65;
            case COCYTUS->2.975;case BANSHEE_WAIL->3.075;case STYX->2.9;
        };
    }
    static double haloRadius(ArkhaiaWaves.Kind kind){
        return scale(kind)*switch(kind){
            case ACHERON->.83;case PHLEGETHON->.97;case COCYTUS->.84;
            case BANSHEE_WAIL->1;case MALEFEASANCE->1.09;case STYX->1.01;
        };
    }
    private final VoidscapePlugin plugin;
    private final Mob mob;
    private final ArkhaiaWaves.Kind kind;
    private ArkhaiaModelSupport.Rig rig;
    private final ArmorStand carrier;
    private final ArkhaiaLook look=new ArkhaiaLook();
    private final double eyeHeight;
    private Location previous;
    private String clip="";
    private double clock;
    private float yaw;
    private boolean closed;
    RiverVisual(VoidscapePlugin plugin,Mob mob,ArkhaiaWaves.Kind kind){
        this(plugin,mob,kind,false);
    }
    RiverVisual(VoidscapePlugin plugin,Mob mob,ArkhaiaWaves.Kind kind,boolean echo){
        this.plugin=plugin;this.mob=mob;this.kind=kind;previous=mob.getLocation();yaw=previous.getYaw();
        eyeHeight=scale(kind)*(echo?.6:1)*(kind==ArkhaiaWaves.Kind.PHLEGETHON?31.5/16:kind==ArkhaiaWaves.Kind.BANSHEE_WAIL?37.2/16:34.0/16);
        String model=echo?"arkhaia_acheron_echo":"arkhaia_"+kind.name().toLowerCase(Locale.ROOT);
        rig=Objects.requireNonNull(plugin.arkhaiaModels().attachRiver(mob,model),"River model unavailable");
        ArmorStand spawned=null;
        try{
            if(plugin.arkhaiaBedrock()!=null&&plugin.arkhaiaBedrock().ready())spawned=mob.getWorld().spawn(previous,ArmorStand.class,e->{
                e.setPersistent(false);e.setGravity(false);e.setInvulnerable(true);e.setSilent(true);e.setVisibleByDefault(false);
                e.setVisible(true);e.setBasePlate(false);e.setCollidable(false);
                e.getPersistentDataContainer().set(plugin.key("world_boss_entity"),PersistentDataType.BYTE,(byte)1);
                plugin.arkhaiaBedrock().register(e.getUniqueId(),model);
                for(var slot:EquipmentSlot.values())e.addEquipmentLock(slot,ArmorStand.LockType.ADDING_OR_CHANGING);
            });
            mob.setInvisible(true);mob.setCustomNameVisible(false);mob.getEquipment().clear();
        }catch(RuntimeException error){if(spawned!=null){plugin.arkhaiaBedrock().unregister(spawned.getUniqueId());spawned.remove();}rig.close();throw error;}
        carrier=spawned;
    }
    void switchModel(String newModelId){
        if(closed)return;
        rig.close();
        rig=java.util.Objects.requireNonNull(plugin.arkhaiaModels().attachRiver(mob,newModelId),"River model unavailable: "+newModelId);
        clip="";
        if(carrier!=null&&plugin.arkhaiaBedrock()!=null&&plugin.arkhaiaBedrock().ready()){
            plugin.arkhaiaBedrock().register(carrier.getUniqueId(),newModelId);
            // Geyser picks the entity definition at spawn: resend the carrier so Bedrock viewers get the new rig.
            for(Player p:carrier.getWorld().getPlayers())if(p.canSee(carrier)){p.hideEntity(plugin,carrier);p.showEntity(plugin,carrier);}
        }
    }

    boolean owns(Entity e){return e==carrier;}
    boolean valid(){return !closed&&(carrier==null||carrier.isValid());}
    double speed(){Location at=mob.getLocation();return previous.getWorld()==at.getWorld()?Math.hypot(at.getX()-previous.getX(),at.getZ()-previous.getZ()):0;}
    Location weaponOrigin(){
        Location nativeHand=rig.boneOrigin("weapon_l");
        // A culled/new R4 bone can still report the receiver's feet before its
        // first evaluated pose. Never launch into the co-located visual carrier.
        if(nativeHand!=null&&nativeHand.getWorld()==mob.getWorld()&&nativeHand.getY()-mob.getY()>3
                &&nativeHand.distanceSquared(mob.getLocation())<16*16)return nativeHand;
        // Bow hand at full draw, in the same local axes as the authored rig.
        double angle=Math.toRadians(yaw);
        // ME's native -Z front maps to Bukkit's +Z at yaw zero.
        double x=1.125,z=4.375;
        return mob.getLocation().add(x*Math.cos(angle)-z*Math.sin(angle),7.25,z*Math.cos(angle)+x*Math.sin(angle));
    }
    Location castOrigin(){
        Location point=rig.boneOrigin(kind==ArkhaiaWaves.Kind.BANSHEE_WAIL?"head_forward":"hand_l");
        if(point!=null&&point.getWorld()==mob.getWorld()&&point.getY()>mob.getY()+2&&point.distanceSquared(mob.getLocation())<16*16)return point;
        return mob.getLocation().add(0,kind==ArkhaiaWaves.Kind.BANSHEE_WAIL?8:5,0);
    }
    void frame(Player target,String requested,double rate,boolean locked){
        if(closed)return;Location at=mob.getLocation();
        double dx=at.getX()-previous.getX(),dz=at.getZ()-previous.getZ(),moved=Math.hypot(dx,dz);
        if(locked&&!clip.equals(requested)&&requested.endsWith("_windup")&&target!=null)
            yaw=(float)Math.toDegrees(Math.atan2(at.getX()-target.getX(),target.getZ()-at.getZ()));
        if(!locked){
            if(moved>.008&&moved<1)yaw=ArkhaiaSteering.facingYaw(yaw,dx,dz);
            else if(target!=null)yaw=ArkhaiaSteering.facingYaw(yaw,target.getX()-at.getX(),target.getZ()-at.getZ());
        }
        mob.setRotation(yaw,0);at.setYaw(yaw);at.setPitch(0);
        ArkhaiaLook.Gaze gaze=look.update(yaw,target==null?0:target.getX()-at.getX(),target==null?0:target.getEyeLocation().getY()-at.getY()-eyeHeight,
            target==null?0:target.getZ()-at.getZ(),target!=null,requested.equals("stun"));
        if(!clip.equals(requested)){clip=requested;clock=0;}else clock+=rate/20;
        double length=switch(clip){case "idle"->2.4;case "walk"->kind==ArkhaiaWaves.Kind.COCYTUS?1.2:1;case "hover"->2;case "cast_windup","ritual_windup","kick_windup","throw_windup","curse_windup","reveal","reform"->1;case "ritual_active","kick_active","throw_active","curse_active"->.5;case "cast_active"->kind==ArkhaiaWaves.Kind.ACHERON?1.2:.5;case "attack_windup"->.8;case "attack_active"->.4;case "cast_recover","attack_recover","ritual_recover","kick_recover","throw_recover","curse_recover"->.7;default->1.5;};
        if(clip.startsWith("slash_")||clip.startsWith("reverse_")||clip.startsWith("thrust_")||clip.startsWith("dash_"))
            length=clip.endsWith("windup")?1:clip.endsWith("active")?(clip.startsWith("dash_")?.6:.4):.7;
        if(clip.startsWith("rain_"))length=clip.endsWith("windup")?1:clip.endsWith("active")?.5:.7;
        if(clip.startsWith("sky_")||clip.startsWith("portal_"))length=clip.endsWith("windup")?1:clip.endsWith("active")?.5:.7;
        if(clip.equals("rebirth"))length=2;
        if(clip.startsWith("blood_")||clip.startsWith("berserk_"))length=clip.endsWith("windup")?1:clip.endsWith("active")?.5:.7;
        boolean loop=Set.of("idle","walk","hover").contains(clip);float time=(float)(loop?clock%length:Math.min(length,clock));
        Set<UUID> accepted=new HashSet<>();List<Player> viewers=new ArrayList<>();
        for(Player p:at.getWorld().getPlayers()){
            boolean near=p.getLocation().distanceSquared(at)<80*80;
            boolean bedrock=JudgePackService.bedrock(p);
            if(near)viewers.add(p);
            if(near&&!bedrock&&plugin.judgePack().applied(p))accepted.add(p.getUniqueId());
            if(carrier!=null){if(near&&bedrock){if(!p.canSee(carrier))p.showEntity(plugin,carrier);}else if(p.canSee(carrier))p.hideEntity(plugin,carrier);}
        }
        rig.viewers(accepted);rig.look(gaze.yaw(),gaze.pitch());rig.clip(clip,rate,at);
        if(carrier!=null){
            var handle=((org.bukkit.craftbukkit.entity.CraftEntity)carrier).getHandle();handle.setPos(at.getX(),at.getY(),at.getZ());carrier.setRotation(yaw,0);
            plugin.arkhaiaBedrock().riverFrame(carrier.getUniqueId(),clip,time,(float)rate,gaze,viewers);
        }
        previous=at;
    }
    @Override public void close(){if(closed)return;closed=true;rig.close();if(carrier!=null){plugin.arkhaiaBedrock().unregister(carrier.getUniqueId());carrier.remove();}}
}
