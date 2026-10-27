package com.example.voidscape.item;

import com.example.voidscape.VoidscapePlugin;
import com.example.voidscape.world.DungeonLayout;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.random.RandomGenerator;

/**
 * 20 Rare Adventurer Weapons (Sanctum Armoury) with 3D Java models, Bedrock attachables,
 * bow/crossbow draw-stage animations, and ModelEngine .bbmodel blueprints.
 * Tuned above vanilla Netherite and strictly below the 5 Mythic/Legendary weapons.
 */
public final class AdventurerWeaponService implements InventoryHolder, Listener {

    public enum AdventurerWeapon {
        EMBERFANG("emberfang", Material.NETHERITE_SWORD, DungeonLayout.Kind.SANCTUM_DARK,
                "Emberfang", "เขี้ยวถ่านอเวจี", "The Blade of the Ashen Hunt",
                NamedTextColor.GOLD, 9.0, 1.65, 0.0),
        STORMPIERCER("stormpiercer", Material.NETHERITE_SPEAR, DungeonLayout.Kind.SANCTUM_ASTRAL,
                "Stormpiercer", "หอกทะลวงพายุ", "The Lancer of Open Skies",
                NamedTextColor.AQUA, 8.8, 1.35, 1.5),
        IRONBLOOM("ironbloom", Material.MACE, DungeonLayout.Kind.SANCTUM_TIME,
                "Ironbloom", "ขวานพฤกษาเหล็ก", "The Axe of the Wild Forge",
                NamedTextColor.GREEN, 10.5, 1.0, 0.0),
        FROSTVEIL("frostveil", Material.NETHERITE_SWORD, DungeonLayout.Kind.SANCTUM_ASTRAL,
                "Frostveil", "กริชม่านเหมันต์", "The Dagger of Silent Winter",
                NamedTextColor.AQUA, 8.0, 2.05, 0.0),
        SUNFLARE("sunflare", Material.MACE, DungeonLayout.Kind.SANCTUM_TIME,
                "Sunflare", "ลูกตุ้มรัศมีรุ่งอรุณ", "The Morningstar of Dawn",
                NamedTextColor.GOLD, 10.2, 1.1, 0.0),
        MOONRIFT("moonrift", Material.NETHERITE_SWORD, DungeonLayout.Kind.SANCTUM_DARK,
                "Moonrift", "ดาบโค้งผ่าจันทร์", "The Saber of Dusk Trails",
                NamedTextColor.LIGHT_PURPLE, 8.8, 1.75, 0.0),
        BRAMBLEPIKE("bramblepike", Material.NETHERITE_SPEAR, DungeonLayout.Kind.SANCTUM_TIME,
                "Bramblepike", "ทวนหนามพงไพร", "The Halberd of the Green March",
                NamedTextColor.GREEN, 9.2, 1.25, 1.5),
        TIDEHOOK("tidehook", Material.NETHERITE_SPEAR, DungeonLayout.Kind.SANCTUM_ASTRAL,
                "Tidehook", "ตรีศูลเกลียวสมุทร", "The Trident of the Deep Run",
                NamedTextColor.AQUA, 8.8, 1.35, 1.5),
        ASHCLEAVER("ashcleaver", Material.NETHERITE_SWORD, DungeonLayout.Kind.SANCTUM_DARK,
                "Ashcleaver", "ดาบใหญ่ผ่าเถ้าถ่าน", "The Greatsword of Black Trails",
                NamedTextColor.RED, 10.0, 1.35, 0.35),
        GLIMMERMAUL("glimmermaul", Material.MACE, DungeonLayout.Kind.SANCTUM_TIME,
                "Glimmermaul", "ค้อนศึกเหล็กประกายแสง", "The Hammer of Bright Steel",
                NamedTextColor.YELLOW, 11.0, 0.95, 0.0),
        THORNWHISPER("thornwhisper", Material.BOW, DungeonLayout.Kind.SANCTUM_TIME,
                "Thornwhisper", "ธนูกระซิบพงหนาม", "The Bow of Hidden Groves",
                NamedTextColor.GREEN, 0.0, 0.0, 0.0),
        RUNELOCK("runelock", Material.CROSSBOW, DungeonLayout.Kind.SANCTUM_ASTRAL,
                "Runelock", "หน้าไม้สลักอักษรรูน", "The Crossbow of Arcane Hunts",
                NamedTextColor.AQUA, 0.0, 0.0, 0.0),
        MISTCALLER("mistcaller", Material.NETHERITE_SPEAR, DungeonLayout.Kind.SANCTUM_ASTRAL,
                "Mistcaller", "พลองสายหมอกดารา", "The Staff of Veiled Paths",
                NamedTextColor.AQUA, 8.5, 1.45, 1.6),
        SILVERSHIV("silvershiv", Material.NETHERITE_SWORD, DungeonLayout.Kind.SANCTUM_DARK,
                "Silvershiv", "มีดสั้นเงาเงิน", "The Knife of Quiet Steps",
                NamedTextColor.LIGHT_PURPLE, 7.8, 2.15, 0.0),
        STONEHEART("stoneheart", Material.MACE, DungeonLayout.Kind.SANCTUM_DARK,
                "Stoneheart", "กระบองหัวใจศิลา", "The Mace of the Iron Hold",
                NamedTextColor.GOLD, 10.6, 1.0, 0.0),
        NIGHTBLOOM("nightbloom", Material.NETHERITE_SPEAR, DungeonLayout.Kind.SANCTUM_DARK,
                "Nightbloom", "เคียวบุปผารัตติกาล", "The Scythe of Shadow Fields",
                NamedTextColor.LIGHT_PURPLE, 9.2, 1.3, 1.5),
        IRONWAVE("ironwave", Material.NETHERITE_SPEAR, DungeonLayout.Kind.SANCTUM_ASTRAL,
                "Ironwave", "ง้าวคลื่นเหล็กกล้า", "The Glaive of Tidal Steel",
                NamedTextColor.AQUA, 9.0, 1.3, 1.5),
        STARFANG("starfang", Material.NETHERITE_SWORD, DungeonLayout.Kind.SANCTUM_ASTRAL,
                "Starfang", "เรเปียร์เขี้ยวดารา", "The Rapier of Bright Dusk",
                NamedTextColor.YELLOW, 8.5, 1.85, 0.35),
        ROOTSPIRE("rootspire", Material.MACE, DungeonLayout.Kind.SANCTUM_TIME,
                "Rootspire", "กระบองรากไม้โบราณ", "The Club of the Old Warden",
                NamedTextColor.GREEN, 10.8, 0.95, 0.0),
        VOIDREEL("voidreel", Material.NETHERITE_SWORD, DungeonLayout.Kind.SANCTUM_DARK,
                "Voidreel", "กงจักรสายลมรัตติกาล", "The Chakram of Midnight Wind",
                NamedTextColor.DARK_PURPLE, 8.6, 1.9, 0.0);

        private final String id;
        private final Material baseMaterial;
        private final DungeonLayout.Kind sanctum;
        private final String englishName;
        private final String thaiName;
        private final String subtitle;
        private final NamedTextColor color;
        private final double attackDamage;
        private final double attackSpeed;
        private final double bonusReach;

        AdventurerWeapon(String id, Material baseMaterial, DungeonLayout.Kind sanctum,
                         String englishName, String thaiName, String subtitle,
                         NamedTextColor color, double attackDamage, double attackSpeed, double bonusReach) {
            this.id = id;
            this.baseMaterial = baseMaterial;
            this.sanctum = sanctum;
            this.englishName = englishName;
            this.thaiName = thaiName;
            this.subtitle = subtitle;
            this.color = color;
            this.attackDamage = attackDamage;
            this.attackSpeed = attackSpeed;
            this.bonusReach = bonusReach;
        }

        public String id() { return id; }
        public String modelKey() { return "adventurer_" + id; }
        public Material baseMaterial() { return baseMaterial; }
        public DungeonLayout.Kind sanctum() { return sanctum; }
        public String englishName() { return englishName; }
        public String thaiName() { return thaiName; }
        public String subtitle() { return subtitle; }
        public NamedTextColor color() { return color; }
        public double attackDamage() { return attackDamage; }
        public double attackSpeed() { return attackSpeed; }
        public double bonusReach() { return bonusReach; }
        public boolean isRanged() { return baseMaterial == Material.BOW || baseMaterial == Material.CROSSBOW; }
        public static List<AdventurerWeapon> enabledWeapons() {
            return List.of(values());
        }

        public static AdventurerWeapon parse(String raw) {
            if (raw == null || raw.isBlank()) return null;
            String clean = raw.trim().toLowerCase(Locale.ROOT)
                    .replace('-', '_')
                    .replace(" ", "");
            if (clean.startsWith("adventurer_")) clean = clean.substring("adventurer_".length());
            if (clean.startsWith("weapon_")) clean = clean.substring("weapon_".length());
            for (AdventurerWeapon w : values()) {
                if (w.id.equals(clean) || w.id.replace("_", "").equals(clean)
                        || w.englishName.toLowerCase(Locale.ROOT).equals(clean)) {
                    return w;
                }
            }
            return null;
        }
    }

    private static final double RANGED_DAMAGE_MULTIPLIER = 1.25; // +25% projectile damage
    /** Bumped when the item meta (lore, built-in enchants) changes; older copies are refreshed in place. */
    private static final int META_REVISION = 2;
    private static final String LORE_END = "EVERGARDEN · SANCTUM ARMOURY";
    private final VoidscapePlugin plugin;
    private final NamespacedKey weaponKey;
    private final NamespacedKey projectileKey;
    private final NamespacedKey revisionKey;
    private final Map<UUID, Long> reachSwingGate = new ConcurrentHashMap<>();
    private final AdventurerWeaponAbilities abilities;

    public AdventurerWeaponService(VoidscapePlugin plugin) {
        this.plugin = plugin;
        this.weaponKey = plugin.key("adventurer_weapon");
        this.projectileKey = plugin.key("adventurer_projectile");
        this.revisionKey = plugin.key("adventurer_revision");
        this.abilities = new AdventurerWeaponAbilities(plugin, this, projectileKey);
        Bukkit.getPluginManager().registerEvents(abilities, plugin);
        RangedBedrockAnimation.schedule(plugin);
    }

    @Override
    public Inventory getInventory() {
        return null;
    }

    public ItemStack create(AdventurerWeapon weapon) {
        if (weapon == null) return null;
        ItemStack item = new ItemStack(weapon.baseMaterial());
        applyWeaponMeta(item, weapon);
        return item;
    }

    public void applyWeaponMeta(ItemStack item, AdventurerWeapon weapon) {
        if (item == null || weapon == null) return;
        if (item.getType() != weapon.baseMaterial()) {
            item.setType(weapon.baseMaterial());
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        meta.displayName(Component.text("✦ " + weapon.englishName() + " · " + weapon.thaiName(), weapon.color())
                .decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.BOLD, true));

        String sanctumLabel = switch (weapon.sanctum()) {
            case SANCTUM_DARK -> "วิหารรัตติกาล (Dark Sanctum)";
            case SANCTUM_ASTRAL -> "วิหารดารา (Astral Sanctum)";
            case SANCTUM_TIME -> "วิหารกาลเวลา (Time Sanctum)";
        };

        String baseTypeLabel = switch (weapon.baseMaterial()) {
            case NETHERITE_SWORD -> "ดาบนักผจญภัย (Rare Sword)";
            case NETHERITE_SPEAR -> "หอก/อาวุธด้ามยาว (Rare Spear)";
            case MACE -> "ค้อน/ขวานศึก (Rare Heavy Mace)";
            case BOW -> "ธนูโบราณ (Rare Bow)";
            case CROSSBOW -> "หน้าไม้รูน (Rare Crossbow)";
            default -> "อาวุธโบราณ (Rare Weapon)";
        };

        // Lines other systems appended below ours (enchant scrolls, Limit Break) are kept.
        List<Component> appended = new ArrayList<>();
        if (meta.hasLore()) {
            List<Component> old = meta.lore();
            var plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText();
            for (int i = 0; i < old.size(); i++) {
                if (plain.serialize(old.get(i)).equals(LORE_END)) {
                    appended.addAll(old.subList(i + 1, old.size()));
                    break;
                }
            }
        }
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(weapon.subtitle(), NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, true));
        lore.add(Component.text("ประเภท: " + baseTypeLabel, NamedTextColor.DARK_AQUA).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("แหล่งค้นพบ: " + sanctumLabel, NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());

        if (weapon.isRanged()) {
            lore.add(Component.text("✦ อานุภาพลูกศรเพิ่มขึ้น +25% เมื่อยิงเต็มแรง", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("✦ รองรับการง้างสาย 3 ระดับ (Java & Bedrock)", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
        } else {
            lore.add(Component.text(String.format(Locale.US, "✦ พลังโจมตีพื้นฐาน: %.1f หน่วย", weapon.attackDamage()), NamedTextColor.GREEN)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text(String.format(Locale.US, "✦ ความเร็วโจมตี: %.2f ครั้ง/วินาที", weapon.attackSpeed()), NamedTextColor.AQUA)
                    .decoration(TextDecoration.ITALIC, false));
            if (weapon.bonusReach() > 0.0) {
                lore.add(Component.text(String.format(Locale.US, "✦ ระยะโจมตีเพิ่มขึ้น: +%.1f บล็อก (Java & Bedrock)", weapon.bonusReach()), NamedTextColor.GOLD)
                        .decoration(TextDecoration.ITALIC, false));
            }
        }
        List<String> powers = AdventurerWeaponAbilities.LORE.getOrDefault(weapon, List.of());
        if (!powers.isEmpty()) {
            lore.add(Component.empty());
            lore.add(Component.text("ความสามารถพิเศษ", weapon.color()).decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, true));
            for (String line : powers) {
                lore.add(Component.text("◆ " + line, NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
            }
            lore.add(Component.empty());
        }
        lore.add(Component.text("✦ รองรับการตีบวกคัมภีร์ Limit Break และ Unique Enchant", NamedTextColor.LIGHT_PURPLE)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text(LORE_END, NamedTextColor.DARK_PURPLE)
                .decoration(TextDecoration.ITALIC, false));
        lore.addAll(appended);
        meta.lore(lore);

        var cmd = meta.getCustomModelDataComponent();
        cmd.setStrings(List.of("voidscape:" + weapon.modelKey()));
        meta.setCustomModelDataComponent(cmd);
        meta.setItemModel(new NamespacedKey("voidscape", weapon.modelKey()));
        meta.getPersistentDataContainer().set(weaponKey, PersistentDataType.STRING, weapon.id());
        meta.getPersistentDataContainer().set(revisionKey, PersistentDataType.INTEGER, META_REVISION);

        if (!meta.hasEnchant(Enchantment.UNBREAKING)) {
            meta.addEnchant(Enchantment.UNBREAKING, 3, true);
        }
        if (weapon == AdventurerWeapon.THORNWHISPER) {
            // Built in together, unlike vanilla's Infinity/Mending exclusion.
            if (!meta.hasEnchant(Enchantment.INFINITY)) meta.addEnchant(Enchantment.INFINITY, 1, true);
            if (!meta.hasEnchant(Enchantment.MENDING)) meta.addEnchant(Enchantment.MENDING, 1, true);
        }

        if (!weapon.isRanged()) {
            meta.removeAttributeModifier(Attribute.ATTACK_DAMAGE);
            meta.removeAttributeModifier(Attribute.ATTACK_SPEED);
            meta.removeAttributeModifier(Attribute.ENTITY_INTERACTION_RANGE);

            meta.addAttributeModifier(Attribute.ATTACK_DAMAGE, new AttributeModifier(
                    new NamespacedKey("voidscape", weapon.id() + "_damage"),
                    weapon.attackDamage() - 1.0,
                    AttributeModifier.Operation.ADD_NUMBER,
                    EquipmentSlotGroup.MAINHAND));
            meta.addAttributeModifier(Attribute.ATTACK_SPEED, new AttributeModifier(
                    new NamespacedKey("voidscape", weapon.id() + "_speed"),
                    weapon.attackSpeed() - 4.0,
                    AttributeModifier.Operation.ADD_NUMBER,
                    EquipmentSlotGroup.MAINHAND));
            if (weapon.bonusReach() > 0.0) {
                meta.addAttributeModifier(Attribute.ENTITY_INTERACTION_RANGE, new AttributeModifier(
                        new NamespacedKey("voidscape", weapon.id() + "_reach"),
                        weapon.bonusReach(),
                        AttributeModifier.Operation.ADD_NUMBER,
                        EquipmentSlotGroup.MAINHAND));
            }
        }

        item.setItemMeta(meta);
    }

    public AdventurerWeapon identify(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        ItemMeta meta = item.getItemMeta();
        String raw = meta.getPersistentDataContainer().get(weaponKey, PersistentDataType.STRING);
        if (raw != null) return AdventurerWeapon.parse(raw);
        NamespacedKey model = meta.getItemModel();
        if (model != null && "voidscape".equals(model.getNamespace()) && model.getKey().startsWith("adventurer_")) {
            return AdventurerWeapon.parse(model.getKey());
        }
        return null;
    }

    /** True when the item's lore and built-in enchants predate the current weapon powers. */
    boolean outdated(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        Integer revision = item.getItemMeta().getPersistentDataContainer().get(revisionKey, PersistentDataType.INTEGER);
        return revision == null || revision != META_REVISION;
    }

    public void migrateInventory(Inventory inventory) {
        if (inventory == null) return;
        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack item = inventory.getItem(i);
            AdventurerWeapon weapon = identify(item);
            // Copies made while the bow/crossbow were disabled lost their model: restore it.
            if (weapon != null && (item.getType() != weapon.baseMaterial() || item.getItemMeta().getItemModel() == null || outdated(item))) {
                applyWeaponMeta(item, weapon);
                inventory.setItem(i, item);
            }
        }
    }

    /**
     * Picks a Sanctum-themed Rare Adventurer Weapon (72% matching the Sanctum's element, 28% any weapon).
     */
    public AdventurerWeapon pickThematicWeapon(DungeonLayout.Kind kind, RandomGenerator random) {
        AdventurerWeapon[] all = AdventurerWeapon.enabledWeapons().toArray(AdventurerWeapon[]::new);
        if (kind != null && random.nextInt(100) < 72) {
            List<AdventurerWeapon> matching = new ArrayList<>();
            for (AdventurerWeapon w : all) {
                if (w.sanctum() == kind) matching.add(w);
            }
            if (!matching.isEmpty()) {
                return matching.get(random.nextInt(matching.size()));
            }
        }
        return all[random.nextInt(all.length)];
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        migrateInventory(player.getInventory());
        migrateInventory(player.getEnderChest());
    }

    /**
     * Bedrock clients do not always apply Java 1.20.5+ ENTITY_INTERACTION_RANGE to left-click melee packets.
     * Ray-trace extended spear/polearm reach between 3.1 and 3.0 + bonusReach blocks when swinging.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onArmSwing(PlayerAnimationEvent event) {
        if (event.getAnimationType() != PlayerAnimationType.ARM_SWING) return;
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.SPECTATOR) return;
        AdventurerWeapon weapon = identify(player.getInventory().getItemInMainHand());
        if (weapon == null || weapon.bonusReach() <= 0.5) return;

        long now = System.currentTimeMillis();
        Long last = reachSwingGate.get(player.getUniqueId());
        if (last != null && now - last < 320L) return;

        double maxReach = 3.0 + weapon.bonusReach();
        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection();
        RayTraceResult hit = player.getWorld().rayTrace(
                eye, dir, maxReach, FluidCollisionMode.NEVER, true, 0.28,
                entity -> entity instanceof LivingEntity living && !entity.equals(player) && !living.isDead());
        if (hit == null || !(hit.getHitEntity() instanceof LivingEntity target)) return;

        double dist = eye.toVector().distance(hit.getHitPosition());
        if (dist <= 3.15) return; // Vanilla melee packet already handles close range

        reachSwingGate.put(player.getUniqueId(), now);
        target.damage(weapon.attackDamage(), player);
        player.getWorld().spawnParticle(Particle.SWEEP_ATTACK, target.getLocation().add(0, target.getHeight() * 0.5, 0),
                1, 0.05, 0.05, 0.05, 0.0);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.75f, 1.15f);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onShootBow(EntityShootBowEvent event) {
        AdventurerWeapon weapon = identify(event.getBow());
        if (weapon == null || !weapon.isRanged()) return;
        Entity proj = event.getProjectile();
        proj.getPersistentDataContainer().set(projectileKey, PersistentDataType.STRING, weapon.id());
        if (event.getEntity() instanceof Player player) {
            Particle trail = weapon == AdventurerWeapon.THORNWHISPER ? Particle.HAPPY_VILLAGER : Particle.ENCHANT;
            player.getWorld().spawnParticle(trail, proj.getLocation(), 8, 0.12, 0.12, 0.12, 0.02);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onProjectileDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Projectile projectile)) return;
        String id = projectile.getPersistentDataContainer().get(projectileKey, PersistentDataType.STRING);
        if (id == null) return;
        AdventurerWeapon weapon = AdventurerWeapon.parse(id);
        if (weapon == null) return;
        event.setDamage(event.getDamage() * RANGED_DAMAGE_MULTIPLIER);
        abilities.onProjectileHit(event, weapon, projectile);
        Particle hitParticle = weapon == AdventurerWeapon.THORNWHISPER ? Particle.SPORE_BLOSSOM_AIR : Particle.CRIT;
        event.getEntity().getWorld().spawnParticle(hitParticle, event.getEntity().getLocation().add(0, 1.0, 0),
                10, 0.25, 0.25, 0.25, 0.03);
    }

    // =========================================================================
    // Admin Showcase GUI (/evergarden weapons)
    // =========================================================================

    private boolean isAdmin(Player p) {
        return p != null && (p.isOp() || p.hasPermission("evergarden.admin") || p.hasPermission("voidscape.admin"));
    }

    public void openGui(Player player) {
        if (!isAdmin(player)) {
            plugin.message(player, "เมนูนี้สำหรับแอดมินเท่านั้น");
            return;
        }
        Inventory inv = Bukkit.createInventory(this, 54,
                Component.text("✦ คลังอาวุธนักผจญภัย 20 เล่ม (Sanctum Armoury)", NamedTextColor.GOLD, TextDecoration.BOLD));

        AdventurerWeapon[] weapons = AdventurerWeapon.enabledWeapons().toArray(AdventurerWeapon[]::new);
        int[] slots = {
                10, 11, 12, 13, 14, 15, 16,
                19, 20, 21, 22, 23, 24, 25,
                28, 29, 30, 31, 32, 33
        };
        for (int i = 0; i < weapons.length && i < slots.length; i++) {
            AdventurerWeapon w = weapons[i];
            ItemStack display = create(w);
            ItemMeta meta = display.getItemMeta();
            List<Component> lore = meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<>();
            lore.add(Component.empty());
            lore.add(Component.text("▶ คลิกเพื่อรับ " + w.englishName() + " 1 ชิ้น", NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
            display.setItemMeta(meta);
            inv.setItem(slots[i], display);
        }

        // Give All 20 Weapons button
        ItemStack giveAll = new ItemStack(Material.NETHER_STAR);
        ItemMeta allMeta = giveAll.getItemMeta();
        allMeta.displayName(Component.text("✦ รับอาวุธนักผจญภัยครบทั้ง 20 ชิ้น", NamedTextColor.GOLD, TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));
        allMeta.lore(List.of(
                Component.text("คลิกเพื่อเสกอาวุธทั้ง 20 ชิ้นลงกระเป๋า", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("ประกอบด้วย ดาบ 7 · หอก 6 · ค้อน 5 · ธนู 1 · หน้าไม้ 1", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false)
        ));
        giveAll.setItemMeta(allMeta);
        inv.setItem(49, giveAll);

        // Back button to AdminTestGui
        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta backMeta = back.getItemMeta();
        backMeta.displayName(Component.text("← กลับสู่เมนู Evergarden Test Kit", NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false));
        back.setItemMeta(backMeta);
        inv.setItem(45, back);

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.8f, 1.2f);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof AdventurerWeaponService)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !isAdmin(player)) return;
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 54) return;

        if (slot == 45) {
            if (plugin.testGui() != null) plugin.testGui().open(player);
            return;
        }
        if (slot == 49) {
        for (AdventurerWeapon w : AdventurerWeapon.enabledWeapons()) {
                giveOrDrop(player, create(w));
            }
            plugin.message(player, "ได้รับอาวุธนักผจญภัยครบทั้ง 20 ชิ้นแล้ว!");
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.15f);
            return;
        }

        ItemStack clicked = event.getCurrentItem();
        AdventurerWeapon weapon = identify(clicked);
        if (weapon != null) {
            giveOrDrop(player, create(weapon));
            plugin.message(player, "ได้รับ " + weapon.englishName() + " (" + weapon.thaiName() + ") แล้ว");
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.85f, 1.2f);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof AdventurerWeaponService) {
            event.setCancelled(true);
        }
    }

    private void giveOrDrop(Player player, ItemStack item) {
        var leftover = player.getInventory().addItem(item);
        if (!leftover.isEmpty()) {
            leftover.values().forEach(drop -> player.getWorld().dropItemNaturally(player.getLocation(), drop));
        }
    }
}
