import com.example.voidscape.VoidscapePlugin;
import com.example.voidscape.boss.JudgePackService;
import com.example.voidscape.pet.*;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import org.bukkit.*;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Slab;
import org.bukkit.entity.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;

/** Reproduces removed footing and model clicks using real entities on disposable Paper. */
public final class PetGroundingChecks extends JavaPlugin {
    VoidscapePlugin garden; World world; Player player;
    net.minecraft.server.level.ServerPlayer actor;
    Object bodies,companions,taming;
    int checks,waited,sequence,actionBars; boolean finished;
    int cx=1536,cz=1536;
    final List<String> lines=new ArrayList<>();
    final List<Throwing> scenarios=new ArrayList<>(); int scenario;
    interface Throwing {void run() throws Exception;}
    void check(boolean ok,String label){if(!ok)throw new AssertionError(label);checks++;lines.add("PASS: "+label);}
    void near(double actual,double expected,String label){check(Math.abs(actual-expected)<.035,label+": "+actual+" expected "+expected);}
    static Object get(Object target,String name)throws Exception{Field f=field(target,name);return f.get(target);}
    static Field field(Object target,String name)throws Exception{for(Class<?> c=target.getClass();c!=null;c=c.getSuperclass())try{Field f=c.getDeclaredField(name);f.setAccessible(true);return f;}catch(NoSuchFieldException ignored){}throw new NoSuchFieldException(name);}
    static void put(Object target,String name,Object value)throws Exception{field(target,name).set(target,value);}
    static Object call(Object target,String name,Object... args)throws Exception{
        outer:for(Method m:target.getClass().getDeclaredMethods())if(m.getName().equals(name)&&m.getParameterCount()==args.length){
            Class<?>[] types=m.getParameterTypes();for(int i=0;i<args.length;i++)if(args[i]!=null&&!types[i].isPrimitive()&&!types[i].isInstance(args[i]))continue outer;
            m.setAccessible(true);return m.invoke(target,args);
        }throw new NoSuchMethodException(name);
    }
    void later(int ticks,Throwing work){Bukkit.getScheduler().runTaskLater(this,()->{try{work.run();}catch(Throwable e){finish(e);}},ticks);}
    @Override public void onEnable(){later(20,()->{
        garden=(VoidscapePlugin)Bukkit.getPluginManager().getPlugin("Evergarden");
        check(garden!=null&&garden.isEnabled()&&garden.pets()!=null,"production pet system boots");
        world=garden.world();awaitReady();
    });}
    void awaitReady()throws Exception{
        if(garden.judgePack().nativePetsIncluded()&&((NativePetRenderer)get(garden.pets(),"renderer")).ready()){setup();next();return;}
        if((waited+=10)>1800)throw new AssertionError("native model resource pack never became ready");
        later(10,this::awaitReady);
    }
    void setup()throws Exception{
        bodies=get(garden.pets(),"bodies");companions=get(garden.pets(),"companions");taming=get(garden.pets(),"taming");
        garden.getConfig().set("pets.wild-spawns.chance-per-player",0.0);
        garden.getConfig().set("pets.wild-spawns.announce",false);
        garden.getConfig().set("seraph-trader.feathers.wild-feed-chance",0.0);
        garden.getConfig().set("pets.taming.feed-cooldown-ms",500);
        garden.wildPets().reload();
        bedrockTargets();
        while(garden.layout().at(cx,cz,12)!=null)cx+=64;
        terrain();
        var server=net.minecraft.server.MinecraftServer.getServer();var level=((org.bukkit.craftbukkit.CraftWorld)world).getHandle();
        var profile=new com.mojang.authlib.GameProfile(UUID.randomUUID(),"PetGroundTest");
        actor=new net.minecraft.server.level.ServerPlayer(server,level,profile,net.minecraft.server.level.ClientInformation.createDefault());
        var connection=new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        connection.channel=new io.netty.channel.embedded.EmbeddedChannel();connection.address=new java.net.InetSocketAddress("127.0.0.1",1);
        actor.connection=new net.minecraft.server.network.ServerGamePacketListenerImpl(server,connection,actor,net.minecraft.server.network.CommonListenerCookie.createInitial(profile,false)){
            @Override public boolean hasClientLoaded(){return true;}
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet){
                if(packet instanceof net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket)actionBars++;
                super.send(packet);
            }
        };
        actor.setPos(cx+.5,180,cz-2.1);server.getPlayerList().getPlayers().add(actor);server.getPlayerList().getPlayersByUUID().put(profile.id(),actor);level.addNewPlayer(actor);
        player=actor.getBukkitEntity();player.setGravity(false);player.setInvulnerable(true);player.setGameMode(GameMode.SURVIVAL);
        Bukkit.getPluginManager().callEvent(new PlayerResourcePackStatusEvent(player,JudgePackService.PACK_ID,PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED));
        for(String mode:List.of("IDLE","REST","GREET","TAMING"))scenarios.add(()->fall(mode,false,false));
        scenarios.add(()->fall("WALK",true,false));
        scenarios.add(()->fall("REST",false,true));
        scenarios.add(this::authoredSteps);
        scenarios.add(this::slabWalk);
        scenarios.add(this::flyer);
        scenarios.add(this::stay);
        scenarios.add(this::summonedFlyer);
        scenarios.add(this::clicks);
    }
    @SuppressWarnings("unchecked") void bedrockTargets()throws Exception{
        var bridge=garden.arkhaiaBedrock();check(bridge!=null,"Bedrock model registry exists without requiring a connected client");
        UUID id=UUID.randomUUID();
        bridge.register(id,PetCatalog.Species.CRYSTAL_STAG.model(PetCatalog.Stage.BABY),.9f,1.05f);
        Object state=((Map<UUID,?>)get(bridge,"actors")).get(id);
        near(((Number)get(state,"interactionWidth")).doubleValue(),.9,"Bedrock pet touch width is retained");
        near(((Number)get(state,"interactionHeight")).doubleValue(),1.05,"Bedrock pet touch height is retained");
        bridge.unregister(id);bridge.register(id,"arkhaia");state=((Map<UUID,?>)get(bridge,"actors")).get(id);
        near(((Number)get(state,"interactionWidth")).doubleValue(),0,"boss sculpture retains its separate receiver width");
        near(((Number)get(state,"interactionHeight")).doubleValue(),0,"boss sculpture retains its separate receiver height");
        bridge.unregister(id);
    }
    void terrain(){
        for(int x=cx-8;x<=cx+8;x++)for(int z=cz-8;z<=cz+8;z++){
            world.getChunkAt(x>>4,z>>4).addPluginChunkTicket(this);
            world.getBlockAt(x,174,z).setType(Material.STONE,false);
            for(int y=175;y<=205;y++)world.getBlockAt(x,y,z).setType(Material.AIR,false);
            world.getBlockAt(x,179,z).setType(Material.STONE,false);
        }
    }
    Location anchor(){return new Location(world,cx+.5,180,cz+.5);}
    @SuppressWarnings("unchecked") Object spawn(PetCatalog.Species species)throws Exception{
        check(garden.wildPets().spawn(species,PetCatalog.Stage.BABY,anchor()),"production wild spawn "+species);
        Map<UUID,Object> active=(Map<UUID,Object>)get(garden.wildPets(),"active");
        Object body=active.values().iterator().next();put(body,"modeUntil",Integer.MAX_VALUE/4);put(body,"greetReady",Integer.MAX_VALUE/4);put(body,"speed",0d);
        // Exercise the same carrier teleport branch when a Geyser bridge is absent in this local server.
        if(get(body,"carrier")==null){
            UUID receiver=shell(body).getUniqueId();
            ArmorStand carrier=world.spawn(anchor(),ArmorStand.class,e->{
                e.getPersistentDataContainer().set(garden.key("pet_body"),PersistentDataType.BYTE,(byte)1);
                e.getPersistentDataContainer().set(garden.key("wild_pet"),PersistentDataType.BYTE,(byte)1);
                e.getPersistentDataContainer().set(garden.key("wild_pet_visual"),PersistentDataType.STRING,receiver.toString());
                e.setGravity(false);e.setInvulnerable(true);e.setVisible(false);e.setVisibleByDefault(false);e.setPersistent(false);
            });
            check(carrier.isValid(),"surrogate Bedrock carrier accepted by production world filter");
            put(body,"carrier",carrier);
        }
        check((boolean)call(body,"valid"),"spawned receiver and carrier are valid before exercise");
        return body;
    }
    @SuppressWarnings({"rawtypes","unchecked"}) void mode(Object body,String mode)throws Exception{put(body,"mode",Enum.valueOf((Class)get(body,"mode").getClass(),mode));}
    Rabbit shell(Object body)throws Exception{return (Rabbit)get(body,"shell");}
    void removePlatform(){for(int x=cx-4;x<=cx+4;x++)for(int z=cz-4;z<=cz+4;z++)world.getBlockAt(x,179,z).setType(Material.AIR,false);}
    void sync(Object body,String label)throws Exception{
        Rabbit shell=shell(body);Location at=shell.getLocation();ArmorStand carrier=(ArmorStand)get(body,"carrier");
        near(carrier.getLocation().getY(),at.getY(),label+" Bedrock carrier feet follow receiver");
        check(carrier.getLocation().distanceSquared(at)<.002,label+" Bedrock carrier keeps position");
        NativePetRenderer.Rig rig=(NativePetRenderer.Rig)get(body,"rig");
        check(!rig.displays().isEmpty(),label+" Java sculpture exists");
        for(ItemDisplay display:rig.displays()){
            check(display.isValid(),label+" Java part remains valid");
            check(rig.riding()?display.getVehicle()==shell:display.getLocation().distanceSquared(at)<.002,label+" Java part remains attached/follows");
            check(Math.abs(display.getLocation().getY()-at.getY())<1,label+" Java part tracks fall on server");
        }
        if(get(body,"nametag") instanceof TextDisplay name)check(name.getVehicle()==shell,label+" nametag stays attached");
    }
    void next(){if(scenario==scenarios.size()){finish(null);return;}try{garden.wildPets().clear();terrain();scenarios.get(scenario++).run();}catch(Throwable e){finish(e);}}
    void fall(String chosen,boolean glide,boolean slab)throws Exception{
        Object body=spawn(PetCatalog.Species.CRYSTAL_STAG);Rabbit receiver=shell(body);
        player.teleport(anchor().add(0,0,-2.6));player.getInventory().clear();
        mode(body,chosen.equals("TAMING")?"IDLE":chosen);
        if(chosen.equals("TAMING")){put(body,"tamer",player.getUniqueId());put(body,"tamerUntil",System.currentTimeMillis()+60_000L);put(body,"trust",25);}
        if(glide)call(body,"glide",anchor().add(.4,0,0),40);
        if(slab){Slab lower=(Slab)Bukkit.createBlockData(Material.STONE_SLAB);lower.setType(Slab.Type.BOTTOM);for(int x=cx-4;x<=cx+4;x++)for(int z=cz-4;z<=cz+4;z++)world.getBlockAt(x,175,z).setBlockData(lower,false);}
        removePlatform();double floor=slab?175.5:175;
        watchFall(body,receiver.getLocation(),floor,0,chosen+(glide?" interrupted stride":"")+(slab?" slab":""),()->{garden.wildPets().clear();later(1,this::next);});
    }
    void watchFall(Object body,Location previous,double floor,int tick,String label,Throwing done){later(1,()->{
        Location at=shell(body).getLocation();check(at.getY()>=floor-.035,label+" never passes through floor tick "+tick);
        check(at.getY()<=previous.getY()+.035,label+" does not jump back to stale glide tick "+tick);
        check(previous.getY()-at.getY()<1.8,label+" falls smoothly tick "+tick);
        if(tick==5)check(at.getY()<179.3,label+" promptly drops after support removed; actualY="+at.getY()+", falling="+get(body,"falling")+", fallSpeed="+get(body,"fallSpeed"));
        if(tick==24){near(at.getY(),floor,label+" lands on collision surface");sync(body,label);}
        if(tick>=34){near(at.getY(),floor,label+" remains settled");check(!(boolean)get(body,"falling"),label+" exits falling state");done.run();}
        else watchFall(body,at,floor,tick+1,label,done);
    });}
    void authoredSteps()throws Exception{
        Object body=spawn(PetCatalog.Species.CRYSTAL_STAG);player.teleport(anchor().add(0,0,-2.6));
        check(!shell(body).getPassengers().isEmpty(),"native receiver carries real sculpture passengers");
        Location before=shell(body).getLocation();List<Entity> passengers=shell(body).getPassengers();
        boolean accepted=shell(body).teleport(before.clone().add(.02,0,0));
        String control="CONTROL default Paper teleport accepted="+accepted+", passengers before="+passengers.size()+", after="+shell(body).getPassengers().size()+", dx="+(shell(body).getLocation().getX()-before.getX());
        lines.add(control);getLogger().info(control);
        // API behavior varies by Paper version; retain the observation and restore the fixture.
        for(Entity passenger:passengers)if(passenger.getVehicle()!=shell(body))shell(body).addPassenger(passenger);
        call(body,"move",before);sync(body,"retained passenger production movement");
        Location up=anchor().add(1,1,0);world.getBlockAt(cx+1,180,cz).setType(Material.STONE,false);
        call(body,"glide",up,5);
        later(6,()->{
            near(shell(body).getLocation().getY(),181,"authored one-block step up completes");
            near(shell(body).getLocation().getX(),cx+1.5,"authored step up reaches its supported endpoint");
            check(!(boolean)get(body,"falling"),"supported upward stride is not treated as falling");sync(body,"authored step up");
            call(body,"glide",anchor(),5);
            later(6,()->{
                near(shell(body).getLocation().getY(),180,"authored one-block step down completes");
                near(shell(body).getLocation().getX(),cx+.5,"authored step down reaches supported endpoint");
                check(!(boolean)get(body,"falling"),"supported downward stride is not interrupted");sync(body,"authored step down");
                garden.wildPets().clear();next();
            });
        });
    }
    void slabWalk()throws Exception{
        Object body=spawn(PetCatalog.Species.CRYSTAL_STAG);player.teleport(anchor().add(0,0,-2.6));
        Slab lower=(Slab)Bukkit.createBlockData(Material.STONE_SLAB);lower.setType(Slab.Type.BOTTOM);
        for(int x=cx-4;x<=cx+4;x++)for(int z=cz-4;z<=cz+4;z++)world.getBlockAt(x,179,z).setBlockData(lower,false);
        call(body,"move",anchor().add(0,-.5,0));call(body,"glide",anchor().add(1,0,0),5);
        near(((Location)call(body,"destination")).getY(),179.5,"slab stride endpoint follows actual collision height");
        later(6,()->{
            near(shell(body).getLocation().getY(),179.5,"walking along bottom slabs keeps feet on slab top");
            near(shell(body).getLocation().getX(),cx+1.5,"slab stride completes horizontal movement");
            check(!(boolean)get(body,"falling"),"bottom-slab movement does not alternate into falling");sync(body,"slab walk");
            garden.wildPets().clear();next();
        });
    }
    void flyer()throws Exception{
        Object body=spawn(PetCatalog.Species.FROST_WYVERN);mode(body,"HOVER");put(body,"airborne",true);put(body,"hover",3d);put(body,"home",anchor());
        call(body,"move",anchor().add(0,3,0));player.teleport(anchor().add(0,0,-12));player.getInventory().clear();removePlatform();
        later(20,()->{check(shell(body).getLocation().getY()>181,"intentional hover remains airborne after support removal");check(!(boolean)get(body,"falling"),"intentional flyer never enters ground fall");sync(body,"intentional flyer");garden.wildPets().clear();next();});
    }
    void stay()throws Exception{
        Object body=spawn(PetCatalog.Species.CRYSTAL_STAG);player.teleport(anchor().add(0,0,-2.6));
        put(body,"tamer",player.getUniqueId());put(body,"tamerUntil",System.currentTimeMillis()+60_000L);put(body,"trust",100);
        call(taming,"feed",body,player);check(player.getUniqueId().equals(get(body,"owner")),"production taming adopts same body for stay test");
        put(get(body,"record"),"mode","stay");put(body,"speed",0d);call(body,"move",anchor());removePlatform();
        watchFall(body,anchor(),175,0,"companion stay",()->{call(companions,"dismiss",player);later(1,this::next);});
    }
    void summonedFlyer()throws Exception{
        Object body=spawn(PetCatalog.Species.FROST_WYVERN);player.teleport(anchor().add(0,0,-2.6));
        put(body,"tamer",player.getUniqueId());put(body,"tamerUntil",System.currentTimeMillis()+60_000L);put(body,"trust",100);
        call(taming,"feed",body,player);Object record=get(body,"record");UUID id=((PetRecord)record).id();
        check(player.getUniqueId().equals(get(body,"owner")),"production taming adopts flyer for summon regression");
        call(companions,"dismiss",player);player.setAllowFlight(true);player.setFlying(true);player.teleport(anchor().add(0,9,-2.6));
        check(call(companions,"summon",player,id)==null,"flyer can summon beside an airborne owner without nearby footing");
        Object airBody=call(companions,"body",player.getUniqueId());check(airBody!=null,"air summon creates companion body");
        check((boolean)get(airBody,"airborne"),"air summon explicitly marks intended flight when no floor is in range");
        later(12,()->{
            check(shell(airBody).getLocation().getY()>187,"summoned flyer stays near airborne owner instead of falling");
            check(!(boolean)get(airBody,"falling"),"summoned flyer is excluded from ground gravity");
            call(companions,"mode",player,id,"perch");Object perched=call(companions,"body",player.getUniqueId());
            later(8,()->{
                check((boolean)get(perched,"perched")&&!(boolean)get(perched,"falling"),"perched body remains excluded from gravity");
                near(shell(perched).getLocation().getY(),player.getLocation().getY()+player.getHeight()+.02,"perched receiver remains on owner's head");
                call(companions,"dismiss",player);player.setFlying(false);player.setAllowFlight(false);player.teleport(anchor().add(0,0,-2.6));next();
            });
        });
    }
    @SuppressWarnings("unchecked") void clearClick()throws Exception{((Map<UUID,?>)get(bodies,"lastClick")).clear();}
    void look(Location origin,Location target){origin.setDirection(target.toVector().subtract(origin.toVector().add(new Vector(0,player.getEyeHeight(),0))));player.teleport(origin);}
    void input(EquipmentSlot hand){Bukkit.getPluginManager().callEvent(new PlayerInteractEvent(player,Action.RIGHT_CLICK_AIR,player.getInventory().getItemInMainHand(),null,BlockFace.SELF,hand));}
    int trust(Object body)throws Exception{return (int)get(body,"trust");}
    @SuppressWarnings("unchecked") void routingDedup(Object body)throws Exception{
        Map<Object,Object> managers=(Map<Object,Object>)get(bodies,"managers");Object original=managers.get(body);int[] deliveries={0};
        Class<?> manager=Class.forName("com.example.voidscape.pet.PetBodies$Manager");
        Object counting=Proxy.newProxyInstance(manager.getClassLoader(),new Class<?>[]{manager},(proxy,method,args)->{
            if(method.getName().equals("interact"))deliveries[0]++;
            return null;
        });
        managers.put(body,counting);
        try{
            clearClick();
            actor.connection.handleUseItem(new ServerboundUseItemPacket(net.minecraft.world.InteractionHand.MAIN_HAND,++sequence,player.getYaw(),player.getPitch()));
            check(deliveries[0]==1,"native model aim reaches its production manager exactly once");
            input(EquipmentSlot.OFF_HAND);
            Bukkit.getPluginManager().callEvent(new PlayerInteractAtEntityEvent(player,shell(body),new Vector(0,.3,0),EquipmentSlot.HAND));
            Bukkit.getPluginManager().callEvent(new PlayerInteractEntityEvent(player,shell(body),EquipmentSlot.HAND));
            check(deliveries[0]==1,"offhand, at-entity and entity callbacks cannot duplicate manager delivery independently of feed cooldown");
        }finally{managers.put(body,original);clearClick();}
    }
    void clicks()throws Exception{
        Object body=spawn(PetCatalog.Species.CRYSTAL_STAG);Rabbit receiver=shell(body);Location visual=anchor().add(0,.8,0);
        look(anchor().add(0,0,-2.6),visual);player.setFoodLevel(20);player.getInventory().setItemInMainHand(new ItemStack(Material.APPLE,32));
        call(bodies,"refresh");check((boolean)call(bodies,"shownTo",body,player),"pack-ready player sees test pet");
        check(visual.getY()>receiver.getBoundingBox().getMaxY()+.15,"test aim is on sculpture above tiny vanilla receiver");
        routingDedup(body);
        actor.connection.handleUseItem(new ServerboundUseItemPacket(net.minecraft.world.InteractionHand.MAIN_HAND,++sequence,player.getYaw(),player.getPitch()));
        later(2,()->{
            int first=trust(body);check(first>=18&&first<=30,"native main-hand right-click packet feeds visible sculpture");check(player.getInventory().getItemInMainHand().getAmount()==31,"native click consumes one apple");
            input(EquipmentSlot.OFF_HAND);Bukkit.getPluginManager().callEvent(new PlayerInteractEntityEvent(player,receiver,EquipmentSlot.HAND));
            check(trust(body)==first&&player.getInventory().getItemInMainHand().getAmount()==31,"fallback, exact receiver and offhand do not duplicate feeding");
            clearClick();int before=actionBars;input(EquipmentSlot.HAND);check(trust(body)==first,"cooldown retains trust and meal");check(actionBars>before,"feed cooldown gives actionbar feedback");
            put(body,"lastFeed",0L);clearClick();
            for(int y=180;y<=182;y++)for(int x=cx-1;x<=cx+1;x++)world.getBlockAt(x,y,cz-1).setType(Material.STONE,false);
            input(EquipmentSlot.HAND);check(trust(body)==first,"aim fallback cannot feed through a wall");
            Bukkit.getPluginManager().callEvent(new PlayerInteractEntityEvent(player,receiver,EquipmentSlot.HAND));check(trust(body)==first,"exact receiver cannot feed through wall either");
            for(int y=180;y<=182;y++)for(int x=cx-1;x<=cx+1;x++)world.getBlockAt(x,y,cz-1).setType(Material.AIR,false);
            look(anchor().add(0,0,-7),visual);clearClick();input(EquipmentSlot.HAND);check(trust(body)==first,"aim fallback respects native interaction reach");
            Bukkit.getPluginManager().callEvent(new PlayerInteractEntityEvent(player,receiver,EquipmentSlot.HAND));check(trust(body)==first,"exact route also respects reach");
            look(anchor().add(0,0,-2.6),visual);player.setGameMode(GameMode.SPECTATOR);clearClick();input(EquipmentSlot.HAND);check(trust(body)==first,"spectator cannot feed");player.setGameMode(GameMode.SURVIVAL);
            garden.getConfig().set("pets.view.max-others",0);call(bodies,"refresh");clearClick();input(EquipmentSlot.HAND);check(trust(body)==first,"culled sculpture cannot receive aim clicks");
            garden.getConfig().set("pets.view.max-others",6);call(bodies,"refresh");clearClick();
            look(anchor().add(0,0,-2.6),visual.clone().add(5,0,0));input(EquipmentSlot.HAND);check(trust(body)==first,"looking beside model cannot select nearby pet");
            look(anchor().add(0,0,-2.6),visual);input(EquipmentSlot.HAND);check(trust(body)>first,"invalid aim does not swallow next valid click");
            put(body,"lastFeed",0L);clearClick();player.getInventory().setItemInMainHand(new ItemStack(Material.DIRT));input(EquipmentSlot.HAND);int count=trust(body);check(count<100,"information click does not feed");
            clearClick();player.getInventory().setItemInMainHand(new ItemStack(Material.APPLE,20));Bukkit.getPluginManager().callEvent(new PlayerInteractEntityEvent(player,receiver,EquipmentSlot.HAND));
            check(trust(body)>count,"normal exact receiver route still feeds after information click");
            garden.wildPets().clear();next();
        });
    }
    void finish(Throwable error){
        if(finished)return;finished=true;
        if(error!=null)getLogger().log(java.util.logging.Level.SEVERE,"PET GROUNDING CHECK FAILED",error);
        String report=(error==null?"PASS ":"FAIL ")+checks+" checks; removed footing idle/rest/greet/taming/stay, interrupted stride, steps and slabs, airborne summon/perch, Java passengers and Bedrock carrier, native model click, visibility/reach/obstruction/manager dedup/cooldown"+(error==null?"":": "+error);
        try{Files.writeString(Path.of("pet-grounding-result.txt"),report+"\n"+String.join("\n",lines)+"\n");}catch(Exception e){throw new RuntimeException(e);}
        if(actor!=null){actor.discard();var list=net.minecraft.server.MinecraftServer.getServer().getPlayerList();list.getPlayers().remove(actor);list.getPlayersByUUID().remove(actor.getUUID());}
        getLogger().info(report);Bukkit.getScheduler().runTaskLater(this,Bukkit::shutdown,1);
    }
}
