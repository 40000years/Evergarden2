package com.example.forge;

import com.example.forge.GemService.Gem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import static com.example.forge.AffixService.line;

/**
 * The forge: chest menus standing in for Apotheosis' reforging, salvaging and gem tables, drawn
 * with the Skill Tree / menu icon set from the Advance Magic pack. Real items only ever sit in the
 * input slots of the open page; everything else is a display copy and every click on it is cancelled.
 */
public final class ForgeMenu implements Listener {
    public enum Page {
        REFORGE("รีฟอร์จ"), SALVAGE("ย่อยอุปกรณ์"), GEMS("ใส่ Gem"), CUT("เจียระไน Gem"), CODEX("คลัง Gem");

        final String label;

        Page(String thai) {
            this.label = thai;
        }
    }

    private static final int SIZE = 54;
    private static final TextColor TITLE = TextColor.color(0x4B2A7A);
    private static final int HELP = 0, CLOSE = 8, INFO = 49, CHARM_TAB = 7;
    private static final int[] TABS = {2, 3, 4, 5, 6};
    private static final String[] TAB_ICONS = {"menu_upgrade", "menu_extract", "menu_socket", "menu_skills", "menu_codex"};
    // reforge
    private static final int GEAR = 20, MATERIAL = 38, GEAR_LABEL = 11, MATERIAL_LABEL = 29;
    private static final int[] CHOICE = {15, 24, 33};
    private static final int[] BADGE = {16, 25, 34};
    // salvage
    private static final int[] SALVAGE_INPUT = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
    private static final int SALVAGE_PREVIEW = 40;
    // gems
    private static final int[] SOCKET = {23, 24, 25};
    private static final int SELECTED = 41;
    // cutting
    private static final int CUT_GEM = 20, CUT_DUST = 22, CUT_MATERIAL = 24, CUT_RESULT = 40;
    // codex
    private static final int[] CODEX_GEMS = {19, 20, 21, 22, 23, 24, 25, 31};
    private static final int[] CODEX_TIERS = {47, 48, 49, 50, 51};

    private final ForgePlugin plugin;

    public ForgeMenu(ForgePlugin plugin) {
        this.plugin = plugin;
    }

    private final class Holder implements InventoryHolder {
        final Page page;
        final Player owner;
        Inventory inventory;
        long armedUntil;
        /** Player inventory slot of the gem chosen on the gem page, or -1. */
        int selected = -1;
        int codexTier = 1;

        Holder(Page page, Player owner) {
            this.page = page;
            this.owner = owner;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        boolean armed() {
            return System.currentTimeMillis() < armedUntil;
        }
    }

    public void open(Player player, Page page) {
        if (!player.hasPermission("forge.use") || player.isDead()) return;
        Holder holder = new Holder(page, player);
        holder.inventory = Bukkit.createInventory(holder, SIZE, Component.text("✦ โรงตีเหล็ก · " + page.label, TITLE));
        redraw(holder);
        player.openInventory(holder.inventory);
        player.playSound(player.getLocation(), Sound.BLOCK_SMITHING_TABLE_USE, 0.6f, 1.2f);
    }

    /** Closing returns the inputs, so call this before the plugin disables. */
    public void closeAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof Holder) player.closeInventory();
        }
    }

    // ---- drawing -----------------------------------------------------------------------------

    private ItemStack icon(String model, String name, TextColor color, List<String> lore) {
        ItemStack item = new ItemStack(Material.PAPER);
        item.editMeta(meta -> {
            meta.setItemModel(new NamespacedKey(plugin.settings().iconNamespace, model));
            if (name == null) {
                meta.setHideTooltip(true);
            } else {
                meta.customName(line(name, color));
                meta.lore(lore.stream().map(text -> line(text, NamedTextColor.GRAY)).toList());
            }
            meta.addItemFlags(ItemFlag.values());
        });
        return item;
    }

    private static ItemStack glint(ItemStack item) {
        item.editMeta(meta -> meta.setEnchantmentGlintOverride(true));
        return item;
    }

    private static ItemStack withLore(ItemStack item, List<Component> extra) {
        ItemStack copy = item.clone();
        copy.editMeta(meta -> {
            List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
            lore.addAll(extra);
            meta.lore(lore);
        });
        return copy;
    }

    private static boolean contains(int[] slots, int slot) {
        for (int s : slots) if (s == slot) return true;
        return false;
    }

    private boolean input(Holder holder, int slot) {
        return switch (holder.page) {
            case REFORGE -> slot == GEAR || slot == MATERIAL;
            case SALVAGE -> contains(SALVAGE_INPUT, slot);
            case GEMS -> slot == GEAR;
            case CUT -> slot == CUT_GEM || slot == CUT_DUST || slot == CUT_MATERIAL;
            case CODEX -> false;
        };
    }

    private void redraw(Holder holder) {
        Inventory inv = holder.inventory;
        ItemStack panel = icon("skilltree_panel", null, null, List.of());
        for (int i = 0; i < SIZE; i++) if (!input(holder, i)) inv.setItem(i, panel);
        inv.setItem(HELP, icon("skilltree_help", "วิธีใช้โรงตีเหล็ก", NamedTextColor.AQUA, List.of(
            "รีฟอร์จ: อุปกรณ์ + วัสดุช่าง → เลือก 1 ใน 3 ผลลัพธ์",
            "ย่อย: อุปกรณ์ → วัสดุช่าง, Gem → ผงอัญมณี",
            "ใส่ Gem: อุปกรณ์ที่มีเกรดมีช่อง Gem 0–3 ช่อง",
            "เจียระไน: Gem เท่ากัน 2 เม็ด + ผง + วัสดุ → ขั้นถัดไป",
            "คลัง Gem: ดูว่า Gem แต่ละเม็ดให้อะไรกับอุปกรณ์ไหน",
            "Charm: สวม / ถอดเครื่องรางจากวิหาร",
            "อาวุธตำนาน ชุดบอส คทา และ Relic ใช้ที่นี่ไม่ได้")));
        Page[] pages = Page.values();
        for (int i = 0; i < pages.length; i++) {
            ItemStack tab = icon(TAB_ICONS[i], pages[i].label, NamedTextColor.GOLD, List.of(pages[i] == holder.page ? "หน้าปัจจุบัน" : "คลิกเพื่อเปิด"));
            inv.setItem(TABS[i], pages[i] == holder.page ? glint(tab) : tab);
        }
        ItemStack charmTab = new ItemStack(Material.PAPER);
        charmTab.editMeta(meta -> {
            meta.setItemModel(new NamespacedKey(CharmService.NAMESPACE, "charm_ui/tab"));
            meta.customName(line("Charm", TextColor.color(0xF2C35B)));
            meta.lore(List.of(line("เครื่องรางจากวิหารทั้งสาม · สวม / ถอด", NamedTextColor.GRAY), line("คลิกเพื่อเปิด", NamedTextColor.GRAY)));
            meta.addItemFlags(ItemFlag.values());
        });
        inv.setItem(CHARM_TAB, charmTab);
        inv.setItem(CLOSE, icon("skilltree_close", "ปิด", NamedTextColor.RED, List.of("ของในช่องวางจะคืนเข้ากระเป๋า")));
        switch (holder.page) {
            case REFORGE -> drawReforge(holder);
            case SALVAGE -> drawSalvage(holder);
            case GEMS -> drawGems(holder);
            case CUT -> drawCut(holder);
            case CODEX -> drawCodex(holder);
        }
    }

    private void drawReforge(Holder holder) {
        Inventory inv = holder.inventory;
        inv.setItem(GEAR_LABEL, icon("menu_pick", "ช่องอุปกรณ์ ↓", NamedTextColor.WHITE,
            List.of("อาวุธ ธนู โล่ เครื่องมือ เบ็ด ชุดเกราะ หรือคทาเวท", "อาวุธตำนานและชุดเกราะเซตก็รีฟอร์จได้ (ย่อยไม่ได้)", "อุปกรณ์ธรรมดาก็รีฟอร์จให้มีเกรดได้", "Gem ที่ใส่อยู่ไม่หาย ช่อง Gem ไม่ลดลง")));
        List<String> materialLore = new ArrayList<>(List.of("วัสดุกำหนดเกรดของผลลัพธ์:"));
        for (Rarity rarity : Rarity.values()) materialLore.add(rarity.materialName + " → เกรด " + rarity.label);
        inv.setItem(MATERIAL_LABEL, icon("menu_socket", "ช่องวัสดุช่าง ↓", NamedTextColor.WHITE, materialLore));

        Player player = holder.owner;
        ItemStack gear = inv.getItem(GEAR);
        ItemStack material = inv.getItem(MATERIAL);
        AffixService affixes = plugin.affixes();
        ReforgeService reforge = plugin.reforge();
        String blocked = null;
        Rarity rarity = affixes.materialRarity(material);
        if (empty(gear)) blocked = "วางอุปกรณ์ในช่องอุปกรณ์ก่อน";
        else if (affixes.category(gear) == null) blocked = "อุปกรณ์นี้รีฟอร์จไม่ได้";
        else if (rarity == null) blocked = "วางวัสดุช่างเพื่อเลือกเกรด";
        else if (!reforge.allowed(rarity)) blocked = "เกรด " + rarity.label + " ยังรีฟอร์จไม่ได้";

        List<ItemStack> choices = blocked == null ? reforge.choices(player, gear, rarity) : List.of();
        for (int i = 0; i < ReforgeService.CHOICES; i++) {
            if (choices.isEmpty()) {
                inv.setItem(CHOICE[i], icon("skilltree_lock", "ตัวเลือก " + (i + 1), NamedTextColor.DARK_GRAY, List.of(blocked == null ? "" : blocked)));
                inv.setItem(BADGE[i], icon("skilltree_panel", null, null, List.of()));
                continue;
            }
            ReforgeService.Cost cost = reforge.cost(rarity, i);
            boolean enoughMaterial = material.getAmount() >= cost.materials();
            boolean enoughLevels = free(player) || player.getLevel() >= cost.levels();
            boolean ok = enoughMaterial && enoughLevels;
            inv.setItem(CHOICE[i], withLore(choices.get(i), List.of(Component.empty(),
                line("ต้นทุน: " + rarity.materialName + " ×" + cost.materials(), enoughMaterial ? NamedTextColor.GREEN : NamedTextColor.RED),
                line("        " + cost.levels() + " เลเวล", enoughLevels ? NamedTextColor.GREEN : NamedTextColor.RED),
                line(ok ? "คลิกเพื่อเลือกผลลัพธ์นี้" : "ทรัพยากรไม่พอ", NamedTextColor.YELLOW))));
            inv.setItem(BADGE[i], icon(ok ? "menu_check_ok" : "menu_check_no", "ตัวเลือก " + (i + 1),
                ok ? NamedTextColor.GREEN : NamedTextColor.RED,
                List.of(rarity.materialName + " ×" + cost.materials() + " · " + cost.levels() + " เลเวล")));
        }
        int[] multipliers = plugin.settings().choiceMultipliers;
        inv.setItem(INFO, icon("menu_stats", "กติกาการรีฟอร์จ", NamedTextColor.LIGHT_PURPLE, List.of(
            "ผลลัพธ์ทั้งสามไม่ได้เรียงตามความแรง",
            "ต้นทุนตัวเลือก 1 / 2 / 3 = ×" + multipliers[0] + " / ×" + multipliers[1] + " / ×" + multipliers[2],
            "รีฟอร์จแล้วชุดผลลัพธ์ถัดไปจะเปลี่ยน",
            "ชื่อ เอนชานต์ Limit Break Unique Enchant และ Gem อยู่ครบ",
            "เลเวลของคุณ: " + player.getLevel())));
    }

    private void drawSalvage(Holder holder) {
        Inventory inv = holder.inventory;
        Map<Rarity, Integer> counts = new EnumMap<>(Rarity.class);
        int plain = 0, dust = 0, gemsBack = 0, ignored = 0;
        for (int slot : SALVAGE_INPUT) {
            ItemStack item = inv.getItem(slot);
            if (empty(item)) continue;
            Gem gem = plugin.gems().read(item);
            if (gem != null) {
                dust += plugin.gems().salvageDust(gem.tier()) * item.getAmount();
                continue;
            }
            if (!plugin.affixes().salvageable(item)) {
                ignored++;
                continue;
            }
            AffixData data = plugin.affixes().read(item);
            if (data == null) plain++;
            else counts.merge(data.rarity(), 1, Integer::sum);
            for (Gem socketed : plugin.affixes().gems(item)) if (socketed != null) gemsBack++;
        }
        List<String> lore = new ArrayList<>();
        counts.forEach((rarity, count) -> {
            ForgeSettings.Grade grade = plugin.settings().grade(rarity);
            lore.add(rarity.label + " ×" + count + " → " + rarity.materialName + " " + grade.salvageMin() * count + "–" + grade.salvageMax() * count);
        });
        if (plain > 0) lore.add("อุปกรณ์ธรรมดา ×" + plain + " → " + Rarity.COMMON.materialName + " 0–" + plain
            + " (โอกาส " + Math.round(plugin.settings().salvagePlainChance * 100) + "% ต่อชิ้น)");
        if (dust > 0) lore.add("Gem → ผงอัญมณี ×" + dust);
        if (gemsBack > 0) lore.add("Gem ที่ใส่อยู่ " + gemsBack + " เม็ด คืนเข้ากระเป๋า");
        boolean any = !lore.isEmpty();
        if (!any) lore.add("วางอุปกรณ์หรือ Gem ในช่องว่างด้านบน");
        if (ignored > 0) lore.add(ignored + " ชิ้นย่อยไม่ได้ (อาวุธตำนาน ชุดเกราะเซต คทา ของพิเศษ) จะคืนตอนปิดเมนู");
        inv.setItem(SALVAGE_PREVIEW, icon("menu_stats", "ผลที่จะได้รับ", NamedTextColor.AQUA, lore));
        if (!any) {
            inv.setItem(INFO, icon("menu_upgrade_off", "ย่อย", NamedTextColor.DARK_GRAY, List.of("ยังไม่มีของที่ย่อยได้")));
        } else if (holder.armed()) {
            inv.setItem(INFO, icon("menu_upgrade_armed", "คลิกอีกครั้งเพื่อยืนยัน", NamedTextColor.RED,
                List.of("อุปกรณ์และ Gem ในช่องจะหายไปและเปลี่ยนเป็นวัสดุ", "Gem ที่ใส่ในอุปกรณ์จะคืนให้ เอนชานต์ไม่คืน")));
        } else {
            inv.setItem(INFO, icon("menu_upgrade", "ย่อย", NamedTextColor.GOLD, List.of("คลิกสองครั้งภายใน 5 วินาทีเพื่อย่อย")));
        }
    }

    private void drawGems(Holder holder) {
        Inventory inv = holder.inventory;
        inv.setItem(GEAR_LABEL, icon("menu_pick", "ช่องอุปกรณ์ ↓", NamedTextColor.WHITE,
            List.of("วางอุปกรณ์ที่มีเกรด", "ช่อง Gem: Tempered 0–1 · Arcane 1 · Exalted 1–2 · Divine 2–3")));
        ItemStack gear = inv.getItem(GEAR);
        LootCategory category = plugin.affixes().category(gear);
        List<Gem> gems = category == null ? List.of() : plugin.affixes().gems(gear);
        Gem selected = selectedGem(holder);
        for (int i = 0; i < SOCKET.length; i++) {
            if (category == null || plugin.affixes().read(gear) == null) {
                inv.setItem(SOCKET[i], icon("skilltree_lock", "ช่อง Gem " + (i + 1), NamedTextColor.DARK_GRAY,
                    List.of(empty(gear) ? "วางอุปกรณ์ก่อน" : "อุปกรณ์นี้ยังไม่มีเกรด: รีฟอร์จก่อน")));
            } else if (i >= gems.size()) {
                inv.setItem(SOCKET[i], icon("skilltree_lock", "ช่อง Gem " + (i + 1), NamedTextColor.DARK_GRAY, List.of("อุปกรณ์ชิ้นนี้ไม่มีช่องนี้")));
            } else if (gems.get(i) == null) {
                List<String> lore = new ArrayList<>(List.of("ช่องว่าง"));
                lore.add(selected == null ? "เลือก Gem ในกระเป๋าก่อน แล้วคลิกช่องนี้" : "คลิกเพื่อใส่ " + selected.type().label + " " + GemService.roman(selected.tier())
                    + " → " + plugin.gems().describe(selected, category.group));
                inv.setItem(SOCKET[i], icon("menu_socket", "ช่อง Gem " + (i + 1), NamedTextColor.WHITE, lore));
            } else {
                Gem gem = gems.get(i);
                inv.setItem(SOCKET[i], withLore(plugin.gems().item(gem, 1), List.of(Component.empty(),
                    line("ในอุปกรณ์นี้ (" + category.group.label + "): " + plugin.gems().describe(gem, category.group), gem.type().color),
                    line(selected == null ? "คลิกเพื่อถอด (ฟรี)" : "คลิกเพื่อสลับกับ Gem ที่เลือก", NamedTextColor.YELLOW))));
            }
        }
        if (selected == null) {
            inv.setItem(SELECTED, icon("menu_pick", "Gem ที่เลือก: ไม่มี", NamedTextColor.GRAY, List.of("คลิก Gem ในกระเป๋าด้านล่างเพื่อเลือก")));
        } else {
            List<Component> extra = new ArrayList<>(List.of(Component.empty(), line("เลือกแล้ว · คลิกช่อง Gem เพื่อใส่", NamedTextColor.YELLOW)));
            if (category != null) extra.add(line("ในอุปกรณ์นี้: " + plugin.gems().describe(selected, category.group), selected.type().color));
            inv.setItem(SELECTED, glint(withLore(plugin.gems().item(selected, 1), extra)));
        }
        inv.setItem(INFO, icon("menu_stats", "วิธีใส่ Gem", NamedTextColor.LIGHT_PURPLE, List.of(
            "1. วางอุปกรณ์ที่มีเกรดในช่องซ้าย",
            "2. คลิก Gem ในกระเป๋าเพื่อเลือก",
            "3. คลิกช่อง Gem เพื่อใส่ (ใช้ 1 เม็ด)",
            "คลิกช่องที่มี Gem โดยไม่เลือก = ถอดคืนฟรี",
            "Gem เม็ดเดียวให้ผลต่างกันตามชนิดอุปกรณ์")));
    }

    private void drawCut(Holder holder) {
        Inventory inv = holder.inventory;
        inv.setItem(11, icon("menu_pick", "Gem ↓", NamedTextColor.WHITE, List.of("Gem ชนิดและขั้นเดียวกันอย่างน้อย 2 เม็ด")));
        inv.setItem(13, icon("menu_pick", "ผงอัญมณี ↓", NamedTextColor.WHITE, List.of("ได้จากการย่อย Gem")));
        inv.setItem(15, icon("menu_pick", "วัสดุช่าง ↓", NamedTextColor.WHITE,
            List.of("I→II ผลึกใบหยก · II→III ชิ้นคริสตัลคราม", "III→IV เกล็ดอาคมม่วง · IV→V อำพันหลอมเทพ")));
        Gem gem = plugin.gems().read(inv.getItem(CUT_GEM));
        GemService.CutCost cost = gem == null ? null : plugin.gems().cutCost(gem.tier());
        if (gem == null) {
            inv.setItem(CUT_RESULT, icon("skilltree_lock", "ผลการเจียระไน", NamedTextColor.DARK_GRAY, List.of("วาง Gem ในช่องซ้าย")));
        } else if (cost == null) {
            inv.setItem(CUT_RESULT, icon("skilltree_lock", "ผลการเจียระไน", NamedTextColor.DARK_GRAY, List.of("Gem นี้อยู่ขั้นสูงสุดแล้ว")));
        } else {
            ItemStack gems = inv.getItem(CUT_GEM), dust = inv.getItem(CUT_DUST), material = inv.getItem(CUT_MATERIAL);
            boolean enoughGems = gems.getAmount() >= cost.gems();
            boolean enoughDust = plugin.gems().isDust(dust) && dust.getAmount() >= cost.dust();
            boolean enoughMaterial = plugin.affixes().materialRarity(material) == cost.rarity() && material.getAmount() >= cost.materials();
            boolean ok = enoughGems && enoughDust && enoughMaterial;
            inv.setItem(CUT_RESULT, withLore(plugin.gems().item(new Gem(gem.type(), gem.tier() + 1), 1), List.of(Component.empty(),
                line("ต้นทุน: " + gem.type().label + " " + GemService.roman(gem.tier()) + " ×" + cost.gems(), enoughGems ? NamedTextColor.GREEN : NamedTextColor.RED),
                line("        ผงอัญมณี ×" + cost.dust(), enoughDust ? NamedTextColor.GREEN : NamedTextColor.RED),
                line("        " + cost.rarity().materialName + " ×" + cost.materials(), enoughMaterial ? NamedTextColor.GREEN : NamedTextColor.RED),
                line(ok ? "คลิกเพื่อเจียระไน" : "ทรัพยากรไม่พอ", NamedTextColor.YELLOW))));
        }
        double[] multipliers = plugin.settings().gemTierMultipliers;
        List<String> ladder = new ArrayList<>();
        for (int tier = 1; tier <= GemService.MAX_TIER; tier++) {
            ladder.add("ขั้น " + GemService.roman(tier) + " " + GemService.tierName(tier) + " · พลัง ×" + multipliers[tier - 1]);
        }
        inv.setItem(INFO, icon("menu_stats", "ขั้นความบริสุทธิ์", NamedTextColor.LIGHT_PURPLE, ladder));
    }

    private void drawCodex(Holder holder) {
        Inventory inv = holder.inventory;
        GemType[] types = GemType.values();
        for (int i = 0; i < types.length && i < CODEX_GEMS.length; i++) {
            inv.setItem(CODEX_GEMS[i], plugin.gems().item(new Gem(types[i], holder.codexTier), 1));
        }
        inv.setItem(13, icon("menu_codex", "คลัง Gem", NamedTextColor.LIGHT_PURPLE, List.of(
            "ชี้ที่ Gem เพื่อดูผลในอุปกรณ์แต่ละชนิด",
            "เลือกขั้นด้านล่างเพื่อดูค่าที่ขั้นนั้น",
            "หา Gem ได้จาก Elite และหีบในสิ่งก่อสร้าง",
            "Elite เกรดสูงดรอป Gem ขั้นสูงกว่า")));
        for (int i = 0; i < CODEX_TIERS.length; i++) {
            int tier = i + 1;
            ItemStack button = icon("menu_page", "ขั้น " + GemService.roman(tier) + " · " + GemService.tierName(tier),
                tier == holder.codexTier ? NamedTextColor.GOLD : NamedTextColor.GRAY,
                List.of("พลัง ×" + plugin.settings().gemTierMultipliers[i]));
            inv.setItem(CODEX_TIERS[i], tier == holder.codexTier ? glint(button) : button);
        }
    }

    private Gem selectedGem(Holder holder) {
        if (holder.selected < 0) return null;
        Gem gem = plugin.gems().read(holder.owner.getInventory().getItem(holder.selected));
        if (gem == null) holder.selected = -1;
        return gem;
    }

    private static boolean empty(ItemStack item) {
        return item == null || item.getType().isAir();
    }

    private static boolean free(Player player) {
        return player.getGameMode() == GameMode.CREATIVE;
    }

    private void redrawLater(Holder holder) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (holder.owner.getOpenInventory().getTopInventory().getHolder() == holder) redraw(holder);
        });
    }

    private boolean accepts(Holder holder, int slot, ItemStack item) {
        if (empty(item)) return true;
        AffixService affixes = plugin.affixes();
        return switch (holder.page) {
            case REFORGE -> slot == GEAR ? affixes.category(item) != null : slot == MATERIAL && affixes.materialRarity(item) != null;
            case SALVAGE -> affixes.salvageable(item) || plugin.gems().read(item) != null;
            case GEMS -> slot == GEAR && affixes.category(item) != null;
            case CUT -> switch (slot) {
                case CUT_GEM -> plugin.gems().read(item) != null;
                case CUT_DUST -> plugin.gems().isDust(item);
                case CUT_MATERIAL -> affixes.materialRarity(item) != null;
                default -> false;
            };
            case CODEX -> false;
        };
    }

    // ---- events ------------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder holder)) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (player != holder.owner || !player.hasPermission("forge.use") || player.isDead()) {
            event.setCancelled(true);
            Bukkit.getScheduler().runTask(plugin, () -> { if (player.getOpenInventory().getTopInventory().getHolder() == holder) player.closeInventory(); });
            return;
        }
        InventoryAction action = event.getAction();
        if (action == InventoryAction.COLLECT_TO_CURSOR) {
            event.setCancelled(true);
            return;
        }
        int raw = event.getRawSlot();
        if (raw < 0) return;
        if (raw >= SIZE) {
            // On the gem page a click on a gem in the player's inventory selects it.
            if (holder.page == Page.GEMS && plugin.gems().read(event.getCurrentItem()) != null && event.getClickedInventory() instanceof PlayerInventory) {
                event.setCancelled(true);
                holder.selected = event.getSlot();
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.4f);
                redraw(holder);
            } else if (event.isShiftClick()) {
                event.setCancelled(true);
                shiftIn(holder, event);
            } else if (holder.page == Page.GEMS) {
                redrawLater(holder);
            }
            return;
        }
        if (input(holder, raw)) {
            switch (action) {
                case PICKUP_ALL, PICKUP_HALF, PICKUP_ONE, PICKUP_SOME, MOVE_TO_OTHER_INVENTORY,
                     DROP_ALL_SLOT, DROP_ONE_SLOT -> { }
                case PLACE_ALL, PLACE_ONE, PLACE_SOME, SWAP_WITH_CURSOR -> {
                    if (!accepts(holder, raw, event.getCursor())) {
                        event.setCancelled(true);
                        fail(player);
                        return;
                    }
                }
                default -> {
                    event.setCancelled(true);
                    return;
                }
            }
            holder.armedUntil = 0;
            redrawLater(holder);
            return;
        }
        event.setCancelled(true);
        if (event.getClick() != org.bukkit.event.inventory.ClickType.LEFT
            && event.getClick() != org.bukkit.event.inventory.ClickType.RIGHT) return;
        if (raw == CLOSE) {
            player.closeInventory();
            return;
        }
        if (raw == CHARM_TAB) {
            plugin.charmMenu().open(player);
            return;
        }
        for (int i = 0; i < TABS.length; i++) {
            if (raw == TABS[i] && holder.page != Page.values()[i]) {
                open(player, Page.values()[i]);
                return;
            }
        }
        switch (holder.page) {
            case REFORGE -> {
                for (int i = 0; i < CHOICE.length; i++) if (raw == CHOICE[i] || raw == BADGE[i]) reforge(holder, i);
            }
            case SALVAGE -> {
                if (raw == INFO) salvage(holder);
            }
            case GEMS -> {
                for (int i = 0; i < SOCKET.length; i++) if (raw == SOCKET[i]) socket(holder, i);
            }
            case CUT -> {
                if (raw == CUT_RESULT) cut(holder);
            }
            case CODEX -> {
                for (int i = 0; i < CODEX_TIERS.length; i++) {
                    if (raw == CODEX_TIERS[i]) {
                        holder.codexTier = i + 1;
                        redraw(holder);
                    }
                }
            }
        }
    }

    /** Shift-click from the player inventory places the item in the matching input slot only. */
    private void shiftIn(Holder holder, InventoryClickEvent event) {
        ItemStack item = event.getCurrentItem();
        if (empty(item) || event.getClickedInventory() == null) return;
        int[] targets = switch (holder.page) {
            case REFORGE -> new int[]{GEAR, MATERIAL};
            case SALVAGE -> SALVAGE_INPUT;
            case GEMS -> new int[]{GEAR};
            case CUT -> new int[]{CUT_GEM, CUT_DUST, CUT_MATERIAL};
            case CODEX -> new int[0];
        };
        Inventory inv = holder.inventory;
        for (int target : targets) {
            if (!accepts(holder, target, item)) continue;
            ItemStack current = inv.getItem(target);
            if (empty(current)) {
                inv.setItem(target, item.clone());
                event.getClickedInventory().setItem(event.getSlot(), null);
            } else if (current.isSimilar(item) && current.getAmount() < current.getMaxStackSize()) {
                int moved = Math.min(item.getAmount(), current.getMaxStackSize() - current.getAmount());
                current.setAmount(current.getAmount() + moved);
                inv.setItem(target, current);
                item.setAmount(item.getAmount() - moved);
                event.getClickedInventory().setItem(event.getSlot(), item.getAmount() > 0 ? item : null);
            } else {
                continue;
            }
            holder.armedUntil = 0;
            redrawLater(holder);
            return;
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder holder)) return;
        if (event.getWhoClicked() != holder.owner || !holder.owner.hasPermission("forge.use") || holder.owner.isDead()) {
            event.setCancelled(true); return;
        }
        for (int raw : event.getRawSlots()) {
            if (raw < SIZE && (!input(holder, raw) || !accepts(holder, raw, event.getOldCursor()))) {
                event.setCancelled(true);
                return;
            }
        }
        holder.armedUntil = 0;
        redrawLater(holder);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder holder)) return;
        Player player = holder.owner;
        for (int slot = 0; slot < SIZE; slot++) {
            if (!input(holder, slot)) continue;
            ItemStack item = holder.inventory.getItem(slot);
            if (empty(item)) continue;
            holder.inventory.setItem(slot, null);
            give(player, item);
        }
    }

    /** Escrow inputs join normal death processing, before Soulbound and keep-item handlers run. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onDeath(PlayerDeathEvent event) {
        if (!(event.getEntity().getOpenInventory().getTopInventory().getHolder() instanceof Holder holder)) return;
        for (int slot = 0; slot < SIZE; slot++) {
            if (!input(holder, slot)) continue;
            ItemStack item = holder.inventory.getItem(slot);
            if (empty(item)) continue;
            holder.inventory.setItem(slot, null);
            if (event.getKeepInventory()) give(holder.owner, item);
            else event.getDrops().add(item.clone());
        }
    }

    private static void give(Player player, ItemStack item) {
        for (ItemStack left : player.getInventory().addItem(item).values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), left);
        }
    }

    private static boolean fits(PlayerInventory inventory, ItemStack item) {
        if (inventory.firstEmpty() >= 0) return true;
        for (ItemStack stack : inventory.getStorageContents()) {
            if (stack != null && stack.isSimilar(item) && stack.getAmount() + item.getAmount() <= stack.getMaxStackSize()) return true;
        }
        return false;
    }

    private static boolean fitsAll(PlayerInventory inventory, List<ItemStack> items) {
        ItemStack[] room=inventory.getStorageContents();
        for(int i=0;i<room.length;i++)if(room[i]!=null)room[i]=room[i].clone();
        for(ItemStack item:items){
            int left=item.getAmount(),max=item.getMaxStackSize();
            for(ItemStack stored:room)if(!empty(stored)&&stored.isSimilar(item)){
                int take=Math.min(left,Math.max(0,max-stored.getAmount()));stored.setAmount(stored.getAmount()+take);left-=take;
            }
            for(int i=0;i<room.length&&left>0;i++)if(empty(room[i])){
                room[i]=item.clone();room[i].setAmount(Math.min(max,left));left-=room[i].getAmount();
            }
            if(left>0)return false;
        }
        return true;
    }

    // ---- actions -----------------------------------------------------------------------------

    private void reforge(Holder holder, int choice) {
        Player player = holder.owner;
        Inventory inv = holder.inventory;
        ItemStack gear = inv.getItem(GEAR);
        ItemStack material = inv.getItem(MATERIAL);
        AffixService affixes = plugin.affixes();
        ReforgeService reforge = plugin.reforge();
        Rarity rarity = affixes.materialRarity(material);
        if (empty(gear) || affixes.category(gear) == null || rarity == null || !reforge.allowed(rarity)) {
            fail(player);
            return;
        }
        ReforgeService.Cost cost = reforge.cost(rarity, choice);
        if (material.getAmount() < cost.materials() || (!free(player) && player.getLevel() < cost.levels())) {
            fail(player);
            return;
        }
        List<ItemStack> choices = reforge.choices(player, gear, rarity);
        if (choices.size() != ReforgeService.CHOICES) {
            fail(player);
            return;
        }
        AffixData before = affixes.read(gear);
        material.setAmount(material.getAmount() - cost.materials());
        inv.setItem(MATERIAL, material.getAmount() > 0 ? material : null);
        if (!free(player)) player.setLevel(player.getLevel() - cost.levels());
        ItemStack result = choices.get(choice);
        inv.setItem(GEAR, result);
        reforge.advanceSeed(player);
        plugin.getLogger().info("[Forge-Reforge] player=" + player.getName() + " uuid=" + player.getUniqueId()
            + " item=" + gear.getType().getKey().getKey() + " rarity=" + rarity + " choice=" + (choice + 1)
            + " cost=" + cost.materials() + "mat+" + cost.levels() + "lv before=" + (before == null ? "-" : before.rarity() + ":" + before.serialize())
            + " after=" + affixes.read(result).serialize() + " sockets=" + affixes.sockets(result));
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.7f, 1.1f);
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.3f);
        player.getWorld().spawnParticle(Particle.ENCHANT, player.getLocation().add(0, 1.2, 0), 40, 0.4, 0.5, 0.4, 0.6);
        redraw(holder);
    }

    private void salvage(Holder holder) {
        Player player = holder.owner;
        Inventory inv = holder.inventory;
        AffixService affixes = plugin.affixes();
        boolean any = false;
        for (int slot : SALVAGE_INPUT) {
            ItemStack item = inv.getItem(slot);
            if (affixes.salvageable(item) || plugin.gems().read(item) != null) any = true;
        }
        if (!any) {
            fail(player);
            return;
        }
        if (!holder.armed()) {
            holder.armedUntil = System.currentTimeMillis() + 5000;
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.6f, 0.8f);
            redraw(holder);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!holder.armed() && holder.owner.getOpenInventory().getTopInventory().getHolder() == holder) redraw(holder);
            }, 101);
            return;
        }
        holder.armedUntil = 0;
        Map<Rarity, Integer> output = new EnumMap<>(Rarity.class);
        List<ItemStack> returned = new ArrayList<>();
        List<Integer> consumed = new ArrayList<>();
        int pieces = 0, dust = 0;
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int slot : SALVAGE_INPUT) {
            ItemStack item = inv.getItem(slot);
            if (empty(item)) continue;
            Gem gem = plugin.gems().read(item);
            if (gem != null) {
                dust += plugin.gems().salvageDust(gem.tier()) * item.getAmount();
                pieces += item.getAmount();
                consumed.add(slot);
                continue;
            }
            if (!affixes.salvageable(item)) continue;
            for (Gem socketed : affixes.gems(item)) if (socketed != null) returned.add(plugin.gems().item(socketed, 1));
            AffixData data = affixes.read(item);
            if (data == null) {
                if (random.nextDouble() < plugin.settings().salvagePlainChance) output.merge(Rarity.COMMON, 1, Integer::sum);
            } else {
                ForgeSettings.Grade grade = plugin.settings().grade(data.rarity());
                output.merge(data.rarity(), random.nextInt(grade.salvageMin(), grade.salvageMax() + 1), Integer::sum);
            }
            consumed.add(slot);
            pieces++;
        }
        List<ItemStack> delivery=new ArrayList<>(returned);
        output.forEach((rarity,amount)->{if(amount>0)delivery.add(affixes.material(rarity,amount));});
        if(dust>0)delivery.add(plugin.gems().dust(dust));
        if(!fitsAll(player.getInventory(),delivery)){
            player.sendMessage(Component.text("กระเป๋าเต็ม รับผลการย่อยและ Gem คืนไม่ครบ",NamedTextColor.RED));
            fail(player);redraw(holder);return;
        }
        consumed.forEach(slot->inv.setItem(slot,null));
        StringBuilder log = new StringBuilder();
        List<String> received = new ArrayList<>();
        output.forEach((rarity, amount) -> {
            if (amount <= 0) return;
            giveStacked(player, affixes.material(rarity, 1), amount);
            log.append(' ').append(rarity).append('=').append(amount);
            received.add(rarity.materialName + " ×" + amount);
        });
        if (dust > 0) {
            giveStacked(player, plugin.gems().dust(1), dust);
            log.append(" dust=").append(dust);
            received.add("ผงอัญมณี ×" + dust);
        }
        returned.forEach(gem -> give(player, gem));
        if (!returned.isEmpty()) received.add("Gem คืน " + returned.size() + " เม็ด");
        plugin.getLogger().info("[Forge-Salvage] player=" + player.getName() + " uuid=" + player.getUniqueId() + " pieces=" + pieces
            + log + " gems_returned=" + returned.size());
        player.sendMessage(Component.text("ย่อย " + pieces + " ชิ้น ได้ " + (received.isEmpty() ? "ไม่มีวัสดุ (ดวงไม่ดี)" : String.join(", ", received)), NamedTextColor.GOLD));
        player.playSound(player.getLocation(), Sound.BLOCK_GRINDSTONE_USE, 0.8f, 1.0f);
        redraw(holder);
    }

    private static void giveStacked(Player player, ItemStack template, int amount) {
        while (amount > 0) {
            ItemStack stack = template.clone();
            stack.setAmount(Math.min(amount, stack.getMaxStackSize()));
            amount -= stack.getAmount();
            give(player, stack);
        }
    }

    private void socket(Holder holder, int socket) {
        Player player = holder.owner;
        Inventory inv = holder.inventory;
        ItemStack gear = inv.getItem(GEAR);
        AffixService affixes = plugin.affixes();
        if (affixes.category(gear) == null || affixes.read(gear) == null || socket >= affixes.sockets(gear)) {
            fail(player);
            return;
        }
        Gem current = affixes.gems(gear).get(socket);
        Gem selected = selectedGem(holder);
        if (selected == null) {
            if (current == null) {
                fail(player);
                return;
            }
            ItemStack back = plugin.gems().item(current, 1);
            if (!fits(player.getInventory(), back)) {
                player.sendMessage(Component.text("กระเป๋าเต็ม ถอด Gem ไม่ได้", NamedTextColor.RED));
                fail(player);
                return;
            }
            affixes.setGem(gear, socket, null);
            inv.setItem(GEAR, gear);
            give(player, back);
            player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_BREAK, 0.7f, 1.2f);
            plugin.getLogger().info("[Forge-Gem] remove player=" + player.getName() + " socket=" + (socket + 1) + " gem=" + current.serialize());
        } else {
            ItemStack stack = player.getInventory().getItem(holder.selected);
            if (current != null) {
                ItemStack back = plugin.gems().item(current, 1);
                ItemStack[] storage = player.getInventory().getStorageContents();
                if (holder.selected >= 0 && holder.selected < storage.length) {
                    ItemStack remaining = stack.clone(); remaining.setAmount(remaining.getAmount() - 1);
                    storage[holder.selected] = remaining.getAmount() == 0 ? null : remaining;
                }
                int room = 0;
                for (ItemStack stored : storage) room += empty(stored) ? back.getMaxStackSize()
                    : stored.isSimilar(back) ? Math.max(0, stored.getMaxStackSize() - stored.getAmount()) : 0;
                if (room < back.getAmount()) {
                    player.sendMessage(Component.text("กระเป๋าเต็ม รับ Gem เม็ดเดิมคืนไม่ได้", NamedTextColor.RED));
                    fail(player); return;
                }
            }
            stack.setAmount(stack.getAmount() - 1);
            player.getInventory().setItem(holder.selected, stack.getAmount() > 0 ? stack : null);
            affixes.setGem(gear, socket, selected);
            inv.setItem(GEAR, gear);
            if (current != null) give(player, plugin.gems().item(current, 1));
            if (plugin.gems().read(player.getInventory().getItem(holder.selected)) == null) holder.selected = -1;
            player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_PLACE, 0.8f, 1.3f);
            player.getWorld().spawnParticle(Particle.WAX_ON, player.getLocation().add(0, 1.2, 0), 12, 0.3, 0.3, 0.3, 0);
            plugin.getLogger().info("[Forge-Gem] insert player=" + player.getName() + " socket=" + (socket + 1) + " gem=" + selected.serialize()
                + " replaced=" + (current == null ? "-" : current.serialize()));
        }
        redraw(holder);
    }

    private void cut(Holder holder) {
        Player player = holder.owner;
        Inventory inv = holder.inventory;
        ItemStack gems = inv.getItem(CUT_GEM), dust = inv.getItem(CUT_DUST), material = inv.getItem(CUT_MATERIAL);
        Gem gem = plugin.gems().read(gems);
        GemService.CutCost cost = gem == null ? null : plugin.gems().cutCost(gem.tier());
        if (cost == null || gems.getAmount() < cost.gems() || !plugin.gems().isDust(dust) || dust.getAmount() < cost.dust()
            || plugin.affixes().materialRarity(material) != cost.rarity() || material.getAmount() < cost.materials()) {
            fail(player);
            return;
        }
        Gem result = new Gem(gem.type(), gem.tier() + 1);
        ItemStack produced=plugin.gems().item(result,1);
        if(!fits(player.getInventory(),produced)){
            player.sendMessage(Component.text("กระเป๋าเต็ม รับ Gem ที่เจียระไนไม่ได้",NamedTextColor.RED));fail(player);return;
        }
        gems.setAmount(gems.getAmount() - cost.gems());
        dust.setAmount(dust.getAmount() - cost.dust());
        material.setAmount(material.getAmount() - cost.materials());
        inv.setItem(CUT_GEM, gems.getAmount() > 0 ? gems : null);
        inv.setItem(CUT_DUST, dust.getAmount() > 0 ? dust : null);
        inv.setItem(CUT_MATERIAL, material.getAmount() > 0 ? material : null);
        give(player, produced);
        plugin.getLogger().info("[Forge-Cut] player=" + player.getName() + " uuid=" + player.getUniqueId() + " " + gem.serialize() + " -> " + result.serialize());
        player.sendMessage(Component.text("เจียระไนสำเร็จ: " + result.type().label + " " + GemService.roman(result.tier()), result.type().color));
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 0.8f, 1.4f);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.6f);
        redraw(holder);
    }

    private static void fail(Player player) {
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.6f);
    }
}
