import com.example.forge.*;
import com.example.forge.GemService.Gem;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.server.network.*;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemAttributeModifiers;
import java.net.*;
import java.security.MessageDigest;
import java.util.*;

/** Real synthetic players and InventoryEvents exercise GUI transactions, death and pack delivery. */
public final class ForgeAuditProbe extends ForgeProbe {
    private ForgePlugin forge;private Player player;
    private ServerPlayer fake(String name){
        World world=Bukkit.getWorlds().getFirst();world.setChunkForceLoaded(0,0,true);
        var server=MinecraftServer.getServer();var level=((CraftWorld)world).getHandle();
        var profile=new GameProfile(UUID.randomUUID(),name);var actor=new ServerPlayer(server,level,profile,ClientInformation.createDefault());
        var connection=new Connection(PacketFlow.SERVERBOUND);connection.channel=new EmbeddedChannel();connection.address=new InetSocketAddress("127.0.0.1",1);
        actor.connection=new ServerGamePacketListenerImpl(server,connection,actor,CommonListenerCookie.createInitial(profile,false)){
            @Override public boolean hasClientLoaded(){return true;}
        };
        actor.setPos(.5,world.getHighestBlockYAt(0,0)+2,.5);
        server.getPlayerList().getPlayers().add(actor);server.getPlayerList().getPlayersByUUID().put(profile.id(),actor);level.addNewPlayer(actor);
        var p=actor.getBukkitEntity();p.setGravity(false);p.setInvulnerable(true);p.setGameMode(GameMode.SURVIVAL);
        p.addAttachment(this,"forge.use",true);return actor;
    }
    private Inventory top(){return player.getOpenInventory().getTopInventory();}
    private InventoryClickEvent click(int slot){return click(slot,ClickType.LEFT,InventoryAction.PICKUP_ALL,false);}
    private InventoryClickEvent click(int slot,ClickType type,InventoryAction action,boolean cancelled){
        var event=new InventoryClickEvent(player.getOpenInventory(),InventoryType.SlotType.CONTAINER,slot,type,action);
        event.setCancelled(cancelled);Bukkit.getPluginManager().callEvent(event);return event;
    }
    private void open(ForgeMenu.Page page){player.closeInventory();player.getInventory().clear();player.setItemOnCursor(null);forge.menu().open(player,page);}
    private void fill(){for(int i=0;i<36;i++)player.getInventory().setItem(i,new ItemStack(Material.STONE,64));}
    private int countGem(Gem gem){
        int n=0;for(ItemStack item:player.getInventory().getStorageContents())if(gem.equals(forge.gems().read(item)))n+=item.getAmount();return n;
    }
    @Override protected void additionalChecks(ForgePlugin plugin)throws Exception{
        forge=plugin;player=fake("ForgeAuditor").getBukkitEntity();player.setLevel(200);
        models();reforge();gems();cutAndSalvage();guards();attributes();protectedItems();oreMoves();death(false);death(true);configMigration();pack();
        player.closeInventory();
    }
    private void models(){
        Set<NamespacedKey> keys=new HashSet<>();
        for(GemType type:GemType.values())for(int tier=1;tier<=5;tier++){
            Gem gem=new Gem(type,tier);ItemStack item=forge.gems().item(gem,3);
            check(keys.add(item.getItemMeta().getItemModel()),"unique model for "+gem.serialize());
            ItemStack old=item.clone();old.editMeta(m->m.setItemModel(null));
            check(ForgeResources.model(forge,old)&&old.isSimilar(item)&&old.getAmount()==3,"legacy gem migrates without losing identity: "+gem.serialize());
        }
        for(Rarity rarity:Rarity.values()){
            ItemStack item=forge.affixes().material(rarity,4);check(keys.add(item.getItemMeta().getItemModel()),"unique material model "+rarity);
            item.editMeta(m->m.setItemModel(null));check(ForgeResources.model(forge,item)&&item.isSimilar(forge.affixes().material(rarity,1)),"legacy material migrates and stacks: "+rarity);
        }
        check(keys.add(forge.gems().dust(1).getItemMeta().getItemModel())&&keys.size()==46,"all 46 gem/material/dust models distinct");
    }
    private void reforge(){
        open(ForgeMenu.Page.REFORGE);ItemStack sword=new ItemStack(Material.IRON_SWORD);
        sword.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.SHARPNESS,8);
        sword.editMeta(m->m.customName(net.kyori.adventure.text.Component.text("Audit keeps this name")));
        top().setItem(20,sword);top().setItem(38,forge.affixes().material(Rarity.RARE,9));
        var expected=forge.reforge().choices(player,sword,Rarity.RARE).getFirst();long seed=forge.reforge().seed(player);int levels=player.getLevel();
        click(15,ClickType.LEFT,InventoryAction.PICKUP_ALL,true);
        check(forge.reforge().seed(player)==seed&&top().getItem(38).getAmount()==9,"cancelled event cannot spend forge inputs or advance seed");
        check(click(15).isCancelled(),"preview click is cancelled as a vanilla pickup");
        check(top().getItem(20).equals(expected)&&top().getItem(38).getAmount()==6&&player.getLevel()==levels-10,"reforge delivers exact preview and charges once");
        check(forge.reforge().seed(player)!=seed&&expected.getEnchantmentLevel(org.bukkit.enchantments.Enchantment.SHARPNESS)==8,"reforge advances seed and preserves overcap enchant");
        ItemStack emptyPreview=top().getItem(15).clone();
        check(click(15,ClickType.MIDDLE,InventoryAction.CLONE_STACK,false).isCancelled(),"creative-style preview cloning is denied");
        // A clone/drop/number key on a result must never trigger a paid action.
        check(top().getItem(38).getAmount()==6,"non-primary preview click does not buy another reforge");
        player.closeInventory();check(Arrays.stream(player.getInventory().getStorageContents()).filter(Objects::nonNull).anyMatch(i->i.equals(expected)),"closing returns the owned reforged item");
        Bukkit.getPluginManager().callEvent(new InventoryCloseEvent(player.getOpenInventory()));
        check(Arrays.stream(player.getInventory().getStorageContents()).filter(Objects::nonNull).filter(i->i.equals(expected)).count()==1,"close cannot return the input twice");
    }
    private void gems(){
        open(ForgeMenu.Page.GEMS);var fire=new Gem(GemType.FIRE,1);var water=new Gem(GemType.WATER,1);
        ItemStack sword=new ItemStack(Material.IRON_SWORD);forge.affixes().apply(sword,new AffixData(Rarity.RARE,Map.of(AffixType.SHARP,.5),1));forge.affixes().setGem(sword,0,fire);
        top().setItem(20,sword);fill();player.getInventory().setItem(0,forge.gems().item(water,2));click(81);
        click(23);check(fire.equals(forge.affixes().gems(top().getItem(20)).getFirst())&&countGem(water)==2,"full-inventory gem replacement is atomic");
        player.getInventory().setItem(0,forge.gems().item(water,1));click(23);
        check(water.equals(forge.affixes().gems(top().getItem(20)).getFirst())&&countGem(fire)==1&&countGem(water)==0,"consuming the last selected gem frees room for the old gem");
        fill();click(23);check(water.equals(forge.affixes().gems(top().getItem(20)).getFirst()),"full-inventory gem extraction is refused");
        player.getInventory().setItem(1,null);click(23);
        check(forge.affixes().gems(top().getItem(20)).getFirst()==null&&countGem(water)==1,"extracting returns exactly one gem and clears socket");
        var drag=new InventoryDragEvent(player.getOpenInventory(),null,forge.gems().item(fire,2),false,Map.of(20,forge.gems().item(fire,1),23,forge.gems().item(fire,1)));
        Bukkit.getPluginManager().callEvent(drag);check(drag.isCancelled(),"drag cannot overwrite equipment or populate a preview socket");
    }
    private void cutAndSalvage(){
        open(ForgeMenu.Page.CUT);Gem gem=new Gem(GemType.LIGHTNING,1);
        top().setItem(20,forge.gems().item(gem,2));top().setItem(22,forge.gems().dust(2));top().setItem(24,forge.affixes().material(Rarity.UNCOMMON,1));fill();
        click(40);check(top().getItem(20).getAmount()==2&&top().getItem(22).getAmount()==2,"full-inventory cutting preserves every input");
        player.getInventory().setItem(0,null);click(40);
        check(top().getItem(20)==null&&top().getItem(22)==null&&top().getItem(24)==null&&countGem(new Gem(GemType.LIGHTNING,2))==1,"cut consumes exact inputs and yields one upgraded gem");
        open(ForgeMenu.Page.SALVAGE);top().setItem(10,forge.gems().item(gem,64));fill();click(49);click(49);
        check(top().getItem(10).getAmount()==64,"full-inventory salvage preserves the complete stack");
        player.getInventory().setItem(0,null);click(49);click(49);
        check(top().getItem(10)==null&&forge.gems().isDust(player.getInventory().getItem(0))&&player.getInventory().getItem(0).getAmount()==64,"bulk salvage returns exact dust and consumes once");
    }
    private void guards(){
        open(ForgeMenu.Page.CODEX);
        check(click(19,ClickType.NUMBER_KEY,InventoryAction.HOTBAR_SWAP,false).isCancelled(),"codex icons cannot be withdrawn by hotbar swap");
        var permission=player.addAttachment(this,"forge.use",false);
        check(click(19).isCancelled(),"open menu checks revoked permission");permission.remove();
        player.closeInventory();Inventory hopper=Bukkit.createInventory(null,InventoryType.HOPPER),furnace=Bukkit.createInventory(null,InventoryType.FURNACE);
        var move=new InventoryMoveItemEvent(hopper,forge.gems().item(new Gem(GemType.LIGHTNING,1),1),furnace,true);
        Bukkit.getPluginManager().callEvent(move);check(move.isCancelled(),"hopper cannot feed a forge ingredient to a vanilla station");
        player.openInventory(furnace);player.getInventory().setItemInOffHand(forge.gems().dust(1));
        var offhand=click(0,ClickType.SWAP_OFFHAND,InventoryAction.HOTBAR_SWAP,false);
        check(offhand.isCancelled(),"offhand swap cannot bypass ingredient protection");player.closeInventory();
    }
    private void attributes(){
        ItemStack sword=new ItemStack(Material.IRON_SWORD);forge.affixes().apply(sword,new AffixData(Rarity.COMMON,Map.of(AffixType.SHARP,.5)));
        var attrs=sword.getData(DataComponentTypes.ATTRIBUTE_MODIFIERS);var changed=ItemAttributeModifiers.itemAttributes();
        for(var entry:attrs.modifiers()){
            if(entry.modifier().getKey().getNamespace().equals("evergardenforge"))changed.addModifier(Attribute.ATTACK_DAMAGE,entry.modifier(),EquipmentSlotGroup.ANY,entry.display());
            else changed.addModifier(entry.attribute(),entry.modifier(),entry.getGroup(),entry.display());
        }
        sword.setData(DataComponentTypes.ATTRIBUTE_MODIFIERS,changed.build());
        check(forge.affixes().ensure(sword),"ensure repairs a correct-valued bonus applied to the wrong slot");
        check(sword.getData(DataComponentTypes.ATTRIBUTE_MODIFIERS).modifiers().stream().filter(e->e.modifier().getKey().getNamespace().equals("evergardenforge"))
            .allMatch(e->e.getGroup().equals(EquipmentSlotGroup.MAINHAND)),"repaired weapon bonus is active only in main hand");
        sword.editMeta(m->{var lore=new ArrayList<>(m.lore());lore.remove(1);m.lore(lore);});
        check(forge.affixes().ensure(sword),"ensure repairs a missing stat line even when the rarity header remains");
        ItemStack bow=new ItemStack(Material.BOW);forge.affixes().apply(bow,new AffixData(Rarity.RARE,Map.of(AffixType.ARROW_POWER,.7)));
        var before=forge.settings().affixes.get(AffixType.ARROW_POWER);
        forge.settings().affixes.put(AffixType.ARROW_POWER,new ForgeSettings.Range(false,before.min(),before.max()));
        check(forge.affixes().ensure(bow)&&forge.affixes().total(bow,Stat.ARROW_DAMAGE)==0,"disabled projectile affix removes stale lore as well as its effect");
        forge.settings().affixes.put(AffixType.ARROW_POWER,before);
    }
    private void protectedItems(){
        for(String namespace:List.of("voidscape","evergarden")){
            ItemStack relic=new ItemStack(Material.DIAMOND_PICKAXE);
            relic.editMeta(m->m.getPersistentDataContainer().set(new NamespacedKey(namespace,"relic_v2"),org.bukkit.persistence.PersistentDataType.STRING,"rift_pickaxe"));
            check(forge.affixes().category(relic)==null&&!forge.affixes().salvageable(relic),"canonical/legacy relic identity cannot enter forge: "+namespace);
            ItemStack upgraded=new ItemStack(Material.IRON_SWORD);
            upgraded.editMeta(m->m.getPersistentDataContainer().set(new NamespacedKey(namespace,"relic_eternity"),org.bukkit.persistence.PersistentDataType.BYTE,(byte)1));
            check(forge.affixes().affix(upgraded,Rarity.RARE,new Random(8)),"Eternity upgrade remains forge-compatible: "+namespace);
        }
    }
    private NamespacedKey oreKey(org.bukkit.block.Block block){
        return new NamespacedKey(forge,"ore_placed_"+block.getX()+"_"+block.getY()+"_"+block.getZ());
    }
    private boolean marked(org.bukkit.block.Block block){
        return block.getChunk().getPersistentDataContainer().has(oreKey(block),org.bukkit.persistence.PersistentDataType.BYTE);
    }
    private void oreMoves(){
        var world=player.getWorld();world.setChunkForceLoaded(15,14,true);
        var piston=world.getBlockAt(238,90,226);piston.setType(Material.PISTON);
        var first=world.getBlockAt(239,90,226);var next=first.getRelative(org.bukkit.block.BlockFace.EAST);
        first.setType(Material.DIAMOND_ORE);next.setType(Material.DIAMOND_ORE);
        forge.oreGems().onPlace(new org.bukkit.event.block.BlockPlaceEvent(first,first.getState(),first,
            new ItemStack(Material.DIAMOND_ORE),player,true,EquipmentSlot.HAND));
        var cancelled=new org.bukkit.event.block.BlockPistonExtendEvent(piston,List.of(first,next),org.bukkit.block.BlockFace.EAST);
        cancelled.setCancelled(true);Bukkit.getPluginManager().callEvent(cancelled);
        check(marked(first)&&!marked(next),"cancelled piston preserves placed-ore provenance");
        Bukkit.getPluginManager().callEvent(new org.bukkit.event.block.BlockPistonExtendEvent(piston,List.of(first,next),org.bukkit.block.BlockFace.EAST));
        check(!marked(first)&&marked(next)&&!marked(next.getRelative(org.bukkit.block.BlockFace.EAST)),
            "piston chain carries only placed-ore marker across a chunk boundary");
        first.setType(Material.AIR);next.setType(Material.DIAMOND_ORE);
        var pulled=next.getRelative(org.bukkit.block.BlockFace.WEST);
        Bukkit.getPluginManager().callEvent(new org.bukkit.event.block.BlockPistonRetractEvent(piston,List.of(next),org.bukkit.block.BlockFace.WEST));
        check(marked(pulled)&&!marked(next),"sticky piston preserves placed-ore marker when pulling back");
        pulled.setType(Material.DIAMOND_ORE);
        var cancelledBreak=new org.bukkit.event.block.BlockBreakEvent(pulled,player);cancelledBreak.setCancelled(true);
        Bukkit.getPluginManager().callEvent(cancelledBreak);
        check(marked(pulled),"cancelled mining cannot erase placed-ore protection");
        double chance=forge.settings().oreGemChances.get(Material.DIAMOND_ORE);
        forge.settings().oreGemChances.put(Material.DIAMOND_ORE,1.0);
        player.getInventory().setItemInMainHand(new ItemStack(Material.DIAMOND_PICKAXE));
        try{
            long before=oreGemDrops(world);
            Bukkit.getPluginManager().callEvent(new org.bukkit.event.block.BlockBreakEvent(pulled,player));
            check(!marked(pulled)&&oreGemDrops(world)==before,
                "mining moved player-placed ore never drops a gem even at a forced 100% rate");
            Bukkit.getPluginManager().callEvent(new org.bukkit.event.block.BlockBreakEvent(pulled,player));
            check(oreGemDrops(world)==before+1,"eligible unmarked ore really drops one gem at a forced 100% rate");
        }finally{forge.settings().oreGemChances.put(Material.DIAMOND_ORE,chance);}
        first.setType(Material.AIR);next.setType(Material.AIR);piston.setType(Material.AIR);
    }
    private long oreGemDrops(World world){
        return world.getEntitiesByClass(Item.class).stream().filter(item->forge.gems().read(item.getItemStack())!=null).count();
    }
    private void configMigration()throws Exception{
        var file=forge.getDataFolder().toPath().resolve("config.yml");
        String original=java.nio.file.Files.readString(file);var backup=forge.getDataFolder().toPath().resolve("config-v6-backup.yml");
        java.nio.file.Files.writeString(backup,"keep existing backup");
        try{
            forge.getConfig().set("config-version",6);forge.getConfig().set("elites.chance",0.99);
            forge.getConfig().set("resource-pack.public-host","packs.example.invalid");
            forge.getConfig().set("resource-pack.public-url","https://packs.example.invalid/forge-{sha1}.zip");
            forge.getConfig().set("resource-pack.port",25580);forge.getConfig().set("resource-pack.geyser-auto-install",false);
            forge.getConfig().set("command-opens-anywhere",false);forge.getConfig().set("station-block","ANVIL");
            forge.saveConfig();forge.reload();
            check(forge.getConfig().getInt("config-version")==7&&Math.abs(forge.settings().eliteChance-.05)<1e-12,
                "old config migrates to current 5% elite rate");
            check("packs.example.invalid".equals(forge.getConfig().getString("resource-pack.public-host"))
                &&"https://packs.example.invalid/forge-{sha1}.zip".equals(forge.getConfig().getString("resource-pack.public-url"))
                &&forge.getConfig().getInt("resource-pack.port")==25580&&!forge.getConfig().getBoolean("resource-pack.geyser-auto-install"),
                "rate migration preserves server pack URL, port and Geyser settings");
            check(!forge.settings().commandAnywhere&&forge.settings().stationBlock==Material.ANVIL,
                "rate migration preserves station access settings");
            check(java.nio.file.Files.readString(backup).equals("keep existing backup")
                &&java.nio.file.Files.readString(forge.getDataFolder().toPath().resolve("config-v6-backup-1.yml")).contains("0.99"),
                "migration keeps existing backup and saves the old config separately");
        }finally{java.nio.file.Files.writeString(file,original);forge.reload();}
    }
    private void death(boolean keep){
        Player previous=player;var actor=fake(keep?"ForgeKeep":"ForgeDrop");player=actor.getBukkitEntity();
        World world=player.getWorld();boolean rule=Boolean.TRUE.equals(world.getGameRuleValue(GameRule.KEEP_INVENTORY));world.setGameRule(GameRule.KEEP_INVENTORY,keep);
        forge.menu().open(player,ForgeMenu.Page.CUT);ItemStack input=forge.gems().item(new Gem(GemType.LIFE,3),7);top().setItem(20,input);
        final int[] dropped={-1};Player dying=player;
        Listener listener=new Listener(){@EventHandler(priority=EventPriority.MONITOR) public void died(PlayerDeathEvent event){
            if(event.getEntity()==dying)dropped[0]=event.getDrops().stream().filter(i->new Gem(GemType.LIFE,3).equals(forge.gems().read(i))).mapToInt(ItemStack::getAmount).sum();
        }};
        Bukkit.getPluginManager().registerEvents(listener,this);player.setInvulnerable(false);player.setHealth(0);
        check(dropped[0]==(keep?0:7)&&countGem(new Gem(GemType.LIFE,3))==(keep?7:0),"real player death preserves escrow exactly once, keepInventory="+keep+" drops="+dropped[0]);
        player.closeInventory();check(countGem(new Gem(GemType.LIFE,3))==(keep?7:0),"death followed by menu close cannot duplicate escrow, keepInventory="+keep);
        HandlerList.unregisterAll(listener);world.setGameRule(GameRule.KEEP_INVENTORY,rule);player=previous;
    }
    private void pack()throws Exception{
        String url=forge.resources().url(player);check(!url.isEmpty(),"resource pack has a usable addon URL");
        forge.resources().offer(player,true);
        var connection=(HttpURLConnection)URI.create(url).toURL().openConnection();connection.setConnectTimeout(5000);connection.setReadTimeout(5000);
        byte[] bytes=connection.getInputStream().readAllBytes();check(connection.getResponseCode()==200,"pack HTTP serves the addon successfully");
        String digest=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(bytes));
        check(forge.resources().status().contains(digest),"offered pack SHA-1 equals downloaded bytes");
        if(Bukkit.getPluginManager().isPluginEnabled("Evergarden")){
            check(forge.resources().status().contains("Evergarden host"),"forge reuses the existing Evergarden host");
            var field=ForgeResources.class.getDeclaredField("delivery");field.setAccessible(true);Object delivery=field.get(forge.resources());
            check(delivery!=null&&!delivery.getClass().getMethod("status",Player.class).invoke(delivery,player).equals("NOT_OFFERED"),"forge joins the shared resource-pack delivery coordinator");
        }
        if(Bukkit.getPluginManager().isPluginEnabled("Geyser-Spigot")){
            var folder=Bukkit.getPluginManager().getPlugin("Geyser-Spigot").getDataFolder().toPath();
            check(java.nio.file.Files.isRegularFile(folder.resolve("packs/evergarden-forge.mcpack"))&&java.nio.file.Files.isRegularFile(folder.resolve("custom_mappings/evergarden-forge.json")),"Bedrock assets installed before Geyser boot");
        }
    }
}
