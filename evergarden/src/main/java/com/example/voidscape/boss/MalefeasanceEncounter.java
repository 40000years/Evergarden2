package com.example.voidscape.boss;

import com.example.voidscape.VoidscapePlugin;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.potion.*;
import org.bukkit.util.Vector;
import java.util.*;

/** A dimensional caster: telegraphed safe blinks every few seconds, committed sky gates and one owned rebirth into Phase 2. */
final class MalefeasanceEncounter {
    // A 0.8-second portal warning, then 7.5 seconds before the next one: players get time to chase and punish.
    static final int WARNING=24, DESCENT=6, BLINK_WARNING=16, BLINK_INTERVAL=150, FIRST_BLINK=90;
    static final double RADIUS=2.8;
    static final class State {
        final ArkhaiaMinions.Spirit owner;
        final List<Gate> gates=new ArrayList<>();
        final Map<UUID,Long> bloodMarks=new HashMap<>();
        final Map<String,Debuff> debuffs=new HashMap<>();
        long nextBatch,nextBlink,blinkAt,burstStarted,portalRecoverUntil;int batch,sequence,hops;boolean crimson;
        Location landing,departure;
        State(ArkhaiaMinions.Spirit owner,long now){this.owner=owner;nextBatch=now+14;nextBlink=now+FIRST_BLINK;burstStarted=now;}
    }
    static final class Gate {
        final String key;final Location ground,sky;final boolean crimson;final boolean phase2;final long born;
        final Set<UUID> victims=new HashSet<>();
        Gate(String key,Location ground,boolean crimson,boolean phase2,long now){this(key,ground,crimson,phase2,now,ground.getY());}
        Gate(String key,Location ground,boolean crimson,boolean phase2,long now,double targetY){this.key=key;this.ground=ground;sky=ground.clone();sky.setY(Math.max(ground.getY()+12,targetY+12));this.crimson=crimson;this.phase2=phase2;born=now;}
    }
    private record Beam(double bottom,double length){}
    private record Debuff(Player player,PotionEffectType type,int amplifier,PotionEffect previous,long began,long until){}
    private final VoidscapePlugin plugin;private final ArkhaiaMinions host;private final Location center;private final JudgeSigils sigils;
    private final Map<ArkhaiaMinions.Spirit,State> states=new IdentityHashMap<>();
    private long clock;
    MalefeasanceEncounter(VoidscapePlugin plugin,ArkhaiaMinions host,Location center,JudgeSigils sigils){this.plugin=plugin;this.host=host;this.center=center;this.sigils=sigils;}
    private State state(ArkhaiaMinions.Spirit owner,long now){return states.computeIfAbsent(owner,k->new State(owner,now));}
    private String key(State s){return "malef:"+s.owner.mob.getUniqueId();}
    private String seal(boolean secondary,boolean phase2){
        if(phase2)return secondary?"malef_gold_seal":"malef_cyan_seal";
        return secondary?"malef_crimson_seal":"malef_violet_seal";
    }
    private String ray(boolean secondary,boolean phase2){
        if(phase2)return secondary?"malef_gold_ray":"malef_cyan_ray";
        return secondary?"malef_crimson_ray":"malef_violet_ray";
    }
    private Color color(boolean secondary,boolean phase2){
        if(phase2)return secondary?Color.fromRGB(250,210,70):Color.fromRGB(34,180,216);
        return secondary?Color.fromRGB(255,48,93):Color.fromRGB(184,84,255);
    }
    private Location ground(Location at){Location p=at.clone();p.setY(center.getY()+.08);return p;}
    private boolean within(Location at,double radius){return at.getWorld()==center.getWorld()&&Math.hypot(at.getX()-center.getX(),at.getZ()-center.getZ())<=radius;}
    private void dust(Location at,Color color,float size){at.getWorld().spawnParticle(Particle.DUST,at,1,0,0,0,0,new Particle.DustOptions(color,size),true);}
    private void motes(Location at,double radius,Color color,long now){
        for(int i=0;i<16;i++){double a=i*Math.PI/8+now*.11;dust(at.clone().add(Math.cos(a)*radius,.15+Math.sin(a*3)*.12,Math.sin(a)*radius),color,1.4f);}
    }
    boolean step(ArkhaiaMinions.Spirit owner,Player target,List<Player> players,long now){
        State s=state(owner,now);owner.mob.setAI(false);owner.mob.getPathfinder().stopPathfinding();
        boolean p2=owner.resurrected;
        if(now%5==0){
            if(p2){
                owner.bar.setColor(s.crimson?org.bukkit.boss.BarColor.YELLOW:org.bukkit.boss.BarColor.BLUE);
                owner.bar.setProgress(Math.clamp(owner.health/owner.maxHealth(),0,1));
                owner.bar.setTitle("Malefeasance | ASTRAL ARCH-SORCERESS | "+(int)Math.ceil(owner.health)+" / "+(int)owner.maxHealth()+" | PHASE 2");
            }else{
                owner.bar.setColor(s.crimson?org.bukkit.boss.BarColor.RED:org.bukkit.boss.BarColor.PURPLE);
                owner.bar.setProgress(Math.clamp(owner.health/owner.maxHealth(),0,1));
                owner.bar.setTitle("Malefeasance | "+(int)Math.ceil(owner.health)+" / "+(int)owner.maxHealth()+" | MAGIC IMMUNE | REBIRTH READY");
            }
        }
        if(now<owner.revivalUntil){rebirthFrame(s,target,now);return true;}
        clearRevival(s);
        if(s.blinkAt>0){
            long remaining=s.blinkAt-now;host.renderTrial(owner,target,remaining>0?"portal_windup":"portal_active",remaining>0?20.0/BLINK_WARNING:1);
            portal(key(s)+":arrival",s.landing,now,false,p2);portal(key(s)+":departure",s.departure,now,true,p2);
            if(now>=s.blinkAt){
                if(safe(s.landing,players)&&owner.mob.teleport(s.landing)){s.hops++;burst(s.departure,p2);burst(s.landing,p2);}
                clearBlink(s);s.nextBlink=now+BLINK_INTERVAL;s.portalRecoverUntil=now+10;
            }
        }else if(now>=s.nextBlink){
            Location landing=landing(s,target,players);
            if(landing!=null){s.departure=owner.mob.getLocation();s.landing=landing;s.blinkAt=now+BLINK_WARNING;
                host.renderTrial(owner,target,"portal_windup",20.0/BLINK_WARNING);portal(key(s)+":arrival",landing,now,false,p2);}
            else{s.nextBlink=now+30;host.renderTrial(owner,target,"hover",1);}
        }else if(now<s.portalRecoverUntil){host.renderTrial(owner,target,"portal_recover",1.4);
        }else{
            long phase=Math.floorMod(now-s.burstStarted,44);
            host.renderTrial(owner,target,phase<20?"sky_windup":phase<30?"sky_active":"sky_recover",1);
        }
        if(now>=s.nextBatch){
            emit(s,players,now);s.batch++;s.nextBatch=now+10;
            if(s.batch>=8){s.batch=0;s.crimson=!s.crimson;s.nextBatch=now+24;}
        }
        return true;
    }
    private boolean safe(Location at,List<Player> players){
        if(at==null||!within(at,29)||!at.clone().subtract(0,.2,0).getBlock().getType().isSolid())return false;
        for(double x:new double[]{-1.4,0,1.4})for(double z:new double[]{-1.4,0,1.4})for(int y=0;y<11;y++)if(!at.clone().add(x,y,z).getBlock().isPassable())return false;
        return players.stream().allMatch(p->p.getWorld()!=at.getWorld()||Math.hypot(p.getX()-at.getX(),p.getZ()-at.getZ())>=3.5);
    }
    private Location landing(State s,Player target,List<Player> players){
        for(double radius:new double[]{7,9,6})for(int i=0;i<12;i++){
            double angle=(s.hops*2.399963+i*Math.PI/6);Location at=ground(target.getLocation()).add(Math.cos(angle)*radius,0,Math.sin(angle)*radius);at.setY(center.getY());
            if(at.distanceSquared(s.owner.mob.getLocation())<7*7||!safe(at,players))continue;
            at.setDirection(target.getLocation().toVector().subtract(at.toVector()).setY(0));return at;
        }
        return null;
    }
    private void portal(String id,Location at,long now,boolean departing,boolean phase2){
        if(at==null)return;Location mid=at.clone().add(0,4.5,0);
        sigils.circle(id,seal(false,phase2),mid,departing?3.2:2.6,now*.12,true);
        sigils.circle(id+":base",seal(false,phase2),ground(at),2.5,-now*.09,false);
        if(now%2==0)motes(ground(at),2.5,color(false,phase2),now);
    }
    private void burst(Location at,boolean phase2){
        if(phase2){
            at.getWorld().spawnParticle(Particle.END_ROD,at.clone().add(0,3.5,0),35,1.2,2.5,1.2,.05);
            at.getWorld().spawnParticle(Particle.ENCHANTED_HIT,at.clone().add(0,3,0),30,1,2,1,.08);
            at.getWorld().playSound(at,Sound.ENTITY_ILLUSIONER_MIRROR_MOVE,1,1.1f);
        }else{
            at.getWorld().spawnParticle(Particle.REVERSE_PORTAL,at.clone().add(0,4,0),70,1.2,3,1.2,.08);
            at.getWorld().spawnParticle(Particle.END_ROD,at.clone().add(0,3,0),24,1,2,1,.03);
            at.getWorld().playSound(at,Sound.ENTITY_ENDERMAN_TELEPORT,1,.8f);
        }
    }
    private void clearBlink(State s){for(String suffix:List.of(":arrival",":departure")){sigils.remove(key(s)+suffix);sigils.remove(key(s)+suffix+":base");}s.blinkAt=0;s.landing=null;s.departure=null;}
    void emit(State s,List<Player> players,long now){
        List<Location> marks=new ArrayList<>();
        Map<Location,Double> targetHeights=new IdentityHashMap<>();
        int start=s.sequence%Math.max(1,players.size());
        for(int i=0;i<Math.min(6,players.size());i++){Player p=players.get((start+i)%players.size());Location at=ground(p.getLocation());
            Location mark=marks.stream().filter(m->m.distanceSquared(at)<9).findFirst().orElse(null);
            if(mark==null){mark=at;marks.add(mark);}
            targetHeights.merge(mark,p.getY(),Math::max);}
        for(int i=0;i<3;i++){
            double a=(s.sequence*3+i)*2.399963;double r=7+Math.floorMod(s.sequence*7+i*11,23);
            Location at=ground(center).add(Math.cos(a)*r,0,Math.sin(a)*r);
            if(marks.stream().noneMatch(m->m.distanceSquared(at)<16))marks.add(at);
        }
        boolean p2=s.owner.resurrected;
        for(Location mark:marks)if(s.gates.size()<48&&within(mark,33))s.gates.add(new Gate(key(s)+":gate:"+(s.sequence++),mark,s.crimson,p2,now,targetHeights.getOrDefault(mark,mark.getY())));
        if(s.sequence%3==0){
            if(p2)s.owner.mob.getWorld().playSound(s.owner.mob.getLocation(),Sound.BLOCK_AMETHYST_BLOCK_CHIME,.55f,s.crimson?1.5f:1.2f);
            else s.owner.mob.getWorld().playSound(s.owner.mob.getLocation(),Sound.BLOCK_AMETHYST_BLOCK_RESONATE,.45f,s.crimson?.6f:1.3f);
        }
    }
    private Beam gateFrame(Gate gate,long now){
        long age=now-gate.born;Color tint=color(gate.crimson,gate.phase2);
        if(now%3==0){
            double fade=Math.clamp((WARNING+DESCENT+16-age)/8.0,.05,1);
            sigils.circle(gate.key+":sky",seal(gate.crimson,gate.phase2),gate.sky,3.8*fade,now*.025*(gate.crimson?-1:1),false);
            sigils.circle(gate.key+":ground",seal(gate.crimson,gate.phase2),gate.ground,RADIUS*fade,-now*.045,false);
            if(age<WARNING){motes(gate.ground,RADIUS,tint,now);
                for(int i=0;i<4;i++)dust(gate.sky.clone().add(Math.sin(i*Math.PI/2+now*.07)*3.8,0,Math.cos(i*Math.PI/2+now*.07)*3.8),tint,1.8f);}
        }
        if(age>=WARNING+DESCENT+8){sigils.remove(gate.key+":beam:x");sigils.remove(gate.key+":beam:z");return null;}
        if(age<WARNING)return null;
        double length=(gate.sky.getY()-gate.ground.getY())*Math.clamp((age-WARNING+1)/(double)DESCENT,0,1);Location end=gate.sky.clone().subtract(0,length,0);
        var wall=gate.sky.getWorld().rayTraceBlocks(gate.sky,new Vector(0,-1,0),length,FluidCollisionMode.NEVER,true);
        if(wall!=null){end=wall.getHitPosition().toLocation(gate.sky.getWorld());length=gate.sky.getY()-end.getY();}
        if(length>.01){Location mid=gate.sky.clone().subtract(0,length*.5,0);
            sigils.plane(gate.key+":beam:x",ray(gate.crimson,gate.phase2),mid,new Vector(1,0,0),new Vector(0,1,0),RADIUS*2,length,0);
            sigils.plane(gate.key+":beam:z",ray(gate.crimson,gate.phase2),mid,new Vector(0,0,1),new Vector(0,1,0),RADIUS*2,length,0);
            if(now%2==0)for(double y=end.getY();y<gate.sky.getY();y+=Math.max(1.1,length/32))for(double radius:new double[]{0,RADIUS*.85}){
                double a=now*.3+y;dust(new Location(end.getWorld(),end.getX()+Math.cos(a)*radius,y,end.getZ()+Math.sin(a)*radius),tint,radius==0?2.2f:1.1f);}
        }else{sigils.remove(gate.key+":beam:x");sigils.remove(gate.key+":beam:z");}
        if(age==WARNING){
            if(gate.phase2)gate.ground.getWorld().playSound(gate.ground,Sound.BLOCK_BEACON_ACTIVATE,.7f,gate.crimson?1.8f:1.4f);
            else gate.ground.getWorld().playSound(gate.ground,Sound.BLOCK_BEACON_ACTIVATE,.65f,gate.crimson?.7f:1.6f);
        }
        return length>.01?new Beam(end.getY(),length):null;
    }
    void tick(long now,List<Player> players){
        clock=now;
        for(State s:new ArrayList<>(states.values())){
            expire(s,now);
            if(players.isEmpty()||!s.owner.mob.isValid()){cancel(s,true);s.nextBatch=now+14;s.nextBlink=now+FIRST_BLINK;continue;}
            if(now<s.owner.revivalUntil)continue;
            for(Gate gate:new ArrayList<>(s.gates)){
                long age=now-gate.born;
                if(age>=WARNING+DESCENT+16){removeGate(gate);s.gates.remove(gate);continue;}
                Beam beam=gateFrame(gate,now);
                if(age<WARNING+DESCENT||age>=WARNING+DESCENT+8||beam==null)continue;
                for(Player p:players){
                    if(gate.victims.contains(p.getUniqueId())||p.getWorld()!=gate.ground.getWorld()||Math.hypot(p.getX()-gate.ground.getX(),p.getZ()-gate.ground.getZ())>RADIUS+.3
                        ||p.getBoundingBox().getMaxY()<=beam.bottom||p.getBoundingBox().getMinY()>=gate.sky.getY())continue;
                    // Collision and line of sight use the very same clipped interval as the visible beam.
                    Location contact=p.getLocation();contact.setY(Math.clamp(p.getY()+.9,beam.bottom,gate.sky.getY()));
                    if(!host.clearTrial(gate.sky,contact))continue;
                    gate.victims.add(p.getUniqueId());strike(s,p,gate.crimson,now);
                    if(!states.containsKey(s.owner)||now<s.owner.revivalUntil)break;
                }
                if(!states.containsKey(s.owner)||now<s.owner.revivalUntil)break;
            }
        }
    }
    void strike(State s,Player p,boolean crimson,long now){
        boolean marked=now<s.bloodMarks.getOrDefault(p.getUniqueId(),0L)&&p.hasPotionEffect(PotionEffectType.WEAKNESS);
        double before=p.getHealth();
        // Exact same damage preserved
        if(!host.hitTrial(p,crimson?24:20,s.owner.mob))return;
        double lost=Math.max(0,before-p.getHealth());
        if(marked&&lost>0&&s.owner.mob.isValid()&&s.owner.health>0&&now>=s.owner.revivalUntil){
            double heal=Math.min(18,lost*.6);s.owner.health=Math.min(s.owner.maxHealth(),s.owner.health+heal);s.owner.mob.setHealth(s.owner.health);
            sigils.fastRay(key(s)+":drain:"+p.getUniqueId(),ray(true,s.owner.resurrected),p.getLocation().add(0,1,0),s.owner.mob.getLocation().add(0,5,0),.55);
            p.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR,p.getLocation().add(0,1,0),12,.3,.5,.3,.02);
            p.sendActionBar(net.kyori.adventure.text.Component.text("BLOOD DEBT - Malefeasance drains "+String.format(Locale.ROOT,"%.1f",heal)+" HP"));
        }
        if(!p.isDead()){
            apply(s,p,crimson?PotionEffectType.WEAKNESS:PotionEffectType.WITHER,crimson?0:1,now);
            if(crimson)s.bloodMarks.put(p.getUniqueId(),now+120);
        }
    }
    private void apply(State s,Player p,PotionEffectType type,int amplifier,long now){
        String id=p.getUniqueId()+":"+type.getKey();Debuff old=s.debuffs.get(id);PotionEffect current=p.getPotionEffect(type);
        if(current!=null&&current.getAmplifier()>amplifier)return;
        s.debuffs.put(id,new Debuff(p,type,amplifier,old==null?current:old.previous,old==null?now:old.began,now+120));
        p.addPotionEffect(new PotionEffect(type,120,amplifier,false,true,true));
    }
    private void restore(Debuff debuff,long now){
        PotionEffect current=debuff.player.getPotionEffect(debuff.type);
        if(current!=null&&current.getAmplifier()==debuff.amplifier&&current.getDuration()<=120){
            debuff.player.removePotionEffect(debuff.type);
            if(debuff.previous!=null){int remaining=debuff.previous.getDuration()-(int)(now-debuff.began);
                if(remaining>0)debuff.player.addPotionEffect(new PotionEffect(debuff.type,remaining,debuff.previous.getAmplifier(),debuff.previous.isAmbient(),debuff.previous.hasParticles(),debuff.previous.hasIcon()));}
        }
    }
    private void expire(State s,long now){
        for(var it=s.debuffs.entrySet().iterator();it.hasNext();){Debuff d=it.next().getValue();if(now>=d.until||!d.player.isOnline()||d.player.isDead()||!host.accepts(d.player)){restore(d,now);it.remove();}}
        s.bloodMarks.entrySet().removeIf(e->{Player p=Bukkit.getPlayer(e.getKey());boolean gone=now>=e.getValue()||p==null||!host.accepts(p)||!p.hasPotionEffect(PotionEffectType.WEAKNESS);if(gone)sigils.remove(key(s)+":drain:"+e.getKey());return gone;});
        if(now%6==0)for(UUID player:s.bloodMarks.keySet())sigils.remove(key(s)+":drain:"+player);
    }
    boolean revive(ArkhaiaMinions.Spirit owner,long now){
        if(owner.kind!=ArkhaiaWaves.Kind.MALEFEASANCE||owner.resurrected)return false;
        owner.resurrected=true;
        // 50% HP (300 / 600) upon Rebirth into Phase 2
        owner.health=owner.maxHealth()*0.50;
        owner.revivalUntil=now+50;
        owner.mob.setHealth(owner.health);
        owner.mob.setAI(false);
        owner.mob.getPathfinder().stopPathfinding();
        State s=state(owner,now);
        cancel(s,true);
        s.nextBatch=owner.revivalUntil+8;
        s.nextBlink=owner.revivalUntil+20;
        owner.bar.setProgress(0.50);
        owner.bar.setColor(org.bukkit.boss.BarColor.BLUE);
        owner.bar.setTitle("Malefeasance | ASTRAL ASCENSION | "+(int)owner.health+" / "+(int)owner.maxHealth()+" | PHASE 2");
        owner.mob.getWorld().playSound(owner.mob.getLocation(),Sound.ITEM_TOTEM_USE,1,.85f);
        owner.mob.getWorld().playSound(owner.mob.getLocation(),Sound.UI_TOAST_CHALLENGE_COMPLETE,1,1.4f);
        burst(owner.mob.getLocation(),true);
        // Transform model to Phase 2: Astral Arch-Sorceress!
        if(owner.visual!=null){
            owner.visual.switchModel("arkhaia_malefeasance_phase2");
        }
        return true;
    }
    private void rebirthFrame(State s,Player target,long now){
        host.renderTrial(s.owner,target,"rebirth",.8);
        Location at=s.owner.mob.getLocation();
        if(now%2==0){
            // Phase transition circle: base cyan, crown gold
            sigils.circle(key(s)+":revive:base",seal(false,true),ground(at),4.5,-now*.06,false);
            sigils.circle(key(s)+":revive:crown",seal(true,true),at.clone().add(0,11.5,0),5.0,now*.07,false);
            double age=50-(s.owner.revivalUntil-now);
            for(int i=0;i<16;i++){
                double a=now*.16+i*Math.PI/8;
                dust(at.clone().add(Math.cos(a)*3.2,(age*.22+i*.65)%11,Math.sin(a)*3.2),i%2==0?color(false,true):color(true,true),1.8f);
            }
            at.getWorld().spawnParticle(Particle.END_ROD,at.clone().add(0,age*.2,0),4,.6,.3,.6,.02);
        }
    }
    private void clearRevival(State s){sigils.remove(key(s)+":revive:base");sigils.remove(key(s)+":revive:crown");}
    private void removeGate(Gate g){for(String suffix:List.of(":sky",":ground",":beam:x",":beam:z"))sigils.remove(g.key+suffix);}
    private void cancel(State s,boolean statuses){
        s.gates.forEach(this::removeGate);s.gates.clear();clearBlink(s);clearRevival(s);
        if(statuses){for(Debuff d:s.debuffs.values())restore(d,clock);s.debuffs.clear();for(UUID p:s.bloodMarks.keySet())sigils.remove(key(s)+":drain:"+p);s.bloodMarks.clear();}
    }
    void departed(Player player){for(State s:states.values()){
        s.bloodMarks.remove(player.getUniqueId());sigils.remove(key(s)+":drain:"+player.getUniqueId());
        s.debuffs.entrySet().removeIf(e->{if(e.getValue().player!=player)return false;restore(e.getValue(),clock);return true;});
    }}
    void remove(ArkhaiaMinions.Spirit owner){State s=states.remove(owner);if(s!=null)cancel(s,true);}
    void clear(){for(State s:states.values())cancel(s,true);states.clear();}
}
