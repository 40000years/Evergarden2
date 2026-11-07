import com.example.forge.ForgePlugin;
import com.example.forge.Rarity;
import io.github.arcaneplugins.levelledmobs.LevelInterface2;
import io.github.arcaneplugins.levelledmobs.LevelledMobs;
import io.github.arcaneplugins.levelledmobs.events.MobPreLevelEvent;
import io.github.arcaneplugins.levelledmobs.events.SummonedMobPreLevelEvent;
import io.github.arcaneplugins.levelledmobs.misc.NamespacedKeys;
import io.github.arcaneplugins.levelledmobs.result.AdditionalLevelInformation;
import io.github.arcaneplugins.levelledmobs.wrappers.LivingEntityWrapper;
import org.bukkit.Bukkit;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Slime;
import org.bukkit.entity.MagmaCube;
import org.bukkit.entity.Phantom;
import org.bukkit.entity.Hoglin;
import org.bukkit.entity.Zoglin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** Regression checks against the real optional LevelledMobs plugin, not an event fixture. */
public final class ForgeLevelledMobsProbe extends JavaPlugin implements Listener {
    private final List<String> passed = new ArrayList<>();
    private final Map<UUID, Integer> blocked = new ConcurrentHashMap<>();
    private final List<Variant> variants = new ArrayList<>();
    private final List<String> variantFailures = new ArrayList<>();
    private ForgePlugin forge;
    private LevelInterface2 levels;
    private World world;
    private Enderman fresh;
    private Enderman normal;
    private Enderman converting;
    private Enderman legacy;
    private Enderman fullNerfed;
    private double baseHealth;
    private double baseDamage;
    private double followRange;
    private double recoveredLegacyHealth;
    private boolean finished;
    private final NamespacedKey thirdParty = new NamespacedKey("otherplugin", "retained_follow_range");
    private final NamespacedKey foreignData = new NamespacedKey("otherplugin", "retained_data");

    @Override public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        later(40, this::begin);
        later(500, () -> { throw new AssertionError("LevelledMobs probe timed out"); });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void preLevel(MobPreLevelEvent event) {
        if (event.isCancelled()) blocked.merge(event.getEntity().getUniqueId(), 1, Integer::sum);
    }

    private void later(long ticks, Runnable action) {
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (finished) return;
            try { action.run(); }
            catch (Throwable error) {
                StringWriter detail = new StringWriter();
                error.printStackTrace(new PrintWriter(detail));
                finish("FAIL " + detail + "\nPassed before failure:\n" + String.join("\n", passed));
            }
        }, ticks);
    }

    private void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        passed.add(message);
    }

    private void near(double actual, double expected, String message) {
        check(Math.abs(actual - expected) < 1e-6, message + ": " + actual + " (expected " + expected + ")");
    }

    private void begin() {
        forge = (ForgePlugin) Bukkit.getPluginManager().getPlugin("EvergardenForge");
        LevelledMobs lm = (LevelledMobs) Bukkit.getPluginManager().getPlugin("LevelledMobs");
        check(forge != null && forge.isEnabled(), "Forge enabled with optional LM installed");
        check(lm != null && lm.isEnabled(), "real LevelledMobs " + lm.getPluginMeta().getVersion() + " enabled");
        levels = lm.getLevelInterface();
        world = Bukkit.getWorlds().getFirst();
        world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        world.setStorm(false);
        world.setTime(6000);
        fresh = spawn(0);
        normal = spawn(4);
        converting = spawn(8);
        legacy = spawn(12);
        fullNerfed = spawn(16);
        variant(spawn(24, Slime.class, entity -> entity.setSize(4)), "large Slime size 4");
        variant(spawn(32, MagmaCube.class, entity -> entity.setSize(4)), "large MagmaCube size 4");
        variant(spawn(40, Phantom.class, entity -> entity.setSize(3)), "Phantom size 3");
        variant(spawn(48, Hoglin.class, entity -> {
            entity.setBaby();
            entity.setImmuneToZombification(true);
        }), "baby Hoglin");
        variant(spawn(56, Zoglin.class, entity -> entity.setBaby()), "baby Zoglin");
        baseHealth = fresh.getAttribute(Attribute.MAX_HEALTH).getBaseValue();
        baseDamage = fresh.getAttribute(Attribute.ATTACK_DAMAGE).getBaseValue();
        near(baseHealth, 40, "vanilla Enderman base health");
        check(forge.elites().makeElite(fresh, Rarity.MYTHIC), "fresh Enderman becomes Divine");
        divine(fresh, "fresh Divine");
        check(!forge.elites().makeElite(fresh, Rarity.MYTHIC), "repeated conversion is rejected");
        divine(fresh, "repeated conversion leaves Divine unchanged");

        MobPreLevelEvent eliteEvent = event(fresh);
        Bukkit.getPluginManager().callEvent(eliteEvent);
        check(eliteEvent.isCancelled(), "LM pre-level event cancelled for Forge elite");
        MobPreLevelEvent normalEvent = event(normal);
        Bukkit.getPluginManager().callEvent(normalEvent);
        check(!normalEvent.isCancelled(), "LM pre-level event remains allowed for ordinary mobs");
        SummonedMobPreLevelEvent summonedElite = new SummonedMobPreLevelEvent(fresh, 1);
        Bukkit.getPluginManager().callEvent(summonedElite);
        check(summonedElite.isCancelled(), "LM summoned pre-level event cancelled for Forge elite");
        SummonedMobPreLevelEvent summonedNormal = new SummonedMobPreLevelEvent(normal, 1);
        Bukkit.getPluginManager().callEvent(summonedNormal);
        check(!summonedNormal.isCancelled(), "LM summoned pre-level event remains allowed for ordinary mobs");

        apply(normal, 1);
        apply(converting, 30);
        apply(legacy, 30);
        apply(fullNerfed, 1);
        apply(fresh, 1);
        for (Variant variant : variants) apply(variant.entity(), 30);
        later(50, this::afterInitialLevelling);
    }

    private Enderman spawn(int offset) {
        return spawn(offset, Enderman.class, entity -> {});
    }

    private <T extends LivingEntity> T spawn(int offset, Class<T> type, Consumer<T> configure) {
        T mob = world.spawn(new Location(world, 640 + offset, 100, 640), type,
            CreatureSpawnEvent.SpawnReason.COMMAND, entity -> {
                entity.setAI(false);
                entity.setGravity(false);
                entity.setPersistent(true);
                entity.setRemoveWhenFarAway(false);
                entity.setInvulnerable(true);
                configure.accept(entity);
            });
        mob.getChunk().setForceLoaded(true);
        return mob;
    }

    private void variant(LivingEntity entity, String name) {
        variants.add(new Variant(entity, entity.getAttribute(Attribute.MAX_HEALTH).getBaseValue(),
            entity.getAttribute(Attribute.ATTACK_DAMAGE).getBaseValue(), name));
    }

    private MobPreLevelEvent event(LivingEntity entity) {
        return new MobPreLevelEvent(entity, 1, MobPreLevelEvent.LevelCause.NORMAL,
            Set.of(AdditionalLevelInformation.NOT_APPLICABLE));
    }

    private void apply(LivingEntity entity, int level) {
        LivingEntityWrapper wrapper = LivingEntityWrapper.Companion.getInstance(entity);
        try {
            // LM deliberately defers this API call from the main thread to its asynchronous worker.
            levels.applyLevelToMob(wrapper, level, false, false,
                Set.of(AdditionalLevelInformation.NOT_APPLICABLE));
        } finally { wrapper.free(); }
    }

    private void afterInitialLevelling() {
        check(levels.isLevelled(normal) && levels.getLevelOfMob(normal) == 1,
            "ordinary Enderman still receives LM level 1");
        check(levels.isLevelled(converting) && levels.getLevelOfMob(converting) == 30,
            "conversion fixture genuinely receives LM level 30");
        check(converting.getAttribute(Attribute.MAX_HEALTH).getValue() > baseHealth,
            "real LM applied a health modifier before Forge conversion");
        check(levels.isLevelled(legacy), "legacy recovery fixture genuinely levelled by LM");
        check(blocked.getOrDefault(fresh.getUniqueId(), 0) >= 2,
            "real asynchronous LM attempt reaches cancelled elite event");
        clean(fresh, "fresh Divine after delayed LM processing");
        divine(fresh, "fresh Divine after delayed LM processing");

        AttributeInstance follow = converting.getAttribute(Attribute.FOLLOW_RANGE);
        follow.addModifier(new AttributeModifier(thirdParty, 3, AttributeModifier.Operation.ADD_NUMBER));
        followRange = follow.getValue();
        converting.getPersistentDataContainer().set(foreignData, PersistentDataType.STRING, "keep-me");
        // Also cover LM base-override rules, which can leave a high-rarity mob at vanilla level 1 stats.
        converting.getAttribute(Attribute.MAX_HEALTH).setBaseValue(1);
        converting.getAttribute(Attribute.ATTACK_DAMAGE).setBaseValue(1);
        check(forge.elites().makeElite(converting, Rarity.MYTHIC), "already levelled mob becomes Divine");
        clean(converting, "converted Divine");
        divine(converting, "converted Divine uses vanilla base rather than LM stats");
        near(converting.getAttribute(Attribute.FOLLOW_RANGE).getValue(), followRange,
            "removing LM preserves another plugin's attribute modifier");
        check("keep-me".equals(converting.getPersistentDataContainer().get(foreignData, PersistentDataType.STRING)),
            "removing LM preserves another plugin's persistent data");
        for (Variant variant : variants) {
            check(levels.isLevelled(variant.entity()), variant.name() + " fixture genuinely levelled by LM");
            variant.entity().getAttribute(Attribute.MAX_HEALTH).setBaseValue(1);
            variant.entity().getAttribute(Attribute.ATTACK_DAMAGE).setBaseValue(1);
            check(forge.elites().makeElite(variant.entity(), Rarity.MYTHIC), variant.name() + " becomes Divine");
            verifyVariant(variant);
        }

        // Represents a saved old elite that LM had reduced or whose Forge modifiers LM removed.
        legacy.getPersistentDataContainer().set(new NamespacedKey(forge, "elite"), PersistentDataType.STRING, "MYTHIC");
        legacy.setHealth(13);
        recoveredLegacyHealth = 13 / legacy.getAttribute(Attribute.MAX_HEALTH).getValue() * baseHealth * 9;
        check(levels.isLevelled(fullNerfed), "full nerfed fixture has real LM state");
        fullNerfed.getPersistentDataContainer().set(new NamespacedKey(forge, "elite"), PersistentDataType.STRING, "MYTHIC");
        for (Attribute attribute : List.of(Attribute.MAX_HEALTH, Attribute.ATTACK_DAMAGE)) {
            AttributeInstance instance = fullNerfed.getAttribute(attribute);
            for (AttributeModifier modifier : List.copyOf(instance.getModifiers())) instance.removeModifier(modifier);
        }
        fullNerfed.getAttribute(Attribute.MAX_HEALTH).setBaseValue(baseHealth);
        fullNerfed.getAttribute(Attribute.ATTACK_DAMAGE).setBaseValue(baseDamage);
        fullNerfed.setHealth(baseHealth);
        forge.elites().onLoad(new EntitiesLoadEvent(legacy.getChunk(), List.of(legacy, fullNerfed)));
        later(30, this::afterRecovery);
    }

    private void afterRecovery() {
        clean(legacy, "loaded legacy Divine");
        divine(legacy, "loaded legacy Divine recovers missing boosts");
        near(legacy.getHealth(), recoveredLegacyHealth, "chunk-load recovery preserves existing health fraction");
        divine(fullNerfed, "full nerfed Divine recovers missing boosts");
        near(fullNerfed.getHealth(), baseHealth * 9, "full nerfed Divine recovers 40/40 to 360/360 health");
        fresh.setHealth(17);
        converting.setHealth(19);
        for (int repeat = 0; repeat < 3; repeat++) {
            forge.elites().onLoad(new EntitiesLoadEvent(legacy.getChunk(), List.of(fresh, converting, legacy, fullNerfed)));
        }
        forge.elites().stop();
        forge.elites().start();
        apply(fresh, 1);
        apply(converting, 1);
        apply(legacy, 1);
        apply(fullNerfed, 1);
        apply(normal, 30);
        later(80, this::afterRepeatedProcessing);
    }

    private void afterRepeatedProcessing() {
        for (Enderman elite : List.of(fresh, converting, legacy, fullNerfed)) {
            clean(elite, "Divine remains excluded after startup/chunk recovery and LM retry");
            divine(elite, "Divine remains x9 with exactly one boost after repeated recovery");
        }
        near(fresh.getHealth(), 17, "repeated recovery never heals fresh elite damage");
        near(converting.getHealth(), 19, "repeated recovery never heals converted elite damage");
        near(legacy.getHealth(), recoveredLegacyHealth, "repeated recovery never heals legacy elite damage");
        near(converting.getAttribute(Attribute.FOLLOW_RANGE).getValue(), followRange,
            "unrelated attribute modifier survives repeated recovery");
        check(levels.isLevelled(normal) && levels.getLevelOfMob(normal) == 30,
            "ordinary mobs still relevel normally after elite recovery");
        for (Variant variant : variants) verifyVariant(variant);

        // An LM worker may already have queued base-value writes before Forge removes the level.
        // No LM PDC level or modifier remains to identify this later write, so native-stat ownership
        // must persist after the initial cleanup. Recovery runs through the real repeating aura.
        clean(converting, "before simulated late LM base-only write");
        converting.getAttribute(Attribute.MAX_HEALTH).setBaseValue(1);
        converting.getAttribute(Attribute.ATTACK_DAMAGE).setBaseValue(1);
        converting.setHealth(3);
        near(converting.getAttribute(Attribute.MAX_HEALTH).getValue(), 9,
            "late LM write temporarily reduces already converted Divine to 9 max health");
        later(30, this::afterLateBaseWrite);
    }

    private void afterLateBaseWrite() {
        clean(converting, "Divine after late LM base-only write without level marker");
        divine(converting, "aura repairs late LM base-only write without level marker");
        near(converting.getHealth(), 120, "late base-only recovery preserves one-third health fraction");
        later(30, () -> {
            near(converting.getHealth(), 120, "later aura passes do not heal repaired elite");
            check(variantFailures.isEmpty(), "all mob variants preserved native bases: " + variantFailures);
            finish("PASS " + passed.size() + " real LevelledMobs compatibility checks\n" + String.join("\n", passed) + "\n");
        });
    }

    private void verifyVariant(Variant variant) {
        try { variantStats(variant); }
        catch (AssertionError error) {
            variantFailures.add(error.getMessage());
            getLogger().severe(error.getMessage());
        }
    }

    private void variantStats(Variant variant) {
        LivingEntity entity = variant.entity();
        clean(entity, variant.name() + " Divine");
        near(entity.getAttribute(Attribute.MAX_HEALTH).getBaseValue(), variant.health(),
            variant.name() + " preserves native max-health base");
        near(entity.getAttribute(Attribute.ATTACK_DAMAGE).getBaseValue(), variant.damage(),
            variant.name() + " preserves native attack-damage base");
        near(entity.getAttribute(Attribute.MAX_HEALTH).getValue(), variant.health() * 9,
            variant.name() + " Divine has x9 native health");
        near(entity.getAttribute(Attribute.ATTACK_DAMAGE).getValue(), variant.damage() * 9,
            variant.name() + " Divine has x9 native damage");
    }

    private record Variant(LivingEntity entity, double health, double damage, String name) {}

    private void clean(LivingEntity entity, String name) {
        check(!levels.isLevelled(entity), name + " has no LM level");
        check(!entity.getPersistentDataContainer().has(NamespacedKeys.INSTANCE.getLevelKey()),
            name + " has no LM level persistent data");
        check(entity.getPersistentDataContainer().getKeys().stream().noneMatch(key ->
                key.getNamespace().equals("levelledmobs") && !Set.of("nolevel", "denylm_nametag").contains(key.getKey())),
            name + " has no cached LM damage, drops or nametag persistent data");
    }

    private void divine(Enderman entity, String name) {
        check(forge.elites().rarity(entity) == Rarity.MYTHIC, name + " retains Divine identity");
        check(entity.customName() != null && PlainTextComponentSerializer.plainText().serialize(entity.customName()).contains("Divine"),
            name + " retains its Divine custom name");
        near(entity.getAttribute(Attribute.MAX_HEALTH).getValue(), baseHealth * 9, name + " has x9 health");
        near(entity.getAttribute(Attribute.ATTACK_DAMAGE).getValue(), baseDamage * 9, name + " has x9 attack damage");
        for (Attribute attribute : List.of(Attribute.MAX_HEALTH, Attribute.ATTACK_DAMAGE,
            Attribute.MOVEMENT_SPEED, Attribute.KNOCKBACK_RESISTANCE)) {
            long count = entity.getAttribute(attribute).getModifiers().stream()
                .filter(modifier -> modifier.getKey().getNamespace().equals("evergardenforge")
                    && modifier.getKey().getKey().startsWith("elite_")).count();
            check(count == 1, name + " has one Forge " + attribute.getKey() + " boost");
        }
    }

    private void finish(String text) {
        if (finished) return;
        finished = true;
        try { Files.writeString(Path.of("forge-levelledmobs-result.txt"), text); }
        catch (Exception error) { getLogger().severe("Cannot record result: " + error); }
        getLogger().info(text.lines().findFirst().orElse(text));
        Bukkit.getScheduler().runTaskLater(this, Bukkit::shutdown, 10L);
    }
}
