package com.example.voidscape.world;

import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.generator.ChunkGenerator.ChunkData;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Twelve small, walkable vanilla-block destinations, rotated in four directions. */
final class ExplorationBlueprintV1 {
    public static final int CHEST_X=5,CHEST_Z=-4;
    public record Point(int x,int y,int z){}
    public record Voxel(int x,int y,int z,Material material){}
    private record Style(Material material,int rotation){}
    private static final Map<ExplorationLayout.Kind,List<Voxel>> BUILDS=new ConcurrentHashMap<>();
    private static final Map<Style,BlockData> STATES=new ConcurrentHashMap<>();
    private ExplorationBlueprintV1(){}
    public static List<Voxel> blocks(ExplorationLayout.Kind kind){return BUILDS.computeIfAbsent(kind,ExplorationBlueprintV1::build);}
    private static final class Builder {
        final Map<Point,Voxel> blocks=new LinkedHashMap<>();
        void put(int x,int y,int z,Material m){
            if(Math.abs(x)>18||Math.abs(z)>18||y<0||y>27)throw new IllegalArgumentException("Small structure bounds");
            blocks.put(new Point(x,y,z),new Voxel(x,y,z,m));
        }
        void box(int ax,int ay,int az,int bx,int by,int bz,Material m){
            for(int x=ax;x<=bx;x++)for(int z=az;z<=bz;z++)for(int y=ay;y<=by;y++)put(x,y,z,m);
        }
        void disk(int cx,int y,int cz,int radius,Material m){
            for(int dx=-radius;dx<=radius;dx++)for(int dz=-radius;dz<=radius;dz++)
                if(dx*dx+dz*dz<=radius*radius)put(cx+dx,y,cz+dz,m);
        }
        void beam(int ax,int ay,int az,int bx,int by,int bz,Material m){
            int steps=Math.max(1,Math.max(Math.abs(bx-ax),Math.max(Math.abs(by-ay),Math.abs(bz-az)))*2);
            for(int i=0;i<=steps;i++){double t=i/(double)steps;put((int)Math.round(ax+(bx-ax)*t),(int)Math.round(ay+(by-ay)*t),(int)Math.round(az+(bz-az)*t),m);}
        }
        void lamp(int x,int y,int z){put(x,y,z,Material.CHISELED_STONE_BRICKS);put(x,y+1,z,Material.LANTERN);}
        void house(int rx,int rz,int wallHeight,Material wall,Material roof){
            box(-rx,1,-rz,rx,wallHeight,rz,wall);box(-rx+1,1,-rz+1,rx-1,wallHeight,rz-1,Material.AIR);
            box(-1,1,rz,1,3,rz,Material.AIR);
            for(int x:new int[]{-rx,rx})box(x,2,-2,x,4,1,Material.GLASS_PANE);
            for(int x=-rx-1;x<=rx+1;x++){
                int y=wallHeight+1+(rx+1-Math.abs(x))/2;
                box(x,y,-rz-1,x,y,rz+1,roof);
            }
        }
        void planter(int x,int z,Material edge,Material plant){
            box(x-2,0,z-2,x+2,0,z+2,edge);box(x-1,1,z-1,x+1,1,z+1,Material.MOSS_BLOCK);
            put(x,2,z,plant);
        }
    }
    private static List<Voxel> build(ExplorationLayout.Kind kind){
        var b=new Builder();boolean frost=kind.biome()==BiomeTerrain.Kind.GLACIER;
        boolean forest=kind.biome()==BiomeTerrain.Kind.ANCIENT_FOREST;
        boolean lunar=kind.biome()==BiomeTerrain.Kind.LUNAR_CITY;
        var paving=frost?Material.PACKED_ICE:forest?Material.MOSSY_STONE_BRICKS:lunar?Material.CALCITE:Material.MOSS_BLOCK;
        int shape=kind.ordinal()%3;
        for(int x=-16;x<=16;x++)for(int z=-16;z<=16;z++){
            if(!footprint(shape,x,z))continue;
            b.put(x,0,z,Math.floorMod(x*7+z*11,19)==0?Material.CHISELED_STONE_BRICKS:paving);
        }
        switch(kind){
            case BLOSSOM_PAVILION->{
                b.disk(0,0,-2,9,Material.SMOOTH_QUARTZ);
                for(int x:new int[]{-7,7})for(int z:new int[]{-8,4}){
                    b.box(x,1,z,x,7,z,Material.CHERRY_LOG);b.lamp(x,1,z+1);
                    b.box(x-2,7,z-2,x+2,8,z+2,Material.CHERRY_LEAVES);
                }
                for(int y=8;y<=10;y++){int r=11-(y-8)*2;b.box(-r,y,-r-2,r,y,r-2,Material.WAXED_OXIDIZED_COPPER);}
                b.box(-5,1,-7,-2,1,-6,Material.CHERRY_SLAB);
                b.box(-4,1,1,-2,1,2,Material.CHERRY_SLAB);
                b.planter(-12,-3,Material.SMOOTH_QUARTZ,Material.PINK_TULIP);
                b.planter(12,1,Material.SMOOTH_QUARTZ,Material.ALLIUM);
                b.put(0,12,-2,Material.PEARLESCENT_FROGLIGHT);
            }
            case GARDENER_COTTAGE->{
                b.house(7,6,5,Material.CHERRY_PLANKS,Material.DARK_PRISMARINE);
                for(int x:new int[]{-7,7})for(int z:new int[]{-6,6})b.box(x,1,z,x,5,z,Material.STRIPPED_CHERRY_LOG);
                b.box(-5,1,-5,-3,3,-5,Material.BOOKSHELF);b.put(-4,1,-3,Material.CRAFTING_TABLE);
                b.box(-11,0,2,-9,1,8,Material.OAK_TRAPDOOR);
                for(int z=2;z<=8;z++)b.put(-10,2,z,z%2==0?Material.ALLIUM:Material.WHITE_TULIP);
                b.box(-5,6,-3,-4,11,-2,Material.BRICKS);b.put(-5,12,-3,Material.COBBLESTONE_WALL);
                b.planter(11,-8,Material.CHERRY_PLANKS,Material.FLOWERING_AZALEA);
            }
            case SPRING_WELL->{
                b.disk(0,0,0,10,Material.MOSSY_COBBLESTONE);
                b.box(-9,0,-6,-3,2,0,Material.STONE_BRICKS);b.box(-8,1,-5,-4,2,-1,Material.WATER);
                for(int x:new int[]{-9,-3})for(int z:new int[]{-6,0})b.box(x,3,z,x,6,z,Material.OAK_FENCE);
                for(int x=-5;x<=5;x++)b.box(x-6,7+(5-Math.abs(x))/2,-8,x-6,7+(5-Math.abs(x))/2,2,Material.SPRUCE_PLANKS);
                b.box(-11,1,-6,-9,6,-4,Material.MOSSY_STONE_BRICKS);
                b.box(-11,7,-6,-5,7,-4,Material.MOSSY_STONE_BRICKS);
                b.planter(10,6,Material.MOSSY_COBBLESTONE,Material.BLUE_ORCHID);
                b.planter(-8,9,Material.MOSSY_COBBLESTONE,Material.FLOWERING_AZALEA);
            }
            case EXPEDITION_CAMP->{
                // Canvas tents, crates and a stone fire ring leave the central trail open.
                for(int cx:new int[]{-8,7}){
                    for(int x=-4;x<=4;x++)b.box(cx+x,1,-9,cx+x,6-Math.abs(x),-2,
                        x%3==0?Material.LIGHT_BLUE_WOOL:Material.WHITE_WOOL);
                    for(int x=-3;x<=3;x++)b.box(cx+x,1,-8,cx+x,5-Math.abs(x),-3,Material.AIR);
                    b.box(cx-1,1,-2,cx+1,3,-2,Material.AIR);
                    b.box(cx-3,0,-8,cx+3,0,-3,Material.SPRUCE_PLANKS);b.put(cx-2,1,-7,Material.BARREL);
                }
                b.disk(-7,0,7,3,Material.COBBLESTONE);b.put(-7,1,7,Material.CAMPFIRE);
                b.box(-11,1,7,-10,1,9,Material.SPRUCE_SLAB);
                b.box(8,1,7,11,2,8,Material.STRIPPED_SPRUCE_LOG);b.lamp(12,1,4);
            }
            case FROST_CHAPEL->{
                b.house(7,8,9,Material.PACKED_ICE,Material.BLUE_ICE);
                for(int x:new int[]{-7,7})for(int z:new int[]{-8,0,8})b.box(x,1,z,x,10,z,Material.QUARTZ_PILLAR);
                b.box(-3,1,-7,3,2,-5,Material.CHISELED_QUARTZ_BLOCK);b.put(0,3,-6,Material.SEA_LANTERN);
                for(int x:new int[]{-3,3})b.box(x,1,0,x,1,4,Material.SMOOTH_QUARTZ_SLAB);
                b.box(0,13,-1,0,18,-1,Material.BLUE_ICE);b.box(-2,16,-1,2,16,-1,Material.SEA_LANTERN);
                b.lamp(-11,1,10);b.lamp(11,1,10);
            }
            case ICE_WATCH->{
                for(int x:new int[]{-6,6})for(int z:new int[]{-6,6})b.box(x,1,z,x,13,z,Material.STRIPPED_SPRUCE_LOG);
                b.box(-7,7,-7,7,7,7,Material.SPRUCE_PLANKS);b.box(-7,14,-7,7,14,7,Material.SPRUCE_PLANKS);
                for(int y:new int[]{8,15}){
                    b.box(-7,y,-7,7,y,-7,Material.SPRUCE_FENCE);b.box(-7,y,7,7,y,7,Material.SPRUCE_FENCE);
                    b.box(-7,y,-7,-7,y,7,Material.SPRUCE_FENCE);b.box(7,y,-7,7,y,7,Material.SPRUCE_FENCE);
                }
                for(int step=0;step<=13;step++){
                    int z=13-step;b.box(-5,step,z,-3,step,z,Material.SPRUCE_PLANKS);
                    b.box(-5,step+1,z,-3,step+3,z,Material.AIR);
                }
                b.box(-8,19,-8,8,19,8,Material.DARK_PRISMARINE);
                for(int x:new int[]{-6,6})for(int z:new int[]{-6,6})b.box(x,15,z,x,18,z,Material.SPRUCE_FENCE);
                b.box(0,20,0,0,24,0,Material.SPRUCE_FENCE);b.box(1,23,0,3,24,0,Material.LIGHT_BLUE_WOOL);
            }
            case ROOT_LODGE->{
                b.house(7,6,5,Material.DARK_OAK_PLANKS,Material.MOSS_BLOCK);
                // Ancient roots embrace the roof, rather than repeating another cabin outline.
                for(int side:new int[]{-1,1}){
                    b.beam(side*13,1,3,side*9,6,0,Material.DARK_OAK_LOG);
                    b.beam(side*9,6,0,side*5,10,-5,Material.DARK_OAK_LOG);
                    b.box(side*5-2,9,-7,side*5+2,10,-3,Material.AZALEA_LEAVES);
                }
                b.box(-5,1,-5,-4,3,-5,Material.BOOKSHELF);b.put(-4,1,-2,Material.BARREL);
                b.planter(-11,-9,Material.MOSSY_COBBLESTONE,Material.RED_MUSHROOM);
                b.put(11,1,7,Material.BROWN_MUSHROOM);b.lamp(-9,1,8);
            }
            case MOSS_RUINS->{
                b.disk(0,0,0,11,Material.MOSSY_STONE_BRICKS);
                for(int x:new int[]{-10,10})for(int z:new int[]{-9,0,9}){
                    int height=4+Math.floorMod(x+z,5);b.box(x-1,1,z-1,x+1,height,z+1,Material.MOSSY_STONE_BRICKS);
                    b.box(x-2,height+1,z-2,x+2,height+1,z+2,Material.MOSS_BLOCK);
                }
                b.box(-11,8,-10,-3,8,-8,Material.CRACKED_STONE_BRICKS);
                b.box(-11,9,-10,-6,9,-8,Material.FLOWERING_AZALEA_LEAVES);
                b.box(8,1,4,12,3,6,Material.MOSSY_COBBLESTONE);
                b.planter(-4,-6,Material.CHISELED_STONE_BRICKS,Material.FERN);
                b.put(-7,1,6,Material.BROWN_MUSHROOM);
            }
            case HERBALIST_HUT->{
                b.house(7,7,5,Material.MUD_BRICKS,Material.SPRUCE_PLANKS);
                for(int x:new int[]{-7,7})for(int z:new int[]{-7,7})b.box(x,1,z,x,6,z,Material.SPRUCE_LOG);
                b.box(-5,1,-6,-3,1,-4,Material.BARREL);b.put(-5,2,-5,Material.FLOWER_POT);
                for(int z=-8;z<=9;z+=5)b.planter(-12,z,Material.SPRUCE_PLANKS,z<0?Material.FERN:Material.ALLIUM);
                b.box(9,1,4,12,1,6,Material.SPRUCE_SLAB);b.put(10,2,5,Material.FLOWER_POT);
                b.put(12,2,5,Material.BROWN_MUSHROOM);b.lamp(8,1,10);
            }
            case CRESCENT_ARCHIVE->{
                b.house(8,7,8,Material.CALCITE,Material.WAXED_OXIDIZED_COPPER);
                b.box(-6,1,-6,-4,5,-5,Material.BOOKSHELF);b.box(-7,1,-3,-7,5,2,Material.BOOKSHELF);
                b.put(-3,1,-3,Material.LECTERN);
                for(int x:new int[]{-8,8})for(int z:new int[]{-7,7})b.box(x,1,z,x,9,z,Material.QUARTZ_PILLAR);
                b.box(-2,12,-1,2,13,1,Material.CHISELED_QUARTZ_BLOCK);b.put(0,14,0,Material.AMETHYST_BLOCK);
                b.planter(-12,8,Material.CALCITE,Material.PALE_OAK_SAPLING);b.lamp(11,1,8);
            }
            case STAR_GAZE->{
                b.disk(0,0,0,12,Material.SMOOTH_QUARTZ);
                for(int i=0;i<8;i++){
                    double a=i*Math.PI/4;int x=(int)Math.round(Math.cos(a)*10),z=(int)Math.round(Math.sin(a)*10);
                    b.box(x,1,z,x,5+i%3,z,Material.QUARTZ_PILLAR);b.put(x,6+i%3,z,Material.SEA_LANTERN);
                }
                b.box(-4,1,-4,-2,3,-2,Material.CHISELED_QUARTZ_BLOCK);
                b.beam(-3,4,-3,-8,8,-9,Material.WAXED_CUT_COPPER);b.box(-9,7,-10,-7,9,-8,Material.LIGHT_BLUE_STAINED_GLASS);
                for(int x=-5;x<=5;x++)for(int z=-5;z<=5;z++)
                    if(Math.abs(Math.hypot(x,z)-5)<.6)b.put(x,0,z,Material.GOLD_BLOCK);
                b.box(8,1,-6,8,5,-6,Material.AMETHYST_BLOCK);b.put(8,6,-6,Material.END_ROD);
            }
            case MOON_GATE->{
                b.box(-12,0,-3,12,0,3,Material.POLISHED_DEEPSLATE);
                for(int x=-10;x<=10;x++)for(int y=1;y<=21;y++){
                    double outer=Math.hypot(x,y-11),inner=Math.hypot(x-3,y-13);
                    if(outer<10.4&&outer>7.9&&inner>8)b.box(x,y,-1,x,y,1,Material.CALCITE);
                }
                b.box(-10,1,0,-8,7,0,Material.CHISELED_QUARTZ_BLOCK);
                b.box(8,1,0,10,5,0,Material.CHISELED_QUARTZ_BLOCK);
                b.planter(-11,8,Material.CALCITE,Material.PALE_OAK_SAPLING);
                b.box(-12,1,-8,-10,4,-6,Material.QUARTZ_BRICKS);
                b.box(10,1,6,12,3,8,Material.MOSSY_STONE_BRICKS);
                b.lamp(-6,1,12);b.lamp(6,1,12);
            }
        }
        // Guaranteed accessible main trail and a sheltered treasure nook in every design.
        for(int z=-7;z<=16;z++){
            b.box(-1,0,z,1,0,z,lunar?Material.POLISHED_DEEPSLATE:Material.STONE_BRICKS);
            b.box(-1,1,z,1,3,z,Material.AIR);
        }
        b.box(2,0,-5,7,0,-3,Material.CHISELED_STONE_BRICKS);
        b.box(2,1,-5,7,3,-3,Material.AIR);
        b.put(CHEST_X,1,CHEST_Z,Material.CHEST);b.lamp(7,1,-6);
        return List.copyOf(b.blocks.values());
    }
    private static boolean footprint(int shape,int x,int z){return switch(shape){
        case 0->Math.abs(x)<=14&&Math.abs(z)<=14&&Math.abs(x)+Math.abs(z)<=24;
        case 1->x*x+z*z<=16*16;default->Math.abs(x)<=13&&Math.abs(z)<=15||Math.abs(x)<=4&&Math.abs(z)<=16;};}
    public static BlockData state(Material material,int rotation){return STATES.computeIfAbsent(new Style(material,rotation),s->{
        var data=s.material.createBlockData();
        if(data instanceof org.bukkit.block.data.type.Leaves leaves)leaves.setPersistent(true);
        if(data instanceof org.bukkit.block.data.Directional d){
            var face=switch(s.rotation){case 1->BlockFace.EAST;case 2->BlockFace.SOUTH;case 3->BlockFace.WEST;default->BlockFace.NORTH;};
            if(d.getFaces().contains(face))d.setFacing(face);
        }
        if(data instanceof org.bukkit.block.data.type.Chest chest)chest.setType(org.bukkit.block.data.type.Chest.Type.SINGLE);
        if(data instanceof org.bukkit.block.data.type.Campfire fire)fire.setLit(true);
        return data;
    });}
    public static void render(VoidGenerator generator,ChunkData data,int cx,int cz,ExplorationLayout.Site site){
        // Only generated terrain is written here. Population never constructs a missing building.
        for(int dx=-18;dx<=18;dx++)for(int dz=-18;dz<=18;dz++){
            if(!footprint(site.kind().ordinal()%3,dx,dz))continue;
            int[] p=site.transform(dx,dz);int lx=p[0]-cx*16,lz=p[1]-cz*16;
            if(lx<0||lx>15||lz<0||lz>15)continue;
            var s=generator.surface(p[0],p[1]);
            int bottom=Math.max(data.getMinHeight(),Math.min(s.top()-3,site.floorY()-7));
            for(int y=bottom;y<site.floorY();y++)data.setBlock(lx,y,lz,
                y<site.floorY()-2?Material.STONE:Material.MOSSY_COBBLESTONE);
            for(int y=site.floorY()+1;y<=site.floorY()+7;y++)data.setBlock(lx,y,lz,Material.AIR);
        }
        for(var v:blocks(site.kind())){
            int[] p=site.transform(v.x,v.z);int lx=p[0]-cx*16,lz=p[1]-cz*16,y=site.floorY()+v.y;
            if(lx>=0&&lx<16&&lz>=0&&lz<16&&y>=data.getMinHeight()&&y<data.getMaxHeight())
                data.setBlock(lx,y,lz,state(v.material,site.rotation()));
        }
        // The entrance follows the local slope with one-block steps and two-block headroom.
        int[] edge=site.transform(0,18);var surface=generator.surface(edge[0],edge[1]);int difference=surface.top()-site.floorY();
        for(int distance=11;distance<=18;distance++)for(int across=-1;across<=1;across++){
            int[] p=site.transform(across,distance);int lx=p[0]-cx*16,lz=p[1]-cz*16;
            if(lx<0||lx>15||lz<0||lz>15)continue;
            int y=site.floorY()+(int)Math.round(difference*(distance-11)/7.0);
            for(int below=y-3;below<=y;below++)data.setBlock(lx,below,lz,Material.STONE_BRICKS);
            for(int above=y+1;above<=y+3;above++)data.setBlock(lx,above,lz,Material.AIR);
        }
    }
}
