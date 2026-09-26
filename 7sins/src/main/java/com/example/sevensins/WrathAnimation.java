package com.example.sevensins;

import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.util.*;

/** Local skeletal poses: one shoulder-to-hand chain owns the hammer grip. */
public final class WrathAnimation {
    public record Pose(Vector3f position, Quaternionf rotation) {}
    private WrathAnimation() {}
    public static float ease(double value) {
        float t = (float)Math.max(0, Math.min(1, value));
        return t*t*(3-2*t);
    }
    private static float mix(float a, float b, float t) { return a+(b-a)*t; }

    public static Map<String, Pose> sample(WrathBoss.State state, WrathBoss.Attack attack,
            double progress, double stride, float walking, int ticks, boolean enraged) {
        float p=ease(progress), active=0, swing=0;
        if(state==WrathBoss.State.WINDUP) active=p;
        if(state==WrathBoss.State.STRIKE) { active=1; swing=p; }
        if(state==WrathBoss.State.RECOVERY) {
            active=1-ease((progress-0.15)/0.85); swing=1;
        }
        float gait=(float)Math.sin(stride)*walking;
        float breath=(float)Math.sin(ticks*0.065)*0.018f;
        float bodyX=-0.035f*walking, bodyY=gait*0.055f, bodyZ=gait*0.025f;
        float drop=-(float)Math.abs(Math.sin(stride))*walking*0.035f;
        float armR=gait*0.22f, armL=-gait*0.32f;
        float armYaw=0, weaponX=-2.3f, weaponY=0, weaponZ=0.35f;
        float legR=-gait*0.32f, legL=gait*0.32f, stompLift=0;
        if(active>0) {
            switch(attack) {
                case SLAM -> {
                    bodyX+=mix(0.12f,-0.18f,swing)*active;
                    drop+=mix(-0.06f,-0.15f,swing)*active;
                    armR=mix(armR,mix(2.7f,0.95f,swing),active);
                    armL=mix(armL,mix(1.9f,0.7f,swing),active);
                    weaponX=mix(weaponX,mix(0.35f,-2.55f,swing),active);
                    weaponZ=mix(weaponZ,0,active);
                }
                case SWEEP -> {
                    bodyY+=mix(-0.65f,0.65f,swing)*active;
                    bodyX-=0.1f*active;
                    armR=mix(armR,1.25f,active);
                    armL=mix(armL,0.55f,active);
                    armYaw=mix(-0.7f,0.7f,swing)*active;
                    weaponX=mix(weaponX,-1.35f,active);
                    weaponY=mix(-0.8f,0.8f,swing)*active;
                    weaponZ=-0.18f*active;
                }
                case STOMP -> {
                    stompLift=(1-swing)*active;
                    legL=mix(legL,0.75f*stompLift,active);
                    bodyZ-=0.09f*stompLift;
                    drop+=mix(0.08f,-0.11f,swing)*active;
                    bodyX-=0.08f*swing*active;
                    armL=mix(armL,-0.2f,active);
                }
                case RING, BLADES -> {
                    armL=mix(armL,1.7f,active);
                    bodyY-=0.15f*active;
                    bodyX+=0.08f*active;
                }
                case CHARGE -> {bodyX-=0.24f*active; armL-=0.3f*active;}
            }
        }
        if(state==WrathBoss.State.CHARGE) {bodyX=-0.28f; armL=-0.35f;}
        if(state==WrathBoss.State.STAGGER) {
            float weight=progress<0.12?ease(progress/0.12):1-ease((progress-0.75)/0.25);
            bodyX-=0.36f*weight; drop-=0.2f*weight;
        }
        Quaternionf torso=new Quaternionf().rotationXYZ(bodyX,bodyY,bodyZ);
        Vector3f pelvis=new Vector3f(0,1.2f+drop+breath,0);
        Map<String,Pose> poses=new LinkedHashMap<>();
        put(poses,"body",pelvis,torso,new Vector3f(0,0.45f,0),new Quaternionf());
        put(poses,"head",pelvis,torso,new Vector3f(0,1.65f,0),new Quaternionf().rotationX(-bodyX*0.35f));
        put(poses,"core",pelvis,torso,new Vector3f(0,0.8f,-0.42f),new Quaternionf());
        put(poses,"crown",pelvis,torso,new Vector3f(0,1.95f,0),new Quaternionf().rotationY(ticks*(enraged?0.035f:0.012f)));
        Quaternionf rightArm=new Quaternionf().rotationXYZ(armR,armYaw,-0.08f);
        Quaternionf leftArm=new Quaternionf().rotationXYZ(armL,0,-0.06f);
        Vector3f shoulderR=new Vector3f(-0.8f,1.1f,0);
        put(poses,"right_arm",pelvis,torso,shoulderR,rightArm);
        put(poses,"left_arm",pelvis,torso,new Vector3f(0.8f,1.1f,0),leftArm);
        // The shaft's grip is the model origin. Never animate the weapon translation independently.
        Vector3f hand=rightArm.transform(new Vector3f(0,-1.08f,-0.02f)).add(shoulderR);
        put(poses,"cleaver",pelvis,torso,hand,new Quaternionf().rotationXYZ(weaponX,weaponY,weaponZ));
        // Hip pivots are above the mesh center; lift the foot only during the swing half of each step.
        leg(poses,"left_leg",0.35f,legL,Math.max(0,gait)*0.10f+stompLift*0.38f);
        leg(poses,"right_leg",-0.35f,legR,Math.max(0,-gait)*0.10f);
        return poses;
    }
    private static void put(Map<String,Pose> result,String id,Vector3f pivot,Quaternionf parent,Vector3f offset,Quaternionf local) {
        result.put(id,new Pose(parent.transform(new Vector3f(offset)).add(pivot),new Quaternionf(parent).mul(local)));
    }
    private static void leg(Map<String,Pose> result,String id,float x,float angle,float lift) {
        Quaternionf rotation=new Quaternionf().rotationX(angle);
        Vector3f position=rotation.transform(new Vector3f(0,-0.35f,0)).add(x,1.225f+lift,0);
        result.put(id,new Pose(position,rotation));
    }
}
