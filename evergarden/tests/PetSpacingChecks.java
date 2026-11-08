import com.example.voidscape.VoidscapePlugin;
import com.example.voidscape.boss.JudgePackService;
import com.example.voidscape.pet.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.plugin.java.JavaPlugin;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;

/** Behavioral regression: two real companion sculptures must spawn and travel separately. */
public final class PetSpacingChecks extends JavaPlugin {
    VoidscapePlugin garden; World world; Player owner;
    net.minecraft.server.level.ServerPlayer actor;
    Object companions,stable,first,second;
    int checks,waited; boolean finished;
    final List<String> lines=new ArrayList<>();
    final Location[] previous=new Location[2];
    void check(boolean ok,String label){if(!ok)throw new AssertionError(label);checks++;lines.add("PASS: "+label);}
    static Field field(Object target,String name)throws Exception{
        for(Class<?> c=target.getClass();c!=null;c=c.getSuperclass())try{Field f=c.getDeclaredField(name);f.setAccessible(true);return f;}catch(NoSuchFieldException ignored){}
        throw new NoSuchFieldException(name);
    }
    static Object get(Object target,String name)throws Exception{return field(target,name).get(target);}
    static void put(Object target,String name,Object value)throws Exception{field(target,name).set(target,value);}
    static Object call(Object target,String name,Object... args)throws Exception{
        for(Method m:target.getClass().getDeclaredMethods())if(m.getName().equals(name)&&m.getParameterCount()==args.length){m.setAccessible(true);return m.invoke(target,args);}
        throw new NoSuchMethodException(name);
    }
    void later(int ticks,Runnable work){Bukkit.getScheduler().runTaskLater(this,()->{try{work.run();}catch(Throwable e){finish(e);}},ticks);}
    @Override public void onEnable(){later(20,()->{
        garden=(VoidscapePlugin)Bukkit.getPluginManager().getPlugin("Evergarden");
        check(garden!=null&&garden.isEnabled()&&garden.pets()!=null,"production pet system boots");
        world=garden.world();awaitReady();
    });}
    void awaitReady(){try{
        if(garden.judgePack().nativePetsIncluded()&&((NativePetRenderer)get(garden.pets(),"renderer")).ready()){
            setup();run();finish(null);return;
        }
        if((waited+=10)>1800)throw new AssertionError("native pet resource pack never became ready");
        later(10,this::awaitReady);
    }catch(Throwable e){finish(e);}}
    Location home(){return new Location(world,1536.5,180,1536.5);}
    void setup()throws Exception{
        companions=get(garden.pets(),"companions");
        garden.getConfig().set("pets.wild-spawns.chance-per-player",0.0);garden.wildPets().reload();
        for(int x=1500;x<=1580;x++)for(int z=1500;z<=1580;z++){
            world.getChunkAt(x>>4,z>>4).addPluginChunkTicket(this);
            world.getBlockAt(x,179,z).setType(Material.STONE,false);
            for(int y=180;y<=188;y++)world.getBlockAt(x,y,z).setType(Material.AIR,false);
        }
        var server=net.minecraft.server.MinecraftServer.getServer();var level=((org.bukkit.craftbukkit.CraftWorld)world).getHandle();
        var profile=new com.mojang.authlib.GameProfile(UUID.randomUUID(),"PetSpacingTest");
        actor=new net.minecraft.server.level.ServerPlayer(server,level,profile,net.minecraft.server.level.ClientInformation.createDefault());
        var connection=new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        connection.channel=new io.netty.channel.embedded.EmbeddedChannel();connection.address=new java.net.InetSocketAddress("127.0.0.1",1);
        actor.connection=new net.minecraft.server.network.ServerGamePacketListenerImpl(server,connection,actor,net.minecraft.server.network.CommonListenerCookie.createInitial(profile,false)){
            @Override public boolean hasClientLoaded(){return true;}
        };
        actor.setPos(home().getX(),180,home().getZ());server.getPlayerList().getPlayers().add(actor);server.getPlayerList().getPlayersByUUID().put(profile.id(),actor);level.addNewPlayer(actor);
        owner=actor.getBukkitEntity();owner.setGravity(false);owner.setInvulnerable(true);owner.setGameMode(GameMode.SURVIVAL);
        Bukkit.getPluginManager().callEvent(new PlayerResourcePackStatusEvent(owner,JudgePackService.PACK_ID,PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED));
        stable=call(companions,"stable",owner);
    }
    PetRecord record(PetCatalog.Species species,PetCatalog.Stage stage,long id)throws Exception{
        var constructor=PetRecord.class.getDeclaredConstructor(UUID.class,PetCatalog.Species.class,PetCatalog.Stage.class);constructor.setAccessible(true);
        return constructor.newInstance(new UUID(0,id),species,stage);
    }
    @SuppressWarnings("unchecked") void pair(PetCatalog.Stage stage,boolean flying,String mode)throws Exception{
        call(companions,"dismiss",owner);((Map<?,?>)get(stable,"pets")).clear();owner.teleport(home());
        PetRecord a=record(flying?PetCatalog.Species.ASTRAL_OWL:PetCatalog.Species.SHADOW_PANTHER,stage,2);
        PetRecord b=record(flying?PetCatalog.Species.AURORA_JELLY:PetCatalog.Species.CRYSTAL_TORTOISE,stage,4);
        put(a,"mode",mode);call(stable,"add",a);call(stable,"add",b);
        check(call(companions,"summon",owner,a.id())==null,"first pet summons "+stage+" "+mode);
        check(call(companions,"summon",owner,b.id())==null,"same-parity second pet summons "+stage+" "+mode);
        first=((Map<UUID,?>)get(companions,"out")).get(owner.getUniqueId());second=((Map<UUID,?>)get(companions,"secondaryOut")).get(owner.getUniqueId());
        check(first!=null&&second!=null,"both companion bodies exist");
        check(distance()>=clearance(),"summoning never stacks sculptures; distance="+distance());
        resetMotion();
    }
    Location at(Object body)throws Exception{return (Location)call(body,"location");}
    double distance()throws Exception{return at(first).distance(at(second));}
    double clearance()throws Exception{
        return (double)call(companions,"spacing",get(first,"stage"),get(first,"record"),second);
    }
    void resetMotion()throws Exception{previous[0]=at(first);previous[1]=at(second);}
    void tick(boolean smooth)throws Exception{
        call(companions,"follow");
        int i=0;for(Object body:List.of(first,second)){
            Location now=at(body);
            if(smooth)check(now.distance(previous[i])<.65,"separation uses smooth strides, not short-range teleports");
            check(world.getBlockAt(now.getBlockX(),now.getBlockY()-1,now.getBlockZ()).getType().isSolid()||(boolean)get(body,"airborne")||(boolean)get(body,"perched"),"pet stays on safe footing, deliberate flight or head perch");
            previous[i++]=now;
        }
    }
    void ticks(int count,boolean smooth)throws Exception{for(int i=0;i<count;i++)tick(smooth);}
    void overlap()throws Exception{
        Location shared=home().add(0,0,-2.4);call(first,"move",shared);call(second,"move",shared);
        put(first,"speed",0d);put(second,"speed",0d);resetMotion();
        check(distance()<.001,"fixture reproduces exact overlap beside stationary owner");
    }
    void settled(String label)throws Exception{
        check(distance()>=clearance()-.04,label+" separates; distance="+distance()+", required="+clearance());
        Location a=at(first),b=at(second);ticks(80,true);
        check(at(first).distance(a)<.15&&at(second).distance(b)<.15,label+" rests without oscillating");
        check(a.distance(owner.getLocation())<9&&b.distance(owner.getLocation())<9,label+" remains near owner");
    }
    void run()throws Exception{
        for(PetCatalog.Stage stage:PetCatalog.Stage.values()){
            pair(stage,false,"follow");overlap();ticks(240,true);settled(stage+" stationary overlap");
        }
        for(String mode:List.of("idle","stay")){
            pair(PetCatalog.Stage.ADULT,false,mode);overlap();Location anchor=at(first);ticks(240,true);
            check(at(first).distance(anchor)<.01,"primary "+mode+" holds its position while second yields");settled(mode);
        }
        pair(PetCatalog.Stage.ADULT,false,"follow");
        for(int i=0;i<320;i++){
            Location next=home().add(i<160?0:(i-160)*.12,0,i<160?i*.12:19.2);next.setYaw(i<160?0:-90);owner.teleport(next);tick(true);
            check(distance()>=clearance()-.2,"walking and turning keep separate sculptures tick "+i+"; distance="+distance());
        }
        ticks(160,true);settled("after walking and turning");
        owner.teleport(home().add(-28,0,28));ticks(2,false);
        check(distance()>=clearance()-.04,"long-distance rescue chooses separate safe spots");
        check(at(first).distance(owner.getLocation())<8&&at(second).distance(owner.getLocation())<8,"both rescued pets catch up to owner");
        pair(PetCatalog.Stage.ADULT,true,"follow");owner.setAllowFlight(true);owner.setFlying(true);owner.teleport(home().add(0,4,0));
        Location shared=home().add(0,4,-2.4);call(first,"move",shared);call(second,"move",shared);
        put(first,"airborne",true);put(second,"airborne",true);resetMotion();ticks(240,true);
        check(distance()>=clearance()-.04,"two flying companions separate while owner hovers");
        check((boolean)get(first,"airborne")&&(boolean)get(second,"airborne"),"spacing preserves deliberate flight");
        owner.setFlying(false);owner.teleport(home());ticks(240,true);settled("flyers after landing");
        pair(PetCatalog.Stage.ADULT,false,"follow");
        call(companions,"mode",owner,((PetRecord)get(first,"record")).id(),"perch");
        first=((Map<?,?>)get(companions,"out")).get(owner.getUniqueId());
        check((boolean)get(first,"perched"),"head perch is preserved with second companion");
        resetMotion();ticks(30,true);
        call(companions,"dismiss",owner,((PetRecord)get(second,"record")).id());
        check(((Map<?,?>)get(companions,"secondaryOut")).isEmpty(),"dismissing second pet clears pair state");
    }
    void finish(Throwable error){
        if(finished)return;finished=true;
        if(error!=null)getLogger().log(java.util.logging.Level.SEVERE,"PET SPACING CHECK FAILED",error);
        String report=(error==null?"PASS ":"FAIL ")+checks+" checks; pair spawn, same UUID parity, stationary overlap, all ages, idle/stay, smooth walking/turning, rescue, flight/landing, perch and dismissal"+(error==null?"":": "+error);
        try{Files.writeString(Path.of("pet-grounding-result.txt"),report+"\n"+String.join("\n",lines)+"\n");}catch(Exception e){throw new RuntimeException(e);}
        if(actor!=null){actor.discard();var list=net.minecraft.server.MinecraftServer.getServer().getPlayerList();list.getPlayers().remove(actor);list.getPlayersByUUID().remove(actor.getUUID());}
        getLogger().info(report);Bukkit.getScheduler().runTaskLater(this,Bukkit::shutdown,1);
    }
}
