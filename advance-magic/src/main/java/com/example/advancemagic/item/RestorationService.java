package com.example.advancemagic.item;

import com.example.advancemagic.AdvanceMagicPlugin;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import java.util.*;

/** Utility items deliberately have no spell tag, recipe or vanilla durability. */
public final class RestorationService implements Listener {
    private final AdvanceMagicPlugin plugin;
    private final NamespacedKey type=new NamespacedKey("advance_magic","restoration_item");
    private final NamespacedKey charges=new NamespacedKey("advance_magic","restoration_charges");
    private final Map<UUID,Long> inputs=new HashMap<>();
    public RestorationService(AdvanceMagicPlugin plugin){this.plugin=plugin;}
    public ItemStack createCore(){
        ItemStack item=new ItemStack(Material.PRISMARINE_CRYSTALS);
        var meta=item.getItemMeta();meta.setDisplayName(ChatColor.AQUA+"Core of Restoration");
        meta.setLore(List.of(ChatColor.GRAY+"ใช้กับแท่นฟื้นฟูใน Whale / Garden / Observatory",
                ChatColor.YELLOW+"1 Core ฟื้นความทนทานคทาจนเต็ม",ChatColor.DARK_GRAY+"ใช้คราฟคทาไม่ได้"));
        meta.setEnchantmentGlintOverride(true);meta.getPersistentDataContainer().set(type,PersistentDataType.STRING,"core");
        item.setItemMeta(meta);model(item);return item;
    }
    public ItemStack createRepairWand(){
        ItemStack item=new ItemStack(Material.BLAZE_ROD);var meta=item.getItemMeta();
        meta.getPersistentDataContainer().set(type,PersistentDataType.STRING,"wand");
        meta.getPersistentDataContainer().set(charges,PersistentDataType.INTEGER,5);
        // Each wand remains a separate item; ordinary blaze rods are unaffected.
        meta.setMaxStackSize(1);item.setItemMeta(meta);refresh(item);model(item);return item;
    }
    /** Appearance-only migration, including items created before the resource pack update. */
    public boolean model(ItemStack item){
        String name=isCore(item)?"restoration_core":isRepairWand(item)?"restoration_wand":null;
        if(name==null)return false;
        var meta=item.getItemMeta();var key=new NamespacedKey("advance_magic",name);
        var cmd=meta.getCustomModelDataComponent();
        if(key.equals(meta.getItemModel())&&cmd.getStrings().equals(List.of(key.toString())))return false;
        meta.setItemModel(key);cmd.setStrings(List.of(key.toString()));meta.setCustomModelDataComponent(cmd);item.setItemMeta(meta);return true;
    }
    public void migrate(Inventory inventory){
        for(int slot=0;slot<inventory.getSize();slot++){ItemStack item=inventory.getItem(slot);if(model(item))inventory.setItem(slot,item);}
    }
    @EventHandler public void join(PlayerJoinEvent e){migrate(e.getPlayer().getInventory());migrate(e.getPlayer().getEnderChest());}
    @EventHandler public void open(InventoryOpenEvent e){migrate(e.getInventory());migrate(e.getPlayer().getInventory());}
    @EventHandler public void held(PlayerItemHeldEvent e){migrate(e.getPlayer().getInventory());}
    @EventHandler public void pickup(EntityPickupItemEvent e){var item=e.getItem().getItemStack();if(model(item))e.getItem().setItemStack(item);}
    private boolean tagged(ItemStack item,Material material,String id){
        return item!=null&&item.getType()==material&&item.hasItemMeta()
                &&id.equals(item.getItemMeta().getPersistentDataContainer().get(type,PersistentDataType.STRING));
    }
    public boolean isCore(ItemStack item){return tagged(item,Material.PRISMARINE_CRYSTALS,"core");}
    public boolean isRepairWand(ItemStack item){return tagged(item,Material.BLAZE_ROD,"wand");}
    public boolean isFuel(ItemStack item){return isCore(item)||plugin.wands().coreSpell(item)!=null;}
    public int charges(ItemStack item){return isRepairWand(item)?Math.clamp(item.getItemMeta().getPersistentDataContainer()
            .getOrDefault(charges,PersistentDataType.INTEGER,0),0,5):0;}
    private void refresh(ItemStack item){
        var meta=item.getItemMeta();meta.setDisplayName(ChatColor.LIGHT_PURPLE+"Wand of Restoration");
        meta.setEnchantmentGlintOverride(true);meta.setLore(List.of(ChatColor.AQUA+"พลังฟื้นฟู: "+charges(item)+" / 5 ครั้ง",
                ChatColor.GRAY+"เล็งแล้วคลิก / แตะผู้เล่น ในระยะ 8 บล็อก",
                ChatColor.GRAY+"เป้าหมายต้องถือคทาที่ต้องการซ่อมในมือหลัก",
                ChatColor.RED+"เติมพลัง ซ่อม หรือเพิ่มจำนวนครั้งไม่ได้"));item.setItemMeta(meta);
    }
    private boolean allowed(Player p){return p.isOnline()&&!p.isDead()&&p.getGameMode()!=GameMode.SPECTATOR&&plugin.casts().canCast(p);}
    private void use(Player player){
        ItemStack source=player.getInventory().getItemInMainHand();
        if(!isRepairWand(source)||!allowed(player))return;
        long now=System.currentTimeMillis();if(now-inputs.getOrDefault(player.getUniqueId(),0L)<600)return;
        inputs.put(player.getUniqueId(),now);
        var hit=player.getWorld().rayTrace(player.getEyeLocation(),player.getEyeLocation().getDirection(),8,
                FluidCollisionMode.NEVER,true,.3,e->e instanceof Player&&e!=player);
        if(hit==null||!(hit.getHitEntity() instanceof Player target)){
            player.sendMessage(ChatColor.YELLOW+"เล็งผู้เล่นที่ถือคทาชำรุด ระยะไม่เกิน 8 บล็อก");return;
        }
        String error=repair(player,target);
        if(error!=null)player.sendMessage(ChatColor.YELLOW+error);
    }
    /** Main-thread transaction. A failed attempt never spends a charge. */
    public String repair(Player player,Player target){
        if(!allowed(player)||!allowed(target)||player==target||player.getWorld()!=target.getWorld()
                ||player.getEyeLocation().distanceSquared(target.getEyeLocation())>64||!player.hasLineOfSight(target))return "เป้าหมายอยู่นอกระยะหรือมีสิ่งกีดขวาง";
        ItemStack source=player.getInventory().getItemInMainHand();
        if(!isRepairWand(source)||source.getAmount()!=1||charges(source)<=0)return "คทาฟื้นฟูนี้ใช้พลังครบ 5 ครั้งแล้ว";
        ItemStack repaired=target.getInventory().getItemInMainHand().clone();
        if(!plugin.wands().restore(repaired))return "เป้าหมายต้องถือคทาปกติที่ความทนทานยังไม่เต็ม";
        ItemStack spent=source.clone();var meta=spent.getItemMeta();
        meta.getPersistentDataContainer().set(charges,PersistentDataType.INTEGER,charges(source)-1);spent.setItemMeta(meta);refresh(spent);
        player.getInventory().setItemInMainHand(spent);target.getInventory().setItemInMainHand(repaired);
        player.saveData();target.saveData();
        target.getWorld().spawnParticle(Particle.END_ROD,target.getLocation().add(0,1,0),24,.4,.6,.4,.03);
        target.getWorld().playSound(target.getLocation(),Sound.BLOCK_AMETHYST_BLOCK_CHIME,1,1.2f);
        target.sendMessage(ChatColor.GREEN+"คทาได้รับการฟื้นฟูจนเต็มแล้ว");
        player.sendMessage(ChatColor.GREEN+"ฟื้นฟูสำเร็จ — เหลือ "+charges(spent)+" ครั้ง");return null;
    }
    @EventHandler(priority=EventPriority.HIGH)
    public void interact(PlayerInteractEvent e){
        if(e.getHand()!=EquipmentSlot.HAND||!isRepairWand(e.getItem()))return;
        if(e.getAction()==org.bukkit.event.block.Action.PHYSICAL)return;
        // Air-use events may start cancelled; explicit block protection denials are respected.
        if(e.getClickedBlock()!=null&&e.useItemInHand()==Event.Result.DENY)return;
        e.setCancelled(true);use(e.getPlayer());
    }
    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true)
    public void entity(PlayerInteractEntityEvent e){
        if(e.getHand()==EquipmentSlot.HAND&&isRepairWand(e.getPlayer().getInventory().getItemInMainHand())){e.setCancelled(true);use(e.getPlayer());}
    }
    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true)
    public void attack(EntityDamageByEntityEvent e){
        if(e.getCause()==EntityDamageEvent.DamageCause.ENTITY_ATTACK&&e.getDamager() instanceof Player p
                &&isRepairWand(p.getInventory().getItemInMainHand())){e.setCancelled(true);use(p);}
    }
    @EventHandler(priority=EventPriority.HIGH)
    public void swing(PlayerAnimationEvent e){if(e.getAnimationType()==PlayerAnimationType.ARM_SWING)use(e.getPlayer());}
    @EventHandler public void quit(PlayerQuitEvent e){inputs.remove(e.getPlayer().getUniqueId());}
}
