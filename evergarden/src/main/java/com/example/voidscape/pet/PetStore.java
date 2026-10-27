package com.example.voidscape.pet;

import com.example.voidscape.VoidscapePlugin;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Stable files ({@code pets/players/<uuid>.yml}) and the egg ledger ({@code pets/eggs.yml}).
 * Snapshots are taken on the server thread and written in order on one background thread
 * through a temporary file and an atomic replace, so saving never stalls a tick.
 */
final class PetStore implements AutoCloseable {
    private final VoidscapePlugin plugin;
    private final Path players,eggsFile;
    private final ExecutorService io=Executors.newSingleThreadExecutor(task->{
        Thread thread=new Thread(task,"Evergarden-pet-store");thread.setDaemon(true);return thread;
    });
    private final Map<UUID,PetStable> stables=new HashMap<>();
    /** Text queued for the writer, so a quick relog reads what is about to be on disk. */
    private final Map<Path,String> pending=new java.util.concurrent.ConcurrentHashMap<>();
    final YamlConfiguration eggs;
    /** A ledger that could not be read is never overwritten. */
    private final boolean eggsLocked;
    boolean eggsDirty;

    PetStore(VoidscapePlugin plugin){
        this.plugin=plugin;
        Path root=plugin.getDataFolder().toPath().resolve("pets");players=root.resolve("players");eggsFile=root.resolve("eggs.yml");
        YamlConfiguration ledger=read(eggsFile);
        eggsLocked=ledger==null;eggs=ledger==null?new YamlConfiguration():ledger;
        if(eggsLocked)plugin.getLogger().severe("Egg ledger unreadable; eggs cannot be laid or hatched until "+eggsFile+" is repaired");
        pruneEggs();
    }
    /** The chest menu shows at most 28 pets, so the stable never grows beyond that. */
    int defaultSlots(){return plugin.integer("pets.stable.slots",6,1,28);}
    boolean eggsLocked(){return eggsLocked;}
    /** Null when the file exists but cannot be read; a missing file is simply empty. */
    private YamlConfiguration read(Path file){
        YamlConfiguration yaml=new YamlConfiguration();
        String queued=pending.get(file);
        if(queued==null&&!Files.isRegularFile(file))return yaml;
        IOException last=null;
        for(int attempt=0;attempt<3;attempt++){
            try{yaml.loadFromString(queued!=null?queued:Files.readString(file,StandardCharsets.UTF_8));return yaml;}
            catch(InvalidConfigurationException broken){break;}
            // A Windows reader can collide with the writer's replace; it settles within moments.
            catch(IOException busy){last=busy;try{Thread.sleep(25);}catch(InterruptedException stop){Thread.currentThread().interrupt();break;}}
        }
        plugin.getLogger().severe("Cannot read "+file+(last==null?" (invalid YAML)":": "+last)+"; it will not be overwritten");
        try{Files.copy(file,file.resolveSibling(file.getFileName()+".broken-"+System.currentTimeMillis()));}catch(IOException ignored){}
        return null;
    }
    /** Stables stay cached for the session: a relog finds the live object, never a stale file. */
    PetStable stable(UUID owner){
        return stables.computeIfAbsent(owner,id->{
            YamlConfiguration yaml=read(players.resolve(id+".yml"));
            if(yaml==null){PetStable locked=new PetStable(id,defaultSlots());locked.locked=true;return locked;}
            return PetStable.load(id,yaml,defaultSlots());
        });
    }
    /** Spent eggs older than a week leave the ledger; their items are already gone. */
    void pruneEggs(){
        var section=eggs.getConfigurationSection("eggs");if(section==null||eggsLocked)return;
        long cutoff=System.currentTimeMillis()-7*86_400_000L;
        for(String id:new ArrayList<>(section.getKeys(false)))
            if(section.getBoolean(id+".hatched")&&section.getLong(id+".hatched-at")<cutoff){section.set(id,null);eggsDirty=true;}
    }
    PetStable cached(UUID owner){return stables.get(owner);}
    Collection<PetStable> loaded(){return stables.values();}
    void save(PetStable stable){
        if(!stable.dirty||stable.locked)return;
        stable.dirty=false;write(players.resolve(stable.owner+".yml"),stable.serialize());
    }
    void saveEggs(){if(eggsDirty&&!eggsLocked){eggsDirty=false;write(eggsFile,eggs.saveToString());}}
    void flush(){stables.values().forEach(this::save);saveEggs();}
    void unload(UUID owner){PetStable stable=stables.get(owner);if(stable!=null)save(stable);}
    private void write(Path file,String text){
        pending.put(file,text);
        try{io.execute(()->{
            try{
                Files.createDirectories(file.getParent());
                Path temporary=file.resolveSibling(file.getFileName()+".tmp");
                Files.writeString(temporary,text,StandardCharsets.UTF_8);
                for(int attempt=0;;attempt++){
                    try{
                        try{Files.move(temporary,file,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
                        catch(AtomicMoveNotSupportedException unsupported){Files.move(temporary,file,StandardCopyOption.REPLACE_EXISTING);}
                        break;
                    }catch(AccessDeniedException busy){
                        // Windows refuses the replace while something reads the file; retry briefly.
                        if(attempt>=20)throw busy;
                        try{Thread.sleep(50);}catch(InterruptedException stop){Thread.currentThread().interrupt();throw busy;}
                    }
                }
                pending.remove(file,text);
            }catch(IOException error){plugin.getLogger().severe("Cannot save pet data "+file+": "+error);}
        });}catch(java.util.concurrent.RejectedExecutionException closed){plugin.getLogger().severe("Pet data written after shutdown was skipped: "+file);}
    }
    @Override public void close(){
        flush();io.shutdown();
        try{if(!io.awaitTermination(10,TimeUnit.SECONDS))plugin.getLogger().severe("Pet data writes did not finish within 10 seconds");}
        catch(InterruptedException interrupted){Thread.currentThread().interrupt();}
    }
}
