import com.example.voidscape.boss.ArkhaiaWaves;
import com.ticxo.modelengine.api.ModelEngineAPI;
import com.ticxo.modelengine.api.model.ModeledEntity;
import com.ticxo.modelengine.api.model.ActiveModel;
import org.bukkit.entity.Mob;

/** Evaluate new poses on R4's actual animation worker, with a headless viewer. */
public class RiverNativePoseProbe extends ArkhaiaModelEngineProbe {
    @Override void checkRiverCharge()throws Exception{
        check(garden.worldBoss().riverTest(player,ArkhaiaWaves.Kind.ACHERON.ordinal(),true),"Acheron native split fixture starts");
        Object controller=riverController(),main=riverSpirits().values().iterator().next();
        Mob owner=(Mob)field(main,"mob");setRiverField(main,"castAt",Long.MAX_VALUE);setRiverField(main,"snareAt",Long.MAX_VALUE);setRiverField(main,"pendingSplit",true);
        stepRivers(1);
        var nativeMain=ModelEngineAPI.getModeledEntity(owner.getUniqueId()).getModel("arkhaia_acheron").orElseThrow();
        check(((com.ticxo.modelengine.api.animation.handler.IStateMachineHandler)nativeMain.getAnimationHandler()).isPlayingAnimation(10,"ritual_windup"),"split selects actual R4 ritual animation instead of spin");
        stepRivers(29);java.util.List<?> echoes=(java.util.List<?>)field(controller,"echoes");
        check(echoes.size()==2,"real renderer stack summons two native echoes at ritual contact");
        for(Object echo:echoes){
            Mob mob=(Mob)field(echo,"mob");var entity=ModelEngineAPI.getModeledEntity(mob.getUniqueId());
            var model=entity.getModel("arkhaia_acheron_echo").orElseThrow();
            check(!model.getBones().containsKey("halo"),"echo model contains no halo bone");
            check(mob.getAttribute(org.bukkit.attribute.Attribute.SCALE).getValue()==2.4&&mob.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue()==200,"native echo receiver matches small model and 200 HP");
            Object visual=field(echo,"visual");org.bukkit.entity.ArmorStand carrier=(org.bukkit.entity.ArmorStand)field(visual,"carrier");
            Object actor=((java.util.Map<?,?>)field(garden.arkhaiaBedrock(),"actors")).get(carrier.getUniqueId());
            check(field(actor,"model").equals("arkhaia_acheron_echo"),"Geyser carrier selects dedicated small halo-free echo geometry");
            check(model.getBlueprint().getAnimations().containsKey("ritual_active"),"echo protocol retains shared clip indexes while AI forbids recursive skills");
        }
        garden.worldBoss().clearRiverTest(player);
        check(echoes.isEmpty(),"native split cleanup removes both echo renderers and carriers");
        super.checkRiverCharge();
    }
    ActiveModel riverModel(ArkhaiaWaves.Kind kind)throws Exception{
        Object spirit=riverSpirits().get(kind);Mob mob=(Mob)field(spirit,"mob");
        return ModelEngineAPI.getModeledEntity(mob.getUniqueId()).getModel("arkhaia_"+kind.name().toLowerCase(java.util.Locale.ROOT)).orElseThrow();
    }
    Mob riverMob(ArkhaiaWaves.Kind kind)throws Exception{return (Mob)field(riverSpirits().get(kind),"mob");}
    void pose(String name)throws Exception{
        // The public GUI intentionally exposes four safe poses only. Select the
        // individual attack clip through the fixture's private controller state.
        setRiverField(riverController(),"demoPose",name);stepRivers(1);
    }
    @Override void checkRiverPermissions(){try{
        for(Object spirit:riverSpirits().values()){
            Mob mob=(Mob)field(spirit,"mob");
            ModelEngineAPI.getModeledEntity(mob.getUniqueId()).registerTickTask(ModeledEntity.Phase.PRE_MODEL_TICK,
                (java.util.function.Consumer<ModeledEntity>) entity->entity.getBase().getData().getMutableTracking().put(player.getUniqueId(),com.ticxo.modelengine.api.entity.CullType.NO_CULL));
        }
        pose("attack_windup");later(14,this::raised);
    }catch(Exception e){throw new RuntimeException(e);}}
    void raised(){try{
        var fire=riverModel(ArkhaiaWaves.Kind.PHLEGETHON);double head=fire.getBones().get("head").getLocation().getY();
        for(String side:new String[]{"r","l"}){
            double height=fire.getBones().get("hand_"+side).getLocation().getY()-head;
            check(height>1,"native Phlegethon fist is above the head "+side+" delta="+height);
        }
        var archer=riverModel(ArkhaiaWaves.Kind.COCYTUS);Mob mob=riverMob(ArkhaiaWaves.Kind.COCYTUS);
        var bow=archer.getBones().get("hand_l").getLocation();var draw=archer.getBones().get("hand_r").getLocation();
        double forward=bow.toVector().subtract(draw.toVector()).dot(mob.getLocation().getDirection());
        check(forward>4,"native side-on bow stance extends bow in front of the draw hand; forward="+forward);
        var axe=riverModel(ArkhaiaWaves.Kind.STYX);
        check(axe.getBones().containsKey("axe_driver")&&!axe.getBones().containsKey("axe_swing"),"native axe uses a weapon driver and articulated arms");
        double gap=axe.getBones().get("hand_r").getLocation().distance(axe.getBones().get("hand_l").getLocation());
        check(gap>.8&&gap<1.2,"native Styx regrips the shaft with both hands; gap="+gap);
        double drift=axe.getBones().get("hand_r").getLocation().distance(axe.getBones().get("weapon_r").getLocation());
        check(drift<.03,"native weapon and right palm retain the same grip point; drift="+drift);
        pose("attack_active");later(6,this::awaitImpact);
    }catch(Exception e){throw new RuntimeException(e);}}
    int impactWait;
    void awaitImpact(){try{
        var handler=(com.ticxo.modelengine.api.animation.handler.IStateMachineHandler)riverModel(ArkhaiaWaves.Kind.PHLEGETHON).getAnimationHandler();
        var active=handler.getAnimation(10,"attack_active");
        if(active!=null&&active.getTime()>=.28){impact();return;}
        if(++impactWait>12)throw new AssertionError("R4 did not evaluate the active contact pose in time");
        later(1,this::awaitImpact);
    }catch(Exception e){throw new RuntimeException(e);}}
    void impact(){try{
        var fire=riverModel(ArkhaiaWaves.Kind.PHLEGETHON);double floor=riverMob(ArkhaiaWaves.Kind.PHLEGETHON).getY();
        for(String side:new String[]{"r","l"}){
            double height=fire.getBones().get("hand_"+side).getLocation().getY()-floor;
            check(height>.3&&height<1.3,"native Phlegethon fist reaches ground slam height "+side+" height="+height);
        }
        var axe=riverModel(ArkhaiaWaves.Kind.STYX);
        check(axe.getBones().get("hand_r").getLocation().distance(axe.getBones().get("weapon_r").getLocation())<.03,"native axe grip stays aligned at impact");
        furnacePose("kick_active");later(6,()->awaitFurnace("kick_active",this::kickContact));
    }catch(Exception e){throw new RuntimeException(e);}}
    void furnacePose(String clip)throws Exception{
        Object visual=field(riverSpirits().get(ArkhaiaWaves.Kind.PHLEGETHON),"visual");
        var method=visual.getClass().getDeclaredMethod("frame",org.bukkit.entity.Player.class,String.class,double.class,boolean.class);method.setAccessible(true);method.invoke(visual,player,clip,1.0,true);
        var carrier=(org.bukkit.entity.ArmorStand)field(visual,"carrier");
        Object actor=((java.util.Map<?,?>)field(garden.arkhaiaBedrock(),"actors")).get(carrier.getUniqueId());
        var snapshot=(com.example.voidscape.boss.ArkhaiaBedrockSupport.Snapshot)field(actor,"snapshot");
        check(snapshot.clip()==com.example.voidscape.boss.ArkhaiaBedrockSupport.riverClips("arkhaia_phlegethon").indexOf(clip),"Geyser uses Phlegethon-specific clip index "+clip);
        impactWait=0;
    }
    void awaitFurnace(String clip,Runnable next){try{
        var h=(com.ticxo.modelengine.api.animation.handler.IStateMachineHandler)riverModel(ArkhaiaWaves.Kind.PHLEGETHON).getAnimationHandler();
        var animation=h.getAnimation(10,clip);
        if(animation!=null&&animation.getTime()>=.22){next.run();return;}
        if(++impactWait>12)throw new AssertionError("R4 furnace contact pose timed out: "+clip);
        later(1,()->awaitFurnace(clip,next));
    }catch(Exception e){throw new RuntimeException(e);}}
    void kickContact(){try{
        var fire=riverModel(ArkhaiaWaves.Kind.PHLEGETHON);var mob=riverMob(ArkhaiaWaves.Kind.PHLEGETHON);
        var foot=fire.getBones().get("foot_r").getLocation();var planted=fire.getBones().get("foot_l").getLocation();
        check(foot.getY()-mob.getY()>1.3&&foot.getY()-mob.getY()<1.7,"R4 kick lifts striking foot to player body height");
        check(planted.getY()-mob.getY()<.6,"R4 kick keeps supporting foot planted");
        check(foot.toVector().subtract(planted.toVector()).dot(mob.getLocation().getDirection())>2,"R4 kicking foot extends forward beyond supporting leg");
        furnacePose("throw_active");later(6,()->awaitFurnace("throw_active",this::throwContact));
    }catch(Exception e){throw new RuntimeException(e);}}
    void throwContact(){try{
        var fire=riverModel(ArkhaiaWaves.Kind.PHLEGETHON);var mob=riverMob(ArkhaiaWaves.Kind.PHLEGETHON);
        for(String side:new String[]{"r","l"}){
            var hand=fire.getBones().get("hand_"+side).getLocation();
            check(hand.toVector().subtract(mob.getLocation().toVector()).dot(mob.getLocation().getDirection())>2,"R4 throw releases boulder in front of body "+side);
        }
        bansheePose("curse_active");later(8,this::bansheeCurse);
    }catch(Exception e){throw new RuntimeException(e);}}
    void bansheePose(String clip)throws Exception{
        Object visual=field(riverSpirits().get(ArkhaiaWaves.Kind.BANSHEE_WAIL),"visual");
        var method=visual.getClass().getDeclaredMethod("frame",org.bukkit.entity.Player.class,String.class,double.class,boolean.class);method.setAccessible(true);method.invoke(visual,player,clip,1.0,true);
        var carrier=(org.bukkit.entity.ArmorStand)field(visual,"carrier");Object actor=((java.util.Map<?,?>)field(garden.arkhaiaBedrock(),"actors")).get(carrier.getUniqueId());
        var snapshot=(com.example.voidscape.boss.ArkhaiaBedrockSupport.Snapshot)field(actor,"snapshot");
        check(snapshot.clip()==com.example.voidscape.boss.ArkhaiaBedrockSupport.riverClips("arkhaia_banshee_wail").indexOf(clip),"Geyser Banshee clip uses its own phase table "+clip);
    }
    void bansheeCurse(){try{
        var ghost=riverModel(ArkhaiaWaves.Kind.BANSHEE_WAIL);var mob=riverMob(ArkhaiaWaves.Kind.BANSHEE_WAIL);
        var h=(com.ticxo.modelengine.api.animation.handler.IStateMachineHandler)ghost.getAnimationHandler();
        check(h.isPlayingAnimation(10,"curse_active"),"R4 runs dedicated Banshee curse animation");
        double cape=ghost.getBones().get("cape_tip").getLocation().toVector().subtract(mob.getLocation().toVector()).dot(mob.getLocation().getDirection());
        check(cape<0,"native curse cape trails behind the forward soul release");
        bansheePose("reveal");later(10,this::bansheeReveal);
    }catch(Exception e){throw new RuntimeException(e);}}
    void bansheeReveal(){try{
        var h=(com.ticxo.modelengine.api.animation.handler.IStateMachineHandler)riverModel(ArkhaiaWaves.Kind.BANSHEE_WAIL).getAnimationHandler();
        check(h.isPlayingAnimation(10,"reveal"),"R4 runs broken-veil reveal pose");bansheePose("reform");later(10,this::bansheeReform);
    }catch(Exception e){throw new RuntimeException(e);}}
    void bansheeReform(){try{
        var h=(com.ticxo.modelengine.api.animation.handler.IStateMachineHandler)riverModel(ArkhaiaWaves.Kind.BANSHEE_WAIL).getAnimationHandler();
        check(h.isPlayingAnimation(10,"reform"),"R4 runs soul-veil reformation pose");
        Object spirit=riverSpirits().get(ArkhaiaWaves.Kind.BANSHEE_WAIL),state=field(spirit,"soul"),souls=field(riverController(),"souls");
        var bind=souls.getClass().getDeclaredMethod("bind",state.getClass(),Mob.class,boolean.class);bind.setAccessible(true);bind.invoke(souls,state,riverMob(ArkhaiaWaves.Kind.BANSHEE_WAIL),false);
        java.util.List<?> anchors=(java.util.List<?>)field(state,"anchors");check(anchors.size()==3,"real ME/Geyser/LevelledMobs stack creates three soul anchors");
        for(Object anchor:anchors){
            Mob receiver=(Mob)field(anchor,"receiver");
            check(receiver.isValid()&&receiver.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue()==30&&com.example.voidscape.compat.LevelledMobsCompat.isEvergardenMob(receiver),"native soul anchor survives spawn filtering and real LevelledMobs at 30 HP");
            check(((org.bukkit.entity.BlockDisplay)field(anchor,"crystal")).isValid(),"native soul crystal display exists");
        }
        var remove=souls.getClass().getDeclaredMethod("remove",state.getClass());remove.setAccessible(true);remove.invoke(souls,state);
        check(anchors.isEmpty(),"native anchor group cleans up without touching other river models");
        nativeBansheeBlink();
    }catch(Exception e){throw new RuntimeException(e);}}
    void nativeBansheeBlink()throws Exception{
        Object controller=riverController(),spirit=riverSpirits().get(ArkhaiaWaves.Kind.BANSHEE_WAIL);Mob mob=riverMob(ArkhaiaWaves.Kind.BANSHEE_WAIL);
        var saved=player.getLocation();long clock=(long)field(controller,"clock");player.teleport(mob.getLocation().add(16,0,0));
        setRiverField(spirit,"blinkNext",0L);setRiverField(spirit,"castAt",Long.MAX_VALUE);
        var begin=controller.getClass().getDeclaredMethod("beginBlink",spirit.getClass(),org.bukkit.entity.Player.class);begin.setAccessible(true);
        check((boolean)begin.invoke(controller,spirit,player),"native Banshee starts telegraphed pursuit");
        var destination=((org.bukkit.Location)field(spirit,"blinkMark")).clone();
        setRiverField(controller,"clock",clock+12);var step=controller.getClass().getDeclaredMethod("blinkStep",spirit.getClass(),org.bukkit.entity.Player.class);step.setAccessible(true);step.invoke(controller,spirit,player);setRiverField(controller,"clock",clock);
        check(mob.getLocation().distanceSquared(destination)<1e-8,"native receiver reaches committed pursuit landing");
        Object visual=field(spirit,"visual");var carrier=(org.bukkit.entity.ArmorStand)field(visual,"carrier");
        check(carrier.getLocation().distanceSquared(destination)<1e-8,"Geyser visual carrier moves with pursuit receiver");
        later(8,()->{try{
            var head=riverModel(ArkhaiaWaves.Kind.BANSHEE_WAIL).getBones().get("head").getLocation();
            check(Math.hypot(head.getX()-destination.getX(),head.getZ()-destination.getZ())<4,"real R4 bones follow teleported receiver instead of staying behind");
            player.teleport(saved);garden.worldBoss().riverPose(player,"idle");styxClip(0);
        }catch(Exception e){throw new RuntimeException(e);}});
    }
    void styxClip(int index)throws Exception{
        var clips=com.example.voidscape.boss.ArkhaiaBedrockSupport.riverClips("arkhaia_styx");
        if(index>=12){garden.worldBoss().riverPose(player,"idle");super.checkRiverPermissions();return;}
        String clip=clips.get(index+10);Object spirit=riverSpirits().get(ArkhaiaWaves.Kind.STYX),visual=field(spirit,"visual");
        var frame=visual.getClass().getDeclaredMethod("frame",org.bukkit.entity.Player.class,String.class,double.class,boolean.class);frame.setAccessible(true);frame.invoke(visual,player,clip,1.0,true);
        var carrier=(org.bukkit.entity.ArmorStand)field(visual,"carrier");Object actor=((java.util.Map<?,?>)field(garden.arkhaiaBedrock(),"actors")).get(carrier.getUniqueId());
        var snapshot=(com.example.voidscape.boss.ArkhaiaBedrockSupport.Snapshot)field(actor,"snapshot");
        check(snapshot.clip()==index+10,"Styx Geyser protocol selects unique clip "+clip);
        later(6,()->{try{
            var model=riverModel(ArkhaiaWaves.Kind.STYX);var h=(com.ticxo.modelengine.api.animation.handler.IStateMachineHandler)model.getAnimationHandler();
            check(h.isPlayingAnimation(10,clip),"R4 actually plays new Styx clip "+clip);
            var animation=h.getAnimation(10,clip);animation.setSpeed(0);
            // Snapshot the same evaluated pose on the asynchronous R4 worker.
            later(3,()->{try{
                double drift=model.getBones().get("hand_r").getLocation().distance(model.getBones().get("weapon_r").getLocation());
                check(drift<.04,"Styx axe stays in authored palm in "+clip+" t="+animation.getTime()+" drift="+drift);
                styxClip(index+1);
            }catch(Exception e){throw new RuntimeException(e);}});
        }catch(Exception e){throw new RuntimeException(e);}});
    }
}
