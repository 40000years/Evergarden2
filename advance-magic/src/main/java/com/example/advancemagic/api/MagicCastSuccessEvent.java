package com.example.advancemagic.api;

import com.example.advancemagic.spell.Spell;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired only when a spell cast succeeds (after cooldown, mana, and spell resolution).
 * Used by external integrations (e.g. Evergarden crop buffs) to perform side-effects
 * or consume charges safely without triggering on failed casts.
 */
public final class MagicCastSuccessEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player caster;
    private final Spell spell;

    public MagicCastSuccessEvent(Player caster, Spell spell) {
        this.caster = caster;
        this.spell = spell;
    }

    public Player getCaster() { return caster; }
    public Spell getSpell() { return spell; }
    public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
