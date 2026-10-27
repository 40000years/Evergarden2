package com.example.voidscape.boss;

import com.example.voidscape.VoidscapePlugin;
import com.example.voidscape.world.BossStructureLayout;
import com.example.voidscape.compat.LevelledMobsCompat;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.boss.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.damage.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.event.world.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;
import java.io.*;
import java.util.*;
import static com.example.voidscape.boss.SeraphonRules.*;

/** Resident guardian of each Supreme Sanctuary; all damage belongs to a visible committed effect. */
public final class SeraphonBoss implements Listener,AutoCloseable {
    private static final class Bolt {
        final String key;Location at;Vector step;final boolean feather;final long expires;
        Bolt(String key,Location at,Vector step,boolean feather,long expires){this.key=key;this.at=at;this.step=step;this.feather=feather;this.expires=expires;}
    }
    private static final class Pillar {
        final String key;final Location at;final double radius,sky,damage;final long fire;final Set<UUID> hit=new HashSet<>();
        Pillar(String key,Location at,double radius,double sky,double damage,long fire){this.key=key;this.at=at;this.radius=radius;this.sky=sky;this.damage=damage;this.fire=fire;}
    }
    private record Aim(UUID player,Location at){}
    private static final class Wheel {
        final String key;double angle;final double radius,turn;int progress;long nextShot;
        Wheel(String key,double angle,double radius,double turn){this.key=key;this.angle=angle;this.radius=radius;this.turn=turn;}
    }
    private static final class Run {
        final String id;final Location center;final BossBar bar;final SeraphonEffects effects;
        final Set<Chunk> tickets=new HashSet<>();final Set<UUID> contributors=new HashSet<>(),roster=new HashSet<>();
        final Map<UUID,Long> hits=new HashMap<>(),beamHits=new HashMap<>();
        final List<Bolt> bolts=new ArrayList<>();final List<Pillar> pillars=new ArrayList<>();
        final List<Location> aims=new ArrayList<>();
        final List<Aim> solar=new ArrayList<>();final List<Location> mirrors=new ArrayList<>();final List<Long> mirrorReturns=new ArrayList<>();
        final List<Wheel> wheels=new ArrayList<>();
        final EnumMap<Skill,Long> ready=new EnumMap<>(Skill.class);final Random random=new Random();
        Slime anchor;SeraphonVisual visual;Location position,wingsFrom,wingsTo;
        double health=HEALTH,maxHealth=HEALTH,sky;long readyAt,emptySince=-1,start,next,introUntil,transitionUntil,stunUntil,choirDue,wingsDue;
        int phase=1,turn,petalOffset,serial;boolean fighting,dead,regrouped;Skill skill,last;
        Run(VoidscapePlugin plugin,BossStructureLayout.Site site){
            center=new Location(plugin.world(),site.x()+.5,site.kind().floorY+1,site.z()+.5);position=center.clone();
            id=center.getWorld().getUID()+"_"+site.x()+"_"+site.z();
            bar=Bukkit.createBossBar("SERAPHON · ผู้พิทักษ์บัญชาสวรรค์",BarColor.YELLOW,BarStyle.SEGMENTED_10);
            effects=new SeraphonEffects(plugin);
        }
    }
    private final VoidscapePlugin plugin;private final Map<String,Run> runs=new LinkedHashMap<>();
    private final Map<UUID,Integer> interactions=new HashMap<>();private final File file;private final YamlConfiguration ledger;
    private final org.bukkit.scheduler.BukkitTask driver;private long tick;private boolean closed,applying;
    public SeraphonBoss(VoidscapePlugin plugin){
        this.plugin=plugin;file=new File(plugin.getDataFolder(),"seraphon-bosses.yml");ledger=YamlConfiguration.loadConfiguration(file);
        upgrade(plugin);
        for(Entity e:plugin.world().getEntities())if(e.getPersistentDataContainer().has(plugin.key("seraphon_boss")))e.remove();
        Bukkit.getPluginManager().registerEvents(this,plugin);driver=Bukkit.getScheduler().runTaskTimer(plugin,this::tick,1,1);
        Bukkit.getScheduler().runTaskLater(plugin,()->{for(Chunk c:plugin.world().getLoadedChunks())discover(c);},80);
    }
    /** The published 10000 HP default becomes the 7000 solo health once; custom values stay. The marker key is never in the defaults. */
    private static void upgrade(VoidscapePlugin plugin){
        var config=plugin.getConfig();if(config.contains("seraphon.balance-version",true))return;
        if(!config.contains("seraphon.health",true)||Double.compare(config.getDouble("seraphon.health"),10000)==0)config.set("seraphon.health",HEALTH);
        config.set("seraphon.balance-version",2);plugin.saveConfig();
    }
    private boolean enabled(){return !closed&&plugin.getConfig().getBoolean("seraphon.enabled",true);}
    private boolean playable(Player p){return p.isOnline()&&!p.isDead()&&(p.getGameMode()==GameMode.SURVIVAL||p.getGameMode()==GameMode.ADVENTURE);}
    private boolean inside(Run r,Player p){return playable(p)&&p.getWorld()==r.center.getWorld()&&SeraphonRules.inside(p.getX()-r.center.getX(),p.getY(),p.getZ()-r.center.getZ(),r.center.getY());}
    private List<Player> players(Run r){return r.center.getWorld().getPlayers().stream().filter(p->inside(r,p)).toList();}
    public boolean owns(Entity e){return runs.values().stream().anyMatch(r->r.anchor==e||r.visual!=null&&r.visual.owns(e)||r.effects.sigils.owns(e));}
    private Run owner(Entity e){return runs.values().stream().filter(r->r.anchor==e||r.visual!=null&&r.visual.owns(e)||r.effects.sigils.owns(e)).findFirst().orElse(null);}
    private double soloHealth(){return Math.clamp(plugin.getConfig().getDouble("seraphon.health",HEALTH),100,1000000);}
    private double scaledHealth(int players){
        var config=plugin.getConfig();
        return SeraphonRules.health(soloHealth(),Math.clamp(config.getDouble("seraphon.health-per-extra-player",HEALTH_PER_EXTRA),0,1000000),
            config.getDouble("seraphon.health-cap",HEALTH_CAP),players);
    }
    private void discover(Chunk chunk){
        if(!enabled()||chunk.getWorld()!=plugin.world()||plugin.arkhaiaModels()==null||!plugin.arkhaiaModels().ready("seraphon"))return;
        var layout=plugin.bossStructures();if(layout==null||!layout.enabled(BossStructureLayout.Kind.SERAPHON))return;
        for(var site:layout.cell(Math.floorDiv(chunk.getX(),layout.spacingChunks()),Math.floorDiv(chunk.getZ(),layout.spacingChunks())))if(site.kind()==BossStructureLayout.Kind.SERAPHON){
            Location at=new Location(plugin.world(),site.x()+.5,site.kind().floorY+1,site.z()+.5);
            String id=at.getWorld().getUID()+"_"+site.x()+"_"+site.z();if(runs.containsKey(id)||!clear(at))continue;
            Run r=new Run(plugin,site);r.maxHealth=soloHealth();r.health=r.maxHealth;
            r.readyAt=ledger.getLong("cooldowns."+r.id,0);runs.put(r.id,r);
            if(at.getWorld().getGameTime()<r.readyAt)r.dead=true;else spawn(r);
        }
    }
    private boolean clear(Location at){
        World w=at.getWorld();
        for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++){
            int bx=at.getBlockX()+x,bz=at.getBlockZ()+z;if(!w.isChunkLoaded(bx>>4,bz>>4))return false;
            if(!w.getBlockAt(bx,at.getBlockY()-1,bz).getType().isSolid())return false;
            for(int y=0;y<11;y++)if(!w.getBlockAt(bx,at.getBlockY()+y,bz).isPassable())return false;
        }return true;
    }
    private void spawn(Run r){
        if(r.visual!=null){r.visual.close();r.visual=null;}if(r.anchor!=null)r.anchor.remove();
        r.anchor=r.center.getWorld().spawn(r.position,Slime.class,e->{
            e.setSize(8);e.setAI(false);e.setGravity(false);e.setSilent(true);e.setInvisible(true);e.setCollidable(false);
            e.setPersistent(false);e.setRemoveWhenFarAway(false);e.setNoDamageTicks(0);
            e.getAttribute(Attribute.MAX_HEALTH).setBaseValue(1024);e.setHealth(1024);
            e.getAttribute(Attribute.ATTACK_DAMAGE).setBaseValue(0);e.getAttribute(Attribute.KNOCKBACK_RESISTANCE).setBaseValue(1);
            e.addScoreboardTag("evergarden_unique");LevelledMobsCompat.tagMob(e,plugin,true);
            e.getPersistentDataContainer().set(plugin.key("world_boss_entity"),PersistentDataType.BYTE,(byte)1);
            e.getPersistentDataContainer().set(plugin.key("seraphon_boss"),PersistentDataType.BYTE,(byte)1);
        });
        r.visual=new SeraphonVisual(plugin,r.anchor);r.dead=false;
    }
    private void hold(Run r){for(int x=(r.center.getBlockX()-64)>>4;x<=(r.center.getBlockX()+64)>>4;x++)for(int z=(r.center.getBlockZ()-64)>>4;z<=(r.center.getBlockZ()+64)>>4;z++){
        Chunk chunk=r.center.getWorld().getChunkAt(x,z);chunk.addPluginChunkTicket(plugin);r.tickets.add(chunk);}}
    private void release(Run r){for(Chunk c:r.tickets)c.removePluginChunkTicket(plugin);r.tickets.clear();}
    private void announce(Run r,String text){for(Player p:players(r)){plugin.message(p,text);p.sendTitle("§6SERAPHON",text,10,50,15);}}
    private void wake(Run r){
        r.fighting=true;r.introUntil=tick+INTRO;r.next=r.introUntil+12;hold(r);
        r.mirrors.clear();r.mirrorReturns.clear();for(int i=0;i<3;i++)placeMirror(r);
        announce(r,"§eผู้ก้าวล่วงเขตศักดิ์สิทธิ์ จงรับคำพิพากษา");
        r.center.getWorld().playSound(r.center,Sound.BLOCK_BELL_RESONATE,3,.55f);
    }
    private void bar(Run r,List<Player> present){
        r.bar.getPlayers().stream().filter(p->!present.contains(p)).toList().forEach(r.bar::removePlayer);
        if(r.fighting)present.forEach(r.bar::addPlayer);
        r.bar.setProgress(Math.clamp(r.health/r.maxHealth,0,1));
        String state=tick<r.stunUntil?" · ร่วงพื้น ×2":r.skill==Skill.CHOIR?" · โล่ศักดิ์สิทธิ์ · ทำลายวงล้อ":r.skill==Skill.WINGS?" · หลบใต้ปีก!":"";
        r.bar.setTitle("SERAPHON · ผู้พิทักษ์บัญชาสวรรค์ · "+(int)Math.ceil(r.health)+" / "+(int)r.maxHealth+" · PHASE "+r.phase+state);
        r.bar.setColor(tick<r.stunUntil?BarColor.PINK:r.phase==3?BarColor.WHITE:r.phase==2?BarColor.BLUE:BarColor.YELLOW);
    }
    private void tick(){
        if(closed)return;tick++;
        if(tick%40==0)for(Player p:plugin.world().getPlayers())discover(p.getLocation().getChunk());
        for(Run r:new ArrayList<>(runs.values()))try{update(r);}catch(RuntimeException error){
            plugin.getLogger().log(java.util.logging.Level.SEVERE,"Seraphon encounter reset at "+r.id,error);remove(r);runs.remove(r.id);
        }
    }
    private double phaseStart(Run r){return r.phase==1?r.maxHealth:r.phase==2?r.maxHealth*.65:r.maxHealth*.30;}
    private void update(Run r){
        if(!enabled()){remove(r);runs.remove(r.id);return;}
        if(r.dead){if(r.center.getWorld().getGameTime()>=r.readyAt&&clear(r.center))reset(r);return;}
        if(r.anchor==null||!r.anchor.isValid()||r.visual==null||!r.visual.valid())spawn(r);
        List<Player> present=players(r);bar(r,present);r.effects.frame(r.center);
        if(r.fighting){
            for(Player p:present)if(r.roster.size()<16&&r.roster.add(p.getUniqueId())&&r.roster.size()>1){
                double next=scaledHealth(r.roster.size());r.health+=next-r.maxHealth;r.maxHealth=next;
            }
            if(present.isEmpty()){
                if(r.emptySince<0)r.emptySince=tick;
                long grace=20L*plugin.integer("seraphon.return-grace-seconds",180,30,3600);
                if(tick-r.emptySince>=grace){reset(r);return;}
                // A wiped party restarts the current phase, not the whole judgment.
                if(!r.regrouped&&tick-r.emptySince>=REGROUP)regroup(r);
            }else{r.emptySince=-1;r.regrouped=false;}
        }
        Player target=present.stream().min(Comparator.comparingDouble(p->p.getLocation().distanceSquared(r.position))).orElse(null);
        String clip="idle";double rate=1;boolean intense=false;
        if(r.fighting){
            projectiles(r);pillars(r);mirrors(r);
            if(tick<r.introUntil){clip="spawn";r.position.setY(r.center.getY()+Math.max(0,(r.introUntil-tick)*.12));
                if(tick%4==0){r.effects.sky(r.center,r.center.getY()+28,tick,1);r.effects.pulse(r.center,(INTRO-(r.introUntil-tick))*.6,tick,false);}}
            else if(tick<r.transitionUntil){clip="transition";
                if(tick%4==0){double age=TRANSITION-(r.transitionUntil-tick);r.effects.circle("attack:transition",r.center.clone().add(0,.2,0),4+age*.55,tick*.025,false);}}
            else if(tick<r.stunUntil){clip="stun";rate=.3;intense=true;
                r.position.setY(Math.max(r.center.getY(),r.position.getY()-.6));
                if(tick%6==0)r.center.getWorld().spawnParticle(Particle.END_ROD,r.position.clone().add(0,3,0),6,1.2,1.5,1.2,.02);}
            else if(r.skill!=null){
                int age=(int)(tick-r.start);Skill skill=r.skill;
                if(age>=skill.duration()){finish(r,skill);}
                else{clip=skill.clip(age);rate=skill.rate(age);intense=skill==Skill.VERDICT||skill==Skill.WINGS||age>=skill.windup&&age<skill.windup+skill.active;
                    cast(r,age);}
            }else{
                r.effects.finishCast();int phase=SeraphonRules.phase(r.health/r.maxHealth);
                if(phase>r.phase){r.phase=phase;r.transitionUntil=tick+TRANSITION;r.effects.clearAttack();r.bolts.clear();r.pillars.clear();r.wheels.clear();clip="transition";
                    if(phase==2)r.choirDue=r.transitionUntil+40;
                    if(phase==3)r.wingsDue=r.transitionUntil+300;
                    announce(r,phase==3?"§fประตูสวรรค์กำลังเปิด — คำพิพากษาสุดท้าย":"§bวงล้อโอฟานิมตื่นขึ้น — ยืนในเงาของมันเพื่อทำลาย");r.center.getWorld().playSound(r.center,Sound.ENTITY_WITHER_SPAWN,1,.8f);}
                else if(target!=null&&tick>=r.next){begin(r,select(r));clip=r.skill.clip(0);rate=r.skill.rate(0);}
                else if(target!=null){move(r,target);clip="walk";rate=.75;}
            }
            boolean high=r.skill==Skill.VERDICT||r.skill==Skill.CHOIR||r.skill==Skill.WINGS;
            if(!high&&tick>=r.introUntil&&tick>=r.stunUntil)r.position.setY(Math.max(r.center.getY(),r.position.getY()-.24));
        }
        boolean tracking=r.skill==null&&tick>=r.stunUntil||r.skill!=null&&tick-r.start<r.skill.windup&&r.skill!=Skill.WINGS;
        if(target!=null&&tracking)r.position.setYaw(ArkhaiaSteering.facingYaw(r.position.getYaw(),target.getX()-r.position.getX(),target.getZ()-r.position.getZ()));
        r.visual.frame(r.position,target,clip,rate,intense);r.effects.ambient(r.center,r.position,tick,r.phase,r.fighting);
    }
    private Skill select(Run r){
        if(r.phase==2&&tick>=r.choirDue){r.choirDue=tick+CHOIR_INTERVAL;return Skill.CHOIR;}
        if(r.phase==3&&tick>=r.wingsDue){r.wingsDue=tick+WINGS_INTERVAL;return Skill.WINGS;}
        return SeraphonRules.choose(r.phase,r.last,r.ready,tick,r.random);
    }
    private void finish(Run r,Skill skill){
        r.skill=null;r.next=tick+(r.phase==3?10:18);r.effects.finishCast();r.solar.clear();
        if(skill==Skill.CHOIR)endChoir(r,false);
        if(skill==Skill.WINGS)judgeWings(r);
    }
    private void ground(Run r,int ticks,String text){
        r.skill=null;r.stunUntil=tick+ticks;r.next=r.stunUntil+16;r.solar.clear();r.wheels.clear();r.effects.finishCast();
        announce(r,text);r.center.getWorld().playSound(r.position,Sound.BLOCK_GLASS_BREAK,2,.6f);
        r.center.getWorld().playSound(r.position,Sound.ENTITY_IRON_GOLEM_DAMAGE,1.4f,.5f);
    }
    private void regroup(Run r){
        r.regrouped=true;r.health=Math.max(r.health,phaseStart(r));r.skill=null;r.stunUntil=0;r.next=tick+40;
        r.bolts.clear();r.pillars.clear();r.wheels.clear();r.solar.clear();r.effects.clearAttack();
        if(r.phase==2)r.choirDue=tick+40;if(r.phase==3)r.wingsDue=tick+300;
        r.position=r.center.clone();
        plugin.getLogger().info("Seraphon regrouped at "+r.id+": party wiped; phase "+r.phase+" restarts at "+(int)r.health);
    }
    private void move(Run r,Player target){
        double angle=tick*.012;Location destination=r.center.clone().add(Math.cos(angle)*12,0,Math.sin(angle)*12);
        Vector delta=destination.toVector().subtract(r.position.toVector()).setY(0);if(delta.lengthSquared()<.01)return;delta.normalize().multiply(.18);
        Location next=r.position.clone().add(delta);
        if(r.position.getWorld().rayTraceBlocks(r.position.clone().add(0,2,0),delta.clone().normalize(),2.5,FluidCollisionMode.NEVER,true)==null)r.position=next;
    }
    private void begin(Run r,Skill skill){
        r.skill=skill;r.start=tick;r.aims.clear();r.beamHits.clear();r.solar.clear();r.petalOffset=r.turn++%4;r.last=skill;
        int cooldown=SeraphonRules.cooldown(skill,r.phase);if(cooldown>0)r.ready.put(skill,tick+cooldown);
        List<Player> players=players(r);
        for(Player p:players)r.aims.add(p.getEyeLocation().clone());
        r.sky=Math.max(r.center.getY()+42,players.stream().mapToDouble(Player::getY).max().orElse(r.center.getY())+18);
        switch(skill){
            case VERDICT -> {announce(r,"§6ประตูสวรรค์เปิด — เข้าสู่กลีบแสงสีฟ้า"+(r.phase==3?" · กลีบจะหมุนหลังแสงแรก":""));r.center.getWorld().playSound(r.center,Sound.BLOCK_BEACON_ACTIVATE,3,.55f);}
            case ORBIT -> announce(r,"§eวงโคจรแห่งมหาวิหาร — เคลื่อนตามช่องว่าง");
            case SOLAR -> {
                List<Player> order=new ArrayList<>(players);Collections.shuffle(order,r.random);
                int count=r.phase==3&&order.size()>=2?2:Math.min(1,order.size());
                for(int i=0;i<count;i++){Player p=order.get(i);r.solar.add(new Aim(p.getUniqueId(),p.getEyeLocation()));
                    p.sendTitle("§6SOLAR LANCE","§eหลบหลังกระจกสุริยะ ให้กระจกบังระหว่างคุณกับเธอ",4,50,10);}
                r.center.getWorld().playSound(r.position,Sound.BLOCK_BEACON_POWER_SELECT,2.5f,.6f);
            }
            case CHOIR -> {
                r.wheels.clear();int count=SeraphonRules.wheels(players.size());double offset=r.random.nextDouble(Math.PI*2);
                for(int i=0;i<count;i++)r.wheels.add(new Wheel("attack:wheel:"+(r.serial++),offset+i*Math.PI*2/count,14+r.random.nextDouble(10),(i%2==0?1:-1)*.006));
                announce(r,"§bคณะวงล้อโอฟานิม — ยืนในเงาวงล้อเพื่อทำลาย ("+count+" วง ภายใน 35 วินาที)");
                r.center.getWorld().playSound(r.center,Sound.BLOCK_BELL_RESONATE,3,1.2f);
            }
            case WINGS -> {
                Vector out=r.position.toVector().subtract(r.center.toVector()).setY(0);
                if(out.lengthSquared()<1)out=new Vector(r.random.nextDouble()-.5,0,r.random.nextDouble()-.5);out.normalize();
                // Bukkit vectors mutate in place: every offset starts from its own clone of the unit direction.
                Vector side=out.clone().crossProduct(new Vector(0,1,0)).multiply(r.random.nextBoolean()?WINGS_SIDE:-WINGS_SIDE);
                r.wingsFrom=r.center.clone().add(out.clone().multiply(WINGS_REACH));r.wingsTo=r.center.clone().subtract(out.clone().multiply(WINGS_REACH)).add(side);
                for(Player p:players)p.sendTitle("§fHEAVEN'S GATE","§bวิ่งตามไปยืนใต้ปีกของเธอ — นอกวงแสงฟ้าคือตาย",6,60,10);
                r.center.getWorld().playSound(r.center,Sound.BLOCK_END_PORTAL_SPAWN,3,.7f);
            }
            default -> {}
        }
    }
    private void cast(Run r,int age){
        Skill skill=r.skill;int active=age-skill.windup;
        if(skill==Skill.VERDICT){
            double wanted=r.center.getY()+(active<skill.active?12:0);r.position.setY(r.position.getY()+Math.clamp(wanted-r.position.getY(),-.35,.22));
            if(tick%3==0){r.effects.sky(r.center,r.sky,age,r.phase);safePetals(r);}
            if(active==0)verdict(r,tick+16);
            if(r.phase==3&&active==18){r.effects.removePrefix("attack:safe:");r.petalOffset=(r.petalOffset+1)%12;verdict(r,tick+32);announce(r,"§fกลีบแสงหมุน — ย้ายไปกลีบถัดไป!");}
        }else if(skill==Skill.ORBIT){
            if(age<skill.windup+skill.active)orbit(r,Math.max(0,active),active>=0);
        }else if(skill==Skill.SOLAR){solar(r,age);
        }else if(skill==Skill.CHOIR){choir(r,age);return;
        }else if(skill==Skill.WINGS){wings(r,age);return;
        }else if(age<skill.windup&&tick%3==0){
            double yaw=Math.toRadians(r.position.getYaw());Vector right=new Vector(Math.cos(yaw),0,Math.sin(yaw));
            for(int i=0;i<6;i++){double angle=i*Math.PI/3;Location gate=r.position.clone().add(right.clone().multiply(Math.cos(angle)*4)).add(0,5+Math.sin(angle)*2,0);
                r.effects.sigils.plane("attack:gate:"+i,"seraphon_seal",gate,right,new Vector(0,1,0),2.8,2.8,age*.035);}
        }
        if(active<0||active>=skill.active)return;
        if(skill==Skill.LANCES&&active%8==0)lances(r,active/8,false);
        if(skill==Skill.FEATHERS&&(active==0||r.phase>1&&active==15))lances(r,active/15,true);
        if(skill==Skill.SMITE&&active%16==0){
            int index=0;for(Location aim:r.aims){Location at=aim.clone();at.setY(r.center.getY()+.12);
                // Subsequent strikes follow the committed ring, never the player's later position.
                double a=(active/16)*Math.PI*2/3;at.add(Math.cos(a)*(active==0?0:5),0,Math.sin(a)*(active==0?0:5));
                addPillar(r,at,3.8,r.sky,SeraphonRules.damage(skill,r.phase),tick+24,"smite:"+(index++));}
        }
    }
    private Location orb(Run r){
        return BossHitbox.bonePoints("seraphon",r.visual.clip(),r.visual.time(),r.position,SCALE,Set.of("orb")).getOrDefault("orb",r.position.clone().add(0,6.4,0)).clone();
    }
    /** Tracking aim, a locked flash, then a beam that a sun mirror can turn back on her. */
    private void solar(Run r,int age){
        Location from=orb(r);
        for(int i=0;i<r.solar.size();i++){Aim aim=r.solar.get(i);Player p=Bukkit.getPlayer(aim.player());
            if(age<60&&p!=null&&inside(r,p))r.solar.set(i,aim=new Aim(aim.player(),p.getEyeLocation()));
            if(age<r.skill.windup&&tick%2==0){Location end=clipped(from,extend(from,aim.at(),1.6));
                r.effects.beam("attack:solar:aim:"+i,from,end,age<60?.12:.12+(age-60)*.02);}
        }
        if(age==60)r.center.getWorld().playSound(r.position,Sound.BLOCK_BEACON_DEACTIVATE,2.5f,1.6f);
        if(age!=r.skill.windup)return;
        boolean reflected=false;
        for(int i=0;i<r.solar.size();i++){Aim aim=r.solar.get(i);Location end=clipped(from,extend(from,aim.at(),1.6));
            Location mirror=null;
            for(Location m:r.mirrors)if(SeraphonRules.mirrored(from.getX(),from.getY(),from.getZ(),aim.at().getX(),aim.at().getY(),aim.at().getZ(),m.getX(),m.getY(),m.getZ())){mirror=m;break;}
            if(mirror!=null){
                r.effects.beam("attack:solar:fire:"+i,from,mirror,1.8);r.effects.beam("attack:solar:back:"+i,mirror,from,1.2);
                shatter(r,mirror);reflected=true;continue;
            }
            r.effects.beam("attack:solar:fire:"+i,from,end,2.4);
            Vector delta=end.toVector().subtract(from.toVector());double length=delta.length();if(length<.01)continue;
            for(Player p:players(r)){var box=p.getBoundingBox().expand(.8);
                if(box.contains(from.toVector())||box.rayTrace(from.toVector(),delta.clone().normalize(),length)!=null)hit(r,p,p.getAttribute(Attribute.MAX_HEALTH).getValue()*.6);}
        }
        r.center.getWorld().playSound(r.position,Sound.ENTITY_GENERIC_EXPLODE,2,1.4f);
        if(reflected)ground(r,GROUNDED,"§bลำแสงสะท้อนกลับ! เธอร่วงลงพื้น — โจมตี ×2");
    }
    private static Location extend(Location from,Location to,double factor){return from.clone().add(to.toVector().subtract(from.toVector()).multiply(factor));}
    private void placeMirror(Run r){
        List<Integer> free=new ArrayList<>();
        for(int i=0;i<6;i++){double a=i*Math.PI/3+Math.PI/6;Location at=r.center.clone().add(Math.cos(a)*MIRROR_RING,MIRROR_RADIUS+.3,Math.sin(a)*MIRROR_RING);
            if(r.mirrors.stream().noneMatch(m->m.distanceSquared(at)<4))free.add(i);}
        if(free.isEmpty())return;int slot=free.get(r.random.nextInt(free.size()));double a=slot*Math.PI/3+Math.PI/6;
        r.mirrors.add(r.center.clone().add(Math.cos(a)*MIRROR_RING,MIRROR_RADIUS+.3,Math.sin(a)*MIRROR_RING));
    }
    private void shatter(Run r,Location mirror){
        int index=r.mirrors.indexOf(mirror);if(index<0)return;r.mirrors.remove(index);r.mirrorReturns.add(tick+200);
        r.effects.removePrefix("fx:mirror:");
        mirror.getWorld().playSound(mirror,Sound.BLOCK_GLASS_BREAK,2,1.2f);mirror.getWorld().spawnParticle(Particle.END_ROD,mirror,40,1,1.4,1,.12);
    }
    private void mirrors(Run r){
        for(var it=r.mirrorReturns.iterator();it.hasNext();)if(tick>=it.next()){it.remove();placeMirror(r);}
        if(tick%4!=0)return;
        for(int i=0;i<r.mirrors.size();i++)r.effects.mirror("fx:mirror:"+i,r.mirrors.get(i),r.position,tick);
    }
    private Location wheelAt(Run r,Wheel w){return r.center.clone().add(Math.cos(w.angle)*w.radius,7.5,Math.sin(w.angle)*w.radius);}
    private boolean shaded(Run r,Wheel w,Player p){Location at=wheelAt(r,w);return Math.hypot(p.getX()-at.getX(),p.getZ()-at.getZ())<=WHEEL_SHADOW&&p.getY()<r.center.getY()+4;}
    private void choir(Run r,int age){
        double wanted=r.center.getY()+14;r.position.setY(r.position.getY()+Math.clamp(wanted-r.position.getY(),-.35,.3));
        Vector home=r.center.toVector().subtract(r.position.toVector()).setY(0);if(home.length()>.3)r.position.add(home.normalize().multiply(Math.min(.3,home.length())));
        if(age<r.skill.windup)return;
        List<Player> players=players(r);
        for(var it=r.wheels.iterator();it.hasNext();){Wheel w=it.next();w.angle+=w.turn;
            boolean occupied=false;
            for(Player p:players)if(shaded(r,w,p)){occupied=true;
                if(tick>=w.nextShot){hit(r,p,12);r.effects.beam(w.key+":shot",wheelAt(r,w),p.getEyeLocation(),.3);}}
            if(occupied&&tick>=w.nextShot)w.nextShot=tick+20;
            if(occupied)w.progress++;
            if(w.progress>=WHEEL_PROGRESS){Location at=wheelAt(r,w);at.getWorld().playSound(at,Sound.BLOCK_GLASS_BREAK,2.5f,.8f);
                at.getWorld().spawnParticle(Particle.END_ROD,at,50,1.5,1.5,1.5,.1);r.effects.removePrefix(w.key);it.remove();continue;}
            if(tick%2==0){r.effects.wheel(w.key,wheelAt(r,w),tick);Location floor=wheelAt(r,w);floor.setY(r.center.getY()+.15);
                r.effects.shadow(w.key+":shadow",floor,WHEEL_SHADOW,w.progress/(double)WHEEL_PROGRESS,tick);}
        }
        if((age-r.skill.windup)%60==30)lances(r,0,false);
        if(r.wheels.isEmpty())endChoir(r,true);
    }
    private void endChoir(Run r,boolean broken){
        if(broken){ground(r,GROUNDED_LONG,"§bวงล้อทั้งหมดแตก! เธอร่วงจากฟ้า — โจมตี ×2");return;}
        // Radiant burst: only the shadows of surviving wheels shelter.
        for(Player p:players(r))if(r.wheels.stream().noneMatch(w->shaded(r,w,p)))hit(r,p,p.getAttribute(Attribute.MAX_HEALTH).getValue()*.4);
        r.effects.pulse(r.center,40,tick,false);r.center.getWorld().playSound(r.center,Sound.ENTITY_GENERIC_EXPLODE,3,.6f);
        announce(r,"§eแสงศักดิ์สิทธิ์ระเบิด — วงล้อที่เหลือหายไป");r.wheels.clear();r.effects.removePrefix("attack:wheel");
    }
    private void wings(Run r,int age){
        Skill skill=r.skill;
        if(age<skill.windup){
            double wanted=r.center.getY()+9;r.position.setY(r.position.getY()+Math.clamp(wanted-r.position.getY(),-.35,.3));
            Vector toward=r.wingsFrom.toVector().subtract(r.position.toVector()).setY(0);if(toward.length()>.2)r.position.add(toward.normalize().multiply(Math.min(.6,toward.length())));
        }else{
            double progress=Math.clamp((age-skill.windup)/(double)skill.active,0,1);
            Location at=r.wingsFrom.clone().add(r.wingsTo.toVector().subtract(r.wingsFrom.toVector()).multiply(progress));
            r.position.setX(at.getX());r.position.setZ(at.getZ());
            int left=skill.windup+skill.active-age;
            if(left%20==0)for(Player p:players(r))p.sendActionBar(net.kyori.adventure.text.Component.text("ประตูสวรรค์ "+left/20+" · "+(SeraphonRules.sheltered(p.getX(),p.getZ(),r.position.getX(),r.position.getZ())?"อยู่ใต้ปีก":"ออกนอกร่มปีก!"),
                SeraphonRules.sheltered(p.getX(),p.getZ(),r.position.getX(),r.position.getZ())?net.kyori.adventure.text.format.NamedTextColor.AQUA:net.kyori.adventure.text.format.NamedTextColor.RED));
            if((age-skill.windup)%40==20)lances(r,0,true);
        }
        if(tick%3==0){r.effects.sky(r.center,r.sky,age,3);Location shelter=r.position.clone();shelter.setY(r.center.getY()+.15);r.effects.circle("attack:shelter",shelter,WINGS_SAFE,-tick*.02,true);}
    }
    private void judgeWings(Run r){
        for(Player p:players(r))if(!SeraphonRules.sheltered(p.getX(),p.getZ(),r.position.getX(),r.position.getZ())){
            // Like the Judge's Final Judgment: direct zero health, no cancellable damage event.
            p.setHealth(0);
        }
        r.effects.pulse(r.center,RADIUS,tick,true);r.center.getWorld().playSound(r.center,Sound.ENTITY_LIGHTNING_BOLT_THUNDER,3,.5f);
        ground(r,GROUNDED_LONG,"§fประตูสวรรค์ปิด — เธอหมดแรงร่วงพื้น โจมตี ×2");
    }
    private void safePetals(Run r){
        for(int i=0;i<12;i++)if(SeraphonRules.safePetal(i,r.petalOffset)){
            double a=i*Math.PI/6;for(int j=1;j<=3;j++)r.effects.circle("attack:safe:"+i+":"+j,r.center.clone().add(Math.cos(a)*j*15,.18,Math.sin(a)*j*15),j*2.8,-tick*.01,true);
        }
    }
    private void verdict(Run r,long fire){
        for(int i=0;i<12;i++)if(!SeraphonRules.safePetal(i,r.petalOffset))for(int ring=1;ring<=3;ring++){
            double a=i*Math.PI/6;Location at=r.center.clone().add(Math.cos(a)*ring*15,.12,Math.sin(a)*ring*15);
            addPillar(r,at,ring*3.3,r.sky,SeraphonRules.damage(Skill.VERDICT,r.phase),fire,"verdict");
        }
        r.center.getWorld().playSound(r.center,Sound.ENTITY_LIGHTNING_BOLT_THUNDER,2,.6f);
    }
    private void lances(Run r,int volley,boolean feather){
        List<Location> aims=r.aims.isEmpty()?List.of(r.center.clone().add(0,1,20)):r.aims;
        if(r.skill==Skill.CHOIR||r.skill==Skill.WINGS)aims=players(r).stream().map(Player::getEyeLocation).toList();
        int count=feather?11+r.phase*2:Math.min(6,2+r.phase);
        var emitters=BossHitbox.bonePoints("seraphon",r.visual.clip(),r.visual.time(),r.position,SCALE,
            Set.of("orb","fx_palm","fx_upper_r","fx_upper_l","fx_middle_r","fx_middle_l","fx_lower_r","fx_lower_l"));
        // Bound visuals and projectiles even with a raid-sized player list.
        int targets=0;for(Location aim:aims){if(targets++>=8)break;
            for(int i=0;i<count;i++){
                if(r.bolts.size()>=128)break;
                String emitter=feather?List.of("fx_upper_r","fx_upper_l","fx_middle_r","fx_middle_l","fx_lower_r","fx_lower_l").get(i%6):"orb";
                Location from=emitters.getOrDefault(emitter,r.position.clone().add(0,6.4,0)).clone();
                Vector direction=aim.toVector().subtract(from.toVector());if(direction.lengthSquared()<.001)continue;
                direction.normalize().rotateAroundY(Math.toRadians((i-(count-1)*.5)*(feather?8:3)));
                r.bolts.add(new Bolt("attack:bolt:"+(r.serial++),from,direction.multiply(feather?.78:1.05),feather,tick+90));
            }
        }
        r.center.getWorld().playSound(r.position,feather?Sound.ENTITY_PHANTOM_FLAP:Sound.ENTITY_BREEZE_SHOOT,2,feather?.65f:1.4f);
    }
    private Location clipped(Location from,Location to){
        Vector delta=to.toVector().subtract(from.toVector());double length=delta.length();if(length<.001)return to;
        var wall=from.getWorld().rayTraceBlocks(from,delta.clone().normalize(),length,FluidCollisionMode.NEVER,true);
        return wall==null?to:wall.getHitPosition().toLocation(from.getWorld());
    }
    private void projectiles(Run r){
        for(var it=r.bolts.iterator();it.hasNext();){Bolt b=it.next();Location end=b.at.clone().add(b.step);Location contact=clipped(b.at,end);
            Vector delta=contact.toVector().subtract(b.at.toVector());double length=delta.length();Player victim=null;double nearest=length;
            if(length>.001)for(Player p:players(r)){
                var box=p.getBoundingBox().expand(b.feather?.22:.18);double distance=Double.POSITIVE_INFINITY;
                if(box.contains(b.at.toVector()))distance=0;else{var hit=box.rayTrace(b.at.toVector(),delta.clone().normalize(),length);if(hit!=null)distance=hit.getHitPosition().distance(b.at.toVector());}
                if(distance<nearest){nearest=distance;victim=p;}
            }
            if(victim!=null){hit(r,victim,SeraphonRules.damage(b.feather?Skill.FEATHERS:Skill.LANCES,r.phase));r.effects.remove(b.key);it.remove();continue;}
            boolean wall=contact.distanceSquared(end)>.000001;
            if(wall||tick>=b.expires||Math.hypot(end.getX()-r.center.getX(),end.getZ()-r.center.getZ())>RADIUS+3){
                if(b.feather&&wall&&Math.abs(contact.getY()-r.center.getY())<3){Location ground=contact.clone();ground.setY(r.center.getY()+.12);addPillar(r,ground,2.8,ground.getY()+3,18,tick+24,"feather");}
                r.effects.remove(b.key);it.remove();continue;
            }
            Location tail=contact.clone().subtract(b.step.clone().normalize().multiply(b.feather?1.7:2.3));
            if(b.feather)r.effects.feather(b.key,tail,contact);else r.effects.beam(b.key,tail,contact,.36);
            if(tick%3==0)contact.getWorld().spawnParticle(Particle.END_ROD,contact,1,.03,.03,.03,.005);
            b.at=contact;if(b.feather)b.step.setY(b.step.getY()-.024);
        }
    }
    private void addPillar(Run r,Location at,double radius,double sky,double damage,long fire,String label){
        if(r.pillars.size()>=96)return;r.pillars.add(new Pillar("attack:pillar:"+label+":"+(r.serial++),at,radius,sky,damage,fire));
    }
    private void pillars(Run r){
        for(var it=r.pillars.iterator();it.hasNext();){Pillar p=it.next();long age=tick-p.fire;
            if(age>8){r.effects.remove(p.key+":ground");r.effects.remove(p.key+":sky");r.effects.remove(p.key+":beam");it.remove();continue;}
            if(tick%3==0||age==0){
                r.effects.circle(p.key+":ground",p.at,p.radius,tick*.018,false);Location sky=p.at.clone();sky.setY(p.sky);
                r.effects.circle(p.key+":sky",sky,p.radius*.8,-tick*.025,false);
                // Show the roof-clipped beam, matching precisely where the column can strike.
                Location bottom=clipped(sky,p.at);r.effects.beam(p.key+":beam",sky,bottom,age<0?.14:p.radius*1.6);
            }
            if(age<0)continue;
            for(Player player:players(r))if(!p.hit.contains(player.getUniqueId())&&columnContact(p,player)){
                p.hit.add(player.getUniqueId());hit(r,player,p.damage);
            }
            if(age==0){r.effects.pulse(p.at,p.radius,tick,false);p.at.getWorld().playSound(p.at,Sound.ENTITY_GENERIC_EXPLODE,.8f,1.5f);}
        }
    }
    private boolean columnContact(Pillar p,Player player){
        var body=player.getBoundingBox();double dx=p.at.getX()-Math.clamp(p.at.getX(),body.getMinX(),body.getMaxX()),dz=p.at.getZ()-Math.clamp(p.at.getZ(),body.getMinZ(),body.getMaxZ());
        if(dx*dx+dz*dz>p.radius*p.radius||body.getMaxY()<p.at.getY()-.2||body.getMinY()>p.sky)return false;
        Location top=player.getEyeLocation();top.setY(p.sky);Location bottom=clipped(top,player.getEyeLocation());
        return bottom.distanceSquared(player.getEyeLocation())<.001;
    }
    private void orbit(Run r,int age,boolean active){
        int beams=r.phase==1?2:3;
        for(int i=0;i<beams;i++){
            double angle=r.petalOffset*Math.PI/6+age*(r.phase==3?.017:.012)+i*Math.PI*2/beams;
            Location from=r.center.clone().add(0,1.2,0);Location to=clipped(from,from.clone().add(Math.cos(angle)*RADIUS,0,Math.sin(angle)*RADIUS));
            r.effects.beam("attack:orbit:"+i,from,to,active?1.4:.18);
            if(!active)continue;Vector delta=to.toVector().subtract(from.toVector());double length=delta.length();if(length<.01)continue;
            for(Player p:players(r))if(tick>=r.beamHits.getOrDefault(p.getUniqueId(),0L)){
                var body=p.getBoundingBox().expand(.7);if(body.contains(from.toVector())||body.rayTrace(from.toVector(),delta.clone().normalize(),length)!=null){
                    r.beamHits.put(p.getUniqueId(),tick+16);hit(r,p,SeraphonRules.damage(Skill.ORBIT,r.phase));
                    // Feather gale: the beam throws its victim outward.
                    Vector push=p.getLocation().toVector().subtract(r.center.toVector()).setY(0);if(push.lengthSquared()>.01)p.setVelocity(push.normalize().multiply(.7).setY(.35));}
            }
        }
    }
    /** Armor-piercing light: Protection, Resistance, absorption, cancellation and Totems still apply. */
    private boolean hit(Run r,Player p,double amount){
        if(!inside(r,p)||!r.fighting||r.dead||r.anchor==null||!r.anchor.isValid())return false;
        double percent=plugin.getConfig().getDouble("seraphon.damage-percent",100);amount*=Double.isFinite(percent)?Math.clamp(percent,0,1000)/100:1;
        applying=true;try{return BossDamage.hit(p,amount,DamageSource.builder(DamageType.INDIRECT_MAGIC).withCausingEntity(r.anchor).withDirectEntity(r.anchor).build(),true);}finally{applying=false;}
    }
    private Player attacker(EntityDamageEvent e){
        if(e.getDamageSource().getCausingEntity() instanceof Player p)return p;
        if(e instanceof EntityDamageByEntityEvent b){if(b.getDamager() instanceof Player p)return p;if(b.getDamager() instanceof Projectile s&&s.getShooter() instanceof Player p)return p;}return null;
    }
    private boolean magicDamage(){
        var addon=Bukkit.getPluginManager().getPlugin("advance-magic");
        return addon instanceof com.example.advancemagic.AdvanceMagicPlugin m&&m.isEnabled()&&m.context()!=null&&m.context().isMagicDamage();
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void damage(EntityDamageEvent e){
        Run r=owner(e.getEntity());if(r==null)return;double amount=e.getFinalDamage();e.setCancelled(true);
        Player p=attacker(e);if(e.getEntity()!=r.anchor||p==null||!inside(r,p)||!Double.isFinite(amount)||amount<=0||r.dead)return;
        if(r.fighting&&(tick<r.introUntil||tick<r.transitionUntil))return;
        if(e instanceof EntityDamageByEntityEvent b&&b.getDamager() instanceof Projectile shot){
            if(!r.id.equals(shot.getPersistentDataContainer().get(plugin.key("seraphon_shot"),PersistentDataType.STRING)))return;
        }
        DamageType type=e.getDamageSource().getDamageType();
        boolean physical=type==DamageType.PLAYER_ATTACK||type==DamageType.MOB_ATTACK||type.getKey().getKey().equals("mace_smash");
        if(e instanceof EntityDamageByEntityEvent b&&b.getDamager()==p&&physical&&!magicDamage()
                &&(e.getCause()==EntityDamageEvent.DamageCause.ENTITY_ATTACK||e.getCause()==EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK)&&!BossHitbox.confirmed(p,r.anchor)
                &&!BossHitbox.melee(p,"seraphon",r.visual.clip(),r.visual.time(),r.position,SCALE,e.getCause()==EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK))return;
        if(tick<r.hits.getOrDefault(p.getUniqueId(),0L))return;
        if(!r.fighting){if(p.getY()>r.center.getY()+5)return;wake(r);}
        r.hits.put(p.getUniqueId(),tick+10);double before=r.health;
        double multiplier=tick<r.stunUntil?2:r.skill==Skill.CHOIR||r.skill==Skill.WINGS?.25
            :r.skill!=null&&tick-r.start>=r.skill.windup+r.skill.active?1.35:1;
        r.health=Math.max(0,r.health-amount*multiplier);r.contributors.add(p.getUniqueId());
        ArkhaiaHitFeedback.confirm(p,r.anchor,e,before-r.health,tick<r.stunUntil);bar(r,players(r));
        if(r.health<=0)defeat(r);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void outgoing(EntityDamageByEntityEvent e){if(!applying&&owns(e.getDamager()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void launch(ProjectileLaunchEvent e){if(e.getEntity().getShooter() instanceof Player p)for(Run r:runs.values())if(inside(r,p)){
        e.getEntity().getPersistentDataContainer().set(plugin.key("seraphon_shot"),PersistentDataType.STRING,r.id);break;}}
    @EventHandler(priority=EventPriority.MONITOR)
    public void used(PlayerInteractEvent e){if(e.getAction()!=org.bukkit.event.block.Action.LEFT_CLICK_AIR)interactions.put(e.getPlayer().getUniqueId(),Bukkit.getCurrentTick());}
    @EventHandler(priority=EventPriority.MONITOR)
    public void usedEntity(PlayerInteractEntityEvent e){interactions.put(e.getPlayer().getUniqueId(),Bukkit.getCurrentTick());}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void swing(PlayerAnimationEvent e){
        Player p=e.getPlayer();if(e.getAnimationType()!=PlayerAnimationType.ARM_SWING||!playable(p))return;
        int serverTick=Bukkit.getCurrentTick();Location eye=p.getEyeLocation();Run selected=null;double nearest=Double.POSITIVE_INFINITY;
        for(Run r:runs.values())if(!r.dead&&inside(r,p)&&tick>=r.hits.getOrDefault(p.getUniqueId(),0L)&&r.visual!=null){
            double distance=BossHitbox.ray(p,"seraphon",r.visual.clip(),r.visual.time(),r.position,SCALE,BossHitbox.reach(p));
            if(distance<nearest){nearest=distance;selected=r;}}
        if(selected==null)return;
        if(nearest>0&&p.getWorld().rayTraceEntities(eye,eye.getDirection(),nearest,x->x!=p&&x instanceof LivingEntity&&!owns(x))!=null)return;
        Run r=selected;Slime receiver=r.anchor;double distance=nearest;
        Bukkit.getScheduler().runTask(plugin,()->{if(!closed&&runs.get(r.id)==r&&inside(r,p)&&r.anchor==receiver
            &&interactions.getOrDefault(p.getUniqueId(),Integer.MIN_VALUE)<serverTick)BossHitbox.attack(p,receiver,eye,distance);});
    }
    private void defeat(Run r){
        if(r.dead)return;
        r.dead=true;r.fighting=false;r.readyAt=r.center.getWorld().getGameTime()+RESPAWN;
        ledger.set("cooldowns."+r.id,r.readyAt);save();r.effects.pulse(r.center,22,tick,true);
        BossArmorCores.drop(plugin,BossArmorCores.Core.SERAPH,r.center);
        announce(r,"§bคำพิพากษาสิ้นสุด — ผู้พิทักษ์ยอมรับในพลังของเจ้า");
        for(UUID uuid:r.contributors){Player p=Bukkit.getPlayer(uuid);if(p==null||!p.isOnline()||p.getWorld()!=r.center.getWorld()||p.getLocation().distanceSquared(r.center)>100*100)continue;
            var magic=Bukkit.getPluginManager().getPlugin("advance-magic");if(magic instanceof com.example.advancemagic.AdvanceMagicPlugin m&&m.isEnabled())m.skillTree().grantPoints(p,5,"Seraphon");
            if(plugin.seraphTrader()!=null)plugin.seraphTrader().feathers().grant(p,25,"พิชิต Seraphon",false);p.giveExp(2000);
        }
        destroy(r);release(r);
    }
    private void reset(Run r){
        destroy(r);release(r);r.maxHealth=soloHealth();r.health=r.maxHealth;r.phase=1;r.fighting=false;r.dead=false;r.skill=null;r.last=null;r.turn=0;r.introUntil=0;r.transitionUntil=0;r.stunUntil=0;r.next=0;r.emptySince=-1;
        r.regrouped=false;r.hits.clear();r.beamHits.clear();r.contributors.clear();r.roster.clear();r.ready.clear();r.position=r.center.clone();spawn(r);
    }
    private void destroy(Run r){
        r.bolts.clear();r.pillars.clear();r.wheels.clear();r.solar.clear();r.mirrors.clear();r.mirrorReturns.clear();r.effects.close();r.bar.removeAll();
        if(r.visual!=null){r.visual.close();r.visual=null;}if(r.anchor!=null){r.anchor.remove();r.anchor=null;}
    }
    private void remove(Run r){destroy(r);release(r);}
    private void save(){try{ledger.save(file);}catch(IOException e){plugin.getLogger().log(java.util.logging.Level.SEVERE,"Cannot save Seraphon cooldown",e);}}
    @EventHandler public void load(ChunkLoadEvent e){Bukkit.getScheduler().runTaskLater(plugin,()->discover(e.getChunk()),2);}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void unload(ChunkUnloadEvent e){for(Run r:new ArrayList<>(runs.values()))if(!r.fighting&&e.getWorld()==r.center.getWorld()&&e.getChunk().getX()==(r.center.getBlockX()>>4)&&e.getChunk().getZ()==(r.center.getBlockZ()>>4)){remove(r);runs.remove(r.id);}}
    @EventHandler public void split(SlimeSplitEvent e){if(owns(e.getEntity()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void heal(EntityRegainHealthEvent e){if(owns(e.getEntity()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void target(EntityTargetLivingEntityEvent e){if(owns(e.getEntity()))e.setCancelled(true);}
    @EventHandler public void quit(PlayerQuitEvent e){interactions.remove(e.getPlayer().getUniqueId());}
    @Override public void close(){if(closed)return;closed=true;driver.cancel();for(Run r:runs.values())remove(r);runs.clear();interactions.clear();save();}
}
