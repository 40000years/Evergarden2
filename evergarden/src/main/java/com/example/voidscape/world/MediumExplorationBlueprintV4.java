package com.example.voidscape.world;

import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import static org.bukkit.Material.*;
import static org.bukkit.block.BlockFace.*;
import com.example.voidscape.world.ExplorationBlueprintV4.Point;
import com.example.voidscape.world.ExplorationBlueprintV4.Voxel;

/** Twenty individually planned destinations, with usable rooms and several exploration routes. */
final class MediumExplorationBlueprintV4 {
    private static final Map<ExplorationLayout.Kind,List<Voxel>> CACHE=new ConcurrentHashMap<>();
    private MediumExplorationBlueprintV4(){}
    static List<Point> chests(ExplorationLayout.Kind kind){return switch(kind){
        case ORCHARD_ESTATE->points(-15,1,-11, -5,1,-9, 13,1,-13, 18,1,-5, -14,7,-9, 13,1,9);
        case ROSE_CONSERVATORY->points(-16,1,-12, 16,1,-12, -14,1,8, 14,1,8, 0,1,-18);
        case GARDEN_WATERMILL->points(-12,1,-11, -3,1,-7, -11,7,-7, 17,1,-13, 19,1,6);
        case TRAVELERS_INN->points(-15,1,-12, 13,1,-12, -12,7,-12, 12,7,-12, -12,7,0, 15,1,9);
        case HEDGE_SANCTUARY->points(-19,1,-17, 19,1,-17, -17,1,7, 17,1,7, 0,1,-22);
        case ALPINE_LODGE->points(-17,1,-12, 14,1,-12, -13,7,-10, 12,7,-10, 16,1,9);
        case FROZEN_BATHHOUSE->points(-17,1,-15, 17,1,-15, -19,1,4, 19,1,4, 0,1,-21);
        case ICEBOUND_OUTPOST->points(-15,1,-11, 15,1,-11, -15,13,-11, 15,13,-11, -8,1,10, 10,1,10);
        case GLACIER_MINE->points(-17,1,-9, 17,1,-9, -10,1,-18, 10,1,-18, -17,1,9, 16,1,9);
        case AURORA_HALL->points(-15,1,-12, 15,1,-12, -8,1,-20, 8,1,-20, -18,1,9);
        case WOODLAND_MANOR->points(-16,1,-13, -5,1,-12, 14,1,-9, -14,7,-11, 13,7,-9, 16,1,11);
        case CANOPY_REFUGE->points(-15,9,-10, 12,9,-8, -5,9,-7, 3,9,4, 12,1,9);
        case MUSHROOM_HAMLET->points(-16,1,-11, 15,1,-13, -15,1,8, 15,1,8, 0,1,-19);
        case OVERGROWN_AQUEDUCT->points(-19,1,-14, 19,1,-14, -17,1,8, 17,1,8, -18,10,-8);
        case ROOTBOUND_SHRINE->points(-17,1,-14, 17,1,-14, -18,1,9, 18,1,9, 0,1,-21);
        case MOON_MARKET->points(-18,1,-14, 18,1,-14, -18,1,7, 18,1,7, -9,7,-14, 9,7,-14);
        case ASTRAL_MONASTERY->points(-19,1,-16, 19,1,-16, -18,1,7, 18,1,7, 0,1,-22);
        case TIDE_OBSERVATORY->points(-6,1,-8, 6,1,-8, -9,9,-12, 8,9,-7, -20,1,7, 20,1,7);
        case FALLEN_AMPHITHEATRE->points(-20,1,-9, 20,1,-9, -13,1,9, 13,1,9, 0,1,-21);
        case LUNAR_BATH_GARDEN->points(-19,1,-16, 19,1,-16, -19,1,7, 19,1,7, 0,1,-23);
        default->throw new IllegalArgumentException("Not a medium destination: "+kind);
    };}
    private static List<Point> points(int... coordinates){
        var result=new ArrayList<Point>();for(int i=0;i<coordinates.length;i+=3)result.add(new Point(coordinates[i],coordinates[i+1],coordinates[i+2]));return List.copyOf(result);
    }
    static List<Voxel> blocks(ExplorationLayout.Kind kind){return CACHE.computeIfAbsent(kind,MediumExplorationBlueprintV4::build);}
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
                box(px,0,pz,px,base+step,pz,STONE_BRICKS);put(px,base+step,pz,material,face);box(px,base+step+1,pz,px,base+step+3,pz,AIR);
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
            put(cx+2,1,cz-2,CRAFTING_TABLE);lamp(cx,4,cz+1);
            bed(cx+2,1,cz+2,cap==RED_MUSHROOM_BLOCK?RED_BED:WHITE_BED);
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
    private static void orchard(Builder b){
        b.room(-21,-19,-1,3,0,5,WHITE_TERRACOTTA,OAK_PLANKS);b.room(-21,-19,-1,3,6,4,WHITE_TERRACOTTA,OAK_PLANKS);
        b.timber(-21,-19,-1,3,0,5,STRIPPED_OAK_LOG);b.timber(-21,-19,-1,3,6,10,STRIPPED_OAK_LOG);
        b.gable(-11,-19,10,3,11,DARK_PRISMARINE_STAIRS,DARK_PRISMARINE_SLAB,WHITE_TERRACOTTA);
        b.room(7,-21,23,-1,0,5,SPRUCE_PLANKS,OAK_PLANKS);b.timber(7,-21,23,-1,0,5,STRIPPED_SPRUCE_LOG);
        b.gable(15,-21,8,-1,6,SPRUCE_STAIRS,SPRUCE_SLAB,SPRUCE_PLANKS);
        b.gableFrame(-11,3,10,11,STRIPPED_OAK_LOG,GLASS_PANE,OAK_SLAB);b.gableFrame(15,-1,8,6,STRIPPED_SPRUCE_LOG,GLASS_PANE,SPRUCE_SLAB);
        for(int x:new int[]{-17,-5}){b.window(x,2,3,SOUTH,GLASS_PANE,OAK_SLAB);b.window(x,8,3,SOUTH,GLASS_PANE,OAK_SLAB);}
        b.path(0,18,0,8,COBBLESTONE);b.path(0,8,-10,8,COBBLESTONE);b.path(-10,8,-10,4,COBBLESTONE);b.path(0,8,15,8,COBBLESTONE);b.path(15,8,15,0,COBBLESTONE);
        b.door(-10,1,3,OAK_DOOR,STRIPPED_OAK_LOG,SOUTH);b.door(15,1,-1,SPRUCE_DOOR,STRIPPED_SPRUCE_LOG,SOUTH);
        b.stairs(-19,0,0,6,NORTH,2,OAK_STAIRS);b.bed(-18,7,-15,RED_BED);b.bed(-4,7,-15,WHITE_BED);
        b.box(-19,1,-17,-15,3,-17,BOOKSHELF);b.put(-4,1,-17,SMOKER,SOUTH);b.table(-8,1,-7,OAK_FENCE,OAK_SLAB);b.put(-8,3,-7,POTTED_POPPY);
        b.box(8,1,-20,11,3,-17,HAY_BLOCK);b.crates(20,-19,SPRUCE_PLANKS);b.put(20,1,-3,COMPOSTER);b.lamp(15,4,-8);b.lamp(-10,4,-5);
        for(int x:new int[]{6,15,22}){b.tree(x,14,7,CHERRY_LOG,CHERRY_LEAVES);b.planter(x,20,OAK_SLAB,PINK_TULIP);}
        b.floor(10,7,17,10,OAK_PLANKS);b.crates(15,10,OAK_PLANKS);b.put(10,1,9,COMPOSTER);
        b.shelf(-18,2,4,-14,OAK_SLAB,POTTED_DANDELION,POTTED_WHITE_TULIP);b.box(-23,0,8,-20,0,17,MOSS_BLOCK);
    }
    private static void conservatory(Builder b){
        b.floor(-23,-23,23,13,STONE_BRICKS);
        for(int x:new int[]{-23,23})b.box(x,1,-23,x,6,13,WHITE_TERRACOTTA);
        b.box(-23,1,-23,23,6,-23,WHITE_TERRACOTTA);b.box(-23,1,13,23,6,13,WHITE_TERRACOTTA);
        for(int x=-23;x<=23;x++)for(int z=-23;z<=13;z++){
            int y=7+(int)Math.round(8*Math.sin((x+23)*Math.PI/46));b.put(x,y,z,z%6==0?WAXED_OXIDIZED_CUT_COPPER:GLASS);
            if(z%6==0)b.box(x== -23||x==23?x:-23,1,z,x== -23||x==23?x:-23,7,z,QUARTZ_PILLAR);
        }
        for(int x:new int[]{-23,23})for(int z=-20;z<=10;z+=6)b.window(x,3,z,x<0?WEST:EAST,GLASS_PANE,QUARTZ_SLAB);
        b.pool(-6,-12,6,-2,QUARTZ_BRICKS);b.box(-6,1,-12,6,1,-12,QUARTZ_SLAB);
        for(int x:new int[]{-16,16})for(int z:new int[]{-16,-5,7}){b.planter(x,z,CHERRY_SLAB,ROSE_BUSH);b.put(x-1,1,z,PINK_TULIP);b.put(x+1,1,z,ALLIUM);}
        b.path(0,18,0,13,STONE_BRICKS);b.path(0,12,-10,12,STONE_BRICKS);b.path(0,12,10,12,STONE_BRICKS);
        b.path(-10,12,-10,-19,STONE_BRICKS);b.path(10,12,10,-19,STONE_BRICKS);b.path(-10,-19,10,-19,STONE_BRICKS);
        b.door(0,1,13,CHERRY_DOOR,STRIPPED_CHERRY_LOG,SOUTH);b.bench(-4,1,-16,4,CHERRY_STAIRS,NORTH);
        b.shelf(-21,2,-21,-15,CHERRY_SLAB,POTTED_AZALEA_BUSH,POTTED_ALLIUM);b.shelf(15,2,-21,21,CHERRY_SLAB,POTTED_FERN,POTTED_WHITE_TULIP);
        b.put(-20,1,10,COMPOSTER);b.put(20,1,10,CAULDRON);b.lamp(-10,6,4);b.lamp(10,6,4);b.put(0,1,-21,DECORATED_POT);
    }
    private static void watermill(Builder b){
        b.room(-21,-19,3,3,0,5,WHITE_TERRACOTTA,SPRUCE_PLANKS);b.room(-21,-19,3,3,6,4,SPRUCE_PLANKS,SPRUCE_PLANKS);
        b.timber(-21,-19,3,3,0,5,STRIPPED_SPRUCE_LOG);b.timber(-21,-19,3,3,6,10,STRIPPED_SPRUCE_LOG);
        b.gable(-9,-19,12,3,11,SPRUCE_STAIRS,SPRUCE_SLAB,WHITE_TERRACOTTA);
        b.pool(5,-24,10,5,MOSSY_STONE_BRICKS);b.pool(5,1,15,7,MOSSY_STONE_BRICKS);
        for(int i=0;i<96;i++){double a=i*Math.PI/48;int z=-7+(int)Math.round(Math.cos(a)*6),y=7+(int)Math.round(Math.sin(a)*6);b.put(4,y,z,DARK_OAK_PLANKS);if(i%8==0)b.put(5,y,z,OAK_TRAPDOOR,EAST);}
        b.beam(3,7,-7,6,7,-7,STRIPPED_DARK_OAK_LOG);b.beam(4,1,-7,4,13,-7,DARK_OAK_LOG);b.beam(4,7,-13,4,7,-1,DARK_OAK_LOG);
        b.room(13,-21,25,-7,0,4,SPRUCE_PLANKS,OAK_PLANKS);b.gable(19,-21,6,-7,5,SPRUCE_STAIRS,SPRUCE_SLAB,SPRUCE_PLANKS);
        b.path(0,18,0,8,GRAVEL);b.path(0,8,-9,8,GRAVEL);b.path(-9,8,-9,4,GRAVEL);b.path(0,11,19,11,GRAVEL);b.path(19,11,19,-6,GRAVEL);
        b.door(-9,1,3,SPRUCE_DOOR,STRIPPED_SPRUCE_LOG,SOUTH);b.door(19,1,-7,OAK_DOOR,STRIPPED_OAK_LOG,SOUTH);
        b.stairs(-19,0,0,6,NORTH,2,SPRUCE_STAIRS);b.window(-21,2,-8,WEST,GLASS_PANE,SPRUCE_SLAB);b.window(-21,8,-8,WEST,GLASS_PANE,SPRUCE_SLAB);
        for(int x:new int[]{-15,-3}){b.window(x,2,3,SOUTH,GLASS_PANE,SPRUCE_SLAB);b.window(x,8,3,SOUTH,GLASS_PANE,SPRUCE_SLAB);}
        b.gableFrame(-9,3,12,11,STRIPPED_SPRUCE_LOG,GLASS_PANE,SPRUCE_SLAB);
        b.floor(-12,4,-6,7,SPRUCE_PLANKS);b.box(-12,4,4,-6,4,7,SPRUCE_SLAB);b.post(-12,7,1,3,SPRUCE_FENCE);b.post(-6,7,1,3,SPRUCE_FENCE);
        b.box(-19,1,-18,-16,2,-15,HAY_BLOCK);b.put(-3,1,-16,CRAFTING_TABLE);b.put(-3,1,-14,STONECUTTER);
        b.table(-7,1,-9,SPRUCE_FENCE,SPRUCE_SLAB);b.box(-18,7,-17,-16,8,-15,HAY_BLOCK);b.crates(15,-19,OAK_PLANKS);
        b.floor(16,3,23,8,OAK_PLANKS);b.crates(21,7,OAK_PLANKS);b.put(17,1,5,COMPOSTER);b.lamp(-6,4,-4);b.lamp(19,3,-11);
    }
    private static void inn(Builder b){
        b.room(-21,-21,21,5,0,5,WHITE_TERRACOTTA,OAK_PLANKS);b.room(-21,-21,21,5,6,4,WHITE_TERRACOTTA,OAK_PLANKS);
        b.timber(-21,-21,21,5,0,5,STRIPPED_DARK_OAK_LOG);b.timber(-21,-21,21,5,6,10,STRIPPED_DARK_OAK_LOG);
        for(int cx:new int[]{-14,0,14})b.gable(cx,-21,7,5,11,DARK_PRISMARINE_STAIRS,DARK_PRISMARINE_SLAB,WHITE_TERRACOTTA);
        for(int cx:new int[]{-14,0,14})b.gableFrame(cx,5,7,11,STRIPPED_DARK_OAK_LOG,LIGHT_BLUE_STAINED_GLASS_PANE,OAK_SLAB);
        for(int x:new int[]{-17,-10,-4,4,10,17}){b.window(x,2,5,SOUTH,GLASS_PANE,OAK_SLAB);b.window(x,8,5,SOUTH,GLASS_PANE,OAK_SLAB);}
        for(int z:new int[]{-16,-9,0})for(int x:new int[]{-21,21}){b.window(x,2,z,x<0?WEST:EAST,GLASS_PANE,OAK_SLAB);b.window(x,8,z,x<0?WEST:EAST,GLASS_PANE,OAK_SLAB);}
        b.floor(-19,6,19,10,OAK_PLANKS);for(int x:new int[]{-19,-7,7,19})b.post(x,10,1,4,DARK_OAK_FENCE);
        b.leanRoof(-20,6,20,11,5,4,OAK_PLANKS,OAK_SLAB);b.path(0,18,0,11,COBBLESTONE);
        b.door(0,1,5,DARK_OAK_DOOR,STRIPPED_DARK_OAK_LOG,SOUTH);b.stairs(17,0,1,6,NORTH,2,OAK_STAIRS);
        b.layer(-20,6,-2,20,-1,OAK_PLANKS);
        for(int x:new int[]{-7,7})b.box(x,7,-20,x,10,4,WHITE_TERRACOTTA);
        b.box(-20,7,-5,20,10,-5,WHITE_TERRACOTTA);
        for(int x:new int[]{-14,0,14})b.door(x,7,-5,OAK_DOOR,STRIPPED_OAK_LOG,SOUTH);
        b.door(-7,7,0,OAK_DOOR,STRIPPED_OAK_LOG,EAST);b.door(7,7,0,OAK_DOOR,STRIPPED_OAK_LOG,WEST);
        for(int x:new int[]{-18,-5,9}){b.bed(x,7,-17,RED_BED);b.bed(x,7,-9,WHITE_BED);b.window(x+3,8,-21,NORTH,GLASS_PANE,OAK_SLAB);}
        b.box(-19,1,-19,-10,1,-19,BARREL);b.put(-18,1,-16,SMOKER,SOUTH);b.put(-16,1,-16,CAULDRON);b.box(10,1,-19,18,3,-19,BOOKSHELF);
        for(int x:new int[]{-13,0,12}){b.table(x,1,-8,OAK_FENCE,OAK_SLAB);b.bench(x-1,1,-5,x+1,OAK_STAIRS,NORTH);b.put(x,3,-8,FLOWER_POT);}
        b.floor(-3,-17,3,3,RED_WOOL);b.lamp(-9,4,0);b.lamp(9,4,0);b.lamp(0,9,-1);
        b.floor(13,8,20,12,STONE_BRICKS);b.crates(17,10,OAK_PLANKS);b.planter(-15,12,OAK_SLAB,PINK_TULIP);
        b.box(-19,11,-19,-17,20,-17,BRICKS);b.put(-18,21,-18,BRICK_SLAB);
    }
    private static void hedges(Builder b){
        b.floor(-25,-25,25,15,MOSS_BLOCK);b.floor(-22,-22,22,12,COARSE_DIRT);
        for(int x:new int[]{-24,24})b.box(x,1,-24,x,4,14,AZALEA_LEAVES);b.box(-24,1,-24,24,4,-24,AZALEA_LEAVES);
        for(int z:new int[]{-17,-7,7})for(int side:new int[]{-1,1}){
            int ax=side<0?-21:6,bx=side<0?-6:21;b.box(ax,1,z,bx,3,z,AZALEA_LEAVES);
            int gap=side<0?(z==-7?-18:-10):(z==-7?18:10);b.box(gap-1,1,z,gap+1,3,z,AIR);
        }
        b.box(-13,1,-15,-13,3,-9,AZALEA_LEAVES);b.box(13,1,-15,13,3,-9,AZALEA_LEAVES);
        b.circle(0,0,-5,7,7,CHERRY_PLANKS);for(int x:new int[]{-5,5})for(int z:new int[]{-10,0})b.post(x,z,1,6,STRIPPED_CHERRY_LOG);
        for(int h=0;h<5;h++){b.circle(0,7+h,-5,8-h,8-h,DARK_PRISMARINE);b.ring(0,7+h,-5,8-h,8-h,.45,DARK_PRISMARINE_SLAB);}
        b.table(0,1,-7,CHERRY_FENCE,CHERRY_SLAB);b.put(0,3,-7,POTTED_CHERRY_SAPLING);b.bench(-4,1,-11,4,CHERRY_STAIRS,NORTH);
        b.path(0,18,0,3,STONE_BRICKS);b.path(-21,11,21,11,STONE_BRICKS);b.path(-21,11,-21,-21,STONE_BRICKS);b.path(21,11,21,-21,STONE_BRICKS);b.path(-21,-21,21,-21,STONE_BRICKS);
        b.path(0,3,-21,3,STONE_BRICKS);b.path(0,3,21,3,STONE_BRICKS);b.path(0,-13,0,-23,STONE_BRICKS);
        for(int x:new int[]{-19,19}){b.shelf(x-2,1,-23,x+2,STONE_BRICK_SLAB,POTTED_ALLIUM,POTTED_WHITE_TULIP);b.planter(x,14,CHERRY_SLAB,ROSE_BUSH);}
        b.lamp(-5,5,0);b.lamp(5,5,0);b.put(0,1,-24,DECORATED_POT);
    }
    private static void alpine(Builder b){
        b.room(-23,-21,23,3,0,5,SPRUCE_PLANKS,SPRUCE_PLANKS);b.room(-23,-21,23,3,6,4,WHITE_TERRACOTTA,SPRUCE_PLANKS);
        b.timber(-23,-21,23,3,0,5,STRIPPED_SPRUCE_LOG);b.timber(-23,-21,23,3,6,10,SPRUCE_LOG);
        for(int cx:new int[]{-15,0,15})b.gable(cx,-21,8,3,11,DEEPSLATE_TILE_STAIRS,DEEPSLATE_TILE_SLAB,SPRUCE_PLANKS);
        for(int cx:new int[]{-15,0,15})b.gableFrame(cx,3,8,11,STRIPPED_SPRUCE_LOG,LIGHT_BLUE_STAINED_GLASS_PANE,SPRUCE_SLAB);
        for(int x:new int[]{-17,-7,7,17}){b.window(x,2,3,SOUTH,GLASS_PANE,SPRUCE_SLAB);b.window(x,8,3,SOUTH,LIGHT_BLUE_STAINED_GLASS_PANE,SPRUCE_SLAB);}
        b.floor(-23,4,23,9,SPRUCE_PLANKS);b.layer(-23,6,4,23,9,SPRUCE_PLANKS);b.box(-23,7,9,-3,7,9,SPRUCE_FENCE);b.box(3,7,9,23,7,9,SPRUCE_FENCE);
        b.path(0,18,0,10,GRAVEL);b.door(0,1,3,SPRUCE_DOOR,STRIPPED_SPRUCE_LOG,SOUTH);b.door(0,7,3,SPRUCE_DOOR,STRIPPED_SPRUCE_LOG,SOUTH);
        b.stairs(-21,0,0,6,NORTH,2,SPRUCE_STAIRS);b.bed(-19,7,-16,LIGHT_BLUE_BED);b.bed(18,7,-16,WHITE_BED);
        b.box(-2,1,-20,2,4,-17,BRICKS);b.put(0,1,-16,SMOKER,SOUTH);b.box(-1,5,-20,1,25,-18,BRICKS);
        for(int x:new int[]{-11,11}){b.table(x,1,-8,SPRUCE_FENCE,SPRUCE_SLAB);b.bench(x-2,1,-5,x+2,SPRUCE_STAIRS,NORTH);b.put(x,3,-8,FLOWER_POT);}
        b.box(-20,1,-19,-15,3,-19,BOOKSHELF);b.put(20,1,-19,BARREL);b.lamp(0,4,-5);b.lamp(0,9,-5);
        b.floor(13,8,21,12,COBBLESTONE);b.crates(18,10,SPRUCE_PLANKS);b.put(14,1,10,CAULDRON);b.bench(-18,1,8,-12,SPRUCE_STAIRS,SOUTH);
    }
    private static void frozenBath(Builder b){
        b.floor(-25,-25,25,14,POLISHED_DIORITE);b.pool(-9,-15,9,-1,STONE_BRICKS);
        for(int side:new int[]{-1,1}){int ax=side<0?-25:13,bx=side<0?-13:25;
            b.room(ax,-23,bx,8,0,5,STONE_BRICKS,SPRUCE_PLANKS);b.gable((ax+bx)/2,-23,6,8,6,DEEPSLATE_TILE_STAIRS,DEEPSLATE_TILE_SLAB,PACKED_ICE);
            b.door((ax+bx)/2,1,8,SPRUCE_DOOR,CHISELED_STONE_BRICKS,SOUTH);b.window((ax+bx)/2,2,-23,NORTH,LIGHT_BLUE_STAINED_GLASS_PANE,STONE_BRICK_SLAB);
            b.bench(ax+2,1,-19,bx-2,SPRUCE_STAIRS,NORTH);b.put(ax+2,1,-7,CAULDRON);b.put(bx-2,1,-7,BARREL);b.lamp((ax+bx)/2,4,0);
        }
        for(int z:new int[]{-20,-11,-2,7})for(int x:new int[]{-11,11}){b.post(x,z,1,7,QUARTZ_PILLAR);b.put(x,8,z,QUARTZ_SLAB);}
        b.box(-11,8,-20,-11,8,7,QUARTZ_SLAB);b.box(11,8,-20,11,8,7,QUARTZ_SLAB);
        b.path(0,18,0,12,STONE_BRICKS);b.path(-19,12,19,12,STONE_BRICKS);b.path(-19,12,-19,9,STONE_BRICKS);b.path(19,12,19,9,STONE_BRICKS);
        b.floor(-8,-24,8,-19,STONE_BRICKS);b.box(-8,1,-24,8,5,-24,PACKED_ICE);b.put(0,1,-23,CAULDRON);b.shelf(-6,2,-23,6,QUARTZ_SLAB,POTTED_WHITE_TULIP,POTTED_BLUE_ORCHID);
        b.path(-11,10,-11,-21,STONE_BRICKS);b.path(11,10,11,-21,STONE_BRICKS);b.path(-11,-21,11,-21,STONE_BRICKS);
        b.bench(-6,1,5,6,STONE_BRICK_STAIRS,NORTH);b.put(-8,1,7,DECORATED_POT);b.put(8,1,7,DECORATED_POT);
    }
    private static void outpost(Builder b){
        b.tower(-15,-12);b.tower(15,-12);b.box(-24,1,-22,24,5,-22,STONE_BRICKS);
        b.layer(-15,12,-14,15,-10,SPRUCE_PLANKS);b.box(-8,13,-14,8,13,-14,SPRUCE_FENCE);b.box(-8,13,-10,8,13,-10,SPRUCE_FENCE);
        b.box(-11,13,-13,-8,15,-11,AIR);b.box(8,13,-13,11,15,-11,AIR);
        b.floor(-23,-4,23,14,COBBLESTONE);b.path(0,18,0,6,GRAVEL);b.path(-15,6,15,6,GRAVEL);
        b.path(-15,6,-15,-5,GRAVEL);b.path(15,6,15,-5,GRAVEL);
        b.stairs(0,0,8,12,NORTH,2,STONE_BRICK_STAIRS);b.layer(-6,12,-10,1,-4,SPRUCE_PLANKS);
        b.box(-6,13,-10,1,15,-10,AIR);
        b.room(-14,7,-2,14,0,3,SPRUCE_PLANKS,SPRUCE_PLANKS);b.leanRoof(-15,6,-1,15,5,3,SPRUCE_PLANKS,SPRUCE_SLAB);
        b.room(5,7,18,14,0,3,SPRUCE_PLANKS,SPRUCE_PLANKS);b.leanRoof(4,6,19,15,5,3,SPRUCE_PLANKS,SPRUCE_SLAB);
        b.door(-8,1,7,SPRUCE_DOOR,STRIPPED_SPRUCE_LOG,NORTH);b.door(11,1,7,SPRUCE_DOOR,STRIPPED_SPRUCE_LOG,NORTH);
        b.put(-11,1,12,SMOKER,NORTH);b.put(16,1,12,CRAFTING_TABLE);b.put(7,1,12,ANVIL);
        b.bed(-18,1,-15,WHITE_BED);b.bed(18,1,-15,LIGHT_BLUE_BED);b.lamp(-15,15,-9);b.lamp(15,15,-9);
        b.crates(-22,1,SPRUCE_PLANKS);b.crates(21,1,SPRUCE_PLANKS);b.put(3,1,1,CAMPFIRE);b.bench(2,1,4,4,SPRUCE_STAIRS,NORTH);
    }
    private static void mine(Builder b){
        for(int y=0;y<=17;y++)b.circle(0,y,-12,25-y*.65,17-y*.55,y%4==0?PACKED_ICE:STONE);
        b.box(-3,1,-25,3,4,9,AIR);b.floor(-3,-25,3,11,SPRUCE_PLANKS);
        b.box(-21,1,-12,21,4,-6,AIR);b.floor(-21,-12,21,-6,COBBLESTONE);
        b.box(-15,1,-21,15,4,-15,AIR);b.floor(-15,-21,15,-15,COBBLESTONE);
        b.box(-12,1,-21,-8,4,-7,AIR);b.box(8,1,-21,12,4,-7,AIR);b.floor(-12,-21,-8,-7,COBBLESTONE);b.floor(8,-21,12,-7,COBBLESTONE);
        for(int z=-23;z<=7;z+=5){b.post(-3,z,1,4,STRIPPED_SPRUCE_LOG);b.post(3,z,1,4,STRIPPED_SPRUCE_LOG);b.beam(-3,5,z,3,5,z,SPRUCE_LOG);b.lamp(2,4,z);}
        for(int z=-23;z<=8;z++)b.put(0,1,z,RAIL,NORTH);
        for(int x:new int[]{-19,19}){b.put(x,1,-7,RAW_IRON_BLOCK);b.put(x,2,-7,IRON_ORE);b.put(x,1,-11,COAL_ORE);}
        b.arch(0,9,5,5,3,STONE_BRICKS);b.floor(-23,7,23,14,COARSE_DIRT);b.path(0,18,0,10,GRAVEL);
        for(int side:new int[]{-1,1}){int ax=side<0?-23:11,bx=side<0?-11:23;
            b.room(ax,5,bx,14,0,3,SPRUCE_PLANKS,SPRUCE_PLANKS);b.leanRoof(ax-1,4,bx+1,15,5,3,SPRUCE_PLANKS,SPRUCE_SLAB);
            b.door((ax+bx)/2,1,14,SPRUCE_DOOR,STRIPPED_SPRUCE_LOG,SOUTH);b.put(ax+2,1,7,CRAFTING_TABLE);b.put(bx-2,1,7,FURNACE,SOUTH);
        }
        b.path(-17,16,17,16,GRAVEL);b.path(0,16,0,18,GRAVEL);b.crates(-20,-10,SPRUCE_PLANKS);b.crates(18,-10,SPRUCE_PLANKS);
        b.put(-10,1,-17,ANVIL);b.put(10,1,-17,STONECUTTER);b.lamp(-10,4,-19);b.lamp(10,4,-19);
    }
    private static void aurora(Builder b){
        b.room(-11,-25,11,7,0,9,STRIPPED_SPRUCE_LOG,SPRUCE_PLANKS);b.gable(0,-25,11,7,10,DEEPSLATE_TILE_STAIRS,DEEPSLATE_TILE_SLAB,SPRUCE_PLANKS);
        for(int side:new int[]{-1,1}){int ax=side<0?-23:12,bx=side<0?-12:23;
            b.room(ax,-18,bx,-6,0,4,STONE_BRICKS,SPRUCE_PLANKS);b.gable((ax+bx)/2,-18,6,-6,5,SPRUCE_STAIRS,SPRUCE_SLAB,SPRUCE_PLANKS);
            b.box(side<0?-13:10,1,-13,side<0?-10:13,3,-11,AIR);b.put(side*18,1,-16,BARREL);b.lamp(side*15,3,-10);
            b.window(side*11,3,-5,side<0?WEST:EAST,LIGHT_BLUE_STAINED_GLASS_PANE,STONE_BRICK_SLAB);
            b.window(side*11,3,-20,side<0?WEST:EAST,LIGHT_BLUE_STAINED_GLASS_PANE,STONE_BRICK_SLAB);
        }
        b.door(0,1,7,SPRUCE_DOOR,STRIPPED_SPRUCE_LOG,SOUTH);b.path(0,18,0,8,STONE_BRICKS);
        b.gableFrame(0,7,11,10,STRIPPED_SPRUCE_LOG,LIGHT_BLUE_STAINED_GLASS_PANE,SPRUCE_SLAB);
        b.floor(-2,-23,2,6,BLUE_WOOL);b.table(-6,1,-7,SPRUCE_FENCE,SPRUCE_SLAB);b.table(6,1,-7,SPRUCE_FENCE,SPRUCE_SLAB);
        b.bench(-8,1,-3,-4,SPRUCE_STAIRS,NORTH);b.bench(4,1,-3,8,SPRUCE_STAIRS,NORTH);
        b.box(-3,1,-23,3,1,-22,CHISELED_QUARTZ_BLOCK);b.put(0,2,-23,SEA_LANTERN);b.box(-3,5,-25,3,9,-25,LIGHT_BLUE_STAINED_GLASS);
        b.lamp(-6,7,0);b.lamp(6,7,0);b.box(-10,3,8,-8,6,8,STRIPPED_SPRUCE_LOG);b.box(8,3,8,10,6,8,STRIPPED_SPRUCE_LOG);
        b.floor(-23,6,-14,13,SPRUCE_PLANKS);b.leanRoof(-24,5,-13,14,5,4,SPRUCE_PLANKS,SPRUCE_SLAB);b.crates(-21,8,SPRUCE_PLANKS);
        b.path(-18,15,0,15,GRAVEL);b.path(-18,15,-18,11,GRAVEL);
    }
    private static void manor(Builder b){
        b.room(-23,-23,0,5,0,5,WHITE_TERRACOTTA,DARK_OAK_PLANKS);b.room(-23,-23,0,5,6,4,WHITE_TERRACOTTA,DARK_OAK_PLANKS);
        b.room(1,-20,23,-2,0,5,MUD_BRICKS,DARK_OAK_PLANKS);b.room(1,-20,23,-2,6,4,MUD_BRICKS,DARK_OAK_PLANKS);
        b.timber(-23,-23,0,5,0,5,STRIPPED_DARK_OAK_LOG);b.timber(-23,-23,0,5,6,10,DARK_OAK_LOG);
        b.timber(1,-20,23,-2,0,5,STRIPPED_DARK_OAK_LOG);b.timber(1,-20,23,-2,6,10,DARK_OAK_LOG);
        b.gable(-12,-23,12,5,11,DARK_OAK_STAIRS,DARK_OAK_SLAB,WHITE_TERRACOTTA);b.gable(12,-20,11,-2,11,DARK_OAK_STAIRS,DARK_OAK_SLAB,MUD_BRICKS);
        b.box(-1,1,-12,2,3,-9,AIR);b.box(-1,7,-12,2,9,-9,AIR);b.path(0,18,0,10,MOSSY_COBBLESTONE);b.path(0,10,-11,10,MOSSY_COBBLESTONE);b.path(-11,10,-11,6,MOSSY_COBBLESTONE);
        b.door(-11,1,5,DARK_OAK_DOOR,STRIPPED_DARK_OAK_LOG,SOUTH);b.stairs(-21,0,2,6,NORTH,2,DARK_OAK_STAIRS);
        b.window(-23,2,-11,WEST,GLASS_PANE,DARK_OAK_SLAB);b.window(-23,8,-11,WEST,GLASS_PANE,DARK_OAK_SLAB);b.window(23,2,-10,EAST,GLASS_PANE,DARK_OAK_SLAB);
        for(int x:new int[]{-18,-5}){b.window(x,2,5,SOUTH,GLASS_PANE,DARK_OAK_SLAB);b.window(x,8,5,SOUTH,GLASS_PANE,DARK_OAK_SLAB);}
        for(int x:new int[]{7,17}){b.window(x,2,-2,SOUTH,GLASS_PANE,DARK_OAK_SLAB);b.window(x,8,-2,SOUTH,GLASS_PANE,DARK_OAK_SLAB);}
        b.gableFrame(-12,5,12,11,STRIPPED_DARK_OAK_LOG,GLASS_PANE,DARK_OAK_SLAB);b.gableFrame(12,-2,11,11,STRIPPED_DARK_OAK_LOG,GLASS_PANE,DARK_OAK_SLAB);
        b.box(-21,1,-21,-15,3,-21,BOOKSHELF);b.put(-4,1,-21,SMOKER,SOUTH);b.table(-7,1,-7,DARK_OAK_FENCE,DARK_OAK_SLAB);b.put(-7,3,-7,POTTED_FERN);
        b.bed(-19,7,-17,GREEN_BED);b.bed(19,7,-16,WHITE_BED);b.box(4,7,-18,8,9,-18,BOOKSHELF);b.lamp(12,9,-7);b.lamp(-12,4,0);
        b.pool(6,4,19,12,MOSSY_STONE_BRICKS);b.planter(3,4,DARK_OAK_SLAB,FERN);b.planter(23,7,DARK_OAK_SLAB,FLOWERING_AZALEA);
        b.floor(13,13,23,16,DARK_OAK_PLANKS);b.path(0,16,17,16,MOSSY_COBBLESTONE);b.crates(21,15,DARK_OAK_PLANKS);
        b.box(-25,1,-19,-24,6,-10,AZALEA_LEAVES);b.box(23,7,-19,24,9,-15,AZALEA_LEAVES);b.box(-21,11,4,-17,11,5,MOSS_CARPET);
    }
    private static void canopy(Builder b){
        b.tree(-21,-9,24,DARK_OAK_LOG,DARK_OAK_LEAVES);b.tree(20,-7,27,OAK_LOG,OAK_LEAVES);b.tree(-3,-20,25,DARK_OAK_LOG,DARK_OAK_LEAVES);
        b.layer(-8,8,-14,6,8,OAK_PLANKS);b.layer(-20,8,-15,-10,-3,OAK_PLANKS);b.layer(6,8,-13,18,-1,OAK_PLANKS);
        b.path(-15,-5,12,-5,8,OAK_PLANKS);b.path(-4,7,-4,-12,8,OAK_PLANKS);
        b.room(-20,-15,-10,-3,8,4,OAK_PLANKS,OAK_PLANKS);b.gable(-15,-15,5,-3,13,DARK_OAK_STAIRS,DARK_OAK_SLAB,OAK_PLANKS);
        b.room(7,-13,17,-1,8,4,OAK_PLANKS,OAK_PLANKS);b.gable(12,-13,5,-1,13,DARK_OAK_STAIRS,DARK_OAK_SLAB,OAK_PLANKS);
        b.door(-15,9,-3,OAK_DOOR,STRIPPED_OAK_LOG,SOUTH);b.door(12,9,-1,OAK_DOOR,STRIPPED_OAK_LOG,SOUTH);
        b.path(-15,-1,0,-1,8,OAK_PLANKS);b.path(-15,-1,-15,-2,8,OAK_PLANKS);
        b.path(12,1,0,1,8,OAK_PLANKS);b.path(12,1,12,0,8,OAK_PLANKS);
        b.box(-8,9,-14,-8,9,8,OAK_FENCE);b.box(6,9,-14,6,9,8,OAK_FENCE);b.box(-8,9,8,-2,9,8,OAK_FENCE);
        b.box(-8,9,-2,-8,11,0,AIR);b.box(6,9,0,6,11,2,AIR);
        b.floor(-2,8,4,18,COARSE_DIRT);b.path(0,18,0,17,MOSSY_COBBLESTONE);b.stairs(0,0,16,8,NORTH,3,OAK_STAIRS);
        b.box(0,9,7,2,11,8,AIR);b.layer(-2,8,7,4,8,OAK_PLANKS);b.bed(-18,9,-12,GREEN_BED);b.bed(15,9,-10,WHITE_BED);
        b.table(-4,9,-9,OAK_FENCE,OAK_SLAB);b.put(-4,11,-9,POTTED_FERN);b.put(4,9,-11,CRAFTING_TABLE);b.lamp(-4,11,2);
        b.floor(9,6,17,12,OAK_PLANKS);b.post(9,6,1,4,OAK_FENCE);b.post(17,6,1,4,OAK_FENCE);b.leanRoof(8,5,18,13,5,4,OAK_PLANKS,OAK_SLAB);
        b.path(2,17,13,17,COARSE_DIRT);b.path(13,17,13,13,COARSE_DIRT);b.crates(15,10,OAK_PLANKS);b.lamp(12,3,8);
    }
    private static void mushrooms(Builder b){
        b.mushroom(-16,-12,6,RED_MUSHROOM_BLOCK);b.mushroom(15,-14,7,BROWN_MUSHROOM_BLOCK);
        b.mushroom(-16,7,5,BROWN_MUSHROOM_BLOCK);b.mushroom(15,7,5,RED_MUSHROOM_BLOCK);
        b.room(-7,-24,7,-16,0,4,MUD_BRICKS,OAK_PLANKS);b.gable(0,-24,7,-16,5,DARK_OAK_STAIRS,DARK_OAK_SLAB,MUD_BRICKS);
        b.door(0,1,-16,OAK_DOOR,STRIPPED_OAK_LOG,SOUTH);b.put(-5,1,-22,SMOKER,SOUTH);b.put(5,1,-22,BARREL);b.lamp(0,3,-19);
        b.path(0,18,0,-15,MOSSY_COBBLESTONE);b.path(0,14,-16,14,MOSSY_COBBLESTONE);b.path(0,14,15,14,MOSSY_COBBLESTONE);
        b.path(-16,14,-16,13,MOSSY_COBBLESTONE);b.path(15,14,15,13,MOSSY_COBBLESTONE);b.path(0,-2,-16,-2,MOSSY_COBBLESTONE);b.path(0,-2,15,-2,MOSSY_COBBLESTONE);
        b.path(-16,-2,-16,-5,MOSSY_COBBLESTONE);b.path(15,-2,15,-6,MOSSY_COBBLESTONE);
        b.circle(0,0,3,5,5,COBBLESTONE);b.put(0,1,3,CAMPFIRE);b.bench(-3,1,6,3,OAK_STAIRS,NORTH);
        for(int x:new int[]{-7,7})for(int z:new int[]{-8,10}){b.planter(x,z,MOSSY_COBBLESTONE,RED_MUSHROOM);b.put(x+1,1,z,BROWN_MUSHROOM);}
        b.shelf(-22,1,14,-18,OAK_SLAB,POTTED_FERN,POTTED_RED_MUSHROOM);b.crates(21,14,OAK_PLANKS);
    }
    private static void aqueduct(Builder b){
        b.floor(-26,-18,26,-10,MOSSY_STONE_BRICKS);b.floor(-24,4,24,13,COARSE_DIRT);
        for(int cx:new int[]{-18,-6,6,18})b.arch(cx,-4,5,5,4,MOSSY_STONE_BRICKS);
        b.box(-25,9,-5,25,9,-3,MOSSY_STONE_BRICKS);b.layer(-25,10,-6,25,-6,STONE_BRICK_SLAB);b.layer(-25,10,-2,25,-2,STONE_BRICK_SLAB);
        b.box(-25,10,-5,25,10,-3,MOSSY_STONE_BRICKS);b.box(-24,10,-4,24,10,-4,WATER);
        b.box(-25,11,-5,25,11,-5,MOSSY_STONE_BRICKS);b.box(-25,11,-3,25,11,-3,MOSSY_STONE_BRICKS);
        b.layer(-24,9,-9,24,-7,STONE_BRICKS);b.box(-24,10,-9,24,10,-9,MOSSY_COBBLESTONE_WALL);
        b.stairs(-28,0,4,9,NORTH,2,MOSSY_STONE_BRICK_STAIRS);b.layer(-28,9,-8,-21,-5,STONE_BRICKS);
        b.path(-24,6,-28,6,MOSSY_COBBLESTONE);
        for(int side:new int[]{-1,1}){int x=side*19;
            b.room(x-5,-20,x+5,-10,0,4,MOSSY_STONE_BRICKS,STONE_BRICKS);b.leanRoof(x-6,-21,x+6,-9,6,4,STONE_BRICKS,STONE_BRICK_SLAB);
            b.door(x,1,-10,OAK_DOOR,MOSSY_STONE_BRICKS,SOUTH);b.put(x+3,1,-18,DECORATED_POT);b.box(x-4,1,-19,x-2,2,-19,BOOKSHELF);
            b.floor(x-4,5,x+4,12,MOSSY_STONE_BRICKS);b.box(x-4,1,5,x-4,4,11,MOSSY_STONE_BRICKS);b.put(x+2,1,10,BARREL);
        }
        b.path(0,18,0,-13,MOSSY_COBBLESTONE);b.path(-19,-8,19,-8,MOSSY_COBBLESTONE);b.path(-19,14,19,14,MOSSY_COBBLESTONE);
        b.path(-19,14,-19,12,MOSSY_COBBLESTONE);b.path(19,14,19,12,MOSSY_COBBLESTONE);
        b.box(12,8,-5,15,9,-3,MOSS_BLOCK);b.box(12,10,-4,15,10,-4,STONE_BRICKS);
        b.put(7,1,6,CRACKED_STONE_BRICKS);b.put(8,1,7,MOSSY_COBBLESTONE);b.put(7,2,6,STONE_BRICK_SLAB);b.lamp(-21,3,-13);
    }
    private static void shrine(Builder b){
        b.floor(-25,-25,25,14,MOSSY_STONE_BRICKS);b.tree(-3,-11,29,DARK_OAK_LOG,DARK_OAK_LEAVES);
        b.arch(0,7,7,6,5,CHISELED_STONE_BRICKS);b.box(-24,1,-24,24,6,-24,MOSSY_STONE_BRICKS);
        for(int side:new int[]{-1,1}){int x=side*18;
            b.room(x-6,-21,x+6,-7,0,5,CRACKED_STONE_BRICKS,STONE_BRICKS);b.gable(x,-21,6,-7,6,MOSSY_STONE_BRICK_STAIRS,MOSSY_STONE_BRICK_SLAB,MOSSY_STONE_BRICKS);
            b.door(x,1,-7,DARK_OAK_DOOR,CHISELED_STONE_BRICKS,SOUTH);b.window(x,2,-21,NORTH,GLASS_PANE,STONE_BRICK_SLAB);
            b.put(x-3,1,-19,BOOKSHELF);b.put(x+3,1,-19,DECORATED_POT);b.lamp(x,4,-10);
            b.floor(x-5,5,x+5,12,MOSSY_STONE_BRICKS);b.box(x-5,1,5,x-5,4,11,MOSSY_STONE_BRICKS);b.shelf(x-3,2,11,x+3,STONE_BRICK_SLAB,POTTED_FERN,POTTED_RED_MUSHROOM);
        }
        b.path(0,18,0,-21,MOSSY_COBBLESTONE);b.path(-18,-4,18,-4,MOSSY_COBBLESTONE);b.path(-18,14,18,14,MOSSY_COBBLESTONE);
        b.path(-18,14,-18,12,MOSSY_COBBLESTONE);b.path(18,14,18,12,MOSSY_COBBLESTONE);
        b.floor(-6,-24,6,-19,CHISELED_STONE_BRICKS);b.put(0,1,-23,LECTERN);b.put(-5,1,-22,DECORATED_POT);b.put(5,1,-22,DECORATED_POT);
        for(int side:new int[]{-1,1})b.beam(-3,5,-11,side*12,1,-3,DARK_OAK_LOG);
        b.box(19,6,-20,22,6,-15,MOSS_BLOCK);b.box(-24,2,-19,-24,5,-12,AZALEA_LEAVES);
    }
    private static void market(Builder b){
        b.floor(-25,-25,25,15,QUARTZ_BRICKS);b.room(-15,-25,15,-9,0,5,CALCITE,QUARTZ_BRICKS);b.room(-15,-25,15,-9,6,4,CALCITE,QUARTZ_BRICKS);
        b.timber(-15,-25,15,-9,0,5,QUARTZ_PILLAR);b.timber(-15,-25,15,-9,6,10,QUARTZ_PILLAR);
        b.gable(0,-25,15,-9,11,WAXED_CUT_COPPER_STAIRS,WAXED_CUT_COPPER_SLAB,CALCITE);
        for(int x:new int[]{-10,10}){b.window(x,2,-9,SOUTH,LIGHT_BLUE_STAINED_GLASS_PANE,QUARTZ_SLAB);b.window(x,8,-9,SOUTH,LIGHT_BLUE_STAINED_GLASS_PANE,QUARTZ_SLAB);}
        b.window(0,8,-9,SOUTH,LIGHT_BLUE_STAINED_GLASS_PANE,QUARTZ_SLAB);b.window(0,16,-9,SOUTH,LIGHT_BLUE_STAINED_GLASS_PANE,QUARTZ_SLAB);
        b.door(0,1,-9,SPRUCE_DOOR,CHISELED_QUARTZ_BLOCK,SOUTH);b.stairs(11,0,-11,6,NORTH,2,QUARTZ_STAIRS);
        for(int side:new int[]{-1,1})for(int z:new int[]{-16,5}){int x=side*19;
            b.box(x-4,1,z-4,x+4,4,z-4,CALCITE);b.post(x-4,z+3,1,4,QUARTZ_PILLAR);b.post(x+4,z+3,1,4,QUARTZ_PILLAR);
            b.leanRoof(x-5,z-5,x+5,z+4,5,4,WAXED_CUT_COPPER,WAXED_CUT_COPPER_SLAB);
            b.box(x-3,1,z+1,x+3,1,z+1,SPRUCE_SLAB);b.shelf(x-3,2,z-3,x+3,SPRUCE_SLAB,POTTED_ALLIUM,FLOWER_POT);
            b.put(x+3,1,z-2,BARREL);b.put(x-3,1,z-2,CRAFTING_TABLE);b.lamp(x,3,z);
        }
        b.path(0,18,0,-8,POLISHED_ANDESITE);b.path(-19,12,19,12,POLISHED_ANDESITE);b.path(-19,-8,19,-8,POLISHED_ANDESITE);
        b.box(-13,1,-23,-8,3,-23,BOOKSHELF);b.put(12,1,-23,CAULDRON);b.put(-12,7,-23,LECTERN);b.box(4,7,-23,12,8,-23,BOOKSHELF);
        b.table(-3,1,1,SPRUCE_FENCE,SPRUCE_SLAB);b.bench(1,1,4,5,SMOOTH_QUARTZ_STAIRS,SOUTH);b.put(4,1,0,DECORATED_POT);
        b.box(-25,1,-23,-23,3,-21,MOSS_BLOCK);b.put(23,1,12,POTTED_ALLIUM);
    }
    private static void monastery(Builder b){
        b.floor(-26,-26,26,15,QUARTZ_BRICKS);b.pool(-7,-13,7,-1,CHISELED_QUARTZ_BLOCK);
        for(int side:new int[]{-1,1}){int x=side*19;
            b.room(x-6,-24,x+6,8,0,5,CALCITE,QUARTZ_BRICKS);b.gable(x,-24,6,8,6,WAXED_OXIDIZED_CUT_COPPER_STAIRS,WAXED_OXIDIZED_CUT_COPPER_SLAB,CALCITE);
            b.door(x,1,8,SPRUCE_DOOR,QUARTZ_PILLAR,SOUTH);b.window(x,2,-24,NORTH,LIGHT_BLUE_STAINED_GLASS_PANE,QUARTZ_SLAB);
            b.bed(x-3,1,-20,WHITE_BED);b.box(x+3,1,-22,x+3,3,-17,BOOKSHELF);b.table(x-3,1,-6,SPRUCE_FENCE,SPRUCE_SLAB);
            for(int z:new int[]{-20,-12,-4,5}){b.post(side*10,z,1,6,QUARTZ_PILLAR);b.put(side*10,7,z,QUARTZ_SLAB);}
            b.box(side*10,7,-24,side*10,7,8,QUARTZ_SLAB);b.lamp(x,4,0);
        }
        b.room(-11,-27,11,-18,0,8,QUARTZ_BRICKS,QUARTZ_BRICKS);b.gable(0,-27,11,-18,9,WAXED_CUT_COPPER_STAIRS,WAXED_CUT_COPPER_SLAB,QUARTZ_BRICKS);
        b.door(0,1,-18,SPRUCE_DOOR,CHISELED_QUARTZ_BLOCK,SOUTH);b.put(0,1,-25,LECTERN);b.put(0,2,-26,SEA_LANTERN);b.bench(-8,1,-21,-4,SMOOTH_QUARTZ_STAIRS,NORTH);b.bench(4,1,-21,8,SMOOTH_QUARTZ_STAIRS,NORTH);
        b.path(0,18,0,12,POLISHED_ANDESITE);b.path(-19,12,19,12,POLISHED_ANDESITE);b.path(-10,12,-10,-16,POLISHED_ANDESITE);b.path(10,12,10,-16,POLISHED_ANDESITE);b.path(-10,-16,10,-16,POLISHED_ANDESITE);
        b.shelf(-6,1,7,6,QUARTZ_SLAB,POTTED_ALLIUM,POTTED_WHITE_TULIP);b.put(-7,1,10,DECORATED_POT);b.put(7,1,10,DECORATED_POT);
    }
    private static void observatory(Builder b){
        for(int y=0;y<=10;y++)b.circle(0,y,-9,12,12,y==0?QUARTZ_BRICKS:CALCITE);
        for(int y=1;y<=10;y++)b.circle(0,y,-9,10.5,10.5,AIR);
        for(int h=0;h<9;h++){double r=13*Math.cos(h/9.0*Math.PI/2);b.circle(0,11+h,-9,r,r,h%3==0?WAXED_CUT_COPPER:WAXED_OXIDIZED_CUT_COPPER);}
        b.circle(0,8,-9,10.8,10.8,QUARTZ_BRICKS);b.circle(0,8,-9,6.5,6.5,AIR);b.ring(0,9,-9,6.8,6.8,.5,WAXED_CUT_COPPER_SLAB);
        b.box(-2,1,0,2,5,4,AIR);b.door(0,1,2,SPRUCE_DOOR,CHISELED_QUARTZ_BLOCK,SOUTH);b.floor(-3,2,3,6,QUARTZ_BRICKS);
        b.stairs(-9,0,-1,8,NORTH,2,QUARTZ_STAIRS);b.box(-9,9,-10,-7,12,-8,AIR);
        b.box(-2,1,-12,2,2,-8,CHISELED_QUARTZ_BLOCK);b.box(0,3,-10,0,6,-10,WAXED_CUT_COPPER);
        for(int i=0;i<64;i++){double a=i*Math.PI/32;b.put((int)Math.round(Math.cos(a)*4),7+(int)Math.round(Math.sin(a)*4),-10,WAXED_EXPOSED_CUT_COPPER);}
        b.put(0,7,-10,AMETHYST_BLOCK);b.put(6,1,-13,LECTERN);b.box(-8,1,-16,-5,3,-16,BOOKSHELF);
        for(int side:new int[]{-1,1}){int x=side*20;
            b.room(x-6,-5,x+6,12,0,5,CALCITE,QUARTZ_BRICKS);b.gable(x,-5,6,12,6,WAXED_CUT_COPPER_STAIRS,WAXED_CUT_COPPER_SLAB,CALCITE);
            b.door(x,1,12,SPRUCE_DOOR,QUARTZ_PILLAR,SOUTH);b.window(x,2,-5,NORTH,GLASS_PANE,QUARTZ_SLAB);
            b.table(x-3,1,0,SPRUCE_FENCE,SPRUCE_SLAB);b.put(x-3,3,0,FLOWER_POT);b.box(x+3,1,-3,x+3,3,3,BOOKSHELF);b.lamp(x,4,8);
        }
        b.path(0,18,0,7,POLISHED_ANDESITE);b.path(-20,16,20,16,POLISHED_ANDESITE);b.path(-20,16,-20,13,POLISHED_ANDESITE);b.path(20,16,20,13,POLISHED_ANDESITE);
        b.lamp(-7,7,-2);b.lamp(7,7,-2);b.put(0,20,-9,AMETHYST_BLOCK);
    }
    private static void theatre(Builder b){
        b.floor(-27,-26,27,14,POLISHED_ANDESITE);b.circle(0,0,-4,12,11,QUARTZ_BRICKS);
        for(int row=0;row<5;row++){
            double r=14+row*2;int y=1+row;
            for(int x=-26;x<=26;x++)for(int z=-25;z<=-2;z++)if(Math.abs(Math.hypot(x,z+4)-r)<.8){
                b.box(x,0,z,x,y,z,STONE_BRICKS);b.put(x,y,z,SMOOTH_QUARTZ_STAIRS,SOUTH);
            }
        }
        b.floor(-9,-19,9,-13,QUARTZ_BRICKS);b.box(-9,1,-24,9,7,-24,CALCITE);b.box(-9,1,-23,-9,6,-17,QUARTZ_PILLAR);b.box(9,1,-23,9,6,-17,QUARTZ_PILLAR);
        b.box(-9,8,-24,9,8,-17,QUARTZ_SLAB);b.put(0,1,-23,LECTERN);b.box(-6,1,-22,-4,3,-22,BOOKSHELF);b.put(6,1,-22,BARREL);
        for(int side:new int[]{-1,1}){int x=side*20;
            b.room(x-6,-14,x+6,-3,0,4,CALCITE,QUARTZ_BRICKS);b.leanRoof(x-7,-15,x+7,-2,6,4,QUARTZ_BRICKS,QUARTZ_SLAB);
            b.door(x,1,-3,SPRUCE_DOOR,CHISELED_QUARTZ_BLOCK,SOUTH);b.put(x+3,1,-12,BOOKSHELF);b.put(x-3,1,-12,DECORATED_POT);
        }
        b.room(-20,5,-7,14,0,3,CALCITE,QUARTZ_BRICKS);b.room(7,5,20,14,0,3,CALCITE,QUARTZ_BRICKS);
        b.leanRoof(-21,4,-6,15,5,4,QUARTZ_BRICKS,QUARTZ_SLAB);b.leanRoof(6,4,21,15,5,4,QUARTZ_BRICKS,QUARTZ_SLAB);
        b.door(-13,1,14,SPRUCE_DOOR,QUARTZ_PILLAR,SOUTH);b.door(13,1,14,SPRUCE_DOOR,QUARTZ_PILLAR,SOUTH);
        b.path(0,18,0,-23,POLISHED_ANDESITE);b.path(-20,1,20,1,POLISHED_ANDESITE);b.path(-13,16,13,16,POLISHED_ANDESITE);
        b.path(-20,1,-20,-2,POLISHED_ANDESITE);b.path(20,1,20,-2,POLISHED_ANDESITE);
        b.box(21,4,-24,26,8,-20,AIR);b.put(23,1,-23,MOSS_BLOCK);b.put(22,1,-22,WAXED_OXIDIZED_CUT_COPPER_STAIRS,EAST);
        b.lamp(-19,3,-7);b.lamp(19,3,-7);b.shelf(-18,2,6,-10,QUARTZ_SLAB,POTTED_ALLIUM,FLOWER_POT);
    }
    private static void moonBath(Builder b){
        b.floor(-26,-27,26,15,QUARTZ_BRICKS);b.pool(-9,-17,9,-5,CHISELED_QUARTZ_BLOCK);b.pool(-8,1,8,11,QUARTZ_BRICKS);
        b.layer(-2,1,-1,2,3,WAXED_CUT_COPPER_SLAB);b.box(-2,0,-1,2,0,3,QUARTZ_BRICKS);
        for(int side:new int[]{-1,1}){int x=side*19;
            b.room(x-6,-24,x+6,10,0,4,CALCITE,QUARTZ_BRICKS);b.leanRoof(x-7,-25,x+7,11,6,6,QUARTZ_BRICKS,QUARTZ_SLAB);
            b.door(x,1,10,SPRUCE_DOOR,QUARTZ_PILLAR,SOUTH);b.window(x,2,-24,NORTH,LIGHT_BLUE_STAINED_GLASS_PANE,QUARTZ_SLAB);
            b.bench(x-4,1,-21,x+4,SMOOTH_QUARTZ_STAIRS,NORTH);b.put(x-3,1,-10,CAULDRON);b.put(x+3,1,-10,DECORATED_POT);
            b.shelf(x-4,2,1,x+4,QUARTZ_SLAB,POTTED_ALLIUM,POTTED_WHITE_TULIP);b.lamp(x,3,6);
            for(int z:new int[]{-20,-10,0,10}){b.post(side*11,z,1,6,QUARTZ_PILLAR);b.put(side*11,7,z,WAXED_OXIDIZED_CUT_COPPER_SLAB);}
        }
        b.floor(-8,-27,8,-20,CALCITE);b.box(-8,1,-27,8,5,-27,QUARTZ_BRICKS);b.shelf(-6,2,-26,6,QUARTZ_SLAB,POTTED_ALLIUM,POTTED_FERN);b.put(0,1,-25,DECORATED_POT);
        b.path(0,18,0,13,POLISHED_ANDESITE);b.path(-19,13,19,13,POLISHED_ANDESITE);b.path(-11,13,-11,-22,POLISHED_ANDESITE);b.path(11,13,11,-22,POLISHED_ANDESITE);b.path(-11,-22,11,-22,POLISHED_ANDESITE);
        b.path(-19,13,-19,11,POLISHED_ANDESITE);b.path(19,13,19,11,POLISHED_ANDESITE);
        b.shelf(-6,1,-3,6,QUARTZ_SLAB,POTTED_WHITE_TULIP,POTTED_ALLIUM);b.put(-6,1,14,DECORATED_POT);b.put(6,1,14,DECORATED_POT);
    }
}
