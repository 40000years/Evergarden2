package com.example.voidscape.world;

import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.block.data.type.Lantern;
import org.bukkit.block.data.type.Slab;
import org.bukkit.generator.ChunkGenerator.ChunkData;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Immutable, chunk-indexed vanilla block geometry shared by the two new landmarks. */
public final class LandmarkBlueprint {
    public record Point(int x,int y,int z) {}
    public record Block(int x,int y,int z,Material material,BlockFace facing) {}
    private record Style(Material material,BlockFace facing) {}
    private final Map<Point,Block> voxels=new HashMap<>();
    private final Map<Long,List<Block>> chunks=new HashMap<>();
    private final Map<Style,BlockData> data=new ConcurrentHashMap<>();
    private List<Block> blocks;
    private final List<Point> route=new ArrayList<>();
    private boolean frozen;
    private static long chunkKey(int x,int z){return ((long)x<<32)|(z&0xffffffffL);}
    public static double noise(int x,int y,int z){return (DungeonLayout.mix(x*341873128712L+y*73428767L+z*132897987541L)&65535)/65535.0;}
    public void put(int x,int y,int z,Material m){put(x,y,z,m,null);}
    public void put(int x,int y,int z,Material m,BlockFace face){
        if(frozen)throw new IllegalStateException("Frozen blueprint");
        if(Math.abs(x)>71||Math.abs(z)>71||y<32||y>210)throw new IllegalArgumentException("Landmark bounds: "+x+","+y+","+z);
        Point p=new Point(x,y,z);
        if(m==Material.AIR)voxels.remove(p);else voxels.put(p,new Block(x,y,z,m,face));
    }
    public Material at(int x,int y,int z){Block b=voxels.get(new Point(x,y,z));return b==null?Material.AIR:b.material();}
    public void box(int x1,int y1,int z1,int x2,int y2,int z2,Material m){
        for(int x=x1;x<=x2;x++)for(int y=y1;y<=y2;y++)for(int z=z1;z<=z2;z++)put(x,y,z,m);
    }
    public void ball(double cx,double cy,double cz,double rx,double ry,double rz,Material m){
        for(int x=(int)Math.floor(cx-rx);x<=Math.ceil(cx+rx);x++)
            for(int y=(int)Math.floor(cy-ry);y<=Math.ceil(cy+ry);y++)
                for(int z=(int)Math.floor(cz-rz);z<=Math.ceil(cz+rz);z++)
                    if(sq((x-cx)/rx)+sq((y-cy)/ry)+sq((z-cz)/rz)<=1)put(x,y,z,m);
    }
    private static double sq(double n){return n*n;}
    public void line(double ax,double ay,double az,double bx,double by,double bz,double radius,Material m){
        int steps=Math.max(1,(int)Math.ceil(Math.sqrt(sq(ax-bx)+sq(ay-by)+sq(az-bz))*2));
        for(int i=0;i<=steps;i++){double t=i/(double)steps;ball(ax+(bx-ax)*t,ay+(by-ay)*t,az+(bz-az)*t,radius,radius,radius,m);}
    }
    public void disk(int cx,int y,int cz,int radius,Material m){
        for(int x=-radius;x<=radius;x++)for(int z=-radius;z<=radius;z++)if(x*x+z*z<=radius*radius)put(cx+x,y,cz+z,m);
    }
    public void ring(int cx,int y,int cz,double radius,double width,Material m){
        int extent=(int)Math.ceil(radius+width);
        for(int x=-extent;x<=extent;x++)for(int z=-extent;z<=extent;z++)if(Math.abs(Math.hypot(x,z)-radius)<=width)put(cx+x,y,cz+z,m);
    }
    public void island(int cx,int top,int cz,int rx,int rz,int depth){
        for(int x=-rx;x<=rx;x++)for(int z=-rz;z<=rz;z++){
            double r=Math.sqrt(sq(x/(double)rx)+sq(z/(double)rz));
            double edge=1+Math.sin(x*.39+z*.23)*.035+Math.cos(z*.43)*.03;
            if(r>edge)continue;
            int drop=(int)(depth*Math.pow(Math.max(0,1-r),.62)+5+noise(x/3,top,z/3)*9);
            for(int y=Math.max(33,top-drop);y<=top;y++){
                int d=top-y;double n=noise((cx+x)/3,y/7,(cz+z)/3);
                double vein=noise((cx+x)/4,0,(cz+z)/4);
                Material m=d==0?Material.GRASS_BLOCK:d<3?Material.ROOTED_DIRT:
                        vein<.14?Material.CALCITE:vein<.28?Material.DIORITE:
                        n<.13?Material.MOSSY_COBBLESTONE:n<.4?Material.TUFF:n<.72?Material.STONE:Material.ANDESITE;
                put(cx+x,y,cz+z,m);
            }
        }
    }
    public void cherry(int x,int y,int z,int height){
        for(int dx:new int[]{-4,4})line(x,y+height-4,z,x+dx,y+height,z+1,1.05,Material.CHERRY_LOG);
        ball(x-4,y+height,z,5,2.5,4,Material.CHERRY_LEAVES);
        ball(x+4,y+height+1,z+1,5,3,5,Material.CHERRY_LEAVES);
        ball(x,y+height+3,z,5.5,2.5,5,Material.CHERRY_LEAVES);
        for(int dy=0;dy<height;dy++)put(x,y+dy,z,Material.CHERRY_LOG);
    }
    public void garland(int x,int top,int z,int length,boolean purple){
        for(int d=0;d<length;d++){
            Material m=purple&&d%5>=2?Material.PURPLE_STAINED_GLASS:Material.AZALEA_LEAVES;
            int dx=(int)Math.round(Math.sin(d*.19)*1.2),dz=(int)Math.round(Math.sin(d*.11));
            put(x+dx,top-d,z+dz,m);
            if(d%6==1)put(x+dx+1,top-d,z+dz,Material.FLOWERING_AZALEA_LEAVES);
        }
    }
    public void rockSpire(int x,int top,int z,int width,int length){
        for(int d=0;d<length;d++){
            double r=width*Math.pow(1-d/(double)length,.8);
            for(int dx=-width;dx<=width;dx++)for(int dz=-width;dz<=width;dz++)if(dx*dx+dz*dz<=r*r)
                put(x+dx,top-d,z+dz,noise(x+dx,0,z+dz)<.28?Material.CALCITE:Material.ANDESITE);
        }
    }
    public void gardenDetails(int y){
        Material[] flowers={Material.ALLIUM,Material.AZURE_BLUET,Material.PINK_TULIP,Material.BLUE_ORCHID};
        for(int x=-60;x<=60;x++)for(int z=-60;z<=60;z++){
            if(at(x,y,z)!=Material.GRASS_BLOCK||at(x,y+1,z)!=Material.AIR)continue;
            double n=noise(x,y,z);
            if(n<.035)put(x,y+1,z,flowers[Math.floorMod(x+z,flowers.length)]);
            else if(n>.96)put(x,y+1,z,Material.MOSS_CARPET);
            else if(n>.945&&noise(x/4,y,z/4)>.6)put(x,y+1,z,Material.FLOWERING_AZALEA_LEAVES);
        }
    }
    public void crystal(int x,int top,int z,int length){
        for(int d=0;d<length;d++){
            double r=2.6*(1-d/(double)length);
            for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++)if(dx*dx+dz*dz<=r*r)
                put(x+dx,top-d,z+dz,d%6==0?Material.SEA_LANTERN:Material.CYAN_STAINED_GLASS);
        }
    }
    public void waterfall(int x,int top,int z,int bottom,int width){
        // Glass-lined channels and a catch basin bound the flowing water footprint.
        for(int y=bottom;y<=top;y++)for(int dx=-width;dx<=width;dx++){
            put(x+dx,y,z+1,Material.CYAN_STAINED_GLASS);
            put(x+dx,y,z,Material.WATER);
            put(x+dx,y,z-1,Material.LIGHT_BLUE_STAINED_GLASS);
        }
        for(int y=bottom;y<=top;y++){put(x-width-1,y,z,Material.CYAN_STAINED_GLASS);put(x+width+1,y,z,Material.CYAN_STAINED_GLASS);}
        box(x-width-1,bottom-1,z-1,x+width+1,bottom-1,z+1,Material.SEA_LANTERN);
        box(x-width-1,bottom,z-1,x+width+1,bottom,z-1,Material.CYAN_STAINED_GLASS);
    }
    public void lamp(int x,int y,int z){
        put(x,y,z,Material.CHISELED_STONE_BRICKS);
        put(x,y+1,z,Material.LANTERN);
    }
    public void alcove(int y,int z){
        box(3,y-1,z-1,7,y-1,z+1,Material.SPRUCE_PLANKS);
        box(3,y,z-1,7,y+3,z,Material.AIR);
        box(3,y,z+1,7,y+2,z+1,Material.BOOKSHELF);
        box(3,y+3,z+1,7,y+3,z+1,Material.SPRUCE_SLAB);
        put(3,y+4,z+1,Material.LANTERN);put(7,y+4,z+1,Material.LANTERN);
    }
    public void path(List<Point> points,int width,Material material){
        Map<Long,Point> floors=new HashMap<>();
        for(Point p:points)for(int dx=-width;dx<=width;dx++)for(int dz=-width;dz<=width;dz++){
            int x=p.x+dx,z=p.z+dz;long key=chunkKey(x,z);
            Point old=floors.get(key);if(old==null||p.y>old.y)floors.put(key,new Point(x,p.y,z));
        }
        for(Point p:floors.values()){
            BlockFace ascent=null;
            for(BlockFace face:List.of(BlockFace.NORTH,BlockFace.SOUTH,BlockFace.EAST,BlockFace.WEST)){
                Point lower=floors.get(chunkKey(p.x-face.getModX(),p.z-face.getModZ()));
                if(lower!=null&&lower.y==p.y-1){ascent=face;break;}
            }
            Material tread=ascent==null?material:material==Material.SPRUCE_PLANKS?Material.SPRUCE_STAIRS:Material.SMOOTH_QUARTZ_STAIRS;
            put(p.x,p.y,p.z,tread,ascent);
            for(int dy=1;dy<=3;dy++)put(p.x,p.y+dy,p.z,Material.AIR);
        }
        for(Point p:points)route.add(floors.get(chunkKey(p.x,p.z)));
    }
    public LandmarkBlueprint freeze(){
        blocks=voxels.values().stream().sorted(Comparator.comparingInt(Block::x).thenComparingInt(Block::y).thenComparingInt(Block::z)).toList();
        for(Block b:blocks)chunks.computeIfAbsent(chunkKey(Math.floorDiv(b.x,16),Math.floorDiv(b.z,16)),k->new ArrayList<>()).add(b);
        chunks.replaceAll((k,v)->List.copyOf(v));frozen=true;return this;
    }
    public List<Block> blocks(){return blocks;}
    public List<Point> route(){return List.copyOf(route);}
    public void render(ChunkData target,int cx,int cz,int originX,int originZ){
        int localX=cx-Math.floorDiv(originX,16),localZ=cz-Math.floorDiv(originZ,16);
        for(Block b:chunks.getOrDefault(chunkKey(localX,localZ),List.of())){
            if(b.y<target.getMinHeight()||b.y>=target.getMaxHeight())continue;
            BlockData state=data.computeIfAbsent(new Style(b.material,b.facing),style->{
                BlockData result=style.material.createBlockData();
                if(result instanceof Leaves leaves)leaves.setPersistent(true);
                if(result instanceof Lantern lantern)lantern.setHanging(style.facing==BlockFace.UP);
                if(result instanceof Slab slab)slab.setType(Slab.Type.TOP);
                if(style.facing!=null&&result instanceof Directional directional)directional.setFacing(style.facing);
                return result;
            });
            target.setBlock(Math.floorMod(b.x,16),b.y,Math.floorMod(b.z,16),state);
        }
    }
}
