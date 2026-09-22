package com.example.advancemagic.api;

import com.example.advancemagic.spell.Spell;
import org.bukkit.entity.Player;
import org.bukkit.event.*;

/** Region/arena integrations may cancel a cast before mana or effects are committed. */
public final class MagicCastEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS=new HandlerList();
    private final Player caster;
    private final Spell spell;
    private boolean cancelled;
    private double cooldownMultiplier = 1.0;
    private boolean bloodCast = false;
    private int extraCasts = 0;
    private double velocityMultiplier = 1.0;

    public MagicCastEvent(Player caster,Spell spell){this.caster=caster;this.spell=spell;}
    public Player getCaster(){return caster;}
    public Spell getSpell(){return spell;}
    public boolean isCancelled(){return cancelled;}
    public void setCancelled(boolean value){cancelled=value;}
    public double getCooldownMultiplier(){return cooldownMultiplier;}
    public void setCooldownMultiplier(double mult){this.cooldownMultiplier=Math.max(0.1, mult);}
    public boolean isBloodCast(){return bloodCast;}
    public void setBloodCast(boolean value){this.bloodCast=value;}
    public int getExtraCasts(){return extraCasts;}
    public void setExtraCasts(int extra){this.extraCasts=Math.max(0, extra);}
    public double getVelocityMultiplier(){return velocityMultiplier;}
    public void setVelocityMultiplier(double mult){this.velocityMultiplier=Math.max(0.5, mult);}
    public HandlerList getHandlers(){return HANDLERS;}
    public static HandlerList getHandlerList(){return HANDLERS;}
}
