import com.example.voidscape.boss.ArkhaiaBrain;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import java.util.*;
import java.util.function.*;

/** Mojang-mapped NMS example, compiled against this workspace's Paper 26.2 server. */
public final class ArkhaiaPathfinderGoal extends Goal {
    private final PathfinderMob mob;
    private final Supplier<LivingEntity> target;
    private final BooleanSupplier enabled;
    private final DoubleSupplier healthFraction;
    private final Consumer<ArkhaiaBrain.Frame> combatAndAnimation;
    private final ArkhaiaBrain brain=new ArkhaiaBrain(new Random(),350);
    public ArkhaiaPathfinderGoal(PathfinderMob mob,Supplier<LivingEntity> target,BooleanSupplier enabled,DoubleSupplier healthFraction,
                                Consumer<ArkhaiaBrain.Frame> combatAndAnimation){
        this.mob=mob;this.target=target;this.enabled=enabled;this.healthFraction=healthFraction;this.combatAndAnimation=combatAndAnimation;
        setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK));
    }
    @Override public boolean canUse(){return enabled.getAsBoolean()&&mob.isAlive();}
    @Override public boolean canContinueToUse(){return canUse();}
    @Override public boolean requiresUpdateEveryTick(){return true;}
    @Override public void stop(){mob.getNavigation().stop();mob.getMoveControl().strafe(0,0);}
    @Override public void tick(){
        var victim=target.get();boolean valid=victim!=null&&victim.isAlive();
        double distance=valid?mob.distanceTo(victim):0;
        var frame=brain.update(mob.tickCount,new ArkhaiaBrain.Sense(valid,distance,healthFraction.getAsDouble(),valid&&mob.hasLineOfSight(victim)));
        if(valid){
            if(frame.state()==ArkhaiaBrain.State.ORBIT||frame.state()==ArkhaiaBrain.State.RETREAT
                ||frame.state()==ArkhaiaBrain.State.BACKSTEP||frame.state()==ArkhaiaBrain.State.WINDUP&&frame.age()<5)
                mob.getLookControl().setLookAt(victim,15,10);
            if(frame.state()==ArkhaiaBrain.State.ORBIT){
                if(distance>12)mob.getNavigation().moveTo(victim,1.0);
                else{
                    mob.getNavigation().stop();
                    float radial=distance>7?.5f:distance<5?-.6f:0;
                    mob.getMoveControl().strafe(radial,.65f);
                }
            }else{
                mob.getNavigation().stop();
                boolean retreat=frame.state()==ArkhaiaBrain.State.BACKSTEP||frame.state()==ArkhaiaBrain.State.RETREAT;
                mob.getMoveControl().strafe(retreat?-.9f:0,0);
            }
        }else stop();
        combatAndAnimation.accept(frame);
    }
    public void onDamage(double amount){brain.hit(amount,mob.tickCount);}
}
