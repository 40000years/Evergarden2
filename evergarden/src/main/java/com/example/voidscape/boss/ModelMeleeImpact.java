package com.example.voidscape.boss;

import com.example.advancemagic.AdvanceMagicPlugin;
import org.bukkit.*;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import java.util.*;

/** Short impact at the attacker's ears, called only after encounter damage is accepted. */
final class ModelMeleeImpact {
    private static int heardTick=Integer.MIN_VALUE;
    private static final Set<UUID> HEARD=new HashSet<>();

    static boolean play(Player attacker,EntityDamageEvent event) {
        if(!(event instanceof EntityDamageByEntityEvent by)||by.getDamager()!=attacker
            ||event.getCause()!=EntityDamageEvent.DamageCause.ENTITY_ATTACK
                &&event.getCause()!=EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK)return false;
        var type=event.getDamageSource().getDamageType();
        if(type!=DamageType.PLAYER_ATTACK&&type!=DamageType.MOB_ATTACK)return false;
        if(!Double.isFinite(event.getFinalDamage())||event.getFinalDamage()<=0)return false;
        var addon=Bukkit.getPluginManager().getPlugin("advance-magic");
        if(addon instanceof AdvanceMagicPlugin magic&&magic.isEnabled()&&magic.context()!=null&&magic.context().isMagicDamage())return false;
        // The virtual-health handlers intentionally cancel native HP damage.
        // Their accepted-hit call site, rather than isCancelled(), owns confirmation.
        int tick=Bukkit.getCurrentTick();
        if(tick!=heardTick){heardTick=tick;HEARD.clear();}
        if(!HEARD.add(attacker.getUniqueId()))return true;
        Location ears=attacker.getLocation();
        attacker.playSound(ears,Sound.ENTITY_PLAYER_ATTACK_STRONG,SoundCategory.PLAYERS,.8f,.95f);
        attacker.playSound(ears,Sound.ITEM_MACE_SMASH_AIR,SoundCategory.PLAYERS,.55f,1.15f);
        attacker.playSound(ears,Sound.BLOCK_ANVIL_HIT,SoundCategory.PLAYERS,.18f,1.55f);
        return true;
    }
    private ModelMeleeImpact(){}
}
