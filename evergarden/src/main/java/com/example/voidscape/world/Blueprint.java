package com.example.voidscape.world;

import org.bukkit.Material;
import org.bukkit.generator.ChunkGenerator.ChunkData;
import java.util.*;

/** Immutable boxes, clipped into each generated chunk. Buildings never use display entities. */
public final class Blueprint {
    public record Box(int x1,int y1,int z1,int x2,int y2,int z2,Material material) {}
    private final List<Box> boxes=new ArrayList<>();
    private void box(int x1,int y1,int z1,int x2,int y2,int z2,Material m) { boxes.add(new Box(x1,y1,z1,x2,y2,z2,m)); }
    private void shell(int x1,int y1,int z1,int x2,int y2,int z2,Material m) {
        box(x1,y1,z1,x2,y2,z2,m); box(x1+1,y1+1,z1+1,x2-1,y2-1,z2-1,Material.AIR);
    }
    public List<Box> boxes() { return List.copyOf(boxes); }
    public static Blueprint sanctumDark() { return gardenSanctum(0); }
    public static Blueprint sanctumAstral() { return gardenSanctum(1); }
    public static Blueprint sanctumTime() { return gardenSanctum(2); }

    /** Open-air cloisters, with an impossible broken halo over the arena. */
    private static Blueprint gardenSanctum(int kind) {
        Blueprint b=new Blueprint();
        Material stone=kind==0?Material.POLISHED_BLACKSTONE_BRICKS:kind==1?Material.SMOOTH_QUARTZ:Material.END_STONE_BRICKS;
        Material trim=kind==0?Material.CHISELED_DEEPSLATE:kind==1?Material.PURPUR_PILLAR:Material.WAXED_OXIDIZED_COPPER;
        Material glow=kind==2?Material.OCHRE_FROGLIGHT:Material.SEA_LANTERN;
        Material glass=kind==0?Material.PURPLE_STAINED_GLASS:kind==1?Material.LIGHT_BLUE_STAINED_GLASS:Material.CYAN_STAINED_GLASS;
        b.box(-23,94,-23,23,95,23,Material.CALCITE);
        b.box(-22,96,-22,22,96,22,stone);
        b.box(-18,96,-18,18,96,18,Material.SMOOTH_QUARTZ);
        // Fine mosaic rings instead of a solid, windowless cube.
        for(int x=-18;x<=18;x++)for(int z=-18;z<=18;z++) {
            double r=Math.hypot(x,z);
            if(Math.abs(r-14)<0.65||Math.abs(r-6)<0.6)b.box(x,96,z,x,96,z,trim);
            if(Math.abs(r-14)<0.65&&(x==0||z==0))b.box(x,96,z,x,96,z,glow);
        }
        for(int side:new int[]{-21,21})for(int along=-21;along<=21;along+=7) {
            for(int axis=0;axis<2;axis++) {
                int x=axis==0?side:along,z=axis==0?along:side;
                if(Math.abs(x)<4||Math.abs(z)<4)continue;
                b.box(x-1,97,z-1,x+1,98,z+1,trim);
                b.box(x,99,z,x,110,z,stone);
                b.box(x-1,110,z-1,x+1,111,z+1,trim);
                b.box(x,112,z,x,112,z,glow);
            }
        }
        // Cloister lintels and open windows reveal the surrounding gardens.
        for(int side:new int[]{-21,21}) {
            b.box(-21,112,side,21,113,side,stone);
            b.box(side,112,-21,side,113,21,stone);
            b.box(-21,114,side,21,114,side,trim);
            b.box(side,114,-21,side,114,21,trim);
            for(int a:new int[]{-14,-7,7,14}) {
                b.box(a-2,105,side,a+2,108,side,glass);
                b.box(side,105,a-2,side,108,a+2,glass);
            }
        }
        // Four graceful gate arches, walkable at floor Y=97.
        for(int z:new int[]{-23,23}) {
            b.box(-5,97,z,-5,106,z,trim);b.box(5,97,z,5,106,z,trim);
            b.box(-5,107,z,5,108,z,stone);b.box(-3,109,z,3,109,z,trim);
            b.box(0,108,z,0,108,z,glow);
        }
        // A broken orbital crown: hollow, floating, and low enough to protect with the arena.
        for(int x=-15;x<=15;x++)for(int z=-15;z<=15;z++) {
            double r=Math.hypot(x,z);
            if(Math.abs(r-13)<0.7&&!(x>4&&z<-4)) {
                int y=126+(int)Math.round(x*0.22);
                b.box(x,y,z,x,y,z,trim);
                if(Math.floorMod(x+z,9)==0)b.box(x,y+1,z,x,y+1,z,glow);
            }
        }
        b.box(0,126,0,0,130,0,Material.AMETHYST_BLOCK);
        b.box(0,128,0,0,128,0,glow);
        if(kind==0) {
            for(int x:new int[]{-16,16})for(int z:new int[]{-16,16})b.box(x,115,z,x,124,z,Material.CRYING_OBSIDIAN);
        } else if(kind==1) {
            for(int x:new int[]{-10,10})b.box(x,119,0,x,119,0,glow);
        } else {
            for(int x=-4;x<=4;x++)for(int y=0;y<=8;y++)if(Math.abs(x)==Math.abs(y-4))b.box(x,116+y,0,x,116+y,0,trim);
            b.box(0,119,0,0,121,0,glow);
        }
        // Planters outside the combat floor; no obstruction to guardians or rewards.
        for(int x:new int[]{-25,25})for(int z:new int[]{-14,0,14}) {
            b.box(x-1,96,z-2,x+1,96,z+2,Material.MOSS_BLOCK);
            b.box(x,97,z,x,97,z,Material.FLOWERING_AZALEA);
        }
        b.box(-3,96,5,3,96,11,trim);b.box(-1,96,7,1,96,9,Material.AMETHYST_BLOCK);
        b.box(0,97,8,0,97,8,Material.LODESTONE);
        b.box(-2,96,-18,2,96,-14,trim);b.box(0,97,-16,0,97,-16,Material.VAULT);
        b.box(-2,97,-17,-2,100,-17,stone);b.box(2,97,-17,2,100,-17,stone);
        b.box(-2,101,-17,2,101,-17,trim);b.box(0,100,-17,0,100,-17,glow);
        return b;
    }

    public void render(ChunkData data,int chunkX,int chunkZ,DungeonLayout.Site site) {
        int ox=chunkX*16-site.x(),oz=chunkZ*16-site.z();
        for(Box b:boxes) {
            int x1=Math.max(0,b.x1-ox), x2=Math.min(15,b.x2-ox);
            int z1=Math.max(0,b.z1-oz), z2=Math.min(15,b.z2-oz);
            if(x1>x2 || z1>z2) continue;
            data.setRegion(x1,b.y1,z1,x2+1,b.y2+1,z2+1,b.material);
        }
    }
}
