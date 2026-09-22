package com.example.voidscape.crop;

import com.example.voidscape.VoidscapePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

public final class CropItemFactory {
    private final VoidscapePlugin plugin;
    private final NamespacedKey seedKey;
    private final NamespacedKey foodKey;

    public CropItemFactory(VoidscapePlugin plugin) {
        this.plugin = plugin;
        this.seedKey = plugin.key("crop_seed");
        this.foodKey = plugin.key("crop_food");
    }

    private boolean vanillaMovement(){return !"enhanced".equalsIgnoreCase(plugin.getConfig().getString("compatibility.crop-movement","vanilla"));}
    private String summary(CropType type){
        if(vanillaMovement()&&type==CropType.FAIRY_MUSHROOM)return "Jump Boost II · กระโดดสูงขึ้น 3 นาที";
        if(vanillaMovement()&&type==CropType.MOUNTAIN_WALKER_BAMBOO)return "Jump Boost I · กระโดดข้ามเนิน 5 นาที";
        return type.summary;
    }
    private List<String> lore(CropType type){
        if(vanillaMovement()&&(type==CropType.FAIRY_MUSHROOM||type==CropType.MOUNTAIN_WALKER_BAMBOO))
            return List.of("§a✦ "+summary(type),"§7กดกระโดดตามปกติเพื่อรับแรงกระโดดที่สูงขึ้น", "§8ระยะเวลาเติบโต: 3 นาที · ปลูกบน Farmland");
        return type.lore;
    }

    public ItemStack createSeed(CropType type, int amount) {
        ItemStack item = new ItemStack(type.seedMaterial, Math.max(1, Math.min(64, amount)));
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.displayName(Component.text(type.tier.chatColor + "✦ เมล็ด" + type.thaiName + " (Seed of " + type.englishName + ")")
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(type.tier.chatColor + "[" + type.tier.title + "]").decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text(ChatColor.YELLOW + "✦ " + summary(type)).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("").decoration(TextDecoration.ITALIC, false));
        for (String line : lore(type)) {
            lore.add(Component.text(line).decoration(TextDecoration.ITALIC, false));
        }
        lore.add(Component.text("").decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text(ChatColor.GRAY + "วิธีปลูก: คลิกขวาลงบนแปลงดินพรวน (Farmland)").decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text(ChatColor.DARK_PURPLE + "EVERGARDEN · CROP SEED").decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);

        var modelData = meta.getCustomModelDataComponent();
        modelData.setStrings(List.of("voidscape:seed_" + type.id));
        meta.setCustomModelDataComponent(modelData);
        meta.setCustomModelData(10000 + type.ordinal());
        meta.setItemModel(new NamespacedKey("voidscape", "seed_" + type.id));

        meta.getPersistentDataContainer().set(seedKey, PersistentDataType.STRING, type.id);
        meta.getPersistentDataContainer().set(new NamespacedKey("evergarden", "crop_seed"), PersistentDataType.STRING, type.id);

        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createFood(CropType type, int amount) {
        ItemStack item = new ItemStack(type.foodMaterial, Math.max(1, Math.min(64, amount)));
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.displayName(Component.text(type.tier.chatColor + "✦ " + type.thaiName + " (" + type.englishName + ")")
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(type.tier.chatColor + "[" + type.tier.title + "]").decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text(ChatColor.GOLD + "✦ " + summary(type)).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("").decoration(TextDecoration.ITALIC, false));
        for (String line : lore(type)) {
            lore.add(Component.text(line).decoration(TextDecoration.ITALIC, false));
        }
        lore.add(Component.text("").decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text(ChatColor.GREEN + "วิธีใช้: กดคลิกขวาเพื่อกิน/ดื่ม (รับพลังบัฟทันที)").decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text(ChatColor.DARK_PURPLE + "EVERGARDEN · HARVESTED CROP").decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);

        var modelData = meta.getCustomModelDataComponent();
        modelData.setStrings(List.of("voidscape:crop_" + type.id));
        meta.setCustomModelDataComponent(modelData);
        meta.setCustomModelData(20000 + type.ordinal());
        meta.setItemModel(new NamespacedKey("voidscape", "crop_" + type.id));

        meta.getPersistentDataContainer().set(foodKey, PersistentDataType.STRING, type.id);
        meta.getPersistentDataContainer().set(new NamespacedKey("evergarden", "crop_food"), PersistentDataType.STRING, type.id);

        var food = meta.getFood();
        food.setCanAlwaysEat(true);
        meta.setFood(food);

        item.setItemMeta(meta);

        try {
            item.setData(io.papermc.paper.datacomponent.DataComponentTypes.FOOD,
                    io.papermc.paper.datacomponent.item.FoodProperties.food()
                            .canAlwaysEat(true)
                            .nutrition(2)
                            .saturation(1.0f)
                            .build());
            item.setData(io.papermc.paper.datacomponent.DataComponentTypes.CONSUMABLE,
                    io.papermc.paper.datacomponent.item.Consumable.consumable()
                            .consumeSeconds(1.0f)
                            .hasConsumeParticles(true)
                            .build());
        } catch (Throwable ignored) {}

        return item;
    }

    public ItemStack createPlantDisplay(CropType type, int stage) {
        // The Bedrock mapping registers crop stages against carved pumpkin, so
        // keep the carrier aligned with the generated Geyser mapping.
        ItemStack item = new ItemStack(Material.CARVED_PUMPKIN, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.displayName(Component.text(type.thaiName + " [Stage " + stage + "]").decoration(TextDecoration.ITALIC, false));
        // Explicitly set HEAD slot and clear the vanilla carved pumpkin model
        // so Java uses the custom plant model from the resource pack.
        var equipment = meta.getEquippable();
        equipment.setSlot(org.bukkit.inventory.EquipmentSlot.HEAD);
        equipment.setModel(null);
        meta.setEquippable(equipment);
        var modelData = meta.getCustomModelDataComponent();
        modelData.setStrings(List.of("voidscape:crop_" + type.id + "_stage_" + stage));
        meta.setCustomModelDataComponent(modelData);
        meta.setItemModel(new NamespacedKey("voidscape", "crop_" + type.id + "_stage_" + stage));

        item.setItemMeta(meta);
        return item;
    }

    public boolean isSeed(ItemStack item) {
        return getSeedType(item) != null;
    }

    public CropType getSeedType(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        var pdc = item.getItemMeta().getPersistentDataContainer();
        String id = pdc.get(seedKey, PersistentDataType.STRING);
        if (id == null) id = pdc.get(new NamespacedKey("evergarden", "crop_seed"), PersistentDataType.STRING);
        if (id != null) return CropType.fromId(id);

        // Fallback check CustomModelData string
        var cmd = item.getItemMeta().getCustomModelDataComponent();
        if (cmd.getStrings() != null && !cmd.getStrings().isEmpty()) {
            String s = cmd.getStrings().get(0);
            if (s.startsWith("voidscape:seed_")) {
                return CropType.fromId(s.substring("voidscape:seed_".length()));
            }
        }
        return null;
    }

    public boolean isFood(ItemStack item) {
        return getFoodType(item) != null;
    }

    public CropType getFoodType(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        var pdc = item.getItemMeta().getPersistentDataContainer();
        String id = pdc.get(foodKey, PersistentDataType.STRING);
        if (id == null) id = pdc.get(new NamespacedKey("evergarden", "crop_food"), PersistentDataType.STRING);
        if (id != null) return CropType.fromId(id);

        // Fallback check CustomModelData string
        var cmd = item.getItemMeta().getCustomModelDataComponent();
        if (cmd.getStrings() != null && !cmd.getStrings().isEmpty()) {
            String s = cmd.getStrings().get(0);
            if (s.startsWith("voidscape:crop_")) {
                String sub = s.substring("voidscape:crop_".length());
                if (!sub.contains("_stage_")) {
                    return CropType.fromId(sub);
                }
            }
        }
        return null;
    }
}
