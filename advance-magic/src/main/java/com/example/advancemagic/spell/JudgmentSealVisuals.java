package com.example.advancemagic.spell;

import com.example.advancemagic.effect.EffectEngine;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Four full-brightness horizontal sigils, owned and removed by the cast scope. */
final class JudgmentSealVisuals {
    static final double[] HEIGHTS={14,20,27,35},RADII={11,16,22,28};
    private final ItemDisplay[] seals=new ItemDisplay[4];
    private final Location[] positions=new Location[4];
    private final double scale;
    JudgmentSealVisuals(EffectEngine.Effect effect,Location base,double scale) {
        this.scale=scale;
        for(int i=0;i<4;i++) {
            final int layer=i;
            positions[i]=base.clone().add(0,HEIGHTS[i]*scale,0);
            ItemStack item=new ItemStack(Material.PAPER);
            var meta=item.getItemMeta();meta.setItemModel(new NamespacedKey("advance_magic","judgment_seal_"+(i%2)));
            item.setItemMeta(meta);
            seals[i]=effect.track(base.getWorld().spawn(positions[i],ItemDisplay.class,entity->{
                entity.setVisibleByDefault(false);entity.setItemStack(item);
                entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                entity.setGravity(false);entity.setInvulnerable(true);entity.setSilent(true);
                entity.setBrightness(new Display.Brightness(15,15));
                entity.setShadowRadius(0);entity.setShadowStrength(0);entity.setViewRange(1f);
                entity.setDisplayWidth((float)(RADII[layer]*2*scale));entity.setDisplayHeight(2);
                entity.setInterpolationDelay(0);entity.setInterpolationDuration(4);
                entity.setTransformation(transform(layer,0,.08));
            }));
        }
    }
    private Transformation transform(int layer,int age,double growth) {
        float diameter=(float)(RADII[layer]*2*scale*growth);
        float turn=(float)(age*.009*(layer%2==0?1:-1)+layer*.3);
        return new Transformation(new Vector3f(),new Quaternionf().rotateY(turn),
            new Vector3f(diameter,1,diameter),new Quaternionf());
    }
    void frame(MythicVisuals visuals,int age) {
        double growth=age<40?.15+.85*Math.sin(Math.PI*.5*age/40.0):1;
        // The seals retract gradually after the last beam pulse.
        if(age>=140)growth*=Math.max(.08,1-(age-140)/24.0);
        for(int i=0;i<4;i++)if(seals[i].isValid()) {
            seals[i].setInterpolationDelay(0);seals[i].setTransformation(transform(i,age,growth));
            visuals.showSeal(seals[i]);
            visuals.judgmentSeal(positions[i],RADII[i]*2*scale*growth,
                Math.toDegrees(age*.009*(i%2==0?1:-1)+i*.3),i%2);
        }
    }
}
