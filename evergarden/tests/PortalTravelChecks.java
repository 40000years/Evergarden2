import com.example.voidscape.VoidscapePlugin;
import com.example.voidscape.listener.PortalVisuals;
import org.bukkit.*;
import org.bukkit.block.data.Orientable;
import org.bukkit.entity.*;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.BoundingBox;
import java.lang.reflect.*;
import java.util.*;

/** Runs against real Paper blocks; a strict player proxy rejects unexpected teleports. */
public final class PortalTravelChecks {
    private static void check(boolean ok, String label) {
        if (!ok) throw new AssertionError(label);
    }
    @SuppressWarnings("unchecked")
    public static void run(VoidscapePlugin garden) throws Exception {
        var travel = garden.travel();
        World world = Bukkit.getWorlds().stream().filter(w -> w != garden.world()).findFirst().orElseThrow();
        int y = 100;
        for (Axis axis : List.of(Axis.X, Axis.Z)) {
            int x = axis == Axis.X ? 40 : 50, z = 40;
            for (int d=-1; d<=2; d++) for(int h=-1; h<=3; h++) {
                var block = world.getBlockAt(x+(axis==Axis.X?d:0), y+h, z+(axis==Axis.Z?d:0));
                block.setType(d==-1||d==2||h==-1||h==3 ? Material.QUARTZ_BLOCK : Material.AIR, false);
            }
            var block=world.getBlockAt(x,y,z);
            Method ignite=travel.getClass().getDeclaredMethod("checkAndFillPortal",org.bukkit.block.Block.class,Axis.class);
            ignite.setAccessible(true);
            check((Boolean)ignite.invoke(travel,block,axis),"quartz gate ignition "+axis);
            check(block.getType()==Material.NETHER_PORTAL,"vanilla portal block "+axis);
            check(((Orientable)block.getBlockData()).getAxis()==axis,"portal orientation "+axis);
            Field visualsField=travel.getClass().getDeclaredField("visuals");visualsField.setAccessible(true);
            PortalVisuals visuals=(PortalVisuals)visualsField.get(travel);
            block.setType(Material.STRUCTURE_VOID,false);visuals.tick();
            check(block.getType()==Material.NETHER_PORTAL,"old blue cell migration");
            ArmorStand stand=world.spawn(block.getLocation(),ArmorStand.class);
            stand.getPersistentDataContainer().set(garden.key("portal_visual"),PersistentDataType.STRING,"legacy");
            visuals.cleanupOrphanDisplays(List.of(stand));
            check(!stand.isValid(),"legacy renderer removed");
            var physics=new BlockPhysicsEvent(block,block.getBlockData(),block);
            travel.blockPhysics(physics);check(physics.isCancelled(),"quartz portal survives physics");

            UUID id=UUID.randomUUID();
            Location[] position={new Location(world,x+0.5,y,z+0.5)};
            Location[] destination={null};
            Player player=(Player)Proxy.newProxyInstance(Player.class.getClassLoader(),new Class[]{Player.class},(proxy,method,args)->switch(method.getName()) {
                case "getUniqueId" -> id;
                case "getWorld" -> world;
                case "getLocation" -> position[0].clone();
                case "getEyeHeight" -> 1.62;
                case "getBoundingBox" -> new BoundingBox(position[0].getX()-0.3,position[0].getY(),position[0].getZ()-0.3,
                    position[0].getX()+0.3,position[0].getY()+1.8,position[0].getZ()+0.3);
                case "teleportAsync" -> {destination[0]=((Location)args[0]).clone();yield new java.util.concurrent.CompletableFuture<Boolean>();}
                default -> throw new AssertionError("Unexpected player call: "+method.getName());
            });
            Method find=travel.getClass().getDeclaredMethod("findQuartzPortalBlock",Player.class,Location.class);find.setAccessible(true);
            check(find.invoke(travel,player,position[0])!=null,"inside portal detected");
            Location outside=position[0].clone().add(axis==Axis.X?0:1,0,axis==Axis.X?1:0);
            check(find.invoke(travel,player,outside)==null,"adjacent player is not pulled in");
            Field latchField=travel.getClass().getDeclaredField("mustExitPortal");latchField.setAccessible(true);
            Set<UUID> latch=(Set<UUID>)latchField.get(travel);latch.add(id);
            travel.enter(player);
            travel.move(new PlayerMoveEvent(player,position[0],position[0].clone()));
            check(latch.contains(id),"standing inside keeps return protection after cooldown");
            travel.move(new PlayerMoveEvent(player,position[0],outside));
            check(!latch.contains(id),"walking out rearms portal");
            Field pendingField=travel.getClass().getDeclaredField("pending");pendingField.setAccessible(true);
            Map<UUID,Long> pending=(Map<UUID,Long>)pendingField.get(travel);
            for(double side:List.of(-0.1,1.1)) {
                Location edge=new Location(world,axis==Axis.X?x+0.5:x+side,y,axis==Axis.X?z+side:z+0.5);
                check(!travel.isQuartzPortal(edge.getBlock()),"edge feet are outside the portal "+axis+" / "+side);
                check(find.invoke(travel,player,edge)!=null,"body contact detected at event position "+axis+" / "+side);
                travel.move(new PlayerMoveEvent(player,outside,edge));
                check(destination[0]!=null&&destination[0].getWorld()==garden.world(),"edge movement targets Evergarden");
                pending.remove(id);destination[0]=null;
                position[0]=edge;
                var event=new PlayerPortalEvent(player,edge,world.getSpawnLocation(),TeleportCause.NETHER_PORTAL);
                travel.portalEvent(event);
                check(event.isCancelled(),"vanilla Nether travel blocked at edge "+axis+" / "+side);
                check(destination[0]!=null&&destination[0].getWorld()==garden.world(),"portal event targets Evergarden");
                pending.remove(id);destination[0]=null;
                latch.add(id);
                var protectedEvent=new PlayerPortalEvent(player,edge,world.getSpawnLocation(),TeleportCause.NETHER_PORTAL);
                travel.portalEvent(protectedEvent);
                check(protectedEvent.isCancelled()&&destination[0]==null,"return protection also blocks Nether travel");
                latch.remove(id);
                ArmorStand contact=world.spawn(edge,ArmorStand.class);
                var entityEvent=new EntityPortalEvent(contact,edge,world.getSpawnLocation());
                travel.entityPortal(entityEvent);
                check(entityEvent.isCancelled(),"entity Nether travel blocked at edge");
                contact.remove();
            }
            var ordinary=world.getBlockAt(60,y,40);ordinary.setType(Material.NETHER_PORTAL,false);
            position[0]=ordinary.getLocation().add(0.5,0,0.5);
            var ordinaryEvent=new PlayerPortalEvent(player,position[0],world.getSpawnLocation(),TeleportCause.NETHER_PORTAL);
            travel.portalEvent(ordinaryEvent);
            check(!ordinaryEvent.isCancelled()&&destination[0]==null,"ordinary Nether travel remains available");
            world.getBlockAt(x- (axis==Axis.X?1:0),y,z-(axis==Axis.Z?1:0)).setType(Material.AIR,false);
            visuals.tick();check(block.getType()==Material.AIR,"broken frame removes portal");
        }
        var normal=world.getBlockAt(60,y,40);normal.setType(Material.NETHER_PORTAL,false);
        check(!travel.isQuartzPortal(normal),"ordinary Nether portals remain ordinary");
        garden.getLogger().info("PASS portal travel: both axes, migration, renderer cleanup, physics, body contact, Evergarden destination, Nether cancellation, return protection, frame break, ordinary Nether portal");
    }
}
