package com.example.voidscape.world;

import com.example.voidscape.VoidscapePlugin;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import java.util.*;

/** Cross-client head models. Decorative stands are never saved or used as item storage. */
public final class RestorationVisuals implements Listener {
    private final VoidscapePlugin plugin;
    private final NamespacedKey tag;
    private final Map<RestorationLayout.Site,UUID> displays=new HashMap<>();
    public RestorationVisuals(VoidscapePlugin plugin){
        this.plugin=plugin;tag=plugin.key("restoration_visual");
        Bukkit.getScheduler().runTaskTimer(plugin,this::tick,20,40);
    }
    public RestorationLayout.Site site(Entity entity){
        if(!(entity instanceof ArmorStand)||entity.getWorld()!=plugin.world()||!entity.getPersistentDataContainer().has(tag,PersistentDataType.BYTE))return null;
        Location l=entity.getLocation();
        var site=plugin.restorationLayout().near(l.getBlockX(),l.getBlockY()+2,l.getBlockZ(),2);
        return site!=null&&site.altar()?site:null;
    }
    private void tick(){
        Set<RestorationLayout.Site> desired=new HashSet<>();World world=plugin.world();
        for(Player p:world.getPlayers()){
            Location l=p.getLocation();int size=plugin.restorationLayout().cellSize();
            for(var s:plugin.restorationLayout().cell(Math.floorDiv(l.getBlockX(),size),Math.floorDiv(l.getBlockZ(),size))){
                if(!s.altar()||Math.abs(s.x()-l.getX())>64||Math.abs(s.z()-l.getZ())>64
                        ||!world.isChunkLoaded(s.x()>>4,s.z()>>4))continue;
                if(world.getBlockAt(s.x(),s.y()+1,s.z()).getType()!=Material.LODESTONE
                        ||world.getBlockAt(s.x(),s.y(),s.z()).getType()!=Material.CHISELED_QUARTZ_BLOCK)continue;
                desired.add(s);
                Entity old=displays.containsKey(s)?Bukkit.getEntity(displays.get(s)):null;
                if(old!=null&&old.isValid())continue;
                Location origin=new Location(world,s.x()+.5,s.y()-.5,s.z()+.5);
                // Reconcile any previously loaded tagged stand before creating a replacement.
                for(Entity nearby:world.getNearbyEntities(origin,1,2,1))if(site(nearby)!=null)nearby.remove();
                ArmorStand stand=world.spawn(origin,ArmorStand.class,a->{
                    a.setVisible(false);a.setGravity(false);a.setMarker(false);a.setCollidable(false);
                    a.setInvulnerable(true);a.setSilent(true);a.setBasePlate(false);a.setPersistent(false);
                    a.getPersistentDataContainer().set(tag,PersistentDataType.BYTE,(byte)1);
                    ItemStack item=new ItemStack(Material.IRON_HELMET);var meta=item.getItemMeta();
                    meta.setItemModel(null);var equipment=meta.getEquippable();equipment.setSlot(EquipmentSlot.HEAD);
                    equipment.setModel(null);meta.setEquippable(equipment);
                    var cmd=meta.getCustomModelDataComponent();cmd.setStrings(List.of("voidscape:restoration_altar_"+s.theme().name().toLowerCase(Locale.ROOT)));
                    meta.setCustomModelDataComponent(cmd);item.setItemMeta(meta);a.getEquipment().setHelmet(item,true);
                    for(EquipmentSlot slot:EquipmentSlot.values())for(ArmorStand.LockType lock:ArmorStand.LockType.values())
                        if(slot==EquipmentSlot.HEAD||slot==EquipmentSlot.CHEST||slot==EquipmentSlot.LEGS||slot==EquipmentSlot.FEET||slot==EquipmentSlot.HAND||slot==EquipmentSlot.OFF_HAND)a.addEquipmentLock(slot,lock);
                });
                displays.put(s,stand.getUniqueId());
            }
        }
        for(var entry:List.copyOf(displays.entrySet()))if(!desired.contains(entry.getKey())){
            Entity entity=Bukkit.getEntity(entry.getValue());if(entity!=null)entity.remove();displays.remove(entry.getKey());
        }
    }
    @EventHandler public void manipulate(PlayerArmorStandManipulateEvent e){if(site(e.getRightClicked())!=null)e.setCancelled(true);}
    @EventHandler public void damage(EntityDamageEvent e){if(site(e.getEntity())!=null)e.setCancelled(true);}
    @EventHandler public void death(EntityDeathEvent e){if(site(e.getEntity())!=null){e.getDrops().clear();e.setDroppedExp(0);}}
    public void close(){for(UUID id:displays.values()){Entity e=Bukkit.getEntity(id);if(e!=null)e.remove();}displays.clear();}
}
