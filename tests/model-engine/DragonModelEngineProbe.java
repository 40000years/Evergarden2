import com.example.voidscape.VoidscapePlugin;
import com.example.voidscape.boss.DragonNestBosses;
import com.example.voidscape.boss.RealmBosses;
import com.example.voidscape.world.DragonNestLayout;
import com.mojang.authlib.GameProfile;
import com.ticxo.modelengine.api.ModelEngineAPI;
import com.ticxo.modelengine.api.animation.handler.IStateMachineHandler;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.bukkit.*;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.entity.*;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Real Paper + ModelEngine 4.1.1 + Geyser: the Dragon Nest dragons' attack clips live in the merged realm_bosses
 * blueprint (still one ModelEngine slot). Each test dragon attaches its own rig only, registers a native Bedrock
 * carrier when Geyser's entity API is ready, and ModelEngine plays the bite, claw, breath and cast clips when the
 * hunt starts those moves; clearing removes rig, anchor and carrier.
 */
public final class DragonModelEngineProbe extends JavaPlugin {
    VoidscapePlugin garden;
    DragonNestBosses dragons;
    World world;
    Player hunter;
    int checks, waited, kindIndex;
    final List<String> lines = new ArrayList<>();
    boolean finished;
    static final String[] MOVES = {"BITE", "CLAW", "BREATH", "MAGIC"};
    static final Map<String, String> CLIP = Map.of("BITE", "bite", "CLAW", "claw", "BREATH", "breath", "MAGIC", "cast");

    void check(boolean ok, String label) {
        if (!ok) throw new AssertionError(label);
        checks++;
        lines.add("PASS: " + label);
        getLogger().info("PASS: " + label);
    }

    void later(long ticks, Runnable work) {
        Bukkit.getScheduler().runTaskLater(this, () -> { try { work.run(); } catch (Throwable error) { finish(error); } }, ticks);
    }

    @Override
    public void onEnable() {
        later(1, () -> {
            garden = (VoidscapePlugin) Bukkit.getPluginManager().getPlugin("Evergarden");
            check(garden != null && garden.isEnabled(), "Evergarden enables");
            dragons = garden.dragonBosses();
            check(dragons != null, "dragon bosses enabled");
            await();
        });
    }

    void await() {
        var models = garden.arkhaiaModels();
        boolean ready = models != null && models.ready() && RealmBosses.DRAGON_MODELS.stream().allMatch(models::ready);
        if (!ready) {
            if ((waited += 10) > 2400) throw new AssertionError("dragon rigs never became ready: " + (models == null ? "no ModelEngine" : models.modelInfo()));
            later(10, this::await);
            return;
        }
        begin();
    }

    void begin() {
        check(ModelEngineAPI.getBlueprint("internal_fire") != null, "internal_fire still imports beside the merged blueprint");
        var merged = ModelEngineAPI.getBlueprint(RealmBosses.BLUEPRINT);
        check(merged != null, "merged realm blueprint imported");
        var clips = merged.getAnimations().keySet();
        for (String model : RealmBosses.DRAGON_MODELS)
            for (String clip : RealmBosses.DRAGON_CLIPS)
                check(clips.contains(model + "_" + clip), "ModelEngine imported " + model + "_" + clip);
        world = Bukkit.getWorlds().getFirst();
        int y = world.getHighestBlockYAt(0, 0) + 1;
        for (int x = -6; x <= 6; x++) for (int z = -6; z <= 6; z++) world.addPluginChunkTicket(x, z, this);
        hunter = fake(new Location(world, 0.5, y, 0.5, 0, 0), "DragonHunter").getBukkitEntity();
        hunter.setGameMode(GameMode.SURVIVAL);
        hunter.setInvulnerable(true);
        Bukkit.getPluginManager().callEvent(new org.bukkit.event.player.PlayerResourcePackStatusEvent(hunter,
            com.example.voidscape.boss.JudgePackService.PACK_ID, org.bukkit.event.player.PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED));
        nextKind();
    }

    Object get(Object o, String name) throws ReflectiveOperationException {
        Field f = o.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(o);
    }

    Object testRun() throws ReflectiveOperationException {
        for (Object r : ((Map<?, ?>) get(dragons, "runs")).values()) if ((boolean) get(r, "test")) return r;
        return null;
    }

    void nextKind() {
        if (kindIndex >= DragonNestLayout.Kind.values().length) {
            finish(null);
            return;
        }
        DragonNestLayout.Kind kind = DragonNestLayout.Kind.values()[kindIndex];
        hunter.teleport(new Location(world, 0.5, world.getHighestBlockYAt(0, 0) + 1, 0.5, 0, 0));
        lines.add("spawn: " + dragons.spawnTest(hunter, kind));
        later(10, () -> inspect(kind));
    }

    void inspect(DragonNestLayout.Kind kind) {
        try {
            Object run = testRun();
            check(run != null, kind + ": test dragon is loaded");
            Slime anchor = (Slime) get(run, "anchor");
            var modeled = ModelEngineAPI.getModeledEntity(anchor.getUniqueId());
            check(modeled != null && modeled.getModel(RealmBosses.BLUEPRINT).isPresent(), kind + ": merged rig attached to the anchor");
            var model = modeled.getModel(RealmBosses.BLUEPRINT).get();
            var bones = model.getBones().keySet();
            long own = bones.stream().filter(b -> RealmBosses.owns(kind.modelId, b)).count();
            check(own > 60 && own == bones.size(), kind + ": only its own bones remain (" + own + " of " + bones.size() + ")");
            Object visual = get(run, "visual");
            ArmorStand carrier = (ArmorStand) get(visual, "carrier");
            if (garden.arkhaiaBedrock() != null && garden.arkhaiaBedrock().ready())
                check(carrier != null && garden.arkhaiaBedrock().registered(carrier.getUniqueId()), kind + ": native Bedrock carrier registered");
            else if (kindIndex == 0) lines.add("NOTE: Geyser entity API not ready in this trial; Bedrock carrier not exercised");
            var data = modeled.getBase().getData();
            check(Boolean.FALSE.equals(data.getBackCull()) && Boolean.FALSE.equals(data.getVerticalCull()) && Boolean.FALSE.equals(data.getBlockedCull()),
                kind + ": behind-viewer, vertical and blocked culling are off (the rig never freezes in its bind pose)");
            var handler = (IStateMachineHandler) model.getAnimationHandler();
            // Prey in the air: the dragon takes off and ModelEngine must play its flight loop.
            Location ground = hunter.getLocation().clone();
            hunter.teleport(ground.clone().add(0, 28, 50));
            later(80, () -> {
                check(handler.getAnimation(10, kind.modelId + "_fly") != null, kind + ": ModelEngine plays " + kind.modelId + "_fly while it chases prey in the air");
                check("fly".equals(dragons.views().stream().filter(DragonNestBosses.View::test).findFirst().orElseThrow().clip()), kind + ": the Bedrock frame flies too");
                hunter.teleport(ground);
                later(5, () -> playMoves(kind, run, handler, anchor, carrier, 0));
            });
        } catch (ReflectiveOperationException error) {
            throw new RuntimeException(error);
        }
    }

    void playMoves(DragonNestLayout.Kind kind, Object run, IStateMachineHandler handler, Slime anchor, ArmorStand carrier, int index) {
        if (index >= MOVES.length) {
            check(dragons.clearTests() == 1, kind + ": clear removes the test dragon");
            later(4, () -> {
                check(!anchor.isValid() && ModelEngineAPI.getModeledEntity(anchor.getUniqueId()) == null && (carrier == null || !carrier.isValid()),
                    kind + ": anchor, rig and carrier are gone");
                kindIndex++;
                nextKind();
            });
            return;
        }
        try {
            Class<?> runType = Class.forName("com.example.voidscape.boss.DragonNestBosses$Run");
            Class<?> moveType = Class.forName("com.example.voidscape.boss.DragonNestBosses$Move");
            Object move = Arrays.stream(moveType.getEnumConstants()).filter(m -> m.toString().equals(MOVES[index])).findFirst().orElseThrow();
            Method start = DragonNestBosses.class.getDeclaredMethod("start", runType, moveType, Player.class);
            start.setAccessible(true);
            Field action = runType.getDeclaredField("action");
            action.setAccessible(true);
            if (action.get(run) != null) {
                later(5, () -> playMoves(kind, run, handler, anchor, carrier, index));
                return;
            }
            start.invoke(dragons, run, move, hunter);
            String clip = kind.modelId + "_" + CLIP.get(MOVES[index]);
            later(4, () -> {
                check(handler.getAnimation(10, clip) != null, kind + ": ModelEngine plays " + clip + " for " + MOVES[index]);
                check(CLIP.get(MOVES[index]).equals(dragons.views().stream().filter(DragonNestBosses.View::test).findFirst().orElseThrow().clip()),
                    kind + ": the Bedrock frame uses the same clip");
                int length = 0;
                try { length = (int) get(move, "length"); } catch (ReflectiveOperationException e) { throw new RuntimeException(e); }
                later(length + 4, () -> playMoves(kind, run, handler, anchor, carrier, index + 1));
            });
        } catch (ReflectiveOperationException error) {
            throw new RuntimeException(error);
        }
    }

    ServerPlayer fake(Location at, String name) {
        var server = MinecraftServer.getServer();
        var level = ((CraftWorld) world).getHandle();
        var profile = new GameProfile(UUID.randomUUID(), name);
        var handle = new ServerPlayer(server, level, profile, ClientInformation.createDefault());
        var connection = new Connection(PacketFlow.SERVERBOUND);
        connection.channel = new EmbeddedChannel();
        connection.address = new InetSocketAddress("127.0.0.1", 1);
        handle.connection = new ServerGamePacketListenerImpl(server, connection, handle, CommonListenerCookie.createInitial(profile, false)) {
            @Override public boolean hasClientLoaded() { return true; }
        };
        handle.setPos(at.getX(), at.getY(), at.getZ());
        server.getPlayerList().getPlayers().add(handle);
        server.getPlayerList().getPlayersByUUID().put(profile.id(), handle);
        level.addNewPlayer(handle);
        handle.getBukkitEntity().setGravity(false);
        return handle;
    }

    void finish(Throwable error) {
        if (finished) return;
        finished = true;
        if (error != null) { error.printStackTrace(); lines.add("ERROR: " + error); }
        lines.add(0, (error == null ? "PASS" : "FAIL") + ": dragon hunt ModelEngine / " + checks + " checks");
        try { Files.write(Path.of("model-engine-result.txt"), lines); } catch (Exception e) { getLogger().warning(e.toString()); }
        if (dragons != null) dragons.clearTests();
        Bukkit.getScheduler().runTaskLater(this, Bukkit::shutdown, 5);
    }
}
