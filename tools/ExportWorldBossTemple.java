import com.example.voidscape.world.WorldBossTemple;
import org.bukkit.Material;
import java.io.*;

/** Export the final ordered block geometry, including intentional air cuts. */
public final class ExportWorldBossTemple {
    public static void main(String[] args)throws Exception {
        int width=WorldBossTemple.RADIUS*2+1,height=WorldBossTemple.MAX_Y-WorldBossTemple.MIN_Y+1;
        short[] cells=new short[width*width*height];Material[] materials=Material.values();
        for(var b:WorldBossTemple.blueprint().boxes()){
            short material=(short)(b.material()==Material.AIR?0:b.material().ordinal()+1);
            for(int x=b.x1();x<=b.x2();x++)for(int z=b.z1();z<=b.z2();z++){
                int start=((x+WorldBossTemple.RADIUS)*width+z+WorldBossTemple.RADIUS)*height;
                java.util.Arrays.fill(cells,start+b.y1()-WorldBossTemple.MIN_Y,start+b.y2()-WorldBossTemple.MIN_Y+1,material);
            }
        }
        try(var out=new BufferedWriter(new FileWriter(args[0]))){
            for(int x=0;x<width;x++)for(int z=0;z<width;z++)for(int y=0;y<height;y++){
                int m=cells[(x*width+z)*height+y];if(m==0)continue;
                out.write((x-WorldBossTemple.RADIUS)+","+(y+WorldBossTemple.MIN_Y)+","+(z-WorldBossTemple.RADIUS)+","+materials[m-1]+"\n");
            }
        }
    }
}
