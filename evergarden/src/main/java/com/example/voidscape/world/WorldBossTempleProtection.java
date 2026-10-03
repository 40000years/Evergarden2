package com.example.voidscape.world;

import com.example.voidscape.VoidscapePlugin;
import org.bukkit.block.Block;
import org.bukkit.block.data.Directional;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.event.world.*;

/** Protect the foundation, architecture and reserved combat headroom from ordinary edits. */
public final class WorldBossTempleProtection implements Listener {
    private final VoidscapePlugin plugin;
    public WorldBossTempleProtection(VoidscapePlugin plugin){this.plugin=plugin;}
    public boolean protects(Block block){
        return block!=null&&block.getWorld()==plugin.world()
            &&plugin.getConfig().getBoolean("structures.world-boss-temple.enabled",true)
            &&block.getY()>=WorldBossTemple.MIN_Y&&block.getY()<=WorldBossTemple.MAX_Y+24
            &&plugin.bossTemples()!=null&&plugin.bossTemples().at(block.getX(),block.getZ(),0)!=null;
    }
    private boolean bypass(org.bukkit.entity.Player player){return plugin.dungeons().canBypassProtection(player);}
    // Cancel before mining abilities run, then enforce again after normal handlers.
    @EventHandler(priority=EventPriority.LOWEST,ignoreCancelled=true)
    public void breakBlock(BlockBreakEvent e){if(protects(e.getBlock())&&!bypass(e.getPlayer()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST)
    public void enforceBreak(BlockBreakEvent e){breakBlock(e);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void place(BlockPlaceEvent e){if(protects(e.getBlockPlaced())&&!bypass(e.getPlayer()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void bucketEmpty(PlayerBucketEmptyEvent e){if((protects(e.getBlock())||protects(e.getBlockClicked().getRelative(e.getBlockFace())))&&!bypass(e.getPlayer()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void bucketFill(PlayerBucketFillEvent e){if(protects(e.getBlock())&&!bypass(e.getPlayer()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void extend(BlockPistonExtendEvent e){if(protects(e.getBlock())||protects(e.getBlock().getRelative(e.getDirection()))||e.getBlocks().stream().anyMatch(b->protects(b)||protects(b.getRelative(e.getDirection()))))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void retract(BlockPistonRetractEvent e){if(protects(e.getBlock())||e.getBlocks().stream().anyMatch(b->protects(b)||protects(b.getRelative(e.getDirection()))||protects(b.getRelative(e.getDirection().getOppositeFace()))))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void entityExplosion(EntityExplodeEvent e){e.blockList().removeIf(this::protects);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void blockExplosion(BlockExplodeEvent e){e.blockList().removeIf(this::protects);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void flow(BlockFromToEvent e){if(protects(e.getBlock())||protects(e.getToBlock()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void burn(BlockBurnEvent e){if(protects(e.getBlock()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void ignite(BlockIgniteEvent e){if(protects(e.getBlock())&&(e.getPlayer()==null||!bypass(e.getPlayer())))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void spread(BlockSpreadEvent e){if(protects(e.getBlock())||protects(e.getSource()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void form(BlockFormEvent e){if(protects(e.getBlock()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void fade(BlockFadeEvent e){if(protects(e.getBlock()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void leaves(LeavesDecayEvent e){if(protects(e.getBlock()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void change(EntityChangeBlockEvent e){if(protects(e.getBlock()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void grow(StructureGrowEvent e){e.getBlocks().removeIf(b->protects(b.getBlock()));}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void fertilize(BlockFertilizeEvent e){e.getBlocks().removeIf(b->protects(b.getBlock()));}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void sponge(SpongeAbsorbEvent e){e.getBlocks().removeIf(b->protects(b.getBlock()));}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void portal(PortalCreateEvent e){if(e.getBlocks().stream().anyMatch(b->protects(b.getBlock())))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void dispense(BlockDispenseEvent e){
        var data=e.getBlock().getBlockData();
        if(protects(e.getBlock())||data instanceof Directional d&&protects(e.getBlock().getRelative(d.getFacing())))e.setCancelled(true);
    }
}
