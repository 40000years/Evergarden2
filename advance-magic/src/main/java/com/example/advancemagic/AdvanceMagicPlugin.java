package com.example.advancemagic;

import com.example.advancemagic.effect.*;
import com.example.advancemagic.item.WandService;
import com.example.advancemagic.item.FlyingStaffService;
import com.example.advancemagic.mana.ManaService;
import com.example.advancemagic.pack.ResourcePackService;
import com.example.advancemagic.spell.*;
import com.example.advancemagic.elemental.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.*;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.*;

public final class AdvanceMagicPlugin extends JavaPlugin implements Listener {
    private ManaService mana;
    private StaffSpellService staffSpells;
    private ElementalSpellService elemental;
    private BedrockBlockModels blockModels;
    public BedrockBlockModels blockModels(){return blockModels;}
    private ElementalStats elementalStats;
    private SkillTreeService skillTree;
    private ElementalAcquisitionService acquisition;
    public StaffSpellService staffSpells(){return staffSpells;}
    public ElementalSpellService elemental(){return elemental;}
    public ElementalStats elementalStats(){return elementalStats;}
    public SkillTreeService skillTree(){return skillTree;}
    public ElementalAcquisitionService acquisition(){return acquisition;}
    private com.example.advancemagic.mana.ManaPotionService manaPotions;
    private WandService wands;
    private com.example.advancemagic.item.ElementalStaffService staffs;
    public com.example.advancemagic.item.ElementalStaffService staffs(){return staffs;}
    private com.example.advancemagic.item.RestorationService restoration;
    private FlyingStaffService flyingStaff;
    private EffectEngine effects;
    private TemporaryTerrainService terrain;
    private StatusService statuses;
    private MagicContext context;
    private AreaSpells areas;
    private ProjectileSpells projectiles;
    private SpellRegistry spells;
    private CastListener casts;
    private ResourcePackService packs;
    private RetiredStaffRecipeData retiredStaffRecipes;
    private com.example.advancemagic.item.LegendaryWeaponService legendaryWeapons;
    private com.example.advancemagic.item.ArmorCombatService armorCombat;
    public com.example.advancemagic.item.ArmorCombatService armorCombat(){return armorCombat;}
    public com.example.advancemagic.item.LegendaryWeaponService legendaryWeapons(){return legendaryWeapons;}
    @Override public void onLoad() {
        saveDefaultConfig();
        retiredStaffRecipes=new RetiredStaffRecipeData(this);
        try {retiredStaffRecipes.start();}
        catch(java.io.IOException error){getLogger().log(java.util.logging.Level.SEVERE,"Could not migrate retired staff recipe books before player loading",error);}
        if(FlyingStaffService.upgradeSpeedConfig(getConfig()))
            getLogger().info("Updated flying-staff defaults and Turbo to 4x; custom base speeds were preserved.");
        if(upgradeMythicBalanceConfig())
            getLogger().info("Updated Solar and Chronos damage defaults for the ice-field finale; custom damage values were preserved.");
        getConfig().options().copyDefaults(true);saveConfig();
        packs=new ResourcePackService(this);
        try {packs.extract();}
        catch(java.io.IOException e){getLogger().log(java.util.logging.Level.SEVERE,"Could not extract bundled resource packs",e);}
    }
    private boolean upgradeMythicBalanceConfig() {
        var config=getConfig();
        if(config.isSet("internal.mythic-ice-balance-version"))return false;
        boolean changed=false;
        if(Double.compare(config.getDouble("damage.solar-beam"),32.0)==0){config.set("damage.solar-beam",45.0);changed=true;}
        if(Double.compare(config.getDouble("damage.solar-apocalypse"),180.0)==0){config.set("damage.solar-apocalypse",250.0);changed=true;}
        if(Double.compare(config.getDouble("damage.solar-lava"),12.0)==0){config.set("damage.solar-lava",18.0);changed=true;}
        if(Double.compare(config.getDouble("damage.chronos-blade"),24.0)==0){config.set("damage.chronos-blade",30.0);changed=true;}
        if(Double.compare(config.getDouble("damage.chronos-shatter"),100.0)==0){config.set("damage.chronos-shatter",180.0);changed=true;}
        config.set("internal.mythic-ice-balance-version",1);
        return changed;
    }
    private com.example.advancemagic.item.MagicItemMenu itemMenu;
    private com.example.advancemagic.item.BedrockCreativeBridge creativeBridge;
    public com.example.advancemagic.item.MagicItemMenu itemMenu(){return itemMenu;}
    public com.example.advancemagic.item.BedrockCreativeBridge creativeBridge(){return creativeBridge;}
    @Override public void onEnable() {
        saveDefaultConfig();mana=new ManaService(this);wands=new WandService(this);
        staffs=new com.example.advancemagic.item.ElementalStaffService(this);
        manaPotions=new com.example.advancemagic.mana.ManaPotionService(this);manaPotions.register();
        restoration=new com.example.advancemagic.item.RestorationService(this);
        flyingStaff=new FlyingStaffService(this);
        for(Player player:Bukkit.getOnlinePlayers()){restoration.migrate(player.getInventory());restoration.migrate(player.getEnderChest());}
        effects=new EffectEngine(this,getConfig().getInt("max-active-effects",128));
        context=new MagicContext(this);statuses=new StatusService(this);
        terrain=new TemporaryTerrainService(this);
        areas=new AreaSpells(context);projectiles=new ProjectileSpells(context);
        spells=new SpellRegistry(context,areas,projectiles);casts=new CastListener(this);
        staffSpells=new StaffSpellService(this);skillTree=new SkillTreeService(this);
        blockModels=new BedrockBlockModels(this);
        elementalStats=new ElementalStats(this);elemental=new ElementalSpellService(this);
        acquisition=new ElementalAcquisitionService(this);acquisition.register();
        for(Listener listener:List.of(staffSpells,skillTree,acquisition))getServer().getPluginManager().registerEvents(listener,this);
        getServer().getPluginManager().registerEvents(retiredStaffRecipes,this);
        var spellMenu=new SpellMenu(this);getServer().getPluginManager().registerEvents(spellMenu,this);
        var spellCommand=new SpellCommand(this,spellMenu);
        for(String name:List.of("spell","staff")){Objects.requireNonNull(getCommand(name)).setExecutor(spellCommand);getCommand(name).setTabCompleter(spellCommand);}
        Objects.requireNonNull(getCommand("skilltree")).setExecutor(skillTree);getCommand("skilltree").setTabCompleter(skillTree);
        for(Listener listener:List.of(this,wands,staffs,restoration,flyingStaff,statuses,areas,projectiles,casts,packs,terrain,manaPotions))getServer().getPluginManager().registerEvents(listener,this);
        terrain.recoverLoaded();
        itemMenu=new com.example.advancemagic.item.MagicItemMenu(this);
        getServer().getPluginManager().registerEvents(itemMenu,this);
        creativeBridge=new com.example.advancemagic.item.BedrockCreativeBridge(this);
        getServer().getPluginManager().registerEvents(creativeBridge,this);
        creativeBridge.init();
        packs.start();
        legendaryWeapons=new com.example.advancemagic.item.LegendaryWeaponService(this);
        var armorSets=new com.example.advancemagic.item.ArmorSetService(this);
        armorCombat=new com.example.advancemagic.item.ArmorCombatService(this,armorSets);
        getServer().getPluginManager().registerEvents(armorCombat,this);
        Bukkit.getScheduler().runTask(this,armorCombat::registerSurvival);
        for(Player player:Bukkit.getOnlinePlayers()){armorSets.migrate(player.getInventory());armorSets.migrate(player.getEnderChest());}
        Bukkit.getScheduler().runTaskTimer(this,armorCombat::tick,1,5);
        getServer().getPluginManager().registerEvents(new com.example.advancemagic.item.RetiredArmorCleanup(),this);
        for(Player player:Bukkit.getOnlinePlayers()){
            com.example.advancemagic.item.RetiredArmorCleanup.clean(player.getInventory());
            com.example.advancemagic.item.RetiredArmorCleanup.clean(player.getEnderChest());
        }
        getServer().getPluginManager().registerEvents(legendaryWeapons,this);
        Bukkit.getScheduler().runTaskTimer(this,legendaryWeapons::tick,4,4);
        Bukkit.getScheduler().runTaskTimer(this,staffs::tick,4,4);
        wands.register();flyingStaff.register();MagicCommand command=new MagicCommand(this);
        Objects.requireNonNull(getCommand("magic")).setExecutor(command);getCommand("magic").setTabCompleter(command);
        Bukkit.getScheduler().runTaskTimer(this,()->{effects.tick();statuses.tick();areas.tick();flyingStaff.tick();elemental.tick();},1,1);
        Bukkit.getScheduler().runTaskTimer(this,()->Bukkit.getOnlinePlayers().forEach(p->{
            manaPotions.applyDebt(p);
            if(!flyingStaff.isRiding(p))mana.regenerate(p);
            skillTree.discover(p);
        }),20,20);
        for(Player p:Bukkit.getOnlinePlayers()){mana.account(p);wands.discover(p);flyingStaff.removeLegacyRecipe(p);wands.migrate(p.getInventory());wands.migrate(p.getEnderChest());packs.offer(p);}
        for(World world:Bukkit.getWorlds())world.getEntities().forEach(wands::migrateEntity);
        getLogger().info("58 elemental spells, scroll imprinting, loot-only elemental staffs and Skill Tree ready. Legacy spell API retained.");
    }
    @Override public void onDisable() {
        Bukkit.getScheduler().cancelTasks(this);
        if(elemental!=null)elemental.close();
        if(blockModels!=null)blockModels.close();
        if(acquisition!=null)acquisition.close();
        if(legendaryWeapons!=null)legendaryWeapons.close();
        if(armorCombat!=null)armorCombat.close();
        if(manaPotions!=null)manaPotions.close();
        if(flyingStaff!=null)flyingStaff.close();
        if(packs!=null)packs.close();
        if(effects!=null)effects.close();if(statuses!=null)statuses.close();
        if(terrain!=null)terrain.close();
        if(areas!=null)areas.close();if(projectiles!=null)projectiles.close();
        if(wands!=null)wands.close();if(mana!=null)Bukkit.getOnlinePlayers().forEach(mana::quit);
    }
    @EventHandler public void join(PlayerJoinEvent e) {
        Player p=e.getPlayer();mana.account(p);wands.discover(p);flyingStaff.removeLegacyRecipe(p);statuses.joined(p);wands.migrate(p.getInventory());wands.migrate(p.getEnderChest());
        Bukkit.getScheduler().runTaskLater(this,()->{if(p.isOnline())statuses.joined(p);},1);
    }
    @EventHandler public void quit(PlayerQuitEvent e){cleanup(e.getPlayer());mana.quit(e.getPlayer());casts.quit(e.getPlayer());}
    @EventHandler public void death(PlayerDeathEvent e){cleanup(e.getEntity());mana.save(e.getEntity());}
    @EventHandler public void world(PlayerChangedWorldEvent e){cleanup(e.getPlayer());}
    private void cleanup(Player p){effects.closeOwner(p.getUniqueId());statuses.clear(p);}
    public ResourcePackService packs(){return packs;}
    public ManaService mana(){return mana;}
    public com.example.advancemagic.mana.ManaPotionService manaPotions(){return manaPotions;}
    public WandService wands(){return wands;}
    public com.example.advancemagic.item.RestorationService restoration(){return restoration;}
    public FlyingStaffService flyingStaff(){return flyingStaff;}
    public EffectEngine effects(){return effects;}
    public TemporaryTerrainService terrain(){return terrain;}
    public StatusService statuses(){return statuses;}
    public MagicContext context(){return context;}
    public AreaSpells areas(){return areas;}
    public ProjectileSpells projectiles(){return projectiles;}
    public SpellRegistry spells(){return spells;}
    public CastListener casts(){return casts;}
}
