package com.example.advancemagic.item;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.persistence.PersistentDataType;
import java.util.*;

/** Wearable armor sets drawn by both clients, never by following entities.
 * Chestplate, leggings and boots use the equipment asset advance_magic:<set>;
 * the helm is equippable without an asset so its 3D item model sits on the head.
 * Native durability/enchanting, 150% netherite armor and toughness. */
public final class ArmorSetService {
    public enum Piece {
        HELM(Material.NETHERITE_HELMET,EquipmentSlot.HEAD),
        CHESTPLATE(Material.NETHERITE_CHESTPLATE,EquipmentSlot.CHEST),
        LEGGINGS(Material.NETHERITE_LEGGINGS,EquipmentSlot.LEGS),
        BOOTS(Material.NETHERITE_BOOTS,EquipmentSlot.FEET);
        public final Material material;public final EquipmentSlot slot;
        Piece(Material material,EquipmentSlot slot){this.material=material;this.slot=slot;}
        public String id(){return name().toLowerCase(Locale.ROOT);}
        public static Piece parse(String id){
            for(Piece piece:values())if(piece.id().equalsIgnoreCase(id)||(piece==HELM&&"helmet".equalsIgnoreCase(id)))return piece;
            return null;
        }
    }
    public enum ArmorSet {
        SERAPH("ชุดเซราฟ",0xFFD978,0xD8DEEA,0x8FD8FF,
            new String[]{"Seraph Helm","Seraph Cuirass","Seraph Greaves","Seraph Sabatons"},
            new String[]{"หมวกเซราฟ เขาทองและหน้ากากศักดิ์สิทธิ์","เกราะอกขาวขลิบทอง ตราสุริยะ","สนับขาและผ้าคาดอักขระ","รองเท้าเหล็กขาวขลิบทอง"}),
        REVENANT("ชุดเรเวแนนต์",0xFF4A4A,0xC9C2DC,0xFF7A7A,
            new String[]{"Revenant Helm","Revenant Carapace","Revenant Greaves","Revenant Sabatons"},
            new String[]{"มงกุฎใบมีด ดวงตาแดงฉาน","เกราะแล็กเกอร์ดำ แกนผลึกโลหิต","สนับเข่าแกนแดง หนามแข้ง","รองเท้าเหล็กฟันเลื่อย"});
        public final String setName;public final int titleColor,loreColor,tagColor;
        private final String[] titles,descriptions;
        ArmorSet(String setName,int titleColor,int loreColor,int tagColor,String[] titles,String[] descriptions){
            this.setName=setName;this.titleColor=titleColor;this.loreColor=loreColor;this.tagColor=tagColor;
            this.titles=titles;this.descriptions=descriptions;
        }
        public String id(){return name().toLowerCase(Locale.ROOT);}
        public String title(Piece piece){return titles[piece.ordinal()];}
        public NamespacedKey model(Piece piece){return new NamespacedKey("advance_magic",id()+"_"+piece.id());}
        public NamespacedKey asset(){return new NamespacedKey("advance_magic",id());}
        public static ArmorSet parse(String id){
            for(ArmorSet set:values())if(set.id().equalsIgnoreCase(id))return set;
            return null;
        }
    }
    private final NamespacedKey key,version;
    private static final int SCHEMA=1;
    public ArmorSetService(org.bukkit.plugin.Plugin plugin){key=new NamespacedKey(plugin,"armor_set");version=new NamespacedKey(plugin,"armor_combat_version");}

    public ArmorSet set(ItemStack item){
        if(item==null||!item.hasItemMeta())return null;
        String id=item.getItemMeta().getPersistentDataContainer().get(key,PersistentDataType.STRING);
        if(id==null)return null;
        for(ArmorSet set:ArmorSet.values())for(Piece piece:Piece.values())
            if(id.equals(set.id()+"_"+piece.id())&&item.getType()==piece.material)return set;
        return null;
    }
    public boolean worn(org.bukkit.entity.Player player,ArmorSet set,Piece piece){
        ItemStack item=player.getInventory().getItem(piece.slot);
        if(set(item)!=set||item.getType()!=piece.material||item.getAmount()!=1)return false;
        var meta=item.getItemMeta();
        return !(meta instanceof Damageable damage)||damage.getDamage()<(damage.hasMaxDamage()?damage.getMaxDamage():item.getType().getMaxDurability());
    }
    public boolean full(org.bukkit.entity.Player player,ArmorSet set){
        for(Piece piece:Piece.values())if(!worn(player,set,piece))return false;return true;
    }

    /** Update only owned presentation/base stats; retain names, damage, enchants and upgrade PDC. */
    public boolean migrate(ItemStack item){
        ArmorSet set=set(item);if(set==null)return false;
        var meta=item.getItemMeta();
        if(meta.getPersistentDataContainer().getOrDefault(version,PersistentDataType.INTEGER,0)>=SCHEMA)return false;
        Piece piece=Arrays.stream(Piece.values()).filter(p->p.material==item.getType()).findFirst().orElseThrow();
        var plain=net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText();
        List<Component> lore=new ArrayList<>();
        lore.add(Component.text("Combat",net.kyori.adventure.text.format.NamedTextColor.GRAY).decoration(TextDecoration.ITALIC,false));
        if(meta.hasLore())for(Component line:meta.lore()){
            String text=plain.serialize(line);
            if(text.equals("Combat")||Arrays.asList(set.descriptions).contains(text)||text.startsWith(set.setName+" ·"))continue;
            lore.add(line);
        }
        meta.lore(lore);meta.setUnbreakable(false);meta.removeItemFlags(ItemFlag.HIDE_UNBREAKABLE);
        var equipment=meta.getEquippable();equipment.setDamageOnHurt(true);meta.setEquippable(equipment);
        // Defining attributes replaces the native component, so copy its complete defaults first.
        if(!meta.hasAttributeModifiers())meta.setAttributeModifiers(piece.material.getDefaultAttributeModifiers(piece.slot));
        for(Attribute attribute:List.of(Attribute.ARMOR,Attribute.ARMOR_TOUGHNESS)){
            var defaults=piece.material.getDefaultAttributeModifiers(piece.slot).get(attribute);
            for(AttributeModifier original:defaults){
                meta.removeAttributeModifier(attribute,original);
                meta.addAttributeModifier(attribute,new AttributeModifier(
                    new NamespacedKey("advance_magic",set.id()+"_"+piece.id()+"_"+attribute.getKey().getKey()),
                    original.getAmount()*1.5,original.getOperation(),EquipmentSlotGroup.getByName(piece.slot.name().toLowerCase(Locale.ROOT))));
            }
        }
        meta.getPersistentDataContainer().set(version,PersistentDataType.INTEGER,SCHEMA);item.setItemMeta(meta);return true;
    }
    public void migrate(org.bukkit.inventory.Inventory inventory){
        for(int slot=0;slot<inventory.getSize();slot++){ItemStack item=inventory.getItem(slot);if(migrate(item))inventory.setItem(slot,item);}
    }

    public ItemStack create(ArmorSet set,Piece piece){
        ItemStack item=new ItemStack(piece.material);
        var meta=item.getItemMeta();
        meta.displayName(Component.text(set.title(piece),TextColor.color(set.titleColor)).decoration(TextDecoration.ITALIC,false));
        meta.setItemModel(set.model(piece));
        var equippable=meta.getEquippable();
        equippable.setSlot(piece.slot);
        // No asset on the helm: the client then renders its item model on the head.
        equippable.setModel(piece==Piece.HELM?null:set.asset());
        equippable.setEquipSound(Sound.ITEM_ARMOR_EQUIP_NETHERITE);
        equippable.setSwappable(true);
        equippable.setDamageOnHurt(true);
        meta.setEquippable(equippable);
        meta.getPersistentDataContainer().set(key,PersistentDataType.STRING,set.id()+"_"+piece.id());
        item.setItemMeta(meta);
        migrate(item);
        return item;
    }

    private static String usage(){
        return "/magic armor give <"+String.join("|",Arrays.stream(ArmorSet.values()).map(ArmorSet::id).toList())
            +"> [helm|chestplate|leggings|boots|all] [player]";
    }

    /** /magic armor give <set> [piece|all] [player]; the set-less form gives Seraph pieces. */
    public boolean command(CommandSender sender,String[] args,java.util.function.BiFunction<CommandSender,String,Player> resolve){
        if(!sender.hasPermission("advance-magic.admin")){sender.sendMessage(ChatColor.RED+"No permission.");return true;}
        if(args.length<3||args.length>5||!args[1].equalsIgnoreCase("give")){sender.sendMessage(ChatColor.YELLOW+usage());return true;}
        ArmorSet set=ArmorSet.parse(args[2]);
        int next=3;
        if(set==null){set=ArmorSet.SERAPH;next=2;}
        String pieceArg=args.length>next?args[next]:"all";
        Piece piece=Piece.parse(pieceArg);boolean all=pieceArg.equalsIgnoreCase("all");
        if(!all&&piece==null){
            sender.sendMessage(ChatColor.RED+"ไม่รู้จัก \""+pieceArg+"\" · ตัวอย่าง: /magic armor give revenant all หรือ /magic armor give seraph helm");
            sender.sendMessage(ChatColor.GRAY+usage());return true;
        }
        if(args.length>next+2){sender.sendMessage(ChatColor.YELLOW+usage());return true;}
        Player target=args.length>next+1?resolve.apply(sender,args[next+1]):sender instanceof Player p?p:null;
        if(target==null){sender.sendMessage(ChatColor.RED+"ไม่พบผู้เล่นที่ออนไลน์"+(args.length>next+1?": "+args[next+1]:""));return true;}
        for(Piece entry:all?Piece.values():new Piece[]{piece}){
            var leftover=target.getInventory().addItem(create(set,entry));
            leftover.values().forEach(drop->target.getWorld().dropItemNaturally(target.getLocation(),drop));
        }
        sender.sendMessage(ChatColor.GREEN+"มอบ"+set.setName+" "+(all?"ครบ 4 ชิ้น":set.title(piece))+" ให้ "+target.getName());
        return true;
    }

    public List<String> complete(CommandSender sender,String[] args){
        if(!sender.hasPermission("advance-magic.admin"))return List.of();
        List<String> values=new ArrayList<>();
        if(args.length==2)values.add("give");
        if(args.length==3)for(ArmorSet s:ArmorSet.values())values.add(s.id());
        if(args.length==4){values.add("all");for(Piece p:Piece.values())values.add(p.id());}
        if(args.length==5){values.add("@s");for(Player p:Bukkit.getOnlinePlayers())values.add(p.getName());}
        String prefix=args[args.length-1].toLowerCase(Locale.ROOT);
        return values.stream().filter(v->v.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }
}
