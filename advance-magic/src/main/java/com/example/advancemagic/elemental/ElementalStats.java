package com.example.advancemagic.elemental;

import com.example.advancemagic.AdvanceMagicPlugin;
import com.example.advancemagic.item.ElementalStaffService.Element;
import org.bukkit.entity.Player;
import java.util.*;

/** A single bounded stat snapshot per cast; third parties can register Provider with ServicesManager. */
public final class ElementalStats {
    public interface Provider { Snapshot stats(Player player,Snapshot base); }
    /** Twenty Efficiency ranks reach 40%; Regeneration reaches +150%. */
    public static final double MAX_REDUCTION=.4;
    public record Snapshot(double spellPower,double healingPower,double maxMana,double manaReduction,
                           double manaRegenBonus,Map<Element,Double> elementalBonuses) {
        public Snapshot {
            spellPower=bound(spellPower,1,10000);healingPower=bound(healingPower,0,10000);
            maxMana=bound(maxMana,10,300);manaReduction=bound(manaReduction,0,MAX_REDUCTION);
            manaRegenBonus=bound(manaRegenBonus,0,1.5);
            var safe=new EnumMap<Element,Double>(Element.class);
            if(elementalBonuses!=null)elementalBonuses.forEach((k,v)->{if(k!=null&&v!=null)safe.put(k,bound(v,0,1));});
            elementalBonuses=Collections.unmodifiableMap(safe);
        }
        public double elementBonus(Element element){return elementalBonuses.getOrDefault(element,0.0);}
        public int cost(ElementalSpell spell){return Math.max(1,(int)Math.ceil(spell.mana()*(1-manaReduction)));}
    }
    private final AdvanceMagicPlugin plugin;
    public ElementalStats(AdvanceMagicPlugin plugin){this.plugin=plugin;}
    public Snapshot snapshot(Player p){
        var c=plugin.getConfig();var a=plugin.mana().account(p);
        double power=c.getDouble("elemental-system.stats.spell-power",12);
        double healing=c.getDouble("elemental-system.stats.healing-power",6);
        double max=a.baseMaxMana(),reduction=0,regen=Math.max(0,a.regenRate()/2-1);
        var bonuses=new EnumMap<Element,Double>(Element.class);
        if(plugin.skillTree()!=null){
            var tree=plugin.skillTree();
            // Tiered ranks: percent values except Mana, which is flat maximum mana.
            power*=1+tree.value(p,SkillTreeService.Node.POWER)/100;
            healing*=1+tree.value(p,SkillTreeService.Node.HEALING)/100;
            // Existing potion progression remains the base, never written over by the tree.
            max+=tree.value(p,SkillTreeService.Node.MANA);
            reduction=tree.value(p,SkillTreeService.Node.EFFICIENCY)/100;
            regen+=tree.value(p,SkillTreeService.Node.REGEN)/100;
            for(Element e:Element.values())bonuses.put(e,tree.value(p,SkillTreeService.Node.valueOf(e.name()))/100);
        }
        Snapshot result=new Snapshot(power,healing,max,reduction,regen,bonuses);
        var registration=plugin.getServer().getServicesManager().getRegistration(Provider.class);
        if(registration!=null)try {
            Snapshot external=registration.getProvider().stats(p,result);
            if(external!=null)result=new Snapshot(external.spellPower(),external.healingPower(),external.maxMana(),external.manaReduction(),external.manaRegenBonus(),external.elementalBonuses());
        }catch(RuntimeException error){plugin.getLogger().warning("Elemental stat provider failed: "+error.getClass().getSimpleName());}
        return result;
    }
    private static double bound(double n,double min,double max){return Double.isFinite(n)?Math.clamp(n,min,max):min;}
}
