import com.example.sevensins.WrathCombat;

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
        // Warning rotations must agree with hits across every world yaw.
        for (int angle = 0; angle < 360; angle += 5) {
            double a = Math.toRadians(angle), dx = Math.cos(a), dz = Math.sin(a);
            check(WrathCombat.inSweep(dx*4, dz*4, dx, dz), "Sweep rotation " + angle);
            check(!WrathCombat.inSweep(-dx*4, -dz*4, dx, dz), "Back safe at " + angle);
            check(WrathCombat.inCharge(dx*1.3, dz*1.3, dx, dz, 1.8), "Charge rotation " + angle);
        }
        System.out.println("PASS: " + checks + " combat geometry checks");
    }
}
