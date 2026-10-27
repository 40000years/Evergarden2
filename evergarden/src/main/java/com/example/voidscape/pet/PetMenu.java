package com.example.voidscape.pet;

import com.example.voidscape.VoidscapePlugin;
import com.example.voidscape.boss.JudgePackService;
import io.papermc.paper.event.packet.UncheckedSignChangeEvent;
import io.papermc.paper.math.Position;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.TileState;
import org.bukkit.block.sign.Side;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.geysermc.cumulus.form.CustomForm;
import org.geysermc.cumulus.form.ModalForm;
import org.geysermc.cumulus.form.SimpleForm;
import org.geysermc.cumulus.util.FormImage;
import org.geysermc.floodgate.api.FloodgateApi;
import java.util.*;

/**
 * {@code /pet}: the stable, one pet's card with its skills and mode, renaming, release and the
 * breeding nest. Chest menus on Java, forms on Bedrock. Renaming uses a sign editor (or a Name
 * Tag, or {@code /pet name}), so it works for players whose chat is disabled.
 * Menu art (pet portraits, buttons) comes from Advance Magic's pack: advance_magic:menu_* models.
 */
public final class PetMenu implements Listener,CommandExecutor,TabCompleter,AutoCloseable {
    private enum Page {STABLE,PET,NEST}
    static final class Holder implements InventoryHolder {
        final Page page;final UUID pet;
        final Map<Integer,Runnable> actions=new HashMap<>();
        Inventory inventory;String armed;long armedUntil;boolean fresh;
        Holder(Page page,UUID pet){this.page=page;this.pet=pet;}
        @Override public Inventory getInventory(){return inventory;}
    }
    private static final int[] SLOTS={10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34,37,38,39,40,41,42,43};
    private static final int[] CANDIDATES={28,29,30,31,32,33,34,35,37,38,39,40,41,42,43,44,46,47,48,49,50,51,52,53};
    private final VoidscapePlugin plugin;
    private final PetCompanions companions;
    private final PetBreeding breeding;
    private final PetTaming taming;
    /** An open sign editor: the pet being named and the client-only sign's block. */
    private record SignPrompt(UUID pet,Location block){}
    private final Map<UUID,SignPrompt> signs=new HashMap<>();
    private final Map<UUID,UUID[]> drafts=new HashMap<>();

    PetMenu(VoidscapePlugin plugin,PetCompanions companions,PetBreeding breeding,PetTaming taming){
        this.plugin=plugin;this.companions=companions;this.breeding=breeding;this.taming=taming;
        Bukkit.getPluginManager().registerEvents(this,plugin);
    }
    private static Component text(String value,TextColor color){return Component.text(value,color).decoration(TextDecoration.ITALIC,false);}
    private static ItemStack button(Material material,String name,TextColor color,List<String> lore){
        ItemStack item=new ItemStack(material);var meta=item.getItemMeta();
        meta.displayName(text(name,color));meta.lore(lore.stream().map(line->text(line,NamedTextColor.GRAY)).toList());
        meta.addItemFlags(ItemFlag.values());item.setItemMeta(meta);return item;
    }
    /** Paper drawn by an Advance Magic menu model; plain paper with the same text if that pack is absent. */
    private static ItemStack art(String model,String name,TextColor color,List<String> lore){
        return art(new NamespacedKey("advance_magic",model),name,color,lore);
    }
    private static ItemStack art(NamespacedKey model,String name,TextColor color,List<String> lore){
        ItemStack item=button(Material.PAPER,name,color,lore);var meta=item.getItemMeta();
        meta.setItemModel(model);item.setItemMeta(meta);return item;
    }
    private static ItemStack glint(ItemStack item){var meta=item.getItemMeta();meta.setEnchantmentGlintOverride(true);item.setItemMeta(meta);return item;}
    /** Portraits of every species ship in Evergarden's own pack (pet-portraits.zip). */
    private static NamespacedKey portrait(PetRecord record,boolean dim){
        return new NamespacedKey("evergarden","menu_pet_"+record.species.id+"_"+record.stage.id+(dim?"_dim":""));
    }
    private static String formImage(PetRecord record){return "textures/menu/pet_"+record.species.id+"_"+record.stage.id;}
    private boolean bedrock(Player player){return JudgePackService.bedrock(player)&&Bukkit.getPluginManager().getPlugin("floodgate")!=null;}
    private static String hours(long millis){return Math.max(1,(millis+3_599_999L)/3_600_000L)+" ชม.";}
    private static String minutes(long millis){return Math.max(1,(millis+59_999L)/60_000L)+" นาที";}
    private static int tenths(double value,double max){return (int)Math.clamp(Math.floor(10*value/Math.max(1e-9,max)),0,10);}

    /** The card text shared by chest lore and Bedrock forms. */
    List<String> describe(PetStable stable,PetRecord record){
        List<String> lines=new ArrayList<>();
        lines.add(record.species.display+" · "+record.species.thai);
        PetCatalog.Stage next=next(record);
        lines.add(record.stage.thai+(next==null?" · โตเต็มวัย":" · เติบโต "+record.growth+"/"+companions.threshold(next)));
        lines.add("ความผูกพัน "+record.bond+"/"+PetRecord.BOND_MAX);
        lines.add("พลังชีวิต "+record.vitality+" · ว่องไว "+record.agility+" · มนตรา "+record.affinity);
        lines.add("ขนาด "+Math.round(record.size*100)+"% · รุ่นที่ "+record.generation+(record.aura?" · ✦ ออร่า":""));
        long now=System.currentTimeMillis(),rest=record.bred+breeding.cooldownMillis()-now;
        Player owner=Bukkit.getPlayer(stable.owner);
        if(record.id.equals(stable.active)&&stable.out&&owner!=null)lines.add("★ "+companions.state(owner));
        if(stable.nesting(record.id))lines.add("อยู่ในรังผสมพันธุ์");
        else if(record.bred>0&&rest>0)lines.add("พักหลังผสมพันธุ์ อีก "+hours(rest));
        lines.add("อาหารที่ชอบ: "+PetTaming.foodName(taming.food(record.species)));
        lines.add("โหมด: "+PetSkills.modeName(record.mode)+" · "+fighter(record));
        if(companions.faintLeft(record)>0)lines.add("✖ สลบ · พักฟื้นอีก "+companions.recovery(record));
        PetSkills.Skill skill=PetSkills.of(record.species);
        if(skill!=null){
            lines.add("✦ "+skill.passiveName()+": "+skill.passive());
            lines.add("⚔ "+skill.traitName()+": "+skill.trait());
        }
        return lines;
    }
    private static PetCatalog.Stage next(PetRecord record){
        return record.stage==PetCatalog.Stage.BABY?PetCatalog.Stage.MEDIUM:record.stage==PetCatalog.Stage.MEDIUM?PetCatalog.Stage.ADULT:null;
    }
    /** Health and bite, as the assist mode uses them. */
    private String fighter(PetRecord record){
        double max=PetSkills.maxHealth(record),health=companions.health(record);
        return "❤ "+(int)Math.ceil(health)+"/"+(int)max+" · กัด "+String.format(Locale.ROOT,"%.1f",PetSkills.damage(record))
            +" ทุก "+String.format(Locale.ROOT,"%.1f",PetSkills.attackTicks(record)/20.0)+" วิ";
    }
    /** The skill page: what the pet does while out, in a fight, and how each mode behaves. */
    List<String> skillPage(PetRecord record){
        List<String> lines=new ArrayList<>();
        PetSkills.Skill skill=PetSkills.of(record.species);
        if(skill!=null){
            lines.add("สกิลติดตัว · "+skill.passiveName());
            lines.add("  "+skill.passive());
            lines.add("  ทำงานเมื่อออกมาอยู่ใกล้คุณ (ไม่เกิน 32 บล็อก) ทุกโหมด");
            lines.add("สกิลต่อสู้ · "+skill.traitName());
            lines.add("  "+skill.trait());
            lines.add("  ทำงานเมื่อกัดศัตรูในโหมดช่วยสู้");
        }
        lines.add("พลังต่อสู้ · "+fighter(record));
        lines.add("  ร่างโตกว่าเลือดและแรงกัดมากกว่า · พลังชีวิตเพิ่มเลือด");
        lines.add("  มนตราเพิ่มแรงกัด · ว่องไวทำให้กัดถี่ขึ้น");
        lines.add("ช่วยสู้ · ไล่กัดสิ่งที่คุณตีหรือมอนที่หมายหัวคุณ");
        lines.add("  ไม่ตีผู้เล่น สัตว์เลี้ยง บอส หรือ NPC");
        lines.add("  ศัตรูสวนกลับได้ เลือดหมดจะสลบ พักฟื้น 1 วันในเกม");
        lines.add("  เลือดฟื้นเองเมื่อพ้นการต่อสู้ 5 วินาที");
        return lines;
    }
    private ItemStack icon(PetStable stable,PetRecord record,String hint){
        List<String> lore=new ArrayList<>(describe(stable,record));
        if(hint!=null){lore.add("");lore.add(hint);}
        ItemStack item=art(portrait(record,false),record.name(),NamedTextColor.GOLD,lore);
        return record.id.equals(stable.active)&&stable.out?glint(item):item;
    }

    // ---- entry points ----
    public void open(Player player){
        if(bedrock(player))try{if(formStable(player))return;}catch(Throwable error){plugin.getLogger().warning("Pet form unavailable for "+player.getName()+": "+error);}
        chestStable(player);
    }
    void openPet(Player player,UUID id){
        if(bedrock(player))try{if(formPet(player,id))return;}catch(Throwable error){plugin.getLogger().warning("Pet form unavailable for "+player.getName()+": "+error);}
        chestPet(player,id);
    }

    // ---- actions shared by both menus ----
    private void toggleSummon(Player player,UUID id){
        PetStable stable=companions.stable(player);
        if(stable.out&&id.equals(stable.active)){companions.dismiss(player);plugin.message(player,stable.get(id).name()+" กลับเข้าคอกแล้ว");return;}
        String problem=companions.summon(player,id);
        plugin.message(player,problem!=null?problem:stable.get(id).name()+" ออกมาเดินกับคุณแล้ว");
    }
    private void release(Player player,UUID id){
        PetStable stable=companions.stable(player);PetRecord record=stable.get(id);if(record==null)return;
        if(id.equals(stable.active))companions.despawn(player.getUniqueId());
        stable.remove(id);
        UUID[] draft=drafts.get(player.getUniqueId());
        if(draft!=null)for(int i=0;i<2;i++)if(id.equals(draft[i]))draft[i]=null;
        plugin.message(player,record.name()+" กลับคืนสู่ธรรมชาติแล้ว");
        plugin.getLogger().info("Pet released: "+player.getName()+" "+record.species.id+" "+record.id);
    }
    /** The pair being prepared; ids of pets that left the stable read as empty. */
    private UUID[] draft(Player player){
        UUID[] draft=drafts.computeIfAbsent(player.getUniqueId(),id->new UUID[2]);
        PetStable stable=companions.stable(player);
        for(int i=0;i<2;i++)if(stable.get(draft[i])==null)draft[i]=null;
        return draft;
    }
    /** From a pet's card: keep a compatible partner already chosen, otherwise start the pair with this pet. */
    private void toNest(Player player,UUID id){
        UUID[] draft=draft(player);PetStable stable=companions.stable(player);
        if(id.equals(draft[0])||id.equals(draft[1]))return;
        PetRecord other=stable.get(draft[0]),self=stable.get(id);
        if(other!=null&&self!=null&&other.species==self.species&&draft[1]==null)draft[1]=id;
        else{draft[0]=id;draft[1]=null;}
    }
    private void startNest(Player player){
        UUID[] draft=draft(player);
        String problem=breeding.start(player,draft[0],draft[1]);
        if(problem!=null)plugin.message(player,problem);
        else{drafts.remove(player.getUniqueId());plugin.message(player,"เริ่มผสมพันธุ์แล้ว · ไข่พร้อมใน "+minutes(breeding.nestMillis()));}
    }
    private void collect(Player player){
        String problem=breeding.collect(player);
        plugin.message(player,problem!=null?problem:"ได้ไข่แล้ว! พกไว้ในกระเป๋าขณะออนไลน์ "+minutes(breeding.incubateMillis())+" แล้วคลิกขวาเพื่อฟัก");
    }
    private String nestStatus(PetStable stable){
        if(!stable.nestBusy())return "รังว่าง";
        long left=stable.nestReady-System.currentTimeMillis();
        PetRecord a=stable.get(stable.nestA),b=stable.get(stable.nestB);
        String pair=(a==null?"?":a.name())+" × "+(b==null?"?":b.name());
        return left>0?"รัง: "+pair+" · ไข่พร้อมใน "+minutes(left):"รัง: "+pair+" · ไข่พร้อมแล้ว!";
    }
    /** The eggs a player carries, one line each, or nothing when they carry none. */
    private List<String> eggLines(Player player){
        List<PetBreeding.Carried> eggs=breeding.carried(player);List<String> lines=new ArrayList<>();
        for(var egg:eggs.subList(0,Math.min(6,eggs.size())))
            lines.add("ไข่ "+egg.species().thai+" · "+(egg.percent()>=100?"พร้อมฟัก!":"ฟักแล้ว "+egg.percent()+"%"));
        if(eggs.size()>6)lines.add("และอีก "+(eggs.size()-6)+" ฟอง");
        return lines;
    }
    /** Hatches the first ready egg the player carries, the same as right-clicking it. */
    private void hatchCarried(Player player){
        for(var egg:breeding.carried(player)){
            if(egg.percent()<100)continue;
            String problem=breeding.hatch(player,player.getInventory().getItem(egg.slot()));
            if(problem!=null)plugin.message(player,problem);
            return;
        }
        plugin.message(player,"ยังไม่มีไข่ที่พร้อมฟัก · พกไข่ไว้ในกระเป๋าขณะออนไลน์ "+minutes(breeding.incubateMillis()));
    }
    /** The egg button: progress of every carried egg; a click hatches one that is ready. */
    private void eggButton(Player player,Holder holder,int slot,Runnable redraw){
        List<String> lines=eggLines(player);
        if(lines.isEmpty())return;
        boolean ready=breeding.carried(player).stream().anyMatch(e->e.percent()>=100);
        List<String> lore=new ArrayList<>(lines);lore.add("");
        lore.add(ready?"คลิกเพื่อฟักไข่ที่พร้อม":"พกไว้ในกระเป๋าขณะออนไลน์ "+minutes(breeding.incubateMillis())+" แล้วจึงฟักได้");
        ItemStack item=button(Material.PAPER,ready?"ฟักไข่!":"ไข่ที่กำลังฟัก",ready?NamedTextColor.GREEN:NamedTextColor.AQUA,lore);
        var meta=item.getItemMeta();meta.setItemModel(PetBreeding.EGG_MODEL);item.setItemMeta(meta);
        put(holder,slot,ready?glint(item):item,ready?()->{hatchCarried(player);redraw.run();}:null);
    }
    /** A breeding condition for the chosen pair; ok is null until enough pets are chosen to judge it. */
    private record Check(String kind,Boolean ok,String label,String detail){}
    private List<Check> checks(Player player,PetStable stable,PetRecord a,PetRecord b,long now){
        List<PetRecord> chosen=new ArrayList<>();if(a!=null)chosen.add(a);if(b!=null)chosen.add(b);
        boolean both=chosen.size()==2;int need=breeding.bondNeeded();
        List<Check> list=new ArrayList<>();
        List<String> young=chosen.stream().filter(r->r.stage!=PetCatalog.Stage.ADULT).map(r->r.name()+" ("+r.stage.thai+")").toList();
        list.add(new Check("adult",!young.isEmpty()?Boolean.FALSE:both?Boolean.TRUE:null,"ทั้งสองตัวโตเต็มวัย",
            young.isEmpty()?"ตัวเด็กและร่างกลางผสมพันธุ์ไม่ได้":"ยังไม่โต: "+String.join(", ",young)));
        list.add(new Check("species",both?a.species==b.species:null,"ชนิดเดียวกัน",
            both?a.species.thai+(a.species==b.species?"":" กับ "+b.species.thai):"เลือกสัตว์ชนิดเดียวกัน 2 ตัว"));
        List<String> weak=chosen.stream().filter(r->r.bond<need).map(r->r.name()+" "+r.bond+"/"+need).toList();
        list.add(new Check("bond",!weak.isEmpty()?Boolean.FALSE:both?Boolean.TRUE:null,"ความผูกพันถึง "+need+" ทั้งคู่",
            weak.isEmpty()?"เพิ่มความผูกพันด้วยการพาออกมาเดินและป้อนอาหาร":"ยังไม่ถึง: "+String.join(", ",weak)));
        List<String> tired=chosen.stream().filter(r->r.bred>0&&now<r.bred+breeding.cooldownMillis()).map(r->r.name()+" อีก "+hours(r.bred+breeding.cooldownMillis()-now)).toList();
        list.add(new Check("rest",!tired.isEmpty()?Boolean.FALSE:both?Boolean.TRUE:null,"ไม่อยู่ในช่วงพักหลังผสม",
            tired.isEmpty()?"ผสมแล้วต้องพัก "+hours(breeding.cooldownMillis()):"ยังพักอยู่: "+String.join(", ",tired)));
        int eggs=stable.eggsToday(now);
        list.add(new Check("daily",eggs<breeding.dailyEggs(),"โควตาไข่วันนี้ "+eggs+"/"+breeding.dailyEggs(),eggs<breeding.dailyEggs()?"ยังเก็บไข่ได้อีก":"ครบแล้ว กลับมาพรุ่งนี้"));
        PetRecord any=a!=null?a:b;
        if(any!=null){
            Material food=taming.food(any.species);int have=0;
            for(ItemStack stack:player.getInventory().getStorageContents())if(stack!=null&&new ItemStack(food).isSimilar(stack))have+=stack.getAmount();
            list.add(new Check("food",have>=breeding.foodCost(),"อาหาร: "+PetTaming.foodName(food)+" ×"+breeding.foodCost(),"มีในกระเป๋า "+have+" ชิ้น (ธรรมดา ไม่ตั้งชื่อ)"));
        }else list.add(new Check("food",null,"อาหารที่ชอบ ×"+breeding.foodCost(),"เลือกสัตว์ก่อนจึงรู้ว่าใช้อาหารอะไร"));
        return list;
    }
    /** Why a stable pet cannot join the pair being chosen, or null. */
    private String unfit(PetStable stable,PetRecord record,PetRecord other,long now){
        if(record.stage!=PetCatalog.Stage.ADULT)return "ยังไม่โตเต็มวัย ("+record.stage.thai+")";
        if(other!=null&&other.species!=record.species)return "ต่างชนิดกับ "+other.name();
        if(record.bond<breeding.bondNeeded())return "ความผูกพัน "+record.bond+"/"+breeding.bondNeeded();
        if(record.bred>0&&now<record.bred+breeding.cooldownMillis())return "พักหลังผสม อีก "+hours(record.bred+breeding.cooldownMillis()-now);
        return null;
    }

    // ---- Java chest menus ----
    /** Reuses the open menu of the same page in place (no flicker, cursor kept); otherwise a new one. */
    private Holder frame(Player player,Page page,UUID pet,int size,String title){
        Holder holder;
        if(player.getOpenInventory().getTopInventory().getHolder() instanceof Holder open&&open.page==page&&Objects.equals(open.pet,pet)&&open.inventory.getSize()==size){
            holder=open;holder.actions.clear();
        }else{
            holder=new Holder(page,pet);holder.fresh=true;
            holder.inventory=Bukkit.createInventory(holder,size,text(title,TextColor.color(0x4b2a7a)));
        }
        ItemStack panel=art("skilltree_panel"," ",NamedTextColor.GRAY,List.of());
        var meta=panel.getItemMeta();meta.setHideTooltip(true);panel.setItemMeta(meta);
        for(int i=0;i<size;i++)holder.inventory.setItem(i,panel);
        return holder;
    }
    private void show(Player player,Holder holder){if(holder.fresh){holder.fresh=false;player.openInventory(holder.inventory);}}
    private void put(Holder holder,int slot,ItemStack item,Runnable action){holder.inventory.setItem(slot,item);if(action!=null)holder.actions.put(slot,action);}
    private boolean armed(Holder holder,String key){return key.equals(holder.armed)&&System.currentTimeMillis()<holder.armedUntil;}
    /** Destructive buttons need a second click within five seconds; the redraw shows the armed state. */
    private boolean confirm(Player player,Holder holder,String key,Runnable redraw){
        if(armed(holder,key)){holder.armed=null;return true;}
        holder.armed=key;holder.armedUntil=System.currentTimeMillis()+5000;
        player.playSound(player.getLocation(),Sound.BLOCK_NOTE_BLOCK_PLING,.6f,.8f);
        Bukkit.getScheduler().runTaskLater(plugin,()->{
            if(key.equals(holder.armed)&&System.currentTimeMillis()>=holder.armedUntil&&player.getOpenInventory().getTopInventory().getHolder()==holder){holder.armed=null;redraw.run();}
        },101);
        return false;
    }
    void chestStable(Player player){
        PetStable stable=companions.stable(player);
        Holder holder=frame(player,Page.STABLE,null,54,"✦ คอกสัตว์เลี้ยง · คลิกสัตว์เพื่อจัดการ");
        PetRecord active=stable.activeRecord();
        List<String> guide=new ArrayList<>(List.of("§fคลิกสัตว์ = ดูข้อมูล เรียก ตั้งชื่อ เปลี่ยนโหมด","§fปุ่มรังด้านบน = ผสมพันธุ์ (ตัวโตเต็มวัย ชนิดเดียวกัน)",
            "","วิธีผูกมิตร: คลิกขวาสัตว์ป่าพร้อมอาหารที่ชอบจนไว้ใจ 100%","ร่างกลางและโตเต็มวัยต้องมีเชือกจูง (Lead) พากลับ","","อาหารที่ชอบ:"));
        for(var species:WildPetSpawns.eligibleSpecies())guide.add(species.thai+": "+PetTaming.foodNameThai(taming.food(species)));
        put(holder,0,art("skilltree_help","วิธีใช้ · อาหารที่ชอบ",NamedTextColor.AQUA,guide),null);
        if(active!=null){
            boolean out=stable.out;
            put(holder,2,art(out?"menu_dismiss":"menu_summon",out?"เก็บ "+active.name()+" เข้าคอก":"เรียก "+active.name()+" ออกมา",out?NamedTextColor.GOLD:NamedTextColor.GREEN,
                List.of(companions.state(player))),()->{toggleSummon(player,active.id);chestStable(player);});
            put(holder,4,icon(stable,active,"ตัวที่ติดตามคุณ · คลิกเพื่อจัดการ"),()->chestPet(player,active.id));
        }else put(holder,4,art("menu_socket","คอกสัตว์เลี้ยง "+stable.pets.size()+"/"+stable.slots,NamedTextColor.GOLD,List.of("ยังไม่มีตัวที่ติดตาม","คลิกสัตว์ด้านล่าง แล้วกด เรียกออกมา")),null);
        long now=System.currentTimeMillis();
        String nestModel=!stable.nestBusy()?"menu_nest":now<stable.nestReady?"menu_nest_wait":"menu_egg_ready";
        List<String> nestLore=new ArrayList<>(List.of(nestStatus(stable),"ตัวโตเต็มวัยชนิดเดียวกันสองตัว"));
        nestLore.addAll(eggLines(player));nestLore.add("คลิกเพื่อเปิดรัง");
        put(holder,6,art(nestModel,"รังผสมพันธุ์",NamedTextColor.YELLOW,nestLore),()->chestNest(player));
        put(holder,7,art(stable.hideOthers?"menu_eye_on":"menu_eye_off","ซ่อนสัตว์เลี้ยงของคนอื่น: "+(stable.hideOthers?"เปิด":"ปิด"),NamedTextColor.AQUA,
            List.of("ช่วยให้เครื่องลื่นขึ้นในที่คนเยอะ","คลิกเพื่อสลับ")),()->{stable.hideOthers=!stable.hideOthers;stable.dirty=true;chestStable(player);});
        put(holder,8,art("skilltree_close","ปิด",NamedTextColor.RED,List.of()),player::closeInventory);
        List<PetRecord> pets=new ArrayList<>(stable.pets.values());
        for(int i=0;i<Math.min(SLOTS.length,stable.slots);i++){
            if(i<pets.size()){PetRecord record=pets.get(i);put(holder,SLOTS[i],icon(stable,record,"คลิกเพื่อจัดการ"),()->chestPet(player,record.id));}
            else put(holder,SLOTS[i],art("menu_socket","ช่องว่าง",NamedTextColor.GRAY,List.of("หาสัตว์ป่าใน Evergarden","แล้วคลิกขวาพร้อมอาหารที่มันชอบ")),null);
        }
        show(player,holder);
    }
    void chestPet(Player player,UUID id){
        PetStable stable=companions.stable(player);PetRecord record=stable.get(id);
        if(record==null){chestStable(player);return;}
        Holder holder=frame(player,Page.PET,id,54,"✦ "+record.name()+" · คลิกปุ่มเพื่อสั่ง");
        put(holder,0,art("menu_back","« กลับคอก",NamedTextColor.YELLOW,List.of()),()->chestStable(player));
        put(holder,4,icon(stable,record,null),null);
        put(holder,8,art("skilltree_close","ปิด",NamedTextColor.RED,List.of()),player::closeInventory);
        int need=breeding.bondNeeded();
        put(holder,10,art("menu_bond_"+tenths(record.bond,PetRecord.BOND_MAX),"ความผูกพัน "+record.bond+"/"+PetRecord.BOND_MAX,TextColor.color(0xff70b0),
            List.of("เพิ่มขึ้นเมื่อพาออกมาเดินด้วยกันและป้อนอาหาร",record.bond>=need?"✔ ถึง "+need+" แล้ว ผสมพันธุ์ได้":"ผสมพันธุ์ต้องถึง "+need)),null);
        PetCatalog.Stage next=next(record);
        put(holder,11,art("menu_growth_"+(next==null?10:tenths(record.growth,companions.threshold(next))),record.stage.thai+(next==null?" · โตเต็มวัย":" · "+record.growth+"/"+companions.threshold(next)),
            TextColor.color(0x5ee87a),List.of(next==null?"โตเต็มที่แล้ว":"โตเป็น"+next.thai+"เมื่อเต็มวง","เติบโตเมื่ออยู่ด้วยกันนอกคอก")),null);
        double max=PetSkills.maxHealth(record),health=companions.health(record);
        put(holder,12,art("menu_health_"+tenths(health,max),"พลังชีวิต "+(int)Math.ceil(health)+"/"+(int)max,TextColor.color(0xff5454),
            companions.faintLeft(record)>0?List.of("✖ สลบ · พักฟื้นอีก "+companions.recovery(record)):List.of("ฟื้นเองเมื่อพ้นการต่อสู้ 5 วินาที")),null);
        put(holder,14,art("menu_skills","สกิลของ "+record.name(),NamedTextColor.LIGHT_PURPLE,skillPage(record)),null);
        put(holder,15,art("menu_stats","ค่าพลัง",NamedTextColor.AQUA,List.of("พลังชีวิต "+record.vitality+"/"+PetRecord.STAT_MAX+" · เพิ่มเลือด",
            "ว่องไว "+record.agility+"/"+PetRecord.STAT_MAX+" · กัดถี่ขึ้น","มนตรา "+record.affinity+"/"+PetRecord.STAT_MAX+" · กัดแรงขึ้น",
            "ขนาด "+Math.round(record.size*100)+"% · รุ่นที่ "+record.generation+(record.aura?" · ✦ ออร่า":""),"ลูกได้ค่าพลังจากพ่อแม่")),null);
        boolean following=stable.out&&id.equals(stable.active);
        put(holder,28,art(following?"menu_dismiss":"menu_summon",following?"เก็บเข้าคอก":"เรียกออกมา",following?NamedTextColor.GOLD:NamedTextColor.GREEN,
            List.of(following?companions.state(player):"ติดตามคุณไปทุกที่ เติบโตเมื่ออยู่ด้วยกัน")),()->{toggleSummon(player,id);chestPet(player,id);});
        put(holder,30,art("menu_rename","ตั้งชื่อ",NamedTextColor.AQUA,List.of("เปิดป้ายให้พิมพ์ชื่อ (ไม่ต้องใช้แชท)",
            "หรือคลิกขวาที่สัตว์ด้วย Name Tag ที่ตั้งชื่อแล้ว","หรือพิมพ์ /pet name <ชื่อ>")),()->promptRename(player,id));
        String unfit=unfit(stable,record,null,System.currentTimeMillis());
        put(holder,32,art(unfit==null?"menu_breed":"menu_breed_off","ผสมพันธุ์ · หาคู่",unfit==null?NamedTextColor.LIGHT_PURPLE:NamedTextColor.GRAY,
            unfit==null?List.of("เปิดรังพร้อมเลือกตัวนี้ไว้แล้ว","จากนั้นเลือกคู่ชนิดเดียวกันอีกหนึ่งตัว"):List.of("✖ "+unfit,"เปิดรังเพื่อดูเงื่อนไขทั้งหมด")),()->{toNest(player,id);chestNest(player);});
        boolean releasing=armed(holder,"release");
        put(holder,34,art(releasing?"menu_release_armed":"menu_release",releasing?"คลิกอีกครั้งเพื่อปล่อย "+record.name():"ปล่อยคืนธรรมชาติ",NamedTextColor.RED,
            List.of("ลบออกจากคอกถาวร","คลิกสองครั้งภายใน 5 วินาทีเพื่อยืนยัน")),null);
        holder.actions.put(34,()->{if(confirm(player,holder,"release",()->chestPet(player,id))){release(player,id);chestStable(player);}else chestPet(player,id);});
        PetSkills.Skill skill=PetSkills.of(record.species);
        String[][] modes={{"follow","เดินตามคุณ ไม่ร่วมต่อสู้"},{"stay","ยืนรอตรงที่อยู่ มองตามคุณ","ถ้าคุณไปไกลเกิน 64 บล็อกจะวิ่งตามมา"},
            {"perch","ตัวเล็กลงแล้วเกาะบนหัวคุณ","ไม่บังการคลิกหรือขุด"},{"assist","ไล่กัดสิ่งที่คุณตีหรือมอนที่หมายหัวคุณ",
            skill==null?"":"⚔ "+skill.traitName()+": "+skill.trait(),"ศัตรูสวนกลับได้ · สลบแล้วพักฟื้น 1 วันในเกม"}};
        for(int i=0;i<modes.length;i++){
            String mode=modes[i][0];boolean current=mode.equals(record.mode);
            List<String> lore=new ArrayList<>(Arrays.asList(modes[i]).subList(1,modes[i].length));
            lore.removeIf(String::isEmpty);lore.add(current?"● ใช้อยู่":"คลิกเพื่อเลือก");
            ItemStack item=art("menu_mode_"+mode+(current?"_on":""),"โหมด"+PetSkills.modeName(mode),current?NamedTextColor.GOLD:NamedTextColor.WHITE,lore);
            put(holder,37+i*2,current?glint(item):item,()->{setMode(player,id,mode);chestPet(player,id);});
        }
        show(player,holder);
    }
    private void setMode(Player player,UUID id,String mode){
        PetRecord record=companions.stable(player).get(id);if(record==null)return;
        String problem=companions.mode(player,id,mode);
        plugin.message(player,problem!=null?problem:record.name()+" · โหมด"+PetSkills.modeName(mode));
    }
    /** One screen: the pair, what is still missing, and every stable pet to pick from. */
    void chestNest(Player player){
        PetStable stable=companions.stable(player);UUID[] draft=draft(player);
        Holder holder=frame(player,Page.NEST,null,54,"✦ รังผสมพันธุ์ · เลือกคู่จากด้านล่าง");
        boolean busy=stable.nestBusy();long now=System.currentTimeMillis();
        PetRecord a=stable.get(busy?stable.nestA:draft[0]),b=stable.get(busy?stable.nestB:draft[1]);
        put(holder,0,art("menu_back","« กลับคอก",NamedTextColor.YELLOW,List.of()),()->chestStable(player));
        put(holder,8,art("skilltree_close","ปิด",NamedTextColor.RED,List.of()),player::closeInventory);
        eggButton(player,holder,4,()->chestNest(player));
        for(int side=0;side<2;side++){
            int slot=side==0?11:15;PetRecord record=side==0?a:b;final int index=side;
            if(record!=null)put(holder,slot,icon(stable,record,busy?"อยู่ในรัง":"คลิกเพื่อเอาออกจากคู่"),busy?null:()->{draft[index]=null;chestNest(player);});
            else put(holder,slot,art("menu_pick","ตัวที่ "+(side+1)+" · ยังไม่ได้เลือก",NamedTextColor.GRAY,List.of("คลิกสัตว์แถวล่างเพื่อเลือก")),null);
        }
        if(busy){
            if(now<stable.nestReady)put(holder,13,art("menu_nest_wait","กำลังฟูมฟัก",NamedTextColor.AQUA,List.of("ไข่พร้อมใน "+minutes(stable.nestReady-now),"กลับมาเก็บไข่ที่หน้านี้")),()->chestNest(player));
            else put(holder,13,art("menu_egg_ready","คลิกเก็บไข่!",NamedTextColor.GREEN,List.of("พกไข่ไว้ขณะออนไลน์ "+minutes(breeding.incubateMillis()),"แล้วคลิกขวาไข่เพื่อฟัก","ต้องมีที่ว่างในกระเป๋า")),()->{collect(player);chestNest(player);});
            boolean cancelling=armed(holder,"cancel");
            put(holder,6,art(cancelling?"menu_cancel_armed":"menu_cancel",cancelling?"คลิกอีกครั้งเพื่อยกเลิกรัง":"ยกเลิกรัง",NamedTextColor.RED,List.of("ไม่คืนอาหารที่ใช้ไป","คลิกสองครั้งภายใน 5 วินาทีเพื่อยืนยัน")),null);
            holder.actions.put(6,()->{if(confirm(player,holder,"cancel",()->chestNest(player))){breeding.cancel(player);plugin.message(player,"ยกเลิกรังแล้ว");}chestNest(player);});
            put(holder,31,art("menu_nest","รังไม่ว่าง",NamedTextColor.GRAY,List.of("เก็บไข่หรือยกเลิกรังก่อนเลือกคู่ใหม่")),null);
            show(player,holder);return;
        }
        List<Check> checks=checks(player,stable,a,b,now);boolean ready=checks.stream().allMatch(c->Boolean.TRUE.equals(c.ok()));
        String problem=breeding.check(stable,a,b,now);ready&=problem==null;
        Material food=a!=null?taming.food(a.species):b!=null?taming.food(b.species):null;
        if(ready)put(holder,13,art("menu_breed","เริ่มผสมพันธุ์",NamedTextColor.LIGHT_PURPLE,List.of("ใช้ "+PetTaming.foodName(food)+" ×"+breeding.foodCost(),"ไข่พร้อมใน "+minutes(breeding.nestMillis()))),()->{startNest(player);chestNest(player);});
        else{
            String first=checks.stream().filter(c->!Boolean.TRUE.equals(c.ok())).map(c->Boolean.FALSE.equals(c.ok())?"✖ "+c.label()+" · "+c.detail():"เลือกสัตว์ให้ครบ 2 ตัว").findFirst().orElse(problem==null?"":problem);
            put(holder,13,art("menu_breed_off","ยังเริ่มไม่ได้",NamedTextColor.GRAY,List.of(first,"ดูเงื่อนไขแถวถัดไป")),null);
        }
        int slot=19;
        for(Check check:checks){
            String state=check.ok()==null?"wait":check.ok()?"ok":"no";
            String mark=check.ok()==null?"… ":check.ok()?"✔ ":"✖ ";
            NamedTextColor color=check.ok()==null?NamedTextColor.GRAY:check.ok()?NamedTextColor.GREEN:NamedTextColor.RED;
            ItemStack item=check.kind().equals("food")&&food!=null?button(food,mark+check.label(),color,List.of(check.detail()))
                :check.kind().equals("food")?art("menu_req_daily_wait",mark+check.label(),color,List.of(check.detail()))
                :art("menu_req_"+check.kind()+"_"+state,mark+check.label(),color,List.of(check.detail()));
            put(holder,slot++,item,null);
        }
        put(holder,27,art("menu_label_pets","เลือกคู่จากสัตว์ในคอก",NamedTextColor.GOLD,List.of("สีปกติ = เลือกได้ · สีเทา = เลือกไม่ได้ (ดูเหตุผล)","คลิกตัวที่เลือกแล้วด้านบนเพื่อเอาออก")),null);
        PetRecord other=a!=null?a:b;
        List<PetRecord> pets=new ArrayList<>(stable.pets.values());
        // Pickable pets first.
        pets.sort(Comparator.comparing(r->unfit(stable,r,other,now)!=null));
        int shown=0;
        for(PetRecord record:pets){
            if(shown>=CANDIDATES.length)break;
            if(record==a||record==b)continue;
            String why=unfit(stable,record,other,now);
            List<String> lore=new ArrayList<>(List.of(record.species.thai+" · "+record.stage.thai,"ความผูกพัน "+record.bond+"/"+PetRecord.BOND_MAX,""));
            lore.add(why==null?"คลิกเพื่อเลือกเป็นคู่":"✖ "+why);
            ItemStack item=art(portrait(record,why!=null),record.name(),why==null?NamedTextColor.GOLD:NamedTextColor.DARK_GRAY,lore);
            put(holder,CANDIDATES[shown++],item,()->{
                if(why!=null){plugin.message(player,record.name()+": "+why);player.playSound(player.getLocation(),Sound.BLOCK_NOTE_BLOCK_BASS,.7f,.6f);}
                else{if(draft[0]==null)draft[0]=record.id;else if(draft[1]==null)draft[1]=record.id;else draft[1]=record.id;}
                chestNest(player);
            });
        }
        if(shown==0)put(holder,31,art("menu_socket","ยังไม่มีตัวอื่นในคอก",NamedTextColor.GRAY,List.of("ผูกมิตรสัตว์ชนิดเดียวกันเพิ่มอีกตัว")),null);
        show(player,holder);
    }
    @EventHandler(priority=EventPriority.LOWEST)
    public void click(InventoryClickEvent event){
        if(!(event.getView().getTopInventory().getHolder() instanceof Holder holder))return;
        event.setCancelled(true);
        if(!(event.getWhoClicked() instanceof Player player)||event.getClickedInventory()!=event.getView().getTopInventory())return;
        Runnable action=holder.actions.get(event.getRawSlot());
        if(action==null)return;
        player.playSound(player.getLocation(),Sound.UI_BUTTON_CLICK,.5f,1.3f);
        Bukkit.getScheduler().runTask(plugin,()->{if(player.isOnline())action.run();});
    }
    @EventHandler(priority=EventPriority.LOWEST)
    public void drag(InventoryDragEvent event){if(event.getView().getTopInventory().getHolder() instanceof Holder)event.setCancelled(true);}

    // ---- renaming ----
    /**
     * Opens a sign editor on a client-only sign a few blocks away; the typed lines come back as
     * an unchecked sign change. Chat can be off (missing profile key, chat hidden) and this still works.
     */
    void promptRename(Player player,UUID id){
        if(bedrock(player)){formRename(player,id);return;}
        PetRecord record=companions.stable(player).get(id);if(record==null)return;
        player.closeInventory();
        restoreSign(player);
        Location block=signSpot(player);
        if(block==null){plugin.message(player,"เปิดป้ายตั้งชื่อไม่ได้ตรงนี้ · ใช้ /pet name <ชื่อ> หรือคลิกขวาด้วย Name Tag แทน");return;}
        signs.put(player.getUniqueId(),new SignPrompt(id,block));
        player.sendBlockChange(block,Material.OAK_SIGN.createBlockData());
        player.sendSignChange(block,List.of(Component.text(record.name==null?"":record.name),Component.empty(),
            Component.text("^ ชื่อสัตว์เลี้ยง ^"),Component.text("สูงสุด 16 ตัวอักษร")));
        player.openVirtualSign(Position.block(block),Side.FRONT);
    }
    /** Within sign-editing reach and free of block entities, whose client copy the fake sign would wipe. */
    private static Location signSpot(Player player){
        Location base=player.getLocation();World world=base.getWorld();
        for(int dy:new int[]{-2,-3,3,4,-4}){
            int y=base.getBlockY()+dy;
            if(y<world.getMinHeight()||y>=world.getMaxHeight())continue;
            Location block=new Location(world,base.getBlockX(),y,base.getBlockZ());
            if(!(block.getBlock().getState(false) instanceof TileState))return block;
        }
        return null;
    }
    private void restoreSign(Player player){
        SignPrompt prompt=signs.remove(player.getUniqueId());
        if(prompt!=null&&player.isOnline())player.sendBlockChange(prompt.block,prompt.block.getBlock().getBlockData());
    }
    @EventHandler public void sign(UncheckedSignChangeEvent event){
        Player player=event.getPlayer();SignPrompt prompt=signs.get(player.getUniqueId());
        var at=event.getEditedBlockPosition();
        if(prompt==null||at.blockX()!=prompt.block.getBlockX()||at.blockY()!=prompt.block.getBlockY()||at.blockZ()!=prompt.block.getBlockZ())return;
        event.setCancelled(true);restoreSign(player);
        var plain=PlainTextComponentSerializer.plainText();var lines=event.lines();
        String typed=((lines.size()>0?plain.serialize(lines.get(0)):"")+" "+(lines.size()>1?plain.serialize(lines.get(1)):"")).trim();
        PetRecord record=companions.stable(player).get(prompt.pet);
        if(record==null)return;
        if(typed.isEmpty()||typed.equals(record.name)){plugin.message(player,"ชื่อไม่เปลี่ยน");return;}
        applyName(player,prompt.pet,typed);
    }
    /** Right-click with a Name Tag: a named tag names the pet (and is used up); a blank one opens the sign. */
    void nameTag(Player player,UUID id,ItemStack hand){
        var meta=hand.getItemMeta();
        if(meta==null||!meta.hasDisplayName()){promptRename(player,id);return;}
        String name=PlainTextComponentSerializer.plainText().serialize(meta.displayName());
        if(applyName(player,id,name)&&player.getGameMode()!=GameMode.CREATIVE)hand.setAmount(hand.getAmount()-1);
    }
    static String sanitize(String raw){
        if(raw==null)return null;
        String cleaned=raw.replaceAll("[§&][0-9a-fk-orA-FK-OR]","").replaceAll("\\p{Cntrl}","").replaceAll("\\s+"," ").trim();
        if(cleaned.isEmpty())return null;
        int end=cleaned.offsetByCodePoints(0,Math.min(16,cleaned.codePointCount(0,cleaned.length())));
        return cleaned.substring(0,end).trim();
    }
    private boolean applyName(Player player,UUID id,String raw){
        PetStable stable=companions.stable(player);PetRecord record=stable.get(id);
        if(record==null)return false;
        if(raw==null||raw.equals("ยกเลิก")||raw.equalsIgnoreCase("cancel")){plugin.message(player,"ยกเลิกการตั้งชื่อ");return false;}
        String name=sanitize(raw);
        if(name==null){plugin.message(player,"ชื่อว่างใช้ไม่ได้");return false;}
        record.name=name;stable.dirty=true;
        PetBody body=companions.body(player.getUniqueId());
        if(body!=null&&body.record==record){
            body.shell.customName(Component.text(name+" · "+record.species.display+" · "+record.stage.thai,NamedTextColor.AQUA));
            body.updateNametag();
        }
        plugin.message(player,"ตั้งชื่อเป็น "+name+" แล้ว");
        return true;
    }
    @EventHandler public void quit(PlayerQuitEvent event){
        UUID id=event.getPlayer().getUniqueId();signs.remove(id);drafts.remove(id);
    }

    // ---- Bedrock forms ----
    private boolean send(Player player,org.geysermc.cumulus.form.Form form){return FloodgateApi.getInstance().sendForm(player.getUniqueId(),form);}
    private SimpleForm.Builder handle(SimpleForm.Builder builder,List<Runnable> actions){
        return builder.validResultHandler(response->{
            int index=response.clickedButtonId();
            if(index>=0&&index<actions.size())Bukkit.getScheduler().runTask(plugin,actions.get(index));
        });
    }
    boolean formStable(Player player){
        PetStable stable=companions.stable(player);PetRecord active=stable.activeRecord();
        List<Runnable> actions=new ArrayList<>();
        SimpleForm.Builder builder=SimpleForm.builder().title("คอกสัตว์เลี้ยง "+stable.pets.size()+"/"+stable.slots)
            .content((active==null?"ยังไม่มีตัวที่ติดตาม":active.name()+" · "+companions.state(player))+"\n"+nestStatus(stable)+"\n§7แตะสัตว์เพื่อดูข้อมูล เรียก ตั้งชื่อ หรือผสมพันธุ์");
        for(PetRecord record:stable.pets.values()){
            builder.button(record.name()+(record.id.equals(stable.active)&&stable.out?" ★":"")+"\n§7"+record.species.thai+" · "+record.stage.thai+" · ผูกพัน "+record.bond,FormImage.Type.PATH,formImage(record));
            actions.add(()->formPet(player,record.id));
        }
        if(active!=null){builder.button(stable.out?"เก็บ "+active.name()+" เข้าคอก":"เรียก "+active.name(),FormImage.Type.PATH,stable.out?"textures/menu/dismiss":"textures/menu/summon");actions.add(()->{toggleSummon(player,active.id);formStable(player);});}
        builder.button("รังผสมพันธุ์\n§7"+nestStatus(stable),FormImage.Type.PATH,"textures/menu/nest");actions.add(()->formNest(player));
        builder.button("ซ่อนสัตว์เลี้ยงของคนอื่น: "+(stable.hideOthers?"เปิด":"ปิด"));actions.add(()->{stable.hideOthers=!stable.hideOthers;stable.dirty=true;formStable(player);});
        builder.button("วิธีผูกมิตร · อาหารที่ชอบ",FormImage.Type.PATH,"textures/skilltree/help");actions.add(()->formGuide(player));
        return send(player,handle(builder,actions).build());
    }
    private void formGuide(Player player){
        StringBuilder text=new StringBuilder("หาสัตว์ป่าใน Evergarden แล้วคลิกขวาพร้อมอาหารที่มันชอบจนความไว้ใจครบ 100%\nร่างกลางและโตเต็มวัยต้องมีเชือกจูง (Lead) พากลับ\n");
        for(var species:WildPetSpawns.eligibleSpecies())text.append("\n§e").append(species.thai).append(" (").append(species.display).append(")\n§f  ชอบ: §a").append(PetTaming.foodNameThai(taming.food(species)));
        send(player,handle(SimpleForm.builder().title("วิธีผูกมิตร · อาหารที่ชอบ").content(text.toString()).button("« กลับ"),List.of(()->formStable(player))).build());
    }
    boolean formPet(Player player,UUID id){
        PetStable stable=companions.stable(player);PetRecord record=stable.get(id);
        if(record==null)return formStable(player);
        boolean following=stable.out&&id.equals(stable.active);
        List<Runnable> actions=new ArrayList<>();
        SimpleForm.Builder builder=SimpleForm.builder().title(record.name()).content(String.join("\n",describe(stable,record)));
        builder.button(following?"เก็บเข้าคอก":"เรียกออกมา",FormImage.Type.PATH,following?"textures/menu/dismiss":"textures/menu/summon");actions.add(()->{toggleSummon(player,id);formPet(player,id);});
        builder.button("โหมด: "+PetSkills.modeName(record.mode)+"\n§7ติดตาม · อยู่กับที่ · เกาะหัว · ช่วยสู้",FormImage.Type.PATH,"textures/menu/mode_"+record.mode+"_on");actions.add(()->formMode(player,id));
        builder.button("สกิลและพลังต่อสู้",FormImage.Type.PATH,"textures/menu/skills");actions.add(()->send(player,handle(SimpleForm.builder().title("สกิลของ "+record.name())
            .content(String.join("\n",skillPage(record))).button("« กลับ"),List.of(()->formPet(player,id))).build()));
        builder.button("ตั้งชื่อ",FormImage.Type.PATH,"textures/menu/rename");actions.add(()->formRename(player,id));
        String unfit=unfit(stable,record,null,System.currentTimeMillis());
        builder.button("ผสมพันธุ์ · หาคู่\n§7"+(unfit==null?"เลือกคู่ชนิดเดียวกันอีกหนึ่งตัว":"✖ "+unfit),FormImage.Type.PATH,unfit==null?"textures/menu/breed":"textures/menu/breed_off");
        actions.add(()->{toNest(player,id);formNest(player);});
        builder.button("§cปล่อยคืนธรรมชาติ",FormImage.Type.PATH,"textures/menu/release");actions.add(()->send(player,ModalForm.builder().title("ปล่อย "+record.name()+"?")
            .content("สัตว์ตัวนี้จะหายไปจากคอกถาวร").button1("ปล่อย").button2("ยกเลิก")
            .validResultHandler(answer->Bukkit.getScheduler().runTask(plugin,()->{if(answer.clickedFirst())release(player,id);formStable(player);})).build()));
        builder.button("« กลับ");actions.add(()->formStable(player));
        return send(player,handle(builder,actions).build());
    }
    private void formMode(Player player,UUID id){
        PetRecord record=companions.stable(player).get(id);if(record==null)return;
        String[][] modes={{"follow","เดินตามคุณ ไม่ร่วมต่อสู้"},{"stay","ยืนรอตรงที่อยู่ ไปไกลเกิน 64 บล็อกจะวิ่งตามมา"},
            {"perch","ตัวเล็กลงแล้วเกาะบนหัวคุณ"},{"assist","ไล่กัดศัตรู · สวนกลับได้ สลบแล้วพักฟื้น 1 วันในเกม"}};
        List<Runnable> actions=new ArrayList<>();
        SimpleForm.Builder builder=SimpleForm.builder().title("โหมดของ "+record.name()).content("ตอนนี้: "+PetSkills.modeName(record.mode));
        for(String[] mode:modes){
            boolean current=mode[0].equals(record.mode);
            builder.button((current?"● ":"")+PetSkills.modeName(mode[0])+"\n§7"+mode[1],FormImage.Type.PATH,"textures/menu/mode_"+mode[0]+(current?"_on":""));
            actions.add(()->{setMode(player,id,mode[0]);formPet(player,id);});
        }
        builder.button("« กลับ");actions.add(()->formPet(player,id));
        send(player,handle(builder,actions).build());
    }
    private void formRename(Player player,UUID id){
        PetRecord record=companions.stable(player).get(id);if(record==null)return;
        send(player,CustomForm.builder().title("ตั้งชื่อ").input("ชื่อใหม่ (สูงสุด 16 ตัวอักษร)",record.species.display,record.name())
            .validResultHandler(response->{String name=response.asInput(0);
                Bukkit.getScheduler().runTask(plugin,()->{applyName(player,id,name);formPet(player,id);});}).build());
    }
    private void formNest(Player player){
        PetStable stable=companions.stable(player);UUID[] draft=draft(player);boolean busy=stable.nestBusy();
        PetRecord a=stable.get(busy?stable.nestA:draft[0]),b=stable.get(busy?stable.nestB:draft[1]);
        List<Runnable> actions=new ArrayList<>();long now=System.currentTimeMillis();
        StringBuilder content=new StringBuilder(nestStatus(stable));
        List<String> eggs=eggLines(player);
        if(!eggs.isEmpty())content.append("\n§b").append(String.join("\n§b",eggs));
        boolean ready=false;
        if(!busy){
            List<Check> checks=checks(player,stable,a,b,now);
            ready=checks.stream().allMatch(c->Boolean.TRUE.equals(c.ok()))&&breeding.check(stable,a,b,now)==null;
            content.append("\n\n§fตัวที่ 1: ").append(a==null?"§7ยังไม่เลือก":a.name()).append("\n§fตัวที่ 2: ").append(b==null?"§7ยังไม่เลือก":b.name()).append("\n");
            for(Check check:checks)content.append("\n").append(check.ok()==null?"§7… ":check.ok()?"§a✔ ":"§c✖ ").append(check.label())
                .append(Boolean.TRUE.equals(check.ok())?"":"§7 · "+check.detail());
        }
        SimpleForm.Builder builder=SimpleForm.builder().title("รังผสมพันธุ์").content(content.toString());
        long hatchable=breeding.carried(player).stream().filter(e->e.percent()>=100).count();
        if(hatchable>0){
            builder.button("ฟักไข่!\n§7พร้อม "+hatchable+" ฟอง",FormImage.Type.PATH,"textures/items/pet_egg");
            actions.add(()->{hatchCarried(player);formNest(player);});
        }
        if(!busy){
            for(int side=0;side<2;side++){
                PetRecord record=side==0?a:b;final int chosen=side;
                if(record==null)builder.button("เลือกตัวที่ "+(side+1),FormImage.Type.PATH,"textures/menu/pick");
                else builder.button("ตัวที่ "+(side+1)+": "+record.name()+"\n§7แตะเพื่อเปลี่ยน",FormImage.Type.PATH,formImage(record));
                actions.add(()->formPick(player,chosen));
            }
            if(ready){
                builder.button("เริ่มผสมพันธุ์\n§7"+PetTaming.foodName(taming.food(a.species))+" ×"+breeding.foodCost(),FormImage.Type.PATH,"textures/menu/breed");
                actions.add(()->{startNest(player);formNest(player);});
            }
        }else if(now>=stable.nestReady){builder.button("เก็บไข่!",FormImage.Type.PATH,"textures/menu/egg_ready");actions.add(()->{collect(player);formNest(player);});}
        else{builder.button("รีเฟรช",FormImage.Type.PATH,"textures/menu/nest_wait");actions.add(()->formNest(player));}
        if(busy){builder.button("§cยกเลิกรัง",FormImage.Type.PATH,"textures/menu/cancel");actions.add(()->send(player,ModalForm.builder().title("ยกเลิกรัง?").content("ไม่คืนอาหารที่ใช้ไป")
            .button1("ยกเลิกรัง").button2("ไม่").validResultHandler(answer->Bukkit.getScheduler().runTask(plugin,()->{if(answer.clickedFirst())breeding.cancel(player);formNest(player);})).build()));}
        builder.button("« กลับ");actions.add(()->formStable(player));
        send(player,handle(builder,actions).build());
    }
    private void formPick(Player player,int side){
        PetStable stable=companions.stable(player);UUID[] draft=draft(player);long now=System.currentTimeMillis();
        PetRecord other=stable.get(draft[1-side]);
        List<Runnable> actions=new ArrayList<>();List<String> unfit=new ArrayList<>();
        SimpleForm.Builder builder=SimpleForm.builder().title("เลือกตัวที่ "+(side+1));
        for(PetRecord record:stable.pets.values()){
            if(record==other)continue;
            String why=unfit(stable,record,other,now);
            if(why!=null){unfit.add("§7"+record.name()+" · "+why);continue;}
            builder.button(record.name()+"\n§7"+record.species.thai+" · ผูกพัน "+record.bond,FormImage.Type.PATH,formImage(record));
            actions.add(()->{draft[side]=record.id;formNest(player);});
        }
        builder.content(actions.isEmpty()?"ยังไม่มีตัวที่เลือกได้ · ต้องโตเต็มวัย ชนิดเดียวกัน และความผูกพันถึง "+breeding.bondNeeded()
            :"ตัวโตเต็มวัย ชนิดเดียวกับอีกตัว ความผูกพันถึง "+breeding.bondNeeded()+(unfit.isEmpty()?"":"\n\nเลือกไม่ได้:\n"+String.join("\n",unfit)));
        if(draft[side]!=null){builder.button("เอาตัวที่ "+(side+1)+" ออก");actions.add(()->{draft[side]=null;formNest(player);});}
        builder.button("« กลับ");actions.add(()->formNest(player));
        send(player,handle(builder,actions).build());
    }

    // ---- /pet ----
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
        if(!(sender instanceof Player player)){plugin.message(sender,"ใช้คำสั่งนี้ในเกม");return true;}
        String action=args.length==0?"menu":args[0].toLowerCase(Locale.ROOT);
        PetStable stable=companions.stable(player);
        switch(action){
            case "summon","call"->{
                PetRecord target=stable.activeRecord();
                if(args.length>1){
                    try{int index=Integer.parseInt(args[1])-1;target=index>=0&&index<stable.pets.size()?new ArrayList<>(stable.pets.values()).get(index):null;}
                    catch(NumberFormatException named){target=stable.pets.values().stream().filter(r->r.name().equalsIgnoreCase(args[1])).findFirst().orElse(null);}
                }else if(target==null&&!stable.pets.isEmpty())target=stable.pets.values().iterator().next();
                if(target==null){plugin.message(player,"ไม่พบสัตว์เลี้ยงตัวนี้ · ดูรายการด้วย /pet list");return true;}
                String problem=companions.summon(player,target.id);
                plugin.message(player,problem!=null?problem:target.name()+" ออกมาเดินกับคุณแล้ว");
            }
            case "dismiss"->{companions.dismiss(player);plugin.message(player,"สัตว์เลี้ยงกลับเข้าคอกแล้ว");}
            case "name","rename"->{
                PetRecord target=stable.activeRecord();
                if(target==null){plugin.message(player,"ยังไม่มีตัวที่ติดตาม · เรียกออกมาก่อนด้วย /pet summon");return true;}
                if(args.length<2)promptRename(player,target.id);
                else applyName(player,target.id,String.join(" ",Arrays.copyOfRange(args,1,args.length)));
            }
            case "mode"->{
                PetRecord target=stable.activeRecord();
                if(target==null){plugin.message(player,"ยังไม่มีตัวที่ติดตาม · เรียกออกมาก่อนด้วย /pet summon");return true;}
                if(args.length<2){plugin.message(player,target.name()+" · โหมด"+PetSkills.modeName(target.mode)+" · เปลี่ยนด้วย /pet mode <follow|stay|perch|assist>");return true;}
                setMode(player,target.id,args[1].toLowerCase(Locale.ROOT));
            }
            case "skills","skill","info"->{
                PetRecord target=stable.activeRecord();
                if(target==null){plugin.message(player,"ยังไม่มีตัวที่ติดตาม");return true;}
                plugin.message(player,target.name()+" · "+target.species.display);
                for(String line:skillPage(target))player.sendMessage(Component.text(line,NamedTextColor.GRAY));
            }
            case "foods","food","guide","tame","taming"->{
                plugin.message(player,"=== รายการอาหารสำหรับเทมสัตว์เลี้ยง "+WildPetSpawns.eligibleSpecies().size()+" ชนิด ===");
                for(var sp:WildPetSpawns.eligibleSpecies()){
                    Material f=taming.food(sp);
                    player.sendMessage(Component.text("✦ "+sp.thai+" ("+sp.display+"): ",NamedTextColor.AQUA)
                        .append(Component.text(PetTaming.foodNameThai(f),NamedTextColor.YELLOW)));
                }
                player.sendMessage(Component.text("วิธีเทม: คลิกขวาป้อนอาหารจนไว้ใจครบ 100% (ตัวโตต้องมีเชือกจูง Lead พากลับ)",NamedTextColor.GRAY));
            }
            case "nest","breed"->{if(bedrock(player))formNest(player);else chestNest(player);}
            case "hide"->{stable.hideOthers=!stable.hideOthers;stable.dirty=true;plugin.message(player,"ซ่อนสัตว์เลี้ยงของคนอื่น: "+(stable.hideOthers?"เปิด":"ปิด"));}
            case "list"->{
                int index=1;plugin.message(player,"คอกสัตว์เลี้ยง "+stable.pets.size()+"/"+stable.slots+" · "+companions.state(player));
                for(PetRecord record:stable.pets.values())player.sendMessage(Component.text(" "+(index++)+". "+record.name()+" · "+record.species.display+" · "+record.stage.thai
                    +" · ผูกพัน "+record.bond+" · "+PetSkills.modeName(record.mode)+(companions.faintLeft(record)>0?" · สลบ อีก "+companions.recovery(record):""),NamedTextColor.GRAY));
            }
            default->open(player);
        }
        return true;
    }
    @Override public List<String> onTabComplete(CommandSender sender,Command command,String alias,String[] args){
        if(args.length==2&&args[0].equalsIgnoreCase("mode"))return PetSkills.MODES.stream().filter(v->v.startsWith(args[1].toLowerCase(Locale.ROOT))).toList();
        if(args.length!=1)return List.of();
        return List.of("summon","dismiss","list","name","mode","skills","nest","hide","foods","guide").stream().filter(v->v.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
    }
    @Override public void close(){HandlerList.unregisterAll(this);}
}
