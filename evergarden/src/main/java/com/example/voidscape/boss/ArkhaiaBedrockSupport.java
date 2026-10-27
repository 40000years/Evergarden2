package com.example.voidscape.boss;

import com.example.voidscape.VoidscapePlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** Optional Geyser 2.11+ entity API. No Geyser classes are linked without the plugin. */
public final class ArkhaiaBedrockSupport implements AutoCloseable {
    public static final List<String> CLIPS=List.of("idle","walk","backstep","hover","light_windup","light_active","light_recover",
        "heavy_windup","heavy_active","heavy_recover","rift_windup","rift_active","rift_recover","nova_windup","nova_active","nova_recover",
        "portals_windup","portals_active","portals_recover","eclipse_windup","eclipse_active","eclipse_recover","stun","transition");
    /** Seraphon's clip order; the native Bedrock controller indexes the same list. */
    public static final List<String> SERAPHON_CLIPS=List.of("idle","walk","hover","cast_windup","cast_active","cast_recover",
        "smite_windup","smite_active","smite_recover","verdict_windup","verdict_active","verdict_recover",
        "wings_windup","wings_active","wings_recover","stun","transition","spawn");
    public static List<String> clips(String model){
        return model.equals("arkhaia")?CLIPS:model.equals("seraphon")?SERAPHON_CLIPS:riverClips(model);
    }
    public static final List<String> RIVER_CLIPS=List.of("idle","walk","hover","cast_windup","cast_active","cast_recover","attack_windup","attack_active","attack_recover","stun","ritual_windup","ritual_active","ritual_recover");
    public static List<String> riverClips(String model){
        // Wild pets: same order as every pet bbmodel and the exported Bedrock controller.
        if(model.startsWith("everpet_"))return com.example.voidscape.pet.PetCatalog.ANIMATIONS;
        if(model.equals("arkhaia_malefeasance")||model.equals("arkhaia_malefeasance_phase2")){
            List<String> clips=new ArrayList<>(RIVER_CLIPS.subList(0,10));
            for(String skill:List.of("sky","portal"))for(String stage:List.of("windup","active","recover"))clips.add(skill+"_"+stage);
            clips.add("rebirth");return clips;
        }
        if(model.equals("arkhaia_cocytus")){
            List<String> clips=new ArrayList<>(RIVER_CLIPS.subList(0,10));clips.addAll(List.of("rain_windup","rain_active","rain_recover"));return clips;
        }
        if(model.equals("arkhaia_styx")){
            List<String> clips=new ArrayList<>(RIVER_CLIPS.subList(0,10));
            for(String skill:List.of("slash","reverse","thrust","dash","blood","berserk"))for(String stage:List.of("windup","active","recover"))clips.add(skill+"_"+stage);
            return clips;
        }
        if(model.equals("arkhaia_banshee_wail")){
            List<String> clips=new ArrayList<>(RIVER_CLIPS.subList(0,10));clips.addAll(List.of("curse_windup","curse_active","curse_recover","reveal","reform"));return clips;
        }
        if(!model.equals("arkhaia_phlegethon"))return RIVER_CLIPS;
        List<String> clips=new ArrayList<>(RIVER_CLIPS.subList(0,10));clips.addAll(List.of("kick_windup","kick_active","kick_recover","throw_windup","throw_active","throw_recover"));return clips;
    }
    private static final List<String> MODELS=new ArrayList<>();
    static{MODELS.add("arkhaia");MODELS.add("seraphon");MODELS.add("arkhaia_acheron_echo");for(var kind:ArkhaiaWaves.Kind.values())MODELS.add("arkhaia_"+kind.name().toLowerCase(Locale.ROOT));
        MODELS.add("arkhaia_malefeasance_phase2");
        // Geyser defines entities once at startup, so every wild pet variant is registered up front.
        for(var species:com.example.voidscape.pet.PetCatalog.Species.values())if(!species.mythical())
            for(var stage:com.example.voidscape.pet.PetCatalog.Stage.values())MODELS.add(species.model(stage));}
    private static final class Actor {
        final String model;
        final float interactionWidth,interactionHeight;
        Actor(String model){this(model,0,0);}
        Actor(String model,float width,float height){this.model=model;this.interactionWidth=width;this.interactionHeight=height;}
        volatile Snapshot snapshot=new Snapshot(0,0,1,0,0);
        String clip="";double clock;
    }
    public record Snapshot(int clip,float time,float rate,float yaw,float pitch) {}
    private final VoidscapePlugin plugin;
    private final Map<UUID,Actor> actors=new ConcurrentHashMap<>();
    private Bridge bridge;
    public ArkhaiaBedrockSupport(VoidscapePlugin plugin){
        this.plugin=plugin;
        var geyser=Bukkit.getPluginManager().getPlugin("Geyser-Spigot");
        if(geyser==null)return;
        try{bridge=new Bridge(geyser.getClass().getClassLoader());}
        catch(ReflectiveOperationException|LinkageError|RuntimeException error){plugin.getLogger().warning("Arkhaia retains Bedrock pose fallback: "+error);}
    }
    public boolean ready(){return bridge!=null&&bridge.defined&&bridge.properties.size()==5;}
    public String status(){return ready()?"native Bedrock rig, "+CLIPS.size()+" clips + independent gaze":"compatible Bedrock pose fallback";}
    public void register(UUID carrier){register(carrier,"arkhaia");}
    public void register(UUID carrier,String model){if(!MODELS.contains(model))throw new IllegalArgumentException(model);actors.put(carrier,new Actor(model));}
    /** Pets have a touch target; combat sculptures retain their separate evaluated receivers. */
    public void register(UUID carrier,String model,float width,float height){
        if(!MODELS.contains(model)||!model.startsWith("everpet_")||!Float.isFinite(width)||!Float.isFinite(height)
            ||width<0||height<0)throw new IllegalArgumentException(model);
        actors.put(carrier,new Actor(model,width,height));
    }
    public void unregister(UUID carrier){actors.remove(carrier);}
    public boolean registered(UUID carrier){return actors.containsKey(carrier);}
    public void frame(UUID carrier,ArkhaiaBrain.Frame frame,double speed,boolean airborne,ArkhaiaLook.Gaze gaze,Collection<? extends Player> viewers){
        if(!ready())return;
        Actor actor=actors.get(carrier);if(actor==null)return;
        String clip=ArkhaiaModelSupport.clip(frame,speed,airborne);
        float rate=(float)(clip.equals("walk")||clip.equals("backstep")?Math.clamp(speed/.16,.35,2):1);
        if(!clip.equals(actor.clip)){actor.clock=0;actor.clip=clip;}
        else actor.clock+=rate/20;
        double length=switch(clip){case "idle"->2.4;case "walk","backstep"->.8;case "hover"->1.4;default->0;};
        double time=length==0?frame.age()/20.0:actor.clock%length;
        Snapshot next=new Snapshot(CLIPS.indexOf(clip),(float)time,rate,gaze.yaw(),gaze.pitch());actor.snapshot=next;
        send(carrier,actor,next,viewers);
    }
    public void riverFrame(UUID carrier,String clip,float time,float rate,ArkhaiaLook.Gaze gaze,Collection<? extends Player> viewers){
        if(!ready())return;Actor actor=actors.get(carrier);if(actor==null)return;
        int index=clips(actor.model).indexOf(clip);if(index<0)throw new IllegalArgumentException(clip);
        Snapshot next=new Snapshot(index,time,rate,gaze.yaw(),gaze.pitch());actor.snapshot=next;send(carrier,actor,next,viewers);
    }
    /** Retained for callers that only choose the authored clip. */
    public void petFrame(UUID carrier,String clip,float time,float rate,Collection<? extends Player> viewers){
        petFrame(carrier,clip,time,rate,0,0,viewers);
    }
    /** Pet attention uses relative Bukkit yaw and positive-up pitch; Bedrock's X mirror reverses pitch. */
    public void petFrame(UUID carrier,String clip,float time,float rate,float headYaw,float headPitch,Collection<? extends Player> viewers){
        float yaw=Float.isFinite(headYaw)?Math.clamp(headYaw,-60,60):0;
        float pitch=Float.isFinite(headPitch)?Math.clamp(-headPitch,-25,60):0;
        riverFrame(carrier,clip,time,rate,new ArkhaiaLook.Gaze(yaw,pitch),viewers);
    }
    private void send(UUID carrier,Actor actor,Snapshot next,Collection<? extends Player> viewers){
        for(Player player:viewers)try{
            Object connection=bridge.connection.invoke(bridge.api,player.getUniqueId());
            if(connection==null)continue;
            Object entity=bridge.byUuid.invoke(bridge.entities.invoke(connection),carrier);
            if(entity!=null)bridge.update(entity,next,actor.model);
        }catch(ReflectiveOperationException error){plugin.getLogger().log(java.util.logging.Level.WARNING,"Cannot sync Arkhaia Bedrock rig",error);}
    }
    @Override public void close(){
        actors.clear();if(bridge!=null)try{bridge.unregister.invoke(bridge.bus,bridge.owner);}
        catch(ReflectiveOperationException error){plugin.getLogger().warning("Cannot unregister Arkhaia Geyser listeners: "+error);}
    }
    private final class Bridge {
        final Object api,bus,owner,definition,type;
        final Class<?> defineEntities,defineProperties,spawn,geyserEntity;
        final Method registerDefinition,registerFloat,registerInt,uuid,setDefinition,preSpawn,connection,entities,byUuid,batch,propertyUpdate,override,unregister;
        final Map<String,Object> properties=new LinkedHashMap<>();
        final Map<String,Object> definitions=new LinkedHashMap<>();
        final Map<String,Map<String,Object>> propertySets=new LinkedHashMap<>();
        final Object verticalOffset,width,height,scale;
        volatile boolean defined;
        Bridge(ClassLoader loader)throws ReflectiveOperationException{
            Class<?> apiType=loader.loadClass("org.geysermc.geyser.api.GeyserApi"),registrar=loader.loadClass("org.geysermc.geyser.api.event.EventRegistrar"),
                ownedBus=loader.loadClass("org.geysermc.event.bus.OwnedEventBus"),identifier=loader.loadClass("org.geysermc.geyser.api.util.Identifier"),
                custom=loader.loadClass("org.geysermc.geyser.api.entity.custom.CustomEntityDefinition"),entityDef=loader.loadClass("org.geysermc.geyser.api.entity.definition.GeyserEntityDefinition");
            api=apiType.getMethod("api").invoke(null);if(api==null)throw new IllegalStateException("Geyser API not loaded");
            bus=apiType.getMethod("eventBus").invoke(api);owner=registrar.getMethod("of",Object.class).invoke(null,plugin);
            type=identifier.getMethod("of",String.class).invoke(null,"voidscape:arkhaia");definition=custom.getMethod("of",identifier).invoke(null,type);
            for(String model:MODELS)definitions.put(model,model.equals("arkhaia")?definition:custom.getMethod("of",identifier).invoke(null,identifier.getMethod("of",String.class).invoke(null,"voidscape:"+model)));
            defineEntities=loader.loadClass("org.geysermc.geyser.api.event.lifecycle.GeyserDefineEntitiesEvent");
            defineProperties=loader.loadClass("org.geysermc.geyser.api.event.lifecycle.GeyserDefineEntityPropertiesEvent");
            spawn=loader.loadClass("org.geysermc.geyser.api.event.java.ServerSpawnEntityEvent");
            registerDefinition=defineEntities.getMethod("register",custom);
            registerFloat=defineProperties.getMethod("registerFloatProperty",identifier,identifier,float.class,float.class,Float.class);
            registerInt=defineProperties.getMethod("registerIntegerProperty",identifier,identifier,int.class,int.class,Integer.class);
            uuid=spawn.getMethod("uuid");setDefinition=spawn.getMethod("definition",entityDef);preSpawn=spawn.getMethod("preSpawnConsumer",Consumer.class);
            connection=apiType.getMethod("connectionByUuid",UUID.class);
            entities=loader.loadClass("org.geysermc.geyser.api.connection.GeyserConnection").getMethod("entities");
            byUuid=loader.loadClass("org.geysermc.geyser.api.entity.EntityData").getMethod("byUuid",UUID.class);
            geyserEntity=loader.loadClass("org.geysermc.geyser.api.entity.type.GeyserEntity");
            batch=geyserEntity.getMethod("updatePropertiesBatched",Consumer.class);
            propertyUpdate=loader.loadClass("org.geysermc.geyser.api.entity.property.BatchPropertyUpdater").getMethod("update",loader.loadClass("org.geysermc.geyser.api.entity.property.GeyserEntityProperty"),Object.class);
            Class<?> data=loader.loadClass("org.geysermc.geyser.api.entity.data.GeyserEntityDataTypes");
            verticalOffset=data.getField("VERTICAL_OFFSET").get(null);width=data.getField("WIDTH").get(null);height=data.getField("HEIGHT").get(null);scale=data.getField("SCALE").get(null);
            override=geyserEntity.getMethod("override",loader.loadClass("org.geysermc.geyser.api.entity.data.GeyserEntityDataType"),Object.class);
            unregister=ownedBus.getMethod("unregisterAll",Object.class);
            Method subscribe=ownedBus.getMethod("subscribe",Object.class,Class.class,Consumer.class);
            subscribe.invoke(bus,owner,defineEntities,(Consumer<Object>)event->guard(()->{for(Object def:definitions.values())registerDefinition.invoke(event,def);defined=true;}));
            subscribe.invoke(bus,owner,defineProperties,(Consumer<Object>)event->guard(()->{
                for(String model:MODELS){
                    Object modelType=identifier.getMethod("of",String.class).invoke(null,"voidscape:"+model);
                    Map<String,Object> props=model.equals("arkhaia")?properties:new LinkedHashMap<>();props.clear();
                    props.put("clip",registerInt.invoke(event,modelType,identifier.getMethod("of",String.class).invoke(null,"voidscape:clip"),0,clips(model).size()-1,0));
                    for(String name:List.of("clip_time","rate","look_yaw","look_pitch")){
                        float min=name.equals("look_yaw")?-60:name.equals("look_pitch")?-25:0;
                        float max=name.equals("look_yaw")?60:name.equals("look_pitch")?60:name.equals("rate")?2:600;
                        props.put(name,registerFloat.invoke(event,modelType,identifier.getMethod("of",String.class).invoke(null,"voidscape:"+name),min,max,name.equals("rate")?1f:0f));
                    }
                    propertySets.put(model,props);
                }
                plugin.getLogger().info("Arkhaia native Bedrock entity and gaze properties registered");
            }));
            subscribe.invoke(bus,owner,spawn,(Consumer<Object>)event->guard(()->{
                Actor actor=actors.get((UUID)uuid.invoke(event));if(actor==null||!ready())return;
                setDefinition.invoke(event,definitions.get(actor.model));
                preSpawn.invoke(event,(Consumer<Object>)entity->guard(()->{
                    override.invoke(entity,verticalOffset,0f);override.invoke(entity,width,actor.interactionWidth);
                    override.invoke(entity,height,actor.interactionHeight);override.invoke(entity,scale,1f);
                    update(entity,actor.snapshot,actor.model);
                }));
            }));
        }
        void update(Object entity,Snapshot snapshot)throws ReflectiveOperationException{update(entity,snapshot,"arkhaia");}
        void update(Object entity,Snapshot snapshot,String model)throws ReflectiveOperationException{
            Map<String,Object> properties=propertySets.get(model);if(properties==null)return;
            batch.invoke(entity,(Consumer<Object>)updater->guard(()->{
                propertyUpdate.invoke(updater,properties.get("clip"),snapshot.clip());
                propertyUpdate.invoke(updater,properties.get("clip_time"),snapshot.time());
                propertyUpdate.invoke(updater,properties.get("rate"),snapshot.rate());
                propertyUpdate.invoke(updater,properties.get("look_yaw"),snapshot.yaw());
                propertyUpdate.invoke(updater,properties.get("look_pitch"),snapshot.pitch());
            }));
        }
        void guard(Checked work){try{work.run();}catch(ReflectiveOperationException|RuntimeException error){plugin.getLogger().log(java.util.logging.Level.WARNING,"Arkhaia Geyser entity API failure",error);}}
    }
    @FunctionalInterface private interface Checked {void run()throws ReflectiveOperationException;}
}
