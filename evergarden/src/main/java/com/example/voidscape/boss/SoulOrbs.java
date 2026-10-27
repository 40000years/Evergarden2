package com.example.voidscape.boss;

import com.example.voidscape.VoidscapePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import java.util.*;

/** Public identity of the six sanctuary Soul Orbs and the Core of Legend fused from them. */
public final class SoulOrbs {
    public enum Orb {
        ACHERON(ArkhaiaWaves.Kind.ACHERON), PHLEGETHON(ArkhaiaWaves.Kind.PHLEGETHON), COCYTUS(ArkhaiaWaves.Kind.COCYTUS),
        BANSHEE(ArkhaiaWaves.Kind.BANSHEE_WAIL), MALEFEASANCE(ArkhaiaWaves.Kind.MALEFEASANCE), STYX(ArkhaiaWaves.Kind.STYX);
        final ArkhaiaWaves.Kind kind;
        Orb(ArkhaiaWaves.Kind kind){this.kind=kind;}
        public String id(){return BossRewards.id(kind);}
        public String title(){return ArkhaiaMinions.name(kind);}
        public int rgb(){return BossRewards.color(kind);}
        public static Orb parse(String id){
            if(id==null)return null;
            for(Orb orb:values())if(orb.id().equalsIgnoreCase(id)||orb.name().equalsIgnoreCase(id))return orb;
            return null;
        }
    }
    public static final NamespacedKey LEGEND_MODEL=new NamespacedKey("voidscape","core_of_legend");
    private SoulOrbs(){}

    public static ItemStack orb(VoidscapePlugin plugin,Orb orb){return BossRewards.orb(plugin,orb.kind);}
    /** Only the authentic PDC marker counts; renamed vanilla hearts never pay. */
    public static Orb identify(VoidscapePlugin plugin,ItemStack item){
        if(item==null||item.getType()!=Material.HEART_OF_THE_SEA||!item.hasItemMeta())return null;
        return Orb.parse(item.getItemMeta().getPersistentDataContainer().get(plugin.key("boss_orb"),PersistentDataType.STRING));
    }
    public static ItemStack legendCore(VoidscapePlugin plugin){
        ItemStack item=new ItemStack(Material.HEART_OF_THE_SEA);var meta=item.getItemMeta();
        String title="Core of Legend";
        // Each letter walks through the six sanctuary colours.
        var name=Component.text("✦ ",NamedTextColor.GOLD).decoration(TextDecoration.ITALIC,false);
        Orb[] orbs=Orb.values();
        for(int i=0;i<title.length();i++)
            name=name.append(Component.text(String.valueOf(title.charAt(i)),TextColor.color(orbs[i%orbs.length].rgb())).decoration(TextDecoration.BOLD,true));
        meta.displayName(name.append(Component.text(" ✦",NamedTextColor.GOLD)).decoration(TextDecoration.ITALIC,false));
        meta.lore(List.of(
            Component.text("แก่นตำนาน หลอมจาก Soul Orb 100 ลูก",NamedTextColor.GRAY).decoration(TextDecoration.ITALIC,false),
            Component.text("ใช้แลกอาวุธตำนานและคัมภีร์ Legendary",NamedTextColor.GOLD).decoration(TextDecoration.ITALIC,false),
            Component.text("กับ Seraph ที่จุดเกิด Evergarden",NamedTextColor.GOLD).decoration(TextDecoration.ITALIC,false)));
        meta.setItemModel(LEGEND_MODEL);
        var model=meta.getCustomModelDataComponent();model.setStrings(List.of(LEGEND_MODEL.toString()));meta.setCustomModelDataComponent(model);
        meta.setEnchantmentGlintOverride(true);
        meta.getPersistentDataContainer().set(plugin.key("legend_core"),PersistentDataType.BYTE,(byte)1);
        item.setItemMeta(meta);return item;
    }
    public static boolean isLegendCore(VoidscapePlugin plugin,ItemStack item){
        return item!=null&&item.getType()==Material.HEART_OF_THE_SEA&&item.hasItemMeta()
            &&item.getItemMeta().getPersistentDataContainer().has(plugin.key("legend_core"),PersistentDataType.BYTE);
    }
}
