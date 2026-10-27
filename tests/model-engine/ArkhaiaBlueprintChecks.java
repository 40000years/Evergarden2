package com.example.voidscape.boss;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Regression: reinstalling Evergarden must not silently keep a released old rig. */
public final class ArkhaiaBlueprintChecks {
    static int checks;
    static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);checks++;}
    record Fixture(Path model,Path primary,Path secondary) {}
    static Fixture fixture(Path root,String name)throws IOException{
        Path dir=root.resolve(name);Files.createDirectories(dir);
        return new Fixture(dir.resolve("ModelEngine/blueprints/arkhaia.bbmodel"),
            dir.resolve("Evergarden/modelengine/arkhaia.sha256"),dir.resolve("ModelEngine/arkhaia-evergarden.sha256"));
    }
    static void write(Path path,byte[] bytes)throws IOException{Files.createDirectories(path.getParent());Files.write(path,bytes);}
    static ArkhaiaBlueprintInstaller.Result install(Fixture f,byte[] next)throws IOException{
        return ArkhaiaBlueprintInstaller.install(f.model,f.primary,f.secondary,next);
    }
    static void markers(Fixture f,String hash)throws IOException{
        check(Files.readString(f.primary).equals(hash),"primary ownership marker restored");
        check(Files.readString(f.secondary).equals(hash),"portable ownership marker restored");
    }
    public static void main(String[] args)throws Exception{
        byte[] next=Files.readAllBytes(Path.of(args[0])),legacy=Files.readAllBytes(Path.of(args[1]));
        Path root=Path.of(args[2]);Files.createDirectory(root);String hash=ArkhaiaBlueprintInstaller.digest(next);
        Fixture missing=fixture(root,"released-without-markers");write(missing.model,legacy);
        var result=install(missing,next);
        check(result.bundled()&&result.upgraded(),"released e2.63 rig upgrades with both ownership files missing");
        check(Arrays.equals(Files.readAllBytes(missing.model),next),"active blueprint matches bundled model");
        check(Arrays.equals(Files.readAllBytes(result.backup()),legacy),"released rig is backed up byte-for-byte");markers(missing,hash);

        byte[] intermediate="managed intermediate rig".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        for(boolean portable:List.of(false,true)){
            Fixture f=fixture(root,portable?"primary-marker-lost":"previous-primary-only");write(f.model,intermediate);
            write(portable?f.secondary:f.primary,ArkhaiaBlueprintInstaller.digest(intermediate).getBytes());
            result=install(f,next);check(result.upgraded(),"previous ownership survives loss of the other marker");
            check(Arrays.equals(Files.readAllBytes(result.backup()),intermediate),"managed intermediate backed up");markers(f,hash);
        }
        byte[] edited="administrator custom rig".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        for(boolean stale:List.of(false,true)){
            Fixture f=fixture(root,stale?"custom-with-stale-markers":"custom-without-markers");write(f.model,edited);
            if(stale){write(f.primary,hash.getBytes());write(f.secondary,hash.getBytes());}
            result=install(f,next);check(!result.bundled()&&!result.upgraded(),"unknown administrator edit preserved");
            check(Arrays.equals(Files.readAllBytes(f.model),edited),"custom file not changed");
            check(result.backup()==null,"preserved custom file not relabeled as managed");
            if(!stale)check(!Files.exists(f.primary)&&!Files.exists(f.secondary),"custom file does not acquire ownership markers");
        }
        Fixture same=fixture(root,"current-without-markers");write(same.model,next);
        result=install(same,next);check(result.bundled()&&!result.upgraded(),"current rig remains unchanged");markers(same,hash);
        Fixture fresh=fixture(root,"fresh-install");result=install(fresh,next);
        check(result.bundled()&&!result.upgraded(),"fresh install succeeds");
        check(Arrays.equals(Files.readAllBytes(fresh.model),next),"fresh model installed");markers(fresh,hash);
        Fixture corrupt=fixture(root,"conflicting-backup");write(corrupt.model,legacy);
        Path backup=corrupt.primary.getParent().resolve("backups/arkhaia-"+ArkhaiaBlueprintInstaller.digest(legacy)+".bbmodel");write(backup,edited);
        boolean refused=false;try{install(corrupt,next);}catch(IOException expected){refused=true;}
        check(refused,"conflicting backup prevents replacement");
        check(Arrays.equals(Files.readAllBytes(corrupt.model),legacy),"failed backup leaves original intact");
        System.out.println("PASS "+checks+" blueprint upgrade checks (missing markers, portable ownership, custom edits, backup integrity)");
    }
}
