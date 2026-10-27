package com.example.voidscape.world;

import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.bukkit.util.noise.SimplexNoiseGenerator;
import java.util.*;

/** Coordinate-only continental skylands. No chunk loads, mutable randoms or generation-order state. */
public final class BiomeTerrain {
    public enum Kind {
        GARDENS("gardens", "สวน Evergarden", "cherry_grove"),
        GLACIER("glacier", "เทือกเขาธารน้ำแข็ง", "frozen_peaks"),
        ANCIENT_FOREST("ancient-forest", "ป่าโบราณ", "old_growth_spruce_taiga"),
        LUNAR_CITY("lunar-city", "นครจันทราที่สาบสูญ", "pale_garden");
        public final String id, title;
        public final String vanillaId;
        Kind(String id,String title,String vanillaId){this.id=id;this.title=title;this.vanillaId=vanillaId;}
        // Resolve only after Paper initializes its registries; coordinate sampling also runs offline.
        public Biome vanilla(){return switch(this){case GARDENS->Biome.CHERRY_GROVE;case GLACIER->Biome.FROZEN_PEAKS;
            case ANCIENT_FOREST->Biome.OLD_GROWTH_SPRUCE_TAIGA;case LUNAR_CITY->Biome.PALE_GARDEN;};}
        public static Kind fromId(String id){for(var kind:values())if(kind.id.equals(id))return kind;return null;}
    }
    public record Sample(Kind kind,boolean land,int top,int depth,double border) {}
    public record Center(Kind kind,int x,int z) {}
    private final long seed;
    private final int scale;
    private final Set<Long> legacy;
    private final Set<Long> sceneryLegacy;
    private final Set<Long> detailLegacy;
    private final SimplexNoiseGenerator broad,detail,warp;
    public BiomeTerrain(long seed,int scale,Set<Long> legacyRegions){
        this(seed,scale,legacyRegions,Set.of());
    }
    public BiomeTerrain(long seed,int scale,Set<Long> legacyRegions,Set<Long> sceneryLegacyRegions){
        this(seed,scale,legacyRegions,sceneryLegacyRegions,Set.of());
    }
    public BiomeTerrain(long seed,int scale,Set<Long> legacyRegions,Set<Long> sceneryLegacyRegions,Set<Long> detailLegacyRegions){
        this.seed=seed;this.scale=Math.clamp(scale,1536,8192);legacy=Set.copyOf(legacyRegions);
        sceneryLegacy=Set.copyOf(sceneryLegacyRegions);
        detailLegacy=Set.copyOf(detailLegacyRegions);
        broad=new SimplexNoiseGenerator(seed^0x42494f4d45534b59L);
        detail=new SimplexNoiseGenerator(seed^0x414e4349454e544cL);
        warp=new SimplexNoiseGenerator(seed^0x4c554e4152534541L);
    }
    public int scale(){return scale;}
    public Set<Long> legacyRegions(){return legacy;}
    public Set<Long> sceneryLegacyRegions(){return sceneryLegacy;}
    public boolean extraDetails(int x,int z,int radius){
        if(!richScenery(x,z,radius))return false;
        int reach=Math.max(0,radius)+192;
        for(int rx=Math.floorDiv(x-reach,512);rx<=Math.floorDiv(x+reach,512);rx++)
            for(int rz=Math.floorDiv(z-reach,512);rz<=Math.floorDiv(z+reach,512);rz++)
                if(detailLegacy.contains(SkyPlacementHistory.key(rx,rz)))return false;
        return true;
    }
    /** Keep new scenery, including its full canopy, outside pre-update region files. */
    public boolean richScenery(int x,int z,int radius){
        int reach=Math.max(0,radius)+192;
        int[] offsets=radius==0?new int[]{0}:new int[]{-radius,0,radius};
        for(int dx:offsets)for(int dz:offsets)
            if(newWeight(x+dx,z+dz)<.5)return false;
        for(int rx=Math.floorDiv(x-reach,512);rx<=Math.floorDiv(x+reach,512);rx++)
            for(int rz=Math.floorDiv(z-reach,512);rz<=Math.floorDiv(z+reach,512);rz++)
                if(sceneryLegacy.contains(SkyPlacementHistory.key(rx,rz)))return false;
        return true;
    }
    public long hash(int x,int z){return DungeonLayout.mix(seed^((long)x*341873128712L)^((long)z*132897987541L));}
    public double noise(double x,double z,double wavelength){return detail.noise(x/wavelength,z/wavelength);}
    public Center center(int gx,int gz){
        long h=hash(gx,gz);
        int x=gx*scale+(int)(scale*(.28+((h>>>8)&65535)/65535.0*.44));
        int z=gz*scale+(int)(scale*(.28+((h>>>32)&65535)/65535.0*.44));
        // Adjacent cells vary while large connected regions of a kind remain possible.
        Kind kind=Kind.values()[Math.floorMod(gx+gz*3+(int)(seed&3),4)];
        return new Center(kind,x,z);
    }
    private record Region(Kind kind,double[] weights,double border) {}
    private Region region(int x,int z){
        double wx=x+warp.noise(x/1900.0,z/1900.0)*scale*.12;
        double wz=z+warp.noise(x/1900.0+79,z/1900.0-53)*scale*.12;
        int gx=(int)Math.floor(wx/scale),gz=(int)Math.floor(wz/scale);
        Center first=null;double a=Double.POSITIVE_INFINITY,b=a;
        double[] distances=new double[9];Kind[] kinds=new Kind[9];int index=0;
        for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++){
            var c=center(gx+dx,gz+dz);double d=Math.hypot(wx-c.x,wz-c.z);
            distances[index]=d;kinds[index++]=c.kind;
            if(d<a){b=a;first=c;a=d;}else if(d<b)b=d;
        }
        double border=Math.clamp((b-a)/280.0,0,1);
        // Include all nearby regions, especially triple junctions. Selecting only the
        // second-nearest biome would abruptly change height when that neighbor switches.
        double[] weights=new double[4];double total=0;
        for(int i=0;i<9;i++){
            double influence=smooth((a+280-distances[i])/280);
            weights[kinds[i].ordinal()]+=influence;total+=influence;
        }
        for(int i=0;i<4;i++)weights[i]/=total;
        return new Region(first.kind,weights,border);
    }
    /** The garden heartland around spawn: about 700 blocks, warped so its edge is never a visible circle. */
    private boolean heartland(int x,int z){return Math.hypot(x,z)<700+150*warp.noise(x/700.0+3,z/700.0-11);}
    public Kind kind(int x,int z){return heartland(x,z)?Kind.GARDENS:region(x,z).kind;}
    public static double smooth(double t){t=Math.clamp(t,0,1);return t*t*(3-2*t);}
    /** Preserve entire pre-upgrade region files plus a smooth 192-block skirt. Captured only once. */
    public double newWeight(int x,int z){
        double weight=smooth((Math.hypot(x,z)-384)/384);
        if(legacy.isEmpty())return weight;
        int rx=Math.floorDiv(x,512),rz=Math.floorDiv(z,512);
        for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)if(legacy.contains(SkyPlacementHistory.key(rx+dx,rz+dz))){
            int ax=(rx+dx)*512,az=(rz+dz)*512;
            double distance=Math.hypot(Math.max(Math.max(ax-x,0),x-(ax+511)),Math.max(Math.max(az-z,0),z-(az+511)));
            weight=Math.min(weight,smooth(distance/192));
        }
        return weight;
    }
    private double height(Kind kind,int x,int z){
        double n=broad.noise(x/630.0,z/630.0),d=noise(x,z,160);
        return switch(kind){
            case GARDENS -> 66+n*16+d*5;
            case ANCIENT_FOREST -> 70+n*20+d*7;
            case LUNAR_CITY -> 65+n*10+d*3;
            case GLACIER -> {
                double ridges=1-Math.abs(broad.noise(x/440.0+17,z/440.0-31));
                double massif=Math.clamp((n+.55)/1.25,0,1);
                yield 79+Math.pow(ridges,2.3)*105+massif*55+d*13+noise(x,z,49)*4;
            }
        };
    }
    public Sample sample(int x,int z){
        var r=region(x,z);
        double h=0;
        for(var kind:Kind.values())if(r.weights[kind.ordinal()]>0)h+=height(kind,x,z)*r.weights[kind.ordinal()];
        double spawnBlend=smooth((Math.hypot(x,z)-512)/384);
        h=height(Kind.GARDENS,x,z)*(1-spawnBlend)+h*spawnBlend;
        // Long narrow sky fissures; continents cover most of a biome, rather than tiny isolated islands.
        double fault=Math.abs(warp.noise(x/1150.0+211,z/1150.0-97));
        double coast=smooth((fault-.018)/.075);
        boolean land=fault>.018;
        h-=Math.pow(1-coast,2)*20;
        int top=(int)Math.round(Math.clamp(h,35,255));
        int depth=(int)Math.round(28+coast*48+Math.max(0,top-85)*.58+noise(x,z,87)*6);
        return new Sample(heartland(x,z)?Kind.GARDENS:r.kind,land,top,Math.clamp(depth,18,170),r.border);
    }
    public Material material(Kind kind,int x,int y,int z,int top,int depth){
        int down=top-y;long h=hash(x,z);
        return switch(kind){
            case GLACIER -> down==0?Material.SNOW_BLOCK:down<4?Material.PACKED_ICE:
                down<Math.max(18,depth-12)?(Math.floorMod(y+Math.floorDiv(x,7)+Math.floorDiv(z,9),19)<7?Material.BLUE_ICE:Material.PACKED_ICE):
                down<depth-3?Material.DIORITE:Material.CALCITE;
            case ANCIENT_FOREST -> down==0?((h&7)<3?Material.MOSS_BLOCK:(h&15)==9?Material.PODZOL:Material.GRASS_BLOCK):
                down<5?Material.ROOTED_DIRT:down<10?Material.MOSSY_COBBLESTONE:
                Math.floorMod(y+Math.floorDiv(x,9)-Math.floorDiv(z,11),23)<3?Material.TUFF:Material.STONE;
            case LUNAR_CITY -> down==0?((h&15)<3?Material.MOSS_BLOCK:Material.GRASS_BLOCK):down<4?Material.DIRT:
                down<13?Material.CALCITE:Math.floorMod(y+Math.floorDiv(x,7)+Math.floorDiv(z,7),17)<4?Material.AMETHYST_BLOCK:Material.DEEPSLATE;
            case GARDENS -> down==0?Material.GRASS_BLOCK:down<4?Material.DIRT:down<10?Material.CALCITE:Material.STONE;
        };
    }
    /** Search pure coordinates, never generate chunks just to find a destination. */
    public Center locate(Kind target,int x,int z){
        Center best=null;double distance=Double.POSITIVE_INFINITY;
        int gx=Math.floorDiv(x,scale),gz=Math.floorDiv(z,scale);
        for(int dx=-4;dx<=4;dx++)for(int dz=-4;dz<=4;dz++){
            var c=center(gx+dx,gz+dz);if(c.kind!=target)continue;
            // Warp can shift boundaries; validate the destination and its upgrade history.
            for(int ox=-256;ox<=256;ox+=128)for(int oz=-256;oz<=256;oz+=128){
                int px=c.x+ox,pz=c.z+oz;var s=sample(px,pz);
                if(s.kind!=target||!s.land||newWeight(px,pz)<1)continue;
                double d=Math.hypot((double)x-px,(double)z-pz);
                if(d<distance){distance=d;best=new Center(target,px,pz);}
            }
        }
        return best;
    }
}
