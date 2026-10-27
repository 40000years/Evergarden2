package com.example.advancemagic.pack.delivery;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.*;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Public HTTPS copies of a pack, named by a fingerprint of its entries instead of its ZIP bytes,
 * so a copy still matches when this server's JVM compresses a generated pack differently. A copy
 * is offered only after this server has downloaded it and found exactly the same entries; the
 * client is told the copy's own SHA-1. Verified copies are remembered, so later starts need only
 * a HEAD request. Hosts that block outbound HTTPS simply keep serving the pack themselves.
 */
public final class PackMirror {
    public record Copy(String url, String sha1) {}

    private static final long MAX_BYTES = 256L << 20;
    private static final String CACHE = "resource-packs/mirror-cache.properties";

    private PackMirror() {}

    /** SHA-1 over every file entry in UTF-8 name order: name, NUL, decimal length, NUL, bytes. */
    public static String fingerprint(Map<String, byte[]> entries) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            List<String> names = new ArrayList<>(entries.keySet());
            names.sort((a, b) -> Arrays.compareUnsigned(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8)));
            for (String name : names) {
                if (name.endsWith("/")) continue;
                byte[] body = entries.get(name);
                digest.update((name + "\0" + body.length + "\0").getBytes(StandardCharsets.UTF_8));
                digest.update(body);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    public static String fingerprint(byte[] zip) throws IOException {
        Map<String, byte[]> entries = new HashMap<>();
        try (var input = new ZipInputStream(new ByteArrayInputStream(zip))) {
            for (ZipEntry entry; (entry = input.getNextEntry()) != null; ) {
                if (entry.isDirectory()) continue;
                if (entries.put(entry.getName(), input.readAllBytes()) != null)
                    throw new IOException("Duplicate pack entry " + entry.getName());
            }
        }
        return fingerprint(entries);
    }

    /** Expands {pack} and {fingerprint}; templates that are not plain HTTP(S) URLs are skipped. */
    public static List<String> urls(List<String> templates, String pack, String fingerprint) {
        List<String> urls = new ArrayList<>();
        for (String template : templates) {
            if (template == null || template.isBlank()) continue;
            String url = template.trim().replace("{pack}", pack).replace("{fingerprint}", fingerprint);
            try {
                URI uri = URI.create(url);
                if (List.of("http", "https").contains(uri.getScheme()) && uri.getHost() != null
                        && uri.getUserInfo() == null && uri.getFragment() == null) urls.add(uri.toASCIIString());
            } catch (IllegalArgumentException ignored) {}
        }
        return urls;
    }

    /**
     * The copy published with this release, from pack-mirror.properties in the plugin JAR
     * ({@code <pack>/<fingerprint> = <sha1>}), at the first configured address; null when this exact
     * pack was not published. No network is needed: the client checks the SHA-1 itself.
     */
    public static Copy published(Plugin owner, String pack, String fingerprint, List<String> templates) {
        List<String> urls = urls(templates, pack, fingerprint);
        if (urls.isEmpty()) return null;
        try (InputStream input = owner.getResource("pack-mirror.properties")) {
            if (input == null) return null;
            Properties published = new Properties();
            published.load(input);
            String sha1 = published.getProperty(pack + "/" + fingerprint, "").trim();
            return sha1.matches("[a-f0-9]{40}") ? new Copy(urls.getFirst(), sha1) : null;
        } catch (IOException unreadable) {
            return null;
        }
    }

    /**
     * Asks, in the background, whether a published copy is still there. Reports only a definite
     * answer that it is gone (HTTP 404 or 410); a host without outbound access keeps the copy.
     */
    public static void confirm(Plugin owner, String url, Runnable gone) {
        Thread worker = new Thread(() -> {
            try {
                HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6))
                        .followRedirects(HttpClient.Redirect.NORMAL).build();
                int status = client.send(request(url, "HEAD", 20), HttpResponse.BodyHandlers.discarding()).statusCode();
                if ((status == 404 || status == 410) && owner.isEnabled()) Bukkit.getScheduler().runTask(owner, gone);
            } catch (IOException | InterruptedException unknown) {
                // No answer is not a missing copy; clients still verify its SHA-1.
            }
        }, owner.getName() + "-pack-mirror");
        worker.setDaemon(true);
        worker.start();
    }

    /**
     * Checks the copies in order on a background thread and reports, on the server thread, the first
     * that holds the same entries, or null when none does. False when there is nothing to check.
     */
    public static boolean find(Plugin owner, String pack, String fingerprint, List<String> templates, Consumer<Copy> found) {
        List<String> urls = urls(templates, pack, fingerprint);
        if (urls.isEmpty()) return false;
        Thread worker = new Thread(() -> {
            Copy copy = null;
            List<String> problems = new ArrayList<>();
            for (String url : urls) {
                try {
                    copy = check(owner, url, fingerprint);
                    if (copy != null) break;
                } catch (IOException | InterruptedException error) {
                    problems.add(URI.create(url).getHost() + ": " + error.getMessage());
                    if (error instanceof InterruptedException) break;
                }
            }
            Copy result = copy;
            String note = problems.isEmpty() ? "" : " (" + String.join("; ", problems) + ")";
            if (owner.isEnabled()) Bukkit.getScheduler().runTask(owner, () -> {
                if (result == null) owner.getLogger().info("No public copy of the " + pack + " pack " + fingerprint.substring(0, 12)
                        + note + "; players download it from this server.");
                else owner.getLogger().info("Players download the " + pack + " pack from " + result.url() + " (this server stays the fallback).");
                found.accept(result);
            });
        }, owner.getName() + "-pack-mirror");
        worker.setDaemon(true);
        worker.start();
        return true;
    }

    private static Copy check(Plugin owner, String url, String fingerprint) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6))
                .followRedirects(HttpClient.Redirect.NORMAL).build();
        Properties cache = loadCache(owner);
        String remembered = cache.getProperty(url + "#" + fingerprint);
        if (remembered != null) {
            String[] parts = remembered.split(" ");
            var head = client.send(request(url, "HEAD", 20), HttpResponse.BodyHandlers.discarding());
            long length = head.headers().firstValueAsLong("Content-Length").orElse(-1);
            if (head.statusCode() == 200 && parts.length == 2 && Long.toString(length).equals(parts[1]))
                return new Copy(url, parts[0]);
        }
        var response = client.send(request(url, "GET", 180), HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream body = response.body()) {
            if (response.statusCode() != 200) throw new IOException("HTTP " + response.statusCode());
            byte[] bytes = readLimited(body);
            if (!fingerprint(bytes).equals(fingerprint)) throw new IOException("different contents");
            String sha1 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(bytes));
            cache.setProperty(url + "#" + fingerprint, sha1 + " " + bytes.length);
            saveCache(owner, cache);
            return new Copy(url, sha1);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static HttpRequest request(String url, String method, int seconds) {
        return HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(seconds))
                .header("User-Agent", "Evergarden-pack-mirror").method(method, HttpRequest.BodyPublishers.noBody()).build();
    }

    private static byte[] readLimited(InputStream body) throws IOException {
        var buffer = new java.io.ByteArrayOutputStream();
        byte[] chunk = new byte[1 << 16];
        for (int read; (read = body.read(chunk)) >= 0; ) {
            buffer.write(chunk, 0, read);
            if (buffer.size() > MAX_BYTES) throw new IOException("larger than " + (MAX_BYTES >> 20) + " MiB");
        }
        return buffer.toByteArray();
    }

    private static synchronized Properties loadCache(Plugin owner) {
        Properties cache = new Properties();
        Path file = owner.getDataFolder().toPath().resolve(CACHE);
        if (Files.isRegularFile(file)) try (InputStream input = Files.newInputStream(file)) { cache.load(input); }
        catch (IOException ignored) {}
        return cache;
    }

    private static synchronized void saveCache(Plugin owner, Properties cache) {
        Path file = owner.getDataFolder().toPath().resolve(CACHE);
        try {
            Files.createDirectories(file.getParent());
            Path staged = Files.createTempFile(file.getParent(), "mirror-", ".tmp");
            try (OutputStream output = Files.newOutputStream(staged)) { cache.store(output, "Verified public pack copies"); }
            Files.move(staged, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ignored) {
            // The next start verifies the copy again.
        }
    }
}
