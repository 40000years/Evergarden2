package com.example.voidscape.boss;

import com.example.voidscape.VoidscapePlugin;
import com.example.voidscape.compat.LevelledMobsCompat;
import com.example.voidscape.world.BossStructureLayout;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.boss.*;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import java.util.*;
import static com.example.voidscape.boss.ArkhaiaSovereign.Move;
import static com.example.voidscape.boss.ArkhaiaSovereign.State;

/** Owns Arkhaia's duel: telegraphed sword strings, posture breaks, the river ritual and the phase-three execution. */
final class ArkhaiaBoss implements Listener,AutoCloseable {
    /** Eye level of the Dreadrose Sovereign rig at scale 3.2 (visor slits at 34.75 model units). */
    static final double EYE_HEIGHT=6.95;
    private static final double LEASH=36,DUEL_RADIUS=10;
    /** Authored clip lengths in seconds (arkhaia.bbmodel). */
    private static final Map<String,Double> CLIP_LENGTH=Map.ofEntries(Map.entry("idle",2.4),Map.entry("walk",.8),Map.entry("backstep",.8),Map.entry("hover",1.4),
        Map.entry("light_windup",.7),Map.entry("light_active",.4),Map.entry("light_recover",1.8),Map.entry("heavy_windup",1.0),Map.entry("heavy_active",.6),Map.entry("heavy_recover",2.0),
        Map.entry("rift_windup",2.0),Map.entry("rift_active",1.7),Map.entry("rift_recover",2.4),Map.entry("nova_windup",2.4),Map.entry("nova_active",2.4),Map.entry("nova_recover",2.6),
        Map.entry("portals_windup",2.3),Map.entry("portals_active",2.6),Map.entry("portals_recover",2.6),Map.entry("eclipse_windup",3.5),Map.entry("eclipse_active",2.7),Map.entry("eclipse_recover",3.2),
        Map.entry("stun",1.8),Map.entry("transition",3.5));
    private final Map<UUID,Integer> interactions=new HashMap<>();
    private record Pending(String key,ArkhaiaPattern.Hazard hazard,long fire,double damage,boolean lethal,boolean portal){}
    private record Trail(String key,Location at,long until){}
    private static final class Ring {
        final String key;final Location origin;final long start;final Set<UUID> hit=new HashSet<>();
        Ring(String key,Location origin,long start){this.key=key;this.origin=origin;this.start=start;}
    }
    private static final class Run {
        final Location center;
        final Location position;
        final ArkhaiaSovereign sovereign;
        final ArkhaiaLook look=new ArkhaiaLook();
        ArkhaiaLook.Gaze gaze=new ArkhaiaLook.Gaze(0,0);
        final ArkhaiaEffects effects;
        final BossAura aura;
        final Random random;
        final List<Entity> parts=new ArrayList<>();
        final Map<UUID,String> viewers=new HashMap<>();
        final Map<UUID,Long> hits=new HashMap<>();
        final Set<UUID> contributors=new HashSet<>(),roster=new HashSet<>();
        final Map<UUID,Long> feedback=new HashMap<>();
        final BossBar bar=Bukkit.createBossBar("Arkhaia",BarColor.PURPLE,BarStyle.SOLID);
        final BossBar stance=Bukkit.createBossBar("เกจเสียหลัก",BarColor.YELLOW,BarStyle.SEGMENTED_10);
        Slime target;ArmorStand bedrock;
        ArkhaiaModelSupport.Rig rig;
        ArkhaiaMinions minions;
        final Set<Chunk> chunks=new HashSet<>();
        long lastPresent,emptySince=-1,nextRigAttempt,nextBloom,reflectUntil;
        double vx,vz,verticalSpeed;
        UUID victim;
        ArkhaiaSovereign.Act act=new ArkhaiaSovereign.Act(State.NEUTRAL,Move.NONE,0,0,false,0,0);
        String bodyPose="idle";
        double bodyTime,bodyRate=1;
        boolean stopped,rebuilding,bedrockNative,applyingImpact,regrouped;
        // Committed geometry: the shape that warns is the shape that hits.
        ArkhaiaPattern.Hazard strike;
        final List<Pending> pending=new ArrayList<>();
        final List<Ring> rings=new ArrayList<>();
        final List<Trail> trails=new ArrayList<>();
        Vector chargeDirection;Location lastTrail;final Set<UUID> chargeHit=new HashSet<>();
        final EnumSet<ArkhaiaWaves.Kind> ritualKinds=EnumSet.noneOf(ArkhaiaWaves.Kind.class),ritualKilled=EnumSet.noneOf(ArkhaiaWaves.Kind.class);
        final EnumSet<ArkhaiaWaves.Kind> blessings=EnumSet.noneOf(ArkhaiaWaves.Kind.class),lastRitual=EnumSet.noneOf(ArkhaiaWaves.Kind.class);
        UUID duelist;Location duelCenter;int duelTurn,serial;
        final Map<UUID,Long> bleeding=new HashMap<>(),raised=new HashMap<>(),grazed=new HashMap<>();
        final Set<UUID> blocking=new HashSet<>();
        Run(VoidscapePlugin plugin,Location center,long tick){
            this.center=center;position=center.clone();lastPresent=tick;
            random=new Random(UUID.randomUUID().getMostSignificantBits());
            var combat=plugin.getConfig();
            sovereign=new ArkhaiaSovereign(random,combat.getDouble("arkhaia.combat.health",7000),
                combat.getDouble("arkhaia.combat.health-per-extra-player",1500),combat.getDouble("arkhaia.combat.health-cap",10000));
            effects=new ArkhaiaEffects(plugin);aura=new BossAura(plugin,BossAura.Style.ARKHAIA);
        }
    }
    private final VoidscapePlugin plugin;
    private final Map<String,Run> active=new HashMap<>();
    private record Demo(ArkhaiaMinions minions,Location center,long until){}
    private final Map<UUID,Demo> demos=new HashMap<>();
    private final Map<Chunk,Integer> tickets=new HashMap<>();
    private long tick;
    private boolean closed;
    private final org.bukkit.scheduler.BukkitTask driver;
    ArkhaiaBoss(VoidscapePlugin plugin){this.plugin=plugin;driver=Bukkit.getScheduler().runTaskTimer(plugin,this::tick,20,1);}
    boolean owns(Entity e){return active.values().stream().anyMatch(r->r.parts.contains(e)||r.effects.owns(e)||r.minions!=null&&r.minions.owns(e))||demos.values().stream().anyMatch(d->d.minions.owns(e))
        ||plugin.arkhaiaBedrock()!=null&&plugin.arkhaiaBedrock().registered(e.getUniqueId());}
    private boolean admin(Player p){return p!=null&&(p.isOp()||p.hasPermission("evergarden.admin")||p.hasPermission("voidscape.admin"));}
    boolean riverTest(Player p,int kind,boolean combat){
        if(closed||!admin(p)||p.isDead())return false;
        if(kind< -1||kind>=ArkhaiaWaves.Kind.values().length)return false;
        if(plugin.arkhaiaModels()==null||!plugin.arkhaiaModels().ready()){plugin.message(p,"Model Engine กำลังเตรียมโมเดล กรุณารอสักครู่");return false;}
        if(!JudgePackService.bedrock(p)&&!plugin.judgePack().applied(p)){plugin.judgePack().offer(p);plugin.message(p,"รับแพ็ก World Boss ให้เสร็จ แล้วกดเสกอีกครั้ง");return false;}
        Location center=p.getLocation().clone();var forward=center.getDirection().setY(0);if(forward.lengthSquared()<.01)forward=new org.bukkit.util.Vector(0,0,1);forward.normalize();
        center.add(forward.clone().multiply(6));center.setPitch(0);
        var right=new org.bukkit.util.Vector(forward.getZ(),0,-forward.getX());
        List<Location> spots=new ArrayList<>();
        int count=kind<0?6:1;
        for(int i=0;i<count;i++){
            Location at=center.clone().add(right.clone().multiply(count==1?0:(i%3-1)*10)).add(forward.clone().multiply(count==1?0:(i/3)*10));
            boolean floor=at.clone().subtract(0,.1,0).getBlock().getType().isSolid();
            for(int y=0;y<12;y++)for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)if(!at.clone().add(x,y,z).getBlock().isPassable())floor=false;
            if(!floor){plugin.message(p,"เลือกพื้นที่ราบโล่งด้านหน้าอย่างน้อย 26 × 16 บล็อกก่อนเสกตัวทดสอบ");return false;}spots.add(at);
        }
        clearRiverTest(p);
        ArkhaiaMinions minions=new ArkhaiaMinions(plugin,center,player->player.isOnline()&&!player.isDead()&&player.getWorld()==center.getWorld()
            &&(player.getGameMode()==GameMode.SURVIVAL||player.getGameMode()==GameMode.ADVENTURE)&&Math.abs(player.getY()-center.getY())<10
            &&Math.hypot(player.getX()-center.getX(),player.getZ()-center.getZ())<=BossStructureLayout.Kind.ARKHAIA.arenaRadius,()->null,now->{});
        minions.demo(p.getUniqueId(),!combat);minions.initializeClock(tick);demos.put(p.getUniqueId(),new Demo(minions,center,tick+20*600));
        try{for(int i=0;i<count;i++)minions.summon(ArkhaiaWaves.Kind.values()[kind<0?i:kind],spots.get(i));}
        catch(RuntimeException error){clearRiverTest(p);plugin.getLogger().log(java.util.logging.Level.WARNING,"Cannot summon river test",error);return false;}
        plugin.message(p,combat?"เสกตัวทดสอบต่อสู้แล้ว — เข้า Survival เพื่อให้เริ่มโจมตี":"เสกโมเดลลูกน้องแล้ว — เปลี่ยนท่าหรือลบได้ในเมนูลูกน้อง Arkhaia");return true;
    }
    void clearRiverTest(Player p){if(!admin(p))return;Demo demo=demos.remove(p.getUniqueId());if(demo!=null)demo.minions.close();}
    void riverPose(Player p,String pose){if(!admin(p)||!Set.of("idle","walk","cast","stun").contains(pose))return;Demo demo=demos.get(p.getUniqueId());if(demo!=null)demo.minions.demoPose(pose);}
    private boolean inside(Run r,Player p){return p.isOnline()&&!p.isDead()&&p.getWorld()==r.center.getWorld()
        &&p.getGameMode()!=GameMode.SPECTATOR&&Math.abs(p.getY()-r.center.getY())<20
        &&Math.hypot(p.getX()-r.center.getX(),p.getZ()-r.center.getZ())<=BossStructureLayout.Kind.ARKHAIA.arenaRadius;}
    private void tag(Entity e){
        e.setPersistent(false);e.setGravity(false);
        e.getPersistentDataContainer().set(plugin.key("world_boss_entity"),PersistentDataType.BYTE,(byte)1);
        e.getPersistentDataContainer().set(plugin.key("arkhaia_boss"),PersistentDataType.BYTE,(byte)1);
        e.addScoreboardTag("evergarden_unique");e.addScoreboardTag("arkhaia");
    }
    @EventHandler(priority=EventPriority.LOWEST)
    public void altar(PlayerInteractEvent event){
        var block=event.getClickedBlock();var p=event.getPlayer();
        if(closed||block==null||block.getWorld()!=plugin.world()||event.getAction()!=Action.RIGHT_CLICK_BLOCK
            ||event.getHand()!=EquipmentSlot.HAND||block.getType()!=Material.RED_GLAZED_TERRACOTTA)return;
        var site=plugin.bossStructures().at(block.getX(),block.getZ(),0);
        if(site==null||site.kind()!=BossStructureLayout.Kind.ARKHAIA||!plugin.bossStructures().enabled(site.kind())
            ||block.getY()!=site.kind().floorY||Math.hypot(block.getX()-site.x(),block.getZ()-site.z())>=5)return;
        event.setCancelled(true);
        if(p.getGameMode()!=GameMode.SURVIVAL&&p.getGameMode()!=GameMode.ADVENTURE&&p.getGameMode()!=GameMode.CREATIVE)return;
        if(p.getInventory().getItemInMainHand().getType()!=Material.NETHERITE_BLOCK){
            plugin.message(p,"ถือบล็อกเนเธอไรต์ในมือหลัก แล้วคลิกขวาตรงกลางลานเพื่ออัญเชิญ Arkhaia");return;
        }
        String id=site.x()+"_"+site.z();
        if(active.containsKey(id)){plugin.message(p,"Arkhaia ถูกอัญเชิญอยู่ในลานนี้แล้ว");return;}
        long readyAt=plugin.world().getPersistentDataContainer().getOrDefault(readyKey(id),PersistentDataType.LONG,0L),clock=plugin.world().getGameTime();
        if(clock<readyAt){plugin.message(p,"ลานนี้กำลังพักฟื้น · อัญเชิญได้อีกครั้งในอีก "+(readyAt-clock+1199)/1200+" นาที");return;}
        if(!JudgePackService.bedrock(p)&&!plugin.judgePack().applied(p)){
            plugin.judgePack().offer(p);plugin.message(p,"รับแพ็ก World Boss ให้เสร็จ แล้วคลิกอัญเชิญอีกครั้ง");return;
        }
        // The Dreadrose Sovereign exists only as its articulated ModelEngine/Geyser rig.
        if(plugin.arkhaiaModels()==null||!plugin.arkhaiaModels().ready("arkhaia")){
            plugin.message(p,"Model Engine กำลังเตรียมโมเดล Arkhaia กรุณารอสักครู่ · /evergarden boss pack");return;
        }
        var center=new Location(plugin.world(),site.x()+.5,site.kind().floorY+1,site.z()+.5);
        if(p.getLocation().distanceSquared(center)>100)return;
        // Check the full enlarged body before spawning; no blocks are overwritten.
        for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)for(int y=0;y<9;y++)
            if(!center.clone().add(x,y,z).getBlock().isPassable()){
                plugin.message(p,"พื้นที่กลางลานไม่ว่างพอสำหรับ Arkhaia");return;
            }
        Run r=new Run(plugin,center,tick);
        try{
            hold(r);
            spawnParts(r);
            r.minions=new ArkhaiaMinions(plugin,r.center,p2->opponent(r,p2),()->r.target,now->{});
            // Ritual spirits are a timed side objective, not six extra sanctuary bosses.
            r.minions.ritualScale(plugin.getConfig().getDouble("arkhaia.ritual.minion-health",.5),plugin.getConfig().getDouble("arkhaia.ritual.minion-damage",.75));
            // Spirits appear only through the river ritual; no automatic roster.
            r.minions.demo(null,false);r.minions.initializeClock(tick);
            r.minions.onDefeat(defeat->r.ritualKilled.add(defeat.kind()));
            active.put(id,r);update(r);
            // The offering is spent only once the summon actually succeeded.
            if(p.getGameMode()!=GameMode.CREATIVE){var hand=p.getInventory().getItemInMainHand();hand.setAmount(hand.getAmount()-1);p.getInventory().setItemInMainHand(hand.getAmount()>0?hand:null);}
            plugin.world().playSound(center,Sound.ENTITY_WITHER_SPAWN,1,.65f);
            plugin.message(p,"อัญเชิญ Arkhaia แล้ว · ใช้บล็อกเนเธอไรต์ 1 ก้อน · เลือด "+(int)r.sovereign.maxHealth()+" (เพิ่มตามจำนวนผู้เล่น สูงสุด "
                +(int)ArkhaiaSovereign.scaledHealth(plugin.getConfig().getDouble("arkhaia.combat.health",7000),0,plugin.getConfig().getDouble("arkhaia.combat.health-cap",10000),1)+")");
            plugin.message(p,"§7ทำลายเกจเสียหลักเพื่อให้มันทรุด · ฟันสวนตอนดาบเรืองทอง หรือยกโล่รับพอดีจังหวะ = ปัดดาบ");
        }catch(Exception error){destroy(r);plugin.getLogger().log(java.util.logging.Level.SEVERE,"Cannot summon Arkhaia",error);
            plugin.message(p,"อัญเชิญ Arkhaia ไม่สำเร็จ · ตรวจสอบ server log");}
    }
    private NamespacedKey readyKey(String site){return plugin.key("arkhaia_ready_"+site);}
    private void hold(Run r){
        int radius=BossStructureLayout.Kind.ARKHAIA.arenaRadius+8;
        for(int x=(r.center.getBlockX()-radius)>>4;x<=(r.center.getBlockX()+radius)>>4;x++)
            for(int z=(r.center.getBlockZ()-radius)>>4;z<=(r.center.getBlockZ()+radius)>>4;z++){
                Chunk chunk=r.center.getWorld().getChunkAt(x,z);
                if(tickets.merge(chunk,1,Integer::sum)==1)chunk.addPluginChunkTicket(plugin);
                r.chunks.add(chunk);
            }
    }
    private void spawnParts(Run r){
            r.target=plugin.world().spawn(r.position,Slime.class,e->{
                tag(e);LevelledMobsCompat.tagMob(e,plugin,true);
                // Paper's isPickable() reads collidable: false also makes arrows pass through.
                // Player exemptions below suppress body pushing without disabling projectile hits.
                e.setSize(8);e.setAI(false);e.setSilent(true);e.setCollidable(true);
                e.setRemoveWhenFarAway(false);e.setInvisible(true);e.setNoDamageTicks(0);
                e.getAttribute(Attribute.MAX_HEALTH).setBaseValue(1024);e.setHealth(1024);
                e.getAttribute(Attribute.ATTACK_DAMAGE).setBaseValue(0);
                e.getAttribute(Attribute.KNOCKBACK_RESISTANCE).setBaseValue(1);
            });r.parts.add(r.target);
            r.bedrockNative=plugin.arkhaiaBedrock()!=null&&plugin.arkhaiaBedrock().ready();
            if(!r.bedrockNative&&plugin.arkhaiaBedrock()!=null)plugin.getLogger().warning("Arkhaia: Geyser custom entity API unavailable; Bedrock players will not see the rig");
            r.bedrock=plugin.world().spawn(r.position,ArmorStand.class,e->{
                tag(e);e.setVisibleByDefault(false);e.setVisible(r.bedrockNative);e.setInvulnerable(true);
                e.setSilent(true);e.setMarker(false);e.setBasePlate(false);e.setCollidable(false);
                if(r.bedrockNative)plugin.arkhaiaBedrock().register(e.getUniqueId());
                for(var slot:EquipmentSlot.values())e.addEquipmentLock(slot,ArmorStand.LockType.ADDING_OR_CHANGING);
            });r.parts.add(r.bedrock);
            if(r.parts.stream().anyMatch(e->!e.isValid()))throw new IllegalStateException("Arkhaia spawn cancelled");
    }
    private void recoverParts(Run r){
        plugin.getLogger().warning("Recovering Arkhaia parts at "+r.center+"; phase="+r.sovereign.phase()+" hp="+r.sovereign.health()
            +" missing="+r.parts.stream().filter(e->!e.isValid()).map(e->e.getType()+":"+e.getUniqueId()).toList());
        if(r.rig!=null){r.rig.close();r.rig=null;}
        if(r.bedrock!=null&&plugin.arkhaiaBedrock()!=null)plugin.arkhaiaBedrock().unregister(r.bedrock.getUniqueId());
        r.rebuilding=true;
        try{r.parts.forEach(Entity::remove);r.parts.clear();spawnParts(r);r.viewers.clear();r.nextRigAttempt=0;}
        finally{r.rebuilding=false;}
    }
    @EventHandler(priority=EventPriority.MONITOR)
    public void removed(EntityRemoveEvent event){
        for(Run r:active.values())if(r.minions!=null)r.minions.removed(event);
        for(Demo d:demos.values())d.minions.removed(event);
        for(Run r:active.values())if(!r.stopped&&!r.rebuilding&&r.parts.contains(event.getEntity()))
            plugin.getLogger().warning("Arkhaia part removed: "+event.getEntity().getType()+" cause="+event.getCause()
                +" phase="+r.sovereign.phase()+" hp="+r.sovereign.health()+" at="+r.position+"; will recover next tick");
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void minionTarget(EntityTargetLivingEntityEvent event){
        for(Run r:active.values())if(r.minions!=null&&r.minions.owns(event.getEntity())&&event.getTarget()!=null
            &&(!(event.getTarget() instanceof Player p)||!opponent(r,p))){event.setCancelled(true);return;}
        for(Demo d:demos.values())if(d.minions.owns(event.getEntity())&&event.getTarget()!=null
            &&(!(event.getTarget() instanceof Player p)||!d.minions.accepts(p))){event.setCancelled(true);return;}
    }
    private Player attacker(EntityDamageEvent event){
        if(event.getDamageSource().getCausingEntity() instanceof Player player)return player;
        if(event instanceof EntityDamageByEntityEvent by){
            if(by.getDamager() instanceof Player player)return player;
            if(by.getDamager() instanceof Projectile projectile&&projectile.getShooter() instanceof Player player)return player;
        }
        return null;
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void minionAttack(EntityDamageByEntityEvent event){
        for(Run r:active.values()){
            // Slimes retain native touch attacks even with AI disabled. Only the
            // explicitly scoped encounter impacts may deal damage from this base.
            if(event.getDamager()==r.target&&!r.applyingImpact){event.setCancelled(true);return;}
            if(r.minions!=null&&r.minions.outgoing(event))return;
        }
        for(Demo d:demos.values())if(d.minions.outgoing(event))return;
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void riverProjectile(ProjectileHitEvent event){
        for(Run r:active.values())if(r.minions!=null&&r.minions.projectileHit(event))return;
        for(Demo d:demos.values())if(d.minions.projectileHit(event))return;
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void soulHealing(EntityRegainHealthEvent event){
        for(Run r:active.values())if(r.minions!=null&&r.minions.healing(event))return;
        for(Demo d:demos.values())if(d.minions.healing(event))return;
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void soulPotion(EntityPotionEffectEvent event){
        for(Run r:active.values())if(r.minions!=null&&r.minions.potion(event))return;
        for(Demo d:demos.values())if(d.minions.potion(event))return;
    }
    private String bodyClip(Run r){return r.bodyPose;}
    @EventHandler(priority=EventPriority.MONITOR)
    public void used(PlayerInteractEvent event){
        if(event.getAction()!=Action.LEFT_CLICK_AIR)interactions.put(event.getPlayer().getUniqueId(),Bukkit.getCurrentTick());
    }
    @EventHandler(priority=EventPriority.MONITOR)
    public void usedEntity(PlayerInteractEntityEvent event){interactions.put(event.getPlayer().getUniqueId(),Bukkit.getCurrentTick());}
    @EventHandler public void quit(PlayerQuitEvent event){interactions.remove(event.getPlayer().getUniqueId());}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void riverRoot(org.bukkit.event.player.PlayerMoveEvent event){
        for(Run r:active.values())if(r.minions!=null)r.minions.moved(event);
        for(Demo d:demos.values())d.minions.moved(event);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void riverTeleporting(org.bukkit.event.player.PlayerTeleportEvent event){
        for(Run r:active.values())if(r.minions!=null)r.minions.teleporting(event);
        for(Demo d:demos.values())d.minions.teleporting(event);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void riverRootVelocity(org.bukkit.event.player.PlayerVelocityEvent event){
        for(Run r:active.values())if(r.minions!=null)r.minions.velocity(event);
        for(Demo d:demos.values())d.minions.velocity(event);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void riverRootMount(EntityMountEvent event){
        for(Run r:active.values())if(r.minions!=null)r.minions.mounting(event);
        for(Demo d:demos.values())d.minions.mounting(event);
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void riverTeleport(org.bukkit.event.player.PlayerTeleportEvent event){
        for(Run r:active.values())if(r.minions!=null)r.minions.teleported(event);
        for(Demo d:demos.values())d.minions.teleported(event);
    }
    @EventHandler public void riverDeparture(PlayerQuitEvent event){releaseRoots(event.getPlayer());}
    @EventHandler public void riverDeath(PlayerDeathEvent event){releaseRoots(event.getEntity());}
    private void releaseRoots(Player player){
        for(Run r:active.values())if(r.minions!=null)r.minions.departed(player);
        for(Demo d:demos.values())d.minions.departed(player);
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void swing(PlayerAnimationEvent event){
        if(event.getAnimationType()!=PlayerAnimationType.ARM_SWING)return;
        Player player=event.getPlayer();if(player.getGameMode()!=GameMode.SURVIVAL&&player.getGameMode()!=GameMode.ADVENTURE)return;
        long swung=tick;int serverTick=Bukkit.getCurrentTick();
        Location eye=player.getEyeLocation();
        double reach=BossHitbox.reach(player),nearest=Double.POSITIVE_INFINITY;LivingEntity receiver=null;
        for(Run r:active.values()){
            if(opponent(r,player)&&(!r.hits.containsKey(player.getUniqueId())||tick-r.hits.get(player.getUniqueId())>=10)){
                double distance=BossHitbox.ray(player,"arkhaia",bodyClip(r),r.bodyTime,r.position,3.2,reach);
                if(distance<nearest){nearest=distance;receiver=r.target;}
            }
            if(r.minions!=null){var aim=r.minions.meleeAim(player,reach,swung);if(aim!=null&&aim.getValue()<nearest){nearest=aim.getValue();receiver=aim.getKey();}}
        }
        for(Demo d:demos.values()){var aim=d.minions.meleeAim(player,reach,swung);if(aim!=null&&aim.getValue()<nearest){nearest=aim.getValue();receiver=aim.getKey();}}
        if(receiver==null||!receiver.isValid())return;
        var obstruction=nearest<=0?null:player.getWorld().rayTraceEntities(eye,eye.getDirection(),nearest,
            e->e!=player&&e instanceof LivingEntity&&!owns(e));
        if(obstruction!=null)return;
        LivingEntity selected=receiver;double distance=nearest;
        // A normal client hit arrives in the same tick. Fill only a missed body
        // selection (e.g. a foot outside the hidden skeleton), using native attack
        // damage/cooldown/enchantments. Retain the pose the player swung at:
        // a moving limb must not be selected again one scheduler tick later.
        Bukkit.getScheduler().runTask(plugin,()->{
            if(closed||!player.isOnline()||interactions.getOrDefault(player.getUniqueId(),Integer.MIN_VALUE)>=serverTick)return;
            if(!selected.isValid()||selected.getWorld()!=player.getWorld())return;
            BossHitbox.attack(player,selected,eye,distance);
        });
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void damage(EntityDamageEvent event){
        Run r=active.values().stream().filter(run->run.parts.contains(event.getEntity())).findFirst().orElse(null);
        if(r==null){for(Run run:active.values())if(run.minions!=null&&run.minions.damage(event,attacker(event)))return;for(Demo d:demos.values())if(d.minions.damage(event,attacker(event)))return;return;}
        double damage=event.getFinalDamage();event.setCancelled(true);
        if(event.getEntity()!=r.target||!Double.isFinite(damage)||damage<=0)return;
        Player p=attacker(event);
        if(p==null||!opponent(r,p))return;
        ArkhaiaRules.Kind kind=damageKind(event);
        if(kind==ArkhaiaRules.Kind.MELEE&&!BossHitbox.confirmed(p,event.getEntity())&&!BossHitbox.melee(p,"arkhaia",bodyClip(r),r.bodyTime,r.position,3.2,event.getCause()==EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK)){
            blocked(r,p,"Approach within melee range and keep line of sight");return;
        }
        Long last=r.hits.get(p.getUniqueId());if(last!=null&&tick-last<10)return;
        r.hits.put(p.getUniqueId(),tick);
        if(r.sovereign.invulnerable()){blocked(r,p,r.sovereign.state()==State.RITUAL?"เกราะกุหลาบ — ทำลายวิญญาณที่โยงโซ่อยู่ก่อน":"Arkhaia กำลังเปลี่ยนเฟส");return;}
        double dx=p.getX()-r.position.getX(),dz=p.getZ()-r.position.getZ();float yaw=r.position.getYaw();
        boolean rear=ArkhaiaRules.behind(yaw,dx,dz);
        if(kind==ArkhaiaRules.Kind.MELEE&&ArkhaiaRules.front(yaw,dx,dz)&&Math.hypot(dx,dz)<=8&&r.sovereign.parry(tick,false))parried(r,p,false);
        boolean bystander=r.act.duel()&&r.duelist!=null&&!r.duelist.equals(p.getUniqueId());
        double dealt=r.sovereign.damage(damage,ArkhaiaRules.posture(kind,rear,bystander),tick);
        if(dealt>0)r.contributors.add(p.getUniqueId());
        if(dealt>0&&tick<r.reflectUntil){
            // Banshee's absorbed wail: a reflecting stance after heavy blows, never recursive.
            double reflected=Math.min(dealt*.3,p.getAttribute(Attribute.MAX_HEALTH).getValue()*.25);magic(r,p,reflected,false);
            p.playSound(p.getLocation(),Sound.ENTITY_ALLAY_HURT,.8f,.5f);
        }
        ArkhaiaHitFeedback.confirm(p,r.target,event,dealt,rear);
        r.target.getWorld().playSound(r.position,Sound.ENTITY_IRON_GOLEM_HURT,.8f,.65f);
        updateBar(r);
    }
    private ArkhaiaRules.Kind damageKind(EntityDamageEvent event){
        var magic=Bukkit.getPluginManager().getPlugin("advance-magic");
        if(magic instanceof com.example.advancemagic.AdvanceMagicPlugin m&&m.isEnabled()&&m.context()!=null&&m.context().isMagicDamage())
            return ArkhaiaRules.Kind.ADVANCE_MAGIC;
        if(event.getCause()==EntityDamageEvent.DamageCause.PROJECTILE||event.getDamageSource().getDirectEntity() instanceof Projectile
            ||event instanceof EntityDamageByEntityEvent by&&by.getDamager() instanceof Projectile)return ArkhaiaRules.Kind.PROJECTILE;
        boolean physical=event.getDamageSource().getDamageType()==DamageType.PLAYER_ATTACK||event.getDamageSource().getDamageType()==DamageType.MOB_ATTACK;
        return physical&&(event.getCause()==EntityDamageEvent.DamageCause.ENTITY_ATTACK||event.getCause()==EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK)
            &&event instanceof EntityDamageByEntityEvent by&&by.getDamager() instanceof Player?ArkhaiaRules.Kind.MELEE:ArkhaiaRules.Kind.OTHER;
    }
    private void blocked(Run r,Player player,String text){
        if(tick<r.feedback.getOrDefault(player.getUniqueId(),0L))return;r.feedback.put(player.getUniqueId(),tick+30);
        player.sendActionBar(net.kyori.adventure.text.Component.text(text));
        player.playSound(player.getLocation(),Sound.BLOCK_AMETHYST_BLOCK_HIT,.45f,1.5f);
    }
    private void announce(Run r,String text){for(Player player:r.center.getWorld().getPlayers())if(inside(r,player))plugin.message(player,text);}
    private void title(Run r,String title,String subtitle){for(Player player:r.center.getWorld().getPlayers())if(inside(r,player))player.sendTitle(title,subtitle,6,50,12);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void magicAffect(com.example.advancemagic.api.MagicAffectEvent event){
        for(Run run:active.values())if(run.minions!=null&&run.minions.rejectsMagic(event.getTarget())){event.setCancelled(true);return;}
        for(Demo d:demos.values())if(d.minions.rejectsMagic(event.getTarget())){event.setCancelled(true);return;}
        Run r=active.values().stream().filter(run->run.parts.contains(event.getTarget())).findFirst().orElse(null);
        Player player=event.getCaster();
        if(r!=null){if(event.getTarget()!=r.target||!opponent(r,player))event.setCancelled(true);return;}
        for(Run run:active.values())if(run.minions!=null&&run.minions.owns(event.getTarget())&&!opponent(run,player)){event.setCancelled(true);return;}
        for(Demo d:demos.values())if(d.minions.owns(event.getTarget())&&!d.minions.accepts(player)){event.setCancelled(true);return;}
    }
    @EventHandler(priority=EventPriority.HIGHEST)
    public void split(SlimeSplitEvent event){if(owns(event.getEntity()))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST)
    public void death(EntityDeathEvent event){if(owns(event.getEntity())){event.getDrops().clear();event.setDroppedExp(0);}}
    @EventHandler(priority=EventPriority.HIGHEST)
    public void equipment(PlayerArmorStandManipulateEvent event){if(owns(event.getRightClicked()))event.setCancelled(true);}
    /** Creative/spectator players may watch, but cannot attract attacks. */
    private boolean opponent(Run r,Player p){return p!=null&&inside(r,p)
        &&(p.getGameMode()==GameMode.SURVIVAL||p.getGameMode()==GameMode.ADVENTURE);}
    private List<Player> opponents(Run r){return r.center.getWorld().getPlayers().stream().filter(p->opponent(r,p)).toList();}
    private Player victim(Run r){
        if(r.duelist!=null&&r.act.duel()){
            Player duelist=Bukkit.getPlayer(r.duelist);if(opponent(r,duelist))return duelist;
        }
        Player current=r.victim==null?null:Bukkit.getPlayer(r.victim);
        // Re-evaluate between moves so a second player can pull aggro by closing in.
        if(opponent(r,current)&&(r.act.committed()||tick%40!=0))return current;
        Player nearest=null;double distance=Double.MAX_VALUE;
        for(Player p:opponents(r)){
            double d=p.getLocation().distanceSquared(r.position);
            if(d<distance){nearest=p;distance=d;}
        }
        r.victim=nearest==null?null:nearest.getUniqueId();return nearest;
    }
    private void face(Run r,Location target){
        double dx=target.getX()-r.position.getX(),dz=target.getZ()-r.position.getZ();
        r.position.setYaw(ArkhaiaSteering.facingYaw(r.position.getYaw(),dx,dz));
    }
    private boolean clear(Location at){
        var world=at.getWorld();
        // The wings are decorative. The body/hitbox needs four blocks of width
        // and nine of height; inspect each crossed block before moving it.
        for(int x=(int)Math.floor(at.getX()-2);x<=(int)Math.floor(at.getX()+2);x++)
            for(int z=(int)Math.floor(at.getZ()-2);z<=(int)Math.floor(at.getZ()+2);z++)
                for(int y=(int)Math.floor(at.getY()+.01);y<=(int)Math.floor(at.getY()+8.8);y++)
                    if(!world.getBlockAt(x,y,z).isPassable())return false;
        return true;
    }
    private boolean supported(Run r,Location at){
        return at.getWorld().getBlockAt(at.getBlockX(),r.center.getBlockY()-1,at.getBlockZ()).getType().isSolid();
    }
    private boolean reachable(Run r,Location next){
        return Math.hypot(next.getX()-r.center.getX(),next.getZ()-r.center.getZ())<=LEASH&&supported(r,next)&&clear(next);
    }
    /** Acceleration-limited locomotion; returns the horizontal distance actually travelled. */
    private double move(Run r,double wantX,double wantZ,double limit,boolean flying){
        double ax=wantX-r.vx,az=wantZ-r.vz,acceleration=Math.hypot(ax,az);
        if(acceleration>limit){ax*=limit/acceleration;az*=limit/acceleration;}
        r.vx+=ax;r.vz+=az;if(Math.hypot(r.vx,r.vz)<.0005){r.vx=0;r.vz=0;}
        double desiredY=r.center.getY()+(flying?.9+.10*Math.sin(tick*.09):0);
        r.verticalSpeed=Math.clamp(r.verticalSpeed+(desiredY-r.position.getY())*.025-r.verticalSpeed*.25,-.10,.10);
        if(Math.abs(desiredY-r.position.getY())<.005&&Math.abs(r.verticalSpeed)<.005)r.verticalSpeed=0;
        if(Math.abs(r.vx)+Math.abs(r.vz)+Math.abs(r.verticalSpeed)<.00001)return 0;
        // Sweep each short step, rather than teleporting to an unchecked endpoint.
        for(double angle:new double[]{0,Math.PI/6,-Math.PI/6,Math.PI/2}){
            double x=r.vx*Math.cos(angle)-r.vz*Math.sin(angle),z=r.vx*Math.sin(angle)+r.vz*Math.cos(angle);
            var next=r.position.clone().add(x,r.verticalSpeed,z);
            if(next.getY()<r.center.getY())next.setY(r.center.getY());
            if(!reachable(r,next))continue;
            double distance=Math.hypot(next.getX()-r.position.getX(),next.getZ()-r.position.getZ());
            r.position.setX(next.getX());r.position.setY(next.getY());r.position.setZ(next.getZ());return distance;
        }
        r.vx*=.35;r.vz*=.35;r.verticalSpeed=0;return 0;
    }
    private void sync(Run r){
        r.target.teleport(r.position);
        var visual=r.position.clone();
        if(r.bedrockNative){
            // Let the normal entity tracker send relative movement. A Bukkit
            // teleport every tick tells Bedrock to snap instead of interpolate.
            var handle=((org.bukkit.craftbukkit.entity.CraftEntity)r.bedrock).getHandle();
            handle.setPos(visual.getX(),visual.getY(),visual.getZ());
            r.bedrock.setRotation(visual.getYaw(),0);
        }else r.bedrock.teleport(visual);
    }
    private double scale(){double percent=plugin.getConfig().getDouble("arkhaia.combat.damage-percent",100);return Double.isFinite(percent)?Math.clamp(percent,0,1000)/100:1;}
    /** Every Arkhaia blow is armor-piercing encounter damage; Protection, Resistance and Totems still apply. */
    private boolean magic(Run r,Player p,double amount,boolean armor){
        if(r.stopped||!opponent(r,p)||!Double.isFinite(amount)||amount<=0)return false;
        r.applyingImpact=true;
        try{return BossDamage.hit(p,amount,DamageSource.builder(DamageType.INDIRECT_MAGIC).withCausingEntity(r.target).withDirectEntity(r.target).build(),armor);}
        finally{r.applyingImpact=false;}
    }
    private static boolean grounded(Run r,Player p,double height){return p.getY()-r.center.getY()<height;}
    private double swordDamage(Run r,Move move,Player p){
        int phase=r.sovereign.phase();double max=p.getAttribute(Attribute.MAX_HEALTH).getValue();
        double base=switch(move){
            case LIGHT -> 16+phase*4;
            case HEAVY -> (36+phase*8)*(r.blessings.contains(ArkhaiaWaves.Kind.STYX)?1.25:1);
            case SWEEP_LEFT,SWEEP_RIGHT -> max*.55;
            case EXECUTE -> max*.6;
            default -> 0;
        };
        return base*scale();
    }
    private int lock(Move move){return switch(move){case LIGHT->5;case HEAVY->7;case CHARGE->6;case SWEEP_LEFT,SWEEP_RIGHT->8;case EXECUTE->9;default->-1;};}
    private ArkhaiaPattern.Hazard shape(Run r,Move move){
        double x=r.position.getX(),z=r.position.getZ(),yaw=r.position.getYaw(),angle=ArkhaiaPattern.facing(yaw);
        return switch(move){
            case LIGHT -> new ArkhaiaPattern.Hazard(ArkhaiaPattern.Shape.SECTOR,x,z,angle,7.5,Math.toRadians(75),0,0);
            case HEAVY -> ArkhaiaPattern.forward(x,z,yaw,13,2.4,0,0);
            case EXECUTE -> ArkhaiaPattern.forward(x,z,yaw,15,3,0,0);
            case SWEEP_LEFT,SWEEP_RIGHT -> new ArkhaiaPattern.Hazard(ArkhaiaPattern.Shape.HALF,x,z,angle,12,move==Move.SWEEP_LEFT?1:-1,0,0);
            case CHARGE -> ArkhaiaPattern.forward(x,z,yaw,28,1.8,0,0);
            default -> null;
        };
    }
    private void schedule(Run r,List<ArkhaiaPattern.Hazard> hazards,long start,double damage,boolean lethal,boolean portal){
        for(var h:hazards)r.pending.add(new Pending("attack:pending:"+(r.serial++),h,start+h.tick(),damage,lethal,portal));
    }
    private ArkhaiaPattern.Hazard widened(ArkhaiaPattern.Hazard h,double factor){
        return new ArkhaiaPattern.Hazard(h.shape(),h.x(),h.z(),h.angle(),h.radius()*factor,h.halfWidth(),h.tick(),h.index());
    }
    /** First tick of a new state: plan its geometry once. */
    private void began(Run r,Player target){
        var act=r.act;int phase=r.sovereign.phase();
        if(act.state()==State.WINDUP){
            int windup=act.duration();
            Player aim=target==null?null:target;
            double tx=aim==null?r.position.getX():aim.getX(),tz=aim==null?r.position.getZ():aim.getZ();
            switch(act.move()){
                case RIFT -> {
                    var plan=ArkhaiaPattern.plan(ArkhaiaBrain.Action.RIFT,phase>1?2:1,r.position.getX(),r.position.getZ(),tx,tz,r.center.getX(),r.center.getZ(),r.position.getYaw());
                    if(r.blessings.contains(ArkhaiaWaves.Kind.MALEFEASANCE))plan=plan.stream().map(h->widened(h,1.35)).toList();
                    schedule(r,plan,tick+windup,(24+phase*4)*scale(),false,false);
                }
                case PORTALS -> schedule(r,ArkhaiaPattern.plan(ArkhaiaBrain.Action.PORTALS,2,r.position.getX(),r.position.getZ(),tx,tz,r.center.getX(),r.center.getZ(),r.position.getYaw()),
                    tick+windup,(26+phase*4)*scale(),false,true);
                case ECLIPSE -> {
                    schedule(r,ArkhaiaPattern.plan(ArkhaiaBrain.Action.ECLIPSE,2,r.position.getX(),r.position.getZ(),tx,tz,r.center.getX(),r.center.getZ(),r.position.getYaw()),
                        tick+windup,0,true,false);
                    title(r,"§4STYX ECLIPSE","§cออกจากเลนสีม่วงภายใน 6 วินาที — โดนคือตาย");
                    r.center.getWorld().playSound(r.center,Sound.ENTITY_WITHER_SPAWN,1.4f,.5f);
                }
                default -> {}
            }
            r.center.getWorld().playSound(r.position,act.sword()?Sound.BLOCK_ANVIL_PLACE:Sound.BLOCK_RESPAWN_ANCHOR_CHARGE,.9f,act.move()==Move.LIGHT?1.1f:.7f);
        }
        if(act.state()==State.ACTIVE&&act.move()==Move.CHARGE){
            double yaw=Math.toRadians(r.position.getYaw());r.chargeDirection=new Vector(-Math.sin(yaw),0,Math.cos(yaw));
            r.chargeHit.clear();r.lastTrail=r.position.clone();
            r.center.getWorld().playSound(r.position,Sound.ENTITY_RAVAGER_ROAR,1.3f,.7f);
        }
        if(act.state()==State.STAGGER||act.state()==State.BROKEN)r.center.getWorld().playSound(r.position,Sound.ENTITY_IRON_GOLEM_DAMAGE,1.2f,.5f);
        if(!act.committed()||act.state()==State.WINDUP)r.strike=null;
    }
    private void event(Run r,ArkhaiaSovereign.Event event,List<Player> opponents){
        switch(event){
            case PHASE_TWO -> {
                clearAttacks(r);
                title(r,"§5ARKHAIA · PHASE II","§dพิธีหกสายน้ำ — ทำลายวิญญาณก่อนมันถูกดูดกลืน");
                announce(r,"§dArkhaia Phase II: เกราะกุหลาบจะเปิดเมื่อวิญญาณที่โยงโซ่ถูกทำลายครบ · ถ้าไม่ทัน มันจะได้พลังของวิญญาณที่รอด");
            }
            case RITUAL,SECOND_RITUAL -> startRitual(r,opponents);
            case RITUAL_BROKEN -> {
                r.ritualKinds.clear();r.minions.clear();
                title(r,"§dพิธีล่ม!","§fArkhaia ทรุด 8 วินาที — โจมตี ×2");
                r.center.getWorld().playSound(r.position,Sound.BLOCK_GLASS_BREAK,2,.5f);
            }
            case RITUAL_FAILED -> absorb(r);
            case PHASE_THREE -> {
                clearAttacks(r);r.minions.clear();r.ritualKinds.clear();r.blessings.clear();r.nextBloom=tick+160;
                title(r,"§4ARKHAIA · PHASE III","§cสุริยคราสสติกซ์ — การดวลประหารเริ่มขึ้น");
                announce(r,"§cPhase III: Arkhaia จะท้าดวลทีละคน ดูเส้นบนพื้นแล้วหลบไปฝั่งที่ปลอดภัย · ปัดดาบท่าที่ 3 ได้ = ทรุดทันที · กุหลาบงอกตรงจุดที่ยืนนิ่ง");
            }
            case BROKEN -> {
                for(Player p:opponents){p.sendActionBar(net.kyori.adventure.text.Component.text("ARKHAIA เสียหลัก! โจมตี ×2",net.kyori.adventure.text.format.NamedTextColor.LIGHT_PURPLE));
                    p.playSound(p.getLocation(),Sound.BLOCK_ANVIL_LAND,.8f,.6f);}
                r.strike=null;r.duelist=null;
            }
            case WALL -> announce(r,"§eArkhaia พุ่งชนกำแพง! เปิดจุดอ่อน");
            case DUEL -> startDuel(r,opponents);
            case LAST_STAND -> title(r,"§4LAST STAND","§cArkhaia โจมตีถี่ขึ้น");
            case DEFEATED -> defeat(r);
            default -> {}
        }
    }
    private void clearAttacks(Run r){r.pending.clear();r.rings.clear();r.trails.clear();r.strike=null;r.duelist=null;r.bleeding.clear();}
    private void startRitual(Run r,List<Player> opponents){
        clearAttacks(r);r.minions.clear();r.ritualKinds.clear();r.ritualKilled.clear();
        int count=Math.clamp(opponents.size(),1,3);
        List<ArkhaiaWaves.Kind> order=new ArrayList<>(List.of(ArkhaiaWaves.Kind.values()));Collections.shuffle(order,r.random);
        order.sort(Comparator.comparing(r.lastRitual::contains));
        double offset=r.random.nextDouble(Math.PI*2);r.minions.initializeClock(tick);
        for(int i=0;i<count;i++){
            var kind=order.get(i);double a=offset+i*Math.PI*2/count;
            r.minions.summon(kind,r.center.clone().add(Math.cos(a)*16,0,Math.sin(a)*16));r.ritualKinds.add(kind);
        }
        r.lastRitual.clear();r.lastRitual.addAll(r.ritualKinds);
        title(r,"§5พิธีหกสายน้ำ","§dทำลายวิญญาณ "+count+" ตนภายใน 60 วินาที");
        r.center.getWorld().playSound(r.center,Sound.BLOCK_END_PORTAL_SPAWN,1.2f,.6f);
    }
    private static String blessing(ArkhaiaWaves.Kind kind){return switch(kind){
        case ACHERON -> "Acheron: ดาบทำให้เลือดไหล";
        case PHLEGETHON -> "Phlegethon: ทางพุ่งกลายเป็นไฟวิญญาณ";
        case COCYTUS -> "Cocytus: ดาบทำให้ช้าลง";
        case BANSHEE_WAIL -> "Banshee: สะท้อนดาเมจหลังท่าหนัก";
        case MALEFEASANCE -> "Malefeasance: วงเวทใต้เท้าใหญ่ขึ้น";
        case STYX -> "Styx: ท่าหนักแรงขึ้นและทำให้อ่อนแรง";
    };}
    private void absorb(Run r){
        var survivors=EnumSet.copyOf(r.ritualKinds.isEmpty()?EnumSet.noneOf(ArkhaiaWaves.Kind.class):r.ritualKinds);survivors.removeAll(r.ritualKilled);
        r.minions.clear();r.ritualKinds.clear();
        if(survivors.isEmpty())return;
        r.blessings.addAll(survivors);
        title(r,"§4Arkhaia ดูดกลืนวิญญาณ","§c"+survivors.size()+" สายน้ำเสริมพลังให้มัน");
        for(var kind:survivors)announce(r,"§c+ "+blessing(kind));
        r.center.getWorld().playSound(r.position,Sound.ENTITY_WITHER_AMBIENT,1.5f,.5f);
    }
    private void startDuel(Run r,List<Player> opponents){
        if(opponents.isEmpty())return;
        List<Player> order=new ArrayList<>(opponents);order.sort(Comparator.comparing(Player::getUniqueId));
        Player duelist=order.get(Math.floorMod(r.duelTurn++,order.size()));
        r.duelist=duelist.getUniqueId();r.duelCenter=duelist.getLocation().clone();r.duelCenter.setY(r.center.getY()+.12);
        // Step through the shadows to stand before the challenged player.
        Vector away=r.position.toVector().subtract(r.duelCenter.toVector()).setY(0);
        if(away.lengthSquared()<.01)away=new Vector(0,0,1);away.normalize().multiply(6.5);
        Location destination=r.duelCenter.clone().add(away);destination.setY(r.center.getY());
        if(reachable(r,destination)){
            r.center.getWorld().spawnParticle(Particle.REVERSE_PORTAL,r.position.clone().add(0,3,0),60,1.5,3,1.5,.05);
            r.position.setX(destination.getX());r.position.setZ(destination.getZ());r.vx=r.vz=0;
        }
        face(r,duelist.getLocation());face(r,duelist.getLocation());face(r,duelist.getLocation());
        duelist.sendTitle("§4ดวลประหาร","§cห้ามออกจากวงหนาม · ดูเส้นบนพื้นแล้วหลบ",6,50,12);
        announce(r,"§cArkhaia ท้าดวล "+duelist.getName()+" · คนอื่นฟันหลังได้เกจเสียหลัก ×2");
        r.center.getWorld().playSound(r.position,Sound.ENTITY_EVOKER_PREPARE_SUMMON,1.5f,.5f);
    }
    private void defeat(Run r){
        if(r.stopped)return;
        BossArmorCores.drop(plugin,BossArmorCores.Core.REVENANT,r.center);
        var addon=Bukkit.getPluginManager().getPlugin("advance-magic");
        if(addon instanceof com.example.advancemagic.AdvanceMagicPlugin magic&&magic.isEnabled())
            for(UUID uuid:r.contributors){Player recipient=Bukkit.getPlayer(uuid);if(recipient!=null&&opponent(r,recipient))magic.skillTree().grantPoints(recipient,1,"Arkhaia");}
        if(plugin.seraphTrader()!=null)for(UUID uuid:r.contributors){Player recipient=Bukkit.getPlayer(uuid);
            if(recipient!=null&&opponent(r,recipient))plugin.seraphTrader().feathers().grant(recipient,plugin.integer("seraph-trader.feathers.world-boss",15,0,64),"พิชิต Arkhaia",false);}
        // Persistent game-clock cooldown, like the sanctuaries: restarts and /time set cannot skip it.
        long respawn=plugin.integer("arkhaia.respawn-ticks",24000,0,1728000);
        for(var entry:active.entrySet())if(entry.getValue()==r)
            plugin.world().getPersistentDataContainer().set(readyKey(entry.getKey()),PersistentDataType.LONG,plugin.world().getGameTime()+respawn);
        title(r,"§dArkhaia พ่ายแพ้","§fราชันย์กุหลาบทมิฬคุกเข่า");
        active.values().remove(r);destroy(r);
    }
    private void parried(Run r,Player p,boolean shield){
        p.sendTitle("",shield?"§6PERFECT GUARD!":"§6PARRY!",2,20,6);
        p.playSound(p.getLocation(),Sound.ITEM_SHIELD_BLOCK,1,.6f);
        r.center.getWorld().playSound(r.position,Sound.BLOCK_ANVIL_LAND,1.2f,1.6f);
        r.center.getWorld().spawnParticle(Particle.END_ROD,r.position.clone().add(0,4,0),40,.8,1.2,.8,.15);
    }
    /** A shield counts as a guard only if it was raised within the last twelve ticks. */
    private void shields(Run r,List<Player> opponents){
        Set<UUID> now=new HashSet<>();
        for(Player p:opponents)if(p.isBlocking()){now.add(p.getUniqueId());if(!r.blocking.contains(p.getUniqueId()))r.raised.put(p.getUniqueId(),tick);}
        r.blocking.clear();r.blocking.addAll(now);
    }
    private boolean facingBoss(Run r,Player p){
        Vector look=p.getLocation().getDirection().setY(0),toward=r.position.toVector().subtract(p.getLocation().toVector()).setY(0);
        return look.lengthSquared()>.01&&toward.lengthSquared()>.01&&look.normalize().dot(toward.normalize())>.5;
    }
    private void impact(Run r){
        Move move=r.act.move();
        if(!r.act.sword()||r.strike==null)return;
        boolean heavy=move==Move.HEAVY||move==Move.EXECUTE;
        List<Player> struck=opponents(r).stream().filter(p->grounded(r,p,4.5)&&r.strike.contains(p.getX(),p.getZ())&&p.hasLineOfSight(r.target)).toList();
        if(heavy)for(Player p:struck)if(r.blocking.contains(p.getUniqueId())&&tick-r.raised.getOrDefault(p.getUniqueId(),Long.MIN_VALUE/2)<=12&&facingBoss(r,p)
                &&r.sovereign.parry(tick,true)){parried(r,p,true);r.effects.impact(r.position,false);return;}
        r.effects.impact(r.position.clone().add(new Vector(-Math.sin(Math.toRadians(r.position.getYaw())),0,Math.cos(Math.toRadians(r.position.getYaw()))).multiply(4)),heavy);
        for(Player p:struck){
            if(heavy&&r.blocking.contains(p.getUniqueId())){p.setCooldown(Material.SHIELD,100);p.sendActionBar(net.kyori.adventure.text.Component.text("การ์ดแตก — ยกโล่ช้าหรือนานเกินไป",net.kyori.adventure.text.format.NamedTextColor.RED));}
            if(!magic(r,p,swordDamage(r,move,p),true))continue;
            if(r.blessings.contains(ArkhaiaWaves.Kind.ACHERON))r.bleeding.put(p.getUniqueId(),tick+100);
            if(r.blessings.contains(ArkhaiaWaves.Kind.COCYTUS))p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,40,1));
            if(heavy&&r.blessings.contains(ArkhaiaWaves.Kind.STYX))p.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS,120,1));
            if(heavy){Vector push=p.getLocation().toVector().subtract(r.position.toVector()).setY(0);if(push.lengthSquared()>.01)p.setVelocity(push.normalize().multiply(.9).setY(.45));}
        }
        if(heavy&&r.blessings.contains(ArkhaiaWaves.Kind.BANSHEE_WAIL))r.reflectUntil=tick+60;
    }
    private void charge(Run r){
        if(r.act.state()!=State.ACTIVE||r.act.move()!=Move.CHARGE||r.chargeDirection==null)return;
        Location next=r.position.clone().add(r.chargeDirection.clone().multiply(1.25));next.setY(r.center.getY());
        if(!reachable(r,next)){
            r.sovereign.wall(tick);r.center.getWorld().playSound(r.position,Sound.ENTITY_GENERIC_EXPLODE,1.2f,.6f);
            r.center.getWorld().spawnParticle(Particle.EXPLOSION,r.position.clone().add(r.chargeDirection.clone().multiply(2)).add(0,2,0),4,1,1,1,0);return;
        }
        r.position.setX(next.getX());r.position.setZ(next.getZ());r.vx=r.vz=0;
        for(Player p:opponents(r))if(!r.chargeHit.contains(p.getUniqueId())&&grounded(r,p,3.5)
                &&Math.hypot(p.getX()-r.position.getX(),p.getZ()-r.position.getZ())<=2.6){
            r.chargeHit.add(p.getUniqueId());
            if(magic(r,p,36*scale(),true)){Vector side=p.getLocation().toVector().subtract(r.position.toVector()).setY(0);
                if(side.lengthSquared()<.01)side=r.chargeDirection.clone();p.setVelocity(side.normalize().multiply(1.1).setY(.6));}
        }
        if(r.lastTrail==null||r.lastTrail.distanceSquared(r.position)>=2.2*2.2){
            Location at=r.position.clone();at.setY(r.center.getY()+.1);r.trails.add(new Trail("attack:trail:"+(r.serial++),at,tick+100));r.lastTrail=r.position.clone();
        }
    }
    private void hazards(Run r){
        boolean fire=r.blessings.contains(ArkhaiaWaves.Kind.PHLEGETHON);
        r.trails.removeIf(t->tick>=t.until());
        for(Trail t:r.trails)for(Player p:opponents(r))if(grounded(r,p,1.5)&&Math.hypot(p.getX()-t.at().getX(),p.getZ()-t.at().getZ())<=1.8
                &&tick>=r.grazed.getOrDefault(p.getUniqueId(),0L)){
            r.grazed.put(p.getUniqueId(),tick+10);if(magic(r,p,(fire?12:8)*scale(),false)&&fire)p.setFireTicks(60);
        }
        if(r.act.state()==State.ACTIVE&&r.act.move()==Move.SPIN&&(r.act.age()==2||r.act.age()==16)){
            Location origin=r.position.clone();origin.setY(r.center.getY()+.25);r.rings.add(new Ring("attack:ring:"+(r.serial++),origin,tick));
            r.center.getWorld().playSound(r.position,Sound.ENTITY_PLAYER_ATTACK_SWEEP,1.4f,.5f);
        }
        for(var it=r.rings.iterator();it.hasNext();){Ring ring=it.next();double radius=1.5+(tick-ring.start)*.9;
            if(radius>36){it.remove();continue;}
            for(Player p:opponents(r))if(!ring.hit.contains(p.getUniqueId())&&grounded(r,p,.6)
                    &&Math.abs(Math.hypot(p.getX()-ring.origin.getX(),p.getZ()-ring.origin.getZ())-radius)<=.9){
                ring.hit.add(p.getUniqueId());magic(r,p,(26+r.sovereign.phase()*4)*scale(),true);
            }
        }
        for(var it=r.pending.iterator();it.hasNext();){Pending h=it.next();
            if(tick>h.fire()+8){it.remove();continue;}
            if(tick!=h.fire())continue;
            r.effects.pulse(new Location(r.center.getWorld(),h.hazard().x(),r.center.getY()+.5,h.hazard().z()),h.portal());
            for(Player p:opponents(r))if(grounded(r,p,3)&&h.hazard().contains(p.getX(),p.getZ()))
                magic(r,p,h.lethal()?Math.max(100,p.getAttribute(Attribute.MAX_HEALTH).getValue()*20):h.damage(),true);
        }
        if(r.sovereign.phase()==3&&tick>=r.nextBloom&&r.sovereign.state()!=State.TRANSITION){
            r.nextBloom=tick+(r.sovereign.lastStand()?70:100);
            // Thorns sprout where each player stands: keep moving.
            for(Player p:opponents(r))r.pending.add(new Pending("attack:bloom:"+(r.serial++),
                new ArkhaiaPattern.Hazard(ArkhaiaPattern.Shape.CIRCLE,p.getX(),p.getZ(),0,2.6,0,0,0),tick+30,24*scale(),false,false));
        }
        for(var it=r.bleeding.entrySet().iterator();it.hasNext();){var entry=it.next();Player p=Bukkit.getPlayer(entry.getKey());
            if(p==null||tick>=entry.getValue()||!opponent(r,p)){it.remove();continue;}
            if((entry.getValue()-tick)%20==0)magic(r,p,2,false);
        }
        if(r.duelist!=null&&r.act.duel()){
            Player duelist=Bukkit.getPlayer(r.duelist);
            if(opponent(r,duelist)&&Math.hypot(duelist.getX()-r.duelCenter.getX(),duelist.getZ()-r.duelCenter.getZ())>DUEL_RADIUS&&tick%10==0){
                magic(r,duelist,6*scale(),false);duelist.sendActionBar(net.kyori.adventure.text.Component.text("หนามกัดกิน — กลับเข้าวงดวล",net.kyori.adventure.text.format.NamedTextColor.RED));
            }
        }else if(r.duelist!=null)r.duelist=null;
    }
    private void ritual(Run r,List<Player> opponents){
        if(r.sovereign.state()!=State.RITUAL)return;
        r.minions.tick(tick);
        int age=r.act.age();
        if(age%140==40)schedule(r,ArkhaiaPattern.plan(ArkhaiaBrain.Action.NOVA,2,r.position.getX(),r.position.getZ(),0,0,r.center.getX(),r.center.getZ(),0)
            .stream().map(h->new ArkhaiaPattern.Hazard(h.shape(),h.x(),h.z(),h.angle(),h.radius(),h.halfWidth(),24+h.index()*14,h.index())).toList(),tick,26*scale(),false,false);
        if(age%140==110&&!opponents.isEmpty()){Player aim=opponents.get(r.random.nextInt(opponents.size()));
            schedule(r,ArkhaiaPattern.plan(ArkhaiaBrain.Action.RIFT,2,r.position.getX(),r.position.getZ(),aim.getX(),aim.getZ(),r.center.getX(),r.center.getZ(),r.position.getYaw())
                .stream().map(h->new ArkhaiaPattern.Hazard(h.shape(),h.x(),h.z(),h.angle(),h.radius(),h.halfWidth(),26+h.tick(),h.index())).toList(),tick,26*scale(),false,false);}
        if(!r.ritualKinds.isEmpty()&&r.minions.alive()==0)r.sovereign.endRitual(true,tick);
        if(age%200==0&&age>0&&r.sovereign.state()==State.RITUAL)announce(r,"§dพิธีหกสายน้ำ · เหลือ "+(ArkhaiaSovereign.RITUAL_LIMIT-age)/20+" วินาที · วิญญาณเหลือ "+r.minions.alive());
    }
    private void regroup(Run r){
        r.regrouped=true;r.sovereign.regroup(tick);clearAttacks(r);r.minions.clear();r.ritualKinds.clear();r.effects.clear();
        r.act=new ArkhaiaSovereign.Act(State.NEUTRAL,Move.NONE,0,0,false,0,0);
        plugin.getLogger().info("Arkhaia regrouped at "+r.center+": party wiped; phase "+r.sovereign.phase()+" restarts at "+(int)r.sovereign.health());
    }
    /** Clip, time and rate shared by the Java rig, the Bedrock rig, the aura and the hitbox. */
    private void pose(Run r,double speed,boolean airborne){
        var act=r.act;String clip;double time,rate=1;
        switch(act.state()){
            case WINDUP,ACTIVE,RECOVER -> {
                String base=switch(act.move()){case LIGHT,SWEEP_LEFT,SWEEP_RIGHT->"light";case HEAVY,EXECUTE,CHARGE->"heavy";case RIFT->"rift";case SPIN->"nova";case PORTALS->"portals";case ECLIPSE->"eclipse";default->"light";};
                clip=base+"_"+act.state().name().toLowerCase(Locale.ROOT);double length=CLIP_LENGTH.getOrDefault(clip,1.0);
                rate=act.duration()<=0?1:Math.clamp(length*20/act.duration(),.15,4);time=Math.min(length,act.age()*rate/20);
            }
            case STAGGER -> {clip="stun";time=Math.min(1.8,act.age()/20.0);}
            case BROKEN -> {clip="stun";rate=.3;time=Math.min(1.8,act.age()*rate/20);}
            case TRANSITION -> {clip="transition";time=Math.min(3.5,act.age()/20.0);}
            default -> {
                clip=airborne||act.state()==State.RITUAL?"hover":speed>.008?"walk":"idle";
                rate=clip.equals("walk")?Math.clamp(speed/.16,.35,2):1;
                time=clip.equals(r.bodyPose)?r.bodyTime+rate/20:0;
                double length=CLIP_LENGTH.get(clip);time%=length;
            }
        }
        r.bodyPose=clip;r.bodyTime=time;r.bodyRate=rate;
    }
    private void render(Run r,List<Player> opponents){
        if(!r.effects.begin(r.position,tick)){return;}
        double floor=r.center.getY();var act=r.act;
        double flash=0;
        if(act.state()==State.WINDUP){int window=ArkhaiaSovereign.flash(act.move(),r.sovereign.phase());
            if(window>0&&act.age()>=act.duration()-window)flash=(act.age()-(act.duration()-window))/(double)window;}
        r.effects.body(r.position,floor,tick,r.sovereign.broken(),flash);
        if(act.state()==State.TRANSITION)r.effects.transition(r.position,floor,act.progress(),tick);
        if(act.committed()&&(act.move()==Move.RIFT||act.move()==Move.PORTALS||act.move()==Move.ECLIPSE)&&act.state()!=State.RECOVER)
            r.effects.casting(r.position,floor,act.move()==Move.ECLIPSE,tick);
        if(r.strike!=null&&(act.state()==State.WINDUP||act.state()==State.ACTIVE))
            r.effects.hazard("attack:strike",r.strike,floor,act.state()==State.ACTIVE,false,tick);
        for(Pending h:r.pending)r.effects.hazard(h.key(),h.hazard(),floor,tick>=h.fire(),h.portal(),tick);
        for(Trail t:r.trails)r.effects.thorns(t.key(),t.at(),1.8,tick);
        for(Ring ring:r.rings)r.effects.shockwave(ring.key,ring.origin,1.5+(tick-ring.start)*.9,tick);
        if(r.sovereign.state()==State.RITUAL){
            r.effects.barrier(r.position,floor,tick);
            for(Entity e:r.center.getWorld().getNearbyEntities(r.center,46,14,46,e->e instanceof Mob&&!(e instanceof Vex)&&r.minions.owns(e)))
                r.effects.tether(r.position.clone().add(0,4.5,0),((Mob)e).getEyeLocation(),ArkhaiaEffects.CRIMSON);
        }
        if(r.duelist!=null&&r.duelCenter!=null){
            r.effects.thornRing(r.duelCenter,DUEL_RADIUS,tick);
            Player duelist=Bukkit.getPlayer(r.duelist);if(duelist!=null)r.effects.crown("attack:duel-crown",duelist.getLocation().add(0,2.6,0),tick);
        }
        if(tick<r.reflectUntil)r.effects.crown("fx:reflect",r.position.clone().add(0,9.4,0),tick);
        r.effects.end();
    }
    private void combat(Run r){
        List<Player> opponents=opponents(r);
        for(Player p:opponents)if(r.roster.size()<16&&r.roster.add(p.getUniqueId()))r.sovereign.roster(r.roster.size());
        // A wiped party restarts the phase after ten empty seconds; the arena closes after the return grace.
        if(opponents.isEmpty()){if(r.emptySince<0)r.emptySince=tick;if(!r.regrouped&&tick-r.emptySince>=200)regroup(r);}
        else{r.emptySince=-1;r.regrouped=false;}
        shields(r,opponents);
        Player target=victim(r);
        double dx=target==null?0:target.getX()-r.position.getX(),dz=target==null?0:target.getZ()-r.position.getZ(),distance=Math.hypot(dx,dz);
        var previous=r.act;
        r.act=r.sovereign.update(tick,new ArkhaiaSovereign.Sense(target!=null,distance,target!=null&&target.hasLineOfSight(r.target)));
        for(var event:r.sovereign.drain()){event(r,event,opponents);if(r.stopped)return;}
        if(r.act.state()!=previous.state()||r.act.move()!=previous.move()||r.act.index()!=previous.index())began(r,target);
        var act=r.act;
        boolean tracking=switch(act.state()){
            case NEUTRAL -> true;
            case WINDUP -> lock(act.move())<0?act.age()<6:act.age()<act.duration()-lock(act.move());
            default -> false;
        };
        if(tracking&&target!=null)face(r,target.getLocation());
        if((act.state()==State.WINDUP||act.state()==State.ACTIVE)&&(act.sword()||act.move()==Move.CHARGE)&&(tracking||r.strike==null))r.strike=shape(r,act.move());
        if(act.state()==State.WINDUP&&ArkhaiaSovereign.flash(act.move(),r.sovereign.phase())>0&&act.age()==act.duration()-ArkhaiaSovereign.flash(act.move(),r.sovereign.phase()))
            r.center.getWorld().playSound(r.position,Sound.BLOCK_BELL_USE,1.6f,1.4f);
        // Locomotion: a duelist closes the distance, never orbits at spell range.
        double want=0,lunge=0,yaw=Math.toRadians(r.position.getYaw());boolean flying=false;
        double wx=0,wz=0,nx=distance>.01?dx/distance:0,nz=distance>.01?dz/distance:0;
        switch(act.state()){
            case NEUTRAL -> {
                if(target!=null){want=distance>5.5?(r.sovereign.phase()==3?.23:.2):distance<3?-.08:0;wx=nx*want;wz=nz*want;}
            }
            case WINDUP -> {if(act.sword()&&target!=null&&distance>6.5){wx=nx*.05;wz=nz*.05;}}
            case ACTIVE -> {
                if(act.move()==Move.LIGHT||act.move()==Move.HEAVY||act.move()==Move.EXECUTE)
                    lunge=(act.move()==Move.LIGHT?.15:.24)*Math.sin(Math.PI*act.progress());
                wx=-Math.sin(yaw)*lunge;wz=Math.cos(yaw)*lunge;
            }
            case RITUAL -> {double cx=r.center.getX()-r.position.getX(),cz=r.center.getZ()-r.position.getZ(),c=Math.hypot(cx,cz);
                if(c>.4){wx=cx/c*Math.min(.3,c);wz=cz/c*Math.min(.3,c);}flying=true;}
            default -> {}
        }
        if(act.committed()&&(act.move()==Move.ECLIPSE||act.move()==Move.PORTALS)&&act.state()!=State.RECOVER)flying=true;
        double speed=act.state()==State.ACTIVE&&act.move()==Move.CHARGE?1.25:move(r,wx,wz,act.state()==State.ACTIVE?.08:.04,flying);
        charge(r);
        ritual(r,opponents);
        if(r.stopped)return;
        boolean airborne=r.position.getY()>r.center.getY()+.35;
        pose(r,speed,airborne);
        r.gaze=r.look.update(r.position.getYaw(),target==null?0:target.getX()-r.position.getX(),
            target==null?0:target.getEyeLocation().getY()-r.position.getY()-EYE_HEIGHT,
            target==null?0:target.getZ()-r.position.getZ(),target!=null,
            act.state()==State.STAGGER||act.state()==State.BROKEN||act.state()==State.TRANSITION||act.state()==State.RECOVER);
        if(r.rig==null&&tick>=r.nextRigAttempt&&plugin.arkhaiaModels()!=null&&plugin.arkhaiaModels().ready("arkhaia")){
            r.nextRigAttempt=tick+200;
            try{r.rig=plugin.arkhaiaModels().attach(r.target);r.viewers.clear();}
            catch(RuntimeException|LinkageError error){plugin.getLogger().log(java.util.logging.Level.WARNING,"Cannot attach Arkhaia rig",error);}
        }
        sync(r);
        if(r.rig!=null){
            r.rig.look(r.gaze.yaw(),r.gaze.pitch());
            r.rig.clip(r.bodyPose,r.bodyRate,r.position);
            r.effects.swordTrail(act.state()==State.ACTIVE&&act.sword(),r.rig.swordTip(),tick);
        }
        r.aura.frame(r.position,r.bodyPose,r.bodyTime,tick,3.2,act.state()==State.ACTIVE||act.state()==State.TRANSITION||r.sovereign.phase()>1);
        if(r.bedrockNative)plugin.arkhaiaBedrock().riverFrame(r.bedrock.getUniqueId(),r.bodyPose,(float)r.bodyTime,(float)r.bodyRate,r.gaze,
            r.center.getWorld().getPlayers().stream().filter(p->p.getLocation().distanceSquared(r.position)<80*80).toList());
        if(act.impact())impact(r);
        if(!r.stopped)hazards(r);
        if(!r.stopped)render(r,opponents);
    }
    private void updateBar(Run r){
        var s=r.sovereign;r.bar.setProgress(Math.clamp(s.health()/s.maxHealth(),0,1));
        r.bar.setColor(s.phase()==1?BarColor.PURPLE:s.phase()==2?BarColor.PINK:BarColor.RED);
        String state=switch(s.state()){case BROKEN->" · §dเสียหลัก ×2";case RITUAL->" · §5เกราะกุหลาบ";case TRANSITION->" · เปลี่ยนเฟส";default->s.lastStand()?" · §4LAST STAND":"";};
        r.bar.setTitle("Arkhaia | Phase "+s.phase()+" | "+(int)Math.ceil(s.health())+" / "+(int)s.maxHealth()+state
            +(r.blessings.isEmpty()?"":" · สายน้ำ "+r.blessings.size()));
        r.stance.setProgress(Math.clamp(s.posture()/100,0,1));
        r.stance.setColor(s.broken()?BarColor.PINK:BarColor.YELLOW);
        r.stance.setTitle(s.broken()?"เสียหลัก! โจมตี ×2":"เกจเสียหลัก · ฟันหลัง / ปัดดาบ เติมเร็ว");
    }
    private void update(Run r){
        updateBar(r);Set<UUID> present=new HashSet<>(),rigViewers=new HashSet<>();
        r.target.getCollidableExemptions().clear();
        for(Player p:r.center.getWorld().getPlayers()){
            r.target.getCollidableExemptions().add(p.getUniqueId());
            present.add(p.getUniqueId());
            boolean nearby=inside(r,p);
            if(nearby){r.lastPresent=tick;r.bar.addPlayer(p);r.stance.addPlayer(p);}else{r.bar.removePlayer(p);r.stance.removePlayer(p);}
            boolean bedrock=JudgePackService.bedrock(p),accepted=plugin.judgePack().applied(p);
            if(!bedrock&&accepted&&r.rig!=null)rigViewers.add(p.getUniqueId());
            String kind=bedrock?"bedrock":accepted?(r.rig==null?"java":"rig"):"none";
            if(kind.equals(r.viewers.put(p.getUniqueId(),kind)))continue;
            if(bedrock)p.showEntity(plugin,r.bedrock);else p.hideEntity(plugin,r.bedrock);
        }
        r.viewers.keySet().retainAll(present);
        if(r.rig!=null)r.rig.viewers(rigViewers);
        for(Player p:new ArrayList<>(r.bar.getPlayers()))if(!inside(r,p)){r.bar.removePlayer(p);r.stance.removePlayer(p);}
    }
    void tick(){
        if(closed)return;tick++;
        for(var it=demos.entrySet().iterator();it.hasNext();){var entry=it.next();Player p=Bukkit.getPlayer(entry.getKey());Demo d=entry.getValue();
            if(p==null||!p.isOnline()||p.getWorld()!=d.center.getWorld()||p.getLocation().distanceSquared(d.center)>64*64||tick>=d.until||d.minions.alive()==0){d.minions.close();it.remove();}
            else try{d.minions.tick(tick);}catch(Exception error){d.minions.close();it.remove();plugin.getLogger().log(java.util.logging.Level.WARNING,"River test stopped",error);}
        }
        long grace=20L*plugin.integer("arkhaia.combat.return-grace-seconds",180,30,3600);
        for(var r:new ArrayList<>(active.values())){
            if(r.stopped)continue;
            // Refresh presence before expiring. Death/respawn/reconnect gets the return grace.
            if(r.center.getWorld().getPlayers().stream().anyMatch(p->inside(r,p)))r.lastPresent=tick;
            if(tick-r.lastPresent>=grace){plugin.getLogger().info("Arkhaia closed: arena empty for "+grace/20+" seconds at "+r.center);active.values().remove(r);destroy(r);continue;}
            try{if(r.parts.stream().anyMatch(e->!e.isValid()))recoverParts(r);combat(r);if(!r.stopped&&tick%10==0)update(r);}
            catch(Exception error){active.values().remove(r);destroy(r);plugin.getLogger().log(java.util.logging.Level.WARNING,"Arkhaia encounter stopped safely",error);}
        }
    }
    private void destroy(Run r){if(r.stopped)return;r.stopped=true;if(r.minions!=null)r.minions.close();
        for(Chunk chunk:r.chunks){Integer count=tickets.get(chunk);if(count!=null&&count==1){tickets.remove(chunk);chunk.removePluginChunkTicket(plugin);}else if(count!=null)tickets.put(chunk,count-1);}r.chunks.clear();if(r.rig!=null)r.rig.close();if(r.bedrock!=null&&plugin.arkhaiaBedrock()!=null)plugin.arkhaiaBedrock().unregister(r.bedrock.getUniqueId());r.effects.close();r.aura.close();r.bar.removeAll();r.stance.removeAll();r.parts.forEach(Entity::remove);r.viewers.clear();r.hits.clear();}
    @Override public void close(){closed=true;driver.cancel();active.values().forEach(this::destroy);active.clear();demos.values().forEach(d->d.minions.close());demos.clear();}
}
