package com.example.sevensins;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.*;

public final class SevenSinsPlugin extends JavaPlugin implements Listener, TabCompleter {
    private final Map<UUID, WrathBoss> bosses = new LinkedHashMap<>();
    private BossPacks packs;
    private WrathArmorBreak armorBreak;
    private WrathTremor tremor;
    private NamespacedKey entityKey;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        int configVersion = getConfig().getInt("config-version", 0);
        if (configVersion < 4) {
            if (configVersion < 2 && getConfig().getDouble("wrath.health", 600) == 600) getConfig().set("wrath.health", 1800);
            if (configVersion < 3) {
                if (getConfig().getDouble("wrath.damage-multiplier", 1) == 1) getConfig().set("wrath.damage-multiplier", 3);
                if (getConfig().getDouble("wrath.arena-radius", 28) == 28) getConfig().set("wrath.arena-radius", 140);
            }
            getConfig().set("config-version", 4); getConfig().options().copyDefaults(true); saveConfig();
        }
        entityKey = new NamespacedKey(this, "boss_entity");
        // Remove only leftovers bearing our own marker, e.g. after an interrupted reload.
        for (World world : Bukkit.getWorlds()) for (Entity e : world.getEntities())
            if (e.getPersistentDataContainer().has(entityKey, PersistentDataType.STRING)) e.remove();
        packs = new BossPacks(this); packs.start();
        armorBreak = new WrathArmorBreak(this);
        tremor = new WrathTremor(this);
        Bukkit.getPluginManager().registerEvents(this, this);
        Objects.requireNonNull(getCommand("7sins")).setTabCompleter(this);
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            armorBreak.tick();
            tremor.tick();
            for (WrathBoss boss : List.copyOf(bosses.values())) {
                try { boss.tick(); }
                catch (RuntimeException error) { getLogger().log(java.util.logging.Level.SEVERE, "Wrath encounter failed", error); boss.remove(); }
                if (boss.state() == WrathBoss.State.REMOVED) bosses.remove(boss.entity().getUniqueId());
            }
        }, 2, 2);
        getLogger().info("7sins: Wrath, the Ashen Executioner, is ready. /7sins spawn wrath");
    }

    @Override
    public void onDisable() {
        bosses.values().forEach(WrathBoss::remove); bosses.clear();
        if (armorBreak != null) armorBreak.close(); if (tremor != null) tremor.close(); if (packs != null) packs.close();
    }
    public BossPacks packs() { return packs; }
    public WrathArmorBreak armorBreak() { return armorBreak; }
    public WrathTremor tremor() { return tremor; }
    public double wrathScale() {
        double scale = getConfig().getDouble("wrath.model-scale", 5);
        return Double.isFinite(scale) ? Math.max(0.25, Math.min(8, scale)) : 5;
    }
    NamespacedKey entityKey() { return entityKey; }
    public Collection<WrathBoss> bosses() { return List.copyOf(bosses.values()); }
    public WrathBoss spawnWrath(Location location) {
        int limit = Math.max(1, Math.min(10, getConfig().getInt("wrath.max-active", 3)));
        if (bosses.size() >= limit) throw new IllegalStateException("จำนวนบอสเต็มแล้ว (" + limit + ")");
        for (WrathBoss boss : bosses.values()) if (boss.home().getWorld().equals(location.getWorld())
                && boss.home().distanceSquared(location) < 64 * 64) throw new IllegalStateException("มีบอสอยู่ใกล้เกินไป กรุณาห่างออกไป 64 บล็อก");
        WrathBoss boss = new WrathBoss(this, location); bosses.put(boss.entity().getUniqueId(), boss); return boss;
    }
    void refresh(Player player) { bosses.values().forEach(b -> b.refresh(player)); }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String action = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        if (action.equals("help")) {
            sender.sendMessage("§c7sins · WRATH — THE ASHEN EXECUTIONER");
            sender.sendMessage("§7/7sins pack §fรับแพ็กโมเดลบอส | §7/7sins list §fดูบอสที่กำลังทำงาน");
            if (sender.hasPermission("7sins.admin")) sender.sendMessage("§7/7sins spawn wrath §fเรียกบอสด้านหน้า | §7/7sins remove §fลบบอสที่ใกล้ที่สุด");
            sender.sendMessage("§6ฟันกวาด: อ้อมหลัง | ทุบพื้น: กระโดด | พุ่งชน: ล่อชนกำแพง | วงไฟ: เข้าวงใน");
            sender.sendMessage("§6กระทืบเท้า (โจมตีปกติ): กระโดดหรือถอย | ดาบจากพื้น: หลบวงแดง");
            sender.sendMessage("§cตอนเกิดระเบิด 200 ดาเมจในระยะ 40 บล็อก | โดนค้อน: ตรึง 1 วิ แล้วช้า 80% อีก 2 วิ");
            sender.sendMessage("§cโดนสกิลแล้วเกราะลด 60% นาน 8 วิ และเสียความทนทานเกราะ 25% — กระทืบปกติไม่มีผลนี้");
            sender.sendMessage("§eเฟสสอง: ช่วงยืนดูดซับ หยุดตี! ดาเมจโจมตีจะกลายเป็นเลือดจนหมดเวลาบน BossBar");
            return true;
        }
        if (action.equals("list")) {
            sender.sendMessage("§6[7sins] บอสที่กำลังทำงาน: " + bosses.size());
            for (WrathBoss b : bosses.values()) sender.sendMessage("§7Wrath · " + b.home().getWorld().getName() + " "
                    + b.home().getBlockX() + " " + b.home().getBlockY() + " " + b.home().getBlockZ()
                    + " · HP " + Math.round(b.health()) + " · " + b.state());
            return true;
        }
        if (!(sender instanceof Player player)) { sender.sendMessage("คำสั่งนี้ต้องใช้ในเกม"); return true; }
        if (action.equals("pack")) { packs.send(player); player.sendMessage("§6[7sins] " + packs.status(player)); return true; }
        if (!action.equals("spawn") && !action.equals("remove")) return false;
        if (!sender.hasPermission("7sins.admin")) { sender.sendMessage("§cต้องมีสิทธิ์ 7sins.admin"); return true; }
        if (action.equals("remove")) {
            WrathBoss nearest = bosses.values().stream().filter(b -> b.entity().getWorld().equals(player.getWorld())
                    && b.entity().getLocation().distanceSquared(player.getLocation()) <= 64 * 64)
                    .min(Comparator.comparingDouble(b -> b.entity().getLocation().distanceSquared(player.getLocation()))).orElse(null);
            if (nearest == null) player.sendMessage("§7ไม่มีบอสในระยะ 64 บล็อก");
            else { nearest.remove(); bosses.remove(nearest.entity().getUniqueId()); player.sendMessage("§6ลบ Wrath แล้ว"); }
            return true;
        }
        if (args.length > 2 || args.length == 2 && !args[1].equalsIgnoreCase("wrath")) return false;
        WrathSpawn.Result spawn = WrathSpawn.find(player.getLocation(),wrathScale());
        if (spawn.location() == null) {
            player.sendMessage("§c" + switch(spawn.failure()) {
                case NO_GROUND -> "ไม่พบพื้นรองรับด้านหน้า ลองลงใกล้พื้นหรือหันไปทางพื้นที่ที่มีพื้น";
                case UNEVEN_GROUND -> "พื้นด้านหน้าแคบหรือต่างระดับมาก ลองหันไปทางพื้นที่ราบกว่านี้";
                case BLOCKED -> "จุดเกิดด้านหน้าติดบล็อก เพดาน หรือของเหลว ต้องมีช่องโล่งสูงประมาณ " + (int)Math.ceil(4*wrathScale()) + " บล็อก";
            });
            return true;
        }
        try {
            spawnWrath(spawn.location()); player.sendMessage("§cWRATH ถูกปลุกแล้ว! §cระเบิดตอนเกิด 200 ดาเมจ! §7จากนั้นเริ่มไล่ใน 4 วินาที — ใช้ Survival เพื่อเข้าต่อสู้");
        } catch (IllegalStateException error) { player.sendMessage("§c[7sins] " + error.getMessage()); }
        return true;
    }
    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> choices = args.length == 1 ? sender.hasPermission("7sins.admin")
                ? List.of("spawn", "remove", "list", "pack", "help") : List.of("list", "pack", "help")
                : args.length == 2 && args[0].equalsIgnoreCase("spawn") ? List.of("wrath") : List.of();
        String prefix = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        return choices.stream().filter(s -> s.startsWith(prefix)).toList();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true) public void target(EntityTargetLivingEntityEvent e) {
        if (bosses.containsKey(e.getEntity().getUniqueId())) e.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true) public void transform(EntityTransformEvent e) {
        if (bosses.containsKey(e.getEntity().getUniqueId())) e.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true) public void damage(EntityDamageEvent e) {
        // Hidden native fangs provide the ground-spike base. Damage is handled once by the cast.
        Entity direct = e.getDamageSource().getDirectEntity();
        if (direct instanceof EvokerFangs && direct.getPersistentDataContainer().has(entityKey, PersistentDataType.STRING)
                || e instanceof EntityDamageByEntityEvent hit && hit.getDamager() instanceof EvokerFangs
                && hit.getDamager().getPersistentDataContainer().has(entityKey, PersistentDataType.STRING)) {
            e.setCancelled(true); return;
        }
        WrathBoss boss = bosses.get(e.getEntity().getUniqueId());
        if (boss == null) return;
        double scale = boss.damageScale();
        if (scale == 0) e.setCancelled(true);
        else {
            e.setDamage(e.getDamage() * scale);
            // A burst hit must leave the protected phase change available.
            double allowed = boss.phaseOneDamageLimit();
            if (e.getFinalDamage() > allowed && allowed >= 0)
                e.setDamage(e.getDamage() * allowed / e.getFinalDamage());
        }
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true) public void rage(EntityDamageEvent e) {
        WrathBoss boss = bosses.get(e.getEntity().getUniqueId());
        if (boss != null) {
            boss.attacked(e.getFinalDamage());
            if (e.getFinalDamage() > 0 && e.getDamageSource().getDirectEntity() instanceof Projectile
                    && e.getDamageSource().getCausingEntity() instanceof Player player) boss.rangedHit(player);
        }
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true) public void absorb(EntityDamageByEntityEvent e) {
        WrathBoss boss = bosses.get(e.getEntity().getUniqueId());
        if (boss == null || !boss.absorbing() || e.isCancelled()) return;
        double damage = e.getFinalDamage();
        e.setCancelled(true);
        boss.absorbDamage(damage);
    }
    @EventHandler(priority = EventPriority.MONITOR) public void outgoing(EntityDamageByEntityEvent e) {
        WrathBoss boss = bosses.get(e.getDamager().getUniqueId());
        if (boss != null && e.getEntity() instanceof Player player) boss.observeHit(player, !e.isCancelled(), e.getFinalDamage());
    }
    @EventHandler public void death(EntityDeathEvent e) {
        WrathBoss boss = bosses.remove(e.getEntity().getUniqueId());
        if (boss == null) return;
        e.getDrops().clear(); e.setDroppedExp(0); boss.killed(e.getEntity().getKiller());
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true) public void unload(ChunkUnloadEvent e) {
        for (WrathBoss boss : List.copyOf(bosses.values())) {
            if (boss.entity().getWorld().equals(e.getWorld()) && boss.entity().getLocation().getBlockX() >> 4 == e.getChunk().getX()
                    && boss.entity().getLocation().getBlockZ() >> 4 == e.getChunk().getZ()) {
                boss.remove(); bosses.remove(boss.entity().getUniqueId());
            }
        }
    }
}
