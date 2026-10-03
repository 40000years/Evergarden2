package com.example.advancemagic.api;

import com.example.advancemagic.spell.Spell;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.*;

/** Allows region owners to protect terrain while the spell's visual/damage timeline continues. */
public final class MagicTerrainEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS=new HandlerList();
    private final Player caster;
    private final Spell spell;
    private final Block block;
    private final Material material;
    private boolean cancelled;
    public MagicTerrainEvent(Player caster,Spell spell,Block block,Material material){
        this.caster=caster;this.spell=spell;this.block=block;this.material=material;
    }
    public Player getCaster(){return caster;}
    public Spell getSpell(){return spell;}
    public Block getBlock(){return block;}
    public Material getMaterial(){return material;}
    @Override public boolean isCancelled(){return cancelled;}
    @Override public void setCancelled(boolean value){cancelled=value;}
    @Override public HandlerList getHandlers(){return HANDLERS;}
    public static HandlerList getHandlerList(){return HANDLERS;}
}
