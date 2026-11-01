package com.example.forge;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Elite monsters: a small share of natural spawns become an elite of a rarity (uncommon..mythic)
 * with a coloured name, an aura and boosted stats. Killed by a player, an elite drops affix gear of
 * its own rarity, maybe a gem and salvage materials. Only NATURAL spawns qualify, so spawners,
 * plugin bosses, sanctuary mobs and pets are never touched.
 */
public final class EliteService implements Listener {
    private final ForgePlugin plugin;
    private final NamespacedKey eliteKey;
    private final Set<UUID> tracked = new HashSet<>();
    private BukkitTask aura;

    public EliteService(ForgePlugin plugin) {
        this.plugin = plugin;
        eliteKey = new NamespacedKey(plugin, "elite");
    }

    public void start() {
        aura = Bukkit.getScheduler().runTaskTimer(plugin, this::aura, 20L, 10L);
        for (World world : Bukkit.getWorlds()) for (LivingEntity entity : world.getLivingEntities()) if (rarity(entity) != null) tracked.add(entity.getUniqueId());
    }

    public void stop() {
        if (aura != null) aura.cancel();
        tracked.clear();
    }

    public Rarity rarity(Entity entity) {
        return Rarity.parse(entity.getPersistentDataContainer().get(eliteKey, PersistentDataType.STRING));
    }

    public int count(World world) {
        int count = 0;
        for (UUID id : tracked) {
            Entity entity = Bukkit.getEntity(id);
            if (entity != null && entity.getWorld().equals(world)) count++;
        }
        return count;
    }

    // ---- spawning ------------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        ForgeSettings settings = plugin.settings();
        LivingEntity entity = event.getEntity();
        double chance = chance(entity.getType(), event.getSpawnReason());
        if (chance <= 0 || settings.eliteBlockedWorlds.contains(entity.getWorld().getName())) return;
        // Fewer elites where many mobs have just died (mob farms).
        chance *= plugin.farmGuard().multiplier(entity.getLocation());
        Map<Rarity, Integer> weights = settings.eliteWeights(entity.getWorld().getEnvironment());
        if (weights == null || ThreadLocalRandom.current().nextDouble() >= chance) return;
        Runnable convert = () -> {
            if (ownedByAnotherPlugin(entity)) return;
            if (count(entity.getWorld()) >= plugin.settings().elitesPerWorld) return;
            Rarity rarity = plugin.affixes().rollRarity(ThreadLocalRandom.current(), weights);
            if (plugin.settings().eliteGrades.containsKey(rarity)) makeElite(entity, rarity);
        };
        if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.NATURAL) {
            // One tick later, so plugins that tag their own natural mobs on spawn (sanctuary habitats) have done so.
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (entity.isValid()) convert.run();
            });
        } else {
            // Structure mobs (mansion evokers, bastion brutes) are added with their chunk: convert them now.
            convert.run();
        }
    }

    /** Elite chance for a mob type and spawn reason: elites.chance x elites.spawn-reasons[reason], 0 when not eligible. */
    public double chance(EntityType type, CreatureSpawnEvent.SpawnReason reason) {
        ForgeSettings settings = plugin.settings();
        if (!settings.elitesEnabled || !settings.eliteTypes.contains(type)) return 0;
        return settings.eliteChance * settings.eliteReasonChance.getOrDefault(reason, 0.0);
    }

    private boolean ownedByAnotherPlugin(Entity entity) {
        for (NamespacedKey key : entity.getPersistentDataContainer().getKeys()) {
            if (plugin.settings().eliteSkipNamespaces.contains(key.getNamespace().toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }

    public boolean makeElite(LivingEntity entity, Rarity rarity) {
        ForgeSettings.EliteGrade grade = plugin.settings().eliteGrades.get(rarity);
        if (grade == null || rarity(entity) != null) return false;
        entity.getPersistentDataContainer().set(eliteKey, PersistentDataType.STRING, rarity.name());
        boost(entity, Attribute.MAX_HEALTH, grade.health() - 1, AttributeModifier.Operation.MULTIPLY_SCALAR_1);
        // Some mobs (the Warden) already hit hard enough: they only gain health.
        if (!plugin.settings().eliteDamageExempt.contains(entity.getType())) {
            boost(entity, Attribute.ATTACK_DAMAGE, grade.damage() - 1, AttributeModifier.Operation.MULTIPLY_SCALAR_1);
        }
        boost(entity, Attribute.MOVEMENT_SPEED, grade.speed(), AttributeModifier.Operation.MULTIPLY_SCALAR_1);
        boost(entity, Attribute.KNOCKBACK_RESISTANCE, grade.knockbackResist(), AttributeModifier.Operation.ADD_NUMBER);
        equip(entity, grade, ThreadLocalRandom.current());
        AttributeInstance health = entity.getAttribute(Attribute.MAX_HEALTH);
        if (health != null) entity.setHealth(health.getValue());
        entity.customName(Component.text("✦ " + rarity.thai + " · ", rarity.color)
            .append(Component.translatable(entity.getType().translationKey(), rarity.color))
            .decoration(TextDecoration.BOLD, rarity.ordinal() >= Rarity.EPIC.ordinal()));
        entity.setCustomNameVisible(true);
        tracked.add(entity.getUniqueId());
        return true;
    }

    private static final EquipmentSlot[] ARMOR_SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final String[] ARMOR_PIECES = {"HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS"};

    /** One armor family per elite (iron/gold/chain for uncommon-rare, diamond or rare netherite above); never dropped. */
    private void equip(LivingEntity entity, ForgeSettings.EliteGrade grade, Random random) {
        EntityEquipment equipment = entity.getEquipment();
        if (equipment == null || grade.armor().isEmpty()) return;
        String family = grade.armor().get(random.nextInt(grade.armor().size())).toUpperCase(Locale.ROOT);
        for (int i = 0; i < ARMOR_SLOTS.length; i++) {
            if (random.nextDouble() >= grade.armorChance()) continue;
            Material material = Material.matchMaterial(family + "_" + ARMOR_PIECES[i]);
            if (material == null) continue;
            equipment.setItem(ARMOR_SLOTS[i], new ItemStack(material));
            equipment.setDropChance(ARMOR_SLOTS[i], 0f);
        }
    }

    private void boost(LivingEntity entity, Attribute attribute, double amount, AttributeModifier.Operation operation) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance == null || amount == 0) return;
        NamespacedKey key = new NamespacedKey(plugin, "elite_" + attribute.getKey().getKey().replace('.', '_'));
        instance.removeModifier(key);
        instance.addModifier(new AttributeModifier(key, amount, operation));
    }

    /** Admin/test spawn in front of a player. */
    public LivingEntity spawn(Player player, EntityType type, Rarity rarity) {
        Location at = player.getLocation().add(player.getLocation().getDirection().setY(0).normalize().multiply(4));
        if (!(player.getWorld().spawnEntity(at, type, CreatureSpawnEvent.SpawnReason.COMMAND) instanceof LivingEntity entity)) return null;
        makeElite(entity, rarity);
        return entity;
    }

    // ---- aura and tracking ---------------------------------------------------------------------

    private void aura() {
        Iterator<UUID> iterator = tracked.iterator();
        while (iterator.hasNext()) {
            Entity entity = Bukkit.getEntity(iterator.next());
            if (!(entity instanceof LivingEntity living) || !living.isValid()) {
                iterator.remove();
                continue;
            }
            Rarity rarity = rarity(living);
            if (rarity == null) continue;
            int rgb = rarity.color.value();
            Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(rgb), 1.1f);
            double height = living.getHeight();
            living.getWorld().spawnParticle(Particle.DUST, living.getLocation().add(0, height * 0.5, 0),
                3 + rarity.ordinal() * 2, living.getWidth() * 0.6, height * 0.4, living.getWidth() * 0.6, 0, dust);
        }
    }

    @EventHandler
    public void onLoad(EntitiesLoadEvent event) {
        for (Entity entity : event.getEntities()) if (rarity(entity) != null) tracked.add(entity.getUniqueId());
    }

    @EventHandler
    public void onUnload(EntitiesUnloadEvent event) {
        for (Entity entity : event.getEntities()) tracked.remove(entity.getUniqueId());
    }

    // ---- rewards -------------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        Rarity rarity = rarity(entity);
        if (rarity == null) return;
        tracked.remove(entity.getUniqueId());
        Player killer = entity.getKiller();
        ForgeSettings.EliteGrade grade = plugin.settings().eliteGrades.get(rarity);
        if (killer == null || grade == null) return;
        // Silent: a farmed elite simply drops vanilla loot.
        if (!plugin.farmGuard().rewardable(entity, ThreadLocalRandom.current())) {
            plugin.getLogger().info("[Forge-Elite] farm-guard withheld rewards killer=" + killer.getName() + " mob="
                + entity.getType().getKey().getKey() + " rarity=" + rarity + " tainted=" + plugin.farmGuard().tainted(entity));
            return;
        }
        List<ItemStack> rewards = rewards(rarity, ThreadLocalRandom.current(), entity.getWorld().getEnvironment());
        event.getDrops().addAll(rewards);
        event.setDroppedExp(event.getDroppedExp() * grade.xp());
        killer.sendMessage(Component.text("✦ ล้ม Elite เกรด " + rarity.thai + " ได้รับ " + rewards.size() + " รางวัล", rarity.color));
        plugin.getLogger().info("[Forge-Elite] killer=" + killer.getName() + " uuid=" + killer.getUniqueId() + " mob="
            + entity.getType().getKey().getKey() + " rarity=" + rarity + " rewards=" + rewards.size());
    }

    /** Gear of the elite's own rarity (from the world's material table), maybe a gem, and salvage materials. */
    public List<ItemStack> rewards(Rarity rarity, Random random, World.Environment environment) {
        ForgeSettings.EliteGrade grade = plugin.settings().eliteGrades.get(rarity);
        List<ItemStack> rewards = new ArrayList<>();
        if (grade == null) return rewards;
        for (int i = 0; i < grade.gear(); i++) rewards.add(plugin.gear().affixed(rarity, random, environment));
        if (random.nextDouble() < grade.gemChance()) rewards.add(plugin.gems().item(plugin.gems().roll(rarity, random), 1));
        int materials = grade.materialsMin() + random.nextInt(grade.materialsMax() - grade.materialsMin() + 1);
        if (materials > 0) rewards.add(plugin.affixes().material(rarity, materials));
        return rewards;
    }

    // ---- elite arrows and gems from ordinary mobs ------------------------------------------------

    /** Arrow damage is not an attribute, so elite skeletons get their damage multiplier here. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent event) {
        Rarity rarity = rarity(event.getEntity());
        ForgeSettings.EliteGrade grade = rarity == null ? null : plugin.settings().eliteGrades.get(rarity);
        if (grade != null && !plugin.settings().eliteDamageExempt.contains(event.getEntity().getType())
            && event.getProjectile() instanceof AbstractArrow arrow) arrow.setDamage(arrow.getDamage() * grade.damage());
    }

    /** A small chance of a purity I gem from any hostile mob a player kills, except spawner mobs. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onOrdinaryDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (!(entity instanceof Enemy) || rarity(entity) != null || entity.getKiller() == null) return;
        CreatureSpawnEvent.SpawnReason reason = entity.getEntitySpawnReason();
        if (reason == CreatureSpawnEvent.SpawnReason.SPAWNER || reason == CreatureSpawnEvent.SpawnReason.TRIAL_SPAWNER) return;
        ThreadLocalRandom random = ThreadLocalRandom.current();
        if (random.nextDouble() < plugin.settings().gemMobDropChance && plugin.farmGuard().rewardable(entity, random)
            && plugin.farmGuard().takeOrdinaryGem(entity.getKiller())) {
            GemType type = GemType.values()[random.nextInt(GemType.values().length)];
            event.getDrops().add(plugin.gems().item(new GemService.Gem(type, 1), 1));
        }
    }
}
