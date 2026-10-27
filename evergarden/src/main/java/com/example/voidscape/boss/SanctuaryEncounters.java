package com.example.voidscape.boss;

import com.example.voidscape.VoidscapePlugin;
import com.example.voidscape.world.*;
import com.example.voidscape.world.OverworldSanctuaries.Site;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.event.world.*;
import org.bukkit.persistence.PersistentDataType;
import java.io.*;
import java.util.*;

/** Loaded sanctuaries own one waiting sculpture, then an attack-initiated encounter. */
public final class SanctuaryEncounters implements Listener,AutoCloseable {
    private static final long RESPAWN_TICKS=24000,EMPTY_RESET_TICKS=300;
    private static final class Run {
        final SanctuaryArena arena;ArkhaiaMinions minions;boolean fighting,dead,warnedEmpty;long emptySince=-1,readyAt;
        final Set<Chunk> tickets=new HashSet<>();
        Run(SanctuaryArena a){arena=a;}
    }
    private final VoidscapePlugin plugin;
    private final Map<String,Run> runs=new LinkedHashMap<>();
    private final File file;private final YamlConfiguration ledger;
    private final org.bukkit.scheduler.BukkitTask task;
    private final Map<UUID,Integer> interactions=new HashMap<>();
    private long tick;private boolean closed;
    public SanctuaryEncounters(VoidscapePlugin plugin){
        this.plugin=plugin;file=new File(plugin.getDataFolder(),"sanctuary-bosses.yml");ledger=YamlConfiguration.loadConfiguration(file);
        for(World w:Bukkit.getWorlds())for(Entity e:w.getEntities())if(e.getPersistentDataContainer().has(plugin.key("sanctuary_boss")))e.remove();
        Bukkit.getPluginManager().registerEvents(this,plugin);
        task=Bukkit.getScheduler().runTaskTimer(plugin,this::tick,1,1);
        Bukkit.getScheduler().runTaskLater(plugin,()->{for(World w:Bukkit.getWorlds())for(Chunk c:w.getLoadedChunks())discover(c);},40);
    }
    private String id(SanctuaryArena a){return a.world().getUID()+"_"+a.site().x()+"_"+a.site().z();}
    private boolean playable(Player p){return p.isOnline()&&!p.isDead()&&(p.getGameMode()==GameMode.SURVIVAL||p.getGameMode()==GameMode.ADVENTURE);}
    private boolean inside(Run r,Player p){return playable(p)&&r.arena.contains(p.getLocation());}
    private List<Player> players(Run r){return r.arena.world().getPlayers().stream().filter(p->inside(r,p)).toList();}
    private boolean enabled(){return !closed&&plugin.getConfig().getBoolean("overworld-sanctuaries.bosses.enabled",true);}
    private void discover(Chunk chunk){
        if(!enabled()||!chunk.isLoaded())return;
        var service=plugin.overworldSanctuaries();var placement=service.placement(chunk.getWorld());if(placement==null)return;
        Site site=placement.cell(Math.floorDiv(chunk.getX(),placement.spacing),Math.floorDiv(chunk.getZ(),placement.spacing));if(site==null)return;
        var arena=new SanctuaryArena(site,chunk.getWorld());Location c=arena.center();
        if(!c.getWorld().isChunkLoaded(c.getBlockX()>>4,c.getBlockZ()>>4)||service.upgrading(c.getChunk())||runs.containsKey(id(arena)))return;
        if(!clearSpawn(arena))return; // Never overwrite blocks, or load a neighbour just to show an idle boss.
        Run r=new Run(arena);String path="game-cooldowns."+id(arena);r.readyAt=ledger.getLong(path,0);
        if(!ledger.contains(path)&&ledger.contains("cooldowns."+id(arena))){
            long old=ledger.getLong("cooldowns."+id(arena));long elapsed=Math.max(0,System.currentTimeMillis()-(old-60000));
            r.readyAt=arena.world().getGameTime()+Math.max(0,RESPAWN_TICKS-elapsed/50);
            ledger.set(path,r.readyAt);ledger.set("cooldowns."+id(arena),null);save();
        }
        runs.put(id(arena),r);spawn(r);
    }
    private boolean clearSpawn(SanctuaryArena arena){
        Location c=arena.center();World w=arena.world();
        for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++){
            int bx=c.getBlockX()+x,bz=c.getBlockZ()+z;
            if(!w.isChunkLoaded(bx>>4,bz>>4)||!w.getBlockAt(bx,c.getBlockY()-1,bz).getType().isSolid())return false;
            for(int y=0;y<13;y++)if(!w.getBlockAt(bx,c.getBlockY()+y,bz).isPassable())return false;
        }
        return true;
    }
    private void spawn(Run r){
        r.dead=false;r.fighting=false;r.emptySince=-1;r.warnedEmpty=false;
        r.minions=new ArkhaiaMinions(plugin,r.arena.center(),p->inside(r,p),()->null,now->{r.dead=true;r.readyAt=r.arena.world().getGameTime()+RESPAWN_TICKS;
            ledger.set("game-cooldowns."+id(r.arena),r.readyAt);ledger.set("cooldowns."+id(r.arena),null);save();});
        r.minions.onDefeat(defeat->BossRewards.victory(plugin,defeat,r.arena.center(),p->inside(r,p)));
        // A closed, empty controller keeps event routing harmless while no boss exists.
        if(r.arena.world().getGameTime()<r.readyAt){r.dead=true;r.minions.close();return;}
        r.minions.initializeClock(tick);r.minions.waiting();
        String kind=r.arena.site().kind().id;
        r.minions.summon(ArkhaiaWaves.Kind.valueOf(kind.equals("banshee")?"BANSHEE_WAIL":kind.toUpperCase(Locale.ROOT)),r.arena.center());
        for(Entity e:r.arena.world().getNearbyEntities(r.arena.center(),8,15,8))if(r.minions.owns(e))e.getPersistentDataContainer().set(plugin.key("sanctuary_boss"),PersistentDataType.BYTE,(byte)1);
    }
    private void save(){try{ledger.save(file);}catch(IOException e){plugin.getLogger().log(java.util.logging.Level.SEVERE,"Cannot save sanctuary cooldowns",e);}}
    private void hold(Run r){
        Location c=r.arena.center();for(int x=(c.getBlockX()-38)>>4;x<=(c.getBlockX()+38)>>4;x++)for(int z=(c.getBlockZ()-38)>>4;z<=(c.getBlockZ()+38)>>4;z++){
            Chunk chunk=c.getWorld().getChunkAt(x,z);chunk.addPluginChunkTicket(plugin);r.tickets.add(chunk);
        }
    }
    private void release(Run r){for(Chunk c:r.tickets)c.removePluginChunkTicket(plugin);r.tickets.clear();}
    private void reset(Run r){r.minions.close();release(r);spawn(r);}
    private void tick(){
        if(closed)return;tick++;
        for(Run r:new ArrayList<>(runs.values())){
            if(!enabled()){r.minions.close();release(r);runs.remove(id(r.arena));continue;}
            if(r.dead){
                r.minions.close();release(r);r.fighting=false;
                if(r.arena.world().getGameTime()>=r.readyAt&&clearSpawn(r.arena))reset(r);
                else if(tick%40==0)for(Player p:r.arena.world().getPlayers())if(p.getLocation().distanceSquared(r.arena.center())<45*45)
                    p.sendActionBar(net.kyori.adventure.text.Component.text("Sanctuary resting: "+Math.max(0,(r.readyAt-r.arena.world().getGameTime()+19)/20)+"s"));
                continue;
            }
            if(r.fighting){
                List<Player> present=players(r);
                if(present.isEmpty()){
                    if(r.emptySince<0){r.emptySince=tick;r.warnedEmpty=false;}
                    long elapsed=tick-r.emptySince;
                    if(elapsed>=EMPTY_RESET_TICKS){
                        for(Player p:r.arena.world().getPlayers())if(p.getLocation().distanceSquared(r.arena.center())<64*64){
                            plugin.message(p,"§cบอสกลับสู่สถานะรอเนื่องจากไม่มีผู้เล่นอยู่ในลานครบ 15 วินาที");
                            p.sendActionBar(net.kyori.adventure.text.Component.text("§cบอสรีเซ็ตแล้ว — โจมตีในลานเพื่อเริ่มใหม่"));
                        }
                        reset(r);continue;
                    }
                    boolean leftHorizontally=r.arena.world().getPlayers().stream()
                        .anyMatch(p->playable(p)&&p.getLocation().distanceSquared(r.arena.center())<64*64
                            &&Math.hypot(p.getX()-r.arena.center().getX(),p.getZ()-r.arena.center().getZ())>SanctuaryArena.RADIUS);
                    if(leftHorizontally||elapsed>=15){
                        long rem=Math.max(1,(EMPTY_RESET_TICKS-elapsed+19)/20);
                        if(!r.warnedEmpty){
                            r.warnedEmpty=true;
                            for(Player p:r.arena.world().getPlayers())if(p.getLocation().distanceSquared(r.arena.center())<64*64)
                                plugin.message(p,"§c⚠ ออกนอกเขตต่อสู้! กลับเข้าลานภายใน "+rem+" วินาที มิฉะนั้นบอสจะรีเซ็ต");
                        }
                        if(elapsed%10==0)for(Player p:r.arena.world().getPlayers())if(p.getLocation().distanceSquared(r.arena.center())<64*64)
                            p.sendActionBar(net.kyori.adventure.text.Component.text("§c⚠ ออกนอกเขตบอส! กลับเข้าลานภายใน §e"+rem+" วินาที §cก่อนบอสรีเซ็ต"));
                        if(elapsed%20==0)for(Player p:r.arena.world().getPlayers())if(p.getLocation().distanceSquared(r.arena.center())<64*64)
                            p.playSound(p.getLocation(),Sound.BLOCK_NOTE_BLOCK_PLING,.8f,rem<=5?1.5f:.9f);
                    }
                }else if(r.emptySince>=0){
                    boolean wasWarned=r.warnedEmpty;
                    r.emptySince=-1;r.warnedEmpty=false;
                    if(wasWarned)for(Player p:present)p.sendActionBar(net.kyori.adventure.text.Component.text("§aกลับเข้าสู่ลานประลองแล้ว — การต่อสู้ดำเนินต่อ!"));
                }
            }
            r.minions.tick(tick);
            if(tick%10==0&&r.fighting)border(r);
            if(tick%20==0&&!r.fighting&&r.arena.world().getPlayers().stream().anyMatch(p->p.getLocation().distanceSquared(r.arena.center())<45*45))border(r);
            if(tick%40==0&&!r.fighting){for(Player p:r.arena.world().getPlayers())if(p.getLocation().distanceSquared(r.arena.center())<45*45)
                p.sendActionBar(net.kyori.adventure.text.Component.text("Strike the boss inside the marked arena to begin"));}
        }
        // Also retries a centre whose neighbour was initially unloaded or still upgrading.
        if(tick%100==0&&enabled())for(Player p:Bukkit.getOnlinePlayers())if(plugin.overworldSanctuaries().placement(p.getWorld())!=null)discover(p.getLocation().getChunk());
    }
    private void border(Run r){Location c=r.arena.center();for(int n=0;n<96;n++){double a=n*Math.PI/48;
        c.getWorld().spawnParticle(Particle.DUST,c.clone().add(Math.cos(a)*SanctuaryArena.RADIUS,.15,Math.sin(a)*SanctuaryArena.RADIUS),1,0,0,0,0,new Particle.DustOptions(Color.fromRGB(255,195,85),1.2f),true);}}
    private Player attacker(EntityDamageEvent event){
        if(event.getDamageSource().getCausingEntity() instanceof Player p)return p;
        if(event instanceof EntityDamageByEntityEvent by){if(by.getDamager() instanceof Player p)return p;if(by.getDamager() instanceof Projectile shot&&shot.getShooter() instanceof Player p)return p;}
        return null;
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void attack(EntityDamageByEntityEvent event){for(Run r:new ArrayList<>(runs.values()))if(r.minions.outgoing(event))return;}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void damage(EntityDamageEvent event){
        for(Run r:new ArrayList<>(runs.values()))if(r.minions.owns(event.getEntity())){
            Player p=attacker(event);
            if(p==null||!inside(r,p)||!Double.isFinite(event.getFinalDamage())||event.getFinalDamage()<=0){event.setCancelled(true);return;}
            if(r.fighting&&r.emptySince>=0){r.emptySince=-1;r.warnedEmpty=false;}
            if(event instanceof EntityDamageByEntityEvent by&&by.getDamager() instanceof Projectile shot){
                String origin=shot.getPersistentDataContainer().get(plugin.key("sanctuary_shot"),PersistentDataType.STRING);
                if(!id(r.arena).equals(origin)){event.setCancelled(true);return;}
            }
            if(!r.fighting){
                if(!r.arena.containsActivation(p.getLocation())){event.setCancelled(true);return;}
                if(r.arena.world().getGameTime()<r.readyAt){event.setCancelled(true);return;}
                // Validate real authored body reach before allowing a fake swing to wake a boss.
                if(event instanceof EntityDamageByEntityEvent by&&by.getDamager()==p&&!BossHitbox.confirmed(p,event.getEntity())
                        &&r.minions.meleeAim(p,BossHitbox.reach(p),tick)==null){event.setCancelled(true);return;}
                r.fighting=true;r.minions.awaken();hold(r);border(r);
                for(Player player:players(r))plugin.message(player,"วิหารตื่นขึ้นแล้ว! หากทุกคนออกนอกลานเกิน 15 วินาที บอสจะรีเซ็ตกลับสู่สถานะรอ");
            }
            r.minions.damage(event,p);return;
        }
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void launch(ProjectileLaunchEvent event){if(event.getEntity().getShooter() instanceof Player p)for(Run r:runs.values())if(inside(r,p)){
        event.getEntity().getPersistentDataContainer().set(plugin.key("sanctuary_shot"),PersistentDataType.STRING,id(r.arena));break;}}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void projectile(ProjectileHitEvent e){for(Run r:runs.values())if(r.minions.projectileHit(e))return;}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void target(EntityTargetLivingEntityEvent e){for(Run r:runs.values())if(r.minions.owns(e.getEntity())&&(!r.fighting||!(e.getTarget() instanceof Player p)||!inside(r,p))){e.setCancelled(true);return;}}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void heal(EntityRegainHealthEvent e){for(Run r:runs.values())if(r.minions.healing(e))return;}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void potion(EntityPotionEffectEvent e){for(Run r:runs.values())if(r.minions.potion(e))return;}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void magicAffect(com.example.advancemagic.api.MagicAffectEvent e){for(Run r:runs.values())if(r.minions.rejectsMagic(e.getTarget())){e.setCancelled(true);return;}}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void move(PlayerMoveEvent e){for(Run r:runs.values()){
        r.minions.moved(e);
        if(r.fighting&&r.emptySince<0&&r.arena.contains(e.getFrom())&&!r.arena.contains(e.getTo())
            &&players(r).stream().noneMatch(p->p!=e.getPlayer())){r.emptySince=tick;r.warnedEmpty=false;}
    }}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void teleporting(PlayerTeleportEvent e){for(Run r:runs.values())r.minions.teleporting(e);}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void teleported(PlayerTeleportEvent e){for(Run r:runs.values()){
        r.minions.teleported(e);
        if(r.fighting&&r.emptySince<0&&r.arena.contains(e.getFrom())&&!r.arena.contains(e.getTo())&&players(r).stream().noneMatch(p->p!=e.getPlayer())){r.emptySince=tick;r.warnedEmpty=false;}
    }}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void velocity(PlayerVelocityEvent e){for(Run r:runs.values())r.minions.velocity(e);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void mount(EntityMountEvent e){for(Run r:runs.values()){
        if(r.minions.owns(e.getEntity())){e.setCancelled(true);return;}r.minions.mounting(e);
    }}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void fish(PlayerFishEvent e){if(e.getCaught()!=null)for(Run r:runs.values())if(r.minions.owns(e.getCaught())){e.setCancelled(true);return;}}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void vehicle(org.bukkit.event.vehicle.VehicleEntityCollisionEvent e){for(Run r:runs.values())if(r.minions.owns(e.getEntity())){e.setCancelled(true);return;}}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void combust(EntityCombustEvent e){for(Run r:runs.values())if(r.minions.owns(e.getEntity())){e.setCancelled(true);return;}}
    private void departure(Player p){interactions.remove(p.getUniqueId());for(Run r:runs.values())r.minions.departed(p);}
    @EventHandler public void quit(PlayerQuitEvent e){departure(e.getPlayer());}
    @EventHandler public void death(PlayerDeathEvent e){departure(e.getEntity());}
    @EventHandler public void changed(PlayerChangedWorldEvent e){departure(e.getPlayer());}
    @EventHandler public void removed(EntityRemoveEvent e){for(Run r:runs.values())r.minions.removed(e);}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void used(PlayerInteractEvent e){if(e.getAction()!=org.bukkit.event.block.Action.LEFT_CLICK_AIR)interactions.put(e.getPlayer().getUniqueId(),Bukkit.getCurrentTick());}
    @EventHandler(priority=EventPriority.MONITOR)
    public void usedEntity(PlayerInteractEntityEvent e){interactions.put(e.getPlayer().getUniqueId(),Bukkit.getCurrentTick());}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void swing(PlayerAnimationEvent e){
        if(e.getAnimationType()!=PlayerAnimationType.ARM_SWING||!playable(e.getPlayer()))return;Player p=e.getPlayer();int serverTick=Bukkit.getCurrentTick();long swung=tick;
        Location eye=p.getEyeLocation();Map.Entry<Mob,Double> nearest=null;Run owner=null;double reach=BossHitbox.reach(p);
        for(Run r:runs.values())if(inside(r,p)){var hit=r.minions.meleeAim(p,reach,swung);if(hit!=null&&(nearest==null||hit.getValue()<nearest.getValue())){nearest=hit;owner=r;}}
        if(nearest==null)return;
        var obstruction=nearest.getValue()<=0?null:p.getWorld().rayTraceEntities(eye,eye.getDirection(),nearest.getValue(),
            entity->entity!=p&&entity instanceof LivingEntity&&runs.values().stream().noneMatch(r->r.minions.owns(entity)));
        if(obstruction!=null)return;
        Mob receiver=nearest.getKey();double distance=nearest.getValue();Run selected=owner;
        Bukkit.getScheduler().runTask(plugin,()->{
            if(closed||!p.isOnline()||!runs.containsValue(selected)||!inside(selected,p)||!selected.minions.owns(receiver)
                    ||interactions.getOrDefault(p.getUniqueId(),Integer.MIN_VALUE)>=serverTick)return;
            BossHitbox.attack(p,receiver,eye,distance);
        });
    }
    @EventHandler public void loaded(ChunkLoadEvent e){Bukkit.getScheduler().runTaskLater(plugin,()->discover(e.getChunk()),20);}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void unloaded(ChunkUnloadEvent e){for(Run r:new ArrayList<>(runs.values())){
        Location c=r.arena.center();if(!r.fighting&&c.getWorld()==e.getWorld()&&(c.getBlockX()>>4)==e.getChunk().getX()&&(c.getBlockZ()>>4)==e.getChunk().getZ()){
            runs.remove(id(r.arena));r.minions.close();release(r);}
    }}
    @Override public void close(){if(closed)return;closed=true;task.cancel();for(Run r:runs.values()){r.minions.close();release(r);}runs.clear();interactions.clear();save();}
}
