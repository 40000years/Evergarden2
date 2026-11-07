package com.example.forge;

import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Evergarden Forge: affix gear, reforging, salvaging, gems and sockets, elite monsters, affix structure loot. */
public final class ForgePlugin extends JavaPlugin {
    private ForgeSettings settings;
    private AffixService affixes;
    private GemService gems;
    private GearFactory gear;
    private ReforgeService reforge;
    private EliteService elites;
    private ForgeMenu menu;
    private EvergardenChestLoot evergardenChests;
    private OreGemDrops oreGems;
    private FarmGuard farmGuard;
    private ForgeResources resources;
    private CharmService charms;
    private CharmEffects charmEffects;
    private CharmMenu charmMenu;

    @Override public void onLoad(){
        resources=new ForgeResources(this);
        try{resources.extract();}catch(Exception error){getLogger().warning("Cannot extract forge resources: "+error);}
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        // onLoad may have read getConfig() before a first-run config.yml existed; read the file now.
        reloadConfig();
        settings = loadSettings();
        affixes = new AffixService(this);
        gems = new GemService(this);
        gear = new GearFactory(this);
        reforge = new ReforgeService(this);
        elites = new EliteService(this);
        menu = new ForgeMenu(this);
        charms = new CharmService(this);
        charmEffects = new CharmEffects(this, charms);
        charmMenu = new CharmMenu(this);
        getServer().getPluginManager().registerEvents(charms, this);
        getServer().getPluginManager().registerEvents(charmEffects, this);
        getServer().getPluginManager().registerEvents(charmMenu, this);
        evergardenChests = new EvergardenChestLoot(this);
        getServer().getPluginManager().registerEvents(evergardenChests, this);
        farmGuard = new FarmGuard(this);
        getServer().getPluginManager().registerEvents(farmGuard, this);
        farmGuard.start();
        oreGems = new OreGemDrops(this);
        getServer().getPluginManager().registerEvents(oreGems, this);
        getServer().getPluginManager().registerEvents(menu, this);
        getServer().getPluginManager().registerEvents(elites, this);
        elites.registerCompatibility();
        getServer().getPluginManager().registerEvents(new ForgeListener(this), this);
        ForgeCommand command = new ForgeCommand(this);
        for (String name : new String[]{"forge", "salvage", "gems"}) {
            PluginCommand pluginCommand = getCommand(name);
            if (pluginCommand != null) {
                pluginCommand.setExecutor(command);
                pluginCommand.setTabCompleter(command);
            }
        }
        elites.start();
        charmEffects.start();
        for (var player : getServer().getOnlinePlayers()) charms.applyPassives(player);
        // Advance Magic may enable after the forge; hook its cast stats once every plugin is up.
        getServer().getScheduler().runTask(this, () -> MagicStats.register(this));
        ForgeApi.bind(this);
        resources.start();
        getLogger().info("EvergardenForge ready: " + AffixType.values().length + " affixes, " + GemType.values().length
            + " gems x " + GemService.MAX_TIER + " tiers, " + Charm.values().length + " charms, elites " + (settings.elitesEnabled ? "on" : "off"));
    }

    @Override
    public void onDisable() {
        if (menu != null) menu.closeAll();
        if (charmMenu != null) charmMenu.closeAll();
        if (charmEffects != null) charmEffects.stop();
        if (elites != null) elites.stop();
        if (farmGuard != null) farmGuard.stop();
        if (resources != null) resources.close();
        ForgeApi.bind(null);
    }

    public void reload() {
        menu.closeAll();
        charmMenu.closeAll();
        reloadConfig();
        settings = loadSettings();
        charms.refreshAll();
        for(var player:getServer().getOnlinePlayers()){
            var inventory=player.getInventory();
            for(int slot=0;slot<inventory.getSize();slot++){
                var item=inventory.getItem(slot);if(item!=null&&(affixes.ensure(item)|gems.refresh(item)|ForgeResources.model(this,item)))inventory.setItem(slot,item);
            }
        }
    }

    /** A config.yml from an older version gains the new sections (gems, elites, sockets...) without losing edits. */
    private ForgeSettings loadSettings() {
        replaceOutdatedConfig();
        // Inspect the actual file without bundled defaults, so old files cannot inherit the new marker.
        var disk = YamlConfiguration.loadConfiguration(getDataFolder().toPath().resolve("config.yml").toFile());
        if (CombatBalance.upgrade(disk)) {
            CombatBalance.copyChanges(disk, getConfig());
            saveConfig();
            getLogger().info("Updated previous default combat balance; custom values were preserved.");
        }
        // Key by key: copyDefaults alone skips keys missing inside a section that already exists
        // (rarities.*.sockets, new affixes).
        Configuration defaults = getConfig().getDefaults();
        int added = 0;
        if (defaults != null) {
            for (String key : defaults.getKeys(true)) {
                if (!defaults.isConfigurationSection(key) && !getConfig().isSet(key)) {
                    getConfig().set(key, defaults.get(key));
                    added++;
                }
            }
        }
        if (added > 0) {
            saveConfig();
            getLogger().info("config.yml: added " + added + " new settings from this version");
        }
        return new ForgeSettings(getConfig(), getLogger());
    }

    /**
     * When the shipped defaults changed meaningfully (config-version went up), an older file would
     * keep its old numbers through the key merge. Back it up and start from the new defaults.
     */
    private void replaceOutdatedConfig() {
        Configuration defaults = getConfig().getDefaults();
        int target = defaults == null ? 0 : defaults.getInt("config-version");
        int current = getConfig().getInt("config-version", 0);
        if (current >= target) return;
        Map<String, Object> retained = new LinkedHashMap<>();
        for (String key : getConfig().getKeys(true)) {
            if (getConfig().isConfigurationSection(key) || !getConfig().isSet(key)) continue;
            if (key.startsWith("resource-pack.") || key.startsWith("affixes.") || key.equals("gems.tier-multipliers")
                || key.equals("gui.icon-namespace") || key.equals("command-opens-anywhere") || key.equals("station-block")) {
                retained.put(key, getConfig().get(key));
            }
        }
        Path file = getDataFolder().toPath().resolve("config.yml");
        Path backup = getDataFolder().toPath().resolve("config-v" + current + "-backup.yml");
        for (int suffix = 1; Files.exists(backup); suffix++) {
            backup = getDataFolder().toPath().resolve("config-v" + current + "-backup-" + suffix + ".yml");
        }
        try {
            Files.move(file, backup);
        } catch (IOException e) {
            getLogger().warning("Could not back up outdated config.yml: " + e.getMessage());
            return;
        }
        saveDefaultConfig();
        reloadConfig();
        retained.forEach((key, value) -> getConfig().set(key, value));
        // The newer file's marker must not skip comparison of preserved pre-v7 affix ranges.
        getConfig().set("combat-balance-version", 0);
        saveConfig();
        getLogger().warning("config.yml was version " + current + "; replaced with version " + target + " defaults. Old file: " + backup.getFileName());
    }

    public ForgeSettings settings() {
        return settings;
    }

    public AffixService affixes() {
        return affixes;
    }

    public GemService gems() {
        return gems;
    }

    public GearFactory gear() {
        return gear;
    }

    public ReforgeService reforge() {
        return reforge;
    }

    public EliteService elites() {
        return elites;
    }

    public FarmGuard farmGuard() {
        return farmGuard;
    }

    public OreGemDrops oreGems() {
        return oreGems;
    }

    public EvergardenChestLoot evergardenChests() {
        return evergardenChests;
    }

    public ForgeMenu menu() {
        return menu;
    }
    public ForgeResources resources(){return resources;}
    public CharmService charms(){return charms;}
    public CharmEffects charmEffects(){return charmEffects;}
    public CharmMenu charmMenu(){return charmMenu;}
}
