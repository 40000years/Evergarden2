package com.example.sevensins;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.attribute.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.*;
import java.util.*;

/** Hammer concussion: one second rooted, then two seconds slowed; only our modifiers are removed. */
public final class WrathTremor implements Listener, AutoCloseable {
    private record Effect(Player player, UUID boss, int rootedUntil, int expires, boolean slowed) {}
    private final Map<UUID, Effect> effects = new HashMap<>();
    private final NamespacedKey moveKey, jumpKey;

    WrathTremor(SevenSinsPlugin plugin) {
        moveKey = new NamespacedKey(plugin, "wrath_tremor_move");
        jumpKey = new NamespacedKey(plugin, "wrath_tremor_jump");
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }
    public void apply(Player player, UUID boss) {
        int now = Bukkit.getCurrentTick();
        effects.put(player.getUniqueId(), new Effect(player, boss, now + 20, now + 60, false));
        modifier(player, Attribute.MOVEMENT_SPEED, moveKey, -1);
        modifier(player, Attribute.JUMP_STRENGTH, jumpKey, -1);
        player.setSprinting(false);
        player.playHurtAnimation(0);
        player.playSound(player.getLocation(), Sound.ENTITY_IRON_GOLEM_STEP, 1.1f, 0.5f);
        player.sendActionBar(net.kyori.adventure.text.Component.text("แรงสะเทือน! ตรึง 1 วิ → ช้า 80% อีก 2 วิ"));
    }
    private void modifier(Player player, Attribute type, NamespacedKey key, double amount) {
        AttributeInstance attribute = player.getAttribute(type);
        if (attribute == null) return;
        attribute.removeModifier(key);
        attribute.addTransientModifier(new AttributeModifier(key, amount, AttributeModifier.Operation.MULTIPLY_SCALAR_1));
    }
    public void tick() { tick(Bukkit.getCurrentTick()); }
    void tick(int now) {
        for (Effect effect : List.copyOf(effects.values())) {
            Player player = effect.player();
            if (now >= effect.expires() || !player.isOnline() || player.isDead()) clear(player);
            else if (!effect.slowed() && now >= effect.rootedUntil()) {
                modifier(player, Attribute.MOVEMENT_SPEED, moveKey, -0.8);
                modifier(player, Attribute.JUMP_STRENGTH, jumpKey, -0.8);
                effects.put(player.getUniqueId(), new Effect(player, effect.boss(), effect.rootedUntil(), effect.expires(), true));
            }
        }
    }
    public void clear(Player player) {
        effects.remove(player.getUniqueId());
        AttributeInstance move = player.getAttribute(Attribute.MOVEMENT_SPEED), jump = player.getAttribute(Attribute.JUMP_STRENGTH);
        if (move != null) move.removeModifier(moveKey);
        if (jump != null) jump.removeModifier(jumpKey);
    }
    public void clearBoss(UUID boss) {
        for (Effect effect : List.copyOf(effects.values())) if (effect.boss().equals(boss)) clear(effect.player());
    }
    @EventHandler public void quit(PlayerQuitEvent event) { clear(event.getPlayer()); }
    @EventHandler public void death(PlayerDeathEvent event) { clear(event.getEntity()); }
    @EventHandler public void world(PlayerChangedWorldEvent event) { clear(event.getPlayer()); }
    @Override public void close() { for (Effect effect : List.copyOf(effects.values())) clear(effect.player()); }
}
