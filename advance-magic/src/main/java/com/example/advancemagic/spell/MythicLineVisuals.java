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
    private final MythicVisuals.BlockProp block;
    private final String model;
    private boolean firstFrame=true;
    MythicLineVisuals(EffectEngine.Effect effect,Location at,String model) {
        this(at,model);effect.track(display);display.setViewRange(6);
    }
    /** Independently owned cosmetic scene; never consumes a combat EffectEngine slot. */
    MythicLineVisuals(Location at,String model) {
        this.model=model;
        // "block:<material>" is a vanilla block item drawn as a solid 3D chunk.
        Material block=model.startsWith("block:")?Material.matchMaterial(model.substring(6)):null;
        ItemStack item=new ItemStack(block!=null&&block.isItem()?block:Material.PAPER);
        if(block==null){var meta=item.getItemMeta();meta.setItemModel(new NamespacedKey("advance_magic",model));item.setItemMeta(meta);}
        display=at.getWorld().spawn(pose(at),ItemDisplay.class,e->{
            e.setVisibleByDefault(false);e.setItemStack(item);e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            e.setGravity(false);e.setInvulnerable(true);e.setSilent(true);e.setBrightness(new Display.Brightness(15,15));
            e.setShadowRadius(0);e.setShadowStrength(0);
            // The upper Legendary Light seal is 32 blocks above a floor anchor.
            // Keep it visible to every viewer selected around that anchor.
            e.setViewRange(model.startsWith("elemental_legendary_")||model.startsWith("block:")?1.5f:.75f);e.setPersistent(false);
            e.setInterpolationDuration(4);e.setTeleportDuration(4);
            e.setTransformation(new Transformation(new Vector3f(),new Quaternionf(),new Vector3f(0),new Quaternionf()));
        });
        this.block=block==null||!block.isBlock()?null:new MythicVisuals.BlockProp(block);
    }
    private static Location pose(Location at){Location pose=at.clone();pose.setYaw(0);pose.setPitch(0);return pose;}
    private void transform(MythicVisuals visuals,Location at,Quaternionf rotation,float x,float y,float z,int ticks) {
        if(!display.isValid())return;
        if(firstFrame){ticks=0;firstFrame=false;}
        display.setTeleportDuration(ticks);display.setInterpolationDuration(ticks);display.setInterpolationDelay(0);
        if(display.getLocation().distanceSquared(at)>1e-8)display.teleport(pose(at));
        display.setDisplayWidth(Math.max(x,Math.max(y,z))+2);display.setDisplayHeight(Math.max(x,Math.max(y,z))+2);
        display.setTransformation(new Transformation(new Vector3f(),rotation,new Vector3f(x,y,z),new Quaternionf()));
        visuals.showSeal(display);
    }
    void plane(MythicVisuals visuals,Location at,Vector right,Vector up,double diameter,double angle) {
        plane(visuals,at,right,up,diameter,diameter,angle);
    }
    /** Preserve an authored hero's proportions while sharing the same native Bedrock plane. */
    void plane(MythicVisuals visuals,Location at,Vector right,Vector up,double width,double height,double angle) {
        plane(visuals,at,right,up,width,height,angle,4);
    }
    void plane(MythicVisuals visuals,Location at,Vector right,Vector up,double width,double height,double angle,int ticks) {
        if(!display.isValid())return;
        planeTransform(visuals,at,right,up,width,height,angle,ticks);
        Vector rotatedRight=right.clone().multiply(Math.cos(angle)).add(up.clone().multiply(Math.sin(angle)));
        Vector rotatedUp=up.clone().multiply(Math.cos(angle)).subtract(right.clone().multiply(Math.sin(angle)));
        visuals.linePlane(model,at,width,height,rotatedRight,rotatedUp);
    }
    /** Continuous authored rotation, including native client spin between Bedrock redraws. */
    void planeSpin(MythicVisuals visuals,Location at,Vector right,Vector up,double diameter,double angle,double radiansPerTick) {
        planeSpin(visuals,at,right,up,diameter,diameter,angle,radiansPerTick);
    }
    void planeSpin(MythicVisuals visuals,Location at,Vector right,Vector up,double width,double height,double angle,double radiansPerTick) {
        if(!display.isValid())return;
        // Scenes redraw every four server ticks. Finish the Java interpolation
        // after three ticks so a new target cannot continually interrupt it.
        // Look ahead by that same interval to keep its phase near client spin.
        double targetAngle=angle+(firstFrame?0:radiansPerTick*3);
        planeTransform(visuals,at,right,up,width,height,targetAngle,3);
        Vector rotatedRight=right.clone().multiply(Math.cos(angle)).add(up.clone().multiply(Math.sin(angle)));
        Vector rotatedUp=up.clone().multiply(Math.cos(angle)).subtract(right.clone().multiply(Math.sin(angle)));
        visuals.linePlaneSpin(model,at,width,height,rotatedRight,rotatedUp,radiansPerTick);
    }
    private void planeTransform(MythicVisuals visuals,Location at,Vector right,Vector up,double width,double height,double angle,int ticks) {
        Vector normal=right.clone().crossProduct(up).normalize();
        Matrix3f basis=new Matrix3f((float)right.getX(),(float)right.getY(),(float)right.getZ(),
            (float)up.getX(),(float)up.getY(),(float)up.getZ(),
            (float)normal.getX(),(float)normal.getY(),(float)normal.getZ());
        Quaternionf rotation=new Quaternionf().setFromNormalized(basis).rotateZ((float)angle);
        transform(visuals,at,rotation,(float)width,(float)height,1,ticks);
    }
    void ray(MythicVisuals visuals,Location from,Location to,double width) {ray(visuals,from,to,width,0);}
    /** {@code ticks} above zero interpolates a sweeping beam; zero snaps a pulsed one into place. */
    void ray(MythicVisuals visuals,Location from,Location to,double width,int ticks) {
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
        transform(visuals,mid,rotation,(float)width,(float)length,(float)width,ticks);
        visuals.lineRay(model,mid,width,length,direction);
    }
    /**
     * A solid block chunk of edge {@code size} centred at {@code at}: the Java display takes the full
     * rotation. The Bedrock cube rig receives the same quaternion and interpolation duration.
     */
    void prop(MythicVisuals visuals,Location at,Quaternionf rotation,double size,float yaw,int ticks) {
        if(!display.isValid())return;
        int duration=firstFrame?0:ticks;
        transform(visuals,at,rotation,(float)size,(float)size,(float)size,ticks);
        if(block!=null)visuals.block(block,at,size,new Quaternionf(rotation),duration);
    }
    void hide() {
        if(block!=null)block.clear();
        if(display.isValid()) {
            display.setInterpolationDuration(0);display.setInterpolationDelay(0);
            var hidden=display.getTransformation();hidden.getScale().zero();
            display.setTransformation(hidden);
        }
    }
    /** Judgment's authored seal is already XZ geometry and uses its dedicated Bedrock variables. */
    void horizontalJudgment(MythicVisuals visuals,Location at,double diameter,double angle,int variant) {
        if(!display.isValid())return;
        transform(visuals,at,new Quaternionf().rotationY((float)angle),(float)diameter,1,(float)diameter,2);
        visuals.judgmentSeal(at,diameter,Math.toDegrees(angle),variant);
    }
    void verticalJudgment(MythicVisuals visuals,Location base,double height,double width) {
        if(!display.isValid())return;
        transform(visuals,base.clone().add(0,height/2,0),new Quaternionf(),(float)width,(float)height,(float)width,0);
        visuals.judgmentBeam(base,height,width);
    }
    @Override public void close(){if(display.isValid())display.remove();if(block!=null)block.clear();}
}
