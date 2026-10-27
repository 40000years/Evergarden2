import com.example.voidscape.item.AdventurerWeaponService.AdventurerWeapon;
import io.papermc.paper.event.entity.EntityLoadCrossbowEvent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ChargedProjectiles;
import org.bukkit.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Firework;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

/** Real Paper use packets, plugin listeners and native player use ticks, without a graphical client. */
public final class AdventurerRangedNativeProbe extends PetSystemNativeProbe implements Listener {
    record Scenario(AdventurerWeapon weapon,InteractionHand hand,int quickCharge,boolean firework) {
        String label(){return weapon.id()+" / "+hand+" / Quick Charge "+quickCharge+" / "+(firework?"rocket":"arrow");}
    }
    final List<Scenario> scenarios=new ArrayList<>();
    Scenario current;net.minecraft.world.item.ItemStack original;
    int scenarioIndex,sequence,useTicks,previousTicks,requiredTicks,interactions,loads,shots;
    int interactionsBefore,loadsBefore,shotsBefore;Entity projectile;

    @Override String suiteName(){return "Adventurer bow and crossbow native use";}
    @Override void awaitSeraph(){later(1,()->{
        Bukkit.getPluginManager().registerEvents(this,this);
        check(garden.adventurerWeapons()!=null,"Adventurer weapon service registered");
        verifyCollection();verifyGeyserDefinitions();
        for(InteractionHand hand:InteractionHand.values()){
            scenarios.add(new Scenario(AdventurerWeapon.THORNWHISPER,hand,0,false));
            for(int quick:new int[]{0,3})for(boolean rocket:new boolean[]{false,true})
                scenarios.add(new Scenario(AdventurerWeapon.RUNELOCK,hand,quick,rocket));
        }
        startNext();
    });}

    void verifyCollection(){
        for(AdventurerWeapon weapon:AdventurerWeapon.values()){
            ItemStack item=garden.adventurerWeapons().create(weapon);
            check(item.getType()==weapon.baseMaterial(),weapon.id()+": native base material retained");
            check(garden.adventurerWeapons().identify(item)==weapon,weapon.id()+": authenticated identity retained");
            var meta=item.getItemMeta();String model="voidscape:"+weapon.modelKey();
            check(meta.getItemModel()!=null&&model.equals(meta.getItemModel().toString()),weapon.id()+": explicit model key retained");
            check(meta.getCustomModelDataComponent().getStrings().equals(List.of(model)),weapon.id()+": Geyser selector matches model key");
        }
        check(AdventurerWeapon.values().length==20,"all 20 Adventurer weapons are covered");
    }

    void verifyGeyserDefinitions(){try{
        var geyser=Bukkit.getPluginManager().getPlugin("Geyser-Spigot");
        check(geyser!=null&&geyser.isEnabled(),"Geyser enables with the ranged mappings");
        var registries=Class.forName("org.geysermc.geyser.registry.Registries",true,geyser.getClass().getClassLoader());
        Object registry=registries.getField("ITEMS").get(null);
        Map<?,?> protocols=(Map<?,?>)registry.getClass().getMethod("get").invoke(registry);
        check(!protocols.isEmpty(),"Geyser has registered Bedrock protocol item tables");
        for(var protocol:protocols.entrySet()){
            Object items=protocol.getValue();var lookup=items.getClass().getMethod("getDefinition",String.class);
            Object standby=lookup.invoke(items,"voidscape:adventurer_runelock");
            check(standby!=null,"Geyser protocol "+protocol.getKey()+": Runelock standby definition exists");
            for(String charge:new String[]{"arrow","rocket"}){
                String id="voidscape:adventurer_runelock_loaded_"+charge;
                Object loaded=lookup.invoke(items,id);
                check(loaded!=null,"Geyser protocol "+protocol.getKey()+": loaded "+charge+" definition is registered");
                int runtime=(int)loaded.getClass().getMethod("getRuntimeId").invoke(loaded);
                int baseRuntime=(int)standby.getClass().getMethod("getRuntimeId").invoke(standby);
                check(runtime!=baseRuntime,"Geyser protocol "+protocol.getKey()+": loaded "+charge+" has a distinct Bedrock runtime ID");
                Map<?,?> root=(Map<?,?>)loaded.getClass().getMethod("getComponentData").invoke(loaded);
                Map<?,?> components=(Map<?,?>)root.get("components");
                Map<?,?> properties=(Map<?,?>)components.get("item_properties");
                Map<?,?> icon=(Map<?,?>)properties.get("minecraft:icon");
                Map<?,?> textures=(Map<?,?>)icon.get("textures");
                check(("voidscape.adventurer_runelock_loaded_"+charge).equals(textures.get("default")),
                    "Geyser protocol "+protocol.getKey()+": loaded "+charge+" UI icon is in the actual client definition");
                check(components.containsKey("minecraft:shooter"),"Geyser protocol "+protocol.getKey()+": loaded "+charge+" retains native shooter behavior");
            }
        }
    }catch(ReflectiveOperationException error){throw new RuntimeException(error);}}

    @EventHandler(priority=EventPriority.MONITOR)
    public void interact(PlayerInteractEvent event){
        if(!event.getPlayer().getUniqueId().equals(tamer.getUniqueId())||current==null)return;
        if(garden.adventurerWeapons().identify(event.getItem())!=current.weapon())return;
        interactions++;
        check(event.useItemInHand()!=Event.Result.DENY,current.label()+": real right-click packet reaches all listeners without item-use denial");
    }
    @EventHandler(priority=EventPriority.MONITOR)
    public void loaded(EntityLoadCrossbowEvent event){
        if(!event.getEntity().getUniqueId().equals(tamer.getUniqueId())||current==null)return;
        loads++;
        check(!event.isCancelled(),current.label()+": native crossbow loading event is accepted");
    }
    @EventHandler(priority=EventPriority.MONITOR)
    public void shot(EntityShootBowEvent event){
        if(!event.getEntity().getUniqueId().equals(tamer.getUniqueId())||current==null)return;
        shots++;projectile=event.getProjectile();
        check(!event.isCancelled(),current.label()+": native shoot event is accepted");
        check(garden.adventurerWeapons().identify(event.getBow())==current.weapon(),current.label()+": shoot event retains weapon identity");
        String tagged=projectile.getPersistentDataContainer().get(garden.key("adventurer_projectile"),PersistentDataType.STRING);
        check(current.weapon().id().equals(tagged),current.label()+": server projectile carries the weapon damage identity");
    }

    void usePacket(){
        actorA.connection.handleUseItem(new ServerboundUseItemPacket(current.hand(),++sequence,0,0));
    }
    void startNext(){
        if(scenarioIndex==scenarios.size()){
            current=null;check(shots==10,"all 10 hand, ammunition and Quick Charge cases really shoot");
            check(loads==8,"all 8 Runelock cases really load through Paper events");finish(null);return;
        }
        current=scenarios.get(scenarioIndex++);actorA.stopUsingItem();tamer.getInventory().clear();
        actorA.setPos(home.getX(),home.getY(),home.getZ());actorA.setYRot(0);actorA.setXRot(0);
        ItemStack weapon=garden.adventurerWeapons().create(current.weapon());
        if(current.quickCharge()>0)weapon.addUnsafeEnchantment(Enchantment.QUICK_CHARGE,current.quickCharge());
        var inventory=tamer.getInventory();inventory.setHeldItemSlot(0);
        if(current.hand()==InteractionHand.MAIN_HAND)inventory.setItemInMainHand(weapon);else inventory.setItemInOffHand(weapon);
        ItemStack ammo=new ItemStack(current.firework()?Material.FIREWORK_ROCKET:Material.ARROW,64);
        // Vanilla accepts fireworks from the other hand, whereas ordinary arrows can be in the inventory.
        if(current.firework()){
            if(current.hand()==InteractionHand.MAIN_HAND)inventory.setItemInOffHand(ammo);else inventory.setItemInMainHand(ammo);
        }else inventory.setItem(9,ammo);
        original=actorA.getItemInHand(current.hand());
        check(original.getUseAnimation()==(current.weapon()==AdventurerWeapon.THORNWHISPER?ItemUseAnimation.BOW:ItemUseAnimation.CROSSBOW),
            current.label()+": genuine native use animation is present");
        check(original.getUseDuration(actorA)==72000,current.label()+": use duration survives custom metadata");
        int charge=current.weapon()==AdventurerWeapon.RUNELOCK?CrossbowItem.getChargeDuration(original,actorA):20;
        if(current.weapon()==AdventurerWeapon.RUNELOCK)
            check(charge==(current.quickCharge()==3?10:25),current.label()+": native Quick Charge duration is correct, ticks="+charge);
        requiredTicks=charge+3;useTicks=0;previousTicks=-1;
        interactionsBefore=interactions;loadsBefore=loads;shotsBefore=shots;projectile=null;
        usePacket();
        check(interactions==interactionsBefore+1,current.label()+": a real packet fires PlayerInteractEvent");
        check(actorA.isUsingItem()&&actorA.getUsedItemHand()==current.hand(),current.label()+": native use starts in the requested hand");
        check(actorA.getUseItem()==actorA.getItemInHand(current.hand()),current.label()+": using_item reference matches the actual held stack");
        later(1,this::sampleUse);
    }

    void sampleUse(){
        // These embedded clients are not registered with the socket listener. Drive its normal
        // ServerPlayer.doTick once per real server tick so native Player/LivingEntity use logic runs,
        // while all actual plugin scheduled tasks run between samples.
        actorA.doTick();useTicks++;
        int elapsed=actorA.getTicksUsingItem();
        check(actorA.isUsingItem(),current.label()+": use remains continuous at real server tick "+useTicks);
        check(actorA.getUseItem()==actorA.getItemInHand(current.hand())&&actorA.getUseItem()==original,
            current.label()+": active and held stack references remain identical at tick "+useTicks);
        check(elapsed>previousTicks,current.label()+": native draw time advances without resets, ticks="+elapsed);
        previousTicks=elapsed;
        ItemStack held=current.hand()==InteractionHand.MAIN_HAND?tamer.getInventory().getItemInMainHand():tamer.getInventory().getItemInOffHand();
        check(garden.adventurerWeapons().identify(held)==current.weapon(),current.label()+": metadata remains intact while drawing at tick "+useTicks);
        if(useTicks<requiredTicks){later(1,this::sampleUse);return;}
        if(current.weapon()==AdventurerWeapon.RUNELOCK){
            check(loads==loadsBefore+1,current.label()+": full charge emits exactly one load event");
            check(CrossbowItem.isCharged(original),current.label()+": charged component is set after holding use");
            ChargedProjectiles charge=original.getOrDefault(DataComponents.CHARGED_PROJECTILES,ChargedProjectiles.EMPTY);
            check(!charge.isEmpty()&&charge.contains(current.firework()?Items.FIREWORK_ROCKET:Items.ARROW),
                current.label()+": charged ammunition selects the proper arrow or rocket state");
        }
        actorA.releaseUsingItem();
        check(!actorA.isUsingItem(),current.label()+": releasing input ends the native draw");
        if(current.weapon()==AdventurerWeapon.RUNELOCK){
            check(CrossbowItem.isCharged(actorA.getItemInHand(current.hand())),current.label()+": loaded state persists after releasing use");
            later(1,()->{usePacket();verifyShot();});
        }else verifyShot();
    }
    void verifyShot(){
        check(shots==shotsBefore+1&&projectile!=null,current.label()+": exactly one projectile is fired");
        check(current.firework()?projectile instanceof Firework:projectile instanceof Arrow,current.label()+": native projectile type is correct");
        if(current.weapon()==AdventurerWeapon.RUNELOCK)
            check(!CrossbowItem.isCharged(actorA.getItemInHand(current.hand())),current.label()+": shooting clears the loaded state");
        projectile.remove();later(2,this::startNext);
    }
}
