import com.example.advancemagic.AdvanceMagicPlugin;
import com.example.advancemagic.item.ElementalStaffService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.permissions.PermissionAttachment;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Additional real-event lifecycle probes; called only by the disposable Paper harness. */
public final class FlyingStaffAdditionalChecks {
    private FlyingStaffAdditionalChecks() {}

    private static Object session(AdvanceMagicPlugin plugin,Player player) throws Exception {
        Field sessions=plugin.flyingStaff().getClass().getDeclaredField("sessions");
        sessions.setAccessible(true);
        return ((Map<?,?>)sessions.get(plugin.flyingStaff())).get(player.getUniqueId());
    }

    private static Object value(Object object,String name) throws Exception {
        Field field=object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(object);
    }

    private static ArmorStand stand(AdvanceMagicPlugin plugin,Player owner) throws Exception {
        Object active=session(plugin,owner);
        return active==null?null:(ArmorStand)value(active,"stand");
    }

    private static void close(AdvanceMagicPlugin plugin,Player owner) throws Exception {
        Method method=plugin.flyingStaff().getClass().getDeclaredMethod("closeOwner",Player.class);
        method.setAccessible(true);
        method.invoke(plugin.flyingStaff(),owner);
    }

    private static void use(Player owner) {
        Bukkit.getPluginManager().callEvent(new PlayerInteractEvent(owner,Action.RIGHT_CLICK_AIR,
            owner.getInventory().getItemInMainHand(),null,null,EquipmentSlot.HAND));
    }

    private static PlayerInteractEntityEvent mount(Player player,ArmorStand stand) {
        var event=new PlayerInteractEntityEvent(player,stand,EquipmentSlot.HAND);
        Bukkit.getPluginManager().callEvent(event);
        return event;
    }

    private static void settle(AdvanceMagicPlugin plugin) {
        for(int tick=0;tick<12;tick++)plugin.flyingStaff().tick();
    }

    public static void run(AdvanceMagicPlugin plugin,Player owner,Player other,
                           BiConsumer<Boolean,String> check,Consumer<String> observe) throws Exception {
        ItemStack[] inventory=owner.getInventory().getContents();
        ItemStack[] otherInventory=other.getInventory().getContents();
        int heldSlot=owner.getInventory().getHeldItemSlot();
        Location original=owner.getLocation();
        double originalMana=plugin.mana().account(owner).manaExact();
        Object originalEnabled=plugin.getConfig().get("flying-staff.enabled");
        PermissionAttachment denied=null;
        org.bukkit.block.Block obstruction=null;
        Material originalBlock=null;
        try {
            close(plugin,owner);
            close(plugin,other);
            plugin.getConfig().set("flying-staff.enabled",true);
            owner.getInventory().clear();
            other.getInventory().clear();
            owner.getInventory().setHeldItemSlot(0);
            owner.teleport(new Location(owner.getWorld(),.5,100,.5,0,0));
            plugin.mana().account(owner).setMana(100);
            owner.getInventory().setItemInMainHand(plugin.flyingStaff().create());
            use(owner);
            ArmorStand display=stand(plugin,owner);
            check.accept(display!=null&&display.isValid(),"additional: summon creates tracked display");
            settle(plugin);

            // Each move stays below teleport cleanup; the total distance requires a recall.
            for(double x:new double[]{3.5,6.5,9.5})
                owner.teleport(new Location(owner.getWorld(),x,100,.5,0,0));
            Location previousLocation=display.getLocation();
            int previousUses=plugin.wands().usesLeft(owner.getInventory().getItemInMainHand());
            obstruction=owner.getWorld().getBlockAt(9,100,2);
            originalBlock=obstruction.getType();
            obstruction.setType(Material.STONE);
            use(owner);
            check.accept(stand(plugin,owner)==display&&display.getLocation().distanceSquared(previousLocation)<.000001,
                "additional: blocked recall preserves original entity and location");
            check.accept(plugin.wands().usesLeft(owner.getInventory().getItemInMainHand())==previousUses,
                "additional: blocked recall does not consume durability");
            obstruction.setType(originalBlock);
            obstruction=null;
            for(int tick=0;tick<8;tick++)plugin.flyingStaff().tick();

            // Three same-tick use events reproduce controller use repetition without assumptions about timing.
            int rapidStart=plugin.wands().usesLeft(owner.getInventory().getItemInMainHand());
            use(owner);use(owner);use(owner);
            int rapidCost=rapidStart-plugin.wands().usesLeft(owner.getInventory().getItemInMainHand());
            check.accept(rapidCost<=1,"additional: repeated summon/recall use does not repeatedly consume durability");
            if("SUMMON".equals(value(session(plugin,owner),"phase").toString())){
                mount(owner,display);
                check.accept(!plugin.flyingStaff().isRiding(owner),"additional: summon animation blocks premature mount");
            }
            settle(plugin);
            if(plugin.flyingStaff().isRiding(owner))owner.leaveVehicle();

            other.getInventory().setItemInMainHand(plugin.flyingStaff().create());
            mount(other,display);
            check.accept(!plugin.flyingStaff().isRiding(other)&&display.getPassengers().isEmpty(),
                "additional: another player cannot mount owner's staff");
            var foreignHit=new EntityDamageByEntityEvent(other,display,EntityDamageEvent.DamageCause.ENTITY_ATTACK,5);
            Bukkit.getPluginManager().callEvent(foreignHit);
            check.accept(foreignHit.isCancelled()&&"IDLE".equals(value(session(plugin,owner),"phase").toString()),
                "additional: another player cannot damage or dismiss owner's staff");
            var damage=new EntityDamageEvent(display,EntityDamageEvent.DamageCause.FIRE,5);
            Bukkit.getPluginManager().callEvent(damage);
            check.accept(damage.isCancelled(),"additional: environmental damage to staff is cancelled");
            var manipulate=new PlayerArmorStandManipulateEvent(other,display,new ItemStack(Material.DIAMOND_HELMET),
                display.getEquipment().getHelmet(),EquipmentSlot.HEAD,EquipmentSlot.HAND);
            Bukkit.getPluginManager().callEvent(manipulate);
            check.accept(manipulate.isCancelled(),"additional: another player cannot replace display equipment");

            denied=owner.addAttachment(plugin,"advance-magic.flying-staff",false);
            mount(owner,display);
            check.accept(!plugin.flyingStaff().isRiding(owner),"additional: explicit denied permission blocks mounting");
            owner.removeAttachment(denied);denied=null;

            // Ownership is satisfied from inventory. Exercise real competing plugin listeners.
            ItemStack flightItem=owner.getInventory().getItemInMainHand();
            owner.getInventory().setItem(9,flightItem);
            owner.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
            mount(owner,display);
            check.accept(plugin.flyingStaff().isRiding(owner),"additional: empty-hand mount accepts staff in inventory");
            owner.leaveVehicle();
            check.accept(!plugin.flyingStaff().isRiding(owner),"additional: empty-hand rider can dismount near ground");

            owner.getInventory().setItemInMainHand(plugin.staffs().create(ElementalStaffService.Element.FIRE));
            ItemStack elementalBefore=owner.getInventory().getItemInMainHand().clone();
            double elementalManaBefore=plugin.mana().account(owner).manaExact();
            var protectedElementalClick=new PlayerInteractEntityEvent(owner,display,EquipmentSlot.HAND);
            protectedElementalClick.setCancelled(true);
            Bukkit.getPluginManager().callEvent(protectedElementalClick);
            check.accept(!plugin.flyingStaff().isRiding(owner)&&protectedElementalClick.isCancelled(),
                "additional: protected elemental-hand interaction cannot mount");
            mount(owner,display);
            check.accept(plugin.flyingStaff().isRiding(owner),"additional: holding elemental staff still mounts owned flying staff");
            check.accept(owner.getInventory().getItemInMainHand().equals(elementalBefore)
                &&plugin.mana().account(owner).manaExact()==elementalManaBefore,
                "additional: mounting with elemental staff spends no spell mana or durability");
            owner.getInventory().setItemInOffHand(elementalBefore.clone());
            var offhandClick=new PlayerInteractEntityEvent(owner,display,EquipmentSlot.OFF_HAND);
            Bukkit.getPluginManager().callEvent(offhandClick);
            check.accept(plugin.flyingStaff().isRiding(owner)&&!(boolean)value(session(plugin,owner),"manualTurbo")
                &&plugin.mana().account(owner).manaExact()==elementalManaBefore,
                "additional: duplicate offhand interaction does not cast or toggle Turbo");
            owner.getInventory().setItemInOffHand(null);
            owner.leaveVehicle();
            var ordinaryElementalClick=new PlayerInteractEntityEvent(owner,other,EquipmentSlot.HAND);
            Bukkit.getPluginManager().callEvent(ordinaryElementalClick);
            check.accept(ordinaryElementalClick.isCancelled(),
                "additional: elemental interaction with ordinary entity still uses spell listener");
            owner.getInventory().setItemInMainHand(plugin.restoration().createRepairWand());
            int repairChargesBefore=plugin.restoration().charges(owner.getInventory().getItemInMainHand());
            var protectedRepairClick=new PlayerInteractEntityEvent(owner,display,EquipmentSlot.HAND);
            protectedRepairClick.setCancelled(true);
            Bukkit.getPluginManager().callEvent(protectedRepairClick);
            check.accept(!plugin.flyingStaff().isRiding(owner)&&protectedRepairClick.isCancelled(),
                "additional: protected repair-hand interaction cannot mount");
            mount(owner,display);
            check.accept(plugin.flyingStaff().isRiding(owner),"additional: holding restoration wand still mounts owned flying staff");
            check.accept(plugin.restoration().charges(owner.getInventory().getItemInMainHand())==repairChargesBefore,
                "additional: mounting with restoration wand spends no repair charges");
            owner.leaveVehicle();
            var ordinaryRepairClick=new PlayerInteractEntityEvent(owner,other,EquipmentSlot.HAND);
            Bukkit.getPluginManager().callEvent(ordinaryRepairClick);
            check.accept(ordinaryRepairClick.isCancelled(),
                "additional: restoration interaction with ordinary entity still uses repair listener");

            owner.getInventory().setItemInMainHand(flightItem);
            owner.getInventory().setItem(9,null);
            mount(owner,display);
            check.accept(plugin.flyingStaff().isRiding(owner),"additional: held flying staff mounts after alternate held-item probes");
            for(int tick=0;tick<8;tick++)plugin.flyingStaff().tick();
            boolean turboBefore=(boolean)value(session(plugin,owner),"manualTurbo");
            use(owner);
            boolean turboFirst=(boolean)value(session(plugin,owner),"manualTurbo");
            use(owner);
            boolean turboSecond=(boolean)value(session(plugin,owner),"manualTurbo");
            check.accept(turboFirst!=turboBefore&&turboSecond==turboFirst,
                "additional: repeated same-tick use cannot toggle Turbo twice");
            check.accept(plugin.flyingStaff().isRiding(owner),"additional: repeated mounted use preserves passenger");
            owner.leaveVehicle();

            plugin.getConfig().set("flying-staff.enabled",false);
            mount(owner,display);
            check.accept(!plugin.flyingStaff().isRiding(owner),"additional: disabling feature blocks mounting existing display");
            plugin.getConfig().set("flying-staff.enabled",true);

            var ownerHit=new EntityDamageByEntityEvent(owner,display,EntityDamageEvent.DamageCause.ENTITY_ATTACK,5);
            Bukkit.getPluginManager().callEvent(ownerHit);
            check.accept(ownerHit.isCancelled()&&"DISMISS".equals(value(session(plugin,owner),"phase").toString()),
                "additional: unmounted owner's attack starts dismissal without damage");
            for(int tick=0;tick<8;tick++)plugin.flyingStaff().tick();
            check.accept(!display.isValid()&&session(plugin,owner)==null,
                "additional: dismissal removes entity and tracked session");

            use(owner);settle(plugin);
            ArmorStand worldDisplay=stand(plugin,owner);
            check.accept(worldDisplay!=null&&worldDisplay.isValid(),"additional: can summon again after dismissal");
            Bukkit.getPluginManager().callEvent(new PlayerChangedWorldEvent(owner,owner.getWorld()));
            check.accept(!worldDisplay.isValid()&&session(plugin,owner)==null,
                "additional: world-change event cleans entity and session");
        } finally {
            if(obstruction!=null&&originalBlock!=null)obstruction.setType(originalBlock);
            if(denied!=null)owner.removeAttachment(denied);
            close(plugin,owner);
            close(plugin,other);
            plugin.getConfig().set("flying-staff.enabled",originalEnabled);
            owner.getInventory().setContents(inventory);
            owner.getInventory().setHeldItemSlot(heldSlot);
            other.getInventory().setContents(otherInventory);
            plugin.mana().account(owner).setMana(originalMana);
            owner.teleport(original);
        }
    }
}
