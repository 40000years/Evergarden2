import com.example.voidscape.VoidscapePlugin;
import com.example.voidscape.dungeon.DungeonManager;
import com.example.voidscape.item.RelicService.Relic;
import com.example.voidscape.world.DungeonLayout;
import org.bukkit.*;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.YamlConfiguration;
import java.lang.reflect.*;
import java.util.*;

/** Runs only as a separate test plugin in an isolated Paper server. Never shipped in the release JAR. */
public final class IntegrationChecks extends JavaPlugin {
    VoidscapePlugin plugin;int passed;
    void check(boolean value,String message){if(!value)throw new AssertionError(message);passed++;getLogger().info("PASS "+message);}
    static Object field(Object target,String name)throws Exception{Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}
    static void set(Object target,String name,Object value)throws Exception{Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);f.set(target,value);}
    @Override public void onEnable(){Bukkit.getScheduler().runTaskLater(this,()->{
        plugin=(VoidscapePlugin)Bukkit.getPluginManager().getPlugin("Voidscape");
        var site=plugin.layout().locate(0,0,DungeonLayout.Kind.DREADSHIP,5);
        load(site,site.x()/16-4,site.z()/16-4,0);
    },20);}
    void load(DungeonLayout.Site site,int cx,int cz,int n){
        if(n==81){runChecks(site);return;}
        plugin.world().getChunkAtAsync(cx+n/9,cz+n%9,true).whenComplete((chunk,error)->Bukkit.getScheduler().runTask(this,()->{
            if(error!=null){getLogger().log(java.util.logging.Level.SEVERE,"CHUNK TEST FAILED",error);return;}
            chunk.addPluginChunkTicket(this);load(site,cx,cz,n+1);
        }));
    }
    @SuppressWarnings("unchecked") void runChecks(DungeonLayout.Site site) {
        ArmorStand dataHolder=null;
        try {
            check(plugin.isEnabled(),"plugin booted on Paper 26.2");
            check(plugin.world().getBlockAt(0,96,0).getType()==Material.SEA_LANTERN,"actual spawn island generated");
            check(plugin.world().getBlockAt(site.x(),134,site.z()).getType()==Material.DEEPSLATE_TILES,"ship deck generated across real chunks");
            for(Relic relic:Relic.values()){ItemStack item=plugin.relics().create(relic,1);check(plugin.relics().type(item)==relic&&item.getItemMeta().getItemModel().getNamespace().equals("voidscape"),"relic metadata "+relic);}
            check(plugin.relics().type(new ItemStack(Material.NETHERITE_SWORD))==null,"vanilla item cannot impersonate relic");
            World outside=Bukkit.getWorlds().get(0);
            dataHolder=outside.spawn(outside.getSpawnLocation(),ArmorStand.class);
            Inventory storage=Bukkit.createInventory(null,36);
            UUID playerId=UUID.randomUUID();Location location=new Location(plugin.world(),site.x()-24,97,site.z()-16);
            PersistentDataContainer pdc=dataHolder.getPersistentDataContainer();
            PlayerInventory inventory=(PlayerInventory)Proxy.newProxyInstance(getClassLoader(),new Class[]{PlayerInventory.class},(o,m,a)->switch(m.getName()){
                case "getStorageContents","getContents"->storage.getContents();
                case "setStorageContents","setContents"->{storage.setContents((ItemStack[])a[0]);yield null;}
                case "firstEmpty"->storage.firstEmpty();
                case "getItemInMainHand"->storage.getItem(0)==null?new ItemStack(Material.AIR):storage.getItem(0);
                case "addItem"->storage.addItem((ItemStack[])a[0]);
                default->empty(m);
            });
            Player p=(Player)Proxy.newProxyInstance(getClassLoader(),new Class[]{Player.class},(o,m,a)->switch(m.getName()){
                case "getUniqueId"->playerId;case "getPersistentDataContainer"->pdc;case "getInventory"->inventory;
                case "getWorld"->plugin.world();case "getLocation","getEyeLocation"->location.clone();
                case "isOnline"->true;case "getGameMode"->GameMode.SURVIVAL;case "getHealth"->20.0;case "getName"->"IntegrationActor";
                case "equals"->o==a[0];case "hashCode"->playerId.hashCode();
                default->empty(m);
            });
            ItemStack shield=plugin.relics().create(Relic.ETERNAL_AEGIS,1);
            PlayerInteractEvent shieldUse=new PlayerInteractEvent(p,Action.RIGHT_CLICK_AIR,shield,null,BlockFace.SELF,EquipmentSlot.HAND);
            shieldUse.setUseItemInHand(org.bukkit.event.Event.Result.ALLOW);plugin.relics().interact(shieldUse);
            check(plugin.relics().immune(p),"shield activates full immunity");
            EntityDamageEvent denied=new EntityDamageEvent(p,EntityDamageEvent.DamageCause.CUSTOM,10000);
            plugin.relics().shieldDamage(denied);check(denied.isCancelled(),"shield cancels lethal damage");
            // Production claim path with a real Bukkit inventory and PDC, simulating a crash-replayed entitlement.
            YamlConfiguration ledger=(YamlConfiguration)field(plugin.dungeons(),"ledger");String root="pending."+playerId+".test-receipt";
            ledger.set(root+".shards",8);ledger.set(root+".relic",Relic.RIFT_BLADE.name());
            plugin.dungeons().claim(p);check(count(storage,Relic.VOID_SHARD)==8&&count(storage,Relic.RIFT_BLADE)==1,"guaranteed fragments and special reward delivered");
            ledger.set(root+".shards",8);ledger.set(root+".relic",Relic.RIFT_BLADE.name());plugin.dungeons().claim(p);
            check(count(storage,Relic.VOID_SHARD)==8,"persisted receipt prevents replay duplication");
            for(int i=0;i<36;i++)storage.setItem(i,new ItemStack(Material.COBBLESTONE,64));
            ledger.set("pending."+playerId+".full.shards",8);plugin.dungeons().claim(p);
            check(ledger.contains("pending."+playerId+".full"),"full inventory retains unclaimed entitlement");
            // Three actual guardian waves must clear before the boss gate opens.
            int[][] seals={{-25,97,-16},{25,104,16},{-25,111,16}};
            Map<String,Object> encounters=(Map<String,Object>)field(plugin.dungeons(),"active");
            for(int i=0;i<3;i++) {
                var block=plugin.world().getBlockAt(site.x()+seals[i][0],seals[i][1],site.z()+seals[i][2]);
                check(block.getType()==Material.LODESTONE,"seal generated on floor "+i);
                plugin.dungeons().interact(new PlayerInteractEvent(p,Action.RIGHT_CLICK_BLOCK,new ItemStack(Material.AIR),block,BlockFace.UP,EquipmentSlot.HAND));
                Object enc=encounters.get(site.id());Map<UUID,?> mobs=(Map<UUID,?>)field(enc,"mobs");
                check(!mobs.isEmpty(),"custom guardians spawn for seal "+i);
                for(UUID id:new ArrayList<>(mobs.keySet()))((LivingEntity)Bukkit.getEntity(id)).setHealth(0);
                check(Integer.bitCount((Integer)field(enc,"seals"))==i+1,"seal advances only after guardian deaths "+i);
            }
            check(plugin.dungeons().mobCount()==0,"guardian accounting released after deaths");
            // Name alone never joins the authoritative encounter registry.
            Warden impostor=outside.spawn(outside.getSpawnLocation(),Warden.class);impostor.customName(net.kyori.adventure.text.Component.text("Abyssal Warden"));
            int before=ledger.getKeys(true).size();impostor.setHealth(0);check(ledger.getKeys(true).size()==before,"named vanilla Warden gives no dungeon rewards");
            getLogger().info("INTEGRATION COMPLETE: "+passed+" assertions passed");
        }catch(Throwable error){getLogger().log(java.util.logging.Level.SEVERE,"INTEGRATION FAILED after "+passed+" assertions",error);}
        finally{
            if(dataHolder!=null)dataHolder.remove();
            if(plugin!=null&&plugin.world()!=null)plugin.world().removePluginChunkTickets(this);
            Bukkit.getScheduler().runTaskLater(this, Bukkit::shutdown, 20L);
        }
    }
    int count(Inventory inv,Relic r){int n=0;for(ItemStack item:inv.getContents())if(plugin.relics().type(item)==r)n+=item.getAmount();return n;}
    static Object empty(Method m){Class<?> t=m.getReturnType();if(t==boolean.class)return false;if(t==int.class)return 0;if(t==long.class)return 0L;if(t==double.class)return 0.0;if(t==float.class)return 0f;return null;}
}
