package com.example.advancemagic.pack;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;

/** Enables the Geyser setting required for custom item mappings. */
public final class GeyserPackSetup {
    private GeyserPackSetup() {}

    /** Returns true when the Geyser config was changed. */
    public static boolean enableCustomContent(Path geyser) throws IOException {
        Path config=geyser.resolve("config.yml");
        if(!Files.exists(config)) {
            Files.createDirectories(geyser);
            Files.writeString(config,"gameplay:\n  enable-custom-content: true\n");
            return true;
        }
        var yaml=new YamlConfiguration();
        try {yaml.load(config.toFile());}
        catch(InvalidConfigurationException e){throw new IOException("Invalid Geyser config: "+config,e);}
        if(yaml.getBoolean("gameplay.enable-custom-content",false))return false;
        Path backup=geyser.resolve("plugin-pack-backups");
        Files.createDirectories(backup);
        Files.copy(config,backup.resolve("advance-magic-"+UUID.randomUUID()+"-config.yml"));
        yaml.set("gameplay.enable-custom-content",true);
        yaml.save(config.toFile());
        return true;
    }
}
