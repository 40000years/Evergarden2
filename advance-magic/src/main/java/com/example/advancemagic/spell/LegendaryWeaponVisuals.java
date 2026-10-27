package com.example.advancemagic.spell;

import com.example.advancemagic.AdvanceMagicPlugin;
import com.example.advancemagic.item.LegendaryWeaponService.Weapon;
import org.bukkit.entity.Player;
import org.bukkit.entity.LivingEntity;
import org.bukkit.*;
import org.bukkit.util.Vector;
import com.example.advancemagic.effect.EffectEngine;
import java.util.function.BiPredicate;
import java.util.function.BooleanSupplier;

/** The established Advance Magic Java plane / native Bedrock particle bridge. */
public final class LegendaryWeaponVisuals {
    private LegendaryWeaponVisuals(){}
    /** Authored rotating seals share their geometry with the native Bedrock bridge. */
    public static EffectEngine.Effect ritual(AdvanceMagicPlugin plugin,Player player,Weapon weapon,Location center,int duration,double radius,BiPredicate<Integer,Location> gameplay){
        var visuals=new MythicVisuals(plugin.context());
        return plugin.effects().start(player,duration,new BiPredicate<>(){
            MythicLineVisuals seal,halo;
            final java.util.List<MythicLineVisuals> spears=new java.util.ArrayList<>();
            public boolean test(EffectEngine.Effect effect,Integer age){
                boolean follows=weapon==Weapon.MJOLNIR||weapon==Weapon.TYRFING||weapon==Weapon.EXCALIBUR;
                var at=follows?player.getLocation():center.clone();
                if(seal==null){
                    seal=new MythicLineVisuals(effect,at,"legendary_"+weapon.id()+"_sigil");
                    halo=new MythicLineVisuals(effect,at,"legendary_"+weapon.id()+"_sigil");
                    if(weapon==Weapon.LONGINUS)for(int i=0;i<12;i++)spears.add(new MythicLineVisuals(effect,at,"legendary_longinus_rain"));
                    effect.onClose(visuals::clear);
                    at.getWorld().playSound(at,weapon==Weapon.TYRFING?Sound.ENTITY_WARDEN_EMERGE:weapon==Weapon.MJOLNIR?Sound.ITEM_TRIDENT_THUNDER:Sound.BLOCK_BEACON_ACTIVATE,1,weapon==Weapon.TYRFING?.6f:1);
                }
                if(!gameplay.test(age,at))return false;
                if(age%4!=0)return true;
                visuals.frame(at);
                double size=weapon==Weapon.VAJRA?Math.max(.4,radius*2*Math.min(1,age/16.0)):
                    weapon==Weapon.EXCALIBUR?2.5+2.5*age/duration:radius*2;
                seal.plane(visuals,at.clone().add(0,.08,0),MythicLineVisuals.X,MythicLineVisuals.Z,size,age*.018);
                var top=at.clone().add(0,weapon==Weapon.LONGINUS?12:weapon==Weapon.EXCALIBUR?2:3,0);
                halo.plane(visuals,top,MythicLineVisuals.X,MythicLineVisuals.Z,size*.8,-age*.026);
                Color color=Color.fromRGB(weapon==Weapon.TYRFING?0xFF315B:weapon==Weapon.MJOLNIR||weapon==Weapon.VAJRA?0x72EDFF:0xFFD34D);
                for(int i=0;i<24;i++){
                    double a=i*Math.PI/12+age*.025;double r=size/2;
                    visuals.dust(at.clone().add(Math.cos(a)*r,.2+(age%20)*.025,Math.sin(a)*r),color,1.2f);
                }
                if(weapon==Weapon.LONGINUS)for(int i=0;i<spears.size();i++){
                    double a=i*2.399963+(age/20)*.65;double r=radius*Math.sqrt((i+.5)/12);
                    double y=12-((age+i*3)%20)/20.0*12;
                    var point=at.clone().add(Math.cos(a)*r,y,Math.sin(a)*r);
                    spears.get(i).plane(visuals,point,MythicLineVisuals.X,MythicLineVisuals.Y,2.8,Math.PI);
                    visuals.dust(point,color,1.4f);
                }
                if(age%20==0)at.getWorld().playSound(at,weapon==Weapon.TYRFING?Sound.ENTITY_WARDEN_HEARTBEAT:weapon==Weapon.MJOLNIR?Sound.ENTITY_LIGHTNING_BOLT_THUNDER:Sound.BLOCK_AMETHYST_BLOCK_RESONATE,.7f,1.25f);
                return true;
            }
        });
    }
    public static void impact(AdvanceMagicPlugin plugin,Player player,Weapon weapon,Location at,double radius){
        if(!plugin.effects().hasCapacity())return;
        ritual(plugin,player,weapon,at,16,radius,(age,point)->true);
        at.getWorld().spawnParticle(weapon==Weapon.MJOLNIR?Particle.ELECTRIC_SPARK:Particle.ENCHANT,at.clone().add(0,1,0),35,.7,.8,.7,.08);
    }
    public static void soul(AdvanceMagicPlugin plugin,Player player,LivingEntity target){
        var from=target.getLocation().add(0,1,0);var delta=player.getLocation().add(0,1,0).toVector().subtract(from.toVector());
        for(int i=0;i<12;i++)from.getWorld().spawnParticle(Particle.DUST,from.clone().add(delta.clone().multiply(i/12.0)),1,.06,.06,.06,0,new Particle.DustOptions(Color.fromRGB(0xFF315B),1.3f));
        player.getWorld().playSound(player.getLocation(),Sound.ENTITY_ALLAY_ITEM_GIVEN,.6f,.5f);
    }
    public static void release(AdvanceMagicPlugin plugin,Player player,Weapon weapon,Location from,Vector direction,double length){
        if(!plugin.effects().hasCapacity())return;
        var visuals=new MythicVisuals(plugin.context());
        plugin.effects().start(player,16,new BiPredicate<>(){
            MythicLineVisuals beam;
            public boolean test(EffectEngine.Effect effect,Integer age){
                if(beam==null){beam=new MythicLineVisuals(effect,from,"solar_ray");effect.onClose(visuals::clear);from.getWorld().playSound(from,Sound.ENTITY_WARDEN_SONIC_BOOM,1,1.3f);}
                if(age%4==0){visuals.frame(from);beam.ray(visuals,from,from.clone().add(direction.clone().multiply(length)),Math.max(.1,2.5-age*.13));
                    for(double d=0;d<length;d+=.4)visuals.dust(from.clone().add(direction.clone().multiply(d)),Color.fromRGB(0xFFD34D),1.5f);}
                return true;
            }
        });
    }
    public static void equip(AdvanceMagicPlugin plugin,Player player,Weapon weapon,BooleanSupplier stillHolding){
        if(!plugin.effects().hasCapacity())return;
        var visuals=new MythicVisuals(plugin.context());
        // Plane callbacks remain inside EffectEngine so death/world change/disable
        // uses the same cleanup as every spell, including the tracked ItemDisplay.
        plugin.effects().start(player,24,new java.util.function.BiPredicate<>(){
            MythicLineVisuals sigil;
            public boolean test(com.example.advancemagic.effect.EffectEngine.Effect current,Integer age){
                if(!stillHolding.getAsBoolean())return false;
                if(sigil==null){sigil=new MythicLineVisuals(current,player.getLocation(),"legendary_"+weapon.id()+"_sigil");current.onClose(visuals::clear);}
                if(age%4==0){
                    var at=player.getLocation().add(0,.06,0);visuals.frame(at);
                    double diameter=1.1+Math.sin(age/24.0*Math.PI)*.5;
                    sigil.plane(visuals,at,MythicLineVisuals.X,MythicLineVisuals.Z,diameter,age*.025);
                }
                return true;
            }
        });
    }
}
