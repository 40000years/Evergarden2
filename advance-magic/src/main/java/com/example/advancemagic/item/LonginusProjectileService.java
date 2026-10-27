package com.example.advancemagic.item;

import com.example.advancemagic.AdvanceMagicPlugin;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.util.*;

/** The vanilla trident owns physics, damage, enchantments, loyalty and pickup. */
public final class LonginusProjectileService implements Listener,AutoCloseable {
    private static final class Flight {
        final Trident trident;
        final ItemDisplay display;
        final ArmorStand carrier;
        final Location launchOrigin;
        final Map<UUID,Integer> viewers=new HashMap<>();
        org.bukkit.util.Vector direction;
        boolean impacted;
        boolean bedrockStarted;
        Flight(Trident trident,ItemDisplay display,ArmorStand carrier,org.bukkit.util.Vector direction){
            this.trident=trident;this.display=display;this.carrier=carrier;this.direction=direction;
            impacted=trident.isInBlock()||trident.hasDealtDamage();
            launchOrigin=trident.getLocation();bedrockStarted=impacted;
        }
    }
    private final AdvanceMagicPlugin plugin;
    private final LegendaryWeaponService weapons;
    private final LonginusBedrockBridge bedrock;
    private final Map<UUID,Flight> flights=new HashMap<>();
    public LonginusProjectileService(AdvanceMagicPlugin plugin,LegendaryWeaponService weapons){
        this.plugin=plugin;this.weapons=weapons;bedrock=new LonginusBedrockBridge(plugin);
        for(World world:Bukkit.getWorlds())for(Trident trident:world.getEntitiesByClass(Trident.class))track(trident);
    }
    public boolean bedrockReady(){return bedrock.ready();}
    public int size(){return flights.size();}
    public ArmorStand bedrockCarrier(UUID trident){var flight=flights.get(trident);return flight==null?null:flight.carrier;}
    public boolean bedrockStarted(UUID trident){var flight=flights.get(trident);return flight!=null&&flight.bedrockStarted;}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void launch(ProjectileLaunchEvent event){if(event.getEntity() instanceof Trident trident)track(trident);}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void hit(ProjectileHitEvent event){
        var flight=flights.get(event.getEntity().getUniqueId());if(flight==null)return;
        // The hit event precedes the small reversed post-impact velocity.
        if(!flight.impacted)flight.direction=direction(flight.trident);
        flight.impacted=true;
        if(flight.carrier!=null)bedrock.frame(flight.carrier.getUniqueId(),flight.direction);
    }
    @EventHandler public void loaded(EntitiesLoadEvent event){for(var entity:event.getEntities())if(entity instanceof Trident t)track(t);}
    public void track(Trident trident){
        if(weapons.identify(trident.getItemStack())!=LegendaryWeaponService.Weapon.LONGINUS||flights.containsKey(trident.getUniqueId()))return;
        var direction=direction(trident);
        trident.setVisibleByDefault(false);
        ItemStack image=new ItemStack(Material.PAPER);
        var meta=image.getItemMeta();meta.setItemModel(new NamespacedKey("advance_magic","longinus_projectile"));image.setItemMeta(meta);
        var at=trident.getLocation();at.setYaw(0);at.setPitch(0);
        ItemDisplay display=trident.getWorld().spawn(at,ItemDisplay.class,d->{
            d.setVisibleByDefault(false);d.setPersistent(false);d.setInvulnerable(true);d.setGravity(false);
            d.setItemStack(image);d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            d.setBrightness(new Display.Brightness(15,15));d.setTeleportDuration(1);d.setViewRange(2);
            d.setTransformation(transform(direction));
        });
        // A marker carrier has no client projectile prediction. Its position
        // therefore stays at the actual impact point when the trident stops.
        ArmorStand carrier=bedrock.available()?trident.getWorld().spawn(at,ArmorStand.class,d->{
            d.setVisibleByDefault(false);d.setPersistent(false);d.setInvulnerable(true);d.setGravity(false);
            d.setMarker(true);d.setVisible(true);d.setBasePlate(false);d.setSilent(true);
            bedrock.add(d.getUniqueId(),direction);
        }):null;
        var flight=new Flight(trident,display,carrier,direction);flights.put(trident.getUniqueId(),flight);viewers(flight);
    }
    private org.bukkit.util.Vector direction(Trident trident){
        var direction=trident.getVelocity();
        if(trident.isInBlock()||trident.hasDealtDamage()||direction.lengthSquared()<.00001){
            double yaw=Math.toRadians(trident.getYaw()),pitch=Math.toRadians(trident.getPitch());
            direction=new org.bukkit.util.Vector(Math.sin(yaw)*Math.cos(pitch),Math.sin(pitch),Math.cos(yaw)*Math.cos(pitch));
        }
        return direction.normalize();
    }
    private Transformation transform(org.bukkit.util.Vector direction){
        // Item geometry is centered on y=16. Its tip is y=31.5. Anchor the
        // tip 0.05 blocks ahead of the real collision point, not one block inside it.
        float back=(31.5f-16)/16-.05f;
        return new Transformation(new Vector3f((float)-direction.getX()*back,(float)-direction.getY()*back,(float)-direction.getZ()*back),
            new Quaternionf().rotationTo(new Vector3f(0,1,0),
                new Vector3f((float)direction.getX(),(float)direction.getY(),(float)direction.getZ())),new Vector3f(1),new Quaternionf());
    }
    private void viewers(Flight flight){
        for(Player player:Bukkit.getOnlinePlayers()){
            boolean isBedrock=plugin.packs().isBedrock(player);
            int view=isBedrock?(flight.carrier!=null&&bedrock.ready()?(flight.bedrockStarted?2:3):0):
                (plugin.getConfig().getBoolean("resource-pack.enabled",true)&&!plugin.packs().hasApplied(player)?0:1);
            Integer old=flight.viewers.put(player.getUniqueId(),view);
            if(old!=null&&old==view)continue;
            if(view==0)player.showEntity(plugin,flight.trident);else player.hideEntity(plugin,flight.trident);
            if(view==1)player.showEntity(plugin,flight.display);else player.hideEntity(plugin,flight.display);
            if(flight.carrier!=null){
                if(view==2)player.showEntity(plugin,flight.carrier);else player.hideEntity(plugin,flight.carrier);
            }
        }
        flight.viewers.keySet().removeIf(id->Bukkit.getPlayer(id)==null);
    }
    public void tick(){
        var iterator=flights.values().iterator();
        while(iterator.hasNext()){
            var flight=iterator.next();
            if(!flight.trident.isValid()||!flight.display.isValid()){
                release(flight);iterator.remove();continue;
            }
            var at=flight.trident.getLocation();at.setYaw(0);at.setPitch(0);
            if(!flight.impacted&&!flight.trident.isInBlock()&&!flight.trident.hasDealtDamage()&&flight.trident.getVelocity().lengthSquared()>.00001)flight.direction=direction(flight.trident);
            if(flight.trident.isInBlock()||flight.trident.hasDealtDamage())flight.impacted=true;
            // The native projectile is born near the shooter's eye before its
            // first physics step. Don't reveal a full-length Bedrock shaft there.
            // Impacted/reloaded spears are allowed to appear immediately.
            if(!flight.bedrockStarted&&(flight.impacted||at.getWorld()!=flight.launchOrigin.getWorld()||
                    at.distanceSquared(flight.launchOrigin)>.0001))flight.bedrockStarted=true;
            flight.display.teleport(at);flight.display.setTransformation(transform(flight.direction));
            if(flight.carrier!=null){
                if(!flight.carrier.getLocation().equals(at))flight.carrier.teleport(at);
                bedrock.frame(flight.carrier.getUniqueId(),flight.direction);
            }
            viewers(flight);
        }
    }
    private void release(Flight flight){
        flight.display.remove();
        if(flight.carrier!=null){bedrock.remove(flight.carrier.getUniqueId());flight.carrier.remove();}
        if(flight.trident.isValid()){
            flight.trident.setVisibleByDefault(true);
            for(Player player:Bukkit.getOnlinePlayers())player.showEntity(plugin,flight.trident);
        }
    }
    @Override public void close(){for(var flight:flights.values())release(flight);flights.clear();bedrock.close();}
}
