import com.example.advancemagic.mana.ManaPotionService;
import com.example.advancemagic.mana.ManaPotionService.Quality;
import io.papermc.paper.event.player.PlayerDeepSleepEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import org.bukkit.*;
import org.bukkit.attribute.*;
import org.bukkit.block.BrewingStand;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.*;
import java.util.*;

/** Real brewing stand ticks, vanilla bottle consumption and persisted progression on a Paper player. */
final class ManaPotionChecks implements Listener {
    final BasicCastingChecks probe;
    boolean cancelConsume,cancelBrew,cancelSleep;
    int brewEvents;
    ManaPotionChecks(BasicCastingChecks probe){this.probe=probe;}
    @EventHandler(priority=EventPriority.HIGH) public void consume(PlayerItemConsumeEvent event){if(cancelConsume)event.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGH) public void brew(BrewEvent event){brewEvents++;if(cancelBrew)event.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGH) public void sleep(PlayerDeepSleepEvent event){if(cancelSleep)event.setCancelled(true);}
    void check(boolean value,String label){probe.check(value,"mana potions: "+label);}
    static void run(BasicCastingChecks probe){new ManaPotionChecks(probe).run();}
    ItemStack vanilla(PotionType type){ItemStack item=new ItemStack(Material.POTION);PotionMeta meta=(PotionMeta)item.getItemMeta();meta.setBasePotionType(type);item.setItemMeta(meta);return item;}
    void run(){
        Bukkit.getPluginManager().registerEvents(this,probe);
        Player player=probe.caster;var magic=probe.magic;var service=magic.manaPotions();
        var account=magic.mana().account(player);var health=player.getAttribute(Attribute.MAX_HEALTH);
        account.setMaxMana(100);account.setMana(0);
        var everyDraw=new ManaPotionService(magic,new Random(){int draw;@Override public int nextInt(int bound){return draw++;}});
        int[] counts=new int[4];for(int draw=0;draw<100;draw++)counts[everyDraw.rollQuality().ordinal()]++;
        check(Arrays.equals(counts,new int[]{60,30,8,2}),"exact quality odds are 60/30/8/2 across all random outcomes");
        for(Quality quality:Quality.values()){
            var potion=service.create(quality);
            check(service.quality(potion)==quality,"quality tag survives an item clone "+quality);
            check(service.quality(potion.clone())==quality,"cloned quality "+quality);
            check(((PotionMeta)potion.getItemMeta()).getColor()!=null,"vanilla potion tint "+quality);
            var current=(PotionMeta)potion.getItemMeta();
            check(current.getItemModel().toString().equals("advance_magic:mana_potion_"+quality.name().toLowerCase(Locale.ROOT)),"shared client bottle model "+quality);
            check(current.lore().size()<=(quality==Quality.LOW||quality==Quality.MEDIUM?4:3),"short tooltip "+quality);
            potion.setAmount(2);current.setItemModel(null);current.lore(List.of(net.kyori.adventure.text.Component.text("legacy tooltip")));potion.setItemMeta(current);
            check(service.migrate(potion)&&potion.getAmount()==2&&service.quality(potion)==quality,"old bottles retain amount and quality during display migration "+quality);
            check(!service.migrate(potion),"display migration is idempotent "+quality);
            player.getPersistentDataContainer().remove(magic.mana().key("mana_potion_doses"));
            var deterministic=new ManaPotionService(magic,new Random(){@Override public double nextDouble(){return .99;}});
            account.setMaxMana(100);account.setMana(0);deterministic.drink(player,quality,100_000);
            check(account.manaExact()==service.restoration(quality),"restores "+service.restoration(quality)+" "+quality);
        }
        check(account.maxMana()==110&&service.debt(player)==10&&health.getValue()==990,"highest grants 10 and costs exactly 10 HP");
        var persisted=player.getPersistentDataContainer();
        magic.mana().quit(player);service.applyDebt(player);
        check(magic.mana().account(player).maxMana()==110&&service.debt(player)==10,"growth and exhaustion survive account eviction/reload");
        var sleep=new PlayerDeepSleepEvent(player);cancelSleep=true;sleep.callEvent();
        check(service.debt(player)==10,"cancelled deep sleep does not restore HP");
        cancelSleep=false;
        var bed=probe.world.getBlockAt(40,100,4);bed.setType(Material.RED_BED);
        probe.world.setTime(13000);
        var sleepingLevel=((CraftWorld)probe.world).getHandle();
        sleepingLevel.environmentAttributes().invalidateTickCache();sleepingLevel.updateSkyBrightness();
        var sleeping=((CraftPlayer)player).getHandle();sleeping.startSleeping(new BlockPos(40,100,4));
        check(sleeping.isSleeping(),"native player enters sleep");
        for(int tick=0;tick<99;tick++)sleeping.doTick();
        check(service.debt(player)==10,"native sleep before 100 ticks keeps the HP penalty");
        sleeping.doTick();
        check(service.debt(player)==0&&health.getValue()==1000,"native successful deep sleep restores HP at 100 ticks");
        sleeping.stopSleepInBed(true,true);bed.setType(Material.AIR);
        account=magic.mana().account(player);account.setMaxMana(195);account.setMana(0);service.drink(player,Quality.SUPREME,200_000);
        check(account.maxMana()==200&&service.debt(player)==5&&health.getValue()==995,"195 to 200 costs only the actual five gained");
        service.drink(player,Quality.SUPREME,200_001);
        check(account.maxMana()==200&&service.debt(player)==5,"drinking at cap adds no HP penalty");
        new PlayerDeepSleepEvent(player).callEvent();
        account.setMaxMana(100);health.setBaseValue(20);player.setHealth(20);
        service.drink(player,Quality.SUPREME,300_000);service.drink(player,Quality.SUPREME,300_001);
        check(account.maxMana()==110&&health.getValue()==10&&service.debt(player)==10,"cannot grow when the full HP cost would leave less than one heart");
        new PlayerDeepSleepEvent(player).callEvent();
        health.setBaseValue(100);player.setHealth(100);account.setMaxMana(100);account.setMana(0);
        var buff=new AttributeModifier(new NamespacedKey(probe,"health_buff"),.5,AttributeModifier.Operation.ADD_SCALAR);
        health.addTransientModifier(buff);service.drink(player,Quality.SUPREME,400_000);
        check(Math.abs(health.getValue()-140)<.001,"HP loss remains exact with another plugin's health multiplier");
        new PlayerDeepSleepEvent(player).callEvent();
        check(health.getValue()==150&&health.getModifier(buff.getKey())!=null,"sleep preserves another plugin's modifier");health.removeModifier(buff);
        health.setBaseValue(1000);player.setHealth(1000);
        service.drink(player,Quality.SUPREME,450_000);health.removeModifier(magic.mana().key("mana_potion_exhaustion"));service.applyDebt(player);
        check(health.getValue()==990&&service.debt(player)==10,"respawn-style attribute reset cannot wash away stored exhaustion");
        health.setBaseValue(5);service.applyDebt(player);
        check(health.getValue()==2&&service.debt(player)==10,"lost health equipment preserves debt and keeps one heart");
        new PlayerDeepSleepEvent(player).callEvent();health.setBaseValue(1000);player.setHealth(1000);
        var failingRandom=new Random(){@Override public double nextDouble(){return .99;}};
        var fixed=new ManaPotionService(magic,failingRandom);
        player.removePotionEffect(PotionEffectType.NAUSEA);
        fixed.drink(player,Quality.LOW,500_000);check(!player.hasPotionEffect(PotionEffectType.NAUSEA),"one low bottle has no nausea");
        fixed.drink(player,Quality.LOW,500_001);check(player.hasPotionEffect(PotionEffectType.NAUSEA),"second low bottle causes nausea");
        check(player.getPotionEffect(PotionEffectType.NAUSEA).getDuration()==600,"nausea lasts thirty seconds rather than ten");
        player.removePotionEffect(PotionEffectType.NAUSEA);player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA,100,0));
        fixed.drink(player,Quality.LOW,500_002);
        check(player.getPotionEffect(PotionEffectType.NAUSEA).getDuration()==600,"another low bottle refreshes nausea to thirty seconds");
        player.removePotionEffect(PotionEffectType.NAUSEA);fixed.drink(player,Quality.LOW,560_003);
        check(!player.hasPotionEffect(PotionEffectType.NAUSEA),"rolling 60 second window expires old doses");
        for(int i=0;i<4;i++)fixed.drink(player,Quality.MEDIUM,600_000+i);
        check(!player.hasPotionEffect(PotionEffectType.NAUSEA),"four medium bottles have no nausea");
        fixed.drink(player,Quality.MEDIUM,600_004);check(player.hasPotionEffect(PotionEffectType.NAUSEA),"fifth medium bottle causes nausea");
        player.removePotionEffect(PotionEffectType.NAUSEA);
        for(int i=0;i<10;i++)fixed.drink(player,Quality.GOOD,700_000+i);
        check(!player.hasPotionEffect(PotionEffectType.NAUSEA),"good quality does not cause nausea");
        var successful=new ManaPotionService(magic,new Random(){@Override public double nextDouble(){return 0;}@Override public int nextInt(int origin,int bound){return 5;}});
        account.setMaxMana(100);successful.drink(player,Quality.GOOD,800_000);
        check(account.maxMana()==105&&service.debt(player)==5,"nonhighest quality can grow by five with the same HP price");
        new PlayerDeepSleepEvent(player).callEvent();
        account.setMana(0);var handle=((CraftPlayer)player).getHandle();
        player.getInventory().setItemInMainHand(service.create(Quality.SUPREME));cancelConsume=true;
        handle.startUsingItem(InteractionHand.MAIN_HAND);handle.completeUsingItem();
        check(account.manaExact()==0&&service.quality(player.getInventory().getItemInMainHand())==Quality.SUPREME,"cancelled vanilla drink keeps potion and changes no mana");
        cancelConsume=false;handle.startUsingItem(InteractionHand.MAIN_HAND);handle.completeUsingItem();
        check(account.manaExact()==100&&player.getInventory().getItemInMainHand().getType()==Material.GLASS_BOTTLE,"native drink restores mana and returns exactly the vanilla bottle");
        new PlayerDeepSleepEvent(player).callEvent();
        ItemStack fake=vanilla(PotionType.THICK);var meta=fake.getItemMeta();meta.displayName(service.create(Quality.SUPREME).getItemMeta().displayName());fake.setItemMeta(meta);
        check(service.quality(fake)==null,"renaming a vanilla bottle cannot forge a mana potion");
        nativeBrewing(service);
        // Restore this actor for the casting probe's usual cleanup.
        new PlayerDeepSleepEvent(player).callEvent();health.setBaseValue(1000);player.setHealth(1000);account.setMaxMana(100);
        HandlerList.unregisterAll(this);
    }
    void nativeBrewing(ManaPotionService service){
        var block=probe.world.getBlockAt(40,100,0);block.setType(Material.BREWING_STAND);
        var stand=(BrewingStand)block.getState();var inventory=stand.getInventory();
        for(int i=0;i<3;i++)inventory.setItem(i,vanilla(PotionType.WATER));
        inventory.setIngredient(new ItemStack(Material.NETHER_WART,2));inventory.setFuel(new ItemStack(Material.BLAZE_POWDER));
        tickStand(block,402);
        for(int i=0;i<3;i++)check(((PotionMeta)inventory.getItem(i).getItemMeta()).getBasePotionType()==PotionType.AWKWARD,"native Nether Wart stage slot "+i);
        check(inventory.getFuel()==null||inventory.getFuel().isEmpty(),"native blaze fuel is consumed");
        inventory.setIngredient(new ItemStack(Material.AMETHYST_SHARD,2));int before=brewEvents;
        tickStand(block,402);
        check(brewEvents>before,"native stand emits BrewEvent for registered amethyst recipe");
        for(int i=0;i<3;i++)check(service.quality(inventory.getItem(i))!=null,"native mana output slot "+i);
        check(inventory.getIngredient().getAmount()==1,"three bottles use one amethyst shard");
        // A scripted random stream proves each occupied slot is rolled separately, including the rare result.
        for(int i=0;i<3;i++)inventory.setItem(i,vanilla(PotionType.AWKWARD));
        var independent=new ManaPotionService(probe.magic,new Random(){int index;@Override public int nextInt(int bound){return new int[]{0,60,99}[index++];}});
        var outputs=new ArrayList<ItemStack>(List.of(service.create(Quality.LOW),service.create(Quality.LOW),service.create(Quality.LOW)));
        independent.brew(new BrewEvent(block,inventory,outputs,10));
        check(service.quality(outputs.get(0))==Quality.LOW&&service.quality(outputs.get(1))==Quality.MEDIUM&&service.quality(outputs.get(2))==Quality.SUPREME,"three bottle rolls are independent");
        inventory.setItem(1,vanilla(PotionType.WATER));inventory.setItem(2,null);
        tickStand(block,402);
        check(service.quality(inventory.getItem(0))!=null&&((PotionMeta)inventory.getItem(1).getItemMeta()).getBasePotionType()==PotionType.WATER&&inventory.getItem(2)==null,"mixed and empty slots remain intact");
        inventory.setItem(0,vanilla(PotionType.AWKWARD));inventory.setIngredient(new ItemStack(Material.AMETHYST_SHARD,2));cancelBrew=true;
        tickStand(block,402);cancelBrew=false;
        check(((PotionMeta)inventory.getItem(0).getItemMeta()).getBasePotionType()==PotionType.AWKWARD&&inventory.getIngredient().getAmount()==2,"cancelled brew leaves ingredients and original potion intact");
        inventory.setItem(0,service.create(Quality.GOOD));inventory.setItem(1,null);inventory.setIngredient(new ItemStack(Material.GUNPOWDER));
        tickStand(block,402);
        check(service.quality(inventory.getItem(0))==Quality.GOOD&&inventory.getItem(0).getType()==Material.POTION,"mana potions cannot be laundered into splash bottles");
        block.setType(Material.AIR);
    }
    void tickStand(org.bukkit.block.Block block,int count){
        var level=((CraftWorld)probe.world).getHandle();var pos=new BlockPos(block.getX(),block.getY(),block.getZ());
        var entity=(BrewingStandBlockEntity)level.getBlockEntity(pos);
        for(int i=0;i<count;i++)BrewingStandBlockEntity.serverTick(level,pos,level.getBlockState(pos),entity);
    }
}
