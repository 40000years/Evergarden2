package com.example.voidscape.boss;

import java.util.*;

/** One immutable plan drives both warning geometry and damage. Coordinates are world X/Z. */
public final class ArkhaiaPattern {
    /** SECTOR: halfWidth is the half-angle in radians. HALF: halfWidth is the side sign (+1 or -1) of the facing angle. */
    public enum Shape { CIRCLE, RING, LANE, SECTOR, HALF }
    public record Hazard(Shape shape,double x,double z,double angle,double radius,double halfWidth,int tick,int index){
        public boolean contains(double px,double pz){
            double dx=px-x,dz=pz-z,distance=Math.hypot(dx,dz);
            return switch(shape){
                case CIRCLE -> distance<=radius;
                case RING -> Math.abs(distance-radius)<=halfWidth;
                case LANE -> Math.abs(dx*Math.cos(angle)+dz*Math.sin(angle))<=radius
                    &&Math.abs(-dx*Math.sin(angle)+dz*Math.cos(angle))<=halfWidth;
                case SECTOR -> distance<=radius&&(distance<1.2
                    ||Math.abs(Math.IEEEremainder(Math.atan2(dz,dx)-angle,Math.PI*2))<=halfWidth);
                case HALF -> distance<=radius&&(-dx*Math.sin(angle)+dz*Math.cos(angle))*Math.signum(halfWidth)>=-.6;
            };
        }
    }
    /** World angle of a Minecraft yaw's forward direction, in the LANE/SECTOR convention. */
    public static double facing(double yaw){double a=Math.toRadians(yaw);return Math.atan2(Math.cos(a),-Math.sin(a));}
    /** A forward lane starting at the body: length blocks long, halfWidth wide. */
    public static Hazard forward(double bx,double bz,double yaw,double length,double halfWidth,int tick,int index){
        double angle=facing(yaw);
        return new Hazard(Shape.LANE,bx+Math.cos(angle)*length/2,bz+Math.sin(angle)*length/2,angle,length/2,halfWidth,tick,index);
    }
    private ArkhaiaPattern(){}
    public static List<Hazard> plan(ArkhaiaBrain.Action action,int phase,double bx,double bz,double tx,double tz,double cx,double cz,double yaw){
        double d=Math.hypot(tx-cx,tz-cz);if(d>40){tx=cx+(tx-cx)*40/d;tz=cz+(tz-cz)*40/d;}
        double a=Math.toRadians(yaw),nx=-Math.sin(a),nz=Math.cos(a),rightX=nz,rightZ=-nx;
        var result=new ArrayList<Hazard>();
        switch(action){
            case RIFT -> {
                double[] offsets=phase==1?new double[]{0,-5,5}:new double[]{-2.5,2.5,-7.5,7.5};
                for(int i=0;i<offsets.length;i++)result.add(new Hazard(Shape.CIRCLE,tx+rightX*offsets[i],tz+rightZ*offsets[i],0,3.2,0,4+i*8,i));
            }
            case NOVA -> {for(int i=0;i<3;i++)result.add(new Hazard(Shape.RING,bx,bz,0,6+i*6,1.7,8+i*14,i));}
            case PORTALS -> {
                for(int i=0;i<4;i++){
                    double side=(i-1.5)*4,sx=bx+rightX*side-nx*4,sz=bz+rightZ*side-nz*4;
                    double ex=tx+rightX*(i-1.5)*3+nx*5,ez=tz+rightZ*(i-1.5)*3+nz*5;
                    result.add(new Hazard(Shape.LANE,(sx+ex)/2,(sz+ez)/2,Math.atan2(ez-sz,ex-sx),Math.hypot(ex-sx,ez-sz)/2,1.35,6+i*12,i));
                }
            }
            case ECLIPSE -> {
                int count=phase==1?3:4;
                for(int i=0;i<count;i++){
                    double offset=(i-(count-1)/2.0)*7;
                    result.add(new Hazard(Shape.LANE,cx+rightX*offset,cz+rightZ*offset,Math.atan2(nz,nx),32,2,10+i*10,i));
                }
            }
            default -> {}
        }
        return List.copyOf(result);
    }
}
