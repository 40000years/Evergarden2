package com.example.voidscape.world;

import org.bukkit.Material;
import com.example.voidscape.world.BiomeDecorations.Canvas;

/** New scenery combines broad ecological patches with individual, seed-shaped silhouettes. */
final class BiomeScenery {
    private static final Material[] LOGS={Material.OAK_LOG,Material.SPRUCE_LOG,Material.BIRCH_LOG,
        Material.DARK_OAK_LOG,Material.CHERRY_LOG,Material.OAK_LOG};
    private static final Material[] LEAVES={Material.OAK_LEAVES,Material.SPRUCE_LEAVES,Material.BIRCH_LEAVES,
        Material.DARK_OAK_LEAVES,Material.CHERRY_LEAVES,Material.AZALEA_LEAVES};
    private BiomeScenery(){}
    private static int pick(long h,int shift,int n){return (int)Long.remainderUnsigned(h>>>shift,n);}
    private static long salt(long h,int i){return DungeonLayout.mix(h^((long)i*0x9e3779b97f4a7c15L));}
    public static Material surfaceMaterial(BiomeTerrain t,BiomeTerrain.Kind kind,int x,int z,Material original){
        double broad=t.noise(x+1703,z-391,185),fine=t.noise(x-809,z+619,29);
        return switch(kind){
            case GARDENS->broad>.25&&fine>-.15?Material.MOSS_BLOCK:original;
            case ANCIENT_FOREST->broad<-.29?Material.COARSE_DIRT:broad>.17?Material.MOSS_BLOCK:
                fine>.28?Material.PODZOL:original;
            case GLACIER->broad<-.23&&fine>.12?Material.BLUE_ICE:
                broad<-.12?Material.PACKED_ICE:original;
            case LUNAR_CITY->broad>.31?Material.MOSS_BLOCK:fine<-.40?Material.COARSE_DIRT:original;
        };
    }
    public static boolean treePatch(BiomeTerrain t,BiomeTerrain.Kind kind,int x,int z,long h){
        double patch=t.noise(x+2381,z-1777,170),grove=t.noise(x-177,z+821,71);
        double density=switch(kind){case GARDENS->.53+patch*.46+grove*.16;
            case ANCIENT_FOREST->.72+patch*.37;case LUNAR_CITY->.34+patch*.3;case GLACIER->.30+patch*.45;};
        return (salt(h,31)&65535)/65536.0<Math.clamp(density,.10,.97);
    }
    public static void renderGround(BiomeTerrain t,VoidGenerator g,Canvas c){
        for(int x=c.x0;x<=c.x1;x++)for(int z=c.z0;z<=c.z1;z++){
            if(!t.richScenery(x,z,0))continue;
            var s=g.surface(x,z);if(!s.land()||!g.canDecorate(x,z,2))continue;
            var kind=t.kind(x,z);long h=t.hash(x+59,z-83);int y=s.top()+1;
            var ground=c.data.getType(x-c.x0,s.top(),z-c.z0);
            boolean soil=ground==Material.MOSS_BLOCK||ground==Material.GRASS_BLOCK||ground==Material.PODZOL||ground==Material.COARSE_DIRT;
            if(kind==BiomeTerrain.Kind.GLACIER){
                if(ground==Material.SNOW_BLOCK&&(h&15)<3)c.put(x,y,z,Material.SNOW);
                continue;
            }
            if(!soil)continue;
            Material plant=groundCover(t,kind,x,z,h);
            if(plant!=null)c.put(x,y,z,plant);
        }
    }
    /**
     * Roughly a third of open soil carries something: grass everywhere, meadow patches of flowers that
     * change colour in broad bands, and each biome's own undergrowth. Patches keep bare ground between.
     */
    static Material groundCover(BiomeTerrain t,BiomeTerrain.Kind kind,int x,int z,long h){
        double patch=t.noise(x+619,z-121,47),meadow=t.noise(x-2213,z+911,96);int roll=pick(h,3,100);
        switch(kind){
            case GARDENS->{
                if(meadow>.12&&roll<20){
                    Material[] flowers={Material.PINK_TULIP,Material.WHITE_TULIP,Material.ALLIUM,
                        Material.AZURE_BLUET,Material.OXEYE_DAISY,Material.BLUE_ORCHID,Material.CORNFLOWER,Material.LILY_OF_THE_VALLEY};
                    return flowers[Math.floorMod((int)Math.floor(t.noise(x,z,135)*8)+pick(h,17,2),flowers.length)];
                }
                if(patch>.25&&roll<16)return Material.PINK_PETALS;
                if(roll<(meadow>.12?44:30))return Material.SHORT_GRASS;
                if(roll<33)return Material.WILDFLOWERS;
                if(roll<34)return Material.BUSH;
                return null;
            }
            case ANCIENT_FOREST->{
                if(patch<-.05&&roll<24)return Material.FERN;
                if(patch>.2&&roll<3)return (h&64)==0?Material.BROWN_MUSHROOM:Material.RED_MUSHROOM;
                if(roll<10)return Material.LEAF_LITTER;
                if(roll<14)return Material.MOSS_CARPET;
                if(roll<28)return Material.SHORT_GRASS;
                if(roll<29)return Material.SWEET_BERRY_BUSH;
                if(roll<30)return Material.FIREFLY_BUSH;
                return null;
            }
            case LUNAR_CITY->{
                if(patch>.15&&roll<14)return Material.PALE_MOSS_CARPET;
                if(meadow>.2&&roll<3)return (h&64)==0?Material.CLOSED_EYEBLOSSOM:Material.WHITE_TULIP;
                if(roll<26)return Material.SHORT_GRASS;
                if(roll<28)return Material.LILY_OF_THE_VALLEY;
                return null;
            }
            default->{return null;}
        }
    }
    public static void tree(Canvas c,int x,int y,int z,BiomeTerrain.Kind kind,long h,int species){
        int shape=pick(salt(h,9),0,5);boolean pale=kind==BiomeTerrain.Kind.LUNAR_CITY;
        boolean garden=kind==BiomeTerrain.Kind.GARDENS;
        Material log=pale?Material.PALE_OAK_LOG:garden?Material.CHERRY_LOG:LOGS[species];
        Material leaves=pale?Material.PALE_OAK_LEAVES:garden?
            (pick(h,28,4)==0?Material.FLOWERING_AZALEA_LEAVES:Material.CHERRY_LEAVES):LEAVES[species];
        int height=shape==1?12+pick(h,42,8):shape==4?5+pick(h,42,4):7+pick(h,42,8);
        if(y+height+6>=300)return;
        double wind=pick(h,7,628)/100.0,lean=shape==2?2.0:.5+pick(h,23,10)*.10;
        double bx=x+Math.cos(wind)*lean,bz=z+Math.sin(wind)*lean;
        if(!garden&&!pale&&species==1){
            // Conifers vary in taper, tier spacing and height instead of sharing one cone.
            int tiers=4+pick(h,3,4);
            for(int i=0;i<tiers;i++){
                double fraction=i/(double)Math.max(1,tiers-1);double cy=y+4+(height-2)*fraction;
                double r=5.3*(1-fraction)+1.1;
                c.ellipsoid(bx,cy,bz,r,1.4+pick(salt(h,i),5,3)*.4,r*.83,leaves,true);
            }
            c.line(x,y-1,z,bx,y+height+1,bz,.7,log);return;
        }
        int arms=shape==4?2:3+pick(h,11,3);
        for(int i=0;i<arms;i++){
            long branch=salt(h,i+1);double a=wind+i*Math.PI*2/arms+pick(branch,7,30)*.02;
            double length=shape==1?2.1+pick(branch,21,12)*.1:2.4+pick(branch,21,16)*.1;
            double ex=x+Math.cos(a)*length,ez=z+Math.sin(a)*length;
            double ey=y+height-2+pick(branch,34,5);
            double rx=shape==1?2.6:2.6+pick(branch,42,9)*.1;
            double ry=shape==3?2.5:1.7+pick(branch,50,8)*.14;
            // Multi-stem coppices, slender crowns, umbrellas and wind-swept trees.
            c.ellipsoid(ex,ey,ez,rx,ry,rx*.87,leaves,true);
            c.line(bx,y+height-5,bz,ex,ey-1,ez,.65,log);
            if(shape==3)c.line(x+(i%2==0?1:-1),y,z,ex,y+height-2,ez,.65,log);
        }
        c.ellipsoid(bx,y+height+1,bz,shape==1?3:4,shape==1?4:2.2,3.2,leaves,true);
        c.line(x,y-1,z,bx,y+height,bz,shape==3?1.1:.75,log);
        if(!garden&&!pale&&shape==2){
            c.line(x,y,z,x+3*Math.cos(wind),y-1,z+3*Math.sin(wind),.65,log);
            c.put(x-1,y-1,z,Material.ROOTED_DIRT);
        }
        if(garden&&pick(h,31,7)==0){
            int lx=x+(int)Math.round(Math.cos(wind)*3),lz=z+(int)Math.round(Math.sin(wind)*3);
            c.put(lx,y+height-3,lz,Material.IRON_CHAIN);c.put(lx,y+height-4,lz,Material.PEARLESCENT_FROGLIGHT);
        }
    }
    public static void ancientTree(Canvas c,VoidGenerator g,int x,int y,int z,long h){
        int height=37+pick(h,37,29),arms=5+pick(h,12,4);
        if(y+height+11>300)return;
        double wind=pick(h,3,628)/100.0;
        double leanX=Math.cos(wind)*2,leanZ=Math.sin(wind)*2;
        Material leaf=pick(h,19,5)==0?Material.OAK_LEAVES:pick(h,19,5)==1?Material.SPRUCE_LEAVES:Material.DARK_OAK_LEAVES;
        c.ellipsoid(x+leanX,y+height,z+leanZ,11+pick(h,8,5),6+pick(h,28,4),13,leaf,true);
        for(int arm=0;arm<arms;arm++){
            long branch=salt(h,arm+71);double angle=wind+arm*Math.PI*2/arms;
            double length=13+pick(branch,17,7),bx=x+Math.cos(angle)*length,bz=z+Math.sin(angle)*length;
            int by=y+height-10+pick(branch,34,12);
            double crown=6.5+pick(branch,42,5)*.5;
            c.ellipsoid(bx,by,bz,crown,4+pick(branch,9,4),crown*.88,leaf,true);
            c.line(x,y+height-25,z,bx,by-1,bz,1.8+pick(branch,3,4)*.2,Material.DARK_OAK_LOG);
            c.line(x,y+height-32,z,(x+bx)/2,by-9,(z+bz)/2,2,Material.DARK_OAK_LOG);
        }
        for(int dy=-4;dy<=height;dy++){
            double t=Math.max(0,dy)/(double)height;
            double radius=(5.3-t*2.7)*(1+.08*Math.sin(dy*.35));
            c.ellipsoid(x+leanX*t,y+dy,z+leanZ*t,radius,.8,radius*.86,Material.DARK_OAK_LOG,false);
        }
        int roots=5+pick(h,9,4);
        for(int root=0;root<roots;root++){
            double angle=wind+root*Math.PI*2/roots;int length=11+pick(salt(h,root+50),0,9);
            for(int step=3;step<=length;step++){
                int rx=x+(int)Math.round(Math.cos(angle)*step),rz=z+(int)Math.round(Math.sin(angle)*step);
                if(!c.intersects(rx,rz,3))continue;var s=g.surface(rx,rz);if(!s.land())continue;
                c.ellipsoid(rx,s.top()+1+(length-step)*.28,rz,step<8?2.4:1.4,1.8,1.7,Material.DARK_OAK_LOG,false);
                c.ellipsoid(rx,s.top(),rz,2,1,2,Material.ROOTED_DIRT,false);
            }
        }
        for(int i=0;i<3;i++){
            double a=wind+i*2;int fx=x+(int)Math.round(Math.cos(a)*5),fz=z+(int)Math.round(Math.sin(a)*5);
            c.ellipsoid(fx,y+8+i*9,fz,2.8,.7,2.6,Material.BROWN_MUSHROOM_BLOCK,false);
            c.put(fx,y+7+i*9,fz,Material.SHROOMLIGHT);
        }
    }
    public static void renderFeatures(BiomeTerrain t,VoidGenerator g,Canvas c){
        int cell=54;
        for(int gx=Math.floorDiv(c.x0-19,cell);gx<=Math.floorDiv(c.x1+19,cell);gx++)
            for(int gz=Math.floorDiv(c.z0-19,cell);gz<=Math.floorDiv(c.z1+19,cell);gz++){
                long h=t.hash(gx*cell-731,gz*cell+1543);
                int x=gx*cell+14+pick(h,0,25),z=gz*cell+14+pick(h,23,25);
                if(!c.intersects(x,z,19)||pick(h,13,5)>2||!t.richScenery(x,z,19)||!g.canDecorate(x,z,19))continue;
                var s=g.surface(x,z);if(!s.land())continue;var kind=t.kind(x,z);
                if(kind==BiomeTerrain.Kind.LUNAR_CITY){
                    // Keep plaza pools and accepted districts clear of later debris.
                    boolean district=false;
                    for(int dx:new int[]{-19,0,19})for(int dz:new int[]{-19,0,19})
                        district|=BiomeDecorations.lunarDistrictReserved(t,x+dx,z+dz);
                    if(district)continue;
                }
                boolean stable=true;
                for(int dx:new int[]{-10,0,10})for(int dz:new int[]{-10,0,10}){
                    var edge=g.surface(x+dx,z+dz);if(!edge.land()||Math.abs(edge.top()-s.top())>7)stable=false;
                }
                if(!stable)continue;int y=s.top()+1;
                switch(kind){
                    case GARDENS->{
                        if(pick(h,6,4)==0)spring(c,x,y,z,h);
                        else if(pick(h,6,4)==1)standingStones(c,x,y,z,h,false);
                        else rockGarden(c,x,y,z,h);
                    }
                    case ANCIENT_FOREST->{
                        if(pick(h,6,4)<2){
                            fallenTree(c,g,x,z,h);
                            if(t.extraDetails(x,z,19)){
                                for(int side:new int[]{-1,1}){
                                    var edge=g.surface(x+side*4,z+3);
                                    c.ellipsoid(x+side*4,edge.top()+2,z+3,1.6,.7,1.4,Material.BROWN_MUSHROOM_BLOCK,false);
                                    c.put(x+side*4,edge.top()+1,z+3,Material.MUSHROOM_STEM);
                                }
                            }
                        }
                        else if(pick(h,6,4)==2)spring(c,x,y,z,h);
                        else standingStones(c,x,y,z,h,true);
                    }
                    case GLACIER->{
                        if(pick(h,6,3)==0)iceShelf(c,g,x,z,h);
                        else{
                            snowDrift(c,g,x,z,h);
                            if(t.extraDetails(x,z,19)&&pick(h,17,4)==0){
                                var edge=g.surface(x-8,z+3);
                                c.ellipsoid(x-8,edge.top()+1,z+3,2.6,2.4,2.1,Material.DIORITE,false);
                                c.ellipsoid(x-8,edge.top()+3,z+3,2,1,1.8,Material.SNOW_BLOCK,false);
                            }
                        }
                    }
                    case LUNAR_CITY->standingStones(c,x,y,z,h,false);
                }
            }
    }
    private static void spring(Canvas c,int x,int y,int z,long h){
        int radius=4+pick(h,17,3);
        // The basin is authored as solid columns, then source water. Never carve a leak.
        for(int dx=-radius-2;dx<=radius+2;dx++)for(int dz=-radius-2;dz<=radius+2;dz++){
            double r=Math.hypot(dx,dz);if(r>radius+1.5)continue;
            c.column(x+dx,z+dz,y-5,y-1,Material.MOSSY_COBBLESTONE);
            c.put(x+dx,y,z+dz,r<=radius?Material.WATER:Material.MOSS_BLOCK);
            if(r>radius&&r<radius+1.5&&pick(salt(h,dx*37+dz),0,4)==0)c.put(x+dx,y+1,z+dz,Material.FERN);
        }
        c.put(x,y-1,z,Material.SEA_LANTERN);
        // An offset rock lip makes each spring read as a natural source.
        c.ellipsoid(x-radius,y+2,z-1,2.6,2,2.4,Material.MOSSY_COBBLESTONE,false);
        c.ellipsoid(x-radius,y+3,z-1,2.1,1.3,2,Material.MOSS_BLOCK,false);
    }
    private static void rockGarden(Canvas c,int x,int y,int z,long h){
        int count=2+pick(h,17,3);
        for(int i=0;i<count;i++){
            long stone=salt(h,i);int dx=pick(stone,0,13)-6,dz=pick(stone,23,13)-6;
            double radius=2+pick(stone,9,4)*.5;
            c.ellipsoid(x+dx,y,z+dz,radius,1.4+pick(stone,32,3),radius*.8,Material.CALCITE,false);
            c.ellipsoid(x+dx-1,y+1,z+dz,radius*.72,1.5,radius*.6,Material.MOSS_BLOCK,false);
            c.put(x+dx,y+3,z+dz,Material.FLOWERING_AZALEA_LEAVES);
        }
    }
    private static void standingStones(Canvas c,int x,int y,int z,long h,boolean moss){
        int count=2+pick(h,17,4);double angle=pick(h,3,628)/100.0;
        for(int i=0;i<count;i++){
            double a=angle+i*Math.PI*2/count;int px=x+(int)Math.round(Math.cos(a)*7),pz=z+(int)Math.round(Math.sin(a)*7);
            int height=3+pick(salt(h,i),17,6);
            c.box(px-1,y-2,pz-1,px+1,y+height,pz+1,moss?Material.MOSSY_STONE_BRICKS:Material.CALCITE);
            c.box(px-2,y+height+1,pz-2,px+2,y+height+1,pz+2,Material.CHISELED_STONE_BRICKS);
            if(moss)c.ellipsoid(px,y+height+2,pz,2.7,1.4,2.4,Material.AZALEA_LEAVES,true);
            else c.put(px,y+height+2,pz,Material.AMETHYST_BLOCK);
        }
    }
    private static void fallenTree(Canvas c,VoidGenerator g,int x,int z,long h){
        double a=pick(h,5,628)/100.0;int length=8+pick(h,27,5);
        for(int step=-length;step<=length;step++){
            int px=x+(int)Math.round(Math.cos(a)*step),pz=z+(int)Math.round(Math.sin(a)*step);
            var s=g.surface(px,pz);if(!s.land())continue;
            c.ellipsoid(px,s.top()+1,pz,1.5,1.3,1.5,Material.DARK_OAK_LOG,false);
            if(step%4==0){c.put(px,s.top()+3,pz,Material.MOSS_CARPET);c.put(px+1,s.top()+2,pz,Material.BROWN_MUSHROOM);}
        }
        var s=g.surface(x,z);c.line(x,s.top()+2,z,x+4*Math.sin(a),s.top()+5,z-4*Math.cos(a),.8,Material.DARK_OAK_LOG);
    }
    public static void iceShelf(Canvas c,VoidGenerator g,int x,int z,long h){
        int length=8+pick(h,12,5);double a=pick(h,5,628)/100.0;
        for(int i=-length;i<=length;i++){
            int px=x+(int)Math.round(Math.cos(a)*i),pz=z+(int)Math.round(Math.sin(a)*i);
            var s=g.surface(px,pz);if(!s.land())continue;
            double radius=3.0+Math.sin((i+length)/(double)(length*2)*Math.PI)*2.5;
            c.ellipsoid(px,s.top()+3,pz,radius,3.5,radius*.7,Material.BLUE_ICE,false);
            c.ellipsoid(px,s.top()+5,pz,radius*.78,1.5,radius*.6,Material.SNOW_BLOCK,false);
        }
    }
    private static void snowDrift(Canvas c,VoidGenerator g,int x,int z,long h){
        int rx=7+pick(h,13,5),rz=5+pick(h,31,4);
        for(int dx=-rx;dx<=rx;dx++)for(int dz=-rz;dz<=rz;dz++){
            double r=dx*dx/(double)(rx*rx)+dz*dz/(double)(rz*rz);if(r>1)continue;
            var s=g.surface(x+dx,z+dz);if(!s.land())continue;
            int depth=(int)Math.round((1-r)*(2+pick(h,21,4)));
            c.column(x+dx,z+dz,s.top()+1,s.top()+depth,Material.SNOW_BLOCK);
        }
    }
    public static void districtDetails(Canvas c,int x,int y,int z,long h,int variant){
        for(int i=0;i<4+variant;i++){
            long detail=salt(h,i+307);int px=x-24+pick(detail,0,49),pz=z+(i%2==0?-51:51);
            if(variant==0){
                c.box(px-2,y,pz-2,px+2,y+1,pz+2,Material.MOSSY_STONE_BRICKS);
                c.put(px,y+2,pz,Material.AMETHYST_BLOCK);
            }else if(variant==1){
                c.box(px-3,y+1,pz,px+3,y+4,pz,Material.QUARTZ_BRICKS);
                c.box(px-2,y+5,pz-2,px+2,y+5,pz+2,Material.WAXED_OXIDIZED_COPPER);
                c.put(px,y+1,pz-1,Material.FLOWER_POT);
            }else if(variant==2){
                c.line(px-4,y+1,pz,px+4,y+3,pz,1.3,Material.QUARTZ_PILLAR);
                c.put(px,y+4,pz,Material.PALE_MOSS_CARPET);
            }else{
                c.box(px,y+1,pz,px,y+7,pz,Material.WAXED_OXIDIZED_COPPER);
                c.line(px,y+7,pz,px-2,y+7,pz,1,Material.WAXED_OXIDIZED_COPPER);
                c.put(px-2,y+6,pz,Material.SEA_LANTERN);
            }
        }
    }
}
