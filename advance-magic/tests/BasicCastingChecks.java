import com.example.advancemagic.AdvanceMagicPlugin;
import com.example.advancemagic.api.*;
import com.example.advancemagic.spell.*;
import com.mojang.authlib.GameProfile;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.BlockFace;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.Vector;
import java.nio.file.*;
import java.util.*;

/** Real Paper events and actors exercise both input modes, policy checks and delayed hit budgets. */
public final class BasicCastingChecks extends JavaPlugin implements Listener {
    static final class TestConnection extends Connection {
        final List<Map<String,Object>> particles=new ArrayList<>();
        TestConnection(){super(PacketFlow.SERVERBOUND);channel=new EmbeddedChannel();address=new java.net.InetSocketAddress("127.0.0.1",1);}
        void capture(Packet<?> packet){
            if(packet instanceof ClientboundLevelParticlesPacket sample&&sample.getParticle() instanceof DustParticleOptions dust&&particles.size()<2000){
                var color=dust.getColor();
                particles.add(Map.of("position",List.of(sample.getX(),sample.getY(),sample.getZ()),
                        "color",List.of(color.x(),color.y(),color.z()),"size",dust.getScale()));
            }
        }
        @Override public void send(Packet<?> packet){capture(packet);}
        @Override public void send(Packet<?> packet,ChannelFutureListener listener){capture(packet);}
        @Override public void send(Packet<?> packet,ChannelFutureListener listener,boolean flush){capture(packet);}
        @Override public boolean isConnected(){return true;}
    }
    AdvanceMagicPlugin magic;
    World world;
    Player caster, opponent;
    LivingEntity target;
    final List<ServerPlayer> actors=new ArrayList<>();
    final List<LivingEntity> mobs=new ArrayList<>();
    final Map<UUID,List<Double>> damage=new HashMap<>();
    final List<Map<String,Object>> scenes=new ArrayList<>();
    Team team;
    TestConnection casterConnection;
    int currentAge;
    final Map<UUID,List<Integer>> damageAges=new HashMap<>();
    boolean cancelCast,cancelAffect,teleportOnAffect,closeOnDamage;
    int basicEvents,mainEvents,basicSuccess,mainSuccess,passed;

    @Override public void onEnable(){Bukkit.getScheduler().runTaskLater(this,()->{
        try{begin();finish(null);}catch(Throwable error){finish(error);}
    },20);}
    void check(boolean value,String label){if(!value)throw new AssertionError(label);passed++;getLogger().info("PASS "+label);}
    @EventHandler(priority=EventPriority.HIGHEST) public void cast(MagicCastEvent event){
        if(event.isBasicCast())basicEvents++;else mainEvents++;
        if(cancelCast)event.setCancelled(true);
    }
    @EventHandler public void success(MagicCastSuccessEvent event){if(event.isBasicCast())basicSuccess++;else mainSuccess++;}
    @EventHandler(priority=EventPriority.HIGHEST) public void affect(MagicAffectEvent event){
        if(cancelAffect)event.setCancelled(true);
        if(teleportOnAffect&&event.getTarget()==target){teleportOnAffect=false;target.teleport(new Location(world,12.5,100,4.5));}
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void damage(EntityDamageByEntityEvent event){
        damage.computeIfAbsent(event.getEntity().getUniqueId(),key->new ArrayList<>()).add(event.getDamage());
        damageAges.computeIfAbsent(event.getEntity().getUniqueId(),key->new ArrayList<>()).add(currentAge);
        if(closeOnDamage){closeOnDamage=false;magic.effects().closeOwner(caster.getUniqueId());}
    }
    void begin(){
        magic=(AdvanceMagicPlugin)Bukkit.getPluginManager().getPlugin("advance-magic");
        check(magic!=null&&magic.isEnabled(),"release boots on Paper 26.2");
        Bukkit.getPluginManager().registerEvents(this,this);
        world=Bukkit.getWorlds().getFirst();world.setPVP(true);
        for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++)world.getChunkAt(x,z).setForceLoaded(true);
        for(int x=-32;x<=32;x++)for(int z=-32;z<=32;z++)world.getBlockAt(x,99,z).setType(Material.STONE);
        caster=actor("BasicCaster",new Location(world,.5,100,.5,0,0));
        opponent=actor("BasicOpponent",new Location(world,25.5,100,.5));
        for(Spell spell:Spell.values())catalog(spell);
        inputs();accounting();policies();limits();patterns();
        ManaPotionChecks.run(this);
        FilesWriteScenes();
    }
    Player actor(String name,Location at){
        var server=MinecraftServer.getServer();var level=((CraftWorld)world).getHandle();
        GameProfile profile=new GameProfile(UUID.randomUUID(),name);
        ServerPlayer handle=new ServerPlayer(server,level,profile,ClientInformation.createDefault());
        TestConnection connection=new TestConnection();if(name.equals("BasicCaster"))casterConnection=connection;
        handle.connection=new ServerGamePacketListenerImpl(server,connection,handle,CommonListenerCookie.createInitial(profile,false)){
            @Override public boolean hasClientLoaded(){return true;}
        };
        handle.setPos(at.getX(),at.getY(),at.getZ());
        server.getPlayerList().getPlayers().add(handle);server.getPlayerList().getPlayersByUUID().put(profile.id(),handle);level.addNewPlayer(handle);actors.add(handle);
        Player player=handle.getBukkitEntity();player.setGameMode(GameMode.SURVIVAL);player.setOp(true);player.setGravity(false);player.teleport(at);
        player.getAttribute(Attribute.MAX_HEALTH).setBaseValue(1000);player.setHealth(1000);player.setNoDamageTicks(0);player.setMaximumNoDamageTicks(0);return player;
    }
    LivingEntity mob(double x,double z){
        Vindicator mob=world.spawn(new Location(world,x,100,z),Vindicator.class);
        mob.setAI(false);mob.setGravity(false);mob.getAttribute(Attribute.MAX_HEALTH).setBaseValue(1000);mob.setHealth(1000);mobs.add(mob);return mob;
    }
    void reset(){
        magic.effects().close();magic.casts().quit(caster);
        for(LivingEntity mob:mobs)mob.remove();mobs.clear();
        for(Vex vex:world.getEntitiesByClass(Vex.class))vex.remove();
        if(team!=null){team.unregister();team=null;}
        caster.teleport(new Location(world,.5,100,.5,0,0));caster.setSneaking(false);
        opponent.teleport(new Location(world,25.5,100,.5));opponent.setHealth(1000);
        magic.getConfig().set("pvp",true);magic.getConfig().set("basic-casting.max-active-per-world",32);
        cancelCast=cancelAffect=teleportOnAffect=closeOnDamage=false;
        var account=magic.mana().account(caster);account.setMana(100);
        for(Spell spell:Spell.values())account.restore(spell.id(),0);
        account.restore(BasicSpellProfile.COOLDOWN_KEY,0);damage.clear();damageAges.clear();casterConnection.particles.clear();
    }
    ItemStack wand(Spell spell){ItemStack wand=magic.wands().create(spell);caster.getInventory().setItemInMainHand(wand);caster.getInventory().setItemInOffHand(new ItemStack(Material.AIR));return caster.getInventory().getItemInMainHand();}
    void click(ItemStack wand){
        PlayerInteractEvent event=new PlayerInteractEvent(caster,Action.RIGHT_CLICK_AIR,wand,null,BlockFace.SELF,EquipmentSlot.HAND);
        event.setUseItemInHand(Event.Result.ALLOW);Bukkit.getPluginManager().callEvent(event);
    }
    void ticks(int count){for(int i=0;i<count;i++){currentAge=i;casterConnection.particles.clear();magic.effects().tick();}}
    double total(LivingEntity target){return damage.getOrDefault(target.getUniqueId(),List.of()).stream().mapToDouble(Double::doubleValue).sum();}

    void catalog(Spell spell){
        reset();BasicSpellProfile profile=magic.spells().basic().profile(spell);
        target=mob(.5,profile.shape()==BasicSpellProfile.Shape.PULSE||spell==Spell.FROST_NOVA?2.5:6.5);
        ItemStack wand=wand(spell);int uses=magic.wands().usesLeft(wand),mastery=magic.wands().casts(wand),success=basicSuccess;
        click(wand);
        check(basicSuccess==success+1,"standing input selects basic "+spell.id());
        check(Math.abs(magic.mana().account(caster).manaExact()-(100-profile.mana()))<.001,"basic mana charged once "+spell.id());
        check(magic.mana().account(caster).remaining(spell.id(),System.currentTimeMillis())==0,"basic leaves main cooldown ready "+spell.id());
        check(magic.wands().usesLeft(wand)==uses&&magic.wands().casts(wand)==mastery,"basic preserves durability and mastery "+spell.id());
        int peak=0;
        for(int age=0;age<80;age++){
            currentAge=age;casterConnection.particles.clear();
            magic.effects().tick();peak=Math.max(peak,world.getEntitiesByClass(ItemDisplay.class).size());
            if(List.of(0,4,8,12,16,24).contains(age))snapshot(spell,age);
        }
        check(Math.abs(total(target)-profile.damage())<.001,"one capped damage budget "+spell.id()+" actual="+total(target));
        check(total(target)<=profile.damage()+.001,"multiple hits share the configured budget "+spell.id());
        check(magic.effects().size()==0&&world.getEntitiesByClass(ItemDisplay.class).isEmpty(),"basic cleans its effects "+spell.id());
        if(spell.isLegendary())check(peak>=3&&peak<=20,"legendary uses bounded authored art "+spell.id()+" displays="+peak);
        if(spell==Spell.SHULKER_LEVITATION)check(peak>=2,"Shulker has a solid authored prism in addition to its particle cage");
        check(wand.getItemMeta().getLore().stream().anyMatch(line->line.contains("Basic:")),"wand explains both modes "+spell.id());
        reset();target=mob(.5,6.5);wand=wand(spell);caster.setSneaking(true);int mains=mainSuccess;
        click(wand);
        check(mainSuccess==mains+1,"sneaking input selects original main "+spell.id());
        check(Math.abs(magic.mana().account(caster).manaExact()-(100-spell.mana))<.001,"main keeps original mana "+spell.id());
        check(magic.wands().usesLeft(wand)==29&&magic.wands().casts(wand)==1,"main consumes one use and gains mastery "+spell.id());
        check(magic.mana().account(caster).remaining(BasicSpellProfile.COOLDOWN_KEY,System.currentTimeMillis())==0,"main leaves basic cooldown ready "+spell.id());
    }
    void inputs(){
        for(boolean sneaking:new boolean[]{false,true})for(int input=0;input<4;input++){
            reset();target=mob(.5,6.5);ItemStack wand=wand(Spell.LIGHTNING_STRIKE);caster.setSneaking(sneaking);
            int before=sneaking?mainSuccess:basicSuccess;
            switch(input){
                case 0->Bukkit.getPluginManager().callEvent(new PlayerAnimationEvent(caster));
                case 1->Bukkit.getPluginManager().callEvent(new PlayerSwapHandItemsEvent(caster,new ItemStack(Material.AIR),wand));
                case 2->Bukkit.getPluginManager().callEvent(new PlayerInteractEntityEvent(caster,target,EquipmentSlot.HAND));
                case 3->Bukkit.getPluginManager().callEvent(new EntityDamageByEntityEvent(caster,target,org.bukkit.event.entity.EntityDamageEvent.DamageCause.ENTITY_ATTACK,1));
            }
            check((sneaking?mainSuccess:basicSuccess)==before+1,"input path="+input+" respects sneak="+sneaking);
        }
        reset();target=mob(.5,6.5);ItemStack wand=wand(Spell.BLAZE_BARRAGE);int before=basicSuccess;
        click(wand);Bukkit.getPluginManager().callEvent(new PlayerAnimationEvent(caster));
        check(basicSuccess==before+1,"interact and swing packets do not double-charge one tap");
        reset();target=mob(.5,6.5);wand=wand(Spell.GUARDIAN_BEAM);caster.getInventory().setItemInMainHand(new ItemStack(Material.AIR));caster.getInventory().setItemInOffHand(wand);
        before=basicSuccess;Bukkit.getPluginManager().callEvent(new PlayerAnimationEvent(caster));
        check(basicSuccess==before+1,"offhand wand supports basic input");
    }
    void accounting(){
        reset();target=mob(.5,6.5);ItemStack wand=wand(Spell.BLAZE_BARRAGE);click(wand);
        double mana=magic.mana().account(caster).manaExact();magic.casts().quit(caster);ItemStack second=wand(Spell.LUNAR_REQUIEM);int before=basicSuccess;click(second);
        check(basicSuccess==before&&magic.mana().account(caster).manaExact()==mana,"switching wands cannot bypass shared cooldown");
        magic.casts().quit(caster);caster.setSneaking(true);second=wand(Spell.GUARDIAN_BEAM);before=mainSuccess;click(second);
        check(mainSuccess==before+1,"basic cooldown does not block the main spell");
        reset();target=mob(.5,6.5);wand=wand(Spell.BLAZE_BARRAGE);magic.mana().account(caster).setMana(2.9);before=basicSuccess;click(wand);
        check(basicSuccess==before&&magic.mana().account(caster).manaExact()==2.9,"fractional insufficient mana cannot launch a basic attack");
        reset();target=mob(.5,6.5);wand=wand(Spell.BLAZE_BARRAGE);cancelCast=true;click(wand);
        check(magic.mana().account(caster).manaExact()==100&&magic.effects().size()==0,"cancelled cast consumes no mana or effect slot");
        reset();long end=System.currentTimeMillis()+5000;magic.mana().account(caster).restore(BasicSpellProfile.COOLDOWN_KEY,end);magic.mana().quit(caster);
        check(magic.mana().account(caster).end(BasicSpellProfile.COOLDOWN_KEY)==end,"basic cooldown persists through account reconnect");
        reset();target=mob(.5,6.5);wand=wand(Spell.BLAZE_BARRAGE);
        var meta=wand.getItemMeta();meta.getPersistentDataContainer().set(new NamespacedKey(magic,"damage_level"),org.bukkit.persistence.PersistentDataType.INTEGER,10);wand.setItemMeta(meta);
        click(wand);ticks(48);
        check(Math.abs(total(target)-18.2)<.001,"delayed bolt captures the 30 percent damage upgrade");
        check(magic.context().getCastDamageMultiplier(caster.getUniqueId())==1,"basic damage clears its temporary upgrade context");
    }
    void policies(){
        for(Spell spell:List.of(Spell.BLAZE_BARRAGE,Spell.GUARDIAN_BEAM,Spell.VERDANT_SOVEREIGN,Spell.LUNAR_REQUIEM,Spell.CRIMSON_OBLIVION)){
            reset();target=mob(.5,6.5);for(int x=-1;x<=1;x++)for(int y=100;y<=103;y++)world.getBlockAt(x,y,3).setType(Material.STONE);
            click(wand(spell));ticks(48);check(total(target)==0,"solid wall blocks "+spell.id());
            for(int x=-1;x<=1;x++)for(int y=100;y<=103;y++)world.getBlockAt(x,y,3).setType(Material.AIR);
        }
        reset();target=mob(.5,6.5);team=Bukkit.getScoreboardManager().getMainScoreboard().registerNewTeam("basic-allies");team.addEntry(caster.getName());team.addEntry(target.getUniqueId().toString());
        click(wand(Spell.BLAZE_BARRAGE));ticks(48);check(total(target)==0,"basic skips scoreboard allies");
        reset();target=mob(.5,6.5);cancelAffect=true;click(wand(Spell.GUARDIAN_BEAM));ticks(48);check(total(target)==0,"cancelled affect event blocks basic damage");
        reset();target=mob(.5,6.5);teleportOnAffect=true;click(wand(Spell.LIGHTNING_STRIKE));ticks(80);check(total(target)==0,"target moved by permission event must still intersect the attack");
        reset();target=mob(.5,6.5);closeOnDamage=true;click(wand(Spell.LUNAR_REQUIEM));ticks(48);
        check(Math.abs(total(target)-8)<.001&&magic.effects().size()==0&&world.getEntitiesByClass(ItemDisplay.class).isEmpty(),"closing during damage stops remaining lunar shots and cannot leak displays");
        reset();opponent.teleport(new Location(world,.5,100,6.5));click(wand(Spell.LUNAR_REQUIEM));ticks(48);
        check(Math.abs(total(opponent)-14.4)<.001,"legendary basic PvP budget is reduced to 14.4 actual="+total(opponent)+" target="+magic.context().targetEntity(caster,24));
        reset();opponent.teleport(new Location(world,.5,100,6.5));magic.getConfig().set("pvp",false);click(wand(Spell.GUARDIAN_BEAM));ticks(48);
        check(total(opponent)==0,"basic honors disabled PvP");
        reset();click(wand(Spell.CRIMSON_OBLIVION));magic.effects().tick();check(!world.getEntitiesByClass(ItemDisplay.class).isEmpty(),"in-flight Crimson creates authored shards");
        magic.effects().closeOwner(caster.getUniqueId());check(world.getEntitiesByClass(ItemDisplay.class).isEmpty(),"owner cleanup removes in-flight basic artwork");
    }
    void limits(){
        reset();magic.getConfig().set("basic-casting.max-active-per-world",1);click(wand(Spell.BLAZE_BARRAGE));
        magic.casts().quit(caster);magic.mana().account(caster).restore(BasicSpellProfile.COOLDOWN_KEY,0);double mana=magic.mana().account(caster).manaExact();int before=basicSuccess;
        click(wand(Spell.FROST_NOVA));check(basicSuccess==before&&magic.mana().account(caster).manaExact()==mana,"world capacity rejection does not charge mana");
        reset();for(int i=0;i<8;i++)mob(.5+Math.cos(i*Math.PI/4)*2,.5+Math.sin(i*Math.PI/4)*2);
        click(wand(Spell.IRON_ARMOR));ticks(80);
        check(mobs.stream().filter(mob->total(mob)>0).count()==6,"nearby pulse hits at most six opponents");
        reset();target=mob(2.5,.5);world.getBlockAt(1,100,0).setType(Material.STONE);world.getBlockAt(1,101,0).setType(Material.STONE);
        click(wand(Spell.IRON_ARMOR));ticks(48);check(total(target)==0,"nearby pulse cannot damage through a wall");
        world.getBlockAt(1,100,0).setType(Material.AIR);world.getBlockAt(1,101,0).setType(Material.AIR);
    }
    void patterns(){
        reset();target=mob(.5,6.5);click(wand(Spell.BLAZE_BARRAGE));ticks(80);
        var ages=damageAges.get(target.getUniqueId());
        check(ages.size()==3&&ages.get(2)-ages.getFirst()>=5,"Blaze emits three separately timed shots within its 14 damage budget");
        reset();target=mob(.5,6.5);click(wand(Spell.GUARDIAN_BEAM));ticks(80);
        check(damageAges.get(target.getUniqueId()).equals(List.of(2,6,10)),"Guardian is a three-pulse locked channel");
        reset();target=mob(.5,6.5);click(wand(Spell.SOLAR_APOCALYPSE));ticks(80);
        check(damageAges.get(target.getUniqueId()).equals(List.of(8)),"Solar charges for eight ticks before discharging");
        reset();target=mob(.5,6.5);click(wand(Spell.HEAVENS_JUDGMENT));ticks(80);
        check(damageAges.get(target.getUniqueId()).equals(List.of(8)),"Judgment drops its column onto the captured target point");
        reset();target=mob(.5,3.5);LivingEntity behind=mob(.5,-1.5);click(wand(Spell.SHADOW_STEP));ticks(80);
        check(total(target)==14&&total(behind)==0,"Shadow's directional slash excludes opponents behind the caster");
        reset();target=mob(.5,5.5);LivingEntity rear=mob(.5,9.5);click(wand(Spell.SONIC_BOOM));ticks(80);
        check(total(target)==12&&total(rear)==12,"Sonic's traveling wave pierces aligned opponents");
        reset();target=mob(1.3,6.5);click(wand(Spell.FROST_NOVA));ticks(80);
        check(total(target)>0&&total(target)<=14,"Frost's spread can hit an opponent away from the centre line");
        reset();target=mob(.5,6.5);click(wand(Spell.DRAGONS_BREATH));ticks(80);
        check(damageAges.get(target.getUniqueId()).equals(List.of(0,3,6)),"Dragon's short cone has three breath pulses");
        reset();target=mob(.5,6.5);click(wand(Spell.VERDANT_SOVEREIGN));ticks(80);
        check(damageAges.get(target.getUniqueId()).size()==3&&Math.abs(total(target)-24)<.001,"Verdant's three moving waves split its 24 damage budget");
        reset();target=mob(.5,6.5);click(wand(Spell.CHRONOS_FINAL_HOUR));ticks(80);
        check(damageAges.get(target.getUniqueId()).size()==2&&Math.abs(total(target)-18)<.001,"Chronos emits a primary shot and a weaker delayed echo");
        reset();target=mob(.5,6.5);click(wand(Spell.CRIMSON_OBLIVION));ticks(80);
        check(damageAges.get(target.getUniqueId()).size()==1&&total(target)==24,"Crimson retains one shared impact for its original three decorative shards");
    }

    void snapshot(Spell spell,int age){
        List<Map<String,Object>> displays=new ArrayList<>();
        for(ItemDisplay display:world.getEntitiesByClass(ItemDisplay.class)){
            var transform=display.getTransformation();Location at=display.getLocation();
            displays.add(Map.of("model",display.getItemStack().getItemMeta().getItemModel().toString(),
                    "position",List.of(at.getX(),at.getY(),at.getZ()),
                    "scale",List.of(transform.getScale().x(),transform.getScale().y(),transform.getScale().z()),
                    "rotation",List.of(transform.getLeftRotation().x(),transform.getLeftRotation().y(),transform.getLeftRotation().z(),transform.getLeftRotation().w())));
        }
        scenes.add(Map.of("spell",spell.id(),"age",age,"displays",displays,"particles",List.copyOf(casterConnection.particles)));
    }
    void FilesWriteScenes(){
        try{Files.writeString(Path.of("basic-runtime-scenes.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(scenes));}
        catch(Exception error){throw new RuntimeException(error);}
    }
    void finish(Throwable error){
        if(error!=null)getLogger().log(java.util.logging.Level.SEVERE,"BASIC CASTING FAILED after "+passed,error);
        try{Files.writeString(Path.of("basic-casting-result.txt"),error==null?"PASS "+passed:"FAIL "+error);}catch(Exception ignored){}
        if(magic!=null)magic.effects().close();if(team!=null)team.unregister();
        for(ServerPlayer actor:actors){MinecraftServer.getServer().getPlayerList().getPlayers().remove(actor);MinecraftServer.getServer().getPlayerList().getPlayersByUUID().remove(actor.getUUID());actor.discard();}
        Bukkit.getScheduler().runTaskLater(this,Bukkit::shutdown,5);
    }
}
