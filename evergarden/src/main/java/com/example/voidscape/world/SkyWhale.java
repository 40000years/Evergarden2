package com.example.voidscape.world;

import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.block.data.type.Lantern;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.generator.ChunkGenerator.ChunkData;
import java.util.*;

/** A sculpted, immutable block landmark. Geometry is built once, then indexed by chunk. */
public final class SkyWhale {
    public static final int MIN_X=-110, MAX_X=110, MIN_Y=48, MAX_Y=184, MIN_Z=-60, MAX_Z=60;
    public record Block(int x,int y,int z,Material material) {}
    public record WalkPoint(int x,int y,int z) {}
    private record P(double x,double y,double z) {
        P add(double dx,double dy,double dz){return new P(x+dx,y+dy,z+dz);}
    }
    private static final Map<Long,Material> VOXELS=new HashMap<>();
    private static final List<WalkPoint> WALK=new ArrayList<>();
    private static final List<Block> BLOCKS;
    private static final Map<Long,List<Block>> CHUNKS;
    private static final Map<Material,BlockData> DATA=new EnumMap<>(Material.class);
    static {
        buildIslands();
        buildSkull();
        buildSkeleton();
        buildFoliage();
        buildWalkway();
        furnishSanctuary();
        List<Block> blocks=new ArrayList<>(VOXELS.size());
        Map<Long,List<Block>> chunks=new HashMap<>();
        VOXELS.forEach((key,material)->{
            int x=(int)((key>>32)&0xffff)-32768,y=(int)((key>>16)&0xffff),z=(int)(key&0xffff)-32768;
            Block b=new Block(x,y,z,material);blocks.add(b);
            int cx=Math.floorDiv(x,16),cz=Math.floorDiv(z,16);
            chunks.computeIfAbsent(chunkKey(cx,cz),k->new ArrayList<>()).add(b);
        });
        blocks.sort(Comparator.comparingInt(Block::x).thenComparingInt(Block::y).thenComparingInt(Block::z));
        BLOCKS=List.copyOf(blocks);
        chunks.replaceAll((k,v)->List.copyOf(v));CHUNKS=Map.copyOf(chunks);
    }
    private SkyWhale() {}
    public static List<Block> blocks(){return BLOCKS;}
    public static List<WalkPoint> walkway(){return List.copyOf(WALK);}
    public static Material blockAt(int x,int y,int z){return VOXELS.get(key(x,y,z));}
    public static boolean containsColumn(int x,int z){return containsColumn(x,z,0);}
    public static boolean containsColumn(int x,int z,int margin){return square(x/(115.0+margin))+square(z/(65.0+margin))<=1;}
    private static long key(int x,int y,int z){return ((long)(x+32768)<<32)|((long)y<<16)|(z+32768L);}
    private static long chunkKey(int x,int z){return ((long)x<<32)|(z&0xffffffffL);}
    private static double square(double v){return v*v;}
    private static double noise(int x,int y,int z){long h= DungeonLayout.mix(x*341873128712L+y*73428767L+z*132897987541L);return (h&0xffff)/65535.0;}
    private static void put(int x,int y,int z,Material m){
        if(x<MIN_X||x>MAX_X||y<MIN_Y||y>MAX_Y||z<MIN_Z||z>MAX_Z)throw new IllegalArgumentException("Whale out of bounds: "+x+","+y+","+z);
        if(m==Material.AIR)VOXELS.remove(key(x,y,z));else VOXELS.put(key(x,y,z),m);
    }
    private static Material bone(int x,int y,int z){double n=noise(x,y,z);return n<.16?Material.CALCITE:n<.24?Material.QUARTZ_BLOCK:Material.BONE_BLOCK;}
    private static void ball(P p,double rx,double ry,double rz,Material material){
        for(int x=(int)Math.floor(p.x-rx);x<=Math.ceil(p.x+rx);x++)
            for(int y=(int)Math.floor(p.y-ry);y<=Math.ceil(p.y+ry);y++)
                for(int z=(int)Math.floor(p.z-rz);z<=Math.ceil(p.z+rz);z++)
                    if(square((x-p.x)/rx)+square((y-p.y)/ry)+square((z-p.z)/rz)<=1)
                        put(x,y,z,material==null?bone(x,y,z):material);
    }
    private static P interpolate(P a,P b,double t){return new P(a.x+(b.x-a.x)*t,a.y+(b.y-a.y)*t,a.z+(b.z-a.z)*t);}
    private static void line(P a,P b,double r1,double r2,Material material){
        int steps=(int)Math.ceil(Math.sqrt(square(a.x-b.x)+square(a.y-b.y)+square(a.z-b.z))*2.3);
        for(int i=0;i<=steps;i++){double t=i/(double)Math.max(1,steps),r=r1+(r2-r1)*t;ball(interpolate(a,b,t),r,r,r,material);}
    }
    private static void curve(List<P> points,double startRadius,double endRadius,Material material){
        for(int segment=0;segment<points.size()-1;segment++){
            P a=points.get(Math.max(0,segment-1)),b=points.get(segment),c=points.get(segment+1),d=points.get(Math.min(points.size()-1,segment+2));
            for(int k=0;k<=28;k++){
                double t=k/28.0,t2=t*t,t3=t2*t;
                P p=new P(.5*((2*b.x)+(-a.x+c.x)*t+(2*a.x-5*b.x+4*c.x-d.x)*t2+(-a.x+3*b.x-3*c.x+d.x)*t3),
                        .5*((2*b.y)+(-a.y+c.y)*t+(2*a.y-5*b.y+4*c.y-d.y)*t2+(-a.y+3*b.y-3*c.y+d.y)*t3),
                        .5*((2*b.z)+(-a.z+c.z)*t+(2*a.z-5*b.z+4*c.z-d.z)*t2+(-a.z+3*b.z-3*c.z+d.z)*t3));
                double fraction=(segment+t)/(points.size()-1),r=startRadius+(endRadius-startRadius)*fraction;
                ball(p,r,r,r,material);
            }
        }
    }
    private static void island(int cx,int top,int cz,int rx,int rz,int depth){
        for(int x=cx-rx-2;x<=cx+rx+2;x++)for(int z=cz-rz-2;z<=cz+rz+2;z++){
            double radial=Math.sqrt(square((x-cx)/(double)rx)+square((z-cz)/(double)rz));
            double edge=1+.07*Math.sin(x*.69+z*.21)+.045*Math.cos(z*.91-x*.31);
            if(radial>edge)continue;
            int surface=top+(int)Math.round(.8*Math.sin(x*.25)*Math.cos(z*.3));
            int bottom=surface-3-(int)(depth*Math.pow(Math.max(0,1-radial/edge),.66))
                    +(int)(4*Math.sin((x-cx)*.43+(z-cz)*.19)*Math.max(0,1-radial));
            for(int y=bottom;y<=surface;y++){
                Material m;
                double n=noise(x,y,z);
                if(y==surface)m=n<.18?Material.MOSS_BLOCK:Material.GRASS_BLOCK;
                else if(y>=surface-3)m=Material.DIRT;
                else if(y<surface-depth*.6)m=n<.3?Material.TUFF:Material.DEEPSLATE;
                else m=n<.22?Material.ANDESITE:n<.3?Material.MOSSY_COBBLESTONE:Material.STONE;
                put(x,y,z,m);
            }
        }
    }
    private static void buildIslands(){
        island(-82,100,1,24,21,43);
        island(49,122,8,21,18,51);
        island(-55,92,30,8,7,19);
        island(73,111,-18,8,9,25);
        // Exposed roots follow the rock taper rather than forming identical cones.
        curve(List.of(new P(-89,99,-13),new P(-96,91,-12),new P(-91,77,-9),new P(-90,69,-6)),1.8,.6,Material.OAK_LOG);
        curve(List.of(new P(51,123,-5),new P(62,111,-7),new P(59,90,-3),new P(58,77,1)),1.8,.6,Material.OAK_LOG);
    }
    private static void buildSkull(){
        // Broad upper rostrum rising into a rounded cranium; the mouth is an actual hollow room.
        for(int x=-78;x<=-28;x++){
            double t=(x+78)/50.0,width=10+11*Math.pow(Math.sin(Math.PI*t),.65)-2*t;
            double ridge=125+9*t+7*Math.exp(-square((t-.82)/.21));
            for(int z=(int)-Math.ceil(width);z<=Math.ceil(width);z++){
                double across=Math.abs(z)/width;if(across>1)continue;
                int roof=(int)Math.round(ridge-4.8*Math.pow(across,1.8));
                for(int y=roof-2;y<=roof;y++)put(x,y,z,bone(x,y,z));
                // Only the rear skull has walls: keep the frontal mouth wide open.
                if(x>-57&&across>.85){
                    int lower=115+(int)Math.round((x+57)*.26);
                    for(int y=lower;y<roof-2;y++){
                        double socket=square((x+40)/7.6)+square((y-129)/5.8);
                        if(socket>1)put(x,y,z,bone(x,y,z));
                    }
                }
            }
        }
        // Curved mandibles separate the open jaw from the upper skull.
        for(int side:new int[]{-1,1}){
            curve(List.of(new P(-79,110,side*2),new P(-70,110,side*13),new P(-53,113,side*22),new P(-36,119,side*20),new P(-29,131,side*12)),2.6,2.1,null);
            // Brow rims leave deep, dark eye openings instead of a colored glass strip.
            for(int i=0;i<64;i++){
                double a=2*Math.PI*i/64.0;
                ball(new P(-40+8.1*Math.cos(a),129+6.4*Math.sin(a),side*17.4),1.2,1.2,1.5,null);
            }
            line(new P(-30,130,side*12),new P(-24,140,side*5),3,2,null);
        }
        // Layered cheek arches keep the two eye sockets legible from the approach.
        for(int side:new int[]{-1,1}){
            for(int x=-44;x<=-36;x++)for(int y=125;y<=132;y++)
                if(square((x+40)/4.5)+square((y-128.5)/4)<1)
                    put(x,y,side*12,Material.POLISHED_BLACKSTONE);
        }
        // A lower jaw shelf under the sanctuary, broad at the hinge and narrow at the lip.
        for(int x=-76;x<=-33;x++){
            int width=(int)(7+10*Math.sin(Math.PI*(x+76)/55.0));
            int floor=110+(int)Math.round((x+76)*.075);
            for(int z=-width;z<=width;z++)if(Math.abs(z)>width-3||x<-70)
                for(int y=floor-1;y<=floor;y++)put(x,y,z,bone(x,y,z));
        }
    }
    private static double bodyZ(double x){return 2.5+3.0*Math.sin((x+30)/72*Math.PI*.8);}
    private static double spineY(double x){return 145+3*Math.sin((x+30)/75*Math.PI);}
    private static void buildSkeleton(){
        curve(List.of(new P(-30,139,0),new P(-16,147,2),new P(8,149,5),new P(32,146,5),new P(47,148,7),new P(62,157,8),new P(72,165,8)),2.5,1.6,null);
        int[] ribs={-24,-17,-9,0,9,18,27,35,42};
        for(int i=0;i<ribs.length;i++){
            double x=ribs[i],z=bodyZ(x),top=spineY(x),radius=21-12*Math.pow((x+24)/66.0,1.55),bottom=106+10*Math.pow((x+24)/66.0,1.7);
            for(int side:new int[]{-1,1}){
                double shortening=(i==3&&side==1)?9:(i==6&&side==-1)?6:0;
                curve(List.of(new P(x,top,z),new P(x-2,top-4,z+side*radius*.63),
                        new P(x-3,130+(i*.25),z+side*radius),new P(x+1,bottom+7+shortening,z+side*radius*.82),
                        new P(x+5,bottom+shortening,z+side*radius*.42)),2.15-i*.08,.8,null);
            }
        }
        // Vertebrae and small spinal processes vary along the curved backbone.
        for(int x=-25;x<=49;x+=5){
            double z=bodyZ(x),y=spineY(x);
            ball(new P(x,y,z),1.8,2.5,3.5,null);
            line(new P(x,y+1,z),new P(x-1,y+5,z),1.3,.8,null);
        }
        // Long articulated pectoral bones slope away from the chest.
        for(int side:new int[]{-1,1}){
            curve(List.of(new P(-20,129,side*17),new P(-12,121,side*27),new P(2,111,side*34),new P(14,108,side*37)),2.1,.9,null);
            for(int finger=0;finger<3;finger++)line(new P(-9+finger*2,119,side*27),new P(8+finger*4,105+finger,side*(35-finger*2)),1.1,.65,null);
        }
        for(int i=0;i<5;i++){
            double t=i/4.0;
            P joint=new P(50+18*t,150+13*t,7+t);
            ball(joint,2.3,2.5,3.2,null);
            line(joint,joint.add(-1,4,0),1.1,.6,null);
        }
        // Raised crescent flukes: fins curve upward and taper to distinct pointed tips.
        for(int side:new int[]{-1,1})for(int i=0;i<=100;i++){
            double t=i/100.0;
            P c=new P(71+20*t-7*t*t,164+16*t*t,8+side*34*t);
            double w=.55+5.2*Math.pow(Math.sin(Math.PI*t),.7);
            ball(c,w,1.0+1.4*(1-t),1.25,null);
            if(i%6==0)ball(c.add(-w*.7,.8,0),.9,1.1,1.2,Material.CALCITE);
        }
    }
    private static void canopy(P center,double rx,double ry,double rz){
        for(int x=(int)(center.x-rx-1);x<=center.x+rx+1;x++)
            for(int y=(int)(center.y-ry-1);y<=center.y+ry+1;y++)
                for(int z=(int)(center.z-rz-1);z<=center.z+rz+1;z++){
                    double edge=square((x-center.x)/rx)+square((y-center.y)/ry)+square((z-center.z)/rz);
                    if(edge<=.88+noise(x,y,z)*.22)put(x,y,z,Material.CHERRY_LEAVES);
                }
    }
    private static void tree(int x,int y,int z,int height){
        curve(List.of(new P(x,y,z),new P(x+1,y+height*.45,z),new P(x-2,y+height,z+1)),1.45,.75,Material.CHERRY_LOG);
        P[] ends={new P(x-7,y+height-2,z-3),new P(x+6,y+height-4,z+4),new P(x-1,y+height+1,z+2),new P(x+3,y+height-1,z-5)};
        for(P end:ends){
            curve(List.of(new P(x,y+height-7,z),interpolate(new P(x,y+height-5,z),end,.65).add(0,-1,0),end),1.05,.55,Material.CHERRY_LOG);
            canopy(end.add(0,1,0),5.8,2.1,4.9);
        }
    }
    private static void vine(int x,int y,int z,int length){
        ball(new P(x,y+1,z),1.5,1.0,1.6,Material.OAK_LEAVES);
        for(int dy=0;dy<length;dy++){
            if(y-dy<MIN_Y)break;
            if(!VOXELS.containsKey(key(x,y-dy,z)))put(x,y-dy,z,dy==length-1?Material.CAVE_VINES:Material.CAVE_VINES_PLANT);
        }
    }
    private static void mossPatch(int cx,int cz,double radius){
        for(int x=(int)(cx-radius);x<=cx+radius;x++)for(int z=(int)(cz-radius);z<=cz+radius;z++){
            if(square((x-cx)/radius)+square((z-cz)/radius)> .8+noise(x,1,z)*.3)continue;
            int y=146;while(y>123&&(blockAt(x,y,z)==null||!Set.of(Material.BONE_BLOCK,Material.CALCITE,Material.QUARTZ_BLOCK).contains(blockAt(x,y,z))))y--;
            if(y<=123)continue;
            put(x,y,z,Material.MOSS_BLOCK);
            if(noise(x,y,z)>.45)put(x,y+1,z,Material.MOSS_CARPET);
        }
    }
    private static void islandVines(int cx,int y,int cz,int rx,int rz,int count){
        for(int i=0;i<count;i++){
            double a=i*Math.PI*2/count;
            int x=cx+(int)Math.round(rx*Math.cos(a)),z=cz+(int)Math.round(rz*Math.sin(a));
            vine(x,y-2,z,11+(i*11)%21);
            if(i%3==0)vine(x+1,y-3,z,8+(i*7)%16);
        }
    }
    private static void buildFoliage(){
        tree(-86,101,13,14);tree(49,124,15,16);tree(62,123,5,10);
        ball(new P(-71,101,-10),5,2,3,Material.OAK_LEAVES);
        ball(new P(43,124,-3),5,2,3,Material.OAK_LEAVES);
        mossPatch(-61,-8,4.8);mossPatch(-46,10,6.0);mossPatch(-34,-9,3.8);
        // Thin fissures and a chipped lip interrupt the formerly smooth, helmet-like roof.
        for(int x=-62;x<=-43;x++){
            int z=2+(int)Math.round(1.5*Math.sin(x*.5));
            int y=145;while(y>125&&blockAt(x,y,z)==null)y--;
            if(y>125){put(x,y,z,Material.AIR);if(x%3!=0)put(x,y-1,z,Material.AIR);}
        }
        ball(new P(-77,125,7),2.8,2.1,2.1,Material.AIR);
        for(int x:new int[]{-65,-60,-51,-39})for(int side:new int[]{-1,1}){
            int z=side*(x<-55?17:20);
            int y=145;while(y>121&&blockAt(x,y,z)==null)y--;
            if(y>121)vine(x,y-1,z+side,11+Math.floorMod(x,11));
        }
        // Find actual rib surfaces before attaching hanging greenery.
        for(int i=0;i<8;i++)for(int side:new int[]{-1,1}){
            int targetX=-24+i*8;double radius=21-12*Math.pow((targetX+24)/66.0,1.55);
            P target=new P(targetX-3,130+i*.25,bodyZ(targetX)+side*radius);
            P best=null;double distance=Double.MAX_VALUE;
            for(int x=(int)target.x-4;x<=target.x+4;x++)for(int y=125;y<=135;y++)for(int z=(int)target.z-3;z<=target.z+3;z++){
                Material material=blockAt(x,y,z);
                if(material!=Material.BONE_BLOCK&&material!=Material.CALCITE&&material!=Material.QUARTZ_BLOCK)continue;
                double d=square(x-target.x)+square(y-target.y)+square(z-target.z);
                if(d<distance){distance=d;best=new P(x,y,z);}
            }
            if(best!=null){
                int x=(int)best.x,y=(int)best.y,z=(int)best.z+side*2;
                vine(x,y,z,12+(i*7)%14);
                if(i%2==0)vine(x+1,y-2,z,9+(i*3)%13);
            }
        }
        islandVines(-82,100,1,23,20,25);islandVines(49,122,8,20,17,23);
        islandVines(-55,92,30,7,6,8);islandVines(73,111,-18,7,8,8);
    }
    private static int deckY(int x){
        if(x<-76)return 100+(int)Math.round((x+95)*12/19.0);
        if(x<-64)return 112+(x+76)/6;
        if(x<-36)return 114;
        if(x<-24)return 114+(int)Math.round((x+36)*7/12.0);
        return 121+(int)Math.round((x+24)*3/81.0);
    }
    private static int deckZ(int x){return x<-32?0:(int)Math.round(-5*Math.sin((x+32)/95.0*Math.PI));}
    private static void buildWalkway(){
        // Sanctuary floor visible through the mouth; the public route passes its center.
        for(int x=-69;x<=-34;x++)for(int z=-10;z<=10;z++){
            int halfWidth=7+(int)Math.round(3*Math.sin((x+69)/35.0*Math.PI));
            if(Math.abs(z)>halfWidth)continue;
            put(x,114,z,Math.abs(z)==halfWidth?Material.STRIPPED_SPRUCE_LOG:Material.SPRUCE_PLANKS);
            if(Math.abs(z)==halfWidth||Math.floorMod(x,8)==0)put(x,113,z,Material.SPRUCE_LOG);
            for(int y=115;y<=119;y++)put(x,y,z,Material.AIR);
        }
        // Transverse beams seat the chamber into the lower jaw rather than suspending it in air.
        for(int x:new int[]{-60,-48,-37})for(int z=-19;z<=19;z++)
            if(blockAt(x,113,z)==null)put(x,113,z,Material.SPRUCE_LOG);
        for(int x=-95;x<=57;x++){
            int y=deckY(x),z=deckZ(x);WALK.add(new WalkPoint(x,y,z));
            boolean step=x>-95&&y>deckY(x-1);
            Material floor=x<-70?(step?Material.SMOOTH_QUARTZ_STAIRS:Material.SMOOTH_QUARTZ):(step?Material.SPRUCE_STAIRS:Material.SPRUCE_PLANKS);
            for(int dz=-2;dz<=2;dz++){
                put(x,y,z+dz,floor);
                for(int h=1;h<=4;h++)put(x,y+h,z+dz,Material.AIR);
            }
            if(x>=-28&&x<=43){
                for(int side:new int[]{-1,1}){
                    put(x,y,z+side*3,Material.SPRUCE_PLANKS);
                    put(x,y+1,z+side*3,Material.SPRUCE_FENCE);
                    if(Math.floorMod(x,7)==0){
                        for(int h=1;h<=3;h++)put(x,y-h,z+side*3,Material.SPRUCE_LOG);
                        if(side==-1){
                            for(int h=2;h<=4;h++)put(x,y+h,z-3,Material.SPRUCE_FENCE);
                            put(x,y+4,z-4,Material.SPRUCE_LOG);
                            put(x,y+3,z-4,Material.IRON_CHAIN);
                            put(x,y+2,z-4,Material.LANTERN);
                        }
                    }
                }
            }
        }
        // Small lookout on the rear island, aligned with the deck exit.
        for(int x=49;x<=60;x++)for(int z=-7;z<=4;z++){
            put(x,124,z,Material.SPRUCE_PLANKS);
            for(int y=125;y<=128;y++)put(x,y,z,Material.AIR);
        }
    }
    private static void hangingLamp(int x,int bottom,int z){
        int support=bottom+1;
        while(support<=MAX_Y&&blockAt(x,support,z)==null)support++;
        if(support>MAX_Y)return;
        for(int y=bottom+1;y<support;y++)put(x,y,z,Material.IRON_CHAIN);
        put(x,bottom,z,Material.LANTERN);
    }
    private static void furnishSanctuary(){
        // Recessed book alcoves and warm light make the skull a place to enter.
        for(int x=-52;x<=-43;x++){
            for(int y=115;y<=117;y++)put(x,y,8,Material.BOOKSHELF);
            put(x,118,8,Material.SPRUCE_PLANKS);
        }
        for(int x:new int[]{-61,-45})for(int side:new int[]{-1,1}){
            for(int dx=-2;dx<=2;dx++)put(x+dx,115,side*6,Material.SPRUCE_SLAB);
            put(x,114,side*5,Material.GOLD_BLOCK);
            put(x,115,side*8,Material.OCHRE_FROGLIGHT);
            put(x,116,side*8,Material.SPRUCE_SLAB);
        }
        for(int x=-58;x<=-48;x++)for(int z:new int[]{-5,-4,4,5})put(x,115,z,Material.CYAN_CARPET);
        for(int x:new int[]{-62,-48,-35})for(int side:new int[]{-1,1})hangingLamp(x,120,side*9);
        for(int x:new int[]{-59,-41})for(int side:new int[]{-1,1}){
            put(x,114,side*6,Material.OCHRE_FROGLIGHT);
            put(x,115,side*6,Material.SPRUCE_SLAB);
        }
        hangingLamp(-74,117,-10);hangingLamp(-74,117,10);
    }
    private static synchronized BlockData data(Material material){
        return DATA.computeIfAbsent(material,m->{
            BlockData data=m.createBlockData();
            if(data instanceof Leaves leaves)leaves.setPersistent(true);
            if(data instanceof Lantern lantern)lantern.setHanging(true);
            if(data instanceof Stairs stairs)stairs.setFacing(BlockFace.EAST);
            if(m==Material.CAVE_VINES&&data instanceof Ageable ageable)ageable.setAge(ageable.getMaximumAge());
            return data;
        });
    }
    public static void render(ChunkData data,int chunkX,int chunkZ,SkyWhaleLayout.Site site){
        int localChunkX=chunkX-Math.floorDiv(site.x(),16);
        int localChunkZ=chunkZ-Math.floorDiv(site.z(),16);
        List<Block> blocks=CHUNKS.get(chunkKey(localChunkX,localChunkZ));if(blocks==null)return;
        int ox=localChunkX*16,oz=localChunkZ*16;
        for(Block b:blocks)if(b.y>=data.getMinHeight()&&b.y<data.getMaxHeight())
            data.setBlock(b.x-ox,b.y,b.z-oz,data(b.material));
    }
}
