import com.example.voidscape.world.*;
import org.bukkit.Material;
import java.util.*;

public final class GeometryChecks {
    static void check(boolean v,String s){if(!v)throw new AssertionError(s);System.out.println("PASS "+s);}
    static int index(int x,int y,int z){return ((x+55)*80+(y-90))*91+(z+45);}
    static boolean inside(int x,int y,int z){return Math.abs(x)<=55&&Math.abs(z)<=45&&y>=90&&y<170;}
    public static void main(String[] args) {
        DungeonLayout planner=new DungeonLayout(72819345,18,0.75);
        check(DungeonLayout.STARTER_SITES.size()==3,"3 guaranteed starter sanctums around spawn island");
        for(var starter : DungeonLayout.STARTER_SITES) {
            check(starter.kind()!=null&&starter.id()!=null,"valid starter site "+starter.id());
            check(starter.x()!=0||starter.z()!=0,"starter is outside spawn island");
        }
        var foundDark=planner.locate(0,0,DungeonLayout.Kind.SANCTUM_DARK,12);
        check(foundDark!=null&&foundDark.kind()==DungeonLayout.Kind.SANCTUM_DARK,"locate finds dark sanctum");
        var foundAstral=planner.locate(0,0,DungeonLayout.Kind.SANCTUM_ASTRAL,12);
        check(foundAstral!=null&&foundAstral.kind()==DungeonLayout.Kind.SANCTUM_ASTRAL,"locate finds astral sanctum");
        var foundTime=planner.locate(0,0,DungeonLayout.Kind.SANCTUM_TIME,12);
        check(foundTime!=null&&foundTime.kind()==DungeonLayout.Kind.SANCTUM_TIME,"locate finds time sanctum");
        
        // Procedural distribution check across large region
        Set<String> sanctums=new HashSet<>();
        for(int x=-5000;x<=5000;x+=576)for(int z=-5000;z<=5000;z+=576) {
            for(var site : planner.nearby(x,z)) sanctums.add(site.id());
        }
        check(sanctums.size()>=10,"sanctums generate across dimension; sampled "+sanctums.size());
        
        // 15 Advance Magic Cores check
        check(com.example.voidscape.item.RelicService.MAGIC_CORES.size()==15,"15 elemental magic cores defined");
        for(var core : com.example.voidscape.item.RelicService.MAGIC_CORES) {
            check(!core.id().isBlank()&&!core.title().isBlank()&&!core.wandTitle().isBlank(),"core validity: "+core.id());
        }
        VoidGenerator terrain=new VoidGenerator(72819345,planner);
        VoidGenerator repeat=new VoidGenerator(72819345,planner);
        int land=0,voids=0;Set<Integer> gardens=new HashSet<>();
        for(int x=-1600;x<=1600;x+=31)for(int z=-1600;z<=1600;z+=31) {
            var s=terrain.surface(x,z);
            checkSilent(s.equals(repeat.surface(x,z)),"seed-stable terrain");
            if(s.land()){land++;checkSilent(s.depth()>=5&&s.top()-s.depth()>-64,"supported floating islands");}else voids++;
            gardens.add(s.garden());
        }
        check(land>1000&&voids>1000,"both explorable land and true void across the region");
        check(gardens.size()==3,"all three garden palettes generate");
        check(terrain.surface(43,33).pond(),"spawn spring pool");
        for(int[] home:new int[][]{{55,-33},{-48,36}})for(int dx=-10;dx<=10;dx++)for(int dz=-10;dz<=10;dz++) {
            var s=terrain.surface(home[0]+dx,home[1]+dz);
            checkSilent(s.land()&&s.top()==95&&!s.pond(),"flat 21x21 home site");
        }
        check(true,"two flat 21x21 home sites");
        for(var bp:List.of(Blueprint.sanctumDark(),Blueprint.sanctumAstral(),Blueprint.sanctumTime()))for(var b:bp.boxes())
            checkSilent(b.x1()>=-28&&b.x2()<=28&&b.z1()>=-28&&b.z2()<=28&&b.y1()>=94&&b.y2()<=140,"temples fit their protected volume");
        check(true,"all redesigned temples fit protection bounds");
    }
    static void checkSilent(boolean value,String message){if(!value)throw new AssertionError(message);}
}
