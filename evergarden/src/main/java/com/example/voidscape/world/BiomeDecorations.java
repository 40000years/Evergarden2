package com.example.voidscape.world;

import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.generator.ChunkGenerator;

import java.util.EnumMap;
import java.util.Map;

/**
 * Biome scenery built entirely from seed and coordinates. Every primitive clips
 * its bounds before iterating; neighboring chunks reconstruct the same feature.
 */
public final class BiomeDecorations {
    private BiomeDecorations() {}
    private static final Material[] FOREST_LOGS={Material.OAK_LOG,Material.SPRUCE_LOG,
            Material.BIRCH_LOG,Material.DARK_OAK_LOG,Material.CHERRY_LOG,Material.OAK_LOG};
    private static final Material[] FOREST_LEAVES={Material.OAK_LEAVES,Material.SPRUCE_LEAVES,
            Material.BIRCH_LEAVES,Material.DARK_OAK_LEAVES,Material.CHERRY_LEAVES,Material.AZALEA_LEAVES};
    private static final int[][] TOWER_STEPS={{-4,-3},{-3,-4},{-2,-4},{-1,-4},{0,-4},{1,-4},{2,-4},{3,-4},
            {4,-3},{4,-2},{4,-1},{4,0},{4,1},{4,2},{4,3},{3,4},{2,4},{1,4},{0,4},{-1,4},{-2,4},{-3,4},
            {-4,3},{-4,2},{-4,1},{-4,0},{-4,-1},{-4,-2}};

    /** Initialized on first render with a running server, rather than class loading. */
    private static final class Foliage {
        private static final Map<Material,BlockData> DATA=create();
        private static Map<Material,BlockData> create(){
            Map<Material,BlockData> result=new EnumMap<>(Material.class);
            for(Material material:new Material[]{Material.OAK_LEAVES,Material.SPRUCE_LEAVES,
                    Material.BIRCH_LEAVES,Material.DARK_OAK_LEAVES,Material.CHERRY_LEAVES,
                    Material.AZALEA_LEAVES,Material.FLOWERING_AZALEA_LEAVES,Material.PALE_OAK_LEAVES}){
                Leaves leaves=(Leaves)material.createBlockData();
                leaves.setPersistent(true);result.put(material,leaves);
            }
            return Map.copyOf(result);
        }
    }

    public static void render(BiomeTerrain terrain,VoidGenerator generator,
                              ChunkGenerator.ChunkData data,int cx,int cz){
        Canvas c=new Canvas(data,cx,cz);
        BiomeScenery.renderGround(terrain,generator,c);
        BiomeScenery.renderFeatures(terrain,generator,c);
        // Small vegetation comes first, so monument and giant-tree silhouettes win.
        for(int gx=Math.floorDiv(c.x0-8,18);gx<=Math.floorDiv(c.x1+8,18);gx++)
            for(int gz=Math.floorDiv(c.z0-8,18);gz<=Math.floorDiv(c.z1+8,18);gz++){
                long h=terrain.hash(gx*18+113,gz*18-71);
                int x=gx*18+4+pick(h,0,10),z=gz*18+4+pick(h,19,10);
                if(!c.intersects(x,z,8))continue;
                var kind=terrain.kind(x,z);
                boolean rich=terrain.richScenery(x,z,8);
                if(rich&&!BiomeScenery.treePatch(terrain,kind,x,z,h))continue;
                if(kind==BiomeTerrain.Kind.LUNAR_CITY&&lunarDistrictReserved(terrain,x,z))continue;
                if(kind==BiomeTerrain.Kind.GARDENS&&pick(h,9,10)>7)continue;
                if(kind==BiomeTerrain.Kind.LUNAR_CITY&&pick(h,9,10)>4)continue;
                var s=generator.surface(x,z);
                // Glacier keeps its bare snowfields above the treeline; spruce groves fill the valleys.
                if(kind==BiomeTerrain.Kind.GLACIER&&(!rich||s.top()>GLACIER_TREELINE))continue;
                if(!s.land()||s.path()||s.pond()||s.depth()<16||!generator.canDecorate(x,z,8))continue;
                if(!grounded(generator,x,z,5,s.top(),7))continue;
                int y=s.top()+1;
                if(kind==BiomeTerrain.Kind.GLACIER){BiomeScenery.tree(c,x,y,z,kind,h,1);continue;}
                if(rich){
                    int feature=pick(h,10,20);
                    if(kind==BiomeTerrain.Kind.ANCIENT_FOREST&&feature==0)mushroom(c,x,y,z,h);
                    else if(kind==BiomeTerrain.Kind.ANCIENT_FOREST&&feature==1)overgrownStone(c,x,y,z,h);
                    else if(kind==BiomeTerrain.Kind.LUNAR_CITY&&pick(h,13,3)!=0)crystalGarden(c,x,y,z,h,true);
                    else if(kind==BiomeTerrain.Kind.GARDENS&&pick(h,13,8)==0)crystalGarden(c,x,y,z,h,false);
                    else BiomeScenery.tree(c,x,y,z,kind,h,pick(h,32,FOREST_LOGS.length));
                    continue;
                }
                if(kind==BiomeTerrain.Kind.ANCIENT_FOREST){
                    int feature=pick(h,10,20);
                    if(feature==0)mushroom(c,x,y,z,h);
                    else if(feature==1)overgrownStone(c,x,y,z,h);
                    else ordinaryTree(c,x,y,z,pick(h,32,FOREST_LOGS.length),h,false);
                }else if(kind==BiomeTerrain.Kind.LUNAR_CITY){
                    if(pick(h,13,3)==0)ordinaryTree(c,x,y,z,0,h,true);
                    else crystalGarden(c,x,y,z,h,true);
                }else if(pick(h,13,8)==0)crystalGarden(c,x,y,z,h,false);
                else gardenTree(c,x,y,z,h);
            }
        for(int gx=Math.floorDiv(c.x0-32,112);gx<=Math.floorDiv(c.x1+32,112);gx++)
            for(int gz=Math.floorDiv(c.z0-32,112);gz<=Math.floorDiv(c.z1+32,112);gz++){
                long h=terrain.hash(gx*112-809,gz*112+277);
                int x=gx*112+30+pick(h,0,52),z=gz*112+30+pick(h,23,52);
                if(!c.intersects(x,z,32)||pick(h,12,5)==0
                        ||terrain.kind(x,z)!=BiomeTerrain.Kind.ANCIENT_FOREST)continue;
                var s=generator.surface(x,z);
                if(s.land()&&generator.canDecorate(x,z,32)&&grounded(generator,x,z,20,s.top(),15)){
                    if(terrain.richScenery(x,z,32))BiomeScenery.ancientTree(c,generator,x,s.top()+1,z,h);
                    else ancientTree(c,generator,x,s.top()+1,z,h);
                }
            }
        for(int gx=Math.floorDiv(c.x0-19,64);gx<=Math.floorDiv(c.x1+19,64);gx++)
            for(int gz=Math.floorDiv(c.z0-19,64);gz<=Math.floorDiv(c.z1+19,64);gz++){
                long h=terrain.hash(gx*64+499,gz*64-397);
                int x=gx*64+17+pick(h,0,30),z=gz*64+17+pick(h,21,30);
                if(!c.intersects(x,z,19)||pick(h,10,5)==0
                        ||terrain.kind(x,z)!=BiomeTerrain.Kind.GLACIER)continue;
                var s=generator.surface(x,z);
                if(s.land()&&generator.canDecorate(x,z,19)){
                    if(terrain.richScenery(x,z,19)&&pick(h,5,3)==0)BiomeScenery.iceShelf(c,generator,x,z,h);
                    else iceSpireCluster(c,generator,x,s.top()+1,z,h);
                }
            }
        for(int gx=Math.floorDiv(c.x0-52,224);gx<=Math.floorDiv(c.x1+52,224);gx++)
            for(int gz=Math.floorDiv(c.z0-52,224);gz<=Math.floorDiv(c.z1+52,224);gz++){
                long h=terrain.hash(gx*224+1013,gz*224+619);
                int x=gx*224+75+pick(h,0,74),z=gz*224+75+pick(h,23,74);
                if(!c.intersects(x,z,52)||pick(h,15,4)!=0
                        ||terrain.kind(x,z)!=BiomeTerrain.Kind.GLACIER)continue;
                if(generator.canDecorate(x,z,52))iceArch(c,generator,x,z,h);
            }
        for(int gx=Math.floorDiv(c.x0-60,128);gx<=Math.floorDiv(c.x1+60,128);gx++)
            for(int gz=Math.floorDiv(c.z0-60,128);gz<=Math.floorDiv(c.z1+60,128);gz++){
                int x=gx*128+64,z=gz*128+64;long h=terrain.hash(x+1637,z-929);
                if(!c.intersects(x,z,60)||!districtExists(h)
                        ||terrain.kind(x,z)!=BiomeTerrain.Kind.LUNAR_CITY)continue;
                var s=generator.surface(x,z);
                if(s.land()&&generator.canDecorate(x,z,60)
                        &&grounded(generator,x,z,49,s.top(),12))lunarDistrict(c,generator,x,s.top()+1,z,h);
            }
    }

    private static final int GLACIER_TREELINE=118;
    private static boolean districtExists(long h){return pick(h,11,5)!=0;}
    static boolean lunarDistrictReserved(BiomeTerrain terrain,int x,int z){
        int ax=Math.floorDiv(x,128)*128+64,az=Math.floorDiv(z,128)*128+64;
        return Math.abs(x-ax)<60&&Math.abs(z-az)<60&&districtExists(terrain.hash(ax+1637,az-929));
    }
    private static int pick(long h,int shift,int bound){return (int)Long.remainderUnsigned(h>>>shift,bound);}
    private static boolean grounded(VoidGenerator g,int x,int z,int radius,int top,int slope){
        for(int dx:new int[]{-radius,0,radius})for(int dz:new int[]{-radius,0,radius}){
            var s=g.surface(x+dx,z+dz);
            if(!s.land()||s.path()||s.pond()||Math.abs(s.top()-top)>slope)return false;
        }
        return true;
    }

    private static void ordinaryTree(Canvas c,int x,int y,int z,int species,long h,boolean pale){
        Material log=pale?Material.PALE_OAK_LOG:FOREST_LOGS[species];
        Material leaves=pale?Material.PALE_OAK_LEAVES:FOREST_LEAVES[species];
        int height=(species==1&&!pale?13:8)+pick(h,42,6);
        if(y+height+5>300)return;
        if(species==1&&!pale){
            for(int dy=4;dy<=height+2;dy+=2){
                double r=Math.max(1.3,5.4*(height+3-dy)/(height-1.0));
                c.ellipsoid(x,y+dy,z,r,2,r,leaves,true);
            }
        }else{
            c.ellipsoid(x,y+height-1,z,5.8,3.8,5.4,leaves,true);
            c.ellipsoid(x-3,y+height-3,z+2,3.5,2.7,3.5,leaves,true);
            c.ellipsoid(x+3,y+height-2,z-2,3.5,2.9,3.5,leaves,true);
            c.line(x,y+height-5,z,x-3,y+height-2,z+2,1,log);
            c.line(x,y+height-5,z,x+3,y+height-2,z-2,1,log);
        }
        int trunk=species==3&&!pale?1:0;
        c.box(x-trunk,y-2,z-trunk,x+trunk,y+height-1,z+trunk,log);
        if(pale&&pick(h,7,3)==0){
            c.put(x+3,y+height-6,z,Material.IRON_CHAIN);
            c.put(x+3,y+height-7,z,Material.SEA_LANTERN);
        }
    }

    private static void gardenTree(Canvas c,int x,int y,int z,long h){
        Material leaves=pick(h,32,3)==0?Material.FLOWERING_AZALEA_LEAVES:Material.CHERRY_LEAVES;
        int height=7+pick(h,42,5);
        c.ellipsoid(x,y+height,z,6.5,3,5.5,leaves,true);
        c.ellipsoid(x-3,y+height-2,z+2,4,2.7,4,leaves,true);
        c.ellipsoid(x+3,y+height-1,z-2,4,2.7,4,leaves,true);
        c.box(x,y-1,z,x,y+height-1,z,Material.CHERRY_LOG);
        c.line(x,y+height-4,z,x-3,y+height-1,z+2,.7,Material.CHERRY_LOG);
        c.line(x,y+height-4,z,x+3,y+height,z-2,.7,Material.CHERRY_LOG);
        if(pick(h,5,3)==0){
            c.put(x+3,y+height-4,z,Material.IRON_CHAIN);
            c.put(x+3,y+height-5,z,Material.PEARLESCENT_FROGLIGHT);
        }
    }

    private static void ancientTree(Canvas c,VoidGenerator g,int x,int y,int z,long h){
        int height=43+pick(h,37,15);
        if(y+height+9>300)return;
        Material leaves=pick(h,17,4)==0?Material.SPRUCE_LEAVES:Material.DARK_OAK_LEAVES;
        // Uneven shoulders break up the silhouette of the massive canopy.
        c.ellipsoid(x,y+height,z,16,8,16,leaves,true);
        for(int arm=0;arm<7;arm++){
            double angle=arm*Math.PI*2/7+pick(h,3,100)/100.0;
            int bx=x+(int)Math.round(Math.cos(angle)*(16+pick(h,arm*5,5)));
            int bz=z+(int)Math.round(Math.sin(angle)*(16+pick(h,arm*5+2,5)));
            int by=y+height-8+pick(h,arm*6,9);
            c.ellipsoid(bx,by,bz,10.5,6,10.5,leaves,true);
            c.line(x,y+height-20,z,bx,by-2,bz,2.4,Material.DARK_OAK_LOG);
            c.line(x,y+height-29,z,(x+bx)/2,y+height-15,(z+bz)/2,2.7,Material.DARK_OAK_LOG);
        }
        for(int wx=Math.max(c.x0,x-6);wx<=Math.min(c.x1,x+6);wx++)
            for(int wz=Math.max(c.z0,z-6);wz<=Math.min(c.z1,z+6);wz++)
                for(int dy=-5;dy<height;dy++){
                    double radius=dy<7?5.8-dy*.16:dy<height-22?4.5:3.4;
                    double wobble=.25*Math.sin(dy*.23);
                    if(Math.pow(wx-x-wobble,2)+Math.pow(wz-z,2)<=radius*radius)
                        c.put(wx,y+dy,wz,Material.DARK_OAK_LOG);
                }
        // Buttress roots descend to the actual hills instead of floating above them.
        for(int root=0;root<8;root++){
            double angle=root*Math.PI/4+.22;
            int length=15+pick(h,root*6,6);
            for(int step=3;step<=length;step++){
                int rx=x+(int)Math.round(Math.cos(angle)*step),rz=z+(int)Math.round(Math.sin(angle)*step);
                if(!c.intersects(rx,rz,3))continue;
                var s=g.surface(rx,rz);if(!s.land())continue;
                int ry=s.top()+1+(int)Math.round((length-step)*.3);
                double radius=step<8?2.6:1.6;
                c.ellipsoid(rx,ry,rz,radius,2.4,radius,Material.DARK_OAK_LOG,false);
                c.ellipsoid(rx,s.top(),rz,radius,2,radius,Material.ROOTED_DIRT,false);
                if(step>length-3)c.put(rx,ry+2,rz,Material.MOSS_BLOCK);
            }
        }
        // A few warm bracket fungi provide landmarks beneath the dark canopy.
        for(int dy=6;dy<height-20;dy+=9){
            int side=pick(h,dy,4);int fx=x+(side<2?(side==0?5:-5):0),fz=z+(side>=2?(side==2?5:-5):0);
            c.ellipsoid(fx,y+dy,fz,3.1,.8,3.1,Material.BROWN_MUSHROOM_BLOCK,false);
            c.put(fx,y+dy-1,fz,Material.SHROOMLIGHT);
        }
    }

    private static void mushroom(Canvas c,int x,int y,int z,long h){
        int height=7+pick(h,37,5);
        c.ellipsoid(x,y+height,z,5.5,2.2,5.5,
                pick(h,14,2)==0?Material.RED_MUSHROOM_BLOCK:Material.BROWN_MUSHROOM_BLOCK,false);
        c.box(x,y-1,z,x+1,y+height-1,z+1,Material.MUSHROOM_STEM);
        c.put(x-2,y+height-1,z,Material.SHROOMLIGHT);
        c.put(x+2,y+height-1,z,Material.SHROOMLIGHT);
    }

    private static void overgrownStone(Canvas c,int x,int y,int z,long h){
        c.ellipsoid(x,y,z,4.8,3.4,4.5,Material.MOSSY_COBBLESTONE,false);
        c.ellipsoid(x-2,y+2,z+1,3,2.5,3,Material.MOSS_BLOCK,false);
        if(pick(h,17,2)==0){
            c.box(x,y+2,z,x,y+7,z,Material.MOSSY_STONE_BRICKS);
            c.put(x,y+8,z,Material.CHISELED_STONE_BRICKS);
            c.put(x,y+5,z-1,Material.GLOW_LICHEN);
        }
    }

    private static void crystalGarden(Canvas c,int x,int y,int z,long h,boolean lunar){
        c.ellipsoid(x,y-1,z,4.3,1.6,4.3,lunar?Material.CALCITE:Material.MOSS_BLOCK,false);
        for(int i=0;i<5;i++){
            int dx=i==0?0:(i%2==0?2:-2),dz=i==0?0:(i<3?2:-2);
            int height=i==0?7+pick(h,35,5):2+pick(h,i*9,5);
            for(int dy=0;dy<height;dy++)c.put(x+dx,y+dy,z+dz,
                    dy==0?Material.SEA_LANTERN:dy==height-1?Material.AMETHYST_BLOCK:
                            lunar?Material.LIGHT_BLUE_STAINED_GLASS:Material.PURPLE_STAINED_GLASS);
            if(i==0)for(int side:new int[]{-1,1})
                c.box(x+side,y,z,x+side,y+height-3,z,lunar?Material.CALCITE:Material.AMETHYST_BLOCK);
        }
    }

    private static void iceSpireCluster(Canvas c,VoidGenerator g,int x,int y,int z,long h){
        for(int i=0;i<5;i++){
            double angle=i*Math.PI*.5+pick(h,13,100)/100.0;
            int sx=i==0?x:x+(int)Math.round(Math.cos(angle)*(8+pick(h,i*7,5)));
            int sz=i==0?z:z+(int)Math.round(Math.sin(angle)*(8+pick(h,i*7+3,5)));
            if(!c.intersects(sx,sz,6))continue;
            var s=g.surface(sx,sz);if(!s.land())continue;
            int sy=s.top()-3,height=i==0?30+pick(h,38,27):12+pick(h,i*8,16);
            height=Math.min(height,300-sy);
            for(int wx=Math.max(c.x0,sx-6);wx<=Math.min(c.x1,sx+6);wx++)
                for(int wz=Math.max(c.z0,sz-6);wz<=Math.min(c.z1,sz+6);wz++){
                    double radius=Math.hypot(wx-sx,wz-sz);
                    if(radius>5.8)continue;
                    int column=(int)Math.round(height*Math.pow(1-radius/6,.62));
                    for(int dy=0;dy<=column;dy++)c.put(wx,sy+dy,wz,
                            Math.floorMod(wx+wz+dy,7)<3?Material.BLUE_ICE:Material.PACKED_ICE);
                    if(column>0)c.put(wx,sy+column,wz,Material.SNOW_BLOCK);
                }
        }
    }

    private static void iceArch(Canvas c,VoidGenerator g,int x,int z,long h){
        boolean alongX=pick(h,7,2)==0;
        int half=40+pick(h,29,7),height=29+pick(h,39,10);
        int ax=alongX?x-half:x,az=alongX?z:z-half,bx=alongX?x+half:x,bz=alongX?z:z+half;
        var a=g.surface(ax,az);var b=g.surface(bx,bz);var middle=g.surface(x,z);
        if(!a.land()||!b.land()||!middle.land())return;
        int y=Math.max(middle.top(),Math.max(a.top(),b.top()))+6;
        if(y+height+5>300)return;
        c.box(ax-3,a.top()-4,az-3,ax+3,y+2,az+3,Material.PACKED_ICE);
        c.box(bx-3,b.top()-4,bz-3,bx+3,y+2,bz+3,Material.PACKED_ICE);
        for(int i=0;i<=64;i++){
            double angle=i*Math.PI/64;
            int span=(int)Math.round(-half*Math.cos(angle));
            int sy=y+(int)Math.round(Math.sin(angle)*height);
            c.ellipsoid(alongX?x+span:x,sy,alongX?z:z+span,
                    4,4,4,i%7<3?Material.BLUE_ICE:Material.PACKED_ICE,false);
        }
        c.ellipsoid(ax,a.top()+1,az,6,4,6,Material.BLUE_ICE,false);
        c.ellipsoid(bx,b.top()+1,bz,6,4,6,Material.BLUE_ICE,false);
        // Hanging teeth make this look like a frozen natural bridge.
        for(int i=-3;i<=3;i++){
            int span=i*10,sy=y+(int)Math.round(Math.sqrt(1-Math.pow(span/(double)half,2))*height)-3;
            int length=4+pick(h,i*6+20,7);
            c.box(alongX?x+span:x,sy-length,alongX?z:z+span,
                    alongX?x+span:x,sy,alongX?z:z+span,Material.BLUE_ICE);
        }
    }

    private static void lunarDistrict(Canvas c,VoidGenerator g,int x,int y,int z,long h){
        // Four district types share a broad plaza but vary their ruined skyline.
        int variant=pick(h,35,4);
        foundation(c,g,x-4,z-53,x+4,z+53,y,Material.SMOOTH_STONE);
        foundation(c,g,x-53,z-4,x+53,z+4,y,Material.SMOOTH_STONE);
        for(int side=0;side<4;side++)districtApproach(c,g,x,y,z,side);
        plaza(c,g,x,y,z,h);
        boolean rich=g.terrain().richScenery(x,z,60);
        int shift=rich?pick(h,3,9)-4:0;
        ruinedHall(c,g,x-36,y,z-33+shift,rich?12+pick(h,7,9):16,12,12+pick(h,22,9),h);
        ruinedHall(c,g,x+34,y,z+35-shift,14,rich?11+pick(h,9,9):15,11+pick(h,29,7),h^0x61b7L);
        lunarTower(c,g,x+37,y,z-34,23+pick(h,42,rich?23:15),h^0x1695L);
        if(variant!=1)lunarTower(c,g,x-36,y,z+34,19+pick(h,47,14),h^0x8479L);
        else ruinedHall(c,g,x-36,y,z+34,15,14,9,h^0x5e1L);
        if(variant==3){
            c.line(x-30,y+26,z-32,x+29,y+33,z-34,1.6,Material.CALCITE);
            c.line(x-30,y+27,z-32,x+9,y+31,z-33,1,Material.SMOOTH_QUARTZ);
        }
        if(rich)BiomeScenery.districtDetails(c,x,y,z,h,variant);
        // Fallen masonry, dry crystal gardens and pale saplings soften the city.
        for(int i=0;i<8;i++){
            double angle=(i+.5)*Math.PI/4;
            int rx=x+(int)Math.round(Math.cos(angle)*31),rz=z+(int)Math.round(Math.sin(angle)*31);
            var s=g.surface(rx,rz);
            if(!s.land())continue;
            if(i%3==0)crystalGarden(c,rx,s.top()+1,rz,h^(i*781L),true);
            else if(i%3==1)ordinaryTree(c,rx,s.top()+1,rz,0,h^(i*1171L),true);
            else c.ellipsoid(rx,s.top()+1,rz,3,2,3,Material.MOSSY_STONE_BRICKS,false);
        }
    }

    private static void districtApproach(Canvas c,VoidGenerator g,int x,int floor,int z,int side){
        int dx=side==0?1:side==1?-1:0,dz=side==2?1:side==3?-1:0;
        var edge=g.surface(x+dx*59,z+dz*59);
        if(!edge.land())return;
        int difference=edge.top()-floor;
        // There are twenty treads. The biome's gentle district hills fit this
        // range; a longer approach also accommodates unusual boundary slopes.
        int start=Math.abs(difference)<=19?40:Math.max(25,59-Math.abs(difference));
        if(Math.abs(difference)>59-start)return;
        for(int distance=start;distance<=59;distance++){
            int tread=floor+(int)Math.round(difference*(distance-start)/(double)(59-start));
            for(int across=-4;across<=4;across++){
                int px=x+dx*distance+dz*across,pz=z+dz*distance+dx*across;
                if(!c.intersects(px,pz,0))continue;
                var s=g.surface(px,pz);if(!s.land())continue;
                c.column(px,pz,Math.min(tread-8,s.top()-3),tread-1,Material.CALCITE);
                c.column(px,pz,tread+1,Math.max(floor+3,s.top()+3),Material.AIR);
                c.put(px,tread,pz,Math.abs(across)==4&&distance%10==7?Material.SEA_LANTERN:Material.SMOOTH_STONE);
            }
        }
    }

    private static void plaza(Canvas c,VoidGenerator g,int x,int y,int z,long h){
        plazaFloor(c,g,x,y,z);
        for(int i=0;i<12;i++){
            double angle=i*Math.PI/6;
            int px=x+(int)Math.round(Math.cos(angle)*20),pz=z+(int)Math.round(Math.sin(angle)*20);
            int height=i%4==pick(h,12,4)?5:10+pick(h,i*4,4);
            c.box(px-1,y+1,pz-1,px+1,y+2,pz+1,Material.CHISELED_QUARTZ_BLOCK);
            c.box(px,y+3,pz,px,y+height,pz,Material.QUARTZ_PILLAR);
            c.box(px-1,y+height+1,pz-1,px+1,y+height+1,pz+1,Material.SMOOTH_QUARTZ);
            if(i%3==0)c.put(px,y+height+2,pz,Material.SEA_LANTERN);
            if(i%4==1){
                double next=(i+1)*Math.PI/6;
                int nx=x+(int)Math.round(Math.cos(next)*20),nz=z+(int)Math.round(Math.sin(next)*20);
                c.line(px,y+12,pz,nx,y+12,nz,.9,Material.SMOOTH_QUARTZ);
            }
        }
        // A ruined vertical moon, with a visible crescent cutout.
        for(int wx=Math.max(c.x0,x-9);wx<=Math.min(c.x1,x+9);wx++)
            for(int wz=Math.max(c.z0,z-2);wz<=Math.min(c.z1,z+2);wz++)
                for(int dy=6;dy<=25;dy++){
                    double outer=Math.hypot(wx-x,dy-15.5),inner=Math.hypot(wx-x-4,dy-17);
                    if(outer<=9.3&&inner>7.7)c.put(wx,y+dy,wz,
                            (dy+wx)%11==0?Material.SEA_LANTERN:Material.CALCITE);
                }
        c.box(x-4,y+1,z-4,x+4,y+3,z+4,Material.POLISHED_DEEPSLATE);
        c.box(x-2,y+4,z-2,x+2,y+5,z+2,Material.CHISELED_QUARTZ_BLOCK);
        c.box(x-1,y+6,z-1,x+1,y+7,z+1,Material.CALCITE);
    }

    private static void plazaFloor(Canvas c,VoidGenerator g,int x,int y,int z){
        for(int wx=Math.max(c.x0,x-24);wx<=Math.min(c.x1,x+24);wx++)
            for(int wz=Math.max(c.z0,z-24);wz<=Math.min(c.z1,z+24);wz++){
                double radius=Math.hypot(wx-x,wz-z);if(radius>24)continue;
                var s=g.surface(wx,wz);
                c.column(wx,wz,Math.min(y-8,s.top()-3),y-1,Material.CALCITE);
                c.column(wx,wz,y+1,Math.max(y+1,s.top()+2),Material.AIR);
                int ring=(int)Math.round(radius);
                Material tile=ring==23||ring==17||ring==10?Material.POLISHED_DEEPSLATE:
                        (Math.abs(wx-x)<=1||Math.abs(wz-z)<=1)?Material.SMOOTH_QUARTZ:Material.CALCITE;
                if(ring==17&&Math.floorMod(wx+wz,5)==0)tile=Material.SEA_LANTERN;
                // Four contained, one-block-deep reflecting pools leave the cardinal
                // paths dry and mirror the crescent without spilling down the skyland.
                if(radius>8&&radius<14&&Math.abs(wx-x)>4&&Math.abs(wz-z)>4){
                    c.put(wx,y-1,wz,Math.floorMod(wx+wz,9)==0?Material.SEA_LANTERN:Material.CALCITE);
                    c.put(wx,y,wz,Material.WATER);
                }else c.put(wx,y,wz,tile);
            }
    }

    private static void foundation(Canvas c,VoidGenerator g,int x0,int z0,int x1,int z1,int floor,Material tile){
        if(!c.intersectsBox(x0,z0,x1,z1))return;
        for(int x=Math.max(c.x0,x0);x<=Math.min(c.x1,x1);x++)
            for(int z=Math.max(c.z0,z0);z<=Math.min(c.z1,z1);z++){
                var s=g.surface(x,z);if(!s.land())continue;
                c.column(x,z,Math.min(floor-8,s.top()-3),floor-1,Material.CALCITE);
                c.column(x,z,floor+1,Math.max(floor+1,s.top()+2),Material.AIR);
                c.put(x,floor,z,tile);
                if((x==x0||x==x1||z==z0||z==z1)&&Math.floorMod(x+z,17)==0)c.put(x,floor,z,Material.SEA_LANTERN);
            }
    }

    private static void ruinedHall(Canvas c,VoidGenerator g,int x,int y,int z,int width,int length,int height,long h){
        int rx=width/2,rz=length/2;
        if(!c.intersects(x,z,Math.max(rx,rz)+2))return;
        foundation(c,g,x-rx-1,z-rz-1,x+rx+1,z+rz+1,y,Material.POLISHED_DEEPSLATE);
        c.box(x-rx+1,y+1,z-rz+1,x+rx-1,y+height+1,z+rz-1,Material.AIR);
        for(int wx=Math.max(c.x0,x-rx);wx<=Math.min(c.x1,x+rx);wx++)
            for(int wz=Math.max(c.z0,z-rz);wz<=Math.min(c.z1,z+rz);wz++){
                if(wx!=x-rx&&wx!=x+rx&&wz!=z-rz&&wz!=z+rz)continue;
                int wallHeight=height-pick(h^((long)wx*7919)^((long)wz*104729),16,6);
                for(int dy=1;dy<=wallHeight;dy++){
                    boolean door=Math.abs(wx-x)<=2&&wz==z+rz&&dy<=5;
                    boolean window=dy>=5&&dy<=8&&((wx==x-rx||wx==x+rx)?Math.floorMod(wz-z,5)==0:Math.floorMod(wx-x,5)==0);
                    if(door||window){c.put(wx,y+dy,wz,Material.AIR);continue;}
                    boolean pillar=(wx==x-rx||wx==x+rx)&&(wz==z-rz||wz==z+rz);
                    c.put(wx,y+dy,wz,pillar?Material.QUARTZ_PILLAR:dy==wallHeight?Material.CHISELED_QUARTZ_BLOCK:
                            Math.floorMod(wx+wz+dy,13)==0?Material.MOSSY_STONE_BRICKS:Material.CALCITE);
                }
            }
        // Broken rafters leave interiors open to the sky and easy to explore.
        for(int dx=-rx;dx<=rx;dx+=5){
            c.box(x+dx,y+height,z-rz,x+dx,y+height,z+1,Material.SMOOTH_QUARTZ);
            c.put(x+dx,y+height-1,z,Material.SEA_LANTERN);
        }
        c.box(x-2,y+1,z-rz+2,x+2,y+2,z-rz+3,Material.CHISELED_QUARTZ_BLOCK);
        c.put(x,y+3,z-rz+2,Material.AMETHYST_BLOCK);
    }

    private static void lunarTower(Canvas c,VoidGenerator g,int x,int y,int z,int height,long h){
        if(!c.intersects(x,z,10))return;
        foundation(c,g,x-9,z-9,x+9,z+9,y,Material.POLISHED_DEEPSLATE);
        for(int wx=Math.max(c.x0,x-8);wx<=Math.min(c.x1,x+8);wx++)
            for(int wz=Math.max(c.z0,z-8);wz<=Math.min(c.z1,z+8);wz++){
                double r=Math.hypot(wx-x,wz-z);if(r>8.5)continue;
                c.column(wx,wz,y+1,y+height+3,Material.AIR);
                int rim=height-pick(h^((long)wx*1237)^((long)wz*2239),16,7);
                for(int dy=1;dy<=rim;dy++){
                    boolean door=Math.abs(wx-x)<3&&wz>=z+6&&dy<6;
                    boolean slit=(Math.abs(wx-x)<2||Math.abs(wz-z)<2)&&dy%9>=4&&dy%9<=7;
                    if(r>=6.5&&!door&&!slit)c.put(wx,y+dy,wz,
                            dy%9==0?Material.CHISELED_QUARTZ_BLOCK:Material.CALCITE);
                    else if(r<6.5&&dy%9==0&&!(wx>x-2&&wx<x+2&&wz<z+3)
                            &&!stairOpening(wx-x,wz-z,dy))
                        c.put(wx,y+dy,wz,Material.SMOOTH_QUARTZ);
                }
                if(r>=6.5&&r<=8.5)c.put(wx,y+rim+1,wz,Material.SMOOTH_QUARTZ);
            }
        // A compact spiral of full blocks reaches the broken upper galleries.
        for(int dy=1;dy<height-6;dy++){
            int[] p=TOWER_STEPS[(dy-1)%TOWER_STEPS.length];
            c.box(x+p[0],y+dy,z+p[1],x+p[0]+1,y+dy,z+p[1]+1,Material.SMOOTH_QUARTZ);
            if(dy%9==4)c.put(x,y+dy,z,Material.SEA_LANTERN);
        }
    }

    private static boolean stairOpening(int x,int z,int floor){
        // Leave headroom both before and after each gallery landing.
        for(int offset=-3;offset<=2;offset++){
            int[] p=TOWER_STEPS[Math.floorMod(floor-1+offset,TOWER_STEPS.length)];
            if(x>=p[0]-1&&x<=p[0]+2&&z>=p[1]-1&&z<=p[1]+2)return true;
        }
        return false;
    }

    static final class Canvas {
        final ChunkGenerator.ChunkData data;final int x0,x1,z0,z1;
        Canvas(ChunkGenerator.ChunkData data,int cx,int cz){this.data=data;x0=cx*16;x1=x0+15;z0=cz*16;z1=z0+15;}
        boolean intersects(int x,int z,int radius){return intersectsBox(x-radius,z-radius,x+radius,z+radius);}
        boolean intersectsBox(int ax,int az,int bx,int bz){return bx>=x0&&ax<=x1&&bz>=z0&&az<=z1;}
        void put(int x,int y,int z,Material material){
            if(x>=x0&&x<=x1&&z>=z0&&z<=z1&&y>=data.getMinHeight()&&y<data.getMaxHeight())
                data.setBlock(x-x0,y,z-z0,material);
        }
        void column(int x,int z,int ay,int by,Material material){
            if(x<x0||x>x1||z<z0||z>z1)return;
            for(int y=Math.max(data.getMinHeight(),ay);y<=Math.min(data.getMaxHeight()-1,by);y++)data.setBlock(x-x0,y,z-z0,material);
        }
        void box(int ax,int ay,int az,int bx,int by,int bz,Material material){
            if(!intersectsBox(ax,az,bx,bz))return;
            for(int x=Math.max(ax,x0);x<=Math.min(bx,x1);x++)for(int z=Math.max(az,z0);z<=Math.min(bz,z1);z++)
                column(x,z,ay,by,material);
        }
        void ellipsoid(double x,double y,double z,double rx,double ry,double rz,Material material,boolean leaf){
            int ax=(int)Math.floor(x-rx),bx=(int)Math.ceil(x+rx),az=(int)Math.floor(z-rz),bz=(int)Math.ceil(z+rz);
            if(!intersectsBox(ax,az,bx,bz))return;
            BlockData foliage=leaf?Foliage.DATA.get(material):null;
            for(int wx=Math.max(ax,x0);wx<=Math.min(bx,x1);wx++)for(int wz=Math.max(az,z0);wz<=Math.min(bz,z1);wz++){
                double remaining=1-Math.pow((wx-x)/rx,2)-Math.pow((wz-z)/rz,2);if(remaining<0)continue;
                double dy=Math.sqrt(remaining)*ry;
                for(int wy=Math.max(data.getMinHeight(),(int)Math.ceil(y-dy));wy<=Math.min(data.getMaxHeight()-1,(int)Math.floor(y+dy));wy++){
                    if(leaf){
                        Material old=data.getType(wx-x0,wy,wz-z0);
                        if(old.isAir()||old==Material.SHORT_GRASS||old==Material.FERN||old==Material.TALL_GRASS||Foliage.DATA.containsKey(old))
                            data.setBlock(wx-x0,wy,wz-z0,foliage);
                    }else data.setBlock(wx-x0,wy,wz-z0,material);
                }
            }
        }
        void line(double ax,double ay,double az,double bx,double by,double bz,double radius,Material material){
            if(!intersectsBox((int)Math.floor(Math.min(ax,bx)-radius),(int)Math.floor(Math.min(az,bz)-radius),
                    (int)Math.ceil(Math.max(ax,bx)+radius),(int)Math.ceil(Math.max(az,bz)+radius)))return;
            int steps=(int)Math.ceil(Math.max(Math.max(Math.abs(bx-ax),Math.abs(by-ay)),Math.abs(bz-az))*1.5);
            for(int step=0;step<=steps;step++){
                double t=steps==0?0:step/(double)steps;
                ellipsoid(ax+(bx-ax)*t,ay+(by-ay)*t,az+(bz-az)*t,radius,radius,radius,material,false);
            }
        }
    }
}
