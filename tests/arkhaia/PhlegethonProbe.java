import com.example.voidscape.boss.ArkhaiaWaves;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.damage.*;
import org.bukkit.entity.*;
import org.bukkit.event.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import java.util.*;

/** Real Paper adaptation, authored contact keys, swept rocks and encounter cleanup. */
public class PhlegethonProbe extends AcheronSplitProbe {
    void fire()throws Exception{summon(ArkhaiaWaves.Kind.PHLEGETHON);}
    void skill(String name)throws Exception{
        var field=unit.getClass().getDeclaredField("furnace");field.setAccessible(true);
        @SuppressWarnings({"rawtypes","unchecked"}) Object value=Enum.valueOf((Class)field.getType(),name);field.set(unit,value);
    }
    double furnaceHp()throws Exception{return (double)get(unit,"health");}
    double shot(Material weapon,double amount)throws Exception{
        player.setNoDamageTicks(0);riverTicks(11);double before=furnaceHp();
        Arrow arrow=world.spawnArrow(player.getEyeLocation(),new Vector(0,0,1),1,0);arrow.setShooter(player);arrow.setWeapon(new ItemStack(weapon));
        mob.damage(amount,DamageSource.builder(DamageType.INDIRECT_MAGIC).withCausingEntity(player).withDirectEntity(arrow).build());arrow.remove();return before-furnaceHp();
    }
    double sword(Material weapon)throws Exception{
        riverTicks(11);resetPlayer(pad.clone().add(.7,0,2.5));face(pad.clone().add(.7,1,0));player.getInventory().setItemInMainHand(new ItemStack(weapon));
        double before=furnaceHp();mob.damage(10,player);return before-furnaceHp();
    }
    List<?> rocks()throws Exception{return (List<?>)get(controller,"boulders");}
    void arm(String name,Location mark)throws Exception{skill(name);put(unit,"mark",mark.clone());impact();}
    @Override void mechanics()throws Exception{
        world.setDifficulty(Difficulty.NORMAL);controller=minions();invoke(controller,"demo",new Class<?>[]{UUID.class,boolean.class},player.getUniqueId(),false);
        pad=new Location(world,site.x()+10.5,109,site.z()+.5);now=(long)get(controller,"clock");
        for(int x=-16;x<=16;x++)for(int z=-16;z<=30;z++){
            world.getBlockAt(pad.getBlockX()+x,108,pad.getBlockZ()+z).setType(Material.STONE,false);
            for(int y=109;y<=122;y++)world.getBlockAt(pad.getBlockX()+x,y,pad.getBlockZ()+z).setType(Material.AIR,false);
        }
        fire();check(furnaceHp()==1000&&mob.getAttribute(Attribute.MAX_HEALTH).getValue()==1000,"Phlegethon has 1000 virtual and native HP");
        check(((org.bukkit.boss.BossBar)get(unit,"bar")).getTitle().contains("1000"),"initial health bar shows 1000 HP");
        resetPlayer(pad.clone().add(0,0,4));
        check(Math.abs(shot(Material.BOW,10)-10)<1e-6,"first bow hit deals full damage");
        check(Math.abs(shot(Material.BOW,10)-5)<1e-6,"second bow hit deals half damage");
        check(Math.abs(shot(Material.BOW,10)-2.5)<1e-6,"third bow hit deals quarter damage");
        player.getInventory().setItemInMainHand(new ItemStack(Material.DIAMOND_SWORD));
        check(Math.abs(shot(Material.BOW,10)-1.25)<1e-6,"arrow retains launched bow identity after shooter switches held item");
        check(Math.abs(shot(Material.CROSSBOW,10)-10)<1e-6,"crossbow switches adaptation and receives full first hit");
        check(Math.abs(shot(Material.BOW,10)-10)<1e-6,"returning to bow after another weapon resets its damage");
        fire();double first=sword(Material.DIAMOND_SWORD),second=sword(Material.NETHERITE_SWORD);
        check(first>0&&Math.abs(second-first*.5)<1e-6,"switching sword materials preserves same sword adaptation family");
        double axe=sword(Material.DIAMOND_AXE);check(axe>0&&Math.abs(axe-first)<1e-6,"axe resets sword adaptation");
        check(Math.abs(sword(Material.DIAMOND_SWORD)-first)<1e-6,"returning to sword after axe restores full damage");
        double unchanged=furnaceHp();int chain=(int)get(unit,"adaptedHits");mob.damage(10,player);
        check(furnaceHp()==unchanged&&(int)get(unit,"adaptedHits")==chain,"rejected rapid hit neither damages nor advances adaptation");
        riverTicks(11);resetPlayer(pad.clone().add(0,0,20));mob.damage(10,player);
        check(furnaceHp()==unchanged&&(int)get(unit,"adaptedHits")==chain,"missed distant melee cannot change adaptation");

        fire();resetPlayer(pad.clone().add(0,0,3.2));Location mark=player.getLocation();
        put(unit,"mark",mark);put(unit,"castStarted",now);put(unit,"castDuration",44L);put(unit,"releaseAt",now+44);
        riverTicks(43);check(player.getHealth()==200,"slam windup does not deal contact damage");riverTicks(1);
        check(player.getHealth()==140,"slam deals 60 damage at authored ground contact");
        check(player.hasPotionEffect(org.bukkit.potion.PotionEffectType.NAUSEA),"slam contact applies six seconds of nausea");riverTicks(20);
        check(player.getHealth()==140,"slam has no lingering damage after contact");
        fire();resetPlayer(pad.clone().add(0,0,15));arm("SLAM",pad.clone().add(0,0,3.2));check(player.getHealth()==200,"escaping marked slam avoids damage");
        for(double[] band:new double[][]{{4.5,44},{8,28},{11,0}}){
            fire();Location origin=pad.clone().add(0,0,3.2);resetPlayer(origin.clone().add(band[0],0,0));arm("SLAM",origin);
            check(player.getHealth()==200,"outer ground is harmless before visible shockwave arrives at "+band[0]);riverTicks(13);
            check(player.getHealth()==200-band[1],"graded expanding slam damage at radius "+band[0]+" is "+band[1]);
            check((get(unit,"slamOrigin")==null),"expanding wave cleans up after passing");
        }
        fire();resetPlayer(pad.clone().add(0,0,3.2));put(unit,"castAt",0L);riverTicks(5);
        Map<?,?> planes=(Map<?,?>)get(get(controller,"sigils"),"planes");
        check(planes.keySet().stream().filter(k->k.toString().startsWith("furnace:")).count()==3,"slam telegraph displays all three concentric damage zones");
        call(controller,"clear",new Class<?>[]{});check(planes.isEmpty(),"warning rings clean up when encounter ends");
        fire();Location waveCenter=pad.clone().add(0,0,3.2);resetPlayer(waveCenter.clone().add(12,0,0));arm("SLAM",waveCenter);riverTicks(6);
        resetPlayer(waveCenter);riverTicks(7);check(player.getHealth()==200,"entering behind a passed wave does not cause invisible residual damage");
        fire();resetPlayer(waveCenter.clone().add(0,0,6));
        for(int x=-10;x<=10;x++)for(int y=109;y<=112;y++)world.getBlockAt(pad.getBlockX()+x,y,pad.getBlockZ()+6).setType(Material.STONE,false);
        arm("SLAM",waveCenter);riverTicks(13);check(player.getHealth()==200,"solid cover blocks ground shockwave damage");
        for(int x=-10;x<=10;x++)for(int y=109;y<=112;y++)world.getBlockAt(pad.getBlockX()+x,y,pad.getBlockZ()+6).setType(Material.AIR,false);
        fire();resetPlayer(waveCenter);
        var armor=new ItemStack[]{new ItemStack(Material.DIAMOND_BOOTS),new ItemStack(Material.DIAMOND_LEGGINGS),new ItemStack(Material.DIAMOND_CHESTPLATE),new ItemStack(Material.DIAMOND_HELMET)};
        for(var item:armor)item.addEnchantment(org.bukkit.enchantments.Enchantment.PROTECTION,4);player.getInventory().setArmorContents(armor);
        arm("SLAM",waveCenter);check(Math.abs(player.getHealth()-178.4)<.001,"center slam deals 21.6 actual damage through full diamond Protection IV");
        check(Arrays.stream(player.getInventory().getArmorContents()).allMatch(i->((org.bukkit.inventory.meta.Damageable)i.getItemMeta()).getDamage()==15),"center shockwave wears every Protection IV armor piece once");
        check(player.getPotionEffect(org.bukkit.potion.PotionEffectType.NAUSEA).getDuration()==120,"nausea lasts exactly six seconds after landed slam");
        call(controller,"clear",new Class<?>[]{});check(get(unit,"slamOrigin")==null&&planes.isEmpty(),"encounter cancellation clears running wave and all effect planes");

        fire();skill("KICK");var bones=(Map<?,?>)call(controller,"furnaceBones",new Class<?>[]{unit.getClass(),String.class,double.class,Set.class},unit,"kick_active",.2,Set.of("foot_r"));
        Location foot=(Location)bones.get("foot_r");resetPlayer(new Location(world,foot.getX(),109,foot.getZ()));
        arm("KICK",player.getLocation());check(player.getHealth()==200,"kick release is harmless before leg extension");riverTicks(3);
        check(player.getHealth()==200,"kick does not hit before active 0.2-second contact key");riverTicks(1);
        check(player.getHealth()==160,"extended leg deals exactly one 40-damage kick");
        check(Math.abs(Math.hypot(player.getVelocity().getX(),player.getVelocity().getZ())-2)<1e-6&&player.getVelocity().getY()>=.54,"kick applies horizontal knockback two with strong lift");riverTicks(20);check(player.getHealth()==160,"kick cannot repeatedly damage during recovery");
        fire();resetPlayer(pad.clone().add(0,0,-3));arm("KICK",player.getLocation());riverTicks(5);check(player.getHealth()==200,"standing behind Phlegethon avoids forward kick");

        fire();resetPlayer(pad.clone().add(0,0,18));Location target=player.getLocation();arm("ROCK",target);
        check(rocks().isEmpty()&&player.getHealth()==200,"rock does not spawn or damage at windup release");riverTicks(3);check(rocks().isEmpty(),"rock remains in hands until release key");riverTicks(1);
        check(rocks().size()==1&&player.getHealth()==200,"release creates one visible travelling rock without remote instant damage");
        BlockDisplay display=(BlockDisplay)get(rocks().get(0),"display");check(display.isValid()&&display.getBlock().getMaterial()==Material.DEEPSLATE,"rock has a real visible deepslate display");
        riverTicks(12);check(player.getHealth()==152&&rocks().isEmpty()&&!display.isValid(),"swept rock contact deals 48 damage once and removes projectile");
        riverTicks(20);check(player.getHealth()==152,"rock has no attached or lingering damage");
        fire();resetPlayer(pad.clone().add(0,0,18));arm("ROCK",player.getLocation());riverTicks(4);resetPlayer(pad.clone().add(8,0,18));riverTicks(60);
        check(player.getHealth()==200&&rocks().isEmpty(),"sidestepping after release dodges fixed rock trajectory");
        fire();resetPlayer(pad.clone().add(0,0,18));arm("ROCK",player.getLocation());riverTicks(4);
        resetPlayer(pad.clone().add(2.5,0,18));riverTicks(30);
        check(player.getHealth()==168&&rocks().isEmpty(),"small sidestep avoids direct rock but receives one 32-damage impact blast");
        riverTicks(20);check(player.getHealth()==168,"impact blast cannot repeatedly damage the same player");
        fire();resetPlayer(pad.clone().add(0,0,18));put(unit,"castAt",0L);riverTicks(1);
        Location committed=null;
        for(int i=0;i<28;i++){
            player.teleport(player.getLocation().add(.25,0,0));riverTicks(1);
            if(i==20)committed=((Location)get(unit,"mark")).clone();
            if(i>=21)check(((Location)get(unit,"mark")).distanceSquared(committed)<1e-8,"aim stays committed during final dodge window "+i);
        }
        check(rocks().isEmpty(),"tracked throw still waits for authored release key");
        for(int i=0;i<4;i++){player.teleport(player.getLocation().add(.25,0,0));riverTicks(1);}
        check(((Location)get(unit,"mark")).distanceSquared(committed)<1e-8,"active throwing frames cannot reacquire moving target");
        check(rocks().size()==1,"production aimed throw spawns one projectile");
        Object aimed=rocks().get(0);var velocity=(Vector)get(aimed,"velocity");
        var handMap=(Map<?,?>)call(controller,"furnaceBones",new Class<?>[]{unit.getClass(),String.class,double.class,Set.class},unit,"throw_active",.2,Set.of("hand_r","hand_l"));
        Location hand=((Location)handMap.get("hand_r")).clone().add((Location)handMap.get("hand_l")).multiply(.5);
        double targetSlope=(player.getX()-hand.getX())/(player.getZ()-hand.getZ());
        check(Math.hypot(velocity.getX(),velocity.getZ())>=1.45&&velocity.getX()/velocity.getZ()<targetSlope-.03,"boulder retains speed but reduced lead allows moving player to outrun committed aim");
        double vx=velocity.getX();
        for(int i=0;i<16;i++){player.teleport(player.getLocation().add(.25,0,0));riverTicks(1);}
        check(velocity.getX()==vx,"released boulder retains its path and never homes invisibly");
        check(player.getHealth()==200&&rocks().isEmpty(),"steady lateral movement through aim lock can dodge both boulder and impact blast");
        fire();resetPlayer(pad.clone().add(0,0,18));arm("ROCK",player.getLocation());riverTicks(4);
        for(int x=-5;x<=5;x++)for(int y=109;y<122;y++)world.getBlockAt(pad.getBlockX()+x,y,pad.getBlockZ()+9).setType(Material.STONE,false);
        riverTicks(40);check(player.getHealth()==200&&rocks().isEmpty(),"solid wall intercepts rock before player");
        for(int x=-5;x<=5;x++)for(int y=109;y<122;y++)world.getBlockAt(pad.getBlockX()+x,y,pad.getBlockZ()+9).setType(Material.AIR,false);
        String[] skills={"SLAM","KICK","ROCK"},poses={"cast_windup","kick_windup","throw_windup"};
        for(int turn=0;turn<3;turn++){
            fire();resetPlayer(pad.clone().add(0,0,3));put(unit,"furnaceTurns",turn);put(unit,"castAt",0L);riverTicks(1);
            check(get(unit,"furnace").toString().equals(skills[turn])&&get(unit,"pose").equals(poses[turn]),"close-range skill rotation selects "+skills[turn]+" with matching animation");
        }
        fire();resetPlayer(pad.clone().add(0,0,18));put(unit,"castAt",0L);riverTicks(1);
        check(get(unit,"furnace").toString().equals("ROCK")&&get(unit,"pose").equals("throw_windup"),"far target selects dedicated throwing animation");riverTicks(10);
        BlockDisplay held=(BlockDisplay)get(unit,"heldRock");check(held!=null&&held.isValid()&&rocks().isEmpty(),"boulder is visible in hands during windup");
        call(controller,"clear",new Class<?>[]{});check(!held.isValid(),"clearing encounter removes held boulder");
        fire();resetPlayer(pad.clone().add(0,0,18));arm("ROCK",player.getLocation());riverTicks(4);display=(BlockDisplay)get(rocks().get(0),"display");
        call(controller,"clear",new Class<?>[]{});check(rocks().isEmpty()&&!display.isValid(),"clearing encounter removes flying boulders");
        fire();check(get(unit,"adaptedWeapon").equals("")&&(int)get(unit,"adaptedHits")==0,"new encounter starts without old adaptation");
        call(controller,"clear",new Class<?>[]{});
    }
}
