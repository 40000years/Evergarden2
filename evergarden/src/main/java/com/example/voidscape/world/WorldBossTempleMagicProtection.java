package com.example.voidscape.world;

import com.example.voidscape.VoidscapePlugin;
import com.example.advancemagic.api.MagicTerrainEvent;
import org.bukkit.event.*;

/** Loaded only with Advance Magic; boss-fight damage and visuals remain available. */
public final class WorldBossTempleMagicProtection implements Listener {
    private final WorldBossTempleProtection protection;
    public WorldBossTempleMagicProtection(VoidscapePlugin plugin){protection=new WorldBossTempleProtection(plugin);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void terrain(MagicTerrainEvent event){if(protection.protects(event.getBlock()))event.setCancelled(true);}
}
