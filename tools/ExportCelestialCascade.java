import com.example.voidscape.world.CelestialCascade;
import java.nio.file.*;
import java.util.*;

/**
 * Exports the Celestial Cascade's visible block shell for previews and checks that its water is stable:
 * every source is enclosed, every lip is fed by a source, every fall shaft is fed from above and lands in water.
 *
 *     java -cp <evergarden classes + paper-api> tools/ExportCelestialCascade.java <out-dir>
 */
public final class ExportCelestialCascade {
    record P(int x,int y,int z){}
    public static void main(String[] args)throws Exception {
        Path folder=Path.of(args[0]);Files.createDirectories(folder);
        long started=System.nanoTime();
        var blueprint=CelestialCascade.blueprint();
        Map<P,String> blocks=new HashMap<>();
        blueprint.forEach((x,y,z,s)->blocks.put(new P(x,y,z),s));
        System.out.printf("built %,d blocks in %.1fs, y %d..%d, %d palette states%n",blueprint.blockCount(),
                (System.nanoTime()-started)/1e9,blueprint.lowestY(),blueprint.highestY(),new HashSet<>(blocks.values()).size());
        int problems=0;int[][] side={{1,0},{-1,0},{0,1},{0,-1}};
        for(var e:blocks.entrySet()){
            P p=e.getKey();String s=e.getValue();
            if(isSource(s)&&!s.startsWith("bubble_column")){
                for(int[] d:side){String n=blocks.get(new P(p.x+d[0],p.y,p.z+d[1]));if(!tight(n)){problems++;if(problems<30)System.out.println("open source side "+p+" -> "+n);}}
                String below=blocks.get(new P(p.x,p.y-1,p.z));if(!tight(below)){problems++;if(problems<30)System.out.println("open source bed "+p+" -> "+below);}
            }else if(s.equals("water[level=1]")){
                boolean fed=false;for(int[] d:side)if(isSource(blocks.get(new P(p.x+d[0],p.y,p.z+d[1]))))fed=true;
                if(!fed){problems++;System.out.println("unfed lip "+p);}
                String below=blocks.get(new P(p.x,p.y-1,p.z));
                if(!(below!=null&&(below.equals("water[level=8]")||isSource(below)))){problems++;System.out.println("lip not falling "+p+" -> "+below);}
            }else if(s.equals("water[level=8]")){
                String above=blocks.get(new P(p.x,p.y+1,p.z)),below=blocks.get(new P(p.x,p.y-1,p.z));
                if(above==null||!above.startsWith("water")){problems++;System.out.println("fall not fed "+p+" <- "+above);}
                if(below==null||!(below.equals("water[level=8]")||isSource(below))){problems++;System.out.println("fall does not land in water "+p+" -> "+below);}
            }
        }
        for(int x:CelestialCascade.CHEST_X){
            if(blocks.get(new P(x,CelestialCascade.CHEST_Y,CelestialCascade.CHEST_Z))!=null
                    ||!"gold_block".equals(blocks.get(new P(x,CelestialCascade.CHEST_Y-1,CelestialCascade.CHEST_Z)))
                    ||!"chiseled_quartz_block".equals(blocks.get(new P(x,CelestialCascade.CHEST_Y,CelestialCascade.CHEST_Z+1)))){problems++;System.out.println("chest place broken at x="+x);}
        }
        System.out.println("water and treasure problems: "+problems);
        int shell=0;
        try(var out=Files.newBufferedWriter(folder.resolve("celestial-cascade.csv"))){
            for(var e:blocks.entrySet()){
                P p=e.getKey();
                boolean hidden=true;
                for(int[] d:new int[][]{{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}})
                    if(!opaque(blocks.get(new P(p.x+d[0],p.y+d[1],p.z+d[2])))){hidden=false;break;}
                if(hidden)continue;
                out.write(p.x+","+p.y+","+p.z+","+material(e.getValue())+"\n");shell++;
            }
        }
        Files.writeString(folder.resolve("celestial-cascade-counts.json"),"{\"total_blocks\":"+blocks.size()+",\"visible_shell_blocks\":"+shell
                +",\"lowest_y\":"+blueprint.lowestY()+",\"highest_y\":"+blueprint.highestY()+",\"water_problems\":"+problems+"}\n");
        System.out.println("visible shell: "+shell);
        if(problems>0)System.exit(2);
    }
    static boolean isSource(String s){return s!=null&&(s.equals("water")||s.startsWith("bubble_column")||s.equals("seagrass")||s.contains("waterlogged=true"));}
    static final Set<String> PLANTS=Set.of("short_grass","tall_grass","fern","pink_petals","wildflowers","allium","azure_bluet","oxeye_daisy",
            "cornflower","lily_of_the_valley","white_tulip","pink_tulip","blue_orchid","azalea","flowering_azalea","bush","firefly_bush",
            "moss_carpet","lily_pad","vine","glow_lichen","cave_vines","cave_vines_plant","hanging_roots","spore_blossom","pointed_dripstone",
            "amethyst_cluster","lantern","end_rod","lilac","peony","rose_bush","big_dripleaf","iron_chain","birch_fence");
    static String base(String s){int i=s.indexOf('[');return i<0?s:s.substring(0,i);}
    static boolean tight(String s){return s!=null&&!PLANTS.contains(base(s));}
    static boolean opaque(String s){
        if(s==null)return false;String b=base(s);
        return !PLANTS.contains(b)&&!b.contains("glass")&&!b.contains("leaves")&&!b.startsWith("water")&&!b.equals("bubble_column")
                &&!b.equals("seagrass")&&!b.endsWith("_slab")&&!b.endsWith("_stairs")&&!b.equals("dirt_path");
    }
    static String material(String s){
        String b=base(s);
        if(b.equals("water")||b.equals("bubble_column"))return "WATER";
        String m=b.toUpperCase(Locale.ROOT);
        if(s.contains("type=top")||s.contains("half=top")&&b.endsWith("_stairs"))m+="|TOP";
        if(b.equals("cave_vines")||b.equals("cave_vines_plant"))m+=s.contains("berries=true")?"|LIT":"";
        if(b.equals("big_dripleaf_stem"))return "BIG_DRIPLEAF_STEM";
        return m;
    }
}
