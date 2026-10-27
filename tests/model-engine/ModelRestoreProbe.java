import com.example.voidscape.VoidscapePlugin;
import com.example.voidscape.boss.*;
import com.example.voidscape.pet.PetCatalog;
import com.ticxo.modelengine.api.ModelEngineAPI;
import com.ticxo.modelengine.api.animation.handler.IStateMachineHandler;
import com.ticxo.modelengine.api.model.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.plugin.java.JavaPlugin;
import java.net.URI;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/** Real restart regression: an existing 51-pet install must not evict boss/internal rigs. */
public final class ModelRestoreProbe extends JavaPlugin {
    static final List<String> BOSSES=List.of("arkhaia","arkhaia_acheron","arkhaia_phlegethon","arkhaia_cocytus",
        "arkhaia_banshee_wail","arkhaia_malefeasance","arkhaia_styx","arkhaia_acheron_echo");
    final List<String> lines=new ArrayList<>();
    final List<UUID> removed=new ArrayList<>();
    final List<String> petIds=new ArrayList<>();
    VoidscapePlugin garden;World world;Mob shell;AutoCloseable visual;ArkhaiaModelSupport.Rig rig;
    ModeledEntity entity;ActiveModel model;String selected="";int checks,waited,index,petIndex;boolean finished;
    void check(boolean ok,String label){if(!ok)throw new AssertionError(label);checks++;lines.add("PASS: "+label);getLogger().info("PASS: "+label);}
    void later(long ticks,Runnable work){Bukkit.getScheduler().runTaskLater(this,()->{try{work.run();}catch(Throwable error){finish(error);}},ticks);}
    @Override public void onEnable(){later(1,()->{
        garden=(VoidscapePlugin)Bukkit.getPluginManager().getPlugin("Evergarden");
        check(garden!=null&&garden.isEnabled(),"replacement Evergarden enabled");
        try{selected=Files.readString(Path.of("model-restore-species.txt")).trim();}catch(Exception error){throw new RuntimeException(error);}
        verifyPreservedLibrary();awaitImport();
    });}
    void verifyPreservedLibrary(){try{
        Path library=garden.getDataFolder().toPath().resolve("modelengine/pets/library");
        for(String id:PetCatalog.modelIds())try(var source=garden.getResource("modelengine/pets/"+id+".bbmodel")){
            check(source!=null&&Files.exists(library.resolve(id+".bbmodel")),"prior pet preserved in library "+id);
            check(Arrays.equals(source.readAllBytes(),Files.readAllBytes(library.resolve(id+".bbmodel"))),"prior pet bytes unchanged "+id);
        }
        Set<String> expected=new HashSet<>();
        if(!selected.isEmpty()){
            var species=PetCatalog.Species.find(selected);check(species!=null,"explicit test species valid");
            for(var stage:PetCatalog.Stage.values())expected.add(species.model(stage));
        }
        Set<String> active=new HashSet<>();
        try(var files=Files.list(Path.of("plugins/ModelEngine/blueprints"))){
            for(Path file:files.toList())if(file.getFileName().toString().startsWith("everpet_")&&file.toString().endsWith(".bbmodel"))
                active.add(file.getFileName().toString().replaceFirst("\\.bbmodel$",""));
        }
        check(active.equals(expected),"startup stages only explicitly selected pets; bosses retain normal import budget");
    }catch(Exception error){throw new RuntimeException(error);}}
    void awaitImport(){
        if(garden.arkhaiaModels().ready()&&ModelEngineAPI.getBlueprint("internal_fire")!=null
            &&BOSSES.stream().allMatch(id->ModelEngineAPI.getBlueprint(id)!=null)){prepare();return;}
        if((waited+=10)>1800)throw new AssertionError("normal startup did not import all bosses/internal_fire: "+garden.arkhaiaModels().modelInfo());
        later(10,this::awaitImport);
    }
    void prepare(){try{
        check(ModelEngineAPI.getBlueprint("internal_fire")!=null,"official renderer internal_fire dependency imported");
        for(String id:BOSSES){
            check(ModelEngineAPI.getBlueprint(id)!=null,"normal startup imports "+id);
            String resource=id.equals("arkhaia")?"modelengine/arkhaia.bbmodel":"modelengine/rivers/"+id+".bbmodel";
            try(var source=garden.getResource(resource)){
                check(source!=null&&Arrays.equals(source.readAllBytes(),Files.readAllBytes(Path.of("plugins/ModelEngine/blueprints/"+id+".bbmodel"))),"original boss blueprint unchanged "+id);
            }
        }
        check(garden.petPreviews().importedModels()==(selected.isEmpty()?0:3),"pet import count follows explicit selection");
        if(!selected.isEmpty())for(var stage:PetCatalog.Stage.values()){
            String id=PetCatalog.Species.find(selected).model(stage);petIds.add(id);
            check(ModelEngineAPI.getBlueprint(id)!=null,"selected growth stage imports "+stage.id);
        }
        Set<String> entries=new HashSet<>();
        try(var stream=URI.create(garden.judgePack().url(null)).toURL().openStream();var zip=new ZipInputStream(stream)){
            for(ZipEntry entry;(entry=zip.getNextEntry())!=null;)entries.add(entry.getName());
        }
        for(String id:BOSSES)check(entries.stream().anyMatch(name->name.startsWith("assets/modelengine/")&&name.contains(id)),"served resource pack retains "+id);
        world=garden.world();world.addPluginChunkTicket(0,0,this);next();
    }catch(Exception error){throw new RuntimeException(error);}}
    void next(){try{
        if(index==BOSSES.size()){nextPet();return;}
        String id=BOSSES.get(index);Location at=new Location(world,.5,125,.5);
        if(id.equals("arkhaia")){
            shell=world.spawn(at,Slime.class,mob->{mob.setSize(8);fixture(mob);});
            check(shell.isValid(),"production boss marker permits receiver spawn "+id);
            rig=garden.arkhaiaModels().attach((Slime)shell);visual=rig;
        }else{
            shell=world.spawn(at,WitherSkeleton.class,this::fixture);
            check(shell.isValid(),"production boss marker permits receiver spawn "+id);
            Class<?> type=Class.forName("com.example.voidscape.boss.RiverVisual");
            boolean echo=id.endsWith("_echo");
            var kind=ArkhaiaWaves.Kind.valueOf(echo?"ACHERON":id.substring("arkhaia_".length()).toUpperCase(Locale.ROOT));
            var ctor=type.getDeclaredConstructor(VoidscapePlugin.class,Mob.class,ArkhaiaWaves.Kind.class,boolean.class);ctor.setAccessible(true);
            visual=(AutoCloseable)ctor.newInstance(garden,shell,kind,echo);
            var field=type.getDeclaredField("rig");field.setAccessible(true);rig=(ArkhaiaModelSupport.Rig)field.get(visual);
            check(shell.isInvisible(),"production river visual hides black base only after attachment "+id);
        }
        check(rig!=null,"production rig attached "+id);
        entity=ModelEngineAPI.getModeledEntity(shell.getUniqueId());check(entity!=null,"native model entity registered "+id);
        model=entity.getModel(id).orElseThrow();later(5,this::inspect);
    }catch(Exception error){throw new RuntimeException(error);}}
    void fixture(Mob mob){
        mob.getPersistentDataContainer().set(garden.key("world_boss_entity"),org.bukkit.persistence.PersistentDataType.BYTE,(byte)1);
        com.example.voidscape.compat.LevelledMobsCompat.tagMob(mob,garden,false);
        mob.setAI(false);mob.setAware(false);mob.setGravity(false);mob.setInvulnerable(true);mob.setPersistent(false);
        mob.setRemoveWhenFarAway(false);mob.addScoreboardTag("evergarden_unique");mob.addScoreboardTag("no-level");
    }
    void inspect(){try{
        String id=BOSSES.get(index);
        check(!model.getBones().isEmpty(),"real renderer generates original model bones "+id);
        check(!entity.isBaseEntityVisible(),"native base skeleton is hidden behind successful model "+id);
        check(!entity.shouldBeSaved()&&!shell.isPersistent(),"test model remains temporary "+id);
        for(String clip:List.of("idle","walk")){
            rig.clip(clip,1,shell.getLocation());
            check(((IStateMachineHandler)model.getAnimationHandler()).isPlayingAnimation(10,clip),"real state machine plays "+id+" / "+clip);
        }
        UUID uuid=shell.getUniqueId();visual.close();shell.remove();removed.add(uuid);shell=null;visual=null;rig=null;index++;later(2,this::next);
    }catch(Exception error){throw new RuntimeException(error);}}
    void nextPet(){try{
        if(petIndex==petIds.size()){later(20,this::cleanupCheck);return;}
        String id=petIds.get(petIndex);shell=world.spawn(new Location(world,.5,125,.5),Slime.class,this::fixture);
        check(shell.isValid(),"production marker permits selected pet test receiver "+id);
        var backendField=garden.petPreviews().getClass().getDeclaredField("backend");backendField.setAccessible(true);
        Object backend=backendField.get(garden.petPreviews());
        var attach=backend.getClass().getDeclaredMethod("attach",Slime.class,String.class);attach.setAccessible(true);
        visual=(AutoCloseable)attach.invoke(backend,(Slime)shell,id);
        check(visual!=null,"production pet backend attaches "+id);
        entity=ModelEngineAPI.getModeledEntity(shell.getUniqueId());check(entity!=null,"native pet model entity registered "+id);
        model=entity.getModel(id).orElseThrow();later(5,this::inspectPet);
    }catch(Exception error){throw new RuntimeException(error);}}
    void inspectPet(){try{
        String id=petIds.get(petIndex);
        check(!model.getBones().isEmpty(),"real renderer generates preserved pet bones "+id);
        check(!entity.isBaseEntityVisible(),"pet receiver hides only after successful attachment "+id);
        var animate=visual.getClass().getDeclaredMethod("animate",String.class);animate.setAccessible(true);
        for(String clip:PetCatalog.ANIMATIONS){
            animate.invoke(visual,clip);
            check(((IStateMachineHandler)model.getAnimationHandler()).isPlayingAnimation(10,clip),"real pet state machine plays "+id+" / "+clip);
        }
        UUID uuid=shell.getUniqueId();visual.close();shell.remove();removed.add(uuid);shell=null;visual=null;petIndex++;later(2,this::nextPet);
    }catch(Exception error){throw new RuntimeException(error);}}
    void cleanupCheck(){
        for(UUID uuid:removed)check(ModelEngineAPI.getModeledEntity(uuid)==null,"model registry cleans detached receiver "+uuid);
        finish(null);
    }
    void finish(Throwable error){
        if(finished)return;finished=true;if(error!=null){error.printStackTrace();lines.add("ERROR: "+error);}
        try{if(visual!=null)visual.close();}catch(Exception ignored){}if(shell!=null)shell.remove();
        try{Files.writeString(Path.of("model-engine-result.txt"),(error==null?"PASS":"FAIL")+": model restore after existing 51-pet installation / "+checks+" checks / selected="+selected+"\n"+String.join("\n",lines)+"\n");}
        catch(Exception failure){failure.printStackTrace();}
        Bukkit.shutdown();
    }
}
