import com.example.voidscape.boss.RealmBosses;
import com.example.voidscape.boss.RealmBossController;
import com.example.voidscape.VoidscapePlugin;
import com.example.voidscape.world.DimensionalSanctuaries;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;
import java.util.function.BiConsumer;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/** Exercises real Paper block and bounding-box ray tracing, not just the targeting math. */
public final class RealmEndAntiAirProbe {
    private static Object helper;
    private static Method tick, reset;
    private static int hits;
    private static Location boss, center;
    private static Player player;

    static void run(World world, Player subject, BiConsumer<Boolean, String> check) {
        try {
            player = subject;
            center = new Location(world, 256.5, 120, 256.5);
            boss = center.clone();
            Class<?> type = Class.forName("com.example.voidscape.boss.EndAntiAir");
            Constructor<?> ctor = type.getDeclaredConstructor(RealmBosses.Kind.class, BiConsumer.class);
            ctor.setAccessible(true);
            tick = type.getDeclaredMethod("tick", List.class, Location.class, Location.class, boolean.class);
            reset = type.getDeclaredMethod("reset");
            tick.setAccessible(true); reset.setAccessible(true);
            for (RealmBosses.Kind kind : List.of(RealmBosses.Kind.CRYSTAL_MOTH, RealmBosses.Kind.UNLIT_SUN)) {
                helper = ctor.newInstance(kind, (BiConsumer<Player,Double>)(p, amount) -> hits++);
                prepare(GameMode.SURVIVAL);
                boolean held = advance(10, true);
                check.accept(held && hits == 0, kind + ": airborne warning holds the main attack without dealing early damage");
                advance(19, true);
                check.accept(hits == 0, kind + ": chorus cannot hit during the 20-tick warning");
                advance(45, true);
                check.accept(hits == 1 && player.hasPotionEffect(PotionEffectType.LEVITATION), kind + ": locked chorus hits once and causes levitation");

                prepare(GameMode.SURVIVAL);
                advance(30, true); // The target point is locked before it can move.
                player.teleport(center.clone().add(8, 10, 20));
                ((CraftPlayer)player).getHandle().setOnGround(false);
                advance(45, true);
                check.accept(hits == 0 && !player.hasPotionEffect(PotionEffectType.LEVITATION), kind + ": moving sideways dodges the non-tracking projectile");

                prepare(GameMode.SURVIVAL);
                for (int x = 254; x <= 259; x++) for (int y = 120; y <= 138; y++)
                    world.getBlockAt(x, y, 266).setType(Material.STONE, false);
                advance(75, true);
                check.accept(hits == 0, kind + ": solid wall blocks chorus instead of letting it hit through cover");
                for (int x = 254; x <= 259; x++) for (int y = 120; y <= 138; y++)
                    world.getBlockAt(x, y, 266).setType(Material.AIR, false);

                prepare(GameMode.SURVIVAL);
                advance(10, true); advance(45, false);
                check.accept(hits == 0, kind + ": stun or transition cancels a pending warning");

                prepare(GameMode.CREATIVE);
                advance(75, true);
                check.accept(hits == 0 && !player.hasPotionEffect(PotionEffectType.LEVITATION), kind + ": creative players are ignored");

                prepare(GameMode.SURVIVAL);
                player.teleport(center.clone().add(0, 0, 20));
                advance(75, true);
                check.accept(hits == 0, kind + ": players at floor height are not targeted");

                prepare(GameMode.SURVIVAL);
                advance(30, true);
                player.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 200, 1));
                advance(45, true);
                var effect = player.getPotionEffect(PotionEffectType.LEVITATION);
                check.accept(hits == 1 && effect != null && effect.getAmplifier() == 1, kind + ": existing stronger levitation is not overwritten");
                reset.invoke(helper);
            }
            player.removePotionEffect(PotionEffectType.LEVITATION);
            player.setGameMode(GameMode.CREATIVE);
            player.teleport(new Location(world,256.5,120,236.5,0,0));
        } catch (ReflectiveOperationException e) { throw new RuntimeException(e); }
    }

    static void controller(VoidscapePlugin garden, World world, Player subject, BiConsumer<Boolean, String> check) {
        RealmBossController boss = null;
        try {
            player = subject;
            center = new Location(world, 256.5, 120, 256.5);
            var site = new DimensionalSanctuaries.Site(DimensionalSanctuaries.Kind.CRYSTAL_CITY, 256, 120, 256, 0, 0, 1);
            var arena = new DimensionalSanctuaries.Arena(256, 120, 256, 30, 20);
            boss = new RealmBossController(garden, site, arena, center, p -> p == subject, d -> {});
            boss.spawnWaiting(1);
            subject.setGameMode(GameMode.SURVIVAL);
            subject.removePotionEffect(PotionEffectType.LEVITATION);
            subject.teleport(center.clone().add(0, 10, 20));
            ((CraftPlayer)subject).getHandle().setOnGround(false);
            boss.awaken(List.of(subject));
            var action = RealmBossController.class.getDeclaredField("action");
            var cooldown = RealmBossController.class.getDeclaredField("cooldownTicks");
            var helperField = RealmBossController.class.getDeclaredField("endAntiAir");
            action.setAccessible(true); cooldown.setAccessible(true); helperField.setAccessible(true);
            @SuppressWarnings({"rawtypes", "unchecked"})
            Object none = Enum.valueOf((Class<? extends Enum>)action.getType(), "NONE");
            action.set(boss, none); cooldown.setInt(boss, 200);
            for (int n = 0; n < 9; n++) boss.tick(100 + n);
            cooldown.setInt(boss, 0);
            boss.tick(109);
            Object anti = helperField.get(boss);
            var warning = anti.getClass().getDeclaredField("warning"); warning.setAccessible(true);
            check.accept(warning.getInt(anti) == 20 && action.get(boss) == none,
                "real End controller begins anti-air warning and holds the otherwise-ready main attack");
            for (int n = 0; n < 19; n++) boss.tick(110 + n);
            check.accept(action.get(boss) == none && warning.getInt(anti) == 1,
                "real End controller preserves the complete warning window");
            boss.tick(129);
            check.accept(warning.getInt(anti) == 0 && action.get(boss) != none,
                "real End controller resumes its main action after launching the chorus shot");
        } catch (ReflectiveOperationException e) { throw new RuntimeException(e); }
        finally {
            if (boss != null) boss.close();
            subject.removePotionEffect(PotionEffectType.LEVITATION);
            subject.setGameMode(GameMode.CREATIVE);
            subject.teleport(new Location(world,256.5,120,236.5,0,0));
        }
    }

    private static void prepare(GameMode mode) throws ReflectiveOperationException {
        reset.invoke(helper);
        hits = 0;
        player.removePotionEffect(PotionEffectType.LEVITATION);
        player.setGameMode(mode);
        player.teleport(center.clone().add(0, 10, 20));
        ((CraftPlayer)player).getHandle().setOnGround(false);
    }

    private static boolean advance(int count, boolean available) throws ReflectiveOperationException {
        boolean held = false;
        for (int i = 0; i < count; i++) held |= (Boolean)tick.invoke(helper, List.of(player), boss, center, available);
        return held;
    }
}
