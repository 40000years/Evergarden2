package com.example.voidscape.trade;

import com.example.voidscape.VoidscapePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.block.Chest;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.temporal.IsoFields;
import java.util.*;

/**
 * Seraph Feathers: the Seraph's currency, earned across Evergarden rather than from bosses alone.
 * Repeatable sources share a daily cap; first-time discoveries and chest loot do not. The ledger
 * ({@code seraph-feathers.yml}) also keeps each player's shop limits per day and per week.
 */
public final class SeraphFeathers implements Listener,AutoCloseable {
    public static final NamespacedKey MODEL=new NamespacedKey("voidscape","seraph_feather");
    public static final NamespacedKey GOLDEN_MODEL=new NamespacedKey("voidscape","seraph_feather_golden");
    /** One golden feather is worth this many feathers in the Seraph's exchange. */
    public static final int GOLDEN_VALUE=10;
    private final VoidscapePlugin plugin;
    private final File file;
    private final YamlConfiguration ledger;
    private final boolean locked;
    private boolean dirty;
    private final BukkitTask saver;

    SeraphFeathers(VoidscapePlugin plugin){
        this.plugin=plugin;file=new File(plugin.getDataFolder(),"seraph-feathers.yml");
        YamlConfiguration loaded=new YamlConfiguration();boolean failed=false;
        if(file.isFile())try{loaded.loadFromString(Files.readString(file.toPath()));}
        catch(Exception error){failed=true;plugin.getLogger().severe("Cannot read seraph-feathers.yml; feather rewards are paused until it is repaired: "+error);}
        ledger=loaded;locked=failed;
        Bukkit.getPluginManager().registerEvents(this,plugin);
        saver=Bukkit.getScheduler().runTaskTimer(plugin,this::save,1200,1200);
    }

    // ---- items ----
    private static ItemStack make(VoidscapePlugin plugin,boolean golden,int amount){
        ItemStack item=new ItemStack(Material.FEATHER,Math.max(1,amount));var meta=item.getItemMeta();
        meta.displayName(Component.text(golden?"✦ ขนนกเซราฟทองคำ ✦":"✦ ขนนกเซราฟ ✦",golden?NamedTextColor.GOLD:NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC,false));
        meta.lore(List.of(Component.text(golden?"มีค่าเท่าขนนกเซราฟ "+GOLDEN_VALUE+" ชิ้น":"ขนนกจากปีกของ Seraph",NamedTextColor.GRAY).decoration(TextDecoration.ITALIC,false),
            Component.text("นำไปแลกของที่จุดเกิด Evergarden",NamedTextColor.AQUA).decoration(TextDecoration.ITALIC,false)));
        NamespacedKey model=golden?GOLDEN_MODEL:MODEL;
        meta.setItemModel(model);
        var data=meta.getCustomModelDataComponent();data.setStrings(List.of(model.toString()));meta.setCustomModelDataComponent(data);
        if(golden)meta.setEnchantmentGlintOverride(true);
        meta.getPersistentDataContainer().set(plugin.key(golden?"seraph_feather_golden":"seraph_feather"),PersistentDataType.BYTE,(byte)1);
        item.setItemMeta(meta);return item;
    }
    public static ItemStack feather(VoidscapePlugin plugin,int amount){return make(plugin,false,amount);}
    public static ItemStack golden(VoidscapePlugin plugin,int amount){return make(plugin,true,amount);}
    public static boolean isFeather(VoidscapePlugin plugin,ItemStack item){
        return item!=null&&item.getType()==Material.FEATHER&&item.hasItemMeta()&&item.getItemMeta().getPersistentDataContainer().has(plugin.key("seraph_feather"),PersistentDataType.BYTE);
    }
    public static boolean isGolden(VoidscapePlugin plugin,ItemStack item){
        return item!=null&&item.getType()==Material.FEATHER&&item.hasItemMeta()&&item.getItemMeta().getPersistentDataContainer().has(plugin.key("seraph_feather_golden"),PersistentDataType.BYTE);
    }
    /** Feathers carried, counting golden ones at their value. */
    public int carried(Player player){
        int total=0;
        for(ItemStack item:player.getInventory().getStorageContents()){
            if(isFeather(plugin,item))total+=item.getAmount();else if(isGolden(plugin,item))total+=item.getAmount()*GOLDEN_VALUE;
        }
        return total;
    }

    // ---- periods and the ledger ----
    static String day(){return LocalDate.now().toString();}
    static String week(){LocalDate now=LocalDate.now();return now.get(IsoFields.WEEK_BASED_YEAR)+"-W"+now.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);}
    private String path(UUID id,String rest){return "players."+id+"."+rest;}
    public int dailyCap(){return plugin.integer("seraph-trader.feathers.daily-cap",60,0,100000);}
    public int earnedToday(UUID id){return day().equals(ledger.getString(path(id,"day")))?ledger.getInt(path(id,"earned")):0;}
    /** Times this player used a shop category in the current day or week. */
    int used(UUID id,String category,boolean weekly){
        String period=weekly?week():day();
        return period.equals(ledger.getString(path(id,"limits."+category+".period")))?ledger.getInt(path(id,"limits."+category+".count")):0;
    }
    void use(UUID id,String category,boolean weekly){
        String period=weekly?week():day(),base=path(id,"limits."+category);
        int count=period.equals(ledger.getString(base+".period"))?ledger.getInt(base+".count"):0;
        ledger.set(base+".period",period);ledger.set(base+".count",count+1);dirty=true;
    }

    // ---- earning ----
    /**
     * Gives feathers now. Capped sources stop at the daily cap; one-time rewards and loot ignore it.
     * Returns the number given.
     */
    public int grant(Player player,int amount,String reason,boolean capped){
        if(locked||player==null||!player.isOnline()||amount<=0)return 0;
        UUID id=player.getUniqueId();
        if(capped){
            int left=Math.max(0,dailyCap()-earnedToday(id));amount=Math.min(amount,left);
            if(amount<=0){player.sendActionBar(Component.text("ขนนกเซราฟจากกิจกรรมวันนี้ครบ "+dailyCap()+" แล้ว",NamedTextColor.GRAY));return 0;}
            if(!day().equals(ledger.getString(path(id,"day")))){ledger.set(path(id,"day"),day());ledger.set(path(id,"earned"),0);}
            ledger.set(path(id,"earned"),ledger.getInt(path(id,"earned"))+amount);dirty=true;
        }
        player.getInventory().addItem(feather(plugin,amount)).values().forEach(left->player.getWorld().dropItemNaturally(player.getLocation(),left));
        player.sendActionBar(Component.text("+"+amount+" ขนนกเซราฟ · "+reason+(capped?" · วันนี้ "+earnedToday(id)+"/"+dailyCap():""),NamedTextColor.YELLOW));
        player.playSound(player.getLocation(),Sound.ENTITY_EXPERIENCE_ORB_PICKUP,.5f,1.6f);
        return amount;
    }
    /** A reward that each player can earn once ever, e.g. the first tame of a species. */
    public void firstTime(Player player,String key,int amount,String reason){
        if(locked||player==null)return;
        String path=path(player.getUniqueId(),"firsts."+key.replace('.','_'));
        if(ledger.getBoolean(path))return;
        ledger.set(path,true);dirty=true;grant(player,amount,reason,false);
    }
    /** The Seraph's blessing: a few feathers on the first visit of each day. */
    public boolean blessing(Player player){
        if(locked)return false;
        String path=path(player.getUniqueId(),"blessing");
        if(day().equals(ledger.getString(path)))return false;
        ledger.set(path,day());dirty=true;
        return grant(player,plugin.integer("seraph-trader.feathers.blessing",5,0,64),"พรประจำวันจาก Seraph",false)>0;
    }
    public boolean blessedToday(Player player){return day().equals(ledger.getString(path(player.getUniqueId(),"blessing")));}

    /**
     * Treasure for a freshly authored structure chest: a few feathers once per chest, keyed in the
     * chunk so breaking, replacing or reopening the chest never rolls again.
     */
    public static void treasure(VoidscapePlugin plugin,Chest chest,String profile){
        if(plugin==null||!plugin.getConfig().getBoolean("seraph-trader.feathers.chest-loot",true))return;
        var key=new NamespacedKey("evergarden","seraph_feather_"+chest.getX()+"_"+chest.getY()+"_"+chest.getZ());
        var data=chest.getChunk().getPersistentDataContainer();if(data.has(key))return;
        data.set(key,PersistentDataType.BYTE,(byte)1);
        int[] range=switch(profile){case "boss","sanctuary"->new int[]{3,5};case "landmark"->new int[]{2,3};default->new int[]{1,2};};
        long seed=chest.getWorld().getSeed()^((long)chest.getX()*73428767L)^((long)chest.getZ()*912931L)^((long)chest.getY()*4217)^0x464541544845L;
        int amount=range[0]+new Random(seed).nextInt(range[1]-range[0]+1);
        int slot=chest.getBlockInventory().firstEmpty();
        if(slot>=0)chest.getBlockInventory().setItem(slot,feather(plugin,amount));
    }

    // ---- feathers are currency, not crafting material ----
    @EventHandler public void craft(PrepareItemCraftEvent event){
        for(ItemStack item:event.getInventory().getMatrix())if(isFeather(plugin,item)||isGolden(plugin,item)){event.getInventory().setResult(null);return;}
    }
    /** A renamed feather would no longer match the Seraph's trades; anvils and grindstones refuse them. */
    @EventHandler public void result(com.destroystokyo.paper.event.inventory.PrepareResultEvent event){
        for(ItemStack item:event.getInventory().getContents())if(isFeather(plugin,item)||isGolden(plugin,item)){event.setResult(null);return;}
    }
    @EventHandler public void crafter(CrafterCraftEvent event){
        if(event.getBlock().getState() instanceof org.bukkit.block.Crafter crafter)
            for(ItemStack item:crafter.getInventory().getContents())if(isFeather(plugin,item)||isGolden(plugin,item)){event.setCancelled(true);return;}
    }

    void save(){
        if(!dirty||locked)return;
        dirty=false;
        try{
            Path target=file.toPath(),temporary=target.resolveSibling(file.getName()+".tmp");
            Files.createDirectories(target.getParent());Files.writeString(temporary,ledger.saveToString());
            try{Files.move(temporary,target,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
            catch(AtomicMoveNotSupportedException unsupported){Files.move(temporary,target,StandardCopyOption.REPLACE_EXISTING);}
        }catch(IOException error){dirty=true;plugin.getLogger().severe("Cannot save seraph-feathers.yml: "+error);}
    }
    @Override public void close(){saver.cancel();save();HandlerList.unregisterAll(this);}
}
