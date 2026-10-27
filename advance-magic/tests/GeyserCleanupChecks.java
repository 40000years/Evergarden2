import com.example.advancemagic.pack.GeyserPackCleanup;
import com.example.advancemagic.pack.GeyserPackSetup;
import com.google.gson.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

public final class GeyserCleanupChecks {
    static int checks;
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
    @FunctionalInterface interface Cleaner {
        int clean(Path geyser,Path mapping,Path pack,String mappingName,String packName)throws IOException;
    }
    static final List<String> RETIRED=List.of(
        "lightning_strike","frost_nova","shadow_step","natures_bloom","earth_wall","dragons_breath",
        "void_pull","sonic_boom","blaze_barrage","wither_ray","shulker_levitation","meteor_strike",
        "iron_armor","vex_legion","guardian_beam","solar_apocalypse","chronos_final_hour","heavens_judgment",
        "verdant_sovereign","lunar_requiem","crimson_oblivion");
    static final List<String> PRESERVED=List.of(
        "another:untouched","advance_magic:restoration_core","advance_magic:skilltree_tab_core",
        "advance_magic:skilltree_tab_core_on","voidscape:core_of_legend","voidscape:core_of_seraph",
        "voidscape:core_of_revenant","advance_magic:core_future_spell",
        "another:core_lightning_strike","advance_magic:core_lightning_strike_extra");
    static JsonObject definition(String id) {
        JsonObject result=new JsonObject();result.addProperty("bedrock_identifier",id);
        result.addProperty("display_name","Keep metadata for "+id);return result;
    }
    static String mapping(JsonArray definitions) {
        JsonObject root=new JsonObject(),items=new JsonObject();root.addProperty("format_version",2);
        root.add("items",items);items.add("minecraft:heart_of_the_sea",definitions);return root.toString();
    }
    static void pack(Path file,String uuid)throws Exception {
        try(var zip=new ZipOutputStream(Files.newOutputStream(file))) {
            zip.putNextEntry(new ZipEntry("manifest.json"));
            zip.write(("{\"header\":{\"uuid\":\""+uuid+"\"}}").getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zip.closeEntry();
        }
    }
    static Path cleanup(String label,Cleaner cleaner)throws Exception {
        Path root=Files.createTempDirectory("geyser-cleanup-"+label+"-");
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
        JsonArray retired=new JsonArray(),retiredMixed=new JsonArray(),preserved=new JsonArray();
        for(String spell:RETIRED) {
            retired.add(definition("advance_magic:core_"+spell));
            retiredMixed.add(definition("advance_magic:core_"+spell));
        }
        for(String id:PRESERVED){retiredMixed.add(definition(id));preserved.add(definition(id));}
        String retiredOnly=mapping(retired),retiredAndLive=mapping(retiredMixed);
        Files.writeString(mappings.resolve("retired-only.json"),retiredOnly);
        Files.writeString(mappings.resolve("retired-mixed.json"),retiredAndLive);
        String unrelated=" \n"+mapping(preserved)+"\n";
        Files.writeString(mappings.resolve("unrelated.json"),unrelated);
        pack(bundlePack,"11111111-1111-1111-1111-111111111111");
        Files.copy(bundlePack,packs.resolve("canonical.mcpack"));Files.copy(bundlePack,packs.resolve("duplicate.mcpack"));
        pack(packs.resolve("other.mcpack"),"22222222-2222-2222-2222-222222222222");
        check(cleaner.clean(root,bundled,bundlePack,"canonical.json","canonical.mcpack")==4,label+": duplicate and retired files cleaned even though new bundle contains no retired IDs");
        var array=JsonParser.parseString(Files.readString(mappings.resolve("mixed.json"))).getAsJsonObject().getAsJsonObject("items").getAsJsonArray("minecraft:stick");
        check(array.size()==1&&array.get(0).getAsJsonObject().get("bedrock_identifier").getAsString().equals("another:untouched"),"mixed mapping preserves other plugin");
        check(Files.readString(mappings.resolve("canonical.json")).equals(canonical),"canonical mapping preserved");
        check(Files.exists(packs.resolve("canonical.mcpack"))&&Files.exists(packs.resolve("other.mcpack"))&&!Files.exists(packs.resolve("duplicate.mcpack")),"only duplicate UUID removed from scan");
        JsonArray cleanedRetired=JsonParser.parseString(Files.readString(mappings.resolve("retired-only.json")))
            .getAsJsonObject().getAsJsonObject("items").getAsJsonArray("minecraft:heart_of_the_sea");
        check(cleanedRetired.isEmpty(),label+": all 21 retired-only definitions removed");
        JsonArray cleanedMixed=JsonParser.parseString(Files.readString(mappings.resolve("retired-mixed.json")))
            .getAsJsonObject().getAsJsonObject("items").getAsJsonArray("minecraft:heart_of_the_sea");
        check(cleanedMixed.equals(preserved),label+": mixed mapping retains active cores, unrelated metadata and near-miss identifiers");
        check(Files.readString(mappings.resolve("unrelated.json")).equals(unrelated),label+": unrelated mappings are byte-identical");
        List<Path> backups;
        try(var files=Files.list(root.resolve("plugin-pack-backups"))){backups=files.toList();}
        check(backups.size()==4,label+": every changed original backed up");
        for(String original:List.of(mixed,retiredOnly,retiredAndLive)) {
            boolean found=false;
            for(Path backup:backups)if(Arrays.equals(Files.readAllBytes(backup),original.getBytes(java.nio.charset.StandardCharsets.UTF_8)))found=true;
            check(found,label+": mapping backup retains exact original bytes");
        }
        boolean archiveBackedUp=false;
        for(Path backup:backups)if(Arrays.equals(Files.readAllBytes(backup),Files.readAllBytes(bundlePack)))archiveBackedUp=true;
        check(archiveBackedUp,label+": removed duplicate archive backed up exactly");
        check(Files.readString(mappings.resolve("malformed.json")).equals("{"),"malformed unrelated file untouched");
        check(cleaner.clean(root,bundled,bundlePack,"canonical.json","canonical.mcpack")==0,label+": cleanup idempotent");
        try(var files=Files.list(root.resolve("plugin-pack-backups"))){check(files.count()==4,label+": idempotent cleanup creates no extra backups");}
        return root;
    }
    public static void main(String[] args)throws Exception {
        Path root=cleanup("advance-magic",GeyserPackCleanup::clean);
        cleanup("evergarden",com.example.voidscape.pack.GeyserPackCleanup::clean);
        if(Arrays.asList(args).contains("--cleanup-only")) {
            System.out.println("PASS: "+checks+" exact retired-core and duplicate cleanup checks for both modules");return;
        }
        Path config=root.resolve("config.yml");
        Files.writeString(config,"gameplay:\n  enable-custom-content: false\n  force-resource-packs: false\n");
        check(GeyserPackSetup.enableCustomContent(root),"disabled custom content enabled");
        String updated=Files.readString(config);
        check(updated.contains("enable-custom-content: true")&&updated.contains("force-resource-packs: false"),"unrelated config preserved");
        try(var files=Files.list(root.resolve("plugin-pack-backups"))){check(files.count()==5,"config backed up");}
        check(!GeyserPackSetup.enableCustomContent(root),"config update idempotent");
        Path fresh=Files.createTempDirectory("geyser-fresh-check-");
        check(GeyserPackSetup.enableCustomContent(fresh)&&Files.readString(fresh.resolve("config.yml")).contains("enable-custom-content: true"),"fresh Geyser config enabled");
        System.out.println("PASS: "+checks+" Geyser retired-core cleanup and custom content setup checks");
    }
}
