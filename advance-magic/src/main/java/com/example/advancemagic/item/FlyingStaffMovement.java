package com.example.advancemagic.item;

import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;

import java.lang.reflect.Method;

/** Moves an already collision-checked mount within its world through Paper's entity tracker. */
final class FlyingStaffMovement {
    private final Method getHandle;
    private final Method setPos;

    FlyingStaffMovement() {
        try {
            Class<?> craftEntity=Class.forName("org.bukkit.craftbukkit.entity.CraftEntity");
            getHandle=craftEntity.getMethod("getHandle");
            setPos=getHandle.getReturnType().getMethod("setPos",double.class,double.class,double.class);
        } catch(ReflectiveOperationException error) {
            throw new IllegalStateException("Flying staff movement requires Paper's mapped Entity.setPos API",error);
        }
    }

    void move(ArmorStand stand,Location destination) {
        if(stand.getWorld()!=destination.getWorld())throw new IllegalArgumentException("Staff movement cannot change worlds");
        destination.checkFinite();
        try {
            // Bukkit teleport(RETAIN_PASSENGERS) also teleports the rider and fires
            // PlayerTeleportEvent every tick. Arrival listeners then refresh the
            // surrounding terrain. Ordinary movement must use the entity tracker;
            // the normal passenger tick keeps the rider attached to the mount.
            setPos.invoke(getHandle.invoke(stand),destination.getX(),destination.getY(),destination.getZ());
            stand.setRotation(destination.getYaw(),destination.getPitch());
        } catch(ReflectiveOperationException error) {
            throw new IllegalStateException("Cannot move flying staff",error);
        }
    }
}
