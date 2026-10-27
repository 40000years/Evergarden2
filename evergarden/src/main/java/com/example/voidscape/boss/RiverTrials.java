package com.example.voidscape.boss;

import com.example.voidscape.VoidscapePlugin;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.event.entity.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.*;
import org.bukkit.util.Vector;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.util.*;
import static com.example.voidscape.boss.ArkhaiaWaves.Kind.*;

/** Three readable encounters. Geometry, deadlines and children belong to one spirit. */
final class RiverTrials {
    enum Move { VOLLEY, FISSURE, BLIZZARD, SHADOWS, TRAIL, BOLT, RITUAL, COMBO, DASH, EXECUTION, SIPHON, BERSERK }
    static final class State {
        final ArkhaiaMinions.Spirit owner;
        Move move;long start,next,recoveryUntil;int turns,pulse,sacrificed,stagger;boolean staggered;
        Location origin,aim;UUID victim;
        long nextSiphon,nextBerserk;
        final List<Location> marks=new ArrayList<>();
        final List<BlockDisplay> ice=new ArrayList<>();
        final List<Shadow> shadows=new ArrayList<>();
        final Set<UUID> victims=new HashSet<>();
        State(ArkhaiaMinions.Spirit s,long now){owner=s;next=now+40;nextSiphon=now+40;nextBerserk=now+240;}
    }
    static final class Shadow {
        final State state;final Vex mob;double health=60;long next;Location strike;final Map<UUID,Long> hits=new HashMap<>();
        Shadow(State s,Vex m,long now){state=s;mob=m;next=now+30;}
    }
    private record Cold(int stacks,long until,long immune){}
    private final VoidscapePlugin plugin;private final ArkhaiaMinions host;
    private final Location center;private final JudgeSigils sigils;
    private final Map<UUID,State> states=new HashMap<>();
    private final Map<UUID,Cold> cold=new HashMap<>();
    private final CocytusEncounter frost;
    private final MalefeasanceEncounter dimensions;
    private long clock;
    RiverTrials(VoidscapePlugin p,ArkhaiaMinions host,Location center,JudgeSigils sigils){this.plugin=p;this.host=host;this.center=center;this.sigils=sigils;frost=new CocytusEncounter(p,host,center,sigils);dimensions=new MalefeasanceEncounter(p,host,center,sigils);}
    private boolean supported(ArkhaiaMinions.Spirit s){return s.kind==COCYTUS||s.kind==MALEFEASANCE||s.kind==STYX;}
    private String key(State s){return "trial:"+s.owner.mob.getUniqueId();}
    boolean owns(Entity e){return frost.owns(e)||states.values().stream().anyMatch(s->s.ice.contains(e)||s.shadows.stream().anyMatch(x->x.mob==e));}
    private State state(ArkhaiaMinions.Spirit s,long now){return states.computeIfAbsent(s.mob.getUniqueId(),id->new State(s,now));}
    boolean aerialReady(ArkhaiaMinions.Spirit unit,long now){
        State s=states.get(unit.mob.getUniqueId());
        return s==null||s.move==null&&now>=s.next&&now>=s.recoveryUntil;
    }
    boolean aerialStrike(ArkhaiaMinions.Spirit unit,Player player,long now){return styxStrike(state(unit,now),player,16,now);}
    void chill(Player p,long now){
        Cold old=cold.get(p.getUniqueId());if(old!=null&&now<old.immune)return;
        int count=old==null||now>=old.until?1:old.stacks+1;
        if(count>=3){cold.put(p.getUniqueId(),new Cold(0,now+100,now+100));p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,40,1,false,true));
            p.sendActionBar(net.kyori.adventure.text.Component.text("FROST BREAK - cold cleared; 5s immunity"));}
        else{cold.put(p.getUniqueId(),new Cold(count,now+100,0));p.sendActionBar(net.kyori.adventure.text.Component.text("CHILL "+count+"/3"));}
    }
    double incoming(ArkhaiaMinions.Spirit unit,double damage,long now){
        if(!supported(unit))return damage;State s=state(unit,now);
        if(unit.kind==MALEFEASANCE)return damage;
        if(now<s.recoveryUntil){damage*=1.5;
            if(unit.kind==STYX&&!s.staggered&&++s.stagger>=3){s.staggered=true;s.move=null;unit.stunUntil=now+60;s.recoveryUntil=now+60;s.next=now+85;
                unit.mob.getWorld().playSound(unit.mob.getLocation(),Sound.BLOCK_ANVIL_LAND,1,.6f);}
        }
        return damage;
    }
    private void styxHeal(ArkhaiaMinions.Spirit unit,double amount){
        if(amount<=0||!unit.mob.isValid()||unit.health<=0)return;
        unit.health=Math.min(unit.maxHealth(),unit.health+amount);unit.mob.setHealth(unit.health);unit.bar.setProgress(unit.health/unit.maxHealth());
    }
    boolean styxIncoming(ArkhaiaMinions.Spirit unit,double damage,long now){
        if(now>=unit.berserkUntil)return false;
        styxHeal(unit,damage);unit.mob.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING,unit.mob.getLocation().add(0,4,0),16,1,2,1,.15);return true;
    }
    boolean styxStrike(State state,Player player,double damage,long now){
        var unit=state.owner;boolean execution=state.move==Move.EXECUTION;
        double amount=(execution?32:damage)*(unit.enraged?1.3:1);double before=player.getHealth();
        boolean landed=host.styxHit(player,amount,unit,execution);
        if(landed&&now<unit.siphonUntil)styxHeal(unit,Math.min(Math.max(0,before-player.getHealth()),execution?amount:Double.MAX_VALUE)*.5);
        return landed;
    }
    boolean damage(EntityDamageEvent event,Player attacker,long now){
        if(frost.damage(event,attacker,now))return true;
        for(State s:states.values())for(Shadow shadow:new ArrayList<>(s.shadows))if(shadow.mob==event.getEntity()){
            double amount=event.getFinalDamage();event.setCancelled(true);
            if(attacker==null||!host.accepts(attacker)||!Double.isFinite(amount)||amount<=0||now<shadow.hits.getOrDefault(attacker.getUniqueId(),0L))return true;
            if(event instanceof EntityDamageByEntityEvent by&&by.getDamager()==attacker){
                Location eye=attacker.getEyeLocation();double reach=attacker.getAttribute(Attribute.ENTITY_INTERACTION_RANGE).getValue();
                var contact=shadow.mob.getBoundingBox().expand(.15).rayTrace(eye.toVector(),eye.getDirection(),reach);
                if(contact==null||!host.clearTrial(eye,contact.getHitPosition().toLocation(eye.getWorld())))return true;
            }
            shadow.hits.put(attacker.getUniqueId(),now+10);double before=shadow.health;shadow.health=Math.max(0,shadow.health-amount);
            ArkhaiaHitFeedback.confirm(attacker,shadow.mob,event,before-shadow.health,false);
            if(shadow.health==0){shadow.mob.remove();s.shadows.remove(shadow);sigils.remove(key(s)+":tether:"+shadow.mob.getUniqueId());
                if(s.move!=Move.RITUAL){s.owner.stunUntil=Math.max(s.owner.stunUntil,now+12);s.recoveryUntil=now+30;}}
            else shadow.mob.customName(net.kyori.adventure.text.Component.text("Bound Shadow | "+(int)Math.ceil(shadow.health)+" / 60"));
            return true;
        }
        return false;
    }
    void tick(long now,List<Player> players){
        clock=now;frost.tick(now,players);dimensions.tick(now,players);
        cold.entrySet().removeIf(e->now>=e.getValue().until||players.stream().noneMatch(p->p.getUniqueId().equals(e.getKey())));
        for(State s:states.values()){
            if(s.owner.kind==STYX)styxAura(s,now);
            for(Shadow x:new ArrayList<>(s.shadows))if(!x.mob.isValid()){s.shadows.remove(x);sigils.remove(key(s)+":tether:"+x.mob.getUniqueId());}
            if(players.isEmpty()){cancel(s);s.next=Math.max(s.next,now+30);continue;}
            for(Shadow x:s.shadows){
                if(now%4==0){sigils.fastRay(key(s)+":tether:"+x.mob.getUniqueId(),"judge_ray",x.mob.getLocation().add(0,.5,0),s.owner.mob.getLocation().add(0,5,0),.1);
                    x.mob.getWorld().spawnParticle(Particle.SOUL,x.mob.getLocation(),2,.15,.2,.15,0);}
                if(s.move==Move.RITUAL)continue;
                Player p=players.stream().min(Comparator.comparingDouble(t->t.getLocation().distanceSquared(x.mob.getLocation()))).orElse(null);
                if(p!=null&&now>=x.next){x.next=now+50;x.strike=x.mob.getLocation();}
                if(x.strike!=null&&now<x.next-36){if(now%3==0)circle(x.strike,2,Color.PURPLE);}
                else if(x.strike!=null&&now>=x.next-36){Location at=x.strike;x.strike=null;circle(at,2,Color.FUCHSIA);for(Player t:players)if(near(t,at,2)&&host.clearTrial(at.clone().add(0,.5,0),t.getLocation().add(0,.8,0)))host.hitTrial(t,12,x.mob);}
                else if(p!=null){Location at=x.mob.getLocation();Vector delta=p.getLocation().toVector().subtract(at.toVector()).setY(0);
                    if(delta.lengthSquared()>2*2){Location next=at.clone().add(delta.normalize().multiply(.09));
                        if(next.distanceSquared(s.owner.mob.getLocation())<16*16&&host.clearTrial(at.clone().add(0,.5,0),next.clone().add(0,.5,0)))x.mob.teleport(next);}}
            }
        }
    }
    boolean step(ArkhaiaMinions.Spirit unit,Player target,List<Player> players,long now){
        if(unit.kind==MALEFEASANCE)return dimensions.step(unit,target,players,now);
        if(!supported(unit))return false;State s=state(unit,now);
        if(now%10==0)unit.bar.setTitle(ArkhaiaMinions.name(unit.kind)+" | "+(int)Math.ceil(unit.health)+" / "+(int)unit.maxHealth()
            +(unit.kind==MALEFEASANCE?" | SHADOWS "+s.shadows.size()+"/2":unit.kind==STYX?" | STAGGER "+s.stagger+"/3"+(now<unit.siphonUntil?" | BLOOD "+((unit.siphonUntil-now+19)/20)+"s":"")+(now<unit.berserkUntil?" | BERSERK "+((unit.berserkUntil-now+19)/20)+"s":"")+(unit.enraged?" | ENRAGED +30%":""):"")
            +(now<s.recoveryUntil?" | EXPOSED x1.5":""));
        if(now<unit.stunUntil){unit.mob.setAI(false);unit.mob.getPathfinder().stopPathfinding();host.renderTrial(unit,target,"stun",1);return true;}
        if(s.move==null){
            if(now<s.next){
                if(now<s.recoveryUntil){unit.mob.setAI(false);unit.mob.getPathfinder().stopPathfinding();host.renderTrial(unit,target,"stun",1);}
                else chase(s,target,now);return true;
            }
            if(!unit.mob.hasLineOfSight(target)){chase(s,target,now);return true;}
            Move move;
            if(unit.kind==COCYTUS)move=switch(s.turns++%3){case 0->Move.BLIZZARD;case 1->Move.FISSURE;default->Move.VOLLEY;};
            else if(unit.kind==MALEFEASANCE)move=switch(s.turns++%4){case 0->Move.SHADOWS;case 1->Move.TRAIL;case 2->Move.BOLT;default->Move.RITUAL;};
            else if(now>=s.nextSiphon)move=Move.SIPHON;
            else if(now>=s.nextBerserk)move=Move.BERSERK;
            else move=Math.hypot(unit.mob.getX()-target.getX(),unit.mob.getZ()-target.getZ())>12?Move.DASH:s.turns++%3==2?Move.EXECUTION:Move.COMBO;
            begin(s,move,target,now);
        }
        unit.mob.setAI(false);unit.mob.getPathfinder().stopPathfinding();long age=now-s.start;
        switch(s.move){
            case VOLLEY->volley(s,target,players,now,age);
            case FISSURE->fissure(s,target,players,now,age);
            case BLIZZARD->blizzard(s,target,players,now,age);
            case SHADOWS->summon(s,target,now,age);
            case TRAIL->trail(s,target,players,now,age);
            case BOLT->{pose(s,target,"attack",age,20,8);if(age==24)host.boltTrial(unit,s.aim);if(age>=42)finish(s,now,12);}
            case RITUAL->ritual(s,target,players,now,age);
            case COMBO->combo(s,target,players,now,age);
            case DASH->dash(s,target,players,now,age);
            case EXECUTION->execution(s,target,players,now,age);
            case SIPHON,BERSERK->styxBuff(s,target,now,age);
        }
        return true;
    }
    private void chase(State s,Player p,long now){
        Mob m=s.owner.mob;Location ground=p.getLocation();ground.setY(center.getY());double distance=m.getLocation().distanceSquared(ground);
        boolean move=s.owner.kind==STYX?distance>6*6:distance>15*15;
        m.setAI(move);if(move&&now%10==0){m.setTarget(p);Vector delta=ground.toVector().subtract(m.getLocation().toVector());
            m.getPathfinder().moveTo(m.getLocation().add(delta.normalize().multiply(Math.min(10,Math.sqrt(distance)))),.9);}
        else if(!move)m.getPathfinder().stopPathfinding();
        host.renderTrial(s.owner,p,move?"walk":"idle",1);
    }
    void begin(State s,Move move,Player target,long now){
        cancelMove(s);s.move=move;s.start=now;s.origin=s.owner.mob.getLocation();s.aim=move==Move.VOLLEY||move==Move.BOLT?target.getEyeLocation():target.getLocation();s.victim=target.getUniqueId();s.pulse=0;s.sacrificed=0;
        if(move==Move.TRAIL)s.marks.add(ground(target.getLocation()));
        if(move==Move.RITUAL)for(Shadow x:s.shadows)x.strike=null;
        face(s,s.aim);s.origin.setYaw(s.owner.mob.getLocation().getYaw());
        if(s.owner.kind==COCYTUS)frost.begin(s,s.owner.mob.getWorld().getPlayers().stream().filter(host::accepts).toList(),now);
        s.owner.mob.getWorld().playSound(s.origin,Sound.BLOCK_AMETHYST_BLOCK_RESONATE,1,.7f);
    }
    private Vector direction(State s){double a=Math.toRadians(s.origin.getYaw());return new Vector(-Math.sin(a),0,Math.cos(a));}
    private Vector right(State s){Vector d=direction(s);return new Vector(d.getZ(),0,-d.getX());}
    private Location forward(State s,double length){return s.origin.clone().add(direction(s).multiply(length));}
    private void face(State s,Location at){s.owner.mob.setRotation((float)Math.toDegrees(Math.atan2(s.owner.mob.getX()-at.getX(),at.getZ()-s.owner.mob.getZ())),0);}
    private void pose(State s,Player p,String prefix,long age,int windup,int active){
        String stage=age<windup?"windup":age<windup+active?"active":"recover";
        double rate=stage.equals("windup")?(prefix.equals("attack")?16.0:20.0)/windup:1;
        host.renderTrial(s.owner,p,prefix+"_"+stage,rate);
    }
    private void finish(State s,long now,int recovery){
        cancelMove(s);s.recoveryUntil=now+recovery;s.stagger=0;s.staggered=false;
        s.next=now+recovery+(s.owner.kind==COCYTUS?(s.owner.health<s.owner.maxHealth()*.4?8:16):(s.owner.health<s.owner.maxHealth()*.4?12:24));
    }
    private void volley(State s,Player p,List<Player> players,long now,long age){
        if(frost.rain(s,p,players,now,age))finish(s,now,20);
    }
    private void fissure(State s,Player p,List<Player> players,long now,long age){
        if(frost.spikes(s,p,players,now,age))finish(s,now,20);
    }
    private void blizzard(State s,Player p,List<Player> players,long now,long age){
        if(frost.storm(s,p,players,now,age))finish(s,now,36);
    }
    void moved(org.bukkit.event.player.PlayerMoveEvent event){frost.moved(event);}
    void teleporting(org.bukkit.event.player.PlayerTeleportEvent event){frost.teleporting(event);}
    void teleported(org.bukkit.event.player.PlayerTeleportEvent event){frost.teleported(event,clock);}
    void departed(Player player){frost.departed(player,clock);dimensions.departed(player);}
    boolean revive(ArkhaiaMinions.Spirit unit,long now){return dimensions.revive(unit,now);}
    boolean trapped(Player player){return frost.trapped(player);}
    Map.Entry<Mob,Double> meleeAim(Player player,double reach){return frost.meleeAim(player,reach);}
    private void summon(State s,Player p,long now,long age){
        pose(s,p,"cast",age,32,10);
        if(age<32&&now%4==0)circle(s.origin,5,Color.PURPLE);
        if(age==33){
            for(int i=s.shadows.size();i<2;i++){Location at=forward(s,7).add(right(s).multiply(i==0?-5:5));
                if(!safe(at,2))continue;
                Vex m=at.getWorld().spawn(at,Vex.class,e->{e.setPersistent(false);e.setAI(false);e.setGravity(false);e.setSilent(true);e.setRemoveWhenFarAway(false);
                    e.getPersistentDataContainer().set(plugin.key("world_boss_entity"),PersistentDataType.BYTE,(byte)1);
                    com.example.voidscape.compat.LevelledMobsCompat.tagMob(e,plugin,true);e.getAttribute(Attribute.MAX_HEALTH).setBaseValue(60);e.setHealth(60);
                    e.getAttribute(Attribute.ATTACK_DAMAGE).setBaseValue(0);e.getAttribute(Attribute.KNOCKBACK_RESISTANCE).setBaseValue(1);
                    e.customName(net.kyori.adventure.text.Component.text("Bound Shadow | 60 / 60"));e.setCustomNameVisible(true);});
                s.shadows.add(new Shadow(s,m,now));
            }
        }
        if(age>=55)finish(s,now,12);
    }
    private void trail(State s,Player p,List<Player> players,long now,long age){
        int part=(int)(age%30);pose(s,p,"cast",part,24,6);
        if(part==0&&age>0){Player marked=Bukkit.getPlayer(s.victim);s.marks.add(ground(marked!=null&&players.contains(marked)?marked.getLocation():s.aim));}
        Location mark=s.marks.getLast();
        if(part<24&&now%3==0)circle(mark,3,Color.PURPLE);
        if(part==24){circle(mark,3,Color.FUCHSIA);for(Player t:players)if(near(t,mark,3)&&host.clearTrial(mark.clone().add(0,.3,0),t.getLocation().add(0,.8,0)))host.hitTrial(t,30,s.owner.mob);}
        if(age>=89)finish(s,now,26);
    }
    private void ritual(State s,Player p,List<Player> players,long now,long age){
        if(age<100){
            host.renderTrial(s.owner,p,"cast_windup",.2);
            if(now%4==0)circle(s.owner.mob.getLocation(),4,Color.PURPLE);
            if(now%20==0)for(Player t:players)t.sendActionBar(net.kyori.adventure.text.Component.text("STOP THE RITUAL: "+s.shadows.size()+" shadows | "+((100-age+19)/20)+"s"));
            for(Shadow x:new ArrayList<>(s.shadows)){
                Location at=x.mob.getLocation();Vector delta=s.owner.mob.getLocation().toVector().subtract(at.toVector()).setY(0);
                if(delta.length()<2){s.sacrificed++;x.mob.remove();s.shadows.remove(x);sigils.remove(key(s)+":tether:"+x.mob.getUniqueId());}
                else{Location next=at.clone().add(delta.normalize().multiply(.07));if(host.clearTrial(at.clone().add(0,.5,0),next.clone().add(0,.5,0)))x.mob.teleport(next);}
            }
            return;
        }
        // Survivors at the deadline are sacrificed, never left as permanent reductions.
        if(age==100){s.sacrificed+=s.shadows.size();clearShadows(s);s.origin=s.owner.mob.getLocation();}
        if(s.sacrificed==0){finish(s,now,100);return;}
        long waveAge=age-100;int pulse=(int)(waveAge/40);int frame=(int)(waveAge%40);
        if(pulse>=s.sacrificed){finish(s,now,45);return;}
        pose(s,p,"cast",frame,20,12);
        if(frame<20&&now%3==0)circle(s.origin,Math.max(2,frame*.6),Color.PURPLE);
        if(frame==20)s.victims.clear();
        if(frame>=20&&frame<36){double radius=(frame-19)*2;
            circle(s.origin,radius,Color.FUCHSIA);
            for(Player t:players){double distance=Math.hypot(t.getX()-s.origin.getX(),t.getZ()-s.origin.getZ());
                if(!s.victims.contains(t.getUniqueId())&&Math.abs(distance-radius)<=1.3&&host.clearTrial(s.origin.clone().add(0,.6,0),t.getLocation().add(0,.8,0))){s.victims.add(t.getUniqueId());host.hitTrial(t,36,s.owner.mob);}}
        }
    }
    private void combo(State s,Player p,List<Player> players,long now,long age){
        int part=(int)(age/38),frame=(int)(age%38);if(part>=3){finish(s,now,44);return;}
        String prefix=part==0?"slash":part==1?"reverse":"thrust";pose(s,p,prefix,frame,20,8);
        if(frame==0){face(s,p.getLocation());s.origin=s.owner.mob.getLocation();s.victims.clear();}
        if(frame<20&&now%3==0){if(part==2)line(s.origin,forward(s,11),Color.YELLOW,1);else arc(s,part==0?-75:75,part==0?75:-75,8,Color.YELLOW);}
        if(frame>=20&&frame<28){
            if(part==2){line(s.origin,forward(s,11),Color.ORANGE,1);strikeLine(s,s.origin,forward(s,11),1.1,40,players);}
            else for(int sample=0;sample<=3;sample++){
                double angle=(part==0?-75:75)+(part==0?1:-1)*(frame-20+sample/3.0)*150.0/8;
                Location to=s.origin.clone().add(direction(s).rotateAroundY(Math.toRadians(angle)).multiply(8));line(s.origin,to,Color.ORANGE,.65);strikeLine(s,s.origin,to,.65,32,players);}
        }
    }
    private void dash(State s,Player p,List<Player> players,long now,long age){
        pose(s,p,"dash",age,28,12);
        if(age<28&&now%3==0)line(s.origin,forward(s,19),Color.YELLOW,1.5);
        if(age>=28&&age<40){
            Location previous=s.owner.mob.getLocation();Vector delta=direction(s).multiply(1.35);Location next=previous.clone().add(delta);
            if(!safe(next,10)){s.owner.stunUntil=now+60;finish(s,now,60);return;}
            ((org.bukkit.craftbukkit.entity.CraftEntity)s.owner.mob).getHandle().move(net.minecraft.world.entity.MoverType.SELF,new net.minecraft.world.phys.Vec3(delta.getX(),0,delta.getZ()));
            Location actual=s.owner.mob.getLocation();line(previous,actual,Color.ORANGE,1.5);strikeLine(s,previous,actual,1.8,38,players);
            if(actual.distanceSquared(previous)<.15){s.owner.stunUntil=now+60;finish(s,now,60);return;}
        }
        if(age>=48)finish(s,now,35);
    }
    private void execution(State s,Player p,List<Player> players,long now,long age){
        pose(s,p,"cast",age,44,12);
        if(age<44&&now%3==0)line(s.origin,forward(s,18),Color.YELLOW,2);
        if(age==44){line(s.origin,forward(s,18),Color.RED,2);strikeLine(s,s.origin,forward(s,18),2,32,players);}
        if(age>=56)finish(s,now,60);
    }
    private void strikeLine(State s,Location a,Location b,double width,double damage,List<Player> players){
        Vector delta=b.toVector().subtract(a.toVector());double length=delta.length();if(length<.01)return;
        for(Player p:players){if(s.victims.contains(p.getUniqueId()))continue;
            var box=p.getBoundingBox().expand(width,1.5,width);Vector start=a.clone().add(0,.8,0).toVector();
            if((box.contains(start)||box.rayTrace(start,delta.clone().normalize(),length)!=null)&&host.clearTrial(a.clone().add(0,.8,0),p.getLocation().add(0,.8,0))){
                if(styxStrike(s,p,damage,clock))s.victims.add(p.getUniqueId());
            }
        }
    }
    private void arc(State s,double from,double to,double radius,Color color){
        for(int i=0;i<=24;i++){double angle=Math.toRadians(from+(to-from)*i/24);Location at=s.origin.clone().add(direction(s).rotateAroundY(angle).multiply(radius));dust(at,color);}
    }
    private boolean near(Player p,Location at,double radius){return p.getWorld()==at.getWorld()&&Math.abs(p.getY()-at.getY())<3.5&&Math.hypot(p.getX()-at.getX(),p.getZ()-at.getZ())<=radius+.3;}
    private Location ground(Location at){Location p=at.clone();p.setY(center.getY());return p;}
    private boolean safe(Location at,int height){
        if(Math.hypot(at.getX()-center.getX(),at.getZ()-center.getZ())>33||!at.clone().subtract(0,1,0).getBlock().getType().isSolid())return false;
        for(double dx:new double[]{-.9,.9})for(double dz:new double[]{-.9,.9})for(int y=0;y<height;y++)if(!at.clone().add(dx,y,dz).getBlock().isPassable())return false;
        return true;
    }
    private BlockDisplay display(Location at,Material material,double x,double y,double z){return at.getWorld().spawn(at,BlockDisplay.class,e->{e.setPersistent(false);e.setInvulnerable(true);e.setBlock(material.createBlockData());e.setBrightness(new Display.Brightness(15,15));
        e.getPersistentDataContainer().set(plugin.key("world_boss_entity"),PersistentDataType.BYTE,(byte)1);
        e.setTransformation(new Transformation(new Vector3f((float)-x/2,0,(float)-z/2),new Quaternionf(),new Vector3f((float)x,(float)y,(float)z),new Quaternionf()));});}
    private void dust(Location at,Color c){at.getWorld().spawnParticle(Particle.DUST,at.clone().add(0,.18,0),1,0,0,0,0,new Particle.DustOptions(c,1.6f),true);}
    private void circle(Location at,double radius,Color c){for(int i=0;i<48;i++){double a=i*Math.PI/24;dust(at.clone().add(Math.cos(a)*radius,0,Math.sin(a)*radius),c);}}
    private void line(Location a,Location b,Color c,double width){
        Vector delta=b.toVector().subtract(a.toVector());double length=delta.length();if(length<.01)return;Vector d=delta.normalize(),r=new Vector(d.getZ(),0,-d.getX()).multiply(width);
        var wall=a.getWorld().rayTraceBlocks(a.clone().add(0,.5,0),d,length,FluidCollisionMode.NEVER,true);if(wall!=null)length=Math.min(length,wall.getHitPosition().distance(a.clone().add(0,.5,0).toVector()));
        for(double step=0;step<=length;step+=.6){Location at=a.clone().add(d.clone().multiply(step));dust(at.clone().add(r),c);dust(at.clone().subtract(r),c);}
    }
    private void clearShadows(State s){for(Shadow x:s.shadows){sigils.remove(key(s)+":tether:"+x.mob.getUniqueId());x.mob.remove();}s.shadows.clear();}
    private void styxBuff(State s,Player target,long now,long age){
        boolean blood=s.move==Move.SIPHON;pose(s,target,blood?"blood":"berserk",age,24,10);
        if(age<24&&now%3==0){sigils.circle(key(s)+":buff",ArkhaiaMinions.sigil(STYX),s.owner.mob.getLocation().add(0,.12,0),4.5,now*.09,false);circle(s.owner.mob.getLocation(),4.5,blood?Color.RED:Color.AQUA);}
        if(age>=24&&s.pulse==0){s.pulse=1;
            if(blood){s.owner.siphonUntil=now+200;s.nextSiphon=now+360;}else{s.owner.berserkUntil=now+100;s.nextBerserk=now+440;}
            s.owner.mob.getWorld().playSound(s.owner.mob.getLocation(),Sound.ENTITY_RAVAGER_ROAR,1,blood?.7f:1.1f);sigils.remove(key(s)+":buff");}
        if(age>=48)finish(s,now,10);
    }
    private void styxAura(State s,long now){
        for(boolean blood:new boolean[]{true,false}){
            String id=key(s)+(blood?":blood":":berserk");boolean active=now<(blood?s.owner.siphonUntil:s.owner.berserkUntil);
            if(!active){sigils.remove(id);continue;}
            if(now%3==0){sigils.circle(id,ArkhaiaMinions.sigil(STYX),s.owner.mob.getLocation().add(0,blood?.16:4.5,0),blood?3.8:3.2,(blood?1:-1)*now*.07,false);
                for(int i=0;i<12;i++){double a=i*Math.PI/6+now*.12;dust(s.owner.mob.getLocation().add(Math.cos(a)*2.5,1+(now*.15+i*.55)%7,Math.sin(a)*2.5),blood?Color.RED:Color.AQUA);}}
        }
    }
    private void cancelMove(State s){frost.cancel(s,clock);for(BlockDisplay d:s.ice)d.remove();s.ice.clear();s.marks.clear();s.victims.clear();s.move=null;}
    private void cancel(State s){cancelMove(s);clearShadows(s);if(s.owner.kind==STYX){s.owner.siphonUntil=0;s.owner.berserkUntil=0;for(String suffix:List.of(":buff",":blood",":berserk"))sigils.remove(key(s)+suffix);}}
    void remove(ArkhaiaMinions.Spirit unit){dimensions.remove(unit);State s=states.remove(unit.mob.getUniqueId());if(s!=null)cancel(s);}
    void clear(){for(State s:states.values())cancel(s);states.clear();cold.clear();frost.clear(clock);dimensions.clear();}
}
