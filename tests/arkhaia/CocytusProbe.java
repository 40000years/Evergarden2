import com.example.voidscape.boss.ArkhaiaWaves;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.damage.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import java.util.*;

/** Real Paper: full-arena cover, raised targets, break/pearl escape, execution and rain timing. */
public class CocytusProbe extends RiverTrialsProbe {
    @Override void run()throws Exception{SanctuaryProbeFixture.open(this,com.example.voidscape.world.OverworldSanctuaries.Kind.COCYTUS);mechanics();garden.sanctuaryEncounters().close();finish(null);}
    @Override Object minions(){return controller;}
    Object frost()throws Exception{return get(trials,"frost");}
    Map<?,?> prisons()throws Exception{return (Map<?,?>)get(frost(),"prisons");}
    List<?> shots()throws Exception{return (List<?>)get(frost(),"arrows");}
    List<?> rainPlans()throws Exception{return (List<?>)((Map<?,?>)get(frost(),"rainMarks")).get(state);}
    void clearHighRain(Location at){for(int x=-6;x<=6;x++)for(int z=-6;z<=6;z++)for(int y=15;y<=134;y++)world.getBlockAt(at.getBlockX()+x,pad.getBlockY()+y,at.getBlockZ()+z).setType(Material.AIR,false);}
    void freshCocytus(Location at)throws Exception{freshTrial(ArkhaiaWaves.Kind.COCYTUS);resetPlayer(at);if(at.getY()>pad.getY()+12)check((boolean)call(controller,"accepts",new Class<?>[]{Player.class},player),"high Cocytus fixture uses active sanctuary target eligibility");}
    void aimedDown(){actor.setYRot(0);actor.setXRot(90);}
    void waitStorm()throws Exception{int peak=shots().size();for(int i=0;i<200&&get(state,"move")!=null;i++){riverTicks(1);peak=Math.max(peak,shots().size());}check(get(state,"move")==null&&peak>=216&&peak<=1600,"storm completes with dense travelling arrows and a fixed entity bound");}
    void projectile(AbstractArrow arrow,ItemStack weapon)throws Exception{
        arrow.setShooter(player);arrow.setWeapon(weapon);Bukkit.getPluginManager().callEvent(new ProjectileLaunchEvent(arrow));
        try{mob.damage(100,DamageSource.builder(arrow instanceof Trident?DamageType.TRIDENT:DamageType.ARROW).withCausingEntity(player).withDirectEntity(arrow).build());}
        finally{arrow.remove();}
    }
    void arrowImmunity()throws Exception{
        freshCocytus(pad.clone().add(0,0,14));ItemStack bow=new ItemStack(Material.BOW);bow.addEnchantment(Enchantment.POWER,5);
        projectile(world.spawn(player.getEyeLocation(),Arrow.class),bow);
        check((double)get(unit,"health")==700,"Power V bow arrows cannot damage Cocytus");
        Arrow tipped=world.spawn(player.getEyeLocation(),Arrow.class);tipped.setBasePotionType(org.bukkit.potion.PotionType.POISON);projectile(tipped,bow);
        projectile(world.spawn(player.getEyeLocation(),SpectralArrow.class),new ItemStack(Material.CROSSBOW));
        check((double)get(unit,"health")==700&&((Map<?,?>)get(unit,"hits")).isEmpty(),"tipped and spectral crossbow arrows are immune without consuming accepted-hit cooldowns");
        var magic=((com.example.advancemagic.AdvanceMagicPlugin)Bukkit.getPluginManager().getPlugin("advance-magic")).context();
        magic.damage(player,mob,20,DamageType.MAGIC);
        check((double)get(unit,"health")<700&&((Map<?,?>)get(unit,"hits")).size()==1,"real Advance Magic still damages Cocytus immediately after rejected arrows; hp="+get(unit,"health"));
        freshCocytus(pad.clone().add(0,0,14));projectile(world.spawn(player.getEyeLocation(),Trident.class),new ItemStack(Material.TRIDENT));
        check((double)get(unit,"health")<700,"thrown tridents remain a valid distinct weapon");
        freshCocytus(pad.clone().add(.7,0,2.5));face(pad.clone().add(.7,1,0));mob.damage(20,player);
        check((double)get(unit,"health")<700,"reachable aimed melee still damages Cocytus");
        freshCocytus(pad.clone().add(0,0,14));begin("BLIZZARD");waitStorm();projectile(world.spawn(player.getEyeLocation(),Arrow.class),bow);
        check((double)get(unit,"health")==700,"Cocytus remains arrow immune during its exposed recovery");
    }
    @Override void mechanics()throws Exception{
        world.setDifficulty(Difficulty.NORMAL);controller=minions();invoke(controller,"demo",new Class<?>[]{UUID.class,boolean.class},player.getUniqueId(),false);
        pad=((Location)get(controller,"center")).clone();now=(long)get(controller,"clock");
        arrowImmunity();
        freshCocytus(pad.clone().add(0,0,16));
        check(mob.getAttribute(Attribute.MAX_HEALTH).getValue()==700&&(double)get(unit,"health")==700,"Cocytus has 700 native and virtual HP");
        var arena=new com.example.voidscape.world.SanctuaryArena(new com.example.voidscape.world.OverworldSanctuaries.Site(com.example.voidscape.world.OverworldSanctuaries.Kind.COCYTUS,0,0,0),world);
        check(arena.contains(arena.center().add(0,6,0))&&arena.contains(arena.center().add(0,60,0))&&!arena.containsActivation(arena.center().add(0,13,0)),"Cocytus active combat retains airborne targets while dormant activation keeps the authored ceiling");
        check(com.example.voidscape.boss.ArkhaiaBedrockSupport.riverClips("arkhaia_cocytus").equals(List.of("idle","walk","hover","cast_windup","cast_active","cast_recover","attack_windup","attack_active","attack_recover","stun","rain_windup","rain_active","rain_recover")),"Cocytus animation indexes match the thirteen shipped Java and Bedrock clips");
        begin("BLIZZARD");check(children("ice").size()==16,"storm supplies sixteen visible cover pillars around the full arena");riverTicks(55);
        check(shots().isEmpty()&&player.getHealth()==200,"storm retains its full 2.8-second shelter warning");
        riverTicks(1);Object shot=shots().getFirst();Arrow visual=(Arrow)get(shot,"entity");Location before=visual.getLocation();
        ((org.bukkit.craftbukkit.entity.CraftArrow)visual).getHandle().tick();
        check(visual.isValid()&&visual.getLocation().distanceSquared(before)<1e-8,"native arrow tick preserves swept visual position and does not create a second impact");
        waitStorm();check(player.getHealth()==200,"ice pillar blocks every storm wave including high-origin aimed arrows");
        check(shots().isEmpty()&&children("ice").isEmpty(),"storm arrows and cover close after the last flight");
        check((double)call(trials,"incoming",new Class<?>[]{unit.getClass(),double.class,long.class},unit,20d,now)==30,"surviving the storm opens the damage window");
        check((long)get(state,"recoveryUntil")==now+36&&(long)get(state,"next")==now+52,"storm recovery and next-cast delay are shortened to 1.8 and 0.8 seconds");
        for(double side:new double[]{1,-1}){
            freshCocytus(pad.clone().add(side*6,0,side*14));begin("BLIZZARD");waitStorm();
            check(player.getHealth()==56,"six storm waves damage an uncovered target on side "+side+" exactly once per wave for 24");
        }
        freshCocytus(pad.clone().add(6,6,14));begin("BLIZZARD");waitStorm();check(player.getHealth()<200,"standing six blocks above the arena does not evade the storm");
        clearHighRain(pad.clone().add(6,0,14));
        freshCocytus(pad.clone().add(6,90,14));begin("BLIZZARD");riverTicks(56);
        Object highStorm=shots().getLast();Vector highStep=(Vector)get(highStorm,"step");
        double highDistance=((Location)get(highStorm,"at")).distance(player.getLocation().add(0,.9,0));
        check(highStep.length()>2&&highDistance/highStep.length()<=40.00001,"storm aimed arrows retain a forty-tick flight limit for targets ninety blocks overhead");
        riverTicks(104);check(player.getHealth()==56&&shots().isEmpty()&&get(state,"move")==null,"all six distant aimed storm waves hit and clean up within the faster 160-tick storm");

        freshCocytus(pad.clone().add(0,0,14));begin("FISSURE");riverTicks(21);
        check(prisons().isEmpty()&&player.getHealth()==200,"spike circles give 1.1 seconds before eruption");
        player.teleport(player.getLocation().add(3,0,0));riverTicks(2);check(prisons().isEmpty(),"leaving the committed spike circle avoids capture");
        riverTicks(11);check(get(state,"move")==null&&(long)get(state,"recoveryUntil")==now+20,"missed prison ends after twelve ticks instead of idling for 5.5 seconds");
        freshCocytus(pad.clone().add(0,0,14));player.setGravity(true);begin("FISSURE");riverTicks(22);
        check(prisons().size()==1&&Math.abs(player.getY()-pad.getY()-2.85)<.01&&!player.hasGravity(),"failed dodge lifts and holds player at the ice tip");
        Location at=player.getLocation();PlayerMoveEvent move=new PlayerMoveEvent(player,at,at.clone().add(1,1,0));move.getTo().setPitch(90);Bukkit.getPluginManager().callEvent(move);
        check(move.getTo().distanceSquared(at)<1e-8&&move.getTo().getPitch()==90,"prison locks walking and jumping while allowing aim");
        PlayerTeleportEvent dash=new PlayerTeleportEvent(player,at,at.clone().add(2,0,0),PlayerTeleportEvent.TeleportCause.PLUGIN);Bukkit.getPluginManager().callEvent(dash);
        check(dash.isCancelled()&&prisons().size()==1,"short plugin teleport cannot bypass the prison");
        Object prison=prisons().values().iterator().next();Mob receiver=(Mob)get(prison,"receiver");aimedDown();
        for(int i=0;i<3;i++){receiver.damage(1,player);if(i<2)riverTicks(8);}
        check(prisons().isEmpty()&&player.hasGravity()&&!receiver.isValid(),"three downward punches break ice and restore gravity before execution");
        riverTicks(100);check(player.getHealth()==200,"breaking ice cancels all three execution arrows");

        freshCocytus(pad.clone().add(0,0,14));begin("FISSURE");riverTicks(22);
        PlayerTeleportEvent denied=new PlayerTeleportEvent(player,player.getLocation(),pad.clone().add(8,0,14),PlayerTeleportEvent.TeleportCause.ENDER_PEARL);denied.setCancelled(true);Bukkit.getPluginManager().callEvent(denied);
        check(prisons().size()==1,"cancelled pearl teleport does not release the prison");
        check(player.teleport(pad.clone().add(8,0,14),PlayerTeleportEvent.TeleportCause.ENDER_PEARL)&&prisons().isEmpty(),"successful ender pearl escapes the prison");riverTicks(100);
        check(player.getHealth()==200,"pearl escape cancels the pending execution");

        freshCocytus(pad.clone().add(0,0,14));player.getAttribute(Attribute.MAX_HEALTH).setBaseValue(400);player.setHealth(400);begin("FISSURE");riverTicks(22);riverTicks(49);
        check(player.getHealth()==400&&shots().isEmpty(),"captured player has 2.5 seconds to escape before the first arrow launches");
        riverTicks(1);check(get(unit,"pose").equals("attack_active"),"execution visibly releases from the attack animation at the first arrow launch");
        riverTicks(43);check(player.getHealth()==160,"unescaped player takes three separate 80-damage travelling arrows");
        riverTicks(8);check(prisons().isEmpty()&&player.getVelocity().getY()>.5,"execution ends by ejecting player from the ice");

        freshCocytus(pad.clone().add(6,0,14));begin("VOLLEY");riverTicks(27);
        check(shots().isEmpty()&&player.getHealth()==200,"upward shot waits for the authored draw and release");riverTicks(4);
        check(player.getHealth()==200&&shots().size()==1,"only the harmless upward arrow exists before the 1.6-second rain warning ends");riverTicks(89);
        check(player.getHealth()==110&&get(state,"move")==null,"two faster rain rounds each hit a stationary player once for 45 within six seconds");check(shots().isEmpty(),"rain projectiles clean up after both rounds");
        freshCocytus(pad.clone().add(6,0,14));begin("VOLLEY");
        for(int i=0;i<60;i++){if(i>=6&&i<34)player.teleport(player.getLocation().add(.215,0,0));riverTicks(1);}
        check(player.getHealth()==200,"ordinary walking after six reaction ticks escapes the five-block rain circle before impact");

        freshCocytus(pad.clone().add(6,120,14));begin("VOLLEY");Object highPlan=rainPlans().getFirst();double highSky=(double)get(highPlan,"skyY");
        check(Math.abs(highSky-player.getY()-12)<1e-8,"rain commits a launch sky twelve blocks above a player far beyond the old height ceiling");
        riverTicks(31);check(player.getHealth()==200,"high rain preserves the complete thirty-two-tick warning");riverTicks(1);
        int falling=0;boolean committedFlight=true;for(Object fallingShot:shots())if((double)get(fallingShot,"damage")==45){falling++;
            committedFlight&=Math.abs(((Location)get(fallingShot,"at")).getY()-highSky)<1e-8&&(long)get(fallingShot,"until")-now>=66&&((Vector)get(fallingShot,"step")).getY()==-2;}
        check(falling>0&&committedFlight,"high rain launches faster swept arrows at its committed sky with enough lifetime to reach the floor");riverTicks(88);
        check(player.getHealth()==110&&get(state,"move").toString().equals("VOLLEY")&&!shots().isEmpty(),"two high rain rounds damage a stationary aerial target while remaining arrows keep flying beyond tick119");
        riverTicks(70);check(shots().isEmpty()&&get(state,"move")==null&&((Map<?,?>)get(frost(),"rainMarks")).isEmpty(),"tall rain waits for every flight then clears its marks without launching a third round");

        freshCocytus(pad.clone().add(6,40,14));Location highCommit=player.getLocation();begin("VOLLEY");Object dodgePlan=rainPlans().getFirst();double dodgeSky=(double)get(dodgePlan,"skyY");
        player.teleport(highCommit.clone().add(6,4,0));riverTicks(60);
        Location committedLane=(Location)((List<?>)get(dodgePlan,"lanes")).getFirst();
        check((double)get(dodgePlan,"skyY")==dodgeSky&&committedLane.getX()==highCommit.getX()&&committedLane.getZ()==highCommit.getZ()&&player.getHealth()==200,"moving out of an aerial warning dodges fixed rain lanes without retargeting height or position");
        invoke(controller,"clear",new Class<?>[]{});
        check(shots().isEmpty()&&((Map<?,?>)get(frost(),"rainMarks")).isEmpty()&&((Map<?,?>)get(get(controller,"sigils"),"planes")).keySet().stream().noneMatch(k->k.toString().startsWith("cocytus:")),"reset removes long-lived aerial arrows and both ground and sky warning displays");
        freshCocytus(pad.clone().add(0,0,14));player.setGravity(true);begin("FISSURE");riverTicks(22);check(prisons().size()==1,"cleanup begins with an active prison");
        invoke(controller,"clear",new Class<?>[]{});check(prisons().isEmpty()&&shots().isEmpty()&&player.hasGravity(),"encounter reset releases prisoners, restores gravity and removes every arrow");
        check(((Set<?>)get(frost(),"owned")).isEmpty(),"reset leaves no owned Cocytus entities");
        for(var kind:List.of(ArkhaiaWaves.Kind.ACHERON,ArkhaiaWaves.Kind.PHLEGETHON,ArkhaiaWaves.Kind.BANSHEE_WAIL)){
            summon(kind);check(mob.getAttribute(Attribute.MAX_HEALTH).getValue()==(kind==ArkhaiaWaves.Kind.PHLEGETHON?1000:kind==ArkhaiaWaves.Kind.BANSHEE_WAIL?500:800),kind+" retains its accepted maximum HP");}
        freshTrial(ArkhaiaWaves.Kind.STYX);resetPlayer(pad.clone().add(0,0,6));put(state,"nextSiphon",Long.MAX_VALUE);put(state,"nextBerserk",Long.MAX_VALUE);
        begin("COMBO");riverTicks(19);check(player.getHealth()==200,"shared controller preserves Styx's full combo warning");riverTicks(10);
        check(player.getHealth()==168,"shared controller preserves Styx's 32-damage first sweep");
        mob.damage(20,player);check((double)get(unit,"health")==600,"Styx retains its melee immunity");
        projectile(world.spawn(player.getEyeLocation(),Arrow.class),new ItemStack(Material.BOW));
        check((double)get(unit,"health")<600,"Cocytus arrow immunity never applies to Styx");
        freshCocytus(pad.clone().add(0,0,14));
        var magic=((com.example.advancemagic.AdvanceMagicPlugin)Bukkit.getPluginManager().getPlugin("advance-magic"));int points=magic.skillTree().points(player);
        magic.context().damage(player,mob,10000,DamageType.MAGIC);
        check(!mob.isValid()&&magic.skillTree().points(player)==points+1,"valid Cocytus magic defeat still grants exactly one Sanctuary skill point");
        check(world.getNearbyEntities(pad,3,3,3).stream().anyMatch(entity->entity instanceof Item item&&"cocytus".equals(item.getItemStack().getItemMeta().getPersistentDataContainer().get(garden.key("boss_orb"),org.bukkit.persistence.PersistentDataType.STRING))),"Cocytus final defeat still drops its actual soul orb");
    }
}
