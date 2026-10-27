import com.example.advancemagic.pack.delivery.RequiredPackSettings;
import org.bukkit.configuration.file.YamlConfiguration;

public final class RequiredPackSettingsChecks {
    public static void main(String[] args) {
        for(boolean judge:new boolean[]{false,true}) {
            var config=new YamlConfiguration();
            config.set("resource-pack.enabled",false);
            config.set("resource-pack.required",false);
            config.set("resource-pack.url","https://cdn.example.org/custom.zip");
            config.set("resource-pack.sha1","1234567890123456789012345678901234567890");
            config.set("resource-pack.host.port",8299);
            config.set("resource-pack.host.public-url","https://proxy.example.org/packs");
            config.set("world-boss.visuals.pack.enabled",false);
            config.set("world-boss.visuals.pack.required",false);
            config.set("world-boss.visuals.pack.url","https://cdn.example.org/judge.zip");
            check(RequiredPackSettings.upgrade(config,judge),"existing optional configuration upgrades");
            check(config.getBoolean("resource-pack.enabled")&&config.getBoolean("resource-pack.required"),"main pack enabled and required");
            check(config.getBoolean("world-boss.visuals.pack.required")==judge,"Judge required only in Evergarden");
            check(config.getBoolean("world-boss.visuals.pack.enabled")==judge,"Judge enabled only in Evergarden");
            check(config.getString("resource-pack.url").equals("https://cdn.example.org/custom.zip"),"CDN preserved");
            check(config.getString("resource-pack.sha1").equals("1234567890123456789012345678901234567890"),"hash preserved");
            check(config.getInt("resource-pack.host.port")==8299,"port preserved");
            check(config.getString("resource-pack.host.public-url").equals("https://proxy.example.org/packs"),"proxy preserved");
            check(config.getString("world-boss.visuals.pack.url").equals("https://cdn.example.org/judge.zip"),"Judge CDN preserved");
            String saved=config.saveToString();
            check(!RequiredPackSettings.upgrade(config,judge)&&saved.equals(config.saveToString()),"migration is idempotent");
        }
        System.out.println("PASS: required-pack migration upgrades existing installs and preserves download settings");
    }
    private static void check(boolean result,String message){if(!result)throw new AssertionError(message);}
}
