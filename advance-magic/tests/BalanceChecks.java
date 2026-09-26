import com.example.advancemagic.AdvanceMagicPlugin;
import com.example.advancemagic.spell.Spell;
import com.mojang.authlib.GameProfile;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.entity.*;
import org.bukkit.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffectType;
import java.net.InetSocketAddress;
import java.util.*;

/** Test-only Paper 26.2 actor. This code and NMS dependencies are never shipped in the plugin. */
public final class BalanceChecks extends JavaPlugin implements org.bukkit.event.Listener {
    static final class TestConnection extends Connection {
        TestConnection(){super(PacketFlow.SERVERBOUND);channel=new EmbeddedChannel();address=new InetSocketAddress("127.0.0.1",1);}
        @Override public void send(Packet<?> packet){}
        @Override public void send(Packet<?> packet,ChannelFutureListener listener){}
        @Override public void send(Packet<?> packet,ChannelFutureListener listener,boolean flush){}
        @Override public boolean isConnected(){return true;}
    }
    AdvanceMagicPlugin plugin;
    Player player; ServerPlayer handle; LivingEntity target;
    World world; int passed; double expected; boolean hit,blocked,cancelDamage;
    Location anchor;
    void check(boolean value,String label){if(!value)throw new AssertionError(label);passed++;getLogger().info("PASS "+label);}
    void run(Runnable work){try{work.run();}catch(Throwable error){finish(error);}}
    void later(int ticks,Runnable work){Bukkit.getScheduler().runTaskLater(this,()->run(work),ticks);}
    @Override public void onEnable(){later(20,this::begin);}
    @org.bukkit.event.EventHandler(priority=org.bukkit.event.EventPriority.MONITOR,ignoreCancelled=true)
    public void damage(org.bukkit.event.entity.EntityDamageByEntityEvent event){
        if(event.getEntity()==target&&Math.abs(event.getDamage()-expected)<0.00001)hit=true;
    }
    @org.bukkit.event.EventHandler
    public void protect(com.example.advancemagic.api.MagicAffectEvent event){if(blocked)event.setCancelled(true);}
    @org.bukkit.event.EventHandler(priority=org.bukkit.event.EventPriority.HIGHEST)
    public void cancelHit(org.bukkit.event.entity.EntityDamageByEntityEvent event){if(cancelDamage)event.setCancelled(true);}
    void begin() {
        plugin=(AdvanceMagicPlugin)Bukkit.getPluginManager().getPlugin("advance-magic");
        check(plugin!=null&&plugin.isEnabled(),"release plugin boots");
        Bukkit.getPluginManager().registerEvents(this,this);
        world=Bukkit.getWorlds().get(0);
        for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)world.getChunkAt(x,z).setForceLoaded(true);
        for(int x=-20;x<=20;x++)for(int z=-20;z<=30;z++) {
            world.getBlockAt(x,99,z).setType(Material.STONE);
            for(int y=100;y<=120;y++)world.getBlockAt(x,y,z).setType(Material.AIR);
        }
        var server=MinecraftServer.getServer();var level=((CraftWorld)world).getHandle();
        GameProfile profile=new GameProfile(UUID.randomUUID(),"BalanceTestActor");
        handle=new ServerPlayer(server,level,profile,ClientInformation.createDefault());
        handle.connection=new ServerGamePacketListenerImpl(server,new TestConnection(),handle,CommonListenerCookie.createInitial(profile,false));
        handle.setPos(0.5,100,0.5);
        server.getPlayerList().getPlayers().add(handle);server.getPlayerList().getPlayersByUUID().put(profile.id(),handle);
        level.addNewPlayer(handle);player=handle.getBukkitEntity();player.setOp(true);player.setGravity(false);player.setInvulnerable(true);
        plugin.getConfig().set("meteor.ignite-terrain",false);
        Bukkit.getScheduler().runTaskTimer(this,()->{
            if(target!=null&&target.isValid()&&!target.isDead()){target.teleport(anchor);target.setVelocity(new org.bukkit.util.Vector());}
        },1,1);
        spell(0);
    }
    void reset() {
        plugin.effects().closeOwner(player.getUniqueId());plugin.statuses().clear(player);
        for(var effect:player.getActivePotionEffects())player.removePotionEffect(effect.getType());
        if(target!=null)target.remove();
        player.teleport(new Location(world,0.5,100,0.5,0,0));
        anchor=new Location(world,0.5,100,4.5);
        target=world.spawn(anchor,WitherSkeleton.class,mob->{mob.setAI(false);mob.setGravity(false);mob.setSilent(true);});
        target.getAttribute(Attribute.MAX_HEALTH).setBaseValue(2000);target.setHealth(target.getAttribute(Attribute.MAX_HEALTH).getValue());target.setMaximumNoDamageTicks(0);
        hit=false;
    }
    void spell(int index) {
        if(index==Spell.values().length){cancellation();return;}
        reset();Spell spell=Spell.values()[index];expected=7.123+index/1000.0;
        plugin.getConfig().set("follow-up.damage."+spell.id(),expected);
        if(spell==Spell.SOLAR_APOCALYPSE)plugin.getConfig().set("damage.solar-apocalypse",expected);
        if(spell==Spell.CHRONOS_FINAL_HOUR)plugin.getConfig().set("damage.chronos-shatter",expected);
        if(spell==Spell.SHADOW_STEP){anchor.setZ(12.5);target.teleport(anchor);}
        if(spell==Spell.DRAGONS_BREATH){anchor.setZ(15.5);target.teleport(anchor);target.setMaximumNoDamageTicks(20);}
        if(spell==Spell.METEOR_STRIKE)player.teleport(new Location(world,0.5,100,0.5,0,20));
        var wand=plugin.wands().create(spell);player.getInventory().setItemInMainHand(wand);
        var account=plugin.mana().account(player);account.setMana(100);account.restore(spell.id(),0);
        check(plugin.casts().cast(player,spell,wand),"single input casts "+spell);
        check(account.manaExact()==100-spell.mana,"charges mana once "+spell);
        long cooldown=account.end(spell.id());
        later(145,()->{
            check(hit,"automatic extra damage stage reaches enemy "+spell);
            check(account.end(spell.id())==cooldown,"extra stage does not restart cooldown "+spell);
            check(plugin.wands().casts(wand)==1,"extra stage does not add mastery casts "+spell);
            spell(index+1);
        });
    }
    void cancellation() {
        reset();expected=17.321;plugin.getConfig().set("follow-up.damage.frost_nova",expected);
        plugin.context().echo(player,anchor,Spell.FROST_NOVA,14,5,expected);
        plugin.effects().closeOwner(player.getUniqueId());
        later(20,()->{
            check(!hit,"owner cleanup cancels queued damage");
            blocked=true;plugin.context().echo(player,anchor,Spell.FROST_NOVA,14,5,expected);
            later(20,()->{
                check(!hit,"MagicAffectEvent cancellation blocks extra damage");blocked=false;
                reset();plugin.getConfig().set("follow-up.damage.guardian_beam",expected);target.setHealth(10);
                check(plugin.spells().cast(player,Spell.GUARDIAN_BEAM),"Guardian Beam starts against low-health target");
                later(24,()->{
                    check(target.isDead(),"Soul Drain kills before channel completion");
                    target=world.spawn(anchor,WitherSkeleton.class,m->{m.setAI(false);m.setGravity(false);});
                    target.getAttribute(Attribute.MAX_HEALTH).setBaseValue(2000);target.setHealth(target.getAttribute(Attribute.MAX_HEALTH).getValue());target.setMaximumNoDamageTicks(0);
                    later(20,()->{check(hit,"early channel kill still triggers final echo");cancelledAmbush();});
                });
            });
        });
    }
    void cancelledAmbush() {
        reset();plugin.getConfig().set("follow-up.damage.shadow_step",expected);
        check(plugin.spells().cast(player,Spell.SHADOW_STEP),"shadow step starts for cancelled-hit check");
        cancelDamage=true;target.damage(1,player);cancelDamage=false;
        later(20,()->{
            check(!hit,"cancelled ambush does not release follow-up damage");
            reset();double health=target.getHealth();
            check(plugin.spells().cast(player,Spell.SHULKER_LEVITATION),"singularity starts for cleanup check");
            plugin.effects().closeOwner(player.getUniqueId());
            check(plugin.effects().size()==0&&target.getHealth()==health,"cancelled singularity neither detonates nor creates sculk effects");
            finish(null);
        });
    }
    void finish(Throwable error) {
        if(error!=null)getLogger().log(java.util.logging.Level.SEVERE,"BALANCE FAILED",error);
        else getLogger().info("BALANCE COMPLETE: "+passed+" assertions passed");
        try {java.nio.file.Files.writeString(java.nio.file.Path.of("balance-result.txt"),error==null?"PASS "+passed:"FAIL "+error);}
        catch(Exception ignored) {}
        Bukkit.getScheduler().cancelTasks(this);
        if(plugin!=null&&player!=null){plugin.effects().closeOwner(player.getUniqueId());plugin.statuses().clear(player);}
        if(target!=null)target.remove();
        if(handle!=null){MinecraftServer.getServer().getPlayerList().getPlayers().remove(handle);MinecraftServer.getServer().getPlayerList().getPlayersByUUID().remove(handle.getUUID());handle.discard();}
        Bukkit.getScheduler().runTaskLater(this,Bukkit::shutdown,5);
    }
}
