package com.example.voidscape.pet;

import com.example.voidscape.VoidscapePlugin;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.geysermc.cumulus.form.CustomForm;
import org.geysermc.floodgate.api.FloodgateApi;

/** Loaded only when the optional Floodgate form integration is used. */
final class PetNameForm {
    private PetNameForm() {}

    static boolean send(VoidscapePlugin plugin, Player player, String placeholder, String current,
                        Consumer<String> onName) {
        return FloodgateApi.getInstance().sendForm(player.getUniqueId(),
            CustomForm.builder().title("ตั้งชื่อ").input("ชื่อใหม่ (สูงสุด 16 ตัวอักษร)",placeholder,current)
                .validResultHandler(response->{
                    String name=response.asInput(0);
                    Bukkit.getScheduler().runTask(plugin,()->{if(player.isOnline())onName.accept(name);});
                }).build());
    }
}
