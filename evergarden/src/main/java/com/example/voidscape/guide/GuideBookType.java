package com.example.voidscape.guide;

import org.bukkit.Material;

public enum GuideBookType {
    RELICS(
        "คู่มือยุทธภัณฑ์ Evergarden",
        "Evergarden Relics & Vault Guide",
        "ผู้พิทักษ์วิหาร",
        Material.NETHERITE_SWORD,
        "§6การเดินทาง บอส แผนที่ สมบัติ และสัตว์เลี้ยง"
    ),
    MAGIC(
        "คัมภีร์เวทมนตร์ Advance Magic",
        "Advance Magic Arcana",
        "มหาจอมเวทบรรพกาล",
        Material.BLAZE_ROD,
        "§bคทา เวท มานา Skill Tree และการฟื้นฟู"
    );

    public final String bookTitle;
    public final String englishTitle;
    public final String author;
    public final Material icon;
    public final String description;

    GuideBookType(String bookTitle, String englishTitle, String author, Material icon, String description) {
        this.bookTitle = bookTitle;
        this.englishTitle = englishTitle;
        this.author = author;
        this.icon = icon;
        this.description = description;
    }

    public static GuideBookType fromId(String id) {
        if (id == null) return null;
        String s = id.trim().toUpperCase();
        if (s.equals("2") || s.contains("RELIC") || s.contains("ITEM") || s.contains("VAULT") || s.contains("SANCTUM")) return RELICS;
        if (s.equals("3") || s.contains("MAGIC") || s.contains("WAND") || s.contains("CORE")) return MAGIC;
        return null;
    }
}
