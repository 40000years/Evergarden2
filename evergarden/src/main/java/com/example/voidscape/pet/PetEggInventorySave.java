package com.example.voidscape.pet;

import com.example.voidscape.VoidscapePlugin;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;

/** Confirms that an egg grant reached the player-data file before its journal is committed. */
final class PetEggInventorySave {
    private static final long MAX_PLAYER_NBT_BYTES=16L*1024*1024;

    private PetEggInventorySave(){}

    /**
     * Paper's saveData() catches write failures internally. A new receipt in the same snapshot
     * lets us distinguish a successful write from a silent failure or disabled player saving.
     * Read the actual PlayerDataStorage directory: a player's current dimension has no bearing
     * on where Minecraft keeps their inventory.
     */
    static boolean save(VoidscapePlugin plugin,Player player){
        if(!Bukkit.isPrimaryThread()||!player.isOnline()||player.isDead()){
            plugin.getLogger().warning("Cannot confirm pet egg inventory save for "+player.getUniqueId()+": player must be alive and online on the server thread");
            return false;
        }
        try{
            var key=plugin.key("pet_egg_save_receipt");
            String receipt=UUID.randomUUID().toString();
            player.getPersistentDataContainer().set(key,PersistentDataType.STRING,receipt);
            player.saveData();
            Path file=MinecraftServer.getServer().getPlayerList().playerIo.getPlayerDir().toPath()
                .resolve(player.getUniqueId()+".dat");
            var saved=NbtIo.readCompressed(file,NbtAccounter.create(MAX_PLAYER_NBT_BYTES));
            String confirmed=saved.getCompound("BukkitValues")
                .flatMap(values->values.getString(key.toString())).orElse(null);
            if(receipt.equals(confirmed))return true;
            plugin.getLogger().warning("Pet egg inventory save was not confirmed for "+player.getUniqueId()+"; delivery remains pending");
        }catch(IOException|RuntimeException|LinkageError error){
            plugin.getLogger().warning("Cannot confirm pet egg inventory save for "+player.getUniqueId()+": "+error+"; delivery remains pending");
        }
        return false;
    }
}
