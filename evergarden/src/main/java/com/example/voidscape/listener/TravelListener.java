package com.example.voidscape.listener;

import com.example.voidscape.VoidscapePlugin;
import com.example.voidscape.world.DungeonLayout.Site;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Orientable;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;
import java.util.*;

public final class TravelListener implements Listener {
    private final VoidscapePlugin plugin;
    private final Map<UUID,Long> standing=new HashMap<>(),pending=new HashMap<>(),fallGrace=new HashMap<>();
    private final Map<UUID,UUID> flowerOfferingOwners=new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<UUID,Long> flowerOfferingExpires=new java.util.concurrent.ConcurrentHashMap<>();
    private final PortalVisuals visuals;
    public TravelListener(VoidscapePlugin plugin){
        this.plugin=plugin;visuals=new PortalVisuals(plugin);
        // Upgrade the built-in return gate only when its expected frame exists.
        World w=plugin.world();
        boolean intact=true;
        for(int x=-1;x<=2;x++)for(int y=96;y<=100;y++) {
            if(x!=-1&&x!=2&&y!=96&&y!=100)continue;
            Material m=w.getBlockAt(x,y,-5).getType();
            if(m!=Material.CRYING_OBSIDIAN&&m!=Material.QUARTZ_BLOCK)intact=false;
        }
        if(intact) {
            for(int x=-1;x<=2;x++)for(int y=96;y<=100;y++) {
                Block b=w.getBlockAt(x,y,-5);
                if(x==-1||x==2||y==96||y==100)b.setType(Material.QUARTZ_BLOCK,false);
                else visuals.add(b,Axis.X);
            }
            visuals.save();
        }
    }

    private boolean allowedEntryWorld(Player p){
        if (p == null || p.getWorld() == plugin.world()) return false;
        List<String> list = plugin.getConfig().getStringList("portal.entry-worlds");
        if (list.isEmpty() || list.contains("*") || list.contains("all")) return true;
        String name = p.getWorld().getName();
        if (list.contains(name)) return true;
        if (list.contains("world")) {
            if (name.equals("world_nether") || name.equals("world_the_end")
                || p.getWorld().getEnvironment() == World.Environment.NORMAL
                || p.getWorld().getEnvironment() == World.Environment.NETHER) {
                return true;
            }
        }
        return false;
    }

    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=false)
    public void interact(PlayerInteractEvent e) {
        Player p=e.getPlayer();
        ItemStack hand=e.getItem();
        if(hand==null && e.getHand()!=null) hand=p.getInventory().getItem(e.getHand());

        // Guide book interaction (both Java & Bedrock, clicking block or air)
        if(e.getAction().isRightClick() && hand!=null && hand.getType()==Material.WRITTEN_BOOK && isGuideBook(hand)) {
            e.setCancelled(true);
            if(e.getHand()==org.bukkit.inventory.EquipmentSlot.HAND) {
                var bookType = plugin.relics().getGuideBookType(hand);
                if (bookType != null) {
                    com.example.voidscape.guide.BedrockGuideService.openGuide(plugin, p, bookType, 0);
                } else {
                    com.example.voidscape.guide.BedrockGuideService.openMenu(plugin, p);
                }
            }
            return;
        }

        Block clicked=e.getClickedBlock();
        if(clicked==null) clicked=p.getTargetBlockExact(5);
        if(clicked==null) return;

        // Spawn Lectern Guide Book interaction
        if(e.getAction().isRightClick() && clicked.getWorld()==plugin.world() && clicked.getType()==Material.LECTERN && clicked.getX()==0 && clicked.getZ()==4) {
            e.setCancelled(true);
            if(e.getHand()==org.bukkit.inventory.EquipmentSlot.HAND) {
                com.example.voidscape.guide.BedrockGuideService.openMenu(plugin, p);
            }
            return;
        }

        if(hand==null) return;

        // Anti-Boat Cheese: prevent placing boats/minecarts near shrines in the void
        if(e.getAction().isRightClick() && clicked.getWorld()==plugin.world() && isVehicleItem(hand.getType())) {
            Site site=plugin.layout().at(clicked.getX(),clicked.getZ(),8);
            if(site!=null) {
                e.setCancelled(true);
                plugin.message(p,"มนต์สะกดของวิหารขัดขวางไม่ให้ใช้เรือหรือยานพาหนะ!");
                return;
            }
        }

        // Flower Ignition by Hand (Both Right-Click and Left-Click / Mobile Tap on quartz frame or opening)
        if(isPortalFlower(hand.getType()) && allowedEntryWorld(p) && p.getWorld()!=plugin.world()) {
            BlockFace face = e.getBlockFace() != null ? e.getBlockFace() : BlockFace.UP;
            Block[] candidates = new Block[] {
                clicked.getRelative(face),
                clicked,
                clicked.getRelative(BlockFace.UP),
                clicked.getRelative(BlockFace.DOWN),
                clicked.getRelative(BlockFace.NORTH),
                clicked.getRelative(BlockFace.SOUTH),
                clicked.getRelative(BlockFace.EAST),
                clicked.getRelative(BlockFace.WEST)
            };
            for(Block candidate : candidates) {
                if(candidate.getType() == Material.AIR || candidate.getType() == Material.FIRE || candidate.getType() == Material.CAVE_AIR) {
                    if(tryIgnitePortal(candidate, p)) {
                        e.setCancelled(true);
                        if(p.getGameMode() != GameMode.CREATIVE) {
                            hand.subtract(1);
                            p.updateInventory();
                        }
                        return;
                    }
                }
            }
            if(clicked.getType() == Material.QUARTZ_BLOCK) {
                p.sendActionBar(net.kyori.adventure.text.Component.text(
                    "✦ กรอบประตูควอตซ์ต้องมีขนาดภายนอก 4x5 บล็อก และช่องว่างด้านใน 2x3 บล็อก",
                    net.kyori.adventure.text.format.NamedTextColor.YELLOW));
            }
        }
    }

    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void offerFlower(PlayerDropItemEvent e) {
        Player p=e.getPlayer();
        if(p.getWorld()==plugin.world()||!allowedEntryWorld(p)||!isPortalFlower(e.getItemDrop().getItemStack().getType()))return;
        UUID dropId=e.getItemDrop().getUniqueId();
        flowerOfferingOwners.put(dropId,p.getUniqueId());
        flowerOfferingExpires.put(dropId,System.currentTimeMillis()+10000L);
    }

    private boolean isPortalFlower(Material material) {
        if(material == null) return false;
        if(Tag.FLOWERS.isTagged(material)) return true;
        String name = material.name();
        return name.contains("FLOWER") || name.contains("ROSE") || name.contains("TULIP")
            || name.contains("ORCHID") || name.contains("DAISY") || name.contains("POPPY")
            || name.contains("DANDELION") || name.contains("BLOSSOM") || name.contains("LILAC")
            || name.contains("PEONY") || name.contains("SUNFLOWER") || name.contains("PETAL")
            || material == Material.PINK_PETALS || material == Material.SPORE_BLOSSOM 
            || material == Material.FLOWERING_AZALEA || material == Material.FLOWERING_AZALEA_LEAVES;
    }

    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void onPlaceFlower(BlockPlaceEvent e) {
        Player p = e.getPlayer();
        if(p.getWorld()==plugin.world()||!allowedEntryWorld(p)) return;
        Block placed = e.getBlockPlaced();
        if(!isPortalFlower(placed.getType())) return;
        if(!hasNearbyQuartz(placed)) return;

        Block[] candidates = new Block[] {
            placed,
            placed.getRelative(BlockFace.UP),
            placed.getRelative(BlockFace.DOWN),
            placed.getRelative(BlockFace.NORTH),
            placed.getRelative(BlockFace.SOUTH),
            placed.getRelative(BlockFace.EAST),
            placed.getRelative(BlockFace.WEST)
        };
        for(Block candidate : candidates) {
            Material orig = placed.getType();
            org.bukkit.block.data.BlockData originalData = placed.getBlockData().clone();
            placed.setType(Material.AIR, false);
            if(tryIgnitePortal(candidate, p)) {
                return;
            }
            placed.setType(orig, false);
            placed.setBlockData(originalData, false);
        }
    }

    private boolean hasNearbyQuartz(Block block) {
        for(int x=-2;x<=2;x++) for(int y=-2;y<=2;y++) for(int z=-2;z<=2;z++) {
            if(block.getRelative(x,y,z).getType()==Material.QUARTZ_BLOCK) return true;
        }
        return false;
    }

    private void consumeFlowerOffering(Item item) {
        ItemStack stack=item.getItemStack();
        if(stack.getAmount()<=1)item.remove();
        else {stack.setAmount(stack.getAmount()-1);item.setItemStack(stack);}
    }

    private boolean isVehicleItem(Material m) {
        String name=m.name();
        return name.endsWith("_BOAT")||name.endsWith("_CHEST_BOAT")||name.endsWith("_RAFT")||name.endsWith("_MINECART")||name.equals("MINECART");
    }

    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void vehicleEnter(VehicleEnterEvent e) {
        if(e.getEntered() instanceof LivingEntity living) {
            if(living.getPersistentDataContainer().has(plugin.key("dungeon_mob"),PersistentDataType.STRING)) {
                e.setCancelled(true);
                if(e.getVehicle() instanceof Boat||e.getVehicle() instanceof Minecart) {
                    Bukkit.getScheduler().runTask(plugin,()->e.getVehicle().remove());
                }
            }
        }
    }

    private boolean tryIgnitePortal(Block inner,Player p) {
        if(inner.getType()!=Material.AIR&&inner.getType()!=Material.CAVE_AIR&&inner.getType()!=Material.FIRE&&inner.getType()!=Material.SOUL_FIRE)return false;
        // Test X-Axis orientation
        if(checkAndFillPortal(inner,Axis.X)) {
            portalIgniteFeedback(inner,p);
            return true;
        }
        // Test Z-Axis orientation
        if(checkAndFillPortal(inner,Axis.Z)) {
            portalIgniteFeedback(inner,p);
            return true;
        }
        return false;
    }

    private void portalIgniteFeedback(Block b,Player p) {
        Location loc=b.getLocation().add(0.5,0.5,0.5);
        p.getWorld().playSound(loc,Sound.BLOCK_RESPAWN_ANCHOR_SET_SPAWN,1.0f,0.8f);
        p.getWorld().playSound(loc,Sound.BLOCK_END_PORTAL_SPAWN,0.8f,1.2f);
        p.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME,loc,40,1.0,1.5,1.0,0.01);
        p.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME,loc,20,0.5,1.0,0.5,0.03);
        plugin.message(p,"ประตูมิติความว่างเปล่า (Evergarden Portal) เปิดออกแล้ว!");
    }

    private boolean checkAndFillPortal(Block start,Axis axis) {
        World w=start.getWorld();
        int y=start.getY(),x=start.getX(),z=start.getZ();
        // Find bottom inner Y
        while(y>start.getY()-21&&y>w.getMinHeight()+1&&isInnerBlock(w.getBlockAt(x,y-1,z))) y--;
        int minY=y;
        if(w.getBlockAt(x,minY-1,z).getType()!=Material.QUARTZ_BLOCK)return false;

        // Find min and max along axis
        int minD=axis==Axis.X?x:z,maxD=minD;
        while(minD>(axis==Axis.X?x:z)-21&&isInnerBlock(axis==Axis.X?w.getBlockAt(minD-1,minY,z):w.getBlockAt(x,minY,minD-1))) minD--;
        while(maxD<(axis==Axis.X?x:z)+21&&isInnerBlock(axis==Axis.X?w.getBlockAt(maxD+1,minY,z):w.getBlockAt(x,minY,maxD+1))) maxD++;

        int width=maxD-minD+1;
        if(width<2||width>21)return false;

        // Check bottom border
        for(int d=minD;d<=maxD;d++) {
            Block b=axis==Axis.X?w.getBlockAt(d,minY-1,z):w.getBlockAt(x,minY-1,d);
            if(b.getType()!=Material.QUARTZ_BLOCK)return false;
        }

        // Find height
        int curY=minY;
        while(curY<w.getMaxHeight()-1) {
            boolean allInner=true;
            for(int d=minD;d<=maxD;d++) {
                Block b=axis==Axis.X?w.getBlockAt(d,curY,z):w.getBlockAt(x,curY,d);
                if(!isInnerBlock(b)){allInner=false;break;}
            }
            if(!allInner)break;
            // Check side frames
            Block left=axis==Axis.X?w.getBlockAt(minD-1,curY,z):w.getBlockAt(x,curY,minD-1);
            Block right=axis==Axis.X?w.getBlockAt(maxD+1,curY,z):w.getBlockAt(x,curY,maxD+1);
            if(left.getType()!=Material.QUARTZ_BLOCK||right.getType()!=Material.QUARTZ_BLOCK)return false;
            curY++;
        }
        int maxY=curY-1;
        int height=maxY-minY+1;
        if(height<3||height>21)return false;

        // Check top border
        for(int d=minD;d<=maxD;d++) {
            Block b=axis==Axis.X?w.getBlockAt(d,maxY+1,z):w.getBlockAt(x,maxY+1,d);
            if(b.getType()!=Material.QUARTZ_BLOCK)return false;
        }

        // Fill inner with NETHER_PORTAL
        for(int h=minY;h<=maxY;h++) {
            for(int d=minD;d<=maxD;d++) {
                Block b=axis==Axis.X?w.getBlockAt(d,h,z):w.getBlockAt(x,h,d);
                visuals.add(b,axis);
                if(b.getBlockData() instanceof Orientable orient) {
                    orient.setAxis(axis);
                    b.setBlockData(orient,false);
                }
            }
        }
        visuals.save();visuals.tick();
        return true;
    }

    private boolean isInnerBlock(Block b) {
        Material m=b.getType();
        return m==Material.AIR||m==Material.CAVE_AIR||m==Material.FIRE||m==Material.SOUL_FIRE||m==Material.STRUCTURE_VOID;
    }

    public boolean isQuartzPortal(Block portalBlock) {
        if(portalBlock==null||!visuals.contains(portalBlock))return false;
        Queue<Block> queue=new ArrayDeque<>();
        Set<Block> visited=new HashSet<>();
        queue.add(portalBlock);
        visited.add(portalBlock);
        while(!queue.isEmpty()&&visited.size()<=128) {
            Block curr=queue.poll();
            for(BlockFace face:new BlockFace[]{BlockFace.NORTH,BlockFace.SOUTH,BlockFace.EAST,BlockFace.WEST,BlockFace.UP,BlockFace.DOWN}) {
                Block adj=curr.getRelative(face);
                if(adj.getType()==Material.QUARTZ_BLOCK)return true;
                if(adj.getType()==Material.STRUCTURE_VOID&&visited.add(adj)) {
                    queue.add(adj);
                }
            }
        }
        return false;
    }

    private Block findQuartzPortalBlock(Player p, Location from) {
        if(from!=null&&from.getWorld()==p.getWorld()&&from.getBlock().getType()==Material.STRUCTURE_VOID) {
            if(isQuartzPortal(from.getBlock())) return from.getBlock();
        }
        Block feet=p.getLocation().getBlock();
        if(feet.getType()==Material.STRUCTURE_VOID&&isQuartzPortal(feet)) return feet;
        Block eye=p.getEyeLocation().getBlock();
        if(eye.getType()==Material.STRUCTURE_VOID&&isQuartzPortal(eye)) return eye;
        Location loc=p.getLocation();
        int px=loc.getBlockX(),py=loc.getBlockY(),pz=loc.getBlockZ();
        for(int dx=-1;dx<=1;dx++) {
            for(int dy=-1;dy<=2;dy++) {
                for(int dz=-1;dz<=1;dz++) {
                    Block b=loc.getWorld().getBlockAt(px+dx,py+dy,pz+dz);
                    if(b.getType()==Material.STRUCTURE_VOID&&isQuartzPortal(b)) {
                        return b;
                    }
                }
            }
        }
        return null;
    }

    @EventHandler(priority=EventPriority.NORMAL,ignoreCancelled=true)
    public void blockPhysics(BlockPhysicsEvent e) {
        if(e.getBlock().getType()==Material.STRUCTURE_VOID) {
            if(isQuartzPortal(e.getBlock())) {
                e.setCancelled(true);
            }
        }
    }

    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void portalEvent(PlayerPortalEvent e) {
        Player p=e.getPlayer();
        if(p.getWorld()==plugin.world()&&visuals.contains(e.getFrom().getBlock())) {
            e.setCancelled(true);
            leave(p,false);
            return;
        }
        Block block=findQuartzPortalBlock(p,e.getFrom());
        if(block!=null) {
            e.setCancelled(true);
            enter(p);
        }
    }

    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void move(PlayerMoveEvent e) {
        if(!e.hasChangedBlock())return;
        Player p=e.getPlayer();
        Block b=p.getLocation().getBlock();
        if(!visuals.contains(b)) {
            Block eye = p.getEyeLocation().getBlock();
            if (visuals.contains(eye)) {
                b = eye;
            } else {
                b = findQuartzPortalBlock(p, p.getLocation());
                if (b == null) return;
            }
        }
        if(p.getWorld()==plugin.world()) {
            // Return portal at spawn island
            if(Math.abs(b.getX())<=3&&b.getZ()<=-4&&b.getZ()>=-6) {
                leave(p,false);
            }
        } else if(allowedEntryWorld(p)) {
            if(findQuartzPortalBlock(p,b.getLocation())!=null) {
                enter(p);
            }
        }
    }

    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void breakLecternOrFrame(BlockBreakEvent e) {
        Block b=e.getBlock();
        if(b.getWorld()==plugin.world()&&b.getX()==0&&b.getY()==97&&b.getZ()==4) {
            if(e.getPlayer().getGameMode()!=GameMode.CREATIVE||!(e.getPlayer().hasPermission("voidscape.admin")||e.getPlayer().isOp())) {
                e.setCancelled(true);
                return;
            }
        }
        handlePortalBreak(b);
    }

    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void takeLecternBook(PlayerTakeLecternBookEvent e) {
        if(e.getLectern().getWorld()==plugin.world()&&e.getLectern().getX()==0&&e.getLectern().getZ()==4) {
            e.setCancelled(true);
        }
    }

    private boolean isGuideBook(ItemStack item) {
        if(item==null||!item.hasItemMeta())return false;
        if(item.getItemMeta().getPersistentDataContainer().has(plugin.key("guide_book"),PersistentDataType.BYTE))return true;
        if(item.getItemMeta() instanceof org.bukkit.inventory.meta.BookMeta bm) {
            String title = bm.getTitle();
            if (title != null) {
                for (var t : com.example.voidscape.guide.GuideBookType.values()) {
                    if (title.equals(t.bookTitle)) return true;
                }
            }
            return "คู่มือมิติ Evergarden".equals(bm.getTitle());
        }
        return false;
    }

    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void explode(EntityExplodeEvent e) {
        for(Block b:e.blockList()) handlePortalBreak(b);
    }

    private void handlePortalBreak(Block broken) {
        if(broken.getType()!=Material.QUARTZ_BLOCK&&broken.getType()!=Material.STRUCTURE_VOID)return;
        if(broken.getType()==Material.STRUCTURE_VOID) {
            clearPortal(broken,new HashSet<>());
        }
        for(BlockFace face:new BlockFace[]{BlockFace.NORTH,BlockFace.SOUTH,BlockFace.EAST,BlockFace.WEST,BlockFace.UP,BlockFace.DOWN}) {
            Block adj=broken.getRelative(face);
            if(adj.getType()==Material.STRUCTURE_VOID) {
                clearPortal(adj,new HashSet<>());
            }
        }
        visuals.save();
    }

    private void clearPortal(Block b,Set<Block> seen) {
        if(!visuals.contains(b)||!seen.add(b)||seen.size()>500)return;
        visuals.remove(b);
        b.setType(Material.AIR);
        for(BlockFace face:new BlockFace[]{BlockFace.NORTH,BlockFace.SOUTH,BlockFace.EAST,BlockFace.WEST,BlockFace.UP,BlockFace.DOWN}) {
            clearPortal(b.getRelative(face),seen);
        }
    }

    public void enter(Player p) {
        if(standing.getOrDefault(p.getUniqueId(),0L)>System.currentTimeMillis())return;
        if(p.getWorld()==plugin.world()||pending.containsKey(p.getUniqueId()))return;
        Location from=p.getLocation();
        teleport(p,new Location(plugin.world(),0.5,97.0,0.5),()->{
            p.getPersistentDataContainer().set(plugin.key("return_location"),PersistentDataType.STRING,
                from.getWorld().getUID()+","+from.getX()+","+from.getY()+","+from.getZ()+","+from.getYaw()+","+from.getPitch());
            // Deliver guide books if first time
            if(!p.getPersistentDataContainer().has(plugin.key("has_guide"),PersistentDataType.BYTE)) {
                p.getPersistentDataContainer().set(plugin.key("has_guide"),PersistentDataType.BYTE,(byte)1);
                for (com.example.voidscape.guide.GuideBookType gType : com.example.voidscape.guide.GuideBookType.values()) {
                    ItemStack book = plugin.relics().createGuideBook(gType);
                    var leftover = p.getInventory().addItem(book);
                    if(!leftover.isEmpty()) p.getWorld().dropItemNaturally(p.getLocation(), book);
                }
                plugin.message(p,"ยินดีต้อนรับสู่ Evergarden! มอบชุดคู่มือแนะนำการเล่นครบทั้ง 3 เล่มให้แล้ว");
            }
            plugin.message(p,"✦ เดินตามสะพานแสงหรือใช้ Elytra สำรวจเพื่อค้นหาวิหารทั้ง 3 · /evergarden guide");
            p.playSound(p.getLocation(),Sound.BLOCK_PORTAL_TRAVEL,0.7f,1.0f);
        });
    }

    public void leave(Player p,boolean rescued) {
        if(!rescued&&standing.getOrDefault(p.getUniqueId(),0L)>System.currentTimeMillis())return;
        if(pending.containsKey(p.getUniqueId()))return;
        if(!rescued&&plugin.dungeons().inCombat(p)){plugin.message(p,"ยังอยู่ระหว่างต่อสู้ · ออกจากเขตดันแล้วรอ 10 วินาที");return;}
        Location to=returnLocation(p);
        teleport(p,to,()->{
            if(rescued){p.setHealth(Math.min(p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue(),8));plugin.message(p,"มิติส่งคุณกลับ · เก็บอุปกรณ์ไว้ แต่การต่อสู้ยังไม่สำเร็จ");}
            else plugin.message(p,"กลับจากมิติ Evergarden แล้ว");
        });
    }

    private Location returnLocation(Player p) {
        String value=p.getPersistentDataContainer().get(plugin.key("return_location"),PersistentDataType.STRING);
        if(value!=null)try {
            String[] a=value.split(",");World w=visuals.resolveWorld(a[0]);
            if(w!=null&&w!=plugin.world()) {
                Location location=new Location(w,Double.parseDouble(a[1]),Double.parseDouble(a[2]),Double.parseDouble(a[3]),Float.parseFloat(a[4]),Float.parseFloat(a[5]));
                if(location.getY()>w.getMinHeight()+2&&w.getWorldBorder().isInside(location))return location;
            }
        }catch(RuntimeException ignored){}
        Location bed=p.getRespawnLocation();
        if(bed!=null&&bed.getWorld()!=plugin.world())return bed;
        return Bukkit.getWorlds().stream().filter(w->w!=plugin.world()&&w.getEnvironment()==World.Environment.NORMAL).findFirst().orElse(Bukkit.getWorlds().get(0)).getSpawnLocation();
    }

    private void teleport(Player p,Location destination,Runnable done) {
        UUID id=p.getUniqueId();pending.put(id,System.currentTimeMillis()+15000);
        p.teleportAsync(destination).whenComplete((success,error)->Bukkit.getScheduler().runTask(plugin,()->{
            pending.remove(id);
            if(!p.isOnline())return;
            if(error==null&&Boolean.TRUE.equals(success)) {standing.put(id,System.currentTimeMillis()+3000);p.setFallDistance(0);p.setVelocity(new Vector());fallGrace.put(id,System.currentTimeMillis()+5000);done.run();}
            else plugin.message(p,"วาร์ปไม่สำเร็จหรือถูกระบบอื่นปฏิเสธ");
        }));
    }

    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void damage(EntityDamageEvent e) {
        if(!(e.getEntity() instanceof Player p))return;
        if(p.getWorld()!=plugin.world())return;
        if(p.getGameMode()==GameMode.CREATIVE||p.getGameMode()==GameMode.SPECTATOR)return;
        long now=System.currentTimeMillis();
        if(plugin.relics().immune(p)||pending.getOrDefault(p.getUniqueId(),0L)>now){e.setCancelled(true);return;}
        // Void rescue: safe return if falling into the void
        if(e.getCause()==EntityDamageEvent.DamageCause.VOID || p.getLocation().getY() < 0) {
            e.setCancelled(true);
            leave(p, true);
            return;
        }
        // Safe fall damage inside void dimension (helps with Elytra gliding)
        if(e.getCause()==EntityDamageEvent.DamageCause.FALL) {
            if(fallGrace.getOrDefault(p.getUniqueId(),0L)>now) { e.setCancelled(true); return; }
            e.setDamage(Math.min(e.getDamage()*0.2,4.0));
        }
    }

    @EventHandler public void quit(PlayerQuitEvent e){UUID id=e.getPlayer().getUniqueId();standing.remove(id);pending.remove(id);fallGrace.remove(id);}

    public void tick() {
        visuals.tick();
        long now=System.currentTimeMillis();
        pending.values().removeIf(end->end<now);
        fallGrace.values().removeIf(end->end<now);
        for(UUID dropId:new ArrayList<>(flowerOfferingExpires.keySet())) {
            Long expire=flowerOfferingExpires.get(dropId);
            UUID ownerId=flowerOfferingOwners.get(dropId);
            if(expire==null||ownerId==null||now>expire) {
                flowerOfferingExpires.remove(dropId);
                flowerOfferingOwners.remove(dropId);
                continue;
            }
            Entity entity=Bukkit.getEntity(dropId);
            if(!(entity instanceof Item item)||!item.isValid()) {
                flowerOfferingExpires.remove(dropId);
                flowerOfferingOwners.remove(dropId);
                continue;
            }
            Player owner=Bukkit.getPlayer(ownerId);
            if(owner==null||!owner.isOnline()||owner.getWorld()!=item.getWorld())continue;
            Block base = item.getLocation().getBlock();
            boolean ignited = false;
            for (int dx = -1; dx <= 1 && !ignited; dx++) {
                for (int dy = 0; dy <= 2 && !ignited; dy++) {
                    for (int dz = -1; dz <= 1 && !ignited; dz++) {
                        Block candidate = base.getRelative(dx, dy, dz);
                        if (candidate.getType() == Material.AIR || candidate.getType() == Material.FIRE || candidate.getType() == Material.CAVE_AIR) {
                            if (tryIgnitePortal(candidate, owner)) {
                                ignited = true;
                                break;
                            }
                        }
                    }
                }
            }
            if(ignited) {
                consumeFlowerOffering(item);
                flowerOfferingExpires.remove(dropId);
                flowerOfferingOwners.remove(dropId);
            }
        }
    }

    public void close() {
        if (visuals != null) {
            visuals.close();
        }
    }
}
