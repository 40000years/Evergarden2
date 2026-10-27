import com.example.advancemagic.AdvanceMagicPlugin;
import com.example.advancemagic.elemental.*;
import com.example.advancemagic.elemental.StaffGemService.Gem;
import com.example.advancemagic.elemental.StaffSpellService.Tier;
import com.example.advancemagic.item.ElementalStaffService.Element;
import com.example.voidscape.VoidscapePlugin;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.server.network.*;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import java.nio.file.*;
import java.util.*;

/** Physical inventory conservation, native GUI events, real casts and actual structure loot. */
public final class StaffGemChecks extends JavaPlugin implements Listener {
    AdvanceMagicPlugin magic;StaffSpellService spells;StaffGemService gems;Player player;ServerPlayer actor;World world;
    int passed;double damage;final List<LivingEntity> mobs=new ArrayList<>();
    void check(boolean ok,String label){if(!ok)throw new AssertionError(label);passed++;}
    void near(double value,double expected,String label){check(Math.abs(value-expected)<.0001,label+" got "+value+" expected "+expected);}
    @Override public void onEnable(){Bukkit.getPluginManager().registerEvents(this,this);later(()->{init();factories();transfers();capacity();casting();loot();gui();});}
    void later(Runnable step){Bukkit.getScheduler().runTaskLater(this,()->{try{step.run();}catch(Throwable error){finish(error);}},3);}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void hit(EntityDamageByEntityEvent event){if(mobs.contains(event.getEntity()))damage+=event.getDamage();}
    void init(){
        magic=(AdvanceMagicPlugin)Bukkit.getPluginManager().getPlugin("advance-magic");check(magic!=null&&magic.isEnabled(),"Magic enabled");
        spells=magic.staffSpells();gems=spells.gems();world=Bukkit.getWorlds().getFirst();
        for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)world.getChunkAt(x,z).setForceLoaded(true);
        for(int x=-8;x<=8;x++)for(int z=-8;z<=8;z++)world.getBlockAt(x,99,z).setType(Material.STONE);
        var server=MinecraftServer.getServer();var level=((CraftWorld)world).getHandle();var profile=new GameProfile(UUID.randomUUID(),"GemTester");
        actor=new ServerPlayer(server,level,profile,ClientInformation.createDefault());var conn=new Connection(PacketFlow.SERVERBOUND);
        conn.channel=new EmbeddedChannel();conn.address=new java.net.InetSocketAddress("127.0.0.1",1);
        actor.connection=new ServerGamePacketListenerImpl(server,conn,actor,CommonListenerCookie.createInitial(profile,false)){@Override public boolean hasClientLoaded(){return true;}};
        actor.setPos(.5,100,.5);server.getPlayerList().getPlayers().add(actor);server.getPlayerList().getPlayersByUUID().put(profile.id(),actor);level.addNewPlayer(actor);
        player=actor.getBukkitEntity();player.setGameMode(GameMode.SURVIVAL);player.setGravity(false);
    }
    ItemStack held(){return player.getInventory().getItemInMainHand();}
    void reset(){
        player.closeInventory();player.getInventory().clear();player.getInventory().setHeldItemSlot(0);
        magic.elemental().close();magic.effects().close();for(var mob:mobs)mob.remove();mobs.clear();damage=0;
        var account=magic.mana().account(player);account.setMaxMana(200);account.setMana(200);
        for(var spell:ElementalSpell.values())account.restore("elemental_"+spell.id(),0);
        for(String key:List.of("elemental_gcd","elemental_ultimate","elemental_regen_after"))player.getPersistentDataContainer().remove(magic.mana().key(key));
    }
    void staff(Tier tier,Element element){reset();player.getInventory().setItem(0,tier.wooden()?spells.createWoodenStaff(tier):spells.createStaff(element,tier));}
    void install(Gem gem,int socket){
        player.getInventory().setItem(9,StaffGemService.create(gem));
        check(gems.insert(player,0,held().clone(),9,player.getInventory().getItem(9).clone(),socket).success(),"insert "+gem+" socket "+socket);
    }
    int inventoryGems(){int n=0;for(var item:player.getInventory().getStorageContents())if(StaffGemService.identify(item)!=null)n+=item.getAmount();return n;}
    void factories(){
        for(Gem gem:Gem.values()){
            var item=StaffGemService.create(gem);check(StaffGemService.identify(item)==gem,"factory identity");
            check(item.getItemMeta().getItemModel().equals(new NamespacedKey("advance_magic",gem.model)),"existing native Gem model retained");
            check(StaffGemService.identify(new ItemStack(gem.material))==null,"plain ingredients never count as gems");
        }
        for(Tier tier:Tier.values())for(Element element:tier.wooden()?new Element[]{Element.FIRE}:Element.values())for(Gem a:Gem.values())for(Gem b:Gem.values()){
            staff(tier,element);var before=held().getItemMeta().getItemModel();install(a,0);install(b,1);
            check(StaffGemService.sockets(held())[0]==a&&StaffGemService.sockets(held())[1]==b,"two independent duplicate-capable sockets");
            near(magic.wands().damageMultiplier(held()),1+.15*((a==Gem.DAMAGE?1:0)+(b==Gem.DAMAGE?1:0)),"damage adds per socket");
            near(magic.wands().cooldownScale(held()),1-.15*((a==Gem.COOLDOWN?1:0)+(b==Gem.COOLDOWN?1:0)),"cooldown adds per socket");
            check(magic.wands().maxUses(held())==300+250*((a==Gem.MAX?1:0)+(b==Gem.MAX?1:0)),"Max capacity adds per socket");
            check(magic.wands().usesLeft(held())==300,"socket operations do not mint repair uses");
            var restored=ItemStack.deserializeBytes(held().serializeAsBytes());
            check(Arrays.equals(StaffGemService.sockets(restored),StaffGemService.sockets(held())),"physical sockets survive item serialization");
            check(before.equals(held().getItemMeta().getItemModel())&&spells.tier(held())==tier,"socketing retains staff model and tier");
            check(gems.remove(player,0,held().clone(),0).success()&&gems.remove(player,0,held().clone(),1).success(),"both physical gems return");
            check(inventoryGems()==2,"no loss through insert/remove cycle");
        }
    }
    void remaining(int uses){var meta=held().getItemMeta();meta.getPersistentDataContainer().set(new NamespacedKey(magic,"durability"),PersistentDataType.INTEGER,uses);held().setItemMeta(meta);}
    void capacity(){
        staff(Tier.MYTHIC,Element.FIRE);remaining(25);install(Gem.MAX,0);
        check(magic.wands().maxUses(held())==550&&magic.wands().usesLeft(held())==25,"Max Gem increases capacity without repairing");
        install(Gem.MAX,1);check(magic.wands().maxUses(held())==800&&magic.wands().usesLeft(held())==25,"two Max Gems stack without repairing");
        check(magic.wands().restore(held())&&magic.wands().usesLeft(held())==800,"existing Restoration fills extended capacity");
        check(gems.remove(player,0,held().clone(),0).success(),"remove one Max Gem");
        check(magic.wands().maxUses(held())==550&&magic.wands().usesLeft(held())==550,"removal clamps remaining uses to new capacity");
        install(Gem.MAX,0);check(magic.wands().maxUses(held())==800&&magic.wands().usesLeft(held())==550,"reinsertion cannot recover clamped uses");
        for(int i=0;i<3;i++){
            check(gems.remove(player,0,held().clone(),0).success(),"repeat remove Max Gem");install(Gem.MAX,0);
            check(magic.wands().usesLeft(held())==550,"repeat swapping never creates free repairs");
        }
        install(Gem.DAMAGE,0);check(magic.wands().maxUses(held())==550&&magic.wands().usesLeft(held())==550,"mixed Damage/Max build");
        install(Gem.COOLDOWN,1);check(magic.wands().maxUses(held())==300&&magic.wands().usesLeft(held())==300,"replacing last Max clamps to base");
        remaining(0);install(Gem.MAX,0);check(magic.wands().usesLeft(held())==0,"depleted staff remains depleted after inserting Max");
        check(gems.remove(player,0,held().clone(),0).success()&&magic.wands().usesLeft(held())==0,"removing Max from depleted staff cannot repair it");
        var oldCore=legacy(Gem.MAX);check(StaffGemService.identify(oldCore)==Gem.MAX,"old Repair Core accepted as Max Gem");
        check(magic.wands().upgrade(StaffGemService.create(Gem.MAX))==com.example.advancemagic.item.WandService.Upgrade.DURABILITY,"Max Gem retains flight repair upgrade compatibility");
        try{
            var method=magic.wands().getClass().getDeclaredMethod("upgraded",ItemStack.class,ItemStack.class);method.setAccessible(true);
            var flight=(ItemStack)method.invoke(magic.wands(),magic.flyingStaff().create(),StaffGemService.create(Gem.MAX));
            check(flight!=null&&magic.wands().maxUses(flight)==35,"Max Gem increases flying staff capacity by its legacy +5");
        }catch(Exception error){throw new RuntimeException(error);}
        staff(Tier.MYTHIC,Element.FIRE);install(Gem.MAX,0);remaining(500);
        for(int slot=1;slot<36;slot++)player.getInventory().setItem(slot,new ItemStack(Material.COBBLESTONE,64));
        var snapshot=held().clone();check(!gems.remove(player,0,snapshot,0).success()&&held().equals(snapshot),"blocked Max extraction changes neither capacity nor remaining uses");
        var saved=ItemStack.deserializeBytes(held().serializeAsBytes());check(magic.wands().maxUses(saved)==550&&magic.wands().usesLeft(saved)==500,"capacity and remaining uses persist together");
    }
    ItemStack legacy(Gem gem){var item=StaffGemService.create(gem);var meta=item.getItemMeta();meta.getPersistentDataContainer().remove(new NamespacedKey("advance_magic","staff_gem"));meta.getPersistentDataContainer().set(new NamespacedKey("advance_magic","wand_upgrade"),PersistentDataType.STRING,gem.model);item.setItemMeta(meta);return item;}
    void transfers(){
        staff(Tier.MYTHIC,Element.FIRE);install(Gem.DAMAGE,0);
        for(int slot=1;slot<36;slot++)player.getInventory().setItem(slot,new ItemStack(Material.COBBLESTONE,64));
        var before=held().clone();check(!gems.remove(player,0,before,0).success()&&held().equals(before),"full inventory blocks extraction without loss");
        var pair=StaffGemService.create(Gem.COOLDOWN);pair.setAmount(2);player.getInventory().setItem(9,pair);
        check(!gems.insert(player,0,held().clone(),9,pair.clone(),0).success()&&player.getInventory().getItem(9).getAmount()==2&&held().equals(before),"full inventory replacement preflights returned gem");
        pair.setAmount(1);player.getInventory().setItem(9,pair);
        check(gems.insert(player,0,held().clone(),9,pair.clone(),0).success(),"replacement can return old gem in consumed input slot");
        check(StaffGemService.identify(player.getInventory().getItem(9))==Gem.DAMAGE,"replacement returned old Damage Gem");
        var stale=held().clone();player.getInventory().setItem(10,null);
        check(gems.remove(player,0,stale,0).success(),"extract to free inventory slot");
        check(!gems.remove(player,0,stale,0).success(),"replayed snapshot cannot duplicate extraction");
        reset();player.getInventory().setItem(0,spells.createStaff(Element.FIRE,Tier.MYTHIC));player.getInventory().setItem(9,legacy(Gem.DAMAGE));
        check(gems.insert(player,0,held().clone(),9,player.getInventory().getItem(9).clone(),0).success(),"old loose core can be socketed as one Gem");
        var old=held().clone();var meta=old.getItemMeta();for(String key:List.of("damage_level","cooldown_level","durability_level"))meta.getPersistentDataContainer().set(new NamespacedKey(magic,key),PersistentDataType.INTEGER,10);old.setItemMeta(meta);
        near(magic.wands().damageMultiplier(old),1.15,"old permanent damage ranks do not stack with gems");near(magic.wands().cooldownScale(old),1,"old permanent cooldown ranks ignored");
        check(magic.wands().maxUses(old)==300,"old max ranks no longer add a third staff upgrade path");
        player.getInventory().setItem(0,old);player.getInventory().setItem(9,StaffGemService.create(Gem.COOLDOWN));var changed=StaffGemService.create(Gem.DAMAGE);
        check(!gems.insert(player,0,held().clone(),9,changed,1).success(),"changed source snapshot rejected");
        check(!gems.insert(player,0,held().clone(),9,player.getInventory().getItem(9).clone(),2).success(),"no third socket");
        var flight=magic.flyingStaff().create();player.getInventory().setItem(0,flight);
        check(!gems.insert(player,0,flight.clone(),9,player.getInventory().getItem(9).clone(),0).success(),"flight staff has no combat Gem sockets");
        near(magic.wands().damageMultiplier(flight),1,"flying staff behavior retained");
        staff(Tier.APPRENTICE,Element.FIRE);magic.wands().migrate(held());check(magic.wands().usesLeft(held())==300,"fresh staff migration initializes correct 300 uses, not legacy 30");
    }
    void casting(){
        for(int level:new int[]{1,5,7})for(int mode=0;mode<4;mode++){
            staff(Tier.MYTHIC,Element.FIRE);
            player.getInventory().setItem(1,spells.createScroll(ElementalSpell.FLAME_BURST,level));
            check(spells.imprint(player,0,held().clone(),1,player.getInventory().getItem(1).clone(),0).success(),"spell imprint before Gem cast");
            if(mode==1){install(Gem.DAMAGE,0);install(Gem.DAMAGE,1);}
            if(mode==2){install(Gem.COOLDOWN,0);install(Gem.COOLDOWN,1);}
            if(mode==3){install(Gem.DAMAGE,0);install(Gem.COOLDOWN,1);}
            var mob=world.spawn(new Location(world,2.5,100,.5),Vindicator.class);mob.setAI(false);mob.setGravity(false);mob.getAttribute(Attribute.MAX_HEALTH).setBaseValue(1000);mob.setHealth(1000);mobs.add(mob);
            int uses=magic.wands().usesLeft(held());
            check(magic.elemental().cast(player),"actual Paper cast accepts Gems build "+mode);
            double scale=mode==1?1.3:mode==3?1.15:1;
            near(damage,18*SpellLevels.damage(level)*scale,"real Flame Burst initial hit uses socket bonus and spell level "+level);
            long remaining=magic.mana().elementalRemaining(player,ElementalSpell.FLAME_BURST,System.currentTimeMillis());
            double cd=8000*SpellLevels.cooldown(level)*(mode==2?.7:mode==3?.85:1);
            check(Math.abs(remaining-cd)<350,"real cooldown reservation uses socket bonus");
            check(magic.wands().usesLeft(held())==uses-1,"Gem casting still spends one use");
            check(StaffGemService.count(held(),Gem.DAMAGE)==(mode==1?2:mode==3?1:0),"cast preserves installed Gems");
        }
    }
    void loot(){
        try{
            var garden=(VoidscapePlugin)Bukkit.getPluginManager().getPlugin("Evergarden");check(garden!=null&&garden.isEnabled(),"paired Evergarden enabled");
            var treasure=new com.example.voidscape.world.WhaleTreasure(garden);var factory=treasure.getClass().getDeclaredMethod("createWandUpgrade",Random.class);factory.setAccessible(true);
            for(int choice=0;choice<3;choice++){
                final int selected=choice;ItemStack item=(ItemStack)factory.invoke(treasure,new Random(){@Override public int nextInt(int bound){check(bound==3,"three loot outcomes retained");return selected;}});
                check(StaffGemService.identify(item)==(choice==0?Gem.MAX:choice==1?Gem.DAMAGE:Gem.COOLDOWN),"structure loot gives each canonical removable Gem");
            }
        }catch(Exception error){throw new RuntimeException(error);}
    }
    void click(int raw){
        var event=new InventoryClickEvent(player.getOpenInventory(),InventoryType.SlotType.CONTAINER,raw,ClickType.LEFT,InventoryAction.PICKUP_ALL);
        Bukkit.getPluginManager().callEvent(event);check(event.isCancelled(),"GUI owns and cancels native inventory click");
    }
    void gui(){
        staff(Tier.MYTHIC,Element.FIRE);var pair=StaffGemService.create(Gem.DAMAGE);pair.setAmount(2);player.getInventory().setItem(9,pair);
        magic.getCommand("spell").execute(player,"spell",new String[]{});var top=player.getOpenInventory().getTopInventory();
        check(top.getItem(18).getItemMeta().getDisplayName().contains("Gem ช่อง 1")&&top.getItem(27).getItemMeta().getDisplayName().contains("Gem ช่อง 2"),"/spell shows both sockets");
        check(StaffGemService.identify(top.getItem(37))==null,"Gem preview has no usable Gem identity");click(37);
        later(()->{click(18);later(()->{
            check(StaffGemService.count(held(),Gem.DAMAGE)==1&&inventoryGems()==1,"GUI installs one physical Gem");click(37);
            later(()->{click(27);later(()->{
                check(StaffGemService.count(held(),Gem.DAMAGE)==2&&inventoryGems()==0,"GUI supports two Damage Gems");click(18);
                later(()->{
                    check(StaffGemService.count(held(),Gem.DAMAGE)==1&&inventoryGems()==1,"GUI extracts and returns Gem");
                    var view=player.getOpenInventory();var blocked=new InventoryClickEvent(view,InventoryType.SlotType.CONTAINER,27,ClickType.NUMBER_KEY,InventoryAction.HOTBAR_SWAP,1);
                    Bukkit.getPluginManager().callEvent(blocked);check(blocked.isCancelled()&&StaffGemService.count(held(),Gem.DAMAGE)==1,"hotbar swaps cannot steal socket icon");
                    player.getInventory().setItem(10,StaffGemService.create(Gem.MAX));
                    // Direct inventory click selects the third Gem type without moving the real item.
                    click(55);later(()->{click(18);later(()->{
                        check(StaffGemService.count(held(),Gem.MAX)==1&&magic.wands().maxUses(held())==550,"GUI installs Max Gem in shared socket");
                        check(player.getOpenInventory().getTopInventory().getItem(18).getItemMeta().getLore().stream().anyMatch(line->line.contains("Max +250")),"GUI displays Max bonus");
                        click(18);later(()->{
                            check(StaffGemService.count(held(),Gem.MAX)==0&&magic.wands().maxUses(held())==300,"GUI extracts Max and restores base cap");
                            int before=inventoryGems();var replacement=spells.createStaff(Element.WATER,Tier.MYTHIC);player.getInventory().setItem(0,replacement);click(27);
                            later(()->{check(StaffGemService.count(held(),Gem.DAMAGE)==0&&inventoryGems()==before,"stale GUI closes when bound staff is replaced");finish(null);});
                        });
                    });});
                });
            });});
        });});
    }
    void finish(Throwable error){
        if(error!=null)getLogger().log(java.util.logging.Level.SEVERE,"STAFF GEM CHECK FAILED",error);
        for(var mob:mobs)mob.remove();
        try{Files.writeString(Path.of("staff-gem-result.txt"),(error==null?"PASS ":"FAIL ")+passed+" checks; two sockets, physical inventory conservation, native GUI, casts and structure loot"+(error==null?"":"; "+error));}catch(Exception ignored){}
        Bukkit.getScheduler().runTaskLater(this,Bukkit::shutdown,2);
    }
}
