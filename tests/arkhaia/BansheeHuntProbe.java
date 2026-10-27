import org.bukkit.*;
import org.bukkit.entity.*;
import java.util.*;

/** Long-range pressure, shared volleys, committed curse marks and safe ghost blinks. */
public class BansheeHuntProbe extends BansheeProbe {
    List<?> bolts()throws Exception{return (List<?>)get(controller,"bolts");}
    void shot(Location aim)throws Exception{put(unit,"aim",aim);call(controller,"attack",new Class<?>[]{unit.getClass(),Player.class},unit,player);}
    boolean blink()throws Exception{return (boolean)call(controller,"beginBlink",new Class<?>[]{unit.getClass(),Player.class},unit,player);}
    void freshHunt(Location at)throws Exception{ghost();put(unit,"blinkNext",Long.MAX_VALUE);resetPlayer(at);}
    @Override void mechanics()throws Exception{
        world.setDifficulty(Difficulty.NORMAL);controller=minions();invoke(controller,"demo",new Class<?>[]{UUID.class,boolean.class},player.getUniqueId(),false);
        pad=new Location(world,site.x()+.5,109,site.z()+.5);now=(long)get(controller,"clock");
        for(int x=-25;x<=25;x++)for(int z=-25;z<=32;z++){
            world.getBlockAt(pad.getBlockX()+x,108,pad.getBlockZ()+z).setType(Material.STONE,false);
            for(int y=109;y<=122;y++)world.getBlockAt(pad.getBlockX()+x,y,pad.getBlockZ()+z).setType(Material.AIR,false);
        }
        freshHunt(pad.clone().add(0,0,24));shot(player.getEyeLocation());
        check(bolts().size()==3,"soul attack emits a three-lane ranged fan");
        Object victims=get(bolts().get(0),"victims");check(bolts().stream().allMatch(b->{try{return get(b,"victims")==victims;}catch(Exception e){throw new RuntimeException(e);}}),"all three lanes share one victim set");
        riverTicks(10);check(player.getHealth()==200,"distant wave has visible travel time");riverTicks(18);
        check(player.getHealth()==195,"soul fan reaches twenty-four blocks with one five-HP contact");riverTicks(7);
        check(player.getHealth()==195&&bolts().isEmpty(),"overlapping lanes never stack damage and expire");
        freshHunt(pad.clone().add(3.7,0,22));shot(pad.clone().add(0,player.getEyeHeight(),22));
        Object outer=bolts().get(0);Location outerContact=((Location)get(outer,"at")).clone().add(((org.bukkit.util.Vector)get(outer,"step")).clone().normalize().multiply(22));
        player.teleport(outerContact.subtract(0,player.getEyeHeight(),0));riverTicks(28);
        check(player.getHealth()==195,"outer fan lane pressures lateral targets beyond the center ray");
        freshHunt(pad.clone().add(0,0,22));
        for(int x=-8;x<=8;x++)for(int y=109;y<=121;y++)world.getBlockAt(pad.getBlockX()+x,y,pad.getBlockZ()+10).setType(Material.STONE,false);
        shot(player.getEyeLocation());riverTicks(35);check(player.getHealth()==200&&bolts().isEmpty(),"solid cover blocks every lane of the long-range fan");
        for(int x=-8;x<=8;x++)for(int y=109;y<=121;y++)world.getBlockAt(pad.getBlockX()+x,y,pad.getBlockZ()+10).setType(Material.AIR,false);
        freshHunt(pad.clone().add(0,0,18));put(unit,"attackAt",0L);riverTicks(1);
        check((long)get(unit,"attackStarted")>=0,"AI commits ranged soul attack at eighteen blocks");put(unit,"attackAt",Long.MAX_VALUE);
        for(int i=0;i<50;i++){player.teleport(player.getLocation().add(0,0,.12));riverTicks(1);}
        check(player.getHealth()==195,"straight backwards retreat remains within the travelling wave's reach");

        freshHunt(pad.clone().add(0,0,22));put(unit,"castAt",0L);riverTicks(1);
        check(((Location)get(unit,"mark")).distanceSquared(player.getLocation())<1e-8&&((Location)get(unit,"mark")).distanceSquared(mob.getLocation())>20*20,"far curse marks player ground rather than caster feet");
        for(int i=0;i<7;i++){player.teleport(player.getLocation().add(.2,0,0));riverTicks(1);}
        Location committed=((Location)get(unit,"mark")).clone();
        for(int i=0;i<26;i++){player.teleport(player.getLocation().add(.35,0,0));riverTicks(1);}
        check(((Location)get(unit,"mark")).distanceSquared(committed)<1e-8,"curse stops tracking after its first eight ticks");
        check(player.getHealth()==200&&!player.hasPotionEffect(org.bukkit.potion.PotionEffectType.HUNGER),"committed curse circle can be escaped before contact");
        freshHunt(pad.clone().add(0,0,22));put(unit,"castAt",0L);riverTicks(34);
        check(player.getHealth()==195&&player.hasPotionEffect(org.bukkit.potion.PotionEffectType.HUNGER),"stationary distant archer receives soul curse and healing seal");
        freshHunt(pad.clone().add(0,0,22));put(unit,"castAt",0L);riverTicks(5);
        var warningPlanes=(Map<?,?>)get(get(controller,"sigils"),"planes");check(warningPlanes.keySet().stream().anyMatch(k->k.toString().startsWith("river:")),"pending targeted curse has a visible warning");
        for(Object anchor:new ArrayList<>(anchors()))wound((Mob)get(anchor,"receiver"),100);riverTicks(1);
        check((long)get(unit,"releaseAt")==0&&warningPlanes.keySet().stream().noneMatch(k->k.toString().startsWith("river:")),"breaking veil interrupts curse and clears its abandoned warning");riverTicks(40);
        check(player.getHealth()==200&&!player.hasPotionEffect(org.bukkit.potion.PotionEffectType.HUNGER),"interrupted curse cannot inflict delayed invisible damage");

        freshHunt(pad.clone().add(0,0,22));put(unit,"blinkNext",0L);Location origin=mob.getLocation();
        check(blink(),"Banshee commits ghost pursuit against a distant target");Location landing=((Location)get(unit,"blinkMark")).clone();
        check((long)get(unit,"blinkAt")==now+12&&landing.distance(origin)<=20.001,"blink has twelve-tick warning and bounded travel");
        var planes=(Map<?,?>)get(get(controller,"sigils"),"planes");check(planes.keySet().stream().anyMatch(k->k.toString().startsWith("banshee-blink:")),"landing is visibly telegraphed before teleport");riverTicks(11);
        check(mob.getLocation().distanceSquared(origin)<1e-8&&player.getHealth()==200,"blink does not move or hit early");riverTicks(1);
        check(mob.getLocation().distanceSquared(landing)<1e-8&&mob.getLocation().distance(player.getLocation())<7,"blink closes the gap to the committed flank landing");
        check(player.getHealth()==200&&(long)get(unit,"attackAt")>=now+10,"blink arrival deals no damage and preserves reaction grace");
        check(planes.keySet().stream().noneMatch(k->k.toString().startsWith("banshee-blink:")),"arrival removes its warning plane");
        resetPlayer(pad.clone());check(!blink(),"blink cooldown prevents repeated instant pursuit");
        exposeBansheeBody();wound(mob,20);check(ghostHealth()==480,"pursuit retains vulnerable true-form arrow damage");
        freshHunt(pad.clone().add(0,0,22));put(unit,"blinkNext",0L);origin=mob.getLocation();check(blink(),"blocked-landing fixture starts with safe destination");landing=((Location)get(unit,"blinkMark")).clone();
        var obstruction=landing.clone().add(0,4,0).getBlock();obstruction.setType(Material.STONE,false);riverTicks(12);
        check(mob.getLocation().distanceSquared(origin)<1e-8&&get(unit,"blinkMark")==null,"new obstruction cancels blink without teleporting into blocks");obstruction.setType(Material.AIR,false);
        freshHunt(pad.clone().add(0,0,22));put(unit,"blinkNext",0L);check(blink(),"cleanup blink fixture starts");call(controller,"clear",new Class<?>[]{});
        check(get(unit,"blinkMark")==null&&((Map<?,?>)get(get(controller,"sigils"),"planes")).isEmpty(),"encounter cleanup cancels pending pursuit and warning");
    }
}
