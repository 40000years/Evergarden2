import com.example.advancemagic.AdvanceMagicPlugin;
import com.example.advancemagic.spell.Spell;
import com.mojang.authlib.GameProfile;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.entity.*;
import org.bukkit.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffectType;
import java.net.InetSocketAddress;
import java.util.*;

/** Test-only Paper 26.2 actor. This code and NMS dependencies are never shipped in the plugin. */
public final class IntegrationChecks extends JavaPlugin {
    static final class TestConnection extends Connection {
        TestConnection(){super(PacketFlow.SERVERBOUND);channel=new EmbeddedChannel();address=new InetSocketAddress("127.0.0.1",1);}
        @Override public void send(Packet<?> packet){}
        @Override public void send(Packet<?> packet,ChannelFutureListener listener){}
        @Override public void send(Packet<?> packet,ChannelFutureListener listener,boolean flush){}
        @Override public boolean isConnected(){return true;}
    }
    AdvanceMagicPlugin plugin;
    Player player;
    ServerPlayer handle;
    Vindicator target;
    int passed;
    void check(boolean value,String label){if(!value)throw new AssertionError(label);passed++;getLogger().info("PASS "+label);}
    void run(Runnable test){try{test.run();}catch(Throwable e){getLogger().log(java.util.logging.Level.SEVERE,"INTEGRATION FAILED after "+passed,e);finish();}}
    void later(int ticks,Runnable test){Bukkit.getScheduler().runTaskLater(this,()->run(test),ticks);}
    @Override public void onEnable(){later(20,this::begin);}
    void begin() {
        plugin=(AdvanceMagicPlugin)Bukkit.getPluginManager().getPlugin("advance-magic");
        check(plugin!=null&&plugin.isEnabled(),"plugin boots on Paper 26.2");
        World w=Bukkit.getWorlds().get(0);
        w.setTime(18000);
        for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++){w.getChunkAt(x,z).load();w.getChunkAt(x,z).setForceLoaded(true);}
        for(int x=-20;x<=20;x++)for(int z=-20;z<=20;z++)w.getBlockAt(x,99,z).setType(Material.STONE);
        var server=MinecraftServer.getServer();var world=((CraftWorld)w).getHandle();
        GameProfile profile=new GameProfile(UUID.randomUUID(),"MagicTestActor");
        handle=new ServerPlayer(server,world,profile,ClientInformation.createDefault());
        handle.connection=new ServerGamePacketListenerImpl(server,new TestConnection(),handle,CommonListenerCookie.createInitial(profile,false));
        handle.setPos(0.5,100,0.5);
        server.getPlayerList().getPlayers().add(handle);server.getPlayerList().getPlayersByUUID().put(profile.id(),handle);
        world.addNewPlayer(handle);player=handle.getBukkitEntity();player.setGameMode(GameMode.SURVIVAL);
        player.setGravity(false);player.setOp(true);
        player.teleport(new Location(w,0.5,100,0.5,0,0));
        check(player.isOnline()&&player.isValid(),"real server-backed test actor is online");
        for(Spell s:Spell.values()) {
            ItemStack wand=plugin.wands().create(s);
            check(plugin.wands().spell(wand)==s&&!wand.getItemMeta().hasItemModel()
                    &&wand.getItemMeta().getCustomModelDataComponent().getStrings().equals(List.of("advance_magic:"+s.id())),"wand PDC and vanilla-safe model selector "+s.id());
            ItemStack old=wand.clone();var oldMeta=old.getItemMeta();oldMeta.setItemModel(new NamespacedKey("advance_magic",s.id()));
            oldMeta.setCustomModelDataComponent(null);oldMeta.setDisplayName("Keep my custom name");old.setItemMeta(oldMeta);
            check(plugin.wands().migrate(old)&&!old.getItemMeta().hasItemModel()&&plugin.wands().spell(old)==s
                    &&old.getItemMeta().getDisplayName().equals("Keep my custom name"),"legacy wand migration preserves PDC and name "+s.id());
            check(!plugin.wands().migrate(old),"legacy migration is idempotent "+s.id());
            Recipe recipe=Bukkit.getRecipe(new NamespacedKey(plugin,s.id()+"_ni_c"));
            check(recipe instanceof ShapedRecipe&&((ShapedRecipe)recipe).getShape().length==3&&Arrays.stream(((ShapedRecipe)recipe).getShape()).allMatch(row->row.length()==3),"recipe shape "+s.id());
            var choices=((ShapedRecipe)recipe).getChoiceMap();
            String[] shape=((ShapedRecipe)recipe).getShape();boolean ingredients=true;
            for(int y=0;y<3;y++)for(int x=0;x<3;x++)ingredients &= choices.get(shape[y].charAt(x)).test(x==1&&y==1?plugin.wands().createCore(s):new ItemStack(Material.NETHERITE_INGOT));
            check(ingredients,"recipe ingredients "+s.id());
            RecipeChoice outer=choices.get(shape[0].charAt(0));
            check(outer.test(new ItemStack(Material.NETHERITE_INGOT))&&outer.test(new ItemStack(Material.NETHER_STAR))
                    &&!outer.test(new ItemStack(Material.NETHERITE_SCRAP)),"recipe accepts only either premium ingredient "+s.id());
            check(outer.getItemStack().getType()==Material.NETHERITE_INGOT,"recipe book has stable ingot preview "+s.id());
        }
        check(plugin.wands().spell(new ItemStack(Material.CARROT_ON_A_STICK))==null,"vanilla item cannot cast");
        check(!plugin.wands().migrate(new ItemStack(Material.CARROT_ON_A_STICK)),"migration leaves vanilla items alone");
        check(plugin.getConfig().getBoolean("resource-pack.enabled")&&plugin.getConfig().getBoolean("resource-pack.host.enabled"),"automatic packs enabled by default, including existing configs");
        for(String asset:List.of("advance-magic-java.zip","advance-magic-bedrock.mcpack","geyser-mappings.json","advance-magic-guide-th.png"))
            check(new java.io.File(plugin.getDataFolder(),"resource-packs/"+asset).length()>0,"embedded asset extracted: "+asset);
        plugin.getConfig().set("resource-pack.host.public-host","127.0.0.1");
        String packUrl=plugin.packs().url(player);
        check(packUrl.startsWith("http://127.0.0.1:"),"automatic pack public URL");
        try(var client=java.net.http.HttpClient.newHttpClient()) {
            var response=client.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(packUrl)).build(),java.net.http.HttpResponse.BodyHandlers.ofByteArray());
            check(response.statusCode()==200&&Arrays.equals(response.body(),java.nio.file.Files.readAllBytes(plugin.getDataFolder().toPath().resolve("resource-packs/advance-magic-java.zip"))),"running plugin serves exact embedded ZIP");
        }catch(Exception e){throw new RuntimeException(e);}
        plugin.getConfig().set("resource-pack.url","not-a-url");check(plugin.packs().url(player).isEmpty(),"invalid external URL is rejected");
        plugin.getConfig().set("resource-pack.url","");plugin.getConfig().set("resource-pack.host.public-host","::1");
        check(plugin.packs().url(player).startsWith("http://[::1]:"),"IPv6 pack URL is bracketed");
        plugin.getConfig().set("resource-pack.host.public-host","127.0.0.1");
        plugin.packs().offer(player);
        var account=plugin.mana().account(player);
        check(account.reserve(Spell.LIGHTNING_STRIKE.id(),60,8,System.currentTimeMillis()),"mana reservation");
        plugin.mana().quit(player);
        check(plugin.mana().account(player).mana()==40&&plugin.mana().account(player).remaining(Spell.LIGHTNING_STRIKE.id(),System.currentTimeMillis())>0,"PDC reload preserves mana and cooldown");
        target=w.spawn(new Location(w,0.5,100,6.5),Vindicator.class);target.setAI(false);target.setSilent(true);target.setGravity(false);
        target.getEquipment().clear();target.getAttribute(Attribute.MAX_HEALTH).setBaseValue(2000);target.setHealth(2000);
        target.getAttribute(Attribute.KNOCKBACK_RESISTANCE).setBaseValue(1);
        check(plugin.spells().cast(player,Spell.FROST_NOVA),"Frost Nova casts");
        check(target.getPotionEffect(PotionEffectType.SLOWNESS).getAmplifier()==3,"Frost Nova Slowness IV");
        later(3,()->{
            check(target.getFreezeTicks()>0,"visual freezing applied by ticker");
            check(plugin.spells().cast(player,Spell.NATURES_BLOOM),"Nature's Bloom casts");
            check(player.hasPotionEffect(PotionEffectType.REGENERATION)&&player.getPotionEffect(PotionEffectType.ABSORPTION).getAmplifier()==1,"caster receives Regen II and Absorption II");
            check(plugin.spells().cast(player,Spell.IRON_ARMOR)&&plugin.statuses().armored(player),"Iron Armor active");
            check(player.getPotionEffect(PotionEffectType.RESISTANCE).getAmplifier()==2,"Resistance III");
            check(plugin.spells().cast(player,Spell.SONIC_BOOM),"Sonic Boom casts");
            check(target.getPotionEffect(PotionEffectType.DARKNESS)!=null,"Sonic Boom applies darkness");
            reset();check(plugin.spells().cast(player,Spell.EARTH_WALL),"Earth Wall casts");
            check(w.getEntitiesByClass(FallingBlock.class).size()==15,"15 temporary wall visuals");
            check(w.getBlockAt(0,100,3).getType()==Material.AIR,"wall never replaces terrain");
            Arrow arrow=w.spawnArrow(new Location(w,0.5,101,1),new org.bukkit.util.Vector(0,0,1),4,0);
            later(3,()->{
                check(!arrow.isValid(),"wall intercepts moving arrow");
                reset();
                check(plugin.spells().cast(player,Spell.LIGHTNING_STRIKE),"Lightning targets entity");
                check(target.getHealth()<2000,"Lightning damage passes through real server damage pipeline");
                reset();check(plugin.spells().cast(player,Spell.DRAGONS_BREATH),"Dragon cloud launches");
                check(w.getEntitiesByClass(AreaEffectCloud.class).size()==1,"native AreaEffectCloud exists");
                later(30,this::projectiles);
            });
        });
    }
    void reset() {
        plugin.effects().closeOwner(player.getUniqueId());plugin.statuses().clear(player);
        player.teleport(new Location(player.getWorld(),0.5,100,0.5,0,0));
        player.setVelocity(new org.bukkit.util.Vector());
        for(var effect:player.getActivePotionEffects())player.removePotionEffect(effect.getType());
        for(var effect:target.getActivePotionEffects())target.removePotionEffect(effect.getType());
        target.setNoDamageTicks(0);target.setHealth(2000);target.setFireTicks(0);target.teleport(new Location(player.getWorld(),0.5,100,6.5));target.setVelocity(new org.bukkit.util.Vector());
    }
    void projectiles() {
        check(target.getHealth()<2000,"Dragon cloud deals continuous magic damage");
        reset();check(plugin.spells().cast(player,Spell.BLAZE_BARRAGE),"Blaze Barrage launches");
        later(16,()->{
            check(target.getFireTicks()>0,"blaze impact ignites target");
            reset();check(plugin.spells().cast(player,Spell.SHULKER_LEVITATION),"homing ShulkerBullet launches");
            later(40,()->{
                check(target.hasPotionEffect(PotionEffectType.LEVITATION)&&target.getPotionEffect(PotionEffectType.LEVITATION).getAmplifier()==1,"shulker impact applies Levitation II");
                reset();check(plugin.spells().cast(player,Spell.WITHER_RAY),"Wither Ray casts");
                later(25,()->{
                    check(target.hasPotionEffect(PotionEffectType.WITHER),"wither skull applies Wither II");
                    reset();check(plugin.spells().cast(player,Spell.VOID_PULL),"gravity orb launches");
                    later(50,()->{
                        check(target.hasPotionEffect(PotionEffectType.SLOWNESS),"gravity orb roots target");
                        reset();check(plugin.spells().cast(player,Spell.VEX_LEGION),"Vex Legion casts");
                        check(!player.getWorld().getEntitiesByClass(Vex.class).isEmpty(),"allied vex spirits spawned");
                        reset();player.setHealth(4);check(plugin.spells().cast(player,Spell.GUARDIAN_BEAM),"Guardian Beam channel starts");
                        later(32,this::finishSpells);
                    });
                });
            });
        });
    }
    void finishSpells() {
        check(target.getHealth()<2000,"Guardian Beam deals shock and tidal burst damage");
        check(player.hasPotionEffect(PotionEffectType.REGENERATION),"Guardian Beam rewards water surge regen");
        reset();World w=player.getWorld();
        for(int x=-1;x<=1;x++)for(int y=100;y<=102;y++)w.getBlockAt(x,y,3).setType(Material.STONE);
        check(plugin.spells().cast(player,Spell.SHADOW_STEP)&&player.getLocation().getZ()>4,"Shadow Step phases a one-block wall");
        check(plugin.context().safeBody(player.getLocation()),"blink destination has clear body space");
        for(int x=-1;x<=1;x++)for(int y=100;y<=102;y++)w.getBlockAt(x,y,3).setType(Material.AIR);
        reset();player.teleport(new Location(w,0.5,100,0.5,0,30));
        check(plugin.spells().cast(player,Spell.METEOR_STRIKE),"Meteor Strike marks ground");
        check(w.getEntitiesByClass(LargeFireball.class).isEmpty(),"meteor respects 1.5-second warning");
        later(32,()->{
            check(!w.getEntitiesByClass(LargeFireball.class).isEmpty(),"meteor drops native fireball after warning");
            later(25,()->{
                boolean fire=false;for(int x=-6;x<=6;x++)for(int z=-2;z<=10;z++)if(w.getBlockAt(x,100,z).getType()==Material.FIRE)fire=true;
                check(fire,"meteor ignites terrain");
                reset();check(plugin.effects().size()==0,"all temporary effects cleaned up");
                check(w.getEntitiesByClass(FallingBlock.class).isEmpty()&&w.getEntitiesByClass(AreaEffectCloud.class).isEmpty(),"temporary entities removed");
                getLogger().info("INTEGRATION COMPLETE: "+passed+" assertions passed");finish();
            });
        });
    }
    void finish(){
        Bukkit.getScheduler().cancelTasks(this);
        if(plugin!=null&&player!=null){plugin.effects().closeOwner(player.getUniqueId());plugin.statuses().clear(player);}
        if(target!=null)target.remove();
        if(handle!=null){MinecraftServer.getServer().getPlayerList().getPlayers().remove(handle);MinecraftServer.getServer().getPlayerList().getPlayersByUUID().remove(handle.getUUID());handle.discard();}
        Bukkit.getScheduler().runTaskLater(this,Bukkit::shutdown,5);
    }
}
