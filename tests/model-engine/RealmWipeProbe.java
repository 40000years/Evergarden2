import com.example.voidscape.VoidscapePlugin;
import com.example.voidscape.boss.RealmBossController;
import com.example.voidscape.boss.RealmSanctuaryEncounters;
import com.example.voidscape.world.DimensionalSanctuaries;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.server.network.*;
import org.bukkit.*;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Item;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import java.lang.reflect.*;
import java.net.InetSocketAddress;
import java.nio.file.*;
import java.util.*;

/** Native Paper regression for real totem/death handling and Unlit Sun's co-op wipe. */
public final class RealmWipeProbe extends JavaPlugin implements Listener {
    final List<String> lines=new ArrayList<>();int checks;boolean finished;
    VoidscapePlugin garden;RealmSanctuaryEncounters manager;World world;Player first,second;
    RealmBossController boss;Object run;Map<String,Object> runs;
    final Map<UUID,String> deaths=new HashMap<>();final Map<UUID,List<ItemStack>> drops=new HashMap<>();
    void check(boolean ok,String name){if(!ok)throw new AssertionError(name);checks++;lines.add("PASS: "+name);}
    static Field field(Class<?> c,String name)throws Exception{Field f=c.getDeclaredField(name);f.setAccessible(true);return f;}
    static Method method(Class<?> c,String name,Class<?>... types)throws Exception{Method m=c.getDeclaredMethod(name,types);m.setAccessible(true);return m;}
    String plain(Component c){return c==null?null:PlainTextComponentSerializer.plainText().serialize(c);}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void capture(PlayerDeathEvent event){deaths.put(event.getEntity().getUniqueId(),plain(event.deathMessage()));drops.put(event.getEntity().getUniqueId(),new ArrayList<>(event.getDrops()));}
    @Override public void onEnable(){Bukkit.getPluginManager().registerEvents(this,this);later(100,()->start());}
    void later(long ticks,Throwing work){Bukkit.getScheduler().runTaskLater(this,()->{try{work.run();}catch(Throwable e){finish(e);}},ticks);}
    interface Throwing{void run()throws Exception;}
    Player fake(String name,double x)throws Exception{
        var server=MinecraftServer.getServer();var level=((CraftWorld)world).getHandle();var profile=new GameProfile(UUID.randomUUID(),name);
        var actor=new ServerPlayer(server,level,profile,ClientInformation.createDefault());var connection=new Connection(PacketFlow.SERVERBOUND);
        connection.channel=new EmbeddedChannel();connection.address=new InetSocketAddress("127.0.0.1",1);
        actor.connection=new ServerGamePacketListenerImpl(server,connection,actor,CommonListenerCookie.createInitial(profile,false)){@Override public boolean hasClientLoaded(){return true;}};
        actor.setPos(x,120,251.5);server.getPlayerList().getPlayers().add(actor);server.getPlayerList().getPlayersByUUID().put(profile.id(),actor);level.addNewPlayer(actor);
        Player p=actor.getBukkitEntity();p.setGameMode(GameMode.SURVIVAL);p.setGravity(false);p.setInvulnerable(false);return p;
    }
    PlayerDeathEvent fakeDeath(Player p,DamageSource src){return new PlayerDeathEvent(p,src,new ArrayList<>(),0,Component.text("original"),false);}
    void start()throws Exception{
        garden=(VoidscapePlugin)Bukkit.getPluginManager().getPlugin("Evergarden");manager=garden.realmSanctuaryEncounters();world=Bukkit.getWorlds().getFirst();
        world.setGameRule(GameRule.KEEP_INVENTORY,false);world.setDifficulty(Difficulty.NORMAL);
        for(int x=245;x<=267;x++)for(int z=245;z<=267;z++)world.getBlockAt(x,119,z).setType(Material.STONE,false);
        first=fake("SunFirst",254.5);second=fake("SunSecond",258.5);
        var site=new DimensionalSanctuaries.Site(DimensionalSanctuaries.Kind.VOID_RING,256,119,256,0,0,1);
        var arena=new DimensionalSanctuaries.Arena(256,119,256,30,20);Location center=new Location(world,256.5,120,256.5);
        Class<?> rt=Class.forName(RealmSanctuaryEncounters.class.getName()+"$Run");Constructor<?> ctor=rt.getDeclaredConstructor(World.class,DimensionalSanctuaries.Site.class,DimensionalSanctuaries.Arena.class,Location.class);ctor.setAccessible(true);
        run=ctor.newInstance(world,site,arena,center);
        Method playable=method(RealmSanctuaryEncounters.class,"playableInRun",rt,Player.class),contains=method(RealmSanctuaryEncounters.class,"contains",rt,Location.class);
        boss=new RealmBossController(garden,site,arena,center,p->{try{return (boolean)playable.invoke(manager,run,p)&&(boolean)contains.invoke(manager,run,p.getLocation());}catch(Exception e){throw new RuntimeException(e);}},d->{});
        boss.spawnWaiting(1);boss.awaken(List.of(first,second));field(rt,"controller").set(run,boss);field(rt,"fighting").setBoolean(run,true);
        @SuppressWarnings("unchecked") Map<String,Object> found=(Map<String,Object>)field(RealmSanctuaryEncounters.class,"runs").get(manager);runs=found;runs.put("wipe-probe",run);
        // Live fights hold the arena's chunks; this manually constructed run must do the same.
        org.bukkit.Chunk held=first.getLocation().getChunk();held.addPluginChunkTicket(garden);
        @SuppressWarnings("unchecked") Set<org.bukkit.Chunk> tickets=(Set<org.bukkit.Chunk>)field(rt,"tickets").get(run);tickets.add(held);
        DamageSource bossHit=DamageSource.builder(DamageType.INDIRECT_MAGIC).withCausingEntity(boss.carrier()).withDirectEntity(boss.carrier()).build();
        // The death event has not fired when a Totem saves a participant.
        first.getInventory().setItemInOffHand(new ItemStack(Material.TOTEM_OF_UNDYING));first.setHealth(2);first.setNoDamageTicks(0);first.damage(100,bossHit);
        check(!first.isDead()&&!deaths.containsKey(first.getUniqueId())&&!field(rt,"wipePending").getBoolean(run),"Totem rescue keeps the fight running");
        first.getActivePotionEffects().forEach(e->first.removePotionEffect(e.getType()));first.getInventory().clear();first.getInventory().setItem(0,new ItemStack(Material.DIAMOND));
        second.teleport(new Location(world,300.5,120,251.5));
        @SuppressWarnings("unchecked") Map<UUID,Long> hunted=(Map<UUID,Long>)field(RealmBossController.class,"lastAttack").get(boss);
        hunted.put(first.getUniqueId(),1L);
        first.setHealth(1);first.setNoDamageTicks(0);first.damage(100,bossHit);
        check(first.isDead()&&deaths.get(first.getUniqueId()).contains("Unlit Sun"),"a real direct boss kill names Unlit Sun");
        check(drops.get(first.getUniqueId()).stream().anyMatch(item->item.getType()==Material.DIAMOND),"normal death drops remain intact");
        check(world.getEntitiesByClass(Item.class).stream().anyMatch(i->i.getItemStack().getType()==Material.DIAMOND),"death physically spawns the dropped item");
        @SuppressWarnings("unchecked") Set<UUID> fallen=(Set<UUID>)field(rt,"fallen").get(run);
        check(fallen.contains(first.getUniqueId())&&!fallen.contains(second.getUniqueId())&&!field(rt,"wipePending").getBoolean(run),"a living fighter outside the ring keeps the fight in its normal grace period");
        @SuppressWarnings("unchecked") Set<UUID> participants=(Set<UUID>)field(RealmBossController.class,"participants").get(boss);
        participants.add(UUID.randomUUID()); // Offline participant must not keep a wiped fight alive.
        Method survivor=method(RealmSanctuaryEncounters.class,"hasLivingFighter",rt,Set.class);
        check((boolean)survivor.invoke(manager,run,participants),"living out-of-ring participant counts as a survivor");
        second.setGameMode(GameMode.SPECTATOR);
        check(!(boolean)survivor.invoke(manager,run,participants),"offline and spectator participants cannot keep a wipe alive");
        Player visitor=fake("SunVisitor",255.5);
        check((boolean)survivor.invoke(manager,run,participants),"a newly arrived arena fighter prevents a premature wipe");
        visitor.teleport(new Location(world,350.5,120,251.5));
        second.setGameMode(GameMode.SURVIVAL);second.teleport(new Location(world,258.5,120,251.5));
        first.spigot().respawn();
        later(3,()->afterRespawn(rt,visitor));
    }
    void afterRespawn(Class<?> rt,Player visitor)throws Exception{
        first=Bukkit.getPlayer(first.getUniqueId());
        check(first!=null&&!first.isDead(),"fallen player actually respawns while a teammate is fighting");
        first.teleport(new Location(world,254.5,120,251.5));
        check(!boss.presentPlayers().contains(first),"respawned player stays out of boss target lists in the arena");
        @SuppressWarnings("unchecked") Map<UUID,Long> hunted=(Map<UUID,Long>)field(RealmBossController.class,"lastAttack").get(boss);
        check(!hunted.containsKey(first.getUniqueId()),"prior anti-camping hunt cannot pull a respawned player");
        Item drop=world.getEntitiesByClass(Item.class).stream().filter(i->i.getItemStack().getType()==Material.DIAMOND).findFirst().orElseThrow();
        drop.setPickupDelay(0);first.teleport(drop.getLocation());
        ((org.bukkit.craftbukkit.entity.CraftItem)drop).getHandle().playerTouch(((org.bukkit.craftbukkit.entity.CraftPlayer)first).getHandle());
        check(first.getInventory().contains(Material.DIAMOND),"respawned player physically picks up the dropped item");
        Method fromRing=method(RealmSanctuaryEncounters.class,"fromRing",rt,Player.class,org.bukkit.entity.Entity.class);
        check(!(boolean)fromRing.invoke(manager,run,first,null),"fallen player cannot re-enter this fight on respawn");
        check(boss.isFighting()&&boss.presentPlayers().contains(second),"living teammate remains eligible to fight");
        // Fire attribution applies only when the boss ignited an unburning player.
        Method ignite=method(RealmBossController.class,"ignite",Player.class,int.class);second.setFireTicks(0);ignite.invoke(boss,second,50);
        var fireDeath=fakeDeath(second,DamageSource.builder(DamageType.ON_FIRE).build());manager.onBossDeathMessage(fireDeath);
        check(plain(fireDeath.deathMessage()).contains("Unlit Sun"),"boss-started fire is attributed to Unlit Sun");
        manager.invalidateSunFire(new EntityCombustEvent(second,3));
        var unrelatedFire=fakeDeath(second,DamageSource.builder(DamageType.ON_FIRE).build());manager.onBossDeathMessage(unrelatedFire);
        check("original".equals(plain(unrelatedFire.deathMessage())),"later external ignition is not attributed to Unlit Sun");
        ignite.invoke(boss,second,50);var preExisting=fakeDeath(second,DamageSource.builder(DamageType.ON_FIRE).build());manager.onBossDeathMessage(preExisting);
        check("original".equals(plain(preExisting.deathMessage())),"pre-existing fire is never claimed by a boss hit");
        var wither=new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.WITHER,100,0);
        visitor.addPotionEffect(wither);
        var witherEvent=new EntityPotionEffectEvent(visitor,null,wither,boss.carrier(),EntityPotionEffectEvent.Cause.ATTACK,EntityPotionEffectEvent.Action.ADDED,true);
        manager.onSunWither(witherEvent);
        var witherDeath=fakeDeath(visitor,DamageSource.builder(DamageType.WITHER).build());manager.onBossDeathMessage(witherDeath);
        check(plain(witherDeath.deathMessage()).contains("Unlit Sun"),"Wither with an explicit Unlit proxy source is attributed to Unlit Sun");
        manager.onSunWither(new EntityPotionEffectEvent(visitor,wither,wither,null,EntityPotionEffectEvent.Cause.PLUGIN,EntityPotionEffectEvent.Action.CHANGED,true));
        var unrelatedWither=fakeDeath(visitor,DamageSource.builder(DamageType.WITHER).build());manager.onBossDeathMessage(unrelatedWither);
        check("original".equals(plain(unrelatedWither.deathMessage())),"an unrelated Wither effect is not attributed to Unlit Sun");
        second.setFireTicks(0);second.setHealth(1);second.setNoDamageTicks(0);second.damage(100,DamageSource.builder(DamageType.GENERIC).build());
        check(second.isDead()&&field(rt,"wipePending").getBoolean(run),"the last fighter's actual death schedules immediate wipe");
        check(!deaths.get(second.getUniqueId()).contains("Unlit Sun"),"an unrelated death is not renamed");
        later(3,()->{
            check(field(rt,"controller").get(run)!=boss&&!field(rt,"fighting").getBoolean(run),"wipe resets to a waiting boss after death/drop processing");
            check(((Set<?>)field(rt,"fallen").get(run)).isEmpty(),"a fresh fight clears the fallen-player exclusion");
            RealmBossController solo=(RealmBossController)field(rt,"controller").get(run);
            first.teleport(new Location(world,1200.5,120,1200.5));
            visitor.removePotionEffect(org.bukkit.potion.PotionEffectType.WITHER);visitor.teleport(new Location(world,256.5,120,251.5));
            solo.awaken(List.of(visitor));field(rt,"fighting").setBoolean(run,true);
            org.bukkit.Chunk soloChunk=visitor.getLocation().getChunk();soloChunk.addPluginChunkTicket(garden);
            @SuppressWarnings("unchecked") Set<org.bukkit.Chunk> soloTickets=(Set<org.bukkit.Chunk>)field(rt,"tickets").get(run);soloTickets.add(soloChunk);
            visitor.getInventory().clear();visitor.getInventory().setItem(0,new ItemStack(Material.GOLD_INGOT));
            visitor.setHealth(1);visitor.setNoDamageTicks(0);visitor.damage(100,DamageSource.builder(DamageType.GENERIC).build());
            check(visitor.isDead()&&field(rt,"wipePending").getBoolean(run),"solo death schedules a wipe without waiting for the empty-arena timer");
            check(world.getEntitiesByClass(Item.class).stream().anyMatch(i->i.getItemStack().getType()==Material.GOLD_INGOT),"solo death spawns the dropped item");
            later(3,()->{check(field(rt,"controller").get(run)!=solo&&!field(rt,"fighting").getBoolean(run),"solo wipe returns to a waiting boss");
                check(!soloChunk.getPluginChunkTickets().contains(garden),"solo wipe releases the arena chunk ticket");
                visitor.spigot().respawn();
                later(3,()->{
                    Player returned=Bukkit.getPlayer(visitor.getUniqueId());check(returned!=null&&!returned.isDead(),"solo player respawns after wipe");
                    returned.teleport(new Location(world,256.5,120,251.5));
                    later(4,()->{
                        Item gold=world.getEntitiesByClass(Item.class).stream().filter(i->i.getItemStack().getType()==Material.GOLD_INGOT).findFirst().orElseThrow();
                        gold.setPickupDelay(0);returned.teleport(gold.getLocation());
                        ((org.bukkit.craftbukkit.entity.CraftItem)gold).getHandle().playerTouch(((org.bukkit.craftbukkit.entity.CraftPlayer)returned).getHandle());
                        check(returned.getInventory().contains(Material.GOLD_INGOT),"solo player returns and picks up loot after chunk ticket release");
                        runs.remove("wipe-probe");((RealmBossController)field(rt,"controller").get(run)).close();finish(null);
                    });
                });
            });
        });
    }
    void finish(Throwable error){if(finished)return;finished=true;if(error!=null){error.printStackTrace();lines.add("ERROR: "+error);}lines.add(0,(error==null?"PASS":"FAIL")+": realm wipe / "+checks+" checks");try{Files.write(Path.of("model-engine-result.txt"),lines);}catch(Exception ignored){}Bukkit.getScheduler().runTaskLater(this,Bukkit::shutdown,5);}
}
