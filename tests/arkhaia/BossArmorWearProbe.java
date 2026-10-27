import com.example.voidscape.VoidscapePlugin;
import com.example.voidscape.boss.ArkhaiaWaves;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.server.network.*;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.damage.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.plugin.java.JavaPlugin;
import java.net.InetSocketAddress;
import java.nio.file.*;
import java.util.*;
import java.util.function.*;

/** Actual Paper durability/enchantment/event behavior, not mocks of item metadata. */
public class BossArmorWearProbe extends JavaPlugin implements Listener {
    VoidscapePlugin garden;Player player;ServerPlayer actor;Husk source;World world;
    Object controller;int checks,breaks,itemEvents;boolean cancelDamage,cancelWear;
    java.lang.reflect.Method helper;
    @EventHandler(priority=EventPriority.HIGHEST) public void damage(EntityDamageEvent e){if(e.getEntity()==player&&cancelDamage)e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST) public void item(PlayerItemDamageEvent e){if(e.getPlayer()==player){itemEvents++;if(cancelWear)e.setCancelled(true);}}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void riverOutgoing(org.bukkit.event.entity.EntityDamageByEntityEvent e){
        if(controller!=null)try{call(controller,"outgoing",new Class<?>[]{org.bukkit.event.entity.EntityDamageByEntityEvent.class},e);}catch(Exception error){throw new RuntimeException(error);}
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void riverProjectile(org.bukkit.event.entity.ProjectileHitEvent e){
        if(controller!=null)try{call(controller,"projectileHit",new Class<?>[]{org.bukkit.event.entity.ProjectileHitEvent.class},e);}catch(Exception error){throw new RuntimeException(error);}
    }
    @EventHandler public void broke(PlayerItemBreakEvent e){if(e.getPlayer()==player)breaks++;}
    void check(boolean condition,String label){if(!condition)throw new AssertionError(label);checks++;getLogger().info("PASS: "+label);}
    @Override public void onEnable(){Bukkit.getScheduler().runTaskLater(this,()->{try{run();finish(null);}catch(Throwable e){finish(e);}},20);}
    Object call(Object object,String name,Class<?>[] types,Object... args)throws Exception{var method=object.getClass().getDeclaredMethod(name,types);method.setAccessible(true);return method.invoke(object,args);}
    void put(Object object,String name,Object value)throws Exception{var f=object.getClass().getDeclaredField(name);f.setAccessible(true);f.set(object,value);}
    Object get(Object object,String name)throws Exception{var f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}
    DamageSource magic(){return DamageSource.builder(DamageType.INDIRECT_MAGIC).withCausingEntity(source).withDirectEntity(source).build();}
    boolean hit(double amount,boolean impact)throws Exception{return (boolean)helper.invoke(null,player,amount,magic(),impact);}
    void reset(){
        cancelDamage=false;cancelWear=false;player.setGameMode(GameMode.SURVIVAL);player.setInvulnerable(false);actor.getAbilities().invulnerable=false;
        player.getActivePotionEffects().forEach(e->player.removePotionEffect(e.getType()));
        player.setAbsorptionAmount(0);player.setHealth(200);player.setNoDamageTicks(0);player.setFireTicks(0);player.getInventory().clear();
        player.getInventory().setArmorContents(new ItemStack[]{new ItemStack(Material.DIAMOND_BOOTS),new ItemStack(Material.DIAMOND_LEGGINGS),new ItemStack(Material.DIAMOND_CHESTPLATE),new ItemStack(Material.DIAMOND_HELMET)});
    }
    int[] wear(){return Arrays.stream(player.getInventory().getArmorContents()).mapToInt(i->i==null||i.isEmpty()?-1:((Damageable)i.getItemMeta()).getDamage()).toArray();}
    void wearIs(int amount,String label){check(Arrays.stream(wear()).allMatch(v->v==amount),label+" "+Arrays.toString(wear()));}
    void run()throws Exception{
        garden=(VoidscapePlugin)Bukkit.getPluginManager().getPlugin("Evergarden");world=garden.world();world.setDifficulty(Difficulty.NORMAL);
        Location at=world.getSpawnLocation().clone();at.setY(200);world.getChunkAt(at).load();
        for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++)world.getBlockAt(at.getBlockX()+x,199,at.getBlockZ()+z).setType(Material.STONE,false);
        var server=MinecraftServer.getServer();var level=((CraftWorld)world).getHandle();var profile=new GameProfile(UUID.randomUUID(),"ArmorWearTest");
        actor=new ServerPlayer(server,level,profile,ClientInformation.createDefault());var connection=new Connection(PacketFlow.SERVERBOUND);connection.channel=new EmbeddedChannel();connection.address=new InetSocketAddress("127.0.0.1",1);
        actor.connection=new ServerGamePacketListenerImpl(server,connection,actor,CommonListenerCookie.createInitial(profile,false)){@Override public boolean hasClientLoaded(){return true;}};
        actor.setPos(at.getX(),200,at.getZ());server.getPlayerList().getPlayers().add(actor);server.getPlayerList().getPlayersByUUID().put(profile.id(),actor);level.addNewPlayer(actor);
        player=actor.getBukkitEntity();player.setGravity(false);player.getAttribute(Attribute.MAX_HEALTH).setBaseValue(200);player.getAttribute(Attribute.MAX_ABSORPTION).setBaseValue(40);
        source=world.spawn(at.clone().add(2,0,0),Husk.class,m->{m.setAI(false);m.setSilent(true);m.setGravity(false);});
        Bukkit.getPluginManager().registerEvents(this,this);
        var clazz=Class.forName("com.example.voidscape.boss.BossDamage");helper=clazz.getDeclaredMethod("hit",Player.class,double.class,DamageSource.class,boolean.class);helper.setAccessible(true);
        reset();player.damage(8,magic());check(player.getHealth()==192,"reproduce original magic HP damage");wearIs(0,"reproduce original magic never wears armor");
        reset();check(hit(8,true)&&player.getHealth()==192,"fixed impact preserves original HP damage");wearIs(2,"all four pieces lose vanilla wear floor(raw/4)");
        int[] before=wear();check(!hit(8,true),"iframe rejects repeated equal hit");check(Arrays.equals(before,wear()),"iframe rejection never charges wear twice");
        reset();cancelDamage=true;check(!hit(8,true),"cancelled damage rejected");wearIs(0,"cancelled damage never wears armor");
        reset();cancelWear=true;hit(8,true);wearIs(0,"item damage cancellation honored");
        reset();player.setAbsorptionAmount(20);check(hit(8,true)&&player.getHealth()==200&&player.getAbsorptionAmount()<20,"absorption-only hit accepted");wearIs(2,"absorption-only hit wears equipment");
        reset();hit(8,false);check(player.getHealth()==192,"damage-over-time HP damage preserved");wearIs(0,"non-impact damage does not repeatedly drain gear");
        reset();player.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.RESISTANCE,200,4));check(!hit(8,true),"fully resisted damage rejected");wearIs(0,"zero actual damage does not wear gear");
        reset();player.setGameMode(GameMode.CREATIVE);hit(8,true);wearIs(0,"Creative gear never wears");
        reset();for(var item:player.getInventory().getArmorContents()){var meta=item.getItemMeta();meta.setUnbreakable(true);item.setItemMeta(meta);}hit(8,true);wearIs(0,"native unbreakable preserved");
        reset();for(var item:player.getInventory().getArmorContents()){var meta=item.getItemMeta();garden.relics().applyEternityMeta(meta);item.setItemMeta(meta);}hit(8,true);wearIs(0,"Eternity protection preserved by real listener");
        reset();for(var item:player.getInventory().getArmorContents())item.addUnsafeEnchantment(Enchantment.UNBREAKING,3);
        for(int i=0;i<200;i++){player.setNoDamageTicks(0);player.setHealth(200);hit(4,true);}
        int total=Arrays.stream(wear()).sum();check(total>440&&total<680,"native armor Unbreaking III probability retained; total="+total+" / 800");
        reset();var chest=player.getInventory().getChestplate();var meta=(Damageable)chest.getItemMeta();meta.setDamage(chest.getType().getMaxDurability()-1);chest.setItemMeta(meta);hit(4,true);
        check((player.getInventory().getChestplate()==null||player.getInventory().getChestplate().isEmpty())&&breaks==1,"native armor break removes slot and emits one break event");
        reset();player.setHealth(1);player.getInventory().setItemInOffHand(new ItemStack(Material.TOTEM_OF_UNDYING));check(hit(8,true)&&!player.isDead(),"low-health Totem resurrection counts as a landed hit");wearIs(2,"Totem rescue still pays impact wear");
        reset();var physical=DamageSource.builder(DamageType.MOB_ATTACK).withCausingEntity(source).withDirectEntity(source).build();helper.invoke(null,player,8.0,physical,true);wearIs(2,"native physical armor wear never doubled");
        check(itemEvents>0,"native durability event path executed");
        rivers(at);
        reset();world.setGameRule(GameRules.KEEP_INVENTORY,false);
        Set<UUID> oldDrops=new HashSet<>();for(Item item:world.getEntitiesByClass(Item.class))oldDrops.add(item.getUniqueId());
        check(hit(400,true)&&player.isDead(),"lethal magic impact accepted");
        var drops=world.getEntitiesByClass(Item.class).stream().filter(i->!oldDrops.contains(i.getUniqueId())&&i.getItemStack().getType().name().startsWith("DIAMOND_")).toList();
        check(drops.size()==4&&drops.stream().allMatch(i->((Damageable)i.getItemStack().getItemMeta()).getDamage()==100),"lethal impact wear follows armor into death drops");
    }
    void rivers(Location at)throws Exception{
        var type=Class.forName("com.example.voidscape.boss.ArkhaiaMinions");var ctor=type.getDeclaredConstructors()[0];ctor.setAccessible(true);
        controller=ctor.newInstance(garden,at,(Predicate<Player>)(p->p==player),(Supplier<Entity>)(()->source),(LongConsumer)(tick->{}));
        var unitType=Class.forName("com.example.voidscape.boss.ArkhaiaMinions$Spirit");var unitCtor=unitType.getDeclaredConstructor(ArkhaiaWaves.Kind.class);unitCtor.setAccessible(true);
        for(var kind:ArkhaiaWaves.Kind.values()){
            call(controller,"clear",new Class<?>[]{});reset();Object unit=unitCtor.newInstance(kind);((Map<Object,Object>)get(controller,"spirits")).put(kind,unit);call(controller,"spawn",new Class<?>[]{unitType},unit);
            ((Mob)get(unit,"mob")).teleport(at.clone().add(0,0,3));put(unit,"mark",player.getLocation().clone());
            if(kind==ArkhaiaWaves.Kind.MALEFEASANCE){call(controller,"attack",new Class<?>[]{unitType,Player.class},unit,player);for(int i=0;i<15;i++)call(controller,"tickBolts",new Class<?>[]{});}
            else if(kind==ArkhaiaWaves.Kind.COCYTUS){
                call(controller,"castImpact",new Class<?>[]{unitType},unit);
                for(int i=0;i<16;i++)for(Object shot:new ArrayList<>(((Map<?,?>)get(controller,"frostArrows")).values())){
                    var method=shot.getClass().getDeclaredMethod("arrow");method.setAccessible(true);Arrow a=(Arrow)method.invoke(shot);if(a.isValid())((org.bukkit.craftbukkit.entity.CraftArrow)a).getHandle().tick();
                }
            }else if(kind==ArkhaiaWaves.Kind.ACHERON){call(controller,"release",new Class<?>[]{unitType},unit);call(controller,"chargeStep",new Class<?>[]{unitType},unit);}
            else call(controller,"castImpact",new Class<?>[]{unitType},unit);
            check(player.getHealth()<200,"real river impact damages player "+kind);
            check(Arrays.stream(wear()).allMatch(v->v>0),"real river impact wears all four armor pieces "+kind+" "+Arrays.toString(wear()));
        }
        call(controller,"close",new Class<?>[]{});controller=null;
    }
    void finish(Throwable error){
        if(error!=null)error.printStackTrace();
        try{if(controller!=null)call(controller,"close",new Class<?>[]{});if(source!=null)source.remove();Files.writeString(Path.of("arkhaia-result.txt"),(error==null?"PASS ":"FAIL ")+checks+" boss armor durability checks"+(error==null?"":"\n"+error)+"\n");}catch(Exception e){e.printStackTrace();}
        if(actor!=null){var server=MinecraftServer.getServer();server.getPlayerList().getPlayers().remove(actor);server.getPlayerList().getPlayersByUUID().remove(actor.getUUID());actor.discard();}
        Bukkit.shutdown();
    }
}
