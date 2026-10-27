import com.example.voidscape.pet.*;
import com.google.gson.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Real Paper passenger teleports plus the entity rotation applied by the client's DisplayRenderer. */
public final class PetFacingNativeProbe extends PetSystemNativeProbe {
    private JsonObject models;
    private static final float[] HEADINGS={0,45,90,135,179,-179,-135,-90,-45,0};

    @Override String suiteName(){return "pet facing and forward travel";}
    @Override void awaitSeraph(){later(1,()->{try{runFacing();}catch(Throwable failure){finish(failure);}});}

    private ItemDisplay bodyPart(Object body)throws Exception{
        PetCatalog.Species kind=(PetCatalog.Species)get(body,"species");
        PetCatalog.Stage stage=(PetCatalog.Stage)get(body,"stage");
        var definition=models.getAsJsonObject(kind.id+"_"+stage.id);
        String item="";
        for(var value:definition.getAsJsonArray("bones")){
            var bone=value.getAsJsonObject();
            if(bone.get("name").getAsString().equals("body")){item=bone.get("item").getAsString();break;}
        }
        String key=item;
        var rig=(NativePetRenderer.Rig)get(body,"rig");
        return rig.displays().stream().filter(d->d.getItemStack().getItemMeta().getItemModel().getKey().equals(key)).findFirst().orElseThrow();
    }

    private Vector3f visibleForward(ItemDisplay part){
        Location at=part.getLocation();Transformation pose=part.getTransformation();
        // FIXED billboard: world entity yaw/pitch precede the sculpture transform.
        // ItemDisplayRenderer's final Y(pi) follows the right rotation.
        Quaternionf frame=new Quaternionf().rotationYXZ((float)Math.toRadians(-at.getYaw()),
            (float)Math.toRadians(at.getPitch()),0).mul(pose.getLeftRotation())
            .mul(pose.getRightRotation()).rotateY((float)Math.PI);
        Vector3f front=frame.transform(new Vector3f(0,0,-1));front.y=0;return front.normalize();
    }

    private void verifyFrame(Object body,String label)throws Exception{
        var rig=(NativePetRenderer.Rig)get(body,"rig");
        for(ItemDisplay part:rig.displays()){
            Location at=part.getLocation();
            check(Math.abs(wrap(at.getYaw()))<.01&&Math.abs(at.getPitch())<.01,
                label+": passenger display retains neutral entity rotation, actual yaw="+at.getYaw());
        }
        float yaw=(float)get(body,"yaw");double radians=Math.toRadians(yaw);
        Vector3f expected=new Vector3f((float)-Math.sin(radians),0,(float)Math.cos(radians));
        float dot=visibleForward(bodyPart(body)).dot(expected);
        check(dot>.98f,label+": visible body faces travel heading, dot="+dot);
    }

    private void allVariants()throws Exception{
        int variants=0;
        for(PetCatalog.Species kind:PetCatalog.Species.values()){
            if(kind.mythical())continue;
            for(PetCatalog.Stage stage:PetCatalog.Stage.values()){
                garden.wildPets().clear();
                Location spawn=home.clone();spawn.setYaw(47);
                check(garden.wildPets().spawn(kind,stage,spawn),"facing fixture "+kind.model(stage));
                Object body=wildActive().values().iterator().next();
                var rig=(NativePetRenderer.Rig)get(body,"rig");
                UUID shell=((Rabbit)get(body,"shell")).getUniqueId();
                check(rig.riding()&&!rig.displays().isEmpty()&&rig.displays().stream().allMatch(part->
                    part.getVehicle()!=null&&part.getVehicle().getUniqueId().equals(shell)),
                    kind.id+"/"+stage.id+": probe exercises actual mounted parts");
                put(body,"headYaw",0f);put(body,"headPitch",0f);
                for(float heading:HEADINGS){
                    put(body,"yaw",heading);put(body,"targetYaw",heading);
                    double radians=Math.toRadians(heading);
                    Location start=((Rabbit)get(body,"shell")).getLocation();
                    Location to=start.clone().add(-Math.sin(radians)*.45,0,Math.cos(radians)*.45);
                    call(body,"glide",to,3);
                    for(int tick=0;tick<3;tick++){
                        // glide already advances the first step immediately.
                        if(tick>0)call(body,"tickGlide");
                        call(body,"present","walk",1f);
                        verifyFrame(body,kind.id+"/"+stage.id+" turn "+heading+" step "+tick);
                    }
                    Location end=((Rabbit)get(body,"shell")).getLocation();
                    Vector3f moved=new Vector3f((float)(end.getX()-start.getX()),0,(float)(end.getZ()-start.getZ())).normalize();
                    check(visibleForward(bodyPart(body)).dot(moved)>.98,
                        kind.id+"/"+stage.id+": movement follows the rendered front at "+heading);
                }
                // Relocations use the same passenger-safe path, including 180 degree turns.
                put(body,"yaw",180f);call(body,"move",home.clone());call(body,"present","idle");
                verifyFrame(body,kind.id+"/"+stage.id+" relocation");
                variants++;
            }
        }
        check(variants==PetCatalog.wildSpecies()*3,"all "+variants+" mounted growth forms face forward after real teleports");
        garden.wildPets().clear();
    }

    private void retreatingOwner()throws Exception{
        garden.pets().command(Bukkit.getConsoleSender(),new String[]{"pets","give",tamer.getName(),"astral_owl","baby"});
        PetRecord record=records(tamer).values().stream().filter(r->r.species()==PetCatalog.Species.ASTRAL_OWL).findFirst().orElseThrow();
        check(call(companions,"summon",tamer,record.id())==null,"the clip's owl comes out for a retreating owner");
        Object body=body(tamer);put(body,"modeUntil",0);mode(body,"IDLE");
        for(float heading:new float[]{0,90,180,-90,179,-179}){
            double radians=Math.toRadians(heading),x=-Math.sin(radians),z=Math.cos(radians);
            put(body,"yaw",heading);put(body,"targetYaw",heading);put(body,"speed",0d);put(body,"airborne",false);
            call(body,"move",home.clone());
            for(int tick=0;tick<30;tick++){
                Location owner=home.clone().add(x*(6+tick*.15),0,z*(6+tick*.15));owner.setYaw(heading);tamer.teleport(owner);
                Location start=((Rabbit)get(body,"shell")).getLocation();
                call(companions,"follow",body,tamer,true);call(body,"tickGlide");call(body,"tickGlide");
                verifyFrame(body,"retreating owner "+heading+" tick "+tick);
                Location end=((Rabbit)get(body,"shell")).getLocation();
                Vector3f moved=new Vector3f((float)(end.getX()-start.getX()),0,(float)(end.getZ()-start.getZ()));
                if(moved.lengthSquared()>1e-6)check(visibleForward(bodyPart(body)).dot(moved.normalize())>.97,
                    "owl walks toward its visible front while its owner retreats at "+heading);
            }
        }
        call(companions,"dismiss",tamer);
    }

    private void runFacing()throws Exception{
        try(var stream=garden.getResource("native-pets.json")){
            models=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("models");
        }
        allVariants();retreatingOwner();finish(null);
    }
}
