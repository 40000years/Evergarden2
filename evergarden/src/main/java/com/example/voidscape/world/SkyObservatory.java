package com.example.voidscape.world;

import org.bukkit.Material;
import java.util.*;
import static com.example.voidscape.world.LandmarkBlueprint.*;

/** Reference's open ribbed celestial dome, brass telescope and waterfall terraces. */
public final class SkyObservatory {
    private SkyObservatory() {}
    private static final class Holder {static final LandmarkBlueprint VALUE=build();}
    public static LandmarkBlueprint blueprint(){return Holder.VALUE;}
    private static LandmarkBlueprint build(){
        LandmarkBlueprint b=new LandmarkBlueprint();
        b.island(0,120,-6,48,43,60);
        b.island(14,100,37,30,25,42);
        b.island(-36,108,20,18,24,35);
        b.rockSpire(-22,96,-8,6,50);b.rockSpire(20,99,-18,5,46);
        b.rockSpire(32,85,30,5,38);
        for(int y=121;y<=127;y++)b.disk(0,y,-15,30,Material.STONE_BRICKS);
        b.disk(0,128,-15,31,Material.SMOOTH_QUARTZ);
        b.ring(0,128,-15,28,1,Material.CHISELED_QUARTZ_BLOCK);
        b.ring(0,128,-15,23,.8,Material.WAXED_OXIDIZED_COPPER);
        b.disk(0,128,-15,16,Material.POLISHED_DIORITE);
        b.ring(0,128,-15,12,.8,Material.GOLD_BLOCK);
        for(int i=0;i<12;i++){
            double a=i*Math.PI/6;int x=(int)Math.round(Math.cos(a)*26),z=-15+(int)Math.round(Math.sin(a)*26);
            if(z>0)continue;
            b.box(x-2,129,z-2,x+2,131,z+2,Material.CHISELED_QUARTZ_BLOCK);
            b.box(x-1,132,z-1,x+1,153,z+1,Material.QUARTZ_PILLAR);
            b.box(x-2,152,z-2,x+2,154,z+2,Material.SMOOTH_QUARTZ);
            b.put(x,155,z,Material.SEA_LANTERN);
            if(i%2==0)b.garland(x+2,152,z+2,17,true);
        }
        for(int y=153;y<=155;y++)b.ring(0,y,-15,26,1.5,Material.SMOOTH_QUARTZ);
        // A deliberate opening faces the telescope; stained glass closes the rear shell.
        for(int x=-26;x<=26;x++)for(int z=-26;z<=26;z++)for(int y=156;y<=182;y++){
            double r=Math.sqrt(x*x/676.0+z*z/676.0+(y-155)*(y-155)/729.0);
            if(r<.94||r>1.03||z>5)continue;
            Material m=y%9==0&&Math.floorMod(x+z,9)==0?Material.SEA_LANTERN:
                    x<0?Material.BLUE_STAINED_GLASS:Material.PURPLE_STAINED_GLASS;
            b.put(x,y,z-15,m);
        }
        for(int rib=0;rib<=8;rib++){
            double a=Math.PI+rib*Math.PI/8;
            for(int step=0;step<=75;step++){
                double t=step/75.0*Math.PI/2;
                double r=26*Math.cos(t),y=155+28*Math.sin(t);
                b.ball(r*Math.cos(a),y,-15+r*Math.sin(a),1.25,1.25,1.25,Material.SMOOTH_QUARTZ);
            }
        }
        // Two exposed front ribs frame the open observatory instead of a closed dome.
        for(int side:new int[]{-1,1})for(int step=0;step<=48;step++){
            double t=step/70.0*Math.PI/2;
            b.ball(side*26*Math.cos(t),155+28*Math.sin(t),-12,1.4,1.4,1.4,Material.QUARTZ_BLOCK);
        }
        for(int x:new int[]{-18,-9,9,18}){
            int z=-15-(int)Math.sqrt(26*26-x*x);
            b.box(x-1,136,z,x+1,150,z,Material.PURPLE_WOOL);
            b.put(x,135,z,Material.GOLD_BLOCK);
            for(int y=138;y<=148;y+=5)b.put(x,y,z+1,Material.AMETHYST_BLOCK);
        }
        // Open front plaza and low parapets.
        b.box(-24,120,6,32,123,24,Material.STONE_BRICKS);
        b.box(-24,124,6,32,124,24,Material.SMOOTH_QUARTZ);
        for(int x=-22;x<=30;x+=7){b.lamp(x,125,24);b.put(x,124,23,Material.WAXED_OXIDIZED_COPPER);}
        telescope(b);
        for(int side:new int[]{-1,1}){
            int x=side*39;
            b.box(x-2,121,-3,x+2,139,1,Material.QUARTZ_PILLAR);
            b.box(x-3,139,-4,x+3,141,2,Material.CHISELED_QUARTZ_BLOCK);
            b.line(x,141,0,x-side*13,141,0,1.3,Material.SMOOTH_QUARTZ);
            b.garland(x,139,2,20,true);
        }
        for(int[] tree:new int[][]{{-35,121,-24},{34,121,-26},{-37,121,13},{35,101,40},{-1,101,50}})b.cherry(tree[0],tree[1],tree[2],10);
        for(int i=0;i<20;i++){
            double a=i*Math.PI*2/20;int x=(int)(46*Math.cos(a)),z=-6+(int)(40*Math.sin(a));
            if(b.at(x,120,z)!=Material.AIR)b.garland(x,120,z,12+i%5*4,i%3==0);
        }
        b.waterfall(30,119,25,73,2);b.waterfall(-23,119,30,65,1);
        b.waterfall(20,99,58,60,2);
        b.crystal(-20,72,-14,14);b.crystal(15,67,-4,12);
        // Low pools, garden edging and ruined archways on the approach terrace.
        for(int y=100;y<=101;y++)b.ring(27,y,41,6,1,Material.SMOOTH_QUARTZ);
        b.disk(27,100,41,4,Material.SEA_LANTERN);b.disk(27,101,41,4,Material.WATER);
        b.box(24,101,47,30,101,57,Material.LIGHT_BLUE_STAINED_GLASS);
        for(int x:new int[]{3,25}){
            b.box(x-1,101,50,x+1,110,52,Material.STONE_BRICKS);
            b.box(x-2,110,49,x+2,111,53,Material.CHISELED_QUARTZ_BLOCK);
        }
        for(int i=0;i<=30;i++){
            double a=i*Math.PI/30;
            b.ball(14+11*Math.cos(a),110+7*Math.sin(a),51,1.1,1.1,1.1,Material.SMOOTH_QUARTZ);
        }
        b.gardenDetails(100);b.gardenDetails(108);b.gardenDetails(120);
        List<Point> path=new ArrayList<>();
        for(int z=58;z>=35;z--)path.add(new Point(14,100+(58-z)/3,z));
        for(int x=14;x>=-13;x--)path.add(new Point(x,108+(14-x)/2,35));
        for(int z=35;z>=12;z--)path.add(new Point(-13,121+(35-z)*7/23,z));
        for(int z=12;z>=-20;z--)path.add(new Point(-13,128,z));
        b.path(path,2,Material.SMOOTH_QUARTZ);
        for(int z=40;z<=58;z+=6)b.lamp(18,101,z);
        b.alcove(129,-30);
        return b.freeze();
    }
    private static void telescope(LandmarkBlueprint b){
        b.disk(9,125,12,11,Material.CHISELED_QUARTZ_BLOCK);
        b.ring(9,126,12,10,.7,Material.WAXED_OXIDIZED_COPPER);
        b.box(5,126,8,13,129,16,Material.POLISHED_DEEPSLATE);
        b.box(7,130,10,11,140,14,Material.WAXED_CUT_COPPER);
        b.box(4,137,11,14,140,13,Material.GOLD_BLOCK);
        // Axis points upward and toward the right foreground. u,v span its circular barrel.
        double ax=-5,ay=139,az=1,dx=.70,dy=.43,dz=.57;
        double len=Math.sqrt(dx*dx+dy*dy+dz*dz);dx/=len;dy/=len;dz/=len;
        double ux=-dz,uz=dx,ul=Math.hypot(ux,uz);ux/=ul;uz/=ul;
        double vx=dy*uz,vy=dz*ux-dx*uz,vz=-dy*ux;
        for(int x=-14;x<=34;x++)for(int y=130;y<=164;y++)for(int z=-8;z<=30;z++){
            double px=x-ax,py=y-ay,pz=z-az,t=px*dx+py*dy+pz*dz;
            if(t<0||t>42)continue;
            double u=px*ux+pz*uz,v=px*vx+py*vy+pz*vz,r=Math.hypot(u,v);
            double radius=t<8?3.7:t<31?4.5:5.6;
            boolean band=t<2||Math.abs(t-9)<1||Math.abs(t-29)<1||t>39;
            if(r>radius+(band?.6:0))continue;
            if(t>40&&r<4.6)b.put(x,y,z,r<3.3?Material.LIGHT_BLUE_STAINED_GLASS:Material.SEA_LANTERN);
            else if(r>=radius-1.3)b.put(x,y,z,band?Material.GOLD_BLOCK:Material.WAXED_OXIDIZED_COPPER);
            else if(t>38)b.put(x,y,z,Material.SEA_LANTERN);
            else b.put(x,y,z,Material.AIR);
        }
    }
}
