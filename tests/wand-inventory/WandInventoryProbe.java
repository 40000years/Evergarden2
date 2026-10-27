import com.example.advancemagic.AdvanceMagicPlugin;
import com.example.advancemagic.spell.Spell;
import com.example.voidscape.VoidscapePlugin;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.network.Connection;
import net.minecraft.network.HashedStack;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.server.network.*;
import net.minecraft.world.inventory.*;
import org.bukkit.*;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Runs real Paper container packets on a disposable server; never included in release jars. */
public final class WandInventoryProbe extends JavaPlugin implements Listener {
    AdvanceMagicPlugin magic;
    ServerPlayer actor;
    Player player;
    int checks, craftEvents, ordinaryResults;
    final List<org.bukkit.entity.Item> spawnedDrops=new ArrayList<>();
    boolean forceCancellation;
    String scenario;
    void check(boolean value, String label) {
        if (!value) throw new AssertionError(scenario + ": " + label);
        checks++; getLogger().info("PASS Inventory: " + scenario + ": " + label);
    }
    @EventHandler(priority=EventPriority.MONITOR)
    public void click(InventoryClickEvent event) {
        if (event.getWhoClicked()!=player || event.getSlotType()!=InventoryType.SlotType.RESULT) return;
        if (event instanceof CraftItemEvent) craftEvents++; else ordinaryResults++;
        getLogger().info("RESULT packet=" + scenario + " event=" + event.getClass().getSimpleName()
            + " click=" + event.getClick() + " action=" + event.getAction() + " cancelled=" + event.isCancelled());
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void spawned(org.bukkit.event.entity.ItemSpawnEvent event) {
        if(magic!=null&&magic.wands().spell(event.getEntity().getItemStack())!=null)spawnedDrops.add(event.getEntity());
    }
    @EventHandler(priority=EventPriority.LOWEST)
    public void cancelledByProtection(InventoryClickEvent event) {
        if(forceCancellation&&event.getWhoClicked()==player&&event.getSlotType()==InventoryType.SlotType.RESULT)event.setCancelled(true);
    }
    @Override public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this,this);
        Bukkit.getScheduler().runTaskLater(this,()-> {
            try { run(); Files.writeString(Path.of("wand-inventory-result.txt"),"PASS " + checks + " checks; craft events=" + craftEvents + "; fallback events=" + ordinaryResults); }
            catch(Throwable error) { getLogger().log(java.util.logging.Level.SEVERE,"INVENTORY PROBE FAILED",error); try { Files.writeString(Path.of("wand-inventory-result.txt"),"FAIL " + scenario + ": " + error); } catch(Exception ignored) {} }
            finally { cleanup(); Bukkit.getScheduler().runTaskLater(this,Bukkit::shutdown,2); }
        },40);
    }
    void run() {
        magic=(AdvanceMagicPlugin)Bukkit.getPluginManager().getPlugin("advance-magic");
        scenario="boot"; check(magic!=null&&magic.isEnabled(),"release plugin enabled");
        var server=MinecraftServer.getServer(); var world=((CraftWorld)Bukkit.getWorlds().getFirst()).getHandle();
        var profile=new GameProfile(UUID.randomUUID(),"InventoryAudit");
        actor=new ServerPlayer(server,world,profile,ClientInformation.createDefault());
        var connection=new Connection(PacketFlow.SERVERBOUND); connection.channel=new EmbeddedChannel(); connection.address=new InetSocketAddress("127.0.0.1",1);
        actor.connection=new ServerGamePacketListenerImpl(server,connection,actor,CommonListenerCookie.createInitial(profile,false)) { @Override public boolean hasClientLoaded() {return true;} };
        actor.setPos(.5,90,.5); server.getPlayerList().getPlayers().add(actor); server.getPlayerList().getPlayersByUUID().put(profile.id(),actor); world.addNewPlayer(actor);
        player=actor.getBukkitEntity(); player.setGravity(false); player.setInvulnerable(true); player.setGameMode(GameMode.SURVIVAL);
        player.addAttachment(this,"advance-magic.craft",true);
        int[][] actions={{0,0},{0,1},{1,0},{2,0},{2,40},{3,2},{4,0},{4,1},{6,0}};
        for(boolean mixed:List.of(false,true)) for(int coreSlot:List.of(4,7)) for(int[] action:actions) {
            crafting(mixed,coreSlot,3,ContainerInput.values()[action[0]],action[1],false,false);
        }
        crafting(true,4,3,ContainerInput.QUICK_MOVE,0,true,false);
        crafting(true,4,3,ContainerInput.PICKUP,0,false,true);
        crafting(true,4,3,ContainerInput.QUICK_MOVE,0,false,true);
        craftPermission();
        menu();
        var garden=(VoidscapePlugin)Bukkit.getPluginManager().getPlugin("Evergarden");
        if(garden!=null) dynamic(garden);
    }
    void reset() {
        player.closeInventory(); player.getInventory().clear(); player.setItemOnCursor(null);
        player.getWorld().getEntitiesByClass(org.bukkit.entity.Item.class).forEach(org.bukkit.entity.Item::remove);
        spawnedDrops.forEach(org.bukkit.entity.Item::remove); spawnedDrops.clear();
    }
    void packet(int slot,int button,ContainerInput action) {
        var menu=actor.containerMenu;
        actor.connection.handleContainerClick(new ServerboundContainerClickPacket(menu.containerId,menu.getStateId(),(short)slot,(byte)button,action,new Int2ObjectOpenHashMap<HashedStack>(),HashedStack.EMPTY));
    }
    int wands() {
        int result=0;
        for(ItemStack item:player.getInventory().getContents()) if(magic.wands().spell(item)!=null) result+=item.getAmount();
        ItemStack cursor=player.getItemOnCursor(); if(magic.wands().spell(cursor)!=null) result+=cursor.getAmount();
        for(var entity:spawnedDrops) if(magic.wands().spell(entity.getItemStack())!=null) result+=entity.getItemStack().getAmount();
        return result;
    }
    int matrixCores(CraftingInventory inv) {return Arrays.stream(inv.getMatrix()).filter(i->magic.wands().coreSpell(i)!=null).mapToInt(ItemStack::getAmount).sum();}
    int matrixPremium(CraftingInventory inv) {return Arrays.stream(inv.getMatrix()).filter(i->i!=null&&(i.getType()==Material.NETHERITE_INGOT||i.getType()==Material.NETHER_STAR)).mapToInt(ItemStack::getAmount).sum();}
    void matrix(CraftingInventory inv,boolean mixed,int coreSlot,int amount) {
        ItemStack[] matrix=new ItemStack[9];
        for(int i=0;i<9;i++) matrix[i]=new ItemStack(mixed&&i%2==0?Material.NETHER_STAR:Material.NETHERITE_INGOT,amount);
        matrix[coreSlot]=magic.wands().createCore(Spell.HEAVENS_JUDGMENT); matrix[coreSlot].setAmount(amount); inv.setMatrix(matrix);
    }
    void crafting(boolean mixed,int coreSlot,int amount,ContainerInput action,int button,boolean full,boolean noRecipe) {
        scenario="craft "+action+"/"+button+" mixed="+mixed+" core="+coreSlot+" full="+full+" missing-recipe="+noRecipe;
        reset(); player.openWorkbench(new Location(player.getWorld(),0,90,0),true);
        var menu=(CraftingMenu)actor.containerMenu; menu.checkReachable=false;
        var inv=(CraftingInventory)player.getOpenInventory().getTopInventory(); matrix(inv,mixed,coreSlot,amount);
        check(magic.wands().spell(inv.getResult())==Spell.HEAVENS_JUDGMENT,"correct server result before clicks");
        if(full) for(int i=0;i<36;i++) player.getInventory().setItem(i,new ItemStack(Material.STONE,64));
        if(noRecipe) menu.craftSlots.setCurrentRecipe(null);
        if(action==ContainerInput.PICKUP_ALL) player.setItemOnCursor(magic.wands().create(Spell.HEAVENS_JUDGMENT));
        int initialWands=wands(), initialCore=matrixCores(inv),initialPremium=matrixPremium(inv);
        for(int repeat=0;repeat<32;repeat++) {
            packet(0,button,action);
            int gained=wands()-initialWands, consumed=initialCore-matrixCores(inv),premium=initialPremium-matrixPremium(inv);
            check(gained>=0&&gained==consumed&&gained*8==premium,"packet "+repeat+" output="+gained+" consumed cores="+consumed+" premium="+premium);
        }
        if(full||action==ContainerInput.CLONE||action==ContainerInput.PICKUP_ALL) check(wands()==initialWands,"unsupported or full-inventory action produces no extra wand");
    }
    void craftPermission() {
        scenario="permission denial"; reset(); var attachment=player.addAttachment(this,"advance-magic.craft",false);
        player.openWorkbench(new Location(player.getWorld(),0,90,0),true); ((CraftingMenu)actor.containerMenu).checkReachable=false;
        var inv=(CraftingInventory)player.getOpenInventory().getTopInventory(); matrix(inv,true,4,3);
        for(ContainerInput action:ContainerInput.values()) packet(0,0,action);
        check(wands()==0&&matrixCores(inv)==3&&matrixPremium(inv)==24,"denied player cannot obtain result or consume ingredients"); player.removeAttachment(attachment);
    }
    void menu() {
        scenario="public Magic chest"; reset();
        player.getInventory().setItem(0,magic.wands().createCore(Spell.HEAVENS_JUDGMENT)); player.getInventory().setItem(1,new ItemStack(Material.NETHER_STAR,8));
        magic.itemMenu().open(player,false);
        for(ContainerInput action:List.of(ContainerInput.QUICK_MOVE,ContainerInput.SWAP,ContainerInput.CLONE,ContainerInput.THROW)) {
            packet(Spell.HEAVENS_JUDGMENT.ordinal(),action==ContainerInput.CLONE?2:0,action);
            check(wands()==0,"display wand cannot be taken by "+action);
        }
        packet(Spell.HEAVENS_JUDGMENT.ordinal(),0,ContainerInput.PICKUP);
        check(wands()==1&&magic.wands().coreSpell(player.getInventory().getItem(0))==null,"left-click crafts one result for one core and eight premium items");
        for(int repeat=0;repeat<32;repeat++) packet(Spell.HEAVENS_JUDGMENT.ordinal(),0,ContainerInput.PICKUP);
        check(wands()==1,"repeated display clicks without ingredients cannot duplicate result");
    }
    void dynamic(VoidscapePlugin garden) {
        // Isolate table accounting from the separately tested inventory scroll listener.
        for(var listener:InventoryClickEvent.getHandlerList().getRegisteredListeners())
            if(listener.getListener().getClass().getName().endsWith("EnchantApplyListener")) HandlerList.unregisterAll(listener.getListener());
        for(boolean registered:List.of(false,true)) for(boolean repair:List.of(false,true)) for(ContainerInput action:List.of(ContainerInput.PICKUP,ContainerInput.QUICK_MOVE,ContainerInput.SWAP,ContainerInput.CLONE,ContainerInput.THROW,ContainerInput.PICKUP_ALL)) {
            for(boolean occupied:List.of(false,true)) {
                scenario="dynamic "+(repair?"repair":"scroll")+" "+action+" occupied="+occupied+" registered="+registered; reset();
                NamespacedKey key=new NamespacedKey(this,"underlying_dynamic"); Bukkit.removeRecipe(key);
                if(registered) {
                    var recipe=new ShapelessRecipe(key,new ItemStack(Material.DIAMOND));
                    recipe.addIngredient(repair?Material.FLINT:Material.PAPER); recipe.addIngredient(Material.CARROT_ON_A_STICK); Bukkit.addRecipe(recipe);
                }
                player.openWorkbench(new Location(player.getWorld(),0,90,0),true); ((CraftingMenu)actor.containerMenu).checkReachable=false;
                var inv=(CraftingInventory)player.getOpenInventory().getTopInventory();
                var scroll=repair?garden.relics().createRepairStone(3):garden.relics().createScrollEternity(); scroll.setAmount(3);
                ItemStack[] matrix=new ItemStack[9]; matrix[0]=scroll; matrix[1]=magic.wands().create(Spell.HEAVENS_JUDGMENT);
                if(repair) {var meta=(org.bukkit.inventory.meta.Damageable)matrix[1].getItemMeta();meta.setDamage(15);matrix[1].setItemMeta(meta);}
                inv.setMatrix(matrix);
                check(magic.wands().spell(inv.getResult())==Spell.HEAVENS_JUDGMENT&&(repair||garden.relics().isEternityItem(inv.getResult())),"server prepares tagged upgraded wand");
                ItemStack expected=inv.getResult().clone();
                if(occupied)player.setItemOnCursor(new ItemStack(Material.STONE,64));
                packet(0,action==ContainerInput.CLONE?2:0,action);
                int leftScroll=Arrays.stream(inv.getMatrix()).filter(i->repair?garden.relics().isRepairStone(i):garden.relics().isScrollEternity(i)).mapToInt(ItemStack::getAmount).sum();
                int leftWands=Arrays.stream(inv.getMatrix()).filter(i->magic.wands().spell(i)!=null).mapToInt(ItemStack::getAmount).sum();
                getLogger().info("DYNAMIC OUTCOME action="+action+" occupied="+occupied+" output="+wands()+" matrix-wands="+leftWands+" matrix-scrolls="+leftScroll+" recipe="+inv.getRecipe());
                if(Boolean.getBoolean("wand.inventory.baseline")) {
                    boolean consumed=registered?action!=ContainerInput.QUICK_MOVE:action==ContainerInput.PICKUP||action==ContainerInput.CLONE||action==ContainerInput.PICKUP_ALL;
                    check(wands()==0&&leftWands==(consumed?0:1)&&leftScroll==(consumed?2:3),"baseline reproduces vanished inputs or blocked transaction");
                } else {
                    boolean success=action==ContainerInput.PICKUP&&!occupied;
                    check(wands()+leftWands==1,"wand conservation: neither duplication nor loss");
                    check(wands()==(success?1:0)&&leftWands==(success?0:1)&&leftScroll==(success?2:3),"exact output and catalyst payment; unsupported or occupied actions preserve inputs");
                    if(success)check(player.getItemOnCursor().isSimilar(expected)&&player.getItemOnCursor().getAmount()==1,"delivered item preserves exact expected type, PDC, upgrades and mastery");
                    ItemStack[] after=inv.getMatrix(); packet(0,0,ContainerInput.PICKUP);
                    check(wands()+Arrays.stream(inv.getMatrix()).filter(i->magic.wands().spell(i)!=null).mapToInt(ItemStack::getAmount).sum()==1,"repeated result click cannot duplicate or delete wand");
                    if(success)check(Arrays.equals(after,inv.getMatrix()),"stale result replay cannot consume another catalyst");
                }
            }
        }
        if(!Boolean.getBoolean("wand.inventory.baseline")) for(boolean registered:List.of(false,true)) {
            scenario="dynamic cancellation registered="+registered; reset();
            NamespacedKey key=new NamespacedKey(this,"underlying_dynamic"); Bukkit.removeRecipe(key);
            if(registered){var recipe=new ShapelessRecipe(key,new ItemStack(Material.DIAMOND));recipe.addIngredient(Material.PAPER);recipe.addIngredient(Material.CARROT_ON_A_STICK);Bukkit.addRecipe(recipe);}
            player.openWorkbench(new Location(player.getWorld(),0,90,0),true); ((CraftingMenu)actor.containerMenu).checkReachable=false;
            var dynamicInv=(CraftingInventory)player.getOpenInventory().getTopInventory(); ItemStack[] inputs=new ItemStack[9];
            inputs[0]=garden.relics().createScrollEternity();inputs[1]=magic.wands().create(Spell.HEAVENS_JUDGMENT);dynamicInv.setMatrix(inputs);
            ItemStack[] before=dynamicInv.getMatrix(); forceCancellation=true; packet(0,0,ContainerInput.PICKUP); forceCancellation=false;
            check(Arrays.equals(before,dynamicInv.getMatrix())&&wands()==0,"earlier protection cancellation preserves all inputs and cursor");
            scenario="dynamic stale preview registered="+registered;
            dynamicInv.setResult(new ItemStack(Material.DIAMOND));packet(0,0,ContainerInput.PICKUP);
            check(Arrays.equals(before,dynamicInv.getMatrix())&&wands()==0&&player.getItemOnCursor().getType().isAir(),"mismatching result preview cannot consume or transfer inputs");
        }
        Bukkit.removeRecipe(new NamespacedKey(this,"underlying_dynamic"));
        scenario="registered Evergarden key"; reset(); player.openWorkbench(new Location(player.getWorld(),0,90,0),true); ((CraftingMenu)actor.containerMenu).checkReachable=false;
        var inv=(CraftingInventory)player.getOpenInventory().getTopInventory(); ItemStack[] matrix=new ItemStack[9];
        for(int slot:List.of(0,1,3,4))matrix[slot]=garden.relics().createKeyShard(3); inv.setMatrix(matrix);
        check(garden.relics().isVoidKey(inv.getResult()),"registered key recipe prepares output");
        packet(0,0,ContainerInput.QUICK_MOVE);
        int keys=Arrays.stream(player.getInventory().getContents()).filter(garden.relics()::isVoidKey).mapToInt(ItemStack::getAmount).sum();
        int shards=Arrays.stream(inv.getMatrix()).filter(garden.relics()::isKeyShard).mapToInt(ItemStack::getAmount).sum();
        check(keys==3&&shards==0,"native shift craft consumes exactly four shards per key without double consumption");
    }
    void cleanup() {
        if(actor==null)return;
        player.closeInventory(); var server=MinecraftServer.getServer(); server.getPlayerList().getPlayers().remove(actor); server.getPlayerList().getPlayersByUUID().remove(actor.getUUID()); actor.discard();
    }
}
