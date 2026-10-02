package com.example.advancemagic.spell;

import com.example.advancemagic.effect.EffectEngine;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Texture-backed lines with arbitrary world orientation, owned by one cast. */
final class MythicLineVisuals implements AutoCloseable {
    static final Vector X=new Vector(1,0,0),Y=new Vector(0,1,0),Z=new Vector(0,0,1);
    private final ItemDisplay display;
    private final String model;
    MythicLineVisuals(EffectEngine.Effect effect,Location at,String model) {
        this.model=model;
        ItemStack item=new ItemStack(Material.PAPER);
        var meta=item.getItemMeta();meta.setItemModel(new NamespacedKey("advance_magic",model));item.setItemMeta(meta);
        display=effect.track(at.getWorld().spawn(pose(at),ItemDisplay.class,e->{
            e.setVisibleByDefault(false);e.setItemStack(item);e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            e.setGravity(false);e.setInvulnerable(true);e.setSilent(true);e.setBrightness(new Display.Brightness(15,15));
            e.setShadowRadius(0);e.setShadowStrength(0);e.setViewRange(6);
            e.setInterpolationDuration(4);e.setTeleportDuration(4);
            e.setTransformation(new Transformation(new Vector3f(),new Quaternionf(),new Vector3f(0),new Quaternionf()));
        }));
    }
    private static Location pose(Location at){Location pose=at.clone();pose.setYaw(0);pose.setPitch(0);return pose;}
    private void transform(MythicVisuals visuals,Location at,Quaternionf rotation,float x,float y,float z,int ticks) {
        if(!display.isValid())return;
        display.setTeleportDuration(ticks);display.setInterpolationDuration(ticks);display.setInterpolationDelay(0);
        if(display.getLocation().distanceSquared(at)>1e-8)display.teleport(pose(at));
        display.setDisplayWidth(Math.max(x,Math.max(y,z))+2);display.setDisplayHeight(Math.max(x,Math.max(y,z))+2);
        display.setTransformation(new Transformation(new Vector3f(),rotation,new Vector3f(x,y,z),new Quaternionf()));
        visuals.showSeal(display);
    }
    void plane(MythicVisuals visuals,Location at,Vector right,Vector up,double diameter,double angle) {
        if(!display.isValid())return;
        Vector normal=right.clone().crossProduct(up).normalize();
        Matrix3f basis=new Matrix3f((float)right.getX(),(float)right.getY(),(float)right.getZ(),
            (float)up.getX(),(float)up.getY(),(float)up.getZ(),
            (float)normal.getX(),(float)normal.getY(),(float)normal.getZ());
        Quaternionf rotation=new Quaternionf().setFromNormalized(basis).rotateZ((float)angle);
        transform(visuals,at,rotation,(float)diameter,(float)diameter,1,4);
        Vector rotatedRight=right.clone().multiply(Math.cos(angle)).add(up.clone().multiply(Math.sin(angle)));
        Vector rotatedUp=up.clone().multiply(Math.cos(angle)).subtract(right.clone().multiply(Math.sin(angle)));
        visuals.linePlane(model,at,diameter,diameter,rotatedRight,rotatedUp);
    }
    void ray(MythicVisuals visuals,Location from,Location to,double width) {
        if(!display.isValid())return;
        Vector delta=to.toVector().subtract(from.toVector());double length=delta.length();
        if(length<.01)return;
        Vector direction=delta.clone().multiply(1/length);
        Location mid=from.clone().add(delta.multiply(.5));
        Quaternionf rotation=new Quaternionf().rotationTo(new Vector3f(0,1,0),
            new Vector3f((float)direction.getX(),(float)direction.getY(),(float)direction.getZ()));
        // A pulsed beam must appear at its final orientation immediately. Slerping
        // from the hidden pose sweeps a full-length ray sideways through the world.
        // Teleport interpolation would also move its endpoints off the cast target.
        transform(visuals,mid,rotation,(float)width,(float)length,(float)width,0);
        visuals.lineRay(model,mid,width,length,direction);
    }
    void hide() {
        if(display.isValid()) {
            display.setInterpolationDuration(0);display.setInterpolationDelay(0);
            var hidden=display.getTransformation();hidden.getScale().zero();
            display.setTransformation(hidden);
        }
    }
    @Override public void close(){if(display.isValid())display.remove();}
}
