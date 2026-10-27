import com.example.voidscape.VoidscapePlugin;
import com.example.voidscape.boss.JudgePackService;
import com.example.voidscape.boss.ArkhaiaBrain;
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

/** Exercise production event handlers on Paper, with an API-shaped LM event fixture. */
public class ArkhaiaProbe extends JavaPlugin implements Listener {
    VoidscapePlugin garden;World world;Player player;ServerPlayer actor;BossStructureLayout.Site site;int checks;
    boolean cancelSword,retaliate;int swordEvents;double swordDamage;
    @EventHandler(priority=EventPriority.HIGH)
    public void sword(EntityDamageByEntityEvent event){
        if(event.getEntity()==player&&event.getDamager().getScoreboardTags().contains("arkhaia")){
            swordEvents++;swordDamage=event.getDamage();if(cancelSword)event.setCancelled(true);
            if(retaliate)target().damage(8000,player);
        }
    }
    void check(boolean ok,String label){if(!ok)throw new AssertionError(label);checks++;getLogger().info("PASS: "+label);}
    @Override public void onEnable(){Bukkit.getScheduler().runTaskLater(this,()->{try{run();}catch(Throwable e){finish(e);}},20);}
    List<Entity> parts(){return world.getEntities().stream().filter(e->e.getPersistentDataContainer().has(garden.key("arkhaia_boss"),PersistentDataType.BYTE)).toList();}
    void click(int x,int z,EquipmentSlot hand){
        var event=new PlayerInteractEvent(player,Action.RIGHT_CLICK_BLOCK,player.getInventory().getItem(hand),world.getBlockAt(site.x()+x,108,site.z()+z),BlockFace.UP,hand);
        Bukkit.getPluginManager().callEvent(event);
    }
    void accept(){Bukkit.getPluginManager().callEvent(new PlayerResourcePackStatusEvent(player,JudgePackService.PACK_ID,PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED));}
    void ticks(int n){advance(n*2);}
    void advance(int n){for(int i=0;i<n;i++)garden.worldBoss().tickArkhaia();}
    Object runState()throws Exception{
        var field=garden.worldBoss().getClass().getDeclaredField("arkhaia");field.setAccessible(true);var manager=field.get(garden.worldBoss());
        var active=manager.getClass().getDeclaredField("active");active.setAccessible(true);return ((Map<?,?>)active.get(manager)).values().iterator().next();
    }
    double hp()throws Exception{var run=runState();var field=run.getClass().getDeclaredField("health");field.setAccessible(true);return field.getDouble(run);}
    void run()throws Exception{
        garden=(VoidscapePlugin)Bukkit.getPluginManager().getPlugin("Evergarden");check(garden!=null&&garden.isEnabled(),"release boots");world=garden.world();
        site=garden.bossStructures().nearest(BossStructureLayout.Kind.ARKHAIA,0,0,40);check(site!=null,"real Arkhaia site found");
        for(int x=(site.x()-16)>>4;x<=(site.x()+16)>>4;x++)for(int z=(site.z()-16)>>4;z<=(site.z()+16)>>4;z++)world.addPluginChunkTicket(x,z,this);
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
        click(0,0,EquipmentSlot.HAND);check(parts().isEmpty(),"empty hand denied");
        player.getInventory().setItemInMainHand(new ItemStack(Material.NETHERITE_INGOT));click(0,0,EquipmentSlot.HAND);check(parts().isEmpty(),"ingot denied");
        player.getInventory().setItemInMainHand(new ItemStack(Material.NETHERITE_BLOCK,3));
        click(0,0,EquipmentSlot.HAND);check(parts().isEmpty(),"unaccepted Java pack denied");accept();
        click(5,0,EquipmentSlot.HAND);check(parts().isEmpty(),"red block outside center denied");
        player.getInventory().setItemInOffHand(new ItemStack(Material.NETHERITE_BLOCK));click(0,0,EquipmentSlot.OFF_HAND);check(parts().isEmpty(),"offhand denied");
        click(0,0,EquipmentSlot.HAND);check(parts().size()==3,"ordinary player summons three boss parts");
        check(hp()==7000,"exactly 7000 virtual HP");check(player.getInventory().getItemInMainHand().getAmount()==3,"ritual does not consume block");
        var initial=parts();click(1,0,EquipmentSlot.HAND);check(parts().equals(initial),"duplicate summon denied");
        var target=(Slime)parts().stream().filter(Slime.class::isInstance).findFirst().orElseThrow();
        var display=(ItemDisplay)parts().stream().filter(ItemDisplay.class::isInstance).findFirst().orElseThrow();
        var stand=(ArmorStand)parts().stream().filter(ArmorStand.class::isInstance).findFirst().orElseThrow();
        check(Math.abs(display.getTransformation().getScale().x-3.2)<.001&&stand.getAttribute(Attribute.SCALE).getBaseValue()==1,"Java display doubled; Bedrock geometry uses unscaled carrier");
        check(!stand.isMarker(),"Bedrock carrier avoids Geyser zero-scale marker mode");
        check(player.canSee(display)&&!player.canSee(stand),"accepted Java player sees correct model");
        check(LevelledMobsCompat.isEvergardenMob(target)&&target.getScoreboardTags().contains("evergarden_unique"),"unique and LM ignore markers");
        check(!target.getScoreboardTags().contains("fixture-levelled"),"LM is blocked during creature spawn before entity becomes valid");
        var event=new io.github.arcaneplugins.levelledmobs.events.MobPreLevelEvent(target);Bukkit.getPluginManager().callEvent(event);check(event.isCancelled(),"LM4 pre-level event cancelled");
        var vanilla=world.spawn(player.getLocation().clone().add(8,0,0),org.bukkit.entity.Zombie.class);event=new io.github.arcaneplugins.levelledmobs.events.MobPreLevelEvent(vanilla);Bukkit.getPluginManager().callEvent(event);check(!event.isCancelled(),"unmarked mob LM event unchanged");vanilla.remove();

        mechanics();completed();
    }
    void completed(){garden.worldBoss().close();check(parts().isEmpty(),"shutdown cleans encounter");finish(null);}
    Object get(Object o,String name)throws Exception{var f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
    void put(Object o,String name,Object value)throws Exception{var f=o.getClass().getDeclaredField(name);f.setAccessible(true);f.set(o,value);}
    Slime target(){return (Slime)parts().stream().filter(Slime.class::isInstance).findFirst().orElseThrow();}
    ArkhaiaBrain.Frame frame()throws Exception{return (ArkhaiaBrain.Frame)get(runState(),"frame");}
    void position(double forward){Location at=target().getLocation();var dir=at.getDirection().setY(0).normalize();at.add(dir.multiply(forward));at.setY(109);
        Location aim=target().getLocation().add(.65,1,0);at.setDirection(aim.toVector().subtract(at.toVector().add(new org.bukkit.util.Vector(0,player.getEyeHeight(),0))));player.teleport(at);}
    void magic(double amount,org.bukkit.damage.DamageType type){((com.example.advancemagic.AdvanceMagicPlugin)Bukkit.getPluginManager().getPlugin("advance-magic")).context().damage(player,target(),amount,type);}
    void arrow(){var arrow=world.spawnArrow(player.getEyeLocation(),new org.bukkit.util.Vector(0,0,1),1,0);arrow.setShooter(player);
        target().damage(100,org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.ARROW).withCausingEntity(player).withDirectEntity(arrow).build());arrow.remove();}
    long attackPlanes()throws Exception{Object effects=get(runState(),"effects"),sigils=get(effects,"sigils");return ((Map<?,?>)get(sigils,"planes")).keySet().stream().filter(k->k.toString().startsWith("attack:")).count();}
    long allPlanes()throws Exception{return ((Map<?,?>)get(get(get(runState(),"effects"),"sigils"),"planes")).size();}
    void force(ArkhaiaBrain.Action action)throws Exception{
        Object run=runState(),brain=get(run,"brain"),boss=get(garden.worldBoss(),"arkhaia");long now=(long)get(boss,"tick");
        put(brain,"state",ArkhaiaBrain.State.WINDUP);put(brain,"action",action);put(brain,"entered",now+1);
        put(brain,"duration",com.example.voidscape.boss.ArkhaiaCombatTiming.of(action).windup());put(brain,"pendingStagger",false);put(brain,"impactEmitted",false);advance(1);
    }
    void release()throws Exception{for(int i=0;i<120;i++){advance(1);if(frame().impact())return;}throw new AssertionError("No release");}
    void vulnerable(){player.setInvulnerable(false);player.setHealth(player.getAttribute(Attribute.MAX_HEALTH).getValue());player.setAbsorptionAmount(0);player.setNoDamageTicks(0);}
    void invoke(Object o,String method,Class<?>[] types,Object... args)throws Exception{var m=o.getClass().getDeclaredMethod(method,types);m.setAccessible(true);m.invoke(o,args);}
    Object minions()throws Exception{return get(runState(),"minions");}
    Map<?,?> spirits()throws Exception{return (Map<?,?>)get(minions(),"spirits");}
    void resetHP(double hp)throws Exception{put(runState(),"health",hp);}
    void mechanics()throws Exception{
        Bukkit.getPluginManager().registerEvents(this,this);world.setDifficulty(Difficulty.NORMAL);
        position(2.8);target().damage(100,player);check(hp()==6950,"phase one frontal melee takes half damage");
        advance(11);check(spirits().size()==1,"solo arena summons one minion");
        Object spirit=spirits().values().iterator().next();Mob mob=(Mob)get(spirit,"mob");
        check((double)get(spirit,"health")==800&&mob.getAttribute(Attribute.MAX_HEALTH).getValue()==800,"minion has exactly 800 HP");
        check(LevelledMobsCompat.isEvergardenMob(mob)&&mob.getScoreboardTags().contains("evergarden_unique")&&!mob.getScoreboardTags().contains("fixture-levelled"),"minion excluded from LevelledMobs at spawn");
        position(2.8);double before=hp();target().damage(100,player);check(Math.abs(hp()-(before-40))<.001,"living minion multiplies boss damage by 0.8");
        advance(11);position(-5);before=hp();magic(100,org.bukkit.damage.DamageType.MAGIC);check(Math.abs(hp()-(before-240))<.001,"phase one rear wand deals x3 before minion shield");
        advance(11);position(5);before=hp();arrow();check(Math.abs(hp()-(before-40))<.001,"arrows now damage phase one");
        advance(11);position(12);before=hp();
        Location launch=player.getEyeLocation();var velocity=target().getLocation().add(0,1,0).toVector().subtract(launch.toVector()).normalize().multiply(3);
        var flying=world.spawnArrow(launch,velocity,3,0);flying.setShooter(player);flying.setGravity(false);
        for(int i=0;i<8&&flying.isValid();i++)((org.bukkit.craftbukkit.entity.CraftArrow)flying).getHandle().tick();flying.remove();
        check(hp()<before,"actual moving arrow collides with and damages hitbox");
        var spell=com.example.advancemagic.spell.Spell.values()[0];
        var affect=new com.example.advancemagic.api.MagicAffectEvent(player,target(),spell);Bukkit.getPluginManager().callEvent(affect);check(!affect.isCancelled(),"front spell effects no longer rejected");
        player.teleport(new Location(world,site.x()+55,109,site.z()));before=hp();advance(11);arrow();check(hp()==before,"outside shooter cannot damage boss");
        double minionHP=(double)get(spirit,"health");mob.damage(100,player);check((double)get(spirit,"health")==minionHP,"outside shooter cannot damage minion");
        affect=new com.example.advancemagic.api.MagicAffectEvent(player,mob,spell);Bukkit.getPluginManager().callEvent(affect);check(affect.isCancelled(),"outside spell side effects blocked on minions");
        player.teleport(mob.getLocation().clone().add(0,0,2.5).setDirection(new org.bukkit.util.Vector(.65,-.6,-2.5)));mob.damage(10000,player);check(spirits().isEmpty(),"minion dies through combat handler");
        advance(11);before=hp();position(2.8);target().damage(100,player);check(Math.abs(hp()-(before-75))<.001,"dead minion grants non-stacking 50 percent exposure");
        advance(187);check(spirits().isEmpty(),"replacement not summoned before ten seconds");advance(2);check(spirits().size()==1,"next unique minion appears at ten seconds");
        Object next=spirits().values().iterator().next();check(!get(next,"kind").equals(get(spirit,"kind")),"replacement advances roster");
        Mob nextMob=(Mob)get(next,"mob");nextMob.teleport(new Location(world,site.x()+60,109,site.z()));advance(1);
        check(nextMob.getLocation().distanceSquared((Location)get(runState(),"center"))<4,"minion beyond arena returned to center");
        // Discard a visual and then the actual base mob: neither should erase fight HP/phase.
        before=hp();parts().stream().filter(ItemDisplay.class::isInstance).findFirst().orElseThrow().remove();advance(1);
        check(parts().size()==3&&hp()==before,"missing visual recovers without deleting fight");
        target().remove();advance(1);check(parts().size()==3&&hp()==before,"missing hitbox recovers without resetting HP");
        check(!((Set<?>)get(runState(),"chunks")).isEmpty(),"arena owns chunk tickets while fighting");
        Object controller=minions();invoke(controller,"clear",new Class<?>[]{});
        advance(11);position(-5);magic(9000,org.bukkit.damage.DamageType.EXPLOSION);
        check(hp()==3500&&(int)get(runState(),"phase")==2,"large explosion stops at phase boundary");advance(1);
        check(frame().state()==ArkhaiaBrain.State.TRANSITION&&spirits().isEmpty(),"phase two clears minions and interrupts old plan");
        advance(11);position(-5);before=hp();magic(100,org.bukkit.damage.DamageType.MAGIC);check(Math.abs(hp()-(before-150))<.001,"phase two accepts magic at half times three");
        advance(11);before=hp();arrow();check(Math.abs(hp()-(before-150))<.001,"phase two accepts arrows");
        advance(11);position(2.8);before=hp();target().damage(100,player);check(Math.abs(hp()-(before-150))<.001,"phase two frontal melee takes x1.5");
        advance(11);position(-2.8);before=hp();player.getInventory().setItemInMainHand(new ItemStack(Material.DIAMOND_AXE));target().damage(100,player);
        check(Math.abs(hp()-(before-900))<.001,"phase two rear axe takes x9 total");resetHP(3500);
        advance(80);force(ArkhaiaBrain.Action.RIFT);advance(8);float locked=target().getLocation().getYaw();position(-5);advance(20);
        check(Math.abs(Math.IEEEremainder(target().getLocation().getYaw()-locked,360))<.01,"long cast remains direction locked");
        check(allPlanes()>=10&&allPlanes()<=36,"grand spells remain within display budget");
        var hazards=(List<com.example.voidscape.boss.ArkhaiaPattern.Hazard>)get(runState(),"hazards");check(hazards.size()==4,"phase two rift has four pulses");
        player.getAttribute(Attribute.MAX_HEALTH).setBaseValue(200);var h=hazards.get(0);player.teleport(new Location(world,h.x(),109,h.z()));vulnerable();int events=swordEvents;release();
        check(swordEvents==events+1&&player.getHealth()+player.getAbsorptionAmount()<200,"rift uses attributed ordinary damage");player.setInvulnerable(true);
        advance(34);check(frame().state()==ArkhaiaBrain.State.RECOVER&&attackPlanes()==0,"recovery clears stale hazards");
        for(var action:List.of(ArkhaiaBrain.Action.NOVA,ArkhaiaBrain.Action.PORTALS)){
            force(action);advance(8);check(allPlanes()<=36&&attackPlanes()>=5,"bounded spell composition "+action);release();
        }
        player.getAttribute(Attribute.MAX_HEALTH).setBaseValue(20);player.getInventory().setItemInOffHand(new ItemStack(Material.TOTEM_OF_UNDYING));
        force(ArkhaiaBrain.Action.ECLIPSE);hazards=(List<com.example.voidscape.boss.ArkhaiaPattern.Hazard>)get(runState(),"hazards");h=hazards.get(0);
        player.teleport(new Location(world,h.x(),109,h.z()));vulnerable();release();
        check(!player.isDead()&&player.getHealth()>0&&player.getInventory().getItemInOffHand().getType()!=Material.TOTEM_OF_UNDYING,"lethal Eclipse consumes Totem and preserves player life");
        player.setInvulnerable(true);advance(80);force(ArkhaiaBrain.Action.LIGHT);position(2.8);player.teleport(player.getLocation().add(.5,0,0));player.getAttribute(Attribute.MAX_HEALTH).setBaseValue(200);vulnerable();release();
        check(Math.abs(swordDamage-28.8)<.01&&player.hasPotionEffect(org.bukkit.potion.PotionEffectType.NAUSEA)&&player.hasPotionEffect(org.bukkit.potion.PotionEffectType.SLOWNESS),"rare phase two sword is x3 and applies nausea plus slow");
        player.setInvulnerable(true);player.setGameMode(GameMode.CREATIVE);advance(25);check(frame().state()==ArkhaiaBrain.State.IDLE&&attackPlanes()==0,"lost opponent cancels hazards");
        player.setGameMode(GameMode.SURVIVAL);advance(11);position(-2.8);target().damage(9000,player);
        check(parts().isEmpty(),"boss death removes carriers");
        check(((Map<?,?>)get(get(garden.worldBoss(),"arkhaia"),"tickets")).isEmpty(),"fight end releases all chunk tickets");
        player.getInventory().setItemInMainHand(new ItemStack(Material.NETHERITE_BLOCK));player.teleport(new Location(world,site.x()+.5,109,site.z()+6.5));click(0,0,EquipmentSlot.HAND);
        check(parts().size()==3&&hp()==7000&&(int)get(runState(),"phase")==1,"resummon resets fight");
        Object manager=get(garden.worldBoss(),"arkhaia");long now=(long)get(manager,"tick");put(runState(),"lastPresent",now-10000);advance(1);
        check(parts().size()==3,"present player refreshes inactivity before expiry check");
        player.teleport(new Location(world,site.x()+60,109,site.z()));now=(long)get(manager,"tick");put(runState(),"lastPresent",now-1201);advance(1);
        check(parts().size()==3,"death or reconnect gap over one minute no longer deletes encounter");
        now=(long)get(manager,"tick");put(runState(),"lastPresent",now-6000);advance(1);check(parts().isEmpty(),"empty arena eventually cleans fight after five minutes");

    }
    void finish(Throwable error){
        try{Files.writeString(Path.of("arkhaia-result.txt"),error==null?"PASS "+checks+" Paper checks":"FAIL "+error);}catch(Exception ignored){}
        if(error!=null)getLogger().log(java.util.logging.Level.SEVERE,"Arkhaia probe failed",error);
        if(garden!=null&&garden.worldBoss()!=null)garden.worldBoss().close();
        if(actor!=null){var server=MinecraftServer.getServer();server.getPlayerList().getPlayers().remove(actor);server.getPlayerList().getPlayersByUUID().remove(actor.getUUID());actor.discard();}
        Bukkit.getScheduler().runTaskLater(this,Bukkit::shutdown,5);
    }
}
