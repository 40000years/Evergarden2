package com.example.advancemagic.pack.delivery;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.ScheduledFuture;
import net.kyori.adventure.key.Key;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Answers resource pack downloads on the Minecraft port itself, so hosts that
 * expose only that port need no extra listener. Paper calls a channel listener
 * for every new connection; a sniffer reads its first bytes. HTTP GET/HEAD
 * requests are answered and closed, everything else continues to Minecraft
 * untouched. Each plugin installs its own sniffer and answers only its own
 * paths: an unclaimed request goes to the next sniffer, and the last one
 * replies 404.
 */
public final class ServerPortPackHost implements AutoCloseable {
    private static final String HOLDER = "io.papermc.paper.network.ChannelInitializeListenerHolder";
    private static final String LISTENER = "io.papermc.paper.network.ChannelInitializeListener";
    private static final String PREFIX = "pack-http-";
    private static final byte[][] METHODS = {"GET ".getBytes(StandardCharsets.US_ASCII), "HEAD ".getBytes(StandardCharsets.US_ASCII)};
    private static final int MAX_REQUEST = 8192;
    private static final long REQUEST_SECONDS = 10;

    private final String name;
    private final Key key;
    private final Function<String, byte[]> files;
    /** Every answered download is logged, so an administrator can tell a request that never arrived from one that failed. */
    private final java.util.logging.Logger log;
    private volatile boolean closed;

    private ServerPortPackHost(String name, Key key, Function<String, byte[]> files, java.util.logging.Logger log) {
        this.name = name; this.key = key; this.files = files; this.log = log;
    }

    /** Registers with Paper's connection listener; fails on servers without one. */
    public static ServerPortPackHost install(Plugin owner, Function<String, byte[]> files) throws IOException {
        Key key = new NamespacedKey(owner, "pack_http");
        ServerPortPackHost host = new ServerPortPackHost(PREFIX + key.namespace(), key, files, owner.getLogger());
        try {
            Class<?> holder = Class.forName(HOLDER), listener = Class.forName(LISTENER);
            Object proxy = Proxy.newProxyInstance(listener.getClassLoader(), new Class<?>[]{listener}, (self, method, args) -> switch (method.getName()) {
                case "afterInitChannel" -> { host.attach((Channel) args[0]); yield null; }
                case "equals" -> self == args[0];
                case "hashCode" -> System.identityHashCode(self);
                default -> host.name;
            });
            holder.getMethod("addListener", Key.class, listener).invoke(null, key, proxy);
        } catch (ReflectiveOperationException | LinkageError error) {
            throw new IOException("This server cannot share the Minecraft port: " + error, error);
        }
        return host;
    }

    /** A host without Paper's listener, for checks that attach their own channels. */
    static ServerPortPackHost detached(String namespace, Function<String, byte[]> files) {
        return new ServerPortPackHost(PREFIX + namespace, null, files, null);
    }

    /** The port the player actually reached, which NAT or a container may remap. */
    public static int port(Player player) {
        InetSocketAddress address = player == null ? null : player.getVirtualHost();
        return address != null && address.getPort() > 0 ? address.getPort() : Bukkit.getPort();
    }

    void attach(Channel channel) {
        if (closed) return;
        try {
            ChannelPipeline pipeline = channel.pipeline();
            if (pipeline.get(name) != null) return;
            // After the proxy-protocol decoder, so a forwarded request still starts with its method.
            String after = pipeline.get("haproxy-decoder") != null ? "haproxy-decoder" : pipeline.get("timeout") != null ? "timeout" : null;
            if (after == null) pipeline.addFirst(name, new Sniffer());
            else pipeline.addAfter(after, name, new Sniffer());
        } catch (RuntimeException | LinkageError ignored) {
            // A failed pipeline change must never block a Minecraft connection.
        }
    }

    /** 1 = HTTP method, 0 = need more bytes, -1 = not HTTP. */
    static int method(ByteBuf data) {
        int available = data.readableBytes(), start = data.readerIndex();
        boolean possible = false;
        for (byte[] method : METHODS) {
            boolean same = true;
            for (int i = 0; i < Math.min(available, method.length) && same; i++) same = data.getByte(start + i) == method[i];
            if (same && available >= method.length) return 1;
            possible |= same;
        }
        return possible ? 0 : -1;
    }

    /** Length of the request head before its blank line, or -1 while incomplete. */
    static int headEnd(ByteBuf data) {
        int start = data.readerIndex(), end = data.writerIndex();
        for (int i = start; i < end; i++) {
            if (data.getByte(i) != '\n') continue;
            if (i + 1 < end && data.getByte(i + 1) == '\n') return i - start;
            if (i + 2 < end && data.getByte(i + 1) == '\r' && data.getByte(i + 2) == '\n') return i - start;
        }
        return -1;
    }

    private static String header(String[] lines, String name) {
        for (int i = 1; i < lines.length; i++) {
            int colon = lines[i].indexOf(':');
            if (colon > 0 && lines[i].substring(0, colon).strip().equalsIgnoreCase(name)) return lines[i].substring(colon + 1).strip();
        }
        return null;
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        if (key == null) return;
        try { Class.forName(HOLDER).getMethod("removeListener", Key.class).invoke(null, key); }
        catch (ReflectiveOperationException | LinkageError ignored) {}
    }

    private final class Sniffer extends ChannelInboundHandlerAdapter {
        private CompositeByteBuf received;
        private ScheduledFuture<?> deadline;
        private boolean answered;

        @Override public void channelRead(ChannelHandlerContext ctx, Object message) {
            if (answered) { ReferenceCountUtil.release(message); return; }
            if (!(message instanceof ByteBuf data)) { ctx.fireChannelRead(message); return; }
            if (received == null) received = ctx.alloc().compositeBuffer();
            received.addComponent(true, data);
            int verdict = method(received);
            if (closed || verdict < 0) { forward(ctx); return; }
            if (verdict == 0) return;
            if (deadline == null) deadline = ctx.executor().schedule(() -> {
                if (log != null) log.warning("Pack HTTP request from " + ctx.channel().remoteAddress() + " never completed its headers; closed after " + REQUEST_SECONDS + "s.");
                ctx.close();
            }, REQUEST_SECONDS, TimeUnit.SECONDS);
            int end = headEnd(received);
            if (end < 0) {
                if (received.readableBytes() > MAX_REQUEST) reply(ctx, "?", "431 Request Header Fields Too Large", null, null, false);
                return;
            }
            String[] lines = received.toString(received.readerIndex(), end, StandardCharsets.ISO_8859_1).split("\r?\n");
            String[] request = lines[0].strip().split(" ");
            if (request.length != 3 || !request[2].startsWith("HTTP/1.")) { reply(ctx, lines[0], "400 Bad Request", null, null, false); return; }
            String path = request[1].contains("?") ? request[1].substring(0, request[1].indexOf('?')) : request[1];
            byte[] content = files.apply(path);
            if (content == null) {
                if (laterSniffer(ctx)) forward(ctx);
                else reply(ctx, request[0] + " " + path, "404 Not Found", null, null, false);
                return;
            }
            String etag = "\"" + path.substring(path.lastIndexOf('/') + 1).replaceFirst("\\.zip$", "") + "\"";
            if (etag.equals(header(lines, "If-None-Match"))) reply(ctx, request[0] + " " + path, "304 Not Modified", etag, null, false);
            else reply(ctx, request[0] + " " + path, "200 OK", etag, content, request[0].equals("HEAD"));
        }

        private boolean laterSniffer(ChannelHandlerContext ctx) {
            List<String> names = ctx.pipeline().names();
            for (int i = names.indexOf(ctx.name()) + 1; i < names.size(); i++) if (names.get(i).startsWith(PREFIX)) return true;
            return false;
        }

        private void forward(ChannelHandlerContext ctx) {
            ByteBuf data = received;
            received = null;
            ctx.pipeline().remove(this);
            if (data != null) ctx.fireChannelRead(data);
        }

        private void reply(ChannelHandlerContext ctx, String request, String status, String etag, byte[] content, boolean head) {
            answered = true;
            cleanup();
            // Large downloads must not trip Minecraft's 30-second read timeout.
            if (ctx.pipeline().get("timeout") != null) ctx.pipeline().remove("timeout");
            StringBuilder headers = new StringBuilder("HTTP/1.1 ").append(status).append("\r\nConnection: close\r\n");
            if (etag != null) headers.append("Content-Type: application/zip\r\nX-Content-Type-Options: nosniff\r\n")
                    .append("Cache-Control: public, max-age=31536000, immutable\r\nETag: ").append(etag).append("\r\n");
            if (!status.startsWith("304")) headers.append("Content-Length: ").append(content == null ? 0 : content.length).append("\r\n");
            ByteBuf response = Unpooled.copiedBuffer(headers.append("\r\n"), StandardCharsets.ISO_8859_1);
            if (content != null && !head) response = Unpooled.wrappedBuffer(response, Unpooled.wrappedBuffer(content));
            long started = System.nanoTime();
            String from = String.valueOf(ctx.channel().remoteAddress());
            int bytes = content == null || head ? 0 : content.length;
            if (log != null) log.info("Pack HTTP " + request + " from " + from + " -> " + status + (bytes > 0 ? ", sending " + bytes + " bytes" : ""));
            ctx.writeAndFlush(response).addListener(future -> {
                if (log != null && bytes > 0) {
                    String took = String.format(java.util.Locale.ROOT, "%.1fs", (System.nanoTime() - started) / 1e9);
                    if (future.isSuccess()) log.info("Pack HTTP " + request + " to " + from + " sent in full (" + took + ").");
                    else log.warning("Pack HTTP " + request + " to " + from + " broke off after " + took + ": " + future.cause());
                }
            }).addListener(ChannelFutureListener.CLOSE);
        }

        private void cleanup() {
            if (deadline != null) { deadline.cancel(false); deadline = null; }
            if (received != null) { received.release(); received = null; }
        }

        @Override public void handlerRemoved(ChannelHandlerContext ctx) { cleanup(); }
    }
}
