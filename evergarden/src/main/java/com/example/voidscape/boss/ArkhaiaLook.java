package com.example.voidscape.boss;

/** Independent, bounded gaze. Positive pitch looks down; yaw is relative to the body. */
public final class ArkhaiaLook {
    public record Gaze(float yaw,float pitch) {}
    private double worldYaw,pitch;
    private boolean initialized;
    public Gaze update(float bodyYaw,double dx,double dy,double dz,boolean target,boolean stunned){
        if(!initialized){worldYaw=bodyYaw;initialized=true;}
        double distance=Math.hypot(dx,dz);
        double desiredYaw=target&&distance>.01?Math.toDegrees(Math.atan2(-dx,dz)):bodyYaw;
        double desiredPitch=target?Math.toDegrees(Math.atan2(-dy,Math.max(.1,distance))):0;
        if(stunned){desiredYaw=bodyYaw;desiredPitch=0;}
        worldYaw+=Math.clamp(wrap(desiredYaw-worldYaw)*.6,-28,28);
        double relative=Math.clamp(wrap(worldYaw-bodyYaw),-60,60);
        worldYaw=bodyYaw+relative;
        pitch+=Math.clamp((Math.clamp(desiredPitch,-25,60)-pitch)*.4,-10,10);
        return new Gaze((float)relative,(float)pitch);
    }
    private static double wrap(double angle){return (angle%360+540)%360-180;}
}
