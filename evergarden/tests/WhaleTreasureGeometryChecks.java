import com.example.voidscape.world.SkyWhale;
import org.bukkit.Material;

/** Checks against the actual blueprint, without starting or modifying a world. */
public class WhaleTreasureGeometryChecks {
    static Material at(int x, int y, int z) {
        return SkyWhale.blocks().stream().filter(b -> b.x()==x && b.y()==y && b.z()==z)
                .map(SkyWhale.Block::material).findFirst().orElse(Material.AIR);
    }
    public static void main(String[] args) {
        for (int x : new int[]{-52, -50}) {
            if (at(x,114,7)!=Material.SPRUCE_PLANKS || at(x,115,7)!=Material.AIR
                    || at(x,116,7)!=Material.AIR || at(x,115,8)!=Material.BOOKSHELF)
                throw new AssertionError("Unsafe treasure position: " + x);
            for (var point : SkyWhale.walkway())
                if (point.x()==x && point.z()==7) throw new AssertionError("Blocks walking route");
        }
        System.out.println("PASS: both chests have floors, lid clearance, bookshelves and clear walking routes");
    }
}
