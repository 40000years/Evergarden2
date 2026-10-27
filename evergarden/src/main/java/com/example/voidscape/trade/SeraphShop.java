package com.example.voidscape.trade;

import com.example.advancemagic.AdvanceMagicPlugin;
import com.example.advancemagic.elemental.ElementalSpell;
import com.example.advancemagic.elemental.StaffSpellService;
import com.example.advancemagic.item.ElementalStaffService.Element;
import com.example.advancemagic.item.LegendaryWeaponService.Weapon;
import com.example.advancemagic.item.ArmorSetService;
import com.example.advancemagic.item.ArmorSetService.ArmorSet;
import com.example.advancemagic.item.ArmorSetService.Piece;
import com.example.advancemagic.mana.ManaPotionService.Quality;
import com.example.voidscape.VoidscapePlugin;
import com.example.voidscape.boss.JudgePackService;
import com.example.voidscape.boss.BossArmorCores;
import com.example.voidscape.boss.BossArmorCores.Core;
import com.example.voidscape.boss.SoulOrbs;
import com.example.voidscape.boss.SoulOrbs.Orb;
import io.papermc.paper.event.player.PlayerPurchaseEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.*;
import org.geysermc.cumulus.form.SimpleForm;
import org.geysermc.cumulus.util.FormImage;
import org.geysermc.floodgate.api.FloodgateApi;

import java.util.*;
import java.util.function.Supplier;

/**
 * The Seraph's exchange, in the villager trading screen players already know. A small menu leads
 * to magic and legends, including main-boss armor exchanges. Seraph Feathers are the main price; Soul
 * Orbs stay the special ingredient for higher goods. Per-player daily and weekly stock keeps
 * diligent players ahead without flooding the server.
 */
public final class SeraphShop implements Listener {
    /** One trade line. {@code limit} is shared by the whole {@code category} per day or week; 0 = unlimited. */
    /**
     * One trade line. {@code limit} is shared by the whole {@code category} per day or week; 0 = unlimited.
     * {@code special} is the villager-style discount on the first ingredient (shown struck through).
     */
    record Offer(String id,String category,int limit,boolean weekly,ItemStack first,ItemStack second,Supplier<ItemStack> result,int special){
        Offer(String id,String category,int limit,boolean weekly,ItemStack first,ItemStack second,Supplier<ItemStack> result){
            this(id,category,limit,weekly,first,second,result,0);
        }
    }
    /** The sale running for one promotion period: some staff elements plus one rotating category. */
    record Promotion(long period,Set<Element> staffs,String other,long endsTick){
        boolean onSale(Element element){return staffs.contains(element);}
    }
    static final List<String> SALE_CATEGORIES=List.of("scrolls","potions","legend-core");
    private record Session(Merchant merchant,List<Offer> offers){}
    static final class Hub implements InventoryHolder {
        final Map<Integer,Runnable> actions=new HashMap<>();Inventory inventory;
        @Override public Inventory getInventory(){return inventory;}
    }
    private static final Map<Element,List<String>> DEFAULT_ORBS=new EnumMap<>(Map.of(
        Element.FIRE,List.of("phlegethon","acheron"),Element.WIND,List.of("banshee"),Element.EARTH,List.of("banshee"),
        Element.ICE,List.of("cocytus"),Element.WATER,List.of("cocytus"),Element.LIGHTNING,List.of("malefeasance"),
        Element.LIGHT,List.of("styx"),Element.DARK,List.of("malefeasance")));
    private final VoidscapePlugin plugin;
    private final SeraphTrader trader;
    private final SeraphFeathers feathers;
    private final SanctuaryMaps maps;
    private final Map<UUID,Session> sessions=new HashMap<>();
    private final Map<UUID,Long> lastOpen=new HashMap<>();

    SeraphShop(VoidscapePlugin plugin,SeraphTrader trader,SeraphFeathers feathers){
        this.plugin=plugin;this.trader=trader;this.feathers=feathers;maps=new SanctuaryMaps(plugin);
    }
    public SanctuaryMaps maps(){return maps;}
    public void close(){sessions.clear();lastOpen.clear();maps.close();}

    private static AdvanceMagicPlugin magic(){
        var addon=Bukkit.getPluginManager().getPlugin("advance-magic");
        return addon instanceof AdvanceMagicPlugin magic&&magic.isEnabled()&&magic.staffSpells()!=null?magic:null;
    }
    private int price(String key,int fallback){return plugin.integer("seraph-trader.prices."+key,fallback,0,64);}
    private int limit(String key,int fallback){return plugin.integer("seraph-trader.limits."+key,fallback,0,1000);}
    List<Orb> orbsFor(Element element){
        List<String> ids=plugin.getConfig().getStringList("seraph-trader.element-orbs."+element.id());
        if(ids.isEmpty())ids=DEFAULT_ORBS.get(element);
        List<Orb> accepted=new ArrayList<>();
        for(String id:ids){Orb orb=Orb.parse(id);if(orb!=null&&!accepted.contains(orb))accepted.add(orb);}
        return accepted.isEmpty()?List.of(Orb.values()):accepted;
    }
    private ItemStack feathers(int amount){return SeraphFeathers.feather(plugin,amount);}
    private ItemStack orbs(Orb orb,int amount){ItemStack item=SoulOrbs.orb(plugin,orb);item.setAmount(amount);return item;}

    // ---- the two traders ----
    /** Spells offered today: the same for everyone, new each day. */
    static List<ElementalSpell> rotation(String seed,ElementalSpell.Rarity rarity,int count){
        List<ElementalSpell> pool=new ArrayList<>(Arrays.stream(ElementalSpell.values()).filter(s->s.rarity()==rarity).toList());
        Collections.shuffle(pool,new Random(seed.hashCode()*31L+rarity.ordinal()));
        return pool.subList(0,Math.min(count,pool.size()));
    }
    // ---- promotions ----
    private long periodTicks(){return plugin.integer("seraph-trader.promotions.period-days",3,1,30)*24000L;}
    private boolean promotionsOn(){return plugin.getConfig().getBoolean("seraph-trader.promotions.enabled",true);}
    /** Evergarden's game clock: it never pauses for /time set and survives restarts. */
    long period(){return plugin.world()==null?0:plugin.world().getGameTime()/periodTicks();}
    Promotion promotion(){return promotion(period());}
    Promotion promotion(long period){
        if(!promotionsOn())return new Promotion(period,Set.of(),"",(period+1)*periodTicks());
        Random random=new Random(period*0x9E3779B97F4A7C15L^0x534552415048L);
        List<Element> elements=new ArrayList<>(List.of(Element.values()));Collections.shuffle(elements,random);
        int count=plugin.integer("seraph-trader.promotions.staff-elements",2,0,8);
        String other=SALE_CATEGORIES.get((int)Math.floorMod(period,(long)SALE_CATEGORIES.size()));
        EnumSet<Element> staffs=EnumSet.noneOf(Element.class);staffs.addAll(elements.subList(0,count));
        return new Promotion(period,staffs,other,(period+1)*periodTicks());
    }
    private long announced=-1;
    /** Tells players in Evergarden when a new sale begins; silent for the period running at startup. */
    void announce(){
        if(!promotionsOn())return;
        long now=period();
        if(announced==now)return;
        boolean first=announced<0;announced=now;
        if(first||plugin.world()==null)return;
        String text="Seraph ประกาศโปรโมชั่นใหม่ · "+describe(promotion(now));
        for(Player player:plugin.world().getPlayers()){
            plugin.message(player,text);player.playSound(player.getLocation(),Sound.BLOCK_NOTE_BLOCK_CHIME,.7f,1.4f);
        }
    }
    private int percent(String key,int fallback){return plugin.integer("seraph-trader.promotions."+key,fallback,0,90);}
    /** Negative special price: how many of the first ingredient the sale takes off. */
    private int off(int cost,int percent){return -Math.min(cost-1,Math.round(cost*percent/100f));}
    String describe(Promotion promotion){
        if(!promotionsOn())return "ไม่มีโปรโมชั่น";
        List<String> parts=new ArrayList<>();
        if(!promotion.staffs.isEmpty())parts.add("คทา "+String.join("/",promotion.staffs.stream().map(e->e.title).toList())+" ลด");
        parts.add(switch(promotion.other){case "scrolls"->"คัมภีร์ประจำวันลด";case "potions"->"ยามานาสูงสุดลด";default->"Core of Legend ลด";});
        long ticks=Math.max(0,promotion.endsTick-(plugin.world()==null?0:plugin.world().getGameTime()));
        return String.join(" · ",parts)+" · เหลือ "+Math.max(1,ticks/1200)+" นาที";
    }
    List<Offer> magicOffers(){return magicOffers(promotion());}
    List<Offer> magicOffers(Promotion sale){
        AdvanceMagicPlugin magic=magic();List<Offer> offers=new ArrayList<>();
        if(magic==null)return offers;
        int staffLimit=limit("staffs-per-week",2);
        for(Element element:Element.values()){
            boolean special=element==Element.LIGHT||element==Element.DARK,onSale=sale.onSale(element);
            int cost=special?price("special-staff-feathers",60):price("staff-feathers",40),orbCost=special?price("special-staff-orbs",9):price("staff-orbs",7);
            int discount=onSale?off(cost,percent("staff-feathers-off",30)):0;
            if(onSale)orbCost=Math.max(1,Math.round(orbCost*(100-percent("staff-orbs-off",40))/100f));
            // Elemental staffs need an ID each; selling without one lets Advance Magic mint a fresh ID per sale.
            for(Orb orb:orbsFor(element))offers.add(new Offer("staff:"+element.id()+":"+orb.id(),"staff",staffLimit,true,feathers(cost),orbs(orb,orbCost),
                ()->idless(magic.staffSpells().createStaff(element,StaffSpellService.Tier.APPRENTICE)),discount));
        }
        String today=SeraphFeathers.day();
        int[] counts={limit("scroll-basic-per-day",2),limit("scroll-rare-per-day",3),limit("scroll-epic-per-day",2)};
        int[] costs={price("scroll-basic-feathers",8),price("scroll-rare-feathers",15),price("scroll-epic-feathers",30)};
        ElementalSpell.Rarity[] rarities={ElementalSpell.Rarity.BASIC,ElementalSpell.Rarity.RARE,ElementalSpell.Rarity.EPIC};
        boolean scrollSale=promotionsOn()&&sale.other.equals("scrolls");
        for(int i=0;i<3;i++){final int cost=costs[i];for(ElementalSpell spell:rotation(today,rarities[i],counts[i]))
            offers.add(new Offer("scroll:"+spell.id(),"scroll:"+spell.id(),1,false,feathers(cost),null,()->magic.staffSpells().createScroll(spell),
                scrollSale?off(cost,percent("other-off",30)):0));}
        offers.add(new Offer("potion:good","potion:good",limit("good-potions-per-day",10),false,feathers(price("good-potion-feathers",3)),null,()->{
            ItemStack potion=magic.manaPotions().create(Quality.GOOD);return potion;}));
        int supreme=price("supreme-potion-feathers",12);
        offers.add(new Offer("potion:supreme","potion:supreme",limit("supreme-potions-per-day",2),false,feathers(supreme),null,()->magic.manaPotions().create(Quality.SUPREME),
            promotionsOn()&&sale.other.equals("potions")?off(supreme,percent("potion-off",50)):0));
        offers.add(new Offer("change:golden","change",0,false,feathers(SeraphFeathers.GOLDEN_VALUE),null,()->SeraphFeathers.golden(plugin,1)));
        offers.add(new Offer("change:feathers","change",0,false,SeraphFeathers.golden(plugin,1),null,()->feathers(SeraphFeathers.GOLDEN_VALUE)));
        return offers;
    }
    /** Each boss core buys any one of its set's four slots, without extra currency or stock limits. */
    List<Offer> armorOffers(){
        AdvanceMagicPlugin magic=magic();List<Offer> offers=new ArrayList<>();
        if(magic==null)return offers;
        ArmorSetService armor=new ArmorSetService(magic);
        for(Core core:Core.values()){
            ArmorSet set=core==Core.SERAPH?ArmorSet.SERAPH:ArmorSet.REVENANT;
            for(Piece piece:Piece.values())offers.add(new Offer("armor:"+set.id()+":"+piece.id(),"armor",0,false,
                BossArmorCores.create(plugin,core),null,()->armor.create(set,piece)));
        }
        return offers;
    }
    List<Offer> legendOffers(){return legendOffers(promotion());}
    List<Offer> legendOffers(Promotion sale){
        AdvanceMagicPlugin magic=magic();List<Offer> offers=new ArrayList<>();
        int golden=price("legend-core-golden",15),orbCost=price("legend-core-orbs",3),coreLimit=limit("legend-cores-per-week",1);
        int coreDiscount=promotionsOn()&&sale.other.equals("legend-core")?off(golden,percent("legend-core-off",20)):0;
        // A Core of Legend asks for orbs of one sanctuary; any of the six will do.
        for(Orb orb:Orb.values())offers.add(new Offer("legend:core:"+orb.id(),"legend-core",coreLimit,true,
            SeraphFeathers.golden(plugin,golden),orbs(orb,orbCost),()->SoulOrbs.legendCore(plugin),coreDiscount));
        if(magic==null)return offers;
        ItemStack cores=SoulOrbs.legendCore(plugin);cores.setAmount(price("legendary-weapon-cores",3));
        for(Weapon weapon:Weapon.values())offers.add(new Offer("weapon:"+weapon.id(),"weapon",limit("legendary-weapons-per-week",1),true,
            cores.clone(),null,()->magic.legendaryWeapons().create(weapon)));
        ItemStack core=SoulOrbs.legendCore(plugin);core.setAmount(price("legendary-scroll-cores",1));
        for(ElementalSpell spell:rotation(SeraphFeathers.week(),ElementalSpell.Rarity.LEGENDARY,limit("legendary-scrolls-on-offer",2)))
            offers.add(new Offer("scroll:"+spell.id(),"legendary-scroll",limit("legendary-scrolls-per-week",1),true,core.clone(),null,()->magic.staffSpells().createScroll(spell)));
        offers.addAll(armorOffers());
        return offers;
    }
    private static ItemStack idless(ItemStack staff){
        var meta=staff.getItemMeta();
        meta.getPersistentDataContainer().remove(new NamespacedKey("advance_magic","spell_staff_id"));
        meta.getPersistentDataContainer().remove(new NamespacedKey("advance_magic","spell_item_version"));
        staff.setItemMeta(meta);return staff;
    }
    int remaining(Player player,Offer offer){
        if(offer.limit<=0)return 9999;
        return Math.max(0,offer.limit-feathers.used(player.getUniqueId(),offer.category,offer.weekly));
    }
    /** Opens a villager-style trading screen whose stock reflects this player's remaining limits. */
    void openTrader(Player player,boolean legends){
        openTrader(player,legends?"✦ Seraph · ตำนาน":"✦ Seraph · เวทมนตร์",legends?legendOffers():magicOffers());
    }
    private void openTrader(Player player,String title,List<Offer> offers){
        if(offers.isEmpty()){plugin.message(player,"ร้านนี้ยังไม่พร้อม กรุณาลองใหม่ภายหลัง");return;}
        BossArmorCores.migratePlayer(plugin,player);
        Merchant merchant=Bukkit.createMerchant(Component.text(title,NamedTextColor.GOLD));
        List<MerchantRecipe> recipes=new ArrayList<>();List<Offer> kept=new ArrayList<>();
        for(Offer offer:offers){
            ItemStack result;
            try{result=offer.result.get();}catch(RuntimeException|LinkageError error){plugin.getLogger().warning("Seraph offer "+offer.id+" unavailable: "+error);continue;}
            // A sale uses the villager discount, so the screen shows the old price struck through.
            MerchantRecipe recipe=new MerchantRecipe(result,0,remaining(player,offer),false,0,0,0,offer.special,offer.special==0);
            recipe.addIngredient(offer.first);if(offer.second!=null)recipe.addIngredient(offer.second);
            recipes.add(recipe);kept.add(offer);
        }
        merchant.setRecipes(recipes);
        sessions.put(player.getUniqueId(),new Session(merchant,kept));
        player.openMerchant(merchant,true);
    }

    // ---- trades ----
    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true)
    public void purchase(PlayerPurchaseEvent event){
        Player player=event.getPlayer();Session session=sessions.get(player.getUniqueId());
        if(session==null||event.getMerchant()!=session.merchant)return;
        int index=session.merchant.getRecipes().indexOf(event.getTrade());
        if(index<0&&player.getOpenInventory().getTopInventory() instanceof MerchantInventory inventory)index=inventory.getSelectedRecipeIndex();
        if(index<0||index>=session.offers.size()){event.setCancelled(true);return;}
        Offer offer=session.offers.get(index);
        // Several lines can share one category (e.g. any staff); the screen only knows its own line.
        if(remaining(player,offer)<=0){
            event.setCancelled(true);plugin.message(player,"ครบจำนวนที่แลกได้"+(offer.weekly?"สัปดาห์นี้":"วันนี้")+"แล้ว");
            player.playSound(player.getLocation(),Sound.ENTITY_VILLAGER_NO,.7f,1.2f);return;
        }
        if(offer.limit>0)feathers.use(player.getUniqueId(),offer.category,offer.weekly);
        plugin.getLogger().info("Seraph trade: "+player.getName()+" ("+player.getUniqueId()+") "+offer.id);
        player.playSound(player.getLocation(),Sound.BLOCK_AMETHYST_BLOCK_CHIME,1,1.25f);
        trader.celebrate(player,offer.id.startsWith("weapon:")||offer.id.startsWith("legend:")||offer.id.startsWith("armor:"));
        if(offer.id.startsWith("staff:"))Bukkit.getScheduler().runTask(plugin,()->mintStaffs(player));
    }
    /** Gives every bought staff (inventory or cursor) its own ID. */
    private void mintStaffs(Player player){
        AdvanceMagicPlugin magic=magic();if(magic==null||!player.isOnline())return;
        magic.staffSpells().migrate(player.getInventory());
        ItemStack cursor=player.getItemOnCursor();
        if(cursor!=null&&magic.staffSpells().migrate(cursor))player.setItemOnCursor(cursor);
    }
    @EventHandler public void closeTrader(InventoryCloseEvent event){
        if(event.getInventory() instanceof MerchantInventory&&event.getPlayer() instanceof Player player&&sessions.remove(player.getUniqueId())!=null)
            Bukkit.getScheduler().runTask(plugin,()->mintStaffs(player));
    }

    // ---- the Seraph's menu ----
    public void open(Player player){
        long now=System.currentTimeMillis();
        // Armor stands fire both the "at" and plain interaction for one click.
        if(now-lastOpen.getOrDefault(player.getUniqueId(),0L)<400)return;
        lastOpen.put(player.getUniqueId(),now);
        if(feathers.blessing(player))plugin.message(player,"Seraph อวยพรคุณ · ได้รับขนนกเซราฟประจำวันแล้ว");
        if(JudgePackService.bedrock(player)&&Bukkit.getPluginManager().getPlugin("floodgate")!=null){
            try{if(form(player))return;}catch(Throwable error){plugin.getLogger().warning("Seraph Bedrock form unavailable for "+player.getName()+": "+error);}
        }
        hub(player);
    }
    private static Component text(String value,TextColor color){return Component.text(value,color).decoration(TextDecoration.ITALIC,false);}
    private ItemStack button(ItemStack base,String name,TextColor color,List<String> lines){
        ItemStack item=base.clone();var meta=item.getItemMeta();
        for(var key:new ArrayList<>(meta.getPersistentDataContainer().getKeys()))meta.getPersistentDataContainer().remove(key);
        meta.displayName(text(name,color));meta.lore(lines.stream().map(l->text(l,NamedTextColor.GRAY)).toList());
        meta.addItemFlags(ItemFlag.values());item.setItemMeta(meta);return item;
    }
    private List<String> summary(Player player){
        UUID id=player.getUniqueId();
        return List.of("โปรโมชั่น: "+describe(promotion()),"ขนนกในกระเป๋า: "+feathers.carried(player)+" (ทองคำนับ "+SeraphFeathers.GOLDEN_VALUE+")",
            "ได้จากกิจกรรมวันนี้: "+feathers.earnedToday(id)+"/"+feathers.dailyCap(),
            "พรประจำวัน: "+(feathers.blessedToday(player)?"รับแล้ว":"ยังไม่รับ"),
            "คทาสัปดาห์นี้: "+feathers.used(id,"staff",true)+"/"+limit("staffs-per-week",2)
                +" · Core of Legend: "+feathers.used(id,"legend-core",true)+"/"+limit("legend-cores-per-week",1));
    }
    void hub(Player player){
        Hub hub=new Hub();hub.inventory=Bukkit.createInventory(hub,27,text("✦ Seraph ผู้แลกเปลี่ยน",NamedTextColor.GOLD));
        ItemStack pane=button(new ItemStack(Material.WHITE_STAINED_GLASS_PANE)," ",NamedTextColor.GRAY,List.of());
        for(int i=0;i<27;i++)hub.inventory.setItem(i,pane);
        AdvanceMagicPlugin magic=magic();
        ItemStack magicIcon=magic!=null?magic.staffSpells().createStaff(Element.FIRE,StaffSpellService.Tier.APPRENTICE):new ItemStack(Material.BLAZE_ROD);
        hub.inventory.setItem(11,button(magicIcon,"ร้านเวทมนตร์",NamedTextColor.AQUA,List.of("คทาธาตุ · คัมภีร์ประจำวัน · ยามานา","แลกขนนกเซราฟเป็นทองคำ","คลิกเพื่อเปิด")));
        hub.actions.put(11,()->openTrader(player,false));
        hub.inventory.setItem(13,button(SeraphFeathers.feather(plugin,1),"ขนนกเซราฟของคุณ",NamedTextColor.YELLOW,summary(player)));
        hub.inventory.setItem(15,button(SoulOrbs.legendCore(plugin),"ร้านตำนาน",NamedTextColor.GOLD,List.of("Core of Legend · อาวุธตำนาน","คัมภีร์ Legendary · ชุด Seraph / Revenant","1 คอร์บอส แลกเกราะของชุดนั้น 1 ชิ้น","คลิกเพื่อเปิด")));
        hub.actions.put(15,()->openTrader(player,true));
        hub.inventory.setItem(19,button(new ItemStack(Material.MAP),"ร้านแผนที่",NamedTextColor.GREEN,List.of(
            "Sanctuary โลกปกติ · ขนนกเซราฟ "+maps.price(SanctuaryMaps.Destination.ACHERON)+" อัน",
            "บอสหลัก Evergarden 3 ตัว · ขนนกเซราฟทองคำ "+maps.price(SanctuaryMaps.Destination.JUDGE)+" อัน","คลิกเพื่อเปิดหน้าแลกเปลี่ยนแผนที่")));
        hub.actions.put(19,()->maps.open(player));
        hub.inventory.setItem(22,button(new ItemStack(Material.BOOK),"หาขนนกได้จากไหน",NamedTextColor.WHITE,List.of(
            "กล่องสมบัติในสิ่งก่อสร้างทั่ว Evergarden","ร่วมพิชิตบอสวิหาร · บอสโลก","เทมหรือฟักสัตว์ชนิดใหม่ครั้งแรก",
            "ป้อนอาหารสัตว์ป่า · พรจาก Seraph วันละครั้ง")));
        player.openInventory(hub.inventory);
    }
    @EventHandler(priority=EventPriority.LOWEST)
    public void click(InventoryClickEvent event){
        if(!(event.getView().getTopInventory().getHolder() instanceof Hub hub))return;
        event.setCancelled(true);
        if(!(event.getWhoClicked() instanceof Player player)||event.getClickedInventory()!=event.getView().getTopInventory())return;
        Runnable action=hub.actions.get(event.getRawSlot());
        if(action!=null){player.playSound(player.getLocation(),Sound.UI_BUTTON_CLICK,.6f,1.2f);Bukkit.getScheduler().runTask(plugin,action);}
    }
    @EventHandler(priority=EventPriority.LOWEST)
    public void drag(InventoryDragEvent event){if(event.getView().getTopInventory().getHolder() instanceof Hub)event.setCancelled(true);}
    @EventHandler public void quit(PlayerQuitEvent event){sessions.remove(event.getPlayer().getUniqueId());lastOpen.remove(event.getPlayer().getUniqueId());}
    private boolean form(Player player){
        List<Runnable> actions=List.of(()->openTrader(player,false),()->openTrader(player,true),()->maps.open(player));
        SimpleForm form=SimpleForm.builder().title("Seraph").content(String.join("\n",summary(player)))
            .button("ร้านเวทมนตร์\n§7คทา · คัมภีร์ · ยามานา",FormImage.Type.PATH,"textures/items/seraph_feather")
            .button("ร้านตำนาน\n§7อาวุธ · คัมภีร์ · ชุด Seraph / Revenant",FormImage.Type.PATH,"textures/items/core_of_legend")
            .button("ร้านแผนที่\n§7โลกปกติ "+maps.price(SanctuaryMaps.Destination.ACHERON)+" ขนนก · บอสหลัก Evergarden "+maps.price(SanctuaryMaps.Destination.JUDGE)+" ทองคำ",FormImage.Type.PATH,"textures/items/map_empty")
            .validResultHandler(response->{
                int index=response.clickedButtonId();
                if(index>=0&&index<actions.size())Bukkit.getScheduler().runTask(plugin,()->{if(player.isOnline())actions.get(index).run();});
            }).build();
        return FloodgateApi.getInstance().sendForm(player.getUniqueId(),form);
    }
}
