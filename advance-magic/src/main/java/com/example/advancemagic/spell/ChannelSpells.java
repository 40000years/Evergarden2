package com.example.advancemagic.spell;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.*;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

public final class ChannelSpells {
    private final MagicContext c;
    public ChannelSpells(MagicContext c){this.c=c;}
    public boolean guardianBeam(Player p) {
        LivingEntity target=c.targetEntity(p,22);if(target==null)return false;
        c.plugin.effects().start(p,31,(effect,age)->{
            if(p.getWorld()!=target.getWorld())return false;
            // A target killed by an earlier pulse must still trigger the final burst.
            if(target.isDead()) {
                tidalBurst(p,target.getLocation());
                return false;
            }
            if(!c.enemy(p,target)||p.getLocation().distanceSquared(target.getLocation())>484
                ||!c.clear(p.getEyeLocation(),target.getEyeLocation()))return false;
            c.beam(p.getEyeLocation(),target.getEyeLocation(),Particle.BUBBLE_POP);
            if(age%2==0)c.beam(p.getEyeLocation(),target.getEyeLocation(),Particle.ELECTRIC_SPARK);
            if(age%10==0)p.getWorld().playSound(p.getLocation(),Sound.ENTITY_GUARDIAN_ATTACK,0.9f,1.2f+(age/30.0f)*0.5f);
            // Continuous shock pulses
            if(age>0&&age%6==0) {
                if(c.affect(p,target,Spell.GUARDIAN_BEAM)) {
                    c.damage(p,target,36,DamageType.MAGIC);
                    target.getWorld().playSound(target.getLocation(),Sound.ENTITY_GUARDIAN_FLOP,0.6f,1.8f);
                }
            }
            // Stage 2: Tidal Burst Detonation at channel culmination.
            if(age==30) {
                tidalBurst(p,target.getLocation());
                return false;
            }
            return true;
        });return true;
    }

    private void tidalBurst(Player p,Location at) {
        c.echo(p,at,Spell.GUARDIAN_BEAM,14,6,80);
        at.getWorld().playSound(at,Sound.ENTITY_PLAYER_SPLASH_HIGH_SPEED,1.5f,0.7f);
        at.getWorld().playSound(at,Sound.ENTITY_GENERIC_EXPLODE,1.0f,1.4f);
        c.ring(at,6,Spell.GUARDIAN_BEAM);
        c.particles(at.clone().add(0,1,0),Particle.SPLASH,50,2.0);
        c.particles(at.clone().add(0,1,0),Particle.BUBBLE_POP,40,1.5);
        c.particles(at.clone().add(0,1,0),Particle.BUBBLE,60,2.5);
        for(var e:c.nearby(p,at,6.0,false))if(c.affect(p,e,Spell.GUARDIAN_BEAM)) {
            c.damage(p,e,c.configuredDamage("damage.guardian-tidal-burst",67.5),DamageType.MAGIC);
            Vector push=e.getLocation().toVector().subtract(at.toVector()).setY(0);
            if(push.lengthSquared()>0.01)c.velocity(e,push.normalize().multiply(1.2).setY(0.4));
            else c.velocity(e,new Vector(0,0.6,0));
        }
        c.potion(p,PotionEffectType.REGENERATION,80,1);
        c.potion(p,PotionEffectType.SPEED,80,0);
    }
}
