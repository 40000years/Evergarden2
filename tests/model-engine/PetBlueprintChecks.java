package com.example.voidscape.pet;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Managed pet assets must preserve administrator edits and exact upgrade backups. */
public final class PetBlueprintChecks {
    static int checks;
    static void check(boolean value,String label){if(!value)throw new AssertionError(label);checks++;}
    record Fixture(Path model,Path marker,Path portable){}
    static Fixture fixture(Path root,String name)throws IOException{
        Path dir=root.resolve(name);Files.createDirectories(dir);
        return new Fixture(dir.resolve("ModelEngine/blueprints/everpet_fenrir_baby.bbmodel"),dir.resolve("Evergarden/modelengine/pets/everpet_fenrir_baby.sha256"),dir.resolve("ModelEngine/evergarden-pets/everpet_fenrir_baby.sha256"));
    }
    static void write(Path file,byte[] bytes)throws IOException{Files.createDirectories(file.getParent());Files.write(file,bytes);}
    static PetBlueprintInstaller.Result install(Fixture fixture,byte[] bytes)throws IOException{return PetBlueprintInstaller.install(fixture.model,fixture.marker,fixture.portable,bytes);}
    public static void main(String[] args)throws Exception{
        Path root=Path.of(args[0]);Files.createDirectories(root);
        byte[] previous="managed previous pet".getBytes(),next="bundled current pet".getBytes(),edited="administrator pet edit".getBytes();
        Fixture fresh=fixture(root,"fresh");var result=install(fresh,previous);
        check(result.bundled()&&!result.upgraded(),"fresh blueprint installed");
        check(Arrays.equals(previous,Files.readAllBytes(fresh.model)),"fresh bytes exact");
        for(Path marker:List.of(fresh.marker,fresh.portable))check(Files.readString(marker).equals(PetBlueprintInstaller.digest(previous)),"dual ownership marker");
        result=install(fresh,previous);check(result.bundled()&&!result.upgraded(),"unchanged model needs no backup");
        for(boolean portable:List.of(false,true)){
            Fixture f=fixture(root,portable?"portable":"primary");write(f.model,previous);
            write(portable?f.portable:f.marker,PetBlueprintInstaller.digest(previous).getBytes());
            result=install(f,next);check(result.bundled()&&result.upgraded(),"either ownership marker permits upgrade");
            check(Arrays.equals(next,Files.readAllBytes(f.model)),"upgrade bytes exact");
            Path backup=f.marker.getParent().resolve("backups/"+f.model.getFileName()+"-"+PetBlueprintInstaller.digest(previous)+".bbmodel");
            check(Arrays.equals(previous,Files.readAllBytes(backup)),"exact prior version backed up");
        }
        for(boolean stale:List.of(false,true)){
            Fixture f=fixture(root,stale?"edited-stale":"edited");write(f.model,edited);
            if(stale){write(f.marker,PetBlueprintInstaller.digest(previous).getBytes());write(f.portable,PetBlueprintInstaller.digest(previous).getBytes());}
            result=install(f,next);check(!result.bundled()&&!result.upgraded(),"administrator edit preserved");
            check(Arrays.equals(edited,Files.readAllBytes(f.model)),"administrator bytes unchanged");
            if(!stale)check(!Files.exists(f.marker)&&!Files.exists(f.portable),"administrator model never marked managed");
        }
        Fixture corrupt=fixture(root,"bad-backup");write(corrupt.model,previous);write(corrupt.marker,PetBlueprintInstaller.digest(previous).getBytes());
        Path backup=corrupt.marker.getParent().resolve("backups/"+corrupt.model.getFileName()+"-"+PetBlueprintInstaller.digest(previous)+".bbmodel");write(backup,edited);
        boolean refused=false;try{install(corrupt,next);}catch(IOException expected){refused=true;}
        check(refused&&Arrays.equals(previous,Files.readAllBytes(corrupt.model)),"conflicting backup prevents destructive replacement");
        check(PetCatalog.modelIds().size()==51&&new HashSet<>(PetCatalog.modelIds()).size()==51,"51 distinct species/stage identifiers");
        System.out.println("PASS "+checks+" pet blueprint ownership, upgrade and catalog checks");
    }
}
