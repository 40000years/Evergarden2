import com.example.advancemagic.AdvanceMagicPlugin;
import com.example.advancemagic.elemental.ElementalSpell;
import com.example.advancemagic.elemental.StaffSpellService;
import com.example.advancemagic.item.ElementalStaffService.Element;
import com.example.advancemagic.item.LegendaryWeaponService.Weapon;
import com.example.advancemagic.mana.ManaPotionService.Quality;
import com.example.voidscape.VoidscapePlugin;
import com.example.voidscape.boss.JudgePackService;
import com.example.voidscape.boss.SoulOrbs;
import com.example.voidscape.pet.*;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.server.network.*;
import org.bukkit.*;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.entity.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantInventory;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import java.lang.reflect.*;
import java.net.InetSocketAddress;
import java.nio.file.*;
import java.util.*;

/** Real Paper regression for the Seraph exchange, taming, companions, growth, persistence and breeding. */
public class PetSystemNativeProbe extends JavaPlugin {
    VoidscapePlugin garden;AdvanceMagicPlugin magic;World world;Location home;
    ServerPlayer actorA,actorB;Player tamer,rival;
    Object pets,companions,taming,breeding,store,bodies,renderer;
    final List<String> lines=new ArrayList<>();int checks,waited;boolean finished;
    final Map<UUID,List<Object>> seatsSent=new HashMap<>();

    void check(boolean value,String label){if(!value)throw new AssertionError(label);checks++;lines.add("PASS: "+label);getLogger().info("PASS: "+label);}
    void later(long ticks,Runnable work){Bukkit.getScheduler().runTaskLater(this,()->{try{work.run();}catch(Throwable error){finish(error);}},ticks);}
    static Object get(Object target,String name)throws Exception{
        for(Class<?> type=target.getClass();type!=null;type=type.getSuperclass())
            try{Field f=type.getDeclaredField(name);f.setAccessible(true);return f.get(target);}catch(NoSuchFieldException next){}
        throw new NoSuchFieldException(name);
    }
    static void put(Object target,String name,Object value)throws Exception{
        for(Class<?> type=target.getClass();type!=null;type=type.getSuperclass())
            try{Field f=type.getDeclaredField(name);f.setAccessible(true);f.set(target,value);return;}catch(NoSuchFieldException next){}
        throw new NoSuchFieldException(name);
    }
    static Object call(Object target,String name,Object... args)throws Exception{
        for(Method m:target.getClass().getDeclaredMethods())
            if(m.getName().equals(name)&&m.getParameterCount()==args.length){m.setAccessible(true);return m.invoke(target,args);}
        throw new NoSuchMethodException(name);
    }
    @Override public void onEnable(){later(1,()->{
        garden=(VoidscapePlugin)Bukkit.getPluginManager().getPlugin("Evergarden");
        magic=(AdvanceMagicPlugin)Bukkit.getPluginManager().getPlugin("advance-magic");
        check(garden!=null&&garden.isEnabled()&&magic!=null&&magic.isEnabled(),"Evergarden and Advance Magic enable together");
        check(garden.pets()!=null&&garden.seraphTrader()!=null,"pet system and Seraph trader registered");
        world=garden.world();awaitReady();
    });}
    void awaitReady(){
        if(garden.arkhaiaModels().ready()&&garden.judgePack().nativePetsIncluded()){prepare();return;}
        if((waited+=10)>1800)throw new AssertionError("native pet pack not ready");
        later(10,this::awaitReady);
    }
    ServerPlayer fake(Location at,String name){
        var server=MinecraftServer.getServer();var level=((CraftWorld)world).getHandle();
        var profile=new GameProfile(UUID.randomUUID(),name);var handle=new ServerPlayer(server,level,profile,ClientInformation.createDefault());
        var connection=new Connection(PacketFlow.SERVERBOUND);connection.channel=new EmbeddedChannel();connection.address=new InetSocketAddress("127.0.0.1",1);
        handle.connection=new ServerGamePacketListenerImpl(server,connection,handle,CommonListenerCookie.createInitial(profile,false)){
            @Override public boolean hasClientLoaded(){return true;}
            // Passenger packets are kept for the perch checks; nothing else is recorded.
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet){
                if(packet instanceof net.minecraft.network.protocol.game.ClientboundSetPassengersPacket)
                    seatsSent.computeIfAbsent(profile.id(),id->new ArrayList<>()).add(packet);
                super.send(packet);
            }
        };
        handle.setPos(at.getX(),at.getY(),at.getZ());server.getPlayerList().getPlayers().add(handle);
        server.getPlayerList().getPlayersByUUID().put(profile.id(),handle);level.addNewPlayer(handle);
        // Admin commands look players up by name.
        try{byName().put(name.toLowerCase(Locale.ROOT),handle);}catch(Exception error){throw new RuntimeException(error);}
        var bukkit=handle.getBukkitEntity();bukkit.setGravity(false);bukkit.setInvulnerable(true);bukkit.setGameMode(GameMode.SURVIVAL);return handle;
    }
    void platform(int cx,int cz,int radius){
        for(int x=cx-radius;x<=cx+radius;x++)for(int z=cz-radius;z<=cz+radius;z++){
            world.addPluginChunkTicket(x>>4,z>>4,this);world.getBlockAt(x,119,z).setType(Material.STONE,false);
            for(int y=120;y<=127;y++)world.getBlockAt(x,y,z).setType(Material.AIR,false);
        }
    }
    void prepare(){try{
        pets=garden.pets();companions=get(pets,"companions");taming=get(pets,"taming");breeding=get(pets,"breeding");
        store=get(pets,"store");bodies=get(pets,"bodies");renderer=get(pets,"renderer");
        garden.getConfig().set("pets.wild-spawns.chance-per-player",0.0);garden.wildPets().reload();
        home=new Location(world,512.5,120,512.5);platform(512,512,64);
        actorA=fake(home,"PetTamer");actorB=fake(home.clone().add(3,0,0),"PetRival");
        tamer=actorA.getBukkitEntity();rival=actorB.getBukkitEntity();
        for(Player p:List.of(tamer,rival))Bukkit.getPluginManager().callEvent(new PlayerResourcePackStatusEvent(p,JudgePackService.PACK_ID,PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED));
        world.addPluginChunkTicket(0,0,this);waited=0;awaitSeraph();
    }catch(Exception error){throw new RuntimeException(error);}}

    // ---- Seraph ----
    void awaitSeraph(){try{
        Object hitbox=get(garden.seraphTrader(),"hitbox");
        if(hitbox!=null&&((Entity)hitbox).isValid()){later(12,this::seraph);return;}
        if((waited+=10)>600)throw new AssertionError("Seraph never appeared at the Evergarden spawn: "+garden.seraphTrader().status());
        later(10,this::awaitSeraph);
    }catch(Exception error){throw new RuntimeException(error);}}
    Object shop(){return garden.seraphTrader().shop();}
    com.example.voidscape.trade.SeraphFeathers feathers(){return garden.seraphTrader().feathers();}
    @SuppressWarnings("unchecked") List<Object> offers(boolean legends)throws Exception{return (List<Object>)call(shop(),legends?"legendOffers":"magicOffers");}
    Object offer(List<Object> list,String id)throws Exception{for(Object o:list)if(id.equals(get(o,"id")))return o;throw new AssertionError("no offer "+id);}
    ItemStack first(Object offer)throws Exception{return (ItemStack)get(offer,"first");}
    ItemStack second(Object offer)throws Exception{return (ItemStack)get(offer,"second");}
    MerchantRecipe recipe(Object offer)throws Exception{
        @SuppressWarnings("unchecked") var result=(java.util.function.Supplier<ItemStack>)get(offer,"result");
        int special=(int)get(offer,"special");
        MerchantRecipe recipe=new MerchantRecipe(result.get(),0,9,false,0,0,0,special,special==0);
        recipe.addIngredient(first(offer));if(second(offer)!=null)recipe.addIngredient(second(offer));return recipe;
    }
    /** Runs the real vanilla matcher: exact components, as the trading screen uses. */
    boolean pays(MerchantRecipe recipe,ItemStack a,ItemStack b){
        var nms=org.bukkit.craftbukkit.inventory.CraftMerchantRecipe.fromBukkit(recipe).toMinecraft();
        return nms.satisfiedBy(org.bukkit.craftbukkit.inventory.CraftItemStack.asNMSCopy(a),
            b==null?net.minecraft.world.item.ItemStack.EMPTY:org.bukkit.craftbukkit.inventory.CraftItemStack.asNMSCopy(b));
    }
    ItemStack copy(ItemStack item,int amount){ItemStack c=item.clone();c.setAmount(amount);return c;}
    void seraph(){try{
        var trader=garden.seraphTrader();
        Interaction hitbox=(Interaction)get(trader,"hitbox");ItemDisplay java=(ItemDisplay)get(trader,"java");
        ArmorStand bedrock=(ArmorStand)get(trader,"bedrock");TextDisplay label=(TextDisplay)get(trader,"label");
        Location anchor=trader.anchor();
        check(hitbox.getLocation().distanceSquared(anchor)<.01,"Seraph stands at the configured Evergarden spawn spot");
        check(hitbox.getInteractionWidth()>=1.5f&&hitbox.getInteractionHeight()>=3f,"Seraph has a full-body click box");
        check(java.getItemStack().getItemMeta().getItemModel().getKey().startsWith("judge_seraph_"),"Java Seraph uses the approved winged model");
        check(bedrock.getEquipment().getHelmet().getItemMeta().getItemModel().getKey().startsWith("judge_seraph_"),"Bedrock Seraph wears the same model");
        check(!hitbox.isPersistent()&&!java.isPersistent()&&!bedrock.isPersistent()&&!label.isPersistent(),"Seraph parts are never saved into the world");
        tamer.teleport(anchor.clone().add(2,0,0));
        call(trader,"refreshViewers",new ArrayList<>(world.getPlayers()));
        check(tamer.canSee(java)&&!tamer.canSee(bedrock),"pack-ready Java player sees the Java Seraph only");
        var inv=tamer.getInventory();var f=feathers();
        // ---- earning ----
        inv.clear();
        check(f.blessing(tamer)&&f.carried(tamer)==5,"the first visit of the day brings the Seraph's blessing: 5 feathers");
        check(!f.blessing(tamer)&&f.carried(tamer)==5,"the blessing is once per day");
        check(f.grant(tamer,70,"test",true)==60&&f.earnedToday(tamer.getUniqueId())==60,"repeatable feathers stop at the daily cap of 60");
        check(f.grant(tamer,5,"test",true)==0,"nothing more from capped sources today");
        check(f.grant(tamer,15,"world boss",false)==15,"world bosses and one-time rewards ignore the cap");
        f.firstTime(tamer,"tame.frost_wyvern",5,"first");int before=f.carried(tamer);f.firstTime(tamer,"tame.frost_wyvern",5,"first");
        check(f.carried(tamer)==before,"a first-time reward is paid once ever");
        var block=world.getBlockAt(home.clone().add(0,0,-30));block.setType(Material.CHEST,false);
        var chest=(org.bukkit.block.Chest)block.getState();
        com.example.voidscape.trade.SeraphFeathers.treasure(garden,chest,"boss");
        int loot=Arrays.stream(chest.getBlockInventory().getContents()).filter(i->com.example.voidscape.trade.SeraphFeathers.isFeather(garden,i)).mapToInt(ItemStack::getAmount).sum();
        check(loot>=3&&loot<=5,"a boss-structure chest holds 3-5 feathers ("+loot+")");
        com.example.voidscape.trade.SeraphFeathers.treasure(garden,chest,"boss");
        check(Arrays.stream(chest.getBlockInventory().getContents()).filter(i->com.example.voidscape.trade.SeraphFeathers.isFeather(garden,i)).mapToInt(ItemStack::getAmount).sum()==loot,"each chest rolls its feathers only once");
        block.setType(Material.AIR,false);
        // ---- what the two traders offer (regular prices first) ----
        garden.getConfig().set("seraph-trader.promotions.enabled",false);
        var magicList=offers(false);var legendList=offers(true);
        Object fire=offer(magicList,"staff:fire:phlegethon"),fireAlt=offer(magicList,"staff:fire:acheron"),light=offer(magicList,"staff:light:styx");
        check(first(fire).getAmount()==40&&SoulOrbs.identify(garden,second(fire))==SoulOrbs.Orb.PHLEGETHON&&second(fire).getAmount()==7,"Fire staff: 40 feathers + 7 Phlegethon orbs");
        check(SoulOrbs.identify(garden,second(fireAlt))==SoulOrbs.Orb.ACHERON,"crimson Acheron orbs also buy the Fire staff");
        check(first(light).getAmount()==60&&second(light).getAmount()==9,"Light staff: 60 feathers + 9 Styx orbs");
        check(magicList.stream().filter(o->{try{return ((String)get(o,"id")).startsWith("scroll:");}catch(Exception e){return false;}}).count()==7,"seven scrolls rotate on offer today");
        Object core=offer(legendList,"legend:core:cocytus");
        check(com.example.voidscape.trade.SeraphFeathers.isGolden(garden,first(core))&&first(core).getAmount()==15&&second(core).getAmount()==3,"Core of Legend: 15 golden feathers + 3 orbs of one sanctuary");
        check(legendList.stream().filter(o->{try{return ((String)get(o,"id")).startsWith("legend:core:");}catch(Exception e){return false;}}).count()==6,"any of the six sanctuaries can pay for the Core");
        Object excalibur=offer(legendList,"weapon:excalibur");
        check(SoulOrbs.isLegendCore(garden,first(excalibur))&&first(excalibur).getAmount()==3,"legendary weapons cost 3 Cores of Legend");
        // ---- the vanilla trading screen only accepts the genuine articles ----
        MerchantRecipe staffRecipe=recipe(fire);
        check(pays(staffRecipe,f.feather(garden,40),copy(SoulOrbs.orb(garden,SoulOrbs.Orb.PHLEGETHON),7)),"genuine feathers and orbs pay for the staff");
        check(!pays(staffRecipe,f.feather(garden,40),copy(SoulOrbs.orb(garden,SoulOrbs.Orb.PHLEGETHON),6)),"six orbs are not enough at the regular price");
        check(!pays(staffRecipe,new ItemStack(Material.FEATHER,40),copy(SoulOrbs.orb(garden,SoulOrbs.Orb.PHLEGETHON),7)),"ordinary feathers cannot pay");
        check(!pays(staffRecipe,f.feather(garden,40),copy(SoulOrbs.orb(garden,SoulOrbs.Orb.COCYTUS),7)),"the wrong orb colour cannot pay");
        ItemStack renamed=f.feather(garden,40);var meta=renamed.getItemMeta();meta.displayName(net.kyori.adventure.text.Component.text("fake"));renamed.setItemMeta(meta);
        check(!pays(staffRecipe,renamed,copy(SoulOrbs.orb(garden,SoulOrbs.Orb.PHLEGETHON),7)),"a renamed feather no longer matches");
        check(!pays(staffRecipe,f.feather(garden,39),copy(SoulOrbs.orb(garden,SoulOrbs.Orb.PHLEGETHON),7)),"39 feathers are not enough");
        // ---- promotions ----
        garden.getConfig().set("seraph-trader.promotions.enabled",true);
        Object sale=null,coreSale=null;Set<String> others=new HashSet<>();
        for(long period=0;period<200&&(sale==null||coreSale==null);period++){
            Object promo=call(shop(),"promotion",period);
            if(period<3)others.add((String)get(promo,"other"));
            if(sale==null&&((Set<?>)get(promo,"staffs")).contains(Element.FIRE))sale=promo;
            if(coreSale==null&&"legend-core".equals(get(promo,"other")))coreSale=promo;
        }
        check(others.size()==3,"the second sale rotates scrolls, potions and Core of Legend");
        check(((Set<?>)get(call(shop(),"promotion",7L),"staffs")).size()==2,"each sale discounts two staff elements");
        @SuppressWarnings("unchecked") List<Object> saleList=(List<Object>)call(shop(),"magicOffers",sale);
        Object fireSale=offer(saleList,"staff:fire:phlegethon");
        check((int)get(fireSale,"special")==-12&&second(fireSale).getAmount()==4,"on sale the Fire staff costs 28 feathers (shown struck through) + 4 orbs");
        MerchantRecipe saleRecipe=recipe(fireSale);
        check(pays(saleRecipe,f.feather(garden,28),copy(SoulOrbs.orb(garden,SoulOrbs.Orb.PHLEGETHON),4)),"the discounted price really pays");
        check(!pays(saleRecipe,f.feather(garden,27),copy(SoulOrbs.orb(garden,SoulOrbs.Orb.PHLEGETHON),4)),"one feather under the sale price does not");
        Set<?> onSale=(Set<?>)get(sale,"staffs");
        for(Object o:saleList){String id=(String)get(o,"id");if(!id.startsWith("staff:"))continue;
            Element e=Element.parse(id.split(":")[1]);
            if(!onSale.contains(e))check((int)get(o,"special")==0,"staffs not on sale keep their price "+id);}
        @SuppressWarnings("unchecked") List<Object> coreList=(List<Object>)call(shop(),"legendOffers",coreSale);
        check((int)get(offer(coreList,"legend:core:styx"),"special")==-3,"a Core of Legend sale takes 3 golden feathers off");
        ItemStack staff=staffRecipe.getResult();
        check(!staff.getItemMeta().getPersistentDataContainer().has(new NamespacedKey("advance_magic","spell_staff_id")),"sold staffs carry no shared ID");
        // ---- opening a trader and its per-player stock ----
        call(shop(),"openTrader",tamer,false);
        check(tamer.getOpenInventory().getTopInventory() instanceof MerchantInventory,"the Seraph opens a villager-style trading screen");
        var merchant=((MerchantInventory)tamer.getOpenInventory().getTopInventory()).getMerchant();
        check(merchant.getRecipe(0).getMaxUses()==2,"stock shows the two staffs left this week");
        var purchase=new io.papermc.paper.event.player.PlayerPurchaseEvent(tamer,merchant,merchant.getRecipe(0),false,true);
        Bukkit.getPluginManager().callEvent(purchase);check(!purchase.isCancelled(),"first staff of the week goes through");
        purchase=new io.papermc.paper.event.player.PlayerPurchaseEvent(tamer,merchant,merchant.getRecipe(0),false,true);
        Bukkit.getPluginManager().callEvent(purchase);check(!purchase.isCancelled(),"second staff of the week goes through");
        purchase=new io.papermc.paper.event.player.PlayerPurchaseEvent(tamer,merchant,merchant.getRecipe(0),false,true);
        Bukkit.getPluginManager().callEvent(purchase);check(purchase.isCancelled(),"a third staff waits for next week, whichever element");
        inv.clear();inv.addItem(staff);
        later(2,()->{try{
            ItemStack minted=Arrays.stream(tamer.getInventory().getStorageContents()).filter(Objects::nonNull).findFirst().orElseThrow();
            check(minted.getItemMeta().getPersistentDataContainer().has(new NamespacedKey("advance_magic","spell_staff_id")),"a bought staff receives its own ID right after the trade");
            tamer.closeInventory();tamer.getInventory().clear();tamer.teleport(home);
            later(2,this::taming);
        }catch(Exception error){throw new RuntimeException(error);}});
    }catch(Exception error){throw new RuntimeException(error);}}

    // ---- taming ----
    @SuppressWarnings("unchecked") Map<UUID,Object> wildActive()throws Exception{return (Map<UUID,Object>)get(garden.wildPets(),"active");}
    Object stable(Player p)throws Exception{return call(companions,"stable",p);}
    @SuppressWarnings("unchecked") Map<UUID,PetRecord> records(Player p)throws Exception{return (Map<UUID,PetRecord>)get(stable(p),"pets");}
    Material food(PetCatalog.Species species)throws Exception{return (Material)call(taming,"food",species);}
    Object feedUntil(Object body,Player p,int limit)throws Exception{
        for(int i=0;i<limit&&get(body,"owner")==null;i++){put(body,"lastFeed",0L);call(taming,"feed",body,p);}
        return get(body,"owner");
    }
    static float wrap(float degrees){degrees%=360;if(degrees>=180)degrees-=360;if(degrees< -180)degrees+=360;return degrees;}
    @SuppressWarnings({"unchecked","rawtypes"}) void mode(Object body,String name)throws Exception{
        put(body,"mode",Enum.valueOf((Class)get(body,"mode").getClass(),name));
    }
    void alignedCarrier(Object body,String label)throws Exception{
        float yaw=(float)get(body,"yaw");Rabbit shell=(Rabbit)get(body,"shell");
        check(Math.abs(wrap(shell.getLocation().getYaw()-yaw))<.3,label+": stationary receiver follows body yaw");
        ArmorStand carrier=(ArmorStand)get(body,"carrier");
        if(carrier!=null)check(Math.abs(wrap(carrier.getLocation().getYaw()-yaw))<.3,label+": Bedrock carrier follows body yaw");
    }
    void wildAttention()throws Exception{
        Location at=home.clone().add(4,0,0);rival.teleport(home.clone().add(30,0,0));
        check(garden.wildPets().spawn(PetCatalog.Species.SHADOW_PANTHER,PetCatalog.Stage.ADULT,at),"wild panther spawns for the live attention check");
        Object body=wildActive().values().iterator().next();Rabbit shell=(Rabbit)get(body,"shell");UUID identity=shell.getUniqueId();
        put(body,"yaw",0f);put(body,"targetYaw",0f);call(body,"move",at);
        tamer.teleport(at.clone().add(-4,0,0));call(garden.wildPets(),"greet",body,tamer,false);
        put(body,"modeUntil",Integer.MAX_VALUE/4);int cooldown=(int)get(body,"greetReady");
        call(garden.wildPets(),"behave",body,tamer,16d);call(garden.wildPets(),"present",body);
        check((float)get(body,"headYaw")>10,"head leads a wild pet's body toward a player on its right");
        check((float)get(body,"headPitch")>0,"a ground pet looks up toward the player's eyes");
        alignedCarrier(body,"wild greet");
        for(int phase=0;phase<2;phase++){
            if(phase==1)mode(body,"IDLE");
            for(float heading:phase==0?new float[]{90,0,179}:new float[]{-179,-90,45}){
                double radians=Math.toRadians(heading);tamer.teleport(at.clone().add(-Math.sin(radians)*4,0,Math.cos(radians)*4));
                float previous=(float)get(body,"yaw");
                for(int step=0;step<12;step++){
                    call(garden.wildPets(),"behave",body,tamer,16d);call(garden.wildPets(),"present",body);
                    float current=(float)get(body,"yaw");
                    if(Math.abs(wrap(current-previous))>25.01)throw new AssertionError("wild attention snaps its body instead of turning");
                    previous=current;
                }
                float facing=(float)get(body,"yaw")+(float)get(body,"headYaw");
                check(Math.abs(wrap(heading-facing))<3,"same wild pet tracks a circling player "+heading+" degrees during "+(phase==0?"greet":"cooldown"));
                check(tamer.getUniqueId().equals(get(body,"attention")),"wild pet retains the same observer at "+heading+" degrees");
            }
        }
        check((int)get(body,"greetReady")==cooldown,"live attention keeps tracking without restarting the greeting cooldown");
        check(shell.getUniqueId().equals(identity)&&shell.getLocation().distanceSquared(at)<1e-6,"attention turns the same stationary pet without respawning or sliding");
        alignedCarrier(body,"wild cooldown");
        garden.wildPets().clear();tamer.teleport(home);rival.teleport(home.clone().add(3,0,0));
    }
    void taming(){try{
        wildAttention();
        check(garden.wildPets().spawn(PetCatalog.Species.FROST_WYVERN,PetCatalog.Stage.BABY,home.clone().add(4,0,0)),"wild baby wyvern spawns for taming");
        Object body=wildActive().values().iterator().next();
        tamer.getInventory().setItemInMainHand(new ItemStack(Material.DIRT,8));
        call(taming,"feed",body,tamer);check((int)get(body,"trust")==0,"ordinary items do not build trust");
        tamer.getInventory().setItemInMainHand(new ItemStack(food(PetCatalog.Species.FROST_WYVERN),64));
        put(body,"lastFeed",0L);call(taming,"feed",body,tamer);
        int first=(int)get(body,"trust");check(first>=18&&first<=30,"a baby gains 18-30 trust per favourite meal");
        check(tamer.getInventory().getItemInMainHand().getAmount()==63,"each meal consumes one item");
        check(tamer.getUniqueId().equals(get(body,"tamer")),"first feeder holds the taming lock");
        rival.getInventory().setItemInMainHand(new ItemStack(food(PetCatalog.Species.FROST_WYVERN),8));
        put(body,"lastFeed",0L);call(taming,"feed",body,rival);
        check((int)get(body,"trust")==first&&rival.getInventory().getItemInMainHand().getAmount()==8,"another player cannot feed or steal a locked pet");
        check(tamer.getUniqueId().equals(feedUntil(body,tamer,10)),"steady feeding tames a baby within ten meals");
        check(garden.wildPets().count()==0,"the tamed pet leaves the wild registry");
        var records=records(tamer);check(records.size()==1,"the stable records the new companion");
        PetRecord record=records.values().iterator().next();
        check(record.species()==PetCatalog.Species.FROST_WYVERN&&record.stage()==PetCatalog.Stage.BABY,"record keeps species and growth stage");
        check(record.id().equals(get(stable(tamer),"active"))&&(boolean)get(stable(tamer),"out"),"a first pet becomes the active companion at once");
        check(call(companions,"body",tamer.getUniqueId())==body,"the same body keeps walking: no despawn or flicker");
        Rabbit shell=(Rabbit)get(body,"shell");
        check(shell.getPersistentDataContainer().has(garden.key("pet_owner"))&&!shell.getPersistentDataContainer().has(garden.key("wild_pet")),"tamed body is marked as a companion");
        // Medium needs a Lead.
        check(garden.wildPets().spawn(PetCatalog.Species.CRIMSON_CERBERUS,PetCatalog.Stage.MEDIUM,home.clone().add(-50,0,0)),"wild medium cerberus spawns");
        Object medium=wildActive().values().iterator().next();
        rival.getInventory().setItemInMainHand(new ItemStack(food(PetCatalog.Species.CRIMSON_CERBERUS),64));
        for(int i=0;i<60&&(int)get(medium,"trust")<100;i++){put(medium,"lastFeed",0L);call(taming,"feed",medium,rival);}
        check((int)get(medium,"trust")==100&&get(medium,"owner")==null,"a trusting medium pet still waits for a Lead");
        rival.getInventory().addItem(new ItemStack(Material.LEAD,1));
        call(taming,"feed",medium,rival);
        check(rival.getUniqueId().equals(get(medium,"owner")),"with a Lead the medium pet comes home");
        check(!rival.getInventory().contains(Material.LEAD),"the Lead is used up");
        later(2,this::companionsTest);
    }catch(Exception error){throw new RuntimeException(error);}}

    // ---- companions ----
    Object body(Player p)throws Exception{return call(companions,"body",p.getUniqueId());}
    double distance(Player p)throws Exception{Object b=body(p);return ((Rabbit)get(b,"shell")).getLocation().distance(p.getLocation());}
    void follow(int steps)throws Exception{for(int i=0;i<steps;i++)call(companions,"follow");}
    void companionTurn(Object body)throws Exception{
        put(body,"yaw",0f);put(body,"targetYaw",0f);put(body,"speed",.32d);put(body,"airborne",false);put(body,"modeUntil",0);
        call(body,"move",home.clone());tamer.teleport(home.clone().add(0,0,-10));
        Location start=((Rabbit)get(body,"shell")).getLocation();call(companions,"follow",body,tamer,true);
        Location next=(Location)call(body,"destination");
        check(start.distanceSquared(next)<1e-6&&(double)get(body,"speed")<.001,"a companion stops its stride before turning toward an owner behind it");
        check(Math.abs(wrap((float)get(body,"yaw")))<=14.01&&(int)get(body,"stuck")==0,"a sharp companion turn is gradual and does not count as stuck");
        alignedCarrier(body,"companion turn");
        for(int i=0;i<32;i++){call(companions,"follow",body,tamer,true);call(body,"tickGlide");call(body,"tickGlide");}
        check(((Rabbit)get(body,"shell")).getLocation().distance(tamer.getLocation())<8,"the companion resumes walking after facing its new destination");
    }
    void companionsTest(){try{
        Object body=body(tamer);Rabbit shell=(Rabbit)get(body,"shell");
        companionTurn(body);
        tamer.teleport(home.clone().add(12,0,0));follow(1);
        double start=distance(tamer);follow(80);
        check(distance(tamer)<start-4&&distance(tamer)<6,"companion walks to its owner");
        check(world.getBlockAt(shell.getLocation()).getRelative(0,-1,0).getType().isSolid(),"a walking companion keeps to solid ground");
        tamer.teleport(home.clone().add(-40,0,30));follow(1);
        check(distance(tamer)<6,"a companion left far behind reappears beside its owner");
        // Per-viewer culling with an owned pet.
        call(bodies,"refresh");check(tamer.canSee(shell),"owner always sees their companion");
        garden.getConfig().set("pets.view.max-others",0);rival.teleport(tamer.getLocation().clone().add(2,0,0));call(bodies,"refresh");
        check(tamer.canSee(shell)&&!rival.canSee(shell),"cap of zero others still shows each owner their own pet");
        garden.getConfig().set("pets.view.max-others",6);call(bodies,"refresh");check(rival.canSee(shell),"others see nearby companions again");
        put(stable(rival),"hideOthers",true);call(bodies,"refresh");check(!rival.canSee(shell),"hide-others hides everyone else's companions");
        put(stable(rival),"hideOthers",false);
        // Bosses send companions away; they return afterwards.
        Slime boss=world.spawn(tamer.getLocation().clone().add(5,0,0),Slime.class,s->{
            s.getPersistentDataContainer().set(garden.key("world_boss_entity"),PersistentDataType.BYTE,(byte)1);s.addScoreboardTag("boss");s.setAI(false);});
        call(companions,"maintain");
        check(body(tamer)==null&&((String)call(companions,"state",tamer)).contains("บอส"),"companion steps away from a boss fight");
        boss.remove();((Map<?,?>)get(companions,"bossSeen")).clear();call(companions,"maintain");
        check(body(tamer)!=null,"companion returns after the fight");
        @SuppressWarnings("unchecked") Map<UUID,Long> moved=(Map<UUID,Long>)get(companions,"moved");
        moved.put(tamer.getUniqueId(),System.currentTimeMillis()-6*60_000L);call(companions,"maintain");
        check(body(tamer)==null&&((String)call(companions,"state",tamer)).contains("AFK"),"AFK owners leave no companion walking around");
        moved.put(tamer.getUniqueId(),System.currentTimeMillis());call(companions,"maintain");
        check(body(tamer)!=null,"companion comes back when the owner moves");
        // Feeding and growth.
        PetRecord record=records(tamer).values().iterator().next();
        int growth=record.growth(),bond=record.bond();
        tamer.getInventory().setItemInMainHand(new ItemStack(food(PetCatalog.Species.FROST_WYVERN),4));
        call(companions,"interact",body(tamer),tamer);
        check(record.growth()==growth+5&&record.bond()==bond+2,"feeding a companion grows it and deepens the bond");
        call(companions,"interact",body(tamer),tamer);
        check(record.growth()==growth+5,"a fed companion stays full for a while");
        put(record,"growth",(int)call(companions,"threshold",PetCatalog.Stage.MEDIUM)-1);
        call(companions,"grow");
        check(record.stage()==PetCatalog.Stage.MEDIUM,"a minute together pushes the baby into its medium form");
        check(get(body(tamer),"stage")==PetCatalog.Stage.MEDIUM,"the companion body is rebuilt with the medium model");
        // Dismiss and summon.
        call(companions,"dismiss",tamer);check(body(tamer)==null&&!(boolean)get(stable(tamer),"out"),"dismiss returns the companion to the stable");
        check(call(companions,"summon",tamer,record.id())==null&&body(tamer)!=null,"summon brings it back beside the owner");
        later(2,this::persistence);
    }catch(Exception error){throw new RuntimeException(error);}}

    // ---- persistence ----
    void persistence(){try{
        put(stable(tamer),"dirty",true);call(store,"flush");
        later(20,()->{try{
            Path file=garden.getDataFolder().toPath().resolve("pets/players/"+tamer.getUniqueId()+".yml");
            check(Files.isRegularFile(file),"stable saved to its own file off the server thread");
            YamlConfiguration yaml=YamlConfiguration.loadConfiguration(file.toFile());
            PetRecord record=records(tamer).values().iterator().next();
            check(record.id().toString().equals(yaml.getString("active"))&&yaml.getBoolean("out"),"active companion persisted");
            check("frost_wyvern".equals(yaml.getString("pets."+record.id()+".species"))&&"medium".equals(yaml.getString("pets."+record.id()+".stage")),"species and grown stage persisted");
            Method load=Class.forName("com.example.voidscape.pet.PetStable").getDeclaredMethod("load",UUID.class,YamlConfiguration.class,int.class);load.setAccessible(true);
            Object copy=load.invoke(null,tamer.getUniqueId(),yaml,6);
            @SuppressWarnings("unchecked") Map<UUID,PetRecord> loaded=(Map<UUID,PetRecord>)get(copy,"pets");
            PetRecord again=loaded.get(record.id());
            check(again!=null&&again.growth()==record.growth()&&again.bond()==record.bond(),"growth and bond reload exactly");
            later(2,this::breedingTest);
        }catch(Exception error){throw new RuntimeException(error);}});
    }catch(Exception error){throw new RuntimeException(error);}}

    // ---- breeding ----
    void breedingTest(){try{
        var console=Bukkit.getConsoleSender();
        for(int i=0;i<2;i++)garden.pets().command(console,new String[]{"pets","give",tamer.getName(),"shadow_panther","adult"});
        var adults=records(tamer).values().stream().filter(r->r.species()==PetCatalog.Species.SHADOW_PANTHER).toList();
        check(adults.size()==2,"two adult panthers in the stable");
        UUID a=adults.get(0).id(),b=adults.get(1).id();
        Material food=food(PetCatalog.Species.SHADOW_PANTHER);
        tamer.getInventory().clear();tamer.getInventory().addItem(new ItemStack(food,7));
        check(call(breeding,"start",tamer,a,b)!=null,"breeding needs the full favourite-food cost");
        tamer.getInventory().addItem(new ItemStack(food,1));
        check(call(breeding,"start",tamer,a,b)==null,"two bonded adults of one species start nesting");
        check(!tamer.getInventory().contains(food),"the nest consumed eight favourite meals");
        Object stable=stable(tamer);
        check(call(breeding,"collect",tamer)!=null,"the egg is not ready immediately");
        put(stable,"nestReady",System.currentTimeMillis()-1);
        check(call(breeding,"collect",tamer)==null,"a ready nest yields an egg");
        ItemStack egg=Arrays.stream(tamer.getInventory().getStorageContents()).filter(i->{try{return call(breeding,"eggId",i)!=null;}catch(Exception e){return false;}}).findFirst().orElseThrow();
        String id=(String)call(breeding,"eggId",egg);
        YamlConfiguration ledger=(YamlConfiguration)get(store,"eggs");
        check(ledger.isConfigurationSection("eggs."+id)&&!ledger.getBoolean("eggs."+id+".hatched"),"egg identity lives in the server ledger");
        check(PetBreeding_EGG_MODEL().equals(egg.getItemMeta().getItemModel()),"egg uses its own item model");
        check(adults.stream().allMatch(r->{try{return (long)get(r,"bred")>0;}catch(Exception e){return false;}}),"both parents start their breeding cooldown");
        check(get(stable,"nestA")==null,"collecting empties the nest");
        Object again=call(breeding,"check",stable,records(tamer).get(a),records(tamer).get(b),System.currentTimeMillis());
        check(again!=null,"parents cannot breed again during their cooldown");
        for(int i=0;i<41;i++)call(breeding,"incubate");
        ItemStack warmed=Arrays.stream(tamer.getInventory().getStorageContents()).filter(i->{try{return id.equals(call(breeding,"eggId",i));}catch(Exception e){return false;}}).findFirst().orElseThrow();
        check(warmed.getItemMeta().getPersistentDataContainer().getOrDefault(garden.key("pet_egg_percent"),PersistentDataType.INTEGER,0)==100,"a carried egg finishes incubating");
        ItemStack copy=warmed.clone();
        int before=records(tamer).size();
        tamer.getInventory().setItemInMainHand(warmed);
        Bukkit.getPluginManager().callEvent(new PlayerInteractEvent(tamer,Action.RIGHT_CLICK_AIR,warmed,null,BlockFace.SELF,EquipmentSlot.HAND));
        check(records(tamer).size()==before+1,"right-clicking a ready egg hatches a baby into the stable");
        PetRecord child=records(tamer).values().stream().filter(r->r.stage()==PetCatalog.Stage.BABY&&r.species()==PetCatalog.Species.SHADOW_PANTHER).findFirst().orElseThrow();
        check((int)get(child,"generation")==1&&((List<?>)get(child,"parents")).containsAll(List.of(a,b)),"offspring records its generation and parents");
        float size=(float)get(child,"size");check(size>=.85f&&size<=1.15f,"offspring size stays within the inherited range");
        check(ledger.getBoolean("eggs."+id+".hatched"),"the ledger marks the egg spent before the pet is added");
        tamer.getInventory().setItemInMainHand(copy);
        Bukkit.getPluginManager().callEvent(new PlayerInteractEvent(tamer,Action.RIGHT_CLICK_AIR,copy,null,BlockFace.SELF,EquipmentSlot.HAND));
        check(records(tamer).size()==before+1,"a duplicated egg item cannot hatch a second pet");
        later(2,this::menuBreedingTest);
    }catch(Exception error){throw new RuntimeException(error);}}
    static NamespacedKey PetBreeding_EGG_MODEL(){return new NamespacedKey("voidscape","pet_egg");}

    // ---- the nest through the /pet menu, the admin rush, and hatching from the menu or the off hand ----
    @SuppressWarnings("unchecked") Map<Integer,Runnable> actions()throws Exception{
        Object holder=tamer.getOpenInventory().getTopInventory().getHolder();
        if(holder==null||!holder.getClass().getSimpleName().equals("Holder"))throw new AssertionError("no /pet menu page is open");
        return (Map<Integer,Runnable>)get(holder,"actions");
    }
    String title(int slot){
        ItemStack item=tamer.getOpenInventory().getTopInventory().getItem(slot);
        return item==null||!item.hasItemMeta()||item.getItemMeta().displayName()==null?""
            :net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(item.getItemMeta().displayName());
    }
    List<?> carried()throws Exception{return (List<?>)call(breeding,"carried",tamer);}
    void menuBreedingTest(){try{
        var console=Bukkit.getConsoleSender();Object menu=get(pets,"menu");Object stable=stable(tamer);
        put(stable,"slots",12);
        for(int i=0;i<2;i++)garden.pets().command(console,new String[]{"pets","give",tamer.getName(),"crystal_tortoise","adult"});
        List<UUID> pair=records(tamer).values().stream().filter(r->r.species()==PetCatalog.Species.CRYSTAL_TORTOISE).map(PetRecord::id).toList();
        check(pair.size()==2,"two adult tortoises join the stable for the menu flow");
        Material seagrass=food(PetCatalog.Species.CRYSTAL_TORTOISE);
        tamer.getInventory().clear();tamer.getInventory().addItem(new ItemStack(seagrass,8));
        call(menu,"chestNest",tamer);
        // Pickable pets are listed first, so the two tortoises take the first candidate slot in turn.
        actions().get(28).run();actions().get(28).run();
        @SuppressWarnings("unchecked") UUID[] draft=((Map<UUID,UUID[]>)get(menu,"drafts")).get(tamer.getUniqueId());
        check(draft!=null&&draft[0]!=null&&draft[1]!=null&&Set.of(draft[0],draft[1]).equals(Set.copyOf(pair)),"clicking two candidates in the nest page pairs the tortoises");
        check(title(13).equals("เริ่มผสมพันธุ์")&&actions().containsKey(13),"with every condition met the nest's start button is live ("+title(13)+")");
        // A real click goes through the menu listener, which runs the button on the next tick.
        Bukkit.getPluginManager().callEvent(new org.bukkit.event.inventory.InventoryClickEvent(tamer.getOpenInventory(),
            org.bukkit.event.inventory.InventoryType.SlotType.CONTAINER,13,org.bukkit.event.inventory.ClickType.LEFT,org.bukkit.event.inventory.InventoryAction.PICKUP_ALL));
        later(2,()->{try{
            check(pair.contains((UUID)get(stable,"nestA"))&&pair.contains((UUID)get(stable,"nestB")),"clicking start in the menu puts the pair in the nest");
            check(!tamer.getInventory().contains(seagrass),"the menu's nest took the eight meals");
            check(title(13).equals("กำลังฟูมฟัก"),"the nest page shows the egg is still on its way ("+title(13)+")");
            garden.pets().command(console,new String[]{"pets","rush",tamer.getName()});
            call(menu,"chestNest",tamer);
            check(title(13).equals("คลิกเก็บไข่!")&&actions().containsKey(13),"after the admin rush the nest page offers the egg");
            actions().get(13).run();
            check(carried().size()==1&&get(stable,"nestA")==null,"collecting from the menu gives the egg and empties the nest");
            call(menu,"chestNest",tamer);
            check(title(4).equals("ไข่ที่กำลังฟัก")&&!actions().containsKey(4),"the nest page lists the carried egg while it incubates");
            garden.pets().command(console,new String[]{"pets","rush",tamer.getName()});
            call(menu,"chestNest",tamer);
            check(title(4).equals("ฟักไข่!")&&actions().containsKey(4),"a ready egg can be hatched from the nest page");
            int before=records(tamer).size();
            put(stable,"locked",true);actions().get(4).run();
            check(records(tamer).size()==before&&carried().size()==1,"a stable whose file could not be read refuses the hatch and keeps the egg");
            put(stable,"locked",false);
            call(menu,"chestNest",tamer);actions().get(4).run();
            check(records(tamer).size()==before+1&&records(tamer).values().stream().anyMatch(r->r.species()==PetCatalog.Species.CRYSTAL_TORTOISE&&r.stage()==PetCatalog.Stage.BABY),
                "hatching from the menu adds a baby tortoise");
            check(carried().isEmpty(),"the hatched egg is used up");
            garden.pets().command(console,new String[]{"pets","egg",tamer.getName(),"crystal_tortoise"});
            ItemStack ready=Arrays.stream(tamer.getInventory().getStorageContents()).filter(i->{try{return call(breeding,"eggId",i)!=null;}catch(Exception e){return false;}}).findFirst().orElseThrow();
            tamer.getInventory().remove(ready);tamer.getInventory().setItemInOffHand(ready);tamer.getInventory().setItemInMainHand(null);
            Bukkit.getPluginManager().callEvent(new PlayerInteractEvent(tamer,Action.RIGHT_CLICK_AIR,ready,null,BlockFace.SELF,EquipmentSlot.OFF_HAND));
            check(records(tamer).size()==before+2,"a ready egg in the off hand hatches with a right-click");
            tamer.closeInventory();
            later(2,this::modesTest);
        }catch(Exception error){throw new RuntimeException(error);}});
    }catch(Exception error){throw new RuntimeException(error);}}

    // ---- modes, skills, assist combat, fainting and renaming ----
    /** A still zombie tagged with the live dungeon run, the only mobs Evergarden lets spawn. */
    Zombie foe(Location at)throws Exception{
        Object dungeons=null;
        for(Field f:garden.getClass().getDeclaredFields())if(f.getType().getSimpleName().equals("DungeonManager")){f.setAccessible(true);dungeons=f.get(garden);}
        String run=(String)get(dungeons,"runId");
        return world.spawn(at,Zombie.class,org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.CUSTOM,z->{
            z.getPersistentDataContainer().set(garden.key("runtime"),PersistentDataType.STRING,run);
            z.setAI(false);z.setSilent(true);z.setPersistent(false);z.setShouldBurnInDay(false);
            z.getEquipment().clear();});
    }
    /** Passenger packets the server sent to a test player since the last call. */
    List<Object> drain(ServerPlayer actor){
        List<Object> sent=seatsSent.remove(actor.getUUID());
        return sent==null?List.of():sent;
    }
    static boolean seats(Object packet,int vehicle,int[] parts){
        if(!(packet instanceof net.minecraft.network.protocol.game.ClientboundSetPassengersPacket seat)||seat.getVehicle()!=vehicle)return false;
        Set<Integer> riders=new HashSet<>();for(int id:seat.getPassengers())riders.add(id);
        return Arrays.stream(parts).allMatch(riders::contains);
    }
    static Object skills(String name,PetRecord record)throws Exception{
        Method m=Class.forName("com.example.voidscape.pet.PetSkills").getDeclaredMethod(name,PetRecord.class);m.setAccessible(true);return m.invoke(null,record);
    }
    void modesTest(){try{
        Object menu=get(pets,"menu");
        tamer.getInventory().clear();tamer.teleport(home);
        PetRecord cat=records(tamer).values().stream().filter(r->r.species()==PetCatalog.Species.SHADOW_PANTHER&&r.stage()==PetCatalog.Stage.ADULT).findFirst().orElseThrow();
        UUID id=cat.id();
        check(call(companions,"summon",tamer,id)==null&&body(tamer)!=null,"an adult panther comes out for the mode checks");
        @SuppressWarnings("unchecked") List<String> card=(List<String>)call(menu,"describe",stable(tamer),cat);
        check(card.stream().anyMatch(l->l.contains("ฝีเท้าเงา"))&&card.stream().anyMatch(l->l.contains("ซุ่มโจมตี")),"the pet card lists its passive and battle skills");
        check(card.stream().anyMatch(l->l.contains("โหมด: ติดตาม")&&l.contains("❤")),"the pet card shows its mode and health");
        // Perch: a small body on the head, moved every tick, never a passenger.
        check(call(companions,"mode",tamer,id,"perch")==null,"perch mode is accepted");
        Object perched=body(tamer);
        check(perched!=null&&(boolean)get(perched,"perched"),"perching rebuilds a small head-sitting body");
        tamer.teleport(home.clone().add(6,0,4));follow(2);
        Location head=tamer.getLocation().add(0,tamer.getHeight(),0);
        Rabbit receiver=(Rabbit)get(perched,"shell");
        check(receiver.getLocation().distance(head)<.1,"a perched pet sits on its owner's head as they move");
        NativePetRenderer.Rig perchRig=(NativePetRenderer.Rig)get(perched,"rig");
        check(!perchRig.riding()&&perchRig.displays().stream().allMatch(d->d.getLocation().distance(head)<.1),"perched parts follow the head without mounting anything");
        check(tamer.getPassengers().isEmpty(),"the owner carries no passengers, so cross-world teleports stay allowed");
        drain(actorA);
        call(bodies,"refresh");
        check(!tamer.canSee(receiver)&&tamer.canSee(perchRig.displays().get(0)),"a perched receiver is never shown, so it cannot block the owner's clicks");
        // Each viewer's client carries the parts on the owner's head; the server never mounts them.
        int[] partIds=perchRig.displays().stream().mapToInt(Entity::getEntityId).toArray();
        List<Object> shown=drain(actorA);
        check(perchRig.carried()&&shown.stream().anyMatch(p->seats(p,tamer.getEntityId(),partIds)),
            "showing a perched pet seats every part on its owner's head on that player's own client (sent "+shown.size()+": "
            +shown.stream().map(p->p.getClass().getSimpleName()+(p instanceof net.minecraft.network.protocol.game.ClientboundSetPassengersPacket s?"["+s.getVehicle()+"->"+Arrays.toString(s.getPassengers())+"]":"")).distinct().limit(40).toList()
            +"; owner "+tamer.getEntityId()+" parts "+Arrays.toString(partIds)+" carried "+perchRig.carried()+")");
        follow(25);
        check(drain(actorA).stream().anyMatch(p->seats(p,tamer.getEntityId(),partIds)),"the client-side seat is refreshed about once a second");
        tamer.teleport(home.clone().add(9.5,0,4));follow(1);
        Location moved=tamer.getLocation().add(0,tamer.getHeight(),0);
        check(perchRig.displays().stream().allMatch(d->d.getLocation().distance(moved)<.1)&&tamer.getPassengers().isEmpty(),
            "parts that drift from the head are brought back on the server, still without a passenger");
        World other=Bukkit.getWorlds().stream().filter(w->w!=world).findFirst().orElseThrow();
        check(tamer.teleport(other.getSpawnLocation())&&tamer.getWorld()==other,"a player with a perched pet can still teleport to another world");
        check(tamer.teleport(home)&&tamer.getWorld()==world,"and back again");
        call(companions,"maintain");perched=body(tamer);
        check(perched!=null&&(boolean)get(perched,"perched"),"the perched pet comes back onto its owner's head after the trip");
        call(bodies,"refresh");
        // Stay: holds its place, then follows past the leash.
        call(companions,"mode",tamer,id,"stay");
        Object staying=body(tamer);
        check(staying!=perched&&!(boolean)get(staying,"perched"),"leaving the head rebuilds a full-size body on the ground");
        Location spot=((Rabbit)get(staying,"shell")).getLocation();
        tamer.teleport(home.clone().add(20,0,0));follow(40);
        check(((Rabbit)get(staying,"shell")).getLocation().distance(spot)<.01,"a pet on stay holds its place while its owner walks away");
        tamer.teleport(home.clone().add(-60,0,30));follow(3);
        check("follow".equals(get(cat,"mode")),"far beyond the stay leash it switches back to follow");
        check(distance(tamer)<8,"and catches up with its owner");
        // Assist: the owner's foe, then a mob hunting the owner; kills credit the owner.
        tamer.teleport(home);call(companions,"mode",tamer,id,"assist");follow(4);
        garden.getConfig().set("pets.companions.counter-chance",0);
        Zombie marked=foe(home.clone().add(3,0,2));
        check(marked.isValid(),"a test zombie spawns under the dungeon run");
        marked.damage(1,tamer);
        check(get(body(tamer),"target")==marked,"the owner's hit marks the foe for the companion");
        marked.remove();follow(2);
        check(get(body(tamer),"target")==null,"a vanished foe is dropped");
        // Mobs refuse invulnerable targets; this still zombie cannot attack anyway.
        tamer.setInvulnerable(false);
        Zombie hunter=foe(home.clone().add(-3,0,2));hunter.setTarget(tamer);
        double before=hunter.getHealth();
        for(int i=0;i<20&&get(body(tamer),"target")==null;i++)follow(1);
        check(get(body(tamer),"target")==hunter,"a mob hunting the owner is found (hunter target: "+hunter.getTarget()+")");
        for(int i=0;i<120&&hunter.getHealth()>=before;i++)follow(1);
        Object fighter=body(tamer);
        check(hunter.getHealth()<before,"the companion reaches and bites it (distance "+((Rabbit)get(fighter,"shell")).getLocation().distance(hunter.getLocation())
            +", yaw "+get(fighter,"yaw")+"/"+get(fighter,"targetYaw")+", stuck "+get(fighter,"stuck")+", next "+get(fighter,"nextStrike")+", target "+get(fighter,"target")+")");
        for(int i=0;i<300&&!hunter.isDead();i++)follow(1);
        check(hunter.isDead(),"the companion bites the foe down on its own");
        check(tamer.equals(hunter.getKiller()),"its kill counts as the owner's, so loot and XP drop");
        tamer.setInvulnerable(true);
        // Counter-hits knock it out for one in-game day.
        garden.getConfig().set("pets.companions.counter-chance",100);
        Zombie brute=foe(home.clone().add(0,0,3));
        put(cat,"health",1d);put(cat,"combatTick",(long)Bukkit.getCurrentTick());
        brute.damage(.5,tamer);
        for(int i=0;i<200&&body(tamer)!=null;i++)follow(1);
        check(body(tamer)==null,"a companion at 1 health that is hit back faints");
        long now=garden.world().getGameTime();
        check((long)get(cat,"faintedUntil")>=now+23_900,"it rests for one in-game day");
        check(String.valueOf(call(companions,"summon",tamer,id)).contains("สลบ"),"a fainted pet cannot be summoned");
        call(companions,"maintain");
        check(body(tamer)==null&&((String)call(companions,"state",tamer)).contains("สลบ"),"upkeep keeps it resting and says why");
        YamlConfiguration saved=new YamlConfiguration();call(cat,"save",saved.createSection("p"));
        Method load=PetRecord.class.getDeclaredMethod("load",UUID.class,ConfigurationSection.class);load.setAccessible(true);
        PetRecord copy=(PetRecord)load.invoke(null,id,saved.getConfigurationSection("p"));
        check("assist".equals(get(copy,"mode"))&&(long)get(copy,"faintedUntil")==(long)get(cat,"faintedUntil"),"mode and rest time survive a restart");
        brute.remove();
        put(cat,"faintedUntil",now-1);call(companions,"maintain");
        check(body(tamer)!=null&&(long)get(cat,"faintedUntil")==0,"after its rest it returns by itself");
        check(Math.abs((double)call(companions,"health",cat)-(double)skills("maxHealth",cat))<1e-6,"and comes back at full health");
        // Passive skill.
        tamer.removePotionEffect(org.bukkit.potion.PotionEffectType.SPEED);call(companions,"maintain");
        check(tamer.hasPotionEffect(org.bukkit.potion.PotionEffectType.SPEED),"the panther's passive gives its owner Speed");
        // Renaming without chat: sign editor, Name Tag, command.
        call(menu,"promptRename",tamer,id);
        Map<?,?> signs=(Map<?,?>)get(menu,"signs");Object prompt=signs.get(tamer.getUniqueId());
        check(prompt!=null,"renaming opens a sign editor instead of waiting for chat");
        Location block=(Location)call(prompt,"block");
        Bukkit.getPluginManager().callEvent(new io.papermc.paper.event.packet.UncheckedSignChangeEvent(tamer,
            io.papermc.paper.math.Position.block(block.getBlockX()+1,block.getBlockY(),block.getBlockZ()),org.bukkit.block.sign.Side.FRONT,
            List.of(net.kyori.adventure.text.Component.text("Nope"))));
        check(!"Nope".equals(cat.name())&&signs.containsKey(tamer.getUniqueId()),"edits of any other sign are ignored");
        var typed=new io.papermc.paper.event.packet.UncheckedSignChangeEvent(tamer,io.papermc.paper.math.Position.block(block),org.bukkit.block.sign.Side.FRONT,
            List.of(net.kyori.adventure.text.Component.text("Shadow"),net.kyori.adventure.text.Component.text("Fang"),
                net.kyori.adventure.text.Component.text("^ ชื่อสัตว์เลี้ยง ^"),net.kyori.adventure.text.Component.text("x")));
        Bukkit.getPluginManager().callEvent(typed);
        check("Shadow Fang".equals(cat.name())&&typed.isCancelled()&&signs.isEmpty(),"the typed sign lines name the pet");
        ItemStack tag=new ItemStack(Material.NAME_TAG,2);var meta=tag.getItemMeta();meta.displayName(net.kyori.adventure.text.Component.text("Kuro"));tag.setItemMeta(meta);
        tamer.getInventory().setItemInMainHand(tag);call(companions,"interact",body(tamer),tamer);
        check("Kuro".equals(cat.name())&&tamer.getInventory().getItemInMainHand().getAmount()==1,"a named Name Tag renames the pet and is used up");
        ((PetMenu)menu).onCommand(tamer,null,"pet",new String[]{"name","Mochi","Cat"});
        check("Mochi Cat".equals(cat.name()),"/pet name renames the active pet");
        ((PetMenu)menu).onCommand(tamer,null,"pet",new String[]{"mode","stay"});
        check("stay".equals(get(cat,"mode")),"/pet mode switches the active pet's mode");
        garden.getConfig().set("pets.companions.counter-chance",35);
        call(companions,"mode",tamer,id,"follow");
        later(2,this::holdPose);
    }catch(Exception error){throw new RuntimeException(error);}}

    // ---- a held pose costs nothing ----
    Rabbit fixture;NativePetRenderer.Rig rig;long settled;int ticks;
    void holdPose(){try{
        call(companions,"dismiss",tamer);call(companions,"dismiss",rival);
        fixture=world.spawn(home.clone().add(0,0,-20),Rabbit.class,org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.CUSTOM,r->{
            r.getPersistentDataContainer().set(garden.key("pet_body"),PersistentDataType.BYTE,(byte)1);r.setAI(false);r.setGravity(false);});
        rig=((NativePetRenderer)renderer).attach(fixture,PetCatalog.Species.SHADOW_PANTHER,PetCatalog.Stage.ADULT);
        check(rig!=null,"standalone rig attaches for the pose check");
        Bukkit.getScheduler().runTaskTimer(this,task->{
            try{
                rig.tick("sit",0);ticks++;
                if(ticks==60)settled=((NativePetRenderer)renderer).transformsSent();
                if(ticks==100){
                    task.cancel();
                    check(((NativePetRenderer)renderer).transformsSent()==settled,"a finished sit pose sends no further transforms");
                    rig.close();fixture.remove();finish(null);
                }
            }catch(Throwable error){task.cancel();finish(error);}
        },1,1);
    }catch(Exception error){throw new RuntimeException(error);}}

    @SuppressWarnings("unchecked") static Map<String,ServerPlayer> byName()throws Exception{
        Field f=net.minecraft.server.players.PlayerList.class.getDeclaredField("playersByName");f.setAccessible(true);
        return (Map<String,ServerPlayer>)f.get(MinecraftServer.getServer().getPlayerList());
    }
    void discard(ServerPlayer actor){
        if(actor==null)return;var server=MinecraftServer.getServer();server.getPlayerList().getPlayers().remove(actor);server.getPlayerList().getPlayersByUUID().remove(actor.getUUID());
        try{byName().values().remove(actor);}catch(Exception ignored){}
        actor.discard();
    }
    String suiteName(){return "pet system and Seraph";}
    void finish(Throwable error){
        if(finished)return;finished=true;if(error!=null){error.printStackTrace();lines.add("ERROR: "+error);}
        discard(actorA);discard(actorB);
        try{Files.writeString(Path.of("model-engine-result.txt"),(error==null?"PASS":"FAIL")+": "+suiteName()+" / "+checks+" checks\n"+String.join("\n",lines)+"\n");}catch(Exception failure){failure.printStackTrace();}
        Bukkit.shutdown();
    }
}
