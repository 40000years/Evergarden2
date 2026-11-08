import com.example.voidscape.boss.BossDamage;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.BundlePacket;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Slime;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Boss kill announcements with the real DeathMessages plugin. Seraphon hits through an invisible Slime anchor with
 * INDIRECT_MAGIC and ends Judge Wings with a scoped setHealth(0); DeathMessages picks its own templates for those, so
 * the boss name only appears if Evergarden rewrites the line. An ordinary death keeps DeathMessages' own text.
 */
public final class BossDeathMessagesProbe extends JavaPlugin implements org.bukkit.event.Listener {
    private final Map<ServerPlayer, List<String>> chat = new HashMap<>();
    /** Without DeathMessages the server broadcasts the final vanilla death line; read it where it is decided. */
    private final List<String> vanilla = new ArrayList<>();

    @org.bukkit.event.EventHandler(priority = org.bukkit.event.EventPriority.MONITOR)
    public void finalLine(org.bukkit.event.entity.PlayerDeathEvent event) {
        if (event.deathMessage() != null)
            vanilla.add(net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(event.deathMessage()));
    }
    private final List<String> passed = new ArrayList<>();
    private ServerPlayer observer;
    private World world;
    private int step;

    @Override public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getScheduler().runTaskLater(this, this::start, 60);
    }

    private void check(boolean ok, String name) {
        if (!ok) throw new AssertionError(name);
        passed.add(name);
        getLogger().info("PASS: " + name);
    }

    private ServerPlayer join(String name, Location at) {
        var server = MinecraftServer.getServer();
        var level = ((CraftWorld) at.getWorld()).getHandle();
        var profile = new GameProfile(UUID.randomUUID(), name);
        var actor = new ServerPlayer(server, level, profile, ClientInformation.createDefault());
        var connection = new Connection(PacketFlow.SERVERBOUND);
        connection.channel = new EmbeddedChannel();
        connection.address = new InetSocketAddress("127.0.0.1", 1);
        chat.put(actor, new ArrayList<>());
        actor.connection = new ServerGamePacketListenerImpl(server, connection, actor, CommonListenerCookie.createInitial(profile, false)) {
            @Override public boolean hasClientLoaded() { return true; }
            @Override public void send(Packet<?> packet) { capture(actor, packet); }
        };
        actor.setPos(at.getX(), at.getY(), at.getZ());
        server.getPlayerList().getPlayers().add(actor);
        server.getPlayerList().getPlayersByUUID().put(profile.id(), actor);
        level.addNewPlayer(actor);
        Player player = actor.getBukkitEntity();
        player.setGameMode(GameMode.SURVIVAL);
        player.setGravity(false);
        Bukkit.getPluginManager().callEvent(new PlayerJoinEvent(player, net.kyori.adventure.text.Component.empty()));
        return actor;
    }

    private void capture(ServerPlayer actor, Packet<?> packet) {
        if (packet instanceof ClientboundSystemChatPacket system) chat.get(actor).add(system.content().getString());
        else if (packet instanceof BundlePacket<?> bundle) for (Packet<?> child : bundle.subPackets()) capture(actor, child);
    }

    private List<String> heardAbout(String victim) {
        List<String> lines = Files.exists(Path.of("no-deathmessages.flag")) ? vanilla : chat.get(observer);
        return lines.stream().filter(line -> line.contains(victim)).toList();
    }

    private void start() {
        try {
            var deathMessages = Bukkit.getPluginManager().getPlugin("DeathMessages");
            if (Files.exists(Path.of("no-deathmessages.flag")))
                check(deathMessages == null, "no DeathMessages: Evergarden's own vanilla death line is the one players read");
            else check(deathMessages != null && deathMessages.isEnabled(), "DeathMessages " + deathMessages.getPluginMeta().getVersion() + " is enabled");
            world = Bukkit.getWorlds().getFirst();
            Location at = world.getSpawnLocation().add(0, 30, 0);
            world.getChunkAt(at).addPluginChunkTicket(this);
            observer = join("Watcher", at);
            // Seraphon's ordinary blows: INDIRECT_MAGIC through the invisible, named Slime anchor.
            ServerPlayer magic = join("MagicVictim", at.clone().add(3, 0, 0));
            Slime anchor = world.spawn(at.clone().add(0, 0, 6), Slime.class, e -> {
                BossDamage.tag(e, "Seraphon");
                e.setAI(false); e.setInvisible(true); e.setSize(8); e.setGravity(false);
            });
            magic.getBukkitEntity().damage(5000, DamageSource.builder(DamageType.INDIRECT_MAGIC).withCausingEntity(anchor).withDirectEntity(anchor).build());
            // Judge Wings: a scoped direct zero health.
            ServerPlayer wings = join("WingsVictim", at.clone().add(-3, 0, 0));
            BossDamage.scoped(wings.getBukkitEntity(), "Seraphon", () -> wings.getBukkitEntity().setHealth(0));
            // A realm boss's melee through its Wither Skeleton carrier.
            ServerPlayer melee = join("MeleeVictim", at.clone().add(3, 0, 3));
            org.bukkit.entity.WitherSkeleton carrier = world.spawn(at.clone().add(6, 0, 6), org.bukkit.entity.WitherSkeleton.class, e -> {
                BossDamage.tag(e, "Cinder Colossus");
                e.setAI(false);
            });
            melee.getBukkitEntity().damage(5000, carrier);
            // A boss's lingering Wither: the killing tick has no entity at all.
            ServerPlayer wither = join("WitherVictim", at.clone().add(-3, 0, 3));
            BossDamage.effect(wither.getBukkitEntity(), new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.WITHER, 200, 1), "Malefeasance");
            wither.getBukkitEntity().damage(5000, DamageSource.builder(DamageType.WITHER).build());
            // No boss involved.
            ServerPlayer plain = join("PlainVictim", at.clone().add(0, 0, -3));
            plain.getBukkitEntity().damage(5000);
            Bukkit.getScheduler().runTaskLater(this, () -> {
                try {
                    getLogger().info("observer chat: " + chat.get(observer));
                    List<String> magicLines = heardAbout("MagicVictim"), wingsLines = heardAbout("WingsVictim"), plainLines = heardAbout("PlainVictim");
                    check(!magicLines.isEmpty() && magicLines.stream().anyMatch(l -> l.contains("SERAPHON") && l.contains("ถูกสังหารโดย")),
                        "a Seraphon magic blow is announced with Seraphon's name: " + magicLines);
                    check(!wingsLines.isEmpty() && wingsLines.stream().anyMatch(l -> l.contains("SERAPHON") && l.contains("ถูกสังหารโดย")),
                        "Judge Wings (direct zero health) is announced with Seraphon's name: " + wingsLines);
                    List<String> meleeLines = heardAbout("MeleeVictim"), witherLines = heardAbout("WitherVictim");
                    check(meleeLines.stream().anyMatch(l -> l.contains("Cinder Colossus") && l.contains("ถูกสังหารโดย")),
                        "a realm boss's melee (Wither Skeleton carrier) is announced with its name: " + meleeLines);
                    check(witherLines.stream().anyMatch(l -> l.contains("Malefeasance") && l.contains("ถูกสังหารโดย")),
                        "a boss's lingering Wither is announced with the boss's name: " + witherLines);
                    check(!plainLines.isEmpty() && plainLines.stream().noneMatch(l -> l.contains("ถูกสังหารโดย") || l.contains("SERAPHON")),
                        "an ordinary death keeps DeathMessages' own line: " + plainLines);
                    check(magicLines.size() == 1 && wingsLines.size() == 1 && meleeLines.size() == 1 && witherLines.size() == 1,
                        "each death is announced once");
                    anchor.remove();
                    carrier.remove();
                    finish("PASS " + passed.size() + " boss death message checks\n" + String.join("\n", passed));
                } catch (Throwable error) { finish("FAIL " + error + "\npassed before failure: " + passed); }
            }, 40);
        } catch (Throwable error) {
            finish("FAIL " + error + "\npassed before failure: " + passed);
        }
    }

    private void finish(String text) {
        try { Files.writeString(Path.of("boss-death-messages-result.txt"), text); } catch (Exception ignored) {}
        Bukkit.getScheduler().runTaskLater(this, Bukkit::shutdown, 20L);
    }
}
