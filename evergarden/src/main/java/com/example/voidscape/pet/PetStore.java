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
import java.util.concurrent.ExecutionException;

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
    /** Initial capacity. Extra slots are permanent and the chest menu supports multiple pages. */
    int defaultSlots(){return plugin.integer("pets.stable.slots",6,1,28);}
    /** Slots each pet raised from a baby to adulthood opens, and the most raising can reach. */
    private PetStable sized(PetStable stable){
        stable.perRaised=plugin.integer("pets.stable.raised-adult-slots",2,0,28);
        int limit=plugin.integer("pets.stable.max-slots",0,0,Integer.MAX_VALUE);
        stable.maxSlots=limit==0?Integer.MAX_VALUE:limit;
        return stable;
    }
    boolean eggsLocked(){return eggsLocked;}
    /** Null when the file exists but cannot be read; a missing file is simply empty. */
    private YamlConfiguration read(Path file){
        YamlConfiguration yaml=new YamlConfiguration();
        String queued=pending.get(file);
        if(queued==null&&Files.notExists(file))return yaml;
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
            if(yaml==null){PetStable locked=sized(new PetStable(id,defaultSlots()));locked.locked=true;return locked;}
            PetStable loaded=sized(PetStable.load(id,yaml,defaultSlots()));
            if(loaded.locked)plugin.getLogger().severe("Invalid pet record in "+players.resolve(id+".yml")+"; the stable is locked to preserve the original file");
            recoverCollections(loaded);
            recoverDeliveries(loaded);
            return loaded;
        });
    }
    /** Spent eggs older than a week leave the ledger; their items are already gone. */
    void pruneEggs(){
        var section=eggs.getConfigurationSection("eggs");if(section==null||eggsLocked)return;
        long cutoff=System.currentTimeMillis()-7*86_400_000L;
        for(String id:new ArrayList<>(section.getKeys(false)))
            if(section.getBoolean(id+".hatched")&&!section.getBoolean(id+".delivery-pending")&&section.getLong(id+".hatched-at")<cutoff){section.set(id,null);eggsDirty=true;}
    }
    PetStable cached(UUID owner){return stables.get(owner);}
    Collection<PetStable> loaded(){return stables.values();}
    void save(PetStable stable){
        if(!stable.dirty||stable.locked)return;
        stable.dirty=false;write(players.resolve(stable.owner+".yml"),stable.serialize());
    }
    void saveEggs(){if(eggsDirty&&!eggsLocked){eggsDirty=false;write(eggsFile,eggs.saveToString());}}
    void flush(){stables.values().forEach(this::save);saveEggs();new HashMap<>(pending).forEach(this::write);}
    void unload(UUID owner){PetStable stable=stables.get(owner);if(stable!=null)save(stable);}
    private void write(Path file,String text){
        pending.put(file,text);
        try{io.execute(()->writeSnapshot(file,text));}
        catch(java.util.concurrent.RejectedExecutionException closed){plugin.getLogger().severe("Pet data written after shutdown was skipped: "+file);}
    }
    private boolean writeSnapshot(Path file,String text){
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
                return true;
            }catch(IOException error){plugin.getLogger().severe("Cannot save pet data "+file+": "+error);return false;}
    }
    /** Wait behind older queued writes for critical ownership transactions only. */
    private boolean writeNow(Path file,String text){
        pending.put(file,text);
        try{return io.submit(()->writeSnapshot(file,text)).get();}
        catch(InterruptedException interrupted){Thread.currentThread().interrupt();plugin.getLogger().severe("Interrupted saving pet data "+file);return false;}
        catch(ExecutionException|java.util.concurrent.RejectedExecutionException error){plugin.getLogger().severe("Cannot save pet data "+file+": "+error);return false;}
    }
    boolean saveEggsNow(){
        if(eggsLocked)return false;
        boolean saved=writeNow(eggsFile,eggs.saveToString());eggsDirty=!saved;return saved;
    }
    boolean saveStableNow(PetStable stable){
        if(stable.locked)return false;
        boolean saved=writeNow(players.resolve(stable.owner+".yml"),stable.serialize());stable.dirty=!saved;return saved;
    }
    /** Finish the nest debit before releasing its egg; the stable receipt makes replay idempotent. */
    boolean finishCollection(PetStable stable,String id,org.bukkit.configuration.ConfigurationSection egg){
        if(stable.locked||eggsLocked)return false;
        if(!egg.getBoolean("collection-pending"))return true;
        if(!stable.owner.toString().equals(egg.getString("owner")))return false;
        if(!id.equals(stable.lastEggCollected)){
            UUID a=PetStable.uuid(egg.getString("source.a")),b=PetStable.uuid(egg.getString("source.b"));
            PetRecord first=stable.get(a),second=stable.get(b);long created=egg.getLong("created");
            if(first==null||second==null||a.equals(b)||created<=0||stable.nestBusy()
                &&(!a.equals(stable.nestA)||!b.equals(stable.nestB)||stable.nestReady!=egg.getLong("source.ready"))){
                stable.locked=true;plugin.getLogger().severe("Invalid pending nest collection "+id+"; stable locked for administrator repair");return false;
            }
            first.bred=Math.max(first.bred,created);second.bred=Math.max(second.bred,created);
            // An old collection recovered after midnight must not move today's counter backwards.
            if(stable.eggDay<=Math.floorDiv(created,86_400_000L)){stable.eggsToday(created);stable.eggsToday++;}
            stable.clearNest();stable.lastEggCollected=id;stable.dirty=true;
        }
        // Also retry a prior in-memory debit whose stable write failed.
        if(!saveStableNow(stable)){stable.locked=true;return false;}
        egg.set("collection-pending",false);eggsDirty=true;
        if(!saveEggsNow()){egg.set("collection-pending",true);eggsDirty=true;stable.locked=true;return false;}
        return true;
    }
    private void recoverCollections(PetStable stable){
        if(stable.locked||eggsLocked)return;
        var all=eggs.getConfigurationSection("eggs");if(all==null)return;
        for(String id:all.getKeys(false)){
            var egg=all.getConfigurationSection(id);
            if(egg==null||!egg.getBoolean("collection-pending")||!stable.owner.toString().equals(egg.getString("owner")))continue;
            if(!finishCollection(stable,id,egg))return;
            plugin.getLogger().info("Recovered pending pet nest collection "+id+" for "+stable.owner);
        }
    }
    /** Recover only unfinished hatch deliveries; committed pets may later be released normally. */
    private void recoverDeliveries(PetStable stable){
        if(stable.locked||eggsLocked)return;
        var all=eggs.getConfigurationSection("eggs");if(all==null)return;
        for(String id:all.getKeys(false)){
            var egg=all.getConfigurationSection(id);
            if(egg==null||!egg.getBoolean("delivery-pending")||!stable.owner.toString().equals(egg.getString("hatched-by")))continue;
            UUID petId=PetStable.uuid(egg.getString("pet-id"));var data=egg.getConfigurationSection("pet");
            PetRecord child=petId==null||data==null?null:PetRecord.load(petId,data);
            if(child==null){stable.locked=true;plugin.getLogger().severe("Invalid pending hatch "+id+"; stable locked for administrator repair");return;}
            if(stable.get(petId)==null){stable.add(child);if(!saveStableNow(stable)){stable.locked=true;return;}}
            egg.set("delivery-pending",false);eggsDirty=true;
            if(!saveEggsNow()){egg.set("delivery-pending",true);eggsDirty=true;stable.locked=true;return;}
            plugin.getLogger().info("Recovered pending pet hatch "+id+" for "+stable.owner);
        }
    }
    /** Journal, then stable, then finalize: recovery can replay safely at every boundary. */
    boolean deliverEgg(PetStable stable,org.bukkit.configuration.ConfigurationSection egg,PetRecord child){
        if(stable.locked||eggsLocked)return false;
        // The hatcher raises it from a baby; the journal keeps that if the delivery must be recovered.
        child.born=System.currentTimeMillis();child.raisedBy=stable.owner;child.save(egg.getConfigurationSection("pet"));
        egg.set("hatched",true);egg.set("hatched-by",stable.owner.toString());egg.set("hatched-at",child.born);egg.set("delivery-pending",true);
        eggsDirty=true;
        if(!saveEggsNow()){stable.locked=true;return false;}
        stable.add(child);
        if(!saveStableNow(stable)){stable.locked=true;return false;}
        egg.set("delivery-pending",false);eggsDirty=true;
        if(!saveEggsNow()){egg.set("delivery-pending",true);eggsDirty=true;stable.locked=true;return false;}
        return true;
    }
    @Override public void close(){
        flush();io.shutdown();
        try{if(!io.awaitTermination(10,TimeUnit.SECONDS))plugin.getLogger().severe("Pet data writes did not finish within 10 seconds");}
        catch(InterruptedException interrupted){Thread.currentThread().interrupt();}
    }
}
