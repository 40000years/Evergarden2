package com.example.voidscape.gui;

import com.example.voidscape.VoidscapePlugin;
import com.example.advancemagic.AdvanceMagicPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

/** Compatibility route for /evergarden wands, now showing the eight elemental staffs. */
public final class WandShowcaseGui implements Listener {
    private final VoidscapePlugin plugin;
    public WandShowcaseGui(VoidscapePlugin plugin){this.plugin=plugin;}
    public void open(Player player){
        var addon=plugin.getServer().getPluginManager().getPlugin("advance-magic");
        if(addon instanceof AdvanceMagicPlugin magic&&magic.isEnabled())magic.itemMenu().open(player,player.hasPermission("advance-magic.admin"));
        else plugin.message(player,"ต้องเปิดใช้งาน Advance Magic ก่อนดูคทาธาตุ");
    }
}
