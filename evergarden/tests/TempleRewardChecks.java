import com.example.advancemagic.AdvanceMagicPlugin;
import com.example.advancemagic.elemental.ElementalSpell;
import com.example.advancemagic.elemental.SpellLevels;
import com.example.voidscape.VoidscapePlugin;
import com.example.voidscape.boss.JudgmentFight;
import com.example.voidscape.boss.WorldBossManager;
import com.example.voidscape.pet.PetCatalog;
import com.example.voidscape.world.WorldBossTempleLayout.Site;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;

/**
 * Real Paper victory and persisted claim transactions; no reward-preview shortcuts.
 * e2.203 rewards: one Tier VII and one Tier VI Vault key, one baby pet egg of any species (equal odds, mythical included),
 * one Legendary spell scroll, plus the unchanged XP, skill point and Seraph feathers.
 */
public final class TempleRewardChecks extends JavaPlugin {
    private VoidscapePlugin garden;
    private AdvanceMagicPlugin magic;
    private WorldBossManager manager;
    private YamlConfiguration ledger;
    private Path ledgerFile;
    private Class<?> runType;
    private Constructor<?> runConstructor;
    private Method victory;
    private net.minecraft.server.level.ServerLevel level;
    private final List<net.minecraft.server.level.ServerPlayer> actors=new ArrayList<>();
    private int passed,siteCounter;
    private final StringBuilder audit=new StringBuilder();
    private int tier7,tier6,eggs,xp,feathers;

    @Override public void onEnable(){Bukkit.getScheduler().runTaskLater(this,()->{
        try{run();finish(null);}catch(Throwable error){finish(error);}
    },20);}
    private void check(boolean condition,String message){if(!condition)throw new AssertionError(message);passed++;}
    private void finish(Throwable error){
        if(error!=null)getLogger().log(java.util.logging.Level.SEVERE,"TEMPLE REWARD CHECK FAILED",error);
        for(var actor:actors)disconnect(actor);
        String report=(error==null?"PASS ":"FAIL ")+passed+" checks; real Paper temple victory and persisted claims; "+
            (error==null?"Tier VII + Tier VI keys, one baby pet egg and one Legendary scroll, offline/dead/full inventory, ledger reload and exactly-once claims":error.toString());
        try{Files.writeString(Path.of("temple-reward-result.txt"),report+"\n"+audit);}
        catch(Exception failure){throw new RuntimeException(failure);}
        getLogger().info(report);Bukkit.getScheduler().runTaskLater(this,Bukkit::shutdown,1);
    }
    private static Field field(Class<?> type,String name)throws Exception{var result=type.getDeclaredField(name);result.setAccessible(true);return result;}
    private static String reward(UUID id){return "rewards."+id;}
    private YamlConfiguration disk(){return YamlConfiguration.loadConfiguration(ledgerFile.toFile());}
    private void persist()throws Exception{ledger.save(ledgerFile.toFile());}
    private void reload()throws Exception{ledger.load(ledgerFile.toFile());}
    private void run()throws Exception{
        garden=(VoidscapePlugin)Bukkit.getPluginManager().getPlugin("Evergarden");
        magic=(AdvanceMagicPlugin)Bukkit.getPluginManager().getPlugin("advance-magic");
        check(garden!=null&&garden.isEnabled(),"Evergarden enabled");
        check(magic!=null&&magic.isEnabled(),"Advance Magic enabled");
        manager=garden.worldBoss();check(manager!=null,"real Boss-temple manager initialized");
        check(garden.pets()!=null,"pet system ready for reward eggs");
        ledger=(YamlConfiguration)field(WorldBossManager.class,"ledger").get(manager);
        ledgerFile=((java.io.File)field(WorldBossManager.class,"file").get(manager)).toPath();
        ledger.set("rewards",null);persist();
        runType=Class.forName("com.example.voidscape.boss.WorldBossManager$Run");
        runConstructor=runType.getDeclaredConstructor(Site.class,Location.class,JudgmentFight.class);runConstructor.setAccessible(true);
        victory=WorldBossManager.class.getDeclaredMethod("victory",runType);victory.setAccessible(true);
        level=((org.bukkit.craftbukkit.CraftWorld)garden.world()).getHandle();
        tier7=garden.integer("world-boss.rewards.tier7-keys",1,0,16);
        tier6=garden.integer("world-boss.rewards.tier6-keys",1,0,16);
        eggs=garden.integer("world-boss.rewards.eggs",1,0,8);
        xp=garden.integer("world-boss.rewards.experience",1500,0,1000000);
        feathers=garden.integer("seraph-trader.feathers.world-boss",15,0,64);
        check(tier7==1&&tier6==1&&eggs==1&&xp==1500&&feathers==15,"defaults: one Tier VII key, one Tier VI key, one egg, 1500 XP and 15 feathers");
        check(garden.seraphTrader()!=null,"Seraph feather provider ready");
        eggOdds();
        onlineVictory();offlinePersistence();accumulatedVictories();deadClaim();inventoryAtomicity();legacyClaim();testEncounter();
        missingScrollProvider();deferredSelection();failedClaimSave();failedVictorySave();disabledMagicVictory();
    }
    /** Every species, mythical included, comes up with equal odds. */
    private void eggOdds()throws Exception{
        Method roll=WorldBossManager.class.getDeclaredMethod("rollEgg");roll.setAccessible(true);
        var species=PetCatalog.available();int draws=species.size()*400;Map<PetCatalog.Species,Integer> seen=new HashMap<>();
        for(int i=0;i<draws;i++)seen.merge((PetCatalog.Species)roll.invoke(manager),1,Integer::sum);
        check(seen.size()==species.size(),"all "+species.size()+" species can hatch from the Judge's egg");
        check(species.stream().filter(PetCatalog.Species::mythical).allMatch(seen::containsKey),"the five mythical species are in the pool");
        int low=seen.values().stream().mapToInt(Integer::intValue).min().orElse(0),high=seen.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        check(low>=280&&high<=520,"equal odds per species (400 expected; seen "+low+".."+high+" over "+draws+" draws)");
        audit.append("Egg odds: ").append(species.size()).append(" species, ").append(draws).append(" draws, per-species ").append(low).append("..").append(high).append('\n');
    }
    private net.minecraft.server.level.ServerPlayer actor(boolean online){
        var server=net.minecraft.server.MinecraftServer.getServer();
        var profile=new com.mojang.authlib.GameProfile(UUID.randomUUID(),"TempleCheck"+actors.size());
        var actor=new net.minecraft.server.level.ServerPlayer(server,level,profile,net.minecraft.server.level.ClientInformation.createDefault());
        var connection=new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        connection.channel=new io.netty.channel.embedded.EmbeddedChannel();connection.address=new java.net.InetSocketAddress("127.0.0.1",1);
        actor.connection=new net.minecraft.server.network.ServerGamePacketListenerImpl(server,connection,actor,net.minecraft.server.network.CommonListenerCookie.createInitial(profile,false)){
            @Override public boolean hasClientLoaded(){return true;}
        };
        actor.setPos(.5,150,.5);var player=actor.getBukkitEntity();player.setGravity(false);player.setGameMode(GameMode.SURVIVAL);
        player.getInventory().clear();player.setLevel(0);player.setExp(0);player.setTotalExperience(0);actors.add(actor);
        if(online)connect(actor);return actor;
    }
    private void connect(net.minecraft.server.level.ServerPlayer actor){
        var server=net.minecraft.server.MinecraftServer.getServer();
        if(!server.getPlayerList().getPlayers().contains(actor))server.getPlayerList().getPlayers().add(actor);
        server.getPlayerList().getPlayersByUUID().put(actor.getUUID(),actor);
        if(level.getEntity(actor.getUUID())==null)level.addNewPlayer(actor);
    }
    private void disconnect(net.minecraft.server.level.ServerPlayer actor){
        var server=net.minecraft.server.MinecraftServer.getServer();
        server.getPlayerList().getPlayers().remove(actor);server.getPlayerList().getPlayersByUUID().remove(actor.getUUID());actor.discard();
    }
    private Object won(UUID... contributors)throws Exception{
        var fight=new JudgmentFight(100,20,0,200,8);
        for(UUID id:contributors){check(fight.join(id),"participant joins actual JudgmentFight");check(fight.hit(id,JudgmentFight.Part.LEFT,1,100).damage()>0,"participant earns real damage credit");}
        UUID winner=contributors[0];
        for(int phase=1;phase<=2;phase++){
            fight.hit(winner,JudgmentFight.Part.LEFT,100,100+phase);
            fight.hit(winner,JudgmentFight.Part.CORE,100,100+phase);
        }
        check(fight.hit(winner,JudgmentFight.Part.CORE,100,104).won(),"real three-phase damage rules reach victory");
        return runConstructor.newInstance(new Site(800+(siteCounter++)*256,800),new Location(garden.world(),.5,150,.5),fight);
    }
    private void defeat(Object run)throws Exception{
        try{victory.invoke(manager,run);}catch(InvocationTargetException failure){throw new RuntimeException(failure.getCause());}
    }
    private int keyCount(Player player,int tier){int result=0;for(var item:player.getInventory().getStorageContents())if(garden.relics().isVoidKey(item)&&garden.relics().getVoidKeyTier(item)==tier)result+=item.getAmount();return result;}
    private int taggedKeyCount(Player player){int result=0;for(var item:player.getInventory().getStorageContents())if(garden.relics().isVoidKey(item)&&item.getItemMeta().getPersistentDataContainer().has(garden.key("key_tier")))result+=item.getAmount();return result;}
    /** Keys carrying an explicit tier tag (an untagged legacy Master Key also reads as tier V). */
    private int taggedKeyCount(Player player,int tier){int result=0;for(var item:player.getInventory().getStorageContents())if(garden.relics().isVoidKey(item)&&item.getItemMeta().getPersistentDataContainer().has(garden.key("key_tier"))&&garden.relics().getVoidKeyTier(item)==tier)result+=item.getAmount();return result;}
    private List<ItemStack> scrolls(Player player){return Arrays.stream(player.getInventory().getStorageContents()).filter(Objects::nonNull).filter(i->magic.staffSpells().scroll(i)!=null).toList();}
    private List<String> eggIds(Player player){
        List<String> ids=new ArrayList<>();
        for(var item:player.getInventory().getStorageContents())if(item!=null&&item.hasItemMeta()){
            String id=item.getItemMeta().getPersistentDataContainer().get(garden.key("pet_egg"),PersistentDataType.STRING);if(id!=null)ids.add(id);
        }
        return ids;
    }
    private YamlConfiguration eggLedger()throws Exception{Object store=field(com.example.voidscape.pet.PetSystem.class,"store").get(garden.pets());return (YamlConfiguration)field(store.getClass(),"eggs").get(store);}
    /** The species of each delivered egg, checked against the pet ledger: registered, owned, unhatched, a baby. */
    private List<String> eggSpecies(Player player)throws Exception{
        List<String> result=new ArrayList<>();var eggsLedger=eggLedger();
        for(String id:eggIds(player)){
            String base="eggs."+id;
            check(eggsLedger.contains(base),"a reward egg is registered in the pet egg ledger");
            check(player.getUniqueId().toString().equals(eggsLedger.getString(base+".owner"))&&!eggsLedger.getBoolean(base+".hatched"),"the egg belongs to the winner and is unhatched");
            check("baby".equals(eggsLedger.getString(base+".pet.stage")),"the egg hatches as a baby");
            String species=eggsLedger.getString(base+".species");check(PetCatalog.Species.find(species)!=null,"the egg holds a real species: "+species);
            result.add(species);
        }
        return result;
    }
    private int itemCount(Player player){return Arrays.stream(player.getInventory().getStorageContents()).filter(Objects::nonNull).filter(i->!i.getType().isAir()).mapToInt(ItemStack::getAmount).sum();}
    /** The whole bundle of n victories in the inventory, nothing else. */
    private void physicalReward(Player player,int victories)throws Exception{
        check(keyCount(player,7)==tier7*victories,"exact Tier VII key count");
        check(keyCount(player,6)==tier6*victories,"exact Tier VI key count");
        check(taggedKeyCount(player)==(tier7+tier6)*victories,"new temple keys carry their explicit tier tag");
        var scrolls=scrolls(player);check(scrolls.stream().mapToInt(ItemStack::getAmount).sum()==victories,"exactly one scroll per real victory");
        for(var scroll:scrolls){check(magic.staffSpells().scroll(scroll).rarity()==ElementalSpell.Rarity.LEGENDARY,"actual usable scroll is Legendary");check(magic.staffSpells().scrollLevel(scroll)==SpellLevels.BASE,"Legendary scroll retains normal base spell level");}
        check(eggSpecies(player).size()==eggs*victories,"exactly one baby egg per real victory");
        check(garden.seraphTrader().feathers().carried(player)==feathers*victories,"existing physical Seraph feathers retained");
        check(itemCount(player)==(tier7+tier6+1+eggs)*victories+feathers*victories,"no mana, cores, Tier V keys or unrequested extras");
    }
    private List<String> queued(UUID id){return queued(id,0);}
    private List<String> queued(UUID id,int legacyKeys){
        var stored=disk();String path=reward(id);var spells=stored.getStringList(path+".legendary-spells");
        check(stored.getInt(path+".tier7-keys")==tier7&&stored.getInt(path+".tier6-keys")==tier6,"victory durably queues one Tier VII and one Tier VI key");
        check(stored.getInt(path+".tier5-keys")==0,"new victories no longer queue Tier V keys");
        check(stored.getInt(path+".keys")==legacyKeys,"new victory never changes legacy key bucket");
        var queuedEggs=stored.getStringList(path+".eggs");
        check(queuedEggs.size()==eggs&&queuedEggs.stream().allMatch(s->PetCatalog.Species.find(s)!=null),"the egg's species is chosen and durably stored at victory");
        check(spells.size()==1,"exactly one selected spell is durably persisted before claim");
        check(ElementalSpell.parse(spells.getFirst())!=null&&ElementalSpell.parse(spells.getFirst()).rarity()==ElementalSpell.Rarity.LEGENDARY,"persisted spell ID is a real Legendary");
        check(stored.getInt(path+".xp")==xp&&stored.getInt(path+".skill-points")==1&&stored.getInt(path+".feathers")==feathers,"existing XP, one skill point and feathers all durably queued");
        return spells;
    }
    private void claimed(Player player){check(!disk().contains(reward(player.getUniqueId())),"successful claim removal is durable before delivery");}
    private void noReplay(Player player)throws Exception{
        int items=itemCount(player),xp=player.getTotalExperience(),points=magic.skillTree().earnedPoints(player);int ledgerEggs=eggLedger().getConfigurationSection("eggs").getKeys(false).size();reload();
        for(int i=0;i<3;i++)manager.claimReward(player);
        check(itemCount(player)==items&&player.getTotalExperience()==xp&&magic.skillTree().earnedPoints(player)==points,"repeated claim after ledger reload delivers nothing twice");
        check(eggLedger().getConfigurationSection("eggs").getKeys(false).size()==ledgerEggs,"repeated claim registers no extra egg");
    }
    private void onlineVictory()throws Exception{
        var actor=actor(true);Player player=actor.getBukkitEntity();int before=magic.skillTree().earnedPoints(player);
        Object run=won(actor.getUUID());var fight=(JudgmentFight)field(runType,"fight").get(run);UUID observer=UUID.randomUUID();fight.join(observer);
        defeat(run);physicalReward(player,1);claimed(player);
        check(player.getTotalExperience()==xp,"online victory retains vanilla XP");
        check(magic.skillTree().earnedPoints(player)==before+1,"online victory retains one skill point");
        check(!disk().contains(reward(observer)),"zero-damage roster spectator receives no reward");
        audit.append("Online victory: Tier VII=").append(keyCount(player,7)).append(" Tier VI=").append(keyCount(player,6)).append(" + egg ").append(eggSpecies(player))
            .append(" + ").append(magic.staffSpells().scroll(scrolls(player).getFirst()).id()).append(" scroll, XP=").append(xp).append(", feathers=").append(feathers).append('\n');
        noReplay(player);
    }
    private void offlinePersistence()throws Exception{
        var actor=actor(false);Player player=actor.getBukkitEntity();check(!player.isOnline(),"offline fixture absent from server player map");
        defeat(won(actor.getUUID()));var selected=queued(actor.getUUID());var egg=disk().getStringList(reward(actor.getUUID())+".eggs");
        check(itemCount(player)==0&&player.getTotalExperience()==0,"offline victory cannot deliver inventory or XP");
        reload();connect(actor);manager.claimReward(player);physicalReward(player,1);claimed(player);
        check(magic.staffSpells().scroll(scrolls(player).getFirst()).id().equals(selected.getFirst()),"reconnect/reload claim delivers the persisted selection without reroll");
        check(eggSpecies(player).equals(egg),"reconnect/reload claim hatches the stored egg species without reroll");noReplay(player);
    }
    private void deadClaim()throws Exception{
        var actor=actor(true);Player player=actor.getBukkitEntity();actor.setHealth(0);check(player.isDead(),"dead online fixture");
        defeat(won(actor.getUUID()));var selected=queued(actor.getUUID());manager.claimReward(player);
        check(itemCount(player)==0&&player.getTotalExperience()==0,"dead player receives neither bundle nor XP");
        check(disk().getStringList(reward(actor.getUUID())+".legendary-spells").equals(selected),"dead claim leaves exact selected spell queued");
        actor.setHealth(20);check(!player.isDead(),"revived fixture can retry claim");manager.claimReward(player);physicalReward(player,1);claimed(player);noReplay(player);
    }
    private void accumulatedVictories()throws Exception{
        var actor=actor(false);Player player=actor.getBukkitEntity();defeat(won(actor.getUUID()));var first=queued(actor.getUUID());
        var firstEgg=disk().getStringList(reward(actor.getUUID())+".eggs");defeat(won(actor.getUUID()));
        var stored=disk();String path=reward(actor.getUUID());var selected=stored.getStringList(path+".legendary-spells");var queuedEggs=stored.getStringList(path+".eggs");
        check(stored.getInt(path+".tier7-keys")==tier7*2&&stored.getInt(path+".tier6-keys")==tier6*2,"two offline victories accumulate Tier VII and Tier VI keys");
        check(queuedEggs.size()==eggs*2&&queuedEggs.getFirst().equals(firstEgg.getFirst()),"second victory appends an egg without replacing the first");
        check(selected.size()==2&&selected.getFirst().equals(first.getFirst()),"second victory appends scroll without replacing first selection");
        check(selected.stream().allMatch(id->ElementalSpell.parse(id)!=null&&ElementalSpell.parse(id).rarity()==ElementalSpell.Rarity.LEGENDARY),"both queued victories select real Legendary IDs");
        check(stored.getInt(path+".xp")==xp*2&&stored.getInt(path+".skill-points")==2&&stored.getInt(path+".feathers")==feathers*2,"two victories retain accumulated auxiliary rewards");
        reload();connect(actor);manager.claimReward(player);physicalReward(player,2);claimed(player);
        var actual=new ArrayList<String>();for(var scroll:scrolls(player))for(int n=0;n<scroll.getAmount();n++)actual.add(magic.staffSpells().scroll(scroll).id());
        Collections.sort(actual);var expected=new ArrayList<>(selected);Collections.sort(expected);check(actual.equals(expected),"accumulated claim preserves both selected IDs including a possible duplicate");
        var hatched=new ArrayList<>(eggSpecies(player));Collections.sort(hatched);var stack=new ArrayList<>(queuedEggs);Collections.sort(stack);check(hatched.equals(stack),"accumulated claim delivers both stored egg species");
        check(player.getTotalExperience()==xp*2&&magic.skillTree().earnedPoints(player)==2,"accumulated claim grants exact XP and skill points");noReplay(player);
    }
    private void inventoryAtomicity()throws Exception{
        var actor=actor(true);Player player=actor.getBukkitEntity();for(int slot=0;slot<36;slot++)player.getInventory().setItem(slot,new ItemStack(Material.STONE,64));
        int drops=garden.world().getEntitiesByClass(org.bukkit.entity.Item.class).size();defeat(won(actor.getUUID()));var selected=queued(actor.getUUID());
        // Bundle slots: Tier VII key, Tier VI key, scroll, feathers and the egg.
        for(int free=1;free<5;free++){
            player.getInventory().setItem(free-1,null);manager.claimReward(player);
            check(keyCount(player,7)==0&&keyCount(player,6)==0&&scrolls(player).isEmpty()&&eggIds(player).isEmpty()&&player.getTotalExperience()==0,
                free+" free slot(s) cannot partially claim the five-slot bundle");
            check(disk().getStringList(reward(actor.getUUID())+".legendary-spells").equals(selected),"full inventory retry does not reroll Legendary");
        }
        check(magic.skillTree().earnedPoints(player)==0,"inventory failure retains skill point in ledger");
        player.getInventory().setItem(4,null);manager.claimReward(player);
        check(keyCount(player,7)==tier7&&keyCount(player,6)==tier6&&scrolls(player).size()==1&&eggIds(player).size()==eggs&&garden.seraphTrader().feathers().carried(player)==feathers,
            "exactly five free storage slots suffice for the full bundle");
        for(int slot=5;slot<36;slot++)player.getInventory().setItem(slot,null);physicalReward(player,1);claimed(player);
        check(garden.world().getEntitiesByClass(org.bukkit.entity.Item.class).size()==drops,"deferred bundle never spills items onto ground");noReplay(player);
    }
    private void legacyClaim()throws Exception{
        var actor=actor(false);String path=reward(actor.getUUID());ledger.set(path+".keys",3);ledger.set(path+".tier5-keys",2);persist();
        defeat(won(actor.getUUID()));check(disk().getInt(path+".keys")==3&&disk().getInt(path+".tier5-keys")==2,"new victory preserves pre-upgrade queued keys");
        connect(actor);Player player=actor.getBukkitEntity();manager.claimReward(player);claimed(player);
        check(keyCount(player,7)==tier7&&keyCount(player,6)==tier6,"new keys remain explicitly Tier VII and VI alongside the legacy queue");
        check(taggedKeyCount(player,5)==2,"Tier V keys queued before the reward change are still delivered");
        ItemStack legacy=garden.relics().createVoidKey();int old=0;for(var item:player.getInventory().getStorageContents())if(item!=null&&item.isSimilar(legacy))old+=item.getAmount();
        check(old==3,"legacy queued keys retain old Master Key factory and metadata");
        check(scrolls(player).stream().mapToInt(ItemStack::getAmount).sum()==1&&eggIds(player).size()==eggs,"legacy queue does not invent or duplicate extra scrolls or eggs");noReplay(player);
    }
    private void testEncounter()throws Exception{
        var actor=actor(true);Object run=won(actor.getUUID());field(runType,"test").setBoolean(run,true);String before=ledger.saveToString();defeat(run);
        check(before.equals(ledger.saveToString()),"admin test encounter never changes reward or cooldown ledger");check(itemCount(actor.getBukkitEntity())==0,"admin test encounter gives no keys, eggs, scrolls or feathers");
    }
    private void missingScrollProvider()throws Exception{
        var actor=actor(false);defeat(won(actor.getUUID()));var selected=queued(actor.getUUID());connect(actor);Player player=actor.getBukkitEntity();
        Field provider=field(AdvanceMagicPlugin.class,"staffSpells");Object service=provider.get(magic);provider.set(magic,null);
        try{manager.claimReward(player);check(itemCount(player)==0&&player.getTotalExperience()==0,"unavailable scroll service defers complete bundle");check(disk().getStringList(reward(actor.getUUID())+".legendary-spells").equals(selected),"provider outage preserves selected spell");}
        finally{provider.set(magic,service);}
        manager.claimReward(player);physicalReward(player,1);claimed(player);noReplay(player);
    }
    private void deferredSelection()throws Exception{
        var actor=actor(false);Player player=actor.getBukkitEntity();Field provider=field(AdvanceMagicPlugin.class,"staffSpells");Object service=provider.get(magic);provider.set(magic,null);
        try{defeat(won(actor.getUUID()));var stored=disk();String path=reward(actor.getUUID());
            check(stored.getInt(path+".tier7-keys")==tier7&&stored.getInt(path+".tier6-keys")==tier6&&stored.getStringList(path+".eggs").size()==eggs
                &&stored.getInt(path+".legendary-spell-rolls")==1,"unavailable service at victory persists keys, egg and deferred scroll entitlement");
            check(stored.getStringList(path+".legendary-spells").isEmpty(),"deferred victory stores no fabricated ID");}
        finally{provider.set(magic,service);}
        connect(actor);for(int slot=0;slot<36;slot++)player.getInventory().setItem(slot,new ItemStack(Material.STONE,64));manager.claimReward(player);
        var selected=queued(actor.getUUID());String path=reward(actor.getUUID());
        check(disk().getInt(path+".legendary-spell-rolls")==0,"recovered service durably consumes deferred roll before capacity check");
        check(keyCount(player,7)==0&&scrolls(player).isEmpty()&&eggIds(player).isEmpty()&&player.getTotalExperience()==0,"deferred selection with full inventory never partially delivers");
        for(int retry=0;retry<3;retry++){manager.claimReward(player);check(disk().getStringList(path+".legendary-spells").equals(selected),"full inventory retry retains already-persisted deferred selection");}
        reload();player.getInventory().clear();manager.claimReward(player);physicalReward(player,1);claimed(player);
        check(magic.staffSpells().scroll(scrolls(player).getFirst()).id().equals(selected.getFirst()),"recovered deferred claim delivers selected Legendary without reroll");noReplay(player);
    }
    private Path blockSave()throws Exception{Path tmp=ledgerFile.resolveSibling("world-bosses.yml.tmp");Files.deleteIfExists(tmp);Files.createDirectory(tmp);return tmp;}
    private void recover(Path obstruction)throws Exception{Files.delete(obstruction);reload();field(WorldBossManager.class,"healthy").setBoolean(manager,true);}
    private void failedClaimSave()throws Exception{
        var actor=actor(false);defeat(won(actor.getUUID()));var selected=queued(actor.getUUID());connect(actor);Player player=actor.getBukkitEntity();Path obstruction=blockSave();
        try{manager.claimReward(player);check(itemCount(player)==0&&player.getTotalExperience()==0&&magic.skillTree().earnedPoints(player)==0,"failed claim persistence delivers absolutely nothing");check(disk().getStringList(reward(actor.getUUID())+".legendary-spells").equals(selected),"failed claim save retains durable selected scroll");}
        finally{recover(obstruction);}
        manager.claimReward(player);physicalReward(player,1);claimed(player);noReplay(player);
    }
    private void failedVictorySave()throws Exception{
        var actor=actor(true);Player player=actor.getBukkitEntity();Path obstruction=blockSave();
        try{defeat(won(actor.getUUID()));check(itemCount(player)==0&&player.getTotalExperience()==0&&magic.skillTree().earnedPoints(player)==0,"failed victory persistence cannot deliver any part of bundle");check(!disk().contains(reward(actor.getUUID())),"failed victory leaves no pretend durable reward");manager.claimReward(player);check(itemCount(player)==0,"unhealthy manager blocks claim after save failure");}
        finally{recover(obstruction);}
    }
    private void disabledMagicVictory()throws Exception{
        var actor=actor(true);Player player=actor.getBukkitEntity();Bukkit.getPluginManager().disablePlugin(magic);check(!magic.isEnabled(),"Advance Magic actually disabled");
        defeat(won(actor.getUUID()));String path=reward(actor.getUUID());var stored=disk();
        check(stored.getInt(path+".tier7-keys")==tier7&&stored.getInt(path+".tier6-keys")==tier6&&stored.getStringList(path+".eggs").size()==eggs,"magic outage still durably queues keys and egg");
        check(stored.getInt(path+".legendary-spell-rolls")==1,"magic outage queues one deferred Legendary roll");
        check(stored.getStringList(path+".legendary-spells").isEmpty(),"magic outage cannot invent unknown scroll ID");
        manager.claimReward(player);check(itemCount(player)==0&&player.getTotalExperience()==0,"disabled magic defers entire guaranteed bundle for later");
    }
}
