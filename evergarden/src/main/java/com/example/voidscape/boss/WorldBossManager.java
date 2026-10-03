package com.example.voidscape.boss;

import com.example.voidscape.VoidscapePlugin;
import com.example.voidscape.compat.LevelledMobsCompat;
import com.example.voidscape.item.RelicService;
import com.example.voidscape.world.WorldBossTemple;
import com.example.voidscape.world.WorldBossTempleLayout.Site;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.boss.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.damage.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.*;
import org.bukkit.util.Vector;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Owns the multipart judge, combat, chunk tickets, cooldown ledger and client-specific sculpture. */
public final class WorldBossManager implements Listener,AutoCloseable {
    private static final double SKY_CAST_HEIGHT=60;
    private enum Attack { SLAM, CROSS, RING, SKY_BEAMS, LANCES, JUDGMENT }
    private enum StopReason {
        EMPTY("ไม่มีผู้ร่วมสู้อยู่ในลานครบเวลารอ"), TIMEOUT("ครบเวลาสู้ที่กำหนด"),
        DISABLED("ระบบบอสถูกปิดในการตั้งค่า"), ADMIN("แอดมินยุติไฟต์"),
        SHUTDOWN("เซิร์ฟเวอร์หรือปลั๊กอินกำลังปิด"), ERROR("ระบบไฟต์เกิดข้อผิดพลาด");
        final String text;StopReason(String text){this.text=text;}
    }
    private record Target(Run run,JudgmentFight.Part part) {}
    private static final class Run {
        final Site site;final Location center;final JudgmentFight fight;
        final Map<JudgmentFight.Part,Slime> targets=new EnumMap<>(JudgmentFight.Part.class);
        final Set<Long> tickets=new HashSet<>();final Map<UUID,Long> lastHit=new HashMap<>();
        final JudgeDamageBudget damageBudget=new JudgeDamageBudget();
        final Map<UUID,Mob> summons=new LinkedHashMap<>();final Set<UUID> summonedFor=new HashSet<>();
        final BossBar bar;JudgmentBody body;
        final List<Location> marks=new ArrayList<>();
        Attack attack,lastAttack;long resolveAt,nextAttack,nextJudgment,lastPresent,started,nextEmptyWarning;
        double safeX,safeZ,ringRadius,crossAngle;int phase=1,pulsesLeft,warningTicks;
        JudgmentPattern.Tuning pattern=JudgmentPattern.forPhase(1);
        Run(Site site,Location center,JudgmentFight fight){
            this.site=site;this.center=center;this.fight=fight;
            bar=Bukkit.createBossBar("ผู้พิพากษาไร้ร่าง",BarColor.PURPLE,BarStyle.SEGMENTED_10);
        }
    }
    private final VoidscapePlugin plugin;
    private final Map<String,Run> active=new LinkedHashMap<>();
    private final Map<UUID,Target> owners=new HashMap<>();
    private final Map<UUID,Run> summonOwners=new HashMap<>();
    private final File file;private final YamlConfiguration ledger=new YamlConfiguration();
    private final Random random=new Random();
    private boolean healthy=true,closed;
    private long tick;
    public WorldBossManager(VoidscapePlugin plugin)throws IOException{
        this.plugin=plugin;file=new File(plugin.getDataFolder(),"world-bosses.yml");
        if(file.exists())try{ledger.load(file);}catch(Exception e){throw new IOException("Cannot read world-boss cooldown ledger",e);}
        for(Entity e:plugin.world().getEntities())if(marked(e))e.remove();
    }
    private boolean marked(Entity e){return e.getPersistentDataContainer().has(plugin.key("world_boss_entity"),PersistentDataType.BYTE);}
    private static String id(Site site){return site.x()+"_"+site.z();}
    private String path(Site site){return "sites."+id(site)+".next-open";}
    private boolean enabled(){return !closed&&plugin.getConfig().getBoolean("world-boss.enabled",true)
        &&plugin.getConfig().getBoolean("structures.world-boss-temple.enabled",true);}
    private double number(String key,double value,double min,double max){
        double n=plugin.getConfig().getDouble("world-boss."+key,value);return Double.isFinite(n)?Math.clamp(n,min,max):value;
    }
    private int seconds(String key,int value,int min,int max){return plugin.integer("world-boss."+key,value,min,max)*20;}
    private int emptyTicks(){return seconds("empty-reset-seconds",JudgeBalance.EMPTY_RESET_SECONDS,10,600);}
    private int fightLimit(){return seconds("fight-timeout-seconds",0,0,86400);}
    private String timeLimit(Run r){int limit=fightLimit();return limit==0?"":" · ไฟต์เหลือ "+Math.max(0,(r.started+limit-tick+19)/20)+" วิ";}
    private boolean playable(Player p){return p!=null&&p.isOnline()&&!p.isDead()
        &&(p.getGameMode()==GameMode.SURVIVAL||p.getGameMode()==GameMode.ADVENTURE);}
    private boolean inside(Run r,Player p){return playable(p)&&p.getWorld()==plugin.world()
        &&p.getY()>=98&&Math.hypot(p.getX()-r.center.getX(),p.getZ()-r.center.getZ())<=79;}
    private List<Player> players(Run r){
        var result=new ArrayList<Player>();
        for(UUID uuid:r.fight.roster()){Player p=Bukkit.getPlayer(uuid);if(inside(r,p))result.add(p);}
        return result;
    }
    public boolean inCombat(Player p){for(Run r:active.values())if(r.fight.roster().contains(p.getUniqueId())&&inside(r,p))return true;return false;}
    public int activeCount(){return active.size();}
    public boolean owns(Entity e){return owners.containsKey(e.getUniqueId())||summonOwners.containsKey(e.getUniqueId());}
    public String status(Player p){
        if(p.getWorld()!=plugin.world())return "เดินทางไปยังวิหารใหญ่ใน Evergarden ก่อน";
        Site site=plugin.bossTemples().at(p.getLocation().getBlockX(),p.getLocation().getBlockZ(),0);
        if(site==null)return "คุณยังไม่ได้อยู่ในวิหารใหญ่ · /evergarden tp boss-temple";
        Run r=active.get(id(site));
        if(r!=null)return "ผู้พิพากษาไร้ร่าง · เฟส "+r.fight.phase()+" · เลือด "+Math.round(r.fight.core()/r.fight.coreMax()*100)
            +"% · ผู้ร่วมสู้ "+r.fight.roster().size()+" · สเกล ×"+String.format(Locale.ROOT,"%.2f",r.fight.scale())+timeLimit(r)
            +(players(r).isEmpty()?" · รอผู้เล่นกลับอีก "+Math.max(0,(r.lastPresent+emptyTicks()-tick+19)/20)+" วิ":"");
        long remaining=ledger.getLong(path(site),0)-System.currentTimeMillis();
        if(remaining<=0)return "พร้อมอัญเชิญ · ใช้มือเปล่าคลิกขวาบล็อก Amethyst ตรงกลางลาน";
        String stopped=ledger.getString("sites."+id(site)+".last-stop","");
        String reason=Arrays.stream(StopReason.values()).filter(value->value.name().equals(stopped)).map(value->" · "+value.text).findFirst().orElse("");
        return "วิหารกำลังฟื้นตัว · อีก "+(remaining<60000?Math.max(1,(remaining+999)/1000)+" วินาที":(remaining+59999)/60000+" นาที")+reason;
    }
    @EventHandler(priority=EventPriority.LOWEST)
    public void altar(PlayerInteractEvent e){
        Player p=e.getPlayer();Block b=e.getClickedBlock();
        if(!enabled()||p.getWorld()!=plugin.world()||b==null||e.getAction()!=Action.RIGHT_CLICK_BLOCK
            ||e.getHand()!=EquipmentSlot.HAND||!p.getInventory().getItemInMainHand().getType().isAir())return;
        Site site=plugin.bossTemples().at(b.getX(),b.getZ(),0);
        if(site==null||b.getY()!=WorldBossTemple.FLOOR_Y||b.getType()!=Material.AMETHYST_BLOCK
            ||Math.abs(b.getX()-site.x())>3||Math.abs(b.getZ()-site.z())>3)return;
        e.setCancelled(true);start(p,false);
    }
    /** Admin force bypasses only cooldown, never unsafe geometry, player modes or capacity. */
    public boolean start(Player p,boolean force){
        if(!enabled()||!healthy){plugin.message(p,"ระบบ World Boss ยังไม่พร้อมใช้งาน");return false;}
        if(!playable(p)||p.getWorld()!=plugin.world()){plugin.message(p,"เริ่มบอสใน Survival หรือ Adventure ภายในลานวิหารใหญ่");return false;}
        Site site=plugin.bossTemples().at(p.getLocation().getBlockX(),p.getLocation().getBlockZ(),0);
        if(site==null||Math.hypot(p.getX()-site.x()-.5,p.getZ()-site.z()-.5)>79||p.getY()<98||p.getY()>150){
            plugin.message(p,"เดินเข้าลานวิหารใหญ่ก่อนอัญเชิญบอส");return false;
        }
        if(active.containsKey(id(site))){plugin.message(p,status(p));return false;}
        if(active.size()>=plugin.integer("world-boss.max-active",2,1,4)){plugin.message(p,"มี World Boss กำลังต่อสู้อยู่ · ลองใหม่ภายหลัง");return false;}
        if(!force&&ledger.getLong(path(site),0)>System.currentTimeMillis()){plugin.message(p,status(p));return false;}
        for(int x:new int[]{-30,0,30})for(int z:new int[]{-30,0,30}){
            if(!plugin.world().getBlockAt(site.x()+x,100,site.z()+z).getType().isSolid()
                ||!plugin.world().getBlockAt(site.x()+x,102,site.z()+z).getType().isAir()){
                plugin.message(p,"ลานวิหารนี้ไม่พร้อม · ตรวจพื้นและพื้นที่ต่อสู้ก่อน");return false;
            }
        }
        Run r=new Run(site,new Location(plugin.world(),site.x()+.5,101,site.z()+.5),
            new JudgmentFight(number("core-health",JudgeBalance.CORE_HEALTH,1000,10000000),number("hand-health",8500,100,1000000),
                number("health-per-extra-player",.65,0,2),seconds("exposure-seconds",12,8,30),
                plugin.integer("world-boss.max-participants",16,1,40)));
        r.fight.join(p.getUniqueId());r.started=r.lastPresent=tick;r.nextAttack=tick+100;
        r.nextJudgment=Long.MAX_VALUE;
        try{
            // Own only the body chunks; warning particles never change or reserve terrain.
            int cx=Math.floorDiv(site.x(),16),cz=Math.floorDiv(site.z(),16);
            for(int x=cx-2;x<=cx+2;x++)for(int z=cz-2;z<=cz+2;z++){
                plugin.world().addPluginChunkTicket(x,z,plugin);r.tickets.add(((long)x<<32)|(z&0xffffffffL));
            }
            active.put(id(site),r);
            spawn(r,JudgmentFight.Part.LEFT);spawn(r,JudgmentFight.Part.RIGHT);spawn(r,JudgmentFight.Part.CORE);
            r.body=new JudgmentBody(plugin,r.center);
            message(r,"ผู้พิพากษาไร้ร่างตื่นขึ้น · ทำลายมือเพื่อเปิดแกนกลาง!");
            sound(r,Sound.ENTITY_ENDER_DRAGON_GROWL,1,.65f);
            return true;
        }catch(Throwable error){cleanup(r);plugin.getLogger().log(java.util.logging.Level.SEVERE,"Cannot spawn the ancient judge",error);
            plugin.message(p,"อัญเชิญไม่สำเร็จ · แจ้งแอดมินตรวจ log");return false;}
    }
    private Location anchor(Run r,JudgmentFight.Part part){
        if(part==JudgmentFight.Part.CORE)return r.center.clone().add(0,r.fight.exposed(tick)||r.fight.phase()==3?0:17,0);
        return r.center.clone().add(part==JudgmentFight.Part.LEFT?-21:21,1,-2);
    }
    private void spawn(Run r,JudgmentFight.Part part){
        Slime mob=plugin.world().spawn(anchor(r,part),Slime.class,e->{
            e.getPersistentDataContainer().set(plugin.key("world_boss_entity"),PersistentDataType.BYTE,(byte)1);
            e.getPersistentDataContainer().set(new NamespacedKey("7sins","control_resistant"),PersistentDataType.BYTE,(byte)1);
            e.addScoreboardTag("evergarden_mob");e.addScoreboardTag("no-level");e.addScoreboardTag("boss");
            // Paper derives LivingEntity.isPickable() and projectile collision from this flag.
            // AI/no gravity keep the proxy still; disabling collision would also disable swords/bows.
            e.setSize(part==JudgmentFight.Part.CORE?38:20);e.setAI(false);e.setGravity(false);e.setSilent(true);e.setCollidable(true);
            e.setPersistent(false);e.setRemoveWhenFarAway(false);
            e.setMaximumNoDamageTicks(0);
            e.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY,PotionEffect.INFINITE_DURATION,0,false,false));
            e.getAttribute(Attribute.MAX_HEALTH).setBaseValue(1024);e.setHealth(1024);
            e.getAttribute(Attribute.ATTACK_DAMAGE).setBaseValue(0);
            e.getAttribute(Attribute.KNOCKBACK_RESISTANCE).setBaseValue(1);
            e.customName(Component.text(part==JudgmentFight.Part.CORE?"แกนกลาง · ทำลายมือเพื่อเปิดผนึก":
                part==JudgmentFight.Part.LEFT?"มือแห่งพันธนาการ":"มือแห่งคำพิพากษา",NamedTextColor.LIGHT_PURPLE));
            e.setCustomNameVisible(true);
        });
        if(!mob.isValid())throw new IllegalStateException("World boss hitbox spawn was cancelled");
        LevelledMobsCompat.tagMob(mob,plugin,true);
        r.targets.put(part,mob);owners.put(mob.getUniqueId(),new Target(r,part));
    }
    private Player attacker(EntityDamageEvent e){
        if(e.getDamageSource().getCausingEntity() instanceof Player p)return p;
        if(e instanceof EntityDamageByEntityEvent by){
            if(by.getDamager() instanceof Player p)return p;
            if(by.getDamager() instanceof Projectile pr&&pr.getShooter() instanceof Player p)return p;
        }
        return null;
    }
    private JudgeDamageBudget.Kind damageKind(EntityDamageEvent e){
        var magic=Bukkit.getPluginManager().getPlugin("advance-magic");
        if(magic!=null&&magic.isEnabled()&&magic instanceof com.example.advancemagic.AdvanceMagicPlugin m&&m.context()!=null&&m.context().isMagicDamage()
            ||e.getDamageSource().getDamageType()==DamageType.MAGIC||e.getDamageSource().getDamageType()==DamageType.INDIRECT_MAGIC)
            return JudgeDamageBudget.Kind.MAGIC;
        Entity direct=e.getDamageSource().getDirectEntity();
        if(direct instanceof Projectile||e instanceof EntityDamageByEntityEvent by&&by.getDamager() instanceof Projectile)
            return JudgeDamageBudget.Kind.PROJECTILE;
        return (e.getCause()==EntityDamageEvent.DamageCause.ENTITY_ATTACK||e.getCause()==EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK)&&
            (direct instanceof Player||e instanceof EntityDamageByEntityEvent by&&by.getDamager() instanceof Player)?
            JudgeDamageBudget.Kind.MELEE:JudgeDamageBudget.Kind.OTHER;
    }
    private JudgeDamageBudget.Limits damageLimits(){return new JudgeDamageBudget.Limits(
        number("damage-caps.melee",100,1,1000000),number("damage-caps.projectile",80,1,1000000),
        number("damage-caps.magic",240,1,1000000),number("damage-caps.other",80,1,1000000),
        number("damage-caps.per-player-per-second",240,1,1000000));}
    /** Bukkit recomputes armor modifiers when raw damage changes; solve for a final-damage ceiling. */
    private void finalDamage(EntityDamageEvent e,double cap){
        if(cap<=0){e.setCancelled(true);return;}
        if(e.getFinalDamage()<=cap)return;
        double low=0,high=e.getDamage();
        for(int i=0;i<24;i++){double mid=(low+high)/2;e.setDamage(mid);if(e.getFinalDamage()>cap)high=mid;else low=mid;}
        e.setDamage(low);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void hit(EntityDamageEvent e){
        Target target=owners.get(e.getEntity().getUniqueId());Run summoned=summonOwners.get(e.getEntity().getUniqueId());
        if(target==null&&summoned==null)return;
        Player p=attacker(e);double damage=e.getFinalDamage();
        Run r=target==null?summoned:target.run;
        if(p==null||!inside(r,p)){e.setCancelled(true);return;}
        double capped=r.damageBudget.available(p.getUniqueId(),damageKind(e),damage,tick,damageLimits());
        if(target==null){
            if(!r.fight.join(p.getUniqueId())){e.setCancelled(true);return;}
            finalDamage(e,capped);if(!e.isCancelled())r.damageBudget.spend(p.getUniqueId(),e.getFinalDamage(),tick);
            return;
        }
        JudgmentFight.Hit result=r.fight.hit(p.getUniqueId(),target.part,capped,tick);
        if(result.damage()>0){
            r.damageBudget.spend(p.getUniqueId(),result.damage()*r.fight.scale(),tick);
            // Preserve genuine vanilla HP loss for Advance Magic lifesteal/accounting.
            // A replenished shell cannot die, split, drop loot or decide encounter HP.
            double shellDamage=Math.min(result.damage()*r.fight.scale(),Math.max(0,((LivingEntity)e.getEntity()).getHealth()-1));
            finalDamage(e,shellDamage);
            r.lastHit.put(p.getUniqueId(),tick);
            if(!r.bar.getPlayers().contains(p)){r.bar.addPlayer(p);plugin.message(p,"คุณเข้าร่วมต่อสู้ · หลบวงแดง และเข้าวงสีเขียวเมื่อบอสพิพากษา");}
            dust(e.getEntity().getLocation().add(0,2,0),Color.fromRGB(210,180,255),2);
            if(r.body!=null)r.body.flash(target.part);
        }else{
            e.setCancelled(true);
            if(target.part==JudgmentFight.Part.CORE)p.sendActionBar(Component.text(objective(r),NamedTextColor.LIGHT_PURPLE));
        }
        if(result.broke()){
            if(r.attack!=Attack.JUDGMENT){r.attack=null;r.marks.clear();r.body.clearSanctuaries();}
            r.nextAttack=tick+60;
            message(r,"มือยักษ์แตก! วิ่งเข้ากลางลานแล้วตีแกนที่พื้น · เปิด "+Math.max(1,(r.fight.exposedUntil()-tick+19)/20)+" วินาที");
            title(r,"แกนกลางเปิด!","เข้ากลางลาน · โจมตีอัญมณีที่พื้น");sound(r,Sound.BLOCK_AMETHYST_BLOCK_BREAK,1,.6f);
        }
        if(result.transition()){
            r.attack=null;r.marks.clear();r.nextAttack=tick+80;r.phase=r.fight.phase();
            r.nextJudgment=r.phase==2?tick+seconds("judgment-phase-two-delay-seconds",12,8,60):Long.MAX_VALUE;
            r.body.clearSanctuaries();
            message(r,r.phase==2?"เฟส 2 · มือทั้งสองฟื้นแล้ว! ทำลายมือข้างใดข้างหนึ่งอีกครั้งเพื่อเปิดแกนกลาง":
                "เฟสสุดท้าย · ไม่ต้องตีมือแล้ว! แกนกลางอยู่กลางลาน · หลบวงแดง 8 วินาที แล้วตีเมื่อผนึกเป็นสีเขียว");
            title(r,r.phase==2?"เฟส 2 · มือฟื้นคืน":"เฟสสุดท้าย · ผนึกหัวใจ",r.phase==2?"ทำลายมืออีกครั้ง → ตีแกนกลาง":"หลบวงแดง · แกนกลางเปิดใน 8 วินาที");
            sound(r,Sound.ENTITY_WITHER_SPAWN,1,.7f);
            if(r.phase==3)summonTemples(r);
        }
        if(result.broke()||result.transition())syncTargets(r);
        if(result.won())victory(r);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void regain(EntityRegainHealthEvent e){if(owns(e.getEntity()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST) public void split(SlimeSplitEvent e){if(owns(e.getEntity()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void landing(EntityChangeBlockEvent e){if(marked(e.getEntity()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST)
    public void summonDeath(EntityDeathEvent e){Run r=summonOwners.remove(e.getEntity().getUniqueId());if(r!=null){
        r.summons.remove(e.getEntity().getUniqueId());e.getDrops().clear();e.setDroppedExp(0);e.getEntity().remove();
    }}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void summonTarget(EntityTargetLivingEntityEvent e){Run r=summonOwners.get(e.getEntity().getUniqueId());
        if(r!=null&&e.getTarget()!=null&&(!(e.getTarget() instanceof Player p)||!r.fight.roster().contains(p.getUniqueId())||!inside(r,p)))e.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void summonDamage(EntityDamageByEntityEvent e){
        Entity source=e.getDamager();if(source instanceof Projectile pr&&pr.getShooter() instanceof Entity shooter)source=shooter;
        Run r=summonOwners.get(source.getUniqueId());
        if(r!=null&&(!(e.getEntity() instanceof Player p)||!r.fight.roster().contains(p.getUniqueId())||!inside(r,p)))e.setCancelled(true);
    }
    @EventHandler public void load(ChunkLoadEvent e){if(e.getWorld()==plugin.world())for(Entity entity:e.getChunk().getEntities())if(marked(entity)&&!owners.containsKey(entity.getUniqueId())){
        boolean owned=summonOwners.containsKey(entity.getUniqueId())||active.values().stream().anyMatch(r->r.body!=null&&r.body.owns(entity));
        if(!owned)entity.remove();
    }}
    public void tick(){
        tick+=2;
        if(tick%40==0&&enabled())for(Player p:plugin.world().getPlayers()){
            if(!playable(p))continue;
            Site site=plugin.bossTemples().at(p.getLocation().getBlockX(),p.getLocation().getBlockZ(),0);
            if(site!=null&&!active.containsKey(id(site))&&Math.hypot(p.getX()-site.x(),p.getZ()-site.z())<9)
                p.sendActionBar(Component.text(status(p),NamedTextColor.LIGHT_PURPLE));
        }
        for(Run r:new ArrayList<>(active.values())){
            try{update(r);}catch(Throwable error){plugin.getLogger().log(java.util.logging.Level.SEVERE,"World boss encounter stopped safely",error);abort(r,StopReason.ERROR);}
        }
    }
    private void update(Run r){
        if(!enabled()){abort(r,StopReason.DISABLED);return;}
        List<Player> players=players(r);
        if(r.fight.phase()==3){summonTemples(r);if(tick%10==0)updateSummons(r,players);}
        if(!players.isEmpty()){r.lastPresent=tick;r.nextEmptyWarning=0;}
        if(tick-r.lastPresent>=emptyTicks()){abort(r,StopReason.EMPTY);return;}
        int limit=fightLimit();
        if(limit>0&&tick-r.started>=limit){abort(r,StopReason.TIMEOUT);return;}
        if(players.isEmpty()&&tick>=r.nextEmptyWarning){
            notifyRoster(r,"ไม่มีผู้ร่วมสู้อยู่ในลาน · กลับภายใน "+Math.max(1,(r.lastPresent+emptyTicks()-tick+19)/20)+" วิ เพื่อสู้ต่อ");
            r.nextEmptyWarning=tick+400;
        }
        boolean wasExposed=r.fight.exposed(tick-2),opening=r.fight.tick(tick);
        if(opening){
            if(r.attack!=Attack.JUDGMENT){r.attack=null;r.marks.clear();r.body.clearSanctuaries();}
            r.nextAttack=tick+60;
            message(r,"ผนึกเขียว! ตีแกนกลางที่พื้นตอนนี้ · เปิด "+seconds("exposure-seconds",12,8,30)/20+" วินาที");
            title(r,"โจมตีแกนกลาง!","อัญมณีที่พื้นกลางลาน · ผนึกเปิดแล้ว");sound(r,Sound.BLOCK_BEACON_ACTIVATE,1,1.2f);
        }else if(wasExposed&&!r.fight.exposed(tick))message(r,r.fight.phase()==3?"ผนึกกลับมา · หลบวงแดง รอแกนเปิดอีก 10 วินาที":"แกนปิดแล้ว · ทำลายมือเพื่อเปิดแกนอีกครั้ง");
        if(tick%4==0||opening||wasExposed!=r.fight.exposed(tick))syncTargets(r);
        if(tick%20==0){
            for(Player old:new ArrayList<>(r.bar.getPlayers()))if(!players.contains(old))r.bar.removePlayer(old);
            for(Player p:players)if(!r.bar.getPlayers().contains(p))r.bar.addPlayer(p);
            r.bar.setProgress(Math.clamp(r.fight.core()/r.fight.coreMax(),0,1));
            String state=r.attack==Attack.JUDGMENT?"☠ พิพากษาใน "+Math.max(0,(r.resolveAt-tick+19)/20)+" วิ · เข้าวงเขียว!":objective(r);
            r.bar.setTitle("ผู้พิพากษาไร้ร่าง · เฟส "+r.fight.phase()+" · "+state+timeLimit(r));
            r.bar.setColor(r.attack==Attack.JUDGMENT?BarColor.RED:r.fight.exposed(tick)?BarColor.GREEN:BarColor.PURPLE);
            for(Player p:players)p.sendActionBar(Component.text(r.attack==null?state:attackInstruction(r),r.attack==null&&r.fight.exposed(tick)?NamedTextColor.GREEN:NamedTextColor.RED));
        }
        if(players.isEmpty()){r.attack=null;r.marks.clear();r.body.clearSanctuaries();r.nextAttack=Math.max(r.nextAttack,tick+60);return;}
        if(r.attack!=null){
            if(tick%4==0)warning(r);
            if(tick>=r.resolveAt)resolve(r,players);
        }else if(tick>=r.nextAttack){
            if(r.fight.phase()==2&&tick>=r.nextJudgment)begin(r,Attack.JUDGMENT,players);
            else{
                var pool=new ArrayList<>(r.fight.phase()==3?List.of(Attack.SKY_BEAMS,Attack.LANCES,Attack.CROSS,Attack.RING):
                    List.of(Attack.SKY_BEAMS,Attack.LANCES,Attack.SLAM,Attack.CROSS,Attack.RING));
                pool.remove(r.lastAttack);
                begin(r,r.lastAttack==null?Attack.SKY_BEAMS:pool.get(random.nextInt(pool.size())),players);
            }
        }
    }
    private String objective(Run r){
        if(r.fight.exposed(tick))return "ตีแกนกลางที่พื้น! · เปิดอีก "+Math.max(1,(r.fight.exposedUntil()-tick+19)/20)+" วิ";
        if(r.fight.phase()==3)return "หลบวงแดง · แกนกลางเปิดใน "+Math.max(1,(r.fight.openingIn(tick)+19)/20)+" วิ";
        return "ทำลายมือ → ตีแกนกลาง · ซ้าย "+Math.round(r.fight.hand(JudgmentFight.Part.LEFT)*r.fight.scale())+" / ขวา "+Math.round(r.fight.hand(JudgmentFight.Part.RIGHT)*r.fight.scale());
    }
    private String attackInstruction(Run r){
        String move=switch(r.attack){
            case SLAM -> "วิ่งออกจากวงแดง · มือทุบ";
            case CROSS -> "ออกจากเส้นแดงทั้งสองแนว · กากบาท";
            case RING -> "ออกจากแถบระหว่างวงแดง · คลื่นเวทย์";
            case SKY_BEAMS -> "ออกจากวงแดงใต้เวทกลางอากาศ · ลำแสงสวรรค์";
            case LANCES -> "ออกจากแนวแดงสามเส้น · หอกแสง";
            case JUDGMENT -> "☠ เข้าวงเขียว! · พิพากษา";
        };
        return move+"ใน "+String.format(Locale.ROOT,"%.1f",Math.max(0,r.resolveAt-tick)/20.0)+" วิ"+
            (r.attack==Attack.JUDGMENT?"":" · เหลือ "+r.pulsesLeft+" จังหวะ");
    }
    private void title(Run r,String title,String subtitle){
        var times=net.kyori.adventure.title.Title.Times.times(java.time.Duration.ofMillis(200),java.time.Duration.ofSeconds(2),java.time.Duration.ofMillis(400));
        for(Player p:players(r))p.showTitle(net.kyori.adventure.title.Title.title(Component.text(title,NamedTextColor.GOLD),Component.text(subtitle,NamedTextColor.WHITE),times));
    }
    /** Apply state changes in the same damage callback, so another cast cannot hit an old pose. */
    private void syncTargets(Run r){
            Location left=anchor(r,JudgmentFight.Part.LEFT),right=anchor(r,JudgmentFight.Part.RIGHT);
            if(r.attack==Attack.SLAM&&!r.marks.isEmpty()){
                double progress=1-Math.clamp((r.resolveAt-tick)/(double)r.warningTicks,0,1);
                Location target=r.marks.getFirst();
                Location hand=r.fight.hand(JudgmentFight.Part.LEFT)>0?left:right;
                hand.add(target.getX()-hand.getX(),10*(1-progress),target.getZ()-hand.getZ());
            }
            r.body.charge(r.attack==Attack.JUDGMENT?1-Math.clamp((r.resolveAt-tick)/(double)seconds("judgment-warning-seconds",8,8,20),0,1):0);
            r.body.update(r.fight,tick,left,right);
            for(var entry:r.targets.entrySet()){
                Slime mob=entry.getValue();if(!mob.isValid()){
                    owners.remove(mob.getUniqueId());spawn(r,entry.getKey());mob=r.targets.get(entry.getKey());
                    plugin.getLogger().warning("Restored missing world-boss hitbox "+entry.getKey()+" at "+id(r.site)+"; encounter HP retained");
                }
                if(entry.getKey()==JudgmentFight.Part.CORE){
                    int size=r.fight.exposed(tick)||r.fight.phase()==3?8:38;
                    if(mob.getSize()!=size){mob.setSize(size);mob.getAttribute(Attribute.MAX_HEALTH).setBaseValue(1024);mob.getAttribute(Attribute.ATTACK_DAMAGE).setBaseValue(0);}
                }
                Location at=entry.getKey()==JudgmentFight.Part.LEFT?left:entry.getKey()==JudgmentFight.Part.RIGHT?right:anchor(r,entry.getKey());
                boolean deadHand=entry.getKey()!=JudgmentFight.Part.CORE&&(r.fight.phase()==3||r.fight.hand(entry.getKey())<=0);
                if(deadHand)at.setY(75);
                if(mob.getLocation().distanceSquared(at)>.04)mob.teleport(at);
                mob.setVelocity(new Vector());mob.setFireTicks(0);mob.setFreezeTicks(0);mob.setHealth(1024);mob.setNoDamageTicks(0);
                if(entry.getKey()==JudgmentFight.Part.CORE)mob.customName(Component.text(r.fight.exposed(tick)?"แกนกลาง · โจมตีเต็มแรง!":r.fight.phase()==3?"แกนกลาง · เปิดใน "+Math.max(1,(r.fight.openingIn(tick)+19)/20)+" วิ":"แกนกลาง · ทำลายมือเพื่อเปิดผนึก",r.fight.exposed(tick)?NamedTextColor.GREEN:NamedTextColor.LIGHT_PURPLE));
            }
    }
    private void begin(Run r,Attack attack,List<Player> players){
        if(attack==Attack.JUDGMENT&&r.fight.phase()!=2)return;
        if(attack==Attack.SLAM&&r.fight.hand(JudgmentFight.Part.LEFT)<=0&&r.fight.hand(JudgmentFight.Part.RIGHT)<=0)attack=Attack.CROSS;
        r.pattern=JudgmentPattern.forPhase(r.fight.phase());
        r.pulsesLeft=attack==Attack.JUDGMENT?1:r.pattern.pulses();
        r.crossAngle=random.nextDouble()*Math.PI/2;
        r.body.sigils().clearAttack();
        r.attack=attack;r.lastAttack=attack;r.marks.clear();
        if(attack==Attack.JUDGMENT){
            double a=random.nextDouble()*Math.PI*2;r.safeX=Math.cos(a)*48;r.safeZ=Math.sin(a)*48;
            r.resolveAt=tick+seconds("judgment-warning-seconds",8,8,20);
            // Keep every sanctuary marker loaded for the whole warning, including low view distances.
            for(int q=0;q<4;q++){
                double angle=q*Math.PI/2;
                double sx=r.center.getX()+r.safeX*Math.cos(angle)-r.safeZ*Math.sin(angle),sz=r.center.getZ()+r.safeX*Math.sin(angle)+r.safeZ*Math.cos(angle);
                int cx=(int)Math.floor(sx)>>4,cz=(int)Math.floor(sz)>>4;
                for(int x=cx-1;x<=cx+1;x++)for(int z=cz-1;z<=cz+1;z++)ticket(r,x,z);
            }
            r.body.sanctuaries(r.safeX,r.safeZ,number("sanctuary-radius",16,16,24));
            r.nextJudgment=r.resolveAt+seconds("judgment-cooldown-seconds",130,90,600)+random.nextInt(401);
            message(r,"☠ บทพิพากษา · ONE HIT! เข้าวงเขียวภายใน "+((r.resolveAt-tick)/20)+" วินาที · Totem ฟีนิกซ์ และผักช่วยชีวิตกันไม่ได้");
            sound(r,Sound.ENTITY_WITHER_AMBIENT,1,.55f);
        }else{
            preparePulse(r,players);
        }
        warning(r);
    }
    /** Reacquire once at the start of each pulse; warnings never chase a player during the charge. */
    private void preparePulse(Run r,List<Player> players){
            r.warningTicks=switch(r.attack){case SLAM -> r.pattern.slamWarning();case CROSS,LANCES -> r.pattern.crossWarning();case SKY_BEAMS -> 44-(r.fight.phase()-1)*2;default -> r.pattern.ringWarning();};
            r.resolveAt=tick+r.warningTicks;
            r.marks.clear();
            if(r.attack==Attack.SLAM){
                var shuffled=new ArrayList<>(players);Collections.shuffle(shuffled,random);
                for(int i=0;i<Math.min(shuffled.size(),1+(r.fight.roster().size()-1)/4);i++){
                    Location at=shuffled.get(i).getLocation();at.setY(101.1);r.marks.add(at);
                    int cx=at.getBlockX()>>4,cz=at.getBlockZ()>>4;
                    for(int x=cx-1;x<=cx+1;x++)for(int z=cz-1;z<=cz+1;z++)ticket(r,x,z);
                }
            }else if(r.attack==Attack.SKY_BEAMS){
                int count=Math.min(10,5+(r.fight.roster().size()-1)/2);
                var shuffled=new ArrayList<>(players);Collections.shuffle(shuffled,random);
                for(Player p:shuffled){Location at=p.getLocation();at.setY(101.1);addSkyMark(r,at,count);}
                for(int i=0;i<160&&r.marks.size()<count;i++){
                    double a=random.nextDouble()*Math.PI*2,d=Math.sqrt(random.nextDouble())*65;
                    addSkyMark(r,r.center.clone().add(Math.cos(a)*d,.1,Math.sin(a)*d),count);
                }
                // Finite lattice fallback ensures distinct circles even if random placement is crowded.
                for(int x=-44;x<=44&&r.marks.size()<count;x+=22)for(int z=-44;z<=44;z+=22)
                    addSkyMark(r,r.center.clone().add(x,.1,z),count);
                for(Location at:r.marks){int cx=at.getBlockX()>>4,cz=at.getBlockZ()>>4;
                    for(int x=cx-1;x<=cx+1;x++)for(int z=cz-1;z<=cz+1;z++)ticket(r,x,z);}
            }else if(r.attack==Attack.RING){
                Player aim=players.get(random.nextInt(players.size()));
                r.ringRadius=Math.clamp(Math.hypot(aim.getX()-r.center.getX(),aim.getZ()-r.center.getZ())+random.nextDouble()*4-2,12,64);
            }
            sound(r,Sound.BLOCK_BEACON_POWER_SELECT,.7f,.7f);
    }
    private void addSkyMark(Run r,Location at,int count){
        if(r.marks.size()<count&&Math.hypot(at.getX()-r.center.getX(),at.getZ()-r.center.getZ())<=68
            &&r.marks.stream().allMatch(old->old.distanceSquared(at)>=400))r.marks.add(at);
    }
    private void warning(Run r){
        JudgeSigils sigils=r.body.sigils();
        switch(r.attack){
            case SLAM -> {
                for(int i=0;i<r.marks.size();i++){
                    Location at=r.marks.get(i);
                    sigils.circle("attack:slam:"+i,"judge_seal",at,r.pattern.slamRadius(),0,false);
                    sigils.circle("attack:slam-inner:"+i,"judge_orbit",at.clone().add(0,.03,0),r.pattern.slamRadius()*.65,-tick*.016,false);
                    sigils.circle("attack:cast:"+i,"judge_seal",at.clone().add(0,14,0),9,tick*.014,false);
                }
            }
            case CROSS -> {
                sigils.lane("attack:cross-x",r.center.clone().add(0,.12,0),true,r.crossAngle,r.pattern.laneHalfWidth());
                sigils.lane("attack:cross-z",r.center.clone().add(0,.15,0),false,r.crossAngle,r.pattern.laneHalfWidth());
                sigils.circle("attack:cross-cast","judge_seal",r.center.clone().add(0,9,0),16,tick*.008,false);
            }
            case RING -> {
                sigils.circle("attack:ring-inner","judge_orbit",r.center.clone().add(0,.12,0),r.ringRadius-r.pattern.ringHalfWidth(),0,false);
                sigils.circle("attack:ring-outer","judge_orbit",r.center.clone().add(0,.12,0),r.ringRadius+r.pattern.ringHalfWidth(),0,false);
                sigils.circle("attack:ring-cast","judge_seal",r.center.clone().add(0,9,0),16,-tick*.01,false);
            }
            case SKY_BEAMS -> {
                for(int i=0;i<r.marks.size();i++){
                    Location at=r.marks.get(i);
                    sigils.circle("attack:sky-floor:"+i,"judge_orbit",at,7,0,false);
                    sigils.circle("attack:sky-cast:"+i,"judge_seal",at.clone().add(0,SKY_CAST_HEIGHT,0),9,(i%2==0?1:-1)*tick*.014,false);
                    sigils.ray("attack:sky-aim:"+i,at.clone().add(0,SKY_CAST_HEIGHT,0),at,.18);
                }
            }
            case LANCES -> {
                for(int i=-1;i<=1;i++){
                    Location at=r.center.clone().add(-Math.sin(r.crossAngle)*i*22,.12,Math.cos(r.crossAngle)*i*22);
                    sigils.lane("attack:lance-lane:"+i,at,true,r.crossAngle,r.pattern.laneHalfWidth());
                    sigils.plane("attack:lance-cast:"+i,"judge_seal",at.clone().add(-Math.cos(r.crossAngle)*68,6,-Math.sin(r.crossAngle)*68),
                        new Vector(-Math.sin(r.crossAngle),0,Math.cos(r.crossAngle)),new Vector(0,1,0),12.8,12.8,tick*.01);
                }
            }
            case JUDGMENT -> {
                double radius=number("sanctuary-radius",16,16,24);
                for(int quarter=0;quarter<4;quarter++){
                    double angle=quarter*Math.PI/2;
                    Location safe=r.center.clone().add(r.safeX*Math.cos(angle)-r.safeZ*Math.sin(angle),.12,r.safeX*Math.sin(angle)+r.safeZ*Math.cos(angle));
                    sigils.circle("attack:safe:"+quarter,"judge_sanctuary",safe,radius,0,false);
                }
                sigils.circle("attack:judgment-floor","judge_orbit",r.center.clone().add(0,.15,0),76,0,false);
                // Layered overhead seals counter-rotate, like the Mythic staff cinematics.
                for(int i=0;i<3;i++)sigils.circle("attack:judgment:"+i,"judge_seal",r.center.clone().add(0,68+i*6,0),
                    18+i*7,(i%2==0?1:-1)*tick*.008,false);
                if(tick%20<6)sound(r,Sound.BLOCK_NOTE_BLOCK_BELL,1,(float)(.6+Math.min(1,(8-(r.resolveAt-tick)/20.0)*.1)));
            }
        }
    }
    private void resolve(Run r,List<Player> players){
        Attack attack=r.attack;
        for(Player p:players){
            double x=p.getX()-r.center.getX(),z=p.getZ()-r.center.getZ();
            boolean hit=switch(attack){
                case SLAM -> r.marks.stream().anyMatch(at->Math.hypot(p.getX()-at.getX(),p.getZ()-at.getZ())<=r.pattern.slamRadius());
                case CROSS -> JudgmentPattern.cross(x,z,r.crossAngle,r.pattern.laneHalfWidth());
                case RING -> JudgmentPattern.ring(x,z,r.ringRadius,r.pattern.ringHalfWidth());
                case SKY_BEAMS -> r.marks.stream().anyMatch(at->Math.hypot(p.getX()-at.getX(),p.getZ()-at.getZ())<=7);
                case LANCES -> Math.abs(x*Math.cos(r.crossAngle)+z*Math.sin(r.crossAngle))<=80&&
                    java.util.stream.IntStream.rangeClosed(-1,1).anyMatch(i->Math.abs(-x*Math.sin(r.crossAngle)+z*Math.cos(r.crossAngle)-i*22)<=r.pattern.laneHalfWidth());
                case JUDGMENT -> !JudgmentFight.sanctuary(x,z,r.safeX,r.safeZ,number("sanctuary-radius",16,16,24));
            };
            if(!hit)continue;
            if(attack==Attack.JUDGMENT){
                // True Death's final step uses direct zero HP. No cancellable damage event means
                // Totem, Phoenix and Soul Ward cannot intercept it; no curse state is copied here.
                p.setHealth(0);
            }else{
                double base=number("attack-damage",56,10,1000)*(1+(r.fight.phase()-1)*.15);
                double hp=p.getAttribute(Attribute.MAX_HEALTH).getValue();
                double damage=Math.min(Math.max(base,hp*.55),hp*number("attack-max-health-fraction",.70,.25,.90));
                if(attack==Attack.SKY_BEAMS)damage=Math.min(damage*1.15,hp*.80);
                // Apply power after the old HP ceilings so +30% really increases every red spell.
                damage*=number("attack-power-percent",JudgeBalance.ATTACK_POWER_PERCENT,10,500)/100.0;
                p.setNoDamageTicks(0);
                // sonic_boom has scaling=always. Undo vanilla's Easy/Hard multiplier so the
                // configured HP fraction stays consistent; defensive effects still run normally.
                double raw=switch(p.getWorld().getDifficulty()){
                    case EASY -> damage<=2?damage:(damage-1)*2;
                    case HARD -> damage/1.5;
                    default -> damage;
                };
                p.damage(raw,
                    DamageSource.builder(DamageType.SONIC_BOOM).withDamageLocation(r.center).build());
            }
        }
        if(attack==Attack.JUDGMENT){sound(r,Sound.ENTITY_LIGHTNING_BOLT_THUNDER,1,.6f);message(r,"บทพิพากษาสิ้นสุด · โจมตีต่อได้!");}
        else sound(r,Sound.ENTITY_IRON_GOLEM_ATTACK,1,.65f);
        if(attack==Attack.SLAM)for(Location at:r.marks)plugin.world().spawnParticle(Particle.EXPLOSION,at,4,2,.2,2,0);
        List<Location> impacts=new ArrayList<>(r.marks);
        if(attack==Attack.RING)for(int i=0;i<8;i++)impacts.add(r.center.clone().add(Math.cos(i*Math.PI/4)*r.ringRadius,.12,Math.sin(i*Math.PI/4)*r.ringRadius));
        if(impacts.isEmpty())impacts.add(r.center.clone().add(0,.12,0));
        r.body.clearSanctuaries();
        for(Location at:impacts)if(attack==Attack.SKY_BEAMS)r.body.impact(at,tick,SKY_CAST_HEIGHT,7);else r.body.impact(at,tick);
        if(attack==Attack.LANCES)for(int i=-1;i<=1;i++){
            Location at=r.center.clone().add(-Math.sin(r.crossAngle)*i*22,2,Math.cos(r.crossAngle)*i*22);
            r.body.lance(i,at.clone().add(-Math.cos(r.crossAngle)*68,0,-Math.sin(r.crossAngle)*68),
                at.clone().add(Math.cos(r.crossAngle)*68,0,Math.sin(r.crossAngle)*68),tick);
        }
        List<Player> survivors=players(r);
        if(attack!=Attack.JUDGMENT&&--r.pulsesLeft>0&&!survivors.isEmpty()){
            if(attack==Attack.CROSS||attack==Attack.LANCES)r.crossAngle+=Math.PI/6;
            preparePulse(r,survivors);warning(r);
        }else{
            r.attack=null;r.marks.clear();r.nextAttack=tick+(attack==Attack.JUDGMENT?80:r.pattern.recovery());
        }
    }
    private void dust(Location at,Color color,float size){
        if(at.getWorld().isChunkLoaded(at.getBlockX()>>4,at.getBlockZ()>>4))
            at.getWorld().spawnParticle(Particle.DUST,at,1,0,0,0,0,new Particle.DustOptions(color,size),true);
    }
    private void summonTemples(Run r){
        int added=0;
        for(UUID player:r.fight.roster())if(!r.summonedFor.contains(player)){
            int i=r.summonedFor.size();double angle=i*Math.PI*(3-Math.sqrt(5)),radius=32+(i%2)*16;
            Location at=r.center.clone().add(Math.cos(angle)*radius,0,Math.sin(angle)*radius);
            ticket(r,at.getBlockX()>>4,at.getBlockZ()>>4);
            Mob mob=JudgeTempleSummons.spawn(plugin,at,i);
            r.summons.put(mob.getUniqueId(),mob);summonOwners.put(mob.getUniqueId(),r);r.summonedFor.add(player);added++;
            r.body.impact(at,tick);
        }
        if(added>0){message(r,"เฟส 3 · บอสวิหารถูกอัญเชิญ "+added+" ตน · หนึ่งตนต่อผู้ร่วมสู้! กำจัดบริวารหรือหาโอกาสตีแกนกลาง");
            sound(r,Sound.ENTITY_EVOKER_PREPARE_SUMMON,1,.65f);updateSummons(r,players(r));}
    }
    private void updateSummons(Run r,List<Player> players){
        for(Mob mob:r.summons.values()){
            if(!mob.isValid()||mob.isDead())continue;
            if(mob.getWorld()!=r.center.getWorld()||mob.getY()<98||mob.getY()>130||
                Math.hypot(mob.getX()-r.center.getX(),mob.getZ()-r.center.getZ())>74)mob.teleport(r.center.clone().add(0,0,32));
            mob.setAI(!players.isEmpty());
            Player nearest=players.stream().min(Comparator.comparingDouble(p->p.getLocation().distanceSquared(mob.getLocation()))).orElse(null);
            if(mob.getTarget()!=nearest)mob.setTarget(nearest);
        }
    }
    private void ticket(Run r,int x,int z){if(r.tickets.add(((long)x<<32)|(z&0xffffffffL)))plugin.world().addPluginChunkTicket(x,z,plugin);}
    private void message(Run r,String text){for(UUID uuid:r.fight.roster()){Player p=Bukkit.getPlayer(uuid);if(inside(r,p))plugin.message(p,text);}}
    private void notifyRoster(Run r,String text){for(UUID uuid:r.fight.roster()){Player p=Bukkit.getPlayer(uuid);if(p!=null&&p.isOnline())plugin.message(p,text);}}
    private void sound(Run r,Sound sound,float volume,float pitch){for(Player p:players(r))p.playSound(p.getLocation(),sound,volume,pitch);}
    private boolean save(){
        try{
            Path dest=file.toPath(),tmp=dest.resolveSibling("world-bosses.yml.tmp");
            Files.createDirectories(dest.getParent());Files.writeString(tmp,ledger.saveToString());
            try{Files.move(tmp,dest,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
            catch(AtomicMoveNotSupportedException e){Files.move(tmp,dest,StandardCopyOption.REPLACE_EXISTING);}
            return true;
        }catch(IOException e){healthy=false;plugin.getLogger().log(java.util.logging.Level.SEVERE,"Cannot persist world-boss ledger; new encounters disabled",e);return false;}
    }
    private void victory(Run r){
        ledger.set("sites."+id(r.site)+".last-stop",null);
        ledger.set(path(r.site),System.currentTimeMillis()+seconds("respawn-seconds",14400,60,604800)*50L);
        int keys=plugin.integer("world-boss.rewards.keys",2,0,16),xp=plugin.integer("world-boss.rewards.experience",1500,0,1000000);
        for(UUID uuid:r.fight.roster()){
            if(r.fight.contribution(uuid)<=0)continue;
            String reward="rewards."+uuid;ledger.set(reward+".keys",ledger.getInt(reward+".keys")+keys);
            ledger.set(reward+".xp",ledger.getInt(reward+".xp")+xp);
            // Roll once on victory, independently for each contributor, then persist the exact core.
            List<String> cores=new ArrayList<>(ledger.getStringList(reward+".cores"));
            cores.add(RelicService.MAGIC_CORES.get(random.nextInt(RelicService.MAGIC_CORES.size())).id());
            ledger.set(reward+".cores",cores);
        }
        if(save()){
            message(r,"ผู้พิพากษาไร้ร่างพ่ายแพ้! ผู้ร่วมสร้างความเสียหายได้รับรางวัล");
            for(UUID uuid:r.fight.roster()){Player p=Bukkit.getPlayer(uuid);if(p!=null&&p.isOnline()&&!p.isDead())claim(p);}
        }else message(r,"บอสพ่ายแพ้ แต่บันทึกรางวัลไม่สำเร็จ · แจ้งแอดมินก่อนเริ่มไฟต์ใหม่");
        sound(r,Sound.UI_TOAST_CHALLENGE_COMPLETE,1,1);cleanup(r);
    }
    /** Persist removal before delivering: a restart cannot repeatedly claim the same reward. */
    private void claim(Player p){
        if(!healthy||p.isDead())return;
        String path="rewards."+p.getUniqueId();int keys=ledger.getInt(path+".keys"),xp=ledger.getInt(path+".xp");
        List<String> cores=ledger.getStringList(path+".cores");
        if(keys<=0&&xp<=0&&cores.isEmpty())return;
        List<ItemStack> items=new ArrayList<>();
        if(keys>0){ItemStack item=plugin.relics().createVoidKey();item.setAmount(keys);items.add(item);}
        for(String id:cores){
            RelicService.MagicCore core=RelicService.MAGIC_CORES.stream().filter(c->c.id().equals(id)).findFirst().orElse(null);
            if(core==null){plugin.getLogger().warning("Unknown queued world-boss core: "+id);return;}
            items.add(plugin.relics().createMagicCore(core));
        }
        if(!fits(p.getInventory(),items)){plugin.message(p,"รางวัลบอสถูกเก็บไว้ · เว้นช่องแล้วใช้ /evergarden boss claim");return;}
        Object previous=ledger.get(path);ledger.set(path,null);if(!save()){ledger.set(path,previous);return;}
        for(ItemStack item:items)p.getInventory().addItem(item).values().forEach(left->p.getWorld().dropItemNaturally(p.getLocation(),left));
        if(xp>0)p.giveExp(xp);
        plugin.message(p,"รางวัลผู้พิพากษาไร้ร่าง · Core สุ่ม "+cores.size()+" · กุญแจ Evergarden "+keys+" · XP "+xp);
    }
    /** Simulate Bukkit's storage stacking before consuming any persisted reward. */
    private static boolean fits(PlayerInventory inventory,List<ItemStack> rewards){
        ItemStack[] slots=Arrays.stream(inventory.getStorageContents()).map(s->s==null?null:s.clone()).toArray(ItemStack[]::new);
        for(ItemStack reward:rewards){
            int remaining=reward.getAmount(),limit=Math.min(inventory.getMaxStackSize(),reward.getMaxStackSize());
            for(ItemStack slot:slots)if(slot!=null&&!slot.getType().isAir()&&slot.isSimilar(reward)){
                int moved=Math.min(remaining,Math.max(0,limit-slot.getAmount()));slot.setAmount(slot.getAmount()+moved);remaining-=moved;
            }
            for(int i=0;i<slots.length&&remaining>0;i++)if(slots[i]==null||slots[i].getType().isAir()){
                slots[i]=reward.clone();int moved=Math.min(remaining,limit);slots[i].setAmount(moved);remaining-=moved;
            }
            if(remaining>0)return false;
        }
        return true;
    }
    @EventHandler public void join(PlayerJoinEvent e){Bukkit.getScheduler().runTaskLater(plugin,()->{if(e.getPlayer().isOnline())claim(e.getPlayer());},20);}
    @EventHandler public void respawn(PlayerRespawnEvent e){Bukkit.getScheduler().runTaskLater(plugin,()->{if(e.getPlayer().isOnline())claim(e.getPlayer());},20);}
    public void claimReward(Player p){claim(p);}
    private void abort(Run r,StopReason reason){
        notifyRoster(r,"การพิพากษาสิ้นสุดลง · "+reason.text+" · บอสจะพร้อมอัญเชิญใหม่ใน 30 วินาที");
        plugin.getLogger().info("World boss at "+id(r.site)+" ended: "+reason.name());
        ledger.set("sites."+id(r.site)+".last-stop",reason.name());
        ledger.set(path(r.site),System.currentTimeMillis()+30000);save();cleanup(r);
    }
    public void stop(Player p){
        if(p.getWorld()!=plugin.world())return;
        Site site=plugin.bossTemples().at(p.getLocation().getBlockX(),p.getLocation().getBlockZ(),0);
        Run r=site==null?null:active.get(id(site));if(r!=null)abort(r,StopReason.ADMIN);else plugin.message(p,"ไม่มีบอสกำลังต่อสู้ที่นี่");
    }
    private void cleanup(Run r){
        active.remove(id(r.site));r.bar.removeAll();
        for(Slime mob:r.targets.values()){owners.remove(mob.getUniqueId());mob.remove();}
        for(Mob mob:r.summons.values()){summonOwners.remove(mob.getUniqueId());mob.remove();}
        r.summons.clear();r.summonedFor.clear();
        if(r.body!=null)r.body.close();
        for(long chunk:r.tickets)plugin.world().removePluginChunkTicket((int)(chunk>>32),(int)chunk,plugin);
        r.tickets.clear();
    }
    @Override public void close(){closed=true;for(Run r:new ArrayList<>(active.values()))abort(r,StopReason.SHUTDOWN);}
}
