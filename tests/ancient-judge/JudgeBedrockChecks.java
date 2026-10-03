import org.bukkit.Material;
import org.bukkit.plugin.java.JavaPlugin;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.data.entity.*;
import org.geysermc.geyser.registry.*;
import org.geysermc.geyser.registry.type.BlockMappings;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.entity.spawn.EntitySpawnContext;
import org.geysermc.geyser.entity.type.FallingBlockEntity;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.type.BooleanEntityMetadata;
import java.util.UUID;
import java.util.*;
import org.bukkit.*;
import org.cloudburstmc.protocol.bedrock.packet.*;
import com.example.voidscape.VoidscapePlugin;

/** Real installed Geyser mapping + no-gravity translation, with no client connection. */
public final class JudgeBedrockChecks {
    public static class Context extends GeyserSession {
        BlockMappings mappings;
        List<SpawnParticleEffectPacket> packets;
        public Context(){super(null,null,null);}
        @Override public BlockMappings getBlockMappings(){return mappings;}
        @Override public void sendUpstreamPacket(BedrockPacket packet){packets.add((SpawnParticleEffectPacket)packet);}
    }
    public static int run(JavaPlugin host)throws Exception{
        var unsafeType=Class.forName("sun.misc.Unsafe");var field=unsafeType.getDeclaredField("theUnsafe");field.setAccessible(true);
        Context session=(Context)unsafeType.getMethod("allocateInstance",Class.class).invoke(field.get(null),Context.class);
        var type=Registries.JAVA_ENTITY_IDENTIFIERS.get().get("minecraft:falling_block");
        if(type==null)throw new AssertionError("Geyser falling-block entity definition missing");
        int checks=0;
        for(var mappings:BlockRegistries.BLOCKS.get().values()){
            session.mappings=mappings;
            for(Material m:new Material[]{Material.CHISELED_STONE_BRICKS,Material.WAXED_OXIDIZED_COPPER,Material.SEA_LANTERN,
                Material.CRYING_OBSIDIAN,Material.POLISHED_TUFF,Material.AMETHYST_BLOCK,Material.EMERALD_BLOCK}){
                String state=m.createBlockData().getAsString();
                if(!BlockRegistries.JAVA_BLOCK_STATE_IDENTIFIER_TO_ID.get().containsKey(state))throw new AssertionError("Geyser does not recognize "+state);
                int id=BlockRegistries.JAVA_BLOCK_STATE_IDENTIFIER_TO_ID.get().getInt(state);
                var spawn=new EntitySpawnContext(session,type,55,UUID.randomUUID(),type.defaultBedrockDefinition(),
                    Vector3f.from(20,120,10),Vector3f.ZERO,0,0,0,123L);
                var entity=new FallingBlockEntity(spawn,id);
                entity.setGravity(new BooleanEntityMetadata(5,org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.MetadataTypes.BOOLEAN,true));
                if(!entity.getFlag(EntityFlag.NO_AI)||entity.getFlag(EntityFlag.HAS_GRAVITY))throw new AssertionError("Bedrock boss block would fall: "+state);
                var block=entity.getMetadata().get(EntityDataTypes.BLOCK);
                if(block==null||block.getRuntimeId()!=mappings.getBedrockBlock(id).getRuntimeId()
                    ||mappings.getBedrockBlock(id).getState().getString("name").equals("minecraft:air"))throw new AssertionError("Invalid Bedrock boss block mapping "+state);
                checks+=2;
            }
        }
        var garden=(VoidscapePlugin)Bukkit.getPluginManager().getPlugin("Evergarden");
        var loader=garden.getClass().getClassLoader();var typeAether=loader.loadClass("com.example.voidscape.boss.JudgeAether");
        var constructor=typeAether.getDeclaredConstructors()[0];constructor.setAccessible(true);Object aether=constructor.newInstance(garden);
        var bridge=typeAether.getDeclaredField("bridge");bridge.setAccessible(true);
        if(bridge.get(aether)==null)throw new AssertionError("Judge optional bridge did not resolve installed Geyser");checks++;
        session.packets=new ArrayList<>();
        var viewer=loader.loadClass("com.example.voidscape.boss.JudgeAether$Viewer").getDeclaredConstructors()[0];viewer.setAccessible(true);
        var viewers=typeAether.getDeclaredField("viewers");viewers.setAccessible(true);viewers.set(aether,List.of(viewer.newInstance(null,session,0,true,false)));
        var dust=typeAether.getDeclaredMethod("dust",Location.class,Color.class,float.class);dust.setAccessible(true);
        try(var pack=new java.util.zip.ZipFile(garden.getDataFolder().toPath().resolve("resource-packs/evergarden-bedrock.mcpack").toFile())){
            for(var tint:Map.of("cyan",0x75DFF2,"violet",0xC08FFF,"amber",0xFFAE5A,"red",0xFF243E).entrySet()){
                dust.invoke(aether,new Location(garden.world(),2,125,4),Color.fromRGB(tint.getValue()),1.5f);
                var packet=session.packets.getLast();
                if(!packet.getIdentifier().equals("voidscape:judge_"+tint.getKey())||packet.getPosition().getY()!=125||!packet.getMolangVariablesJson().orElseThrow().contains("1.5"))throw new AssertionError("Judge colored particle packet mismatch: "+packet);checks++;
                if(pack.getEntry("particles/judge_"+tint.getKey()+".particle.json")==null||pack.getEntry("textures/particle/judge_glint.png")==null)throw new AssertionError("Judge served particle assets missing");checks++;
            }
            org.bukkit.entity.Player player=(org.bukkit.entity.Player)java.lang.reflect.Proxy.newProxyInstance(
                org.bukkit.entity.Player.class.getClassLoader(),new Class<?>[]{org.bukkit.entity.Player.class},
                (proxy,method,args)->{if(method.getName().equals("getEyeLocation"))return new Location(garden.world(),2,102.6,4);throw new UnsupportedOperationException(method.getName());});
            viewers.set(aether,List.of(viewer.newInstance(player,session,0,true,false)));
            var plane=typeAether.getDeclaredMethod("plane",String.class,Location.class,double.class,double.class,org.bukkit.util.Vector.class,org.bukkit.util.Vector.class);plane.setAccessible(true);
            for(boolean flat:new boolean[]{false,true}){
                plane.invoke(aether,"judge_seal",new Location(garden.world(),2,125,4),32.0,32.0,
                    new org.bukkit.util.Vector(1,0,0),flat?new org.bukkit.util.Vector(0,0,1):new org.bukkit.util.Vector(0,1,0));
                var packet=session.packets.getLast();String id="voidscape:judge_seal"+(flat?"_flat":"");
                if(!packet.getIdentifier().equals(id)||pack.getEntry("particles/"+id.substring(10)+".particle.json")==null)throw new AssertionError("Boss seal packet/asset route mismatch");checks++;
                Map<String,Double> values=new HashMap<>();
                for(var value:com.google.gson.JsonParser.parseString(packet.getMolangVariablesJson().orElseThrow()).getAsJsonArray()){
                    var entry=value.getAsJsonObject();values.put(entry.get("name").getAsString(),entry.getAsJsonObject("value").get("value").getAsDouble());}
                if(Math.abs(packet.getPosition().getY()+values.get("variable.line_offset_y")-125)>.001
                    ||Math.abs(packet.getPosition().getY()-114.6)>.001||values.get("variable.line_width")!=32)throw new AssertionError("Boss seal world center/diameter differs from Java");checks++;
                if(flat&&values.get("variable.line_normal_y")!=-1||!flat&&values.get("variable.line_normal_z")!=1)throw new AssertionError("Boss seal basis does not stay fixed in world space");checks++;
            }
        }
        host.getLogger().info("PASS Judge Bedrock: "+checks+" sanctuary metadata and original aether packet checks");
        java.nio.file.Files.writeString(java.nio.file.Path.of("judge-bedrock-result.txt"),"PASS "+checks+" installed Geyser metadata/particle translations; client visual inspection remains manual");
        return checks;
    }
}
