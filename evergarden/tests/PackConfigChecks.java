package com.example.voidscape.pack;

import org.bukkit.configuration.file.YamlConfiguration;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

public final class PackConfigChecks {
    public static void main(String[] args) throws Exception {
        Path pack=args.length>0?Path.of(args[0]):Path.of("evergarden/dist/evergarden-java.zip");
        if(!Files.isRegularFile(pack)&&args.length==0)pack=Path.of("dist/evergarden-java.zip");
        String embedded=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(Files.readAllBytes(pack)));
        for(String ref:new String[]{"ecec8fa","DEV","main","0e6a864979528b50e00e48baa98e267993803ba4"}) {
            var config=new YamlConfiguration();
            config.set("resource-pack.url","https://raw.githubusercontent.com/40000years/Afterdeath/"+ref+"/evergarden/dist/evergarden-java.zip");
            config.set("resource-pack.sha1","5391fe0c4f449ed4dc73e41e49a48136638b9393");
            config.set("resource-pack.required",true);
            config.set("resource-pack.host.port",8298);
            config.set("resource-pack.host.bind","127.0.0.1");
            config.set("resource-pack.host.public-host","packs.example.org");
            config.set("resource-pack.host.public-url","https://proxy.example.org/garden");
            check(ResourcePackService.migratePackConfig(config,embedded),"Old official URL migrated: "+ref);
            var saved=new YamlConfiguration();
            saved.loadFromString(config.saveToString());
            check(saved.getString("resource-pack.url").isEmpty(),"Embedded delivery survives save/reload");
            check(saved.getString("resource-pack.sha1").isEmpty(),"Old hash replaced with embedded hash mode");
            check(saved.getBoolean("resource-pack.required"),"Other configuration preserved");
            check(saved.getBoolean("resource-pack.host.enabled"),"Embedded host enabled");
            check(saved.getInt("resource-pack.host.port")==8298,"Custom port preserved");
            check("127.0.0.1".equals(saved.getString("resource-pack.host.bind")),"Bind address preserved");
            check("packs.example.org".equals(saved.getString("resource-pack.host.public-host")),"Public host preserved");
            check("https://proxy.example.org/garden".equals(saved.getString("resource-pack.host.public-url")),"Reverse proxy preserved");
            check(embedded.equals(ResourcePackService.packDigest(saved,embedded)),"Select exact SHA-1 of the new pack bytes");
            check(!ResourcePackService.migratePackConfig(saved,embedded),"Migration is idempotent");
        }
        var pinned=new YamlConfiguration();pinned.set("resource-pack.url",ResourcePackService.DEFAULT_CDN_URL);
        pinned.set("resource-pack.sha1","764b727583b67d39a9445ce5bb99fc243f16d683");
        pinned.set("resource-pack.host.enabled",false);
        check(ResourcePackService.migratePackConfig(pinned,embedded),"Previously current managed CDN also upgrades to new artwork");
        check(embedded.equals(ResourcePackService.packDigest(pinned,embedded)),"A former pinned CDN cannot select its stale hash");

        for(String url:new String[]{"", "https://cdn.example.org/custom.zip", "http://localhost:8188/pack.zip",
                "https://raw.githubusercontent.com/someone/Afterdeath/DEV/evergarden/dist/evergarden-java.zip",
                "https://raw.githubusercontent.com/40000years/Evergarden2/main/evergarden/dist/custom.zip"}) {
            var config=new YamlConfiguration();
            config.set("resource-pack.url",url);
            String customDigest="1111111111111111111111111111111111111111";
            config.set("resource-pack.sha1",customDigest);
            config.set("resource-pack.host.enabled",false);config.set("resource-pack.enabled",false);
            String before=config.saveToString();
            check(!ResourcePackService.migratePackConfig(config,embedded),"Non-legacy settings unchanged");
            check(before.equals(config.saveToString()),"No configuration data changed");
            check((url.isEmpty()?embedded:customDigest).equals(ResourcePackService.packDigest(config,embedded)),"Custom CDN selects its own hash; local bundle selects actual bytes");
        }
        var missing=new YamlConfiguration();missing.set("resource-pack.url",ResourcePackService.DEFAULT_CDN_URL);
        String missingSnapshot=missing.saveToString();
        check(!ResourcePackService.migratePackConfig(missing,""),"Missing embedded bytes must not migrate official URL");
        check(missingSnapshot.equals(missing.saveToString()),"Failed bundle leaves saved config intact");
        var external=new YamlConfiguration();external.set("resource-pack.url","https://cdn.example.org/custom.zip");
        check(ResourcePackService.packDigest(external,embedded).isEmpty(),"External CDN without its own hash cannot borrow the bundle hash");
        var staleLocal=new YamlConfiguration();staleLocal.set("resource-pack.url","");
        staleLocal.set("resource-pack.sha1","0000000000000000000000000000000000000000");
        check(embedded.equals(ResourcePackService.packDigest(staleLocal,embedded)),"Saved obsolete digest cannot override embedded content");
        System.out.println("PASS: official Evergarden URLs select exact embedded pack SHA-1 "+embedded+"; custom CDN/host/proxy settings are preserved");
    }
    private static void check(boolean condition,String message) {
        if(!condition)throw new AssertionError(message);
    }
}
