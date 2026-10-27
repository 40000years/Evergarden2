import com.example.voidscape.VoidscapePlugin;
import com.example.voidscape.enchant.UniqueEnchant;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerPlayer;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffectType;
import java.nio.file.*;
import java.util.*;

/** Ancient enchant balance on a real Paper server: 33% procs, cooldowns and boss exemptions. */
public final class UniqueBalanceProbe extends JavaPlugin implements Listener {
    record Hit(int tick, Entity target, double raw, boolean cancelled) {}
    final List<Hit> hits = new ArrayList<>();
    VoidscapePlugin garden; World world; ServerPlayer actor; Player player; Location base; int checks; boolean done;
    java.lang.reflect.Field strengthTicker;

    void check(boolean ok, String label) {if (!ok) throw new AssertionError(label); checks++; getLogger().info("PASS: " + label);}

    @Override public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        Bukkit.getScheduler().runTaskLater(this, () -> {try {setup(); bows();} catch (Throwable error) {finish(error);}}, 200);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void record(EntityDamageEvent event) {hits.add(new Hit(Bukkit.getCurrentTick(), event.getEntity(), event.getDamage(), event.isCancelled()));}

    void setup() throws Exception {
        garden = (VoidscapePlugin) Bukkit.getPluginManager().getPlugin("Evergarden");
        check(garden != null && garden.isEnabled(), "Evergarden boots");
        world = Bukkit.getWorlds().get(0);
        world.setTime(18000); world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false); world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        world.setDifficulty(Difficulty.HARD);
        base = new Location(world, 0.5, 150, 0.5);
        for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) world.addPluginChunkTicket(x, z, this);
        for (int x = -24; x <= 24; x++) for (int z = -24; z <= 24; z++) world.getBlockAt(x, 149, z).setType(Material.SMOOTH_STONE, false);
        var server = net.minecraft.server.MinecraftServer.getServer();
        var level = ((org.bukkit.craftbukkit.CraftWorld) world).getHandle();
        var profile = new com.mojang.authlib.GameProfile(UUID.randomUUID(), "BalanceTest");
        actor = new ServerPlayer(server, level, profile, net.minecraft.server.level.ClientInformation.createDefault());
        var connection = new Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        connection.channel = new EmbeddedChannel(); connection.address = new java.net.InetSocketAddress("127.0.0.1", 1);
        actor.connection = new net.minecraft.server.network.ServerGamePacketListenerImpl(server, connection, actor,
                net.minecraft.server.network.CommonListenerCookie.createInitial(profile, false)) {@Override public boolean hasClientLoaded() {return true;}};
        actor.setPos(base.getX(), base.getY(), base.getZ());
        server.getPlayerList().getPlayers().add(actor); server.getPlayerList().getPlayersByUUID().put(profile.id(), actor); level.addNewPlayer(actor);
        player = actor.getBukkitEntity(); player.setGameMode(GameMode.SURVIVAL); player.setInvulnerable(true);
        for (Class<?> type = actor.getClass(); type != null && strengthTicker == null; type = type.getSuperclass())
            for (var field : type.getDeclaredFields()) if (field.getName().equals("attackStrengthTicker")) {strengthTicker = field; field.setAccessible(true);}
        Bukkit.getScheduler().runTaskTimer(this, () -> {if (!done) actor.doTick();}, 1, 1);
    }

    void after(int ticks, Runnable task) {Bukkit.getScheduler().runTaskLater(this, () -> {if (done) return; try {task.run();} catch (Throwable error) {finish(error);}}, ticks);}

    <T extends LivingEntity> T spawn(Class<T> type, double dx, double dz, double hp) {
        return world.spawn(base.clone().add(dx, 0, dz), type, entity -> {
            entity.getAttribute(Attribute.MAX_HEALTH).setBaseValue(hp); entity.setHealth(hp);
            if (entity instanceof Mob m) {m.setAI(false); m.setPersistent(true);}
            entity.getEquipment().clear();
        });
    }

    void clean() {for (Entity e : world.getEntities()) if (!(e instanceof Player)) e.remove();}

    ItemStack enchanted(Material material, UniqueEnchant enchant) {
        ItemStack item = new ItemStack(material);
        var meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(new NamespacedKey("evergarden", "ue_" + enchant.id()), PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    Arrow arrow(UniqueEnchant enchant, Location at) {
        Arrow arrow = world.spawn(at, Arrow.class, a -> {a.setShooter(player); a.setGravity(false);});
        arrow.getPersistentDataContainer().set(new NamespacedKey("evergarden", "shot_unique"), PersistentDataType.STRING, enchant.name());
        arrow.getPersistentDataContainer().set(new NamespacedKey("evergarden", "shot_shooter"), PersistentDataType.STRING, player.getUniqueId().toString());
        return arrow;
    }

    /** One arrow landing on {@code hit} (or the ground at {@code at}) through the real hit event. */
    void land(UniqueEnchant enchant, Entity hit, Location at) {
        Arrow arrow = arrow(enchant, at);
        Bukkit.getPluginManager().callEvent(new ProjectileHitEvent(arrow, hit, hit == null ? at.getBlock().getRelative(0, -1, 0) : null, null));
        arrow.remove();
    }

    void resetCooldowns() throws Exception {
        Object listener = garden.abilities();
        var cooldowns = listener.getClass().getDeclaredField("ancientCooldown"); cooldowns.setAccessible(true);
        ((Map<?, ?>) cooldowns.get(listener)).clear();
    }

    static boolean rate(int procs, int trials) {double r = procs / (double) trials; return r > .25 && r < .42;}

    void bows() throws Exception {
        // Ricochet: 33% with no cooldown.
        clean();
        Zombie victim = spawn(Zombie.class, 0, 4, 1000);
        Zombie nearby = spawn(Zombie.class, 2, 4, 1000);
        int procs = 0, trials = 300;
        for (int i = 0; i < trials; i++) {
            int before = (int) hits.stream().filter(h -> h.target() == nearby).count();
            nearby.setNoDamageTicks(0); nearby.setHealth(1000); victim.setHealth(1000);
            land(UniqueEnchant.RICOCHET, victim, victim.getLocation());
            if (hits.stream().filter(h -> h.target() == nearby).count() > before) procs++;
        }
        check(rate(procs, trials), "Ricochet chains on about 33% of arrows (" + procs + "/" + trials + ")");
        // Absolute Zero: 33%, then 6 s cooldown; bosses take the damage but are not slowed.
        clean(); resetCooldowns();
        Zombie mob = spawn(Zombie.class, 0, 4, 1000);
        Zombie boss = spawn(Zombie.class, 1, 4, 1000); boss.addScoreboardTag("boss");
        int tries = 0;
        while (!mob.hasPotionEffect(PotionEffectType.SLOWNESS) && tries++ < 60) land(UniqueEnchant.ABSOLUTE_ZERO, null, mob.getLocation());
        check(mob.hasPotionEffect(PotionEffectType.SLOWNESS) && mob.getPotionEffect(PotionEffectType.SLOWNESS).getAmplifier() == 3, "Absolute Zero procs (after " + tries + " arrows) with Slowness IV");
        check(!boss.hasPotionEffect(PotionEffectType.SLOWNESS) && boss.getHealth() < 1000, "Absolute Zero damages a boss but does not freeze it");
        mob.removePotionEffect(PotionEffectType.SLOWNESS);
        for (int i = 0; i < 60; i++) land(UniqueEnchant.ABSOLUTE_ZERO, null, mob.getLocation());
        check(!mob.hasPotionEffect(PotionEffectType.SLOWNESS), "Absolute Zero is on cooldown for 6 s (60 arrows, no proc)");
        // Singularity: 33%, 8 s cooldown, never pulls a boss.
        clean(); resetCooldowns();
        Location hole = base.clone().add(0, 0, 6);
        // Live AI with no walking speed: only the black hole can move them.
        Zombie pulled = spawn(Zombie.class, 4, 6, 1000);
        Zombie heavy = spawn(Zombie.class, -4, 6, 1000); heavy.addScoreboardTag("boss");
        for (Zombie z : List.of(pulled, heavy)) {z.setAI(true); z.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(0);}
        Location pulledHome = pulled.getLocation(), heavyHome = heavy.getLocation();
        int[] singularity = {0};
        Runnable[] fire = new Runnable[1];
        fire[0] = () -> {
            land(UniqueEnchant.SINGULARITY, null, hole);
            after(14, () -> {
                double moved = pulled.getLocation().distance(pulledHome);
                if (moved < .3 && singularity[0]++ < 60) {fire[0].run(); return;}
                check(moved >= .3, "Singularity procs (after " + singularity[0] + " arrows) and pulls a mob " + String.format("%.2f", moved) + " blocks");
                check(heavy.getLocation().distance(heavyHome) < .1, "Singularity does not pull a boss");
                after(60, () -> {
                    pulled.teleport(pulledHome);
                    for (int i = 0; i < 60; i++) land(UniqueEnchant.SINGULARITY, null, hole);
                    after(14, () -> {
                        check(pulled.getLocation().distance(pulledHome) < .1, "Singularity is on cooldown for 8 s");
                        meteor();
                    });
                });
            });
        };
        fire[0].run();
    }

    void meteor() {
        clean();
        try {resetCooldowns();} catch (Exception error) {throw new RuntimeException(error);}
        Zombie mob = spawn(Zombie.class, 0, 4, 1000);
        int[] tries = {0};
        Runnable[] fire = new Runnable[1];
        fire[0] = () -> {
            int mark = Bukkit.getCurrentTick();
            land(UniqueEnchant.METEOR_ARROW, null, mob.getLocation());
            after(22, () -> {
                boolean hit = hits.stream().anyMatch(h -> h.target() == mob && h.tick() > mark && Math.abs(h.raw() - 32) < .01);
                if (!hit && tries[0]++ < 40) {fire[0].run(); return;}
                check(hit, "Meteor Arrow procs (after " + tries[0] + " arrows) for 32");
                int quiet = Bukkit.getCurrentTick();
                for (int i = 0; i < 40; i++) land(UniqueEnchant.METEOR_ARROW, null, mob.getLocation());
                after(22, () -> {
                    check(hits.stream().noneMatch(h -> h.target() == mob && h.tick() > quiet && Math.abs(h.raw() - 32) < .01), "Meteor Arrow is on cooldown for 5 s");
                    try {colossus(); melee();} catch (Throwable error) {finish(error);}
                });
            });
        };
        fire[0].run();
    }

    void colossus() {
        clean();
        Zombie titan = spawn(Zombie.class, 0, 4, 1000);
        int procs = 0, trials = 300;
        double biggest = 0;
        for (int i = 0; i < trials; i++) {
            Arrow arrow = arrow(UniqueEnchant.COLOSSUS_SLAYER, titan.getLocation().add(0, 3, 0));
            titan.setNoDamageTicks(0); titan.setHealth(1000);
            titan.damage(5, DamageSource.builder(DamageType.ARROW).withDirectEntity(arrow).withCausingEntity(player).build());
            arrow.remove();
            double raw = hits.get(hits.size() - 1).raw();
            if (raw > 5.01) {procs++; biggest = Math.max(biggest, raw - 5);}
        }
        check(rate(procs, trials), "Colossus Slayer procs on about 33% of arrows (" + procs + "/" + trials + ")");
        check(Math.abs(biggest - 80) < .01, "Colossus Slayer bonus is 8% of max HP (" + biggest + " on 1,000 HP)");
    }

    void swing(LivingEntity target) {
        try {strengthTicker.setInt(actor, 1000);} catch (IllegalAccessException error) {throw new RuntimeException(error);}
        target.setNoDamageTicks(0);
        actor.attack(((CraftEntity) target).getHandle());
    }

    void melee() {
        // Guillotine: executes a mob at 10% HP, never a boss.
        clean();
        player.getInventory().setItemInMainHand(enchanted(Material.NETHERITE_SWORD, UniqueEnchant.GUILLOTINE)); actor.doTick();
        Zombie low = spawn(Zombie.class, 0, 2, 1000); low.setHealth(100);
        swing(low);
        check(low.isDead() || low.getHealth() <= 0, "Guillotine still executes a normal mob under 15%");
        Zombie boss = spawn(Zombie.class, 4, 2, 1000); boss.setHealth(100); boss.addScoreboardTag("boss");
        swing(boss);
        check(!boss.isDead() && boss.getHealth() > 80, "Guillotine does not execute a boss (" + boss.getHealth() + ")");
        // Titan Breach: 33%, bonus goes through the hit (raw damage) instead of setHealth.
        clean();
        player.getInventory().setItemInMainHand(enchanted(Material.NETHERITE_AXE, UniqueEnchant.TITAN_BREACH)); actor.doTick();
        Zombie target = spawn(Zombie.class, 0, 2, 1000);
        target.getEquipment().setChestplate(new ItemStack(Material.DIAMOND_CHESTPLATE));
        double plain = -1;
        int procs = 0, trials = 300;
        List<Double> raws = new ArrayList<>();
        for (int i = 0; i < trials; i++) {
            target.setHealth(1000);
            swing(target);
            raws.add(hits.get(hits.size() - 1).raw());
        }
        plain = raws.stream().mapToDouble(Double::doubleValue).min().orElse(0);
        for (double raw : raws) if (raw > plain + 9) procs++;
        check(rate(procs, trials), "Titan Breach procs on about 33% of hits (" + procs + "/" + trials + ")");
        double boosted = raws.stream().mapToDouble(Double::doubleValue).max().orElse(0);
        double armor = target.getAttribute(Attribute.ARMOR).getValue();
        check(Math.abs(boosted - plain - (10 + armor * .35)) < .01, "Titan Breach adds 10 + 35% armor to the hit itself (" + (boosted - plain) + ")");
        // Echo Strike: 33%.
        clean();
        player.getInventory().setItemInMainHand(enchanted(Material.NETHERITE_SWORD, UniqueEnchant.ECHO_STRIKE)); actor.doTick();
        Zombie echo = spawn(Zombie.class, 0, 2, 1000);
        int start = Bukkit.getCurrentTick();
        int swings = 240;
        for (int i = 0; i < swings; i++) after(i * 6, () -> {echo.setHealth(1000); swing(echo);});
        after(swings * 6 + 10, () -> {
            long all = hits.stream().filter(h -> h.target() == echo && h.tick() >= start && !h.cancelled()).count();
            int echoes = (int) (all - swings);
            check(rate(echoes, swings), "Echo Strike repeats on about 33% of hits (" + echoes + "/" + swings + ")");
            finish(null);
        });
    }

    void finish(Throwable error) {
        if (done) return;
        done = true;
        try {Files.writeString(Path.of("arkhaia-result.txt"), error == null ? "PASS " + checks + " Paper checks" : "FAIL " + error);} catch (Exception ignored) {}
        if (error != null) getLogger().log(java.util.logging.Level.SEVERE, "Unique balance probe failed", error);
        Bukkit.getScheduler().runTaskLater(this, Bukkit::shutdown, 5);
    }
}
