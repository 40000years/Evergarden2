package com.example.voidscape.pet;

import com.example.voidscape.VoidscapePlugin;
import com.example.voidscape.boss.JudgePackService;
import com.example.voidscape.world.BiomeTerrain;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.block.Biome;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.*;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import java.util.*;
import java.util.logging.Level;
import java.util.random.RandomGenerator;

/**
 * Rare, peaceful encounters that players can befriend with {@link PetTaming}.
 * Java players see {@link NativePetRenderer} displays; Bedrock players see a Geyser
 * custom entity on a separate carrier, exactly like the sanctuary bosses.
 */
public final class WildPetSpawns implements PetBodies.Manager,AutoCloseable {
    private static final List<PetCatalog.Species> SPECIES=Arrays.stream(PetCatalog.Species.values())
        .filter(s->!s.mythical()).toList();
    private static final Set<PetCatalog.Species> FLYING=EnumSet.of(PetCatalog.Species.FROST_WYVERN,
        PetCatalog.Species.ASTRAL_OWL,PetCatalog.Species.BLOOD_GARGOYLE,
        PetCatalog.Species.ABYSSAL_MANTA,PetCatalog.Species.CHRONO_FAIRY,PetCatalog.Species.CLOVER_BUMBLE,
        PetCatalog.Species.TITAN_BEETLE,PetCatalog.Species.BONE_DRAKE,PetCatalog.Species.JADE_LUNG,
        PetCatalog.Species.LUNA_MOTH,PetCatalog.Species.CELESTIAL_KOI,PetCatalog.Species.AURORA_JELLY);
    /** Each species is found everywhere, but three times as often in the regions it calls home. */
    private static final Map<PetCatalog.Species,Set<BiomeTerrain.Kind>> HABITAT=new EnumMap<>(PetCatalog.Species.class);
    static {
        var gardens=BiomeTerrain.Kind.GARDENS;var glacier=BiomeTerrain.Kind.GLACIER;
        var forest=BiomeTerrain.Kind.ANCIENT_FOREST;var city=BiomeTerrain.Kind.LUNAR_CITY;
        HABITAT.put(PetCatalog.Species.ARCANE_NINE_TAILS,EnumSet.of(gardens,forest));
        HABITAT.put(PetCatalog.Species.FROST_WYVERN,EnumSet.of(glacier));
        HABITAT.put(PetCatalog.Species.ASTRAL_OWL,EnumSet.of(forest,city));
        HABITAT.put(PetCatalog.Species.CRIMSON_CERBERUS,EnumSet.of(city));
        HABITAT.put(PetCatalog.Species.SHADOW_PANTHER,EnumSet.of(forest));
        HABITAT.put(PetCatalog.Species.BLOOD_GARGOYLE,EnumSet.of(city));
        HABITAT.put(PetCatalog.Species.RUNIC_GOLEM,EnumSet.of(glacier,city));
        HABITAT.put(PetCatalog.Species.WORLD_TREE_ENTLING,EnumSet.of(forest,gardens));
        HABITAT.put(PetCatalog.Species.CRYSTAL_TORTOISE,EnumSet.of(gardens,glacier));
        HABITAT.put(PetCatalog.Species.ABYSSAL_MANTA,EnumSet.of(city,glacier));
        HABITAT.put(PetCatalog.Species.ALCHEMIST_MIMIC,EnumSet.of(city));
        HABITAT.put(PetCatalog.Species.CHRONO_FAIRY,EnumSet.of(gardens));
        HABITAT.put(PetCatalog.Species.MOCHI_SLIME,EnumSet.of(gardens));
        HABITAT.put(PetCatalog.Species.CLOUD_LAMB,EnumSet.of(gardens,glacier));
        HABITAT.put(PetCatalog.Species.MOON_RABBIT,EnumSet.of(city,gardens));
        HABITAT.put(PetCatalog.Species.BLOOM_HEDGEHOG,EnumSet.of(forest,gardens));
        HABITAT.put(PetCatalog.Species.CORAL_AXOLOTL,EnumSet.of(glacier,city));
        HABITAT.put(PetCatalog.Species.CLOVER_BUMBLE,EnumSet.of(gardens));
        HABITAT.put(PetCatalog.Species.AURORA_PENGUIN,EnumSet.of(glacier));
        HABITAT.put(PetCatalog.Species.STORM_RAPTOR,EnumSet.of(forest));
        HABITAT.put(PetCatalog.Species.MAGMA_RHINO,EnumSet.of(city));
        HABITAT.put(PetCatalog.Species.ECLIPSE_SCORPION,EnumSet.of(city));
        HABITAT.put(PetCatalog.Species.RAIJIN_TIGER,EnumSet.of(glacier,forest));
        HABITAT.put(PetCatalog.Species.TITAN_BEETLE,EnumSet.of(forest));
        HABITAT.put(PetCatalog.Species.ANUBIS_JACKAL,EnumSet.of(city));
        HABITAT.put(PetCatalog.Species.BONE_DRAKE,EnumSet.of(city,glacier));
        HABITAT.put(PetCatalog.Species.JADE_LUNG,EnumSet.of(gardens,glacier));
        HABITAT.put(PetCatalog.Species.CRYSTAL_STAG,EnumSet.of(forest,glacier));
        HABITAT.put(PetCatalog.Species.LUNA_MOTH,EnumSet.of(forest,city));
        HABITAT.put(PetCatalog.Species.CELESTIAL_KOI,EnumSet.of(gardens));
        HABITAT.put(PetCatalog.Species.STARLIGHT_PEACOCK,EnumSet.of(gardens,city));
        HABITAT.put(PetCatalog.Species.AURORA_JELLY,EnumSet.of(glacier,city));
    }
    private static final int STEP=5;
    private static final double WALK_STEP=.2,FLY_STEP=.35,GROUND_LEASH=8*8,AIR_LEASH=12*12,ACTIVE_RANGE=48*48;
    /** An expired pet with a player this close (squared blocks) waits, up to LINGER_MS past its time. */
    private static final double WATCHED=24*24;
    private static final long LINGER_MS=300_000;
    private final VoidscapePlugin plugin;
    private final NativePetRenderer renderer;
    private final PetBodies bodies;
    private final PetTerrain terrain;
    private final Map<UUID,PetBody> active=new LinkedHashMap<>();
    private final RandomGenerator random=new Random();
    private PetTaming taming;
    private BukkitTask attempts,maintenance;
    private boolean enabled,closed;
    private int clock;
    private long busy;
    private String lastFailure="",lastRoll="ยังไม่เคยสุ่ม";

    WildPetSpawns(VoidscapePlugin plugin,NativePetRenderer renderer,PetBodies bodies,PetTerrain terrain){
        this.plugin=plugin;this.renderer=renderer;this.bodies=bodies;this.terrain=terrain;
        reload();maintenance=Bukkit.getScheduler().runTaskTimer(plugin,this::tick,1,1);
    }
    void taming(PetTaming taming){this.taming=taming;}
    public static List<PetCatalog.Species> eligibleSpecies(){return SPECIES;}
    public static PetCatalog.Species chooseSpecies(RandomGenerator random){return SPECIES.get(random.nextInt(SPECIES.size()));}
    /** The Evergarden region a vanilla biome paints, or null outside them. */
    static BiomeTerrain.Kind region(Biome biome){
        return switch(biome==null?"":biome.getKey().getKey()){
            case "cherry_grove","flower_forest","meadow"->BiomeTerrain.Kind.GARDENS;
            case "frozen_peaks"->BiomeTerrain.Kind.GLACIER;
            case "old_growth_spruce_taiga"->BiomeTerrain.Kind.ANCIENT_FOREST;
            case "pale_garden"->BiomeTerrain.Kind.LUNAR_CITY;
            default->null;
        };
    }
    static boolean home(PetCatalog.Species species,BiomeTerrain.Kind region){return region!=null&&HABITAT.getOrDefault(species,Set.of()).contains(region);}
    /**
     * Home regions weigh three, elsewhere one, so every species can still turn up anywhere;
     * one this player met among their last few encounters counts half, to keep variety.
     */
    static PetCatalog.Species chooseSpecies(RandomGenerator random,BiomeTerrain.Kind region,Collection<PetCatalog.Species> recent){
        double[] weights=new double[SPECIES.size()];double total=0;
        for(int i=0;i<weights.length;i++){
            PetCatalog.Species species=SPECIES.get(i);
            double weight=home(species,region)?3:1;
            if(recent!=null&&recent.contains(species))weight*=.5;
            weights[i]=weight;total+=weight;
        }
        double roll=random.nextDouble()*total;
        for(int i=0;i<weights.length;i++)if((roll-=weights[i])<0)return SPECIES.get(i);
        return SPECIES.getLast();
    }
    public static PetCatalog.Stage stageForRoll(double roll,int baby,int medium,int adult) {
        if(!Double.isFinite(roll))throw new IllegalArgumentException("Finite growth roll required");
        double b=Math.max(0,baby),m=Math.max(0,medium),a=Math.max(0,adult),total=b+m+a;
        if(total<=0){b=85;m=13;a=2;total=100;}
        double value=Math.clamp(roll,0,Math.nextDown(1d))*total;
        return value<b?PetCatalog.Stage.BABY:value<b+m?PetCatalog.Stage.MEDIUM:PetCatalog.Stage.ADULT;
    }
    public static boolean flies(PetCatalog.Species species){return FLYING.contains(species);}
    private int setting(String key,int fallback,int min,int max){return plugin.integer("pets.wild-spawns."+key,fallback,min,max);}
    public void reload() {
        clear();if(attempts!=null)attempts.cancel();attempts=null;
        enabled=plugin.getConfig().getBoolean("pets.wild-spawns.enabled",true);
        if(enabled&&!closed){long period=setting("attempt-interval-seconds",45,5,3600)*20L;
            attempts=Bukkit.getScheduler().runTaskTimer(plugin,this::attemptSpawns,period,period);}
    }
    public int count(){return active.size();}
    Collection<PetBody> bodies(){return active.values();}
    private boolean bedrockReady(){return plugin.arkhaiaBedrock()!=null&&plugin.arkhaiaBedrock().ready();}
    public String status(){return (enabled?"เปิด":"ปิด")+" · สัตว์เลี้ยงป่า "+count()+"/"+setting("max-active-world",16,1,64)
        +" ตัว · "+SPECIES.size()+" ชนิด · "+renderer.status()+" · Bedrock "+(bedrockReady()?"native":"ไม่พร้อม")
        +(lastFailure.isEmpty()?"":" · "+lastFailure)+" · สุ่มล่าสุด: "+lastRoll;}

    /** Players who can both see a pet and trigger natural encounters. */
    private boolean audience(Player player) {
        if(!player.isOnline()||player.isDead()||player.getWorld()!=plugin.world()||player.getGameMode()==GameMode.SPECTATOR)return false;
        return JudgePackService.bedrock(player)?bedrockReady():plugin.judgePack().applied(player);
    }
    private double chance(){double value=plugin.getConfig().getDouble("pets.wild-spawns.chance-per-player",.12);
        return Double.isFinite(value)?Math.clamp(value,0,1):.12;}
    private int nearby(Location at,double radius){double square=radius*radius;return (int)active.values().stream()
        .filter(p->p.shell.getWorld()==at.getWorld()&&p.location().distanceSquared(at)<square).count();}
    /** Each player's latest natural roll, in words, for /evergarden pets wild status. */
    private final Map<UUID,String> rolls=new HashMap<>();
    /** Game ticks each eligible player has gone without an encounter; full means the next roll passes. */
    private final Map<UUID,Long> drought=new HashMap<>();
    private final Map<UUID,List<PetCatalog.Species>> recentHistory=new HashMap<>();
    private long guaranteeTicks(){return setting("guarantee-minutes",8,0,240)*1200L;}
    private String why(Player player){
        if(player.getGameMode()==GameMode.SPECTATOR)return "โหมดผู้ชมไม่ทำให้สัตว์เกิด";
        if(player.isDead())return "ผู้เล่นหมดสติ";
        if(JudgePackService.bedrock(player))return bedrockReady()?null:"Geyser entity API ยังไม่พร้อม";
        return plugin.judgePack().applied(player)?null:"ยังไม่ได้รับแพ็ก World Boss (/evergarden boss pack)";
    }
    private void attemptSpawns() {
        if(!enabled||closed||!renderer.ready())return;
        long interval=setting("attempt-interval-seconds",45,5,3600)*20L;
        List<Player> players=new ArrayList<>(plugin.world().getPlayers());Collections.shuffle(players);
        rolls.keySet().retainAll(players.stream().map(Player::getUniqueId).toList());
        for(Player player:players) {
            UUID id=player.getUniqueId();
            if(count()>=setting("max-active-world",16,1,64)){rolls.put(id,"สัตว์ป่าครบจำนวนสูงสุดของโลกแล้ว");continue;}
            String blocked=why(player);
            if(blocked!=null){rolls.put(id,blocked);continue;}
            if(nearby(player.getLocation(),setting("nearby-radius",96,32,256))>=setting("max-nearby-player",2,1,8)){
                rolls.put(id,"มีสัตว์ป่าใกล้ตัวครบ "+setting("max-nearby-player",2,1,8)+" ตัวแล้ว");continue;
            }
            long waited=drought.merge(id,interval,Long::sum);
            boolean guaranteed=chance()>0&&guaranteeTicks()>0&&waited>=guaranteeTicks();
            if(!guaranteed&&random.nextDouble()>=chance()){
                rolls.put(id,"สุ่มไม่ผ่าน ("+Math.round(chance()*100)+"%) · การันตีในอีก "+Math.max(0,(guaranteeTicks()-waited)/1200)+" นาทีเกม");continue;
            }
            var stage=stageForRoll(random.nextDouble(),setting("stage-weights.baby",85,0,100000),
                setting("stage-weights.medium",13,0,100000),setting("stage-weights.adult",2,0,100000));
            Location at=findNaturalSpot(player,stage);
            if(at==null&&stage!=PetCatalog.Stage.BABY){
                stage=stage==PetCatalog.Stage.ADULT?PetCatalog.Stage.MEDIUM:PetCatalog.Stage.BABY;
                at=findNaturalSpot(player,stage);
                if(at==null&&stage!=PetCatalog.Stage.BABY){
                    stage=PetCatalog.Stage.BABY;
                    at=findNaturalSpot(player,stage);
                }
            }
            if(at==null){rolls.put(id,lastRoll);continue;}
            BiomeTerrain.Kind region=region(at.getWorld().getBiome(at.getBlockX(),at.getBlockY(),at.getBlockZ()));
            var species=chooseSpecies(random,region,recentHistory.get(id));
            if(!spawn(species,stage,at)){rolls.put(id,"พบพื้นแต่เกิดไม่สำเร็จ · "+lastFailure);continue;}
            drought.remove(id);
            recentHistory.computeIfAbsent(id,k->new ArrayList<>()).add(species);
            if(recentHistory.get(id).size()>4)recentHistory.get(id).remove(0);
            announce(player,at);
            String where=region==null?"":" · "+region.title+(home(species,region)?" (ถิ่นของมัน)":"");
            rolls.put(id,"เกิด "+species.display+" · "+stage.thai+" ที่ "+at.getBlockX()+" "+at.getBlockY()+" "+at.getBlockZ()+where+(guaranteed?" (การันตี)":""));
            plugin.getLogger().info("Wild pet "+species.id+"/"+stage.id+" appeared near "+player.getName()+" at "+at.getBlockX()+" "+at.getBlockY()+" "+at.getBlockZ()+(guaranteed?" (guaranteed)":""));
        }
    }
    /** A soft chime and sparkle where a pet appears, and for the player it came for, which way to look. */
    private void announce(Player player,Location at){
        Location glow=at.clone().add(0,.8,0);
        at.getWorld().spawnParticle(Particle.END_ROD,glow,24,.5,.7,.5,.03);
        at.getWorld().playSound(glow,Sound.BLOCK_AMETHYST_BLOCK_CHIME,SoundCategory.NEUTRAL,2.2f,1.25f);
        if(!plugin.getConfig().getBoolean("pets.wild-spawns.announce",true))return;
        Location eye=player.getLocation();double dx=at.getX()-eye.getX(),dz=at.getZ()-eye.getZ();
        player.sendActionBar(Component.text("✦ สัมผัสได้ถึงสัตว์วิเศษ"+direction(wrap(yawTowards(dx,dz)-eye.getYaw()))
            +" ห่างราว "+Math.round(Math.sqrt(dx*dx+dz*dz))+" บล็อก",NamedTextColor.LIGHT_PURPLE));
    }
    /** Where something lies relative to the way a player faces (positive is to their right). */
    static String direction(float relative){
        String[] names={"ด้านหน้า","ทางหน้าเยื้องขวา","ทางขวา","ทางหลังเยื้องขวา","ด้านหลัง","ทางหลังเยื้องซ้าย","ทางซ้าย","ทางหน้าเยื้องซ้าย"};
        return names[Math.floorMod(Math.round(relative/45f),8)];
    }
    /** Status for one player: the outcome of their latest natural roll. */
    public String status(Player player){
        if(player.getWorld()!=plugin.world())return "คุณอยู่นอกโลก Evergarden · สัตว์ป่าเกิดเฉพาะใน Evergarden";
        String blocked=why(player);
        return "คุณ: "+(blocked!=null?blocked:rolls.getOrDefault(player.getUniqueId(),"รอการสุ่มรอบถัดไป (ทุก "+setting("attempt-interval-seconds",45,5,3600)+" วินาทีเกม)"));
    }
    /** The ground a player stands on, or the ground below a flying player; their own height when over void. */
    private static double groundLevel(Player player){
        Location at=player.getLocation();double ground=PetTerrain.groundBelow(at,128);
        return Double.isNaN(ground)?at.getY():ground;
    }
    private Location findNaturalSpot(Player player,PetCatalog.Stage stage) {
        World world=plugin.world();Location center=player.getLocation();
        double min=setting("min-player-distance",24,12,128),max=Math.max(min+1,setting("max-player-distance",48,16,192));
        int base=(int)Math.floor(groundLevel(player)),attempts=setting("spot-attempts",24,1,64);
        Map<String,Integer> rejected=new TreeMap<>();
        for(int attempt=0;attempt<attempts;attempt++) {
            // Two tries in three fall within 110 degrees of where the player looks, so encounters are seen, not missed behind them.
            double angle=attempt%3<2?Math.toRadians(center.getYaw()+90)+(random.nextDouble()-.5)*Math.toRadians(220):random.nextDouble()*Math.PI*2;
            double distance=Math.sqrt(min*min+random.nextDouble()*(max*max-min*min));
            int x=(int)Math.floor(center.getX()+Math.cos(angle)*distance),z=(int)Math.floor(center.getZ()+Math.sin(angle)*distance);
            if(!PetTerrain.loadedFootprint(world,x,z)){rejected.merge("chunk ยังไม่โหลด",1,Integer::sum);continue;}
            // Skylands stack islands: look for floor near the player's own level, not the highest block.
            double y=PetTerrain.floorBetween(world,x,z,base+16,base-36);
            if(Double.isNaN(y)){rejected.merge("ไม่มีพื้นใกล้ระดับผู้เล่น",1,Integer::sum);continue;}
            Location at=new Location(world,x+.5,y,z+.5,random.nextFloat()*360,0);
            String why=terrain.reason(at,PetTerrain.radius(stage),PetTerrain.HEADROOM,true);
            if(why==null&&PetTerrain.roofed(world,x,(int)y,z))why="มีหลังคาหรือเพดานถ้ำ";
            if(why==null&&!spaced(at))why="ใกล้สัตว์ป่าตัวอื่น";
            if(why==null&&world.getPlayers().stream().anyMatch(p->horizontal(p.getLocation(),at)<min*min))why="ใกล้ผู้เล่นเกินไป";
            if(why==null){lastRoll="เจอพื้นในการลองครั้งที่ "+(attempt+1);return at;}
            rejected.merge(why.replaceAll(" \\(.*\\)",""),1,Integer::sum);
        }
        StringBuilder text=new StringBuilder("ไม่พบพื้นใน "+attempts+" จุด:");
        rejected.entrySet().stream().sorted(Map.Entry.<String,Integer>comparingByValue().reversed()).limit(3)
            .forEach(e->text.append(" ").append(e.getKey()).append(" ×").append(e.getValue()));
        lastRoll=text.toString();
        return null;
    }
    /** Admin preview: the nearest standable spot around a target point, or null. */
    private Location nearestSafe(Location target,PetCatalog.Stage stage){
        for(int r=0;r<=6;r++)for(int dx=-r;dx<=r;dx++)for(int dz=-r;dz<=r;dz++){
            if(Math.max(Math.abs(dx),Math.abs(dz))!=r)continue;
            for(int dy:new int[]{0,1,-1,2,-2,3,-3,4,-4}){
                Location at=new Location(target.getWorld(),target.getBlockX()+dx+.5,target.getBlockY()+dy,target.getBlockZ()+dz+.5,target.getYaw(),0);
                if(terrain.safe(at,PetTerrain.radius(stage),PetTerrain.HEADROOM,true))return at;
            }
        }
        return null;
    }
    /** The original 5x5, six-high check; retained for callers that size nothing. */
    public boolean safeLocation(Location at){return safeLocation(at,2,6);}
    /** Reads only already loaded blocks; protects skyland edges and authored encounters. */
    public boolean safeLocation(Location at,int radius,int headroom){return terrain.safe(at,radius,headroom,true);}
    private boolean spaced(Location at){return nearby(at,setting("min-spacing",48,8,256))==0;}
    private boolean withinLocalCaps(Location at) {
        double radius=setting("nearby-radius",96,32,256);int cap=setting("max-nearby-player",2,1,8);
        for(Player player:plugin.world().getPlayers())
            if(player.getLocation().distanceSquared(at)<radius*radius&&nearby(player.getLocation(),radius)>=cap)return false;
        return true;
    }

    /** Also used by the explicit admin preview of a natural encounter. */
    public boolean spawn(PetCatalog.Species species,PetCatalog.Stage stage,Location at){return spawn(species,stage,at,false);}
    private boolean spawn(PetCatalog.Species species,PetCatalog.Stage stage,Location at,boolean preview) {
        if(closed||species==null||species.mythical()||stage==null)return false;
        if(!enabled){lastFailure="ระบบสัตว์ป่าปิดอยู่ (pets.wild-spawns.enabled)";return false;}
        if(!renderer.ready()){lastFailure="โมเดลสัตว์ยังไม่พร้อม: "+renderer.status();return false;}
        if(count()>=setting("max-active-world",16,1,64)){lastFailure="สัตว์ป่าครบจำนวนสูงสุดของโลกแล้ว";return false;}
        String why=terrain.reason(at,PetTerrain.radius(stage),PetTerrain.HEADROOM,true);
        if(why!=null){lastFailure=why;return false;}
        if(!preview&&!spaced(at)){lastFailure="ใกล้สัตว์ป่าตัวอื่นไม่ถึง "+setting("min-spacing",48,8,256)+" บล็อก";return false;}
        if(!preview&&!withinLocalCaps(at)){lastFailure="บริเวณนี้มีสัตว์ป่าครบแล้ว";return false;}
        PetBody body=PetBody.spawn(plugin,renderer,species,stage,at,null,null);
        if(body==null){lastFailure="ดู console: ผูกโมเดลสัตว์เลี้ยงไม่สำเร็จ หรือระบบอื่นกันการเกิด";return false;}
        body.expires=System.currentTimeMillis()+setting("lifetime-seconds",1800,60,7200)*1000L;
        body.deadline=body.expires+LINGER_MS;
        body.modeUntil=clock+20+random.nextInt(40);
        active.put(body.shell.getUniqueId(),body);bodies.add(body,this);bodies.refreshSoon();
        lastFailure="";return true;
    }
    long busy(){return busy;}
    private void tick() {
        long started=System.nanoTime();
        try{update();}finally{busy+=System.nanoTime()-started;}
    }
    /** Every tick: carry each body along its current glide. Every STEP ticks: decide and plan the next one. */
    private void update() {
        clock++;
        for(PetBody pet:active.values())if(!pet.tickGrounding())pet.tickGlide();
        if(clock%STEP!=0)return;
        long now=System.currentTimeMillis();
        double range=setting("despawn-distance",160,64,512);range*=range;
        List<Player> players=plugin.world().getPlayers();
        for(PetBody pet:new ArrayList<>(active.values())) {
            Location at=pet.location();double nearest=Double.MAX_VALUE;Player closest=null;
            if(at.getWorld()==plugin.world())for(Player p:players){
                if(p.isDead()||p.getGameMode()==GameMode.SPECTATOR)continue;
                double distance=p.getLocation().distanceSquared(at);if(distance<nearest){nearest=distance;closest=p;}
            }
            // Someone feeding the pet keeps it in the world while they win its trust.
            if(pet.tamer!=null&&now<pet.tamerUntil){pet.expires=Math.max(pet.expires,now+120_000);pet.deadline=Math.max(pet.deadline,pet.expires);}
            if(!enabled||closed||!renderer.ready()||!pet.valid()
                ||at.getWorld()!=plugin.world()||at.getY()<=at.getWorld().getMinHeight()+1||nearest>range){remove(pet);continue;}
            if(now>=pet.expires){
                // Time is up, but it never blinks out in front of someone: it waits until they wander off.
                if(nearest<=WATCHED&&now<pet.deadline)pet.expires=now+15_000;
                else{depart(pet);remove(pet);continue;}
            }
            // Nobody close enough to watch: hold the pose and send nothing at all.
            if(nearest>ACTIVE_RANGE)continue;
            try{behave(pet,closest,nearest);present(pet);}
            catch(RuntimeException|LinkageError error){remove(pet);plugin.getLogger().log(Level.WARNING,"Cannot update wild pet",error);}
        }
    }

    // ---- Natural behaviour: stand, wander, rest, greet; flyers also take off, cruise, hover and land.
    private void behave(PetBody pet,Player closest,double nearest) {
        if(pet.falling)return;
        boolean flyer=flies(pet.species);
        Player tamer=pet.tamer==null||System.currentTimeMillis()>=pet.tamerUntil?null:Bukkit.getPlayer(pet.tamer);
        boolean befriending=tamer!=null&&!tamer.isDead()&&tamer.getGameMode()!=GameMode.SPECTATOR&&tamer.getWorld()==pet.shell.getWorld();
        if(befriending){
            // Being befriended: stay put, land, and attend to the player holding food.
            if(!pet.airborne&&(pet.mode!=PetBody.Mode.GREET||clock>=pet.modeUntil))set(pet,PetBody.Mode.IDLE,40);
        }else{
            if(closest!=null&&nearest<6*6&&pet.shell.hasLineOfSight(closest)&&!pet.airborne&&clock>=pet.greetReady&&(pet.mode==PetBody.Mode.IDLE||pet.mode==PetBody.Mode.WALK))greet(pet,closest,false);
            // Favourite food held nearby coaxes a flyer down.
            if(closest!=null&&pet.airborne&&nearest<8*8&&taming!=null&&taming.tempted(pet,closest))set(pet,PetBody.Mode.LAND,20*20);
            if(clock>=pet.modeUntil)decide(pet,flyer);
        }
        Player look=pet.watch(befriending?tamer:null,closest,8);
        boolean moving=pet.mode!=PetBody.Mode.HOVER&&(pet.airborne||!befriending&&pet.mode==PetBody.Mode.WALK||pet.speed>.03);
        float before=pet.yaw;
        // Track a live observer throughout greet, idle and rest, including the greet cooldown.
        // During a stride the head glances towards them while the body follows its path.
        pet.steer(look,moving,STEP,pet.mode==PetBody.Mode.HOVER?15f:pet.airborne?9f:25f);
        if(befriending){
            if(pet.airborne)land(pet);else coast(pet);
        }else{
            switch(pet.mode){
                case WALK -> walk(pet);
                case FLY -> fly(pet,true);
                case HOVER -> fly(pet,false);
                case LAND -> land(pet);
                default -> coast(pet);
            }
        }
        // Flyers bank into the same smooth body turn used for travel and attention.
        float bank=pet.airborne?Math.clamp(-wrap(pet.yaw-before)*1.6f,-22f,22f):0;
        pet.roll+=(bank-pet.roll)*.35f;
        if(!pet.airborne)pet.pitch*=.5f;
    }
    private void decide(PetBody pet,boolean flyer) {
        PetBody.Mode previous=pet.mode;
        if(pet.airborne){
            int aloft=clock-pet.airborneSince;
            double roll=random.nextDouble();
            if(aloft>20*30||roll<.2&&aloft>20*6)set(pet,PetBody.Mode.LAND,20*20);
            else if(roll<.5)set(pet,PetBody.Mode.HOVER,40+random.nextInt(60));
            else{heading(pet,AIR_LEASH);set(pet,PetBody.Mode.FLY,60+random.nextInt(100));}
            return;
        }
        if(previous==PetBody.Mode.REST||previous==PetBody.Mode.GREET||previous==PetBody.Mode.WALK){set(pet,PetBody.Mode.IDLE,40+random.nextInt(80));return;}
        double roll=random.nextDouble();
        if(flyer&&roll<.3){
            pet.airborne=true;pet.airborneSince=clock;pet.hover=2.5+random.nextDouble()*2;
            heading(pet,AIR_LEASH);set(pet,PetBody.Mode.FLY,80+random.nextInt(120));
        }else if(roll<(flyer?.45:.2))set(pet,PetBody.Mode.REST,160+random.nextInt(200));
        else if(roll<(flyer?.75:.7)){heading(pet,GROUND_LEASH);set(pet,PetBody.Mode.WALK,40+random.nextInt(80));}
        else set(pet,PetBody.Mode.IDLE,40+random.nextInt(80));
    }
    private void set(PetBody pet,PetBody.Mode mode,int ticks){pet.mode=mode;pet.modeUntil=clock+ticks;}
    private void heading(PetBody pet,double leash) {
        Location at=pet.location();double dx=pet.home.getX()-at.getX(),dz=pet.home.getZ()-at.getZ();
        // Wander freely, but drift back once the pet strays from where it appeared.
        pet.targetYaw=dx*dx+dz*dz>leash*.6?yawTowards(dx,dz)+(random.nextFloat()-.5f)*60:random.nextFloat()*360-180;
    }
    void greet(PetBody pet,Player player,boolean clicked) {
        if(pet.airborne)return;
        pet.notice(player);
        Location at=pet.location();
        pet.targetYaw=yawTowards(player.getX()-at.getX(),player.getZ()-at.getZ());
        set(pet,PetBody.Mode.GREET,48);pet.greetReady=clock+20*(clicked?5:30);
    }
    /** Speeds up over a few steps rather than lurching into a full stride. */
    private void walk(PetBody pet) {
        pet.speed=Math.min(WALK_STEP,pet.speed+WALK_STEP*.35);
        pet.speed*=PetAttention.turnStride(pet.targetYaw-pet.yaw);
        if(!stride(pet,pet.speed,true)){pet.speed=0;heading(pet,0);set(pet,PetBody.Mode.IDLE,20+random.nextInt(40));}
    }
    /** Slows to a halt over a few steps instead of freezing mid-stride. */
    private void coast(PetBody pet){
        if(pet.airborne||pet.speed<.02){pet.speed=0;return;}
        pet.speed*=.45;
        if(!stride(pet,pet.speed,false))pet.speed=0;
    }
    private boolean stride(PetBody pet,double distance,boolean leashed){
        if(distance<.005)return true;
        Location at=pet.destination();double radians=Math.toRadians(pet.yaw);
        Location next=at.clone().add(-Math.sin(radians)*distance,0,Math.cos(radians)*distance);
        Location stand=null;
        // Step up or down one block; skyland terrain is rarely flat.
        for(int dy:new int[]{0,1,-1}){
            Location candidate=next.clone();candidate.setY(at.getBlockY()+dy);
            if(safeLocation(candidate,PetTerrain.radius(pet.stage),PetTerrain.HEADROOM)){stand=candidate;break;}
        }
        if(stand==null||leashed&&horizontal(stand,pet.home)>GROUND_LEASH&&horizontal(stand,pet.home)>horizontal(at,pet.home))return false;
        pet.glide(stand,STEP);return true;
    }
    private void fly(PetBody pet,boolean cruise) {
        Location at=pet.destination();Location next=at.clone();
        pet.speed+=((cruise?FLY_STEP:FLY_STEP*.12)-pet.speed)*.3;
        // Cruising drifts in gentle curves instead of ruler-straight legs.
        if(cruise)pet.targetYaw=wrap(pet.targetYaw+(random.nextFloat()-.5f)*16);
        double radians=Math.toRadians(pet.yaw);next.add(-Math.sin(radians)*pet.speed,0,Math.cos(radians)*pet.speed);
        double ground=PetTerrain.groundBelow(next,12);
        // Never drift over open void or too far from home: turn back instead.
        if(Double.isNaN(ground)||cruise&&horizontal(next,pet.home)>AIR_LEASH&&horizontal(next,pet.home)>horizontal(at,pet.home)){
            heading(pet,0);next=at.clone();ground=PetTerrain.groundBelow(at,12);pet.speed*=.5;
            if(Double.isNaN(ground)){set(pet,PetBody.Mode.LAND,20*20);return;}
        }
        double bob=pet.mode==PetBody.Mode.HOVER?Math.sin(clock/12.0)*.25:Math.sin(clock/9.0)*.08;
        double dy=Math.clamp(ground+pet.hover+bob-at.getY(),-.2,.2);
        next.setY(at.getY()+dy);
        // The nose follows the climb or dive.
        pet.pitch+=((float)Math.clamp(dy*70,-18,18)-pet.pitch)*.4f;
        if(terrain.airClear(next,pet.stage,true))pet.glide(next,STEP);
        else{pet.targetYaw=wrap(pet.yaw+150+random.nextFloat()*60);pet.speed*=.5;}
    }
    private void land(PetBody pet) {
        Location at=pet.destination();double ground=PetTerrain.groundBelow(at,24);
        if(Double.isNaN(ground)){heading(pet,0);set(pet,PetBody.Mode.FLY,40);fly(pet,true);return;}
        pet.speed*=.6;pet.pitch+=(-12-pet.pitch)*.4f;
        Location next=at.clone();next.setY(Math.max(ground,at.getY()-.25));
        if(next.getY()-ground>.05){if(terrain.airClear(next,pet.stage,true))pet.glide(next,STEP);return;}
        if(!safeLocation(next,PetTerrain.radius(pet.stage),PetTerrain.HEADROOM)){
            // Leaves or a ledge below: touch down on the nearest good ground instead of hanging in the air.
            Location spot=nearestSafe(next,pet.stage);
            if(spot==null||spot.distanceSquared(next)>4*4){heading(pet,0);set(pet,PetBody.Mode.FLY,40);return;}
            next=spot;
        }
        pet.glide(next,STEP);pet.airborne=false;pet.speed=0;pet.pitch=0;set(pet,PetBody.Mode.IDLE,40+random.nextInt(60));
    }
    /** Sends the same clip to Java displays and, when it changes, to the Bedrock entity. */
    private void present(PetBody pet) {
        if(pet.falling){pet.present("idle");return;}
        String clip=switch(pet.mode){
            case WALK->pet.speed>.02?"walk":"idle";case REST->"sit";case GREET->"greet";
            case FLY,HOVER,LAND->"fly";default->pet.airborne?"fly":pet.speed>.03?"walk":"idle";
        };
        // Legs keep pace with the ground speed; wings beat slower while hovering.
        float pace=clip.equals("walk")?(float)Math.max(.6,pet.speed/WALK_STEP)
            :clip.equals("fly")?(pet.mode==PetBody.Mode.HOVER?.8f:1f+(float)(pet.speed/FLY_STEP)*.2f):1;
        pet.present(clip,pace);
    }
    private static double horizontal(Location a,Location b){double dx=a.getX()-b.getX(),dz=a.getZ()-b.getZ();return dx*dx+dz*dz;}
    static float yawTowards(double dx,double dz){return (float)Math.toDegrees(Math.atan2(-dx,dz));}
    static float wrap(float degrees){return PetAttention.wrap(degrees);}

    private void remove(PetBody pet){active.remove(pet.shell.getUniqueId());bodies.remove(pet);}
    /** A last sparkle where a pet leaves the world, for anyone still in sight of it. */
    private static void depart(PetBody pet){
        Location at=pet.location().add(0,.7,0);
        at.getWorld().spawnParticle(Particle.CLOUD,at,10,.35,.3,.35,.02);
        at.getWorld().spawnParticle(Particle.END_ROD,at,12,.3,.5,.3,.05);
        at.getWorld().playSound(at,Sound.BLOCK_AMETHYST_BLOCK_RESONATE,SoundCategory.NEUTRAL,.8f,1.6f);
    }
    /** A befriended pet leaves the wild registry with its parts intact. */
    void release(PetBody pet){active.remove(pet.shell.getUniqueId());}
    public int clear(){int count=active.size();for(PetBody pet:new ArrayList<>(active.values()))remove(pet);return count;}
    @Override public void interact(PetBody body,Player player){
        if(taming!=null&&taming.feed(body,player))return;
        if(body.mode!=PetBody.Mode.GREET)greet(body,player,true);
    }
    @Override public void lost(PetBody body){active.remove(body.shell.getUniqueId());}

    private boolean admin(CommandSender sender){return sender.isOp()||sender.hasPermission("evergarden.admin")||sender.hasPermission("voidscape.admin");}
    public boolean command(CommandSender sender,String[] args) {
        if(!admin(sender)){plugin.message(sender,"คำสั่งสัตว์เลี้ยงป่าสำหรับแอดมินเท่านั้น");return true;}
        String action=args.length>2?args[2].toLowerCase(Locale.ROOT):"status";
        if(action.equals("status")){
            plugin.message(sender,status());
            if(sender instanceof Player player)plugin.message(sender,status(player));
            for(Player other:plugin.world().getPlayers())if(other!=sender)plugin.message(sender,other.getName()+": "+rolls.getOrDefault(other.getUniqueId(),"ยังไม่มีผลสุ่ม"));
            return true;
        }
        if(action.equals("clear")){plugin.message(sender,"ล้างสัตว์เลี้ยงป่าแล้ว "+clear()+" ตัว");return true;}
        if(action.equals("spawn")&&args.length==5) {
            var species=PetCatalog.Species.find(args[3]);var stage=PetCatalog.Stage.find(args[4]);
            if(species==null||species.mythical()){plugin.message(sender,"เลือกสัตว์เลี้ยงป่า "+SPECIES.size()+" ชนิด · ยังไม่เปิดระดับตำนาน");return true;}
            if(stage==null){plugin.message(sender,"ระบุวัย baby · medium · adult");return true;}
            if(!(sender instanceof Player player)||player.getWorld()!=plugin.world()){plugin.message(sender,"ใช้คำสั่งนี้ในโลก Evergarden");return true;}
            if(!JudgePackService.bedrock(player))plugin.judgePack().offer(player);
            Vector forward=player.getLocation().getDirection().setY(0);if(forward.lengthSquared()<.001)forward=new Vector(0,0,1);else forward.normalize();
            Location target=player.getLocation().add(forward.multiply(6));target.setY(Math.floor(groundLevel(player)));
            Location at=nearestSafe(target,stage);
            if(at==null){
                String why=terrain.reason(target,PetTerrain.radius(stage),PetTerrain.HEADROOM,true);
                plugin.message(sender,"ยังเสกไม่ได้ · ไม่มีพื้นที่เหมาะในระยะ 6 บล็อก · จุดด้านหน้า: "+(why==null?"ใช้ได้":why));return true;
            }
            plugin.message(sender,spawn(species,stage,at,true)?"เสก "+species.display+" · "+stage.thai:"ยังเสกไม่ได้ · "+lastFailure);return true;
        }
        plugin.message(sender,"/evergarden pets wild status|spawn <id> <baby|medium|adult>|clear");return true;
    }
    public List<String> complete(CommandSender sender,String[] args) {
        if(!admin(sender))return List.of();List<String> values=new ArrayList<>();
        if(args.length==3)values.addAll(List.of("status","spawn","clear"));
        if(args.length==4&&args[2].equalsIgnoreCase("spawn"))SPECIES.forEach(s->values.add(s.id));
        if(args.length==5&&args[2].equalsIgnoreCase("spawn"))for(var stage:PetCatalog.Stage.values())values.add(stage.id);
        String prefix=args[args.length-1].toLowerCase(Locale.ROOT);return values.stream().filter(v->v.startsWith(prefix)).toList();
    }
    @Override public void close(){closed=true;enabled=false;if(attempts!=null)attempts.cancel();if(maintenance!=null)maintenance.cancel();clear();}
}
