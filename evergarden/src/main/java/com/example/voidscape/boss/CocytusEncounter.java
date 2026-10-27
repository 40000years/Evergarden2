package com.example.voidscape.boss;

import com.example.voidscape.VoidscapePlugin;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.util.*;

/** Cocytus owns its cover, breakable prisons and swept, non-homing frost arrows. */
final class CocytusEncounter {
    static final int STORM_WARNING=56, RAIN_WARNING=32, SPIKE_WARNING=22;
    private static final int STORM_WAVES=6, STORM_INTERVAL=12, STORM_FLIGHT=44, RAIN_ROUND=60;
    private static final double STORM_SPEED=2, RAIN_SPEED=2;
    static final double RAIN_RADIUS=5, SPIKE_RADIUS=2;
    private static final class Prison {
        final RiverTrials.State state;final Player player;final Location tip;final Slime receiver;
        final List<BlockDisplay> spike;final TextDisplay label;final boolean gravity;final long caught;
        final Map<UUID,Long> strikes=new HashMap<>();int hits=3,shots;boolean lifting;
        Prison(RiverTrials.State state,Player player,Location tip,Slime receiver,List<BlockDisplay> spike,TextDisplay label,long now){
            this.state=state;this.player=player;this.tip=tip;this.receiver=receiver;this.spike=spike;this.label=label;
            gravity=player.hasGravity();caught=now;
        }
    }
    private static final class Shot {
        final RiverTrials.State state;final Arrow entity;final Vector step;final long until;
        final double damage;final Set<UUID> victims;final Prison prison;Location at;
        Shot(RiverTrials.State state,Arrow entity,Location at,Vector step,long until,double damage,Set<UUID> victims,Prison prison){
            this.state=state;this.entity=entity;this.at=at;this.step=step;this.until=until;
            this.damage=damage;this.victims=victims;this.prison=prison;
        }
    }
    private static final class RainMark {
        final Location ground;final List<Location> lanes=new ArrayList<>();double skyY;
        RainMark(Location ground,double skyY){this.ground=ground;this.skyY=skyY;}
    }
    private final VoidscapePlugin plugin;private final ArkhaiaMinions host;private final Location center;private final JudgeSigils sigils;
    private final List<Shot> arrows=new ArrayList<>();
    private final Map<UUID,Prison> prisons=new HashMap<>();
    private final Map<UUID,Long> immunity=new HashMap<>();
    private final Set<UUID> owned=new HashSet<>();
    private final Map<RiverTrials.State,Set<UUID>> rainVictims=new IdentityHashMap<>();
    private final Map<RiverTrials.State,List<RainMark>> rainMarks=new IdentityHashMap<>();
    CocytusEncounter(VoidscapePlugin plugin,ArkhaiaMinions host,Location center,JudgeSigils sigils){this.plugin=plugin;this.host=host;this.center=center;this.sigils=sigils;}
    boolean owns(Entity entity){return owned.contains(entity.getUniqueId());}
    boolean trapped(Player p){return prisons.containsKey(p.getUniqueId());}
    private String key(RiverTrials.State s){return "cocytus:"+s.owner.mob.getUniqueId();}
    private void tag(Entity e){owned.add(e.getUniqueId());e.setPersistent(false);e.getPersistentDataContainer().set(plugin.key("world_boss_entity"),PersistentDataType.BYTE,(byte)1);}
    private void remove(Entity e){owned.remove(e.getUniqueId());e.remove();}
    private Location floor(Location at){Location p=at.clone();p.setY(center.getY());return p;}
    private boolean horizontal(Player p,Location at,double radius){return p.getWorld()==at.getWorld()&&Math.hypot(p.getX()-at.getX(),p.getZ()-at.getZ())<=radius+.3;}
    private void text(Player p,String text){p.sendActionBar(net.kyori.adventure.text.Component.text(text));}
    private BlockDisplay ice(Location at,double width,double height){
        return at.getWorld().spawn(at,BlockDisplay.class,e->{tag(e);e.setInvulnerable(true);e.setBlock(Material.PACKED_ICE.createBlockData());
            e.setBrightness(new Display.Brightness(15,15));e.setTransformation(new Transformation(new Vector3f((float)-width/2,0,(float)-width/2),new Quaternionf(),new Vector3f((float)width,(float)height,(float)width),new Quaternionf()));});
    }
    private List<BlockDisplay> spike(Location at){
        return List.of(ice(at,1.5,1.2),ice(at.clone().add(0,1.2,0),1,.9),ice(at.clone().add(0,2.1,0),.4,.7));
    }
    private void ring(String id,Location at,double radius,long now){
        sigils.circle(id,"river_cocytus",floor(at).add(0,.12,0),radius,now*.025,false);
        for(int i=0;i<40;i++){double a=i*Math.PI/20;at.getWorld().spawnParticle(Particle.DUST,floor(at).add(Math.cos(a)*radius,.16,Math.sin(a)*radius),1,0,0,0,0,new Particle.DustOptions(Color.AQUA,1.5f),true);}
    }
    private void pose(RiverTrials.State s,Player target,String prefix,long age,int prepare,int active){
        String stage=age<prepare?"windup":age<prepare+active?"active":"recover";
        host.renderTrial(s.owner,target,prefix+"_"+stage,stage.equals("windup")?(prefix.equals("attack")?16.0:20.0)/prepare:1);
    }
    void begin(RiverTrials.State s,List<Player> players,long now){
        if(s.move==RiverTrials.Move.BLIZZARD){
            // Telegraph from the arena centre, so the cover rings protect every direction.
            boolean clearCenter=true;for(int y=0;y<11;y++)if(!center.clone().add(0,y,0).getBlock().isPassable())clearCenter=false;
            if(clearCenter&&s.owner.mob.teleport(center.clone()))s.origin=s.owner.mob.getLocation();
            // Two rings keep cover reachable across the entire 34-block arena.
            for(double radius:new double[]{10,24})for(int i=0;i<8;i++){
                double a=i*Math.PI/4;Location at=floor(s.origin).add(Math.cos(a)*radius,0,Math.sin(a)*radius);
                if(Math.hypot(at.getX()-center.getX(),at.getZ()-center.getZ())>32)continue;
                boolean clear=true;for(double dx:new double[]{-1.3,1.3})for(double dz:new double[]{-1.3,1.3})for(int y=0;y<6;y++)if(!at.clone().add(dx,y,dz).getBlock().isPassable())clear=false;
                if(clear)s.ice.add(ice(at,2.6,5.5));
            }
            for(Player p:players)text(p,"FROST TEMPEST - hide behind an ice pillar!");
        }else mark(s,players);
    }
    private void mark(RiverTrials.State s,List<Player> players){
        s.marks.clear();
        List<RainMark> rain=new ArrayList<>();
        // Overlapping rain circles merge. Prison circles remain per player.
        for(Player p:players){Location at=floor(p.getLocation());
            if(s.move==RiverTrials.Move.VOLLEY){
                RainMark mark=rain.stream().filter(m->m.ground.distanceSquared(at)<16).findFirst().orElse(null);
                double skyY=Math.max(at.getY()+12,p.getY()+12);
                if(mark==null){mark=new RainMark(at,skyY);rain.add(mark);s.marks.add(at);}
                else mark.skyY=Math.max(mark.skyY,skyY);
                // Precision lanes commit with the warning, so walking can still dodge them.
                mark.lanes.add(at.clone());continue;
            }
            s.marks.add(at);
        }
        if(s.move==RiverTrials.Move.VOLLEY)rainMarks.put(s,rain);
    }
    /** Returns true only after every launched arrow has had time to reach the arena edge. */
    boolean storm(RiverTrials.State s,Player target,List<Player> players,long now,long age){
        if(s.ice.isEmpty())return true;
        pose(s,target,"cast",age,STORM_WARNING,STORM_WAVES*STORM_INTERVAL);
        if(age<STORM_WARNING){if(now%4==0){ring(key(s)+":storm",s.origin,4,now);s.origin.getWorld().spawnParticle(Particle.SNOWFLAKE,s.origin.clone().add(0,6,0),30,3,3,3,.05);}return false;}
        sigils.remove(key(s)+":storm");
        if(age<STORM_WARNING+STORM_WAVES*STORM_INTERVAL&&(age-STORM_WARNING)%STORM_INTERVAL==0){
            Set<UUID> victims=new HashSet<>();int wave=(int)((age-STORM_WARNING)/STORM_INTERVAL);
            for(double height:new double[]{1,4,8})for(int i=0;i<72;i++){
                double a=(i+wave*.35)*Math.PI/36;Vector step=new Vector(Math.cos(a)*STORM_SPEED,0,Math.sin(a)*STORM_SPEED);
                shoot(s,s.origin.clone().add(0,height,0),step,now+STORM_FLIGHT,24,victims,null);
            }
            // Aimed lanes fill radial gaps and reach players standing on raised blocks.
            for(Player p:players){Location from=s.origin.clone().add(0,7,0);Vector d=p.getLocation().add(0,.9,0).toVector().subtract(from.toVector());
                if(d.lengthSquared()>.01){double speed=Math.max(STORM_SPEED,d.length()/40);shoot(s,from,d.normalize().multiply(speed),now+STORM_FLIGHT,24,victims,null);}}
            s.origin.getWorld().playSound(s.origin,Sound.ENTITY_ARROW_SHOOT,1.5f,.65f);
        }
        if(age>=STORM_WARNING+(STORM_WAVES-1)*STORM_INTERVAL+STORM_FLIGHT){shatter(s);return true;}return false;
    }
    boolean spikes(RiverTrials.State s,Player target,List<Player> players,long now,long age){
        Prison executing=prisons.values().stream().filter(p->p.state==s&&now-p.caught>=40).findFirst().orElse(null);
        if(executing==null)pose(s,target,"cast",age,SPIKE_WARNING,12);
        else if(now-executing.caught<82)pose(s,executing.player,"attack",(now-executing.caught-40)%14,10,4);
        else host.renderTrial(s.owner,executing.player,"attack_recover",1);
        if(age<SPIKE_WARNING){if(now%3==0)for(int i=0;i<s.marks.size();i++)ring(key(s)+":mark:"+i,s.marks.get(i),SPIKE_RADIUS,now);return false;}
        if(age==SPIKE_WARNING){clearMarks(s);
            for(Location at:s.marks){
                int captured=prisons.size();
                at.getWorld().spawnParticle(Particle.BLOCK,at.clone().add(0,1,0),35,.7,1,.7,Material.PACKED_ICE.createBlockData());
                for(Player p:players)if(!trapped(p)&&now>=immunity.getOrDefault(p.getUniqueId(),0L)&&horizontal(p,at,SPIKE_RADIUS)&&Math.abs(p.getY()-at.getY())<3){
                    capture(s,p,at,now);
                }
                if(prisons.size()==captured)s.ice.addAll(spike(at));
            }
            s.origin.getWorld().playSound(s.origin,Sound.BLOCK_GLASS_BREAK,1,.65f);
        }
        // A missed or broken prison opens a short punish window instead of idling for 5.5 seconds.
        return age>=SPIKE_WARNING+12&&prisons.values().stream().noneMatch(prison->prison.state==s);
    }
    private void capture(RiverTrials.State s,Player p,Location at,long now){
        Location tip=at.clone().add(0,2.85,0);tip.setYaw(p.getYaw());tip.setPitch(p.getPitch());
        // Do not lift through a roof or leave a player inside terrain.
        for(double dx:new double[]{-.3,.3})for(double dz:new double[]{-.3,.3})for(int y=0;y<5;y++)if(!at.clone().add(dx,y,dz).getBlock().isPassable())return;
        List<BlockDisplay> spike=spike(at);
        Slime receiver=at.getWorld().spawn(at.clone().add(0,2.1,0),Slime.class,e->{tag(e);e.setSize(2);e.setAI(false);e.setGravity(false);e.setInvisible(true);e.setSilent(true);e.setRemoveWhenFarAway(false);e.setCollidable(false);
            e.getAttribute(Attribute.MAX_HEALTH).setBaseValue(30);e.setHealth(30);e.getAttribute(Attribute.ATTACK_DAMAGE).setBaseValue(0);e.getAttribute(Attribute.KNOCKBACK_RESISTANCE).setBaseValue(1);
            e.addScoreboardTag("evergarden_unique");com.example.voidscape.compat.LevelledMobsCompat.tagMob(e,plugin,true);});
        TextDisplay label=at.getWorld().spawn(at.clone().add(0,2.15,0),TextDisplay.class,e->{tag(e);e.setBillboard(Display.Billboard.CENTER);e.text(net.kyori.adventure.text.Component.text("BREAK ICE: 3 hits / ENDER PEARL"));});
        Prison prison=new Prison(s,p,tip,receiver,spike,label,now);prisons.put(p.getUniqueId(),prison);
        if(p.isInsideVehicle())p.leaveVehicle();p.setGliding(false);
        prison.lifting=true;boolean lifted;
        try{lifted=p.teleport(tip);}finally{prison.lifting=false;}
        if(!lifted){release(prison,false,now);return;}
        p.setGravity(false);p.setVelocity(new Vector());p.setFallDistance(0);
        text(p,"IMPALED - look down and hit ice 3 times, or throw an ENDER PEARL! 2.5s");
    }
    boolean rain(RiverTrials.State s,Player target,List<Player> players,long now,long age){
        // Tall committed rain can outlive the two authored rounds, without starting a third.
        if(age>=RAIN_ROUND*2){host.renderTrial(s.owner,target,"rain_recover",1);return arrows.stream().noneMatch(shot->shot.state==s);}
        int frame=(int)(age%RAIN_ROUND);
        if(frame==0){if(age>0){clearMarks(s);mark(s,players);}rainVictims.put(s,new HashSet<>());}
        pose(s,target,"rain",frame,24,12);
        if(frame<RAIN_WARNING&&now%3==0)for(int i=0;i<s.marks.size();i++){
            ring(key(s)+":mark:"+i,s.marks.get(i),RAIN_RADIUS,now);
            List<RainMark> marks=rainMarks.get(s);
            if(marks!=null&&i<marks.size()&&marks.get(i).skyY>center.getY()+12.01){
                Location sky=marks.get(i).ground.clone();sky.setY(marks.get(i).skyY);
                sigils.circle(key(s)+":mark:"+i+":sky","river_cocytus",sky,RAIN_RADIUS,now*.025,false);
            }
        }
        if(frame==28){Location from=s.owner.visual==null?s.origin.clone().add(0,7,0):s.owner.visual.weaponOrigin();
            // A visible upward shot connects the bow animation to the falling rain.
            shoot(s,from,new Vector(0,1.8,0),now+7,0,new HashSet<>(),null);
            s.origin.getWorld().playSound(s.origin,Sound.ENTITY_ARROW_SHOOT,1.4f,.8f);}
        if(frame>=RAIN_WARNING&&frame<RAIN_WARNING+12&&(frame-RAIN_WARNING)%4==0){
            if(frame==RAIN_WARNING)clearMarks(s);
            Set<UUID> victims=rainVictims.computeIfAbsent(s,k->new HashSet<>());int pulse=(frame-RAIN_WARNING)/4;
            for(RainMark plan:rainMarks.getOrDefault(s,List.of())){Location mark=plan.ground.clone();mark.setY(plan.skyY);
                long until=now+Math.max(12,(long)Math.ceil((plan.skyY-center.getY())/RAIN_SPEED)+2);
                for(double x=-4.5;x<=4.5;x+=1.5)for(double z=-4.5;z<=4.5;z+=1.5){double dx=x+(pulse%2)*.55,dz=z+(pulse%2)*.55;
                    if(dx*dx+dz*dz>RAIN_RADIUS*RAIN_RADIUS)continue;
                    shoot(s,mark.clone().add(dx,0,dz),new Vector(0,-RAIN_SPEED,0),until,45,victims,null);}
                for(Location lane:plan.lanes){Location from=lane.clone();from.setY(plan.skyY);shoot(s,from,new Vector(0,-RAIN_SPEED,0),until,45,victims,null);}
            }
        }
        return age>=RAIN_ROUND*2-1&&arrows.stream().noneMatch(shot->shot.state==s);
    }
    private void shoot(RiverTrials.State s,Location from,Vector step,long until,double damage,Set<UUID> victims,Prison prison){
        if(arrows.size()>=1600)return; // Bound visual entities even with a crowded raid.
        Arrow arrow=from.getWorld().spawn(from,Arrow.class,e->{tag(e);e.setShooter(s.owner.mob);e.setGravity(false);e.setVelocity(new Vector());e.setDamage(0);e.setSilent(true);e.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);});
        // Only the swept production path below owns collision, never a second native contact.
        ((org.bukkit.craftbukkit.entity.CraftArrow)arrow).getHandle().setNoPhysics(true);
        arrow.setRotation((float)Math.toDegrees(Math.atan2(-step.getX(),step.getZ())),(float)-Math.toDegrees(Math.atan2(step.getY(),Math.hypot(step.getX(),step.getZ()))));
        arrows.add(new Shot(s,arrow,from.clone(),step.clone(),until,damage,victims,prison));
    }
    private BoundingBox cover(BlockDisplay d){Location a=d.getLocation();return new BoundingBox(a.getX()-1.3,a.getY(),a.getZ()-1.3,a.getX()+1.3,a.getY()+5.5,a.getZ()+1.3);}
    void tick(long now,List<Player> players){
        immunity.entrySet().removeIf(e->now>=e.getValue());
        for(Prison prison:new ArrayList<>(prisons.values())){
            Player p=prison.player;
            if(!p.isOnline()||p.isDead()||!players.contains(p)||!prison.receiver.isValid()||prison.spike.stream().anyMatch(e->!e.isValid())||!prison.state.owner.mob.isValid()){release(prison,false,now);continue;}
            p.setVelocity(new Vector());p.setFallDistance(0);
            long age=now-prison.caught;
            if(prison.shots<3&&age>=50+prison.shots*14){
                Location from=prison.state.owner.visual==null?prison.state.owner.mob.getLocation().add(0,7,0):prison.state.owner.visual.weaponOrigin();
                Vector delta=p.getLocation().add(0,.9,0).toVector().subtract(from.toVector());
                // <=10 tick travel leaves native immunity time between the three impacts.
                shoot(prison.state,from,delta.clone().normalize().multiply(Math.max(3,delta.length()/7)),now+12,80,new HashSet<>(),prison);prison.shots++;
                p.playSound(p.getLocation(),Sound.ENTITY_ARROW_SHOOT,1,.6f);
            }
            if(age>=100){release(prison,true,now);continue;}
            if(now%5==0){prison.label.text(net.kyori.adventure.text.Component.text("BREAK ICE: "+prison.hits+" hits | "+String.format(Locale.ROOT,"%.1fs",Math.max(0,50-age)/20.0)));
                text(p,age<50?"BREAK ICE / ENDER PEARL - "+String.format(Locale.ROOT,"%.1fs",(50-age)/20.0):"FROST EXECUTION - ESCAPE NOW!");}
        }
        // Apply impacts after iterating: death/reset may synchronously close this encounter.
        record Impact(Player player,double damage,Mob owner){}
        List<Impact> impacts=new ArrayList<>();
        for(Iterator<Shot> it=arrows.iterator();it.hasNext();){Shot shot=it.next();
            if(now>=shot.until||!shot.entity.isValid()||!shot.state.owner.mob.isValid()){remove(shot.entity);it.remove();continue;}
            Vector direction=shot.step.clone().normalize();double travel=shot.step.length();boolean blocked=false;
            var wall=shot.at.getWorld().rayTraceBlocks(shot.at,direction,travel,FluidCollisionMode.NEVER,true);
            if(wall!=null){travel=Math.min(travel,wall.getHitPosition().distance(shot.at.toVector()));blocked=true;}
            if(shot.state.move==RiverTrials.Move.BLIZZARD)for(BlockDisplay d:shot.state.ice){BoundingBox box=cover(d);var hit=box.rayTrace(shot.at.toVector(),direction,travel);
                if(box.contains(shot.at.toVector())){travel=0;blocked=true;}else if(hit!=null){travel=Math.min(travel,hit.getHitPosition().distance(shot.at.toVector()));blocked=true;}}
            Player victim=null;double nearest=travel;
            if(shot.damage>0)for(Player p:players){if(shot.victims.contains(p.getUniqueId()))continue;
                BoundingBox box=p.getBoundingBox().expand(.22);var hit=box.rayTrace(shot.at.toVector(),direction,travel);
                double distance=box.contains(shot.at.toVector())?0:hit==null?Double.POSITIVE_INFINITY:hit.getHitPosition().distance(shot.at.toVector());
                if(distance<nearest){nearest=distance;victim=p;}}
            shot.at.add(direction.multiply(victim==null?travel:nearest));shot.entity.teleport(shot.at);shot.entity.setVelocity(new Vector());
            if(victim!=null){shot.victims.add(victim.getUniqueId());impacts.add(new Impact(victim,shot.damage,shot.state.owner.mob));}
            if(victim!=null||blocked){remove(shot.entity);it.remove();}
        }
        for(Impact impact:impacts)if(impact.owner.isValid()&&host.hitTrial(impact.player,impact.damage,impact.owner))impact.player.getWorld().spawnParticle(Particle.SNOWFLAKE,impact.player.getLocation().add(0,1,0),8,.3,.5,.3,.02);
    }
    boolean damage(EntityDamageEvent event,Player attacker,long now){
        if(!owns(event.getEntity()))return false;event.setCancelled(true);
        for(Prison prison:new ArrayList<>(prisons.values()))if(prison.receiver==event.getEntity()){
            if(attacker==null||!host.accepts(attacker)||!(event instanceof EntityDamageByEntityEvent by)||by.getDamager()!=attacker||event.getFinalDamage()<=0||now<prison.strikes.getOrDefault(attacker.getUniqueId(),0L))return true;
            if(aim(attacker,prison.receiver,attacker.getAttribute(Attribute.ENTITY_INTERACTION_RANGE).getValue())==null)return true;
            prison.strikes.put(attacker.getUniqueId(),now+8);prison.hits--;
            prison.tip.getWorld().playSound(prison.tip,Sound.BLOCK_GLASS_HIT,1,1.3f);
            prison.tip.getWorld().spawnParticle(Particle.BLOCK,prison.tip,16,.4,.4,.4,Material.PACKED_ICE.createBlockData());
            if(prison.hits<=0)release(prison,false,now);return true;
        }
        return true;
    }
    private Double aim(Player p,Mob receiver,double reach){
        Location eye=p.getEyeLocation();var box=receiver.getBoundingBox().expand(.12);var hit=box.rayTrace(eye.toVector(),eye.getDirection(),reach);
        if(hit==null||!host.clearTrial(eye,hit.getHitPosition().toLocation(eye.getWorld())))return null;
        return eye.toVector().distance(hit.getHitPosition());
    }
    Map.Entry<Mob,Double> meleeAim(Player p,double reach){
        Map.Entry<Mob,Double> best=null;
        for(Prison prison:prisons.values()){Double distance=aim(p,prison.receiver,reach);if(distance!=null&&(best==null||distance<best.getValue()))best=Map.entry(prison.receiver,distance);}return best;
    }
    void moved(PlayerMoveEvent event){
        if(event instanceof PlayerTeleportEvent)return;Prison prison=prisons.get(event.getPlayer().getUniqueId());if(prison==null||prison.lifting||event.getTo()==null)return;
        Location to=prison.tip.clone();to.setYaw(event.getTo().getYaw());to.setPitch(event.getTo().getPitch());event.setTo(to);
    }
    void teleported(PlayerTeleportEvent event,long now){
        Prison prison=prisons.get(event.getPlayer().getUniqueId());if(prison!=null&&!prison.lifting)release(prison,false,now);
    }
    void teleporting(PlayerTeleportEvent event){
        Prison prison=prisons.get(event.getPlayer().getUniqueId());if(prison==null||prison.lifting)return;
        if(event.getCause()!=PlayerTeleportEvent.TeleportCause.ENDER_PEARL&&event.getCause()!=PlayerTeleportEvent.TeleportCause.COMMAND&&event.getTo()!=null&&event.getTo().getWorld()==prison.tip.getWorld())event.setCancelled(true);
    }
    void departed(Player p,long now){Prison prison=prisons.get(p.getUniqueId());if(prison!=null)release(prison,false,now);immunity.remove(p.getUniqueId());}
    private void release(Prison prison,boolean knockback,long now){
        if(!prisons.remove(prison.player.getUniqueId(),prison))return;
        remove(prison.receiver);prison.spike.forEach(this::remove);remove(prison.label);
        arrows.removeIf(shot->{if(shot.prison!=prison)return false;remove(shot.entity);return true;});
        Player p=prison.player;p.setGravity(prison.gravity);p.setFallDistance(0);immunity.put(p.getUniqueId(),now+100);
        if(p.isOnline()&&!p.isDead()){text(p,"ICE SHATTERED");
            if(knockback){Vector away=p.getLocation().toVector().subtract(prison.state.owner.mob.getLocation().toVector()).setY(0);if(away.lengthSquared()<.01)away=new Vector(1,0,0);p.setVelocity(away.normalize().multiply(1.1).setY(.55));}}
    }
    private void clearMarks(RiverTrials.State s){for(int i=0;i<s.marks.size();i++){sigils.remove(key(s)+":mark:"+i);sigils.remove(key(s)+":mark:"+i+":sky");}}
    private void shatter(RiverTrials.State s){for(BlockDisplay ice:s.ice)ice.getWorld().spawnParticle(Particle.BLOCK,ice.getLocation().add(0,1,0),12,.7,1,.7,Material.PACKED_ICE.createBlockData());}
    void cancel(RiverTrials.State s,long now){
        clearMarks(s);sigils.remove(key(s)+":storm");rainVictims.remove(s);rainMarks.remove(s);
        for(Prison prison:new ArrayList<>(prisons.values()))if(prison.state==s)release(prison,false,now);
        arrows.removeIf(shot->{if(shot.state!=s)return false;remove(shot.entity);return true;});
        for(BlockDisplay d:s.ice)owned.remove(d.getUniqueId());
    }
    void clear(long now){for(Prison prison:new ArrayList<>(prisons.values()))release(prison,false,now);for(Shot shot:arrows)remove(shot.entity);arrows.clear();owned.clear();rainVictims.clear();rainMarks.clear();immunity.clear();}
}
