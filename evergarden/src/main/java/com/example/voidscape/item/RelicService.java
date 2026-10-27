package com.example.voidscape.item;

import com.example.voidscape.VoidscapePlugin;
import com.example.voidscape.enchant.EnchantApplyListener;
import com.example.voidscape.enchant.LimitBreakType;
import com.example.voidscape.enchant.UniqueEnchant;
import com.example.voidscape.enchant.UniqueCompatibility;
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

import java.util.*;

public final class RelicService implements Listener {
    public enum Relic {
        VOID_KEY(Material.TRIAL_KEY,"กุญแจ Evergarden","ใช้สำหรับเปิด Evergarden Vault ในวิหารโบราณ"),
        RIFT_PICKAXE(Material.NETHERITE_PICKAXE,"อีเต้อแยกพิภพ","ขุด 3×3 บล็อกพร้อมกัน · ย่อตัวเพื่อขุดทีละก้อน"),
        SMELTER_PICKAXE(Material.NETHERITE_PICKAXE,"อีเต้อหลอมเพลิงมิติ","หลอมบล็อกที่ขุดอัตโนมัติ (ทราย->กระจก, แร่->แท่งโลหะ)"),
        SCROLL_ETERNITY(Material.PAPER,"คัมภีร์ศิลานิรันดร์","ลากทับไอเทมเพื่อทำให้อุปกรณ์ 'ไม่มีวันพังถาวร (Unbreakable)'"),
        SCROLL_LIMIT_BREAK(Material.PAPER,"คัมภีร์ทลายขีดจำกัด","ลากทับไอเทมเพื่อเพิ่มเลเวลเอนแชนต์เดิม +1"),
        SCROLL_UNIQUE(Material.PAPER,"คัมภีร์มนตราโบราณ","ลากทับไอเทมเพื่อสลักเวทมนตร์เฉพาะตัว");

        public final Material material; public final String title,lore;
        Relic(Material m,String t,String l){material=m;title=t;lore=l;}
        public String id(){return name().toLowerCase(Locale.ROOT);}
    }

    private final VoidscapePlugin plugin;
    private final NamespacedKey type,voidKeyTag;
    private final Set<UUID> mining=new HashSet<>();

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
        new MagicCore("guardian_beam", "Core of the Guardian", "Guardian Beam"),
        new MagicCore("solar_apocalypse", "Core of the Sun", "Solar Apocalypse"),
        new MagicCore("chronos_final_hour", "Core of Time", "Chronos: Final Hour"),
        new MagicCore("heavens_judgment", "Core of Judgment", "Heaven's Judgment"),
        new MagicCore("verdant_sovereign", "Core of the Verdant Crown", "Verdant Sovereign"),
        new MagicCore("lunar_requiem", "Core of the Requiem Moon", "Lunar Requiem"),
        new MagicCore("crimson_oblivion", "Core of Oblivion", "Crimson Oblivion")
    );

    public RelicService(VoidscapePlugin plugin) {
        this.plugin=plugin; type=plugin.key("relic_v2");
        voidKeyTag=plugin.key("void_key");
        for(String namespace:List.of("evergarden","voidscape"))for(String id:RemovedRelics.RECIPES)Bukkit.removeRecipe(new NamespacedKey(namespace,id));
    }

    public ItemStack createMagicCore(MagicCore core) {
        ItemStack item=new ItemStack(Material.HEART_OF_THE_SEA);
        ItemMeta meta=item.getItemMeta();
        meta.setDisplayName(ChatColor.LIGHT_PURPLE+"✦ "+ChatColor.GOLD+core.title()+ChatColor.DARK_GRAY+" [LEGACY]");
        meta.setLore(List.of(ChatColor.GRAY+"แกนคราฟต์ระบบเก่า · เลิกใช้คราฟต์คทาแล้ว",
                ChatColor.DARK_AQUA+"เวทเดิม: "+core.wandTitle(),
                ChatColor.YELLOW+"ยังใช้เป็นเชื้อเพลิงที่แท่นฟื้นฟูได้"));
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
        var selector=meta.getCustomModelDataComponent();selector.setStrings(List.of("voidscape:"+relic.id()));meta.setCustomModelDataComponent(selector);
        meta.getPersistentDataContainer().set(type,PersistentDataType.STRING,relic.name());

        if(relic==Relic.VOID_KEY) {
            meta.getPersistentDataContainer().set(voidKeyTag,PersistentDataType.BYTE,(byte)1);
        } else if(relic.material==Material.NETHERITE_PICKAXE||relic.material==Material.NETHERITE_SWORD||relic.material==Material.BOW||relic.material==Material.SHIELD) {
            meta.addEnchant(Enchantment.UNBREAKING,3,true);
            if(relic==Relic.RIFT_PICKAXE||relic==Relic.SMELTER_PICKAXE) {meta.addEnchant(Enchantment.EFFICIENCY,5,true);meta.addEnchant(Enchantment.FORTUNE,3,true);}
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
            Component.text("ใช้ร่วมกับ Soulbound ไม่ได้",NamedTextColor.RED).decoration(TextDecoration.ITALIC,false),
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
        List<Component> lore=new ArrayList<>(List.of(
            Component.text("✦ "+ue.thaiTitle(),NamedTextColor.GOLD).decoration(TextDecoration.ITALIC,false),
            Component.text("ความสามารถ: "+ue.description(),NamedTextColor.GRAY).decoration(TextDecoration.ITALIC,false),
            Component.text("ประเภทอุปกรณ์: "+ue.category().name(),NamedTextColor.DARK_AQUA).decoration(TextDecoration.ITALIC,false),
            Component.text(UniqueCompatibility.ruleLabel(ue),NamedTextColor.RED).decoration(TextDecoration.ITALIC,false),
            Component.text("วิธีใช้: พิมพ์ /upgrade หรือลากแตะทับอุปกรณ์ในกระเป๋า",NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC,false),
            Component.text("[อัตราสำเร็จ 100% · ลากแตะเพื่อใช้งาน]",NamedTextColor.GREEN).decoration(TextDecoration.ITALIC,false)
        ));
        meta.lore(lore);
        meta.getPersistentDataContainer().set(plugin.key("unique_enchant"),PersistentDataType.STRING,ue.name());
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
            if (!UniqueCompatibility.canApplyEternity(target,this)) return null;
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
            if (!UniqueCompatibility.canApply(target,enchant,this)) return null;
            ItemMeta meta = target.getItemMeta();
            if (meta == null) return null;
            NamespacedKey key = new NamespacedKey("evergarden", "ue_" + enchant.id().toLowerCase(Locale.ROOT));
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







    public boolean migrate(ItemStack item) {
        if(RemovedRelics.clean(item))return true;
        if (item == null || !item.hasItemMeta()) return false;
        var guide=getGuideBookType(item);
        if(guide!=null&&item.getItemMeta().getPersistentDataContainer().has(plugin.key("guide_book"))){
            var updated=createGuideBook(guide).getItemMeta();
            if(!item.getItemMeta().equals(updated)){item.setItemMeta(updated);return true;}
        }
        if (!isManagedItem(item)) return false;
        boolean changed = false;
        if (EnchantApplyListener.hasUnique(item, UniqueEnchant.ADVANCE_TOOL)) {
            var meta = item.getItemMeta();
            boolean active=UniqueCompatibility.active(item,UniqueEnchant.ADVANCE_TOOL,this);
            if (meta != null && active && (!meta.hasTool() || meta.getTool().getRules().isEmpty())) {
                EnchantApplyListener.applyAdvanceToolComponent(meta);
                item.setItemMeta(meta);
                changed = true;
            } else if(meta != null && !active && meta.hasTool() && meta.getTool().getDefaultMiningSpeed()==25.0f) {
                meta.setTool(null);
                int efficiency=getLimitBreakLevel(item,LimitBreakType.EFFICIENCY);
                if(efficiency>=6)EnchantApplyListener.applyEfficiencyToolComponent(item.getType(),meta,efficiency);
                item.setItemMeta(meta);
                changed=true;
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
        NamespacedKey itemModel=null;
        if(Objects.equals(meta.getItemModel(),itemModel)&&data.getStrings().equals(List.of(model)))return changed;
        meta.setItemModel(itemModel);data.setStrings(List.of(model));meta.setCustomModelDataComponent(data);item.setItemMeta(meta);return true;
    }

    private boolean isManagedItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
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
            || type(item) != null;
    }

    public void migrate(Inventory inventory){for(int i=0;i<inventory.getSize();i++){var item=inventory.getItem(i);if(migrate(item))inventory.setItem(i,item);}}
    @EventHandler public void join(PlayerJoinEvent e){
        Player p=e.getPlayer();
        RemovedRelics.player(p);
        migrate(p.getInventory());
        migrate(p.getEnderChest());
        if(Arrays.stream(p.getInventory().getContents()).anyMatch(item->UniqueCompatibility.needsResolution(item,this)))
            plugin.message(p,"มีอุปกรณ์เก่าที่สลักมนตราขัดกัน · ถือชิ้นนั้นแล้วพิมพ์ /upgrade เพื่อเลือกพลังและรับคัมภีร์คืน");
    }
    @EventHandler public void open(org.bukkit.event.inventory.InventoryOpenEvent e){migrate(e.getInventory());migrate(e.getPlayer().getInventory());}
    @EventHandler public void held(PlayerItemHeldEvent e){
        var inventory=e.getPlayer().getInventory();
        var item=inventory.getItem(e.getNewSlot());
        if(migrate(item))inventory.setItem(e.getNewSlot(),item);
    }
    @EventHandler public void pickup(EntityPickupItemEvent e){if(RemovedRelics.matches(e.getItem().getItemStack())){e.setCancelled(true);e.getItem().remove();return;}var item=e.getItem().getItemStack();if(migrate(item))e.getItem().setItemStack(item);}
    @EventHandler public void drop(ItemSpawnEvent e){if(RemovedRelics.matches(e.getEntity().getItemStack())){e.setCancelled(true);return;}var item=e.getEntity().getItemStack();if(migrate(item))e.getEntity().setItemStack(item);}
    public void migrateEntity(Entity entity) {
        RemovedRelics.entity(entity);if(!entity.isValid())return;
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
    public ItemStack createVoidKey(int tier) {
        int clamped = Math.clamp(tier, 1, 5);
        String roman = toRoman(clamped);
        NamedTextColor tierColor = switch (clamped) {
            case 1 -> NamedTextColor.GREEN;
            case 2 -> NamedTextColor.AQUA;
            case 3 -> NamedTextColor.YELLOW;
            case 4 -> NamedTextColor.GOLD;
            default -> NamedTextColor.RED;
        };
        ItemStack item = create(Relic.VOID_KEY, 1);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("✦ Evergarden Key [Tier " + roman + "]", tierColor).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
            Component.text("กุญแจศักดิ์สิทธิ์จากวิหารระดับ Tier " + roman, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
            Component.text("✦ ไข Vault ระดับ Tier I ถึง Tier " + roman + " ได้", tierColor).decoration(TextDecoration.ITALIC, false),
            Component.text("EVERGARDEN · TRIAL KEY [TIER " + roman + "]", NamedTextColor.DARK_PURPLE).decoration(TextDecoration.ITALIC, false)
        ));
        meta.getPersistentDataContainer().set(plugin.key("key_tier"), PersistentDataType.INTEGER, clamped);
        item.setItemMeta(meta);
        return item;
    }
    /** Returns the key tier (1..5), or 5 for legacy/untagged keys so old keys work as Master Keys. */
    public int getVoidKeyTier(ItemStack item) {
        if (!isVoidKey(item)) return 0;
        Integer tier = item.getItemMeta().getPersistentDataContainer().get(plugin.key("key_tier"), PersistentDataType.INTEGER);
        return tier == null ? 5 : Math.clamp(tier, 1, 5);
    }

    private static final LimitBreakType[] DARK_LIMIT_BREAKS = {
        LimitBreakType.SHARPNESS, LimitBreakType.SMITE, LimitBreakType.SWEEPING_EDGE,
        LimitBreakType.LOOTING, LimitBreakType.DENSITY, LimitBreakType.BREACH
    };
    private static final LimitBreakType[] ASTRAL_LIMIT_BREAKS = {
        LimitBreakType.POWER, LimitBreakType.FEATHER_FALLING, LimitBreakType.LOOTING,
        LimitBreakType.DEPTH_STRIDER, LimitBreakType.SWEEPING_EDGE
    };
    private static final LimitBreakType[] TIME_LIMIT_BREAKS = {
        LimitBreakType.PROTECTION, LimitBreakType.EFFICIENCY, LimitBreakType.FORTUNE,
        LimitBreakType.UNBREAKING, LimitBreakType.THORNS
    };

    private static final UniqueEnchant[] DARK_UNIQUES = {
        UniqueEnchant.GUILLOTINE, UniqueEnchant.ECHO_STRIKE, UniqueEnchant.BLADE_VORTEX,
        UniqueEnchant.SOUL_HARVEST, UniqueEnchant.VAMPIRIC, UniqueEnchant.TITAN_BREACH,
        UniqueEnchant.SHADOW_STEP
    };
    private static final UniqueEnchant[] ASTRAL_UNIQUES = {
        UniqueEnchant.COLOSSUS_SLAYER, UniqueEnchant.RICOCHET, UniqueEnchant.KINETIC_GRAPPLE,
        UniqueEnchant.ABSOLUTE_ZERO, UniqueEnchant.SINGULARITY, UniqueEnchant.METEOR_ARROW,
        UniqueEnchant.THUNDERLORD
    };
    private static final UniqueEnchant[] TIME_UNIQUES = {
        UniqueEnchant.SEISMIC_SLAM, UniqueEnchant.VEIN_SMELTER, UniqueEnchant.ADVANCE_TOOL,
        UniqueEnchant.TELEPATHY, UniqueEnchant.PHOENIX_REBIRTH, UniqueEnchant.TITAN_STANCE,
        UniqueEnchant.SOULBOUND
    };

    private LimitBreakType pickThematicLimitBreak(com.example.voidscape.world.DungeonLayout.Kind kind, java.util.random.RandomGenerator random) {
        if (kind != null && random.nextInt(100) < 68) {
            LimitBreakType[] pool = switch (kind) {
                case SANCTUM_DARK -> DARK_LIMIT_BREAKS;
                case SANCTUM_ASTRAL -> ASTRAL_LIMIT_BREAKS;
                case SANCTUM_TIME -> TIME_LIMIT_BREAKS;
            };
            return pool[random.nextInt(pool.length)];
        }
        LimitBreakType[] all = LimitBreakType.values();
        return all[random.nextInt(all.length)];
    }

    private UniqueEnchant pickThematicUnique(com.example.voidscape.world.DungeonLayout.Kind kind, java.util.random.RandomGenerator random) {
        if (kind != null && random.nextInt(100) < 68) {
            UniqueEnchant[] pool = switch (kind) {
                case SANCTUM_DARK -> DARK_UNIQUES;
                case SANCTUM_ASTRAL -> ASTRAL_UNIQUES;
                case SANCTUM_TIME -> TIME_UNIQUES;
            };
            return pool[random.nextInt(pool.length)];
        }
        UniqueEnchant[] all = UniqueEnchant.values();
        return all[random.nextInt(all.length)];
    }

    public List<ItemStack> rollVaultRewards(com.example.voidscape.world.DungeonLayout.Site site) {
        return rollVaultRewards(site, java.util.concurrent.ThreadLocalRandom.current());
    }

    /**
     * Tier-scaled & element-biased Vault reward generator:
     * - Tier 1-2: 35/45% native over-cap enchant books; no Tier 1 weapons or staffs.
     * - Tier 2: independent 0.25% Rare weapon and 0.25% elemental staff bonuses.
     * - Tier 3-5: weapon bonuses 5/10/15%, elemental staff bonuses 10/15/20%, plus supplies.
     * - Tier 4-5: guaranteed non-consumable majors; Tier 5 rolls two majors.
     */
    public List<ItemStack> rollVaultRewards(com.example.voidscape.world.DungeonLayout.Site site, java.util.random.RandomGenerator random) {
        int tier = site == null ? 1 : Math.clamp(site.tier(), 1, 5);
        var kind = site == null ? null : site.kind();
        List<ItemStack> results = new ArrayList<>(4);

        int majorRolls = tier >= 5 ? 2 : 1;
        boolean forceMajor = tier >= 4;

        for (int r = 0; r < majorRolls; r++) {
            results.add(rollSingleTieredReward(kind, tier, forceMajor, random));
        }

        // Tier 3-5 bonus supply drop alongside major reward(s)
        if (tier >= 3) {
            ItemStack bonus = createBonusSupply(tier, random);
            if (bonus != null) results.add(bonus);
        }
        // Draw once per opening, even when Tier 5 grants two major rewards.
        ItemStack weapon = rollVaultWeaponBonus(site, random);
        if (weapon != null) results.add(weapon);
        ItemStack staff = rollVaultStaffBonus(site, random);
        if (staff != null) results.add(staff);
        return results;
    }

    /** Modeled weapon bonus, independent of the normal major rewards and supplies. */
    public ItemStack rollVaultWeaponBonus(com.example.voidscape.world.DungeonLayout.Site site,
                                         java.util.random.RandomGenerator random) {
        if (site == null || site.tier() < 2) return null;
        var reward = VaultLootTable.weaponReward(site.tier(), random.nextInt(10000));
        if (reward == VaultLootTable.WeaponReward.NONE) return null;
        if (reward == VaultLootTable.WeaponReward.LEGENDARY) {
            var addon = plugin.getServer().getPluginManager().getPlugin("advance-magic");
            if (addon instanceof com.example.advancemagic.AdvanceMagicPlugin magic
                    && magic.isEnabled() && magic.legendaryWeapons() != null) {
                var weapons = com.example.advancemagic.item.LegendaryWeaponService.Weapon.values();
                return magic.legendaryWeapons().create(weapons[random.nextInt(weapons.length)]);
            }
            // Advance Magic is optional: retain the weapon drop as an enabled Rare instead.
        }
        var weapons = plugin.adventurerWeapons();
        return weapons == null ? null : weapons.create(weapons.pickThematicWeapon(site.kind(), random));
    }

    /** Tier-aware staff bonus; never repeats for Astral or Tier 5's second major reward. */
    public ItemStack rollVaultStaffBonus(com.example.voidscape.world.DungeonLayout.Site site,
                                        java.util.random.RandomGenerator random) {
        int chance = site == null ? 0 : VaultLootTable.staffChanceBps(site.tier());
        if (chance == 0 || !plugin.getConfig().getBoolean("staff-loot.enabled", true)) return null;
        var addon = plugin.getServer().getPluginManager().getPlugin("advance-magic");
        if (!(addon instanceof com.example.advancemagic.AdvanceMagicPlugin magic) || !magic.isEnabled()
                || magic.staffSpells() == null || magic.staffs() == null) return null;
        if (random.nextInt(10000) >= chance) return null;
        var elements = com.example.advancemagic.item.ElementalStaffService.Element.values();
        return magic.staffSpells().createStaff(elements[random.nextInt(elements.length)],
                com.example.advancemagic.elemental.StaffSpellService.Tier.APPRENTICE);
    }

    /** Real stored enchants, stamped for the existing scoped high-level anvil transfer. */
    public ItemStack createVaultEnchantBook(int tier, java.util.random.RandomGenerator random) {
        Enchantment[] enchants = {Enchantment.SHARPNESS, Enchantment.PROTECTION, Enchantment.LOOTING,
                Enchantment.FORTUNE, Enchantment.EFFICIENCY, Enchantment.UNBREAKING};
        Enchantment enchant = enchants[random.nextInt(enchants.length)];
        int level = switch (enchant.getKey().getKey()) {
            case "sharpness", "protection", "efficiency" -> 6;
            default -> 4;
        };
        if (tier >= 2 && random.nextInt(100) < 30) level++;
        ItemStack item = new ItemStack(Material.ENCHANTED_BOOK);
        var meta = (org.bukkit.inventory.meta.EnchantmentStorageMeta) item.getItemMeta();
        meta.setDisplayName(ChatColor.AQUA + "สมุดมนตราแห่ง Vault");
        meta.setLore(List.of(ChatColor.GRAY + "ใช้กับอุปกรณ์ที่รองรับบนทั่งตีเหล็ก"));
        meta.addStoredEnchant(enchant, level, true);
        plugin.bossEnchants().markGenerated(meta);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createBonusSupply(int tier, java.util.random.RandomGenerator random) {
        int roll = random.nextInt(100);
        if (roll < 40) {
            int feathers = tier >= 5 ? 3 : tier >= 4 ? 2 : 1;
            return com.example.voidscape.trade.SeraphFeathers.feather(plugin, feathers);
        }
        var addon = plugin.getServer().getPluginManager().getPlugin("advance-magic");
        if (addon instanceof com.example.advancemagic.AdvanceMagicPlugin magic && magic.isEnabled()) {
            if (roll < 75 && magic.staffSpells() != null) {
                var spells = com.example.advancemagic.elemental.ElementalSpell.values();
                ItemStack scroll = magic.staffSpells().createScroll(spells[random.nextInt(spells.length)]);
                scroll.setAmount(tier >= 5 ? 2 : 1);
                return scroll;
            }
            var qualities = com.example.advancemagic.mana.ManaPotionService.Quality.values();
            var q = tier >= 4 ? qualities[3] : qualities[2]; // Supreme for Tier 4-5, Good for Tier 3
            ItemStack pot = magic.manaPotions().create(q);
            pot.setAmount(tier >= 5 ? 2 : 1);
            return pot;
        }
        return com.example.voidscape.trade.SeraphFeathers.feather(plugin, tier >= 4 ? 2 : 1);
    }

    private ItemStack rollSingleTieredReward(com.example.voidscape.world.DungeonLayout.Kind kind, int tier,
                                             boolean forceMajor, java.util.random.RandomGenerator random) {
        if (tier <= 2 && VaultLootTable.enchantBookReward(tier, random.nextInt(10000)))
            return createVaultEnchantBook(tier, random);

        // Extra Mythic bonus roll for Tier 3-5
        int mythicBonusBps = switch (tier) {
            case 1 -> 0;
            case 2 -> 60;   // +0.6%
            case 3 -> 150;  // +1.5%
            case 4 -> 320;  // +3.2%
            default -> 550; // +5.5% on Tier 5
        };
        if (mythicBonusBps > 0 && random.nextInt(10000) < mythicBonusBps) {
            boolean preferEternity = kind == com.example.voidscape.world.DungeonLayout.Kind.SANCTUM_TIME
                    ? random.nextInt(100) < 65
                    : kind == com.example.voidscape.world.DungeonLayout.Kind.SANCTUM_ASTRAL
                    ? random.nextInt(100) < 30
                    : random.nextBoolean();
            if (preferEternity || tier < 3) {
                return createScrollEternity();
            } else {
                var addon = plugin.getServer().getPluginManager().getPlugin("advance-magic");
                if (addon instanceof com.example.advancemagic.AdvanceMagicPlugin magic && magic.isEnabled()) {
                    return magic.flyingStaff().create();
                }
                return createScrollEternity();
            }
        }

        VaultLootTable.Reward category = VaultLootTable.activeReward(random.nextInt(9250));
        // All staffs start at Tier 3, apart from the explicit tiny Tier 2 elemental bonus.
        if (tier < 3 && category == VaultLootTable.Reward.FLYING_STAFF)
            category = VaultLootTable.Reward.EQUIPMENT;
        if (category == VaultLootTable.Reward.CONSUMABLE) {
            int rerollChance = switch (tier) {
                case 1 -> 0;
                case 2 -> 38;
                case 3 -> 68;
                default -> 100; // Tier 4 & 5 never roll plain consumable
            };
            if (forceMajor || random.nextInt(100) < rerollChance) {
                // Reroll directly into the major non-consumable band (0..3199 in activeReward: Limit Break, Unique, Equipment)
                // with thematic boost for Time Sanctum toward Equipment (Rift/Smelter Pickaxe)
                if (kind == com.example.voidscape.world.DungeonLayout.Kind.SANCTUM_TIME && random.nextInt(100) < 25) {
                    category = VaultLootTable.Reward.EQUIPMENT;
                } else {
                    category = VaultLootTable.activeReward(random.nextInt(3200));
                }
            }
        }

        return switch (category) {
            case FLYING_STAFF -> {
                var addon = plugin.getServer().getPluginManager().getPlugin("advance-magic");
                yield addon instanceof com.example.advancemagic.AdvanceMagicPlugin magic && magic.isEnabled()
                        ? magic.flyingStaff().create() : createScrollEternity();
            }
            case SCROLL_ETERNITY -> createScrollEternity();
            case MYTHIC_CORE, CORE -> throw new IllegalStateException("Retired core ticket in active Vault table");
            case LIMIT_BREAK -> createScrollLimitBreak(pickThematicLimitBreak(kind, random));
            case UNIQUE_SCROLL -> createScrollUnique(pickThematicUnique(kind, random));
            case EQUIPMENT -> {
                Relic[] equipment = {Relic.RIFT_PICKAXE, Relic.SMELTER_PICKAXE};
                yield create(equipment[random.nextInt(equipment.length)], 1);
            }
            case CONSUMABLE -> {
                var addon = plugin.getServer().getPluginManager().getPlugin("advance-magic");
                if (addon instanceof com.example.advancemagic.AdvanceMagicPlugin magic && magic.isEnabled()) {
                    var qualities = com.example.advancemagic.mana.ManaPotionService.Quality.values();
                    int draw = random.nextInt(100);
                    var quality = tier >= 2
                            ? (draw < 55 ? qualities[2] : qualities[3])
                            : (draw < 60 ? qualities[1] : draw < 90 ? qualities[2] : qualities[3]);
                    yield magic.manaPotions().create(quality);
                }
                yield new ItemStack(Material.ECHO_SHARD, random.nextInt(2) + tier);
            }
        };
    }

    public ItemStack rollVaultReward() {
        return rollVaultReward(java.util.concurrent.ThreadLocalRandom.current());
    }

    /** Untiered admin previews use the same Tier 1 major reward pool as real Vaults. */
    public ItemStack rollVaultReward(java.util.random.RandomGenerator random) {
        return rollSingleTieredReward(null, 1, false, random);
    }

    public ItemStack createGuideBook(com.example.voidscape.guide.GuideBookType type) {
        if (type == null) type = com.example.voidscape.guide.GuideBookType.RELICS;
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


    public ItemStack createRelicGuideBook() {
        return createGuideBook(com.example.voidscape.guide.GuideBookType.RELICS);
    }

    public ItemStack createMagicGuideBook() {
        return createGuideBook(com.example.voidscape.guide.GuideBookType.MAGIC);
    }

    public ItemStack createGuideBook() {
        return createGuideBook(com.example.voidscape.guide.GuideBookType.RELICS);
    }

    public com.example.voidscape.guide.GuideBookType getGuideBookType(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        var pdc = item.getItemMeta().getPersistentDataContainer();
        String typeName = pdc.get(plugin.key("guide_book_type"), PersistentDataType.STRING);
        if (typeName != null) {
            try { return com.example.voidscape.guide.GuideBookType.valueOf(typeName); } catch (Exception ignored) { return null; }
        }
        if (item.getItemMeta() instanceof org.bukkit.inventory.meta.BookMeta bm) {
            String title = bm.getTitle();
            if (title != null) {
                for (var t : com.example.voidscape.guide.GuideBookType.values()) {
                    if (title.equals(t.bookTitle)) return t;
                }
            }
        }
        return pdc.has(plugin.key("guide_book"), PersistentDataType.BYTE) ? com.example.voidscape.guide.GuideBookType.RELICS : null;
    }

    public Relic type(ItemStack item) {
        if(item==null||!item.hasItemMeta()) return null;
        ItemMeta meta = item.getItemMeta();
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
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareCraft(org.bukkit.event.inventory.PrepareItemCraftEvent e) {
        CraftingInventory inv = e.getInventory();
        if(Arrays.stream(inv.getMatrix()).anyMatch(RemovedRelics::matches)){RemovedRelics.clean(inv);inv.setResult(null);return;}
        ItemStack[] matrix = inv.getMatrix();

        int totalItems = 0;
        int scrollCount = 0;
        ItemStack singleScroll = null;
        ItemStack singleEquip = null;

        for (ItemStack it : matrix) {
            if (it == null || it.getType().isAir()) continue;
            totalItems++;
            if (isScrollEternity(it) || getLimitBreakType(it) != null || getUniqueEnchant(it) != null) {
                scrollCount++;
                singleScroll = it;
            } else if (it.getType().getMaxDurability() > 0) {
                singleEquip = it;
            }
        }

        // 5. Bedrock Scrolls in Crafting Table: 1 Scroll + 1 Target Equipment
        if (scrollCount == 1 && singleEquip != null && singleEquip.getAmount() == 1 && totalItems == 2) {
            ItemStack result = evaluateScrollCraft(singleScroll, singleEquip);
            if (result != null) {
                inv.setResult(result);
                return;
            }
        }

        // 6. Only reserve the crafting grid when it actually contains Evergarden custom ingredients.
        // This avoids hijacking unrelated vanilla recipes on Bedrock, which can cause crafting-table hangs.
        if (scrollCount > 0) {
            inv.setResult(null);
            return;
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCraftItem(org.bukkit.event.inventory.CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        CraftingInventory inventory = event.getInventory();
        if (handleDynamicCraft(event, player, inventory)) return;
        // Registered recipes are consumed by Minecraft after a successful transfer.
        ItemStack result = inventory.getResult();
        if (result == null || result.getType().isAir()) return;
        if (isVoidKey(result)) {
            player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.2f);
            Bukkit.getScheduler().runTask(plugin, player::updateInventory);
        }
    }

    /** Re-evaluate the two inputs rather than trusting a cached result-slot preview. */
    private ItemStack dynamicCraftResult(ItemStack[] matrix) {
        ItemStack catalyst = null, equipment = null;
        int occupied = 0;
        for (ItemStack item : matrix) {
            if (item == null || item.getType().isAir()) continue;
            occupied++;
            if (isScrollEternity(item)
                    || getLimitBreakType(item) != null || getUniqueEnchant(item) != null) {
                if (catalyst != null) return null;
                catalyst = item;
            } else if (item.getType().getMaxDurability() > 0) {
                if (equipment != null) return null;
                equipment = item;
            } else return null;
        }
        if (occupied != 2 || catalyst == null || equipment == null || equipment.getAmount() != 1) return null;
        return evaluateScrollCraft(catalyst, equipment);
    }

    /** One cancelled, synchronous transaction owns both consumption and delivery. */
    private boolean handleDynamicCraft(org.bukkit.event.inventory.InventoryClickEvent event,
                                       Player player, CraftingInventory inventory) {
        ItemStack[] matrix = inventory.getMatrix();
        boolean dynamic = Arrays.stream(matrix).anyMatch(item -> isScrollEternity(item)
                || getLimitBreakType(item) != null || getUniqueEnchant(item) != null);
        if (!dynamic) return false;
        event.setCancelled(true);
        if (event.getClick() != org.bukkit.event.inventory.ClickType.LEFT
                && event.getClick() != org.bukkit.event.inventory.ClickType.RIGHT) return true;
        ItemStack result = dynamicCraftResult(matrix);
        ItemStack preview = inventory.getResult();
        if (result == null || preview == null || preview.getAmount() != result.getAmount() || !preview.isSimilar(result)) return true;
        ItemStack cursor = event.getCursor();
        ItemStack delivered = result.clone();
        if (cursor != null && !cursor.getType().isAir()) {
            if (!cursor.isSimilar(result) || cursor.getAmount() > result.getMaxStackSize() - result.getAmount()) return true;
            delivered.setAmount(cursor.getAmount() + result.getAmount());
        }
        ItemStack[] remaining = Arrays.stream(matrix).map(item -> {
            if (item == null || item.getType().isAir()) return null;
            if (item.getAmount() == 1) return null;
            ItemStack copy = item.clone(); copy.setAmount(copy.getAmount() - 1); return copy;
        }).toArray(ItemStack[]::new);
        inventory.setMatrix(remaining);
        player.setItemOnCursor(delivered);
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 1.25f);
        // Refresh client state only; never replay item snapshots in a later tick.
        Bukkit.getScheduler().runTask(plugin, player::updateInventory);
        return true;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCraftResultClick(org.bukkit.event.inventory.InventoryClickEvent event) {
        if (event instanceof org.bukkit.event.inventory.CraftItemEvent) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getInventory() instanceof CraftingInventory inventory)) return;
        if (event.getSlotType() != org.bukkit.event.inventory.InventoryType.SlotType.RESULT) return;
        handleDynamicCraft(event, player, inventory);
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
        // Only ores earn the smelting XP; placed cobblestone, logs or sand would be an endless XP farm.
        if(block.getType().name().endsWith("_ORE")||block.getType()==Material.ANCIENT_DEBRIS)e.setExpToDrop(Math.max(e.getExpToDrop(),1));
        Location loc=block.getLocation().add(0.5,0.5,0.5);

        // Telepathy support: if tool has Telepathy enchant, put directly into player inventory
        boolean hasTelepathy = UniqueCompatibility.active(held, UniqueEnchant.TELEPATHY,this);
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
}
