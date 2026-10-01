package com.example.advancemagic.spell;

import com.example.advancemagic.effect.EffectEngine;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** One owned, non-colliding display for the enlarged native beacon texture. */
final class JudgmentBeamVisuals {
    private final ItemDisplay display;
    private final float height;
    JudgmentBeamVisuals(EffectEngine.Effect effect,Location base,double height) {
        this.height=(float)height;
        ItemStack item=new ItemStack(Material.YELLOW_STAINED_GLASS);
        var meta=item.getItemMeta();
        meta.setItemModel(new NamespacedKey("advance_magic","judgment_beam"));
        item.setItemMeta(meta);
        display=effect.track(base.getWorld().spawn(base,ItemDisplay.class,entity->{
            entity.setItemStack(item);
            entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            entity.setGravity(false);entity.setInvulnerable(true);entity.setSilent(true);
            entity.setBrightness(new Display.Brightness(15,15));
            entity.setShadowRadius(0);entity.setShadowStrength(0);
            entity.setViewRange(1.6f);entity.setDisplayWidth(12);entity.setDisplayHeight(this.height*2);
            entity.setInterpolationDelay(0);entity.setInterpolationDuration(4);
            entity.setTransformation(transform(0,0.08f));
        }));
    }
    private Transformation transform(int age,float growth) {
        return new Transformation(new Vector3f(0,height/2,0),new Quaternionf().rotateY(age*.025f),
            new Vector3f(8*growth,height,8*growth),new Quaternionf());
    }
    void frame(MythicVisuals visuals,int age,float growth) {
        if(!display.isValid())return;
        display.setInterpolationDelay(0);display.setTransformation(transform(age,growth));
        visuals.hideBedrock(display);
    }
    void close(){display.remove();}
}
