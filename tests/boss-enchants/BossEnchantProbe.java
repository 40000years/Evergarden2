import com.example.voidscape.VoidscapePlugin;
import com.example.voidscape.enchant.*;
import com.example.voidscape.world.*;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.server.network.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.enchantment.*;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.enchantments.CraftEnchantment;
import org.bukkit.craftbukkit.entity.*;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import java.net.InetSocketAddress;
import java.nio.file.*;
import java.util.*;

/** Real menu clicks and native effect functions, never shipped with the release. */
public final class BossEnchantProbe extends JavaPlugin implements Listener {
    VoidscapePlugin garden;World world;ServerLevel level;ServerPlayer actor;Player player;LivingEntity victim;
    final List<String> checks=new ArrayList<>(),audit=new ArrayList<>();
    final Map<Enchantment,int[]> ranges=new HashMap<>();final Map<Enchantment,Material> targets=new HashMap<>();
    EntityDamageEvent lastDamage;
    void check(boolean value,String message){if(!value)throw new AssertionError(message);checks.add(message);getLogger().info("PASS: "+message);}
    void near(double actual,double expected,String label){check(Math.abs(actual-expected)<1e-4,label+" = "+actual+" (expected "+expected+")");}
    @Override public void onEnable(){Bukkit.getPluginManager().registerEvents(this,this);Bukkit.getScheduler().runTaskLater(this,()->{
        String report="PASS";
        try{
            garden=(VoidscapePlugin)Bukkit.getPluginManager().getPlugin("Evergarden");world=Bukkit.getWorlds().getFirst();level=((CraftWorld)world).getHandle();createPlayer();
            if(Files.exists(Path.of("enchant-saved-items.bin")))restart();else first();
        }catch(Throwable error){report="FAIL "+error;getLogger().log(java.util.logging.Level.SEVERE,"BOSS ENCHANT PROBE",error);}
        finally{
            if(victim!=null)victim.remove();if(player!=null)garden.bossEnchantEffects().clear(player);
            if(actor!=null){actor.discard();var list=MinecraftServer.getServer().getPlayerList();list.getPlayers().remove(actor);list.getPlayersByUUID().remove(actor.getUUID());}
            try{Files.writeString(Path.of("boss-enchant-result.txt"),report+"\n"+String.join("\n",checks));if(!audit.isEmpty())Files.write(Path.of("enchant-runtime-audit.txt"),audit);}catch(Exception ignored){}
            Bukkit.getScheduler().runTaskLater(this,Bukkit::shutdown,3);
        }
    },20);}
    void createPlayer(){
        var server=MinecraftServer.getServer();var profile=new GameProfile(UUID.randomUUID(),"BossEnchantTester");actor=new ServerPlayer(server,level,profile,ClientInformation.createDefault());
        var connection=new Connection(PacketFlow.SERVERBOUND);connection.channel=new EmbeddedChannel();connection.address=new InetSocketAddress("127.0.0.1",1);
        actor.connection=new ServerGamePacketListenerImpl(server,connection,actor,CommonListenerCookie.createInitial(profile,false)){@Override public boolean hasClientLoaded(){return true;}};
        actor.setPos(.5,100,.5);server.getPlayerList().getPlayers().add(actor);server.getPlayerList().getPlayersByUUID().put(profile.id(),actor);level.addNewPlayer(actor);
        player=actor.getBukkitEntity();player.setGravity(false);player.setGameMode(GameMode.SURVIVAL);player.setLevel(1000);
        world.getBlockAt(0,99,0).setType(Material.STONE);world.getBlockAt(2,100,0).setType(Material.ANVIL);
        victim=(LivingEntity)world.spawnEntity(new Location(world,4,100,0),EntityType.COW);victim.setGravity(false);victim.getAttribute(Attribute.MAX_HEALTH).setBaseValue(1000);victim.setHealth(1000);
    }
    ItemStack item(Material material,Enchantment enchant,int number,boolean trusted){
        var item=new ItemStack(material);var meta=item.getItemMeta();
        if(meta instanceof EnchantmentStorageMeta book)book.addStoredEnchant(enchant,number,true);else meta.addEnchant(enchant,number,true);
        if(trusted){meta.getPersistentDataContainer().set(garden.key("boss_treasure_v3"),PersistentDataType.STRING,"arkhaia-castle");garden.bossEnchants().markGenerated(meta);}
        item.setItemMeta(meta);return item;
    }
    AnvilMenu menu(ItemStack left,ItemStack right){
        var menu=new AnvilMenu(1,actor.getInventory(),ContainerLevelAccess.create(level,new net.minecraft.core.BlockPos(2,100,0)));menu.checkReachable=false;actor.containerMenu=menu;
        menu.getSlot(0).set(CraftItemStack.asNMSCopy(left));menu.getSlot(1).set(right==null?net.minecraft.world.item.ItemStack.EMPTY:CraftItemStack.asNMSCopy(right));menu.slotsChanged(menu.getSlot(0).container);return menu;
    }
    ItemStack result(AnvilMenu menu){var raw=menu.getSlot(2).getItem();return raw.isEmpty()?null:CraftItemStack.asBukkitCopy(raw);}
    int lvl(ItemStack item,Enchantment enchant){return BossEnchantService.enchants(item).getOrDefault(enchant,0);}
    void first()throws Exception {
        var config=YamlConfiguration.loadConfiguration(new java.io.File(garden.getDataFolder(),"boss-treasure.yml"));var entries=config.getConfigurationSection("equipment.entries");
        for(var id:entries.getKeys(false)){
            var row=entries.getConfigurationSection(id);var material=Material.valueOf(row.getString("material"));var enchants=row.getConfigurationSection("enchantments");
            for(var name:enchants.getKeys(false)){
                var enchant=Registry.ENCHANTMENT.get(NamespacedKey.minecraft(name));var range=enchants.getConfigurationSection(name);
                int low=range.getInt("min"),high=range.getInt("max");ranges.merge(enchant,new int[]{low,high},(a,b)->new int[]{Math.min(a[0],b[0]),Math.max(a[1],b[1])});
                if(material!=Material.ENCHANTED_BOOK&&enchant.canEnchantItem(new ItemStack(material)))targets.putIfAbsent(enchant,material);
            }
        }
        check(ranges.size()==31,"all 31 configured loot enchantments enumerated");
        var table=new BossLootTable(garden);var seen=new HashSet<Enchantment>();var random=new Random(729341);
        for(int i=0;i<15000;i++)for(var loot:table.roll(random,BossStructureLayout.Kind.ARKHAIA)){
            var auth=garden.bossEnchants().authorized(loot);
            for(var e:BossEnchantService.enchants(loot).entrySet()){
                seen.add(e.getKey());if(e.getValue()>e.getKey().getMaxLevel()&&auth.getOrDefault(e.getKey(),0)!=e.getValue())throw new AssertionError("Loot missing entitlement "+e);
            }
        }
        check(seen.equals(ranges.keySet()),"15,000 actual chest rolls cover every configured enchant and stamp every over-cap award");
        var saved=new ArrayList<ItemStack>();
        for(var entry:ranges.entrySet()){
            var enchant=entry.getKey();var target=targets.get(enchant);check(target!=null,"supported target exists for "+enchant.getKey());
            for(int n=entry.getValue()[0];n<=entry.getValue()[1];n++){
                var book=item(Material.ENCHANTED_BOOK,enchant,n,true);var menu=menu(new ItemStack(target),book);var output=result(menu);
                check(output!=null&&lvl(output,enchant)==n,enchant.getKey()+" level "+n+" transfers through native anvil exactly (actual "+(output==null?"null":lvl(output,enchant))+", cost "+menu.getCost()+", auth "+garden.bossEnchants().authorized(book)+")");
                check(!menu.bypassEnchantmentLevelRestriction,"scoped bypass reset after "+enchant.getKey());
                if(n>enchant.getMaxLevel()){
                    check(garden.bossEnchants().level(output,enchant)==n,"transferred entitlement equals existing level "+n);
                    output=result(menu(output,book));check(output!=null&&lvl(output,enchant)==n,"equal boss levels cannot grow: "+enchant.getKey()+" "+n+" + "+n);
                    saved.add(output);
                }
            }
        }
        anvilSecurity();runtime();
        Files.write(Path.of("enchant-saved-items.bin"),ItemStack.serializeItemsAsBytes(saved));world.save();
    }
    void anvilSecurity(){
        var p8=item(Material.ENCHANTED_BOOK,Enchantment.PROTECTION,8,true);var m=menu(new ItemStack(Material.DIAMOND_CHESTPLATE),p8);var output=result(m);near(m.getCost(),8,"Protection VIII book costs 8 XP");
        int xp=player.getLevel();m.clicked(2,0,net.minecraft.world.inventory.ContainerInput.PICKUP,actor);
        check(lvl(CraftItemStack.asBukkitCopy(m.getCarried()),Enchantment.PROTECTION)==8&&m.getSlot(0).getItem().isEmpty()&&m.getSlot(1).getItem().isEmpty()&&player.getLevel()==xp-8,"real survival result click delivers VIII, consumes both inputs and pays XP once");
        m.clicked(2,0,net.minecraft.world.inventory.ContainerInput.PICKUP,actor);check(player.getLevel()==xp-8,"repeated result click cannot replay reward/payment");m.setCarried(net.minecraft.world.item.ItemStack.EMPTY);
        m=menu(new ItemStack(Material.DIAMOND_SWORD),p8);check(result(m)==null,"incompatible Protection book cannot enchant a sword");
        var fortune=item(Material.DIAMOND_PICKAXE,Enchantment.FORTUNE,5,true);m=menu(fortune,item(Material.ENCHANTED_BOOK,Enchantment.SILK_TOUCH,1,false));check(result(m)==null,"Silk Touch remains incompatible with Fortune");
        var normal4=item(Material.ENCHANTED_BOOK,Enchantment.UNBREAKING,4,false);m=menu(normal4,normal4);
        check(lvl(result(m),Enchantment.UNBREAKING)<=3,"unmarked villager/foreign Unbreaking IV + IV cannot make V");
        var sharp=item(Material.DIAMOND_SWORD,Enchantment.SHARPNESS,8,true);m=menu(sharp,normal4);output=result(m);
        check(lvl(output,Enchantment.SHARPNESS)==8&&lvl(output,Enchantment.UNBREAKING)==3,"boss item does not authorize an unrelated normal Unbreaking enchant");
        m=menu(output,normal4);check(lvl(result(m),Enchantment.UNBREAKING)==3,"repeated ordinary merges into boss gear cannot promote Unbreaking");
        var stripped=sharp.clone();var meta=stripped.getItemMeta();meta.removeEnchant(Enchantment.SHARPNESS);stripped.setItemMeta(meta);
        m=menu(stripped,item(Material.ENCHANTED_BOOK,Enchantment.SHARPNESS,5,false));check(lvl(result(m),Enchantment.SHARPNESS)==5,"removed enchant leaves no reusable high-level entitlement");
        var legacy=item(Material.ENCHANTED_BOOK,Enchantment.PROTECTION,8,false);meta=legacy.getItemMeta();meta.getPersistentDataContainer().set(garden.key("boss_treasure_v3"),PersistentDataType.STRING,"seraphon-sanctum");legacy.setItemMeta(meta);
        check(lvl(result(menu(new ItemStack(Material.DIAMOND_CHESTPLATE),legacy)),Enchantment.PROTECTION)==8,"existing e2.50 boss books transfer without reissuing loot");
        var lb=new ItemStack(Material.DIAMOND_SWORD);meta=lb.getItemMeta();garden.relics().applyLimitBreakMeta(lb.getType(),meta,LimitBreakType.SHARPNESS,6);lb.setItemMeta(meta);
        output=result(menu(lb,item(Material.ENCHANTED_BOOK,Enchantment.SHARPNESS,8,true)));
        check(lvl(output,Enchantment.SHARPNESS)==8&&garden.relics().getLimitBreakLevel(output,LimitBreakType.SHARPNESS)==5,"native VIII replaces weaker virtual VI without doubled damage");
        meta=lb.getItemMeta();garden.relics().applyLimitBreakMeta(lb.getType(),meta,LimitBreakType.SHARPNESS,9);lb.setItemMeta(meta);output=result(menu(lb,item(Material.ENCHANTED_BOOK,Enchantment.SHARPNESS,8,true)));
        check(lvl(output,Enchantment.SHARPNESS)==5&&garden.relics().getLimitBreakLevel(output,LimitBreakType.SHARPNESS)==9,"stronger virtual IX remains intact when adding a boss VIII book");
        var infinity=item(Material.BOW,Enchantment.INFINITY,1,false);check(result(menu(infinity,item(Material.ENCHANTED_BOOK,Enchantment.MENDING,1,true)))==null,"Infinity and Mending stay incompatible");
        var worked=item(Material.DIAMOND_CHESTPLATE,Enchantment.PROTECTION,8,true);var repair=(org.bukkit.inventory.meta.Repairable)worked.getItemMeta();repair.setRepairCost(31);worked.setItemMeta(repair);m=menu(worked,p8);
        check(result(m)!=null&&lvl(result(m),Enchantment.PROTECTION)==8&&m.getCost()==39&&m.maximumRepairCost==40,"VIII + VIII keeps VIII and native cost 39 at the prior-work boundary");
        repair=(org.bukkit.inventory.meta.Repairable)worked.getItemMeta();repair.setRepairCost(32);worked.setItemMeta(repair);m=menu(worked,p8);
        check(result(m)==null&&m.getCost()==40&&m.maximumRepairCost==40,"native Too Expensive limit still applies at cost 40");
    }
    net.minecraft.world.item.ItemStack nativeItem(Material type,Enchantment enchant,int value){return CraftItemStack.asNMSCopy(item(type,enchant,value,true));}
    net.minecraft.world.entity.LivingEntity mob(){return ((CraftLivingEntity)victim).getHandle();}
    void runtime()throws Exception {
        var source=level.damageSources().playerAttack(actor);
        for(var entry:ranges.entrySet()){
            var enchant=entry.getKey();String id=enchant.getKey().getKey();var holder=CraftEnchantment.bukkitToMinecraftHolder(enchant);
            for(int n=entry.getValue()[0];n<=entry.getValue()[1];n++){
                var gear=nativeItem(targets.get(enchant),enchant,n);double value;
                switch(id){
                    case "sharpness"->{value=EnchantmentHelper.modifyDamage(level,gear,mob(),source,0);near(value,.5*n+.5,"native Sharpness "+n);}
                    case "impaling"->{var squid=world.spawnEntity(new Location(world,7,100,0),EntityType.SQUID);value=EnchantmentHelper.modifyDamage(level,gear,((CraftEntity)squid).getHandle(),source,0);squid.remove();near(value,2.5*n,"native Impaling "+n);}
                    case "density"->{value=EnchantmentHelper.modifyFallBasedDamage(level,gear,mob(),source,0);near(value,.5*n,"native Density damage per fallen block "+n);}
                    case "breach"->{value=EnchantmentHelper.modifyArmorEffectiveness(level,gear,mob(),source,1);near(value,1-.15*n,"native Breach armor factor "+n);}
                    case "loyalty"->{value=EnchantmentHelper.getTridentReturnToOwnerAcceleration(level,gear,actor);near(value,n,"native Loyalty acceleration "+n);}
                    case "riptide"->{value=EnchantmentHelper.getTridentSpinAttackStrength(gear,actor);near(value,.75+.75*n,"native Riptide strength "+n);}
                    case "quick_charge"->{value=net.minecraft.world.item.CrossbowItem.getChargeDuration(gear,actor);near(value,25-5*n,"native Quick Charge ticks "+n);}
                    case "piercing"->{value=EnchantmentHelper.getPiercingCount(level,gear,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ARROW));near(value,n,"native Piercing penetration level "+n);}
                    case "multishot"->{value=EnchantmentHelper.processProjectileCount(level,gear,actor,1);near(value,3,"native Multishot projectile count");}
                    case "infinity"->{value=EnchantmentHelper.processAmmoUse(level,gear,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ARROW),1);near(value,0,"native Infinity arrow consumption");}
                    case "mending"->{value=EnchantmentHelper.modifyDurabilityToRepairFromXp(level,gear,5);near(value,10,"native Mending repairs from 5 XP");}
                    case "silk_touch"->{value=EnchantmentHelper.processBlockExperience(level,gear,5);near(value,0,"native Silk Touch ore XP");}
                    case "unbreaking"->{int trials=20000,total=0;for(int i=0;i<trials;i++)total+=EnchantmentHelper.processDurabilityChange(level,gear,1);value=total/(double)trials;double expected=targets.get(enchant).name().contains("HELMET")?.6+.4/(n+1.0):1/(n+1.0);check(Math.abs(value-expected)<.02,"native Unbreaking "+n+" durability rate "+value+" expected "+expected);
                        var armor=nativeItem(Material.DIAMOND_CHESTPLATE,enchant,n);total=0;for(int i=0;i<trials;i++)total+=EnchantmentHelper.processDurabilityChange(level,armor,1);double armorRate=total/(double)trials;check(Math.abs(armorRate-(.6+.4/(n+1.0)))<.02,"native armor Unbreaking "+n+" durability rate "+armorRate);
                    }
                    case "efficiency","aqua_affinity","respiration","sweeping_edge","depth_strider","swift_sneak"->{
                        var effects=holder.value().getEffects(EnchantmentEffectComponents.ATTRIBUTES);check(!effects.isEmpty(),id+" has a native attribute effect");value=effects.getFirst().amount().calculate(n);
                        double expected=switch(id){case "efficiency"->n*n+1;case "aqua_affinity"->4*n;case "respiration"->n;case "sweeping_edge"->n/(n+1.0);case "depth_strider"->n/3.0;default->.15*n;};near(value,expected,"native "+id+" attribute "+n);
                    }
                    case "protection","feather_falling"->{
                        var damage=id.equals("feather_falling")?level.damageSources().fall():source;var count=new org.apache.commons.lang3.mutable.MutableFloat();holder.value().modifyDamageProtection(level,n,gear,mob(),damage,count);value=count.floatValue();near(value,id.equals("feather_falling")?3*n:n,"native "+id+" EPF "+n);
                    }
                    case "fortune"->{var ore=world.getBlockAt(10,100,0);ore.setType(Material.DIAMOND_ORE);int total=0,trials=10000,max=0;var tool=CraftItemStack.asBukkitCopy(gear);for(int i=0;i<trials;i++){int drops=ore.getDrops(tool,player).stream().mapToInt(ItemStack::getAmount).sum();total+=drops;max=Math.max(max,drops);}value=total/(double)trials;double expected=1+n*(n+1)/(2.0*(n+2));check(Math.abs(value-expected)<.07&&max==n+1,"native Fortune "+n+" ore mean "+value+", max "+max+", expected "+expected);}
                    case "looting"->{value=EnchantmentHelper.processEquipmentDropChance(level,mob(),source,.1f);player.getInventory().setItemInMainHand(CraftItemStack.asBukkitCopy(gear));value=EnchantmentHelper.processEquipmentDropChance(level,mob(),source,.1f);near(value,.1+.01*n,"native Looting equipment chance "+n);player.getInventory().setItemInMainHand(null);}
                    case "fire_aspect"->{mob().clearFire();EnchantmentHelper.doPostAttackEffectsWithItemSource(level,mob(),source,gear);value=mob().getRemainingFireTicks();near(value,80*n,"native Fire Aspect burning ticks "+n);}
                    case "power","punch"->{var arrow=(AbstractArrow)world.spawnEntity(new Location(world,1,103,1),EntityType.ARROW);var arrowSource=level.damageSources().arrow(((CraftAbstractArrow)arrow).getHandle(),actor);value=id.equals("power")?EnchantmentHelper.modifyDamage(level,gear,mob(),arrowSource,0):EnchantmentHelper.modifyKnockback(level,gear,mob(),arrowSource,0);arrow.remove();near(value,id.equals("power")?.5*n+.5:n,"native "+id+" "+n);}
                    case "flame"->{var arrow=(AbstractArrow)world.spawnEntity(new Location(world,1,103,1),EntityType.ARROW);EnchantmentHelper.onProjectileSpawned(level,gear,((CraftAbstractArrow)arrow).getHandle(),ignored->{});value=arrow.getFireTicks();arrow.remove();near(value,2000,"native Flame burning ticks");}
                    case "frost_walker","soul_speed","channeling","wind_burst"->{value=complexEffect(id,holder,n,gear,source);}
                    default->throw new AssertionError("Runtime audit missing "+id);
                }
                audit.add(id+" "+n+" = "+value);
            }
        }
        capEffects();
    }
    double complexEffect(String id,net.minecraft.core.Holder<net.minecraft.world.item.enchantment.Enchantment> holder,int n,net.minecraft.world.item.ItemStack gear,net.minecraft.world.damagesource.DamageSource source){
        var effects=holder.value().effects();
        String encoded=effects.toString();check(!encoded.isBlank(),"native "+id+" effect definition exists at "+n);
        // Contextual effects are exercised below with real on-ground, weather and falling conditions.
        if(id.equals("channeling")){
            world.setStorm(true);world.setThundering(true);level.setRainLevel(1);level.setThunderLevel(1);var trident=(Trident)world.spawnEntity(new Location(world,5,102,0),EntityType.TRIDENT);
            var tridentSource=level.damageSources().trident(((CraftTrident)trident).getHandle(),actor);
            int before=(int)world.getEntities().stream().filter(e->e instanceof LightningStrike).count();
            EnchantmentHelper.doPostAttackEffectsWithItemSource(level,mob(),tridentSource,gear);trident.remove();
            int after=(int)world.getEntities().stream().filter(e->e instanceof LightningStrike).count();check(after>before,"native Channeling summons lightning under thunder and open sky");return after-before;
        }
        if(id.equals("wind_burst")){
            var row=holder.value().getEffects(EnchantmentEffectComponents.POST_ATTACK).getFirst();
            var explosion=(net.minecraft.world.item.enchantment.effects.ExplodeEffect)row.effect();double amount=explosion.knockbackMultiplier().orElseThrow().calculate(n);near(amount,new double[]{1.2,1.75,2.2}[n-1],"native Wind Burst knockback "+n);return amount;
        }
        if(id.equals("soul_speed")){
            var row=holder.value().getEffects(EnchantmentEffectComponents.LOCATION_CHANGED).getFirst();
            var group=(net.minecraft.world.item.enchantment.effects.AllOf.LocationBasedEffects)row.effect();
            var attribute=(net.minecraft.world.item.enchantment.effects.EnchantmentAttributeEffect)group.effects().getFirst();double amount=attribute.amount().calculate(n);near(amount,.03+.0105*n,"native Soul Speed movement addition "+n);return amount;
        }
        var row=holder.value().getEffects(EnchantmentEffectComponents.LOCATION_CHANGED).getFirst();
        var disk=(net.minecraft.world.item.enchantment.effects.ReplaceDisk)row.effect();double radius=disk.radius().calculate(n);near(radius,n+2,"native Frost Walker ice radius "+n);return radius;
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void damaged(EntityDamageEvent event){if(event.getEntity()==player)lastDamage=event;}
    void capEffects()throws Exception {
        var effects=garden.bossEnchantEffects();near(effects.depthMultiplier(4),1.1,"Depth Strider IV runtime multiplier");near(effects.depthMultiplier(5),1.2,"Depth Strider V runtime multiplier");near(effects.swiftMultiplier(5),1.05,"Swift Sneak V overflow multiplier");
        near(((net.minecraft.world.entity.ai.attributes.RangedAttribute)net.minecraft.world.entity.ai.attributes.Attributes.WATER_MOVEMENT_EFFICIENCY.value()).getMaxValue(),1,"native water attribute ceiling");
        near(((net.minecraft.world.entity.ai.attributes.RangedAttribute)net.minecraft.world.entity.ai.attributes.Attributes.SNEAKING_SPEED.value()).getMaxValue(),1,"native sneaking attribute ceiling");
        player.getInventory().setArmorContents(new ItemStack[]{item(Material.DIAMOND_BOOTS,Enchantment.PROTECTION,8,true),item(Material.DIAMOND_LEGGINGS,Enchantment.PROTECTION,8,true),item(Material.DIAMOND_CHESTPLATE,Enchantment.PROTECTION,8,true),item(Material.DIAMOND_HELMET,Enchantment.PROTECTION,8,true)});
        player.getAttribute(Attribute.MAX_HEALTH).setBaseValue(1000);player.setHealth(1000);player.setNoDamageTicks(0);player.damage(100);
        check(lastDamage!=null,"actual damage event captured for full Protection VIII armor");
        double beforeMagic=lastDamage.getDamage();for(var modifier:EntityDamageEvent.DamageModifier.values())if(modifier!=EntityDamageEvent.DamageModifier.BASE&&modifier!=EntityDamageEvent.DamageModifier.MAGIC&&modifier!=EntityDamageEvent.DamageModifier.ABSORPTION&&lastDamage.isApplicable(modifier))beforeMagic+=lastDamage.getDamage(modifier);
        near(lastDamage.getFinalDamage(),beforeMagic*.2*Math.pow(.96,12),"actual Protection VIII full-set damage extends EPF 20 to EPF 32");
        var vanilla=new ItemStack[]{item(Material.DIAMOND_BOOTS,Enchantment.PROTECTION,4,false),item(Material.DIAMOND_LEGGINGS,Enchantment.PROTECTION,4,false),item(Material.DIAMOND_CHESTPLATE,Enchantment.PROTECTION,4,false),item(Material.DIAMOND_HELMET,Enchantment.PROTECTION,4,false)};player.getInventory().setArmorContents(vanilla);player.setNoDamageTicks(0);player.damage(100);
        near(lastDamage.getFinalDamage(),36,"ordinary Protection IV full-set damage remains vanilla");
        player.getInventory().setArmorContents(null);player.getInventory().setLeggings(item(Material.DIAMOND_LEGGINGS,Enchantment.SWIFT_SNEAK,5,true));player.setSneaking(true);effects.movement(player);
        near(player.getAttribute(Attribute.MOVEMENT_SPEED).getModifier(garden.key("boss_swift_speed")).getAmount(),.05,"actual Swift Sneak modifier applied");
        player.setSneaking(false);effects.movement(player);check(player.getAttribute(Attribute.MOVEMENT_SPEED).getModifier(garden.key("boss_swift_speed"))==null,"Swift modifier removed on standing");
        player.getInventory().setBoots(item(Material.DIAMOND_BOOTS,Enchantment.DEPTH_STRIDER,5,true));world.getBlockAt(12,100,12).setType(Material.WATER);actor.setPos(12.5,100,12.5);actor.tickCount++;var update=net.minecraft.world.entity.Entity.class.getDeclaredMethod("updateFluidInteraction");update.setAccessible(true);update.invoke(actor);effects.movement(player);
        check(player.isInWater(),"native player entered test water");near(player.getAttribute(Attribute.MOVEMENT_SPEED).getModifier(garden.key("boss_depth_speed")).getAmount(),.2,"actual Depth Strider V water modifier applied");
        player.getInventory().setBoots(null);effects.movement(player);check(player.getAttribute(Attribute.MOVEMENT_SPEED).getModifier(garden.key("boss_depth_speed"))==null,"water bonus removed when boots are removed");world.getBlockAt(12,100,12).setType(Material.AIR);
        player.getInventory().setArmorContents(null);
    }
    void restart()throws Exception {
        var saved=ItemStack.deserializeItemsFromBytes(Files.readAllBytes(Path.of("enchant-saved-items.bin")));check(saved.length>20,"all transferred high levels deserialize after a real restart");
        for(var gear:saved)for(var e:BossEnchantService.enchants(gear).entrySet())if(e.getValue()>e.getKey().getMaxLevel()){
            check(garden.bossEnchants().level(gear,e.getKey())==e.getValue(),"saved entitlement and native enchant agree: "+e.getKey()+" "+e.getValue());
            var output=result(menu(gear,item(Material.ENCHANTED_BOOK,e.getKey(),e.getValue(),true)));check(output!=null&&lvl(output,e.getKey())==e.getValue(),"restart cannot turn equal high levels into a free upgrade");
        }
    }
}
