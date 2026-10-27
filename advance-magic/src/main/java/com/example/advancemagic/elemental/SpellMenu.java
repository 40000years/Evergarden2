package com.example.advancemagic.elemental;

import com.example.advancemagic.AdvanceMagicPlugin;
import com.example.advancemagic.item.ElementalStaffService.Element;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import java.util.*;

/**
 * One-screen staff workbench: the held staff's spells, the scrolls already in the player's inventory
 * and every action as a button. A scroll fills the first empty slot; on a full staff it swaps with the
 * spell clicked next and the old spell comes back as a scroll, so nothing here destroys a spell.
 * The real staff and scrolls never leave their player slots.
 */
public final class SpellMenu implements Listener {
    private static final int HELP=0,FUSE=2,PROTECT=4,TREE=5,UPGRADE=6,CODEX=7,CLOSE=8,STAFF=9,GEM_A=18,GEM_B=27,SPELL=11,EXTRACT=20,MOVE=29,SCROLLS=36,PAGE=45;
    /** Fusion altar, read bottom to top: two equal scrolls, the fuse button, the next level. */
    private static final int F_BACK=0,F_HELP=1,F_TITLE=4,F_CLOSE=8,F_RESULT_BADGE=12,F_RESULT=13,F_BUTTON=22,F_COUNT=23,
            F_INPUT_BADGE=29,F_INPUT_A=30,F_PLUS=31,F_INPUT_B=32,F_LIST=36,F_PAGE=45;
    private static final int[] SCROLL_SLOTS={37,38,39,40,41,42,43,44,46,47,48,49,50,51,52,53};
    private static final class Workbench implements InventoryHolder {
        final UUID owner;final int staffSlot;final Inventory inventory;
        /** Menu slot → player inventory slot of the scroll shown there. */
        final Map<Integer,Integer> scrolls=new HashMap<>();
        int armed=-1,armedGem=-1,page;long upgradeArmed;
        ItemStack gemSnapshot;
        final String staffId;
        Workbench(Player player){
            owner=player.getUniqueId();staffSlot=player.getInventory().getHeldItemSlot();
            staffId=player.getInventory().getItem(staffSlot).getItemMeta().getPersistentDataContainer()
                .get(new NamespacedKey("advance_magic","spell_staff_id"),org.bukkit.persistence.PersistentDataType.STRING);
            inventory=Bukkit.createInventory(this,54,"คทาเวท · คลิกคัมภีร์ด้านล่างเพื่อใส่");
        }
        @Override public Inventory getInventory(){return inventory;}
    }
    private record Group(ElementalSpell spell,int level,int count){
        boolean fusable(){return count>=2&&level<SpellLevels.MAX;}
    }
    private static final class Fusion implements InventoryHolder {
        final UUID owner;final Inventory inventory;
        final Map<Integer,Group> entries=new HashMap<>();
        Group selected;int page;
        Fusion(Player player){owner=player.getUniqueId();inventory=Bukkit.createInventory(this,54,"แท่นหลอมคัมภีร์ · Scroll Fusion");}
        @Override public Inventory getInventory(){return inventory;}
    }
    private static final class Codex implements InventoryHolder {
        final UUID owner;final Inventory inventory;
        Codex(Player player){owner=player.getUniqueId();inventory=Bukkit.createInventory(this,54,"Arcane Codex");}
        @Override public Inventory getInventory(){return inventory;}
    }
    private final AdvanceMagicPlugin plugin;
    public SpellMenu(AdvanceMagicPlugin plugin){this.plugin=plugin;}
    private StaffSpellService service(){return plugin.staffSpells();}

    // ---- entry points ----
    public void open(Player player){
        service().discover(player);
        if(service().tier(player.getInventory().getItemInMainHand())==null){
            // Holding a hotbar staff is what this menu is for, so pick it up instead of explaining.
            for(int slot=0;slot<9;slot++)if(service().tier(player.getInventory().getItem(slot))!=null){
                player.getInventory().setHeldItemSlot(slot);
                player.sendActionBar(net.kyori.adventure.text.Component.text("ถือคทาจากช่อง "+(slot+1)+" ให้แล้ว"));break;
            }
        }
        if(service().tier(player.getInventory().getItemInMainHand())==null){
            player.sendMessage(ChatColor.YELLOW+"ถือคทาไว้ในมือ (หรือในแถบด่วน) แล้วพิมพ์ /spell อีกครั้ง · เปิดสมุดรวมเวทให้แทน");
            codex(player,Element.FIRE);return;
        }
        Workbench workbench=new Workbench(player);draw(player,workbench);player.openInventory(workbench.inventory);
    }
    public void imprint(Player player){open(player);}
    /** Scroll fusion works without a staff in hand. */
    public void fusion(Player player){
        service().discover(player);Fusion view=new Fusion(player);drawFusion(player,view);player.openInventory(view.inventory);
    }
    public void extract(Player player){open(player);}
    /** A command slot is one-based; extracting is free and returns the scroll, so it needs no confirmation. */
    public void extract(Player player,int oneBased){
        ItemStack staff=player.getInventory().getItemInMainHand();
        if(service().tier(staff)==null){player.sendMessage(ChatColor.RED+"ถือคทาในมือหลักก่อน แล้วใช้ /spell extract [ช่อง]");return;}
        if(oneBased<1||oneBased>service().spells(staff).size()){player.sendMessage(ChatColor.RED+"เลือกช่องเวทที่มีอยู่: /spell extract 1-"+Math.max(1,service().spells(staff).size()));return;}
        message(player,service().extract(player,player.getInventory().getHeldItemSlot(),staff.clone(),oneBased-1));
    }

    // ---- workbench ----
    private static ItemStack gui(String model,String name,List<String> lore){
        ItemStack item=new ItemStack(Material.PAPER);var meta=item.getItemMeta();
        meta.setItemModel(new NamespacedKey("advance_magic",model));
        if(name==null){meta.setDisplayName(" ");meta.setHideTooltip(true);}
        else{meta.setDisplayName(name);meta.setLore(lore.stream().map(line->line.startsWith("§")?line:ChatColor.GRAY+line).toList());}
        meta.addItemFlags(ItemFlag.values());item.setItemMeta(meta);return item;
    }
    private static ItemStack glint(ItemStack item){var meta=item.getItemMeta();meta.setEnchantmentGlintOverride(true);item.setItemMeta(meta);return item;}
    private boolean holding(Player player,Workbench wb){
        ItemStack staff=player.getInventory().getItem(wb.staffSlot);
        return player.getInventory().getHeldItemSlot()==wb.staffSlot&&service().tier(staff)!=null
            &&Objects.equals(wb.staffId,staff.getItemMeta().getPersistentDataContainer().get(new NamespacedKey("advance_magic","spell_staff_id"),org.bukkit.persistence.PersistentDataType.STRING));
    }
    /** Why a scroll cannot go on this staff, or null. */
    private String blocked(ItemStack staff,ElementalSpell spell,int level){
        StaffSpellService.Tier tier=service().tier(staff);
        if(!tier.accepts(spell,level))return "คทา "+tier+" รองรับสูงสุด "+tier.maxRarity+" · อัปเกรดคทาก่อน";
        if(service().spells(staff).contains(spell)&&service().level(staff,spell)>=level)return "คทานี้มี "+SpellLevels.label(spell,service().level(staff,spell))+" อยู่แล้ว";
        return null;
    }
    private String blocked(ItemStack staff,ItemStack scroll){return blocked(staff,service().scroll(scroll),service().scrollLevel(scroll));}
    private void draw(Player player,Workbench wb){
        Inventory inv=wb.inventory;
        for(int i=0;i<54;i++)inv.setItem(i,gui("skilltree_panel",null,null));
        ItemStack staff=player.getInventory().getItem(wb.staffSlot);StaffSpellService.Tier tier=service().tier(staff);
        if(tier==null)return;
        List<ElementalSpell> spells=service().spells(staff);int selected=service().selectedIndex(staff);
        ItemStack armedScroll=wb.armed<0?null:player.getInventory().getItem(wb.armed);ElementalSpell armed=service().scroll(armedScroll);
        if(armed==null)wb.armed=-1;
        if(wb.armedGem>=0&&!Objects.equals(wb.gemSnapshot,player.getInventory().getItem(wb.armedGem))){wb.armedGem=-1;wb.gemSnapshot=null;}

        inv.setItem(HELP,gui("skilltree_help",ChatColor.AQUA+"วิธีใช้",List.of(
            "§f1. คัมภีร์ในกระเป๋าอยู่แถวล่าง · คลิกเพื่อใส่ลงคทา",
            "§f2. คลิกเวทในคทา = เลือกเป็นเวทที่จะร่าย",
            "§f3. ปุ่มส้มใต้เวท = ถอดออกเป็นคัมภีร์ (ไม่เสียอะไร)",
            "§f4. ปุ่มฟ้า = เลื่อนเวทไปทางซ้าย 1 ช่อง",
            "§f5. คทาเต็ม: คลิกคัมภีร์ แล้วคลิกเวทที่จะสลับออก",
            "   เวทเดิมกลับเป็นคัมภีร์ให้อัตโนมัติ","",
            "ธาตุตรงกับคทา ดาเมจ +50% · ต่างธาตุ −50%",
            "ถือคทา: คลิกขวา = ร่าย · ย่อ+คลิกขวา = เปลี่ยนเวท","",
            "§dเวทมีระดับ I–VII · คัมภีร์ระดับสูงกว่าของเวทเดิม = อัประดับ",
            "§dรวมคัมภีร์ 2 แผ่นเป็นระดับถัดไปที่ปุ่มแท่นหลอม","",
            "§bGem 2 ช่อง: คลิก Gem แถวล่าง แล้วเลือกช่องทางซ้าย",
            "§bคลิกช่องที่ใส่แล้วเพื่อถอด · สลับแล้วได้เม็ดเดิมคืน",
            "§7Damage +15% / Cooldown −15% / Max +250 ต่อเม็ด",
            "§7Max เพิ่มเพดานเท่านั้น · ซ่อมเพื่อเติมจำนวนครั้ง")));
        inv.setItem(FUSE,gui("menu_swap",ChatColor.LIGHT_PURPLE+"แท่นหลอมคัมภีร์",List.of("รวมคัมภีร์ 2 แผ่นระดับเดียวกัน","→ 1 แผ่นระดับถัดไป (สูงสุด VII)","คลิกเพื่อเปิด · /spell fuse")));
        boolean guarded=staff.getItemMeta().getPersistentDataContainer().getOrDefault(new NamespacedKey("advance_magic","staff_drop_protected"),org.bukkit.persistence.PersistentDataType.BYTE,(byte)0)==1;
        inv.setItem(PROTECT,gui(guarded?"menu_shield_on":"menu_shield_off",(guarded?ChatColor.GREEN+"ป้องกันการกดทิ้ง: เปิด":ChatColor.GRAY+"ป้องกันการกดทิ้ง: ปิด"),
            List.of("กด Q ทิ้งคทาไม่ได้เมื่อเปิด","ยังตกเมื่อตายตามกฎเซิร์ฟเวอร์","คลิกเพื่อสลับ")));
        inv.setItem(TREE,gui("skilltree_tab_core_on",ChatColor.GOLD+"Skill Tree",List.of("พัฒนาพลังเวท มานา และความเชี่ยวชาญธาตุ","คลิกเพื่อเปิด")));
        inv.setItem(UPGRADE,upgradeButton(player,wb,tier));
        inv.setItem(CODEX,gui("menu_codex",ChatColor.LIGHT_PURPLE+"สมุดรวมเวท (Codex)",List.of(service().discovered(player).size()+" / "+ElementalSpell.values().length+" เวทที่เคยพบ","คลิกเพื่อเปิด")));
        inv.setItem(CLOSE,gui("skilltree_close",ChatColor.RED+"ปิด",List.of()));

        inv.setItem(STAFF,staff.clone());
        drawGem(player,wb,staff,GEM_A,0);
        drawGem(player,wb,staff,GEM_B,1);
        for(int i=0;i<5;i++){
            if(i<spells.size()){
                ElementalSpell spell=spells.get(i);boolean active=i==selected;int level=service().level(staff,spell);String label=SpellLevels.label(spell,level);
                List<String> lore=new ArrayList<>();
                lore.add(active?"§e▶ ช่อง "+(i+1)+" · เวทที่ใช้อยู่":"§7ช่อง "+(i+1)+" · §aคลิกเพื่อใช้เวทนี้");
                lore.addAll(details(staff,spell,level));
                if(armed!=null)lore.addAll(List.of("","§d⇄ คลิกเพื่อสลับกับ "+SpellLevels.label(armed,service().scrollLevel(armedScroll)),"§7ได้คัมภีร์ "+label+" คืน"));
                ItemStack icon=scrollIcon(spell,level,spell.element().color+(active?"▶ ":"")+label,lore);
                inv.setItem(SPELL+i,active?glint(icon):icon);
                inv.setItem(EXTRACT+i,gui("menu_extract",ChatColor.GOLD+"ถอด "+label,List.of("ได้คัมภีร์คืน 1 ชิ้น · ใส่กลับเมื่อไรก็ได้","ช่องที่เหลือเลื่อนมาต่อกัน","ต้องมีที่ว่างในกระเป๋า")));
                if(i>0)inv.setItem(MOVE+i,gui("menu_move_left",ChatColor.AQUA+"เลื่อน "+spell.title()+" ไปช่อง "+i,List.of("สลับที่กับ "+spells.get(i-1).title(),"ลำดับนี้ใช้ตอนย่อ+คลิกขวาเปลี่ยนเวท")));
            }else if(i<tier.slots)inv.setItem(SPELL+i,gui("menu_socket",ChatColor.GRAY+"ช่อง "+(i+1)+" · ว่าง",List.of("คลิกคัมภีร์แถวล่างเพื่อใส่เวท")));
            else inv.setItem(SPELL+i,gui("skilltree_lock",ChatColor.GRAY+"ช่อง "+(i+1)+" · ยังไม่ปลดล็อก",List.of(tier.next()==null?"คทานี้มีได้ "+tier.slots+" ช่อง":"อัปเกรดคทา (ปุ่มเขียวด้านบน) เพื่อเพิ่มช่อง")));
        }

        List<Integer> found=new ArrayList<>();
        for(int slot=0;slot<36;slot++)if(slot!=wb.staffSlot&&(service().isScroll(player.getInventory().getItem(slot))||StaffGemService.identify(player.getInventory().getItem(slot))!=null))found.add(slot);
        // Usable scrolls first, inventory order otherwise.
        found.sort(Comparator.comparing(slot->{var item=player.getInventory().getItem(slot);return StaffGemService.identify(item)==null&&blocked(staff,item)!=null;}));
        int pages=Math.max(1,(found.size()+SCROLL_SLOTS.length-1)/SCROLL_SLOTS.length);wb.page=Math.floorMod(wb.page,pages);
        inv.setItem(SCROLLS,gui("menu_label_scrolls",ChatColor.GOLD+"คัมภีร์ / Gem ในกระเป๋า",List.of(found.size()+" กอง · คลิกเพื่อเลือก","คลิกของในกระเป๋าด้านล่างโดยตรงก็ได้")));
        wb.scrolls.clear();
        for(int k=0;k<SCROLL_SLOTS.length;k++){
            int index=wb.page*SCROLL_SLOTS.length+k;if(index>=found.size())break;
            int slot=found.get(index);wb.scrolls.put(SCROLL_SLOTS[k],slot);
            inv.setItem(SCROLL_SLOTS[k],StaffGemService.identify(player.getInventory().getItem(slot))!=null
                ?gemEntry(player,wb,slot):scrollEntry(player,wb,staff,slot,spells.size()<tier.slots));
        }
        if(found.isEmpty())inv.setItem(SCROLL_SLOTS[3],gui("menu_socket",ChatColor.GRAY+"ไม่มีคัมภีร์ในกระเป๋า",List.of("หาคัมภีร์จากหีบโครงสร้าง Evergarden","หรือถอดเวทจากคทาเพื่อรับคัมภีร์คืน")));
        if(pages>1)inv.setItem(PAGE,gui("menu_page",ChatColor.YELLOW+"หน้า "+(wb.page+1)+"/"+pages,List.of("คลิกเพื่อดูคัมภีร์หน้าถัดไป")));
    }
    private void drawGem(Player player,Workbench wb,ItemStack staff,int menuSlot,int socket){
        var gem=StaffGemService.sockets(staff)[socket];
        var chosen=wb.armedGem<0?null:StaffGemService.identify(wb.gemSnapshot);
        List<String> lore=new ArrayList<>();
        if(gem!=null)lore.add("§f"+gem.effect);
        lore.add("§7"+StaffGemService.summary(staff));
        if(chosen!=null){lore.add("§aคลิกเพื่อใส่ "+chosen.title);if(gem!=null)lore.add("§7ได้ "+gem.title+" คืนกระเป๋า");}
        else lore.add(gem==null?"§bคลิก Gem ในกระเป๋า แล้วเลือกช่องนี้":"§eคลิกเพื่อถอด Gem คืนกระเป๋า");
        lore.add("§7Damage / Cooldown / Max · ใส่ซ้ำหรือผสมได้");
        if(gem==StaffGemService.Gem.MAX||chosen==StaffGemService.Gem.MAX)lore.add("§7ถอด Max จะลดค่าที่เหลือให้ไม่เกินเพดานใหม่");
        ItemStack icon=gui(gem==null?"menu_socket":gem.model,(gem==null?ChatColor.GRAY:ChatColor.LIGHT_PURPLE)+"Gem ช่อง "+(socket+1)+" · "+(gem==null?"ว่าง":gem.title),lore);
        if(gem!=null)icon.setType(gem.material);
        wb.inventory.setItem(menuSlot,chosen==null?icon:glint(icon));
    }
    private ItemStack gemEntry(Player player,Workbench wb,int slot){
        ItemStack stack=player.getInventory().getItem(slot);var gem=StaffGemService.identify(stack);
        boolean armed=wb.armedGem==slot;
        ItemStack icon=StaffGemService.create(gem);var meta=icon.getItemMeta();
        meta.getPersistentDataContainer().remove(new NamespacedKey("advance_magic","staff_gem"));
        meta.setDisplayName((armed?ChatColor.LIGHT_PURPLE+"✦ ":ChatColor.AQUA.toString())+gem.title);
        meta.setLore(List.of(ChatColor.GRAY+gem.effect,armed?ChatColor.YELLOW+"เลือกแล้ว · คลิก Gem ช่อง 1 หรือ 2 ทางซ้าย":ChatColor.GREEN+"คลิกเพื่อเลือก แล้วเลือกช่อง Gem ทางซ้าย",
            ChatColor.GRAY+"คลิกเม็ดที่เลือกอีกครั้งเพื่อยกเลิก",ChatColor.DARK_GRAY+"มี "+stack.getAmount()+" เม็ดในกระเป๋า"));
        icon.setItemMeta(meta);icon.setAmount(Math.min(64,stack.getAmount()));return armed?glint(icon):icon;
    }
    private void itemClicked(Player player,Workbench wb,int slot){
        ItemStack item=player.getInventory().getItem(slot);
        var gem=StaffGemService.identify(item);
        if(gem==null){wb.armedGem=-1;wb.gemSnapshot=null;scrollClicked(player,wb,slot);return;}
        wb.armed=-1;
        if(wb.armedGem==slot){wb.armedGem=-1;wb.gemSnapshot=null;hint(player,"ยกเลิกการเลือก Gem");return;}
        wb.armedGem=slot;wb.gemSnapshot=item.clone();hint(player,"เลือก "+gem.title+" แล้ว · คลิกช่อง Gem ทางซ้าย");
    }
    private void gemClicked(Player player,Workbench wb,int socket){
        ItemStack staff=player.getInventory().getItem(wb.staffSlot);
        StaffSpellService.Result result=wb.armedGem<0?service().gems().remove(player,wb.staffSlot,staff.clone(),socket)
            :service().gems().insert(player,wb.staffSlot,staff.clone(),wb.armedGem,wb.gemSnapshot,socket);
        wb.armedGem=-1;wb.gemSnapshot=null;done(player,result);
    }
    private List<String> details(ItemStack staff,ElementalSpell spell,int level){
        boolean matching=service().affinityMultiplier(staff,spell)>1;
        return List.of("§7"+spell.element().title+" · "+SpellLevels.rarity(spell,level)+(spell.ultimate()?" · Ultimate":" · ระดับ "+SpellLevels.roman(level)+"/VII"),
            "§bMana "+SpellLevels.mana(spell,level)+" · CD "+StaffSpellService.format1(SpellLevels.cooldown(spell,level))+"s · ×"+StaffSpellService.format2(SpellLevels.multiplier(spell,level)),
            "§7"+spell.description(),
            matching?"§aธาตุตรงกับคทา: ดาเมจ +50%":"§cธาตุไม่ตรงกับคทา: ดาเมจ −50%");
    }
    private ItemStack scrollIcon(ElementalSpell spell,int level,String name,List<String> lore){
        ItemStack item=service().scrollIcon(spell,level);var meta=item.getItemMeta();
        meta.setDisplayName(name);meta.setLore(lore);meta.addItemFlags(ItemFlag.values());item.setItemMeta(meta);return item;
    }
    private ItemStack scrollEntry(Player player,Workbench wb,ItemStack staff,int slot,boolean room){
        ItemStack stack=player.getInventory().getItem(slot);ElementalSpell spell=service().scroll(stack);int level=service().scrollLevel(stack);
        String problem=blocked(staff,spell,level);boolean armed=wb.armed==slot;
        boolean raise=problem==null&&service().spells(staff).contains(spell);
        List<String> lore=new ArrayList<>();
        if(armed)lore.addAll(List.of("§d✦ เลือกแล้ว · คลิกเวทในคทา (แถวบน) ที่จะสลับออก","§7คลิกคัมภีร์นี้อีกครั้งเพื่อยกเลิก"));
        else if(problem!=null)lore.add("§c✖ "+problem);
        else if(raise)lore.addAll(List.of("§d⬆ คลิกเพื่ออัประดับ "+SpellLevels.label(spell,service().level(staff,spell))+" → "+SpellLevels.roman(level),"§7คัมภีร์ระดับเดิมคืนให้"));
        else if(room)lore.add("§a▶ คลิกเพื่อใส่ในช่อง "+(service().spells(staff).size()+1));
        else lore.addAll(List.of("§e▶ คทาเต็ม · คลิก แล้วเลือกเวทแถวบนที่จะสลับออก","§7เวทเดิมกลับเป็นคัมภีร์ให้"));
        lore.addAll(details(staff,spell,level));
        lore.add("§8มี "+stack.getAmount()+" ชิ้นในกระเป๋า");
        ItemStack icon=scrollIcon(spell,level,(problem!=null?ChatColor.DARK_GRAY+"✖ ":armed?ChatColor.LIGHT_PURPLE+"✦ ":raise?ChatColor.LIGHT_PURPLE+"⬆ ":spell.element().color.toString())+SpellLevels.label(spell,level),lore);
        icon.setAmount(Math.max(1,Math.min(64,stack.getAmount())));
        return armed?glint(icon):icon;
    }
    private ItemStack upgradeButton(Player player,Workbench wb,StaffSpellService.Tier tier){
        StaffSpellService.Tier next=tier.next();
        if(next==null)return gui("menu_upgrade_off",ChatColor.GRAY+"อัปเกรดคทา",List.of(tier.wooden()?"คทาไม้สูงสุดแล้ว (2 ช่อง · ถึง Rare)":"คทาระดับสูงสุดแล้ว (5 ช่อง)"));
        List<String> lore=new ArrayList<>(List.of("§f"+tier+" → "+next+" · "+next.slots+" ช่อง · รองรับถึง "+next.maxRarity,"วัตถุดิบ:"));
        boolean ready=true;
        for(var cost:service().upgradeCost(tier).entrySet()){
            int have=0;for(int slot=0;slot<36;slot++){ItemStack stack=player.getInventory().getItem(slot);if(slot!=wb.staffSlot&&stack!=null&&new ItemStack(cost.getKey()).isSimilar(stack))have+=stack.getAmount();}
            ready&=have>=cost.getValue();
            lore.add((have>=cost.getValue()?"§a✔ ":"§c✖ ")+pretty(cost.getKey())+" "+Math.min(have,cost.getValue())+"/"+cost.getValue());
        }
        lore.add("§7เวทเดิมอยู่ครบ");
        if(!ready)return gui("menu_upgrade_off",ChatColor.GRAY+"อัปเกรดคทา · วัตถุดิบไม่พอ",lore);
        boolean armed=System.currentTimeMillis()<wb.upgradeArmed;
        lore.add(armed?"§6คลิกอีกครั้งภายใน 5 วินาทีเพื่อยืนยัน":"§aคลิก 2 ครั้งเพื่ออัปเกรด");
        return gui(armed?"menu_upgrade_armed":"menu_upgrade",(armed?ChatColor.GOLD+"ยืนยันอัปเกรด?":ChatColor.GREEN+"อัปเกรดคทา"),lore);
    }
    private static String pretty(Material material){
        StringBuilder out=new StringBuilder();
        for(String word:material.name().toLowerCase(Locale.ROOT).split("_"))out.append(out.isEmpty()?"":" ").append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        return out.toString();
    }

    private void scrollClicked(Player player,Workbench wb,int slot){
        ItemStack staff=player.getInventory().getItem(wb.staffSlot),stack=player.getInventory().getItem(slot);ElementalSpell spell=service().scroll(stack);
        if(spell==null){hint(player,"คลิกคัมภีร์เวทเพื่อใส่ลงคทา");return;}
        if(wb.armed==slot){wb.armed=-1;hint(player,"ยกเลิกการเลือก "+spell.title());return;}
        String problem=blocked(staff,stack);
        if(problem!=null){fail(player,problem);return;}
        List<ElementalSpell> known=service().spells(staff);
        if(known.contains(spell)){
            // A higher level of an imprinted spell replaces it in place; the old level comes back as a scroll.
            done(player,service().swap(player,wb.staffSlot,staff.clone(),slot,stack.clone(),known.indexOf(spell)));return;
        }
        if(known.size()<service().tier(staff).slots){
            StaffSpellService.Result result=service().imprint(player,wb.staffSlot,staff.clone(),slot,stack.clone(),known.size());
            done(player,result);
        }else{
            wb.armed=slot;player.playSound(player.getLocation(),Sound.UI_BUTTON_CLICK,.6f,1.4f);
            hint(player,"คทาเต็ม · คลิกเวทแถวบนที่จะสลับกับ "+spell.title());
        }
    }
    private void spellClicked(Player player,Workbench wb,int index){
        ItemStack staff=player.getInventory().getItem(wb.staffSlot);List<ElementalSpell> known=service().spells(staff);
        if(index>=known.size()){hint(player,index<service().tier(staff).slots?"ช่องว่าง · คลิกคัมภีร์แถวล่างเพื่อใส่":"อัปเกรดคทาเพื่อปลดล็อกช่องนี้");return;}
        if(wb.armed>=0){
            int scroll=wb.armed;wb.armed=-1;
            done(player,service().swap(player,wb.staffSlot,staff.clone(),scroll,player.getInventory().getItem(scroll)==null?null:player.getInventory().getItem(scroll).clone(),index));
            return;
        }
        service().select(player,index+1);
    }
    private void upgradeClicked(Player player,Workbench wb){
        long now=System.currentTimeMillis();
        if(now>=wb.upgradeArmed){
            ItemStack button=wb.inventory.getItem(UPGRADE);
            if(button!=null&&new NamespacedKey("advance_magic","menu_upgrade").equals(button.getItemMeta().getItemModel())){
                wb.upgradeArmed=now+5000;player.playSound(player.getLocation(),Sound.BLOCK_NOTE_BLOCK_PLING,.6f,.8f);
                Bukkit.getScheduler().runTaskLater(plugin,()->{if(wb.upgradeArmed!=0&&System.currentTimeMillis()>=wb.upgradeArmed&&player.getOpenInventory().getTopInventory().getHolder()==wb){wb.upgradeArmed=0;draw(player,wb);}},101);
            }else fail(player,"วัตถุดิบไม่พอ หรือคทาสูงสุดแล้ว · ดูรายการในปุ่ม");
            return;
        }
        wb.upgradeArmed=0;
        StaffSpellService.Result result=service().upgrade(player);done(player,result);
        if(result.success())player.playSound(player.getLocation(),Sound.BLOCK_SMITHING_TABLE_USE,1f,1f);
    }
    private void hint(Player player,String text){player.sendActionBar(net.kyori.adventure.text.Component.text(text,net.kyori.adventure.text.format.NamedTextColor.YELLOW));}
    private void fail(Player player,String text){
        player.sendActionBar(net.kyori.adventure.text.Component.text(text,net.kyori.adventure.text.format.NamedTextColor.RED));
        player.playSound(player.getLocation(),Sound.BLOCK_NOTE_BLOCK_BASS,.7f,.6f);
    }
    private void done(Player player,StaffSpellService.Result result){
        message(player,result);
        if(result.success())player.playSound(player.getLocation(),Sound.BLOCK_AMETHYST_BLOCK_RESONATE,1f,1.2f);
        else player.playSound(player.getLocation(),Sound.BLOCK_NOTE_BLOCK_BASS,.7f,.6f);
    }

    // ---- codex ----
    public void codex(Player player,Element element){
        if(element==null)element=Element.FIRE;
        service().discover(player);Codex view=new Codex(player);
        ItemStack fill=gui("skilltree_panel",null,null);for(int i=0;i<54;i++)view.inventory.setItem(i,fill);
        Set<ElementalSpell> discovered=service().discovered(player);
        for(Element choice:Element.values()){
            long total=Arrays.stream(ElementalSpell.values()).filter(spell->spell.element()==choice).count();
            long found=discovered.stream().filter(spell->spell.element()==choice).count();
            view.inventory.setItem(choice.ordinal(),icon(material(choice),choice.color+choice.title+(choice==element?" ◀":""),found+" / "+total+" ค้นพบ","คลิกเพื่อดูธาตุนี้"));
        }
        int slot=18;
        for(ElementalSpell spell:ElementalSpell.values())if(spell.element()==element){
            view.inventory.setItem(slot++,discovered.contains(spell)?scrollIcon(spell,SpellLevels.BASE,spell.element().color+spell.title(),codexLore(spell))
                :icon(Material.GRAY_DYE,ChatColor.DARK_GRAY+"???",element.title+" · "+spell.rarity(),"ค้นหาคัมภีร์จากหีบโครงสร้าง Evergarden","การค้นพบไม่ได้ให้เวทหรือคัมภีร์ฟรี"));
        }
        view.inventory.setItem(45,gui("menu_back",ChatColor.YELLOW+"กลับหน้าคทา",List.of()));
        view.inventory.setItem(51,gui("menu_swap",ChatColor.LIGHT_PURPLE+"แท่นหลอมคัมภีร์",List.of("รวมคัมภีร์ 2 แผ่นระดับเดียวกัน → ระดับถัดไป","คลิกเพื่อเปิด")));
        view.inventory.setItem(47,icon(Material.CRAFTING_TABLE,ChatColor.GREEN+"สูตรคราฟต์คทาไม้","_ A _ / _ B _ / _ B _","A = Amethyst Shard 1 · B = Blaze Rod รวม 2","เริ่ม 1 ช่อง · อัปเป็น 2 ช่อง · สูงสุด Rare","คทาธาตุทั้ง 8 ชนิดหาได้จากลูต","/staff recipe wood"));
        view.inventory.setItem(49,gui("menu_codex",ChatColor.GOLD+"ค้นพบ "+discovered.size()+" / "+ElementalSpell.values().length,List.of("สะสมประวัติในตัวผู้เล่น","เวทที่ใช้ได้ขึ้นกับคทาในมือ")));
        view.inventory.setItem(53,gui("skilltree_close",ChatColor.RED+"ปิด",List.of()));player.openInventory(view.inventory);
    }

    private static List<String> codexLore(ElementalSpell spell){
        List<String> lore=new ArrayList<>(List.of("§aค้นพบแล้ว · "+spell.rarity()+(spell.ultimate()?" · Ultimate":""),"§7"+spell.description()));
        if(!SpellLevels.leveled(spell)){lore.add("§bMana "+spell.mana()+" · CD "+StaffSpellService.format1(spell.cooldown())+"s");return lore;}
        lore.add("§7ระดับ · Mana · CD · ดาเมจ");
        for(int level=SpellLevels.MIN;level<=SpellLevels.MAX;level++)
            lore.add(StaffSpellService.rarityColor(spell,level)+SpellLevels.roman(level)+"§7 · "+SpellLevels.mana(spell,level)+" MP · "
                +StaffSpellService.format1(SpellLevels.cooldown(spell,level))+"s · ×"+StaffSpellService.format2(SpellLevels.multiplier(spell,level))
                +(level==SpellLevels.BASE?" §8(มาตรฐาน)":""));
        return lore;
    }

    // ---- fusion ----
    private List<Group> groups(Player player){
        Map<String,Group> merged=new LinkedHashMap<>();
        for(int slot=0;slot<36;slot++){
            ItemStack stack=player.getInventory().getItem(slot);ElementalSpell spell=service().scroll(stack);
            if(spell==null||!SpellLevels.leveled(spell))continue;
            int level=service().scrollLevel(stack);
            merged.merge(spell.id()+"@"+level,new Group(spell,level,stack.getAmount()),(a,b)->new Group(a.spell(),a.level(),a.count()+b.count()));
        }
        return merged.values().stream().sorted(Comparator.comparing((Group g)->!g.fusable())
            .thenComparing(g->g.spell().ordinal()).thenComparing(Group::level)).toList();
    }
    private static String stats(ElementalSpell spell,int level){
        return "§bMana "+SpellLevels.mana(spell,level)+" · CD "+StaffSpellService.format1(SpellLevels.cooldown(spell,level))+"s · ×"+StaffSpellService.format2(SpellLevels.multiplier(spell,level));
    }
    private void drawFusion(Player player,Fusion view){
        Inventory inv=view.inventory;
        for(int i=0;i<54;i++)inv.setItem(i,gui("skilltree_panel",null,null));
        List<Group> groups=groups(player);
        if(view.selected!=null)view.selected=groups.stream().filter(g->g.spell()==view.selected.spell()&&g.level()==view.selected.level()).findFirst().orElse(null);
        if(view.selected==null)view.selected=groups.stream().filter(Group::fusable).findFirst().orElse(null);
        inv.setItem(F_BACK,gui("menu_back",ChatColor.YELLOW+"กลับหน้าคทา",List.of()));
        inv.setItem(F_HELP,gui("skilltree_help",ChatColor.AQUA+"วิธีหลอมคัมภีร์",List.of(
            "§f1. เลือกคัมภีร์จากแถวล่าง (ต้องมี 2 แผ่นขึ้นไป)",
            "§f2. กดปุ่มลูกศรเขียว = หลอม 1 ครั้ง",
            "§f   Shift+คลิก = หลอมทั้งหมดที่ทำได้",
            "§f3. คัมภีร์ 2 แผ่น → 1 แผ่นระดับถัดไป","",
            "§7ระดับ V = พลังมาตรฐาน · VII = Epic สูงสุด",
            "§7ขึ้น 1 ระดับ: ดาเมจ ×1.25 · คูลดาวน์สั้นลง",
            "§7ระดับ VII: ดาเมจ ×1.56 · คูลดาวน์ ×0.67 ของระดับ V",
            "§7ใช้คัมภีร์ในกระเป๋า ไม่ต้องถือคทา")));
        inv.setItem(F_TITLE,glint(gui("menu_codex",ChatColor.LIGHT_PURPLE+"✦ แท่นหลอมคัมภีร์ ✦",List.of("รวมพลังคัมภีร์เวทระดับเดียวกัน","ให้กลายเป็นคัมภีร์ระดับที่สูงขึ้น"))));
        inv.setItem(F_CLOSE,gui("skilltree_close",ChatColor.RED+"ปิด",List.of()));
        Group pick=view.selected;
        if(pick==null){
            inv.setItem(F_RESULT,gui("menu_socket",ChatColor.GRAY+"ผลลัพธ์",List.of("เลือกคัมภีร์ที่มี 2 แผ่นขึ้นไปจากแถวล่าง")));
            inv.setItem(F_INPUT_A,gui("menu_socket",ChatColor.GRAY+"คัมภีร์แผ่นที่ 1",List.of("ว่าง")));
            inv.setItem(F_INPUT_B,gui("menu_socket",ChatColor.GRAY+"คัมภีร์แผ่นที่ 2",List.of("ว่าง")));
            inv.setItem(F_PLUS,gui("skilltree_plus_off",ChatColor.GRAY+"+",List.of()));
            inv.setItem(F_BUTTON,gui("menu_upgrade_off",ChatColor.GRAY+"หลอม",List.of("ยังไม่มีคัมภีร์ที่หลอมได้","หาคัมภีร์ซ้ำจากหีบโครงสร้าง Evergarden")));
        }else{
            ElementalSpell spell=pick.spell();int level=pick.level();boolean maxed=level>=SpellLevels.MAX;int times=pick.count()/2;
            String from=SpellLevels.label(spell,level);
            List<String> inputLore=List.of("§7"+spell.element().title+" · "+SpellLevels.rarity(spell,level)+" · ระดับ "+SpellLevels.roman(level),stats(spell,level),"§8มี "+pick.count()+" แผ่นในกระเป๋า");
            ItemStack input=scrollIcon(spell,level,spell.element().color+from,inputLore);
            inv.setItem(F_INPUT_A,input.clone());
            ItemStack second=pick.count()>=2?input.clone():gui("menu_socket",ChatColor.GRAY+"ต้องมีอีก 1 แผ่น",List.of("คัมภีร์ "+from+" แผ่นที่ 2"));
            inv.setItem(F_INPUT_B,second);
            inv.setItem(F_PLUS,gui(pick.count()>=2?"skilltree_plus":"skilltree_plus_off",ChatColor.GREEN+"+",List.of()));
            inv.setItem(F_INPUT_BADGE,gui("skilltree_level_"+level,StaffSpellService.rarityColor(spell,level)+"ระดับ "+SpellLevels.roman(level),List.of(SpellLevels.rarity(spell,level).toString())));
            if(maxed){
                inv.setItem(F_RESULT,glint(scrollIcon(spell,level,ChatColor.LIGHT_PURPLE+from+" · สูงสุด",List.of("§dระดับ VII คือระดับสูงสุด (Epic)",stats(spell,level)))));
                inv.setItem(F_BUTTON,gui("menu_upgrade_off",ChatColor.GRAY+"ระดับสูงสุดแล้ว",List.of("คัมภีร์นี้หลอมต่อไม่ได้")));
            }else{
                int next=level+1;String to=SpellLevels.label(spell,next);
                List<String> resultLore=new ArrayList<>(List.of("§7"+spell.element().title+" · "+SpellLevels.rarity(spell,next)+" · ระดับ "+SpellLevels.roman(next),
                    "§7ก่อน: "+stats(spell,level),"§aหลัง: "+stats(spell,next),"§7"+spell.description()));
                if(SpellLevels.rarity(spell,next)!=SpellLevels.rarity(spell,level))resultLore.add("§dความหายากขึ้นเป็น "+SpellLevels.rarity(spell,next)+" · ตรวจระดับคทาที่รองรับ");
                inv.setItem(F_RESULT,glint(scrollIcon(spell,next,StaffSpellService.rarityColor(spell,next)+"✦ "+to,resultLore)));
                inv.setItem(F_RESULT_BADGE,gui("skilltree_level_"+next,StaffSpellService.rarityColor(spell,next)+"ระดับ "+SpellLevels.roman(next),List.of(SpellLevels.rarity(spell,next).toString())));
                if(times>0){
                    inv.setItem(F_BUTTON,gui("menu_upgrade",ChatColor.GREEN+"หลอม "+from+" → "+to,List.of("§fคลิก: หลอม 1 ครั้ง (ใช้ 2 แผ่น)","§fShift+คลิก: หลอมทั้งหมด "+times+" ครั้ง ("+(times*2)+" แผ่น → "+times+")")));
                    inv.setItem(F_COUNT,gui("skilltree_orb_"+Math.min(times,99),ChatColor.GOLD+"หลอมได้ "+times+" ครั้ง",List.of("จาก "+pick.count()+" แผ่น")));
                }else inv.setItem(F_BUTTON,gui("menu_upgrade_off",ChatColor.GRAY+"ต้องมี 2 แผ่น",List.of("มี "+from+" เพียง 1 แผ่น")));
            }
        }
        inv.setItem(F_LIST,gui("menu_label_scrolls",ChatColor.GOLD+"คัมภีร์ในกระเป๋า",List.of(groups.size()+" แบบ · คลิกเพื่อเลือก","คัมภีร์ที่หลอมได้แสดงก่อน")));
        int pages=Math.max(1,(groups.size()+SCROLL_SLOTS.length-1)/SCROLL_SLOTS.length);view.page=Math.floorMod(view.page,pages);
        view.entries.clear();
        for(int k=0;k<SCROLL_SLOTS.length;k++){
            int index=view.page*SCROLL_SLOTS.length+k;if(index>=groups.size())break;
            Group group=groups.get(index);view.entries.put(SCROLL_SLOTS[k],group);
            boolean chosen=pick!=null&&group.spell()==pick.spell()&&group.level()==pick.level();
            List<String> lore=new ArrayList<>();
            if(chosen)lore.add("§d✦ กำลังเลือกอยู่");
            else if(group.fusable())lore.add("§a▶ หลอมได้ "+group.count()/2+" ครั้ง → "+SpellLevels.roman(group.level()+1));
            else lore.add(group.level()>=SpellLevels.MAX?"§dระดับสูงสุด":"§7ต้องมีอย่างน้อย 2 แผ่น");
            lore.add(stats(group.spell(),group.level()));lore.add("§8มี "+group.count()+" แผ่น");
            ItemStack icon=scrollIcon(group.spell(),group.level(),(group.fusable()?group.spell().element().color.toString():ChatColor.GRAY.toString())+SpellLevels.label(group.spell(),group.level()),lore);
            icon.setAmount(Math.max(1,Math.min(64,group.count())));
            inv.setItem(SCROLL_SLOTS[k],chosen?glint(icon):icon);
        }
        if(groups.isEmpty())inv.setItem(SCROLL_SLOTS[3],gui("menu_socket",ChatColor.GRAY+"ไม่มีคัมภีร์ในกระเป๋า",List.of("หาคัมภีร์จากหีบโครงสร้าง Evergarden")));
        if(pages>1)inv.setItem(F_PAGE,gui("menu_page",ChatColor.YELLOW+"หน้า "+(view.page+1)+"/"+pages,List.of("คลิกเพื่อดูหน้าถัดไป")));
    }
    private void fusionClicked(Player player,Fusion view,int raw,boolean shift){
        if(view.entries.containsKey(raw)){view.selected=view.entries.get(raw);player.playSound(player.getLocation(),Sound.UI_BUTTON_CLICK,.6f,1.4f);return;}
        switch(raw){
            case F_BUTTON->{
                Group pick=view.selected;if(pick==null||!pick.fusable()){fail(player,"เลือกคัมภีร์ที่มี 2 แผ่นขึ้นไปก่อน");return;}
                StaffSpellService.Result result=service().fuse(player,pick.spell(),pick.level(),shift?pick.count()/2:1);
                message(player,result);
                if(result.success()){
                    player.playSound(player.getLocation(),Sound.BLOCK_ENCHANTMENT_TABLE_USE,1f,1.1f);
                    player.playSound(player.getLocation(),Sound.BLOCK_AMETHYST_BLOCK_RESONATE,1f,1.4f);
                    player.getWorld().spawnParticle(Particle.ENCHANT,player.getLocation().add(0,1.2,0),60,.6,.8,.6,.8);
                    // Follow the new scroll so the next press keeps climbing.
                    view.selected=new Group(pick.spell(),pick.level()+1,0);
                }else player.playSound(player.getLocation(),Sound.BLOCK_NOTE_BLOCK_BASS,.7f,.6f);
            }
            case F_PAGE->view.page++;
            default->{}
        }
    }

    // ---- input ----
    @EventHandler(priority=EventPriority.HIGHEST) public void click(InventoryClickEvent event){
        InventoryHolder holder=event.getView().getTopInventory().getHolder();
        if(!(holder instanceof Workbench)&&!(holder instanceof Codex)&&!(holder instanceof Fusion))return;
        event.setCancelled(true);
        if(!(event.getWhoClicked() instanceof Player player))return;
        ClickType type=event.getClick();
        if(type!=ClickType.LEFT&&type!=ClickType.RIGHT&&type!=ClickType.SHIFT_LEFT&&type!=ClickType.SHIFT_RIGHT)return;
        int raw=event.getRawSlot();if(raw<0)return;
        if(holder instanceof Fusion view){
            if(!view.owner.equals(player.getUniqueId())||raw>=54)return;
            boolean shift=type==ClickType.SHIFT_LEFT||type==ClickType.SHIFT_RIGHT;
            if(raw==F_BACK){later(()->open(player));return;}
            if(raw==F_CLOSE){later(player::closeInventory);return;}
            later(()->{
                if(player.getOpenInventory().getTopInventory().getHolder()!=view)return;
                fusionClicked(player,view,raw,shift);
                if(player.getOpenInventory().getTopInventory().getHolder()==view)drawFusion(player,view);
            });
            return;
        }
        if(holder instanceof Codex codex){
            if(!codex.owner.equals(player.getUniqueId())||raw>=54)return;
            if(raw<Element.values().length)later(()->codex(player,Element.values()[raw]));
            else if(raw==45)later(()->open(player));
            else if(raw==47)SpellCommand.recipes(player,null);
            else if(raw==51)later(()->fusion(player));
            else if(raw==53)later(player::closeInventory);
            return;
        }
        Workbench wb=(Workbench)holder;
        if(!wb.owner.equals(player.getUniqueId()))return;
        boolean bottom=raw>=54&&event.getClickedInventory()==player.getInventory();int slot=event.getSlot();
        later(()->{
            if(player.getOpenInventory().getTopInventory().getHolder()!=wb)return;
            if(!holding(player,wb)){player.closeInventory();player.sendMessage(ChatColor.RED+"ถือคทาไว้ในมือระหว่างใช้เมนู · พิมพ์ /spell อีกครั้ง");return;}
            if(raw!=UPGRADE)wb.upgradeArmed=0;
            if(bottom){if(slot<36&&slot!=wb.staffSlot)itemClicked(player,wb,slot);}
            else if(wb.scrolls.containsKey(raw))itemClicked(player,wb,wb.scrolls.get(raw));
            else if(raw==GEM_A||raw==GEM_B)gemClicked(player,wb,raw==GEM_A?0:1);
            else if(raw>=SPELL&&raw<SPELL+5)spellClicked(player,wb,raw-SPELL);
            else if(raw>=EXTRACT&&raw<EXTRACT+5&&raw-EXTRACT<service().spells(player.getInventory().getItem(wb.staffSlot)).size()){
                wb.armed=-1;done(player,service().extract(player,wb.staffSlot,player.getInventory().getItem(wb.staffSlot).clone(),raw-EXTRACT));
            }
            else if(raw>MOVE&&raw<MOVE+5&&raw-MOVE<service().spells(player.getInventory().getItem(wb.staffSlot)).size()){
                int index=raw-MOVE;StaffSpellService.Result result=service().reorder(player,index,index+1);
                if(result.success())player.playSound(player.getLocation(),Sound.UI_BUTTON_CLICK,.6f,1.2f);else fail(player,result.message());
            }
            else switch(raw){
                case PROTECT->done(player,service().protect(player));
                case TREE->{plugin.skillTree().open(player);return;}
                case UPGRADE->upgradeClicked(player,wb);
                case CODEX->{codex(player,plugin.staffs().element(player.getInventory().getItem(wb.staffSlot)));return;}
                case FUSE->{fusion(player);return;}
                case CLOSE->{player.closeInventory();return;}
                case PAGE->wb.page++;
                default->{}
            }
            if(player.getOpenInventory().getTopInventory().getHolder()==wb)draw(player,wb);
        });
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void drag(InventoryDragEvent event){
        InventoryHolder holder=event.getView().getTopInventory().getHolder();
        if(holder instanceof Workbench||holder instanceof Codex||holder instanceof Fusion)event.setCancelled(true);
    }
    private void later(Runnable action){Bukkit.getScheduler().runTask(plugin,action);}
    private static void message(Player player,StaffSpellService.Result result){player.sendMessage((result.success()?ChatColor.GREEN:ChatColor.RED)+result.message());}
    public static ItemStack spellIcon(ElementalSpell spell,boolean selected,String... extra){
        List<String> lore=new ArrayList<>(List.of(ChatColor.GRAY+spell.element().title+" · "+spell.rarity()+(spell.ultimate()?" · Ultimate":""),
                ChatColor.AQUA+"Mana "+spell.mana()+" · CD "+StaffSpellService.format(spell.cooldown())+"s",
                ChatColor.GRAY+"Multiplier รวม "+StaffSpellService.format(spell.multiplier())+"x",ChatColor.GRAY+spell.description()));
        for(String line:extra)lore.add(ChatColor.YELLOW+line);
        ItemStack item=new ItemStack(material(spell.element()));var meta=item.getItemMeta();
        meta.setDisplayName(spell.element().color+(selected?"▶ ":"")+spell.title());meta.setLore(lore);item.setItemMeta(meta);return item;
    }
    private static ItemStack icon(Material material,String title,String... lines){
        ItemStack item=new ItemStack(material);var meta=item.getItemMeta();meta.setDisplayName(title);
        meta.setLore(Arrays.stream(lines).map(line->ChatColor.GRAY+line).toList());item.setItemMeta(meta);return item;
    }
    private static Material material(Element element){return switch(element){case FIRE->Material.BLAZE_POWDER;case WATER->Material.PRISMARINE_CRYSTALS;case ICE->Material.SNOWBALL;case WIND->Material.FEATHER;case EARTH->Material.FLINT;case LIGHT->Material.GLOWSTONE_DUST;case DARK->Material.ENDER_PEARL;case LIGHTNING->Material.AMETHYST_SHARD;};}
}
