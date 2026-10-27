package com.example.advancemagic.spell;

import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.util.*;

/** The real Ancient Judge hand, with client-specific carriers and finite scene ownership. */
final class LegendaryEarthHand implements AutoCloseable {
    private static final Map<UUID,Integer> ACTIVE=new HashMap<>();
    private final MagicContext context;
    private final List<ItemDisplay> java=new ArrayList<>();
    private final List<ArmorStand> bedrock=new ArrayList<>();
    private final World world;
    private boolean closed;
    static LegendaryEarthHand create(MagicContext context,Location root){
        if(ACTIVE.getOrDefault(root.getWorld().getUID(),0)>=4)return null;
        return new LegendaryEarthHand(context,root);
    }
    private LegendaryEarthHand(MagicContext context,Location root){
        this.context=context;world=root.getWorld();
        ACTIVE.merge(world.getUID(),1,Integer::sum);
        try{
            for(String model:List.of("judge_left","judge_left_light")){
                ItemStack item=new ItemStack(Material.IRON_HELMET);
                var meta=item.getItemMeta();meta.setItemModel(new NamespacedKey("voidscape",model));item.setItemMeta(meta);
                ItemDisplay display=world.spawn(root,ItemDisplay.class,e->{
                    prepare(e);e.setItemStack(item);e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
                    e.setInterpolationDuration(4);e.setTeleportDuration(4);e.setViewRange(1.5f);
                    e.setDisplayWidth(20);e.setDisplayHeight(20);e.setShadowRadius(0);
                    e.setBrightness(new Display.Brightness(15,15));
                    e.setTransformation(new Transformation(new Vector3f(0,2.184f,0),new Quaternionf(),new Vector3f(3.36f),new Quaternionf()));
                });
                java.add(display);
                ArmorStand carrier=world.spawn(root,ArmorStand.class,e->{
                    prepare(e);e.setVisible(false);e.setMarker(false);e.setSilent(true);e.setBasePlate(false);
                    e.setArms(false);e.setCollidable(false);e.getEquipment().setHelmet(item);
                    var scale=e.getAttribute(Attribute.SCALE);if(scale!=null)scale.setBaseValue(.42);
                    for(EquipmentSlot slot:List.of(EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET,EquipmentSlot.HAND,EquipmentSlot.OFF_HAND))e.addEquipmentLock(slot,ArmorStand.LockType.ADDING_OR_CHANGING);
                });
                bedrock.add(carrier);
            }
        }catch(RuntimeException failure){close();throw failure;}
    }
    private static void prepare(Entity e){e.setPersistent(false);e.setGravity(false);e.setInvulnerable(true);e.setVisibleByDefault(false);}
    void pose(Location root,float yaw){
        if(closed)return;
        Location pose=root.clone();pose.setYaw(yaw);pose.setPitch(0);
        for(Entity e:java)e.teleport(pose);
        // Existing Judge attachables are centred on the stand's head anchor.
        // Scale both the original 5.2-block centre and its 1.5-block carrier offset.
        Location carrierPose=pose.clone().add(0,1.554,0);
        for(Entity e:bedrock)e.teleport(carrierPose);
        for(Player player:world.getPlayers()){
            boolean near=player.isOnline()&&player.getLocation().distanceSquared(root)<=96*96;
            boolean nativeClient=context.plugin.packs().isBedrock(player);
            boolean javaVisible=near&&!nativeClient&&context.plugin.packs().hasApplied(player);
            for(Entity e:java)visibility(player,e,javaVisible);
            for(Entity e:bedrock)visibility(player,e,near&&nativeClient);
        }
    }
    private void visibility(Player player,Entity e,boolean show){
        if(show&&!player.canSee(e))player.showEntity(context.plugin,e);
        else if(!show&&player.canSee(e))player.hideEntity(context.plugin,e);
    }
    @Override public void close(){if(closed)return;closed=true;java.forEach(Entity::remove);bedrock.forEach(Entity::remove);java.clear();bedrock.clear();ACTIVE.computeIfPresent(world.getUID(),(id,count)->count<=1?null:count-1);}
}
