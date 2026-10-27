package com.example.voidscape.world;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Independent seed rolls on the sky grid; all reservations stay inside their cell. */
public final class BossStructureLayout {
    public enum Kind {
        ARKHAIA("arkhaia-castle", "Arkhaia — Dreadrose Castle", 104, 108, 42, -68, 94, 260, 0x41524b484149414cL),
        VALKYRIE("seraphon-valkyrie", "Seraphon Valkyrie — Temple of Blades", 96, 112, 44, -60, 86, 260, 0x56414c4b59524945L),
        SERAPHON("seraphon-sanctum", "Seraphon — Supreme Sanctuary", 120, 120, 58, -78, 108, 304, 0x5345524150484f4eL);

        public final String id, title;
        public final int radius, floorY, arenaRadius, chestZ, arrivalZ, maxY;
        final long salt;
        Kind(String id,String title,int radius,int floorY,int arenaRadius,int chestZ,int arrivalZ,int maxY,long salt){
            this.id=id;this.title=title;this.radius=radius;this.floorY=floorY;this.arenaRadius=arenaRadius;
            this.chestZ=chestZ;this.arrivalZ=arrivalZ;this.maxY=maxY;this.salt=salt;
        }
        public BossStructure blueprint(){return BossStructure.blueprint(this);}
        public static Kind fromId(String id){
            return switch(id.toLowerCase(Locale.ROOT)){
                case "arkhaia", "arkhaia-castle" -> ARKHAIA;
                case "valkyrie", "seraphon-valkyrie" -> VALKYRIE;
                case "seraphon", "seraphon-sanctum" -> SERAPHON;
                default -> null;
            };
        }
    }
    public record Site(Kind kind,int x,int z,boolean legacyDesign){
        public Site(Kind kind,int x,int z){this(kind,x,z,false);}
        public BossStructure blueprint(){return BossStructure.blueprint(kind,legacyDesign);}
        public boolean contains(int bx,int bz,int margin){
            return Math.abs((long)bx-x)<=kind.radius+margin&&Math.abs((long)bz-z)<=kind.radius+margin;
        }
    }
    private final long seed;
    private final DungeonLayout dungeons;
    private final SkyWhaleLayout whales;
    private final SkyLandmarkLayout landmarks;
    private final WorldBossTempleLayout ancient;
    private final int spacing,gap,marginChunks;
    private final Map<Kind,Double> chances;
    private final Set<Kind> enabled;
    private final Set<Long> excluded;
    private final Set<Long> legacyDesignCells;
    private final Map<Long,List<Site>> cache=new ConcurrentHashMap<>();

    public BossStructureLayout(long seed,DungeonLayout dungeons,SkyWhaleLayout whales,SkyLandmarkLayout landmarks,
            WorldBossTempleLayout ancient,int spacing,int gap,Map<Kind,Double> chances,Set<Kind> enabled,Set<Long> excluded){
        this(seed,dungeons,whales,landmarks,ancient,spacing,gap,chances,enabled,excluded,Set.of());
    }
    public BossStructureLayout(long seed,DungeonLayout dungeons,SkyWhaleLayout whales,SkyLandmarkLayout landmarks,
            WorldBossTempleLayout ancient,int spacing,int gap,Map<Kind,Double> chances,Set<Kind> enabled,Set<Long> excluded,Set<Long> legacyDesignCells){
        this.seed=seed;this.dungeons=dungeons;this.whales=whales;this.landmarks=landmarks;this.ancient=ancient;
        this.spacing=Math.clamp(spacing,32,64);this.gap=Math.clamp(gap,32,128);
        // Even the two largest sites in adjacent cells retain the configured empty gap.
        marginChunks=(int)Math.ceil((120+this.gap/2.0)/16);
        var rates=new EnumMap<Kind,Double>(Kind.class);
        for(var kind:Kind.values()){
            double rate=chances.getOrDefault(kind,.03);
            rates.put(kind,Double.isFinite(rate)?Math.clamp(rate,0,1):0);
        }
        this.chances=Map.copyOf(rates);this.enabled=Set.copyOf(enabled);this.excluded=Set.copyOf(excluded);
        this.legacyDesignCells=Set.copyOf(legacyDesignCells);
        // Every large layout exists now: sanctum dungeons yield to all of them.
        dungeons.reserve(this::reservesDungeon);
    }
    /** A sanctum (radius 28) centred here would touch a boss structure, whale, landmark or ancient temple. */
    private boolean reservesDungeon(int x,int z){
        int margin=DUNGEON_RADIUS+gap;
        if(at(x,z,margin)!=null||ancient.at(x,z,margin)!=null)return true;
        int ws=whales.spacingChunks()*16,ls=landmarks.spacingChunks()*16;
        for(int gx=Math.floorDiv(x,ws)-1;gx<=Math.floorDiv(x,ws)+1;gx++)for(int gz=Math.floorDiv(z,ws)-1;gz<=Math.floorDiv(z,ws)+1;gz++){
            var whale=whales.cell(gx,gz);
            if(whale!=null&&Math.abs((long)x-whale.x())<=115+margin&&Math.abs((long)z-whale.z())<=65+margin)return true;
        }
        for(int gx=Math.floorDiv(x,ls)-1;gx<=Math.floorDiv(x,ls)+1;gx++)for(int gz=Math.floorDiv(z,ls)-1;gz<=Math.floorDiv(z,ls)+1;gz++)
            for(var landmark:landmarks.cell(gx,gz))if(landmark.contains(x,z,margin))return true;
        return false;
    }
    private static final int DUNGEON_RADIUS=28;
    public int spacingChunks(){return spacing;}
    public int minimumGap(){return gap;}
    public double chance(Kind kind){return chances.get(kind);}
    public boolean enabled(Kind kind){return enabled.contains(kind);}
    public List<Site> cell(int gx,int gz){
        return cache.computeIfAbsent(SkyPlacementHistory.key(gx,gz),ignored->compute(gx,gz));
    }
    private List<Site> compute(int gx,int gz){
        if(excluded.contains(SkyPlacementHistory.key(gx,gz)))return List.of();
        var result=new ArrayList<Site>();
        for(var kind:Kind.values()){
            // Disabled builds keep their reserved candidate, so toggles never move another kind.
            var random=new Random(DungeonLayout.mix(seed^kind.salt^((long)gx*341873128712L)^((long)gz*132897987541L)));
            if(random.nextDouble()>=chance(kind))continue;
            int x=(gx*spacing+marginChunks+random.nextInt(spacing-2*marginChunks))*16;
            int z=(gz*spacing+marginChunks+random.nextInt(spacing-2*marginChunks))*16;
            var site=new Site(kind,x,z,legacyDesignCells.contains(SkyPlacementHistory.key(gx,gz)));
            if(Math.hypot(x,z)<768||conflictsWithExisting(site))continue;
            if(result.stream().anyMatch(other->close(site,other.x,other.z,other.kind.radius,other.kind.radius)))continue;
            result.add(site);
        }
        return List.copyOf(result);
    }
    private boolean close(Site site,int x,int z,int radiusX,int radiusZ){
        return Math.abs((long)site.x-x)<=site.kind.radius+radiusX+gap
            &&Math.abs((long)site.z-z)<=site.kind.radius+radiusZ+gap;
    }
    private boolean conflictsWithExisting(Site site){
        // The 28-block building plus the gap also clears its floating shore and trees.
        // Sanctum dungeons give way to boss structures (DungeonLayout.reserve).
int reach=site.kind.radius+120+gap;
        int ws=whales.spacingChunks()*16,ls=landmarks.spacingChunks()*16,as=ancient.spacingChunks()*16;
        for(int gx=Math.floorDiv(site.x-reach,ws);gx<=Math.floorDiv(site.x+reach,ws);gx++)
            for(int gz=Math.floorDiv(site.z-reach,ws);gz<=Math.floorDiv(site.z+reach,ws);gz++){
                var whale=whales.cell(gx,gz);
                if(whale!=null&&close(site,whale.x(),whale.z(),115,65))return true;
            }
        for(int gx=Math.floorDiv(site.x-reach,ls);gx<=Math.floorDiv(site.x+reach,ls);gx++)
            for(int gz=Math.floorDiv(site.z-reach,ls);gz<=Math.floorDiv(site.z+reach,ls);gz++)
                for(var landmark:landmarks.cell(gx,gz))
                    if(close(site,landmark.x(),landmark.z(),landmark.kind().radius,landmark.kind().radius))return true;
        for(int gx=Math.floorDiv(site.x-reach,as);gx<=Math.floorDiv(site.x+reach,as);gx++)
            for(int gz=Math.floorDiv(site.z-reach,as);gz<=Math.floorDiv(site.z+reach,as);gz++){
                var temple=ancient.cell(gx,gz);
                if(temple!=null&&close(site,temple.x(),temple.z(),WorldBossTemple.RADIUS,WorldBossTemple.RADIUS))return true;
            }
        return false;
    }
    public Site at(int x,int z,int margin){
        int size=spacing*16,reach=120+margin;
        for(int gx=Math.floorDiv(x-reach,size);gx<=Math.floorDiv(x+reach,size);gx++)
            for(int gz=Math.floorDiv(z-reach,size);gz<=Math.floorDiv(z+reach,size);gz++)
                for(var site:cell(gx,gz))if(site.contains(x,z,margin))return site;
        return null;
    }
    public Site nearest(Kind kind,int x,int z,int radiusCells){
        return nearest(kind,x,z,radiusCells,false);
    }
    public Site nearest(Kind kind,int x,int z,int radiusCells,boolean latestOnly){
        Site best=null;double distance=Double.POSITIVE_INFINITY;
        int gx=Math.floorDiv(x,spacing*16),gz=Math.floorDiv(z,spacing*16);
        for(int dx=-radiusCells;dx<=radiusCells;dx++)for(int dz=-radiusCells;dz<=radiusCells;dz++)
            for(var site:cell(gx+dx,gz+dz))if(site.kind==kind&&(!latestOnly||!site.legacyDesign)){
                double d=Math.hypot((double)x-site.x,(double)z-site.z);
                if(d<distance){distance=d;best=site;}
            }
        return best;
    }
}
