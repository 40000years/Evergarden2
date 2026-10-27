package com.example.advancemagic.elemental;

import com.example.advancemagic.AdvanceMagicPlugin;
import com.example.advancemagic.item.ElementalStaffService.Element;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.*;
import java.util.*;

/** Loot-only elemental staffs, a craftable wooden starter and the chest-loot entry point. */
public final class ElementalAcquisitionService implements Listener,AutoCloseable {
    private final AdvanceMagicPlugin plugin;
    private final Set<NamespacedKey> retiredRecipes=new HashSet<>();
    private final NamespacedKey woodenRecipe;
    public ElementalAcquisitionService(AdvanceMagicPlugin plugin){
        this.plugin=plugin;
        woodenRecipe=new NamespacedKey(plugin,"wooden_staff");
        for(Element element:Element.values())retiredRecipes.add(new NamespacedKey(plugin,"elemental_staff_"+element.id()));
    }
    public void register(){
        // Paper resolves recipe keys to live holders when forgetting them. Do this
        // before removal, otherwise a hot reload leaves known recipe keys behind.
        Bukkit.getOnlinePlayers().forEach(this::forgetRecipes);
        retiredRecipes.forEach(Bukkit::removeRecipe);
        Bukkit.removeRecipe(woodenRecipe);
        var recipe=new ShapedRecipe(woodenRecipe,plugin.staffSpells().createCraftingWoodenStaff());
        recipe.shape(" A "," B "," B ");
        recipe.setIngredient('A',new RecipeChoice.ExactChoice(new ItemStack(Material.AMETHYST_SHARD)));
        recipe.setIngredient('B',new RecipeChoice.ExactChoice(new ItemStack(Material.BLAZE_ROD)));
        Bukkit.addRecipe(recipe);
        Bukkit.getOnlinePlayers().forEach(player->player.discoverRecipe(woodenRecipe));
    }
    private void forgetRecipes(Player player){player.undiscoverRecipes(retiredRecipes);}
    @EventHandler public void join(PlayerJoinEvent event){forgetRecipes(event.getPlayer());event.getPlayer().discoverRecipe(woodenRecipe);}
    private boolean allowedWood(Recipe recipe,ItemStack item){
        return recipe instanceof Keyed keyed&&keyed.getKey().equals(woodenRecipe)
                &&plugin.staffs().isWooden(item)&&plugin.staffSpells().tier(item)==StaffSpellService.Tier.WOODEN
                &&plugin.staffSpells().spells(item).isEmpty();
    }
    /** Reject stale recipes and third-party aliases too: a crafted legacy wand would migrate to a staff. */
    private boolean prohibitedCraft(Recipe recipe,ItemStack result){
        return recipe instanceof Keyed keyed&&retiredRecipes.contains(keyed.getKey())
                ||plugin.staffs().isRetiredOrStaff(result)&&!allowedWood(recipe,result)
                ||recipe!=null&&plugin.staffs().isRetiredOrStaff(recipe.getResult())&&!allowedWood(recipe,recipe.getResult());
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void prepare(PrepareItemCraftEvent event){
        if(prohibitedCraft(event.getRecipe(),event.getInventory().getResult()))event.getInventory().setResult(null);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void craft(CraftItemEvent event){
        if(prohibitedCraft(event.getRecipe(),event.getCurrentItem()))event.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void resultClick(InventoryClickEvent event){
        if(event.getSlotType()==InventoryType.SlotType.RESULT&&event.getInventory() instanceof CraftingInventory inventory
                &&prohibitedCraft(inventory.getRecipe(),inventory.getResult()))event.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void automatic(CrafterCraftEvent event){
        if(prohibitedCraft(event.getRecipe(),event.getResult()))event.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void crafted(CraftItemEvent event){
        if(event.getWhoClicked() instanceof Player player&&allowedWood(event.getRecipe(),event.getCurrentItem()))
            Bukkit.getScheduler().runTask(plugin,()->{if(player.isOnline())plugin.staffSpells().migrate(player.getInventory());});
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void combat(EntityDamageByEntityEvent e){
        if(e.getFinalDamage()<=0)return;
        Player source=e.getDamager() instanceof Player p?p:e.getDamager() instanceof org.bukkit.entity.Projectile shot&&shot.getShooter() instanceof Player p?p:null;
        if(source!=null&&e.getEntity() instanceof org.bukkit.entity.LivingEntity living&&plugin.context().enemy(source,living))plugin.mana().markCombat(source);
        if(e.getEntity() instanceof Player p)plugin.mana().markCombat(p);
    }
    /** profile: exploration, landmark, boss, sanctuary. null is an ordinary no-scroll roll. */
    public ItemStack rollScroll(String profile,Random random){
        String path="elemental-system.scroll-loot."+profile;
        double chance=plugin.getConfig().getDouble(path+".chance",switch(profile){case "boss","sanctuary"->.65;case "landmark"->.4;default->.25;});
        if(!Double.isFinite(chance)||random.nextDouble()>=Math.clamp(chance,0,1))return null;
        double[] defaults=switch(profile){case "boss","sanctuary"->new double[]{25,35,30,10};case "landmark"->new double[]{45,35,17,3};default->new double[]{65,28,6,1};};
        var weights=new double[4];double total=0;
        for(int i=0;i<4;i++){double value=plugin.getConfig().getDouble(path+".rarity-weights."+ElementalSpell.Rarity.values()[i].name().toLowerCase(Locale.ROOT),defaults[i]);weights[i]=Double.isFinite(value)?Math.max(0,value):0;total+=weights[i];}
        if(total<=0)return null;double draw=random.nextDouble()*total;int rarity=3;
        for(int i=0;i<4;i++)if((draw-=weights[i])<0){rarity=i;break;}
        final int chosen=rarity;var pool=Arrays.stream(ElementalSpell.values()).filter(s->s.rarity().ordinal()==chosen).toList();
        if(pool.isEmpty())return null;
        ElementalSpell spell=pool.get(random.nextInt(pool.size()));
        return plugin.staffSpells().createScroll(spell,SpellLevels.leveled(spell)?rollLevel(profile,random):SpellLevels.BASE);
    }
    /** Level I–VII by chest profile: ordinary structures mostly low levels, bosses and sanctuaries the high ones. */
    public int rollLevel(String profile,Random random){
        String path="elemental-system.scroll-loot."+profile+".level-weights.";
        double[] defaults=switch(profile){
            case "boss","sanctuary"->new double[]{0,3,10,20,30,24,13};
            case "landmark"->new double[]{10,15,22,23,17,9,4};
            default->new double[]{30,28,20,12,7,2.5,.5};
        };
        double[] weights=new double[7];double total=0;
        for(int i=0;i<7;i++){double value=plugin.getConfig().getDouble(path+SpellLevels.roman(i+1).toLowerCase(Locale.ROOT),defaults[i]);weights[i]=Double.isFinite(value)?Math.max(0,value):0;total+=weights[i];}
        if(total<=0)return SpellLevels.BASE;
        double draw=random.nextDouble()*total;
        for(int i=0;i<7;i++)if((draw-=weights[i])<0)return i+1;
        return SpellLevels.MAX;
    }
    public void close(){
        Bukkit.getOnlinePlayers().forEach(this::forgetRecipes);
        retiredRecipes.forEach(Bukkit::removeRecipe);
        Bukkit.getOnlinePlayers().forEach(player->player.undiscoverRecipe(woodenRecipe));
        Bukkit.removeRecipe(woodenRecipe);
    }
}
