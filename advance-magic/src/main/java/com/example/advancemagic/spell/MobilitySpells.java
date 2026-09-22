package com.example.advancemagic.spell;

import org.bukkit.*;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

public final class MobilitySpells {
    private final MagicContext c;
    public MobilitySpells(MagicContext c){this.c=c;}
    public boolean shadowStep(Player p) {
        if(p.isInsideVehicle())return false;
        Location start=p.getLocation(),last=null;
        Vector dir=start.getDirection();double wallStart=-1;boolean passedWall=false;
        for(double d=0.25;d<=12;d+=0.25) {
            Location next=start.clone().add(dir.clone().multiply(d));
            if(!c.loaded(next)||!c.loaded(next.clone().add(0,1.8,0)))break;
            if(!c.safeBody(next)) {
                if(passedWall)break;
                if(wallStart<0)wallStart=d;
                // A one-block wall plus the player's 0.6-block body footprint.
                if(d-wallStart>1.5)break;
            } else {
                if(wallStart>=0){passedWall=true;wallStart=-1;}
                last=next;
            }
        }
        if(last==null||last.distanceSquared(start)<1||!p.teleport(last,PlayerTeleportEvent.TeleportCause.PLUGIN))return false;
        p.setFallDistance(0);
        // Stage 1: Departure Abyssal Smoke Screen
        c.particles(start.clone().add(0,1,0),Particle.PORTAL,35,0.6);
        c.particles(start.clone().add(0,1,0),Particle.SQUID_INK,25,0.8);
        start.getWorld().playSound(start,Sound.ENTITY_ENDERMAN_TELEPORT,0.7f,0.8f);
        for(var e:c.nearby(p,start,4.0,false))if(c.affect(p,e,Spell.SHADOW_STEP)) {
            c.potion(e,PotionEffectType.BLINDNESS,60,0);
            c.potion(e,PotionEffectType.SLOWNESS,60,1);
        }
        // Stage 2: Arrival Shadow Rupture & Tactical Reposition
        c.particles(last.clone().add(0,1,0),Particle.PORTAL,35,0.6);
        c.particles(last.clone().add(0,1,0),Particle.SWEEP_ATTACK,12,0.6);
        p.getWorld().playSound(last,Sound.ENTITY_PLAYER_ATTACK_SWEEP,1.0f,1.4f);
        for(var e:c.nearby(p,last,3.5,false))if(c.affect(p,e,Spell.SHADOW_STEP)) {
            c.damage(p,e,c.configuredDamage("damage.shadow-step-rupture",37.5),DamageType.MAGIC);
            c.potion(e,PotionEffectType.DARKNESS,40,0);
        }
        c.potion(p,PotionEffectType.SPEED,50,1);
        c.potion(p,PotionEffectType.INVISIBILITY,40,0);
        c.echo(p,last,Spell.SHADOW_STEP,14,4,80);
        return true;
    }
    public boolean armor(Player p) {
        c.potion(p,PotionEffectType.RESISTANCE,1200,3);
        c.potion(p,PotionEffectType.FIRE_RESISTANCE,1200,0);
        c.potion(p,PotionEffectType.ABSORPTION,1200,3);
        c.potion(p,PotionEffectType.STRENGTH,1200,1);
        c.potion(p,PotionEffectType.SPEED,1200,0);
        c.plugin.statuses().armor(p);
        c.ring(p.getLocation(),1.5,Spell.IRON_ARMOR);
        p.getWorld().playSound(p.getLocation(),Sound.ITEM_ARMOR_EQUIP_NETHERITE,1.2f,0.8f);
        p.getWorld().playSound(p.getLocation(),Sound.BLOCK_ANVIL_USE,0.8f,1.2f);
        // Stage 1: Bastion Shockwave Repel
        for(var e:c.nearby(p,p.getLocation(),4.0,false))if(c.affect(p,e,Spell.IRON_ARMOR)) {
            Vector push=e.getLocation().toVector().subtract(p.getLocation().toVector()).setY(0);
            if(push.lengthSquared()>0.01)c.velocity(e,push.normalize().multiply(0.7).setY(0.3));
        }
        c.echo(p,p.getLocation(),Spell.IRON_ARMOR,20,5,100);
        return true;
    }
    public boolean bloom(Player p) {
        Location center=p.getLocation();
        java.util.List<PotionEffectType> negative=java.util.List.of(
            PotionEffectType.POISON,PotionEffectType.WITHER,PotionEffectType.SLOWNESS,
            PotionEffectType.WEAKNESS,PotionEffectType.BLINDNESS,PotionEffectType.NAUSEA,
            PotionEffectType.DARKNESS,PotionEffectType.MINING_FATIGUE,PotionEffectType.HUNGER
        );
        for(var ally:c.nearby(p,center,8,true))if(c.affect(p,ally,Spell.NATURES_BLOOM)) {
            for(PotionEffectType neg:negative)ally.removePotionEffect(neg);
            c.potion(ally,PotionEffectType.REGENERATION,900,3);
            c.potion(ally,PotionEffectType.ABSORPTION,900,4);
            c.potion(ally,PotionEffectType.STRENGTH,900,1);
            c.potion(ally,PotionEffectType.SPEED,900,1);
            if(ally instanceof Player pl) c.heal(pl,12.0);
            else if(ally.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH)!=null)
                ally.setHealth(Math.min(ally.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue(),ally.getHealth()+12.0));
            c.particles(ally.getLocation().add(0,1,0),Particle.HAPPY_VILLAGER,25,0.6);
        }
        c.plugin.effects().start(p,61,(effect,age)->{
            if(!c.loaded(center))return false;
            if(age%3==0)c.ring(center,Math.min(8,age/3.0+0.5),Spell.NATURES_BLOOM);
            // Stage 2: Second Bloom (Overgrowth & Entangling Roots) at tick 30
            if(age==30) {
                center.getWorld().playSound(center,Sound.BLOCK_CHERRY_SAPLING_PLACE,1.4f,0.8f);
                center.getWorld().playSound(center,Sound.ENTITY_EXPERIENCE_ORB_PICKUP,1.2f,0.6f);
                c.ring(center,8.5,Spell.NATURES_BLOOM);
                c.particles(center.clone().add(0,1,0),Particle.HAPPY_VILLAGER,40,2.5);
                for(var ally:c.nearby(p,center,8.5,true))if(c.affect(p,ally,Spell.NATURES_BLOOM)) {
                    if(ally instanceof Player pl)c.heal(pl,10.0);
                    c.potion(ally,PotionEffectType.SATURATION,40,1);
                }
                for(var enemy:c.nearby(p,center,8.5,false))if(c.affect(p,enemy,Spell.NATURES_BLOOM)) {
                    c.damage(p,enemy,c.configuredDamage("damage.natures-bloom-thorns",30),DamageType.MAGIC);
                    c.plugin.statuses().root(p,enemy);
                }
            }
            // Third bloom: one final heal and thorn pulse, without spending more mana.
            if(age==60) {
                c.ring(center,8.5,Spell.NATURES_BLOOM);
                for(var ally:c.nearby(p,center,8.5,true))if(c.affect(p,ally,Spell.NATURES_BLOOM)) {
                    if(ally instanceof Player pl)c.heal(pl,6);
                    c.potion(ally,PotionEffectType.RESISTANCE,100,0);
                }
                for(var enemy:c.nearby(p,center,8.5,false))if(c.affect(p,enemy,Spell.NATURES_BLOOM))
                    c.damage(p,enemy,c.configuredDamage("follow-up.damage.natures_bloom",22.5),DamageType.MAGIC);
            }
            return true;
        });
        p.getWorld().playSound(center,Sound.BLOCK_BEACON_ACTIVATE,1.0f,1.4f);
        return true;
    }
}
