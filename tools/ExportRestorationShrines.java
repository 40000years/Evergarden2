import com.example.voidscape.world.RestorationLayout;
import com.example.voidscape.world.RestorationShrine;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;

/** Export the generation blueprint for art previews; no server or world is modified. */
public final class ExportRestorationShrines {
    public static void main(String[] args) throws Exception {
        Path folder=Path.of(args[0]);Files.createDirectories(folder);
        for(var theme:RestorationLayout.Theme.values()){
            try(var writer=Files.newBufferedWriter(folder.resolve(theme.name().toLowerCase()+".csv"),StandardCharsets.UTF_8)){
                for(var b:RestorationShrine.blocks(theme,true)){
                    writer.write(b.x()+","+b.y()+","+b.z()+","+b.material().name());writer.newLine();
                }
            }
        }
    }
}
