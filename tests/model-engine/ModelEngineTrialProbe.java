import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.plugin.java.JavaPlugin;
import java.nio.file.*;
import java.util.*;

/** Isolated compatibility probe; only installed by tools/try_model_engine.py. */
public final class ModelEngineTrialProbe extends JavaPlugin {
    @Override public void onEnable(){Bukkit.getScheduler().runTaskLater(this,this::test,120);}
    private void test(){
        var lines=new ArrayList<String>();boolean enabled=false;
        try{
            lines.add("Paper: "+Bukkit.getVersion());
            for(String name:List.of("ModelEngine","Evergarden","advance-magic","LevelledMobs","Geyser-Spigot","floodgate")){
                var plugin=Bukkit.getPluginManager().getPlugin(name);
                lines.add(name+": "+(plugin==null?"NOT LOADED":plugin.getDescription().getVersion()+" enabled="+plugin.isEnabled()));
                if(name.equals("ModelEngine"))enabled=plugin!=null&&plugin.isEnabled();
            }
            if(enabled){
                var plugin=Bukkit.getPluginManager().getPlugin("ModelEngine");
                var type=Class.forName("com.ticxo.modelengine.api.ModelEngineAPI",true,plugin.getClass().getClassLoader());
                lines.add("ModelEngine API: "+type.getName());
                lines.add("ModelEngine generated files: "+Files.exists(plugin.getDataFolder().toPath().resolve("config.yml")));
            }
        }catch(Throwable error){enabled=false;lines.add("Probe error: "+error);getLogger().log(java.util.logging.Level.SEVERE,"Model Engine probe",error);}
        lines.add(0,enabled?"PASS: ModelEngine starts on this server":"FAIL: ModelEngine did not start on this server");
        try{Files.write(Path.of("model-engine-result.txt"),lines);}catch(Exception e){getLogger().warning(e.toString());}
        lines.forEach(getLogger()::info);Bukkit.getScheduler().runTaskLater(this,Bukkit::shutdown,5);
    }
}
