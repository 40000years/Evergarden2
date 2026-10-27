package com.example.voidscape.boss;

import com.example.voidscape.VoidscapePlugin;
import com.example.voidscape.compat.LevelledMobsCompat;
import org.bukkit.*;
import org.bukkit.attribute.*;
import org.bukkit.boss.*;
import org.bukkit.damage.*;
import org.bukkit.entity.*;
import org.bukkit.event.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.*;
import java.util.*;
import java.util.function.*;

/** Encounter-owned river spirits. All clocks, temporary debuffs and children close with the fight. */
final class ArkhaiaMinions implements AutoCloseable {
    private enum AcheronSkill {SPIN,SPLIT,SNARE}
    private enum FurnaceSkill {SLAM,KICK,ROCK}
    static final class Spirit {
        final ArkhaiaWaves.Kind kind;
        final BossBar bar;
        Mob mob;
        RiverVisual visual;Location home;
        long nextVisual,castStarted,castDuration,castActiveUntil,skillContactAt,attackStarted=-1;
        double health=800;
        long attackAt,castAt,releaseAt,stunUntil;
        Location mark,aim;
        String pose="idle";long poseAt;double poseRate=1,poseElapsed;boolean poseInitialized;
        long spinUntil;
        boolean quillsVisible;long snareBurstUntil;
        double chargeX,chargeZ;
        final Set<UUID> spinVictims=new HashSet<>();
        final Map<UUID,Long> hits=new HashMap<>();
        final Set<UUID> contributors=new HashSet<>();
        Spirit parent;boolean echo,pendingSplit;int splitThresholds;
        long snareAt;AcheronSkill skill=AcheronSkill.SPIN;
        FurnaceSkill furnace=FurnaceSkill.SLAM;int furnaceTurns;
        String adaptedWeapon="";int adaptedHits;
        BlockDisplay heldRock;
        UUID throwTarget;Location throwPrevious;org.bukkit.util.Vector throwMotion=new org.bukkit.util.Vector();
        long slamStarted=-1;Location slamOrigin;final Set<UUID> slamVictims=new HashSet<>();
        BansheeSouls.State soul;
        long blinkNext,blinkAt;Location blinkMark;int blinkSide;
        UUID curseTarget;
        boolean resurrected;long revivalUntil,magicFeedbackAt;
        boolean enraged;long siphonUntil,berserkUntil;
        /** 1 for sanctuary bosses; Arkhaia's ritual summons lighter spirits. */
        double healthScale=1;
        final List<Location> snareMarks=new ArrayList<>();
        double maxHealth(){return healthScale*(echo?200:kind==ArkhaiaWaves.Kind.PHLEGETHON?1000:kind==ArkhaiaWaves.Kind.BANSHEE_WAIL?500:kind==ArkhaiaWaves.Kind.COCYTUS?700:kind==ArkhaiaWaves.Kind.MALEFEASANCE?600:kind==ArkhaiaWaves.Kind.STYX?900:800);}
        double size(){return kind==ArkhaiaWaves.Kind.MALEFEASANCE?(echo?1.2:2.0):(echo?2.4:RiverVisual.SCALE);}
        String key(){return echo?kind+":"+mob.getUniqueId():kind.toString();}
        Spirit(ArkhaiaWaves.Kind kind){this.kind=kind;health=kind==ArkhaiaWaves.Kind.STYX?600:maxHealth();bar=Bukkit.createBossBar(name(kind)+" · "+(int)health+" / "+(int)maxHealth(),BarColor.BLUE,BarStyle.SOLID);bar.setProgress(health/maxHealth());}
    }
    private static final class Shade {
        final Mob mob;final long until;long attackAt,contactAt;Location aim;
        Shade(Mob mob,long until){this.mob=mob;this.until=until;}
        Mob mob(){return mob;}long until(){return until;}
    }
    private record FrostArrow(Arrow arrow,Mob owner,long until,boolean burst){}
    private record Quill(Location from,Location to){}
    private static final class Bolt {
        Location at;final org.bukkit.util.Vector step;final Mob owner;final long until;final boolean sonic;Set<UUID> victims=new HashSet<>();
        Bolt(Location at,org.bukkit.util.Vector step,Mob owner,long until,boolean sonic){this.at=at;this.step=step;this.owner=owner;this.until=until;this.sonic=sonic;}
    }
    private final List<Bolt> bolts=new ArrayList<>();
    private static final class Boulder {
        final Spirit owner;final BlockDisplay display;final long until;
        Location at;final org.bukkit.util.Vector velocity;
        Boulder(Spirit owner,BlockDisplay display,Location at,org.bukkit.util.Vector velocity,long until){this.owner=owner;this.display=display;this.at=at;this.velocity=velocity;this.until=until;}
    }
    private final List<Boulder> boulders=new ArrayList<>();
    private static final double BOULDER_RADIUS=.9;
    private final Map<UUID,FrostArrow> frostArrows=new HashMap<>();
    private final VoidscapePlugin plugin;
    private final Location center;
    private final Predicate<Player> inside;
    private final Supplier<? extends Entity> boss;
    private final LongConsumer death;
    record Defeat(ArkhaiaWaves.Kind kind,Location at,Set<UUID> contributors){}
    private Consumer<Defeat> defeated=ignored->{};
    void onDefeat(Consumer<Defeat> listener){defeated=Objects.requireNonNull(listener);}
    final ArkhaiaWaves waves=new ArkhaiaWaves();
    private final EnumMap<ArkhaiaWaves.Kind,Spirit> spirits=new EnumMap<>(ArkhaiaWaves.Kind.class);
    private final List<Spirit> echoes=new ArrayList<>();
    private record Root(Location anchor,long until,UUID source){}
    private final Map<UUID,Root> roots=new HashMap<>();
    private final Map<UUID,Long> rootImmune=new HashMap<>();
    private final NamespacedKey rootKey;
    private final List<Shade> shades=new ArrayList<>();
    private final Map<UUID,Long> sundered=new HashMap<>();
    private final Map<UUID,Player> armorOwners=new HashMap<>();
    private final NamespacedKey armorKey;
    private final JudgeAether aether;
    private final JudgeSigils sigils;
    private final BansheeSouls souls;
    private final RiverTrials trials;
    private final AerialCounter aerial;
    private long clock;
    private boolean closed,applying,clearing;
    private boolean demo,passive;
    private UUID demoOwner;
    private String demoPose="idle";
    private long demoPoseStarted;
    ArkhaiaMinions(VoidscapePlugin plugin,Location center,Predicate<Player> inside,Supplier<? extends Entity> boss,LongConsumer death){
        this.plugin=plugin;this.center=center.clone();this.inside=inside;this.boss=boss;this.death=death;
        armorKey=plugin.key("arkhaia_sunder_"+UUID.randomUUID().toString().replace("-",""));
        aether=new JudgeAether(plugin);sigils=new JudgeSigils(plugin,aether);
        souls=new BansheeSouls(plugin,this.center,inside,()->clock,sigils);
        trials=new RiverTrials(plugin,this,this.center,sigils);
        aerial=new AerialCounter(this,this.center,sigils);
        rootKey=plugin.key("acheron_root_"+UUID.randomUUID().toString().replace("-",""));
    }
    static String name(ArkhaiaWaves.Kind kind){return switch(kind){
        case ACHERON->"Acheron";case PHLEGETHON->"Phlegethon";case COCYTUS->"Cocytus";
        case BANSHEE_WAIL->"Banshee Wail";case MALEFEASANCE->"Malefeasance";case STYX->"Styx";};}
    boolean owns(Entity entity){return trials.owns(entity)||souls.owns(entity)||frostArrows.containsKey(entity.getUniqueId())||spirit(entity)!=null||allSpirits().stream().anyMatch(s->s.visual!=null&&s.visual.owns(entity))||shades.stream().anyMatch(s->s.mob()==entity)||sigils.owns(entity);}
    private List<Spirit> allSpirits(){List<Spirit> all=new ArrayList<>(spirits.values());all.addAll(echoes);return all;}
    private Spirit spirit(Entity entity){return allSpirits().stream().filter(s->s.mob==entity).findFirst().orElse(null);}
    int alive(){return spirits.size();}
    boolean accepts(Player p){return p!=null&&inside.test(p);}
    private List<Player> players(){
        if(demo&&passive){Player owner=demoOwner==null?null:Bukkit.getPlayer(demoOwner);return owner!=null&&owner.isOnline()&&owner.getWorld()==center.getWorld()?List.of(owner):List.of();}
        return center.getWorld().getPlayers().stream().filter(inside).toList();
    }
    private void tag(Mob mob){
        if(passive)mob.setAI(false);
        mob.setPersistent(false);mob.setRemoveWhenFarAway(false);mob.addScoreboardTag("evergarden_unique");mob.addScoreboardTag("arkhaia_minion");
        mob.getPersistentDataContainer().set(plugin.key("world_boss_entity"),PersistentDataType.BYTE,(byte)1);
        mob.getPersistentDataContainer().set(plugin.key("arkhaia_minion"),PersistentDataType.BYTE,(byte)1);
        LevelledMobsCompat.tagMob(mob,plugin,true);
        mob.getAttribute(Attribute.ATTACK_DAMAGE).setBaseValue(0);
        mob.getAttribute(Attribute.KNOCKBACK_RESISTANCE).setBaseValue(.7);
        mob.getAttribute(Attribute.FOLLOW_RANGE).setBaseValue(84);
        if(mob instanceof Zombie zombie){zombie.setAdult();zombie.setShouldBurnInDay(false);}
        if(mob instanceof AbstractSkeleton skeleton)skeleton.setShouldBurnInDay(false);
        mob.setSilent(true);mob.setCanPickupItems(false);
    }
    private void spawn(Spirit s){
        double a=s.kind.ordinal()*Math.PI/3;
        Location at=s.home==null?center.clone().add(Math.cos(a)*12,0,Math.sin(a)*12):s.home.clone();
        if(s.visual!=null){s.visual.close();s.visual=null;}
        s.nextVisual=0;
        Class<? extends Mob> type=switch(s.kind){case ACHERON,STYX->WitherSkeleton.class;case COCYTUS->Stray.class;case PHLEGETHON->Husk.class;case BANSHEE_WAIL->Drowned.class;case MALEFEASANCE->Skeleton.class;};
        s.mob=center.getWorld().spawn(at,type,m->{s.mob=m;tag(m);m.getAttribute(Attribute.SCALE).setBaseValue(s.size());m.getAttribute(Attribute.MAX_HEALTH).setBaseValue(s.maxHealth());m.setHealth(s.maxHealth());
            m.customName(net.kyori.adventure.text.Component.text(s.echo?"Acheron Echo":name(s.kind)));m.setCustomNameVisible(true);
            m.getEquipment().setItemInMainHand(new ItemStack(s.kind==ArkhaiaWaves.Kind.ACHERON||s.kind==ArkhaiaWaves.Kind.STYX?Material.IRON_SWORD:Material.AMETHYST_SHARD));
            m.getEquipment().setHelmet(new ItemStack(Material.CHAINMAIL_HELMET));
            for(var slot:List.of(org.bukkit.inventory.EquipmentSlot.HAND,org.bukkit.inventory.EquipmentSlot.OFF_HAND,org.bukkit.inventory.EquipmentSlot.HEAD,org.bukkit.inventory.EquipmentSlot.CHEST,org.bukkit.inventory.EquipmentSlot.LEGS,org.bukkit.inventory.EquipmentSlot.FEET))m.getEquipment().setDropChance(slot,0);
        });
        visual(s);
        if(s.kind==ArkhaiaWaves.Kind.BANSHEE_WAIL)s.soul=souls.bind(s.soul,s.mob,passive);
        s.attackAt=clock+30;s.castAt=clock+(s.echo?45:60)+s.kind.ordinal()*12;s.snareAt=clock+40;
        if(s.kind==ArkhaiaWaves.Kind.MALEFEASANCE||s.kind==ArkhaiaWaves.Kind.STYX)s.mob.setHealth(Math.max(.01,s.health));
        s.blinkNext=clock+70;
        center.getWorld().spawnParticle(Particle.SOUL,at.clone().add(0,1,0),30,.7,1,.7,.03);
        center.getWorld().playSound(at,Sound.ENTITY_EVOKER_PREPARE_SUMMON,.8f,.7f);
        if(!s.echo)for(Player p:players())plugin.message(p,"§dArkhaia เรียก "+name(s.kind)+" §7("+(int)s.maxHealth()+" HP)");
    }
    /** Return true for owned incoming damage, including rejected outside-arena shots. */
    private void removeSpirit(Spirit s){
        aerial.remove(s);
        trials.remove(s);
        clearBlink(s);
        souls.remove(s.soul);
        clearSlam(s);
        clearHeldRock(s);
        boulders.removeIf(b->{if(b.owner!=s)return false;b.display.remove();return true;});
        echoes.remove(s);s.bar.removeAll();if(s.visual!=null)s.visual.close();
        sigils.remove("river:"+s.key());clearSpin(s);sigils.remove("identity:"+s.key());clearSnareMarks(s);s.mob.remove();
    }
    private void clearEchoes(Spirit parent){for(Spirit echo:new ArrayList<>(echoes))if(echo.parent==parent)removeSpirit(echo);}
    private boolean hasEchoes(Spirit parent){return echoes.stream().anyMatch(s->s.parent==parent&&s.mob.isValid());}
    private void splitThresholds(Spirit s,double before){
        double[] thresholds={s.maxHealth()*.75,s.maxHealth()*.5,s.maxHealth()*.25};
        for(int i=0;i<thresholds.length;i++)if(before>thresholds[i]&&s.health<=thresholds[i]&&(s.splitThresholds&(1<<i))==0){
            s.splitThresholds|=1<<i;
            // A skipped threshold is consumed, never queued to respawn the pair later.
            if(!hasEchoes(s)&&!s.pendingSplit&&!(s.skill==AcheronSkill.SPLIT&&(s.releaseAt>0||clock<s.castActiveUntil)))s.pendingSplit=true;
        }
    }
    private boolean echoSpace(Location at){
        if(Math.hypot(at.getX()-center.getX(),at.getZ()-center.getZ())>36||!at.clone().subtract(0,1,0).getBlock().getType().isSolid())return false;
        for(int x=(int)Math.floor(at.getX()-.85);x<=(int)Math.floor(at.getX()+.85);x++)
            for(int z=(int)Math.floor(at.getZ()-.85);z<=(int)Math.floor(at.getZ()+.85);z++)
                for(int y=at.getBlockY();y<=at.getBlockY()+5;y++)if(!at.getWorld().getBlockAt(x,y,z).isPassable())return false;
        return true;
    }
    private void split(Spirit parent){
        if(parent.echo||hasEchoes(parent)||!parent.mob.isValid())return;
        List<Location> spots=new ArrayList<>();
        for(double radius:new double[]{4,5,3})for(int i=0;i<8&&spots.size()<2;i++){
            double angle=Math.toRadians(parent.mob.getLocation().getYaw())+Math.PI/2+i*Math.PI/4;
            Location at=parent.mob.getLocation().add(Math.cos(angle)*radius,0,Math.sin(angle)*radius);
            if(echoSpace(at)&&spots.stream().allMatch(other->other.distanceSquared(at)>=9))spots.add(at);
        }
        if(spots.size()!=2)return;
        for(Location at:spots){Spirit echo=new Spirit(ArkhaiaWaves.Kind.ACHERON);echo.echo=true;echo.parent=parent;echo.healthScale=parent.healthScale;echo.health=echo.maxHealth();echo.home=at;echoes.add(echo);spawn(echo);}
        parent.mob.getWorld().playSound(parent.mob.getLocation(),Sound.ENTITY_ILLUSIONER_MIRROR_MOVE,1,.65f);
    }
    private String castClip(Spirit s,String stage){
        if(s.kind==ArkhaiaWaves.Kind.BANSHEE_WAIL)return "curse_"+stage;
        if(s.kind==ArkhaiaWaves.Kind.PHLEGETHON)return (s.furnace==FurnaceSkill.KICK?"kick_":s.furnace==FurnaceSkill.ROCK?"throw_":"cast_")+stage;
        return s.kind==ArkhaiaWaves.Kind.ACHERON&&s.skill!=AcheronSkill.SPIN?"ritual_"+stage:"cast_"+stage;
    }
    private void clearSnareMarks(Spirit s){for(int i=0;i<s.snareMarks.size();i++)sigils.remove("snare:"+s.key()+":"+i);s.snareMarks.clear();s.snareBurstUntil=0;}
    private void bindingRing(Location at,double radius,boolean impact){
        // Vanilla full-bright sparks remain visible even before/without a pack.
        for(int n=0;n<48;n++){
            double angle=n*Math.PI/24;Location point=at.clone().add(Math.cos(angle)*radius,.18,Math.sin(angle)*radius);
            at.getWorld().spawnParticle(Particle.DUST,point,1,0,0,0,0,new Particle.DustOptions(Color.fromRGB(255,45,85),impact?2.1f:1.6f),true);
            if(n%4==0)at.getWorld().spawnParticle(Particle.END_ROD,point,1,0,impact?.25:.03,0,0,null,true);
        }
        for(int n=0;n<12;n++){
            double angle=n*Math.PI/6+clock*.08;at.getWorld().spawnParticle(Particle.ELECTRIC_SPARK,at.clone().add(Math.cos(angle)*1.2,.35,Math.sin(angle)*1.2),1,0,.1,0,0,null,true);
        }
    }
    private void snareWarning(Spirit s){
        for(int i=0;i<s.snareMarks.size();i++){
            Location at=s.snareMarks.get(i).clone().add(0,.12,0);
            sigils.circle("snare:"+s.key()+":"+i,sigil(s.kind),at,6,clock*.025,false);
            bindingRing(s.snareMarks.get(i),6,false);
            // Concentric rings give the large AoE a visible interior on both clients.
            for(double r:new double[]{2,4})for(int n=0;n<12;n++){
                double a=n*Math.PI/6+clock*.04;at.getWorld().spawnParticle(Particle.CRIMSON_SPORE,at.clone().add(Math.cos(a)*r,.15,Math.sin(a)*r),1,0,0,0,0);
            }
        }
    }
    private void root(Player p,Spirit source){
        if(roots.containsKey(p.getUniqueId()))return;
        if(clock<rootImmune.getOrDefault(p.getUniqueId(),0L)){p.sendActionBar(net.kyori.adventure.text.Component.text("BINDING RESISTED"));return;}
        // Attribute roots do not stop a passenger being carried by a flying staff.
        // Dismount before recording the anchor so its correction cannot clear root.
        if(p.isInsideVehicle())p.leaveVehicle();
        roots.put(p.getUniqueId(),new Root(p.getLocation().clone(),clock+60,source.mob.getUniqueId()));rootImmune.put(p.getUniqueId(),clock+300);
        for(Attribute attribute:List.of(Attribute.MOVEMENT_SPEED,Attribute.JUMP_STRENGTH)){
            var instance=p.getAttribute(attribute);if(instance==null)continue;
            instance.removeModifier(rootKey);instance.addTransientModifier(new AttributeModifier(rootKey,-1,AttributeModifier.Operation.MULTIPLY_SCALAR_1));
        }
        p.setVelocity(new org.bukkit.util.Vector());p.playSound(p.getLocation(),Sound.BLOCK_CHAIN_PLACE,1,.6f);
        p.sendActionBar(net.kyori.adventure.text.Component.text("BOUND - 3.0s"));
        p.getWorld().spawnParticle(Particle.END_ROD,p.getLocation().add(0,1,0),28,.45,.8,.45,.03,null,true);
    }
    private void unroot(UUID id){
        boolean bound=roots.remove(id)!=null;sigils.remove("root:"+id);Player p=Bukkit.getPlayer(id);if(p==null)return;
        for(Attribute attribute:List.of(Attribute.MOVEMENT_SPEED,Attribute.JUMP_STRENGTH)){var instance=p.getAttribute(attribute);if(instance!=null)instance.removeModifier(rootKey);}
        if(bound)p.sendActionBar(net.kyori.adventure.text.Component.text("Binding released"));
    }
    private void clearRoots(UUID source){for(var entry:new ArrayList<>(roots.entrySet()))if(source==null||entry.getValue().source().equals(source))unroot(entry.getKey());}
    void moved(org.bukkit.event.player.PlayerMoveEvent event){
        trials.moved(event);
        Root root=roots.get(event.getPlayer().getUniqueId());if(root==null)return;
        if(clock>=root.until()||!inside.test(event.getPlayer())){unroot(event.getPlayer().getUniqueId());return;}
        Location to=event.getTo();if(to==null)return;Location anchor=root.anchor().clone();anchor.setYaw(to.getYaw());anchor.setPitch(to.getPitch());event.setTo(anchor);event.getPlayer().setVelocity(new org.bukkit.util.Vector());
    }
    void teleporting(org.bukkit.event.player.PlayerTeleportEvent event){
        trials.teleporting(event);
        Root root=roots.get(event.getPlayer().getUniqueId());if(root==null||clock>=root.until())return;
        Location to=event.getTo();if(to==null)return;
        // Nearby plugin corrections/dashes must not silently erase a binding.
        // Explicit commands, cross-world travel and long relocations still work.
        if(event.getCause()!=org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.COMMAND&&to.getWorld()==root.anchor().getWorld()&&to.distanceSquared(root.anchor())<=12*12)event.setCancelled(true);
    }
    void teleported(org.bukkit.event.player.PlayerTeleportEvent event){trials.teleported(event);unroot(event.getPlayer().getUniqueId());}
    void velocity(org.bukkit.event.player.PlayerVelocityEvent event){if(trials.trapped(event.getPlayer())||roots.containsKey(event.getPlayer().getUniqueId()))event.setVelocity(new org.bukkit.util.Vector());}
    void mounting(org.bukkit.event.entity.EntityMountEvent event){if(event.getEntity() instanceof Player p&&(trials.trapped(p)||roots.containsKey(p.getUniqueId())))event.setCancelled(true);}
    void departed(Player player){trials.departed(player);souls.departed(player);unroot(player.getUniqueId());rootImmune.remove(player.getUniqueId());}
    private void tickRoots(){
        for(var entry:new ArrayList<>(roots.entrySet())){
            Player p=Bukkit.getPlayer(entry.getKey());Root root=entry.getValue();
            if(p==null||p.isDead()||!inside.test(p)||clock>=root.until()||p.getWorld()!=root.anchor().getWorld()){unroot(entry.getKey());continue;}
            p.setVelocity(new org.bukkit.util.Vector());
            if(clock%4==0){sigils.circle("root:"+entry.getKey(),sigil(ArkhaiaWaves.Kind.ACHERON),p.getLocation().add(0,.08,0),.8,clock*.07,false);
                p.sendActionBar(net.kyori.adventure.text.Component.text(String.format(java.util.Locale.ROOT,"BOUND - %.1fs",Math.max(0,root.until()-clock)/20.0)));
                for(int n=0;n<12;n++){
                    double angle=clock*.2+n*Math.PI/3;Location point=p.getLocation().add(Math.cos(angle)*.5,n*.15,Math.sin(angle)*.5);
                    p.getWorld().spawnParticle(Particle.ELECTRIC_SPARK,point,1,0,0,0,0,null,true);
                }}
        }
        rootImmune.entrySet().removeIf(e->clock>=e.getValue());
    }
    boolean damage(EntityDamageEvent event,Player attacker){
        if(trials.damage(event,attacker,clock))return true;
        if(souls.damage(event,attacker))return true;
        Spirit s=spirit(event.getEntity());
        boolean shade=shades.stream().anyMatch(x->x.mob()==event.getEntity());
        if(s==null&&!shade)return false;
        double damage=event.getFinalDamage();event.setCancelled(true);
        if(passive||attacker==null||!inside.test(attacker)||!Double.isFinite(damage)||damage<=0)return true;
        if(s!=null&&s.kind==ArkhaiaWaves.Kind.COCYTUS&&event instanceof EntityDamageByEntityEvent by
            &&by.getDamager() instanceof AbstractArrow&&!(by.getDamager() instanceof Trident)&&!advanceMagicDamage()){
            if(clock>=s.magicFeedbackAt){s.magicFeedbackAt=clock+20;attacker.sendActionBar(net.kyori.adventure.text.Component.text("COCYTUS - ARROW IMMUNE"));}return true;
        }
        if(s!=null&&s.kind==ArkhaiaWaves.Kind.STYX&&event instanceof EntityDamageByEntityEvent by&&by.getDamager()==attacker
            &&(event.getCause()==EntityDamageEvent.DamageCause.ENTITY_ATTACK||event.getCause()==EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK)&&!advanceMagicDamage()){
            if(clock>=s.magicFeedbackAt){s.magicFeedbackAt=clock+20;attacker.sendActionBar(net.kyori.adventure.text.Component.text("STYX - MELEE IMMUNE"));}return true;
        }
        if(s!=null&&s.kind==ArkhaiaWaves.Kind.MALEFEASANCE){
            var magic=Bukkit.getPluginManager().getPlugin("advance-magic");
            if(magic instanceof com.example.advancemagic.AdvanceMagicPlugin m&&m.isEnabled()&&m.context()!=null&&m.context().isMagicDamage()){
                if(clock>=s.magicFeedbackAt){s.magicFeedbackAt=clock+20;attacker.sendActionBar(net.kyori.adventure.text.Component.text("DIMENSIONAL WARD - ADVANCE MAGIC IMMUNE"));}
                return true;
            }
            if(clock<s.revivalUntil)return true;
        }
        if(shade){Mob m=(Mob)event.getEntity();ArkhaiaHitFeedback.confirm(attacker,m,event,Math.min(m.getHealth(),damage),false);double hp=m.getHealth()-damage;if(hp<=0){m.remove();shades.removeIf(x->x.mob()==m);}else m.setHealth(hp);return true;}
        if(s!=null&&souls.shielded(s.soul)){
            attacker.sendActionBar(net.kyori.adventure.text.Component.text("SOUL VEIL: destroy all 3 Soul Anchors to reveal Banshee"));return true;
        }
        if(event instanceof EntityDamageByEntityEvent by&&by.getDamager()==attacker
                &&(event.getCause()==EntityDamageEvent.DamageCause.ENTITY_ATTACK||event.getCause()==EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK)
                &&!(s.kind==ArkhaiaWaves.Kind.STYX&&advanceMagicDamage())
                &&!BossHitbox.confirmed(attacker,s.mob)
                &&!BossHitbox.melee(attacker,model(s),s.pose,poseTime(s),s.mob.getLocation(),RiverVisual.scale(model(s)),event.getCause()==EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK))return true;
        UUID id=attacker.getUniqueId();if(clock<s.hits.getOrDefault(id,0L))return true;s.hits.put(id,clock+10);
        damage=trials.incoming(s,damage,clock);
        if(s.kind==ArkhaiaWaves.Kind.STYX&&trials.styxIncoming(s,damage,clock))return true;
        if(s.kind==ArkhaiaWaves.Kind.PHLEGETHON){
            String weapon=weaponType(event,attacker);
            if(!s.adaptedWeapon.equals(weapon)){s.adaptedWeapon=weapon;s.adaptedHits=0;}
            // In the ritual, adaptation stops at half damage instead of halving on every repeated hit.
            damage=Math.scalb(damage,-Math.min(s.adaptedHits,ritual()?1:1023));s.adaptedHits=Math.min(1023,s.adaptedHits+1);
            attacker.sendActionBar(net.kyori.adventure.text.Component.text("Phlegethon adapts: "+weapon+" | chain "+s.adaptedHits));
            s.mob.getWorld().spawnParticle(Particle.ENCHANT,s.mob.getLocation().add(0,4,0),18,1.5,2,1.5,.3);
        }
        double before=s.health;s.health=Math.max(0,s.health-damage);
        if(before>s.health)(s.echo?s.parent:s).contributors.add(attacker.getUniqueId());
        ArkhaiaHitFeedback.confirm(attacker,s.mob,event,before-s.health,false);
        if(s.health==0){
            if(trials.revive(s,clock))return true;
            if(s.soul!=null)s.contributors.addAll(s.soul.contributors);
            Defeat reward=new Defeat(s.kind,s.mob.getLocation().clone(),Set.copyOf(s.contributors));
            if(!s.echo)spirits.remove(s.kind);
            removeSpirit(s);
            if(!s.echo){waves.died(s.kind,clock);clearEchoes(s);clearRoots(s.mob.getUniqueId());death.accept(clock);defeated.accept(reward);}
        }else{
            if(s.kind==ArkhaiaWaves.Kind.MALEFEASANCE||s.kind==ArkhaiaWaves.Kind.STYX)s.mob.setHealth(s.health);
            if(s.kind==ArkhaiaWaves.Kind.STYX&&s.health<s.maxHealth()*.5)s.enraged=true;
            s.bar.setProgress(s.health/s.maxHealth());s.bar.setTitle((s.echo?"Acheron Echo":name(s.kind))+" | "+(int)Math.ceil(s.health)+" / "+(int)s.maxHealth());
            if(s.kind==ArkhaiaWaves.Kind.ACHERON&&!s.echo)splitThresholds(s,before);
        }
        return true;
    }
    boolean rejectsMagic(Entity target){return allSpirits().stream().anyMatch(s->s.kind==ArkhaiaWaves.Kind.MALEFEASANCE&&(s.mob==target||s.visual!=null&&s.visual.owns(target)));}
    private boolean advanceMagicDamage(){var magic=Bukkit.getPluginManager().getPlugin("advance-magic");return magic instanceof com.example.advancemagic.AdvanceMagicPlugin m&&m.isEnabled()&&m.context()!=null&&m.context().isMagicDamage();}
    private String weaponType(EntityDamageEvent event,Player attacker){
        if(event instanceof EntityDamageByEntityEvent by){
            if(by.getDamager() instanceof Trident)return "TRIDENT";
            if(by.getDamager() instanceof AbstractArrow arrow){
                ItemStack weapon=arrow.getWeapon();
                return weapon==null?(arrow.isShotFromCrossbow()?"CROSSBOW":"BOW"):weapon.getType().name();
            }
            if(by.getDamager() instanceof Projectile projectile)return projectile.getType().name();
        }
        String type=attacker.getInventory().getItemInMainHand().getType().name();
        for(String family:List.of("SWORD","PICKAXE","AXE","SHOVEL","HOE"))if(type.endsWith("_"+family))return family;
        return type.equals("AIR")?"FIST":type;
    }
    boolean outgoing(EntityDamageByEntityEvent event){
        if(applying)return false;
        if(trials.owns(event.getDamager())){event.setCancelled(true);return true;}
        if(souls.owns(event.getDamager())){event.setCancelled(true);return true;}
        FrostArrow shot=frostArrows.remove(event.getDamager().getUniqueId());
        if(shot!=null){
            event.setCancelled(true);shot.arrow().remove();
            if(shot.owner().isValid()&&spirit(shot.owner())!=null){
                if(shot.burst())frostBurst(shot,event.getEntity().getLocation());
                else if(event.getEntity() instanceof Player p&&hit(p,18,shot.owner()))trials.chill(p,clock);
            }
            return true;
        }
        Entity source=event.getDamager();if(source instanceof Projectile p&&p.getShooter() instanceof Entity e)source=e;
        if(spirit(source)!=null||shades.stream().anyMatch(s->s.mob()==event.getDamager())){event.setCancelled(true);return true;}return false;
    }
    boolean projectileHit(ProjectileHitEvent event){
        FrostArrow shot=frostArrows.get(event.getEntity().getUniqueId());if(shot==null)return false;
        // Entity damage is processed by outgoing(); block impact is a separate path.
        if(event.getHitBlock()!=null){frostArrows.remove(event.getEntity().getUniqueId());
            if(shot.burst()&&shot.owner().isValid()&&spirit(shot.owner())!=null){
                Location at=shot.arrow().getLocation();var velocity=shot.arrow().getVelocity();
                if(velocity.lengthSquared()>.001){var impact=at.getWorld().rayTraceBlocks(at,velocity.clone().normalize(),velocity.length()+1,FluidCollisionMode.NEVER,true);
                    if(impact!=null)at=impact.getHitPosition().toLocation(at.getWorld()).subtract(velocity.clone().normalize().multiply(.05));}
                frostBurst(shot,at);
            }
            shot.arrow().remove();
        }
        return true;
    }
    private void frostBurst(FrostArrow shot,Location at){
        at.getWorld().spawnParticle(Particle.SNOWFLAKE,at,45,1,.2,1,.02);
        sigils.circle("frost:"+shot.arrow().getUniqueId(),sigil(ArkhaiaWaves.Kind.COCYTUS),at.clone().add(0,.05,0),2.5,0,false);
        // The visible burst exists only at projectile contact, never at the old target mark.
        for(Player p:players())if(inCircle(p,at,2.5)&&clearLine(at,p.getEyeLocation())){
            if(hit(p,6,shot.owner())){debuff(p,PotionEffectType.SLOWNESS,80,2);p.setVelocity(p.getVelocity().multiply(.15));}
        }
        String key="frost:"+shot.arrow().getUniqueId();Bukkit.getScheduler().runTaskLater(plugin,()->sigils.remove(key),6);
    }
    private boolean clearLine(Location from,Location to){
        var delta=to.toVector().subtract(from.toVector());return delta.lengthSquared()<.001||from.getWorld().rayTraceBlocks(from,delta.clone().normalize(),delta.length(),FluidCollisionMode.NEVER,true)==null;
    }
    private static String model(Spirit s){return s.echo?"arkhaia_acheron_echo":s.kind==ArkhaiaWaves.Kind.MALEFEASANCE&&s.resurrected?"arkhaia_malefeasance_phase2":"arkhaia_"+s.kind.name().toLowerCase(Locale.ROOT);}
    private double poseTime(Spirit s){return s.poseElapsed;}
    Map.Entry<Mob,Double> meleeAim(Player player,double reach,long swung){
        if(passive&&demoOwner!=null||!inside.test(player))return null;Map.Entry<Mob,Double> best=trials.meleeAim(player,reach);
        for(Spirit s:allSpirits())if(s.mob.isValid()&&s.mob.getWorld()==player.getWorld()&&s.mob.getLocation().distanceSquared(player.getLocation())<20*20
                &&s.hits.getOrDefault(player.getUniqueId(),Long.MIN_VALUE)<=swung){
            double distance=BossHitbox.ray(player,model(s),s.pose,poseTime(s),s.mob.getLocation(),RiverVisual.scale(model(s)),reach);
            if(Double.isFinite(distance)&&(best==null||distance<best.getValue()))best=Map.entry(s.mob,distance);
        }
        return best;
    }
    private boolean inCircle(Player p,Location at,double radius){
        if(p.getWorld()!=at.getWorld()||p.getY()>at.getY()+2.2||p.getBoundingBox().getMaxY()<at.getY())return false;
        var box=p.getBoundingBox();double x=Math.clamp(at.getX(),box.getMinX(),box.getMaxX()),z=Math.clamp(at.getZ(),box.getMinZ(),box.getMaxZ());
        return Math.hypot(x-at.getX(),z-at.getZ())<=radius;
    }
    private double maxHealth(Player p){return p.getAttribute(Attribute.MAX_HEALTH).getValue();}
    private boolean hit(Player p,double damage,Entity source){
        return hit(p,damage,source,true);
    }
    private boolean soulHit(Player player,Entity source){
        if(closed||!inside.test(player)||source==null||!source.isValid())return false;
        applying=true;try{return BossDamage.soul(player,source);}finally{applying=false;}
    }
    boolean healing(EntityRegainHealthEvent event){return souls.healing(event);}
    boolean potion(EntityPotionEffectEvent event){return souls.potion(event);}
    private boolean hit(Player p,double damage,Entity source,boolean armorImpact){
        if(closed||!inside.test(p))return false;
        if(source==null)source=spirits.values().stream().map(s->(Entity)s.mob).filter(Entity::isValid).findFirst().orElse(null);
        if(source==null)return false;
        applying=true;try{return BossDamage.hit(p,damage*ritualDamage,DamageSource.builder(DamageType.INDIRECT_MAGIC).withCausingEntity(source).withDirectEntity(source).build(),armorImpact);}finally{applying=false;}
    }
    private void debuff(Player p,PotionEffectType type,int duration,int strength){p.addPotionEffect(new PotionEffect(type,duration,strength,false,true,true));}
    private double radius(Spirit s){return switch(s.kind){case STYX,COCYTUS->2.5;case ACHERON->s.echo?2.2:s.skill==AcheronSkill.SNARE?6:s.skill==AcheronSkill.SPLIT?4:3;case PHLEGETHON->s.furnace==FurnaceSkill.SLAM?10:s.furnace==FurnaceSkill.ROCK?3.5:3;case BANSHEE_WAIL->5.5;default->2;};}
    private int castCooldown(Spirit s){return switch(s.kind){case ACHERON->s.echo?110:90;case PHLEGETHON->80;case STYX->160;case BANSHEE_WAIL->260;default->150;};}
    private double castRange(Spirit s){return switch(s.kind){case ACHERON->14;case PHLEGETHON->28;case STYX->12;case COCYTUS->24;case BANSHEE_WAIL->28;case MALEFEASANCE->12;};}
    private Location impactPoint(Spirit s,Player target,double reach){
        var direction=target.getLocation().toVector().subtract(s.mob.getLocation().toVector()).setY(0);
        if(direction.lengthSquared()<.001)direction=s.mob.getLocation().getDirection().setY(0);
        return s.mob.getLocation().add(direction.normalize().multiply(reach));
    }
    private Location localPoint(Spirit s,double x,double z){
        double yaw=Math.toRadians(s.mob.getLocation().getYaw());return s.mob.getLocation().add(x*Math.cos(yaw)-z*Math.sin(yaw),0,z*Math.cos(yaw)+x*Math.sin(yaw));
    }
    private void clearSpin(Spirit s){
        sigils.remove("spin:"+s.key());
        if(s.quillsVisible){for(int i=0;i<22;i++)sigils.remove("quill:"+s.key()+":"+i);s.quillsVisible=false;}
    }
    private List<Quill> spinQuills(Spirit s){
        Location center=s.mob.getLocation();double age=Math.clamp(24-(s.spinUntil-clock),0,24)/20.0,scale=s.echo?.6:1;
        var bones=BossHitbox.bonePoints(model(s),"cast_active",age,center,RiverVisual.SCALE,Set.of("weapon_r","weapon_l"));
        List<Quill> rays=new ArrayList<>();
        for(String side:List.of("weapon_r","weapon_l")){
            Location hand=bones.get(side);if(hand==null)continue;
            double heading=Math.atan2(hand.getZ()-center.getZ(),hand.getX()-center.getX());
            for(int i=0;i<11;i++){
                double angle=heading+Math.toRadians((i-5)*14),length=(i%3==0?3.8:i%2==0?2.5:1.2)*scale;
                Location from=hand.clone(),to=from.clone().add(Math.cos(angle)*length,0,Math.sin(angle)*length);
                // Some quills rake the floor, others fan out at wrist height.
                // Every damaging line is the same segment rendered to the client.
                to.setY(i%3==0?center.getY()+.4:hand.getY()+((i%3)-1)*.65*scale);
                var delta=to.toVector().subtract(from.toVector());double distance=delta.length();
                var wall=from.getWorld().rayTraceBlocks(from,delta.clone().normalize(),distance,FluidCollisionMode.NEVER,true);
                if(wall!=null)to=wall.getHitPosition().toLocation(from.getWorld());
                rays.add(new Quill(from,to));
            }
        }
        return rays;
    }
    private boolean quillContact(Player p,Quill ray){
        var direction=ray.to().toVector().subtract(ray.from().toVector());double length=direction.length();if(length<.03)return false;
        var box=p.getBoundingBox().expand(.16);
        return box.contains(ray.from().toVector())||box.rayTrace(ray.from().toVector(),direction.normalize(),length)!=null;
    }
    private void quillEffect(Spirit s,List<Quill> rays){
        s.quillsVisible=true;
        for(int i=0;i<rays.size();i++){
            Quill ray=rays.get(i);String key="quill:"+s.key()+":"+i;
            var delta=ray.to().toVector().subtract(ray.from().toVector());if(delta.lengthSquared()<.03*.03){sigils.remove(key);continue;}
            sigils.fastRay(key,"judge_ray",ray.from(),ray.to(),s.echo?.10:.16);
            // Pack-independent travelling sparks make the quills visible on both
            // clients even when custom spell particles are unavailable.
            var direction=delta.clone().normalize();
            ray.from().getWorld().spawnParticle(Particle.END_ROD,ray.from(),0,direction.getX(),direction.getY(),direction.getZ(),.28,null,true);
            ray.to().getWorld().spawnParticle(Particle.ELECTRIC_SPARK,ray.to(),1,.03,.03,.03,0,null,true);
        }
    }
    private void spinPulse(Spirit s){
        Location at=s.mob.getLocation();
        // The daggers are overhead on this giant rig. A visible ground vortex
        // is the damaging spell, rather than an invisible five-block contact aura.
        sigils.circle("spin:"+s.key(),sigil(s.kind),at.clone().add(0,.12,0),radius(s),clock*.18,false);
        List<Quill> rays=spinQuills(s);quillEffect(s,rays);
        for(Player p:players())if(!s.spinVictims.contains(p.getUniqueId())&&
                (inCircle(p,at,radius(s))&&clearLine(at.clone().add(0,.5,0),p.getLocation().add(0,.8,0))||rays.stream().anyMatch(ray->quillContact(p,ray)))){
            s.spinVictims.add(p.getUniqueId());if(hit(p,s.echo?60:36,s.mob)&&s.echo)debuff(p,PotionEffectType.SLOWNESS,60,1);
        }
    }
    private void chargeStep(Spirit s){
        long age=24-(s.spinUntil-clock);
        double ease=Math.min(1,Math.min((age+1)/4.0,(s.spinUntil-clock)/4.0));
        double dx=s.chargeX*.38*ease,dz=s.chargeZ*.38*ease;
        Location next=s.mob.getLocation().add(dx,0,dz);
        if(Math.hypot(next.getX()-center.getX(),next.getZ()-center.getZ())>36){spinPulse(s);return;}
        // Native collision resolution moves the real receiver and its following
        // renderers together. A wall stops the charge; no teleport through blocks.
        ((org.bukkit.craftbukkit.entity.CraftEntity)s.mob).getHandle().move(
            net.minecraft.world.entity.MoverType.SELF,new net.minecraft.world.phys.Vec3(dx,0,dz));
        spinPulse(s);
        if(clock%3==0)s.mob.getWorld().spawnParticle(Particle.SWEEP_ATTACK,s.mob.getLocation().add(0,1,0),3,1,.2,1,0);
    }
    private void bowShot(Spirit s,Player target){bowShot(s,s.aim==null?target.getEyeLocation():s.aim,false);}
    private void bowShot(Spirit s,Location aim,boolean burst){
        Location from=s.visual==null?s.mob.getLocation().add(0,7,0):s.visual.weaponOrigin();
        var direction=aim.toVector().subtract(from.toVector());
        if(direction.lengthSquared()<.01)return;
        Arrow arrow=from.getWorld().spawnArrow(from,direction.normalize(),1.6f,0);
        arrow.setShooter(s.mob);arrow.setGravity(false);arrow.setDamage(6);arrow.setSilent(true);
        arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);arrow.setPersistent(false);
        arrow.getPersistentDataContainer().set(plugin.key("world_boss_entity"),PersistentDataType.BYTE,(byte)1);
        frostArrows.put(arrow.getUniqueId(),new FrostArrow(arrow,s.mob,clock+60,burst));
        from.getWorld().playSound(from,Sound.ENTITY_ARROW_SHOOT,1,.7f);
    }
    private void attack(Spirit s,Player target){
        if(s.kind==ArkhaiaWaves.Kind.COCYTUS){bowShot(s,target);return;}
        Location from=s.visual==null?s.mob.getLocation().add(0,s.kind==ArkhaiaWaves.Kind.BANSHEE_WAIL?8:5,0):s.visual.castOrigin(),aim=s.aim==null?target.getEyeLocation():s.aim;
        var direction=aim.toVector().subtract(from.toVector());if(direction.lengthSquared()<.01)return;
        boolean sonic=s.kind==ArkhaiaWaves.Kind.BANSHEE_WAIL;
        if(sonic){
            Set<UUID> victims=new HashSet<>();
            for(double angle:new double[]{-10,0,10}){
                Bolt bolt=new Bolt(from.clone(),direction.clone().normalize().rotateAroundY(Math.toRadians(angle)).multiply(1.05),s.mob,clock+Math.max(34,(long)Math.ceil(direction.length()/1.05)+8),true);
                bolt.victims=victims;bolts.add(bolt);
            }
        }else bolts.add(new Bolt(from,direction.normalize().multiply(.85),s.mob,clock+36,false));
        from.getWorld().playSound(from,sonic?Sound.ENTITY_PHANTOM_AMBIENT:Sound.ENTITY_EVOKER_CAST_SPELL,.8f,sonic?1.4f:.6f);
    }
    private void tickBolts(){
        for(var it=bolts.iterator();it.hasNext();){Bolt bolt=it.next();
            if(clock>=bolt.until||!bolt.owner.isValid()||spirit(bolt.owner)==null){it.remove();continue;}
            double length=bolt.step.length();var direction=bolt.step.clone().normalize();
            var wall=bolt.at.getWorld().rayTraceBlocks(bolt.at,direction,length,FluidCollisionMode.NEVER,true);
            if(wall!=null)length=Math.min(length,bolt.at.toVector().distance(wall.getHitPosition()));
            List<Map.Entry<Player,Double>> contacts=new ArrayList<>();double width=bolt.sonic?.75:.25;
            for(Player p:players())if(!bolt.victims.contains(p.getUniqueId())){
                var box=p.getBoundingBox().expand(width);var result=box.rayTrace(bolt.at.toVector(),direction,length);
                if(box.contains(bolt.at.toVector()))contacts.add(Map.entry(p,0.0));
                else if(result!=null)contacts.add(Map.entry(p,bolt.at.toVector().distance(result.getHitPosition())));
            }
            contacts.sort(Map.Entry.comparingByValue());
            for(var contact:contacts){Player p=contact.getKey();bolt.victims.add(p.getUniqueId());if(bolt.sonic)soulHit(p,bolt.owner);else hit(p,22,bolt.owner);if(closed||!bolt.owner.isValid())return;if(!bolt.sonic)break;}
            for(double d=0;d<=length;d+=.2)bolt.at.getWorld().spawnParticle(bolt.sonic?Particle.SOUL:Particle.WITCH,bolt.at.clone().add(direction.clone().multiply(d)),2,.06,.06,.06,0);
            bolt.at.add(direction.multiply(length));
            if(wall!=null||!bolt.sonic&&!contacts.isEmpty())it.remove();
        }
    }
    private void clearBlink(Spirit s){sigils.remove("banshee-blink:"+s.key());s.blinkAt=0;s.blinkMark=null;}
    private boolean blinkSpace(Location at){
        if(Math.hypot(at.getX()-center.getX(),at.getZ()-center.getZ())>35||!at.clone().subtract(0,1,0).getBlock().getType().isSolid())return false;
        for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++)for(int y=0;y<11;y++)if(!at.clone().add(x,y,z).getBlock().isPassable())return false;
        return true;
    }
    private void blinkWarning(Spirit s){
        sigils.circle("banshee-blink:"+s.key(),sigil(s.kind),s.blinkMark.clone().add(0,.16,0),3.5,clock*.06,false);
        for(int i=0;i<20;i++){
            double a=i*Math.PI/10;Location at=s.blinkMark.clone().add(Math.cos(a)*3.5,.25,Math.sin(a)*3.5);
            at.getWorld().spawnParticle(Particle.DUST,at,1,0,0,0,0,new Particle.DustOptions(Color.fromRGB(124,244,206),1.6f),true);
        }
    }
    private boolean beginBlink(Spirit s,Player target){
        if(s.kind!=ArkhaiaWaves.Kind.BANSHEE_WAIL||clock<s.blinkNext||clock>=s.castAt||Math.hypot(s.mob.getX()-target.getX(),s.mob.getZ()-target.getZ())<=12)return false;
        var forward=target.getLocation().getDirection().setY(0);if(forward.lengthSquared()<.01)forward.setZ(1);forward.normalize();
        var side=new org.bukkit.util.Vector(forward.getZ(),0,-forward.getX());int sign=s.blinkSide++%2==0?1:-1;
        for(double angle:new double[]{0,Math.PI,-Math.PI/2,Math.PI/2}){
            var offset=side.clone().multiply(4.5*sign).subtract(forward.clone().multiply(2.5)).rotateAroundY(angle);
            Location at=target.getLocation().add(offset);at.setY(center.getY());
            var move=at.toVector().subtract(s.mob.getLocation().toVector());if(move.lengthSquared()>20*20)at=s.mob.getLocation().add(move.normalize().multiply(20));at.setY(center.getY());
            if(!blinkSpace(at)||at.distanceSquared(s.mob.getLocation())<4*4)continue;
            s.blinkMark=at;s.blinkAt=clock+12;s.blinkNext=clock+110;s.mob.setAI(false);s.mob.getPathfinder().stopPathfinding();
            render(s,target,"reform",20.0/12,true);blinkWarning(s);
            s.mob.getWorld().playSound(at,Sound.ENTITY_VEX_CHARGE,.9f,.7f);return true;
        }
        s.blinkNext=clock+20;return false;
    }
    private void blinkStep(Spirit s,Player target){
        s.mob.setAI(false);s.mob.getPathfinder().stopPathfinding();render(s,target,"reform",20.0/12,true);
        if(clock%3==0)blinkWarning(s);
        if(clock<s.blinkAt)return;
        Location from=s.mob.getLocation(),to=s.blinkMark.clone();clearBlink(s);
        if(!blinkSpace(to))return;
        if(!s.mob.teleport(to))return;
        var delta=to.toVector().subtract(from.toVector());int steps=Math.max(1,(int)delta.length());
        for(int i=0;i<=steps;i++)from.getWorld().spawnParticle(Particle.SOUL,from.clone().add(delta.clone().multiply((double)i/steps)).add(0,2,0),3,.4,1,.4,.01);
        to.getWorld().playSound(to,Sound.ENTITY_ENDERMAN_TELEPORT,1,.65f);
        s.attackAt=Math.max(s.attackAt,clock+10);s.castAt=Math.max(s.castAt,clock+12);render(s,target,"hover",1,false);
    }
    private void curseWarning(Spirit s){
        if(clock-s.castStarted<8){
            Player target=s.curseTarget==null?null:Bukkit.getPlayer(s.curseTarget);
            if(target!=null&&inside.test(target)){s.mark=target.getLocation();s.mark.setY(center.getY());}
        }
        if(clock%4==0){
            sigils.circle("river:"+s.key(),sigil(s.kind),s.mark.clone().add(0,.12,0),radius(s),clock*.025,false);
            for(int i=0;i<48;i++){
                double angle=i*Math.PI/24;Location point=s.mark.clone().add(Math.cos(angle)*radius(s),.2,Math.sin(angle)*radius(s));
                point.getWorld().spawnParticle(Particle.DUST,point,1,0,0,0,0,new Particle.DustOptions(Color.fromRGB(124,244,206),1.6f),true);
            }
        }
    }
    private Map<String,Location> furnaceBones(Spirit s,String clip,double time,Set<String> names){
        return BossHitbox.bonePoints(model(s),clip,time,s.mob.getLocation(),s.size(),names);
    }
    private void furnaceRing(Location at,double radius,Color color,boolean impact){
        int count=Math.max(24,(int)(radius*12));
        for(int i=0;i<count;i++){
            double angle=i*Math.PI*2/count;Location point=at.clone().add(Math.cos(angle)*radius,.2,Math.sin(angle)*radius);
            at.getWorld().spawnParticle(Particle.DUST,point,1,0,0,0,0,new Particle.DustOptions(color,impact?1.8f:1.2f),true);
            if(impact&&i%4==0)at.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME,point,1,0,.15,0,.025,null,true);
        }
    }
    private void clearSlamWarning(Spirit s){for(int i=0;i<3;i++)sigils.remove("furnace:"+s.key()+":"+i);}
    private void clearSlam(Spirit s){clearSlamWarning(s);sigils.remove("furnace-wave:"+s.key());s.slamStarted=-1;s.slamOrigin=null;s.slamVictims.clear();}
    private void slamWarning(Spirit s){
        double[] radii={3,6,10};Color[] colors={Color.fromRGB(255,55,20),Color.fromRGB(255,145,25),Color.fromRGB(255,215,90)};
        for(int i=0;i<radii.length;i++){
            sigils.circle("furnace:"+s.key()+":"+i,sigil(s.kind),s.mark.clone().add(0,.12+i*.025,0),radii[i],clock*.014*(i+1),false);
            furnaceRing(s.mark,radii[i],colors[i],false);
        }
    }
    private void slamWave(Spirit s){
        if(s.slamStarted<0||s.slamOrigin==null)return;
        long age=clock-s.slamStarted;
        if(age>11){clearSlam(s);return;}
        double outer=Math.min(10,(age+1)*.85),inner=Math.max(0,outer-1.25);
        Location at=s.slamOrigin;
        Color color=outer<=3?Color.fromRGB(255,55,20):outer<=6?Color.fromRGB(255,145,25):Color.fromRGB(255,215,90);
        sigils.circle("furnace-wave:"+s.key(),sigil(s.kind),at.clone().add(0,.2,0),outer,clock*.08,false);
        furnaceRing(at,outer,color,true);
        for(Player p:players())if(!s.slamVictims.contains(p.getUniqueId())&&inCircle(p,at,outer)){
            var box=p.getBoundingBox();double farX=Math.max(Math.abs(box.getMinX()-at.getX()),Math.abs(box.getMaxX()-at.getX()));
            double farZ=Math.max(Math.abs(box.getMinZ()-at.getZ()),Math.abs(box.getMaxZ()-at.getZ()));
            if(farX*farX+farZ*farZ<inner*inner||!clearLine(at.clone().add(0,.35,0),p.getLocation().add(0,.8,0)))continue;
            double distance=Math.hypot(p.getX()-at.getX(),p.getZ()-at.getZ());
            s.slamVictims.add(p.getUniqueId());
            if(hit(p,distance<=3?60:distance<=6?44:28,s.mob))debuff(p,PotionEffectType.NAUSEA,120,0);
            if(closed||s.slamOrigin==null||!s.mob.isValid())return;
        }
    }
    private void trackThrow(Spirit s){
        // Commit aim half a second before release, leaving a readable dodge window.
        if(clock>=s.castStarted+s.castDuration+4-10)return;
        Player target=s.throwTarget==null?null:Bukkit.getPlayer(s.throwTarget);
        if(target==null||!inside.test(target)||target.getWorld()!=s.mob.getWorld())return;
        Location at=target.getLocation();
        if(s.throwPrevious!=null){
            var step=at.toVector().subtract(s.throwPrevious.toVector()).setY(0);
            if(step.lengthSquared()>1.2*1.2)step.zero(); // Teleports are not walking velocity.
            if(step.lengthSquared()>.45*.45)step.normalize().multiply(.45);
            s.throwMotion.multiply(.35).add(step.multiply(.65));
        }
        s.throwPrevious=at;s.mark=at.clone();
    }
    private BlockDisplay rockDisplay(Location from){
        return from.getWorld().spawn(from,BlockDisplay.class,e->{
            e.setPersistent(false);e.setGravity(false);e.setInvulnerable(true);e.setBlock(Material.DEEPSLATE.createBlockData());
            e.setTransformation(new org.bukkit.util.Transformation(new org.joml.Vector3f((float)-BOULDER_RADIUS),new org.joml.Quaternionf(),new org.joml.Vector3f((float)(BOULDER_RADIUS*2)),new org.joml.Quaternionf()));
            e.setTeleportDuration(1);e.setViewRange(1.5f);
        });
    }
    private void clearHeldRock(Spirit s){if(s.heldRock!=null){s.heldRock.remove();s.heldRock=null;}}
    private void heldRock(Spirit s,String clip,double time){
        var hands=furnaceBones(s,clip,time,Set.of("hand_r","hand_l"));
        if(hands.size()!=2)return;
        Location at=hands.get("hand_r").clone().add(hands.get("hand_l")).multiply(.5);
        if(s.heldRock==null||!s.heldRock.isValid())s.heldRock=rockDisplay(at);else s.heldRock.teleport(at);
    }
    private void kick(Spirit s){
        var bones=furnaceBones(s,"kick_active",.2,Set.of("foot_r","shin_r"));
        Location foot=bones.get("foot_r"),knee=bones.get("shin_r");if(foot==null||knee==null)return;
        var direction=foot.toVector().subtract(knee.toVector());double length=direction.length();
        if(length<.001)return;direction.normalize();
        s.mob.getWorld().spawnParticle(Particle.SWEEP_ATTACK,foot,1,0,0,0,0);
        for(Player p:players()){
            var box=p.getBoundingBox().expand(.8);
            if((box.contains(foot.toVector())||box.contains(knee.toVector())||box.rayTrace(knee.toVector(),direction,length)!=null)
                    &&clearLine(knee,p.getLocation().add(0,.8,0))&&hit(p,40,s.mob)){
                var push=p.getLocation().toVector().subtract(s.mob.getLocation().toVector()).setY(0);
                if(push.lengthSquared()>.001)p.setVelocity(push.normalize().multiply(2).setY(.55));
            }
        }
    }
    private void throwRock(Spirit s){
        var bones=furnaceBones(s,"throw_active",.2,Set.of("hand_r","hand_l"));
        if(!bones.containsKey("hand_r")||!bones.containsKey("hand_l"))return;
        Location from=bones.get("hand_r").clone().add(bones.get("hand_l")).multiply(.5);
        Location to=s.mark.clone().add(0,.9,0);var delta=to.toVector().subtract(from.toVector());
        double ticks=Math.clamp(Math.hypot(delta.getX(),delta.getZ())/1.5,7,20),gravity=.045;
        var lead=s.throwMotion.clone().multiply(ticks*.35);if(lead.lengthSquared()>2.5*2.5)lead.normalize().multiply(2.5);
        to.add(lead);delta=to.toVector().subtract(from.toVector());
        var velocity=delta.multiply(1/ticks);velocity.setY(velocity.getY()+gravity*(ticks-1)/2);
        BlockDisplay display=s.heldRock==null?rockDisplay(from):s.heldRock;s.heldRock=null;display.teleport(from);
        boulders.add(new Boulder(s,display,from,velocity,clock+70));
        from.getWorld().playSound(from,Sound.ENTITY_IRON_GOLEM_ATTACK,1,.6f);
    }
    private void rockBurst(Boulder b,Player direct){
        Location at=b.at;
        at.getWorld().spawnParticle(Particle.EXPLOSION,at,3,.6,.4,.6,0);
        at.getWorld().spawnParticle(Particle.BLOCK,at,85,1.5,.7,1.5,.13,Material.DEEPSLATE.createBlockData());
        at.getWorld().playSound(at,Sound.ENTITY_GENERIC_EXPLODE,1.3f,.65f);
        furnaceRing(at.clone().subtract(0,.5,0),1.5,Color.fromRGB(255,100,25),true);
        furnaceRing(at.clone().subtract(0,.5,0),3.5,Color.fromRGB(255,210,80),true);
        for(Player p:players())if(p!=direct){
            var box=p.getBoundingBox();double x=Math.clamp(at.getX(),box.getMinX(),box.getMaxX()),y=Math.clamp(at.getY(),box.getMinY(),box.getMaxY()),z=Math.clamp(at.getZ(),box.getMinZ(),box.getMaxZ());
            if(at.distanceSquared(new Location(at.getWorld(),x,y,z))<=3.5*3.5&&clearLine(at,p.getLocation().add(0,.8,0)))hit(p,32,b.owner.mob);
            if(closed)return;
        }
    }
    private void tickBoulders(){
        for(var it=boulders.iterator();it.hasNext();){Boulder b=it.next();
            if(closed||!b.owner.mob.isValid()||spirit(b.owner.mob)==null||clock>=b.until||!b.display.isValid()){
                b.display.remove();it.remove();continue;
            }
            var direction=b.velocity.clone();double length=direction.length();if(length<.001)continue;direction.normalize();
            // Trace the full visible block volume, including its leading corners.
            double travel=length;boolean wall=false;
            for(double x:new double[]{-BOULDER_RADIUS,0,BOULDER_RADIUS})for(double y:new double[]{-BOULDER_RADIUS,0,BOULDER_RADIUS})for(double z:new double[]{-BOULDER_RADIUS,0,BOULDER_RADIUS}){
                Location start=b.at.clone().add(x,y,z);
                if(!start.getBlock().isPassable()){travel=0;wall=true;continue;}
                var collision=start.getWorld().rayTraceBlocks(start,direction,length,FluidCollisionMode.NEVER,true);
                if(collision!=null){travel=Math.min(travel,start.toVector().distance(collision.getHitPosition()));wall=true;}
            }
            Player victim=null;double nearest=travel;
            for(Player p:players()){
                var box=p.getBoundingBox().expand(BOULDER_RADIUS);var contact=box.rayTrace(b.at.toVector(),direction,travel);
                double distance=box.contains(b.at.toVector())?0:contact==null?Double.POSITIVE_INFINITY:b.at.toVector().distance(contact.getHitPosition());
                if(distance<nearest||distance==0){nearest=distance;victim=p;}
            }
            b.at.add(direction.multiply(victim==null?travel:nearest));
            b.display.teleport(b.at);b.velocity.setY(b.velocity.getY()-.045);
            b.at.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME,b.at,5,.3,.3,.3,.01);
            b.at.getWorld().spawnParticle(Particle.BLOCK,b.at,8,.35,.35,.35,0,Material.DEEPSLATE.createBlockData());
            if(victim!=null||wall){
                b.display.remove();it.remove();
                if(victim!=null)hit(victim,48,b.owner.mob);
                if(closed||spirit(b.owner.mob)==null)return;rockBurst(b,victim);if(closed||spirit(b.owner.mob)==null)return;
            }
        }
    }
    private boolean beginAcheron(Spirit s,Player target,List<Player> players){
        if(s.echo)return false;
        if(s.pendingSplit){s.pendingSplit=false;if(hasEchoes(s))return false;s.skill=AcheronSkill.SPLIT;}
        else{
            if(s.mob.getLocation().distanceSquared(target.getLocation())>26*26||!s.mob.hasLineOfSight(target))return false;
            clearSnareMarks(s);
            for(Player p:players)if(clock>=rootImmune.getOrDefault(p.getUniqueId(),0L)&&s.mob.getLocation().distanceSquared(p.getLocation())<=26*26&&s.mob.hasLineOfSight(p))s.snareMarks.add(p.getLocation().clone());
            if(s.snareMarks.isEmpty()){s.snareAt=clock+40;return false;}
            s.skill=AcheronSkill.SNARE;s.snareAt=clock+300;
        }
        s.mark=s.mob.getLocation();s.castStarted=clock;s.castDuration=s.skill==AcheronSkill.SPLIT?28:36;
        s.releaseAt=clock+s.castDuration;s.castAt=Math.max(s.castAt,clock+60);s.mob.setAI(false);s.mob.getPathfinder().stopPathfinding();
        render(s,target,castClip(s,"windup"),20.0/s.castDuration,true);
        if(s.skill==AcheronSkill.SNARE)snareWarning(s);
        for(Player p:players)plugin.message(p,s.skill==AcheronSkill.SPLIT?"\u00a7cAcheron is splitting into two echoes!":"\u00a7cAcheron: escape the large circles before the binding seals close!");
        return true;
    }
    private void release(Spirit s){
        s.castActiveUntil=clock+(s.kind==ArkhaiaWaves.Kind.ACHERON&&s.skill==AcheronSkill.SPIN?38:24);
        int contact=s.kind==ArkhaiaWaves.Kind.ACHERON&&!s.echo&&s.skill!=AcheronSkill.SPIN?1:s.kind==ArkhaiaWaves.Kind.COCYTUS?4:s.kind==ArkhaiaWaves.Kind.BANSHEE_WAIL||s.kind==ArkhaiaWaves.Kind.MALEFEASANCE?1:0;
        if(s.kind==ArkhaiaWaves.Kind.PHLEGETHON&&s.furnace!=FurnaceSkill.SLAM)contact=4;
        s.skillContactAt=clock+contact;
        if(contact==0){s.skillContactAt=0;castImpact(s);}
    }
    private void castImpact(Spirit s){
        if(s.kind==ArkhaiaWaves.Kind.BANSHEE_WAIL){
            center.getWorld().playSound(s.mark,Sound.ENTITY_GHAST_SCREAM,1,.6f);
            center.getWorld().spawnParticle(Particle.SOUL,s.mark.clone().add(0,.3,0),75,3,.3,3,.04);
            for(Player p:players())if(inCircle(p,s.mark,radius(s))&&clearLine(s.mark.clone().add(0,.35,0),p.getLocation().add(0,.8,0))&&soulHit(p,s.mob))souls.curse(s.soul,p);
            return;
        }
        if(s.kind==ArkhaiaWaves.Kind.PHLEGETHON){
            if(s.furnace==FurnaceSkill.KICK){kick(s);return;}
            if(s.furnace==FurnaceSkill.ROCK){throwRock(s);return;}
            clearSlam(s);s.slamStarted=clock;s.slamOrigin=s.mark.clone();
            s.mob.getWorld().spawnParticle(Particle.EXPLOSION,s.mark.clone().add(0,.3,0),2,.4,.2,.4,0);
            s.mob.getWorld().spawnParticle(Particle.BLOCK,s.mark.clone().add(0,.2,0),100,2,.2,2,.15,Material.DEEPSLATE.createBlockData());
            s.mob.getWorld().playSound(s.mark,Sound.ITEM_MACE_SMASH_GROUND_HEAVY,1.6f,.5f);
            s.mob.getWorld().playSound(s.mark,Sound.ENTITY_GENERIC_EXPLODE,1.3f,.55f);
            slamWave(s);
            return;
        }
        if(s.kind==ArkhaiaWaves.Kind.ACHERON){
            if(!s.echo&&s.skill==AcheronSkill.SPLIT){split(s);return;}
            if(!s.echo&&s.skill==AcheronSkill.SNARE){
                for(Location at:s.snareMarks){
                    bindingRing(at,6,true);
                    at.getWorld().spawnParticle(Particle.FLASH,at.clone().add(0,.4,0),1,0,0,0,0,Color.fromRGB(255,65,95),true);
                    at.getWorld().playSound(at,Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE,.8f,.6f);
                    for(Player p:players())if(inCircle(p,at,6)&&clearLine(at.clone().add(0,.35,0),p.getLocation().add(0,.8,0)))root(p,s);
                }
                s.snareBurstUntil=clock+10;return;
            }
            var direction=s.mark.toVector().subtract(s.mob.getLocation().toVector()).setY(0);
            if(direction.lengthSquared()<.01)direction=s.mob.getLocation().getDirection().setY(0);
            direction.normalize();s.chargeX=direction.getX();s.chargeZ=direction.getZ();
            s.spinUntil=clock+24;s.spinVictims.clear();s.mob.setVelocity(new org.bukkit.util.Vector());return;
        }
        if(s.kind==ArkhaiaWaves.Kind.MALEFEASANCE){
            for(int i=0;i<2&&shades.size()<4;i++){
                Mob m=center.getWorld().spawn(s.mob.getLocation().clone().add(i==0?1:-1,1,0),Vex.class,v->{tag(v);v.customName(net.kyori.adventure.text.Component.text("Sentient Darkness"));v.getAttribute(Attribute.MAX_HEALTH).setBaseValue(60);v.setHealth(60);});
                shades.add(new Shade(m,clock+400));
            }
            return;
        }
        if(s.kind==ArkhaiaWaves.Kind.COCYTUS){bowShot(s,s.mark.clone().add(0,.5,0),true);return;}
        if(s.kind==ArkhaiaWaves.Kind.BANSHEE_WAIL)center.getWorld().playSound(s.mark,Sound.ENTITY_GHAST_SCREAM,1,.6f);
        Particle effect=switch(s.kind){case COCYTUS->Particle.SNOWFLAKE;case BANSHEE_WAIL->Particle.SOUL;case ACHERON,STYX->Particle.SWEEP_ATTACK;default->Particle.SOUL_FIRE_FLAME;};
        center.getWorld().spawnParticle(effect,s.mark.clone().add(0,.3,0),65,radius(s)*.5,.2,radius(s)*.5,.03);
        if(s.kind==ArkhaiaWaves.Kind.STYX)center.getWorld().playSound(s.mark,Sound.ITEM_MACE_SMASH_GROUND_HEAVY,1.2f,.6f);
        for(Player p:players())if(inCircle(p,s.mark,radius(s))&&s.mob.hasLineOfSight(p)){
            double amount=switch(s.kind){case PHLEGETHON->maxHealth(p)*.18;case STYX->18;case COCYTUS->6;default->5;};
            if(!hit(p,amount,s.mob))continue;
            switch(s.kind){
                case COCYTUS->{debuff(p,PotionEffectType.SLOWNESS,80,2);p.setVelocity(p.getVelocity().multiply(.15));}
                case STYX->{debuff(p,PotionEffectType.WEAKNESS,120,1);var armor=p.getAttribute(Attribute.ARMOR);
                    armor.removeModifier(armorKey);armor.addTransientModifier(new AttributeModifier(armorKey,-.5,AttributeModifier.Operation.MULTIPLY_SCALAR_1));sundered.put(p.getUniqueId(),clock+120);armorOwners.put(p.getUniqueId(),p);}
                default->{}
            }
        }
        if(s.kind==ArkhaiaWaves.Kind.STYX){s.stunUntil=clock+60;s.mob.getPathfinder().stopPathfinding();}
    }
    void tick(long now){
        if(closed)return;clock=now;List<Player> players=players();tickRoots();souls.tick();
        trials.tick(now,passive?List.of():players);
        for(var kind:demo?List.<ArkhaiaWaves.Kind>of():waves.fill(now,players.size())){Spirit s=new Spirit(kind);spirits.put(kind,s);spawn(s);}
        if(now%4==0)aether.frame(center,85);
        for(Spirit s:allSpirits()){
            if(s.echo&&!s.mob.isValid()){removeSpirit(s);continue;}
            if(!s.mob.isValid()){aerial.remove(s);trials.remove(s);plugin.getLogger().warning("Recovering missing Arkhaia minion "+s.kind);spawn(s);}
            leash(s.mob);
            s.bar.getPlayers().stream().filter(p->!players.contains(p)).toList().forEach(s.bar::removePlayer);if(!s.echo)players.forEach(s.bar::addPlayer);
            if(now%10==0&&!s.echo)s.bar.setTitle(name(s.kind)+" · "+(int)Math.ceil(s.health)+" / "+(int)s.maxHealth()+souls.status(s.soul)+(now<s.stunUntil?" · STUN":""));
            Player target=players.stream().filter(p->!repelled(p,s.mob)).min(Comparator.comparingDouble(p->p.getLocation().distanceSquared(s.mob.getLocation()))).orElse(null);
            if(demo&&passive){
                s.mob.setAI(false);s.mob.getPathfinder().stopPathfinding();
                Player owner=demoOwner==null?null:Bukkit.getPlayer(demoOwner);String pose=demoPose;double rate=1;
                if(pose.equals("idle")&&s.kind==ArkhaiaWaves.Kind.BANSHEE_WAIL||pose.equals("walk")&&s.kind==ArkhaiaWaves.Kind.BANSHEE_WAIL)pose="hover";
                if(pose.equals("cast")){
                    int prepare=s.kind==ArkhaiaWaves.Kind.STYX?50:s.kind==ArkhaiaWaves.Kind.PHLEGETHON?44:s.kind==ArkhaiaWaves.Kind.ACHERON?24:32;
                    int active=s.kind==ArkhaiaWaves.Kind.ACHERON?24:10;
                    long age=(now-demoPoseStarted)%(prepare+active+24);
                    pose=age<prepare?"cast_windup":age<prepare+active?"cast_active":age<prepare+active+14?"cast_recover":"idle";
                    rate=age<prepare?20.0/prepare:1;
                }
                render(s,owner,pose,rate,pose.equals("stun"));identity(s,now);continue;
            }
            // Vanilla goals must not restart a path after the scheduler stopped it.
            s.mob.setAI(target!=null&&now>=s.stunUntil&&s.releaseAt==0&&now>=s.castActiveUntil&&s.attackStarted<0);
            if(target==null){aerial.cancel(s);clearBlink(s);clearSlam(s);clearHeldRock(s);clearSpin(s);s.attackStarted=-1;s.mob.setTarget(null);s.mob.getPathfinder().stopPathfinding();s.releaseAt=0;s.spinUntil=0;s.castActiveUntil=0;s.skillContactAt=0;clearSnareMarks(s);sigils.remove("river:"+s.key());render(s,null,s.kind==ArkhaiaWaves.Kind.BANSHEE_WAIL?"hover":"idle",1,false);continue;}
            identity(s,now);
            if(aerial.step(s,target,players,now))continue;
            if(trials.step(s,target,players,now))continue;
            String soulPose=souls.transition(s.soul);
            if(soulPose!=null){clearBlink(s);sigils.remove("river:"+s.key());s.releaseAt=0;s.skillContactAt=0;s.castActiveUntil=0;s.attackStarted=-1;s.mob.setAI(false);s.mob.getPathfinder().stopPathfinding();render(s,target,soulPose,1,true);continue;}
            if(s.blinkAt>0){blinkStep(s,target);continue;}
            if(s.slamStarted>=0)slamWave(s);
            if(s.snareBurstUntil>0){
                if(now>=s.snareBurstUntil)clearSnareMarks(s);
                else if(now%2==0)for(int i=0;i<s.snareMarks.size();i++){
                    Location at=s.snareMarks.get(i);sigils.circle("snare:"+s.key()+":"+i,sigil(s.kind),at.clone().add(0,.18,0),6,now*.04,false);bindingRing(at,6,true);
                }
            }
            if(now<s.stunUntil&&now>=s.castActiveUntil){s.mob.getPathfinder().stopPathfinding();render(s,target,"stun",1,true);continue;}
            if(s.attackStarted>=0){
                long age=now-s.attackStarted;
                RiverCombatProfile profile=RiverCombatProfile.of(s.kind);
                if(age==profile.hitTick()){
                    attack(s,target);
                }
                if(age<profile.duration()){render(s,target,profile.clip(age),profile.rate(age),true);continue;}
                s.attackStarted=-1;
            }
            if(now>=s.spinUntil)clearSpin(s);
            if(now<s.castActiveUntil){
                if(s.heldRock!=null)heldRock(s,"throw_active",Math.min(.2,(now-(s.castActiveUntil-24))/20.0));
                if(s.skillContactAt>0&&now>=s.skillContactAt){s.skillContactAt=0;castImpact(s);}
                if(now<s.spinUntil)chargeStep(s);
                render(s,target,castClip(s,now<s.castActiveUntil-14?"active":"recover"),1,true);continue;
            }
            if(s.releaseAt>0){
                if(s.kind==ArkhaiaWaves.Kind.BANSHEE_WAIL)curseWarning(s);
                if(s.kind==ArkhaiaWaves.Kind.PHLEGETHON&&s.furnace==FurnaceSkill.ROCK){trackThrow(s);heldRock(s,"throw_windup",Math.min(1,(double)(now-s.castStarted)/s.castDuration));}
                render(s,target,castClip(s,"windup"),20.0/s.castDuration,true);
                s.mob.getPathfinder().stopPathfinding();
                if(now%4==0){if(s.kind==ArkhaiaWaves.Kind.ACHERON&&s.skill==AcheronSkill.SNARE)snareWarning(s);else if(s.kind==ArkhaiaWaves.Kind.PHLEGETHON&&s.furnace==FurnaceSkill.SLAM)slamWarning(s);else sigils.circle("river:"+s.key(),sigil(s.kind),s.mark.clone().add(0,.12,0),radius(s),now*.012,false);}
                if(s.kind==ArkhaiaWaves.Kind.PHLEGETHON&&now%4==0){
                    s.mob.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME,s.mob.getLocation().add(0,9.8,0),12,.8,.4,.5,.01);
                }
                if(now>=s.releaseAt){s.releaseAt=0;clearSlamWarning(s);sigils.remove("river:"+s.key());render(s,target,castClip(s,"active"),1,true);release(s);}
            }else if(s.kind==ArkhaiaWaves.Kind.ACHERON&&!s.echo&&(s.pendingSplit||now>=s.snareAt)&&beginAcheron(s,target,players)){
                // Threshold summoning and the wide snare own a separate ritual pose.
            }else if(beginBlink(s,target)){
                // The warning commits a safe landing spot; arrival itself deals no damage.
            }else if(now>=s.castAt&&engagementDistance(s,target)<=castRange(s)*castRange(s)&&s.mob.hasLineOfSight(target)
                    &&!(s.kind==ArkhaiaWaves.Kind.BANSHEE_WAIL&&target.getY()>center.getY()+3)){
                s.skill=AcheronSkill.SPIN;
                if(s.kind==ArkhaiaWaves.Kind.PHLEGETHON){
                    double distance=s.mob.getLocation().distanceSquared(target.getLocation());
                    int choice=s.furnaceTurns++%3;
                    s.furnace=target.getY()>center.getY()+3||distance>7*7?FurnaceSkill.ROCK:choice==1&&distance<=5*5?FurnaceSkill.KICK:choice==2?FurnaceSkill.ROCK:FurnaceSkill.SLAM;
                }
                s.mob.setRotation((float)Math.toDegrees(Math.atan2(s.mob.getX()-target.getX(),target.getZ()-s.mob.getZ())),0);
                s.mark=switch(s.kind){case PHLEGETHON->impactPoint(s,target,3.2);case STYX->localPoint(s,.8,8.4);case ACHERON,COCYTUS->target.getLocation().clone();default->s.mob.getLocation();};s.mark.setY(center.getY());
                if(s.kind==ArkhaiaWaves.Kind.BANSHEE_WAIL){s.curseTarget=target.getUniqueId();s.mark=target.getLocation();s.mark.setY(center.getY());}
                if(s.kind==ArkhaiaWaves.Kind.PHLEGETHON){
                    if(s.furnace==FurnaceSkill.ROCK){s.mark=target.getLocation().clone();s.throwTarget=target.getUniqueId();s.throwPrevious=s.mark.clone();s.throwMotion.zero();}
                    else if(s.furnace==FurnaceSkill.KICK)s.mark=localPoint(s,.7,2.5);
                }
                s.castStarted=now;s.castDuration=s.kind==ArkhaiaWaves.Kind.STYX?50:s.kind==ArkhaiaWaves.Kind.PHLEGETHON?44:s.kind==ArkhaiaWaves.Kind.ACHERON?24:32;
                if(s.kind==ArkhaiaWaves.Kind.PHLEGETHON)s.castDuration=s.furnace==FurnaceSkill.SLAM?34:s.furnace==FurnaceSkill.KICK?22:28;
                s.releaseAt=now+s.castDuration;s.castAt=now+castCooldown(s);
                s.mob.setAI(false);render(s,target,castClip(s,"windup"),20.0/s.castDuration,true);
                s.mob.getWorld().playSound(s.mob.getLocation(),Sound.BLOCK_AMETHYST_BLOCK_RESONATE,.8f,.7f);
                for(Player p:players)plugin.message(p,"\u00a75"+name(s.kind)+" \u00a7dis casting - dodge the marked area!");
            }else{
                RiverCombatProfile profile=RiverCombatProfile.of(s.kind);
                double distance=engagementDistance(s,target);
                if(profile.standOff()>0&&distance<=profile.standOff()*profile.standOff()&&s.mob.hasLineOfSight(target)){
                    s.mob.setAI(false);s.mob.getPathfinder().stopPathfinding();
                }else if(now%10==0&&distance>6){
                    s.mob.setTarget(target);
                    // Short paths remain reachable even when the target is across the arena.
                    Location destination=target.getLocation();destination.setY(center.getY());
                    var delta=destination.toVector().subtract(s.mob.getLocation().toVector());
                    if(delta.lengthSquared()>12*12)destination=s.mob.getLocation().add(delta.normalize().multiply(12));
                    s.mob.getPathfinder().moveTo(destination,.8);
                }
                boolean basic=s.kind==ArkhaiaWaves.Kind.COCYTUS||s.kind==ArkhaiaWaves.Kind.MALEFEASANCE||s.kind==ArkhaiaWaves.Kind.BANSHEE_WAIL&&(s.castAt-now>24||target.getY()>center.getY()+3);
                if(basic&&now>=s.attackAt&&distance<=profile.reach()*profile.reach()&&s.mob.hasLineOfSight(target)){s.attackAt=now+profile.cooldown();s.attackStarted=now;s.aim=target.getEyeLocation().clone();s.mob.getPathfinder().stopPathfinding();s.mob.setAI(false);render(s,target,"attack_windup",profile.rate(0),true);}
                else render(s,target,null,1,false);
            }
        }
        tickBolts();tickBoulders();
        for(var it=frostArrows.values().iterator();it.hasNext();){FrostArrow shot=it.next();
            if(!shot.arrow().isValid()||shot.arrow().isInBlock()||now>=shot.until()||!shot.owner().isValid()||spirit(shot.owner())==null){shot.arrow().remove();it.remove();continue;}
            shot.arrow().getWorld().spawnParticle(Particle.SNOWFLAKE,shot.arrow().getLocation(),2,.08,.08,.08,0);
        }
        for(var it=shades.iterator();it.hasNext();){Shade s=it.next();if(!s.mob().isValid()||now>=s.until()){s.mob().remove();it.remove();continue;}
            leash(s.mob());Player p=players.stream().min(Comparator.comparingDouble(x->x.getLocation().distanceSquared(s.mob().getLocation()))).orElse(null);
            if(p==null){s.contactAt=0;continue;}
            if(now%10==0&&s.contactAt==0)s.mob().getPathfinder().moveTo(p,.8);
            if(s.contactAt>0){
                s.mob().getPathfinder().stopPathfinding();
                if(now==s.contactAt-2)s.mob().swingMainHand();
                if(now>=s.contactAt){
                    Location from=s.mob().getEyeLocation();var direction=s.aim.toVector().subtract(from.toVector());
                    if(direction.lengthSquared()>.001){direction.normalize();var box=p.getBoundingBox().expand(.25);
                        if((box.contains(from.toVector())||box.rayTrace(from.toVector(),direction,1.8)!=null)&&clearLine(from,p.getEyeLocation()))hit(p,3,s.mob());}
                    ((Vex)s.mob()).setCharging(false);s.mob().setAI(true);s.contactAt=0;s.attackAt=now+40;
                }else if(now%3==0)s.mob().getWorld().spawnParticle(Particle.WITCH,s.mob().getLocation().add(0,.5,0),5,.2,.2,.2,0);
            }else if(now>=s.attackAt&&p.getLocation().distanceSquared(s.mob().getLocation())<2.5*2.5){
                s.contactAt=now+14;s.aim=p.getLocation().add(0,1,0);s.mob().setAI(false);((Vex)s.mob()).setCharging(true);
                s.mob().setRotation((float)Math.toDegrees(Math.atan2(s.mob().getX()-p.getX(),p.getZ()-s.mob().getZ())),0);s.mob().getPathfinder().stopPathfinding();
            }
        }
        for(var it=sundered.entrySet().iterator();it.hasNext();){var entry=it.next();Player p=Bukkit.getPlayer(entry.getKey());
            if(p==null||!inside.test(p)||now>=entry.getValue()){Player owner=armorOwners.remove(entry.getKey());if(owner!=null)owner.getAttribute(Attribute.ARMOR).removeModifier(armorKey);it.remove();}}
    }
    static String sigil(ArkhaiaWaves.Kind kind){return "river_"+kind.name().toLowerCase(Locale.ROOT);}
    static Color color(ArkhaiaWaves.Kind kind){return Color.fromRGB(switch(kind){case ACHERON->0xf54268;case PHLEGETHON->0xffa44a;case COCYTUS->0x64e0ff;case BANSHEE_WAIL->0x7cf4ce;case MALEFEASANCE->0xbf77ff;case STYX->0xeec66d;});}
    void demo(UUID owner,boolean passive){this.demo=true;this.passive=passive;demoOwner=owner;}
    void waiting(){demo=true;passive=true;demoOwner=null;}
    void awaken(){passive=false;for(Spirit s:spirits.values())if(s.kind==ArkhaiaWaves.Kind.BANSHEE_WAIL)s.soul=souls.bind(s.soul,s.mob,false);}
    boolean hitTrial(Player p,double amount,Mob source){return hit(p,amount,source);}
    boolean styxHit(Player p,double amount,Spirit source,boolean execution){
        if(closed||!inside.test(p)||!source.mob.isValid())return false;
        applying=true;try{return BossDamage.styx(p,amount*ritualDamage,source.mob,execution);}finally{applying=false;}
    }
    void sunderTrial(Player p){
        var armor=p.getAttribute(Attribute.ARMOR);if(armor==null)return;
        armor.removeModifier(armorKey);armor.addTransientModifier(new AttributeModifier(armorKey,-.5,AttributeModifier.Operation.MULTIPLY_SCALAR_1));
        sundered.put(p.getUniqueId(),clock+100);armorOwners.put(p.getUniqueId(),p);
    }
    private boolean repelled(Player player,LivingEntity mob){
        var addon=Bukkit.getPluginManager().getPlugin("advance-magic");
        return addon instanceof com.example.advancemagic.AdvanceMagicPlugin magic&&magic.isEnabled()
            &&magic.armorCombat()!=null&&magic.armorCombat().repels(player,mob);
    }
    void renderTrial(Spirit s,Player target,String clip,double rate){render(s,target,clip,rate,true);}
    boolean aerialReady(Spirit s,long now){return !s.echo&&now>=s.stunUntil&&s.releaseAt==0&&now>=s.castActiveUntil&&s.attackStarted<0&&s.blinkAt==0&&s.slamStarted<0
        &&!s.pendingSplit&&(s.kind!=ArkhaiaWaves.Kind.STYX||trials.aerialReady(s,now));}
    boolean aerialHit(Spirit s,Player p,long now){return s.kind==ArkhaiaWaves.Kind.STYX?trials.aerialStrike(s,p,now):hit(p,18,s.mob);}
    private double engagementDistance(Spirit s,Player p){
        if(s.kind==ArkhaiaWaves.Kind.PHLEGETHON||s.kind==ArkhaiaWaves.Kind.BANSHEE_WAIL){double dx=s.mob.getX()-p.getX(),dz=s.mob.getZ()-p.getZ();return dx*dx+dz*dz;}
        return s.mob.getLocation().distanceSquared(p.getLocation());
    }
    void shotTrial(Spirit s,Location aim){bowShot(s,aim,false);}
    void boltTrial(Spirit s,Location aim){s.aim=aim.clone();attack(s,null);}
    boolean clearTrial(Location a,Location b){return clearLine(a,b);}
    List<Player> trialPlayers(){return players();}
    void initializeClock(long now){clock=now;}
    void demoPose(String pose){demoPose=pose;demoPoseStarted=clock;}
    /** Arkhaia's river ritual summons spirits with this share of a sanctuary boss's HP and damage. */
    private double ritualHealth=1,ritualDamage=1;
    void ritualScale(double health,double damage){ritualHealth=health;ritualDamage=damage;}
    boolean ritual(){return ritualHealth<1||ritualDamage<1;}
    void summon(ArkhaiaWaves.Kind kind,Location at){Spirit s=new Spirit(kind);s.healthScale=ritualHealth;s.health*=ritualHealth;s.home=at.clone();spirits.put(kind,s);spawn(s);}
    private void visual(Spirit s){
        // R4 removes renderers on its worker. Give that worker time before reusing the same base UUID.
        if(s.visual!=null&&!s.visual.valid()){s.visual.close();s.visual=null;s.nextVisual=clock+10;}
        if(s.visual!=null||clock<s.nextVisual||plugin.arkhaiaModels()==null
            ||!plugin.arkhaiaModels().ready(model(s)))return;
        s.nextVisual=clock+200;
        try{
            s.visual=new RiverVisual(plugin,s.mob,s.kind,s.echo);
            if(s.kind==ArkhaiaWaves.Kind.MALEFEASANCE&&s.resurrected)s.visual.switchModel(model(s));
            s.poseInitialized=false;
        }
        catch(RuntimeException|LinkageError error){plugin.getLogger().log(java.util.logging.Level.WARNING,"River model unavailable: "+s.kind,error);}
    }
    private void render(Spirit s,Player target,String clip,double rate,boolean locked){
        visual(s);
        if(clip==null){double speed=s.visual==null?0:s.visual.speed();clip=s.kind==ArkhaiaWaves.Kind.BANSHEE_WAIL?"hover":speed>.008?"walk":"idle";if(clip.equals("walk"))rate=Math.clamp(speed/.12,.35,2);}
        if(!s.poseInitialized||!s.pose.equals(clip)){s.pose=clip;s.poseAt=clock;s.poseElapsed=0;s.poseInitialized=true;}
        else s.poseElapsed+=rate/20;
        s.poseRate=rate;
        if(s.visual==null)return;
        s.visual.frame(target,clip,rate,locked);
    }
    private void identity(Spirit s,long now){
        if(!s.echo&&now%4==0){
            boolean adapted=s.kind==ArkhaiaWaves.Kind.PHLEGETHON&&s.adaptedHits>0;
            Location halo=s.mob.getLocation().add(0,RiverVisual.haloHeight(s.kind),0);
            sigils.circle("identity:"+s.key(),sigil(s.kind),halo,RiverVisual.haloRadius(s.kind),now*(adapted?.025:.006),false);
            if(adapted)for(int i=0;i<8;i++){
                double angle=now*.08+i*Math.PI/4;
                s.mob.getWorld().spawnParticle(Particle.DUST,halo.clone().add(Math.cos(angle)*2.8,0,Math.sin(angle)*2.8),1,0,0,0,0,new Particle.DustOptions(Color.fromRGB(255,164,74),1.3f),true);
            }
        }
    }
    private void leash(Mob mob){
        Location at=mob.getLocation();
        if(at.getWorld()!=center.getWorld()||at.getY()<center.getY()-.75||at.getY()>center.getY()+7||Math.hypot(at.getX()-center.getX(),at.getZ()-center.getZ())>36){
            mob.getPathfinder().stopPathfinding();mob.setTarget(null);mob.teleport(center.clone().add(0,.2,0));mob.setVelocity(new org.bukkit.util.Vector());
        }
    }
    void removed(EntityRemoveEvent event){Spirit s=spirit(event.getEntity());if(s!=null&&!closed&&!clearing)
        plugin.getLogger().warning("River minion removed: "+s.kind+" cause="+event.getCause()+" hp="+s.health+"; retaining encounter state");}
    void clear(){
        clearing=true;
        aerial.clear();
        trials.clear();
        souls.close();
        for(Spirit s:allSpirits()){clearBlink(s);clearSlam(s);clearHeldRock(s);s.bar.removeAll();if(s.visual!=null)s.visual.close();s.mob.remove();}spirits.clear();echoes.clear();waves.clear();clearRoots(null);rootImmune.clear();
        shades.forEach(s->s.mob().remove());shades.clear();bolts.clear();boulders.forEach(b->b.display.remove());boulders.clear();
        frostArrows.values().forEach(s->s.arrow().remove());frostArrows.clear();
        armorOwners.values().forEach(p->p.getAttribute(Attribute.ARMOR).removeModifier(armorKey));armorOwners.clear();sundered.clear();sigils.close();clearing=false;
    }
    @Override public void close(){if(closed)return;closed=true;clear();}
}
