import org.cloudburstmc.math.vector.Vector2f;
import org.cloudburstmc.protocol.bedrock.BedrockServerSession;
import org.cloudburstmc.protocol.bedrock.data.InputInteractionModel;
import org.cloudburstmc.protocol.bedrock.data.InputMode;
import org.cloudburstmc.protocol.bedrock.data.PlayerAuthInputData;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.InteractPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacketHandler;
import org.cloudburstmc.protocol.bedrock.packet.TextPacket;
import org.cloudburstmc.protocol.bedrock.packet.AnimatePacket;
import org.cloudburstmc.protocol.common.PacketSignal;
import com.example.advancemagic.AdvanceMagicPlugin;
import com.example.advancemagic.item.FlyingStaffBedrockInput;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.entity.VanillaEntities;
import org.geysermc.geyser.entity.spawn.EntitySpawnContext;
import org.geysermc.geyser.entity.type.living.ArmorStandEntity;
import org.geysermc.geyser.network.bedrock.GameProtocol;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.translator.protocol.bedrock.entity.player.BedrockInteractTranslator;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.level.ServerboundPlayerInputPacket;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

/**
 * Executes the installed Geyser InputCache and dismount translator on an unconnected session.
 * Requires a fully enabled Geyser plugin on an isolated Paper server. Never shipped.
 * Transport, sneaking side effects, and delayed remount are stubbed; conversion is real.
 * This does not simulate a Bedrock client, physical controller, packet decoding, or rendering.
 */
public final class GeyserGamepadChecks {
    private GeyserGamepadChecks() {}

    private static final class ProbeSession extends GeyserSession {
        final List<ServerboundPlayerInputPacket> inputs=new ArrayList<>();
        boolean sneaking;
        int scheduledRemounts;
        ProbeSession(){this(null);}
        ProbeSession(BedrockServerSession upstream){super(GeyserImpl.getInstance(),upstream,null);}
        @Override public int protocolVersion(){return GameProtocol.DEFAULT_BEDROCK_PROTOCOL;}
        @Override public void sendDownstreamGamePacket(Packet packet){
            if(packet instanceof ServerboundPlayerInputPacket input)inputs.add(input);
        }
        @Override public void sendUpstreamPacket(BedrockPacket packet){}
        @Override public void sendUpstreamPacketImmediately(BedrockPacket packet){}
        @Override public boolean isSneaking(){return sneaking;}
        @Override public void startSneaking(boolean update){sneaking=true;}
        @Override public void stopSneaking(boolean update){sneaking=false;}
        @Override public boolean attemptToBlock(){return false;}
        @Override public ScheduledFuture<?> scheduleInEventLoop(Runnable task,long delay,TimeUnit unit){
            // Do not enqueue a delayed mount or start a network/event-loop thread.
            scheduledRemounts++;
            return null;
        }
        ServerboundPlayerInputPacket process(PlayerAuthInputPacket packet){
            getInputCache().processInputs(getPlayerEntity(),packet);
            return inputs.isEmpty()?new ServerboundPlayerInputPacket(false,false,false,false,false,false,false):inputs.getLast();
        }
    }

    private static PlayerAuthInputPacket packet(InputMode mode,InputInteractionModel model,float x,float y,
                                               PlayerAuthInputData... flags){
        var packet=new PlayerAuthInputPacket();
        packet.setInputMode(mode);
        packet.setInputInteractionModel(model);
        packet.setAnalogMoveVector(Vector2f.from(x,y));
        packet.setMotion(Vector2f.from(x,y));
        java.util.Collections.addAll(packet.getInputData(),flags);
        return packet;
    }
    private static PlayerAuthInputPacket gamepad(float x,float y,PlayerAuthInputData... flags){
        return packet(InputMode.GAMEPAD,InputInteractionModel.CROSSHAIR,x,y,flags);
    }
    private static String bits(ServerboundPlayerInputPacket p){
        return ""+(p.isForward()?1:0)+(p.isBackward()?1:0)+(p.isLeft()?1:0)+(p.isRight()?1:0)
            +(p.isJump()?1:0)+(p.isShift()?1:0)+(p.isSprint()?1:0);
    }
    private static void expect(BiConsumer<Boolean,String> check,ProbeSession session,PlayerAuthInputPacket packet,
                               String expected,String label){
        String actual=bits(session.process(packet));
        check.accept(expected.equals(actual),"Geyser "+label+" [F B L R Jump Shift Sprint="+actual+"]");
    }

    public static void run(BiConsumer<Boolean,String> check) throws Exception {
        check.accept(GeyserImpl.getInstance()!=null,"Geyser is fully loaded for actual input conversion checks");
        var session=new ProbeSession();
        check.accept(GeyserImpl.getInstance().getSessionManager().getSessions().values().stream().noneMatch(s->s==session),
            "Geyser input probe is unregistered and has no live login");

        expect(check,session,gamepad(0,0),"0000000","neutral gamepad has no directions");
        for(float amount:new float[]{.0001f,.01f,.25f,.5f,1f}){
            expect(check,session,gamepad(0,amount),"1000000","forward magnitude "+amount+" becomes the same Boolean");
            expect(check,session,gamepad(0,-amount),"0100000","backward magnitude "+amount);
            expect(check,session,gamepad(amount,0),"0010000","positive X is left at magnitude "+amount);
            expect(check,session,gamepad(-amount,0),"0001000","negative X is right at magnitude "+amount);
        }
        expect(check,session,gamepad(.001f,1),"1010000","tiny sideways component makes full forward-left input");
        expect(check,session,gamepad(-.001f,1),"1001000","tiny sideways component makes full forward-right input");
        expect(check,session,gamepad(.9f,.1f),"1010000","unequal forward-left analog proportions are lost");
        expect(check,session,gamepad(.1f,.9f),"1010000","opposite analog proportions produce identical directions");
        expect(check,session,gamepad(1,-1),"0110000","backward-left quadrant");
        expect(check,session,gamepad(-1,-1),"0101000","backward-right quadrant");
        expect(check,session,gamepad(0,0,PlayerAuthInputData.UP,PlayerAuthInputData.LEFT),"0000000",
            "gamepad uses analog vector instead of keyboard direction flags");
        expect(check,session,packet(InputMode.MOUSE,InputInteractionModel.CROSSHAIR,1,1),"0000000",
            "keyboard mode ignores analog vector without direction flags");
        expect(check,session,packet(InputMode.MOUSE,InputInteractionModel.CROSSHAIR,0,0,PlayerAuthInputData.UP,PlayerAuthInputData.RIGHT),
            "1001000","keyboard UP+RIGHT flags remain forward-right");
        expect(check,session,packet(InputMode.TOUCH,InputInteractionModel.CLASSIC,0,0,PlayerAuthInputData.DOWN_LEFT),
            "0110000","classic touch corner direction is recognized");
        expect(check,session,packet(InputMode.TOUCH,InputInteractionModel.CROSSHAIR,-.1f,.2f),
            "1001000","crosshair touch uses analog signs too");

        for(var jump:new PlayerAuthInputData[]{PlayerAuthInputData.JUMP_CURRENT_RAW,PlayerAuthInputData.JUMP_DOWN,
                                              PlayerAuthInputData.AUTO_JUMPING_IN_WATER}){
            expect(check,session,gamepad(0,0,jump),"0000100",jump+" enables Jump");
            expect(check,session,gamepad(0,0),"0000000",jump+" clears on release");
        }
        expect(check,session,gamepad(0,0,PlayerAuthInputData.JUMP_PRESSED_RAW),"0000000",
            "Jump pressed edge alone is not the held Jump flag");
        expect(check,session,gamepad(0,0,PlayerAuthInputData.JUMPING),"0000000",
            "physical jumping state alone is not the held Jump flag");
        expect(check,session,gamepad(0,0,PlayerAuthInputData.SPRINT_DOWN),"0000001","held Sprint reaches input");
        expect(check,session,gamepad(0,0),"0000000","Sprint clears on release");
        expect(check,session,gamepad(0,0,PlayerAuthInputData.START_SPRINTING),"0000000",
            "Sprint command edge alone is separate from held Sprint input");
        session.setSprinting(true);
        expect(check,session,gamepad(0,0),"0000000","session sprint state does not set held Sprint input");
        session.setSprinting(false);

        var held=gamepad(-.1f,1,PlayerAuthInputData.JUMP_CURRENT_RAW,PlayerAuthInputData.SPRINT_DOWN);
        expect(check,session,held,"1001101","movement+Jump+Sprint can be combined");
        int sent=session.inputs.size();
        for(int tick=0;tick<40;tick++)session.process(held);
        check.accept(session.inputs.size()==sent,"Geyser unchanged held input is not resent every tick");
        expect(check,session,gamepad(0,0),"0000000","all held movement/Jump/Sprint clear together");
        sent=session.inputs.size();
        for(int tick=0;tick<40;tick++)session.process(gamepad(0,0));
        check.accept(session.inputs.size()==sent,"Geyser repeated neutral input does not emit duplicate packets");

        expect(check,session,gamepad(0,0,PlayerAuthInputData.START_SNEAKING),"0000010","sneak start becomes Shift");
        expect(check,session,gamepad(0,0),"0000010","sneaking persists until a stop edge");
        expect(check,session,gamepad(0,0,PlayerAuthInputData.STOP_SNEAKING),"0000000","sneak stop clears Shift");
        expect(check,session,gamepad(0,0,PlayerAuthInputData.SNEAK_CURRENT_RAW),"0000000",
            "raw Sneak alone does not set normal non-flight Shift");
        expect(check,session,gamepad(0,0,PlayerAuthInputData.DESCEND),"0000000",
            "creative-flight Descend flag alone is ignored for this non-flying rider");

        var mount=new ArmorStandEntity(new EntitySpawnContext(session,VanillaEntities.ARMOR_STAND,12345,UUID.randomUUID()));
        session.getPlayerEntity().setVehicle(mount);
        expect(check,session,gamepad(0,1,PlayerAuthInputData.JUMP_CURRENT_RAW,PlayerAuthInputData.SPRINT_DOWN),
            "1000101","ArmorStand passenger still forwards Jump+Sprint+movement");
        expect(check,session,gamepad(0,0),"0000000","ArmorStand passenger input clears normally");
        var dismount=new InteractPacket();
        dismount.setRuntimeEntityId(session.getPlayerEntity().geyserId());
        dismount.setAction(InteractPacket.Action.LEAVE_VEHICLE);
        var translator=new BedrockInteractTranslator();
        for(int attempt=0;attempt<3;attempt++){
            translator.translate(session,dismount);
            check.accept(session.isShouldSendSneak(),"Geyser actual dismount translator queues Shift attempt "+attempt);
            expect(check,session,gamepad(0,0),"0000010","dismount produces one-shot Shift attempt "+attempt);
            check.accept(!session.isShouldSendSneak(),"Geyser dismount Shift request is consumed attempt "+attempt);
            expect(check,session,gamepad(0,0),"0000000","dismount Shift releases on next input attempt "+attempt);
        }
        check.accept(session.scheduledRemounts==3,"Geyser each dismount queues a delayed reconciliation (captured, never executed)");
        session.getPlayerEntity().setVehicle(null);
        translator.translate(session,dismount);
        check.accept(!session.isShouldSendSneak()&&session.scheduledRemounts==3,
            "Geyser repeated dismount after leaving vehicle does not queue Shift or remount");
        for(int repeat=0;repeat<20;repeat++){
            expect(check,session,held,"1001101","repeat "+repeat+" holds input cleanly");
            expect(check,session,gamepad(0,0),"0000000","repeat "+repeat+" releases input cleanly");
        }
        runAnalogBridge(check);
    }

    private static void runAnalogBridge(BiConsumer<Boolean,String> check) throws Exception {
        var plugin=(AdvanceMagicPlugin)org.bukkit.Bukkit.getPluginManager().getPlugin("advance-magic");
        var bridge=new FlyingStaffBedrockInput(plugin);
        var type=FlyingStaffBedrockInput.class;
        var attach=type.getDeclaredMethod("attach",UUID.class,Object.class,java.util.concurrent.Executor.class,double.class);
        var detach=type.getDeclaredMethod("detach",UUID.class);
        var sample=type.getDeclaredMethod("sample",UUID.class,long.class);
        var normalize=type.getDeclaredMethod("normalize",double.class,double.class,double.class,long.class);
        for(var method:new java.lang.reflect.Method[]{attach,detach,sample,normalize})method.setAccessible(true);
        // Exercise the exact reflective getter chain used by mount, with real installed Geyser/Netty
        // objects and an in-memory channel. It is never registered, connected, or logged in.
        var memoryChannel=new io.netty.channel.embedded.EmbeddedChannel();
        try {
            var memoryPeer=new org.cloudburstmc.protocol.bedrock.BedrockPeer(memoryChannel,null);
            var memoryUpstream=new BedrockServerSession(memoryPeer,0);
            var memoryConnection=new ProbeSession(memoryUpstream);
            var geyserPlugin=org.bukkit.Bukkit.getPluginManager().getPlugin("Geyser-Spigot");
            ClassLoader loader=geyserPlugin.getClass().getClassLoader();
            Class<?> apiType=Class.forName("org.geysermc.geyser.api.GeyserApi",false,loader);
            Object api=apiType.getMethod("api").invoke(null);
            check.accept(api!=null&&apiType.getMethod("connectionByUuid",UUID.class).invoke(api,UUID.randomUUID())==null,
                "analog bridge actual Geyser API discovery is available and missing connection stays absent");
            Object discoveredUpstream=memoryConnection.getClass().getMethod("getUpstream").invoke(memoryConnection);
            Object discoveredSession=discoveredUpstream.getClass().getMethod("getSession").invoke(discoveredUpstream);
            Object discoveredPeer=discoveredSession.getClass().getMethod("getPeer").invoke(discoveredSession);
            Object discoveredChannel=discoveredPeer.getClass().getMethod("getChannel").invoke(discoveredPeer);
            Object discoveredLoop=Class.forName("io.netty.channel.Channel",false,loader).getMethod("eventLoop").invoke(discoveredChannel);
            check.accept(discoveredSession==memoryUpstream&&discoveredChannel==memoryChannel&&discoveredLoop==memoryChannel.eventLoop(),
                "analog bridge exact mount getter chain resolves actual Geyser upstream and Netty event loop");
            var memoryHandler=new BedrockPacketHandler(){};memoryUpstream.setPacketHandler(memoryHandler);
            UUID memoryOwner=UUID.randomUUID();
            attach.invoke(bridge,memoryOwner,discoveredSession,(java.util.concurrent.Executor)discoveredLoop,.15);
            memoryChannel.runPendingTasks();
            check.accept(memoryUpstream.getPacketHandler()!=memoryHandler,"analog bridge installs using the discovered actual event loop");
            detach.invoke(bridge,memoryOwner);memoryChannel.runPendingTasks();
            check.accept(memoryUpstream.getPacketHandler()==memoryHandler,"analog bridge restores through the discovered actual event loop");
        } finally {memoryChannel.finishAndReleaseAll();}
        java.util.function.Function<double[],FlyingStaffBedrockInput.Snapshot> norm=values->{
            try{return (FlyingStaffBedrockInput.Snapshot)normalize.invoke(null,values[0],values[1],values[2],1L);}
            catch(Exception error){throw new RuntimeException(error);}
        };
        var zero=norm.apply(new double[]{.1,.05,.15});
        check.accept(zero.left()==0&&zero.forward()==0,"analog bridge radial deadzone suppresses centered stick drift");
        var half=norm.apply(new double[]{0,.5,.15});
        check.accept(Math.abs(half.forward()-(.5-.15)/.85)<1e-9,"analog bridge remaps partial stick magnitude after deadzone");
        var diagonal=norm.apply(new double[]{.5,.25,.15});
        check.accept(Math.abs(diagonal.left()/diagonal.forward()-2)<1e-9,"analog bridge preserves unequal axis proportions");
        var full=norm.apply(new double[]{-1,1,.15});
        check.accept(full.left()<0&&full.forward()>0&&Math.abs(Math.hypot(full.left(),full.forward())-1)<1e-9,
            "analog bridge caps diagonal magnitude while preserving signs");
        check.accept(norm.apply(new double[]{Double.NaN,1,.15}).forward()==0,"analog bridge nonfinite input is neutral");
        check.accept(norm.apply(new double[]{0,.5,-1}).forward()==.5,"analog bridge negative deadzone clamps to zero");
        check.accept(norm.apply(new double[]{0,.5,1}).forward()==0,"analog bridge high deadzone clamps to one half");
        check.accept(Math.abs(norm.apply(new double[]{0,.5,Double.NaN}).forward()-half.forward())<1e-9,
            "analog bridge invalid deadzone uses the default");

        // Real upstream session class, no peer/connection/network. This queue models its event-loop boundary.
        var upstream=new BedrockServerSession(null,0);
        var tasks=new java.util.ArrayDeque<Runnable>();
        java.util.concurrent.Executor eventLoop=tasks::addLast;
        int[] calls={0,0,0};
        RuntimeException marker=new RuntimeException("expected original handler exception");
        var original=new BedrockPacketHandler(){
            @Override public PacketSignal handle(PlayerAuthInputPacket packet){calls[0]++;return PacketSignal.HANDLED;}
            @Override public PacketSignal handle(TextPacket packet){calls[1]++;return PacketSignal.UNHANDLED;}
            @Override public PacketSignal handle(AnimatePacket packet){throw marker;}
            @Override public void onDisconnect(CharSequence reason){calls[2]++;}
        };
        upstream.setPacketHandler(original);
        UUID owner=UUID.randomUUID();
        attach.invoke(bridge,owner,upstream,eventLoop,.15);
        check.accept(upstream.getPacketHandler()==original,"analog bridge attachment waits for packet event loop");
        while(!tasks.isEmpty())tasks.removeFirst().run();
        BedrockPacketHandler wrapper=upstream.getPacketHandler();
        check.accept(wrapper!=original,"analog bridge installs a passive wrapper on the real upstream session");
        var input=gamepad(.5f,.25f,PlayerAuthInputData.JUMP_CURRENT_RAW,PlayerAuthInputData.SPRINT_DOWN);
        var vector=input.getAnalogMoveVector();
        var flags=Set.copyOf(input.getInputData());
        check.accept(wrapper.handlePacket(input)==PacketSignal.HANDLED&&calls[0]==1,
            "analog bridge generic dispatch preserves original result and forwards once");
        check.accept(input.getAnalogMoveVector()==vector&&input.getInputData().equals(flags),
            "analog bridge leaves input packet vector and flags untouched");
        var captured=(FlyingStaffBedrockInput.Snapshot)sample.invoke(bridge,owner,System.nanoTime());
        check.accept(captured!=null&&captured.gamepad()&&captured.jump()&&captured.sprint()
            &&Math.abs(captured.left()/captured.forward()-2)<1e-9,"analog bridge captures immutable analog and held flags");
        input.setAnalogMoveVector(Vector2f.ZERO);input.getInputData().clear();
        var stable=(FlyingStaffBedrockInput.Snapshot)sample.invoke(bridge,owner,System.nanoTime());
        check.accept(stable.equals(captured),"analog bridge snapshot survives later packet mutation");
        var expired=(FlyingStaffBedrockInput.Snapshot)sample.invoke(bridge,owner,captured.capturedNanos()+500_000_001L);
        check.accept(expired.gamepad()&&expired.left()==0&&expired.forward()==0&&!expired.jump()&&!expired.sprint(),
            "analog bridge expired gamepad stays neutral instead of reviving stale Boolean movement");
        check.accept(wrapper.handle(new TextPacket())==PacketSignal.UNHANDLED&&calls[1]==1,
            "analog bridge unrelated packet result and dispatch remain unchanged");
        boolean sameException=false;
        try{wrapper.handle(new AnimatePacket());}catch(RuntimeException error){sameException=error==marker;}
        check.accept(sameException,"analog bridge preserves original handler exception identity");
        wrapper.onDisconnect("probe");
        check.accept(calls[2]==1,"analog bridge forwards disconnect callback once");
        wrapper.handle(packet(InputMode.MOUSE,InputInteractionModel.CROSSHAIR,.5f,.5f));
        check.accept(sample.invoke(bridge,owner,System.nanoTime())==null,"analog bridge non-gamepad mode requests Boolean fallback");
        wrapper.handle(gamepad(.1f,.05f));
        var centered=(FlyingStaffBedrockInput.Snapshot)sample.invoke(bridge,owner,System.nanoTime());
        check.accept(centered!=null&&centered.left()==0&&centered.forward()==0,"analog bridge packet capture applies deadzone");
        for(var sprintFlag:new PlayerAuthInputData[]{PlayerAuthInputData.SPRINT_DOWN,PlayerAuthInputData.SPRINTING,
                                                    PlayerAuthInputData.START_SPRINTING}){
            wrapper.handle(gamepad(0,1,sprintFlag));
            var sprintSnapshot=(FlyingStaffBedrockInput.Snapshot)sample.invoke(bridge,owner,System.nanoTime());
            check.accept(sprintSnapshot.sprint(),"analog bridge "+sprintFlag+" keeps controller Turbo available");
        }
        wrapper.handle(gamepad(0,1,PlayerAuthInputData.SPRINTING,PlayerAuthInputData.START_SPRINTING,PlayerAuthInputData.STOP_SPRINTING));
        check.accept(!((FlyingStaffBedrockInput.Snapshot)sample.invoke(bridge,owner,System.nanoTime())).sprint(),
            "analog bridge Stop Sprint overrides sprint state and start edge");
        wrapper.handle(gamepad(0,1,PlayerAuthInputData.SPRINT_DOWN,PlayerAuthInputData.STOP_SPRINTING));
        check.accept(((FlyingStaffBedrockInput.Snapshot)sample.invoke(bridge,owner,System.nanoTime())).sprint(),
            "analog bridge explicitly held Sprint remains a Turbo request alongside Stop Sprint");
        wrapper.handle(gamepad(0,1,PlayerAuthInputData.STOP_SPRINTING));
        check.accept(!((FlyingStaffBedrockInput.Snapshot)sample.invoke(bridge,owner,System.nanoTime())).sprint(),
            "analog bridge Stop Sprint alone releases controller Turbo");
        wrapper.handle(gamepad(0,1));
        check.accept(!((FlyingStaffBedrockInput.Snapshot)sample.invoke(bridge,owner,System.nanoTime())).sprint(),
            "analog bridge cleared sprint flags do not latch Turbo");
        detach.invoke(bridge,owner);
        check.accept(sample.invoke(bridge,owner,System.nanoTime())==null,"analog bridge unmount clears captured input immediately");
        check.accept(upstream.getPacketHandler()==wrapper,"analog bridge restoration waits for packet event loop");
        while(!tasks.isEmpty())tasks.removeFirst().run();
        check.accept(upstream.getPacketHandler()==original,"analog bridge unmount restores exact original handler");
        for(int repeat=0;repeat<10;repeat++){
            attach.invoke(bridge,owner,upstream,eventLoop,.15);
            while(!tasks.isEmpty())tasks.removeFirst().run();
            upstream.getPacketHandler().handle(gamepad(0,.5f));
            detach.invoke(bridge,owner);
            while(!tasks.isEmpty())tasks.removeFirst().run();
        }
        check.accept(upstream.getPacketHandler()==original,"analog bridge repeated mount/unmount never stacks wrappers");
        attach.invoke(bridge,owner,upstream,eventLoop,.15);
        detach.invoke(bridge,owner);
        while(!tasks.isEmpty())tasks.removeFirst().run();
        check.accept(upstream.getPacketHandler()==original,"analog bridge unmount before queued attach leaves original handler");
        attach.invoke(bridge,owner,upstream,eventLoop,.15);
        while(!tasks.isEmpty())tasks.removeFirst().run();
        var replacement=new BedrockPacketHandler(){};
        upstream.setPacketHandler(replacement);
        detach.invoke(bridge,owner);
        while(!tasks.isEmpty())tasks.removeFirst().run();
        check.accept(upstream.getPacketHandler()==replacement,"analog bridge detach respects a later handler replacement");
        upstream.setPacketHandler(original);
        attach.invoke(bridge,owner,upstream,eventLoop,.15);
        while(!tasks.isEmpty())tasks.removeFirst().run();
        bridge.close();
        while(!tasks.isEmpty())tasks.removeFirst().run();
        check.accept(upstream.getPacketHandler()==original&&sample.invoke(bridge,owner,System.nanoTime())==null,
            "analog bridge close restores handler and clears all snapshots");
    }

    /** Installs real packet observation on an unconnected upstream for service-level regression checks. */
    public static AutoCloseable bindAnalog(FlyingStaffBedrockInput bridge,UUID owner,double left,double forward,
                                          boolean jump,boolean sprint,long ageNanos) throws Exception {
        var attach=FlyingStaffBedrockInput.class.getDeclaredMethod("attach",UUID.class,Object.class,java.util.concurrent.Executor.class,double.class);
        var detach=FlyingStaffBedrockInput.class.getDeclaredMethod("detach",UUID.class);
        attach.setAccessible(true);detach.setAccessible(true);
        var upstream=new BedrockServerSession(null,0);
        upstream.setPacketHandler(new BedrockPacketHandler(){});
        attach.invoke(bridge,owner,upstream,(java.util.concurrent.Executor)Runnable::run,.15);
        var packet=gamepad((float)left,(float)forward);
        if(jump)packet.getInputData().add(PlayerAuthInputData.JUMP_CURRENT_RAW);
        if(sprint)packet.getInputData().add(PlayerAuthInputData.SPRINT_DOWN);
        upstream.getPacketHandler().handle(packet);
        if(ageNanos>0){
            var mapField=FlyingStaffBedrockInput.class.getDeclaredField("bindings");mapField.setAccessible(true);
            Object binding=((java.util.Map<?,?>)mapField.get(bridge)).get(owner);
            var snapshotField=binding.getClass().getDeclaredField("snapshot");snapshotField.setAccessible(true);
            var snapshot=(FlyingStaffBedrockInput.Snapshot)snapshotField.get(binding);
            snapshotField.set(binding,new FlyingStaffBedrockInput.Snapshot(snapshot.left(),snapshot.forward(),true,
                snapshot.capturedNanos()-ageNanos,snapshot.jump(),snapshot.sprint()));
        }
        return ()->detach.invoke(bridge,owner);
    }
}
