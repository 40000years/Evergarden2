package com.example.forge;

import com.example.forge.GemService.Gem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

public final class ForgeCommand implements TabExecutor {
    private static final List<String> PAGES = List.of("salvage", "gems", "cut", "codex", "charm");
    private static final List<String> ADMIN = List.of("affix", "material", "gem", "dust", "gear", "elite", "reload", "pack");

    private final ForgePlugin plugin;

    public ForgeCommand(ForgePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        boolean admin = sender.hasPermission("forge.admin");
        String sub = switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "salvage" -> "salvage";
            case "gems" -> "codex";
            default -> args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        };
        switch (sub) {
            case "", "salvage", "gems", "cut", "codex" -> {
                if (!(sender instanceof Player player)) return message(sender, "ใช้ได้เฉพาะผู้เล่น", NamedTextColor.RED);
                if (!player.hasPermission("forge.use")) return message(sender, "ไม่มีสิทธิ์ใช้โรงตีเหล็ก", NamedTextColor.RED);
                ForgeMenu.Page page = switch (sub) {
                    case "salvage" -> ForgeMenu.Page.SALVAGE;
                    case "gems" -> ForgeMenu.Page.GEMS;
                    case "cut" -> ForgeMenu.Page.CUT;
                    case "codex" -> ForgeMenu.Page.CODEX;
                    default -> ForgeMenu.Page.REFORGE;
                };
                // The codex is a reference page: always open. Working pages may require the station block.
                if (page != ForgeMenu.Page.CODEX && !plugin.settings().commandAnywhere && !admin) {
                    return message(sender, "ย่อตัว + คลิกขวาที่โต๊ะช่างตีเหล็ก (Smithing Table) เพื่อเปิดโรงตีเหล็ก", NamedTextColor.YELLOW);
                }
                plugin.menu().open(player, page);
                return true;
            }
            case "charm", "charms" -> {
                if (args.length <= 1) {
                    if (!(sender instanceof Player player)) return message(sender, "ใช้ได้เฉพาะผู้เล่น", NamedTextColor.RED);
                    if (!player.hasPermission("forge.use")) return message(sender, "ไม่มีสิทธิ์ใช้โรงตีเหล็ก", NamedTextColor.RED);
                    plugin.charmMenu().open(player);
                    return true;
                }
                if (!admin) return message(sender, "/forge charm · เปิดหน้า Charm", NamedTextColor.YELLOW);
                Charm charm = Charm.parse(args[1]);
                if (charm == null && !args[1].equalsIgnoreCase("random")) return message(sender, "/forge charm <id|random> [rarity] [player]", NamedTextColor.YELLOW);
                if (charm == null) charm = Charm.values()[java.util.concurrent.ThreadLocalRandom.current().nextInt(Charm.values().length)];
                Rarity grade = args.length >= 3 ? Rarity.parse(args[2]) : Rarity.COMMON;
                if (grade == null) return message(sender, "เกรดไม่ถูกต้อง: " + args[2], NamedTextColor.RED);
                Player target = args.length >= 4 ? Bukkit.getPlayerExact(args[3]) : sender instanceof Player p ? p : null;
                if (target == null) return message(sender, "ผู้เล่นไม่ถูกต้อง", NamedTextColor.RED);
                ItemStack item = plugin.charms().item(charm, grade);
                target.getInventory().addItem(item).values().forEach(left -> target.getWorld().dropItemNaturally(target.getLocation(), left));
                return message(sender, "ให้ Charm " + charm.title + " (" + grade.label + ") แก่ " + target.getName(), NamedTextColor.GREEN);
            }
            case "inspect" -> {
                if (!(sender instanceof Player player)) return message(sender, "ใช้ได้เฉพาะผู้เล่น", NamedTextColor.RED);
                ItemStack held = player.getInventory().getItemInMainHand();
                Gem gem = plugin.gems().read(held);
                if (gem != null) return message(sender, "Gem: " + gem.serialize(), NamedTextColor.GOLD);
                AffixData data = plugin.affixes().read(held);
                if (data == null) {
                    String why = plugin.affixes().category(held) == null ? "รับ Affix ไม่ได้" : "ยังไม่มี Affix (รีฟอร์จได้)";
                    return message(sender, "ของในมือ: " + why, NamedTextColor.GRAY);
                }
                message(sender, "เกรด " + data.rarity().label + " (" + data.rarity() + ") · ช่อง Gem " + data.sockets(), NamedTextColor.GOLD);
                data.levels().forEach((type, level) -> message(sender, " " + type.id() + " level=" + Math.round(level * 1000) / 1000.0
                    + " → " + type.stat.format(plugin.affixes().value(type, level)), NamedTextColor.GRAY));
                List<Gem> gems = plugin.affixes().gems(held);
                for (int i = 0; i < gems.size(); i++) message(sender, " socket " + (i + 1) + ": " + (gems.get(i) == null ? "ว่าง" : gems.get(i).serialize()), NamedTextColor.GRAY);
                return true;
            }
            default -> {
                if (!ADMIN.contains(sub)) {
                    return message(sender, "/forge [salvage|gems|cut|codex|inspect" + (admin ? "|" + String.join("|", ADMIN) : "") + "]", NamedTextColor.YELLOW);
                }
                if (!admin) return message(sender, "ต้องเป็นแอดมิน", NamedTextColor.RED);
                return adminCommand(sender, sub, args);
            }
        }
    }

    private boolean adminCommand(CommandSender sender, String sub, String[] args) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        switch (sub) {
            case "pack" -> {
                if (sender instanceof Player player) plugin.resources().offer(player, true);
                return message(sender, plugin.resources().status(), NamedTextColor.GOLD);
            }
            case "reload" -> {
                plugin.reload();
                return message(sender, "โหลด config ของ EvergardenForge ใหม่แล้ว", NamedTextColor.GREEN);
            }
            case "affix" -> {
                if (!(sender instanceof Player player) || args.length < 2) return message(sender, "/forge affix <rarity>", NamedTextColor.YELLOW);
                Rarity rarity = Rarity.parse(args[1]);
                if (rarity == null) return message(sender, "เกรดไม่ถูกต้อง: " + args[1], NamedTextColor.RED);
                ItemStack held = player.getInventory().getItemInMainHand();
                if (!plugin.affixes().affix(held, rarity, random)) return message(sender, "ของในมือรับ Affix ไม่ได้", NamedTextColor.RED);
                player.getInventory().setItemInMainHand(held);
                return message(sender, "สุ่ม Affix เกรด " + rarity.label + " ให้ของในมือแล้ว", NamedTextColor.GREEN);
            }
            case "material" -> {
                if (args.length < 2) return message(sender, "/forge material <rarity> [amount] [player]", NamedTextColor.YELLOW);
                Rarity rarity = Rarity.parse(args[1]);
                if (rarity == null) return message(sender, "เกรดไม่ถูกต้อง: " + args[1], NamedTextColor.RED);
                return give(sender, args, 2, plugin.affixes().material(rarity, 1), 16);
            }
            case "gem" -> {
                if (args.length < 3) return message(sender, "/forge gem <type|random> <tier 1-5> [amount] [player]", NamedTextColor.YELLOW);
                GemType type = args[1].equalsIgnoreCase("random") ? GemType.values()[random.nextInt(GemType.values().length)] : GemType.parse(args[1]);
                Integer tier = number(args[2]);
                if (type == null || tier == null) return message(sender, "Gem หรือขั้นไม่ถูกต้อง", NamedTextColor.RED);
                return give(sender, args, 3, plugin.gems().item(new Gem(type, tier), 1), 1);
            }
            case "dust" -> {
                return give(sender, args, 1, plugin.gems().dust(1), 16);
            }
            case "gear" -> {
                if (args.length < 2) return message(sender, "/forge gear <rarity> [amount] [player]", NamedTextColor.YELLOW);
                Rarity rarity = Rarity.parse(args[1]);
                if (rarity == null) return message(sender, "เกรดไม่ถูกต้อง: " + args[1], NamedTextColor.RED);
                Integer amount = args.length >= 3 ? number(args[2]) : Integer.valueOf(1);
                Player target = args.length >= 4 ? Bukkit.getPlayerExact(args[3]) : sender instanceof Player p ? p : null;
                if (amount == null || target == null) return message(sender, "จำนวนหรือผู้เล่นไม่ถูกต้อง", NamedTextColor.RED);
                for (int i = 0; i < Math.clamp(amount, 1, 27); i++) {
                    target.getInventory().addItem(plugin.gear().affixed(rarity, random, target.getWorld().getEnvironment()))
                        .values().forEach(left -> target.getWorld().dropItemNaturally(target.getLocation(), left));
                }
                return message(sender, "ให้อุปกรณ์เกรด " + rarity.label + " ×" + amount + " แก่ " + target.getName(), NamedTextColor.GREEN);
            }
            case "elite" -> {
                if (!(sender instanceof Player player) || args.length < 2) return message(sender, "/forge elite <rarity> [entity]", NamedTextColor.YELLOW);
                Rarity rarity = Rarity.parse(args[1]);
                if (rarity == null || !plugin.settings().eliteGrades.containsKey(rarity)) return message(sender, "เกรด Elite: uncommon rare epic mythic", NamedTextColor.RED);
                EntityType type = EntityType.ZOMBIE;
                if (args.length >= 3) {
                    try {
                        type = EntityType.valueOf(args[2].toUpperCase(Locale.ROOT));
                    } catch (IllegalArgumentException e) {
                        return message(sender, "ไม่รู้จัก entity: " + args[2], NamedTextColor.RED);
                    }
                }
                return plugin.elites().spawn(player, type, rarity) != null
                    ? message(sender, "เรียก Elite " + rarity.label + " (" + type.name().toLowerCase(Locale.ROOT) + ")", NamedTextColor.GREEN)
                    : message(sender, "เรียก Elite ไม่สำเร็จ", NamedTextColor.RED);
            }
            default -> {
                return false;
            }
        }
    }

    /** Gives `template` × [amount at args[index]] to [player at args[index+1]] or the sender. */
    private boolean give(CommandSender sender, String[] args, int index, ItemStack template, int defaultAmount) {
        Integer amount = args.length > index ? number(args[index]) : Integer.valueOf(defaultAmount);
        Player target = args.length > index + 1 ? Bukkit.getPlayerExact(args[index + 1]) : sender instanceof Player p ? p : null;
        if (amount == null || target == null) return message(sender, "จำนวนหรือผู้เล่นไม่ถูกต้อง", NamedTextColor.RED);
        ItemStack stack = template.clone();
        stack.setAmount(Math.clamp(amount, 1, stack.getMaxStackSize()));
        target.getInventory().addItem(stack).values().forEach(left -> target.getWorld().dropItemNaturally(target.getLocation(), left));
        return message(sender, "ให้ " + plainName(stack) + " ×" + stack.getAmount() + " แก่ " + target.getName(), NamedTextColor.GREEN);
    }

    private static String plainName(ItemStack item) {
        Component name = item.getItemMeta().customName();
        return name == null ? item.getType().name() : net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(name);
    }

    private static Integer number(String text) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean message(CommandSender sender, String text, NamedTextColor color) {
        sender.sendMessage(Component.text(text, color));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!command.getName().equalsIgnoreCase("forge")) return List.of();
        boolean admin = sender.hasPermission("forge.admin");
        List<String> options = new ArrayList<>();
        String first = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 1) {
            options.addAll(PAGES);
            options.add("inspect");
            if (admin) options.addAll(ADMIN);
        } else if (admin && args.length == 2 && (first.equals("charm") || first.equals("charms"))) {
            options.add("random");
            Arrays.stream(Charm.values()).map(Charm::id).forEach(options::add);
        } else if (admin && args.length == 3 && (first.equals("charm") || first.equals("charms"))) {
            Arrays.stream(Rarity.values()).map(Rarity::id).forEach(options::add);
        } else if (admin && args.length == 2 && List.of("affix", "material", "gear", "elite").contains(first)) {
            Arrays.stream(Rarity.values()).map(Rarity::id).forEach(options::add);
        } else if (admin && args.length == 2 && first.equals("gem")) {
            options.add("random");
            Arrays.stream(GemType.values()).map(GemType::id).forEach(options::add);
        } else if (admin && args.length == 3 && first.equals("gem")) {
            options.addAll(List.of("1", "2", "3", "4", "5"));
        } else if (admin && args.length == 3 && first.equals("elite")) {
            plugin.settings().eliteTypes.forEach(type -> options.add(type.name().toLowerCase(Locale.ROOT)));
        }
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        return options.stream().filter(option -> option.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }
}
