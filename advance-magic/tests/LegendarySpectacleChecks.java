import com.example.advancemagic.AdvanceMagicPlugin;
import com.example.advancemagic.elemental.ElementalSpell;
import com.example.advancemagic.elemental.StaffSpellService.Tier;
import com.example.advancemagic.item.ElementalStaffService.Element;
import com.mojang.authlib.GameProfile;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.plugin.java.JavaPlugin;
import java.nio.file.*;
import java.util.*;

/**
 * Real Paper casts of the Meteor Rain and Tempest Dominion spectacles from an actual staff.
 * Records every display pose, particle packet and damage tick, then checks choreography
 * against combat, budgets and cleanup. The JSON capture drives the offline preview.
 */
public class LegendarySpectacleChecks extends JavaPlugin implements Listener {
    static final class Capture extends Connection {
        final List<String> tick=new ArrayList<>();
        final List<Object[]> raw=new ArrayList<>();
        Capture(){super(PacketFlow.SERVERBOUND);channel=new EmbeddedChannel();address=new java.net.InetSocketAddress("127.0.0.1",1);}
        // Paper 26.3 made this packet a record; support its accessors and the cached 26.2 compiler API.
        static Object sample(ClientboundLevelParticlesPacket packet,String current,String legacy){
            try{
                java.lang.reflect.Method method;
                try{method=packet.getClass().getMethod(current);}catch(NoSuchMethodException old){method=packet.getClass().getMethod(legacy);}
                return method.invoke(packet);
            }catch(ReflectiveOperationException failure){throw new AssertionError("Cannot capture particle packet",failure);}
        }
        void capture(Packet<?> packet){
            if(packet instanceof ClientboundLevelParticlesPacket p)
                raw.add(new Object[]{String.valueOf(BuiltInRegistries.PARTICLE_TYPE.getKey(((net.minecraft.core.particles.ParticleOptions)sample(p,"particle","getParticle")).getType())),sample(p,"x","getX"),sample(p,"y","getY"),sample(p,"z","getZ"),sample(p,"count","getCount"),sample(p,"xDist","getXDist"),sample(p,"yDist","getYDist"),sample(p,"zDist","getZDist"),sample(p,"xMaxSpeed","getMaxSpeed")});
        }
        @Override public void send(Packet<?> packet){capture(packet);}
        @Override public void send(Packet<?> packet,ChannelFutureListener listener){capture(packet);}
        @Override public void send(Packet<?> packet,ChannelFutureListener listener,boolean flush){capture(packet);}
        @Override public boolean isConnected(){return true;}
    }
    AdvanceMagicPlugin magic;
    World world;
    Player caster;
    ServerPlayer actor;
    Capture capture;
    int passed,simulatedTick;
    final List<LivingEntity> targets=new ArrayList<>();
    final TreeSet<Integer> damageTicks=new TreeSet<>();
    final Map<Integer,Integer> strikeTicks=new TreeMap<>();final Set<UUID> strikes=new HashSet<>();

    void check(boolean ok,String label){if(!ok)throw new AssertionError(label);passed++;getLogger().info("PASS "+label);}
    @Override public void onEnable(){
        Bukkit.getPluginManager().registerEvents(this,this);
        Bukkit.getScheduler().runTaskLater(this,()->{try{run();finish(null);}catch(Throwable error){finish(error);}},20);
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void damaged(EntityDamageByEntityEvent event){
        if(targets.stream().anyMatch(t->t.getUniqueId().equals(event.getEntity().getUniqueId())))damageTicks.add(simulatedTick);
    }
    @SuppressWarnings("unchecked") void run() throws Exception {
        magic=(AdvanceMagicPlugin)Bukkit.getPluginManager().getPlugin("advance-magic");
        check(magic!=null&&magic.isEnabled(),"production plugin boots on real Paper");
        world=Bukkit.getWorlds().getFirst();world.setPVP(true);
        for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++)world.getChunkAt(x,z).setForceLoaded(true);
        for(int x=-30;x<=30;x++)for(int z=-30;z<=30;z++)world.getBlockAt(x,99,z).setType(Material.STONE);
        var server=MinecraftServer.getServer();var profile=new GameProfile(UUID.randomUUID(),"SpectacleProbe");
        actor=new ServerPlayer(server,((CraftWorld)world).getHandle(),profile,ClientInformation.createDefault());capture=new Capture();
        actor.connection=new ServerGamePacketListenerImpl(server,capture,actor,CommonListenerCookie.createInitial(profile,false)){
            @Override public boolean hasClientLoaded(){return true;}
        };
        actor.setPos(.5,100,.5);server.getPlayerList().getPlayers().add(actor);server.getPlayerList().getPlayersByUUID().put(profile.id(),actor);
        ((CraftWorld)world).getHandle().addNewPlayer(actor);caster=actor.getBukkitEntity();caster.setOp(true);caster.setGravity(false);
        caster.getAttribute(Attribute.MAX_HEALTH).setBaseValue(1000);caster.setHealth(500);caster.setGameMode(GameMode.SURVIVAL);
        var status=magic.packs().getClass().getDeclaredField("statuses");status.setAccessible(true);
        ((Map<UUID,String>)status.get(magic.packs())).put(caster.getUniqueId(),"SUCCESSFULLY_LOADED");
        magic.getConfig().set("elemental-visuals.style","native");magic.getConfig().set("elemental-visuals.quality","balanced");
        meteorRain();tempest();leviathan();absoluteZero();titans();abyssal();raijin();heavensLance();lowQuality();
    }
    Location stage(){return new Location(world,.5,100,6.5);}
    List<ItemDisplay> displays(){return new ArrayList<>(world.getEntitiesByClass(ItemDisplay.class));}
    String model(ItemDisplay display){
        var item=display.getItemStack();var meta=item.getItemMeta();
        return meta!=null&&meta.getItemModel()!=null?meta.getItemModel().getKey():"block:"+item.getType().getKey().getKey();
    }
    void prepare(Element element,ElementalSpell spell){
        magic.elemental().close();magic.effects().close();magic.statuses().close();
        for(var t:targets)t.remove();targets.clear();damageTicks.clear();capture.raw.clear();strikes.clear();strikeTicks.clear();
        caster.getInventory().clear();caster.getInventory().setHeldItemSlot(0);
        // Look down at the stage so ground-targeted spells land on it.
        Location eye=new Location(world,.5,100,.5);eye.setDirection(stage().toVector().subtract(eye.clone().add(0,1.62,0).toVector()));caster.teleport(eye);caster.setHealth(500);
        caster.getInventory().setItemInMainHand(magic.staffSpells().createStaff(element,Tier.MYTHIC));
        caster.getInventory().setItem(1,magic.staffSpells().createScroll(spell));
        check(magic.staffSpells().imprint(caster,0,caster.getInventory().getItemInMainHand().clone(),1,caster.getInventory().getItem(1).clone(),0).success(),spell.id()+" imprints in a Mythic staff");
        var account=magic.mana().account(caster);account.setMaxMana(200);account.setMana(200);account.restore("elemental_"+spell.id(),0);
        for(String name:List.of("elemental_gcd","elemental_ultimate","elemental_regen_after"))caster.getPersistentDataContainer().remove(magic.mana().key(name));
        simulatedTick=0;
    }
    LivingEntity target(Location at){
        var mob=world.spawn(at,Vindicator.class);mob.setAI(false);mob.setGravity(false);mob.getAttribute(Attribute.MAX_HEALTH).setBaseValue(1000);mob.setHealth(1000);mob.setMaximumNoDamageTicks(0);
        targets.add(mob);return mob;
    }
    record Frame(int tick,List<Map<String,Object>> displays,List<Object[]> particles){}
    /** Advance real combat and visuals, recording every display and particle packet per tick. */
    List<Frame> record(int ticks,int[] peak,int[] particlePeak){
        List<Frame> frames=new ArrayList<>();
        for(int i=0;i<ticks;i++){
            simulatedTick++;capture.raw.clear();
            magic.effects().tick();magic.elemental().tick();
            List<Map<String,Object>> poses=new ArrayList<>();
            for(var d:displays()){
                var loc=d.getLocation();var t=d.getTransformation();
                if(!Double.isFinite(loc.getX()+loc.getY()+loc.getZ())||!t.getScale().isFinite()||!t.getLeftRotation().isFinite()||d.isPersistent())
                    throw new AssertionError("invalid or persistent display "+model(d));
                var q=t.getLeftRotation();var s=t.getScale();
                poses.add(Map.of("model",model(d),"pos",List.of(loc.getX(),loc.getY(),loc.getZ()),"scale",List.of(s.x,s.y,s.z),"rot",List.of(q.x,q.y,q.z,q.w),"ticks",d.getInterpolationDuration()));
            }
            for(var stand:world.getEntitiesByClass(ArmorStand.class)){
                var loc=stand.getLocation();var helmet=stand.getEquipment().getHelmet();
                double scale=stand.getAttribute(Attribute.SCALE).getBaseValue();
                if(stand.isPersistent()||stand.isVisible()||!stand.isMarker())throw new AssertionError("Bedrock carrier must be a hidden, non-persistent marker");
                poses.add(Map.of("model","carrier:"+helmet.getType().getKey().getKey(),"pos",List.of(loc.getX(),loc.getY(),loc.getZ()),"scale",List.of(scale,scale,scale),"rot",List.of(0f,0f,0f,1f)));
            }
            for(var strike:world.getEntitiesByClass(LightningStrike.class))if(strikes.add(strike.getUniqueId()))strikeTicks.merge(simulatedTick,1,Integer::sum);
            int count=capture.raw.stream().mapToInt(p->Math.max(1,(int)p[4])).sum();
            peak[0]=Math.max(peak[0],(int)poses.stream().filter(d->!((String)d.get("model")).startsWith("carrier:")).count());particlePeak[0]=Math.max(particlePeak[0],count);
            Frame frame=new Frame(simulatedTick,poses,new ArrayList<>(capture.raw));frames.add(frame);frameCaptured(frame);
        }
        return frames;
    }
    void frameCaptured(Frame frame){}
    void write(String name,List<Frame> frames,Location stage)throws Exception{
        StringBuilder json=new StringBuilder("{\"stage\":["+stage.getX()+","+stage.getY()+","+stage.getZ()+"],\"damage\":"+damageTicks+",\"frames\":[");
        for(int i=0;i<frames.size();i++){
            var f=frames.get(i);if(i>0)json.append(',');
            json.append("{\"tick\":").append(f.tick).append(",\"displays\":[");
            for(int j=0;j<f.displays.size();j++){var d=f.displays.get(j);if(j>0)json.append(',');
                json.append("{\"model\":\"").append(d.get("model")).append("\",\"pos\":").append(d.get("pos")).append(",\"scale\":").append(d.get("scale")).append(",\"rot\":").append(d.get("rot")).append('}');}
            json.append("],\"particles\":[");
            for(int j=0;j<f.particles.size();j++){var p=f.particles.get(j);if(j>0)json.append(',');
                json.append("[\"").append(p[0]).append('"');for(int k=1;k<p.length;k++)json.append(',').append(p[k]);json.append(']');}
            json.append("]}");
        }
        Files.writeString(Path.of(name),json.append("]}\n").toString());
    }
    List<Object[]> particlesNamed(Frame f,String type){return f.particles.stream().filter(p->p[0].equals(type)).toList();}
    double flat(Object[] p,Location at){return Math.hypot((double)p[1]-at.getX(),(double)p[3]-at.getZ());}

    void meteorRain()throws Exception{
        prepare(Element.FIRE,ElementalSpell.METEOR_RAIN);
        Location center=stage();target(center);
        for(int i=0;i<6;i++){double a=i*Math.PI/3;target(center.clone().add(Math.cos(a)*2.8,0,Math.sin(a)*2.8));}
        check(magic.elemental().cast(caster),"the actual Meteor Rain scroll cast starts");
        int[] peak={0},particles={0};
        var frames=record(240,peak,particles);
        write("meteor-rain-capture.json",frames,center);
        // The six small hits and the giant hit, in combat ticks.
        // Burn damage-over-time adds further ticks; the impacts follow the combat schedule.
        int first=damageTicks.first();
        List<Integer> hits=new ArrayList<>();for(int i=0;i<6;i++)hits.add(first+18*i);hits.add(first+110);
        check(damageTicks.containsAll(hits),"six small meteor hits eighteen ticks apart and the giant twenty after: "+hits+" within "+damageTicks);
        // Each visual impact lands on the same tick and spot as its damage.
        for(int i=0;i<6;i++){
            int tick=hits.get(i);double a=i*Math.PI/3;Location landing=center.clone().add(Math.cos(a)*2.8,0,Math.sin(a)*2.8);
            var frame=frames.get(tick-1);
            check(particlesNamed(frame,"minecraft:explosion").stream().anyMatch(p->flat(p,landing)<.6),"small meteor "+i+" explodes on its damage tick at its landing point");
            var before=frames.get(tick-3);
            check(before.displays.stream().anyMatch(d->d.get("model").equals("block:magma_block")&&Math.hypot((double)((List<?>)d.get("pos")).get(0)-landing.getX(),(double)((List<?>)d.get("pos")).get(2)-landing.getZ())<4.5),
                "a tumbling 3D magma rock is closing on landing "+i+" just before impact");
        }
        var giant=frames.get(hits.get(6)-1);
        check(particlesNamed(giant,"minecraft:explosion_emitter").stream().anyMatch(p->flat(p,center)<.6),"the giant meteor detonates on its damage tick at the centre");
        check(frames.get(hits.get(6)-30).displays.stream().filter(d->d.get("model").equals("block:magma_block")).anyMatch(d->(double)((List<?>)d.get("pos")).get(1)>center.getY()+12),
            "the giant meteor hangs high in the sky before it falls");
        check(frames.get(hits.get(6)-30).displays.stream().filter(d->d.get("model").equals("elemental_legendary_fire_sigil")).mapToDouble(d->(double)((List<?>)d.get("pos")).get(1)).max().orElse(0)>center.getY()+20,
            "a burning sky gate spins high above the field");
        check(peak[0]<=32,"display pieces stay within the 32-piece spectacle cap (peak "+peak[0]+")");
        check(particles[0]<=450,"particles per viewer tick stay within the 450 budget (peak "+particles[0]+")");
        int total=frames.stream().mapToInt(f->f.particles.stream().mapToInt(p->Math.max(1,(int)p[4])).sum()).sum();
        check(total>6000,"Meteor Rain is a dense particle spectacle ("+total+" particles)");
        check(displays().isEmpty()&&world.getEntitiesByClass(ArmorStand.class).isEmpty()&&magic.effects().size()==0,"Meteor Rain leaves no displays, carriers or combat effects behind");
        parity("Meteor Rain",frames);
        getLogger().info("METEOR peak displays "+peak[0]+" peak particles/tick "+particles[0]+" total "+total);
    }
    void tempest()throws Exception{
        prepare(Element.WIND,ElementalSpell.TEMPEST_DOMINION);
        Location center=caster.getLocation();target(center.clone().add(3,0,0));
        check(magic.elemental().cast(caster),"the actual Tempest Dominion scroll cast starts");
        int[] peak={0},particles={0};
        var frames=record(170,peak,particles);
        write("tempest-dominion-capture.json",frames,center);
        List<Integer> hits=new ArrayList<>(damageTicks);
        check(hits.size()==6,"six tempest damage pulses: "+hits);
        for(int hit:hits)check(particlesNamed(frames.get(hit-1),"minecraft:sweep_attack").size()>=10,"wind blades flash on damage pulse "+hit);
        var funnel=frames.get(hits.get(0)+39);
        var sigils=funnel.displays.stream().filter(d->d.get("model").equals("elemental_legendary_wind_sigil")).map(d->(double)((List<?>)d.get("pos")).get(1)).sorted().toList();
        check(sigils.size()>=8&&sigils.getLast()-sigils.getFirst()>14,"a tall funnel of stacked spinning circles stands mid-cast ("+sigils.size()+" circles)");
        double widest=funnel.displays.stream().filter(d->d.get("model").equals("elemental_legendary_wind_sigil")&&(double)((List<?>)d.get("pos")).get(1)>center.getY()+10)
            .mapToDouble(d->((Number)((List<?>)d.get("scale")).get(0)).doubleValue()).max().orElse(0);
        double narrowest=funnel.displays.stream().filter(d->d.get("model").equals("elemental_legendary_wind_sigil")&&(double)((List<?>)d.get("pos")).get(1)>center.getY()+1&&(double)((List<?>)d.get("pos")).get(1)<center.getY()+3)
            .mapToDouble(d->((Number)((List<?>)d.get("scale")).get(0)).doubleValue()).min().orElse(99);
        check(widest>narrowest*2,"the funnel widens with height like a tornado ("+narrowest+" to "+widest+")");
        int burst=hits.get(0)+116;
        check(frames.subList(burst-3,burst+2).stream().anyMatch(f->!particlesNamed(f,"minecraft:gust_emitter_large").isEmpty()),"the funnel bursts with large gusts at the finale");
        check(blocks(funnel,"oak_log")+blocks(funnel,"dirt")+blocks(funnel,"cobblestone")+blocks(funnel,"grass_block")>=3,"3D debris torn from the ground rides the funnel");
        check(peak[0]<=32,"display pieces stay within the 32-piece spectacle cap (peak "+peak[0]+")");
        check(particles[0]<=450,"particles per viewer tick stay within the 450 budget (peak "+particles[0]+")");
        int total=frames.stream().mapToInt(f->f.particles.stream().mapToInt(p->Math.max(1,(int)p[4])).sum()).sum();
        check(total>4000,"Tempest Dominion is a dense particle spectacle ("+total+" particles)");
        check(displays().isEmpty()&&world.getEntitiesByClass(ArmorStand.class).isEmpty()&&magic.effects().size()==0,"Tempest Dominion leaves no displays, carriers or combat effects behind");
        parity("Tempest Dominion",frames);
        getLogger().info("TEMPEST peak displays "+peak[0]+" peak particles/tick "+particles[0]+" total "+total);
    }
    int total(List<Frame> frames){return frames.stream().mapToInt(f->f.particles.stream().mapToInt(p->Math.max(1,(int)p[4])).sum()).sum();}
    long blocks(Frame f,String material){return f.displays.stream().filter(d->d.get("model").equals("block:"+material)).count();}
    double num(Map<String,Object> d,String key,int i){return ((Number)((List<?>)d.get(key)).get(i)).doubleValue();}
    /** Java geometry stays solid; native Bedrock actor parity is checked by LegendaryBlockBedrockChecks. */
    void parity(String name,List<Frame> frames){
        long props=frames.stream().flatMap(frame->frame.displays.stream()).filter(d->((String)d.get("model")).startsWith("block:")).count();
        check(props>0&&world.getEntitiesByClass(ArmorStand.class).isEmpty(),name+": solid Java props never create unsupported block helmets");
    }
    void budgets(String name,List<Frame> frames,int[] peak,int[] particles,int minimum){
        check(peak[0]<=32,name+" display pieces stay within the 32-piece cap (peak "+peak[0]+")");
        check(particles[0]<=450,name+" particles per viewer tick stay within the 450 budget (peak "+particles[0]+")");
        int total=total(frames);
        check(total>minimum,name+" is a dense particle spectacle ("+total+" particles)");
        check(displays().isEmpty()&&world.getEntitiesByClass(ArmorStand.class).isEmpty()&&magic.effects().size()==0,name+" leaves no displays, carriers or combat effects behind");
        parity(name,frames);
        getLogger().info("SPECTACLE "+name+" peak displays "+peak[0]+" peak particles/tick "+particles[0]+" total "+total);
    }
    void leviathan()throws Exception{
        prepare(Element.WATER,ElementalSpell.LEVIATHANS_WRATH);
        Location start=caster.getLocation();for(int i=0;i<3;i++)target(start.clone().add(0,0,6+i*5));
        check(magic.elemental().cast(caster),"the actual Leviathan's Wrath scroll cast starts");
        int[] peak={0},particles={0};var frames=record(170,peak,particles);write("leviathans-wrath-capture.json",frames,start);
        check(damageTicks.size()>=5,"waves reach the targets on their path: "+damageTicks);
        for(int wave=0;wave<5;wave++){
            var mid=frames.get(wave*24+12);
            check(blocks(mid,"prismarine_bricks")>=6&&blocks(mid,"dark_prismarine")>=1&&blocks(mid,"sea_lantern")>=2,"a 3D prismarine leviathan rides wave "+wave);
            check(mid.displays.stream().anyMatch(d->d.get("model").equals("block:dark_prismarine")&&num(d,"pos",1)>start.getY()+4),"the leviathan's head breaches above the crest of wave "+wave);
            check(!particlesNamed(mid,"minecraft:splash").isEmpty(),"wave "+wave+" throws spray");
        }
        check(blocks(frames.get(60),"dark_prismarine")==2&&blocks(frames.get(60),"sea_lantern")==4,"a guardian serpent coils round the caster while the leviathan rides the wave");
        budgets("Leviathan's Wrath",frames,peak,particles,4000);
    }
    void absoluteZero()throws Exception{
        prepare(Element.ICE,ElementalSpell.ABSOLUTE_ZERO);
        Location center=stage();target(center);target(center.clone().add(3,0,0));
        check(magic.elemental().cast(caster),"the actual Absolute Zero scroll cast starts");
        int[] peak={0},particles={0};var frames=record(140,peak,particles);write("absolute-zero-capture.json",frames,center);
        int first=damageTicks.first();
        check(damageTicks.containsAll(List.of(first,first+20,first+60)),"frost hits land at 0, 20 and 60: "+damageTicks);
        var standing=frames.get(first+40);
        check(blocks(standing,"packed_ice")+blocks(standing,"blue_ice")+blocks(standing,"ice")>=14,"a field of 3D ice spires stands before the shatter");
        check(blocks(standing,"blue_ice")>=2,"the central blue-ice spire has grown on the freeze");
        var shatter=frames.get(first+59);
        check(particlesNamed(shatter,"minecraft:block").stream().mapToInt(p->(int)p[4]).sum()>=150,"the shatter throws ice shards on its damage tick");
        check(blocks(standing,"blue_ice")>=8&&standing.displays.stream().anyMatch(d->d.get("model").equals("block:blue_ice")&&num(d,"pos",1)>center.getY()+9),"a crown of blue-ice crystals hangs high over the field");
        check(frames.get(first+64).displays.stream().filter(d->((String)d.get("model")).startsWith("block:")&&((String)d.get("model")).endsWith("ice")).allMatch(d->d.get("model").equals("block:blue_ice")),"every spire is gone after the shatter; only the falling crown remains");
        check(frames.get(first+76).displays.stream().noneMatch(d->((String)d.get("model")).endsWith("ice")),"the crown crystals have fallen and burst");
        budgets("Absolute Zero",frames,peak,particles,3000);
    }
    void titans()throws Exception{
        prepare(Element.EARTH,ElementalSpell.TITANS_JUDGMENT);
        Location center=stage();target(center);
        check(magic.elemental().cast(caster),"the actual Titan's Judgment scroll cast starts");
        int[] peak={0},particles={0};var frames=record(110,peak,particles);write("titans-judgment-capture.json",frames,center);
        int slam=damageTicks.first();
        check(particlesNamed(frames.get(slam-1),"minecraft:explosion_emitter").stream().anyMatch(p->flat(p,center)<.6),"the slam erupts on its damage tick at the centre");
        var sky=frames.get(slam-16);
        check(sky.displays.stream().anyMatch(d->d.get("model").equals("block:gold_block")&&num(d,"pos",1)>center.getY()+15),"the golden-capped Pillar of Judgment hangs high above during the charge");
        var after=frames.get(slam+10);
        check(after.displays.stream().anyMatch(d->d.get("model").equals("block:deepslate_bricks")&&num(d,"pos",1)<center.getY()+1.5),"the pillar is driven into the ground by the slam");
        check(after.displays.stream().filter(d->Set.of("block:stone","block:cobblestone","block:granite","block:andesite").contains(d.get("model"))).count()>=4,"3D boulders are flung by the slam");
        check(after.displays.stream().filter(d->Set.of("block:deepslate","block:tuff").contains(d.get("model"))).count()>=8,"a ring of stone spikes bursts from the ground");
        check(frames.get(slam+6).displays.stream().filter(d->Set.of("block:dirt","block:coarse_dirt","block:gravel").contains(d.get("model"))).count()>=4,"the ground heaves in a rolling ring of earth blocks");
        budgets("Titan's Judgment",frames,peak,particles,1500);
    }
    void abyssal()throws Exception{
        prepare(Element.DARK,ElementalSpell.ABYSSAL_COLLAPSE);
        Location center=stage();target(center);
        check(magic.elemental().cast(caster),"the actual Abyssal Collapse scroll cast starts");
        int[] peak={0},particles={0};var frames=record(150,peak,particles);write("abyssal-collapse-capture.json",frames,center);
        int first=damageTicks.first();
        check(damageTicks.contains(first+80),"the implosion hits eighty ticks after the binding: "+damageTicks);
        check(blocks(frames.get(first+40),"crying_obsidian")>=1&&blocks(frames.get(first+40),"obsidian")>=2,"a 3D singularity core and debris hang over the field");
        check(!particlesNamed(frames.get(first+79),"minecraft:sonic_boom").isEmpty(),"the singularity detonates with sonic booms on the implosion tick");
        check(blocks(frames.get(first+82),"crying_obsidian")==0,"the core is gone after the implosion");
        check(blocks(frames.get(first+40),"amethyst_block")==4,"four void tentacles reach towards the core");
        budgets("Abyssal Collapse",frames,peak,particles,3000);
    }
    void raijin()throws Exception{
        prepare(Element.LIGHTNING,ElementalSpell.WRATH_OF_RAIJIN);
        Location center=stage();target(center);
        check(magic.elemental().cast(caster),"the actual Wrath of Raijin scroll cast starts");
        int[] peak={0},particles={0};var frames=record(170,peak,particles);write("wrath-of-raijin-capture.json",frames,center);
        int first=damageTicks.first();
        for(int k=0;k<6;k++)check(damageTicks.contains(first+20*k)&&strikeTicks.getOrDefault(first+20*k,0)>=1,"strike "+k+" lands a lightning bolt on its damage tick");
        check(strikeTicks.getOrDefault(first+100,0)==3,"the great bolt calls three strikes at once");
        check(blocks(frames.get(first+30),"barrel")==6&&blocks(frames.get(first+30),"gold_block")==2,"a 3D ring of six thunder drums and the gold-headed thunder dragon circle the sky");
        check(blocks(frames.get(first+60),"sea_lantern")>=5,"the thunder dragon's glowing body coils through the storm");
        check(blocks(frames.get(first+104),"sea_lantern")==0,"the dragon is gone after diving with the great bolt");
        budgets("Wrath of Raijin",frames,peak,particles,3000);
    }
    /** Heaven's Descent: the golden beam cannon is charged, fired, steered with the camera and closed with a blast. */
    void heavensLance()throws Exception{
        prepare(Element.LIGHT,ElementalSpell.HEAVENS_DESCENT);
        Location eye=new Location(world,.5,100,.5,0,0);caster.teleport(eye);
        var ahead=target(new Location(world,.5,100,12.5));var far=target(new Location(world,.5,100,30.5));var side=target(new Location(world,14.5,100,8.5));
        check(magic.elemental().cast(caster),"the actual Heaven's Descent scroll cast starts without aiming at the ground");
        int[] peak={0},particles={0};
        var frames=new ArrayList<Frame>(record(64,peak,particles));
        double aheadBefore=ahead.getHealth(),farBefore=far.getHealth(),sideBefore=side.getHealth();
        // Swing the camera towards the side target; the heavy beam follows at its bounded turn rate.
        Location turn=caster.getLocation();turn.setYaw(-60);caster.teleport(turn);
        frames.addAll(record(116,peak,particles));
        write("heavens-descent-capture.json",frames,new Location(world,.5,100,.5));
        var charge=frames.get(12);
        check(charge.displays.stream().anyMatch(d->d.get("model").equals("judgment_gold_seal"))&&charge.displays.stream().anyMatch(d->d.get("model").equals("judgment_gold_halo")),"the Ancient Judge's gold seal pair opens in front of the caster during the charge");
        check(blocks(charge,"gold_block")==4,"four gold capacitors orbit the charging seal");
        check(frames.subList(0,23).stream().noneMatch(f->f.displays.stream().anyMatch(d->d.get("model").equals("judgment_gold_ray"))),"no beam is fired before the 24-tick charge completes");
        var firing=frames.get(40);
        var beam=firing.displays.stream().filter(d->d.get("model").equals("judgment_gold_ray")).toList();
        check(beam.size()==2&&beam.stream().anyMatch(d->num(d,"scale",1)>25),"a long two-layer golden beam fires straight ahead ("+beam.size()+" layers)");
        check(firing.displays.stream().filter(d->d.get("model").equals("judgment_gold_halo")).count()>=3,"rings of judgment run down the barrel");
        check(!particlesNamed(firing,"minecraft:end_rod").isEmpty()&&!particlesNamed(firing,"minecraft:dust").isEmpty(),"gold light and dust stream along the beam");
        var aheadHits=damageTicks.stream().filter(t->t>=25&&t<64).count();
        check(aheadHits>=6,"pulses every five ticks strike the target in the beam ("+aheadHits+" hits)");
        check(farBefore<1000&&aheadBefore<1000,"the beam reaches thirty blocks and pierces every target on its line");
        check(sideBefore==1000,"the side target is untouched while the beam points ahead");
        check(frames.get(66).displays.stream().filter(d->d.get("model").equals("judgment_gold_ray")).allMatch(d->num(d,"pos",0)<15),"the heavy beam cannot snap to a new aim at once");
        check(frames.get(84).displays.stream().filter(d->d.get("model").equals("judgment_gold_ray")).anyMatch(d->num(d,"pos",0)>17),"within a second the beam has swung to the new aim");
        check(side.getHealth()<1000,"steering the camera swings the beam onto the side target");
        int finale=24+120;
        check(frames.subList(finale-3,finale+3).stream().mapToLong(f->particlesNamed(f,"minecraft:explosion_emitter").size()).sum()==1,"the closing blast detonates once at the last impact point when the beam ends");
        check(frames.get(finale+4).displays.stream().noneMatch(d->d.get("model").equals("judgment_gold_ray")),"the beam has collapsed after the blast");
        budgets("Heaven's Descent",frames,peak,particles,3000);
    }
    void lowQuality()throws Exception{
        magic.getConfig().set("elemental-visuals.quality","low");
        for(var pair:List.of(Map.entry(Element.FIRE,ElementalSpell.METEOR_RAIN),Map.entry(Element.WIND,ElementalSpell.TEMPEST_DOMINION),
                Map.entry(Element.WATER,ElementalSpell.LEVIATHANS_WRATH),Map.entry(Element.ICE,ElementalSpell.ABSOLUTE_ZERO),
                Map.entry(Element.EARTH,ElementalSpell.TITANS_JUDGMENT),Map.entry(Element.DARK,ElementalSpell.ABYSSAL_COLLAPSE),
                Map.entry(Element.LIGHTNING,ElementalSpell.WRATH_OF_RAIJIN),Map.entry(Element.LIGHT,ElementalSpell.HEAVENS_DESCENT))){
            prepare(pair.getKey(),pair.getValue());target(stage());
            check(magic.elemental().cast(caster),pair.getValue().id()+" casts on low quality");
            int[] peak={0},particles={0};record(240,peak,particles);
            check(peak[0]<=10&&particles[0]<=160,pair.getValue().id()+" low quality stays within 10 pieces and 160 particles per tick ("+peak[0]+", "+particles[0]+")");
            check(displays().isEmpty()&&world.getEntitiesByClass(ArmorStand.class).isEmpty(),pair.getValue().id()+" low quality cleanup");
        }
        magic.getConfig().set("elemental-visuals.quality","balanced");
    }
    void finish(Throwable error){
        if(error!=null)getLogger().log(java.util.logging.Level.SEVERE,"LEGENDARY SPECTACLE FAILED after "+passed,error);
        if(magic!=null){magic.elemental().close();magic.effects().close();}
        for(var t:targets)t.remove();
        if(actor!=null){actor.getBukkitEntity().setOp(false);MinecraftServer.getServer().getPlayerList().getPlayers().remove(actor);MinecraftServer.getServer().getPlayerList().getPlayersByUUID().remove(actor.getUUID());actor.discard();}
        try{Files.writeString(Path.of("legendary-spectacle-result.txt"),(error==null?"PASS "+passed:"FAIL after "+passed+": "+error)+"\n");}catch(Exception ignored){}
        Bukkit.getScheduler().runTaskLater(this,Bukkit::shutdown,2);
    }
}
