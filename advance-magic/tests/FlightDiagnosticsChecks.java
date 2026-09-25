import com.example.advancemagic.AdvanceMagicPlugin;
import org.geysermc.mcprotocollib.network.Session;
import org.geysermc.mcprotocollib.network.event.session.SessionListener;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundSetPassengersPacket;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Exercises the observer against the installed Geyser packet interface without a Bedrock login. */
public final class FlightDiagnosticsChecks {
    public static final class Network {
        final List<SessionListener> listeners=new ArrayList<>();
        public void addListener(SessionListener listener){listeners.add(listener);}
        public void removeListener(SessionListener listener){listeners.remove(listener);}
    }
    public static final class Downstream {
        final Network network=new Network();
        public Network getSession(){return network;}
    }
    public static final class EntitySnapshot {
        public Object getVehicle(){return null;}
        public Object position(){return "(1, 100, 1)";}
        public Object getRemovedPlayerVehicleId(){return null;}
    }
    public static final class Connection {
        final Downstream downstream=new Downstream();
        public Downstream getDownstream(){return downstream;}
        public void executeInEventLoop(Runnable task){task.run();}
        public EntitySnapshot getPlayerEntity(){return new EntitySnapshot();}
        public Object getLastChunkPosition(){return "(0, 0)";}
        public int getServerRenderDistance(){return 10;}
        public Object getUnconfirmedTeleport(){return null;}
    }
    static Object field(Object object,String name) throws Exception {
        Field field=object.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(object);
    }
    static void invoke(Object object,String name,Class<?>[] types,Object...args) throws Exception {
        Method method=object.getClass().getDeclaredMethod(name,types);method.setAccessible(true);method.invoke(object,args);
    }
    static void run(FlyingStaffChecks checks,AdvanceMagicPlugin plugin) throws Exception {
        // Fail on missing methods in the real installed Geyser classes before distributing the observer.
        Class<?> session=Class.forName("org.geysermc.geyser.session.GeyserSession");
        for(String method:List.of("getPlayerEntity","getDownstream","getLastChunkPosition","getServerRenderDistance","getUnconfirmedTeleport"))
            session.getMethod(method);
        session.getMethod("executeInEventLoop",Runnable.class);
        Class<?> entity=Class.forName("org.geysermc.geyser.entity.type.player.SessionPlayerEntity");
        for(String method:List.of("getVehicle","position","getRemovedPlayerVehicleId","getEntityId"))entity.getMethod(method);
        checks.check(true,"trace reflection matches installed Geyser methods");
        Class<?> outer=Class.forName("com.example.advancemagic.item.FlyingStaffDiagnostics");
        Constructor<?> outerConstructor=outer.getDeclaredConstructor(AdvanceMagicPlugin.class);outerConstructor.setAccessible(true);
        Object diagnostics=outerConstructor.newInstance(plugin);
        Class<?> inner=Class.forName(outer.getName()+"$Trace");
        Constructor<?> constructor=inner.getDeclaredConstructors()[0];constructor.setAccessible(true);
        Connection connection=new Connection();
        Object trace=constructor.newInstance(diagnostics,UUID.randomUUID(),42,connection);
        SessionListener listener=(SessionListener)field(trace,"listener");
        connection.downstream.network.addListener(listener);
        int[] passengers={9};
        var packet=new ClientboundSetPassengersPacket(42,passengers);
        listener.packetReceived((Session)null,packet);
        listener.packetReceived((Session)null,new ClientboundSetPassengersPacket(99,new int[]{10}));
        @SuppressWarnings("unchecked") var counts=(Map<String,AtomicInteger>)field(trace,"counts");
        checks.check(counts.get("mountLinks").get()==1,"trace counts only the observed mount");
        checks.check(Arrays.equals(packet.getPassengerIds(),new int[]{9}),"trace leaves the original packet intact");
        invoke(trace,"sample",new Class<?>[]{String.class},"server=test");
        checks.check(!(boolean)field(trace,"failed")&&counts.get("mountLinks").get()==0,"trace samples and drains interval counters");
        invoke(trace,"stop",new Class<?>[]{});
        checks.check(connection.downstream.network.listeners.isEmpty(),"trace removes its network listener on stop");
        listener.packetReceived((Session)null,packet);
        checks.check(counts.get("mountLinks").get()==0,"stopped trace ignores late packets");
    }
}
