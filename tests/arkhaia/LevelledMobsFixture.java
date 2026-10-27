import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.entity.Mob;
import org.bukkit.event.*;
import org.bukkit.event.entity.CreatureSpawnEvent;
import io.github.arcaneplugins.levelledmobs.events.MobPreLevelEvent;
/** No levelling logic: verifies optional plugin load order and event hook wiring. */
public final class LevelledMobsFixture extends JavaPlugin implements Listener {
    @Override public void onEnable(){getServer().getPluginManager().registerEvents(this,this);}
    @EventHandler public void spawn(CreatureSpawnEvent event){
        if(!(event.getEntity() instanceof Mob))return;
        var pre=new MobPreLevelEvent(event.getEntity());getServer().getPluginManager().callEvent(pre);
        if(!pre.isCancelled())event.getEntity().addScoreboardTag("fixture-levelled");
    }
}
