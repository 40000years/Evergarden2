package com.example.voidscape.pet;

/** Shared angle math for a head that leads a turning body, without depending on server state. */
final class PetAttention {
    private PetAttention() {}

    static float wrap(float degrees){
        degrees%=360;
        if(degrees>=180)degrees-=360;
        if(degrees< -180)degrees+=360;
        return degrees;
    }
    static float approachYaw(float current,float target,float maxStep){
        float difference=wrap(target-current);
        return wrap(current+Math.clamp(difference,-maxStep,maxStep));
    }
    /** Bukkit-relative head yaw: positive turns to the pet's right. */
    static float headYaw(float targetYaw,float bodyYaw,boolean moving){
        float limit=moving?45:60;
        return Math.clamp(wrap(targetYaw-bodyYaw),-limit,limit);
    }
    /** Positive pitch looks up; the upper limit also matches the Bedrock property range. */
    static float targetPitch(double dy,double horizontal){
        return (float)Math.clamp(Math.toDegrees(Math.atan2(dy,Math.max(.1,horizontal))),-30,25);
    }
    /** Sharp turns lose forward speed, reaching a stop while the destination is behind. */
    static double turnStride(float yawError){
        double angle=Math.abs(wrap(yawError));
        if(angle>=90)return 0;
        double forward=Math.cos(Math.toRadians(angle));
        return forward*forward;
    }
}
