import com.example.advancemagic.AdvanceMagicPlugin;
import com.example.advancemagic.api.MagicAffectEvent;
import com.example.advancemagic.spell.Spell;
import com.mojang.authlib.GameProfile;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
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
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.Vector;

import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Test-only actors for a DISPOSABLE Paper 26.2 server. Never shipped in advance-magic. */
public final class LegendarySpellChecks extends JavaPlugin implements Listener {
    private static final double FIXTURE_HEALTH = 1024;
    private static final Spell[] LEGENDARY = {
            Spell.VERDANT_SOVEREIGN, Spell.LUNAR_REQUIEM, Spell.CRIMSON_OBLIVION
    };
    private static final double[] TOTAL_DAMAGE = {775, 760, 800};
    private static final int[] PULSES = {10, 7, 12};
    private static final class TestConnection extends Connection {
        TestConnection() {
            super(PacketFlow.SERVERBOUND);
            channel = new EmbeddedChannel();
            address = new InetSocketAddress("127.0.0.1", 1);
        }
        @Override public void send(Packet<?> packet) {}
        @Override public void send(Packet<?> packet, ChannelFutureListener listener) {}
        @Override public void send(Packet<?> packet, ChannelFutureListener listener, boolean flush) {}
        @Override public boolean isConnected() { return true; }
    }
    private AdvanceMagicPlugin magic;
    private World world;
    private Player caster, opponent;
    private final List<ServerPlayer> actors = new ArrayList<>();
    private Vindicator target;
    private Location targetAnchor;
    private Team team;
    private final List<Double> damage = new ArrayList<>();
    private final Set<UUID> protectedTargets = new HashSet<>();
    private final Set<UUID> mustRemainStill = new HashSet<>();
    private final Map<String, Material> floor = new LinkedHashMap<>();
    private record DamageSample(int age, double amount) {}
    private final Map<UUID, List<DamageSample>> geometryDamage = new LinkedHashMap<>();
    private final Map<UUID, Location> geometryAnchors = new LinkedHashMap<>();
    private final List<LivingEntity> geometryMobs = new ArrayList<>();
    private final Set<UUID> geometryKeepAlive = new HashSet<>();
    private final List<Map<String, Object>> runtimeScenes = new ArrayList<>();
    private int geometryAge = -1;
    private int passed, affectChecks, damageToOpponent, maxDisplays;
    private boolean finished, pinTarget = true;
    private boolean teleportOnAffect, allyDuringDamage, worldChangeDuringDamage, closeDuringDamage;

    @Override public void onEnable() { later(20, this::begin); }
    private void check(boolean value, String label) {
        if (!value) throw new AssertionError(label);
        passed++;
        getLogger().info("PASS " + label);
    }
    private void run(Runnable test) {
        if (finished) return;
        try { test.run(); } catch (Throwable error) { finish(error); }
    }
    private void later(int ticks, Runnable test) {
        Bukkit.getScheduler().runTaskLater(this, () -> run(test), ticks);
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void protect(MagicAffectEvent event) {
        if (event.getTarget() == target && teleportOnAffect) {
            teleportOnAffect = false;
            targetAnchor = new Location(world, 30.5, 100, 30.5);
            target.teleport(targetAnchor);
        }
        if (protectedTargets.contains(event.getTarget().getUniqueId())) {
            affectChecks++;
            event.setCancelled(true);
        }
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void damage(EntityDamageByEntityEvent event) {
        if (event.getEntity() == target) damage.add(event.getDamage());
        if (event.getEntity() == opponent) damageToOpponent++;
        List<DamageSample> samples = geometryDamage.get(event.getEntity().getUniqueId());
        if (samples != null) samples.add(new DamageSample(geometryAge, event.getDamage()));
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void mutateDuringDamage(EntityDamageByEntityEvent event) {
        if (event.getEntity() != target) return;
        if (allyDuringDamage) {
            allyDuringDamage = false;
            team = Bukkit.getScoreboardManager().getMainScoreboard().registerNewTeam("damage-ally-test");
            team.addEntry(caster.getName());
            team.addEntry(target.getUniqueId().toString());
        } else if (worldChangeDuringDamage) {
            worldChangeDuringDamage = false;
            World other = otherWorld();
            targetAnchor = new Location(other, .5, 100, 6.5);
            target.teleport(targetAnchor);
        } else if (closeDuringDamage) {
            closeDuringDamage = false;
            magic.effects().closeOwner(caster.getUniqueId());
        } else return;
        // Remove the earlier, permitted control pulse so the test can detect
        // any new post-damage impulse/potion after the target changes policy.
        for (var potion : target.getActivePotionEffects()) target.removePotionEffect(potion.getType());
        target.setVelocity(new Vector());
        mustRemainStill.add(target.getUniqueId());
    }

    private void begin() {
        magic = (AdvanceMagicPlugin) Bukkit.getPluginManager().getPlugin("advance-magic");
        check(magic != null && magic.isEnabled(), "release plugin boots on Paper 26.2");
        Bukkit.getPluginManager().registerEvents(this, this);
        world = Bukkit.getWorlds().getFirst();
        world.setPVP(true);
        for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++) {
            world.getChunkAt(x, z).setForceLoaded(true);
        }
        for (int x = -52; x <= 52; x++) for (int z = -52; z <= 68; z++) {
            world.getBlockAt(x, 99, z).setType(Material.STONE);
            floor.put(x + ":" + z, Material.STONE);
        }
        caster = actor("LegendaryCaster", new Location(world, .5, 100, .5, 0, 0));
        opponent = actor("LegendaryTarget", new Location(world, 35.5, 100, .5, 0, 0));
        opponent.getAttribute(Attribute.MAX_HEALTH).setBaseValue(FIXTURE_HEALTH);
        opponent.setHealth(FIXTURE_HEALTH);
        caster.setInvulnerable(true);
        magic.getConfig().set("legendary.max-active-per-world", 2);
        magic.getConfig().set("legendary.radius", 14);
        magic.getConfig().set("legendary.visual-scale", 1);
        magic.getConfig().set("legendary.particle-budget-per-frame", 220);
        magic.getConfig().set("pvp", true);
        Bukkit.getScheduler().runTaskTimer(this, () -> run(this::observe), 1, 1);
        check(caster.isOnline() && opponent.isOnline(), "two real server-backed actors are online");
        crafting();
        single(0);
    }

    private Player actor(String name, Location at) {
        var server = MinecraftServer.getServer();
        var level = ((CraftWorld) at.getWorld()).getHandle();
        GameProfile profile = new GameProfile(UUID.randomUUID(), name);
        var handle = new ServerPlayer(server, level, profile, ClientInformation.createDefault());
        handle.connection = new ServerGamePacketListenerImpl(server, new TestConnection(), handle,
                CommonListenerCookie.createInitial(profile, false));
        handle.setPos(at.getX(), at.getY(), at.getZ());
        server.getPlayerList().getPlayers().add(handle);
        server.getPlayerList().getPlayersByUUID().put(profile.id(), handle);
        level.addNewPlayer(handle);
        actors.add(handle);
        Player player = handle.getBukkitEntity();
        player.setGameMode(GameMode.SURVIVAL);
        player.setOp(true);
        player.setGravity(false);
        player.teleport(at);
        return player;
    }

    private void crafting() {
        check(Spell.values().length == 21, "all 21 spell definitions and registry handlers exist");
        check(Spell.mythicCount() == 7, "seven themed mythic cores share the vault tier");
        InventoryView view = caster.openWorkbench(caster.getLocation(), true);
        check(view != null && view.getTopInventory() instanceof CraftingInventory,
                "real workbench view opens for authoritative craft events");
        CraftingInventory inventory = (CraftingInventory) view.getTopInventory();
        for (Spell spell : Spell.values()) {
            ItemStack core = magic.wands().createCore(spell);
            ItemStack wand = magic.wands().create(spell);
            check(magic.wands().coreSpell(core) == spell && magic.wands().spell(wand) == spell,
                    "core and wand PDC routes " + spell.id());
            check(core.getItemMeta().getCustomModelDataComponent().getStrings()
                            .equals(List.of("advance_magic:core_" + spell.id()))
                            && wand.getItemMeta().getCustomModelDataComponent().getStrings()
                            .equals(List.of("advance_magic:" + spell.id())),
                    "core/wand resource selectors " + spell.id());
            Recipe recipe = Bukkit.getRecipe(new NamespacedKey(magic, spell.id() + "_ni_c"));
            check(recipe instanceof ShapedRecipe, "registered shaped recipe " + spell.id());
            ItemStack[] matrix = new ItemStack[9];
            for (int index = 0; index < 9; index++) {
                matrix[index] = index == 4 ? core.clone()
                        : new ItemStack(index % 2 == 0 ? Material.NETHERITE_INGOT : Material.NETHER_STAR);
            }
            inventory.setMatrix(matrix);
            Bukkit.getPluginManager().callEvent(new PrepareItemCraftEvent(inventory, view, false));
            check(magic.wands().spell(inventory.getResult()) == spell,
                    "mixed premium recipe prepares correct wand " + spell.id());
            // Feed a different recipe ID, as a Bedrock collision does, and prove
            // the tagged core remains the authority over the crafted output.
            Recipe wrong = Bukkit.getRecipe(new NamespacedKey(magic,
                    (spell == Spell.LIGHTNING_STRIKE ? Spell.FROST_NOVA : Spell.LIGHTNING_STRIKE).id() + "_ni_c"));
            CraftItemEvent craft = new CraftItemEvent(wrong, view, InventoryType.SlotType.RESULT,
                    0, ClickType.LEFT, InventoryAction.PICKUP_ALL);
            Bukkit.getPluginManager().callEvent(craft);
            check(!craft.isCancelled() && magic.wands().spell(craft.getCurrentItem()) == spell,
                    "core overrides collided client recipe " + spell.id());
            matrix[4] = new ItemStack(Material.NETHER_STAR);
            matrix[7] = core.clone();
            check(magic.wands().craftingSpell(matrix) == spell, "bottom-center core recipe " + spell.id());
            matrix[7] = new ItemStack(Material.HEART_OF_THE_SEA);
            check(magic.wands().craftingSpell(matrix) == null, "untagged vanilla heart cannot forge " + spell.id());
        }
        inventory.clear();
        caster.closeInventory();
    }

    private void observe() {
        List<ItemDisplay> displays = world.getEntitiesByClass(ItemDisplay.class).stream().toList();
        maxDisplays = Math.max(maxDisplays, displays.size());
        if (displays.size() > 144) throw new AssertionError("two cast display cap exceeded: " + displays.size());
        for (ItemDisplay display : displays) {
            Location at = display.getLocation();
            if (display.isPersistent() || display.hasGravity() || !display.isInvulnerable()
                    || at.getY() < world.getMinHeight() || at.getY() >= world.getMaxHeight()) {
                throw new AssertionError("unsafe or untracked cinematic display at " + at);
            }
        }
        for (UUID id : mustRemainStill) {
            Entity entity = Bukkit.getEntity(id);
            if (entity != null && entity.getVelocity().lengthSquared() > .0001) {
                throw new AssertionError("protected/friendly target was displaced: " + id);
            }
        }
        if (pinTarget && target != null && target.isValid() && !target.isDead()) {
            target.teleport(targetAnchor);
            target.setVelocity(new Vector());
        }
        for (var entry : geometryAnchors.entrySet()) {
            Entity entity = Bukkit.getEntity(entry.getKey());
            if (entity != null && entity.isValid() && !entity.isDead()) {
                entity.teleport(entry.getValue());entity.setVelocity(new Vector());
                if (geometryKeepAlive.contains(entity.getUniqueId()) && entity instanceof LivingEntity living)
                    living.setHealth(FIXTURE_HEALTH);
            }
        }
    }

    private void reset() {
        magic.effects().closeOwner(caster.getUniqueId());
        if (opponent != null) magic.effects().closeOwner(opponent.getUniqueId());
        protectedTargets.clear();
        mustRemainStill.clear();
        if (team != null) { team.unregister(); team = null; }
        for (LivingEntity mob : geometryMobs) mob.remove();
        geometryMobs.clear();geometryAnchors.clear();geometryDamage.clear();geometryKeepAlive.clear();geometryAge = -1;
        caster.teleport(new Location(world, .5, 100, .5, 0, 0));
        caster.setVelocity(new Vector());
        caster.getInventory().clear();
        for (var potion : caster.getActivePotionEffects()) caster.removePotionEffect(potion.getType());
        if (opponent != null && !opponent.isDead()) {
            opponent.teleport(new Location(world, 35.5, 100, .5));
            opponent.setVelocity(new Vector());
            for (var potion : opponent.getActivePotionEffects()) opponent.removePotionEffect(potion.getType());
            opponent.setHealth(FIXTURE_HEALTH);
        }
        if (target != null) target.remove();
        targetAnchor = new Location(world, .5, 100, 6.5);
        target = world.spawn(targetAnchor, Vindicator.class, mob -> {
            mob.setAI(false); mob.setGravity(false); mob.setSilent(true); mob.getEquipment().clear();
        });
        target.getAttribute(Attribute.MAX_HEALTH).setBaseValue(FIXTURE_HEALTH);
        target.getAttribute(Attribute.ARMOR).setBaseValue(0);
        target.getAttribute(Attribute.KNOCKBACK_RESISTANCE).setBaseValue(1);
        target.setHealth(FIXTURE_HEALTH);
        target.setMaximumNoDamageTicks(0);
        pinTarget = true;
        damage.clear();
        damageToOpponent = 0;
        affectChecks = 0;
        maxDisplays = 0;
        teleportOnAffect = allyDuringDamage = worldChangeDuringDamage = closeDuringDamage = false;
    }

    private void single(int index) {
        if (index == LEGENDARY.length) { geometry(); eventRevalidation(); return; }
        reset();
        Spell spell = LEGENDARY[index];
        ItemStack wand = magic.wands().create(spell);
        double multiplier = index == 0 ? 1.3 : 1;
        if (index == 0) {
            var meta = wand.getItemMeta();
            meta.getPersistentDataContainer().set(new NamespacedKey(magic, "damage_level"),
                    PersistentDataType.INTEGER, 10);
            wand.setItemMeta(meta);
        }
        caster.getInventory().setItemInMainHand(wand);
        var account = magic.mana().account(caster);
        account.setMaxMana(300);
        account.setMana(300);
        account.restore(spell.id(), 0);
        check(magic.casts().cast(caster, spell, wand), "one player input starts " + spell.id());
        long cooldown = account.end(spell.id());
        check(account.manaExact() == 300 - spell.mana, "mana charged exactly once " + spell.id());
        check(magic.effects().size() == 1, "one effect owns the whole cast " + spell.id());
        check(magic.context().getCastDamageMultiplier(caster.getUniqueId()) == 1,
                "temporary cast multiplier clears immediately " + spell.id());
        later(48, () -> {
            var displays = world.getEntitiesByClass(ItemDisplay.class);
            check(displays.size() >= 3 && displays.size() <= 72, "layered tracked authored art " + spell.id());
            check(displays.stream().allMatch(display -> display.getBrightness() != null
                            && display.getBrightness().getBlockLight() == 15
                            && display.getBrightness().getSkyLight() == 15),
                    "authored art stays full bright " + spell.id());
        });
        later(index == 2 ? 188 : 160, () -> {
            double total = damage.stream().mapToDouble(Double::doubleValue).sum();
            check(damage.size() == PULSES[index], "deterministic opening/finale/echo count " + spell.id());
            check(Math.abs(total - TOTAL_DAMAGE[index] * multiplier) < .001,
                    "exact configured damage with captured power " + spell.id() + " = " + total);
            check(Math.abs((FIXTURE_HEALTH - target.getHealth()) - total) < .01,
                    "real server magic health loss matches damage " + spell.id());
            check(account.end(spell.id()) == cooldown && magic.wands().casts(wand) == 1
                            && magic.wands().usesLeft(wand) == 29,
                    "automatic finale charges no extra cooldown/mastery/durability " + spell.id());
            check(magic.effects().size() == 0 && world.getEntitiesByClass(ItemDisplay.class).isEmpty(),
                    "complete fade removes scope and every temporary display " + spell.id());
            for (var entry : floor.entrySet()) {
                String[] coordinates = entry.getKey().split(":");
                if (world.getBlockAt(Integer.parseInt(coordinates[0]), 99,
                        Integer.parseInt(coordinates[1])).getType() != entry.getValue()) {
                    throw new AssertionError("legendary cast changed floor terrain: " + entry.getKey());
                }
            }
            check(world.getEntitiesByClass(EnderDragon.class).isEmpty(), "dragon remains authored art " + spell.id());
            check(maxDisplays <= 72, "per-cast display count remains bounded " + spell.id());
            single(index + 1);
        });
    }

    private World otherWorld() {
        World other = Bukkit.getWorlds().stream().filter(candidate -> candidate != world).findFirst().orElse(null);
        if (other == null) throw new AssertionError("a second dimension is required for event world-change checks");
        return other;
    }

    private List<ItemDisplay> displays(String model) {
        return world.getEntitiesByClass(ItemDisplay.class).stream().filter(display -> {
            var meta = display.getItemStack().getItemMeta();
            return meta != null && meta.getItemModel() != null
                    && meta.getItemModel().getNamespace().equals("advance_magic")
                    && meta.getItemModel().getKey().equals(model)
                    && display.getTransformation().getScale().lengthSquared() > .001;
        }).toList();
    }

    private Vector displayAxis(ItemDisplay display, int axis) {
        var rotation = display.getTransformation().getLeftRotation();
        var vector = rotation.transform(new org.joml.Vector3f(axis == 0 ? 1 : 0,
                axis == 1 ? 1 : 0, axis == 2 ? 1 : 0));
        return new Vector(vector.x(), vector.y(), vector.z()).normalize();
    }

    /** Record real production display poses, rather than a parallel visual specification. */
    private void exportScene(Spell spell, double visualScale, int age) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (ItemDisplay display : world.getEntitiesByClass(ItemDisplay.class)) {
            var transform = display.getTransformation();
            if (transform.getScale().lengthSquared() < .001) continue;
            Location at = display.getLocation();
            var left = transform.getLeftRotation();var right = transform.getRightRotation();
            var scale = transform.getScale();var translation = transform.getTranslation();
            var meta = display.getItemStack().getItemMeta();
            if (meta == null || meta.getItemModel() == null) continue;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("model", meta.getItemModel().getKey());
            item.put("position", List.of(at.getX(), at.getY(), at.getZ()));
            item.put("yaw", at.getYaw());item.put("pitch", at.getPitch());
            item.put("left_rotation", List.of(left.x(), left.y(), left.z(), left.w()));
            item.put("right_rotation", List.of(right.x(), right.y(), right.z(), right.w()));
            item.put("scale", List.of(scale.x(), scale.y(), scale.z()));
            item.put("translation", List.of(translation.x(), translation.y(), translation.z()));
            item.put("interpolation_ticks", display.getInterpolationDuration());
            items.add(item);
        }
        Location eye = caster.getEyeLocation();
        Map<String, Object> scene = new LinkedHashMap<>();
        scene.put("spell", spell.id());scene.put("visual_scale", visualScale);scene.put("age", age);
        Location body = caster.getLocation();Vector facing = body.getDirection();
        scene.put("caster", List.of(body.getX(), body.getY(), body.getZ()));
        scene.put("facing", List.of(facing.getX(), facing.getY(), facing.getZ()));
        scene.put("camera_position", List.of(eye.getX(), eye.getY(), eye.getZ()));
        scene.put("camera_yaw", eye.getYaw());scene.put("camera_pitch", eye.getPitch());
        scene.put("displays", items);runtimeScenes.add(scene);
        try {
            Files.writeString(Path.of("legendary-runtime-scenes.json"),
                    new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(Map.of(
                            "description", "Actual Paper ItemDisplay states captured from production spell frames; software previews are not client screenshots.",
                            "coordinate_system", "Minecraft world XYZ; quaternion arrays are XYZW; scale and translation are local display transform vectors.",
                            "frames", runtimeScenes)));
        } catch (java.io.IOException error) { throw new RuntimeException(error); }
    }

    private void manualTick(int age) {
        geometryAge = age;
        magic.effects().tick();magic.statuses().tick();observe();
    }

    private LivingEntity geometryMob(Location at) {
        var mob = world.spawn(at, Vindicator.class, entity -> {
            entity.setAI(false);entity.setGravity(false);entity.setSilent(true);entity.getEquipment().clear();
        });
        mob.getAttribute(Attribute.MAX_HEALTH).setBaseValue(FIXTURE_HEALTH);
        mob.getAttribute(Attribute.ARMOR).setBaseValue(0);
        mob.getAttribute(Attribute.KNOCKBACK_RESISTANCE).setBaseValue(1);
        mob.setHealth(FIXTURE_HEALTH);mob.setMaximumNoDamageTicks(0);
        geometryMobs.add(mob);geometryAnchors.put(mob.getUniqueId(), at.clone());
        geometryDamage.put(mob.getUniqueId(), new ArrayList<>());
        return mob;
    }

    private void trackMainTarget() {
        geometryDamage.put(target.getUniqueId(), new ArrayList<>());
    }

    private void geometry() {
        for (double visualScale : new double[]{.5, 1, 1.5}) {
            verdantGeometry(visualScale);
            lunarGeometry(visualScale);
        }
        capturedFacing();
        verdantWall();
        verdantWorldBounds();
        crimsonGeometry();
        magic.getConfig().set("legendary.visual-scale", 1);
        check(runtimeScenes.size() >= 26, "actual display scenes exported including sequential Crimson impacts");
    }

    private void verdantGeometry(double visualScale) {
        reset();magic.getConfig().set("legendary.visual-scale", visualScale);
        Location origin = caster.getLocation();
        trackMainTarget();
        LivingEntity behind = geometryMob(new Location(world, .5, 100, -5.5));
        LivingEntity side = geometryMob(new Location(world, 8.5, 100, 6.5));
        LivingEntity sky = geometryMob(new Location(world, .5, 116, 6.5));
        check(magic.spells().cast(caster, Spell.VERDANT_SOVEREIGN), "horizontal Verdant geometry cast scale=" + visualScale);
        Location dragonPose = null;
        List<Double> priorWaveDepths = null;
        for (int age = 0; age <= 156; age++) {
            manualTick(age);
            if (age == 48 || age == 80 || age == 112) {
                exportScene(Spell.VERDANT_SOVEREIGN, visualScale, age);
                var dragon = displays("verdant_dragon");
                check(dragon.size() == 1, "one authored dragon plane rather than crossed copies scale=" + visualScale + " age=" + age);
                ItemDisplay sigil = dragon.getFirst();
                check(sigil.getLocation().getZ() < origin.getZ()
                                && Math.abs(sigil.getLocation().getX() - origin.getX()) < .001,
                        "stationary dragon sigil stays behind the caster age=" + age);
                check(Math.abs(displayAxis(sigil, 2).getY()) < .001,
                        "dragon sigil is upright and visible from the ground age=" + age);
                check(Math.abs(sigil.getTransformation().getScale().x() - 22 * visualScale) < .002
                                && Math.abs(sigil.getLocation().getZ() - origin.getZ() + 7.5) < .001
                                && Math.abs(sigil.getLocation().getY() - origin.getY() - 12 * visualScale) < .001,
                        "large dragon altar stays behind and above the casting camera scale=" + visualScale);
                var heroHalo = displays("verdant_halo").stream().filter(halo ->
                        halo.getLocation().distanceSquared(sigil.getLocation()) < .01).toList();
                check(heroHalo.size() == 1 && heroHalo.getFirst().getTransformation().getScale().x() >
                                sigil.getTransformation().getScale().x(),
                        "one larger halo frames the readable dragon without duplicating its silhouette");
                var floorHalo = displays("verdant_halo").stream().filter(halo ->
                        Math.abs(displayAxis(halo, 2).getY()) > .999).toList();
                check(!floorHalo.isEmpty() && floorHalo.stream().anyMatch(halo ->
                                Math.abs(halo.getTransformation().getScale().x() - 26 * visualScale) < .002),
                        "large outer ground altar anchors the dragon scene");
                var waves = displays("verdant_halo").stream().filter(halo ->
                        Math.abs(displayAxis(halo, 2).getY()) < .001
                                && halo.getLocation().getZ() > origin.getZ() + 4.5).toList();
                check(waves.size() >= 3 && waves.stream().allMatch(halo ->
                                halo.getLocation().getZ() >= origin.getZ() + 12.49),
                        "travelling discharge rings stay far beyond the caster view age=" + age);
                List<Double> waveDepths = waves.stream().map(wave -> wave.getLocation().getZ()).sorted().toList();
                if (priorWaveDepths != null) check(!waveDepths.equals(priorWaveDepths),
                        "discharge rings visibly travel forward between captured frames age=" + age);
                priorWaveDepths = waveDepths;
                if (dragonPose == null) dragonPose = sigil.getLocation();
                else check(sigil.getLocation().distanceSquared(dragonPose) < .0001,
                        "dragon plane does not orbit or fall from the sky age=" + age);
                var rays = displays("verdant_ray");
                check(displays("verdant_crown").isEmpty(), "no decorative crown covers the caster view");
                check(displays("verdant_beam_core").size() == 1, "far light cap makes the beam visible without a near camera disc");
                check(displays("verdant_beam_core").stream().allMatch(core -> core.getLocation().distanceSquared(origin) > 25),
                        "no light cap sits near the casting camera");
                check(!rays.isEmpty(), "large front-facing Verdant breath is visible age=" + age);
                for (ItemDisplay ray : rays) {
                    Vector axis = displayAxis(ray, 1);
                    check(Math.abs(axis.getY()) < .001 && axis.getZ() > .999,
                            "Verdant beam axis runs horizontally forward age=" + age);
                    var scale = ray.getTransformation().getScale();
                    Location from = ray.getLocation().subtract(axis.clone().multiply(scale.y() / 2));
                    Location to = ray.getLocation().add(axis.clone().multiply(scale.y() / 2));
                    check(from.getZ() >= origin.getZ() + 4.49 && to.getZ() > from.getZ() + 30,
                            "beam source is in front of caster and reaches the forward lane age=" + age);
                    check(from.getZ() - origin.getZ() > Math.max(scale.x(), scale.z()) / 2 + .2,
                            "caster eye stays outside the discharge volume even during finale");
                    check(Math.abs(from.getY() - to.getY()) < .001,
                            "beam stays level instead of descending from above age=" + age);
                }
            }
        }
        var hits = geometryDamage.get(target.getUniqueId());
        check(hits.size() == 10 && Math.abs(hits.stream().mapToDouble(DamageSample::amount).sum() - 775) < .001,
                "front enemy receives exact 775 horizontal beam damage scale=" + visualScale);
        for (var excluded : List.of(behind, side, sky)) {
            check(geometryDamage.get(excluded.getUniqueId()).isEmpty() && excluded.getHealth() == FIXTURE_HEALTH,
                    "behind/side/sky enemy stays outside actual beam capsule scale=" + visualScale);
        }
        check(magic.effects().size() == 0 && world.getEntitiesByClass(ItemDisplay.class).isEmpty(),
                "horizontal Verdant geometry cleans every tracked display scale=" + visualScale);
    }

    private void lunarGeometry(double visualScale) {
        reset();magic.getConfig().set("legendary.visual-scale", visualScale);
        trackMainTarget();
        double spacing = Math.max(14 * .9, 10 * visualScale / .8);
        Location middle = targetAnchor.clone();
        var columns = new ArrayList<LivingEntity>();
        var indexes = new ArrayList<Integer>();
        for (int row = 0; row < 4; row++) for (int column = 0; column < 4; column++) {
            int index = row * 4 + column;
            LivingEntity enemy = index == 5 ? target : geometryMob(middle.clone().add((column - 1) * spacing, 0, (row - 1) * spacing));
            columns.add(enemy);indexes.add(index);
        }
        LivingEntity gap = geometryMob(middle.clone().add(spacing / 2, 0, 0));
        Location largeAt = middle.clone().add(spacing / 2, 0, spacing / 2);
        Slime large = world.spawn(largeAt, Slime.class, slime -> {
            slime.setSize(64);slime.setAI(false);slime.setGravity(false);slime.setSilent(true);
        });
        large.getAttribute(Attribute.MAX_HEALTH).setBaseValue(FIXTURE_HEALTH);
        large.setHealth(FIXTURE_HEALTH);large.setMaximumNoDamageTicks(0);
        geometryMobs.add(large);geometryAnchors.put(large.getUniqueId(), largeAt);
        geometryKeepAlive.add(large.getUniqueId());
        geometryDamage.put(large.getUniqueId(), new ArrayList<>());
        check(magic.spells().cast(caster, Spell.LUNAR_REQUIEM), "sixteen-column Lunar geometry cast scale=" + visualScale);
        var visibleColumnPositions = new HashSet<String>();
        for (int age = 0; age <= 156; age++) {
            manualTick(age);
            for (ItemDisplay beam : displays("lunar_ray")) {
                Vector axis = displayAxis(beam, 1);
                check(Math.abs(beam.getTransformation().getScale().x() - 1.1 * visualScale) < .002,
                        "Lunar needles are narrow relative to the large portals");
                if (age % 4 == 0) check(axis.getY() < -.999, "Lunar pillar points downward during its own firing envelope age=" + age);
                visibleColumnPositions.add(Math.round(beam.getLocation().getX() * 100) + ":" + Math.round(beam.getLocation().getZ() * 100));
            }
            if (age == 48 || age == 80 || age == 112) {
                exportScene(Spell.LUNAR_REQUIEM, visualScale, age);
                var rings = displays("lunar_seal").stream().filter(display -> display.getLocation().getY() > middle.getY() + 6).toList();
                check(rings.size() == 16, "sixteen separate primary Lunar sky circles scale=" + visualScale + " age=" + age);
                var halos = displays("lunar_orbit").stream().filter(display ->
                        display.getLocation().getY() > middle.getY() + 6).toList();
                check(halos.size() == 16, "each moon receives its own separate outer halo age=" + age);
                for (ItemDisplay ring : rings) {
                    var size = ring.getTransformation().getScale();
                    check(Math.abs(size.x() - 10 * visualScale) < .002 && Math.max(size.x(), size.y()) <= spacing * .8 + .001,
                            "ring diameter fits its own grid cell scale=" + visualScale);
                    Vector normal = displayAxis(ring, 2);
                    Vector toViewer = caster.getEyeLocation().toVector().subtract(ring.getLocation().toVector()).normalize();
                    check(Math.abs(normal.getY()) > .15 && Math.abs(normal.dot(toViewer)) > .99,
                            "Lunar ring tilt presents its face toward ground viewers scale=" + visualScale);
                    var matching = halos.stream().filter(halo ->
                            halo.getLocation().distanceSquared(ring.getLocation()) < .01).toList();
                    check(matching.size() == 1 && matching.getFirst().getTransformation().getScale().x() > size.x()
                                    && matching.getFirst().getTransformation().getScale().x() < spacing - .1,
                            "outer moon halo fits the same separate sky cell scale=" + visualScale);
                    Vector haloOffset = matching.getFirst().getLocation().toVector().subtract(ring.getLocation().toVector());
                    check(Math.abs(haloOffset.length() - .06) < .001
                                    && haloOffset.dot(toViewer) < -.059,
                            "rotating Lunar engraving sits behind its hero without coplanar flicker scale=" + visualScale);
                }
                var altars = displays("lunar_constellation").stream().filter(display ->
                        display.getTransformation().getScale().x() > 10 * visualScale).toList();
                check(altars.size() == 2 && altars.stream().allMatch(altar ->
                                Math.abs(displayAxis(altar, 2).getY()) > .999
                                        && altar.getLocation().getY() < middle.getY() + 1),
                        "two wide constellation altars stay on the ground beneath the sky formation");
                for (int i = 0; i < rings.size(); i++) for (int j = i + 1; j < rings.size(); j++) {
                    Location a = rings.get(i).getLocation(), b = rings.get(j).getLocation();
                    double distance = Math.hypot(a.getX() - b.getX(), a.getZ() - b.getZ());
                    var as = rings.get(i).getTransformation().getScale();
                    var bs = rings.get(j).getTransformation().getScale();
                    check(distance > (Math.max(as.x(), as.y()) + Math.max(bs.x(), bs.y())) / 2 + .1,
                            "separate Lunar rings never overlap at visual scale=" + visualScale);
                }
                if (age == 112) {
                    check(displays("lunar_ray").size() == 16,
                            "final Lunar strike shows all sixteen downward needles together scale=" + visualScale);
                    var ripples = displays("lunar_constellation").stream().filter(display ->
                            display.getTransformation().getScale().x() <= 7.01 * visualScale).toList();
                    check(ripples.size() == 16 && ripples.stream().allMatch(display ->
                                    display.getLocation().getY() < middle.getY() + 1),
                            "sixteen separate shot ripples mark their own ground impacts");
                }
            }
        }
        check(visibleColumnPositions.size() == 16, "all sixteen Lunar pillars fire across the staggered cycle scale=" + visualScale);
        for (int i = 0; i < columns.size(); i++) {
            List<DamageSample> samples = geometryDamage.get(columns.get(i).getUniqueId());
            int phase = indexes.get(i) % 4 * 4;
            List<Integer> expectedAges = List.of(24 + phase, 44 + phase, 64 + phase, 84 + phase, 112, 124, 136);
            check(samples.stream().map(DamageSample::age).toList().equals(expectedAges),
                    "column " + indexes.get(i) + " affects only its own staggered footprint scale=" + visualScale);
            check(samples.stream().filter(sample -> sample.age() == 112 && sample.amount() == 260).count() == 1,
                    "final sixteen-column blast applies exactly once per target scale=" + visualScale);
            check(Math.abs(samples.stream().mapToDouble(DamageSample::amount).sum() - 760) < .001,
                    "column " + indexes.get(i) + " preserves exact total 760 scale=" + visualScale);
        }
        check(geometryDamage.get(gap.getUniqueId()).isEmpty() && gap.getHealth() == FIXTURE_HEALTH,
                "enemy between Lunar columns is excluded from their actual footprints scale=" + visualScale);
        check(geometryDamage.get(large.getUniqueId()).stream().filter(sample -> sample.age() == 112 && sample.amount() == 260).count() == 1,
                "large body crossing multiple Lunar pillars receives one simultaneous final hit scale=" + visualScale);
        check(magic.effects().size() == 0 && world.getEntitiesByClass(ItemDisplay.class).isEmpty(),
                "sixteen-ring Lunar geometry cleans every tracked display scale=" + visualScale);
    }

    private void crimsonGeometry() {
        reset();magic.getConfig().set("legendary.visual-scale", 1);
        trackMainTarget();
        check(magic.spells().cast(caster, Spell.CRIMSON_OBLIVION), "sequential Crimson fixture starts");
        Map<UUID, Location> waiting = new HashMap<>();
        for (int age = 0; age <= 184; age++) {
            manualTick(age);
            if (age == 48 || age == 96 || age == 104 || age == 128 || age == 160
                    || (age >= 100 && age <= 156 && age % 8 == 4))
                exportScene(Spell.CRIMSON_OBLIVION, 1, age);
            if (age == 96) for (ItemDisplay stone : displays("crimson_shard"))
                waiting.put(stone.getUniqueId(), stone.getLocation());
            if (age >= 96 && age <= 160 && age % 4 == 0) {
                var stones = displays("crimson_shard");
                int consumed = Math.max(0, (age - 96) / 8);
                check(stones.size() == 16 - consumed * 2,
                        "only the arrived Crimson shard disappears age=" + age);
                long movingFaces = stones.stream().filter(stone -> stone.getLocation().distanceSquared(waiting.get(stone.getUniqueId())) > .001).count();
                check(movingFaces == (age % 8 == 4 ? 2 : 0),
                        "one two-face projectile moves while all waiting shards hold position age=" + age);
                check(displays("crimson_ray").size() == (age % 8 == 4 ? 1 : 0),
                        "only the individual travelling shard owns a visible central attack trail age=" + age);
                check(displays("crimson_heart").stream().allMatch(heart ->
                                heart.getLocation().getY() <= targetAnchor.getY() + 2.1
                                        && heart.getTransformation().getScale().x() <= 2.81),
                        "Crimson ending uses compact individual impacts instead of collective collapse age=" + age);
                if (age >= 104) {
                    var shocks = displays("crimson_seal").stream().filter(ring ->
                            ring.getLocation().getY() >= targetAnchor.getY() + .29
                                    && ring.getLocation().getY() <= targetAnchor.getY() + .65).toList();
                    check(shocks.size() == Math.min(3, (age - 104) / 8 + 1)
                                    && shocks.stream().allMatch(ring -> ring.getTransformation().getScale().x() >= 4.99
                                            && Math.abs(displayAxis(ring, 2).getY()) > .999),
                            "individual Crimson impacts preserve up to three fading ground shockwaves age=" + age);
                    if (age >= 108) check(shocks.stream().anyMatch(ring -> ring.getTransformation().getScale().x() > 5.1),
                            "arrived shard shockwaves expand after their own impact age=" + age);
                }
            }
        }
        var samples = geometryDamage.get(target.getUniqueId());
        check(samples.stream().map(DamageSample::age).toList().equals(
                        List.of(40,56,72,88,104,112,120,128,136,144,152,160)),
                "four unchanged opening beats followed by eight separate shard arrivals");
        check(samples.stream().filter(sample -> sample.age() >= 104).allMatch(sample -> Math.abs(sample.amount() - 62.5) < .001),
                "every visible shard impact deals its share of the 500 ending damage");
        check(Math.abs(samples.stream().mapToDouble(DamageSample::amount).sum() - 800) < .001,
                "sequential Crimson preserves the configured total 800");
        check(magic.effects().size() == 0 && world.getEntitiesByClass(ItemDisplay.class).isEmpty(),
                "extended Crimson ending cleans all tracked displays");
    }

    private void verdantWall() {
        reset();magic.getConfig().set("legendary.visual-scale", 1);
        trackMainTarget();
        LivingEntity blocked = geometryMob(new Location(world, .5, 100, 16.5));
        for (int x = -4; x <= 4; x++) for (int y = 100; y <= 108; y++) world.getBlockAt(x, y, 10).setType(Material.STONE);
        try {
            check(magic.spells().cast(caster, Spell.VERDANT_SOVEREIGN), "solid-wall horizontal beam fixture starts");
            for (int age = 0; age <= 156; age++) {
                manualTick(age);
                if (age == 80) {
                    var rays = displays("verdant_ray");
                    check(!rays.isEmpty(), "wall-clipped beam remains visible before the obstacle");
                    for (ItemDisplay ray : rays) {
                        Vector axis = displayAxis(ray, 1);
                        Location end = ray.getLocation().add(axis.multiply(ray.getTransformation().getScale().y() / 2));
                        check(end.getZ() <= 10.01, "rendered breath endpoint stops at the solid wall");
                    }
                }
            }
            check(geometryDamage.get(target.getUniqueId()).size() == 10,
                    "wall keeps the complete beam damage timeline before the obstacle");
            check(geometryDamage.get(blocked.getUniqueId()).isEmpty() && blocked.getHealth() == FIXTURE_HEALTH,
                    "solid wall blocks every horizontal pulse and final hit beyond it");
        } finally {
            for (int x = -4; x <= 4; x++) for (int y = 100; y <= 108; y++) world.getBlockAt(x, y, 10).setType(Material.AIR);
        }
    }

    private void capturedFacing() {
        for (float yaw : new float[]{90, -90}) for (float pitch : new float[]{90, -90}) {
            reset();magic.getConfig().set("legendary.visual-scale", 1);
            caster.teleport(new Location(world, .5, 100, .5, yaw, pitch));
            check(magic.spells().cast(caster, Spell.VERDANT_SOVEREIGN),
                    "vertical pitch still starts a horizontal discharge yaw=" + yaw + " pitch=" + pitch);
            caster.teleport(new Location(world, .5, 100, .5, -yaw, -pitch));
            for (int age = 0; age <= 80; age++) manualTick(age);
            var rays = displays("verdant_ray");
            check(!rays.isEmpty(), "captured horizontal beam remains visible after player turns");
            double expectedX = yaw == 90 ? -1 : 1;
            for (ItemDisplay ray : rays) {
                Vector axis = displayAxis(ray, 1);
                check(Math.abs(axis.getX() - expectedX) < .001 && Math.abs(axis.getY()) < .001 && Math.abs(axis.getZ()) < .001,
                        "beam preserves initial yaw with vertical pitch and later turns yaw=" + yaw + " pitch=" + pitch);
            }
        }
        reset();magic.getConfig().set("legendary.visual-scale", 1);
        check(magic.spells().cast(caster, Spell.LUNAR_REQUIEM), "captured Lunar grid orientation fixture starts");
        for (int age = 0; age <= 48; age++) manualTick(age);
        var before = new HashSet<>(displays("lunar_seal").stream().map(display -> List.of(display.getLocation().getX(),
                display.getLocation().getY(), display.getLocation().getZ())).toList());
        caster.teleport(new Location(world, .5, 100, .5, 180, 45));
        for (int age = 49; age <= 80; age++) manualTick(age);
        var after = new HashSet<>(displays("lunar_seal").stream().map(display -> List.of(display.getLocation().getX(),
                display.getLocation().getY(), display.getLocation().getZ())).toList());
        check(before.equals(after), "turning the player never rotates or moves the captured sixteen-ring grid");
    }

    private void verdantWorldBounds() {
        reset();magic.getConfig().set("legendary.visual-scale", 1);
        var border = world.getWorldBorder();
        Location previousCenter = border.getCenter();double previousSize = border.getSize();
        border.setCenter(.5, .5);border.setSize(20);
        int loaded = world.getLoadedChunks().length;
        try {
            check(magic.spells().cast(caster, Spell.VERDANT_SOVEREIGN), "bounded horizontal beam fixture starts");
            for (int age = 0; age <= 156; age++) {
                manualTick(age);
                if (age == 80) {
                    for (ItemDisplay ray : displays("verdant_ray")) {
                        Vector axis = displayAxis(ray, 1);
                        Location end = ray.getLocation().add(axis.multiply(ray.getTransformation().getScale().y() / 2));
                        check(end.getZ() < 10.51, "horizontal breath stops inside the configured world bounds");
                    }
                }
            }
            check(world.getLoadedChunks().length == loaded, "bounded horizontal cast loads no additional chunks");
        } finally { border.setCenter(previousCenter.getX(), previousCenter.getZ());border.setSize(previousSize); }
    }

    private void eventRevalidation() {
        reset();
        teleportOnAffect = true;
        mustRemainStill.add(target.getUniqueId());
        check(magic.spells().cast(caster, Spell.VERDANT_SOVEREIGN), "affect-event teleport fixture starts");
        later(32, () -> {
            check(!teleportOnAffect && damage.isEmpty() && target.getHealth() == FIXTURE_HEALTH
                            && target.getActivePotionEffects().isEmpty(),
                    "same-world out-of-radius teleport during affect rejects damage and control");
            reset();
            allyDuringDamage = true;
            check(magic.spells().cast(caster, Spell.VERDANT_SOVEREIGN), "damage-event ally change fixture starts");
            later(32, () -> {
                check(!allyDuringDamage && damage.size() == 1 && damage.getFirst() == 65
                                && target.getActivePotionEffects().isEmpty(),
                        "ally change during damage suppresses post-hit control and later pulses");
                reset();
                worldChangeDuringDamage = true;
                World other = otherWorld();
                check(magic.spells().cast(caster, Spell.VERDANT_SOVEREIGN), "damage-event world change fixture starts");
                later(32, () -> {
                    check(!worldChangeDuringDamage && damage.size() == 1 && target.getWorld() == other
                                    && target.getActivePotionEffects().isEmpty(),
                            "target world change during damage suppresses post-hit impulse and potion");
                    reset();
                    closeDuringDamage = true;
                    check(magic.spells().cast(caster, Spell.VERDANT_SOVEREIGN), "damage-event scope close fixture starts");
                    later(32, () -> {
                        check(!closeDuringDamage && damage.size() == 1 && magic.effects().size() == 0
                                        && world.getEntitiesByClass(ItemDisplay.class).isEmpty()
                                        && target.getActivePotionEffects().isEmpty(),
                                "closing owner scope inside damage leaves no residual post-hit control");
                        later(28, () -> {
                            check(damage.size() == 1, "scope closed during damage never releases queued later pulses");
                            protection();
                        });
                    });
                });
            });
        });
    }

    private void protection() {
        reset();
        protectedTargets.add(target.getUniqueId());
        mustRemainStill.add(target.getUniqueId());
        check(magic.spells().cast(caster, Spell.VERDANT_SOVEREIGN), "protected-area visual cast starts");
        later(104, () -> {
            check(affectChecks > 0 && damage.isEmpty() && target.getHealth() == FIXTURE_HEALTH
                            && target.getActivePotionEffects().isEmpty(),
                    "MagicAffectEvent blocks opening damage, potions and control");
            reset();
            team = Bukkit.getScoreboardManager().getMainScoreboard().registerNewTeam("legendary-test");
            team.addEntry(caster.getName());
            team.addEntry(target.getUniqueId().toString());
            mustRemainStill.add(target.getUniqueId());
            check(magic.spells().cast(caster, Spell.CRIMSON_OBLIVION), "friendly-team visual cast starts");
            later(72, () -> {
                check(damage.isEmpty() && target.getHealth() == FIXTURE_HEALTH && target.getActivePotionEffects().isEmpty(),
                        "scoreboard ally is excluded from all damage and control");
                pvp();
            });
        });
    }

    private void pvp() {
        reset();
        targetAnchor = new Location(world, 30.5, 100, 30.5);
        target.teleport(targetAnchor);
        opponent.teleport(new Location(world, .5, 100, 6.5));
        world.setPVP(false);
        mustRemainStill.add(opponent.getUniqueId());
        check(magic.spells().cast(caster, Spell.LUNAR_REQUIEM), "PvP-disabled visual cast starts");
        later(100, () -> {
            check(damageToOpponent == 0 && opponent.getHealth() == FIXTURE_HEALTH
                            && opponent.getActivePotionEffects().isEmpty(),
                    "world PvP policy blocks lunar damage and lifting");
            world.setPVP(true);
            capacity();
        });
    }

    private void capacity() {
        reset();
        check(magic.spells().cast(caster, Spell.VERDANT_SOVEREIGN)
                        && magic.spells().cast(caster, Spell.LUNAR_REQUIEM),
                "two legendary scopes can coexist in one world");
        ItemStack rejectedWand = magic.wands().create(Spell.CRIMSON_OBLIVION);
        var account = magic.mana().account(caster);
        account.setMana(300);
        account.restore(Spell.CRIMSON_OBLIVION.id(), 0);
        check(!magic.casts().cast(caster, Spell.CRIMSON_OBLIVION, rejectedWand),
                "third cast respects configured per-world limit");
        check(account.manaExact() == 300 && account.end(Spell.CRIMSON_OBLIVION.id()) == 0
                        && magic.wands().usesLeft(rejectedWand) == 30 && magic.wands().casts(rejectedWand) == 0,
                "rejected capacity cast refunds mana/cooldown and consumes no wand use");
        later(52, () -> {
            check(magic.effects().size() == 2 && maxDisplays <= 144,
                    "concurrent scopes keep independent bounded world-space art");
            magic.effects().closeOwner(caster.getUniqueId());
            check(magic.effects().size() == 0 && world.getEntitiesByClass(ItemDisplay.class).isEmpty(),
                    "owner cleanup removes both concurrent casts");
            ceiling();
        });
    }

    private void ceiling() {
        reset();
        int floorY = world.getMaxHeight() - 7;
        world.getBlockAt(0, floorY, 6).setType(Material.STONE);
        world.getBlockAt(0, floorY, 0).setType(Material.STONE);
        targetAnchor = new Location(world, .5, floorY + 1, 6.5);
        target.teleport(targetAnchor);
        caster.teleport(new Location(world, .5, floorY + 1, .5, 0, 0));
        for (Spell spell : LEGENDARY) check(!magic.spells().cast(caster, spell),
                "unsafe height rejects without temporary entities " + spell.id());
        check(magic.effects().size() == 0, "ceiling rejection leaves no effects");
        int safeFloor = world.getMaxHeight() - 18;
        world.getBlockAt(0, safeFloor, 6).setType(Material.STONE);
        world.getBlockAt(0, safeFloor, 0).setType(Material.STONE);
        targetAnchor = new Location(world, .5, safeFloor + 1, 6.5);
        target.teleport(targetAnchor);
        caster.teleport(new Location(world, .5, safeFloor + 1, .5, 0, 0));
        check(magic.spells().cast(caster, Spell.LUNAR_REQUIEM), "vertical art scales under the world ceiling");
        later(36, () -> {
            check(world.getEntitiesByClass(ItemDisplay.class).stream().allMatch(display ->
                            display.getLocation().getY() < world.getMaxHeight()),
                    "scaled moon and orbits stay inside world bounds");
            world.getBlockAt(0, floorY, 6).setType(Material.AIR);
            world.getBlockAt(0, floorY, 0).setType(Material.AIR);
            world.getBlockAt(0, safeFloor, 6).setType(Material.AIR);
            world.getBlockAt(0, safeFloor, 0).setType(Material.AIR);
            cleanupEvents();
        });
    }

    private void cleanupEvents() {
        reset();
        check(magic.spells().cast(caster, Spell.CRIMSON_OBLIVION), "quit cleanup fixture starts");
        later(32, () -> {
            Bukkit.getPluginManager().callEvent(new PlayerQuitEvent(caster, "test-only quit"));
            check(magic.effects().size() == 0 && world.getEntitiesByClass(ItemDisplay.class).isEmpty(),
                    "quit event immediately removes all cinematic displays");
            reset();
            check(magic.spells().cast(caster, Spell.VERDANT_SOVEREIGN), "world unload fixture starts");
            later(32, () -> {
                Bukkit.getPluginManager().callEvent(new WorldUnloadEvent(world));
                check(magic.effects().size() == 0 && world.getEntitiesByClass(ItemDisplay.class).isEmpty(),
                        "world unload hook cancels scopes before displays can persist");
                reset();
                World other = Bukkit.getWorlds().stream().filter(candidate -> candidate != world).findFirst().orElse(null);
                check(other != null, "isolated server has a second dimension for actual world-change check");
                check(magic.spells().cast(caster, Spell.LUNAR_REQUIEM), "world change fixture starts");
                later(32, () -> {
                    caster.teleport(new Location(other, .5, 100, .5));
                    check(magic.effects().size() == 0 && world.getEntitiesByClass(ItemDisplay.class).isEmpty(),
                            "actual caster world change cancels damage and art");
                    reset();
                    opponent.teleport(new Location(world, .5, 100, .5, 0, 0));
                    check(magic.spells().cast(opponent, Spell.CRIMSON_OBLIVION), "death cleanup fixture starts");
                    later(32, () -> {
                        opponent.setHealth(0);
                        later(3, () -> {
                            check(opponent.isDead() && magic.effects().size() == 0
                                            && world.getEntitiesByClass(ItemDisplay.class).isEmpty(),
                                    "actual caster death cancels scope and every owned display");
                            reset();
                            check(magic.spells().cast(caster, Spell.VERDANT_SOVEREIGN), "disable cleanup fixture starts");
                            later(32, () -> {
                                Bukkit.getPluginManager().disablePlugin(magic);
                                check(magic.effects().size() == 0 && world.getEntitiesByClass(ItemDisplay.class).isEmpty(),
                                        "plugin disable removes remaining cinematic entities");
                                finish(null);
                            });
                        });
                    });
                });
            });
        });
    }

    private void finish(Throwable error) {
        if (finished) return;
        finished = true;
        if (error == null) getLogger().info("LEGENDARY COMPLETE: " + passed + " assertions passed");
        else getLogger().log(java.util.logging.Level.SEVERE, "LEGENDARY FAILED after " + passed, error);
        try { Files.writeString(Path.of("legendary-result.txt"), error == null ? "PASS " + passed : "FAIL " + error); }
        catch (Exception ignored) {}
        Bukkit.getScheduler().cancelTasks(this);
        if (team != null) team.unregister();
        if (magic != null) magic.effects().close();
        if (target != null) target.remove();
        for (ServerPlayer handle : actors) {
            MinecraftServer.getServer().getPlayerList().getPlayers().remove(handle);
            MinecraftServer.getServer().getPlayerList().getPlayersByUUID().remove(handle.getUUID());
            handle.discard();
        }
        Bukkit.getScheduler().runTaskLater(this, Bukkit::shutdown, 5);
    }
}
