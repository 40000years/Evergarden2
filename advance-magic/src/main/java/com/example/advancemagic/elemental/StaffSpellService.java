package com.example.advancemagic.elemental;

import com.example.advancemagic.AdvanceMagicPlugin;
import com.example.advancemagic.item.ElementalStaffService.Element;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import java.util.*;

/** Item-owned loadouts. Every mutation is synchronous and validates the original inventory slots. */
public final class StaffSpellService implements Listener {
    public enum Tier {
        APPRENTICE(3,ElementalSpell.Rarity.RARE), ARCANE(4,ElementalSpell.Rarity.EPIC), MYTHIC(5,ElementalSpell.Rarity.LEGENDARY),
        WOODEN(1,ElementalSpell.Rarity.RARE), REINFORCED_WOODEN(2,ElementalSpell.Rarity.RARE);
        public final int slots;
        public final ElementalSpell.Rarity maxRarity;
        Tier(int slots,ElementalSpell.Rarity maxRarity){this.slots=slots;this.maxRarity=maxRarity;}
        public int slots(){return slots;}
        public boolean accepts(ElementalSpell spell){return accepts(spell,SpellLevels.BASE);}
        public boolean accepts(ElementalSpell spell,int level){return SpellLevels.rarity(spell,level).ordinal()<=maxRarity.ordinal();}
        public boolean wooden(){return this==WOODEN||this==REINFORCED_WOODEN;}
        public Tier next(){return switch(this){case WOODEN->REINFORCED_WOODEN;case APPRENTICE->ARCANE;case ARCANE->MYTHIC;default->null;};}
        public static Tier parse(String input){
            if(input==null)return null;
            for(Tier tier:values())if(tier.name().equalsIgnoreCase(input))return tier;
            return null;
        }
    }
    public record Result(boolean success,String message) {
        static Result ok(String message){return new Result(true,message);}
        static Result fail(String message){return new Result(false,message);}
    }
    private static final NamespacedKey TIER=key("spell_staff_tier"), SPELLS=key("staff_spells"), SELECTED=key("staff_selected_spell"),
            SCROLL=key("spell_scroll"), VERSION=key("spell_item_version"), ID=key("spell_staff_id"),
            PROTECTED=key("staff_drop_protected"), DISCOVERED=key("spell_codex"), LEVEL=key("spell_level");
    private static final String LORE_MARK=ChatColor.DARK_GRAY+"[Spell] ";
    private final AdvanceMagicPlugin plugin;
    private final StaffGemService gems;
    public StaffSpellService(AdvanceMagicPlugin plugin){this.plugin=plugin;gems=new StaffGemService(plugin);}
    public StaffGemService gems(){return gems;}
    private static NamespacedKey key(String path){return new NamespacedKey("advance_magic",path);}
    private static void serverThread(){if(!Bukkit.isPrimaryThread())throw new IllegalStateException("Staff transactions require the server thread");}
    public Tier tier(ItemStack item){
        if(!plugin.staffs().isStaff(item))return null;
        String value=item.getItemMeta().getPersistentDataContainer().get(TIER,PersistentDataType.STRING);
        boolean wooden=plugin.staffs().isWooden(item);
        Tier tier=value==null?(wooden?Tier.WOODEN:Tier.APPRENTICE):Tier.parse(value);
        return tier!=null&&tier.wooden()==wooden?tier:null;
    }
    public List<ElementalSpell> spells(ItemStack item){return List.copyOf(levels(item).keySet());}
    /** Imprinted spells with their levels in slot order ("fireball" is level V, "fireball@7" level VII); empty when invalid. */
    public Map<ElementalSpell,Integer> levels(ItemStack item){
        Tier rank=tier(item);if(rank==null)return Map.of();
        String value=item.getItemMeta().getPersistentDataContainer().get(SPELLS,PersistentDataType.STRING);
        if(value==null||value.isBlank())return Map.of();
        Map<ElementalSpell,Integer> result=new LinkedHashMap<>();
        for(String entry:value.split(",",-1)){
            int at=entry.indexOf('@');ElementalSpell spell=ElementalSpell.parse(at<0?entry:entry.substring(0,at));int level=SpellLevels.BASE;
            if(at>=0)try{level=Integer.parseInt(entry.substring(at+1));}catch(NumberFormatException invalid){return Map.of();}
            if(spell==null||level!=SpellLevels.clamp(spell,level)||!rank.accepts(spell,level)||result.containsKey(spell))return Map.of();
            result.put(spell,level);
        }
        return result.size()>rank.slots?Map.of():Collections.unmodifiableMap(result);
    }
    public int level(ItemStack staff,ElementalSpell spell){return spell==null?SpellLevels.BASE:levels(staff).getOrDefault(spell,SpellLevels.BASE);}
    private boolean valid(ItemStack item){
        if(tier(item)==null||item.getAmount()!=1)return false;
        String raw=item.getItemMeta().getPersistentDataContainer().get(SPELLS,PersistentDataType.STRING);
        return raw==null||raw.isBlank()||!spells(item).isEmpty();
    }
    public int selectedIndex(ItemStack item){
        if(tier(item)==null)return 0;
        int index=item.getItemMeta().getPersistentDataContainer().getOrDefault(SELECTED,PersistentDataType.INTEGER,0);
        return Math.max(0,Math.min(index,Math.max(0,spells(item).size()-1)));
    }
    public ElementalSpell selected(ItemStack item){
        List<ElementalSpell> known=spells(item);return known.isEmpty()?null:known.get(selectedIndex(item));
    }
    public ItemStack createStaff(Element element,Tier rank){
        if(rank==null||rank.wooden())throw new IllegalArgumentException("Elemental staff requires an elemental tier");
        ItemStack item=plugin.staffs().create(Objects.requireNonNull(element));
        var meta=item.getItemMeta();
        meta.getPersistentDataContainer().set(ID,PersistentDataType.STRING,UUID.randomUUID().toString());
        item.setItemMeta(meta);write(item,Objects.requireNonNull(rank),List.of(),Map.of(),0);return item;
    }
    public ItemStack createWoodenStaff(){return createWoodenStaff(Tier.WOODEN);}
    public ItemStack createWoodenStaff(Tier rank){
        if(rank==null||!rank.wooden())throw new IllegalArgumentException("Wooden staff requires a wooden tier");
        ItemStack item=plugin.staffs().createWooden();write(item,rank,List.of(),Map.of(),0);return item;
    }
    /** Recipes clone their output; assign each physical staff its own ID on inventory migration. */
    public ItemStack createCraftingWoodenStaff(){
        ItemStack item=createWoodenStaff();var meta=item.getItemMeta();
        meta.getPersistentDataContainer().remove(ID);meta.getPersistentDataContainer().remove(VERSION);
        item.setItemMeta(meta);return item;
    }
    /** A cast snapshots this once, so swapping the held staff never changes a paid spell. */
    public double affinityMultiplier(ItemStack item,ElementalSpell spell){
        return spell!=null&&tier(item)!=null&&plugin.staffs().element(item)==spell.element()?1.5:.5;
    }
    public ItemStack createScroll(ElementalSpell spell){return createScroll(spell,SpellLevels.BASE);}
    public ItemStack createScroll(ElementalSpell spell,int level){
        Objects.requireNonNull(spell);level=SpellLevels.clamp(spell,level);ItemStack item=new ItemStack(Material.PAPER);var meta=item.getItemMeta();
        NamespacedKey model=new NamespacedKey("advance_magic","scroll_"+spell.id());
        var models=meta.getCustomModelDataComponent();models.setStrings(List.of(model.toString()));
        meta.setCustomModelDataComponent(models);meta.setItemModel(model);
        boolean leveled=SpellLevels.leveled(spell);
        meta.setDisplayName(spell.element().color+spell.title()+ChatColor.WHITE+" Scroll"+(leveled?rarityColor(spell,level)+" · "+SpellLevels.roman(level):""));
        List<String> lore=new ArrayList<>();
        lore.add(ChatColor.GRAY+spell.element().title+" · "+SpellLevels.rarity(spell,level)+(leveled?" · ระดับ "+SpellLevels.roman(level)+"/VII":" · Ultimate"));
        if(leveled)lore.add(stars(level));
        lore.add(ChatColor.AQUA+"Mana "+SpellLevels.mana(spell,level)+" · CD "+format1(SpellLevels.cooldown(spell,level))+"s"+(leveled?" · Damage ×"+format2(SpellLevels.damage(level)):""));
        lore.add(ChatColor.GRAY+spell.description());
        lore.add(ChatColor.YELLOW+"ถือคทา แล้วใช้ /spell (ใส่ข้ามธาตุได้)");
        lore.add(ChatColor.GRAY+"ธาตุตรง: Damage +50% · ต่างธาตุ/คทาไม้: −50%");
        if(leveled&&level<SpellLevels.MAX)lore.add(ChatColor.LIGHT_PURPLE+"2 แผ่นระดับเดียวกัน → ระดับ "+SpellLevels.roman(level+1)+" ที่ /spell fuse");
        lore.add(ChatColor.DARK_GRAY+"ประทับสำเร็จจะใช้คัมภีร์ 1 ชิ้น");
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(SCROLL,PersistentDataType.STRING,spell.id());
        meta.getPersistentDataContainer().set(VERSION,PersistentDataType.INTEGER,1);
        if(leveled)meta.getPersistentDataContainer().set(LEVEL,PersistentDataType.INTEGER,level);
        item.setItemMeta(meta);return item;
    }
    public static ChatColor rarityColor(ElementalSpell spell,int level){
        return switch(SpellLevels.rarity(spell,level)){case BASIC->ChatColor.WHITE;case RARE->ChatColor.AQUA;case EPIC->ChatColor.LIGHT_PURPLE;case LEGENDARY->ChatColor.GOLD;};
    }
    private static String stars(int level){
        StringBuilder out=new StringBuilder();
        for(int i=1;i<=SpellLevels.MAX;i++)out.append(i<=level?(i==SpellLevels.MAX?ChatColor.LIGHT_PURPLE:i>=SpellLevels.BASE?ChatColor.GOLD:ChatColor.YELLOW)+"★":ChatColor.DARK_GRAY+"☆");
        return out.toString();
    }
    /** Looks like the scroll but carries no scroll data, so a menu copy can never be imprinted. */
    public ItemStack scrollIcon(ElementalSpell spell){return scrollIcon(spell,SpellLevels.BASE);}
    public ItemStack scrollIcon(ElementalSpell spell,int level){
        ItemStack item=createScroll(spell,level);var meta=item.getItemMeta();var data=meta.getPersistentDataContainer();
        data.remove(SCROLL);data.remove(VERSION);data.remove(LEVEL);item.setItemMeta(meta);return item;
    }
    public ElementalSpell scroll(ItemStack item){
        if(item==null||item.getType()!=Material.PAPER||!item.hasItemMeta())return null;
        var data=item.getItemMeta().getPersistentDataContainer();
        if(data.getOrDefault(VERSION,PersistentDataType.INTEGER,0)!=1)return null;
        return ElementalSpell.parse(data.get(SCROLL,PersistentDataType.STRING));
    }
    /** The scroll's level; scrolls made before levels existed are level V. */
    public int scrollLevel(ItemStack item){
        ElementalSpell spell=scroll(item);if(spell==null)return SpellLevels.BASE;
        return SpellLevels.clamp(spell,item.getItemMeta().getPersistentDataContainer().getOrDefault(LEVEL,PersistentDataType.INTEGER,SpellLevels.BASE));
    }
    /** Rewrites scrolls made before levels (or with stale lore) as their current level-V or leveled form, so equal scrolls stack. */
    public void migrateScrolls(Inventory inventory){
        serverThread();
        for(int slot=0;slot<inventory.getSize();slot++){
            ItemStack item=inventory.getItem(slot);ElementalSpell spell=scroll(item);if(spell==null)continue;
            ItemStack canonical=createScroll(spell,scrollLevel(item));
            if(!canonical.isSimilar(item)){canonical.setAmount(item.getAmount());inventory.setItem(slot,canonical);}
        }
    }
    public boolean isScroll(ItemStack item){return scroll(item)!=null;}
    /** Adds only loadout metadata/lore; custom names, legacy data, enchantments and models survive. */
    public boolean migrate(ItemStack item){
        if(!valid(item))return false;
        var data=item.getItemMeta().getPersistentDataContainer();
        if(data.has(VERSION,PersistentDataType.INTEGER))return false;
        write(item,tier(item),spells(item),levels(item),selectedIndex(item));return true;
    }
    public void refresh(ItemStack item){if(valid(item))write(item,tier(item),spells(item),levels(item),selectedIndex(item));}
    public void migrate(Inventory inventory){serverThread();for(int slot=0;slot<inventory.getSize();slot++){ItemStack item=inventory.getItem(slot);if(migrate(item))inventory.setItem(slot,item);}}
    private void write(ItemStack item,Tier rank,List<ElementalSpell> known,Map<ElementalSpell,Integer> levels,int active){
        var meta=item.getItemMeta();var data=meta.getPersistentDataContainer();
        data.set(TIER,PersistentDataType.STRING,rank.name());data.set(VERSION,PersistentDataType.INTEGER,1);
        data.set(SPELLS,PersistentDataType.STRING,String.join(",",known.stream().map(spell->{
            int level=levels.getOrDefault(spell,SpellLevels.BASE);return level==SpellLevels.BASE?spell.id():spell.id()+"@"+level;}).toList()));
        data.set(SELECTED,PersistentDataType.INTEGER,Math.max(0,Math.min(active,Math.max(0,known.size()-1))));
        if(!data.has(ID,PersistentDataType.STRING))data.set(ID,PersistentDataType.STRING,UUID.randomUUID().toString());
        List<String> lore=new ArrayList<>();
        if(meta.hasLore())for(String line:Objects.requireNonNull(meta.getLore())){
            String plain=ChatColor.stripColor(line);
            if(!line.startsWith(LORE_MARK)&&!plain.contains("โมเดลตัวอย่าง")&&!plain.startsWith("วงจรธาตุ:"))lore.add(line);
        }
        lore.add(LORE_MARK+ChatColor.GOLD+rank.name()+" · "+known.size()+"/"+rank.slots+" slots");
        lore.add(LORE_MARK+ChatColor.GRAY+"รองรับสูงสุด "+rank.maxRarity);
        lore.add(LORE_MARK+ChatColor.GRAY+(rank.wooden()?"ไม่มีธาตุ · Damage ทุกเวท −50%":"ธาตุตรง +50% Damage · ต่างธาตุ −50% Damage"));
        var wands=plugin.wands();
        if(wands!=null)lore.add(LORE_MARK+ChatColor.AQUA+"ความทนทาน "+wands.usesLeft(item)+"/"+wands.maxUses(item));
        lore.add(LORE_MARK+ChatColor.GRAY+StaffGemService.summary(item));
        var sockets=StaffGemService.sockets(item);
        lore.add(LORE_MARK+ChatColor.LIGHT_PURPLE+"Gem 1: "+(sockets[0]==null?"ว่าง":sockets[0].title)
            +" · Gem 2: "+(sockets[1]==null?"ว่าง":sockets[1].title));
        for(int index=0;index<known.size();index++)lore.add(LORE_MARK+(index==active?ChatColor.YELLOW+"▶ ":ChatColor.GRAY+"  ")+(index+1)+". "+SpellLevels.label(known.get(index),levels.getOrDefault(known.get(index),SpellLevels.BASE))
                +(affinityMultiplier(item,known.get(index))>1?ChatColor.GREEN+" (+50%)":ChatColor.RED+" (−50%)"));
        if(known.isEmpty())lore.add(LORE_MARK+ChatColor.YELLOW+"คทาว่าง · ใช้คัมภีร์ด้วย /spell imprint");
        lore.add(LORE_MARK+ChatColor.GRAY+"คลิกขวา: ร่าย · ย่อ+คลิกขวา: เปลี่ยนเวท");
        lore.add(LORE_MARK+ChatColor.GRAY+"/spell · /staff upgrade · /spell extract");
        if(data.getOrDefault(PROTECTED,PersistentDataType.BYTE,(byte)0)==1)lore.add(LORE_MARK+ChatColor.GREEN+"ป้องกันการกดทิ้ง (ยังตกเมื่อตาย)");
        meta.setLore(lore);meta.setMaxStackSize(1);item.setItemMeta(meta);
    }
    public boolean select(Player player,int oneBased){
        serverThread();ItemStack held=player.getInventory().getItemInMainHand();List<ElementalSpell> known=spells(held);
        if(!valid(held)||oneBased<1||oneBased>known.size())return false;
        ItemStack updated=held.clone();write(updated,tier(held),known,levels(held),oneBased-1);
        player.getInventory().setItemInMainHand(updated);discover(player,known.get(oneBased-1));
        ElementalSpell chosen=known.get(oneBased-1);int level=level(held,chosen);
        player.sendActionBar(net.kyori.adventure.text.Component.text("["+oneBased+"/"+known.size()+"] "+SpellLevels.label(chosen,level)+" · "+SpellLevels.mana(chosen,level)+" MP"));
        player.playSound(player.getLocation(),Sound.BLOCK_AMETHYST_BLOCK_CHIME,.5f,1.5f);return true;
    }
    public boolean cycle(Player player){ItemStack held=player.getInventory().getItemInMainHand();return select(player,(selectedIndex(held)+1)%Math.max(1,spells(held).size())+1);}
    public boolean matches(Player player,int slot,ItemStack snapshot){return slot>=0&&slot<player.getInventory().getSize()&&Objects.equals(snapshot,player.getInventory().getItem(slot));}
    public Result imprint(Player player,int staffSlot,ItemStack staffSnapshot,int scrollSlot,ItemStack scrollSnapshot,int target){
        serverThread();
        if(staffSlot==scrollSlot||!matches(player,staffSlot,staffSnapshot)||!matches(player,scrollSlot,scrollSnapshot))return Result.fail("ไอเทมเปลี่ยนแล้ว กรุณาเปิดหน้าประทับใหม่");
        if(!valid(staffSnapshot))return Result.fail("คทานี้มีข้อมูลไม่ถูกต้อง");
        ElementalSpell spell=scroll(scrollSnapshot);Tier rank=tier(staffSnapshot);int level=scrollLevel(scrollSnapshot);
        if(spell==null||scrollSnapshot.getAmount()<1)return Result.fail("คัมภีร์ไม่ถูกต้อง");
        if(!rank.accepts(spell,level))return Result.fail("คทา "+rank+" ไม่รองรับ "+SpellLevels.rarity(spell,level)+" กรุณาอัปเกรดคทา");
        List<ElementalSpell> known=new ArrayList<>(spells(staffSnapshot));Map<ElementalSpell,Integer> levels=new HashMap<>(levels(staffSnapshot));
        if(known.contains(spell))return Result.fail("คทานี้มีเวทดังกล่าวแล้ว");
        if(target<0||target>=rank.slots||target>known.size())return Result.fail("ช่องเวทไม่ถูกต้อง");
        ElementalSpell replaced=target<known.size()?known.get(target):null;
        if(replaced==null)known.add(spell);else known.set(target,spell);
        levels.put(spell,level);
        ItemStack updated=staffSnapshot.clone();write(updated,rank,known,levels,selectedIndex(staffSnapshot));
        ItemStack remaining=scrollSnapshot.clone();remaining.setAmount(remaining.getAmount()-1);
        // No tasks, cursor items, GUI transfers or post-transaction writes are involved.
        player.getInventory().setItem(staffSlot,updated);player.getInventory().setItem(scrollSlot,remaining.getAmount()==0?null:remaining);
        discover(player,spell);
        plugin.getLogger().info("SPELL_IMPRINT player="+player.getUniqueId()+" staff="+updated.getItemMeta().getPersistentDataContainer().get(ID,PersistentDataType.STRING)+" spell="+spell.id()+" level="+level+" slot="+(target+1)+" replaced="+(replaced==null?"none":replaced.id()));
        return Result.ok("ประทับ "+SpellLevels.label(spell,level)+" ลงช่อง "+(target+1)+" แล้ว"+(replaced==null?"":" (เวทเดิมสูญหาย)"));
    }
    /** Replaces one imprinted spell with a scroll and hands the old spell back as a scroll, so nothing is lost. */
    public Result swap(Player player,int staffSlot,ItemStack staffSnapshot,int scrollSlot,ItemStack scrollSnapshot,int target){
        serverThread();
        if(staffSlot==scrollSlot||staffSlot!=player.getInventory().getHeldItemSlot()||!matches(player,staffSlot,staffSnapshot)||!matches(player,scrollSlot,scrollSnapshot))
            return Result.fail("ไอเทมเปลี่ยนแล้ว กรุณาลองใหม่");
        if(!valid(staffSnapshot))return Result.fail("คทานี้มีข้อมูลไม่ถูกต้อง");
        ElementalSpell spell=scroll(scrollSnapshot);Tier rank=tier(staffSnapshot);int level=scrollLevel(scrollSnapshot);
        if(spell==null||scrollSnapshot.getAmount()<1)return Result.fail("คัมภีร์ไม่ถูกต้อง");
        if(!rank.accepts(spell,level))return Result.fail("คทา "+rank+" ไม่รองรับ "+SpellLevels.rarity(spell,level)+" กรุณาอัปเกรดคทา");
        List<ElementalSpell> known=new ArrayList<>(spells(staffSnapshot));Map<ElementalSpell,Integer> levels=new HashMap<>(levels(staffSnapshot));
        if(target<0||target>=known.size())return Result.fail("เลือกเวทที่มีอยู่ในคทา");
        boolean raise=known.get(target)==spell;
        if(raise&&level<=levels.getOrDefault(spell,SpellLevels.BASE))return Result.fail("คทานี้มี "+SpellLevels.label(spell,levels.getOrDefault(spell,SpellLevels.BASE))+" อยู่แล้ว (ระดับเท่ากันหรือสูงกว่า)");
        if(!raise&&known.contains(spell))return Result.fail("คทานี้มีเวทดังกล่าวแล้ว");
        ElementalSpell old=known.get(target),active=selected(staffSnapshot);int oldLevel=levels.getOrDefault(old,SpellLevels.BASE);
        ItemStack returned=createScroll(old,oldLevel),remaining=scrollSnapshot.clone();remaining.setAmount(remaining.getAmount()-1);
        // The used scroll's slot counts as free when it empties.
        int destination=-1;ItemStack output=null;
        for(int pass=0;pass<2&&destination<0;pass++)for(int slot=0;slot<36;slot++){
            if(slot==staffSlot)continue;
            ItemStack stack=slot==scrollSlot?(remaining.getAmount()==0?null:remaining):player.getInventory().getItem(slot);
            if(pass==0&&stack!=null&&stack.isSimilar(returned)&&stack.getAmount()<Math.min(stack.getMaxStackSize(),player.getInventory().getMaxStackSize())){
                destination=slot;output=stack.clone();output.setAmount(stack.getAmount()+1);break;
            }
            if(pass==1&&(stack==null||stack.getType().isAir())){destination=slot;output=returned;break;}
        }
        if(destination<0)return Result.fail("กระเป๋าเต็ม กรุณาเว้นที่สำหรับคัมภีร์ที่ได้คืนก่อน");
        known.set(target,spell);levels.remove(old);levels.put(spell,level);
        int selected=old==active?target:known.indexOf(active);
        ItemStack updated=staffSnapshot.clone();write(updated,rank,known,levels,selected);
        player.getInventory().setItem(staffSlot,updated);
        player.getInventory().setItem(scrollSlot,remaining.getAmount()==0?null:remaining);
        player.getInventory().setItem(destination,output);
        discover(player,spell);
        plugin.getLogger().info("SPELL_SWAP player="+player.getUniqueId()+" staff="+updated.getItemMeta().getPersistentDataContainer().get(ID,PersistentDataType.STRING)+" slot="+(target+1)+" out="+old.id()+"@"+oldLevel+" in="+spell.id()+"@"+level);
        String before=SpellLevels.label(old,oldLevel),after=SpellLevels.label(spell,level);
        return Result.ok((raise?"อัประดับ "+before+" → "+after:"สลับ "+before+" → "+after)+" แล้ว · ได้คัมภีร์ "+before+" คืน");
    }
    public Result reorder(Player player,int first,int second){
        serverThread();ItemStack item=player.getInventory().getItemInMainHand();
        if(!valid(item))return Result.fail("ถือคทาในมือหลักก่อน");
        List<ElementalSpell> known=new ArrayList<>(spells(item));first--;second--;
        if(first<0||second<0||first>=known.size()||second>=known.size())return Result.fail("เลือกช่องเวทที่มีอยู่");
        ElementalSpell active=selected(item);Collections.swap(known,first,second);ItemStack updated=item.clone();
        write(updated,tier(item),known,levels(item),known.indexOf(active));player.getInventory().setItemInMainHand(updated);
        return Result.ok("สลับลำดับเวทแล้ว");
    }
    /** Reclaims one imprinted spell. Preflight both destinations before changing either item. */
    public Result extract(Player player,int staffSlot,ItemStack snapshot,int spellIndex){
        serverThread();
        if(staffSlot!=player.getInventory().getHeldItemSlot()||!matches(player,staffSlot,snapshot))
            return Result.fail("คทาเปลี่ยนแล้ว กรุณาเปิดหน้าถอนเวทใหม่");
        if(!valid(snapshot))return Result.fail("ถือคทาที่มีข้อมูลถูกต้องในมือหลักก่อน");
        List<ElementalSpell> known=new ArrayList<>(spells(snapshot));
        if(spellIndex<0||spellIndex>=known.size())return Result.fail("ช่องนี้ไม่มีเวทให้ถอน");
        ElementalSpell removed=known.get(spellIndex),active=selected(snapshot);int removedLevel=level(snapshot,removed);
        ItemStack scroll=createScroll(removed,removedLevel);int destination=-1;
        ItemStack output=null;
        for(int slot=0;slot<36;slot++){
            if(slot==staffSlot)continue;ItemStack stack=player.getInventory().getItem(slot);
            if(stack!=null&&stack.isSimilar(scroll)&&stack.getAmount()<Math.min(stack.getMaxStackSize(),player.getInventory().getMaxStackSize())){
                destination=slot;output=stack.clone();output.setAmount(stack.getAmount()+1);break;
            }
        }
        if(destination<0)for(int slot=0;slot<36;slot++){
            if(slot==staffSlot)continue;ItemStack stack=player.getInventory().getItem(slot);
            if(stack==null||stack.getType().isAir()){destination=slot;output=scroll;break;}
        }
        if(destination<0)return Result.fail("กระเป๋าเต็ม กรุณาเว้นที่สำหรับคัมภีร์ก่อนถอนเวท");
        known.remove(spellIndex);
        int selected=known.indexOf(active);if(selected<0)selected=Math.min(spellIndex,Math.max(0,known.size()-1));
        ItemStack updated=snapshot.clone();write(updated,tier(snapshot),known,levels(snapshot),selected);
        player.getInventory().setItem(staffSlot,updated);player.getInventory().setItem(destination,output);
        plugin.getLogger().info("SPELL_EXTRACT player="+player.getUniqueId()+" staff="+updated.getItemMeta().getPersistentDataContainer().get(ID,PersistentDataType.STRING)+" spell="+removed.id()+"@"+removedLevel+" slot="+(spellIndex+1));
        return Result.ok("ถอน "+SpellLevels.label(removed,removedLevel)+" คืนเป็นคัมภีร์ 1 ชิ้นแล้ว");
    }
    public Result protect(Player player){
        serverThread();ItemStack item=player.getInventory().getItemInMainHand();if(!valid(item))return Result.fail("ถือคทาในมือหลักก่อน");
        ItemStack updated=item.clone();var meta=updated.getItemMeta();var data=meta.getPersistentDataContainer();
        boolean enable=data.getOrDefault(PROTECTED,PersistentDataType.BYTE,(byte)0)!=1;
        data.set(PROTECTED,PersistentDataType.BYTE,(byte)(enable?1:0));updated.setItemMeta(meta);refresh(updated);player.getInventory().setItemInMainHand(updated);
        return Result.ok(enable?"เปิดป้องกันการกดทิ้งคทาแล้ว (ยังตกเมื่อตาย)":"ปิดป้องกันการกดทิ้งคทาแล้ว");
    }
    public Map<Material,Integer> upgradeCost(Tier current){
        return switch(current){case WOODEN->Map.of(Material.AMETHYST_SHARD,4,Material.BLAZE_ROD,2);case APPRENTICE->Map.of(Material.DIAMOND,8,Material.AMETHYST_SHARD,16);case ARCANE->Map.of(Material.NETHERITE_INGOT,1,Material.NETHER_STAR,1);default->Map.of();};
    }
    public String upgradeDescription(Tier current){return switch(current){case WOODEN->"4 Amethyst Shard + 2 Blaze Rod → REINFORCED_WOODEN (2 slots, Rare)";case APPRENTICE->"8 Diamond + 16 Amethyst Shard → ARCANE (4 slots)";case ARCANE->"1 Netherite Ingot + 1 Nether Star → MYTHIC (5 slots)";default->"คทาระดับสูงสุดแล้ว";};}
    public Result upgrade(Player player){
        serverThread();int staffSlot=player.getInventory().getHeldItemSlot();ItemStack original=player.getInventory().getItem(staffSlot);
        if(!valid(original))return Result.fail("ถือคทาในมือหลักก่อน");Tier current=tier(original);
        if(current.next()==null)return Result.fail(current.wooden()?"คทาไม้สูงสุด 2 ช่อง และรองรับสูงสุด Rare":"คทาระดับสูงสุดแล้ว");
        Map<Integer,ItemStack> before=new LinkedHashMap<>(),after=new LinkedHashMap<>();
        for(var cost:upgradeCost(current).entrySet()){
            int needed=cost.getValue();ItemStack plain=new ItemStack(cost.getKey());
            for(int slot=0;slot<36&&needed>0;slot++){
                if(slot==staffSlot)continue;ItemStack stack=player.getInventory().getItem(slot);
                if(stack==null||!plain.isSimilar(stack))continue;
                int take=Math.min(needed,stack.getAmount());needed-=take;
                before.put(slot,stack.clone());ItemStack remaining=stack.clone();remaining.setAmount(stack.getAmount()-take);after.put(slot,remaining.getAmount()==0?null:remaining);
            }
            if(needed>0)return Result.fail("วัตถุดิบไม่พอ: "+upgradeDescription(current));
        }
        if(!Objects.equals(original,player.getInventory().getItem(staffSlot)))return Result.fail("คทาเปลี่ยนแล้ว");
        for(var entry:before.entrySet())if(!matches(player,entry.getKey(),entry.getValue()))return Result.fail("วัตถุดิบเปลี่ยนแล้ว");
        ItemStack upgraded=original.clone();Tier next=current.next();write(upgraded,next,spells(original),levels(original),selectedIndex(original));
        after.forEach((slot,item)->player.getInventory().setItem(slot,item));player.getInventory().setItem(staffSlot,upgraded);
        plugin.getLogger().info("STAFF_UPGRADE player="+player.getUniqueId()+" from="+current+" to="+next);
        return Result.ok("อัปเกรดเป็น "+next+" แล้ว เวทเดิมอยู่ครบ");
    }
    /** Administrative tier changes cannot downgrade and never replace the item. */
    public Result upgrade(Player player,Tier target){
        serverThread();ItemStack item=player.getInventory().getItemInMainHand();
        if(!valid(item)||target==null)return Result.fail("คทาหรือระดับไม่ถูกต้อง");
        Tier current=tier(item);
        if(current.wooden()!=target.wooden())return Result.fail("คทาไม้และคทาธาตุเป็นคนละสายอัปเกรด");
        boolean reachable=false;for(Tier next=current.next();next!=null;next=next.next())if(next==target){reachable=true;break;}
        if(!reachable)return Result.fail("ระดับใหม่ต้องสูงกว่าเดิมในสายคทาเดียวกัน");
        ItemStack updated=item.clone();write(updated,target,spells(item),levels(item),selectedIndex(item));player.getInventory().setItemInMainHand(updated);
        plugin.getLogger().info("STAFF_ADMIN_UPGRADE player="+player.getUniqueId()+" to="+target);return Result.ok("อัปเกรดคทาเป็น "+target+" แล้ว");
    }
    /**
     * Fuses pairs of equal scrolls (same spell, same level) from the player's 36 storage slots into the
     * next level, up to {@code times} pairs. Preflights the whole inventory before changing any slot.
     */
    public Result fuse(Player player,ElementalSpell spell,int level,int times){
        serverThread();
        if(spell==null||!SpellLevels.leveled(spell))return Result.fail("คัมภีร์ Ultimate รวมระดับไม่ได้");
        if(level<SpellLevels.MIN||level>=SpellLevels.MAX)return Result.fail("ระดับสูงสุดแล้ว");
        ItemStack[] slots=new ItemStack[36];int have=0;
        for(int slot=0;slot<36;slot++){
            ItemStack stack=player.getInventory().getItem(slot);slots[slot]=stack==null?null:stack.clone();
            if(scroll(stack)==spell&&scrollLevel(stack)==level)have+=stack.getAmount();
        }
        int pairs=Math.min(Math.max(1,times),have/2);
        if(pairs<1)return Result.fail("ต้องมี "+SpellLevels.label(spell,level)+" อย่างน้อย 2 แผ่น");
        int take=pairs*2;
        for(int slot=0;slot<36&&take>0;slot++){
            ItemStack stack=slots[slot];if(scroll(stack)!=spell||scrollLevel(stack)!=level)continue;
            int used=Math.min(take,stack.getAmount());take-=used;stack.setAmount(stack.getAmount()-used);if(stack.getAmount()==0)slots[slot]=null;
        }
        ItemStack result=createScroll(spell,level+1);int give=pairs,limit=Math.min(result.getMaxStackSize(),player.getInventory().getMaxStackSize());
        for(int pass=0;pass<2&&give>0;pass++)for(int slot=0;slot<36&&give>0;slot++){
            ItemStack stack=slots[slot];
            if(pass==0&&stack!=null&&stack.isSimilar(result)&&stack.getAmount()<limit){int add=Math.min(give,limit-stack.getAmount());stack.setAmount(stack.getAmount()+add);give-=add;}
            if(pass==1&&(stack==null||stack.getType().isAir())){ItemStack placed=result.clone();placed.setAmount(Math.min(give,limit));slots[slot]=placed;give-=placed.getAmount();}
        }
        if(give>0)return Result.fail("กระเป๋าเต็ม กรุณาเว้นที่สำหรับคัมภีร์ใหม่");
        for(int slot=0;slot<36;slot++)if(!Objects.equals(slots[slot],player.getInventory().getItem(slot)))player.getInventory().setItem(slot,slots[slot]);
        discover(player,spell);
        plugin.getLogger().info("SPELL_FUSE player="+player.getUniqueId()+" spell="+spell.id()+" from="+level+" to="+(level+1)+" pairs="+pairs);
        return Result.ok("รวม "+SpellLevels.label(spell,level)+" ×"+(pairs*2)+" → "+SpellLevels.label(spell,level+1)+" ×"+pairs+" แล้ว");
    }
    public Set<ElementalSpell> discovered(Player player){
        String raw=player.getPersistentDataContainer().getOrDefault(DISCOVERED,PersistentDataType.STRING,"");
        Set<ElementalSpell> result=EnumSet.noneOf(ElementalSpell.class);
        for(String id:raw.split(",")){ElementalSpell spell=ElementalSpell.parse(id);if(spell!=null)result.add(spell);}return result;
    }
    public void discover(Player player,ElementalSpell spell){
        Set<ElementalSpell> known=discovered(player);if(spell==null||!known.add(spell))return;
        player.getPersistentDataContainer().set(DISCOVERED,PersistentDataType.STRING,String.join(",",known.stream().map(ElementalSpell::id).toList()));
    }
    public void discover(Player player){migrateScrolls(player.getInventory());for(ItemStack item:player.getInventory().getContents()){discover(player,scroll(item));for(ElementalSpell spell:spells(item))discover(player,spell);}}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void dropping(PlayerDropItemEvent event){
        ItemStack item=event.getItemDrop().getItemStack();
        if(tier(item)!=null&&item.getItemMeta().getPersistentDataContainer().getOrDefault(PROTECTED,PersistentDataType.BYTE,(byte)0)==1){event.setCancelled(true);event.getPlayer().sendMessage(ChatColor.YELLOW+"คทาป้องกันการทิ้งอยู่ ใช้ /staff protect เพื่อปิด");}
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void pickup(EntityPickupItemEvent event){if(event.getEntity() instanceof Player player){ItemStack item=event.getItem().getItemStack();discover(player,scroll(item));for(ElementalSpell spell:spells(item))discover(player,spell);}}
    @EventHandler(priority=EventPriority.MONITOR) public void joined(PlayerJoinEvent event){discover(event.getPlayer());}
    @EventHandler public void craft(PrepareItemCraftEvent event){for(ItemStack item:event.getInventory().getMatrix())if(isScroll(item)){event.getInventory().setResult(null);return;}}
    public static String format(double value){return value==Math.floor(value)?Long.toString((long)value):Double.toString(value);}
    /** One decimal, without a trailing ".0". */
    public static String format1(double value){String text=String.format(Locale.ROOT,"%.1f",value);return text.endsWith(".0")?text.substring(0,text.length()-2):text;}
    public static String format2(double value){String text=String.format(Locale.ROOT,"%.2f",value);return text.endsWith(".00")?text.substring(0,text.length()-3):text;}
}
