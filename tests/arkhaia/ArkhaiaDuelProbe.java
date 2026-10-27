import com.example.voidscape.VoidscapePlugin;
import com.example.voidscape.boss.JudgePackService;
import com.example.voidscape.boss.ArkhaiaPattern;
import com.example.voidscape.boss.ArkhaiaSovereign;
import com.example.voidscape.world.BossStructureLayout;
import com.example.voidscape.compat.LevelledMobsCompat;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.server.network.*;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.BlockFace;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.entity.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import java.net.InetSocketAddress;
import java.nio.file.*;
import java.util.*;

/** The production Arkhaia duel on isolated Paper with official ModelEngine: summon, posture, ritual, duel, Eclipse, regroup and cleanup. */
public class ArkhaiaDuelProbe extends JavaPlugin implements Listener {
    VoidscapePlugin garden;World world;Player player;ServerPlayer actor;BossStructureLayout.Site site;int checks;
    int swordEvents;
    @EventHandler(priority=EventPriority.HIGH)
    public void sword(EntityDamageByEntityEvent event){
        if(player!=null&&event.getEntity()==player&&event.getDamager().getScoreboardTags().contains("arkhaia"))swordEvents++;
    }
    void check(boolean ok,String label){if(!ok)throw new AssertionError(label);checks++;getLogger().info("PASS: "+label);}
    @Override public void onEnable(){Bukkit.getScheduler().runTaskLater(this,()->{try{run();}catch(Throwable e){finish(e);}},Files.exists(Path.of("seraphon-native.flag"))?220:20);}
    List<Entity> parts(){return world.getEntities().stream().filter(e->e.getPersistentDataContainer().has(garden.key("arkhaia_boss"),PersistentDataType.BYTE)).toList();}
    void click(int x,int z,EquipmentSlot hand){
        var event=new PlayerInteractEvent(player,Action.RIGHT_CLICK_BLOCK,player.getInventory().getItem(hand),world.getBlockAt(site.x()+x,108,site.z()+z),BlockFace.UP,hand);
        Bukkit.getPluginManager().callEvent(event);
    }
    void accept(){Bukkit.getPluginManager().callEvent(new PlayerResourcePackStatusEvent(player,JudgePackService.PACK_ID,PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED));}
    boolean watching;double farthest,fastest,fastestCharge;int blinks,duels;Location lastBody;
    void advance(int n){for(int i=0;i<n;i++){garden.worldBoss().tickArkhaia();if(watching)watch();}}
    /** Track the body every tick: distance from the arena center and horizontal travel per tick. */
    void watch(){try{
        if(active().isEmpty()){lastBody=null;return;}
        Location b=body().clone();var a=act();Location center=(Location)get(runState(),"center");
        farthest=Math.max(farthest,Math.hypot(b.getX()-center.getX(),b.getZ()-center.getZ()));
        if(a.duel()&&a.state()==ArkhaiaSovereign.State.WINDUP&&a.index()==0&&a.age()==0)duels++;
        if(lastBody!=null){double d=Math.hypot(b.getX()-lastBody.getX(),b.getZ()-lastBody.getZ());
            if(d>2)blinks++;else if(a.state()==ArkhaiaSovereign.State.ACTIVE&&a.move()==ArkhaiaSovereign.Move.CHARGE)fastestCharge=Math.max(fastestCharge,d);else fastest=Math.max(fastest,d);}
        lastBody=b;
    }catch(Exception e){throw new RuntimeException(e);}}
    /** Banshee's Soul Veil: shatter every Soul Anchor first, exactly as a player must in her sanctuary. */
    void breakAnchors()throws Exception{
        Object souls=get(get(runState(),"minions"),"souls");
        for(Object state:new ArrayList<>((Collection<?>)get(souls,"states")))for(Object anchor:new ArrayList<>((List<?>)get(state,"anchors"))){
            Slime receiver=(Slime)get(anchor,"receiver");Location at=receiver.getLocation().clone().add(0,0,1.5);
            at.setDirection(receiver.getBoundingBox().getCenter().subtract(at.toVector().add(new org.bukkit.util.Vector(0,player.getEyeHeight(),0))));
            player.teleport(at);receiver.damage(1000,player);advance(1);
        }
    }
    Map<?,?> active()throws Exception{return (Map<?,?>)get(manager(),"active");}
    Object manager()throws Exception{return get(garden.worldBoss(),"arkhaia");}
    Object runState()throws Exception{return ((Map<?,?>)get(manager(),"active")).values().iterator().next();}
    ArkhaiaSovereign boss()throws Exception{return (ArkhaiaSovereign)get(runState(),"sovereign");}
    ArkhaiaSovereign.Act act()throws Exception{return (ArkhaiaSovereign.Act)get(runState(),"act");}
    long now()throws Exception{return (long)get(manager(),"tick");}
    double hp()throws Exception{return boss().health();}
    Object get(Object o,String name)throws Exception{var f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
    void put(Object o,String name,Object value)throws Exception{var f=o.getClass().getDeclaredField(name);f.setAccessible(true);f.set(o,value);}
    Slime target(){return (Slime)parts().stream().filter(Slime.class::isInstance).findFirst().orElseThrow();}
    Location body()throws Exception{return (Location)get(runState(),"position");}
    /** Stand forward (positive) or behind (negative) the body, aiming at its torso. */
    void position(double forward)throws Exception{Location b=body();var dir=b.getDirection().setY(0).normalize();Location at=b.clone().add(dir.multiply(forward));at.setY(109);
        Location aim=b.clone().add(0,3,0);at.setDirection(aim.toVector().subtract(at.toVector().add(new org.bukkit.util.Vector(0,player.getEyeHeight(),0))));player.teleport(at);}
    void cooldown()throws Exception{((Map<?,?>)get(runState(),"hits")).clear();}
    void melee(double amount)throws Exception{cooldown();target().damage(amount,player);}
    void magic(double amount){((com.example.advancemagic.AdvanceMagicPlugin)Bukkit.getPluginManager().getPlugin("advance-magic")).context().damage(player,target(),amount,org.bukkit.damage.DamageType.MAGIC);}
    void burst(double amount)throws Exception{cooldown();magic(amount);}
    void arrow(){var arrow=world.spawnArrow(player.getEyeLocation(),new org.bukkit.util.Vector(0,0,1),1,0);arrow.setShooter(player);
        target().damage(100,org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.ARROW).withCausingEntity(player).withDirectEntity(arrow).build());arrow.remove();}
    void vulnerable(){player.setInvulnerable(false);player.setHealth(player.getAttribute(Attribute.MAX_HEALTH).getValue());player.setAbsorptionAmount(0);player.setNoDamageTicks(0);}
    Map<?,?> spirits()throws Exception{return (Map<?,?>)get(get(runState(),"minions"),"spirits");}
    void run()throws Exception{
        garden=(VoidscapePlugin)Bukkit.getPluginManager().getPlugin("Evergarden");check(garden!=null&&garden.isEnabled(),"release boots");world=garden.world();
        site=garden.bossStructures().nearest(BossStructureLayout.Kind.ARKHAIA,0,0,40);check(site!=null,"real Arkhaia site found");
        for(int x=(site.x()-48)>>4;x<=(site.x()+48)>>4;x++)for(int z=(site.z()-48)>>4;z<=(site.z()+48)>>4;z++)world.addPluginChunkTicket(x,z,this);
        // Prepare only the ritual area; placement history and site lookup remain production code.
        for(int x=-6;x<=6;x++)for(int z=-6;z<=6;z++)for(int y=109;y<=120;y++)world.getBlockAt(site.x()+x,y,site.z()+z).setType(Material.AIR,false);
        for(int x=-5;x<=5;x++)for(int z=-5;z<=5;z++)world.getBlockAt(site.x()+x,108,site.z()+z).setType(Material.RED_GLAZED_TERRACOTTA,false);
        var server=MinecraftServer.getServer();var level=((CraftWorld)world).getHandle();var profile=new GameProfile(UUID.randomUUID(),"ArkhaiaTester");
        actor=new ServerPlayer(server,level,profile,ClientInformation.createDefault());
        var connection=new Connection(PacketFlow.SERVERBOUND);connection.channel=new EmbeddedChannel();connection.address=new InetSocketAddress("127.0.0.1",1);
        actor.connection=new ServerGamePacketListenerImpl(server,connection,actor,CommonListenerCookie.createInitial(profile,false)){
            @Override public boolean hasClientLoaded(){return true;}
        };
        actor.setPos(site.x()+.5,109,site.z()+6.5);server.getPlayerList().getPlayers().add(actor);server.getPlayerList().getPlayersByUUID().put(profile.id(),actor);level.addNewPlayer(actor);
        player=actor.getBukkitEntity();player.setGravity(false);player.setInvulnerable(true);player.setGameMode(GameMode.SURVIVAL);
        Bukkit.getPluginManager().registerEvents(this,this);world.setDifficulty(Difficulty.NORMAL);
        click(0,0,EquipmentSlot.HAND);check(parts().isEmpty(),"empty hand denied");
        player.getInventory().setItemInMainHand(new ItemStack(Material.NETHERITE_INGOT));click(0,0,EquipmentSlot.HAND);check(parts().isEmpty(),"ingot denied");
        player.getInventory().setItemInMainHand(new ItemStack(Material.NETHERITE_BLOCK,3));
        click(0,0,EquipmentSlot.HAND);check(parts().isEmpty(),"unaccepted Java pack denied");accept();
        click(5,0,EquipmentSlot.HAND);check(parts().isEmpty(),"red block outside center denied");
        player.getInventory().setItemInOffHand(new ItemStack(Material.NETHERITE_BLOCK));click(0,0,EquipmentSlot.OFF_HAND);check(parts().isEmpty(),"offhand denied");
        check(garden.arkhaiaModels()!=null&&garden.arkhaiaModels().ready("arkhaia"),"official ModelEngine imported the Arkhaia rig");
        click(0,0,EquipmentSlot.HAND);check(parts().size()==2,"ordinary player summons hitbox and Bedrock carrier");
        check(hp()==7000&&boss().maxHealth()==7000,"solo summon has 7000 health");check(player.getInventory().getItemInMainHand().getAmount()==2,"summon spends one netherite block");
        var initial=parts();click(1,0,EquipmentSlot.HAND);check(parts().equals(initial),"duplicate summon denied");
        check(LevelledMobsCompat.isEvergardenMob(target())&&target().getScoreboardTags().contains("evergarden_unique"),"unique and LM ignore markers");
        advance(30);check(get(runState(),"rig")!=null,"encounter attaches its ModelEngine rig");
        check(spirits().isEmpty(),"phase one is a pure duel: no automatic spirits");
        mechanics();
        garden.worldBoss().close();check(parts().isEmpty(),"shutdown cleans encounter");finish(null);
    }
    void mechanics()throws Exception{
        advance(2);position(2.8);double before=hp(),posture=boss().posture();melee(100);
        check(Math.abs(hp()-(before-100))<1e-6,"frontal melee deals full damage (no half-damage gate)");
        check(boss().posture()>posture+11,"melee builds the posture gauge");
        advance(11);position(-5);before=hp();burst(100);check(Math.abs(hp()-(before-100))<1e-6,"rear wand damage counts in full");
        advance(11);position(5);before=hp();cooldown();arrow();check(Math.abs(hp()-(before-100))<1e-6,"arrows damage Arkhaia");
        advance(11);position(12);before=hp();cooldown();
        Location launch=player.getEyeLocation();var velocity=target().getLocation().add(0,1,0).toVector().subtract(launch.toVector()).normalize().multiply(3);
        var flying=world.spawnArrow(launch,velocity,3,0);flying.setShooter(player);flying.setGravity(false);
        for(int i=0;i<8&&flying.isValid();i++)((org.bukkit.craftbukkit.entity.CraftArrow)flying).getHandle().tick();flying.remove();
        check(hp()<before,"actual moving arrow collides with and damages hitbox");
        player.teleport(new Location(world,site.x()+55,109,site.z()));before=hp();cooldown();arrow();check(hp()==before,"outside shooter cannot damage boss");
        // Posture break: 14% of max health in frontal hits, then double damage.
        position(2.8);put(runState(),"emptySince",-1L);
        for(int i=0;i<30&&!boss().broken();i++){advance(11);position(-5);burst(150);}
        advance(1);check(boss().broken()&&act().state()==ArkhaiaSovereign.State.BROKEN,"posture gauge fills into Broken (hp="+hp()+" posture="+boss().posture()+" state="+boss().state()+" act="+act()+")");
        position(-5);before=hp();burst(100);check(Math.abs(hp()-(before-200))<1e-6,"broken Arkhaia takes double damage");
        advance(ArkhaiaSovereign.BROKEN+2);check(!boss().broken()&&boss().posture()==0,"break ends and resets posture");
        // Phase two: the burst stops at 65%, then the river ritual.
        // Pin the first ritual to Banshee, the spirit with an extra defence (Soul Veil).
        @SuppressWarnings("unchecked") var recent=(Set<com.example.voidscape.boss.ArkhaiaWaves.Kind>)get(runState(),"lastRitual");
        for(var kind:com.example.voidscape.boss.ArkhaiaWaves.Kind.values())if(kind!=com.example.voidscape.boss.ArkhaiaWaves.Kind.BANSHEE_WAIL)recent.add(kind);
        position(-5);burst(99999);check(Math.abs(hp()-7000*.65)<1e-6&&boss().phase()==2,"large burst stops at the phase boundary");
        advance(1);check(act().state()==ArkhaiaSovereign.State.TRANSITION,"phase two opens with a transition");
        before=hp();burst(500);check(hp()==before,"transition is invulnerable");
        advance(ArkhaiaSovereign.TRANSITION+2);check(act().state()==ArkhaiaSovereign.State.RITUAL&&spirits().size()==1&&spirits().containsKey(com.example.voidscape.boss.ArkhaiaWaves.Kind.BANSHEE_WAIL),"solo ritual summons one tethered spirit (Banshee)");
        check(Math.abs((double)get(spirits().get(com.example.voidscape.boss.ArkhaiaWaves.Kind.BANSHEE_WAIL),"health")-250)<1e-6,"ritual spirits are summoned at half a sanctuary boss's HP (Banshee 250 of 500)");
        before=hp();burst(500);check(hp()==before,"rose barrier blocks damage during the ritual");
        Object spirit=spirits().values().iterator().next();Mob mob=(Mob)get(spirit,"mob");
        // Each river keeps its own defences (reflect stance, ghost form); keep striking like a player would.
        for(int i=0;i<40&&!spirits().isEmpty();i++){if(!mob.isValid()){advance(2);continue;}
            breakAnchors();
            player.teleport(mob.getLocation().clone().add(0,0,2.5).setDirection(new org.bukkit.util.Vector(0,-.3,-2.5)));mob.damage(10000,player);advance(5);}
        advance(2);
        check(spirits().isEmpty()&&boss().broken(),"killing every ritual spirit breaks Arkhaia (spirits="+spirits().size()+" kind="+get(spirit,"kind")+" hp="+get(spirit,"health")+" state="+boss().state()+")");
        advance(ArkhaiaSovereign.RITUAL_BROKEN+2);
        // The second ritual at 50%; letting it time out empowers the boss.
        position(-5);burst(99999);check(Math.abs(hp()-boss().maxHealth()*.5)<1e-6&&boss().secondRitual(),"second ritual stops the burst at 50%");
        advance(2);check(act().state()==ArkhaiaSovereign.State.RITUAL&&spirits().size()==1,"second ritual summons a new spirit");
        for(int i=0;i<ArkhaiaSovereign.RITUAL_LIMIT+5;i++){player.teleport(new Location(world,site.x()+.5,109,site.z()+30.5));advance(1);}
        check(spirits().isEmpty()&&!((Set<?>)get(runState(),"blessings")).isEmpty(),"ritual timeout absorbs the surviving spirit as a blessing");
        // Phase three: the execution duel.
        advance(12);position(-5);burst(99999);advance(1);check(boss().phase()==3&&((Set<?>)get(runState(),"blessings")).isEmpty(),"phase three at 35% clears blessings");
        advance(ArkhaiaSovereign.TRANSITION+2);boss().schedule(now(),0,9999);
        player.getAttribute(Attribute.MAX_HEALTH).setBaseValue(200);
        boolean duel=false,struck=false;
        for(int i=0;i<200&&!struck;i++){
            var a=act();duel|=a.duel()&&get(runState(),"duelist")!=null;
            var strike=(ArkhaiaPattern.Hazard)get(runState(),"strike");
            if(a.state()==ArkhaiaSovereign.State.WINDUP&&(a.move()==ArkhaiaSovereign.Move.SWEEP_LEFT||a.move()==ArkhaiaSovereign.Move.SWEEP_RIGHT)&&a.age()>=a.duration()-2&&strike!=null){
                double side=strike.angle()+Math.signum(strike.halfWidth())*Math.PI/2;
                player.teleport(new Location(world,strike.x()+Math.cos(side)*5,109,strike.z()+Math.sin(side)*5));vulnerable();
                advance(a.duration()-a.age()+5);struck=player.getHealth()<200;player.setInvulnerable(true);continue;
            }
            advance(1);
        }
        check(duel,"phase three challenges the player to an execution duel");
        check(struck&&player.getHealth()<=200-200*.55+1,"duel sweep hits for 55% of max health on its marked side");
        boolean parried=false;
        for(int i=0;i<200&&!parried;i++){var a=act();
            if(a.state()==ArkhaiaSovereign.State.WINDUP&&a.move()==ArkhaiaSovereign.Move.EXECUTE&&a.age()>=a.duration()-ArkhaiaSovereign.flash(ArkhaiaSovereign.Move.EXECUTE,3)+1){
                position(2.8);melee(50);parried=boss().broken();break;}
            advance(1);
        }
        check(parried,"striking the execution's gold flash breaks Arkhaia outright");
        advance(ArkhaiaSovereign.RITUAL_BROKEN+2);
        // Styx Eclipse stays lethal; a Totem still saves.
        player.getAttribute(Attribute.MAX_HEALTH).setBaseValue(20);player.getInventory().setItemInOffHand(new ItemStack(Material.TOTEM_OF_UNDYING));
        boss().schedule(now(),9999,0);boolean eclipse=false;
        for(int i=0;i<200&&!eclipse;i++){advance(1);eclipse=act().move()==ArkhaiaSovereign.Move.ECLIPSE;}
        check(eclipse&&act().duration()>=120,"Eclipse warns for at least six seconds");
        List<?> pending=(List<?>)get(runState(),"pending");check(!pending.isEmpty(),"Eclipse lanes are scheduled with the warning");
        var lane=(ArkhaiaPattern.Hazard)get(pending.get(0),"hazard");long fire=(long)get(pending.get(0),"fire");
        while(now()<fire-1){player.teleport(new Location(world,lane.x(),109,lane.z()));player.setInvulnerable(true);advance(1);}
        vulnerable();advance(2);
        check(!player.isDead()&&player.getInventory().getItemInOffHand().getType()!=Material.TOTEM_OF_UNDYING,"lethal Eclipse consumes a Totem and preserves the player");
        player.setInvulnerable(true);
        // Two minutes of real phase-three combat: the player keeps moving around the arena.
        player.getInventory().setItemInOffHand(null);player.getAttribute(Attribute.MAX_HEALTH).setBaseValue(200);
        boss().schedule(now(),40,1200);var random=new Random(7);Location arena=(Location)get(runState(),"center");
        watching=true;farthest=fastest=fastestCharge=0;blinks=duels=0;lastBody=null;
        for(int i=0;i<2400;i++){
            if(i%60==0){double a=random.nextDouble(Math.PI*2),d=8+random.nextDouble(26);
                player.teleport(new Location(world,arena.getX()+Math.cos(a)*d,109,arena.getZ()+Math.sin(a)*d));}
            player.setInvulnerable(true);advance(1);
            if(boss().broken()||act().state()==ArkhaiaSovereign.State.STAGGER)continue;
        }
        watching=false;
        check(boss().phase()==3,"soak stayed in phase three");
        check(farthest<=36.5,"Arkhaia never leaves the arena leash in phase three (farthest "+farthest+")");
        check(fastest<=.35,"walking and sword lunges stay slower than a sprint (fastest "+fastest+" blocks/tick)");
        check(fastestCharge<=1.3,"the telegraphed charge keeps its fixed speed ("+fastestCharge+")");
        check(duels>=2&&duels<=3&&blinks<=duels,"the only teleports are duel openings ("+blinks+" blinks, "+duels+" duels)");
        // Defeat, then resummon to check the regroup and the return grace.
        advance(200);position(-3);burst(99999);advance(1);
        check(parts().isEmpty()&&((Map<?,?>)get(manager(),"tickets")).isEmpty(),"defeat removes carriers and releases chunk tickets");
        String id=site.x()+"_"+site.z();check(world.getPersistentDataContainer().has(garden.key("arkhaia_ready_"+id),PersistentDataType.LONG),"victory sets the altar cooldown");
        world.getPersistentDataContainer().remove(garden.key("arkhaia_ready_"+id));
        player.getInventory().setItemInMainHand(new ItemStack(Material.NETHERITE_BLOCK));player.teleport(new Location(world,site.x()+.5,109,site.z()+6.5));click(0,0,EquipmentSlot.HAND);
        check(parts().size()==2&&hp()==7000&&boss().phase()==1,"resummon starts a fresh fight");
        advance(2);position(-5);burst(1500);check(hp()==5500,"damage lands before the wipe");
        player.teleport(new Location(world,site.x()+80,109,site.z()));advance(205);
        check(hp()==7000&&parts().size()==2,"a wiped party restarts the phase instead of closing the fight");
        long clock=now();put(runState(),"lastPresent",clock-180*20);advance(1);check(parts().isEmpty(),"empty arena closes after the three-minute return grace");
    }
    void finish(Throwable error){
        try{Files.writeString(Path.of("arkhaia-result.txt"),error==null?"PASS "+checks+" Paper checks":"FAIL "+error);}catch(Exception ignored){}
        if(error!=null)getLogger().log(java.util.logging.Level.SEVERE,"Arkhaia duel probe failed",error);
        if(garden!=null&&garden.worldBoss()!=null)garden.worldBoss().close();
        if(actor!=null){var server=MinecraftServer.getServer();server.getPlayerList().getPlayers().remove(actor);server.getPlayerList().getPlayersByUUID().remove(actor.getUUID());actor.discard();}
        Bukkit.getScheduler().runTaskLater(this,Bukkit::shutdown,5);
    }
}
