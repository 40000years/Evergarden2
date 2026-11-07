import com.example.forge.Charm;
import com.example.forge.CharmService;
import com.example.forge.ForgeApi;
import com.example.forge.ForgePlugin;
import com.example.forge.Rarity;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Husk;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 0.7.1 charm abilities on a real (connectionless) server player: the events a client would cause are
 * fired for real (hits, kills, sneak and flight toggles, swings, falls, death) and the results checked.
 * Compiled by run.py --with-core, because it needs the server classes.
 */
final class ForgeCharmChecks {
    private interface Step { void run() throws Exception; }
    private record Timed(long delay, Step step) {}

    private final ForgeProbe probe;
    private final ForgePlugin forge;
    private final CharmService charms;
    private final List<Timed> steps = new ArrayList<>();
    private Player player;
    private World world;
    private Location home;
    private final List<Husk> husks = new ArrayList<>();
    // values carried between steps
    private Husk branded, chained, echoed, holed, frozen, cascade, waveTarget, summoned;
    private Item magnet;
    private double echoedHealth, holedHealth, magnetDistance, summonedHealth, chainedHealth;
    private Location anchorFrom, beforeDash;
    private List<Location> vein;

    private ForgeCharmChecks(ForgeProbe probe, ForgePlugin forge) {
        this.probe = probe;
        this.forge = forge;
        this.charms = forge.charms();
    }

    static void start(ForgeProbe probe, ForgePlugin forge, Runnable done, Consumer<Throwable> fail) {
        ForgeCharmChecks checks = new ForgeCharmChecks(probe, forge);
        checks.plan();
        checks.next(0, done, fail);
    }

    private void next(int index, Runnable done, Consumer<Throwable> fail) {
        if (index >= steps.size()) {
            cleanup();
            done.run();
            return;
        }
        Timed timed = steps.get(index);
        Bukkit.getScheduler().runTaskLater(forge, () -> {
            try {
                timed.step().run();
            } catch (Throwable error) {
                cleanup();
                fail.accept(error);
                return;
            }
            next(index + 1, done, fail);
        }, Math.max(1, timed.delay()));
    }

    private void at(long delay, Step step) {
        steps.add(new Timed(delay, step));
    }

    private void check(boolean condition, String name) {
        probe.check(condition, "charm runtime: " + name);
    }

    private void cleanup() {
        husks.forEach(husk -> { if (husk.isValid()) husk.remove(); });
        if (home != null) world.removePluginChunkTickets(forge);
    }

    private Husk husk(double dx, double dz, boolean aware) {
        Husk husk = world.spawn(home.clone().add(dx, 0, dz), Husk.class, h -> {
            h.setAware(aware);
            h.setPersistent(true);
            h.setRemoveWhenFarAway(false);
            h.getEquipment().clear();
        });
        husks.add(husk);
        return husk;
    }

    private void wear(Object... charmAndGrade) {
        while (!charms.wornItems(player).isEmpty()) charms.unequip(player, 0);
        for (int i = 0; i < charmAndGrade.length; i += 2) {
            Charm charm = (Charm) charmAndGrade[i];
            ItemStack item = ForgeApi.charm(charm.id(), ((Rarity) charmAndGrade[i + 1]).name());
            if (charms.equip(player, item) != CharmService.EquipResult.OK) throw new AssertionError("could not wear " + charm);
        }
    }

    private void ground(Location location) {
        player.teleport(location);
        ((CraftPlayer) player).getHandle().setOnGround(true);
    }

    private void sneak(boolean down) {
        player.setSneaking(down);
        Bukkit.getPluginManager().callEvent(new PlayerToggleSneakEvent(player, down));
    }

    @SuppressWarnings("removal")
    private boolean damageEvent(EntityDamageEvent.DamageCause cause, DamageType type, double amount) {
        EntityDamageEvent event = new EntityDamageEvent(player, cause, DamageSource.builder(type).build(), amount);
        Bukkit.getPluginManager().callEvent(event);
        return event.isCancelled();
    }

    private void plan() {
        at(1, () -> {
            world = Bukkit.getWorlds().getFirst();
            world.setTime(18000);
            // Close to spawn, where chunks keep ticking (mobs lose hurt frames, items move); away from the probe's elites.
            Location spawn = world.getSpawnLocation();
            int x = spawn.getBlockX() + 20, z = spawn.getBlockZ() + 20;
            home = new Location(world, x + 0.5, world.getHighestBlockYAt(x, z) + 1, z + 0.5, -90, 0); // facing +X
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++)
                world.getChunkAt((home.getBlockX() >> 4) + dx, (home.getBlockZ() >> 4) + dz).addPluginChunkTicket(forge);
            player = fake(world, home);
            ground(home);
            check(player.isOnline() && Bukkit.getOnlinePlayers().contains(player), "fake player online");
        });

        // ---- Reaper's Mark, Hex Brand, Echo Strike ----
        at(2, () -> {
            wear(Charm.REAPER_MARK, Rarity.MYTHIC, Charm.HEX_BRAND, Rarity.MYTHIC, Charm.ECHO_STRIKE, Rarity.MYTHIC);
            Husk low = husk(2, 3, false);
            low.setHealth(4);
            low.damage(1, player);
            check(low.isDead(), "Reaper's Mark executes a mob under 25%");
            branded = husk(3, -3, false);
            chained = husk(3.8, -3, false);
            branded.damage(2, player);
            check(branded.getPersistentDataContainer().has(new NamespacedKey(forge, "charm_hex"), PersistentDataType.STRING), "Hex Brand marks the mob hit");
            chainedHealth = chained.getHealth();
            branded.damage(1000, player);
            echoed = husk(-3, 0, false);
            echoed.damage(4, player);
            echoedHealth = echoed.getHealth();
        });
        at(20, () -> {
            check(chained.getHealth() < chainedHealth - 3, "Hex Brand: a branded mob's death blasts its neighbour: " + chainedHealth + " -> " + chained.getHealth());
            check(chained.getPersistentDataContainer().has(new NamespacedKey(forge, "charm_hex"), PersistentDataType.STRING), "Hex Brand spreads to the neighbour");
            check(echoed.getHealth() < echoedHealth - 1.8, "Echo Strike repeats the hit a moment later: " + echoedHealth + " -> " + echoed.getHealth());
        });

        // ---- Black Hole, Stasis Trap, Lodestar ----
        at(2, () -> {
            wear(Charm.BLACK_HOLE, Rarity.MYTHIC, Charm.STASIS_TRAP, Rarity.MYTHIC, Charm.LODESTAR, Rarity.MYTHIC);
            holed = husk(3, 3, true);
            holed.getAttribute(Attribute.MAX_HEALTH).setBaseValue(100);
            holed.setHealth(100);
            holed.setTarget(null);
            holed.damage(1, player);
            holedHealth = holed.getHealth();
            check(!holed.hasAI(), "Stasis Trap freezes the mob hit");
            magnet = world.dropItem(home.clone().add(-7, 0.5, 0), new ItemStack(Material.EMERALD));
            magnet.setPickupDelay(30);
            magnetDistance = magnet.getLocation().distance(player.getLocation());
        });
        at(25, () -> check(!magnet.isValid() || magnet.getLocation().distance(player.getLocation()) < magnetDistance - 1.5,
            "Lodestar pulls dropped items in: " + magnetDistance + " -> " + magnet.getLocation().distance(player.getLocation())
                + " age " + magnet.getTicksLived() + " velocity " + magnet.getVelocity()));
        at(45, () -> {
            check(!holed.isDead() && holed.hasAI(), "Stasis Trap releases after 3 seconds");
            check(holed.getHealth() < holedHealth - 2, "Black Hole grinds the mob: " + holedHealth + " -> " + holed.getHealth());
            magnet.remove();
        });

        // ---- Shadow Step, Crimson Wave, Overclock ----
        at(2, () -> {
            wear(Charm.SHADOW_STEP, Rarity.MYTHIC, Charm.CRIMSON_WAVE, Rarity.MYTHIC, Charm.OVERCLOCK, Rarity.MYTHIC);
            ground(home);
            beforeDash = player.getLocation();
            sneak(true);
            sneak(false);
            sneak(true);
            sneak(false);
            check(player.getLocation().getX() - beforeDash.getX() > 5, "Shadow Step dashes forward: " + beforeDash.getX() + " -> " + player.getLocation().getX());
            ground(home);
            player.setHealth(player.getAttribute(Attribute.MAX_HEALTH).getValue());
            player.getInventory().setItemInMainHand(new ItemStack(Material.IRON_SWORD));
            waveTarget = husk(4, 0, false);
            Bukkit.getPluginManager().callEvent(new PlayerAnimationEvent(player, PlayerAnimationType.ARM_SWING));
            for (int i = 0; i < 5; i++) husk(-2, 6 + i, false).damage(1000, player);
        });
        at(12, () -> {
            check(waveTarget.getHealth() < waveTarget.getAttribute(Attribute.MAX_HEALTH).getValue() - 3, "Crimson Wave hits a mob 4 blocks ahead: " + waveTarget.getHealth());
            check(player.getAttribute(Attribute.ATTACK_SPEED).getModifier(new NamespacedKey("evergarden_forge", "charm_overclock")) != null,
                "Overclock starts after 5 quick kills");
        });

        // ---- Soul Legion + Star Familiar ----
        at(2, () -> {
            wear(Charm.SOUL_LEGION, Rarity.MYTHIC, Charm.STAR_FAMILIAR, Rarity.MYTHIC);
            ground(home);
            for (int i = 0; i < 6; i++) husk(-3, -6 - i, false).damage(1000, player);
            summoned = husk(4, -4, false);
            summonedHealth = summoned.getHealth();
        });
        at(50, () -> check(summoned.getHealth() < summonedHealth - 6 || summoned.isDead(),
            "Soul Legion spirits and the Star Familiar attack a nearby mob: " + summonedHealth + " -> " + summoned.getHealth()));

        // ---- Time Stop, Vein of Ages, Starwalker ----
        at(2, () -> {
            wear(Charm.TIME_STOP, Rarity.COMMON, Charm.VEIN_OF_AGES, Rarity.MYTHIC, Charm.STARWALKER, Rarity.MYTHIC);
            ground(home);
            player.setInvulnerable(true);
            frozen = husk(6, 0, true);
            sneak(true);
            vein = new ArrayList<>();
            Location ore = home.clone().add(0, -1, 8);
            for (int i = 0; i < 5; i++) {
                Location block = ore.clone().add(i, 0, 0);
                block.getBlock().setType(Material.IRON_ORE);
                vein.add(block);
            }
            player.getInventory().setItemInMainHand(new ItemStack(Material.DIAMOND_PICKAXE));
        });
        at(3, () -> {
            check(player.getAllowFlight(), "Starwalker arms an air jump while on the ground");
            ground(home.clone().add(0, 2, 0));
            ((CraftPlayer) player).getHandle().setOnGround(false);
            PlayerToggleFlightEvent jump = new PlayerToggleFlightEvent(player, true);
            Bukkit.getPluginManager().callEvent(jump);
            check(jump.isCancelled() && player.getVelocity().getY() > 0.5 && !player.isFlying(), "Starwalker turns a flight toggle into a jump: " + player.getVelocity());
            ground(home);
            player.setSneaking(true);
        });
        at(40, () -> {
            check(!frozen.hasAI(), "Time Stop freezes mobs after holding sneak 1.5 seconds");
            check(player.getPersistentDataContainer().getOrDefault(new NamespacedKey(forge, "charm_time_stop_ready"), PersistentDataType.LONG, 0L)
                > System.currentTimeMillis() + 100_000, "Time Stop has its 120 second cooldown");
            sneak(false);
            player.breakBlock(vein.getFirst().getBlock());
        });
        at(3, () -> check(vein.stream().allMatch(location -> location.getBlock().getType() == Material.AIR), "Vein of Ages mines the whole vein"));
        at(90, () -> {
            check(frozen.hasAI(), "Time Stop ends after 4 seconds");
            frozen.remove(); // an awake husk would keep the player in combat for the Nightfall Veil check
        });

        // ---- Meteor Fall, Chrono Anchor, Hourglass ----
        at(2, () -> {
            wear(Charm.HOURGLASS, Rarity.MYTHIC, Charm.METEOR_FALL, Rarity.MYTHIC, Charm.CHRONO_ANCHOR, Rarity.MYTHIC);
            check(!player.getAllowFlight(), "taking Starwalker off removes the managed flight");
            player.setInvulnerable(false);
            ground(home);
        });
        at(80, () -> {
            Husk landing = husk(2, 0, false);
            double before = landing.getHealth();
            player.setFallDistance(12);
            check(damageEvent(EntityDamageEvent.DamageCause.FALL, DamageType.FALL, 9), "Meteor Fall cancels fall damage");
            check(landing.getHealth() < before - 5, "Meteor Fall blasts mobs where you land: " + before + " -> " + landing.getHealth());
            anchorFrom = player.getLocation();
            ground(home.clone().add(0, 0, 6));
            check(damageEvent(EntityDamageEvent.DamageCause.VOID, DamageType.OUT_OF_WORLD, 4), "Chrono Anchor cancels void damage");
            check(player.getLocation().distance(anchorFrom) < 1.5, "Chrono Anchor steps back to safe ground: " + player.getLocation());
            player.setHealth(20);
            player.damage(1000);
            check(!player.isDead() && player.getHealth() >= 6, "Hourglass rewinds a lethal hit: health " + player.getHealth());
            check(player.getPersistentDataContainer().getOrDefault(new NamespacedKey(forge, "charm_hourglass_ready"), PersistentDataType.LONG, 0L)
                > System.currentTimeMillis() + 200_000, "Hourglass has its cooldown");
        });

        // ---- Nightfall Veil, Bat Form, Timeless ----
        at(2, () -> {
            wear(Charm.NIGHTFALL_VEIL, Rarity.MYTHIC, Charm.BAT_FORM, Rarity.MYTHIC, Charm.TIMELESS, Rarity.MYTHIC);
            ground(home);
            // The main probe's elites wander over and keep the player in combat; their checks are long done.
            for (var entity : player.getNearbyEntities(64, 32, 64)) if (entity instanceof org.bukkit.entity.Enemy) entity.remove();
        });
        at(120, () -> {
            PotionEffect veil = player.getPotionEffect(PotionEffectType.INVISIBILITY);
            check(veil != null && veil.isInfinite(), "Nightfall Veil hides a player out of combat");
            // Mob damage to players scales with difficulty (easy: half + 1); 9 health - 6 lands at 3-5, under 30%.
            player.setHealth(9);
            player.damage(6, husk(1.5, 0, false));
        });
        at(3, () -> {
            check(player.isFlying(), "Bat Form takes flight below 30% health");
            PotionEffect veil = player.getPotionEffect(PotionEffectType.INVISIBILITY);
            check(veil == null || !veil.isInfinite(), "a mob hit breaks Nightfall Veil");
        });
        at(70, () -> {
            check(!player.isFlying(), "Bat Form ends after 3 seconds");
            player.getInventory().clear();
            player.getInventory().setItem(0, new ItemStack(Material.DIAMOND_SWORD));
            player.getInventory().setItem(1, new ItemStack(Material.APPLE, 5));
            player.getInventory().setItem(20, new ItemStack(Material.DIRT, 3));
            player.setLevel(10);
            player.setHealth(1);
            player.damage(1000);
            check(player.isDead(), "the player dies");
            var stored = player.getPersistentDataContainer().get(new NamespacedKey(forge, "charm_timeless_items"),
                PersistentDataType.LIST.listTypeFrom(PersistentDataType.BYTE_ARRAY));
            check(stored != null && stored.size() == 2, "Timeless keeps the two hotbar stacks, not the backpack dirt: " + (stored == null ? 0 : stored.size()));
            check(player.getPersistentDataContainer().getOrDefault(new NamespacedKey(forge, "charm_timeless_ready"), PersistentDataType.LONG, 0L)
                > System.currentTimeMillis() + 14 * 60_000, "Timeless has its 15 minute cooldown");
            List<Item> drops = world.getEntitiesByClass(Item.class).stream().filter(i -> i.getLocation().distance(player.getLocation()) < 4).toList();
            check(drops.stream().noneMatch(i -> i.getItemStack().getType() == Material.DIAMOND_SWORD)
                && drops.stream().anyMatch(i -> i.getItemStack().getType() == Material.DIRT), "the sword stays out of the death drops, the dirt drops");
            drops.forEach(Item::remove);
            player.spigot().respawn();
        });
        at(40, () -> {
            check(!player.isDead() && player.getInventory().contains(Material.DIAMOND_SWORD) && player.getInventory().contains(Material.APPLE, 5)
                && player.getLevel() == 10, "after respawning Timeless gives the hotbar and levels back");
            check(!player.getPersistentDataContainer().has(new NamespacedKey(forge, "charm_timeless_items")), "Timeless storage is emptied once returned");
            while (!charms.wornItems(player).isEmpty()) charms.unequip(player, 0);
        });
    }

    private static Player fake(World world, Location at) {
        var server = MinecraftServer.getServer();
        var level = ((CraftWorld) world).getHandle();
        var profile = new GameProfile(UUID.randomUUID(), "CharmRunner");
        var actor = new ServerPlayer(server, level, profile, ClientInformation.createDefault());
        var connection = new Connection(PacketFlow.SERVERBOUND);
        connection.channel = new EmbeddedChannel();
        connection.address = new InetSocketAddress("127.0.0.1", 1);
        actor.connection = new ServerGamePacketListenerImpl(server, connection, actor, CommonListenerCookie.createInitial(profile, false)) {
            @Override public boolean hasClientLoaded() { return true; }
        };
        actor.setPos(at.getX(), at.getY(), at.getZ());
        server.getPlayerList().getPlayers().add(actor);
        server.getPlayerList().getPlayersByUUID().put(profile.id(), actor);
        level.addNewPlayer(actor);
        Player player = actor.getBukkitEntity();
        player.setGravity(false);
        player.setGameMode(GameMode.SURVIVAL);
        player.setInvulnerable(false);
        return player;
    }
}
