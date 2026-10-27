package com.example.advancemagic.spell;

import com.example.advancemagic.AdvanceMagicPlugin;
import org.bukkit.Bukkit;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.*;
import java.util.*;
import java.util.function.Consumer;

/** Registers a resource-pack cube rig before Geyser starts; no Geyser dependency on Java-only servers. */
public final class BedrockBlockModels implements AutoCloseable {
    public static final String TYPE="advance_magic:legendary_block";
    public static final List<String> CHANNELS=List.of("x","y","z","qx","qy","qz","qw","size");
    private final AdvanceMagicPlugin plugin;
    private final List<String> materials;
    private final Map<String,Object> properties=new LinkedHashMap<>();
    private Object bus,owner,type;
    private Method unregister,batch,update;
    private volatile boolean defined;
    public BedrockBlockModels(AdvanceMagicPlugin plugin){
        this.plugin=plugin;
        try(var reader=new InputStreamReader(Objects.requireNonNull(plugin.getResource("legendary-blocks.json")),StandardCharsets.UTF_8)){
            materials=List.of(new com.google.gson.Gson().fromJson(reader,String[].class));
        }catch(java.io.IOException failure){throw new IllegalStateException("Cannot read Legendary block catalog",failure);}
        var geyser=Bukkit.getPluginManager().getPlugin("Geyser-Spigot");if(geyser==null)return;
        try{
            var loader=geyser.getClass().getClassLoader();
            var apiClass=loader.loadClass("org.geysermc.geyser.api.GeyserApi");
            Object api=apiClass.getMethod("api").invoke(null);bus=apiClass.getMethod("eventBus").invoke(api);
            owner=loader.loadClass("org.geysermc.geyser.api.event.EventRegistrar").getMethod("of",Object.class).invoke(null,plugin);
            var owned=loader.loadClass("org.geysermc.event.bus.OwnedEventBus");
            unregister=owned.getMethod("unregisterAll",Object.class);
            var subscribe=owned.getMethod("subscribe",Object.class,Class.class,Consumer.class);
            var id=loader.loadClass("org.geysermc.geyser.api.util.Identifier");var idOf=id.getMethod("of",String.class);type=idOf.invoke(null,TYPE);
            var custom=loader.loadClass("org.geysermc.geyser.api.entity.custom.CustomEntityDefinition");
            Object definition=custom.getMethod("of",String.class).invoke(null,TYPE);
            var define=loader.loadClass("org.geysermc.geyser.api.event.lifecycle.GeyserDefineEntitiesEvent");
            var register=define.getMethod("register",custom);
            subscribe.invoke(bus,owner,define,(Consumer<Object>)event->guard(()->{register.invoke(event,definition);defined=true;}));
            var defineProperties=loader.loadClass("org.geysermc.geyser.api.event.lifecycle.GeyserDefineEntityPropertiesEvent");
            var registerFloat=defineProperties.getMethod("registerFloatProperty",id,id,float.class,float.class,Float.class);
            var registerInt=defineProperties.getMethod("registerIntegerProperty",id,id,int.class,int.class,Integer.class);
            subscribe.invoke(bus,owner,defineProperties,(Consumer<Object>)event->guard(()->{
                properties.clear();
                properties.put("material",registerInt.invoke(event,type,idOf.invoke(null,"advance_magic:material"),0,materials.size()-1,0));
                properties.put("revision",registerInt.invoke(event,type,idOf.invoke(null,"advance_magic:revision"),0,Integer.MAX_VALUE,0));
                for(String end:List.of("from_","to_"))for(String channel:CHANNELS){
                    String name=end+channel;
                    float min=channel.startsWith("q")?-1:channel.equals("size")?0:-256;
                    float max=channel.startsWith("q")?1:256;
                    float initial=channel.equals("qw")?1:0;
                    properties.put(name,registerFloat.invoke(event,type,idOf.invoke(null,"advance_magic:"+name),min,max,initial));
                }
                properties.put("duration",registerFloat.invoke(event,type,idOf.invoke(null,"advance_magic:duration"),0f,3f,0f));
            }));
            var entity=loader.loadClass("org.geysermc.geyser.api.entity.type.GeyserEntity");
            batch=entity.getMethod("updatePropertiesBatched",Consumer.class,boolean.class);
            update=loader.loadClass("org.geysermc.geyser.api.entity.property.BatchPropertyUpdater").getMethod("update",loader.loadClass("org.geysermc.geyser.api.entity.property.GeyserEntityProperty"),Object.class);
        }catch(ReflectiveOperationException|LinkageError|RuntimeException failure){plugin.getLogger().warning("Legendary Bedrock cube rig unavailable: "+failure);close();}
    }
    public boolean ready(){return defined&&properties.size()==19;}
    int material(String name){return materials.indexOf(name);}
    Object definition(ClassLoader loader)throws ReflectiveOperationException{
        if(!ready())throw new IllegalStateException("Legendary cube definition is not registered; restart Geyser with this plugin");
        Object registry=loader.loadClass("org.geysermc.geyser.registry.Registries").getField("BEDROCK_ENTITY_DEFINITIONS").get(null);
        return Objects.requireNonNull(((Map<?,?>)registry.getClass().getMethod("get").invoke(registry)).get(type));
    }
    void pose(Object entity,int material,int revision,float[] from,float[] to,float duration)throws ReflectiveOperationException{
        batch.invoke(entity,(Consumer<Object>)updater->guard(()->{
            update.invoke(updater,properties.get("material"),material);update.invoke(updater,properties.get("revision"),revision);
            for(int i=0;i<CHANNELS.size();i++){
                update.invoke(updater,properties.get("from_"+CHANNELS.get(i)),from[i]);
                update.invoke(updater,properties.get("to_"+CHANNELS.get(i)),to[i]);
            }
            update.invoke(updater,properties.get("duration"),duration);
        }),false);
    }
    private interface Work {void run()throws ReflectiveOperationException;}
    private void guard(Work work){try{work.run();}catch(ReflectiveOperationException failure){throw new IllegalStateException("Cannot synchronize Legendary cube rig",failure);}}
    @Override public void close(){defined=false;if(bus!=null&&unregister!=null)try{unregister.invoke(bus,owner);}catch(ReflectiveOperationException failure){plugin.getLogger().warning("Cannot unregister Legendary cube rig: "+failure);}}
}
