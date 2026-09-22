package com.example.advancemagic.api;

import com.example.advancemagic.spell.Spell;
import org.bukkit.entity.*;
import org.bukkit.event.*;

/** Called before a spell damages, moves or applies a status to a target. */
public final class MagicAffectEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS=new HandlerList();
    private final Player caster;
    private final LivingEntity target;
    private final Spell spell;
    private boolean cancelled;
    public MagicAffectEvent(Player caster,LivingEntity target,Spell spell){this.caster=caster;this.target=target;this.spell=spell;}
    public Player getCaster(){return caster;}
    public LivingEntity getTarget(){return target;}
    public Spell getSpell(){return spell;}
    public boolean isCancelled(){return cancelled;}
    public void setCancelled(boolean value){cancelled=value;}
    public HandlerList getHandlers(){return HANDLERS;}
    public static HandlerList getHandlerList(){return HANDLERS;}
}
