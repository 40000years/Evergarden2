package com.example.voidscape.world;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Adds a matched pair in each eligible whale cell without moving legacy whale sites. */
public final class SkyLandmarkLayout {
    public enum Kind {
        OBSERVATORY("observatory",64,129,-30,14,101,58),
        HANGING_GARDEN("hanging-garden",48,162,-5,0,77,22);
        public final String id;
        public final int radius,chestY,chestZ,arrivalX,arrivalY,arrivalZ;
        Kind(String id,int radius,int chestY,int chestZ,int x,int y,int z){
            this.id=id;this.radius=radius;this.chestY=chestY;this.chestZ=chestZ;
            arrivalX=x;arrivalY=y;arrivalZ=z;
        }
        public LandmarkBlueprint blueprint(){return this==OBSERVATORY?SkyObservatory.blueprint():HangingGarden.blueprint();}
    }
    public record Site(Kind kind,int x,int z) {
        public boolean contains(int bx,int bz,int margin){return Math.abs(bx-x)<=kind.radius+margin&&Math.abs(bz-z)<=kind.radius+margin;}
    }
    private final long seed;
    private final DungeonLayout temples;
    private final SkyWhaleLayout whales;
    private final Map<Long,List<Site>> cache=new ConcurrentHashMap<>();
    public SkyLandmarkLayout(long seed,DungeonLayout temples,SkyWhaleLayout whales){this.seed=seed;this.temples=temples;this.whales=whales;}
    public int spacingChunks(){return whales.spacingChunks();}
    public List<Site> cell(int gx,int gz){
        long key=((long)gx<<32)|(gz&0xffffffffL);
        return cache.computeIfAbsent(key,ignored->candidates(gx,gz));
    }
    private List<Site> candidates(int gx,int gz){
        var whale=whales.cell(gx,gz);if(whale==null)return List.of();
        // The same accepted-cell decision gives all three landmarks equal frequency.
        // Search the cell's free space as a pair: neither new type gets placement priority.
        List<Site> candidates=new ArrayList<>();
        for(int x=5;x<spacingChunks()-5;x++)for(int z=5;z<spacingChunks()-5;z++){
            int bx=(gx*spacingChunks()+x)*16,bz=(gz*spacingChunks()+z)*16;
            if(Math.hypot(bx,bz)<480)continue;
            double wx=Math.max(0,Math.abs(bx-whale.x())-72)/115.0;
            double wz=Math.max(0,Math.abs(bz-whale.z())-72)/65.0;
            if(wx*wx+wz*wz<=1)continue;
            boolean clear=true;
            for(var temple:temples.nearby(bx,bz))
                if(Math.abs(bx-temple.x())<104&&Math.abs(bz-temple.z())<104){clear=false;break;}
            if(clear)candidates.add(new Site(Kind.OBSERVATORY,bx,bz));
        }
        Collections.shuffle(candidates,new Random(DungeonLayout.mix(seed^0x534b59474152444eL^(long)gx*341873128712L^(long)gz*132897987541L)));
        for(Site first:candidates)for(Site second:candidates)
            if(Math.abs(first.x-second.x)>124||Math.abs(first.z-second.z)>124)
                return List.of(first,new Site(Kind.HANGING_GARDEN,second.x,second.z));
        // Extremely crowded cells omit both rather than overlap another structure.
        return List.of();
    }
    public Site at(int x,int z,int margin){
        int size=spacingChunks()*16;
        for(Site site:cell(Math.floorDiv(x,size),Math.floorDiv(z,size)))if(site.contains(x,z,margin))return site;
        return null;
    }
    public Site nearest(Kind kind,int x,int z,int radius){
        int size=spacingChunks()*16,gx=Math.floorDiv(x,size),gz=Math.floorDiv(z,size);
        Site result=null;double best=Double.POSITIVE_INFINITY;
        for(int dx=-radius;dx<=radius;dx++)for(int dz=-radius;dz<=radius;dz++)
            for(Site site:cell(gx+dx,gz+dz))if(site.kind==kind){
                double d=Math.hypot(site.x-x,site.z-z);if(d<best){best=d;result=site;}
            }
        return result;
    }
}
