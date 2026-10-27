package com.example.advancemagic.pack.delivery;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.function.Consumer;

/**
 * The public-copy check for one pack, and the players whose offer waits for it. While the check runs,
 * a joining player's batch is held, so the pack goes out with the public copy first instead of only
 * this server's own address (which a tunnel or proxy may not carry). When the check ends, waiting
 * players get the pack; anyone still waiting after the time limit gets this server's address alone.
 */
public final class MirrorLookup {
    private final Plugin owner;
    private final String pack;
    private final int waitSeconds;
    private final Consumer<Player> offer;
    private final Set<UUID> waiting = new HashSet<>(), late = new HashSet<>();
    private PackMirror.Copy copy;
    private String fingerprint = "";
    private boolean pending;

    public MirrorLookup(Plugin owner, String pack, int waitSeconds, Consumer<Player> offer) {
        this.owner = owner; this.pack = pack; this.waitSeconds = waitSeconds; this.offer = offer;
    }

    /**
     * Uses the copy published with this release when the JAR lists this exact pack, without waiting
     * for the network; otherwise checks the copies. An earlier check's late answer is ignored.
     */
    public void start(String fingerprint, List<String> templates) {
        String current = fingerprint;
        this.fingerprint = current;
        copy = null;
        PackMirror.Copy known = PackMirror.published(owner, pack, current, templates);
        if (known != null) {
            copy = known;
            pending = false;
            owner.getLogger().info("Players download the " + pack + " pack from " + known.url() + " (published with this release; this server stays the fallback).");
            finished();
            PackMirror.confirm(owner, known.url(), () -> {
                if (copy != known) return;
                owner.getLogger().warning("The published " + pack + " pack is gone from " + known.url() + "; looking for another copy.");
                copy = null;
                pending = PackMirror.find(owner, pack, current, templates, found -> {
                    if (!current.equals(this.fingerprint)) return;
                    copy = found;
                    finished();
                });
                if (!pending) finished();
            });
            return;
        }
        pending = PackMirror.find(owner, pack, current, templates, found -> {
            if (!current.equals(this.fingerprint)) return;
            copy = found;
            finished();
        });
        if (!pending) finished();
    }

    /** The verified copy, or null. */
    public PackMirror.Copy copy() { return copy; }

    public String fingerprint() { return fingerprint; }

    public boolean pending() { return pending; }

    /**
     * True when the caller must not offer yet: the player's batch is held and the pack is offered
     * again when the check ends (or at the time limit).
     */
    public boolean defer(Player player, ResourcePackManager.Client delivery) {
        UUID id = player.getUniqueId();
        if (!pending || delivery == null || late.contains(id)) return false;
        if (waiting.add(id)) {
            delivery.hold(player, waitSeconds);
            Bukkit.getScheduler().runTaskLater(owner, () -> {
                if (!waiting.remove(id)) return;
                late.add(id);
                if (player.isOnline()) offer.accept(player);
            }, waitSeconds * 20L);
        }
        return true;
    }

    public void forget(Player player) {
        waiting.remove(player.getUniqueId());
        late.remove(player.getUniqueId());
    }

    private void finished() {
        pending = false;
        late.clear();
        for (UUID id : List.copyOf(waiting)) {
            waiting.remove(id);
            Player player = Bukkit.getPlayer(id);
            if (player != null && player.isOnline()) offer.accept(player);
        }
    }
}
