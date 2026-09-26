package com.example.advancemagic.spell;

import com.example.advancemagic.effect.EffectEngine;
import org.bukkit.*;
import org.bukkit.entity.*;
import java.lang.reflect.*;
import java.util.*;

/** Native End Crystal beams sent to clients only; no spawned crystals or dragon healing. */
final class CrystalBeamVisuals {
    private final MagicContext c;
    private Bridge bridge;
    private boolean disabled;
    CrystalBeamVisuals(MagicContext c){this.c=c;}

    Projection open(EffectEngine.Effect effect,List<Location> sources,Location target){
        Projection projection=new Projection(target.getWorld());
        if(disabled)return projection;
        try{
            if(bridge==null)bridge=new Bridge();
            List<Integer> ids=new ArrayList<>();
            for(Location source:sources){
                if(!c.loaded(source))continue;
                // createEntity allocates a normal entity ID but never adds it to the world.
                EnderCrystal crystal=source.getWorld().createEntity(source,EnderCrystal.class);
                crystal.setPersistent(false);crystal.setGravity(false);crystal.setInvulnerable(true);
                crystal.setShowingBottom(false);crystal.setBeamTarget(target);
                Object handle=bridge.entityHandle.invoke(crystal);
                Object spawn=bridge.spawn.newInstance(crystal.getEntityId(),crystal.getUniqueId(),
                    source.getX(),source.getY(),source.getZ(),0f,0f,bridge.entityType.invoke(handle),0,bridge.zero,0d);
                Object values=bridge.nonDefault.invoke(bridge.entityData.invoke(handle));
                Object data=bridge.metadata.newInstance(crystal.getEntityId(),values);
                projection.packets.add(spawn);projection.packets.add(data);ids.add(crystal.getEntityId());
            }
            if(!ids.isEmpty()){
                projection.remove=bridge.remove.newInstance((Object)ids.stream().mapToInt(Integer::intValue).toArray());
                projection.available=true;effect.onClose(projection::close);
            }
        }catch(ReflectiveOperationException|LinkageError|RuntimeException ex){
            disable(ex);
        }
        return projection;
    }
    private void disable(Throwable error){
        if(!disabled)c.plugin.getLogger().warning("Chronos crystal beam adapter unavailable; using coloured particle beams: "+error.getClass().getSimpleName());
        disabled=true;
    }
    final class Projection implements AutoCloseable {
        private record Viewer(Player player,Object connection) {}
        private final World world;
        private final List<Object> packets=new ArrayList<>();
        private final Map<UUID,Viewer> shown=new HashMap<>();
        private Object remove;
        private boolean available,closed;
        private Projection(World world){this.world=world;}
        boolean available(){return available&&!closed;}
        void sync(Location center,boolean firing){
            if(!available())return;
            Set<UUID> present=new HashSet<>();
            if(firing)for(Player player:world.getPlayers()){
                if(!player.isOnline()||player.getLocation().distanceSquared(center)>64*64)continue;
                UUID id=player.getUniqueId();present.add(id);
                Viewer old=shown.get(id);
                if(old!=null&&old.player==player)continue;
                if(old!=null)forget(id);
                try{
                    Object connection=bridge.connection.get(bridge.playerHandle.invoke(player));
                    // Record before sending so a partially delivered projection is cleaned up too.
                    shown.put(id,new Viewer(player,connection));
                    for(Object packet:packets)bridge.send.invoke(connection,packet);
                }catch(ReflectiveOperationException|LinkageError|RuntimeException ex){disable(ex);close();return;}
            }
            for(UUID id:List.copyOf(shown.keySet()))if(!present.contains(id))forget(id);
        }
        private void forget(UUID id){
            Viewer viewer=shown.remove(id);if(viewer==null||!viewer.player.isOnline()||viewer.player.getWorld()!=world)return;
            try{bridge.send.invoke(viewer.connection,remove);}catch(ReflectiveOperationException|RuntimeException ignored){}
        }
        @Override public void close(){
            if(closed)return;closed=true;
            for(UUID id:List.copyOf(shown.keySet()))forget(id);
            packets.clear();remove=null;
        }
    }
    private static final class Bridge {
        final Constructor<?> spawn,metadata,remove;
        final Method entityHandle,playerHandle,entityType,entityData,nonDefault,send;
        final Field connection;
        final Object zero;
        Bridge()throws ReflectiveOperationException{
            ClassLoader loader=Bukkit.getServer().getClass().getClassLoader();
            Class<?> entity=loader.loadClass("net.minecraft.world.entity.Entity");
            Class<?> type=loader.loadClass("net.minecraft.world.entity.EntityType");
            Class<?> vector=loader.loadClass("net.minecraft.world.phys.Vec3");
            Class<?> player=loader.loadClass("org.bukkit.craftbukkit.entity.CraftPlayer");
            entityHandle=loader.loadClass("org.bukkit.craftbukkit.entity.CraftEntity").getMethod("getHandle");
            playerHandle=player.getMethod("getHandle");
            entityType=entity.getMethod("getType");entityData=entity.getMethod("getEntityData");
            nonDefault=loader.loadClass("net.minecraft.network.syncher.SynchedEntityData").getMethod("getNonDefaultValues");
            zero=vector.getField("ZERO").get(null);
            spawn=loader.loadClass("net.minecraft.network.protocol.game.ClientboundAddEntityPacket")
                .getConstructor(int.class,UUID.class,double.class,double.class,double.class,float.class,float.class,type,int.class,vector,double.class);
            metadata=loader.loadClass("net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket").getConstructor(int.class,List.class);
            remove=loader.loadClass("net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket").getConstructor(int[].class);
            connection=playerHandle.getReturnType().getField("connection");
            send=connection.getType().getMethod("send",loader.loadClass("net.minecraft.network.protocol.Packet"));
        }
    }
}
