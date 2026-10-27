import com.example.voidscape.boss.*;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.damage.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.*;
import org.bukkit.util.Vector;
import java.util.*;

/** Real Paper events + actual moving projectiles + production animation clocks. */
public class BossContactProbe extends ArkhaiaProbe {
    Object controller,unit;Mob mob;Location pad;long now;
    void riverTicks(int count)throws Exception{
        for(int i=0;i<count;i++){
            player.setNoDamageTicks(0);
            invoke(controller,"tick",new Class<?>[]{long.class},++now);
        }
    }
    void resetPlayer(Location at){
        player.setGameMode(GameMode.SURVIVAL);player.setInvulnerable(false);actor.getAbilities().invulnerable=false;
        player.getAttribute(Attribute.MAX_HEALTH).setBaseValue(200);player.setHealth(200);player.setAbsorptionAmount(0);player.setNoDamageTicks(0);
        player.getActivePotionEffects().forEach(e->player.removePotionEffect(e.getType()));player.getInventory().clear();player.teleport(at);player.setVelocity(new Vector());
    }
    void summon(ArkhaiaWaves.Kind kind)throws Exception{
        invoke(controller,"clear",new Class<?>[]{});invoke(controller,"summon",new Class<?>[]{ArkhaiaWaves.Kind.class,Location.class},kind,pad.clone());
        unit=((Map<?,?>)get(controller,"spirits")).values().iterator().next();mob=(Mob)get(unit,"mob");mob.setGravity(false);mob.setAI(false);mob.setVelocity(new Vector());mob.setRotation(0,0);
        // This suite isolates individual contact helpers; the full new AI has its own suite.
        if(kind==ArkhaiaWaves.Kind.COCYTUS||kind==ArkhaiaWaves.Kind.MALEFEASANCE||kind==ArkhaiaWaves.Kind.STYX){
            Object trials=get(controller,"trials");var state=trials.getClass().getDeclaredMethod("state",unit.getClass(),long.class);state.setAccessible(true);
            Object trial=state.invoke(trials,unit,now);put(trial,"next",Long.MAX_VALUE);
        }
        put(unit,"castAt",Long.MAX_VALUE);put(unit,"attackAt",Long.MAX_VALUE);resetPlayer(pad.clone().add(0,0,3));
    }
    void impact()throws Exception{invoke(controller,"release",new Class<?>[]{unit.getClass()},unit);}
    void arrowTicks(int n)throws Exception{
        for(int i=0;i<n;i++){
            var shots=new ArrayList<>(((Map<?,?>)get(controller,"frostArrows")).values());
            for(Object shot:shots){var method=shot.getClass().getDeclaredMethod("arrow");method.setAccessible(true);Arrow a=(Arrow)method.invoke(shot);
                if(a.isValid())((org.bukkit.craftbukkit.entity.CraftArrow)a).getHandle().tick();}
            riverTicks(1);
        }
    }
    void face(Location aim){Location at=player.getLocation();at.setDirection(aim.toVector().subtract(player.getEyeLocation().toVector()));player.teleport(at);}
    void exposeBansheeBody()throws Exception{
        var state=get(unit,"soul");var souls=get(controller,"souls");
        for(Object anchor:new ArrayList<>((List<?>)get(state,"anchors")))invoke(souls,"remove",new Class<?>[]{anchor.getClass()},anchor);
        ((List<?>)get(state,"anchors")).clear();put(state,"exposedUntil",now+1000);
    }
    @Override void mechanics()throws Exception{
        world.setDifficulty(Difficulty.NORMAL);Bukkit.getPluginManager().registerEvents(this,this);
        resetPlayer(target().getLocation().add(0,0,1));
        player.damage(10,target());check(player.getHealth()==200,"main slime native contact is cancelled outside an authored impact");
        force(ArkhaiaBrain.Action.LIGHT);resetPlayer(target().getLocation().add(0,0,5));release();
        check(player.getHealth()==200,"standing in old six-block cone beyond the visible blade no longer hits");
        player.setInvulnerable(true);controller=minions();invoke(controller,"demo",new Class<?>[]{UUID.class,boolean.class},player.getUniqueId(),false);
        pad=new Location(world,site.x()+20.5,109,site.z()+.5);now=(long)get(controller,"clock");
        for(int x=-11;x<=11;x++)for(int z=-12;z<=14;z++){
            world.getBlockAt(pad.getBlockX()+x,108,pad.getBlockZ()+z).setType(Material.STONE,false);
            for(int y=109;y<=122;y++)world.getBlockAt(pad.getBlockX()+x,y,pad.getBlockZ()+z).setType(Material.AIR,false);
        }
        for(var kind:ArkhaiaWaves.Kind.values()){
            summon(kind);resetPlayer(pad.clone().add(0,0,1));
            player.damage(10,mob);check(player.getHealth()==200,"no native walking/melee damage "+kind);
            put(unit,"mark",player.getLocation().clone());put(unit,"castDuration",40L);put(unit,"releaseAt",now+40);put(unit,"castStarted",now);
            riverTicks(20);check(player.getHealth()==200,"windup is harmless "+kind);
            put(unit,"releaseAt",0L);
            // Isolate posed-body melee from Banshee's separately tested soul shield.
            if(kind==ArkhaiaWaves.Kind.BANSHEE_WAIL)exposeBansheeBody();
            // A sword can hit the visible lower leg and cannot damage from afar or while facing away.
            resetPlayer(pad.clone().add(kind==ArkhaiaWaves.Kind.BANSHEE_WAIL?.95:.7,0,2.5));face(pad.clone().add(kind==ArkhaiaWaves.Kind.BANSHEE_WAIL?.95:.7,1,kind==ArkhaiaWaves.Kind.BANSHEE_WAIL?.55:0));double hp=(double)get(unit,"health");
            mob.damage(10,player);check((double)get(unit,"health")<hp,"sword reaches visible leg "+kind);
            riverTicks(11);resetPlayer(pad.clone().add(0,0,10));face(pad.clone().add(.7,1,0));hp=(double)get(unit,"health");mob.damage(10,player);
            check((double)get(unit,"health")==hp,"sword cannot hit distant hidden receiver "+kind);
            resetPlayer(pad.clone().add(0,0,2.5));face(pad.clone().add(0,1,8));mob.damage(10,player);
            check((double)get(unit,"health")==hp,"facing away cannot hit model "+kind);
        }
        summon(ArkhaiaWaves.Kind.ACHERON);resetPlayer(pad.clone().add(0,0,5));put(unit,"mark",player.getLocation().clone());impact();
        check(player.getHealth()==200,"spin release does not hit before moving vortex contact");riverTicks(24);
        check(player.getHealth()==164,"moving spin crosses player and hits once per cast despite disabled i-frames");
        resetPlayer(pad.clone().add(-8,0,-8));riverTicks(120);check(player.getHealth()==200,"escaping Acheron has no lingering bleed or recovery hits");
        for(var kind:List.of(ArkhaiaWaves.Kind.PHLEGETHON,ArkhaiaWaves.Kind.STYX)){
            summon(kind);Location mark=pad.clone().add(kind==ArkhaiaWaves.Kind.STYX?.8:0,0,kind==ArkhaiaWaves.Kind.STYX?8.4:3.2);put(unit,"mark",mark);
            resetPlayer(mark.clone().add(kind==ArkhaiaWaves.Kind.PHLEGETHON?12:5,0,0));impact();check(player.getHealth()==200,"outside marked impact is safe "+kind);
            resetPlayer(mark);impact();check(player.getHealth()<200,"ground slam contact damages inside its marked circle "+kind);
            double remaining=player.getHealth();resetPlayer(mark.clone().add(-12,0,0));player.setHealth(remaining);riverTicks(120);
            check(player.getHealth()==remaining,"no repeated or attached damage after escaping "+kind);
            if(kind==ArkhaiaWaves.Kind.STYX)check(!((Map<?,?>)get(controller,"sundered")).containsKey(player.getUniqueId()),"temporary sunder expires");
        }
        summon(ArkhaiaWaves.Kind.BANSHEE_WAIL);put(unit,"mark",pad.clone());resetPlayer(pad.clone().add(0,0,4));impact();
        check(player.getHealth()==200,"scream starts its animation before radial pulse");riverTicks(1);check(player.getHealth()==195,"visible scream circle pulses once at contact key");
        resetPlayer(pad.clone().add(0,0,10));double hp=(double)get(unit,"health");
        Arrow incoming=world.spawnArrow(player.getEyeLocation(),new Vector(0,0,1),1,0);incoming.setShooter(player);
        mob.damage(20,DamageSource.builder(DamageType.ARROW).withCausingEntity(player).withDirectEntity(incoming).build());incoming.remove();
        check((double)get(unit,"health")==hp&&player.getHealth()==200,"shrouded Banshee blocks arrow without invisible remote reflection");
        summon(ArkhaiaWaves.Kind.MALEFEASANCE);resetPlayer(pad.clone().add(0,0,6));put(unit,"aim",player.getEyeLocation().clone());
        invoke(controller,"attack",new Class<?>[]{unit.getClass(),Player.class},unit,player);check(player.getHealth()==200,"shadow bolt is not instant damage on cast");
        resetPlayer(pad.clone().add(5,0,6));riverTicks(20);check(player.getHealth()==200,"sidestep dodges fixed shadow projectile");
        resetPlayer(pad.clone().add(0,0,6));put(unit,"aim",player.getEyeLocation().clone());invoke(controller,"attack",new Class<?>[]{unit.getClass(),Player.class},unit,player);riverTicks(17);
        check(player.getHealth()==178,"visible shadow bolt deals one upgraded contact hit");
        summon(ArkhaiaWaves.Kind.BANSHEE_WAIL);resetPlayer(pad.clone().add(0,0,6));put(unit,"aim",player.getEyeLocation().clone());
        invoke(controller,"attack",new Class<?>[]{unit.getClass(),Player.class},unit,player);resetPlayer(pad.clone().add(5,0,6));riverTicks(18);
        check(player.getHealth()==200,"sidestep dodges visible sonic wave");
        resetPlayer(pad.clone().add(0,0,6));put(unit,"aim",player.getEyeLocation().clone());invoke(controller,"attack",new Class<?>[]{unit.getClass(),Player.class},unit,player);riverTicks(18);
        check(player.getHealth()==195,"sonic wave crosses player and hits once despite disabled i-frames");
        summon(ArkhaiaWaves.Kind.MALEFEASANCE);resetPlayer(pad.clone().add(0,0,6));put(unit,"aim",player.getEyeLocation().clone());
        for(int x=-2;x<=2;x++)for(int y=0;y<10;y++)world.getBlockAt(pad.getBlockX()+x,109+y,pad.getBlockZ()+3).setType(Material.STONE,false);
        invoke(controller,"attack",new Class<?>[]{unit.getClass(),Player.class},unit,player);riverTicks(20);
        check(player.getHealth()==200,"solid wall blocks swept shadow projectile");
        for(int x=-2;x<=2;x++)for(int y=0;y<10;y++)world.getBlockAt(pad.getBlockX()+x,109+y,pad.getBlockZ()+3).setType(Material.AIR,false);
        invoke(controller,"castImpact",new Class<?>[]{unit.getClass()},unit);List<?> shadows=(List<?>)get(controller,"shades");
        check(shadows.size()==2,"Malefeasance retains two summoned shades");((Mob)get(shadows.get(1),"mob")).remove();Mob shade=(Mob)get(shadows.get(0),"mob");
        resetPlayer(shade.getLocation().add(0,0,1.3));player.damage(10,shade);check(player.getHealth()==200,"shade native proximity hit is cancelled");
        riverTicks(10);check(player.getHealth()==200,"shade charging windup is harmless");resetPlayer(pad.clone().add(0,0,10));riverTicks(50);
        check(player.getHealth()==200,"leaving shade swing before contact avoids damage");
        resetPlayer(shade.getLocation().add(0,0,1.3));riverTicks(16);check(player.getHealth()==197,"shade deals one contact hit after telegraph");
        summon(ArkhaiaWaves.Kind.COCYTUS);resetPlayer(pad.clone().add(0,0,12));Object frostTrials=get(controller,"trials");
        var method=frostTrials.getClass().getDeclaredMethod("state",unit.getClass(),long.class);method.setAccessible(true);Object frostState=method.invoke(frostTrials,unit,now);put(frostState,"next",now+1);
        riverTicks(1);check(((Map<?,?>)get(controller,"frostArrows")).isEmpty(),"new frost volley waits for drawn bow release key");riverTicks(21);
        check(((Map<?,?>)get(controller,"frostArrows")).isEmpty()&&player.getHealth()==200,"no damage or arrow before release key");riverTicks(1);
        check(((Map<?,?>)get(controller,"frostArrows")).size()==1&&player.getHealth()==200,"bow launches actual frost projectile at contact key");arrowTicks(16);
        check(player.getHealth()==182&&((Map<?,?>)get(frostTrials,"cold")).containsKey(player.getUniqueId()),"new frost arrow deals eighteen damage and chills on actual impact");
        resetPlayer(pad.clone().add(-8,0,0));riverTicks(90);check(player.getHealth()==200,"frost recovery cannot continue hitting escaped player");
        summon(ArkhaiaWaves.Kind.COCYTUS);resetPlayer(pad.clone().add(0,0,12));put(unit,"aim",player.getEyeLocation().clone());invoke(controller,"attack",new Class<?>[]{unit.getClass(),Player.class},unit,player);
        resetPlayer(pad.clone().add(5,0,12));arrowTicks(20);check(player.getHealth()==200,"sidestep dodges Cocytus actual basic arrow");
        summon(ArkhaiaWaves.Kind.COCYTUS);resetPlayer(pad.clone().add(0,0,8.8));put(unit,"mark",player.getLocation().clone());
        for(int x=-3;x<=3;x++)for(int y=0;y<10;y++)world.getBlockAt(pad.getBlockX()+x,109+y,pad.getBlockZ()+8).setType(Material.STONE,false);
        put(unit,"aim",player.getEyeLocation().clone());invoke(controller,"attack",new Class<?>[]{unit.getClass(),Player.class},unit,player);arrowTicks(20);
        check(player.getHealth()==200&&((Map<?,?>)get(controller,"frostArrows")).isEmpty(),"wall consumes frost arrow and shields player inside burst radius behind wall");
        for(int x=-3;x<=3;x++)for(int y=0;y<10;y++)world.getBlockAt(pad.getBlockX()+x,109+y,pad.getBlockZ()+8).setType(Material.AIR,false);
        invoke(controller,"clear",new Class<?>[]{});check(((Map<?,?>)get(controller,"frostArrows")).isEmpty()&&((List<?>)get(controller,"bolts")).isEmpty(),"cleanup removes pending projectiles and all delayed damage");
    }
    @Override void completed(){Bukkit.getScheduler().runTaskLater(this,this::fallback,1);}
    void fallback(){
        try{
            summon(ArkhaiaWaves.Kind.BANSHEE_WAIL);resetPlayer(pad.clone().add(.95,0,2.5));face(pad.clone().add(.95,1,.55));
            exposeBansheeBody();
            player.getInventory().setItemInMainHand(new ItemStack(Material.DIAMOND_SWORD));
            Bukkit.getPluginManager().callEvent(new org.bukkit.event.player.PlayerAnimationEvent(player,org.bukkit.event.player.PlayerAnimationType.ARM_SWING));
            Bukkit.getScheduler().runTaskLater(this,()->{try{
                double hp=(double)get(unit,"health");check(hp<500&&hp>490,"actual air-swing fallback calls native sword attack on revealed robe hp="+hp);
                riverTicks(11);face(pad.clone().add(0,1,8));
                Bukkit.getPluginManager().callEvent(new org.bukkit.event.player.PlayerAnimationEvent(player,org.bukkit.event.player.PlayerAnimationType.ARM_SWING));
                Bukkit.getScheduler().runTaskLater(this,()->{try{
                    check((double)get(unit,"health")==hp,"air swing facing away cannot cause fallback damage");super.completed();
                }catch(Throwable error){finish(error);}},2);
            }catch(Throwable error){finish(error);}},2);
        }catch(Throwable error){finish(error);}
    }

}
