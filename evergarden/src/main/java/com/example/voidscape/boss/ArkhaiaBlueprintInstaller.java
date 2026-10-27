package com.example.voidscape.boss;

import java.io.IOException;
import java.nio.file.*;
import java.security.*;
import java.util.*;

/** Recognize published rigs even when the Evergarden data folder was reset. */
final class ArkhaiaBlueprintInstaller {
    private static final Set<String> RELEASED=Set.of(
        "edfd01ca07de66c3171fd4f349e92699070773de36e3d24ad21f717a53291b46", // e2.62
        "34d7b37d7c6793f311e788b700307e263f37b8d2b82dae1075326d4d89940de0", // e2.63
        "b2d91574ac38f292b47d7307e48411474fe5aecb132c199ebb297ffd9ae6027e", // e2.64
        "9116d4ed11cdd2018c5df1bc7e5d4bb3d41c69330a0499cf9d30b492d2d3032a", // e2.66
        "86967958869d9cc3c1f15659b453d4d3d4de652209a11d5907784f9150a48405", // e2.67-68
        "80a8895a54dd8587fb2bdd8a138293bc8332300e5e0dd994799cd1b52581f782"  // e2.69-e2.115 articulated v6
    );
    record Result(boolean bundled,boolean upgraded,String hash,Path backup) {}

    static Result install(Path blueprint,Path marker,Path engineMarker,byte[] bundled)throws IOException{
        String next=digest(bundled);boolean upgraded=false;Path backup=null;
        Files.createDirectories(blueprint.getParent());
        if(Files.exists(blueprint)){
            byte[] existing=Files.readAllBytes(blueprint);String previous=digest(existing);
            if(!previous.equals(next)){
                boolean ours=RELEASED.contains(previous)||previous.equals(recorded(marker))||previous.equals(recorded(engineMarker));
                if(!ours)return new Result(false,false,previous,null);
                backup=marker.getParent().resolve("backups/arkhaia-"+previous+".bbmodel");
                Files.createDirectories(backup.getParent());
                if(!Files.exists(backup))Files.write(backup,existing,StandardOpenOption.CREATE_NEW);
                else if(!Arrays.equals(Files.readAllBytes(backup),existing))throw new IOException("Arkhaia backup differs from the original: "+backup);
                Path temp=Files.createTempFile(blueprint.getParent(),"arkhaia-",".tmp");
                try{Files.write(temp,bundled);Files.move(temp,blueprint,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
                finally{Files.deleteIfExists(temp);}
                upgraded=true;
            }
        }else Files.write(blueprint,bundled,StandardOpenOption.CREATE_NEW);
        // Keep a second ownership record alongside ModelEngine. It survives
        // resetting only the Evergarden directory, a normal pack reinstall.
        for(Path record:List.of(marker,engineMarker)){
            Files.createDirectories(record.getParent());Files.writeString(record,next);
        }
        return new Result(true,upgraded,next,backup);
    }
    private static String recorded(Path marker)throws IOException{return Files.exists(marker)?Files.readString(marker).trim():"";}
    static String digest(byte[] data)throws IOException{
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));}
        catch(NoSuchAlgorithmException impossible){throw new IOException(impossible);}
    }
}
