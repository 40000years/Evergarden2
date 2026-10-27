package com.example.voidscape;

import com.example.voidscape.world.*;
import com.example.voidscape.item.RelicService;
import com.example.voidscape.dungeon.DungeonManager;
import com.example.voidscape.listener.TravelListener;
import com.example.voidscape.command.VoidCommand;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.*;
import java.nio.file.*;
import java.util.*;

public final class VoidscapePlugin extends JavaPlugin {
    private World voidWorld;
    private DungeonLayout layout;
    private SkyWhaleLayout skyWhales;
    private SkyLandmarkLayout skyLandmarks;
    private WorldBossTempleLayout bossTemples;
    private BossStructureLayout bossStructures;
    private BiomeTerrain biomeTerrain;
    private ExplorationLayout exploration;
    private OverworldSanctuaries overworldSanctuaries;
    private com.example.voidscape.boss.SanctuaryEncounters sanctuaryEncounters;
    private com.example.voidscape.boss.SeraphonBoss seraphonBoss;
    public com.example.voidscape.boss.SeraphonBoss seraphonBoss(){return seraphonBoss;}
    public com.example.voidscape.boss.SanctuaryEncounters sanctuaryEncounters(){return sanctuaryEncounters;}
    public OverworldSanctuaries overworldSanctuaries(){return overworldSanctuaries;}
    private RestorationLayout restorationLayout;
    private RestorationAltars restorationAltars;
    private RelicService relics;
    private com.example.voidscape.item.AdventurerWeaponService adventurerWeapons;
    private DungeonManager dungeons;
    private StructureProtection structureProtection;
    private com.example.voidscape.boss.WorldBossManager worldBoss;
    private TravelListener travel;

    private com.example.voidscape.pack.ResourcePackService packs;
    private com.example.voidscape.boss.JudgePackService judgePack;
    private com.example.voidscape.boss.ArkhaiaModelSupport arkhaiaModels;
    private com.example.voidscape.pet.PetModelPreviews petPreviews;
    private com.example.voidscape.pet.PetSystem pets;
    private com.example.voidscape.trade.SeraphTrader seraphTrader;
    private com.example.voidscape.boss.ArkhaiaBedrockSupport arkhaiaBedrock;
    private com.example.voidscape.gui.AdminTestGui testGui;
    private com.example.voidscape.gui.WandShowcaseGui wandGui;
    private com.example.voidscape.gui.RelicShowcaseGui relicGui;
    private com.example.voidscape.guide.GuideMenuGui guideMenu;
    private com.example.voidscape.enchant.UniqueAbilityListener abilities;
    private com.example.voidscape.enchant.BossEnchantService bossEnchants;
    private com.example.voidscape.enchant.BossEnchantEffects bossEnchantEffects;
    private boolean loadFailed;
    private com.example.voidscape.item.RetiredRecipeData retiredRecipes;
    @Override public void onLoad() {
        try {
            // A branding change must keep the exact layout, reward ledger and configuration.
            Path current=getDataFolder().toPath();
            Path legacy=current.resolveSibling("Voidscape");
            if(Files.isDirectory(legacy)) {
                Files.createDirectories(current);
                for(String file:List.of("config.yml","world-layout.yml","dungeons.yml","portals.yml")) {
                    Path source=legacy.resolve(file),target=current.resolve(file);
                    if(Files.exists(source)&&!Files.exists(target))Files.copy(source,target);
                }
            }
            saveDefaultConfig();WhaleTreasure.upgradeFlyingStaffRates(getConfig());
            String retiredWeight="structures.treasure.bonus-weights.key-shard";
            if(getConfig().isSet(retiredWeight)){
                getConfig().set("structures.treasure.bonus-weights.echo-shard",getConfig().getInt(retiredWeight,300));
                getConfig().set(retiredWeight,null);
            }
            com.example.voidscape.boss.JudgeBalance.upgrade(getConfig());
            getConfig().options().copyDefaults(true);saveConfig();
            // Stage old managed pet imports before ModelEngine's first generation.
            // Optional pets must never consume the slots needed by boss/internal rigs.
            var modelEngine=getServer().getPluginManager().getPlugin("ModelEngine");
            Path engineFolder=modelEngine!=null?modelEngine.getDataFolder().toPath():current.resolveSibling("ModelEngine");
            if(modelEngine!=null||Files.isDirectory(engineFolder)) {
                var pets=com.example.voidscape.pet.PetBlueprintStaging.prepare(this,engineFolder);
                getLogger().info("Pet blueprint staging before ModelEngine startup: "+pets.description());
            }
            packs=new com.example.voidscape.pack.ResourcePackService(this);packs.extract();
            retiredRecipes=new com.example.voidscape.item.RetiredRecipeData(this);retiredRecipes.start();
        } catch(Exception error) {loadFailed=true;getLogger().log(java.util.logging.Level.SEVERE,"Cannot safely initialize Evergarden",error);}
    }

    @Override public void onEnable() {
        try {
            if(loadFailed)throw new IllegalStateException("Evergarden initialization failed");
            // Subscribe before Geyser onEnable defines entities and properties.
            arkhaiaBedrock=new com.example.voidscape.boss.ArkhaiaBedrockSupport(this);
            saveDefaultConfig();
            if(getConfig().getInt("config-version",0)<2) {
                Path old=getDataFolder().toPath().resolve("config.yml");
                Path backup=getDataFolder().toPath().resolve("config-v1-backup.yml");
                if(!Files.exists(backup))Files.copy(old,backup);
                saveResource("config.yml",true);reloadConfig();
                getLogger().info("Archived v1 config; v2 uses a separate world. Original world is preserved.");
            }
            // Keep the old layout for cells that already contain generated chunks.
            File file=new File(getDataFolder(),"world-layout.yml");
            YamlConfiguration saved=YamlConfiguration.loadConfiguration(file);
            String configuredWorld=getConfig().getString("dimension.world-name","evergarden");
            if(!file.exists()) {
                saved.set("world",configuredWorld);
                saved.set("seed",getConfig().getLong("dimension.seed",72819345L));
                saved.set("spacing",integer("structures.spacing-chunks",18,14,64));
                saved.set("chance",getConfig().getDouble("structures.chance",1.0));
                saved.save(file);
            } else if(saved.getString("world","").equals("the_void") || saved.getString("world","").equals("the_void_v2")) {
                saved.set("world",configuredWorld);
                saved.save(file);
            }
            double defaultWhaleChance = 0.01;
            double whaleChance = getConfig().getDouble("structures.sky-whale.chance", defaultWhaleChance);
            if (!saved.contains("sky-whale-chance") || saved.getDouble("sky-whale-chance") > 0.50) {
                saved.set("sky-whale-chance", whaleChance);
            } else {
                whaleChance = saved.getDouble("sky-whale-chance", defaultWhaleChance);
            }
            int whaleSpacing = saved.contains("sky-whale-spacing")
                    ? Math.clamp(saved.getInt("sky-whale-spacing"),32,64)
                    : integer("structures.sky-whale.spacing-chunks",32,32,64);
            saved.set("sky-whale-spacing", whaleSpacing);
            String worldName=saved.getString("world",configuredWorld);
            if(saved.getInt("sky-rarity-version",0)<2) {
                if(Bukkit.getWorlds().isEmpty())throw new IllegalStateException("Primary world must load before sky placement migration");
                Set<Long> oldCells=SkyPlacementHistory.captureWorld(Bukkit.getWorldContainer().toPath(),
                        Bukkit.getWorlds().getFirst().getWorldFolder().toPath(),worldName,whaleSpacing);
                saved.set("sky-legacy-cells",oldCells.stream().sorted().toList());
                saved.set("sky-whale-unexplored-chance",
                        getConfig().getDouble("structures.sky-whale.unexplored-chance",0.01));
                saved.set("sky-rarity-version",2);
                getLogger().info("Preserved "+oldCells.size()+" explored sky cells at the original structure rate.");
            }
            if(saved.getInt("sky-rarity-version",0)<3){
                Set<Long> explored=new HashSet<>(SkyPlacementHistory.captureWorld(Bukkit.getWorldContainer().toPath(),
                        Bukkit.getWorlds().getFirst().getWorldFolder().toPath(),worldName,whaleSpacing));
                explored.addAll(saved.getLongList("sky-legacy-cells"));
                saved.set("restoration-legacy-cells",explored.stream().sorted().toList());
                saved.set("restoration-legacy-chance",saved.getDouble("sky-whale-unexplored-chance",.01));
                saved.set("sky-whale-unexplored-chance",getConfig().getDouble("structures.restoration.unexplored-chance",.03));
                saved.set("restoration-altar-chance",getConfig().getDouble("structures.restoration.altar-chance",.6));
                saved.set("sky-rarity-version",3);
                getLogger().info("Preserved "+explored.size()+" explored sky cells; restoration sanctuaries enabled in new cells.");
            }
            saved.save(file);
            long seed=saved.getLong("seed");
            double unexploredChance=saved.getDouble("sky-whale-unexplored-chance",0.01);
            Set<Long> oldCells=new HashSet<>(saved.getLongList("sky-legacy-cells"));
            layout=new DungeonLayout(seed,saved.getInt("spacing",18),saved.getDouble("chance",1.0));
            Set<Long> repairOldCells=new HashSet<>(saved.getLongList("restoration-legacy-cells"));
            skyWhales=new SkyWhaleLayout(seed,layout,whaleSpacing,whaleChance,unexploredChance,oldCells,
                    repairOldCells,saved.getDouble("restoration-legacy-chance",.01));
            skyLandmarks=new SkyLandmarkLayout(seed,layout,skyWhales);
            if(!saved.contains("world-boss-temple-spacing")){
                int spacing=getConfig().isInt("structures.world-boss-temple.spacing-chunks")
                    ?integer("structures.world-boss-temple.spacing-chunks",whaleSpacing,32,64):whaleSpacing;
                double chance=getConfig().isSet("structures.world-boss-temple.chance")
                    ?getConfig().getDouble("structures.world-boss-temple.chance",unexploredChance):unexploredChance;
                saved.set("world-boss-temple-spacing",spacing);
                saved.set("world-boss-temple-chance",Double.isFinite(chance)?Math.clamp(chance,0,1):0);
                var explored=SkyPlacementHistory.captureWorld(Bukkit.getWorldContainer().toPath(),
                    Bukkit.getWorlds().getFirst().getWorldFolder().toPath(),worldName,spacing);
                saved.set("world-boss-temple-excluded-cells",explored.stream().sorted().toList());
                saved.save(file);
                getLogger().info("Ancient world boss temples enabled in unexplored cells; preserved "+explored.size()+" explored cells.");
            }
            bossTemples=new WorldBossTempleLayout(seed,layout,skyWhales,skyLandmarks,
                saved.getInt("world-boss-temple-spacing"),saved.getDouble("world-boss-temple-chance"),
                new HashSet<>(saved.getLongList("world-boss-temple-excluded-cells")));
            if(!saved.contains("boss-structures.spacing")){
                int spacing=getConfig().isInt("structures.boss-structures.spacing-chunks")
                    ?integer("structures.boss-structures.spacing-chunks",whaleSpacing,32,64):whaleSpacing;
                saved.set("boss-structures.spacing",spacing);
                saved.set("boss-structures.minimum-gap",integer("structures.boss-structures.minimum-gap",64,32,128));
                for(var kind:BossStructureLayout.Kind.values()){
                    String path="structures.boss-structures."+kind.id+".chance";
                    double rate=getConfig().isSet(path)?getConfig().getDouble(path):unexploredChance;
                    saved.set("boss-structures.chances."+kind.id,Double.isFinite(rate)?Math.clamp(rate,0,1):0);
                }
                var explored=SkyPlacementHistory.captureWorld(Bukkit.getWorldContainer().toPath(),
                    Bukkit.getWorlds().getFirst().getWorldFolder().toPath(),worldName,spacing);
                saved.set("boss-structures.excluded-cells",explored.stream().sorted().toList());
                saved.save(file);
                getLogger().info("Three boss structures enabled in unexplored sky cells; preserved "+explored.size()+" explored cells.");
            }
            if(saved.getInt("boss-structures.geometry-version",1)<2){
                var legacyDesignCells=SkyPlacementHistory.captureWorld(Bukkit.getWorldContainer().toPath(),
                    Bukkit.getWorlds().getFirst().getWorldFolder().toPath(),worldName,saved.getInt("boss-structures.spacing"));
                saved.set("boss-structures.legacy-design-cells",legacyDesignCells.stream().sorted().toList());
                saved.set("boss-structures.geometry-version",2);saved.save(file);
                getLogger().info("Boss structure architecture v2 enabled in fresh cells; preserved "+legacyDesignCells.size()+" explored cells.");
            }
            var structureChances=new EnumMap<BossStructureLayout.Kind,Double>(BossStructureLayout.Kind.class);
            var enabledStructures=EnumSet.noneOf(BossStructureLayout.Kind.class);
            for(var kind:BossStructureLayout.Kind.values()){
                structureChances.put(kind,saved.getDouble("boss-structures.chances."+kind.id,unexploredChance));
                if(getConfig().getBoolean("structures.boss-structures."+kind.id+".enabled",true))enabledStructures.add(kind);
            }
            bossStructures=new BossStructureLayout(seed,layout,skyWhales,skyLandmarks,bossTemples,
                saved.getInt("boss-structures.spacing"),saved.getInt("boss-structures.minimum-gap",64),
                structureChances,enabledStructures,new HashSet<>(saved.getLongList("boss-structures.excluded-cells")),
                new HashSet<>(saved.getLongList("boss-structures.legacy-design-cells")));
            restorationLayout=new RestorationLayout(seed,skyWhales,skyLandmarks,repairOldCells,
                    saved.getDouble("restoration-altar-chance",.6),getConfig().getBoolean("structures.sky-whale.enabled",true),
                    getConfig().getBoolean("structures.hanging-garden.enabled",true),getConfig().getBoolean("structures.observatory.enabled",true));
            if(!saved.contains("terrain.version")){
                var legacyRegions=SkyPlacementHistory.captureWorld(Bukkit.getWorldContainer().toPath(),
                    Bukkit.getWorlds().getFirst().getWorldFolder().toPath(),worldName,32);
                saved.set("terrain.version",1);
                saved.set("terrain.biome-size",integer("dimension.terrain.biome-size",2048,1536,8192));
                saved.set("terrain.legacy-regions",legacyRegions.stream().sorted().toList());
                saved.save(file);
                getLogger().info("Continental biomes enabled in fresh terrain; preserved "+legacyRegions.size()+" existing region files with gradual transitions.");
            }
            if(saved.getInt("terrain.version")!=1)throw new IllegalStateException("Unsupported saved terrain version; refusing to change world generation.");
            if(!saved.contains("scenery.version")){
                var exploredRegions=SkyPlacementHistory.captureWorld(Bukkit.getWorldContainer().toPath(),
                    Bukkit.getWorlds().getFirst().getWorldFolder().toPath(),worldName,32);
                saved.set("scenery.version",1);
                saved.set("scenery.legacy-regions",exploredRegions.stream().sorted().toList());
                double rate=getConfig().getDouble("structures.exploration.chance",.35);
                saved.set("scenery.exploration-chance",Double.isFinite(rate)?Math.clamp(rate,0,1):.35);
                saved.save(file);
            }
            if(saved.getInt("scenery.version")!=1)throw new IllegalStateException("Unsupported saved scenery version");
            if(!saved.contains("scenery.exploration-geometry-version")){
                var previousCells=SkyPlacementHistory.captureWorld(Bukkit.getWorldContainer().toPath(),
                    Bukkit.getWorlds().getFirst().getWorldFolder().toPath(),worldName,16);
                saved.set("scenery.exploration-legacy-cells",previousCells.stream().sorted().toList());
                saved.set("scenery.exploration-geometry-version",2);saved.save(file);
            }
            if(saved.getInt("scenery.exploration-geometry-version")==2){
                var previousCells=SkyPlacementHistory.captureWorld(Bukkit.getWorldContainer().toPath(),
                    Bukkit.getWorlds().getFirst().getWorldFolder().toPath(),worldName,16);
                previousCells.removeAll(saved.getLongList("scenery.exploration-legacy-cells"));
                saved.set("scenery.exploration-v2-cells",previousCells.stream().sorted().toList());
                saved.set("scenery.exploration-geometry-version",3);saved.save(file);
            }
            if(saved.getInt("scenery.exploration-geometry-version")==3){
                var detailedCells=SkyPlacementHistory.captureWorld(Bukkit.getWorldContainer().toPath(),
                    Bukkit.getWorlds().getFirst().getWorldFolder().toPath(),worldName,16);
                detailedCells.removeAll(saved.getLongList("scenery.exploration-legacy-cells"));
                detailedCells.removeAll(saved.getLongList("scenery.exploration-v2-cells"));
                saved.set("scenery.exploration-v3-cells",detailedCells.stream().sorted().toList());
                saved.set("scenery.exploration-geometry-version",4);saved.save(file);
            }
            if(saved.getInt("scenery.exploration-geometry-version")==4){
                var mediumCells=SkyPlacementHistory.captureWorld(Bukkit.getWorldContainer().toPath(),
                    Bukkit.getWorlds().getFirst().getWorldFolder().toPath(),worldName,16);
                mediumCells.removeAll(saved.getLongList("scenery.exploration-legacy-cells"));
                mediumCells.removeAll(saved.getLongList("scenery.exploration-v2-cells"));
                mediumCells.removeAll(saved.getLongList("scenery.exploration-v3-cells"));
                saved.set("scenery.exploration-v4-cells",mediumCells.stream().sorted().toList());
                saved.set("scenery.exploration-geometry-version",5);saved.save(file);
            }
            if(saved.getInt("scenery.exploration-geometry-version")!=5)throw new IllegalStateException("Unsupported exploration structure geometry version");
            if(!saved.contains("scenery.details-version")){
                saved.set("scenery.details-legacy-regions",SkyPlacementHistory.captureWorld(Bukkit.getWorldContainer().toPath(),
                    Bukkit.getWorlds().getFirst().getWorldFolder().toPath(),worldName,32).stream().sorted().toList());
                saved.set("scenery.details-version",2);saved.save(file);
            }
            if(saved.getInt("scenery.details-version")!=2)throw new IllegalStateException("Unsupported scenery details version");
            biomeTerrain=new BiomeTerrain(seed,saved.getInt("terrain.biome-size",3072),
                new HashSet<>(saved.getLongList("terrain.legacy-regions")),new HashSet<>(saved.getLongList("scenery.legacy-regions")),
                new HashSet<>(saved.getLongList("scenery.details-legacy-regions")));
            if(worldName.equals("the_void"))throw new IllegalStateException("Use a new world name for Evergarden; never replace the legacy world generator.");
            voidWorld=new WorldCreator(worldName).seed(seed).environment(World.Environment.NORMAL).generator(new VoidGenerator(seed,layout,skyWhales,getConfig().getBoolean("structures.sky-whale.enabled",true),
                    skyLandmarks,getConfig().getBoolean("structures.observatory.enabled",true),getConfig().getBoolean("structures.hanging-garden.enabled",true),restorationLayout,
                    bossTemples,getConfig().getBoolean("structures.world-boss-temple.enabled",true),bossStructures,biomeTerrain,
                    getConfig().getBoolean("structures.exploration.enabled",true),saved.getDouble("scenery.exploration-chance",.35),
                    new HashSet<>(saved.getLongList("scenery.exploration-legacy-cells")),
                    new HashSet<>(saved.getLongList("scenery.exploration-v2-cells")),
                    new HashSet<>(saved.getLongList("scenery.exploration-v3-cells")),
                    new HashSet<>(saved.getLongList("scenery.exploration-v4-cells")))).createWorld();
            if(voidWorld==null)throw new IllegalStateException("Cannot load Evergarden world");
            exploration=((VoidGenerator)voidWorld.getGenerator()).exploration();
            voidWorld.setSpawnLocation(0,97,0);voidWorld.setTime(integer("dimension.time",13000,0,23999));
            voidWorld.setGameRule(GameRule.DO_DAYLIGHT_CYCLE,false);
            voidWorld.setGameRule(GameRule.DO_WEATHER_CYCLE,false);
            voidWorld.setGameRule(GameRule.DO_MOB_SPAWNING,false);
            voidWorld.setGameRule(GameRule.DO_PATROL_SPAWNING,false);
            voidWorld.setGameRule(GameRule.DO_TRADER_SPAWNING,false);
            voidWorld.setStorm(false);voidWorld.setThundering(false);
            voidWorld.getWorldBorder().setCenter(0,0);
            voidWorld.getWorldBorder().setSize(integer("dimension.border-size",24000,4096,60000000));
            com.example.voidscape.compat.LevelledMobsCompat.register(this);
            relics=new RelicService(this);adventurerWeapons=new com.example.voidscape.item.AdventurerWeaponService(this);dungeons=new DungeonManager(this);travel=new TravelListener(this);
            var pm=getServer().getPluginManager();pm.registerEvents(relics,this);pm.registerEvents(adventurerWeapons,this);pm.registerEvents(dungeons,this);pm.registerEvents(travel,this);
            overworldSanctuaries=new OverworldSanctuaries(this);
            pm.registerEvents(retiredRecipes,this);
            pm.registerEvents(new com.example.voidscape.item.RemovedRelics(),this);
            Bukkit.getWorlds().forEach(world->{for(Chunk chunk:world.getLoadedChunks())com.example.voidscape.item.RemovedRelics.chunk(chunk);});
            worldBoss=new com.example.voidscape.boss.WorldBossManager(this);pm.registerEvents(worldBoss,this);
            structureProtection=new StructureProtection(this);pm.registerEvents(structureProtection,this);
            pm.registerEvents(new BossStructureTraps(this),this);
            pm.registerEvents(new com.example.voidscape.guide.ChestGuideGui(this),this);
            pm.registerEvents(new com.example.voidscape.enchant.EnchantApplyListener(this,relics),this);
            abilities=new com.example.voidscape.enchant.UniqueAbilityListener(this);pm.registerEvents(abilities,this);
            bossEnchants=new com.example.voidscape.enchant.BossEnchantService(this);
            pm.registerEvents(new com.example.voidscape.enchant.BossEnchantAnvil(bossEnchants),this);
            bossEnchantEffects=new com.example.voidscape.enchant.BossEnchantEffects(this,bossEnchants);pm.registerEvents(bossEnchantEffects,this);
            testGui=new com.example.voidscape.gui.AdminTestGui(this);pm.registerEvents(testGui,this);
            wandGui=new com.example.voidscape.gui.WandShowcaseGui(this);pm.registerEvents(wandGui,this);
            relicGui=new com.example.voidscape.gui.RelicShowcaseGui(this);pm.registerEvents(relicGui,this);
            guideMenu=new com.example.voidscape.guide.GuideMenuGui(this);pm.registerEvents(guideMenu,this);
            if(packs==null){packs=new com.example.voidscape.pack.ResourcePackService(this);try{packs.extract();}catch(Throwable ignored){}}
            packs.start();pm.registerEvents(packs,this);
            judgePack=new com.example.voidscape.boss.JudgePackService(this);judgePack.start();pm.registerEvents(judgePack,this);
            arkhaiaModels=new com.example.voidscape.boss.ArkhaiaModelSupport(this);
            petPreviews=new com.example.voidscape.pet.PetModelPreviews(this);
            pets=new com.example.voidscape.pet.PetSystem(this);
            var petCommand=getCommand("pet");
            if(petCommand!=null){petCommand.setExecutor(pets.menu());petCommand.setTabCompleter(pets.menu());}
            seraphTrader=new com.example.voidscape.trade.SeraphTrader(this);
            sanctuaryEncounters=new com.example.voidscape.boss.SanctuaryEncounters(this);
            seraphonBoss=new com.example.voidscape.boss.SeraphonBoss(this);
            var whaleTreasure = new WhaleTreasure(this);
            var magicPlugin=pm.getPlugin("advance-magic");
            if(magicPlugin instanceof com.example.advancemagic.AdvanceMagicPlugin magic&&magic.isEnabled()){
                restorationAltars=new RestorationAltars(this,magic);pm.registerEvents(restorationAltars,this);
                try{
                    magic.getClass().getClassLoader().loadClass("com.example.advancemagic.api.MagicTerrainEvent");
                    pm.registerEvents(new StructureMagicProtection(this),this);
                }catch(ClassNotFoundException oldMagic){getLogger().warning("Update Advance Magic to protect Evergarden structures from temporary spell terrain.");}
            }else getLogger().warning("Advance Magic is unavailable; restoration altar interactions are disabled.");
            pm.registerEvents(whaleTreasure,this);
            for (Chunk chunk : voidWorld.getLoadedChunks()) whaleTreasure.populate(chunk);
            var explorationTreasure=new ExplorationTreasure(this,exploration);pm.registerEvents(explorationTreasure,this);
            for(Chunk chunk:voidWorld.getLoadedChunks())explorationTreasure.populate(chunk);
            try {
                org.bukkit.block.Block lecternBlock = voidWorld.getBlockAt(0, 97, 4);
                if (lecternBlock.getType() == Material.LECTERN && lecternBlock.getState() instanceof org.bukkit.block.Lectern lectern) {
                    if (lectern.getInventory() instanceof org.bukkit.inventory.LecternInventory lecternInv) {
                        if (lecternInv.getBook() == null || lecternInv.getBook().getItemMeta().getPersistentDataContainer()
                                .has(key("guide_book"), org.bukkit.persistence.PersistentDataType.BYTE)) {
                            lecternInv.setBook(relics.createGuideBook());
                            lectern.update(true, false);
                        }
                    }
                }
            } catch (Throwable ignored) {}
            getServer().getOnlinePlayers().forEach(p->{com.example.voidscape.item.RemovedRelics.player(p);relics.migrate(p.getInventory());relics.migrate(p.getEnderChest());adventurerWeapons.migrateInventory(p.getInventory());adventurerWeapons.migrateInventory(p.getEnderChest());packs.offer(p);judgePack.offer(p);});
            pm.registerEvents(new com.example.voidscape.gui.ChestUpgradeGui(this),this);
            pm.registerEvents(new com.example.voidscape.gui.LegacyEnchantResolutionGui(this),this);
            VoidCommand command=new VoidCommand(this);
            getCommand("evergarden").setExecutor(command);getCommand("evergarden").setTabCompleter(command);
            if (getCommand("upgrade") != null) {
                getCommand("upgrade").setExecutor(command);
                getCommand("upgrade").setTabCompleter(command);
            }
            getServer().getScheduler().runTaskTimer(this,()->{dungeons.tick();travel.tick();},20,10);
            getServer().getScheduler().runTaskTimer(this,()->worldBoss.tick(),20,2);
            getLogger().info("Evergarden 3.0 enabled in "+worldName);
        } catch(Exception e) {
            getLogger().log(java.util.logging.Level.SEVERE,"Evergarden failed to start safely",e);
            getServer().getPluginManager().disablePlugin(this);
        }
    }
    @Override public void onDisable(){if(seraphonBoss!=null)seraphonBoss.close();if(seraphTrader!=null)seraphTrader.close();if(pets!=null)pets.close();if(petPreviews!=null)petPreviews.close();if(sanctuaryEncounters!=null)sanctuaryEncounters.close();if(overworldSanctuaries!=null)overworldSanctuaries.close();if(bossEnchantEffects!=null)bossEnchantEffects.close();if(worldBoss!=null)worldBoss.close();if(arkhaiaBedrock!=null)arkhaiaBedrock.close();if(arkhaiaModels!=null)arkhaiaModels.close();if(judgePack!=null)judgePack.close();if(restorationAltars!=null)restorationAltars.close();if(travel!=null)travel.close();if(packs!=null)packs.close();if(dungeons!=null)dungeons.close();}

    public NamespacedKey key(String value){return new NamespacedKey("voidscape",value);}
    public int integer(String path,int value,int min,int max){return Math.max(min,Math.min(max,getConfig().getInt(path,value)));}
    public void message(CommandSender sender,String text){sender.sendMessage(Component.text("✦ "+text,NamedTextColor.AQUA));}
    public com.example.voidscape.pack.ResourcePackService packs(){return packs;}
    public com.example.voidscape.boss.JudgePackService judgePack(){return judgePack;}
    public com.example.voidscape.boss.ArkhaiaModelSupport arkhaiaModels(){return arkhaiaModels;}
    public com.example.voidscape.pet.PetModelPreviews petPreviews(){return petPreviews;}
    public com.example.voidscape.pet.WildPetSpawns wildPets(){return pets==null?null:pets.wild();}
    public com.example.voidscape.pet.PetSystem pets(){return pets;}
    public com.example.voidscape.trade.SeraphTrader seraphTrader(){return seraphTrader;}
    public com.example.voidscape.boss.ArkhaiaBedrockSupport arkhaiaBedrock(){return arkhaiaBedrock;}
    public World world(){return voidWorld;} public DungeonLayout layout(){return layout;}
    public com.example.voidscape.boss.WorldBossManager worldBoss(){return worldBoss;}
    public SkyWhaleLayout skyWhales(){return skyWhales;}
    public SkyLandmarkLayout skyLandmarks(){return skyLandmarks;}
    public WorldBossTempleLayout bossTemples(){return bossTemples;}
    public BossStructureLayout bossStructures(){return bossStructures;}
    public BiomeTerrain biomeTerrain(){return biomeTerrain;}
    public ExplorationLayout exploration(){return exploration;}
    public StructureProtection structureProtection(){return structureProtection;}
    public RestorationLayout restorationLayout(){return restorationLayout;}
    public RelicService relics(){return relics;} public com.example.voidscape.item.AdventurerWeaponService adventurerWeapons(){return adventurerWeapons;} public DungeonManager dungeons(){return dungeons;} public TravelListener travel(){return travel;}
    public com.example.voidscape.gui.AdminTestGui testGui(){return testGui;}
    public com.example.voidscape.gui.WandShowcaseGui wandGui(){return wandGui;}
    public com.example.voidscape.gui.RelicShowcaseGui relicGui(){return relicGui;}
    public com.example.voidscape.guide.GuideMenuGui guideMenu(){return guideMenu;}
    public com.example.voidscape.enchant.UniqueAbilityListener abilities(){return abilities;}
    public com.example.voidscape.enchant.BossEnchantService bossEnchants(){return bossEnchants;}
    public com.example.voidscape.enchant.BossEnchantEffects bossEnchantEffects(){return bossEnchantEffects;}
}
