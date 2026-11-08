package com.example.voidscape.pet;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * The bundled pet blueprints. A release JAR keeps each pet PNG once, in native-pets.zip: its blueprints carry a
 * token where the base64 data URI was, and blueprint-textures.json maps each token back and records the original
 * size and SHA-256 (tools/pet_blueprint_slim.py). The original bytes are rebuilt only when a blueprint really has
 * to be written, and must match that SHA-256. A JAR without the index carries complete blueprints, used as is.
 * Click geometry (PetAim) reads only elements and outliner, so it works on either form.
 */
final class PetBlueprints implements AutoCloseable {
    private static final String INDEX="modelengine/pets/blueprint-textures.json";
    private record Entry(String sha256,long size,Map<String,String> textures){}
    private final JavaPlugin plugin;
    private final String archive;
    private final Map<String,Entry> models=new HashMap<>();
    private Map<String,byte[]> pngs;

    PetBlueprints(JavaPlugin plugin)throws IOException {
        this.plugin=plugin;
        try(InputStream stream=plugin.getResource(INDEX)) {
            if(stream==null){archive=null;return;}
            JsonObject root=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();
            if(root.get("format").getAsInt()!=1)throw new IOException("Unsupported "+INDEX+" format");
            archive=root.get("archive").getAsString();
            for(var model:root.getAsJsonObject("models").entrySet()) {
                JsonObject value=model.getValue().getAsJsonObject();
                Map<String,String> textures=new LinkedHashMap<>();
                for(var texture:value.getAsJsonObject("textures").entrySet())textures.put(texture.getKey(),texture.getValue().getAsString());
                models.put(model.getKey(),new Entry(value.get("sha256").getAsString(),value.get("size").getAsLong(),textures));
            }
        }
    }
    /** SHA-256 of the complete blueprint; read from the index without touching the blueprint when recorded. */
    String hash(String id)throws IOException {
        Entry entry=models.get(id);
        return entry!=null?entry.sha256():PetBlueprintInstaller.digest(bytes(id));
    }
    /** The complete original blueprint, byte for byte. */
    byte[] bytes(String id)throws IOException {
        byte[] bundled;
        try(InputStream input=plugin.getResource("modelengine/pets/"+id+".bbmodel")) {
            if(input==null)throw new IOException("Missing bundled pet blueprint "+id);
            bundled=input.readAllBytes();
        }
        Entry entry=models.get(id);
        if(entry==null)return bundled;
        // Tokens and base64 are ASCII; Latin-1 maps every byte to one char and back, so UTF-8 text is untouched.
        StringBuilder text=new StringBuilder(new String(bundled,StandardCharsets.ISO_8859_1));
        for(var texture:entry.textures().entrySet()) {
            String token=texture.getKey();int at=text.indexOf(token);
            if(at<0)throw new IOException("Pet blueprint "+id+" lacks texture token "+token);
            String uri="data:image/png;base64,"+Base64.getEncoder().encodeToString(png(texture.getValue()));
            for(;at>=0;at=text.indexOf(token,at+uri.length()))text.replace(at,at+token.length(),uri);
        }
        byte[] restored=text.toString().getBytes(StandardCharsets.ISO_8859_1);
        if(restored.length!=entry.size()||!PetBlueprintInstaller.digest(restored).equals(entry.sha256()))
            throw new IOException("Pet blueprint "+id+" did not restore to its bundled original");
        return restored;
    }
    /** One pass over native-pets.zip collects every pet texture; only needed when a blueprint is written. */
    private byte[] png(String path)throws IOException {
        if(pngs==null) {
            Set<String> wanted=new HashSet<>();
            for(Entry entry:models.values())wanted.addAll(entry.textures().values());
            Map<String,byte[]> found=new HashMap<>();
            try(InputStream raw=plugin.getResource(archive)) {
                if(raw==null)throw new IOException("Missing bundled "+archive);
                try(ZipInputStream zip=new ZipInputStream(new BufferedInputStream(raw))) {
                    for(ZipEntry entry;(entry=zip.getNextEntry())!=null;)
                        if(wanted.contains(entry.getName()))found.put(entry.getName(),zip.readAllBytes());
                }
            }
            pngs=found;
        }
        byte[] data=pngs.get(path);
        if(data==null)throw new IOException(archive+" lacks pet texture "+path);
        return data;
    }
    @Override public void close(){pngs=null;}
}
