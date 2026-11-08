package dev.mrshawn.deathmessages.api.events;

import dev.mrshawn.deathmessages.enums.MessageType;
import net.kyori.adventure.text.TextComponent;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import java.util.List;

/** Test stand-in with the public API of DeathMessages 1.5.0's event (javap-checked against 1.4.21 and 1.5.0). */
public class BroadcastDeathMessageEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final LivingEntity livingEntity;
    private final MessageType messageType;
    private final TextComponent[] textComponents;   // 0: prefix, 1: body
    private final List<World> broadcastedWorlds;
    private final boolean isGangDeath;
    private boolean cancelled;

    public BroadcastDeathMessageEvent(Player player, LivingEntity livingEntity, MessageType messageType, TextComponent[] textComponents,
                                      List<World> broadcastedWorlds, boolean isGangDeath) {
        this.player = player; this.livingEntity = livingEntity; this.messageType = messageType;
        this.textComponents = textComponents; this.broadcastedWorlds = broadcastedWorlds; this.isGangDeath = isGangDeath;
    }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
    public Player getPlayer() { return player; }
    public LivingEntity getLivingEntity() { return livingEntity; }
    public MessageType getMessageType() { return messageType; }
    public TextComponent[] getTextComponents() { return textComponents; }
    public boolean isGangDeath() { return isGangDeath; }
    public List<World> getBroadcastedWorlds() { return broadcastedWorlds; }
}
