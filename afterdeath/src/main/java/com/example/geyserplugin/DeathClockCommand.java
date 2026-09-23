package com.example.geyserplugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class DeathClockCommand implements CommandExecutor {

    private final DeathClockListener deathClockListener;

    public DeathClockCommand(DeathClockListener deathClockListener) {
        this.deathClockListener = deathClockListener;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("คำสั่งนี้ใช้ได้เฉพาะในเกมเท่านั้น", NamedTextColor.RED));
            return true;
        }

        deathClockListener.giveDeathClock(player);
        player.sendMessage(Component.text("ได้รับนาฬิกาย้อนเวลาเรียบร้อยแล้ว!", NamedTextColor.GREEN));
        return true;
    }
}
