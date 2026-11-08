import com.example.advancemagic.AdvanceMagicPlugin;
import com.example.voidscape.VoidscapePlugin;
import com.example.voidscape.boss.DragonNestBosses;
import com.example.voidscape.world.DragonNestLayout;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.entity.*;
import org.bukkit.event.Listener;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

/**
 * Dragon Nest hunt on real Paper (no ModelEngine or Geyser needed: the server-side hitbox, bone points, damage and
 * every spell run the same without them). Uses the admin test dragon so no world-gen nest is required.
 * <ul>
 *   <li>health rolls stay within 13,000-15,000 in steps of 10 and vary;</li>
 *   <li>each of the ten moves runs for all three elements without an exception, and the damaging ones land;</li>
 *   <li>props (meteors, ice spikes) appear, never place a block and are gone afterwards;</li>
 *   <li>the chase: prey 250 blocks away is pursued, beyond 300 it is dropped, beyond 1000 from the nest it is dropped,
 *       and the dragon itself never crosses the 1000-block ceiling;</li>
 *   <li>returning is untouchable and finishes off-screen; phase 2 queues the nova; a defeat cleans up.</li>
 * </ul>
 */
public final class DragonHuntProbe extends JavaPlugin implements Listener {
    VoidscapePlugin garden;
    DragonNestBosses dragons;
    World world;
    Player player;
    ServerPlayer actor;
    int checks;
    Method tick, start, clearSpells;
    Class<?> moveType, runType;
    final List<String> severe = new ArrayList<>();
    int ground;

    void check(boolean ok, String label) {
        if (!ok) throw new AssertionError(label);
        checks++;
        getLogger().info("PASS: " + label);
    }

    @Override
    public void onEnable() {
        Bukkit.getScheduler().runTaskLater(this, () -> {
            try { run(); finish(null); } catch (Throwable e) { finish(e); }
        }, 40);
    }

    EmbeddedChannel channel;
    long advanced;

    void advance(int n) throws Exception {
        for (int i = 0; i < n; i++) {
            tick.invoke(dragons);
            // The fake connection keeps every packet it is sent; drop them so particles cannot exhaust the heap.
            if (++advanced % 20 == 0 && channel != null) channel.releaseOutbound();
            if (!severe.isEmpty()) throw new AssertionError("dragon logged SEVERE: " + severe);
        }
    }

    Object get(Object o, String name) throws Exception {
        Field f = o.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(o);
    }

    void put(Object o, String name, Object value) throws Exception {
        Field f = o.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(o, value);
    }

    Map<?, ?> runs() throws Exception {
        return (Map<?, ?>) get(dragons, "runs");
    }

    Object testRun() throws Exception {
        for (Object r : runs().values()) if ((boolean) get(r, "test")) return r;
        return null;
    }

    DragonNestBosses.View view() {
        return dragons.views().stream().filter(DragonNestBosses.View::test).findFirst().orElse(null);
    }

    String state() throws Exception {
        Object r = testRun();
        return r == null ? "GONE" : get(r, "state").toString();
    }

    Location position() throws Exception {
        return (Location) get(testRun(), "position");
    }

    void load(Location at, int radius) {
        for (int x = (at.getBlockX() >> 4) - radius; x <= (at.getBlockX() >> 4) + radius; x++)
            for (int z = (at.getBlockZ() >> 4) - radius; z <= (at.getBlockZ() >> 4) + radius; z++) {
                world.addPluginChunkTicket(x, z, this);
                world.getChunkAt(x, z);
            }
    }

    void heal() {
        player.setHealth(player.getAttribute(Attribute.MAX_HEALTH).getValue());
        player.setFireTicks(0);
        player.setFreezeTicks(0);
        player.getActivePotionEffects().forEach(e -> player.removePotionEffect(e.getType()));
        player.setNoDamageTicks(0);
    }

    void place(Location at) {
        Location to = at.clone();
        to.setY(ground);
        load(to, 2);
        player.teleport(to);
        player.setVelocity(new Vector());
    }

    Vector facing() throws Exception {
        double yaw = Math.toRadians(position().getYaw());
        return new Vector(-Math.sin(yaw), 0, Math.cos(yaw));
    }

    void placeRelaysNow() throws Exception {
        Method place = DragonNestBosses.class.getDeclaredMethod("placeRelays", runType);
        place.setAccessible(true);
        place.invoke(dragons, testRun());
    }

    Object move(String name) {
        for (Object c : moveType.getEnumConstants()) if (c.toString().equals(name)) return c;
        throw new IllegalArgumentException(name);
    }

    long props() {
        return world.getEntities().stream().filter(e -> e instanceof FallingBlock
            && e.getPersistentDataContainer().has(garden.key("dragon_prop"), PersistentDataType.BYTE)).count();
    }

    void magic(double amount) throws Exception {
        Slime anchor = (Slime) get(testRun(), "anchor");
        ((AdvanceMagicPlugin) Bukkit.getPluginManager().getPlugin("advance-magic")).context().damage(player, anchor, amount, org.bukkit.damage.DamageType.INDIRECT_MAGIC);
    }

    String spawn(DragonNestLayout.Kind kind) throws Exception {
        dragons.clearTests();
        place(new Location(world, 0.5, ground, 0.5));
        player.teleport(new Location(world, 0.5, ground, 0.5, 0, 0));
        String text = dragons.spawnTest(player, kind);
        check(view() != null, kind + " test dragon spawns: " + text);
        return text;
    }

    void awaitHunt() throws Exception {
        for (int i = 0; i < 120 && !state().equals("HUNT") && !state().equals("MOVE"); i++) advance(1);
        check(state().equals("HUNT") || state().equals("MOVE"), "dragon wakes and hunts (" + state() + ")");
    }

    void run() throws Exception {
        garden = (VoidscapePlugin) Bukkit.getPluginManager().getPlugin("Evergarden");
        check(garden != null && garden.isEnabled(), "Evergarden boots");
        dragons = garden.dragonBosses();
        check(dragons != null, "dragon bosses are enabled");
        garden.getLogger().addHandler(new Handler() {
            @Override public void publish(LogRecord record) {
                if (record.getLevel().intValue() >= Level.SEVERE.intValue()) {
                    severe.add(record.getMessage() + (record.getThrown() == null ? "" : " " + record.getThrown()));
                    if (record.getThrown() != null) record.getThrown().printStackTrace();
                }
            }
            @Override public void flush() {}
            @Override public void close() {}
        });
        tick = DragonNestBosses.class.getDeclaredMethod("tick");
        tick.setAccessible(true);
        runType = Class.forName("com.example.voidscape.boss.DragonNestBosses$Run");
        moveType = Class.forName("com.example.voidscape.boss.DragonNestBosses$Move");
        start = DragonNestBosses.class.getDeclaredMethod("start", runType, moveType, Player.class);
        start.setAccessible(true);
        clearSpells = DragonNestBosses.class.getDeclaredMethod("clearSpells", runType);
        clearSpells.setAccessible(true);

        world = Bukkit.getWorlds().getFirst();
        world.setDifficulty(Difficulty.NORMAL);
        ground = world.getHighestBlockYAt(0, 0) + 1;
        load(new Location(world, 0, ground, 0), 6);
        var server = MinecraftServer.getServer();
        var level = ((CraftWorld) world).getHandle();
        var profile = new GameProfile(UUID.randomUUID(), "DragonTester");
        actor = new ServerPlayer(server, level, profile, ClientInformation.createDefault());
        var connection = new Connection(PacketFlow.SERVERBOUND);
        channel = new EmbeddedChannel();
        connection.channel = channel;
        connection.address = new InetSocketAddress("127.0.0.1", 1);
        actor.connection = new ServerGamePacketListenerImpl(server, connection, actor, CommonListenerCookie.createInitial(profile, false)) {
            @Override public boolean hasClientLoaded() { return true; }
        };
        actor.setPos(0.5, ground, 0.5);
        server.getPlayerList().getPlayers().add(actor);
        server.getPlayerList().getPlayersByUUID().put(profile.id(), actor);
        level.addNewPlayer(actor);
        player = actor.getBukkitEntity();
        player.setGameMode(GameMode.SURVIVAL);
        player.setGravity(false);
        player.getAttribute(Attribute.MAX_HEALTH).setBaseValue(1024);
        heal();

        // 1. Health roll.
        Set<Double> rolls = new HashSet<>();
        for (int i = 0; i < 24; i++) {
            dragons.spawnTest(player, DragonNestLayout.Kind.values()[i % 3]);
            double max = view().maxHealth();
            check(max >= 13000 && max <= 15000 && Math.abs(max / 10 - Math.round(max / 10)) < 1e-9 && view().health() == max,
                "health roll " + max + " is within 13,000-15,000 in steps of 10");
            rolls.add(max);
        }
        check(rolls.size() >= 6, "health rolls vary (" + rolls.size() + " distinct of 24)");

        // 2. Every move for every element.
        String[] order = {"BITE", "CLAW", "TAIL_WHIP", "WING_GUST", "BREATH", "BARRAGE", "SWOOP", "STRAFE", "MAGIC", "NOVA"};
        Set<String> mustHit = Set.of("BITE", "CLAW", "TAIL_WHIP", "WING_GUST", "BREATH", "BARRAGE", "MAGIC", "NOVA");
        for (DragonNestLayout.Kind kind : DragonNestLayout.Kind.values()) {
            spawn(kind);
            awaitHunt();
            long peakProps = 0;
            for (String name : order) {
                Object r = testRun();
                check(r != null, kind + " dragon survives until " + name);
                clearSpells.invoke(dragons, r);
                put(r, "action", null);
                put(r, "state", get(r, "state").getClass().getEnumConstants()[2]); // HUNT
                Location at = position().clone();
                Vector f = facing();
                double distance = switch (name) {
                    case "BITE" -> 18.4;
                    case "CLAW" -> 14.6;
                    case "TAIL_WHIP" -> -14;
                    case "WING_GUST" -> 14;
                    case "BREATH" -> 36;
                    case "NOVA" -> 20;
                    default -> 30;
                };
                place(at.clone().add(f.clone().multiply(distance)));
                heal();
                double before = player.getHealth();
                start.invoke(dragons, r, move(name), player);
                int length = (int) get(move(name), "length");
                double lowest = before;
                double nearestEnd = Double.POSITIVE_INFINITY;
                String trace = "";
                Method bone = DragonNestBosses.class.getDeclaredMethod("bone", runType, String.class, String.class, double.class);
                bone.setAccessible(true);
                Method end = DragonNestBosses.class.getDeclaredMethod("breathEnd", runType, Class.forName("com.example.voidscape.boss.DragonNestBosses$Action"), Location.class, double.class);
                end.setAccessible(true);
                for (int i = 0; i < length + (name.equals("MAGIC") ? 90 : 12); i++) {
                    advance(1);
                    lowest = Math.min(lowest, player.getHealth());
                    Object action = testRun() == null ? null : get(testRun(), "action");
                    if (name.equals("BREATH") && action != null && i >= 19 && i <= 50) {
                        Location mouth = (Location) bone.invoke(dragons, testRun(), "fx_mouth", "breath", i / 20.0);
                        Location e = (Location) end.invoke(dragons, testRun(), action, mouth, (double) get(action, "reach"));
                        double gap = e.distance(player.getLocation().add(0, 0.9, 0));
                        if (gap < nearestEnd) { nearestEnd = gap; trace = "mouth " + mouth.toVector() + " end " + e.toVector() + " prey " + player.getLocation().toVector() + " anchor " + position().toVector() + " yaw " + position().getYaw() + " reach " + get(action, "reach") + " aimY " + get(action, "aimY"); }
                    }
                    peakProps = Math.max(peakProps, props());
                    if (testRun() == null) break;
                }
                check(testRun() != null, kind + " " + name + " runs to the end without an error");
                if (mustHit.contains(name)) check(lowest < before, kind + " " + name + " damages the prey (" + (int) (before - lowest) + ")" + (trace.isEmpty() ? "" : " nearest end " + nearestEnd + " " + trace));
            }
            if (kind != DragonNestLayout.Kind.STORM) check(peakProps > 0, kind + " spells raise visible props (" + peakProps + ")");
            Object r = testRun();
            clearSpells.invoke(dragons, r);
            advance(2);
            check(props() == 0, kind + " leaves no props behind");
            int placed = 0;
            Location c = position();
            for (int x = -48; x <= 48; x++) for (int z = -48; z <= 48; z++) for (int y = ground - 2; y <= ground + 4; y++) {
                Material m = world.getBlockAt(c.getBlockX() + x, y, c.getBlockZ() + z).getType();
                if (m == Material.MAGMA_BLOCK || m == Material.BLUE_ICE || m == Material.PACKED_ICE || m == Material.ICE || m == Material.BLACKSTONE) placed++;
            }
            check(placed == 0, kind + " spells never place a block");
        }

        // 3. Chase radius: pursued at 250, dropped beyond 300; returning is untouchable and finishes off-screen.
        heal();
        spawn(DragonNestLayout.Kind.FIRE);
        awaitHunt();
        Location home = view().center();
        Location far = position().clone().add(250, 0, 0);
        for (int x = 0; x <= 620; x += 48) load(position().clone().add(x, 0, 0), 3);
        place(far);
        double startGap = position().distance(player.getLocation());
        boolean returning = false;
        for (int i = 0; i < 80; i++) {
            advance(1);
            if (state().equals("RETURNING")) returning = true;
        }
        check(!returning, "prey 250 blocks away keeps the hunt alive");
        check(position().distance(player.getLocation()) < startGap - 40, "the dragon flies after prey 250 blocks away ("
            + (int) startGap + " -> " + (int) position().distance(player.getLocation()) + ")");
        Location gone = position().clone().add(330, 0, 0);
        load(gone, 3);
        place(gone);
        advance(2);
        check(state().equals("RETURNING"), "prey beyond 300 blocks is dropped and the dragon heads home (" + state() + ")");
        double hp = view().health();
        magic(500);
        check(view().health() == hp, "a returning dragon takes no damage");
        Location p0 = position().clone();
        advance(5);
        check(position().distance(p0) <= 5 * 0.6 + 0.01, "the flight home is slow (" + position().distance(p0) + " in 5 ticks)");
        for (int i = 0; i < 90 && testRun() != null; i++) advance(1);
        check(testRun() == null, "an unwatched flight home completes (test dragon retired)");

        // 4. Max roam: the dragon never crosses 1000 blocks from its nest, and prey past it is dropped.
        heal();
        spawn(DragonNestLayout.Kind.STORM);
        awaitHunt();
        home = view().center();
        Location edge = home.clone().add(996, 0, 0);
        load(edge, 4);
        Location dragonAt = home.clone().add(980, 12, 0);
        dragonAt.setYaw(-90);
        put(testRun(), "position", dragonAt);
        place(edge.clone().add(0, 0, 3));
        double farthest = 0;
        for (int i = 0; i < 60; i++) {
            advance(1);
            Location p = position();
            farthest = Math.max(farthest, Math.hypot(p.getX() - home.getX(), p.getZ() - home.getZ()));
        }
        check(!state().equals("RETURNING"), "prey within 1000 blocks of the nest is still hunted");
        check(farthest <= 1000.001, "the dragon never crosses the 1000-block ceiling (" + farthest + ")");
        Location outside = home.clone().add(1060, 0, 0);
        load(outside, 3);
        place(outside);
        advance(2);
        check(state().equals("RETURNING"), "prey beyond 1000 blocks of the nest escapes (" + state() + ")");
        dragons.clearTests();

        // 5. Arrows and spells land on the body, neck, head, tail and wings, not only on the anchor under the hind legs.
        heal();
        spawn(DragonNestLayout.Kind.FIRE);
        awaitHunt();
        @SuppressWarnings("unchecked") List<Slime> relays = (List<Slime>) get(testRun(), "relays");
        check(relays.size() == 7 && relays.stream().allMatch(Entity::isValid), "seven hitboxes ride the dragon's bones (" + relays.size() + ")");
        Slime head = relays.get(2);
        double headGap = head.getLocation().distance(position());
        check(headGap > 12, "the head hitbox follows the head, " + Math.round(headGap) + " blocks from the anchor");
        place(position().clone().add(facing().multiply(34)));
        Location eye = player.getEyeLocation();
        Vector aim = head.getBoundingBox().getCenter().subtract(eye.toVector()).normalize();
        Arrow arrow = world.spawnArrow(eye.clone().add(aim.clone().multiply(1.5)), aim, 3.0f, 0f);
        arrow.setShooter(player);
        arrow.setGravity(false);
        double before = view().health();
        for (int i = 0; i < 40 && arrow.isValid() && view().health() == before; i++) {
            ((org.bukkit.craftbukkit.entity.CraftArrow) arrow).getHandle().tick();
            advance(1);
        }
        check(view().health() < before, "an arrow aimed at the dragon's head deals damage (" + Math.round(before - view().health()) + ")");
        check(!arrow.isValid(), "the arrow that struck is spent instead of bouncing off");
        advance(10);
        Slime wingBox = relays.get(5);
        Vector wingAim = wingBox.getBoundingBox().getCenter().subtract(player.getEyeLocation().toVector()).normalize();
        Arrow second = world.spawnArrow(player.getEyeLocation().add(wingAim.clone().multiply(1.5)), wingAim, 3.0f, 0f);
        second.setShooter(player);
        second.setGravity(false);
        before = view().health();
        for (int i = 0; i < 40 && second.isValid() && view().health() == before; i++) {
            ((org.bukkit.craftbukkit.entity.CraftArrow) second).getHandle().tick();
            advance(1);
        }
        check(view().health() < before, "an arrow into the wing deals damage too (" + Math.round(before - view().health()) + ")");
        Arrow miss = world.spawnArrow(player.getEyeLocation(), new Vector(0, 1, 0), 3.0f, 0f);
        miss.setShooter(player);
        miss.setGravity(false);
        advance(10);
        before = view().health();
        for (int i = 0; i < 20 && miss.isValid(); i++) {
            ((org.bukkit.craftbukkit.entity.CraftArrow) miss).getHandle().tick();
            advance(1);
        }
        check(view().health() == before, "an arrow shot straight up past the dragon does nothing");
        miss.remove();
        advance(10);
        var context = ((AdvanceMagicPlugin) Bukkit.getPluginManager().getPlugin("advance-magic")).context();
        List<LivingEntity> near = context.nearby(player, head.getLocation(), 3.5, false);
        check(near.stream().anyMatch(relays::contains), "a spell bursting on the head finds the dragon");
        LivingEntity struck = near.stream().filter(relays::contains).findFirst().orElseThrow();
        before = view().health();
        context.damage(player, struck, 250, org.bukkit.damage.DamageType.INDIRECT_MAGIC);
        check(view().health() < before, "spell damage on the head reaches the dragon (" + Math.round(before - view().health()) + ")");
        advance(10);
        Slime wing = relays.get(5);
        before = view().health();
        context.damage(player, wing, 250, org.bukkit.damage.DamageType.INDIRECT_MAGIC);
        check(view().health() < before, "a spell on the wing counts too");
        before = view().health();
        context.damage(player, relays.get(0), 250, org.bukkit.damage.DamageType.INDIRECT_MAGIC);
        check(view().health() == before, "a second hitbox in the same moment does not double the hit (cooldown)");
        dragons.clearTests();
        check(relays.stream().noneMatch(Entity::isValid), "clearing the dragon removes its hitboxes");

        // 6. Phases and defeat.
        heal();
        spawn(DragonNestLayout.Kind.ICE);
        awaitHunt();
        place(position().clone().add(facing().multiply(20)));
        double max = view().maxHealth();
        magic(max * 0.45);
        check(view().health() < max && view().phase() == 2, "phase two at 60% (" + view().phase() + ")");
        boolean nova = false;
        for (int i = 0; i < 400 && !nova; i++) {
            if (i % 40 == 0) heal();
            advance(1);
            nova = "NOVA".equals(view().move());
        }
        check(nova, "phase two opens with the nova");
        // Scales: 1 for 3%+, 2 from 12%, +1 for the top damage dealer (real nests only; driven directly here).
        Object run = testRun();
        @SuppressWarnings("unchecked") Map<UUID, Double> dealt = (Map<UUID, Double>) get(run, "dealt");
        dealt.put(player.getUniqueId(), view().maxHealth() * 0.5);
        player.getInventory().clear();
        Method grant = DragonNestBosses.class.getDeclaredMethod("grantScales", runType);
        grant.setAccessible(true);
        grant.invoke(dragons, run);
        int scales = 0;
        for (var item : player.getInventory().getContents())
            if (com.example.voidscape.boss.DragonScales.identify(garden, item) == DragonNestLayout.Kind.ICE) scales += item.getAmount();
        check(scales == 3, "the top hunter with half the damage earns three frost scales (" + scales + ")");
        dealt.put(player.getUniqueId(), view().maxHealth() * 0.02);
        player.getInventory().clear();
        grant.invoke(dragons, run);
        check(java.util.Arrays.stream(player.getInventory().getContents()).noneMatch(i -> com.example.voidscape.boss.DragonScales.identify(garden, i) != null),
            "a hunter under 3% of the damage earns no scale");
        phaseThree();
        for (int i = 0; i < 400 && view() != null && view().health() > 0; i++) {
            if (i % 40 == 0) heal();
            magic(4000);
            advance(1);
        }
        advance(2);
        check(view() == null, "a defeated test dragon is cleaned up");
        check(props() == 0, "defeat leaves no props");
    }

    /** e2.196: at 25% the enraged dragon adapts like Phlegethon and its blows deal twice their damage. */
    void phaseThree() throws Exception {
        heal();
        // A dragon ignores a player's hits for 8 ticks after the last accepted one.
        advance(10);
        magic(Math.max(1, view().health() - view().maxHealth() * 0.20));
        advance(10);
        check(view().phase() == 3, "phase three (enraged) at 25% (phase " + view().phase() + ", health "
            + Math.round(100 * view().health() / view().maxHealth()) + "%, state " + state() + ")");
        Object enraged = testRun();
        double[] dealt = new double[5];
        org.bukkit.inventory.ItemStack[] held = {null, null, null, new org.bukkit.inventory.ItemStack(Material.DIAMOND_SWORD), null};
        for (int i = 0; i < dealt.length; i++) {
            heal();
            player.getInventory().setItemInMainHand(held[i]);
            double before = view().health();
            magic(200);
            dealt[i] = before - view().health();
            advance(10);
        }
        check(dealt[0] > 0 && Math.abs(dealt[1] - dealt[0] / 2) < 1e-6 && Math.abs(dealt[2] - dealt[0] / 4) < 1e-6,
            "the enraged dragon halves each repeated spell from the same held item (" + Math.round(dealt[0]) + ", " + Math.round(dealt[1]) + ", " + Math.round(dealt[2]) + ")");
        check(Math.abs(dealt[3] - dealt[0]) < 1e-6, "switching to a sword resets the dragon's adaptation");
        check(Math.abs(dealt[4] - dealt[0]) < 1e-6, "returning to bare hands starts at full damage");
        // Outgoing: phase three = enraged x1.15 (as before) x2 (e2.196), measured against the same blow in phase two.
        Method strike = DragonNestBosses.class.getDeclaredMethod("strike", runType, Player.class, double.class, boolean.class);
        strike.setAccessible(true);
        var maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        double oldMax = maxHealth.getBaseValue();
        Difficulty oldDifficulty = world.getDifficulty();
        maxHealth.setBaseValue(1000);
        world.setDifficulty(Difficulty.NORMAL);
        try {
            heal();
            double before = player.getHealth();
            strike.invoke(dragons, enraged, player, 10.0, false);
            double third = before - player.getHealth();
            put(enraged, "phase", 2);
            heal();
            before = player.getHealth();
            strike.invoke(dragons, enraged, player, 10.0, false);
            double second = before - player.getHealth();
            put(enraged, "phase", 3);
            check(second > 0 && Math.abs(third / second - 2.3) < 0.01,
                "an enraged dragon's spell hits 2.3x its phase-two damage (" + String.format(java.util.Locale.ROOT, "%.2f vs %.2f", third, second) + ")");
        } finally {
            world.setDifficulty(oldDifficulty);
            maxHealth.setBaseValue(oldMax);
            heal();
        }
    }

    void finish(Throwable error) {
        try {
            Files.writeString(Path.of("arkhaia-result.txt"), error == null ? "PASS " + checks + " Dragon Nest hunt Paper checks" : "FAIL " + error);
        } catch (Exception ignored) {}
        if (error != null) getLogger().log(Level.SEVERE, "Dragon probe failed", error);
        if (dragons != null) dragons.clearTests();
        if (actor != null) {
            var server = MinecraftServer.getServer();
            server.getPlayerList().getPlayers().remove(actor);
            server.getPlayerList().getPlayersByUUID().remove(actor.getUUID());
            actor.discard();
        }
        Bukkit.getScheduler().runTaskLater(this, Bukkit::shutdown, 5);
    }
}
