package com.example.voidscape.pack;

import org.bukkit.configuration.file.YamlConfiguration;

public final class PackConfigChecks {
    public static void main(String[] args) throws Exception {
        for(String ref:new String[]{"ecec8fa","DEV","main"}) {
            var config=new YamlConfiguration();
            config.set("resource-pack.url","https://raw.githubusercontent.com/40000years/Afterdeath/"+ref+"/evergarden/dist/evergarden-java.zip");
            config.set("resource-pack.sha1","5391fe0c4f449ed4dc73e41e49a48136638b9393");
            config.set("resource-pack.required",true);
            check(ResourcePackService.migratePackConfig(config),"Old official URL migrated: "+ref);
            var saved=new YamlConfiguration();
            saved.loadFromString(config.saveToString());
            check(ResourcePackService.DEFAULT_CDN_URL.equals(saved.getString("resource-pack.url")),"Pinned URL survives save/reload");
            check(saved.getString("resource-pack.sha1").isEmpty(),"Old hash replaced with embedded hash mode");
            check(saved.getBoolean("resource-pack.required"),"Other configuration preserved");
            check(!ResourcePackService.migratePackConfig(saved),"Migration is idempotent");
        }
        for(String url:new String[]{"", "https://cdn.example.org/custom.zip", "http://localhost:8188/pack.zip",
                "https://raw.githubusercontent.com/someone/Afterdeath/DEV/evergarden/dist/evergarden-java.zip",
                ResourcePackService.DEFAULT_CDN_URL}) {
            var config=new YamlConfiguration();
            config.set("resource-pack.url",url);
            config.set("resource-pack.sha1","administrator-value");
            String before=config.saveToString();
            check(!ResourcePackService.migratePackConfig(config),"Non-legacy settings unchanged");
            check(before.equals(config.saveToString()),"No configuration data changed");
        }
        String key="compatibility.aeternum-seasons.resource-pack";
        for(String url:new String[]{
                "https://cdn.modrinth.com/data/4hkZZzlQ/versions/VveNYYee/Aeternum-Foods-26.x.zip",
                "https://raw.githubusercontent.com/40000years/Afterdeath/DEV/evergarden/dist/Aeternum-Foods-26.x.zip"}) {
            var config=new YamlConfiguration();
            config.set(key+".url",url);config.set(key+".sha1","old-hash");config.set(key+".enabled",false);
            check(ResourcePackService.migrateAeternumPackConfig(config),"Legacy Aeternum URL migrated to GitHub");
            var saved=new YamlConfiguration();saved.loadFromString(config.saveToString());
            check(ResourcePackService.AETERNUM_PACK_URL.equals(saved.getString(key+".url")),"GitHub URL persisted");
            check(ResourcePackService.AETERNUM_PACK_SHA1.equals(saved.getString(key+".sha1")),"Matched Aeternum hash persisted");
            check(!saved.getBoolean(key+".enabled"),"Disabled integration stays disabled");
            check(!ResourcePackService.migrateAeternumPackConfig(saved),"Aeternum migration is idempotent");
        }
        for(String url:new String[]{ResourcePackService.AETERNUM_PACK_URL,"https://example.org/custom.zip",
                "https://cdn.modrinth.com/data/other/versions/custom/Aeternum-Foods-26.x.zip"}) {
            var config=new YamlConfiguration();config.set(key+".url",url);config.set(key+".sha1","custom-hash");
            String before=config.saveToString();
            check(!ResourcePackService.migrateAeternumPackConfig(config),"Other Aeternum sources preserved");
            check(before.equals(config.saveToString()),"Custom URL/hash unchanged");
        }
        System.out.println("Pack configuration migration checks passed.");
    }
    private static void check(boolean condition,String message) {
        if(!condition)throw new AssertionError(message);
    }
}
