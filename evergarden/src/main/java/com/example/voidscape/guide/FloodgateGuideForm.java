package com.example.voidscape.guide;

import com.example.voidscape.VoidscapePlugin;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.geysermc.cumulus.form.SimpleForm;
import org.geysermc.floodgate.api.FloodgateApi;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class FloodgateGuideForm {
    private FloodgateGuideForm() {}

    /**
     * Re-colors text for Bedrock SimpleForm modals.
     * Bedrock uses dark translucent grey form backgrounds, so dark/black colors (like §0, §8, §1)
     * are completely illegible. This transforms them into bright, crisp white/aqua/gold colors.
     */
    public static String formatBedrockText(String text) {
        if (text == null || text.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        String[] lines = text.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i]
                    .replace("§0", "§f")   // Black -> Crisp White
                    .replace("§8", "§7")   // Dark Gray -> Light Gray
                    .replace("§1", "§b")   // Dark Blue -> Aqua
                    .replace("§2", "§a")   // Dark Green -> Light Green
                    .replace("§4", "§c")   // Dark Red -> Light Red
                    .replace("§5", "§d")   // Dark Purple -> Light Purple
                    .replace("§r", "§r§f"); // Reset -> Crisp White

            if (!line.isEmpty() && !line.startsWith("§")) {
                line = "§f" + line;
            }
            sb.append(line);
            if (i < lines.length - 1) {
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    public static boolean sendBookSelector(VoidscapePlugin plugin, Player player) {
        SimpleForm.Builder builder = SimpleForm.builder();
        builder.title("§e§lเลือกคู่มือมิติ Evergarden");
        builder.content("§fระบบคู่มือถูกแบ่งออกเป็น 3 เล่มเพื่อความสะดวกในการศึกษา:\n§7กรุณาเลือกเล่มที่ต้องการอ่านด้านล่างนี้");

        List<Consumer<Player>> buttonActions = new ArrayList<>();

        for (GuideBookType type : GuideBookType.values()) {
            builder.button("§6§l" + type.bookTitle + "\n§7" + type.description);
            buttonActions.add(p -> BedrockGuideService.openGuide(plugin, p, type, 0));
        }

        builder.button("❌ ปิดเมนู");
        buttonActions.add(p -> {});

        builder.validResultHandler(response -> {
            int buttonId = response.clickedButtonId();
            if (buttonId >= 0 && buttonId < buttonActions.size()) {
                Consumer<Player> action = buttonActions.get(buttonId);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    action.accept(player);
                    player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.8f, 1.0f);
                });
            }
        });

        return FloodgateApi.getInstance().sendForm(player.getUniqueId(), builder.build());
    }

    public static boolean sendPage(VoidscapePlugin plugin, Player player, GuideBookType type, int pageIndex) {
        if (type == null) type = GuideBookType.CROPS;
        List<GuidePage> pages = GuideData.pagesFor(type);
        if (pageIndex < 0) pageIndex = 0;
        if (pageIndex >= pages.size()) pageIndex = pages.size() - 1;

        GuidePage page = pages.get(pageIndex);
        int finalPageIndex = pageIndex;
        int totalPages = pages.size();
        GuideBookType finalType = type;

        SimpleForm.Builder builder = SimpleForm.builder();
        builder.title("§e§l" + type.bookTitle + " §7[§f" + (finalPageIndex + 1) + "§7/§f" + totalPages + "§7]");
        builder.content(formatBedrockText(page.content()));

        List<Consumer<Player>> buttonActions = new ArrayList<>();

        // Next page button
        if (finalPageIndex < totalPages - 1) {
            builder.button("➡️ หน้าถัดไป (" + (finalPageIndex + 2) + "/" + totalPages + ")");
            buttonActions.add(p -> BedrockGuideService.openGuide(plugin, p, finalType, finalPageIndex + 1));
        }

        // Previous page button
        if (finalPageIndex > 0) {
            builder.button("⬅️ หน้าก่อนหน้า (" + finalPageIndex + "/" + totalPages + ")");
            buttonActions.add(p -> BedrockGuideService.openGuide(plugin, p, finalType, finalPageIndex - 1));
        }

        // Table of Contents button
        builder.button("📑 สารบัญหัวข้อ (" + type.bookTitle + ")");
        buttonActions.add(p -> BedrockGuideService.openIndex(plugin, p, finalType, finalPageIndex));

        // Switch book button
        builder.button("📚 เลือกอ่านเล่มอื่น (3 เล่ม)");
        buttonActions.add(p -> sendBookSelector(plugin, p));

        // Close button
        builder.button("❌ ปิดคู่มือ");
        buttonActions.add(p -> {});

        builder.validResultHandler(response -> {
            int buttonId = response.clickedButtonId();
            if (buttonId >= 0 && buttonId < buttonActions.size()) {
                Consumer<Player> action = buttonActions.get(buttonId);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    action.accept(player);
                    player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.8f, 1.0f);
                });
            }
        });

        return FloodgateApi.getInstance().sendForm(player.getUniqueId(), builder.build());
    }

    public static boolean sendPage(VoidscapePlugin plugin, Player player, int pageIndex) {
        return sendPage(plugin, player, GuideBookType.CROPS, pageIndex);
    }

    public static boolean sendIndex(VoidscapePlugin plugin, Player player, GuideBookType type, int returnPageIndex) {
        if (type == null) type = GuideBookType.CROPS;
        List<GuidePage> pages = GuideData.pagesFor(type);
        GuideBookType finalType = type;

        SimpleForm.Builder builder = SimpleForm.builder();
        builder.title("§e§lสารบัญ: " + type.bookTitle);
        builder.content("§fเลือกหัวข้อที่ต้องการอ่านเพื่อเปิดหน้านั้นได้ทันที:\n§7(หน้าที่อ่านค้างอยู่: §eหน้า " + (returnPageIndex + 1) + "§7)");

        List<Consumer<Player>> buttonActions = new ArrayList<>();

        for (int i = 0; i < pages.size(); i++) {
            int targetPage = i;
            String prefix = (i == returnPageIndex) ? "§6▶ " : "§f";
            builder.button(prefix + (i + 1) + ". " + pages.get(i).title());
            buttonActions.add(p -> BedrockGuideService.openGuide(plugin, p, finalType, targetPage));
        }

        builder.button("⬅️ กลับไปหน้าที่อ่านค้างไว้ (หน้า " + (returnPageIndex + 1) + ")");
        buttonActions.add(p -> BedrockGuideService.openGuide(plugin, p, finalType, returnPageIndex));

        builder.button("📚 เลือกอ่านเล่มอื่น (3 เล่ม)");
        buttonActions.add(p -> sendBookSelector(plugin, p));

        builder.button("❌ ปิด");
        buttonActions.add(p -> {});

        builder.validResultHandler(response -> {
            int buttonId = response.clickedButtonId();
            if (buttonId >= 0 && buttonId < buttonActions.size()) {
                Consumer<Player> action = buttonActions.get(buttonId);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    action.accept(player);
                    player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.8f, 1.0f);
                });
            }
        });

        return FloodgateApi.getInstance().sendForm(player.getUniqueId(), builder.build());
    }

    public static boolean sendIndex(VoidscapePlugin plugin, Player player, int returnPageIndex) {
        return sendIndex(plugin, player, GuideBookType.CROPS, returnPageIndex);
    }
}
