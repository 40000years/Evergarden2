package com.example.voidscape.world;

import com.example.voidscape.VoidscapePlugin;
import com.example.advancemagic.api.MagicTerrainEvent;
import org.bukkit.event.*;

/** Loaded only with Advance Magic; combat damage and visuals remain available. */
public final class StructureMagicProtection implements Listener {
    private final StructureProtection protection;
    public StructureMagicProtection(VoidscapePlugin plugin){protection=plugin.structureProtection();}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void terrain(MagicTerrainEvent event){if(protection.protects(event.getBlock()))event.setCancelled(true);}
}
