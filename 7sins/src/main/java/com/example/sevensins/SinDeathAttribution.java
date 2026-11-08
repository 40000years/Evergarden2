package com.example.sevensins;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Names only direct boss attacks and damaging effects that a sin actually applied. */
final class SinDeathAttribution implements Listener, AutoCloseable {
    private record Effect(SinType sin, UUID world, long expires) {}
    private final SevenSinsPlugin plugin;
    private final Map<UUID, Map<PotionEffectType, Effect>> effects = new HashMap<>();
    private boolean applying;
    private UUID applyingPlayer;
    private PotionEffectType applyingType;
    private boolean changedActiveEffect;

    SinDeathAttribution(SevenSinsPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    void apply(Player player, PotionEffect effect, SinType sin) {
        // A later outside change invalidates ownership even if it has the same amplifier.
        applying = true;
        applyingPlayer = player.getUniqueId();
        applyingType = effect.getType();
        changedActiveEffect = false;
        boolean accepted;
        try { accepted = player.addPotionEffect(effect); }
        finally { applying = false; applyingPlayer = null; applyingType = null; }
        if (accepted && changedActiveEffect) effects.computeIfAbsent(player.getUniqueId(), id -> new HashMap<>())
            .put(effect.getType(), new Effect(sin, player.getWorld().getUID(),
                (long) Bukkit.getCurrentTick() + effect.getDuration() + 2L));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void changed(EntityPotionEffectEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (applying && player.getUniqueId().equals(applyingPlayer) && event.getModifiedType() == applyingType) {
            changedActiveEffect = event.getNewEffect() != null && (event.getOldEffect() == null || event.isOverride());
            return;
        }
        Map<PotionEffectType, Effect> owned = effects.get(player.getUniqueId());
        if (owned == null) return;
        owned.remove(event.getModifiedType());
        if (owned.isEmpty()) effects.remove(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void death(PlayerDeathEvent event) {
        Player player = event.getEntity();
        Entity direct = event.getDamageSource().getDirectEntity();
        Entity cause = event.getDamageSource().getCausingEntity();
        SinType sin = plugin.bosses().stream().filter(b -> b.entity().equals(direct) || b.entity().equals(cause))
            .map(WrathBoss::type).findFirst().orElse(null);
        EntityDamageEvent last = player.getLastDamageCause();
        if (sin == null && last != null) {
            PotionEffectType type = switch (last.getCause()) {
                case WITHER -> PotionEffectType.WITHER;
                case POISON -> PotionEffectType.POISON;
                default -> null;
            };
            Map<PotionEffectType, Effect> owned = effects.get(player.getUniqueId());
            Effect tracked = type == null || owned == null ? null : owned.get(type);
            if (tracked != null && tracked.world().equals(player.getWorld().getUID())
                && tracked.expires() >= Bukkit.getCurrentTick() && player.hasPotionEffect(type)) sin = tracked.sin();
        }
        if (sin != null) {
            Component message = Component.text(player.getName() + " ถูกสังหารโดย " + sin.display());
            if (event.deathMessage() != null) event.deathMessage(message);
            event.deathScreenMessageOverride(message);
        }
        effects.remove(player.getUniqueId());
    }

    @EventHandler public void quit(PlayerQuitEvent event) { effects.remove(event.getPlayer().getUniqueId()); }

    @Override public void close() { effects.clear(); HandlerList.unregisterAll(this); }
}
