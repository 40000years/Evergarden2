package com.example.advancemagic.spell;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.entity.Entity;
import org.bukkit.util.Vector;
import java.lang.reflect.*;
import java.util.*;

/** Optional Geyser adapter for coloured dust and authored world-space line art. */
final class MythicVisuals {
    private static final double LINE_EMITTER_DISTANCE=12;
    private record Viewer(Player player,Object session,int dimension) {}
    private final MagicContext c;
    private final java.util.function.Predicate<Player> packetPermit;
    private final int range;
    private List<Viewer> viewers=List.of();
    private Bridge bridge;
    private boolean disabled;
    private BlockBridge blockBridge;
    private boolean blocksDisabled;
    MythicVisuals(MagicContext c){this(c,p->true,64);}
    MythicVisuals(MagicContext c,java.util.function.Predicate<Player> packetPermit,int range){this.c=c;this.packetPermit=packetPermit;this.range=range;}
    boolean hasJavaViewers(){return viewers.stream().anyMatch(v->(v.session==null||disabled)&&c.plugin.packs().hasApplied(v.player));}
    boolean hasViewers(){return !viewers.isEmpty();}
    void clear(){viewers=List.of();}

    void frame(Location center) {
        if(bridge==null&&!disabled) {
            var geyser=Bukkit.getPluginManager().getPlugin("Geyser-Spigot");
            if(geyser!=null&&geyser.isEnabled())try{bridge=new Bridge(geyser.getClass().getClassLoader());}
            catch(ReflectiveOperationException|LinkageError e){disable(e);}
        }
        var list=new ArrayList<Viewer>();
        for(Player p:center.getWorld().getPlayers())if(p.isOnline()&&p.getLocation().distanceSquared(center)<=range*range) {
            Object session=null;int dimension=0;
            if(bridge!=null&&!disabled)try {
                session=bridge.connection.invoke(bridge.instance,p.getUniqueId());
                if(session!=null)dimension=(int)bridge.dimension.invoke(null,session);
            }catch(ReflectiveOperationException|LinkageError e){disable(e);session=null;}
            list.add(new Viewer(p,session,dimension));
        }
        viewers=list;
    }
    void dust(Location at,Color color,float size) {
        dust(at,color,size,false);
    }
    void dustFallback(Location at,Color color,float size) {
        dust(at,color,size,true);
    }
    boolean needsSealFallback() {
        return viewers.stream().anyMatch(viewer->(viewer.session==null||disabled)&&!c.plugin.packs().hasApplied(viewer.player));
    }
    private void dust(Location at,Color color,float size,boolean fallbackOnly) {
        if(!c.loaded(at))return;
        var data=new Particle.DustOptions(color,size);
        for(var viewer:viewers) {
            if(!viewer.player.isOnline()||viewer.player.getWorld()!=at.getWorld())continue;
            if(fallbackOnly&&((viewer.session!=null&&!disabled)||c.plugin.packs().hasApplied(viewer.player)))continue;
            if(!packetPermit.test(viewer.player))continue;
            if(viewer.session!=null&&!disabled)try {
                Object packet=bridge.packet.newInstance();
                bridge.identifier.invoke(packet,"advance_magic:mythic_"+tint(color));
                bridge.position.invoke(packet,bridge.vector.invoke(null,at.getX(),at.getY(),at.getZ()));
                bridge.setDimension.invoke(packet,viewer.dimension);
                bridge.variables.invoke(packet,Optional.of("[{\"name\":\"variable.magic_size\",\"value\":{\"type\":\"float\",\"value\":"+size+"}}]"));
                bridge.send.invoke(viewer.session,packet);
                continue;
            }catch(ReflectiveOperationException|LinkageError e){disable(e);}
            viewer.player.spawnParticle(Particle.DUST,at,1,0,0,0,0,data,true);
        }
    }
    void hideBedrock(Entity entity) {
        for(var viewer:viewers)if(viewer.session!=null&&!disabled&&viewer.player.canSee(entity))
            viewer.player.hideEntity(c.plugin,entity);
    }
    void showSeal(Entity entity) {
        for(var viewer:viewers) {
            boolean show=(viewer.session==null||disabled)&&c.plugin.packs().hasApplied(viewer.player);
            if(show&&!viewer.player.canSee(entity))viewer.player.showEntity(c.plugin,entity);
            else if(!show&&viewer.player.canSee(entity))viewer.player.hideEntity(c.plugin,entity);
        }
    }
    /** Shows an entity to Bedrock viewers only (the Bedrock body of a 3D prop). */
    void showBedrockOnly(Entity entity) {
        for(var viewer:viewers) {
            boolean show=viewer.session!=null&&!disabled;
            if(show&&!viewer.player.canSee(entity))viewer.player.showEntity(c.plugin,entity);
            else if(!show&&viewer.player.canSee(entity))viewer.player.hideEntity(c.plugin,entity);
        }
    }
    /** A centred, fully rotatable cube rig; the vanilla falling-block renderer cannot reproduce display transforms. */
    void block(BlockProp prop,Location at,double size,org.joml.Quaternionf rotation,int ticks) {
        var active=new HashMap<UUID,Viewer>();
        if(!disabled&&!blocksDisabled)for(Viewer viewer:viewers)if(viewer.session!=null&&viewer.player.isOnline()&&viewer.player.getWorld()==at.getWorld())active.put(viewer.player.getUniqueId(),viewer);
        for(var entry:List.copyOf(prop.bodies.entrySet())){
            Viewer viewer=active.get(entry.getKey());BlockBody body=entry.getValue();
            if(viewer==null||viewer.session!=body.session||viewer.dimension!=body.dimension||!at.getWorld().getUID().equals(body.world)){
                body.close();prop.bodies.remove(entry.getKey());
            }
        }
        if(active.isEmpty())return;
        if(blockBridge==null)try{
            blockBridge=new BlockBridge(Bukkit.getPluginManager().getPlugin("Geyser-Spigot").getClass().getClassLoader(),c.plugin.blockModels());
        }catch(ReflectiveOperationException|LinkageError failure){blocksDisabled=true;blockFailure(failure);return;}
        for(var entry:active.entrySet()){
            Viewer viewer=entry.getValue();
            BlockBody body=prop.bodies.computeIfAbsent(entry.getKey(),id->new BlockBody(this,blockBridge,viewer.session,viewer.dimension,at.getWorld().getUID(),c.plugin.blockModels().material(prop.material)));
            body.update(at.getX(),at.getY(),at.getZ(),(float)size,rotation.x,rotation.y,rotation.z,rotation.w,ticks);
        }
    }
    private void blockFailure(Throwable failure){
        Throwable cause=failure instanceof InvocationTargetException target&&target.getCause()!=null?target.getCause():failure;
        c.plugin.getLogger().warning("Bedrock block VFX unavailable: "+cause.getClass().getSimpleName()+": "+cause.getMessage());
    }
    static final class BlockProp {
        final String material;
        final Map<UUID,BlockBody> bodies=new HashMap<>();
        BlockProp(Material material){this.material=material.getKey().getKey();}
        void clear(){for(BlockBody body:bodies.values())body.close();bodies.clear();}
    }
    private static final class BlockBody {
        final MythicVisuals owner;final BlockBridge bridge;final Object session;final int dimension,material;final UUID world;
        Object entity;double anchorX,anchorY,anchorZ;float[] previous;int revision;volatile boolean closed,failed;
        BlockBody(MythicVisuals owner,BlockBridge bridge,Object session,int dimension,UUID world,int material){
            this.owner=owner;this.bridge=bridge;this.session=session;this.dimension=dimension;this.world=world;this.material=material;
        }
        void enqueue(Runnable work){
            try{bridge.execute.invoke(session,work);}catch(ReflectiveOperationException|RuntimeException failure){fail(failure);}
        }
        void fail(Throwable failure){if(!failed){failed=true;owner.blockFailure(failure);}}
        void update(double x,double y,double z,float size,float qx,float qy,float qz,float qw,int ticks){
            if(closed||failed)return;
            enqueue(()->{
                if(closed||failed)return;
                try{
                    if(material<0)throw new IllegalArgumentException("Unregistered Legendary block material");
                    if(entity==null){
                        anchorX=x;anchorY=y;anchorZ=z;
                        Object position=bridge.vector.invoke(null,x,y,z);
                        Object context=bridge.context.newInstance(session,bridge.definition,-1,UUID.randomUUID());
                        bridge.contextPosition.invoke(context,position);bridge.contextBedrock.invoke(context,bridge.bedrockDefinition);
                        entity=bridge.entity.newInstance(context);
                        bridge.flag.invoke(entity,bridge.gravity,false);bridge.flag.invoke(entity,bridge.collision,false);
                        bridge.flag.invoke(entity,bridge.noAi,true);bridge.flag.invoke(entity,bridge.silent,true);
                        bridge.width.invoke(entity,0f);bridge.height.invoke(entity,0f);
                        // Bedrock maps bone space to the world as (x, y, -z) at yaw 0 (the convention every
                        // Evergarden boss and pet rig renders with). Facing 180 turns that into (-x, y, z),
                        // the mirrored-X frame the cube rig's Molang encodes; at yaw 0 every prop is
                        // rotated 180 degrees around its own spawn point.
                        bridge.yaw.invoke(entity,180f);bridge.headYaw.invoke(entity,180f);
                    }
                    float[] target={(float)(x-anchorX),(float)(y-anchorY),(float)(z-anchorZ),qx,qy,qz,qw,size};
                    boolean first=previous==null;
                    if(!first&&previous[3]*qx+previous[4]*qy+previous[5]*qz+previous[6]*qw<0)for(int i=3;i<7;i++)target[i]=-target[i];
                    bridge.models.pose(entity,material,++revision,first?target:previous,target,first?0:ticks/20f);
                    previous=target;
                    if(first)bridge.spawn.invoke(entity);else bridge.metadata.invoke(entity);
                }catch(ReflectiveOperationException|RuntimeException failure){fail(failure);}
            });
        }
        void close(){
            closed=true;enqueue(()->{if(entity!=null)try{bridge.despawn.invoke(entity);entity=null;}catch(ReflectiveOperationException|RuntimeException failure){fail(failure);}});
        }
    }
    /** Optional reflection boundary: Java-only servers never load Geyser classes. */
    private static final class BlockBridge {
        final BedrockBlockModels models;
        final Constructor<?> context,entity;final Object definition,bedrockDefinition,gravity,collision,noAi,silent;
        final Method contextPosition,contextBedrock,vector,execute,width,height,yaw,headYaw,flag,spawn,metadata,despawn;
        BlockBridge(ClassLoader loader,BedrockBlockModels models)throws ReflectiveOperationException {
            this.models=models;bedrockDefinition=models.definition(loader);
            Class<?> session=loader.loadClass("org.geysermc.geyser.session.GeyserSession");
            Class<?> definitionType=loader.loadClass("org.geysermc.geyser.entity.EntityTypeDefinition");
            Class<?> contextType=loader.loadClass("org.geysermc.geyser.entity.spawn.EntitySpawnContext");
            Class<?> entityType=loader.loadClass("org.geysermc.geyser.entity.type.Entity");
            Class<?> flagType=loader.loadClass("org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag");
            Class<?> vec=loader.loadClass("org.cloudburstmc.math.vector.Vector3f");
            definition=loader.loadClass("org.geysermc.geyser.entity.VanillaEntities").getField("FALLING_BLOCK").get(null);
            context=contextType.getConstructor(session,definitionType,int.class,UUID.class);entity=entityType.getConstructor(contextType);
            contextPosition=contextType.getMethod("position",vec);contextBedrock=contextType.getMethod("bedrockEntityDefinition",loader.loadClass("org.geysermc.geyser.entity.BedrockEntityDefinition"));
            vector=vec.getMethod("from",double.class,double.class,double.class);execute=session.getMethod("ensureInEventLoop",Runnable.class);
            width=entityType.getMethod("setBoundingBoxWidth",float.class);height=entityType.getMethod("setBoundingBoxHeight",float.class);flag=entityType.getMethod("setFlag",flagType,boolean.class);
            yaw=entityType.getMethod("setYaw",float.class);headYaw=entityType.getMethod("setHeadYaw",float.class);
            spawn=entityType.getMethod("spawnEntity");
            metadata=entityType.getMethod("updateBedrockMetadata");despawn=entityType.getMethod("despawnEntity");
            gravity=flagType.getField("HAS_GRAVITY").get(null);collision=flagType.getField("HAS_COLLISION").get(null);
            noAi=flagType.getField("NO_AI").get(null);silent=flagType.getField("SILENT").get(null);
        }
    }
    /** The same authored sigil stays horizontal on Bedrock's emitter XZ plane. */
    void judgmentSeal(Location at,double diameter,double rotation,int variant) {
        if(bridge==null||disabled)return;
        for(var viewer:viewers)if(viewer.session!=null&&viewer.player.isOnline())try {
            Object packet=bridge.packet.newInstance();
            bridge.identifier.invoke(packet,"advance_magic:judgment_seal_"+variant);
            bridge.position.invoke(packet,bridge.vector.invoke(null,at.getX(),at.getY(),at.getZ()));
            bridge.setDimension.invoke(packet,viewer.dimension);
            bridge.variables.invoke(packet,Optional.of("[{\"name\":\"variable.seal_diameter\",\"value\":{\"type\":\"float\",\"value\":"+diameter+"}},{\"name\":\"variable.seal_rotation\",\"value\":{\"type\":\"float\",\"value\":"+rotation+"}}]"));
            bridge.send.invoke(viewer.session,packet);
        }catch(ReflectiveOperationException|LinkageError e){disable(e);return;}
    }
    /** Bedrock uses one tall beam billboard rather than translating a stretched item display. */
    void judgmentBeam(Location base,double height,double width) {
        if(bridge==null||disabled)return;
        Location at=base.clone().add(0,height/2,0);
        for(var viewer:viewers)if(viewer.session!=null&&viewer.player.isOnline())try {
            Object packet=bridge.packet.newInstance();
            bridge.identifier.invoke(packet,"advance_magic:judgment_beam");
            bridge.position.invoke(packet,bridge.vector.invoke(null,at.getX(),at.getY(),at.getZ()));
            bridge.setDimension.invoke(packet,viewer.dimension);
            bridge.variables.invoke(packet,Optional.of("[{\"name\":\"variable.beam_height\",\"value\":{\"type\":\"float\",\"value\":"+height+"}},{\"name\":\"variable.beam_width\",\"value\":{\"type\":\"float\",\"value\":"+width+"}}]"));
            bridge.send.invoke(viewer.session,packet);
        }catch(ReflectiveOperationException|LinkageError e){disable(e);return;}
    }
    void linePlane(String model,Location at,double width,double height,Vector right,Vector up) {
        if(bridge==null||disabled)return;
        for(var viewer:viewers)if(viewer.session!=null&&viewer.player.isOnline())
            linePacket(viewer,model,at,width,height,right,up,null);
    }
    /** Spin is radians per server tick; Bedrock's native spin takes degrees per second. */
    void linePlaneSpin(String model,Location at,double width,double height,Vector right,Vector up,double radiansPerTick) {
        if(bridge==null||disabled)return;
        for(var viewer:viewers)if(viewer.session!=null&&viewer.player.isOnline())
            linePacket(viewer,model,at,width,height,right,up,radiansPerTick);
    }
    void lineRay(String model,Location at,double width,double height,Vector direction) {
        if(bridge==null||disabled)return;
        for(var viewer:viewers)if(viewer.session!=null&&viewer.player.isOnline()) {
            // Bedrock's direction_y aligns the LONG axis directly with the ray.
            // A viewer-dependent normal plus billboard spin can rotate that axis
            // away from the endpoint as the camera moves. Never spin attack rays.
            boolean legendary = model.startsWith("verdant_") || model.startsWith("lunar_") || model.startsWith("crimson_") || model.startsWith("elemental_");
            sendLinePacket(viewer,model,at,width,height,0,direction,legendary,null);
        }
    }
    private void linePacket(Viewer viewer,String model,Location at,double width,double height,Vector right,Vector up,Double radiansPerTick) {
        Vector normal=right.clone().crossProduct(up).normalize();
        boolean flat=Math.abs(normal.getY())>.9999;
        double rotation;
        if(flat)rotation=Math.toDegrees(Math.atan2(right.getZ(),right.getX()));
        else {
            Vector baseUp=new Vector(0,1,0).subtract(normal.clone().multiply(normal.getY()));
            if(baseUp.lengthSquared()<1e-8)baseUp=new Vector(0,0,1);
            baseUp.normalize();Vector baseRight=baseUp.clone().crossProduct(normal).normalize();
            rotation=Math.toDegrees(Math.atan2(right.dot(baseUp),right.dot(baseRight)));
        }
        // The native XZ billboard uses X->Z headings. Our XY basis spin has the
        // opposite handedness when its normal points up, so preserve that sign.
        Double rotationRate=radiansPerTick==null?null:Math.toDegrees(radiansPerTick)*20*(flat?-Math.signum(normal.getY()):1);
        sendLinePacket(viewer,model+(flat?"_flat":""),at,width,height,rotation,normal,true,rotationRate);
    }
    private void sendLinePacket(Viewer viewer,String model,Location at,double width,double height,double rotation,Vector normal,boolean nearbyEmitter) {
        sendLinePacket(viewer,model,at,width,height,rotation,normal,nearbyEmitter,null);
    }
    private void sendLinePacket(Viewer viewer,String model,Location at,double width,double height,double rotation,Vector normal,boolean nearbyEmitter,Double rotationRate) {
        if(!packetPermit.test(viewer.player))return;
        // Keep a world plane's activation point near its viewer, including when
        // the dial's center is high above/outside the view. The shape offset
        // still places the particle at the unchanged world center and scale.
        Location emitter=nearbyEmitter?nearbyEmitter(viewer.player,at):at.clone();
        Vector offset=at.toVector().subtract(emitter.toVector());
        String[] names={"line_width","line_height","line_rotation","line_normal_x","line_normal_y","line_normal_z",
            "line_offset_x","line_offset_y","line_offset_z"};
        double[] values={width,height,rotation,normal.getX(),normal.getY(),normal.getZ(),offset.getX(),offset.getY(),offset.getZ()};
        StringJoiner json=new StringJoiner(",","[","]");
        for(int i=0;i<names.length;i++)json.add("{\"name\":\"variable."+names[i]+"\",\"value\":{\"type\":\"float\",\"value\":"+values[i]+"}}");
        if(rotationRate!=null)json.add("{\"name\":\"variable.line_rotation_rate\",\"value\":{\"type\":\"float\",\"value\":"+rotationRate+"}}");
        try {
            Object packet=bridge.packet.newInstance();
            bridge.identifier.invoke(packet,"advance_magic:"+model);
            bridge.position.invoke(packet,bridge.vector.invoke(null,emitter.getX(),emitter.getY(),emitter.getZ()));
            bridge.setDimension.invoke(packet,viewer.dimension);bridge.variables.invoke(packet,Optional.of(json.toString()));
            bridge.send.invoke(viewer.session,packet);
        }catch(ReflectiveOperationException|LinkageError e){disable(e);}
    }
    private static Location nearbyEmitter(Player viewer,Location at){
        Location eye=viewer.getEyeLocation();
        Vector toward=at.toVector().subtract(eye.toVector());
        double distance=toward.length();
        return distance>LINE_EMITTER_DISTANCE?eye.add(toward.multiply(LINE_EMITTER_DISTANCE/distance)):at.clone();
    }
    private String tint(Color color) {
        return switch(color.asRGB()) {
            case 0xFFD34D, 0xFFE578 -> "gold";
            case 0xFF732D, 0xFFA500, 0xFF8546 -> "orange";
            case 0x72EDFF, 0x8DEAFF, 0x0BE8DA, 0x56E0B5 -> "cyan";
            case 0xA36BFF, 0x8E69D4, 0x6658C9, 0xE07FFF, 0x110822 -> "violet";
            case 0x58FFB1, 0x8DEF81 -> "emerald";
            case 0x12B76A -> "jade";
            case 0xD8F3FF, 0xCCD8E0, 0xB4D8E7 -> "silver";
            case 0xFF315B -> "crimson";
            case 0xA41B52 -> "ruby";
            default -> "white";
        };
    }
    private void disable(Throwable error) {
        if(!disabled)c.plugin.getLogger().warning("Mythic Bedrock colour adapter unavailable; using vanilla particles: "+error.getClass().getSimpleName());
        disabled=true;
    }
    private static final class Bridge {
        final Object instance;
        final Constructor<?> packet;
        final Method connection,dimension,identifier,position,setDimension,variables,vector,send;
        Bridge(ClassLoader loader)throws ReflectiveOperationException {
            Class<?> geyser=loader.loadClass("org.geysermc.geyser.GeyserImpl");
            Class<?> session=loader.loadClass("org.geysermc.geyser.session.GeyserSession");
            Class<?> particles=loader.loadClass("org.cloudburstmc.protocol.bedrock.packet.SpawnParticleEffectPacket");
            Class<?> vec=loader.loadClass("org.cloudburstmc.math.vector.Vector3f");
            Class<?> dimensionUtils=loader.loadClass("org.geysermc.geyser.util.DimensionUtils");
            instance=geyser.getMethod("getInstance").invoke(null);
            packet=particles.getConstructor();
            connection=geyser.getMethod("connectionByUuid",UUID.class);
            dimension=dimensionUtils.getMethod("javaToBedrock",session);
            identifier=particles.getMethod("setIdentifier",String.class);
            position=particles.getMethod("setPosition",vec);
            setDimension=particles.getMethod("setDimensionId",int.class);
            variables=particles.getMethod("setMolangVariablesJson",Optional.class);
            vector=vec.getMethod("from",double.class,double.class,double.class);
            send=session.getMethod("sendUpstreamPacket",loader.loadClass("org.cloudburstmc.protocol.bedrock.packet.BedrockPacket"));
        }
    }
}
