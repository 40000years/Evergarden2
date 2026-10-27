import com.example.voidscape.world.*;
import org.bukkit.Material;
import java.nio.file.*;
import java.io.*;

/** Actual furnished blocks, with selected shell walls removed only in the review export. */
public final class ExportBossInteriors {
    record View(String id,BossStructureLayout.Kind kind,int x1,int x2,int z1,int z2,int above){}
    public static void main(String[] args)throws Exception {
        var folder=Path.of(args[0]);Files.createDirectories(folder);
        var views=new View[]{new View("arkhaia-hall",BossStructureLayout.Kind.ARKHAIA,-30,30,-94,-47,88),
            new View("arkhaia-gallery",BossStructureLayout.Kind.ARKHAIA,57,79,-44,43,31),
            new View("valkyrie-reliquary",BossStructureLayout.Kind.VALKYRIE,-22,22,-81,-43,44),
            new View("seraphon-hall",BossStructureLayout.Kind.SERAPHON,-31,31,-116,-61,62)};
        var materials=Material.values();
        for(var view:views){
            int width=view.x2-view.x1+1,depth=view.z2-view.z1+1,height=view.above+1,minY=view.kind.floorY;
            short[] old=new short[width*depth*height],now=new short[old.length];
            fill(BossStructure.architecture(view.kind),old,view,width,depth,height);
            fill(view.kind.blueprint(),now,view,width,depth,height);
            int count=0;
            try(var writer=Files.newBufferedWriter(folder.resolve(view.id+".csv"))){
                for(int x=view.x1;x<=view.x2;x++)for(int z=view.z1;z<=view.z2;z++)for(int dy=0;dy<height;dy++){
                    int i=((x-view.x1)*depth+z-view.z1)*height+dy,m=now[i];if(m==0)continue;
                    boolean original=old[i]==m;
                    // These cuts expose the interior; no cut is ever applied to the game structure.
                    if(original&&dy>0){
                        boolean keep=switch(view.kind){
                            case ARKHAIA->view.id.equals("arkhaia-gallery")?dy==13||x>=77&&dy<=25:x<=-28&&dy<=61||z<=-92&&dy<=61;
                            case VALKYRIE->dy<=26&&(z<=-64||x<=-16);
                            case SERAPHON->dy<=57&&(z<=-90||x<=-25);
                        };
                        if(!keep)continue;
                    }
                    writer.write(x+","+(minY+dy)+","+z+","+materials[m-1]+"\n");count++;
                }
            }
            System.out.println(view.id+": "+count+" actual blocks in interior cutaway");
        }
    }
    static void fill(BossStructure build,short[] data,View view,int width,int depth,int height){
        int minY=view.kind.floorY;
        for(var b:build.boxes()){
            int x1=Math.max(view.x1,b.x1()),x2=Math.min(view.x2,b.x2()),z1=Math.max(view.z1,b.z1()),z2=Math.min(view.z2,b.z2());
            int y1=Math.max(minY,b.y1()),y2=Math.min(minY+height-1,b.y2());if(x1>x2||z1>z2||y1>y2)continue;
            short material=(short)(b.material()==Material.AIR?0:b.material().ordinal()+1);
            for(int x=x1;x<=x2;x++)for(int z=z1;z<=z2;z++){
                int start=((x-view.x1)*depth+z-view.z1)*height;
                java.util.Arrays.fill(data,start+y1-minY,start+y2-minY+1,material);
            }
        }
    }
}
