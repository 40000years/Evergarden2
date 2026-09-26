package com.example.advancemagic.spell;

import org.bukkit.entity.Player;
import java.util.*;
import java.util.function.Predicate;

public final class SpellRegistry {
    private final EnumMap<Spell,Predicate<Player>> spells=new EnumMap<>(Spell.class);
    public SpellRegistry(MagicContext c,AreaSpells area,ProjectileSpells projectile) {
        MobilitySpells mobility=new MobilitySpells(c);ChannelSpells channel=new ChannelSpells(c);
        MythicSpells mythic=new MythicSpells(c);
        spells.put(Spell.LIGHTNING_STRIKE,area::lightning);
        spells.put(Spell.FROST_NOVA,area::frost);
        spells.put(Spell.SHADOW_STEP,mobility::shadowStep);
        spells.put(Spell.NATURES_BLOOM,mobility::bloom);
        spells.put(Spell.EARTH_WALL,area::wall);
        spells.put(Spell.DRAGONS_BREATH,projectile::dragon);
        spells.put(Spell.VOID_PULL,projectile::voidPull);
        spells.put(Spell.SONIC_BOOM,area::sonicBoom);
        spells.put(Spell.BLAZE_BARRAGE,projectile::blaze);
        spells.put(Spell.WITHER_RAY,projectile::wither);
        spells.put(Spell.SHULKER_LEVITATION,projectile::shulker);
        spells.put(Spell.METEOR_STRIKE,projectile::meteor);
        spells.put(Spell.IRON_ARMOR,mobility::armor);
        spells.put(Spell.VEX_LEGION,area::vexLegion);
        spells.put(Spell.GUARDIAN_BEAM,channel::guardianBeam);
        spells.put(Spell.SOLAR_APOCALYPSE,mythic::solar);
        spells.put(Spell.CHRONOS_FINAL_HOUR,mythic::chronos);
        if(spells.size()!=Spell.values().length)throw new IllegalStateException("Missing spell implementation");
    }
    public boolean cast(Player player,Spell spell){return spells.get(spell).test(player);}
}
