package com.example.voidscape.world;

import java.util.*;

/** Immutable generation settings; old cells never receive new builds or rerolled sites. */
public final class RestorationLayout {
    public enum Theme { WHALE, GARDEN, OBSERVATORY }
    public record Site(Theme theme,int x,int y,int z,boolean altar) {}
    private final long seed;
    private final SkyWhaleLayout whales;
    private final SkyLandmarkLayout landmarks;
    private final Set<Long> oldCells;
    private final double chance;
    private final boolean whaleEnabled,gardenEnabled,observatoryEnabled;
    public RestorationLayout(long seed,SkyWhaleLayout whales,SkyLandmarkLayout landmarks,Set<Long> oldCells,
                             double chance,boolean whaleEnabled,boolean gardenEnabled,boolean observatoryEnabled){
        this.seed=seed;this.whales=whales;this.landmarks=landmarks;this.oldCells=Set.copyOf(oldCells);
        this.chance=Double.isFinite(chance)?Math.clamp(chance,0,1):.6;
        this.whaleEnabled=whaleEnabled;this.gardenEnabled=gardenEnabled;this.observatoryEnabled=observatoryEnabled;
    }
    public int cellSize(){return whales.spacingChunks()*16;}
    public boolean fresh(int x,int z){return !oldCells.contains(((long)Math.floorDiv(x,cellSize())<<32)|(Math.floorDiv(z,cellSize())&0xffffffffL));}
    private Site site(Theme theme,int x,int y,int z){
        long salt=DungeonLayout.mix(seed^((long)x*341873128712L)^((long)z*132897987541L)^0x524550414952L^theme.ordinal());
        return new Site(theme,x,y,z,new Random(salt).nextDouble()<chance);
    }
    public List<Site> cell(int gx,int gz){
        if(!fresh(gx*cellSize(),gz*cellSize()))return List.of();
        List<Site> result=new ArrayList<>();
        if(whaleEnabled){var whale=whales.cell(gx,gz);if(whale!=null)result.add(site(Theme.WHALE,whale.x()-48,114,whale.z()-22));}
        for(var landmark:landmarks.cell(gx,gz)){
            if(landmark.kind()==SkyLandmarkLayout.Kind.HANGING_GARDEN&&gardenEnabled)
                result.add(site(Theme.GARDEN,landmark.x(),76,landmark.z()+5));
            if(landmark.kind()==SkyLandmarkLayout.Kind.OBSERVATORY&&observatoryEnabled)
                result.add(site(Theme.OBSERVATORY,landmark.x(),128,landmark.z()-18));
        }
        return List.copyOf(result);
    }
    public Site near(int x,int y,int z,int radius){
        for(Site site:cell(Math.floorDiv(x,cellSize()),Math.floorDiv(z,cellSize())))
            if(Math.abs(x-site.x())<=radius&&Math.abs(z-site.z())<=radius&&Math.abs(y-site.y()-1)<=radius)return site;
        return null;
    }
    public Site nearestAltar(int x,int z,int radius){
        Site best=null;double distance=Double.POSITIVE_INFINITY;
        int gx=Math.floorDiv(x,cellSize()),gz=Math.floorDiv(z,cellSize());
        for(int dx=-radius;dx<=radius;dx++)for(int dz=-radius;dz<=radius;dz++)for(Site site:cell(gx+dx,gz+dz)){
            if(!site.altar())continue;
            double d=Math.hypot((double)x-site.x(),(double)z-site.z());if(d<distance){distance=d;best=site;}
        }
        return best;
    }
}
