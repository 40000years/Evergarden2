package com.example.advancemagic.item;

import com.example.advancemagic.AdvanceMagicPlugin;
import org.bukkit.Bukkit;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** Register before Geyser enables. Only explicitly tracked Longinus UUIDs change. */
final class LonginusBedrockBridge implements AutoCloseable {
    private record Pose(float yaw,float pitch) {}
    private record Delivered(Object entity,Pose pose,int tick) {}
    private final Map<UUID,Pose> projectiles=new ConcurrentHashMap<>();
    private final AdvanceMagicPlugin plugin;
    private Object bus,owner,api,yawProperty,pitchProperty;
    private final Map<UUID,Map<Object,Delivered>> delivered=new HashMap<>();
    private Method unregister,connections,entities,byUuid,batch,propertyUpdate;
    private volatile boolean ready;
    LonginusBedrockBridge(AdvanceMagicPlugin plugin){
        this.plugin=plugin;
        var geyser=Bukkit.getPluginManager().getPlugin("Geyser-Spigot");
        if(geyser==null)return;
        try{
            var loader=geyser.getClass().getClassLoader();
            var apiClass=loader.loadClass("org.geysermc.geyser.api.GeyserApi");
            api=apiClass.getMethod("api").invoke(null);
            bus=apiClass.getMethod("eventBus").invoke(api);
            owner=loader.loadClass("org.geysermc.geyser.api.event.EventRegistrar").getMethod("of",Object.class).invoke(null,plugin);
            var owned=loader.loadClass("org.geysermc.event.bus.OwnedEventBus");
            var subscribe=owned.getMethod("subscribe",Object.class,Class.class,Consumer.class);
            unregister=owned.getMethod("unregisterAll",Object.class);
            var custom=loader.loadClass("org.geysermc.geyser.api.entity.custom.CustomEntityDefinition");
            var definition=custom.getMethod("of",String.class).invoke(null,"advance_magic:longinus_projectile");
            var identifier=loader.loadClass("org.geysermc.geyser.api.util.Identifier");
            var idOf=identifier.getMethod("of",String.class);
            Object type=idOf.invoke(null,"advance_magic:longinus_projectile");
            var defineProperties=loader.loadClass("org.geysermc.geyser.api.event.lifecycle.GeyserDefineEntityPropertiesEvent");
            var registerFloat=defineProperties.getMethod("registerFloatProperty",identifier,identifier,float.class,float.class,Float.class);
            subscribe.invoke(bus,owner,defineProperties,(Consumer<Object>)event->{try{
                yawProperty=registerFloat.invoke(event,type,idOf.invoke(null,"advance_magic:flight_yaw"),-180f,180f,0f);
                pitchProperty=registerFloat.invoke(event,type,idOf.invoke(null,"advance_magic:flight_pitch"),-90f,90f,0f);
            }catch(ReflectiveOperationException e){plugin.getLogger().warning("Longinus Bedrock rotation properties: "+e);}});
            connections=apiClass.getMethod("onlineConnections");
            entities=loader.loadClass("org.geysermc.geyser.api.connection.GeyserConnection").getMethod("entities");
            byUuid=loader.loadClass("org.geysermc.geyser.api.entity.EntityData").getMethod("byUuid",UUID.class);
            var entityClass=loader.loadClass("org.geysermc.geyser.api.entity.type.GeyserEntity");
            batch=entityClass.getMethod("updatePropertiesBatched",Consumer.class);
            propertyUpdate=loader.loadClass("org.geysermc.geyser.api.entity.property.BatchPropertyUpdater").getMethod("update",loader.loadClass("org.geysermc.geyser.api.entity.property.GeyserEntityProperty"),Object.class);
            var override=entityClass.getMethod("override",loader.loadClass("org.geysermc.geyser.api.entity.data.GeyserEntityDataType"),Object.class);
            var offset=loader.loadClass("org.geysermc.geyser.api.entity.data.GeyserEntityDataTypes").getField("VERTICAL_OFFSET").get(null);
            var define=loader.loadClass("org.geysermc.geyser.api.event.lifecycle.GeyserDefineEntitiesEvent");
            var register=define.getMethod("register",custom);
            subscribe.invoke(bus,owner,define,(Consumer<Object>)event->{try{
                register.invoke(event,definition);ready=true;
            }catch(ReflectiveOperationException e){plugin.getLogger().warning("Longinus Bedrock definition: "+e);}});
            var spawn=loader.loadClass("org.geysermc.geyser.api.event.java.ServerSpawnEntityEvent");
            var uuid=spawn.getMethod("uuid");
            var replace=spawn.getMethod("definition",loader.loadClass("org.geysermc.geyser.api.entity.definition.GeyserEntityDefinition"));
            var preSpawn=spawn.getMethod("preSpawnConsumer",Consumer.class);
            subscribe.invoke(bus,owner,spawn,(Consumer<Object>)event->{try{
                UUID id=(UUID)uuid.invoke(event);
                var pose=projectiles.get(id);
                if(ready()&&pose!=null){
                    replace.invoke(event,definition);
                    preSpawn.invoke(event,(Consumer<Object>)entity->{try{
                        // Spawn translation can run after the server has already
                        // advanced to the impact frame. Read the latest pose here.
                        var latest=projectiles.get(id);
                        if(latest!=null){override.invoke(entity,offset,0f);update(entity,latest);}
                    }catch(ReflectiveOperationException e){plugin.getLogger().warning("Longinus initial pose: "+e);}});
                }
            }catch(ReflectiveOperationException e){plugin.getLogger().warning("Longinus Bedrock spawn: "+e);}});
        }catch(ReflectiveOperationException|LinkageError|RuntimeException e){
            plugin.getLogger().warning("Longinus custom Bedrock projectile unavailable: "+e);
            close();
        }
    }
    boolean ready(){return ready&&yawProperty!=null&&pitchProperty!=null;}
    boolean available(){return bus!=null&&unregister!=null;}
    private Pose pose(org.bukkit.util.Vector direction){
        return new Pose((float)Math.toDegrees(Math.atan2(direction.getX(),direction.getZ())),
            (float)Math.toDegrees(Math.atan2(direction.getY(),Math.hypot(direction.getX(),direction.getZ()))));
    }
    void add(UUID id,org.bukkit.util.Vector direction){projectiles.put(id,pose(direction));}
    void frame(UUID id,org.bukkit.util.Vector direction){
        Pose next=pose(direction);projectiles.put(id,next);
        if(!ready())return;
        try{
            List<?> online=(List<?>)connections.invoke(api);
            var sent=delivered.computeIfAbsent(id,k->new IdentityHashMap<>());
            sent.keySet().removeIf(c->!online.contains(c));
            for(Object conn:online){
                Object entity=byUuid.invoke(entities.invoke(conn),id);
                // A missing client-side entity is not a successful delivery.
                // Retry the identical final pose after it enters this session's cache.
                if(entity==null)continue;
                Delivered old=sent.get(conn);int tick=Bukkit.getCurrentTick();
                if(old!=null&&old.entity==entity&&old.pose.equals(next)&&tick-old.tick<40)continue;
                update(entity,next);sent.put(conn,new Delivered(entity,next,tick));
            }
        }catch(ReflectiveOperationException e){plugin.getLogger().warning("Longinus Bedrock flight pose: "+e);}
    }
    private void update(Object entity,Pose pose)throws ReflectiveOperationException{
        batch.invoke(entity,(Consumer<Object>)updater->{try{
            propertyUpdate.invoke(updater,yawProperty,pose.yaw());propertyUpdate.invoke(updater,pitchProperty,pose.pitch());
        }catch(ReflectiveOperationException e){plugin.getLogger().warning("Longinus rotation sync: "+e);}});
    }
    void remove(UUID id){projectiles.remove(id);delivered.remove(id);}
    @Override public void close(){
        ready=false;projectiles.clear();delivered.clear();
        if(bus!=null&&owner!=null&&unregister!=null)try{unregister.invoke(bus,owner);}catch(ReflectiveOperationException ignored){}
    }
}
