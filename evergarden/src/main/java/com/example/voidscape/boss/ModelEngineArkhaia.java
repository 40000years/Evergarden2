package com.example.voidscape.boss;

import com.example.voidscape.VoidscapePlugin;
import com.ticxo.modelengine.api.ModelEngineAPI;
import com.ticxo.modelengine.api.animation.handler.IStateMachineHandler;
import com.ticxo.modelengine.api.entity.BukkitEntity;
import com.ticxo.modelengine.api.entity.CullType;
import com.ticxo.modelengine.api.entity.cull.BasicCulling;
import com.ticxo.modelengine.api.entity.data.BukkitEntityData;
import com.ticxo.modelengine.api.events.ModelRegistrationEvent;
import com.ticxo.modelengine.api.generator.ModelGenerator;
import com.ticxo.modelengine.api.model.ActiveModel;
import com.ticxo.modelengine.api.model.ModeledEntity;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Slime;
import org.bukkit.entity.Mob;
import org.bukkit.event.*;
import java.nio.file.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** R4 API lives only in this class so Evergarden still boots without Model Engine. */
final class ModelEngineArkhaia implements ArkhaiaModelSupport.Backend,Listener {
    static final List<String> BOSSES=List.of("seraphon");
    private final VoidscapePlugin plugin;
    private final Path engineFolder;
    private final List<String> requiredModels=new ArrayList<>();
    private boolean ready,published,closed;
    private String modelInfo="not installed";
    private String readiness="waiting for ModelEngine import";
    ModelEngineArkhaia(VoidscapePlugin plugin)throws IOException{
        this.plugin=plugin;
        engineFolder=Bukkit.getPluginManager().getPlugin("ModelEngine").getDataFolder().toPath();
        Path blueprint=engineFolder.resolve("blueprints/arkhaia.bbmodel");Files.createDirectories(blueprint.getParent());
        byte[] bundled;
        try(var input=plugin.getResource("modelengine/arkhaia.bbmodel")){
            if(input==null)throw new IOException("Bundled Arkhaia rig missing");bundled=input.readAllBytes();
        }
        installBlueprint(blueprint,bundled);
        requiredModels.add("internal_fire");requiredModels.add("arkhaia");
        // Main-boss showcase rigs share the managed-install and readiness path.
        for(String id:BOSSES){
            try(var input=plugin.getResource("modelengine/"+id+".bbmodel")){
                if(input==null)throw new IOException("Missing boss model "+id);
                ArkhaiaBlueprintInstaller.install(engineFolder.resolve("blueprints/"+id+".bbmodel"),
                    plugin.getDataFolder().toPath().resolve("modelengine/"+id+".sha256"),engineFolder.resolve(id+"-evergarden.sha256"),input.readAllBytes());
            }
            requiredModels.add(id);
        }
        List<String> rivers=new ArrayList<>();for(var kind:ArkhaiaWaves.Kind.values())rivers.add("arkhaia_"+kind.name().toLowerCase(Locale.ROOT));rivers.add("arkhaia_acheron_echo");rivers.add("arkhaia_malefeasance_phase2");
        requiredModels.addAll(rivers);
        for(String id:rivers){
            try(var input=plugin.getResource("modelengine/rivers/"+id+".bbmodel")){
                if(input==null)throw new IOException("Missing river model "+id);
                ArkhaiaBlueprintInstaller.install(engineFolder.resolve("blueprints/"+id+".bbmodel"),
                    plugin.getDataFolder().toPath().resolve("modelengine/"+id+".sha256"),engineFolder.resolve(id+"-evergarden.sha256"),input.readAllBytes());
            }
        }
        Bukkit.getPluginManager().registerEvents(this,plugin);
        // An Evergarden reload can happen after ME has already finished importing.
        Bukkit.getScheduler().runTask(plugin,()->refresh(false));
    }
    private void installBlueprint(Path blueprint,byte[] bundled)throws IOException{
        Path managed=plugin.getDataFolder().toPath().resolve("modelengine/arkhaia.sha256");
        var result=ArkhaiaBlueprintInstaller.install(blueprint,managed,engineFolder.resolve("arkhaia-evergarden.sha256"),bundled);
        modelInfo=(result.bundled()?"bundled rig ":"custom rig ")+result.hash().substring(0,12);
        if(result.upgraded())plugin.getLogger().info("Updated Arkhaia rig; previous blueprint backed up to "+result.backup());
        if(!result.bundled())plugin.getLogger().warning("Using existing edited Arkhaia blueprint: "+blueprint);
        plugin.getLogger().info("Arkhaia blueprint: "+modelInfo+" | expected "+ArkhaiaBlueprintInstaller.digest(bundled).substring(0,12));
    }
    @EventHandler public void generated(ModelRegistrationEvent event){
        Bukkit.getScheduler().runTask(plugin,()->{
            if(closed)return;
            ready=false;published=false;readiness="ModelEngine is rebuilding model assets";
            if(event.getPhase()==ModelGenerator.Phase.FINISHED)refresh(true);
        });
    }
    private void refresh(boolean reportMissing){
        if(closed)return;
        ready=false;published=false;
        List<String> missing=requiredModels.stream().filter(id->ModelEngineAPI.getBlueprint(id)==null).toList();
        if(missing.contains("internal_fire")||missing.contains("arkhaia")){
            readiness="missing ModelEngine blueprints: "+String.join(", ",missing);
            if(reportMissing)plugin.getLogger().warning("Arkhaia renderer unavailable: "+readiness+
                ". Keep room for ModelEngine built-in models (including internal_fire), then restart or use /meg reload. Boss models have not been replaced.");
            return;
        }
        try{
            // Publish whatever imported: one broken minion or showcase blueprint must not hide the boss.
            published=plugin.judgePack().includeModelEngine(engineFolder.resolve("resource pack.zip"));
            ready=published&&missing.isEmpty();
            readiness=!published?"waiting for published resource pack":ready?"ready":"ready except missing blueprints: "+String.join(", ",missing);
            if(published&&!missing.isEmpty())plugin.getLogger().warning("Model Engine blueprints failed to import: "+String.join(", ",missing)
                +". Those models use their fallbacks; the other rigs stay available.");
            plugin.getLogger().info("Arkhaia articulated renderer: "+readiness);
        }catch(Exception error){readiness="cannot publish ModelEngine resource pack";plugin.getLogger().log(java.util.logging.Level.WARNING,"Cannot prepare Arkhaia model assets",error);}
    }
    @Override public boolean ready(){
        if(!ready||closed)return false;
        // R4 initializes the fire display even when this model disables fire.
        // A successful boss import alone is insufficient if internal_fire was skipped.
        if(requiredModels.stream().anyMatch(id->ModelEngineAPI.getBlueprint(id)==null)){ready=false;return false;}
        return true;
    }
    @Override public boolean ready(String model){
        return published&&!closed&&ModelEngineAPI.getBlueprint("internal_fire")!=null&&ModelEngineAPI.getBlueprint(model)!=null;
    }
    @Override public String modelInfo(){return modelInfo+" | "+readiness;}
    @Override public ArkhaiaModelSupport.Rig attach(Slime target){return new Rig(target,"arkhaia",3.2);}
    @Override public ArkhaiaModelSupport.Rig attachRiver(Mob target,String model){return new Rig(target,model,RiverVisual.scale(model));}
    @Override public ArkhaiaModelSupport.Rig attachBoss(Mob target,String model,double scale){return new Rig(target,model,scale);}
    @Override public void close(){closed=true;ready=false;published=false;HandlerList.unregisterAll(this);}

    /** Culling uses UUID snapshots; no Bukkit calls from our animation worker. */
    private static final class AudienceCull extends BasicCulling {
        final Set<UUID> accepted=ConcurrentHashMap.newKeySet();
        @Override public void updateCulledPlayer(){
            super.updateCulledPlayer();
            if(data!=null)for(UUID id:new ArrayList<>(data.getMutableTracking().keySet()))
                if(!accepted.contains(id))put(id,CullType.CULLED);
        }
    }
    private static final int AURA_LANE=1;
    private final class Rig implements ArkhaiaModelSupport.Rig {
        private final Mob target;
        private final AudienceCull audience=new AudienceCull();
        private final ModeledEntity entity;
        private final ActiveModel model;
        private final IStateMachineHandler animation;
        private String clip="";
        private boolean destroyed;
        private float gazeYaw,gazePitch;
        Rig(Mob target,String modelId,double scale){
            if(!ready(modelId))
                throw new IllegalStateException("ModelEngine renderer unavailable for "+modelId+": "+readiness);
            this.target=target;
            // The real slime remains the damage receiver; ME must not resize its hitbox.
            BukkitEntity base=new BukkitEntity(target){
                @Override protected BukkitEntityData createEntityData(Entity entity){
                    return new BukkitEntityData(entity,this,audience);
                }
                @Override public boolean isWalking(){return false;}
                @Override public boolean isFlying(){return false;}
                @Override public boolean isJumping(){return false;}
            };
            entity=ModelEngineAPI.createModeledEntity(base);
            ActiveModel created=null;
            try{
                entity.setSaved(false);entity.setModelRotationLocked(true);
                model=created=ModelEngineAPI.createActiveModel(modelId,null,ModelEngineAPI::createStateMachineHandler);
                model.setScale(scale);model.setCanHurt(false);model.setShadowVisible(false);
                model.setRenderFire(false);model.setOnFire(false);
                model.setBlockLight(14);model.setSkyLight(15);model.setLockPitch(true);model.setLockYaw(true);
                entity.addModel(model,false);
                if(entity.getModel(modelId).orElse(null)!=model)throw new IllegalStateException("ModelEngine attachment cancelled: "+modelId);
                // R4 reads locked angles only when the attached ActiveModel is
                // explicitly locked. The entity lock alone freezes its old yaw.
                model.setModelRotationLocked(true);
                animation=(IStateMachineHandler)model.getAnimationHandler();
                // The aura (halo, flames, sigils) loops on its own lower lane; combat
                // clips never key those bones, so switching clips cannot reset them.
                if(model.getBlueprint().getAnimations().containsKey("aura"))animation.playAnimation(AURA_LANE,"aura",0,0,1,true);
                play("idle",1);
                entity.setBaseEntityVisible(false);
            }catch(RuntimeException|Error error){
                // addModel generates before inserting into the entity's model map.
                // If generation fails, queued entity removal cannot find this partial model.
                if(created!=null&&created.getModeledEntity()!=null&&!entity.getModels().containsValue(created))
                    try{created.destroy();}catch(RuntimeException|Error cleanup){error.addSuppressed(cleanup);}
                ModelEngineAPI.removeModeledEntity(target.getUniqueId());throw error;
            }
        }
        private void play(String next,double speed){
            if(!next.equals(clip)){
                // One explicit priority lane: switch directly between authored poses.
                // Release clips start at the held windup pose. Avoid delaying
                // their contact keyframe beyond the brain's damage tick.
                animation.playAnimation(10,next,next.endsWith("_active")?0:.04,.06,speed,true);clip=next;
            }else{var property=animation.getAnimation(10,next);if(property!=null)property.setSpeed(speed);}
        }
        @Override public void frame(ArkhaiaBrain.Frame frame,double speed,boolean airborne,Location at){
            if(destroyed)return;
            String next=ArkhaiaModelSupport.clip(frame,speed,airborne);
            play(next,next.equals("walk")||next.equals("backstep")?Math.clamp(speed/.16,.35,2):1);
            model.setLockedYBodyRot(at.getYaw());model.setLockedYHeadRot(at.getYaw()+gazeYaw);model.setLockedXHeadRot(gazePitch);
        }
        @Override public void look(float yaw,float pitch){gazeYaw=yaw;gazePitch=pitch;}
        @Override public void clip(String name,double rate,Location at){
            if(destroyed)return;play(name,rate);
            model.setLockedYBodyRot(at.getYaw());model.setLockedYHeadRot(at.getYaw()+gazeYaw);model.setLockedXHeadRot(gazePitch);
        }
        @Override public void viewers(Set<UUID> players){
            audience.accepted.retainAll(players);audience.accepted.addAll(players);
            entity.getBase().getData().markViewersDirty();
        }
        @Override public Location swordTip(){
            var bone=model.getBones().get("sword_tip");
            return bone==null||destroyed?null:bone.getLocation();
        }
        @Override public Location boneOrigin(String name){
            var bone=model.getBones().get(name);return bone==null||destroyed?null:bone.getLocation();
        }
        @Override public void close(){
            if(destroyed)return;destroyed=true;audience.accepted.clear();
            // R4 queues removal on its worker, which owns renderer destruction.
            ModelEngineAPI.removeModeledEntity(target.getUniqueId());
        }
    }
}
