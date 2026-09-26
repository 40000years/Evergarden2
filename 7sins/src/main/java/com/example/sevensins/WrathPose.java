package com.example.sevensins;

/** Shortest-path yaw interpolation, including the -180/+180 seam. */
public final class WrathPose {
    private WrathPose() {}
    public static float turn(float current, float desired, float limit) {
        float delta = (desired - current) % 360;
        if (delta > 180) delta -= 360;
        if (delta < -180) delta += 360;
        return current + Math.max(-limit, Math.min(limit, delta));
    }
}
