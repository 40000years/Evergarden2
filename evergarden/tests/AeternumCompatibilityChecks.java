import com.example.voidscape.VoidscapePlugin;
import com.example.voidscape.enchant.*;
import com.example.voidscape.crop.CropType;
import com.example.voidscape.item.RelicService.Relic;
import com.example.voidscape.pack.ResourcePackService;
import com.mojang.authlib.GameProfile;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.bukkit.*;
import org.bukkit.block.BlockFace;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import java.net.InetSocketAddress;
import java.lang.reflect.Proxy;
import java.nio.file.*;
import java.util.*;

/** Run only in the disposable server created by run_aeternum_checks.py. */
public final class AeternumCompatibilityChecks extends JavaPlugin {
    static final class TestConnection extends Connection {
        TestConnection(){super(PacketFlow.SERVERBOUND);channel=new EmbeddedChannel();address=new InetSocketAddress("127.0.0.1",1);}
        @Override public void send(Packet<?> p){}
        @Override public void send(Packet<?> p,ChannelFutureListener l){}
        @Override public void send(Packet<?> p,ChannelFutureListener l,boolean f){}
        @Override public boolean isConnected(){return true;}
    }
    final List<String> results=new ArrayList<>();
    Player actor;
    void check(boolean ok,String description){String line=(ok?"PASS ":"FAIL ")+description;results.add(line);getLogger().info(line);}
    @Override public void onEnable(){Bukkit.getScheduler().runTaskLater(this,()->{
        try{run();}catch(Throwable t){check(false,"exception: "+t);getLogger().log(java.util.logging.Level.SEVERE,"Compatibility checks failed",t);}
        finally{
            if(actor!=null){actor.getInventory().clear();MinecraftServer.getServer().getPlayerList().getPlayers().removeIf(p->p.getUUID().equals(actor.getUniqueId()));MinecraftServer.getServer().getPlayerList().getPlayersByUUID().remove(actor.getUniqueId());}
            try{Files.write(Path.of("compatibility-result.txt"),results);}catch(Exception e){throw new RuntimeException(e);}
            Bukkit.getScheduler().runTaskLater(this,Bukkit::shutdown,5);
        }
    },40);}
    void run() throws Exception {
        var garden=(VoidscapePlugin)Bukkit.getPluginManager().getPlugin("Evergarden");
        check(garden!=null&&garden.isEnabled()&&Bukkit.getPluginManager().isPluginEnabled("AeternumSeasons"),"both plugins enabled");
        if(Files.exists(Path.of("check-fresh-geyser"))) {
            check(Bukkit.getPluginManager().isPluginEnabled("Geyser-Spigot"),"Geyser enabled after automatic installation");
            Path geyser=Path.of("plugins/Geyser-Spigot");
            for(String name:com.example.voidscape.pack.AeternumBedrockInstaller.FILES) {
                Path installed=geyser.resolve(name.endsWith(".mcpack")?"packs":"custom_mappings").resolve(name);
                try(var input=garden.getResource("aeternum-bedrock/"+name)) {
                    check(Files.exists(installed)&&Arrays.equals(Files.readAllBytes(installed),input.readAllBytes()),"automatically installed official asset: "+name);
                }
            }
            check(org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(geyser.resolve("config.yml").toFile())
                    .getBoolean("gameplay.enable-custom-content"),"Geyser custom content automatically enabled");
        }
        var relics=garden.relics();
        for(int level:new int[]{7,8,10})for(boolean unbreakable:new boolean[]{false,true}){
            var item=new ItemStack(Material.DIAMOND_PICKAXE);var meta=item.getItemMeta();
            meta.addEnchant(Enchantment.EFFICIENCY,level,true);meta.setUnbreakable(unbreakable);
            meta.setDisplayName("Aeternum heat loot");
            meta.getPersistentDataContainer().set(new NamespacedKey("aeternumseasons","test_origin"),PersistentDataType.STRING,"heat");
            item.setItemMeta(meta);var before=item.clone();
            relics.migrate(item);relics.type(item);
            check(before.equals(item),"foreign Efficiency "+level+" unbreakable="+unbreakable+" preserved exactly");
            check(!EnchantApplyListener.hasUnique(item,UniqueEnchant.ADVANCE_TOOL)&&!relics.isEternityItem(item),"foreign loot gains no Evergarden abilities "+level+"/"+unbreakable);
            if(level<10){
                var upgraded=relics.evaluateScrollCraft(relics.createScrollLimitBreak(LimitBreakType.EFFICIENCY),item);
                check(upgraded!=null&&relics.getLimitBreakLevel(upgraded,LimitBreakType.EFFICIENCY)==level+1,"explicit scroll upgrades native "+level+" to "+(level+1));
            }
        }
        var named=new ItemStack(Material.NETHERITE_PICKAXE);var meta=named.getItemMeta();
        meta.setDisplayName("Aeternum Smelter");meta.addEnchant(Enchantment.EFFICIENCY,8,true);named.setItemMeta(meta);
        var before=named.clone();relics.migrate(named);
        check(named.equals(before)&&relics.type(named)==null,"foreign display name cannot claim Evergarden ownership");
        var external=new ItemStack(Material.DIAMOND_PICKAXE);meta=external.getItemMeta();
        var tool=meta.getTool();tool.setDefaultMiningSpeed(3.0f);tool.addRule(List.of(Material.STONE),37.0f,true);meta.setTool(tool);
        relics.applyEternityMeta(meta);meta.setUnbreakable(true);external.setItemMeta(meta);before=external.clone();relics.migrate(external);
        check(external.equals(before),"Eternity does not erase another plugin's tool component or native unbreakable flag");
        var upgraded=new ItemStack(Material.DIAMOND_PICKAXE);meta=upgraded.getItemMeta();
        relics.applyLimitBreakMeta(upgraded.getType(),meta,LimitBreakType.EFFICIENCY,8);meta.setTool(null);upgraded.setItemMeta(meta);
        relics.migrate(upgraded);check(upgraded.getItemMeta().hasTool(),"owned Limit Break item repairs its tool component");
        check(new NamespacedKey("voidscape","void_elixir").equals(relics.create(Relic.VOID_ELIXIR,1).getItemMeta().getItemModel()),"elixir has independent model for stacked food packs");
        var oldElixir=relics.create(Relic.VOID_ELIXIR,1);meta=oldElixir.getItemMeta();meta.setItemModel(null);oldElixir.setItemMeta(meta);
        check(relics.migrate(oldElixir)&&new NamespacedKey("voidscape","void_elixir").equals(oldElixir.getItemMeta().getItemModel())&&!relics.migrate(oldElixir),"stored elixir migrates once without changing its identity");
        var offers=new ArrayList<UUID>();var offerArgs=new ArrayList<Object[]>();var packPlayerId=UUID.randomUUID();
        Player packPlayer=(Player)Proxy.newProxyInstance(getClassLoader(),new Class[]{Player.class},(o,m,a)->switch(m.getName()){
            case "getUniqueId" -> packPlayerId;
            case "getName" -> "PackTestActor";
            case "addResourcePack" -> {offers.add((UUID)a[0]);offerArgs.add(a);yield null;}
            default -> null;
        });
        garden.getConfig().set("resource-pack.enabled",true);
        garden.packs().offer(packPlayer);
        check(offers.equals(List.of(ResourcePackService.PACK_ID,ResourcePackService.AETERNUM_PACK_ID)),"food pack is added after Evergarden without replacing its pack");
        check(offerArgs.size()==2&&garden.getConfig().getString("compatibility.aeternum-seasons.resource-pack.url",ResourcePackService.AETERNUM_PACK_URL).equals(offerArgs.get(1)[1])&&ResourcePackService.AETERNUM_PACK_SHA1.equals(HexFormat.of().formatHex((byte[])offerArgs.get(1)[2])),"official food pack URL and SHA-1 are paired");
        offers.clear();garden.getConfig().set("compatibility.aeternum-seasons.resource-pack.enabled",false);garden.packs().offer(packPlayer);
        check(offers.equals(List.of(ResourcePackService.PACK_ID)),"food pack integration can be disabled");
        garden.getConfig().set("resource-pack.enabled",false);

        var world=Bukkit.getWorlds().get(0);var server=MinecraftServer.getServer();var level=((CraftWorld)world).getHandle();
        var profile=new GameProfile(UUID.randomUUID(),"CompatibilityActor");
        var handle=new ServerPlayer(server,level,profile,ClientInformation.createDefault());
        handle.connection=new ServerGamePacketListenerImpl(server,new TestConnection(),handle,CommonListenerCookie.createInitial(profile,false));
        handle.setPos(0.5,100,0.5);server.getPlayerList().getPlayers().add(handle);server.getPlayerList().getPlayersByUUID().put(profile.id(),handle);
        level.addNewPlayer(handle);actor=handle.getBukkitEntity();actor.setOp(true);actor.setGravity(false);actor.setGameMode(GameMode.SURVIVAL);
        Object foods=Arrays.stream(PlayerInteractEvent.getHandlerList().getRegisteredListeners()).map(r->r.getListener())
            .filter(l->l.getClass().getName().equals("Kinkin.aeternum.food.SeasonFoods")).findFirst().orElseThrow();
        int x=1;
        for(String id:List.of("onion","tomato")){
            var field=foods.getClass().getDeclaredField(id+"Proto");field.setAccessible(true);
            var seed=((ItemStack)field.get(foods)).clone();seed.setAmount(3);before=seed.clone();relics.migrate(seed);
            check(seed.equals(before)&&garden.crops().factory().getSeedType(seed)==null,"Aeternum "+id+" seed metadata stays separate");
            actor.getInventory().setItemInMainHand(seed);
            var soil=world.getBlockAt(x++,99,0);soil.setType(Material.FARMLAND,false);soil.getRelative(BlockFace.UP).setType(Material.AIR,false);
            var event=new PlayerInteractEvent(actor,Action.RIGHT_CLICK_BLOCK,actor.getInventory().getItemInMainHand(),soil,BlockFace.UP,EquipmentSlot.HAND);
            Bukkit.getPluginManager().callEvent(event);
            check(!soil.getRelative(BlockFace.UP).getType().isAir(),"Aeternum "+id+" plants with both event listeners loaded");
            check(actor.getInventory().getItemInMainHand().getAmount()==2,"Aeternum "+id+" consumes one seed");
        }
        var riceField=foods.getClass().getDeclaredField("riceProto");riceField.setAccessible(true);
        var rice=((ItemStack)riceField.get(foods)).clone();rice.setAmount(3);before=rice.clone();relics.migrate(rice);
        check(rice.equals(before)&&garden.crops().factory().getSeedType(rice)==null,"Aeternum rice seed metadata stays separate");
        actor.getInventory().setItemInMainHand(rice);
        var water=world.getBlockAt(x++,100,0);water.setType(Material.WATER,false);
        var riceEvent=new PlayerInteractEvent(actor,Action.RIGHT_CLICK_BLOCK,actor.getInventory().getItemInMainHand(),water,BlockFace.UP,EquipmentSlot.HAND);
        Bukkit.getPluginManager().callEvent(riceEvent);
        check(water.getType()==Material.KELP,"Aeternum rice plants in source water with both listeners loaded");
        check(actor.getInventory().getItemInMainHand().getAmount()==2,"Aeternum rice consumes one seed");

        var isAeternumSeed=foods.getClass().getMethod("isAnyArtificialSeed",ItemStack.class);
        int evergardenPlanted=0;
        for(CropType cropType:CropType.values()){
            var seed=garden.crops().factory().createSeed(cropType,3);before=seed.clone();relics.migrate(seed);
            check(seed.equals(before)&&!(boolean)isAeternumSeed.invoke(foods,seed),"Evergarden "+cropType.id+" seed metadata stays separate");
            actor.getInventory().setItemInMainHand(seed);
            var soil=world.getBlockAt(x++,99,0);soil.setType(Material.FARMLAND,false);var above=soil.getRelative(BlockFace.UP);above.setType(Material.AIR,false);
            var event=new PlayerInteractEvent(actor,Action.RIGHT_CLICK_BLOCK,actor.getInventory().getItemInMainHand(),soil,BlockFace.UP,EquipmentSlot.HAND);
            Bukkit.getPluginManager().callEvent(event);
            boolean planted=garden.crops().getCropAt(above.getLocation())!=null;
            check(planted,"Evergarden "+cropType.id+" plants with both event listeners loaded");
            check(actor.getInventory().getItemInMainHand().getAmount()==2,"Evergarden "+cropType.id+" consumes one seed");
            if(planted)evergardenPlanted++;
        }
        check(evergardenPlanted==CropType.values().length,"all "+CropType.values().length+" Evergarden crop types planted");
    }
}
