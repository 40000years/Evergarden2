package com.example.voidscape.pet;

import java.io.IOException;
import java.nio.file.*;
import java.security.*;
import java.util.*;

/** Never overwrite administrator-edited blueprints; retain exact originals on upgrades. */
final class PetBlueprintInstaller {
    record Result(boolean bundled, boolean upgraded, String hash) {}
    static Result install(Path blueprint, Path marker, Path engineMarker, byte[] bundled)throws IOException {
        String next=digest(bundled);boolean upgraded=false;
        Files.createDirectories(blueprint.getParent());
        if(Files.exists(blueprint)) {
            byte[] previousBytes=Files.readAllBytes(blueprint);String previous=digest(previousBytes);
            if(!previous.equals(next)) {
                if(!previous.equals(recorded(marker))&&!previous.equals(recorded(engineMarker)))
                    return new Result(false,false,previous);
                Path backup=marker.getParent().resolve("backups/"+blueprint.getFileName()+"-"+previous+".bbmodel");
                Files.createDirectories(backup.getParent());
                if(!Files.exists(backup))Files.write(backup,previousBytes,StandardOpenOption.CREATE_NEW);
                else if(!Arrays.equals(previousBytes,Files.readAllBytes(backup)))throw new IOException("Pet blueprint backup differs: "+backup);
                Path temp=Files.createTempFile(blueprint.getParent(),"everpet-",".tmp");
                try {
                    Files.write(temp,bundled);
                    try {Files.move(temp,blueprint,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
                    catch(AtomicMoveNotSupportedException unavailable) {Files.move(temp,blueprint,StandardCopyOption.REPLACE_EXISTING);}
                } finally {Files.deleteIfExists(temp);}
                upgraded=true;
            }
        } else Files.write(blueprint,bundled,StandardOpenOption.CREATE_NEW);
        for(Path record:List.of(marker,engineMarker)) {
            Files.createDirectories(record.getParent());Files.writeString(record,next);
        }
        return new Result(true,upgraded,next);
    }
    private static String recorded(Path marker)throws IOException {return Files.exists(marker)?Files.readString(marker).trim():"";}
    static String digest(byte[] bytes)throws IOException {
        try {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
        catch(NoSuchAlgorithmException impossible) {throw new IOException(impossible);}
    }
}
