import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * e2.180: the admin test menu's EvergardenForge page. Calls AdminTestGui.forgeItem (the reflection
 * bridge into ForgeApi) for every gem type and purity, every material grade and gem dust, and checks the
 * items carry the forge's own data. Also checks the bridge returns null instead of failing when the forge is off.
 */
public final class ForgeTestMenuChecks extends JavaPlugin {
    private final List<String> passed = new ArrayList<>();

    private void check(boolean condition, String name) {
        if (!condition) throw new AssertionError(name);
        passed.add(name);
    }

    @Override
    public void onEnable() {
        Bukkit.getScheduler().runTaskLater(this, () -> {
            String result;
            try {
                run();
                result = "PASS " + passed.size() + " forge test menu checks\n" + String.join("\n", passed);
            } catch (Throwable error) {
                result = "FAIL " + error + "\npassed before failure: " + passed;
            }
            try {
                Files.writeString(Path.of("forge-test-menu-result.txt"), result);
            } catch (Exception ignored) {
            }
            Bukkit.getScheduler().runTaskLater(this, Bukkit::shutdown, 20L);
        }, 60L);
    }

    private static boolean has(ItemStack item, String key) {
        return item != null && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().getKeys().stream()
            .anyMatch(k -> k.getNamespace().equals("evergardenforge") && k.getKey().equals(key));
    }

    private void run() throws Exception {
        Plugin garden = Bukkit.getPluginManager().getPlugin("Evergarden");
        Plugin forge = Bukkit.getPluginManager().getPlugin("EvergardenForge");
        check(garden != null && garden.isEnabled(), "Evergarden enabled: " + (garden == null ? "-" : garden.getPluginMeta().getVersion()));
        check(forge != null && forge.isEnabled(), "EvergardenForge enabled: " + (forge == null ? "-" : forge.getPluginMeta().getVersion()));
        Class<?> guiClass = Class.forName("com.example.voidscape.gui.AdminTestGui", true, garden.getClass().getClassLoader());
        Object gui = guiClass.getConstructor(garden.getClass()).newInstance(garden);
        Method bridge = guiClass.getDeclaredMethod("forgeItem", String.class, Object[].class);
        bridge.setAccessible(true);
        String[] gems = {"fire", "water", "wind", "earth", "lightning", "light", "dark", "life"};
        int gemItems = 0;
        for (String gem : gems) {
            for (int tier = 1; tier <= 5; tier++) {
                ItemStack item = (ItemStack) bridge.invoke(gui, "gem", new Object[]{gem, tier, 3});
                String stored = item == null ? null : item.getItemMeta().getPersistentDataContainer().getKeys().stream()
                    .filter(k -> k.getNamespace().equals("evergardenforge") && k.getKey().equals("gem"))
                    .map(k -> item.getItemMeta().getPersistentDataContainer().get(k, org.bukkit.persistence.PersistentDataType.STRING))
                    .findFirst().orElse(null);
                if (!(gem + ":" + tier).equals(stored) || item.getAmount() != 3) throw new AssertionError("gem " + gem + " " + tier + " -> " + stored);
                gemItems++;
            }
        }
        check(gemItems == 40, "40 gems (8 types x purity I-V) with forge data");
        for (String rarity : new String[]{"COMMON", "UNCOMMON", "RARE", "EPIC", "MYTHIC"}) {
            ItemStack item = (ItemStack) bridge.invoke(gui, "material", new Object[]{rarity, 4});
            check(has(item, "material") && item.getAmount() == 4, "material " + rarity);
        }
        ItemStack dust = (ItemStack) bridge.invoke(gui, "dust", new Object[]{16});
        check(has(dust, "gem_dust") && dust.getAmount() == 16, "gem dust x16");
        check(bridge.invoke(gui, "nope", new Object[]{1}) == null, "unknown forge method returns null, no exception");
        Bukkit.getPluginManager().disablePlugin(forge);
        check(bridge.invoke(gui, "dust", new Object[]{1}) == null, "forge disabled: bridge returns null");
    }
}
