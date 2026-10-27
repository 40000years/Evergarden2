import com.example.voidscape.VoidscapePlugin;
import com.ticxo.modelengine.api.ModelEngineAPI;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.plugin.java.JavaPlugin;
import java.nio.file.*;
import java.util.*;

/** Real ME R4.1.1: one broken river blueprint must not hide Arkhaia or the main-boss showcase rigs.
 *  Corrupts arkhaia_styx after Evergarden installs it and before ModelEngine imports. */
public final class BossReadinessProbe extends JavaPlugin {
    VoidscapePlugin garden;int checks,waited;final List<String> lines=new ArrayList<>();
    void check(boolean ok,String label){if(!ok)throw new AssertionError(label);checks++;lines.add("PASS: "+label);getLogger().info("PASS: "+label);}
    @Override public void onEnable(){
        garden=(VoidscapePlugin)Bukkit.getPluginManager().getPlugin("Evergarden");
        try{
            Path styx=Bukkit.getPluginManager().getPlugin("ModelEngine").getDataFolder().toPath().resolve("blueprints/arkhaia_styx.bbmodel");
            Files.writeString(styx,"{\"meta\":{\"format_version\":\"4.10\",\"model_format\":\"free\"},\"elements\":[{\"name\":\"hitbox\",\"from\":0}]}");
        }catch(Exception error){finish(error);return;}
        Bukkit.getScheduler().runTaskLater(this,this::await,20);
    }
    void await(){
        try{
            if(!garden.arkhaiaModels().ready("arkhaia")){
                if((waited+=10)>1800)throw new AssertionError("Arkhaia never became available: "+garden.arkhaiaModels().modelInfo());
                Bukkit.getScheduler().runTaskLater(this,this::await,10);return;
            }
            check(ModelEngineAPI.getBlueprint("arkhaia_styx")==null,"corrupted river blueprint is rejected by ModelEngine");
            check(!garden.arkhaiaModels().ready(),"aggregate readiness reports the missing blueprint");
            check(garden.arkhaiaModels().modelInfo().contains("arkhaia_styx"),"diagnostics name the failed blueprint: "+garden.arkhaiaModels().modelInfo());
            check(garden.arkhaiaModels().ready("arkhaia")&&garden.arkhaiaModels().ready("seraphon"),"Arkhaia and Seraphon stay available");
            check(!garden.arkhaiaModels().ready("arkhaia_styx")&&garden.arkhaiaModels().ready("arkhaia_acheron"),"only the broken minion is unavailable");
            World world=Bukkit.getWorlds().getFirst();Location at=world.getSpawnLocation().clone().add(0,2,0);
            Slime anchor=world.spawn(at,Slime.class,e->{e.setAI(false);e.setPersistent(false);e.setInvisible(true);});
            var rig=garden.arkhaiaModels().attachBoss(anchor,"seraphon",3.2);
            check(rig!=null&&ModelEngineAPI.getModeledEntity(anchor.getUniqueId()).getModel("seraphon").isPresent(),"Seraphon rig attaches despite the broken minion");
            check(garden.arkhaiaModels().attachRiver(anchor,"arkhaia_styx")==null,"broken minion falls back instead of throwing");
            rig.close();anchor.remove();
            finish(null);
        }catch(Throwable error){finish(error);}
    }
    void finish(Throwable error){
        if(error!=null){error.printStackTrace();lines.add("ERROR: "+error);}
        lines.add(0,(error==null?"PASS":"FAIL")+": main-boss readiness / "+checks+" checks");
        try{Files.write(Path.of("model-engine-result.txt"),lines);}catch(Exception e){getLogger().warning(e.toString());}
        Bukkit.getScheduler().runTaskLater(this,Bukkit::shutdown,5);
    }
}
