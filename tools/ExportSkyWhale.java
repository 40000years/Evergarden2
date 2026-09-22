import com.example.voidscape.world.SkyWhale;
import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

/** Exports exactly the same immutable block blueprint used by the chunk generator. */
public final class ExportSkyWhale {
    public static void main(String[] args) throws Exception {
        var out = new BufferedWriter(new OutputStreamWriter(System.out, StandardCharsets.UTF_8));
        for (var block : SkyWhale.blocks()) {
            out.write(block.x() + "," + block.y() + "," + block.z() + "," + block.material().name());
            out.newLine();
        }
        out.flush();
        System.err.println("Exported " + SkyWhale.blocks().size() + " blocks from SkyWhale.blocks().");
    }
}
