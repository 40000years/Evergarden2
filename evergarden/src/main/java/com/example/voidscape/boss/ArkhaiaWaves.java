package com.example.voidscape.boss;

import java.util.*;

/** Rotating unique roster: a dead slot waits ten seconds; survivors are never duplicated. */
public final class ArkhaiaWaves {
    public enum Kind { ACHERON, PHLEGETHON, COCYTUS, BANSHEE_WAIL, MALEFEASANCE, STYX }
    private final EnumSet<Kind> alive=EnumSet.noneOf(Kind.class);
    private final EnumMap<Kind,Long> ready=new EnumMap<>(Kind.class);
    private int cursor;
    private long replacementAt;
    public List<Kind> fill(long tick,int players){
        int capacity=Math.clamp(players,0,6);
        if(tick<replacementAt)return List.of();
        List<Kind> result=new ArrayList<>();
        for(int i=0;i<6&&alive.size()<capacity;i++){
            Kind kind=Kind.values()[cursor];cursor=(cursor+1)%6;
            if(!alive.contains(kind)&&tick>=ready.getOrDefault(kind,0L)){alive.add(kind);result.add(kind);}
        }
        return List.copyOf(result);
    }
    public void died(Kind kind,long tick){if(alive.remove(kind)){ready.put(kind,tick+200);replacementAt=tick+200;}}
    public int alive(){return alive.size();}
    public void clear(){alive.clear();ready.clear();cursor=0;replacementAt=0;}
}
