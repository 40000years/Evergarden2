package io.github.arcaneplugins.levelledmobs.events;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.*;
/** Test double for LM4's documented cancellable pre-level event. */
public final class MobPreLevelEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS=new HandlerList();
    private final LivingEntity entity;private boolean cancelled;
    public MobPreLevelEvent(LivingEntity entity){this.entity=entity;}
    public LivingEntity getEntity(){return entity;}
    public boolean isCancelled(){return cancelled;}
    public void setCancelled(boolean value){cancelled=value;}
    public HandlerList getHandlers(){return HANDLERS;}
    public static HandlerList getHandlerList(){return HANDLERS;}
}
