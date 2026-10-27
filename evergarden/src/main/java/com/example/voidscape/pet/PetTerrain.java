package com.example.voidscape.pet;

import com.example.voidscape.VoidscapePlugin;
import org.bukkit.*;
import org.bukkit.block.Block;
import java.util.EnumSet;
import java.util.Set;

/**
 * Footing and airspace rules shared by every pet body. Wild encounters keep Evergarden's
 * habitat limits (outside the spawn island, sanctums and protected structures); companions
 * only need safe, loaded ground wherever their owner goes. Reads only loaded chunks.
 */
final class PetTerrain {
    static final Set<Material> HAZARDS=EnumSet.of(Material.MAGMA_BLOCK,Material.CACTUS,
        Material.CAMPFIRE,Material.SOUL_CAMPFIRE,Material.FIRE,Material.SOUL_FIRE,
        Material.SWEET_BERRY_BUSH,Material.POWDER_SNOW,Material.WITHER_ROSE,Material.LAVA,
        Material.WATER,Material.POINTED_DRIPSTONE);
    static final int HEADROOM=2;
    private final VoidscapePlugin plugin;
    PetTerrain(VoidscapePlugin plugin){this.plugin=plugin;}

    // Measured from the models: adults are at most 2.6 blocks wide and 1.7 tall.
    static int radius(PetCatalog.Stage stage){return stage==PetCatalog.Stage.BABY?0:1;}
    /** The 5x5 footprint touches at most four chunks: check its corners, not all 25 blocks. */
    static boolean loadedFootprint(World world,int x,int z){
        return world.isChunkLoaded((x-2)>>4,(z-2)>>4)&&world.isChunkLoaded((x+2)>>4,(z-2)>>4)
            &&world.isChunkLoaded((x-2)>>4,(z+2)>>4)&&world.isChunkLoaded((x+2)>>4,(z+2)>>4);
    }
    /** Shared world/region rules for both standing and flying positions. */
    boolean openArea(Location at,boolean wild){
        if(at==null||!Double.isFinite(at.getX())||!Double.isFinite(at.getY())||!Double.isFinite(at.getZ()))return false;
        World world=at.getWorld();if(world==null)return false;
        int x=at.getBlockX(),y=at.getBlockY(),z=at.getBlockZ();
        if(y<=world.getMinHeight()+1||y+6>=world.getMaxHeight()||!loadedFootprint(world,x,z))return false;
        if(!wild)return true;
        if(world!=plugin.world())return false;
        int radius=Math.max(plugin.integer("pets.wild-spawns.spawn-island-radius",128,80,512),plugin.integer("spawn-protection.radius",80,0,512)+8);
        return Math.hypot(x,z)>=radius&&plugin.layout().at(x,z,8)==null;
    }
    static boolean clearColumn(World world,int x,int y,int z,int height){
        for(int dy=0;dy<height;dy++){
            Block space=world.getBlockAt(x,y+dy,z);
            if(!space.isPassable()||space.isLiquid()||HAZARDS.contains(space.getType()))return false;
        }
        return true;
    }
    /** Solid, harmless ground under the whole footprint and clear headroom above it. */
    boolean safe(Location at,int radius,int headroom,boolean wild){return reason(at,radius,headroom,wild)==null;}
    /** Why a pet cannot stand here, in words an admin can act on; null when it can. */
    String reason(Location at,int radius,int headroom,boolean wild){
        if(at==null||!Double.isFinite(at.getX())||!Double.isFinite(at.getY())||!Double.isFinite(at.getZ())||at.getWorld()==null)return "ตำแหน่งไม่ถูกต้อง";
        World world=at.getWorld();int x=at.getBlockX(),y=at.getBlockY(),z=at.getBlockZ();
        if(wild&&world!=plugin.world())return "ไม่ใช่โลก Evergarden";
        if(y<=world.getMinHeight()+1||y+6>=world.getMaxHeight())return "สูงหรือต่ำเกินไป";
        if(!loadedFootprint(world,x,z))return "chunk รอบจุดยังไม่โหลด";
        if(wild){
            int island=Math.max(plugin.integer("pets.wild-spawns.spawn-island-radius",128,80,512),plugin.integer("spawn-protection.radius",80,0,512)+8);
            if(Math.hypot(x,z)<island)return "ใกล้จุดเกิดเกิน "+island+" บล็อก";
            if(plugin.layout().at(x,z,8)!=null)return "อยู่ในเขตวิหาร/ดันเจียน";
        }
        for(int dx=-radius;dx<=radius;dx++)for(int dz=-radius;dz<=radius;dz++){
            if(!world.getWorldBorder().isInside(new Location(world,x+dx+.5,y,z+dz+.5)))return "นอกขอบโลก";
            int fy=y-1;
            Block floor=world.getBlockAt(x+dx,fy,z+dz);
            if((dx!=0||dz!=0)&&!floor.getType().isSolid()){
                Block step=world.getBlockAt(x+dx,fy-1,z+dz);
                if(step.getType().isSolid()&&floor.isPassable()){floor=step;fy--;}
            }else if((dx!=0||dz!=0)&&world.getBlockAt(x+dx,y,z+dz).getType().isSolid()){
                Block step=world.getBlockAt(x+dx,y,z+dz);
                if(step.getType().isSolid()){floor=step;fy=y;}
            }
            Material ground=floor.getType();
            if(!ground.isSolid())return "ไม่มีพื้นแข็ง ("+ground.name().toLowerCase()+")";
            if(HAZARDS.contains(ground))return "พื้นอันตราย ("+ground.name().toLowerCase()+")";
            if(wild&&(Tag.LEAVES.isTagged(ground)||Tag.LOGS.isTagged(ground)))return "พื้นเป็นใบไม้/ขอนไม้";
            if(wild&&plugin.structureProtection()!=null&&plugin.structureProtection().protects(floor))return "พื้นเป็นสิ่งก่อสร้างที่ป้องกันไว้";
            for(int dy=fy+1;dy<=fy+headroom;dy++){
                Block space=world.getBlockAt(x+dx,dy,z+dz);
                if(!space.isPassable()||space.isLiquid()||HAZARDS.contains(space.getType()))return "ที่ว่างเหนือพื้นไม่พอ ("+space.getType().name().toLowerCase()+")";
            }
        }
        return null;
    }
    /**
     * A ceiling within a few blocks of a standing spot: a cave, an overhang or a building. Islands
     * high overhead and leafy canopies do not count, so skyland valleys and forests stay open.
     */
    static boolean roofed(World world,int x,int y,int z){
        for(int dy=HEADROOM;dy<HEADROOM+6&&y+dy<world.getMaxHeight();dy++){
            Material above=world.getBlockAt(x,y+dy,z).getType();
            if(above.isSolid()&&!Tag.LEAVES.isTagged(above))return true;
        }
        return false;
    }
    /** Feet height of the first standable floor scanning down from top to bottom, or NaN. */
    static double floorBetween(World world,int x,int z,int top,int bottom){
        for(int y=Math.min(top,world.getMaxHeight()-3);y>=Math.max(bottom,world.getMinHeight()+2);y--){
            Material floor=world.getBlockAt(x,y-1,z).getType();
            // Leaves count as solid; look through the canopy for the ground beneath it.
            if(Tag.LEAVES.isTagged(floor)||Tag.LOGS.isTagged(floor))continue;
            if(floor.isSolid()&&world.getBlockAt(x,y,z).isPassable()&&world.getBlockAt(x,y+1,z).isPassable())return y;
        }
        return Double.NaN;
    }
    boolean airClear(Location at,PetCatalog.Stage stage,boolean wild){
        if(!openArea(at,wild))return false;
        World world=at.getWorld();int x=at.getBlockX(),y=at.getBlockY(),z=at.getBlockZ(),r=radius(stage);
        for(int dx=-r;dx<=r;dx++)for(int dz=-r;dz<=r;dz++)
            if(!world.getWorldBorder().isInside(new Location(world,x+dx+.5,y,z+dz+.5))||!clearColumn(world,x+dx,y,z+dz,HEADROOM))return false;
        return !wild||plugin.structureProtection()==null||!plugin.structureProtection().protects(world.getBlockAt(x,y,z));
    }
    /** Feet height of the first solid surface within depth blocks below, or NaN over the void. */
    static double groundBelow(Location at,int depth){
        World world=at.getWorld();int x=at.getBlockX(),z=at.getBlockZ(),top=(int)Math.floor(at.getY());
        for(int y=top;y>=Math.max(world.getMinHeight()+1,top-depth);y--)
            if(world.getBlockAt(x,y-1,z).getType().isSolid())return y;
        return Double.NaN;
    }
}
