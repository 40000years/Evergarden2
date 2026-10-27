import com.example.voidscape.boss.ArkhaiaWaves;
import org.bukkit.*;
import org.bukkit.entity.*;
import java.util.*;

/** Production AI clocks, accepted damage, dodge geometry, rewards and cleanup. */
public class RiverTrialsProbe extends AcheronSplitProbe {
    Object trials,state;
    void freshTrial(ArkhaiaWaves.Kind kind)throws Exception{
        summon(kind);trials=get(controller,"trials");state=call(trials,"state",new Class<?>[]{unit.getClass(),long.class},unit,now);
        put(state,"next",Long.MAX_VALUE);put(unit,"stunUntil",0L);
    }
    @SuppressWarnings({"unchecked","rawtypes"}) void begin(String move)throws Exception{
        Class<?> kind=Arrays.stream(trials.getClass().getDeclaredClasses()).filter(c->c.getSimpleName().equals("Move")).findFirst().orElseThrow();
        Object value=Enum.valueOf((Class)kind,move);call(trials,"begin",new Class<?>[]{state.getClass(),kind,Player.class,long.class},state,value,player,now);
    }
    double incoming(double amount)throws Exception{return (double)call(trials,"incoming",new Class<?>[]{unit.getClass(),double.class,long.class},unit,amount,now);}
    List<?> children(String field)throws Exception{return (List<?>)get(state,field);}
    void holdNext()throws Exception{put(state,"next",Long.MAX_VALUE);}
    @Override void mechanics()throws Exception{
        world.setDifficulty(Difficulty.NORMAL);controller=minions();invoke(controller,"demo",new Class<?>[]{UUID.class,boolean.class},player.getUniqueId(),false);
        pad=new Location(world,site.x()+.5,109,site.z()+.5);now=(long)get(controller,"clock");
        for(int x=-36;x<=36;x++)for(int z=-36;z<=36;z++){
            world.getBlockAt(pad.getBlockX()+x,108,pad.getBlockZ()+z).setType(Material.STONE,false);
            for(int y=109;y<=122;y++)world.getBlockAt(pad.getBlockX()+x,y,pad.getBlockZ()+z).setType(Material.AIR,false);
        }
        // Cocytus storm, prisons and rain are exercised by --cocytus / CocytusProbe.
        freshTrial(ArkhaiaWaves.Kind.COCYTUS);for(int i=0;i<3;i++)call(trials,"chill",new Class<?>[]{Player.class,long.class},player,now);
        check(player.hasPotionEffect(org.bukkit.potion.PotionEffectType.SLOWNESS),"three chills cause a short slowdown");
        Object cold=((Map<?,?>)get(trials,"cold")).get(player.getUniqueId());for(int i=0;i<4;i++)call(trials,"chill",new Class<?>[]{Player.class,long.class},player,now+1);
        check(((Map<?,?>)get(trials,"cold")).get(player.getUniqueId()).equals(cold),"cold break grants anti-chain immunity");

        // Malefeasance dimensions, immunity and rebirth are covered by --malefeasance.

        freshTrial(ArkhaiaWaves.Kind.STYX);resetPlayer(pad.clone().add(0,0,6));begin("COMBO");riverTicks(19);check(player.getHealth()==200,"Styx first swing has a full readable warning");riverTicks(10);
        check(player.getHealth()==168,"Styx first sweeping arc hits once for 32");player.teleport(pad.clone().add(0,0,-8));riverTicks(8);
        check(player.getHealth()==168,"walking behind a committed swing avoids follow-up contact");
        freshTrial(ArkhaiaWaves.Kind.STYX);resetPlayer(pad.clone().add(0,0,10));begin("EXECUTION");riverTicks(43);check(player.getHealth()==200,"execution respects forty-four-tick warning");player.teleport(pad.clone().add(5,0,10));riverTicks(1);
        check(player.getHealth()==200,"execution remains harmless outside its committed line");
        riverTicks(12);check(incoming(20)==30,"Styx heavy recovery rewards counterattacks");incoming(20);incoming(20);
        check((boolean)get(state,"staggered")&&(long)get(unit,"stunUntil")==now+60,"three accepted recovery hits break Styx stance for three seconds");
        freshTrial(ArkhaiaWaves.Kind.STYX);resetPlayer(pad.clone().add(0,0,20));begin("DASH");Location start=mob.getLocation();riverTicks(27);
        check(mob.getLocation().distanceSquared(start)<1e-8,"dash stays at origin until committed warning ends");
        for(int x=-3;x<=3;x++)for(int y=109;y<120;y++)world.getBlockAt(pad.getBlockX()+x,y,pad.getBlockZ()+5).setType(Material.STONE,false);
        riverTicks(15);check((long)get(unit,"stunUntil")>now&&mob.getZ()<pad.getZ()+5,"solid wall stops dash and creates punishable crash");
        for(int x=-3;x<=3;x++)for(int y=109;y<120;y++)world.getBlockAt(pad.getBlockX()+x,y,pad.getBlockZ()+5).setType(Material.AIR,false);
        freshTrial(ArkhaiaWaves.Kind.STYX);resetPlayer(pad.clone().add(0,0,10));begin("EXECUTION");player.teleport(pad.clone().add(5,0,10));riverTicks(45);
        check(player.getHealth()==200,"sideways dodge escapes the committed execution line");
        call(controller,"clear",new Class<?>[]{});check(((Map<?,?>)get(trials,"states")).isEmpty()&&((Map<?,?>)get(trials,"cold")).isEmpty(),"cleanup removes every owned trial state and cold counter");
        check(player.getAttribute(org.bukkit.attribute.Attribute.ARMOR).getModifiers().stream().noneMatch(m->m.getKey().getKey().startsWith("arkhaia_sunder")),"cleanup restores temporary armor penalties");
    }
}
