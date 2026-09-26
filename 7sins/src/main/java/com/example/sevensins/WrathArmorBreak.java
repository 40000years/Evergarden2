package com.example.sevensins;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.attribute.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import java.util.*;

/** Temporary modifiers, with rollback for cancelled damage and deterministic armor wear. */
public final class WrathArmorBreak implements Listener, AutoCloseable {
    public record Trial(Player player, Integer previousExpiry) {}
    private final SevenSinsPlugin plugin;
    private final NamespacedKey key;
    private final Map<UUID, Integer> expiry = new HashMap<>();
    private final double reduction, durability;
    private final int duration;

    WrathArmorBreak(SevenSinsPlugin plugin) {
        this.plugin = plugin; key = new NamespacedKey(plugin, "wrath_armor_break");
        reduction = bounded(plugin.getConfig().getDouble("wrath.armor-break.reduction", 0.6), 0, 0.95, 0.6);
        durability = bounded(plugin.getConfig().getDouble("wrath.armor-break.durability-percent", 25), 0, 100, 25) / 100;
        duration = (int) (bounded(plugin.getConfig().getDouble("wrath.armor-break.duration-seconds", 8), 1, 60, 8) * 20);
        for (Player p : Bukkit.getOnlinePlayers()) clear(p);
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }
    private static double bounded(double value, double min, double max, double fallback) {
        return Double.isFinite(value) ? Math.max(min, Math.min(max, value)) : fallback;
    }
    public Trial begin(Player player) {
        Integer old = expiry.get(player.getUniqueId());
        for (Attribute type : List.of(Attribute.ARMOR, Attribute.ARMOR_TOUGHNESS)) {
            AttributeInstance attribute = player.getAttribute(type);
            if (attribute == null) continue;
            if (attribute.getModifiers().stream().noneMatch(m -> m.getKey().equals(key)))
                attribute.addTransientModifier(new AttributeModifier(key, -reduction, AttributeModifier.Operation.MULTIPLY_SCALAR_1));
        }
        return new Trial(player, old);
    }
    public void finish(Trial trial, boolean landed) {
        Player player = trial.player;
        if (!landed || player.isDead()) {
            if (trial.previousExpiry == null || player.isDead()) clear(player);
            return;
        }
        expiry.put(player.getUniqueId(), Bukkit.getCurrentTick() + duration);
        wear(player);
        player.sendActionBar(Component.text("เกราะแตก! เกราะลด " + Math.round(reduction*100)
                + "% · " + duration/20 + " วินาที · ความทนทานเกราะ −" + Math.round(durability*100) + "%", NamedTextColor.RED));
    }
    private void wear(Player player) {
        ItemStack[] armor = player.getInventory().getArmorContents();
        for (int i = 0; i < armor.length; i++) {
            ItemStack item = armor[i];
            if (item == null || !(item.getItemMeta() instanceof Damageable meta) || meta.isUnbreakable()) continue;
            int max = meta.hasMaxDamage() ? meta.getMaxDamage() : item.getType().getMaxDurability();
            if (max <= 0) continue;
            int wear = (int) Math.ceil(max * durability);
            if (wear <= 0) continue;
            PlayerItemDamageEvent event = new PlayerItemDamageEvent(player, item, wear, wear);
            Bukkit.getPluginManager().callEvent(event);
            if (event.isCancelled() || event.getDamage() <= 0) continue;
            if ((long) meta.getDamage() + event.getDamage() >= max) {
                Bukkit.getPluginManager().callEvent(new PlayerItemBreakEvent(player, item));
                armor[i] = null;
                player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1, 0.8f);
            } else { meta.setDamage(meta.getDamage() + event.getDamage()); item.setItemMeta(meta); }
        }
        player.getInventory().setArmorContents(armor);
    }
    public void tick() {
        int now = Bukkit.getCurrentTick();
        for (UUID id : List.copyOf(expiry.keySet())) {
            Player player = Bukkit.getPlayer(id);
            if (player == null) expiry.remove(id);
            else if (expiry.get(id) <= now) clear(player);
        }
    }
    public void clear(Player player) {
        expiry.remove(player.getUniqueId());
        for (Attribute type : List.of(Attribute.ARMOR, Attribute.ARMOR_TOUGHNESS)) {
            AttributeInstance attribute = player.getAttribute(type);
            if (attribute != null) for (AttributeModifier modifier : List.copyOf(attribute.getModifiers()))
                if (modifier.getKey().equals(key)) attribute.removeModifier(modifier);
        }
    }
    @EventHandler public void join(PlayerJoinEvent event) { clear(event.getPlayer()); }
    @EventHandler public void quit(PlayerQuitEvent event) { clear(event.getPlayer()); }
    @EventHandler public void death(PlayerDeathEvent event) { clear(event.getEntity()); }
    @Override public void close() { for (Player player : Bukkit.getOnlinePlayers()) clear(player); expiry.clear(); }
}
