import com.example.voidscape.VoidscapePlugin;
import com.example.voidscape.pet.*;
import com.example.voidscape.boss.JudgePackService;
import com.google.gson.*;
import com.ticxo.modelengine.api.ModelEngineAPI;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.server.network.*;
import org.bukkit.*;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.world.*;
import org.bukkit.event.player.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import java.net.*;
import java.io.*;
import java.lang.reflect.Method;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/** Real Paper regression for scarce, neutral pets alongside the existing boss rigs. */
public final class WildPetsNativeProbe extends JavaPlugin {
    static final class ExternalSpawnProtection implements Listener {
        @EventHandler(priority=EventPriority.LOWEST) public void cancel(CreatureSpawnEvent event){event.setCancelled(true);}
    }
    static final List<String> BOSSES=List.of("arkhaia","arkhaia_acheron","arkhaia_phlegethon","arkhaia_cocytus",
        "arkhaia_banshee_wail","arkhaia_malefeasance","arkhaia_styx","arkhaia_acheron_echo");
    VoidscapePlugin garden;WildPetSpawns wild;World world;Location home;ServerPlayer actor;Player player;
    final List<String> lines=new ArrayList<>();final List<UUID> previousDisplays=new ArrayList<>();
    final List<PetCatalog.Species> species=new ArrayList<>();int checks,waited,index;boolean finished;
    Rabbit shell;List<ItemDisplay> displays=List.of();
    void check(boolean value,String label){if(!value)throw new AssertionError(label);checks++;lines.add("PASS: "+label);getLogger().info("PASS: "+label);}
    void later(long ticks,Runnable work){Bukkit.getScheduler().runTaskLater(this,()->{try{work.run();}catch(Throwable error){finish(error);}},ticks);}
    Object field(Object object,String name)throws Exception{var f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}
    void set(Object object,String name,Object value)throws Exception{var f=object.getClass().getDeclaredField(name);f.setAccessible(true);f.set(object,value);}
    Object bodies()throws Exception{return field(garden.pets(),"bodies");}
    void refresh()throws Exception{var m=bodies().getClass().getDeclaredMethod("refresh");m.setAccessible(true);m.invoke(bodies());}
    long transforms()throws Exception{var r=field(garden.pets(),"renderer");return ((NativePetRenderer)r).transformsSent();}
    void invoke(String name)throws Exception{var m=wild.getClass().getDeclaredMethod(name);m.setAccessible(true);m.invoke(wild);}
    /** One behaviour decision: the wild service glides every tick and decides every fifth. */
    void step()throws Exception{for(int i=0;i<5;i++)invoke("tick");}
    @SuppressWarnings("unchecked") Map<UUID,Object> active()throws Exception{return (Map<UUID,Object>)field(wild,"active");}
    NativePetRenderer.Rig rig()throws Exception{return (NativePetRenderer.Rig)field(active().values().iterator().next(),"rig");}
    @Override public void onEnable(){later(1,()->{
        garden=(VoidscapePlugin)Bukkit.getPluginManager().getPlugin("Evergarden");
        check(garden!=null&&garden.isEnabled(),"Evergarden enables with wild pets");wild=garden.wildPets();
        check(wild!=null,"wild pet service registered");world=garden.world();awaitReady();
    });}
    void awaitReady(){
        if(garden.arkhaiaModels().ready()&&garden.judgePack().nativePetsIncluded()){prepare();return;}
        if((waited+=10)>1800)throw new AssertionError("normal import/native pet pack not ready: "+wild.status());
        later(10,this::awaitReady);
    }
    ServerPlayer fake(Location at){
        var server=MinecraftServer.getServer();var level=((CraftWorld)world).getHandle();
        var profile=new GameProfile(UUID.randomUUID(),"WildPetExplorer");var handle=new ServerPlayer(server,level,profile,ClientInformation.createDefault());
        var connection=new Connection(PacketFlow.SERVERBOUND);connection.channel=new EmbeddedChannel();connection.address=new InetSocketAddress("127.0.0.1",1);
        handle.connection=new ServerGamePacketListenerImpl(server,connection,handle,CommonListenerCookie.createInitial(profile,false)){
            @Override public boolean hasClientLoaded(){return true;}
        };
        handle.setPos(at.getX(),at.getY(),at.getZ());server.getPlayerList().getPlayers().add(handle);
        server.getPlayerList().getPlayersByUUID().put(profile.id(),handle);level.addNewPlayer(handle);
        var bukkit=handle.getBukkitEntity();bukkit.setGravity(false);bukkit.setInvulnerable(true);bukkit.setGameMode(GameMode.SURVIVAL);return handle;
    }
    void platform(int cx,int cz,int radius){
        for(int x=cx-radius;x<=cx+radius;x++)for(int z=cz-radius;z<=cz+radius;z++){
            world.addPluginChunkTicket(x>>4,z>>4,this);world.getBlockAt(x,119,z).setType(Material.STONE,false);
            for(int y=120;y<=127;y++)world.getBlockAt(x,y,z).setType(Material.AIR,false);
        }
    }
    void prepare(){try{
        garden.getConfig().set("pets.wild-spawns.chance-per-player",0.0);wild.reload();
        check(WildPetSpawns.eligibleSpecies().size()==PetCatalog.wildSpecies()&&PetCatalog.wildSpecies()==32,"exactly the 32 nonmythical species are eligible");
        for(var value:PetCatalog.Species.values())check(WildPetSpawns.eligibleSpecies().contains(value)!=value.mythical(),"catalog eligibility "+value.id);
        Set<PetCatalog.Species> chosen=EnumSet.noneOf(PetCatalog.Species.class);var random=new Random(20261021);
        for(int i=0;i<4096;i++)chosen.add(WildPetSpawns.chooseSpecies(random));
        check(chosen.equals(new HashSet<>(WildPetSpawns.eligibleSpecies())),"seeded species selection reaches every wild species and excludes legends");
        int[] counts=new int[3];for(int i=0;i<10000;i++)counts[WildPetSpawns.stageForRoll((i+.5)/10000,85,13,2).ordinal()]++;
        check(Arrays.equals(counts,new int[]{8500,1300,200}),"default stage weights exactly 85% babies, 13% medium, 2% adults");
        check(WildPetSpawns.stageForRoll(0,85,13,2)==PetCatalog.Stage.BABY,"lowest stage roll is baby");
        check(WildPetSpawns.stageForRoll(.849999,85,13,2)==PetCatalog.Stage.BABY,"baby upper boundary");
        check(WildPetSpawns.stageForRoll(.85,85,13,2)==PetCatalog.Stage.MEDIUM,"medium lower boundary");
        check(WildPetSpawns.stageForRoll(.979999,85,13,2)==PetCatalog.Stage.MEDIUM,"medium upper boundary");
        check(WildPetSpawns.stageForRoll(.98,85,13,2)==PetCatalog.Stage.ADULT,"adult lower boundary");
        check(WildPetSpawns.stageForRoll(.999999,85,13,2)==PetCatalog.Stage.ADULT,"highest valid roll is adult");
        check(WildPetSpawns.stageForRoll(.5,0,0,0)==PetCatalog.Stage.BABY&&WildPetSpawns.stageForRoll(.9,0,0,0)==PetCatalog.Stage.MEDIUM&&WildPetSpawns.stageForRoll(.99,0,0,0)==PetCatalog.Stage.ADULT,"zero total stage weights safely use default proportions");
        boolean rejected=false;try{WildPetSpawns.stageForRoll(Double.NaN,85,13,2);}catch(IllegalArgumentException expected){rejected=true;}
        check(rejected,"nonfinite growth rolls rejected");
        check(ModelEngineAPI.getBlueprint("internal_fire")!=null,"wild pets retain the official renderer internal model");
        for(String id:BOSSES)check(ModelEngineAPI.getBlueprint(id)!=null,"wild pets retain boss import "+id);
        try(var files=Files.list(Path.of("plugins/ModelEngine/blueprints"))){check(files.noneMatch(p->p.getFileName().toString().startsWith("everpet_")),"wild pets consume no active ModelEngine blueprint slots");}
        verifyPack();
        home=new Location(world,512.5,120,512.5);platform(512,512,56);
        check(wild.safeLocation(home),"loaded broad clear platform is a safe wild habitat");
        actor=fake(home);player=actor.getBukkitEntity();
        verifySafety();verifyProtection();species.addAll(WildPetSpawns.eligibleSpecies());nextVariant();
    }catch(Exception error){throw new RuntimeException(error);}}
    void verifySafety(){
        check(!wild.safeLocation(null),"null habitat rejected");
        check(!wild.safeLocation(new Location(world,Double.NaN,120,512.5)),"nonfinite habitat rejected");
        World other=Bukkit.getWorlds().stream().filter(w->w!=world).findFirst().orElseThrow();
        check(!wild.safeLocation(new Location(other,512.5,120,512.5)),"ordinary worlds cannot receive wild Evergarden pets");
        check(!wild.safeLocation(new Location(world,300000.5,120,300000.5)),"unloaded destination rejected");
        check(!world.isChunkLoaded(300000>>4,300000>>4),"habitat checks do not generate distant chunks");
        check(!wild.safeLocation(new Location(world,.5,120,.5)),"spawn island remains free of wild pets");
        var shrine=com.example.voidscape.world.DungeonLayout.STARTER_SITES.getFirst();
        check(!wild.safeLocation(new Location(world,shrine.x()+.5,120,shrine.z()+.5)),"authored elemental sanctum cannot receive wild pets");
        var floor=world.getBlockAt(home).getRelative(0,-1,0);floor.setType(Material.LAVA,false);
        check(!wild.safeLocation(home),"lava support rejected");floor.setType(Material.STONE,false);
        var edge=world.getBlockAt(home).getRelative(2,-1,0);edge.setType(Material.AIR,false);
        check(!wild.safeLocation(home),"five-block footprint rejects unsafe ledges");edge.setType(Material.STONE,false);
        var head=world.getBlockAt(home).getRelative(0,2,0);head.setType(Material.STONE,false);
        check(!wild.safeLocation(home),"adult headroom obstruction rejected");head.setType(Material.AIR,false);
        var feet=world.getBlockAt(home);feet.setType(Material.WATER,false);
        check(!wild.safeLocation(home),"submerged habitat rejected");feet.setType(Material.AIR,false);
        check(wild.safeLocation(home),"safe habitat restored after hazard checks");
        var border=world.getWorldBorder();double size=border.getSize();Location center=border.getCenter();
        try{border.setCenter(home);border.setSize(64);check(!wild.safeLocation(home.clone().add(33,0,0)),"world border bounds the complete habitat footprint");}
        finally{border.setCenter(center);border.setSize(size);}
    }
    void verifyPack()throws Exception{
        Map<String,byte[]> entries=new HashMap<>();
        // The merged pack the server delivers; it now rides the game port, which the trial server leaves at 0.
        try(var stream=Files.newInputStream(Path.of("plugins/Evergarden/resource-packs/judge-java.zip"));var zip=new ZipInputStream(stream)){
            for(ZipEntry entry;(entry=zip.getNextEntry())!=null;)if(!entry.isDirectory())entries.put(entry.getName(),zip.readAllBytes());
        }
        JsonObject nativeModels;try(var source=garden.getResource("native-pets.json")){
            nativeModels=JsonParser.parseReader(new InputStreamReader(source,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("models");
        }
        check(nativeModels.size()==PetCatalog.wildSpecies()*3,"shipped native manifest contains every wild growth model ("+nativeModels.size()+")");
        for(var kind:WildPetSpawns.eligibleSpecies())for(var stage:PetCatalog.Stage.values()){
            String id=kind.id+"_"+stage.id;var model=nativeModels.getAsJsonObject(id);check(model!=null,"native animation manifest "+id);
            check(model.getAsJsonObject("clips").keySet().containsAll(PetCatalog.ANIMATIONS),"authored animation clips preserved "+id);
            for(var value:model.getAsJsonArray("bones")){
                String item=value.getAsJsonObject().get("item").getAsString();if(item.isEmpty())continue;
                check(entries.containsKey("assets/evergarden/items/"+item+".json")&&entries.containsKey("assets/evergarden/models/"+item+".json"),"served native item and sculpture bone "+item);
            }
            byte[] texture=entries.get("assets/evergarden/textures/item/pet_native/"+id+"_0.png");
            check(texture!=null&&texture.length>8&&texture[0]==(byte)137&&texture[1]==80&&texture[2]==78&&texture[3]==71,"served native texture "+id);
        }
        check(entries.keySet().stream().noneMatch(n->n.startsWith("assets/minecraft/shaders/")),"wild pack does not override player shaders");
    }
    void verifyProtection(){
        Rabbit ordinary=world.spawn(home,Rabbit.class);check(!ordinary.isValid(),"ordinary custom rabbits remain blocked in Evergarden");
        Slime slime=world.spawn(home,Slime.class);check(!slime.isValid(),"ordinary slime spawn remains blocked");
        Zombie zombie=world.spawn(home,Zombie.class);check(!zombie.isValid(),"ordinary hostile spawn remains blocked");
        Rabbit marked=world.spawn(home,Rabbit.class,r->r.getPersistentDataContainer().set(garden.key("wild_pet"),PersistentDataType.BYTE,(byte)1));
        check(marked.isValid(),"only explicitly marked custom pet passes the dimension filter");
        var natural=new CreatureSpawnEvent(marked,CreatureSpawnEvent.SpawnReason.NATURAL);Bukkit.getPluginManager().callEvent(natural);
        check(natural.isCancelled(),"wild pet marker does not permit natural mob spawning");marked.remove();
    }
    List<ItemDisplay> petDisplays(){return world.getEntitiesByClass(ItemDisplay.class).stream().filter(d->!previousDisplays.contains(d.getUniqueId())).toList();}
    void nextVariant(){try{
        wild.clear();check(wild.count()==0,"clear resets wild encounter budget");
        if(index==species.size()*3){lifecycle();return;}
        previousDisplays.clear();world.getEntitiesByClass(ItemDisplay.class).forEach(d->previousDisplays.add(d.getUniqueId()));
        var kind=species.get(index/3);var stage=PetCatalog.Stage.values()[index%3];String id=kind.model(stage);
        check(wild.spawn(kind,stage,home),"spawn nonmythical growth model "+id);
        check(wild.count()==1,"one managed encounter "+id);
        shell=world.getEntitiesByClass(Rabbit.class).stream().filter(r->r.getPersistentDataContainer().has(garden.key("wild_pet"),PersistentDataType.BYTE)).findFirst().orElseThrow();
        check(shell.isValid()&&shell.isInvisible(),"visible model has hidden valid receiver "+id);
        check(!shell.isVisibleByDefault(),"receiver is culled per viewer rather than broadcast "+id);
        check(!shell.hasAI()&&!shell.hasGravity()&&!shell.isCollidable()&&shell.isInvulnerable()&&shell.isSilent(),"wild receiver remains neutral and harmless "+id);
        check(shell.getPersistentDataContainer().get(garden.key("wild_pet_species"),PersistentDataType.STRING).equals(kind.id),"species identity preserved "+id);
        check(shell.getPersistentDataContainer().get(garden.key("wild_pet_stage"),PersistentDataType.STRING).equals(stage.id),"growth identity preserved "+id);
        check(shell.getScoreboardTags().contains("no-level"),"wild pet avoids mob-level modification "+id);
        check(ModelEngineAPI.getModeledEntity(shell.getUniqueId())==null,"native wild model uses no ModelEngine slot "+id);
        var carrier=(ArmorStand)field(active().values().iterator().next(),"carrier");
        if(garden.arkhaiaBedrock()!=null&&garden.arkhaiaBedrock().ready()){
            check(carrier!=null&&carrier.isValid()&&!carrier.isPersistent()&&garden.arkhaiaBedrock().registered(carrier.getUniqueId()),"native Bedrock carrier registered with Geyser "+id);
            check(!player.canSee(carrier),"Bedrock carrier is never sent to Java players "+id);
        }else check(carrier==null,"no Bedrock carrier without the Geyser entity API "+id);
        displays=petDisplays();check(!displays.isEmpty(),"native animated sculpture parts spawned "+id);
        var visual=rig();check(visual.partCount()==displays.size(),"every authored visible bone has one display "+id);
        check(visual.riding()&&new HashSet<Entity>(shell.getPassengers()).containsAll(displays),"sculpture parts ride the receiver instead of teleporting "+id);
        visual.tick(false,false,0);check(visual.animation().equals("idle"),"native idle clip selected "+id);
        visual.tick(true,false,45);check(visual.animation().equals("walk"),"native walk clip selected "+id);
        visual.tick(false,true,90);check(visual.animation().equals("fly"),"native fly clip selected "+id);
        later(3,this::inspectVariant);
    }catch(Exception error){throw new RuntimeException(error);}}
    void inspectVariant(){try{
        String id=species.get(index/3).model(PetCatalog.Stage.values()[index%3]);
        double lift=shell.getHeight();
        for(ItemDisplay display:displays)check(Math.abs(display.getLocation().getY()-(shell.getLocation().getY()+lift))<.01
            &&display.getLocation().distanceSquared(shell.getLocation().clone().add(0,lift,0))<.01,"riding parts sit at the receiver passenger point "+id);
        for(ItemDisplay display:displays){
            check(display.isValid()&&!display.isPersistent(),"temporary model part remains valid "+id);
            var t=display.getTransformation();check(t.getScale().isFinite()&&t.getTranslation().isFinite()&&t.getLeftRotation().isFinite()&&t.getRightRotation().isFinite(),"finite animated model transform "+id);
        }
        UUID receiver=shell.getUniqueId();wild.clear();
        check(!shell.isValid()&&Bukkit.getEntity(receiver)==null,"clearing removes receiver "+id);
        check(displays.stream().noneMatch(Entity::isValid),"clearing removes every sculpture part "+id);
        index++;later(1,this::nextVariant);
    }catch(Exception error){throw new RuntimeException(error);}}
    void lifecycle(){try{
        var external=new ExternalSpawnProtection();Bukkit.getPluginManager().registerEvents(external,this);
        try{check(!wild.spawn(PetCatalog.Species.ABYSSAL_MANTA,PetCatalog.Stage.BABY,home),"external spawn protection cancellation remains authoritative");
            check(wild.count()==0,"cancelled custom spawn creates no managed or invisible encounter");}
        finally{HandlerList.unregisterAll(external);}
        for(var mythical:PetCatalog.Species.values())if(mythical.mythical())for(var stage:PetCatalog.Stage.values())
            check(!wild.spawn(mythical,stage,home),"legendary natural habitat entry rejected "+mythical.model(stage));
        check(wild.count()==0,"legendary requests leave no hidden receivers");
        check(!wild.spawn(null,PetCatalog.Stage.BABY,home)&&!wild.spawn(PetCatalog.Species.ABYSSAL_MANTA,null,home)&&!wild.spawn(PetCatalog.Species.ABYSSAL_MANTA,PetCatalog.Stage.BABY,null),"invalid spawn arguments create no receiver");
        check(!wild.spawn(PetCatalog.Species.ABYSSAL_MANTA,PetCatalog.Stage.BABY,new Location(world,300000,120,300000)),"unsafe spawn creates no hidden model");
        check(wild.count()==0,"unsafe spawn leaves encounter budget unchanged");
        check(wild.spawn(PetCatalog.Species.ABYSSAL_MANTA,PetCatalog.Stage.BABY,home),"lifecycle fixture spawned");
        check(!wild.spawn(PetCatalog.Species.ASTRAL_OWL,PetCatalog.Stage.BABY,home.clone().add(1,0,0)),"minimum encounter separation prevents clustering");
        var receiver=(Rabbit)field(active().values().iterator().next(),"shell");
        check(rig().displays().stream().noneMatch(player::canSee),"models remain hidden from players without their graphics pack");
        refresh();check(!player.canSee(receiver),"receiver stays hidden from players without the graphics pack");
        Bukkit.getPluginManager().callEvent(new PlayerResourcePackStatusEvent(player,JudgePackService.PACK_ID,PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED));
        refresh();check(rig().displays().stream().allMatch(player::canSee)&&player.canSee(receiver),"pack-ready player can see the receiver and every native model part");
        garden.getConfig().set("pets.view.show-distance",16);garden.getConfig().set("pets.view.hide-distance",20);
        player.teleport(home.clone().add(30,0,0));refresh();
        check(rig().displays().stream().noneMatch(player::canSee)&&!player.canSee(receiver),"pets beyond the view distance send that player nothing");
        player.teleport(home);refresh();check(rig().displays().stream().allMatch(player::canSee),"walking back into range shows the pet again");
        garden.getConfig().set("pets.view.show-distance",56);garden.getConfig().set("pets.view.hide-distance",64);
        garden.getConfig().set("pets.view.max-others",0);refresh();
        check(rig().displays().stream().noneMatch(player::canSee),"per-viewer cap culls other pets");
        garden.getConfig().set("pets.view.max-others",6);refresh();check(rig().displays().stream().allMatch(player::canSee),"raising the cap shows the pet again");
        Bukkit.getPluginManager().callEvent(new PlayerResourcePackStatusEvent(player,JudgePackService.PACK_ID,PlayerResourcePackStatusEvent.Status.DECLINED));
        refresh();check(rig().displays().stream().noneMatch(player::canSee),"declining graphics hides native parts without affecting ordinary entities");
        var incoming=new EntityDamageByEntityEvent(player,receiver,EntityDamageEvent.DamageCause.ENTITY_ATTACK,4);
        Bukkit.getPluginManager().callEvent(incoming);check(incoming.isCancelled(),"wild pet cannot receive combat damage");
        var outgoing=new EntityDamageByEntityEvent(receiver,player,EntityDamageEvent.DamageCause.ENTITY_ATTACK,4);
        Bukkit.getPluginManager().callEvent(outgoing);check(outgoing.isCancelled(),"wild pet cannot damage players");
        var target=new EntityTargetEvent(receiver,player,EntityTargetEvent.TargetReason.CLOSEST_PLAYER);
        Bukkit.getPluginManager().callEvent(target);check(target.isCancelled(),"wild pet cannot acquire a combat target");
        garden.getConfig().set("pets.wild-spawns.max-active-world",1);
        check(!wild.spawn(PetCatalog.Species.ASTRAL_OWL,PetCatalog.Stage.BABY,home.clone().add(50,0,0)),"world encounter cap blocks a safely separated extra pet");
        garden.getConfig().set("pets.wild-spawns.max-active-world",16);
        garden.getConfig().set("pets.wild-spawns.max-nearby-player",1);
        check(!wild.spawn(PetCatalog.Species.ASTRAL_OWL,PetCatalog.Stage.BABY,home.clone().add(50,0,0)),"direct spawns also respect the local encounter cap around players");
        garden.getConfig().set("pets.wild-spawns.max-nearby-player",2);
        List<Entity> before=world.getEntities().stream().filter(e->e.getPersistentDataContainer().has(garden.key("wild_pet"),PersistentDataType.BYTE)).toList();
        wild.reload();check(wild.count()==0&&before.stream().noneMatch(Entity::isValid),"configuration reload removes managed receivers");
        check(wild.spawn(PetCatalog.Species.ABYSSAL_MANTA,PetCatalog.Stage.MEDIUM,home),"service recovers after reload");
        var unload=new ChunkUnloadEvent(world.getChunkAt(home));Bukkit.getPluginManager().callEvent(unload);
        check(wild.count()==0,"chunk unload removes encounter and animated model parts");
        naturalAttempts();
        habitats();
        wanderInvariants(PetCatalog.Species.FROST_WYVERN);wanderInvariants(PetCatalog.Species.SHADOW_PANTHER);
        check(wild.spawn(PetCatalog.Species.ABYSSAL_MANTA,PetCatalog.Stage.BABY,home),"expiry fixture spawns");
        Object expiring=active().values().iterator().next();
        set(expiring,"expires",System.currentTimeMillis()-1);
        step();check(wild.count()==1,"an expired encounter waits while a player stands close enough to watch it go");
        set(expiring,"deadline",System.currentTimeMillis()-1);set(expiring,"expires",System.currentTimeMillis()-1);
        step();check(wild.count()==0,"past its last moment it leaves anyway, with every visual part");
        check(wild.spawn(PetCatalog.Species.ABYSSAL_MANTA,PetCatalog.Stage.BABY,home),"second expiry fixture spawns");
        set(active().values().iterator().next(),"expires",System.currentTimeMillis()-1);
        player.teleport(home.clone().add(40,0,0));step();player.teleport(home);
        check(wild.count()==0,"an expired encounter nobody is near leaves at once");
        check(wild.spawn(PetCatalog.Species.ABYSSAL_MANTA,PetCatalog.Stage.BABY,home),"frozen fixture spawns");
        player.teleport(home.clone().add(100,0,0));step();long frozen=transforms();
        for(int i=0;i<40;i++)step();
        check(wild.count()==1&&transforms()==frozen,"an unwatched pet holds its pose and sends no transforms");
        player.teleport(home);wild.clear();
        check(wild.spawn(PetCatalog.Species.ABYSSAL_MANTA,PetCatalog.Stage.BABY,home),"distance fixture spawns");
        player.teleport(home.clone().add(200,0,0));step();
        check(wild.count()==0,"abandoned distant encounter removed");player.teleport(home);
        check(wild.spawn(PetCatalog.Species.ABYSSAL_MANTA,PetCatalog.Stage.BABY,home),"world unload fixture spawns");
        Bukkit.getPluginManager().callEvent(new WorldUnloadEvent(world));check(wild.count()==0,"world unload clears encounter registry");
        check(wild.spawn(PetCatalog.Species.ABYSSAL_MANTA,PetCatalog.Stage.ADULT,home),"adult lifecycle fixture spawns");
        int beforeCount=wild.count();invoke("attemptSpawns");check(wild.count()==beforeCount,"zero spawn chance creates no additional encounters");
        List<ItemDisplay> remaining=petDisplays();wild.close();check(wild.count()==0,"plugin close empties wild encounter registry");
        check(remaining.stream().noneMatch(Entity::isValid),"plugin close removes animated display parts");
        finish(null);
    }catch(Exception error){throw new RuntimeException(error);}}
    /** 2,000 behaviour updates with a watching player: leashed, grounded or safely aloft, varied clips. */
    void wanderInvariants(PetCatalog.Species kind)throws Exception{
        wild.clear();player.teleport(home);
        check(wild.spawn(kind,PetCatalog.Stage.ADULT,home),"behaviour fixture spawns "+kind.id);
        Object pet=active().values().iterator().next();Rabbit body=(Rabbit)field(pet,"shell");
        Set<String> clips=new HashSet<>();boolean flew=false,moved=false;Location start=body.getLocation(),last=start;
        int walking=0,gliding=0;
        for(int i=0;i<10000;i++){
            invoke("tick");if(wild.count()!=1)break;
            Location at=body.getLocation();clips.add(rig().animation());
            if(rig().animation().equals("walk")&&!(boolean)field(pet,"airborne")){walking++;if(at.distanceSquared(last)>1e-6)gliding++;}
            last=at;
            boolean airborne=(boolean)field(pet,"airborne");flew|=airborne;moved|=at.distanceSquared(start)>1;
            double dx=at.getX()-home.getX(),dz=at.getZ()-home.getZ();
            check(dx*dx+dz*dz<=14*14,"wandering stays near its habitat "+kind.id);
            if(!airborne)check(world.getBlockAt(at).getRelative(0,-1,0).getType().isSolid(),"grounded pet always stands on solid ground "+kind.id);
            else check(at.getY()-home.getY()<=5.2&&at.getY()>=home.getY()-.01,"flight stays a few blocks above the ground "+kind.id);
        }
        check(wild.count()==1,"behaviour fixture survives continuous updates "+kind.id);
        check(walking>0&&gliding>=walking*.6,"a walking pet moves a little every tick instead of every fifth "+kind.id+" "+gliding+"/"+walking);
        check(moved&&clips.contains("idle")&&clips.contains("walk"),"pet both rests in place and walks "+kind.id+" "+clips);
        check(WildPetSpawns.flies(kind)==flew,(WildPetSpawns.flies(kind)?"flyer takes off":"ground species never flies")+" "+kind.id);
        check(WildPetSpawns.flies(kind)||!clips.contains("fly"),"ground species never plays the flight clip "+kind.id);
        wild.clear();
    }
    void naturalAttempts()throws Exception{
        garden.getConfig().set("pets.wild-spawns.chance-per-player",1.0);
        invoke("attemptSpawns");check(wild.count()==0,"players without the applied graphics pack do not trigger wild models");
        Bukkit.getPluginManager().callEvent(new PlayerResourcePackStatusEvent(player,JudgePackService.PACK_ID,PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED));
        player.setGameMode(GameMode.SPECTATOR);invoke("attemptSpawns");check(wild.count()==0,"spectators do not trigger natural encounters");player.setGameMode(GameMode.SURVIVAL);
        garden.getConfig().set("pets.wild-spawns.chance-per-player",0.0);
        invoke("attemptSpawns");check(wild.count()==0,"zero configured chance skips natural spawning");
        garden.getConfig().set("pets.wild-spawns.chance-per-player",1.0);
        garden.getConfig().set("pets.wild-spawns.stage-weights.baby",0);garden.getConfig().set("pets.wild-spawns.stage-weights.medium",0);garden.getConfig().set("pets.wild-spawns.stage-weights.adult",1);
        invoke("attemptSpawns");check(wild.count()==1,"eligible player on safe terrain receives a natural encounter");
        Rabbit natural=(Rabbit)field(active().values().iterator().next(),"shell");
        Location spawned=natural.getLocation(),observer=player.getLocation();
        double dx=spawned.getX()-observer.getX(),dz=spawned.getZ()-observer.getZ(),distance=Math.hypot(dx,dz);
        // Natural spots sample the horizontal annulus, then snap X/Z to block centers.
        check(distance>=24-1e-6&&distance<=48+Math.sqrt(.5)+1e-6,"natural encounter respects the horizontal twenty-four to forty-eight block annulus with block-center rounding: "+distance);
        check(spawned.getY()>=home.getBlockY()-24&&spawned.getY()<=home.getBlockY()+12,"natural encounter selects floor near the fixture ground level: "+spawned.getY());
        check(natural.getPersistentDataContainer().get(garden.key("wild_pet_stage"),PersistentDataType.STRING).equals("adult"),"natural selection honors configured stage weights");
        garden.getConfig().set("pets.wild-spawns.max-nearby-player",1);invoke("attemptSpawns");check(wild.count()==1,"local player encounter cap prevents crowding");
        garden.getConfig().set("pets.wild-spawns.max-nearby-player",2);wild.clear();
        garden.getConfig().set("pets.wild-spawns.stage-weights.baby",85);garden.getConfig().set("pets.wild-spawns.stage-weights.medium",13);garden.getConfig().set("pets.wild-spawns.stage-weights.adult",2);
        garden.getConfig().set("pets.wild-spawns.chance-per-player",0.0);
        // A roof over a candidate spot (a cave or an overhang) keeps natural encounters out.
        Method roofed=Class.forName("com.example.voidscape.pet.PetTerrain").getDeclaredMethod("roofed",World.class,int.class,int.class,int.class);roofed.setAccessible(true);
        check(!(boolean)roofed.invoke(null,world,home.getBlockX(),home.getBlockY(),home.getBlockZ()),"an open platform counts as open sky");
        var lid=world.getBlockAt(home).getRelative(0,4,0);lid.setType(Material.STONE,false);
        check((boolean)roofed.invoke(null,world,home.getBlockX(),home.getBlockY(),home.getBlockZ()),"a ceiling four blocks up marks the spot as roofed");
        lid.setType(Material.OAK_LEAVES,false);
        check(!(boolean)roofed.invoke(null,world,home.getBlockX(),home.getBlockY(),home.getBlockZ()),"a leafy canopy does not count as a roof");
        lid.setType(Material.AIR,false);
    }
    /** Species favour their home regions three to one, and the arrival hint names the right side. */
    void habitats()throws Exception{
        Method region=WildPetSpawns.class.getDeclaredMethod("region",org.bukkit.block.Biome.class);region.setAccessible(true);
        Method choose=WildPetSpawns.class.getDeclaredMethod("chooseSpecies",java.util.random.RandomGenerator.class,com.example.voidscape.world.BiomeTerrain.Kind.class,Collection.class);choose.setAccessible(true);
        Method direction=WildPetSpawns.class.getDeclaredMethod("direction",float.class);direction.setAccessible(true);
        var glacier=com.example.voidscape.world.BiomeTerrain.Kind.GLACIER;
        check(region.invoke(null,org.bukkit.block.Biome.FROZEN_PEAKS)==glacier&&region.invoke(null,org.bukkit.block.Biome.PALE_GARDEN)==com.example.voidscape.world.BiomeTerrain.Kind.LUNAR_CITY
            &&region.invoke(null,org.bukkit.block.Biome.MEADOW)==com.example.voidscape.world.BiomeTerrain.Kind.GARDENS&&region.invoke(null,org.bukkit.block.Biome.DESERT)==null,
            "vanilla biomes map onto Evergarden's four regions");
        Method home=WildPetSpawns.class.getDeclaredMethod("home",PetCatalog.Species.class,com.example.voidscape.world.BiomeTerrain.Kind.class);home.setAccessible(true);
        double weights=0;for(var kind:WildPetSpawns.eligibleSpecies())weights+=(boolean)home.invoke(null,kind,glacier)?3:1;
        var random=new java.util.SplittableRandom(7);Map<PetCatalog.Species,Integer> seen=new EnumMap<>(PetCatalog.Species.class);
        for(int i=0;i<24000;i++)seen.merge((PetCatalog.Species)choose.invoke(null,random,glacier,List.of()),1,Integer::sum);
        double wyvern=seen.getOrDefault(PetCatalog.Species.FROST_WYVERN,0)/24000.0,fairy=seen.getOrDefault(PetCatalog.Species.CHRONO_FAIRY,0)/24000.0;
        // Within five standard deviations of the 3:1 home weighting.
        double wyvernSigma=Math.sqrt(3/weights*(1-3/weights)/24000),fairySigma=Math.sqrt(1/weights*(1-1/weights)/24000);
        check(Math.abs(wyvern-3/weights)<5*wyvernSigma&&Math.abs(fairy-1/weights)<5*fairySigma&&seen.size()==PetCatalog.wildSpecies(),
            "on the glacier the Frost Wyvern is three times as common as the Chrono Fairy, and every species can still appear ("+wyvern+" vs "+fairy+")");
        seen.clear();
        for(int i=0;i<24000;i++)seen.merge((PetCatalog.Species)choose.invoke(null,random,null,List.of(PetCatalog.Species.ASTRAL_OWL)),1,Integer::sum);
        double owl=seen.getOrDefault(PetCatalog.Species.ASTRAL_OWL,0)/24000.0,expectedOwl=.5/(PetCatalog.wildSpecies()-.5);
        check(Math.abs(owl-expectedOwl)<5*Math.sqrt(expectedOwl*(1-expectedOwl)/24000),"a species met recently is half as likely next time ("+owl+")");
        check("ด้านหน้า".equals(direction.invoke(null,0f))&&"ทางขวา".equals(direction.invoke(null,90f))&&"ด้านหลัง".equals(direction.invoke(null,-180f))
            &&"ทางหน้าเยื้องซ้าย".equals(direction.invoke(null,-40f)),"the arrival hint names the side relative to where the player faces");
    }
    void discard(){if(actor==null)return;var server=MinecraftServer.getServer();server.getPlayerList().getPlayers().remove(actor);server.getPlayerList().getPlayersByUUID().remove(actor.getUUID());actor.discard();}
    void finish(Throwable error){
        if(finished)return;finished=true;if(error!=null){error.printStackTrace();lines.add("ERROR: "+error);}
        try{if(wild!=null)wild.clear();}catch(Exception ignored){}discard();
        try{Files.writeString(Path.of("model-engine-result.txt"),(error==null?"PASS":"FAIL")+": nonmythical wild pets / "+checks+" checks\n"+String.join("\n",lines)+"\n");}catch(Exception failure){failure.printStackTrace();}
        Bukkit.shutdown();
    }
}
