import com.example.sevensins.WrathCombat;
import com.example.sevensins.WrathPose;
import com.example.sevensins.WrathAnimation;
import com.example.sevensins.WrathBoss;
import org.joml.Vector3f;

public final class WrathCombatChecks {
    private static int checks;
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); checks++; }
    public static void main(String[] args) {
        check(WrathCombat.inSweep(0, 4, 0, 1), "Front is dangerous");
        check(!WrathCombat.inSweep(0, -1, 0, 1), "Behind boss is safe");
        check(!WrathCombat.inSweep(0, 5.01, 0, 1), "Outside sweep is safe");
        check(!WrathCombat.inSweep(3, 0, 0, 1), "Outside 150-degree arc is safe");
        check(WrathCombat.inSlam(6.49, 0, 0), "Ground inside slam is dangerous");
        check(!WrathCombat.inSlam(3, 0, 0.66), "Timed jump dodges slam");
        check(!WrathCombat.inSlam(6.51, 0, 0), "Outside slam is safe");
        check(!WrathCombat.inRing(2.99, 0, 0), "Center of ring is safe");
        check(WrathCombat.inRing(5, 0, 0), "Annulus is dangerous");
        check(!WrathCombat.inRing(9.01, 0, 0), "Outside ring is safe");
        check(WrathCombat.inCharge(0.5, 1.7, 0, 1, 1.8), "Charge hits between server updates");
        check(!WrathCombat.inCharge(1.51, 1, 0, 1, 1.8), "Sidestep clears charge");
        check(WrathPose.turn(179,-179,18)==181,"Yaw seam takes the two-degree path");
        check(WrathPose.turn(-179,179,18)==-181,"Reverse yaw seam takes the two-degree path");
        check(WrathPose.turn(0,160,18)==18,"Large target jumps have bounded visual turn speed");
        check(WrathPose.turn(720,1,18)==721,"Unwrapped headings remain continuous");
        check(WrathCombat.inStomp(0,3.19,0),"Stomp reaches a nearby grounded player");
        check(!WrathCombat.inStomp(0,3.21,0),"Stomp does not reach beyond its warning");
        check(!WrathCombat.inStomp(0,1,0.66),"Jumping clears the normal stomp");
        animationChecks();
        // Warning rotations must agree with hits across every world yaw.
        for (int angle = 0; angle < 360; angle += 5) {
            double a = Math.toRadians(angle), dx = Math.cos(a), dz = Math.sin(a);
            check(WrathCombat.inSweep(dx*4, dz*4, dx, dz), "Sweep rotation " + angle);
            check(!WrathCombat.inSweep(-dx*4, -dz*4, dx, dz), "Back safe at " + angle);
            check(WrathCombat.inCharge(dx*1.3, dz*1.3, dx, dz, 1.8), "Charge rotation " + angle);
        }
        System.out.println("PASS: " + checks + " combat geometry checks");
    }

    private static void animationChecks() {
        for(var attack:WrathBoss.Attack.values()) {
            for(var state:new WrathBoss.State[]{WrathBoss.State.WINDUP,WrathBoss.State.STRIKE,WrathBoss.State.RECOVERY}) {
                for(int step=0;step<=20;step++) {
                    var pose=WrathAnimation.sample(state,attack,step/20.0,0,0,0,false);
                    var arm=pose.get("right_arm");
                    Vector3f grip=arm.rotation().transform(new Vector3f(0,-1.08f,-0.02f)).add(arm.position());
                    check(grip.distance(pose.get("cleaver").position())<0.0001,"Hammer stays attached to hand throughout "+attack+" "+state);
                }
            }
            var raised=WrathAnimation.sample(WrathBoss.State.WINDUP,attack,1,0,0,0,false);
            var swing=WrathAnimation.sample(WrathBoss.State.STRIKE,attack,0,0,0,0,false);
            var contact=WrathAnimation.sample(WrathBoss.State.STRIKE,attack,1,0,0,0,false);
            var recover=WrathAnimation.sample(WrathBoss.State.RECOVERY,attack,0,0,0,0,false);
            for(String bone:raised.keySet()) {
                check(raised.get(bone).position().distance(swing.get(bone).position())<0.0001
                        &&Math.abs(raised.get(bone).rotation().dot(swing.get(bone).rotation()))>0.9999,"Continuous windup/swing "+attack+" "+bone);
                check(contact.get(bone).position().distance(recover.get(bone).position())<0.0001
                        &&Math.abs(contact.get(bone).rotation().dot(recover.get(bone).rotation()))>0.9999,"Continuous impact/recovery "+attack+" "+bone);
            }
        }
        var hammer=WrathAnimation.sample(WrathBoss.State.STRIKE,WrathBoss.Attack.SLAM,1,0,0,0,false).get("cleaver");
        Vector3f head=hammer.rotation().transform(new Vector3f(0,1.22f,0)).add(hammer.position());
        Vector3f handle=hammer.rotation().transform(new Vector3f(0,-1.25f,0)).add(hammer.position());
        check(head.y<0.4&&head.y>0&&head.z<hammer.position().z,"Hammer head contacts ground in front of the grip: "+head);
        check(handle.y>hammer.position().y&&handle.y>head.y,"Handle stays above the striking head");
        var raisedFoot=WrathAnimation.sample(WrathBoss.State.WINDUP,WrathBoss.Attack.STOMP,1,0,0,0,false).get("left_leg");
        var landedFoot=WrathAnimation.sample(WrathBoss.State.STRIKE,WrathBoss.Attack.STOMP,1,0,0,0,false).get("left_leg");
        check(raisedFoot.position().y-landedFoot.position().y>0.4,"Stomp lifts foot before contact");
    }
}
