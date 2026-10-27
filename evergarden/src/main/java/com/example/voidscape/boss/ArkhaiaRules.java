package com.example.voidscape.boss;

/** Rear/front mean the caster/attacker's position, not a projectile's explosion center. */
public final class ArkhaiaRules {
    public enum Kind { ADVANCE_MAGIC, MELEE, PROJECTILE, OTHER }
    private ArkhaiaRules(){}
    private static double facing(double yaw,double dx,double dz){
        double distance=Math.hypot(dx,dz);if(distance<.05)return 0;
        double a=Math.toRadians(yaw);return (-Math.sin(a)*dx+Math.cos(a)*dz)/distance;
    }
    /** 120-degree cone behind the body. */
    public static boolean behind(double yaw,double dx,double dz){return Math.hypot(dx,dz)>=.05&&facing(yaw,dx,dz)<=-.5;}
    /** 120-degree cone in front of the body: where a parry must come from. */
    public static boolean front(double yaw,double dx,double dz){return Math.hypot(dx,dz)>=.05&&facing(yaw,dx,dz)>=.5;}
    /** Posture gain: rear hits and melee risk build it faster; a duel bystander striking the back doubles it. */
    public static double posture(Kind kind,boolean rear,boolean bystander){return (rear?1.5:1)*(kind==Kind.MELEE?1.2:1)*(bystander?2:1);}
}
