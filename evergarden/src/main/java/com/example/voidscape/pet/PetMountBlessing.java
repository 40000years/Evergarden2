package com.example.voidscape.pet;

import com.example.voidscape.VoidscapePlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.potion.*;
import java.util.*;

/** Rider-only blessing. Finite effects are refreshed without replenishing consumed absorption. */
final class PetMountBlessing implements Listener,AutoCloseable {
    private static final List<PotionEffectType> TYPES=List.of(PotionEffectType.NIGHT_VISION,
        PotionEffectType.REGENERATION,PotionEffectType.STRENGTH,PotionEffectType.SPEED,PotionEffectType.ABSORPTION);
    private final VoidscapePlugin plugin;
    private final Map<UUID,Set<PotionEffectType>> owned=new HashMap<>();
    private final Map<UUID,Long> shieldReady=new HashMap<>();
    private boolean applying;
    PetMountBlessing(VoidscapePlugin plugin){this.plugin=plugin;Bukkit.getPluginManager().registerEvents(this,plugin);}
    void tick(Player player){
        var effects=owned.computeIfAbsent(player.getUniqueId(),id->new HashSet<>());
        for(var type:TYPES){
            PotionEffect previous=player.getPotionEffect(type);
            boolean refresh=effects.contains(type)&&previous!=null&&previous.getDuration()<=400;
            if(effects.contains(type)&&!refresh)continue;
            // Leave equal and stronger effects from potions, beacons and other plugins alone.
            if(previous!=null&&!refresh)continue;
            if(!refresh&&type==PotionEffectType.ABSORPTION&&System.currentTimeMillis()<shieldReady.getOrDefault(player.getUniqueId(),0L))continue;
            double shield=player.getAbsorptionAmount();
            applying=true;
            try{
                if(player.addPotionEffect(new PotionEffect(type,1200,0,true,false,true))){
                    effects.add(type);
                    if(type==PotionEffectType.ABSORPTION&&refresh)player.setAbsorptionAmount(shield);
                    if(type==PotionEffectType.ABSORPTION&&!refresh){
                        int cooldown=Math.clamp(plugin.getConfig().getInt("pets.riding.aetherius.absorption-cooldown-seconds",120),30,600);
                        shieldReady.put(player.getUniqueId(),System.currentTimeMillis()+cooldown*1000L);
                    }
                }
            }finally{applying=false;}
        }
    }
    void stop(Player player){
        var effects=owned.remove(player.getUniqueId());if(effects==null)return;
        applying=true;
        try{for(var type:effects){
            PotionEffect current=player.getPotionEffect(type);
            if(current!=null&&current.getAmplifier()==0&&current.isAmbient())player.removePotionEffect(type);
        }}finally{applying=false;}
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void changed(EntityPotionEffectEvent event){
        if(applying)return;
        var effects=owned.get(event.getEntity().getUniqueId());
        if(effects!=null)effects.remove(event.getModifiedType());
    }
    @Override public void close(){
        for(UUID id:new ArrayList<>(owned.keySet())){Player player=Bukkit.getPlayer(id);if(player!=null)stop(player);}
        owned.clear();shieldReady.clear();HandlerList.unregisterAll(this);
    }
}
