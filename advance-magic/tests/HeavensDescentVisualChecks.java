import com.example.advancemagic.AdvanceMagicPlugin;
import com.example.advancemagic.elemental.ElementalSpell;
import com.example.advancemagic.elemental.StaffSpellService.Tier;
import com.example.advancemagic.item.ElementalStaffService.Element;
import com.example.advancemagic.spell.ElementalVfx;
import com.mojang.authlib.GameProfile;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.embedded.EmbeddedChannel;
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
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import java.nio.file.*;
import java.util.*;

/** Real Paper scene lifecycle and the actual Legendary light scroll cast. */
public final class HeavensDescentVisualChecks extends JavaPlugin implements Listener {
    static final ElementalSpell SPELL=ElementalSpell.HEAVENS_DESCENT;
    static final String PREFIX="elemental_legendary_light_";
    static final class Capture extends Connection {
        int particles;
        final Map<Integer,Integer> particlesPerTick=new TreeMap<>();
        Capture(){super(PacketFlow.SERVERBOUND);channel=new EmbeddedChannel();address=new java.net.InetSocketAddress("127.0.0.1",1);}
        void capture(Packet<?> packet){if(packet instanceof ClientboundLevelParticlesPacket){particles++;particlesPerTick.merge(Bukkit.getCurrentTick(),1,Integer::sum);}}
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
    Map<UUID,String> applied;
    int passed,damageEvents,simulatedTick,firstDamageTick=-1;
    LivingEntity target;
    final List<ElementalVfx> renderers=new ArrayList<>();

    void check(boolean ok,String label){if(!ok)throw new AssertionError(label);passed++;getLogger().info("PASS "+label);}
    @Override public void onEnable(){
        Bukkit.getPluginManager().registerEvents(this,this);
        Bukkit.getScheduler().runTaskLater(this,()->{try{run();finish(null);}catch(Throwable error){finish(error);}},20);
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void damaged(EntityDamageByEntityEvent event){
        if(target!=null&&event.getEntity().getUniqueId().equals(target.getUniqueId())){damageEvents++;if(firstDamageTick<0)firstDamageTick=simulatedTick;}
    }
    @SuppressWarnings("unchecked") void run() throws Exception {
        magic=(AdvanceMagicPlugin)Bukkit.getPluginManager().getPlugin("advance-magic");
        check(magic!=null&&magic.isEnabled(),"production plugin boots on real Paper");
        world=Bukkit.getWorlds().getFirst();world.setPVP(true);
        for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)world.getChunkAt(x,z).setForceLoaded(true);
        for(int x=-24;x<=24;x++)for(int z=-24;z<=24;z++)world.getBlockAt(x,99,z).setType(Material.STONE);
        var server=MinecraftServer.getServer();var profile=new GameProfile(UUID.randomUUID(),"HeavensProbe");
        actor=new ServerPlayer(server,((CraftWorld)world).getHandle(),profile,ClientInformation.createDefault());capture=new Capture();
        actor.connection=new ServerGamePacketListenerImpl(server,capture,actor,CommonListenerCookie.createInitial(profile,false)){
            @Override public boolean hasClientLoaded(){return true;}
        };
        actor.setPos(.5,100,.5);server.getPlayerList().getPlayers().add(actor);server.getPlayerList().getPlayersByUUID().put(profile.id(),actor);
        ((CraftWorld)world).getHandle().addNewPlayer(actor);caster=actor.getBukkitEntity();caster.setOp(true);caster.setGravity(false);
        caster.getAttribute(Attribute.MAX_HEALTH).setBaseValue(1000);caster.setHealth(500);caster.setGameMode(GameMode.SURVIVAL);
        var status=magic.packs().getClass().getDeclaredField("statuses");status.setAccessible(true);applied=(Map<UUID,String>)status.get(magic.packs());
        applied.put(caster.getUniqueId(),"SUCCESSFULLY_LOADED");
        magic.getConfig().set("elemental-visuals.style","native");magic.getConfig().set("elemental-visuals.quality","balanced");
        magic.getConfig().set("elemental-visuals.legendary-light.enabled",true);
        magic.getConfig().set("elemental-visuals.legendary-light.afterglow-ticks",170);
        check(SPELL.rarity()==ElementalSpell.Rarity.LEGENDARY&&SPELL.element()==Element.LIGHT,"test remains the existing Legendary light scroll");
        lifecycle();interruptions();qualityAndFallback();budgets();castLifecycle();
    }
    ElementalVfx renderer(){var value=new ElementalVfx(magic.context());renderers.add(value);return value;}
    Location stage(){return new Location(world,.5,100,4.5);}
    List<ItemDisplay> displays(){return new ArrayList<>(world.getEntitiesByClass(ItemDisplay.class));}
    Set<String> models(){
        Set<String> result=new HashSet<>();
        for(var display:displays()){var meta=display.getItemStack().getItemMeta();if(meta!=null&&meta.getItemModel()!=null)result.add(meta.getItemModel().getKey());}
        return result;
    }
    String model(ItemDisplay display){var meta=display.getItemStack().getItemMeta();return meta!=null&&meta.getItemModel()!=null?meta.getItemModel().getKey():"";}
    boolean circular(ItemDisplay display){return Set.of(PREFIX+"sigil",PREFIX+"halo").contains(model(display));}
    org.joml.Vector3f axis(ItemDisplay display,float x,float y,float z){
        var transform=display.getTransformation();
        return new org.joml.Vector3f(x,y,z).rotate(transform.getRightRotation()).rotate(transform.getLeftRotation()).normalize();
    }
    void horizontalCircles(){
        for(var display:displays())if(circular(display)){
            var normal=axis(display,0,0,1);
            if(Math.abs(normal.y)<.9999||Math.abs(normal.x)>.001||Math.abs(normal.z)>.001)
                throw new AssertionError("standing or tilted rune plane: "+model(display)+" at "+display.getLocation()+" normal="+normal);
        }
    }
    void separatedCircles(String phase){
        var sigils=displays().stream().filter(d->model(d).equals(PREFIX+"sigil")).toList();
        double floor=sigils.stream().mapToDouble(d->d.getLocation().getY()).min().orElseThrow();
        double sky=sigils.stream().mapToDouble(d->d.getLocation().getY()).max().orElseThrow();
        check(sky-floor>=31&&sky-floor<=33,phase+" has a 32-block floor-to-sky gap");
        horizontalCircles();check(true,phase+" keeps every sigil and halo horizontal");
    }
    double heading(ItemDisplay display){var direction=axis(display,1,0,0);return Math.atan2(direction.z,direction.x);}
    double delta(double end,double start){return Math.atan2(Math.sin(end-start),Math.cos(end-start));}
    List<ItemDisplay> orbitals(){return displays().stream().filter(d->circular(d)&&d.getLocation().getY()>stage().getY()+1&&d.getLocation().getY()<stage().getY()+28).toList();}
    org.bukkit.util.Vector endpoint(ItemDisplay display,boolean end){
        var direction=axis(display,0,1,0);double half=display.getTransformation().getScale().y*.5*(end?1:-1);
        return display.getLocation().toVector().add(new org.bukkit.util.Vector(direction.x,direction.y,direction.z).multiply(half));
    }
    ItemDisplay beacon(){return displays().stream().filter(d->model(d).equals(PREFIX+"beacon")).findFirst().orElseThrow(()->new AssertionError("missing dedicated beacon shaft"));}
    void beaconGeometry(String phase){
        var shaft=beacon();var from=endpoint(shaft,false);var to=endpoint(shaft,true);
        check(from.getY()>stage().getY()+31&&to.getY()<stage().getY()+1&&from.getY()>to.getY()
                &&Math.hypot(from.getX()-stage().getX(),from.getZ()-stage().getZ())<.01
                &&Math.hypot(to.getX()-stage().getX(),to.getZ()-stage().getZ())<.01,
                phase+" preserves a visible straight sky-to-floor descending beacon shaft");
    }
    void finite(){for(var display:displays()){
        var position=display.getLocation();var transform=display.getTransformation();
        if(!Double.isFinite(position.getX()+position.getY()+position.getZ())||!transform.getScale().isFinite()
                ||!transform.getLeftRotation().isFinite()||display.isPersistent())throw new AssertionError("invalid or persistent display");
    }horizontalCircles();}
    int advance(ElementalVfx renderer,int ticks){int peak=displays().size();for(int i=0;i<ticks;i++){renderer.tick();finite();peak=Math.max(peak,displays().size());}return peak;}
    void clearRenderers(){for(var value:renderers)value.close();renderers.clear();}
    void lifecycle(){
        int combat=magic.effects().size();double mana=magic.mana().account(caster).manaExact();
        var renderer=renderer();var scene=renderer.begin(caster,SPELL);int peak=0;
        scene.startup();advance(renderer,4);
        check(displays().stream().anyMatch(this::circular),"startup shows horizontal circular artwork before the charge");
        horizontalCircles();check(true,"startup has no standing rune plane");
        for(int age=0;age<=30;age++){scene.telegraph(stage(),7,age,30);peak=Math.max(peak,advance(renderer,1));}
        check(models().contains(PREFIX+"sigil")&&models().contains(PREFIX+"halo")&&!models().contains(PREFIX+"mist"),"charge preserves the authored seals without painted giant mist columns");
        separatedCircles("charge");
        scene.clear("telegraph");scene.pillar(stage(),12);
        double firstBottom=endpoint(beacon(),true).getY();advance(renderer,4);double nextBottom=endpoint(beacon(),true).getY();
        check(firstBottom>stage().getY()+20&&nextBottom<firstBottom-2,"the beacon visibly starts at the sky and extends downward before reaching the floor");
        peak=Math.max(peak,advance(renderer,12));
        check(displays().size()>=9&&displays().size()<=12&&peak<=12,"balanced impact has a dedicated beacon and finite display planes within the twelve-plane cap");
        check(orbitals().size()==4,"four small horizontal helper seals occupy the air around the beacon");
        beaconGeometry("impact");
        separatedCircles("impact");
        check(displays().stream().filter(this::circular).allMatch(d->d.getViewRange()>=1.5f),"circle displays retain a 96-block client view range for the elevated ceiling");
        check(displays().stream().filter(this::circular).allMatch(d->d.getInterpolationDuration()>0&&d.getInterpolationDuration()<4),
                "native client rotation interpolation completes before the next four-tick pose update");
        var rotating=displays().stream().filter(d->model(d).equals(PREFIX+"sigil")).min(Comparator.comparingDouble(d->d.getLocation().getY())).orElseThrow();
        var counter=displays().stream().filter(d->model(d).equals(PREFIX+"halo")).min(Comparator.comparingDouble(d->d.getLocation().getY())).orElseThrow();
        UUID id=rotating.getUniqueId(),counterId=counter.getUniqueId();
        double start=heading(rotating),counterStart=heading(counter),last=start,counterLast=counterStart,turn=0,counterTurn=0;
        for(int i=0;i<10;i++){
            advance(renderer,4);double now=heading(rotating),counterNow=heading(counter);
            double step=delta(now,last),counterStep=delta(counterNow,counterLast);
            if(Math.abs(step)<.015||Math.abs(counterStep)<.015||step*counterStep>=0)throw new AssertionError("circles fail coherent visible counter-rotation on tick "+(i*4));
            turn+=step;counterTurn+=counterStep;last=now;counterLast=counterNow;
        }
        check(rotating.isValid()&&counter.isValid()&&rotating.getUniqueId().equals(id)&&counter.getUniqueId().equals(counterId)
                &&Math.abs(turn)>.5&&Math.abs(counterTurn)>.5&&turn*counterTurn<0,
                "existing floor sigil and halo visibly counter-rotate by over half a radian in two seconds");
        int emissions=0;Set<Integer> sourceHeights=new HashSet<>();List<org.bukkit.util.Vector> previous=new ArrayList<>();
        for(int age=0;age<192;age+=4){
            var shots=displays().stream().filter(d->model(d).equals(PREFIX+"shot")).toList();
            for(var shot:shots){
                var from=endpoint(shot,false);var to=endpoint(shot,true);
                if(orbitals().stream().noneMatch(d->d.getLocation().toVector().distance(from)<2.5))throw new AssertionError("cosmetic shot is detached from its moving helper seal: "+from);
                if(to.getY()>stage().getY()+1||from.distance(to)<10||Math.hypot(to.getX()-stage().getX(),to.getZ()-stage().getZ())<15)
                    throw new AssertionError("cosmetic shot does not visibly travel outward and down to the test field");
                if(previous.stream().noneMatch(p->p.distance(from)<.01)){emissions++;sourceHeights.add((int)Math.round(from.getY()*10));}
            }
            previous=shots.stream().map(d->endpoint(d,false)).toList();
            advance(renderer,4);
        }
        check(emissions>=8&&sourceHeights.size()==4,"all four helper seals repeatedly discharge visible anchored cosmetic beams");
        beaconGeometry("sustained volleys");
        check(!models().contains(PREFIX+"mist")&&capture.particles>0,"sustained air sparkle uses native particle packets without giant mist display entities");
        check(capture.particlesPerTick.values().stream().allMatch(n->n<=64),"native Java light flecks obey the shared sixty-four-particle-packet per viewer real-tick budget");
        List<LivingEntity> crowded=new ArrayList<>();
        try{
            for(int i=0;i<40;i++){
                var mob=world.spawn(stage(),Vindicator.class);mob.setAI(false);mob.setGravity(false);crowded.add(mob);
                scene.status(mob,"radiance",1,80,()->true);scene.impact(mob);
            }
            advance(renderer,12);
            check(displays().size()<=12&&models().contains(PREFIX+"sigil")&&models().contains(PREFIX+"beacon"),"crowded status and impact clips cannot evict the long-lived beacon stage or exceed its plane budget");
        }finally{for(var mob:crowded)mob.remove();}
        advance(renderer,48);
        check(!displays().isEmpty()&&scene.enabled()&&models().contains(PREFIX+"sigil"),"afterglow remains visible at fifteen seconds despite the legacy 170-tick setting");
        advance(renderer,260);
        check(!displays().isEmpty()&&scene.enabled(),"thirty-second afterglow remains visible beyond twenty-eight seconds");
        advance(renderer,100);
        check(displays().isEmpty()&&!scene.enabled(),"afterglow expires and closes every display without an explicit close");
        check(magic.effects().size()==combat&&magic.mana().account(caster).manaExact()==mana,"visual scene adds no combat effect slots or mana charges");
        clearRenderers();
    }
    void interruptions(){
        var renderer=renderer();var scene=renderer.begin(caster,SPELL);scene.telegraph(stage(),7,24,30);
        check(!displays().isEmpty(),"charge interruption begins with visible artwork");
        scene.clear("telegraph");advance(renderer,20);
        check(displays().isEmpty()&&!scene.enabled(),"cancelled charge leaves no afterglow or orphan displays");
        scene=renderer.begin(caster,SPELL);scene.pillar(stage(),12);renderer.closeOwner(caster.getUniqueId());
        check(displays().isEmpty()&&!scene.enabled(),"owner cleanup removes an in-flight afterglow immediately");
        scene=renderer.begin(caster,SPELL);scene.pillar(stage(),12);renderer.close();
        check(displays().isEmpty()&&!scene.enabled(),"renderer shutdown removes all owned artwork");clearRenderers();
    }
    void qualityAndFallback(){
        var renderer=renderer();var scene=renderer.begin(caster,SPELL);scene.pillar(stage(),12);
        check(displays().size()>=9&&displays().size()<=12,"balanced baseline for quality change");magic.getConfig().set("elemental-visuals.quality","low");advance(renderer,16);
        check(displays().size()<=7&&models().contains(PREFIX+"sigil")&&models().contains(PREFIX+"beacon")
                &&!models().contains(PREFIX+"mist")&&orbitals().size()==2,"live low quality keeps the beacon and two helper seals within seven planes");
        beaconGeometry("low quality");
        boolean lowShot=false;int lowPeak=0;for(int i=0;i<96;i++){lowPeak=Math.max(lowPeak,advance(renderer,1));lowShot|=models().contains(PREFIX+"shot");}
        check(lowShot&&lowPeak<=7,"low quality retains occasional helper-beam discharges within its seven-plane cap");
        scene.close();check(displays().isEmpty(),"low quality scene cleanup");renderer.close();
        magic.getConfig().set("elemental-visuals.quality","balanced");
        magic.getConfig().set("elemental-visuals.legendary-light.enabled",false);
        renderer=renderer();scene=renderer.begin(caster,SPELL);scene.pillar(stage(),12);advance(renderer,8);
        check(displays().isEmpty(),"disabled experimental light setting restores native presentation");renderer.close();
        magic.getConfig().set("elemental-visuals.legendary-light.enabled",true);applied.remove(caster.getUniqueId());int before=capture.particles;
        renderer=renderer();scene=renderer.begin(caster,SPELL);scene.pillar(stage(),12);advance(renderer,12);
        check(displays().isEmpty()&&capture.particles>before,"Java viewer without the pack receives particles without missing-texture displays");
        renderer.close();applied.put(caster.getUniqueId(),"SUCCESSFULLY_LOADED");clearRenderers();
    }
    void budgets(){
        magic.getConfig().set("elemental-visuals.max-planes-per-world",16);var renderer=renderer();
        for(int i=0;i<12;i++)renderer.begin(caster,SPELL).pillar(stage().add(i*.05,0,0),12);
        int peak=advance(renderer,16);check(peak<=16&&models().contains(PREFIX+"sigil"),"many simultaneous scenes obey the shared sixteen-plane world cap");
        renderer.closeOwner(caster.getUniqueId());check(displays().isEmpty(),"world-cap stress cleanup removes every scene");
        clearRenderers();magic.getConfig().set("elemental-visuals.max-planes-per-world",128);
    }
    void prepareStaff(){
        magic.elemental().close();magic.effects().close();magic.statuses().close();
        caster.getInventory().clear();caster.getInventory().setHeldItemSlot(0);caster.teleport(new Location(world,.5,100,.5,0,24));caster.setHealth(500);
        caster.getInventory().setItemInMainHand(magic.staffSpells().createStaff(Element.LIGHT,Tier.MYTHIC));
        caster.getInventory().setItem(1,magic.staffSpells().createScroll(SPELL));
        check(magic.staffSpells().imprint(caster,0,caster.getInventory().getItemInMainHand().clone(),1,caster.getInventory().getItem(1).clone(),0).success(),"real Legendary scroll imprints in a light staff");
        var account=magic.mana().account(caster);account.setMaxMana(100);account.setMana(100);account.restore("elemental_"+SPELL.id(),0);
        for(String name:List.of("elemental_gcd","elemental_ultimate","elemental_regen_after"))caster.getPersistentDataContainer().remove(magic.mana().key(name));
        damageEvents=0;firstDamageTick=-1;simulatedTick=0;
    }
    int combatTicks(int count){int peak=displays().size();for(int i=0;i<count;i++){simulatedTick++;magic.effects().tick();magic.elemental().tick();finite();peak=Math.max(peak,displays().size());}return peak;}
    void castLifecycle(){
        prepareStaff();target=world.spawn(stage(),Vindicator.class);target.setAI(false);target.setGravity(false);target.getAttribute(Attribute.MAX_HEALTH).setBaseValue(1000);target.setHealth(1000);target.setMaximumNoDamageTicks(0);
        check(magic.elemental().cast(caster),"actual staff cast starts the light Legendary");
        check(magic.mana().account(caster).manaExact()==100-SPELL.mana(),"cast consumes one existing Legendary mana cost");
        int peak=combatTicks(24);check(target.getHealth()==1000&&damageEvents==0&&!displays().isEmpty(),"charge is visible before any combat impact");
        peak=Math.max(peak,combatTicks(16));
        check(damageEvents==1&&firstDamageTick>=30&&target.getHealth()<1000&&caster.getHealth()>500,"impact applies one enemy hit and allied healing on the completed charge");
        check(magic.effects().size()==0&&!displays().isEmpty(),"cosmetic afterglow continues after combat channel closes");
        double health=target.getHealth(),allyHealth=caster.getHealth(),mana=magic.mana().account(caster).manaExact();peak=Math.max(peak,combatTicks(300));
        check(peak<=12&&damageEvents==1&&target.getHealth()==health&&caster.getHealth()==allyHealth
                &&magic.mana().account(caster).manaExact()==mana,"cosmetic orbital volleys stay within twelve planes without repeating enemy damage, allied healing or mana charges");
        check(!displays().isEmpty(),"the actual staff cast keeps its circular scene visible for over fifteen seconds");
        peak=Math.max(peak,combatTicks(360));
        check(displays().isEmpty()&&magic.effects().size()==0,"full actual cast expires with no leaked displays or combat effects");
        target.remove();target=null;
        prepareStaff();check(magic.elemental().cast(caster),"movement cancellation cast begins");combatTicks(5);caster.teleport(caster.getLocation().add(3,0,0));combatTicks(230);
        check(displays().isEmpty()&&magic.effects().size()==0&&damageEvents==0,"moving away cancels the paid charge without a detached afterglow");
        prepareStaff();check(magic.elemental().cast(caster),"world change cleanup cast begins");combatTicks(40);
        Bukkit.getPluginManager().callEvent(new PlayerChangedWorldEvent(caster,world));
        check(displays().isEmpty()&&magic.effects().size()==0,"production world-change listener removes the active afterglow");
        prepareStaff();check(magic.elemental().cast(caster),"quit cleanup cast begins");combatTicks(40);
        Bukkit.getPluginManager().callEvent(new PlayerQuitEvent(caster,""));
        check(displays().isEmpty()&&magic.effects().size()==0,"production quit listener removes the active afterglow");
    }
    void finish(Throwable error){
        if(error!=null)getLogger().log(java.util.logging.Level.SEVERE,"HEAVENS DESCENT VISUAL FAILED after "+passed,error);
        clearRenderers();if(magic!=null){magic.elemental().close();magic.effects().close();}if(target!=null)target.remove();
        if(actor!=null){actor.getBukkitEntity().setOp(false);MinecraftServer.getServer().getPlayerList().getPlayers().remove(actor);MinecraftServer.getServer().getPlayerList().getPlayersByUUID().remove(actor.getUUID());actor.discard();}
        try{Files.writeString(Path.of("heavens-descent-visual-result.txt"),(error==null?"PASS "+passed:"FAIL after "+passed+": "+error)+"\n");}catch(Exception ignored){}
        Bukkit.getScheduler().runTaskLater(this,Bukkit::shutdown,2);
    }
}
