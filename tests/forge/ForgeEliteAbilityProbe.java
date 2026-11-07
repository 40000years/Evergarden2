import com.example.forge.EliteAbilities;
import com.example.forge.EliteMove;
import com.example.forge.EliteService;
import com.example.forge.ForgePlugin;
import com.example.forge.Rarity;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Blaze;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Ghast;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.Spider;
import org.bukkit.entity.Warden;
import org.bukkit.entity.Zombie;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/** Drives the real EliteAbilities and EliteService on isolated Paper with real server players. */
public final class ForgeEliteAbilityProbe extends JavaPlugin {
    private interface Step { void run() throws Exception; }

    private final List<String> passed = new ArrayList<>();
    private final List<Entity> spawned = new ArrayList<>();
    private final Deque<Step> steps = new ArrayDeque<>();
    private ForgePlugin forge;
    private EliteAbilities abilities;
    private EliteService elites;
    private Method tickMethod, auraMethod;
    private World world;
    private Location home;
    private Player player;
    private boolean finished;
    // Shared between a setup step and its check step.
    private Zombie plainRider, eliteRider;
    private Entity plainBoat, eliteBoat;

    private static final class Silent extends ServerGamePacketListenerImpl {
        Silent(MinecraftServer server, Connection connection, ServerPlayer actor, GameProfile profile) {
            super(server, connection, actor, CommonListenerCookie.createInitial(profile, false));
        }
        @Override public boolean hasClientLoaded() { return true; }
        @Override public void send(Packet<?> packet) {}
    }

    @Override public void onEnable() {
        steps.add(this::begin);
        steps.add(this::rolls);
        steps.add(this::boatRules);
        steps.add(this::vanillaBoatSetup);
        steps.add(this::vanillaBoatCheck);
        steps.add(() -> arena(10, 0));
        steps.add(this::hookPull);
        steps.add(() -> arena(8, 0));
        steps.add(this::hookDismountAndLock);
        steps.add(() -> arena(8, 0));
        steps.add(this::hookDodge);
        steps.add(() -> arena(2, 0));
        steps.add(this::shockwavePush);
        steps.add(() -> arena(2, 0));
        steps.add(this::shockwaveLava);
        steps.add(() -> arena(2, 0));
        steps.add(this::shockwaveVoid);
        steps.add(() -> arena(6, 0));
        steps.add(this::vortexDrag);
        steps.add(() -> arena(8, 0));
        steps.add(this::automaticHookOnRider);
        steps.add(() -> arena(8, 14));
        steps.add(this::noHookFromHighPillar);
        steps.add(() -> arena(8, 6));
        steps.add(this::hookFromLowPillar);
        steps.add(this::exemptAndCreative);
        Bukkit.getScheduler().runTaskLater(this, this::next, 40);
    }

    private void next() {
        if (finished) return;
        Step step = steps.poll();
        if (step == null) {
            finish("PASS " + passed.size() + " elite ability checks\n" + String.join("\n", passed) + "\n");
            return;
        }
        try {
            step.run();
        } catch (Throwable error) {
            StringWriter detail = new StringWriter();
            error.printStackTrace(new PrintWriter(detail));
            finish("FAIL " + detail + "\nPassed before failure:\n" + String.join("\n", passed));
            return;
        }
        // Real ticks between steps let new mobs land (moves need an anchored elite) and boats tick.
        Bukkit.getScheduler().runTaskLater(this, this::next, 8);
    }

    // ---- setup ---------------------------------------------------------------------------------

    private void begin() throws Exception {
        forge = (ForgePlugin) Bukkit.getPluginManager().getPlugin("EvergardenForge");
        check(forge != null && forge.isEnabled(), "Forge enabled");
        abilities = forge.eliteAbilities();
        elites = forge.elites();
        check(abilities != null, "EliteAbilities is created and exposed");
        ((BukkitTask) get(abilities, "ticker")).cancel(); // The probe drives the ability clock itself.
        tickMethod = EliteAbilities.class.getDeclaredMethod("tick");
        tickMethod.setAccessible(true);
        auraMethod = EliteService.class.getDeclaredMethod("aura");
        auraMethod.setAccessible(true);
        var settings = forge.settings();
        check(settings.eliteNoVehicles && settings.eliteAbilitiesEnabled, "default config enables no-vehicles and elite moves");
        check(settings.eliteMoves.get(EliteMove.HOOK).cooldown() == 200 && settings.eliteMoves.get(EliteMove.HOOK).windup() == 15
            && settings.eliteMoves.get(EliteMove.VORTEX).duration() == 30 && settings.eliteRemountLock == 60,
            "move timings are read from seconds into ticks");
        check(settings.eliteMoveGrades.get(Rarity.UNCOMMON).moves() == 1 && settings.eliteMoveGrades.get(Rarity.MYTHIC).moves() == 3,
            "Tempered elites roll one move and Divine elites roll all three");
        check(settings.eliteAbilityExempt.contains(EntityType.WARDEN), "the Warden is exempt from elite moves by default");
        world = Bukkit.getWorlds().getFirst();
        world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        world.setTime(18000);
        Location spawn = world.getSpawnLocation();
        int x = spawn.getBlockX() + 24, z = spawn.getBlockZ() + 24;
        home = new Location(world, x + .5, world.getHighestBlockYAt(x, z) + 1, z + .5);
        for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) world.getChunkAt((x >> 4) + dx, (z >> 4) + dz).addPluginChunkTicket(forge);
        player = actor("EliteTarget", home);
        check(player.isOnline() && player.getGameMode() == GameMode.SURVIVAL, "the target is a real online survival server player");
    }

    private Player actor(String name, Location at) {
        MinecraftServer server = MinecraftServer.getServer();
        var level = ((CraftWorld) world).getHandle();
        GameProfile profile = new GameProfile(UUID.randomUUID(), name);
        ServerPlayer actor = new ServerPlayer(server, level, profile, ClientInformation.createDefault());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        connection.channel = new EmbeddedChannel();
        connection.address = new InetSocketAddress("127.0.0.1", 1);
        actor.connection = new Silent(server, connection, actor, profile);
        actor.setPos(at.getX(), at.getY(), at.getZ());
        actor.setOnGround(true);
        server.getPlayerList().getPlayers().add(actor);
        server.getPlayerList().getPlayersByUUID().put(profile.id(), actor);
        level.addNewPlayer(actor);
        Player bukkit = actor.getBukkitEntity();
        bukkit.setGravity(false);
        bukkit.setGameMode(GameMode.SURVIVAL);
        // Not invulnerable: vanilla never treats an invulnerable player as an enemy, so mobs would drop it as a target.
        // The probe's elites cannot walk, so nothing reaches the player.
        bukkit.setInvulnerable(false);
        return bukkit;
    }

    // ---- rolls and boats -------------------------------------------------------------------------

    private void rolls() {
        Zombie divine = zombie(home.clone().add(0, 0, 6), Rarity.MYTHIC);
        List<EliteMove> moves = abilities.moves(divine);
        check(moves.equals(List.of(EliteMove.HOOK, EliteMove.SHOCKWAVE, EliteMove.VORTEX)), "a Divine zombie rolls hook, shockwave and vortex");
        String stored = divine.getPersistentDataContainer().get(new NamespacedKey(forge, "elite_moves"), PersistentDataType.STRING);
        check("HOOK,SHOCKWAVE,VORTEX".equals(stored), "the roll is kept on the mob's persistent data");
        check(abilities.moves(divine).equals(moves), "a kept roll is not rerolled");

        Zombie tempered = zombie(home.clone().add(3, 0, 6), Rarity.UNCOMMON);
        List<EliteMove> one = abilities.moves(tempered);
        check(one.size() == 1 && EliteMove.pool(EntityType.ZOMBIE).contains(one.getFirst()), "a Tempered zombie rolls exactly one move from its pool");

        Blaze blaze = world.spawn(home.clone().add(6, 1, 6), Blaze.class, mob -> mob.setRemoveWhenFarAway(false));
        spawned.add(blaze);
        check(elites.makeElite(blaze, Rarity.MYTHIC), "a Divine blaze becomes an elite");
        check(abilities.moves(blaze).equals(List.of(EliteMove.SHOCKWAVE)), "a Divine blaze (flying) only rolls shockwave");
        check(EliteMove.pool(EntityType.GHAST).equals(List.of(EliteMove.SHOCKWAVE)) && EliteMove.pool(EntityType.SHULKER).equals(List.of(EliteMove.SHOCKWAVE))
            && EliteMove.pool(EntityType.PHANTOM).equals(List.of(EliteMove.SHOCKWAVE)), "ghasts, phantoms and shulkers never pull a player toward them");

        Zombie oldElite = zombie(home.clone().add(9, 0, 6), Rarity.EPIC);
        oldElite.getPersistentDataContainer().remove(new NamespacedKey(forge, "elite_moves"));
        check(abilities.moves(oldElite).size() == 2, "an elite from before this version rolls its two Exalted moves on first use");
        clear();
    }

    private void boatRules() throws Exception {
        Zombie elite = zombie(home.clone().add(0, 0, 10), Rarity.MYTHIC);
        Entity boat = spawn(home.clone().add(2, 0, 10), EntityType.OAK_BOAT);
        check(!boat.addPassenger(elite) && !elite.isInsideVehicle(), "an elite cannot be seated in a boat");
        Entity cart = spawn(home.clone().add(4, 0, 10), EntityType.MINECART);
        check(!cart.addPassenger(elite) && !elite.isInsideVehicle(), "an elite cannot be seated in a minecart");

        Zombie plain = zombie(home.clone().add(6, 0, 10), null);
        Entity second = spawn(home.clone().add(6, 0, 10), EntityType.OAK_BOAT);
        check(second.addPassenger(plain) && plain.isInsideVehicle(), "an ordinary zombie can still sit in a boat (control)");
        check(elites.makeElite(plain, Rarity.RARE), "a zombie in a boat becomes an elite");
        auraMethod.invoke(elites);
        check(!plain.isInsideVehicle(), "an elite already sitting in a boat is thrown out on the next aura pass");

        Spider spider = world.spawn(home.clone().add(9, 0, 10), Spider.class, mob -> mob.setRemoveWhenFarAway(false));
        Skeleton skeleton = world.spawn(home.clone().add(9, 1, 10), Skeleton.class, mob -> mob.setRemoveWhenFarAway(false));
        spawned.add(spider);
        spawned.add(skeleton);
        check(elites.makeElite(skeleton, Rarity.RARE), "a skeleton becomes an elite");
        check(spider.addPassenger(skeleton) && skeleton.isInsideVehicle(), "an elite may still ride a living mount (spider jockey)");
        clear();
    }

    private void vanillaBoatSetup() {
        // Vanilla seats a small mob whose hitbox touches a boat on the boat's own tick.
        plainRider = zombie(home.clone().add(0, 0, 14), null);
        eliteRider = zombie(home.clone().add(5, 0, 14), Rarity.MYTHIC);
        plainBoat = spawn(plainRider.getLocation(), EntityType.OAK_BOAT);
        eliteBoat = spawn(eliteRider.getLocation(), EntityType.OAK_BOAT);
    }

    private void vanillaBoatCheck() {
        check(plainRider.getVehicle() == plainBoat, "vanilla seats an ordinary zombie that touches a boat (control)");
        check(!eliteRider.isInsideVehicle() && eliteBoat.getPassengers().isEmpty(), "vanilla boat pickup never seats an elite");
        clear();
    }

    // ---- hook ----------------------------------------------------------------------------------

    private Zombie arenaElite;

    /** A fresh Divine zombie standing still at home and the target at the given distance (+x) and height. */
    private void arena(double distance, double height) throws Exception {
        clear();
        resetGrace();
        place(home.clone().add(distance, height, 0));
        if (height > 0) world.getBlockAt(home.clone().add(distance, height - 1, 0)).setType(Material.STONE);
        arenaElite = zombie(home.clone(), Rarity.MYTHIC);
    }

    private void hookPull() throws Exception {
        check(arenaElite.isOnGround(), "the arena elite stands on the ground");
        check(abilities.force(arenaElite, player, EliteMove.HOOK), "a hook starts on a survival player in sight");
        check(abilities.casting(arenaElite.getUniqueId()), "the hook is in its wind-up");
        ticks(14);
        check(player.getVelocity().lengthSquared() < 1e-6, "nothing moves the player during the 0.75 s telegraph");
        ticks(1);
        Vector velocity = player.getVelocity();
        Vector toward = arenaElite.getLocation().toVector().subtract(player.getLocation().toVector()).setY(0).normalize();
        Vector flat = velocity.clone().setY(0);
        check(flat.length() > 0.7 && flat.length() < 1.3, "a 10-block hook launches at a landing speed (" + fmt(flat.length()) + ")");
        check(flat.clone().normalize().dot(toward) > 0.99, "the hook pulls straight toward the elite");
        check(velocity.getY() > 0.42 && velocity.getY() < 0.7, "the hook lifts the player into a short arc (" + fmt(velocity.getY()) + ")");
        check(!abilities.casting(arenaElite.getUniqueId()), "the hook finishes when it lands");
    }

    private void hookDismountAndLock() throws Exception {
        Entity boat = spawn(player.getLocation(), EntityType.OAK_BOAT);
        check(boat.addPassenger(player) && player.isInsideVehicle(), "the target sits in a boat");
        check(abilities.force(arenaElite, player, EliteMove.HOOK), "a hook starts on a player in a boat");
        ticks(15);
        check(!player.isInsideVehicle(), "the hook throws the player out of the boat");
        check(!boat.addPassenger(player) && !player.isInsideVehicle(), "the player cannot climb straight back into the boat");
        ticks(2);
        Vector toward = arenaElite.getLocation().toVector().subtract(player.getLocation().toVector()).setY(0).normalize();
        check(player.getVelocity().clone().setY(0).normalize().dot(toward) > 0.95 && player.getVelocity().getY() > 0.4,
            "the dismounted player is yanked toward the elite two ticks later");
        ticks(60);
        check(boat.addPassenger(player) && player.isInsideVehicle(), "the remount lock ends after three seconds");
        player.leaveVehicle();
    }

    private void hookDodge() throws Exception {
        check(abilities.force(arenaElite, player, EliteMove.HOOK), "a hook starts");
        ticks(5);
        for (int y = 0; y < 4; y++) for (int z = -2; z <= 2; z++) world.getBlockAt(home.clone().add(4, y, z)).setType(Material.STONE);
        ticks(10);
        check(player.getVelocity().lengthSquared() < 1e-6, "a player who breaks line of sight during the telegraph dodges the hook");
        for (int y = 0; y < 4; y++) for (int z = -2; z <= 2; z++) world.getBlockAt(home.clone().add(4, y, z)).setType(Material.AIR);
    }

    // ---- shockwave -----------------------------------------------------------------------------

    private void shockwavePush() throws Exception {
        check(abilities.force(arenaElite, player, EliteMove.SHOCKWAVE), "a shockwave starts on a player beside the elite");
        ticks(12);
        Vector velocity = player.getVelocity();
        Vector flat = velocity.clone().setY(0);
        check(flat.clone().normalize().dot(new Vector(1, 0, 0)) > 0.99, "the shockwave throws the player straight away from the elite");
        check(Math.abs(flat.length() - 1.12) < 0.02 && Math.abs(velocity.getY() - 0.42) < 1e-6,
            "a 2-block shockwave on safe ground is full strength (" + fmt(flat.length()) + ")");
    }

    private void shockwaveLava() throws Exception {
        int ground = home.getBlockY() - 1;
        // The player stands 2 blocks out. Full strength lands ~8.4 blocks further (block +10), 60% ~5.1 (block +7),
        // 30% ~2.5 (block +5). Lava from block +9: only the full push would land in it.
        strip(9, 16, ground, Material.LAVA);
        check(abilities.force(arenaElite, player, EliteMove.SHOCKWAVE), "a shockwave starts beside a lava pool");
        ticks(12);
        double reduced = player.getVelocity().clone().setY(0).length();
        check(Math.abs(reduced - 0.672) < 0.02, "the shockwave weakens to 60% so the player lands short of the lava (" + fmt(reduced) + ")");
        // Lava right behind the player: no strength lands safely.
        strip(3, 16, ground, Material.LAVA);
        resetGrace();
        player.setVelocity(new Vector());
        check(abilities.force(arenaElite, player, EliteMove.SHOCKWAVE), "a shockwave starts beside lava right behind the player");
        ticks(12);
        Vector jolt = player.getVelocity();
        check(jolt.clone().setY(0).length() < 1e-6 && Math.abs(jolt.getY() - 0.25) < 1e-6,
            "with lava right behind, the player keeps their footing and is only jolted");
        strip(3, 16, ground, Material.GRASS_BLOCK);
    }

    private void shockwaveVoid() throws Exception {
        // Remove every block down to the world floor behind the player.
        for (int dx = 3; dx <= 16; dx++) for (int dz = -2; dz <= 2; dz++)
            for (int y = world.getMinHeight(); y < home.getBlockY(); y++) world.getBlockAt(home.getBlockX() + dx, y, home.getBlockZ() + dz).setType(Material.AIR);
        check(abilities.force(arenaElite, player, EliteMove.SHOCKWAVE), "a shockwave starts at the edge of the void");
        ticks(12);
        check(player.getVelocity().clone().setY(0).length() < 1e-6, "a shockwave never throws a player into the void");
        for (int dx = 3; dx <= 16; dx++) for (int dz = -2; dz <= 2; dz++) {
            int x = home.getBlockX() + dx, z = home.getBlockZ() + dz;
            world.getBlockAt(x, world.getMinHeight(), z).setType(Material.BEDROCK);
            for (int y = world.getMinHeight() + 1; y < home.getBlockY() - 1; y++) world.getBlockAt(x, y, z).setType(Material.DIRT);
            world.getBlockAt(x, home.getBlockY() - 1, z).setType(Material.GRASS_BLOCK);
        }
    }

    // ---- vortex --------------------------------------------------------------------------------

    private void vortexDrag() throws Exception {
        Player far = actor("EliteFar", home.clone().add(-12, 0, 0));
        Player core = actor("EliteCore", home.clone().add(0, 0, 1.5));
        check(abilities.force(arenaElite, player, EliteMove.VORTEX), "a vortex starts on a player 6 blocks away");
        ticks(14);
        check(player.getVelocity().lengthSquared() < 1e-6, "nothing is dragged during the telegraph");
        ticks(1);
        Vector velocity = player.getVelocity();
        Vector flat = velocity.clone().setY(0);
        check(flat.clone().normalize().dot(new Vector(-1, 0, 0)) > 0.99 && Math.abs(flat.length() - 0.22) < 0.01,
            "the vortex drags the player toward the elite (" + fmt(flat.length()) + ")" + diag(arenaElite));
        check(Math.abs(velocity.getY() + 0.0784) < 1e-6, "the drag keeps the resting fall speed (never floats or cushions a fall)");
        check(far.getVelocity().lengthSquared() < 1e-6, "a player outside the 9-block radius is not dragged");
        check(core.getVelocity().lengthSquared() < 1e-6, "a player already at the elite's feet is not pushed around");
        ticks(30);
        check(!abilities.casting(arenaElite.getUniqueId()), "the vortex ends after 1.5 seconds");
        far.setGameMode(GameMode.SPECTATOR);
        core.setGameMode(GameMode.SPECTATOR);
    }

    // ---- automatic use -------------------------------------------------------------------------

    private void automaticHookOnRider() throws Exception {
        Zombie rare = rareHooker();
        Entity boat = spawn(player.getLocation(), EntityType.OAK_BOAT);
        check(boat.addPassenger(player), "the target sits in a boat 8 blocks from an Arcane elite");
        rare.setTarget(player);
        check(rare.getTarget() == player, "the elite targets the player");
        check(!until(() -> abilities.casting(rare.getUniqueId()), 35), "the elite waits before its first move after targeting someone");
        check(until(() -> abilities.casting(rare.getUniqueId()), 15), "an Arcane elite hooks a target sitting in a boat on its own" + diag(rare));
        check(until(() -> !player.isInsideVehicle(), 16), "the automatic hook throws the target out of the boat");
        ticks(62);
        check(boat.addPassenger(player), "the target climbs back into the boat once the lock ends");
        check(!until(() -> abilities.casting(rare.getUniqueId()), 160), "the hook stays on cooldown (10 s x 1.25 for Arcane)");
        check(until(() -> abilities.casting(rare.getUniqueId()), 40), "the hook is used again when its cooldown is over");
        player.leaveVehicle();
    }

    private void noHookFromHighPillar() throws Exception {
        Zombie rare = rareHooker();
        rare.setTarget(player);
        check(!until(() -> abilities.casting(rare.getUniqueId()), 80) && rare.getTarget() == player,
            "no hook drags a player down from 14 blocks up (limit 12), though the elite targets them" + diag(rare));
    }

    private void hookFromLowPillar() throws Exception {
        Zombie rare = rareHooker();
        rare.setTarget(player);
        check(until(() -> abilities.casting(rare.getUniqueId()), 80), "a player pillared 6 blocks up gets hooked down" + diag(rare));
    }

    private Zombie rareHooker() {
        arenaElite.remove();
        Zombie rare = zombie(home.clone(), Rarity.RARE);
        rare.getPersistentDataContainer().set(new NamespacedKey(forge, "elite_moves"), PersistentDataType.STRING, "HOOK");
        // Spawned on the ground by the step before; land it now so the decision sees an anchored elite.
        ((org.bukkit.craftbukkit.entity.CraftEntity) rare).getHandle().setOnGround(true);
        return rare;
    }

    private void exemptAndCreative() throws Exception {
        clear();
        place(home.clone().add(8, 0, 0));
        // Wardens choose targets through their brain (setTarget does not stick), so the exempt rule is checked
        // with zombies listed as exempt; the default list containing WARDEN is checked at startup.
        forge.getConfig().set("elites.abilities.exempt-types", List.of("ZOMBIE"));
        forge.saveConfig(); // reload() reads the file
        forge.reload();
        Zombie exempt = zombie(home.clone(), Rarity.RARE);
        exempt.getPersistentDataContainer().set(new NamespacedKey(forge, "elite_moves"), PersistentDataType.STRING, "HOOK");
        ((org.bukkit.craftbukkit.entity.CraftEntity) exempt).getHandle().setOnGround(true);
        exempt.setTarget(player);
        check(exempt.getTarget() == player, "the exempt elite targets the player");
        check(!until(() -> abilities.casting(exempt.getUniqueId()), 80), "a mob type listed in exempt-types never uses moves");
        forge.getConfig().set("elites.abilities.exempt-types", List.of("WARDEN"));
        forge.saveConfig();
        forge.reload();
        check(forge.settings().eliteAbilityExempt.equals(java.util.Set.of(EntityType.WARDEN)), "/forge reload applies exempt-types");
        // Its first decision starts the usual 2 s first-move delay.
        check(until(() -> abilities.casting(exempt.getUniqueId()), 50), "the same zombie uses its move once it is no longer exempt");
        exempt.remove();

        Zombie rare = zombie(home.clone().add(0, 0, 4), Rarity.RARE);
        rare.getPersistentDataContainer().set(new NamespacedKey(forge, "elite_moves"), PersistentDataType.STRING, "HOOK");
        ((org.bukkit.craftbukkit.entity.CraftEntity) rare).getHandle().setOnGround(true);
        // Vanilla never aims a mob at a creative player; the real case is a target who switches to creative.
        rare.setTarget(player);
        check(rare.getTarget() == player, "the elite targets the player");
        player.setGameMode(GameMode.CREATIVE);
        check(!until(() -> abilities.casting(rare.getUniqueId()), 80), "a creative player is never targeted by elite moves");
        check(!abilities.force(rare, player, EliteMove.HOOK), "no move can be forced on a creative player");
        player.setGameMode(GameMode.SURVIVAL);
        clear();
    }

    // ---- helpers -------------------------------------------------------------------------------

    private Zombie zombie(Location at, Rarity rarity) {
        Zombie zombie = world.spawn(at, Zombie.class, mob -> {
            mob.setAdult();
            mob.setRemoveWhenFarAway(false);
            mob.setShouldBurnInDay(false);
            mob.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(0);
        });
        spawned.add(zombie);
        if (rarity != null) check(elites.makeElite(zombie, rarity), "a zombie becomes a " + rarity.label + " elite");
        return zombie;
    }

    private Entity spawn(Location at, EntityType type) {
        Entity entity = world.spawnEntity(at, type);
        spawned.add(entity);
        return entity;
    }

    private void place(Location at) {
        if (player.isInsideVehicle()) player.leaveVehicle();
        ((CraftPlayer) player).getHandle().setPos(at.getX(), at.getY(), at.getZ());
        player.setVelocity(new Vector());
    }

    private void strip(int from, int to, int y, Material material) {
        for (int dx = from; dx <= to; dx++) for (int dz = -2; dz <= 2; dz++) world.getBlockAt(home.getBlockX() + dx, y, home.getBlockZ() + dz).setType(material);
    }

    private void clear() {
        if (player != null && player.isInsideVehicle()) player.leaveVehicle();
        for (Entity entity : spawned) if (entity.isValid()) entity.remove();
        spawned.clear();
        if (player != null) player.setVelocity(new Vector());
    }

    /** State behind a move decision, appended to a failing check. */
    private String diag(Mob mob) {
        try {
            Object brain = ((java.util.Map<?, ?>) get(abilities, "brains")).get(mob.getUniqueId());
            String brainText = brain == null ? "none" : "target=" + get(brain, "target") + " aggroSince=" + get(brain, "aggroSince")
                + " nextMove=" + get(brain, "nextMove") + " ready=" + get(brain, "ready");
            return " [clock=" + get(abilities, "clock") + " valid=" + mob.isValid() + " ai=" + mob.hasAI()
                + " target=" + (mob.getTarget() == null ? "null" : mob.getTarget().getName()) + " onGround=" + mob.isOnGround()
                + " inWater=" + mob.isInWater() + " sight=" + mob.hasLineOfSight(player)
                + " distance=" + fmt(mob.getLocation().distance(player.getLocation()))
                + " rise=" + fmt(mob.getLocation().getY() - player.getLocation().getY()) + " mounted=" + player.isInsideVehicle()
                + " tracked=" + elites.trackedIds().contains(mob.getUniqueId()) + " moves=" + abilities.moves(mob)
                + " elite=" + mob.getLocation().toVector() + " player=" + player.getLocation().toVector() + " brain{" + brainText + "}]";
        } catch (Exception error) {
            return " [diag failed: " + error + "]";
        }
    }

    /** Every test starts without the previous test's hit immunity (the probe clock skips real ticks). */
    @SuppressWarnings("unchecked")
    private void resetGrace() throws Exception {
        ((java.util.Map<UUID, Long>) get(abilities, "grace")).clear();
    }

    private void ticks(int count) throws Exception {
        for (int i = 0; i < count; i++) tickMethod.invoke(abilities);
    }

    /** Ticks the ability clock until the condition holds; false if it never does within the limit. */
    private boolean until(BooleanSupplier condition, int limit) throws Exception {
        for (int i = 0; i < limit; i++) {
            tickMethod.invoke(abilities);
            if (condition.getAsBoolean()) return true;
        }
        return false;
    }

    private static Object get(Object object, String name) throws Exception {
        Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(object);
    }

    private static String fmt(double value) {
        return String.format(java.util.Locale.ROOT, "%.3f", value);
    }

    private void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        passed.add("PASS " + message);
    }

    private void finish(String result) {
        if (finished) return;
        finished = true;
        try {
            Files.writeString(Path.of("forge-elite-abilities-result.txt"), result);
        } catch (Exception error) {
            getLogger().severe("Cannot write result: " + error);
        }
        Bukkit.getScheduler().runTaskLater(this, Bukkit::shutdown, 2);
    }
}
