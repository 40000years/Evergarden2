import com.example.voidscape.boss.ArkhaiaWaves;
import com.example.voidscape.boss.ArkhaiaBrain;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import java.lang.reflect.Method;
import java.util.*;

/** Real Paper regression for body geometry, scale, transformed rigs and deferred air swings. */
@SuppressWarnings("unchecked")
public class BossHitboxProbe extends BossContactProbe {
    boolean sanctuaryRound;
    Object arkhaia;
    @Override void run()throws Exception{
        SanctuaryProbeFixture.open(this,com.example.voidscape.world.OverworldSanctuaries.Kind.BANSHEE);
        for(int x=(pad.getBlockX()-40)>>4;x<=(pad.getBlockX()+40)>>4;x++)
            for(int z=(pad.getBlockZ()-40)>>4;z<=(pad.getBlockZ()+40)>>4;z++)world.addPluginChunkTicket(x,z,this);
        mechanics();completed();
    }
    @Override Object minions(){return controller;}
    Object call(Object object,String name,Class<?>[] types,Object... args)throws Exception{
        Method method=object.getClass().getDeclaredMethod(name,types);method.setAccessible(true);return method.invoke(object,args);
    }
    Object staticCall(String owner,String name,Class<?>[] types,Object... args)throws Exception{
        Method method=Class.forName("com.example.voidscape.boss."+owner).getDeclaredMethod(name,types);method.setAccessible(true);return method.invoke(null,args);
    }
    double health()throws Exception{return (double)get(unit,"health");}
    void clearHits()throws Exception{((Map<?,?>)get(unit,"hits")).clear();}
    String bodyModel()throws Exception{return (String)call(controller,"model",new Class<?>[]{unit.getClass()},unit);}
    double bodyTime()throws Exception{return (double)call(controller,"poseTime",new Class<?>[]{unit.getClass()},unit);}
    double bodyScale()throws Exception{return (double)staticCall("RiverVisual","scale",new Class<?>[]{String.class},bodyModel());}
    double bodyRay()throws Exception{return (double)staticCall("BossHitbox","ray",new Class<?>[]{Player.class,String.class,String.class,double.class,Location.class,double.class,double.class},
        player,bodyModel(),get(unit,"pose"),bodyTime(),mob.getLocation(),bodyScale(),player.getAttribute(Attribute.ENTITY_INTERACTION_RANGE).getValue());}
    void render(String clip,double rate)throws Exception{
        call(controller,"render",new Class<?>[]{unit.getClass(),Player.class,String.class,double.class,boolean.class},unit,null,clip,rate,true);
    }
    void aimAt(double x,double y,double z,double distance,float yaw){
        mob.setRotation(yaw,0);
        double angle=Math.toRadians(yaw),dx=x*Math.cos(angle)-z*Math.sin(angle),dz=z*Math.cos(angle)+x*Math.sin(angle);
        Location point=mob.getLocation().add(dx,y,dz);
        Location standing=point.clone().add(-Math.sin(angle)*distance,-player.getEyeHeight(),Math.cos(angle)*distance);
        standing.setY(Math.max(mob.getY(),standing.getY()));resetPlayer(standing);face(point);
    }
    void nativeHit(boolean expected,String label)throws Exception{
        clearHits();double hp=health();mob.damage(10,player);check((health()<hp)==expected,label+" hp="+hp+" -> "+health());
    }
    void aimSelected(boolean expected,String label)throws Exception{
        clearHits();Map.Entry<?,?> selected=(Map.Entry<?,?>)call(controller,"meleeAim",new Class<?>[]{Player.class,double.class,long.class},player,3.0,now);
        check((selected!=null&&selected.getKey()==mob)==expected,label);
    }
    @Override void mechanics()throws Exception{
        world.setDifficulty(Difficulty.NORMAL);controller=minions();invoke(controller,"demo",new Class<?>[]{UUID.class,boolean.class},player.getUniqueId(),false);
        now=(long)get(controller,"clock");
        for(int x=-10;x<=10;x++)for(int z=-10;z<=10;z++){
            world.getBlockAt(pad.getBlockX()+x,pad.getBlockY()-1,pad.getBlockZ()+z).setType(Material.STONE,false);
            for(int y=0;y<=15;y++)world.getBlockAt(pad.getBlockX()+x,pad.getBlockY()+y,pad.getBlockZ()+z).setType(Material.AIR,false);
        }
        Object boss=get(garden.worldBoss(),"arkhaia");arkhaia=boss;
        Class<?> runType=Arrays.stream(boss.getClass().getDeclaredClasses()).filter(c->c.getSimpleName().equals("Run")).findFirst().orElseThrow();
        var constructor=runType.getDeclaredConstructor(com.example.voidscape.VoidscapePlugin.class,Location.class,long.class);constructor.setAccessible(true);
        Object run=constructor.newInstance(garden,pad,now);Object originalAct=get(run,"act");
        Class<?>[] pose={run.getClass(),double.class,boolean.class};
        call(boss,"pose",pose,run,.08,false);
        check(get(run,"bodyPose").equals("walk")&&(double)get(run,"bodyTime")==0,"moving Arkhaia uses the visible walk body instead of idle");
        call(boss,"pose",pose,run,.16,false);
        call(boss,"pose",pose,run,.08,false);
        check(Math.abs((double)get(run,"bodyTime")-.075)<1e-8,"Arkhaia walk body integrates changing movement rates");
        put(run,"act",new com.example.voidscape.boss.ArkhaiaSovereign.Act(com.example.voidscape.boss.ArkhaiaSovereign.State.WINDUP,com.example.voidscape.boss.ArkhaiaSovereign.Move.LIGHT,10,20,false,0,3));
        call(boss,"pose",pose,run,0.0,false);
        check(get(run,"bodyPose").equals("light_windup")&&Math.abs((double)get(run,"bodyTime")-.35)<1e-8,"Arkhaia committed body pose spans its authored clip across the windup");
        put(run,"act",originalAct);call(boss,"pose",pose,run,0.0,false);
        ((AutoCloseable)get(run,"effects")).close();((AutoCloseable)get(run,"aura")).close();
        for(boolean phase2:List.of(false,true)){
            summon(ArkhaiaWaves.Kind.MALEFEASANCE);render("idle",1);
            if(phase2){
                check((boolean)call(get(controller,"trials"),"revive",new Class<?>[]{unit.getClass(),long.class},unit,now),"production rebirth changes Malefeasance to phase two");
                render("rebirth",.8);put(unit,"revivalUntil",0L);render("idle",1);
            }
            check(bodyModel().equals(phase2?"arkhaia_malefeasance_phase2":"arkhaia_malefeasance"),"Malefeasance uses the visible phase "+(phase2?2:1)+" body model");
            check(bodyScale()==2,"Malefeasance body matches the unchanged scale-two rig");
            for(float yaw:new float[]{0,90,180,270}){
                aimAt(0,2.8,0,2.5,yaw);check(Double.isFinite(bodyRay()),"ray intersects visible scale-two torso yaw="+yaw+" phase2="+phase2);
                aimSelected(true,"air swing selects visible scale-two torso yaw="+yaw+" phase2="+phase2);
                nativeHit(true,"native melee reaches visible scale-two torso yaw="+yaw+" phase2="+phase2);
            }
            aimAt(0,7.2,0,2.5,0);check(!Double.isFinite(bodyRay()),"no scale-four ghost body above Malefeasance phase2="+phase2);
            aimSelected(false,"air swing ignores the old oversized ghost phase2="+phase2);nativeHit(false,"native melee ignores the old oversized ghost phase2="+phase2);
            aimAt(0,2.8,0,7,0);nativeHit(false,"distant visible torso stays outside melee reach phase2="+phase2);
            aimAt(0,2.8,0,2.5,0);face(player.getEyeLocation().add(0,0,5));nativeHit(false,"facing away from visible torso is harmless phase2="+phase2);
            aimAt(0,2.8,0,2.5,0);Location wall=pad.clone().add(0,0,1);
            for(int x=-1;x<=1;x++)for(int y=0;y<=5;y++)world.getBlockAt(wall.getBlockX()+x,wall.getBlockY()+y,wall.getBlockZ()).setType(Material.STONE,false);
            nativeHit(false,"solid wall blocks body melee phase2="+phase2);aimSelected(false,"solid wall blocks air-swing selection phase2="+phase2);
            for(int x=-1;x<=1;x++)for(int y=0;y<=5;y++)world.getBlockAt(wall.getBlockX()+x,wall.getBlockY()+y,wall.getBlockZ()).setType(Material.AIR,false);
        }
        summon(ArkhaiaWaves.Kind.BANSHEE_WAIL);exposeBansheeBody();render("hover",1);
        // The approved gown has skirt bones and no leg bones; its visible lower body must still be targetable.
        for(float yaw:new float[]{0,90,180,270}){
            aimAt(.95,1,.55,2,yaw);check(Double.isFinite(bodyRay()),"Banshee visible lower gown has a body hitbox yaw="+yaw);
            aimSelected(true,"air swing selects Banshee lower gown yaw="+yaw);nativeHit(true,"native melee reaches Banshee lower gown yaw="+yaw);
        }
        summon(ArkhaiaWaves.Kind.MALEFEASANCE);
        for(int i=0;i<11;i++)render("walk",.5);
        check(Math.abs(bodyTime()-.25)<1e-8,"slow walk body clock accumulates ten half-speed frame intervals");
        for(int i=0;i<5;i++)render("walk",2);
        check(Math.abs(bodyTime()-.75)<1e-8,"changing playback speed preserves elapsed body pose instead of rescaling its history");
        Object visual=get(unit,"visual");if(visual!=null)check(Math.abs((double)get(visual,"clock")-bodyTime())<1e-8,"body and visible rig use the same accumulated animation clock");
        render("cast_windup",1.5);check(bodyTime()==0,"changing animation resets its body clock to the visible first frame");
        render("cast_windup",1.5);check(Math.abs(bodyTime()-.075)<1e-8,"new animation advances at its own playback rate");
        summon(ArkhaiaWaves.Kind.BANSHEE_WAIL);exposeBansheeBody();render("hover",1);
        aimAt(.95,1,.55,2,0);clearHits();
        ((Map<UUID,Long>)get(unit,"hits")).put(player.getUniqueId(),now);
        Map.Entry<?,?> atBoundary=(Map.Entry<?,?>)call(controller,"meleeAim",new Class<?>[]{Player.class,double.class,long.class},player,3.0,now);
        check(atBoundary!=null&&atBoundary.getKey()==mob,"a swing on the exact expiry tick can select the body");
        clearHits();player.getInventory().setItemInMainHand(new ItemStack(Material.DIAMOND_SWORD));
    }
    @Override void completed(){Bukkit.getScheduler().runTaskLater(this,this::swingThenTurn,1);}
    void fireSwing()throws Exception{
        PlayerAnimationEvent event=new PlayerAnimationEvent(player,PlayerAnimationType.ARM_SWING);
        if(sanctuaryRound){Bukkit.getPluginManager().callEvent(event);return;}
        // Register only while selecting the swing. The shared sanctuary controller
        // remains owned by its real encounter and is never driven by a fake main run.
        Class<?> demoType=Arrays.stream(arkhaia.getClass().getDeclaredClasses()).filter(c->c.getSimpleName().equals("Demo")).findFirst().orElseThrow();
        var constructor=demoType.getDeclaredConstructor(controller.getClass(),Location.class,long.class);constructor.setAccessible(true);
        Map<UUID,Object> demos=(Map<UUID,Object>)get(arkhaia,"demos");Object previous=demos.put(player.getUniqueId(),constructor.newInstance(controller,pad,now+12000));
        try{call(arkhaia,"swing",new Class<?>[]{PlayerAnimationEvent.class},event);}
        finally{if(previous==null)demos.remove(player.getUniqueId());else demos.put(player.getUniqueId(),previous);}
    }
    void swingThenTurn(){
        try{
            check(mob.isValid()&&get(unit,"mob")==mob,"deferred swing keeps its original loaded receiver");
            aimAt(.95,1,.55,2,0);clearHits();player.getInventory().setItemInMainHand(new ItemStack(Material.DIAMOND_SWORD));
            double before=health();fireSwing();
            face(player.getEyeLocation().add(0,0,5));Vector away=player.getEyeLocation().getDirection();
            Bukkit.getScheduler().runTaskLater(this,()->{try{
                check(health()<before,"deferred air swing uses the contact aim captured before the next-tick turn");
                check(player.getEyeLocation().getDirection().distanceSquared(away)<1e-8,"deferred hit preserves the player's current view direction");
                turnThenSwing();
            }catch(Throwable error){finish(error);}},2);
        }catch(Throwable error){finish(error);}
    }
    void turnThenSwing()throws Exception{
        aimAt(.95,1,.55,2,0);clearHits();Location inward=pad.clone().add(.95,1,.55);face(player.getEyeLocation().add(0,0,5));
        double before=health();fireSwing();face(inward);
        Bukkit.getScheduler().runTaskLater(this,()->{try{
            check(health()==before,"turning toward the boss after an away-facing swing cannot create a late hit");
            clearHits();aimAt(.95,1,.55,2,0);double hp=health();mob.damage(10,player);double once=health();check(once<hp,"native contact records one accepted strike");
            fireSwing();
            Bukkit.getScheduler().runTaskLater(this,()->{try{
                check(health()==once,"native hit plus same-tick air-swing event cannot deal duplicate damage");
                invoke(controller,"clear",new Class<?>[]{});superCompleted();
            }catch(Throwable error){finish(error);}},2);
        }catch(Throwable error){finish(error);}},2);
    }
    void superCompleted(){
        try{
            if(!sanctuaryRound){
                check(!mob.isValid(),"Arkhaia fallback fixture cleans its receiver before sanctuary fallback checks");
                sanctuaryRound=true;
                summon(ArkhaiaWaves.Kind.BANSHEE_WAIL);exposeBansheeBody();render("hover",1);
                Bukkit.getScheduler().runTaskLater(this,this::swingThenTurn,1);
            }else{
                garden.sanctuaryEncounters().close();check(!mob.isValid(),"sanctuary fallback fixture cleans its receiver");finish(null);
            }
        }catch(Throwable error){finish(error);}
    }
}
