package com.example.voidscape.world;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Where the Celestial Cascade stands: at most one per cell of 128 chunks (2048 blocks), on a chunk centre so the
 * blueprint's treasure chunk is fixed. A candidate needs clear sky over its whole footprint (no other sky structure
 * within reach and terrain well below the lowest islet) and dry, free ground for the lift pool beside it.
 * Large layouts are only read here, never the other way round, so there is no dependency cycle.
 */
public final class CelestialCascadeLayout {
    public record Site(int x,int z){
        public boolean contains(int bx,int bz,int margin){
            return Math.abs(bx-x)<=CelestialCascade.RADIUS+margin&&Math.abs(bz-z)<=CelestialCascade.RADIUS+margin;
        }
        public int liftX(){return x+CelestialCascade.LIFT_X;}
        public int liftZ(){return z+CelestialCascade.LIFT_Z;}
        public int poolX(){return x+CelestialCascade.POOL_X;}
        public int poolZ(){return z+CelestialCascade.POOL_Z;}
        /** Within the ground pool, its rim or the lift shaft above it, at any height. */
        public boolean liftColumn(int bx,int bz,int margin){
            int reach=CelestialCascade.POOL_HALF+1+margin;
            return Math.abs(bx-poolX())<=reach&&Math.abs(bz-poolZ())<=reach;
        }
    }
    /** Terrain the structure must clear: the ground top (or MIN_VALUE over the void) and whether a spot is free. */
    public interface Ground{
        int top(int x,int z);
        boolean free(int x,int z,int radius);
    }

    private static final long SALT=0x43454c4553544941L;
    /** Clear air kept between the terrain and the structure's lowest block. */
    private static final int CLEARANCE=28;
    private final long seed;
    private final int spacing;
    private final double chance;
    private final Set<Long> excluded;
    private final SkyWhaleLayout whales;
    private final SkyLandmarkLayout landmarks;
    private final WorldBossTempleLayout temples;
    private final BossStructureLayout bosses;
    private volatile Ground ground;
    private final Map<Long,Optional<Site>> cache=new ConcurrentHashMap<>();

    public CelestialCascadeLayout(long seed,int spacingChunks,double chance,Set<Long> excluded,SkyWhaleLayout whales,
                                  SkyLandmarkLayout landmarks,WorldBossTempleLayout temples,BossStructureLayout bosses){
        this.seed=seed;this.spacing=Math.clamp(spacingChunks,32,256);
        this.chance=Double.isFinite(chance)?Math.clamp(chance,0,1):.6;
        this.excluded=Set.copyOf(excluded);this.whales=whales;this.landmarks=landmarks;this.temples=temples;this.bosses=bosses;
    }
    public int spacingChunks(){return spacing;}
    /** Installed by the world generator before any chunk is generated. */
    public void ground(Ground ground){this.ground=ground;cache.clear();}
    private static long key(int x,int z){return ((long)x<<32)|(z&0xffffffffL);}

    public Site cell(int gx,int gz){
        Ground g=ground;if(g==null)return null;
        if(cache.size()>100_000)cache.clear();
        return cache.computeIfAbsent(key(gx,gz),k->Optional.ofNullable(candidate(gx,gz,g))).orElse(null);
    }
    public Site at(int x,int z,int margin){
        int size=spacing*16;Site site=cell(Math.floorDiv(x,size),Math.floorDiv(z,size));
        return site!=null&&site.contains(x,z,margin)?site:null;
    }
    public Site nearest(int x,int z,int radiusCells){
        int size=spacing*16,gx=Math.floorDiv(x,size),gz=Math.floorDiv(z,size);
        Site best=null;double distance=Double.MAX_VALUE;
        for(int dx=-radiusCells;dx<=radiusCells;dx++)for(int dz=-radiusCells;dz<=radiusCells;dz++){
            Site site=cell(gx+dx,gz+dz);if(site==null)continue;
            double d=Math.hypot(site.x-x,site.z-z);if(d<distance){distance=d;best=site;}
        }
        return best;
    }

    private Site candidate(int gx,int gz,Ground g){
        if(excluded.contains(key(gx,gz)))return null;
        Random random=new Random(DungeonLayout.mix(seed^SALT^gx*341873128712L^gz*132897987541L));
        if(random.nextDouble()>=chance)return null;
        // The whole footprint stays inside the cell, so a chunk only ever looks at its own cell.
        int margin=(CelestialCascade.RADIUS+16)/16+1,span=spacing-2*margin;
        for(int attempt=0;attempt<6;attempt++){
            int x=(gx*spacing+margin+random.nextInt(span))*16+8,z=(gz*spacing+margin+random.nextInt(span))*16+8;
            if(Math.hypot(x,z)<1500||Math.abs(x)>29_999_000||Math.abs(z)>29_999_000)continue;
            if(blocked(x,z)||!clearSky(x,z,g))continue;
            Site site=new Site(x,z);
            int pool=g.top(site.poolX(),site.poolZ());
            if(pool==Integer.MIN_VALUE||pool<40||pool>CelestialCascade.blueprint().lowestY()-CLEARANCE)continue;
            if(!g.free(site.poolX(),site.poolZ(),CelestialCascade.POOL_HALF+3))continue;
            return site;
        }
        return null;
    }
    /** Other sky structures anywhere near the footprint, at any height. */
    private boolean blocked(int x,int z){
        int reach=CelestialCascade.RADIUS+40;
        int[][] probes={{0,0},{reach,0},{-reach,0},{0,reach},{0,-reach},{reach,reach},{-reach,reach},{reach,-reach},{-reach,-reach}};
        for(int[] p:probes){
            int px=x+p[0],pz=z+p[1];
            if(whales!=null&&whales.at(px,pz,40)!=null)return true;
            if(landmarks!=null&&landmarks.at(px,pz,40)!=null)return true;
            if(temples!=null&&temples.at(px,pz,40)!=null)return true;
            if(bosses!=null&&bosses.at(px,pz,40)!=null)return true;
        }
        return false;
    }
    /** Terrain everywhere under the footprint sits well below the structure's lowest block. */
    private boolean clearSky(int x,int z,Ground g){
        int limit=CelestialCascade.blueprint().lowestY()-CLEARANCE;
        int r=CelestialCascade.RADIUS;
        for(int dx=-r;dx<=r;dx+=20)for(int dz=-r;dz<=r;dz+=20){
            int top=g.top(x+dx,z+dz);
            if(top!=Integer.MIN_VALUE&&top>limit)return false;
        }
        return true;
    }
}
