package com.example.advancemagic.pack.delivery;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;

/** Minecraft and HTTP traffic sharing one port, on Netty's in-memory channel. */
public final class ServerPortPackHostChecks {
    private static final String MAGIC_PATH = "/advance-magic/" + "a".repeat(40) + ".zip";
    private static final String GARDEN_PATH = "/evergarden/" + "b".repeat(40) + ".zip";
    private static final byte[] MAGIC = "magic-pack-bytes".getBytes(StandardCharsets.UTF_8);
    private static final byte[] GARDEN = new byte[3 * 1024 * 1024];

    public static void main(String[] args) {
        Arrays.fill(GARDEN, (byte) 7);
        var magic = ServerPortPackHost.detached("advance-magic", Map.of(MAGIC_PATH, MAGIC)::get);
        var garden = ServerPortPackHost.detached("evergarden", Map.of(GARDEN_PATH, GARDEN)::get);

        byte[] handshake = {0x10, 0x00, (byte) 0xFB, 0x05, 0x09, 'l', 'o', 'c', 'a', 'l', 'h', 'o', 's', 't', 0x63, (byte) 0xDD, 0x01};
        var session = connect(garden, magic);
        session.channel.writeInbound(Unpooled.wrappedBuffer(handshake));
        check(Arrays.equals(session.minecraft.toByteArray(), handshake), "Minecraft handshake reaches the game unchanged");
        check(session.channel.pipeline().names().stream().noneMatch(n -> n.startsWith("pack-http-")), "sniffers leave a game connection");
        check(session.channel.pipeline().get("timeout") != null && session.channel.isOpen(), "game connection keeps its timeout");

        session = connect(garden, magic);
        session.channel.writeInbound(Unpooled.wrappedBuffer(new byte[]{(byte) 0xFE, 0x01}));
        check(Arrays.equals(session.minecraft.toByteArray(), new byte[]{(byte) 0xFE, 0x01}), "legacy server-list ping passes");

        session = connect(garden, magic);
        session.channel.writeInbound(Unpooled.copiedBuffer("GE", StandardCharsets.US_ASCII));
        check(session.minecraft.size() == 0, "an undecided prefix waits for more bytes");
        session.channel.writeInbound(Unpooled.copiedBuffer("X-not-http", StandardCharsets.US_ASCII));
        check(session.minecraft.toString(StandardCharsets.US_ASCII).equals("GEX-not-http"), "a near-miss is forwarded byte for byte");

        session = connect(garden, magic);
        session.channel.writeInbound(Unpooled.copiedBuffer("G", StandardCharsets.US_ASCII));
        session.channel.writeInbound(Unpooled.copiedBuffer("ET " + MAGIC_PATH + "?v=1 HTTP/1.1\r\nHost: play.example.org:25565\r\n\r\n", StandardCharsets.US_ASCII));
        var response = session.response();
        check(response.status.equals("HTTP/1.1 200 OK"), "split GET with a query is answered: " + response.status);
        check(Arrays.equals(response.body, MAGIC), "Advance Magic bytes are exact");
        check(response.head.contains("ETag: \"" + "a".repeat(40) + "\"") && response.head.contains("Content-Length: " + MAGIC.length), "ETag and length are sent");
        check(session.minecraft.size() == 0 && !session.channel.isOpen(), "HTTP never reaches the game and the connection closes");
        check(session.channel.pipeline().get("timeout") == null, "the game read timeout cannot cut a slow download");

        session = connect(garden, magic);
        session.channel.writeInbound(Unpooled.copiedBuffer("GET " + GARDEN_PATH + " HTTP/1.1\r\n\r\n", StandardCharsets.US_ASCII));
        response = session.response();
        check(response.status.endsWith("200 OK") && Arrays.equals(response.body, GARDEN), "the next plugin answers its own path through the first sniffer");

        session = connect(garden, magic);
        session.channel.writeInbound(Unpooled.copiedBuffer("HEAD " + GARDEN_PATH + " HTTP/1.0\n\n", StandardCharsets.US_ASCII));
        response = session.response();
        check(response.status.endsWith("200 OK") && response.body.length == 0 && response.head.contains("Content-Length: " + GARDEN.length), "HEAD sends headers only");

        session = connect(garden, magic);
        session.channel.writeInbound(Unpooled.copiedBuffer("GET " + MAGIC_PATH + " HTTP/1.1\r\nif-none-match: \"" + "a".repeat(40) + "\"\r\n\r\n", StandardCharsets.US_ASCII));
        response = session.response();
        check(response.status.endsWith("304 Not Modified") && response.body.length == 0, "a cached client gets 304");

        session = connect(garden, magic);
        session.channel.writeInbound(Unpooled.copiedBuffer("GET /evergarden/../config.yml HTTP/1.1\r\n\r\n", StandardCharsets.US_ASCII));
        response = session.response();
        check(response.status.endsWith("404 Not Found") && session.minecraft.size() == 0, "unknown paths get 404 from the last sniffer");

        session = connect(garden, magic);
        session.channel.writeInbound(Unpooled.copiedBuffer("GET nonsense\r\n\r\n", StandardCharsets.US_ASCII));
        check(session.response().status.endsWith("400 Bad Request"), "a malformed request line is rejected");

        session = connect(garden, magic);
        session.channel.writeInbound(Unpooled.copiedBuffer("GET " + MAGIC_PATH + " HTTP/1.1\r\nX: " + "y".repeat(9000), StandardCharsets.US_ASCII));
        check(session.response().status.endsWith("431 Request Header Fields Too Large"), "an endless header is cut off");

        session = connect(garden, magic);
        session.channel.writeInbound(Unpooled.copiedBuffer("GET " + MAGIC_PATH + " HTTP/1.1\r\n", StandardCharsets.US_ASCII));
        session.channel.advanceTimeBy(11, java.util.concurrent.TimeUnit.SECONDS);
        session.channel.runScheduledPendingTasks();
        check(!session.channel.isOpen(), "an unfinished request is closed after its deadline");

        magic.close();
        session = connect(magic);
        session.channel.writeInbound(Unpooled.copiedBuffer("GET " + MAGIC_PATH + " HTTP/1.1\r\n\r\n", StandardCharsets.US_ASCII));
        check(session.channel.pipeline().get("pack-http-advance-magic") == null, "a closed host no longer attaches");
        System.out.println("PASS: Minecraft and resource pack HTTP share one port without disturbing the game");
    }

    private record Response(String status, String head, byte[] body) {}

    private static final class Session {
        final EmbeddedChannel channel = new EmbeddedChannel();
        final ByteArrayOutputStream minecraft = new ByteArrayOutputStream();

        Response response() {
            var out = new ByteArrayOutputStream();
            for (ByteBuf buf; (buf = channel.readOutbound()) != null; buf.release()) {
                byte[] bytes = new byte[buf.readableBytes()];
                buf.readBytes(bytes);
                out.writeBytes(bytes);
            }
            byte[] all = out.toByteArray();
            String text = new String(all, StandardCharsets.ISO_8859_1);
            int split = text.indexOf("\r\n\r\n");
            check(split > 0, "response has a header block");
            String head = text.substring(0, split);
            return new Response(head.split("\r\n")[0], head, Arrays.copyOfRange(all, split + 4, all.length));
        }
    }

    /** Mirrors Paper's order: read timeout first, sniffers after it, the game handler last. */
    private static Session connect(ServerPortPackHost... hosts) {
        var session = new Session();
        session.channel.pipeline().addLast("timeout", new ChannelInboundHandlerAdapter());
        for (var host : hosts) host.attach(session.channel);
        session.channel.pipeline().addLast("packet_handler", new ChannelInboundHandlerAdapter() {
            @Override public void channelRead(ChannelHandlerContext ctx, Object message) {
                ByteBuf buf = (ByteBuf) message;
                byte[] bytes = new byte[buf.readableBytes()];
                buf.readBytes(bytes);
                buf.release();
                session.minecraft.writeBytes(bytes);
            }
        });
        return session;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
