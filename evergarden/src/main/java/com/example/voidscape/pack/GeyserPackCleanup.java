package com.example.voidscape.pack;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;

/** Removes only identifiers owned by the bundled mapping; backs up every changed file. */
public final class GeyserPackCleanup {
    private GeyserPackCleanup() {}

    public static int clean(Path geyser, Path bundledMapping, Path bundledPack,
                            String mappingName, String packName) throws IOException {
        Set<String> owned=new HashSet<>();
        collect(new JsonParser().parse(Files.readString(bundledMapping)),owned);
        Path backup=geyser.resolve("plugin-pack-backups");
        int changed=0;
        Path mappings=geyser.resolve("custom_mappings");
        if(Files.isDirectory(mappings))try(var paths=Files.walk(mappings)) {
            for(Path path:paths.filter(Files::isRegularFile).filter(p->p.toString().endsWith(".json")).toList()) {
                if(path.equals(mappings.resolve(mappingName)))continue;
                JsonElement json;
                try {json=new JsonParser().parse(Files.readString(path));}
                catch(JsonParseException e){continue;}
                if(strip(json,owned)) {
                    backup(path,backup);
                    Files.writeString(path,new GsonBuilder().setPrettyPrinting().create().toJson(json),StandardCharsets.UTF_8);
                    changed++;
                }
            }
        }
        String uuid=uuid(bundledPack);
        Path packs=geyser.resolve("packs");
        if(uuid!=null&&Files.isDirectory(packs))try(var paths=Files.walk(packs)) {
            for(Path path:paths.filter(Files::isRegularFile).toList()) {
                if(path.equals(packs.resolve(packName)))continue;
                if(uuid.equalsIgnoreCase(Objects.toString(uuid(path),""))) {
                    Path saved=backup(path,backup);
                    // The copy has completed before the duplicate leaves Geyser's scan directory.
                    if(Files.size(saved)==Files.size(path))Files.delete(path);
                    changed++;
                }
            }
        }
        return changed;
    }

    private static Path backup(Path source,Path directory)throws IOException {
        Files.createDirectories(directory);
        Path target=directory.resolve(UUID.randomUUID()+"-"+source.getFileName());
        Files.copy(source,target);
        return target;
    }
    private static String uuid(Path path) {
        try(ZipFile zip=new ZipFile(path.toFile())) {
            var entry=zip.getEntry("manifest.json");
            if(entry==null)return null;
            try(var reader=new InputStreamReader(zip.getInputStream(entry),StandardCharsets.UTF_8)) {
                return new JsonParser().parse(reader).getAsJsonObject().getAsJsonObject("header").get("uuid").getAsString();
            }
        }catch(IOException|RuntimeException e){return null;}
    }
    private static void collect(JsonElement node,Set<String> ids) {
        if(node.isJsonArray())for(JsonElement child:node.getAsJsonArray())collect(child,ids);
        else if(node.isJsonObject()) {
            JsonObject object=node.getAsJsonObject();
            if(object.has("bedrock_identifier"))ids.add(object.get("bedrock_identifier").getAsString());
            for(var entry:object.entrySet())collect(entry.getValue(),ids);
        }
    }
    private static boolean strip(JsonElement node,Set<String> ids) {
        boolean changed=false;
        if(node.isJsonArray()) {
            for(var it=node.getAsJsonArray().iterator();it.hasNext();) {
                JsonElement child=it.next();
                if(child.isJsonObject()&&child.getAsJsonObject().has("bedrock_identifier")
                    &&ids.contains(child.getAsJsonObject().get("bedrock_identifier").getAsString())) {
                    it.remove();changed=true;
                }else changed|=strip(child,ids);
            }
        }else if(node.isJsonObject())for(var entry:node.getAsJsonObject().entrySet())changed|=strip(entry.getValue(),ids);
        return changed;
    }
}
