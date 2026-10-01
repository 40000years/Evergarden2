package com.example.advancemagic.pack;

import org.bukkit.configuration.file.YamlConfiguration;

public final class PackConfigChecks {
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static final String URL="https://raw.githubusercontent.com/40000years/Evergarden2/0e6a864979528b50e00e48baa98e267993803ba4/advance-magic/dist/advance-magic-java.zip";
    private static final String SHA1="278eef0ea167e62d84152f57bb32c60483b62e8f";
    private static YamlConfiguration config() {
        var defaults=new YamlConfiguration();defaults.set("resource-pack.url",URL);defaults.set("resource-pack.sha1",SHA1);
        var config=new YamlConfiguration();config.setDefaults(defaults);return config;
    }

    public static void main(String[] args) {
        var old=config();
        old.set("resource-pack.url","https://raw.githubusercontent.com/40000years/Evergarden2/07dcac3/advance-magic/dist/advance-magic-java.zip");
        old.set("resource-pack.sha1","0000000000000000000000000000000000000000");
        check(ResourcePackService.migratePackConfig(old,SHA1),"old official URL migrated");
        check(URL.equals(old.getString("resource-pack.url")),"published URL replaces obsolete pack");
        check(SHA1.equals(old.getString("resource-pack.sha1")),"published SHA-1 is pinned");
        check(!old.getBoolean("resource-pack.host.enabled"),"bundled host disabled");
        check(!ResourcePackService.migratePackConfig(old,SHA1),"migration is idempotent");

        var legacy=config();legacy.set("resource-pack.url","");legacy.set("resource-pack.sha1","");
        legacy.set("resource-pack.host.enabled",true);
        check(ResourcePackService.migratePackConfig(legacy,SHA1),"default bundled host migrated");
        check(URL.equals(legacy.getString("resource-pack.url")),"legacy installation uses public URL");
        check(SHA1.equals(legacy.getString("resource-pack.sha1")),"legacy installation pins published pack");

        var current=config();
        check(!ResourcePackService.migratePackConfig(current,SHA1),"matching official pack is preserved");

        var custom=config();
        custom.set("resource-pack.url","https://packs.example.org/magic.zip");
        custom.set("resource-pack.sha1","1111111111111111111111111111111111111111");
        check(!ResourcePackService.migratePackConfig(custom,SHA1),"custom URL is preserved");
        check("1111111111111111111111111111111111111111".equals(custom.getString("resource-pack.sha1")),"custom SHA-1 is preserved");
        var customHost=config();customHost.set("resource-pack.url","");customHost.set("resource-pack.host.enabled",true);
        customHost.set("resource-pack.host.public-host","packs.example.org");
        check(!ResourcePackService.migratePackConfig(customHost,SHA1),"custom public host is preserved");
        var changedPack=config();changedPack.set("resource-pack.url","");changedPack.set("resource-pack.host.enabled",true);
        check(!ResourcePackService.migratePackConfig(changedPack,"0000000000000000000000000000000000000000"),"stale published pack is not selected");
        System.out.println("PASS: legacy pack delivery migrates to the published Java pack; custom hosts and CDNs are preserved");
    }
}
