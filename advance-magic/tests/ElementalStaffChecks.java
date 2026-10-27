import com.example.advancemagic.AdvanceMagicPlugin;
import com.example.advancemagic.api.MagicCastEvent;
import com.example.advancemagic.item.ElementalStaffService;
import com.example.advancemagic.item.ElementalStaffService.Element;
import com.example.advancemagic.item.WandService;
import com.example.advancemagic.spell.Spell;
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
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import java.nio.file.*;
import java.util.*;

/** Real Paper regression probe for the model-first migration, commands and retired inputs. */
public final class ElementalStaffChecks extends JavaPlugin implements Listener {
    static final class Capture extends Connection {
        Capture(){super(PacketFlow.SERVERBOUND);channel=new EmbeddedChannel();address=new java.net.InetSocketAddress("127.0.0.1",1);}
        @Override public void send(Packet<?> p){}
        @Override public void send(Packet<?> p,ChannelFutureListener l){}
        @Override public void send(Packet<?> p,ChannelFutureListener l,boolean flush){}
        @Override public boolean isConnected(){return true;}
    }
    int passed,castEvents;
    final List<ServerPlayer> actors=new ArrayList<>();
    AdvanceMagicPlugin magic;
    World world;
    @Override public void onEnable(){Bukkit.getPluginManager().registerEvents(this,this);Bukkit.getScheduler().runTaskLater(this,()->{try{run();finish(null);}catch(Throwable failure){finish(failure);}},20);}
    @EventHandler public void cast(MagicCastEvent event){castEvents++;}
    void check(boolean condition,String label){if(!condition)throw new AssertionError(label);passed++;getLogger().info("PASS "+label);}
    Player actor(String name,boolean op){
        var server=MinecraftServer.getServer();var level=((CraftWorld)world).getHandle();
        var profile=new GameProfile(UUID.randomUUID(),name);var handle=new ServerPlayer(server,level,profile,ClientInformation.createDefault());
        handle.connection=new ServerGamePacketListenerImpl(server,new Capture(),handle,CommonListenerCookie.createInitial(profile,false)){
            @Override public boolean hasClientLoaded(){return true;}
        };
        handle.setPos(.5,100,.5);server.getPlayerList().getPlayers().add(handle);server.getPlayerList().getPlayersByUUID().put(profile.id(),handle);level.addNewPlayer(handle);actors.add(handle);
        Player player=handle.getBukkitEntity();player.setOp(op);player.setGameMode(GameMode.SURVIVAL);player.setGravity(false);return player;
    }
    ItemStack legacy(Spell spell){
        ItemStack item=new ItemStack(WandService.BASE);var meta=item.getItemMeta();
        meta.setDisplayName(ChatColor.LIGHT_PURPLE+spell.title+" Wand");meta.setLore(List.of("original lore"));
        meta.getPersistentDataContainer().set(new NamespacedKey("advance-magic","wand"),PersistentDataType.STRING,spell.id());
        meta.getPersistentDataContainer().set(new NamespacedKey("advance_magic","wand"),PersistentDataType.STRING,spell.id());
        meta.setItemModel(new NamespacedKey("advance_magic",spell.id()));item.setItemMeta(meta);return item;
    }
    int count(Player player){return Arrays.stream(player.getInventory().getContents()).filter(Objects::nonNull).filter(magic.staffs()::isStaff).mapToInt(ItemStack::getAmount).sum();}
    void click(Player player,int slot){Bukkit.getPluginManager().callEvent(new InventoryClickEvent(player.getOpenInventory(),InventoryType.SlotType.CONTAINER,slot,ClickType.LEFT,InventoryAction.PICKUP_ALL));}
    void run() throws Exception {
        magic=(AdvanceMagicPlugin)Bukkit.getPluginManager().getPlugin("advance-magic");
        check(magic!=null&&magic.isEnabled(),"plugin starts on Paper");world=Bukkit.getWorlds().getFirst();world.getChunkAt(0,0).setForceLoaded(true);
        Player admin=actor("StaffAdmin",true),guest=actor("StaffGuest",false);
        check(Element.values().length==8,"exactly eight elemental staff identities");
        Set<NamespacedKey> models=new HashSet<>();
        for(Element element:Element.values()){
            ItemStack item=magic.staffs().create(element);models.add(item.getItemMeta().getItemModel());
            check(magic.staffs().element(item)==element,"server identity "+element.id());
            check(item.getItemMeta().getItemModel().toString().equals("advance_magic:staff_"+element.id()),"model ID "+element.id());
            check(item.getItemMeta().getCustomModelDataComponent().getStrings().equals(List.of(element.model().toString())),"Geyser string selector "+element.id());
            check(magic.wands().spell(item)==null,"new staff has no assigned legacy spell "+element.id());
            check(magic.staffs().element(ItemStack.deserializeBytes(item.serializeAsBytes()))==element,"saved roundtrip "+element.id());
            check(item.getMaxStackSize()==1,"single staff maximum stack "+element.id());
        }
        check(models.size()==8,"all models unique");
        Element cursor=Element.FIRE;for(int i=0;i<5;i++)cursor=cursor.next();check(cursor==Element.FIRE,"five-element cycle closes");
        cursor=Element.LIGHTNING;for(int i=0;i<3;i++)cursor=cursor.next();check(cursor==Element.LIGHTNING,"special-element cycle closes");
        check(Element.WATER.next()==Element.FIRE&&Element.DARK.next()==Element.LIGHTNING,"cycles do not leak into each other");
        var owner=new NamespacedKey(this,"soulbound_owner");
        for(Spell spell:Spell.values()){
            var item=legacy(spell);var meta=item.getItemMeta();
            meta.getPersistentDataContainer().set(owner,PersistentDataType.STRING,"owner-123");
            meta.getPersistentDataContainer().set(new NamespacedKey("advance-magic","casts"),PersistentDataType.INTEGER,27);
            meta.getPersistentDataContainer().set(new NamespacedKey("advance-magic","durability"),PersistentDataType.INTEGER,9);
            meta.getPersistentDataContainer().set(new NamespacedKey("advance-magic","damage_level"),PersistentDataType.INTEGER,4);
            meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING,3,true);item.setItemMeta(meta);
            check(magic.wands().migrate(item),"legacy migrates "+spell.id());
            check(magic.staffs().element(item)==ElementalStaffService.fromLegacy(spell),"legacy element mapping "+spell.id());
            check(magic.wands().spell(item)==spell,"integration identity remains "+spell.id());
            check(magic.wands().casts(item)==27&&magic.wands().usesLeft(item)==9&&magic.wands().damageLevel(item)==4,"mastery durability upgrades preserved "+spell.id());
            check("owner-123".equals(item.getItemMeta().getPersistentDataContainer().get(owner,PersistentDataType.STRING))&&item.getEnchantmentLevel(org.bukkit.enchantments.Enchantment.UNBREAKING)==3,"foreign metadata and enchantments preserved "+spell.id());
            check(!magic.wands().migrate(item),"migration idempotent "+spell.id());
        }
        var renamed=legacy(Spell.FROST_NOVA);var meta=renamed.getItemMeta();meta.setDisplayName("My personal staff");renamed.setItemMeta(meta);magic.staffs().migrate(renamed);
        check(renamed.getItemMeta().getDisplayName().equals("My personal staff"),"player-assigned name survives");
        check(renamed.getItemMeta().getLore().contains("original lore"),"unrelated custom lore survives in the active item");
        check(renamed.getItemMeta().getPersistentDataContainer().get(new NamespacedKey("advance_magic","retired_wand_lore"),PersistentDataType.STRING).equals("original lore"),"old lore archived for recovery");
        var vanilla=new ItemStack(WandService.BASE);check(!magic.wands().migrate(vanilla)&&!vanilla.hasItemMeta(),"vanilla rod unchanged");
        var conflict=legacy(Spell.FROST_NOVA);meta=conflict.getItemMeta();meta.getPersistentDataContainer().set(new NamespacedKey("advance_magic","wand"),PersistentDataType.STRING,Spell.EARTH_WALL.id());conflict.setItemMeta(meta);
        check(!magic.staffs().migrate(conflict)&&magic.staffs().element(conflict)==null,"contradictory legacy tags rejected");
        var bad=magic.staffs().create(Element.FIRE);meta=bad.getItemMeta();meta.setItemModel(Element.ICE.model());bad.setItemMeta(meta);check(!magic.staffs().isStaff(bad),"contradictory model rejected");
        for(var weapon:com.example.advancemagic.item.LegendaryWeaponService.Weapon.values()){
            var item=magic.legendaryWeapons().create(weapon);check(!magic.wands().migrate(item)&&magic.legendaryWeapons().identify(item)==weapon,"legendary weapon unchanged "+weapon);
        }
        var flight=magic.flyingStaff().create();var flightCopy=flight.clone();magic.wands().migrate(flight);
        check(magic.staffs().element(flight)==null&&flight.equals(flightCopy),"flying staff remains separate");
        admin.getInventory().setItem(3,legacy(Spell.SHADOW_STEP));Bukkit.getPluginManager().callEvent(new PlayerItemHeldEvent(admin,0,3));
        check(magic.staffs().element(admin.getInventory().getItem(3))==Element.DARK,"hotbar selection migrates stored wand");admin.getInventory().clear();
        Bukkit.dispatchCommand(guest,"magic staff give fire");check(count(guest)==0,"give permission enforced");
        Bukkit.dispatchCommand(admin,"magic staff give all");check(count(admin)==8,"admin gives exactly eight staffs");admin.getInventory().clear();
        Bukkit.dispatchCommand(admin,"magic staff give water StaffGuest");check(count(guest)==1&&magic.staffs().element(guest.getInventory().getItem(0))==Element.WATER,"explicit recipient");guest.getInventory().clear();
        Bukkit.dispatchCommand(admin,"magic give frost_nova");Bukkit.dispatchCommand(admin,"magic givecore frost_nova");Bukkit.dispatchCommand(admin,"magic craft");
        check(admin.getInventory().isEmpty(),"retired give/core/craft commands issue no items");
        Bukkit.dispatchCommand(admin,"magic staffs");var menu=admin.getOpenInventory().getTopInventory();
        check(menu.getSize()==27,"new staff preview menu");
        for(Element e:Element.values())check(magic.staffs().element(menu.getItem(e.ordinal()))==e,"preview slot "+e.id());
        check(Arrays.stream(menu.getContents()).filter(Objects::nonNull).noneMatch(i->magic.wands().spell(i)!=null||magic.wands().coreSpell(i)!=null),"preview has no old offensive wands or cores");
        click(admin,0);check(count(admin)==1,"admin preview grants selected elemental staff");admin.closeInventory();admin.getInventory().clear();
        Bukkit.dispatchCommand(guest,"magic staffs");click(guest,0);check(guest.getInventory().isEmpty(),"public preview grants nothing");guest.closeInventory();
        for(Spell spell:Spell.values())check(Bukkit.getRecipe(new NamespacedKey(magic,spell.id()+"_ni_c"))==null,"retired recipe removed "+spell.id());
        ItemStack[] ingredients=new ItemStack[9];for(int i=0;i<9;i++)ingredients[i]=i==4?magic.wands().createCore(Spell.FROST_NOVA):new ItemStack(Material.NETHERITE_INGOT);
        var result=Bukkit.craftItemResult(ingredients,world,admin);check(result.getResult().getType().isAir(),"actual crafting grid cannot make old wand");
        var repairA=magic.staffs().create(Element.ICE);var repairB=magic.staffs().create(Element.WATER);
        for(ItemStack item:List.of(repairA,repairB)){var damage=(org.bukkit.inventory.meta.Damageable)item.getItemMeta();damage.setDamage(20);item.setItemMeta(damage);}
        ItemStack[] repairGrid=new ItemStack[9];repairGrid[0]=repairA;repairGrid[1]=repairB;
        check(Bukkit.craftItemResult(repairGrid,world,admin).getResult().getType().isAir(),"vanilla repair cannot consume staffs and lose custom identity");
        admin.getInventory().clear();admin.getInventory().setItem(0,magic.wands().createCore(Spell.FROST_NOVA));admin.getInventory().setItem(1,new ItemStack(Material.NETHERITE_INGOT,8));
        var before=Arrays.stream(admin.getInventory().getContents()).map(i->i==null?null:i.clone()).toArray(ItemStack[]::new);
        check(magic.itemMenu().craft(admin,Spell.FROST_NOVA)!=null&&Arrays.equals(before,admin.getInventory().getContents()),"retired menu craft rejects without consuming materials");admin.getInventory().clear();
        for(ItemStack staff:List.of(magic.staffs().create(Element.FIRE),legacy(Spell.LIGHTNING_STRIKE))){
            admin.getInventory().setItemInMainHand(staff);double mana=magic.mana().account(admin).manaExact();int countBefore=castEvents;
            var event=new PlayerInteractEvent(admin,Action.RIGHT_CLICK_AIR,staff,null,BlockFace.SELF,EquipmentSlot.HAND);Bukkit.getPluginManager().callEvent(event);
            Bukkit.getPluginManager().callEvent(new PlayerAnimationEvent(admin,PlayerAnimationType.ARM_SWING));
            Bukkit.getPluginManager().callEvent(new PlayerSwapHandItemsEvent(admin,admin.getInventory().getItemInOffHand(),admin.getInventory().getItemInMainHand()));
            check(event.isCancelled()&&castEvents==countBefore&&magic.mana().account(admin).manaExact()==mana,"staff inputs cast nothing and charge no mana");
            check(!magic.casts().castInput(admin,Spell.LIGHTNING_STRIKE,staff)&&castEvents==countBefore,"input API cannot bypass retirement");
            check(!magic.casts().cast(admin,Spell.LIGHTNING_STRIKE,staff)&&castEvents==countBefore,"item-backed direct cast cannot bypass retirement");
            check(!magic.casts().castInput(admin,Spell.LIGHTNING_STRIKE,null)&&castEvents==countBefore,"input API cannot bypass retirement with an empty item");
            var thorns=new org.bukkit.event.entity.EntityDamageByEntityEvent(admin,guest,org.bukkit.event.entity.EntityDamageEvent.DamageCause.THORNS,2);
            magic.casts().onAttackEntity(thorns);check(!thorns.isCancelled(),"holding a staff does not cancel unrelated Thorns damage");
        }
        var garden=Bukkit.getPluginManager().getPlugin("Evergarden");
        if(garden!=null&&garden.isEnabled()){
            var loot=Class.forName("com.example.voidscape.item.VaultLootTable");var active=loot.getMethod("activeReward",int.class);
            for(int i=0;i<9250;i++){String reward=active.invoke(null,i).toString();if(reward.equals("CORE")||reward.equals("MYTHIC_CORE"))throw new AssertionError("retired core ticket "+i);}
            check(true,"all 9250 active Vault tickets exclude retired cores");
            admin.getInventory().clear();Bukkit.dispatchCommand(admin,"evergarden give core_frost_nova");check(admin.getInventory().isEmpty(),"Evergarden old give alias retired");
            Bukkit.dispatchCommand(admin,"evergarden wands");check(admin.getOpenInventory().getTopInventory().getSize()==27,"Evergarden showcase delegates new staffs");admin.closeInventory();
        }
    }
    void finish(Throwable failure){
        if(failure!=null)getLogger().log(java.util.logging.Level.SEVERE,"Elemental staff probe failed",failure);
        try{Files.writeString(Path.of("elemental-staff-result.txt"),(failure==null?"PASS "+passed:"FAIL "+failure)+"\n");}catch(Exception ignored){}
        for(ServerPlayer actor:actors){actor.getBukkitEntity().setOp(false);MinecraftServer.getServer().getPlayerList().getPlayers().remove(actor);MinecraftServer.getServer().getPlayerList().getPlayersByUUID().remove(actor.getUUID());actor.discard();}
        Bukkit.getScheduler().runTaskLater(this,Bukkit::shutdown,2);
    }
}
