package com.example.voidscape.world;

import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import static org.bukkit.Material.*;
import static org.bukkit.block.BlockFace.*;
import com.example.voidscape.world.ExplorationBlueprint.Point;
import com.example.voidscape.world.ExplorationBlueprint.Voxel;

/** Twenty individually planned destinations, with usable rooms and several exploration routes. */
final class MediumExplorationBlueprint {
    private static final Map<ExplorationLayout.Kind,List<Voxel>> CACHE=new ConcurrentHashMap<>();
    private MediumExplorationBlueprint(){}
    static List<Point> chests(ExplorationLayout.Kind kind){return switch(kind){
        case ORCHARD_ESTATE->points(-3,1,-12, 16,1,-16, -22,1,-18, 20,1,13, -11,1,12, 20,1,-10);
        case ROSE_CONSERVATORY->points(-18,1,-9, 18,1,-9, -18,1,14, 18,1,14, 2,1,-21);
        case GARDEN_WATERMILL->points(-5,1,-13, -6,6,-10, 22,1,-12, 24,1,7, -10,1,10);
        case TRAVELERS_INN->points(-9,1,-16, 12,3,-15, 18,3,-1, -15,3,5, 18,1,12, -24,1,10);
        case HEDGE_SANCTUARY->points(-18,1,5, 18,1,-5, -18,1,-17, 21,1,-11, 2,1,-23);
        case ALPINE_LODGE->points(-13,7,-12, 21,7,-12, -14,1,5, 11,1,10, 17,1,12);
        case FROZEN_BATHHOUSE->points(-12,1,-15, 14,4,-13, -18,1,8, 23,1,5, 0,1,-22);
        case ICEBOUND_OUTPOST->points(18,1,-13, 14,7,-12, 17,13,-17, -11,1,-12, -18,1,5, 18,1,8);
        case GLACIER_MINE->points(-20,1,-18, 21,1,-18, -9,-3,-7, 9,-3,-8, -22,1,10, 22,1,10);
        case AURORA_HALL->points(-23,1,-6, 23,1,-6, -2,4,-4, 2,1,-24, -18,1,12);
        case WOODLAND_MANOR->points(-17,1,-13, 12,1,-19, -3,-2,-19, 3,-2,-17, -20,1,12, 23,1,-1);
        case CANOPY_REFUGE->points(-10,9,-5, 20,9,3, -3,9,-11, 3,9,6, 18,1,11);
        case MUSHROOM_HAMLET->points(-10,1,-14, 18,1,-15, -6,1,8, 21,1,11, -23,1,11);
        case OVERGROWN_AQUEDUCT->points(-2,1,-14, 21,1,-15, -21,1,8, 21,1,12, -20,9,-9);
        case ROOTBOUND_SHRINE->points(-17,1,-12, 17,1,-12, -19,1,9, 19,1,9, 1,1,-22);
        case MOON_MARKET->points(-18,1,-12, 17,1,-15, -14,1,8, 23,1,8, -5,4,-20, 4,4,-20);
        case ASTRAL_MONASTERY->points(-11,1,-21, 20,9,-20, -18,1,6, 22,1,6, 3,1,-24);
        case TIDE_OBSERVATORY->points(-6,1,-9, 6,1,-7, -8,7,-13, 8,7,-12, -24,1,6, 24,1,6);
        case FALLEN_AMPHITHEATRE->points(-22,1,-13, 22,1,-13, -19,1,11, 19,1,11, 3,-2,-24);
        case LUNAR_BATH_GARDEN->points(-22,1,-13, 22,1,-13, -22,1,7, 22,1,7, 4,1,-25);
        default->throw new IllegalArgumentException("Not a medium destination: "+kind);
    };}
    private static List<Point> points(int... coordinates){
        var result=new ArrayList<Point>();for(int i=0;i<coordinates.length;i+=3)result.add(new Point(coordinates[i],coordinates[i+1],coordinates[i+2]));return List.copyOf(result);
    }
    static List<Voxel> blocks(ExplorationLayout.Kind kind){return CACHE.computeIfAbsent(kind,MediumExplorationBlueprint::build);}
    private static final class Builder {
        final Map<Point,Voxel> blocks=new LinkedHashMap<>();final List<Voxel> doors=new ArrayList<>(),doorFrames=new ArrayList<>();
        void put(int x,int y,int z,Material m){put(x,y,z,m,null,false);}
        void put(int x,int y,int z,Material m,BlockFace face){put(x,y,z,m,face,false);}
        void put(int x,int y,int z,Material m,BlockFace face,boolean upper){
            if(Math.abs(x)>30||Math.abs(z)>30||y< -6||y>42)throw new IllegalArgumentException("Medium bounds "+x+","+y+","+z);
            blocks.put(new Point(x,y,z),new Voxel(x,y,z,m,face,upper));
        }
        void box(int ax,int ay,int az,int bx,int by,int bz,Material m){
            if(ax>bx||ay>by||az>bz)throw new IllegalArgumentException("Inverted medium box: "+ax+","+ay+","+az+" -> "+bx+","+by+","+bz);
            for(int x=ax;x<=bx;x++)for(int z=az;z<=bz;z++)for(int y=ay;y<=by;y++)put(x,y,z,m);
        }
        void floor(int ax,int az,int bx,int bz,Material m){box(ax,0,az,bx,0,bz,m);}
        void layer(int ax,int y,int az,int bx,int bz,Material m){box(ax,y,az,bx,y,bz,m);}
        void circle(int cx,int y,int cz,double rx,double rz,Material m){
            for(int x=-(int)Math.ceil(rx);x<=rx;x++)for(int z=-(int)Math.ceil(rz);z<=rz;z++)if(x*x/(rx*rx)+z*z/(rz*rz)<=1)put(cx+x,y,cz+z,m);
        }
        void ring(int cx,int y,int cz,double rx,double rz,double width,Material m){
            for(int x=-(int)Math.ceil(rx+width);x<=rx+width;x++)for(int z=-(int)Math.ceil(rz+width);z<=rz+width;z++)
                if(Math.abs(Math.sqrt(x*x/(rx*rx)+z*z/(rz*rz))-1)*Math.min(rx,rz)<=width)put(cx+x,y,cz+z,m);
        }
        void beam(int ax,int ay,int az,int bx,int by,int bz,Material m){
            int steps=Math.max(1,Math.max(Math.abs(bx-ax),Math.max(Math.abs(by-ay),Math.abs(bz-az)))*3);
            BlockFace face=Math.abs(by-ay)>Math.max(Math.abs(bx-ax),Math.abs(bz-az))?UP:Math.abs(bx-ax)>Math.abs(bz-az)?EAST:NORTH;
            for(int i=0;i<=steps;i++){double t=i/(double)steps;put((int)Math.round(ax+(bx-ax)*t),(int)Math.round(ay+(by-ay)*t),(int)Math.round(az+(bz-az)*t),m,face);}
        }
        void post(int x,int z,int base,int top,Material m){put(x,0,z,STONE_BRICKS);box(x,base,z,x,top,z,m);}
        void path(int ax,int az,int bx,int bz,Material m){path(ax,az,bx,bz,0,m);}
        void path(int ax,int az,int bx,int bz,int y,Material m){
            int n=Math.max(1,Math.max(Math.abs(bx-ax),Math.abs(bz-az))*2);
            for(int i=0;i<=n;i++){double t=i/(double)n;int x=(int)Math.round(ax+(bx-ax)*t),z=(int)Math.round(az+(bz-az)*t);
                layer(x-1,y,z-1,x+1,z+1,m);box(x-1,y+1,z-1,x+1,y+3,z+1,AIR);}
        }
        void room(int ax,int az,int bx,int bz,int base,int height,Material wall,Material floor){
            layer(ax,base,az,bx,bz,floor);box(ax,base+1,az,bx,base+height,bz,wall);box(ax+1,base+1,az+1,bx-1,base+height,bz-1,AIR);
        }
        void timber(int ax,int az,int bx,int bz,int base,int top,Material log){
            for(int x:new int[]{ax,bx})for(int z:new int[]{az,bz})post(x,z,base+1,top,log);
            beam(ax,top,az,bx,top,az,log);beam(ax,top,bz,bx,top,bz,log);beam(ax,top,az,ax,top,bz,log);beam(bx,top,az,bx,top,bz,log);
        }
        void window(int x,int y,int z,BlockFace face,Material glass,Material sill){
            boolean alongX=face==NORTH||face==SOUTH;
            for(int i=-1;i<=1;i++){int dx=alongX?i:0,dz=alongX?0:i;
                put(x+dx,y,z+dz,glass);put(x+dx,y+1,z+dz,glass);put(x+dx,y-1,z+dz,sill,null,true);}
        }
        void door(int x,int y,int z,Material material,Material frame,BlockFace face){
            boolean alongX=face==NORTH||face==SOUTH;
            for(int i=-1;i<=1;i++)for(int h=0;h<=2;h++){
                int px=x+(alongX?i:0),pz=z+(alongX?0:i);put(px,y+h,pz,frame);
                doorFrames.add(new Voxel(px,y+h,pz,frame));
            }
            put(x,y,z,AIR);put(x,y+1,z,AIR);
            doors.add(new Voxel(x,y,z,material,face,false));doors.add(new Voxel(x,y+1,z,material,face,true));
        }
        void gable(int cx,int az,int half,int bz,int eave,Material stairs,Material slab,Material gable){
            for(int dx=-half-1;dx<=half+1;dx++){
                int y=eave+half+1-Math.abs(dx);
                for(int z=az-1;z<=bz+1;z++)put(cx+dx,y,z,dx==0?slab:stairs,dx<0?EAST:WEST);
                if(Math.abs(dx)<=half)for(int z:new int[]{az,bz})box(cx+dx,eave,z,cx+dx,y-1,z,gable);
                if(Math.abs(dx)==half+1)for(int z=az-1;z<=bz+1;z++)put(cx+dx,y-1,z,stairs,dx<0?WEST:EAST,true);
            }
        }
        void leanRoof(int ax,int az,int bx,int bz,int low,int every,Material wood,Material slab){
            for(int z=az;z<=bz;z++)for(int x=ax;x<=bx;x++){int y=low+(bz-z)/every;put(x,y-1,z,wood);put(x,y,z,slab);}
        }
        void gableFrame(int cx,int z,int half,int eave,Material log,Material glass,Material sill){
            beam(cx-half,eave,z,cx,eave+half,z,log);beam(cx+half,eave,z,cx,eave+half,z,log);
            beam(cx,eave,z,cx,eave+half,z,log);window(cx,eave+Math.max(2,half/3),z,SOUTH,glass,sill);
        }
        void stairs(int x,int base,int z,int rise,BlockFace face,int width,Material material){
            for(int step=0;step<=rise;step++)for(int side=0;side<width;side++){
                int px=x+face.getModX()*step+(face==NORTH||face==SOUTH?side:0),pz=z+face.getModZ()*step+(face==EAST||face==WEST?side:0);
                box(px,Math.min(0,base),pz,px,base+step,pz,STONE_BRICKS);put(px,base+step,pz,material,face);box(px,base+step+1,pz,px,base+step+3,pz,AIR);
            }
        }
        void arch(int cx,int z,int half,int spring,int rise,Material wall){
            for(int side:new int[]{-1,1}){floor(cx+side*half-1,z-1,cx+side*half+1,z+1,wall);box(cx+side*half,1,z-1,cx+side*half,spring,z+1,wall);}
            for(int dx=-half;dx<=half;dx++){int y=spring+rise-(int)Math.round(Math.abs(dx)*rise/(double)half);box(cx+dx,y,z-1,cx+dx,y+1,z+1,wall);}
        }
        void pool(int ax,int az,int bx,int bz,Material rim){
            box(ax,-1,az,bx,-1,bz,rim);floor(ax,az,bx,bz,rim);floor(ax+1,az+1,bx-1,bz-1,WATER);
        }
        void table(int x,int y,int z,Material fence,Material top){put(x,y,z,fence);put(x,y+1,z,top,null,true);}
        void bench(int ax,int y,int z,int bx,Material stairs,BlockFace face){for(int x=ax;x<=bx;x++)put(x,y,z,stairs,face);}
        void shelf(int ax,int y,int z,int bx,Material slab,Material... plants){
            for(int x=ax;x<=bx;x++){put(x,y,z,slab,null,true);if((x-ax)%2==0)put(x,y+1,z,plants[(x-ax)/2%plants.length]);}
        }
        void crates(int x,int z,Material wood){floor(x-1,z-1,x+2,z+1,wood);put(x,1,z,BARREL,SOUTH);put(x+1,1,z,wood);put(x,2,z,wood);put(x+1,2,z,OAK_TRAPDOOR,SOUTH);}
        void lamp(int x,int y,int z){put(x,y,z,LANTERN,UP);}
        void bed(int x,int y,int z,Material material){put(x,y,z,material,NORTH,false);put(x,y,z-1,material,NORTH,true);}
        void planter(int x,int z,Material rim,Material flower){floor(x-2,z-1,x+2,z+1,rim);floor(x-1,z,x+1,z,MOSS_BLOCK);put(x,1,z,flower);if(flower==ROSE_BUSH)put(x,2,z,flower,null,true);}
        void tree(int x,int z,int tall,Material log,Material leaves){
            circle(x,0,z,2,2,ROOTED_DIRT);box(x-1,1,z-1,x+1,tall,z+1,log);
            for(var face:List.of(NORTH,EAST,SOUTH,WEST))beam(x+face.getModX(),4,z+face.getModZ(),x+face.getModX()*6,0,z+face.getModZ()*6,log);
            for(int side:new int[]{-1,1}){beam(x,tall-3,z,x+side*5,tall-1,z+2,log);circle(x+side*4,tall,z+2,4,4,leaves);}
            for(int y=tall-1;y<=tall+3;y++)circle(x,y,z,6-Math.abs(y-tall-1),5-Math.abs(y-tall-1)*.6,leaves);
        }
        void tower(int cx,int cz){
            for(int y=0;y<=16;y++)circle(cx,y,cz,6.8,6.8,y%6==0?STONE_BRICKS:COBBLESTONE);
            for(int y=1;y<=15;y++)circle(cx,y,cz,5.3,5.3,AIR);
            for(int y:new int[]{0,12})circle(cx,y,cz,6,6,SPRUCE_PLANKS);
            for(int y:new int[]{5,11,16})ring(cx,y,cz,6.6,6.6,.5,CHISELED_STONE_BRICKS);
            for(int h=0;h<7;h++)circle(cx,17+h,cz,7.5-h,7.5-h,h==6?SPRUCE_SLAB:DEEPSLATE_TILES);
            window(cx-6,4,cz,WEST,LIGHT_BLUE_STAINED_GLASS_PANE,STONE_BRICK_SLAB);
            window(cx+6,4,cz,EAST,LIGHT_BLUE_STAINED_GLASS_PANE,STONE_BRICK_SLAB);
            box(cx-1,1,cz+4,cx+1,3,cz+7,AIR);door(cx,1,cz+5,SPRUCE_DOOR,STONE_BRICKS,SOUTH);
        }
        void mushroom(int cx,int cz,int radius,Material cap){
            for(int y=0;y<=5;y++)circle(cx,y,cz,radius,radius,y==0?OAK_PLANKS:MUSHROOM_STEM);
            for(int y=1;y<=5;y++)circle(cx,y,cz,radius-1.2,radius-1.2,AIR);
            for(int h=0;h<5;h++)circle(cx,6+h,cz,radius+2-h*.65,radius+2-h*.65,cap);
            box(cx-1,1,cz+radius-1,cx+1,3,cz+radius,AIR);door(cx,1,cz+radius,OAK_DOOR,STRIPPED_OAK_LOG,SOUTH);
            window(cx-radius,2,cz,WEST,GLASS_PANE,OAK_SLAB);window(cx+radius,2,cz,EAST,GLASS_PANE,OAK_SLAB);
            table(cx-2,1,cz-1,OAK_FENCE,OAK_SLAB);put(cx-2,3,cz-1,POTTED_RED_MUSHROOM);
            put(cx+3,1,cz,CRAFTING_TABLE);lamp(cx,4,cz+1);
            bed(cx+2,1,cz+2,cap==RED_MUSHROOM_BLOCK?RED_BED:WHITE_BED);
        }
        // Low roofs, open arcades and compact workrooms replace the former shared giant shells.
        void paving(int ax,int az,int bx,int bz,Material... palette){
            for(int x=ax;x<=bx;x++)for(int z=az;z<=bz;z++)put(x,0,z,palette[Math.floorMod(x*17+z*31+x*z, palette.length)]);
        }
        void hip(int ax,int az,int bx,int bz,int eave,Material tile,Material stairs,Material slab){
            for(int x=ax;x<=bx;x++)for(int z=az;z<=bz;z++){
                int d=Math.min(Math.min(x-ax,bx-x),Math.min(z-az,bz-z)),y=eave+d/2;
                if(d==0){BlockFace face=x==ax?EAST:x==bx?WEST:z==az?SOUTH:NORTH;put(x,y,z,stairs,face);}
                else if(d%2==0)put(x,y,z,slab);else put(x,y,z,tile);
            }
        }
        void awning(int ax,int az,int bx,int bz,int y,Material cloth,Material poles){
            awning(ax,az,bx,bz,0,y,cloth,poles);
        }
        void awning(int ax,int az,int bx,int bz,int base,int y,Material cloth,Material poles){
            Material deck=poles==QUARTZ_PILLAR?QUARTZ_BRICKS:poles==CHERRY_FENCE?CHERRY_PLANKS:poles==SPRUCE_FENCE?SPRUCE_PLANKS:COARSE_DIRT;
            for(int x=ax;x<=bx;x++)for(int z=az;z<=bz;z++){
                var old=blocks.get(new Point(x,base,z));if(old==null||old.material().isAir())put(x,base,z,deck);
            }
            for(int x:new int[]{ax,bx})for(int z:new int[]{az,bz})post(x,z,base+1,y-1,poles);
            for(int x=ax-1;x<=bx+1;x++)for(int z=az-1;z<=bz+1;z++)put(x,y+(x>ax&&x<bx?1:0),z,cloth);
            for(int x=ax;x<=bx;x+=3){put(x,y-1,az,LANTERN,UP);put(x,y-1,bz,LANTERN,UP);}
        }
        void cabinet(int x,int y,int z,Material wood,Material slab,BlockFace facing){
            put(x,y,z,BARREL,facing);put(x,y+1,z,slab,null,true);
            put(x-1,y,z,wood);put(x+1,y,z,wood);put(x-1,y+1,z,POTTED_FERN);put(x+1,y+1,z,DECORATED_POT);
        }
        void kitchen(int x,int y,int z,Material counter,Material shelf){
            for(int dx=-3;dx<=3;dx++)put(x+dx,y,z,counter);
            put(x-2,y,z,SMOKER,SOUTH);put(x,y,z,FURNACE,SOUTH);put(x+2,y,z,CAULDRON);
            put(x-3,y+1,z,DECORATED_POT);put(x+1,y+1,z,CAKE);put(x+3,y+1,z,POTTED_RED_MUSHROOM);
            shelf(x-3,y+3,z,x+3,shelf,FLOWER_POT,POTTED_BROWN_MUSHROOM);
        }
        void workshop(int x,int y,int z,Material wood,Material slab){
            put(x-2,y,z,CRAFTING_TABLE);put(x,y,z,SMITHING_TABLE);put(x+2,y,z,STONECUTTER,SOUTH);
            shelf(x-3,y+2,z,x+3,slab,POTTED_FERN,DECORATED_POT,FLOWER_POT);
            put(x+3,y,z,BARREL,SOUTH);put(x-3,y,z,wood);
        }
        void writingDesk(int x,int y,int z,Material wood,Material slab){
            put(x,y,z,LECTERN,SOUTH);put(x-1,y,z,wood);put(x+1,y,z,wood);
            put(x-1,y+1,z,FLOWER_POT);put(x+1,y+1,z,LANTERN);
            put(x,y,z+2,SPRUCE_STAIRS,NORTH);put(x-2,y,z,BOOKSHELF);
        }
        void dining(int x,int y,int z,int length,Material leg,Material top){
            for(int i=0;i<length;i++)table(x+i,y,z,leg,top);
            for(int i=0;i<length;i+=2){put(x+i,y,z-2,SPRUCE_STAIRS,SOUTH);put(x+i,y,z+2,SPRUCE_STAIRS,NORTH);}
            put(x,y+2,z,CAKE);put(x+length-1,y+2,z,FLOWER_POT);
        }
        void firepit(int x,int z,Material masonry){
            circle(x,0,z,3.5,3.5,masonry);put(x,1,z,CAMPFIRE);
            for(int dx:new int[]{-3,3}){put(x+dx,1,z,SPRUCE_STAIRS,dx<0?EAST:WEST);put(x+dx,1,z+1,SPRUCE_STAIRS,dx<0?EAST:WEST);}
        }
        void bollard(int x,int z,Material base){put(x,0,z,base);put(x,1,z,base);put(x,2,z,LANTERN);}
        void balustrade(int ax,int az,int bx,int bz,int y,Material material){
            beam(ax,y,az,bx,y,bz,material);
        }
        void ruinWall(int ax,int az,int bx,int bz,int height,Material... stone){
            int n=Math.max(Math.abs(bx-ax),Math.abs(bz-az));
            for(int i=0;i<=n;i++){int x=ax+(bx-ax)*i/Math.max(1,n),z=az+(bz-az)*i/Math.max(1,n);
                int top=Math.max(2,height-Math.floorMod(i*7+i*i,4));
                for(int y=0;y<=top;y++)put(x,y,z,stone[Math.floorMod(x*13+z*17+y*7,stone.length)]);
                put(x,top+1,z,STONE_BRICK_SLAB);}
        }
        void cropBed(int ax,int az,int bx,int bz,Material crop){
            floor(ax-1,az-1,bx+1,bz+1,OAK_LOG);floor(ax,az,bx,bz,FARMLAND);
            for(int x=ax;x<=bx;x++)for(int z=az;z<=bz;z++)put(x,1,z,crop);
        }
        void archAt(int x,int y,int z,int half,int spring,int rise,Material masonry){
            for(int side:new int[]{-1,1})box(x+side*half,y,z,x+side*half,y+spring,z,masonry);
            for(int dx=-half;dx<=half;dx++){int h=y+spring+rise-(int)Math.round(Math.abs(dx)*rise/(double)half);put(x+dx,h,z,masonry);}
            for(int side:new int[]{-1,1})put(x+side*half,y+spring,z,CHISELED_STONE_BRICKS);
        }
        void roundPool(int x,int y,int z,double rx,double rz,Material rim){
            circle(x,y-1,z,rx+1,rz+1,rim);circle(x,y,z,rx+1,rz+1,rim);
            circle(x,y,z,rx,rz,WATER);
        }
        void rock(int x,int z,int height,double radius,Material... stone){
            for(int y=0;y<=height;y++){
                double r=radius*Math.sqrt(Math.max(.025,1-y*y/(double)(height*height+1)));
                for(int dx=-(int)Math.ceil(r);dx<=r;dx++)for(int dz=-(int)Math.ceil(r);dz<=r;dz++){
                    double edge=r+.65*Math.sin(Math.atan2(dz,dx)*3+y/3.)+.35*Math.cos(dx+dz);
                    if(dx*dx+dz*dz<=edge*edge)put(x+dx+(y>height/2?1:0),y,z+dz,stone[Math.floorMod(dx*7+dz*13+y*11,stone.length)]);
                }
            }
        }
        void branchingTree(int x,int z,int height,Material log,Material leaves){
            for(int y=0;y<=height;y++){
                int r=y<4?3:y<height*2/3?2:1,shift=y>height/2?1:0;
                for(int dx=-r;dx<=r;dx++)for(int dz=-r;dz<=r;dz++)if(dx*dx+dz*dz<=r*r+1)put(x+dx+shift,y,z+dz,log);
            }
            for(int i=0;i<5;i++){
                double angle=i*Math.PI*2/5+.3;int dx=(int)Math.round(Math.cos(angle)*8),dz=(int)Math.round(Math.sin(angle)*8);
                beam(x,5,z,x+dx,0,z+dz,log);
                int base=height-7+i%3,end=height-2+i%3;
                beam(x,base,z,x+dx/2,base+2,z+dz/2,log);beam(x+dx/2,base+2,z+dz/2,x+dx,end,z+dz,log);
                for(int dy=-2;dy<=2;dy++)circle(x+dx,end+dy,z+dz,4.8-Math.abs(dy),4.2-Math.abs(dy)*.8,leaves);
            }
            for(int dy=-2;dy<=2;dy++)circle(x+1,height+dy,z,5-Math.abs(dy),4-Math.abs(dy)*.7,leaves);
        }

        List<Voxel> finish(ExplorationLayout.Kind kind){
            path(0,18,0,20,kind.biome()==BiomeTerrain.Kind.LUNAR_CITY?POLISHED_ANDESITE:COBBLESTONE);
            for(var v:doorFrames)blocks.put(new Point(v.x(),v.y(),v.z()),v);
            for(var v:doors)blocks.put(new Point(v.x(),v.y(),v.z()),v);
            for(var p:chests(kind)){put(p.x(),p.y()-1,p.z(),CHISELED_STONE_BRICKS);put(p.x(),p.y(),p.z(),CHEST,SOUTH);put(p.x(),p.y()+1,p.z(),AIR);put(p.x(),p.y()+2,p.z(),AIR);}
            for(var lamp:List.copyOf(blocks.values()))if(lamp.material()==LANTERN&&lamp.facing()==UP){
                int support=lamp.y()+1;while(support<=42){var v=blocks.get(new Point(lamp.x(),support,lamp.z()));if(v!=null&&v.material().isSolid())break;support++;}
                if(support<=42)for(int y=lamp.y()+1;y<support;y++)put(lamp.x(),y,lamp.z(),IRON_CHAIN);
                else{var below=blocks.get(new Point(lamp.x(),lamp.y()-1,lamp.z()));if(below!=null&&below.material().isSolid())put(lamp.x(),lamp.y(),lamp.z(),LANTERN);else blocks.remove(new Point(lamp.x(),lamp.y(),lamp.z()));}
            }
            return List.copyOf(blocks.values());
        }
    }
    private static List<Voxel> build(ExplorationLayout.Kind kind){
        var b=new Builder();switch(kind){
            case ORCHARD_ESTATE->orchard(b);case ROSE_CONSERVATORY->conservatory(b);case GARDEN_WATERMILL->watermill(b);
            case TRAVELERS_INN->inn(b);case HEDGE_SANCTUARY->hedges(b);
            case ALPINE_LODGE->alpine(b);case FROZEN_BATHHOUSE->frozenBath(b);case ICEBOUND_OUTPOST->outpost(b);
            case GLACIER_MINE->mine(b);case AURORA_HALL->aurora(b);
            case WOODLAND_MANOR->manor(b);case CANOPY_REFUGE->canopy(b);case MUSHROOM_HAMLET->mushrooms(b);
            case OVERGROWN_AQUEDUCT->aqueduct(b);case ROOTBOUND_SHRINE->shrine(b);
            case MOON_MARKET->market(b);case ASTRAL_MONASTERY->monastery(b);case TIDE_OBSERVATORY->observatory(b);
            case FALLEN_AMPHITHEATRE->theatre(b);case LUNAR_BATH_GARDEN->moonBath(b);
            default->throw new IllegalArgumentException("Not a medium destination "+kind);
        }return b.finish(kind);
    }
    /** A working orchard: pressing house, drying racks, irrigated nursery and packing yard. */
    private static void orchard(Builder b){
        b.paving(-13,-17,1,-4,STONE_BRICKS,MOSSY_STONE_BRICKS,ANDESITE);
        b.room(-12,-16,0,-5,0,4,WHITE_TERRACOTTA,OAK_PLANKS);b.timber(-12,-16,0,-5,0,4,STRIPPED_OAK_LOG);
        b.hip(-13,-17,1,-4,5,BRICKS,BRICK_STAIRS,BRICK_SLAB);
        b.door(-6,1,-5,OAK_DOOR,STRIPPED_OAK_LOG,SOUTH);b.window(-12,2,-11,WEST,GLASS_PANE,OAK_SLAB);b.window(0,2,-11,EAST,GLASS_PANE,OAK_SLAB);
        // Fruit press, washing counter and compact foreman's nook, all visible from the entrance.
        b.box(-10,1,-14,-8,2,-12,OAK_PLANKS);b.post(-9,-13,3,4,STRIPPED_OAK_LOG);b.put(-9,3,-13,IRON_BLOCK);
        b.kitchen(-5,1,-15,OAK_PLANKS,OAK_SLAB);b.put(-2,1,-9,CAULDRON);b.put(-2,1,-7,BARREL,WEST);
        b.writingDesk(-10,1,-8,OAK_PLANKS,OAK_SLAB);b.put(-6,1,-8,RED_CARPET);b.lamp(-6,3,-10);
        b.awning(7,-19,17,-10,4,WHITE_WOOL,OAK_FENCE);b.paving(6,-20,18,-9,COARSE_DIRT,ROOTED_DIRT,GRAVEL);
        for(int z:new int[]{-17,-13}){b.shelf(8,1,z,15,OAK_SLAB,DECORATED_POT,POTTED_RED_MUSHROOM);b.bench(8,1,z+2,12,OAK_STAIRS,NORTH);}
        b.crates(17,-11,OAK_PLANKS);b.put(8,1,-11,CRAFTING_TABLE);b.put(10,1,-10,COMPOSTER);
        for(int x:new int[]{-20,8,20})for(int z:new int[]{0,11}){
            b.tree(x,z,6+(x+z)%2,CHERRY_LOG,CHERRY_LEAVES);b.circle(x,0,z,3,3,ROOTED_DIRT);
            b.put(x-3,1,z+2,OAK_SLAB);b.put(x+2,1,z-2,COMPOSTER);
        }
        b.cropBed(-24,-15,-19,-9,CARROTS);b.cropBed(-24,-5,-19,-2,WHEAT);
        b.pool(3,-5,6,12,STONE_BRICKS);b.layer(2,1,5,7,7,OAK_SLAB); // footbridge over a sealed irrigation ditch
        b.path(0,18,0,9,GRAVEL);b.path(-20,8,20,8,GRAVEL);b.path(-6,8,-6,-4,GRAVEL);b.path(12,8,12,-9,GRAVEL);
        b.path(-17,8,-17,-18,GRAVEL);b.path(-17,-18,-22,-18,GRAVEL);b.path(20,8,20,-12,GRAVEL);
        b.paving(-14,7,-4,14,GRAVEL,COARSE_DIRT,GRAVEL);
        b.awning(-13,8,-5,13,4,YELLOW_WOOL,OAK_FENCE);b.dining(-11,1,10,4,OAK_FENCE,OAK_SLAB);
        b.crates(15,12,OAK_PLANKS);b.bollard(-3,14,STONE_BRICKS);b.bollard(3,14,STONE_BRICKS);
        for(int x=-23;x<=23;x+=3){if(Math.abs(x)>3)b.put(x,1,16,MOSSY_COBBLESTONE_WALL);}
    }
    /** Glass petals surrounding a living rose rotunda, with potting tables and an open pergola. */
    private static void conservatory(Builder b){
        b.circle(0,0,-6,12,12,STONE_BRICKS);b.ring(0,0,-6,9,9,.6,BRICKS);
        for(int y=1;y<=13;y++){
            double r=Math.sqrt(Math.max(0,144-(y-1)*(y-1)));
            if(r<1)continue;b.ring(0,y,-6,r,r,.55,y%4==1?WAXED_OXIDIZED_CUT_COPPER:GLASS);
        }
        for(int x:new int[]{-8,0,8}){b.post(x,-6+(int)Math.round(Math.sqrt(144-x*x)),1,5,WAXED_OXIDIZED_CUT_COPPER);b.post(x,-6-(int)Math.round(Math.sqrt(144-x*x)),1,5,WAXED_OXIDIZED_CUT_COPPER);}
        for(int side:new int[]{-1,1}){
            int x=side*18;b.room(x-5,-14,x+5,2,0,4,GLASS,STONE_BRICKS);
            b.timber(x-5,-14,x+5,2,0,4,WAXED_OXIDIZED_CUT_COPPER);b.hip(x-6,-15,x+6,3,5,GLASS,WAXED_OXIDIZED_CUT_COPPER_STAIRS,GLASS);
            b.box(side<0?-13:11,1,-8,side<0?-11:13,3,-4,AIR);
            for(int z:new int[]{-11,-1})b.shelf(x-3,1,z,x+3,OAK_SLAB,POTTED_ALLIUM,POTTED_WHITE_TULIP,POTTED_FERN);
            b.put(x+2,1,-6,COMPOSTER);b.put(x-2,1,-6,CAULDRON);b.put(x,1,-8,CRAFTING_TABLE);b.lamp(x,3,-4);
        }
        b.roundPool(0,0,-6,2.7,2.7,CHISELED_STONE_BRICKS);b.post(0,-6,1,3,QUARTZ_PILLAR);b.put(0,4,-6,DECORATED_POT);
        for(int x:new int[]{-6,6})for(int z:new int[]{-12,0}){b.planter(x,z,BRICKS,ROSE_BUSH);b.put(x-3,1,z,DECORATED_POT);}
        b.path(0,18,0,4,STONE_BRICKS);b.path(-18,7,18,7,STONE_BRICKS);b.path(-18,7,-18,3,STONE_BRICKS);b.path(18,7,18,3,STONE_BRICKS);
        b.box(-1,1,4,1,3,7,AIR);b.door(0,1,5,OAK_DOOR,WAXED_OXIDIZED_CUT_COPPER,SOUTH);
        b.box(-3,1,-19,3,4,-16,AIR);b.path(0,-16,0,-22,STONE_BRICKS);
        b.awning(-8,11,8,14,4,CHERRY_SLAB,CHERRY_FENCE);b.dining(-5,1,12,4,CHERRY_FENCE,CHERRY_SLAB);b.bench(3,1,12,6,CHERRY_STAIRS,NORTH);
        for(int x:new int[]{-19,19}){b.planter(x,11,BRICKS,ROSE_BUSH);b.crates(x,14,OAK_PLANKS);}
        b.path(-16,7,-16,14,STONE_BRICKS);b.path(16,7,16,14,STONE_BRICKS);
        b.path(-16,14,-18,14,STONE_BRICKS);b.path(16,14,18,14,STONE_BRICKS);
        b.shelf(-5,1,-22,5,OAK_SLAB,POTTED_FERN,POTTED_ALLIUM);b.put(-4,1,8,COMPOSTER);b.put(4,1,8,DECORATED_POT);
    }
    /** Compact mill machinery and a sluice fed from an arched watercourse, rather than a mansion. */
    private static void watermill(Builder b){
        b.room(-15,-15,-3,-4,0,4,WHITE_TERRACOTTA,OAK_PLANKS);b.timber(-15,-15,-3,-4,0,4,STRIPPED_OAK_LOG);
        b.layer(-15,5,-15,-3,-4,OAK_PLANKS);b.room(-15,-15,-3,-4,5,3,WHITE_TERRACOTTA,OAK_PLANKS);
        b.hip(-16,-16,-2,-3,9,BRICKS,BRICK_STAIRS,BRICK_SLAB);
        b.door(-9,1,-4,OAK_DOOR,STRIPPED_OAK_LOG,SOUTH);b.window(-15,2,-10,WEST,GLASS_PANE,OAK_SLAB);b.window(-3,7,-10,EAST,GLASS_PANE,OAK_SLAB);
        b.circle(-9,1,-11,2,2,SMOOTH_STONE);b.put(-9,2,-11,STONECUTTER,SOUTH);b.post(-9,-11,3,4,STRIPPED_OAK_LOG);
        b.beam(-14,4,-11,-4,4,-11,OAK_LOG);b.put(-13,1,-6,CRAFTING_TABLE);b.put(-5,1,-6,BARREL,WEST);
        b.shelf(-14,2,-14,-4,OAK_SLAB,DECORATED_POT,FLOWER_POT);b.lamp(-7,3,-8);
        b.stairs(-13,0,-6,5,NORTH,2,OAK_STAIRS);b.layer(-14,5,-14,-11,-11,OAK_PLANKS);
        b.put(-7,6,-13,HAY_BLOCK);b.put(-5,6,-13,HAY_BLOCK);b.cabinet(-6,6,-6,OAK_PLANKS,OAK_SLAB,SOUTH);b.put(-10,6,-6,HAY_BLOCK);
        // A narrow wheel, axle and visible paddles sit outside the actual grinding room.
        for(int dy=-5;dy<=5;dy++)for(int dz=-5;dz<=5;dz++)if(Math.abs(Math.hypot(dy,dz)-5)<.65)b.put(-2,6+dy,-10+dz,OAK_PLANKS);
        b.beam(-2,1,-10,-2,11,-10,OAK_LOG);b.beam(-2,6,-15,-2,6,-5,OAK_LOG);b.beam(-9,6,-10,0,6,-10,OAK_LOG);
        for(int dz=-4;dz<=4;dz+=2){b.put(-1,2+Math.abs(dz),-10+dz,OAK_TRAPDOOR,EAST);b.put(-3,2+Math.abs(dz),-10+dz,OAK_TRAPDOOR,WEST);}
        b.pool(1,-19,5,11,STONE_BRICKS);b.layer(0,1,4,6,6,OAK_SLAB);
        for(int x:new int[]{7,15,23})b.archAt(x,0,-19,4,3,2,STONE_BRICKS);
        b.box(6,5,-20,27,5,-18,STONE_BRICKS);b.box(6,6,-20,27,6,-20,STONE_BRICKS);b.box(6,6,-18,27,6,-18,STONE_BRICKS);
        b.box(6,6,-19,26,6,-19,WATER);b.put(5,6,-19,STONE_BRICKS);b.put(27,6,-19,STONE_BRICKS);
        b.awning(10,-13,23,-7,4,SPRUCE_SLAB,SPRUCE_FENCE);b.paving(9,-14,24,-6,GRAVEL,COARSE_DIRT,ANDESITE);
        b.workshop(16,1,-11,OAK_PLANKS,OAK_SLAB);b.crates(22,-8,OAK_PLANKS);b.put(11,1,-8,HAY_BLOCK);
        b.cropBed(11,2,20,6,WHEAT);b.crates(23,8,OAK_PLANKS);b.put(17,1,10,COMPOSTER);
        b.path(0,18,0,13,GRAVEL);b.path(-9,13,9,13,GRAVEL);b.path(-9,13,-9,-3,GRAVEL);
        b.path(9,13,9,-6,GRAVEL);b.path(9,9,25,9,GRAVEL);b.path(6,5,10,5,GRAVEL);
        b.bollard(-5,10,STONE_BRICKS);b.bollard(7,10,STONE_BRICKS);
    }
    /** A caravan waystation: covered wagons, one intimate tavern, bath and repair stops. */
    private static void inn(Builder b){
        b.room(-21,-19,-7,-9,0,4,WHITE_TERRACOTTA,OAK_PLANKS);b.timber(-21,-19,-7,-9,0,4,STRIPPED_OAK_LOG);
        b.hip(-22,-20,-6,-8,5,BRICKS,BRICK_STAIRS,BRICK_SLAB);b.door(-14,1,-9,OAK_DOOR,STRIPPED_OAK_LOG,SOUTH);
        b.window(-21,2,-14,WEST,GLASS_PANE,OAK_SLAB);b.window(-7,2,-14,EAST,GLASS_PANE,OAK_SLAB);
        b.kitchen(-14,1,-18,OAK_PLANKS,OAK_SLAB);b.dining(-18,1,-13,4,OAK_FENCE,OAK_SLAB);b.cabinet(-9,1,-11,OAK_PLANKS,OAK_SLAB,SOUTH);
        b.awning(-22,-7,-8,-3,4,WHITE_WOOL,OAK_FENCE);b.bench(-20,1,-5,-16,OAK_STAIRS,NORTH);b.table(-11,1,-5,OAK_FENCE,OAK_SLAB);
        // Every wagon has wheels, a tongue, curved canvas ribs and a furnished sleeping/storage deck.
        int wagon=0;for(int[] p:new int[][]{{11,-14},{17,0},{-16,6}}){int x=p[0],z=p[1];
            b.floor(x-4,z-6,x+4,z+5,COARSE_DIRT);b.layer(x-3,2,z-4,x+3,z+3,SPRUCE_PLANKS);
            for(int dx:new int[]{-4,4})for(int dz:new int[]{-3,2}){b.put(x+dx,1,z+dz,SPRUCE_LOG,EAST);b.put(x+dx,2,z+dz,SPRUCE_TRAPDOOR,dx<0?WEST:EAST);}
            for(int dx=-3;dx<=3;dx++)for(int dz=-4;dz<=3;dz++){
                int roof=5+(Math.abs(dx)<=1?1:0);b.put(x+dx,roof,z+dz,wagon==1?GREEN_WOOL:WHITE_WOOL);
                if(Math.abs(dx)==3)b.put(x+dx,3,z+dz,SPRUCE_TRAPDOOR,dx<0?WEST:EAST);
            }
            for(int dz:new int[]{-4,0,3})b.beam(x-3,5,z+dz,x+3,5,z+dz,SPRUCE_FENCE);
            b.box(x-1,3,z+3,x+1,4,z+4,AIR);b.stairs(x-1,0,z+6,2,NORTH,3,SPRUCE_STAIRS);b.layer(x-1,2,z+3,x+1,z+4,SPRUCE_PLANKS);
            b.bed(x-2,3,z,WHITE_BED);b.put(x+2,3,z-3,BARREL,SOUTH);b.put(x+2,3,z,CRAFTING_TABLE);b.put(x,4,z-4,LANTERN,UP);
            b.beam(x,1,z-5,x,1,z-6,SPRUCE_FENCE);wagon++;
        }
        b.path(0,18,0,-20,GRAVEL);b.path(-24,-1,9,-1,GRAVEL);b.path(-14,-1,-14,-8,GRAVEL);b.path(0,13,-16,13,GRAVEL);
        b.path(0,8,17,8,GRAVEL);b.path(0,-7,11,-7,GRAVEL);
        b.path(17,8,17,15,GRAVEL);b.path(-16,13,-24,13,GRAVEL);
        b.firepit(-7,5,COBBLESTONE);b.paving(6,9,20,15,GRAVEL,COARSE_DIRT,GRAVEL);
        b.awning(7,10,19,14,4,YELLOW_WOOL,OAK_FENCE);b.workshop(12,1,12,OAK_PLANKS,OAK_SLAB);
        b.paving(-25,-5,-22,0,COARSE_DIRT,GRAVEL,COARSE_DIRT);
        b.put(-24,1,-4,CAULDRON);b.put(-23,1,-4,COMPOSTER);b.put(4,1,-21,HAY_BLOCK);b.put(6,1,-21,BARREL,SOUTH);
        b.bollard(-3,15,STONE_BRICKS);b.bollard(3,15,STONE_BRICKS);b.crates(-24,11,OAK_PLANKS);
    }
    /** Real winding garden paths with five distinct destinations, terraces and a central sundial. */
    private static void hedges(Builder b){
        b.circle(0,0,-5,24,23,GRASS_BLOCK);
        b.path(0,18,0,8,GRAVEL);b.path(0,8,-11,8,GRAVEL);b.path(-11,8,-11,-9,GRAVEL);
        b.path(-11,-9,0,-9,GRAVEL);b.path(0,-9,0,-5,GRAVEL);b.path(0,-5,12,-5,GRAVEL);b.path(12,-5,12,-16,GRAVEL);
        b.path(12,-16,-20,-16,GRAVEL);b.path(-20,-16,-20,1,GRAVEL);b.path(-20,1,-16,1,GRAVEL);
        b.path(0,8,21,8,GRAVEL);b.path(21,8,21,-4,GRAVEL);b.path(21,-4,17,-4,GRAVEL);
        b.path(-11,5,-19,5,GRAVEL);b.path(12,-13,20,-13,GRAVEL);b.path(1,-16,1,-23,GRAVEL);
        // Tall hedges follow each corridor rather than a few arbitrary stripes on a paved slab.
        for(int[] r:new int[][]{{-15,-11,-15,10},{-8,-6,-8,4},{-22,-19,22,-19},{-18,-13,8,-13},{15,-16,15,5},{-7,11,18,11},{4,-10,4,4},{-22,3,-15,3}}){
            b.box(r[0],1,r[1],r[2],2,r[3],OAK_LEAVES);b.layer(r[0],3,r[1],r[2],r[3],FLOWERING_AZALEA_LEAVES);
        }
        b.circle(0,0,-4,4,4,STONE_BRICKS);b.put(0,1,-4,CHISELED_STONE_BRICKS);b.put(0,2,-4,QUARTZ_SLAB);b.put(0,3,-4,LIGHTNING_ROD,UP);
        b.circle(-18,0,6,4,3,BRICKS);b.bench(-21,1,7,-18,CHERRY_STAIRS,NORTH);b.table(-19,1,4,CHERRY_FENCE,CHERRY_SLAB);
        b.roundPool(19,0,-14,2,2,BRICKS);b.put(18,1,-17,DECORATED_POT);
        b.awning(-5,-25,5,-22,4,CHERRY_SLAB,CHERRY_FENCE);b.shelf(-4,1,-24,4,CHERRY_SLAB,POTTED_ALLIUM,POTTED_WHITE_TULIP);
        b.crates(18,-3,OAK_PLANKS);b.put(20,1,-1,COMPOSTER);b.put(-18,1,-13,DECORATED_POT);
        for(int[] p:new int[][]{{-4,14},{8,6},{-19,-8},{8,-23},{-23,10},{23,1}}){b.planter(p[0],p[1],BRICKS,ROSE_BUSH);}
        for(int[] p:new int[][]{{-8,9},{13,8},{-19,-17},{18,-8},{2,-21}})b.bollard(p[0],p[1],MOSSY_STONE_BRICKS);
    }

    /** A mountain pass shelter with a switchback trail, bridge, climbing supplies and an intimate bothy. */
    private static void alpine(Builder b){
        b.rock(-18,-13,9,8,STONE,ANDESITE,COBBLESTONE);b.rock(17,-12,12,9,STONE,ANDESITE,PACKED_ICE);
        b.layer(-24,6,-17,-11,-7,STONE_BRICKS);b.layer(10,6,-16,23,-7,STONE_BRICKS);
        b.box(-24,7,-17,-11,12,-7,AIR);b.box(10,7,-16,23,11,-7,AIR);
        b.room(-23,-16,-12,-8,6,4,SPRUCE_PLANKS,SPRUCE_PLANKS);b.timber(-23,-16,-12,-8,6,10,STRIPPED_SPRUCE_LOG);
        b.hip(-24,-17,-11,-7,11,DEEPSLATE_TILES,DEEPSLATE_TILE_STAIRS,DEEPSLATE_TILE_SLAB);
        b.door(-17,7,-8,SPRUCE_DOOR,STRIPPED_SPRUCE_LOG,SOUTH);b.window(-23,8,-12,WEST,GLASS_PANE,SPRUCE_SLAB);
        b.kitchen(-17,7,-15,SPRUCE_PLANKS,SPRUCE_SLAB);b.bed(-21,7,-10,WHITE_BED);b.bed(-19,7,-10,WHITE_BED);
        b.table(-14,7,-11,SPRUCE_FENCE,SPRUCE_SLAB);b.put(-14,9,-11,FLOWER_POT);b.lamp(-17,9,-11);
        b.box(-24,7,-15,-24,15,-15,STONE_BRICKS);b.put(-24,16,-15,CAMPFIRE);
        // Low, supported suspension footbridge; each landing connects to stairs and the shelter door.
        b.layer(-12,6,-12,11,-10,SPRUCE_PLANKS);
        for(int z:new int[]{-13,-9})b.beam(-12,7,z,11,7,z,SPRUCE_FENCE);
        for(int x:new int[]{-12,11})for(int z:new int[]{-13,-9}){b.post(x,z,7,10,STRIPPED_SPRUCE_LOG);b.lamp(x,9,z);}
        b.path(-17,-7,-17,-9,6,SPRUCE_PLANKS);b.path(-17,-10,-12,-10,6,SPRUCE_PLANKS);b.path(10,-11,21,-11,6,SPRUCE_PLANKS);
        b.awning(13,-16,21,-13,6,10,WHITE_WOOL,SPRUCE_FENCE);b.workshop(17,7,-14,SPRUCE_PLANKS,SPRUCE_SLAB);
        b.put(22,7,-9,BARREL,WEST);b.bench(13,7,-8,16,SPRUCE_STAIRS,NORTH);
        b.stairs(17,0,0,6,NORTH,3,STONE_BRICK_STAIRS);b.layer(16,6,-9,21,-6,STONE_BRICKS);
        b.paving(-17,4,-7,12,GRAVEL,COBBLESTONE,SNOW_BLOCK);b.awning(-17,4,-7,10,4,WHITE_WOOL,SPRUCE_FENCE);
        b.bed(-15,1,8,WHITE_BED);b.put(-9,1,5,BARREL,SOUTH);b.put(-9,1,8,CRAFTING_TABLE);b.lamp(-12,3,6);
        b.firepit(-1,7,STONE_BRICKS);b.crates(10,10,SPRUCE_PLANKS);
        b.path(0,18,0,13,GRAVEL);b.path(-12,13,17,13,GRAVEL);b.path(17,13,17,1,GRAVEL);b.path(-12,13,-12,11,GRAVEL);
        b.beam(-23,1,3,-21,4,3,SPRUCE_FENCE);b.beam(-21,4,3,-17,4,3,SPRUCE_FENCE);b.put(-20,3,3,LANTERN,UP);
        b.bollard(-4,15,STONE_BRICKS);b.bollard(4,15,STONE_BRICKS);
    }
    /** Terraced natural hot springs with steam vents, a round changing room and an open massage deck. */
    private static void frozenBath(Builder b){
        b.circle(-3,0,-5,22,20,GRAVEL);b.roundPool(-10,0,0,6.5,5,STONE_BRICKS);b.roundPool(10,0,4,5.5,4,STONE_BRICKS);
        b.circle(7,0,-14,9,6,STONE_BRICKS);b.circle(7,1,-14,8.5,5.5,STONE_BRICKS);b.circle(7,2,-14,8,5,STONE_BRICKS);
        b.roundPool(7,3,-14,5,3,SMOOTH_QUARTZ);b.stairs(16,0,-7,3,NORTH,3,STONE_BRICK_STAIRS);b.layer(13,3,-13,19,-10,STONE_BRICKS);
        for(int y=0;y<=5;y++)b.circle(-16,y,-15,6,6,y==0?SPRUCE_PLANKS:STONE_BRICKS);
        for(int y=1;y<=5;y++)b.circle(-16,y,-15,4.8,4.8,AIR);
        for(int h=0;h<4;h++)b.circle(-16,6+h,-15,7-h*1.5,7-h*1.5,SPRUCE_SLAB);
        b.box(-17,1,-10,-15,3,-8,AIR);b.door(-16,1,-10,SPRUCE_DOOR,STONE_BRICKS,SOUTH);
        b.bench(-19,1,-18,-13,SPRUCE_STAIRS,SOUTH);b.put(-19,1,-13,CAULDRON);b.put(-13,1,-13,BARREL,WEST);
        b.shelf(-19,2,-19,-13,SPRUCE_SLAB,FLOWER_POT,POTTED_FERN);b.lamp(-16,4,-15);
        b.paving(-23,5,-16,12,SPRUCE_PLANKS,SPRUCE_PLANKS,STRIPPED_SPRUCE_LOG);b.awning(-23,5,-17,11,4,WHITE_WOOL,SPRUCE_FENCE);
        b.bench(-22,1,7,-18,SPRUCE_STAIRS,NORTH);b.put(-22,1,10,CAULDRON);b.put(-18,1,10,DECORATED_POT);
        b.paving(19,-2,25,10,STONE_BRICKS,ANDESITE,GRAVEL);b.awning(20,0,25,8,4,SPRUCE_SLAB,SPRUCE_FENCE);
        b.shelf(20,1,6,24,SPRUCE_SLAB,POTTED_FERN,DECORATED_POT);b.bench(20,1,2,24,SPRUCE_STAIRS,SOUTH);
        for(int[] p:new int[][]{{-7,-13},{-2,3},{14,-4}}){b.circle(p[0],0,p[1],2,2,COBBLESTONE);b.put(p[0],0,p[1],CAMPFIRE);b.put(p[0],1,p[1],IRON_BARS);}
        b.path(0,18,0,12,GRAVEL);b.path(-20,13,22,13,GRAVEL);b.path(-20,13,-20,4,GRAVEL);b.path(22,13,22,9,GRAVEL);
        b.path(0,12,0,-5,GRAVEL);b.path(0,-5,-16,-5,GRAVEL);b.path(-16,-5,-16,-9,GRAVEL);b.path(0,-5,17,-5,GRAVEL);
        b.layer(13,3,-11,17,-10,STONE_BRICKS);b.put(14,4,-10,DECORATED_POT);b.put(17,4,-13,LANTERN);
        b.bollard(-4,13,STONE_BRICKS);b.bollard(4,13,STONE_BRICKS);
    }
    /** An asymmetrical fortified gate with one climbable, roofless watchtower and a busy garrison yard. */
    private static void outpost(Builder b){
        b.paving(-22,-21,22,12,STONE_BRICKS,COBBLESTONE,ANDESITE);
        b.ruinWall(-23,-21,23,-21,5,STONE_BRICKS,CRACKED_STONE_BRICKS,COBBLESTONE);
        b.ruinWall(-23,-21,-23,9,5,STONE_BRICKS,COBBLESTONE);b.ruinWall(23,-21,23,9,4,STONE_BRICKS,COBBLESTONE);
        b.box(-22,1,12,-5,4,12,STONE_BRICKS);b.box(5,1,12,22,4,12,STONE_BRICKS);b.archAt(0,0,12,5,4,2,STONE_BRICKS);
        for(int x:new int[]{-6,6}){b.post(x,12,1,6,CHISELED_STONE_BRICKS);b.put(x,7,12,LANTERN);}
        // Square tower has three occupied landings and an accessible crenellated fighting platform.
        b.room(9,-19,21,-7,0,12,STONE_BRICKS,SPRUCE_PLANKS);b.layer(10,6,-18,20,-8,SPRUCE_PLANKS);b.layer(9,12,-19,21,-7,STONE_BRICKS);
        for(int x=9;x<=21;x++)for(int z:new int[]{-19,-7}){b.put(x,13,z,STONE_BRICK_WALL);if(x%3==0)b.put(x,14,z,STONE_BRICKS);}
        for(int z=-18;z< -7;z++)for(int x:new int[]{9,21})b.put(x,13,z,STONE_BRICK_WALL);
        b.door(15,1,-7,SPRUCE_DOOR,STONE_BRICKS,SOUTH);b.window(21,3,-13,EAST,GLASS_PANE,STONE_BRICK_SLAB);
        b.stairs(10,0,-9,6,NORTH,2,STONE_BRICK_STAIRS);b.layer(10,6,-18,18,-15,SPRUCE_PLANKS);
        b.stairs(19,6,-16,6,SOUTH,2,STONE_BRICK_STAIRS);b.layer(17,12,-10,20,-8,STONE_BRICKS);
        b.put(17,1,-17,CARTOGRAPHY_TABLE);b.cabinet(17,1,-9,SPRUCE_PLANKS,SPRUCE_SLAB,SOUTH);b.lamp(17,4,-13);
        b.writingDesk(15,7,-17,SPRUCE_PLANKS,SPRUCE_SLAB);b.put(16,7,-11,BARREL,SOUTH);b.lamp(14,9,-12);
        b.table(15,13,-13,SPRUCE_FENCE,SPRUCE_SLAB);b.put(15,15,-13,LANTERN);b.put(18,13,-17,FLETCHING_TABLE);
        for(int z:new int[]{-17,-9})for(int x:new int[]{8,22}){
            b.box(x,0,z,x,7,z,COBBLESTONE);b.put(x,8,z,STONE_BRICK_STAIRS,x==8?EAST:WEST);
            b.put(x,1,z-1,STONE_BRICK_STAIRS,z== -17?SOUTH:NORTH);b.put(x,1,z+1,STONE_BRICK_STAIRS,z== -17?NORTH:SOUTH);
        }
        for(int x:new int[]{11,19})for(int z:new int[]{-20,-6}){
            b.box(x,0,z,x,7,z,COBBLESTONE);b.put(x,8,z,STONE_BRICK_STAIRS,z== -20?SOUTH:NORTH);
        }
        b.window(9,9,-13,WEST,GLASS_PANE,STONE_BRICK_SLAB);b.window(21,9,-13,EAST,GLASS_PANE,STONE_BRICK_SLAB);
        b.window(15,9,-19,NORTH,GLASS_PANE,STONE_BRICK_SLAB);b.window(15,9,-7,SOUTH,GLASS_PANE,STONE_BRICK_SLAB);
        b.put(10,9,-6,BLUE_WALL_BANNER,SOUTH);b.put(20,9,-6,WHITE_WALL_BANNER,SOUTH);
        b.room(-21,-18,-10,-9,0,4,SPRUCE_PLANKS,SPRUCE_PLANKS);b.timber(-21,-18,-10,-9,0,4,STRIPPED_SPRUCE_LOG);
        b.hip(-22,-19,-9,-8,5,DEEPSLATE_TILES,DEEPSLATE_TILE_STAIRS,DEEPSLATE_TILE_SLAB);b.door(-15,1,-9,SPRUCE_DOOR,STRIPPED_SPRUCE_LOG,SOUTH);
        for(int x:new int[]{-19,-16,-13})b.bed(x,1,-14,WHITE_BED);b.kitchen(-16,1,-17,SPRUCE_PLANKS,SPRUCE_SLAB);b.lamp(-16,3,-12);
        b.window(-21,2,-13,WEST,GLASS_PANE,SPRUCE_SLAB);b.window(-10,2,-15,EAST,GLASS_PANE,SPRUCE_SLAB);
        for(int x:new int[]{-6,0,6}){b.put(x,1,-19,HAY_BLOCK);b.put(x,2,-19,TARGET);b.put(x,3,-19,SPRUCE_SLAB);}
        b.crates(20,-1,SPRUCE_PLANKS);b.put(18,1,1,ANVIL,SOUTH);b.put(-6,1,4,GRINDSTONE,SOUTH);
        b.beam(-8,3,1,-8,3,7,STRIPPED_SPRUCE_LOG);b.post(-8,1,1,3,SPRUCE_FENCE);b.post(-8,7,1,3,SPRUCE_FENCE);
        b.put(-8,2,3,BLUE_WALL_BANNER,EAST);b.put(-8,2,5,WHITE_WALL_BANNER,EAST);
        b.awning(-20,1,-10,7,4,SPRUCE_SLAB,SPRUCE_FENCE);b.workshop(-15,1,3,SPRUCE_PLANKS,SPRUCE_SLAB);
        b.crates(17,6,SPRUCE_PLANKS);b.put(18,1,3,HAY_BLOCK);b.firepit(0,-14,STONE_BRICKS);
        b.path(0,18,0,13,STONE_BRICKS);b.path(0,11,0,-7,STONE_BRICKS);b.path(-15,-6,15,-6,STONE_BRICKS);b.path(-15,-6,-15,-8,STONE_BRICKS);
        b.path(15,-6,15,-6,STONE_BRICKS);b.path(-15,8,17,8,STONE_BRICKS);
        b.awning(5,-3,12,3,4,WHITE_WOOL,SPRUCE_FENCE);b.put(8,1,-1,CARTOGRAPHY_TABLE);
        b.table(10,1,-1,SPRUCE_FENCE,SPRUCE_SLAB);b.bench(7,1,1,10,SPRUCE_STAIRS,NORTH);
        b.put(6,1,-1,BARREL,EAST);b.put(10,3,-1,FLOWER_POT);
    }
    /** Open, stepped quarry with a working crane, mine galleries, rails and ore sorting benches. */
    private static void mine(Builder b){
        b.paving(-25,-21,25,13,STONE,ANDESITE,GRAVEL);
        b.box(-13,-3,-15,12,1,2,AIR);b.layer(-13,-4,-15,12,2,STONE);
        b.box(-17,-1,-18,16,0,5,AIR);b.layer(-17,-2,-18,16,5,ANDESITE);
        b.box(-12,-3,-14,11,-2,1,AIR);b.layer(-12,-4,-14,11,1,STONE);
        b.stairs(-2,-4,1,4,SOUTH,4,STONE_STAIRS);b.layer(-3,0,5,3,7,ANDESITE);
        for(int x:new int[]{-11,-5,5,10}){b.put(x,-3,-12,x<0?IRON_ORE:COAL_ORE);b.put(x,-3,-11,STONE);}
        for(int x=-10;x<=9;x++)b.put(x,-3,-5,RAIL,EAST);
        b.put(-8,-3,-6,BARREL,SOUTH);b.put(7,-3,-6,CRAFTING_TABLE);b.put(9,-3,-10,BLAST_FURNACE,SOUTH);
        for(int x:new int[]{-19,20}){
            b.rock(x,-17,12,8,STONE,ANDESITE,PACKED_ICE);
            b.box(x-2,1,-23,x+2,4,-9,AIR);b.floor(x-2,-23,x+2,-9,SPRUCE_PLANKS);
            for(int z:new int[]{-20,-15,-10}){for(int dx:new int[]{-2,2})b.post(x+dx,z,1,4,STRIPPED_SPRUCE_LOG);b.beam(x-2,4,z,x+2,4,z,STRIPPED_SPRUCE_LOG);b.lamp(x,3,z);}
            for(int z=-22;z<= -9;z++)b.put(x,1,z,RAIL,NORTH);
            b.put(x-1,1,-21,BARREL,SOUTH);b.put(x+1,1,-19,IRON_ORE);
        }
        b.post(-11,9,1,11,STRIPPED_SPRUCE_LOG);b.beam(-11,11,9,2,11,9,STRIPPED_SPRUCE_LOG);
        b.beam(-11,6,9,-6,11,9,SPRUCE_FENCE);b.box(1,5,9,1,10,9,IRON_CHAIN);b.put(1,4,9,CAULDRON);
        b.box(-11,1,8,-11,3,10,STONE_BRICKS);b.put(-10,1,10,GRINDSTONE,SOUTH);
        b.awning(9,7,23,12,5,SPRUCE_SLAB,SPRUCE_FENCE);b.workshop(16,1,9,SPRUCE_PLANKS,SPRUCE_SLAB);
        b.put(13,1,11,BLAST_FURNACE,SOUTH);b.put(20,1,11,BARREL,SOUTH);b.crates(-21,9,SPRUCE_PLANKS);
        b.path(0,18,0,14,GRAVEL);b.path(-22,14,22,14,GRAVEL);b.path(-22,14,-22,-8,GRAVEL);b.path(22,14,22,-8,GRAVEL);
        b.path(-22,-8,-19,-8,GRAVEL);b.path(20,-8,22,-8,GRAVEL);
        b.bollard(-4,12,STONE_BRICKS);b.bollard(5,12,STONE_BRICKS);
    }
    /** An outdoor aurora sanctuary: radial standing stones, a raised altar and weathered expedition alcoves. */
    private static void aurora(Builder b){
        b.circle(0,0,-6,19,19,STONE_BRICKS);b.ring(0,0,-6,14,14,.7,CHISELED_STONE_BRICKS);b.ring(0,0,-6,8,8,.6,POLISHED_ANDESITE);
        for(int i=0;i<9;i++){
            double a=Math.PI*2*i/9;int x=(int)Math.round(Math.cos(a)*17),z=-6+(int)Math.round(Math.sin(a)*17);
            int h=5+i%3*2;b.box(x-1,1,z-1,x+1,h,z+1,PACKED_ICE);b.post(x,z,1,h+1,CHISELED_STONE_BRICKS);
            b.put(x,h+2,z,LANTERN);b.put(x-1,h+1,z,BLUE_ICE);b.put(x+1,h+1,z,BLUE_ICE);
            b.path(0,-6,x,z,STONE_BRICKS);
        }
        b.circle(0,1,-6,5,5,CHISELED_STONE_BRICKS);b.circle(0,2,-6,4,4,STONE_BRICKS);b.circle(0,3,-6,3,3,SMOOTH_QUARTZ);
        b.stairs(-1,0,1,3,NORTH,3,STONE_BRICK_STAIRS);b.layer(-1,3,-4,1,-2,SMOOTH_QUARTZ);
        b.put(0,4,-7,LODESTONE);b.put(0,5,-7,AMETHYST_CLUSTER,UP);
        for(int x:new int[]{-2,2}){b.put(x,4,-6,QUARTZ_PILLAR);b.put(x,5,-6,LANTERN);}
        for(int side:new int[]{-1,1}){int x=side*22;
            b.paving(x-4,-13,x+4,2,STONE_BRICKS,CRACKED_STONE_BRICKS,GRAVEL);b.ruinWall(x-4,-13,x+4,-13,5,STONE_BRICKS,CRACKED_STONE_BRICKS);
            b.awning(x-3,-10,x+3,-4,4,WHITE_WOOL,SPRUCE_FENCE);b.bed(x-2,1,-6,WHITE_BED);b.put(x+2,1,-9,BARREL,SOUTH);
            b.shelf(x-2,1,0,x+2,STONE_BRICK_SLAB,DECORATED_POT,POTTED_FERN);b.put(x,1,-2,CARTOGRAPHY_TABLE);
            b.path(side*13,5,x,5,GRAVEL);b.path(x,5,x,1,GRAVEL);
        }
        b.path(0,18,0,13,STONE_BRICKS);b.path(-22,13,22,13,STONE_BRICKS);
        b.firepit(-17,11,STONE_BRICKS);b.crates(18,12,SPRUCE_PLANKS);b.put(-3,1,-23,DECORATED_POT);b.put(3,1,-23,DECORATED_POT);
        b.archAt(0,0,14,4,4,3,CHISELED_STONE_BRICKS);b.put(0,8,14,BLUE_ICE);
    }

    /** A collapsed estate reclaimed by the forest: banquet ruin, glasshouse remains and a sunken cellar. */
    private static void manor(Builder b){
        b.paving(-22,-23,18,-5,MOSSY_STONE_BRICKS,CRACKED_STONE_BRICKS,STONE_BRICKS);
        b.paving(-22,-4,-11,10,MOSSY_STONE_BRICKS,STONE_BRICKS,MOSS_BLOCK);
        b.ruinWall(-22,-23,18,-23,7,STONE_BRICKS,MOSSY_STONE_BRICKS,CRACKED_STONE_BRICKS);
        b.ruinWall(-22,-23,-22,10,7,STONE_BRICKS,MOSSY_STONE_BRICKS);b.ruinWall(18,-23,18,-8,6,STONE_BRICKS,CRACKED_STONE_BRICKS);
        b.ruinWall(-21,-5,-11,-5,5,MOSSY_STONE_BRICKS,CRACKED_STONE_BRICKS);
        for(int x:new int[]{-16,-8,4,12})b.archAt(x,0,-22,3,3,2,MOSSY_STONE_BRICKS);
        b.ruinWall(-11,-21,-11,-12,5,STONE_BRICKS,MOSSY_STONE_BRICKS);b.box(-12,1,-17,-10,3,-15,AIR);
        b.kitchen(-17,1,-20,SPRUCE_PLANKS,SPRUCE_SLAB);b.put(-19,1,-9,CAULDRON);b.put(-16,1,-9,SMOKER,SOUTH);
        b.dining(-6,1,-16,9,DARK_OAK_FENCE,DARK_OAK_SLAB);b.put(-1,1,-14,MOSS_CARPET);b.put(6,1,-18,BOOKSHELF);
        b.writingDesk(13,1,-20,DARK_OAK_PLANKS,DARK_OAK_SLAB);b.put(15,1,-11,DECORATED_POT);
        b.beam(-21,6,-22,-16,4,-15,STRIPPED_DARK_OAK_LOG);b.beam(-21,5,-12,-14,2,-7,STRIPPED_DARK_OAK_LOG);
        for(int x=-21;x<= -14;x++)for(int z=-22;z<= -18;z++)b.put(x,7,z,DEEPSLATE_TILE_SLAB);
        for(int x:new int[]{-21,-14,7,17}){b.put(x,2,-22,VINE,SOUTH);b.put(x,3,-22,VINE,SOUTH);b.put(x,4,-22,VINE,SOUTH);}
        b.tree(-18,4,9,DARK_OAK_LOG,OAK_LEAVES);b.roundPool(11,0,6,5,4,MOSSY_STONE_BRICKS);
        b.paving(18,-6,26,4,MOSSY_STONE_BRICKS,MOSS_BLOCK,CRACKED_STONE_BRICKS);
        b.awning(19,-5,25,3,4,GLASS,STONE_BRICK_WALL);b.shelf(20,1,-3,24,OAK_SLAB,POTTED_FERN,POTTED_BROWN_MUSHROOM);
        b.put(20,1,1,COMPOSTER);b.put(24,1,1,CAULDRON);b.crates(-19,12,DARK_OAK_PLANKS);
        // The cellar sits below the collapsed central bay; the stair is the discovery route.
        b.room(-5,-24,5,-11,-3,4,MOSSY_STONE_BRICKS,SPRUCE_PLANKS);b.layer(-5,2,-24,5,-11,MOSSY_STONE_BRICKS);
        b.box(-1,-2,-12,1,3,-8,AIR);b.stairs(-1,-3,-11,3,SOUTH,3,STONE_BRICK_STAIRS);b.layer(-1,0,-8,1,-5,MOSSY_STONE_BRICKS);
        b.cabinet(-2,-2,-22,SPRUCE_PLANKS,SPRUCE_SLAB,SOUTH);b.put(3,-2,-14,BARREL,WEST);b.lamp(0,0,-17);
        b.path(0,18,0,-7,GRAVEL);b.path(0,-5,-17,-5,GRAVEL);b.path(0,11,22,11,GRAVEL);b.path(22,11,22,2,GRAVEL);
        b.path(-19,14,-19,10,GRAVEL);b.path(0,14,-19,14,GRAVEL);b.path(-9,-7,-9,-19,MOSSY_STONE_BRICKS);
        b.path(7,-7,15,-7,MOSSY_STONE_BRICKS);b.path(7,-7,7,-19,MOSSY_STONE_BRICKS);
        for(int[] p:new int[][]{{-25,-17},{-25,3},{23,10},{13,13}})b.planter(p[0],p[1],ROOTED_DIRT,FERN);
    }
    /** Open canopy decks, a tea shelter and branching oak crowns connected by walkable rope bridges. */
    private static void canopy(Builder b){
        b.branchingTree(-14,-12,21,DARK_OAK_LOG,OAK_LEAVES);b.branchingTree(15,-9,23,DARK_OAK_LOG,OAK_LEAVES);
        b.layer(-23,8,-9,-6,5,SPRUCE_PLANKS);b.layer(6,8,-6,23,7,SPRUCE_PLANKS);b.layer(-5,8,-16,5,-5,SPRUCE_PLANKS);
        for(int[] p:new int[][]{{-22,-8},{-7,4},{7,-5},{22,6},{-4,-15},{4,-6}}){
            b.post(p[0],p[1],1,8,DARK_OAK_LOG);b.beam(p[0],5,p[1],p[0]+(p[0]<0?2:-2),8,p[1],DARK_OAK_LOG);
        }
        for(int z:new int[]{-9,5})b.balustrade(-23,z,-6,z,9,SPRUCE_FENCE);
        for(int x:new int[]{-23,-6})b.balustrade(x,-9,x,5,9,SPRUCE_FENCE);
        for(int z:new int[]{-6,7})b.balustrade(6,z,23,z,9,SPRUCE_FENCE);
        for(int x:new int[]{6,23})b.balustrade(x,-6,x,7,9,SPRUCE_FENCE);
        b.awning(-22,-6,-13,0,8,12,SPRUCE_SLAB,SPRUCE_FENCE);b.dining(-20,9,-3,5,SPRUCE_FENCE,SPRUCE_SLAB);
        b.bed(-20,9,2,GREEN_BED);b.bed(-17,9,2,GREEN_BED);b.cabinet(-10,9,-7,SPRUCE_PLANKS,SPRUCE_SLAB,SOUTH);
        b.awning(12,-4,21,1,8,12,GREEN_WOOL,SPRUCE_FENCE);b.workshop(17,9,-2,SPRUCE_PLANKS,SPRUCE_SLAB);
        b.put(9,9,5,CAULDRON);b.put(12,9,5,COMPOSTER);b.shelf(15,9,5,21,SPRUCE_SLAB,POTTED_FERN,POTTED_BROWN_MUSHROOM);
        b.writingDesk(0,9,-14,SPRUCE_PLANKS,SPRUCE_SLAB);b.shelf(-3,9,-7,3,SPRUCE_SLAB,POTTED_FERN,FLOWER_POT);
        b.layer(-6,8,1,6,3,SPRUCE_PLANKS);b.layer(-1,8,-5,1,8,SPRUCE_PLANKS);
        for(int z:new int[]{0,4})b.balustrade(-6,z,6,z,9,SPRUCE_FENCE);
        for(int x:new int[]{-2,2})b.balustrade(x,-5,x,0,9,SPRUCE_FENCE);
        b.box(-7,9,1,-5,11,3,AIR);b.box(5,9,1,7,11,3,AIR);b.box(-1,9,0,1,11,4,AIR);
        b.stairs(-1,0,16,8,NORTH,3,SPRUCE_STAIRS);b.layer(-4,8,5,4,8,SPRUCE_PLANKS);
        b.path(0,18,0,17,GRAVEL);b.path(3,18,18,18,GRAVEL);b.path(18,18,18,10,GRAVEL);b.crates(18,12,SPRUCE_PLANKS);
        b.lamp(-11,11,-7);b.lamp(10,11,4);b.lamp(0,11,-10);
        for(int x:new int[]{-5,5}){b.post(x,2,9,12,SPRUCE_FENCE);b.put(x,13,2,LANTERN);}
    }
    /** One inhabited mushroom bakery, wild fungi, a hollow fallen log and a busy gathering camp. */
    private static void mushrooms(Builder b){
        b.mushroom(-13,-13,5,RED_MUSHROOM_BLOCK);b.kitchen(-13,1,-16,OAK_PLANKS,OAK_SLAB);
        // Long fallen trunk: open ends and a walkable interior, with fungi growing on its weathered shell.
        b.floor(1,-20,23,-10,MOSS_BLOCK);
        for(int x=2;x<=22;x++)for(int dy=-4;dy<=4;dy++)for(int dz=-4;dz<=4;dz++){
            double r=Math.hypot(dy,dz);if(r>=3.2&&r<=4.3)b.put(x,4+dy,-15+dz,DARK_OAK_LOG,EAST);
        }
        b.box(1,1,-17,24,5,-13,AIR);b.layer(1,0,-17,24,-13,SPRUCE_PLANKS);
        b.shelf(6,1,-17,16,SPRUCE_SLAB,POTTED_BROWN_MUSHROOM,POTTED_RED_MUSHROOM);b.put(20,1,-16,BARREL,WEST);
        for(int x:new int[]{5,13,21}){b.lamp(x,5,-15);b.put(x,9,-15,BROWN_MUSHROOM_BLOCK);b.put(x+1,9,-15,BROWN_MUSHROOM_BLOCK);}
        for(int[] p:new int[][]{{-22,3},{16,5},{-2,-25}}){int x=p[0],z=p[1];
            b.post(x,z,1,6,MUSHROOM_STEM);for(int h=0;h<3;h++)b.circle(x,7+h,z,5-h,4-h*.5,h==2?RED_MUSHROOM_BLOCK:BROWN_MUSHROOM_BLOCK);
            b.circle(x,0,z,4,4,ROOTED_DIRT);b.put(x-2,1,z,POTTED_RED_MUSHROOM);
        }
        b.awning(-15,4,-5,11,4,BROWN_MUSHROOM_BLOCK,OAK_FENCE);b.paving(-16,3,-4,12,COARSE_DIRT,MOSS_BLOCK,ROOTED_DIRT);
        b.shelf(-14,1,6,-6,OAK_SLAB,POTTED_RED_MUSHROOM,POTTED_BROWN_MUSHROOM);b.put(-7,1,10,CAULDRON);b.put(-13,1,10,COMPOSTER);
        b.awning(7,7,22,12,4,GREEN_WOOL,OAK_FENCE);b.dining(10,1,10,5,OAK_FENCE,OAK_SLAB);b.put(20,1,9,SMOKER,SOUTH);
        b.firepit(-1,3,MOSSY_COBBLESTONE);b.cropBed(-25,-9,-22,-3,CARROTS);
        b.path(0,18,0,12,GRAVEL);b.path(-18,13,20,13,GRAVEL);b.path(-18,13,-18,-6,GRAVEL);b.path(-18,-6,-13,-6,GRAVEL);
        b.path(20,13,25,13,GRAVEL);b.path(25,13,25,-15,GRAVEL);b.path(22,-15,25,-15,GRAVEL);b.path(0,12,0,-15,GRAVEL);b.path(0,-15,2,-15,GRAVEL);
        b.crates(-21,11,OAK_PLANKS);b.crates(23,3,OAK_PLANKS);b.bollard(-3,14,MOSSY_COBBLESTONE);b.bollard(3,14,MOSSY_COBBLESTONE);
    }
    /** A weathered seven-arch watercourse with a catchment basin, upper inspection walk and dig camp. */
    private static void aqueduct(Builder b){
        b.paving(-26,-16,26,-4,MOSSY_STONE_BRICKS,CRACKED_STONE_BRICKS,ANDESITE);
        for(int x=-21;x<=21;x+=7){
            b.archAt(x,0,-12,3,5,2,MOSSY_STONE_BRICKS);b.archAt(x,0,-10,3,5,2,STONE_BRICKS);
            for(int side:new int[]{-3,3}){b.box(x+side,0,-12,x+side,7,-10,MOSSY_STONE_BRICKS);b.put(x+side,4,-9,CHISELED_STONE_BRICKS);b.put(x+side,8,-9,STONE_BRICK_SLAB);}
            b.put(x-2,5,-9,VINE,SOUTH);b.put(x+2,6,-9,VINE,SOUTH);
        }
        b.layer(-25,8,-13,25,-9,STONE_BRICKS);b.layer(-25,9,-13,25,-13,STONE_BRICKS);b.layer(-25,9,-11,25,-11,STONE_BRICKS);
        b.layer(-24,9,-12,24,-12,WATER);b.put(-25,9,-12,STONE_BRICKS);b.put(25,9,-12,STONE_BRICKS);
        b.layer(-25,8,-10,25,-8,STONE_BRICKS);b.balustrade(-25,-7,25,-7,9,STONE_BRICK_WALL);
        b.stairs(-28,0,3,8,NORTH,3,STONE_BRICK_STAIRS);b.layer(-28,8,-10,-24,-5,STONE_BRICKS);b.box(-25,9,-7,-23,11,-7,AIR);
        b.pool(8,-3,22,7,MOSSY_STONE_BRICKS);b.layer(7,1,1,23,3,OAK_SLAB);
        b.paving(-23,2,-11,11,COARSE_DIRT,GRAVEL,MOSS_BLOCK);
        b.awning(-22,3,-12,10,4,GREEN_WOOL,OAK_FENCE);b.workshop(-17,1,5,OAK_PLANKS,OAK_SLAB);b.put(-20,1,8,CARTOGRAPHY_TABLE);
        b.awning(10,10,22,14,4,OAK_SLAB,OAK_FENCE);b.shelf(11,1,12,20,OAK_SLAB,POTTED_FERN,DECORATED_POT);
        b.crates(-2,-15,OAK_PLANKS);b.put(20,9,-9,CRAFTING_TABLE);b.put(-18,9,-9,BARREL,EAST);b.lamp(-17,7,-10);
        b.path(0,18,0,11,GRAVEL);b.path(-26,11,23,11,GRAVEL);b.path(-26,11,-26,4,GRAVEL);
        b.path(0,11,0,-15,GRAVEL);b.path(-22,-15,22,-15,GRAVEL);b.path(23,11,23,-4,GRAVEL);
        b.bollard(-5,13,MOSSY_STONE_BRICKS);b.bollard(5,13,MOSSY_STONE_BRICKS);
    }
    /** A hollow root grotto and ancient ritual garden, woven into an asymmetric branching tree. */
    private static void shrine(Builder b){
        b.branchingTree(0,-10,27,DARK_OAK_LOG,DARK_OAK_LEAVES);
        b.circle(0,0,-10,7,7,MOSSY_STONE_BRICKS);b.box(-2,1,-14,2,5,-5,AIR);
        b.beam(-5,11,-10,-12,6,-3,DARK_OAK_LOG);b.beam(-12,6,-3,-17,0,3,DARK_OAK_LOG);
        b.beam(5,10,-10,12,5,-6,DARK_OAK_LOG);b.beam(12,5,-6,18,0,-1,DARK_OAK_LOG);
        b.floor(-2,-17,2,-4,MOSSY_STONE_BRICKS);b.put(0,1,-12,LODESTONE);b.put(0,2,-12,AMETHYST_CLUSTER,UP);
        b.put(-2,1,-9,DECORATED_POT);b.put(2,1,-9,DECORATED_POT);b.lamp(0,4,-9);
        b.circle(0,0,5,10,8,MOSS_BLOCK);b.ring(0,0,5,9,7,.7,MOSSY_STONE_BRICKS);b.roundPool(0,0,5,3.5,2.5,MOSSY_STONE_BRICKS);
        for(int side:new int[]{-1,1}){int x=side*18;
            b.circle(x,0,-12,6,7,MOSSY_STONE_BRICKS);b.ruinWall(x-5,-19,x+5,-19,5,MOSSY_STONE_BRICKS,CRACKED_STONE_BRICKS);
            b.archAt(x,0,-18,4,3,2,MOSSY_STONE_BRICKS);b.shelf(x-3,1,-16,x+3,OAK_SLAB,POTTED_FERN,POTTED_BROWN_MUSHROOM);
            b.put(x-3,1,-10,CAULDRON);b.put(x+3,1,-10,BARREL,SOUTH);b.put(x,1,-10,BREWING_STAND);
            b.circle(x,0,9,5,4,MOSSY_STONE_BRICKS);b.bench(x-3,1,11,x+1,DARK_OAK_STAIRS,NORTH);b.put(x+2,1,7,DECORATED_POT);
        }
        b.path(0,18,0,14,GRAVEL);b.path(-18,14,18,14,GRAVEL);b.path(-11,14,-11,-5,GRAVEL);b.path(11,14,11,-6,GRAVEL);
        b.path(-11,-5,-18,-5,GRAVEL);b.path(11,-6,18,-6,GRAVEL);b.path(-18,-5,-18,-15,GRAVEL);b.path(18,-6,18,-15,GRAVEL);
        b.path(-18,14,-18,10,GRAVEL);b.path(18,14,18,10,GRAVEL);b.path(-11,-3,0,-3,GRAVEL);b.path(0,-3,0,-7,GRAVEL);
        b.path(-8,-3,-8,-22,GRAVEL);b.path(-8,-22,3,-22,GRAVEL);b.path(3,-22,3,-16,GRAVEL);
        b.shelf(-3,1,-24,3,STONE_BRICK_SLAB,DECORATED_POT,POTTED_FERN);b.put(-5,1,11,DECORATED_POT);b.put(5,1,11,DECORATED_POT);
    }

    /** A proper bazaar of varied, densely furnished stalls, not a warehouse with four identical kiosks. */
    private static void market(Builder b){
        b.circle(0,0,-3,25,22,QUARTZ_BRICKS);b.ring(0,0,-3,9,9,.6,POLISHED_ANDESITE);
        b.roundPool(0,0,-3,3,3,CHISELED_QUARTZ_BLOCK);b.post(0,-3,1,3,QUARTZ_PILLAR);b.put(0,4,-3,DECORATED_POT);
        b.awning(-24,-17,-13,-10,5,PURPLE_WOOL,QUARTZ_PILLAR);b.shelf(-23,1,-15,-15,QUARTZ_SLAB,DECORATED_POT,POTTED_ALLIUM);
        b.put(-23,1,-12,BREWING_STAND);b.put(-16,1,-12,CAULDRON);b.put(-14,1,-12,BARREL,SOUTH);
        b.awning(10,-20,23,-13,5,WHITE_WOOL,QUARTZ_PILLAR);b.workshop(16,1,-17,QUARTZ_BRICKS,QUARTZ_SLAB);
        b.put(12,1,-15,BLAST_FURNACE,SOUTH);b.put(21,1,-15,GRINDSTONE,SOUTH);
        b.awning(-23,3,-13,10,4,YELLOW_WOOL,QUARTZ_PILLAR);b.kitchen(-18,1,5,QUARTZ_BRICKS,QUARTZ_SLAB);
        b.bench(-22,1,8,-18,SMOOTH_QUARTZ_STAIRS,NORTH);b.table(-15,1,8,OAK_FENCE,QUARTZ_SLAB);
        b.awning(12,3,24,10,5,CYAN_WOOL,QUARTZ_PILLAR);b.shelf(13,1,5,22,QUARTZ_SLAB,POTTED_ALLIUM,POTTED_FERN);
        b.put(14,1,8,COMPOSTER);b.put(21,1,8,BARREL,SOUTH);
        // Textile arcade and packing gallery, with a stair instead of a giant attic.
        b.layer(-8,3,-23,7,-15,WAXED_OXIDIZED_CUT_COPPER);
        for(int x:new int[]{-8,7})for(int z:new int[]{-23,-15})b.post(x,z,1,7,QUARTZ_PILLAR);
        b.hip(-9,-24,8,-14,8,WAXED_OXIDIZED_CUT_COPPER,WAXED_OXIDIZED_CUT_COPPER_STAIRS,WAXED_OXIDIZED_CUT_COPPER_SLAB);
        b.shelf(-6,4,-22,5,QUARTZ_SLAB,FLOWER_POT,DECORATED_POT);b.put(-6,4,-18,BARREL,SOUTH);b.put(5,4,-18,LOOM,SOUTH);
        b.stairs(-2,0,-10,3,NORTH,3,QUARTZ_STAIRS);b.layer(-2,3,-16,0,-13,WAXED_OXIDIZED_CUT_COPPER);
        for(int x:new int[]{-7,6})b.balustrade(x,-22,x,-16,4,QUARTZ_PILLAR);
        b.put(-5,1,-16,PURPLE_WOOL);b.put(-3,1,-16,LOOM,SOUTH);b.put(3,1,-16,WHITE_WOOL);b.put(5,1,-16,BARREL,SOUTH);
        b.path(0,18,0,11,POLISHED_ANDESITE);b.path(-21,13,21,13,POLISHED_ANDESITE);b.path(-10,13,-10,-20,POLISHED_ANDESITE);b.path(9,13,9,-21,POLISHED_ANDESITE);
        b.path(-10,-8,-20,-8,POLISHED_ANDESITE);b.path(9,-11,18,-11,POLISHED_ANDESITE);b.path(-10,11,-19,11,POLISHED_ANDESITE);b.path(9,11,20,11,POLISHED_ANDESITE);
        b.path(-10,-10,-2,-10,POLISHED_ANDESITE);b.path(-8,0,9,0,POLISHED_ANDESITE);
        b.archAt(0,0,15,4,3,2,QUARTZ_BRICKS);b.put(0,6,15,WAXED_OXIDIZED_CUT_COPPER_SLAB);
        for(int[] p:new int[][]{{-11,8},{10,8},{-10,-5},{9,-6}})b.bollard(p[0],p[1],CHISELED_QUARTZ_BLOCK);
        b.crates(24,-5,OAK_PLANKS);b.put(-24,1,-4,DECORATED_POT);
    }
    /** Roofless cloister arcades, a small furnished scriptorium and an accessible bell platform. */
    private static void monastery(Builder b){
        b.paving(-24,-24,24,13,QUARTZ_BRICKS,SMOOTH_QUARTZ,CALCITE);
        b.roundPool(0,0,-3,5,4,CHISELED_QUARTZ_BLOCK);b.ring(0,0,-3,10,9,.6,POLISHED_ANDESITE);
        for(int x=-21;x<=21;x+=7){
            b.archAt(x,0,-17,3,4,2,QUARTZ_BRICKS);b.archAt(x,0,10,3,4,2,QUARTZ_BRICKS);
            for(int side:new int[]{-3,3})b.put(x+side,5,-17,CHISELED_QUARTZ_BLOCK);
        }
        for(int z:new int[]{-12,-5,2})for(int side:new int[]{-1,1}){
            int x=side*22;b.post(x,z-3,1,5,QUARTZ_PILLAR);b.post(x,z+3,1,5,QUARTZ_PILLAR);
            for(int dz=-3;dz<=3;dz++)b.put(x,5+3-Math.abs(dz),z+dz,QUARTZ_BRICKS);
        }
        b.layer(-24,7,-19,24,-17,QUARTZ_SLAB);b.layer(-24,7,10,24,12,QUARTZ_SLAB);
        for(int x:new int[]{-24,24})b.layer(x,7,-16,x,9,QUARTZ_SLAB);
        // A compact writing room with individual desks and storage opens onto the cloister.
        b.room(-23,-23,-10,-19,0,4,CALCITE,QUARTZ_BRICKS);b.hip(-24,-24,-9,-18,5,WAXED_OXIDIZED_CUT_COPPER,WAXED_OXIDIZED_CUT_COPPER_STAIRS,WAXED_OXIDIZED_CUT_COPPER_SLAB);
        b.door(-16,1,-19,SPRUCE_DOOR,QUARTZ_PILLAR,SOUTH);b.window(-23,2,-21,WEST,GLASS_PANE,QUARTZ_SLAB);
        b.writingDesk(-20,1,-22,QUARTZ_BRICKS,QUARTZ_SLAB);b.writingDesk(-13,1,-22,QUARTZ_BRICKS,QUARTZ_SLAB);
        b.lamp(-16,3,-21);b.put(-22,1,-20,BOOKSHELF);b.put(-11,1,-20,BARREL,WEST);
        b.circle(1,0,-23,7,4,CHISELED_QUARTZ_BLOCK);b.put(1,1,-23,LODESTONE);b.put(1,2,-23,AMETHYST_CLUSTER,UP);
        b.bench(-4,1,-20,5,SMOOTH_QUARTZ_STAIRS,NORTH);b.put(-5,1,-23,DECORATED_POT);b.put(6,1,-23,DECORATED_POT);
        b.layer(12,8,-24,23,-16,QUARTZ_BRICKS);b.stairs(13,0,-6,8,NORTH,3,QUARTZ_STAIRS);b.layer(12,8,-18,16,-14,QUARTZ_BRICKS);
        for(int x:new int[]{12,23})for(int z:new int[]{-24,-16})b.post(x,z,1,12,QUARTZ_PILLAR);
        b.archAt(17,8,-20,5,3,2,QUARTZ_BRICKS);b.put(17,11,-20,CHISELED_QUARTZ_BLOCK);b.put(17,12,-20,BELL,SOUTH);b.put(17,13,-20,WAXED_OXIDIZED_CUT_COPPER_SLAB);
        b.balustrade(12,-24,23,-24,9,STONE_BRICK_WALL);b.balustrade(23,-24,23,-16,9,STONE_BRICK_WALL);
        b.shelf(17,9,-23,21,QUARTZ_SLAB,DECORATED_POT,POTTED_ALLIUM);
        b.awning(-23,1,-16,7,4,WHITE_WOOL,QUARTZ_PILLAR);b.dining(-22,1,4,4,OAK_FENCE,QUARTZ_SLAB);
        b.awning(17,2,23,8,4,WHITE_WOOL,QUARTZ_PILLAR);b.kitchen(20,1,4,QUARTZ_BRICKS,QUARTZ_SLAB);
        b.path(0,18,0,11,POLISHED_ANDESITE);b.path(-16,12,20,12,POLISHED_ANDESITE);b.path(-9,12,-9,-21,POLISHED_ANDESITE);
        b.path(9,12,9,-21,POLISHED_ANDESITE);b.path(-9,-16,-16,-16,POLISHED_ANDESITE);b.path(-16,-16,-16,-18,POLISHED_ANDESITE);
        b.path(9,-5,14,-5,POLISHED_ANDESITE);b.path(-9,8,-19,8,POLISHED_ANDESITE);b.path(9,9,20,9,POLISHED_ANDESITE);
        b.bollard(-6,14,CHISELED_QUARTZ_BLOCK);b.bollard(6,14,CHISELED_QUARTZ_BLOCK);
        for(int x:new int[]{-14,14})for(int z:new int[]{-10,-2})b.planter(x,z,CHISELED_QUARTZ_BLOCK,z== -10?ALLIUM:WHITE_TULIP);
        b.bench(-17,1,-5,-13,SMOOTH_QUARTZ_STAIRS,SOUTH);b.bench(13,1,-5,17,SMOOTH_QUARTZ_STAIRS,SOUTH);
        b.put(-14,1,-14,DECORATED_POT);b.put(14,1,11,DECORATED_POT);
    }
    /** A compact domed observatory: telescope, radial map stations, upper gallery and instrument yard. */
    private static void observatory(Builder b){
        b.circle(0,0,-10,11,11,QUARTZ_BRICKS);
        for(int y=1;y<=5;y++)b.ring(0,y,-10,10.5,10.5,.65,y==1?CHISELED_QUARTZ_BLOCK:CALCITE);
        for(int y=9;y<=18;y++){
            double r=Math.sqrt(Math.max(0,121-(y-8)*(y-8)));
            b.ring(0,y,-10,r,r,.65,y%3==0?WAXED_OXIDIZED_CUT_COPPER:LIGHT_BLUE_STAINED_GLASS);
        }
        b.put(0,19,-10,WAXED_OXIDIZED_CUT_COPPER);b.put(0,20,-10,LIGHTNING_ROD,UP);
        for(int x=-11;x<=11;x++)for(int z=-21;z<=1;z++){
            double r=Math.hypot(x,z+10);if(r>=6.5&&r<=10.7)b.put(x,6,z,QUARTZ_BRICKS);
        }
        b.ring(0,7,-10,6.5,6.5,.35,IRON_BARS);b.stairs(-9,0,-4,6,NORTH,2,QUARTZ_STAIRS);
        b.layer(-10,6,-13,-7,-10,QUARTZ_BRICKS);b.box(-9,7,-11,-8,9,-9,AIR);
        b.box(-2,1,-1,2,4,2,AIR);b.door(0,1,0,SPRUCE_DOOR,QUARTZ_PILLAR,SOUTH);
        b.circle(0,1,-10,3.5,3.5,POLISHED_ANDESITE);b.post(0,-10,2,4,WAXED_CUT_COPPER);
        for(int i=0;i<6;i++){
            int x=i,y=4+i,z=-10-i/2;b.box(x-1,y-1,z-1,x+1,y+1,z+1,WAXED_OXIDIZED_CUT_COPPER);
            if(i%2==0)b.ring(x,y,z,1.8,1.8,.5,WAXED_CUT_COPPER);
        }
        b.put(6,10,-13,BLACK_STAINED_GLASS);b.beam(-3,2,-10,3,2,-10,WAXED_CUT_COPPER);
        b.writingDesk(-5,1,-17,QUARTZ_BRICKS,QUARTZ_SLAB);b.writingDesk(6,1,-17,QUARTZ_BRICKS,QUARTZ_SLAB);
        b.put(8,1,-9,CARTOGRAPHY_TABLE);b.put(-6,1,-6,LODESTONE);b.put(5,1,-6,LECTERN,NORTH);b.lamp(0,4,-4);
        b.shelf(-5,7,-19,5,QUARTZ_SLAB,DECORATED_POT,FLOWER_POT);b.put(8,7,-6,CARTOGRAPHY_TABLE);
        b.circle(-20,0,5,7,6,QUARTZ_BRICKS);b.awning(-25,1,-15,8,5,PURPLE_WOOL,QUARTZ_PILLAR);
        b.shelf(-24,1,3,-16,QUARTZ_SLAB,DECORATED_POT,POTTED_ALLIUM);b.writingDesk(-21,1,6,QUARTZ_BRICKS,QUARTZ_SLAB);
        b.paving(14,-1,25,10,QUARTZ_BRICKS,POLISHED_ANDESITE,SMOOTH_QUARTZ);b.awning(15,1,24,8,5,WHITE_WOOL,QUARTZ_PILLAR);
        b.workshop(20,1,3,QUARTZ_BRICKS,QUARTZ_SLAB);b.put(18,1,7,LODESTONE);b.put(21,1,7,GRINDSTONE,SOUTH);
        b.path(0,18,0,2,POLISHED_ANDESITE);b.path(-20,12,20,12,POLISHED_ANDESITE);b.path(-20,12,-20,9,POLISHED_ANDESITE);b.path(20,12,20,9,POLISHED_ANDESITE);
        b.bollard(-4,13,CHISELED_QUARTZ_BLOCK);b.bollard(4,13,CHISELED_QUARTZ_BLOCK);
    }
    /** A staged amphitheatre with circular seating, orchestra steps, backstage rooms and a sunken prop vault. */
    private static void theatre(Builder b){
        b.circle(0,0,-4,25,21,QUARTZ_BRICKS);b.circle(0,0,-4,9,7,POLISHED_ANDESITE);
        for(int row=0;row<6;row++){
            double rx=12+row*2,rz=9+row*1.8;
            for(int x=-(int)rx;x<=rx;x++)for(int z=-4;z<=19;z++){
                double r=Math.sqrt(x*x/(rx*rx)+(z+4)*(z+4)/(rz*rz));
                if(r>.88&&r<=1&&Math.abs(x)>2){b.box(x,0,z,x,row+1,z,QUARTZ_BRICKS);b.put(x,row+2,z,SMOOTH_QUARTZ_STAIRS,NORTH);}
            }
        }
        b.layer(-9,1,-15,9,-9,QUARTZ_BRICKS);b.layer(-7,2,-14,7,-11,CHISELED_QUARTZ_BLOCK);
        b.archAt(0,0,-16,9,5,3,QUARTZ_BRICKS);
        for(int x:new int[]{-10,10}){b.post(x,-15,1,8,QUARTZ_PILLAR);b.box(x,3,-15,x,7,-15,PURPLE_WOOL);b.put(x,9,-15,WAXED_OXIDIZED_CUT_COPPER_SLAB);}
        b.put(-5,3,-12,JUKEBOX);b.put(4,3,-12,NOTE_BLOCK);b.put(6,3,-12,DECORATED_POT);b.put(0,3,-13,LECTERN,SOUTH);
        for(int side:new int[]{-1,1}){int x=side*19;
            b.room(x-5,-18,x+5,-8,0,4,CALCITE,QUARTZ_BRICKS);b.hip(x-6,-19,x+6,-7,5,WAXED_OXIDIZED_CUT_COPPER,WAXED_OXIDIZED_CUT_COPPER_STAIRS,WAXED_OXIDIZED_CUT_COPPER_SLAB);
            b.door(x,1,-8,SPRUCE_DOOR,QUARTZ_PILLAR,SOUTH);b.window(x,2,-18,NORTH,GLASS_PANE,QUARTZ_SLAB);
            b.shelf(x-3,1,-16,x+3,QUARTZ_SLAB,DECORATED_POT,FLOWER_POT);b.put(x-3,1,-11,LOOM,SOUTH);b.put(x+3,1,-11,BARREL,SOUTH);
            b.bench(x-2,1,-10,x+1,SMOOTH_QUARTZ_STAIRS,NORTH);b.lamp(x,3,-13);
        }
        b.room(-6,-27,6,-20,-3,4,QUARTZ_BRICKS,SPRUCE_PLANKS);b.layer(-6,2,-27,6,-20,QUARTZ_BRICKS);
        b.box(-1,-2,-21,1,3,-18,AIR);b.stairs(-1,-3,-20,3,SOUTH,3,QUARTZ_STAIRS);b.layer(-1,0,-17,1,-16,QUARTZ_BRICKS);
        b.cabinet(-3,-2,-25,SPRUCE_PLANKS,SPRUCE_SLAB,SOUTH);b.put(4,-2,-23,NOTE_BLOCK);b.lamp(0,0,-24);
        b.path(0,18,0,-8,POLISHED_ANDESITE);b.path(-25,-6,25,-6,POLISHED_ANDESITE);b.path(-19,-6,-19,-7,POLISHED_ANDESITE);b.path(19,-6,19,-7,POLISHED_ANDESITE);
        b.path(-26,11,-26,-20,POLISHED_ANDESITE);b.path(26,11,26,-20,POLISHED_ANDESITE);b.path(-26,-20,26,-20,POLISHED_ANDESITE);
        b.path(-26,11,-13,11,POLISHED_ANDESITE);b.path(13,11,26,11,POLISHED_ANDESITE);b.path(-13,11,13,11,POLISHED_ANDESITE);
        b.awning(-21,8,-14,12,4,WHITE_WOOL,QUARTZ_PILLAR);b.awning(14,8,21,12,4,WHITE_WOOL,QUARTZ_PILLAR);
        b.table(-18,1,10,OAK_FENCE,QUARTZ_SLAB);b.table(18,1,10,OAK_FENCE,QUARTZ_SLAB);b.put(-23,1,-5,DECORATED_POT);b.put(23,1,-5,DECORATED_POT);
        b.stairs(-1,-3,-20,3,SOUTH,3,QUARTZ_STAIRS);
    }
    /** A crescent pool and open copper pavilion, with bathing alcoves and a planted processional walk. */
    private static void moonBath(Builder b){
        b.circle(0,0,-6,24,22,QUARTZ_BRICKS);b.ring(0,0,-6,20,18,.6,POLISHED_ANDESITE);
        b.roundPool(0,0,-9,11,10,CHISELED_QUARTZ_BLOCK);
        // Fill the western bite of the circle to make a crescent of water, keeping every edge sealed.
        for(int x=-12;x<=3;x++)for(int z=-20;z<=2;z++)if(Math.hypot(x+6,(z+9)*1.05)<9)b.put(x,0,z,QUARTZ_BRICKS);
        b.circle(-7,0,-9,6,6,CHISELED_QUARTZ_BLOCK);b.ring(-7,1,-9,5.5,5.5,.4,QUARTZ_SLAB);
        for(int i=0;i<8;i++){
            double a=Math.PI*2*i/8;int x=-7+(int)Math.round(Math.cos(a)*5.5),z=-9+(int)Math.round(Math.sin(a)*5.5);
            b.post(x,z,1,5,QUARTZ_PILLAR);b.put(x,6,z,WAXED_OXIDIZED_CUT_COPPER_SLAB);
        }
        for(int h=0;h<5;h++)b.ring(-7,6+h,-9,6-h,6-h,.6,WAXED_OXIDIZED_CUT_COPPER_SLAB);
        b.put(-7,11,-9,LANTERN);b.put(-7,1,-9,DECORATED_POT);b.bench(-9,1,-11,-5,SMOOTH_QUARTZ_STAIRS,SOUTH);
        b.box(-9,1,-4,-5,3,-3,AIR);b.path(-7,-4,-7,3,POLISHED_ANDESITE);
        for(int side:new int[]{-1,1}){int x=side*20;
            b.awning(x-4,-17,x+4,-10,5,WHITE_WOOL,QUARTZ_PILLAR);b.shelf(x-3,1,-15,x+3,QUARTZ_SLAB,POTTED_ALLIUM,DECORATED_POT);
            b.put(x-3,1,-12,CAULDRON);b.put(x+3,1,-12,BARREL,SOUTH);b.bench(x-2,1,-11,x+1,SMOOTH_QUARTZ_STAIRS,NORTH);
            b.paving(x-4,2,x+4,10,QUARTZ_BRICKS,SMOOTH_QUARTZ,CALCITE);
            b.awning(x-4,3,x+4,9,4,WHITE_WOOL,QUARTZ_PILLAR);b.shelf(x-3,1,5,x+3,QUARTZ_SLAB,FLOWER_POT,POTTED_WHITE_TULIP);
            b.put(x-2,1,8,LOOM,SOUTH);b.put(x+2,1,8,CAULDRON);
        }
        for(int x:new int[]{-11,0,11}){b.post(x,-23,1,5,QUARTZ_PILLAR);b.put(x,6,-23,WAXED_OXIDIZED_CUT_COPPER_SLAB);}
        b.archAt(-5,0,-23,5,3,2,QUARTZ_BRICKS);b.archAt(6,0,-23,5,3,2,QUARTZ_BRICKS);
        b.shelf(-8,1,-26,8,QUARTZ_SLAB,POTTED_ALLIUM,DECORATED_POT);b.put(0,1,-24,CAULDRON);
        b.path(0,18,0,13,POLISHED_ANDESITE);b.path(-20,12,20,12,POLISHED_ANDESITE);
        b.path(-15,12,-15,-20,POLISHED_ANDESITE);b.path(15,12,15,-20,POLISHED_ANDESITE);b.path(-15,-21,15,-21,POLISHED_ANDESITE);
        b.path(-20,12,-20,10,POLISHED_ANDESITE);b.path(20,12,20,10,POLISHED_ANDESITE);b.path(-15,-8,-20,-8,POLISHED_ANDESITE);b.path(15,-8,20,-8,POLISHED_ANDESITE);
        b.path(-20,-8,-20,-10,POLISHED_ANDESITE);b.path(20,-8,20,-10,POLISHED_ANDESITE);b.path(2,-21,2,-25,POLISHED_ANDESITE);
        b.path(-7,3,-7,12,POLISHED_ANDESITE);
        for(int x:new int[]{-10,10}){b.planter(x,10,CHISELED_QUARTZ_BLOCK,ALLIUM);b.put(x,1,14,DECORATED_POT);}
        b.bollard(-3,15,CHISELED_QUARTZ_BLOCK);b.bollard(3,15,CHISELED_QUARTZ_BLOCK);
    }
}
