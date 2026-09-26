package com.example.sevensins;

/** Geometry shared by warnings and hits. All coordinates are relative to the boss. */
public final class WrathCombat {
    private WrathCombat() {}

    public static boolean inArrival(double x, double z, double height, double radius) {
        return Math.hypot(x, z) <= radius && height >= -2 && height <= 20;
    }

    public static boolean inStomp(double x, double z, double feetAboveGround) {
        return Math.hypot(x, z) <= 3.2 && feetAboveGround < 0.65 && feetAboveGround > -2;
    }

    public static boolean inSweep(double x, double z, double dx, double dz) {
        double length = Math.hypot(x, z);
        return length <= 5.0 && (length < 0.001 || (x * dx + z * dz) / length >= Math.cos(Math.toRadians(75)));
    }

    public static boolean inSlam(double x, double z, double feetAboveGround) {
        return Math.hypot(x, z) <= 6.5 && feetAboveGround < 0.65 && feetAboveGround > -2;
    }

    public static boolean inRing(double x, double z, double feetAboveGround) {
        double r = Math.hypot(x, z);
        return r >= 3.0 && r <= 9.0 && feetAboveGround < 1.5 && feetAboveGround > -2;
    }

    public static boolean inCharge(double x, double z, double dx, double dz, double length) {
        double along = Math.max(0, Math.min(length, x * dx + z * dz));
        return Math.hypot(x - along * dx, z - along * dz) <= 1.5;
    }
}
