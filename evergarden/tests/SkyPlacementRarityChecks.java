import com.example.voidscape.world.DungeonLayout;
import com.example.voidscape.world.SkyLandmarkLayout;
import com.example.voidscape.world.SkyPlacementHistory;
import com.example.voidscape.world.SkyWhaleLayout;
import org.bukkit.configuration.file.YamlConfiguration;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;

public final class SkyPlacementRarityChecks {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        var regions=Files.createTempDirectory("sky-placement-regions-");
        Files.createFile(regions.resolve("r.2.-1.mca"));
        Files.createFile(regions.resolve("r.-3.4.mca"));
        Set<Long> oldCells=SkyPlacementHistory.capture(regions,32);
        check(oldCells.equals(Set.of(SkyPlacementHistory.key(2,-1),SkyPlacementHistory.key(-3,4))),
                "Generated regions map to the original 32-chunk cells");
        check(SkyPlacementHistory.capture(regions,40).contains(SkyPlacementHistory.key(1,-1)),
                "A region overlapping two cells preserves both");
        var container=Files.createTempDirectory("sky-placement-world-");
        var overworld=container.resolve("world");
        var paperRegion=overworld.resolve("dimensions/minecraft/evergarden2/region");
        Files.createDirectories(paperRegion);
        Files.createFile(paperRegion.resolve("r.2.-1.mca"));
        check(SkyPlacementHistory.captureWorld(container,overworld,"evergarden2",32)
                        .contains(SkyPlacementHistory.key(2,-1)),
                "Paper custom dimension regions are preserved");
        check(SkyPlacementHistory.captureWorld(container,
                        overworld.resolve("dimensions/minecraft/overworld"),"evergarden2",32)
                        .contains(SkyPlacementHistory.key(2,-1)),
                "Nested Paper overworld folders find the custom dimension");
        var saved=new YamlConfiguration();saved.set("sky-legacy-cells",oldCells.stream().sorted().toList());
        var loaded=new YamlConfiguration();loaded.loadFromString(saved.saveToString());
        check(new HashSet<>(loaded.getLongList("sky-legacy-cells")).equals(oldCells),
                "Explored cells survive world-layout.yml save and reload");

        long seed=72819345L;
        var temples=new DungeonLayout(seed,18,.85);
        var oldWhales=new SkyWhaleLayout(seed,temples,32,.12);
        var newWhales=new SkyWhaleLayout(seed,temples,32,.12,.01,oldCells);
        var oldLandmarks=new SkyLandmarkLayout(seed,temples,oldWhales);
        var newLandmarks=new SkyLandmarkLayout(seed,temples,newWhales);
        int before=0,after=0;
        for(int gx=-20;gx<=20;gx++)for(int gz=-20;gz<=20;gz++) {
            var oldWhale=oldWhales.cell(gx,gz);
            var newWhale=newWhales.cell(gx,gz);
            var oldSites=oldLandmarks.cell(gx,gz);
            var newSites=newLandmarks.cell(gx,gz);
            if(oldCells.contains(SkyPlacementHistory.key(gx,gz))) {
                check(java.util.Objects.equals(oldWhale,newWhale),"Old whale position moved");
                check(oldSites.equals(newSites),"Old landmarks moved");
            }
            before+=(oldWhale==null?0:1)+oldSites.size();
            after+=(newWhale==null?0:1)+newSites.size();
        }
        check(after < before/4,"Unexplored sky structures were not made substantially rarer");
        System.out.println("PASS: explored cells preserved; structure sites in sample fell from "+before+" to "+after);
    }
}
