package com.example.advancemagic;

import com.example.advancemagic.effect.*;
import com.example.advancemagic.item.WandService;
import com.example.advancemagic.item.FlyingStaffService;
import com.example.advancemagic.mana.ManaService;
import com.example.advancemagic.pack.ResourcePackService;
import com.example.advancemagic.spell.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.*;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.*;

public final class AdvanceMagicPlugin extends JavaPlugin implements Listener {
    private ManaService mana;
    private WandService wands;
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
    @Override public void onLoad() {
        saveDefaultConfig();
        if(FlyingStaffService.upgradeSpeedConfig(getConfig()))
            getLogger().info("Updated flying-staff defaults and Turbo to 4x; custom base speeds were preserved.");
        getConfig().options().copyDefaults(true);saveConfig();
        packs=new ResourcePackService(this);
        try {packs.extract();}
        catch(java.io.IOException e){getLogger().log(java.util.logging.Level.SEVERE,"Could not extract bundled resource packs",e);}
    }
    private com.example.advancemagic.item.MagicItemMenu itemMenu;
    private com.example.advancemagic.item.BedrockCreativeBridge creativeBridge;
    public com.example.advancemagic.item.MagicItemMenu itemMenu(){return itemMenu;}
    public com.example.advancemagic.item.BedrockCreativeBridge creativeBridge(){return creativeBridge;}
    @Override public void onEnable() {
        saveDefaultConfig();mana=new ManaService(this);wands=new WandService(this);
        restoration=new com.example.advancemagic.item.RestorationService(this);
        flyingStaff=new FlyingStaffService(this);
        for(Player player:Bukkit.getOnlinePlayers()){restoration.migrate(player.getInventory());restoration.migrate(player.getEnderChest());}
        effects=new EffectEngine(this,getConfig().getInt("max-active-effects",128));
        context=new MagicContext(this);statuses=new StatusService(this);
        terrain=new TemporaryTerrainService(this);
        areas=new AreaSpells(context);projectiles=new ProjectileSpells(context);
        spells=new SpellRegistry(context,areas,projectiles);casts=new CastListener(this);
        for(Listener listener:List.of(this,wands,restoration,flyingStaff,statuses,areas,projectiles,casts,packs,terrain))getServer().getPluginManager().registerEvents(listener,this);
        terrain.recoverLoaded();
        itemMenu=new com.example.advancemagic.item.MagicItemMenu(this);
        getServer().getPluginManager().registerEvents(itemMenu,this);
        creativeBridge=new com.example.advancemagic.item.BedrockCreativeBridge(this);
        getServer().getPluginManager().registerEvents(creativeBridge,this);
        creativeBridge.init();
        packs.start();
        wands.register();flyingStaff.register();MagicCommand command=new MagicCommand(this);
        Objects.requireNonNull(getCommand("magic")).setExecutor(command);getCommand("magic").setTabCompleter(command);
        Bukkit.getScheduler().runTaskTimer(this,()->{effects.tick();statuses.tick();areas.tick();flyingStaff.tick();},1,1);
        Bukkit.getScheduler().runTaskTimer(this,()->Bukkit.getOnlinePlayers().stream().filter(p->!flyingStaff.isRiding(p)).forEach(mana::regenerate),20,20);
        for(Player p:Bukkit.getOnlinePlayers()){mana.account(p);wands.discover(p);flyingStaff.removeLegacyRecipe(p);wands.migrate(p.getInventory());wands.migrate(p.getEnderChest());packs.offer(p);}
        for(World world:Bukkit.getWorlds())world.getEntities().forEach(wands::migrateEntity);
        getLogger().info(Spell.values().length+" spells and recipes registered. No client mod or packet dependency required.");
    }
    @Override public void onDisable() {
        Bukkit.getScheduler().cancelTasks(this);
        if(flyingStaff!=null)flyingStaff.close();
        if(packs!=null)packs.close();
        if(effects!=null)effects.close();if(statuses!=null)statuses.close();
        if(terrain!=null)terrain.close();
        if(areas!=null)areas.close();if(projectiles!=null)projectiles.close();
        if(wands!=null)wands.close();if(mana!=null)Bukkit.getOnlinePlayers().forEach(mana::quit);
    }
    @EventHandler public void join(PlayerJoinEvent e) {
        Player p=e.getPlayer();mana.account(p);wands.discover(p);flyingStaff.removeLegacyRecipe(p);statuses.joined(p);wands.migrate(p.getInventory());wands.migrate(p.getEnderChest());
        Bukkit.getScheduler().runTaskLater(this,()->{if(p.isOnline()){statuses.joined(p);packs.offer(p);}},1);
    }
    @EventHandler public void quit(PlayerQuitEvent e){cleanup(e.getPlayer());mana.quit(e.getPlayer());casts.quit(e.getPlayer());}
    @EventHandler public void death(PlayerDeathEvent e){cleanup(e.getEntity());mana.save(e.getEntity());}
    @EventHandler public void world(PlayerChangedWorldEvent e){cleanup(e.getPlayer());}
    private void cleanup(Player p){effects.closeOwner(p.getUniqueId());statuses.clear(p);}
    public ResourcePackService packs(){return packs;}
    public ManaService mana(){return mana;}
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
