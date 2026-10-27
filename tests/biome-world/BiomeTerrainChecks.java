import com.example.voidscape.world.*;
import org.bukkit.Material;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Pure coordinate checks: no Bukkit server, chunk creation or mutable generation order. */
public final class BiomeTerrainChecks {
    private static int checks;
    private static void check(boolean condition,String message){
        if(!condition)throw new AssertionError(message);
        checks++;System.out.println("PASS "+message);
    }
    private static long key(int x,int z){return ((long)x<<32)|(z&0xffffffffL);}
    public static void main(String[] args)throws Exception {
        Path output=Path.of(args.length==0?"output/biome-world":args[0]);Files.createDirectories(output);
        long seed=20261013L;int scale=3072;
        var terrain=new BiomeTerrain(seed,scale,Set.of());
        var repeat=new BiomeTerrain(seed,scale,Set.of());
        var different=new BiomeTerrain(seed+1,scale,Set.of());
        check(BiomeTerrain.Kind.values().length==4,"exactly four Evergarden biome kinds");
        check(new BiomeTerrain(seed,1,Set.of()).scale()==1536&&new BiomeTerrain(seed,99999,Set.of()).scale()==8192,"biome size clamps to 1536..8192 blocks");
        var counts=new EnumMap<BiomeTerrain.Kind,Integer>(BiomeTerrain.Kind.class);
        var land=new EnumMap<BiomeTerrain.Kind,Integer>(BiomeTerrain.Kind.class);
        var sums=new EnumMap<BiomeTerrain.Kind,Long>(BiomeTerrain.Kind.class);
        var peaks=new EnumMap<BiomeTerrain.Kind,Integer>(BiomeTerrain.Kind.class);
        var corePeaks=new EnumMap<BiomeTerrain.Kind,Integer>(BiomeTerrain.Kind.class);
        var coreSums=new EnumMap<BiomeTerrain.Kind,Long>(BiomeTerrain.Kind.class);
        var coreCounts=new EnumMap<BiomeTerrain.Kind,Integer>(BiomeTerrain.Kind.class);
        int changes=0,edgeChecks=0,maxStep=0;String worst="";
        for(int x=-8192;x<=8192;x+=32)for(int z=-8192;z<=8192;z+=32){
            var s=terrain.sample(x,z);
            if(!s.equals(repeat.sample(x,z)))throw new AssertionError("Non-deterministic sample at "+x+","+z);
            if(!s.equals(different.sample(x,z)))changes++;
            counts.merge(s.kind(),1,Integer::sum);
            if(s.land()){land.merge(s.kind(),1,Integer::sum);sums.merge(s.kind(),(long)s.top(),Long::sum);peaks.merge(s.kind(),s.top(),Math::max);}
            if(s.land()&&s.border()>=.98&&Math.hypot(x,z)>=1280){coreCounts.merge(s.kind(),1,Integer::sum);coreSums.merge(s.kind(),(long)s.top(),Long::sum);corePeaks.merge(s.kind(),s.top(),Math::max);}
            if(s.top()<35||s.top()>255||s.depth()<18||s.depth()>170)throw new AssertionError("Out of world range "+s);
            for(int[] offset:new int[][]{{1,0},{0,1}}){
                var next=terrain.sample(x+offset[0],z+offset[1]);
                int delta=Math.abs(next.top()-s.top());
                if(delta>maxStep){maxStep=delta;worst=x+","+z+" "+s+" -> "+next;}
                if(next.kind()!=s.kind())edgeChecks++;
            }
        }
        check(counts.size()==4&&counts.values().stream().allMatch(n->n>30000),"16 km survey contains substantial areas of all four kinds: "+counts);
        check(changes>200000,"different seeds change terrain and biome boundaries: "+changes+" samples");
        check(edgeChecks>20&&maxStep<=6,"neighbor heights remain continuous across biome boundaries (max step "+maxStep+"; "+edgeChecks+" edges; worst "+worst+")");
        for(var kind:BiomeTerrain.Kind.values())
            check(land.get(kind)>counts.get(kind)*.80,kind+" is a continent with >80% terrain coverage");
        double iceMean=sums.get(BiomeTerrain.Kind.GLACIER)/(double)land.get(BiomeTerrain.Kind.GLACIER);
        check(iceMean>125&&peaks.get(BiomeTerrain.Kind.GLACIER)>=220,"glaciers form tall mountain massifs (mean "+Math.round(iceMean)+", peak "+peaks.get(BiomeTerrain.Kind.GLACIER)+")");
        double forestCoreMean=coreSums.get(BiomeTerrain.Kind.ANCIENT_FOREST)/(double)coreCounts.get(BiomeTerrain.Kind.ANCIENT_FOREST);
        double lunarCoreMean=coreSums.get(BiomeTerrain.Kind.LUNAR_CITY)/(double)coreCounts.get(BiomeTerrain.Kind.LUNAR_CITY);
        check(corePeaks.get(BiomeTerrain.Kind.ANCIENT_FOREST)<100&&corePeaks.get(BiomeTerrain.Kind.LUNAR_CITY)<100
                &&forestCoreMean<iceMean-30&&lunarCoreMean<iceMean-30,
                "forest and lunar interiors have lowland profiles distinct from glaciers (core peaks "+corePeaks+", means "+Math.round(forestCoreMean)+"/"+Math.round(lunarCoreMean)+")");
        var glacier=terrain.locate(BiomeTerrain.Kind.GLACIER,0,0);var ice=terrain.sample(glacier.x(),glacier.z());
        long iceBlocks=0,total=0;
        for(int y=ice.top()-ice.depth();y<=ice.top();y++){
            var material=terrain.material(ice.kind(),glacier.x(),y,glacier.z(),ice.top(),ice.depth());
            if(Set.of(Material.PACKED_ICE,Material.BLUE_ICE,Material.SNOW_BLOCK).contains(material))iceBlocks++;
            total++;
        }
        check(iceBlocks>total*.70,"glacier columns contain mostly packed/blue ice: "+iceBlocks+"/"+total);
        check(terrain.material(BiomeTerrain.Kind.ANCIENT_FOREST,32,61,32,65,80)==Material.ROOTED_DIRT,"ancient forest has rooted soil");
        check(terrain.material(BiomeTerrain.Kind.LUNAR_CITY,32,59,32,65,80)==Material.CALCITE,"lunar terrain has a calcite stratum");
        for(var kind:BiomeTerrain.Kind.values()){
            var target=terrain.locate(kind,0,0);
            check(target!=null&&terrain.sample(target.x(),target.z()).kind()==kind&&terrain.sample(target.x(),target.z()).land()&&terrain.newWeight(target.x(),target.z())==1,"coordinate locator returns fresh land in "+kind);
            int bestDiameter=0;
            for(int gx=-3;gx<=3;gx++)for(int gz=-3;gz<=3;gz++){
                var center=terrain.center(gx,gz);if(center.kind()!=kind)continue;
                if(terrain.kind(center.x(),center.z())!=kind)continue;
                int radius=0;
                while(radius<scale){
                    int r=radius+64;boolean all=true;
                    for(int d=0;d<16;d++){
                        double a=d*Math.PI/8;
                        if(terrain.kind(center.x()+(int)(Math.cos(a)*r),center.z()+(int)(Math.sin(a)*r))!=kind){all=false;break;}
                    }
                    if(!all)break;radius=r;
                }
                bestDiameter=Math.max(bestDiameter,radius*2);
            }
            check(bestDiameter>=1408,kind+" contains a broad biome interior (diameter "+bestDiameter+" blocks)");
        }
        var legacy=new BiomeTerrain(seed,scale,Set.of(key(-2,3)));
        boolean preserved=true;for(int x=-1024;x<=-513;x+=17)for(int z=1536;z<=2047;z+=17)preserved&=legacy.newWeight(x,z)==0;
        check(preserved,"existing region remains entirely on the legacy generator, including negative coordinates");
        double prior=legacy.newWeight(-513,1792);boolean smooth=true;
        for(int d=1;d<=192;d++){double weight=legacy.newWeight(-513+d,1792);smooth&=weight>=prior&&weight-prior<.009;prior=weight;}
        check(smooth&&prior==1,"legacy-to-new terrain skirt is monotonic and smooth over 192 blocks");
        check(terrain.newWeight(0,0)==0&&terrain.newWeight(384,0)==0&&terrain.newWeight(768,0)==1,"starter zone is retained with a smooth transition to new generation");
        export(terrain,output.resolve("overview.csv"),-8192,-8192,16384,32);
        for(var kind:BiomeTerrain.Kind.values()){
            var c=terrain.locate(kind,0,0);
            export(terrain,output.resolve(kind.id+".csv"),c.x()-2048,c.z()-2048,4096,16);
        }
        Files.writeString(output.resolve("checks.txt"),"PASS "+checks+" pure terrain checks\nseed="+seed+"\nscale="+scale+"\ncounts="+counts+"\n");
        System.out.println("PASS "+checks+" pure terrain checks; exact coordinate samples exported to "+output);
    }
    private static void export(BiomeTerrain terrain,Path file,int minX,int minZ,int size,int step)throws IOException {
        try(var writer=Files.newBufferedWriter(file)){
            writer.write("x,z,kind,land,top,depth,border,weight\n");
            for(int z=minZ;z<=minZ+size;z+=step)for(int x=minX;x<=minX+size;x+=step){
                var s=terrain.sample(x,z);
                writer.write(x+","+z+","+s.kind().id+","+s.land()+","+s.top()+","+s.depth()+","+String.format(Locale.ROOT,"%.4f,%.4f",s.border(),terrain.newWeight(x,z))+"\n");
            }
        }
    }
}
