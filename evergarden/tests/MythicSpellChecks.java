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
        try{test.begin();Files.writeString(Path.of("mythic-result.txt"),"PASS "+test.checks+" checks; complete Solar/Chronos/Judgment timelines, damage, mana, durability, protection, cleanup, GUI and Geyser particle packets");}
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
        for(Spell spell:List.of(Spell.SOLAR_APOCALYPSE,Spell.CHRONOS_FINAL_HOUR,Spell.HEAVENS_JUDGMENT)) {
            LivingEntity elevated=null,outsideBeam=null;
            if(spell==Spell.HEAVENS_JUDGMENT) {
                world.getBlockAt(0,99,12).setType(Material.STONE);
                elevated=mob(world,.5,12.5,1000);elevated.teleport(elevated.getLocation().add(0,20,0));
                outsideBeam=mob(world,6.5,12.5,1000);
            }
            target.setHealth(1000);target.setFireTicks(0);hits.clear();connection.particles.clear();
            magic.casts().quit(player);magic.mana().account(player).setMana(100);
            magic.packs().status(new org.bukkit.event.player.PlayerResourcePackStatusEvent(player,
                com.example.advancemagic.pack.ResourcePackService.PACK_ID,org.bukkit.event.player.PlayerResourcePackStatusEvent.Status.DECLINED));
            var wand=magic.wands().create(spell);player.getInventory().setItemInMainHand(wand);
            if(spell==Spell.HEAVENS_JUDGMENT) {
                check(wand.getItemMeta().getLore().size()==magic.wands().create(Spell.SOLAR_APOCALYPSE).getItemMeta().getLore().size(),"Judgment uses the same standard item lore as other wands");
                var legacy=wand.clone();var meta=legacy.getItemMeta();var lore=new ArrayList<>(meta.getLore());
                lore.add("วงเวทย์ทอง 4 ชั้น · ชาร์จ 3 วินาที");lore.add("ลำแสงพิพากษากว้าง 8 บล็อก · ยิงต่อเนื่อง 4 วินาที");
                meta.setLore(lore);legacy.setItemMeta(meta);
                check(magic.wands().migrate(legacy)&&legacy.getItemMeta().getLore().equals(wand.getItemMeta().getLore()),"existing Judgment wands lose obsolete ability descriptions during migration");
            }
            check(magic.casts().cast(player,spell,player.getInventory().getItemInMainHand()),spell.id()+" casts through the real listener");
            check(magic.mana().account(player).mana()==100-spell.mana,"mana charged once");
            check(magic.wands().usesLeft(player.getInventory().getItemInMainHand())==29,"durability charged once");
            check(magic.mana().account(player).remaining(spell.id(),System.currentTimeMillis())>=(spell.cooldown-1)*1000,"cooldown starts");
            tick(31);
            if(spell!=Spell.HEAVENS_JUDGMENT) {
                var art=world.getEntities().stream().filter(e->e instanceof ItemDisplay).map(e->(ItemDisplay)e)
                    .filter(e->e.getTransformation().getScale().x()>0).toList();
                check(art.size()==(spell==Spell.SOLAR_APOCALYPSE?41:19),"continuous celestial geometry replaces dotted outlines");
                check(art.stream().noneMatch(player::canSee),"unloaded pack hides Solar and Chronos authored geometry");
                check(art.stream().allMatch(e->!e.isPersistent()&&e.getBrightness().getBlockLight()==15),"celestial planes are temporary and glow at full brightness");
                magic.packs().status(new org.bukkit.event.player.PlayerResourcePackStatusEvent(player,
                    com.example.advancemagic.pack.ResourcePackService.PACK_ID,org.bukkit.event.player.PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED));
                tick(4);
                check(art.stream().allMatch(player::canSee),"Solar and Chronos line art appears after the pack loads");
                if(spell==Spell.SOLAR_APOCALYPSE) {
                    var orbs=art.stream().filter(e->e.getItemStack().getItemMeta().getItemModel().getKey().equals("solar_corona")&&e.getLocation().getY()>101).toList();
                    check(orbs.size()==15,"five suns each have three intersecting celestial circles");
                    double smallest=orbs.stream().mapToDouble(e->e.getTransformation().getScale().x()).min().orElseThrow();
                    double largest=orbs.stream().mapToDouble(e->e.getTransformation().getScale().x()).max().orElseThrow();
                    check(Math.abs(largest/smallest-16)<.001,"Solar retains the original doubling proportions");
                    tick(28);
                    check(world.getEntities().stream().anyMatch(e->e instanceof ItemDisplay d&&d.getItemStack().getItemMeta().getItemModel().getKey().equals("solar_ray")&&d.getTransformation().getScale().y()>0),"Solar fires a continuous gold ribbon during its pulse");
                } else {
                    var minute=art.stream().filter(e->e.getItemStack().getItemMeta().getItemModel().getKey().equals("chronos_minute")).toList();
                    var hour=art.stream().filter(e->e.getItemStack().getItemMeta().getItemModel().getKey().equals("chronos_hour")).toList();
                    check(minute.size()==6&&hour.size()==6,"all six Chronos clocks have independent minute and hour hands");
                    var hand=minute.getFirst();var rotation=new org.joml.Quaternionf(hand.getTransformation().getLeftRotation());
                    tick(4);
                    check(!hand.getTransformation().getLeftRotation().equals(rotation),"clock hands animate independently of the detailed dial");
                    tick(4);
                    check(world.getEntities().stream().filter(e->e instanceof ItemDisplay d&&d.getItemStack().getItemMeta().getItemModel().getKey().equals("chronos_ray")&&d.getTransformation().getScale().y()>0).count()==5,"five clocks fire continuous cyan rays together");
                }
            }
            if(spell==Spell.HEAVENS_JUDGMENT) {
                var sigils=world.getEntities().stream().filter(e->e instanceof ItemDisplay).map(e->(ItemDisplay)e).toList();
                check(sigils.size()==4&&sigils.stream().allMatch(e->e.getItemStack().getItemMeta().getItemModel().getKey().startsWith("judgment_seal_")),"four textured horizontal seals charge before the beam appears");
                check(sigils.stream().allMatch(e->!e.isPersistent()&&e.getBrightness().getBlockLight()==15),"every golden seal is temporary and full brightness");
                check(sigils.stream().noneMatch(player::canSee),"unloaded resource pack never renders giant paper items");
                magic.packs().status(new org.bukkit.event.player.PlayerResourcePackStatusEvent(player,
                    com.example.advancemagic.pack.ResourcePackService.PACK_ID,org.bukkit.event.player.PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED));
                tick(30);
                check(sigils.stream().allMatch(player::canSee),"Java viewers see authored seals once the pack loads");
                check(sigils.stream().mapToDouble(e->e.getTransformation().getScale().x()).max().orElseThrow()==56,"upper golden seal reaches fifty-six blocks across");
                check(sigils.stream().allMatch(e->e.getTransformation().getScale().y()==1),"seal planes keep their horizontal thickness");
                var beam=world.getEntities().stream().filter(e->e instanceof ItemDisplay).map(e->(ItemDisplay)e)
                    .filter(e->e.getItemStack().getItemMeta().getItemModel().getKey().equals("judgment_beam")).findFirst().orElseThrow();
                check(beam.getItemStack().getItemMeta().getItemModel().getKey().equals("judgment_beam"),"Judgment uses the beacon beam model");
                check(!beam.isPersistent()&&beam.getBrightness().getBlockLight()==15,"beam is temporary and full brightness");
                tick(12);
                check(Math.abs(beam.getTransformation().getScale().x()-8)<.001,"beam grows to eight blocks wide");
            }
            if(spell==Spell.CHRONOS_FINAL_HOUR) {
                check(ordinary.hasPotionEffect(PotionEffectType.SLOWNESS)&&ordinary.getPotionEffect(PotionEffectType.SLOWNESS).getAmplifier()==127,"ordinary mobs are rooted");
                check(target.getPotionEffect(PotionEffectType.SLOWNESS).getAmplifier()==1,"high-health bosses are slowed without a hard root");
                tick(42); // Last rendered frame is age 84, immediately before reversal.
                var hand=world.getEntities().stream().filter(e->e instanceof ItemDisplay d&&d.getItemStack().getItemMeta().getItemModel().getKey().equals("chronos_minute"))
                    .map(e->(ItemDisplay)e).findFirst().orElseThrow();
                var before=new org.joml.Quaternionf(hand.getTransformation().getLeftRotation());
                tick(4);
                check(Math.abs(before.dot(hand.getTransformation().getLeftRotation()))>.99,"Chronos hands reverse without jumping to an unrelated angle");
            }
            tick(110+magic.terrain().duration()+1);
            check(magic.effects().size()==0,"full timeline finishes and cleans up");
            check(hits.size()==(spell==Spell.SOLAR_APOCALYPSE?6:spell==Spell.HEAVENS_JUDGMENT?9:12),"all beams/blades, echo and finisher hit");
            double expected=spell==Spell.SOLAR_APOCALYPSE
                ?5*magic.getConfig().getDouble("damage.solar-beam")+magic.getConfig().getDouble("damage.solar-apocalypse")
                :spell==Spell.HEAVENS_JUDGMENT?760
                :8.5*magic.getConfig().getDouble("damage.chronos-blade")+magic.getConfig().getDouble("damage.chronos-shatter")+magic.getConfig().getDouble("damage.chronos-final-burst");
            check(Math.abs(hits.stream().mapToDouble(Double::doubleValue).sum()-expected)<.001,"complete damage budget "+expected);
            check(ally.getHealth()==1000&&protectedTarget.getHealth()==1000,"allies and protected targets untouched");
            if(spell==Spell.HEAVENS_JUDGMENT) {
                check(Math.abs(elevated.getHealth()-240)<.001,"Judgment damages the tall beam column, not just a ground sphere");
                check(outsideBeam.getHealth()==1000,"targets outside the four-block beam radius remain unharmed");
                elevated.remove();outsideBeam.remove();
                world.getBlockAt(0,99,12).setType(Material.AIR);
            }
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
        double chronosDamage=8.5*magic.getConfig().getDouble("damage.chronos-blade")+magic.getConfig().getDouble("damage.chronos-shatter")+magic.getConfig().getDouble("damage.chronos-final-burst");
        check(Math.abs(hits.stream().mapToDouble(Double::doubleValue).sum()-chronosDamage*1.3)<.001,"damage multiplier survives the delayed timeline");
        tick(1);
        blockAll=true;hits.clear();check(magic.spells().cast(player,Spell.SOLAR_APOCALYPSE),"protected solar still renders");tick(142);
        check(hits.isEmpty(),"cancelled affect events block every damage stage");blockAll=false;
        for(var spell:List.of(Spell.SOLAR_APOCALYPSE,Spell.CHRONOS_FINAL_HOUR,Spell.HEAVENS_JUDGMENT)) {
            check(magic.spells().cast(player,spell),"cancellable cast starts");tick(30);hits.clear();
            magic.effects().closeOwner(player.getUniqueId());tick(155);
            check(hits.isEmpty()&&magic.effects().size()==0,"early cleanup never detonates or echoes");
        }
        check(magic.spells().cast(player,Spell.HEAVENS_JUDGMENT),"active Judgment cleanup starts");tick(75);hits.clear();
        magic.effects().closeOwner(player.getUniqueId());tick(100);
        check(hits.isEmpty()&&world.getEntities().stream().noneMatch(e->e instanceof ItemDisplay),"active beam disappears immediately on owner cleanup");
        blockAll=true;hits.clear();check(magic.spells().cast(player,Spell.HEAVENS_JUDGMENT),"protected Judgment still renders");tick(166);
        check(hits.isEmpty(),"Judgment honors cancelled affect events for every pulse");blockAll=false;
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
        for(var spell:List.of(Spell.SOLAR_APOCALYPSE,Spell.CHRONOS_FINAL_HOUR,Spell.HEAVENS_JUDGMENT)) {
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
        check(mythicIds.equals(Set.of("shulker_levitation","solar_apocalypse","chronos_final_hour","heavens_judgment")),"Vault Mythic pool contains exactly all four cores");
        check(com.example.voidscape.item.RelicService.MAGIC_CORES.size()-mythicIds.size()==14,"normal Vault pool retains fourteen cores");
        judgmentGrounding();castingMotion();judgmentPull();lineGeometry();bedrockParticles();team.unregister();
    }
    void judgmentGrounding()throws Exception {
        var world=player.getWorld();Location saved=player.getLocation(),targetSaved=target.getLocation();
        for(int x=-20;x<=20;x++)for(int z=-20;z<=30;z++)world.getBlockAt(x,99,z).setType(Material.STONE);
        var spells=new com.example.advancemagic.spell.MythicSpells(magic.context());
        var center=spells.getClass().getDeclaredMethod("judgmentCenter",Player.class);center.setAccessible(true);
        try {
            for(float pitch:new float[]{-90,-30,0,30,90}) {
                player.teleport(new Location(world,10.5,110,-10.5,0,pitch));
                var base=(Location)center.invoke(spells,player);
                check(base!=null&&Math.abs(base.getY()-100)<1e-6,"Judgment finds the floor when aiming through air at pitch "+pitch+"; base="+base+"; hit="+magic.context().target(player,30));
                check(base.getYaw()==0&&base.getPitch()==0,"Judgment ground anchor carries no camera rotation");
            }
            player.teleport(new Location(world,10.5,110,-10.5,0,-90));
            check(magic.spells().cast(player,Spell.HEAVENS_JUDGMENT),"upward empty-air Judgment casts onto the floor");tick(73);
            checkJudgmentColumn(100,35);
            magic.effects().closeOwner(player.getUniqueId());
            // The original entity target path used flying mob feet as the beam floor.
            target.setHealth(1000);
            target.teleport(targetSaved.clone().add(0,10,0));
            player.teleport(new Location(world,.5,110,.5,0,0));
            check(magic.context().target(player,30).getHitEntity()==target,"regression aims at an airborne target");
            check(magic.spells().cast(player,Spell.HEAVENS_JUDGMENT),"airborne-target Judgment casts");tick(73);
            checkJudgmentColumn(100,35);
            magic.effects().closeOwner(player.getUniqueId());target.teleport(targetSaved);
            player.teleport(new Location(world,10.5,110,-10.5,0,-90));
            var floor=world.getBlockAt(10,99,-11);floor.setType(Material.STONE_SLAB);
            var base=(Location)center.invoke(spells,player);
            check(base!=null&&Math.abs(base.getY()-99.5)<1e-6,"Judgment follows the half-block collision surface of a slab");
            floor.setType(Material.STONE_BRICK_STAIRS);
            var stairs=(org.bukkit.block.data.type.Stairs)floor.getBlockData();stairs.setFacing(org.bukkit.block.BlockFace.EAST);floor.setBlockData(stairs);
            player.teleport(new Location(world,10.25,110,-10.5,0,-90));
            base=(Location)center.invoke(spells,player);
            check(base!=null&&Math.abs(base.getY()-99.5)<1e-6,"Judgment follows the lower stair tread at the exact aimed X/Z");
            player.teleport(new Location(world,10.75,110,-10.5,0,-90));
            base=(Location)center.invoke(spells,player);
            check(base!=null&&Math.abs(base.getY()-100)<1e-6,"Judgment follows the upper stair tread without using the block bounding-box height");
            floor.setType(Material.STONE_SLAB);player.teleport(new Location(world,10.5,110,-10.5,0,-90));
            var ceiling=world.getBlockAt(10,120,-11);ceiling.setType(Material.STONE);
            base=(Location)center.invoke(spells,player);
            check(base!=null&&Math.abs(base.getY()-99.5)<1e-6,"aiming at a ceiling underside still finds the cave floor");
            ceiling.setType(Material.AIR);floor.setType(Material.STONE);
            var wall=world.getBlockAt(10,105,-7);wall.setType(Material.STONE);
            player.teleport(new Location(world,10.5,104,-10.5,0,0));
            base=(Location)center.invoke(spells,player);
            check(base!=null&&Math.abs(base.getY()-100)<1e-6,"wall-face aim resolves the floor outside the wall");wall.setType(Material.AIR);
            // A roof above the aim must not replace the local floor with a heightmap surface.
            ceiling.setType(Material.STONE);player.teleport(new Location(world,10.5,110,-10.5,0,90));
            base=(Location)center.invoke(spells,player);
            check(base!=null&&Math.abs(base.getY()-100)<1e-6,"Judgment uses the local floor beneath a roof");ceiling.setType(Material.AIR);
            for(int y=world.getMinHeight();y<110;y++)world.getBlockAt(10,y,-11).setType(Material.AIR);
            player.teleport(new Location(world,10.5,110,-10.5,0,-90));
            check(center.invoke(spells,player)==null&&!magic.spells().cast(player,Spell.HEAVENS_JUDGMENT),"Judgment refuses a void column instead of creating a floating beam");
            floor.setType(Material.STONE);
            int surface=world.getMaxHeight()-15;
            world.getBlockAt(10,surface-1,-11).setType(Material.STONE);
            player.teleport(new Location(world,10.5,surface+2,-10.5,0,90));
            check(magic.spells().cast(player,Spell.HEAVENS_JUDGMENT),"Judgment near the build ceiling still casts");tick(73);
            checkJudgmentColumn(surface,35*(world.getMaxHeight()-1-surface)/36.0);
            world.getBlockAt(10,surface-1,-11).setType(Material.AIR);
        } finally {
            magic.effects().closeOwner(player.getUniqueId());target.teleport(targetSaved);player.teleport(saved);
            player.setVelocity(new org.bukkit.util.Vector());
        }
    }
    void checkJudgmentColumn(double ground,double height) {
        var displays=player.getWorld().getEntities().stream().filter(e->e instanceof ItemDisplay).map(e->(ItemDisplay)e).toList();
        var beam=displays.stream().filter(e->e.getItemStack().getItemMeta().getItemModel().getKey().equals("judgment_beam")).findFirst().orElseThrow();
        var transform=beam.getTransformation();
        double bottom=beam.getY()+transform.getTranslation().y()-transform.getScale().y()/2;
        double top=beam.getY()+transform.getTranslation().y()+transform.getScale().y()/2;
        check(Math.abs(bottom-ground)<1e-5&&Math.abs(top-ground-height)<1e-5,"Java beam endpoints run from the collision floor to the highest seal");
        check(displays.size()==5&&displays.stream().allMatch(e->e.getPitch()==0&&e.getYaw()==0),"all Judgment displays stay vertical/horizontal independently of aim rotation");
        var highest=displays.stream().filter(e->e!=beam).mapToDouble(Entity::getY).max().orElseThrow();
        check(Math.abs(highest-top)<1e-5,"beam top meets the highest seal even near the world ceiling");
        check(Math.abs(transform.getScale().x()-8)<1e-5,"grounding preserves the eight-block attack width");
    }
    void castingMotion() {
        Location saved=player.getLocation();boolean gravity=player.hasGravity(),flight=player.getAllowFlight();
        try {
            // Looking straight up still retreats by yaw, and does not normalize a zero horizontal vector.
            player.teleport(new Location(saved.getWorld(),.5,100,.5,45,-90));
            Location facing=player.getLocation();facing.setPitch(0);
            var forward=facing.getDirection();
            for(var spell:List.of(Spell.SOLAR_APOCALYPSE,Spell.CHRONOS_FINAL_HOUR,Spell.HEAVENS_JUDGMENT)) {
                magic.context().clearPlayerVelocity(player.getUniqueId());player.setVelocity(new org.bukkit.util.Vector());
                check(magic.spells().cast(player,spell),spell.id()+" entrance starts");tick(1);
                var impulse=player.getVelocity();impulse.checkFinite();
                check(impulse.getY()>0&&impulse.getY()<.4&&impulse.clone().setY(0).dot(forward)<0
                    &&impulse.clone().setY(0).length()<.2,spell.id()+" gently lifts and retreats even at vertical pitch");
                var movement=new org.bukkit.util.Vector(.03,0,0);player.setVelocity(movement);tick(8);
                check(player.getVelocity().distanceSquared(movement)<1e-12,spell.id()+" entrance does not repeatedly override movement");
                check(player.hasGravity()==gravity&&player.getAllowFlight()==flight,spell.id()+" entrance preserves gravity and flight flags");
                magic.effects().closeOwner(player.getUniqueId());
            }
            magic.getConfig().set("compatibility.player-spell-velocity",false);
            magic.context().clearPlayerVelocity(player.getUniqueId());player.setVelocity(new org.bukkit.util.Vector());
            check(magic.spells().cast(player,Spell.HEAVENS_JUDGMENT),"entrance with disabled player velocity still casts");tick(1);
            check(player.getVelocity().lengthSquared()==0,"caster entrance honors the player velocity compatibility switch");
            magic.effects().closeOwner(player.getUniqueId());
            magic.getConfig().set("compatibility.player-spell-velocity",true);
            magic.context().clearPlayerVelocity(player.getUniqueId());player.setVelocity(new org.bukkit.util.Vector());
            check(magic.spells().cast(player,Spell.SOLAR_APOCALYPSE),"entrance can be cancelled before startup");
            magic.effects().closeOwner(player.getUniqueId());tick(1);
            check(player.getVelocity().lengthSquared()==0,"cancellation before startup applies no caster displacement");
        } finally {
            magic.getConfig().set("compatibility.player-spell-velocity",true);
            magic.effects().closeOwner(player.getUniqueId());magic.context().clearPlayerVelocity(player.getUniqueId());
            player.teleport(saved);player.setVelocity(new org.bukkit.util.Vector());
        }
    }
    void judgmentPull() {
        var world=player.getWorld();
        LivingEntity near=mob(world,6.5,12.5,1000),far=mob(world,9.5,12.5,1000),diagonal=mob(world,7,19,1000);
        try {
            for(var mob:List.of(near,far,diagonal,ally,protectedTarget,target))mob.setVelocity(new org.bukkit.util.Vector());
            target.setHealth(1000);hits.clear();magic.context().clearPlayerVelocity(player.getUniqueId());
            check(magic.spells().cast(player,Spell.HEAVENS_JUDGMENT),"Judgment gathering starts");tick(1);
            check(near.getVelocity().getX()<0&&near.getVelocity().length()<.3&&near.getVelocity().getY()==0,
                "Judgment gently draws enemies outside the damage column inward without lifting them");
            check(far.getVelocity().lengthSquared()==0&&diagonal.getVelocity().lengthSquared()==0,
                "Judgment pull stays inside its eight-block cylindrical radius");
            check(ally.getVelocity().lengthSquared()==0&&protectedTarget.getVelocity().lengthSquared()==0,
                "Judgment pull respects allied targets and cancelled protection events");
            check(target.getVelocity().lengthSquared()==0,"Judgment does not jitter enemies already at the center");
            check(hits.isEmpty()&&near.getHealth()==1000,"Judgment charge pull adds no early damage");
            blockAll=true;near.setVelocity(new org.bukkit.util.Vector());tick(10);
            check(near.getVelocity().lengthSquared()==0,"cancelled affect events stop subsequent Judgment pulls");
            blockAll=false;tick(49);near.setVelocity(new org.bukkit.util.Vector());tick(1);
            check(near.getVelocity().getX()<0,"Judgment keeps gathering enemies while the beam is active");
            tick(79);near.setVelocity(new org.bukkit.util.Vector());tick(1);
            check(near.getVelocity().lengthSquared()==0,"Judgment stops gathering when the beam closes");
            magic.effects().closeOwner(player.getUniqueId());near.setVelocity(new org.bukkit.util.Vector());tick(20);
            check(near.getVelocity().lengthSquared()==0,"cancelled Judgment leaves no pull task running");
        } finally {
            blockAll=false;magic.effects().closeOwner(player.getUniqueId());near.remove();far.remove();diagonal.remove();
        }
    }
    void lineGeometry()throws Exception {
        var loader=magic.getClass().getClassLoader();
        var visualType=loader.loadClass("com.example.advancemagic.spell.MythicVisuals");
        var visualCtor=visualType.getDeclaredConstructors()[0];visualCtor.setAccessible(true);
        Object visuals=visualCtor.newInstance(magic.context());
        var lineType=loader.loadClass("com.example.advancemagic.spell.MythicLineVisuals");
        var ctor=lineType.getDeclaredConstructors()[0];ctor.setAccessible(true);
        var ray=lineType.getDeclaredMethod("ray",visualType,Location.class,Location.class,double.class);ray.setAccessible(true);
        var hide=lineType.getDeclaredMethod("hide");hide.setAccessible(true);
        var field=lineType.getDeclaredField("display");field.setAccessible(true);
        var effect=magic.effects().start(player,1,(e,age)->false);
        try {
            Location from=new Location(player.getWorld(),.5,125,12.5);
            Object line=ctor.newInstance(effect,from,"solar_ray");
            ItemDisplay display=(ItemDisplay)field.get(line);
            var destinations=new ArrayList<Location>();
            destinations.add(from.clone().add(0,-24,0));
            for(int i=0;i<5;i++)destinations.add(from.clone().add(Math.cos(Math.PI*2*i/5)*4,-24,Math.sin(Math.PI*2*i/5)*4));
            destinations.add(from.clone().add(14,-24,14));
            destinations.add(from.clone().add(-14,-24,-14));
            destinations.add(from.clone().add(0,24,0));
            for(Location to:destinations) {
                ray.invoke(line,visuals,from,to,.75);
                var transform=display.getTransformation();
                var halfAxis=transform.getLeftRotation().transform(new org.joml.Vector3f(0,transform.getScale().y()/2,0));
                Location center=display.getLocation().add(transform.getTranslation().x(),transform.getTranslation().y(),transform.getTranslation().z());
                Location start=center.clone().add(-halfAxis.x(),-halfAxis.y(),-halfAxis.z());
                Location end=center.clone().add(halfAxis.x(),halfAxis.y(),halfAxis.z());
                check(start.distanceSquared(from)<1e-8&&end.distanceSquared(to)<1e-8,"pulsed ray keeps both exact world endpoints: "+to.toVector());
                check(display.getInterpolationDuration()==0&&display.getTeleportDuration()==0,"ray appears without a sideways interpolation sweep");
                var rotation=new org.joml.Quaternionf(transform.getLeftRotation());
                hide.invoke(line);
                check(display.getTransformation().getScale().lengthSquared()==0&&display.getTransformation().getLeftRotation().equals(rotation),"hidden ray preserves its orientation for the next pulse");
            }
        } finally {effect.close();}
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
        var beam=clazz.getDeclaredMethod("judgmentBeam",Location.class,double.class,double.class);beam.setAccessible(true);
        beam.invoke(visuals,new Location(player.getWorld(),2,105,4),35.0,8.0);
        var packet=session.particles.getLast();
        check(packet.getIdentifier().equals("advance_magic:judgment_beam")&&packet.getPosition().getY()==122.5f,"Bedrock beam is centered on the same vertical column as Java");
        check(packet.getMolangVariablesJson().orElseThrow().contains("35.0")&&packet.getMolangVariablesJson().orElseThrow().contains("8.0"),"Bedrock receives the full beam height and width");
        Location aimSaved=player.getLocation();
        try {
            player.teleport(new Location(player.getWorld(),10.5,110,-10.5,45,-90));
            var spells=new com.example.advancemagic.spell.MythicSpells(magic.context());
            var ground=spells.getClass().getDeclaredMethod("judgmentCenter",Player.class);ground.setAccessible(true);
            Location base=(Location)ground.invoke(spells,player);
            beam.invoke(visuals,base,35.0,8.0);packet=session.particles.getLast();
            check(Math.abs(packet.getPosition().getY()-17.5-100)<1e-6,"Bedrock beam lower endpoint reaches the same resolved floor when aiming upward");
            check(Math.abs(packet.getPosition().getY()+17.5-135)<1e-6,"Bedrock beam upper endpoint meets the same highest seal");
        } finally {player.teleport(aimSaved);}
        var seal=clazz.getDeclaredMethod("judgmentSeal",Location.class,double.class,double.class,int.class);seal.setAccessible(true);
        seal.invoke(visuals,new Location(player.getWorld(),2,140,4),56.0,-30.0,1);
        packet=session.particles.getLast();
        check(packet.getIdentifier().equals("advance_magic:judgment_seal_1")&&packet.getPosition().getY()==140,"Bedrock receives the authored seal at the exact overhead height");
        check(packet.getMolangVariablesJson().orElseThrow().contains("56.0")&&packet.getMolangVariablesJson().orElseThrow().contains("-30.0"),"Bedrock seal receives the same diameter and counter-rotation");
        var plane=clazz.getDeclaredMethod("linePlane",String.class,Location.class,double.class,double.class,org.bukkit.util.Vector.class,org.bukkit.util.Vector.class);plane.setAccessible(true);
        var x=new org.bukkit.util.Vector(1,0,0);var y=new org.bukkit.util.Vector(0,1,0);var z=new org.bukkit.util.Vector(0,0,1);
        plane.invoke(visuals,"chronos_dial",new Location(player.getWorld(),2,124,4),16.0,16.0,x,y);
        packet=session.particles.getLast();
        check(packet.getIdentifier().equals("advance_magic:chronos_dial")&&particleCenter(packet).distanceSquared(new Location(player.getWorld(),2,124,4))<1e-8,"Bedrock receives the upright authored Chronos clock at the same position");
        check(packet.getMolangVariablesJson().orElseThrow().contains("line_normal_z")&&!packet.getMolangVariablesJson().orElseThrow().contains("NaN"),"upright Bedrock clocks receive a finite world normal");
        plane.invoke(visuals,"solar_corona",new Location(player.getWorld(),2,140,4),44.0,44.0,x,z);
        packet=session.particles.getLast();
        check(packet.getIdentifier().equals("advance_magic:solar_corona_flat")&&particleCenter(packet).distanceSquared(new Location(player.getWorld(),2,140,4))<1e-8,"horizontal Solar circles use the explicit Bedrock XZ plane");
        var ray=clazz.getDeclaredMethod("lineRay",String.class,Location.class,double.class,double.class,org.bukkit.util.Vector.class);ray.setAccessible(true);
        ray.invoke(visuals,"chronos_echo",new Location(player.getWorld(),2,112,4),.75,24.0,y);
        packet=session.particles.getLast();
        check(packet.getIdentifier().equals("advance_magic:chronos_echo")&&packet.getMolangVariablesJson().orElseThrow().contains("24.0"),"Bedrock echo rays use continuous violet ribbons with the full attack length");
        Location saved=player.getLocation();
        var direction=new org.bukkit.util.Vector(4,-24,-7).normalize();
        ray.invoke(visuals,"solar_ray",new Location(player.getWorld(),2,112,4),1.1,25.0,direction);
        String original=session.particles.getLast().getMolangVariablesJson().orElseThrow();
        player.teleport(saved.clone().add(12,3,-10));
        ray.invoke(visuals,"solar_ray",new Location(player.getWorld(),2,112,4),1.1,25.0,direction);
        check(original.equals(session.particles.getLast().getMolangVariablesJson().orElseThrow()),"moving the viewer cannot change the Bedrock attack axis");
        var variables=com.google.gson.JsonParser.parseString(original).getAsJsonArray();
        var values=new HashMap<String,Double>();
        for(var row:variables){var entry=row.getAsJsonObject();values.put(entry.get("name").getAsString(),entry.getAsJsonObject("value").get("value").getAsDouble());}
        check(values.get("variable.line_rotation")==0&&Math.abs(values.get("variable.line_normal_y")-direction.getY())<1e-12,"Bedrock ray carries the endpoint direction and zero spin");
        player.teleport(saved);
        chronosBedrockLayout(visuals,session);
        for(var owner:List.of(magic,garden))try(var pack=new java.util.zip.ZipFile(owner.getDataFolder().toPath().resolve("resource-packs/"+(owner==magic?"advance-magic":"evergarden")+"-bedrock.mcpack").toFile())) {
            for(String name:List.of("solar_corona","solar_orbit","chronos_dial","chronos_minute","chronos_hour","chronos_ripple","solar_ray","chronos_ray","chronos_echo")) {
                check(pack.getEntry("particles/"+name+".particle.json")!=null&&pack.getEntry("particles/"+name+"_flat.particle.json")!=null&&pack.getEntry("textures/particle/"+name+".png")!=null,"served Bedrock pack contains every orientation of "+name);
            }
        }
    }
    Map<String,Double> particleVariables(SpawnParticleEffectPacket packet) {
        var result=new HashMap<String,Double>();
        for(var row:com.google.gson.JsonParser.parseString(packet.getMolangVariablesJson().orElseThrow()).getAsJsonArray()) {
            var entry=row.getAsJsonObject();
            result.put(entry.get("name").getAsString(),entry.getAsJsonObject("value").get("value").getAsDouble());
        }
        return result;
    }
    Location particleCenter(SpawnParticleEffectPacket packet) {
        var vars=particleVariables(packet);var pos=packet.getPosition();
        return new Location(player.getWorld(),pos.getX()+vars.getOrDefault("variable.line_offset_x",0d),
            pos.getY()+vars.getOrDefault("variable.line_offset_y",0d),pos.getZ()+vars.getOrDefault("variable.line_offset_z",0d));
    }
    void chronosBedrockLayout(Object visuals,CaptureSession session)throws Exception {
        var loader=magic.getClass().getClassLoader();
        var type=loader.loadClass("com.example.advancemagic.spell.ChronosLineVisuals");
        var ctor=type.getDeclaredConstructors()[0];ctor.setAccessible(true);
        var frame=type.getDeclaredMethod("frame",visuals.getClass(),int.class,boolean.class);frame.setAccessible(true);
        var x=new org.bukkit.util.Vector(1,0,0);var z=new org.bukkit.util.Vector(0,0,1);
        Location base=player.getLocation().add(0,0,16),outer=base.clone().add(0,36,0);
        var faces=new ArrayList<Location>();var axes=new ArrayList<org.bukkit.util.Vector>();
        faces.add(base.clone().add(0,24,0));axes.add(x);
        for(int i=0;i<4;i++) {
            double a=Math.PI*2*i/4+Math.PI/4;
            faces.add(base.clone().add(Math.cos(a)*14,25,Math.sin(a)*14));
            axes.add(x.clone().multiply(-Math.sin(a)).add(z.clone().multiply(Math.cos(a))));
        }
        var expected=new ArrayList<Location>(faces);expected.add(outer);
        var effect=magic.effects().start(player,1,(e,age)->false);
        try {
            Object clocks=ctor.newInstance(effect,base,faces,axes,outer,x,z);
            for(int age:new int[]{0,40,84,88,100,156}) {
                session.particles.clear();frame.invoke(clocks,visuals,age,false);
                var dials=session.particles.stream().filter(p->p.getIdentifier().matches("advance_magic:chronos_dial(?:_flat)?")
                    &&particleCenter(p).getY()>base.getY()+20).toList();
                check(dials.size()==6,"Bedrock receives all six aerial Chronos clocks in one frame at age "+age);
                for(int i=0;i<6;i++) {
                    var packet=dials.get(i);var center=particleCenter(packet);var vars=particleVariables(packet);
                    var javaDial=player.getWorld().getEntities().stream().filter(e->e instanceof ItemDisplay d
                        &&d.getItemStack().getItemMeta().getItemModel().getKey().equals("chronos_dial")
                        &&d.getLocation().distanceSquared(center)<1e-7).map(e->(ItemDisplay)e).findFirst().orElseThrow();
                    check(center.distanceSquared(expected.get(i))<1e-8&&Math.abs(vars.get("variable.line_width")-javaDial.getTransformation().getScale().x())<1e-5,
                        "Bedrock clock "+i+" keeps Java's world center and diameter");
                    var pos=packet.getPosition();var emitter=new Location(player.getWorld(),pos.getX(),pos.getY(),pos.getZ());
                    check(emitter.distanceSquared(player.getEyeLocation())<=144.001,"aerial clock "+i+" activates within twelve blocks of its viewer");
                }
            }
        }finally{effect.close();}
    }
    void cleanup() {
        HandlerList.unregisterAll(this);
        if(player!=null){magic.effects().closeOwner(player.getUniqueId());magic.statuses().clear(player);magic.casts().quit(player);player.closeInventory();}
        mobs.forEach(Entity::remove);
        var board=Bukkit.getScoreboardManager().getMainScoreboard();var team=board.getTeam("mythic-test");if(team!=null)team.unregister();
        if(handle!=null){MinecraftServer.getServer().getPlayerList().getPlayers().remove(handle);MinecraftServer.getServer().getPlayerList().getPlayersByUUID().remove(handle.getUUID());handle.discard();}
    }
}
