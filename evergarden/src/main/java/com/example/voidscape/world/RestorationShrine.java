package com.example.voidscape.world;

import org.bukkit.Material;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.generator.ChunkGenerator.ChunkData;
import java.util.*;

/** Real vanilla-block architecture, visible with Java or Bedrock and no resource pack. */
public final class RestorationShrine {
    public record Block(int x,int y,int z,Material material) {}
    private record Key(RestorationLayout.Theme theme,boolean altar) {}
    private static final Map<Key,List<Block>> BLUEPRINTS=new java.util.concurrent.ConcurrentHashMap<>();
    private RestorationShrine(){}
    public static List<Block> blocks(RestorationLayout.Theme theme,boolean altar){return BLUEPRINTS.computeIfAbsent(new Key(theme,altar),k->build(k.theme(),k.altar()));}
    private static List<Block> build(RestorationLayout.Theme theme,boolean altar){
        Map<String,Block> blocks=new LinkedHashMap<>();
        class Builder {
            void put(int x,int y,int z,Material m){blocks.put(x+","+y+","+z,new Block(x,y,z,m));}
            void line(double x1,double y1,double z1,double x2,double y2,double z2,Material m){
                int steps=(int)(Math.max(Math.max(Math.abs(x2-x1),Math.abs(y2-y1)),Math.abs(z2-z1))*3)+1;
                int py=(int)Math.round(y1),pz=(int)Math.round(z1);
                for(int i=0;i<=steps;i++){
                    double t=i/(double)steps;int x=(int)Math.round(x1+(x2-x1)*t),y=(int)Math.round(y1+(y2-y1)*t),z=(int)Math.round(z1+(z2-z1)*t);
                    put(x,py,pz,m);put(x,y,pz,m);put(x,y,z,m);py=y;pz=z;
                }
            }
        }
        Builder b=new Builder();
        boolean garden=theme==RestorationLayout.Theme.GARDEN,whale=theme==RestorationLayout.Theme.WHALE;
        Material floor=garden?Material.MOSS_BLOCK:whale?Material.SMOOTH_QUARTZ:Material.POLISHED_DEEPSLATE;
        Material edge=garden?Material.MOSSY_STONE_BRICKS:whale?Material.CALCITE:Material.WAXED_CUT_COPPER;
        Material arch=garden?Material.MANGROVE_ROOTS:whale?Material.BONE_BLOCK:Material.WAXED_EXPOSED_COPPER;
        // A 19-block sanctuary. Open cardinal entrances keep all approaches walkable.
        for(int x=-9;x<=9;x++)for(int z=-9;z<=9;z++){
            double r=Math.hypot(x,z);if(r>9.4)continue;
            b.put(x,-1,z,edge);b.put(x,0,z,r>8.2?edge:floor);
            if(r>5.8&&r<7.1)b.put(x,0,z,Material.AMETHYST_BLOCK);
            if((Math.abs(x)==Math.abs(z)&&r>2&&r<8)||(x==0&&Math.abs(z)==8)||(z==0&&Math.abs(x)==8))b.put(x,0,z,Material.SEA_LANTERN);
            if(r>8.1&&Math.abs(x)>2&&Math.abs(z)>2)b.put(x,1,z,edge);
        }
        // Whale chapel joins the existing skull walkway with a broad bone bridge.
        if(whale)for(int z=9;z<=22;z++)for(int x=-2;x<=2;x++){
            b.put(x,0,z,Material.SPRUCE_PLANKS);if(Math.abs(x)==2&&z<=16)b.put(x,1,z,Material.BONE_BLOCK);
        }
        if(altar){
            if(garden)for(int x=-4;x<=4;x++)for(int z=-4;z<=4;z++){
                double r=Math.hypot(x,z);
                if(r>=2.5&&r<=4.5&&Math.abs(x)>1&&Math.abs(z)>1){
                    b.put(x,-1,z,Material.SEA_LANTERN);b.put(x,0,z,Material.CYAN_STAINED_GLASS);
                }
            }
            for(int sx:new int[]{-1,1})for(int sz:new int[]{-1,1}){
                for(int y=1;y<=5;y++)b.put(sx*6,y,sz*6,arch);
                b.put(sx*6,6,sz*6,Material.SEA_LANTERN);
                b.line(sx*6,6,sz*6,sx*4,9,sz*4,arch);
                b.line(sx*4,9,sz*4,sx*2,11,sz*2,arch);
                if(garden){
                    for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)b.put(sx*6+dx,7,sz*6+dz,Material.FLOWERING_AZALEA_LEAVES);
                    b.line(sx*8,-1,sz*7,sx*5,-4,sz*5,Material.MANGROVE_ROOTS);
                }
            }
            // Floating crystal heart / starlens framed by two offset orbital rings.
            for(int i=0;i<100;i++){
                double a=i*Math.PI*2/100;
                b.put((int)Math.round(Math.cos(a)*4),9+(int)Math.round(Math.sin(a)*2),(int)Math.round(Math.sin(a)*4),edge);
                if(!garden)b.put((int)Math.round(Math.cos(a)*4),9-(int)Math.round(Math.sin(a)*2),(int)Math.round(Math.sin(a)*4),arch);
            }
            for(int y=6;y<=11;y++){
                int radius=y==8||y==9?1:0;
                for(int x=-radius;x<=radius;x++)for(int z=-radius;z<=radius;z++)
                    b.put(x,y,z,x==0&&z==0?Material.SEA_LANTERN:Material.AMETHYST_BLOCK);
            }
            for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)b.put(x,0,z,Material.CHISELED_QUARTZ_BLOCK);
            b.put(0,1,0,Material.LODESTONE);
        }else{
            // Empty sanctuaries retain a readable waystone; no deceptive working pedestal.
            for(int sx:new int[]{-1,1})for(int sz:new int[]{-1,1})
                for(int y=1;y<=2+(sx==sz?1:0);y++)b.put(sx*6,y,sz*6,arch);
            b.put(0,1,0,Material.CHISELED_STONE_BRICKS);b.put(0,0,0,Material.SEA_LANTERN);
        }
        return List.copyOf(blocks.values());
    }
    public static Material marker(RestorationLayout.Site site){return site.altar()?Material.LODESTONE:Material.CHISELED_STONE_BRICKS;}
    public static void render(ChunkData data,int cx,int cz,RestorationLayout.Site site){
        // Clear only the new chapel interior during generation, never edit a loaded old world.
        for(int x=0;x<16;x++)for(int z=0;z<16;z++){
            int dx=cx*16+x-site.x(),dz=cz*16+z-site.z();
            if(Math.hypot(dx,dz)<=9.4)for(int y=site.y()+1;y<=site.y()+12;y++)data.setBlock(x,y,z,Material.AIR);
        }
        for(Block b:blocks(site.theme(),site.altar())){
            int x=site.x()+b.x()-cx*16,z=site.z()+b.z()-cz*16,y=site.y()+b.y();
            if(x<0||x>15||z<0||z>15||y<data.getMinHeight()||y>=data.getMaxHeight())continue;
            if(b.material()==Material.FLOWERING_AZALEA_LEAVES){var leaves=(Leaves)b.material().createBlockData();leaves.setPersistent(true);data.setBlock(x,y,z,leaves);}
            else data.setBlock(x,y,z,b.material());
        }
    }
}
