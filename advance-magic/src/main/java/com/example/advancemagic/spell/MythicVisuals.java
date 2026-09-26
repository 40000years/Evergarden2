package com.example.advancemagic.spell;

import org.bukkit.*;
import org.bukkit.entity.Player;
import java.lang.reflect.*;
import java.util.*;

/** Optional Geyser adapter for the new spells' coloured points only.
 * Java keeps native DustOptions; Bedrock receives the same position, tint and size
 * through the five particle definitions bundled in both served resource packs.
 */
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
        if(!c.loaded(at))return;
        var data=new Particle.DustOptions(color,size);
        for(var viewer:viewers) {
            if(!viewer.player.isOnline()||viewer.player.getWorld()!=at.getWorld())continue;
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
