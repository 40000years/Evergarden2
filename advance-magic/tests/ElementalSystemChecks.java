import com.example.advancemagic.AdvanceMagicPlugin;
import com.example.advancemagic.api.ElementalCastEvent;
import com.example.advancemagic.api.MagicAffectEvent;
import com.example.advancemagic.elemental.ElementalSpell;
import com.example.advancemagic.elemental.ElementalStats;
import com.example.advancemagic.elemental.StaffSpellService.Tier;
import com.example.advancemagic.elemental.SkillTreeService.Node;
import com.example.advancemagic.item.ElementalStaffService.Element;
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
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import java.nio.file.*;
import java.util.*;

/** Real Paper entities, inventories, persistence, protection events, and scheduled spell effects. */
public final class ElementalSystemChecks extends JavaPlugin implements Listener {
    static final class Capture extends Connection {
        int particleCount,particlePackets;
        private static final java.lang.reflect.Method particleCounter=particleCounter();
        private static java.lang.reflect.Method particleCounter(){
            for(String accessor:List.of("getCount","count"))try{return ClientboundLevelParticlesPacket.class.getMethod(accessor);}
            catch(NoSuchMethodException ignored){}
            throw new IllegalStateException("Paper particle packet has no count accessor");
        }
        Capture(){super(PacketFlow.SERVERBOUND);channel=new EmbeddedChannel();address=new java.net.InetSocketAddress("127.0.0.1",1);}
        void capture(Packet<?> packet){if(packet instanceof ClientboundLevelParticlesPacket particles){
            try{particleCount+=Math.max(1,((Number)particleCounter.invoke(particles)).intValue());particlePackets++;}
            catch(ReflectiveOperationException failure){throw new IllegalStateException("Cannot inspect Paper particle packet count",failure);}
        }}
        @Override public void send(Packet<?> packet){capture(packet);}
        @Override public void send(Packet<?> packet,ChannelFutureListener listener){capture(packet);}
        @Override public void send(Packet<?> packet,ChannelFutureListener listener,boolean flush){capture(packet);}
        @Override public boolean isConnected(){return true;}
    }
    int passed,castEvents;
    boolean cancelCast,cancelAffect,cancelDamage,cancelHealing;
    long simulatedTick;
    final List<ServerPlayer> actors=new ArrayList<>();
    final Map<UUID,Capture> captures=new HashMap<>();
    final List<LivingEntity> mobs=new ArrayList<>();
    final Map<UUID,List<Double>> damage=new HashMap<>();
    final Map<UUID,List<Long>> damageTimes=new HashMap<>();
    AdvanceMagicPlugin magic;
    World world;
    Player caster;
    void check(boolean condition,String label){if(!condition)throw new AssertionError(label);passed++;getLogger().info("PASS "+label);}
    void near(double actual,double expected,String label){check(Math.abs(actual-expected)<.0001,label+" actual="+actual+" expected="+expected);}
    @Override public void onEnable(){Bukkit.getPluginManager().registerEvents(this,this);Bukkit.getScheduler().runTaskLater(this,()->{try{run();gui();}catch(Throwable error){finish(error);}},20);}
    @EventHandler(priority=EventPriority.HIGHEST) public void cast(ElementalCastEvent event){castEvents++;if(cancelCast)event.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST) public void affect(MagicAffectEvent event){if(cancelAffect)event.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST) public void damageGuard(EntityDamageEvent event){if(cancelDamage)event.setCancelled(true);}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void damage(EntityDamageByEntityEvent event){damage.computeIfAbsent(event.getEntity().getUniqueId(),ignored->new ArrayList<>()).add(event.getDamage());damageTimes.computeIfAbsent(event.getEntity().getUniqueId(),ignored->new ArrayList<>()).add(simulatedTick);}
    @EventHandler(priority=EventPriority.HIGHEST) public void healGuard(EntityRegainHealthEvent event){if(cancelHealing)event.setCancelled(true);}
    Player actor(String name,Location at){
        var server=MinecraftServer.getServer();var level=((CraftWorld)world).getHandle();
        var profile=new GameProfile(UUID.randomUUID(),name);var handle=new ServerPlayer(server,level,profile,ClientInformation.createDefault());
        Capture capture=new Capture();captures.put(profile.id(),capture);
        handle.connection=new ServerGamePacketListenerImpl(server,capture,handle,CommonListenerCookie.createInitial(profile,false)){
            @Override public boolean hasClientLoaded(){return true;}
        };
        handle.setPos(at.getX(),at.getY(),at.getZ());server.getPlayerList().getPlayers().add(handle);server.getPlayerList().getPlayersByUUID().put(profile.id(),handle);level.addNewPlayer(handle);actors.add(handle);
        Player player=handle.getBukkitEntity();player.setOp(true);player.setGameMode(GameMode.SURVIVAL);player.setGravity(false);player.teleport(at);
        player.getAttribute(Attribute.MAX_HEALTH).setBaseValue(1000);player.setHealth(1000);player.setMaximumNoDamageTicks(0);return player;
    }
    LivingEntity mob(double x,double z){
        var mob=world.spawn(new Location(world,x,100,z),Vindicator.class);mob.setAI(false);mob.setGravity(false);
        mob.getAttribute(Attribute.MAX_HEALTH).setBaseValue(1000);mob.setHealth(1000);mob.setMaximumNoDamageTicks(0);mobs.add(mob);return mob;
    }
    double total(LivingEntity target){return damage.getOrDefault(target.getUniqueId(),List.of()).stream().mapToDouble(Double::doubleValue).sum();}
    void ticks(int count){for(int i=0;i<count;i++){simulatedTick++;magic.effects().tick();magic.elemental().tick();}}
    void run() throws Exception {
        magic=(AdvanceMagicPlugin)Bukkit.getPluginManager().getPlugin("advance-magic");
        check(magic!=null&&magic.isEnabled(),"production plugin boots on actual Paper");
        check(magic.staffSpells()!=null&&magic.elemental()!=null&&magic.elementalStats()!=null,"elemental services installed");
        world=Bukkit.getWorlds().getFirst();world.setPVP(true);
        for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)world.getChunkAt(x,z).setForceLoaded(true);
        for(int x=-24;x<=24;x++)for(int z=-24;z<=24;z++)world.getBlockAt(x,99,z).setType(Material.STONE);
        caster=actor("ElementCaster",new Location(world,.5,100,.5,0,0));
        catalog();stats();items();crafting();accounting();visuals();nativeVisuals();affinities();spells();policies();reactions();phoenix();
    }
    void crafting(){
        for(Element element:Element.values()){
            NamespacedKey key=new NamespacedKey(magic,"elemental_staff_"+element.id());
            check(Bukkit.getRecipe(key)==null,"loot-only staff recipe absent "+element.id());
            // Simulate an old recipe already installed and discovered before a hot reload.
            var stale=new ShapedRecipe(key,magic.staffSpells().createStaff(element,Tier.APPRENTICE));
            stale.shape("ACA"," S "," S ");stale.setIngredient('A',Material.AMETHYST_SHARD);
            stale.setIngredient('C',Material.DIRT);stale.setIngredient('S',Material.STICK);
            check(Bukkit.addRecipe(stale)&&caster.discoverRecipe(key)&&caster.hasDiscoveredRecipe(key),"stale reload fixture installs a discovered staff recipe "+element.id());
        }
        magic.acquisition().register();
        for(Element element:Element.values()){
            NamespacedKey key=new NamespacedKey(magic,"elemental_staff_"+element.id());
            check(Bukkit.getRecipe(key)==null,"reload removes stale staff recipe "+element.id());
            check(!caster.hasDiscoveredRecipe(key),"reload forgets stale staff recipe-book entry "+element.id());
            Material catalyst=switch(element){case FIRE->Material.BLAZE_POWDER;case WIND->Material.FEATHER;case EARTH->Material.CLAY_BALL;
                case ICE->Material.SNOWBALL;case WATER->Material.PRISMARINE_SHARD;case LIGHTNING->Material.COPPER_INGOT;
                case LIGHT->Material.GLOWSTONE_DUST;case DARK->Material.ENDER_PEARL;};
            ItemStack[] matrix=new ItemStack[9];matrix[0]=new ItemStack(Material.AMETHYST_SHARD);matrix[1]=new ItemStack(catalyst);
            matrix[2]=new ItemStack(Material.AMETHYST_SHARD);matrix[4]=new ItemStack(Material.STICK);matrix[7]=new ItemStack(Material.STICK);
            check(Bukkit.craftItemResult(matrix,world,caster).getResult().getType().isAir(),"old survival pattern yields no staff "+element.id());
        }
        NamespacedKey woodenKey=new NamespacedKey(magic,"wooden_staff");Recipe woodenRecipe=Bukkit.getRecipe(woodenKey);
        check(woodenRecipe instanceof ShapedRecipe&&magic.staffs().isWooden(woodenRecipe.getResult())
                &&magic.staffSpells().tier(woodenRecipe.getResult())==Tier.WOODEN,"registered survival recipe makes only the one-slot Rare-capped neutral wood staff");
        ItemStack[] woodenMatrix=new ItemStack[9];woodenMatrix[1]=new ItemStack(Material.AMETHYST_SHARD);
        woodenMatrix[4]=new ItemStack(Material.BLAZE_ROD);woodenMatrix[7]=new ItemStack(Material.BLAZE_ROD);
        ItemStack woodenResult=Bukkit.craftItemResult(woodenMatrix,world,caster).getResult();
        check(magic.staffs().isWooden(woodenResult)&&magic.staffSpells().tier(woodenResult)==Tier.WOODEN
                &&magic.staffSpells().spells(woodenResult).isEmpty(),"actual survival vertical amethyst and two Blaze Rod recipe produces usable empty wood staff");
        ItemStack secondWooden=Bukkit.craftItemResult(woodenMatrix,world,caster).getResult();
        magic.staffSpells().migrate(woodenResult);magic.staffSpells().migrate(secondWooden);
        NamespacedKey staffId=new NamespacedKey("advance_magic","spell_staff_id");
        String firstId=woodenResult.getItemMeta().getPersistentDataContainer().get(staffId,PersistentDataType.STRING);
        String secondId=secondWooden.getItemMeta().getPersistentDataContainer().get(staffId,PersistentDataType.STRING);
        check(firstId!=null&&secondId!=null&&!firstId.equals(secondId),"separately crafted wood staffs receive distinct persistent IDs on inventory migration");
        for(int replaced:List.of(1,4,7)){
            ItemStack[] wrong=Arrays.stream(woodenMatrix).map(item->item==null?null:item.clone()).toArray(ItemStack[]::new);wrong[replaced]=new ItemStack(Material.STICK);
            check(Bukkit.craftItemResult(wrong,world,caster).getResult().getType().isAir(),"wood recipe rejects wrong ingredient in occupied slot "+replaced);
        }
        for(int named:List.of(1,4,7)){
            ItemStack[] wrong=Arrays.stream(woodenMatrix).map(item->item==null?null:item.clone()).toArray(ItemStack[]::new);
            var custom=wrong[named].getItemMeta();custom.setDisplayName("Custom ingredient cannot be consumed");wrong[named].setItemMeta(custom);
            check(Bukkit.craftItemResult(wrong,world,caster).getResult().getType().isAir(),"wood recipe requires plain ingredient in occupied slot "+named);
        }
        InventoryView view=caster.openWorkbench(caster.getLocation(),true);
        check(view!=null&&view.getTopInventory() instanceof CraftingInventory,"native workbench opens for loot-only crafting guards");
        CraftingInventory inventory=(CraftingInventory)view.getTopInventory();
        ItemStack staff=magic.staffSpells().createStaff(Element.LIGHTNING,Tier.APPRENTICE);
        var alias=new ShapelessRecipe(new NamespacedKey(this,"staff_alias"),staff);alias.addIngredient(Material.DIRT);
        inventory.setMatrix(new ItemStack[]{new ItemStack(Material.DIRT),null,null,null,null,null,null,null,null});
        inventory.setResult(staff);Bukkit.getPluginManager().callEvent(new PrepareItemCraftEvent(inventory,view,false));
        check(inventory.getResult()==null||inventory.getResult().getType().isAir(),"prepare rejects staff output from a foreign alias");
        for(ClickType click:List.of(ClickType.LEFT,ClickType.SHIFT_LEFT,ClickType.NUMBER_KEY)){
            CraftItemEvent event=click==ClickType.NUMBER_KEY
                    ?new CraftItemEvent(alias,view,InventoryType.SlotType.RESULT,0,click,InventoryAction.HOTBAR_SWAP,2)
                    :new CraftItemEvent(alias,view,InventoryType.SlotType.RESULT,0,click,InventoryAction.PICKUP_ALL);
            Bukkit.getPluginManager().callEvent(event);
            check(event.isCancelled(),"craft-result guard rejects stale staff alias "+click);
        }
        inventory.setResult(staff);
        var click=new InventoryClickEvent(view,InventoryType.SlotType.RESULT,0,ClickType.LEFT,InventoryAction.PICKUP_ALL);
        Bukkit.getPluginManager().callEvent(click);check(click.isCancelled(),"generic result click cannot take an injected staff");
        var block=world.getBlockAt(10,100,10);block.setType(Material.CRAFTER);
        var automatic=new org.bukkit.event.block.CrafterCraftEvent(block,alias,staff);
        Bukkit.getPluginManager().callEvent(automatic);check(automatic.isCancelled(),"redstone Crafter cannot create a staff through an alias");
        var woodAlias=new ShapelessRecipe(new NamespacedKey(this,"wood_staff_alias"),woodenResult);woodAlias.addIngredient(Material.DIRT);
        automatic=new org.bukkit.event.block.CrafterCraftEvent(block,woodAlias,woodenResult);
        Bukkit.getPluginManager().callEvent(automatic);check(automatic.isCancelled(),"redstone Crafter cannot create wood staff through a foreign alias");
        automatic=new org.bukkit.event.block.CrafterCraftEvent(block,(CraftingRecipe)woodenRecipe,woodenResult);
        Bukkit.getPluginManager().callEvent(automatic);check(!automatic.isCancelled(),"redstone Crafter may use canonical wood staff recipe");
        inventory.setResult(woodenResult);Bukkit.getPluginManager().callEvent(new PrepareItemCraftEvent(inventory,view,false));
        check(inventory.getResult()==null||inventory.getResult().getType().isAir(),"prepare guard rejects injected wood staff without canonical ingredient pattern");
        inventory.setResult(woodenResult);
        for(ClickType type:List.of(ClickType.LEFT,ClickType.SHIFT_LEFT,ClickType.NUMBER_KEY)){
            CraftItemEvent forged=type==ClickType.NUMBER_KEY
                    ?new CraftItemEvent(woodAlias,view,InventoryType.SlotType.RESULT,0,type,InventoryAction.HOTBAR_SWAP,2)
                    :new CraftItemEvent(woodAlias,view,InventoryType.SlotType.RESULT,0,type,InventoryAction.PICKUP_ALL);
            Bukkit.getPluginManager().callEvent(forged);check(forged.isCancelled(),"craft result rejects wood staff alias "+type);
        }
        ItemStack legacy=new ItemStack(Material.CARROT_ON_A_STICK);var legacyMeta=legacy.getItemMeta();
        legacyMeta.getPersistentDataContainer().set(new NamespacedKey(magic,"wand"),PersistentDataType.STRING,"frost_nova");legacy.setItemMeta(legacyMeta);
        var legacyAlias=new ShapelessRecipe(new NamespacedKey(this,"legacy_staff_alias"),legacy);legacyAlias.addIngredient(Material.DIRT);
        automatic=new org.bukkit.event.block.CrafterCraftEvent(block,legacyAlias,legacy);Bukkit.getPluginManager().callEvent(automatic);
        check(automatic.isCancelled(),"Crafter cannot create a legacy wand that would migrate to a staff");
        ItemStack flying=magic.flyingStaff().create();var flyingAlias=new ShapelessRecipe(new NamespacedKey(this,"utility_alias"),flying);flyingAlias.addIngredient(Material.DIRT);
        automatic=new org.bukkit.event.block.CrafterCraftEvent(block,flyingAlias,flying);Bukkit.getPluginManager().callEvent(automatic);
        check(!automatic.isCancelled(),"loot-only elemental guard leaves flying utility staff outputs alone");
        var sticks=new ShapelessRecipe(new NamespacedKey(this,"vanilla_output_alias"),new ItemStack(Material.STICK,4));sticks.addIngredient(Material.DIRT);
        automatic=new org.bukkit.event.block.CrafterCraftEvent(block,sticks,sticks.getResult());Bukkit.getPluginManager().callEvent(automatic);
        check(!automatic.isCancelled(),"loot-only elemental guard leaves unrelated automated crafting alone");
        inventory.clear();caster.closeInventory();block.setType(Material.AIR);
    }
    void visuals() throws Exception {
        magic.getConfig().set("elemental-visuals.style","authored");
        try{authoredVisuals();}
        finally{magic.getConfig().set("elemental-visuals.style","native");}
    }
    @SuppressWarnings("unchecked") void authoredVisuals() throws Exception {
        reset();
        var field=magic.packs().getClass().getDeclaredField("statuses");field.setAccessible(true);
        var applied=(Map<UUID,String>)field.get(magic.packs());applied.put(caster.getUniqueId(),"SUCCESSFULLY_LOADED");
        int combatEffects=magic.effects().size();double mana=magic.mana().account(caster).manaExact();
        for(var spell:ElementalSpell.values()){
            var renderer=new com.example.advancemagic.spell.ElementalVfx(magic.context());
            var scene=renderer.begin(caster,spell);scene.startup();scene.area(caster.getLocation().add(0,0,3),2);
            // The eight Legendary scenes draw their own spectacle instead of the shared spell icon.
            if(!LEGENDARY_SCENES.contains(spell))check(world.getEntitiesByClass(ItemDisplay.class).stream().anyMatch(d->d.getItemStack().hasItemMeta()
                    &&new NamespacedKey("advance_magic","elemental_"+spell.id()).equals(d.getItemStack().getItemMeta().getItemModel())),"authored Java scene resolves model for "+spell.id());
            check(magic.effects().size()==combatEffects&&magic.mana().account(caster).manaExact()==mana,"cosmetic scene cannot consume combat slots or mana "+spell.id());
            renderer.close();check(world.getEntitiesByClass(ItemDisplay.class).isEmpty(),"cosmetic scene removes every display "+spell.id());
        }
        java.util.function.Predicate<String> hasModel=id->world.getEntitiesByClass(ItemDisplay.class).stream().anyMatch(d->d.getItemStack().hasItemMeta()
                &&new NamespacedKey("advance_magic",id).equals(d.getItemStack().getItemMeta().getItemModel()));
        java.util.function.Supplier<Set<UUID>> displayIds=()->{
            Set<UUID> ids=new HashSet<>();for(ItemDisplay display:world.getEntitiesByClass(ItemDisplay.class))ids.add(display.getUniqueId());return ids;
        };
        Location stage=caster.getLocation().add(0,0,4);
        int ultimateHeroes=0;
        for(var spell:ElementalSpell.values())if(spell.ultimate()){
            var heroRenderer=new com.example.advancemagic.spell.ElementalVfx(magic.context());
            var scene=heroRenderer.begin(caster,spell);
            switch(spell){
                case METEOR_RAIN->scene.telegraph(stage,6,48,50);
                case PHOENIX_REBIRTH->scene.phoenix();
                case TSUNAMI->scene.wave(stage,new org.bukkit.util.Vector(0,0,1),5,0);
                case LEVIATHANS_WRATH->scene.wave(stage,new org.bukkit.util.Vector(0,0,1),4,0);
                case ABSOLUTE_ZERO->scene.area(stage,6);
                case TEMPEST_DOMINION->scene.field(stage,7,0,120);
                case TITANS_JUDGMENT->scene.pillar(stage,8);
                case HEAVENS_DESCENT->scene.lance(caster.getEyeLocation().add(0,0,2),stage.clone().add(0,1,8),new org.bukkit.util.Vector(0,0,1),0,24,120);
                case ABYSSAL_COLLAPSE->scene.pillar(stage,4);
                case WRATH_OF_RAIJIN->scene.storm(stage,0);
                default->throw new AssertionError("Ultimate visual fixture missing for "+spell.id());
            }
            if(LEGENDARY_SCENES.contains(spell))check(!world.getEntitiesByClass(ItemDisplay.class).isEmpty(),"actual Legendary scene hook shows its spectacle "+spell.id());
            else check(hasModel.test("elemental_rare_"+spell.id()),"actual Ultimate scene hook shows bespoke hero "+spell.id());
            check(world.getEntitiesByClass(ItemDisplay.class).size()>=2,"Ultimate hero has a separate visual layer "+spell.id());
            check(magic.effects().size()==combatEffects&&magic.mana().account(caster).manaExact()==mana,"Ultimate presentation preserves combat slots and mana "+spell.id());
            heroRenderer.close();check(world.getEntitiesByClass(ItemDisplay.class).isEmpty(),"Ultimate hero and its layers clean up "+spell.id());ultimateHeroes++;
        }
        check(ultimateHeroes==10,"every one of the ten Ultimate visual hooks is covered");

        List<LivingEntity> pressureTargets=new ArrayList<>();
        for(int i=0;i<10;i++)pressureTargets.add(mob(-3+i%5,3+i/5));
        var pressureRenderer=new com.example.advancemagic.spell.ElementalVfx(magic.context());
        var epic=pressureRenderer.begin(caster,ElementalSpell.SCORCHING_VORTEX);
        for(LivingEntity target:pressureTargets){epic.status(target,"burn",1,80,()->true);epic.impact(target);}
        check(world.getEntitiesByClass(ItemDisplay.class).size()==8,"EPIC status pressure reaches the eight-display scene limit");
        epic.field(stage,4,0,100);
        check(hasModel.test("elemental_rare_fire_plume")&&hasModel.test("elemental_rare_fire_ground"),"EPIC field silhouette displaces status decorations under pressure");
        for(int age=1;age<=8;age++){pressureRenderer.tick();epic.field(stage,4,age,100);}
        check(hasModel.test("elemental_rare_fire_plume")&&world.getEntitiesByClass(ItemDisplay.class).size()<=8,"status redraw cannot evict the EPIC hero or exceed its scene cap");
        pressureRenderer.close();check(world.getEntitiesByClass(ItemDisplay.class).isEmpty(),"EPIC pressure scene releases heroes and retained markers");

        var movingRenderer=new com.example.advancemagic.spell.ElementalVfx(magic.context());
        var moving=movingRenderer.begin(caster,ElementalSpell.SCORCHING_VORTEX);moving.field(stage,4,0,100);
        Set<UUID> initialFieldDisplays=displayIds.get();boolean fieldReused=true;
        for(int age=1;age<=24;age++){
            movingRenderer.tick();moving.field(stage.clone().add(age*.025,0,0),4,age,100);
            fieldReused&=displayIds.get().equals(initialFieldDisplays);
        }
        check(initialFieldDisplays.size()>=4&&fieldReused,"moving EPIC field refreshes reuse every display instead of accumulating entities");
        movingRenderer.close();check(world.getEntitiesByClass(ItemDisplay.class).isEmpty(),"refreshed moving field removes every display on close");

        for(var entry:Map.of(ElementalSpell.METEOR_RAIN,50,ElementalSpell.MAGMA_ERUPTION,16,ElementalSpell.TITANS_JUDGMENT,40,
                ElementalSpell.DIVINE_JUDGMENT,12,ElementalSpell.ABYSSAL_COLLAPSE,30,ElementalSpell.WRATH_OF_RAIJIN,30).entrySet()){
            var channelRenderer=new com.example.advancemagic.spell.ElementalVfx(magic.context());var scene=channelRenderer.begin(caster,entry.getKey());
            scene.telegraph(stage,6,entry.getValue()-2,entry.getValue());scene.boundary(stage,6);
            check(!world.getEntitiesByClass(ItemDisplay.class).isEmpty(),"late telegraph has visible authored layers "+entry.getKey().id());
            scene.clear("telegraph");scene.clear("boundary");for(int i=0;i<8;i++)channelRenderer.tick();
            check(world.getEntitiesByClass(ItemDisplay.class).isEmpty(),"interrupted telegraph removes hero and trims without a finish effect "+entry.getKey().id());channelRenderer.close();
        }
        magic.getConfig().set("elemental-visuals.max-planes-per-world",16);
        var renderer=new com.example.advancemagic.spell.ElementalVfx(magic.context());
        for(int i=0;i<20;i++)renderer.begin(caster,ElementalSpell.FIREBALL).startup();
        check(world.getEntitiesByClass(ItemDisplay.class).size()<=16,"world VFX cap bounds display allocation under simultaneous scenes");
        renderer.close();check(world.getEntitiesByClass(ItemDisplay.class).isEmpty(),"world-cap fallback does not leak displays");
        renderer=new com.example.advancemagic.spell.ElementalVfx(magic.context());
        for(int i=0;i<16;i++)renderer.begin(caster,ElementalSpell.FIREBALL).status(pressureTargets.get(i%10),i<10?"burn":"wet",1,80,()->true);
        check(world.getEntitiesByClass(ItemDisplay.class).size()==16,"world display cap is filled with distinct status decorations");
        Set<UUID> decorationIds=displayIds.get();renderer.begin(caster,ElementalSpell.PHOENIX_REBIRTH).phoenix();
        check(hasModel.test("elemental_rare_phoenix_rebirth")&&world.getEntitiesByClass(ItemDisplay.class).size()<=16,"Ultimate hero evicts world-cap status decoration while preserving the hard limit");
        check(!displayIds.get().containsAll(decorationIds),"hero priority retires an existing decoration instead of exceeding world capacity");
        for(int i=0;i<8;i++)renderer.tick();
        check(hasModel.test("elemental_rare_phoenix_rebirth")&&world.getEntitiesByClass(ItemDisplay.class).size()<=16,"status redraw cannot displace the Ultimate hero at the world cap");
        renderer.close();check(world.getEntitiesByClass(ItemDisplay.class).isEmpty(),"world priority eviction leaves no displays or orphaned heroes");
        magic.getConfig().set("elemental-visuals.max-planes-per-world",128);
        magic.getConfig().set("elemental-visuals.quality","low");
        // Legendary spectacles keep up to ten 3D pieces on low (LegendarySpectacleChecks); ordinary spells use none.
        renderer=new com.example.advancemagic.spell.ElementalVfx(magic.context());renderer.begin(caster,ElementalSpell.MAGMA_ERUPTION).startup();
        check(world.getEntitiesByClass(ItemDisplay.class).isEmpty(),"Low quality uses particle silhouettes without display entities");renderer.close();
        magic.getConfig().set("elemental-visuals.quality","balanced");
        applied.remove(caster.getUniqueId());renderer=new com.example.advancemagic.spell.ElementalVfx(magic.context());renderer.begin(caster,ElementalSpell.FIREBALL).startup();
        check(world.getEntitiesByClass(ItemDisplay.class).isEmpty(),"unloaded Java pack receives fallback without missing-texture displays");renderer.close();
        applied.put(caster.getUniqueId(),"SUCCESSFULLY_LOADED");reset();
    }
    List<Projectile> nativeBodies(){
        NamespacedKey marker=new NamespacedKey(magic,"native_spell_visual");
        return world.getEntitiesByClass(Projectile.class).stream().filter(e->e.getPersistentDataContainer().has(marker)).toList();
    }
    <T extends Projectile> T nativeBody(Class<T> type){
        return nativeBodies().stream().filter(type::isInstance).map(type::cast).findFirst()
                .orElseThrow(()->new AssertionError("Missing native "+type.getSimpleName()+" visual"));
    }
    void nativeVisuals() throws Exception {
        reset();
        int combatEffects=magic.effects().size();double mana=magic.mana().account(caster).manaExact();
        LivingEntity target=mob(.5,4.5);Location stage=new Location(world,.5,100,4.5);
        var forward=new org.bukkit.util.Vector(0,0,1);
        for(var spell:ElementalSpell.values()){
            var renderer=new com.example.advancemagic.spell.ElementalVfx(magic.context());
            try{
                var scene=renderer.begin(caster,spell);scene.startup();scene.area(stage,2);
                scene.projectile(caster.getEyeLocation(),stage.clone().add(0,1,0));
                scene.beam(stage.clone().add(0,4,0),stage);scene.cone(stage,forward,3,.5);scene.boundary(stage,3);
                scene.impact(target);scene.heal(caster);scene.field(stage,3,0,80);scene.telegraph(stage,3,48,50);
                scene.meteors(stage,0);scene.pillar(stage,4);scene.wave(stage,forward,3,0);scene.storm(stage,0);
                scene.status(target,"burn",1,40,()->true);scene.cage(target,40,()->true);
                scene.buff(caster,"iron_skin",40,()->true);scene.summon(target,0);
                scene.wall(org.bukkit.util.BoundingBox.of(stage,1,1,1));scene.phoenix();
                for(int tick=0;tick<6;tick++)renderer.tick();
                check(scene.enabled(),"native scene accepts every visual hook "+spell.id());
                // The eight Legendary spectacles draw their 3D art in either style, by design.
                if(!LEGENDARY_SCENES.contains(spell))check(world.getEntitiesByClass(ItemDisplay.class).isEmpty(),"native spell never creates authored image displays "+spell.id());
                check(magic.effects().size()==combatEffects&&magic.mana().account(caster).manaExact()==mana,
                        "native presentation consumes no combat slots or mana "+spell.id());
                near(target.getHealth(),1000,"native visual hooks cannot damage a nearby target "+spell.id());
                scene.close();
                check(nativeBodies().isEmpty()&&world.getEntitiesByClass(BlockDisplay.class).isEmpty(),
                        "closing native scene removes projectile bodies and block fragments "+spell.id());
            }finally{renderer.close();}
        }

        var renderer=new com.example.advancemagic.spell.ElementalVfx(magic.context());
        try{
            var scene=renderer.begin(caster,ElementalSpell.FIREBALL);
            Location from=caster.getEyeLocation().add(0,0,1),to=from.clone().add(0,0,.7);
            scene.projectile(from,to);SmallFireball fireball=nativeBody(SmallFireball.class);
            check(nativeBodies().size()==1&&fireball.getShooter()==caster&&!fireball.isPersistent()
                    &&!fireball.isIncendiary()&&fireball.getYield()==0,"fire spell uses one owned nonpersistent harmless Blaze projectile");
            UUID bodyId=fireball.getUniqueId();
            for(int age=1;age<=8;age++){
                renderer.tick();scene.projectile(from.clone().add(0,0,age*.7),to.clone().add(0,0,age*.7));
            }
            check(nativeBodies().size()==1&&nativeBody(SmallFireball.class).getUniqueId().equals(bodyId)
                    &&fireball.getLocation().distanceSquared(from)>4,"fireball motion reuses the actual native body");
            var hit=new ProjectileHitEvent(fireball,target);Bukkit.getPluginManager().callEvent(hit);
            check(hit.isCancelled(),"native projectile collision cannot resolve a second combat hit");
            var damageEvent=new EntityDamageByEntityEvent(fireball,target,EntityDamageEvent.DamageCause.PROJECTILE,20);
            Bukkit.getPluginManager().callEvent(damageEvent);check(damageEvent.isCancelled(),"native fireball damage event is suppressed");
            var deflect=new EntityDamageByEntityEvent(caster,fireball,EntityDamageEvent.DamageCause.ENTITY_ATTACK,1);
            Bukkit.getPluginManager().callEvent(deflect);
            check(deflect.isCancelled()&&fireball.getShooter()==caster,"players cannot deflect native spell visuals or change their owner");
            var damageSource=org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.FIREBALL)
                    .withCausingEntity(caster).withDirectEntity(fireball).build();
            double health=target.getHealth();target.damage(20,damageSource);
            near(target.getHealth(),health,"actual native fireball damage cannot reduce target health");
            var combust=new EntityCombustByEntityEvent(fireball,target,8);Bukkit.getPluginManager().callEvent(combust);
            check(combust.isCancelled(),"native visual cannot ignite a living target");
            var ignite=new org.bukkit.event.block.BlockIgniteEvent(world.getBlockAt(0,100,4),
                    org.bukkit.event.block.BlockIgniteEvent.IgniteCause.FIREBALL,fireball);
            Bukkit.getPluginManager().callEvent(ignite);check(ignite.isCancelled(),"native fireball cannot ignite world blocks");
            var prime=new ExplosionPrimeEvent(fireball,4,true);Bukkit.getPluginManager().callEvent(prime);
            check(prime.isCancelled(),"native fireball cannot prime a vanilla explosion");
            var explode=new EntityExplodeEvent(fireball,stage,new ArrayList<>(List.of(world.getBlockAt(0,99,4))),1,ExplosionResult.DESTROY);
            Bukkit.getPluginManager().callEvent(explode);
            check(explode.isCancelled()&&explode.blockList().isEmpty(),"native explosion has no block damage or drops");
            scene.clear("projectile");check(nativeBodies().isEmpty(),"projectile clear immediately retires the native fireball");

            SmallFireball vanilla=world.spawn(stage.clone().add(4,2,0),SmallFireball.class);
            try{
                var vanillaHit=new ProjectileHitEvent(vanilla,target);Bukkit.getPluginManager().callEvent(vanillaHit);
                var vanillaDamage=new EntityDamageByEntityEvent(vanilla,target,EntityDamageEvent.DamageCause.PROJECTILE,2);
                Bukkit.getPluginManager().callEvent(vanillaDamage);
                var vanillaIgnite=new org.bukkit.event.block.BlockIgniteEvent(world.getBlockAt(0,100,4),
                        org.bukkit.event.block.BlockIgniteEvent.IgniteCause.FIREBALL,vanilla);
                Bukkit.getPluginManager().callEvent(vanillaIgnite);
                var vanillaPrime=new ExplosionPrimeEvent(vanilla,1,true);Bukkit.getPluginManager().callEvent(vanillaPrime);
                check(!vanillaHit.isCancelled()&&!vanillaDamage.isCancelled()&&!vanillaIgnite.isCancelled()&&!vanillaPrime.isCancelled(),
                        "native visual guards preserve unrelated vanilla fireball events");
            }finally{vanilla.remove();}
        }finally{renderer.close();}

        renderer=new com.example.advancemagic.spell.ElementalVfx(magic.context());
        try{
            // Since 1.27.1 Meteor Rain is a 3D spectacle (LegendarySpectacleChecks): no native fireball bodies.
            var meteor=renderer.begin(caster,ElementalSpell.METEOR_RAIN);
            for(int age=38;age<50;age++){renderer.tick();meteor.telegraph(stage,6,age,50);}
            meteor.meteors(stage,0);for(int step=1;step<10;step++)renderer.tick();
            check(nativeBodies().isEmpty(),"the Meteor Rain spectacle draws 3D rocks instead of native fireball bodies");
            meteor.close();
        }finally{renderer.close();}

        Object displayCap=magic.getConfig().get("elemental-visuals.max-native-displays-per-world");
        try{
            magic.getConfig().set("elemental-visuals.max-native-displays-per-world",8);
            renderer=new com.example.advancemagic.spell.ElementalVfx(magic.context());
            try{
                for(int i=0;i<20;i++)renderer.begin(caster,ElementalSpell.EARTH_SPIKE).area(stage,3);
                check(world.getEntitiesByClass(BlockDisplay.class).size()==8,"native block fragments respect the configured world display cap");
                renderer.closeOwner(caster.getUniqueId());
                check(world.getEntitiesByClass(BlockDisplay.class).isEmpty(),"native owner cleanup releases every capped block fragment");
                check(world.getBlockAt(0,99,4).getType()==Material.STONE&&world.getBlockAt(0,100,4).getType()==Material.AIR,
                        "native block fragments never replace real world blocks");
            }finally{renderer.close();}
            magic.getConfig().set("elemental-visuals.max-native-displays-per-world",0);
            renderer=new com.example.advancemagic.spell.ElementalVfx(magic.context());
            try{
                renderer.begin(caster,ElementalSpell.EARTH_SPIKE).area(stage,3);
                check(world.getEntitiesByClass(BlockDisplay.class).isEmpty(),"zero native display cap supports particle-only spell rendering");
            }finally{renderer.close();}
        }finally{magic.getConfig().set("elemental-visuals.max-native-displays-per-world",displayCap);}

        String[] budgetKeys={"max-native-particles-per-world-tick","max-native-particles-per-viewer-tick","max-native-packets-per-viewer-tick"};
        Object[] budgets=Arrays.stream(budgetKeys).map(key->magic.getConfig().get("elemental-visuals."+key)).toArray();
        Capture capture=captures.get(caster.getUniqueId());
        try{
            magic.getConfig().set("elemental-visuals."+budgetKeys[0],64);
            magic.getConfig().set("elemental-visuals."+budgetKeys[1],2000);
            magic.getConfig().set("elemental-visuals."+budgetKeys[2],512);
            capture.particleCount=capture.particlePackets=0;
            renderer=new com.example.advancemagic.spell.ElementalVfx(magic.context());
            try{
                for(int i=0;i<24;i++)renderer.begin(caster,ElementalSpell.MAGMA_ERUPTION).area(stage,6);
                check(capture.particleCount>0&&capture.particleCount<=64,"native world particle budget bounds actual client particle packets");
                int spent=capture.particleCount;for(int i=0;i<6;i++)renderer.tick();
                check(capture.particleCount==spent,"manual scene updates cannot bypass the shared Bukkit tick particle budget");
            }finally{renderer.close();}
            magic.getConfig().set("elemental-visuals."+budgetKeys[0],8000);
            magic.getConfig().set("elemental-visuals."+budgetKeys[1],24);
            magic.getConfig().set("elemental-visuals."+budgetKeys[2],16);
            capture.particleCount=capture.particlePackets=0;
            renderer=new com.example.advancemagic.spell.ElementalVfx(magic.context());
            try{
                for(int i=0;i<24;i++)renderer.begin(caster,ElementalSpell.MAGMA_ERUPTION).area(stage,6);
                check(capture.particleCount>0&&capture.particleCount<=24&&capture.particlePackets<=16,
                        "native per-viewer particle and packet budgets bound actual client traffic");
            }finally{renderer.close();}
        }finally{for(int i=0;i<budgetKeys.length;i++)magic.getConfig().set("elemental-visuals."+budgetKeys[i],budgets[i]);}

        Object projectileCap=magic.getConfig().get("elemental-visuals.max-native-projectiles-per-world");
        try{
            magic.getConfig().set("elemental-visuals.max-native-projectiles-per-world",2);
            renderer=new com.example.advancemagic.spell.ElementalVfx(magic.context());
            try{
                for(int i=0;i<12;i++)renderer.begin(caster,ElementalSpell.FIREBALL).projectile(stage,stage.clone().add(0,0,1));
                check(nativeBodies().size()==2,"native projectile world cap bounds simultaneous real fireballs");
                check(magic.effects().size()==combatEffects&&magic.mana().account(caster).manaExact()==mana,
                        "native projectile cap changes no combat slots or mana");
                renderer.closeOwner(caster.getUniqueId());check(nativeBodies().isEmpty(),"owner cleanup removes every capped native body");
            }finally{renderer.close();}
        }finally{magic.getConfig().set("elemental-visuals.max-native-projectiles-per-world",projectileCap);}

        reset();staff(ElementalSpell.FIREBALL);check(magic.elemental().cast(caster),"world cleanup native fireball cast setup");ticks(1);
        check(!nativeBodies().isEmpty(),"real spell cast owns a native fireball before world cleanup");
        LivingEntity protectedTarget=mob(.5,7.5);double protectedHealth=protectedTarget.getHealth();
        protectedTarget.damage(20,org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.FIREBALL)
                .withCausingEntity(caster).withDirectEntity(nativeBody(SmallFireball.class)).build());
        near(protectedTarget.getHealth(),protectedHealth,"reused elemental service retains native projectile damage guards");
        Bukkit.getPluginManager().callEvent(new PlayerChangedWorldEvent(caster,world));
        check(nativeBodies().isEmpty()&&magic.effects().size()==0,"world change removes native projectile bodies and combat effects");
        reset();staff(ElementalSpell.FIREBALL);check(magic.elemental().cast(caster),"quit cleanup native fireball cast setup");ticks(1);
        check(!nativeBodies().isEmpty(),"real spell cast owns a native fireball before quit cleanup");
        Bukkit.getPluginManager().callEvent(new PlayerQuitEvent(caster,"native visual fixture"));
        check(nativeBodies().isEmpty()&&magic.effects().size()==0,"player quit removes native projectile bodies and combat effects");
        reset();
    }
    void catalog(){
        check(ElementalSpell.values().length==58,"exactly 58 elemental spells");
        var counts=new EnumMap<Element,Integer>(Element.class);var ids=new HashSet<String>();
        for(var spell:ElementalSpell.values()){
            check(ids.add(spell.id()),"unique stable spell ID "+spell.id());
            check(ElementalSpell.parse(spell.id())==spell&&ElementalSpell.parse(spell.title())==spell,"spell IDs and player-readable names parse "+spell.id());
            check(spell.mana()>0&&spell.mana()<=100&&spell.cooldown()>0&&spell.multiplier()>=0,"bounded authored balance "+spell.id());
            counts.merge(spell.element(),1,Integer::sum);
        }
        check(counts.equals(Map.of(Element.FIRE,10,Element.WATER,10,Element.ICE,7,Element.WIND,7,Element.EARTH,8,Element.LIGHT,5,Element.DARK,5,Element.LIGHTNING,6)),"per-element catalog counts match design");
        check(ElementalSpell.parse("unknown")==null&&ElementalSpell.parse(null)==null,"invalid spell IDs rejected");
    }
    void stats(){
        var bounded=new ElementalStats.Snapshot(Double.NaN,Double.POSITIVE_INFINITY,1000,.9,7,Map.of(Element.FIRE,5.0));
        check(Double.isFinite(bounded.spellPower())&&Double.isFinite(bounded.healingPower()),"non-finite external combat stats bounded");
        near(bounded.maxMana(),300,"maximum mana cap");near(bounded.manaReduction(),.4,"mana cost reduction cap");near(bounded.manaRegenBonus(),1.5,"mana regeneration bonus cap");
        check(bounded.cost(ElementalSpell.FIREBALL)==8,"mana cost rounds up after reduction");
        ElementalStats.Provider provider=(player,base)->new ElementalStats.Snapshot(20,8,140,.3,.5,Map.of(Element.FIRE,.2));
        Bukkit.getServicesManager().register(ElementalStats.Provider.class,provider,this,ServicePriority.Highest);
        var snapshot=magic.elementalStats().snapshot(caster);
        near(snapshot.spellPower(),20,"registered skill stat adapter controls spell power");near(snapshot.elementBonus(Element.FIRE),.2,"registered adapter controls elemental bonus");
        Bukkit.getServicesManager().unregister(ElementalStats.Provider.class,provider);
        var learner=actor("ElementScholar",new Location(world,20.5,100,20.5));var tree=magic.skillTree();
        check(tree.level(learner)==1&&tree.points(learner)==3,"skill tree begins with three spendable points");
        check(!tree.learn(learner,Node.FIRE)&&!tree.learn(learner,Node.EFFICIENCY),"skill prerequisite gates apply");
        check(tree.learn(learner,Node.POWER)&&tree.learn(learner,Node.POWER)&&tree.learn(learner,Node.FIRE),"spending prerequisite points unlocks elemental mastery");
        check(tree.points(learner)==0&&!tree.learn(learner,Node.MANA),"skill points cannot be overspent");
        check(tree.cost(1)>0&&tree.cost(20)>tree.cost(10)&&tree.cost(10)>tree.cost(1),"each Arcane level costs more XP than the last");
        long vanillaLevel=learner.getLevel();tree.addXp(learner,tree.totalXp(40)-1);
        check(tree.level(learner)==39&&tree.points(learner)==38,"one XP short of a level does not grant its point");
        tree.addXp(learner,1);
        check(tree.level(learner)==40&&tree.points(learner)==39&&learner.getLevel()==vanillaLevel,"Arcane levels grant one point each without consuming vanilla experience");
        check(Node.POWER.max==20&&Node.POWER.value(5)==20&&Node.POWER.value(20)==50
            &&Node.MANA.value(10)==40&&Node.EFFICIENCY.value(20)==40&&Node.REGEN.value(20)==150,"tiered ranks keep old values and reach their twenty-rank totals");
        check(tree.learn(learner,Node.MANA)&&tree.learn(learner,Node.MANA),"maximum mana skill ranks learn");
        var account=magic.mana().account(learner);account.setMaxMana(120);
        magic.mana().regenerate(learner,System.currentTimeMillis());
        near(account.maxMana(),128,"skill mana bonus added over potion progression");
        for(int i=0;i<3;i++)magic.mana().regenerate(learner,System.currentTimeMillis());
        near(account.maxMana(),128,"repeated regeneration cannot compound skill maximum mana");
        magic.mana().quit(learner);
        near(magic.mana().account(learner).baseMaxMana(),120,"saved maximum mana excludes temporary skill bonus");
        magic.mana().regenerate(learner,System.currentTimeMillis());near(magic.mana().account(learner).maxMana(),128,"reload restores skill bonus once over potion base");
        check(tree.rank(learner,Node.MANA)==2,"skill ranks persist in player PDC");
        long xp=tree.xp(learner);int points=tree.points(learner);
        check(!tree.reset(learner)&&tree.rank(learner,Node.MANA)==2&&tree.points(learner)==points&&tree.xp(learner)==xp,"insufficient respec resources do not mutate ranks XP or points");
        learner.setLevel(5);learner.getInventory().setItem(0,new ItemStack(Material.AMETHYST_SHARD,16));
        check(tree.reset(learner)&&learner.getLevel()==0&&learner.getInventory().isEmpty(),"respec consumes exact sixteen shards and five vanilla levels");
        check(tree.rank(learner,Node.MANA)==0&&tree.rank(learner,Node.POWER)==0&&tree.rank(learner,Node.FIRE)==0&&tree.points(learner)==42&&tree.xp(learner)==xp,"respec restores skill points while preserving earned Arcane XP");
        magic.mana().regenerate(learner,System.currentTimeMillis());near(magic.mana().account(learner).maxMana(),120,"respec removes transient skill maximum without resetting potion base");
        magic.mana().quit(learner);check(tree.xp(learner)==xp&&tree.points(learner)==42,"Arcane progression survives mana account reload");
        var scribe=actor("ElementScribe",new Location(world,20.5,100,10.5));magic.staffSpells().discover(scribe,ElementalSpell.FIREBALL);scribe.getInventory().clear();
        tree.discover(scribe);check(tree.xp(scribe)==0&&tree.points(scribe)==3,"codex discovery records spells without granting progression");
        tree.discover(scribe);scribe.getInventory().clear();tree.discover(scribe);check(tree.xp(scribe)==0&&tree.points(scribe)==3,"repeated codex scans cannot farm skill points");
    }
    ItemStack held(){return caster.getInventory().getItemInMainHand();}
    boolean imprint(ElementalSpell spell,int index){
        caster.getInventory().setItem(1,magic.staffSpells().createScroll(spell));
        return magic.staffSpells().imprint(caster,0,held().clone(),1,caster.getInventory().getItem(1).clone(),index).success();
    }
    ItemStack staff(ElementalSpell... spells){
        caster.getInventory().clear();caster.getInventory().setHeldItemSlot(0);
        caster.getInventory().setItemInMainHand(magic.staffSpells().createStaff(spells[0].element(),Tier.MYTHIC));
        for(int i=0;i<spells.length;i++)check(imprint(spells[i],i),"prepare learned staff "+spells[i].id());
        return held();
    }
    void items() throws Exception {
        var service=magic.staffSpells();
        for(Element element:Element.values())for(Tier tier:List.of(Tier.APPRENTICE,Tier.ARCANE,Tier.MYTHIC)){
            var item=service.createStaff(element,tier);
            check(magic.staffs().element(item)==element&&service.tier(item)==tier&&service.spells(item).isEmpty(),"new empty "+tier+" "+element+" staff");
            check(service.tier(ItemStack.deserializeBytes(item.serializeAsBytes()))==tier,"staff tier survives server serialization "+tier+" "+element);
        }
        check(Tier.APPRENTICE.slots()==3&&Tier.ARCANE.slots()==4&&Tier.MYTHIC.slots()==5,"staff slot capacities 3/4/5");
        for(var spell:ElementalSpell.values()){
            var scroll=service.createScroll(spell);check(service.scroll(scroll)==spell,"scroll identity "+spell.id());
            check(service.scroll(ItemStack.deserializeBytes(scroll.serializeAsBytes()))==spell,"scroll identity survives serialization "+spell.id());
            check(Tier.MYTHIC.accepts(spell)&&(!spell.ultimate()||!Tier.ARCANE.accepts(spell)),"rarity gate "+spell.id());
        }
        check(!service.isScroll(new ItemStack(Material.PAPER)),"plain paper cannot impersonate scroll");
        var old=magic.staffs().create(Element.FIRE);var oldMeta=old.getItemMeta();
        var owner=new NamespacedKey("foreign_plugin","soulbound_owner");oldMeta.getPersistentDataContainer().set(owner,PersistentDataType.STRING,"preserved-owner");
        oldMeta.setDisplayName("Player personal name");oldMeta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING,3,true);old.setItemMeta(oldMeta);
        var model=old.getItemMeta().getItemModel();
        check(service.migrate(old)&&!service.migrate(old),"existing model staff loadout migration is idempotent");
        check(service.tier(old)==Tier.APPRENTICE&&service.spells(old).isEmpty(),"existing staffs migrate to empty apprentice");
        check(old.getItemMeta().getDisplayName().equals("Player personal name")&&old.getEnchantmentLevel(org.bukkit.enchantments.Enchantment.UNBREAKING)==3&&old.getItemMeta().getItemModel().equals(model),"migration preserves name enchantment model");
        check("preserved-owner".equals(old.getItemMeta().getPersistentDataContainer().get(owner,PersistentDataType.STRING)),"migration preserves third-party ownership PDC");
        caster.getInventory().clear();caster.getInventory().setHeldItemSlot(0);caster.getInventory().setItemInMainHand(old);
        var stacked=service.createScroll(ElementalSpell.FIREBALL);stacked.setAmount(3);caster.getInventory().setItem(1,stacked);
        var staffSnapshot=held().clone();var scrollSnapshot=caster.getInventory().getItem(1).clone();
        check(service.imprint(caster,0,staffSnapshot,1,scrollSnapshot,0).success(),"imprint transaction succeeds");
        check(service.spells(held()).equals(List.of(ElementalSpell.FIREBALL))&&caster.getInventory().getItem(1).getAmount()==2,"imprint learns one spell and consumes exactly one scroll");
        var committed=held().clone();
        check(!service.imprint(caster,0,staffSnapshot,1,scrollSnapshot,0).success()&&held().equals(committed)&&caster.getInventory().getItem(1).getAmount()==2,"replayed/stale imprint cannot consume or duplicate");
        var current=held().clone();var scrolls=caster.getInventory().getItem(1).clone();
        check(!service.imprint(caster,0,current,1,scrolls,1).success()&&caster.getInventory().getItem(1).getAmount()==2,"duplicate spell rejected without consumption");
        check(imprint(ElementalSpell.AQUA_BULLET,1)&&service.spells(held()).equals(List.of(ElementalSpell.FIREBALL,ElementalSpell.AQUA_BULLET))&&caster.getInventory().getItem(1)==null,"cross-element scroll imprints and consumes exactly one scroll");
        check(!imprint(ElementalSpell.MAGMA_ERUPTION,1)&&!imprint(ElementalSpell.METEOR_RAIN,1),"apprentice rejects epic and legendary");
        check(imprint(ElementalSpell.FLAME_BURST,1)&&imprint(ElementalSpell.BURNING_GROUND,2),"apprentice accepts three eligible spells");
        check(!imprint(ElementalSpell.FLAME_DASH,3)&&service.spells(held()).size()==3,"apprentice cannot exceed three slots");
        check(imprint(ElementalSpell.INFERNO_SPEAR,0)&&service.spells(held()).equals(List.of(ElementalSpell.INFERNO_SPEAR,ElementalSpell.FLAME_BURST,ElementalSpell.BURNING_GROUND)),"explicit replacement permanently replaces exactly selected slot");
        check(service.select(caster,2)&&service.selected(held())==ElementalSpell.FLAME_BURST,"one-based spell selection");
        var serialized=ItemStack.deserializeBytes(held().serializeAsBytes());check(service.selected(serialized)==ElementalSpell.FLAME_BURST,"selection and loadout persist on the staff item");
        check(service.reorder(caster,1,2).success()&&service.selected(held())==ElementalSpell.FLAME_BURST&&service.selectedIndex(held())==0,"reordering preserves selected spell identity");
        check(!service.select(caster,0)&&!service.select(caster,4),"out-of-range selection rejected");
        var before=held().clone();caster.getInventory().setItem(1,new ItemStack(Material.DIAMOND,8));
        check(!service.upgrade(caster).success()&&held().equals(before)&&caster.getInventory().getItem(1).getAmount()==8,"insufficient upgrade materials leave all inventory intact");
        caster.getInventory().setItem(2,new ItemStack(Material.AMETHYST_SHARD,18));
        var known=service.spells(held());var selected=service.selected(held());
        check(service.upgrade(caster).success()&&service.tier(held())==Tier.ARCANE,"material transaction upgrades apprentice to arcane");
        check(caster.getInventory().getItem(1)==null&&caster.getInventory().getItem(2).getAmount()==2,"upgrade consumes exact vanilla material quantities");
        check(service.spells(held()).equals(known)&&service.selected(held())==selected&&"preserved-owner".equals(held().getItemMeta().getPersistentDataContainer().get(owner,PersistentDataType.STRING)),"tier upgrade preserves learned spells selection ownership");
        check(imprint(ElementalSpell.MAGMA_ERUPTION,3)&&!imprint(ElementalSpell.METEOR_RAIN,0),"arcane adds fourth epic and rejects legendary");
        caster.getInventory().setItem(1,new ItemStack(Material.NETHERITE_INGOT));caster.getInventory().setItem(2,new ItemStack(Material.NETHER_STAR));
        check(service.upgrade(caster).success()&&service.tier(held())==Tier.MYTHIC&&imprint(ElementalSpell.METEOR_RAIN,4),"mythic material upgrade unlocks fifth legendary spell");
        check(!service.upgrade(caster,Tier.APPRENTICE).success()&&service.spells(held()).size()==5,"downgrade cannot truncate the loadout");
        check(service.discovered(caster).containsAll(service.spells(held())),"collection records learned spells");
        var protectedStaff=held().clone();check(service.protect(caster).success(),"staff drop protection toggles");
        var dropped=world.dropItem(caster.getLocation(),held());var dropEvent=new PlayerDropItemEvent(caster,dropped);Bukkit.getPluginManager().callEvent(dropEvent);dropped.remove();
        check(dropEvent.isCancelled(),"protected staff blocks accidental player drop");
        caster.getInventory().setItemInMainHand(protectedStaff);
        var corrupt=held().clone();var meta=corrupt.getItemMeta();meta.getPersistentDataContainer().set(new NamespacedKey("advance_magic","staff_spells"),PersistentDataType.STRING,"fireball,aqua_bullet");corrupt.setItemMeta(meta);
        check(service.spells(corrupt).equals(List.of(ElementalSpell.FIREBALL,ElementalSpell.AQUA_BULLET)),"valid mixed-element loadout survives metadata parsing");
        meta=corrupt.getItemMeta();meta.getPersistentDataContainer().set(new NamespacedKey("advance_magic","staff_spells"),PersistentDataType.STRING,"fireball,fireball");corrupt.setItemMeta(meta);
        check(service.spells(corrupt).isEmpty()&&service.selected(corrupt)==null,"duplicate forged loadout rejected");
        for(Element element:Element.values()){
            caster.getInventory().clear();caster.getInventory().setItemInMainHand(service.createStaff(element,Tier.APPRENTICE));
            for(ElementalSpell spell:List.of(ElementalSpell.FIREBALL,ElementalSpell.AQUA_BULLET,ElementalSpell.FROST_BOLT,ElementalSpell.WIND_CUTTER,
                    ElementalSpell.STONE_BULLET,ElementalSpell.RADIANT_BOLT,ElementalSpell.SHADOW_BOLT,ElementalSpell.SPARK_SHOT))
                check(imprint(spell,0)&&service.selected(held())==spell,"every elemental staff accepts every basic element "+element+" / "+spell.id());
        }
        woodenItems(owner);extraction();
    }
    void woodenItems(NamespacedKey owner){
        var service=magic.staffSpells();
        check(Tier.WOODEN.slots()==1&&Tier.REINFORCED_WOODEN.slots()==2
                &&Tier.WOODEN.maxRarity==ElementalSpell.Rarity.RARE&&Tier.REINFORCED_WOODEN.maxRarity==ElementalSpell.Rarity.RARE,
                "wood staff starts at one slot and upgrades to two with Rare cap on both tiers");
        for(Tier rank:List.of(Tier.WOODEN,Tier.REINFORCED_WOODEN)){
            var wooden=service.createWoodenStaff(rank);var restored=ItemStack.deserializeBytes(wooden.serializeAsBytes());
            check(magic.staffs().isWooden(wooden)&&magic.staffs().isStaff(wooden)&&magic.staffs().element(wooden)==null
                    &&service.tier(wooden)==rank&&service.spells(wooden).isEmpty(),"neutral empty wood staff identity "+rank);
            check(magic.staffs().isWooden(restored)&&service.tier(restored)==rank
                    &&Objects.equals(restored.getItemMeta().getItemModel(),wooden.getItemMeta().getItemModel())
                    &&restored.getItemMeta().getItemModel()!=null,"wood identity tier and native model persist on real server serialization "+rank);
            caster.getInventory().clear();caster.getInventory().setItemInMainHand(wooden);
            for(ElementalSpell spell:ElementalSpell.values()){
                check(imprint(spell,0)==rank.accepts(spell),"wood tier accepts only Basic and Rare scrolls "+rank+" / "+spell.id());
                near(service.affinityMultiplier(held(),spell),.5,"wood has no matching element damage bonus "+rank+" / "+spell.id());
            }
        }
        for(Tier rank:List.of(Tier.WOODEN,Tier.REINFORCED_WOODEN)){
            boolean rejected=false;try{service.createStaff(Element.FIRE,rank);}catch(IllegalArgumentException expected){rejected=true;}
            check(rejected,"elemental staff cannot claim wood tier "+rank);
        }
        for(Tier rank:List.of(Tier.APPRENTICE,Tier.ARCANE,Tier.MYTHIC)){
            boolean rejected=false;try{service.createWoodenStaff(rank);}catch(IllegalArgumentException expected){rejected=true;}
            check(rejected,"wood factory cannot claim elemental tier "+rank);
        }
        caster.getInventory().clear();caster.getInventory().setItemInMainHand(service.createWoodenStaff());
        check(imprint(ElementalSpell.INFERNO_SPEAR,0),"starter wood accepts a Rare spell in its only slot");
        check(!imprint(ElementalSpell.AQUA_BULLET,1)&&service.spells(held()).equals(List.of(ElementalSpell.INFERNO_SPEAR)),"starter wood cannot use second slot before upgrade");
        var woodMeta=held().getItemMeta();woodMeta.setDisplayName("Personal apprentice branch");
        woodMeta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING,2,true);
        woodMeta.getPersistentDataContainer().set(owner,PersistentDataType.STRING,"wood-owner");
        var personal=held().clone();personal.setItemMeta(woodMeta);caster.getInventory().setItemInMainHand(personal);service.protect(caster);
        var original=held().clone();var model=original.getItemMeta().getItemModel();
        check(service.upgradeCost(Tier.WOODEN).equals(Map.of(Material.AMETHYST_SHARD,4,Material.BLAZE_ROD,2)),"wood upgrade requires exactly four shards and two Blaze Rods");
        caster.getInventory().setItem(1,new ItemStack(Material.BLAZE_ROD,2));caster.getInventory().setItem(2,new ItemStack(Material.AMETHYST_SHARD,3));
        var insufficient=caster.getInventory().getStorageContents();
        check(!service.upgrade(caster).success()&&Arrays.equals(insufficient,caster.getInventory().getStorageContents()),"insufficient wood upgrade materials leave all inventory intact");
        var renamed=new ItemStack(Material.AMETHYST_SHARD,4);var renamedMeta=renamed.getItemMeta();renamedMeta.setDisplayName("not a plain crafting shard");renamed.setItemMeta(renamedMeta);caster.getInventory().setItem(2,renamed);
        check(!service.upgrade(caster).success()&&held().equals(original)&&caster.getInventory().getItem(2).equals(renamed),"named upgrade material cannot stand in for plain shards");
        caster.getInventory().setItem(2,new ItemStack(Material.AMETHYST_SHARD,6));
        check(service.upgrade(caster).success()&&service.tier(held())==Tier.REINFORCED_WOODEN,"material transaction upgrades wood from one to two slots");
        check(caster.getInventory().getItem(1)==null&&caster.getInventory().getItem(2).getAmount()==2,"wood upgrade consumes exact quantities without using staff");
        check(service.selected(held())==ElementalSpell.INFERNO_SPEAR&&magic.staffs().isWooden(held())&&magic.staffs().element(held())==null
                &&Objects.equals(model,held().getItemMeta().getItemModel())&&held().getItemMeta().getDisplayName().equals("Personal apprentice branch")
                &&held().getEnchantmentLevel(org.bukkit.enchantments.Enchantment.UNBREAKING)==2
                &&"wood-owner".equals(held().getItemMeta().getPersistentDataContainer().get(owner,PersistentDataType.STRING))
                &&held().getItemMeta().getPersistentDataContainer().getOrDefault(new NamespacedKey("advance_magic","staff_drop_protected"),PersistentDataType.BYTE,(byte)0)==1,
                "wood upgrade preserves spell selection neutral identity model name enchantment ownership and drop protection");
        check(imprint(ElementalSpell.AQUA_BULLET,1)&&service.spells(held()).size()==2,"reinforced wood holds two mixed-element spells");
        check(!imprint(ElementalSpell.FROST_BOLT,2)&&!imprint(ElementalSpell.MAGMA_ERUPTION,0)&&!imprint(ElementalSpell.METEOR_RAIN,0),"reinforced wood keeps two-slot Rare cap");
        var capped=held().clone();
        caster.getInventory().setItem(1,new ItemStack(Material.DIAMOND,8));caster.getInventory().setItem(2,new ItemStack(Material.AMETHYST_SHARD,16));
        caster.getInventory().setItem(3,new ItemStack(Material.NETHERITE_INGOT));caster.getInventory().setItem(4,new ItemStack(Material.NETHER_STAR));
        var inventory=caster.getInventory().getStorageContents();
        check(!service.upgrade(caster).success()&&Arrays.equals(inventory,caster.getInventory().getStorageContents()),"reinforced wood has no further material upgrade");
        for(Tier target:Tier.values())check(!service.upgrade(caster,target).success()&&held().equals(capped),"wood cannot cross upgrade path or exceed reinforced cap "+target);
        caster.getInventory().setItemInMainHand(service.createStaff(Element.FIRE,Tier.APPRENTICE));
        for(Tier target:List.of(Tier.WOODEN,Tier.REINFORCED_WOODEN))check(!service.upgrade(caster,target).success()&&service.tier(held())==Tier.APPRENTICE,"elemental staff cannot upgrade into wood path "+target);
        caster.getInventory().clear();
    }
    long scrollCount(ElementalSpell spell){
        return Arrays.stream(caster.getInventory().getStorageContents()).filter(Objects::nonNull)
                .filter(item->magic.staffSpells().scroll(item)==spell).mapToLong(ItemStack::getAmount).sum();
    }
    void extraction(){
        var service=magic.staffSpells();staff(ElementalSpell.FIREBALL,ElementalSpell.AQUA_BULLET,ElementalSpell.FROST_BOLT);
        check(service.select(caster,3),"extraction active-spell fixture selects third slot");var snapshot=held().clone();
        check(service.extract(caster,0,snapshot,0).success()&&service.spells(held()).equals(List.of(ElementalSpell.AQUA_BULLET,ElementalSpell.FROST_BOLT))
                &&service.selected(held())==ElementalSpell.FROST_BOLT&&service.selectedIndex(held())==1&&scrollCount(ElementalSpell.FIREBALL)==1,
                "extracting earlier slot returns one scroll and preserves surviving selected spell");
        var committed=held().clone();
        check(!service.extract(caster,0,snapshot,0).success()&&held().equals(committed)&&scrollCount(ElementalSpell.FIREBALL)==1,"replayed extraction snapshot cannot duplicate scroll or remove another spell");
        check(service.extract(caster,0,held().clone(),1).success()&&service.selected(held())==ElementalSpell.AQUA_BULLET
                &&service.selectedIndex(held())==0&&scrollCount(ElementalSpell.FROST_BOLT)==1,"extracting selected last slot selects remaining prior spell");
        check(service.extract(caster,0,held().clone(),0).success()&&service.spells(held()).isEmpty()&&service.selected(held())==null
                &&scrollCount(ElementalSpell.AQUA_BULLET)==1,"last extraction leaves valid empty staff and returns exact scroll");
        committed=held().clone();
        check(!service.extract(caster,0,committed,0).success()&&held().equals(committed),"empty staff extraction cannot mint a scroll");
        staff(ElementalSpell.FIREBALL,ElementalSpell.AQUA_BULLET,ElementalSpell.FROST_BOLT);service.select(caster,2);
        check(service.extract(caster,0,held().clone(),1).success()&&service.selected(held())==ElementalSpell.FROST_BOLT
                &&service.selectedIndex(held())==1,"extracting active middle slot selects the spell shifted into that slot");
        staff(ElementalSpell.FIREBALL);snapshot=held().clone();caster.getInventory().setHeldItemSlot(2);
        check(!service.extract(caster,0,snapshot,0).success()&&caster.getInventory().getItem(0).equals(snapshot),"extraction rejects changed held slot without inventory mutation");
        caster.getInventory().setHeldItemSlot(0);
        for(int slot=1;slot<36;slot++)caster.getInventory().setItem(slot,new ItemStack(Material.STONE,64));
        var before=caster.getInventory().getStorageContents();int dropped=world.getEntitiesByClass(Item.class).size();
        check(!service.extract(caster,0,held().clone(),0).success()&&Arrays.equals(before,caster.getInventory().getStorageContents())
                &&world.getEntitiesByClass(Item.class).size()==dropped,"full normal storage rejects extraction atomically without dropping scroll");
        var existing=service.createScroll(ElementalSpell.FIREBALL);existing.setAmount(63);caster.getInventory().setItem(7,existing);
        check(service.extract(caster,0,held().clone(),0).success()&&service.spells(held()).isEmpty()&&scrollCount(ElementalSpell.FIREBALL)==64,
                "extraction can merge into one available scroll stack when every storage slot is occupied");
        staff(ElementalSpell.FIREBALL);snapshot=held().clone();
        for(int index:List.of(-1,1,Integer.MAX_VALUE))check(!service.extract(caster,0,snapshot,index).success()&&held().equals(snapshot),"extraction rejects invalid zero-based index "+index);
        caster.getInventory().setItemInMainHand(service.createWoodenStaff());check(imprint(ElementalSpell.INFERNO_SPEAR,0),"wood extraction Rare scroll fixture");
        check(service.extract(caster,0,held().clone(),0).success()&&magic.staffs().isWooden(held())&&service.tier(held())==Tier.WOODEN
                &&service.spells(held()).isEmpty()&&scrollCount(ElementalSpell.INFERNO_SPEAR)==1,"wood staff extraction keeps neutral identity and returns Rare scroll");
        caster.getInventory().clear();
    }
    void clearAccounting(){
        var account=magic.mana().account(caster);account.setMaxMana(100);account.setMana(100);
        for(var spell:ElementalSpell.values())account.restore("elemental_"+spell.id(),0);
        var data=caster.getPersistentDataContainer();
        for(String name:List.of("elemental_gcd","elemental_ultimate","elemental_regen_after"))data.remove(magic.mana().key(name));
    }
    void accounting() throws Exception {
        clearAccounting();var mana=magic.mana();long now=System.currentTimeMillis();
        check(!mana.reserveElemental(caster,ElementalSpell.FIREBALL,-1,now),"negative mana charge rejected");
        check(mana.reserveElemental(caster,ElementalSpell.FIREBALL,12,now),"reserve a single elemental cast");
        near(mana.account(caster).manaExact(),88,"mana deducted exactly once");
        check(mana.elementalRemaining(caster,ElementalSpell.FIREBALL,now)==4000&&mana.gcdRemaining(caster,now)==650,"independent spell and global cooldowns");
        check(!mana.reserveElemental(caster,ElementalSpell.AQUA_BULLET,10,now+100),"different staff cannot bypass global cooldown");
        check(!mana.reserveElemental(caster,ElementalSpell.FIREBALL,12,now+700),"duplicate staff cannot bypass per-player spell cooldown");
        check(mana.reserveElemental(caster,ElementalSpell.AQUA_BULLET,10,now+700),"different spell usable after GCD");
        near(mana.account(caster).manaExact(),78,"blocked casts did not consume extra mana");
        mana.quit(caster);
        check(mana.elementalRemaining(caster,ElementalSpell.FIREBALL,now+800)==3200&&mana.gcdRemaining(caster,now+800)==550,"spell and global cooldown persist after account reload");
        near(mana.account(caster).manaExact(),78,"mana survives account save reload");
        clearAccounting();check(mana.reserveElemental(caster,ElementalSpell.METEOR_RAIN,65,now),"reserve ultimate");
        mana.account(caster).setMana(100);
        check(mana.gcdRemaining(caster,now)==1200&&mana.ultimateRemaining(caster,now)==15000,"ultimate has longer GCD and category lock");
        check(!mana.reserveElemental(caster,ElementalSpell.TSUNAMI,58,now+1300),"staff/element swap cannot bypass ultimate category");
        mana.quit(caster);
        check(mana.ultimateRemaining(caster,now+1300)==13700&&mana.elementalRemaining(caster,ElementalSpell.METEOR_RAIN,now+1300)==43700,"ultimate locks survive account reload");
        clearAccounting();mana.account(caster).setMana(11.99);
        check(!mana.reserveElemental(caster,ElementalSpell.FIREBALL,12,now)&&mana.account(caster).manaExact()==11.99,"fractional insufficient mana rejects atomically");
        clearAccounting();check(mana.reserveElemental(caster,ElementalSpell.FIREBALL,12,now),"reserve rollback fixture");
        mana.rollbackElemental(caster,ElementalSpell.FIREBALL,12);
        check(mana.account(caster).manaExact()==100&&mana.gcdRemaining(caster,now)==0&&mana.elementalRemaining(caster,ElementalSpell.FIREBALL,now)==0,"rollback restores mana spell cooldown and GCD");
        clearAccounting();mana.account(caster).setMana(50);check(mana.reserveElemental(caster,ElementalSpell.FIREBALL,12,now),"reserve regen delay fixture");
        mana.regenerate(caster,now+1000);near(mana.account(caster).manaExact(),38,"no regeneration within two-second cast delay");
        mana.markCombat(caster);mana.regenerate(caster,now+3000);near(mana.account(caster).manaExact(),41,"combat regeneration is three mana per second");
        mana.regenerate(caster,now+30000);near(mana.account(caster).manaExact(),48,"out-of-combat regeneration is seven mana per second");
        ElementalStats.Provider provider=(player,base)->new ElementalStats.Snapshot(12,6,100,.3,1,Map.of());
        Bukkit.getServicesManager().register(ElementalStats.Provider.class,provider,this,ServicePriority.Highest);
        mana.regenerate(caster,now+30001);near(mana.account(caster).manaExact(),62,"stat regeneration bonus applies after baseline rate");
        Bukkit.getServicesManager().unregister(ElementalStats.Provider.class,provider);
        clearAccounting();
    }
    void reset(){
        magic.elemental().close();magic.effects().close();magic.statuses().close();
        for(LivingEntity mob:mobs)mob.remove();mobs.clear();damage.clear();damageTimes.clear();
        cancelCast=cancelAffect=cancelDamage=cancelHealing=false;caster.closeInventory();caster.getInventory().clear();
        caster.teleport(new Location(world,.5,100,.5,0,0));caster.setSneaking(false);caster.setHealth(1000);caster.setNoDamageTicks(0);
        magic.getConfig().set("pvp",true);clearAccounting();
    }
    void aim(boolean ground){var at=caster.getLocation();at.setYaw(0);at.setPitch(ground?24:0);caster.teleport(at);}
    static final Set<String> GROUND=Set.of("burning_ground","magma_eruption","scorching_vortex","meteor_rain","healing_spring","whirlpool","ice_spike","blizzard","absolute_zero","cyclone_trap","earth_spike","quicksand","titans_judgment","holy_sanctuary","divine_judgment","abyssal_collapse","static_field","wrath_of_raijin");
    static final Set<ElementalSpell> LEGENDARY_SCENES=EnumSet.of(ElementalSpell.METEOR_RAIN,ElementalSpell.LEVIATHANS_WRATH,ElementalSpell.ABSOLUTE_ZERO,
        ElementalSpell.TEMPEST_DOMINION,ElementalSpell.TITANS_JUDGMENT,ElementalSpell.HEAVENS_DESCENT,ElementalSpell.ABYSSAL_COLLAPSE,ElementalSpell.WRATH_OF_RAIJIN);
    static final Set<String> HEAL=Set.of("healing_spring","rain_blessing","holy_sanctuary");
    static final Set<String> SUPPORT=Set.of("healing_spring","aqua_shield","rain_blessing","frozen_armor","gale_step","wind_barrier","stone_wall","iron_skin","holy_sanctuary","purification","cursed_mark","shadow_doppelganger");
    void affinities(){
        var service=magic.staffSpells();
        for(ElementalSpell spell:List.of(ElementalSpell.FIREBALL,ElementalSpell.AQUA_BULLET,ElementalSpell.FROST_BOLT,ElementalSpell.WIND_CUTTER,
                ElementalSpell.STONE_BULLET,ElementalSpell.RADIANT_BOLT,ElementalSpell.SHADOW_BOLT,ElementalSpell.SPARK_SHOT)){
            Element other=Arrays.stream(Element.values()).filter(element->element!=spell.element()).findFirst().orElseThrow();
            for(int kind=0;kind<3;kind++){
                reset();var target=mob(.5,4.5);double affinity=kind==0?1.5:.5;
                caster.getInventory().setItemInMainHand(kind==2?service.createWoodenStaff():service.createStaff(kind==0?spell.element():other,Tier.MYTHIC));
                check(imprint(spell,0),"affinity combat fixture accepts scroll "+kind+" / "+spell.id());
                near(service.affinityMultiplier(held(),spell),affinity,"affinity helper computes matching mismatching or neutral coefficient "+kind+" / "+spell.id());
                check(magic.elemental().cast(caster),"matching mismatching and neutral staff actually casts "+kind+" / "+spell.id());
                // Delayed projectiles retain the launch staff's affinity after a held-item swap.
                caster.getInventory().setItemInMainHand(service.createStaff(kind==0?other:spell.element(),Tier.MYTHIC));
                ticks(10);
                near(total(target),12*spell.multiplier()*affinity,"real combat uses launch affinity exactly once "+kind+" / "+spell.id());
                near(magic.mana().account(caster).manaExact(),100-spell.mana(),"affinity affects damage without changing mana cost "+kind+" / "+spell.id());
            }
        }
        reset();var target=mob(.5,4.5);caster.getInventory().setItemInMainHand(service.createWoodenStaff());
        check(imprint(ElementalSpell.FIREBALL,0)&&magic.elemental().cast(caster),"neutral Burn delayed snapshot fixture launches");
        caster.getInventory().setItemInMainHand(service.createStaff(Element.FIRE,Tier.MYTHIC));ticks(100);
        var hits=damage.get(target.getUniqueId());
        check(hits!=null&&hits.size()==4&&hits.subList(1,4).stream().allMatch(hit->Math.abs(hit-.3)<.0001),"wood Burn retains half damage after swapping to matching elemental staff");
        for(int kind=0;kind<3;kind++){
            reset();caster.setHealth(500);
            caster.getInventory().setItemInMainHand(kind==2?service.createWoodenStaff():service.createStaff(kind==0?Element.WATER:Element.FIRE,Tier.MYTHIC));
            check(imprint(ElementalSpell.HEALING_SPRING,0),"healing affinity fixture accepts Rare scroll "+kind);
            var downward=caster.getLocation();downward.setPitch(90);caster.teleport(downward);
            check(magic.elemental().cast(caster),"healing remains castable across matching mismatching and wood staffs "+kind);ticks(120);
            near(caster.getHealth(),507.2,"damage affinity leaves healing power unchanged "+kind);
            clearAccounting();caster.getInventory().clear();
            caster.getInventory().setItemInMainHand(kind==2?service.createWoodenStaff():service.createStaff(kind==0?Element.WATER:Element.FIRE,Tier.MYTHIC));
            check(imprint(ElementalSpell.AQUA_SHIELD,0)&&magic.elemental().cast(caster),"shield affinity fixture casts "+kind);
            var attacker=mob(.5,4.5);var event=new EntityDamageByEntityEvent(attacker,caster,EntityDamageEvent.DamageCause.ENTITY_ATTACK,25);
            Bukkit.getPluginManager().callEvent(event);
            check(!event.isCancelled(),"unchanged shield absorbs less than incoming twenty-five damage "+kind);
            near(event.getDamage(),3.4,"damage affinity leaves shield capacity unchanged "+kind);
        }
        reset();
    }
    void spells() throws Exception {
        Player ally=actor("ElementAlly",new Location(world,.5,100,4.5));
        var team=Bukkit.getScoreboardManager().getMainScoreboard().registerNewTeam("elemental-allies");team.addEntry(caster.getName());team.addEntry(ally.getName());
        try{
            for(var spell:ElementalSpell.values()){
                if(spell==ElementalSpell.PHOENIX_REBIRTH)continue;
                reset();ally.teleport(new Location(world,.5,100,4.5));ally.setHealth(500);caster.setHealth(500);
                var target=mob(.5,4.5);staff(spell);aim(GROUND.contains(spell.id()));
                double mana=magic.mana().account(caster).manaExact();int events=castEvents;
                check(magic.elemental().cast(caster),"actual cast dispatch "+spell.id());
                check(castEvents==events+1,"one cancellable cast event "+spell.id());
                near(magic.mana().account(caster).manaExact(),mana-spell.mana(),"one full mana charge "+spell.id());
                check(magic.mana().elementalRemaining(caster,spell,System.currentTimeMillis())>0,"per-player cooldown recorded "+spell.id());
                if(Set.of("frozen_armor","iron_skin").contains(spell.id())){
                    var event=new EntityDamageByEntityEvent(target,caster,EntityDamageEvent.DamageCause.ENTITY_ATTACK,20);Bukkit.getPluginManager().callEvent(event);
                    near(event.getDamage(),spell==ElementalSpell.FROZEN_ARMOR?16:15,"defensive reduction "+spell.id());
                }
                if(spell==ElementalSpell.CURSED_MARK)check(magic.elemental().hasStatus(target,"curse_mark"),"cursed mark is an actual timed status");
                if(spell==ElementalSpell.STONE_WALL)check(!world.getEntitiesByClass(BlockDisplay.class).isEmpty(),"stone wall has tracked collision proxy art");
                if(spell==ElementalSpell.SHADOW_DOPPELGANGER)check(world.getEntitiesByClass(ArmorStand.class).stream().anyMatch(e->e.getPersistentDataContainer().has(new NamespacedKey(magic,"elemental_summon"))),"shadow creates an owned temporary summon");
                ticks(360);
                if(!SUPPORT.contains(spell.id())){
                    check(total(target)>0,"scheduled spell hits a real target "+spell.id());
                    check(total(target)<=18*(spell.multiplier()+.6)+.001,"whole-cast damage stays inside matching-affinity authored budget plus Burn "+spell.id()+" total="+total(target));
                }
                if(HEAL.contains(spell.id()))check(ally.getHealth()>500,"healing field restores allied player health "+spell.id());
                if(spell==ElementalSpell.HEAVENS_DESCENT)check(ally.getHealth()>500,"heaven ultimate heals allies while damaging enemies");
                check(magic.effects().size()==0,"effect duration cleanup "+spell.id());
                check(world.getEntitiesByClass(BlockDisplay.class).isEmpty()&&world.getEntitiesByClass(ArmorStand.class).stream().noneMatch(e->e.getPersistentDataContainer().has(new NamespacedKey(magic,"elemental_summon"))),"temporary entity cleanup "+spell.id());
                check(world.getBlockAt(0,99,4).getType()==Material.STONE&&world.getBlockAt(0,100,4).getType()==Material.AIR,"spell preserves real world blocks "+spell.id());
            }
        }finally{team.unregister();ally.teleport(new Location(world,20.5,100,-20.5));}
        reset();var target=mob(.5,4.5);staff(ElementalSpell.AQUA_SHIELD);
        check(magic.elemental().cast(caster),"aqua shield activated");
        double before=caster.getHealth();caster.damage(20,target);near(caster.getHealth(),before,"aqua shield absorbs actual incoming damage");
        reset();target=mob(.5,4.5);staff(ElementalSpell.LIFE_DRAIN);caster.setHealth(500);cancelDamage=true;
        check(magic.elemental().cast(caster),"life drain can launch into a protected damage target");near(caster.getHealth(),500,"cancelled damage cannot mint life-drain healing");cancelDamage=false;
        reset();target=mob(.5,4.5);staff(ElementalSpell.SHADOW_DOPPELGANGER,ElementalSpell.SHADOW_BOLT);
        check(magic.elemental().cast(caster),"shadow summon activated");clearAccounting();check(magic.staffSpells().select(caster,2)&&magic.elemental().cast(caster),"shadow copies an allowed normal attack");ticks(40);
        near(total(target),18*1.35,"shadow echo applies one affinity-scaled bounded copy without recursion");
        reset();
    }
    void policies() throws Exception {
        reset();var target=mob(.5,4.5);staff(ElementalSpell.FROST_BOLT);cancelCast=true;
        check(!magic.elemental().cast(caster)&&magic.mana().account(caster).manaExact()==100&&magic.effects().size()==0,"cancelled cast consumes no mana and launches no effects");
        check(magic.mana().elementalRemaining(caster,ElementalSpell.FROST_BOLT,System.currentTimeMillis())==0,"cancelled cast leaves cooldown ready");
        reset();target=mob(.5,4.5);staff(ElementalSpell.FROST_BOLT);cancelAffect=true;
        check(magic.elemental().cast(caster),"projectile launches before target protection check");ticks(30);
        check(total(target)==0&&magic.elemental().stacks(target,"frost")==0&&!magic.elemental().hasStatus(target,"slow"),"cancelled target-affect blocks both damage and status");
        reset();target=mob(.5,4.5);staff(ElementalSpell.FROST_BOLT);cancelDamage=true;
        check(magic.elemental().cast(caster),"projectile launches before native damage protection");ticks(30);
        check(total(target)==0&&magic.elemental().stacks(target,"frost")==0,"cancelled native damage cannot add elemental stacks");
        reset();target=mob(.5,4.5);var team=Bukkit.getScoreboardManager().getMainScoreboard().registerNewTeam("element-protect");team.addEntry(caster.getName());team.addEntry(target.getUniqueId().toString());
        try{staff(ElementalSpell.FIREBALL);check(magic.elemental().cast(caster),"friendly target permits a missed projectile");ticks(40);check(total(target)==0,"scoreboard allies cannot be damaged by elemental attacks");}finally{team.unregister();}
        reset();var other=actor("ElementPvp",new Location(world,.5,100,4.5));magic.getConfig().set("pvp",false);staff(ElementalSpell.SHADOW_BOLT);
        check(magic.elemental().cast(caster),"PvP-disabled projectile can launch");ticks(30);check(total(other)==0,"disabled PvP is respected");other.teleport(new Location(world,-20.5,100,-20.5));
        reset();target=mob(.5,4.5);staff(ElementalSpell.FROST_BOLT);
        for(int x=-1;x<=1;x++)for(int y=100;y<=103;y++)world.getBlockAt(x,y,2).setType(Material.STONE);
        check(magic.elemental().cast(caster),"blocked projectile still consumes one attempted cast");ticks(40);check(total(target)==0,"real solid world wall blocks projectiles");
        for(int x=-1;x<=1;x++)for(int y=100;y<=103;y++)world.getBlockAt(x,y,2).setType(Material.AIR);
        reset();staff(ElementalSpell.SHADOW_BOLT);while(magic.effects().hasCapacity())magic.effects().start(caster,1000,(effect,age)->true);
        check(!magic.elemental().cast(caster)&&magic.mana().account(caster).manaExact()==100,"effect capacity rejection is free of mana charge");
        reset();staff(ElementalSpell.LIFE_DRAIN);
        check(!magic.elemental().cast(caster)&&magic.mana().account(caster).manaExact()==100,"direct spell with no target rejects before mana reserve");
        reset();staff(ElementalSpell.STONE_WALL);check(magic.elemental().cast(caster),"world cleanup wall setup");
        check(!world.getEntitiesByClass(BlockDisplay.class).isEmpty(),"temporary wall has live entities before cleanup");
        var walker=actor("ElementWalker",new Location(world,.5,100,2.5));var from=walker.getLocation();var to=from.clone().add(0,0,2);
        var movement=new PlayerMoveEvent(walker,from,to);Bukkit.getPluginManager().callEvent(movement);
        check(movement.getTo().distanceSquared(from)<.0001,"Stone Wall blocks an enemy player crossing the virtual collision box");
        var wallTeam=Bukkit.getScoreboardManager().getMainScoreboard().registerNewTeam("element-wall-team");wallTeam.addEntry(caster.getName());wallTeam.addEntry(walker.getName());
        try{movement=new PlayerMoveEvent(walker,from,to);Bukkit.getPluginManager().callEvent(movement);check(movement.getTo().distanceSquared(to)<.0001,"Stone Wall lets allied players pass");}
        finally{wallTeam.unregister();}
        var arrow=world.spawn(new Location(world,.5,101,2.5),Arrow.class);arrow.setShooter(walker);arrow.setGravity(false);arrow.setVelocity(new org.bukkit.util.Vector(0,0,3));ticks(1);
        check(!arrow.isValid(),"Stone Wall blocks an enemy native arrow before its next movement segment");
        var friendlyArrow=world.spawn(new Location(world,.5,101,2.5),Arrow.class);friendlyArrow.setShooter(caster);friendlyArrow.setGravity(false);friendlyArrow.setVelocity(new org.bukkit.util.Vector(0,0,3));ticks(1);
        check(friendlyArrow.isValid(),"Stone Wall allows allied native projectiles");friendlyArrow.remove();walker.teleport(new Location(world,-20.5,100,20.5));
        Bukkit.getPluginManager().callEvent(new PlayerChangedWorldEvent(caster,world));
        check(magic.effects().size()==0&&world.getEntitiesByClass(BlockDisplay.class).isEmpty(),"world change removes owned wall and pending effects");
        reset();target=mob(.5,4.5);target.getPersistentDataContainer().set(new NamespacedKey("7sins","control_resistant"),PersistentDataType.BYTE,(byte)1);
        check(magic.elemental().isBoss(target),"encounter PDC identifies crowd-control-resistant boss");
        for(int i=0;i<5;i++)castQuick(ElementalSpell.FROST_BOLT,5);
        check(!magic.elemental().hasStatus(target,"frozen")&&magic.elemental().hasStatus(target,"frost_vulnerability"),"five frost stacks give boss vulnerability instead of freeze");
        castQuick(ElementalSpell.BUBBLE_PRISON,1);check(!magic.elemental().hasStatus(target,"frozen"),"prison never hard-locks encounter bosses");
        reset();target=mob(.5,4.5);target.addScoreboardTag("sanctuary_boss");
        ElementalStats.Provider high=(player,base)->new ElementalStats.Snapshot(10000,6,100,0,0,Map.of());
        Bukkit.getServicesManager().register(ElementalStats.Provider.class,high,this,ServicePriority.Highest);
        try{staff(ElementalSpell.METEOR_RAIN);aim(true);check(magic.elemental().cast(caster),"boss damage cap meteor setup");ticks(150);check(1000-target.getHealth()<=50.0001&&target.getHealth()<1000,"all meteor pulses and Burn share five-percent boss health cap");}
        finally{Bukkit.getServicesManager().unregister(ElementalStats.Provider.class,high);}
        reset();target=mob(.5,4.5);double speed=target.getAttribute(Attribute.MOVEMENT_SPEED).getValue();castQuick(ElementalSpell.FROST_BOLT,5);
        check(target.getAttribute(Attribute.MOVEMENT_SPEED).getValue()<speed,"Frost slow changes actual movement speed");
        magic.elemental().close();near(target.getAttribute(Attribute.MOVEMENT_SPEED).getValue(),speed,"runtime cleanup removes movement modifier without an orphaned slow");
        reset();
    }
    void castQuick(ElementalSpell spell,int ticks){
        staff(spell);clearAccounting();aim(GROUND.contains(spell.id()));check(magic.elemental().cast(caster),"combo cast "+spell.id());ticks(ticks);
    }
    void reactions() throws Exception {
        reset();var target=mob(.5,4.5);castQuick(ElementalSpell.AQUA_BULLET,5);
        check(magic.elemental().hasStatus(target,"wet"),"water applies Wet without its own periodic damage");damage.clear();
        castQuick(ElementalSpell.FROST_BOLT,5);near(total(target),18*1.4,"Wet plus Ice adds a single affinity-scaled Flash Freeze damage reaction");
        check(magic.elemental().stacks(target,"frost")==3,"Flash Freeze adds two Frost stacks to normal one");damage.clear();
        castQuick(ElementalSpell.FROST_BOLT,5);near(total(target),18,"reaction internal cooldown blocks repeat bonus");
        check(magic.elemental().stacks(target,"frost")==4,"reaction cooldown does not block normal Frost stack");
        castQuick(ElementalSpell.FROST_BOLT,5);check(magic.elemental().hasStatus(target,"frozen")&&magic.elemental().stacks(target,"frost")==0,"five normal-target Frost stacks freeze and clear stacks");
        ticks(45);castQuick(ElementalSpell.BUBBLE_PRISON,1);check(!magic.elemental().hasStatus(target,"frozen"),"freeze immunity prevents a repeated prison lock");
        reset();target=mob(.5,4.5);for(int i=0;i<5;i++)castQuick(ElementalSpell.FIREBALL,5);
        check(magic.elemental().stacks(target,"burn")==5,"Burn stacks cap at five");damage.clear();
        castQuick(ElementalSpell.INFERNO_SPEAR,0);near(total(target),18*3.2,"Inferno Spear detonates five Burn stacks for 0.8x bonus");
        check(magic.elemental().stacks(target,"burn")==1,"detonated Burn does not retain old stacks");
        reset();target=mob(.5,4.5);castQuick(ElementalSpell.RADIANT_BOLT,5);check(magic.elemental().hasStatus(target,"radiance"),"Radiant Bolt marks target");damage.clear();
        castQuick(ElementalSpell.DIVINE_JUDGMENT,20);near(total(target),18*4.6,"Divine Judgment consumes Radiance for one bonus burst");check(!magic.elemental().hasStatus(target,"radiance"),"Radiance consumed after burst");
        reset();target=mob(.5,4.5);for(int i=0;i<3;i++)castQuick(ElementalSpell.SHADOW_BOLT,5);check(magic.elemental().stacks(target,"curse")==3,"shadow attacks accumulate independent Curse stacks");damage.clear();
        castQuick(ElementalSpell.ABYSSAL_COLLAPSE,115);near(total(target),18*(16*.2+16*.8+.6),"Abyssal Collapse consumes each Curse stack once");check(magic.elemental().stacks(target,"curse")==0,"Abyssal consumes Curse instead of duplicating it");
        reset();target=mob(.5,4.5);castQuick(ElementalSpell.SPARK_SHOT,5);castQuick(ElementalSpell.SPARK_SHOT,5);damage.clear();castQuick(ElementalSpell.SPARK_SHOT,5);
        near(total(target),18*1.7,"third Shock stack discharges one 0.7x bonus");check(magic.elemental().stacks(target,"shock")==0,"Discharge consumes the Shock stacks");
        reset();target=mob(.5,4.5);castQuick(ElementalSpell.AQUA_BULLET,5);damage.clear();castQuick(ElementalSpell.SPARK_SHOT,5);near(total(target),18*1.7,"Wet lightning triggers one bounded Electro-Charged reaction");
        damage.clear();castQuick(ElementalSpell.SPARK_SHOT,5);near(total(target),18,"Electro-Charged obeys the per-target two-second cooldown");
        reset();target=mob(.5,4.5);castQuick(ElementalSpell.SHADOW_BOLT,5);damage.clear();castQuick(ElementalSpell.RADIANT_BOLT,5);near(total(target),18*2.1,"Curse plus Light triggers Eclipse once");
        check(magic.elemental().hasStatus(target,"healing_reduction")&&magic.elemental().stacks(target,"curse")==0,"Eclipse consumes Curse and applies healing reduction");
        reset();target=mob(.5,4.5);ticks(7);castQuick(ElementalSpell.FIREBALL,100);
        var hits=damage.get(target.getUniqueId());var times=damageTimes.get(target.getUniqueId());
        check(hits.size()==4&&hits.subList(1,4).stream().allMatch(n->Math.abs(n-.9)<.0001),"Fireball Burn adds exactly three non-stacking affinity-scaled 0.05x ticks");
        check(times.get(2)-times.get(1)==20&&times.get(3)-times.get(2)==20,"Burn ticks follow application cadence independently of global phase");
        reset();target=mob(.5,4.5);ticks(11);castQuick(ElementalSpell.INFERNO_SPEAR,100);
        hits=damage.get(target.getUniqueId());check(hits.size()==5&&hits.subList(1,5).stream().allMatch(n->Math.abs(n-.9)<.0001),"standard Burn has exactly four affinity-scaled ticks and does not add new stacks itself");
        reset();
    }
    void phoenix() throws Exception {
        reset();var attacker=mob(.5,4.5);staff(ElementalSpell.PHOENIX_REBIRTH);caster.setHealth(4);
        check(!magic.elemental().cast(caster)&&magic.mana().account(caster).manaExact()==100,"Phoenix is passive and cannot be manually charged");
        check(magic.mana().reserveElemental(caster,ElementalSpell.FIREBALL,12,System.currentTimeMillis()),"passive Phoenix global cooldown fixture");magic.mana().account(caster).setMana(100);
        check(magic.mana().gcdRemaining(caster,System.currentTimeMillis())>0,"normal spell global cooldown active before lethal hit");
        caster.damage(20,attacker);
        check(!caster.isDead()&&caster.getHealth()>4,"pre-imprinted held Phoenix prevents lethal hit during ordinary GCD");
        near(magic.mana().account(caster).manaExact(),20,"Phoenix consumes its mana once when triggered");
        check(magic.mana().elementalRemaining(caster,ElementalSpell.PHOENIX_REBIRTH,System.currentTimeMillis())>119000,"Phoenix records its two-minute player cooldown");
        staff(ElementalSpell.PHOENIX_REBIRTH);magic.mana().account(caster).setMana(100);
        check(magic.mana().elementalRemaining(caster,ElementalSpell.PHOENIX_REBIRTH,System.currentTimeMillis())>119000,"different Phoenix staff cannot bypass passive cooldown");
        reset();staff(ElementalSpell.PHOENIX_REBIRTH);caster.setHealth(4);
        var voidHit=new EntityDamageEvent(caster,EntityDamageEvent.DamageCause.VOID,20);Bukkit.getPluginManager().callEvent(voidHit);
        check(!voidHit.isCancelled()&&magic.mana().account(caster).manaExact()==100,"Phoenix does not intercept Void death");
        reset();attacker=mob(.5,4.5);staff(ElementalSpell.PHOENIX_REBIRTH);caster.setHealth(4);cancelHealing=true;caster.damage(20,attacker);
        check(!caster.isDead()&&caster.getHealth()==1&&magic.mana().account(caster).manaExact()==20,"cancelled native regain respects heal protection while Phoenix leaves one health");
        reset();
    }
    InventoryClickEvent click(InventoryView view,int raw,ClickType type){
        InventoryClickEvent event=type==ClickType.NUMBER_KEY
                ?new InventoryClickEvent(view,InventoryType.SlotType.CONTAINER,raw,type,InventoryAction.HOTBAR_SWAP,2)
                :new InventoryClickEvent(view,InventoryType.SlotType.CONTAINER,raw,type,InventoryAction.PICKUP_ALL);
        Bukkit.getPluginManager().callEvent(event);return event;
    }
    void later(Runnable action){Bukkit.getScheduler().runTaskLater(this,()->{try{action.run();}catch(Throwable failure){finish(failure);}},2);}
    void gui(){
        reset();staff(ElementalSpell.FIREBALL);var scroll=magic.staffSpells().createScroll(ElementalSpell.INFERNO_SPEAR);scroll.setAmount(3);caster.getInventory().setItem(1,scroll);
        check(Bukkit.dispatchCommand(caster,"spell"),"cross-platform spell command opens standard inventory UI");
        var view=caster.getOpenInventory();check(view.getTopInventory().getSize()==27&&view.getTitle().equals("Staff Spell Management"),"staff manager has standard 27-slot inventory");
        var original=held().clone();
        for(var type:List.of(ClickType.SHIFT_LEFT,ClickType.SHIFT_RIGHT,ClickType.NUMBER_KEY,ClickType.DOUBLE_CLICK,ClickType.DROP,ClickType.CONTROL_DROP,ClickType.SWAP_OFFHAND))
            check(click(view,10,type).isCancelled()&&held().equals(original)&&caster.getInventory().getItem(1).getAmount()==3,"GUI blocks item movement exploit "+type);
        var drag=new InventoryDragEvent(view,new ItemStack(Material.AIR),new ItemStack(Material.DIAMOND),false,Map.of(0,new ItemStack(Material.DIAMOND)));
        Bukkit.getPluginManager().callEvent(drag);check(drag.isCancelled(),"GUI blocks dragging real items into previews");
        check(Bukkit.dispatchCommand(caster,"spell imprint")&&caster.getOpenInventory().getTitle().equals("Arcane Imprinting"),"imprinting page opens without transferring items");
        click(caster.getOpenInventory(),55,ClickType.LEFT);
        later(()->{
            check(magic.staffSpells().scroll(caster.getOpenInventory().getTopInventory().getItem(4))==ElementalSpell.INFERNO_SPEAR,"bottom inventory click chooses scroll preview");
            click(caster.getOpenInventory(),10,ClickType.LEFT);
            later(()->{
                var confirm=caster.getOpenInventory();check(confirm.getTitle().equals("Confirm Spell Imprint"),"overwrite requires explicit preview confirmation");
                check(magic.staffSpells().selected(held())==ElementalSpell.FIREBALL&&caster.getInventory().getItem(1).getAmount()==3,"opening confirmation does not consume items");
                click(confirm,15,ClickType.LEFT);click(confirm,15,ClickType.LEFT);
                check(magic.staffSpells().selected(held())==ElementalSpell.INFERNO_SPEAR&&caster.getInventory().getItem(1).getAmount()==2,"rapid duplicate confirm consumes exactly one scroll");
                later(()->{
                    check(caster.getOpenInventory().getTitle().equals("Staff Spell Management"),"successful imprint returns to staff manager");
                    var replacement=magic.staffSpells().createScroll(ElementalSpell.BURNING_GROUND);replacement.setAmount(2);caster.getInventory().setItem(1,replacement);
                    Bukkit.dispatchCommand(caster,"spell imprint");click(caster.getOpenInventory(),55,ClickType.LEFT);
                    later(()->{
                        click(caster.getOpenInventory(),10,ClickType.LEFT);
                        later(()->{
                            check(caster.getOpenInventory().getTitle().equals("Confirm Spell Imprint"),"second preview opened for stale-item defense");
                            var changed=held().clone();var meta=changed.getItemMeta();meta.setDisplayName("Changed since preview");changed.setItemMeta(meta);caster.getInventory().setItemInMainHand(changed);
                            click(caster.getOpenInventory(),15,ClickType.LEFT);
                            check(magic.staffSpells().selected(held())==ElementalSpell.INFERNO_SPEAR&&caster.getInventory().getItem(1).getAmount()==2&&held().equals(changed),"changed staff invalidates confirmation without loss or delayed writes");
                            caster.closeInventory();guiExtraction();
                        });
                    });
                });
            });
        });
    }
    void guiExtraction(){
        reset();staff(ElementalSpell.FIREBALL,ElementalSpell.AQUA_BULLET);var original=held().clone();
        check(Bukkit.dispatchCommand(caster,"spell extract")&&caster.getOpenInventory().getTitle().equals("Extract Spell Scroll"),"spell extraction command opens standard inventory picker");
        check(held().equals(original)&&scrollCount(ElementalSpell.AQUA_BULLET)==0,"opening extraction picker preserves staff and grants no preview item");
        click(caster.getOpenInventory(),11,ClickType.LEFT);
        later(()->{
            var confirm=caster.getOpenInventory();
            check(confirm.getTitle().equals("Confirm Spell Extraction")&&magic.staffSpells().scroll(confirm.getTopInventory().getItem(12))==ElementalSpell.AQUA_BULLET,"picker shows explicit exact-scroll extraction confirmation");
            for(ClickType type:List.of(ClickType.SHIFT_LEFT,ClickType.NUMBER_KEY,ClickType.DOUBLE_CLICK,ClickType.DROP,ClickType.SWAP_OFFHAND))
                check(click(confirm,15,type).isCancelled()&&held().equals(original)&&scrollCount(ElementalSpell.AQUA_BULLET)==0,"extraction preview blocks item movement exploit "+type);
            click(confirm,11,ClickType.LEFT);
            later(()->{
                check(caster.getOpenInventory().getTitle().equals("Extract Spell Scroll")&&held().equals(original),"cancelling extraction confirmation preserves entire loadout");
                click(caster.getOpenInventory(),11,ClickType.LEFT);
                later(()->{
                    var accepted=caster.getOpenInventory();click(accepted,15,ClickType.LEFT);click(accepted,15,ClickType.LEFT);
                    check(magic.staffSpells().spells(held()).equals(List.of(ElementalSpell.FIREBALL))&&scrollCount(ElementalSpell.AQUA_BULLET)==1,"rapid duplicate extraction confirmation removes and returns exactly one spell");
                    later(()->{
                        check(caster.getOpenInventory().getTitle().equals("Staff Spell Management"),"successful extraction returns to manager");
                        check(Bukkit.dispatchCommand(caster,"spell extract 1")&&caster.getOpenInventory().getTitle().equals("Confirm Spell Extraction"),"one-based extraction command still requires GUI confirmation");
                        var changed=held().clone();var meta=changed.getItemMeta();meta.setDisplayName("Changed after extraction preview");changed.setItemMeta(meta);caster.getInventory().setItemInMainHand(changed);
                        click(caster.getOpenInventory(),15,ClickType.LEFT);
                        check(held().equals(changed)&&magic.staffSpells().selected(held())==ElementalSpell.FIREBALL&&scrollCount(ElementalSpell.FIREBALL)==0,"changed staff invalidates extraction confirmation without scroll grant");
                        for(int slot=1;slot<36;slot++)caster.getInventory().setItem(slot,new ItemStack(Material.STONE,64));
                        Bukkit.dispatchCommand(caster,"spell extract 1");click(caster.getOpenInventory(),15,ClickType.LEFT);
                        check(held().equals(changed)&&magic.staffSpells().selected(held())==ElementalSpell.FIREBALL,"GUI extraction with full inventory preserves staff and spell");
                        later(this::guiWoodenImprint);
                    });
                });
            });
        });
    }
    void guiWoodenImprint(){
        reset();caster.getInventory().setItemInMainHand(magic.staffSpells().createWoodenStaff());
        caster.getInventory().setItem(1,magic.staffSpells().createScroll(ElementalSpell.AQUA_BULLET));
        Bukkit.dispatchCommand(caster,"spell imprint");click(caster.getOpenInventory(),55,ClickType.LEFT);
        later(()->{
            check(magic.staffSpells().scroll(caster.getOpenInventory().getTopInventory().getItem(4))==ElementalSpell.AQUA_BULLET,"neutral wood GUI accepts a scroll with an elemental affinity");
            click(caster.getOpenInventory(),10,ClickType.LEFT);
            later(()->{
                check(caster.getOpenInventory().getTitle().equals("Confirm Spell Imprint"),"wood GUI reaches imprint confirmation");click(caster.getOpenInventory(),15,ClickType.LEFT);
                check(magic.staffs().isWooden(held())&&magic.staffSpells().selected(held())==ElementalSpell.AQUA_BULLET&&caster.getInventory().getItem(1)==null,"wood GUI imprints a real usable spell and consumes one scroll");
                later(()->{
                    check(caster.getOpenInventory().getTopInventory().getItem(11).getType()==Material.BARRIER,"wood manager marks second spell slot locked before upgrade");
                    click(caster.getOpenInventory(),22,ClickType.LEFT);
                    later(()->{
                        check(caster.getOpenInventory().getTitle().equals("Arcane Codex"),"neutral wood staff can open elemental codex without null-element failure");
                        caster.closeInventory();finish(null);
                    });
                });
            });
        });
    }
    void finish(Throwable error){
        if(error!=null)getLogger().log(java.util.logging.Level.SEVERE,"ELEMENTAL SYSTEM FAILED after "+passed,error);
        try{if(magic!=null){magic.elemental().close();magic.effects().close();}}catch(Throwable ignored){}
        for(LivingEntity mob:mobs)mob.remove();
        for(ServerPlayer actor:actors){actor.getBukkitEntity().setOp(false);MinecraftServer.getServer().getPlayerList().getPlayers().remove(actor);MinecraftServer.getServer().getPlayerList().getPlayersByUUID().remove(actor.getUUID());actor.discard();}
        try{Files.writeString(Path.of("elemental-system-result.txt"),(error==null?"PASS "+passed:"FAIL after "+passed+": "+error)+"\n");}catch(Exception ignored){}
        Bukkit.getScheduler().runTaskLater(this,Bukkit::shutdown,2);
    }
}
