import com.example.advancemagic.AdvanceMagicPlugin;
import org.bukkit.*;
import org.bukkit.entity.*;

/** Final boss rewards and the existing offline world-boss claim path. */
public final class BossRewardHooksProbe extends ArkhaiaProbe {
    @Override void mechanics()throws Exception{
        Bukkit.getPluginManager().registerEvents(this,this);
        world.setDifficulty(Difficulty.NORMAL);
        var magic=(AdvanceMagicPlugin)Bukkit.getPluginManager().getPlugin("advance-magic");var tree=magic.skillTree();
        int before=tree.points(player);var ledger=(org.bukkit.configuration.file.YamlConfiguration)get(garden.worldBoss(),"ledger");
        String path="rewards."+player.getUniqueId();ledger.set(path+".skill-points",1);
        garden.worldBoss().claimReward(player);
        check(tree.points(player)==before+1&&!ledger.contains(path),"queued world-boss skill point is delivered and its claim consumed");
        garden.worldBoss().claimReward(player);check(tree.points(player)==before+1,"repeated world-boss claim cannot duplicate skill points");
        World vanilla=Bukkit.getWorlds().getFirst();vanilla.setDifficulty(Difficulty.NORMAL);
        Location home=player.getLocation(),outside=new Location(vanilla,.5,120,.5);player.teleport(outside);
        Wither wither=vanilla.spawn(outside.clone().add(3,0,0),Wither.class,w->{w.setAI(false);w.setInvulnerabilityTicks(0);});
        before=tree.points(player);wither.damage(100000,player);
        check(wither.isDead()&&tree.points(player)==before+1,"actual native Wither defeat grants one direct point, including an immediate first-tick kill");
        player.teleport(home);before=tree.points(player);position(5);magic(1000000,org.bukkit.damage.DamageType.INDIRECT_MAGIC);
        check(hp()==3500&&tree.points(player)==before,"Arkhaia phase transition gives no premature reward");
        advance(11);magic(1000000,org.bukkit.damage.DamageType.INDIRECT_MAGIC);
        check(parts().isEmpty()&&tree.points(player)==before+1,"Arkhaia's real final defeat grants one contributor point");
        advance(30);check(tree.points(player)==before+1,"closed Arkhaia cannot grant a duplicate point");
    }
}
