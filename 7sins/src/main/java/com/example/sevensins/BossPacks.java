package com.example.sevensins;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;

/** Adds an independent pack UUID to the player's stack without replacing other plugins' packs. */
public final class BossPacks implements Listener, AutoCloseable {
    public static final UUID PACK_ID = UUID.fromString("c67c5645-89c9-4a41-a8ef-bd93e55280c5");
    /** Pinned to the source pack that ships inside the matching version of 7sins.jar. */
    public static final String DEFAULT_CDN_URL = "https://raw.githubusercontent.com/40000years/Evergarden2/e92101935ef8ea08b0fa886ffec1cddd25c8a218/7sins/src/main/resources/resource-packs/7sins-java.zip";
    private final SevenSinsPlugin plugin;
    private final Set<UUID> loaded = new HashSet<>();
    private final Map<UUID, String> statuses = new HashMap<>();
    private byte[] bytes, hash;
    private String sha1, path, failure = "";
    private HttpServer server;
    private ExecutorService workers;

    BossPacks(SevenSinsPlugin plugin) { this.plugin = plugin; }
    void start() {
        String currentUrl = plugin.getConfig().getString("resource-pack.url", "").trim();
        if (currentUrl.matches("https://raw\\.githubusercontent\\.com/40000years/Evergarden2/[0-9a-f]{7,40}/7sins/src/main/resources/resource-packs/7sins-java\\.zip")
                && !currentUrl.equals(DEFAULT_CDN_URL)) {
            plugin.getConfig().set("resource-pack.url", DEFAULT_CDN_URL); plugin.saveConfig();
            plugin.getLogger().info("Updated official Wrath pack to the matching pinned GitHub URL.");
        }
        try (InputStream in = plugin.getResource("resource-packs/7sins-java.zip")) {
            if (in == null) throw new IOException("Missing bundled 7sins-java.zip");
            bytes = in.readAllBytes(); hash = MessageDigest.getInstance("SHA-1").digest(bytes);
            sha1 = HexFormat.of().formatHex(hash); path = "/7sins/" + sha1 + ".zip";
            Path out = plugin.getDataFolder().toPath().resolve("resource-packs/7sins-java.zip");
            Files.createDirectories(out.getParent()); Files.write(out, bytes);
            if (plugin.getConfig().getString("resource-pack.url", "").isBlank()
                    && plugin.getConfig().getBoolean("resource-pack.host.enabled", true)) {
                server = HttpServer.create(new InetSocketAddress(plugin.getConfig().getString("resource-pack.host.bind", "0.0.0.0"),
                        plugin.getConfig().getInt("resource-pack.host.port", 8188)), 32);
                workers = new ThreadPoolExecutor(1, 3, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(32), r -> {
                    Thread t = new Thread(r, "7sins-pack"); t.setDaemon(true); return t;
                }, new ThreadPoolExecutor.AbortPolicy());
                server.setExecutor(workers); server.createContext("/", this::serve); server.start();
                plugin.getLogger().info("7sins resource pack listening on TCP " + server.getAddress().getPort());
            }
        } catch (IOException | NoSuchAlgorithmException | IllegalArgumentException error) {
            failure = error.getMessage(); plugin.getLogger().warning("7sins pack unavailable: " + failure + ". Bosses use the armor fallback.");
            if (server != null) server.stop(0); server = null;
            if (workers != null) workers.shutdownNow();
        }
        Bukkit.getPluginManager().registerEvents(this, plugin);
        for (Player p : Bukkit.getOnlinePlayers()) if (plugin.getConfig().getBoolean("resource-pack.auto-send", true)) send(p);
    }

    private void serve(HttpExchange exchange) throws IOException {
        try (exchange) {
            if (!exchange.getRequestURI().getRawPath().equals(path)) { exchange.sendResponseHeaders(404, -1); return; }
            String method = exchange.getRequestMethod();
            if (!method.equals("GET") && !method.equals("HEAD")) {
                exchange.getResponseHeaders().set("Allow", "GET, HEAD"); exchange.sendResponseHeaders(405, -1); return;
            }
            var headers = exchange.getResponseHeaders();
            headers.set("Content-Type", "application/zip"); headers.set("X-Content-Type-Options", "nosniff");
            headers.set("Cache-Control", "public, max-age=31536000, immutable"); headers.set("ETag", "\"" + sha1 + "\"");
            if (headers.getFirst("ETag").equals(exchange.getRequestHeaders().getFirst("If-None-Match"))) {
                exchange.sendResponseHeaders(304, -1); return;
            }
            headers.set("Content-Length", Integer.toString(bytes.length));
            exchange.sendResponseHeaders(200, method.equals("HEAD") ? -1 : bytes.length);
            if (method.equals("GET")) exchange.getResponseBody().write(bytes);
        }
    }

    public boolean loaded(Player player) { return loaded.contains(player.getUniqueId()) && !bedrock(player); }
    public String status(Player player) { return statuses.getOrDefault(player.getUniqueId(), "ยังไม่ได้ส่งแพ็ก"); }
    public boolean bedrock(Player player) {
        if (Bukkit.getPluginManager().getPlugin("floodgate") == null) return false;
        try {
            Class<?> api = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            Object instance = api.getMethod("getInstance").invoke(null);
            return (boolean) api.getMethod("isFloodgatePlayer", UUID.class).invoke(instance, player.getUniqueId());
        } catch (ReflectiveOperationException | LinkageError ignored) { return false; }
    }
    public String url(Player player) {
        String configured = plugin.getConfig().getString("resource-pack.url", "").trim();
        if (!configured.isBlank()) return configured;
        if (server == null) return DEFAULT_CDN_URL;
        String host = plugin.getConfig().getString("resource-pack.host.public-host", "").trim();
        if (host.isBlank() && player.getVirtualHost() != null) host = player.getVirtualHost().getHostString();
        if (host.isBlank()) return "";
        try { return new URI("http", null, host, server.getAddress().getPort(), path, null, null).toASCIIString(); }
        catch (URISyntaxException error) { return ""; }
    }
    public void send(Player player) {
        if (bedrock(player)) { statuses.put(player.getUniqueId(), "Bedrock: ใช้ร่างเกราะสำรอง"); return; }
        String url = url(player);
        if (url.isBlank() || hash == null) {
            player.sendMessage("§6[7sins] แพ็กยังไม่พร้อม ใช้ร่างเกราะสำรอง ตั้งค่า resource-pack.host.public-host หรือ resource-pack.url");
            return;
        }
        try {
            player.addResourcePack(PACK_ID, url, hash, "7sins: โมเดลบอส Wrath", false);
            statuses.put(player.getUniqueId(), "ส่งแพ็กแล้ว");
        } catch (IllegalArgumentException error) { player.sendMessage("§c[7sins] URL แพ็กไม่ถูกต้อง: " + error.getMessage()); }
    }
    @EventHandler public void join(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!event.getPlayer().isOnline()) return;
            plugin.refresh(event.getPlayer());
            if (plugin.getConfig().getBoolean("resource-pack.auto-send", true)) send(event.getPlayer());
        }, 40);
    }
    @EventHandler public void quit(PlayerQuitEvent event) {
        loaded.remove(event.getPlayer().getUniqueId()); statuses.remove(event.getPlayer().getUniqueId());
    }
    @EventHandler public void status(PlayerResourcePackStatusEvent event) {
        if (!event.getID().equals(PACK_ID)) return;
        statuses.put(event.getPlayer().getUniqueId(), event.getStatus().name());
        switch (event.getStatus()) {
            case SUCCESSFULLY_LOADED -> loaded.add(event.getPlayer().getUniqueId());
            case DECLINED, FAILED_DOWNLOAD, INVALID_URL, FAILED_RELOAD, DISCARDED -> loaded.remove(event.getPlayer().getUniqueId());
            default -> {}
        }
        plugin.refresh(event.getPlayer());
    }
    String failure() { return failure; }
    @Override public void close() {
        loaded.clear(); statuses.clear(); if (server != null) server.stop(0); if (workers != null) workers.shutdownNow();
        for (Player p : Bukkit.getOnlinePlayers()) p.removeResourcePack(PACK_ID);
    }
}
