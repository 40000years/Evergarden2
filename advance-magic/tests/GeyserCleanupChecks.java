import com.example.advancemagic.pack.GeyserPackCleanup;
import com.example.advancemagic.pack.GeyserPackSetup;
import com.google.gson.*;
import java.nio.file.*;
import java.util.zip.*;

public final class GeyserCleanupChecks {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    static void pack(Path file,String uuid)throws Exception {
        try(var zip=new ZipOutputStream(Files.newOutputStream(file))) {
            zip.putNextEntry(new ZipEntry("manifest.json"));
            zip.write(("{\"header\":{\"uuid\":\""+uuid+"\"}}").getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zip.closeEntry();
        }
    }
    public static void main(String[] args)throws Exception {
        Path root=Files.createTempDirectory("geyser-cleanup-check-");
        Path mappings=Files.createDirectories(root.resolve("custom_mappings"));
        Path packs=Files.createDirectories(root.resolve("packs"));
        Path bundled=root.resolve("bundled.json"),bundlePack=root.resolve("bundled.mcpack");
        String own="{\"bedrock_identifier\":\"test:owned\"}";
        String other="{\"bedrock_identifier\":\"another:untouched\"}";
        String canonical="{\"format_version\":2,\"items\":{\"minecraft:stick\":["+own+"]}}";
        String mixed="{\"format_version\":2,\"items\":{\"minecraft:stick\":["+own+","+other+"]}}";
        Files.writeString(bundled,canonical);
        Files.writeString(mappings.resolve("canonical.json"),canonical);
        Files.writeString(mappings.resolve("mixed.json"),mixed);
        Files.writeString(mappings.resolve("malformed.json"),"{");
        pack(bundlePack,"11111111-1111-1111-1111-111111111111");
        Files.copy(bundlePack,packs.resolve("canonical.mcpack"));Files.copy(bundlePack,packs.resolve("duplicate.mcpack"));
        pack(packs.resolve("other.mcpack"),"22222222-2222-2222-2222-222222222222");
        check(GeyserPackCleanup.clean(root,bundled,bundlePack,"canonical.json","canonical.mcpack")==2,"two duplicate files cleaned");
        var array=JsonParser.parseString(Files.readString(mappings.resolve("mixed.json"))).getAsJsonObject().getAsJsonObject("items").getAsJsonArray("minecraft:stick");
        check(array.size()==1&&array.get(0).getAsJsonObject().get("bedrock_identifier").getAsString().equals("another:untouched"),"mixed mapping preserves other plugin");
        check(Files.readString(mappings.resolve("canonical.json")).equals(canonical),"canonical mapping preserved");
        check(Files.exists(packs.resolve("canonical.mcpack"))&&Files.exists(packs.resolve("other.mcpack"))&&!Files.exists(packs.resolve("duplicate.mcpack")),"only duplicate UUID removed from scan");
        try(var files=Files.list(root.resolve("plugin-pack-backups"))){check(files.count()==2,"both originals backed up");}
        check(Files.readString(mappings.resolve("malformed.json")).equals("{"),"malformed unrelated file untouched");
        check(GeyserPackCleanup.clean(root,bundled,bundlePack,"canonical.json","canonical.mcpack")==0,"cleanup idempotent");
        Path config=root.resolve("config.yml");
        Files.writeString(config,"gameplay:\n  enable-custom-content: false\n  force-resource-packs: false\n");
        check(GeyserPackSetup.enableCustomContent(root),"disabled custom content enabled");
        String updated=Files.readString(config);
        check(updated.contains("enable-custom-content: true")&&updated.contains("force-resource-packs: false"),"unrelated config preserved");
        try(var files=Files.list(root.resolve("plugin-pack-backups"))){check(files.count()==3,"config backed up");}
        check(!GeyserPackSetup.enableCustomContent(root),"config update idempotent");
        Path fresh=Files.createTempDirectory("geyser-fresh-check-");
        check(GeyserPackSetup.enableCustomContent(fresh)&&Files.readString(fresh.resolve("config.yml")).contains("enable-custom-content: true"),"fresh Geyser config enabled");
        System.out.println("PASS: Geyser duplicate cleanup and custom content setup checks");
    }
}
