import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * e2.187: the admin test menu's Charm page. Reads the charm ids through AdminTestGui's ForgeApi bridge,
 * makes every charm at every grade and checks the forge data, and checks the page goes quiet (no ids,
 * null items) once the forge is disabled. The e2.180 gem/material bridge is re-checked too.
 */
public final class CharmTestMenuChecks extends JavaPlugin {
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
                result = "PASS " + passed.size() + " charm test menu checks\n" + String.join("\n", passed);
            } catch (Throwable error) {
                result = "FAIL " + error + "\npassed before failure: " + passed;
            }
            try {
                Files.writeString(Path.of("charm-test-menu-result.txt"), result);
            } catch (Exception ignored) {
            }
            Bukkit.getScheduler().runTaskLater(this, Bukkit::shutdown, 20L);
        }, 60L);
    }

    private static String data(ItemStack item, String key) {
        return item == null || !item.hasItemMeta() ? null
            : item.getItemMeta().getPersistentDataContainer().get(new NamespacedKey("evergardenforge", key), PersistentDataType.STRING);
    }

    @SuppressWarnings("unchecked")
    private void run() throws Exception {
        Plugin garden = Bukkit.getPluginManager().getPlugin("Evergarden");
        Plugin forge = Bukkit.getPluginManager().getPlugin("EvergardenForge");
        check(garden != null && garden.isEnabled(), "Evergarden enabled: " + (garden == null ? "-" : garden.getPluginMeta().getVersion()));
        check(forge != null && forge.isEnabled() && forge.getPluginMeta().getVersion().startsWith("0.7."),
            "EvergardenForge 0.7.x enabled: " + (forge == null ? "-" : forge.getPluginMeta().getVersion()));
        Class<?> guiClass = Class.forName("com.example.voidscape.gui.AdminTestGui", true, garden.getClass().getClassLoader());
        Object gui = guiClass.getConstructor(garden.getClass()).newInstance(garden);
        Method bridge = guiClass.getDeclaredMethod("forgeItem", String.class, Object[].class);
        Method ids = guiClass.getDeclaredMethod("forgeCharmIds");
        bridge.setAccessible(true);
        ids.setAccessible(true);
        check(guiClass.getMethod("openCharmTest", org.bukkit.entity.Player.class, int.class) != null, "openCharmTest(Player, grade) exists");
        List<String> charms = (List<String>) ids.invoke(gui);
        check(charms.size() == 27 && new HashSet<>(charms).size() == 27, "27 charm ids through the bridge");
        check(charms.getFirst().equals("shadow_step") && charms.get(9).equals("starwalker") && charms.get(18).equals("time_stop"),
            "rows follow the sanctums: dark, astral, time");
        int made = 0;
        for (String rarity : new String[]{"COMMON", "UNCOMMON", "RARE", "EPIC", "MYTHIC"}) {
            for (String id : charms) {
                ItemStack item = (ItemStack) bridge.invoke(gui, "charm", new Object[]{id, rarity});
                if (!id.equals(data(item, "charm")) || !rarity.equals(data(item, "charm_grade")) || item.getMaxStackSize() != 1
                    || !item.getItemMeta().getItemModel().equals(new NamespacedKey("evergarden_forge", "charm/" + id)))
                    throw new AssertionError("charm " + id + " " + rarity + " -> " + data(item, "charm") + "/" + data(item, "charm_grade"));
                made++;
            }
        }
        check(made == 135, "135 charm items (27 x 5 grades) with forge data and models");
        check(bridge.invoke(gui, "charm", new Object[]{"no_such_charm", "RARE"}) == null, "unknown charm id gives null");
        ItemStack gem = (ItemStack) bridge.invoke(gui, "gem", new Object[]{"fire", 3, 2});
        ItemStack dust = (ItemStack) bridge.invoke(gui, "dust", new Object[]{16});
        check(gem != null && gem.getAmount() == 2 && dust != null && dust.getAmount() == 16, "gem and dust page still works");
        Bukkit.getPluginManager().disablePlugin(forge);
        check(((List<String>) ids.invoke(gui)).isEmpty() && bridge.invoke(gui, "charm", new Object[]{"timeless", "RARE"}) == null,
            "forge disabled: no charm ids, bridge returns null");
    }
}
