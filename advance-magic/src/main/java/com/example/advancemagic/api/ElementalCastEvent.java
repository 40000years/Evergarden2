package com.example.advancemagic.api;

import com.example.advancemagic.elemental.ElementalSpell;
import org.bukkit.entity.Player;
import org.bukkit.event.*;

/** Fired before costs are committed. Region and combat plugins may cancel the cast. */
public final class ElementalCastEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS=new HandlerList();
    private final Player caster;
    private final ElementalSpell spell;
    private boolean cancelled;
    private double damageMultiplier=1;
    public ElementalCastEvent(Player caster,ElementalSpell spell){this.caster=caster;this.spell=spell;}
    public Player getCaster(){return caster;}
    public ElementalSpell getSpell(){return spell;}
    public boolean isCancelled(){return cancelled;}
    public void setCancelled(boolean value){cancelled=value;}
    public double getDamageMultiplier(){return damageMultiplier;}
    public void setDamageMultiplier(double value){damageMultiplier=Double.isFinite(value)?Math.clamp(value,0,3):1;}
    public HandlerList getHandlers(){return HANDLERS;}
    public static HandlerList getHandlerList(){return HANDLERS;}
}
