package com.example.voidscape.world;

import com.example.voidscape.VoidscapePlugin;
import org.bukkit.block.Block;
import org.bukkit.block.data.Directional;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.event.world.*;

/** Protect generated landmarks and their reserved headroom, within the Evergarden world only. */
public final class StructureProtection implements Listener {
    private final VoidscapePlugin plugin;
    private final LegacyLandmarkProtection legacy;
    public StructureProtection(VoidscapePlugin plugin){this.plugin=plugin;legacy=new LegacyLandmarkProtection(plugin);}
    public boolean protects(Block block){
        if(block==null)return false;
        if(plugin.overworldSanctuaries()!=null&&plugin.overworldSanctuaries().habitat().protectedAt(block.getLocation()))return true;
        if(block.getWorld()!=plugin.world())return false;
        int x=block.getX(),y=block.getY(),z=block.getZ();
        if(plugin.bossStructures()!=null&&y>=BossStructure.MIN_Y){
            var boss=plugin.bossStructures().at(x,z,0);
            if(boss!=null&&y<=boss.kind().maxY+16&&plugin.bossStructures().enabled(boss.kind())
                    &&plugin.getConfig().getBoolean("structures.boss-structures."+boss.kind().id+".enabled",true))return true;
        }
        if(plugin.getConfig().getBoolean("structures.world-boss-temple.enabled",true)
            &&y>=WorldBossTemple.MIN_Y&&y<=WorldBossTemple.MAX_Y+24
            &&plugin.bossTemples()!=null&&plugin.bossTemples().at(x,z,0)!=null)return true;
        if(plugin.getConfig().getBoolean("structures.sky-whale.enabled",true)
            &&y>=SkyWhale.MIN_Y&&y<=SkyWhale.MAX_Y
            &&plugin.skyWhales()!=null&&plugin.skyWhales().at(x,z,0)!=null)return true;
        if(y<LandmarkBlueprint.MIN_Y||y>LandmarkBlueprint.MAX_Y||plugin.skyLandmarks()==null)return false;
        var site=plugin.skyLandmarks().at(x,z,0);
        if(site!=null&&plugin.getConfig().getBoolean("structures."+site.kind().id+".enabled",true))return true;
        return legacy.protects(block);
    }
    private boolean bypass(org.bukkit.entity.Player player){return plugin.dungeons().canBypassProtection(player);}
    // Cancel before mining abilities run, then enforce again after normal handlers.
    @EventHandler(priority=EventPriority.LOWEST,ignoreCancelled=true)
    public void breakBlock(BlockBreakEvent e){if(protects(e.getBlock())&&!bypass(e.getPlayer()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST)
    public void enforceBreak(BlockBreakEvent e){breakBlock(e);}
    @EventHandler(priority=EventPriority.LOWEST,ignoreCancelled=true)
    public void damage(BlockDamageEvent e){if(protects(e.getBlock())&&!bypass(e.getPlayer()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST)
    public void enforceDamage(BlockDamageEvent e){damage(e);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void place(BlockPlaceEvent e){if(protects(e.getBlockPlaced())&&!bypass(e.getPlayer()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void bucketEmpty(PlayerBucketEmptyEvent e){if((protects(e.getBlock())||protects(e.getBlockClicked().getRelative(e.getBlockFace())))&&!bypass(e.getPlayer()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void bucketFill(PlayerBucketFillEvent e){if(protects(e.getBlock())&&!bypass(e.getPlayer()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void interact(PlayerInteractEvent e){
        if(!protects(e.getClickedBlock())||bypass(e.getPlayer()))return;
        if(e.getAction()==org.bukkit.event.block.Action.PHYSICAL){e.setCancelled(true);return;}
        if(e.getAction()!=org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK||e.getItem()==null)return;
        String item=e.getItem().getType().name();
        if(item.endsWith("_HOE")||item.endsWith("_SHOVEL")||item.endsWith("_AXE")
            ||item.equals("FLINT_AND_STEEL")||item.equals("FIRE_CHARGE"))e.setCancelled(true);
    }
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
    public void entityPlace(EntityPlaceEvent e){if(protects(e.getEntity().getLocation().getBlock())&&(e.getPlayer()==null||!bypass(e.getPlayer())))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void hangingPlace(org.bukkit.event.hanging.HangingPlaceEvent e){if(protects(e.getBlock())&&(e.getPlayer()==null||!bypass(e.getPlayer())))e.setCancelled(true);}
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
