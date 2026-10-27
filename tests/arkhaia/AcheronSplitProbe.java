import com.example.voidscape.boss.ArkhaiaWaves;
import org.bukkit.*;
import org.bukkit.attribute.*;
import org.bukkit.damage.*;
import org.bukkit.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.util.Vector;
import java.util.*;

/** Production events, AI clocks, HP thresholds, child ownership and root cleanup. */
public class AcheronSplitProbe extends BossContactProbe {
    List<?> echoes()throws Exception{return (List<?>)get(controller,"echoes");}
    Object call(Object o,String name,Class<?>[] types,Object... args)throws Exception{var m=o.getClass().getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(o,args);}
    void wound(Mob victim,double amount)throws Exception{
        Arrow arrow=world.spawnArrow(player.getEyeLocation(),new Vector(0,0,1),1,0);arrow.setShooter(player);
        victim.damage(amount,DamageSource.builder(DamageType.INDIRECT_MAGIC).withCausingEntity(player).withDirectEntity(arrow).build());arrow.remove();
    }
    void fresh()throws Exception{summon(ArkhaiaWaves.Kind.ACHERON);put(unit,"snareAt",Long.MAX_VALUE);}
    void silenceEchoes()throws Exception{for(Object echo:echoes())put(echo,"castAt",Long.MAX_VALUE);}
    void snare()throws Exception{
        put(unit,"releaseAt",0L);put(unit,"castActiveUntil",0L);put(unit,"skillContactAt",0L);put(unit,"snareAt",0L);
        riverTicks(1);riverTicks(37);
    }
    Map<?,?> roots()throws Exception{return (Map<?,?>)get(controller,"roots");}
    void moveBlocked()throws Exception{
        Location from=player.getLocation(),to=from.clone().add(1,.5,1);to.setYaw(77);to.setPitch(31);
        PlayerMoveEvent move=new PlayerMoveEvent(player,from,to);Bukkit.getPluginManager().callEvent(move);
        check(move.getTo().distanceSquared(from)<1e-8&&move.getTo().getYaw()==77&&move.getTo().getPitch()==31,"root blocks walking and jumping while allowing camera look");
    }
    @Override void mechanics()throws Exception{
        world.setDifficulty(Difficulty.NORMAL);controller=minions();invoke(controller,"demo",new Class<?>[]{UUID.class,boolean.class},player.getUniqueId(),false);
        pad=new Location(world,site.x()+18.5,109,site.z()+.5);now=(long)get(controller,"clock");
        for(int x=-12;x<=12;x++)for(int z=-12;z<=15;z++){
            world.getBlockAt(pad.getBlockX()+x,108,pad.getBlockZ()+z).setType(Material.STONE,false);
            for(int y=109;y<=122;y++)world.getBlockAt(pad.getBlockX()+x,y,pad.getBlockZ()+z).setType(Material.AIR,false);
        }
        fresh();wound(mob,199);check((double)get(unit,"health")==601&&!((boolean)get(unit,"pendingSplit")),"above 75 percent cannot summon echoes");
        riverTicks(11);wound(mob,1);check((double)get(unit,"health")==600&&(boolean)get(unit,"pendingSplit"),"crossing exactly 75 percent queues one split");
        riverTicks(1);check(get(unit,"pose").equals("ritual_windup")&&echoes().isEmpty(),"split has its own harmless ritual windup");
        riverTicks(28);check(echoes().isEmpty(),"no children appear before ritual contact key");riverTicks(1);
        check(echoes().size()==2,"split contact summons exactly two echoes");silenceEchoes();
        for(Object echo:echoes()){
            Mob em=(Mob)get(echo,"mob");
            check((double)get(echo,"health")==200&&em.getAttribute(Attribute.MAX_HEALTH).getValue()==200,"echo has exact 200 virtual and native maximum HP");
            check(Math.abs(em.getAttribute(Attribute.SCALE).getValue()-2.4)<1e-6,"echo receiver is 60 percent of main scale");
            check((boolean)call(controller,"owns",new Class<?>[]{Entity.class},em),"echo belongs to combat damage and cleanup controller");
            wound(em,50);check((double)get(echo,"health")==150&&!(boolean)get(echo,"pendingSplit"),"damaging an echo cannot recursively summon");
        }
        Object echo=echoes().get(0);Mob echoMob=(Mob)get(echo,"mob");resetPlayer(echoMob.getLocation().add(0,0,2));
        player.damage(25,echoMob);check(player.getHealth()==200,"walking into an echo causes no native damage");
        player.getInventory().setArmorContents(new org.bukkit.inventory.ItemStack[]{new org.bukkit.inventory.ItemStack(Material.DIAMOND_BOOTS),new org.bukkit.inventory.ItemStack(Material.DIAMOND_LEGGINGS),new org.bukkit.inventory.ItemStack(Material.DIAMOND_CHESTPLATE),new org.bukkit.inventory.ItemStack(Material.DIAMOND_HELMET)});
        put(echo,"mark",player.getLocation().clone());call(controller,"release",new Class<?>[]{echo.getClass()},echo);riverTicks(24);
        check(player.getHealth()==140,"echo moving spin deals one powerful 60-damage hit");
        check(Arrays.stream(player.getInventory().getArmorContents()).allMatch(i->((org.bukkit.inventory.meta.Damageable)i.getItemMeta()).getDamage()==15),"powerful echo spin wears all four armor pieces once");
        check(player.hasPotionEffect(org.bukkit.potion.PotionEffectType.SLOWNESS)&&player.getPotionEffect(org.bukkit.potion.PotionEffectType.SLOWNESS).getAmplifier()==1,"echo spin applies Slow II");
        Map<?,?> planes=(Map<?,?>)get(get(controller,"sigils"),"planes");
        check(planes.keySet().stream().noneMatch(k->k.toString().startsWith("identity:ACHERON:")),"echo never receives an overhead sigil");
        for(Object child:new ArrayList<>(echoes()))put(child,"castAt",0L);riverTicks(1);
        check(echoes().stream().allMatch(child->{try{return get(child,"skill").toString().equals("SPIN");}catch(Exception e){throw new RuntimeException(e);}}),"echo AI selects only spin, never snare or split");silenceEchoes();
        wound(echoMob,1000);check(echoes().size()==1,"one surviving echo keeps the previous pair active");
        wound(mob,200);check((double)get(unit,"health")==400&&!(boolean)get(unit,"pendingSplit"),"50 percent split is skipped while one echo survives");
        Object survivor=echoes().get(0);wound((Mob)get(survivor,"mob"),1000);riverTicks(65);
        check(echoes().isEmpty(),"skipped 50 percent threshold is never queued after echoes die");
        wound(mob,200);check((double)get(unit,"health")==200&&(boolean)get(unit,"pendingSplit"),"25 percent can summon a new pair when previous pair is gone");riverTicks(30);
        check(echoes().size()==2,"25 percent produces two echoes with cap still enforced");
        List<Mob> children=new ArrayList<>();for(Object child:echoes())children.add((Mob)get(child,"mob"));wound(mob,1000);
        check(echoes().isEmpty()&&children.stream().noneMatch(Entity::isValid),"main death removes all owned echoes immediately");
        fresh();wound(mob,650);check((int)get(unit,"splitThresholds")==7,"one large hit consumes all crossed thresholds");riverTicks(30);check(echoes().size()==2,"large multi-threshold hit cannot summon more than two echoes");silenceEchoes();
        for(Object child:new ArrayList<>(echoes()))wound((Mob)get(child,"mob"),1000);riverTicks(20);wound(mob,10);riverTicks(40);
        check(echoes().isEmpty(),"already consumed thresholds cannot retrigger below 25 percent");

        fresh();resetPlayer(pad.clone().add(0,0,4));put(unit,"snareAt",0L);riverTicks(1);
        List<?> marks=(List<?>)get(unit,"snareMarks");Location marked=((Location)marks.get(0)).clone();
        check(marks.size()==1&&marked.distanceSquared(player.getLocation())<1e-8,"wide snare is marked under player feet at cast commit");
        check((double)call(controller,"radius",new Class<?>[]{unit.getClass()},unit)==6,"snare AoE radius is six blocks");
        resetPlayer(marked.clone().add(7,0,0));riverTicks(37);check(roots().isEmpty(),"running out of fixed warning circle avoids root");
        check((long)get(unit,"snareAt")>now+200,"snare has a long independent cooldown");
        resetPlayer(pad.clone().add(0,0,4));snare();check(roots().size()==1&&player.getHealth()==200,"failed dodge roots once without invisible extra damage");
        check(player.getAttribute(Attribute.MOVEMENT_SPEED).getValue()==0&&player.getAttribute(Attribute.JUMP_STRENGTH).getValue()==0,"root prevents client movement and jumping through owned modifiers");moveBlocked();
        Object binding=roots().values().iterator().next();long expiry=(long)call(binding,"until",new Class<?>[]{});
        @SuppressWarnings("unchecked") List<Location> duplicate=(List<Location>)get(unit,"snareMarks");duplicate.add(player.getLocation());duplicate.add(player.getLocation());
        call(controller,"castImpact",new Class<?>[]{unit.getClass()},unit);
        check(roots().size()==1&&(long)call(roots().values().iterator().next(),"until",new Class<?>[]{})==expiry,"overlapping circles cannot stack or extend root duration");
        riverTicks(58);check(roots().size()==1,"root persists for three full seconds");riverTicks(2);
        check(roots().isEmpty()&&player.getAttribute(Attribute.MOVEMENT_SPEED).getValue()>0&&player.getAttribute(Attribute.JUMP_STRENGTH).getValue()>0,"root expires after 60 ticks and restores movement");
        snare();check(roots().isEmpty(),"recent victim cannot be chain-rooted by another cast");
        put(unit,"snareAt",Long.MAX_VALUE);riverTicks(300);resetPlayer(pad.clone().add(0,0,4));snare();check(roots().size()==1,"root becomes available after anti-repeat immunity expires");
        PlayerTeleportEvent teleport=new PlayerTeleportEvent(player,player.getLocation(),player.getLocation().add(0,0,1),PlayerTeleportEvent.TeleportCause.PLUGIN);Bukkit.getPluginManager().callEvent(teleport);
        check(teleport.isCancelled()&&roots().size()==1,"short plugin teleport cannot silently release root");
        PlayerVelocityEvent impulse=new PlayerVelocityEvent(player,new Vector(1,1,1));Bukkit.getPluginManager().callEvent(impulse);
        check(impulse.getVelocity().lengthSquared()==0,"root stops knockback impulse as well as walking");
        teleport=new PlayerTeleportEvent(player,player.getLocation(),player.getLocation().add(0,0,1),PlayerTeleportEvent.TeleportCause.COMMAND);Bukkit.getPluginManager().callEvent(teleport);
        check(!teleport.isCancelled()&&roots().isEmpty()&&player.getAttribute(Attribute.MOVEMENT_SPEED).getValue()>0,"explicit command teleport releases root modifiers");
        @SuppressWarnings("unchecked") Map<UUID,Long> immunity=(Map<UUID,Long>)get(controller,"rootImmune");immunity.clear();snare();check(roots().size()==1,"cleanup fixture is rooted");
        wound(mob,1000);check(roots().isEmpty()&&player.getAttribute(Attribute.MOVEMENT_SPEED).getValue()>0,"main death releases its bound players");
        fresh();snare();check(roots().size()==1,"encounter-clear fixture is rooted");call(controller,"clear",new Class<?>[]{});
        check(roots().isEmpty()&&echoes().isEmpty()&&player.getAttribute(Attribute.MOVEMENT_SPEED).getValue()>0,"encounter clear removes children and restores all root movement");
    }
    @Override void completed(){garden.worldBoss().close();check(parts().isEmpty(),"shutdown cleans encounter");finish(null);}
}
