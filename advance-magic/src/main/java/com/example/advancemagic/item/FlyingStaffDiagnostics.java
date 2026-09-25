package com.example.advancemagic.item;

import com.example.advancemagic.AdvanceMagicPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Read-only, bounded packet observation for one Bedrock flight per player per restart. */
final class FlyingStaffDiagnostics implements AutoCloseable {
    private final AdvanceMagicPlugin plugin;
    private final Map<UUID,Trace> active=new HashMap<>();
    private final Set<UUID> recorded=new HashSet<>();

    FlyingStaffDiagnostics(AdvanceMagicPlugin plugin){this.plugin=plugin;}

    void mount(Player player,Entity mount){
        if(!plugin.getConfig().getBoolean("flying-staff.diagnostics.enabled",true)||
            active.containsKey(player.getUniqueId())||recorded.contains(player.getUniqueId()))return;
        try {
            Class<?> apiType=Class.forName("org.geysermc.geyser.api.GeyserApi");
            Object api=apiType.getMethod("api").invoke(null);
            Object connection=apiType.getMethod("connectionByUuid",UUID.class).invoke(api,player.getUniqueId());
            if(connection==null)return;
            Trace trace=new Trace(player.getUniqueId(),mount.getEntityId(),connection);
            active.put(player.getUniqueId(),trace);recorded.add(player.getUniqueId());
            trace.execute(()->{
                try {
                    trace.network.getClass().getMethod("addListener",trace.listenerType).invoke(trace.network,trace.listener);
                    trace.log("START build="+plugin.getPluginMeta().getVersion()+" mount="+trace.mountId);
                }catch(ReflectiveOperationException error){trace.fail(error);}
            });
        }catch(ClassNotFoundException ignored){
            // Geyser is optional.
        }catch(ReflectiveOperationException error){plugin.getLogger().warning("[StaffTrace] Cannot start: "+error);}
    }

    void dismount(Player player,boolean cancelled){
        Trace trace=active.get(player.getUniqueId());
        if(trace==null)return;
        if(!cancelled)trace.remaining=Math.min(trace.remaining,400);
        trace.log("DISMOUNT cancelled="+cancelled+" server="+position(player.getLocation()));
    }

    void tick(){
        for(Trace trace:new ArrayList<>(active.values())){
            Player player=Bukkit.getPlayer(trace.playerId);
            if(player==null||!player.isOnline()||trace.failed||--trace.remaining<=0){
                active.remove(trace.playerId);trace.stop();continue;
            }
            if(++trace.ticks%20!=0)continue;
            Entity vehicle=player.getVehicle();
            String server="server="+position(player.getLocation())+" vehicle="+(vehicle==null?"none":vehicle.getEntityId())+
                " ground="+player.isOnGround()+" sneak="+player.isSneaking();
            trace.execute(()->trace.sample(server));
        }
    }

    private static String position(Location location){
        return String.format(Locale.ROOT,"%.3f,%.3f,%.3f",location.getX(),location.getY(),location.getZ());
    }
    private static Object call(Object object,String method) throws ReflectiveOperationException {
        return object.getClass().getMethod(method).invoke(object);
    }
    @Override public void close(){for(Trace trace:active.values())trace.stop();active.clear();}

    private final class Trace {
        final UUID playerId;
        final int mountId;
        final Object connection,network,listener;
        final Class<?> listenerType;
        final Method execute;
        final Map<String,AtomicInteger> counts=new ConcurrentHashMap<>();
        final Set<String> chunks=ConcurrentHashMap.newKeySet();
        int remaining=2400,ticks;
        volatile boolean stopped,failed;
        volatile String lastCorrection="none",lastPassengers="none";

        Trace(UUID playerId,int mountId,Object connection) throws ReflectiveOperationException {
            this.playerId=playerId;this.mountId=mountId;this.connection=connection;
            network=call(call(connection,"getDownstream"),"getSession");
            execute=connection.getClass().getMethod("executeInEventLoop",Runnable.class);
            listenerType=Class.forName("org.geysermc.mcprotocollib.network.event.session.SessionListener");
            listener=Proxy.newProxyInstance(listenerType.getClassLoader(),new Class<?>[]{listenerType},(proxy,method,args)->{
                if(method.getDeclaringClass()==Object.class)return switch(method.getName()){
                    case "equals" -> proxy==args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    default -> "FlyingStaffDiagnostics";
                };
                if(!stopped&&method.getName().equals("packetReceived")&&args!=null&&args.length==2){
                    try{packet(args[1]);}catch(ReflectiveOperationException error){fail(error);}
                }
                // Never cancel, replace, resend or modify packets.
                return null;
            });
        }
        void count(String name){counts.computeIfAbsent(name,key->new AtomicInteger()).incrementAndGet();}
        void packet(Object packet) throws ReflectiveOperationException {
            String name=packet.getClass().getSimpleName();
            switch(name){
                case "ClientboundLevelChunkWithLightPacket" -> {
                    count("chunks");
                    String chunk=call(packet,"getX")+","+call(packet,"getZ");
                    if(chunks.size()<8192&&!chunks.add(chunk))count("chunksRepeated");
                }
                case "ClientboundForgetLevelChunkPacket" -> count("chunkUnloads");
                case "ClientboundChunksBiomesPacket" -> count("biomeBatches");
                case "ClientboundSetChunkCacheCenterPacket" -> count("chunkCenters");
                case "ClientboundRespawnPacket" -> count("respawns");
                case "ClientboundPlayerPositionPacket" -> {
                    count("playerCorrections");
                    lastCorrection=call(packet,"getPosition")+" relative="+call(packet,"getRelatives");
                }
                case "ClientboundSetPassengersPacket" -> {
                    if(((Number)call(packet,"getEntityId")).intValue()==mountId){
                        count("mountLinks");lastPassengers=Arrays.toString((int[])call(packet,"getPassengerIds"));
                    }
                }
                case "ClientboundTeleportEntityPacket","ClientboundEntityPositionSyncPacket" -> {
                    if(((Number)call(packet,"getId")).intValue()==mountId)count("mountPositionSyncs");
                }
                default -> {}
            }
        }
        void execute(Runnable task){try{execute.invoke(connection,task);}catch(ReflectiveOperationException error){fail(error);}}
        void sample(String server){
            if(stopped)return;
            try {
                Object entity=call(connection,"getPlayerEntity"),vehicle=call(entity,"getVehicle");
                SortedMap<String,Integer> interval=new TreeMap<>();
                counts.forEach((key,value)->{int n=value.getAndSet(0);if(n>0)interval.put(key,n);});
                log(server+" geyser="+call(entity,"position")+" geyserVehicle="+(vehicle==null?"none":call(vehicle,"getEntityId"))+
                    " removedVehicle="+call(entity,"getRemovedPlayerVehicleId")+
                    " chunkCenter="+call(connection,"getLastChunkPosition")+" view="+call(connection,"getServerRenderDistance")+
                    " pendingTeleport="+(call(connection,"getUnconfirmedTeleport")!=null)+
                    " packets="+interval+" lastLinks="+lastPassengers+" lastCorrection="+lastCorrection);
            }catch(ReflectiveOperationException error){fail(error);}
        }
        void fail(Exception error){if(!failed){failed=true;log("ERROR "+error);}}
        void log(String message){plugin.getLogger().info("[StaffTrace "+playerId+"] "+message);}
        void stop(){
            stopped=true;
            execute(()->{
                try{network.getClass().getMethod("removeListener",listenerType).invoke(network,listener);log("END");}
                catch(ReflectiveOperationException error){fail(error);}
            });
        }
    }
}
