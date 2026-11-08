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
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffectType;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.random.RandomGenerator;

/**
 * e2.188: Tier VI-VIII sanctums. Labels and numbers, keys, Vault rolls (Tier V + Tier III/IV/V + charm 3/5/7%),
 * placement only on untouched ground, the illager/blaze/magma waves with Forge uniques, no infighting, two
 * bosses from Tier VII, the melee / arrow / spell resistances and the tier seals on a real (connectionless) player.
 */
public final class SanctumTierChecks extends JavaPlugin implements org.bukkit.event.Listener {
    private final List<String> playerHits = new ArrayList<>();

    @org.bukkit.event.EventHandler(priority = org.bukkit.event.EventPriority.MONITOR)
    public void onPlayerHurt(org.bukkit.event.entity.EntityDamageEvent event) {
        if (event.getEntity() == player) playerHits.add(event.getCause() + "/" + event.getDamageSource().getDamageType().getKey().getKey()
            + " raw " + event.getDamage() + " final " + event.getFinalDamage() + (event.isCancelled() ? " cancelled" : ""));
    }
    private interface Step { void run() throws Exception; }
    private record Timed(long delay, Step step) {}
    private final List<String> passed = new ArrayList<>();
    private final List<Timed> steps = new ArrayList<>();

    private Plugin garden;
    private Object dungeons, layout, expansion;
    private World world;
    private Class<?> siteClass, kindClass, encounterClass;
    private Player player;
    private Object site6, site7, site8, encounter;
    private final List<Mob> spawned = new ArrayList<>();
    private double before;

    private void check(boolean condition, String name) {
        if (!condition) throw new AssertionError(name);
        passed.add(name);
    }

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        plan();
        next(0);
    }

    private void next(int index) {
        if (index >= steps.size()) {
            finish("PASS " + passed.size() + " sanctum tier checks\n" + String.join("\n", passed));
            return;
        }
        Bukkit.getScheduler().runTaskLater(this, () -> {
            try {
                steps.get(index).step().run();
            } catch (Throwable error) {
                java.io.StringWriter trace = new java.io.StringWriter();
                error.printStackTrace(new java.io.PrintWriter(trace));
                getLogger().severe(trace.toString());
                finish("FAIL " + error + "\npassed before failure: " + passed);
                return;
            }
            next(index + 1);
        }, Math.max(1, steps.get(index).delay()));
    }

    private void finish(String text) {
        try {
            Files.writeString(Path.of("sanctum-tiers-result.txt"), text);
        } catch (Exception ignored) {
        }
        Bukkit.getScheduler().runTaskLater(this, Bukkit::shutdown, 20L);
    }

    private void at(long delay, Step step) {
        steps.add(new Timed(delay, step));
    }

    private static Object call(Object target, String name, Object... args) throws Exception {
        for (Class<?> type = target instanceof Class<?> c ? c : target.getClass(); type != null; type = type.getSuperclass()) {
            for (Method method : type.getDeclaredMethods()) {
                if (!method.getName().equals(name) || method.getParameterCount() != args.length) continue;
                boolean fits = true;
                Class<?>[] params = method.getParameterTypes();
                for (int i = 0; i < args.length; i++) {
                    Class<?> p = params[i].isPrimitive() ? box(params[i]) : params[i];
                    if (args[i] != null && !p.isInstance(args[i])) fits = false;
                }
                if (!fits) continue;
                method.setAccessible(true);
                return method.invoke(target instanceof Class<?> ? null : target, args);
            }
        }
        throw new NoSuchMethodException(name);
    }

    private static Class<?> box(Class<?> primitive) {
        return primitive == int.class ? Integer.class : primitive == long.class ? Long.class : primitive == double.class ? Double.class
            : primitive == boolean.class ? Boolean.class : primitive == float.class ? Float.class : primitive;
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private Object site(String kind, int x, int z, int tier) throws Exception {
        Constructor<?> constructor = siteClass.getConstructor(kindClass, int.class, int.class, long.class, int.class);
        return constructor.newInstance(Enum.valueOf((Class) kindClass, kind), x, z, 7L, tier);
    }

    private int tier(Object site) throws Exception { return (int) call(site, "tier"); }
    private int sx(Object site) throws Exception { return (int) call(site, "x"); }
    private int sz(Object site) throws Exception { return (int) call(site, "z"); }

    private static String charmOf(ItemStack item) {
        return item == null || !item.hasItemMeta() ? null : item.getItemMeta().getPersistentDataContainer().get(new NamespacedKey("evergardenforge", "charm"), PersistentDataType.STRING);
    }

    private static String gradeOf(ItemStack item) {
        return item.getItemMeta().getPersistentDataContainer().get(new NamespacedKey("evergardenforge", "charm_grade"), PersistentDataType.STRING);
    }

    @SuppressWarnings("unchecked")
    private void plan() {
        at(60, () -> {
            garden = Bukkit.getPluginManager().getPlugin("Evergarden");
            check(garden != null && garden.isEnabled(), "Evergarden enabled: " + garden.getPluginMeta().getVersion());
            check(Bukkit.getPluginManager().getPlugin("EvergardenForge").isEnabled(), "EvergardenForge enabled");
            dungeons = call(garden, "dungeons");
            layout = call(garden, "layout");
            expansion = call(dungeons, "expansion");
            world = (World) call(garden, "world");
            ClassLoader loader = garden.getClass().getClassLoader();
            siteClass = Class.forName("com.example.voidscape.world.DungeonLayout$Site", true, loader);
            kindClass = Class.forName("com.example.voidscape.world.DungeonLayout$Kind", true, loader);
            encounterClass = Class.forName("com.example.voidscape.dungeon.DungeonManager$Encounter", true, loader);
            Object six = site("SANCTUM_DARK", 0, 0, 6), seven = site("SANCTUM_ASTRAL", 0, 0, 7), eight = site("SANCTUM_TIME", 0, 0, 8), nine = site("SANCTUM_TIME", 0, 0, 9);
            check(call(six, "romanTier").equals("VI") && call(seven, "romanTier").equals("VII") && call(eight, "romanTier").equals("VIII") && tier(nine) == 8,
                "Site tiers VI-VIII (tier 9 clamps to VIII)");
            check(call(six, "tierColorCode").equals("§9") && call(seven, "tierColorCode").equals("§5") && call(eight, "tierColorCode").equals("§4"), "VI azure, VII violet, VIII blood");
            check(((String) call(seven, "difficultyDescription")).contains("70%") && ((String) call(eight, "difficultyDescription")).contains("50%"), "VII/VIII descriptions state their resistances");
            Class<?> balance = Class.forName("com.example.voidscape.dungeon.DungeonBalance", true, loader);
            double h6 = (double) call(balance, "healthMultiplier", 6), h7 = (double) call(balance, "healthMultiplier", 7), h8 = (double) call(balance, "healthMultiplier", 8);
            double d6 = (double) call(balance, "damageMultiplier", 6), d7 = (double) call(balance, "damageMultiplier", 7), d8 = (double) call(balance, "damageMultiplier", 8);
            check(Math.abs(h6 - 1.2) < 1e-9 && Math.abs(d6 - 1.45) < 1e-9 && Math.abs(h7 - 1.26) < 1e-9 && Math.abs(d7 - 1.6) < 1e-9
                && Math.abs(h8 - 1.323) < 1e-9 && Math.abs(d8 - 1.75) < 1e-9 && (double) call(balance, "healthMultiplier", 5) == 1.0
                && (double) call(balance, "damageMultiplier", 5) == 1.0,
                "health VI x1.2 · VII x1.26 · VIII x1.323; damage VI x1.45 · VII x1.6 · VIII x1.75 (e2.193)");
            check(Math.abs((double) call(balance, "rendRatio", 6) - 0.22) < 1e-9 && Math.abs((double) call(balance, "rendRatio", 7) - 0.28) < 1e-9
                && Math.abs((double) call(balance, "rendRatio", 8) - 0.34) < 1e-9 && (double) call(balance, "rendRatio", 5) == 0.0
                && Math.abs((double) call(balance, "bossHealthMultiplier", 8) - 1.75) < 1e-9 && (double) call(balance, "bossHealthMultiplier", 5) == 1.0
                && Math.abs((double) call(balance, "skillMultiplier", 8) - 1.75) < 1e-9 && (double) call(balance, "skillMultiplier", 5) == 1.0,
                "Tier VI-VIII rend 22/28/34% past armor and Protection, bosses x1.5/x1.6/x1.75 tougher, skills scale with the tier");
            check((double) call(balance, "meleeTaken", 7) == 0.3 && (double) call(balance, "rangedTaken", 7) == 1 && (double) call(balance, "magicTaken", 7) == 1
                && (double) call(balance, "meleeTaken", 8) == 1 && (double) call(balance, "rangedTaken", 8) == 0.5 && (double) call(balance, "magicTaken", 8) == 0.5
                && (double) call(balance, "meleeTaken", 6) == 1, "VII melee 70% resisted · VIII arrows and spells 50% · VI none");

            Object relics = call(garden, "relics");
            for (int t = 6; t <= 8; t++) check((int) call(relics, "getVoidKeyTier", call(relics, "createVoidKey", t)) == t, "Key Tier " + t + " reads back as " + t);
            ItemStack legacy = (ItemStack) call(relics, "createVoidKey");
            legacy.editMeta(meta -> meta.getPersistentDataContainer().remove(new NamespacedKey("voidscape", "key_tier")));
            check((int) call(relics, "getVoidKeyTier", legacy) == 5, "an untagged master key stays Tier V (never opens VI-VIII)");
        });

        // ---- Vault rolls ----
        at(1, () -> {
            Object relics = call(garden, "relics");
            Object five = site("SANCTUM_DARK", 100, 100, 5);
            for (String kind : List.of("SANCTUM_DARK", "SANCTUM_ASTRAL", "SANCTUM_TIME")) for (int t = 6; t <= 8; t++) {
                Object s = site(kind, 100, 100, t);
                int rolls = 12000, charms = 0, minItems = 99, diamonds = 0, golden = 0, themed = 0, overEight = 0;
                Map<String, Integer> grades = new TreeMap<>();
                String temple = kind.equals("SANCTUM_DARK") ? "dark" : kind.equals("SANCTUM_ASTRAL") ? "astral" : "time";
                Class<?> charmClass = Class.forName("com.example.forge.ForgeApi", true, Bukkit.getPluginManager().getPlugin("EvergardenForge").getClass().getClassLoader());
                for (int i = 0; i < rolls; i++) {
                    List<ItemStack> items = (List<ItemStack>) call(relics, "rollVaultRewards", s, (RandomGenerator) new SplittableRandom(i * 31L + t));
                    int charmCount = 0;
                    for (ItemStack item : items) {
                        String id = charmOf(item);
                        if (id == null) continue;
                        charmCount++;
                        if (!temple.equals(call(charmClass, "charmTemple", id))) throw new AssertionError("charm " + id + " not from " + temple);
                        grades.merge(gradeOf(item), 1, Integer::sum);
                    }
                    charms += charmCount;
                    minItems = Math.min(minItems, items.size() - charmCount);
                    boolean hasGolden = false, hasGem = false;
                    for (ItemStack item : items) {
                        if (item.getType().name().startsWith("DIAMOND_")) diamonds++;
                        if (item.getType() == org.bukkit.Material.FEATHER && item.getAmount() == t - 5) hasGolden = true;
                        if (item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().getKeys().stream().anyMatch(k -> k.getNamespace().equals("evergardenforge") && k.getKey().contains("gem"))) hasGem = true;
                        Object cap = call(relics, "getLimitBreakCap", item);
                        if (cap instanceof Integer c && c > 8) overEight++;
                    }
                    if (hasGolden) golden++;
                    if (hasGem) themed++;
                }
                double rate = charms / (double) rolls, expected = t == 6 ? 0.10 : t == 7 ? 0.15 : 0.22;
                check(minItems >= 4, kind + " Tier " + t + ": two hoard draws plus golden feathers and gem rewards every time (" + minItems + ")");
                check(diamonds == 0, kind + " Tier " + t + ": no diamond gear in the hoard (" + diamonds + ")");
                check(golden == rolls, kind + " Tier " + t + ": every opening pays " + (t - 5) + " Golden Seraph Feather(s) (" + golden + "/" + rolls + ")");
                check(themed == rolls, kind + " Tier " + t + ": every opening carries Forge gems (" + themed + "/" + rolls + ")");
                check(overEight > rolls * 0.05, kind + " Tier " + t + ": Limit Break scrolls past VIII drop (" + overEight + " in " + rolls + ")");
                check(Math.abs(rate - expected) < 0.012, kind + " Tier " + t + " charm rate " + rate + " ~ " + expected);
                if (kind.equals("SANCTUM_DARK")) {
                    Set<String> allowed = t == 6 ? Set.of("COMMON", "UNCOMMON", "RARE") : t == 7 ? Set.of("UNCOMMON", "RARE", "EPIC") : Set.of("RARE", "EPIC", "MYTHIC");
                    check(allowed.containsAll(grades.keySet()), "Tier " + t + " charm grades " + grades + " within " + allowed);
                }
            }
            int fives = 0;
            for (int i = 0; i < 2000; i++) for (ItemStack item : (List<ItemStack>) call(relics, "rollVaultRewards", five, (RandomGenerator) new SplittableRandom(i))) if (charmOf(item) != null) fives++;
            check(fives == 0, "Tier V Vaults never give charms");
            Object sharp = Enum.valueOf((Class<Enum>) Class.forName("com.example.voidscape.enchant.LimitBreakType", true, relics.getClass().getClassLoader()), "SHARPNESS");
            Object fortune = Enum.valueOf((Class<Enum>) Class.forName("com.example.voidscape.enchant.LimitBreakType", true, relics.getClass().getClassLoader()), "FORTUNE");
            int[] sharpCaps = new int[9], fortuneCaps = new int[9];
            for (int t = 5; t <= 8; t++) {
                sharpCaps[t] = (int) call(relics, "getLimitBreakCap", call(relics, "createScrollLimitBreak", sharp, t));
                fortuneCaps[t] = (int) call(relics, "getLimitBreakCap", call(relics, "createScrollLimitBreak", fortune, t));
            }
            check(sharpCaps[5] == 8 && sharpCaps[6] == 9 && sharpCaps[7] == 10 && sharpCaps[8] == 10
                && fortuneCaps[5] == 5 && fortuneCaps[6] == 6 && fortuneCaps[7] == 7 && fortuneCaps[8] == 8,
                "Limit Break past VIII: Sharpness V-VIII caps " + sharpCaps[5] + "/" + sharpCaps[6] + "/" + sharpCaps[7] + "/" + sharpCaps[8]
                + ", Fortune " + fortuneCaps[5] + "/" + fortuneCaps[6] + "/" + fortuneCaps[7] + "/" + fortuneCaps[8]);
        });

        // ---- placement: untouched ground only ----
        at(1, () -> {
            int size = (int) call(layout, "cellSize");
            Object fresh = null, explored = null;
            int fx = 0, fz = 0, ex = 0, ez = 0;
            for (int gx = 200; gx < 400 && (fresh == null || explored == null); gx++) {
                for (int gz = 200; gz < 230; gz++) {
                    Object candidate = call(layout, "extraCandidate", gx, gz);
                    if (candidate == null) continue;
                    if (fresh == null) { fresh = candidate; fx = gx; fz = gz; }
                    else if (explored == null && Math.abs(gx - fx) > 3) { explored = candidate; ex = gx; ez = gz; break; }
                }
            }
            check(fresh != null && explored != null, "the far grid has Tier VI-VIII corner candidates");
            // Explore the second one's ground first: its decision must refuse it.
            world.getChunkAt(sx(explored) >> 4, sz(explored) >> 4).load(true);
            call(expansion, "decideAround", ex * size, ez * size, 0);
            call(expansion, "decideAround", fx * size, fz * size, 0);
            List<?> nearExplored = (List<?>) call(layout, "nearby", sx(explored), sz(explored));
            List<?> nearFresh = (List<?>) call(layout, "nearby", sx(fresh), sz(fresh));
            boolean exploredBuilt = false, freshBuilt = false;
            for (Object s : nearExplored) if (sx(s) == sx(explored) && sz(s) == sz(explored)) exploredBuilt = true;
            for (Object s : nearFresh) if (sx(s) == sx(fresh) && sz(s) == sz(fresh)) freshBuilt = true;
            check(!exploredBuilt, "a corner whose ground was already generated never gets a shrine");
            check(freshBuilt && tier(fresh) >= 6, "a corner on untouched ground becomes a Tier " + tier(fresh) + " shrine at " + sx(fresh) + "," + sz(fresh));
            for (Object s : nearFresh) if (tier(s) <= 5) check(Math.hypot(sx(s) - sx(fresh), sz(s) - sz(fresh)) >= 190, "the new shrine keeps 190+ blocks from regular shrine " + sx(s) + "," + sz(s));
            check(call(layout, "at", sx(fresh), sz(fresh), 4) != null, "the shrine is found by position");
            world.getChunkAt(sx(fresh) >> 4, (sz(fresh) + 8) >> 4).load(true);
            world.getChunkAt(sx(fresh) >> 4, (sz(fresh) - 16) >> 4).load(true);
            check(world.getBlockAt(sx(fresh), 97, sz(fresh) + 8).getType() == org.bukkit.Material.LODESTONE
                && world.getBlockAt(sx(fresh), 97, sz(fresh) - 16).getType() == org.bukkit.Material.VAULT,
                "the accepted corner generates a whole shrine (altar Lodestone and Vault)");
            call(expansion, "flush");
            String saved = Files.readString(garden.getDataFolder().toPath().resolve("sanctum-tiers.yml"));
            check(saved.contains("accepted: true") && saved.contains("accepted: false"), "accepted and refused corners are both remembered on disk");
        });

        // ---- encounters on real sites ----
        at(1, () -> {
            site6 = site("SANCTUM_DARK", 30000, 30000, 6);
            site7 = site("SANCTUM_ASTRAL", 30400, 30000, 7);
            site8 = site("SANCTUM_TIME", 30800, 30000, 8);
            for (Object s : List.of(site6, site7, site8)) for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++)
                world.getChunkAt((sx(s) >> 4) + dx, (sz(s) >> 4) + dz).addPluginChunkTicket(this);
            player = fake(world, new Location(world, sx(site6) + 0.5, 97, sz(site6) + 0.5));
            check(player.isOnline(), "fake player online");
        });
        at(5, () -> {
            encounter = encounter(site6);
            Map<UUID, Object> mobs = (Map<UUID, Object>) field(encounter, "mobs");
            Set<EntityType> types = new HashSet<>();
            for (int wave = 0; wave < 10; wave++) {
                call(dungeons, "startWave", encounter, 2);
            }
            int uniques = ((Set<?>) field(encounter, "uniques")).size();
            boolean undead = false, riders = false;
            for (UUID id : mobs.keySet()) {
                Entity entity = Bukkit.getEntity(id);
                if (!(entity instanceof Mob mob)) continue;
                spawned.add(mob);
                types.add(mob.getType());
                undead |= org.bukkit.Tag.ENTITY_TYPES_UNDEAD.isTagged(mob.getType());
                riders |= mob instanceof Ravager && !mob.getPassengers().isEmpty();
            }
            check(!undead && Set.of(EntityType.VINDICATOR, EntityType.MAGMA_CUBE, EntityType.BLAZE, EntityType.RAVAGER, EntityType.EVOKER).containsAll(types) && types.size() >= 4,
                "Tier VI waves: illagers, blazes, magma cubes, no undead: " + types);
            check(riders, "a Ravager carries an Evoker");
            if (uniques == 0) {
                Class<?> api = Class.forName("com.example.forge.ForgeApi", true, Bukkit.getPluginManager().getPlugin("EvergardenForge").getClass().getClassLoader());
                Mob probe = spawned.getFirst();
                Object direct = api.getMethod("makeElite", LivingEntity.class, String.class).invoke(null, probe, "RARE");
                throw new AssertionError("no uniques; direct makeElite on " + probe.getType() + " = " + direct + " keys " + probe.getPersistentDataContainer().getKeys());
            }
            check(uniques >= 2 && uniques <= mobs.size() * 0.45, "about one in five is a Forge unique: " + uniques + "/" + mobs.size());
            Mob unique = null;
            for (UUID id : (Set<UUID>) field(encounter, "uniques")) if (Bukkit.getEntity(id) instanceof Mob m) unique = m;
            check(unique != null && unique.getPersistentDataContainer().has(new NamespacedKey("evergardenforge", "elite"), PersistentDataType.STRING)
                && net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(unique.customName()).contains("✦"),
                "a unique carries the Forge elite mark and a ✦ grade name");
            Mob vindicator = spawned.stream().filter(m -> m instanceof Vindicator && !((Set<?>) fieldUnchecked(encounter, "uniques")).contains(m.getUniqueId())).findFirst().orElse(null);
            check(vindicator != null && Math.abs(vindicator.getAttribute(Attribute.MAX_HEALTH).getBaseValue() - Math.round(150 * 1.2)) < 1.01,
                "a Tier VI vindicator has Tier V minion health x1.2: " + (vindicator == null ? "-" : vindicator.getAttribute(Attribute.MAX_HEALTH).getBaseValue()));
            Mob blaze = spawned.stream().filter(m -> m instanceof Blaze).findFirst().orElse(null);
            before = vindicator.getHealth();
            vindicator.damage(8, blaze != null ? blaze : spawned.stream().filter(m -> m != vindicator).findFirst().orElseThrow());
            check(vindicator.getHealth() == before, "encounter mobs cannot hurt each other");
        });
        at(1, () -> {
            Mob target = spawned.stream().filter(m -> m instanceof Vindicator && m.isValid()).findFirst().orElseThrow();
            target.setNoDamageTicks(0);
            before = target.getHealth();
            target.damage(10, player);
            check(Math.abs(before - target.getHealth() - 10) < 0.6, "Tier VI takes melee in full: " + (before - target.getHealth()));
            call(dungeons, "remove", encounter);
            ((Map<String, Object>) field(dungeons, "active")).remove(call(site6, "id"));
        });

        // ---- Tier VII: melee resisted, two bosses ----
        at(2, () -> {
            player.teleport(new Location(world, sx(site7) + 0.5, 97, sz(site7) + 0.5));
            encounter = encounter(site7);
            call(dungeons, "startWave", encounter, 1);
            Mob mob = firstMob(encounter);
            mob.setNoDamageTicks(0);
            before = mob.getHealth();
            mob.damage(10, player);
            double melee = before - mob.getHealth();
            mob.setNoDamageTicks(0);
            before = mob.getHealth();
            mob.damage(10, DamageSource.builder(DamageType.MAGIC).withCausingEntity(player).withDirectEntity(player).build());
            double magic = before - mob.getHealth();
            check(Math.abs(melee - 3) < 0.5 && magic > 8, "Tier VII: melee 10 -> " + melee + ", spell 10 -> " + magic);
            call(dungeons, "remove", encounter);
            call(dungeons, "spawnBoss", encounter);
            List<UUID> bosses = (List<UUID>) field(encounter, "bosses");
            check(bosses.size() == 2 && bosses.stream().allMatch(id -> Bukkit.getEntity(id) instanceof Mob), "Tier VII summons two bosses");
            Mob boss = (Mob) Bukkit.getEntity(bosses.getFirst());
            Mob twin = (Mob) Bukkit.getEntity(bosses.get(1));
            twin.setNoDamageTicks(0);
            before = twin.getHealth();
            twin.damage(10, DamageSource.builder(DamageType.MAGIC).withCausingEntity(player).withDirectEntity(player).build());
            double plain = before - twin.getHealth();
            var sets = new com.example.advancemagic.item.ArmorSetService(Bukkit.getPluginManager().getPlugin("advance-magic"));
            var seraph = com.example.advancemagic.item.ArmorSetService.ArmorSet.SERAPH;
            player.getInventory().setHelmet(sets.create(seraph, com.example.advancemagic.item.ArmorSetService.Piece.HELM));
            player.getInventory().setChestplate(sets.create(seraph, com.example.advancemagic.item.ArmorSetService.Piece.CHESTPLATE));
            player.getInventory().setLeggings(sets.create(seraph, com.example.advancemagic.item.ArmorSetService.Piece.LEGGINGS));
            player.getInventory().setBoots(sets.create(seraph, com.example.advancemagic.item.ArmorSetService.Piece.BOOTS));
            twin.setNoDamageTicks(0);
            before = twin.getHealth();
            twin.damage(10, DamageSource.builder(DamageType.MAGIC).withCausingEntity(player).withDirectEntity(player).build());
            double withSeraph = before - twin.getHealth();
            player.getInventory().setArmorContents(new ItemStack[4]);
            check(plain > 0 && Math.abs(withSeraph / plain - 2) < 0.05, "full Seraph hits an undead Tier VII boss x2, not x4: " + plain + " -> " + withSeraph);
            double wanted = 740 * 1.26 * 1.6, expected = Math.min(1024, Math.round(wanted));
            Double carried = boss.getPersistentDataContainer().get(new NamespacedKey("voidscape", "sanctum_hp_scale"), PersistentDataType.DOUBLE);
            check(Math.abs(boss.getAttribute(Attribute.MAX_HEALTH).getBaseValue() - expected) < 1.01 && carried != null && Math.abs(carried * expected - wanted) < 2,
                "Tier VII boss health = Tier V x1.26 x1.6 (" + Math.round(wanted) + ", the part above 1024 carried as reduction): "
                + boss.getAttribute(Attribute.MAX_HEALTH).getBaseValue() + " x" + carried);
            boss.setHealth(1);
            boss.damage(1000, player);
            check(((List<?>) field(encounter, "bosses")).size() == 1 && ((Map<?, ?>) field(dungeons, "active")).containsKey(call(site7, "id")),
                "one boss down: the fight goes on until the second falls");
            call(dungeons, "remove", encounter);
            ((Map<String, Object>) field(dungeons, "active")).remove(call(site7, "id"));
        });

        // ---- Tier VIII: arrows and spells resisted, melee in full; the crimson seal ----
        at(2, () -> {
            player.teleport(new Location(world, sx(site8) + 0.5, 97, sz(site8) + 0.5));
            encounter = encounter(site8);
            call(dungeons, "startWave", encounter, 1);
            Mob mob = firstMob(encounter);
            mob.setNoDamageTicks(0);
            before = mob.getHealth();
            mob.damage(10, player);
            double melee = before - mob.getHealth();
            Arrow arrow = world.spawnArrow(mob.getLocation().add(0, 5, 0), new org.bukkit.util.Vector(0, -1, 0), 0.1f, 0);
            arrow.setShooter(player);
            mob.setNoDamageTicks(0);
            before = mob.getHealth();
            mob.damage(10, DamageSource.builder(DamageType.ARROW).withCausingEntity(player).withDirectEntity(arrow).build());
            double ranged = before - mob.getHealth();
            arrow.remove();
            mob.setNoDamageTicks(0);
            before = mob.getHealth();
            mob.damage(10, DamageSource.builder(DamageType.MAGIC).withCausingEntity(player).withDirectEntity(player).build());
            double magic = before - mob.getHealth();
            check(melee > 9 && Math.abs(ranged - 5) < 0.6 && Math.abs(magic - 5) < 0.6, "Tier VIII: melee " + melee + ", arrow " + ranged + ", spell " + magic);
            call(dungeons, "remove", encounter);
            call(dungeons, "spawnBoss", encounter);
            List<UUID> bosses = (List<UUID>) field(encounter, "bosses");
            check(bosses.size() == 2, "Tier VIII has two bosses as well");
            quiet(encounter);
            Mob boss = (Mob) Bukkit.getEntity(bosses.getFirst());
            playerHits.clear();
            player.setHealth(20);
            player.setNoDamageTicks(0);
            call(dungeons, "skillHit", player, 10.0, boss);
            check(playerHits.stream().anyMatch(h -> h.contains("/sonic_boom")),
                "a Tier VIII skill hit lands a second 34% rend past armor and Protection: " + playerHits);
            player.setHealth(20);
            player.setNoDamageTicks(0);
            player.setFoodLevel(10); // no natural regeneration between the burst and the reading
            player.setSaturation(0);
            call(dungeons, "castSeal", encounter, boss, player, 8);
            check(((List<?>) field(encounter, "seals")).size() == 1, "the crimson seal is placed under the player");
        });
        at(50, () -> {
            check(Math.abs(player.getHealth() - 15) < 0.6, "the seal takes a quarter of maximum health: " + player.getHealth()
                + " max " + player.getAttribute(Attribute.MAX_HEALTH).getValue() + " hits " + playerHits);
            check(player.hasPotionEffect(PotionEffectType.WITHER) && player.getPotionEffect(PotionEffectType.WITHER).getAmplifier() == 1
                && player.hasPotionEffect(PotionEffectType.POISON) && player.hasPotionEffect(PotionEffectType.WEAKNESS) && player.hasPotionEffect(PotionEffectType.SLOWNESS),
                "the crimson seal leaves Wither II, Poison, Weakness and Slowness");
            List<UUID> bosses = (List<UUID>) field(encounter, "bosses");
            Mob boss = (Mob) Bukkit.getEntity(bosses.getFirst());
            for (PotionEffectType effect : List.of(PotionEffectType.WITHER, PotionEffectType.POISON, PotionEffectType.WEAKNESS, PotionEffectType.SLOWNESS)) player.removePotionEffect(effect);
            player.setHealth(20);
            call(dungeons, "acheronSnare", encounter, boss, player, List.of(player));
        });
        at(45, () -> {
            check(player.getAttribute(Attribute.MOVEMENT_SPEED).getModifier(new NamespacedKey("voidscape", "sanctum_root")) != null, "Acheron's circle roots the player");
            List<UUID> bosses = (List<UUID>) field(encounter, "bosses");
            Mob boss = (Mob) Bukkit.getEntity(bosses.getFirst());
            call(dungeons, "styxBerserk", encounter, boss, List.of(player), System.currentTimeMillis());
            check(boss.hasPotionEffect(PotionEffectType.SPEED) && (long) ((Map<?, ?>) field(encounter, "berserkUntil")).get(boss.getUniqueId()) > System.currentTimeMillis(),
                "Styx berserk speeds the boss up for eight seconds");
            call(dungeons, "cocytusVolley", encounter, boss, List.of(player));
        });
        at(30, () -> {
            long arrows = world.getEntitiesByClass(Arrow.class).stream().filter(a -> a.getPersistentDataContainer().has(new NamespacedKey("voidscape", "sanctum_volley"))).count();
            check(arrows >= 16 || player.getHealth() < 20, "Cocytus' volley fires rings of arrows: " + arrows);
        });
        at(70, () -> {
            check(player.getAttribute(Attribute.MOVEMENT_SPEED).getModifier(new NamespacedKey("voidscape", "sanctum_root")) == null, "the root wears off");
            call(dungeons, "remove", encounter);
            ((Map<String, Object>) field(dungeons, "active")).remove(call(site8, "id"));
            check(((List<?>) field(encounter, "seals")).isEmpty(), "ending the fight clears its seals");
        });
    }

    private static Object fieldUnchecked(Object target, String name) {
        try { return field(target, name); } catch (Exception e) { throw new RuntimeException(e); }
    }

    /** Bosses stand still and their automatic skills wait, so a manual skill is the only thing hitting the player. */
    @SuppressWarnings("unchecked")
    private void quiet(Object enc) throws Exception {
        long now = System.currentTimeMillis(), later = now + 600_000;
        for (UUID id : (List<UUID>) field(enc, "bosses")) {
            if (Bukkit.getEntity(id) instanceof Mob boss) boss.setAware(false);
            ((Map<UUID, Long>) field(enc, "nextSeal")).put(id, later);
            ((Map<UUID, Long>) field(enc, "nextSpecial")).put(id, later);
        }
        for (String name : List.of("lastSonicBoom", "lastLaser", "lastSkill", "lastHazard")) {
            Field f = enc.getClass().getDeclaredField(name);
            f.setAccessible(true);
            f.setLong(enc, later);
        }
    }

    @SuppressWarnings("unchecked")
    private Object encounter(Object site) throws Exception {
        Constructor<?> constructor = encounterClass.getDeclaredConstructor(siteClass, int.class);
        constructor.setAccessible(true);
        Object enc = constructor.newInstance(site, 5);
        ((Map<String, Object>) field(dungeons, "active")).put((String) call(site, "id"), enc);
        return enc;
    }

    @SuppressWarnings("unchecked")
    private Mob firstMob(Object enc) throws Exception {
        for (UUID id : ((Map<UUID, Object>) field(enc, "mobs")).keySet())
            if (Bukkit.getEntity(id) instanceof Mob mob && !(mob instanceof Ravager) && !((Set<?>) field(enc, "uniques")).contains(id)) return mob;
        throw new AssertionError("no plain mob spawned");
    }

    private static Player fake(World world, Location at) {
        var server = MinecraftServer.getServer();
        var level = ((CraftWorld) world).getHandle();
        var profile = new GameProfile(UUID.randomUUID(), "TierRunner");
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
