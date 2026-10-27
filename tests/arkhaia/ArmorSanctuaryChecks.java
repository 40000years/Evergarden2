import com.example.advancemagic.AdvanceMagicPlugin;
import com.example.advancemagic.item.ArmorSetService;
import com.example.advancemagic.item.ArmorSetService.*;
import com.example.voidscape.world.OverworldSanctuaries.Kind;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.inventory.ItemStack;

/** Production Sanctuary event cancellation, virtual health and victory rewards together. */
final class ArmorSanctuaryChecks {
    private final SanctuaryCombatProbe p;
    ArmorSanctuaryChecks(SanctuaryCombatProbe probe){p=probe;}
    private void reset(Object run)throws Exception{
        p.put(run,"readyAt",0L);p.call(p.manager,"reset",new Class<?>[]{run.getClass()},run);
    }
    private void equip(ArmorSetService sets,ArmorSet set){
        p.player.getInventory().setArmorContents(new ItemStack[4]);
        for(Piece piece:Piece.values())p.player.getInventory().setItem(piece.slot,sets.create(set,piece));p.actor.doTick();
    }
    void inspect()throws Exception{
        var magic=(AdvanceMagicPlugin)Bukkit.getPluginManager().getPlugin("advance-magic");
        var sets=new ArmorSetService(magic);var combat=magic.armorCombat();
        var saved=p.player.getInventory().getArmorContents();var location=p.player.getLocation();
        Object r=p.run(Kind.ACHERON);reset(r);p.player.teleport(p.arena(Kind.ACHERON).center().add(0,0,5));
        equip(sets,ArmorSet.REVENANT);p.player.setInvulnerable(true);p.player.setHealth(10);
        double before=p.health(r);p.shoot(r,10);double dealt=before-p.health(r);
        p.check(dealt>0&&Math.abs(p.player.getHealth()-(10+dealt*.5))<1e-5,"real Sanctuary virtual hit heals half confirmed HP exactly once despite cancelling native damage");
        double healed=p.player.getHealth();p.shoot(r,10);
        p.check(p.player.getHealth()==healed&&p.health(r)==before-dealt,"Sanctuary cooldown-rejected hit grants no extra lifesteal");
        p.arenaTicks(11);p.shoot(r,1_000_000);
        p.check(combat.souls(p.player)==1&&p.player.getAttribute(Attribute.MAX_HEALTH).getValue()==202,"actual Sanctuary defeat awards one unique soul through production BossRewards");
        reset(r);p.shoot(r,1_000_000);
        p.check(combat.souls(p.player)==1&&p.player.getAttribute(Attribute.MAX_HEALTH).getValue()==202,"a second actual Acheron victory never stacks the same HP reward");
        reset(r);equip(sets,ArmorSet.SERAPH);combat.refresh(p.player);before=p.health(r);p.shoot(r,10);
        p.check(before-p.health(r)>30&&before-p.health(r)<=40,"actual undead Sanctuary boss receives Seraph's separate x4 damage");
        p.arenaTicks(1);
        p.check(p.mob(r).getTarget()==null&&!p.mob(r).hasAI(),"scripted undead Sanctuary boss cannot start chasing full Seraph wearer");
        p.player.getInventory().setArmorContents(saved);combat.refresh(p.player);p.player.setHealth(200);p.player.setInvulnerable(false);
        p.player.teleport(location);reset(r);
    }
}
