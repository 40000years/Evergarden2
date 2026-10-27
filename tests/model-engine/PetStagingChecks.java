package com.example.voidscape.pet;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Optional pet staging may remove an import only after retaining identical bytes. */
public final class PetStagingChecks {
    static int checks;
    static void check(boolean value,String label){if(!value)throw new AssertionError(label);checks++;}
    record Fixture(Path active,Path library,Path archive){}
    static Fixture fixture(Path root,String name)throws IOException {
        Path dir=root.resolve(name);Files.createDirectories(dir);
        return new Fixture(dir.resolve("ModelEngine/blueprints/everpet_fenrir_baby.bbmodel"),
            dir.resolve("Evergarden/modelengine/pets/library/everpet_fenrir_baby.bbmodel"),
            dir.resolve("Evergarden/modelengine/pets/library/previous/everpet_fenrir_baby-oldhash.bbmodel"));
    }
    static void write(Path file,byte[] bytes)throws IOException{Files.createDirectories(file.getParent());Files.write(file,bytes);}
    static void park(Fixture fixture,byte[] previous)throws IOException{PetBlueprintStaging.park(fixture.active,fixture.library,fixture.archive,previous);}
    static boolean refused(Fixture fixture,byte[] previous)throws IOException {
        try{park(fixture,previous);return false;}catch(IOException expected){return true;}
    }
    public static void main(String[] args)throws Exception {
        Path root=Path.of(args[0]);Files.createDirectories(root);
        byte[] previous="managed previous pet\u0000\u00ff".getBytes(StandardCharsets.UTF_8);
        byte[] current="bundled current pet".getBytes(StandardCharsets.UTF_8);
        byte[] edited="administrator pet edit".getBytes(StandardCharsets.UTF_8);
        Fixture exact=fixture(root,"exact-library");write(exact.active,previous);write(exact.library,previous);
        park(exact,previous);
        check(!Files.exists(exact.active),"managed active import removed with exact library copy");
        check(Arrays.equals(previous,Files.readAllBytes(exact.library)),"library retains exact binary bytes");
        check(!Files.exists(exact.archive),"identical library needs no duplicate archive");
        Fixture old=fixture(root,"older-managed");write(old.active,previous);write(old.library,current);
        park(old,previous);
        check(!Files.exists(old.active),"older managed import removed only after archiving");
        check(Arrays.equals(previous,Files.readAllBytes(old.archive)),"exact older managed bytes archived");
        check(Arrays.equals(current,Files.readAllBytes(old.library)),"new library version preserved");
        Fixture backup=fixture(root,"existing-backup");write(backup.active,previous);write(backup.library,current);write(backup.archive,previous);
        park(backup,previous);
        check(!Files.exists(backup.active)&&Arrays.equals(previous,Files.readAllBytes(backup.archive)),"verified existing archive permits safe retry");
        Fixture conflict=fixture(root,"conflicting-backup");write(conflict.active,previous);write(conflict.library,current);write(conflict.archive,edited);
        check(refused(conflict,previous),"conflicting archive refuses destructive staging");
        check(Arrays.equals(previous,Files.readAllBytes(conflict.active)),"conflicting archive leaves active untouched");
        check(Arrays.equals(edited,Files.readAllBytes(conflict.archive)),"conflicting archive is never overwritten");
        Fixture changed=fixture(root,"changed-active");write(changed.active,edited);write(changed.library,previous);
        check(refused(changed,previous),"active file changed since inspection refuses removal");
        check(Arrays.equals(edited,Files.readAllBytes(changed.active)),"administrator concurrent edit preserved");
        Fixture noLibrary=fixture(root,"no-library");write(noLibrary.active,previous);
        park(noLibrary,previous);
        check(!Files.exists(noLibrary.active)&&Arrays.equals(previous,Files.readAllBytes(noLibrary.archive)),"missing library still retains exact archive before delete");
        System.out.println("PASS "+checks+" pet staging byte preservation and failure safety checks");
    }
}
