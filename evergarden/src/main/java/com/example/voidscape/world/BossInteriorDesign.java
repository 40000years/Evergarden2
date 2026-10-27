package com.example.voidscape.world;

import org.bukkit.Material;
import org.bukkit.block.BlockFace;

/** Interior-only furnishings; every addition is clipped against the approved architecture. */
final class BossInteriorDesign {
    private BossInteriorDesign(){}
    static void build(BossStructure b){
        var f=new Furnisher(b);
        switch(b.kind){case ARKHAIA->arkhaia(f);case VALKYRIE->valkyrie(f);case SERAPHON->seraphon(f);}
        treasuryAltar(f);
        registerTreasures(f);
    }
    public static boolean inside(BossStructureLayout.Kind kind,int x,int y,int z){
        int floor=kind.floorY;
        if(y<floor||Math.hypot(x,z)<=kind.arenaRadius)return false;
        return switch(kind){
            case ARKHAIA -> Math.abs(x)<=27&&z>=-91&&z<=-50&&y<=floor+88
                ||Math.abs(x)>=60&&Math.abs(x)<=76&&z>=-40&&z<=39&&y<=floor+32;
            case VALKYRIE -> x*x/(17.0*17)+Math.pow((z+62)/14.0,2)<=1&&y<=floor+44;
            case SERAPHON -> x*x/(27.0*27)+Math.pow((z+89)/23.0,2)<=1&&y<=floor+62;
        };
    }
    private static final class Furnisher {
        final BossStructure b,original;final int floor;
        Furnisher(BossStructure b){this.b=b;floor=b.floor;original=BossStructure.architecture(b.kind);}
        boolean allowed(int x,int y,int z){
            if(!inside(b.kind,x,y,z))return false;
            var old=original.at(x,y,z);
            return old==Material.AIR||y==floor||old==Material.BOOKSHELF
                ||x>=3&&x<=7&&z>=b.kind.chestZ-2&&z<=b.kind.chestZ+2&&y<=floor+4;
        }
        void put(int x,int y,int z,Material m){put(x,y,z,m,null);}
        void put(int x,int y,int z,Material m,BlockFace face){if(allowed(x,y,z))b.box(x,y,z,x,y,z,m,face);}
        void box(int x1,int y1,int z1,int x2,int y2,int z2,Material m){box(x1,y1,z1,x2,y2,z2,m,null);}
        void box(int x1,int y1,int z1,int x2,int y2,int z2,Material m,BlockFace face){
            for(int x=x1;x<=x2;x++)for(int z=z1;z<=z2;z++){
                int start=Integer.MIN_VALUE;
                for(int y=y1;y<=y2+1;y++){
                    boolean yes=y<=y2&&allowed(x,y,z);
                    if(yes&&start==Integer.MIN_VALUE)start=y;
                    if(!yes&&start!=Integer.MIN_VALUE){b.box(x,start,z,x,y-1,z,m,face);start=Integer.MIN_VALUE;}
                }
            }
        }
        void line(double ax,double ay,double az,double bx,double by,double bz,Material material){
            int steps=Math.max(1,(int)Math.ceil(Math.sqrt(Math.pow(ax-bx,2)+Math.pow(ay-by,2)+Math.pow(az-bz,2))*2));
            for(int i=0;i<=steps;i++){
                double t=i/(double)steps;put((int)Math.round(ax+(bx-ax)*t),(int)Math.round(ay+(by-ay)*t),(int)Math.round(az+(bz-az)*t),material);
            }
        }
        void guard(int x,int feet,int z,BlockFace facing,Material weapon,boolean diamond){
            box(x-1,feet-2,z-1,x+1,feet-2,z+1,diamond?Material.CHISELED_QUARTZ_BLOCK:Material.CHISELED_POLISHED_BLACKSTONE);
            box(x-1,feet-1,z-1,x+1,feet-1,z+1,diamond?Material.GOLD_BLOCK:Material.POLISHED_BLACKSTONE);
            b.display(x,feet,z,facing,weapon,diamond);
        }
    }
    private static void arkhaia(Furnisher f){
        int y=f.floor;
        // Crimson runner, funerary mosaics and inlaid borders leave generous aisles.
        for(int x=-25;x<=25;x++)for(int z=-90;z<=-51;z++){
            var material=Math.abs(x)<=5?Material.RED_NETHER_BRICKS:Material.POLISHED_DEEPSLATE;
            if(Math.abs(x)==6||Math.abs(x)==24||z==-90||z==-51)material=Material.CHISELED_POLISHED_BLACKSTONE;
            if(Math.abs(x)>8&&Math.floorMod(x+z,9)==0)material=Material.CRACKED_DEEPSLATE_TILES;
            f.put(x,y,z,material);
        }
        for(int z=-86;z<=-53;z++)for(int x=-2;x<=2;x++)f.put(x,y+1,z,Material.RED_CARPET);
        for(int side:new int[]{-1,1}){
            for(int z:new int[]{-56,-72,-87}){
                int x=side*26;
                f.box(x-1,y+1,z-1,x+1,y+54,z+1,Material.POLISHED_BLACKSTONE_BRICKS);
                f.box(x,y+4,z-1,x,y+50,z-1,Material.RED_NETHER_BRICKS);
                for(int h:new int[]{9,25,42,54})f.box(x-1,y+h,z-2,x+1,y+h,z+2,Material.CHISELED_POLISHED_BLACKSTONE);
                f.put(x-side,y+12,z,Material.SOUL_LANTERN);
                f.put(x-side,y+37,z,Material.SOUL_LANTERN);
            }
            for(int z:new int[]{-59,-69,-79}){
                int x=side*13;
                f.box(x-3,y+1,z-1,x+3,y+1,z+1,Material.DARK_OAK_SLAB);
                f.box(x-3,y+2,z+1,x+3,y+2,z+1,Material.DARK_OAK_STAIRS,BlockFace.NORTH);
                for(int dx:new int[]{-4,4})f.box(x+dx,y+1,z-1,x+dx,y+3,z+1,Material.POLISHED_BLACKSTONE_WALL);
            }
            f.guard(side*18,y+2,-87,BlockFace.SOUTH,Material.DIAMOND_SWORD,false);
            for(int z:new int[]{-54,-77})urn(f,side*24,y+1,z,false);
        }
        // A raised, richly framed throne occupies the rear, outside the processional route.
        f.box(-10,y+1,-91,10,y+1,-86,Material.POLISHED_BLACKSTONE_BRICKS);
        f.box(-8,y+2,-91,8,y+2,-88,Material.RED_NETHER_BRICKS);
        f.box(-3,y+3,-90,3,y+3,-87,Material.POLISHED_BLACKSTONE);
        f.box(-2,y+4,-90,2,y+4,-88,Material.CRIMSON_PLANKS);
        f.box(-3,y+4,-91,3,y+13,-91,Material.CHISELED_POLISHED_BLACKSTONE);
        f.box(-2,y+5,-90,2,y+11,-90,Material.RED_STAINED_GLASS);
        f.put(0,y+9,-89,Material.SHROOMLIGHT);
        for(int x:new int[]{-5,5}){f.box(x,y+3,-90,x,y+16,-90,Material.POLISHED_BLACKSTONE_WALL);f.put(x,y+17,-90,Material.SOUL_LANTERN);}
        for(int x=-5;x<=5;x++)f.put(x,y+14+(5-Math.abs(x)), -90,Material.RED_NETHER_BRICKS);
        // Vault ribs and suspended candelabra give the tall nave a finished ceiling.
        for(int z:new int[]{-56,-72,-86}){
            for(int x=-25;x<=25;x++){
                int top=y+54+(int)Math.round(24*(1-Math.abs(x)/25.0));
                f.box(x,top,z,x,top+1,z,Material.POLISHED_BLACKSTONE_BRICKS);
                if(x%6==0)f.put(x,top-1,z,Material.SHROOMLIGHT);
            }
            chandelier(f,0,y+30,z,y+76,8,false);
        }
        // Furnish both floors of the cloisters without changing their exterior arcades.
        for(int side:new int[]{-1,1})for(int level:new int[]{y,y+13}){
            int x=side*73;
            for(int z:new int[]{-32,-10,12,33}){
                f.box(x-2,level+1,z-1,x+2,level+1,z+1,Material.DARK_OAK_SLAB);
                f.box(x-2,level+2,z+1,x+2,level+2,z+1,Material.CRIMSON_STAIRS,BlockFace.NORTH);
                f.put(x,level+3,z,Material.RED_CANDLE,BlockFace.UP);
                f.box(side*75,level+1,z-1,side*75,level+8,z+1,Material.POLISHED_BLACKSTONE_BRICKS);
                f.put(side*75,level+9,z,Material.SOUL_LANTERN);
            }
            for(int z=-36;z<=37;z++)f.put(side*65,level+1,z,Material.RED_CARPET);
        }
    }
    private static void valkyrie(Furnisher f){
        int y=f.floor;
        mosaic(f,-62,16,13,Material.QUARTZ_BRICKS,Material.GOLD_BLOCK,Material.BLUE_GLAZED_TERRACOTTA);
        for(int side:new int[]{-1,1}){
            for(int z:new int[]{-55,-64,-72}){
                int x=side*15;
                f.box(x,y+1,z-1,x,y+20,z+1,Material.QUARTZ_PILLAR,BlockFace.UP);
                for(int h:new int[]{5,12,20})f.box(x-1,y+h,z-1,x+1,y+h,z+1,Material.GOLD_BLOCK);
                f.put(x-side,y+13,z,Material.SEA_LANTERN);
            }
            for(int z:new int[]{-56,-67}){
                f.box(side*8-2,y+1,z-1,side*8+2,y+1,z+1,Material.SMOOTH_QUARTZ_SLAB);
                f.box(side*8-2,y+2,z+1,side*8+2,y+2,z+1,Material.SMOOTH_QUARTZ_STAIRS,BlockFace.NORTH);
            }
            f.guard(side*13,y+4,-61,side<0?BlockFace.EAST:BlockFace.WEST,Material.DIAMOND_SPEAR,true);
            urn(f,side*8,y+1,-74,true);
            f.box(side*13-1,y+1,-54,side*13+1,y+1,-52,Material.GOLD_BLOCK);
            f.put(side*13,y+2,-53,Material.FLOWERING_AZALEA_LEAVES);
            f.put(side*13,y+3,-53,Material.BLUE_ORCHID);
        }
        // A small sculpted blade altar sits at the far wall, under the round reliquary dome.
        f.box(-7,y+1,-75,7,y+1,-72,Material.CHISELED_QUARTZ_BLOCK);
        f.box(-4,y+2,-75,4,y+2,-73,Material.GOLD_BLOCK);
        f.box(-1,y+3,-74,1,y+9,-74,Material.IRON_BLOCK);
        f.box(-4,y+5,-74,4,y+5,-74,Material.GOLD_BLOCK);
        f.put(0,y+10,-74,Material.SEA_LANTERN);
        for(int side:new int[]{-1,1})for(int t=0;t<=7;t++){
            f.put(side*(3+t),y+9+t,-74,Material.SMOOTH_QUARTZ);
            f.put(side*(4+t),y+8+t,-74,Material.GOLD_BLOCK);
        }
        chandelier(f,0,y+23,-62,y+40,8,true);
        for(int z:new int[]{-55,-69})for(int x=-14;x<=14;x++){
            int top=y+25+(int)Math.round(6*Math.sqrt(Math.max(0,1-x*x/196.0)));
            f.put(x,top,z,Material.CHISELED_QUARTZ_BLOCK);
            if(Math.floorMod(x,5)==0)f.put(x,top-1,z,Material.SEA_LANTERN);
        }
    }
    private static void seraphon(Furnisher f){
        int y=f.floor;
        mosaic(f,-89,26,22,Material.SMOOTH_QUARTZ,Material.GOLD_BLOCK,Material.CYAN_GLAZED_TERRACOTTA);
        for(int side:new int[]{-1,1}){
            for(int z:new int[]{-74,-89,-103}){
                int x=side*(z==-89?24:19);
                f.box(x-1,y+1,z-1,x+1,y+42,z+1,Material.QUARTZ_PILLAR,BlockFace.UP);
                for(int h:new int[]{4,16,31,42})f.box(x-2,y+h,z-1,x+2,y+h,z+1,Material.CHISELED_QUARTZ_BLOCK);
                f.box(x-side,y+7,z,x-side,y+37,z,Material.GOLD_BLOCK);
                f.put(x-side*2,y+22,z,Material.SEA_LANTERN);
            }
            f.guard(side*19,y+6,-90,side<0?BlockFace.EAST:BlockFace.WEST,Material.DIAMOND_SWORD,true);
            for(int z:new int[]{-77,-98}){
                int x=side*12;
                f.box(x-3,y+1,z-1,x+3,y+1,z+1,Material.QUARTZ_BRICKS);
                f.box(x-3,y+2,z+1,x+3,y+2,z+1,Material.SMOOTH_QUARTZ_STAIRS,BlockFace.NORTH);
                f.put(x,y+3,z+1,Material.WHITE_CANDLE,BlockFace.UP);
            }
            urn(f,side*23,y+1,-89,true);
            for(int z:new int[]{-80,-101})f.put(side*21,y+2,z,Material.END_ROD);
        }
        // A winged ceremonial throne and luminous astrolabe replace the bare rear dais.
        f.box(-6,y+4,-106,6,y+4,-102,Material.GOLD_BLOCK);
        f.box(-3,y+5,-106,3,y+5,-103,Material.CHISELED_QUARTZ_BLOCK);
        f.box(-3,y+6,-106,3,y+16,-106,Material.SMOOTH_QUARTZ);
        f.box(-2,y+7,-105,2,y+14,-105,Material.LIGHT_BLUE_STAINED_GLASS);
        f.put(0,y+11,-104,Material.SEA_LANTERN);
        for(int side:new int[]{-1,1})for(int t=0;t<9;t++){
            f.put(side*(4+t),y+12+t,-105,Material.CHISELED_QUARTZ_BLOCK);
            f.put(side*(4+t),y+13+t,-105,Material.GOLD_BLOCK);
        }
        chandelier(f,0,y+31,-89,y+60,12,true);
        for(int x=-20;x<=20;x++)for(int z=-107;z<=-69;z++){
            double r=Math.hypot(x,(z+89)*1.1);
            if(Math.abs(r-18)<.7)f.put(x,y+47,z,Material.GOLD_BLOCK);
            if(Math.abs(r-13)<.65)f.put(x,y+50,z,Material.SEA_LANTERN);
        }
    }
    private static void mosaic(Furnisher f,int cz,int rx,int rz,Material base,Material border,Material accent){
        for(int x=-rx;x<=rx;x++)for(int z=cz-rz;z<=cz+rz;z++){
            double r=Math.sqrt(x*x/(double)(rx*rx)+Math.pow((z-cz)/(double)rz,2));
            if(r>1)continue;
            double angle=Math.atan2((z-cz)/(double)rz,x/(double)rx);
            Material m=r>.91||Math.abs(r-.62)<.025?border:
                Math.abs(Math.sin(angle*6))<.12&&r>.2?accent:base;
            f.put(x,f.floor,z,m);
        }
    }
    private static void chandelier(Furnisher f,int cx,int y,int cz,int ceiling,int radius,boolean bright){
        var metal=bright?Material.GOLD_BLOCK:Material.POLISHED_BLACKSTONE_WALL;
        f.box(cx,y+1,cz,cx,ceiling,cz,Material.IRON_CHAIN);
        for(int i=0;i<48;i++){
            double a=i*Math.PI/24;int x=cx+(int)Math.round(Math.cos(a)*radius),z=cz+(int)Math.round(Math.sin(a)*radius);
            f.put(x,y,z,metal);
            if(i%6==0){
                f.line(cx,y+5,cz,x,y,z,metal);
                f.put(x,y-1,z,bright?Material.LANTERN:Material.SOUL_LANTERN,BlockFace.UP);
                f.put(x,y+1,z,bright?Material.WHITE_CANDLE:Material.RED_CANDLE,BlockFace.UP);
            }
        }
        f.put(cx,y-2,cz,bright?Material.SEA_LANTERN:Material.SHROOMLIGHT);
    }
    private static void urn(Furnisher f,int x,int y,int z,boolean bright){
        f.box(x-1,y,z-1,x+1,y,z+1,bright?Material.CHISELED_QUARTZ_BLOCK:Material.CHISELED_POLISHED_BLACKSTONE);
        f.put(x,y+1,z,Material.DECORATED_POT);
        f.put(x,y+2,z,bright?Material.WHITE_CANDLE:Material.RED_CANDLE,BlockFace.UP);
    }
    private static void treasuryAltar(Furnisher f){
        int z=f.b.kind.chestZ,y=f.floor;boolean dark=f.b.kind==BossStructureLayout.Kind.ARKHAIA;
        var stone=dark?Material.POLISHED_BLACKSTONE_BRICKS:Material.CHISELED_QUARTZ_BLOCK;
        var trim=dark?Material.RED_NETHER_BRICKS:Material.GOLD_BLOCK;
        f.box(3,y,z-2,7,y+4,z+2,Material.AIR);
        f.box(3,y,z-2,7,y,z+2,stone);
        for(int x:new int[]{3,7}){
            f.box(x,y+1,z+1,x,y+5,z+1,stone);
            f.put(x,y+6,z+1,dark?Material.SOUL_LANTERN:Material.LANTERN);
        }
        f.box(4,y+4,z+1,6,y+4,z+1,trim);
        f.put(5,y+2,z+1,dark?Material.RED_GLAZED_TERRACOTTA:Material.BLUE_GLAZED_TERRACOTTA);
        f.put(5,y+5,z+1,dark?Material.SHROOMLIGHT:Material.SEA_LANTERN);
    }
    private static void registerTreasures(Furnisher f){
        int y=f.floor,z=f.b.kind.chestZ;
        f.b.treasure(4,y+1,z,BlockFace.NORTH);f.b.treasure(6,y+1,z,BlockFace.NORTH);
        switch(f.b.kind){
            case ARKHAIA->{
                for(int side:new int[]{-1,1})for(int dz:new int[]{-57,-83})chest(f,side*20,y+2,dz,side<0?BlockFace.EAST:BlockFace.WEST);
                for(int side:new int[]{-1,1})chest(f,side*68,y+15,20,side<0?BlockFace.EAST:BlockFace.WEST);
            }
            case VALKYRIE->{
                for(int side:new int[]{-1,1})for(int dz:new int[]{-53,-70})chest(f,side*11,y+2,dz,side<0?BlockFace.EAST:BlockFace.WEST);
                for(int side:new int[]{-1,1})chest(f,side*7,y+3,-74,BlockFace.SOUTH);
            }
            case SERAPHON->{
                for(int side:new int[]{-1,1})for(int dz:new int[]{-74,-100})chest(f,side*17,y+2,dz,side<0?BlockFace.EAST:BlockFace.WEST);
                for(int side:new int[]{-1,1})chest(f,side*10,y+3,-103,BlockFace.SOUTH);
            }
        }
    }
    private static void chest(Furnisher f,int x,int y,int z,BlockFace face){
        boolean dark=f.b.kind==BossStructureLayout.Kind.ARKHAIA;
        var material=dark?Material.CHISELED_POLISHED_BLACKSTONE:Material.CHISELED_QUARTZ_BLOCK;
        f.box(x-1,y-2,z-1,x+1,y-1,z+1,material);
        f.put(x-1,y,z,dark?Material.RED_CANDLE:Material.WHITE_CANDLE,BlockFace.UP);
        f.put(x+1,y,z,dark?Material.SOUL_LANTERN:Material.LANTERN);
        // Open headroom is essential for both right-click access and the chest lid animation.
        f.put(x,y+1,z,Material.AIR);f.put(x,y+2,z,Material.AIR);
        f.b.treasure(x,y,z,face);
    }
}
