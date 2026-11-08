import com.example.voidscape.pet.*;
import com.example.voidscape.world.BiomeTerrain;
import com.example.voidscape.boss.JudgePackService;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;

/** Runs the entire existing pet/egg/menu suite, then the mythical and earned-capacity regressions. */
public class MythicalPetProbe extends PetSystemNativeProbe {
    @Override String suiteName(){return "pet system, eggs, mythicals and permanent stable expansion";}
    static Object stat(Class<?> type,String name,Class<?>[] types,Object... values)throws Exception{
        Method m=type.getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(null,values);
    }
    int omen(PetCatalog.Species species,BiomeTerrain.Kind region)throws Exception{
        return (int)stat(WildPetSpawns.class,"omen",new Class<?>[]{PetCatalog.Species.class,BiomeTerrain.Kind.class},species,region);
    }
    PetCatalog.Species roll(double roll,double chance,Map<PetCatalog.Species,Integer> weights)throws Exception{
        return (PetCatalog.Species)stat(WildPetSpawns.class,"legendForRoll",new Class<?>[]{double.class,double.class,Map.class},roll,chance,weights);
    }
    PetRecord make(PetCatalog.Species species,PetCatalog.Stage age)throws Exception{
        Constructor<PetRecord> ctor=PetRecord.class.getDeclaredConstructor(UUID.class,PetCatalog.Species.class,PetCatalog.Stage.class);
        ctor.setAccessible(true);return ctor.newInstance(UUID.randomUUID(),species,age);
    }
    Object reloadStable(Object stable,UUID owner)throws Exception{
        YamlConfiguration yaml=new YamlConfiguration();yaml.loadFromString((String)call(stable,"serialize"));
        return stat(stable.getClass(),"load",new Class<?>[]{UUID.class,YamlConfiguration.class,int.class},owner,yaml,6);
    }
    @Override void holdPose(){try{
        mythicalChecks();capacityChecks();paginationChecks();
        super.holdPose();
    }catch(Throwable error){finish(error);}}

    void mythicalChecks()throws Exception{
        check(PetCatalog.available().size()==37&&PetCatalog.modelIds().size()==111,"all 37 species and 111 growth models are available at runtime");
        check(WildPetSpawns.eligibleSpecies().size()==32&&WildPetSpawns.eligibleSpecies().stream().noneMatch(PetCatalog.Species::mythical),
            "ordinary encounter pool never rolls a mythical species");
        check(Math.abs((double)call(garden.wildPets(),"legendChance")-.02)<1e-12,"new and upgraded servers use 2 percent mythical odds");
        garden.getConfig().set("pets.mythical.chance",Double.NaN);
        check((double)call(garden.wildPets(),"legendChance")==.02,"non-finite mythical chance uses the safe default");
        garden.getConfig().set("pets.mythical.chance",.01);
        var old=new YamlConfiguration();old.set("pets.mythical.chance",.001);old.set("pets.companions.faint-ticks",24000);
        Class<?>[] section={org.bukkit.configuration.ConfigurationSection.class};
        check((boolean)stat(WildPetSpawns.class,"upgradeMythicalConfig",section,old)&&old.getDouble("pets.mythical.chance")==.02,"legacy default spawn rate upgraded");
        check((boolean)stat(WildPetSpawns.class,"upgradeRecoveryConfig",section,old)&&old.getInt("pets.companions.faint-ticks")==6000,"legacy default recovery upgraded");
        check(!(boolean)stat(WildPetSpawns.class,"upgradeMythicalConfig",section,old)&&!(boolean)stat(WildPetSpawns.class,"upgradeRecoveryConfig",section,old),"config upgrades run only once");
        for(double custom:new double[]{0,.005,.03}){
            var configured=new YamlConfiguration();configured.set("pets.mythical.chance",custom);configured.set("pets.companions.faint-ticks",12000);
            stat(WildPetSpawns.class,"upgradeMythicalConfig",section,configured);stat(WildPetSpawns.class,"upgradeRecoveryConfig",section,configured);
            check(configured.getDouble("pets.mythical.chance")==custom&&configured.getInt("pets.companions.faint-ticks")==12000,"custom rates and recovery preserved");
        }
        var habitats=Map.of(PetCatalog.Species.AETHERIUS,BiomeTerrain.Kind.GARDENS,
            PetCatalog.Species.SOLARIS,BiomeTerrain.Kind.GARDENS,PetCatalog.Species.FENRIR,BiomeTerrain.Kind.ANCIENT_FOREST,
            PetCatalog.Species.ZEPHYROS,BiomeTerrain.Kind.GLACIER,PetCatalog.Species.IGNIS_DRACONIS,BiomeTerrain.Kind.LUNAR_CITY);
        for(var entry:habitats.entrySet()){
            var s=entry.getKey();var region=entry.getValue();
            check(omen(s,region)==1,s.id+" is obtainable solely by its habitat, without time or weather inputs");
            check(omen(s,null)==0,s.id+" never appears outside its biome");
            var wrong=region==BiomeTerrain.Kind.GARDENS?BiomeTerrain.Kind.GLACIER:BiomeTerrain.Kind.GARDENS;
            check(omen(s,wrong)==0,s.id+" rejects another species' habitat");
            for(var age:PetCatalog.Stage.values())check(((NativePetRenderer)renderer).hasModel(s,age),s.id+" has a native "+age.id+" rig");
        }
        check(omen(PetCatalog.Species.SOLARIS,BiomeTerrain.Kind.GARDENS)==1,"Solaris requires only its habitat");
        check(omen(PetCatalog.Species.ZEPHYROS,BiomeTerrain.Kind.GLACIER)==1,"Zephyros requires only its habitat, never a storm");
        var weights=new EnumMap<PetCatalog.Species,Integer>(PetCatalog.Species.class);
        weights.put(PetCatalog.Species.AETHERIUS,2);weights.put(PetCatalog.Species.SOLARIS,1);
        check(roll(0,.001,weights)==PetCatalog.Species.AETHERIUS&&roll(.001999,.001,weights)==PetCatalog.Species.AETHERIUS,
            "first species occupies exactly its 0.2 percent interval");
        check(roll(.002,.001,weights)==PetCatalog.Species.SOLARIS&&roll(.002999,.001,weights)==PetCatalog.Species.SOLARIS,
            "second species keeps its full 0.1 percent interval without enum-order bias");
        check(roll(.003,.001,weights)==null&&roll(.99,.001,weights)==null&&roll(0,0,weights)==null,
            "ordinary encounters stay ordinary and zero disables mythical rolls");
        check(roll(.999,1,weights)==PetCatalog.Species.SOLARIS,"high configured odds normalize without starving later species");
        Class<?>[] encounterTypes={double.class,double.class,Map.class,int.class,int.class};
        weights.put(PetCatalog.Species.AETHERIUS,1);
        check(stat(WildPetSpawns.class,"legendForEncounter",encounterTypes,.9,.01,weights,18,20)==null,"19th encounter still uses ordinary odds");
        check(stat(WildPetSpawns.class,"legendForEncounter",encounterTypes,.2,.01,weights,19,20)==PetCatalog.Species.AETHERIUS
            &&stat(WildPetSpawns.class,"legendForEncounter",encounterTypes,.8,.01,weights,19,20)==PetCatalog.Species.SOLARIS,"20th eligible encounter guarantees either equally eligible garden species");
        check(stat(WildPetSpawns.class,"legendForEncounter",encounterTypes,.9,0.,weights,100,20)==null
            &&stat(WildPetSpawns.class,"legendForEncounter",encounterTypes,.9,.01,weights,100,0)==null,"zero odds or zero pity disables the guarantee");
        UUID progressId=UUID.randomUUID();
        call(garden.wildPets(),"recordLegendEncounter",progressId,false);
        var progressFile=(java.io.File)get(garden.wildPets(),"pityFile");
        check(YamlConfiguration.loadConfiguration(progressFile).getInt("players."+progressId)==1,"eligible misses persist across a reload");
        call(garden.wildPets(),"recordLegendEncounter",progressId,true);
        check(!YamlConfiguration.loadConfiguration(progressFile).contains("players."+progressId),"successful legendary encounter clears progress durably");
        naturalFixedWorldChecks();
        PetRecord mythical=make(PetCatalog.Species.FENRIR,PetCatalog.Stage.ADULT);
        check((long)call(breeding,"cooldownMillis",mythical)==72*3_600_000L,"mythical parents rest 72 real hours");
        check((long)call(breeding,"cooldownMillis",make(PetCatalog.Species.FROST_WYVERN,PetCatalog.Stage.ADULT))==24*3_600_000L,
            "ordinary breeding cooldown remains 24 hours");
        garden.wildPets().clear();
        for(var s:WildPetSpawns.mythicalSpecies())for(var age:PetCatalog.Stage.values()){
            check((boolean)call(garden.wildPets(),"spawn",s,age,home.clone().add(7,0,0),true),s.id+"/"+age.id+" actually spawns on Paper: "+garden.wildPets().status());
            check(!(boolean)call(garden.wildPets(),"spawn",PetCatalog.Species.SOLARIS,PetCatalog.Stage.BABY,home.clone().add(14,0,0),true),
                "a second wild mythical is refused while "+s.id+" is roaming");
            garden.wildPets().clear();
        }
        check(garden.wildPets().spawn(PetCatalog.Species.AETHERIUS,PetCatalog.Stage.BABY,home.clone().add(7,0,0)),"a mythical baby is available for the real taming flow");
        Object body=wildActive().values().iterator().next();
        tamer.getInventory().setItemInMainHand(new ItemStack(food(PetCatalog.Species.AETHERIUS),64));
        call(taming,"feed",body,tamer);
        int gain=(int)get(body,"trust");check(gain>=9&&gain<=15,"mythical baby gains half the ordinary trust per meal");
        int before=records(tamer).size();
        for(int i=0;i<16&&!wildActive().isEmpty();i++){put(body,"lastFeed",0L);call(taming,"feed",body,tamer);}
        check(wildActive().isEmpty()&&records(tamer).size()==before+1,"feeding befriends the mythical through the normal taming flow");
        PetRecord captured=records(tamer).values().stream().filter(r->r.species()==PetCatalog.Species.AETHERIUS).findFirst().orElseThrow();
        call(taming,"feed",body,tamer);
        check(records(tamer).size()==before+1,"a stale click on a mythical already sent to the stable cannot duplicate it");
        check(tamer.getUniqueId().equals(get(captured,"raisedBy")),"a wild mythical caught as a baby records its actual raiser");
        check(garden.arkhaiaModels().ready(),"existing boss ModelEngine rigs remain ready with native mythical pets");
        try(var paths=Files.list(Path.of("plugins/ModelEngine/blueprints"))){
            check(paths.filter(p->p.toString().endsWith(".bbmodel")).count()==11,"default install still imports only the existing 11 Evergarden boss blueprints");
        }
    }

    /** Exercise the actual natural-encounter loop, not just the habitat selection helper. */
    void naturalFixedWorldChecks()throws Exception{
        var wild=garden.wildPets();var config=garden.getConfig();
        var saved=new LinkedHashMap<String,Object>();
        for(String key:List.of("pets.wild-spawns.chance-per-player","pets.wild-spawns.max-nearby-player",
            "pets.mythical.chance","pets.mythical.pity-encounters","pets.mythical.stage-weights.baby",
            "pets.mythical.stage-weights.medium","pets.mythical.stage-weights.adult"))saved.put(key,config.get(key));
        Boolean dayCycle=world.getGameRuleValue(GameRule.DO_DAYLIGHT_CYCLE),weatherCycle=world.getGameRuleValue(GameRule.DO_WEATHER_CYCLE);
        long previousTime=world.getTime();boolean previousStorm=world.hasStorm(),previousThunder=world.isThundering();
        var previousMode=rival.getGameMode();Location previousRival=rival.getLocation();
        var oldBiomes=new LinkedHashMap<Location,org.bukkit.block.Biome>();
        int min=home.getBlockX()-56,max=home.getBlockX()+56,minZ=home.getBlockZ()-56,maxZ=home.getBlockZ()+56;
        for(int x=min;x<=max;x+=4)for(int z=minZ;z<=maxZ;z+=4){
            var at=new Location(world,x,120,z);oldBiomes.put(at,world.getBiome(x,120,z));
        }
        try{
            world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE,false);world.setGameRule(GameRule.DO_WEATHER_CYCLE,false);
            world.setTime(13000);world.setStorm(false);world.setThundering(false);
            rival.setGameMode(GameMode.SPECTATOR);rival.teleport(home.clone().add(200,0,0));tamer.teleport(home);
            config.set("pets.wild-spawns.chance-per-player",1.0);config.set("pets.wild-spawns.max-nearby-player",2);
            config.set("pets.mythical.chance",.01);config.set("pets.mythical.pity-encounters",1);
            config.set("pets.mythical.stage-weights.baby",1);config.set("pets.mythical.stage-weights.medium",0);config.set("pets.mythical.stage-weights.adult",0);
            ((Random)get(wild,"random")).setSeed(20261105L);
            var observed=EnumSet.noneOf(PetCatalog.Species.class);
            var biomes=List.of(org.bukkit.block.Biome.CHERRY_GROVE,org.bukkit.block.Biome.FROZEN_PEAKS,
                org.bukkit.block.Biome.OLD_GROWTH_SPRUCE_TAIGA,org.bukkit.block.Biome.PALE_GARDEN);
            for(var biome:biomes){
                for(var at:oldBiomes.keySet())world.setBiome(at.getBlockX(),at.getBlockY(),at.getBlockZ(),biome);
                int repetitions=biome==org.bukkit.block.Biome.CHERRY_GROVE?16:1;
                for(int i=0;i<repetitions;i++){
                    wild.clear();call(wild,"attemptSpawns");
                    check(wildActive().size()==1,"real natural spawn succeeds in "+biome+" at locked time and clear weather; "+wild.status(tamer));
                    Object body=wildActive().values().iterator().next();
                    PetCatalog.Species species=(PetCatalog.Species)get(body,"species");
                    check(species.mythical(),"pity produces a real mythical in "+biome+" with time and weather fixed");observed.add(species);
                }
            }
            check(observed.containsAll(WildPetSpawns.mythicalSpecies()),"all five mythicals naturally spawn in their habitats without day, night or rain cycles: "+observed);
            check(call(wild,"legend",BiomeTerrain.Kind.LUNAR_CITY,tamer.getUniqueId())==null,"one active wild mythical blocks another worldwide");
            config.set("pets.wild-spawns.max-nearby-player",1);call(wild,"attemptSpawns");
            check(wildActive().size()==1&&wild.status(tamer).contains("ครบ"),"the nearby wild-pet cap prevents another natural encounter");
            wild.clear();config.set("pets.mythical.chance",0.0);call(wild,"attemptSpawns");
            check(wildActive().values().stream().noneMatch(body->{try{return ((PetCatalog.Species)get(body,"species")).mythical();}catch(Exception e){throw new RuntimeException(e);}}),
                "zero configured mythical chance disables mythicals even when pity is set");
        }finally{
            wild.clear();for(var entry:saved.entrySet())config.set(entry.getKey(),entry.getValue());
            for(var entry:oldBiomes.entrySet()){var at=entry.getKey();world.setBiome(at.getBlockX(),at.getBlockY(),at.getBlockZ(),entry.getValue());}
            world.setTime(previousTime);world.setStorm(previousStorm);world.setThundering(previousThunder);
            if(dayCycle!=null)world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE,dayCycle);
            if(weatherCycle!=null)world.setGameRule(GameRule.DO_WEATHER_CYCLE,weatherCycle);
            rival.setGameMode(previousMode);rival.teleport(previousRival);
        }
    }

    @SuppressWarnings("unchecked") void capacityChecks()throws Exception{
        Object stable=stable(tamer);int before=(int)call(stable,"capacity");
        PetRecord grown=null;
        for(int i=0;i<32;i++){
            PetRecord baby=make(i==0?PetCatalog.Species.SOLARIS:PetCatalog.Species.MOCHI_SLIME,PetCatalog.Stage.BABY);
            put(baby,"bond",60);
            put(baby,"raisedBy",tamer.getUniqueId());call(stable,"add",baby);
            put(baby,"growth",(int)call(companions,"threshold",PetCatalog.Stage.ADULT));
            call(companions,"checkGrowth",tamer,baby);
            check(baby.stage()==PetCatalog.Stage.MEDIUM&&(int)call(stable,"capacity")==before+2*i,"baby to medium gives no early slot reward "+i);
            call(companions,"checkGrowth",tamer,baby);
            check(baby.stage()==PetCatalog.Stage.ADULT&&(int)call(stable,"capacity")==before+2*(i+1),"raising to adulthood permanently gives exactly two slots "+i);
            call(companions,"checkGrowth",tamer,baby);
            check((int)call(stable,"capacity")==before+2*(i+1),"repeat growth check cannot duplicate the reward "+i);
            grown=baby;
        }
        int earned=(int)call(stable,"capacity");
        check(earned>28&&!(boolean)call(stable,"full"),"earned capacity continues beyond the old 28-slot menu limit");
        PetRecord adult=make(PetCatalog.Species.FENRIR,PetCatalog.Stage.ADULT);call(stable,"add",adult);
        PetRecord medium=make(PetCatalog.Species.FENRIR,PetCatalog.Stage.MEDIUM);call(stable,"add",medium);
        put(medium,"growth",100000);call(companions,"checkGrowth",tamer,medium);
        check(!(boolean)call(stable,"raise",adult)&&(int)call(stable,"capacity")==earned,"pets obtained at medium or adult age earn no baby-raising reward");
        PetRecord foreign=make(PetCatalog.Species.FENRIR,PetCatalog.Stage.ADULT);put(foreign,"raisedBy",rival.getUniqueId());
        call(stable,"add",foreign);check(!(boolean)call(stable,"raise",foreign),"another owner's raised pet cannot unlock slots here");
        check((boolean)call(store,"saveStableNow",stable),"grown forms and award receipts reach disk together");
        Object copy=reloadStable(stable,tamer.getUniqueId());
        check((int)call(copy,"capacity")==earned&&!(boolean)call(copy,"raise",grown),"restart preserves capacity and prevents paying the same adult twice");
        call(copy,"remove",grown.id());copy=reloadStable(copy,tamer.getUniqueId());
        check((int)call(copy,"capacity")==earned,"releasing a raised adult never removes earned slots");
        var yaml=new YamlConfiguration();PetRecord legacyBaby=make(PetCatalog.Species.MOCHI_SLIME,PetCatalog.Stage.BABY);
        PetRecord legacyHatched=make(PetCatalog.Species.FROST_WYVERN,PetCatalog.Stage.ADULT);put(legacyHatched,"generation",1);
        PetRecord legacyAdult=make(PetCatalog.Species.FENRIR,PetCatalog.Stage.ADULT);
        for(PetRecord r:List.of(legacyBaby,legacyHatched,legacyAdult)){
            var section=yaml.createSection("pets."+r.id());call(r,"save",section);section.set("raised-by",null);
        }
        Object migrated=stat(stable.getClass(),"load",new Class<?>[]{UUID.class,YamlConfiguration.class,int.class},tamer.getUniqueId(),yaml,6);
        Map<UUID,PetRecord> oldPets=(Map<UUID,PetRecord>)get(migrated,"pets");
        check(tamer.getUniqueId().equals(get(oldPets.get(legacyBaby.id()),"raisedBy"))&&(int)call(migrated,"capacity")==8,
            "legacy babies retain eligibility and known hatched adults receive their one-time reward");
        check(get(oldPets.get(legacyAdult.id()),"raisedBy")==null,"unknown legacy wild adults are not falsely treated as raised babies");
        check((int)call(reloadStable(migrated,tamer.getUniqueId()),"capacity")==8,"legacy migration is idempotent on a second reload");
    }

    @SuppressWarnings("unchecked") void paginationChecks()throws Exception{
        Object menu=get(pets,"menu"),stable=stable(tamer);
        Field platforms=JudgePackService.class.getDeclaredField("PLATFORMS");platforms.setAccessible(true);
        Map<UUID,Object> cache=(Map<UUID,Object>)platforms.get(null);
        var platform=Class.forName("com.example.voidscape.boss.JudgePackService$Platform").getDeclaredConstructor(boolean.class,long.class);
        platform.setAccessible(true);
        try{
            for(boolean bedrock:new boolean[]{false,true}){
                cache.put(tamer.getUniqueId(),platform.newInstance(bedrock,System.currentTimeMillis()));
                ((Map<UUID,Integer>)get(menu,"stablePages")).clear();((PetMenu)menu).open(tamer);
                check(actions().containsKey(52),"stable has a next page for "+(bedrock?"Bedrock":"Java"));
                check(title(49).contains("/"+call(stable,"capacity")),"stable footer displays earned capacity");
                actions().get(52).run();
                PetRecord expected=new ArrayList<>(records(tamer).values()).get(28);
                check(title(10).equals(expected.name())&&actions().containsKey(46),"second stable page exposes pet 29 and the previous button");
                actions().get(10).run();
                check(expected.id().equals(get(tamer.getOpenInventory().getTopInventory().getHolder(),"pet")),"second-page selection opens the correct pet card");
                actions().get(0).run();check(title(10).equals(expected.name()),"back from a pet card preserves its stable page");
                actions().get(46).run();check(!actions().containsKey(46),"previous button returns to the first stable page");
                call(stable,"clearNest");((Map<?,?>)get(menu,"drafts")).clear();((Map<?,?>)get(menu,"nestPages")).clear();
                call(menu,"chestNest",tamer);check(actions().containsKey(53),"breeding candidates have a next page on "+(bedrock?"Bedrock":"Java"));
                actions().get(53).run();check(actions().containsKey(46)&&actions().containsKey(28),"later breeding page contains selectable pets and previous navigation");
                actions().get(28).run();
                UUID[] pair=((Map<UUID,UUID[]>)get(menu,"drafts")).get(tamer.getUniqueId());
                check(pair[0]!=null||pair[1]!=null,"a partner can be chosen beyond the first breeding page");
            }
        }finally{cache.remove(tamer.getUniqueId());}
    }
}
