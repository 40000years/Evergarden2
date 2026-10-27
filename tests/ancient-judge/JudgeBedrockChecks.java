import org.bukkit.Material;
import org.bukkit.plugin.java.JavaPlugin;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.data.entity.*;
import org.geysermc.geyser.registry.*;
import org.geysermc.geyser.registry.type.BlockMappings;
import org.geysermc.geyser.registry.type.ItemMappings;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.session.cache.ComponentCache;
import org.geysermc.geyser.session.cache.RegistryCache;
import org.geysermc.geyser.session.cache.TagCache;
import org.geysermc.geyser.entity.spawn.EntitySpawnContext;
import org.geysermc.geyser.entity.type.FallingBlockEntity;
import org.geysermc.geyser.entity.type.living.ArmorStandEntity;
import org.geysermc.geyser.inventory.GeyserItemStack;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.MetadataTypes;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.type.BooleanEntityMetadata;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.type.ByteEntityMetadata;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.*;
import org.geysermc.geyser.platform.spigot.shaded.net.kyori.adventure.key.Key;
import java.util.UUID;
import java.util.*;
import org.bukkit.*;
import org.cloudburstmc.protocol.bedrock.packet.*;
import com.example.voidscape.VoidscapePlugin;

/** Real installed Geyser equipment, metadata and particle translation, without a client connection. */
public final class JudgeBedrockChecks {
    public static class Context extends GeyserSession {
        BlockMappings mappings;
        ItemMappings itemMappings;
        ComponentCache components;
        RegistryCache registries;
        TagCache tags;
        List<SpawnParticleEffectPacket> packets;
        List<BedrockPacket> translated;
        public Context(){super(null,null,null);}
        @Override public BlockMappings getBlockMappings(){return mappings;}
        @Override public ItemMappings getItemMappings(){return itemMappings;}
        @Override public ComponentCache getComponentCache(){return components;}
        @Override public RegistryCache getRegistryCache(){return registries;}
        @Override public TagCache getTagCache(){return tags;}
        @Override public org.geysermc.geyser.GeyserImpl getGeyser(){return org.geysermc.geyser.GeyserImpl.getInstance();}
        @Override public String locale(){return "en_us";}
        @Override public void sendUpstreamPacket(BedrockPacket packet){
            translated.add(packet);
            if(packet instanceof SpawnParticleEffectPacket particle)packets.add(particle);
        }
    }
    public static int run(JavaPlugin host)throws Exception{
        var unsafeType=Class.forName("sun.misc.Unsafe");var field=unsafeType.getDeclaredField("theUnsafe");field.setAccessible(true);
        Context session=(Context)unsafeType.getMethod("allocateInstance",Class.class).invoke(field.get(null),Context.class);
        session.packets=new ArrayList<>();session.translated=new ArrayList<>();
        session.components=new ComponentCache(session);session.registries=new RegistryCache(session);session.tags=new TagCache(session);
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
        var loader=garden.getClass().getClassLoader();
        checks+=equipment(session,loader,host);
        var typeAether=loader.loadClass("com.example.voidscape.boss.JudgeAether");
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
        host.getLogger().info("PASS Judge Bedrock: "+checks+" helmet equipment, sanctuary metadata and original aether packet checks");
        java.nio.file.Files.writeString(java.nio.file.Path.of("judge-bedrock-result.txt"),"PASS "+checks+" installed Geyser equipment/metadata/particle translations; client visual inspection remains manual");
        return checks;
    }

    private static int equipment(Context session,ClassLoader loader,JavaPlugin host)throws Exception{
        var factory=loader.loadClass("com.example.voidscape.boss.JudgmentBody").getDeclaredMethod("model",String.class);
        factory.setAccessible(true);
        var type=Registries.JAVA_ENTITY_IDENTIFIERS.get().get("minecraft:armor_stand");
        if(type==null)throw new AssertionError("Geyser armor-stand definition missing");
        int checks=0;
        Set<String> missingWearables=new TreeSet<>();
        for(var table:Registries.ITEMS.get().entrySet()){
            session.itemMappings=table.getValue();session.mappings=BlockRegistries.BLOCKS.get().get(table.getKey());
            for(String name:List.of("mask","mask_light","left","left_light","right","right_light","heart","shard",
                "wing_left","wing_left_light","wing_right","wing_right_light","mantle","mantle_light")){
                String expected="voidscape:judge_"+name;
                var item=(org.bukkit.inventory.ItemStack)factory.invoke(null,"judge_"+name);
                var meta=item.getItemMeta();
                if(item.getType()!=Material.IRON_HELMET||meta.getItemModel()==null||!meta.getItemModel().toString().equals(expected))
                    throw new AssertionError("Judge model factory has wrong carrier/model: "+name);checks++;
                var patch=new DataComponents(new HashMap<>());
                patch.put(DataComponentTypes.ITEM_MODEL,Key.key(meta.getItemModel().toString()));
                var custom=meta.getCustomModelDataComponent();
                patch.put(DataComponentTypes.CUSTOM_MODEL_DATA,new CustomModelData(custom.getFloats(),custom.getFlags(),custom.getStrings(),List.of()));
                var mapping=session.itemMappings.getMapping(item.getType().getKey().toString());
                var helmet=GeyserItemStack.of(session,mapping.getJavaItem().javaId(),item.getAmount(),patch);
                var equippable=helmet.getComponent(DataComponentTypes.EQUIPPABLE);
                if(equippable==null||equippable.slot()!=org.geysermc.mcprotocollib.protocol.data.game.entity.EquipmentSlot.HELMET)
                    throw new AssertionError("Java Judge helmet lost inherited head equipment component: "+expected);checks++;
                // Paper sends equipment after entity spawn; also cover equipment staged before spawn.
                for(boolean equipBeforeSpawn:new boolean[]{false,true}){
                    session.translated.clear();
                    long runtimeId=1234;
                    var spawn=new EntitySpawnContext(session,type,55,UUID.randomUUID(),type.defaultBedrockDefinition(),
                        Vector3f.from(20,120,10),Vector3f.ZERO,0,0,0,runtimeId);
                    var entity=new ArmorStandEntity(spawn);
                    entity.setFlags(new ByteEntityMetadata(0,MetadataTypes.BYTE,(byte)0x20));
                    entity.setArmorStandFlags(new ByteEntityMetadata(15,MetadataTypes.BYTE,(byte)0x08));
                    entity.setGravity(new BooleanEntityMetadata(5,MetadataTypes.BOOLEAN,true));
                    if(equipBeforeSpawn)entity.setHelmet(helmet);
                    entity.updateArmor();
                    if(session.translated.stream().anyMatch(packet->packet instanceof MobArmorEquipmentPacket))
                        throw new AssertionError("Judge armor equipment sent before entity spawn: "+expected);checks++;
                    entity.spawnEntity();
                    if(!equipBeforeSpawn)entity.setHelmet(helmet);
                    entity.updateArmor();entity.updateBedrockMetadata();
                    AddEntityPacket added=null;MobArmorEquipmentPacket armor=null;int addedIndex=-1,armorIndex=-1;
                    for(int i=0;i<session.translated.size();i++){
                        var packet=session.translated.get(i);
                        if(packet instanceof AddEntityPacket value&&value.getRuntimeEntityId()==runtimeId){added=value;addedIndex=i;}
                        if(packet instanceof MobArmorEquipmentPacket value&&value.getRuntimeEntityId()==runtimeId){armor=value;armorIndex=i;}
                        if(packet instanceof SetEntityDataPacket value&&value.getRuntimeEntityId()==runtimeId){
                            Float scale=value.getMetadata().get(EntityDataTypes.SCALE);
                            if(scale!=null&&scale<=0)throw new AssertionError("Invisible Judge helmet carrier has zero scale: "+expected);
                        }
                    }
                    if(added==null||armor==null||addedIndex>=armorIndex||!"minecraft:armor_stand".equals(added.getIdentifier()))
                        throw new AssertionError("Judge spawn/equipment packet ordering failed: "+expected);checks++;
                    if(!added.getMetadata().getFlag(EntityFlag.INVISIBLE)||added.getMetadata().getFlag(EntityFlag.HAS_GRAVITY)
                        ||added.getMetadata().get(EntityDataTypes.SCALE)==null||added.getMetadata().get(EntityDataTypes.SCALE)<=0
                        ||entity.isMarker()||entity.getMetadata().value(EntityDataTypes.SCALE)==null||entity.getMetadata().value(EntityDataTypes.SCALE)<=0)
                        throw new AssertionError("Judge carrier metadata hides equipment or enables gravity: "+expected);checks++;
                    var translated=armor.getHelmet();
                    if(translated==null||translated.getCount()!=1||!expected.equals(translated.getDefinition().getIdentifier())
                        ||!session.itemMappings.isRegistered(translated.getDefinition()))
                        throw new AssertionError("Judge head equipment did not retain custom item: "+expected+" actual="+translated);checks++;
                    if(!armor.getChestplate().isNull()||!armor.getLeggings().isNull()||!armor.getBoots().isNull()||!armor.getBody().isNull())
                        throw new AssertionError("Judge custom helmet moved to a different equipment slot: "+expected);checks++;
                    // Matching an item_model is insufficient: Bedrock also needs the
                    // registered item's head wearable component to render the attachable.
                    var componentData=translated.getDefinition().getComponentData();
                    String wearable=componentData==null?"":componentData.getCompound("components").getCompound("minecraft:wearable").getString("slot");
                    if(!translated.getDefinition().isComponentBased()||!"slot.armor.head".equals(wearable))
                        missingWearables.add(expected+" protocol="+table.getKey()+" wearable="+(wearable.isEmpty()?"missing":wearable));
                    else checks++;
                }
            }
        }
        if(!missingWearables.isEmpty()){
            host.getLogger().warning("Judge equipment packets passed, but registered head wearables are absent: "+missingWearables);
            throw new AssertionError("Judge custom helmet definitions lack minecraft:wearable slot.armor.head: "+missingWearables);
        }
        return checks;
    }
}
