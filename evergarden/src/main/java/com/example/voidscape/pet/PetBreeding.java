package com.example.voidscape.pet;

import com.example.voidscape.VoidscapePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import com.destroystokyo.paper.event.inventory.PrepareResultEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import java.time.Duration;
import java.util.*;
import java.util.random.RandomGenerator;

/**
 * Breeding happens in data, never as a herd of entities: two adults of one species rest in
 * the stable's nest, then the owner collects an egg item. The egg's identity and the unborn
 * pet live in the server ledger, so a duplicated egg item cannot hatch twice. Eggs incubate
 * while carried by an online player and hatch with a right-click.
 */
final class PetBreeding implements Listener,AutoCloseable {
    static final NamespacedKey EGG_MODEL=new NamespacedKey("voidscape","pet_egg");
    private final VoidscapePlugin plugin;
    private final PetStore store;
    private final PetCompanions companions;
    private final PetTaming taming;
    private final RandomGenerator random=new Random();
    private final BukkitTask incubator;

    PetBreeding(VoidscapePlugin plugin,PetStore store,PetCompanions companions,PetTaming taming){
        this.plugin=plugin;this.store=store;this.companions=companions;this.taming=taming;
        Bukkit.getPluginManager().registerEvents(this,plugin);
        incubator=Bukkit.getScheduler().runTaskTimer(plugin,this::incubate,600,600);
        Bukkit.getScheduler().runTask(plugin,()->Bukkit.getOnlinePlayers().forEach(this::retryDeliveries));
    }
    private int setting(String key,int fallback,int min,int max){return plugin.integer("pets.breeding."+key,fallback,min,max);}
    long nestMillis(){return setting("nest-minutes",30,1,10080)*60_000L;}
    long cooldownMillis(){return setting("cooldown-hours",24,0,720)*3_600_000L;}
    /** A mythical parent rests three times as long, so legends never become common. */
    long cooldownMillis(PetRecord record){
        return record.species.mythical()?setting("mythical-cooldown-hours",72,0,2160)*3_600_000L:cooldownMillis();
    }
    int foodCost(){return setting("food-cost",8,0,64);}
    int bondNeeded(){return setting("bond-required",50,0,PetRecord.BOND_MAX);}
    int dailyEggs(){return setting("eggs-per-day",2,1,64);}
    long incubateMillis(){return setting("incubate-minutes",20,1,10080)*60_000L;}

    /** Why these two cannot share the nest now, or null when they can. */
    String check(PetStable stable,PetRecord a,PetRecord b,long now){
        if(a==null||b==null)return "เลือกสัตว์เลี้ยงสองตัว";
        if(a.id.equals(b.id))return "ต้องเป็นสองตัวที่ต่างกัน";
        if(a.species!=b.species)return "ต้องเป็นชนิดเดียวกัน";
        if(a.stage!=PetCatalog.Stage.ADULT||b.stage!=PetCatalog.Stage.ADULT)return "ทั้งสองตัวต้องโตเต็มวัย";
        if(a.bond<bondNeeded()||b.bond<bondNeeded())return "ความผูกพันต้องถึง "+bondNeeded()+" ทั้งสองตัว";
        for(PetRecord r:List.of(a,b))if(r.bred>0&&now<r.bred+cooldownMillis(r))
            return r.name()+" เพิ่งผสมพันธุ์ · พร้อมอีก "+Math.max(1,(r.bred+cooldownMillis(r)-now)/3_600_000L)+" ชม.";
        if(stable.eggsToday(now)>=dailyEggs())return "วันนี้ได้ไข่ครบ "+dailyEggs()+" ฟองแล้ว";
        return null;
    }
    String start(Player player,UUID first,UUID second){
        PetStable stable=companions.stable(player);long now=System.currentTimeMillis();
        if(stable.locked)return "อ่านข้อมูลคอกสัตว์ของคุณไม่ได้ · แจ้งแอดมิน";
        if(stable.nestBusy())return "รังมีคู่อยู่แล้ว";
        PetRecord a=stable.get(first),b=stable.get(second);
        String problem=check(stable,a,b,now);if(problem!=null)return problem;
        if(store.eggsLocked())return "บันทึกไข่เสียหาย · แจ้งแอดมิน";
        Material food=taming.food(a.species);int cost=foodCost();
        if(player.getGameMode()!=GameMode.CREATIVE){
            // Plain items only: removeItem matches exactly, so tagged items on the same base stay put.
            if(!player.getInventory().containsAtLeast(new ItemStack(food),cost))return "ต้องใช้ "+PetTaming.foodName(food)+" ธรรมดา ×"+cost;
            player.getInventory().removeItem(new ItemStack(food,cost));
            if(food==Material.HONEY_BOTTLE)player.getInventory().addItem(new ItemStack(Material.GLASS_BOTTLE,cost))
                .values().forEach(left->player.getWorld().dropItemNaturally(player.getLocation(),left));
        }
        if(stable.out&&(a.id.equals(stable.active)||b.id.equals(stable.active)))companions.dismiss(player);
        stable.nestA=a.id;stable.nestB=b.id;stable.nestReady=now+nestMillis();stable.dirty=true;
        player.playSound(player.getLocation(),Sound.BLOCK_AMETHYST_BLOCK_RESONATE,.8f,1.3f);
        return null;
    }
    void cancel(Player player){companions.stable(player).clearNest();}
    String collect(Player player){
        retryDeliveries(player);
        PetStable stable=companions.stable(player);long now=System.currentTimeMillis();
        if(stable.locked||store.eggsLocked())return "อ่านข้อมูลสัตว์หรือไข่ไม่ได้ · แจ้งแอดมิน";
        if(!stable.nestBusy())return "ยังไม่มีคู่ในรัง";
        if(now<stable.nestReady)return "ไข่ยังไม่พร้อม · อีก "+Math.max(1,(stable.nestReady-now)/60_000L)+" นาที";
        if(stable.eggsToday(now)>=dailyEggs())return "วันนี้ได้ไข่ครบ "+dailyEggs()+" ฟองแล้ว · กลับมาเก็บพรุ่งนี้";
        if(player.getInventory().firstEmpty()<0)return "กระเป๋าเต็ม";
        PetRecord a=stable.get(stable.nestA),b=stable.get(stable.nestB);
        if(a==null||b==null){stable.clearNest();return "คู่ในรังไม่อยู่แล้ว";}
        PetRecord child=PetRecord.hatch(a.species,a,b,random);
        String id=UUID.randomUUID().toString();
        ConfigurationSection egg=store.eggs.createSection("eggs."+id);
        egg.set("owner",player.getUniqueId().toString());egg.set("species",a.species.id);egg.set("created",now);
        egg.set("parents",a.name()+" × "+b.name());egg.set("incubated",0L);egg.set("hatched",false);
        egg.set("collection-pending",true);egg.set("source.a",a.id.toString());egg.set("source.b",b.id.toString());egg.set("source.ready",stable.nestReady);
        egg.set("item-delivery-pending",true);egg.set("item-token",UUID.randomUUID().toString());
        child.save(egg.createSection("pet"));egg.set("pet-id",child.id.toString());
        // The item must not be handed out until its ledger entry is on disk.
        store.eggsDirty=true;
        if(!store.saveEggsNow()){
            store.eggs.set("eggs."+id,null);store.eggsDirty=true;
            return "บันทึกไข่ไม่ได้ · ไข่ยังอยู่ในรัง กรุณาแจ้งแอดมิน";
        }
        if(!store.finishCollection(stable,id,egg))return "บันทึกรังยังไม่สำเร็จ · เก็บรายการไข่ไว้แล้ว กรุณาแจ้งแอดมิน";
        retryDeliveries(player);
        if(egg.getBoolean("item-delivery-pending"))return "ไข่เก็บไว้ในรายการรอส่งแล้ว · เว้นช่องกระเป๋าแล้วเปิด /pets อีกครั้ง";
        player.playSound(player.getLocation(),Sound.ENTITY_CHICKEN_EGG,1,.8f);
        plugin.getLogger().info("Pet egg "+id+" laid for "+player.getName()+" ("+a.species.id+")");
        return null;
    }
    ItemStack egg(String id,PetCatalog.Species species,String parents,int percent){
        ItemStack item=new ItemStack(Material.PAPER);var meta=item.getItemMeta();
        meta.displayName(Component.text("ไข่ "+species.display,NamedTextColor.GOLD).decoration(TextDecoration.ITALIC,false));
        List<Component> lore=new ArrayList<>();
        lore.add(Component.text(species.thai,NamedTextColor.GRAY).decoration(TextDecoration.ITALIC,false));
        lore.add(Component.text("พ่อแม่: "+parents,NamedTextColor.GRAY).decoration(TextDecoration.ITALIC,false));
        lore.add(percent>=100?Component.text("พร้อมฟัก! คลิกขวาเพื่อฟัก",NamedTextColor.GREEN).decoration(TextDecoration.ITALIC,false)
            :Component.text("ฟักแล้ว "+percent+"% · พกไว้ขณะออนไลน์",NamedTextColor.AQUA).decoration(TextDecoration.ITALIC,false));
        lore.add(Component.text("หรือกดฟักที่ /pet → รังผสมพันธุ์",NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC,false));
        meta.lore(lore);meta.setItemModel(EGG_MODEL);meta.setMaxStackSize(1);
        var model=meta.getCustomModelDataComponent();model.setStrings(List.of(EGG_MODEL.toString()));meta.setCustomModelDataComponent(model);
        meta.getPersistentDataContainer().set(plugin.key("pet_egg"),PersistentDataType.STRING,id);
        meta.getPersistentDataContainer().set(plugin.key("pet_egg_percent"),PersistentDataType.INTEGER,percent);
        String token=store.eggs.getString("eggs."+id+".item-token");
        if(token!=null)meta.getPersistentDataContainer().set(plugin.key("pet_egg_token"),PersistentDataType.STRING,token);
        item.setItemMeta(meta);return item;
    }
    String eggId(ItemStack item){
        if(item==null||item.getType()!=Material.PAPER||!item.hasItemMeta())return null;
        return item.getItemMeta().getPersistentDataContainer().get(plugin.key("pet_egg"),PersistentDataType.STRING);
    }
    private int percent(ConfigurationSection egg){
        return (int)Math.min(100,egg.getLong("incubated")*100/Math.max(1,incubateMillis()));
    }
    /** Legacy eggs have no token; new delivery attempts invalidate an old rolled-back item. */
    private boolean currentEgg(ItemStack item,ConfigurationSection egg){
        String token=egg.getString("item-token");
        return token==null||item!=null&&item.hasItemMeta()&&token.equals(item.getItemMeta().getPersistentDataContainer().get(plugin.key("pet_egg_token"),PersistentDataType.STRING));
    }
    /** Retry only unfinished initial grants, never an egg the player has traded or put away. */
    int retryDeliveries(Player player){
        if(!player.isOnline()||player.isDead()||store.eggsLocked())return 0;
        var all=store.eggs.getConfigurationSection("eggs");if(all==null)return 0;
        int delivered=0;
        for(String id:all.getKeys(false)){
            var data=all.getConfigurationSection(id);
            if(data==null||data.getBoolean("hatched")||!data.getBoolean("item-delivery-pending")
                ||!player.getUniqueId().toString().equals(data.getString("owner")))continue;
            PetStable stable=companions.stable(player);
            if(stable.locked||data.getBoolean("collection-pending"))continue;
            var species=PetCatalog.Species.find(data.getString("species"));
            if(species==null||data.getConfigurationSection("pet")==null)continue;
            var inventory=player.getInventory();boolean present=false;
            for(int slot=0;slot<inventory.getSize();slot++){
                ItemStack item=inventory.getItem(slot);if(!id.equals(eggId(item)))continue;
                if(currentEgg(item,data))present=true;
                else inventory.setItem(slot,null); // A stale recovery copy must not occupy its replacement's only slot.
            }
            // Cursor contents are not part of the saved player inventory. Move this egg back first.
            ItemStack cursor=player.getItemOnCursor();
            if(id.equals(eggId(cursor))&&currentEgg(cursor,data)){
                if(!present){int slot=inventory.firstEmpty();if(slot<0)continue;inventory.setItem(slot,cursor.clone());present=true;}
                player.setItemOnCursor(null);
            }else if(id.equals(eggId(cursor)))player.setItemOnCursor(null);
            if(!present){
                int slot=inventory.firstEmpty();if(slot<0)continue;
                String old=data.getString("item-token");data.set("item-token",UUID.randomUUID().toString());store.eggsDirty=true;
                if(!store.saveEggsNow()){data.set("item-token",old);store.eggsDirty=true;break;}
                inventory.setItem(slot,egg(id,species,data.getString("parents",""),percent(data)));
            }
            // saveData() swallows some disk errors; verify a nonce in the actual saved NBT.
            if(!PetEggInventorySave.save(plugin,player))break;
            data.set("item-delivery-pending",false);store.eggsDirty=true;
            if(!store.saveEggsNow()){data.set("item-delivery-pending",true);store.eggsDirty=true;break;}
            delivered++;
        }
        return delivered;
    }
    private boolean pendingItem(ItemStack item){
        String id=eggId(item);var data=id==null?null:store.eggs.getConfigurationSection("eggs."+id);
        return data!=null&&data.getBoolean("item-delivery-pending")&&currentEgg(item,data);
    }
    private void retryNextTick(Player player){
        Bukkit.getScheduler().runTask(plugin,()->{
            if(player.isOnline()&&retryDeliveries(player)>0)plugin.message(player,"ส่งไข่ที่ค้างไว้เข้ากระเป๋าแล้ว");
        });
    }
    @EventHandler public void join(PlayerJoinEvent event){retryNextTick(event.getPlayer());}
    @EventHandler public void respawn(PlayerRespawnEvent event){retryNextTick(event.getPlayer());}
    // A grant without a verified inventory save must not leave its recipient before recovery.
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void movePending(InventoryClickEvent event){
        ItemStack hotbar=event.getHotbarButton()<0?null:event.getWhoClicked().getInventory().getItem(event.getHotbarButton());
        ItemStack offhand=event.getClick()==ClickType.SWAP_OFFHAND?event.getWhoClicked().getInventory().getItemInOffHand():null;
        if(pendingItem(event.getCurrentItem())||pendingItem(event.getCursor())||pendingItem(hotbar)||pendingItem(offhand))event.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void dragPending(InventoryDragEvent event){if(pendingItem(event.getOldCursor()))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void dropPending(PlayerDropItemEvent event){if(pendingItem(event.getItemDrop().getItemStack()))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void swapPending(PlayerSwapHandItemsEvent event){if(pendingItem(event.getMainHandItem())||pendingItem(event.getOffHandItem()))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void hopperPending(InventoryMoveItemEvent event){if(pendingItem(event.getItem()))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void hopperPickupPending(InventoryPickupItemEvent event){if(pendingItem(event.getItem().getItemStack()))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void pickupPending(EntityPickupItemEvent event){if(pendingItem(event.getItem().getItemStack()))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST)
    public void deathPending(PlayerDeathEvent event){if(!event.getKeepInventory())event.getDrops().removeIf(this::pendingItem);}
    /** An egg a player carries, as the nest page lists it. */
    record Carried(int slot,String id,PetCatalog.Species species,int percent){}
    /** Registered, unhatched eggs in a player's inventory, ready ones first. */
    List<Carried> carried(Player player){
        retryDeliveries(player);
        List<Carried> eggs=new ArrayList<>();var inventory=player.getInventory();
        for(int slot=0;slot<inventory.getSize();slot++){
            String id=eggId(inventory.getItem(slot));if(id==null)continue;
            ConfigurationSection egg=store.eggs.getConfigurationSection("eggs."+id);
            var species=egg==null?null:PetCatalog.Species.find(egg.getString("species"));
            if(species==null||egg.getBoolean("hatched")||egg.getBoolean("item-delivery-pending")||!currentEgg(inventory.getItem(slot),egg)||eggs.stream().anyMatch(e->e.id().equals(id)))continue;
            eggs.add(new Carried(slot,id,species,percent(egg)));
        }
        eggs.sort(Comparator.comparingInt(Carried::percent).reversed());
        return eggs;
    }
    /** Admin testing: the nest's egg is ready now and every egg the player carries finishes incubating. */
    int rush(Player player){
        PetStable stable=companions.stable(player);int changed=0;
        if(stable.nestBusy()&&stable.nestReady>System.currentTimeMillis()){stable.nestReady=System.currentTimeMillis();stable.dirty=true;changed++;}
        for(Carried egg:carried(player)){
            if(egg.percent()>=100)continue;
            store.eggs.set("eggs."+egg.id()+".incubated",incubateMillis());store.eggsDirty=true;changed++;
            ConfigurationSection data=store.eggs.getConfigurationSection("eggs."+egg.id());
            player.getInventory().setItem(egg.slot(),egg(egg.id(),egg.species(),data.getString("parents",""),100));
        }
        store.saveEggs();
        return changed;
    }
    /** Every 30 seconds each carried egg warms once, however many copies exist. */
    private void incubate(){
        Set<String> warmed=new HashSet<>();
        for(Player player:Bukkit.getOnlinePlayers()){
            retryDeliveries(player);
            var inventory=player.getInventory();
            for(int slot=0;slot<inventory.getSize();slot++){
                ItemStack item=inventory.getItem(slot);String id=eggId(item);if(id==null)continue;
                ConfigurationSection egg=store.eggs.getConfigurationSection("eggs."+id);
                if(egg==null||egg.getBoolean("hatched")||egg.getBoolean("item-delivery-pending")||!currentEgg(item,egg))continue;
                if(warmed.add(id)){egg.set("incubated",Math.min(incubateMillis(),egg.getLong("incubated")+30_000L));store.eggsDirty=true;}
                int now=percent(egg),shown=item.getItemMeta().getPersistentDataContainer().getOrDefault(plugin.key("pet_egg_percent"),PersistentDataType.INTEGER,0);
                // Rewrite the item only in 10% steps.
                if(now/10!=shown/10||now>=100&&shown<100){
                    var species=PetCatalog.Species.find(egg.getString("species"));
                    if(species!=null)inventory.setItem(slot,egg(id,species,egg.getString("parents",""),now));
                }
            }
        }
        store.saveEggs();
    }
    /** Right-click with an egg in either hand; the main hand goes first. */
    @EventHandler(priority=EventPriority.HIGH)
    public void hatch(PlayerInteractEvent event){
        if(event.getAction()!=Action.RIGHT_CLICK_AIR&&event.getAction()!=Action.RIGHT_CLICK_BLOCK)return;
        Player player=event.getPlayer();var inventory=player.getInventory();
        boolean main=eggId(inventory.getItemInMainHand())!=null;
        if(event.getHand()==EquipmentSlot.OFF_HAND&&main)return;
        ItemStack item=event.getHand()==EquipmentSlot.HAND?inventory.getItemInMainHand():event.getHand()==EquipmentSlot.OFF_HAND?inventory.getItemInOffHand():null;
        if(eggId(item)==null)return;
        event.setCancelled(true);
        String problem=hatch(player,item);
        if(problem!=null)plugin.message(player,problem);
    }
    /** Hatches one egg item into the player's stable; why not, or null once the baby is in. */
    String hatch(Player player,ItemStack item){
        retryDeliveries(player);
        String id=eggId(item);if(id==null)return "นี่ไม่ใช่ไข่สัตว์เลี้ยง";
        ConfigurationSection egg=store.eggs.getConfigurationSection("eggs."+id);
        if(egg==null||egg.getBoolean("hatched"))return "ไข่ใบนี้ฟักไปแล้วหรือไม่ได้ลงทะเบียน · ฟักไม่ได้";
        if(!currentEgg(item,egg))return "ไข่ใบนี้ถูกแทนที่ระหว่างกู้คืนแล้ว · ใช้ใบล่าสุดในกระเป๋า";
        if(egg.getBoolean("item-delivery-pending"))return "ไข่ยังรอยืนยันการบันทึก · เปิด /pets เพื่อลองส่งอีกครั้ง หรือแจ้งแอดมิน";
        if(store.eggsLocked())return "บันทึกไข่เสียหาย · แจ้งแอดมิน";
        if(percent(egg)<100)return "ไข่ยังไม่พร้อม · ฟักแล้ว "+percent(egg)+"% · พกไว้ขณะออนไลน์";
        PetStable stable=companions.stable(player);
        // A stable file that could not be read is never saved, so a baby hatched into it would be lost.
        if(stable.locked)return "อ่านข้อมูลคอกสัตว์ของคุณไม่ได้ · แจ้งแอดมินก่อนฟัก";
        if(stable.full())return "คอกสัตว์เต็ม · ปล่อยตัวหนึ่งใน /pet ก่อนฟัก";
        ConfigurationSection data=egg.getConfigurationSection("pet");UUID petId=PetStable.uuid(egg.getString("pet-id"));
        PetRecord child=data==null||petId==null?null:PetRecord.load(petId,data);
        if(child==null)return "ข้อมูลไข่เสียหาย · แจ้งแอดมิน";
        // A durable pending-delivery journal recovers the baby if shutdown interrupts the hatch.
        if(!store.deliverEgg(stable,egg,child))return "บันทึกการฟักไม่สำเร็จ · แจ้งแอดมิน ห้ามทิ้งไข่";
        item.setAmount(item.getAmount()-1);
        Location at=player.getLocation().add(0,1,0);
        at.getWorld().spawnParticle(Particle.HEART,at,10,.5,.4,.5,0);at.getWorld().spawnParticle(Particle.END_ROD,at,24,.4,.5,.4,.04);
        player.playSound(at,Sound.BLOCK_SNIFFER_EGG_HATCH,1,1.2f);
        player.showTitle(Title.title(Component.text("ไข่ฟักแล้ว!",NamedTextColor.GOLD),Component.text(child.species.display+" ร่างเด็ก · รุ่นที่ "+child.generation,NamedTextColor.AQUA),
            Title.Times.times(Duration.ofMillis(200),Duration.ofSeconds(2),Duration.ofMillis(500))));
        plugin.message(player,"ลูก "+child.species.display+" อยู่ในคอกแล้ว · เรียกได้ที่ /pet");
        plugin.getLogger().info("Pet egg "+id+" hatched by "+player.getName()+" into "+child.id);
        if(plugin.seraphTrader()!=null)plugin.seraphTrader().feathers().firstTime(player,"hatch."+child.species.id,
            plugin.integer("seraph-trader.feathers.first-hatch",5,0,64),"ฟัก "+child.species.display+" ครั้งแรก");
        return null;
    }
    // Eggs are paper underneath; keep them out of books, maps and fireworks.
    @EventHandler public void craft(PrepareItemCraftEvent event){
        for(ItemStack item:event.getInventory().getMatrix())if(eggId(item)!=null){event.getInventory().setResult(null);return;}
    }
    @EventHandler public void crafter(CrafterCraftEvent event){
        if(event.getBlock().getState() instanceof org.bukkit.block.Crafter crafter)
            for(ItemStack item:crafter.getInventory().getContents())if(eggId(item)!=null){event.setCancelled(true);return;}
    }
    @EventHandler public void result(PrepareResultEvent event){
        for(ItemStack item:event.getInventory().getContents())if(eggId(item)!=null){event.setResult(null);return;}
    }
    @Override public void close(){incubator.cancel();HandlerList.unregisterAll(this);}
}
