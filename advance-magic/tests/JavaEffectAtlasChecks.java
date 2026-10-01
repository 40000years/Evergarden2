import com.google.gson.JsonParser;
import net.minecraft.client.renderer.texture.atlas.SpriteSource;
import net.minecraft.client.renderer.texture.atlas.SpriteSourceList;
import net.minecraft.client.renderer.texture.atlas.SpriteSources;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.*;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackCompatibility;
import net.minecraft.server.packs.metadata.pack.PackFormat;
import net.minecraft.server.packs.resources.*;
import net.minecraft.world.flag.FeatureFlagSet;
import javax.imageio.ImageIO;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Predicate;

/** Uses the installed client's actual atlas codecs and sprite source resolution.
 * Does not open Minecraft or require a graphics context. Packs are low-to-high priority.
 */
public final class JavaEffectAtlasChecks {
    private static Map<Identifier,Resource> atlas(ResourceManager resources,String name)throws Exception {
        var parsed=SpriteSourceList.load(resources,Identifier.withDefaultNamespace(name));
        var field=SpriteSourceList.class.getDeclaredField("sources");field.setAccessible(true);
        var sprites=new HashMap<Identifier,Resource>();
        var output=new SpriteSource.Output() {
            @Override public void add(Identifier id,Resource resource){sprites.put(id,resource);}
            @Override public void add(Identifier id,SpriteSource.DiscardableLoader loader){sprites.put(id,null);}
            @Override public void removeAll(Predicate<Identifier> predicate){sprites.keySet().removeIf(predicate);}
        };
        for(var source:(List<?>)field.get(parsed))((SpriteSource)source).run(resources,output);
        return sprites;
    }
    public static void main(String[] args)throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        SpriteSources.bootstrap();
        boolean expectMissing=args[0].equals("missing");
        PackFormat format;
        try(var client=new java.util.zip.ZipFile(args[1]);var reader=new java.io.InputStreamReader(client.getInputStream(client.getEntry("version.json")))) {
            var version=JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("pack_version");
            format=new PackFormat(version.get("resource_major").getAsInt(),version.get("resource_minor").getAsInt());
        }
        var packs=new ArrayList<PackResources>();
        for(int i=1;i<args.length;i++) {
            var info=new PackLocationInfo("check-"+i,Component.literal("Atlas check"),PackSource.DEFAULT,Optional.empty());
            var supplier=new FilePackResources.FileResourcesSupplier(Path.of(args[i]));
            var metadata=i==1?new Pack.Metadata(Component.literal("Vanilla"),PackCompatibility.COMPATIBLE,FeatureFlagSet.of(),List.of())
                :Pack.readPackMetadata(info,supplier,format,PackType.CLIENT_RESOURCES);
            if(metadata==null)throw new AssertionError("Invalid pack metadata: "+args[i]);
            try {
                packs.add((PackResources)supplier.getClass().getMethod("openFull",PackLocationInfo.class,Pack.Metadata.class).invoke(supplier,info,metadata));
            } catch(NoSuchMethodException changedIn263) {
                try(var stream=(java.util.stream.Stream<?>)supplier.getClass().getMethod("openResources",PackLocationInfo.class,Pack.Metadata.class).invoke(supplier,info,metadata)) {
                    stream.forEach(pack->packs.add((PackResources)pack));
                }
            }
        }
        try(var resources=new MultiPackResourceManager(PackType.CLIENT_RESOURCES,packs)) {
            var items=atlas(resources,"items");var blocks=atlas(resources,"blocks");
            if(!items.containsKey(Identifier.parse("minecraft:item/paper"))||!blocks.containsKey(Identifier.parse("minecraft:block/stone")))
                throw new AssertionError("Vanilla atlas control sprites did not load");
            var missing=new TreeSet<String>();int models=0;
            for(var entry:resources.listResources("models/effect",id->id.getNamespace().equals("advance_magic")&&id.getPath().endsWith(".json")).entrySet()) {
                models++;
                var model=JsonParser.parseReader(entry.getValue().openAsReader()).getAsJsonObject();
                Set<String> usedAtlases=new HashSet<>();
                for(var texture:model.getAsJsonObject("textures").entrySet()) {
                    String value=texture.getValue().getAsString();
                    if(value.startsWith("#"))continue;
                    var id=Identifier.parse(value);
                    Resource image=items.get(id);
                    if(items.containsKey(id))usedAtlases.add("items");
                    else if(blocks.containsKey(id)){usedAtlases.add("blocks");image=blocks.get(id);}
                    else {missing.add(id.toString());continue;}
                    if(image==null)throw new AssertionError("Expected a PNG resource for "+id);
                    try(var stream=image.open()) {
                        if(ImageIO.read(stream)==null)throw new AssertionError("Unreadable PNG: "+id);
                    }
                }
                if(usedAtlases.size()>1)throw new AssertionError("Mixed atlases: "+entry.getKey());
            }
            if(models!=12)throw new AssertionError("Expected all 12 spell effect models, got "+models);
            if(expectMissing) {
                if(missing.size()!=12)throw new AssertionError("Expected original 12 missing sprites, got "+missing);
                System.out.println("REPRODUCED: Java cannot resolve all 12 effect sprites: "+missing);
            } else {
                if(!missing.isEmpty())throw new AssertionError("Missing Java atlas sprites: "+missing);
                System.out.println("PASS: actual client atlas loader resolves all 12 effect models to readable PNGs in one atlas per model");
            }
        }
    }
}
