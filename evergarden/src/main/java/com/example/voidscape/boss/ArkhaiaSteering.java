package com.example.voidscape.boss;

/** Acceleration-limited steering, independent of Bukkit and of skill decisions. */
public final class ArkhaiaSteering {
    public record Step(double x,double z) {public double length(){return Math.hypot(x,z);}}
    private double vx,vz;
    public Step update(double dx,double dz,ArkhaiaBrain.Frame frame,int orbitSign,double yaw){
        double distance=Math.hypot(dx,dz),nx=distance>.001?dx/distance:0,nz=distance>.001?dz/distance:1;
        double x=0,z=0,speed=0;
        switch(frame.state()){
            case ORBIT -> {
                // A dead band prevents forward/backward jitter at the desired range.
                double radial=distance>26?(distance-26)*.25:0;
                radial=Math.clamp(radial,-1,1);
                x=nx*radial;z=nz*radial;speed=.09;
            }
            case BACKSTEP,RETREAT -> {x=-nx;z=-nz;speed=frame.state()==ArkhaiaBrain.State.BACKSTEP?.34:.28;}
            case ACTIVE -> {
                // A short eased lunge carries the sword through its release.
                if(frame.action()==ArkhaiaBrain.Action.LIGHT||frame.action()==ArkhaiaBrain.Action.HEAVY){
                    x=-Math.sin(Math.toRadians(yaw));z=Math.cos(Math.toRadians(yaw));
                    speed=(frame.action()==ArkhaiaBrain.Action.HEAVY?.24:.15)*Math.sin(Math.PI*frame.progress());
                }
            }
            default -> {}
        }
        double length=Math.hypot(x,z);
        if(length>1){x/=length;z/=length;}
        double targetX=x*speed,targetZ=z*speed;
        double ax=targetX-vx,az=targetZ-vz,acceleration=Math.hypot(ax,az);
        double limit=frame.state()==ArkhaiaBrain.State.STUN?.06:.035;
        if(acceleration>limit){ax*=limit/acceleration;az*=limit/acceleration;}
        vx+=ax;vz+=az;
        if(Math.hypot(vx,vz)<.0005){vx=0;vz=0;}
        return new Step(vx,vz);
    }
    public void blocked(){vx*=.35;vz*=.35;}
    public void stop(){vx=0;vz=0;}
    public static boolean tracksTarget(ArkhaiaBrain.Frame frame){
        return switch(frame.state()){
            case ACTIVE,STUN,RECOVER,TRANSITION -> false;
            // Lock just the final 3 ticks of the telegraph so a sidestep still works.
            case WINDUP -> frame.age()<6;
            default -> true;
        };
    }
    public static float facingYaw(float current,double dx,double dz){
        if(dx*dx+dz*dz<.001)return current;
        double desired=Math.toDegrees(Math.atan2(-dx,dz));
        double turn=((desired-current)%360+540)%360-180;
        return (float)(current+(Math.abs(turn)<.5?turn:Math.clamp(turn*.5,-18,18)));
    }
}
