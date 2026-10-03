package com.example.voidscape.boss;

import com.example.voidscape.VoidscapePlugin;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.util.*;

/** Full-bright, double-sided spell planes; each key owns exactly one reusable display. */
final class JudgeSigils implements AutoCloseable {
    static final Color RED=Color.fromRGB(255,36,62),GREEN=Color.fromRGB(66,255,155);
    private final VoidscapePlugin plugin;
    private final JudgeAether aether;
    private final Map<String,ItemDisplay> planes=new HashMap<>();
    JudgeSigils(VoidscapePlugin plugin,JudgeAether aether){this.plugin=plugin;this.aether=aether;}
    private static Location pose(Location at){Location p=at.clone();p.setYaw(0);p.setPitch(0);return p;}
    void plane(String key,String model,Location at,Vector right,Vector up,double width,double height,double angle){
        ItemDisplay display=planes.computeIfAbsent(key,k->at.getWorld().spawn(pose(at),ItemDisplay.class,e->{
            ItemStack item=new ItemStack(Material.PAPER);var meta=item.getItemMeta();
            meta.setItemModel(new NamespacedKey("voidscape",model));item.setItemMeta(meta);
            e.setItemStack(item);e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            e.setPersistent(false);e.setGravity(false);e.setInvulnerable(true);e.setVisibleByDefault(false);
            e.setBrightness(new Display.Brightness(15,15));e.setShadowRadius(0);e.setViewRange(4);
            e.setTeleportDuration(0);e.setInterpolationDuration(4);
            e.getPersistentDataContainer().set(plugin.key("world_boss_entity"),PersistentDataType.BYTE,(byte)1);
            e.setTransformation(new Transformation(new Vector3f(),new Quaternionf(),new Vector3f(0),new Quaternionf()));
        }));
        if(!display.getItemStack().getItemMeta().getItemModel().getKey().equals(model)){
            ItemStack item=display.getItemStack();var meta=item.getItemMeta();meta.setItemModel(new NamespacedKey("voidscape",model));
            item.setItemMeta(meta);display.setItemStack(item);
        }
        Vector normal=right.clone().crossProduct(up).normalize();
        Matrix3f basis=new Matrix3f((float)right.getX(),(float)right.getY(),(float)right.getZ(),
            (float)up.getX(),(float)up.getY(),(float)up.getZ(),(float)normal.getX(),(float)normal.getY(),(float)normal.getZ());
        Quaternionf rotation=new Quaternionf().setFromNormalized(basis).rotateZ((float)angle);
        Vector rx=right.clone().multiply(Math.cos(angle)).add(up.clone().multiply(Math.sin(angle)));
        Vector ry=up.clone().multiply(Math.cos(angle)).subtract(right.clone().multiply(Math.sin(angle)));
        double lower=Math.abs(rx.getY())*width*.5+Math.abs(ry.getY())*height*.5+.1;
        Location root=at.clone().subtract(0,lower,0);
        display.setDisplayWidth((float)Math.max(width,height)+2);display.setDisplayHeight((float)Math.max(width,height)+2);
        if(display.getLocation().distanceSquared(root)>1e-8)display.teleport(pose(root));
        display.setInterpolationDelay(0);
        display.setTransformation(new Transformation(new Vector3f(0,(float)lower,0),rotation,new Vector3f((float)width,(float)height,1),new Quaternionf()));
        aether.showPlane(display);
        aether.plane(model,at,width,height,rx,ry);
    }
    void circle(String key,String model,Location at,double radius,double turn,boolean upright){
        Vector right=new Vector(1,0,0),up=upright?new Vector(0,1,0):new Vector(0,0,1);
        double diameter=radius*2*512/480;
        plane(key,model,at,right,up,diameter,diameter,turn);
        if(!aether.needsFallback())return;
        Color color=model.equals("judge_sanctuary")?GREEN:RED;
        // Only clients lacking the textures receive dust. Outer radius matches the damage boundary.
        int samples=(int)Math.clamp(radius*5,32,112);
        for(int i=0;i<samples;i++){
            double a=i*Math.PI*2/samples;
            aether.fallbackDust(at.clone().add(right.clone().multiply(Math.cos(a)*radius)).add(up.clone().multiply(Math.sin(a)*radius)),color,1.8f);
        }
    }
    void lane(String key,Location at,boolean acrossX,double angle,double halfWidth){
        double a=angle+(acrossX?0:Math.PI/2);
        Vector right=new Vector(Math.cos(a),0,Math.sin(a)),up=new Vector(-Math.sin(a),0,Math.cos(a));
        plane(key,"judge_lane",at,right,up,160,halfWidth*2,0);
        if(!aether.needsFallback())return;
        for(int i=-80;i<=80;i+=3)for(double side:new double[]{-halfWidth,halfWidth})
            aether.fallbackDust(at.clone().add(right.clone().multiply(i)).add(up.clone().multiply(side)),RED,1.6f);
    }
    void ray(String key,Location from,Location to,double width){
        Vector delta=to.toVector().subtract(from.toVector());double length=delta.length();if(length<.01)return;
        Vector up=delta.clone().multiply(1/length),right=new Vector(1,0,0);
        if(Math.abs(up.dot(right))>.95)right=new Vector(0,0,1);
        right.subtract(up.clone().multiply(right.dot(up))).normalize();
        plane(key,"judge_ray",from.clone().add(delta.multiply(.5)),right,up,width,length,0);
    }
    void clearAttack(){
        for(var it=planes.entrySet().iterator();it.hasNext();){var entry=it.next();
            if(entry.getKey().startsWith("attack:")){entry.getValue().remove();it.remove();}}
    }
    void remove(String key){ItemDisplay display=planes.remove(key);if(display!=null)display.remove();}
    void clearEffects(){for(String key:new ArrayList<>(planes.keySet()))if(key.startsWith("fx:"))remove(key);}
    boolean owns(Entity entity){return planes.containsValue(entity);}
    @Override public void close(){for(ItemDisplay display:planes.values())display.remove();planes.clear();}
}
