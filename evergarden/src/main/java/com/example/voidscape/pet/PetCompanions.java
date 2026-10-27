package com.example.voidscape.pet;

import com.example.voidscape.VoidscapePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import java.time.Duration;
import java.util.*;

/**
 * Owned pets. Each player keeps a stable of records; at most one companion is in the world,
 * following its owner across islands and worlds. It steps aside while its owner is AFK, dead
 * or fighting a boss and returns by itself afterwards. Time spent together and feeding grow
 * it from baby to adult and deepen the bond needed for breeding.
 * <p>
 * Modes: follow, stay (holds its place), perch (sits small on its owner's head) and assist
 * (bites what its owner fights). Only assist puts it at risk: foes hit back, and a companion
 * knocked out rests in the stable for one in-game day.
 */
public final class PetCompanions implements PetBodies.Manager,Listener,AutoCloseable {
    private static final long AFK_MS=5*60_000L;
    private final VoidscapePlugin plugin;
    private final NativePetRenderer renderer;
    private final PetBodies bodies;
    private final PetTerrain terrain;
    private final PetStore store;
    private final Map<UUID,PetBody> out=new HashMap<>();
    private final Map<UUID,String> suspended=new HashMap<>();
    private final Map<UUID,Long> moved=new HashMap<>(),bossSeen=new HashMap<>(),bossCredit=new HashMap<>();
    private final Map<UUID,Integer> stranded=new HashMap<>();
    private final Map<UUID,Location> still=new HashMap<>();
    private final Map<UUID,Integer> stillSince=new HashMap<>();
    private final BukkitTask motion,upkeep,growth,saver;
    private final Random random=new Random();
    private PetTaming taming;
    private PetMenu menu;
    private int clock;
    private long busy;

    PetCompanions(VoidscapePlugin plugin,NativePetRenderer renderer,PetBodies bodies,PetTerrain terrain,PetStore store){
        this.plugin=plugin;this.renderer=renderer;this.bodies=bodies;this.terrain=terrain;this.store=store;
        bodies.hidesOthers(id->{PetStable stable=store.cached(id);return stable!=null&&stable.hideOthers;});
        Bukkit.getPluginManager().registerEvents(this,plugin);
        long now=System.currentTimeMillis();
        for(Player player:Bukkit.getOnlinePlayers()){store.stable(player.getUniqueId());moved.put(player.getUniqueId(),now);}
        motion=Bukkit.getScheduler().runTaskTimer(plugin,this::motion,1,1);
        upkeep=Bukkit.getScheduler().runTaskTimer(plugin,this::upkeep,40,20);
        growth=Bukkit.getScheduler().runTaskTimer(plugin,this::grow,1200,1200);
        saver=Bukkit.getScheduler().runTaskTimer(plugin,store::flush,600,600);
    }
    void taming(PetTaming taming){this.taming=taming;}
    void menu(PetMenu menu){this.menu=menu;}
    PetStable stable(Player player){return store.stable(player.getUniqueId());}
    PetBody body(UUID owner){return out.get(owner);}
    int count(){return out.size();}
    void dirty(PetStable stable){stable.dirty=true;}
    private int setting(String key,int fallback,int min,int max){return plugin.integer("pets.companions."+key,fallback,min,max);}
    private boolean worldAllowed(World world){return !plugin.getConfig().getStringList("pets.companions.disabled-worlds").contains(world.getName());}
    String state(Player player){
        PetStable stable=stable(player);
        if(!stable.out||stable.active==null)return "อยู่ในคอก";
        if(out.containsKey(player.getUniqueId()))return "ออกมาเดินด้วย";
        String reason=suspended.get(player.getUniqueId());
        return "พักชั่วคราว"+(reason==null?"":" · "+reason);
    }

    // ---- summon / dismiss ----
    String summon(Player player,UUID id){
        PetStable stable=stable(player);PetRecord record=stable.get(id);
        if(record==null)return "ไม่พบสัตว์เลี้ยงตัวนี้";
        if(stable.nesting(id))return record.name()+" อยู่ในรังผสมพันธุ์";
        if(!renderer.ready())return "ระบบโมเดลสัตว์เลี้ยงยังไม่พร้อม";
        if(!renderer.hasModel(record.species,record.stage))return record.species.display+" ยังไม่มีโมเดลในเซิร์ฟเวอร์นี้";
        if(faintLeft(record)>0)return record.name()+" ยังสลบอยู่ · พักฟื้นอีก "+recovery(record);
        despawn(player.getUniqueId());
        stable.active=id;stable.out=true;stable.dirty=true;suspended.remove(player.getUniqueId());
        if(!spawnNear(player))suspended.put(player.getUniqueId(),"รอพื้นที่ปลอดภัยใกล้เจ้าของ");
        return null;
    }
    void dismiss(Player player){
        PetStable stable=stable(player);stable.out=false;stable.dirty=true;
        suspended.remove(player.getUniqueId());despawn(player.getUniqueId());
    }
    /** A freshly tamed wild body becomes the companion, or goes straight to the stable. */
    void adopt(Player player,PetRecord record,PetBody body){
        PetStable stable=stable(player);stable.add(record);
        if(stable.active==null||!stable.out||!out.containsKey(player.getUniqueId())){
            despawn(player.getUniqueId());
            stable.active=record.id;stable.out=true;suspended.remove(player.getUniqueId());
            body.adopt(player.getUniqueId(),record);body.notice(player);body.airborne=false;body.mode=PetBody.Mode.GREET;body.modeUntil=clock+24;
            bodies.transfer(body,this);out.put(player.getUniqueId(),body);
            plugin.message(player,record.name()+" ติดตามคุณแล้ว · จัดการได้ที่ /pet");
        }else{
            bodies.remove(body);
            plugin.message(player,record.name()+" ถูกส่งเข้าคอกแล้ว · เรียกได้ที่ /pet");
        }
    }
    /** Rebuilds the visible body after growth, so the new form's model appears in place. */
    void rebuild(Player player){
        PetBody body=out.get(player.getUniqueId());if(body==null)return;
        // A body leaving its owner's head looks for footing instead of appearing mid-air.
        Location at=body.perched?null:body.location();despawn(player.getUniqueId());
        if(!spawnAt(player,at)&&!spawnNear(player))suspended.put(player.getUniqueId(),"รอพื้นที่ปลอดภัยใกล้เจ้าของ");
    }
    void despawn(UUID owner){PetBody body=out.remove(owner);if(body!=null)bodies.remove(body);stranded.remove(owner);}
    private boolean spawnAt(Player player,Location at){
        PetStable stable=stable(player);PetRecord record=stable.activeRecord();
        if(record==null)return false;
        float perch=record.mode.equals("perch")?perchScale(record.stage):0;
        if(perch>0)at=head(player);
        if(at==null)return false;
        PetBody body=PetBody.spawn(plugin,renderer,record.species,record.stage,at,record,player.getUniqueId(),perch);
        if(body==null)return false;
        Location owner=player.getLocation();
        body.yaw=body.targetYaw=perch>0?owner.getYaw():WildPetSpawns.yawTowards(owner.getX()-at.getX(),owner.getZ()-at.getZ());
        body.notice(player);
        out.put(player.getUniqueId(),body);bodies.add(body,this);bodies.refreshSoon();
        at.getWorld().spawnParticle(Particle.END_ROD,at.clone().add(0,.6,0),10,.3,.3,.3,.02);
        return true;
    }
    private boolean spawnNear(Player player){
        PetRecord record=stable(player).activeRecord();
        if(record==null||!worldAllowed(player.getWorld()))return false;
        if(record.mode.equals("perch"))return spawnAt(player,null);
        Location spot=near(player,record.stage,WildPetSpawns.flies(record.species));
        if(spot==null)return false;
        boolean ok=spawnAt(player,spot);
        if(ok){
            PetBody body=out.get(player.getUniqueId());double ground=PetTerrain.groundBelow(spot,3);
            body.airborne=WildPetSpawns.flies(record.species)&&(Double.isNaN(ground)||spot.getY()-ground>.5);
            if(body.airborne)body.hover=1.5;
        }
        return ok;
    }
    /** Safe footing behind or beside the owner; flyers may also take the air beside them. */
    private Location near(Player player,PetCatalog.Stage stage,boolean flyer){
        Location base=player.getLocation();int radius=PetTerrain.radius(stage);
        double yaw=Math.toRadians(base.getYaw());
        double[][] offsets={{0,-2.5},{1.6,-2},{-1.6,-2},{2.5,0},{-2.5,0},{0,-4},{3,-3},{-3,-3},{0,2.5}};
        for(double[] o:offsets){
            // o = (right, forward) in the owner's frame.
            double x=base.getX()-Math.cos(yaw)*o[0]-Math.sin(yaw)*o[1],z=base.getZ()-Math.sin(yaw)*o[0]+Math.cos(yaw)*o[1];
            for(int dy:new int[]{0,1,-1,2,-2}){
                Location c=new Location(base.getWorld(),Math.floor(x)+.5,base.getBlockY()+dy,Math.floor(z)+.5,base.getYaw(),0);
                if(terrain.safe(c,radius,PetTerrain.HEADROOM,false))return c;
            }
        }
        if(flyer){
            Location air=base.clone().add(Math.sin(yaw)*2,1.5,-Math.cos(yaw)*2);air.setPitch(0);
            if(terrain.airClear(air,stage,false))return air;
        }
        return null;
    }

    // ---- following ----
    long busy(){return busy;}
    private void motion(){
        long started=System.nanoTime();
        try{follow();}finally{busy+=System.nanoTime()-started;}
    }
    /** Every tick: glide, and catch up at once if far behind. Every other tick: choose the next stride. */
    private void follow(){
        clock++;
        for(var entry:new ArrayList<>(out.entrySet())){
            Player owner=Bukkit.getPlayer(entry.getKey());PetBody body=entry.getValue();
            if(owner==null||!body.valid())continue;
            try{
                if(!body.tickGrounding())body.tickGlide();
                follow(body,owner,clock%2==0);
            }
            catch(RuntimeException|LinkageError error){
                despawn(entry.getKey());plugin.getLogger().log(java.util.logging.Level.WARNING,"Cannot update companion",error);
            }
        }
    }
    private void follow(PetBody body,Player owner,boolean decide){
        Location at=body.destination(),to=owner.getLocation();
        if(at.getWorld()!=to.getWorld()){despawn(owner.getUniqueId());return;}
        String mode=body.record.mode;
        if(body.perched!=mode.equals("perch")){rebuild(owner);return;}
        if(body.perched){perch(body,owner);return;}
        if(body.falling){
            // Stay/follow/assist cannot pin a grounded pet in midair. Rescue only after a
            // long fall or reaching the world floor, using the ordinary safe owner landing.
            if(Math.abs(to.getY()-at.getY())>48||at.getY()<=at.getWorld().getMinHeight()+1){
                Location spot=near(owner,body.stage,WildPetSpawns.flies(body.species));
                if(spot!=null){
                    body.move(spot);double ground=PetTerrain.groundBelow(spot,3);
                    body.airborne=WildPetSpawns.flies(body.species)&&(Double.isNaN(ground)||spot.getY()-ground>.5);
                }
            }
            body.present("idle");return;
        }
        if(mode.equals("stay")&&stay(body,owner,decide))return;
        if(mode.equals("assist")&&fight(body,owner,decide))return;
        double dx=to.getX()-at.getX(),dz=to.getZ()-at.getZ(),flat=dx*dx+dz*dz,dy=to.getY()-at.getY();
        boolean flyer=WildPetSpawns.flies(body.species);
        if(flat>24*24||Math.abs(dy)>12){
            Location spot=near(owner,body.stage,flyer);
            if(spot!=null){double ground=PetTerrain.groundBelow(spot,3);body.airborne=flyer&&(Double.isNaN(ground)||spot.getY()-ground>.5);body.stuck=0;body.speed=0;body.move(spot);stranded.remove(owner.getUniqueId());}
            else stranded.merge(owner.getUniqueId(),1,Integer::sum);
            body.present(body.airborne?"fly":"idle");return;
        }
        if(!decide)return;
        stranded.remove(owner.getUniqueId());
        double yaw=Math.toRadians(to.getYaw()),side=(body.record.id.getLeastSignificantBits()&1)==0?1:-1;
        double tx=to.getX()+Math.sin(yaw)*2.2-Math.cos(yaw)*side*1.3,tz=to.getZ()-Math.cos(yaw)*2.2-Math.sin(yaw)*side*1.3;
        boolean ownerAloft=owner.isFlying()||owner.isGliding()||to.getY()-PetTerrain.groundBelow(to,4)>2.5||Double.isNaN(PetTerrain.groundBelow(to,4));
        double gap=(tx-at.getX())*(tx-at.getX())+(tz-at.getZ())*(tz-at.getZ());
        float before=body.yaw;
        Player greeted=clock<body.modeUntil&&body.mode==PetBody.Mode.GREET&&body.attention!=null?Bukkit.getPlayer(body.attention):null;
        Player look=body.watch(greeted!=null?greeted:owner,null,8);
        String clip;float pace=1;
        if(clock<body.modeUntil&&body.mode==PetBody.Mode.GREET&&flat<6*6){
            body.speed=0;body.steer(look,false,2,12);clip="greet";
        }else if(flyer&&(ownerAloft||body.airborne&&flat>4*4)){
            body.airborne=true;
            double ty=to.getY()+(ownerAloft?.6:1.6);
            // Ease the air speed; it slows as it closes in, so it glides in instead of braking.
            double target=Math.min(flat>10*10?.6:.32,Math.sqrt(gap)*.25);
            body.targetYaw=WildPetSpawns.yawTowards(tx-at.getX(),tz-at.getZ());
            body.steer(look,true,2,14);
            body.speed+=(target-body.speed)*.35;
            body.speed*=PetAttention.turnStride(body.targetYaw-body.yaw);
            Location next=toward(at,tx,ty,tz,body.speed,body.yaw);
            body.pitch+=((float)Math.clamp((next.getY()-at.getY())*60,-18,18)-body.pitch)*.4f;
            if(terrain.airClear(next,body.stage,false))body.glide(next,2);
            clip="fly";pace=.85f+(float)(body.speed/.6)*.4f;
        }else if(body.airborne){
            body.steer(look,false,2,14);
            double ground=PetTerrain.groundBelow(at,24);
            Location next=at.clone();
            if(Double.isNaN(ground)||!terrain.airClear(next.clone().add(0,-.3,0),body.stage,false)){
                // Nothing to land on right here (void edge, fence): hover level, then settle beside the owner.
                next=null;body.pitch*=.5f;body.stuck+=2;
                if(body.stuck>60){Location spot=near(owner,body.stage,false);if(spot!=null){body.airborne=false;body.move(spot);}body.stuck=0;}
            }else{body.pitch+=(-10-body.pitch)*.4f;next.setY(Math.max(ground,at.getY()-.3));if(next.getY()-ground<.05){body.airborne=false;body.pitch=0;}}
            if(next!=null)body.glide(next,2);
            clip="fly";
        }else if(flat>3.2*3.2&&gap>1||body.speed>.06){
            // Accelerate towards a run when far behind, and roll to a stop near the owner.
            double target=flat>3.2*3.2&&gap>1?(flat>8*8?.55:.32):0;
            body.targetYaw=WildPetSpawns.yawTowards(tx-at.getX(),tz-at.getZ());
            body.steer(look,true,2,14);
            body.speed=(target>body.speed?Math.min(target,body.speed+.1):body.speed*.5)
                *PetAttention.turnStride(body.targetYaw-body.yaw);
            if(body.speed>.02&&walk(body,tx,tz,body.speed)){body.stuck=0;clip="walk";pace=(float)Math.clamp(body.speed/2/.04,.7,2.2);}
            else if(Math.abs(WildPetSpawns.wrap(body.targetYaw-body.yaw))>30){
                // Face a destination behind us before stepping into it; turning is not a stuck pet.
                body.stuck=0;body.speed=0;clip="idle";
            }
            else{
                body.stuck+=2;body.speed=0;clip="idle";
                if(body.stuck>40){Location spot=near(owner,body.stage,flyer);if(spot!=null){body.move(spot);body.stuck=0;}}
            }
        }else{
            body.speed=0;body.steer(look,false,2,12);
            clip=stillTicks(owner)>20*12?"sit":"idle";
        }
        float bank=body.airborne?Math.clamp(-WildPetSpawns.wrap(body.yaw-before)*1.4f,-22f,22f):0;
        body.roll+=(bank-body.roll)*.35f;
        if(!body.airborne)body.pitch*=.5f;
        body.present(clip,pace);
    }
    private static Location toward(Location from,double x,double y,double z,double step,float yaw){
        double dx=x-from.getX(),dy=y-from.getY(),dz=z-from.getZ(),length=Math.sqrt(dx*dx+dy*dy+dz*dz);
        if(length<1e-4)return from.clone();
        double scale=Math.min(step,length)/length;
        double horizontal=Math.sqrt(dx*dx+dz*dz)*scale,radians=Math.toRadians(yaw);
        return from.clone().add(-Math.sin(radians)*horizontal,Math.clamp(dy*scale,-.35,.35),Math.cos(radians)*horizontal);
    }
    private boolean walk(PetBody body,double x,double z,double speed){
        Location at=body.destination();double dx=x-at.getX(),dz=z-at.getZ(),length=Math.sqrt(dx*dx+dz*dz);
        if(length<1e-4)return false;
        double step=Math.min(speed,length);
        double radians=Math.toRadians(body.yaw);
        Location next=at.clone().add(-Math.sin(radians)*step,0,Math.cos(radians)*step);
        // Step up or down one block, never off an island edge.
        for(int dy:new int[]{0,1,-1}){
            Location candidate=next.clone();candidate.setY(at.getBlockY()+dy);
            if(terrain.safe(candidate,PetTerrain.radius(body.stage),PetTerrain.HEADROOM,false)){body.glide(candidate,2);return true;}
        }
        return false;
    }

    // ---- modes ----
    /** Switches what a pet does; perching swaps in the small head-sitting body at once. */
    String mode(Player player,UUID id,String mode){
        PetStable stable=stable(player);PetRecord record=stable.get(id);
        if(record==null)return "ไม่พบสัตว์เลี้ยงตัวนี้";
        if(!PetSkills.MODES.contains(mode))return "โหมดมี follow, stay, perch และ assist";
        record.mode=mode;stable.dirty=true;
        PetBody body=out.get(player.getUniqueId());
        if(body!=null&&body.record==record){
            body.target=null;body.stuck=0;
            if(body.perched!=mode.equals("perch"))rebuild(player);
            else body.updateNametag();
        }
        return null;
    }
    /** Model scale on a head, by form: adults are several blocks long. */
    private static float perchScale(PetCatalog.Stage stage){return switch(stage){case BABY->.55f;case MEDIUM->.38f;case ADULT->.28f;};}
    private static Location head(Player owner){return owner.getLocation().add(0,owner.getHeight()+.02,0);}
    /**
     * Perched: each Java client carries the body on its owner's head (PetBodies.seat), so it never
     * trails behind. It turns after its owner's head instead of snapping, and looks up or down with them.
     */
    private void perch(PetBody body,Player owner){
        Location look=owner.getLocation();
        float turn=PetAttention.wrap(look.getYaw()-body.yaw);
        body.targetYaw=look.getYaw();
        body.yaw=PetAttention.approachYaw(body.yaw,body.targetYaw,Math.max(3,Math.abs(turn)*.45f));
        body.pitch=0;body.roll=0;body.speed=0;body.airborne=false;
        body.headYaw=Math.clamp(turn,-40,40);
        body.headPitch=Math.clamp(-look.getPitch()*.5f,-20,25);
        stranded.remove(owner.getUniqueId());
        body.perchAt(head(owner));
        // A safety net for any client that missed its seat; the regular seat follows each showing.
        if(clock%20==0)bodies.seat(body);
        body.present("sit");
    }
    /** Stays put and watches its owner; it gives up and follows once they are far away. */
    private boolean stay(PetBody body,Player owner,boolean decide){
        Location at=body.destination(),to=owner.getLocation();
        double dx=to.getX()-at.getX(),dz=to.getZ()-at.getZ(),flat=dx*dx+dz*dz;
        int leash=setting("stay-leash",64,16,256);
        if(flat>leash*leash||Math.abs(to.getY()-at.getY())>48){
            body.record.mode="follow";stable(owner).dirty=true;
            plugin.message(owner,body.record.name()+" วิ่งตามคุณมาเพราะคุณไปไกลเกิน "+leash+" บล็อก · เปลี่ยนเป็นโหมดติดตาม");
            return false;
        }
        if(!decide)return true;
        stranded.remove(owner.getUniqueId());body.speed=0;
        if(body.airborne){
            // A flyer told to stay lands where it is, or hovers when there is nothing below.
            double ground=PetTerrain.groundBelow(at,24);
            if(!Double.isNaN(ground)&&terrain.airClear(at.clone().add(0,-.3,0),body.stage,false)){
                Location next=at.clone();next.setY(Math.max(ground,at.getY()-.3));
                if(next.getY()-ground<.05){body.airborne=false;body.pitch=0;}
                body.glide(next,2);
            }
            body.steer(null,false,2,12);body.present("fly");return true;
        }
        Player look=body.watch(owner,null,10);
        body.steer(look,false,2,12);body.pitch*=.5f;body.roll*=.5f;
        body.present(flat<10*10?"idle":"sit");
        return true;
    }

    // ---- assist: fighting beside the owner ----
    private static boolean assisting(PetBody body){return body.record!=null&&body.record.mode.equals("assist")&&!body.perched;}
    /** Something a companion may bite: a living mob near its owner, never a player, pet, boss, NPC or the owner's own animal. */
    private boolean foe(Player owner,LivingEntity target){
        int radius=setting("assist-radius",16,4,48);
        return target.isValid()&&!target.isDead()&&!(target instanceof Player)&&!(target instanceof ArmorStand)&&!target.isInvulnerable()
            &&target.getWorld()==owner.getWorld()&&target.getLocation().distanceSquared(owner.getLocation())<=radius*radius
            &&!bodies.part(target)&&!boss(target)&&!target.hasMetadata("NPC")
            &&!(target instanceof Tameable pet&&pet.isTamed()&&owner.getUniqueId().equals(pet.getOwnerUniqueId()));
    }
    /** The nearest hostile mob already hunting the owner. */
    private LivingEntity threat(Player owner){
        LivingEntity best=null;double closest=Double.MAX_VALUE;
        for(Entity entity:owner.getNearbyEntities(12,6,12)){
            if(!(entity instanceof Mob mob)||!(entity instanceof Enemy)||mob.getTarget()!=owner||!foe(owner,mob))continue;
            double distance=mob.getLocation().distanceSquared(owner.getLocation());
            if(distance<closest){closest=distance;best=mob;}
        }
        return best;
    }
    /** Chases and bites the current foe; false when there is none, so it simply follows. */
    private boolean fight(PetBody body,Player owner,boolean decide){
        if(body.target!=null&&!foe(owner,body.target))body.target=null;
        if(body.target==null&&decide&&clock%10==0){body.target=threat(owner);body.ambushed=false;}
        LivingEntity target=body.target;
        if(target==null)return false;
        if(!decide)return true;
        stranded.remove(owner.getUniqueId());
        Location at=body.destination(),there=target.getLocation();
        boolean flyer=WildPetSpawns.flies(body.species);
        double dx=there.getX()-at.getX(),dz=there.getZ()-at.getZ(),flat=Math.sqrt(dx*dx+dz*dz),dy=there.getY()-at.getY();
        double reach=1.3+target.getWidth()/2+PetTerrain.radius(body.stage)*.6;
        body.targetYaw=WildPetSpawns.yawTowards(dx,dz);
        float before=body.yaw;String clip;float pace=1;
        if(flat<=reach&&dy>-2.5&&dy<(flyer||body.airborne?3:2)){
            body.speed=0;body.steer(null,false,2,24);
            if(clock>=body.nextStrike&&Math.abs(PetAttention.wrap(body.targetYaw-body.yaw))<50)strike(body,owner,target);
            clip=clock-body.struck<10?"greet":body.airborne?"fly":"idle";
        }else if(flyer&&(body.airborne||dy>1.5)){
            body.airborne=true;
            body.steer(null,true,2,18);
            body.speed+=(Math.min(.6,Math.max(.12,flat*.3))-body.speed)*.35;
            body.speed*=PetAttention.turnStride(body.targetYaw-body.yaw);
            Location next=toward(at,there.getX(),there.getY()+target.getHeight()*.5,there.getZ(),Math.max(.04,body.speed),body.yaw);
            body.pitch+=((float)Math.clamp((next.getY()-at.getY())*60,-18,18)-body.pitch)*.4f;
            if(terrain.airClear(next,body.stage,false)){body.glide(next,2);body.stuck=0;}else body.stuck+=2;
            clip="fly";pace=1.2f;
        }else{
            body.steer(null,true,2,18);
            body.speed=Math.min(.55,body.speed+.1)*PetAttention.turnStride(body.targetYaw-body.yaw);
            if(body.speed>.02&&walk(body,there.getX(),there.getZ(),body.speed)){body.stuck=0;clip="walk";pace=(float)Math.clamp(body.speed/2/.04,.7,2.2);}
            else{if(Math.abs(PetAttention.wrap(body.targetYaw-body.yaw))<=30)body.stuck+=2;body.speed=0;clip="idle";}
        }
        // Out of reach (a ledge, a wall): give up after a few seconds and return to the owner.
        if(body.stuck>60){body.target=null;body.stuck=0;}
        float bank=body.airborne?Math.clamp(-WildPetSpawns.wrap(body.yaw-before)*1.4f,-22f,22f):0;
        body.roll+=(bank-body.roll)*.35f;
        if(!body.airborne)body.pitch*=.5f;
        body.present(clip,pace);
        return true;
    }
    private void strike(PetBody body,Player owner,LivingEntity target){
        PetRecord record=body.record;
        boolean first=!body.ambushed;body.ambushed=true;
        double amount=PetSkills.damage(record)*PetSkills.strikeMultiplier(record,first,target,random),before=target.getHealth();
        // Bites land between the owner's hits instead of vanishing into their invulnerability frames.
        target.setNoDamageTicks(0);
        bodies.strike(body,()->target.damage(amount,DamageSource.builder(DamageType.MOB_ATTACK).withDirectEntity(body.shell).withCausingEntity(owner).build()));
        double dealt=Math.max(0,before-Math.max(0,target.getHealth()));
        body.nextStrike=clock+PetSkills.attackTicks(record);body.struck=clock;combat(record);
        Location hit=target.getLocation().add(0,target.getHeight()*.6,0);
        hit.getWorld().spawnParticle(Particle.SWEEP_ATTACK,hit,1,0,0,0,0);
        hit.getWorld().playSound(hit,Sound.ENTITY_FOX_BITE,.8f,.85f+random.nextFloat()*.3f);
        if(dealt>0)PetSkills.onHit(record,owner,target,dealt,random);
        // Foes hit back, so a companion that fights can be knocked out.
        if(!target.isDead()&&target instanceof Mob&&random.nextInt(100)<setting("counter-chance",35,0,100))hurt(body,owner,attack(target));
    }
    private static double attack(LivingEntity foe){
        var attribute=foe.getAttribute(Attribute.ATTACK_DAMAGE);
        return attribute==null?2:Math.max(1,attribute.getValue());
    }
    private static void combat(PetRecord record){record.combatTick=Bukkit.getCurrentTick();}
    /** Current health: 5% of the maximum returns each second once a fight has been over for five seconds. */
    double health(PetRecord record){
        long now=Bukkit.getCurrentTick();double max=PetSkills.maxHealth(record);
        if(record.health<0||record.health>max)record.health=max;
        long from=Math.max(record.healthTick,record.combatTick+100);
        if(now>from)record.health=Math.min(max,record.health+max*.05*(now-from)/20.0);
        record.healthTick=now;return record.health;
    }
    private void hurt(PetBody body,Player owner,double raw){
        PetRecord record=body.record;double max=PetSkills.maxHealth(record);
        double health=health(record)-PetSkills.damageTaken(record,raw);
        record.health=health;combat(record);
        Location at=body.location().add(0,.5,0);
        at.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR,at,3,.2,.2,.2,0);
        at.getWorld().playSound(at,Sound.ENTITY_ALLAY_HURT,.7f,.6f);
        if(health<=0){faint(owner,body);return;}
        owner.sendActionBar(Component.text(record.name()+" ❤ "+(int)Math.ceil(health)+"/"+(int)max,health<max*.3?NamedTextColor.RED:NamedTextColor.GOLD));
    }
    /** Knocked out: back to the stable for one in-game day, then it returns by itself. */
    private void faint(Player owner,PetBody body){
        PetRecord record=body.record;
        record.faintedUntil=gameTime()+setting("faint-ticks",24000,200,240000);record.health=-1;stable(owner).dirty=true;
        Location at=body.location().add(0,.5,0);despawn(owner.getUniqueId());
        at.getWorld().spawnParticle(Particle.CLOUD,at,16,.4,.3,.4,.02);
        at.getWorld().spawnParticle(Particle.SOUL,at,6,.3,.3,.3,.02);
        at.getWorld().playSound(at,Sound.BLOCK_BEACON_DEACTIVATE,.8f,1.4f);
        suspended.put(owner.getUniqueId(),"สลบ · พักฟื้นอีก "+recovery(record));
        owner.showTitle(Title.title(Component.text(record.name()+" สลบ!",NamedTextColor.RED),
            Component.text("พักฟื้นในคอก "+recovery(record),NamedTextColor.GRAY),Title.Times.times(Duration.ofMillis(200),Duration.ofSeconds(2),Duration.ofMillis(500))));
        plugin.message(owner,record.name()+" ถูกโจมตีจนสลบ · พักฟื้น 1 วันในเกม (อีก "+recovery(record)+") แล้วจะกลับมาเอง · ระหว่างนี้เรียกตัวอื่นได้");
        plugin.getLogger().info("Pet fainted: "+owner.getName()+" "+record.species.id+" "+record.id);
    }
    /** Fainting counts in-game time on Evergarden's clock: /time set cannot skip it, restarts do not reset it. */
    private long gameTime(){return plugin.world().getGameTime();}
    /** Ticks before a knocked-out pet may come out again; 0 once recovered. */
    long faintLeft(PetRecord record){return record.faintedUntil<=0?0:Math.max(0,record.faintedUntil-gameTime());}
    String recovery(PetRecord record){return Math.max(1,(faintLeft(record)+1199)/1200)+" นาที";}

    private int stillTicks(Player owner){
        Location last=still.get(owner.getUniqueId()),now=owner.getLocation();
        if(last==null||last.getWorld()!=now.getWorld()||last.distanceSquared(now)>.04){still.put(owner.getUniqueId(),now);stillSince.put(owner.getUniqueId(),clock);return 0;}
        return clock-stillSince.getOrDefault(owner.getUniqueId(),clock);
    }

    // ---- upkeep: absence, AFK, bosses, respawns ----
    private String blocker(Player player){
        if(player.isDead())return "เจ้าของหมดสติ";
        if(player.getGameMode()==GameMode.SPECTATOR)return "โหมดผู้ชม";
        if(!worldAllowed(player.getWorld()))return "ปิดสัตว์เลี้ยงในโลกนี้";
        if(System.currentTimeMillis()-moved.getOrDefault(player.getUniqueId(),System.currentTimeMillis())>AFK_MS)return "เจ้าของ AFK";
        if(bossNear(player))return "ใกล้การต่อสู้กับบอส";
        return null;
    }
    /** World boss parts, Arkhaia and live sanctuary encounters. */
    private boolean boss(Entity entity){
        var tags=entity.getScoreboardTags();var data=entity.getPersistentDataContainer();
        return tags.contains("boss")||tags.contains("arkhaia")||data.has(plugin.key("sanctuary_boss"),PersistentDataType.BYTE)
            ||data.has(plugin.key("world_boss_entity"),PersistentDataType.BYTE)&&entity instanceof org.bukkit.entity.LivingEntity;
    }
    /** Companions step away from boss arenas and come back ten seconds after the fight is out of range. */
    private boolean bossNear(Player player){
        long now=System.currentTimeMillis();int radius=setting("boss-radius",40,8,128);
        // Step away within the radius; come back only beyond radius+16 (true distance, not the box corners).
        double reach=bossSeen.containsKey(player.getUniqueId())?radius+16:radius;
        for(Entity entity:player.getNearbyEntities(radius+16,32,radius+16)){
            if(!boss(entity)||entity.getLocation().distanceSquared(player.getLocation())>reach*reach)continue;
            bossSeen.put(player.getUniqueId(),now);return true;
        }
        Long seen=bossSeen.get(player.getUniqueId());
        if(seen!=null&&now-seen<10_000)return true;
        bossSeen.remove(player.getUniqueId());return false;
    }
    private void upkeep(){
        long started=System.nanoTime();
        try{maintain();}finally{busy+=System.nanoTime()-started;}
    }
    private void maintain(){
        for(Player player:Bukkit.getOnlinePlayers()){
            PetStable stable=store.cached(player.getUniqueId());
            if(stable==null||!stable.out||stable.activeRecord()==null){
                if(out.containsKey(player.getUniqueId()))despawn(player.getUniqueId());
                continue;
            }
            UUID id=player.getUniqueId();PetBody body=out.get(id);PetRecord active=stable.activeRecord();
            if(active.faintedUntil>0){
                if(faintLeft(active)>0){if(body!=null)despawn(id);suspended.put(id,"สลบ · พักฟื้นอีก "+recovery(active));continue;}
                active.faintedUntil=0;stable.dirty=true;
                plugin.message(player,active.name()+" ฟื้นตัวแล้ว กำลังกลับมาหาคุณ");
            }
            String reason=blocker(player);
            if(reason==null&&stranded.getOrDefault(id,0)>20*10)reason="ตามเจ้าของไม่ทัน";
            if(reason!=null){
                if(body!=null){despawn(id);Location at=body.location();at.getWorld().spawnParticle(Particle.END_ROD,at.add(0,.6,0),8,.3,.3,.3,.02);}
                if(!reason.equals(suspended.put(id,reason))&&reason.equals("ใกล้การต่อสู้กับบอส"))
                    plugin.message(player,stable.activeRecord().name()+" หลบไปรอนอกสนามบอส · จะกลับมาเองหลังจบการต่อสู้");
                if(reason.equals("ตามเจ้าของไม่ทัน"))stranded.remove(id);
                continue;
            }
            if(body!=null&&!body.valid()){despawn(id);body=null;}
            if(body==null){if(spawnNear(player))suspended.remove(id);else suspended.put(id,"รอพื้นที่ปลอดภัยใกล้เจ้าของ");}
            body=out.get(id);
            // Passive skills work while the companion is close (a pet left on stay far away does not count).
            if(body!=null&&(body.perched||body.location().getWorld()==player.getWorld()&&body.location().distanceSquared(player.getLocation())<=32*32))
                PetSkills.passive(player,active,clock,target->foe(player,target));
        }
    }
    private void grow(){
        long now=System.currentTimeMillis();
        for(var entry:new ArrayList<>(out.entrySet())){
            Player player=Bukkit.getPlayer(entry.getKey());if(player==null)continue;
            if(now-moved.getOrDefault(entry.getKey(),now)>AFK_MS)continue;
            PetStable stable=stable(player);PetRecord record=stable.activeRecord();if(record==null)continue;
            record.growth++;record.minutes++;
            if(record.minutes%10==0)record.bond=Math.min(PetRecord.BOND_MAX,record.bond+1);
            stable.dirty=true;checkGrowth(player,record);
        }
    }
    /** Growth already "earned" by a pet that joins at this stage. */
    int growthFloor(PetCatalog.Stage stage){return stage==PetCatalog.Stage.BABY?0:threshold(stage);}
    int threshold(PetCatalog.Stage next){
        return next==PetCatalog.Stage.MEDIUM?plugin.integer("pets.growth.medium",120,1,100000):plugin.integer("pets.growth.adult",360,2,100000);
    }
    void checkGrowth(Player player,PetRecord record){
        PetCatalog.Stage next=record.stage==PetCatalog.Stage.BABY?PetCatalog.Stage.MEDIUM:record.stage==PetCatalog.Stage.MEDIUM?PetCatalog.Stage.ADULT:null;
        if(next==null||record.growth<threshold(next)||!renderer.hasModel(record.species,next))return;
        record.stage=next;stable(player).dirty=true;
        player.showTitle(Title.title(Component.text(record.name()+" เติบโตแล้ว!",NamedTextColor.GOLD),
            Component.text(next.thai,NamedTextColor.AQUA),Title.Times.times(Duration.ofMillis(200),Duration.ofSeconds(2),Duration.ofMillis(500))));
        player.playSound(player.getLocation(),Sound.ENTITY_PLAYER_LEVELUP,.8f,1.2f);
        if(record.id.equals(stable(player).active))rebuild(player);
    }

    // ---- interaction ----
    @Override public void interact(PetBody body,Player player){
        if(!player.getUniqueId().equals(body.owner)){
            body.notice(player);
            Player owner=Bukkit.getPlayer(body.owner);
            player.sendActionBar(Component.text(body.record.name()+" · สัตว์เลี้ยงของ "+(owner==null?"ผู้เล่น":owner.getName()),NamedTextColor.AQUA));
            body.mode=PetBody.Mode.GREET;body.modeUntil=clock+24;return;
        }
        ItemStack hand=player.getInventory().getItemInMainHand();
        if(taming!=null&&hand.getType()==taming.food(body.species)&&PetTaming.plain(hand)){feed(player,body,hand);return;}
        if(menu!=null&&hand.getType()==Material.NAME_TAG){menu.nameTag(player,body.record.id,hand);return;}
        if(menu!=null)menu.openPet(player,body.record.id);
    }
    private void feed(Player player,PetBody body,ItemStack hand){
        long now=System.currentTimeMillis();PetRecord record=body.record;
        long wait=record.fed+setting("feed-cooldown-seconds",60,5,3600)*1000L-now;
        if(wait>0){plugin.message(player,record.name()+" ยังอิ่มอยู่ · อีก "+(wait/1000+1)+" วินาที");return;}
        PetTaming.consume(player,hand);
        body.notice(player);
        record.fed=now;record.growth+=setting("feed-growth",5,0,1000);record.bond=Math.min(PetRecord.BOND_MAX,record.bond+2);
        stable(player).dirty=true;
        body.mode=PetBody.Mode.GREET;body.modeUntil=clock+24;
        Location at=body.location().add(0,1,0);
        at.getWorld().spawnParticle(Particle.HEART,at,4,.35,.3,.35,0);
        player.playSound(at,Sound.ENTITY_GENERIC_EAT,.7f,1.3f);
        player.sendActionBar(Component.text(record.name()+" · ความผูกพัน "+record.bond+" · การเติบโต "+record.growth,NamedTextColor.LIGHT_PURPLE));
        checkGrowth(player,record);
    }
    @Override public void lost(PetBody body){if(body.owner!=null&&out.get(body.owner)==body)out.remove(body.owner);}

    // ---- player lifecycle ----
    @EventHandler public void join(PlayerJoinEvent event){
        UUID id=event.getPlayer().getUniqueId();moved.put(id,System.currentTimeMillis());store.stable(id);
    }
    @EventHandler public void quit(PlayerQuitEvent event){
        UUID id=event.getPlayer().getUniqueId();
        despawn(id);suspended.remove(id);moved.remove(id);bossSeen.remove(id);bossCredit.remove(id);still.remove(id);stillSince.remove(id);
        store.unload(id);
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void move(PlayerMoveEvent event){
        Location from=event.getFrom(),to=event.getTo();
        if(from.getBlockX()!=to.getBlockX()||from.getBlockY()!=to.getBlockY()||from.getBlockZ()!=to.getBlockZ()
            ||Math.abs(from.getYaw()-to.getYaw())>15||Math.abs(from.getPitch()-to.getPitch())>15)
            moved.put(event.getPlayer().getUniqueId(),System.currentTimeMillis());
    }
    @EventHandler public void chat(io.papermc.paper.event.player.AsyncChatEvent event){
        UUID id=event.getPlayer().getUniqueId();Bukkit.getScheduler().runTask(plugin,()->moved.put(id,System.currentTimeMillis()));
    }
    /** Assist: the owner's own target (melee or arrow) becomes the companion's; so does a mob that hits the owner. */
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void combat(EntityDamageByEntityEvent event){
        if(bodies.striking())return;
        Entity damager=event.getDamager();
        Entity source=damager instanceof Projectile shot&&shot.getShooter() instanceof Entity shooter?shooter:damager;
        if(source instanceof Player attacker&&event.getEntity() instanceof LivingEntity victim){
            PetBody body=out.get(attacker.getUniqueId());
            if(body!=null&&assisting(body)&&body.target!=victim&&foe(attacker,victim)){body.target=victim;body.ambushed=false;body.stuck=0;}
        }
        if(event.getEntity() instanceof Player owner&&source instanceof LivingEntity foe){
            PetBody body=out.get(owner.getUniqueId());
            if(body!=null&&assisting(body)&&body.target==null&&foe(owner,foe)){body.target=foe;body.ambushed=false;body.stuck=0;}
        }
    }
    /** Runic Golem's guard: its owner takes less damage from foes while the golem fights close by. */
    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true) public void guard(EntityDamageByEntityEvent event){
        if(!(event.getEntity() instanceof Player owner))return;
        PetBody body=out.get(owner.getUniqueId());
        if(body==null||!assisting(body)||PetSkills.guard(body.record)>=1||body.location().getWorld()!=owner.getWorld()
            ||body.location().distanceSquared(owner.getLocation())>8*8)return;
        event.setDamage(event.getDamage()*PetSkills.guard(body.record));
    }
    /** Explosions hurt fighting companions caught within six blocks. */
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void blast(EntityExplodeEvent event){
        Location at=event.getLocation();
        for(var entry:new ArrayList<>(out.entrySet())){
            PetBody body=entry.getValue();Player owner=Bukkit.getPlayer(entry.getKey());
            if(owner==null||!assisting(body)||body.location().getWorld()!=at.getWorld())continue;
            double distance=body.location().distance(at);
            if(distance<6)hurt(body,owner,(1-distance/6)*14);
        }
    }
    @EventHandler public void world(PlayerChangedWorldEvent event){despawn(event.getPlayer().getUniqueId());}
    @EventHandler public void death(org.bukkit.event.entity.PlayerDeathEvent event){despawn(event.getPlayer().getUniqueId());}
    /** Defeating a boss near an active companion is a growth spurt. */
    /** Once per ten minutes: a boss is many entities (hands, summons, minions) that each die. */
    @EventHandler public void bossDefeat(EntityDeathEvent event){
        if(!boss(event.getEntity()))return;
        Location at=event.getEntity().getLocation();long now=System.currentTimeMillis();
        for(Player player:at.getWorld().getPlayers()){
            if(player.getLocation().distanceSquared(at)>64*64||player.isDead()||player.getGameMode()==GameMode.SPECTATOR)continue;
            if(now-moved.getOrDefault(player.getUniqueId(),now)>AFK_MS||now-bossCredit.getOrDefault(player.getUniqueId(),0L)<600_000L)continue;
            PetStable stable=store.cached(player.getUniqueId());PetRecord record=stable==null?null:stable.activeRecord();
            if(record==null||!stable.out)continue;
            bossCredit.put(player.getUniqueId(),now);
            record.growth+=setting("boss-growth",20,0,1000);stable.dirty=true;checkGrowth(player,record);
        }
    }
    @Override public void close(){
        motion.cancel();upkeep.cancel();growth.cancel();saver.cancel();
        for(UUID owner:new ArrayList<>(out.keySet()))despawn(owner);
        HandlerList.unregisterAll(this);
    }
}
