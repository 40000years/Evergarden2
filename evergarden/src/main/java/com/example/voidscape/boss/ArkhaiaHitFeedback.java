package com.example.voidscape.boss;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.entity.EntityDamageEvent;
import java.util.Locale;

/** Called only after encounter health actually decreases, including lethal hits. */
final class ArkhaiaHitFeedback {
    private ArkhaiaHitFeedback(){}
    static void confirm(Player attacker,LivingEntity target,EntityDamageEvent event,double dealt,boolean rear){
        if(!Double.isFinite(dealt)||dealt<=0)return;
        var addon=Bukkit.getPluginManager().getPlugin("advance-magic");
        if(addon instanceof com.example.advancemagic.AdvanceMagicPlugin magic&&magic.isEnabled()&&magic.armorCombat()!=null)
            magic.armorCombat().confirmedHit(attacker,dealt);
        boolean projectile=event.getDamageSource().getDirectEntity() instanceof Projectile
            ||event.getCause()==EntityDamageEvent.DamageCause.PROJECTILE;
        // Play at the shooter's ears: distance to a giant must not mute confirmation.
        if(projectile||!ModelMeleeImpact.play(attacker,event))
            attacker.playSound(attacker.getLocation(),projectile?Sound.ENTITY_ARROW_HIT_PLAYER:Sound.ENTITY_PLAYER_ATTACK_STRONG,
                SoundCategory.PLAYERS,.8f,rear?1.25f:1f);
        if(rear)attacker.playSound(attacker.getLocation(),Sound.BLOCK_AMETHYST_BLOCK_CHIME,SoundCategory.PLAYERS,.65f,1.5f);
        var at=target.getLocation();
        at.setY(Math.clamp(attacker.getEyeLocation().getY(),target.getY()+.5,target.getBoundingBox().getMaxY()-.1));
        attacker.spawnParticle(rear?Particle.ENCHANTED_HIT:Particle.DAMAGE_INDICATOR,at,rear?12:5,.6,.35,.6,.05);
        attacker.sendActionBar(net.kyori.adventure.text.Component.text(String.format(Locale.ROOT,
            rear?"REAR HIT  -%.1f HP":"HIT  -%.1f HP",dealt),rear?net.kyori.adventure.text.format.NamedTextColor.LIGHT_PURPLE:net.kyori.adventure.text.format.NamedTextColor.GOLD));
    }
}
