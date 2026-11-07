package com.example.forge;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

import static com.example.forge.AffixService.line;

/**
 * Charm items and the charms a player wears. Worn charms leave the inventory: their items are kept,
 * serialized, in the player's persistent data, so death never drops them and the menu can give them back.
 */
public final class CharmService implements Listener {
    public static final String NAMESPACE = "evergarden_forge";
    private static final TextColor TEXT = TextColor.color(0xE6DDF0), HINT = TextColor.color(0x8C8399);

    private final ForgePlugin plugin;
    private final NamespacedKey charmKey, gradeKey, wornKey;
    /** Worn charms by player, rebuilt from persistent data whenever it changes. */
    private final Map<UUID, EnumMap<Charm, Rarity>> worn = new HashMap<>();
    private final Map<UUID, Long> gestureCooldown = new HashMap<>();

    public CharmService(ForgePlugin plugin) {
        this.plugin = plugin;
        charmKey = new NamespacedKey(plugin, "charm");
        gradeKey = new NamespacedKey(plugin, "charm_grade");
        wornKey = new NamespacedKey(plugin, "charms_worn");
    }

    public int capacity() {
        return Math.clamp(plugin.getConfig().getInt("charms.capacity", 6), 1, CharmMenu.capacitySlots());
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("charms.enabled", true);
    }

    // ---- items -------------------------------------------------------------------------------

    public ItemStack item(Charm charm, Rarity grade) {
        ItemStack item = new ItemStack(Material.PAPER);
        item.editMeta(meta -> {
            PersistentDataContainer data = meta.getPersistentDataContainer();
            data.set(charmKey, PersistentDataType.STRING, charm.id());
            data.set(gradeKey, PersistentDataType.STRING, grade.name());
            meta.setMaxStackSize(1);
            meta.setItemModel(new NamespacedKey(NAMESPACE, "charm/" + charm.id()));
            meta.customName(line("✦ " + charm.title, charm.temple.color));
            meta.lore(lore(charm, grade, true));
            if (grade.ordinal() >= Rarity.EPIC.ordinal()) meta.setEnchantmentGlintOverride(true);
            meta.addItemFlags(ItemFlag.values());
        });
        return item;
    }

    List<Component> lore(Charm charm, Rarity grade, boolean howTo) {
        List<Component> lore = new ArrayList<>();
        lore.add(line("Charm · " + charm.temple.label, charm.temple.deep));
        lore.add(line("✦ เกรด " + grade.label + "  ", grade.color).append(line(cost(charm), charm.temple.color)));
        lore.add(Component.empty());
        for (String text : charm.describe(grade)) lore.add(line(" ▸ " + text, TEXT));
        if (howTo) {
            lore.add(Component.empty());
            lore.add(line("คลิกขวา หรือ ย่อ+ตี เพื่อสวมใส่", HINT));
            lore.add(line("/forge → Charm เพื่อถอดหรือจัดการ", HINT));
        }
        return lore;
    }

    static String cost(Charm charm) {
        return "◆".repeat(charm.cost) + " ใช้ " + charm.cost + " ช่อง";
    }

    public Charm charm(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return null;
        return Charm.parse(item.getItemMeta().getPersistentDataContainer().get(charmKey, PersistentDataType.STRING));
    }

    public Rarity grade(ItemStack item) {
        if (charm(item) == null) return null;
        Rarity grade = Rarity.parse(item.getItemMeta().getPersistentDataContainer().get(gradeKey, PersistentDataType.STRING));
        return grade == null ? Rarity.COMMON : grade;
    }

    public boolean isCharm(ItemStack item) {
        return charm(item) != null;
    }

    /** Rolls a charm of a temple (null = any). */
    public ItemStack roll(Charm.Temple temple, Rarity grade, Random random) {
        List<Charm> pool = temple == null ? List.of(Charm.values()) : temple.charms();
        return item(pool.get(random.nextInt(pool.size())), grade);
    }

    // ---- worn charms -------------------------------------------------------------------------

    private static final PersistentDataType<?, List<byte[]>> STORED = PersistentDataType.LIST.listTypeFrom(PersistentDataType.BYTE_ARRAY);

    /** The worn charm items in the order they were put on. */
    public List<ItemStack> wornItems(Player player) {
        List<byte[]> stored = player.getPersistentDataContainer().get(wornKey, STORED);
        List<ItemStack> items = new ArrayList<>();
        if (stored == null) return items;
        for (byte[] bytes : stored) {
            try {
                ItemStack item = ItemStack.deserializeBytes(bytes);
                if (isCharm(item)) items.add(item);
            } catch (RuntimeException error) {
                plugin.getLogger().warning("Dropped an unreadable worn charm of " + player.getName() + ": " + error);
            }
        }
        return items;
    }

    private void store(Player player, List<ItemStack> items) {
        if (items.isEmpty()) player.getPersistentDataContainer().remove(wornKey);
        else player.getPersistentDataContainer().set(wornKey, STORED, items.stream().map(ItemStack::serializeAsBytes).toList());
        worn.remove(player.getUniqueId());
        applyPassives(player);
    }

    /** Worn charm → grade, cached. Empty when charms are disabled. */
    public Map<Charm, Rarity> worn(Player player) {
        if (!enabled()) return Map.of();
        return worn.computeIfAbsent(player.getUniqueId(), id -> {
            EnumMap<Charm, Rarity> map = new EnumMap<>(Charm.class);
            for (ItemStack item : wornItems(player)) map.putIfAbsent(charm(item), grade(item));
            return map;
        });
    }

    public Rarity grade(Player player, Charm charm) {
        return worn(player).get(charm);
    }

    public boolean has(Player player, Charm charm) {
        return worn(player).containsKey(charm);
    }

    public int used(Player player) {
        int used = 0;
        for (ItemStack item : wornItems(player)) used += charm(item).cost;
        return used;
    }

    public enum EquipResult { OK, NOT_CHARM, DUPLICATE, NO_ROOM, DISABLED }

    /** Puts a charm on. The caller removes the item from wherever it was only when this returns OK. */
    public EquipResult equip(Player player, ItemStack item) {
        if (!enabled()) return EquipResult.DISABLED;
        Charm charm = charm(item);
        if (charm == null) return EquipResult.NOT_CHARM;
        List<ItemStack> items = wornItems(player);
        for (ItemStack wornItem : items) if (charm(wornItem) == charm) return EquipResult.DUPLICATE;
        if (used(player) + charm.cost > capacity()) return EquipResult.NO_ROOM;
        ItemStack single = item.clone();
        single.setAmount(1);
        items.add(single);
        store(player, items);
        Rarity grade = grade(single);
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.8f, 1.3f);
        player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_CHAIN, 0.6f, 1.2f);
        player.getWorld().spawnParticle(Particle.END_ROD, player.getLocation().add(0, 1.1, 0), 16, 0.35, 0.5, 0.35, 0.03);
        player.sendActionBar(line("✦ สวม " + charm.title + " · " + grade.label + "  ", charm.temple.color)
            .append(line("ช่อง " + used(player) + "/" + capacity(), NamedTextColor.GRAY)));
        plugin.getLogger().info("[Forge-Charm] equip player=" + player.getName() + " charm=" + charm.id() + " grade=" + grade);
        return EquipResult.OK;
    }

    /** Takes off the worn charm at an index and returns its item, or null. */
    public ItemStack unequip(Player player, int index) {
        List<ItemStack> items = wornItems(player);
        if (index < 0 || index >= items.size()) return null;
        ItemStack removed = items.remove(index);
        store(player, items);
        player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_GENERIC, 0.7f, 0.9f);
        plugin.getLogger().info("[Forge-Charm] unequip player=" + player.getName() + " charm=" + charm(removed).id());
        return removed;
    }

    public void explain(Player player, EquipResult result, Charm charm) {
        switch (result) {
            case DUPLICATE -> player.sendMessage(line("✦ สวม " + charm.title + " อยู่แล้ว · สวมชิ้นเดียวกันซ้ำไม่ได้", NamedTextColor.RED));
            case NO_ROOM -> player.sendMessage(line("✦ ช่อง Charm ไม่พอ (ใช้ " + used(player) + "/" + capacity() + " ต้องการ " + charm.cost
                + ") · ถอดชิ้นอื่นที่ /forge → Charm", NamedTextColor.RED));
            case DISABLED -> player.sendMessage(line("✦ ระบบ Charm ปิดอยู่", NamedTextColor.RED));
            default -> { }
        }
        if (result != EquipResult.OK) player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.6f);
    }

    /** Lets the effects drop what a charm that came off had changed (flight, invisibility...). */
    public void applyPassives(Player player) {
        if (plugin.charmEffects() != null) plugin.charmEffects().wornChanged(player);
    }

    // ---- events ------------------------------------------------------------------------------

    /** Right click (Java and Bedrock) or sneak + punch (Bedrock touch) puts the held charm on. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Player player = event.getPlayer();
        ItemStack held = player.getInventory().getItemInMainHand();
        Charm charm = charm(held);
        if (charm == null) return;
        Action action = event.getAction();
        boolean use = action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;
        boolean punch = player.isSneaking() && (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK);
        if (!use && !punch) return;
        // A chest, door or station clicked while holding a charm still opens.
        if (action == Action.RIGHT_CLICK_BLOCK && !player.isSneaking() && event.getClickedBlock() != null
            && event.getClickedBlock().getType().isInteractable()) return;
        event.setUseItemInHand(Event.Result.DENY);
        event.setUseInteractedBlock(Event.Result.DENY);
        long now = System.currentTimeMillis();
        // One click can arrive as both an air and a block interaction.
        if (now < gestureCooldown.getOrDefault(player.getUniqueId(), 0L)) return;
        gestureCooldown.put(player.getUniqueId(), now + 350);
        EquipResult result = equip(player, held);
        if (result == EquipResult.OK) {
            if (held.getAmount() > 1) held.setAmount(held.getAmount() - 1);
            else player.getInventory().setItemInMainHand(null);
        } else {
            explain(player, result, charm);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        worn.remove(event.getPlayer().getUniqueId());
        applyPassives(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        worn.remove(event.getPlayer().getUniqueId());
        gestureCooldown.remove(event.getPlayer().getUniqueId());
    }

    /** Rebuilds caches (config reload toggled charms). */
    public void refreshAll() {
        worn.clear();
        for (Player player : plugin.getServer().getOnlinePlayers()) applyPassives(player);
    }
}
