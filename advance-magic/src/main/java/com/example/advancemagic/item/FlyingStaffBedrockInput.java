package com.example.advancemagic.item;

import com.example.advancemagic.AdvanceMagicPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * Optional, passive observation of Bedrock analog input before Geyser reduces it to Java buttons.
 * Requires a local Geyser-Spigot connection. Remote/proxy Geyser and unsupported versions retain
 * ordinary Java-button controls, with one warning; they cannot expose the original analog vector here.
 */
public final class FlyingStaffBedrockInput implements AutoCloseable {
    private static final long MAX_AGE_NANOS=500_000_000L;
    private static final String AUTH_INPUT="org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket";
    private final AdvanceMagicPlugin plugin;
    private final Logger logger;
    private final Map<UUID,Binding> bindings=new ConcurrentHashMap<>();
    private final AtomicBoolean warned=new AtomicBoolean();
    private volatile boolean closed;

    /** Bedrock X is positive left; forward is positive ahead. Values retain stick proportions. */
    public record Snapshot(double left,double forward,boolean gamepad,long capturedNanos,boolean jump,boolean sprint) {
        public Snapshot(double left,double forward,boolean gamepad,long capturedNanos){
            this(left,forward,gamepad,capturedNanos,false,false);
        }
    }
    private static final class Binding {
        final UUID owner;
        final Object upstream;
        final Executor executor;
        final Access access;
        final double deadzone;
        volatile boolean active=true;
        volatile boolean failed;
        volatile Snapshot snapshot;
        Object original;
        Object wrapper;
        Binding(UUID owner,Object upstream,Executor executor,Access access,double deadzone){
            this.owner=owner;this.upstream=upstream;this.executor=executor;this.access=access;this.deadzone=deadzone;
        }
    }
    private static final class Access {
        final Method getHandler,setHandler,getMode,getAnalog,getFlags,getX,getY;
        final Class<?> handlerType,authType;
        Access(Object upstream) throws ReflectiveOperationException {
            getHandler=upstream.getClass().getMethod("getPacketHandler");
            handlerType=getHandler.getReturnType();
            if(!handlerType.isInterface())throw new NoSuchMethodException("Bedrock packet handler is not an interface");
            setHandler=upstream.getClass().getMethod("setPacketHandler",handlerType);
            authType=Class.forName(AUTH_INPUT,false,handlerType.getClassLoader());
            getMode=authType.getMethod("getInputMode");
            getAnalog=authType.getMethod("getAnalogMoveVector");
            getFlags=authType.getMethod("getInputData");
            getX=getAnalog.getReturnType().getMethod("getX");
            getY=getAnalog.getReturnType().getMethod("getY");
        }
    }

    public FlyingStaffBedrockInput(AdvanceMagicPlugin plugin){this.plugin=plugin;this.logger=plugin.getLogger();}

    public void mount(Player player){
        if(closed)return;
        UUID owner=player.getUniqueId();
        detach(owner);
        try {
            var geyser=Bukkit.getPluginManager().getPlugin("Geyser-Spigot");
            if(geyser==null||!geyser.isEnabled())throw new IllegalStateException("local Geyser plugin is unavailable");
            ClassLoader loader=geyser.getClass().getClassLoader();
            Class<?> apiType=Class.forName("org.geysermc.geyser.api.GeyserApi",false,loader);
            Object api=apiType.getMethod("api").invoke(null);
            Object connection=apiType.getMethod("connectionByUuid",UUID.class).invoke(api,owner);
            if(connection==null)throw new IllegalStateException("local Bedrock connection is unavailable");
            Object upstream=connection.getClass().getMethod("getUpstream").invoke(connection);
            Object session=upstream.getClass().getMethod("getSession").invoke(upstream);
            Object peer=session.getClass().getMethod("getPeer").invoke(session);
            Object channel=peer.getClass().getMethod("getChannel").invoke(peer);
            Class<?> channelType=Class.forName("io.netty.channel.Channel",false,loader);
            Executor executor=(Executor)channelType.getMethod("eventLoop").invoke(channel);
            double deadzone=plugin.getConfig().getDouble("flying-staff.bedrock-controls.analog-deadzone",.15);
            attach(owner,session,executor,deadzone);
        } catch(ReflectiveOperationException|RuntimeException|LinkageError error){warn(error);}
    }

    public void unmount(Player player){detach(player.getUniqueId());}

    /** null requests Java-button fallback; an expired recognized gamepad remains neutral. */
    public Snapshot sample(Player player){return sample(player.getUniqueId(),System.nanoTime());}

    // Package access keeps isolated regression probes independent of Bukkit player/login creation.
    void attach(UUID owner,Object upstream,Executor executor,double deadzone) throws ReflectiveOperationException {
        if(closed)return;
        detach(owner);
        var binding=new Binding(owner,upstream,executor,new Access(upstream),safeDeadzone(deadzone));
        bindings.put(owner,binding);
        try {executor.execute(()->install(binding));}
        catch(RuntimeException error){binding.failed=true;binding.active=false;bindings.remove(owner,binding);warn(error);}
    }
    Snapshot sample(UUID owner,long now){
        Binding binding=bindings.get(owner);
        if(binding==null||!binding.active||binding.failed)return null;
        Snapshot snapshot=binding.snapshot;
        if(snapshot==null||!snapshot.gamepad())return null;
        if(now-snapshot.capturedNanos()>MAX_AGE_NANOS)
            return new Snapshot(0,0,true,snapshot.capturedNanos(),false,false);
        return snapshot;
    }
    void detach(UUID owner){
        Binding binding=bindings.remove(owner);
        if(binding==null)return;
        binding.active=false;
        binding.snapshot=null;
        try {binding.executor.execute(()->restore(binding));}
        catch(RuntimeException error){warn(error);}
    }

    private void install(Binding binding){
        if(closed||!binding.active)return;
        try {
            binding.original=binding.access.getHandler.invoke(binding.upstream);
            if(binding.original==null)throw new IllegalStateException("Bedrock packet handler is unavailable");
            binding.wrapper=Proxy.newProxyInstance(binding.access.handlerType.getClassLoader(),
                new Class<?>[]{binding.access.handlerType},(proxy,method,args)->{
                    if(binding.active&&!binding.failed&&args!=null&&args.length==1&&binding.access.authType.isInstance(args[0])){
                        try {capture(binding,args[0]);}
                        catch(ReflectiveOperationException|RuntimeException|LinkageError error){
                            binding.failed=true;
                            binding.snapshot=null;
                            warn(error);
                        }
                    }
                    // Invoke the original exactly once, preserving its result and original exception.
                    try {return method.invoke(binding.original,args);}
                    catch(InvocationTargetException error){throw error.getCause();}
                });
            binding.access.setHandler.invoke(binding.upstream,binding.wrapper);
        } catch(ReflectiveOperationException|RuntimeException|LinkageError error){binding.failed=true;warn(error);}
    }
    private void capture(Binding binding,Object packet) throws ReflectiveOperationException {
        boolean gamepad="GAMEPAD".equals(String.valueOf(binding.access.getMode.invoke(packet)));
        long now=System.nanoTime();
        if(!gamepad){binding.snapshot=new Snapshot(0,0,false,now);return;}
        Object vector=binding.access.getAnalog.invoke(packet);
        double left=vector==null?0:((Number)binding.access.getX.invoke(vector)).doubleValue();
        double forward=vector==null?0:((Number)binding.access.getY.invoke(vector)).doubleValue();
        boolean jump=false,sprintHeld=false,sprintState=false,sprintStop=false;
        Object data=binding.access.getFlags.invoke(packet);
        if(data instanceof Set<?> flags)for(Object flag:flags){
            String name=String.valueOf(flag);
            if(name.equals("JUMP_CURRENT_RAW")||name.equals("JUMP_DOWN")||name.equals("AUTO_JUMPING_IN_WATER"))jump=true;
            if(name.equals("SPRINT_DOWN"))sprintHeld=true;
            if(name.equals("SPRINTING")||name.equals("START_SPRINTING"))sprintState=true;
            if(name.equals("STOP_SPRINTING"))sprintStop=true;
        }
        Snapshot movement=normalize(left,forward,binding.deadzone,now);
        // A controller may toggle sprint without holding L3. Stop cancels the sprint state/start
        // edge, while an explicitly held sprint button remains an independent Turbo request.
        boolean sprint=sprintHeld||(sprintState&&!sprintStop);
        binding.snapshot=new Snapshot(movement.left(),movement.forward(),true,now,jump,sprint);
    }
    static Snapshot normalize(double left,double forward,double deadzone,long now){
        if(!Double.isFinite(left)||!Double.isFinite(forward))return new Snapshot(0,0,true,now);
        left=Math.clamp(left,-1,1);forward=Math.clamp(forward,-1,1);
        double length=Math.hypot(left,forward);
        deadzone=safeDeadzone(deadzone);
        if(length<=deadzone)return new Snapshot(0,0,true,now);
        double magnitude=(Math.min(length,1)-deadzone)/(1-deadzone);
        return new Snapshot(left/length*magnitude,forward/length*magnitude,true,now);
    }
    private static double safeDeadzone(double value){return Double.isFinite(value)?Math.clamp(value,0,.5):.15;}
    private void restore(Binding binding){
        if(binding.wrapper==null)return;
        try {
            // A later Geyser/plugin replacement owns the handler; do not overwrite it.
            if(binding.access.getHandler.invoke(binding.upstream)==binding.wrapper)
                binding.access.setHandler.invoke(binding.upstream,binding.original);
        } catch(ReflectiveOperationException|RuntimeException|LinkageError error){warn(error);}
    }
    private void warn(Throwable error){
        if(!warned.compareAndSet(false,true))return;
        if(error instanceof InvocationTargetException invocation&&invocation.getCause()!=null)error=invocation.getCause();
        logger.warning("Flying staff Bedrock analog input is unavailable; using movement buttons ("+
            error.getClass().getSimpleName()+": "+error.getMessage()+").");
    }
    @Override public void close(){closed=true;for(UUID owner:bindings.keySet().toArray(UUID[]::new))detach(owner);}
}
