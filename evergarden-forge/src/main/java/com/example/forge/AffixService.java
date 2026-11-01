package com.example.forge;

import com.example.forge.GemService.Gem;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemAttributeModifiers;
import io.papermc.paper.datacomponent.item.attribute.AttributeModifierDisplay;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Affix data, sockets and gems live in the item's persistent data. Name, lore and attribute
 * modifiers are derived from it and can be rebuilt at any time ({@link #ensure}), because other
 * plugins rewrite attack modifiers on their weapons (Evergarden's adventurer weapon revisions, for one).
 */
public final class AffixService {
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static final TextColor QUALITY = TextColor.color(0x8A8399);
    private static final TextColor STAT = TextColor.color(0xE8E4F0);
    private static final int VERSION = 2;

    /** One modifier this plugin wants on an item. */
    private record Planned(NamespacedKey key, Stat stat, double value) {}

    private final ForgePlugin plugin;
    private final NamespacedKey rarityKey, affixKey, socketKey, gemsKey, loreKey, nameKey, versionKey, materialKey;

    public AffixService(ForgePlugin plugin) {
        this.plugin = plugin;
        rarityKey = new NamespacedKey(plugin, "rarity");
        affixKey = new NamespacedKey(plugin, "affixes");
        socketKey = new NamespacedKey(plugin, "sockets");
        gemsKey = new NamespacedKey(plugin, "gems");
        loreKey = new NamespacedKey(plugin, "lore");
        nameKey = new NamespacedKey(plugin, "name");
        versionKey = new NamespacedKey(plugin, "version");
        materialKey = new NamespacedKey(plugin, "material");
    }

    private ForgeSettings settings() {
        return plugin.settings();
    }

    // ---- what can carry affixes --------------------------------------------------------

    public boolean isProtected(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        for (NamespacedKey key : item.getItemMeta().getPersistentDataContainer().getKeys()) {
            if (settings().isProtectedKey(key.toString().toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }

    /** Category of gear that may roll or reforge affixes; null for anything else. */
    public LootCategory category(ItemStack item) {
        if (item == null || item.getType().isAir() || item.getAmount() != 1) return null;
        LootCategory category = LootCategory.of(item.getType());
        return category == null || isProtected(item) ? null : category;
    }

    /** Affix gear always salvages; plain gear does too (for a chance at common material). */
    public boolean salvageable(ItemStack item) {
        return category(item) != null;
    }

    // ---- reading and rolling -------------------------------------------------------------

    public AffixData read(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        Rarity rarity = Rarity.parse(pdc.get(rarityKey, PersistentDataType.STRING));
        if (rarity == null) return null;
        return AffixData.parse(rarity, pdc.get(affixKey, PersistentDataType.STRING), pdc.getOrDefault(socketKey, PersistentDataType.INTEGER, 0));
    }

    public int sockets(ItemStack item) {
        AffixData data = read(item);
        return data == null ? 0 : data.sockets();
    }

    /** One entry per socket; null marks an empty socket. */
    public List<Gem> gems(ItemStack item) {
        int sockets = sockets(item);
        List<Gem> gems = new ArrayList<>(Collections.nCopies(sockets, null));
        if (sockets == 0) return gems;
        String stored = item.getItemMeta().getPersistentDataContainer().get(gemsKey, PersistentDataType.STRING);
        if (stored != null) {
            String[] parts = stored.split("\\|", -1);
            for (int i = 0; i < sockets && i < parts.length; i++) gems.set(i, Gem.parse(parts[i]));
        }
        return gems;
    }

    public List<AffixType> pool(LootCategory category) {
        List<AffixType> pool = new ArrayList<>();
        for (AffixType type : AffixType.values()) {
            if (type.categories.contains(category) && settings().affixes.get(type).enabled()) pool.add(type);
        }
        return pool;
    }

    public AffixData roll(LootCategory category, Rarity rarity, Random random) {
        ForgeSettings.Grade grade = settings().grade(rarity);
        List<AffixType> pool = pool(category);
        Collections.shuffle(pool, random);
        Map<AffixType, Double> levels = new LinkedHashMap<>();
        for (AffixType type : pool.subList(0, Math.min(grade.affixes(), pool.size()))) {
            levels.put(type, grade.levelLow() + random.nextDouble() * (grade.levelHigh() - grade.levelLow()));
        }
        int sockets = grade.socketsMin() + random.nextInt(grade.socketsMax() - grade.socketsMin() + 1);
        return new AffixData(rarity, levels, sockets);
    }

    public Rarity rollRarity(Random random, Map<Rarity, Integer> weights) {
        int total = 0;
        for (int weight : weights.values()) total += Math.max(0, weight);
        if (total <= 0) return Rarity.COMMON;
        int ticket = random.nextInt(total);
        for (Rarity rarity : Rarity.values()) {
            ticket -= Math.max(0, weights.getOrDefault(rarity, 0));
            if (ticket < 0) return rarity;
        }
        return Rarity.COMMON;
    }

    /** Rolls and applies; false when the item cannot carry affixes. */
    public boolean affix(ItemStack item, Rarity rarity, Random random) {
        LootCategory category = category(item);
        if (category == null) return false;
        apply(item, roll(category, rarity, random));
        return true;
    }

    public double value(AffixType type, double level) {
        return settings().affixes.get(type).at(level);
    }

    /** Sum of one stat from affixes and gems on an item (used for the arrow stats). */
    public double total(ItemStack item, Stat stat) {
        LootCategory category = item == null ? null : LootCategory.of(item.getType());
        if (category == null) return 0;
        double total = 0;
        for (Planned planned : plan(item, category)) if (planned.stat() == stat) total += planned.value();
        return total;
    }

    // ---- writing ---------------------------------------------------------------------------

    /**
     * Writes new affixes. Sockets never go down (a reforge must not swallow gems), and gems
     * already installed stay in their sockets.
     */
    public void apply(ItemStack item, AffixData data) {
        if (LootCategory.of(item.getType()) == null) return;
        int sockets = Math.max(data.sockets(), sockets(item));
        item.editMeta(meta -> {
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(rarityKey, PersistentDataType.STRING, data.rarity().name());
            pdc.set(affixKey, PersistentDataType.STRING, data.serialize());
            pdc.set(socketKey, PersistentDataType.INTEGER, sockets);
            pdc.set(versionKey, PersistentDataType.INTEGER, VERSION);
        });
        render(item);
    }

    /** Puts a gem in (or, with null, empties) one socket. False when the socket does not exist. */
    public boolean setGem(ItemStack item, int socket, Gem gem) {
        List<Gem> gems = gems(item);
        if (socket < 0 || socket >= gems.size()) return false;
        gems.set(socket, gem);
        List<String> parts = gems.stream().map(g -> g == null ? "" : g.serialize()).toList();
        item.editMeta(meta -> meta.getPersistentDataContainer().set(gemsKey, PersistentDataType.STRING, String.join("|", parts)));
        render(item);
        return true;
    }

    /** Re-applies name, lore and modifiers if another plugin removed them. True when it changed the item. */
    public boolean ensure(ItemStack item) {
        if (read(item) == null || LootCategory.of(item.getType()) == null) return false;
        if (intact(item)) return false;
        render(item);
        return true;
    }

    /** True when every planned modifier is present with its value and operation and our lore block is still there. */
    boolean intact(ItemStack item) {
        LootCategory category = LootCategory.of(item.getType());
        Map<NamespacedKey, Planned> expected = new LinkedHashMap<>();
        for (Planned planned : plan(item, category)) if (planned.stat().attribute != null) expected.put(planned.key(), planned);
        Map<NamespacedKey, ItemAttributeModifiers.Entry> found = new LinkedHashMap<>();
        ItemAttributeModifiers current = item.getData(DataComponentTypes.ATTRIBUTE_MODIFIERS);
        if (current != null) {
            for (ItemAttributeModifiers.Entry entry : current.modifiers()) {
                if (ours(entry.modifier().getKey())) {
                    if (found.put(entry.modifier().getKey(), entry) != null) return false;
                }
            }
        }
        if (!found.keySet().equals(expected.keySet())) return false;
        for (Planned planned : expected.values()) {
            var entry = found.get(planned.key());
            AttributeModifier modifier = entry.modifier();
            if (entry.attribute() != planned.stat().attribute || !entry.getGroup().equals(category.slot)
                || modifier.getOperation() != planned.stat().operation || Math.abs(modifier.getAmount() - planned.value()) > 1e-6) return false;
        }
        List<String> stored = storedLore(item.getItemMeta());
        List<String> expectedLore=affixLore(read(item),active(read(item)),gems(item),category).stream().map(PLAIN::serialize).toList();
        if(!stored.equals(expectedLore))return false;
        List<Component> lore = item.getItemMeta().lore();
        return lore != null && lore.size()>=stored.size()
            && lore.subList(0,stored.size()).stream().map(PLAIN::serialize).toList().equals(stored);
    }

    /** Diagnostic text for probes and /forge inspect. */
    public String describeModifiers(ItemStack item) {
        ItemAttributeModifiers current = item.getData(DataComponentTypes.ATTRIBUTE_MODIFIERS);
        if (current == null) return "none";
        StringBuilder text = new StringBuilder();
        for (ItemAttributeModifiers.Entry entry : current.modifiers()) {
            text.append(entry.attribute().getKey().getKey()).append(' ').append(entry.modifier().getKey())
                .append('=').append(entry.modifier().getAmount()).append("; ");
        }
        return text.toString();
    }

    private Map<AffixType, Double> active(AffixData data) {
        Map<AffixType, Double> active = new LinkedHashMap<>();
        data.levels().forEach((type, level) -> {
            if (settings().affixes.get(type).enabled()) active.put(type, level);
        });
        return active;
    }

    private List<Planned> plan(ItemStack item, LootCategory category) {
        List<Planned> planned = new ArrayList<>();
        AffixData data = read(item);
        if (data == null) return planned;
        String slot = category.name().toLowerCase(Locale.ROOT);
        active(data).forEach((type, level) ->
            planned.add(new Planned(new NamespacedKey(plugin, "affix_" + type.id() + "_" + slot), type.stat, value(type, level))));
        List<Gem> gems = gems(item);
        for (int i = 0; i < gems.size(); i++) {
            Gem gem = gems.get(i);
            if (gem == null) continue;
            planned.add(new Planned(new NamespacedKey(plugin, "gem" + i + "_" + slot),
                gem.type().effects.get(category.group).stat(), plugin.gems().value(gem, category.group)));
        }
        return planned;
    }

    private boolean ours(NamespacedKey key) {
        return key.getNamespace().equals(plugin.namespace());
    }

    private void render(ItemStack item) {
        LootCategory category = LootCategory.of(item.getType());
        AffixData data = read(item);
        if (category == null || data == null) return;
        Map<AffixType, Double> active = active(data);
        List<Gem> gems = gems(item);
        item.editMeta(meta -> {
            writeName(meta, item, data, active);
            writeLore(meta, data, active, gems, category);
        });
        writeModifiers(item, category);
    }

    /** Slot is part of every key: vanilla merges equal ids from a helmet and boots into one modifier. */
    private void writeModifiers(ItemStack item, LootCategory category) {
        ItemAttributeModifiers current = item.getData(DataComponentTypes.ATTRIBUTE_MODIFIERS);
        // Copy the base modifiers first: an explicit component replaces the material's defaults.
        if (current == null) current = item.getType().asItemType().getDefaultData(DataComponentTypes.ATTRIBUTE_MODIFIERS);
        ItemAttributeModifiers.Builder builder = ItemAttributeModifiers.itemAttributes();
        if (current != null) {
            for (ItemAttributeModifiers.Entry entry : current.modifiers()) {
                if (!ours(entry.modifier().getKey())) builder.addModifier(entry.attribute(), entry.modifier(), entry.getGroup(), entry.display());
            }
        }
        for (Planned planned : plan(item, category)) {
            if (planned.stat().attribute == null) continue;
            AttributeModifier modifier = new AttributeModifier(planned.key(), planned.value(), planned.stat().operation, category.slot);
            // Lore shows the bonus on Java and Bedrock alike, so the vanilla attribute line is hidden.
            builder.addModifier(planned.stat().attribute, modifier, category.slot, AttributeModifierDisplay.hidden());
        }
        item.setData(DataComponentTypes.ATTRIBUTE_MODIFIERS, builder.build());
    }

    private void writeName(ItemMeta meta, ItemStack item, AffixData data, Map<AffixType, Double> active) {
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String owned = pdc.get(nameKey, PersistentDataType.STRING);
        // A name from another plugin (Rare weapons) or an anvil rename is part of the item's identity: keep it.
        if (meta.hasCustomName() && (owned == null || !owned.equals(PLAIN.serialize(meta.customName())))) return;
        List<AffixType> types = new ArrayList<>(active.keySet());
        Component name = Component.translatable(item.getType().translationKey());
        if (!types.isEmpty()) name = name.append(Component.text(" " + types.getFirst().adjective));
        if (types.size() > 1) name = name.append(Component.text(" แห่ง" + types.get(1).noun));
        name = name.color(data.rarity().color).decoration(TextDecoration.ITALIC, false);
        meta.customName(name);
        pdc.set(nameKey, PersistentDataType.STRING, PLAIN.serialize(name));
    }

    private void writeLore(ItemMeta meta, AffixData data, Map<AffixType, Double> active, List<Gem> gems, LootCategory category) {
        List<Component> ours=affixLore(data,active,gems,category);
        Set<String> previous = new HashSet<>(storedLore(meta));
        List<Component> lore = new ArrayList<>(ours);
        if (meta.lore() != null) {
            for (Component existing : meta.lore()) if (!previous.contains(PLAIN.serialize(existing))) lore.add(existing);
        }
        meta.lore(lore);
        meta.getPersistentDataContainer().set(loreKey, PersistentDataType.STRING,
            String.join("\n", ours.stream().map(PLAIN::serialize).toList()));
    }
    private List<Component> affixLore(AffixData data,Map<AffixType,Double> active,List<Gem> gems,LootCategory category){
        List<Component> ours = new ArrayList<>();
        ours.add(line("✦ เกรด " + data.rarity().thai, data.rarity().color));
        active.forEach((type, level) -> ours.add(line(" ▸ " + type.stat.format(value(type, level)), STAT)
            .append(line("  (" + Math.round(level * 100) + "%)", QUALITY))));
        for (Gem gem : gems) {
            if (gem == null) {
                ours.add(line(" ◇ ช่อง Gem ว่าง", NamedTextColor.DARK_GRAY));
            } else {
                ours.add(line(" ◆ " + gem.type().thai + " " + GemService.roman(gem.tier()) + ": ", gem.type().color)
                    .append(line(plugin.gems().describe(gem, category.group), STAT)));
            }
        }

        return ours;
    }

    private List<String> storedLore(ItemMeta meta) {
        if (meta == null) return List.of();
        String stored = meta.getPersistentDataContainer().get(loreKey, PersistentDataType.STRING);
        return stored == null || stored.isEmpty() ? List.of() : List.of(stored.split("\n"));
    }

    static Component line(String text, TextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }

    // ---- salvage materials ----------------------------------------------------------------

    public ItemStack material(Rarity rarity, int amount) {
        ItemStack item = new ItemStack(rarity.material, Math.max(1, amount));
        item.editMeta(meta -> {
            meta.customName(line(rarity.materialName, rarity.color));
            meta.lore(List.of(
                line("วัสดุช่างเกรด " + rarity.thai, rarity.color),
                line("ได้จากการย่อยอุปกรณ์เกรด " + rarity.thai, NamedTextColor.GRAY),
                line("ใช้รีฟอร์จอุปกรณ์ให้เป็นเกรด " + rarity.thai, NamedTextColor.GRAY),
                line("/forge เพื่อเปิดโรงตีเหล็ก", NamedTextColor.DARK_GRAY)));
            if (rarity.ordinal() >= Rarity.RARE.ordinal()) meta.setEnchantmentGlintOverride(true);
            meta.getPersistentDataContainer().set(materialKey, PersistentDataType.STRING, rarity.name());
        });
        ForgeResources.model(plugin,item);
        return item;
    }

    public Rarity materialRarity(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return Rarity.parse(item.getItemMeta().getPersistentDataContainer().get(materialKey, PersistentDataType.STRING));
    }
}
