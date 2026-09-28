package com.example.sevensins;

import org.bukkit.Color;
import java.util.Locale;

/** Shared tuning scale; every new sin has its own silhouette and combat signature. */
public enum SinType {
    WRATH("Wrath", "The Ashen Executioner", 0xe13b24, 1800, 0.42, 3.0),
    PRIDE("Pride", "The Gilded Regent", 0xf4c644, 1900, 0.43, 3.0),
    GREED("Greed", "The Coinbound Devourer", 0xe2a530, 2200, 0.40, 3.1),
    LUST("Lust", "The Crimson Enchantress", 0xd74b86, 1750, 0.47, 3.0),
    ENVY("Envy", "The Mirror Wraith", 0x4bd994, 1700, 0.49, 3.3),
    GLUTTONY("Gluttony", "The Abyssal Maw", 0x85c756, 2250, 0.40, 3.0),
    SLOTH("Sloth", "The Gravebound Colossus", 0x9b81d5, 2200, 0.38, 3.0);

    private final String title, epithet;
    private final Color color;
    private final double health, speed, damage;
    SinType(String title, String epithet, int rgb, double health, double speed, double damage) {
        this.title=title;this.epithet=epithet;this.color=Color.fromRGB(rgb);
        this.health=health;this.speed=speed;this.damage=damage;
    }
    public String id() { return name().toLowerCase(Locale.ROOT); }
    public String title() { return title; }
    public String epithet() { return epithet; }
    public String display() { return title.toUpperCase(Locale.ROOT)+" · "+epithet.toUpperCase(Locale.ROOT); }
    public Color color() { return color; }
    public double health() { return health; }
    public double speed() { return speed; }
    public double damage() { return damage; }
    public static SinType parse(String value) {
        if (value == null) return null;
        try { return valueOf(value.toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException error) { return null; }
    }
}
