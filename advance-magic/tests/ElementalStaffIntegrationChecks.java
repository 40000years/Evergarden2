import com.example.advancemagic.AdvanceMagicPlugin;
import com.example.advancemagic.item.ElementalStaffService.Element;
import com.example.advancemagic.item.LegendaryWeaponService.Weapon;
import com.example.advancemagic.spell.Spell;
import com.ticxo.modelengine.api.ModelEngineAPI;
import com.ticxo.modelengine.api.animation.handler.IStateMachineHandler;
import org.bukkit.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.geysermc.geyser.registry.Registries;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.translator.item.CustomItemTranslator;
import org.geysermc.geyser.platform.spigot.shaded.net.kyori.adventure.key.Key;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.*;
import java.nio.file.*;
import java.util.*;

/** Imports all new source models into actual R4.1.1 and runs installed Geyser matching. */
public final class ElementalStaffIntegrationChecks extends JavaPlugin {
    static final class Session extends GeyserSession {
        private Session(){super(null,null,null);}
        @Override public org.geysermc.geyser.GeyserImpl getGeyser(){return org.geysermc.geyser.GeyserImpl.getInstance();}
    }
    int checks, waited;
    void check(boolean ok,String label){if(!ok)throw new AssertionError(label);checks++;getLogger().info("PASS "+label);}
    @Override public void onEnable(){Bukkit.getScheduler().runTaskLater(this,this::await,100);}
    void await(){
        try{
            if(ModelEngineAPI.getBlueprint("elemental_staff_dark")==null&&waited<600){
                waited+=20;Bukkit.getScheduler().runTaskLater(this,this::await,20);return;
            }
            run();finish(null);
        }catch(Throwable error){finish(error);}
    }
    void run() throws Exception {
        check(Bukkit.getPluginManager().isPluginEnabled("ModelEngine"),"ModelEngine enabled");
        check(Bukkit.getPluginManager().getPlugin("ModelEngine").getDescription().getVersion().contains("4.1.1"),"actual ModelEngine R4.1.1");
        check(Bukkit.getPluginManager().isPluginEnabled("Geyser-Spigot"),"actual Geyser enabled");
        var magic=(AdvanceMagicPlugin)Bukkit.getPluginManager().getPlugin("advance-magic");
        check(magic.isEnabled()&&magic.staffs()!=null,"elemental catalog loaded");
        var unsafe=Class.forName("sun.misc.Unsafe");var field=unsafe.getDeclaredField("theUnsafe");field.setAccessible(true);
        var session=(Session)unsafe.getMethod("allocateInstance",Class.class).invoke(field.get(null),Session.class);
        woodenItemMappings(magic,session);
        for(Element element:Element.values()){
            String name=element.name().toLowerCase(Locale.ROOT),id="advance_magic:staff_"+name;
            var item=magic.staffs().create(element);
            check(id.equals(item.getItemMeta().getItemModel().toString()),"item model "+name);
            var components=new DataComponents(new HashMap<>());
            components.put(DataComponentTypes.ITEM_MODEL,Key.key(id));
            for(var table:Registries.ITEMS.get().values()){
                var base=table.getMapping(item.getType().getKey().toString());
                var mapped=CustomItemTranslator.getCustomItem(session,item.getAmount(),components,base);
                check(mapped!=null&&id.equals(mapped.getIdentifier()),"Geyser protocol mapping "+id);
            }
            var blueprint=ModelEngineAPI.getBlueprint("elemental_staff_"+name);
            check(blueprint!=null,"ModelEngine blueprint "+name);
            check(blueprint.getAnimations().containsKey("idle"),"imported idle clip "+name);
            var active=ModelEngineAPI.createActiveModel("elemental_staff_"+name,null,ModelEngineAPI::createStateMachineHandler);
            var world=Bukkit.getWorlds().getFirst();
            var carrier=world.spawn(new Location(world,.5,100,.5),org.bukkit.entity.Slime.class,
                mob->{mob.setAI(false);mob.setGravity(false);mob.setSilent(true);mob.setPersistent(false);});
            try{
                var entity=ModelEngineAPI.createModeledEntity(carrier);entity.setSaved(false);entity.addModel(active,false);
                if(active.getBones().isEmpty())active.generateModel();
                check(!active.getBones().isEmpty(),"generated native model bones "+name);
                var handler=(IStateMachineHandler)active.getAnimationHandler();
                handler.playAnimation(10,"idle",0,0,1,true);
                check(handler.isPlayingAnimation(10,"idle"),"playing imported idle "+name);
            }finally{ModelEngineAPI.removeModeledEntity(carrier.getUniqueId());carrier.remove();}
        }
        for(Spell spell:Spell.values()){
            var components=new DataComponents(new HashMap<>());
            components.put(DataComponentTypes.ITEM_MODEL,Key.key("advance_magic:"+spell.id()));
            for(var table:Registries.ITEMS.get().values()){
                var base=table.getMapping("minecraft:carrot_on_a_stick");
                check(CustomItemTranslator.getCustomItem(session,1,components,base)==null,"old wand not registered "+spell.id());
            }
        }
        for(Weapon weapon:Weapon.values()){
            var item=magic.legendaryWeapons().create(weapon);
            var components=new DataComponents(new HashMap<>());
            components.put(DataComponentTypes.ITEM_MODEL,Key.key(item.getItemMeta().getItemModel().toString()));
            for(var table:Registries.ITEMS.get().values()){
                var mapped=CustomItemTranslator.getCustomItem(session,1,components,table.getMapping(item.getType().getKey().toString()));
                check(mapped!=null&&("advance_magic:legendary_"+weapon.id()).equals(mapped.getIdentifier()),"legendary weapon preserved "+weapon.id());
            }
        }
        Path installed=Path.of("plugins/Geyser-Spigot/packs/advance-magic-bedrock.mcpack");
        check(Arrays.equals(Files.readAllBytes(installed),Files.readAllBytes(Path.of("plugins/advance-magic/resource-packs/advance-magic-bedrock.mcpack"))),"Geyser installed the exact new pack");
        check(Files.exists(Path.of("plugins/ModelEngine/resource pack.zip")),"ModelEngine compiled resource pack");
        ElementalStaffAmbientChecks.run(magic,this::check);
    }
    void woodenItemMappings(AdvanceMagicPlugin magic,Session session) throws Exception {
        String id="advance_magic:staff_wood";
        check(!Registries.ITEMS.get().isEmpty(),"Geyser protocol tables loaded for wooden mapping checks");
        var item=magic.staffSpells().createWoodenStaff();
        var meta=item.getItemMeta();
        check(id.equals(meta.getItemModel().toString()),"wooden staff item model");
        check(meta.getCustomModelDataComponent().getStrings().equals(List.of(id)),"wooden staff string selector");
        check(magic.staffs().isWooden(item)&&magic.staffs().isStaff(item)&&magic.staffs().element(item)==null,"wooden staff has neutral identity");
        check(magic.staffSpells().tier(item)==com.example.advancemagic.elemental.StaffSpellService.Tier.WOODEN,"wooden staff starts with wooden tier");

        var components=new DataComponents(new HashMap<>());
        components.put(DataComponentTypes.ITEM_MODEL,Key.key(meta.getItemModel().toString()));
        components.put(DataComponentTypes.CUSTOM_MODEL_DATA,new CustomModelData(List.of(),List.of(),meta.getCustomModelDataComponent().getStrings(),List.of()));
        var modelOnly=new DataComponents(new HashMap<>());
        modelOnly.put(DataComponentTypes.ITEM_MODEL,Key.key(id));
        var unknownModel=new DataComponents(new HashMap<>());
        unknownModel.put(DataComponentTypes.ITEM_MODEL,Key.key("advance_magic:staff_unknown"));
        unknownModel.put(DataComponentTypes.CUSTOM_MODEL_DATA,new CustomModelData(List.of(),List.of(),List.of(id),List.of()));
        var stringOnly=new DataComponents(new HashMap<>());
        stringOnly.put(DataComponentTypes.CUSTOM_MODEL_DATA,new CustomModelData(List.of(),List.of(),List.of(id),List.of()));
        for(var table:Registries.ITEMS.get().values()){
            var base=table.getMapping(item.getType().getKey().toString());
            var mapped=CustomItemTranslator.getCustomItem(session,item.getAmount(),components,base);
            check(mapped!=null&&id.equals(mapped.getIdentifier()),"real Geyser translates wooden model and string selector");
            // V2 visual definitions are selected by ITEM_MODEL. Gameplay identity
            // additionally requires the matching string and private staff tags.
            var visual=CustomItemTranslator.getCustomItem(session,1,modelOnly,base);
            check(visual!=null&&id.equals(visual.getIdentifier()),"Geyser preserves explicit wooden item-model mapping");
            check(CustomItemTranslator.getCustomItem(session,1,unknownModel,base)==null,"Geyser rejects unknown model even with wooden string");
            check(CustomItemTranslator.getCustomItem(session,1,stringOnly,base)==null,"wooden string alone cannot select Geyser model");
        }

        var forged=item.clone();var forgedMeta=forged.getItemMeta();
        var selector=forgedMeta.getCustomModelDataComponent();selector.setStrings(List.of("advance_magic:staff_fire"));
        forgedMeta.setCustomModelDataComponent(selector);forged.setItemMeta(forgedMeta);
        check(!magic.staffs().isStaff(forged)&&magic.staffSpells().tier(forged)==null,"wooden gameplay identity rejects conflicting string selector");
        forged=item.clone();forgedMeta=forged.getItemMeta();forgedMeta.setItemModel(new NamespacedKey("advance_magic","staff_unknown"));forged.setItemMeta(forgedMeta);
        check(!magic.staffs().isStaff(forged),"wooden gameplay identity rejects unknown item model");
        forged=new org.bukkit.inventory.ItemStack(item.getType());forgedMeta=forged.getItemMeta();
        selector=forgedMeta.getCustomModelDataComponent();selector.setStrings(List.of(id));forgedMeta.setCustomModelDataComponent(selector);
        forgedMeta.setItemModel(new NamespacedKey("advance_magic","staff_wood"));forged.setItemMeta(forgedMeta);
        check(!magic.staffs().isStaff(forged),"decorative wooden selectors alone cannot forge staff identity");

        var resolve=magic.creativeBridge().getClass().getDeclaredMethod("resolveCustomItem",String.class);resolve.setAccessible(true);
        var creative=(org.bukkit.inventory.ItemStack)resolve.invoke(magic.creativeBridge(),id);
        check(magic.staffs().isWooden(creative)&&magic.staffSpells().tier(creative)==com.example.advancemagic.elemental.StaffSpellService.Tier.WOODEN
                &&magic.staffSpells().spells(creative).isEmpty(),"Bedrock creative bridge issues empty wooden starter");
        check(resolve.invoke(magic.creativeBridge(),"advance_magic:staff_unknown")==null,"Bedrock creative bridge rejects unknown staff");

        try(var pack=new java.util.zip.ZipFile(magic.getDataFolder().toPath().resolve("resource-packs/advance-magic-java.zip").toFile())){
            var definition=packJson(pack,"assets/advance_magic/items/staff_wood.json").getAsJsonObject("model");
            check("minecraft:select".equals(definition.get("type").getAsString())&&"minecraft:display_context".equals(definition.get("property").getAsString()),"wooden Java definition selects display context");
            String normal=definition.getAsJsonObject("fallback").get("model").getAsString(),gui=null;
            for(var entry:definition.getAsJsonArray("cases")){
                var entryObject=entry.getAsJsonObject();
                if("gui".equals(entryObject.get("when").getAsString()))gui=entryObject.getAsJsonObject("model").get("model").getAsString();
            }
            check("advance_magic:item/wooden_staff/wood".equals(normal),"wooden Java default resolves authored hand model");
            check("advance_magic:item/wooden_staff/wood_icon".equals(gui),"wooden Java GUI resolves authored inventory icon");
            var handModel=packJson(pack,assetPath(normal,"models",".json"));
            check(!handModel.getAsJsonArray("elements").isEmpty(),"wooden Java hand model contains geometry");
            check(handModel.getAsJsonObject("display").has("firstperson_righthand")&&handModel.getAsJsonObject("display").has("firstperson_lefthand"),"wooden Java first-person transforms available in both hands");
            String handTexture=handModel.getAsJsonObject("textures").get("body").getAsString();
            check(pack.getEntry(assetPath(handTexture,"textures",".png"))!=null,"wooden Java hand texture packaged");
            var guiModel=packJson(pack,assetPath(gui,"models",".json"));
            check("minecraft:item/generated".equals(guiModel.get("parent").getAsString()),"wooden GUI icon uses generated item model");
            check(pack.getEntry(assetPath(guiModel.getAsJsonObject("textures").get("layer0").getAsString(),"textures",".png"))!=null,"wooden Java GUI icon texture packaged");
        }
    }
    static String assetPath(String id,String kind,String extension){String[] key=id.split(":",2);return "assets/"+key[0]+"/"+kind+"/"+key[1]+extension;}
    static com.google.gson.JsonObject packJson(java.util.zip.ZipFile pack,String path) throws Exception {
        var entry=pack.getEntry(path);if(entry==null)throw new AssertionError("Missing Java pack entry "+path);
        try(var reader=new java.io.InputStreamReader(pack.getInputStream(entry),java.nio.charset.StandardCharsets.UTF_8)){
            return com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
        }
    }
    void finish(Throwable error){
        if(error!=null)getLogger().log(java.util.logging.Level.SEVERE,"STAFF INTEGRATION FAILED after "+checks,error);
        try{Files.writeString(Path.of("elemental-staff-integration-result.txt"),error==null?"PASS "+checks:"FAIL "+error);}catch(Exception ignored){}
        Bukkit.getScheduler().runTaskLater(this,Bukkit::shutdown,5);
    }
}
