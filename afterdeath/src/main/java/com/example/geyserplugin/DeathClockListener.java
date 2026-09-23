package com.example.geyserplugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class DeathClockListener implements Listener {

    private static final long EXPIRE_SECONDS = 120L;
    private static final long EXPIRE_TICKS = EXPIRE_SECONDS * 20L; // 2400 ticks

    private final GeyserExamplePlugin plugin;
    private final NamespacedKey keyIsDeathClock;
    private final NamespacedKey keyExpiresAt;
    private final NamespacedKey keyWorld;
    private final NamespacedKey keyX;
    private final NamespacedKey keyY;
    private final NamespacedKey keyZ;
    private final NamespacedKey keyYaw;
    private final NamespacedKey keyPitch;

    private final Map<UUID, Location> lastDeathLocations = new ConcurrentHashMap<>();

    public DeathClockListener(GeyserExamplePlugin plugin) {
        this.plugin = plugin;
        this.keyIsDeathClock = new NamespacedKey(plugin, "is_death_clock");
        this.keyExpiresAt = new NamespacedKey(plugin, "death_clock_expires_at");
        this.keyWorld = new NamespacedKey(plugin, "death_world");
        this.keyX = new NamespacedKey(plugin, "death_x");
        this.keyY = new NamespacedKey(plugin, "death_y");
        this.keyZ = new NamespacedKey(plugin, "death_z");
        this.keyYaw = new NamespacedKey(plugin, "death_yaw");
        this.keyPitch = new NamespacedKey(plugin, "death_pitch");
    }

    /**
     * ดักจับตอนผู้เล่นเสียชีวิต เพื่อบันทึกพิกัดล่าสุด
     */
    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getPlayer();
        Location deathLoc = player.getLocation();
        lastDeathLocations.put(player.getUniqueId(), deathLoc);
    }

    /**
     * มอบนาฬิกา Enchant ใส่เข้ามือหลักของผู้เล่นทันทีตอนเกิดใหม่ (Respawn)
     */
    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        Location deathLoc = lastDeathLocations.get(player.getUniqueId());

        if (deathLoc != null) {
            // ดีเลย์ 1 tick เพื่อให้ระบบ Respawn ของเกมเซ็ตอัป inventory เสร็จสมบูรณ์
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) {
                    giveDeathClockToHand(player, deathLoc);
                }
            });
        }
    }

    /**
     * ดักจับการคลิกขวาที่นาฬิกาเพื่อเทเลพอร์ตกลับจุดเสียชีวิตทันที
     */
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        // รับเฉพาะการคลิกด้วยมือหลัก
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.CLOCK || !item.hasItemMeta()) {
            return;
        }

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (!pdc.has(keyIsDeathClock, PersistentDataType.BYTE)) {
            return;
        }

        // ยกเลิก Action ปกติของเกม
        event.setCancelled(true);
        Player player = event.getPlayer();

        // ตรวจสอบเวลาหมดอายุ (120 วินาที)
        Long expiresAt = pdc.get(keyExpiresAt, PersistentDataType.LONG);
        if (expiresAt != null && System.currentTimeMillis() > expiresAt) {
            item.setAmount(0); // ลบไอเทมที่หมดอายุ
            player.playSound(player.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 1.0f, 1.0f);
            player.sendMessage(Component.text("⏰ นาฬิกาย้อนเวลานี้หมดอายุแล้ว (เกิน 120 วินาที) และสลายไป!", NamedTextColor.RED));
            return;
        }

        // ดึงพิกัดจากข้อมูลที่ฝังไว้ในตัวไอเทม (PDC)
        Location targetLoc = getLocationFromPdc(pdc);
        if (targetLoc == null) {
            // สำรอง: ดึงจาก map หน่วยความจำ
            targetLoc = lastDeathLocations.get(player.getUniqueId());
        }

        if (targetLoc == null || targetLoc.getWorld() == null) {
            player.sendMessage(Component.text("ไม่พบข้อมูลจุดเสียชีวิตล่าสุดของคุณ", NamedTextColor.RED));
            return;
        }

        // เทเลพอร์ตผู้เล่นไปยังจุดเสียชีวิตทันที
        player.teleport(targetLoc);
        player.playSound(targetLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);

        player.sendMessage(
            Component.text("⚡ ย้อนเวลากลับมายังจุดที่คุณเสียชีวิตเรียบร้อยแล้ว!", NamedTextColor.GREEN, TextDecoration.BOLD)
        );

        // ลดยอดการใช้งานลง 1 ชิ้น
        item.subtract(1);
    }

    /**
     * เสกนาฬิกาเข้าไปที่มือหลักของผู้เล่นโดยตรง พร้อมตั้งเวลานับถอยหลัง 120 วิ
     */
    public void giveDeathClockToHand(Player player, Location deathLoc) {
        ItemStack clock = createDeathClock(deathLoc);
        PlayerInventory inv = player.getInventory();

        // ถ้าที่มือหลักมีของอยู่ ให้ย้ายของเดิมเข้าช่องกระเป๋าอื่นก่อน
        ItemStack currentHandItem = inv.getItemInMainHand();
        if (currentHandItem != null && currentHandItem.getType() != Material.AIR) {
            inv.addItem(currentHandItem);
        }

        // วางนาฬิกาลงในมือหลักทันที
        inv.setItemInMainHand(clock);

        // แสดงข้อความและ Title บนหน้าจอ
        Title title = Title.title(
            Component.text("นาฬิกาย้อนเวลา", NamedTextColor.GOLD, TextDecoration.BOLD),
            Component.text("คลิกขวาในมือเพื่อวาร์ปกลับจุดตาย (หมดเวลาใน 120 วิ)", NamedTextColor.YELLOW),
            Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(3), Duration.ofMillis(500))
        );
        player.showTitle(title);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.5f);

        player.sendMessage(
            Component.text("คุณได้รับ ", NamedTextColor.GRAY)
                .append(Component.text("นาฬิกาย้อนเวลา", NamedTextColor.GOLD, TextDecoration.BOLD))
                .append(Component.text(" เข้ามือแล้ว! คลิกขวาเพื่อวาร์ปกลับจุดตายทันที (มีเวลา 120 วินาที)", NamedTextColor.YELLOW))
        );

        // แจ้งเตือนก่อนหมดเวลา 10 วินาที (2200 ticks = 110 วินาที)
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && hasDeathClock(player)) {
                player.sendMessage(Component.text("⚠️ นาฬิกาย้อนเวลาเหลือเวลาอีก 10 วินาทีก่อนจะสลายไป!", NamedTextColor.GOLD));
            }
        }, 110L * 20L);

        // เมื่อครบ 120 วินาที (2400 ticks) นาฬิกาจะหายไปอัตโนมัติ
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                removeExpiredClocks(player);
            }
        }, EXPIRE_TICKS);
    }

    /**
     * ตรวจสอบและลบนาฬิกาที่หมดอายุ (เกิน 120 วินาที) ออกจากตัวผู้เล่น
     */
    private void removeExpiredClocks(Player player) {
        boolean removed = false;
        long now = System.currentTimeMillis();

        PlayerInventory inv = player.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack item = inv.getItem(i);
            if (item != null && item.getType() == Material.CLOCK && item.hasItemMeta()) {
                ItemMeta meta = item.getItemMeta();
                PersistentDataContainer pdc = meta.getPersistentDataContainer();
                if (pdc.has(keyIsDeathClock, PersistentDataType.BYTE)) {
                    Long expiresAt = pdc.get(keyExpiresAt, PersistentDataType.LONG);
                    if (expiresAt != null && now >= expiresAt) {
                        inv.setItem(i, null);
                        removed = true;
                    }
                }
            }
        }

        if (removed) {
            player.playSound(player.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 1.0f, 1.0f);
            player.sendMessage(Component.text("⏰ นาฬิกาย้อนเวลาหมดอายุครบ 120 วินาทีแล้ว และสลายไปเรียบร้อยแล้ว!", NamedTextColor.RED));
        }
    }

    private boolean hasDeathClock(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == Material.CLOCK && item.hasItemMeta()) {
                if (item.getItemMeta().getPersistentDataContainer().has(keyIsDeathClock, PersistentDataType.BYTE)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * ฟังก์ชันสร้างนาฬิกา Enchant พร้อมบันทึกพิกัดและเวลาหมดอายุลงในตัวไอเทม
     */
    public ItemStack createDeathClock(Location loc) {
        ItemStack clock = new ItemStack(Material.CLOCK);
        ItemMeta meta = clock.getItemMeta();

        if (meta != null) {
            meta.displayName(
                Component.text("นาฬิกาย้อนเวลา", NamedTextColor.GOLD, TextDecoration.BOLD)
            );

            String worldName = loc.getWorld() != null ? loc.getWorld().getName() : "world";
            long expireTimeMillis = System.currentTimeMillis() + (EXPIRE_SECONDS * 1000L);

            meta.lore(Arrays.asList(
                Component.text("คลิกขวาเพื่อเทเลพอร์ตกลับไปยังจุดที่คุณเสียชีวิตล่าสุด", NamedTextColor.YELLOW),
                Component.text(
                    String.format("พิกัด: %s (X: %d, Y: %d, Z: %d)",
                        worldName, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()),
                    NamedTextColor.GRAY
                ),
                Component.text("⏳ มีเวลาใช้งาน 120 วินาที (2 นาที)", NamedTextColor.RED, TextDecoration.BOLD),
                Component.text("⚡ ใช้งานได้ 1 ครั้ง", NamedTextColor.DARK_GRAY)
            ));

            // แสงวิบวับแบบไอเทม Enchant
            meta.setEnchantmentGlintOverride(true);

            // บันทึกข้อมูลลง PersistentDataContainer ของไอเทม
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(keyIsDeathClock, PersistentDataType.BYTE, (byte) 1);
            pdc.set(keyExpiresAt, PersistentDataType.LONG, expireTimeMillis);
            pdc.set(keyWorld, PersistentDataType.STRING, worldName);
            pdc.set(keyX, PersistentDataType.DOUBLE, loc.getX());
            pdc.set(keyY, PersistentDataType.DOUBLE, loc.getY());
            pdc.set(keyZ, PersistentDataType.DOUBLE, loc.getZ());
            pdc.set(keyYaw, PersistentDataType.FLOAT, loc.getYaw());
            pdc.set(keyPitch, PersistentDataType.FLOAT, loc.getPitch());

            clock.setItemMeta(meta);
        }

        return clock;
    }

    private Location getLocationFromPdc(PersistentDataContainer pdc) {
        String worldName = pdc.get(keyWorld, PersistentDataType.STRING);
        Double x = pdc.get(keyX, PersistentDataType.DOUBLE);
        Double y = pdc.get(keyY, PersistentDataType.DOUBLE);
        Double z = pdc.get(keyZ, PersistentDataType.DOUBLE);
        Float yaw = pdc.get(keyYaw, PersistentDataType.FLOAT);
        Float pitch = pdc.get(keyPitch, PersistentDataType.FLOAT);

        if (worldName == null || x == null || y == null || z == null) {
            return null;
        }

        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return null;
        }

        return new Location(world, x, y, z, yaw != null ? yaw : 0f, pitch != null ? pitch : 0f);
    }

    public void giveDeathClock(Player player) {
        Location loc = lastDeathLocations.getOrDefault(player.getUniqueId(), player.getLocation());
        giveDeathClockToHand(player, loc);
    }
}
