package com.example.voidscape.boss;

import java.util.*;

/** Final damage limits after weapon bonuses; one rolling budget across every encounter target. */
public final class JudgeDamageBudget {
    public enum Kind { MELEE, PROJECTILE, MAGIC, OTHER }
    public record Limits(double melee,double projectile,double magic,double other,double perSecond) {
        public double hit(Kind kind){return switch(kind){case MELEE->melee;case PROJECTILE->projectile;case MAGIC->magic;case OTHER->other;};}
    }
    private record Spent(long tick,double amount) {}
    private final Map<UUID,ArrayDeque<Spent>> history=new HashMap<>();
    public double available(UUID player,Kind kind,double incoming,long now,Limits limits){
        if(!Double.isFinite(incoming)||incoming<=0)return 0;
        var hits=history.get(player);double used=0;
        if(hits!=null){while(!hits.isEmpty()&&hits.getFirst().tick()<=now-20)hits.removeFirst();for(var h:hits)used+=h.amount();}
        return Math.max(0,Math.min(incoming,Math.min(limits.hit(kind),limits.perSecond()-used)));
    }
    public void spend(UUID player,double actual,long now){if(actual>0)history.computeIfAbsent(player,p->new ArrayDeque<>()).addLast(new Spent(now,actual));}
}
