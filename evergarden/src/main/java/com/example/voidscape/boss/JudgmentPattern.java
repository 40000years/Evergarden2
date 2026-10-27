package com.example.voidscape.boss;

/** Shared warning/damage geometry and explicit phase pressure; units are blocks and server ticks. */
public final class JudgmentPattern {
    private JudgmentPattern(){}
    public record Tuning(double slamRadius,double laneHalfWidth,double ringHalfWidth,
                         int slamWarning,int crossWarning,int ringWarning,int recovery,int pulses){}
    public static Tuning forPhase(int phase){
        return switch(phase){
            case 1 -> new Tuning(11,5,7,48,40,40,68,1);
            case 2 -> new Tuning(12,5.5,8,52,36,38,56,2);
            default -> new Tuning(12,6,8,52,36,36,48,3);
        };
    }
    public static boolean cross(double x,double z,double angle,double halfWidth){
        double along=x*Math.cos(angle)+z*Math.sin(angle);
        double across=-x*Math.sin(angle)+z*Math.cos(angle);
        return Math.abs(along)<=halfWidth||Math.abs(across)<=halfWidth;
    }
    public static boolean ring(double x,double z,double radius,double halfWidth){
        return Math.abs(Math.hypot(x,z)-radius)<=halfWidth;
    }
    /** The slam's center must land inside the visible three-block lure rune. */
    public static boolean baitRune(double x,double z,double runeX,double runeZ){return Math.hypot(x-runeX,z-runeZ)<=3;}
}
