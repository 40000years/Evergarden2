package com.example.voidscape.pet;

import com.example.voidscape.VoidscapePlugin;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Keep optional pet imports from displacing the existing boss and ME internal rigs. */
public final class PetBlueprintStaging {
    public record Summary(int active,int staged,int custom) {
        public String description(){return active+" active pet blueprints / "+staged+" in library / "+custom+" administrator edits preserved";}
    }
    public static Summary prepare(VoidscapePlugin plugin,Path engineFolder)throws IOException {
        boolean enabled=plugin.getConfig().getBoolean("pets.model-preview.enabled",true);
        boolean all=enabled&&plugin.getConfig().getBoolean("pets.model-preview.install-all-models",false);
        Set<String> selected=new HashSet<>();
        if(enabled)for(String name:plugin.getConfig().getStringList("pets.model-preview.install-species")) {
            var species=PetCatalog.Species.find(name);
            if(species==null)plugin.getLogger().warning("Unknown pets.model-preview.install-species entry: "+name);
            else if(species.mythical())plugin.getLogger().warning("pets.model-preview.install-species entry "+name+" is mythical and has no model yet");
            else for(var stage:PetCatalog.Stage.values())selected.add(species.model(stage));
        }
        int active=0,staged=0,custom=0;
        Path folder=plugin.getDataFolder().toPath().resolve("modelengine/pets");
        for(String id:PetCatalog.modelIds())try(var input=plugin.getResource("modelengine/pets/"+id+".bbmodel")) {
            if(input==null)throw new IOException("Missing bundled pet blueprint "+id);
            byte[] bundled=input.readAllBytes();
            Path library=folder.resolve("library/"+id+".bbmodel");
            // The editable library exists even when no pet is actively imported.
            PetBlueprintInstaller.install(library,folder.resolve("library-ownership/"+id+".sha256"),
                folder.resolve("library-ownership/"+id+".portable.sha256"),bundled);
            Path blueprint=engineFolder.resolve("blueprints/"+id+".bbmodel");
            Path marker=folder.resolve(id+".sha256"),portable=engineFolder.resolve("evergarden-pets/"+id+".sha256");
            if(all||selected.contains(id)) {
                var result=PetBlueprintInstaller.install(blueprint,marker,portable,bundled);active++;
                if(!result.bundled())custom++;
            } else {
                staged++;
                if(Files.exists(blueprint)) {
                    byte[] previous=Files.readAllBytes(blueprint);String hash=PetBlueprintInstaller.digest(previous);
                    if(hash.equals(PetBlueprintInstaller.digest(bundled))||hash.equals(recorded(marker))||hash.equals(recorded(portable))) {
                        park(blueprint,library,folder.resolve("library/previous/"+id+"-"+hash+".bbmodel"),previous);
                    } else {
                        custom++;active++;
                        plugin.getLogger().warning("Keeping administrator-edited active pet blueprint: "+blueprint
                            +". It still uses a ModelEngine import slot; move it out of blueprints manually if not needed.");
                    }
                }
            }
        }
        return new Summary(active,staged,custom);
    }
    private static String recorded(Path path)throws IOException{return Files.exists(path)?Files.readString(path).trim():"";}
    static void park(Path active,Path library,Path archive,byte[] previous)throws IOException {
        // Confirm a byte-identical copy before removing a managed active import.
        Path retained=Files.exists(library)&&Arrays.equals(Files.readAllBytes(library),previous)?library:archive;
        if(!Files.exists(retained)) {
            Files.createDirectories(retained.getParent());Files.write(retained,previous,StandardOpenOption.CREATE_NEW);
        }
        if(!Arrays.equals(Files.readAllBytes(retained),previous))throw new IOException("Pet library backup differs: "+retained);
        if(!Arrays.equals(Files.readAllBytes(active),previous))throw new IOException("Pet blueprint changed during staging: "+active);
        Files.delete(active);
    }
    private PetBlueprintStaging(){}
}
