import com.destroystokyo.paper.entity.ai.*;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Mob;
import org.bukkit.plugin.Plugin;
import java.util.EnumSet;
import java.util.function.BooleanSupplier;

/** Optional Paper Goal adapter for an AI-enabled carrier. Do not also run a scheduler. */
public final class ArkhaiaGoal implements Goal<Mob> {
    private final Mob mob;
    private final GoalKey<Mob> key;
    private final Runnable updateCombat;
    private final BooleanSupplier encounterActive;
    public ArkhaiaGoal(Plugin plugin,Mob mob,Runnable updateCombat,BooleanSupplier encounterActive){
        this.mob=mob;this.updateCombat=updateCombat;this.encounterActive=encounterActive;
        key=GoalKey.of(Mob.class,new NamespacedKey(plugin,"arkhaia_combat"));
    }
    @Override public boolean shouldActivate(){return mob.isValid()&&!mob.isDead()&&encounterActive.getAsBoolean();}
    @Override public boolean shouldStayActive(){return shouldActivate();}
    @Override public void tick(){updateCombat.run();}
    @Override public void stop(){mob.getPathfinder().stopPathfinding();}
    @Override public GoalKey<Mob> getKey(){return key;}
    @Override public EnumSet<GoalType> getTypes(){return EnumSet.of(GoalType.MOVE,GoalType.LOOK);}
}
