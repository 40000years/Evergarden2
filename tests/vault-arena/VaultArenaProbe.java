import com.example.voidscape.VoidscapePlugin;
import com.example.voidscape.dungeon.DungeonManager;
import com.example.voidscape.world.DungeonLayout;
import com.example.voidscape.world.DungeonLayout.Site;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.server.network.*;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.entity.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.nio.file.*;
import java.util.*;

/**
 * Vault temple arena on real Paper: players may jump, fly a wind-charge arc or mace-dive above the floor
 * without being pulled back; standing on pillars, staying outside the ring or under the floor stops their
 * hits; the temple resets after 15 seconds with nobody inside; mobs that leave the ring return to the centre.
 */
public final class VaultArenaProbe extends JavaPlugin {
    VoidscapePlugin garden;
    DungeonManager dungeons;
    Site site;
    Player player;
    final List<ServerPlayer> actors = new ArrayList<>();
    final List<Runnable> steps = new ArrayList<>();
    final List<Integer> delays = new ArrayList<>();
    int checks;

    void check(boolean value, String label) {
        if (!value) throw new AssertionError(label);
        checks++;
        getLogger().info("PASS Arena: " + label);
    }
    Object field(Object target, String name) throws Exception {
        Field f = target.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(target);
    }
    @SuppressWarnings("unchecked") Map<String, Object> active() throws Exception { return (Map<String, Object>) field(dungeons, "active"); }
    @SuppressWarnings("unchecked") List<Mob> mobs() throws Exception {
        Object enc = active().get(site.id());
        if (enc == null) return List.of();
        List<Mob> out = new ArrayList<>();
        for (UUID id : ((Map<UUID, Object>) field(enc, "mobs")).keySet()) if (Bukkit.getEntity(id) instanceof Mob m && m.isValid()) out.add(m);
        return out;
    }
    Location at(double dx, double y, double dz) { return new Location(garden.world(), site.x() + .5 + dx, y, site.z() + .5 + dz); }
    void place(double dx, double y, double dz) {
        ServerPlayer handle = ((org.bukkit.craftbukkit.entity.CraftPlayer) player).getHandle();
        handle.setPos(site.x() + .5 + dx, y, site.z() + .5 + dz);
    }
    boolean hitAllowed() throws Exception {
        List<Mob> list = mobs();
        if (list.isEmpty()) throw new AssertionError("no encounter mob to hit");
        var event = new EntityDamageByEntityEvent(player, list.getFirst(), EntityDamageEvent.DamageCause.ENTITY_ATTACK,
            org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.PLAYER_ATTACK).withCausingEntity(player).withDirectEntity(player).build(), 1.0);
        Bukkit.getPluginManager().callEvent(event);
        return !event.isCancelled();
    }
    void then(int ticks, Runnable step) { delays.add(ticks); steps.add(step); }

    @Override public void onEnable() {
        Bukkit.getScheduler().runTaskLater(this, () -> {
            try { plan(); run(0); }
            catch (Throwable error) { fail(error); }
        }, 40);
    }
    void run(int index) {
        if (index >= steps.size()) { finish("PASS " + checks + " vault arena checks"); return; }
        Bukkit.getScheduler().runTaskLater(this, () -> {
            try { steps.get(index).run(); run(index + 1); }
            catch (Throwable error) { fail(error); }
        }, delays.get(index));
    }
    void fail(Throwable error) {
        getLogger().log(java.util.logging.Level.SEVERE, "VAULT ARENA CHECK FAILED", error);
        finish("FAIL " + error);
    }
    void finish(String text) {
        try { Files.writeString(Path.of("vault-arena-result.txt"), text); } catch (Exception ignored) {}
        for (ServerPlayer handle : actors) {
            var server = MinecraftServer.getServer(); server.getPlayerList().getPlayers().remove(handle);
            server.getPlayerList().getPlayersByUUID().remove(handle.getUUID()); handle.discard();
        }
        Bukkit.getScheduler().runTaskLater(this, Bukkit::shutdown, 5);
    }
    interface Step { void run() throws Exception; }
    Runnable step(Step s) { return () -> { try { s.run(); } catch (RuntimeException e) { throw e; } catch (Exception e) { throw new RuntimeException(e); } }; }

    void plan() throws Exception {
        garden = (VoidscapePlugin) Bukkit.getPluginManager().getPlugin("Evergarden");
        dungeons = garden.dungeons();
        site = DungeonLayout.STARTER_SITES.getFirst();
        player = actor("ArenaProbe");
        // The detached test player does not load chunks, and wave spawns need the arena loaded.
        for (int cx = (site.x() - 40) >> 4; cx <= (site.x() + 40) >> 4; cx++)
            for (int cz = (site.z() - 40) >> 4; cz <= (site.z() + 40) >> 4; cz++) garden.world().setChunkForceLoaded(cx, cz, true);
        Block lodestone = null;
        for (int dx = -10; dx <= 10 && lodestone == null; dx++) for (int dz = -2; dz <= 18 && lodestone == null; dz++) for (int y = 94; y <= 101; y++) {
            Block b = garden.world().getBlockAt(site.x() + dx, y, site.z() + dz);
            if (b.getType() == Material.LODESTONE) { lodestone = b; break; }
        }
        check(lodestone != null, "the temple altar Lodestone exists");
        Block altar = lodestone;
        then(1, step(() -> {
            place(0, 97, 8);
            var e = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, null, altar, BlockFace.UP, EquipmentSlot.HAND);
            Bukkit.getPluginManager().callEvent(e);
            check(active().containsKey(site.id()), "clicking the Lodestone starts the encounter");
        }));
        then(40, step(() -> check(!mobs().isEmpty(), "the first wave spawns")));
        then(1, step(() -> { place(4, 97, 4); check(hitAllowed(), "a player on the arena floor can hit the wave"); }));
        // Airborne above the floor: a wind-charge or mace arc, nothing underfoot.
        then(1, step(() -> place(3, 115, 3)));
        then(30, step(() -> {
            check(Math.abs(player.getY() - 115) < .01, "a player high above the floor is not pulled back down");
            check(active().containsKey(site.id()) && hitAllowed(), "an airborne player still counts and can strike (mace dive)");
        }));
        // Standing on top of a pillar inside the ring.
        then(1, step(() -> {
            garden.world().getBlockAt(site.x() + 10, 104, site.z() + 3).setType(Material.STONE);
            place(10, 105, 3);
            check(!hitAllowed(), "a player standing on a pillar above Y 102.5 cannot hurt the encounter");
        }));
        then(30, step(() -> check(Math.abs(player.getY() - 105) < .01, "the pillar player is warned, not teleported")));
        // Under the floor and outside the ring.
        then(1, step(() -> { place(4, 93, 4); check(!hitAllowed(), "a player under the floor cannot hurt the encounter"); }));
        then(1, step(() -> { place(26, 97, 0); check(!hitAllowed(), "a player outside radius 21.5 cannot hurt the encounter"); }));
        then(160, step(() -> check(active().containsKey(site.id()), "eight seconds outside: the temple keeps running")));
        then(1, step(() -> place(0, 97, 6)));
        then(160, step(() -> check(active().containsKey(site.id()), "returning inside restarts the 15-second allowance")));
        // Mobs that leave the ring are returned to the centre.
        then(1, step(() -> {
            Mob mob = mobs().getFirst();
            mob.teleport(at(25, 97, 0));
        }));
        then(20, step(() -> {
            boolean back = mobs().stream().allMatch(m -> Math.hypot(m.getX() - (site.x() + .5), m.getZ() - (site.z() + .5)) <= 19.2);
            check(back, "a mob pushed outside the ring is brought back inside");
        }));
        // Everyone leaves: the temple resets after 15 seconds.
        then(1, step(() -> place(30, 97, 0)));
        then(240, step(() -> check(active().containsKey(site.id()), "twelve seconds with nobody inside: not reset yet")));
        then(100, step(() -> {
            check(!active().containsKey(site.id()), "after 15 seconds with nobody inside, the temple resets");
            check(mobs().isEmpty(), "the reset removes the encounter's mobs");
        }));
    }

    Player actor(String name) {
        var server = MinecraftServer.getServer(); var level = ((CraftWorld) garden.world()).getHandle();
        UUID uuid = UUID.randomUUID(); var profile = new GameProfile(uuid, name);
        ServerPlayer handle = new ServerPlayer(server, level, profile, ClientInformation.createDefault());
        var connection = new Connection(PacketFlow.SERVERBOUND); connection.channel = new EmbeddedChannel();
        connection.address = new InetSocketAddress("127.0.0.1", 1);
        handle.connection = new ServerGamePacketListenerImpl(server, connection, handle, CommonListenerCookie.createInitial(profile, false)) {
            @Override public boolean hasClientLoaded() { return true; }
        };
        handle.setPos(site.x() + .5, 97, site.z() + 8.5);
        server.getPlayerList().getPlayers().add(handle); server.getPlayerList().getPlayersByUUID().put(uuid, handle); level.addNewPlayer(handle);
        actors.add(handle);
        Player p = handle.getBukkitEntity(); p.setGravity(false); p.setInvulnerable(true); p.setGameMode(GameMode.SURVIVAL);
        return p;
    }
}
