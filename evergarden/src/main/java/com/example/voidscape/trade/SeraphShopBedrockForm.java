package com.example.voidscape.trade;

import com.example.voidscape.VoidscapePlugin;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.geysermc.cumulus.form.SimpleForm;
import org.geysermc.cumulus.util.FormImage;
import org.geysermc.floodgate.api.FloodgateApi;

/** Loaded only when the optional Floodgate form integration is used. */
final class SeraphShopBedrockForm {
    private SeraphShopBedrockForm() {}

    static boolean send(VoidscapePlugin plugin, Player player, List<String> summary,
                        int sanctuaryPrice, int bossPrice, List<Runnable> actions) {
        SimpleForm form=SimpleForm.builder().title("Seraph").content(String.join("\n",summary))
            .button("ร้านเวทมนตร์\n§7คทา · คัมภีร์ · ยามานา",FormImage.Type.PATH,"textures/items/seraph_feather")
            .button("ร้านตำนาน\n§7อาวุธ · คัมภีร์ · ชุด · Core บอสมิติ",FormImage.Type.PATH,"textures/items/core_of_legend")
            .button("ร้านแผนที่\n§7Sanctuary "+sanctuaryPrice+" ขนนก · บอสหลัก "+bossPrice+" ทองคำ",FormImage.Type.PATH,"textures/items/map_empty")
            .validResultHandler(response->{
                int index=response.clickedButtonId();
                if(index>=0&&index<actions.size())Bukkit.getScheduler().runTask(plugin,()->{if(player.isOnline())actions.get(index).run();});
            }).build();
        return FloodgateApi.getInstance().sendForm(player.getUniqueId(),form);
    }
}
