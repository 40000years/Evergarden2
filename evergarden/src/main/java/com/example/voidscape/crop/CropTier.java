package com.example.voidscape.crop;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.ChatColor;

public enum CropTier {
    TIER_1("Tier I · พืชเกษตรทั่วไป", NamedTextColor.GREEN, ChatColor.GREEN, 180),
    TIER_2("Tier II · พืชสงครามล่าสังหาร", NamedTextColor.RED, ChatColor.RED, 300),
    TIER_3("Tier III · พืชข้ามมิติเอาชีวิตรอด", NamedTextColor.DARK_PURPLE, ChatColor.DARK_PURPLE, 450),
    TIER_4("Tier IV · พืชเหมืองแร่ & กาลเวลา", NamedTextColor.GOLD, ChatColor.GOLD, 600),
    TIER_5("Tier V · พืชเวทมนตร์บรรพกาล [MYTHIC]", NamedTextColor.LIGHT_PURPLE, ChatColor.LIGHT_PURPLE, 900);

    public final String title;
    public final NamedTextColor textColor;
    public final ChatColor chatColor;
    public final int growthSeconds;

    CropTier(String title, NamedTextColor textColor, ChatColor chatColor, int growthSeconds) {
        this.title = title;
        this.textColor = textColor;
        this.chatColor = chatColor;
        this.growthSeconds = growthSeconds;
    }

    public String formattedTime() {
        int m = growthSeconds / 60;
        int s = growthSeconds % 60;
        return s > 0 ? (m + " นาที " + s + " วินาที") : (m + " นาที");
    }
}
