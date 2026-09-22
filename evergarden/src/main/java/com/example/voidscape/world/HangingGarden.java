package com.example.voidscape.world;

import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import java.util.*;
import static com.example.voidscape.world.LandmarkBlueprint.*;

/** Tall inverted garden island with a continuous climbing route and teal pavilion. */
public final class HangingGarden {
    private HangingGarden() {}
    private static final class Holder {static final LandmarkBlueprint VALUE=build();}
    public static LandmarkBlueprint blueprint(){return Holder.VALUE;}
    private static LandmarkBlueprint build(){
        LandmarkBlueprint b=new LandmarkBlueprint();
        b.island(0,160,0,37,34,60);
        b.island(0,76,5,29,26,30);
        b.disk(0,160,0,16,Material.MOSSY_STONE_BRICKS);
        b.ring(0,160,0,14,1,Material.SMOOTH_QUARTZ);
        // Open pavilion, carved columns, layered oxidized-copper pagoda roof.
        b.box(-9,160,-9,9,161,9,Material.SMOOTH_QUARTZ);
        for(int x:new int[]{-7,7})for(int z:new int[]{-7,7}){
            b.box(x-1,162,z-1,x+1,163,z+1,Material.CHISELED_QUARTZ_BLOCK);
            b.box(x,164,z,x,174,z,Material.QUARTZ_PILLAR);
            b.box(x-1,174,z-1,x+1,175,z+1,Material.CHISELED_QUARTZ_BLOCK);
            b.put(x,176,z,Material.GOLD_BLOCK);
        }
        for(int y=175;y<=181;y++){
            int r=12-(y-175);
            b.box(-r,y,-r,r,y,r,Material.WAXED_OXIDIZED_COPPER);
            if(y%2==1){
                b.box(-r,y,-r,r,y,-r,Material.DARK_PRISMARINE);
                b.box(-r,y,r,r,y,r,Material.DARK_PRISMARINE);
                b.box(-r,y,-r,-r,y,r,Material.DARK_PRISMARINE);
                b.box(r,y,-r,r,y,r,Material.DARK_PRISMARINE);
            }
        }
        b.box(-2,182,-2,2,183,2,Material.GOLD_BLOCK);
        b.box(0,184,0,0,189,0,Material.LIGHTNING_ROD);
        b.put(0,174,0,Material.IRON_CHAIN);b.put(0,173,0,Material.IRON_CHAIN);b.put(0,172,0,Material.IRON_CHAIN);
        b.put(0,171,0,Material.LANTERN,BlockFace.UP);
        for(int[] tree:new int[][]{{-22,-13},{22,-11},{-19,18},{21,17},{0,-25}})b.cherry(tree[0],161,tree[1],10+Math.abs(tree[0])%4);
        for(int i=0;i<28;i++){
            double a=i*Math.PI*2/28;int x=(int)Math.round(35*Math.cos(a)),z=(int)Math.round(32*Math.sin(a));
            if(b.at(x,160,z)==Material.AIR)continue;
            b.ball(x,161,z,2,1.5,2,Material.FLOWERING_AZALEA_LEAVES);
            b.garland(x,159,z,22+(i*13)%40,i%3!=0);
            if(i%4==0)b.crystal((int)(x*.7),112+(i%3)*8,(int)(z*.7),12+i%7);
            if(i%3==0)b.lamp(x,162,z);
        }
        // Crystal roots punctuate the lower, pointed silhouette.
        b.rockSpire(-11,123,4,5,31);b.rockSpire(14,126,-8,4,30);
        b.crystal(0,104,0,17);b.crystal(-15,122,10,19);b.crystal(17,125,-8,16);
        b.waterfall(-18,159,25,99,1);
        b.waterfall(-12,75,28,46,2);
        for(int[] tree:new int[][]{{-19,-3},{18,9},{-12,20}})b.cherry(tree[0],77,tree[1],8);
        b.disk(0,76,5,12,Material.SMOOTH_QUARTZ);
        b.ring(0,76,5,10,1,Material.MOSSY_STONE_BRICKS);
        for(int z=7;z<=27;z++)b.box(-2,76,z,2,76,z,Material.SMOOTH_QUARTZ);
        for(int x:new int[]{-5,5})b.lamp(x,77,22);
        b.gardenDetails(76);b.gardenDetails(160);
        List<Point> route=new ArrayList<>();
        // Radius increases with elevation to follow the cliff rather than cut through it.
        for(int i=0;i<=504;i++){
            double t=i/504.0,a=t*Math.PI*5.6,r=14+26*Math.pow(t,.60);
            route.add(new Point((int)Math.round(r*Math.cos(a)),76+i/6,(int)Math.round(r*Math.sin(a))));
        }
        b.path(route,1,Material.SPRUCE_PLANKS);
        Point last=route.getLast();
        List<Point> approach=new ArrayList<>();
        for(int i=0;i<=40;i++){
            double t=i/40.0;approach.add(new Point((int)Math.round(last.x()*(1-t)),160,(int)Math.round(last.z()*(1-t))));
        }
        b.path(approach,1,Material.SMOOTH_QUARTZ);
        // Connect the lower arrival platform to the first spiral tread.
        List<Point> entry=new ArrayList<>();for(int x=0;x<=14;x++)entry.add(new Point(x,76,0));
        b.path(entry,1,Material.SMOOTH_QUARTZ);
        for(int i=18;i<490;i+=36){
            Point p=route.get(i);double r=Math.hypot(p.x(),p.z());
            int x=(int)Math.round(p.x()*(r+3)/r),z=(int)Math.round(p.z()*(r+3)/r);
            b.line(p.x(),p.y(),p.z(),x,p.y(),z,.65,Material.SPRUCE_PLANKS);
            b.put(x,p.y(),z,Material.SPRUCE_PLANKS);
            b.put(x,p.y()+1,z,Material.SPRUCE_FENCE);b.put(x,p.y()+2,z,Material.SPRUCE_FENCE);
            b.put(x,p.y()+3,z,Material.LANTERN);
            if(i%72==18)b.line(x,p.y()-1,z,x,p.y()-6,z,.6,Material.SPRUCE_LOG);
        }
        b.alcove(162,-5);
        return b.freeze();
    }
}
