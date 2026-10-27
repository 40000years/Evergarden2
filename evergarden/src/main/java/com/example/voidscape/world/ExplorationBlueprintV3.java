package com.example.voidscape.world;

import org.bukkit.Axis;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.generator.ChunkGenerator.ChunkData;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Individually planned, furnished destinations; only their outside approach is shared. */
final class ExplorationBlueprintV3 {
    public static final int CHEST_X=5,CHEST_Z=-4;
    public record Point(int x,int y,int z){}
    public record Voxel(int x,int y,int z,Material material,BlockFace facing,boolean upper){
        public Voxel(int x,int y,int z,Material material){this(x,y,z,material,null,false);}
    }
    private record Style(Material material,BlockFace facing,int rotation,boolean upper){}
    private static final Map<ExplorationLayout.Kind,List<Voxel>> BUILDS=new ConcurrentHashMap<>();
    private static final Map<Style,BlockData> STATES=new ConcurrentHashMap<>();
    private static final Map<ExplorationLayout.Kind,Map<Point,Voxel>> LOOKUPS=new ConcurrentHashMap<>();
    private ExplorationBlueprintV3(){}
    public static Point chest(ExplorationLayout.Kind kind,boolean legacy){
        if(legacy)return new Point(CHEST_X,1,CHEST_Z);
        return switch(kind){
            case BLOSSOM_PAVILION->new Point(-4,1,-2);case GARDENER_COTTAGE->new Point(-7,1,-5);
            case SPRING_WELL->new Point(7,1,-5);case EXPEDITION_CAMP->new Point(-6,1,-6);
            case FROST_CHAPEL->new Point(3,1,-7);case ICE_WATCH->new Point(-3,13,-4);
            case ROOT_LODGE->new Point(-6,1,-6);case MOSS_RUINS->new Point(-8,1,-7);
            case HERBALIST_HUT->new Point(-6,1,-5);case CRESCENT_ARCHIVE->new Point(4,1,-7);
            case STAR_GAZE->new Point(-6,3,-5);case MOON_GATE->new Point(10,1,-7);
            default->throw new IllegalArgumentException("No v3 blueprint for "+kind);
        };
    }
    public static List<Point> chests(ExplorationLayout.Kind kind,int version){
        var first=chest(kind,version==1);if(version<3)return List.of(first);
        return switch(kind){
            case BLOSSOM_PAVILION->List.of(first,new Point(4,1,0));
            case GARDENER_COTTAGE->List.of(first,new Point(7,1,-3));
            case SPRING_WELL->List.of(first,new Point(-5,1,5));
            case EXPEDITION_CAMP->List.of(first,new Point(9,1,-4));
            case FROST_CHAPEL->List.of(first,new Point(-3,1,-7));
            case ICE_WATCH->List.of(first,new Point(-5,1,-6),new Point(11,1,5));
            case ROOT_LODGE->List.of(first,new Point(-6,1,-2));
            case MOSS_RUINS->List.of(first,new Point(9,1,-8),new Point(-9,1,1));
            case HERBALIST_HUT->List.of(first,new Point(9,1,-8));
            case CRESCENT_ARCHIVE->List.of(first,new Point(-4,1,-7),new Point(-10,1,0));
            case STAR_GAZE->List.of(first,new Point(5,3,0));
            case MOON_GATE->List.of(first,new Point(-11,1,0));
            default->throw new IllegalArgumentException("No v3 blueprint for "+kind);
        };
    }
    public static List<Voxel> blocks(ExplorationLayout.Kind kind){return blocks(kind,false);}
    public static List<Voxel> blocks(ExplorationLayout.Kind kind,boolean legacy){
        if(legacy)return ExplorationBlueprintV1.blocks(kind).stream().map(v->new Voxel(v.x(),v.y(),v.z(),v.material())).toList();
        return BUILDS.computeIfAbsent(kind,ExplorationBlueprintV3::build);
    }
    public static List<Voxel> blocks(ExplorationLayout.Kind kind,int version){
        if(version==1)return blocks(kind,true);
        if(version==2)return ExplorationBlueprintV2.blocks(kind).stream().map(v->new Voxel(v.x(),v.y(),v.z(),v.material(),v.facing(),v.upper())).toList();
        return blocks(kind);
    }
    private static final class Builder {
        final Map<Point,Voxel> blocks=new LinkedHashMap<>();
        void put(int x,int y,int z,Material m){put(x,y,z,m,null,false);}
        void put(int x,int y,int z,Material m,BlockFace face){put(x,y,z,m,face,false);}
        void put(int x,int y,int z,Material m,BlockFace face,boolean upper){
            if(Math.abs(x)>18||Math.abs(z)>18||y< -6||y>27)throw new IllegalArgumentException("Small structure bounds: "+x+","+y+","+z);
            blocks.put(new Point(x,y,z),new Voxel(x,y,z,m,face,upper));
        }
        void box(int ax,int ay,int az,int bx,int by,int bz,Material m){
            for(int x=ax;x<=bx;x++)for(int z=az;z<=bz;z++)for(int y=ay;y<=by;y++)put(x,y,z,m);
        }
        void box(int ax,int ay,int az,int bx,int by,int bz,Material m,BlockFace face){
            for(int x=ax;x<=bx;x++)for(int z=az;z<=bz;z++)for(int y=ay;y<=by;y++)put(x,y,z,m,face);
        }
        void floor(int ax,int az,int bx,int bz,Material m){box(ax,0,az,bx,0,bz,m);}
        void ellipse(int cx,int y,int cz,double rx,double rz,Material m){
            for(int x=-(int)Math.ceil(rx);x<=rx;x++)for(int z=-(int)Math.ceil(rz);z<=rz;z++)
                if(x*x/(rx*rx)+z*z/(rz*rz)<=1)put(cx+x,y,cz+z,m);
        }
        void ring(int cx,int y,int cz,double rx,double rz,double width,Material m){
            for(int x=-(int)Math.ceil(rx+width);x<=rx+width;x++)for(int z=-(int)Math.ceil(rz+width);z<=rz+width;z++)
                if(Math.abs(Math.sqrt(x*x/(rx*rx)+z*z/(rz*rz))-1)*Math.min(rx,rz)<=width)put(cx+x,y,cz+z,m);
        }
        void beam(int ax,int ay,int az,int bx,int by,int bz,Material m){
            int steps=Math.max(1,Math.max(Math.abs(bx-ax),Math.max(Math.abs(by-ay),Math.abs(bz-az)))*3);
            BlockFace axis=Math.abs(bx-ax)>Math.abs(bz-az)?BlockFace.EAST:BlockFace.NORTH;
            if(Math.abs(by-ay)>Math.max(Math.abs(bx-ax),Math.abs(bz-az)))axis=BlockFace.UP;
            for(int i=0;i<=steps;i++){double t=i/(double)steps;put((int)Math.round(ax+(bx-ax)*t),(int)Math.round(ay+(by-ay)*t),(int)Math.round(az+(bz-az)*t),m,axis);}
        }
        void path(int ax,int az,int bx,int bz,Material paving){
            int steps=Math.max(1,Math.max(Math.abs(bx-ax),Math.abs(bz-az))*2);
            for(int i=0;i<=steps;i++){double t=i/(double)steps;int x=(int)Math.round(ax+(bx-ax)*t),z=(int)Math.round(az+(bz-az)*t);
                floor(x-1,z-1,x+1,z+1,paving);box(x-1,1,z-1,x+1,3,z+1,Material.AIR);}
        }
        void room(int ax,int az,int bx,int bz,int height,Material wall,Material floor){
            floor(ax,az,bx,bz,floor);box(ax,1,az,bx,height,bz,wall);box(ax+1,1,az+1,bx-1,height,bz-1,Material.AIR);
        }
        void gable(int cx,int az,int half,int bz,int eave,Material stair,Material slab,Material gable){
            for(int dx=-half-1;dx<=half+1;dx++){
                int y=eave+half+1-Math.abs(dx);
                for(int z=az-1;z<=bz+1;z++){
                    if(dx==0)put(cx,y,z,slab);else put(cx+dx,y,z,stair,dx<0?BlockFace.EAST:BlockFace.WEST);
                    if(Math.abs(dx)==half+1)put(cx+dx,y-1,z,stair,dx<0?BlockFace.WEST:BlockFace.EAST,true);
                }
                if(Math.abs(dx)<=half)for(int z:new int[]{az,bz})box(cx+dx,eave,z,cx+dx,y-1,z,gable);
            }
        }
        void post(int x,int z,int height,Material log){put(x,0,z,Material.STONE_BRICKS);box(x,1,z,x,height,z,log);}
        void bench(int ax,int y,int z,int bx,Material stairs,BlockFace facing){for(int x=ax;x<=bx;x++)put(x,y,z,stairs,facing);}
        void planter(int x,int z,Material rim,Material plant){floor(x-2,z-1,x+2,z+1,rim);floor(x-1,z,x+1,z,Material.MOSS_BLOCK);put(x,1,z,plant);}
        void lamp(int x,int y,int z){put(x,y,z,Material.LANTERN,BlockFace.UP);}
        void table(int x,int y,int z,Material wood,Material top){
            put(x,y,z,wood);put(x,y+1,z,top,null,true);
        }
        void pots(int ax,int y,int z,int bx,Material slab,Material... plants){
            for(int x=ax;x<=bx;x++){put(x,y,z,slab,null,true);if((x-ax)%2==0)put(x,y+1,z,plants[(x-ax)/2%plants.length]);}
        }
        void crates(int x,int z,Material wood){
            floor(x-1,z-1,x+2,z+1,wood);put(x,1,z,Material.BARREL,BlockFace.SOUTH);
            put(x+1,1,z,wood);put(x,2,z,wood);put(x+1,2,z,Material.OAK_TRAPDOOR,BlockFace.SOUTH);
        }
        void arch(int cx,int z,int half,int spring,int rise,Material pillar,Material arch){
            for(int side:new int[]{-1,1}){floor(cx+side*half-1,z-1,cx+side*half+1,z+1,pillar);box(cx+side*half-1,1,z-1,cx+side*half+1,spring,z+1,pillar);}
            for(int dx=-half;dx<=half;dx++){
                int y=spring+(int)Math.round(rise*(1-Math.pow(Math.abs(dx)/(double)half,.7)));
                int neighbor=spring+(int)Math.round(rise*(1-Math.pow(Math.abs(Math.min(half,dx+1))/(double)half,.7)));
                box(cx+dx,Math.min(y,neighbor),z-1,cx+dx,Math.max(y,neighbor)+1,z+1,arch);
            }
        }
    }
    private static List<Voxel> build(ExplorationLayout.Kind kind){
        var b=new Builder();
        switch(kind){
            case BLOSSOM_PAVILION->pavilion(b);case GARDENER_COTTAGE->cottage(b);case SPRING_WELL->well(b);
            case EXPEDITION_CAMP->camp(b);case FROST_CHAPEL->chapel(b);case ICE_WATCH->watch(b);
            case ROOT_LODGE->lodge(b);case MOSS_RUINS->ruins(b);case HERBALIST_HUT->herbalist(b);
            case CRESCENT_ARCHIVE->archive(b);case STAR_GAZE->observatory(b);case MOON_GATE->gate(b);
        }
        embellish(kind,b);
        b.path(0,9,0,11,kind.biome()==BiomeTerrain.Kind.LUNAR_CITY?Material.POLISHED_ANDESITE:Material.COBBLESTONE);
        for(var chest:chests(kind,3)){
            b.put(chest.x(),chest.y()-1,chest.z(),Material.CHISELED_STONE_BRICKS);
            b.put(chest.x(),chest.y()+1,chest.z(),Material.AIR);b.put(chest.x(),chest.y()+2,chest.z(),Material.AIR);
            b.put(chest.x(),chest.y(),chest.z(),Material.CHEST,BlockFace.SOUTH);
        }
        for(var lamp:List.copyOf(b.blocks.values()))if(lamp.material()==Material.LANTERN){
            int support=lamp.y()+1;
            while(support<=27){var v=b.blocks.get(new Point(lamp.x(),support,lamp.z()));if(v!=null&&!v.material().isAir())break;support++;}
            if(support<=27)for(int y=lamp.y()+1;y<support;y++)b.put(lamp.x(),y,lamp.z(),Material.IRON_CHAIN);
            else{
                var below=b.blocks.get(new Point(lamp.x(),lamp.y()-1,lamp.z()));
                if(below!=null&&!below.material().isAir())b.put(lamp.x(),lamp.y(),lamp.z(),Material.LANTERN);
                else b.blocks.remove(new Point(lamp.x(),lamp.y(),lamp.z()));
            }
        }
        return List.copyOf(b.blocks.values());
    }
    private static void embellish(ExplorationLayout.Kind kind,Builder b){
        switch(kind){
            case BLOSSOM_PAVILION->{
                for(int side:new int[]{-1,1}){
                    b.box(side*7,1,-6,side*7,1,1,Material.CHERRY_FENCE);
                    b.beam(side*6,6,-6,side*6,6,2,Material.STRIPPED_CHERRY_LOG);
                    b.put(side*7,2,-6,Material.CHERRY_TRAPDOOR,side<0?BlockFace.WEST:BlockFace.EAST);
                    b.pots(side<0?-7:3,2,3,side<0?-3:7,Material.CHERRY_SLAB,Material.POTTED_AZALEA_BUSH,Material.POTTED_PINK_TULIP);
                    b.box(side*6,0,5,side*6,1,6,Material.MOSSY_STONE_BRICKS);
                    b.put(side*6,2,5,Material.FLOWERING_AZALEA);
                }
                b.table(0,1,-3,Material.CHERRY_FENCE,Material.CHERRY_SLAB);b.put(0,3,-3,Material.POTTED_CHERRY_SAPLING);
                b.floor(-2,-1,2,1,Material.PINK_WOOL);
                b.pots(-6,1,-10,6,Material.STONE_BRICK_SLAB,Material.POTTED_ALLIUM,Material.POTTED_WHITE_TULIP);
                b.floor(-12,-4,-10,0,Material.COARSE_DIRT);b.put(-11,1,-2,Material.COMPOSTER);b.put(-12,1,-3,Material.BARREL);
                b.box(10,1,-5,12,2,-3,Material.AZALEA_LEAVES);b.put(11,3,-4,Material.FLOWERING_AZALEA);
            }
            case GARDENER_COTTAGE->{
                b.beam(-9,4,3,3,4,3,Material.STRIPPED_OAK_LOG);b.beam(-9,4,-9,3,4,-9,Material.STRIPPED_OAK_LOG);
                b.beam(-9,4,-9,-9,4,3,Material.STRIPPED_OAK_LOG);
                b.box(-8,1,4,-4,1,5,Material.MOSSY_STONE_BRICKS);b.pots(-8,2,4,-4,Material.OAK_SLAB,Material.POTTED_POPPY,Material.POTTED_WHITE_TULIP);
                b.box(4,2,0,4,3,0,Material.OAK_TRAPDOOR,BlockFace.SOUTH);
                b.box(8,2,0,8,3,0,Material.OAK_TRAPDOOR,BlockFace.SOUTH);
                b.box(-8,1,-7,-7,3,-6,Material.BRICKS);b.put(-7,1,-5,Material.SMOKER,BlockFace.SOUTH);
                b.table(-4,1,-5,Material.OAK_FENCE,Material.OAK_SLAB);b.put(-4,3,-5,Material.POTTED_DANDELION);
                b.bench(-5,1,-3,-3,Material.OAK_STAIRS,BlockFace.NORTH);
                b.floor(-8,-1,-5,1,Material.RED_WOOL);b.put(-8,1,0,Material.BARREL);
                b.floor(11,-10,16,-7,Material.OAK_PLANKS);b.floor(12,-9,15,-8,Material.FARMLAND);
                for(int x=12;x<=15;x++)for(int z=-9;z<=-8;z++)b.put(x,1,z,x%2==0?Material.WHEAT:Material.CARROTS);
                b.put(16,1,-9,Material.COMPOSTER);b.put(16,1,-7,Material.HAY_BLOCK);
                b.crates(11,2,Material.OAK_PLANKS);b.put(13,1,1,Material.FLOWER_POT);
                b.post(-12,5,2,Material.OAK_FENCE);b.lamp(-12,3,5);
            }
            case SPRING_WELL->{
                b.path(0,5,-5,5,Material.MOSSY_COBBLESTONE);b.floor(-8,4,-3,7,Material.MOSSY_COBBLESTONE);
                b.pots(-8,1,7,-4,Material.SPRUCE_SLAB,Material.POTTED_BLUE_ORCHID,Material.POTTED_FERN);
                b.post(-9,-8,3,Material.MOSSY_COBBLESTONE_WALL);b.post(-1,-8,3,Material.MOSSY_COBBLESTONE_WALL);
                b.box(-8,0,-7,-2,0,-6,Material.MOSSY_COBBLESTONE);
                b.put(-8,1,-7,Material.DECORATED_POT);b.put(-2,1,-7,Material.BARREL);
                b.box(-6,6,-3,-4,6,-3,Material.STRIPPED_SPRUCE_LOG,BlockFace.EAST);
                b.box(-6,7,-3,-4,7,-3,Material.SPRUCE_SLAB);b.put(-5,2,-2,Material.CAULDRON);
                b.pots(5,2,-7,9,Material.STONE_BRICK_SLAB,Material.POTTED_FERN,Material.POTTED_AZALEA_BUSH);
                b.box(10,1,-9,11,3,-7,Material.AZALEA_LEAVES);
                b.bench(-10,1,1,-8,Material.MOSSY_STONE_BRICK_STAIRS,BlockFace.SOUTH);
                b.floor(7,3,11,6,Material.COARSE_DIRT);b.put(10,1,5,Material.COMPOSTER);b.crates(8,4,Material.SPRUCE_PLANKS);
            }
            case EXPEDITION_CAMP->{
                b.floor(-9,-5,-3,-2,Material.BLUE_WOOL);b.put(-9,1,-4,Material.WHITE_WOOL);b.put(-9,1,-3,Material.WHITE_WOOL);
                b.put(-3,1,-2,Material.CRAFTING_TABLE);b.put(-3,2,-6,Material.LANTERN);
                b.table(5,1,-3,Material.SPRUCE_FENCE,Material.SPRUCE_SLAB);b.put(5,3,-3,Material.FLOWER_POT);
                b.put(5,1,-5,Material.SMOKER,BlockFace.SOUTH);b.put(7,1,-7,Material.HAY_BLOCK);b.put(7,2,-7,Material.HAY_BLOCK);
                b.crates(11,-7,Material.SPRUCE_PLANKS);b.put(10,1,-1,Material.CAULDRON);
                b.box(-11,0,1,-9,0,3,Material.COBBLESTONE);b.post(-11,2,3,Material.SPRUCE_FENCE);b.post(-9,2,3,Material.SPRUCE_FENCE);
                b.beam(-11,4,2,-9,4,2,Material.SPRUCE_LOG);b.lamp(-10,3,2);
                b.floor(6,6,10,8,Material.COARSE_DIRT);b.put(8,1,7,Material.SPRUCE_LOG,BlockFace.EAST);b.put(9,1,7,Material.SPRUCE_LOG,BlockFace.EAST);
                b.put(8,2,7,Material.SPRUCE_LOG,BlockFace.EAST);b.put(6,1,7,Material.ANVIL);
                for(int z:new int[]{-9,1})for(int side:new int[]{-1,1})b.beam(-6+side*4,3,z,-6+side*7,0,z+1,Material.SPRUCE_FENCE);
                b.path(2,1,8,0,Material.GRAVEL);b.path(8,0,8,-4,Material.GRAVEL);
            }
            case FROST_CHAPEL->{
                for(int side:new int[]{-1,1})for(int z:new int[]{-7,-2,2}){
                    int x=side*6;b.floor(x-1,z-1,x+1,z+1,Material.STONE_BRICKS);
                    b.box(x,1,z,x,4,z,Material.STONE_BRICKS);b.put(x,5,z,Material.QUARTZ_STAIRS,side<0?BlockFace.EAST:BlockFace.WEST);
                    b.put(side*5,7,z,Material.CHISELED_QUARTZ_BLOCK);
                }
                b.floor(-1,-8,1,2,Material.BLUE_WOOL);b.put(0,1,-10,Material.LECTERN);
                b.box(-4,2,-8,-4,4,-8,Material.BOOKSHELF);b.box(4,2,-8,4,4,-8,Material.BOOKSHELF);
                b.pots(-2,2,-12,2,Material.QUARTZ_SLAB,Material.POTTED_WHITE_TULIP,Material.POTTED_BLUE_ORCHID);
                for(int x:new int[]{-3,3}){b.put(x,8,1,Material.CHISELED_QUARTZ_BLOCK);b.lamp(x,6,1);}
                b.box(-1,15,-9,1,15,-7,Material.CHISELED_STONE_BRICKS);b.put(0,16,-7,Material.IRON_BARS);
                b.floor(-10,4,-6,8,Material.STONE_BRICKS);b.bench(-9,1,6,-7,Material.STONE_BRICK_STAIRS,BlockFace.SOUTH);
                b.put(-8,2,4,Material.POTTED_SPRUCE_SAPLING);b.put(-8,1,4,Material.CHISELED_STONE_BRICKS);
                b.post(7,7,3,Material.COBBLESTONE_WALL);b.lamp(7,4,7);
            }
            case ICE_WATCH->{
                b.floor(10,2,14,7,Material.COBBLESTONE);b.path(0,10,11,10,Material.GRAVEL);b.path(11,10,11,7,Material.GRAVEL);
                b.box(10,1,2,14,4,2,Material.STONE_BRICKS);b.post(14,7,3,Material.SPRUCE_FENCE);
                for(int z=2;z<=7;z++)b.box(10,5-(z-2)/3,z,15,5-(z-2)/3,z,Material.SPRUCE_SLAB);
                b.put(13,1,4,Material.CRAFTING_TABLE);b.put(13,1,3,Material.BARREL);b.lamp(14,3,6);
                b.box(-7,1,-7,-6,2,-7,Material.BOOKSHELF);b.put(-1,1,-7,Material.FURNACE,BlockFace.SOUTH);
                b.put(-6,7,-4,Material.BARREL);b.put(-5,7,-5,Material.HAY_BLOCK);
                b.table(-3,13,-6,Material.SPRUCE_FENCE,Material.SPRUCE_SLAB);b.put(-3,15,-6,Material.FLOWER_POT);
                b.bench(-5,13,-2,-3,Material.SPRUCE_STAIRS,BlockFace.SOUTH);
                for(int z=0;z<=8;z++)if(z%3==0)b.box(9,0,z,9,Math.max(1,7-z),z,Material.COBBLESTONE);
                b.box(2,13,-5,6,13,-5,Material.SPRUCE_FENCE);b.box(2,13,-1,6,13,-1,Material.SPRUCE_FENCE);
                for(int x:new int[]{-9,3}){b.put(x,4,-4,Material.STONE_BRICK_SLAB);b.put(x,10,-4,Material.STONE_BRICK_SLAB);}
                b.box(-4,1,-1,-2,3,2,Material.AIR);
            }
            case ROOT_LODGE->{
                b.box(-9,1,-6,-8,2,-5,Material.BRICKS);b.put(-8,1,-4,Material.SMOKER,BlockFace.EAST);
                b.table(-4,1,-5,Material.DARK_OAK_FENCE,Material.OAK_SLAB);b.put(-4,3,-5,Material.POTTED_RED_MUSHROOM);
                b.floor(-3,-2,-1,0,Material.GREEN_WOOL);b.put(-7,1,0,Material.BARREL);
                b.pots(-7,2,-8,-5,Material.OAK_SLAB,Material.POTTED_FERN,Material.POTTED_BROWN_MUSHROOM);
                b.box(2,2,-7,2,2,-1,Material.OAK_SLAB);b.put(2,3,-4,Material.POTTED_FERN);
                for(int[] p:new int[][]{{-9,-3},{-6,-9},{1,-7},{-1,1}}){b.put(p[0],12,p[1],Material.MOSS_BLOCK);b.put(p[0],13,p[1],Material.FERN);}
                b.box(-11,0,3,-6,0,6,Material.COARSE_DIRT);b.put(-10,1,4,Material.COMPOSTER);b.put(-8,1,5,Material.BROWN_MUSHROOM);b.put(-6,1,4,Material.RED_MUSHROOM);
                b.bench(5,1,0,8,Material.DARK_OAK_STAIRS,BlockFace.EAST);b.put(7,1,-2,Material.DECORATED_POT);
                b.ellipse(-12,4,-5,2,1.5,Material.BROWN_MUSHROOM_BLOCK);b.ellipse(-1,5,-11,2,1.5,Material.RED_MUSHROOM_BLOCK);
                b.box(-1,1,3,-1,3,3,Material.DARK_OAK_FENCE);b.beam(-1,4,3,3,4,3,Material.DARK_OAK_LOG);b.lamp(2,3,3);
            }
            case MOSS_RUINS->{
                b.box(8,1,-10,11,3,-10,Material.CRACKED_STONE_BRICKS);b.box(11,1,-9,11,5,-6,Material.MOSSY_STONE_BRICKS);
                b.box(8,4,-10,9,4,-10,Material.MOSS_BLOCK);b.put(10,1,-7,Material.DECORATED_POT);
                b.box(-11,1,1,-11,4,3,Material.MOSSY_STONE_BRICKS);b.box(-10,5,1,-8,5,3,Material.STONE_BRICK_SLAB);
                b.put(-10,1,3,Material.DECORATED_POT);b.put(-8,1,3,Material.BARREL);
                for(int x:new int[]{3,7,11}){b.put(x,4,-3,Material.COBBLESTONE_WALL);b.put(x,4,0,Material.MOSSY_COBBLESTONE_WALL);}
                b.floor(-4,3,-1,5,Material.COARSE_DIRT);b.box(-3,1,4,-2,1,4,Material.CRACKED_STONE_BRICKS);
                b.put(-3,2,4,Material.STONE_BRICK_SLAB);b.put(-4,1,5,Material.MOSS_CARPET);
                b.box(-12,2,-8,-12,4,-5,Material.AZALEA_LEAVES);b.box(-10,5,-10,-7,5,-10,Material.MOSS_CARPET);
                b.put(4,1,-7,Material.CHISELED_STONE_BRICKS);b.put(5,1,-6,Material.MOSSY_COBBLESTONE);
                b.bench(6,1,5,9,Material.MOSSY_STONE_BRICK_STAIRS,BlockFace.NORTH);b.put(11,1,5,Material.POTTED_FERN);
            }
            case HERBALIST_HUT->{
                b.beam(-10,5,-9,1,5,-9,Material.SPRUCE_LOG);b.beam(-10,4,3,1,4,3,Material.STRIPPED_SPRUCE_LOG);
                b.post(-5,3,4,Material.STRIPPED_SPRUCE_LOG);b.box(-10,2,3,-6,2,3,Material.SPRUCE_TRAPDOOR,BlockFace.SOUTH);
                b.pots(-9,2,2,-6,Material.SPRUCE_SLAB,Material.POTTED_RED_MUSHROOM,Material.POTTED_FERN);
                b.put(-8,1,-3,Material.SPRUCE_FENCE);b.put(-8,2,-3,Material.SPRUCE_SLAB,null,true);b.put(-8,3,-3,Material.BREWING_STAND);
                b.box(-9,1,-6,-7,1,-6,Material.BARREL);b.put(-9,2,-6,Material.POTTED_ALLIUM);b.put(-7,2,-6,Material.POTTED_BROWN_MUSHROOM);
                b.pots(4,2,-8,7,Material.SPRUCE_SLAB,Material.POTTED_FERN,Material.POTTED_AZALEA_BUSH);
                b.put(9,1,-1,Material.COMPOSTER);b.put(9,2,-1,Material.POTTED_FERN);
                b.floor(11,-7,14,-3,Material.SPRUCE_PLANKS);b.floor(12,-6,13,-4,Material.FARMLAND);
                for(int x=12;x<=13;x++)for(int z=-6;z<=-4;z++)b.put(x,1,z,Material.CARROTS);
                b.put(14,1,-6,Material.HAY_BLOCK);b.put(14,1,-3,Material.BARREL);b.put(12,1,-2,Material.COMPOSTER);
                b.path(1,3,6,3,Material.COARSE_DIRT);b.lamp(0,3,2);b.put(-11,1,-4,Material.POTTED_FERN);
            }
            case CRESCENT_ARCHIVE->{
                b.path(0,6,-10,6,Material.POLISHED_ANDESITE);b.path(-10,6,-10,4,Material.POLISHED_ANDESITE);
                for(int side:new int[]{-1,1})for(int z:new int[]{-9,-4,0}){
                    b.post(side*7,z,8,Material.QUARTZ_PILLAR);b.put(side*7,9,z,Material.WAXED_EXPOSED_CUT_COPPER_SLAB);
                }
                b.table(0,1,-6,Material.SPRUCE_FENCE,Material.SPRUCE_SLAB);b.put(0,3,-6,Material.FLOWER_POT);
                b.bench(-2,1,-4,-1,Material.SPRUCE_STAIRS,BlockFace.NORTH);b.bench(1,1,-4,2,Material.SPRUCE_STAIRS,BlockFace.NORTH);
                b.floor(-2,-2,2,0,Material.PURPLE_WOOL);b.put(3,1,-2,Material.LECTERN);
                b.pots(-11,2,-1,-9,Material.QUARTZ_SLAB,Material.POTTED_ALLIUM,Material.POTTED_AZALEA_BUSH);
                b.box(-12,1,-2,-12,4,1,Material.BOOKSHELF);b.put(-8,1,-1,Material.DECORATED_POT);
                for(int x=-2;x<=2;x++)b.put(x,7,2,Material.WAXED_OXIDIZED_CUT_COPPER_SLAB);
                b.lamp(-4,5,-1);b.lamp(4,5,-1);
                b.box(9,0,-10,12,0,-7,Material.MOSSY_STONE_BRICKS);b.put(10,1,-9,Material.CHISELED_QUARTZ_BLOCK);
                b.put(11,1,-8,Material.WAXED_OXIDIZED_CUT_COPPER_STAIRS,BlockFace.EAST);b.put(12,1,-7,Material.MOSS_CARPET);
            }
            case STAR_GAZE->{
                for(int x=-6;x<=2;x++)b.put(x,3,-8,Material.WAXED_CUT_COPPER_SLAB);
                b.box(-2,3,0,2,3,0,Material.LIGHT_GRAY_CARPET);b.put(0,3,-1,Material.CHISELED_QUARTZ_BLOCK);b.put(0,4,-1,Material.LECTERN);
                b.table(-7,3,-2,Material.SPRUCE_FENCE,Material.SPRUCE_SLAB);b.put(-7,5,-2,Material.FLOWER_POT);
                b.bench(2,3,2,5,Material.SMOOTH_QUARTZ_STAIRS,BlockFace.SOUTH);
                b.put(7,3,-1,Material.BARREL);b.put(7,4,-1,Material.LANTERN);
                for(int z=-6;z<=-2;z++)b.put(8,3,z,Material.WAXED_OXIDIZED_CUT_COPPER_SLAB);
                b.put(-8,3,-5,Material.DECORATED_POT);b.put(-8,4,-5,Material.POTTED_ALLIUM);
                b.floor(9,3,13,6,Material.POLISHED_ANDESITE);b.put(11,1,4,Material.QUARTZ_PILLAR);b.put(11,2,4,Material.AMETHYST_BLOCK);
                b.put(11,3,4,Material.WAXED_CUT_COPPER_SLAB);b.bench(10,1,6,12,Material.STONE_BRICK_STAIRS,BlockFace.SOUTH);
            }
            case MOON_GATE->{
                b.floor(-13,-1,-9,2,Material.QUARTZ_BRICKS);b.path(0,5,-11,5,Material.POLISHED_ANDESITE);b.path(-11,5,-11,2,Material.POLISHED_ANDESITE);
                for(int side:new int[]{-1,1}){
                    b.box(side*6,1,1,side*6,2,2,Material.CHISELED_QUARTZ_BLOCK);b.put(side*6,3,1,Material.QUARTZ_SLAB);
                    b.box(side*7,3,-2,side*7,7,-2,Material.WAXED_OXIDIZED_CUT_COPPER);
                    b.box(side*6,7,0,side*6,8,0,Material.WAXED_CUT_COPPER);
                    b.put(side*5,9,0,Material.QUARTZ_STAIRS,side<0?BlockFace.EAST:BlockFace.WEST);
                }
                b.box(-12,1,1,-10,1,1,Material.BOOKSHELF);b.put(-13,1,0,Material.DECORATED_POT);
                b.pots(9,2,-8,11,Material.QUARTZ_SLAB,Material.POTTED_ALLIUM,Material.POTTED_FERN);
                b.lamp(12,3,-6);b.put(12,1,-5,Material.BARREL);
                b.floor(-3,-5,3,-3,Material.CHISELED_STONE_BRICKS);b.put(-3,1,-4,Material.QUARTZ_SLAB);b.put(3,1,-4,Material.QUARTZ_SLAB);
                b.box(13,0,4,15,0,6,Material.MOSSY_STONE_BRICKS);b.put(14,1,5,Material.QUARTZ_STAIRS,BlockFace.EAST);
                b.put(13,1,6,Material.MOSS_CARPET);b.put(15,1,4,Material.WAXED_OXIDIZED_CUT_COPPER_SLAB);
            }
        }
    }
    private static void pavilion(Builder b){
        for(int x=-8;x<=8;x++)for(int z=-10;z<=6;z++)if(Math.abs(x)+Math.abs(z+2)<=12)
            b.put(x,0,z,Math.abs(x)==8||Math.abs(z+2)==8?Material.STONE_BRICKS:Material.CHERRY_PLANKS);
        int[][] posts={{-6,-6},{6,-6},{-6,2},{6,2},{-3,-9},{3,-9}};
        for(int[] p:posts){b.post(p[0],p[1],6,Material.STRIPPED_CHERRY_LOG);b.lamp(p[0],5,p[1]+1);}
        b.beam(-6,6,-6,6,6,-6,Material.CHERRY_LOG);b.beam(-6,6,2,6,6,2,Material.CHERRY_LOG);
        for(int side:new int[]{-1,1}){b.beam(side*6,4,2,side*3,6,2,Material.CHERRY_LOG);b.beam(side*6,4,-6,side*3,6,-6,Material.CHERRY_LOG);}
        for(int level=0;level<5;level++){
            int r=9-level,y=7+level;
            for(int x=-r;x<=r;x++)for(int z=-r;z<=r;z++){
                if(Math.abs(x)+Math.abs(z)>r*1.6)continue;
                if(Math.max(Math.abs(x),Math.abs(z))==r){
                    BlockFace face=Math.abs(x)>=Math.abs(z)?(x<0?BlockFace.EAST:BlockFace.WEST):(z<0?BlockFace.SOUTH:BlockFace.NORTH);
                    b.put(x,y,z-2,Material.DARK_PRISMARINE_STAIRS,face);
                }else b.put(x,y,z-2,Material.DARK_PRISMARINE_SLAB);
            }
        }
        b.box(-2,12,-4,2,12,0,Material.WAXED_OXIDIZED_COPPER);b.put(0,13,-2,Material.DARK_PRISMARINE_SLAB);
        b.bench(-5,1,-7,-2,Material.CHERRY_STAIRS,BlockFace.NORTH);b.bench(2,1,-7,5,Material.CHERRY_STAIRS,BlockFace.NORTH);
        b.floor(-1,-5,1,2,Material.WHITE_WOOL);b.path(0,8,0,4,Material.STONE_BRICKS);
        b.planter(-10,4,Material.STONE_BRICK_SLAB,Material.FLOWERING_AZALEA);b.planter(10,3,Material.STONE_BRICK_SLAB,Material.PINK_TULIP);
        b.post(-3,7,2,Material.CHERRY_FENCE);b.lamp(-3,3,7);
    }
    private static void cottage(Builder b){
        b.room(-9,-9,3,3,4,Material.WHITE_TERRACOTTA,Material.OAK_PLANKS);
        b.room(3,-7,9,-1,3,Material.WHITE_TERRACOTTA,Material.OAK_PLANKS);
        b.box(2,1,-5,4,3,-3,Material.AIR);b.box(-1,1,3,1,3,3,Material.AIR);
        for(int x:new int[]{-9,3})for(int z:new int[]{-9,-3,3})b.post(x,z,4,Material.STRIPPED_OAK_LOG);
        for(int x:new int[]{3,9})for(int z:new int[]{-7,-1})b.post(x,z,3,Material.STRIPPED_OAK_LOG);
        b.gable(-3,-9,6,3,5,Material.DARK_PRISMARINE_STAIRS,Material.DARK_PRISMARINE_SLAB,Material.WHITE_TERRACOTTA);
        b.gable(6,-7,3,-1,4,Material.SPRUCE_STAIRS,Material.SPRUCE_SLAB,Material.WHITE_TERRACOTTA);
        for(int z:new int[]{-6,0}){b.box(-9,2,z-1,-9,3,z+1,Material.GLASS_PANE);b.put(-10,2,z-2,Material.OAK_TRAPDOOR,BlockFace.WEST);b.put(-10,2,z+2,Material.OAK_TRAPDOOR,BlockFace.WEST);}
        b.box(-7,2,3,-5,3,3,Material.GLASS_PANE);b.box(5,2,-1,7,2,-1,Material.GLASS_PANE);
        b.box(-8,5,-7,-7,12,-6,Material.BRICKS);b.put(-8,13,-7,Material.BRICK_SLAB);
        b.floor(-3,4,3,6,Material.OAK_PLANKS);for(int x:new int[]{-3,3})b.post(x,6,3,Material.OAK_FENCE);
        b.box(-3,4,4,3,4,6,Material.SPRUCE_SLAB);b.lamp(-2,3,5);b.path(0,8,0,7,Material.COBBLESTONE);
        b.box(-8,1,-8,-5,2,-8,Material.BOOKSHELF);b.put(-8,1,-2,Material.CRAFTING_TABLE);
        b.put(7,1,-5,Material.BARREL,BlockFace.SOUTH);b.put(6,1,-5,Material.FURNACE,BlockFace.SOUTH);
        b.bench(-6,1,1,-4,Material.OAK_STAIRS,BlockFace.SOUTH);
        for(int z:new int[]{-5,0,5})b.planter(12,z,Material.OAK_PLANKS,z<1?Material.ALLIUM:Material.WHITE_TULIP);
        b.post(11,7,5,Material.OAK_FENCE);b.post(15,7,5,Material.OAK_FENCE);b.beam(11,5,7,15,5,7,Material.OAK_LOG);b.box(11,5,6,15,6,7,Material.AZALEA_LEAVES);
    }
    private static void well(Builder b){
        b.ellipse(-5,-1,-3,5,5,Material.MOSSY_COBBLESTONE);b.ellipse(-5,0,-3,5,5,Material.STONE_BRICKS);
        b.ellipse(-5,0,-3,2.8,2.8,Material.WATER);b.ring(-5,1,-3,3.8,3.8,.7,Material.STONE_BRICK_SLAB);
        b.post(-9,-3,5,Material.STRIPPED_SPRUCE_LOG);b.post(-1,-3,5,Material.STRIPPED_SPRUCE_LOG);
        b.beam(-9,6,-3,-1,6,-3,Material.SPRUCE_LOG);b.box(-5,2,-3,-5,5,-3,Material.IRON_CHAIN);
        b.lamp(-1,4,-2);b.put(-9,2,-2,Material.LEVER,BlockFace.SOUTH);
        b.floor(-7,-12,-3,-8,Material.MOSSY_STONE_BRICKS);b.box(-7,1,-12,-3,1,-8,Material.STONE_BRICKS);
        b.box(-6,1,-12,-4,1,-8,Material.WATER);b.box(-6,1,-13,-4,2,-13,Material.MOSSY_STONE_BRICKS);
        b.box(-6,1,-7,-4,1,-7,Material.STONE_BRICKS);
        b.path(0,8,2,3,Material.MOSSY_COBBLESTONE);b.path(2,3,6,-3,Material.MOSSY_COBBLESTONE);
        b.floor(4,-8,10,-3,Material.MOSSY_STONE_BRICKS);b.box(4,1,-8,10,4,-8,Material.MOSSY_STONE_BRICKS);
        b.box(4,1,-7,4,4,-3,Material.MOSSY_STONE_BRICKS);b.box(10,1,-7,10,4,-3,Material.STONE_BRICKS);
        b.box(4,5,-8,10,5,-3,Material.STONE_BRICK_SLAB);b.box(4,6,-7,7,6,-5,Material.MOSS_BLOCK);
        b.put(8,1,-7,Material.DECORATED_POT);b.planter(-11,4,Material.MOSSY_COBBLESTONE,Material.BLUE_ORCHID);
    }
    private static void camp(Builder b){
        b.floor(-10,-8,-2,0,Material.SPRUCE_PLANKS);
        for(int dx=-4;dx<=4;dx++)for(int z=-8;z<=0;z++){
            int y=6-Math.abs(dx);b.put(-6+dx,y,z,dx%3==0?Material.LIGHT_BLUE_WOOL:Material.WHITE_WOOL);
            if(z==-8||z==0)b.box(-6+dx,1,z,-6+dx,y-1,z,Material.WHITE_WOOL);
        }
        b.box(-7,1,0,-5,3,0,Material.AIR);b.beam(-6,7,-9,-6,7,1,Material.SPRUCE_LOG);
        b.post(-6,2,6,Material.SPRUCE_FENCE);b.lamp(-6,6,1);b.bench(-9,1,-7,-8,Material.SPRUCE_STAIRS,BlockFace.NORTH);
        b.put(-3,1,-6,Material.BARREL,BlockFace.SOUTH);b.floor(4,-8,10,-2,Material.COARSE_DIRT);
        for(int x:new int[]{4,10}){b.post(x,-8,6,Material.SPRUCE_LOG);b.post(x,-2,3,Material.SPRUCE_FENCE);}
        for(int z=-8;z<=-2;z++)for(int x=3;x<=11;x++){
            int y=4+(-2-z)/2;b.put(x,y-1,z,Material.SPRUCE_PLANKS);b.put(x,y,z,Material.SPRUCE_SLAB);
        }
        for(int x:new int[]{5,8}){b.put(x,1,-7,Material.BARREL,BlockFace.SOUTH);b.put(x,1,-6,Material.SPRUCE_PLANKS);}
        b.ellipse(3,0,3,3,3,Material.COBBLESTONE);b.put(3,1,3,Material.CAMPFIRE);
        b.bench(-2,1,4,0,Material.SPRUCE_STAIRS,BlockFace.SOUTH);b.bench(6,1,4,8,Material.SPRUCE_STAIRS,BlockFace.SOUTH);
        b.path(0,8,0,0,Material.GRAVEL);b.path(0,1,-5,1,Material.GRAVEL);
        b.post(11,4,5,Material.SPRUCE_FENCE);b.put(12,5,4,Material.LIGHT_BLUE_WOOL);b.put(12,4,4,Material.WHITE_WOOL);
    }
    private static void chapel(Builder b){
        b.room(-5,-9,5,3,7,Material.POLISHED_DIORITE,Material.STONE_BRICKS);
        for(int y=0;y<=8;y++)b.ellipse(0,y,-9,4,4,y==0?Material.STONE_BRICKS:Material.PACKED_ICE);
        for(int y=1;y<=7;y++)b.ellipse(0,y,-9,2.8,2.8,Material.AIR);b.box(-3,1,-9,3,7,-8,Material.AIR);
        b.gable(0,-9,5,3,8,Material.QUARTZ_STAIRS,Material.QUARTZ_SLAB,Material.PACKED_ICE);b.box(-1,1,3,1,4,3,Material.AIR);
        for(int x:new int[]{-5,5})for(int z:new int[]{-6,-1,3}){b.post(x,z,8,Material.QUARTZ_PILLAR);b.box(x,3,z-1,x,6,z+1,Material.LIGHT_BLUE_STAINED_GLASS_PANE);}
        b.arch(0,4,3,5,4,Material.STONE_BRICKS,Material.QUARTZ_BRICKS);b.floor(-2,4,2,7,Material.POLISHED_DIORITE);b.path(0,8,0,7,Material.STONE_BRICKS);
        b.bench(-4,1,-1,-3,Material.SPRUCE_STAIRS,BlockFace.NORTH);b.bench(3,1,-1,4,Material.SPRUCE_STAIRS,BlockFace.NORTH);
        b.bench(-4,1,-4,-3,Material.SPRUCE_STAIRS,BlockFace.NORTH);b.bench(3,1,-4,4,Material.SPRUCE_STAIRS,BlockFace.NORTH);
        b.box(-2,1,-11,2,1,-10,Material.CHISELED_QUARTZ_BLOCK);b.put(0,2,-11,Material.SEA_LANTERN);
        b.box(-1,15,-9,1,16,-7,Material.STONE_BRICKS);b.box(0,17,-8,0,21,-8,Material.QUARTZ_PILLAR);b.put(0,22,-8,Material.QUARTZ_SLAB);
        b.lamp(-4,5,1);b.lamp(4,5,1);
    }
    private static void watch(Builder b){
        for(int y=0;y<=15;y++)b.ellipse(-3,y,-4,5.5,5.5,y==0?Material.STONE_BRICKS:Material.COBBLESTONE);
        for(int y=1;y<15;y++)b.ellipse(-3,y,-4,3.8,3.8,Material.AIR);
        for(int y:new int[]{0,6,12})b.ellipse(-3,y,-4,4.4,4.4,Material.SPRUCE_PLANKS);
        for(int y:new int[]{5,11,15})b.ring(-3,y,-4,5.5,5.5,.6,Material.STONE_BRICKS);
        b.box(-4,1,1,-2,3,2,Material.AIR);
        for(int y:new int[]{3,8,13}){b.box(-8,y,-5,-8,y+1,-3,Material.GLASS_PANE);b.box(-4,y,-9,-2,y+1,-9,Material.GLASS_PANE);}
        for(int level=0;level<7;level++){
            double radius=6.5-level*.8;b.ellipse(-3,16+level,-4,radius,radius,level==6?Material.SPRUCE_SLAB:Material.DEEPSLATE_TILES);
            if(level<6)b.ring(-3,16+level,-4,radius,radius,.55,Material.DEEPSLATE_TILE_SLAB);
        }
        b.path(0,8,-3,3,Material.COBBLESTONE);b.path(0,9,7,9,Material.COBBLESTONE);
        for(int step=0;step<=12;step++)for(int x=6;x<=8;x++){
            int z=9-step;b.put(x,0,z,Material.STONE_BRICKS);if(step%4==0)b.box(x,1,z,x,Math.max(1,step-1),z,Material.COBBLESTONE);
            b.put(x,step,z,Material.STONE_BRICK_STAIRS,BlockFace.NORTH);b.box(x,step+1,z,x,step+3,z,Material.AIR);
            if(x==8&&step%3==0)b.put(x,step+1,z,Material.SPRUCE_FENCE);
        }
        b.box(1,12,-4,7,12,-2,Material.SPRUCE_PLANKS);b.box(1,13,-4,3,15,-2,Material.AIR);
        b.put(-5,13,-6,Material.BARREL,BlockFace.SOUTH);b.lamp(-1,14,-6);
    }
    private static void lodge(Builder b){
        for(int y=0;y<=10;y++)b.ellipse(-4,y,-4,8-y*.08,7-y*.06,Material.DARK_OAK_LOG);
        for(int y=1;y<=7;y++)b.ellipse(-4,y,-4,5.8,4.8,Material.AIR);
        b.ellipse(-4,0,-4,6,5,Material.OAK_PLANKS);b.box(-2,1,0,1,4,4,Material.AIR);
        b.ellipse(-4,11,-4,7.0,6.2,Material.STRIPPED_OAK_LOG);b.ring(-4,11,-4,5,4.2,.6,Material.DARK_OAK_WOOD);
        for(int i=0;i<7;i++){
            double a=i*Math.PI*2/7;int x=-4+(int)Math.round(Math.cos(a)*7.4),z=-4+(int)Math.round(Math.sin(a)*6.4);
            b.box(x,1,z,x,9+(i%3),z,Material.STRIPPED_DARK_OAK_LOG);
            int rx=-4+(int)Math.round(Math.cos(a)*11),rz=-4+(int)Math.round(Math.sin(a)*10);b.beam(x,4,z,rx,0,rz,Material.DARK_OAK_LOG);
        }
        b.ellipse(-7,5,2,3,2,Material.BROWN_MUSHROOM_BLOCK);b.ellipse(3,7,-5,3,2,Material.BROWN_MUSHROOM_BLOCK);
        b.ellipse(-4,12,-4,5,4,Material.MOSS_BLOCK);b.put(-6,13,-4,Material.FERN);
        b.box(2,3,-6,4,5,-2,Material.AIR);b.box(3,3,-6,3,5,-2,Material.GLASS_PANE);
        b.box(-6,3,-11,-3,5,-9,Material.AIR);b.box(-6,3,-10,-3,5,-10,Material.GLASS_PANE);
        b.box(-10,9,-6,-9,14,-5,Material.DARK_OAK_LOG);
        b.put(-8,1,-3,Material.CRAFTING_TABLE);b.box(-8,1,-8,-5,3,-8,Material.BOOKSHELF);
        b.bench(-2,1,-7,0,Material.OAK_STAIRS,BlockFace.NORTH);b.lamp(0,4,-5);
        b.path(0,8,0,4,Material.MOSSY_COBBLESTONE);b.planter(6,4,Material.MOSSY_COBBLESTONE,Material.RED_MUSHROOM);
    }
    private static void ruins(Builder b){
        b.floor(-11,-10,-5,4,Material.MOSSY_STONE_BRICKS);b.floor(-4,-10,11,-6,Material.CRACKED_STONE_BRICKS);
        b.ellipse(6,-1,-1,6,4,Material.MOSSY_COBBLESTONE);b.ellipse(6,0,-1,6,4,Material.MOSSY_COBBLESTONE);b.ellipse(6,0,-1,4.5,2.5,Material.WATER);
        b.arch(-1,-8,6,4,4,Material.MOSSY_STONE_BRICKS,Material.CRACKED_STONE_BRICKS);b.box(2,6,-9,4,9,-7,Material.AIR);b.box(2,8,-9,3,9,-8,Material.MOSS_BLOCK);
        b.box(-11,1,-10,-5,4,-10,Material.MOSSY_STONE_BRICKS);b.box(-11,1,-9,-11,6,-3,Material.STONE_BRICKS);
        b.box(-11,7,-8,-9,7,-4,Material.MOSS_BLOCK);b.box(-10,8,-7,-8,8,-5,Material.AZALEA_LEAVES);
        for(int x=-2;x<=12;x++){
            int y=1+Math.min(2,Math.min(x+2,12-x)/2);b.box(x,-1,-2,x,y,-1,Material.MOSSY_STONE_BRICKS);
            b.box(x,y+1,-2,x,y+3,-1,Material.AIR);b.put(x,y,-2,Material.STONE_BRICK_STAIRS,x<5?BlockFace.EAST:BlockFace.WEST);
        }
        b.path(0,8,-7,3,Material.MOSSY_COBBLESTONE);b.path(-7,3,-7,-6,Material.MOSSY_COBBLESTONE);
        b.put(-10,1,-3,Material.DECORATED_POT);b.ellipse(10,1,-7,2.5,1.5,Material.MOSSY_COBBLESTONE);
    }
    private static void herbalist(Builder b){
        b.floor(-10,-9,1,3,Material.SPRUCE_PLANKS);b.box(-10,1,-9,1,5,-9,Material.MUD_BRICKS);b.box(-10,1,-8,-10,4,3,Material.MUD_BRICKS);
        for(int x:new int[]{-10,1}){b.post(x,-9,7,Material.STRIPPED_SPRUCE_LOG);b.post(x,3,4,Material.SPRUCE_FENCE);}
        for(int z=-10;z<=4;z++)for(int x=-11;x<=2;x++){
            int y=5+(3-z)/4;b.put(x,y-1,z,Material.SPRUCE_PLANKS);b.put(x,y,z,Material.SPRUCE_SLAB);
        }
        b.box(-9,1,-8,-4,1,-8,Material.SPRUCE_SLAB);b.box(-9,2,-8,-4,4,-8,Material.BOOKSHELF);
        b.put(-8,1,-4,Material.BREWING_STAND);b.put(-9,1,-1,Material.CAULDRON);b.put(-5,1,-2,Material.CRAFTING_TABLE);
        b.lamp(-3,4,-5);b.put(-7,2,-8,Material.POTTED_FERN);b.put(-4,2,-8,Material.POTTED_ALLIUM);
        b.floor(3,-9,10,1,Material.STONE_BRICKS);
        for(int dx=0;dx<=7;dx++)for(int z=-9;z<=1;z++){
            int top=3+(int)Math.round(3*Math.sin(dx*Math.PI/7));b.put(3+dx,top,z,z%4==0?Material.SPRUCE_SLAB:Material.GLASS);
            if(dx==0||dx==7)b.box(3+dx,1,z,3+dx,top-1,z,z%4==0?Material.SPRUCE_FENCE:Material.GLASS_PANE);
            if(z==-9||z==1)b.box(3+dx,1,z,3+dx,top-1,z,Material.GLASS_PANE);
        }
        b.box(5,1,1,7,3,1,Material.AIR);b.box(5,1,-7,8,1,-2,Material.MOSS_BLOCK);
        for(int z=-6;z<=-2;z+=2){b.put(6,2,z,Material.FERN);b.put(8,2,z,Material.FLOWERING_AZALEA);}
        for(int z:new int[]{5,9}){b.planter(7,z,Material.SPRUCE_SLAB,Material.ALLIUM);b.planter(13,z,Material.SPRUCE_SLAB,Material.FERN);}
        b.path(0,8,-3,4,Material.COARSE_DIRT);
    }
    private static void archive(Builder b){
        for(int y=0;y<=8;y++)b.ellipse(0,y,-4,7.5,7.5,y==0?Material.QUARTZ_BRICKS:Material.CALCITE);
        for(int y=1;y<=8;y++)b.ellipse(0,y,-4,5.9,5.9,Material.AIR);
        for(int y:new int[]{1,7,9})b.ring(0,y,-4,7.4,7.4,.6,Material.CHISELED_QUARTZ_BLOCK);
        b.box(-2,1,2,2,5,4,Material.AIR);
        for(int side:new int[]{-1,1}){b.box(side*6,2,-7,side*6,6,-1,Material.LIGHT_BLUE_STAINED_GLASS_PANE);b.box(side*4,1,-9,side*4,4,-9,Material.BOOKSHELF);b.box(side*5,1,-7,side*5,4,-4,Material.BOOKSHELF);}
        b.box(-2,1,-10,2,4,-10,Material.BOOKSHELF);b.put(-2,1,-5,Material.LECTERN);
        for(int h=0;h<=6;h++){
            double radius=8*Math.cos(h/7.0*Math.PI/2);
            for(int x=-(int)Math.ceil(radius);x<=radius;x++)for(int z=-(int)Math.ceil(radius);z<=radius;z++){
                if(Math.hypot(x,z)>radius||x>3&&z>1)continue;
                b.put(x,10+h,z-4,h%3==0?Material.WAXED_CUT_COPPER:Material.WAXED_OXIDIZED_CUT_COPPER);
            }
        }
        b.floor(-12,-2,-8,5,Material.QUARTZ_BRICKS);for(int x:new int[]{-12,-8})b.post(x,5,5,Material.QUARTZ_PILLAR);
        b.box(-12,6,-2,-8,6,5,Material.QUARTZ_SLAB);b.bench(-11,1,3,-9,Material.SMOOTH_QUARTZ_STAIRS,BlockFace.SOUTH);
        b.path(0,8,0,5,Material.POLISHED_ANDESITE);b.lamp(3,6,0);b.put(0,16,-4,Material.AMETHYST_BLOCK);
    }
    private static void observatory(Builder b){
        b.ellipse(-1,0,-3,10,9,Material.STONE_BRICKS);b.ellipse(-1,1,-3,9.5,8.5,Material.POLISHED_ANDESITE);
        b.ellipse(-1,2,-3,9,8,Material.CALCITE);b.ring(-1,2,-3,8.5,7.5,.5,Material.WAXED_CUT_COPPER);
        for(int z=5;z<=9;z++){int y=Math.max(0,Math.min(2,(9-z)/2));b.box(-2,0,z,2,y,z,Material.STONE_BRICKS);b.box(-2,y+1,z,2,y+3,z,Material.AIR);}
        for(int i=0;i<10;i++){
            double a=(i+.5)*Math.PI/5;int x=-1+(int)Math.round(Math.cos(a)*9),z=-3+(int)Math.round(Math.sin(a)*8);
            if(z>3&&Math.abs(x)<4)continue;b.box(x,2,z,x,3,z,Material.STONE_BRICK_WALL);b.put(x,4,z,Material.WAXED_CUT_COPPER_SLAB);
        }
        b.box(-4,3,-5,-2,4,-3,Material.CHISELED_QUARTZ_BLOCK);b.box(-3,5,-4,-3,7,-4,Material.WAXED_CUT_COPPER);
        for(int i=0;i<64;i++){
            double a=i*Math.PI/32;b.put(-3+(int)Math.round(Math.cos(a)*3),8+(int)Math.round(Math.sin(a)*3),-4,Material.WAXED_EXPOSED_CUT_COPPER);
            b.put(-3,8+(int)Math.round(Math.sin(a)*2),-4+(int)Math.round(Math.cos(a)*3),Material.WAXED_OXIDIZED_COPPER);
        }
        b.put(-3,8,-4,Material.AMETHYST_BLOCK);b.box(4,3,-7,6,4,-5,Material.CHISELED_STONE_BRICKS);b.box(5,5,-6,5,7,-6,Material.WAXED_CUT_COPPER);
        for(int i=0;i<7;i++){int x=5+i,y=7+i/2,z=-6;b.box(x,y-1,z-1,x,y+1,z+1,Material.WAXED_EXPOSED_CUT_COPPER);b.put(x,y,z,Material.AIR);if(i==6)b.put(x,y,z,Material.LIGHT_BLUE_STAINED_GLASS);}
        b.bench(-7,3,0,-4,Material.SMOOTH_QUARTZ_STAIRS,BlockFace.SOUTH);b.lamp(-8,3,-7);
    }
    private static void gate(Builder b){
        b.floor(-8,-5,8,4,Material.QUARTZ_BRICKS);b.arch(0,-1,6,8,6,Material.CHISELED_QUARTZ_BLOCK,Material.QUARTZ_BRICKS);
        for(int side:new int[]{-1,1}){
            b.box(side*6,1,-2,side*6,9,0,Material.QUARTZ_PILLAR);b.box(side<0?-14:8,1,-3,side<0?-8:14,5,-1,Material.CALCITE);
            b.box(side<0?-14:8,6,-3,side<0?-8:14,6,-1,Material.QUARTZ_SLAB);b.post(side*9,-2,8,Material.QUARTZ_PILLAR);
            b.put(side*9,9,-2,Material.WAXED_OXIDIZED_CUT_COPPER_SLAB);b.box(side*6,9,-2,side*6,10,0,Material.WAXED_EXPOSED_CUT_COPPER);b.lamp(side*5,6,0);
        }
        b.box(-14,3,-3,-11,6,-1,Material.AIR);b.put(-12,1,-2,Material.MOSS_BLOCK);
        b.box(9,1,-9,13,4,-9,Material.QUARTZ_BRICKS);b.box(13,1,-8,13,4,-5,Material.CALCITE);
        b.floor(8,-9,13,-4,Material.QUARTZ_BRICKS);b.box(8,5,-9,13,5,-5,Material.QUARTZ_SLAB);
        b.path(0,8,0,4,Material.POLISHED_ANDESITE);b.path(2,3,10,3,Material.POLISHED_ANDESITE);b.path(10,3,10,-4,Material.POLISHED_ANDESITE);
        b.put(11,1,-8,Material.DECORATED_POT);b.put(-10,1,3,Material.POTTED_AZALEA_BUSH);
        b.put(0,15,-1,Material.AMETHYST_BLOCK);b.put(0,16,-1,Material.QUARTZ_SLAB);
    }
    private static BlockFace rotate(BlockFace face,int rotation){
        if(face==null)return null;var directions=List.of(BlockFace.NORTH,BlockFace.EAST,BlockFace.SOUTH,BlockFace.WEST);
        int index=directions.indexOf(face);return index<0?face:directions.get((index+rotation)%4);
    }
    public static BlockData state(Material material,int rotation){return state(material,null,rotation,false);}
    public static BlockData state(Material material,BlockFace facing,int rotation,boolean upper){
        return STATES.computeIfAbsent(new Style(material,facing,rotation,upper),s->{
            var data=s.material.createBlockData();BlockFace face=rotate(s.facing,s.rotation);
            if(data instanceof org.bukkit.block.data.type.Leaves leaves)leaves.setPersistent(true);
            if(data instanceof org.bukkit.block.data.type.Farmland soil)soil.setMoisture(soil.getMaximumMoisture());
            if(data instanceof org.bukkit.block.data.Ageable crop)crop.setAge(crop.getMaximumAge());
            if(data instanceof org.bukkit.block.data.Directional d){if(face==null)face=rotate(BlockFace.SOUTH,s.rotation);if(d.getFaces().contains(face))d.setFacing(face);}
            if(data instanceof org.bukkit.block.data.Orientable axis&&face!=null){Axis value=face==BlockFace.EAST||face==BlockFace.WEST?Axis.X:face==BlockFace.NORTH||face==BlockFace.SOUTH?Axis.Z:Axis.Y;if(axis.getAxes().contains(value))axis.setAxis(value);}
            if(data instanceof org.bukkit.block.data.type.Stairs stairs)stairs.setHalf(s.upper?org.bukkit.block.data.Bisected.Half.TOP:org.bukkit.block.data.Bisected.Half.BOTTOM);
            if(data instanceof org.bukkit.block.data.type.Slab slab)slab.setType(s.upper?org.bukkit.block.data.type.Slab.Type.TOP:org.bukkit.block.data.type.Slab.Type.BOTTOM);
            if(data instanceof org.bukkit.block.data.type.TrapDoor trap){trap.setOpen(true);trap.setHalf(org.bukkit.block.data.Bisected.Half.TOP);}
            if(data instanceof org.bukkit.block.data.type.Lantern lamp)lamp.setHanging(s.facing==BlockFace.UP);
            if(data instanceof org.bukkit.block.data.type.Chest chest)chest.setType(org.bukkit.block.data.type.Chest.Type.SINGLE);
            if(data instanceof org.bukkit.block.data.type.Campfire fire)fire.setLit(true);return data;
        });
    }
    public static void render(VoidGenerator generator,ChunkData data,int cx,int cz,ExplorationLayout.Site site){
        if(site.legacyDesign()){ExplorationBlueprintV1.render(generator,data,cx,cz,site);return;}
        if(site.designVersion()==2){ExplorationBlueprintV2.render(generator,data,cx,cz,site);return;}
        var voxels=blocks(site.kind());var floors=new HashSet<Long>();
        var lookup=LOOKUPS.computeIfAbsent(site.kind(),kind->{var map=new HashMap<Point,Voxel>();for(var v:blocks(kind))map.put(new Point(v.x(),v.y(),v.z()),v);return Map.copyOf(map);});
        for(var v:voxels)if(v.y==0&&!v.material.isAir())floors.add(SkyPlacementHistory.key(v.x,v.z));
        for(long column:floors){
            int[] p=site.transform((int)(column>>32),(int)column);int lx=p[0]-cx*16,lz=p[1]-cz*16;
            if(lx<0||lx>15||lz<0||lz>15)continue;var s=generator.surface(p[0],p[1]);
            for(int y=Math.max(data.getMinHeight(),Math.min(s.top()-2,site.floorY()-4));y<site.floorY();y++)data.setBlock(lx,y,lz,Material.STONE);
            for(int y=site.floorY()+1;y<=site.floorY()+27;y++)data.setBlock(lx,y,lz,Material.AIR);
        }
        for(var v:voxels){
            int[] p=site.transform(v.x,v.z);int lx=p[0]-cx*16,lz=p[1]-cz*16,y=site.floorY()+v.y;
            if(lx>=0&&lx<16&&lz>=0&&lz<16&&y>=data.getMinHeight()&&y<data.getMaxHeight()){
                var placed=state(v.material,v.facing,site.rotation(),v.upper);
                if(placed instanceof org.bukkit.block.data.MultipleFacing||placed instanceof org.bukkit.block.data.type.Wall){
                    placed=placed.clone();
                    for(var direction:List.of(BlockFace.NORTH,BlockFace.EAST,BlockFace.SOUTH,BlockFace.WEST)){
                        var neighbor=lookup.get(new Point(v.x+direction.getModX(),v.y,v.z+direction.getModZ()));
                        boolean connect=neighbor!=null&&(neighbor.material.isOccluding()||neighbor.material.name().endsWith("_PANE")||neighbor.material.name().endsWith("_FENCE")||neighbor.material.name().endsWith("_WALL"));
                        var face=rotate(direction,site.rotation());
                        if(placed instanceof org.bukkit.block.data.MultipleFacing multi&&multi.getAllowedFaces().contains(face))multi.setFace(face,connect);
                        if(placed instanceof org.bukkit.block.data.type.Wall wall)wall.setHeight(face,connect?org.bukkit.block.data.type.Wall.Height.LOW:org.bukkit.block.data.type.Wall.Height.NONE);
                    }
                }
                data.setBlock(lx,y,lz,placed);
            }
        }
        int[] edge=site.transform(0,18);int difference=generator.surface(edge[0],edge[1]).top()-site.floorY();
        for(int distance=11;distance<=18;distance++)for(int across=-1;across<=1;across++){
            int[] p=site.transform(across,distance);int lx=p[0]-cx*16,lz=p[1]-cz*16;
            if(lx<0||lx>15||lz<0||lz>15)continue;int y=site.floorY()+(int)Math.round(difference*(distance-11)/7.0);
            for(int below=y-2;below<=y;below++)data.setBlock(lx,below,lz,Material.COBBLESTONE);
            for(int above=y+1;above<=Math.max(y+3,site.floorY()+3);above++)data.setBlock(lx,above,lz,Material.AIR);
        }
    }
}
