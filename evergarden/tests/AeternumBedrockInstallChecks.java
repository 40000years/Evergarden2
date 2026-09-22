package com.example.voidscape.pack;

import org.bukkit.configuration.file.YamlConfiguration;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;

public final class AeternumBedrockInstallChecks {
    public static void main(String[] args) throws Exception {
        Path root=Files.createTempDirectory("aeternum-install-check-");
        Path assets=root.resolve("assets");Files.createDirectories(assets);
        for(String name:AeternumBedrockInstaller.FILES) {
            try(var input=AeternumBedrockInstallChecks.class.getResourceAsStream("/aeternum-bedrock/"+name)) {
                if(input==null)throw new AssertionError("Missing packaged asset "+name);
                Files.copy(input,assets.resolve(name));
            }
        }
        Path plugins=root.resolve("plugins");Files.createDirectories(plugins);
        check(!AeternumBedrockInstaller.isPresent(plugins),"No plugin detected when absent");
        try(var jar=new JarOutputStream(Files.newOutputStream(plugins.resolve("renamed-plugin.jar")))) {
            jar.putNextEntry(new JarEntry("plugin.yml"));
            jar.write("name: 'AeternumSeasons'\nversion: '4.7'\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            jar.closeEntry();
        }
        check(AeternumBedrockInstaller.isPresent(plugins),"Plugin detected by descriptor before enable, regardless of filename");
        Path geyser=plugins.resolve("Geyser-Spigot");
        check(AeternumBedrockInstaller.install(geyser,assets)==3,"Fresh Geyser receives all three assets");
        for(String name:AeternumBedrockInstaller.FILES) {
            Path target=geyser.resolve(name.endsWith(".mcpack")?"packs":"custom_mappings").resolve(name);
            check(Arrays.equals(Files.readAllBytes(target),Files.readAllBytes(assets.resolve(name))),"Installed bytes match bundle: "+name);
        }
        Path config=geyser.resolve("config.yml");
        check(YamlConfiguration.loadConfiguration(config.toFile()).getBoolean("gameplay.enable-custom-content"),"Fresh config enables custom content");
        Files.writeString(config,"bedrock:\n  port: 19199\ngameplay:\n  enable-custom-content: false\n  force-resource-packs: false\n");
        String before=Files.readString(config);
        check(AeternumBedrockInstaller.install(geyser,assets)==0,"Existing assets are not replaced");
        var yaml=YamlConfiguration.loadConfiguration(config.toFile());
        check(yaml.getBoolean("gameplay.enable-custom-content"),"Explicitly disabled custom content is enabled");
        check(yaml.getInt("bedrock.port")==19199&&!yaml.getBoolean("gameplay.force-resource-packs"),"Other settings preserved");
        try(var backups=Files.list(geyser.resolve("plugin-pack-backups"))) {
            var paths=backups.toList();
            check(paths.size()==1&&Files.readString(paths.getFirst()).equals(before),"Exact original configuration backed up");
        }
        Path custom=geyser.resolve("custom_mappings/aeternum_food_items.json");
        Files.writeString(custom,"{\"customized\":true}");
        Path missing=geyser.resolve("custom_mappings/aeternum_food_blocks.json");Files.delete(missing);
        String configBefore=Files.readString(config);
        check(AeternumBedrockInstaller.install(geyser,assets)==1,"Partial installation repaired");
        check(Files.readString(custom).equals("{\"customized\":true}"),"Customized mappings preserved");
        check(Files.readString(config).equals(configBefore),"Repeated install leaves enabled config unchanged");
        System.out.println("Aeternum Bedrock installation checks passed.");
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
