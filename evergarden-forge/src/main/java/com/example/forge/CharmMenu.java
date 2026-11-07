package com.example.forge;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.example.forge.AffixService.line;

/**
 * The Charm page of the forge: slot meter, worn charms (click to take off), the player's inventory
 * (click a charm to put it on) and a codex of each sanctum's nine charms. Nothing here is a real item
 * except what the player clicks in their own inventory.
 */
public final class CharmMenu implements Listener {
    private static final int SIZE = 54, BACK = 0, HEADER = 4, CLOSE = 8, METER = 13, GUIDE = 22, CODEX_LABEL = 31, HELP = 45, CLEAR = 53;
    private static final int[] NOTCH = {10, 11, 12, 14, 15, 16};
    private static final int[] WORN = {19, 20, 21, 23, 24, 25};
    private static final int[] CODEX = {36, 37, 38, 39, 40, 41, 42, 43, 44};
    private static final int[] TEMPLE_TAB = {47, 49, 51};
    private static final TextColor TITLE = TextColor.color(0x4B2A7A);

    private final ForgePlugin plugin;

    public CharmMenu(ForgePlugin plugin) {
        this.plugin = plugin;
    }

    private final class Holder implements InventoryHolder {
        final Player owner;
        Inventory inventory;
        Charm.Temple temple = Charm.Temple.DARK;
        long clearArmedUntil;

        Holder(Player owner) {
            this.owner = owner;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    public static int capacitySlots() {
        return NOTCH.length;
    }

    public void open(Player player) {
        if (!player.hasPermission("forge.use") || player.isDead()) return;
        Holder holder = new Holder(player);
        holder.inventory = Bukkit.createInventory(holder, SIZE, Component.text("✦ โรงตีเหล็ก · Charm", TITLE));
        redraw(holder);
        player.openInventory(holder.inventory);
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.6f, 1.1f);
    }

    public void closeAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof Holder) player.closeInventory();
        }
    }

    // ---- drawing -----------------------------------------------------------------------------

    private ItemStack icon(String namespace, String model, String name, TextColor color, List<Component> lore) {
        ItemStack item = new ItemStack(Material.PAPER);
        item.editMeta(meta -> {
            meta.setItemModel(new NamespacedKey(namespace, model));
            if (name == null) {
                meta.setHideTooltip(true);
            } else {
                meta.customName(line(name, color));
                meta.lore(lore);
            }
            meta.addItemFlags(ItemFlag.values());
        });
        return item;
    }

    private ItemStack menuIcon(String model, String name, TextColor color, List<String> lore) {
        return icon(plugin.settings().iconNamespace, model, name, color, gray(lore));
    }

    private ItemStack charmIcon(String model, String name, TextColor color, List<Component> lore) {
        return icon(CharmService.NAMESPACE, model, name, color, lore);
    }

    private static List<Component> gray(List<String> lines) {
        return lines.stream().map(text -> line(text, NamedTextColor.GRAY)).toList();
    }

    private void redraw(Holder holder) {
        Inventory inv = holder.inventory;
        Player player = holder.owner;
        CharmService charms = plugin.charms();
        ItemStack panel = menuIcon("skilltree_panel", null, null, List.of());
        for (int i = 0; i < SIZE; i++) inv.setItem(i, panel);

        List<ItemStack> worn = charms.wornItems(player);
        int capacity = Math.min(charms.capacity(), NOTCH.length), used = charms.used(player);

        inv.setItem(BACK, menuIcon("menu_back", "กลับโรงตีเหล็ก", NamedTextColor.GOLD, List.of("รีฟอร์จ · ย่อย · Gem")));
        inv.setItem(CLOSE, menuIcon("skilltree_close", "ปิด", NamedTextColor.RED, List.of()));
        List<Component> header = new ArrayList<>();
        header.add(line("ช่องที่ใช้ " + used + "/" + capacity + "  ·  สวมอยู่ " + worn.size() + " ชิ้น", NamedTextColor.GRAY));
        header.add(Component.empty());
        if (worn.isEmpty()) header.add(line("ยังไม่ได้สวม Charm", NamedTextColor.DARK_GRAY));
        for (ItemStack item : worn) {
            Charm charm = charms.charm(item);
            Rarity grade = charms.grade(item);
            header.add(line(" ✦ " + charm.title + "  ", charm.temple.color).append(line(grade.label + " · " + "◆".repeat(charm.cost), grade.color)));
        }
        inv.setItem(HEADER, glint(charmIcon("charm_ui/tab", "Charm ของคุณ", TextColor.color(0xF1E6DB), header)));

        // Slot meter: each worn charm paints as many notches as it costs, in its sanctum colour.
        List<Charm.Temple> notchOwner = new ArrayList<>();
        for (ItemStack item : worn) {
            Charm charm = charms.charm(item);
            for (int i = 0; i < charm.cost; i++) notchOwner.add(charm.temple);
        }
        for (int i = 0; i < NOTCH.length; i++) {
            if (i >= capacity) {
                inv.setItem(NOTCH[i], menuIcon("skilltree_lock", "ช่องล็อก", NamedTextColor.DARK_GRAY, List.of()));
            } else if (i < notchOwner.size()) {
                Charm.Temple temple = notchOwner.get(i);
                inv.setItem(NOTCH[i], charmIcon("charm_ui/notch_" + temple.id(), "ช่อง " + (i + 1) + " · " + temple.label, temple.color,
                    List.of(line("ใช้งานอยู่", NamedTextColor.GRAY))));
            } else {
                inv.setItem(NOTCH[i], charmIcon("charm_ui/notch_empty", "ช่อง " + (i + 1) + " · ว่าง", NamedTextColor.WHITE,
                    List.of(line("Charm แต่ละชิ้นใช้ 1–3 ช่อง", NamedTextColor.GRAY))));
            }
        }
        inv.setItem(METER, charmIcon("charm_ui/meter", "ช่อง Charm " + used + "/" + capacity, used >= capacity ? NamedTextColor.GOLD : NamedTextColor.WHITE,
            gray(List.of("เหลือ " + (capacity - used) + " ช่อง", "◆ = 1 ช่อง · ชิ้นแรงใช้หลายช่อง"))));

        for (int i = 0; i < WORN.length; i++) {
            if (i < worn.size()) {
                ItemStack item = worn.get(i);
                Charm charm = charms.charm(item);
                Rarity grade = charms.grade(item);
                List<Component> lore = new ArrayList<>(charms.lore(charm, grade, false));
                lore.add(Component.empty());
                lore.add(line("▶ คลิกเพื่อถอดกลับเข้ากระเป๋า", NamedTextColor.YELLOW));
                ItemStack shown = charms.item(charm, grade);
                shown.editMeta(meta -> meta.lore(lore));
                inv.setItem(WORN[i], shown);
            } else {
                inv.setItem(WORN[i], charmIcon("charm_ui/slot_empty", "ช่องว่าง", NamedTextColor.GRAY,
                    gray(List.of("คลิก Charm ในกระเป๋าด้านล่างเพื่อสวม", "หรือถือ Charm แล้วคลิกขวา / ย่อ+ตี"))));
            }
        }
        inv.setItem(GUIDE, menuIcon("skilltree_help", "สวม / ถอด", NamedTextColor.AQUA, List.of(
            "สวม: คลิก Charm ในกระเป๋าของคุณ",
            "ถอด: คลิก Charm ในแถวด้านบน",
            "สวมชิ้นเดียวกันซ้ำไม่ได้",
            "Charm ที่สวมไม่หล่นเมื่อตาย")));

        Charm.Temple temple = holder.temple;
        inv.setItem(CODEX_LABEL, charmIcon("charm_ui/temple_" + temple.id(), "คลัง Charm · " + temple.label, temple.color,
            gray(List.of("Charm 9 ชิ้นที่ได้จาก" + temple.label, "ตัวเลขแสดงช่วง Worn → Divine"))));
        Map<Charm, Rarity> active = charms.worn(player);
        List<Charm> list = temple.charms();
        for (int i = 0; i < CODEX.length && i < list.size(); i++) {
            Charm charm = list.get(i);
            List<Component> lore = new ArrayList<>();
            lore.add(line("Charm · " + charm.temple.label, charm.temple.deep));
            lore.add(line(CharmService.cost(charm), charm.temple.color));
            lore.add(Component.empty());
            for (String text : charm.describeRange()) lore.add(line(" ▸ " + text, TextColor.color(0xE6DDF0)));
            if (active.containsKey(charm)) {
                lore.add(Component.empty());
                lore.add(line("✔ สวมอยู่ · " + active.get(charm).label, NamedTextColor.GREEN));
            }
            ItemStack entry = charmIcon("charm/" + charm.id(), "✦ " + charm.title, charm.temple.color, lore);
            inv.setItem(CODEX[i], active.containsKey(charm) ? glint(entry) : entry);
        }
        Charm.Temple[] temples = Charm.Temple.values();
        for (int i = 0; i < temples.length; i++) {
            Charm.Temple tab = temples[i];
            ItemStack icon = charmIcon("charm_ui/temple_" + tab.id(), tab.label, tab.color,
                gray(List.of(tab == temple ? "กำลังแสดง" : "คลิกเพื่อดู Charm ของวิหารนี้")));
            inv.setItem(TEMPLE_TAB[i], tab == temple ? glint(icon) : icon);
        }
        inv.setItem(HELP, menuIcon("skilltree_help", "Charm คืออะไร", NamedTextColor.AQUA, List.of(
            "เครื่องรางจากวิหารทั้งสาม ให้พลังพิเศษขณะสวม",
            "มีช่องรวม " + capacity + " ช่อง · Charm ใช้ 1–3 ช่อง",
            "เกรด Worn → Divine ยิ่งสูงยิ่งแรง",
            "วิหารความมืด: แลกเปลี่ยนเพื่อพลัง",
            "วิหารดวงดาว: เวทมนตร์และ Gem",
            "วิหารกาลเวลา: จังหวะและการย้อนเวลา")));
        boolean armed = System.currentTimeMillis() < holder.clearArmedUntil;
        inv.setItem(CLEAR, worn.isEmpty() ? panel : menuIcon(armed ? "menu_cancel_armed" : "menu_cancel",
            armed ? "คลิกอีกครั้งเพื่อยืนยัน" : "ถอดทั้งหมด", NamedTextColor.RED, List.of("ต้องมีที่ว่างในกระเป๋า")));
    }

    private static ItemStack glint(ItemStack item) {
        item.editMeta(meta -> meta.setEnchantmentGlintOverride(true));
        return item;
    }

    // ---- events ------------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || player != holder.owner || player.isDead()) return;
        ClickType click = event.getClick();
        if (click != ClickType.LEFT && click != ClickType.RIGHT && click != ClickType.SHIFT_LEFT && click != ClickType.SHIFT_RIGHT) return;
        int raw = event.getRawSlot();
        CharmService charms = plugin.charms();
        if (raw >= SIZE) {
            if (!(event.getClickedInventory() instanceof PlayerInventory inventory)) return;
            ItemStack item = event.getCurrentItem();
            Charm charm = charms.charm(item);
            if (charm == null) return;
            CharmService.EquipResult result = charms.equip(player, item);
            if (result == CharmService.EquipResult.OK) {
                if (item.getAmount() > 1) {
                    item.setAmount(item.getAmount() - 1);
                    inventory.setItem(event.getSlot(), item);
                } else {
                    inventory.setItem(event.getSlot(), null);
                }
            } else {
                charms.explain(player, result, charm);
            }
            redraw(holder);
            return;
        }
        if (raw == CLOSE) {
            player.closeInventory();
            return;
        }
        if (raw == BACK) {
            plugin.menu().open(player, ForgeMenu.Page.REFORGE);
            return;
        }
        for (int i = 0; i < WORN.length; i++) {
            if (raw != WORN[i]) continue;
            if (i >= charms.wornItems(player).size()) return;
            if (player.getInventory().firstEmpty() < 0) {
                player.sendMessage(line("✦ กระเป๋าเต็ม · เว้นที่ว่าง 1 ช่องก่อนถอด Charm", NamedTextColor.RED));
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.6f);
                return;
            }
            ItemStack removed = charms.unequip(player, i);
            if (removed != null) player.getInventory().addItem(removed);
            redraw(holder);
            return;
        }
        Charm.Temple[] temples = Charm.Temple.values();
        for (int i = 0; i < TEMPLE_TAB.length; i++) {
            if (raw == TEMPLE_TAB[i] && holder.temple != temples[i]) {
                holder.temple = temples[i];
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.3f);
                redraw(holder);
                return;
            }
        }
        if (raw == CLEAR && !charms.wornItems(player).isEmpty()) {
            long now = System.currentTimeMillis();
            if (now >= holder.clearArmedUntil) {
                holder.clearArmedUntil = now + 3000;
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 0.8f);
                redraw(holder);
                return;
            }
            holder.clearArmedUntil = 0;
            while (!charms.wornItems(player).isEmpty() && player.getInventory().firstEmpty() >= 0) {
                ItemStack removed = charms.unequip(player, charms.wornItems(player).size() - 1);
                if (removed != null) player.getInventory().addItem(removed);
            }
            if (!charms.wornItems(player).isEmpty())
                player.sendMessage(line("✦ กระเป๋าเต็ม · ถอดได้ไม่ครบทุกชิ้น", NamedTextColor.RED));
            redraw(holder);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) event.setCancelled(true);
    }
}
