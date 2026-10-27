import com.example.advancemagic.AdvanceMagicPlugin;
import com.example.advancemagic.item.LegendaryWeaponService;
import com.example.advancemagic.item.LegendaryWeaponService.Weapon;
import com.mojang.authlib.GameProfile;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.bukkit.*;
import org.joml.Vector3f;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import java.nio.file.*;
import java.util.*;

/** Exercise real items, command permissions, menu events, packets and cleanup. */
public final class LegendaryWeaponChecks extends JavaPlugin {
    static final class Capture extends Connection {
        int sounds;
        final Map<Integer,int[]> mounts=new HashMap<>();
        Capture(){super(PacketFlow.SERVERBOUND);channel=new EmbeddedChannel();address=new java.net.InetSocketAddress("127.0.0.1",1);}
        void capture(Packet<?> p){if(p instanceof ClientboundSoundPacket)sounds++;if(p instanceof net.minecraft.network.protocol.game.ClientboundSetPassengersPacket m)mounts.put(m.getVehicle(),m.getPassengers());}
        @Override public void send(Packet<?> p){capture(p);}
        @Override public void send(Packet<?> p,ChannelFutureListener l){capture(p);}
        @Override public void send(Packet<?> p,ChannelFutureListener l,boolean f){capture(p);}
        @Override public boolean isConnected(){return true;}
    }
    int passed;
    final List<ServerPlayer> actors=new ArrayList<>();
    final List<Capture> connections=new ArrayList<>();
    AdvanceMagicPlugin magic;
    LegendaryWeaponService service;
    World world;
    void check(boolean value,String label){if(!value)throw new AssertionError(label);passed++;getLogger().info("PASS "+label);}
    @Override public void onEnable(){Bukkit.getScheduler().runTaskLater(this,()->{try{run();finish(null);}catch(Throwable e){finish(e);}},20);}
    Player actor(String name,boolean op){
        var server=MinecraftServer.getServer();var level=((CraftWorld)world).getHandle();
        var profile=new GameProfile(UUID.randomUUID(),name);
        var handle=new ServerPlayer(server,level,profile,ClientInformation.createDefault());
        var connection=new Capture();connections.add(connection);
        handle.connection=new ServerGamePacketListenerImpl(server,connection,handle,CommonListenerCookie.createInitial(profile,false)){
            @Override public boolean hasClientLoaded(){return true;}
        };
        handle.setPos(.5,100,.5);server.getPlayerList().getPlayers().add(handle);
        server.getPlayerList().getPlayersByUUID().put(profile.id(),handle);level.addNewPlayer(handle);actors.add(handle);
        Player p=handle.getBukkitEntity();p.setOp(op);p.setGameMode(GameMode.SURVIVAL);p.setGravity(false);return p;
    }
    int count(Player p){return Arrays.stream(p.getInventory().getStorageContents()).filter(Objects::nonNull).filter(i->service.identify(i)!=null).mapToInt(ItemStack::getAmount).sum();}
    void advance(int ticks){for(int i=0;i<ticks;i++){if(i%4==0)service.tick();magic.effects().tick();}}
    record Smash(double damage,double fall,boolean protectedFall) {}
    Smash strike(Player player,ItemStack weapon,double fall) throws Exception {
        player.getInventory().setItemInMainHand(weapon);
        var handle=((org.bukkit.craftbukkit.entity.CraftPlayer)player).getHandle();
        handle.detectEquipmentUpdates();
        var cooldown=net.minecraft.world.entity.LivingEntity.class.getDeclaredField("attackStrengthTicker");
        cooldown.setAccessible(true);cooldown.setInt(handle,100);
        handle.setOnGround(false);handle.fallDistance=fall;
        handle.resetCurrentImpulseContext();handle.setDeltaMovement(0,-.8,0);
        var target=world.spawn(new Location(world,.5,100,1.5),IronGolem.class,g->{g.setAI(false);g.setGravity(false);g.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).setBaseValue(1000);g.setHealth(1000);});
        double before=target.getHealth();
        handle.attack(((org.bukkit.craftbukkit.entity.CraftLivingEntity)target).getHandle());
        var result=new Smash(before-target.getHealth(),handle.fallDistance,handle.isIgnoringFallDamageFromCurrentImpulse());
        target.remove();return result;
    }
    void mechanics(Player player) throws Exception {
        var legacy=service.create(Weapon.MJOLNIR);legacy.setType(Material.NETHERITE_AXE);
        legacy.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.UNBREAKING,3);
        var marker=new NamespacedKey(this,"owner");var meta=legacy.getItemMeta();
        meta.getPersistentDataContainer().set(marker,PersistentDataType.STRING,"retained");legacy.setItemMeta(meta);
        var oldMeta=legacy.getItemMeta();
        check(service.migrate(legacy)&&legacy.getType()==Material.MACE,"old tagged axe migrates to mace");
        check(legacy.getItemMeta().equals(oldMeta)&&service.identify(legacy)==Weapon.MJOLNIR,"hammer upgrade retains complete metadata and enchantments");
        check(!service.migrate(legacy),"hammer migration is idempotent");
        check(!service.migrate(new ItemStack(Material.NETHERITE_AXE)),"ordinary axes never migrate");
        var forged=service.create(Weapon.MJOLNIR);forged.setType(Material.NETHERITE_AXE);meta=forged.getItemMeta();meta.setItemModel(NamespacedKey.minecraft("stick"));forged.setItemMeta(meta);
        check(!service.migrate(forged),"migration rejects inconsistent hammer model");
        var ground=strike(player,service.create(Weapon.MJOLNIR),0);
        var smash=strike(player,service.create(Weapon.MJOLNIR),6);
        var vanilla=strike(player,new ItemStack(Material.MACE),6);
        getLogger().info("mace damage ground="+ground+" smash="+smash+" vanilla="+vanilla);
        check(ground.damage>0&&smash.damage>ground.damage+10,"real player attack gains falling smash damage");
        check(Math.abs((smash.damage-15*1.5)-(vanilla.damage-6*1.5)*4)<.001,"Mjolnir multiplies native falling bonus by four");
        check(smash.fall==0&&smash.protectedFall,"successful smash resets fall distance and grants vanilla fall protection");
        player.setFallDistance(0);player.setVelocity(new org.bukkit.util.Vector());player.getInventory().clear();
        magic.packs().status(new org.bukkit.event.player.PlayerResourcePackStatusEvent(player,
            com.example.advancemagic.pack.ResourcePackService.PACK_ID,
            org.bukkit.event.player.PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED));
        var original=service.create(Weapon.LONGINUS);
        check(original.getType()==Material.NETHERITE_SWORD,"Longinus uses a nonthrowable melee base");
        var old=original.clone();old.setType(Material.TRIDENT);
        old.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.LOYALTY,3);
        check(service.migrate(old)&&service.identify(old)==Weapon.LONGINUS,"old Longinus migrates to melee");
        check(old.getEnchantmentLevel(org.bukkit.enchantments.Enchantment.LOYALTY)==3,"old spear retains enchantments");
        check(!service.migrate(old)&&!service.migrate(new ItemStack(Material.TRIDENT)),"migration is idempotent and leaves vanilla tridents alone");
        var handle=((org.bukkit.craftbukkit.entity.CraftPlayer)player).getHandle();
        player.getInventory().setItemInMainHand(original);
        var stack=handle.getMainHandItem();stack.getItem().use(handle.level(),handle,net.minecraft.world.InteractionHand.MAIN_HAND);
        check(!handle.isUsingItem(),"Longinus cannot start charging or throwing");
        var legacySpear=world.spawn(new Location(world,2,105,2),Trident.class,t->{var i=original.clone();i.setType(Material.TRIDENT);t.setItemStack(i);});
        var launch=new org.bukkit.event.entity.ProjectileLaunchEvent(legacySpear);Bukkit.getPluginManager().callEvent(launch);
        check(launch.isCancelled(),"legacy Longinus launch is blocked defensively");legacySpear.remove();
        var ordinary=world.spawn(new Location(world,2,105,2),Trident.class);
        launch=new org.bukkit.event.entity.ProjectileLaunchEvent(ordinary);Bukkit.getPluginManager().callEvent(launch);
        check(!launch.isCancelled(),"ordinary tridents remain throwable");ordinary.remove();
        for(var other:Bukkit.getOnlinePlayers())if(other!=player)other.teleport(new Location(world,20,100,20));
        player.teleport(new Location(world,.5,100,.5,0,0));
        var cooldown=net.minecraft.world.entity.LivingEntity.class.getDeclaredField("attackStrengthTicker");cooldown.setAccessible(true);
        var target=world.spawn(new Location(world,.5,100,7.6),IronGolem.class,g->{g.setAI(false);g.setGravity(false);});
        cooldown.setInt(handle,100);double before=target.getHealth();
        check(service.extendedStrike(player)&&target.getHealth()<before,"melee reaches a hitbox within 7 blocks");
        check(!service.extendedStrike(player),"extended melee respects native attack cooldown");
        target.setNoDamageTicks(0);target.teleport(new Location(world,.5,100,8.6));cooldown.setInt(handle,100);
        check(!service.extendedStrike(player),"melee cannot reach a hitbox beyond 7 blocks");
        target.teleport(new Location(world,.5,100,6.5));
        var wall=world.getBlockAt(0,101,3);wall.setType(Material.STONE);cooldown.setInt(handle,100);
        check(!service.extendedStrike(player),"extended melee cannot hit through walls");wall.setType(Material.AIR);
        var blocker=new org.bukkit.event.Listener(){
            @org.bukkit.event.EventHandler public void damage(org.bukkit.event.entity.EntityDamageByEntityEvent e){if(e.getDamager()==player)e.setCancelled(true);}
        };
        Bukkit.getPluginManager().registerEvents(blocker,this);cooldown.setInt(handle,100);before=target.getHealth();service.extendedStrike(player);
        check(target.getHealth()==before,"extended attack honors cancelled protection damage events");org.bukkit.event.HandlerList.unregisterAll(blocker);
        player.getInventory().setItemInMainHand(service.create(Weapon.EXCALIBUR));cooldown.setInt(handle,100);
        check(!service.extendedStrike(player),"other legendary weapons receive no extended reach");
        player.getInventory().setItemInMainHand(original);
        var outOfReach=new org.bukkit.event.entity.EntityDamageByEntityEvent(player,target,org.bukkit.event.entity.EntityDamageEvent.DamageCause.ENTITY_ATTACK,9);
        target.teleport(new Location(world,.5,100,10.5));service.reachLimit(outOfReach);
        check(outOfReach.isCancelled(),"native attacks also enforce a hard seven block cap");
        check(original.getItemMeta().getAttributeModifiers(org.bukkit.attribute.Attribute.ENTITY_INTERACTION_RANGE).iterator().next().getAmount()==4,"mainhand native targeting gains four blocks to reach seven");
        target.remove();player.getInventory().clear();
    }

    LivingEntity combatTarget(double z){
        return world.spawn(new Location(world,.5,100,z),IronGolem.class,g->{g.setAI(false);g.setGravity(false);g.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).setBaseValue(1000);g.setHealth(1000);});
    }
    void combatTicks(int n){for(int i=0;i<n;i++){service.combat().tick();magic.effects().tick();}}
    double attack(Player p,LivingEntity target) throws Exception {
        var h=((org.bukkit.craftbukkit.entity.CraftPlayer)p).getHandle();h.detectEquipmentUpdates();h.setOnGround(true);h.fallDistance=0;h.setDeltaMovement(0,0,0);p.setSprinting(false);
        var field=net.minecraft.world.entity.LivingEntity.class.getDeclaredField("attackStrengthTicker");field.setAccessible(true);field.setInt(h,100);
        target.setNoDamageTicks(0);double before=target.getHealth();p.attack(target);return before-target.getHealth();
    }
    void resetSkill(Player p,Weapon w){p.getPersistentDataContainer().remove(new NamespacedKey(magic,"legendary_cd_"+w.id()));}
    void combatChecks(Player p) throws Exception {
        var combat=service.combat();magic.effects().close();p.setSneaking(false);p.teleport(new Location(world,.5,100,.5,0,0));
        for(Weapon w:Weapon.values()){
            var target=combatTarget(.5);p.getInventory().setItemInMainHand(service.create(w));
            double lost=attack(p,target);check(Math.abs(lost-combat.base(w))<.01,"native unenchanted melee damage "+w+" = "+lost);target.remove();
        }
        var target=combatTarget(3.5);p.getInventory().setItemInMainHand(service.create(Weapon.TYRFING));
        for(int i=0;i<7;i++){double lost=attack(p,target);check(Math.abs(lost-Math.min(60,35+5*i))<.01,"Tyrfing successive target damage "+i+" = "+lost);}
        var fresh=combatTarget(4.5);check(Math.abs(attack(p,fresh)-35)<.01,"Tyrfing bonus is separate for each target");fresh.remove();
        combatTicks(220);check(Math.abs(combat.furyDamage(p,target)-55)<.01,"Tyrfing decays after ten idle seconds");
        combatTicks(100);check(Math.abs(combat.furyDamage(p,target)-35)<.01,"Tyrfing returns to 35 damage");
        p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).setBaseValue(200);p.setHealth(20);
        target.setAbsorptionAmount(10);resetSkill(p,Weapon.TYRFING);check(combat.activate(p,Weapon.TYRFING),"Tyrfing life steal activates");
        double health=p.getHealth();double lost=attack(p,target);check(Math.abs((p.getHealth()-health)-lost)<.01,"life steal equals health damage after absorption");
        check(combat.cooldownRemaining(p,Weapon.TYRFING)>179000&&!combat.activate(p,Weapon.TYRFING),"three-minute cooldown survives another weapon instance");
        combatTicks(200);health=p.getHealth();attack(p,target);check(p.getHealth()==health,"life steal expires after ten seconds");target.remove();magic.effects().close();
        p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).setBaseValue(20);p.setHealth(20);
        var near=combatTarget(2.5);var far=combatTarget(7.5);p.getInventory().setItemInMainHand(service.create(Weapon.LONGINUS));
        double close=attack(p,near),distant=attack(p,far);check(distant>close&&distant<=30&&close>=20,"Longinus damage rises with reach, capped at 30");
        check(far.hasPotionEffect(org.bukkit.potion.PotionEffectType.SLOWNESS),"Longinus applies slow only on successful melee hit");near.remove();far.remove();
        target=combatTarget(6.5);p.getInventory().setItemInMainHand(service.create(Weapon.EXCALIBUR));resetSkill(p,Weapon.EXCALIBUR);
        var armor=new ItemStack(Material.NETHERITE_CHESTPLATE);armor.addEnchantment(org.bukkit.enchantments.Enchantment.PROTECTION,4);target.getEquipment().setChestplate(armor);target.getAttribute(org.bukkit.attribute.Attribute.ARMOR).setBaseValue(30);target.getAttribute(org.bukkit.attribute.Attribute.ARMOR_TOUGHNESS).setBaseValue(20);
        check(combat.activate(p,Weapon.EXCALIBUR)&&combat.charging(p),"Excalibur begins a three second channel");double before=target.getHealth();combatTicks(60);check(target.getHealth()==before,"Excalibur does not release before charge completes");
        combatTicks(1);check(Math.abs(before-target.getHealth()-100)<.01,"Excalibur deals 100 through armor and protection");check(!combat.charging(p),"Excalibur releases channel lock");target.remove();magic.effects().close();
        target=combatTarget(6.5);p.getInventory().setItemInMainHand(service.create(Weapon.LONGINUS));resetSkill(p,Weapon.LONGINUS);before=target.getHealth();
        check(combat.activate(p,Weapon.LONGINUS),"Longinus gate opens");combatTicks(202);check(Math.abs(before-target.getHealth()-100)<.01,"Longinus rain deals ten pulses of ten without melee scaling");
        check(world.getEntitiesByClass(ItemDisplay.class).isEmpty(),"rain removes every temporary spear and seal");target.remove();magic.effects().close();
        target=combatTarget(6.5);p.getInventory().setItemInMainHand(service.create(Weapon.MJOLNIR));resetSkill(p,Weapon.MJOLNIR);before=target.getHealth();
        check(combat.activate(p,Weapon.MJOLNIR),"Mjolnir storm opens");combatTicks(601);check(Math.abs(before-target.getHealth()-300)<.01,"Mjolnir storm deals thirty pulses of ten");target.remove();magic.effects().close();
        var fall=new org.bukkit.event.entity.EntityDamageEvent(p,org.bukkit.event.entity.EntityDamageEvent.DamageCause.FALL,100);Bukkit.getPluginManager().callEvent(fall);check(fall.isCancelled(),"Mjolnir protects from landing without requiring a hit");magic.effects().close();
        target=combatTarget(3.5);p.getInventory().setItemInMainHand(service.create(Weapon.VAJRA));
        for(int i=0;i<10;i++)attack(p,target);check(combat.rootCharges(p)==0,"Vajra exhausts ten roots and starts two minute recovery");
        var anchor=target.getLocation();target.teleport(anchor.clone().add(2,0,0));combatTicks(1);check(target.getLocation().distanceSquared(anchor)<.01,"Vajra root actually holds a mob in place");
        combatTicks(60);target.teleport(anchor.clone().add(2,0,0));combatTicks(1);check(target.getLocation().distanceSquared(anchor)>1,"Vajra root releases after three seconds");
        check(p.getPersistentDataContainer().getOrDefault(new NamespacedKey(magic,"vajra_root_cd"),PersistentDataType.LONG,0L)>System.currentTimeMillis()+119000,"Vajra root recharge is persisted separately from active skill");target.remove();magic.effects().close();
        target=combatTarget(6.5);p.getInventory().setItemInMainHand(service.create(Weapon.VAJRA));resetSkill(p,Weapon.VAJRA);before=target.getHealth();
        check(combat.activate(p,Weapon.VAJRA),"Vajra expanding wave activates");combatTicks(21);check(Math.abs(before-target.getHealth()-50)<.01,"Vajra wave hits each target only once for fifty");target.remove();magic.effects().close();
        var enchanting=new com.example.advancemagic.item.LegendaryEnchanting(service);var book=new ItemStack(Material.ENCHANTED_BOOK);var bm=(org.bukkit.inventory.meta.EnchantmentStorageMeta)book.getItemMeta();bm.addStoredEnchant(org.bukkit.enchantments.Enchantment.IMPALING,5,true);book.setItemMeta(bm);
        var enchanted=enchanting.combine(service.create(Weapon.LONGINUS),book);check(enchanted!=null&&enchanted.getEnchantmentLevel(org.bukkit.enchantments.Enchantment.IMPALING)==5&&service.identify(enchanted)==Weapon.LONGINUS,"Longinus accepts a trident enchanted book without changing model or reach");
        bm=(org.bukkit.inventory.meta.EnchantmentStorageMeta)book.getItemMeta();bm.removeStoredEnchant(org.bukkit.enchantments.Enchantment.IMPALING);bm.addStoredEnchant(org.bukkit.enchantments.Enchantment.SHARPNESS,5,true);book.setItemMeta(bm);
        check(enchanting.combine(service.create(Weapon.LONGINUS),book)==null,"Longinus rejects sword-only enchantments");
        for(int seed=0;seed<10;seed++)check(enchanting.roll(seed,2,30).keySet().stream().allMatch(e->e.canEnchantItem(new ItemStack(Material.TRIDENT))),"table offers only vanilla trident enchantments seed "+seed);
        var sharp=service.create(Weapon.EXCALIBUR);sharp.addEnchantment(org.bukkit.enchantments.Enchantment.SHARPNESS,5);p.getInventory().setItemInMainHand(sharp);target=combatTarget(3.5);
        check(Math.abs(attack(p,target)-53)<.01,"native Sharpness enchant adds damage to Excalibur");target.remove();
        for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)if(Math.abs(x)==2||Math.abs(z)==2)world.getBlockAt(x,100,z).setType(Material.BOOKSHELF);
        world.getBlockAt(0,100,0).setType(Material.ENCHANTING_TABLE);p.setLevel(40);
        var ev=(org.bukkit.inventory.view.EnchantmentView)p.openEnchanting(new Location(world,0,100,0),true);
        ev.getTopInventory().setItem(0,service.create(Weapon.LONGINUS));ev.getTopInventory().setItem(1,new ItemStack(Material.LAPIS_LAZULI,64));
        var offers=ev.getOffers();int button=-1;for(int i=0;i<offers.length;i++)if(offers[i]!=null)button=i;
        check(button>=0&&offers[button].getEnchantment().canEnchantItem(new ItemStack(Material.TRIDENT)),"actual enchanting table shows a trident offer for Longinus");
        var handle=((org.bukkit.craftbukkit.entity.CraftPlayer)p).getHandle();int levels=p.getLevel();
        boolean accepted=handle.containerMenu.clickMenuButton(handle,button);var result=ev.getTopInventory().getItem(0);
        check(accepted&&result!=null&&!result.getEnchantments().isEmpty()&&result.getEnchantments().keySet().stream().allMatch(e->e.canEnchantItem(new ItemStack(Material.TRIDENT))),"actual table purchase enchants Longinus with only trident enchantments");
        check(p.getLevel()==levels-button-1&&ev.getTopInventory().getItem(1).getAmount()==64-button-1,"native enchanting consumes levels and lapis");p.closeInventory();
        var av=(org.bukkit.inventory.view.AnvilView)p.openAnvil(new Location(world,0,100,0),true);
        book=new ItemStack(Material.ENCHANTED_BOOK);bm=(org.bukkit.inventory.meta.EnchantmentStorageMeta)book.getItemMeta();bm.addStoredEnchant(org.bukkit.enchantments.Enchantment.IMPALING,5,true);book.setItemMeta(bm);
        av.getTopInventory().setFirstItem(service.create(Weapon.LONGINUS));av.getTopInventory().setSecondItem(book);
        result=av.getTopInventory().getResult();
        check(result!=null&&result.getEnchantmentLevel(org.bukkit.enchantments.Enchantment.IMPALING)==5&&av.getRepairCost()>0,"actual anvil offers Impaling Longinus at an XP cost");
        levels=p.getLevel();int cost=av.getRepairCost();handle.containerMenu.clicked(2,0,net.minecraft.world.inventory.ContainerInput.PICKUP,handle);
        check(p.getItemOnCursor().getEnchantmentLevel(org.bukkit.enchantments.Enchantment.IMPALING)==5&&p.getLevel()==levels-cost&&av.getTopInventory().getSecondItem()==null,"native anvil purchase consumes book and XP");p.setItemOnCursor(null);p.closeInventory();
        var victim=actors.get(1).getBukkitEntity();victim.teleport(new Location(world,.5,100,3.5));victim.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).setBaseValue(1000);victim.setHealth(1000);
        p.getPersistentDataContainer().remove(new NamespacedKey(magic,"vajra_root_cd"));p.getPersistentDataContainer().remove(new NamespacedKey(magic,"vajra_root_charges"));p.getInventory().setItemInMainHand(service.create(Weapon.VAJRA));attack(p,victim);
        var move=new org.bukkit.event.player.PlayerMoveEvent(victim,victim.getLocation(),victim.getLocation().add(2,0,0));Bukkit.getPluginManager().callEvent(move);
        check(move.getTo().distanceSquared(move.getFrom())<.01,"Vajra also prevents a player from moving");
        combatTicks(60);move=new org.bukkit.event.player.PlayerMoveEvent(victim,victim.getLocation(),victim.getLocation().add(2,0,0));Bukkit.getPluginManager().callEvent(move);
        check(move.getTo().distanceSquared(move.getFrom())>1,"player movement is restored after root expires");victim.teleport(new Location(world,20,100,20));
        p.getInventory().clear();
    }
    void run() throws Exception {
        magic=(AdvanceMagicPlugin)Bukkit.getPluginManager().getPlugin("advance-magic");
        check(magic!=null&&magic.isEnabled(),"release boots on Paper 26.2");
        service=magic.legendaryWeapons();world=Bukkit.getWorlds().getFirst();world.getChunkAt(0,0).setForceLoaded(true);
        Player admin=actor("RelicAdmin",true),guest=actor("RelicGuest",false);
        for(Weapon weapon:Weapon.values()){
            ItemStack item=service.create(weapon);
            check(service.identify(item)==weapon,"server identity "+weapon);
            check(item.getItemMeta().getItemModel().equals(weapon.model()),"item model component "+weapon);
            check(item.getItemMeta().isUnbreakable(),"prototype durability "+weapon);
            check(service.identify(ItemStack.deserializeBytes(item.serializeAsBytes()))==weapon,"saved item round trip "+weapon);
            check(magic.wands().spell(item)==null,"does not invoke wand casts "+weapon);
            var forged=item.clone();var meta=forged.getItemMeta();meta.setItemModel(new NamespacedKey("minecraft","stick"));forged.setItemMeta(meta);
            check(service.identify(forged)==null,"reject inconsistent model identity "+weapon);
        }
        check(service.identify(new ItemStack(Material.NETHERITE_SWORD))==null,"vanilla sword stays vanilla");
        check(service.identify(null)==null,"empty hand");
        check(Weapon.parse("MJOLNIR")==Weapon.MJOLNIR&&Weapon.parse("mjonir")==Weapon.MJOLNIR,"hammer spelling aliases");
        Bukkit.dispatchCommand(guest,"magic weapon give all");check(count(guest)==0,"give requires admin");
        Bukkit.dispatchCommand(guest,"magic weapons");check(guest.getOpenInventory().getTopInventory().getSize()!=9,"menu requires admin");
        Bukkit.dispatchCommand(admin,"magic weapon give all");check(count(admin)==5,"give all five");admin.getInventory().clear();
        Bukkit.dispatchCommand(admin,"magic weapon give mjolnir RelicGuest");check(count(guest)==1,"explicit online recipient");guest.getInventory().clear();
        Bukkit.dispatchCommand(admin,"magic weapon give unknown");check(count(admin)==0,"invalid ID does not give items");
        Bukkit.dispatchCommand(admin,"magic weapon give mjolnir MissingPlayer");check(count(admin)==0,"missing player does not fall back to sender");
        for(int i=0;i<36;i++)admin.getInventory().setItem(i,new ItemStack(Material.STONE,64));
        int drops=world.getEntitiesByClass(Item.class).size();
        Bukkit.dispatchCommand(admin,"magic weapon give mjolnir");check(count(admin)==0&&world.getEntitiesByClass(Item.class).size()==drops,"full inventory preserves world and items");
        admin.getInventory().clear();Bukkit.dispatchCommand(admin,"magic weapons");
        check(admin.getOpenInventory().getTopInventory().getSize()==9,"opens weapon showcase");
        var click=new InventoryClickEvent(admin.getOpenInventory(),InventoryType.SlotType.CONTAINER,5,ClickType.LEFT,InventoryAction.PICKUP_ALL);
        Bukkit.getPluginManager().callEvent(click);check(click.isCancelled()&&count(admin)==1,"menu issues server-owned hammer");
        var shift=new InventoryClickEvent(admin.getOpenInventory(),InventoryType.SlotType.CONTAINER,5,ClickType.SHIFT_LEFT,InventoryAction.MOVE_TO_OTHER_INVENTORY);
        Bukkit.getPluginManager().callEvent(shift);check(shift.isCancelled()&&count(admin)==1,"shift transfer cannot duplicate menu icons");
        admin.setOp(false);Bukkit.getPluginManager().callEvent(click);check(count(admin)==1,"permission rechecked after opening menu");
        admin.closeInventory();admin.setOp(true);admin.getInventory().clear();
        admin.getInventory().setItemInMainHand(service.create(Weapon.MJOLNIR));
        int beforeSounds=connections.getFirst().sounds;service.tick();magic.effects().tick();
        check(connections.getFirst().sounds>=beforeSounds+2,"equip sends layered resonance to holder");
        check(connections.get(1).sounds==0,"nearby player does not receive equip thunder");
        check(magic.effects().size()==1&&world.getEntitiesByClass(ItemDisplay.class).size()==1,"one owned equip circle");
        for(int i=0;i<6;i++)service.tick();check(magic.effects().size()==1,"holding does not repeatedly spawn circles");
        advance(30);check(magic.effects().size()==0&&world.getEntitiesByClass(ItemDisplay.class).isEmpty(),"circle expires without entity leaks");
        admin.getInventory().clear();service.tick();
        admin.getInventory().setItemInOffHand(service.create(Weapon.VAJRA));advance(48);
        check(service.holding(admin,Weapon.VAJRA),"offhand weapon recognized");
        admin.getInventory().setItemInMainHand(service.create(Weapon.TYRFING));service.tick();magic.effects().tick();
        check(magic.effects().size()==1,"switch weapon starts one circle");
        admin.getInventory().clear();admin.getInventory().setItemInOffHand(null);magic.effects().tick();
        check(magic.effects().size()==0&&world.getEntitiesByClass(ItemDisplay.class).isEmpty(),"putting weapon away removes owned effects");
        advance(44);admin.getInventory().setItemInMainHand(service.create(Weapon.EXCALIBUR));service.tick();magic.effects().tick();
        check(magic.effects().size()==1,"fresh equip after cooldown");magic.effects().closeOwner(admin.getUniqueId());
        check(world.getEntitiesByClass(ItemDisplay.class).isEmpty(),"death and world-change cleanup path");
        magic.getConfig().set("legendary-weapons.sounds",false);beforeSounds=connections.getFirst().sounds;
        admin.getInventory().setItemInMainHand(service.create(Weapon.LONGINUS));advance(280);
        check(connections.getFirst().sounds==beforeSounds,"sounds option disables equip and ambient audio");
        magic.getConfig().set("legendary-weapons.equip-sigil",false);admin.getInventory().setItemInMainHand(service.create(Weapon.VAJRA));advance(48);
        check(magic.effects().size()==0,"sigil can be disabled independently of native held VFX");
        admin.getInventory().clear();admin.getInventory().setItemInOffHand(null);advance(30);
        var retired = new ItemStack(Material.NETHERITE_CHESTPLATE);
        var retiredMeta = retired.getItemMeta();
        retiredMeta.setItemModel(new NamespacedKey("advance_magic", "armor_aegis_chestplate"));
        var equip = retiredMeta.getEquippable();equip.setModel(new NamespacedKey("advance_magic", "armor_aegis"));retiredMeta.setEquippable(equip);
        retiredMeta.getPersistentDataContainer().set(new NamespacedKey("advance-magic", "legendary_armor"),PersistentDataType.STRING,"aegis:chestplate");
        retired.setItemMeta(retiredMeta);retired.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.PROTECTION,4);
        admin.getInventory().setChestplate(retired);
        com.example.advancemagic.item.RetiredArmorCleanup.clean(admin.getInventory());
        var nativeArmor = admin.getInventory().getChestplate();
        check(nativeArmor.getItemMeta().getItemModel()==null && NamespacedKey.minecraft("netherite").equals(nativeArmor.getItemMeta().getEquippable().getModel()),"retired armor restores native wearable model");
        check(nativeArmor.getEnchantmentLevel(org.bukkit.enchantments.Enchantment.PROTECTION)==4 && !nativeArmor.getItemMeta().getPersistentDataContainer().has(new NamespacedKey("advance-magic","legendary_armor")),"retired armor preserves enchantments and removes tag");
        admin.getInventory().clear();
        var observedCasts = new ArrayList<com.example.advancemagic.api.MagicCastEvent>();
        org.bukkit.event.Listener wandProbe = new org.bukkit.event.Listener() {};
        Bukkit.getPluginManager().registerEvent(com.example.advancemagic.api.MagicCastEvent.class,wandProbe,org.bukkit.event.EventPriority.LOWEST,(listener,event)->{
            var cast=(com.example.advancemagic.api.MagicCastEvent)event;observedCasts.add(cast);cast.setCancelled(true);
        },this);
        try {
            for(var spell:com.example.advancemagic.spell.Spell.values()) for(boolean sneak:new boolean[]{false,true}) {
                admin.setSneaking(sneak);magic.casts().quit(admin);
                var wand=magic.wands().create(spell);admin.getInventory().setItemInMainHand(wand);
                int beforeCast=observedCasts.size();
                magic.casts().interact(new org.bukkit.event.player.PlayerInteractEvent(admin,org.bukkit.event.block.Action.RIGHT_CLICK_AIR,wand,null,org.bukkit.block.BlockFace.SELF,EquipmentSlot.HAND));
                check(observedCasts.size()==beforeCast+1 && !observedCasts.getLast().isBasicCast(),"wand input always uses main spell "+spell+" sneak="+sneak);
            }
        } finally {org.bukkit.event.HandlerList.unregisterAll(wandProbe);admin.setSneaking(false);admin.getInventory().clear();magic.casts().quit(admin);}
        mechanics(admin);
        combatChecks(admin);
        magic.effects().close();
        service.quit(new PlayerQuitEvent(admin,net.kyori.adventure.text.Component.empty()));service.close();
        check(magic.effects().size()==0,"service shutdown is clean");
    }
    void finish(Throwable error){
        if(error!=null)getLogger().log(java.util.logging.Level.SEVERE,"LEGENDARY WEAPONS FAILED after "+passed,error);
        try{Files.writeString(Path.of("legendary-weapons-result.txt"),error==null?"PASS "+passed:"FAIL "+error);}catch(Exception ignored){}
        if(magic!=null)magic.effects().close();
        for(ServerPlayer p:actors){MinecraftServer.getServer().getPlayerList().getPlayers().remove(p);MinecraftServer.getServer().getPlayerList().getPlayersByUUID().remove(p.getUUID());p.discard();}
        Bukkit.getScheduler().runTaskLater(this,Bukkit::shutdown,5);
    }
}
