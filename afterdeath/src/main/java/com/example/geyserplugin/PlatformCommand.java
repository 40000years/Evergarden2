package com.example.geyserplugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class PlatformCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("คำสั่งนี้ใช้ได้เฉพาะในเกมเท่านั้น", NamedTextColor.RED));
            return true;
        }

        GeyserExamplePlugin plugin = GeyserExamplePlugin.getInstance();

        if (plugin.isBedrockPlayer(player.getUniqueId())) {
            player.sendMessage(Component.text("คุณกำลังเล่นผ่าน Bedrock Edition (Geyser)", NamedTextColor.AQUA));
            String os = plugin.getBedrockDeviceOs(player.getUniqueId());
            player.sendMessage(Component.text("อุปกรณ์: " + os, NamedTextColor.GRAY));
        } else {
            player.sendMessage(Component.text("คุณกำลังเล่นผ่าน Java Edition", NamedTextColor.GREEN));
        }

        return true;
    }
}
