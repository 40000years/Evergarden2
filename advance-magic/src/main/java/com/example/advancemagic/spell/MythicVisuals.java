package com.example.advancemagic.spell;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.entity.Entity;
import org.bukkit.util.Vector;
import java.lang.reflect.*;
import java.util.*;

/** Optional Geyser adapter for coloured dust and authored world-space line art. */
final class MythicVisuals {
    private record Viewer(Player player,Object session,int dimension) {}
    private final MagicContext c;
    private List<Viewer> viewers=List.of();
    private Bridge bridge;
    private boolean disabled;
    MythicVisuals(MagicContext c){this.c=c;}
    void clear(){viewers=List.of();}

    void frame(Location center) {
        if(bridge==null&&!disabled) {
            var geyser=Bukkit.getPluginManager().getPlugin("Geyser-Spigot");
            if(geyser!=null&&geyser.isEnabled())try{bridge=new Bridge(geyser.getClass().getClassLoader());}
            catch(ReflectiveOperationException|LinkageError e){disable(e);}
        }
        var list=new ArrayList<Viewer>();
        for(Player p:center.getWorld().getPlayers())if(p.isOnline()&&p.getLocation().distanceSquared(center)<=64*64) {
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
            linePacket(viewer,model,at,width,height,right,up);
    }
    void lineRay(String model,Location at,double width,double height,Vector direction) {
        if(bridge==null||disabled)return;
        for(var viewer:viewers)if(viewer.session!=null&&viewer.player.isOnline()) {
            // Rotate only around the ray's axis so the ribbon faces this viewer.
            Vector normal=viewer.player.getEyeLocation().toVector().subtract(at.toVector());
            normal.subtract(direction.clone().multiply(normal.dot(direction)));
            if(normal.lengthSquared()<1e-6)normal=direction.clone().crossProduct(new Vector(1,0,0));
            if(normal.lengthSquared()<1e-6)normal=direction.clone().crossProduct(new Vector(0,0,1));
            normal.normalize();
            Vector right=direction.clone().crossProduct(normal).normalize();
            linePacket(viewer,model,at,width,height,right,direction);
        }
    }
    private void linePacket(Viewer viewer,String model,Location at,double width,double height,Vector right,Vector up) {
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
        String[] names={"line_width","line_height","line_rotation","line_normal_x","line_normal_y","line_normal_z"};
        double[] values={width,height,rotation,normal.getX(),normal.getY(),normal.getZ()};
        StringJoiner json=new StringJoiner(",","[","]");
        for(int i=0;i<names.length;i++)json.add("{\"name\":\"variable."+names[i]+"\",\"value\":{\"type\":\"float\",\"value\":"+values[i]+"}}");
        try {
            Object packet=bridge.packet.newInstance();
            bridge.identifier.invoke(packet,"advance_magic:"+model+(flat?"_flat":""));
            bridge.position.invoke(packet,bridge.vector.invoke(null,at.getX(),at.getY(),at.getZ()));
            bridge.setDimension.invoke(packet,viewer.dimension);bridge.variables.invoke(packet,Optional.of(json.toString()));
            bridge.send.invoke(viewer.session,packet);
        }catch(ReflectiveOperationException|LinkageError e){disable(e);}
    }
    private String tint(Color color) {
        return switch(color.asRGB()) {
            case 0xFFD34D -> "gold";
            case 0xFF732D -> "orange";
            case 0x72EDFF -> "cyan";
            case 0xA36BFF -> "violet";
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
