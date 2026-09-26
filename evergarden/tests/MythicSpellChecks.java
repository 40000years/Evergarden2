import com.example.advancemagic.AdvanceMagicPlugin;
import com.example.advancemagic.api.MagicAffectEvent;
import com.example.advancemagic.spell.Spell;
import com.example.voidscape.VoidscapePlugin;
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
import org.bukkit.potion.PotionEffectType;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.session.GeyserSession;
import org.cloudburstmc.protocol.bedrock.packet.*;
import java.net.InetSocketAddress;
import java.nio.file.*;
import java.util.*;

/** Test-only real Paper casts and real Geyser packet objects; never shipped. */
public final class MythicSpellChecks implements Listener {
    static final class TestConnection extends Connection {
        final List<ClientboundLevelParticlesPacket> particles=new ArrayList<>();
        TestConnection(){super(PacketFlow.SERVERBOUND);channel=new EmbeddedChannel();address=new InetSocketAddress("127.0.0.1",1);}
        void receive(Packet<?> p){if(p instanceof ClientboundLevelParticlesPacket particle)particles.add(particle);}
        @Override public void send(Packet<?> p){receive(p);}
        @Override public void send(Packet<?> p,ChannelFutureListener listener){receive(p);}
        @Override public void send(Packet<?> p,ChannelFutureListener listener,boolean flush){receive(p);}
        @Override public boolean isConnected(){return true;}
    }
    public static class CaptureSession extends GeyserSession {
        public List<SpawnParticleEffectPacket> particles;
        public CaptureSession(){super(null,null,null);}
        @Override public void sendUpstreamPacket(BedrockPacket packet){particles.add((SpawnParticleEffectPacket)packet);}
    }
    final AdvanceMagicPlugin magic;
    final VoidscapePlugin garden;
    final org.bukkit.plugin.java.JavaPlugin host;
    Player player;ServerPlayer handle;TestConnection connection;
    LivingEntity target,ally,protectedTarget,ordinary;
    final List<LivingEntity> mobs=new ArrayList<>();
    final List<Double> hits=new ArrayList<>();
    int checks;boolean blockAll;
    MythicSpellChecks(org.bukkit.plugin.java.JavaPlugin host,AdvanceMagicPlugin magic,VoidscapePlugin garden){this.host=host;this.magic=magic;this.garden=garden;}
    void check(boolean value,String label){if(!value)throw new AssertionError(label);checks++;host.getLogger().info("PASS Mythic: "+label);}
    @EventHandler public void protect(MagicAffectEvent event){if(blockAll||event.getTarget()==protectedTarget)event.setCancelled(true);}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void damage(EntityDamageByEntityEvent event){if(event.getEntity()==target)hits.add(event.getDamage());}
    @EventHandler(priority=EventPriority.HIGHEST)
    public void keepOrdinaryMobAlive(EntityDamageByEntityEvent event){if(event.getEntity()==ordinary)event.setCancelled(true);}
    public static void run(org.bukkit.plugin.java.JavaPlugin host,AdvanceMagicPlugin magic,VoidscapePlugin garden)throws Exception {
        var test=new MythicSpellChecks(host,magic,garden);
        try{test.begin();Files.writeString(Path.of("mythic-result.txt"),"PASS "+test.checks+" checks; complete Solar/Chronos timelines, damage, mana, durability, protection, cleanup, GUI and Geyser particle packets");}
        finally{test.cleanup();}
    }
    LivingEntity mob(World w,double x,double z,double health) {
        var e=w.spawn(new Location(w,x,100,z),WitherSkeleton.class,m->{m.setAI(false);m.setGravity(false);m.setSilent(true);});
        e.getAttribute(Attribute.MAX_HEALTH).setBaseValue(health);e.setHealth(health);e.setMaximumNoDamageTicks(0);mobs.add(e);return e;
    }
    void tick(int count){for(int i=0;i<count;i++){magic.effects().tick();magic.statuses().tick();}}
    void begin()throws Exception {
        World world=Bukkit.getWorlds().getFirst();
        for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)world.getChunkAt(x,z).setForceLoaded(true);
        for(int x=-20;x<=20;x++)for(int z=-20;z<=30;z++)for(int y=100;y<=125;y++)world.getBlockAt(x,y,z).setType(Material.AIR);
        var server=MinecraftServer.getServer();var level=((CraftWorld)world).getHandle();
        var profile=new GameProfile(UUID.randomUUID(),"MythicTestActor");
        handle=new ServerPlayer(server,level,profile,ClientInformation.createDefault());connection=new TestConnection();
        handle.connection=new ServerGamePacketListenerImpl(server,connection,handle,CommonListenerCookie.createInitial(profile,false));
        handle.setPos(.5,100,.5);server.getPlayerList().getPlayers().add(handle);server.getPlayerList().getPlayersByUUID().put(profile.id(),handle);
        level.addNewPlayer(handle);player=handle.getBukkitEntity();player.setOp(true);player.setGravity(false);player.setInvulnerable(true);
        player.teleport(new Location(world,.5,100,.5,0,0));
        target=mob(world,.5,12.5,1000);ally=mob(world,-3,12.5,1000);protectedTarget=mob(world,3,12.5,1000);ordinary=mob(world,5,12.5,80);
        var team=Bukkit.getScoreboardManager().getMainScoreboard().registerNewTeam("mythic-test");team.addEntry(player.getName());team.addEntry(ally.getUniqueId().toString());
        Bukkit.getPluginManager().registerEvents(this,host);
        long time=world.getTime();boolean storm=world.hasStorm();int entities=world.getEntities().size();
        for(Spell spell:List.of(Spell.SOLAR_APOCALYPSE,Spell.CHRONOS_FINAL_HOUR)) {
            target.setHealth(1000);target.setFireTicks(0);hits.clear();connection.particles.clear();
            magic.casts().quit(player);magic.mana().account(player).setMana(100);
            var wand=magic.wands().create(spell);player.getInventory().setItemInMainHand(wand);
            check(magic.casts().cast(player,spell,player.getInventory().getItemInMainHand()),spell.id()+" casts through the real listener");
            check(magic.mana().account(player).mana()==100-spell.mana,"mana charged once");
            check(magic.wands().usesLeft(player.getInventory().getItemInMainHand())==29,"durability charged once");
            check(magic.mana().account(player).remaining(spell.id(),System.currentTimeMillis())>=(spell.cooldown-1)*1000,"cooldown starts");
            tick(31);
            if(spell==Spell.CHRONOS_FINAL_HOUR) {
                check(ordinary.hasPotionEffect(PotionEffectType.SLOWNESS)&&ordinary.getPotionEffect(PotionEffectType.SLOWNESS).getAmplifier()==127,"ordinary mobs are rooted");
                check(target.getPotionEffect(PotionEffectType.SLOWNESS).getAmplifier()==1,"high-health bosses are slowed without a hard root");
            }
            tick(110+magic.terrain().duration()+1);
            check(magic.effects().size()==0,"full timeline finishes and cleans up");
            check(hits.size()==(spell==Spell.SOLAR_APOCALYPSE?6:11),"all beams/blades, echo and finisher hit");
            double expected=spell==Spell.SOLAR_APOCALYPSE?340:304;
            check(Math.abs(hits.stream().mapToDouble(Double::doubleValue).sum()-expected)<.001,"complete damage budget "+expected);
            check(ally.getHealth()==1000&&protectedTarget.getHealth()==1000,"allies and protected targets untouched");
            check(!connection.particles.isEmpty(),"Java receives actual particle packets");
            check(connection.particles.size()<65000,"per-cast particle work is bounded, including crystal-beam fallback");
            check(magic.wands().restore(player.getInventory().getItemInMainHand()),"restoration supports the new wand");
            check(magic.wands().usesLeft(player.getInventory().getItemInMainHand())==30,"restored durability preserved");
            check(world.getTime()==time&&world.hasStorm()==storm,"season time and weather unchanged");
            check(world.getEntities().size()<=entities,"no effect entities or displays leaked");
        }
        target.setHealth(1000);hits.clear();
        magic.context().setCastDamageMultiplier(player.getUniqueId(),1.3);
        check(magic.spells().cast(player,Spell.CHRONOS_FINAL_HOUR),"upgraded Chronos starts");
        magic.context().clearCastDamageMultiplier(player.getUniqueId());tick(88+magic.terrain().duration()+1);
        check(Math.abs(hits.stream().mapToDouble(Double::doubleValue).sum()-395.2)<.001,"damage multiplier survives the delayed timeline");
        tick(1);
        blockAll=true;hits.clear();check(magic.spells().cast(player,Spell.SOLAR_APOCALYPSE),"protected solar still renders");tick(142);
        check(hits.isEmpty(),"cancelled affect events block every damage stage");blockAll=false;
        for(var spell:List.of(Spell.SOLAR_APOCALYPSE,Spell.CHRONOS_FINAL_HOUR)) {
            check(magic.spells().cast(player,spell),"cancellable cast starts");tick(30);hits.clear();
            magic.effects().closeOwner(player.getUniqueId());tick(155);
            check(hits.isEmpty()&&magic.effects().size()==0,"early cleanup never detonates or echoes");
        }
        for(int i=0;i<4;i++)check(magic.spells().cast(player,Spell.SOLAR_APOCALYPSE),"concurrent Mythic slot "+i);
        check(!magic.spells().cast(player,Spell.CHRONOS_FINAL_HOUR),"world cinematic limit refuses a fifth cast safely");
        magic.effects().closeOwner(player.getUniqueId());check(magic.spells().cast(player,Spell.CHRONOS_FINAL_HOUR),"cleanup releases cinematic slots");magic.effects().closeOwner(player.getUniqueId());
        magic.itemMenu().open(player,true);
        var inventory=player.getOpenInventory().getTopInventory();
        for(var spell:Spell.values()) {
            check(magic.wands().spell(inventory.getItem(spell.ordinal()))==spell,"Magic GUI wand "+spell.id());
            check(magic.wands().coreSpell(inventory.getItem(27+spell.ordinal()))==spell,"Magic GUI core "+spell.id());
        }
        check(magic.flyingStaff().isStaff(inventory.getItem(50)),"Magic GUI staff stays separate");
        garden.wandGui().open(player);inventory=player.getOpenInventory().getTopInventory();
        for(var spell:Spell.values()) {
            check(magic.wands().spell(inventory.getItem(spell.ordinal()))==spell,"Evergarden GUI wand "+spell.id());
            check(magic.wands().coreSpell(inventory.getItem(18+spell.ordinal()))==spell,"Evergarden GUI core "+spell.id());
        }
        check(magic.flyingStaff().isStaff(inventory.getItem(50)),"Evergarden GUI staff stays separate");
        for(var spell:List.of(Spell.SOLAR_APOCALYPSE,Spell.CHRONOS_FINAL_HOUR)) {
            var recipe=(org.bukkit.inventory.ShapedRecipe)Bukkit.getRecipe(new NamespacedKey(magic,spell.id()+"_ni_c"));
            check(recipe!=null&&magic.wands().spell(recipe.getResult())==spell,"new recipe returns the matching wand");
            check(recipe.getChoiceMap().get(recipe.getShape()[1].charAt(1)).test(garden.relics().createMagicCore(spell.id())),"recipe accepts the Evergarden core");
            player.getInventory().clear();player.getInventory().addItem(garden.relics().createMagicCore(spell.id()),new org.bukkit.inventory.ItemStack(Material.NETHERITE_INGOT,8));
            check(magic.itemMenu().craft(player,spell)==null,"inventory craft succeeds");
            check(magic.wands().spell(player.getInventory().getItem(0))==spell&&!player.getInventory().contains(Material.NETHERITE_INGOT),"craft consumes ingredients and delivers the wand");
        }
        var rarity=com.example.voidscape.item.RelicService.class.getDeclaredMethod("mythicCore",com.example.voidscape.item.RelicService.MagicCore.class);rarity.setAccessible(true);
        var mythicIds=new HashSet<String>();
        for(var core:com.example.voidscape.item.RelicService.MAGIC_CORES)if((boolean)rarity.invoke(null,core))mythicIds.add(core.id());
        check(mythicIds.equals(Set.of("shulker_levitation","solar_apocalypse","chronos_final_hour")),"Vault Mythic pool contains exactly all three cores");
        check(com.example.voidscape.item.RelicService.MAGIC_CORES.size()-mythicIds.size()==14,"normal Vault pool retains fourteen cores");
        bedrockParticles();team.unregister();
    }
    void bedrockParticles()throws Exception {
        var loader=magic.getClass().getClassLoader();
        var clazz=loader.loadClass("com.example.advancemagic.spell.MythicVisuals");
        var create=clazz.getDeclaredConstructors()[0];create.setAccessible(true);Object visuals=create.newInstance(magic.context());
        var frame=clazz.getDeclaredMethod("frame",Location.class);frame.setAccessible(true);frame.invoke(visuals,player.getLocation());
        var bridge=clazz.getDeclaredField("bridge");bridge.setAccessible(true);check(bridge.get(visuals)!=null,"optional adapter resolves the real installed Geyser API");
        var unsafeField=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");unsafeField.setAccessible(true);
        var session=(CaptureSession)((sun.misc.Unsafe)unsafeField.get(null)).allocateInstance(CaptureSession.class);session.particles=new ArrayList<>();
        var viewer=loader.loadClass("com.example.advancemagic.spell.MythicVisuals$Viewer").getDeclaredConstructors()[0];viewer.setAccessible(true);
        var viewers=clazz.getDeclaredField("viewers");viewers.setAccessible(true);viewers.set(visuals,List.of(viewer.newInstance(player,session,0)));
        var dust=clazz.getDeclaredMethod("dust",Location.class,Color.class,float.class);dust.setAccessible(true);
        var colors=Map.of("gold",0xFFD34D,"white",0xFFF2AF,"orange",0xFF732D,"cyan",0x72EDFF,"violet",0xA36BFF);
        for(var color:colors.entrySet()) {
            dust.invoke(visuals,new Location(player.getWorld(),2,105,4),Color.fromRGB(color.getValue()),2.5f);
            var packet=session.particles.getLast();
            check(packet.getIdentifier().equals("advance_magic:mythic_"+color.getKey())&&packet.getPosition().getX()==2&&packet.getPosition().getY()==105,"Bedrock receives matching world-space "+color.getKey());
            check(packet.getMolangVariablesJson().orElseThrow().contains("2.5"),"Bedrock receives Java particle size");
            for(var owner:List.of(magic,garden))try(var pack=new java.util.zip.ZipFile(owner.getDataFolder().toPath().resolve("resource-packs/"+(owner==magic?"advance-magic":"evergarden")+"-bedrock.mcpack").toFile())) {
                check(pack.getEntry("particles/mythic_"+color.getKey()+".particle.json")!=null&&pack.getEntry("textures/particle/mythic_dot.png")!=null,"served pack contains "+packet.getIdentifier());
            }
        }
    }
    void cleanup() {
        HandlerList.unregisterAll(this);
        if(player!=null){magic.effects().closeOwner(player.getUniqueId());magic.statuses().clear(player);magic.casts().quit(player);player.closeInventory();}
        mobs.forEach(Entity::remove);
        var board=Bukkit.getScoreboardManager().getMainScoreboard();var team=board.getTeam("mythic-test");if(team!=null)team.unregister();
        if(handle!=null){MinecraftServer.getServer().getPlayerList().getPlayers().remove(handle);MinecraftServer.getServer().getPlayerList().getPlayersByUUID().remove(handle.getUUID());handle.discard();}
    }
}
