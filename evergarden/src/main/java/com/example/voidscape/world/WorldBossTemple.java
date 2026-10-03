package com.example.voidscape.world;

import org.bukkit.Material;
import org.bukkit.generator.ChunkGenerator.ChunkData;
import java.util.*;

/** Ancient open-air boss arena. Ordered boxes are indexed by chunk, never placed by a task. */
public final class WorldBossTemple {
    public static final int RADIUS=120,ARENA_RADIUS=80,FLOOR_Y=100,MIN_Y=40,MAX_Y=181;
    public static final int ARRIVAL_Y=105,ARRIVAL_Z=108;
    public record Box(int x1,int y1,int z1,int x2,int y2,int z2,Material material) {}
    private final List<Box> boxes=new ArrayList<>();
    private final Map<Long,List<Box>> chunks=new HashMap<>();
    private final Map<Material,org.bukkit.block.data.BlockData> leafData=new java.util.concurrent.ConcurrentHashMap<>();
    private static final class Holder {static final WorldBossTemple INSTANCE=new WorldBossTemple();}
    public static WorldBossTemple blueprint(){return Holder.INSTANCE;}
    private static long key(int x,int z){return ((long)x<<32)|(z&0xffffffffL);}
    public static boolean footprint(int x,int z,int margin){
        return Math.abs(x)<=RADIUS+margin&&Math.abs(z)<=RADIUS+margin
            &&Math.abs(x)+Math.abs(z)<=170+2*margin;
    }
    private void box(int x1,int y1,int z1,int x2,int y2,int z2,Material material){
        if(x1>x2||y1>y2||z1>z2)return;
        if(x1<-RADIUS||x2>RADIUS||z1<-RADIUS||z2>RADIUS||y1<MIN_Y||y2>MAX_Y)
            throw new IllegalArgumentException("World boss temple exceeds its reservation");
        boxes.add(new Box(x1,y1,z1,x2,y2,z2,material));
    }
    private void block(int x,int y,int z,Material m){box(x,y,z,x,y,z,m);}
    private static double noise(int x,int y,int z){return LandmarkBlueprint.noise(x,y,z);}
    private static Material ancient(int x,int y,int z){
        double n=noise(x,y,z);
        return n<.13?Material.CRACKED_STONE_BRICKS:n<.27?Material.MOSSY_STONE_BRICKS:
            n<.34?Material.TUFF_BRICKS:Material.STONE_BRICKS;
    }
    private WorldBossTemple(){
        // A tapered, faceted floating rock; one vertical box per stratum/column.
        for(int x=-RADIUS;x<=RADIUS;x++)for(int z=-RADIUS;z<=RADIUS;z++){
            if(!footprint(x,z,0))continue;
            double edge=Math.max(Math.max(Math.abs(x),Math.abs(z))/120.0,(Math.abs(x)+Math.abs(z))/170.0);
            int bottom=92-(int)(47*Math.pow(1-edge,.6))-((int)(noise(x/5,0,z/5)*6));
            box(x,bottom,z,x,87,z,noise(x/7,0,z/7)<.18?Material.CALCITE:Material.DEEPSLATE);
            box(x,88,z,x,92,z,noise(x/4,1,z/4)<.3?Material.MOSSY_COBBLESTONE:Material.TUFF);
            box(x,93,z,x,96,z,Material.STONE_BRICKS);
            double r=Math.hypot(x,z);
            int top=r<=80?FLOOR_Y:r<=88?102:r<=111?104:96;
            Material surface=r<=80?Material.SMOOTH_STONE:r<=88?Material.POLISHED_ANDESITE:
                r<=111?ancient(x,top,z):Material.MOSS_BLOCK;
            if(r>80&&r<112&&(Math.abs(x)<8||Math.abs(z)<8)){
                // Broad cardinal stairs, descending one block every three blocks.
                top=Math.min(104,100+(int)((r-80)/3));surface=Material.POLISHED_ANDESITE;
            }
            box(x,97,z,x,top-1,z,Material.STONE_BRICKS);
            block(x,top,z,surface);
            if(r<=80){
                if(Math.abs(r-77)<.65||Math.abs(r-56)<.55||Math.abs(r-32)<.55||Math.abs(r-12)<.6)
                    block(x,FLOOR_Y,z,Material.WAXED_OXIDIZED_COPPER);
                if((Math.abs(x)<=1||Math.abs(z)<=1)&&r>14&&r<77)
                    block(x,FLOOR_Y,z,Material.CHISELED_STONE_BRICKS);
                if(r<10&&(Math.abs(x)==Math.abs(z)||r<3.5))block(x,FLOOR_Y,z,Material.AMETHYST_BLOCK);
                if((Math.abs(r-77)<.65||Math.abs(r-32)<.55)&&Math.floorMod(x+z,13)==0)
                    block(x,FLOOR_Y,z,Material.SEA_LANTERN);
                // Eight inlaid spokes and glyph diamonds never obstruct combat headroom.
                for(int i=0;i<8;i++){
                    double angle=i*Math.PI/4,along=x*Math.cos(angle)+z*Math.sin(angle),across=-x*Math.sin(angle)+z*Math.cos(angle);
                    if(along>38&&along<72&&Math.abs(across)<.6)block(x,FLOOR_Y,z,Material.CALCITE);
                    if(Math.abs(Math.abs(along-66)+Math.abs(across)-4)<.55)
                        block(x,FLOOR_Y,z,Material.WAXED_OXIDIZED_COPPER);
                }
            }
            if(r>112&&Math.abs(x)>12&&Math.abs(z)>12&&noise(x,2,z)<.045)
                block(x,97,z,Material.FLOWERING_AZALEA_LEAVES);
        }
        // Three concentric terrace borders, with four 15-block-wide entrances.
        for(int x=-112;x<=112;x++)for(int z=-112;z<=112;z++){
            double r=Math.hypot(x,z);
            if(Math.abs(x)<8||Math.abs(z)<8)continue;
            if(Math.abs(r-88)<.7)block(x,102,z,Material.CHISELED_STONE_BRICKS);
            if(Math.abs(r-111)<.8){block(x,105,z,Material.CHISELED_STONE_BRICKS);block(x,106,z,Material.MOSSY_STONE_BRICKS);}
        }
        // An outer octagonal cloister: monumental fluted columns and broken lintels.
        for(int axis=0;axis<2;axis++)for(int side:new int[]{-98,98}){
            for(int along=-56;along<=56;along+=14){
                if(Math.abs(along)<14)continue;
                int x=axis==0?along:side,z=axis==0?side:along;
                column(x,z,104,side<0&&Math.abs(along)==42?29:36);
            }
            for(int along=-63;along<=63;along++){
                if(Math.abs(along)<12||Math.abs(along-39)<4&&side<0)continue;
                int x=axis==0?along:side,z=axis==0?side:along;
                for(int d=-2;d<=2;d++)block(x+(axis==0?0:d),140,z+(axis==0?d:0),ancient(x,140,z));
                block(x,141,z,Material.WAXED_OXIDIZED_COPPER);
            }
        }
        // Four grand pointed gateways. Clear aperture is 23 wide and 28 high.
        for(int axis=0;axis<2;axis++)for(int side:new int[]{-98,98}){
            for(int a:new int[]{-14,14})column(axis==0?a:side,axis==0?side:a,104,40);
            for(int a=-14;a<=14;a++){
                int y=140+(14-Math.abs(a))/2;
                if(axis==0)box(a,y,side-3,a,y+3,side+3,Material.CHISELED_STONE_BRICKS);
                else box(side-3,y,a,side+3,y+3,a,Material.CHISELED_STONE_BRICKS);
            }
            int x=axis==0?0:side,z=axis==0?side:0;
            box(x-1,149,z-1,x+1,151,z+1,Material.WAXED_OXIDIZED_COPPER);
            block(x,152,z,Material.SEA_LANTERN);
        }
        // Corner watchtowers: stepped crowns, weathered ribs and illuminated windows.
        for(int x:new int[]{-74,74})for(int z:new int[]{-74,74})tower(x,z);
        // Four diagonal ruins frame the arena; gaps expose the twilight sky.
        for(int sx:new int[]{-1,1})for(int sz:new int[]{-1,1}){
            for(int t=0;t<=4;t++)column(sx*(64+t*7),sz*(92-t*7),104,26+t%2*5);
            for(int t=0;t<=28;t++)if(t<12||t>16)
                box(sx>0?64+t:-(64+t)-1,137,sz>0?92-t:-(92-t)-1,
                    sx>0?64+t+1:-(64+t),139,sz>0?92-t+1:-(92-t),ancient(t,137,sz));
        }
        // An ancient broken celestial crown, high above the completely empty arena.
        for(int x=-62;x<=62;x++)for(int z=-62;z<=62;z++){
            double r=Math.hypot(x,z),a=Math.atan2(z,x);
            boolean gap=(a>-.75&&a<-.4)||(a>1.7&&a<1.94);
            if(gap)continue;
            if(Math.abs(r-60)<1.15){
                int y=158+(int)Math.round(x*.13);
                box(x,y,z,x,y+1,z,Material.WAXED_OXIDIZED_COPPER);
                if(Math.floorMod(x+z,19)==0)block(x,y+2,z,Material.SEA_LANTERN);
            }
            if(Math.abs(r-44)<.7&&!(a>2.6&&a<3.0)){
                int y=165-(int)Math.round(z*.1);block(x,y,z,Material.CHISELED_QUARTZ_BLOCK);
            }
        }
        // Large urn gardens and roots stay outside the combat disc.
        for(int x:new int[]{-104,104})for(int z:new int[]{-42,42})garden(x,z);
        for(int z:new int[]{-104,104})for(int x:new int[]{-42,42})garden(x,z);
        for(int cx=-8;cx<=7;cx++)for(int cz=-8;cz<=7;cz++){
            var list=new ArrayList<Box>();
            for(Box b:boxes)if(b.x1<=cx*16+15&&b.x2>=cx*16&&b.z1<=cz*16+15&&b.z2>=cz*16)list.add(b);
            if(!list.isEmpty())chunks.put(key(cx,cz),List.copyOf(list));
        }
    }
    private void column(int x,int z,int base,int height){
        box(x-3,96,z-3,x+3,base-1,z+3,Material.MOSSY_STONE_BRICKS);
        box(x-4,base,z-4,x+4,base+1,z+4,Material.CHISELED_STONE_BRICKS);
        box(x-3,base+2,z-3,x+3,base+3,z+3,Material.POLISHED_TUFF);
        box(x-2,base+4,z-2,x+2,base+height-3,z+2,Material.STONE_BRICKS);
        for(int d:new int[]{-2,2}){
            box(x+d,base+5,z-1,x+d,base+height-4,z+1,Material.CALCITE);
            box(x-1,base+5,z+d,x+1,base+height-4,z+d,Material.CALCITE);
        }
        box(x-3,base+height-2,z-3,x+3,base+height-1,z+3,Material.CHISELED_STONE_BRICKS);
        box(x-4,base+height,z-4,x+4,base+height,z+4,Material.WAXED_OXIDIZED_COPPER);
        block(x,base+height+1,z,Material.SEA_LANTERN);
        for(int i=0;i<10;i++)if(noise(x+i,3,z)>.35)
            block(x-2,base+height-4-i,z-2,Material.MOSSY_STONE_BRICKS);
    }
    private void tower(int x,int z){
        box(x-7,104,z-7,x+7,108,z+7,Material.CHISELED_STONE_BRICKS);
        box(x-6,109,z-6,x+6,153,z+6,Material.STONE_BRICKS);
        box(x-4,110,z-4,x+4,152,z+4,Material.AIR);
        for(int axis=0;axis<2;axis++)for(int side:new int[]{-6,6}){
            if(axis==0)box(x-2,123,z+side,x+2,141,z+side,Material.CYAN_STAINED_GLASS);
            else box(x+side,123,z-2,x+side,141,z+2,Material.CYAN_STAINED_GLASS);
        }
        for(int y=154;y<=168;y++){
            int r=8-(y-154)/2;
            box(x-r,y,z-r,x+r,y,z+r,y%4==0?Material.WAXED_OXIDIZED_COPPER:Material.CHISELED_STONE_BRICKS);
        }
        box(x-1,169,z-1,x+1,177,z+1,Material.AMETHYST_BLOCK);
        box(x,178,z,x,181,z,Material.SEA_LANTERN);
    }
    private void garden(int x,int z){
        box(x-5,97,z-5,x+5,105,z+5,Material.CHISELED_STONE_BRICKS);
        box(x-4,106,z-4,x+4,106,z+4,Material.MOSS_BLOCK);
        for(int dx=-4;dx<=4;dx++)for(int dz=-4;dz<=4;dz++)if(dx*dx+dz*dz<19)
            block(x+dx,107,z+dz,Material.FLOWERING_AZALEA_LEAVES);
        box(x,107,z,x,113,z,Material.CHERRY_LOG);
        for(int dx=-6;dx<=6;dx++)for(int dz=-5;dz<=5;dz++)if(dx*dx/36.0+dz*dz/25.0<1)
            box(x+dx,114,z+dz,x+dx,116,z+dz,Material.CHERRY_LEAVES);
    }
    public List<Box> boxes(){return List.copyOf(boxes);}
    /** Exact blueprint material for diagnostics, previews and safe arrivals. */
    public Material at(int x,int y,int z){
        var list=chunks.getOrDefault(key(Math.floorDiv(x,16),Math.floorDiv(z,16)),List.of());
        for(int i=list.size()-1;i>=0;i--){Box b=list.get(i);
            if(x>=b.x1&&x<=b.x2&&y>=b.y1&&y<=b.y2&&z>=b.z1&&z<=b.z2)return b.material;
        }
        return Material.AIR;
    }
    public void render(ChunkData data,int cx,int cz,int originX,int originZ){
        int ox=cx*16-originX,oz=cz*16-originZ;
        for(Box b:chunks.getOrDefault(key(Math.floorDiv(ox,16),Math.floorDiv(oz,16)),List.of())){
            int x1=Math.max(0,b.x1-ox),x2=Math.min(15,b.x2-ox),z1=Math.max(0,b.z1-oz),z2=Math.min(15,b.z2-oz);
            int y1=Math.max(data.getMinHeight(),b.y1),y2=Math.min(data.getMaxHeight()-1,b.y2);
            if(x1<=x2&&z1<=z2&&y1<=y2){
                if(b.material.name().endsWith("_LEAVES")){
                    var leaves=leafData.computeIfAbsent(b.material,m->{
                        var state=(org.bukkit.block.data.type.Leaves)m.createBlockData();state.setPersistent(true);return state;
                    });
                    data.setRegion(x1,y1,z1,x2+1,y2+1,z2+1,leaves);
                }else data.setRegion(x1,y1,z1,x2+1,y2+1,z2+1,b.material);
            }
        }
    }
}
