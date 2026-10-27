import com.example.voidscape.VoidscapePlugin;
import com.ticxo.modelengine.api.ModelEngineAPI;
import com.ticxo.modelengine.api.animation.handler.IStateMachineHandler;
import com.ticxo.modelengine.api.model.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.plugin.java.JavaPlugin;
import java.nio.file.*;
import java.util.*;

/** Official R4 import and state-machine registration of the released dimensional rig. */
public class StyxNativeProbe extends JavaPlugin {
    int checks,waited;Mob mob;ModeledEntity entity;ActiveModel model;IStateMachineHandler animation;
    final List<String> lines=new ArrayList<>();
    final List<String> clips=List.of("blood_windup","blood_active","blood_recover","berserk_windup","berserk_active","berserk_recover");
    void check(boolean ok,String text){if(!ok)throw new AssertionError(text);checks++;lines.add(text);getLogger().info("PASS: "+text);}
    @Override public void onEnable(){Bukkit.getScheduler().runTaskLater(this,this::start,160);}
    void start(){try{
        VoidscapePlugin garden=(VoidscapePlugin)Bukkit.getPluginManager().getPlugin("Evergarden");
        if(ModelEngineAPI.getBlueprint("arkhaia_styx")==null){if((waited+=20)<1200){Bukkit.getScheduler().runTaskLater(this,this::start,20);return;}throw new AssertionError("R4 import never completed");}
        check(garden!=null&&garden.isEnabled(),"release boots with official Model Engine");
        check(ModelEngineAPI.getBlueprint("arkhaia_styx").getAnimations().size()==28,"R4 imports all twenty-eight Styx animation clips");
        mob=garden.world().spawn(new Location(garden.world(),0,125,0),Skeleton.class,e->{e.setAI(false);e.setGravity(false);e.setPersistent(false);e.addScoreboardTag("evergarden_unique");});
        entity=ModelEngineAPI.createModeledEntity(mob);entity.setSaved(false);entity.setBaseEntityVisible(false);
        model=ModelEngineAPI.createActiveModel("arkhaia_styx",null,ModelEngineAPI::createStateMachineHandler);model.setScale(4);entity.addModel(model,false);
        animation=(IStateMachineHandler)model.getAnimationHandler();
        check(model.getBones().containsKey("axe_driver")&&model.getBones().containsKey("weapon_r"),"native rig retains the articulated two-handed axe");play(0);
    }catch(Throwable e){finish(e);}}
    void play(int index){try{
        if(index==clips.size()){entity.destroy();mob.remove();check(!mob.isValid(),"temporary native model and receiver clean up");finish(null);return;}
        String clip=clips.get(index);animation.playAnimation(10,clip,0,0,1,true);
        check(animation.isPlayingAnimation(10,clip),"actual R4 state machine plays "+clip);
        Bukkit.getScheduler().runTaskLater(this,()->play(index+1),4);
    }catch(Throwable e){finish(e);}}
    void finish(Throwable error){
        if(error!=null){error.printStackTrace();lines.add("ERROR: "+error);}
        try{Files.writeString(Path.of("model-engine-result.txt"),(error==null?"PASS":"FAIL")+": Styx Model Engine / "+checks+" checks\n"+String.join("\n",lines)+"\n");}catch(Exception e){e.printStackTrace();}
        Bukkit.shutdown();
    }
}
