import com.example.voidscape.VoidscapePlugin;
import com.example.voidscape.boss.JudgePackService;
import com.example.voidscape.pet.*;
import com.example.voidscape.world.ExplorationLayout;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.server.network.*;
import org.bukkit.*;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.plugin.java.JavaPlugin;
import java.lang.reflect.Method;
import org.bukkit.World;
import java.net.InetSocketAddress;
import java.nio.file.*;
import java.util.*;

/** Diagnostic on a copy of the real Evergarden: where do natural wild-pet spots succeed or fail, and why. */
public final class WildSpawnTerrainProbe extends JavaPlugin {
    VoidscapePlugin garden;World world;ServerPlayer actor;Player player;WildPetSpawns wild;
    final List<String> lines=new ArrayList<>();int waited,index;boolean finished;List<ExplorationLayout.Kind> kinds;
    void later(long ticks,Runnable work){Bukkit.getScheduler().runTaskLater(this,()->{try{work.run();}catch(Throwable error){finish(error);}},ticks);}
    Object call(Object target,String name,Object... args)throws Exception{
        for(Method m:target.getClass().getDeclaredMethods())if(m.getName().equals(name)&&m.getParameterCount()==args.length){m.setAccessible(true);return m.invoke(target,args);}
        throw new NoSuchMethodException(name);
    }
    Object get(Object target,String name)throws Exception{var f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}
    void note(String line){lines.add(line);getLogger().info(line);}
    @Override public void onEnable(){later(1,()->{garden=(VoidscapePlugin)Bukkit.getPluginManager().getPlugin("Evergarden");world=garden.world();wild=garden.wildPets();awaitReady();});}
    void awaitReady(){
        if(garden.arkhaiaModels().ready()&&garden.judgePack().nativePetsIncluded()){prepare();return;}
        if((waited+=10)>2400)throw new AssertionError("native pet pack not ready");
        later(10,this::awaitReady);
    }
    void prepare(){
        garden.getConfig().set("pets.wild-spawns.chance-per-player",0.0);wild.reload();
        var server=MinecraftServer.getServer();var level=((CraftWorld)world).getHandle();
        var profile=new GameProfile(UUID.randomUUID(),"TerrainWalker");var handle=new ServerPlayer(server,level,profile,ClientInformation.createDefault());
        var connection=new Connection(PacketFlow.SERVERBOUND);connection.channel=new EmbeddedChannel();connection.address=new InetSocketAddress("127.0.0.1",1);
        handle.connection=new ServerGamePacketListenerImpl(server,connection,handle,CommonListenerCookie.createInitial(profile,false)){@Override public boolean hasClientLoaded(){return true;}};
        handle.setPos(0,100,0);server.getPlayerList().getPlayers().add(handle);server.getPlayerList().getPlayersByUUID().put(profile.id(),handle);level.addNewPlayer(handle);
        actor=handle;player=handle.getBukkitEntity();player.setGravity(false);player.setGameMode(GameMode.SURVIVAL);
        Bukkit.getPluginManager().callEvent(new PlayerResourcePackStatusEvent(player,JudgePackService.PACK_ID,PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED));
        note("renderer: "+wild.status());
        kinds=new ArrayList<>(List.of(ExplorationLayout.Kind.values()));
        kinds.sort(Comparator.comparing(k->k==ExplorationLayout.Kind.ICEBOUND_OUTPOST?0:1));
        next();
    }
    void next(){try{
        if(index>=Math.min(kinds.size(),10)){later(2,this::walk);return;}
        var kind=kinds.get(index++);
        var site=garden.exploration().nearest(kind,0,0,48,true);
        if(site==null&&kind.medium())site=garden.exploration().nearest(kind,0,0,96,true);
        if(site==null){note(kind.id+": no site");later(1,this::next);return;}
        int[] entry=site.transform(0,site.entryZ());
        Location arrival=new Location(world,entry[0]+.5,site.floorY()+1,entry[1]+.5);
        for(int cx=-5;cx<=5;cx++)for(int cz=-5;cz<=5;cz++)world.getChunkAt((entry[0]>>4)+cx,(entry[1]>>4)+cz).addPluginChunkTicket(this);
        player.teleport(arrival);if(walkStart==null)walkStart=arrival.clone();
        var kindFinal=kind;
        later(5,()->{try{measure(kindFinal,arrival);}catch(Exception error){throw new RuntimeException(error);}});
    }catch(Exception error){throw new RuntimeException(error);}}
    void measure(ExplorationLayout.Kind kind,Location arrival)throws Exception{
        // Admin preview: the same search the command uses, six blocks ahead.
        Location target=arrival.clone().add(0,0,6);
        Object found=call(wild,"nearestSafe",target,PetCatalog.Stage.BABY);
        note(kind.id+" @ "+arrival.getBlockX()+","+arrival.getBlockY()+","+arrival.getBlockZ()+" · admin spawn ahead: "+(found==null?"NONE":"ok "+((Location)found).toVector()));
        int ok=0;Map<String,Integer> reasons=new TreeMap<>();
        for(int i=0;i<200;i++){
            Object spot=call(wild,"findNaturalSpot",player,PetCatalog.Stage.BABY);
            if(spot!=null)ok++;else reasons.merge((String)get(wild,"lastRoll"),1,Integer::sum);
        }
        note(kind.id+" · natural rolls finding a spot: "+ok+"/200"+(reasons.isEmpty()?"":" · e.g. "+reasons.keySet().iterator().next()));
        boolean spawned=wild.spawn(PetCatalog.Species.FROST_WYVERN,PetCatalog.Stage.BABY,(Location)(found==null?arrival:found));
        note(kind.id+" · spawn at admin spot: "+spawned+(spawned?"":" · "+get(wild,"lastFailure")));
        wild.clear();
        later(2,this::next);
    }
    Location walkStart,here;int steps,spawned;final Map<String,Integer> outcomes=new TreeMap<>();
    final java.util.Random walker=new java.util.Random(20261022);
    /** A player wandering 30 blocks at a time across the real terrain, rolling a natural spawn each stop. */
    void walk(){try{
        if(here==null){here=walkStart==null?null:walkStart.clone();garden.getConfig().set("pets.wild-spawns.chance-per-player",1.0);
            garden.getConfig().set("pets.wild-spawns.guarantee-minutes",0);
            if(here==null){finish(null);return;}}
        if(steps>=80){
            note("walk: natural spawns "+spawned+"/"+steps+" stops");
            outcomes.forEach((k,v)->note("walk outcome x"+v+": "+k));
            finish(null);return;
        }
        Method floor=Class.forName("com.example.voidscape.pet.PetTerrain").getDeclaredMethod("floorBetween",World.class,int.class,int.class,int.class,int.class);floor.setAccessible(true);
        Location next=null;
        for(int tries=0;tries<12&&next==null;tries++){
            double a=walker.nextDouble()*Math.PI*2;int x=(int)(here.getX()+Math.cos(a)*30),z=(int)(here.getZ()+Math.sin(a)*30);
            world.getChunkAt(x>>4,z>>4);
            double y=(double)floor.invoke(null,world,x,z,here.getBlockY()+20,here.getBlockY()-20);
            if(!Double.isNaN(y))next=new Location(world,x+.5,y,z+.5);
        }
        if(next==null){note("walk: stranded at "+here.toVector());steps=80;later(1,this::walk);return;}
        world.removePluginChunkTickets(this);
        for(int cx=-4;cx<=4;cx++)for(int cz=-4;cz<=4;cz++)world.getChunkAt((next.getBlockX()>>4)+cx,(next.getBlockZ()>>4)+cz).addPluginChunkTicket(this);
        here=next;player.teleport(here);
        later(2,()->{try{
            wild.clear();call(wild,"attemptSpawns");steps++;
            if(wild.count()>0)spawned++;
            @SuppressWarnings("unchecked") Map<UUID,String> rolls=(Map<UUID,String>)get(wild,"rolls");
            String outcome=rolls.getOrDefault(player.getUniqueId(),"(none)").replaceAll("[0-9-]+ [0-9]+ [0-9-]+$","").replaceAll(" ×[0-9]+","");
            outcomes.merge(outcome.length()>90?outcome.substring(0,90):outcome,1,Integer::sum);
            wild.clear();later(1,this::walk);
        }catch(Exception error){throw new RuntimeException(error);}});
    }catch(Exception error){throw new RuntimeException(error);}}
    void finish(Throwable error){
        if(finished)return;finished=true;if(error!=null){error.printStackTrace();lines.add("ERROR: "+error);}
        try{var server=MinecraftServer.getServer();server.getPlayerList().getPlayers().remove(actor);server.getPlayerList().getPlayersByUUID().remove(actor.getUUID());actor.discard();}catch(Exception ignored){}
        try{Files.writeString(Path.of("model-engine-result.txt"),(error==null?"PASS":"FAIL")+": wild spawn terrain diagnostic\n"+String.join("\n",lines)+"\n");}catch(Exception failure){failure.printStackTrace();}
        Bukkit.shutdown();
    }
}
