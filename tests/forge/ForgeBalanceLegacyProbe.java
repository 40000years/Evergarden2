import com.example.forge.ForgePlugin;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Starts from a v6 config to exercise the real on-disk migration path, not just its helper. */
public class ForgeBalanceLegacyProbe extends JavaPlugin {
    @Override public void onEnable(){Bukkit.getScheduler().runTaskLater(this,()->{
        String result;
        try{
            var forge=(ForgePlugin)Bukkit.getPluginManager().getPlugin("EvergardenForge");
            var c=forge.getConfig();
            if(c.getInt("config-version")!=7 || c.getInt("combat-balance-version")!=2
                    || c.getBoolean("affixes.sharp.enabled") || c.getDouble("affixes.sharp.min")!=.5
                    || c.getDouble("affixes.sharp.max")!=7.0 || c.getDouble("affixes.vigor.min")!=1.0
                    || c.getDouble("affixes.vigor.max")!=5.0
                    || !c.getDoubleList("gems.tier-multipliers").equals(List.of(1.0,1.4,2.0,3.0,4.0))
                    || !Files.exists(forge.getDataFolder().toPath().resolve("config-v6-backup.yml")))
                throw new AssertionError("legacy custom values, previous defaults or backup not preserved");
            result="PASS legacy Forge config migration preserves custom ranges, enabled flag and tier multipliers";
        }catch(Throwable error){result="FAIL "+error;error.printStackTrace();}
        try{Files.writeString(Path.of("forge-result.txt"),result+"\n");}catch(Exception error){error.printStackTrace();}
        Bukkit.getScheduler().runTaskLater(this,Bukkit::shutdown,20);
    },40);}
}
