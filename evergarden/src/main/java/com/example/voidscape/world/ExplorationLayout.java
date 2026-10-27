package com.example.voidscape.world;

import java.util.*;

/** Small destinations on a separate grid; placement never loads chunks or rerolls on load. */
public final class ExplorationLayout {
    public static final int CELL_SIZE=256,RADIUS=30;
    public enum Kind {
        BLOSSOM_PAVILION("blossom-pavilion","ศาลาชมดอกไม้"),
        GARDENER_COTTAGE("gardener-cottage","กระท่อมคนสวน"),
        SPRING_WELL("spring-well","บ่อน้ำสวนเก่า"),
        EXPEDITION_CAMP("expedition-camp","ค่ายสำรวจธารน้ำแข็ง"),
        FROST_CHAPEL("frost-chapel","โบสถ์น้ำแข็ง"),
        ICE_WATCH("ice-watch","หอเฝ้าธารน้ำแข็ง"),
        ROOT_LODGE("root-lodge","บ้านใต้รากไม้"),
        MOSS_RUINS("moss-ruins","ซากลานมอส"),
        HERBALIST_HUT("herbalist-hut","กระท่อมนักสมุนไพร"),
        CRESCENT_ARCHIVE("crescent-archive","หอจดหมายเหตุจันทร์เสี้ยว"),
        STAR_GAZE("star-gaze","ลานดูดาว"),
        MOON_GATE("moon-gate","ประตูจันทราโบราณ"),
        ORCHARD_ESTATE("orchard-estate","เรือนสวนผลไม้"),
        ROSE_CONSERVATORY("rose-conservatory","เรือนกระจกกุหลาบ"),
        GARDEN_WATERMILL("garden-watermill","โรงสีริมน้ำ"),
        TRAVELERS_INN("travelers-inn","โรงแรมนักเดินทาง"),
        HEDGE_SANCTUARY("hedge-sanctuary","สวนเขาวงกต"),
        ALPINE_LODGE("alpine-lodge","ชาเลต์ธารน้ำแข็ง"),
        FROZEN_BATHHOUSE("frozen-bathhouse","โรงอาบน้ำหิมะ"),
        ICEBOUND_OUTPOST("icebound-outpost","ป้อมสะพานน้ำแข็ง"),
        GLACIER_MINE("glacier-mine","เหมืองธารน้ำแข็ง"),
        AURORA_HALL("aurora-hall","โถงแสงเหนือ"),
        WOODLAND_MANOR("woodland-manor","คฤหาสน์ในป่า"),
        CANOPY_REFUGE("canopy-refuge","หมู่เรือนบนยอดไม้"),
        MUSHROOM_HAMLET("mushroom-hamlet","หมู่บ้านเห็ด"),
        OVERGROWN_AQUEDUCT("overgrown-aqueduct","ทางส่งน้ำป่าโบราณ"),
        ROOTBOUND_SHRINE("rootbound-shrine","ศาลเจ้ารากไม้"),
        MOON_MARKET("moon-market","ตลาดจันทรา"),
        ASTRAL_MONASTERY("astral-monastery","อารามดวงดาว"),
        TIDE_OBSERVATORY("tide-observatory","หอดูดาวจันทรา"),
        FALLEN_AMPHITHEATRE("fallen-amphitheatre","โรงละครจันทราเก่า"),
        LUNAR_BATH_GARDEN("lunar-bath-garden","สวนสระจันทรา");
        public final String id,title;
        Kind(String id,String title){this.id=id;this.title=title;}
        public BiomeTerrain.Kind biome(){return BiomeTerrain.Kind.values()[ordinal()<12?ordinal()/3:(ordinal()-12)/5];}
        public boolean medium(){return ordinal()>=12;}
        public int radius(){return medium()?30:18;}
        public int height(){return medium()?42:27;}
        public static List<Kind> inBiome(BiomeTerrain.Kind biome){return Arrays.stream(values()).filter(k->k.biome()==biome).toList();}
        public static Kind fromId(String id){for(var kind:values())if(kind.id.equalsIgnoreCase(id))return kind;return null;}
    }
    public record Site(Kind kind,int x,int floorY,int z,int rotation,long variant,boolean legacyDesign,int designVersion) {
        public Site(Kind kind,int x,int floorY,int z,int rotation,long variant){this(kind,x,floorY,z,rotation,variant,false,5);}
        public Site(Kind kind,int x,int floorY,int z,int rotation,long variant,boolean legacyDesign){this(kind,x,floorY,z,rotation,variant,legacyDesign,legacyDesign?1:5);}
        public int radius(){return kind.radius();}
        public int entryZ(){return kind.medium()?18:9;}
        public int rampZ(){return kind.medium()?20:11;}
        public int[] transform(int dx,int dz){return switch(rotation){
            case 1->new int[]{x-dz,z+dx};case 2->new int[]{x-dx,z-dz};
            case 3->new int[]{x+dz,z-dx};default->new int[]{x+dx,z+dz};};}
        public List<ExplorationBlueprint.Point> chestLocations(){return ExplorationBlueprint.chests(kind,designVersion);}
        public int[] chest(){var spot=chestLocations().getFirst();return transform(spot.x(),spot.z());}
        public int chestY(){return floorY+chestLocations().getFirst().y();}
    }
    public enum PlacementResult { DISABLED, CHANCE, LEGACY_SCENERY, RESERVED_AREA, UNSAFE_CENTER, UNSAFE_FOOTPRINT, BIOME_EDGE, ACCEPTED }
    public record Inspection(PlacementResult result,Site site){}
    private final VoidGenerator generator;
    private final BiomeTerrain terrain;
    private final boolean enabled;
    private final double chance;
    private final Set<Long> legacyCells,previousCells,detailedCells,mediumCells;
    private final Map<Long,Optional<Site>> cache=new LinkedHashMap<>(64,.75f,true);
    public ExplorationLayout(VoidGenerator generator,BiomeTerrain terrain,boolean enabled,double chance){
        this(generator,terrain,enabled,chance,Set.of());
    }
    public ExplorationLayout(VoidGenerator generator,BiomeTerrain terrain,boolean enabled,double chance,Set<Long> legacyCells){
        this(generator,terrain,enabled,chance,legacyCells,Set.of());
    }
    public ExplorationLayout(VoidGenerator generator,BiomeTerrain terrain,boolean enabled,double chance,Set<Long> legacyCells,Set<Long> previousCells){
        this(generator,terrain,enabled,chance,legacyCells,previousCells,Set.of());
    }
    public ExplorationLayout(VoidGenerator generator,BiomeTerrain terrain,boolean enabled,double chance,Set<Long> legacyCells,Set<Long> previousCells,Set<Long> detailedCells){
        this(generator,terrain,enabled,chance,legacyCells,previousCells,detailedCells,Set.of());
    }
    public ExplorationLayout(VoidGenerator generator,BiomeTerrain terrain,boolean enabled,double chance,Set<Long> legacyCells,Set<Long> previousCells,Set<Long> detailedCells,Set<Long> mediumCells){
        this.generator=generator;this.terrain=terrain;this.enabled=enabled;
        this.legacyCells=Set.copyOf(legacyCells);
        this.previousCells=Set.copyOf(previousCells);
        this.detailedCells=Set.copyOf(detailedCells);
        this.mediumCells=Set.copyOf(mediumCells);
        this.chance=Double.isFinite(chance)?Math.clamp(chance,0,1):.35;
    }
    public boolean enabled(){return enabled;}
    public double chance(){return chance;}
    public synchronized Site cell(int gx,int gz){
        if(!enabled||terrain==null)return null;
        long key=SkyPlacementHistory.key(gx,gz);var found=cache.get(key);
        if(found!=null)return found.orElse(null);
        var value=Optional.ofNullable(candidate(gx,gz));
        if(cache.size()>=1024)cache.remove(cache.keySet().iterator().next());
        cache.put(key,value);return value.orElse(null);
    }
    private Site candidate(int gx,int gz){
        return inspectCell(gx,gz).site();
    }
    /** Runs the exact production placement checks without loading or creating any chunks. */
    public Inspection inspectCell(int gx,int gz){
        if(!enabled||terrain==null)return new Inspection(PlacementResult.DISABLED,null);
        long h=terrain.hash(gx*CELL_SIZE+37,gz*CELL_SIZE-91)^0x4558504c4f52454cL;
        var random=new Random(DungeonLayout.mix(h));if(random.nextDouble()>=chance)return new Inspection(PlacementResult.CHANCE,null);
        long key=SkyPlacementHistory.key(gx,gz);boolean legacy=legacyCells.contains(key);
        int version=legacy?1:previousCells.contains(key)?2:detailedCells.contains(key)?3:mediumCells.contains(key)?4:5;
        Inspection failure=null;
        for(int attempt=0;attempt<ATTEMPTS;attempt++){
            var inspection=attempt(random,gx,gz,h,legacy,version);
            if(inspection.result()==PlacementResult.ACCEPTED)return inspection;
            failure=inspection;
        }
        return failure;
    }
    /** Candidate spots per cell; the first acceptable one wins. Rugged glacier cells need the extra tries. */
    private static final int ATTEMPTS=4;
    private Inspection attempt(Random random,int gx,int gz,long h,boolean legacy,int version){
        int x=gx*CELL_SIZE+48+random.nextInt(160),z=gz*CELL_SIZE+48+random.nextInt(160);
        var biome=terrain.kind(x,z);Kind kind=null;int radius=18;
        if(version>=4){var pool=Kind.inBiome(biome);kind=pool.get(random.nextInt(pool.size()));radius=kind.radius();}
        if(!terrain.richScenery(x,z,radius+8))return new Inspection(PlacementResult.LEGACY_SCENERY,null);
        if(!generator.canDecorateTerrain(x,z,radius+8))return new Inspection(PlacementResult.RESERVED_AREA,null);
        var center=generator.surface(x,z);if(!center.land()||center.depth()<22||center.top()>238)return new Inspection(PlacementResult.UNSAFE_CENTER,null);
        for(int dx:new int[]{-radius,0,radius})for(int dz:new int[]{-radius,0,radius}){
            var s=generator.surface(x+dx,z+dz);
            if(!s.land()||s.path()||s.pond()||Math.abs(s.top()-center.top())>(radius==30?8:6)+(biome==BiomeTerrain.Kind.GLACIER?3:0))return new Inspection(PlacementResult.UNSAFE_FOOTPRINT,null);
            if(terrain.kind(x+dx,z+dz)!=biome)return new Inspection(PlacementResult.BIOME_EDGE,null);
        }
        if(kind==null)kind=Kind.values()[biome.ordinal()*3+random.nextInt(3)];
        return new Inspection(PlacementResult.ACCEPTED,new Site(kind,x,center.top()+1,z,random.nextInt(4),h,legacy,version));
    }
    public List<Site> nearby(int x,int z,int radius){
        if(!enabled)return List.of();int reach=RADIUS+Math.max(0,radius);var result=new ArrayList<Site>();
        for(int gx=Math.floorDiv(x-reach,CELL_SIZE);gx<=Math.floorDiv(x+reach,CELL_SIZE);gx++)
            for(int gz=Math.floorDiv(z-reach,CELL_SIZE);gz<=Math.floorDiv(z+reach,CELL_SIZE);gz++){
                var s=cell(gx,gz);if(s!=null&&Math.abs((long)x-s.x)<=s.radius()+Math.max(0,radius)&&Math.abs((long)z-s.z)<=s.radius()+Math.max(0,radius))result.add(s);
            }
        return List.copyOf(result);
    }
    public boolean reserved(int x,int z,int radius){return !nearby(x,z,radius).isEmpty();}
    public Site nearest(Kind kind,int x,int z,int cells){
        return nearest(kind,x,z,cells,false);
    }
    public Site nearest(Kind kind,int x,int z,int cells,boolean latestOnly){
        Site best=null;double distance=Double.POSITIVE_INFINITY;
        int gx=Math.floorDiv(x,CELL_SIZE),gz=Math.floorDiv(z,CELL_SIZE);
        for(int dx=-cells;dx<=cells;dx++)for(int dz=-cells;dz<=cells;dz++){
            var s=cell(gx+dx,gz+dz);if(s==null||s.kind!=kind||latestOnly&&s.designVersion<5)continue;
            double d=Math.hypot((double)x-s.x,(double)z-s.z);if(d<distance){distance=d;best=s;}
        }
        return best;
    }
}
