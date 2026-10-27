package com.example.advancemagic.mana;

import com.example.advancemagic.AdvanceMagicPlugin;
import io.papermc.paper.event.player.PlayerDeepSleepEvent;
import io.papermc.paper.potion.PotionMix;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.attribute.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.*;
import java.util.*;
import java.util.random.RandomGenerator;

/** Native brewing and consumption; all progression and exhaustion live in player PDC. */
public final class ManaPotionService implements Listener, AutoCloseable {
    public enum Quality {
        LOW("ต่ำ",20,0x8978AC), MEDIUM("ปานกลาง",35,0x7963DA),
        GOOD("ดี",50,0x5F9CEF), SUPREME("สูงสุด",100,0xDD9EFF);
        final String label; final int restore, color;
        Quality(String label,int restore,int color){this.label=label;this.restore=restore;this.color=color;}
    }
    private final AdvanceMagicPlugin plugin;
    private final RandomGenerator random;
    private final NamespacedKey qualityKey,recipeKey,debtKey,modifierKey;
    public ManaPotionService(AdvanceMagicPlugin plugin){this(plugin,RandomGenerator.getDefault());}
    public ManaPotionService(AdvanceMagicPlugin plugin,RandomGenerator random){
        this.plugin=plugin;this.random=Objects.requireNonNull(random);
        qualityKey=key("mana_potion_quality");recipeKey=key("amethyst_mana_potion");
        debtKey=key("mana_potion_hp_debt");modifierKey=key("mana_potion_exhaustion");
    }
    private NamespacedKey key(String id){return new NamespacedKey(plugin,id);}
    public void register(){
        Bukkit.getPotionBrewer().removePotionMix(recipeKey);
        Bukkit.getPotionBrewer().addPotionMix(new PotionMix(recipeKey,create(Quality.LOW),
                PotionMix.createPredicateChoice(this::isAwkward),new RecipeChoice.MaterialChoice(Material.AMETHYST_SHARD)));
        Bukkit.getOnlinePlayers().forEach(p->{applyDebt(p);migrate(p.getInventory());migrate(p.getEnderChest());});
        Bukkit.getWorlds().forEach(world->world.getEntities().forEach(this::migrateEntity));
    }
    public int restoration(Quality quality){
        return Math.max(0,Math.min(200,plugin.getConfig().getInt("mana-potions.restore."+quality.name().toLowerCase(Locale.ROOT),quality.restore)));
    }
    public int nauseaSeconds(){return Math.max(10,Math.min(120,plugin.getConfig().getInt("mana-potions.nausea-seconds",30)));}
    public ItemStack create(Quality quality){
        ItemStack item=new ItemStack(Material.POTION);
        PotionMeta meta=(PotionMeta)item.getItemMeta();
        meta.setBasePotionType(PotionType.THICK);meta.setColor(Color.fromRGB(quality.color));
        meta.getPersistentDataContainer().set(qualityKey,PersistentDataType.STRING,quality.name());
        decorate(meta,quality);
        item.setItemMeta(meta);return item;
    }
    private Component line(String text,NamedTextColor color){return Component.text(text,color).decoration(TextDecoration.ITALIC,false);}
    private void decorate(PotionMeta meta,Quality quality){
        meta.setItemModel(NamespacedKey.fromString("advance_magic:mana_potion_"+quality.name().toLowerCase(Locale.ROOT)));
        meta.displayName(line("ยามานา · "+quality.label,NamedTextColor.LIGHT_PURPLE));
        meta.lore(List.of(line("ดื่มเพื่อฟื้นฟูมานา",NamedTextColor.AQUA)));
        meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
    }
    public boolean migrate(ItemStack item){
        Quality quality=quality(item);if(quality==null)return false;
        PotionMeta before=(PotionMeta)item.getItemMeta(),updated=before.clone();decorate(updated,quality);
        if(before.equals(updated))return false;
        item.setItemMeta(updated);return true;
    }
    public void migrate(Inventory inventory){
        for(int slot=0;slot<inventory.getSize();slot++){
            ItemStack item=inventory.getItem(slot);if(migrate(item))inventory.setItem(slot,item);
        }
    }
    private void migrateEntity(org.bukkit.entity.Entity entity){
        if(entity instanceof org.bukkit.entity.Item dropped){ItemStack item=dropped.getItemStack();if(migrate(item))dropped.setItemStack(item);}
        if(entity instanceof org.bukkit.entity.ItemFrame frame){ItemStack item=frame.getItem();if(migrate(item))frame.setItem(item);}
    }
    @EventHandler(priority=EventPriority.LOWEST) public void open(InventoryOpenEvent event){migrate(event.getInventory());migrate(event.getPlayer().getInventory());}
    @EventHandler(priority=EventPriority.LOWEST) public void pickup(EntityPickupItemEvent event){migrateEntity(event.getItem());}
    @EventHandler(priority=EventPriority.LOWEST) public void spawn(ItemSpawnEvent event){migrateEntity(event.getEntity());}
    @EventHandler public void load(EntitiesLoadEvent event){event.getEntities().forEach(this::migrateEntity);}
    public Quality quality(ItemStack item){
        if(item==null||item.getType()!=Material.POTION||!(item.getItemMeta() instanceof PotionMeta meta))return null;
        String value=meta.getPersistentDataContainer().get(qualityKey,PersistentDataType.STRING);
        if(value==null)return null;
        try{return Quality.valueOf(value);}catch(IllegalArgumentException error){return null;}
    }
    private boolean isAwkward(ItemStack item){
        return item!=null&&item.getType()==Material.POTION&&quality(item)==null
                &&item.getItemMeta() instanceof PotionMeta meta&&meta.getBasePotionType()==PotionType.AWKWARD;
    }
    public Quality rollQuality(){
        int draw=random.nextInt(100);
        return draw<60?Quality.LOW:draw<90?Quality.MEDIUM:draw<98?Quality.GOOD:Quality.SUPREME;
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void brew(BrewEvent event){
        ItemStack ingredient=event.getContents().getIngredient();
        boolean amethyst=ingredient!=null&&ingredient.getType()==Material.AMETHYST_SHARD;
        boolean changed=false,protectedSlot=false;
        List<ItemStack> results=event.getResults();
        for(int slot=0;slot<Math.min(3,results.size());slot++){
            ItemStack input=event.getContents().getItem(slot);
            if(amethyst&&isAwkward(input)){results.set(slot,create(rollQuality()));changed=true;}
            else if(quality(input)!=null){results.set(slot,input.clone());protectedSlot=true;}
            else if(!Objects.equals(input,results.get(slot)))changed=true;
        }
        // Do not turn mana potions into untagged splash/lingering potions or waste reagents on them alone.
        if(protectedSlot&&!changed)event.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void consume(PlayerItemConsumeEvent event){
        Quality quality=quality(event.getItem());if(quality==null)return;
        drink(event.getPlayer(),quality,System.currentTimeMillis());
        // Vanilla owns consumption, the hand slot and its glass bottle, including creative mode.
    }
    public void drink(Player player,Quality quality,long now){
        var data=player.getPersistentDataContainer();
        int streak=recordDose(player,quality,now);
        if((quality==Quality.LOW&&streak>=2)||(quality==Quality.MEDIUM&&streak>=5))
            player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA,nauseaSeconds()*20,0));
        long doses=data.getOrDefault(key("mana_potion_doses"),PersistentDataType.LONG,0L);
        doses=Math.min(1_000_000L,Math.max(0,doses)+1);
        data.set(key("mana_potion_doses"),PersistentDataType.LONG,doses);
        double chance=Math.min(.15,.02+.005*(doses-1));
        int requested=quality==Quality.SUPREME?10:random.nextDouble()<chance?random.nextInt(2,6):0;
        CastAccount account=plugin.mana().account(player);
        double gain=Math.min(requested,Math.max(0,200-account.baseMaxMana()));
        var health=player.getAttribute(Attribute.MAX_HEALTH);
        applyDebt(player);
        if(gain>0&&health!=null&&health.getValue()-gain>=2){
            account.setMaxMana(account.baseMaxMana()+gain);
            data.set(debtKey,PersistentDataType.DOUBLE,debt(player)+gain);applyDebt(player);
        }
        account.setMana(account.manaExact()+restoration(quality));plugin.mana().save(player);
        player.playSound(player.getLocation(),Sound.BLOCK_AMETHYST_BLOCK_CHIME,.7f,1.2f);
    }
    private int recordDose(Player player,Quality quality,long now){
        var data=player.getPersistentDataContainer();NamespacedKey timestamps=key("mana_potion_streak_"+quality.name().toLowerCase(Locale.ROOT));
        long[] old=data.getOrDefault(timestamps,PersistentDataType.LONG_ARRAY,new long[0]);
        // Only the most recent five doses are needed for either threshold; logout cannot reset the window.
        long[] recent=Arrays.stream(old).filter(time->time<=now&&time>now-60_000).toArray();
        int keep=Math.min(4,recent.length);long[] next=new long[keep+1];
        System.arraycopy(recent,recent.length-keep,next,0,keep);next[keep]=now;
        data.set(timestamps,PersistentDataType.LONG_ARRAY,next);return next.length;
    }
    public double debt(Player player){return Math.max(0,player.getPersistentDataContainer().getOrDefault(debtKey,PersistentDataType.DOUBLE,0.0));}
    public void applyDebt(Player player){
        var attribute=player.getAttribute(Attribute.MAX_HEALTH);if(attribute==null)return;
        if(debt(player)==0&&attribute.getModifier(modifierKey)==null)return;
        attribute.removeModifier(modifierKey);
        double reduction=Math.min(debt(player),Math.max(0,attribute.getValue()-2));
        double additive=attribute.getBaseValue(),scalar=0,multiplier=1;
        for(AttributeModifier modifier:attribute.getModifiers()){
            if(modifier.getOperation()==AttributeModifier.Operation.ADD_NUMBER)additive+=modifier.getAmount();
            else if(modifier.getOperation()==AttributeModifier.Operation.ADD_SCALAR)scalar+=modifier.getAmount();
            else if(modifier.getOperation()==AttributeModifier.Operation.MULTIPLY_SCALAR_1)multiplier*=1+modifier.getAmount();
        }
        double factor=(1+scalar)*multiplier;
        if(reduction>0&&Double.isFinite(factor)&&factor>0)
            attribute.addTransientModifier(new AttributeModifier(modifierKey,(attribute.getValue()-reduction)/factor-additive,AttributeModifier.Operation.ADD_NUMBER));
        if(player.getHealth()>attribute.getValue())player.setHealth(attribute.getValue());
    }
    @EventHandler(priority=EventPriority.MONITOR) public void join(PlayerJoinEvent event){
        applyDebt(event.getPlayer());migrate(event.getPlayer().getInventory());migrate(event.getPlayer().getEnderChest());
    }
    @EventHandler(priority=EventPriority.MONITOR) public void respawn(PlayerRespawnEvent event){
        Bukkit.getScheduler().runTask(plugin,()->{if(event.getPlayer().isOnline())applyDebt(event.getPlayer());});
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void sleep(PlayerDeepSleepEvent event){
        Player player=event.getPlayer();if(debt(player)<=0)return;
        player.getPersistentDataContainer().remove(debtKey);applyDebt(player);
    }
    @Override public void close(){Bukkit.getPotionBrewer().removePotionMix(recipeKey);}
}
