import com.example.voidscape.VoidscapePlugin;
import com.example.voidscape.boss.RealmBosses;
import com.example.voidscape.boss.RealmBossController;
import com.example.voidscape.world.DimensionalSanctuaries;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.data.BlockData;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/** Ledge campers are hunted, fliers and floor fighters never are, and leftover blocks leave the arena. */
public final class RealmPerchProbe {
    private static Object hunt;
    private static Method tick, skyfall, reset;
    private static Field perches;
    private static Location center;
    private static Player player;
    private static final List<Double> damages = new ArrayList<>();
    private static int clears;

    static void run(World world, Player subject, BiConsumer<Boolean, String> check) {
        try {
            player = subject;
            center = new Location(world, 256.5, 120, 256.5);
            Class<?> type = Class.forName("com.example.voidscape.boss.PerchHunt");
            Class<?> hooks = Class.forName("com.example.voidscape.boss.PerchHunt$Hooks");
            Object recorder = Proxy.newProxyInstance(hooks.getClassLoader(), new Class<?>[]{hooks}, (proxy, method, args) -> switch (method.getName()) {
                case "strike" -> { damages.add((Double) args[1]); yield true; }
                case "clear" -> { clears++; yield 0; }
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> "hooks";
            });
            Constructor<?> ctor = type.getDeclaredConstructor(RealmBosses.Kind.class, Location.class, double.class, int.class, hooks);
            ctor.setAccessible(true);
            tick = type.getDeclaredMethod("tick", List.class, Location.class);
            skyfall = type.getDeclaredMethod("skyfall", Player.class);
            reset = type.getDeclaredMethod("reset");
            perches = type.getDeclaredField("perches");
            tick.setAccessible(true); skyfall.setAccessible(true); reset.setAccessible(true); perches.setAccessible(true);

            for (RealmBosses.Kind kind : RealmBosses.Kind.values()) {
                hunt = ctor.newInstance(kind, center, 30.0, 60, recorder);
                stand(GameMode.SURVIVAL, 0, 6, 20, true);
                advance(90);
                check.accept(damages.equals(List.of(12.0)) && clears == 1, kind + ": a ledge camper is struck once (12) and the perch is swept");
            }
            hunt = ctor.newInstance(RealmBosses.Kind.CINDER_COLOSSUS, center, 30.0, 60, recorder);

            stand(GameMode.SURVIVAL, 0, 6, 20, false);
            advance(200);
            check.accept(damages.isEmpty() && mark() == null && heat() == 0, "Wind Charge / Mace flight at any height builds no heat");
            stand(GameMode.SURVIVAL, 0, 0, 20, true);
            advance(200);
            check.accept(damages.isEmpty(), "floor fighters are never hunted");
            stand(GameMode.SURVIVAL, 0, 0, 29.5, true);
            advance(200);
            check.accept(damages.isEmpty(), "standing just inside the drawn ring is fine");
            stand(GameMode.CREATIVE, 0, 6, 20, true);
            advance(200);
            check.accept(damages.isEmpty(), "creative players are ignored");

            stand(GameMode.SURVIVAL, 0, 6, 20, true);
            advance(59);
            check.accept(mark() == null, "the first three seconds on a perch are free");
            advance(1);
            check.accept(mark() != null && warning() == 30 && damages.isEmpty(), "the 60th perch tick calls a 30-tick warning without damage");
            advance(8);
            place(3, 6, 20, true);
            advance(22);
            check.accept(damages.equals(List.of(12.0)), "the mark follows the camper along the ledge");
            Vector inward = center.toVector().subtract(player.getLocation().toVector()).setY(0).normalize();
            Vector thrown = player.getVelocity();
            check.accept(thrown.clone().setY(0).normalize().dot(inward) > 0.95 && thrown.getY() > 0, "the strike throws the camper back toward the arena: " + thrown);
            advance(80);
            check.accept(damages.equals(List.of(12.0, 16.0)), "camping on escalates the next strike to 16: " + damages);

            stand(GameMode.SURVIVAL, 0, 6, 20, true);
            advance(65);
            place(3, 0, 20, true);
            advance(30);
            check.accept(damages.isEmpty() && clears == 1, "dropping to the floor dodges the strike, which still lands on the perch");
            advance(20);
            check.accept(heat() == 0 && escalation() == 0, "the arena floor drains the heat and forgets the escalation");

            stand(GameMode.SURVIVAL, 0, 6, 20, true);
            advance(80);
            place(4, 6, 20, true);
            advance(10);
            check.accept(damages.isEmpty(), "the last half second is locked, so a late sidestep along the ledge still dodges");

            stand(GameMode.SURVIVAL, 0, 0, 31, true);
            advance(90);
            check.accept(damages.equals(List.of(12.0)), "standing on the floor outside the drawn ring is hunted too");
            stand(GameMode.SURVIVAL, 0, 0, 31, true);
            advance(40);
            place(0, 0, 28.5, true);
            advance(40);
            check.accept(heat() == 40 && damages.isEmpty(), "stepping back to the ring edge keeps the heat");
            place(0, 0, 31, true);
            advance(20);
            check.accept(mark() != null, "hopping in and out of the ring adds up to a hunt");
            advance(5);
            place(0, 0, 28.5, true);
            advance(25);
            check.accept(damages.equals(List.of(12.0)), "the mark chases the hopper along the ring edge");
            stand(GameMode.SURVIVAL, 0, 0, 31, true);
            advance(40);
            place(0, 0, 25.5, true);
            advance(14);
            check.accept(heat() == 0, "committing four blocks inside the ring drains the heat");

            stand(GameMode.SURVIVAL, 0, 6, 20, true);
            advance(30);
            place(0, 4, 18, false);
            check.accept((Boolean) skyfall.invoke(hunt, player) && heat() == 0, "a Mace dive from the perch (Skyfall) ends the hunt");
            check.accept(!(Boolean) skyfall.invoke(hunt, player), "Skyfall needs perch time since the last floor touch");
            stand(GameMode.SURVIVAL, 0, 0, 20, true);
            advance(30);
            place(0, 4, 20, false);
            check.accept(!(Boolean) skyfall.invoke(hunt, player), "a Wind Charge jump from the floor is an ordinary smash");
            reset.invoke(hunt);
        } catch (ReflectiveOperationException e) { throw new RuntimeException(e); }
        finally {
            subject.setGameMode(GameMode.CREATIVE);
            subject.setVelocity(new Vector());
            subject.teleport(new Location(world, 256.5, 120, 236.5, 0, 0));
        }
    }

    /** The real Bone Temple asset: sweep, intruder repair and a real alcove hunted by the real Soul-Eater. */
    static void structure(VoidscapePlugin garden, World world, Player subject, BiConsumer<Boolean, String> check) {
        var service = garden.dimensionalSanctuaries();
        var site = new DimensionalSanctuaries.Site(DimensionalSanctuaries.Kind.BONE_TEMPLE, 2048, 80, 2048, 1, 7L, 1);
        int[] b = service.bounds(site);
        for (int cx = b[0] >> 4; cx <= b[1] >> 4; cx++)
            for (int cz = b[4] >> 4; cz <= b[5] >> 4; cz++) service.constructChunk(world, world.getChunkAt(cx, cz), site);
        var arena = service.arena(site);
        Location mid = new Location(world, arena.x() + .5, arena.y() + 1.0, arena.z() + .5);
        int feet = mid.getBlockY(), radius = arena.radius(), top = feet + arena.height() + 28;
        check.accept(world.getBlockAt(arena.x(), arena.y(), arena.z()).getType().isSolid(), "the Bone Temple asset builds with a solid arena floor");

        // Snapshot the whole sweep volume, plant strays in authored air, and sweep.
        int reach = radius + 2, span = 2 * reach + 1, height = top - (feet - 4) + 1;
        Material[] before = new Material[span * span * height];
        for (int dx = -reach; dx <= reach; dx++) for (int dz = -reach; dz <= reach; dz++) for (int y = feet - 4; y <= top; y++)
            before[((dx + reach) * span + dz + reach) * height + y - feet + 4] = world.getBlockAt(arena.x() + dx, y, arena.z() + dz).getType();
        int[][] strays = {{5, 0, 5}, {5, 1, 5}, {5, 2, 5}, {-6, 10, 0}, {0, 20, -8}};
        Material[] kinds = {Material.BONE_BLOCK, Material.BONE_BLOCK, Material.BONE_BLOCK, Material.CRIMSON_STEM, Material.LAVA};
        boolean planted = true;
        for (int i = 0; i < strays.length; i++) {
            var block = world.getBlockAt(arena.x() + strays[i][0], feet + strays[i][1], arena.z() + strays[i][2]);
            planted &= block.getType().isAir();
            block.setType(kinds[i], false);
        }
        check.accept(planted, "the stray positions are authored air");
        int cleared = 0;
        for (int cx = (arena.x() - reach) >> 4; cx <= (arena.x() + reach) >> 4; cx++)
            for (int cz = (arena.z() - reach) >> 4; cz <= (arena.z() + reach) >> 4; cz++)
                cleared += service.clearArena(world, world.getChunkAt(cx, cz), site, radius + 2.0, feet - 4, top);
        int differ = 0;
        for (int dx = -reach; dx <= reach; dx++) for (int dz = -reach; dz <= reach; dz++) for (int y = feet - 4; y <= top; y++)
            if (before[((dx + reach) * span + dz + reach) * height + y - feet + 4] != world.getBlockAt(arena.x() + dx, y, arena.z() + dz).getType()) differ++;
        check.accept(cleared == strays.length && differ == 0, "the arena sweep removes the fossil, stem and lava and leaves every authored block: cleared=" + cleared + " differ=" + differ);

        // A real alcove ledge out of the hound's reach.
        int[] ledge = null;
        for (int dx = -reach; dx <= reach && ledge == null; dx++) for (int dz = -reach; dz <= reach && ledge == null; dz++) {
            double flat = Math.hypot(dx, dz);
            if (flat < radius - 1.0 || flat > radius + 1.0) continue;
            for (int y = feet + 4; y <= feet + 30 && ledge == null; y++) {
                int x = arena.x() + dx, z = arena.z() + dz;
                if (world.getBlockAt(x, y - 1, z).getType().isSolid() && world.getBlockAt(x, y, z).isPassable() && world.getBlockAt(x, y + 1, z).isPassable())
                    ledge = new int[]{x, y, z};
            }
        }
        check.accept(ledge != null, "the Bone Temple has an alcove ledge beside the ring edge");
        var floorBlock = world.getBlockAt(ledge[0], ledge[1] - 1, ledge[2]);
        BlockData authored = floorBlock.getBlockData().clone();
        floorBlock.setType(authored.getMaterial() == Material.BONE_BLOCK ? Material.CRIMSON_STEM : Material.BONE_BLOCK, false);
        service.repair(world, floorBlock.getChunk(), site);
        check.accept(floorBlock.getBlockData().equals(authored), "repair now restores structure overwritten by fossils or fungus stems");

        RealmBossController boss = null;
        var maxHealth = subject.getAttribute(Attribute.MAX_HEALTH);
        var difficulty = world.getDifficulty();
        try {
            world.setDifficulty(org.bukkit.Difficulty.NORMAL);
            boss = new RealmBossController(garden, site, arena, mid, p -> p == subject, d -> {});
            boss.spawnWaiting(1);
            maxHealth.setBaseValue(200);
            subject.setGameMode(GameMode.SURVIVAL);
            subject.setInvulnerable(false);
            subject.setHealth(200);
            subject.teleport(new Location(world, ledge[0] + .5, ledge[1], ledge[2] + .5));
            ((CraftPlayer) subject).getHandle().setOnGround(true);
            boss.awaken(List.of(subject));
            var action = RealmBossController.class.getDeclaredField("action");
            var cooldown = RealmBossController.class.getDeclaredField("cooldownTicks");
            action.setAccessible(true); cooldown.setAccessible(true);
            @SuppressWarnings({"rawtypes", "unchecked"})
            Object none = Enum.valueOf((Class<? extends Enum>) action.getType(), "NONE");
            action.set(boss, none); cooldown.setInt(boss, 100000);
            long t = 100;
            for (int n = 0; n < 89; n++) boss.tick(t++);
            check.accept(subject.getHealth() == 200, "the real hound gives the camper three seconds and a warning first");
            boss.tick(t++);
            Vector toHound = boss.location().toVector().subtract(subject.getLocation().toVector()).setY(0).normalize();
            Vector pulled = subject.getVelocity().clone().setY(0);
            check.accept(subject.getHealth() < 200 && pulled.lengthSquared() > 0.5 && pulled.normalize().dot(toHound) > 0.95,
                "the real Soul-Eater hits the alcove camper through cover and drags them at the hound: hp=" + subject.getHealth());
            check.accept(floorBlock.getBlockData().equals(authored), "the authored alcove survives the strike");

            int px = arena.x() + 8, pz = arena.z();
            for (int y = feet; y <= feet + 3; y++) world.getBlockAt(px, y, pz).setType(Material.BONE_BLOCK, false);
            double hp = subject.getHealth();
            subject.setVelocity(new Vector());
            subject.teleport(new Location(world, px + .5, feet + 4, pz + .5));
            ((CraftPlayer) subject).getHandle().setOnGround(true);
            for (int n = 0; n < 81; n++) boss.tick(t++);
            check.accept(subject.getHealth() < hp && world.getBlockAt(px, feet + 3, pz).getType().isAir()
                    && world.getBlockAt(px, feet + 2, pz).getType() == Material.BONE_BLOCK,
                "the next strike blows the stray block out from under the camper");
        } catch (ReflectiveOperationException e) { throw new RuntimeException(e); }
        finally {
            if (boss != null) boss.close();
            world.setDifficulty(difficulty);
            subject.setInvulnerable(true);
            maxHealth.setBaseValue(20);
            subject.setHealth(20);
            subject.setFireTicks(0);
            subject.getActivePotionEffects().forEach(effect -> subject.removePotionEffect(effect.getType()));
            subject.setGameMode(GameMode.CREATIVE);
            subject.setVelocity(new Vector());
            subject.teleport(new Location(world, 256.5, 120, 236.5, 0, 0));
        }
    }

    /** Hit and run: strike from inside, hide in a corridor past the arena, come back. The corridor is not safe. */
    static void corridor(VoidscapePlugin garden, World world, Player subject, BiConsumer<Boolean, String> check) {
        Location mid = new Location(world, 256.5, 120, 256.5);
        var site = new DimensionalSanctuaries.Site(DimensionalSanctuaries.Kind.CROWN_FORTRESS, 256, 120, 256, 0, 0, 1);
        var arena = new DimensionalSanctuaries.Arena(256, 120, 256, 30, 20);
        RealmBossController boss = null;
        var maxHealth = subject.getAttribute(Attribute.MAX_HEALTH);
        var difficulty = world.getDifficulty();
        try {
            world.setDifficulty(org.bukkit.Difficulty.NORMAL);
            boss = new RealmBossController(garden, site, arena, mid,
                p -> p == subject && Math.hypot(p.getX() - mid.getX(), p.getZ() - mid.getZ()) <= 31.5, d -> {});
            boss.spawnWaiting(1);
            maxHealth.setBaseValue(200);
            subject.setGameMode(GameMode.SURVIVAL);
            subject.setInvulnerable(false);
            subject.setHealth(200);
            Location corridor = mid.clone().add(0, 0, 40);
            subject.teleport(corridor);
            ((CraftPlayer) subject).getHandle().setOnGround(true);
            boss.awaken(List.of(subject));
            var action = RealmBossController.class.getDeclaredField("action");
            var cooldown = RealmBossController.class.getDeclaredField("cooldownTicks");
            var lastAttack = RealmBossController.class.getDeclaredField("lastAttack");
            action.setAccessible(true); cooldown.setAccessible(true); lastAttack.setAccessible(true);
            @SuppressWarnings({"rawtypes", "unchecked"})
            Object none = Enum.valueOf((Class<? extends Enum>) action.getType(), "NONE");
            action.set(boss, none); cooldown.setInt(boss, 100000);
            long t = 100;
            for (int n = 0; n < 120; n++) boss.tick(t++);
            check.accept(subject.getHealth() == 200, "a bystander waiting outside the arena who never struck is left alone");

            subject.teleport(mid.clone().add(0, 0, 20));
            var source = DamageSource.builder(DamageType.PLAYER_ATTACK).withCausingEntity(subject).withDirectEntity(subject).build();
            double hp = boss.health();
            boss.handleIncomingDamage(new EntityDamageByEntityEvent(subject, boss.carrier(), EntityDamageEvent.DamageCause.ENTITY_ATTACK, source, 6.0), subject);
            check.accept(boss.health() < hp, "the strike from inside the ring lands");
            subject.teleport(corridor);
            ((CraftPlayer) subject).getHandle().setOnGround(true);
            for (int n = 0; n < 89; n++) boss.tick(t++);
            check.accept(subject.getHealth() == 200, "the hider gets the same three seconds and warning");
            boss.tick(t++);
            Vector inward = mid.toVector().subtract(subject.getLocation().toVector()).setY(0).normalize();
            check.accept(subject.getHealth() < 200 && subject.getVelocity().clone().setY(0).normalize().dot(inward) > 0.95,
                "a hit-and-run attacker hiding in the corridor past the arena is hunted and thrown back in: hp=" + subject.getHealth());

            subject.setHealth(200);
            ((Map<?, ?>) lastAttack.get(boss)).clear();
            for (int n = 0; n < 120; n++) boss.tick(t++);
            check.accept(subject.getHealth() == 200, "once the last hit is more than 15 seconds old the corridor is just outside again");
        } catch (ReflectiveOperationException e) { throw new RuntimeException(e); }
        finally {
            if (boss != null) boss.close();
            world.setDifficulty(difficulty);
            subject.setInvulnerable(true);
            maxHealth.setBaseValue(20);
            subject.setHealth(20);
            subject.setFireTicks(0);
            subject.getActivePotionEffects().forEach(effect -> subject.removePotionEffect(effect.getType()));
            ((CraftPlayer) subject).getHandle().setOnGround(false);
            subject.setGameMode(GameMode.SPECTATOR);
            subject.setVelocity(new Vector());
            subject.teleport(new Location(world, 256.5, 160, 1200.5, 0, 0));
        }
    }

    private static void stand(GameMode mode, double x, double y, double z, boolean ground) throws ReflectiveOperationException {
        reset.invoke(hunt);
        damages.clear();
        clears = 0;
        player.setGameMode(mode);
        player.setVelocity(new Vector());
        place(x, y, z, ground);
    }

    private static void place(double x, double y, double z, boolean ground) {
        player.teleport(center.clone().add(x, y, z));
        ((CraftPlayer) player).getHandle().setOnGround(ground);
    }

    private static void advance(int count) throws ReflectiveOperationException {
        for (int i = 0; i < count; i++) tick.invoke(hunt, List.of(player), center);
    }

    private static Object perch() throws ReflectiveOperationException {
        return ((Map<?, ?>) perches.get(hunt)).get(player.getUniqueId());
    }

    private static Object field(String name) throws ReflectiveOperationException {
        Object s = perch();
        if (s == null) return null;
        Field f = s.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(s);
    }

    private static Object mark() throws ReflectiveOperationException { return field("mark"); }
    private static int heat() throws ReflectiveOperationException { Object v = field("heat"); return v == null ? 0 : (Integer) v; }
    private static int warning() throws ReflectiveOperationException { Object v = field("warning"); return v == null ? 0 : (Integer) v; }
    private static int escalation() throws ReflectiveOperationException { Object v = field("escalation"); return v == null ? 0 : (Integer) v; }
}
