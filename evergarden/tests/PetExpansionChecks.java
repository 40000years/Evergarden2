import com.example.voidscape.VoidscapePlugin;
import com.example.voidscape.boss.JudgePackService;
import com.example.voidscape.pet.*;
import com.example.voidscape.world.BiomeTerrain;
import com.google.gson.*;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.*;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.Predicate;

/** Real-Paper integration probe. Ship this as a separate plugin depending on Evergarden.
 * Never starts its own server; the harness reads pet-grounding-result.txt and shuts down.
 */
public final class PetExpansionChecks extends JavaPlugin {
    VoidscapePlugin garden; World world; Player owner;
    net.minecraft.server.level.ServerPlayer actor;
    Object companions,bodies,stable,menu,taming;
    Class<?> expansion,skills,bodyType;
    NativePetRenderer renderer;
    final List<String> lines=new ArrayList<>();
    final List<Entity> fixtures=new ArrayList<>();
    final Set<UUID> created=new HashSet<>();
    final List<PetCatalog.Species> additions=new ArrayList<>();
    final Map<PetCatalog.Species,JsonObject> source=new EnumMap<>(PetCatalog.Species.class);
    int checks,waited,variant,bodiesBefore; long serial=10000;
    boolean finished;
    Object openBody;
    static final String[] LEGACY={"AETHERIUS","IGNIS_DRACONIS","ZEPHYROS","SOLARIS","FENRIR",
        "ARCANE_NINE_TAILS","FROST_WYVERN","ASTRAL_OWL","CRIMSON_CERBERUS","SHADOW_PANTHER",
        "BLOOD_GARGOYLE","RUNIC_GOLEM","WORLD_TREE_ENTLING","CRYSTAL_TORTOISE","ABYSSAL_MANTA",
        "ALCHEMIST_MIMIC","CHRONO_FAIRY","MOCHI_SLIME","CLOUD_LAMB","MOON_RABBIT","BLOOM_HEDGEHOG",
        "CORAL_AXOLOTL","CLOVER_BUMBLE","AURORA_PENGUIN","STORM_RAPTOR","MAGMA_RHINO","ECLIPSE_SCORPION",
        "RAIJIN_TIGER","TITAN_BEETLE","ANUBIS_JACKAL","BONE_DRAKE","JADE_LUNG","CRYSTAL_STAG","LUNA_MOTH",
        "CELESTIAL_KOI","STARLIGHT_PEACOCK","AURORA_JELLY"};
    void check(boolean value,String label){if(!value)throw new AssertionError(label);checks++;lines.add("PASS: "+label);}
    void near(double actual,double expected,String label){check(Math.abs(actual-expected)<1e-5,label+" actual="+actual+" expected="+expected);}
    static Field field(Object target,String name)throws Exception{
        Class<?> type=target instanceof Class<?> c?c:target.getClass();
        for(Class<?> c=type;c!=null;c=c.getSuperclass())try{Field f=c.getDeclaredField(name);f.setAccessible(true);return f;}catch(NoSuchFieldException ignored){}
        throw new NoSuchFieldException(type.getName()+"."+name);
    }
    static Object get(Object target,String name)throws Exception{return field(target,name).get(target instanceof Class<?>?null:target);}
    static void put(Object target,String name,Object value)throws Exception{field(target,name).set(target instanceof Class<?>?null:target,value);}
    static Class<?> boxed(Class<?> type){
        if(type==int.class)return Integer.class;if(type==long.class)return Long.class;if(type==float.class)return Float.class;
        if(type==double.class)return Double.class;if(type==boolean.class)return Boolean.class;return type;
    }
    static Object call(Object target,String name,Object...args)throws Exception{
        Class<?> type=target instanceof Class<?> c?c:target.getClass();
        for(Class<?> c=type;c!=null;c=c.getSuperclass())for(Method m:c.getDeclaredMethods()){
            if(!m.getName().equals(name)||m.getParameterCount()!=args.length)continue;
            Class<?>[] parameters=m.getParameterTypes();boolean matches=true;
            for(int i=0;i<args.length;i++)if(args[i]!=null&&!boxed(parameters[i]).isInstance(args[i])){matches=false;break;}
            if(!matches)continue;m.setAccessible(true);
            try{return m.invoke(target instanceof Class<?>?null:target,args);}catch(InvocationTargetException e){
                Throwable cause=e.getCause();if(cause instanceof Exception x)throw x;if(cause instanceof Error x)throw x;throw e;
            }
        }
        throw new NoSuchMethodException(type.getName()+"."+name+"/"+args.length);
    }
    void later(int ticks,Checked work){Bukkit.getScheduler().runTaskLater(this,()->{try{work.run();}catch(Throwable e){finish(e);}},ticks);}
    interface Checked{void run()throws Exception;}
    @Override public void onEnable(){later(20,()->{
        garden=(VoidscapePlugin)Bukkit.getPluginManager().getPlugin("Evergarden");
        check(garden!=null&&garden.isEnabled()&&garden.pets()!=null,"production pet system boots");world=garden.world();awaitReady();
    });}
    void awaitReady()throws Exception{
        renderer=(NativePetRenderer)get(garden.pets(),"renderer");
        if(!garden.judgePack().nativePetsIncluded()||!renderer.ready()){
            if((waited+=10)>2400)throw new AssertionError("native pet pack never became ready: "+renderer.status());
            later(10,this::awaitReady);return;
        }
        setup();later(40,()->{catalog();spawnRolls();passives();combat();aura();owner.teleport(home());renderBatch();});
    }
    Location home(){return new Location(world,1536.5,180,1536.5);}
    Location darkRoom(){return new Location(world,1553.5,175,1553.5);}
    void setup()throws Exception{
        companions=get(garden.pets(),"companions");bodies=get(garden.pets(),"bodies");menu=garden.pets().menu();taming=get(garden.pets(),"taming");
        expansion=Class.forName("com.example.voidscape.pet.PetExpansion");skills=Class.forName("com.example.voidscape.pet.PetSkills");bodyType=Class.forName("com.example.voidscape.pet.PetBody");
        garden.getConfig().set("pets.wild-spawns.chance-per-player",0d);garden.getConfig().set("pets.companions.counter-chance",0);garden.wildPets().reload();garden.wildPets().clear();
        garden.getConfig().set("dimensional-sanctuaries.habitat.suppress-natural-hostiles",false);
        garden.getConfig().set("overworld-sanctuaries.habitat.suppress-natural-hostiles",false);
        for(int x=1510;x<=1570;x++)for(int z=1510;z<=1570;z++){
            world.getChunkAt(x>>4,z>>4).addPluginChunkTicket(this);world.getBlockAt(x,179,z).setType(Material.STONE,false);
            for(int y=180;y<=190;y++)world.getBlockAt(x,y,z).setType(Material.AIR,false);
        }
        // Fully enclosed room: no time/weather assumptions and no visible light sources.
        for(int x=1548;x<=1558;x++)for(int z=1548;z<=1558;z++)for(int y=173;y<=179;y++){
            boolean boundary=x==1548||x==1558||z==1548||z==1558||y<=174||y>=178;
            world.getBlockAt(x,y,z).setType(boundary?Material.STONE:Material.AIR,false);
        }
        var server=net.minecraft.server.MinecraftServer.getServer();var level=((org.bukkit.craftbukkit.CraftWorld)world).getHandle();
        var profile=new com.mojang.authlib.GameProfile(UUID.randomUUID(),"PetExpansionTest");
        actor=new net.minecraft.server.level.ServerPlayer(server,level,profile,net.minecraft.server.level.ClientInformation.createDefault());
        var connection=new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        connection.channel=new io.netty.channel.embedded.EmbeddedChannel();connection.address=new java.net.InetSocketAddress("127.0.0.1",1);
        actor.connection=new net.minecraft.server.network.ServerGamePacketListenerImpl(server,connection,actor,net.minecraft.server.network.CommonListenerCookie.createInitial(profile,false)){
            @Override public boolean hasClientLoaded(){return true;}
        };
        actor.setPos(home().getX(),180,home().getZ());server.getPlayerList().getPlayers().add(actor);server.getPlayerList().getPlayersByUUID().put(profile.id(),actor);level.addNewPlayer(actor);
        owner=actor.getBukkitEntity();owner.setGravity(false);owner.setInvulnerable(true);owner.setGameMode(GameMode.SURVIVAL);
        Bukkit.getPluginManager().callEvent(new PlayerResourcePackStatusEvent(owner,JudgePackService.PACK_ID,PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED));
        stable=call(companions,"stable",owner);bodiesBefore=(int)call(bodies,"count");
    }
    PetRecord record(PetCatalog.Species species,PetCatalog.Stage stage)throws Exception{
        var ctor=PetRecord.class.getDeclaredConstructor(UUID.class,PetCatalog.Species.class,PetCatalog.Stage.class);ctor.setAccessible(true);
        return ctor.newInstance(new UUID(0,++serial),species,stage);
    }
    JsonObject entry(String id){return source.get(PetCatalog.Species.find(id));}
    void catalog()throws Exception{
        PetCatalog.Species[] all=PetCatalog.Species.values();check(all.length==94,"94 species registered");
        for(int i=0;i<LEGACY.length;i++)check(all[i].name().equals(LEGACY[i]),"legacy ordinal "+i+" remains "+LEGACY[i]);
        check(Arrays.stream(all).filter(PetCatalog.Species::mythical).count()==5,"five mythical species remain first");
        check(Arrays.stream(all).filter(s->s.rarity==PetCatalog.Rarity.RARE).count()==17,"17 explicit rare species");
        check(Arrays.stream(all).filter(s->s.rarity==PetCatalog.Rarity.COMMON).count()==72,"72 explicit common species");
        check(((Collection<?>)call(expansion,"profiles")).size()==57,"57 expansion profiles load through production initializer");
        try(InputStream stream=garden.getResource("pet-expansion.json")){
            check(stream!=null,"shipped expansion metadata exists");
            JsonObject root=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();Set<String> powers=new HashSet<>();
            for(JsonElement value:root.getAsJsonArray("species")){
                JsonObject e=value.getAsJsonObject();PetCatalog.Species species=PetCatalog.Species.find(e.get("id").getAsString());
                check(species!=null&&species.ordinal()>=37&&!source.containsKey(species),"unique appended profile "+e.get("id").getAsString());source.put(species,e);additions.add(species);
                Material food=Material.valueOf(e.get("food").getAsString());check(food.isItem()&&call(taming,"food",species)==food,"real taming food "+species.id);
                Object profile=call(expansion,"of",species);check(profile!=null&&call(profile,"food")==food,"runtime profile food matches "+species.id);
                check(e.getAsJsonArray("growth").size()==3&&new HashSet<>(e.getAsJsonArray("growth").asList()).size()==3,"three distinct growth descriptions "+species.id);
                JsonObject power=new JsonObject();for(String key:List.of("boosts","pulses","guard","armor","hit","aura"))power.add(key,e.get(key));
                check(powers.add(power.toString()),"mechanically distinct passive and hit data "+species.id);
                check(species.rare()==e.get("rarity").getAsString().equals("rare"),"rarity wiring "+species.id);
                for(PetCatalog.Stage stage:PetCatalog.Stage.values())check(renderer.hasModel(species,stage),"native model registered "+species.id+"/"+stage.id);
            }
        }
        check(additions.size()==57&&((Map<?,?>)get(renderer,"models")).size()==282,"all 282 native variants loaded");
    }
    void spawnRolls()throws Exception{
        Random random=new Random(20261108);Set<PetCatalog.Species> seen=new HashSet<>();int rare=0,total=80000;
        for(int i=0;i<total;i++){
            PetCatalog.Species s=(PetCatalog.Species)call(WildPetSpawns.class,"chooseSpecies",random,BiomeTerrain.Kind.values()[i%4],List.of());
            if(s.mythical())throw new AssertionError("ordinary spawn selected myth "+s);if(s.rare()){rare++;seen.add(s);}
        }
        check(rare>total*.075&&rare<total*.085,"default seeded rare share is 8%: "+rare+"/"+total);
        check(seen.size()==17,"all rare species reachable across habitats");
        Object saved=garden.getConfig().get("pets.rare.chance");
        try{
            for(double chance:new double[]{0,1}){
                garden.getConfig().set("pets.rare.chance",chance);double actual=garden.wildPets().rareChance();near(actual,chance,"configured rare chance "+chance);
                Random seeded=new Random(643021);Set<PetCatalog.Species> atOne=new HashSet<>();
                for(int i=0;i<4000;i++){
                    PetCatalog.Species s=(PetCatalog.Species)call(WildPetSpawns.class,"chooseSpecies",seeded,BiomeTerrain.Kind.values()[i%4],List.of(),actual);
                    if(s.mythical()||s.rare()!=(chance==1))throw new AssertionError("rare config "+chance+" selected "+s);
                    atOne.add(s);
                }
                check(atOne.size()==(chance==1?17:72),"config "+chance+" reaches every species in its tier and zero myths");
            }
        }finally{garden.getConfig().set("pets.rare.chance",saved);}
    }
    final Predicate<LivingEntity> none=entity->false;
    void passive(PetRecord record,int clock)throws Exception{call(skills,"passive",owner,record,clock,none);}
    int pulsePeriod(JsonObject entry,PetCatalog.Stage stage){double factor=stage==PetCatalog.Stage.BABY?2:stage==PetCatalog.Stage.MEDIUM?1.4:1;return Math.max(20,(int)Math.round(entry.getAsJsonArray("pulses").get(0).getAsJsonObject().get("every").getAsInt()*factor)*20);}
    void clearEffects()throws Exception{call(skills,"clear",owner);for(PotionEffect effect:owner.getActivePotionEffects())owner.removePotionEffect(effect.getType());}
    void passives()throws Exception{
        owner.teleport(home());clearEffects();
        Location feet=home();feet.getBlock().setType(Material.MOSS_CARPET,false);
        check((boolean)call(expansion,"condition",owner,"ON_MOSS"),"thin moss carpet at feet activates habitat condition");
        feet.getBlock().setType(Material.SNOW,false);
        check((boolean)call(expansion,"condition",owner,"ON_SNOW"),"thin snow layer at feet activates habitat condition");
        feet.getBlock().setType(Material.AIR,false);
        PetRecord hamster=record(PetCatalog.Species.STRAWBERRY_HAMSTER,PetCatalog.Stage.ADULT);int hp=pulsePeriod(entry("strawberry_hamster"),PetCatalog.Stage.ADULT);
        owner.setFoodLevel(18);passive(hamster,hp-20);check(owner.getFoodLevel()==18,"hamster waits for resource pulse interval");
        passive(hamster,hp);check(owner.getFoodLevel()==19,"hamster restores actual hunger at cadence");
        passive(hamster,hp+20);check(owner.getFoodLevel()==19,"hamster does not pulse on adjacent second");
        owner.setFoodLevel(19);passive(hamster,hp*2);check(owner.getFoodLevel()==20,"hamster food clamps to 20");passive(hamster,hp*3);check(owner.getFoodLevel()==20,"full owner gets no extra food");
        Location water=home().add(2,-1,0);water.getBlock().setType(Material.AIR,false);
        for(PetCatalog.Stage stage:PetCatalog.Stage.values()){
            PetRecord capy=record(PetCatalog.Species.COCOA_CAPYBARA,stage);int period=pulsePeriod(entry("cocoa_capybara"),stage);
            owner.setHealth(10);passive(capy,period);near(owner.getHealth(),10,"capybara cannot heal without water "+stage);
            water.getBlock().setType(Material.WATER,false);passive(capy,period-20);near(owner.getHealth(),10,"capybara waits for "+stage+" cadence from resource");
            passive(capy,period);near(owner.getHealth(),11,"capybara heals near water "+stage);
            owner.setHealth(19.6);passive(capy,period*2);near(owner.getHealth(),20,"capybara healing clamps to max health "+stage);
            water.getBlock().setType(Material.AIR,false);clearEffects();
        }
        water.getBlock().setType(Material.STONE,false);
        PetRecord young=record(PetCatalog.Species.STRAWBERRY_HAMSTER,PetCatalog.Stage.MEDIUM);owner.setFoodLevel(10);
        passive(young,hp);check(owner.getFoodLevel()==10,"juvenile pulse does not use adult cadence");
        passive(young,pulsePeriod(entry("strawberry_hamster"),PetCatalog.Stage.MEDIUM));check(owner.getFoodLevel()==11,"juvenile 1.4 cadence executes gameplay pulse");
        PetRecord shiba=record(PetCatalog.Species.PEACH_SHIBA,PetCatalog.Stage.ADULT);clearEffects();owner.setHealth(12);passive(shiba,20);
        check(!owner.hasPotionEffect(PotionEffectType.SPEED),"shiba speed requires high health");owner.setHealth(20);passive(shiba,20);
        PotionEffect granted=owner.getPotionEffect(PotionEffectType.SPEED);check(granted!=null&&granted.getAmplifier()==0,"shiba installs actual speed at high health");
        call(skills,"clear",owner);check(!owner.hasPotionEffect(PotionEffectType.SPEED),"pet-owned unchanged effect clears on dismissal");
        owner.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,500,1,false,true,true));passive(shiba,20);
        PotionEffect outside=owner.getPotionEffect(PotionEffectType.SPEED);check(outside!=null&&outside.getDuration()==500&&outside.getAmplifier()==1,"pet preserves pre-existing external potion");
        call(skills,"clear",owner);check(owner.getPotionEffect(PotionEffectType.SPEED).getAmplifier()==1,"dismissal preserves external potion");
        owner.removePotionEffect(PotionEffectType.SPEED);passive(shiba,20);
        owner.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,520,2,false,true,true));call(skills,"clear",owner);
        check(owner.getPotionEffect(PotionEffectType.SPEED)!=null&&owner.getPotionEffect(PotionEffectType.SPEED).getAmplifier()==2,"external replacement revokes pet effect ownership");clearEffects();
    }
    <T extends LivingEntity> T mob(Location at,Class<T> type)throws Exception{
        // Evergarden rejects every unowned creature in its world. Use the actual
        // dungeon runtime stamp in the pre-spawn consumer, before the filter runs.
        // This is neither a pet marker (which would block damage) nor a boss marker.
        NamespacedKey runtime=(NamespacedKey)get(garden.dungeons(),"runKey");String run=(String)get(garden.dungeons(),"runId");
        T entity=world.spawn(at,type,CreatureSpawnEvent.SpawnReason.CUSTOM,e->{
            e.getPersistentDataContainer().set(runtime,PersistentDataType.STRING,run);
            e.setAI(false);e.setGravity(false);e.setPersistent(false);e.setInvulnerable(false);
        });
        fixtures.add(entity);check(entity.isValid()&&!entity.isDead(),"sanctioned live "+type.getSimpleName()+" fixture accepted by creature filter");return entity;
    }
    Object summoned(PetRecord record)throws Exception{
        call(companions,"dismiss",owner);((Map<?,?>)get(stable,"pets")).clear();owner.teleport(home());call(stable,"add",record);
        check(call(companions,"summon",owner,record.id())==null,"real summon accepts "+record.species().id+"/"+record.stage().id);
        Object body=call(companions,"body",owner.getUniqueId());check(body!=null,"summon creates companion receiver and rig");remember(body);return body;
    }
    static final class Roll extends Random {final double roll;int draws;Roll(double roll){this.roll=roll;}@Override public double nextDouble(){draws++;return roll;}@Override public int nextInt(int bound){return bound-1;}}
    void combat()throws Exception{
        Zombie target=mob(home().add(2,0,0),Zombie.class);target.getAttribute(Attribute.MAX_HEALTH).setBaseValue(100);
        target.getAttribute(Attribute.ARMOR).setBaseValue(0);target.getAttribute(Attribute.ARMOR_TOUGHNESS).setBaseValue(0);target.getEquipment().clear();target.setHealth(100);
        PetRecord basilisk=record(PetCatalog.Species.OBSIDIAN_BASILISK,PetCatalog.Stage.ADULT);Object tank=summoned(basilisk);
        put(basilisk,"health",60d);put(basilisk,"healthTick",(long)Bukkit.getCurrentTick());put(basilisk,"combatTick",(long)Bukkit.getCurrentTick());
        call(companions,"hurt",tank,owner,10d);near((double)get(basilisk,"health"),56,"real counterattack applies rare armour reduction");
        var guarded=new EntityDamageByEntityEvent(target,owner,EntityDamageEvent.DamageCause.ENTITY_ATTACK,10d);put(basilisk,"mode","assist");
        call(companions,"guard",guarded);near(guarded.getDamage(),9.2,"nearby rare assist pet reduces owner damage");
        put(basilisk,"mode","follow");var plain=new EntityDamageByEntityEvent(target,owner,EntityDamageEvent.DamageCause.ENTITY_ATTACK,10d);call(companions,"guard",plain);near(plain.getDamage(),10,"owner guard requires assist mode");
        call(companions,"dismiss",owner);
        PetRecord kirin=record(PetCatalog.Species.PRISM_KIRIN,PetCatalog.Stage.ADULT);Object striker=summoned(kirin);put(kirin,"mode","assist");
        ((Random)get(companions,"random")).setSeed(1234L);garden.getConfig().set("pets.companions.counter-chance",0);
        check(target.isValid()&&!target.isDead()&&!target.isInvulnerable(),"first-strike hostile target is a live damageable entity");
        check(!(boolean)get(striker,"ambushed"),"new companion has not spent its first strike");
        List<String> damageEvents=new ArrayList<>();Listener damageTrace=new Listener(){
            @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=false) public void trace(EntityDamageEvent event){
                if(event.getEntity()!=target)return;Entity direct=event.getDamageSource().getDirectEntity(),cause=event.getDamageSource().getCausingEntity();
                String detail="damage event cancelled="+event.isCancelled()+" raw="+event.getDamage()+" final="+event.getFinalDamage()+" cause="+event.getCause()
                    +" direct="+(direct==null?"none":direct.getType()+"/"+direct.getUniqueId())+" causing="+(cause==null?"none":cause.getType()+"/"+cause.getUniqueId())
                    +" receiverHealth="+target.getHealth()+" noDamageTicks="+target.getNoDamageTicks();
                damageEvents.add(detail);lines.add("DIAG: "+detail);getLogger().info(detail);
            }
        };
        Bukkit.getPluginManager().registerEvents(damageTrace,this);double first,next;
        try{
            double before=target.getHealth();call(companions,"strike",striker,owner,target);first=before-target.getHealth();
            target.setHealth(100);call(companions,"strike",striker,owner,target);next=100-target.getHealth();
        }finally{HandlerList.unregisterAll(damageTrace);}
        String strikeDetails=" first="+first+" next="+next+" valid="+target.isValid()+" dead="+target.isDead()+" invulnerable="+target.isInvulnerable()
            +" armor="+target.getAttribute(Attribute.ARMOR).getValue()+" events="+damageEvents;
        lines.add("DIAG: first-strike"+strikeDetails);getLogger().info("first-strike"+strikeDetails);
        check(damageEvents.size()==2,"both production bites deliver real Paper damage events"+strikeDetails);
        check(first>next&&next>0,"real first strike deals extra damage once"+strikeDetails);near(first/next,entry("prism_kirin").getAsJsonObject("hit").get("first").getAsDouble(),"first strike uses authored multiplier");
        call(companions,"dismiss",owner);target.removePotionEffect(PotionEffectType.GLOWING);
        PetRecord proc=record(PetCatalog.Species.PRISM_KIRIN,PetCatalog.Stage.ADULT);Roll yes=new Roll(0);call(skills,"onHit",proc,owner,target,6d,yes);
        check(target.hasPotionEffect(PotionEffectType.GLOWING)&&yes.draws==1,"deterministic successful hit installs actual status");
        long expiry=(long)get(proc,"traitTick");check(expiry==Integer.toUnsignedLong(Bukkit.getCurrentTick())+entry("prism_kirin").getAsJsonObject("hit").get("cooldown").getAsInt(),"hit sets per-record cooldown on global server tick");
        target.removePotionEffect(PotionEffectType.GLOWING);Roll gated=new Roll(0);call(skills,"onHit",proc,owner,target,6d,gated);
        check(!target.hasPotionEffect(PotionEffectType.GLOWING)&&gated.draws==0,"cooldown blocks actual proc before consuming randomness");
        World other=Bukkit.getWorlds().stream().filter(w->w!=world).findFirst().orElse(null);
        if(other!=null){
            owner.teleport(new Location(other,.5,Math.min(other.getMaxHeight()-5,180),.5));Roll across=new Roll(0);call(skills,"onHit",proc,owner,target,6d,across);
            check(!target.hasPotionEffect(PotionEffectType.GLOWING)&&across.draws==0,"moving worlds preserves hit cooldown");owner.teleport(home());
        }
        put(proc,"traitTick",Integer.toUnsignedLong(Bukkit.getCurrentTick()));Roll no=new Roll(.99999);call(skills,"onHit",proc,owner,target,6d,no);
        check(!target.hasPotionEffect(PotionEffectType.GLOWING)&&no.draws==1,"deterministic failed chance produces no status");
        call(skills,"onHit",proc,owner,target,6d,new Roll(0));check(target.hasPotionEffect(PotionEffectType.GLOWING),"proc becomes available at expiry");
        PetRecord moth=record(PetCatalog.Species.TWILIGHT_MOTH_DRAGON,PetCatalog.Stage.ADULT);owner.setHealth(10);call(skills,"onHit",moth,owner,target,10d,new Roll(0));near(owner.getHealth(),10.8,"rare lifesteal restores health from actual dealt damage");
        target.remove();clearEffects();owner.setHealth(20);
    }
    void aura()throws Exception{
        call(companions,"dismiss",owner);owner.teleport(darkRoom());
        check((boolean)call(expansion,"condition",owner,"DARK"),"enclosed fixture supplies aura condition without sky or time assumptions");
        Zombie ordinary=mob(darkRoom().add(2,0,0),Zombie.class),boss=mob(darkRoom().add(-2,0,0),Zombie.class),pet=mob(darkRoom().add(0,0,2),Zombie.class);
        boss.addScoreboardTag("boss");pet.getPersistentDataContainer().set(garden.key("pet_body"),PersistentDataType.BYTE,(byte)1);
        Cow animal=mob(darkRoom().add(0,0,-2),Cow.class);List<UUID> tested=new ArrayList<>();
        Predicate<LivingEntity> rules=entity->{tested.add(entity.getUniqueId());try{return (boolean)call(companions,"foe",owner,entity);}catch(Exception e){throw new RuntimeException(e);}};
        check(!(boolean)call(companions,"foe",owner,owner)&&!(boolean)call(companions,"foe",owner,boss),"production foe predicate excludes players and bosses");
        // A real registered receiver exercises the pet exclusion, rather than trusting a custom label.
        PetRecord r=record(PetCatalog.Species.LANTERN_FIREFLY,PetCatalog.Stage.ADULT);
        Object body=call(bodyType,"spawn",garden,renderer,r.species(),r.stage(),darkRoom().add(1,0,1),r,owner.getUniqueId());check(body!=null,"aura pet fixture spawns");remember(body);call(bodies,"add",body,companions);
        LivingEntity receiver=(LivingEntity)get(body,"shell");check(!(boolean)call(companions,"foe",owner,receiver),"production foe predicate excludes real pet receiver");
        Set<UUID> denied=Set.of(boss.getUniqueId(),pet.getUniqueId());
        Predicate<LivingEntity> filter=entity->!denied.contains(entity.getUniqueId())&&rules.test(entity);
        try{
            call(skills,"passive",owner,r,20,filter);
            check(ordinary.hasPotionEffect(PotionEffectType.GLOWING),"aura actually marks eligible hostile mob through passed predicate");
            check(!boss.hasPotionEffect(PotionEffectType.GLOWING)&&!pet.hasPotionEffect(PotionEffectType.GLOWING),"aura obeys passed exclusions for boss and pet");
            check(!owner.hasPotionEffect(PotionEffectType.GLOWING)&&!receiver.hasPotionEffect(PotionEffectType.GLOWING)&&!animal.hasPotionEffect(PotionEffectType.GLOWING),"aura never marks players, real pets or non-monsters");
            check(tested.contains(ordinary.getUniqueId()),"aura consulted caller targeting rule");
        }finally{call(bodies,"remove",body);openBody=null;}
        for(Entity entity:List.of(ordinary,boss,pet,animal))entity.remove();clearEffects();owner.teleport(home());
    }
    void remember(Object body)throws Exception{
        openBody=body;for(String key:List.of("shell","carrier","nametag")){Entity entity=(Entity)get(body,key);if(entity!=null)created.add(entity.getUniqueId());}
        NativePetRenderer.Rig rig=(NativePetRenderer.Rig)get(body,"rig");for(ItemDisplay d:rig.displays())created.add(d.getUniqueId());
    }
    void renderBatch()throws Exception{
        for(int batch=0;batch<3&&variant<171;batch++,variant++){
            PetCatalog.Species species=additions.get(variant/3);PetCatalog.Stage stage=PetCatalog.Stage.values()[variant%3];PetRecord record=record(species,stage);
            ((Map<?,?>)get(stable,"pets")).clear();call(stable,"add",record);
            Object body;
            if(stage==PetCatalog.Stage.ADULT)body=summoned(record);
            else{body=call(bodyType,"spawn",garden,renderer,species,stage,home().add(0,0,3),record,owner.getUniqueId());check(body!=null,"PetBody.spawn "+species.id+"/"+stage.id);remember(body);call(bodies,"add",body,companions);}
            try{
                NativePetRenderer.Rig rig=(NativePetRenderer.Rig)get(body,"rig");check(rig.partCount()>0,"real visible display bones "+species.id+"/"+stage.id);
                int i=0;for(String clip:PetCatalog.ANIMATIONS){
                    rig.tick(clip,20+i*9,4,-3,1.1f,15,8);put(rig,"clipTime",.45f);put(rig,"lastAdvance",Bukkit.getCurrentTick()-4);
                    rig.tick(clip,25+i*9,5,-2,1.1f,16,9);i++;
                    check(rig.animation().equals(clip)&&finite(rig),"real "+clip+" transforms remain finite "+species.id+"/"+stage.id);
                }
                menuLabels(record);
            }finally{
                if(stage==PetCatalog.Stage.ADULT)call(companions,"dismiss",owner);else call(bodies,"remove",body);openBody=null;
            }
            check((int)call(bodies,"count")==bodiesBefore,"body registry restored after "+species.id+"/"+stage.id);
        }
        if(variant<171){later(1,this::renderBatch);return;}
        call(companions,"dismiss",owner);owner.closeInventory();
        check(created.stream().noneMatch(id->{Entity e=Bukkit.getEntity(id);return e!=null&&e.isValid();}),"all receiver, carrier, nametag and display entities cleaned after 171 variants");
        check((int)call(bodies,"count")==bodiesBefore,"no body registry leaks");finish(null);
    }
    boolean finite(NativePetRenderer.Rig rig){
        for(ItemDisplay display:rig.displays()){
            if(!display.isValid())return false;var t=display.getTransformation();var a=t.getTranslation();var b=t.getScale();var c=t.getLeftRotation();var d=t.getRightRotation();
            for(float v:new float[]{a.x,a.y,a.z,b.x,b.y,b.z,c.x,c.y,c.z,c.w,d.x,d.y,d.z,d.w})if(!Float.isFinite(v))return false;
            if(b.x<=0||b.y<=0||b.z<=0)return false;Location at=display.getLocation();if(!Double.isFinite(at.getX()+at.getY()+at.getZ()))return false;
        }
        return true;
    }
    @SuppressWarnings("unchecked") void menuLabels(PetRecord record)throws Exception{
        List<String> description=(List<String>)call(menu,"describe",stable,record);String text=String.join("\n",description);JsonObject e=source.get(record.species());
        check(text.contains(record.species().rarity.thai)&&text.contains(record.stage().thai)&&text.contains(e.getAsJsonArray("growth").get(record.stage().ordinal()).getAsString()),"shared Java/Bedrock menu shows real rarity and age design "+record.species().id+"/"+record.stage().id);
        if(record.species()==PetCatalog.Species.PRISM_KIRIN||record.species()==PetCatalog.Species.STRAWBERRY_HAMSTER){
            call(menu,"openPet",owner,record.id());ItemStack icon=owner.getOpenInventory().getTopInventory().getItem(4);
            check(icon!=null&&icon.getItemMeta().lore()!=null,"real chest menu creates pet portrait and lore");
            String lore=icon.getItemMeta().lore().stream().map(PlainTextComponentSerializer.plainText()::serialize).reduce("",(a,b)->a+"\n"+b);
            check(lore.contains(record.species().rarity.thai)&&lore.contains(record.stage().thai),"opened inventory contains rarity and growth labels");owner.closeInventory();
        }
    }
    void finish(Throwable error){
        if(finished)return;finished=true;
        try{if(companions!=null&&owner!=null)call(companions,"dismiss",owner);if(openBody!=null&&bodies!=null)call(bodies,"remove",openBody);if(owner!=null){clearEffects();owner.closeInventory();}}catch(Throwable cleanup){if(error==null)error=cleanup;else error.addSuppressed(cleanup);}
        for(Entity e:fixtures)if(e.isValid())e.remove();
        if(actor!=null){actor.discard();var list=net.minecraft.server.MinecraftServer.getServer().getPlayerList();list.getPlayers().remove(actor);list.getPlayersByUUID().remove(actor.getUUID());}
        if(error!=null)getLogger().log(java.util.logging.Level.SEVERE,"PET EXPANSION CHECK FAILED",error);
        String report=(error==null?"PASS ":"FAIL ")+checks+" checks; 94 species / 57 profiles / 171 new variants, real summon and display clips, menu labels, real passives, potion ownership, combat, aura, seeded rarity and cleanup"+(error==null?"":": "+error);
        try{Files.writeString(Path.of("pet-grounding-result.txt"),report+"\n"+String.join("\n",lines)+"\n",StandardCharsets.UTF_8);}catch(IOException e){throw new RuntimeException(e);}
        getLogger().info(report);Bukkit.getScheduler().runTaskLater(this,Bukkit::shutdown,1);
    }
}
