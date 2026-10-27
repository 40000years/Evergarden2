import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import java.util.zip.ZipFile;

/** Run with an installed official client jar, without opening a game window. */
public class ArmorNativeClientCodecChecks {
    public static void main(String[] args) throws Exception {
        try(var zip=new ZipFile(args[0])) {
            for(String name:new String[]{"aegis","kavacha","aegishjalmur"}) {
                var entry=zip.getEntry("assets/advance_magic/equipment/armor_"+name+".json");
                var json=JsonParser.parseString(new String(zip.getInputStream(entry).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));
                var result=EquipmentClientInfo.CODEC.parse(JsonOps.INSTANCE,json).getOrThrow();
                if(result.layers().size()!=2)throw new AssertionError("Missing native armor layer: "+name);
                System.out.println("PASS official Java client equipment codec: "+name);
            }
        }
    }
}
