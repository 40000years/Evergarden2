package com.example.advancemagic.pack;

import org.bukkit.configuration.file.YamlConfiguration;

public final class PackConfigChecks {
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}

    public static void main(String[] args) {
        var old=new YamlConfiguration();
        old.set("resource-pack.url","https://raw.githubusercontent.com/40000years/Evergarden2/07dcac3/advance-magic/dist/advance-magic-java.zip");
        old.set("resource-pack.sha1","0000000000000000000000000000000000000000");
        check(ResourcePackService.migratePackConfig(old),"old official URL migrated");
        check(old.getString("resource-pack.url").isEmpty(),"old official URL no longer serves obsolete pack");
        check(old.getString("resource-pack.sha1").isEmpty(),"bundled pack SHA-1 is computed from bytes");
        check(old.getBoolean("resource-pack.host.enabled"),"bundled host enabled");
        check(!ResourcePackService.migratePackConfig(old),"migration is idempotent");

        var custom=new YamlConfiguration();
        custom.set("resource-pack.url","https://packs.example.org/magic.zip");
        custom.set("resource-pack.sha1","1111111111111111111111111111111111111111");
        check(!ResourcePackService.migratePackConfig(custom),"custom URL is preserved");
        check("1111111111111111111111111111111111111111".equals(custom.getString("resource-pack.sha1")),"custom SHA-1 is preserved");
        System.out.println("PASS: previous official pack migrates to bundled host; custom CDN is preserved");
    }
}
