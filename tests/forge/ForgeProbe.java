import com.example.forge.AffixData;
import com.example.forge.AffixService;
import com.example.forge.AffixType;
import com.example.forge.ForgeApi;
import com.example.forge.ForgePlugin;
import com.example.forge.GemService;
import com.example.forge.GemService.Gem;
import com.example.forge.GemType;
import com.example.forge.LootCategory;
import com.example.forge.Rarity;
import com.example.forge.Stat;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemAttributeModifiers;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Zombie;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.loot.LootContext;
import org.bukkit.loot.LootTables;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/** Headless checks of EvergardenForge item data, attributes, gems, lore ownership, chest loot and elites. */
public class ForgeProbe extends JavaPlugin {
    private final List<String> passed = new ArrayList<>();
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    protected void check(boolean condition, String name) {
        if (!condition) throw new AssertionError(name);
        passed.add(name);
    }

    @Override
    public void onEnable() {
        Bukkit.getScheduler().runTaskLater(this, () -> {
            try {
                run();
            } catch (Throwable error) {
                finish("FAIL " + error + "\npassed before failure: " + passed);
            }
        }, 40L);
    }

    private void finish(String text) {
        try {
            Files.writeString(Path.of("forge-result.txt"), text);
        } catch (Exception ignored) {
        }
        Bukkit.getScheduler().runTaskLater(this, Bukkit::shutdown, 20L);
    }

    private static List<String> lore(ItemStack item) {
        List<Component> lore = item.getItemMeta().lore();
        return lore == null ? List.of() : lore.stream().map(PLAIN::serialize).toList();
    }

    private static List<ItemAttributeModifiers.Entry> ours(ItemStack item) {
        ItemAttributeModifiers data = item.getData(DataComponentTypes.ATTRIBUTE_MODIFIERS);
        return data == null ? List.of() : data.modifiers().stream().filter(e -> e.modifier().getKey().getNamespace().equals("evergardenforge")).toList();
    }

    private static boolean hasBaseDamage(ItemStack item) {
        ItemAttributeModifiers data = item.getData(DataComponentTypes.ATTRIBUTE_MODIFIERS);
        return data != null && data.modifiers().stream().anyMatch(e -> e.attribute() == Attribute.ATTACK_DAMAGE
            && e.modifier().getKey().getNamespace().equals("minecraft"));
    }

    private void run() {
        ForgePlugin forge = (ForgePlugin) Bukkit.getPluginManager().getPlugin("EvergardenForge");
        check(forge != null && ForgeApi.available(), "forge enabled and API bound");
        AffixService affixes = forge.affixes();
        GemService gems = forge.gems();

        // 1. Rolling keeps the sword's own damage and adds one hidden modifier per attribute affix.
        ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
        check(ForgeApi.affix(sword, "MYTHIC", new Random(7)), "mythic roll accepted");
        AffixData data = affixes.read(sword);
        check(data != null && data.rarity() == Rarity.MYTHIC && data.levels().size() == 4, "mythic sword has 4 affixes: " + data);
        check(data.sockets() >= 2 && data.sockets() <= 3, "mythic sword has 2-3 sockets: " + data.sockets());
        long attributeAffixes = data.levels().keySet().stream().filter(t -> t.stat.attribute != null).count();
        check(ours(sword).size() == attributeAffixes, "one modifier per attribute affix");
        check(hasBaseDamage(sword), "base attack damage kept after explicit component");
        check(lore(sword).getFirst().equals("✦ เกรด ปรัมปรา"), "rarity lore line first: " + lore(sword));
        check(lore(sword).stream().filter(l -> l.contains("ช่อง Gem ว่าง")).count() == data.sockets(), "empty sockets shown in lore");
        check(!affixes.ensure(sword), "ensure is a no-op on a fresh item: " + affixes.describeModifiers(sword));

        // 2. Same seed, same roll (reforge choices depend on this).
        AffixData a = affixes.roll(LootCategory.MELEE, Rarity.RARE, new Random(42));
        AffixData b = affixes.roll(LootCategory.MELEE, Rarity.RARE, new Random(42));
        check(a.equals(b), "deterministic roll for one seed");
        check(a.levels().values().stream().allMatch(l -> l >= 0.30 && l <= 0.60), "rare levels inside window");
        check(affixes.roll(LootCategory.MELEE, Rarity.COMMON, new Random(1)).sockets() == 0, "common has no sockets");

        // 3. Gems: socket, modifier per group, lore, survive a reforge, sum for arrows.
        Gem fire3 = new Gem(GemType.FIRE, 3);
        check(affixes.setGem(sword, 0, fire3), "fire III socketed");
        ItemAttributeModifiers.Entry gemModifier = ours(sword).stream().filter(e -> e.modifier().getKey().getKey().equals("gem0_melee")).findFirst().orElse(null);
        check(gemModifier != null && gemModifier.attribute() == Attribute.ATTACK_DAMAGE && Math.abs(gemModifier.modifier().getAmount() - 0.5 * 2.1) < 1e-9,
            "fire III on a sword = +1.05 attack damage: " + affixes.describeModifiers(sword));
        check(lore(sword).stream().anyMatch(l -> l.startsWith(" ◆ อัญมณีเพลิง III: +1.1 ดาเมจโจมตี")), "gem lore line: " + lore(sword));
        check(!affixes.ensure(sword), "ensure is a no-op with a gem");
        affixes.apply(sword, new AffixData(Rarity.COMMON, Map.of(AffixType.SHARP, 0.5), 0));
        check(affixes.sockets(sword) == data.sockets() && fire3.equals(affixes.gems(sword).getFirst()), "reforge keeps sockets and gems");
        check(!affixes.setGem(sword, 5, fire3), "socket out of range refused");

        ItemStack bow = new ItemStack(Material.BOW);
        affixes.apply(bow, new AffixData(Rarity.EPIC, Map.of(AffixType.ARROW_POWER, 1.0), 2));
        affixes.setGem(bow, 0, new Gem(GemType.DARK, 1));
        check(Math.abs(affixes.total(bow, Stat.ARROW_DAMAGE) - (0.25 + 0.05)) < 1e-9, "bow arrow damage = affix + gem: " + affixes.total(bow, Stat.ARROW_DAMAGE));

        ItemStack gemItem = gems.item(new Gem(GemType.LIFE, 2), 3);
        check(new Gem(GemType.LIFE, 2).equals(gems.read(gemItem)), "gem item round trip");
        check(gemItem.isSimilar(gems.item(new Gem(GemType.LIFE, 2), 1)) && !gemItem.isSimilar(gems.item(new Gem(GemType.LIFE, 3), 1)), "gems stack by type and tier only");
        check(lore(gemItem).stream().filter(l -> l.startsWith(" • ")).count() == LootCategory.Group.values().length, "gem lore lists every gear group: " + lore(gemItem));
        check(gems.cutCost(1).rarity() == Rarity.UNCOMMON && gems.cutCost(4).rarity() == Rarity.MYTHIC && gems.cutCost(5) == null, "cutting costs follow tiers");
        check(gems.isForgeItem(gemItem) && gems.isForgeItem(gems.dust(1)) && gems.isForgeItem(affixes.material(Rarity.RARE, 1))
            && !gems.isForgeItem(new ItemStack(Material.EMERALD)), "forge items recognised, vanilla emerald is not");

        // 4. Another plugin wiping attack modifiers (adventurer weapon revision) is repaired.
        Map<AffixType, Double> sharp = new LinkedHashMap<>();
        sharp.put(AffixType.SHARP, 1.0);
        sharp.put(AffixType.SWIFT, 0.5);
        ItemStack wiped = new ItemStack(Material.IRON_SWORD);
        affixes.apply(wiped, new AffixData(Rarity.EPIC, sharp));
        wiped.editMeta(meta -> meta.removeAttributeModifier(Attribute.ATTACK_DAMAGE));
        check(ours(wiped).size() == 1, "simulated wipe removed sharp modifier");
        check(affixes.ensure(wiped) && ours(wiped).size() == 2, "ensure restored the wiped modifier");

        // 5. Foreign name and lore survive, our lore is not duplicated on re-apply.
        ItemStack named = new ItemStack(Material.IRON_SWORD);
        named.editMeta(meta -> {
            meta.customName(Component.text("Excalibur"));
            meta.lore(List.of(Component.text("Unique: Holy Edge")));
        });
        ForgeApi.affix(named, "RARE", new Random(1));
        ForgeApi.affix(named, "EPIC", new Random(2));
        check(PLAIN.serialize(named.getItemMeta().customName()).equals("Excalibur"), "foreign custom name kept");
        List<String> namedLore = lore(named);
        check(namedLore.stream().filter("Unique: Holy Edge"::equals).count() == 1, "foreign lore kept once: " + namedLore);
        check(namedLore.stream().filter(l -> l.startsWith("✦ เกรด")).count() == 1, "affix block not duplicated: " + namedLore);

        // 6. Gear variety: shields, tools, rods and elytra take affixes; protected items do not.
        for (Material material : List.of(Material.SHIELD, Material.DIAMOND_PICKAXE, Material.IRON_SHOVEL, Material.FISHING_ROD, Material.ELYTRA, Material.NETHERITE_HOE)) {
            ItemStack item = new ItemStack(material);
            check(ForgeApi.affix(item, "RARE", new Random(3)) && affixes.read(item).levels().size() == 3, material + " takes 3 rare affixes");
        }
        Set<String> kinds = new HashSet<>();
        for (int i = 0; i < 300; i++) {
            ItemStack gear = forge.gear().affixed(Rarity.EPIC, new Random(i), World.Environment.NETHER);
            check(affixes.read(gear) != null && affixes.read(gear).rarity() == Rarity.EPIC, "generated gear is epic affix gear");
            kinds.add(LootCategory.of(gear.getType()).name());
        }
        passed.removeIf(name -> name.equals("generated gear is epic affix gear"));
        passed.add("300 generated epic pieces all carry epic affixes");
        // Staffs come only from Advance Magic; generated gear covers every other category.
        check(kinds.size() == LootCategory.values().length - 1 && !kinds.contains("STAFF"), "generated gear covers every category but staffs: " + kinds);
        ItemStack legendary = new ItemStack(Material.NETHERITE_SWORD);
        legendary.editMeta(meta -> meta.getPersistentDataContainer().set(new NamespacedKey("advance-magic", "legendary_weapon"), PersistentDataType.STRING, "excalibur"));
        check(ForgeApi.affix(legendary, "COMMON", new Random(3)) && affixes.category(legendary) == LootCategory.MELEE, "legendary weapon takes affixes");
        check(!affixes.salvageable(legendary) && affixes.salvageLocked(legendary), "legendary weapon can never be salvaged");
        ItemStack seraph = new ItemStack(Material.NETHERITE_CHESTPLATE);
        seraph.editMeta(meta -> meta.getPersistentDataContainer().set(new NamespacedKey("advance-magic", "armor_set"), PersistentDataType.STRING, "seraph_chestplate"));
        check(ForgeApi.affix(seraph, "MYTHIC", new Random(4)) && affixes.category(seraph) == LootCategory.CHESTPLATE && affixes.read(seraph).levels().size() == 4,
            "armor set piece takes 4 mythic chestplate affixes");
        check(!affixes.salvageable(seraph), "armor set piece can never be salvaged");
        // 6b. Advance Magic staffs: carrot-on-a-stick items with the staff keys, a pool of their own, never salvaged.
        ItemStack fireStaff = staff("staff_element", "fire");
        check(affixes.category(fireStaff) == LootCategory.STAFF && affixes.category(staff("staff_kind", "wood")) == LootCategory.STAFF, "elemental and wooden staffs are staffs");
        check(affixes.category(new ItemStack(Material.CARROT_ON_A_STICK)) == null && affixes.category(staff("staff_kind", "other")) == null, "a plain carrot on a stick is not a staff");
        ItemStack migratedStaff = staff("staff_element", "dark");
        migratedStaff.editMeta(meta -> meta.getPersistentDataContainer().set(new NamespacedKey("advance-magic", "wand"), PersistentDataType.STRING, "wither_ray"));
        check(affixes.category(migratedStaff) == LootCategory.STAFF, "a staff migrated from a retired wand keeps its staff identity");
        Set<AffixType> staffPool = Set.of(AffixType.ARCANE, AffixType.MENDING, AffixType.WELLSPRING, AffixType.FRUGAL, AffixType.FLOW, AffixType.VIGOR, AffixType.FLEET);
        check(Set.copyOf(affixes.pool(LootCategory.STAFF)).equals(staffPool), "staff affix pool: " + affixes.pool(LootCategory.STAFF));
        check(ForgeApi.affix(fireStaff, "MYTHIC", new Random(5)) && staffPool.containsAll(affixes.read(fireStaff).levels().keySet())
            && affixes.read(fireStaff).levels().size() == 4, "mythic staff rolls 4 staff affixes: " + affixes.read(fireStaff));
        check(!affixes.salvageable(fireStaff), "staff can never be salvaged");
        affixes.apply(fireStaff, new AffixData(Rarity.MYTHIC, Map.of(AffixType.ARCANE, 1.0, AffixType.WELLSPRING, 0.5), 2));
        affixes.setGem(fireStaff, 0, new Gem(GemType.FIRE, 3));
        affixes.setGem(fireStaff, 1, new Gem(GemType.LIFE, 1));
        check(Math.abs(affixes.total(fireStaff, Stat.SPELL_POWER) - 0.15) < 1e-9 && Math.abs(affixes.total(fireStaff, Stat.MAX_MANA) - 15) < 1e-9
            && Math.abs(affixes.total(fireStaff, Stat.ELEMENT_FIRE) - 0.03 * 2.1) < 1e-9 && Math.abs(affixes.total(fireStaff, Stat.HEALING_POWER) - 0.03) < 1e-9,
            "staff totals: arcane 15% spell power, wellspring +15 mana, fire III +6.3% fire, life I +3% healing");
        check(lore(fireStaff).contains(" ▸ +15% พลังเวท  (100%)") && lore(fireStaff).stream().anyMatch(l -> l.startsWith(" ◆ อัญมณีเพลิง III: +6.3% พลังธาตุไฟ")),
            "staff lore shows spell stats: " + lore(fireStaff));
        check(ours(fireStaff).isEmpty() && !affixes.ensure(fireStaff), "spell stats add no attribute modifiers and the staff is intact");
        ItemStack staff = new ItemStack(Material.IRON_SWORD);
        staff.editMeta(meta -> meta.getPersistentDataContainer().set(new NamespacedKey("advance-magic", "elemental_staff_fire"), PersistentDataType.STRING, "x"));
        check(affixes.category(staff) == null, "prefix-protected key refused");
        ItemStack m1 = affixes.material(Rarity.RARE, 3), m2 = affixes.material(Rarity.RARE, 5);
        check(affixes.materialRarity(m1) == Rarity.RARE && m1.isSimilar(m2), "rare materials identify and stack");

        // 7. Structure chest loot converts gear and leaves other items alone.
        World world = Bukkit.getWorlds().getFirst();
        Location origin = new Location(world, 0.5, world.getHighestBlockYAt(0, 0) + 1, 0.5);
        List<ItemStack> loot = new ArrayList<>();
        for (int i = 0; i < 60; i++) loot.add(new ItemStack(Material.IRON_SWORD));
        ItemStack lootLegendary = new ItemStack(Material.NETHERITE_SWORD);
        lootLegendary.editMeta(meta -> meta.getPersistentDataContainer().set(new NamespacedKey("advance-magic", "legendary_weapon"), PersistentDataType.STRING, "tyrfing"));
        for (int i = 0; i < 20; i++) loot.add(lootLegendary.clone());
        loot.add(staff("staff_element", "ice"));
        loot.add(new ItemStack(Material.BREAD, 4));
        LootContext context = new LootContext.Builder(origin).build();
        LootGenerateEvent event = new LootGenerateEvent(world, null, null, LootTables.SIMPLE_DUNGEON.getLootTable(), context, loot, false);
        Bukkit.getPluginManager().callEvent(event);
        long converted = event.getLoot().stream().filter(i -> affixes.read(i) != null && i.getType() == Material.IRON_SWORD).count();
        check(converted >= 20 && converted <= 60, "chest converts about 60% of swords: " + converted);
        check(event.getLoot().stream().anyMatch(i -> i.getType() == Material.BREAD && !i.hasItemMeta()), "bread untouched");
        check(event.getLoot().stream().filter(i -> affixes.salvageLocked(i)).allMatch(i -> affixes.read(i) == null), "chests never turn legendary weapons or staffs into affix loot");
        List<ItemStack> pluginLoot = new ArrayList<>(List.of(new ItemStack(Material.IRON_SWORD)));
        Bukkit.getPluginManager().callEvent(new LootGenerateEvent(world, null, null, LootTables.SIMPLE_DUNGEON.getLootTable(), context, pluginLoot, true));
        check(affixes.read(pluginLoot.getFirst()) == null, "plugin-filled chest untouched");

        // 7b. Evergarden chests: 60% gem / 40% affix gear, sometimes several; first open of a non-empty chest in a marked chunk only.
        com.example.forge.EvergardenChestLoot evg = forge.evergardenChests();
        int trials = 20000, anyGem = 0, twoGems = 0, anyGear = 0, twoGear = 0, maxGems = 0;
        Random rolls = new Random(99);
        for (int i = 0; i < trials; i++) {
            List<ItemStack> items = evg.roll(rolls, World.Environment.NORMAL);
            int gemCount = 0, gearCount = 0;
            for (ItemStack item : items) {
                if (gems.read(item) != null) gemCount++;
                else if (affixes.read(item) != null) {
                    gearCount++;
                    String name = item.getType().name();
                    if (name.startsWith("DIAMOND") || name.startsWith("NETHERITE")) throw new AssertionError("overworld evergarden chest gear " + name);
                }
            }
            if (gemCount >= 1) anyGem++;
            if (gemCount >= 2) twoGems++;
            if (gearCount >= 1) anyGear++;
            if (gearCount >= 2) twoGear++;
            maxGems = Math.max(maxGems, gemCount);
        }
        check(Math.abs(anyGem / (double) trials - 0.60) < 0.02, "evergarden chest gem 60%: " + anyGem / (double) trials);
        check(Math.abs(twoGems / (double) trials - 0.15) < 0.015, "2+ gems 15%: " + twoGems / (double) trials);
        check(Math.abs(anyGear / (double) trials - 0.40) < 0.02, "evergarden chest affix gear 40%: " + anyGear / (double) trials);
        check(Math.abs(twoGear / (double) trials - 0.08) < 0.01 && maxGems == 3, "2 gear 8%, at most 3 gems: " + twoGear / (double) trials + " max " + maxGems);

        World chestWorld = Bukkit.getWorlds().getFirst();
        chestWorld.setChunkForceLoaded(10, 10, true);
        chestWorld.setChunkForceLoaded(12, 12, true);
        org.bukkit.Chunk marked = chestWorld.getChunkAt(10, 10), unmarked = chestWorld.getChunkAt(12, 12);
        marked.getPersistentDataContainer().set(new NamespacedKey("evergarden", "whale_treasure_v1"), PersistentDataType.BYTE, (byte) 1);
        check(evg.evergardenChunk(marked) && !evg.evergardenChunk(unmarked), "evergarden chunk marker recognised");
        int baseY = chestWorld.getHighestBlockYAt(160, 160) + 1;
        int decorated = 0;
        for (int i = 0; i < 12; i++) {
            org.bukkit.block.Block block = chestWorld.getBlockAt(160 + i, baseY, 162);
            block.setType(i % 2 == 0 ? Material.CHEST : Material.BARREL);
            ((org.bukkit.block.Container) block.getState()).getInventory().addItem(new ItemStack(Material.BREAD, 3));
            List<ItemStack> added = evg.decorate(block, new Random(1000 + i));
            int inside = 0;
            for (ItemStack item : ((org.bukkit.block.Container) block.getState()).getInventory().getContents()) {
                if (item != null && (gems.read(item) != null || affixes.read(item) != null)) inside++;
            }
            check(inside == added.size(), "decorated items are in the chest");
            passed.removeLast();
            if (!added.isEmpty()) decorated++;
            check(evg.decorate(block, new Random(5)).isEmpty(), "second open adds nothing");
            passed.removeLast();
        }
        passed.add("12 evergarden chests/barrels: items land inside, each decorated once");
        check(decorated >= 5, "most evergarden chests gain loot: " + decorated + "/12");
        org.bukkit.block.Block emptyChest = chestWorld.getBlockAt(160, baseY, 165);
        emptyChest.setType(Material.CHEST);
        check(evg.decorate(emptyChest, new Random(1)).isEmpty(), "empty chest (player's or looted) untouched");
        org.bukkit.block.Block outside = chestWorld.getBlockAt(192, chestWorld.getHighestBlockYAt(192, 192) + 1, 192);
        outside.setType(Material.CHEST);
        ((org.bukkit.block.Container) outside.getState()).getInventory().addItem(new ItemStack(Material.BREAD, 3));
        check(evg.decorate(outside, new Random(1)).isEmpty(), "chest outside an evergarden chunk untouched");
        org.bukkit.block.Block placed = chestWorld.getBlockAt(161, baseY, 166);
        placed.setType(Material.CHEST);
        evg.onPlace(new org.bukkit.event.block.BlockPlaceEvent(placed, placed.getState(), placed,
            new ItemStack(Material.CHEST), null, true, org.bukkit.inventory.EquipmentSlot.HAND));
        ((org.bukkit.block.Container) placed.getState()).getInventory().addItem(new ItemStack(Material.BREAD, 3));
        boolean placedTouched = false;
        for (int i = 0; i < 20; i++) placedTouched |= !evg.decorate(placed, new Random(i)).isEmpty();
        check(!placedTouched, "player-placed chest in an evergarden chunk untouched");

        // End City table outside the End = Evergarden sanctuary chest: normal structure roll skipped, Evergarden roll armed.
        org.bukkit.block.Block sanctuary = chestWorld.getBlockAt(194, outside.getY(), 194);
        sanctuary.setType(Material.CHEST);
        List<ItemStack> sanctuaryLoot = new ArrayList<>();
        for (int i = 0; i < 20; i++) sanctuaryLoot.add(new ItemStack(Material.IRON_SWORD));
        Bukkit.getPluginManager().callEvent(new LootGenerateEvent(chestWorld, null, (org.bukkit.block.Container) sanctuary.getState(),
            LootTables.END_CITY_TREASURE.getLootTable(), new LootContext.Builder(sanctuary.getLocation()).build(), sanctuaryLoot, false));
        check(sanctuaryLoot.stream().noneMatch(i -> affixes.read(i) != null), "sanctuary loot skips the vanilla structure roll");
        ((org.bukkit.block.Container) sanctuary.getState()).getInventory().addItem(new ItemStack(Material.BREAD, 3));
        evg.decorate(sanctuary, new Random(1000));
        NamespacedKey done = new NamespacedKey("evergardenforge", "evg_loot_" + sanctuary.getX() + "_" + sanctuary.getY() + "_" + sanctuary.getZ());
        check(sanctuary.getChunk().getPersistentDataContainer().has(done, PersistentDataType.BYTE) && evg.decorate(sanctuary, new Random(1)).isEmpty(),
            "sanctuary chest (unmarked chunk) decorated on its first open only");

        // 7c. Mining gems: per-block chance, purity I-II only, one roll (no tool input), placed ores marked.
        com.example.forge.OreGemDrops ores = forge.oreGems();
        Map<Material, Double> expected = new LinkedHashMap<>();
        expected.put(Material.COAL_ORE, 0.005);
        expected.put(Material.DEEPSLATE_COPPER_ORE, 0.0075);
        expected.put(Material.LAPIS_ORE, 0.0075);
        expected.put(Material.REDSTONE_ORE, 0.0075);
        expected.put(Material.NETHER_QUARTZ_ORE, 0.0075);
        expected.put(Material.IRON_ORE, 0.015);
        expected.put(Material.DEEPSLATE_GOLD_ORE, 0.015);
        expected.put(Material.NETHER_GOLD_ORE, 0.0025);
        expected.put(Material.DIAMOND_ORE, 0.04);
        expected.put(Material.EMERALD_ORE, 0.04);
        expected.put(Material.ANCIENT_DEBRIS, 0.04);
        Random oreRandom = new Random(321);
        int tierOne = 0, tierTwo = 0;
        for (Map.Entry<Material, Double> entry : expected.entrySet()) {
            int hits = 0, n = 400000;
            for (int i = 0; i < n; i++) {
                Gem gem = ores.roll(entry.getKey(), oreRandom);
                if (gem == null) continue;
                hits++;
                if (gem.tier() == 1) tierOne++;
                else if (gem.tier() == 2) tierTwo++;
                else throw new AssertionError("ore gem purity " + gem.tier());
            }
            double rate = hits / (double) n;
            if (Math.abs(rate - entry.getValue()) > entry.getValue() * 0.12) throw new AssertionError(entry.getKey() + " rate " + rate);
        }
        passed.add("ore gem rates: coal 0.5%, copper/lapis/redstone/quartz 0.75%, iron/gold 1.5%, nether gold 0.25%, diamond/emerald/debris 4%");
        double twoShare = tierTwo / (double) (tierOne + tierTwo);
        check(Math.abs(twoShare - 0.15) < 0.015, "ore gems purity I-II only, II about 15%: " + twoShare);
        int stoneHits = 0;
        for (int i = 0; i < 2000000; i++) if (ores.roll(Material.STONE, oreRandom) != null) stoneHits++;
        check(stoneHits >= 60 && stoneHits <= 145, "stone 0.005%: " + stoneHits + " in 2,000,000");
        check(ores.roll(Material.DIRT, oreRandom) == null && ores.roll(Material.COBBLESTONE, oreRandom) == null, "other blocks never roll");
        World oreWorld = Bukkit.getWorlds().getFirst();
        oreWorld.setChunkForceLoaded(14, 14, true);
        org.bukkit.block.Block ore = oreWorld.getBlockAt(226, oreWorld.getHighestBlockYAt(226, 226) + 1, 226);
        ore.setType(Material.DIAMOND_ORE);
        ores.onPlace(new org.bukkit.event.block.BlockPlaceEvent(ore, ore.getState(), ore,
            new ItemStack(Material.DIAMOND_ORE), null, true, org.bukkit.inventory.EquipmentSlot.HAND));
        check(ore.getChunk().getPersistentDataContainer().has(new NamespacedKey("evergardenforge",
            "ore_placed_" + ore.getX() + "_" + ore.getY() + "_" + ore.getZ()), PersistentDataType.BYTE), "player-placed ore is marked (never rolls)");

        // 7d. Farm guard: rate slides down where many mobs just died; exploring keeps it; environmental damage gives nothing.
        com.example.forge.FarmGuard guard = forge.farmGuard();
        World guardWorld = Bukkit.getWorlds().getFirst();
        long t0 = 1_000_000_000L;
        check(guard.multiplier(0) == 1 && guard.multiplier(5) == 1, "first 5 kills in a spot keep the full rate");
        Location spot = new Location(guardWorld, 9000, 64, 9000);
        for (int i = 0; i < 10; i++) guard.recordDeath(spot, t0 + i * 3_000L);
        double ten = guard.multiplier(guard.heat(spot, t0 + 30_000L));
        check(ten > 0.55 && ten < 0.65, "10 endermen in a row: rate " + Math.round(ten * 100) + "%");
        for (int i = 10; i < 30; i++) guard.recordDeath(spot, t0 + i * 3_000L);
        double thirty = guard.multiplier(guard.heat(spot, t0 + 90_000L));
        check(thirty < 0.15, "30 in a row: rate " + Math.round(thirty * 100) + "%");
        for (int i = 30; i < 300; i++) guard.recordDeath(spot, t0 + i * 3_000L);
        double chamber = guard.multiplier(guard.heat(spot, t0 + 900_000L));
        check(chamber < 0.001, "kill chamber (20 a minute for 15 minutes): rate " + chamber);
        double nextDoor = guard.multiplier(guard.heat(spot.clone().add(16 * 3, 0, 0), t0 + 900_000L));
        check(nextDoor == 1, "3 chunks further on: full rate again");
        double rested = guard.multiplier(guard.heat(spot, t0 + 900_000L + 45 * 60_000L));
        check(rested > 0.9, "the same spot after 45 minutes: rate " + Math.round(rested * 100) + "%");
        // An explorer: a kill every 2 minutes, moving one chunk each time, for an hour.
        double explorerSum = 0;
        for (int i = 0; i < 30; i++) {
            Location here = new Location(guardWorld, 20000 + i * 16, 64, 20000);
            explorerSum += guard.multiplier(guard.heat(here, t0 + i * 120_000L));
            guard.recordDeath(here, t0 + i * 120_000L);
        }
        check(explorerSum / 30 > 0.97, "explorer keeps ~full rate: " + Math.round(explorerSum / 30 * 100) + "%");
        guardWorld.setChunkForceLoaded(0, 0, true);
        Zombie dropped = (Zombie) guardWorld.spawnEntity(new Location(guardWorld, 0.5, guardWorld.getHighestBlockYAt(0, 0) + 1, 0.5), EntityType.ZOMBIE);
        Zombie sunburnt = (Zombie) guardWorld.spawnEntity(new Location(guardWorld, 2.5, guardWorld.getHighestBlockYAt(2, 0) + 1, 0.5), EntityType.ZOMBIE);
        for (Zombie zombie : List.of(dropped, sunburnt)) zombie.setAI(false);
        Bukkit.getPluginManager().callEvent(new org.bukkit.event.entity.EntityDamageEvent(dropped, org.bukkit.event.entity.EntityDamageEvent.DamageCause.FALL,
            org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.FALL).build(), 4));
        Bukkit.getPluginManager().callEvent(new org.bukkit.event.entity.EntityDamageEvent(sunburnt, org.bukkit.event.entity.EntityDamageEvent.DamageCause.FIRE_TICK,
            org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.ON_FIRE).build(), 1));
        boolean droppedEver = false;
        for (int i = 0; i < 200; i++) droppedEver |= guard.rewardable(dropped, new Random(i));
        check(guard.tainted(dropped) && !droppedEver, "fall-damaged mob never gives forge rewards");
        check(!guard.tainted(sunburnt), "a zombie burning in the sun still counts");
        check(Math.abs(forge.settings().gemMobDropChance - 0.01) < 1e-12 && forge.settings().farmOrdinaryGemsPerHour < 0,
            "ordinary mob gem 1%, no hourly cap");
        dropped.remove();
        sunburnt.remove();

        // 8. Elite rewards follow the elite's rarity; Overworld gear stops at iron; rarity tiers by dimension.
        for (int i = 0; i < 500; i++) {
            for (Rarity rarity : Rarity.values()) {
                String name = forge.gear().affixed(rarity, new Random(i * 7L + rarity.ordinal()), World.Environment.NORMAL).getType().name();
                if (name.startsWith("DIAMOND") || name.startsWith("NETHERITE")) throw new AssertionError("overworld " + rarity + " gear is " + name);
            }
        }
        passed.add("2500 overworld gear pieces: none diamond or netherite");
        boolean netherDiamond = false;
        for (int i = 0; i < 2000; i++) {
            for (World.Environment environment : List.of(World.Environment.NETHER, World.Environment.THE_END)) {
                String name = forge.gear().affixed(Rarity.values()[i % 5], new Random(i), environment).getType().name();
                if (name.startsWith("NETHERITE")) throw new AssertionError("netherite gear dropped: " + name);
                netherDiamond |= name.startsWith("DIAMOND");
            }
        }
        check(netherDiamond, "4000 nether/end gear pieces: diamond at most, never netherite");
        check(!forge.settings().allowNetherite && forge.settings().eliteGrades.values().stream()
            .allMatch(grade -> grade.armor().stream().noneMatch(family -> family.equalsIgnoreCase("NETHERITE"))), "elite armor never netherite");
        Map<Rarity, Integer> overworld = forge.settings().eliteWeights(World.Environment.NORMAL);
        Map<Rarity, Integer> nether = forge.settings().eliteWeights(World.Environment.NETHER);
        Map<Rarity, Integer> end = forge.settings().eliteWeights(World.Environment.THE_END);
        check(overworld.get(Rarity.UNCOMMON) > 0 && overworld.get(Rarity.RARE) > 0 && overworld.get(Rarity.EPIC) == 0 && overworld.get(Rarity.MYTHIC) == 0,
            "overworld elites: uncommon and rare only " + overworld);
        check(nether.get(Rarity.EPIC) > 0 && nether.get(Rarity.MYTHIC) > 0 && nether.get(Rarity.RARE) == 0 && end.get(Rarity.MYTHIC) > 0,
            "nether/end elites: epic and mythic " + nether + " " + end);
        check(Math.abs(forge.settings().eliteChance - 0.05) < 1e-9, "elite chance 5%");
        org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason natural = org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.NATURAL,
            chunkGen = org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.CHUNK_GEN, raid = org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.RAID,
            spawner = org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.SPAWNER, spell = org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.SPELL,
            trial = org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.TRIAL_SPAWNER;
        var elites = forge.elites();
        check(elites.chance(EntityType.WARDEN, natural) == 0.05 && elites.chance(EntityType.BLAZE, natural) == 0.05
            && elites.chance(EntityType.GHAST, natural) == 0.05, "warden (shrieker), blaze, ghast can be elites");
        check(elites.chance(EntityType.EVOKER, chunkGen) == 0.05 && elites.chance(EntityType.PIGLIN_BRUTE, chunkGen) == 0.05
            && elites.chance(EntityType.ELDER_GUARDIAN, chunkGen) == 0.05, "mansion evoker, bastion brute, elder guardian can be elites");
        check(elites.chance(EntityType.EVOKER, raid) == 0.025 && elites.chance(EntityType.BREEZE, trial) == 0.025, "raids and trial spawners at half chance");
        check(elites.chance(EntityType.BLAZE, spawner) == 0 && elites.chance(EntityType.VEX, spell) == 0
            && elites.chance(EntityType.ENDER_DRAGON, natural) == 0 && elites.chance(EntityType.WITHER, natural) == 0,
            "spawner blazes, evoker vexes, dragon and wither never");
        check(ForgeApi.dust(4) != null && gems.isDust(ForgeApi.dust(4)) && ForgeApi.dust(4).getAmount() == 4, "API gives gem dust");
        for (Rarity rarity : List.of(Rarity.UNCOMMON, Rarity.RARE, Rarity.EPIC, Rarity.MYTHIC)) {
            List<ItemStack> rewards = forge.elites().rewards(rarity, new Random(rarity.ordinal()), World.Environment.NETHER);
            long gear = rewards.stream().filter(i -> affixes.read(i) != null && affixes.read(i).rarity() == rarity).count();
            check(gear >= 1, rarity + " elite drops " + rarity + " gear: " + rewards.stream().map(i -> i.getType().name()).toList());
        }
        check(forge.elites().rewards(Rarity.MYTHIC, new Random(5), World.Environment.NETHER).stream().anyMatch(i -> gems.read(i) != null
            && gems.read(i).tier() >= 3), "mythic elite always drops a purity III+ gem");

        // 9. Attributes reach living wearers; helmet and boots VIGOR do not collide; shields work in the off hand;
        //    an elite gets its health multiplier.
        Map<AffixType, Double> vigor = Map.of(AffixType.VIGOR, 1.0);
        ItemStack helmet = new ItemStack(Material.IRON_HELMET), boots = new ItemStack(Material.IRON_BOOTS), shield = new ItemStack(Material.SHIELD);
        affixes.apply(helmet, new AffixData(Rarity.COMMON, vigor));
        affixes.apply(boots, new AffixData(Rarity.COMMON, vigor));
        affixes.apply(shield, new AffixData(Rarity.COMMON, vigor, 1));
        affixes.setGem(shield, 0, new Gem(GemType.LIFE, 1));
        ItemStack strong = new ItemStack(Material.DIAMOND_SWORD);
        affixes.apply(strong, new AffixData(Rarity.MYTHIC, Map.of(AffixType.SHARP, 1.0)));
        world.setChunkForceLoaded(0, 0, true);
        Zombie plain = (Zombie) world.spawnEntity(origin, EntityType.ZOMBIE);
        Zombie geared = (Zombie) world.spawnEntity(origin.clone().add(3, 0, 3), EntityType.ZOMBIE);
        Zombie elite = (Zombie) world.spawnEntity(origin.clone().add(-3, 0, 3), EntityType.ZOMBIE);
        Zombie epic = (Zombie) world.spawnEntity(origin.clone().add(-3, 0, -3), EntityType.ZOMBIE);
        org.bukkit.entity.Warden warden = (org.bukkit.entity.Warden) world.spawnEntity(origin.clone().add(6, 0, -6), EntityType.WARDEN);
        warden.setAI(false);
        double wardenDamage = warden.getAttribute(Attribute.ATTACK_DAMAGE).getValue();
        check(ForgeApi.makeElite(warden, "RARE"), "warden elite made");
        for (Zombie zombie : List.of(plain, geared, elite, epic)) {
            zombie.setAI(false);
            zombie.setPersistent(true);
        }
        plain.getEquipment().setItemInMainHand(new ItemStack(Material.DIAMOND_SWORD));
        geared.getEquipment().setItemInMainHand(strong);
        geared.getEquipment().setItemInOffHand(shield);
        geared.getEquipment().setHelmet(helmet);
        geared.getEquipment().setBoots(boots);
        check(ForgeApi.makeElite(elite, "RARE") && !ForgeApi.makeElite(elite, "EPIC"), "elite made once");
        check(ForgeApi.makeElite(epic, "EPIC"), "epic elite made");
        boolean epicArmor = true;
        for (ItemStack piece : epic.getEquipment().getArmorContents()) {
            epicArmor &= piece != null && piece.getType().name().startsWith("DIAMOND");
        }
        check(epicArmor && epic.getEquipment().getHelmetDropChance() == 0f, "epic elite wears full diamond, never dropped");
        check(elite.customName() != null && PLAIN.serialize(elite.customName()).startsWith("✦ หายาก"), "elite name: " + PLAIN.serialize(elite.customName()));
        Bukkit.getScheduler().runTaskLater(this, () -> {
            try {
                double damage = geared.getAttribute(Attribute.ATTACK_DAMAGE).getValue() - plain.getAttribute(Attribute.ATTACK_DAMAGE).getValue();
                double health = geared.getAttribute(Attribute.MAX_HEALTH).getValue() - plain.getAttribute(Attribute.MAX_HEALTH).getValue();
                check(Math.abs(damage - 3.0) < 1e-6, "sharp +3 damage reaches the wearer: " + damage);
                check(Math.abs(health - (2.5 + 2.5 + 2.5 + 0.8)) < 1e-6, "helmet + boots + off-hand shield (+life gem) stack to +8.3 health: " + health);
                double eliteHealth = elite.getAttribute(Attribute.MAX_HEALTH).getValue();
                check(Math.abs(eliteHealth - 20 * 4.0) < 1e-6 && Math.abs(elite.getHealth() - eliteHealth) < 1e-6, "rare elite has 4x health (+300%), full: " + eliteHealth);
                double eliteDamage = elite.getAttribute(Attribute.ATTACK_DAMAGE).getValue();
                check(Math.abs(eliteDamage - 3.0 * 4.0) < 1e-6, "rare elite deals 4x damage: " + eliteDamage);
                check(Math.abs(epic.getAttribute(Attribute.MAX_HEALTH).getValue() - 20 * 6.0) < 1e-6, "epic elite has 6x health (+500%)");
                double wardenHealth = warden.getAttribute(Attribute.MAX_HEALTH).getValue();
                // 500 x 4 = 2000, but vanilla caps max health at 1024.
                check(Math.abs(wardenHealth - 1024) < 1e-6 && Math.abs(warden.getAttribute(Attribute.ATTACK_DAMAGE).getValue() - wardenDamage) < 1e-6,
                    "rare warden elite: health " + wardenHealth + " (vanilla cap 1024), damage unchanged " + wardenDamage);
                check(forge.elites().count(world) == 3, "three elites tracked");
                magicGear(forge);
                additionalChecks(forge);
                finish("PASS " + passed.size() + " forge checks\n" + String.join("\n", passed));
            } catch (Throwable error) {
                finish("FAIL " + error + "\npassed before failure: " + passed);
            }
        }, 20L);
    }
    protected void additionalChecks(ForgePlugin forge)throws Exception {}

    /** Real Advance Magic items, when run.py --with-core compiled ForgeMagicGearChecks against Advance Magic. */
    private void magicGear(ForgePlugin forge) throws Throwable {
        Class<?> checks;
        try {
            checks = Class.forName("ForgeMagicGearChecks");
        } catch (ClassNotFoundException absent) {
            return;
        }
        var run = checks.getDeclaredMethod("run", ForgeProbe.class, ForgePlugin.class);
        run.setAccessible(true);
        try {
            run.invoke(null, this, forge);
        } catch (java.lang.reflect.InvocationTargetException error) {
            throw error.getCause();
        }
    }

    private static ItemStack staff(String key, String value) {
        ItemStack item = new ItemStack(Material.CARROT_ON_A_STICK);
        item.editMeta(meta -> meta.getPersistentDataContainer().set(new NamespacedKey("advance_magic", key), PersistentDataType.STRING, value));
        return item;
    }
}
