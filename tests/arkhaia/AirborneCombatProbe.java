import com.example.voidscape.world.SanctuaryArena;
import com.example.voidscape.world.OverworldSanctuaries.Kind;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.*;
import org.bukkit.util.Vector;
import java.util.*;

/** Actual sanctuary controllers: wind-charge height, committed casts and dodgeable aerial contact. */
public class AirborneCombatProbe extends SanctuaryCombatProbe {
    long time()throws Exception{return (long)get(manager,"tick");}
    void healthy(Location at){
        player.setInvulnerable(false);actor.getAbilities().invulnerable=false;player.setNoDamageTicks(0);
        player.getAttribute(Attribute.MAX_HEALTH).setBaseValue(200);player.setHealth(200);player.setAbsorptionAmount(0);
        player.getActivePotionEffects().forEach(e->player.removePotionEffect(e.getType()));player.getInventory().clear();
        player.teleport(at);player.setVelocity(new Vector());
    }
    void quiet(Object r)throws Exception{
        Object u=unit(r),host=controller(r);put(u,"castAt",Long.MAX_VALUE);put(u,"attackAt",Long.MAX_VALUE);put(u,"snareAt",Long.MAX_VALUE);
        put(u,"stunUntil",0L);put(u,"releaseAt",0L);put(u,"castActiveUntil",0L);put(u,"attackStarted",-1L);put(u,"skillContactAt",0L);
        Object trials=get(host,"trials");Object state=call(trials,"state",new Class<?>[]{u.getClass(),long.class},u,time());
        put(state,"next",((SanctuaryArena)get(r,"arena")).site().kind()==Kind.STYX?time()+1:Long.MAX_VALUE);
        put(state,"nextSiphon",Long.MAX_VALUE);put(state,"nextBerserk",Long.MAX_VALUE);
        Mob m=mob(r);m.setGravity(false);m.setAI(false);m.setVelocity(new Vector());
    }
    Object fresh(Kind kind)throws Exception{
        Object r=run(kind);call(manager,"reset",new Class<?>[]{r.getClass()},r);
        Location c=arena(kind).center();healthy(c.clone().add(0,0,8));shoot(r,10);
        check((boolean)get(r,"fighting"),"ground attack starts aerial fixture "+kind);quiet(r);return r;
    }
    Map<?,?> counters(Object r)throws Exception{return (Map<?,?>)get(get(controller(r),"aerial"),"states");}
    Object counter(Object r)throws Exception{return counters(r).get(unit(r));}
    Object warn(Kind kind)throws Exception{
        Object r=fresh(kind);healthy(arena(kind).center().add(0,14,0));arenaTicks(1);
        check(counter(r)!=null,"airborne player receives committed aerial warning "+kind);return r;
    }
    void until(long tick)throws Exception{arenaTicks((int)Math.max(0,tick-time()));}
    @Override void inspect()throws Exception{
        for(Kind kind:Kind.values()){
            SanctuaryArena a=arena(kind);Location c=a.center();call(manager,"discover",new Class<?>[]{Chunk.class},c.getChunk());
            check(a.contains(c.clone().add(0,60,0))&&a.contains(c.clone().add(34,60,0)),"active membership retains high player through exact radius "+kind);
            check(!a.contains(c.clone().add(34.01,60,0))&&!a.contains(c.clone().add(0,-.251,0)),"lateral exit and underground remain outside "+kind);
            check(a.containsActivation(c.clone().add(0,kind==Kind.COCYTUS?12:2.2,0))&&!a.containsActivation(c.clone().add(0,kind==Kind.COCYTUS?12.01:2.21,0)),"dormant activation keeps authored height boundary "+kind);
            check(!a.contains(new Location(world,c.getX(),Double.POSITIVE_INFINITY,c.getZ()))&&!a.contains(new Location(world,Double.NaN,c.getY(),c.getZ())),"non-finite positions are rejected "+kind);
        }
        createActor();
        for(Kind kind:Kind.values()){
            Object r=run(kind);Location c=arena(kind).center();Mob dormant=mob(r);double hp=health(r);
            healthy(c.clone().add(0,kind==Kind.COCYTUS?13:4,8));shoot(r,30);
            check(!(boolean)get(r,"fighting")&&mob(r)==dormant&&health(r)==hp,"elevated first shot cannot wake dormant boss "+kind);
            healthy(c.clone().add(0,0,8));shoot(r,10);check((boolean)get(r,"fighting"),"valid ground attack awakens "+kind);
            Object active=unit(r);quiet(r);put(active,"stunUntil",time()+1000);player.setInvulnerable(true);actor.getAbilities().invulnerable=true;
            player.teleport(c.clone().add(0,60,8));arenaTicks(321);
            check((boolean)get(r,"fighting")&&unit(r)==active&&(long)get(r,"emptySince")<0,"high wind-charge loop never starts empty reset or replaces boss "+kind);
            check(((BossBar)get(active,"bar")).getPlayers().contains(player),"airborne player remains in boss combat audience "+kind);
            player.teleport(c.clone().add(35,0,0));arenaTicks(299);check((boolean)get(r,"fighting"),"lateral exit retains full return grace "+kind);arenaTicks(2);
            check(!(boolean)get(r,"fighting")&&unit(r)!=active&&health(r)==hp,"real lateral exit still resets after fifteen seconds "+kind);
        }
        committedGroundCast();
        for(Kind kind:List.of(Kind.ACHERON,Kind.STYX))aerialContact(kind);
        rangedAirborne();
        garden.sanctuaryEncounters().close();check(runs().isEmpty(),"airborne suite cleanup removes every sanctuary encounter");
    }
    void committedGroundCast()throws Exception{
        Object r=fresh(Kind.ACHERON),u=unit(r);Location c=arena(Kind.ACHERON).center();healthy(c.clone().add(0,0,5));
        put(u,"castAt",time()+1);arenaTicks(1);long release=(long)get(u,"releaseAt");
        check(release>time(),"Acheron starts actual ground spin windup");player.teleport(c.clone().add(0,10,5));arenaTicks(1);
        check((long)get(u,"releaseAt")==release,"wind charge cannot cancel an already committed ground windup");until(release);
        check((long)get(u,"spinUntil")>time()&&(long)get(u,"castActiveUntil")>time(),"ground cast releases on schedule while player is airborne");arenaTicks(24);
        check(player.getHealth()==200,"jumping still avoids the actual ground spin contact volume");
        check(counters(r).isEmpty(),"aerial counter does not overlap a committed ground cast");
        call(manager,"reset",new Class<?>[]{r.getClass()},r);
    }
    void aerialContact(Kind kind)throws Exception{
        Object r=warn(kind),state=counter(r);Location aim=((Location)get(state,"aim")).clone();long fire=(long)get(state,"fireAt");
        check(fire-time()==28,"aerial response grants full twenty-eight-tick warning "+kind);until(fire-1);
        check(player.getHealth()==200,"aerial warning deals no early damage "+kind);arenaTicks(1);
        double expected=kind==Kind.ACHERON?182:184;
        check(player.getHealth()==expected&&(boolean)get(state,"fired"),"committed three-dimensional ray hits hovering target once "+kind);arenaTicks(7);
        check(player.getHealth()==expected,"aerial recovery cannot repeatedly hit same player "+kind);
        until(fire+8);check(counter(r)==null,"aerial warning state expires after owned recovery "+kind);
        until(fire-28+99);check(counter(r)==null,"aerial response respects one-hundred-tick cooldown "+kind);
        if(kind==Kind.ACHERON){arenaTicks(1);check(counter(r)!=null,"remaining airborne permits another warning after cooldown "+kind);}

        r=warn(kind);state=counter(r);fire=(long)get(state,"fireAt");aim=((Location)get(state,"aim")).clone();player.teleport(player.getLocation().add(5,0,0));until(fire);
        check(((Location)get(state,"aim")).distanceSquared(aim)<1e-8&&player.getHealth()==200,"sideways dodge escapes fixed warning without retargeting "+kind);

        r=warn(kind);state=counter(r);fire=(long)get(state,"fireAt");player.teleport(arena(kind).center());until(fire);
        check(player.getHealth()==200,"landing beneath high warning ray avoids vertical-column damage "+kind);

        r=warn(kind);state=counter(r);fire=(long)get(state,"fireAt");Location from=((Location)get(state,"from")).clone(),to=((Location)get(state,"aim")).clone();
        Location midpoint=from.clone().add(to.toVector().subtract(from.toVector()).multiply(.55));var block=midpoint.getBlock();var old=block.getBlockData();block.setType(Material.STONE,false);
        try{until(fire);check(player.getHealth()==200,"solid cover clips aerial ray before player contact "+kind);}finally{block.setBlockData(old,false);}

        r=fresh(kind);Object u=unit(r);put(u,"stunUntil",time()+60);healthy(arena(kind).center().add(0,14,0));arenaTicks(30);
        check(counter(r)==null&&player.getHealth()==200,"stun prevents aerial retaliation "+kind);
        r=fresh(kind);healthy(arena(kind).center().add(0,2.9,0));arenaTicks(2);check(counter(r)==null,"ordinary low jump does not trigger aerial counter "+kind);
        r=warn(kind);Object previous=get(controller(r),"aerial");call(manager,"reset",new Class<?>[]{r.getClass()},r);
        check(((Map<?,?>)get(previous,"states")).isEmpty()&&((Map<?,?>)get(previous,"next")).isEmpty(),"reset clears pending aerial contact and cooldown ownership "+kind);
    }
    void rangedAirborne()throws Exception{
        Object r=fresh(Kind.BANSHEE),u=unit(r);Location c=arena(Kind.BANSHEE).center();healthy(c.clone().add(0,18,8));put(u,"blinkNext",Long.MAX_VALUE);put(u,"castAt",time()-1);put(u,"attackAt",time()+1);arenaTicks(1);
        long started=(long)get(u,"attackStarted");check(started==time()&&((Location)get(u,"aim")).getY()==player.getEyeLocation().getY(),"Banshee acquires high player with actual basic windup and eye-level aim");until(started+20);
        check(player.getHealth()==200&&((List<?>)get(controller(r),"bolts")).isEmpty(),"high sonic wave retains its full contact windup");arenaTicks(1);
        check(((List<?>)get(controller(r),"bolts")).size()==3&&player.getHealth()==200,"high sonic wave launches travelling three-dimensional projectiles");arenaTicks(30);
        check(player.getHealth()==195,"Banshee sonic wave reaches airborne target once");

        r=fresh(Kind.PHLEGETHON);u=unit(r);c=arena(Kind.PHLEGETHON).center();healthy(c.clone().add(0,18,8));put(u,"castAt",time()+1);arenaTicks(1);
        check(get(u,"furnace").toString().equals("ROCK")&&(long)get(u,"releaseAt")>time(),"Phlegethon selects actual rock windup against elevated player");
        check(Math.abs(((Location)get(u,"mark")).getY()-player.getY())<1e-8,"Phlegethon records airborne height without projecting rock mark onto floor");
        long release=(long)get(u,"releaseAt");until(release+3);check(((List<?>)get(controller(r),"boulders")).isEmpty()&&player.getHealth()==200,"high rock remains harmless in hands before release contact");arenaTicks(1);
        check(((List<?>)get(controller(r),"boulders")).size()==1,"Phlegethon launches one visible rock toward high target");arenaTicks(22);
        check(player.getHealth()==152&&((List<?>)get(controller(r),"boulders")).isEmpty(),"Phlegethon travelling rock hits high player once for unchanged direct damage");
    }
}
