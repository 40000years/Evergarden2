package com.example.voidscape.pet;

import com.example.voidscape.VoidscapePlugin;
import com.ticxo.modelengine.api.ModelEngineAPI;
import com.ticxo.modelengine.api.animation.handler.IStateMachineHandler;
import com.ticxo.modelengine.api.entity.BukkitEntity;
import com.ticxo.modelengine.api.events.ModelRegistrationEvent;
import com.ticxo.modelengine.api.generator.ModelGenerator;
import com.ticxo.modelengine.api.model.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.Slime;
import org.bukkit.event.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.logging.Level;

/** Optional R4 boundary, loaded only after the dependency has been verified. */
final class ModelEnginePetBackend implements PetModelPreviews.Backend,Listener {
    private final VoidscapePlugin plugin;
    private final PetModelPreviews owner;
    private final Path engineFolder;
    private final PetBlueprintStaging.Summary installed;
    private boolean closed,ready;
    private boolean reportedMissingFire;
    private int imported;
    private String status="กำลังรอ ModelEngine นำเข้าสัตว์เลี้ยง "+PetCatalog.modelIds().size()+" โมเดล";
    ModelEnginePetBackend(VoidscapePlugin plugin,PetModelPreviews owner)throws IOException {
        this.plugin=plugin;this.owner=owner;
        engineFolder=Objects.requireNonNull(Bukkit.getPluginManager().getPlugin("ModelEngine")).getDataFolder().toPath();
        installed=PetBlueprintStaging.prepare(plugin,engineFolder);
        plugin.getLogger().info("Pet blueprints: "+installed.description());
        Bukkit.getPluginManager().registerEvents(this,plugin);
        // Also handle a plugin reload after ME has already completed its first import.
        Bukkit.getScheduler().runTask(plugin,this::refresh);
    }
    @EventHandler public void registration(ModelRegistrationEvent event) {
        Bukkit.getScheduler().runTask(plugin,()->{
            if(closed)return;
            ready=false;owner.invalidate();status="ModelEngine กำลังสร้าง resource pack สัตว์เลี้ยง · รอสักครู่";
            if(event.getPhase()==ModelGenerator.Phase.FINISHED)refresh();
        });
    }
    private void refresh() {
        if(closed)return;
        ready=false;
        imported=0;for(String id:PetCatalog.modelIds())if(ModelEngineAPI.getBlueprint(id)!=null)imported++;
        if(imported==0){
            status=installed.active()==0?"เก็บโมเดลสัตว์เลี้ยงไว้ใน library แล้ว · เลือก pets.model-preview.install-species ก่อนเสก":
                "รอ ModelEngine นำเข้าโมเดลที่เลือก · ถ้าเพิ่งเปลี่ยน config ให้รีสตาร์ตหรือใช้ /meg reload";
            return;
        }
        if(ModelEngineAPI.getBlueprint("internal_fire")==null){
            status="ModelEngine ขาด internal_fire · ลดจำนวน blueprint แล้วรีสตาร์ตหรือใช้ /meg reload";
            if(!reportedMissingFire){
                reportedMissingFire=true;
                plugin.getLogger().warning("Pet renderer unavailable: ModelEngine did not import its built-in internal_fire blueprint. "+
                    "Keep room for built-in models; select fewer pets in pets.model-preview.install-species, then restart or use /meg reload. No preview entities were created.");
            }
            return;
        }
        reportedMissingFire=false;
        try {
            readable=imported+"/"+PetCatalog.modelIds().size()+" โมเดล · "+installed.description();
            ready=plugin.judgePack().includeModelEngine(engineFolder.resolve("resource pack.zip"));
            status=ready?"พร้อม "+readable+" · Java Edition":
                "นำเข้าแล้ว "+readable+" · ต้องเปิด boss pack HTTP host และเว้น world-boss.visuals.pack.url ว่าง";
            plugin.getLogger().info("Pet model previews: "+status);
        } catch(Exception error) {
            ready=false;status="นำเข้าโมเดลแล้ว แต่เผยแพร่ resource pack ไม่สำเร็จ · ดู console";
            plugin.getLogger().log(Level.WARNING,"Cannot publish pet model assets",error);
        }
    }
    private String readable="";
    @Override public boolean ready() {
        if(!ready||closed)return false;
        if(ModelEngineAPI.getBlueprint("internal_fire")==null){
            ready=false;status="ModelEngine ขาด internal_fire · รอสร้างโมเดลใหม่";return false;
        }
        return true;
    }
    @Override public int imported() {return imported;}
    @Override public boolean hasModel(String id) {return ready()&&ModelEngineAPI.getBlueprint(id)!=null;}
    @Override public String status() {return status;}
    @Override public PetModelPreviews.Rig attach(Slime shell,String id) {
        if(!hasModel(id))throw new IllegalStateException("Pet model unavailable: "+id);
        ModeledEntity entity=ModelEngineAPI.createModeledEntity(new BukkitEntity(shell) {
            @Override public boolean isWalking(){return false;}
            @Override public boolean isFlying(){return false;}
            @Override public boolean isJumping(){return false;}
        });
        ActiveModel model=null;
        try {
            entity.setSaved(false);
            model=ModelEngineAPI.createActiveModel(id,null,ModelEngineAPI::createStateMachineHandler);
            model.setScale(1);model.setCanHurt(false);model.setShadowVisible(false);
            model.setRenderFire(false);model.setOnFire(false);
            model.setBlockLight(12);model.setSkyLight(15);
            entity.addModel(model,false);
            if(entity.getModel(id).orElse(null)!=model)throw new IllegalStateException("ModelEngine attachment cancelled: "+id);
            var rig=new Rig(shell,model);rig.animate("idle");
            entity.setBaseEntityVisible(false);
            return rig;
        } catch(RuntimeException|Error failure) {
            if(model!=null&&model.getModeledEntity()!=null&&!entity.getModels().containsValue(model))
                try{model.destroy();}catch(RuntimeException|Error cleanup){failure.addSuppressed(cleanup);}
            ModelEngineAPI.removeModeledEntity(shell.getUniqueId());throw failure;
        }
    }
    @Override public void close(){closed=true;ready=false;HandlerList.unregisterAll(this);}
    private static final class Rig implements PetModelPreviews.Rig {
        private final UUID id;
        private final IStateMachineHandler handler;
        private String clip="";
        private boolean closed;
        Rig(Slime shell,ActiveModel model){id=shell.getUniqueId();handler=(IStateMachineHandler)model.getAnimationHandler();}
        @Override public void animate(String next) {
            if(closed)return;
            if(!clip.isEmpty())handler.forceStopAnimation(10,clip);
            if(handler.playAnimation(10,next,.12,.12,1,true)==null)throw new IllegalStateException("Pet animation missing: "+next);
            clip=next;
        }
        @Override public void close(){if(!closed){closed=true;ModelEngineAPI.removeModeledEntity(id);}}
    }
}
