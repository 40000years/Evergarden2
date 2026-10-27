import com.example.voidscape.VoidscapePlugin;
import com.example.voidscape.pet.*;
import com.example.voidscape.boss.JudgePackService;
import com.ticxo.modelengine.api.ModelEngineAPI;
import com.ticxo.modelengine.api.animation.handler.IStateMachineHandler;
import com.ticxo.modelengine.api.model.ActiveModel;
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
import org.bukkit.event.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/** Exercises real production commands and every exported model on isolated Paper + ME R4. */
public final class PetModelsNativeProbe extends JavaPlugin implements Listener {
    VoidscapePlugin garden;PetModelPreviews pets;World world;Player player,stranger;
    ServerPlayer actor,otherActor;PermissionAttachment admin;Location home;
    int checks,waitTicks,index,batch,boneWait;final List<String> allIds=PetCatalog.modelIds();List<String> ids;
    final Path blueprintFolder=Path.of("plugins/ModelEngine/blueprints"),parked=Path.of("pet-probe-parked-blueprints");
    final List<String> lines=new ArrayList<>();List<UUID> removed=new ArrayList<>();
    Slime current;ActiveModel model;boolean finished;
    void check(boolean ok,String label){if(!ok)throw new AssertionError(label);checks++;lines.add("PASS: "+label);}
    void later(long ticks,Runnable next){Bukkit.getScheduler().runTaskLater(this,()->{try{next.run();}catch(Throwable error){finish(error);}},ticks);}
    Object field(Object object,String name)throws Exception{var f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}
    @SuppressWarnings("unchecked") Map<UUID,Object> previews()throws Exception{return (Map<UUID,Object>)field(pets,"previews");}
    void tick()throws Exception{var method=pets.getClass().getDeclaredMethod("tick");method.setAccessible(true);method.invoke(pets);}
    void command(Player sender,String text){check(Bukkit.dispatchCommand(sender,"evergarden pets "+text),"command accepted: "+text);}
    @Override public void onEnable(){try{
        Bukkit.getPluginManager().registerEvents(this,this);
        garden=(VoidscapePlugin)Bukkit.getPluginManager().getPlugin("Evergarden");
        check(garden!=null&&garden.isEnabled(),"Evergarden enabled");pets=garden.petPreviews();
        // Legitimate free-trial verification: keep one worldboss plus at most 9
        // pets imported at once. No license fields or plugin code are modified.
        stageBatch(false);later(10,this::awaitReady);
    }catch(Throwable error){finish(error);}}
    @EventHandler public void removed(EntityRemoveEvent event){
        if(!event.getEntity().getScoreboardTags().contains("evergarden_pet_preview"))return;
        lines.add("SHELL REMOVAL: "+event.getCause()+" ready="+pets.ready()+" admin="+(player!=null&&player.hasPermission("evergarden.admin"))+" online="+(player!=null&&player.isOnline())+" found="+(player!=null&&Bukkit.getPlayer(player.getUniqueId())==player)+" dead="+(player!=null&&player.isDead())+" owner="+(player==null?"none":player.getLocation())+" shell="+event.getEntity().getLocation());
        for(StackTraceElement trace:Thread.currentThread().getStackTrace())if(trace.getClassName().contains("voidscape")||trace.getClassName().contains("levelled")||trace.getClassName().contains("Entity.remove"))lines.add("REMOVE TRACE: "+trace);
    }
    void stageBatch(boolean reload)throws Exception{
        ids=allIds.subList(batch*9,Math.min(allIds.size(),batch*9+9));index=0;waitTicks=0;Files.createDirectories(parked);
        for(String id:allIds){
            Path source=blueprintFolder.resolve(id+".bbmodel");if(!Files.exists(source))source=parked.resolve(id+".bbmodel");
            try(var bundled=garden.getResource("modelengine/pets/"+id+".bbmodel")){
                check(bundled!=null&&Arrays.equals(bundled.readAllBytes(),Files.readAllBytes(source)),"batch source matches shipped model "+id);
            }
        }
        try(var files=Files.list(blueprintFolder)){
            for(Path file:files.toList())if(file.getFileName().toString().endsWith(".bbmodel")){
                String id=file.getFileName().toString().replaceFirst("\\.bbmodel$","");
                if(!id.equals("arkhaia")&&!ids.contains(id))Files.move(file,parked.resolve(file.getFileName()),StandardCopyOption.REPLACE_EXISTING);
            }
        }
        for(String id:ids){Path active=blueprintFolder.resolve(id+".bbmodel");if(!Files.exists(active))Files.move(parked.resolve(id+".bbmodel"),active);}
        try(var files=Files.list(blueprintFolder)){check(files.filter(file->file.toString().endsWith(".bbmodel")).count()<=10,"batch leaves room for ME internal models");}
        lines.add("BATCH "+(batch+1)+" / 6: "+String.join(", ",ids));
        if(reload)check(Bukkit.dispatchCommand(Bukkit.getConsoleSender(),"meg reload"),"reload official generator for next bounded batch");
    }
    void awaitReady(){
        if(pets.ready()&&pets.importedModels()==ids.size()&&ModelEngineAPI.getBlueprint("internal_fire")!=null&&ids.stream().allMatch(id->ModelEngineAPI.getBlueprint(id)!=null)){
            if(player==null)prepare();else{
                verifyBatchAssets();Bukkit.getPluginManager().callEvent(new PlayerResourcePackStatusEvent(player,JudgePackService.PACK_ID,PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED));nextModel();
            }return;
        }
        if((waitTicks+=10)>3600)throw new AssertionError("ME pet import did not become ready: "+pets.status());
        later(10,this::awaitReady);
    }
    ServerPlayer fake(String name,Location at){
        var server=MinecraftServer.getServer();var level=((CraftWorld)world).getHandle();
        var profile=new GameProfile(UUID.randomUUID(),name);var actor=new ServerPlayer(server,level,profile,ClientInformation.createDefault());
        var connection=new Connection(PacketFlow.SERVERBOUND);connection.channel=new EmbeddedChannel();connection.address=new InetSocketAddress("127.0.0.1",1);
        actor.connection=new ServerGamePacketListenerImpl(server,connection,actor,CommonListenerCookie.createInitial(profile,false)){
            @Override public boolean hasClientLoaded(){return true;}
        };
        actor.setPos(at.getX(),at.getY(),at.getZ());actor.setYRot(0);
        server.getPlayerList().getPlayers().add(actor);server.getPlayerList().getPlayersByUUID().put(profile.id(),actor);level.addNewPlayer(actor);
        var player=actor.getBukkitEntity();player.setGravity(false);player.setInvulnerable(true);player.setGameMode(GameMode.CREATIVE);return actor;
    }
    void prepare(){try{
        check(pets.importedModels()==ids.size(),"bounded pet batch imported");
        check(garden.arkhaiaModels().ready(),"river/worldboss renderer still available");
        for(String id:allIds){
            Path active=blueprintFolder.resolve(id+".bbmodel");if(!Files.exists(active))active=parked.resolve(id+".bbmodel");
            try(var bundled=garden.getResource("modelengine/pets/"+id+".bbmodel")){
                check(bundled!=null&&Arrays.equals(bundled.readAllBytes(),Files.readAllBytes(active)),"active/parked rig matches shipped model "+id);
            }
            check(Files.readString(garden.getDataFolder().toPath().resolve("modelengine/pets/"+id+".sha256")).trim().length()==64,"ownership hash "+id);
        }
        verifyBatchAssets();
        world=garden.world();home=new Location(world,.5,120,0.5,0,0);
        for(int x=-24;x<=24;x++)for(int z=-12;z<=42;z++){
            world.addPluginChunkTicket(x>>4,z>>4,this);world.getBlockAt(x,119,z).setType(Material.STONE,false);
            for(int y=120;y<=128;y++)world.getBlockAt(x,y,z).setType(Material.AIR,false);
        }
        actor=fake("PetModelAdmin",home);player=actor.getBukkitEntity();
        otherActor=fake("PetModelOther",home.clone().add(0,0,-5));stranger=otherActor.getBukkitEntity();
        stranger.addAttachment(this,"evergarden.admin",true);
        command(player,"spawn aetherius baby");check(previews().isEmpty(),"non-admin cannot spawn");
        check(garden.getCommand("evergarden").tabComplete(player,"evergarden",new String[]{"pets","sp"}).isEmpty(),"non-admin receives no pet completions");
        admin=player.addAttachment(this,"evergarden.admin",true);
        check(garden.getCommand("evergarden").tabComplete(player,"evergarden",new String[]{"pets","spawn","ign"}).equals(List.of("ignis_draconis")),"species completion uses stable model IDs");
        check(garden.getCommand("evergarden").tabComplete(player,"evergarden",new String[]{"pets","spawn","aetherius","m"}).equals(List.of("medium")),"growth-stage completion");
        Bukkit.getPluginManager().callEvent(new PlayerResourcePackStatusEvent(player,JudgePackService.PACK_ID,PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED));
        for(String bad:List.of("spawn unknown baby","spawn aetherius juvenile","spawn","gallery bad","animate bite","growth missing"))command(player,bad);
        check(previews().isEmpty(),"invalid arguments create no shell");
        nextModel();
    }catch(Exception error){throw new RuntimeException(error);}}
    void verifyBatchAssets(){try{
        for(String id:ids){
            var blueprint=ModelEngineAPI.getBlueprint(id);check(blueprint!=null,"import "+id);
            check(blueprint.getAnimations().keySet().containsAll(PetCatalog.ANIMATIONS),"five authored clips "+id);
            Path active=Path.of("plugins/ModelEngine/blueprints/"+id+".bbmodel");
            try(var bundled=garden.getResource("modelengine/pets/"+id+".bbmodel")){
                check(bundled!=null&&Arrays.equals(bundled.readAllBytes(),Files.readAllBytes(active)),"active rig matches shipped model "+id);
            }
        }
        String url=garden.judgePack().url(null);check(!url.isBlank(),"merged pet pack has an HTTP URL");
        Set<String> entries=new HashSet<>();
        try(var stream=URI.create(url).toURL().openStream();var zip=new ZipInputStream(stream)){
            for(ZipEntry entry;(entry=zip.getNextEntry())!=null;)entries.add(entry.getName());
        }
        for(String id:ids)check(entries.stream().anyMatch(name->name.startsWith("assets/modelengine/")&&name.contains(id)),"served assets "+id);
        check(entries.stream().noneMatch(name->name.startsWith("assets/minecraft/shaders/")),"no unrelated player shader override");
    }catch(Exception error){throw new RuntimeException(error);}}
    void nextModel(){try{
        if(index==ids.size()){
            if((batch+1)*9<allIds.size()){batch++;stageBatch(true);later(20,this::awaitReady);}
            else safety();return;
        }
        String id=ids.get(index);String stripped=id.substring("everpet_".length());int end=stripped.lastIndexOf('_');
        command(player,"spawn "+stripped.substring(0,end)+" "+stripped.substring(end+1));
        check(pets.activeCount(player.getUniqueId())==1,"one preview spawned "+id);
        current=(Slime)field(previews().values().iterator().next(),"shell");
        var entity=ModelEngineAPI.getModeledEntity(current.getUniqueId());check(entity!=null,"ME entity registered "+id);
        check(!entity.shouldBeSaved()&&!current.isPersistent(),"no persistent preview "+id);
        check(current.isInvisible()&&!entity.isBaseEntityVisible(),"only successful rig hides shell "+id);
        check(!current.hasAI()&&!current.isAware()&&!current.hasGravity()&&!current.isCollidable()&&current.isInvulnerable()&&current.isSilent(),"harmless stationary shell "+id);
        check(current.getPersistentDataContainer().has(garden.key("pet_model_preview"),PersistentDataType.BYTE),"preview bypasses only its own internal spawn filter "+id);
        check(current.getScoreboardTags().contains("no-level"),"preview excluded from external mob leveling "+id);
        model=entity.getModel(id).orElseThrow();check(!model.canHurt(),"model cannot hurt "+id);
        lines.add("POST SPAWN: ready="+pets.ready()+" online="+player.isOnline()+" found="+(Bukkit.getPlayer(player.getUniqueId())==player)+" dead="+player.isDead()+" admin="+player.hasPermission("evergarden.admin")+" owner="+player.getLocation()+" shell="+current.getLocation());
        tick();check(current.isValid(),"immediate maintenance retains owned preview "+id);
        boneWait=0;later(5,this::inspectModel);
    }catch(Exception error){throw new RuntimeException(error);}}
    void inspectModel(){try{
        String id=ids.get(index);
        if(model.getBones().isEmpty()&&(boneWait+=5)<=60){
            lines.add("Awaiting native bones "+id+" ticks="+boneWait+" blueprint="+model.getBlueprint().getBones().size()+" destroyed="+model.isDestroyed()+" initialized="+model.getModeledEntity().isInitialized()+" shellValid="+current.isValid());
            later(5,this::inspectModel);return;
        }
        check(!model.getBones().isEmpty(),"native animated bones initialized "+id);
        var handler=(IStateMachineHandler)model.getAnimationHandler();
        for(String clip:PetCatalog.ANIMATIONS){
            command(player,"animate "+clip);check(handler.isPlayingAnimation(10,clip),"active animation "+id+" / "+clip);
        }
        UUID uuid=current.getUniqueId();command(player,"clear");
        check(previews().isEmpty()&&!current.isValid(),"shell removed by clear "+id);removed.add(uuid);index++;later(1,this::nextModel);
    }catch(Exception error){throw new RuntimeException(error);}}
    Slime spawnOne()throws Exception{
        command(player,"spawn chrono_fairy adult");check(pets.activeCount(player.getUniqueId())==1,"safety fixture spawned");
        return (Slime)field(previews().values().iterator().next(),"shell");
    }
    void safety(){try{
        for(UUID id:removed)check(ModelEngineAPI.getModeledEntity(id)==null,"removed model registry cleaned "+id);
        Slime shell=spawnOne();command(stranger,"clear");check(shell.isValid()&&pets.activeCount(player.getUniqueId())==1,"another admin clears only their own previews");
        var outgoing=new EntityDamageByEntityEvent(shell,player,EntityDamageEvent.DamageCause.ENTITY_ATTACK,4);
        Bukkit.getPluginManager().callEvent(outgoing);check(outgoing.isCancelled(),"preview cannot damage a player");
        var incoming=new EntityDamageByEntityEvent(player,shell,EntityDamageEvent.DamageCause.ENTITY_ATTACK,4);
        Bukkit.getPluginManager().callEvent(incoming);check(incoming.isCancelled(),"preview cannot receive damage");
        var target=new EntityTargetEvent(shell,player,EntityTargetEvent.TargetReason.CLOSEST_PLAYER);
        Bukkit.getPluginManager().callEvent(target);check(target.isCancelled(),"preview cannot acquire an attack target");
        command(player,"growth chrono_fairy");check(pets.activeCount(player.getUniqueId())==3&&!shell.isValid(),"growth command replaces own models with three stages");
        Set<String> grown=new HashSet<>();for(Object value:previews().values()){
            Slime stage=(Slime)field(value,"shell");grown.addAll(stage.getScoreboardTags());
        }
        for(var stage:PetCatalog.Stage.values())check(grown.contains(PetCatalog.Species.CHRONO_FAIRY.model(stage)),"growth includes "+stage.id);
        command(player,"gallery medium");check(pets.activeCount(player.getUniqueId())==3,"gallery preserves existing previews when models exceed trial capacity");
        command(player,"animate sit");
        for(Object value:previews().values()){
            Slime preview=(Slime)field(value,"shell");var active=ModelEngineAPI.getModeledEntity(preview.getUniqueId()).getModels().values().iterator().next();
            check(((IStateMachineHandler)active.getAnimationHandler()).isPlayingAnimation(10,"sit"),"pose affects all three growth models");
        }
        command(player,"clear");
        garden.getConfig().set("pets.model-preview.max-per-admin",1);shell=spawnOne();command(player,"spawn chrono_fairy baby");
        check(pets.activeCount(player.getUniqueId())==1,"per-admin entity budget enforced");
        garden.getConfig().set("pets.model-preview.max-per-admin",18);command(player,"clear");
        shell=spawnOne();Bukkit.getPluginManager().callEvent(new PlayerChangedWorldEvent(player,world));
        check(!shell.isValid()&&previews().isEmpty(),"world change removes own previews");
        shell=spawnOne();Bukkit.getPluginManager().callEvent(new PlayerQuitEvent(player,"test"));
        check(!shell.isValid()&&previews().isEmpty(),"logout removes own previews");
        shell=spawnOne();admin.setPermission("evergarden.admin",false);tick();
        check(!shell.isValid()&&previews().isEmpty(),"permission removal cleans previews");admin.setPermission("evergarden.admin",true);
        shell=spawnOne();player.teleport(home.clone().add(150,0,0));tick();
        check(!shell.isValid()&&previews().isEmpty(),"distance removes preview");player.teleport(home);
        shell=spawnOne();Object old=previews().values().iterator().next();
        var constructor=old.getClass().getDeclaredConstructors()[0];constructor.setAccessible(true);
        previews().put(shell.getUniqueId(),constructor.newInstance(player.getUniqueId(),shell,field(old,"rig"),System.currentTimeMillis()-1));tick();
        check(!shell.isValid()&&previews().isEmpty(),"expired preview removed by maintenance");
        shell=spawnOne();garden.getConfig().set("pets.model-preview.enabled",false);pets.reload();
        check(!shell.isValid()&&!pets.ready()&&previews().isEmpty(),"config reload clears models and disables backend");
        command(player,"spawn chrono_fairy baby");check(previews().isEmpty(),"unavailable backend never creates hidden shell");
        garden.getConfig().set("pets.model-preview.enabled",true);pets.reload();later(3,this::reloadCheck);
    }catch(Exception error){throw new RuntimeException(error);}}
    void reloadCheck(){try{
        check(pets.ready()&&pets.importedModels()==ids.size(),"backend recovers after ME registration already finished");
        Slime shell=spawnOne();UUID id=shell.getUniqueId();pets.close();
        check(!shell.isValid()&&previews().isEmpty()&&!pets.ready(),"plugin close removes previews and closes backend");
        awaitRemoval(id,0);
    }catch(Exception error){throw new RuntimeException(error);}}
    void awaitRemoval(UUID id,int waited){
        if(ModelEngineAPI.getModeledEntity(id)==null){check(true,"close clears ME entity registry");finish(null);return;}
        if(waited>=60)throw new AssertionError("close did not clear ME entity registry within 60 ticks");
        later(5,()->awaitRemoval(id,waited+5));
    }
    void discard(ServerPlayer actor){if(actor==null)return;var server=MinecraftServer.getServer();server.getPlayerList().getPlayers().remove(actor);server.getPlayerList().getPlayersByUUID().remove(actor.getUUID());actor.discard();}
    void finish(Throwable error){
        if(finished)return;finished=true;if(error!=null){error.printStackTrace();lines.add("ERROR: "+error);}
        try{Files.writeString(Path.of("model-engine-result.txt"),(error==null?"PASS":"FAIL")+": 51 pet models in 6 bounded trial batches / "+checks+" checks\n"+String.join("\n",lines)+"\n");}
        catch(Exception failure){failure.printStackTrace();}
        if(pets!=null)pets.clear(player==null?UUID.randomUUID():player.getUniqueId());
        discard(actor);discard(otherActor);Bukkit.shutdown();
    }
}
