package com.example.voidscape.item;

import com.example.voidscape.VoidscapePlugin;
import com.example.voidscape.enchant.EnchantApplyListener;
import com.example.voidscape.enchant.LimitBreakType;
import com.example.voidscape.enchant.UniqueEnchant;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.*;
import org.bukkit.util.Vector;

import java.util.*;

public final class RelicService implements Listener {
    public enum Relic {
        VOID_KEY(Material.TRIAL_KEY,"กุญแจ Evergarden","ใช้สำหรับเปิด Evergarden Vault ในวิหารโบราณ"),
        RIFT_PICKAXE(Material.NETHERITE_PICKAXE,"อีเต้อแยกพิภพ","ขุด 3×3 บล็อกพร้อมกัน · ย่อตัวเพื่อขุดทีละก้อน"),
        SMELTER_PICKAXE(Material.NETHERITE_PICKAXE,"อีเต้อหลอมเพลิงมิติ","หลอมบล็อกที่ขุดอัตโนมัติ (ทราย->กระจก, แร่->แท่งโลหะ)"),
        STORM_BOW(Material.BOW,"ธนูพิพากษาสายฟ้า","ยิงธนูผ่าสายฟ้าต่อเนื่องใส่ศัตรู"),
        NOVA_BOW(Material.BOW,"ธนูสะเก็ดดาว","ชาร์จเต็ม: ระเบิดพลังงาน · ไม่ทำลายบล็อก"),
        RIFT_BLADE(Material.NETHERITE_SWORD,"ดาบกรีดมิติ","คลิกขวา: วาร์ปไปข้างหน้า · ต้องมีทางโล่ง"),
        ETERNAL_AEGIS(Material.SHIELD,"โล่แห่งความอมตะ","คลิกขวา/ย่อ 2 ครั้ง/สลับมือ: อมตะ 3 วินาที · โจมตีไม่ได้ขณะใช้งาน"),
        SCROLL_ETERNITY(Material.PAPER,"คัมภีร์ศิลานิรันดร์","ลากทับไอเทมเพื่อทำให้อุปกรณ์ 'ไม่มีวันพังถาวร (Unbreakable)'"),
        SCROLL_LIMIT_BREAK(Material.PAPER,"คัมภีร์ทลายขีดจำกัด","ลากทับไอเทมเพื่อเพิ่มเลเวลเอนแชนต์เดิม +1"),
        SCROLL_UNIQUE(Material.PAPER,"คัมภีร์มนตราโบราณ","ลากทับไอเทมเพื่อสลักเวทมนตร์เฉพาะตัว"),
        ASTRAL_DUST(Material.SUGAR,"ผงละอองดาว","ละอองดาวดึกดำบรรพ์ สสารเวทมนตร์แห่ง Evergarden"),
        KEY_SHARD(Material.PRISMARINE_SHARD,"เศษกุญแจมิติ","รวบรวมครบ 4 ชิ้นคราฟต์เป็น Evergarden Key ได้ที่โต๊ะคราฟต์"),
        REPAIR_STONE(Material.FLINT,"ศิลาฟื้นฟูมิติ","คลิกขวาเพื่อซ่อมแซมความทนทานของอุปกรณ์ 500 หน่วย"),
        VOID_ELIXIR(Material.HONEY_BOTTLE,"น้ำยาเดินเวหา","ดื่มเพื่อรับ Speed II, Jump Boost II และ Slow Falling 3 นาที");

        public final Material material; public final String title,lore;
        Relic(Material m,String t,String l){material=m;title=t;lore=l;}
        public String id(){return name().toLowerCase(Locale.ROOT);}
    }

    private final VoidscapePlugin plugin;
    private final NamespacedKey type,shot,shotOwner,shieldUntil,voidKeyTag;
    private final Set<UUID> mining=new HashSet<>();
    private final Map<UUID,Long> arrows=new HashMap<>();
    private final Map<UUID,Long> lastAegisSneak=new HashMap<>();

    public record MagicCore(String id, String title, String wandTitle) {}
    public static final List<MagicCore> MAGIC_CORES = List.of(
        new MagicCore("lightning_strike", "Core of Lightning", "Lightning Strike"),
        new MagicCore("frost_nova", "Core of Frost", "Frost Nova"),
        new MagicCore("shadow_step", "Core of Shadows", "Shadow Step"),
        new MagicCore("natures_bloom", "Core of Nature", "Nature's Bloom"),
        new MagicCore("earth_wall", "Core of Earth", "Earth Wall"),
        new MagicCore("dragons_breath", "Core of Dragon", "Dragon's Breath"),
        new MagicCore("void_pull", "Core of the Void", "Void Pull"),
        new MagicCore("sonic_boom", "Core of the Warden", "Sonic Boom"),
        new MagicCore("blaze_barrage", "Core of the Blaze", "Blaze Barrage"),
        new MagicCore("wither_ray", "Core of Wither", "Wither Ray"),
        new MagicCore("shulker_levitation", "Core of Levitation", "Shulker Levitation"),
        new MagicCore("meteor_strike", "Core of Meteor", "Meteor Strike"),
        new MagicCore("iron_armor", "Core of Iron", "Iron Armor"),
        new MagicCore("vex_legion", "Core of Evocation", "Vex Legion"),
        new MagicCore("guardian_beam", "Core of the Guardian", "Guardian Beam")
    );

    public RelicService(VoidscapePlugin plugin) {
        this.plugin=plugin; type=plugin.key("relic_v2");shot=plugin.key("shot");shotOwner=plugin.key("shot_owner");shieldUntil=plugin.key("shield_until");
        voidKeyTag=plugin.key("void_key");
        registerKeyRecipe();
    }

    public void registerKeyRecipe() {
        // 1. Evergarden Key (4 Key Shards 2x2)
        NamespacedKey key = plugin.key("craft_void_key");
        try { Bukkit.removeRecipe(key); } catch (Exception ignored) {}
        ShapedRecipe recipe = new ShapedRecipe(key, createVoidKey());
        recipe.shape("SS", "SS");
        recipe.setIngredient('S', RecipeChoice.predicateChoice(this::isKeyShard,createKeyShard(1)));
        try { Bukkit.addRecipe(recipe); } catch (Exception ignored) {}

        // 1b. Evergarden Key Shapeless (any 4 Prismarine Shards)
        NamespacedKey keyShapeless = plugin.key("craft_void_key_shapeless");
        try { Bukkit.removeRecipe(keyShapeless); } catch (Exception ignored) {}
        ShapelessRecipe recipeShapeless = new ShapelessRecipe(keyShapeless, createVoidKey());
        for(int i=0;i<4;i++)recipeShapeless.addIngredient(RecipeChoice.predicateChoice(this::isKeyShard,createKeyShard(1)));
        try { Bukkit.addRecipe(recipeShapeless); } catch (Exception ignored) {}

        // 2. Vault Repair Stone (4 Astral Dust + 1 Amethyst Shard)
        NamespacedKey repairKey = plugin.key("craft_repair_stone");
        try { Bukkit.removeRecipe(repairKey); } catch (Exception ignored) {}
        ShapedRecipe repairRecipe = new ShapedRecipe(repairKey, createRepairStone(1));
        repairRecipe.shape(" D ", "DAD", " D ");
        repairRecipe.setIngredient('D', RecipeChoice.predicateChoice(this::isAstralDust,createAstralDust(1)));
        repairRecipe.setIngredient('A', Material.AMETHYST_SHARD);
        try { Bukkit.addRecipe(repairRecipe); } catch (Exception ignored) {}

        // 3. Void Walker Elixir (1 Astral Dust + 1 Glass Bottle)
        NamespacedKey elixirKey = plugin.key("craft_void_elixir");
        try { Bukkit.removeRecipe(elixirKey); } catch (Exception ignored) {}
        ShapelessRecipe elixirRecipe = new ShapelessRecipe(elixirKey, createVoidElixir(1));
        elixirRecipe.addIngredient(RecipeChoice.predicateChoice(this::isAstralDust,createAstralDust(1)));
        elixirRecipe.addIngredient(Material.GLASS_BOTTLE);
        try { Bukkit.addRecipe(elixirRecipe); } catch (Exception ignored) {}
    }

    public ItemStack createMagicCore(MagicCore core) {
        ItemStack item=new ItemStack(Material.HEART_OF_THE_SEA);
        ItemMeta meta=item.getItemMeta();
        if(core.id().equals("shulker_levitation")) {
            meta.setDisplayName(ChatColor.LIGHT_PURPLE+"✦ "+ChatColor.GOLD+"Core of Levitation "+ChatColor.RED+"[MYTHIC]");
            meta.setLore(List.of(
                ChatColor.GOLD+"[ระดับตำนานสูงสุด · MYTHIC 0.5%]",
                ChatColor.DARK_PURPLE+"§k||§r "+ChatColor.LIGHT_PURPLE+"Forbidden Dragon Heart "+ChatColor.DARK_PURPLE+"§k||",
                ChatColor.GRAY+"ใช้คราฟต์: "+ChatColor.LIGHT_PURPLE+"Shulker Levitation Wand",
                ChatColor.YELLOW+"สูตร: 8 Netherite Ingots หรือ Nether Stars + แกนนี้",
                ChatColor.RED+"✦ อัตราดรอป 0.5% ใน Evergarden Vault [สุดยอดของแรร์]"
            ));
        } else {
            meta.setDisplayName(ChatColor.GOLD+"✦ "+core.title());
            meta.setLore(List.of(
                ChatColor.AQUA+"Ancient Magic Core (แกนเวทมนตร์โบราณ)",
                ChatColor.GRAY+"ใช้คราฟต์: "+ChatColor.LIGHT_PURPLE+core.wandTitle()+" Wand",
                ChatColor.YELLOW+"สูตร: 8 Netherite Ingots หรือ Nether Stars + แกนนี้",
                ChatColor.DARK_AQUA+"หาได้จาก: Evergarden Vault"
            ));
        }
        var modelData=meta.getCustomModelDataComponent();
        modelData.setStrings(List.of("advance_magic:core_"+core.id()));
        meta.setCustomModelDataComponent(modelData);
        meta.setItemModel(new NamespacedKey("advance_magic","core_"+core.id()));
        meta.getPersistentDataContainer().set(new NamespacedKey("advance_magic","core"),PersistentDataType.STRING,core.id());
        meta.getPersistentDataContainer().set(new NamespacedKey("advance-magic","core"),PersistentDataType.STRING,core.id());
        meta.getPersistentDataContainer().set(new NamespacedKey("voidscape","magic_core"),PersistentDataType.STRING,core.id());
        meta.getPersistentDataContainer().set(new NamespacedKey("evergarden","magic_core"),PersistentDataType.STRING,core.id());
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createMagicCore(String id) {
        if(id != null) {
            String lower = id.toLowerCase(Locale.ROOT);
            if(lower.contains("invisibility") || lower.equals("shroud")) id = "sonic_boom";
            else if(lower.contains("poison") || lower.contains("spores")) id = "blaze_barrage";
            else if(lower.contains("soul") || lower.equals("souls") || lower.contains("drain")) id = "guardian_beam";
            else if(lower.contains("time") || lower.contains("dilation")) id = "vex_legion";
        }
        for(MagicCore c : MAGIC_CORES) {
            if(c.id().equalsIgnoreCase(id)||c.id().replace("_","").equalsIgnoreCase(id.replace("_",""))) return createMagicCore(c);
        }
        return createMagicCore(MAGIC_CORES.get(0));
    }

    public ItemStack create(Relic relic,int count) {
        ItemStack item=new ItemStack(relic.material,Math.min(relic.material.getMaxStackSize(),Math.max(1,count)));
        ItemMeta meta=item.getItemMeta();
        meta.displayName(Component.text(relic.title,relic==Relic.VOID_KEY?NamedTextColor.LIGHT_PURPLE:NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
            Component.text(relic.lore,NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
            Component.text(relic==Relic.VOID_KEY?"EVERGARDEN · TRIAL KEY":"EVERGARDEN · RELIC",NamedTextColor.DARK_PURPLE).decoration(TextDecoration.ITALIC, false)
        ));
        // The Aeternum food pack also defines honey_bottle. Give our elixir its
        // own item definition so pack order cannot turn it into a vanilla drink.
        meta.setItemModel(relic == Relic.VOID_ELIXIR ? new NamespacedKey("voidscape", relic.id()) : null);
        var selector=meta.getCustomModelDataComponent();selector.setStrings(List.of("voidscape:"+relic.id()));meta.setCustomModelDataComponent(selector);
        meta.getPersistentDataContainer().set(type,PersistentDataType.STRING,relic.name());

        if(relic==Relic.VOID_KEY) {
            meta.getPersistentDataContainer().set(voidKeyTag,PersistentDataType.BYTE,(byte)1);
        } else if(relic.material==Material.NETHERITE_PICKAXE||relic.material==Material.NETHERITE_SWORD||relic.material==Material.BOW||relic.material==Material.SHIELD) {
            meta.addEnchant(Enchantment.UNBREAKING,3,true);
            if(relic==Relic.RIFT_PICKAXE||relic==Relic.SMELTER_PICKAXE) {meta.addEnchant(Enchantment.EFFICIENCY,5,true);meta.addEnchant(Enchantment.FORTUNE,3,true);}
            if(relic==Relic.RIFT_BLADE) applyLimitBreakMeta(meta, LimitBreakType.SHARPNESS, 8);
            if(relic==Relic.NOVA_BOW||relic==Relic.STORM_BOW) applyLimitBreakMeta(meta, LimitBreakType.POWER, 6);
        }
        item.setItemMeta(meta); return item;
    }

    public ItemStack createScrollEternity() {
        ItemStack item=create(Relic.SCROLL_ETERNITY,1);
        ItemMeta meta=item.getItemMeta();
        meta.displayName(Component.text("✦ คัมภีร์ศิลานิรันดร์ (Scroll of Eternity)",NamedTextColor.GOLD).decoration(TextDecoration.ITALIC,false));
        meta.lore(List.of(
            Component.text("§4§k||§r §c[ระดับตำนานสูงสุด · MYTHIC 0.5%] §4§k||§r"),
            Component.text("ลากคัมภีร์นี้ไปแตะที่อาวุธ ชุดเกราะ หรือเครื่องมือ",NamedTextColor.WHITE).decoration(TextDecoration.ITALIC,false),
            Component.text("ไอเทมนั้นจะได้รับสถานะ 'ไม่มีวันพังเสียหาย (Unbreakable 100%)'",NamedTextColor.GOLD).decoration(TextDecoration.ITALIC,false),
            Component.text("หลอดความทนทานจะหายไปตลอดกาล",NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC,false),
            Component.text("วิธีใช้: พิมพ์ /upgrade หรือลากแตะทับไอเทมในกระเป๋า",NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC,false)
        ));
        meta.getPersistentDataContainer().set(plugin.key("scroll_eternity"),PersistentDataType.BYTE,(byte)1);
        item.setItemMeta(meta);return item;
    }

    public ItemStack createScrollLimitBreak(LimitBreakType lb) {
        ItemStack item=create(Relic.SCROLL_LIMIT_BREAK,1);
        ItemMeta meta=item.getItemMeta();
        meta.displayName(Component.text("✦ คัมภีร์ทลายขีดจำกัด: "+lb.title(),NamedTextColor.GOLD).decoration(TextDecoration.ITALIC,false));
        meta.lore(List.of(
            Component.text("✦ "+lb.thaiTitle(),NamedTextColor.AQUA).decoration(TextDecoration.ITALIC,false),
            Component.text("ใช้สำหรับ: "+lb.targetDescription(),NamedTextColor.GRAY).decoration(TextDecoration.ITALIC,false),
            Component.text("วิธีใช้: พิมพ์ /upgrade หรือลากแตะทับไอเทมในกระเป๋า",NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC,false),
            Component.text("เพื่อเพิ่มเลเวลเอนแชนต์เดิมขึ้น +1 (สูงสุดเลเวล "+lb.maxLevel()+")",NamedTextColor.GRAY).decoration(TextDecoration.ITALIC,false),
            Component.text("[อัตราสำเร็จ 100% · แตะเพื่อใช้งาน]",NamedTextColor.GREEN).decoration(TextDecoration.ITALIC,false)
        ));
        meta.getPersistentDataContainer().set(plugin.key("limit_break_type"),PersistentDataType.STRING,lb.name());
        item.setItemMeta(meta);return item;
    }

    public ItemStack createScrollUnique(UniqueEnchant ue) {
        ItemStack item=create(Relic.SCROLL_UNIQUE,1);
        ItemMeta meta=item.getItemMeta();
        meta.displayName(Component.text("✦ มนตราโบราณ: "+ue.title(),NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.ITALIC,false));
        meta.lore(List.of(
            Component.text("✦ "+ue.thaiTitle(),NamedTextColor.GOLD).decoration(TextDecoration.ITALIC,false),
            Component.text("ความสามารถ: "+ue.description(),NamedTextColor.GRAY).decoration(TextDecoration.ITALIC,false),
            Component.text("ประเภทอุปกรณ์: "+ue.category().name(),NamedTextColor.DARK_AQUA).decoration(TextDecoration.ITALIC,false),
            Component.text("วิธีใช้: พิมพ์ /upgrade หรือลากแตะทับอุปกรณ์ในกระเป๋า",NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC,false),
            Component.text("[อัตราสำเร็จ 100% · ลากแตะเพื่อใช้งาน]",NamedTextColor.GREEN).decoration(TextDecoration.ITALIC,false)
        ));
        meta.getPersistentDataContainer().set(plugin.key("unique_enchant"),PersistentDataType.STRING,ue.name());
        item.setItemMeta(meta);return item;
    }

    public ItemStack createKeyShard(int count) {
        ItemStack item=create(Relic.KEY_SHARD,count);
        ItemMeta meta=item.getItemMeta();
        meta.displayName(Component.text("✦ เศษกุญแจมิติ (Evergarden Key Shard)",NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.ITALIC,false));
        meta.lore(List.of(
            Component.text("เศษผลึกโบราณจาก Evergarden Vault",NamedTextColor.GRAY).decoration(TextDecoration.ITALIC,false),
            Component.text("• วาง 4 ชิ้น (2×2) ที่โต๊ะคราฟต์เพื่อประกอบเป็น Evergarden Key",NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC,false),
            Component.text("• ใช้เปิดกล่องสมบัติ Evergarden Vault ประจำวิหาร",NamedTextColor.AQUA).decoration(TextDecoration.ITALIC,false)
        ));
        meta.getPersistentDataContainer().set(plugin.key("key_shard"),PersistentDataType.BYTE,(byte)1);
        item.setItemMeta(meta);return item;
    }

    public ItemStack createAstralDust(int count) {
        ItemStack item=create(Relic.ASTRAL_DUST,count);
        ItemMeta meta=item.getItemMeta();
        meta.displayName(Component.text("✦ ผงละอองดาว (Astral Dust)",NamedTextColor.AQUA).decoration(TextDecoration.ITALIC,false));
        meta.lore(List.of(
            Component.text("ละอองดวงดาวโบราณ สสารเวทมนตร์แห่ง Evergarden",NamedTextColor.GRAY).decoration(TextDecoration.ITALIC,false),
            Component.text("• คลิกขวาใส่พืช Evergarden = เร่งเวลาเติบโต +5%",NamedTextColor.AQUA).decoration(TextDecoration.ITALIC,false),
            Component.text("• คราฟต์คู่ขวดแก้ว = น้ำยาเดินเวหา (Void Elixir)",NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC,false),
            Component.text("• ใช้ 4 ชิ้น + 1 Amethyst = ศิลาฟื้นฟู (Repair Stone)",NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.ITALIC,false)
        ));
        meta.getPersistentDataContainer().set(plugin.key("astral_dust"),PersistentDataType.BYTE,(byte)1);
        item.setItemMeta(meta);return item;
    }

    public ItemStack createRepairStone(int count) {
        ItemStack item=create(Relic.REPAIR_STONE,count);
        ItemMeta meta=item.getItemMeta();
        meta.displayName(Component.text("✦ ศิลาฟื้นฟูมิติ (Vault Repair Stone)",NamedTextColor.GREEN).decoration(TextDecoration.ITALIC,false));
        meta.lore(List.of(
            Component.text("ศิลาจารึกอักขระฟื้นฟูโบราณ",NamedTextColor.GRAY).decoration(TextDecoration.ITALIC,false),
            Component.text("คลิกขวาเพื่อซ่อมแซมความทนทาน 500 หน่วย",NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC,false),
            Component.text("(ซ่อมให้กับอุปกรณ์ที่ชำรุดมากที่สุดในตัวคุณ)",NamedTextColor.GRAY).decoration(TextDecoration.ITALIC,false),
            Component.text("สูตรคราฟต์: 4 Astral Dust + 1 Amethyst Shard",NamedTextColor.AQUA).decoration(TextDecoration.ITALIC,false)
        ));
        meta.getPersistentDataContainer().set(plugin.key("repair_stone"),PersistentDataType.BYTE,(byte)1);
        item.setItemMeta(meta);return item;
    }

    public ItemStack createVoidElixir(int count) {
        ItemStack item=create(Relic.VOID_ELIXIR,count);
        ItemMeta meta=item.getItemMeta();
        meta.displayName(Component.text("✦ น้ำยาเดินเวหา (Void Walker Elixir)",NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.ITALIC,false));
        meta.lore(List.of(
            Component.text("น้ำยาเรืองแสงผสมละอองดาวบริสุทธิ์",NamedTextColor.GRAY).decoration(TextDecoration.ITALIC,false),
            Component.text("ดื่มเพื่อรับผลลัพธ์เป็นเวลา 3 นาที:",NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC,false),
            Component.text("• ความเร็ว Speed II",NamedTextColor.AQUA).decoration(TextDecoration.ITALIC,false),
            Component.text("• กระโดดสูง Jump Boost II",NamedTextColor.GREEN).decoration(TextDecoration.ITALIC,false),
            Component.text("• ตกช้า Slow Falling (ป้องกันตกหลุม Void)",NamedTextColor.WHITE).decoration(TextDecoration.ITALIC,false),
            Component.text("สูตรคราฟต์: 1 Astral Dust + 1 ขวดแก้ว",NamedTextColor.GOLD).decoration(TextDecoration.ITALIC,false)
        ));
        meta.getPersistentDataContainer().set(plugin.key("void_elixir"),PersistentDataType.BYTE,(byte)1);
        item.setItemMeta(meta);return item;
    }

    public boolean isScrollEternity(ItemStack item) {
        if(item==null||!item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(plugin.key("scroll_eternity"),PersistentDataType.BYTE)
            || type(item)==Relic.SCROLL_ETERNITY;
    }

    public LimitBreakType getLimitBreakType(ItemStack item) {
        if(item==null||!item.hasItemMeta()) return null;
        String raw=item.getItemMeta().getPersistentDataContainer().get(plugin.key("limit_break_type"),PersistentDataType.STRING);
        if(raw==null) return null;
        try{return LimitBreakType.valueOf(raw);}catch(Exception e){return null;}
    }

    public UniqueEnchant getUniqueEnchant(ItemStack item) {
        if(item==null||!item.hasItemMeta()) return null;
        String raw=item.getItemMeta().getPersistentDataContainer().get(plugin.key("unique_enchant"),PersistentDataType.STRING);
        if(raw==null) return null;
        try{return UniqueEnchant.valueOf(raw);}catch(Exception e){return null;}
    }

    public boolean isKeyShard(ItemStack item) {
        if(item==null||item.getType()!=Material.PRISMARINE_SHARD||!item.hasItemMeta()) return false;
        var pdc = item.getItemMeta().getPersistentDataContainer();
        return pdc.has(plugin.key("key_shard"),PersistentDataType.BYTE)
            || type(item)==Relic.KEY_SHARD;
    }

    public boolean isRepairStone(ItemStack item) {
        if(item==null||item.getType()!=Material.FLINT||!item.hasItemMeta()) return false;
        var pdc = item.getItemMeta().getPersistentDataContainer();
        return pdc.has(plugin.key("repair_stone"),PersistentDataType.BYTE)
            || type(item)==Relic.REPAIR_STONE;
    }

    public boolean isVoidElixir(ItemStack item) {
        if(item==null||item.getType()!=Material.HONEY_BOTTLE||!item.hasItemMeta()) return false;
        var pdc = item.getItemMeta().getPersistentDataContainer();
        return pdc.has(plugin.key("void_elixir"),PersistentDataType.BYTE)
            || type(item)==Relic.VOID_ELIXIR;
    }

    public boolean isAstralDust(ItemStack item) {
        if(item==null||item.getType()!=Material.SUGAR||!item.hasItemMeta()) return false;
        var pdc = item.getItemMeta().getPersistentDataContainer();
        return pdc.has(plugin.key("astral_dust"),PersistentDataType.BYTE)
            || type(item)==Relic.ASTRAL_DUST;
    }

    public static String toRoman(int n) {
        return switch (n) {
            case 1 -> "I"; case 2 -> "II"; case 3 -> "III"; case 4 -> "IV"; case 5 -> "V";
            case 6 -> "VI"; case 7 -> "VII"; case 8 -> "VIII"; case 9 -> "IX"; case 10 -> "X";
            default -> String.valueOf(n);
        };
    }

    public boolean isEternityItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta.getPersistentDataContainer().has(plugin.key("relic_eternity"), PersistentDataType.BYTE)) return true;
        return false;
    }

    public int getLimitBreakLevel(ItemStack item, LimitBreakType type) {
        if (item == null || !item.hasItemMeta() || type == null) return 0;
        ItemMeta meta = item.getItemMeta();
        NamespacedKey key = plugin.key("lb_" + type.name().toLowerCase(Locale.ROOT));
        Integer custom = meta.getPersistentDataContainer().get(key, PersistentDataType.INTEGER);
        if (custom != null) return custom;
        return Math.min(meta.getEnchantLevel(type.enchantment()), type.enchantment().getMaxLevel());
    }

    /** Upgrade from the actual level, including native over-level loot from other plugins.
     * Ability listeners still use getLimitBreakLevel so they don't add our bonus to foreign enchants. */
    public int getLimitBreakUpgradeLevel(ItemStack item, LimitBreakType type) {
        if (item == null || !item.hasItemMeta() || type == null) return 0;
        return Math.max(item.getItemMeta().getEnchantLevel(type.enchantment()), getLimitBreakLevel(item, type));
    }

    public void applyEternityMeta(ItemMeta meta) {
        meta.getPersistentDataContainer().set(plugin.key("relic_eternity"), PersistentDataType.BYTE, (byte) 1);
        if (meta instanceof org.bukkit.inventory.meta.Damageable d) {
            d.setDamage(0);
        }
        // Do NOT setUnbreakable(true) so that Anti-Dupe / Anti-Illegal plugins won't flag it!
        meta.setUnbreakable(false);

        List<Component> lore = meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<>();
        lore.removeIf(line -> net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(line).contains("สถิตนิรันดร์"));
        lore.add(0, Component.text("✦ สถิตนิรันดร์: ไม่มีวันพังเสียหาย", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
    }

    public void applyLimitBreakMeta(Material mat, ItemMeta meta, LimitBreakType type, int next) {
        NamespacedKey key = plugin.key("lb_" + type.name().toLowerCase(Locale.ROOT));
        meta.getPersistentDataContainer().set(key, PersistentDataType.INTEGER, next);
        int vanillaCap = Math.min(next, type.enchantment().getMaxLevel());
        meta.addEnchant(type.enchantment(), vanillaCap, true);

        if (type == LimitBreakType.EFFICIENCY && next >= 6) {
            EnchantApplyListener.applyEfficiencyToolComponent(mat, meta, next);
        }

        List<Component> lore = meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<>();
        lore.removeIf(line -> net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(line).contains(type.thaiTitle()));
        lore.add(Component.text("✦ " + type.thaiTitle() + " ระดับ " + toRoman(next), NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
    }

    public void applyLimitBreakMeta(ItemMeta meta, LimitBreakType type, int next) {
        applyLimitBreakMeta(null, meta, type, next);
    }

    public ItemStack evaluateScrollCraft(ItemStack scroll, ItemStack target) {
        if (scroll == null || target == null || target.getType().isAir()) return null;

        // 1. Scroll of Eternity
        if (isScrollEternity(scroll)) {
            if (target.getType().getMaxDurability() <= 0) return null;
            if (isEternityItem(target)) return null;
            ItemMeta meta = target.getItemMeta();
            if (meta == null) return null;
            applyEternityMeta(meta);
            ItemStack result = target.clone();
            result.setItemMeta(meta);
            return result;
        }

        // 2. Limit Break Scroll
        LimitBreakType type = getLimitBreakType(scroll);
        if (type != null) {
            if (!type.category().matches(target.getType())) return null;
            int current = getLimitBreakUpgradeLevel(target, type);
            if (current <= 0 || current >= type.maxLevel()) return null;
            int next = current + 1;
            ItemMeta meta = target.getItemMeta();
            if (meta == null) return null;
            applyLimitBreakMeta(target.getType(), meta, type, next);
            ItemStack result = target.clone();
            result.setItemMeta(meta);
            return result;
        }

        // 3. Unique Enchant Scroll
        UniqueEnchant enchant = getUniqueEnchant(scroll);
        if (enchant != null) {
            if (!enchant.category().matches(target.getType())) return null;
            ItemMeta meta = target.getItemMeta();
            if (meta == null) return null;
            NamespacedKey key = new NamespacedKey("evergarden", "ue_" + enchant.id().toLowerCase(Locale.ROOT));
            if (meta.getPersistentDataContainer().has(key)) return null;
            meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
            if (enchant == UniqueEnchant.ADVANCE_TOOL) {
                EnchantApplyListener.applyAdvanceToolComponent(meta);
            }
            List<Component> lore = meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<>();
            lore.add(Component.text("✦ " + enchant.title() + " · " + enchant.thaiTitle(), NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("   §7" + enchant.description()).decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
            ItemStack result = target.clone();
            result.setItemMeta(meta);
            return result;
        }

        return null;
    }

    public boolean isEvergardenItem(ItemMeta meta) {
        if (meta == null) return false;
        var pdc = meta.getPersistentDataContainer();
        if (pdc.has(new NamespacedKey("voidscape", "crop_seed"), PersistentDataType.STRING)
            || pdc.has(new NamespacedKey("evergarden", "crop_seed"), PersistentDataType.STRING)
            || pdc.has(new NamespacedKey("voidscape", "crop_food"), PersistentDataType.STRING)
            || pdc.has(new NamespacedKey("evergarden", "crop_food"), PersistentDataType.STRING)
            || pdc.has(type, PersistentDataType.STRING)
            || pdc.has(plugin.key("relic"), PersistentDataType.STRING)
            || pdc.has(new NamespacedKey("evergarden", "relic_v2"), PersistentDataType.STRING)
            || pdc.has(new NamespacedKey("evergarden", "relic"), PersistentDataType.STRING)
            || pdc.has(plugin.key("limit_break_type"), PersistentDataType.STRING)
            || pdc.has(plugin.key("unique_enchant"), PersistentDataType.STRING)
            || pdc.has(plugin.key("relic_eternity"), PersistentDataType.BYTE)
            || pdc.has(voidKeyTag, PersistentDataType.BYTE)
            || pdc.has(new NamespacedKey("evergarden", "void_key"), PersistentDataType.BYTE)
            || pdc.has(plugin.key("key_shard"), PersistentDataType.BYTE)
            || pdc.has(new NamespacedKey("evergarden", "key_shard"), PersistentDataType.BYTE)
            || pdc.has(plugin.key("repair_stone"), PersistentDataType.BYTE)
            || pdc.has(new NamespacedKey("evergarden", "repair_stone"), PersistentDataType.BYTE)
            || pdc.has(plugin.key("void_elixir"), PersistentDataType.BYTE)
            || pdc.has(new NamespacedKey("evergarden", "void_elixir"), PersistentDataType.BYTE)
            || pdc.has(plugin.key("astral_dust"), PersistentDataType.BYTE)
            || pdc.has(new NamespacedKey("evergarden", "astral_dust"), PersistentDataType.BYTE)) {
            return true;
        }
        if (meta.hasLore()) {
            for (Component line : meta.lore()) {
                String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(line);
                if (plain.contains("EVERGARDEN ·") || plain.contains("VOIDSCAPE ·")) return true;
            }
        }
        return false;
    }

    public boolean isAeternumItem(ItemMeta meta) {
        if (meta == null) return false;
        if (isEvergardenItem(meta)) return false;
        var pdc = meta.getPersistentDataContainer();
        for (NamespacedKey k : pdc.getKeys()) {
            String ns = k.getNamespace().toLowerCase(Locale.ROOT);
            String key = k.getKey().toLowerCase(Locale.ROOT);
            if (ns.contains("aeternum") || key.contains("aeternum")) {
                return true;
            }
        }
        if (meta.hasDisplayName()) {
            String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(meta.displayName());
            if (plain.toLowerCase(Locale.ROOT).contains("aeternum")) return true;
        }
        if (meta.hasLore()) {
            for (Component line : meta.lore()) {
                String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(line);
                if (plain.toLowerCase(Locale.ROOT).contains("aeternum")) return true;
            }
        }
        return false;
    }

    public boolean cleanseAeternumCorruption(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        if (!isAeternumItem(meta)) return false;

        boolean changed = false;
        var pdc = meta.getPersistentDataContainer();

        // 1. Remove corrupted Evergarden / Voidscape Relic & Limit Break tags
        List<NamespacedKey> toRemove = new ArrayList<>();
        for (NamespacedKey k : pdc.getKeys()) {
            String ns = k.getNamespace().toLowerCase(Locale.ROOT);
            String key = k.getKey().toLowerCase(Locale.ROOT);
            if (ns.equals("voidscape") || ns.equals("evergarden")) {
                if (key.startsWith("relic") || key.equals("type") || key.equals("limit_break_type")
                    || key.equals("unique_enchant") || key.equals("scroll_eternity")
                    || key.equals("void_key") || key.equals("key_shard") || key.equals("repair_stone")
                    || key.equals("void_elixir") || key.equals("astral_dust") || key.startsWith("lb_") || key.startsWith("ue_")) {
                    toRemove.add(k);
                }
            }
        }
        for (NamespacedKey k : toRemove) {
            pdc.remove(k);
            changed = true;
        }

        // 2. Remove corrupted CustomModelData strings from Evergarden
        var cmdComp = meta.getCustomModelDataComponent();
        if (cmdComp != null && !cmdComp.getStrings().isEmpty()) {
            List<String> validStrings = new ArrayList<>();
            for (String str : cmdComp.getStrings()) {
                String lower = str.toLowerCase(Locale.ROOT);
                if (lower.startsWith("voidscape:") || lower.startsWith("evergarden:")) {
                    changed = true;
                } else {
                    validStrings.add(str);
                }
            }
            if (changed) {
                cmdComp.setStrings(validStrings);
                meta.setCustomModelDataComponent(cmdComp);
            }
        }

        // 3. Remove corrupted ItemModel
        if (meta.getItemModel() != null) {
            String ns = meta.getItemModel().getNamespace().toLowerCase(Locale.ROOT);
            if (ns.equals("voidscape") || ns.equals("evergarden")) {
                meta.setItemModel(null);
                changed = true;
            }
        }

        // 4. Clean up corrupted Evergarden lore lines
        if (meta.hasLore()) {
            List<Component> lore = new ArrayList<>(meta.lore());
            boolean loreChanged = lore.removeIf(line -> {
                String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(line);
                return plain.contains("EVERGARDEN · RELIC") || plain.contains("EVERGARDEN · TRIAL KEY");
            });
            if (loreChanged) {
                meta.lore(lore.isEmpty() ? null : lore);
                changed = true;
            }
        }

        if (changed) {
            item.setItemMeta(meta);
        }
        return changed;
    }

    public boolean migrate(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        if (cleanseAeternumCorruption(item)) return true;
        if (isAeternumItem(item.getItemMeta())) return false;
        if (!isManagedItem(item)) return false;
        boolean changed = false;
        if (EnchantApplyListener.hasUnique(item, UniqueEnchant.ADVANCE_TOOL)) {
            var meta = item.getItemMeta();
            if (meta != null && (!meta.hasTool() || meta.getTool().getRules().isEmpty())) {
                EnchantApplyListener.applyAdvanceToolComponent(meta);
                item.setItemMeta(meta);
                changed = true;
            }
        }
        int lbEff = getLimitBreakLevel(item, LimitBreakType.EFFICIENCY);
        if (lbEff >= 6) {
            var meta = item.getItemMeta();
            if (meta != null && (!meta.hasTool() || meta.getTool().getRules().isEmpty() || meta.getTool().getDefaultMiningSpeed() != 1.0f)) {
                EnchantApplyListener.applyEfficiencyToolComponent(item.getType(), meta, lbEff);
                item.setItemMeta(meta);
                changed = true;
            }
        }
        // A scroll only grants its own ability. It does not transfer ownership
        // of unrelated tool components or the native unbreakable flag to us.
        Relic relic=type(item);if(relic==null)return changed;
        var meta=item.getItemMeta();var data=meta.getCustomModelDataComponent();String model="voidscape:"+relic.id();
        NamespacedKey itemModel=relic==Relic.VOID_ELIXIR?new NamespacedKey("voidscape",relic.id()):null;
        if(Objects.equals(meta.getItemModel(),itemModel)&&data.getStrings().equals(List.of(model)))return changed;
        meta.setItemModel(itemModel);data.setStrings(List.of(model));meta.setCustomModelDataComponent(data);item.setItemMeta(meta);return true;
    }

    private boolean isManagedItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        if (isAeternumItem(meta)) return false;
        var pdc = meta.getPersistentDataContainer();
        for (LimitBreakType enchant : LimitBreakType.values()) {
            if (pdc.has(plugin.key("lb_" + enchant.name().toLowerCase(Locale.ROOT)), PersistentDataType.INTEGER)) return true;
        }
        for (UniqueEnchant enchant : UniqueEnchant.values()) {
            if (EnchantApplyListener.hasUnique(item, enchant)) return true;
        }
        return pdc.has(type, PersistentDataType.STRING)
            || pdc.has(plugin.key("relic"), PersistentDataType.STRING)
            || pdc.has(new NamespacedKey("evergarden", "relic_v2"), PersistentDataType.STRING)
            || pdc.has(new NamespacedKey("evergarden", "relic"), PersistentDataType.STRING)
            || pdc.has(plugin.key("limit_break_type"), PersistentDataType.STRING)
            || pdc.has(plugin.key("unique_enchant"), PersistentDataType.STRING)
            || pdc.has(plugin.key("relic_eternity"), PersistentDataType.BYTE)
            || pdc.has(voidKeyTag, PersistentDataType.BYTE)
            || pdc.has(plugin.key("key_shard"), PersistentDataType.BYTE)
            || pdc.has(plugin.key("repair_stone"), PersistentDataType.BYTE)
            || pdc.has(plugin.key("void_elixir"), PersistentDataType.BYTE)
            || pdc.has(plugin.key("astral_dust"), PersistentDataType.BYTE)
            || type(item) != null;
    }

    public void migrate(Inventory inventory){for(int i=0;i<inventory.getSize();i++){var item=inventory.getItem(i);if(migrate(item))inventory.setItem(i,item);}}
    @EventHandler public void join(PlayerJoinEvent e){
        Player p=e.getPlayer();
        migrate(p.getInventory());
        migrate(p.getEnderChest());
        p.discoverRecipes(List.of(
            plugin.key("craft_void_key"),
            plugin.key("craft_void_key_shapeless"),
            plugin.key("craft_repair_stone"),
            plugin.key("craft_void_elixir")
        ));
    }
    @EventHandler public void open(org.bukkit.event.inventory.InventoryOpenEvent e){migrate(e.getInventory());migrate(e.getPlayer().getInventory());}
    @EventHandler public void pickup(EntityPickupItemEvent e){var item=e.getItem().getItemStack();if(migrate(item))e.getItem().setItemStack(item);}
    @EventHandler public void drop(ItemSpawnEvent e){var item=e.getEntity().getItemStack();if(migrate(item))e.getEntity().setItemStack(item);}
    public void migrateEntity(Entity entity) {
        if(entity instanceof org.bukkit.entity.Item dropped){var item=dropped.getItemStack();if(migrate(item))dropped.setItemStack(item);}
        if(entity instanceof ItemFrame frame){var item=frame.getItem();if(migrate(item))frame.setItem(item);}
    }
    @EventHandler public void load(org.bukkit.event.world.EntitiesLoadEvent e){e.getEntities().forEach(this::migrateEntity);}
    @EventHandler public void click(org.bukkit.event.inventory.InventoryClickEvent e){if(migrate(e.getCurrentItem()))e.setCurrentItem(e.getCurrentItem());}

    public boolean isVoidKey(ItemStack item) {
        if(item==null||!item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(voidKeyTag,PersistentDataType.BYTE)
            || type(item)==Relic.VOID_KEY;
    }
    public ItemStack createVoidKey() {
        return create(Relic.VOID_KEY,1);
    }

    public ItemStack rollVaultReward() {
        var random=java.util.concurrent.ThreadLocalRandom.current();
        return switch(VaultLootTable.reward(random.nextInt(10000))) {
            case SCROLL_ETERNITY -> createScrollEternity();
            case MYTHIC_CORE -> createMagicCore("shulker_levitation");
            case LIMIT_BREAK -> {
                LimitBreakType[] types=LimitBreakType.values();
                yield createScrollLimitBreak(types[random.nextInt(types.length)]);
            }
            case UNIQUE_SCROLL -> {
                UniqueEnchant[] enchants=UniqueEnchant.values();
                yield createScrollUnique(enchants[random.nextInt(enchants.length)]);
            }
            case CORE -> {
                List<MagicCore> cores=MAGIC_CORES.stream().filter(c->!c.id().equals("shulker_levitation")).toList();
                yield createMagicCore(cores.get(random.nextInt(cores.size())));
            }
            case EQUIPMENT -> {
                Relic[] equipment={Relic.RIFT_PICKAXE,Relic.SMELTER_PICKAXE,Relic.STORM_BOW,Relic.NOVA_BOW,Relic.RIFT_BLADE,Relic.ETERNAL_AEGIS};
                yield create(equipment[random.nextInt(equipment.length)],1);
            }
            case CONSUMABLE -> {
                int sub = random.nextInt(4);
                yield switch(sub) {
                    case 0 -> createKeyShard(random.nextInt(2) + 1);
                    case 1 -> createRepairStone(1);
                    case 2 -> createVoidElixir(1);
                    default -> new ItemStack(Material.ECHO_SHARD, random.nextInt(2) + 1);
                };
            }
        };
    }

    public ItemStack createGuideBook(com.example.voidscape.guide.GuideBookType type) {
        if (type == null) type = com.example.voidscape.guide.GuideBookType.CROPS;
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        org.bukkit.inventory.meta.BookMeta meta = (org.bukkit.inventory.meta.BookMeta) book.getItemMeta();
        meta.setTitle(type.bookTitle);
        meta.setAuthor(type.author);
        List<Component> pages = new java.util.ArrayList<>();
        for (com.example.voidscape.guide.GuidePage page : com.example.voidscape.guide.GuideData.pagesFor(type)) {
            pages.add(LegacyComponentSerializer.legacySection().deserialize(page.content()));
        }
        meta.pages(pages);
        meta.getPersistentDataContainer().set(plugin.key("guide_book"), PersistentDataType.BYTE, (byte) 1);
        meta.getPersistentDataContainer().set(plugin.key("guide_book_type"), PersistentDataType.STRING, type.name());

        List<Component> lore = new java.util.ArrayList<>();
        lore.add(LegacyComponentSerializer.legacySection().deserialize("§7" + type.englishTitle));
        lore.add(LegacyComponentSerializer.legacySection().deserialize(type.description));
        lore.add(Component.empty());
        lore.add(LegacyComponentSerializer.legacySection().deserialize("§eคลิกขวาเพื่อเปิดอ่าน"));
        meta.lore(lore);

        book.setItemMeta(meta);
        return book;
    }

    public ItemStack createCropGuideBook() {
        return createGuideBook(com.example.voidscape.guide.GuideBookType.CROPS);
    }

    public ItemStack createRelicGuideBook() {
        return createGuideBook(com.example.voidscape.guide.GuideBookType.RELICS);
    }

    public ItemStack createMagicGuideBook() {
        return createGuideBook(com.example.voidscape.guide.GuideBookType.MAGIC);
    }

    public ItemStack createGuideBook() {
        return createGuideBook(com.example.voidscape.guide.GuideBookType.CROPS);
    }

    public com.example.voidscape.guide.GuideBookType getGuideBookType(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        var pdc = item.getItemMeta().getPersistentDataContainer();
        String typeName = pdc.get(plugin.key("guide_book_type"), PersistentDataType.STRING);
        if (typeName != null) {
            try { return com.example.voidscape.guide.GuideBookType.valueOf(typeName); } catch (Exception ignored) {}
        }
        if (item.getItemMeta() instanceof org.bukkit.inventory.meta.BookMeta bm) {
            String title = bm.getTitle();
            if (title != null) {
                for (var t : com.example.voidscape.guide.GuideBookType.values()) {
                    if (title.equals(t.bookTitle)) return t;
                }
            }
        }
        return pdc.has(plugin.key("guide_book"), PersistentDataType.BYTE) ? com.example.voidscape.guide.GuideBookType.CROPS : null;
    }

    public Relic type(ItemStack item) {
        if(item==null||!item.hasItemMeta()) return null;
        ItemMeta meta = item.getItemMeta();
        if (isAeternumItem(meta)) return null;
        var pdc = meta.getPersistentDataContainer();

        // 1. Primary PDC key (voidscape:relic_v2)
        String id = pdc.get(type, PersistentDataType.STRING);

        // 2. Legacy / alternative PDC keys (voidscape:relic, evergarden:relic_v2, evergarden:relic)
        if (id == null) id = pdc.get(new NamespacedKey("voidscape", "relic"), PersistentDataType.STRING);
        if (id == null) id = pdc.get(new NamespacedKey("evergarden", "relic_v2"), PersistentDataType.STRING);
        if (id == null) id = pdc.get(new NamespacedKey("evergarden", "relic"), PersistentDataType.STRING);

        if (id != null) {
            String clean = id.trim().toUpperCase(Locale.ROOT);
            for (Relic r : Relic.values()) {
                if (r.name().equals(clean) || r.name().replace("_", "").equals(clean.replace("_", ""))) {
                    if (item.getType() == r.material) {
                        // Auto-heal to canonical voidscape:relic_v2 tag
                        if (!pdc.has(type, PersistentDataType.STRING)) {
                            pdc.set(type, PersistentDataType.STRING, r.name());
                            item.setItemMeta(meta);
                        }
                        return r;
                    }
                }
            }
        }

        // 3. CustomModelData string check (Critical for Bedrock/Geyser custom items and 1.21 component models)
        var cmdComp = meta.getCustomModelDataComponent();
        if (cmdComp != null && !cmdComp.getStrings().isEmpty()) {
            for (String str : cmdComp.getStrings()) {
                for (Relic r : Relic.values()) {
                    if (str.equalsIgnoreCase("voidscape:" + r.id()) || str.equalsIgnoreCase("evergarden:" + r.id())) {
                        if (item.getType() == r.material) {
                            pdc.set(type, PersistentDataType.STRING, r.name());
                            item.setItemMeta(meta);
                            return r;
                        }
                    }
                }
            }
        }

        // Names and lore are player-editable and shared by other plugins.
        // Only our PDC keys or explicitly namespaced models identify a relic.
        return null;
    }
    public boolean immune(Player p) {
        return p.getPersistentDataContainer().getOrDefault(shieldUntil,PersistentDataType.LONG,0L)>System.currentTimeMillis();
    }
    private boolean ready(Player p,Relic relic) {
        long end=p.getPersistentDataContainer().getOrDefault(plugin.key("cd_"+relic.id()),PersistentDataType.LONG,0L);
        long left=end-System.currentTimeMillis();
        if(left<=0)return true;
        p.sendActionBar(Component.text("คูลดาวน์ " + ((left+999)/1000) + " วินาที",NamedTextColor.GRAY));return false;
    }
    private void cooldown(Player p,Relic r,int seconds) {
        p.getPersistentDataContainer().set(plugin.key("cd_"+r.id()),PersistentDataType.LONG,System.currentTimeMillis()+seconds*1000L);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareCraft(org.bukkit.event.inventory.PrepareItemCraftEvent e) {
        CraftingInventory inv = e.getInventory();
        ItemStack[] matrix = inv.getMatrix();

        int totalItems = 0;
        int keyShards = 0;
        int astralDust = 0;
        int repairStones = 0;
        int amethystShards = 0;
        int glassBottles = 0;
        int scrollCount = 0;
        int damagedEquipCount = 0;
        ItemStack singleScroll = null;
        ItemStack singleEquip = null;
        ItemStack singleDamagedEquip = null;

        for (ItemStack it : matrix) {
            if (it == null || it.getType().isAir()) continue;
            totalItems++;
            if (isKeyShard(it)) {
                keyShards++;
            } else if (isAstralDust(it)) {
                astralDust++;
            } else if (isRepairStone(it)) {
                repairStones++;
            } else if (isScrollEternity(it) || getLimitBreakType(it) != null || getUniqueEnchant(it) != null) {
                scrollCount++;
                singleScroll = it;
            } else if (it.getType() == Material.AMETHYST_SHARD) {
                amethystShards++;
            } else if (it.getType() == Material.GLASS_BOTTLE) {
                glassBottles++;
            } else if (it.getType().getMaxDurability() > 0) {
                singleEquip = it;
                if (it.hasItemMeta() && it.getItemMeta() instanceof org.bukkit.inventory.meta.Damageable dmg && dmg.getDamage() > 0) {
                    damagedEquipCount++;
                    singleDamagedEquip = it;
                }
            }
        }

        // 1. Evergarden Key: exactly 4 Key Shards and nothing else
        if (keyShards == 4 && totalItems == 4) {
            inv.setResult(createVoidKey());
            return;
        }

        // 2. Vault Repair Stone: exactly 4 Astral Dust + 1 Amethyst Shard and nothing else
        if (astralDust == 4 && amethystShards == 1 && totalItems == 5) {
            inv.setResult(createRepairStone(1));
            return;
        }

        // 3. Void Walker Elixir: exactly 1 Astral Dust + 1 Glass Bottle and nothing else
        if (astralDust == 1 && glassBottles == 1 && totalItems == 2) {
            inv.setResult(createVoidElixir(1));
            return;
        }

        // 4. Bedrock Crafting Table Repair: 1 Repair Stone + 1 Damaged Item
        if (repairStones == 1 && damagedEquipCount == 1 && totalItems == 2) {
            org.bukkit.inventory.meta.Damageable dmg = (org.bukkit.inventory.meta.Damageable) singleDamagedEquip.getItemMeta();
            if (dmg.getDamage() > 0) {
                ItemStack result = singleDamagedEquip.clone();
                org.bukkit.inventory.meta.Damageable resDmg = (org.bukkit.inventory.meta.Damageable) result.getItemMeta();
                resDmg.setDamage(Math.max(0, resDmg.getDamage() - 500));
                result.setItemMeta(resDmg);
                inv.setResult(result);
                return;
            }
        }

        // 5. Bedrock Scrolls in Crafting Table: 1 Scroll + 1 Target Equipment
        if (scrollCount == 1 && singleEquip != null && totalItems == 2) {
            ItemStack result = evaluateScrollCraft(singleScroll, singleEquip);
            if (result != null) {
                inv.setResult(result);
                return;
            }
        }

        // 6. Only reserve the crafting grid when it actually contains Evergarden custom ingredients.
        // This avoids hijacking unrelated vanilla recipes on Bedrock, which can cause crafting-table hangs.
        if (keyShards > 0 || astralDust > 0 || repairStones > 0 || amethystShards > 0 || glassBottles > 0 || scrollCount > 0 || damagedEquipCount > 0) {
            inv.setResult(null);
            return;
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCraftItem(org.bukkit.event.inventory.CraftItemEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        CraftingInventory inv = e.getInventory();
        ItemStack result = inv.getResult();
        if (result == null || result.getType().isAir()) return;

        ItemStack[] matrix = inv.getMatrix();
        int totalItems = 0;
        int keyShards = 0;
        int astralDust = 0;
        int repairStones = 0;
        int amethystShards = 0;
        int glassBottles = 0;
        int scrollCount = 0;
        int damagedEquipCount = 0;

        for (ItemStack it : matrix) {
            if (it == null || it.getType().isAir()) continue;
            totalItems++;
            if (isKeyShard(it)) keyShards++;
            else if (isAstralDust(it)) astralDust++;
            else if (isRepairStone(it)) repairStones++;
            else if (it.getType() == Material.AMETHYST_SHARD) amethystShards++;
            else if (it.getType() == Material.GLASS_BOTTLE) glassBottles++;
            else if (isScrollEternity(it) || getLimitBreakType(it) != null || getUniqueEnchant(it) != null) scrollCount++;
            else if (it.hasItemMeta() && it.getItemMeta() instanceof org.bukkit.inventory.meta.Damageable dmg && dmg.getDamage() > 0) damagedEquipCount++;
        }

        boolean isCustomEvergardenCraft = false;

        if (keyShards == 4 && totalItems == 4) {
            isCustomEvergardenCraft = true;
            p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.2f);
            p.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, p.getLocation().add(0, 1, 0), 10, 0.3, 0.3, 0.3, 0.05);
            p.sendActionBar(Component.text("✦ ประกอบ Evergarden Key สำเร็จ!", NamedTextColor.LIGHT_PURPLE));
        } else if (astralDust == 4 && amethystShards == 1 && totalItems == 5) {
            isCustomEvergardenCraft = true;
            p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.2f);
            p.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, p.getLocation().add(0, 1, 0), 10, 0.3, 0.3, 0.3, 0.05);
            p.sendActionBar(Component.text("✦ สร้างศิลาฟื้นฟูมิติ (Vault Repair Stone) สำเร็จ!", NamedTextColor.GREEN));
        } else if (astralDust == 1 && glassBottles == 1 && totalItems == 2) {
            isCustomEvergardenCraft = true;
            p.playSound(p.getLocation(), Sound.ITEM_BOTTLE_FILL, 1.0f, 1.2f);
            p.getWorld().spawnParticle(Particle.PORTAL, p.getLocation().add(0, 1, 0), 10, 0.3, 0.3, 0.3, 0.05);
            p.sendActionBar(Component.text("✦ ปรุงน้ำยาเดินเวหา (Void Walker Elixir) สำเร็จ!", NamedTextColor.LIGHT_PURPLE));
        } else if (repairStones == 1 && damagedEquipCount == 1 && totalItems == 2) {
            isCustomEvergardenCraft = true;
            p.playSound(p.getLocation(), Sound.BLOCK_GRINDSTONE_USE, 1.0f, 1.2f);
            p.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, p.getLocation().add(0, 1, 0), 15, 0.3, 0.3, 0.3, 0.05);
            p.sendActionBar(Component.text("✦ ศิลาฟื้นฟูมิติ ซ่อมแซมความทนทาน 500 หน่วย!", NamedTextColor.GREEN));
        } else if (scrollCount == 1 && totalItems == 2) {
            isCustomEvergardenCraft = true;
            p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 1.25f);
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.35f);
            p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, p.getLocation().add(0, 1.2, 0), 25, 0.35, 0.35, 0.35, 0.1);
            p.sendActionBar(Component.text("✦ ปลุกเสกมนตราผ่านโต๊ะคราฟต์สำเร็จ!", NamedTextColor.GREEN));
        }

        if (isCustomEvergardenCraft) {
            e.setCurrentItem(result);
            inv.setResult(result);

            // Check if recipe is upgrade/repair: disable Shift-Click to prevent item dupes
            // NOTE: Only unregistered dynamic recipes (scroll upgrade / repair stone on equipment)
            // require manual ingredient consumption. Registered Bukkit recipes (Evergarden Key,
            // Repair Stone craft, Void Elixir) are natively consumed by Minecraft! Calling
            // consumeMatrixIngredients on registered recipes causes double-consumption (requiring 2 of each).
            if (scrollCount == 1 || (repairStones == 1 && damagedEquipCount == 1)) {
                if (e.isShiftClick()) {
                    e.setCancelled(true);
                    p.sendActionBar(Component.text("⚠ การตีบวก/ซ่อมแซม ไม่รองรับการกด Shift-Click (กรุณาแตะหยิบทีละชิ้น)", NamedTextColor.YELLOW));
                    return;
                }
                consumeMatrixIngredients(inv);
            }

            Bukkit.getScheduler().runTask(plugin, () -> {
                if (p.isOnline()) {
                    p.updateInventory();
                }
            });
        }
    }

    public void consumeMatrixIngredients(CraftingInventory inv) {
        ItemStack[] matrix = inv.getMatrix();
        boolean changed = false;
        for (int i = 0; i < matrix.length; i++) {
            ItemStack it = matrix[i];
            if (it != null && !it.getType().isAir()) {
                int amount = it.getAmount() - 1;
                if (amount <= 0) {
                    matrix[i] = null;
                } else {
                    it.setAmount(amount);
                    matrix[i] = it;
                }
                changed = true;
            }
        }
        if (changed) {
            inv.setMatrix(matrix);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCraftResultClick(org.bukkit.event.inventory.InventoryClickEvent e) {
        if (e instanceof org.bukkit.event.inventory.CraftItemEvent) return; // Handled authoritatively by onCraftItem
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (!(e.getInventory() instanceof CraftingInventory inv)) return;
        if (e.getSlotType() != org.bukkit.event.inventory.InventoryType.SlotType.RESULT && e.getRawSlot() != 0) return;

        // Prevent number-key swap, drop, or non-standard clicks from duping custom crafts
        if (e.getClick() == org.bukkit.event.inventory.ClickType.NUMBER_KEY
            || e.getClick() == org.bukkit.event.inventory.ClickType.DROP
            || e.getClick() == org.bukkit.event.inventory.ClickType.CONTROL_DROP
            || e.getClick() == org.bukkit.event.inventory.ClickType.SWAP_OFFHAND) {
            e.setCancelled(true);
            return;
        }

        ItemStack result = inv.getResult();
        if (result == null || result.getType().isAir()) return;

        ItemStack[] matrix = inv.getMatrix();
        int totalItems = 0;
        int keyShards = 0;
        int astralDust = 0;
        int repairStones = 0;
        int amethystShards = 0;
        int glassBottles = 0;
        int scrollCount = 0;
        int damagedEquipCount = 0;

        for (ItemStack it : matrix) {
            if (it == null || it.getType().isAir()) continue;
            totalItems++;
            if (isKeyShard(it)) keyShards++;
            else if (isAstralDust(it)) astralDust++;
            else if (isRepairStone(it)) repairStones++;
            else if (it.getType() == Material.AMETHYST_SHARD) amethystShards++;
            else if (it.getType() == Material.GLASS_BOTTLE) glassBottles++;
            else if (isScrollEternity(it) || getLimitBreakType(it) != null || getUniqueEnchant(it) != null) scrollCount++;
            else if (it.hasItemMeta() && it.getItemMeta() instanceof org.bukkit.inventory.meta.Damageable dmg && dmg.getDamage() > 0) damagedEquipCount++;
        }

        boolean isCustom = (keyShards == 4 && totalItems == 4)
            || (astralDust == 4 && amethystShards == 1 && totalItems == 5)
            || (astralDust == 1 && glassBottles == 1 && totalItems == 2)
            || (repairStones == 1 && damagedEquipCount == 1 && totalItems == 2)
            || (scrollCount == 1 && totalItems == 2);

        if (isCustom) {
            if (scrollCount == 1 || (repairStones == 1 && damagedEquipCount == 1)) {
                if (e.isShiftClick()) {
                    e.setCancelled(true);
                    p.sendActionBar(Component.text("⚠ การตีบวก/ซ่อมแซม ไม่รองรับการกด Shift-Click (กรุณาแตะหยิบทีละชิ้น)", NamedTextColor.YELLOW));
                    return;
                }
                consumeMatrixIngredients(inv);
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (p.isOnline()) {
                    p.updateInventory();
                }
            });
        }
    }

    public void triggerRiftBladeWarp(Player p) {
        if(p.getGameMode()==GameMode.SPECTATOR||!ready(p,Relic.RIFT_BLADE)||immune(p))return;
        Location start=p.getLocation(),target=null;
        Vector direction=start.getDirection();
        if(direction.getY() < -0.2) direction.setY(-0.05); // prevent diving into floor on slight look-down
        direction.normalize();
        double distance=plugin.integer("relics.blink.distance",8,2,12);
        for(double d=0.5;d<=distance;d+=0.5) {
            Location next=start.clone().add(direction.clone().multiply(d));
            if(!p.getWorld().getWorldBorder().isInside(next))break;
            if(!p.getWorld().isChunkLoaded(next.getBlockX()>>4,next.getBlockZ()>>4))break;
            BoundingBox body=BoundingBox.of(next.clone().add(-0.31,0,-0.31),next.clone().add(0.31,1.85,0.31));
            boolean collision=false;
            for(int x=(int)Math.floor(body.getMinX());x<=Math.floor(body.getMaxX());x++)
                for(int y=(int)Math.floor(body.getMinY());y<=Math.floor(body.getMaxY());y++)
                    for(int z=(int)Math.floor(body.getMinZ());z<=Math.floor(body.getMaxZ());z++)
                        if(!p.getWorld().getBlockAt(x,y,z).isPassable())collision=true;
            if(collision)break;target=next;
        }
        if(target!=null&&start.distanceSquared(target)>=1&&p.teleport(target,PlayerTeleportEvent.TeleportCause.PLUGIN)) {
            cooldown(p,Relic.RIFT_BLADE,plugin.integer("relics.blink.cooldown-seconds",8,2,120));
            p.setFallDistance(0);p.playSound(target,Sound.ENTITY_ENDERMAN_TELEPORT,0.7f,0.7f);
            p.spawnParticle(Particle.REVERSE_PORTAL,target.clone().add(0,1,0),12,0.3,0.5,0.3,0.03);
            p.sendActionBar(Component.text("✦ กรีดมิติวาร์ปพริบตา!", NamedTextColor.AQUA));
        } else {
            p.playSound(p.getLocation(), Sound.BLOCK_DISPENSER_FAIL, 0.7f, 1.4f);
            p.sendActionBar(Component.text("⚠ ทางข้างหน้าไม่เปิดโล่ง ไม่สามารถวาร์ปได้", NamedTextColor.YELLOW));
        }
    }

    public void triggerAegis(Player p) {
        if(p.getGameMode()==GameMode.SPECTATOR||!ready(p,Relic.ETERNAL_AEGIS))return;
        int seconds=plugin.integer("relics.shield.duration-seconds",3,1,5);
        p.getPersistentDataContainer().set(shieldUntil,PersistentDataType.LONG,System.currentTimeMillis()+seconds*1000L);
        cooldown(p,Relic.ETERNAL_AEGIS,plugin.integer("relics.shield.cooldown-seconds",75,10,600));
        p.playSound(p.getLocation(),Sound.ITEM_TOTEM_USE,0.6f,0.7f);
        p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING,p.getLocation().add(0,1,0),20,0.4,0.4,0.4,0.05);
        p.sendActionBar(Component.text("โล่แห่งความอมตะ · "+seconds+" วินาที",NamedTextColor.GOLD));
    }

    @EventHandler(priority=EventPriority.HIGH)
    public void interact(PlayerInteractEvent e) {
        Player p=e.getPlayer();
        ItemStack held=e.getItem();
        if(held==null||held.getType().isAir())return;

        // 1. Vault Repair Stone interaction (support right click, sneak + left click / swing for Bedrock)
        if(isRepairStone(held)) {
            if(e.getAction().isRightClick() || (p.isSneaking() && (e.getAction()==org.bukkit.event.block.Action.LEFT_CLICK_AIR||e.getAction()==org.bukkit.event.block.Action.LEFT_CLICK_BLOCK))) {
                e.setCancelled(true);
                useRepairStone(p,held);
                return;
            }
        }

        Relic r=type(held);
        if(r==Relic.ETERNAL_AEGIS) {
            if(e.getAction().isRightClick()) {
                e.setCancelled(true);
                triggerAegis(p);
                return;
            }
        } else if(r==Relic.RIFT_BLADE) {
            if(e.getAction().isRightClick() || (p.isSneaking() && (e.getAction()==org.bukkit.event.block.Action.LEFT_CLICK_AIR||e.getAction()==org.bukkit.event.block.Action.LEFT_CLICK_BLOCK))) {
                e.setCancelled(true);
                triggerRiftBladeWarp(p);
                return;
            }
        }
    }

    @EventHandler(priority=EventPriority.HIGH)
    public void onSwapHand(PlayerSwapHandItemsEvent e) {
        Player p=e.getPlayer();
        if(type(e.getMainHandItem())==Relic.RIFT_BLADE) {
            e.setCancelled(true);
            triggerRiftBladeWarp(p);
        } else if(type(e.getMainHandItem())==Relic.ETERNAL_AEGIS||type(e.getOffHandItem())==Relic.ETERNAL_AEGIS) {
            e.setCancelled(true);
            triggerAegis(p);
        }
    }

    @EventHandler(priority=EventPriority.HIGH)
    public void onSneak(PlayerToggleSneakEvent e) {
        if(!e.isSneaking())return;
        Player p=e.getPlayer();
        ItemStack main=p.getInventory().getItemInMainHand();
        ItemStack off=p.getInventory().getItemInOffHand();
        if(type(main)==Relic.ETERNAL_AEGIS||type(off)==Relic.ETERNAL_AEGIS) {
            long now=System.currentTimeMillis();
            long last=lastAegisSneak.getOrDefault(p.getUniqueId(),0L);
            lastAegisSneak.put(p.getUniqueId(),now);
            if(now-last<=550L) { // Double-sneak within 550ms to activate intentionally
                triggerAegis(p);
            }
        }
    }

    private void useRepairStone(Player p,ItemStack item) {
        ItemStack best=null;
        int maxDamage=0;
        List<ItemStack> candidates=new ArrayList<>();
        if(p.getInventory().getArmorContents()!=null) {
            candidates.addAll(Arrays.asList(p.getInventory().getArmorContents()));
        }
        candidates.add(p.getInventory().getItemInOffHand());
        candidates.add(p.getInventory().getItemInMainHand());
        for(ItemStack it:candidates) {
            if(it!=null&&it.hasItemMeta()&&it.getItemMeta() instanceof org.bukkit.inventory.meta.Damageable dmg) {
                if(dmg.getDamage()>maxDamage&&!it.equals(item)) {
                    maxDamage=dmg.getDamage();
                    best=it;
                }
            }
        }
        if(best==null||maxDamage<=0) {
            p.playSound(p.getLocation(),Sound.ENTITY_VILLAGER_NO,0.8f,1.0f);
            p.sendActionBar(Component.text("⚠ อุปกรณ์และชุดเกราะของคุณไม่ได้รับความเสียหาย",NamedTextColor.YELLOW));
            return;
        }
        org.bukkit.inventory.meta.Damageable dmg=(org.bukkit.inventory.meta.Damageable)best.getItemMeta();
        int repaired=Math.min(500,dmg.getDamage());
        dmg.setDamage(dmg.getDamage()-repaired);
        best.setItemMeta(dmg);

        item.subtract(1);
        p.updateInventory();
        p.playSound(p.getLocation(),Sound.BLOCK_GRINDSTONE_USE,1.0f,1.2f);
        p.getWorld().spawnParticle(Particle.HAPPY_VILLAGER,p.getLocation().add(0,1,0),15,0.3,0.3,0.3,0.05);
        p.sendActionBar(Component.text("✦ ศิลาฟื้นฟูมิติ ซ่อมแซมความทนทาน "+repaired+" หน่วย!",NamedTextColor.GREEN));
    }

    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true)
    public void onConsume(PlayerItemConsumeEvent e) {
        if(isVoidElixir(e.getItem())) {
            Player p=e.getPlayer();
            p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,20*180,1));
            p.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST,20*180,1));
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING,20*180,0));
            p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION,20*30,0));
            p.playSound(p.getLocation(),Sound.BLOCK_AMETHYST_BLOCK_CHIME,1.0f,1.4f);
            p.getWorld().spawnParticle(Particle.PORTAL,p.getLocation().add(0,1,0),30,0.5,0.5,0.5,0.1);
            p.sendActionBar(Component.text("✦ พลังแห่งเดินเวหาตื่นขึ้น! (Speed II + Jump II + Slow Falling 3 นาที)",NamedTextColor.LIGHT_PURPLE));
        }
    }

    @EventHandler(priority=EventPriority.HIGHEST)
    public void shieldDamage(EntityDamageEvent e) {if(e.getEntity() instanceof Player p&&immune(p))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void shieldAttack(EntityDamageByEntityEvent e) {
        Player p=e.getDamager() instanceof Player direct?direct:e.getDamager() instanceof Projectile pr&&pr.getShooter() instanceof Player shooter?shooter:null;
        if(p!=null&&immune(p))e.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void shoot(EntityShootBowEvent e) {
        if(!(e.getEntity() instanceof Player p))return;
        if(immune(p)){e.setCancelled(true);return;}
        Relic r=type(e.getBow());
        if((r!=Relic.NOVA_BOW&&r!=Relic.STORM_BOW)||e.getForce()<0.85)return;
        if(arrows.size()>=plugin.integer("performance.max-special-projectiles",64,1,256)) {e.setCancelled(true);return;}
        Entity arrow=e.getProjectile();
        arrow.getPersistentDataContainer().set(shot,PersistentDataType.STRING,r.name());
        arrow.getPersistentDataContainer().set(shotOwner,PersistentDataType.STRING,p.getUniqueId().toString());
        arrows.put(arrow.getUniqueId(),System.currentTimeMillis()+10000);
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void hit(ProjectileHitEvent e) {
        Projectile arrow=e.getEntity();String id=arrow.getPersistentDataContainer().get(shot,PersistentDataType.STRING);
        if(id==null)return;
        arrow.getPersistentDataContainer().remove(shot);arrows.remove(arrow.getUniqueId());
        if(!(arrow.getShooter() instanceof Player p)||!p.isOnline()||p.getWorld()!=arrow.getWorld()||immune(p)){arrow.remove();return;}
        Location at=arrow.getLocation();arrow.remove();
        List<LivingEntity> targets=new ArrayList<>(at.getNearbyLivingEntities(5.0));
        targets.removeIf(t->t.equals(p)||t instanceof ArmorStand||t.isDead()||t instanceof Player&&!plugin.getConfig().getBoolean("relics.allow-pvp",false));
        targets.sort(Comparator.comparingDouble(t->t.getLocation().distanceSquared(at)));
        if(id.equals(Relic.NOVA_BOW.name())) {
            p.getWorld().spawnParticle(Particle.EXPLOSION,at,1);
            p.getWorld().playSound(at,Sound.ENTITY_GENERIC_EXPLODE,0.7f,1.3f);
            for(LivingEntity t:targets.subList(0,Math.min(8,targets.size())))
                if(clear(at,t.getEyeLocation()))t.damage(Math.max(2,16-at.distance(t.getLocation())*3),p);
        } else {
            int n=0;
            for(LivingEntity t:targets) {
                if(!clear(at,t.getEyeLocation()))continue;
                t.getWorld().strikeLightningEffect(t.getLocation());t.damage(14-n*3,p);
                if(++n==3)break;
            }
        }
    }
    private boolean clear(Location a,Location b) {
        Vector v=b.toVector().subtract(a.toVector());double d=v.length();
        return d<0.3||a.getWorld().rayTraceBlocks(a,v,d-0.2,FluidCollisionMode.NEVER,true)==null;
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void mine(BlockBreakEvent e) {
        Player p=e.getPlayer();
        if(p.isSneaking()||p.getGameMode()!=GameMode.SURVIVAL||mining.contains(p.getUniqueId())||type(p.getInventory().getItemInMainHand())!=Relic.RIFT_PICKAXE)return;
        Block origin=e.getBlock();if(!Tag.MINEABLE_PICKAXE.isTagged(origin.getType()))return;
        RayTraceResult ray=p.rayTraceBlocks(6);
        org.bukkit.block.BlockFace face=ray==null?null:ray.getHitBlockFace();
        if(face==null) {
            float pitch=p.getLocation().getPitch();
            if(pitch>45)face=org.bukkit.block.BlockFace.UP;
            else if(pitch<-45)face=org.bukkit.block.BlockFace.DOWN;
            else {
                float yaw=(p.getLocation().getYaw()%360+360)%360;
                if(yaw>=45&&yaw<135)face=org.bukkit.block.BlockFace.WEST;
                else if(yaw>=135&&yaw<225)face=org.bukkit.block.BlockFace.NORTH;
                else if(yaw>=225&&yaw<315)face=org.bukkit.block.BlockFace.EAST;
                else face=org.bukkit.block.BlockFace.SOUTH;
            }
        }
        final org.bukkit.block.BlockFace finalFace=face;
        UUID id=p.getUniqueId();ItemStack held=p.getInventory().getItemInMainHand();
        Bukkit.getScheduler().runTask(plugin,()->{
            if(!p.isOnline()||p.getWorld()!=origin.getWorld()||type(p.getInventory().getItemInMainHand())!=Relic.RIFT_PICKAXE)return;
            if(!p.getInventory().getItemInMainHand().equals(held))return;
            mining.add(id);
            try {
                for(int a=-1;a<=1;a++)for(int b=-1;b<=1;b++) {
                    if(a==0&&b==0)continue;
                    Block block=finalFace.getModY()!=0?origin.getRelative(a,0,b):finalFace.getModX()!=0?origin.getRelative(0,a,b):origin.getRelative(a,b,0);
                    if(!block.getWorld().isChunkLoaded(block.getX()>>4,block.getZ()>>4)||p.getLocation().distanceSquared(block.getLocation())>64)continue;
                    if(!Tag.MINEABLE_PICKAXE.isTagged(block.getType())||block.getType().getHardness()<0||block.getState() instanceof org.bukkit.inventory.InventoryHolder)continue;
                    if(type(p.getInventory().getItemInMainHand())!=Relic.RIFT_PICKAXE)break;
                    p.breakBlock(block);
                }
            } finally {mining.remove(id);}
        });
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void smeltMine(BlockBreakEvent e) {
        Player p=e.getPlayer();
        if(p.getGameMode()!=GameMode.SURVIVAL && p.getGameMode()!=GameMode.CREATIVE && p.getGameMode()!=GameMode.ADVENTURE)return;
        ItemStack held=p.getInventory().getItemInMainHand();
        if(type(held)!=Relic.SMELTER_PICKAXE)return;
        Block block=e.getBlock();
        ItemStack smelted=smeltResult(block.getType());
        if(smelted==null)return;

        int fortune = getLimitBreakLevel(held, LimitBreakType.FORTUNE);
        if(fortune>0&&isFortuneOre(block.getType())) {
            int roll=java.util.concurrent.ThreadLocalRandom.current().nextInt(fortune+2);
            smelted.setAmount(smelted.getAmount()*Math.max(1,roll));
        }

        e.setDropItems(false);
        e.setExpToDrop(Math.max(e.getExpToDrop(),1));
        Location loc=block.getLocation().add(0.5,0.5,0.5);

        // Telepathy support: if tool has Telepathy enchant, put directly into player inventory
        boolean hasTelepathy = EnchantApplyListener.hasUnique(held, UniqueEnchant.TELEPATHY);
        if (hasTelepathy) {
            var leftover = p.getInventory().addItem(smelted);
            leftover.values().forEach(rem -> block.getWorld().dropItemNaturally(loc, rem));
        } else {
            block.getWorld().dropItemNaturally(loc,smelted);
        }

        block.getWorld().spawnParticle(Particle.FLAME,loc,8,0.2,0.2,0.2,0.03);
        block.getWorld().spawnParticle(Particle.SMOKE,loc,4,0.1,0.1,0.1,0.01);
        p.playSound(loc,Sound.BLOCK_FURNACE_FIRE_CRACKLE,0.7f,1.2f);
        p.sendActionBar(Component.text("✦ อีเต้อหลอมเพลิงมิติ: หลอมผลิตผลสำเร็จ!", NamedTextColor.GOLD));
    }
    private boolean isFortuneOre(Material m) {
        return switch(m) {
            case IRON_ORE, DEEPSLATE_IRON_ORE,
                 GOLD_ORE, DEEPSLATE_GOLD_ORE, NETHER_GOLD_ORE,
                 COPPER_ORE, DEEPSLATE_COPPER_ORE,
                 ANCIENT_DEBRIS -> true;
            default -> false;
        };
    }
    private ItemStack smeltResult(Material m) {
        return switch(m) {
            case SAND, RED_SAND, SUSPICIOUS_SAND -> new ItemStack(Material.GLASS,1);
            case IRON_ORE, DEEPSLATE_IRON_ORE -> new ItemStack(Material.IRON_INGOT,1);
            case RAW_IRON_BLOCK -> new ItemStack(Material.IRON_INGOT,9);
            case GOLD_ORE, DEEPSLATE_GOLD_ORE, NETHER_GOLD_ORE -> new ItemStack(Material.GOLD_INGOT,1);
            case RAW_GOLD_BLOCK -> new ItemStack(Material.GOLD_INGOT,9);
            case COPPER_ORE, DEEPSLATE_COPPER_ORE -> new ItemStack(Material.COPPER_INGOT,java.util.concurrent.ThreadLocalRandom.current().nextInt(2,6));
            case RAW_COPPER_BLOCK -> new ItemStack(Material.COPPER_INGOT,9);
            case ANCIENT_DEBRIS -> new ItemStack(Material.NETHERITE_SCRAP,1);
            case COBBLESTONE -> new ItemStack(Material.STONE,1);
            case STONE -> new ItemStack(Material.SMOOTH_STONE,1);
            case COBBLED_DEEPSLATE, DEEPSLATE -> new ItemStack(Material.DEEPSLATE,1);
            case BASALT -> new ItemStack(Material.SMOOTH_BASALT,1);
            case SANDSTONE -> new ItemStack(Material.SMOOTH_SANDSTONE,1);
            case RED_SANDSTONE -> new ItemStack(Material.SMOOTH_RED_SANDSTONE,1);
            case QUARTZ_BLOCK -> new ItemStack(Material.SMOOTH_QUARTZ,1);
            case CLAY -> new ItemStack(Material.TERRACOTTA,1);
            case NETHERRACK -> new ItemStack(Material.NETHER_BRICK,1);
            case WET_SPONGE -> new ItemStack(Material.SPONGE,1);
            case CACTUS -> new ItemStack(Material.GREEN_DYE,1);
            case OAK_LOG, SPRUCE_LOG, BIRCH_LOG, JUNGLE_LOG, ACACIA_LOG, DARK_OAK_LOG, MANGROVE_LOG, CHERRY_LOG,
                 STRIPPED_OAK_LOG, STRIPPED_SPRUCE_LOG, STRIPPED_BIRCH_LOG, STRIPPED_JUNGLE_LOG,
                 STRIPPED_ACACIA_LOG, STRIPPED_DARK_OAK_LOG, STRIPPED_MANGROVE_LOG, STRIPPED_CHERRY_LOG,
                 OAK_WOOD, SPRUCE_WOOD, BIRCH_WOOD, JUNGLE_WOOD, ACACIA_WOOD, DARK_OAK_WOOD, MANGROVE_WOOD, CHERRY_WOOD -> new ItemStack(Material.CHARCOAL,1);
            default -> null;
        };
    }
    public void tick() {
        if(arrows.isEmpty())return;
        long now=System.currentTimeMillis();
        arrows.entrySet().removeIf(e->{Entity a=Bukkit.getEntity(e.getKey());if(a==null)return true;if(now>=e.getValue()){a.remove();return true;}return false;});
    }
    public void close(){for(UUID id:arrows.keySet()){Entity e=Bukkit.getEntity(id);if(e!=null)e.remove();}arrows.clear();}
}
