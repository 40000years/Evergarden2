package com.example.voidscape.boss;

import com.example.voidscape.VoidscapePlugin;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.entity.Entity;
import org.bukkit.util.Vector;
import java.lang.reflect.*;
import java.util.*;

/** Colored original fracture filaments on both clients; Geyser's vanilla dust loses its tint. */
final class JudgeAether {
    private record Viewer(Player player,Object session,int dimension,boolean bedrock,boolean custom){}
    private final VoidscapePlugin plugin;
    private final Bridge bridge;
    private List<Viewer> viewers=List.of();
    private boolean failed;
    JudgeAether(VoidscapePlugin plugin){
        this.plugin=plugin;Bridge found=null;
        var geyser=Bukkit.getPluginManager().getPlugin("Geyser-Spigot");
        if(geyser!=null)try{found=new Bridge(geyser.getClass().getClassLoader());}
        catch(ReflectiveOperationException|LinkageError ignored){}
        bridge=found;
    }
    void frame(Location center,double range){
        var list=new ArrayList<Viewer>();
        for(Player p:center.getWorld().getPlayers())if(p.getLocation().distanceSquared(center)<range*range){
            Object session=null;int dimension=0;
            if(bridge!=null&&!failed)try{
                session=bridge.connection.invoke(bridge.instance,p.getUniqueId());
                if(session!=null)dimension=(int)bridge.dimension.invoke(null,session);
            }catch(ReflectiveOperationException|LinkageError error){disable(error);}
            list.add(new Viewer(p,session,dimension,session!=null||JudgePackService.bedrock(p),plugin.judgePack().applied(p)));
        }
        viewers=list;
    }
    void dust(Location at,Color color,float size){
        for(Viewer v:viewers){
            if(v.session!=null&&!failed)try{
                String tint=color.getRed()>220&&color.getGreen()<140?"red":color.getRed()>240?"amber":color.getRed()>150&&color.getBlue()>240?"violet":"cyan";
                Object packet=bridge.packet.newInstance();bridge.identifier.invoke(packet,"voidscape:judge_"+tint);
                bridge.position.invoke(packet,bridge.vector.invoke(null,at.getX(),at.getY(),at.getZ()));
                bridge.setDimension.invoke(packet,v.dimension);
                bridge.variables.invoke(packet,Optional.of("[{\"name\":\"variable.judge_size\",\"value\":{\"type\":\"float\",\"value\":"+size+"}}]"));
                bridge.send.invoke(v.session,packet);continue;
            }catch(ReflectiveOperationException|LinkageError error){disable(error);}
            v.player.spawnParticle(Particle.DUST,at,1,0,0,0,0,new Particle.DustOptions(color,size));
        }
    }
    void showPlane(Entity entity){
        for(Viewer v:viewers){
            boolean show=!v.bedrock&&v.custom;
            if(show&&!v.player.canSee(entity))v.player.showEntity(plugin,entity);
            else if(!show&&v.player.canSee(entity))v.player.hideEntity(plugin,entity);
        }
    }
    /** Identical world-space texture and basis to Java; keep Bedrock's emitter near its viewer. */
    void plane(String model,Location at,double width,double height,Vector right,Vector up){
        if(bridge==null||failed)return;
        Vector normal=right.clone().crossProduct(up).normalize();boolean flat=Math.abs(normal.getY())>.9999;
        double angle;
        if(flat)angle=Math.toDegrees(Math.atan2(right.getZ(),right.getX()));
        else{
            Vector baseUp=new Vector(0,1,0).subtract(normal.clone().multiply(normal.getY())).normalize();
            Vector baseRight=baseUp.clone().crossProduct(normal).normalize();
            angle=Math.toDegrees(Math.atan2(right.dot(baseUp),right.dot(baseRight)));
        }
        for(Viewer v:viewers)if(v.session!=null)try{
            Location emitter=v.player.getEyeLocation();Vector toward=at.toVector().subtract(emitter.toVector());
            double distance=toward.length();emitter.add(toward.multiply(distance>12?12/distance:1));
            Vector offset=at.toVector().subtract(emitter.toVector());
            String[] names={"line_width","line_height","line_rotation","line_normal_x","line_normal_y","line_normal_z","line_offset_x","line_offset_y","line_offset_z"};
            double[] values={width,height,angle,normal.getX(),normal.getY(),normal.getZ(),offset.getX(),offset.getY(),offset.getZ()};
            StringJoiner json=new StringJoiner(",","[","]");
            for(int i=0;i<names.length;i++)json.add("{\"name\":\"variable."+names[i]+"\",\"value\":{\"type\":\"float\",\"value\":"+values[i]+"}}");
            Object packet=bridge.packet.newInstance();bridge.identifier.invoke(packet,"voidscape:"+model+(flat?"_flat":""));
            bridge.position.invoke(packet,bridge.vector.invoke(null,emitter.getX(),emitter.getY(),emitter.getZ()));
            bridge.setDimension.invoke(packet,v.dimension);bridge.variables.invoke(packet,Optional.of(json.toString()));bridge.send.invoke(v.session,packet);
        }catch(ReflectiveOperationException|LinkageError error){disable(error);}
    }
    void fallbackDust(Location at,Color color,float size){
        for(Viewer v:viewers)if((v.session==null||failed)&&(v.bedrock||!v.custom))
            v.player.spawnParticle(Particle.DUST,at,1,0,0,0,0,new Particle.DustOptions(color,size),true);
    }
    boolean needsFallback(){return viewers.stream().anyMatch(v->(v.session==null||failed)&&(v.bedrock||!v.custom));}
    private void disable(Throwable error){if(!failed)plugin.getLogger().warning("Judge Bedrock aether uses vanilla fallback: "+error.getClass().getSimpleName());failed=true;}
    private static final class Bridge {
        final Object instance;final Constructor<?> packet;
        final Method connection,dimension,identifier,position,setDimension,variables,vector,send;
        Bridge(ClassLoader loader)throws ReflectiveOperationException{
            Class<?> geyser=loader.loadClass("org.geysermc.geyser.GeyserImpl"),session=loader.loadClass("org.geysermc.geyser.session.GeyserSession");
            Class<?> particles=loader.loadClass("org.cloudburstmc.protocol.bedrock.packet.SpawnParticleEffectPacket"),vec=loader.loadClass("org.cloudburstmc.math.vector.Vector3f");
            instance=geyser.getMethod("getInstance").invoke(null);packet=particles.getConstructor();
            connection=geyser.getMethod("connectionByUuid",UUID.class);
            dimension=loader.loadClass("org.geysermc.geyser.util.DimensionUtils").getMethod("javaToBedrock",session);
            identifier=particles.getMethod("setIdentifier",String.class);position=particles.getMethod("setPosition",vec);
            setDimension=particles.getMethod("setDimensionId",int.class);variables=particles.getMethod("setMolangVariablesJson",Optional.class);
            vector=vec.getMethod("from",double.class,double.class,double.class);
            send=session.getMethod("sendUpstreamPacket",loader.loadClass("org.cloudburstmc.protocol.bedrock.packet.BedrockPacket"));
        }
    }
}
