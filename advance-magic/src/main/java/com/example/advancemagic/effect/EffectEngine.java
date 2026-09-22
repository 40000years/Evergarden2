package com.example.advancemagic.effect;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.plugin.Plugin;
import java.util.*;
import java.util.function.BiPredicate;

/** One synchronous ticker owns every temporary entity, channel and cleanup callback. */
public final class EffectEngine {
    public final class Effect {
        public final Player owner;
        private final UUID world;
        private final int duration;
        private final BiPredicate<Effect,Integer> body;
        private final List<Entity> entities=new ArrayList<>();
        private final List<Runnable> cleanup=new ArrayList<>();
        private int age;
        private boolean closed;
        private Effect(Player owner,int duration,BiPredicate<Effect,Integer> body) {
            this.owner=owner;this.world=owner.getWorld().getUID();this.duration=duration;this.body=body;
        }
        public <T extends Entity> T track(T entity) { entity.setPersistent(false);entities.add(entity);return entity; }
        public void onClose(Runnable callback) { cleanup.add(callback); }
        public void close() {
            if(closed)return;closed=true;
            entities.forEach(Entity::remove);
            for(Runnable r:cleanup)try{r.run();}catch(Exception ex){plugin.getLogger().warning("Effect cleanup: "+ex);}
            effects.remove(this);
        }
        private void tick() {
            if(closed)return;
            if(!owner.isOnline()||owner.isDead()||!owner.getWorld().getUID().equals(world)||age>=duration){close();return;}
            try { if(!body.test(this,age++))close(); }
            catch(Exception ex){plugin.getLogger().log(java.util.logging.Level.SEVERE,"Spell effect failed",ex);close();}
        }
    }
    private final Plugin plugin;
    private final int limit;
    private final Set<Effect> effects=new LinkedHashSet<>();
    public EffectEngine(Plugin plugin,int limit){this.plugin=plugin;this.limit=Math.max(16,Math.min(512,limit));}
    public boolean hasCapacity(){return effects.size()<limit;}
    public Effect start(Player owner,int duration,BiPredicate<Effect,Integer> body) {
        if(!hasCapacity())throw new IllegalStateException("Too many active magic effects");
        Effect e=new Effect(owner,duration,body);effects.add(e);return e;
    }
    public void tick(){if(effects.isEmpty())return;for(Effect e:List.copyOf(effects))e.tick();}
    public void closeOwner(UUID id){for(Effect e:List.copyOf(effects))if(e.owner.getUniqueId().equals(id))e.close();}
    public void close(){for(Effect e:List.copyOf(effects))e.close();}
    public int size(){return effects.size();}
}
