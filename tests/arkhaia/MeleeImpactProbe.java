import com.example.voidscape.boss.*;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.sounds.SoundSource;
import org.bukkit.*;
import org.bukkit.damage.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import java.util.*;

/** Actual Paper damage acceptance and the sound packets delivered to the attacker. */
public final class MeleeImpactProbe extends AcheronSplitProbe {
    private static final String STRONG="minecraft:entity.player.attack.strong";
    private static final String MACE="minecraft:item.mace.smash_air";
    private static final String ANVIL="minecraft:block.anvil.hit";
    private static final String ARROW="minecraft:entity.arrow.hit_player";
    private final List<net.minecraft.network.protocol.Packet<?>> sentPackets=new ArrayList<>();
    private Entity cancelledTarget;
    private JudgmentFight judgeFight;
    private Slime judgeCore;
    @FunctionalInterface interface Stage {void run()throws Exception;}
    void next(Stage stage){Bukkit.getScheduler().runTaskLater(this,()->{try{stage.run();}catch(Throwable error){finish(error);}},2);}
    @Override void completed(){} // The asynchronous packet cases own final completion.
    @Override void run()throws Exception{
        garden=(com.example.voidscape.VoidscapePlugin)Bukkit.getPluginManager().getPlugin("Evergarden");
        check(garden!=null&&garden.isEnabled(),"release boots");world=garden.world();
        site=garden.bossStructures().nearest(com.example.voidscape.world.BossStructureLayout.Kind.ARKHAIA,0,0,40);
        check(site!=null,"production Arkhaia site located");
        for(int x=(site.x()-16)>>4;x<=(site.x()+16)>>4;x++)for(int z=(site.z()-16)>>4;z<=(site.z()+16)>>4;z++)world.addPluginChunkTicket(x,z,this);
        for(int x=-6;x<=6;x++)for(int z=-6;z<=6;z++)for(int y=109;y<=122;y++)world.getBlockAt(site.x()+x,y,site.z()+z).setType(Material.AIR,false);
        for(int x=-5;x<=5;x++)for(int z=-5;z<=5;z++)world.getBlockAt(site.x()+x,108,site.z()+z).setType(Material.RED_GLAZED_TERRACOTTA,false);
        var server=net.minecraft.server.MinecraftServer.getServer();var level=((org.bukkit.craftbukkit.CraftWorld)world).getHandle();
        var profile=new com.mojang.authlib.GameProfile(UUID.randomUUID(),"MeleeImpactTest");
        actor=new net.minecraft.server.level.ServerPlayer(server,level,profile,net.minecraft.server.level.ClientInformation.createDefault());
        var connection=new Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);connection.channel=new EmbeddedChannel();connection.address=new java.net.InetSocketAddress("127.0.0.1",1);
        actor.connection=new net.minecraft.server.network.ServerGamePacketListenerImpl(server,connection,actor,net.minecraft.server.network.CommonListenerCookie.createInitial(profile,false)){
            @Override public boolean hasClientLoaded(){return true;}
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet){sentPackets.add(packet);super.send(packet);}
        };
        actor.setPos(site.x()+.5,109,site.z()+6.5);server.getPlayerList().getPlayers().add(actor);server.getPlayerList().getPlayersByUUID().put(profile.id(),actor);level.addNewPlayer(actor);
        player=actor.getBukkitEntity();player.setGravity(false);player.setInvulnerable(true);player.setGameMode(GameMode.SURVIVAL);accept();
        player.getInventory().setItemInMainHand(new ItemStack(Material.NETHERITE_BLOCK,3));click(0,0,org.bukkit.inventory.EquipmentSlot.HAND);
        check(hp()==7000,"production ritual creates Arkhaia at full encounter HP");mechanics();
    }

    @EventHandler(priority=EventPriority.HIGH)
    public void cancelIncoming(EntityDamageEvent event){if(event.getEntity()==cancelledTarget)event.setCancelled(true);}

    List<ClientboundSoundPacket> sounds(){return sentPackets.stream().filter(ClientboundSoundPacket.class::isInstance).map(ClientboundSoundPacket.class::cast).toList();}
    int mark(){return sounds().size();}
    List<ClientboundSoundPacket> since(int mark){return sounds().subList(mark,sounds().size());}
    String key(ClientboundSoundPacket packet){return packet.getSound().value().location().toString();}
    long count(int mark,String sound){return since(mark).stream().filter(packet->key(packet).equals(sound)).count();}
    void noImpact(int mark,String label){check(count(mark,MACE)==0&&count(mark,ANVIL)==0,label+" produces no melee impact");}
    void stack(int mark,String label){
        List<ClientboundSoundPacket> delivered=since(mark);
        check(count(mark,STRONG)==1&&count(mark,MACE)==1&&count(mark,ANVIL)==1,label+" delivers exactly one three-layer melee impact [tick="+Bukkit.getCurrentTick()+", sounds="+delivered.stream().map(this::key).toList()+"]");
        for(var spec:Map.of(STRONG,new float[]{.8f,.95f},MACE,new float[]{.55f,1.15f},ANVIL,new float[]{.18f,1.55f}).entrySet()){
            ClientboundSoundPacket packet=delivered.stream().filter(p->key(p).equals(spec.getKey())).findFirst().orElseThrow();
            check(packet.getSource()==SoundSource.PLAYERS&&Math.abs(packet.getVolume()-spec.getValue()[0])<.0001&&Math.abs(packet.getPitch()-spec.getValue()[1])<.0001,
                label+" has the intended player-category mix for "+spec.getKey());
            check(Math.abs(packet.getX()-player.getX())<=.126&&Math.abs(packet.getY()-player.getY())<=.126&&Math.abs(packet.getZ()-player.getZ())<=.126,
                label+" delivers "+spec.getKey()+" directly at the attacker's location");
        }
    }
    void clearHits(Object target)throws Exception{((Map<?,?>)get(target,"hits")).clear();}
    double unitHp()throws Exception{return (double)get(unit,"health");}
    void aimUnit(){
        double offset=mob.getName().contains("Banshee")?.95:.7;
        player.teleport(pad.clone().add(offset,0,2.5));face(pad.clone().add(offset,1,mob.getName().contains("Banshee")?.55:0));
        player.getInventory().setItemInMainHand(new ItemStack(Material.DIAMOND_SWORD));
    }
    void spell(LivingEntity target,double amount){
        ((com.example.advancemagic.AdvanceMagicPlugin)Bukkit.getPluginManager().getPlugin("advance-magic")).context().damage(player,target,amount,DamageType.MAGIC);
    }
    void shot(LivingEntity target,double amount){
        Arrow arrow=world.spawnArrow(player.getEyeLocation(),new Vector(0,0,1),1,0);arrow.setShooter(player);
        target.damage(amount,DamageSource.builder(DamageType.ARROW).withCausingEntity(player).withDirectEntity(arrow).build());arrow.remove();
    }
    @Override void mechanics()throws Exception{
        Bukkit.getPluginManager().registerEvents(this,this);world.setDifficulty(Difficulty.NORMAL);
        controller=minions();invoke(controller,"clear",new Class<?>[]{});
        player.getInventory().setItemInMainHand(new ItemStack(Material.DIAMOND_SWORD));

        position(2.8);int start=mark();double hpBefore=hp();target().damage(10,player);
        check(hp()<hpBefore,"accepted Arkhaia sword hit removes encounter HP");stack(start,"Arkhaia sword hit");
        start=mark();hpBefore=hp();target().damage(10,player);check(hp()==hpBefore,"Arkhaia cooldown rejects immediate repeat");noImpact(start,"Arkhaia cooldown");
        clearHits(runState());position(12);start=mark();target().damage(10,player);check(hp()==hpBefore,"distant sword misses Arkhaia");noImpact(start,"distant Arkhaia miss");
        position(2.8);start=mark();cancelledTarget=target();target().damage(10,player);cancelledTarget=null;
        check(hp()==hpBefore,"pre-cancelled Arkhaia damage does not remove HP");noImpact(start,"pre-cancelled Arkhaia hit");
        start=mark();target().damage(0,player);check(hp()==hpBefore,"zero Arkhaia damage removes no HP");noImpact(start,"zero Arkhaia hit");
        clearHits(runState());position(5);start=mark();shot(target(),10);check(hp()<hpBefore,"Arkhaia arrow remains accepted");
        check(count(start,ARROW)==1,"Arkhaia arrow retains its original hit ding");noImpact(start,"Arkhaia arrow");
        clearHits(runState());start=mark();hpBefore=hp();spell(target(),10);check(hp()<hpBefore,"Arkhaia spell remains accepted");noImpact(start,"Arkhaia spell");
        clearHits(runState());position(2.8);start=mark();hpBefore=hp();target().damage(10,player);
        check(hp()<hpBefore,"a separate accepted same-tick melee event still removes encounter HP");
        check(count(start,STRONG)==0,"same-tick melee confirmation does not repeat the original attack layer");noImpact(start,"same-tick accepted melee deduplication");

        invoke(controller,"demo",new Class<?>[]{UUID.class,boolean.class},player.getUniqueId(),false);
        pad=new Location(world,site.x()+10.5,109,site.z()+.5);now=(long)get(controller,"clock");
        for(int x=-8;x<=8;x++)for(int z=-8;z<=12;z++){
            world.getBlockAt(pad.getBlockX()+x,108,pad.getBlockZ()+z).setType(Material.STONE,false);
            for(int y=109;y<=122;y++)world.getBlockAt(pad.getBlockX()+x,y,pad.getBlockZ()+z).setType(Material.AIR,false);
        }
        next(()->riverCase(0));
    }
    void riverCase(int index)throws Exception{
        if(index<ArkhaiaWaves.Kind.values().length){river(ArkhaiaWaves.Kind.values()[index]);next(()->riverCase(index+1));return;}
        summon(ArkhaiaWaves.Kind.ACHERON);aimUnit();int start=mark();mob.damage(10000,player);
        check(!mob.isValid(),"lethal river sword hit removes the model receiver");stack(start,"lethal Acheron hit");
        invoke(controller,"clear",new Class<?>[]{});next(this::judge);
    }
    void river(ArkhaiaWaves.Kind kind)throws Exception{
        summon(kind);aimUnit();double before=unitHp();int start=mark();
        if(kind==ArkhaiaWaves.Kind.STYX){
            mob.damage(10,player);check(unitHp()==before,"Styx retains melee immunity");noImpact(start,"immune Styx melee");
            start=mark();spell(mob,10);check(unitHp()<before,"Styx remains vulnerable to spells");noImpact(start,"Styx spell");return;
        }
        if(kind==ArkhaiaWaves.Kind.BANSHEE_WAIL){
            mob.damage(10,player);check(unitHp()==before,"Banshee veil rejects the body hit");noImpact(start,"shielded Banshee melee");
            exposeBansheeBody();start=mark();
        }
        mob.damage(10,player);check(unitHp()<before,"accepted sword removes HP from "+kind);stack(start,kind+" sword hit");
        before=unitHp();start=mark();mob.damage(10,player);check(unitHp()==before,kind+" cooldown rejects repeat");noImpact(start,kind+" cooldown");
        clearHits(unit);face(player.getLocation().add(0,1,10));start=mark();mob.damage(10,player);check(unitHp()==before,"facing away misses "+kind);noImpact(start,kind+" facing-away miss");
        player.teleport(pad.clone().add(0,0,14));face(pad.clone().add(.7,1,0));start=mark();mob.damage(10,player);check(unitHp()==before,"distant sword misses "+kind);noImpact(start,kind+" distant miss");
        aimUnit();cancelledTarget=mob;start=mark();mob.damage(10,player);cancelledTarget=null;
        check(unitHp()==before,"cancelled sword preserves HP for "+kind);noImpact(start,kind+" cancelled hit");
        start=mark();mob.damage(0,player);check(unitHp()==before,"zero sword damage preserves HP for "+kind);noImpact(start,kind+" zero hit");
        player.teleport(new Location(world,site.x()+60,109,site.z()));start=mark();shot(mob,10);
        check(unitHp()==before,"outside attack preserves HP for "+kind);noImpact(start,kind+" outside hit");
        aimUnit();clearHits(unit);start=mark();shot(mob,10);
        check(kind==ArkhaiaWaves.Kind.COCYTUS?unitHp()==before:unitHp()<before,kind+" respects its arrow acceptance rule");
        check(count(start,ARROW)==(kind==ArkhaiaWaves.Kind.COCYTUS?0:1),kind+" arrow hit ding requires accepted damage");noImpact(start,kind+" arrow");
        clearHits(unit);start=mark();before=unitHp();spell(mob,10);
        check(kind==ArkhaiaWaves.Kind.MALEFEASANCE?unitHp()==before:unitHp()<before,kind+" retains its magic acceptance rules");noImpact(start,kind+" spell");
    }
    @SuppressWarnings("unchecked") void judge()throws Exception{
        var temple=garden.bossTemples().nearest(0,0,64);check(temple!=null,"Judge temple located for audio integration");
        for(int x:new int[]{-30,0,30})for(int z:new int[]{-30,0,30}){
            world.getBlockAt(temple.x()+x,100,temple.z()+z).setType(Material.STONE,false);
            for(int y=101;y<=103;y++)world.getBlockAt(temple.x()+x,y,temple.z()+z).setType(Material.AIR,false);
        }
        world.getBlockAt(temple.x(),100,temple.z()+6).setType(Material.STONE,false);
        for(int y=101;y<=103;y++)world.getBlockAt(temple.x(),y,temple.z()+6).setType(Material.AIR,false);
        player.teleport(new Location(world,temple.x()+.5,101,temple.z()+6.5));
        check(garden.worldBoss().start(player,true),"Judge starts through the production manager");
        Object run=((Map<?,?>)get(garden.worldBoss(),"active")).values().iterator().next();
        JudgmentFight fight=(JudgmentFight)get(run,"fight");Map<JudgmentFight.Part,Slime> targets=(Map<JudgmentFight.Part,Slime>)get(run,"targets");
        Slime hand=targets.get(JudgmentFight.Part.LEFT),core=targets.get(JudgmentFight.Part.CORE);
        player.getInventory().setItemInMainHand(new ItemStack(Material.DIAMOND_SWORD));
        int start=mark();double before=fight.hand(JudgmentFight.Part.LEFT);hand.damage(10,player);
        check(fight.hand(JudgmentFight.Part.LEFT)<before,"Judge hand accepts real melee damage");stack(start,"Judge hand melee");
        start=mark();before=fight.core();core.damage(10,player);check(fight.core()==before,"sealed Judge core rejects melee damage");noImpact(start,"sealed Judge core");
        hand.setNoDamageTicks(0);cancelledTarget=hand;start=mark();before=fight.hand(JudgmentFight.Part.LEFT);hand.damage(10,player);cancelledTarget=null;
        check(fight.hand(JudgmentFight.Part.LEFT)==before,"cancelled Judge melee preserves hand HP");noImpact(start,"cancelled Judge melee");
        hand.setNoDamageTicks(0);start=mark();spell(hand,10);check(fight.hand(JudgmentFight.Part.LEFT)<before,"Judge hand retains spell damage");noImpact(start,"Judge spell");
        hand.setNoDamageTicks(0);start=mark();before=fight.hand(JudgmentFight.Part.LEFT);shot(hand,10);check(fight.hand(JudgmentFight.Part.LEFT)<before,"Judge hand retains projectile damage");noImpact(start,"Judge arrow");
        judgeFight=fight;judgeCore=core;next(this::exposedJudge);
    }
    void exposedJudge()throws Exception{
        put(judgeFight,"exposedUntil",Long.MAX_VALUE);judgeCore.setNoDamageTicks(0);int start=mark();double before=judgeFight.core();judgeCore.damage(10,player);
        check(judgeFight.core()<before,"exposed Judge core accepts melee damage");stack(start,"exposed Judge core melee");
        garden.worldBoss().stop(player);super.completed();
    }
}
