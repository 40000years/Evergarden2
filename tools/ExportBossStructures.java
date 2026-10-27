import com.example.voidscape.world.*;
import org.bukkit.Material;
import java.nio.file.*;
import java.io.*;

/** Exports the final visible block shell, after every air cut, for faithful previews. */
public final class ExportBossStructures {
    public static void main(String[] args)throws Exception {
        Path folder=Path.of(args[0]);Files.createDirectories(folder);
        for(var kind:BossStructureLayout.Kind.values()){
            int width=kind.radius*2+1,height=kind.maxY-BossStructure.MIN_Y+1;
            short[] cells=new short[width*width*height];var materials=Material.values();
            for(var b:kind.blueprint().boxes()){
                short material=(short)(b.material()==Material.AIR?0:b.material().ordinal()+1);
                for(int x=b.x1();x<=b.x2();x++)for(int z=b.z1();z<=b.z2();z++){
                    int start=((x+kind.radius)*width+z+kind.radius)*height;
                    java.util.Arrays.fill(cells,start+b.y1()-BossStructure.MIN_Y,start+b.y2()-BossStructure.MIN_Y+1,material);
                }
            }
            int total=0,surface=0;
            try(var out=Files.newBufferedWriter(folder.resolve(kind.id+".csv"))){
                for(int x=0;x<width;x++)for(int z=0;z<width;z++)for(int y=0;y<height;y++){
                    int i=(x*width+z)*height+y,m=cells[i];if(m==0)continue;total++;
                    if(x>0&&x<width-1&&z>0&&z<width-1&&y>0&&y<height-1
                            &&cells[i-width*height]!=0&&cells[i+width*height]!=0
                            &&cells[i-height]!=0&&cells[i+height]!=0&&cells[i-1]!=0&&cells[i+1]!=0)continue;
                    out.write((x-kind.radius)+","+(y+BossStructure.MIN_Y)+","+(z-kind.radius)+","+materials[m-1]+"\n");surface++;
                }
            }
            Files.writeString(folder.resolve(kind.id+"-counts.json"),"{\"total_blocks\":"+total+",\"visible_shell_blocks\":"+surface+"}");
            System.out.println(kind.id+": "+total+" final blocks, "+surface+" visible shell blocks");
        }
    }
}
