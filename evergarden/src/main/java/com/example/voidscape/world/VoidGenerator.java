package com.example.voidscape.world;

import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.generator.*;
import org.bukkit.util.noise.SimplexNoiseGenerator;
import java.util.*;

/** Seed-stable floating gardens; decorations are clipped into their own chunk. */
public final class VoidGenerator extends ChunkGenerator {
    private final long seed;
    private final DungeonLayout layout;
    private final boolean skyWhaleEnabled;
    private final SkyWhaleLayout skyWhales;
    private final SkyLandmarkLayout landmarks;
    private final boolean observatoryEnabled,gardenEnabled;
    private final RestorationLayout restoration;
    private final SimplexNoiseGenerator islands, detail;
    private final Blueprint[] sanctums={Blueprint.sanctumDark(),Blueprint.sanctumAstral(),Blueprint.sanctumTime()};
    public record Surface(boolean land,int top,int depth,int garden,boolean pond,boolean path) {}
    public VoidGenerator(long seed,DungeonLayout layout) { this(seed,layout,true); }
    public VoidGenerator(long seed,DungeonLayout layout,boolean skyWhaleEnabled) {
        this(seed,layout,new SkyWhaleLayout(seed,layout,32,.12),skyWhaleEnabled);
    }
    public VoidGenerator(long seed,DungeonLayout layout,SkyWhaleLayout skyWhales,boolean skyWhaleEnabled) {
        this(seed,layout,skyWhales,skyWhaleEnabled,new SkyLandmarkLayout(seed,layout,skyWhales),false,false);
    }
    public VoidGenerator(long seed,DungeonLayout layout,SkyWhaleLayout skyWhales,boolean skyWhaleEnabled,
                         SkyLandmarkLayout landmarks,boolean observatoryEnabled,boolean gardenEnabled) {
        this(seed,layout,skyWhales,skyWhaleEnabled,landmarks,observatoryEnabled,gardenEnabled,null);
    }
    public VoidGenerator(long seed,DungeonLayout layout,SkyWhaleLayout skyWhales,boolean skyWhaleEnabled,
                         SkyLandmarkLayout landmarks,boolean observatoryEnabled,boolean gardenEnabled,RestorationLayout restoration) {
        this.seed=seed;this.layout=layout;this.skyWhales=skyWhales;this.skyWhaleEnabled=skyWhaleEnabled;
        this.landmarks=landmarks;this.observatoryEnabled=observatoryEnabled;this.gardenEnabled=gardenEnabled;
        this.restoration=restoration;
        islands=new SimplexNoiseGenerator(seed);detail=new SimplexNoiseGenerator(seed^721945L);
    }
    private boolean landmarkEnabled(SkyLandmarkLayout.Site site){
        return site!=null&&(site.kind()==SkyLandmarkLayout.Kind.OBSERVATORY?observatoryEnabled:gardenEnabled);
    }
    private boolean landmarkReserved(int x,int z,int margin){
        return (observatoryEnabled||gardenEnabled)&&landmarkEnabled(landmarks.at(x,z,margin));
    }
    private static double smooth(double t){t=Math.clamp(t,0,1);return t*t*(3-2*t);}
    private long hash(int x,int z){return DungeonLayout.mix(seed^(long)x*341873128712L^(long)z*132897987541L);}
    public Surface surface(int x,int z){return surface(x,z,layout.nearby(x,z));}
    private Surface surface(int x,int z,List<DungeonLayout.Site> sites) {
        // The landmark supplies its own islands and empty spaces. Noise terrain here
        // would fill its rib cage, bury the approach, and spoil the floating silhouette.
        if((skyWhaleEnabled&&skyWhales.at(x,z,0)!=null)||landmarkReserved(x,z,0))
            return new Surface(false,95,0,garden(x,z),false,false);
        double radial=Math.hypot(x,z);
        double density=islands.noise(x/155.0,z/155.0)+0.18*detail.noise(x/49.0,z/49.0);
        double strength=Math.max(density-0.02,1-radial/(116+10*detail.noise(x/60.0,z/60.0)));
        int top=93+(int)Math.round(detail.noise(x/120.0,z/120.0)*8);
        if(radial<135)top=(int)Math.round(95+(top-95)*smooth((radial-72)/63));
        double siteWeight=0;
        for(var site:sites) {
            double distance=Math.max(Math.abs(x-site.x()),Math.abs(z-site.z()));
            double shore=Math.hypot(x-site.x(),z-site.z());
            strength=Math.max(strength,(70-shore+detail.noise(x/35.0,z/35.0)*7)/90);
            siteWeight=Math.max(siteWeight,1-smooth((distance-29)/30));
        }
        top=(int)Math.round(top+(95-top)*siteWeight);
        boolean path=radial<120&&(Math.abs(x)<=2||Math.abs(z)<=2||Math.abs(radial-29)<2.4);
        // Causeways end at the temple gateways. The first expedition needs no Elytra.
        for(var site:DungeonLayout.STARTER_SITES) {
            int endZ=site.z()+(site.z()<0?28:-28);
            double len=(double)site.x()*site.x()+(double)endZ*endZ;
            double t=Math.clamp((x*(double)site.x()+z*(double)endZ)/len,0,1);
            if(Math.hypot(x-t*site.x(),z-t*endZ)<3.2){path=true;strength=Math.max(strength,0.055);top=95;}
        }
        int garden=garden(x,z);
        boolean pond=siteWeight==0&&!path&&(ellipse(x,z,43,33,13,9)<1||ellipse(x,z,-48,-35,10,15)<1);
        if(pond)top=95;
        int depth=Math.max(5,(int)(12+Math.max(0,strength)*53+detail.noise(x/26.0,z/26.0)*4));
        return new Surface(strength>0,top,depth,garden,pond,path);
    }
    private static double ellipse(int x,int z,int cx,int cz,int rx,int rz){return Math.pow((x-cx)/(double)rx,2)+Math.pow((z-cz)/(double)rz,2);}
    private int garden(int x,int z){return Math.floorMod((int)Math.floor((islands.noise(x/380.0+50,z/380.0-20)+1)*2.5),3);}
    @Override public BiomeProvider getDefaultBiomeProvider(WorldInfo info) {
        return new BiomeProvider() {
            @Override public Biome getBiome(WorldInfo w,int x,int y,int z){return switch(garden(x,z)){case 0->Biome.CHERRY_GROVE;case 1->Biome.FLOWER_FOREST;default->Biome.MEADOW;};}
            @Override public List<Biome> getBiomes(WorldInfo w){return List.of(Biome.CHERRY_GROVE,Biome.FLOWER_FOREST,Biome.MEADOW);}
        };
    }
    @Override public void generateNoise(WorldInfo info,Random ignored,int cx,int cz,ChunkData data) {
        List<DungeonLayout.Site> sites=layout.nearby(cx*16+8,cz*16+8);
        for(int x=0;x<16;x++)for(int z=0;z<16;z++) {
            int wx=cx*16+x,wz=cz*16+z;Surface s=surface(wx,wz,sites);if(!s.land())continue;
            for(int y=Math.max(data.getMinHeight(),s.top()-s.depth());y<=s.top();y++) {
                int down=s.top()-y;
                Material m=down==0?Material.GRASS_BLOCK:down<4?Material.DIRT:down<9?Material.CALCITE:Material.DEEPSLATE;
                if(down>=9&&Math.floorMod(y+Math.floorDiv(wx,5)+Math.floorDiv(wz,5),13)<2)m=Material.TUFF;
                if(down==s.depth()&&(hash(wx,wz)&31)==0)m=Material.SEA_LANTERN;
                data.setBlock(x,y,z,m);
            }
            if(s.pond()) {
                data.setBlock(x,92,z,Material.CLAY);
                for(int y=93;y<=95;y++)data.setBlock(x,y,z,Material.WATER);
                if((hash(wx,wz)&15)==0)data.setBlock(x,92,z,Material.SEA_LANTERN);
                if((hash(wx,wz)&31)==1)data.setBlock(x,96,z,Material.LILY_PAD);
            } else if(s.path())data.setBlock(x,s.top(),z,Math.floorMod(wx+wz,13)==0?Material.SEA_LANTERN:Material.SMOOTH_QUARTZ);
            else if(Math.hypot(wx,wz)>13&&sites.stream().noneMatch(site->site.contains(wx,wz,8))) {
                long h=hash(wx,wz);
                if((h&31)==0) {
                    Material[] flowers={Material.ALLIUM,Material.AZURE_BLUET,Material.WHITE_TULIP,Material.PINK_TULIP,Material.BLUE_ORCHID,Material.OXEYE_DAISY};
                    data.setBlock(x,s.top()+1,z,flowers[Math.floorMod((int)(h>>>8),flowers.length)]);
                } else if((h&31)==1)data.setBlock(x,s.top()+1,z,Material.SHORT_GRASS);
            }
        }
        // Neighboring candidate cells are evaluated identically regardless of generation order.
        for(int gx=Math.floorDiv(cx*16-7,19);gx<=Math.floorDiv(cx*16+22,19);gx++)for(int gz=Math.floorDiv(cz*16-7,19);gz<=Math.floorDiv(cz*16+22,19);gz++) {
            long h=hash(gx*19,gz*19);int tx=gx*19+4+Math.floorMod((int)h,11),tz=gz*19+4+Math.floorMod((int)(h>>>20),11);
            // A neighboring tree can reach across the reservation and across chunks.
            if(skyWhaleEnabled&&skyWhales.at(tx,tz,6)!=null)continue;
            if(landmarkReserved(tx,tz,6))continue;
            Surface s=surface(tx,tz);
            if(!s.land()||s.pond()||s.path()||s.depth()<16||Math.hypot(tx,tz)<19||layout.at(tx,tz,13)!=null)continue;
            // Broad, deliberately empty home sites.
            if(ellipse(tx,tz,55,-33,25,22)<1||ellipse(tx,tz,-48,36,27,22)<1)continue;
            boolean clear=true;
            for(int dx:new int[]{-5,0,5})for(int dz:new int[]{-5,0,5}) {
                Surface edge=surface(tx+dx,tz+dz);
                if(!edge.land()||edge.path()||edge.pond()||Math.abs(edge.top()-s.top())>2)clear=false;
            }
            if(!clear)continue;
            if((h&7)<5)tree(data,cx,cz,tx,s.top()+1,tz,s.garden(),h);
            else if((h&7)==5)crystal(data,cx,cz,tx,s.top()+1,tz);
            else if((h&7)==6)ruin(data,cx,cz,tx,s.top()+1,tz);
        }
        for(int x=-12;x<=12;x++)for(int z=-12;z<=12;z++) {
            double r=Math.hypot(x,z);if(r>12)continue;
            put(data,cx,cz,x,96,z,r>10?Material.CHISELED_QUARTZ_BLOCK:Material.SMOOTH_QUARTZ);
            if(r>10&&Math.abs(x)>3&&Math.abs(z)>3&&Math.floorMod(x+z,7)==0)put(data,cx,cz,x,97,z,Material.SEA_LANTERN);
        }
        put(data,cx,cz,0,96,0,Material.SEA_LANTERN);put(data,cx,cz,0,97,4,Material.LECTERN);
        for(int x=-1;x<=2;x++)for(int y=96;y<=100;y++)put(data,cx,cz,x,y,-5,(x==-1||x==2||y==96||y==100)?Material.QUARTZ_BLOCK:Material.STRUCTURE_VOID);
        for(var site:sites)if(site.contains(cx*16+8,cz*16+8,12))sanctums[site.kind().ordinal()].render(data,cx,cz,site);
        if(skyWhaleEnabled){
            var whale=skyWhales.cell(Math.floorDiv(cx,skyWhales.spacingChunks()),
                    Math.floorDiv(cz,skyWhales.spacingChunks()));
            if(whale!=null)SkyWhale.render(data,cx,cz,whale);
        }
        if(observatoryEnabled||gardenEnabled)
            for(var site:landmarks.cell(Math.floorDiv(cx,landmarks.spacingChunks()),Math.floorDiv(cz,landmarks.spacingChunks())))
                if(landmarkEnabled(site))site.kind().blueprint().render(data,cx,cz,site.x(),site.z());
        if(restoration!=null)for(var site:restoration.cell(Math.floorDiv(cx*16,restoration.cellSize()),Math.floorDiv(cz*16,restoration.cellSize())))
            RestorationShrine.render(data,cx,cz,site);
    }
    private static void put(ChunkData data,int cx,int cz,int x,int y,int z,Material m) {
        int lx=x-cx*16,lz=z-cz*16;
        if(lx>=0&&lx<16&&lz>=0&&lz<16&&y>=data.getMinHeight()&&y<data.getMaxHeight())data.setBlock(lx,y,lz,m);
    }
    private static void leaf(ChunkData data,int cx,int cz,int x,int y,int z,Material m) {
        int lx=x-cx*16,lz=z-cz*16;if(lx<0||lx>=16||lz<0||lz>=16)return;
        Leaves leaves=(Leaves)m.createBlockData();leaves.setPersistent(true);data.setBlock(lx,y,lz,leaves);
    }
    private static void tree(ChunkData d,int cx,int cz,int x,int y,int z,int garden,long h) {
        int height=5+Math.floorMod((int)(h>>>32),3);
        Material leaves=garden==0?Material.CHERRY_LEAVES:garden==1?Material.FLOWERING_AZALEA_LEAVES:Material.OAK_LEAVES;
        Material log=garden==0?Material.CHERRY_LOG:Material.OAK_LOG;
        for(int dy=height-2;dy<=height+1;dy++)for(int dx=-4;dx<=4;dx++)for(int dz=-4;dz<=4;dz++) {
            double radius=dy==height+1?2.5:dy==height-2?3.4:4.5;
            if(dx*dx+dz*dz<=radius*radius)leaf(d,cx,cz,x+dx,y+dy,z+dz,leaves);
        }
        for(int dy=0;dy<height;dy++)put(d,cx,cz,x,y+dy,z,log);
        for(int dx:new int[]{-2,2}){put(d,cx,cz,x+dx,y+height-3,z,Material.IRON_CHAIN);put(d,cx,cz,x+dx,y+height-4,z,Material.PEARLESCENT_FROGLIGHT);}
    }
    private static void crystal(ChunkData d,int cx,int cz,int x,int y,int z) {
        for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++)if(dx*dx+dz*dz<=5) {
            int height=dx==0&&dz==0?7:Math.abs(dx)+Math.abs(dz)<3?3:1;
            for(int dy=0;dy<height;dy++)put(d,cx,cz,x+dx,y+dy,z+dz,dy==0?Material.CALCITE:dy==height-1?Material.AMETHYST_BLOCK:Material.PURPLE_STAINED_GLASS);
        }
        put(d,cx,cz,x,y+2,z,Material.SEA_LANTERN);
    }
    private static void ruin(ChunkData d,int cx,int cz,int x,int y,int z) {
        for(int dx=-3;dx<=3;dx++)put(d,cx,cz,x+dx,y-1,z,Material.MOSSY_STONE_BRICKS);
        for(int dy=0;dy<5;dy++){put(d,cx,cz,x-3,y+dy,z,Material.DEEPSLATE_TILE_WALL);put(d,cx,cz,x+3,y+dy,z,Material.DEEPSLATE_TILE_WALL);}
        for(int dx=-3;dx<=3;dx++)put(d,cx,cz,x+dx,y+5,z,Material.CHISELED_QUARTZ_BLOCK);
        put(d,cx,cz,x,y+4,z,Material.SEA_LANTERN);put(d,cx,cz,x,y+8,z,Material.CRYING_OBSIDIAN);
    }
    @Override public boolean shouldGenerateNoise(){return false;}
    @Override public boolean shouldGenerateSurface(){return false;}
    @Override public boolean shouldGenerateBedrock(){return false;}
    @Override public boolean shouldGenerateCaves(){return false;}
    @Override public boolean shouldGenerateDecorations(){return false;}
    @Override public boolean shouldGenerateMobs(){return false;}
    @Override public boolean shouldGenerateStructures(){return false;}
}
