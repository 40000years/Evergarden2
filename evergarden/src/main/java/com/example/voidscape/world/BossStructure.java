package com.example.voidscape.world;

import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.generator.ChunkGenerator.ChunkData;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Three immutable vanilla-block builds, with ordered air cuts and chunk-local rendering. */
public final class BossStructure {
    public static final int MIN_Y=40;
    public record Box(int x1,int y1,int z1,int x2,int y2,int z2,Material material,BlockFace facing){}
    public record Point(int x,int y,int z){}
    public record Treasure(Point point,BlockFace facing){}
    public record Display(Point point,BlockFace facing,Material weapon,boolean diamond){}
    private record Style(Material material,BlockFace facing){}
    final BossStructureLayout.Kind kind;
    private final List<Box> boxes=new ArrayList<>();
    private final Map<Long,List<Box>> chunks=new HashMap<>();
    private final Map<Style,BlockData> states=new ConcurrentHashMap<>();
    private final List<Point> traps=new ArrayList<>();
    private final List<Treasure> treasures=new ArrayList<>();
    private final List<Display> displays=new ArrayList<>();
    private final List<Box> interiorBoxes;
    private final Material stone,trim,accent,glass,light;
    final int floor;
    private static final class Holder {
        static final Map<BossStructureLayout.Kind,BossStructure> BUILDS;
        static {
            var builds=new EnumMap<BossStructureLayout.Kind,BossStructure>(BossStructureLayout.Kind.class);
            for(var kind:BossStructureLayout.Kind.values())builds.put(kind,new BossStructure(kind,false,true));
            BUILDS=Map.copyOf(builds);
        }
    }
    public static BossStructure blueprint(BossStructureLayout.Kind kind){return Holder.BUILDS.get(kind);}
    private static final class LegacyHolder {
        static final Map<BossStructureLayout.Kind,BossStructure> BUILDS;
        static {
            var builds=new EnumMap<BossStructureLayout.Kind,BossStructure>(BossStructureLayout.Kind.class);
            for(var kind:BossStructureLayout.Kind.values())builds.put(kind,new BossStructure(kind,true,false));
            BUILDS=Map.copyOf(builds);
        }
    }
    public static BossStructure blueprint(BossStructureLayout.Kind kind,boolean legacy){
        return legacy?LegacyHolder.BUILDS.get(kind):blueprint(kind);
    }
    private static final class ArchitectureHolder {
        static final Map<BossStructureLayout.Kind,BossStructure> BUILDS;
        static {
            var builds=new EnumMap<BossStructureLayout.Kind,BossStructure>(BossStructureLayout.Kind.class);
            for(var kind:BossStructureLayout.Kind.values())builds.put(kind,new BossStructure(kind,false,false));
            BUILDS=Map.copyOf(builds);
        }
    }
    /** Exact published e2.49 geometry, used to preserve exterior blocks and upgrade interiors. */
    public static BossStructure architecture(BossStructureLayout.Kind kind){return ArchitectureHolder.BUILDS.get(kind);}
    private static long key(int x,int z){return ((long)x<<32)|(z&0xffffffffL);}
    private BossStructure(BossStructureLayout.Kind kind,boolean legacy,boolean furnished){
        this.kind=kind;floor=kind.floorY;
        boolean dark=kind==BossStructureLayout.Kind.ARKHAIA;
        stone=dark?Material.DEEPSLATE_BRICKS:Material.SMOOTH_QUARTZ;
        trim=dark?Material.POLISHED_BLACKSTONE_BRICKS:Material.CHISELED_QUARTZ_BLOCK;
        accent=dark?Material.CRIMSON_PLANKS:Material.GOLD_BLOCK;
        glass=dark?Material.PURPLE_STAINED_GLASS:Material.LIGHT_BLUE_STAINED_GLASS;
        light=dark?Material.SHROOMLIGHT:Material.SEA_LANTERN;
        if(legacy){
            foundation();arena();
            switch(kind){case ARKHAIA->castle();case VALKYRIE->valkyrie();case SERAPHON->sanctuary();}
        }else switch(kind){
            case ARKHAIA->ArkhaiaCastleDesign.build(this);
            case VALKYRIE->ValkyrieTempleDesign.build(this);
            case SERAPHON->SeraphonSanctuaryDesign.build(this);
        }
        int interiorStart=boxes.size();
        if(furnished)BossInteriorDesign.build(this);else library();
        interiorBoxes=furnished?List.copyOf(boxes.subList(interiorStart,boxes.size())):List.of();
        // Preserve a broad walkable spine between the arrival, arena and rear treasury.
        box(-3,floor+1,kind.chestZ-4,2,floor+4,kind.arrivalZ+2,Material.AIR);
        for(int z=kind.chestZ-4;z<=kind.arrivalZ+2;z++)for(int x=-3;x<=2;x++)
            if(Math.hypot(x,z)>kind.arenaRadius)block(x,floor,z,trim);
        // Index in insertion order so later cuts override roofs, walls and foundations.
        for(var b:boxes)for(int x=Math.floorDiv(b.x1,16);x<=Math.floorDiv(b.x2,16);x++)
            for(int z=Math.floorDiv(b.z1,16);z<=Math.floorDiv(b.z2,16);z++)
                chunks.computeIfAbsent(key(x,z),ignored->new ArrayList<>()).add(b);
        chunks.replaceAll((k,v)->List.copyOf(v));
    }
    void box(int x1,int y1,int z1,int x2,int y2,int z2,Material m){box(x1,y1,z1,x2,y2,z2,m,null);}
    void box(int x1,int y1,int z1,int x2,int y2,int z2,Material m,BlockFace face){
        if(x1>x2||y1>y2||z1>z2)return;
        if(x1<-kind.radius||x2>kind.radius||z1<-kind.radius||z2>kind.radius||y1<MIN_Y||y2>kind.maxY)
            throw new IllegalArgumentException(kind.id+" exceeds reservation: "+x1+","+y1+","+z1+" .. "+x2+","+y2+","+z2);
        boxes.add(new Box(x1,y1,z1,x2,y2,z2,m,face));
    }
    void block(int x,int y,int z,Material m){box(x,y,z,x,y,z,m);}
    void trap(int x,int y,int z){traps.add(new Point(x,y,z));}
    void treasure(int x,int y,int z,BlockFace facing){
        treasures.add(new Treasure(new Point(x,y,z),facing));
        box(x,y,z,x,y,z,Material.CHEST,facing);
    }
    void display(int x,int y,int z,BlockFace facing,Material weapon,boolean diamond){
        displays.add(new Display(new Point(x,y,z),facing,weapon,diamond));
    }
    void disk(int cx,int y,int cz,int radius,Material material){
        for(int x=-radius;x<=radius;x++){
            int z=(int)Math.floor(Math.sqrt(radius*radius-x*x));
            box(cx+x,y,cz-z,cx+x,y,cz+z,material);
        }
    }
    void ring(int cx,int y,int cz,double radius,double width,Material material){
        int extent=(int)Math.ceil(radius+width);
        for(int x=-extent;x<=extent;x++){
            int start=Integer.MIN_VALUE;
            for(int z=-extent;z<=extent+1;z++){
                boolean inside=z<=extent&&Math.abs(Math.hypot(x,z)-radius)<=width;
                if(inside&&start==Integer.MIN_VALUE)start=z;
                if(!inside&&start!=Integer.MIN_VALUE){box(cx+x,y,cz+start,cx+x,y,cz+z-1,material);start=Integer.MIN_VALUE;}
            }
        }
    }
    void ellipse(int cx,int y,int cz,int rx,int rz,Material material){
        ellipse(cx,y,cz,(double)rx,(double)rz,material);
    }
    void ellipse(int cx,int y,int cz,double rx,double rz,Material material){
        if(rx<=0||rz<=0){block(cx,y,cz,material);return;}
        for(int x=-(int)Math.floor(rx);x<=Math.floor(rx);x++){
            int z=(int)Math.floor(rz*Math.sqrt(Math.max(0,1-x*x/(rx*rx))));
            box(cx+x,y,cz-z,cx+x,y,cz+z,material);
        }
    }
    void ellipseRing(int cx,int y,int cz,int rx,int rz,double width,Material material){
        ellipseRing(cx,y,cz,(double)rx,(double)rz,width,material);
    }
    void ellipseRing(int cx,int y,int cz,double rx,double rz,double width,Material material){
        if(rx<=0||rz<=0){block(cx,y,cz,material);return;}
        int extent=(int)Math.ceil(width),limitZ=(int)Math.floor(rz+extent);
        for(int x=-(int)Math.ceil(rx)-extent;x<=rx+extent;x++){
            int start=Integer.MIN_VALUE;
            for(int z=-(int)Math.ceil(rz)-extent;z<=limitZ+1;z++){
                boolean inside=z<=limitZ&&Math.abs(Math.sqrt(x*x/(rx*rx)+z*z/(rz*rz))-1)*Math.min(rx,rz)<=width;
                if(inside&&start==Integer.MIN_VALUE)start=z;
                if(!inside&&start!=Integer.MIN_VALUE){box(cx+x,y,cz+start,cx+x,y,cz+z-1,material);start=Integer.MIN_VALUE;}
            }
        }
    }
    void line(double ax,double ay,double az,double bx,double by,double bz,double radius,Material material){
        int steps=Math.max(1,(int)Math.ceil(Math.sqrt(Math.pow(ax-bx,2)+Math.pow(ay-by,2)+Math.pow(az-bz,2))*2));
        for(int step=0;step<=steps;step++){
            double t=step/(double)steps,cx=ax+(bx-ax)*t,cy=ay+(by-ay)*t,cz=az+(bz-az)*t;
            int r=(int)Math.ceil(radius);
            for(int x=(int)Math.round(cx)-r;x<=(int)Math.round(cx)+r;x++)
                for(int z=(int)Math.round(cz)-r;z<=(int)Math.round(cz)+r;z++){
                    double distance=radius*radius-Math.pow(x-cx,2)-Math.pow(z-cz,2);
                    if(distance<0)continue;
                    double dy=Math.sqrt(distance);int y1=(int)Math.ceil(cy-dy),y2=(int)Math.floor(cy+dy);
                    if(y1<=y2)box(x,y1,z,x,y2,z,material);
                }
            if(radius<.8)block((int)Math.round(cx),(int)Math.round(cy),(int)Math.round(cz),material);
        }
    }
    private void foundation(){
        int r=kind.radius;
        for(int x=-r;x<=r;x++)for(int z=-r;z<=r;z++){
            if(Math.abs(x)+Math.abs(z)>r*1.65)continue;
            double edge=Math.max(Math.max(Math.abs(x),Math.abs(z))/(double)r,(Math.abs(x)+Math.abs(z))/(r*1.65));
            int bottom=Math.max(MIN_Y,floor-14-(int)(48*Math.pow(1-edge,.65)));
            box(x,bottom,z,x,floor-7,z,Material.DEEPSLATE);
            box(x,floor-6,z,x,floor-3,z,kind==BossStructureLayout.Kind.ARKHAIA?Material.BLACKSTONE:Material.CALCITE);
            box(x,floor-2,z,x,floor,z,stone);
            if(Math.max(Math.abs(x),Math.abs(z))>r-5){
                block(x,floor,z,trim);
                if(Math.abs(x)>6&&Math.abs(z)>6)block(x,floor+1,z,trim);
            }
        }
        // Decorative underside crystal chains expose the floating silhouette.
        for(int x:new int[]{-r+20,r-20})for(int z:new int[]{-r/2,r/2})
            for(int y=floor-32;y<floor-8;y++)block(x,y,z,y%7==0?light:glass);
    }
    private void arena(){
        int r=kind.arenaRadius;
        for(int x=-r-5;x<=r+5;x++)for(int z=-r-5;z<=r+5;z++){
            double d=Math.hypot(x,z);if(d>r+5)continue;
            Material m=kind==BossStructureLayout.Kind.ARKHAIA?Material.POLISHED_DEEPSLATE:Material.CALCITE;
            if(Math.abs(d-r)<.8||Math.abs(d-r*.68)<.65||Math.abs(d-11)<.65)m=accent;
            if((Math.abs(x)==Math.abs(z)||Math.abs(x)<=1||Math.abs(z)<=1)&&d>12&&d<r-2)m=trim;
            if(d<5)m=kind==BossStructureLayout.Kind.ARKHAIA?Material.RED_GLAZED_TERRACOTTA:Material.BLUE_GLAZED_TERRACOTTA;
            if(Math.abs(d-r)<.8&&Math.floorMod(x+z,11)==0)m=light;
            block(x,floor,z,m);
        }
    }
    private void column(int x,int z,int height){
        box(x-3,floor+1,z-3,x+3,floor+3,z+3,trim);
        box(x-2,floor+4,z-2,x+2,floor+height-3,z+2,stone);
        for(int dx:new int[]{-2,2})box(x+dx,floor+6,z-1,x+dx,floor+height-5,z+1,trim);
        box(x-3,floor+height-2,z-3,x+3,floor+height,z+3,trim);
        box(x-3,floor+height+1,z-3,x+3,floor+height+1,z+3,accent);
        block(x,floor+height+2,z,light);
    }
    private void arch(int cx,int z,int halfWidth,int spring,int rise,int thickness){
        column(cx-halfWidth,z,spring);column(cx+halfWidth,z,spring);
        for(int x=-halfWidth;x<=halfWidth;x++){
            int y=floor+spring+(int)Math.round(rise*(1-Math.abs(x)/(double)halfWidth));
            box(cx+x,y,z-thickness,cx+x,y+2,z+thickness,trim);
            block(cx+x,y+3,z,accent);
        }
    }
    private void hall(int cx,int cz,int rx,int rz,int height){
        box(cx-rx,floor+1,cz-rz,cx+rx,floor+height,cz+rz,stone);
        box(cx-rx+2,floor+1,cz-rz+2,cx+rx-2,floor+height-2,cz+rz-2,Material.AIR);
        // Arched doors on both ends keep the treasury accessible from the courtyard.
        for(int z:new int[]{cz-rz,cz+rz}){
            box(cx-4,floor+1,z-1,cx+4,floor+12,z+1,Material.AIR);
            for(int x=-5;x<=5;x++)block(cx+x,floor+13+(5-Math.abs(x)),z,trim);
        }
        for(int side:new int[]{-1,1})for(int z=cz-rz+7;z<=cz+rz-7;z+=12){
            box(cx+side*rx,floor+9,z-2,cx+side*rx,floor+height-6,z+2,glass);
            for(int y=floor+9;y<=floor+height-6;y+=7)box(cx+side*rx,y,z-2,cx+side*rx,y,z+2,accent);
        }
        // Pitched roofs have layered eaves, with a contrasting spine.
        for(int dx=-rx-2;dx<=rx+2;dx++){
            int y=floor+height+(rx+2-Math.abs(dx));
            box(cx+dx,y,cz-rz-2,cx+dx,y+1,cz+rz+2,kind==BossStructureLayout.Kind.ARKHAIA?Material.POLISHED_BLACKSTONE:Material.QUARTZ_BRICKS);
        }
        box(cx,floor+height+rx+3,cz-rz-2,cx,floor+height+rx+3,cz+rz+2,accent);
    }
    private void tower(int cx,int cz,int radius,int height){
        box(cx-radius-1,floor+1,cz-radius-1,cx+radius+1,floor+4,cz+radius+1,trim);
        box(cx-radius,floor+5,cz-radius,cx+radius,floor+height,cz+radius,stone);
        box(cx-radius+2,floor+5,cz-radius+2,cx+radius-2,floor+height-2,cz+radius-2,Material.AIR);
        for(int side:new int[]{-1,1}){
            box(cx-2,floor+18,cz+side*radius,cx+2,floor+height-8,cz+side*radius,glass);
            box(cx+side*radius,floor+18,cz-2,cx+side*radius,floor+height-8,cz+2,glass);
            box(cx+side*radius,floor+5,cz-radius,cx+side*radius,floor+height,cz-radius,trim);
            box(cx+side*radius,floor+5,cz+radius,cx+side*radius,floor+height,cz+radius,trim);
        }
        for(int h=0;h<=radius*3;h++){
            int r=Math.max(0,radius+2-h/3);
            box(cx-r,floor+height+1+h,cz-r,cx+r,floor+height+1+h,cz+r,h%5==0?accent:trim);
        }
        box(cx,floor+height+radius*3+2,cz,cx,floor+height+radius*3+5,cz,light);
    }
    private void rose(int cx,int cy,int z,int radius){
        for(int x=-radius-1;x<=radius+1;x++)for(int y=-radius-1;y<=radius+1;y++){
            double r=Math.hypot(x,y);if(r>radius+1)continue;
            Material m=r>radius-1?trim:(Math.abs(x)==Math.abs(y)||x==0||y==0)?accent:glass;
            block(cx+x,cy+y,z,m);
        }
        block(cx,cy,z,light);
    }
    private void castle(){
        for(int x:new int[]{-78,78})for(int z:new int[]{-70,70})tower(x,z,9,z<0?66:51);
        // Rear keep and two inhabited wings surround an open gothic courtyard.
        hall(0,-68,25,18,44);
        for(int side:new int[]{-1,1}){
            hall(side*69,-6,12,43,30);
            for(int z=-38;z<=38;z+=19){
                column(side*52,z,28);
                for(int t=0;t<=16;t++){
                    int x=side*(52+t),y=floor+30+(int)Math.round(Math.sin(t/16.0*Math.PI)*9);
                    box(x,y,z-1,x,y+2,z+1,trim);
                }
            }
            // Battlement curtain walls, with arrow-slit windows.
            box(side<0?-91:89,floor+1,-64,side<0?-89:91,floor+20,64,stone);
            for(int z=-60;z<=60;z+=8){
                box(side<0?-92:89,floor+21,z-1,side<0?-89:92,floor+24,z+1,trim);
                block(side*90,floor+13,z,glass);
            }
        }
        arch(0,78,16,33,14,3);
        rose(0,floor+32,-49,12);
        // Crimson rose beds and soul-lit pylons set the beautiful, threatening palette.
        for(int x:new int[]{-35,35})for(int z:new int[]{-70,68}){
            box(x-4,floor+1,z-4,x+4,floor+1,z+4,trim);
            box(x-3,floor+2,z-3,x+3,floor+2,z+3,Material.CRIMSON_NYLIUM);
            block(x,floor+3,z,Material.ROSE_BUSH);
            box(x,floor+4,z,x,floor+4,z,Material.ROSE_BUSH,BlockFace.UP);
            block(x-4,floor+3,z,Material.SOUL_LANTERN);block(x+4,floor+3,z,Material.SOUL_LANTERN);
        }
        // A visible side passage contains pits and repeatable ward plates, off the arena.
        for(int z:new int[]{-27,-3,21}){
            box(-74,floor-3,z-2,-64,floor-1,z+2,Material.AIR);
            box(-74,floor-4,z-2,-64,floor-4,z+2,Material.MAGMA_BLOCK);
            box(-74,floor,z-2,-72,floor,z+2,Material.AIR);
            box(-66,floor,z-2,-64,floor,z+2,Material.AIR);
            for(int x:new int[]{-71,-67})block(x,floor+1,z,Material.COBWEB);
            block(-69,floor,z,Material.RED_GLAZED_TERRACOTTA);
            block(-69,floor+1,z,Material.POLISHED_BLACKSTONE_PRESSURE_PLATE);
            traps.add(new Point(-69,floor+1,z));
        }
    }
    private void valkyrie(){
        hall(0,-60,22,14,36);
        for(int x:new int[]{-70,70})for(int z:new int[]{-62,58})tower(x,z,7,z<0?65:53);
        arch(0,65,18,39,16,3);
        for(int x:new int[]{-58,58})for(int z:new int[]{-36,-12,12,36})column(x,z,42);
        for(int side:new int[]{-1,1}){
            hall(side*76,-2,8,39,28);
            // Swept feather ribs, raised well above the combat floor.
            for(int feather=0;feather<5;feather++)for(int t=0;t<=38;t++){
                int x=side*(18+t),z=-22+feather*10+t/3;
                int y=floor+56+(int)Math.round(Math.sin(t/38.0*Math.PI)*13)-feather*2;
                box(x,y,z,x,y+1,z+1,feather%2==0?trim:stone);
                if(t%9==0)block(x,y+2,z,accent);
            }
            for(int z=-44;z<=44;z++){
                block(side*58,floor+44,z,trim);block(side*58,floor+45,z,accent);
            }
        }
        // A monumental sword on the rear roof marks the armed Valkyrie temple.
        box(-1,floor+60,-61,1,floor+84,-59,Material.IRON_BLOCK);
        box(-7,floor+64,-62,7,floor+66,-58,accent);
        box(0,floor+85,-60,0,floor+88,-60,light);
        rose(0,floor+27,-45,10);
        for(int x:new int[]{-30,30})for(int z:new int[]{-62,58}){
            box(x-4,floor+1,z-4,x+4,floor+1,z+4,trim);
            box(x-3,floor+2,z-3,x+3,floor+2,z+3,Material.BLUE_STAINED_GLASS);
            block(x,floor+3,z,light);
        }
    }
    private void sanctuary(){
        hall(0,-80,29,20,54);
        tower(0,-82,12,84);
        for(int x:new int[]{-91,91})for(int z:new int[]{-70,70})tower(x,z,9,z<0?80:64);
        for(int x:new int[]{-78,78}){
            hall(x,-2,11,47,46);
            for(int z=-42;z<=42;z+=21)column(x<0?-66:66,z,61);
            for(int z=-48;z<=48;z++){
                block(x<0?-66:66,floor+63,z,trim);block(x<0?-66:66,floor+64,z,accent);
            }
        }
        arch(0,84,23,56,24,4);
        rose(0,floor+53,-59,17);
        // Eight elevated buttress ribs form a crown; the entire fighting disc stays clear.
        for(int rib=0;rib<8;rib++){
            double angle=rib*Math.PI/4;
            int x=(int)Math.round(Math.cos(angle)*72),z=(int)Math.round(Math.sin(angle)*72);
            column(x,z,50);
            for(int t=0;t<=32;t++){
                double r=72-t;
                int bx=(int)Math.round(Math.cos(angle)*r),bz=(int)Math.round(Math.sin(angle)*r);
                int y=floor+53+(int)Math.round(33*Math.sin(t/32.0*Math.PI/2));
                box(bx-1,y,bz-1,bx+1,y+2,bz+1,trim);
            }
        }
        halo(43,floor+88,accent);halo(28,floor+99,trim);halo(15,floor+108,accent);
        for(int x:new int[]{-40,40})for(int z:new int[]{-88,82}){
            box(x-6,floor+1,z-5,x+6,floor+1,z+5,trim);
            box(x-5,floor+2,z-4,x+5,floor+2,z+4,Material.LIGHT_BLUE_STAINED_GLASS);
            for(int dx:new int[]{-6,6})column(x+dx,z,12);
        }
        // Processional lanterns and gold-inlaid paths distinguish the supreme sanctuary.
        for(int z=64;z<=112;z+=8)for(int x:new int[]{-7,7}){
            block(x,floor+1,z,trim);block(x,floor+2,z,light);
        }
    }
    private void halo(int radius,int y,Material m){
        for(int x=-radius-2;x<=radius+2;x++)for(int z=-radius-2;z<=radius+2;z++)
            if(Math.abs(Math.hypot(x,z)-radius)<1.2){
                block(x,y,z,m);
                if(Math.floorMod(x+z,13)==0)block(x,y+1,z,light);
            }
    }
    private void library(){
        int z=kind.chestZ,y=floor+1;
        box(3,floor,z-2,7,floor,z+2,Material.SPRUCE_PLANKS);
        box(3,y,z-2,7,y+3,z,Material.AIR);
        box(3,y,z+1,7,y+2,z+1,Material.BOOKSHELF);
        block(3,y+3,z+1,light);block(7,y+3,z+1,light);
        // Chests are filled once on chunk load, using the existing shared treasure table.
    }
    public BossStructureLayout.Kind kind(){return kind;}
    public List<Box> boxes(){return Collections.unmodifiableList(boxes);}
    public List<Point> traps(){return List.copyOf(traps);}
    public List<Treasure> treasures(){return List.copyOf(treasures);}
    public List<Display> displays(){return List.copyOf(displays);}
    public List<Box> interiorBoxes(){return interiorBoxes;}
    List<Box> chunkBoxes(int cx,int cz){return chunks.getOrDefault(key(cx,cz),List.of());}
    BlockData state(Material material,BlockFace facing){
        return states.computeIfAbsent(new Style(material,facing),style->{
            var data=style.material.createBlockData();
            if(data instanceof org.bukkit.block.data.type.Leaves leaves)leaves.setPersistent(true);
            if(data instanceof org.bukkit.block.data.Bisected plant&&style.facing==BlockFace.UP)
                plant.setHalf(org.bukkit.block.data.Bisected.Half.TOP);
            if(data instanceof org.bukkit.block.data.type.Slab slab&&style.facing==BlockFace.UP)
                slab.setType(org.bukkit.block.data.type.Slab.Type.TOP);
            if(data instanceof org.bukkit.block.data.type.Lantern lantern&&style.facing==BlockFace.UP)lantern.setHanging(true);
            if(data instanceof org.bukkit.block.data.type.Candle candle){candle.setLit(true);candle.setCandles(3);}
            if(style.facing!=null&&data instanceof Directional direction)direction.setFacing(style.facing);
            if(style.facing!=null&&data instanceof org.bukkit.block.data.Rotatable rotated&&style.facing!=BlockFace.UP)
                rotated.setRotation(style.facing);
            return data;
        });
    }
    public Material at(int x,int y,int z){
        var list=chunks.getOrDefault(key(Math.floorDiv(x,16),Math.floorDiv(z,16)),List.of());
        for(int i=list.size()-1;i>=0;i--){var b=list.get(i);
            if(x>=b.x1&&x<=b.x2&&y>=b.y1&&y<=b.y2&&z>=b.z1&&z<=b.z2)return b.material;
        }
        return Material.AIR;
    }
    public void render(ChunkData target,int cx,int cz,int originX,int originZ){
        if(Math.floorMod(originX,16)!=0||Math.floorMod(originZ,16)!=0)throw new IllegalArgumentException("Chunk-aligned origin required");
        int ox=cx*16-originX,oz=cz*16-originZ;
        for(var b:chunks.getOrDefault(key(Math.floorDiv(ox,16),Math.floorDiv(oz,16)),List.of())){
            int x1=Math.max(0,b.x1-ox),x2=Math.min(15,b.x2-ox),z1=Math.max(0,b.z1-oz),z2=Math.min(15,b.z2-oz);
            int y1=Math.max(target.getMinHeight(),b.y1),y2=Math.min(target.getMaxHeight()-1,b.y2);
            if(x1>x2||z1>z2||y1>y2)continue;
            if(b.facing==null&&!b.material.name().endsWith("_LEAVES"))target.setRegion(x1,y1,z1,x2+1,y2+1,z2+1,b.material);
            else{
                var state=state(b.material,b.facing);
                target.setRegion(x1,y1,z1,x2+1,y2+1,z2+1,state);
            }
        }
    }
}
