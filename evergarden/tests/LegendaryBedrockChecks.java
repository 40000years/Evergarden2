import com.example.advancemagic.AdvanceMagicPlugin;
import com.example.voidscape.VoidscapePlugin;
import com.google.gson.JsonParser;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;
import org.cloudburstmc.protocol.bedrock.packet.SpawnParticleEffectPacket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.ZipFile;

/** Installed Geyser packets for every legendary texture and dust tint; no client required. */
public final class LegendaryBedrockChecks {
    private final JavaPlugin host;
    private final Player player;
    private int checks;
    private static final List<String> PLANES=List.of("verdant_seal","verdant_dragon","verdant_beam_core","verdant_halo",
        "lunar_seal","lunar_orbit","lunar_constellation","crimson_seal","crimson_heart","crimson_shard");
    private static final List<String> RAYS=List.of("verdant_ray","lunar_ray","crimson_ray");
    private LegendaryBedrockChecks(JavaPlugin host,Player player){this.host=host;this.player=player;}
    private void check(boolean value,String label){
        if(!value)throw new AssertionError(label);
        checks++;host.getLogger().info("PASS Legendary Bedrock: "+label);
    }
    public static void run(JavaPlugin host,AdvanceMagicPlugin magic,VoidscapePlugin garden,Player player)throws Exception {
        var test=new LegendaryBedrockChecks(host,player);
        test.verify(magic,garden);
        Files.writeString(Path.of("legendary-bedrock-result.txt"),"PASS "+test.checks
            +" checks; 10 live planes in two orientations, 3 rays, 5 dust tints, native Geyser packets and the owning Advance Magic pack; no real client connection");
    }
    private Map<String,Double> variables(SpawnParticleEffectPacket packet){
        var result=new HashMap<String,Double>();
        for(var row:JsonParser.parseString(packet.getMolangVariablesJson().orElseThrow()).getAsJsonArray()){
            var entry=row.getAsJsonObject();
            result.put(entry.get("name").getAsString(),entry.getAsJsonObject("value").get("value").getAsDouble());
        }
        return result;
    }
    private void linePacket(SpawnParticleEffectPacket packet,String id,Location center,double width,double height,Vector normal){
        check(packet.getIdentifier().equals("advance_magic:"+id),"native packet identifier "+id);
        var values=variables(packet);
        check(values.size()==9&&values.values().stream().allMatch(Double::isFinite),"finite scale/rotation/offset variables "+id);
        check(values.get("variable.line_width")==width&&values.get("variable.line_height")==height,"full authored dimensions "+id);
        check(Math.abs(values.get("variable.line_normal_x")-normal.getX())<1e-9
            &&Math.abs(values.get("variable.line_normal_y")-normal.getY())<1e-9
            &&Math.abs(values.get("variable.line_normal_z")-normal.getZ())<1e-9,"world-space plane or ray direction "+id);
        var position=packet.getPosition();
        var reconstructed=new Location(player.getWorld(),position.getX()+values.get("variable.line_offset_x"),
            position.getY()+values.get("variable.line_offset_y"),position.getZ()+values.get("variable.line_offset_z"));
        check(reconstructed.distanceSquared(center)<1e-8,"world center preserved by nearby emitter "+id);
        if(RAYS.contains(id)) {
            var emitter=new Location(player.getWorld(),position.getX(),position.getY(),position.getZ());
            check(emitter.distance(player.getEyeLocation())<=12.0001,"distant legendary attack uses a nearby activation point "+id);
            check(Math.abs(values.get("variable.line_offset_x"))+Math.abs(values.get("variable.line_offset_y"))
                +Math.abs(values.get("variable.line_offset_z"))>1,"near emitter retains a real world-space offset "+id);
        }
    }
    private void verify(AdvanceMagicPlugin magic,VoidscapePlugin garden)throws Exception {
        var loader=magic.getClass().getClassLoader();
        var type=loader.loadClass("com.example.advancemagic.spell.MythicVisuals");
        var ctor=type.getDeclaredConstructors()[0];ctor.setAccessible(true);
        Object visuals=ctor.newInstance(magic.context());
        var frame=type.getDeclaredMethod("frame",Location.class);frame.setAccessible(true);
        frame.invoke(visuals,player.getLocation());
        var bridge=type.getDeclaredField("bridge");bridge.setAccessible(true);
        check(bridge.get(visuals)!=null,"real installed Geyser bridge resolves");
        var unsafeType=Class.forName("sun.misc.Unsafe");
        var unsafeField=unsafeType.getDeclaredField("theUnsafe");unsafeField.setAccessible(true);
        var session=(MythicSpellChecks.CaptureSession)unsafeType.getMethod("allocateInstance",Class.class)
            .invoke(unsafeField.get(null),MythicSpellChecks.CaptureSession.class);
        session.particles=new ArrayList<>();
        var viewer=loader.loadClass("com.example.advancemagic.spell.MythicVisuals$Viewer").getDeclaredConstructors()[0];viewer.setAccessible(true);
        var viewers=type.getDeclaredField("viewers");viewers.setAccessible(true);
        viewers.set(visuals,List.of(viewer.newInstance(player,session,0)));
        var plane=type.getDeclaredMethod("linePlane",String.class,Location.class,double.class,double.class,Vector.class,Vector.class);plane.setAccessible(true);
        var ray=type.getDeclaredMethod("lineRay",String.class,Location.class,double.class,double.class,Vector.class);ray.setAccessible(true);
        var dust=type.getDeclaredMethod("dust",Location.class,Color.class,float.class);dust.setAccessible(true);
        var x=new Vector(1,0,0);var y=new Vector(0,1,0);var z=new Vector(0,0,1);
        Location center=player.getLocation().add(3,32,4);
        for(String name:PLANES){
            plane.invoke(visuals,name,center,40.5,28.25,x,y);
            linePacket(session.particles.getLast(),name,center,40.5,28.25,z);
            plane.invoke(visuals,name,center,40.5,28.25,x,z);
            linePacket(session.particles.getLast(),name+"_flat",center,40.5,28.25,new Vector(0,-1,0));
        }
        var direction=new Vector(3,-24,7).normalize();
        for(String name:RAYS){
            ray.invoke(visuals,name,center,1.25,28.0,direction);
            var packet=session.particles.getLast();
            linePacket(packet,name,center,1.25,28.0,direction);
            check(variables(packet).get("variable.line_rotation")==0,"attack ray retains zero spin "+name);
        }
        var colors=Map.of("emerald",0x58FFB1,"jade",0x12B76A,"silver",0xD8F3FF,"crimson",0xFF315B,"ruby",0xA41B52);
        Location dustAt=player.getLocation().add(2,4,3);
        for(var color:colors.entrySet()){
            int before=session.particles.size();
            dust.invoke(visuals,dustAt,Color.fromRGB(color.getValue()),2.75f);
            check(session.particles.size()==before+1,"dust emits exactly one packet "+color.getKey());
            var packet=session.particles.getLast();
            check(packet.getIdentifier().equals("advance_magic:mythic_"+color.getKey()),"legendary dust tint identifier "+color.getKey());
            check(variables(packet).get("variable.magic_size")==2.75,"dust scale matches Java "+color.getKey());
        }
        byte[] firstTexture=null;
        for(var owner:List.of(magic))try(var pack=new ZipFile(owner.getDataFolder().toPath()
                .resolve("resource-packs/"+(owner==magic?"advance-magic":"evergarden")+"-bedrock.mcpack").toFile())){
            for(String name:PLANES){
                for(String suffix:List.of("","_flat")){
                    var entry=pack.getEntry("particles/"+name+suffix+".particle.json");
                    check(entry!=null,"served "+owner.getName()+" contains "+name+suffix);
                    var document=JsonParser.parseString(new String(pack.getInputStream(entry).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8))
                        .getAsJsonObject().getAsJsonObject("particle_effect");
                    check(document.getAsJsonObject("description").get("identifier").getAsString().equals("advance_magic:"+name+suffix),"authored plane definition matches native identifier "+name+suffix);
                    var billboard=document.getAsJsonObject("components").getAsJsonObject("minecraft:particle_appearance_billboard");
                    check(billboard.get("facing_camera_mode").getAsString().equals(suffix.isEmpty()?"direction_z":"emitter_transform_xz"),"authored plane uses intended world orientation "+name+suffix);
                }
                check(pack.getEntry("textures/particle/"+name+".png")!=null,"served hero/sigil texture "+name);
            }
            for(String name:RAYS){
                var entry=pack.getEntry("particles/"+name+".particle.json");
                check(entry!=null&&pack.getEntry("textures/particle/"+name+".png")!=null,"served attack ribbon "+name);
                var components=JsonParser.parseString(new String(pack.getInputStream(entry).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8))
                    .getAsJsonObject().getAsJsonObject("particle_effect").getAsJsonObject("components");
                check(components.getAsJsonObject("minecraft:particle_appearance_billboard").get("facing_camera_mode").getAsString().equals("direction_y"),"ribbon aligns long axis with endpoint direction "+name);
            }
            for(var color:colors.entrySet()){
                var entry=pack.getEntry("particles/mythic_"+color.getKey()+".particle.json");
                check(entry!=null,"served legendary tint "+color.getKey());
                var rgba=JsonParser.parseString(new String(pack.getInputStream(entry).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8))
                    .getAsJsonObject().getAsJsonObject("particle_effect").getAsJsonObject("components")
                    .getAsJsonObject("minecraft:particle_appearance_tinting").getAsJsonArray("color");
                check(Math.abs(rgba.get(0).getAsDouble()-((color.getValue()>>16)&255)/255.0)<1e-12
                    &&Math.abs(rgba.get(1).getAsDouble()-((color.getValue()>>8)&255)/255.0)<1e-12
                    &&Math.abs(rgba.get(2).getAsDouble()-(color.getValue()&255)/255.0)<1e-12,"served tint matches actual Java color "+color.getKey());
            }
            byte[] texture=pack.getInputStream(pack.getEntry("textures/particle/mythic_dot.png")).readAllBytes();
            if(firstTexture==null)firstTexture=texture;
            else check(Arrays.equals(firstTexture,texture),"pack stacking preserves the shared native dust texture");
        }
    }
}
