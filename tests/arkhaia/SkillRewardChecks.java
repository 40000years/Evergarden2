import com.example.advancemagic.AdvanceMagicPlugin;
import com.example.advancemagic.elemental.*;
import com.example.voidscape.world.ElementalScrollTreasure;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.loot.LootTables;
import org.bukkit.persistence.PersistentDataType;

/** Production progression and container events, including player-made and cancelled opens. */
public final class SkillRewardChecks implements Listener {
    final SanctuaryCombatProbe probe;boolean cancel;
    SkillRewardChecks(SanctuaryCombatProbe probe){this.probe=probe;}
    @EventHandler(priority=EventPriority.HIGH) public void opening(InventoryOpenEvent event){if(cancel)event.setCancelled(true);}
    void check(boolean ok,String label){probe.check(ok,label);}
    void open(Chest chest){probe.player.openInventory(chest.getInventory());probe.player.closeInventory();}
    Chest chest(Location at){at.getBlock().setType(Material.CHEST,false);at.clone().add(0,1,0).getBlock().setType(Material.AIR,false);return (Chest)at.getBlock().getState();}
    void inspect()throws Exception{
        var player=probe.player;var world=probe.world;var magic=(AdvanceMagicPlugin)Bukkit.getPluginManager().getPlugin("advance-magic");var tree=magic.skillTree();
        Location home=player.getLocation(),out=home.clone().add(120,0,0);player.teleport(out);
        int original=tree.points(player);long xp=tree.xp(player);
        Zombie zombie=world.spawn(out.clone().add(2,0,0),Zombie.class,z->z.setAI(false));zombie.damage(1000,player);
        check(zombie.isDead()&&tree.points(player)==original&&tree.xp(player)==xp,"killing an ordinary monster grants no Arcane XP or skill points");
        magic.staffSpells().discover(player,ElementalSpell.FIREBALL);tree.discover(player);tree.discover(player);
        check(tree.points(player)==original&&tree.xp(player)==xp,"discovering scrolls keeps the codex without granting progression");
        var legacy=new NamespacedKey("advance-magic","tree_xp");player.getPersistentDataContainer().set(legacy,PersistentDataType.LONG,300L);
        int before=tree.points(player);long vanilla=player.getTotalExperience();
        tree.grantPoints(player,1,"Probe boss");
        check(tree.points(player)==before+1&&tree.xp(player)==300&&player.getTotalExperience()==vanilla,"direct rewards preserve legacy Arcane XP and vanilla experience");
        check(tree.learn(player,SkillTreeService.Node.POWER),"a rewarded point can immediately buy a real skill rank");
        int rank=tree.rank(player,SkillTreeService.Node.POWER);tree.discover(player);
        check(tree.rank(player,SkillTreeService.Node.POWER)==rank,"existing learned skills survive the progression migration");
        int chestXp=tree.structureChestXp(),level=tree.level(player);long exp=tree.xp(player);
        Chest normal=chest(out.clone().add(4,0,0));before=tree.points(player);open(normal);
        check(tree.xp(player)==exp&&tree.points(player)==before,"player-made chest grants no Arcane XP");
        Chest custom=chest(out.clone().add(6,0,0));ElementalScrollTreasure.add(custom,"exploration");
        Bukkit.getPluginManager().registerEvents(this,probe);cancel=true;open(custom);cancel=false;
        check(tree.xp(player)==exp,"cancelled structure chest open cannot consume or grant its XP");
        open(custom);check(chestXp>0&&tree.xp(player)==(exp+=chestXp),"a real authored exploration chest grants its Arcane XP once");
        check(tree.points(player)-before==tree.level(player)-level,"structure chests give points only through Arcane levels");
        open(custom);check(tree.xp(player)==exp,"reopening the same structure chest cannot farm more XP");
        custom.getBlock().setType(Material.AIR,false);custom=chest(custom.getLocation());SkillTreeService.markStructureChest(custom);open(custom);
        check(tree.xp(player)==exp,"breaking replacing and remarking a claimed chest cannot reset its coordinate claim");
        Chest vanillaChest=chest(out.clone().add(8,0,0));vanillaChest.setLootTable(LootTables.SIMPLE_DUNGEON.getLootTable());vanillaChest.update(false,false);open((Chest)vanillaChest.getBlock().getState());
        check(tree.xp(player)==(exp+=chestXp),"lazy vanilla dungeon loot marks a structure chest before its first successful open");
        open((Chest)vanillaChest.getBlock().getState());check(tree.xp(player)==exp,"vanilla structure chest is a one-time shared reward");
        Chest doubleA=chest(out.clone().add(10,0,0)),doubleB=chest(out.clone().add(11,0,0));
        var left=(org.bukkit.block.data.type.Chest)doubleA.getBlockData();left.setFacing(org.bukkit.block.BlockFace.NORTH);left.setType(org.bukkit.block.data.type.Chest.Type.LEFT);doubleA.getBlock().setBlockData(left,false);
        var right=(org.bukkit.block.data.type.Chest)doubleB.getBlockData();right.setFacing(org.bukkit.block.BlockFace.NORTH);right.setType(org.bukkit.block.data.type.Chest.Type.RIGHT);doubleB.getBlock().setBlockData(right,false);
        doubleA=(Chest)doubleA.getBlock().getState();doubleB=(Chest)doubleB.getBlock().getState();SkillTreeService.markStructureChest(doubleA);SkillTreeService.markStructureChest(doubleB);
        check(doubleA.getInventory().getHolder() instanceof DoubleChest,"test uses a real double chest inventory");open(doubleA);
        check(tree.xp(player)==(exp+=chestXp),"opening a double structure chest grants one reward for the combined inventory");open(doubleB);
        check(tree.xp(player)==exp,"opening the other half cannot grant a second reward");
        Chest legacyChest=chest(out.clone().add(13,0,0));
        legacyChest.getChunk().getPersistentDataContainer().set(new NamespacedKey("evergarden","elemental_scroll_"+legacyChest.getX()+"_"+legacyChest.getY()+"_"+legacyChest.getZ()),PersistentDataType.BYTE,(byte)1);
        open(legacyChest);check(tree.xp(player)==(exp+=chestXp),"existing structure treasure with durable authored provenance gains its one-time reward after upgrading");
        open(legacyChest);check(tree.xp(player)==exp,"legacy structure provenance cannot recreate an already claimed reward");
        HandlerList.unregisterAll(this);player.teleport(home);
    }
}
