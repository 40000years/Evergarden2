import com.example.voidscape.world.*;
import org.bukkit.Material;
import java.nio.file.*;
import java.util.*;

/** Geometry and layout checks use precisely the blueprints consumed by generation. */
public final class SkyLandmarkChecks {
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    // Pure geometry checks cannot query Paper's live Material registry.
    private static boolean floor(Material m){return !Set.of(Material.AIR,Material.WATER,Material.LANTERN,
            Material.IRON_CHAIN,Material.LIGHTNING_ROD,Material.SPRUCE_FENCE,Material.MOSS_CARPET,
            Material.ALLIUM,Material.AZURE_BLUET,Material.PINK_TULIP,Material.BLUE_ORCHID).contains(m);}
    public static void main(String[] args)throws Exception{
        for(var kind:SkyLandmarkLayout.Kind.values()){
            LandmarkBlueprint b=kind.blueprint();
            check(b.blocks().size()>20000,"Empty landmark: "+kind);
            for(var v:b.blocks())check(Math.abs(v.x())<=kind.radius&&Math.abs(v.z())<=kind.radius,"Reservation escaped");
            for(int x:new int[]{4,6}){
                check(b.at(x,kind.chestY-1,kind.chestZ)==Material.SPRUCE_PLANKS,"Chest floor");
                check(b.at(x,kind.chestY,kind.chestZ)==Material.AIR&&b.at(x,kind.chestY+1,kind.chestZ)==Material.AIR,"Chest lid clearance");
                check(b.at(x,kind.chestY,kind.chestZ+1)==Material.BOOKSHELF,"Chest marker");
            }
            for(var p:b.route()){
                check(floor(b.at(p.x(),p.y(),p.z())),kind+" route floor "+p);
                check(b.at(p.x(),p.y()+1,p.z())==Material.AIR&&b.at(p.x(),p.y()+2,p.z())==Material.AIR,kind+" route headroom "+p);
            }
            check(floor(b.at(kind.arrivalX,kind.arrivalY-1,kind.arrivalZ)),"Arrival floor "+kind);
            check(b.at(kind.arrivalX,kind.arrivalY,kind.arrivalZ)==Material.AIR,"Arrival clearance "+kind);
            // Flood-fill walkable voxel surfaces with single-block steps; both treasure alcoves must be reachable.
            Set<LandmarkBlueprint.Point> visited=new HashSet<>();ArrayDeque<LandmarkBlueprint.Point> todo=new ArrayDeque<>();
            todo.add(new LandmarkBlueprint.Point(kind.arrivalX,kind.arrivalY-1,kind.arrivalZ));
            while(!todo.isEmpty()){
                var p=todo.remove();if(!visited.add(p))continue;
                for(int[] d:new int[][]{{1,0},{-1,0},{0,1},{0,-1}})for(int dy=-1;dy<=1;dy++){
                    var n=new LandmarkBlueprint.Point(p.x()+d[0],p.y()+dy,p.z()+d[1]);
                    if(!visited.contains(n)&&floor(b.at(n.x(),n.y(),n.z()))
                            &&b.at(n.x(),n.y()+1,n.z())==Material.AIR&&b.at(n.x(),n.y()+2,n.z())==Material.AIR)todo.add(n);
                }
            }
            for(int x:new int[]{4,6})check(visited.contains(new LandmarkBlueprint.Point(x,kind.chestY-1,kind.chestZ)),"Unreachable chest "+kind);
            if(args.length>0){
                Path dir=Path.of(args[0]);Files.createDirectories(dir);
                try(var out=Files.newBufferedWriter(dir.resolve(kind.id+".csv"))){
                    for(var v:b.blocks())out.write(v.x()+","+v.y()+","+v.z()+","+v.material()+"\n");
                }
            }
            System.out.println("PASS "+kind+": "+b.blocks().size()+" blocks, "+visited.size()+" reachable floor positions");
        }
        for(long seed:new long[]{72819345L,12345L,-817342L}){
            var temples=new DungeonLayout(seed,18,.85);var whales=new SkyWhaleLayout(seed,temples,32,.8);
            var layout=new SkyLandmarkLayout(seed,temples,whales);var repeat=new SkyLandmarkLayout(seed,temples,whales);
            int whaleCount=0,pairs=0;
            for(int gx=-8;gx<=8;gx++)for(int gz=-8;gz<=8;gz++){
                var whale=whales.cell(gx,gz);if(whale!=null)whaleCount++;
                var sites=layout.cell(gx,gz);check(sites.equals(repeat.cell(gx,gz)),"Non-deterministic layout");
                if(!sites.isEmpty()){
                    pairs++;check(sites.size()==2,"Unbalanced landmark types");
                    for(var site:sites){
                        check(site.x()%16==0&&site.z()%16==0,"Unaligned origin");
                        check(Math.floorDiv(site.x()-site.kind().radius,512)==gx&&Math.floorDiv(site.x()+site.kind().radius,512)==gx,"Cell boundary X");
                        check(Math.floorDiv(site.z()-site.kind().radius,512)==gz&&Math.floorDiv(site.z()+site.kind().radius,512)==gz,"Cell boundary Z");
                        for(int dx=-site.kind().radius;dx<=site.kind().radius;dx++)for(int dz=-site.kind().radius;dz<=site.kind().radius;dz++)
                            check(!SkyWhale.containsColumn(site.x()+dx-whale.x(),site.z()+dz-whale.z(),0),"Whale overlap");
                        for(var temple:temples.nearby(site.x(),site.z()))check(Math.abs(site.x()-temple.x())>=104||Math.abs(site.z()-temple.z())>=104,"Temple overlap");
                    }
                }
            }
            System.out.println("Placement seed="+seed+": whales="+whaleCount+", observatories="+pairs+", gardens="+pairs);
            check(pairs>=whaleCount*.90,"New structures too rare relative to whales");
        }
        var temples=new DungeonLayout(1,18,.85);
        check(new SkyLandmarkLayout(1,temples,new SkyWhaleLayout(1,temples,32,0)).cell(10,10).isEmpty(),"Zero chance ignored");
        System.out.println("PASS geometry, reachable chests, deterministic placement, equal rates, exclusions and zero chance");
    }
}
