package com.example.sevensins;

import org.bukkit.*;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/** Find a supported collision-free surface, including fractional-height blocks. */
public final class WrathSpawn {
    public enum Failure { NO_GROUND, UNEVEN_GROUND, BLOCKED }
    public record Result(Location location, Failure failure) {}
    private static final Vector DOWN = new Vector(0,-1,0);
    private WrathSpawn() {}

    public static Result find(Location origin, double scale) {
        World world=origin.getWorld();
        Vector forward=origin.getDirection().setY(0);
        if(forward.lengthSquared()<0.001) {
            double yaw=Math.toRadians(origin.getYaw());
            forward=new Vector(-Math.sin(yaw),0,Math.cos(yaw));
        }
        forward.normalize();
        Vector side=new Vector(-forward.getZ(),0,forward.getX());
        double halfWidth=0.3*1.65*scale+0.1;
        double height=Math.max(1.95*1.65*scale+0.1,4*scale);
        double preferred=7*scale, nearest=Math.max(7,4*scale);
        preferred=Math.max(preferred,nearest);
        Failure failure=Failure.NO_GROUND;
        for(int step=0;step<=3;step++) for(int offset:new int[]{0,-1,1,-2,2}) {
            double distance=preferred-(preferred-nearest)*step/3;
            Location point=origin.clone().add(forward.clone().multiply(distance)).add(side.clone().multiply(offset*scale));
            point.setX(point.getBlockX()+0.5);point.setZ(point.getBlockZ()+0.5);
            // Collision queries intentionally do not load chunks, so load only this bounded footprint.
            for(int cx=((int)Math.floor(point.getX()-halfWidth))>>4;cx<=((int)Math.floor(point.getX()+halfWidth))>>4;cx++)
                for(int cz=((int)Math.floor(point.getZ()-halfWidth))>>4;cz<=((int)Math.floor(point.getZ()+halfWidth))>>4;cz++)
                    world.getChunkAt(cx,cz);
            double rayY=Math.min(world.getMaxHeight()-0.01,origin.getY()+4);
            double length=Math.min(64,rayY-world.getMinHeight()-0.01);
            if(length<=0)continue;
            RayTraceResult center=surface(world,point.getX(),rayY,point.getZ(),length);
            if(center==null)continue;
            double centerY=center.getHitPosition().getY(), floorY=centerY;
            double[] support=new double[9];int index=0;
            for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++) {
                RayTraceResult hit=surface(world,point.getX()+dx*halfWidth*0.9,rayY,point.getZ()+dz*halfWidth*0.9,length);
                double y=hit==null?Double.NEGATIVE_INFINITY:hit.getHitPosition().getY();
                support[index++]=y;floorY=Math.max(floorY,y);
            }
            int supported=0;for(double y:support)if(floorY-y<=1.25)supported++;
            if(floorY-centerY>1.0||supported<5) {
                if(failure==Failure.NO_GROUND)failure=Failure.UNEVEN_GROUND;
                continue;
            }
            point.setY(floorY+0.01);point.setYaw(origin.getYaw()+180);point.setPitch(0);
            BoundingBox body=new BoundingBox(point.getX()-halfWidth,point.getY(),point.getZ()-halfWidth,
                    point.getX()+halfWidth,point.getY()+height,point.getZ()+halfWidth);
            if(body.getMaxY()>=world.getMaxHeight()||world.hasCollisionsIn(body)||wet(world,body)) {
                failure=Failure.BLOCKED;continue;
            }
            return new Result(point,null);
        }
        return new Result(null,failure);
    }
    private static RayTraceResult surface(World world,double x,double y,double z,double length) {
        return world.rayTraceBlocks(new Location(world,x,y,z),DOWN,length,FluidCollisionMode.NEVER,true);
    }
    private static boolean wet(World world,BoundingBox box) {
        for(int x=(int)Math.floor(box.getMinX());x<=Math.floor(box.getMaxX());x++)
            for(int z=(int)Math.floor(box.getMinZ());z<=Math.floor(box.getMaxZ());z++)
                for(int y=(int)Math.floor(box.getMinY());y<=Math.floor(box.getMaxY());y++) {
                    var block=world.getBlockAt(x,y,z);
                    if(block.isLiquid()||block.getBlockData() instanceof Waterlogged water&&water.isWaterlogged())return true;
                }
        return false;
    }
}
