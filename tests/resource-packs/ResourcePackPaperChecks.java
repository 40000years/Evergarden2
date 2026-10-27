import com.example.voidscape.VoidscapePlugin;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.BundlePacket;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Integration checks against real Paper players, push packets and status events. */
public final class ResourcePackPaperChecks extends JavaPlugin implements Listener {
    static final UUID MAGIC = UUID.fromString("3e8e5b71-0600-4a42-a678-483a7cce5fb0");
    static final UUID GARDEN = UUID.fromString("c8f2b94e-4a35-4d1b-9b67-0d2a6ef4f821");
    static final UUID JUDGE = UUID.fromString("b198c85c-c302-4c62-a365-a358fa544509");
    final List<ClientboundResourcePackPushPacket> pushes = new ArrayList<>();
    VoidscapePlugin garden;
    Plugin magic;
    Object magicPacks;
    Player player;
    ServerPlayer actor;
    int checks, before, packBundles, kickRequests, beforeKicks;

    @Override public void onEnable() { Bukkit.getPluginManager().registerEvents(this,this);later(this::start); }
    void later(Runnable action) {
        later(action,4);
    }
    void later(Runnable action,long ticks){Bukkit.getScheduler().runTaskLater(this, () -> { try { action.run(); } catch (Throwable error) { finish(error); } },ticks);}
    @EventHandler public void kick(PlayerKickEvent event){
        if(event.getPlayer()!=player)return;
        // Observe the real kick request, then keep the synthetic player alive for
        // subsequent cases; a real client would be disconnected here.
        kickRequests++;event.setCancelled(true);
    }
    void start() {
        garden = (VoidscapePlugin) Bukkit.getPluginManager().getPlugin("Evergarden");
        magic = Bukkit.getPluginManager().getPlugin("advance-magic");
        check(garden != null && garden.isEnabled(), "Evergarden enables");
        check(magic == null || magic.isEnabled(), "optional Magic enables when installed");
        if (magic != null) magicPacks = call(magic, "packs");
        var server = MinecraftServer.getServer();
        var level = ((CraftWorld) Bukkit.getWorlds().getFirst()).getHandle();
        var profile = new GameProfile(UUID.randomUUID(), "PackProbe");
        actor = new ServerPlayer(server, level, profile, ClientInformation.createDefault());
        var connection = new Connection(PacketFlow.SERVERBOUND);
        connection.channel = new EmbeddedChannel();
        connection.address = new InetSocketAddress("127.0.0.1", 1);
        actor.connection = new ServerGamePacketListenerImpl(server, connection, actor, CommonListenerCookie.createInitial(profile, false)) {
            @Override public boolean hasClientLoaded() { return true; }
            @Override public void send(Packet<?> packet) { capture(packet); }
        };
        level.getChunk(0, 0); actor.setPos(.5, 200, .5);
        server.getPlayerList().getPlayers().add(actor);
        server.getPlayerList().getPlayersByUUID().put(profile.id(), actor);
        level.addNewPlayer(actor);
        player = actor.getBukkitEntity();
        player.setGravity(false); player.setInvulnerable(true); player.setGameMode(GameMode.SURVIVAL);
        // Exercise all three existing join listeners together.
        Bukkit.getPluginManager().callEvent(new PlayerJoinEvent(player, ""));
        check(pushes.isEmpty(), "join listeners defer sending to one scheduled batch");
        later(this::joined);
    }
    void joined() {
        // Judge shares the core packs' batch, so the client reloads once; a broken batch is recovered pack by pack.
        List<UUID> expected = magic == null ? List.of(GARDEN, JUDGE) : List.of(MAGIC, GARDEN, JUDGE);
        check(pushes.stream().map(ClientboundResourcePackPushPacket::id).toList().equals(expected), "every pack pushed exactly once in priority order: " + pushes.stream().map(ClientboundResourcePackPushPacket::id).toList());
        check(packBundles == 1, "Paper bundles join packs in one packet group");
        check(pushes.stream().allMatch(ClientboundResourcePackPushPacket::required), "every Java pack is required");
        check(garden.getConfig().getBoolean("resource-pack.required")&&garden.getConfig().getBoolean("world-boss.visuals.pack.required"),"existing optional Evergarden configuration upgrades to required");
        if(magic!=null)check(magic.getConfig().getBoolean("resource-pack.required"),"existing optional Magic configuration upgrades to required");
        check(serviceCount() == 1, "actual plugin classloaders share one delivery service");
        check(describe().contains("Batches: 1"), "all join offers belong to one batch");
        check(!garden.judgePack().applied(player), "boss visuals wait for client success");
        for (var push : List.copyOf(pushes)) {
            verifyHttp(push);
            Bukkit.getPluginManager().callEvent(new PlayerResourcePackStatusEvent(player, push.id(), PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED));
        }
        later(this::loaded);
    }
    void loaded() {
        check(pushes.size() == (magic == null ? 2 : 3), "nothing follows the single join batch");
        check(garden.judgePack().applied(player), "boss pack status still gates visuals");
        if (magicPacks != null) check((boolean) call(magicPacks, "hasApplied", Player.class, player), "magic pack status still gates effects");
        releasedBytes(garden.packs());
        if (magicPacks != null) releasedBytes(magicPacks);
        before = pushes.size();
        garden.packs().offer(player); garden.judgePack().offer(player);
        if (magicPacks != null) call(magicPacks, "offer", Player.class, player);
        later(this::duplicates);
    }
    void duplicates() {
        check(pushes.size() == before, "ordinary repeated offers do not produce new packets");
        garden.packs().resend(player); garden.judgePack().resend(player);
        later(this::resent);
    }
    void resent() {
        check(pushes.size() == before + 2, "explicit Evergarden resend pushes its two packs");
        check(describe().contains("Batches: 2"), "resend packs are also batched (join, resend)");
        Bukkit.getPluginManager().callEvent(new PlayerResourcePackStatusEvent(player, GARDEN, PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED));
        Bukkit.getPluginManager().callEvent(new PlayerResourcePackStatusEvent(player, JUDGE, PlayerResourcePackStatusEvent.Status.FAILED_RELOAD));
        check(!garden.judgePack().applied(player), "failed reload restores boss fallback");
        garden.judgePack().resend(player);
        before = pushes.size();
        if (magic != null) Bukkit.getPluginManager().disablePlugin(magic);
        later(this::handoff);
    }
    void handoff() {
        check(pushes.size() == before + 1, "queued retry survives coordinator owner shutdown");
        check(serviceCount() == 1, "one service remains available");
        for (Class<?> type : Bukkit.getServicesManager().getKnownServices())
            if (type.getName().endsWith("pack.delivery.ResourcePackManager"))
                check(Bukkit.getServicesManager().getRegistration(type).getPlugin() == garden, "Evergarden owns standalone delivery");
        Bukkit.getPluginManager().callEvent(new PlayerResourcePackStatusEvent(player, JUDGE, PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED));
        check(garden.judgePack().applied(player), "retry status works after handoff");
        check(kickRequests==0,"successful required loads allow the player to remain connected");
        beforeKicks=kickRequests;
        Bukkit.getPluginManager().callEvent(new PlayerResourcePackStatusEvent(player,JUDGE,PlayerResourcePackStatusEvent.Status.FAILED_DOWNLOAD));
        later(this::failed,25);
    }
    void failed(){
        check(kickRequests>beforeKicks,"failed required download requests a real Paper disconnect");
        Bukkit.getPluginManager().callEvent(new PlayerResourcePackStatusEvent(player,JUDGE,PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED));
        beforeKicks=kickRequests;
        Bukkit.getPluginManager().callEvent(new PlayerResourcePackStatusEvent(player,GARDEN,PlayerResourcePackStatusEvent.Status.DECLINED));
        later(this::declined,25);
    }
    void declined(){
        check(kickRequests>beforeKicks,"declining a required pack requests a real Paper disconnect");
        Bukkit.getPluginManager().callEvent(new PlayerResourcePackStatusEvent(player,GARDEN,PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED));
        beforeKicks=kickRequests;
        garden.getConfig().set("resource-pack.url","file:///invalid-pack.zip");
        garden.packs().offer(player);
        later(this::unavailable,25);
    }
    void unavailable(){
        check(kickRequests>beforeKicks,"invalid required URL cannot silently allow pack-free play");
        garden.getConfig().set("resource-pack.url","");
        finish(null);
    }
    void verifyHttp(ClientboundResourcePackPushPacket push) {
        try (var client = HttpClient.newHttpClient()) {
            var uri = URI.create(push.url());
            var result = client.send(HttpRequest.newBuilder(uri).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
            check(result.statusCode() == 200, "embedded pack host responds");
            String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(result.body()));
            check(digest.equals(push.hash()) && uri.getPath().contains(digest), "packet hash and content-addressed URL match served bytes");
            String etag = result.headers().firstValue("etag").orElseThrow();
            var cached = client.send(HttpRequest.newBuilder(uri).header("If-None-Match", etag).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
            check(cached.statusCode() == 304 && cached.body().length == 0, "unchanged pack supports HTTP cache validation");
        } catch (Exception error) { throw new RuntimeException(error); }
    }
    void capture(Packet<?> packet) {
        if (packet instanceof ClientboundResourcePackPushPacket push) pushes.add(push);
        else if (packet instanceof BundlePacket<?> bundle) {
            int previous = pushes.size();
            for (Packet<?> child : bundle.subPackets()) capture(child);
            if (pushes.size() != previous) packBundles++;
        }
    }
    void releasedBytes(Object packs) {
        try {
            var field = packs.getClass().getDeclaredField("extractedJavaPack"); field.setAccessible(true);
            check(field.get(packs) == null, "startup buffer released after handing bytes to host");
        } catch (Exception error) { throw new RuntimeException(error); }
    }
    int serviceCount() { return (int) Bukkit.getServicesManager().getKnownServices().stream().filter(type -> type.getName().endsWith("pack.delivery.ResourcePackManager")).count(); }
    String describe() {
        for (Class<?> type : Bukkit.getServicesManager().getKnownServices())
            if (type.getName().endsWith("pack.delivery.ResourcePackManager"))
                return (String) call(Bukkit.getServicesManager().getRegistration(type).getProvider(), "describe", Player.class, player);
        throw new AssertionError("No delivery service");
    }
    Object call(Object target, String method) { try { return target.getClass().getMethod(method).invoke(target); } catch (Exception error) { throw new RuntimeException(error); } }
    Object call(Object target, String method, Class<?> type, Object value) { try { return target.getClass().getMethod(method, type).invoke(target, value); } catch (Exception error) { throw new RuntimeException(error); } }
    void check(boolean condition, String message) { checks++; if (!condition) throw new AssertionError(message); getLogger().info("PASS: " + message); }
    void finish(Throwable error) {
        try {
            if (error != null) getLogger().log(java.util.logging.Level.SEVERE, "Resource pack integration check failed", error);
            Files.writeString(Path.of("resource-pack-result.txt"), error == null ? "PASS: " + checks + " real Paper resource pack checks" : "FAIL: " + error);
            if (actor != null) {
                var server = MinecraftServer.getServer();
                server.getPlayerList().getPlayers().remove(actor);
                server.getPlayerList().getPlayersByUUID().remove(actor.getUUID());
                actor.discard();
            }
        } catch (Exception problem) { getLogger().warning(problem.toString()); }
        Bukkit.shutdown();
    }
}
