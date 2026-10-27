package com.example.voidscape.boss;

import com.example.voidscape.VoidscapePlugin;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;
import java.util.*;

/** Admin-only, harmless showcase of the Evergarden main-boss rigs (Arkhaia, Seraphon):
 *  the real ModelEngine model for Java, the native Geyser entity for Bedrock, the
 *  aura lane and particle aura, cycling through authored clips. Never deals damage. */
final class BossShowcase implements AutoCloseable {
    enum Kind {
        ARKHAIA("arkhaia","Arkhaia",3.2,6.95,BossAura.Style.ARKHAIA),
        SERAPHON("seraphon","Seraphon",3.2,6.9,BossAura.Style.SERAPHON);
        final String model,title;final double scale,eye;final BossAura.Style aura;
        Kind(String model,String title,double scale,double eye,BossAura.Style aura){this.model=model;this.title=title;this.scale=scale;this.eye=eye;this.aura=aura;}
    }
    static final List<String> MODES=List.of("showcase","idle","walk","hover","attack","spell","ultimate","stun","phase");
    private static final int LIFETIME=20*600;
    /** Arkhaia's airborne clips; the real encounter lifts the body while they play. */
    private static final Set<String> AIRBORNE=Set.of("hover","portals_windup","portals_active","eclipse_windup","eclipse_active");
    private record Step(String clip,double seconds){}
    private static final class Show {
        final UUID admin;final Kind kind;final Slime anchor;final ArmorStand carrier;final ArkhaiaModelSupport.Rig rig;final BossAura aura;
        final ArkhaiaLook look=new ArkhaiaLook();final Location home;final long created;
        List<Step> program;int step;double clock;String mode="showcase",clip="";float yaw;double lift;
        Show(UUID admin,Kind kind,Slime anchor,ArmorStand carrier,ArkhaiaModelSupport.Rig rig,BossAura aura,Location home,long created){
            this.admin=admin;this.kind=kind;this.anchor=anchor;this.carrier=carrier;this.rig=rig;this.aura=aura;this.home=home;this.created=created;yaw=home.getYaw();
        }
    }
    private final VoidscapePlugin plugin;
    private final Map<UUID,EnumMap<Kind,Show>> active=new HashMap<>();
    private final org.bukkit.scheduler.BukkitTask driver;
    private long tick;
    BossShowcase(VoidscapePlugin plugin){this.plugin=plugin;driver=Bukkit.getScheduler().runTaskTimer(plugin,this::tick,1,1);}

    private static List<Step> cast(String prefix,List<Double> lengths){
        return List.of(new Step(prefix+"_windup",lengths.get(0)),new Step(prefix+"_active",lengths.get(1)),new Step(prefix+"_recover",lengths.get(2)));
    }
    private static List<Step> arkhaia(String action){
        var t=ArkhaiaCombatTiming.of(ArkhaiaBrain.Action.valueOf(action.toUpperCase(Locale.ROOT)));
        return cast(action,List.of(t.windup()/20.0,t.active()/20.0,t.recovery()/20.0));
    }
    private static List<Step> program(Kind kind,String mode){
        var steps=new ArrayList<Step>();
        if(kind==Kind.ARKHAIA)switch(mode){
            case "idle"->steps.add(new Step("idle",2.4));
            case "walk"->steps.add(new Step("walk",.8));
            case "hover"->steps.add(new Step("hover",1.4));
            case "attack"->{steps.addAll(arkhaia("light"));steps.add(new Step("idle",1.2));steps.addAll(arkhaia("heavy"));steps.add(new Step("idle",1.2));}
            case "spell"->{steps.addAll(arkhaia("rift"));steps.add(new Step("idle",1.0));steps.addAll(arkhaia("nova"));steps.add(new Step("idle",1.0));steps.addAll(arkhaia("portals"));steps.add(new Step("idle",1.0));}
            case "ultimate"->{steps.addAll(arkhaia("eclipse"));steps.add(new Step("idle",1.6));}
            case "stun"->{steps.add(new Step("stun",ArkhaiaCombatTiming.STUN/20.0));steps.add(new Step("idle",1.4));}
            case "phase"->{steps.add(new Step("transition",3.5));steps.add(new Step("idle",1.6));}
            default->{
                steps.add(new Step("idle",4.8));steps.add(new Step("walk",3.2));steps.addAll(arkhaia("light"));steps.add(new Step("idle",1.2));
                steps.addAll(arkhaia("rift"));steps.add(new Step("hover",2.8));steps.addAll(arkhaia("eclipse"));
                steps.addAll(arkhaia("nova"));steps.add(new Step("transition",3.5));steps.add(new Step("stun",ArkhaiaCombatTiming.STUN/20.0));
            }
        }else switch(mode){
            case "idle"->steps.add(new Step("idle",3.0));
            case "walk"->steps.add(new Step("walk",1.6));
            case "hover"->steps.add(new Step("hover",1.2));
            case "attack"->{steps.addAll(cast("cast",List.of(1.0,.6,.8)));steps.add(new Step("idle",1.2));steps.addAll(cast("smite",List.of(.8,.5,.9)));steps.add(new Step("idle",1.2));}
            case "spell"->{steps.addAll(cast("wings",List.of(.9,.5,.8)));steps.add(new Step("idle",1.2));steps.addAll(cast("cast",List.of(1.0,.6,.8)));steps.add(new Step("idle",1.2));}
            case "ultimate"->{steps.addAll(cast("verdict",List.of(2.0,1.2,1.4)));steps.add(new Step("idle",1.6));}
            case "stun"->{steps.add(new Step("stun",1.8));steps.add(new Step("idle",1.4));}
            case "phase"->{steps.add(new Step("transition",3.5));steps.add(new Step("idle",1.6));}
            default->{
                steps.add(new Step("spawn",3.0));steps.add(new Step("idle",4.5));steps.add(new Step("walk",3.2));
                steps.addAll(cast("cast",List.of(1.0,.6,.8)));steps.add(new Step("idle",1.0));steps.addAll(cast("smite",List.of(.8,.5,.9)));
                steps.add(new Step("idle",1.0));steps.addAll(cast("wings",List.of(.9,.5,.8)));steps.add(new Step("hover",2.4));
                steps.addAll(cast("verdict",List.of(2.0,1.2,1.4)));steps.add(new Step("transition",3.5));steps.add(new Step("stun",1.8));
            }
        }
        return steps;
    }
    private boolean admin(Player p){return p!=null&&p.isOnline()&&(p.isOp()||p.hasPermission("evergarden.admin")||p.hasPermission("voidscape.admin"));}
    boolean owns(Entity e){
        for(var shows:active.values())for(Show s:shows.values())if(s.anchor==e||s.carrier==e)return true;
        return false;
    }
    private void tag(Entity e){
        e.setPersistent(false);
        e.getPersistentDataContainer().set(plugin.key("world_boss_entity"),PersistentDataType.BYTE,(byte)1);
        e.getPersistentDataContainer().set(plugin.key("boss_showcase"),PersistentDataType.BYTE,(byte)1);
    }
    /** Spawn one rig (or both, side by side) in front of the admin. */
    boolean spawn(Player p,List<Kind> kinds){
        if(!admin(p)||p.isDead())return false;
        if(plugin.arkhaiaModels()==null||kinds.stream().anyMatch(k->!plugin.arkhaiaModels().ready(k.model))){
            plugin.message(p,"Model Engine กำลังเตรียมโมเดลบอส กรุณารอสักครู่ · /evergarden boss pack");return false;
        }
        if(!JudgePackService.bedrock(p)&&!plugin.judgePack().applied(p)){plugin.judgePack().offer(p);plugin.message(p,"รับแพ็ก World Boss ให้เสร็จ แล้วกดเสกอีกครั้ง");return false;}
        var forward=p.getLocation().getDirection().setY(0);if(forward.lengthSquared()<1e-6)forward=new org.bukkit.util.Vector(0,0,1);forward.normalize();
        var right=new org.bukkit.util.Vector(-forward.getZ(),0,forward.getX());
        List<Location> spots=new ArrayList<>();
        for(int i=0;i<kinds.size();i++){
            Location at=p.getLocation().clone().add(forward.clone().multiply(12)).add(right.clone().multiply(kinds.size()==1?0:(i==0?-8:8)));
            at.setY(Math.floor(at.getY()));at.setPitch(0);
            at.setDirection(p.getLocation().toVector().subtract(at.toVector()).setY(0));
            boolean clear=at.clone().subtract(0,.1,0).getBlock().getType().isSolid();
            for(int y=0;y<10&&clear;y++)for(int x=-2;x<=2&&clear;x++)for(int z=-2;z<=2;z++)if(!at.clone().add(x,y,z).getBlock().isPassable()){clear=false;break;}
            if(!clear){plugin.message(p,"ต้องการพื้นราบและที่ว่างสูง 10 บล็อก ห่างไปข้างหน้า 12 บล็อก"+(kinds.size()>1?" ทั้งซ้ายและขวา":""));return false;}
            spots.add(at);
        }
        var shows=active.computeIfAbsent(p.getUniqueId(),k->new EnumMap<>(Kind.class));
        for(int i=0;i<kinds.size();i++){
            Kind kind=kinds.get(i);Show old=shows.remove(kind);if(old!=null)destroy(old);
            try{shows.put(kind,create(p,kind,spots.get(i)));}
            catch(RuntimeException|LinkageError error){
                plugin.getLogger().log(java.util.logging.Level.WARNING,"Cannot spawn "+kind.title+" showcase",error);
                plugin.message(p,"เสกโมเดล "+kind.title+" ไม่สำเร็จ · ตรวจ server log");return false;
            }
        }
        p.getWorld().playSound(p.getLocation(),Sound.BLOCK_BEACON_ACTIVATE,1,.8f);
        plugin.message(p,"เสกโมเดลบอสหลักแล้ว · เลือกท่าได้ในเมนู World Boss Test หรือ /evergarden boss model pose <ท่า> · อยู่ได้ 10 นาที");
        return true;
    }
    private Show create(Player owner,Kind kind,Location at){
        Slime anchor=null;ArmorStand carrier=null;ArkhaiaModelSupport.Rig rig=null;
        try{
            anchor=at.getWorld().spawn(at,Slime.class,e->{
                tag(e);e.setSize(1);e.setAI(false);e.setSilent(true);e.setInvisible(true);e.setInvulnerable(true);
                e.setGravity(false);e.setCollidable(false);e.setRemoveWhenFarAway(false);
                e.addScoreboardTag("evergarden_unique");com.example.voidscape.compat.LevelledMobsCompat.tagMob(e,plugin,true);
            });
            rig=java.util.Objects.requireNonNull(plugin.arkhaiaModels().attachBoss(anchor,kind.model,kind.scale),"rig unavailable");
            var bedrock=plugin.arkhaiaBedrock();
            if(bedrock!=null&&bedrock.ready())carrier=at.getWorld().spawn(at,ArmorStand.class,e->{
                tag(e);e.setGravity(false);e.setInvulnerable(true);e.setSilent(true);e.setVisibleByDefault(false);
                e.setVisible(true);e.setBasePlate(false);e.setCollidable(false);
                bedrock.register(e.getUniqueId(),kind.model);
                for(var slot:EquipmentSlot.values())e.addEquipmentLock(slot,ArmorStand.LockType.ADDING_OR_CHANGING);
            });
            Show show=new Show(owner.getUniqueId(),kind,anchor,carrier,rig,new BossAura(plugin,kind.aura),at.clone(),tick);
            show.program=program(kind,"showcase");return show;
        }catch(RuntimeException|LinkageError error){
            if(rig!=null)rig.close();
            if(carrier!=null){if(plugin.arkhaiaBedrock()!=null)plugin.arkhaiaBedrock().unregister(carrier.getUniqueId());carrier.remove();}
            if(anchor!=null)anchor.remove();
            throw error;
        }
    }
    void mode(Player p,String mode){
        if(!admin(p)||!MODES.contains(mode))return;
        var shows=active.get(p.getUniqueId());
        if(shows==null||shows.isEmpty()){plugin.message(p,"ยังไม่ได้เสกโมเดลบอสหลัก");return;}
        for(Show s:shows.values()){s.mode=mode;s.program=program(s.kind,mode);s.step=0;s.clock=0;}
        plugin.message(p,"เปลี่ยนท่าโมเดลบอสหลัก: "+mode);
    }
    boolean remove(Player p){
        if(!admin(p))return false;
        var shows=active.remove(p.getUniqueId());if(shows==null||shows.isEmpty())return false;
        shows.values().forEach(this::destroy);return true;
    }
    void quit(Player p){var shows=active.remove(p.getUniqueId());if(shows!=null)shows.values().forEach(this::destroy);}
    private void destroy(Show s){
        s.rig.close();s.aura.close();
        if(s.carrier!=null){if(plugin.arkhaiaBedrock()!=null)plugin.arkhaiaBedrock().unregister(s.carrier.getUniqueId());s.carrier.remove();}
        s.anchor.remove();
    }
    private void tick(){
        tick++;
        for(var it=active.entrySet().iterator();it.hasNext();){
            var entry=it.next();Player owner=Bukkit.getPlayer(entry.getKey());
            for(var shows=entry.getValue().values().iterator();shows.hasNext();){
                Show s=shows.next();
                boolean gone=owner==null||!owner.isOnline()||owner.getWorld()!=s.home.getWorld()||owner.getLocation().distanceSquared(s.home)>96*96
                    ||tick-s.created>=LIFETIME||!s.anchor.isValid()||s.carrier!=null&&!s.carrier.isValid();
                if(gone){destroy(s);shows.remove();continue;}
                try{frame(s,owner);}catch(RuntimeException error){
                    plugin.getLogger().log(java.util.logging.Level.WARNING,"Boss showcase stopped",error);destroy(s);shows.remove();
                }
            }
            if(entry.getValue().isEmpty())it.remove();
        }
    }
    private void frame(Show s,Player owner){
        Step step=s.program.get(s.step);
        s.clock+=1/20.0;
        if(s.clock>=step.seconds()){s.clock-=step.seconds();s.step=(s.step+1)%s.program.size();step=s.program.get(s.step);}
        s.clip=step.clip();
        Location at=s.home.clone();
        double dx=owner.getX()-at.getX(),dz=owner.getZ()-at.getZ();
        // Face the admin between actions, as the real encounters do while orbiting.
        if(!s.clip.contains("_active")&&Math.hypot(dx,dz)>.5)s.yaw=ArkhaiaSteering.facingYaw(s.yaw,dx,dz);
        double lift=s.kind==Kind.ARKHAIA&&AIRBORNE.contains(s.clip)?.9:0;
        s.lift+=Math.clamp((lift-s.lift)*.15,-.08,.08);
        at.add(0,s.lift,0);at.setYaw(s.yaw);at.setPitch(0);
        var handle=((org.bukkit.craftbukkit.entity.CraftEntity)s.anchor).getHandle();handle.setPos(at.getX(),at.getY(),at.getZ());
        s.anchor.setRotation(s.yaw,0);
        var gaze=s.look.update(s.yaw,dx,owner.getEyeLocation().getY()-at.getY()-s.kind.eye,dz,true,s.clip.equals("stun"));
        Set<UUID> accepted=new HashSet<>();List<Player> viewers=new ArrayList<>();
        for(Player viewer:at.getWorld().getPlayers()){
            boolean near=viewer.getLocation().distanceSquared(at)<80*80,bedrock=JudgePackService.bedrock(viewer);
            if(near)viewers.add(viewer);
            if(near&&!bedrock&&plugin.judgePack().applied(viewer))accepted.add(viewer.getUniqueId());
            if(s.carrier!=null){if(near&&bedrock){if(!viewer.canSee(s.carrier))viewer.showEntity(plugin,s.carrier);}else if(viewer.canSee(s.carrier))viewer.hideEntity(plugin,s.carrier);}
        }
        s.rig.viewers(accepted);s.rig.look(gaze.yaw(),gaze.pitch());
        s.rig.clip(s.clip,1,at);
        if(s.carrier!=null){
            var carrier=((org.bukkit.craftbukkit.entity.CraftEntity)s.carrier).getHandle();carrier.setPos(at.getX(),at.getY(),at.getZ());
            s.carrier.setRotation(s.yaw,0);
            plugin.arkhaiaBedrock().riverFrame(s.carrier.getUniqueId(),s.clip,(float)s.clock,1,gaze,viewers);
        }
        boolean intense=s.clip.contains("_active")||s.clip.equals("transition")||s.clip.startsWith("verdict")||s.clip.startsWith("eclipse");
        s.aura.frame(at,s.clip,s.clock,tick,s.kind.scale,intense);
    }
    @Override public void close(){driver.cancel();for(var shows:active.values())shows.values().forEach(this::destroy);active.clear();}
}
