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
 * Native durability/enchanting, 150% netherite armor and toughness. The dragon sets (AM 1.40.0) carry
 * fixed stats per piece (armor 12/19/14/12, toughness 8, knockback resistance 10%, durability
 * 1000/1200/1100/1050); vanilla clamps armor at 30 and toughness at 20, so {@link DragonSetPowers} turns the
 * part above those caps into extra damage reduction. Their set powers live there too. */
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
            new String[]{"มงกุฎใบมีด ดวงตาแดงฉาน","เกราะแล็กเกอร์ดำ แกนผลึกโลหิต","สนับเข่าแกนแดง หนามแข้ง","รองเท้าเหล็กฟันเลื่อย"}),
        // Dragon Conqueror sets: fixed per-piece stats (DRAGON_*) and full-set powers in DragonSetPowers.
        INFERNAL_DRAKE("ชุดอสูรมังกรเพลิง",0xFF8A2A,0xE8CDB6,0xFFB347,2.0,
            new String[]{"Infernal Drake Helm","Infernal Drake Cuirass","Infernal Drake Greaves","Infernal Drake Sabatons"},
            new String[]{"หมวกเขามังกรเพลิง ดวงตาลาวา","เกราะเกล็ดดำหัวใจมังกร ผ้าคลุมปีกมังกรลุกไหม้","สนับขาเกล็ดดำ รอยร้าวลาวา","รองเท้ากรงเล็บมังกร"}),
        FROSTWYRM("ชุดมังกรน้ำแข็งนิรันดร์",0x9BE6FF,0xD8E6F5,0xBDEFFF,2.0,
            new String[]{"Frostwyrm Helm","Frostwyrm Cuirass","Frostwyrm Greaves","Frostwyrm Sabatons"},
            new String[]{"มงกุฎผลึกน้ำแข็ง ดวงตาเยือกแข็ง","เกราะเงินขนสัตว์ ผ้าคลุมเกล็ดหิมะ","สนับขาผลึกน้ำแข็ง","รองเท้าขนสัตว์ หนามน้ำแข็ง"}),
        TEMPEST_DRAKE("ชุดมังกรพายุอัสนี",0xC79BFF,0xE2DAF2,0xFFD36B,2.0,
            new String[]{"Tempest Drake Helm","Tempest Drake Cuirass","Tempest Drake Greaves","Tempest Drake Sabatons"},
            new String[]{"เขาทองสายฟ้า ปีกขนนกทอง","เกราะมุกขลิบทอง ผ้าคลุมพายุม่วง","สนับขามุกทอง สายฟ้าแลบ","รองเท้าทองอัสนี"});
        public static final String DRAGON_TAG="ชุดเกราะผู้พิชิตมังกร · ",DRAGON_POWER="พลังชุด: เร็วๆ นี้";
        /** Lore lines of set powers start with this, so a migration can replace them. */
        public static final String POWER_MARK="◆ ";
        /** Dragon armor per piece (helm, chestplate, leggings, boots). */
        public static final double[] DRAGON_ARMOR={12,19,14,12},DRAGON_TOUGHNESS={8,8,8,8},DRAGON_KNOCKBACK={.1,.1,.1,.1};
        public static final int[] DRAGON_DURABILITY={1000,1200,1100,1050};
        public java.util.List<String> powers(){
            return switch(this){
                case INFERNAL_DRAKE->java.util.List.of("พลังชุด (ครบ 4 ชิ้น):",
                    "กันไฟและลาวา · ยิ่ง HP ต่ำยิ่งตีแรง (สูงสุด +75%)",
                    "โจมตีมีโอกาส 25% ยิงลูกไฟเบลซตาม",
                    "กันตาย 1 ครั้ง (คูลดาวน์ 40 วินาที)");
                case FROSTWYRM->java.util.List.of("พลังชุด (ครบ 4 ชิ้น):",
                    "กันความหนาว · วิ่งเร็วบนหิมะ ไม่จมหิมะผง",
                    "ยิ่งโดนตียิ่งถึก (ลดดาเมจ 3%/ครั้ง สูงสุด 30%)",
                    "โจมตีมีโอกาส 15% ทำให้ศัตรูชะงัก 3 วินาที",
                    "กันตาย 1 ครั้ง (คูลดาวน์ 40 วินาที)");
                case TEMPEST_DRAKE->java.util.List.of("พลังชุด (ครบ 4 ชิ้น):",
                    "กันฟ้าผ่า · โดนฟ้าผ่า: ตีแรง +300% 4 วินาที",
                    "และหัวใจทอง 5 ดวง (ไม่ซ้อน, คูลดาวน์ 20 วินาที)",
                    "โจมตีมีโอกาส 20% เรียกสายฟ้าผ่าศัตรูหรือตัวเอง",
                    "ศัตรูเลือดต่ำกว่า 100 ที่โดนผ่าจะตายทันที (ไม่มีผลกับผู้เล่น)");
                default->java.util.List.<String>of();
            };
        }
        /** Shown on every dragon piece: the part above vanilla's 30 armor / 20 toughness cap still counts. */
        public static final String OVERCAP_LINE="เกราะเกินเพดาน 30/ความทนเกิน 20 → ลดดาเมจเพิ่ม (ครบชุด 21%)";
        public final String setName;public final int titleColor,loreColor,tagColor;public final double scale;
        private final String[] titles,descriptions;
        ArmorSet(String setName,int titleColor,int loreColor,int tagColor,String[] titles,String[] descriptions){
            this(setName,titleColor,loreColor,tagColor,1.5,titles,descriptions);
        }
        ArmorSet(String setName,int titleColor,int loreColor,int tagColor,double scale,String[] titles,String[] descriptions){
            this.setName=setName;this.titleColor=titleColor;this.loreColor=loreColor;this.tagColor=tagColor;this.scale=scale;
            this.titles=titles;this.descriptions=descriptions;
        }
        public boolean dragon(){return scale>=2.0;}
        public String description(Piece piece){return descriptions[piece.ordinal()];}
        public String id(){return name().toLowerCase(Locale.ROOT);}
        public String title(Piece piece){return titles[piece.ordinal()];}
        public NamespacedKey model(Piece piece){return new NamespacedKey("advance_magic",id()+"_"+piece.id());}
        public NamespacedKey asset(){return new NamespacedKey("advance_magic",id());}
        public static ArmorSet parse(String id){
            for(ArmorSet set:values())if(set.id().equalsIgnoreCase(id))return set;
            if(id.equalsIgnoreCase("infernal"))return INFERNAL_DRAKE;
            if(id.equalsIgnoreCase("frost"))return FROSTWYRM;
            if(id.equalsIgnoreCase("tempest"))return TEMPEST_DRAKE;
            return null;
        }
        public static List<ArmorSet> dragons(){return Arrays.stream(values()).filter(ArmorSet::dragon).toList();}
    }
    private final NamespacedKey key,version;
    private static final int SCHEMA=2;
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
        if(set.dragon()){
            lore.add(Component.text(ArmorSet.DRAGON_TAG+set.setName,TextColor.color(set.tagColor)).decoration(TextDecoration.ITALIC,false));
            lore.add(Component.text(set.description(piece),TextColor.color(set.loreColor)).decoration(TextDecoration.ITALIC,false));
            for(String power:set.powers())lore.add(Component.text(ArmorSet.POWER_MARK+power,TextColor.color(set.tagColor)).decoration(TextDecoration.ITALIC,false));
            lore.add(Component.text(ArmorSet.POWER_MARK+ArmorSet.OVERCAP_LINE,net.kyori.adventure.text.format.NamedTextColor.GRAY).decoration(TextDecoration.ITALIC,false));
        }else lore.add(Component.text("Combat",net.kyori.adventure.text.format.NamedTextColor.GRAY).decoration(TextDecoration.ITALIC,false));
        if(meta.hasLore())for(Component line:meta.lore()){
            String text=plain.serialize(line);
            if(text.equals("Combat")||Arrays.asList(set.descriptions).contains(text)||text.startsWith(set.setName+" ·"))continue;
            if(set.dragon()&&(text.startsWith(ArmorSet.DRAGON_TAG)||text.equals(ArmorSet.DRAGON_POWER)||text.startsWith(ArmorSet.POWER_MARK)))continue;
            lore.add(line);
        }
        meta.lore(lore);meta.setUnbreakable(false);meta.removeItemFlags(ItemFlag.HIDE_UNBREAKABLE);
        var equipment=meta.getEquippable();equipment.setDamageOnHurt(true);meta.setEquippable(equipment);
        if(set.dragon()&&meta instanceof Damageable damage)damage.setMaxDamage(ArmorSet.DRAGON_DURABILITY[piece.ordinal()]);
        // Defining attributes replaces the native component, so copy its complete defaults first.
        if(!meta.hasAttributeModifiers())meta.setAttributeModifiers(piece.material.getDefaultAttributeModifiers(piece.slot));
        for(Attribute attribute:set.dragon()?List.of(Attribute.ARMOR,Attribute.ARMOR_TOUGHNESS,Attribute.KNOCKBACK_RESISTANCE):List.of(Attribute.ARMOR,Attribute.ARMOR_TOUGHNESS)){
            var defaults=piece.material.getDefaultAttributeModifiers(piece.slot).get(attribute);
            NamespacedKey own=new NamespacedKey("advance_magic",set.id()+"_"+piece.id()+"_"+attribute.getKey().getKey());
            // Re-migration replaces this set's own modifier and the native one; other plugins' modifiers (affixes) stay.
            var current=meta.getAttributeModifiers(attribute);
            if(current!=null)for(AttributeModifier modifier:new ArrayList<>(current))
                if(modifier.getKey().equals(own)||defaults.stream().anyMatch(d->d.getKey().equals(modifier.getKey())))meta.removeAttributeModifier(attribute,modifier);
            double amount=0;var operation=AttributeModifier.Operation.ADD_NUMBER;
            for(AttributeModifier original:defaults){amount+=original.getAmount();operation=original.getOperation();}
            if(set.dragon())amount=(attribute==Attribute.ARMOR?ArmorSet.DRAGON_ARMOR:attribute==Attribute.ARMOR_TOUGHNESS?ArmorSet.DRAGON_TOUGHNESS:ArmorSet.DRAGON_KNOCKBACK)[piece.ordinal()];
            else amount*=set.scale;
            meta.addAttributeModifier(attribute,new AttributeModifier(own,amount,operation,EquipmentSlotGroup.getByName(piece.slot.name().toLowerCase(Locale.ROOT))));
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
            +"|dragon> [helm|chestplate|leggings|boots|all] [player]";
    }

    /** /magic armor give <set|dragon> [piece|all] [player]; the set-less form gives Seraph pieces, dragon gives all three dragon sets. */
    public boolean command(CommandSender sender,String[] args,java.util.function.BiFunction<CommandSender,String,Player> resolve){
        if(!sender.hasPermission("advance-magic.admin")){sender.sendMessage(ChatColor.RED+"No permission.");return true;}
        if(args.length<3||args.length>5||!args[1].equalsIgnoreCase("give")){sender.sendMessage(ChatColor.YELLOW+usage());return true;}
        if(args[2].equalsIgnoreCase("dragon")){
            String pieceArg=args.length>3?args[3]:"all";
            Piece piece=Piece.parse(pieceArg);boolean all=pieceArg.equalsIgnoreCase("all");
            if(!all&&piece==null){sender.sendMessage(ChatColor.RED+"ไม่รู้จัก \""+pieceArg+"\" · ตัวอย่าง: /magic armor give dragon all");return true;}
            Player target=args.length>4?resolve.apply(sender,args[4]):sender instanceof Player p?p:null;
            if(target==null){sender.sendMessage(ChatColor.RED+"ไม่พบผู้เล่นที่ออนไลน์"+(args.length>4?": "+args[4]:""));return true;}
            for(ArmorSet dragon:ArmorSet.dragons())for(Piece entry:all?Piece.values():new Piece[]{piece}){
                var leftover=target.getInventory().addItem(create(dragon,entry));
                leftover.values().forEach(drop->target.getWorld().dropItemNaturally(target.getLocation(),drop));
            }
            sender.sendMessage(ChatColor.GOLD+"มอบชุดเกราะผู้พิชิตมังกรทั้ง 3 ชุด"+(all?" (12 ชิ้น)":"")+" ให้ "+target.getName());
            return true;
        }
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
        if(args.length==3){for(ArmorSet s:ArmorSet.values())values.add(s.id());values.add("dragon");}
        if(args.length==4){values.add("all");for(Piece p:Piece.values())values.add(p.id());}
        if(args.length==5){values.add("@s");for(Player p:Bukkit.getOnlinePlayers())values.add(p.getName());}
        String prefix=args[args.length-1].toLowerCase(Locale.ROOT);
        return values.stream().filter(v->v.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }
}
