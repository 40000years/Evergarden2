package com.example.voidscape.pack;

import org.bukkit.configuration.file.YamlConfiguration;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.JarFile;

/** Installs the bundled official food pack before Geyser starts reading custom content. */
public final class AeternumBedrockInstaller {
    public static final List<String> FILES=List.of("Aeternum-Foods-Bedrock.mcpack",
            "aeternum_food_items.json","aeternum_food_blocks.json");
    private AeternumBedrockInstaller() {}

    public static boolean isPresent(Path plugins) throws IOException {
        // onLoad runs before Aeternum is enabled, so inspect descriptors instead of isPluginEnabled.
        try(var paths=Files.list(plugins)) {
            for(Path path:paths.filter(p->p.getFileName().toString().endsWith(".jar")).toList()) {
                try(JarFile jar=new JarFile(path.toFile())) {
                    var entry=jar.getJarEntry("plugin.yml");
                    if(entry==null)continue;
                    try(var reader=new InputStreamReader(jar.getInputStream(entry),java.nio.charset.StandardCharsets.UTF_8)) {
                        var descriptor=YamlConfiguration.loadConfiguration(reader);
                        if("AeternumSeasons".equals(descriptor.getString("name")))return true;
                    }
                }catch(IOException ignored){/* Ignore unrelated incomplete plugin downloads. */}
            }
        }
        return false;
    }

    public static int install(Path geyser,Path bundled) throws IOException {
        int installed=0;
        for(String name:FILES) {
            Path target=geyser.resolve(name.endsWith(".mcpack")?"packs":"custom_mappings").resolve(name);
            // Existing installations may contain newer or customized official assets.
            if(Files.exists(target))continue;
            Files.createDirectories(target.getParent());
            Files.copy(bundled.resolve(name),target);
            installed++;
        }
        Path config=geyser.resolve("config.yml");
        if(Files.exists(config)) {
            var yaml=new YamlConfiguration();
            try {yaml.load(config.toFile());}
            catch(org.bukkit.configuration.InvalidConfigurationException e){throw new IOException("Invalid Geyser config",e);}
            if(!yaml.getBoolean("gameplay.enable-custom-content",false)) {
                Path backup=geyser.resolve("plugin-pack-backups");
                Files.createDirectories(backup);
                Files.copy(config,backup.resolve("aeternum-"+UUID.randomUUID()+"-config.yml"));
                yaml.set("gameplay.enable-custom-content",true);
                yaml.save(config.toFile());
            }
        } else {
            // Geyser supplies the rest of its defaults on a fresh installation.
            Files.createDirectories(geyser);
            Files.writeString(config,"gameplay:\n  enable-custom-content: true\n");
        }
        return installed;
    }
}
