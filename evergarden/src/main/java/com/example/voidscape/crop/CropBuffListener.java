package com.example.voidscape.crop;

import com.example.advancemagic.api.MagicCastEvent;
import com.example.voidscape.VoidscapePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class CropBuffListener implements Listener, AutoCloseable {
    private final VoidscapePlugin plugin;
    private final CropService cropService;
    private final com.example.voidscape.compat.CropMovement movement;
    public com.example.voidscape.compat.CropMovement movement(){return movement;}

    // Buff tracking maps: UUID -> expiration epoch millis
    private final Map<UUID, Long> chronoSurge = new ConcurrentHashMap<>();
    private final Map<UUID, Long> overchargeUntil = new ConcurrentHashMap<>();
    private final Map<UUID, Double> overchargeOriginalMax = new ConcurrentHashMap<>();
    private final Map<UUID, Double> pendingOfflineManaResets = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> arcaneEchoCharges = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastArcaneEcho = new ConcurrentHashMap<>();
    private final Map<UUID, Long> vampiricUntil = new ConcurrentHashMap<>();
    private final Map<UUID, Long> glacialUntil = new ConcurrentHashMap<>();
    private final Map<UUID, Long> chainLightningUntil = new ConcurrentHashMap<>();
    private final Set<UUID> isProcessingChainLightning = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> executionerUntil = new ConcurrentHashMap<>();
    private final Map<UUID, Long> titanUntil = new ConcurrentHashMap<>();
    private final Map<UUID, Long> shreddedTargets = new ConcurrentHashMap<>();
    private final Set<UUID> soulWardActive = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Map<UUID, Long> soulWardCooldown = new ConcurrentHashMap<>();
    private final Map<UUID, Long> kineticSlamUntil = new ConcurrentHashMap<>();
    private final Map<UUID, Long> chameleonUntil = new ConcurrentHashMap<>();
    private final Map<UUID, Long> abyssalBubbleUntil = new ConcurrentHashMap<>();
    private final Map<UUID, Long> abyssalCooldown = new ConcurrentHashMap<>();
    private final Map<UUID, Long> magnetUntil = new ConcurrentHashMap<>();
    private final Map<UUID, Long> oreResonanceUntil = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> floraAuraCharges = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> treeFellerCharges = new ConcurrentHashMap<>();
    private final Set<UUID> fellingPlayers = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Map<UUID, Long> vaultFortuneUntil = new ConcurrentHashMap<>();
    private final Map<UUID, Long> bloodCastUntil = new ConcurrentHashMap<>();
    private final Map<UUID, Long> debuffImmunityUntil = new ConcurrentHashMap<>();
    private final Map<UUID, Long> sniperCastUntil = new ConcurrentHashMap<>();
    private final Map<UUID, Long> mendingNectarUntil = new ConcurrentHashMap<>();
    private final Map<UUID, Long> omniReboundUntil = new ConcurrentHashMap<>();

    // Double jump ground tracking

    // Recall warp channeling tracking
    private final Map<UUID, Location> recallChannelLoc = new ConcurrentHashMap<>();

    public CropBuffListener(VoidscapePlugin plugin, CropService cropService) {
        this.plugin = plugin;
        this.cropService = cropService;
        movement=new com.example.voidscape.compat.CropMovement(plugin);
        plugin.getServer().getPluginManager().registerEvents(movement,plugin);
    }

    public boolean hasVaultFortune(Player p) {
        return vaultFortuneUntil.getOrDefault(p.getUniqueId(), 0L) > System.currentTimeMillis();
    }

    // ==========================================
    // 0. Advance Magic Wand Casting Integration
    // ==========================================
    @EventHandler(priority = EventPriority.NORMAL)
    public void onMagicCast(MagicCastEvent e) {
        Player p = e.getCaster();
        if (p == null) return;
        UUID id = p.getUniqueId();
        long now = System.currentTimeMillis();

        // 1. Chrono Surge: -40% Cooldown
        if (chronoSurge.getOrDefault(id, 0L) > now) {
            e.setCooldownMultiplier(0.60);
        }

        // 2. Blood Cast: cast with HP when out of mana
        if (bloodCastUntil.getOrDefault(id, 0L) > now) {
            e.setBloodCast(true);
        }

        // 3. Sniper Cast: +100% velocity & range
        if (sniperCastUntil.getOrDefault(id, 0L) > now) {
            e.setVelocityMultiplier(2.0);
        }

        // 4. Arcane Echo: extra spell casts with 3s internal cooldown
        int echoCharges = arcaneEchoCharges.getOrDefault(id, 0);
        if (echoCharges > 0) {
            long lastEcho = lastArcaneEcho.getOrDefault(id, 0L);
            if (now - lastEcho >= 3000L) {
                lastArcaneEcho.put(id, now);
                e.setExtraCasts(1);
                int rem = echoCharges - 1;
                if (rem <= 0) arcaneEchoCharges.remove(id);
                else arcaneEchoCharges.put(id, rem);
                p.sendMessage(ChatColor.AQUA + "✦ [Arcane Echo] ร่ายเวทซ้ำสองเท่า! (เหลือ " + rem + " ชาร์จ)");
            }
        }

        // 5. Omni Rebound: elemental explosion on cast
        if (omniReboundUntil.getOrDefault(id, 0L) > now) {
            triggerOmniRebound(p);
        }
    }

    private void triggerOmniRebound(Player p) {
        Location loc = p.getLocation();
        World w = loc.getWorld();
        w.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 0.7f, 1.6f);
        w.playSound(loc, Sound.ITEM_FIRECHARGE_USE, 0.8f, 1.2f);
        w.spawnParticle(Particle.FLAME, loc.clone().add(0, 1, 0), 20, 0.5, 0.5, 0.5, 0.08);
        w.spawnParticle(Particle.SNOWFLAKE, loc.clone().add(0, 1, 0), 20, 0.5, 0.5, 0.5, 0.08);
        w.spawnParticle(Particle.ELECTRIC_SPARK, loc.clone().add(0, 1, 0), 20, 0.5, 0.5, 0.5, 0.12);
        w.spawnParticle(Particle.SPORE_BLOSSOM_AIR, loc.clone().add(0, 1, 0), 15, 0.5, 0.5, 0.5, 0.05);

        for (Entity ent : w.getNearbyEntities(loc, 6, 4, 6)) {
            if (ent instanceof Monster m && !ent.equals(p) && !m.isDead()) {
                m.damage(18.0, p);
                m.setFireTicks(80);
                m.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1));
                Vector diff = m.getLocation().toVector().subtract(loc.toVector());
                if (diff.lengthSquared() > 0.001) {
                    m.setVelocity(diff.normalize().setY(0.4).multiply(1.4));
                }
            }
        }
        p.sendActionBar(Component.text("✦ Omni Rebound: ระเบิดคลื่นมหาธาตุ 18 ดาเมจรอบตัว!", NamedTextColor.GOLD));
    }

    // ==========================================
    // 1. Food Consumption Buff Dispatcher
    // ==========================================
    @EventHandler(priority = EventPriority.LOW)
    public void onCropFoodInteract(PlayerInteractEvent e) {
        ItemStack item = e.getItem();
        if (item == null) return;
        CropType crop = cropService.factory().getFoodType(item);
        if (crop == null) return;

        // Prevent placing sweet berries / carrots on Farmland / Grass / Soil when trying to eat
        if (e.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) {
            e.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
        }

        // Auto-upgrade items in player hand so canAlwaysEat is active even for old items
        var meta = item.getItemMeta();
        if (meta != null) {
            var food = meta.getFood();
            if (!food.canAlwaysEat()) {
                food.setCanAlwaysEat(true);
                meta.setFood(food);
                item.setItemMeta(meta);
                try {
                    item.setData(io.papermc.paper.datacomponent.DataComponentTypes.FOOD,
                            io.papermc.paper.datacomponent.item.FoodProperties.food()
                                    .canAlwaysEat(true)
                                    .nutrition(2)
                                    .saturation(1.0f)
                                    .build());
                    item.setData(io.papermc.paper.datacomponent.DataComponentTypes.CONSUMABLE,
                            io.papermc.paper.datacomponent.item.Consumable.consumable()
                                    .consumeSeconds(1.0f)
                                    .hasConsumeParticles(true)
                                    .build());
                } catch (Throwable ignored) {}
            }
        }

        // Creative mode support: vanilla client does not allow eating food in Creative mode
        Player p = e.getPlayer();
        if (p.getGameMode() == GameMode.CREATIVE &&
                (e.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_AIR || e.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK)) {
            e.setCancelled(true);
            org.bukkit.inventory.EquipmentSlot hand = e.getHand() != null ? e.getHand() : org.bukkit.inventory.EquipmentSlot.HAND;
            PlayerItemConsumeEvent consumeEvent = new PlayerItemConsumeEvent(p, item, hand);
            Bukkit.getPluginManager().callEvent(consumeEvent);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent e) {
        ItemStack item = e.getItem();
        CropType crop = cropService.factory().getFoodType(item);
        if (crop == null) return;

        Player p = e.getPlayer();
        UUID id = p.getUniqueId();
        long now = System.currentTimeMillis();

        // Tier 4 Totem vegetable: one activation per minute. Keep the item when
        // the player attempts to consume it during the cooldown.
        if (crop == CropType.SOUL_WARD_BULB && soulWardCooldown.getOrDefault(id, 0L) > now) {
            e.setCancelled(true);
            long remaining = (soulWardCooldown.get(id) - now + 999L) / 1000L;
            p.sendActionBar(Component.text("โฆ Totem ผักยังติดคูลดาวน์อีก " + remaining + " วินาที", NamedTextColor.RED));
            return;
        }

        switch (crop) {
            // ==========================================
            // Tier 1
            // ==========================================
            case MANA_DEW_BERRY -> {
                addPlayerMana(p, 50.0);
                p.getWorld().playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.8f);
                p.getWorld().spawnParticle(Particle.GLOW, p.getLocation().add(0, 1, 0), 20, 0.3, 0.5, 0.3, 0.05);
                p.sendActionBar(Component.text("✦ บลูเบอร์รี: ฟื้นฟูทันที +50 Mana!", NamedTextColor.AQUA));
            }
            case CHAMELEON_LEAF -> {
                chameleonUntil.put(id, now + 25_000L);
                for (Entity ent : p.getWorld().getNearbyEntities(p.getLocation(), 24, 24, 24)) {
                    if (ent instanceof Mob mob && mob.getTarget() == p) {
                        mob.setTarget(null);
                    }
                }
                p.getWorld().playSound(p.getLocation(), Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 0.8f, 1.2f);
                p.getWorld().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, p.getLocation().add(0, 1, 0), 25, 0.4, 0.6, 0.4, 0.02);
                p.sendActionBar(Component.text("✦ ผักกาดหอม: มอนสเตอร์จะไม่โจมตีก่อน (25 วินาที)", NamedTextColor.GREEN));
            }
            case FAIRY_MUSHROOM -> {
                movement.fairy(p);
            }
            case MAGNETIC_SQUASH -> {
                magnetUntil.put(id, now + 120_000L);
                p.getWorld().playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.8f, 1.4f);
                p.getWorld().spawnParticle(Particle.WAX_ON, p.getLocation().add(0, 1, 0), 20, 0.4, 0.5, 0.4, 0.05);
                p.sendActionBar(Component.text("✦ ฟักทองบัตเตอร์นัต: ดูดไอเทมและ EXP ในระยะ 12 บล็อก (2 นาที)", NamedTextColor.BLUE));
            }
            case MOUNTAIN_WALKER_BAMBOO -> {
                movement.bamboo(p);
            }
            case LUMBERJACK_ACORN -> {
                treeFellerCharges.put(id, treeFellerCharges.getOrDefault(id, 0) + 5);
                p.getWorld().playSound(p.getLocation(), Sound.BLOCK_WOOD_BREAK, 1.0f, 0.8f);
                p.getWorld().spawnParticle(Particle.COMPOSTER, p.getLocation().add(0, 1, 0), 20, 0.4, 0.5, 0.4, 0.05);
                p.sendActionBar(Component.text("✦ เกาลัด: โค่นต้นไม้ทั้งต้นในพริบตา (5 ชาร์จ)", NamedTextColor.GOLD));
            }

            // ==========================================
            // Tier 2
            // ==========================================
            case BLOOD_THORN_TOMATO -> {
                vampiricUntil.put(id, now + 60_000L);
                p.getWorld().playSound(p.getLocation(), Sound.ENTITY_PHANTOM_BITE, 0.8f, 1.2f);
                p.getWorld().spawnParticle(Particle.HEART, p.getLocation().add(0, 1, 0), 10, 0.3, 0.4, 0.3, 0.05);
                p.sendActionBar(Component.text("✦ มะเขือเทศ: ดูดเลือด 20% จากการโจมตี (60 วินาที)", NamedTextColor.RED));
            }
            case FROSTBITE_RADISH -> {
                glacialUntil.put(id, now + 60_000L);
                p.getWorld().playSound(p.getLocation(), Sound.BLOCK_GLASS_BREAK, 0.7f, 1.8f);
                p.getWorld().spawnParticle(Particle.SNOWFLAKE, p.getLocation().add(0, 1, 0), 25, 0.4, 0.5, 0.4, 0.08);
                p.sendActionBar(Component.text("✦ หัวไชเท้า: ทุกการโจมตีแช่แข็งศัตรู (60 วินาที)", NamedTextColor.AQUA));
            }
            case THUNDER_KERNEL_CORN -> {
                chainLightningUntil.put(id, now + 60_000L);
                p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 1200, 1));
                p.getWorld().playSound(p.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.5f, 1.6f);
                p.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, p.getLocation().add(0, 1, 0), 25, 0.4, 0.5, 0.4, 0.1);
                p.sendActionBar(Component.text("✦ ข้าวโพด: Speed II + ชิ่งสายฟ้าใส่ศัตรู 3 ตัว (60 วินาที)", NamedTextColor.YELLOW));
            }
            case REAPERS_GARLIC -> {
                executionerUntil.put(id, now + 60_000L);
                p.getWorld().playSound(p.getLocation(), Sound.ENTITY_WITHER_SHOOT, 0.6f, 1.4f);
                p.getWorld().spawnParticle(Particle.SOUL, p.getLocation().add(0, 1, 0), 20, 0.4, 0.5, 0.4, 0.05);
                p.sendActionBar(Component.text("✦ กระเทียม: ปลิดชีพศัตรูเลือดต่ำกว่า 20% ทันที (60 วินาที)", NamedTextColor.DARK_RED));
            }
            case TITAN_PUMPKIN -> {
                titanUntil.put(id, now + 60_000L);
                p.getWorld().playSound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_NETHERITE, 1.0f, 0.9f);
                p.getWorld().spawnParticle(Particle.CRIT, p.getLocation().add(0, 1, 0), 20, 0.4, 0.5, 0.4, 0.1);
                p.sendActionBar(Component.text("✦ มะเขือม่วง: โจมตีทำลายเกราะศัตรู 25% (60 วินาที)", NamedTextColor.GOLD));
            }
            case KINETIC_PEA_POD -> {
                kineticSlamUntil.put(id, now + 180_000L);
                p.getWorld().playSound(p.getLocation(), Sound.ENTITY_IRON_GOLEM_ATTACK, 0.8f, 1.4f);
                p.getWorld().spawnParticle(Particle.EXPLOSION, p.getLocation().add(0, 0.5, 0), 5, 0.2, 0.2, 0.2, 0.05);
                p.sendActionBar(Component.text("✦ ถั่วลันเตา: ยกเลิกดาเมจตกจากที่สูง & ปลดปล่อย Ground Slam (3 นาที)", NamedTextColor.GREEN));
            }

            // ==========================================
            // Tier 3
            // ==========================================
            case TWILIGHT_GRAPE -> {
                sniperCastUntil.put(id, now + 60_000L);
                p.getWorld().playSound(p.getLocation(), Sound.ENTITY_ARROW_SHOOT, 0.9f, 1.8f);
                p.getWorld().spawnParticle(Particle.WITCH, p.getLocation().add(0, 1, 0), 20, 0.4, 0.5, 0.4, 0.05);
                p.sendActionBar(Component.text("✦ องุ่น: เพิ่มความเร็ว & ระยะยิงเวทมนตร์ +100% (60 วินาที)", NamedTextColor.DARK_PURPLE));
            }
            case VOID_FEATHER_BLOSSOM -> {
                movement.rescue(p);
                p.getWorld().playSound(p.getLocation(), Sound.ENTITY_ALLAY_ITEM_TAKEN, 1.0f, 1.2f);
                p.getWorld().spawnParticle(Particle.PORTAL, p.getLocation().add(0, 1, 0), 25, 0.4, 0.5, 0.4, 0.1);
                p.sendActionBar(Component.text("✦ กะหล่ำปลีม่วง: คุ้มกันการตก Void ดีดตัวลอยขึ้นปลอดภัย (5 นาที)", NamedTextColor.LIGHT_PURPLE));
            }
            case LODESTONE_GOURD -> {
                recallChannelLoc.put(id, p.getLocation().clone());
                p.sendTitle(ChatColor.GOLD + "✦ RECALL WARP ✦", ChatColor.YELLOW + "กำลังวาร์ปกลับจุดเกิด... (อยู่นิ่งๆ 3 วินาที)", 5, 50, 10);
                p.getWorld().playSound(p.getLocation(), Sound.BLOCK_PORTAL_TRIGGER, 0.8f, 1.5f);

                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (!p.isOnline() || p.isDead()) {
                        recallChannelLoc.remove(id);
                        return;
                    }
                    Location start = recallChannelLoc.remove(id);
                    if (start == null || start.distanceSquared(p.getLocation()) > 1.5) {
                        p.sendMessage(ChatColor.RED + "✦ การวาร์ปถูกยกเลิกเนื่องจากมีการเคลื่อนที่!");
                        return;
                    }

                    Location dest = p.getRespawnLocation();
                    if (dest == null) dest = p.getWorld().getSpawnLocation();

                    p.getWorld().spawnParticle(Particle.REVERSE_PORTAL, p.getLocation().add(0, 1, 0), 30, 0.4, 0.6, 0.4, 0.1);
                    p.teleport(dest.clone().add(0.5, 0.1, 0.5));
                    dest.getWorld().playSound(dest, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
                    dest.getWorld().spawnParticle(Particle.PORTAL, dest.clone().add(0, 1, 0), 40, 0.5, 0.8, 0.5, 0.2);
                    p.sendTitle(ChatColor.GREEN + "✦ WARPED HOME ✦", ChatColor.WHITE + "กลับสู่จุดเกิดสำเร็จ!", 5, 40, 10);
                }, 60L);
            }
            case ABYSSAL_KELP -> {
                abyssalBubbleUntil.put(id, now + 120_000L);
                p.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 2400, 1, false, false, true));
                p.addPotionEffect(new PotionEffect(PotionEffectType.WATER_BREATHING, 2400, 0, false, false, true));
                if (p.getFireTicks() > 0) {
                    p.setFireTicks(0);
                }
                p.getWorld().playSound(p.getLocation(), Sound.ITEM_BUCKET_FILL, 0.9f, 1.2f);
                p.getWorld().playSound(p.getLocation(), Sound.BLOCK_CONDUIT_ACTIVATE, 0.8f, 1.4f);
                p.getWorld().spawnParticle(Particle.BUBBLE_POP, p.getLocation().add(0, 1, 0), 30, 0.5, 0.6, 0.5, 0.08);
                p.getWorld().spawnParticle(Particle.SPLASH, p.getLocation().add(0, 0.5, 0), 25, 0.4, 0.4, 0.4, 0.1);
                p.sendActionBar(Component.text("✦ ขึ้นฉ่าย: เกราะฟองสบู่น้ำลึก (Absorption II & สะท้อนคลื่นน้ำ 2 นาที)", NamedTextColor.DARK_AQUA));
            }
            case GLIDER_SPORE -> {
                movement.glide(p);
                p.getWorld().playSound(p.getLocation(), Sound.ENTITY_BAT_TAKEOFF, 0.8f, 0.9f);
                p.getWorld().spawnParticle(Particle.SPORE_BLOSSOM_AIR, p.getLocation().add(0, 1, 0), 25, 0.4, 0.5, 0.4, 0.05);
                p.sendActionBar(Component.text("✦ บรอกโคลี: ย่อตัวกลางอากาศเพื่อกางร่มชูชีพร่อนช้าๆ (3 นาที)", NamedTextColor.WHITE));
            }
            case STAR_ANISE -> {
                debuffImmunityUntil.put(id, now + 120_000L);
                cleanseDebuffs(p);
                p.getWorld().playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.6f);
                p.getWorld().spawnParticle(Particle.WAX_OFF, p.getLocation().add(0, 1, 0), 30, 0.4, 0.6, 0.4, 0.08);
                p.sendActionBar(Component.text("✦ โป๊ยกั๊ก: ล้างดีบัฟทั้งหมด & ป้องกันสถานะผิดปกติ (2 นาที)", NamedTextColor.YELLOW));
            }

            // ==========================================
            // Tier 4
            // ==========================================
            case FORTUNE_BEET -> {
                oreResonanceUntil.put(id, now + 180_000L);
                p.getWorld().playSound(p.getLocation(), Sound.BLOCK_AMETHYST_CLUSTER_STEP, 1.0f, 1.4f);
                p.getWorld().spawnParticle(Particle.SCRAPE, p.getLocation().add(0, 1, 0), 20, 0.4, 0.5, 0.4, 0.1);
                p.sendActionBar(Component.text("✦ เทอร์นิป: +35% โอกาสขุดแร่แล้วดรอปเบิ้ล 2 เท่า (3 นาที)", NamedTextColor.LIGHT_PURPLE));
            }
            case DEMETERS_MELON -> {
                floraAuraCharges.put(id, floraAuraCharges.getOrDefault(id, 0) + 6);
                p.getWorld().playSound(p.getLocation(), Sound.BLOCK_COMPOSTER_READY, 0.9f, 1.2f);
                p.getWorld().spawnParticle(Particle.COMPOSTER, p.getLocation().add(0, 1, 0), 20, 0.4, 0.4, 0.4, 0.05);
                p.sendActionBar(Component.text("✦ มะละกอ: ออร่าเร่งโตพืชผักรอบตัว (6 ชาร์จ)", NamedTextColor.GREEN));
            }
            case PRISM_SHARD_CARROT -> {
                vaultFortuneUntil.put(id, now + 300_000L);
                p.getWorld().playSound(p.getLocation(), Sound.BLOCK_CONDUIT_ATTACK_TARGET, 0.8f, 1.4f);
                p.getWorld().spawnParticle(Particle.NAUTILUS, p.getLocation().add(0, 1, 0), 25, 0.4, 0.5, 0.4, 0.1);
                p.sendActionBar(Component.text("✦ มันสำปะหลัง: +25% โอกาสพบของแรร์ใน Evergarden Vault (5 นาที)", NamedTextColor.AQUA));
            }
            case GOLDLEAF_HERB -> {
                mendingNectarUntil.put(id, now + 120_000L);
                p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 2400, 0));
                p.getWorld().playSound(p.getLocation(), Sound.BLOCK_ANVIL_USE, 0.6f, 1.6f);
                p.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, p.getLocation().add(0, 1, 0), 25, 0.4, 0.5, 0.4, 0.05);
                p.sendActionBar(Component.text("✦ ผักโขม: Resistance I & แปลง 50% ดาเมจซ่อมเกราะ (2 นาที)", NamedTextColor.GOLD));
            }
            case SOUL_WARD_BULB -> {
                soulWardCooldown.put(id, now + 60_000L);
                soulWardActive.add(id);
                p.getWorld().playSound(p.getLocation(), Sound.ITEM_TOTEM_USE, 0.6f, 1.5f);
                p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, p.getLocation().add(0, 1, 0), 25, 0.3, 0.5, 0.3, 0.15);
                p.sendActionBar(Component.text("✦ หอมหัวใหญ่: ม่านพลังป้องกันการตาย 1 ครั้ง เปิดใช้งานแล้ว!", NamedTextColor.AQUA));
            }
            case CHRONO_PEPPER -> {
                chronoSurge.put(id, now + 60_000L);
                p.getWorld().playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1.0f, 1.8f);
                p.getWorld().spawnParticle(Particle.ENCHANT, p.getLocation().add(0, 1, 0), 30, 0.4, 0.6, 0.4, 0.2);
                p.sendActionBar(Component.text("✦ พริกหวาน: -40% คูลดาวน์คทาเวทมนตร์ทั้งหมด (60 วินาที)", NamedTextColor.GOLD));
            }

            // ==========================================
            // Tier 5: Mythic
            // ==========================================
            case ANCIENT_ASTRAL_ROOT -> {
                if (!handleAncientAstralRoot(p)) {
                    e.setCancelled(true);
                }
            }
            case YGGDRASIL_SPROUT -> {
                if (!handleYggdrasilSprout(p)) {
                    e.setCancelled(true);
                }
            }
            case VOID_OVERCHARGE_FIG -> {
                if (!overchargeUntil.containsKey(id)) {
                    double currentMax = getPlayerMaxMana(p);
                    overchargeOriginalMax.put(id, currentMax);
                    setPlayerMaxMana(p, currentMax + 100.0);
                }
                overchargeUntil.put(id, now + 45_000L);
                addPlayerMana(p, 100.0);

                p.getWorld().playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.6f, 1.6f);
                p.getWorld().spawnParticle(Particle.DRAGON_BREATH, p.getLocation().add(0, 1, 0), 35, 0.4, 0.6, 0.4, 0.05, 1.0f);
                p.sendTitle(ChatColor.LIGHT_PURPLE + "✦ MANA OVERCHARGE ✦", ChatColor.AQUA + "+100 Overcharge Mana (45 วินาที)!", 5, 40, 10);
            }
            case ETHEREAL_MINT -> {
                arcaneEchoCharges.put(id, arcaneEchoCharges.getOrDefault(id, 0) + 2);
                p.getWorld().playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1.0f, 1.6f);
                p.getWorld().spawnParticle(Particle.WITCH, p.getLocation().add(0, 1, 0), 25, 0.4, 0.5, 0.4, 0.1);
                p.sendTitle(ChatColor.AQUA + "✦ ARCANE ECHO ✦", ChatColor.WHITE + "ร่ายเวทซ้ำเบิ้ล 2 เท่าฟรี! (2 ชาร์จ, คูลดาวน์ 3s)", 5, 40, 10);
            }
            case BLOODBURN_CHILI -> {
                bloodCastUntil.put(id, now + 30_000L);
                p.getWorld().playSound(p.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 0.9f, 1.2f);
                p.getWorld().spawnParticle(Particle.FLAME, p.getLocation().add(0, 1, 0), 35, 0.4, 0.6, 0.4, 0.08);
                p.sendTitle(ChatColor.RED + "✦ BLOOD CAST ✦", ChatColor.YELLOW + "ร่ายเวทด้วยพลังชีวิตแทนเมื่อมานาหมด (30 วินาที)!", 5, 40, 10);
            }
            case OMNI_POMEGRANATE -> {
                omniReboundUntil.put(id, now + 60_000L);
                p.getWorld().playSound(p.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 1.8f);
                p.getWorld().spawnParticle(Particle.END_ROD, p.getLocation().add(0, 1, 0), 30, 0.4, 0.6, 0.4, 0.1);
                p.sendTitle(ChatColor.GOLD + "✦ ELEMENTAL REBOUND ✦", ChatColor.WHITE + "ระเบิดมหาธาตุ 18 ดาเมจรอบตัวเมื่อร่ายเวท (60 วินาที)!", 5, 40, 10);
            }
        }
    }

    private boolean handleAncientAstralRoot(Player p) {
        World rootWorld = Bukkit.getWorlds().isEmpty() ? p.getWorld() : Bukkit.getWorlds().get(0);
        long currentFullTime = rootWorld.getFullTime();
        long currentDay = currentFullTime / 24000L;
        var pdc = p.getPersistentDataContainer();
        NamespacedKey lastKey = plugin.key("last_astral_day");
        NamespacedKey dragonKey = new NamespacedKey("advance-magic", "last_dragon_day");
        NamespacedKey legacyDragonKey = new NamespacedKey("advance_magic", "last_dragon_day");

        long lastDay = pdc.getOrDefault(lastKey, PersistentDataType.LONG, -1L);
        if (lastDay == -1L && pdc.has(dragonKey, PersistentDataType.LONG)) {
            lastDay = pdc.getOrDefault(dragonKey, PersistentDataType.LONG, -1L);
        } else if (lastDay == -1L && pdc.has(legacyDragonKey, PersistentDataType.LONG)) {
            lastDay = pdc.getOrDefault(legacyDragonKey, PersistentDataType.LONG, -1L);
        }

        if (currentDay == lastDay) {
            long ticksRemaining = 24000L - (currentFullTime % 24000L);
            long totalSec = Math.max(1L, ticksRemaining / 20L);
            long m = totalSec / 60L;
            long s = totalSec % 60L;
            String timeStr = m > 0 ? (m + " นาที " + s + " วินาที") : (s + " วินาที");
            p.sendActionBar(Component.text("กิน Ancient Astral Root ได้วันละ 1 ครั้งในเกมเท่านั้น (รออีก " + timeStr + ")", NamedTextColor.RED));
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return false;
        }

        double currentMax = getPlayerMaxMana(p);
        if (currentMax >= 300.0) {
            p.sendActionBar(Component.text("คุณมี Max Mana ถึงขีดจำกัดสูงสุดแล้ว (300/300)", NamedTextColor.LIGHT_PURPLE));
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return false;
        }

        double gain;
        if (currentMax < 150.0) gain = 5.0;
        else if (currentMax < 200.0) gain = 2.0;
        else gain = 0.5;

        double newMax = Math.min(300.0, currentMax + gain);
        double newRegen = Math.min(15.0, getPlayerManaRegen(p) + 0.2);

        setPlayerMaxMana(p, newMax);
        setPlayerManaRegen(p, newRegen);
        addPlayerMana(p, newMax); // full replenish

        pdc.set(lastKey, PersistentDataType.LONG, currentDay);
        pdc.set(dragonKey, PersistentDataType.LONG, currentDay);
        pdc.set(legacyDragonKey, PersistentDataType.LONG, currentDay);

        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
        p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.7f, 1.3f);
        p.getWorld().spawnParticle(Particle.DRAGON_BREATH, p.getLocation().add(0, 1, 0), 45, 0.4, 0.6, 0.4, 0.05, 1.0f);
        p.getWorld().spawnParticle(Particle.END_ROD, p.getLocation().add(0, 1, 0), 30, 0.4, 0.6, 0.4, 0.08);

        String gainStr = (gain == (long)gain) ? String.valueOf((long)gain) : String.format(Locale.ROOT, "%.1f", gain);
        String maxStr = (newMax == (long)newMax) ? String.valueOf((long)newMax) : String.format(Locale.ROOT, "%.1f", newMax);
        String regenStr = String.format(Locale.ROOT, "%.1f", newRegen);

        p.sendTitle(ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "✦ SWEET POTATO ✦",
                ChatColor.AQUA + "Max Mana: " + maxStr + " (+" + gainStr + ") | " + ChatColor.GREEN + "Regen: " + regenStr + "/s", 10, 70, 20);
        p.sendMessage(ChatColor.LIGHT_PURPLE + "[Evergarden] " + ChatColor.WHITE + "คุณบริโภค " + ChatColor.GOLD + "มันหวาน " +
                ChatColor.WHITE + "ซึมซับพลังดวงดาวดึกดำบรรพ์! Max Mana: " + ChatColor.AQUA + maxStr + ChatColor.GREEN + " (+" + gainStr + ")" +
                ChatColor.WHITE + " | Mana Regen: " + ChatColor.AQUA + regenStr + "/s");
        return true;
    }

    private boolean handleYggdrasilSprout(Player p) {
        World rootWorld = Bukkit.getWorlds().isEmpty() ? p.getWorld() : Bukkit.getWorlds().get(0);
        long currentFullTime = rootWorld.getFullTime();
        long currentDay = currentFullTime / 24000L;
        var pdc = p.getPersistentDataContainer();
        NamespacedKey lastKey = plugin.key("last_yggdrasil_day");

        long lastDay = pdc.getOrDefault(lastKey, PersistentDataType.LONG, -1L);
        if (currentDay == lastDay) {
            long ticksRemaining = 24000L - (currentFullTime % 24000L);
            long totalSec = Math.max(1L, ticksRemaining / 20L);
            long m = totalSec / 60L;
            long s = totalSec % 60L;
            String timeStr = m > 0 ? (m + " นาที " + s + " วินาที") : (s + " วินาที");
            p.sendActionBar(Component.text("กิน Yggdrasil Sprout ได้วันละ 1 ครั้งในเกมเท่านั้น (รออีก " + timeStr + ")", NamedTextColor.RED));
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return false;
        }

        double currentRegen = getPlayerManaRegen(p);
        if (currentRegen >= 15.0) {
            p.sendActionBar(Component.text("คุณมี Mana Regen สูงสุดแล้ว (15.0/s)", NamedTextColor.GREEN));
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return false;
        }

        double newRegen = Math.min(15.0, currentRegen + 0.2);
        setPlayerManaRegen(p, newRegen);
        pdc.set(lastKey, PersistentDataType.LONG, currentDay);

        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.4f);
        p.getWorld().spawnParticle(Particle.SPORE_BLOSSOM_AIR, p.getLocation().add(0, 1, 0), 40, 0.5, 0.8, 0.5, 0.05);
        p.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, p.getLocation().add(0, 1, 0), 30, 0.4, 0.6, 0.4, 0.1);

        String regenStr = String.format(Locale.ROOT, "%.1f", newRegen);
        p.sendTitle(ChatColor.GREEN + "" + ChatColor.BOLD + "✦ ASPARAGUS ✦",
                ChatColor.AQUA + "Mana Regen: " + regenStr + "/s (+0.2/s)", 10, 60, 20);
        p.sendMessage(ChatColor.LIGHT_PURPLE + "[Evergarden] " + ChatColor.WHITE + "คุณบริโภค " + ChatColor.GREEN + "หน่อไม้ฝรั่ง " +
                ChatColor.WHITE + "อัตราฟื้นฟู Mana เพิ่มขึ้นเป็น: " + ChatColor.AQUA + regenStr + "/s");
        return true;
    }

    private void cleanseDebuffs(Player p) {
        for (PotionEffect pe : p.getActivePotionEffects()) {
            PotionEffectType t = pe.getType();
            if (t.equals(PotionEffectType.POISON) || t.equals(PotionEffectType.WITHER) ||
                t.equals(PotionEffectType.SLOWNESS) || t.equals(PotionEffectType.MINING_FATIGUE) ||
                t.equals(PotionEffectType.NAUSEA) || t.equals(PotionEffectType.BLINDNESS) ||
                t.equals(PotionEffectType.HUNGER) || t.equals(PotionEffectType.WEAKNESS) ||
                t.equals(PotionEffectType.DARKNESS) || t.equals(PotionEffectType.LEVITATION)) {
                p.removePotionEffect(t);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPotionEffect(EntityPotionEffectEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        if (e.getAction() != EntityPotionEffectEvent.Action.ADDED) return;
        if (debuffImmunityUntil.getOrDefault(p.getUniqueId(), 0L) > System.currentTimeMillis()) {
            PotionEffect pe = e.getNewEffect();
            if (pe != null) {
                PotionEffectType t = pe.getType();
                if (t.equals(PotionEffectType.POISON) || t.equals(PotionEffectType.WITHER) ||
                    t.equals(PotionEffectType.SLOWNESS) || t.equals(PotionEffectType.MINING_FATIGUE) ||
                    t.equals(PotionEffectType.NAUSEA) || t.equals(PotionEffectType.BLINDNESS) ||
                    t.equals(PotionEffectType.HUNGER) || t.equals(PotionEffectType.WEAKNESS) ||
                    t.equals(PotionEffectType.DARKNESS) || t.equals(PotionEffectType.LEVITATION)) {
                    e.setCancelled(true);
                }
            }
        }
    }

    // ==========================================
    // 2. Combat & Damage Handlers
    // ==========================================
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCombat(EntityDamageByEntityEvent e) {
        if (shreddedTargets.getOrDefault(e.getEntity().getUniqueId(), 0L) > System.currentTimeMillis()) {
            e.setDamage(e.getDamage() * 1.25);
        }

        Player attacker = null;
        if (e.getDamager() instanceof Player pl) attacker = pl;
        else if (e.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player pl) attacker = pl;
        if (attacker == null) return;

        UUID id = attacker.getUniqueId();
        long now = System.currentTimeMillis();

        // 1. Vampiric Lifesteal (20%)
        if (vampiricUntil.getOrDefault(id, 0L) > now && e.getEntity() instanceof LivingEntity) {
            double heal = e.getFinalDamage() * 0.20;
            if (heal > 0.1) {
                var maxHpAttr = attacker.getAttribute(Attribute.MAX_HEALTH);
                double maxHp = maxHpAttr != null ? maxHpAttr.getValue() : 20.0;
                attacker.setHealth(Math.min(maxHp, attacker.getHealth() + heal));
                attacker.getWorld().spawnParticle(Particle.HEART, attacker.getLocation().add(0, 1, 0), 3, 0.2, 0.2, 0.2, 0.05);
            }
        }

        // 2. Glacial Trap (Freeze)
        if (glacialUntil.getOrDefault(id, 0L) > now && e.getEntity() instanceof LivingEntity target) {
            target.setFreezeTicks(140);
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 30, 5, false, false));
            target.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 30, 2, false, false));
            target.getWorld().playSound(target.getLocation(), Sound.BLOCK_GLASS_BREAK, 0.8f, 1.8f);
            target.getWorld().spawnParticle(Particle.SNOWFLAKE, target.getLocation().add(0, 1, 0), 20, 0.3, 0.4, 0.3, 0.05);
        }

        // 3. Chain Lightning (with reentrancy guard)
        if (chainLightningUntil.getOrDefault(id, 0L) > now && e.getEntity() instanceof LivingEntity target) {
            target.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, target.getLocation().add(0, 1, 0), 15, 0.3, 0.3, 0.3, 0.1);
            if (isProcessingChainLightning.add(id)) {
                try {
                    int chains = 0;
                    for (Entity nearby : target.getWorld().getNearbyEntities(target.getLocation(), 6, 6, 6)) {
                        if (nearby instanceof LivingEntity le && !nearby.equals(target) && !nearby.equals(attacker) && !le.isDead() && !(le instanceof ArmorStand) && chains < 3) {
                            if (le instanceof Monster || (le instanceof Player && plugin.getConfig().getBoolean("relics.allow-pvp", false))) {
                                chains++;
                                le.damage(6.0, attacker);
                                le.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, le.getLocation().add(0, 1, 0), 15, 0.3, 0.3, 0.3, 0.1);
                                le.getWorld().playSound(le.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.5f, 1.8f);
                            }
                        }
                    }
                } finally {
                    isProcessingChainLightning.remove(id);
                }
            }
        }

        // 4. Executioner (< 20% max HP)
        if (executionerUntil.getOrDefault(id, 0L) > now && e.getEntity() instanceof LivingEntity target) {
            if (!(target instanceof Boss) && !(target instanceof Player)) {
                var maxAttr = target.getAttribute(Attribute.MAX_HEALTH);
                double max = maxAttr != null ? maxAttr.getValue() : 20.0;
                double remaining = target.getHealth() - e.getFinalDamage();
                if (remaining > 0 && remaining <= max * 0.20) {
                    e.setDamage(max * 2.0); // lethal damage
                    target.getWorld().playSound(target.getLocation(), Sound.ENTITY_WITHER_BREAK_BLOCK, 0.8f, 1.6f);
                    target.getWorld().spawnParticle(Particle.SOUL, target.getLocation().add(0, 1, 0), 30, 0.4, 0.5, 0.4, 0.1);
                }
            }
        }

        // 5. Titan Armor Shred (25% more damage for 5s)
        if (titanUntil.getOrDefault(id, 0L) > now && e.getEntity() instanceof LivingEntity target) {
            shreddedTargets.put(target.getUniqueId(), now + 5000L);
            target.getWorld().playSound(target.getLocation(), Sound.ITEM_ARMOR_EQUIP_IRON, 0.9f, 0.8f);
            target.getWorld().spawnParticle(Particle.CRIT, target.getLocation().add(0, 1, 0), 15, 0.3, 0.4, 0.3, 0.1);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onIncomingDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        UUID id = p.getUniqueId();
        long now = System.currentTimeMillis();

        // 1. Kinetic Fall Damage Cancel & Ground Slam
        if (e.getCause() == EntityDamageEvent.DamageCause.FALL && kineticSlamUntil.getOrDefault(id, 0L) > now) {
            e.setCancelled(true);
            double fallDistance = p.getFallDistance();
            p.setFallDistance(0);
            if (fallDistance > 3.0) {
                double radius = Math.min(8.0, 3.0 + fallDistance * 0.4);
                double damage = Math.min(25.0, fallDistance * 1.5);
                Location loc = p.getLocation();
                loc.getWorld().playSound(loc, Sound.ENTITY_IRON_GOLEM_DAMAGE, 1.0f, 0.6f);
                loc.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.4f);
                loc.getWorld().spawnParticle(Particle.EXPLOSION, loc, 3, 0.5, 0.2, 0.5, 0.1);
                loc.getWorld().spawnParticle(Particle.BLOCK, loc, 40, 1.2, 0.2, 1.2, 0.2, Material.DIRT.createBlockData());

                for (Entity ent : loc.getWorld().getNearbyEntities(loc, radius, radius, radius)) {
                    if (ent instanceof Monster m && !m.isDead()) {
                        m.damage(damage, p);
                        Vector away = m.getLocation().toVector().subtract(loc.toVector());
                        if (away.lengthSquared() > 0.001) {
                            m.setVelocity(away.normalize().setY(0.4).multiply(1.1));
                        }
                    }
                }
            }
            return;
        }

        // 2. Mending Nectar (repair durability from 50% of incoming damage)
        if (mendingNectarUntil.getOrDefault(id, 0L) > now && e.getDamage() > 0) {
            int repairPoints = (int) Math.round(e.getDamage() * 0.50 * 10);
            if (repairPoints > 0) {
                repairEquipment(p, repairPoints);
                p.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, p.getLocation().add(0, 1, 0), 6, 0.3, 0.3, 0.3, 0.05);
            }
        }

        // 2.5 Abyssal Bubble Shield: Extinguish fire & counter-blast knockback
        if (abyssalBubbleUntil.getOrDefault(id, 0L) > now) {
            if (p.getFireTicks() > 0) {
                p.setFireTicks(0);
                p.getWorld().playSound(p.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 0.8f, 1.2f);
            }
            if (e.getDamage() > 0 && abyssalCooldown.getOrDefault(id, 0L) <= now) {
                abyssalCooldown.put(id, now + 5000L);
                Location loc = p.getLocation();
                loc.getWorld().playSound(loc, Sound.ENTITY_PLAYER_SPLASH, 1.0f, 1.2f);
                loc.getWorld().playSound(loc, Sound.BLOCK_CONDUIT_DEACTIVATE, 0.8f, 1.6f);
                loc.getWorld().spawnParticle(Particle.SPLASH, loc.clone().add(0, 0.8, 0), 35, 0.5, 0.4, 0.5, 0.15);
                loc.getWorld().spawnParticle(Particle.BUBBLE_POP, loc.clone().add(0, 1.0, 0), 20, 0.5, 0.5, 0.5, 0.05);

                for (Entity ent : loc.getWorld().getNearbyEntities(loc, 5.0, 5.0, 5.0)) {
                    if (ent instanceof Monster m && !m.isDead()) {
                        m.damage(4.0, p);
                        Vector away = m.getLocation().toVector().subtract(loc.toVector());
                        if (away.lengthSquared() > 0.001) {
                            m.setVelocity(away.normalize().setY(0.35).multiply(0.9));
                        }
                    }
                }
                p.sendActionBar(Component.text("✦ คลื่นน้ำลึกระเบิดสะท้อนศัตรูกระเด็น!", NamedTextColor.AQUA));
            }
        }

        // 3. Undying Aegis (Prevent death)
        if (soulWardActive.contains(id)) {
            if (p.getHealth() - e.getFinalDamage() <= 0) {
                e.setCancelled(true);
                soulWardActive.remove(id);
                p.setHealth(4.0);
                p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 120, 2));
                p.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 200, 0));

                Location loc = p.getLocation();
                loc.getWorld().playSound(loc, Sound.ITEM_TOTEM_USE, 1.0f, 1.0f);
                loc.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, loc.clone().add(0, 1, 0), 60, 0.5, 0.8, 0.5, 0.3);

                // Radial shockwave knockback (guarded against NaN)
                for (Entity ent : loc.getWorld().getNearbyEntities(loc, 8, 4, 8)) {
                    if (ent instanceof LivingEntity le && !ent.equals(p)) {
                        Vector diff = ent.getLocation().toVector().subtract(loc.toVector());
                        if (diff.lengthSquared() > 0.001) {
                            Vector away = diff.normalize().setY(0.5).multiply(1.8);
                            com.example.voidscape.compat.PlayerImpulse.apply(plugin,ent,away);
                        } else {
                            com.example.voidscape.compat.PlayerImpulse.apply(plugin,ent,new Vector(0, 0.8, 0));
                        }
                    }
                }
                p.sendTitle(ChatColor.GOLD + "✦ UNDYING AEGIS ✦", ChatColor.WHITE + "ม่านพลังวิญญาณช่วยชีวิตคุณไว้!", 5, 50, 15);
            }
        }
    }

    private void repairEquipment(Player p, int points) {
        List<ItemStack> pieces = new ArrayList<>();
        for (ItemStack armor : p.getInventory().getArmorContents()) {
            if (armor != null && armor.getItemMeta() instanceof Damageable d && d.hasDamage()) pieces.add(armor);
        }
        ItemStack main = p.getInventory().getItemInMainHand();
        if (main != null && main.getItemMeta() instanceof Damageable d && d.hasDamage()) pieces.add(main);

        if (pieces.isEmpty()) return;
        int each = Math.max(1, points / pieces.size());
        for (ItemStack item : pieces) {
            Damageable d = (Damageable) item.getItemMeta();
            d.setDamage(Math.max(0, d.getDamage() - each));
            item.setItemMeta(d);
        }
    }

    // ==========================================
    // 3. Double Jump & Movement Handlers
    // ==========================================
    // ==========================================
    // 4. Utility & Mining Buff Handlers
    // ==========================================
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent e) {
        if (cropService.factory().isFood(e.getItemInHand())) {
            e.setCancelled(true);
            return;
        }
        String name = e.getBlock().getType().name();
        if (name.endsWith("_ORE") || name.equals("ANCIENT_DEBRIS")) {
            e.getBlock().setMetadata("evergarden_placed", new FixedMetadataValue(plugin, true));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onOreBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        UUID id = p.getUniqueId();
        long now = System.currentTimeMillis();

        // 1. Ore Resonance (+35% duplication, prevented if Silk Touch or player-placed!)
        if (oreResonanceUntil.getOrDefault(id, 0L) > now) {
            ItemStack hand = p.getInventory().getItemInMainHand();
            boolean hasSilk = hand != null && hand.containsEnchantment(org.bukkit.enchantments.Enchantment.SILK_TOUCH);
            boolean playerPlaced = e.getBlock().hasMetadata("evergarden_placed");

            if (!hasSilk && !playerPlaced) {
                String name = e.getBlock().getType().name();
                if (name.endsWith("_ORE") || name.equals("ANCIENT_DEBRIS")) {
                    if (Math.random() < 0.35) {
                        Location loc = e.getBlock().getLocation().clone().add(0.5, 0.5, 0.5);
                        for (ItemStack drop : e.getBlock().getDrops(p.getInventory().getItemInMainHand())) {
                            loc.getWorld().dropItemNaturally(loc, drop);
                        }
                        loc.getWorld().playSound(loc, Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.8f);
                        loc.getWorld().spawnParticle(Particle.SCRAPE, loc, 12, 0.3, 0.3, 0.3, 0.1);
                        p.sendActionBar(Component.text("✦ Ore Resonance: แร่ดรอปเบิ้ล 2 เท่า!", NamedTextColor.LIGHT_PURPLE));
                    }
                }
            }
        }

        if (fellingPlayers.contains(id)) return;

        // 2. Tree Feller (5 charges, respecting claims and durability)
        int charges = treeFellerCharges.getOrDefault(id, 0);
        if (charges > 0 && Tag.LOGS.isTagged(e.getBlock().getType())) {
            ItemStack mainHand = p.getInventory().getItemInMainHand();
            if (com.example.voidscape.enchant.EnchantApplyListener.hasUnique(mainHand, com.example.voidscape.enchant.UniqueEnchant.TITAN_BREACH)) {
                return;
            }
            int remaining = charges - 1;
            if (remaining <= 0) {
                treeFellerCharges.remove(id);
                p.sendActionBar(Component.text("✦ Tree Feller: โค่นต้นไม้สำเร็จ! (ชาร์จหมดแล้ว)", NamedTextColor.GRAY));
            } else {
                treeFellerCharges.put(id, remaining);
                p.sendActionBar(Component.text("✦ Tree Feller: โค่นต้นไม้สำเร็จ! (เหลือ " + remaining + " ต้น)", NamedTextColor.GOLD));
            }
            fellTree(e.getBlock(), p);
        }
    }

    private void fellTree(Block start, Player p) {
        fellingPlayers.add(p.getUniqueId());
        try {
            Set<Block> logs = new HashSet<>();
            Queue<Block> queue = new ArrayDeque<>();
            queue.add(start);

        while (!queue.isEmpty() && logs.size() < 128) {
            Block curr = queue.poll();
            if (!logs.add(curr)) continue;

            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        Block n = curr.getRelative(dx, dy, dz);
                        if (Tag.LOGS.isTagged(n.getType()) && !logs.contains(n)) {
                            queue.add(n);
                        } else if (Tag.LEAVES.isTagged(n.getType())) {
                            // Check claim protection on leaves
                            BlockBreakEvent leafEvent = new BlockBreakEvent(n, p);
                            Bukkit.getPluginManager().callEvent(leafEvent);
                            if (!leafEvent.isCancelled()) {
                                n.breakNaturally();
                            }
                        }
                    }
                }
            }
        }

        for (Block log : logs) {
            if (!log.equals(start)) {
                // Check claim protection on logs
                BlockBreakEvent logEvent = new BlockBreakEvent(log, p);
                Bukkit.getPluginManager().callEvent(logEvent);
                if (logEvent.isCancelled()) continue;

                log.breakNaturally(p.getInventory().getItemInMainHand());

                // Damage axe durability
                if (p.getGameMode() != GameMode.CREATIVE) {
                    ItemStack tool = p.getInventory().getItemInMainHand();
                    if (tool != null && tool.getItemMeta() instanceof Damageable d) {
                        d.setDamage(d.getDamage() + 1);
                        tool.setItemMeta(d);
                    }
                }
            }
        }
    } finally {
        fellingPlayers.remove(p.getUniqueId());
    }
}

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent e) {
        if (e.getTarget() instanceof Player p) {
            if (chameleonUntil.getOrDefault(p.getUniqueId(), 0L) > System.currentTimeMillis()) {
                e.setCancelled(true);
            }
        }
    }

    // ==========================================
    // 5. Player Lifecycle & Memory Cleanup
    // ==========================================
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        UUID id = p.getUniqueId();
        if (pendingOfflineManaResets.containsKey(id)) {
            Double orig = pendingOfflineManaResets.remove(id);
            if (orig != null) {
                setPlayerMaxMana(p, orig);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        UUID id = p.getUniqueId();

        // 3. Reset Overcharge
        if (overchargeUntil.remove(id) != null) {
            Double orig = overchargeOriginalMax.remove(id);
            if (orig != null) {
                setPlayerMaxMana(p, orig);
            }
        }

        cleanPlayerMaps(id);
    }

    private void cleanPlayerMaps(UUID id) {
        chronoSurge.remove(id);
        arcaneEchoCharges.remove(id);
        lastArcaneEcho.remove(id);
        vampiricUntil.remove(id);
        glacialUntil.remove(id);
        chainLightningUntil.remove(id);
        executionerUntil.remove(id);
        titanUntil.remove(id);
        shreddedTargets.remove(id);
        soulWardActive.remove(id);
        kineticSlamUntil.remove(id);
        chameleonUntil.remove(id);
        abyssalBubbleUntil.remove(id);
        abyssalCooldown.remove(id);
        magnetUntil.remove(id);
        oreResonanceUntil.remove(id);
        floraAuraCharges.remove(id);
        treeFellerCharges.remove(id);
        fellingPlayers.remove(id);
        vaultFortuneUntil.remove(id);
        bloodCastUntil.remove(id);
        debuffImmunityUntil.remove(id);
        sniperCastUntil.remove(id);
        mendingNectarUntil.remove(id);
        omniReboundUntil.remove(id);
        recallChannelLoc.remove(id);
    }

    // ==========================================
    // 6. Ticker for Passive Buffs
    // ==========================================
    public void tick() {
        long now = System.currentTimeMillis();

        // 1. Magnet Ticker
        for (Map.Entry<UUID, Long> entry : magnetUntil.entrySet()) {
            if (entry.getValue() > now) {
                Player p = Bukkit.getPlayer(entry.getKey());
                if (p != null && p.isOnline()) {
                    for (Entity ent : p.getWorld().getNearbyEntities(p.getLocation(), 12, 12, 12)) {
                        if (ent instanceof Item item && item.isValid() && !item.isDead()) {
                            Vector dir = p.getLocation().add(0, 0.5, 0).toVector().subtract(item.getLocation().toVector()).normalize().multiply(0.85);
                            item.setVelocity(dir);
                        } else if (ent instanceof ExperienceOrb orb && orb.isValid()) {
                            Vector dir = p.getLocation().add(0, 0.5, 0).toVector().subtract(orb.getLocation().toVector()).normalize().multiply(0.85);
                            orb.setVelocity(dir);
                        }
                    }
                }
            } else {
                magnetUntil.remove(entry.getKey());
            }
        }

        movement.tick();

        // 4. Overcharge Cleanup
        for (Map.Entry<UUID, Long> entry : overchargeUntil.entrySet()) {
            if (entry.getValue() <= now) {
                overchargeUntil.remove(entry.getKey());
                Player p = Bukkit.getPlayer(entry.getKey());
                Double orig = overchargeOriginalMax.remove(entry.getKey());
                if (p != null && orig != null) {
                    setPlayerMaxMana(p, orig);
                    p.sendMessage(ChatColor.DARK_PURPLE + "[Evergarden] " + ChatColor.GRAY + "Mana Overcharge สิ้นสุดลงแล้ว");
                } else if (orig != null) {
                    pendingOfflineManaResets.put(entry.getKey(), orig);
                }
            }
        }

        // 5. Flora Aura Ticker (using spatial chunk lookup)
        for (Map.Entry<UUID, Integer> entry : floraAuraCharges.entrySet()) {
            if (entry.getValue() > 0) {
                Player p = Bukkit.getPlayer(entry.getKey());
                if (p != null && p.isOnline()) {
                    PlantedCrop crop = cropService.findNearestUnripeCrop(p.getLocation(), 5.0);
                    if (crop != null) {
                        int accel = Math.max(60, crop.getType().tier.growthSeconds / 3);
                        crop.accelerate(accel);
                        cropService.onCropAccelerated(crop);
                        Location cLoc = crop.getLocation().add(0.5, 0.5, 0.5);
                        cLoc.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, cLoc, 10, 0.3, 0.3, 0.3, 0.05);
                        int remaining = entry.getValue() - 1;
                        if (remaining <= 0) {
                            floraAuraCharges.remove(entry.getKey());
                            p.sendMessage(ChatColor.GREEN + "[Flora Aura] พลังเร่งโตพืชพรรณหมดแล้ว");
                        } else {
                            floraAuraCharges.put(entry.getKey(), remaining);
                        }
                    }
                }
            } else {
                floraAuraCharges.remove(entry.getKey());
            }
        }
    }

    // ==========================================
    // 7. Advance Magic Spell & Mana Integrations
    // ==========================================
    public double getPlayerMaxMana(Player p) {
        var data = p.getPersistentDataContainer();
        NamespacedKey key = new NamespacedKey("advance-magic", "max_mana");
        NamespacedKey legacyKey = new NamespacedKey("advance_magic", "max_mana");
        if (data.has(key, PersistentDataType.DOUBLE)) return data.get(key, PersistentDataType.DOUBLE);
        if (data.has(legacyKey, PersistentDataType.DOUBLE)) return data.get(legacyKey, PersistentDataType.DOUBLE);
        if (data.has(key, PersistentDataType.INTEGER)) return data.get(key, PersistentDataType.INTEGER);
        return 100.0;
    }

    public void setPlayerMaxMana(Player p, double max) {
        var data = p.getPersistentDataContainer();
        NamespacedKey key = new NamespacedKey("advance-magic", "max_mana");
        NamespacedKey legacyKey = new NamespacedKey("advance_magic", "max_mana");
        data.set(key, PersistentDataType.DOUBLE, max);
        data.set(legacyKey, PersistentDataType.DOUBLE, max);
        syncAdvanceMagicAccount(p, max, null, null);
    }

    public double getPlayerManaRegen(Player p) {
        var data = p.getPersistentDataContainer();
        NamespacedKey key = new NamespacedKey("advance-magic", "mana_regen");
        NamespacedKey legacyKey = new NamespacedKey("advance_magic", "mana_regen");
        if (data.has(key, PersistentDataType.DOUBLE)) return data.get(key, PersistentDataType.DOUBLE);
        if (data.has(legacyKey, PersistentDataType.DOUBLE)) return data.get(legacyKey, PersistentDataType.DOUBLE);
        return 2.0;
    }

    public void setPlayerManaRegen(Player p, double regen) {
        var data = p.getPersistentDataContainer();
        NamespacedKey key = new NamespacedKey("advance-magic", "mana_regen");
        NamespacedKey legacyKey = new NamespacedKey("advance_magic", "mana_regen");
        data.set(key, PersistentDataType.DOUBLE, regen);
        data.set(legacyKey, PersistentDataType.DOUBLE, regen);
        syncAdvanceMagicAccount(p, null, regen, null);
    }

    public void addPlayerMana(Player p, double amount) {
        var data = p.getPersistentDataContainer();
        NamespacedKey key = new NamespacedKey("advance-magic", "mana");
        NamespacedKey legacyKey = new NamespacedKey("advance_magic", "mana");
        double cur = 100.0;
        if (data.has(key, PersistentDataType.DOUBLE)) cur = data.get(key, PersistentDataType.DOUBLE);
        else if (data.has(legacyKey, PersistentDataType.DOUBLE)) cur = data.get(legacyKey, PersistentDataType.DOUBLE);

        double max = getPlayerMaxMana(p);
        double newMana = Math.min(max, cur + amount);
        data.set(key, PersistentDataType.DOUBLE, newMana);
        data.set(legacyKey, PersistentDataType.DOUBLE, newMana);
        syncAdvanceMagicAccount(p, null, null, newMana);
    }

    private void syncAdvanceMagicAccount(Player p, Double max, Double regen, Double mana) {
        try {
            var advPlugin = Bukkit.getPluginManager().getPlugin("advance-magic");
            if (advPlugin != null) {
                var manaServiceField = advPlugin.getClass().getMethod("mana");
                Object manaService = manaServiceField.invoke(advPlugin);
                var accountMethod = manaService.getClass().getMethod("account", Player.class);
                Object account = accountMethod.invoke(manaService, p);
                if (max != null) account.getClass().getMethod("setMaxMana", double.class).invoke(account, max);
                if (regen != null) account.getClass().getMethod("setRegenRate", double.class).invoke(account, regen);
                if (mana != null) account.getClass().getMethod("setMana", double.class).invoke(account, mana);
            }
        } catch (Throwable ignored) {}
    }

    @Override
    public void close() {
        movement.close();

        // 3. Reset overcharge max mana
        for (Map.Entry<UUID, Double> entry : overchargeOriginalMax.entrySet()) {
            Player p = Bukkit.getPlayer(entry.getKey());
            if (p != null) {
                setPlayerMaxMana(p, entry.getValue());
            }
        }
    }
}
