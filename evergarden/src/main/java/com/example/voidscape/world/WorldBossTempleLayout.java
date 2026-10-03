package com.example.voidscape.world;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Independent Garden-style rarity roll. Existing landmarks retain their exact candidates. */
public final class WorldBossTempleLayout {
    public record Site(int x,int z){
        public boolean contains(int bx,int bz,int margin){return WorldBossTemple.footprint(bx-x,bz-z,margin);}
    }
    private final long seed;
    private final DungeonLayout temples;
    private final SkyWhaleLayout whales;
    private final SkyLandmarkLayout landmarks;
    private final int spacing;
    private final double chance;
    private final Set<Long> explored;
    private final Map<Long,Optional<Site>> cache=new ConcurrentHashMap<>();
    public WorldBossTempleLayout(long seed,DungeonLayout temples,SkyWhaleLayout whales,SkyLandmarkLayout landmarks,
            int spacing,double chance,Set<Long> explored){
        this.seed=seed;this.temples=temples;this.whales=whales;this.landmarks=landmarks;
        this.spacing=Math.clamp(spacing,32,64);this.chance=Double.isFinite(chance)?Math.clamp(chance,0,1):0;
        this.explored=Set.copyOf(explored);
    }
    private static long key(int x,int z){return ((long)x<<32)|(z&0xffffffffL);}
    public int spacingChunks(){return spacing;}
    public double chance(){return chance;}
    public Site cell(int gx,int gz){return cache.computeIfAbsent(key(gx,gz),k->Optional.ofNullable(candidate(gx,gz))).orElse(null);}
    private Site candidate(int gx,int gz){
        if(explored.contains(key(gx,gz)))return null;
        var random=new Random(DungeonLayout.mix(seed^0x57424f5353544d50L^((long)gx*341873128712L)^((long)gz*132897987541L)));
        if(random.nextDouble()>=chance)return null;
        int x=(gx*spacing+8+random.nextInt(spacing-16))*16,z=(gz*spacing+8+random.nextInt(spacing-16))*16;
        if(Math.hypot(x,z)<640)return null;
        for(var temple:temples.nearby(x,z))
            if(Math.abs(x-temple.x())<WorldBossTemple.RADIUS+temple.radius()+40
                &&Math.abs(z-temple.z())<WorldBossTemple.RADIUS+temple.radius()+40)return null;
        // The grids can differ in size; inspect all cells touched by this footprint.
        int ws=whales.spacingChunks()*16,ls=landmarks.spacingChunks()*16;
        for(int a=Math.floorDiv(x-260,ws);a<=Math.floorDiv(x+260,ws);a++)
            for(int b=Math.floorDiv(z-260,ws);b<=Math.floorDiv(z+260,ws);b++){
                var whale=whales.cell(a,b);
                if(whale!=null&&Math.abs(x-whale.x())<WorldBossTemple.RADIUS+115+24
                    &&Math.abs(z-whale.z())<WorldBossTemple.RADIUS+65+24)return null;
            }
        for(int a=Math.floorDiv(x-210,ls);a<=Math.floorDiv(x+210,ls);a++)
            for(int b=Math.floorDiv(z-210,ls);b<=Math.floorDiv(z+210,ls);b++)
                for(var landmark:landmarks.cell(a,b))
                    if(Math.abs(x-landmark.x())<WorldBossTemple.RADIUS+landmark.kind().radius+24
                        &&Math.abs(z-landmark.z())<WorldBossTemple.RADIUS+landmark.kind().radius+24)return null;
        return new Site(x,z);
    }
    public Site at(int x,int z,int margin){
        int size=spacing*16;
        for(int gx=Math.floorDiv(x-margin,size);gx<=Math.floorDiv(x+margin,size);gx++)
            for(int gz=Math.floorDiv(z-margin,size);gz<=Math.floorDiv(z+margin,size);gz++){
                var site=cell(gx,gz);if(site!=null&&site.contains(x,z,margin))return site;
            }
        return null;
    }
    public Site nearest(int x,int z,int radiusCells){
        int gx=Math.floorDiv(x,spacing*16),gz=Math.floorDiv(z,spacing*16);
        Site result=null;double best=Double.POSITIVE_INFINITY;
        for(int dx=-radiusCells;dx<=radiusCells;dx++)for(int dz=-radiusCells;dz<=radiusCells;dz++){
            var site=cell(gx+dx,gz+dz);if(site==null)continue;
            double d=Math.hypot((double)x-site.x,(double)z-site.z);if(d<best){best=d;result=site;}
        }
        return result;
    }
}
