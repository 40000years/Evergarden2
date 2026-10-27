import com.example.voidscape.VoidscapePlugin;
import com.example.voidscape.boss.*;
import com.example.voidscape.compat.LevelledMobsCompat;
import com.example.voidscape.world.WorldBossTempleLayout;
import com.example.advancemagic.AdvanceMagicPlugin;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.server.network.*;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.damage.DamageType;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import java.net.URI;
import java.net.http.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.*;
import java.net.InetSocketAddress;
import java.nio.file.*;
import java.lang.reflect.*;
import java.util.*;

/** Isolated real Paper integration; deliberately not shipped in the release. */
public final class AncientJudgeProbe extends JavaPlugin {
    int checks;VoidscapePlugin garden;WorldBossManager boss;AdvanceMagicPlugin magic;
    final List<ServerPlayer> actors=new ArrayList<>();
    void check(boolean value,String label){if(!value)throw new AssertionError(label);checks++;getLogger().info("PASS Judge: "+label);}
    Object field(Object target,String name)throws Exception{Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}
    void setField(Object target,String name,Object value)throws Exception{Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);f.set(target,value);}
    @Override public void onEnable(){Bukkit.getScheduler().runTaskLater(this,()->{
        try{run();Files.writeString(Path.of("ancient-judge-result.txt"),"PASS "+checks+" real Paper checks + "+JudgmentRulesChecks.run()+" encounter rules checks");}
        catch(Throwable error){getLogger().log(java.util.logging.Level.SEVERE,"JUDGE CHECK FAILED",error);try{Files.writeString(Path.of("ancient-judge-result.txt"),"FAIL "+error);}catch(Exception ignored){}}
        finally{cleanup();Bukkit.getScheduler().runTaskLater(this,Bukkit::shutdown,5);}
    },40);}
    @SuppressWarnings("unchecked") void run()throws Exception{
        garden=(VoidscapePlugin)Bukkit.getPluginManager().getPlugin("Evergarden");
        magic=(AdvanceMagicPlugin)Bukkit.getPluginManager().getPlugin("advance-magic");
        check(garden!=null&&garden.isEnabled()&&magic!=null&&magic.isEnabled(),"both release plugins boot");
        boss=garden.worldBoss();World world=garden.world();
        balanceConfigChecks();
        var site=garden.bossTemples().nearest(0,0,12);check(site!=null,"new and previously generated arenas are located");
        Path checkpoint=Path.of("judge-checkpoint.txt");
        if(Files.exists(checkpoint)){
            List<String> pending=Files.readAllLines(checkpoint);
            UUID returningId=UUID.fromString(pending.get(1));String rolledCore=pending.get(2);
            var rewards=(org.bukkit.configuration.file.YamlConfiguration)field(boss,"ledger");
            String rewardPath="rewards."+returningId;
            check(rewards.getStringList(rewardPath+".cores").equals(List.of(rolledCore)),"dead/offline contributor keeps the exact core roll across a full restart");
            Player returning=actor(world,site,"ReturningJudge",returningId);
            boss.claimReward(returning);
            check(coreIds(returning).equals(List.of(rolledCore))&&!rewards.contains(rewardPath),"returning contributor receives exactly the saved core and consumes its pending reward");
            int exp=returning.getTotalExperience();boss.claimReward(returning);
            check(coreIds(returning).equals(List.of(rolledCore))&&returning.getTotalExperience()==exp,"repeated claim after restart cannot duplicate a core or XP");
            Player actor=actor(world,site,"RestartActor");
            check(boss.status(actor).contains("ฟื้นตัว"),"victory cooldown survives a full Paper restart");
            check(!boss.start(actor,false)&&boss.activeCount()==0,"ordinary summoning cannot bypass persisted cooldown");
            check(boss.start(actor,true),"admin can explicitly start another test encounter");
            boss.stop(actor);check(boss.activeCount()==0,"admin stop fully cleans an encounter after restart");return;
        }
        Player a=actor(world,site,"JudgeSolo");Player b=actor(world,site,"JudgeDuo");Player outsider=actor(world,site,"JudgeObserver");
        seraphPreviewChecks(a,outsider,world);
        hotfixChecks(a,world,site);
        outsider.teleport(new Location(world,site.x()+70.5,101,site.z()+.5));
        BlockStateSnapshot floor=new BlockStateSnapshot(world,site.x(),site.z());
        var altar=new PlayerInteractEvent(a,Action.RIGHT_CLICK_BLOCK,null,world.getBlockAt(site.x(),100,site.z()),org.bukkit.block.BlockFace.UP,EquipmentSlot.HAND);
        Bukkit.getPluginManager().callEvent(altar);
        check(altar.isCancelled()&&boss.activeCount()==1,"ordinary player summons at the existing amethyst altar with an empty hand");
        check(boss.inCombat(a)&&!boss.inCombat(outsider),"only actual participants enter combat; nearby visitors do not scale HP");
        Object run=((Map<?,?>)field(boss,"active")).values().iterator().next();
        JudgmentFight fight=(JudgmentFight)field(run,"fight");
        check(fight.coreMax()==10000&&fight.hand(JudgmentFight.Part.LEFT)==1500&&fight.hand(JudgmentFight.Part.RIGHT)==1500,"legacy config starts with 10000 core HP and 1500 HP per hand");
        check((long)field(run,"nextJudgment")-(long)field(boss,"tick")==800,"phase-one first judgment is scheduled after 40 seconds");
        Map<JudgmentFight.Part,Slime> targets=(Map<JudgmentFight.Part,Slime>)field(run,"targets");
        Slime left=targets.get(JudgmentFight.Part.LEFT),right=targets.get(JudgmentFight.Part.RIGHT),core=targets.get(JudgmentFight.Part.CORE);
        check(targets.size()==3&&targets.values().stream().allMatch(Entity::isValid),"all three living hitboxes bypass the dimension spawn filter");
        for(Slime slime:targets.values()){
            check(LevelledMobsCompat.isEvergardenMob(slime)&&slime.getScoreboardTags().contains("no-level"),"hitbox is excluded from external LevelMob changes");
            check(slime.hasPotionEffect(PotionEffectType.INVISIBILITY)&&!slime.hasAI()&&!slime.hasGravity(),"hitbox does not appear as an armored vanilla monster");
            check(magic.context().enemy(a,slime),"Advance Magic targeting accepts every boss part");
            check(((CraftEntity)slime).getHandle().isPickable()&&((CraftEntity)slime).getHandle().canBeHitByProjectile(),"vanilla client selection and arrow collision both accept the proxy");
        }
        List<Entity> owned=world.getEntities().stream().filter(e->e.getPersistentDataContainer().has(garden.key("world_boss_entity"),PersistentDataType.BYTE)).toList();
        long blocks=owned.stream().filter(FallingBlock.class::isInstance).count();
        long fallback=owned.stream().filter(BlockDisplay.class::isInstance).count();
        check(fallback>=550&&fallback<=750&&blocks==0,"solid model fallback has a bounded display budget: "+fallback);
        check(owned.stream().filter(ItemDisplay.class::isInstance).map(ItemDisplay.class::cast).filter(e->e.getItemStack().getType()==Material.IRON_HELMET).count()==25&&owned.stream().filter(ArmorStand.class::isInstance).count()==25,"Java sculpture and Bedrock carriers use 25 components each");
        try(var reader=new java.io.InputStreamReader(garden.getResource("judge-design.json"),java.nio.charset.StandardCharsets.UTF_8)){
            var design=com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
            check(garden.judgePack().modelInfo().contains(design.get("revision").getAsString())&&garden.judgePack().modelInfo().contains(design.get("bedrock-version").getAsString()),"pack diagnostics identify the bundled grand model revision and Bedrock version");
        }
        for(ItemDisplay display:owned.stream().filter(ItemDisplay.class::isInstance).map(ItemDisplay.class::cast).toList()){
            String model=display.getItemStack().getItemMeta().getItemModel().getKey();
            if(model.startsWith("judge_wing")&&!model.endsWith("_light"))check(display.getDisplayWidth()>40,"wing display culling covers the outermost ivory vane");
            if(model.equals("judge_mantle"))check(display.getDisplayWidth()>48,"mantle display culling covers both suspended cloak tips");
        }
        check(owned.stream().filter(BlockDisplay.class::isInstance).map(BlockDisplay.class::cast).anyMatch(e->e.getDisplayWidth()>48),"vanilla fallback uses the enlarged mantle culling bounds too");
        check(owned.stream().filter(ItemDisplay.class::isInstance).map(ItemDisplay.class::cast).noneMatch(e->e.getItemStack().getType()==Material.PAPER),"idle boss has no decorative spell planes covering its face");
        check(owned.stream().filter(ArmorStand.class::isInstance).allMatch(e->!((CraftEntity)e).getHandle().isPickable()),"decoration carriers cannot intercept swords or projectiles");
        check(owned.stream().filter(ArmorStand.class::isInstance).noneMatch(a::canSee),"Java clients never see Bedrock helmet carriers");
        check(owned.stream().allMatch(e->!e.isPersistent()),"runtime entities cannot survive chunk serialization");
        check(owned.stream().filter(ItemDisplay.class::isInstance).noneMatch(a::canSee)&&owned.stream().filter(BlockDisplay.class::isInstance).anyMatch(a::canSee),"without accepted pack Java sees the sculpture, no missing-texture models");
        var packReply=HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(garden.judgePack().url(null))).GET().build(),HttpResponse.BodyHandlers.ofByteArray());
        check(packReply.statusCode()==200&&Arrays.equals(packReply.body(),garden.getResource("resource-packs/judge-java.zip").readAllBytes()),"automatic local pack host serves the exact embedded addon");
        Bukkit.getPluginManager().callEvent(new PlayerResourcePackStatusEvent(a,JudgePackService.PACK_ID,PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED));
        for(int i=0;i<2;i++)boss.tick();
        check(owned.stream().filter(BlockDisplay.class::isInstance).noneMatch(a::canSee)&&owned.stream().filter(ItemDisplay.class::isInstance).anyMatch(a::canSee),"pack acceptance swaps only that viewer to the new sculpture");
        check(owned.stream().filter(BlockDisplay.class::isInstance).anyMatch(outsider::canSee),"another viewer without the addon retains the fallback");
        Bukkit.getPluginManager().callEvent(new PlayerResourcePackStatusEvent(a,JudgePackService.PACK_ID,PlayerResourcePackStatusEvent.Status.FAILED_RELOAD));
        for(int i=0;i<2;i++)boss.tick();
        check(owned.stream().filter(ItemDisplay.class::isInstance).noneMatch(a::canSee)&&owned.stream().filter(BlockDisplay.class::isInstance).anyMatch(a::canSee),"reload failure returns to usable fallback");
        Bukkit.getPluginManager().callEvent(new PlayerResourcePackStatusEvent(a,JudgePackService.PACK_ID,PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED));
        for(int i=0;i<2;i++)boss.tick();
        // Give the weapon stress test enough HP to measure multiple uncapped hits without breaking a part.
        setField(fight,"left",20000.0);setField(fight,"right",20000.0);
        double hp=fight.hand(JudgmentFight.Part.LEFT);
        double actual=magic.context().damage(a,left,1000,DamageType.MAGIC);
        check(Math.abs(fight.hand(JudgmentFight.Part.LEFT)-(hp-1000))<.01,"a real Advance Magic damage call retains all 1000 damage");
        check(actual>0&&left.getHealth()>0,"accepted hits report real HP loss for lifesteal without killing a hitbox");
        double before=fight.core();magic.context().damage(a,core,2000,DamageType.MAGIC);
        check(fight.core()==before,"sealed core cannot be killed by an opening spell combo");
        magic.statuses().timeLock(a,left,40);
        check(((Map<?,?>)field(magic.statuses(),"roots")).isEmpty(),"encounter control marker prevents permanent time-lock cheese");
        hp=fight.hand(JudgmentFight.Part.LEFT);left.damage(1000);check(fight.hand(JudgmentFight.Part.LEFT)==hp,"environmental and unattributed damage cannot farm the boss");
        for(int i=0;i<10;i++)boss.tick();
        a.teleport(left.getLocation().add(0,0,-7));a.getInventory().setItemInMainHand(new ItemStack(Material.DIAMOND_SWORD));
        var pick=world.rayTraceEntities(a.getEyeLocation(),new org.bukkit.util.Vector(0,0,1),5,e->e!=a&&((CraftEntity)e).getHandle().isPickable());
        check(pick!=null&&pick.getHitEntity()==left,"a vanilla-style selection ray aimed at the palm selects its hitbox");
        left.setNoDamageTicks(0);actors.getFirst().attack(((CraftEntity)left).getHandle());
        check(fight.hand(JudgmentFight.Part.LEFT)<hp,"actual NMS sword attack reaches boss health");
        hp=fight.hand(JudgmentFight.Part.LEFT);left.setNoDamageTicks(0);
        Arrow arrow=world.spawn(left.getLocation().add(0,1.6,-9),Arrow.class,e->{e.setShooter(a);e.setGravity(false);e.setVelocity(new org.bukkit.util.Vector(0,0,2));});
        var arrowHandle=((CraftEntity)arrow).getHandle();for(int i=0;i<5&&arrow.isValid();i++)arrowHandle.tick();
        check(fight.hand(JudgmentFight.Part.LEFT)<hp,"a moving vanilla arrow intersects the proxy and applies bow damage");arrow.remove();
        for(int i=0;i<10;i++)boss.tick();
        a.getInventory().setItemInMainHand(com.example.voidscape.gui.AdminTestGui.createGodBow());
        Arrow titan=world.spawn(left.getLocation().add(0,1,-9),Arrow.class,e->{e.setShooter(a);e.setGravity(false);e.setVelocity(new org.bukkit.util.Vector(0,0,2));e.setDamage(100);});
        Bukkit.getPluginManager().callEvent(new EntityShootBowEvent(a,a.getInventory().getItemInMainHand(),new ItemStack(Material.ARROW),titan,EquipmentSlot.HAND,1,true));
        hp=fight.hand(JudgmentFight.Part.LEFT);left.setNoDamageTicks(0);
        for(int i=0;i<5&&titan.isValid();i++)((CraftEntity)titan).getHandle().tick();
        double titanDamage=hp-fight.hand(JudgmentFight.Part.LEFT);
        check(titanDamage>80,"actual Titan Hunter dev bow including Max HP bonus exceeds the removed arrow cap: "+titanDamage);titan.remove();
        double rightHp=fight.hand(JudgmentFight.Part.RIGHT);magic.context().damage(a,right,1000,DamageType.MAGIC);
        check(Math.abs(rightHp-fight.hand(JudgmentFight.Part.RIGHT)-1000)<.02,"arrow hits do not reduce a same-tick wand hit on the other hand");
        hp=fight.hand(JudgmentFight.Part.LEFT);magic.context().damage(a,left,1000,DamageType.MAGIC);
        check(Math.abs(hp-fight.hand(JudgmentFight.Part.LEFT)-1000)<.02,"repeated spells in the same tick have no rolling damage budget");
        magic.context().damage(b,right,1650,DamageType.MAGIC);
        check(fight.roster().size()==2&&Math.abs(fight.scale()-1.65)<.001,"second real attacker increases effective HP exactly once");
        check(!fight.roster().contains(outsider.getUniqueId()),"uninvolved visitor remains absent from the roster");
        for(int i=0;i<10;i++)boss.tick();
        hp=fight.hand(JudgmentFight.Part.LEFT);left.setNoDamageTicks(0);left.damage(1000,a);
        check(Math.abs((hp-fight.hand(JudgmentFight.Part.LEFT))*fight.scale()-1000)<.02,"real melee retains all damage after multiplayer scaling");
        for(int i=0;i<10;i++)boss.tick();
        TNTPrimed damageOrigin=world.spawn(left.getLocation(),TNTPrimed.class,e->e.setFuseTicks(100000));
        hp=fight.hand(JudgmentFight.Part.LEFT);left.setNoDamageTicks(0);
        left.damage(1000,org.bukkit.damage.DamageSource.builder(DamageType.PLAYER_EXPLOSION).withCausingEntity(a).withDirectEntity(damageOrigin).build());damageOrigin.remove();
        check(Math.abs((hp-fight.hand(JudgmentFight.Part.LEFT))*fight.scale()-1000)<.02,"player-attributed explosions have no encounter damage cap");
        // Schedule the exact live rare attack, not a hand-built damage event.
        Class<?> attack=Class.forName("com.example.voidscape.boss.WorldBossManager$Attack");
        Object judgment=Arrays.stream(attack.getEnumConstants()).filter(e->e.toString().equals("JUDGMENT")).findFirst().orElseThrow();
        Method begin=boss.getClass().getDeclaredMethod("begin",run.getClass(),attack,List.class);begin.setAccessible(true);
        begin.invoke(boss,run,judgment,List.of(a,b));
        check(field(run,"attack")==judgment,"phase one now accepts the same judgment skill as phase two");
        long phaseOneCooldown=(long)field(run,"nextJudgment")-(long)field(run,"resolveAt");
        check(phaseOneCooldown>=4600&&phaseOneCooldown<=5000,"phase-one judgment uses the modestly shorter 230-250 second cooldown");
        outsider.teleport(new Location(world,site.x()+.5,101,site.z()+.5));
        outsider.getAttribute(Attribute.MAX_HEALTH).setBaseValue(80);outsider.setHealth(80);outsider.setInvulnerable(false);
        Material[] armor={Material.NETHERITE_BOOTS,Material.NETHERITE_LEGGINGS,Material.NETHERITE_CHESTPLATE,Material.NETHERITE_HELMET};
        ItemStack[] gear=new ItemStack[4];for(int i=0;i<4;i++){gear[i]=new ItemStack(armor[i]);gear[i].addUnsafeEnchantment(Enchantment.PROTECTION,4);}
        outsider.getInventory().setArmorContents(gear);
        magic.context().damage(outsider,right,100,DamageType.MAGIC);
        Object cross=Arrays.stream(attack.getEnumConstants()).filter(e->e.toString().equals("CROSS")).findFirst().orElseThrow();
        begin.invoke(boss,run,cross,List.of(a,outsider));
        long warningTicks=(long)field(run,"resolveAt")-(long)field(boss,"tick");
        check(warningTicks==40,"phase-one cross has a faster two-second warning");
        for(int i=0;i<warningTicks/2-1;i++)boss.tick();
        check(outsider.getHealth()==80,"ordinary attack never damages before its full warning");
        boss.tick();check(Math.abs(outsider.getHealth()-7.2)<.02,"red crossing lanes deal 72.8 HP through armor, exactly 30 percent above the old 56 HP");
        Object sky=Arrays.stream(attack.getEnumConstants()).filter(e->e.toString().equals("SKY_BEAMS")).findFirst().orElseThrow();
        outsider.getAttribute(Attribute.MAX_HEALTH).setBaseValue(100);outsider.setHealth(100);outsider.setNoDamageTicks(0);
        begin.invoke(boss,run,sky,List.of(outsider));
        List<Location> skyMarks=new ArrayList<>((List<Location>)field(run,"marks"));
        check(skyMarks.size()==6,"phase-one aerial volley creates multiple separate magic circles");
        for(int i=0;i<skyMarks.size();i++)for(int j=i+1;j<skyMarks.size();j++)check(skyMarks.get(i).distanceSquared(skyMarks.get(j))>=400,"aerial circle centers leave a gap between their eighteen-block diameters");
        Object bodySky=field(run,"body");Map<?,?> planesSky=(Map<?,?>)field(field(bodySky,"sigils"),"planes");
        check(planesSky.entrySet().stream().filter(e->e.getKey().toString().startsWith("attack:sky-cast:")).map(e->(ItemDisplay)e.getValue()).allMatch(e->Math.abs(e.getY()+e.getTransformation().getTranslation().y()-161.1)<.01),"aerial circles share one height above the whole crown rather than stacking");
        outsider.teleport(skyMarks.getFirst());
        for(int i=0;i<21;i++)boss.tick();check(outsider.getHealth()==100,"aerial beams wait the full warning");
        boss.tick();check(Math.abs(outsider.getHealth()-16.28)<.02,"red aerial beams deal 83.72 HP, exactly 30 percent above the old 64.4 HP; HP="+outsider.getHealth());
        for(int i=0;i<2;i++)boss.tick();check(planesSky.keySet().stream().filter(k->k.toString().startsWith("fx:cast:")).count()==6,"sky beams keep every aerial casting circle visible while firing");
        for(int i=0;i<24;i++)boss.tick();check(field(run,"attack")!=sky,"attack dispatcher never repeats the previous pattern consecutively");
        Object lances=Arrays.stream(attack.getEnumConstants()).filter(e->e.toString().equals("LANCES")).findFirst().orElseThrow();
        outsider.getAttribute(Attribute.MAX_HEALTH).setBaseValue(80);outsider.setHealth(80);outsider.setNoDamageTicks(0);outsider.teleport(new Location(world,site.x()+.5,101,site.z()+.5));
        begin.invoke(boss,run,lances,List.of(outsider));
        check(planesSky.keySet().stream().filter(k->k.toString().startsWith("attack:lance-lane:")).count()==3,"lance attack warns three separate parallel corridors");
        for(int i=0;i<20;i++)boss.tick();
        check(Math.abs(outsider.getHealth()-7.2)<.02,"red lance corridors also receive the exact 30 percent damage boost");
        for(int i=0;i<2;i++)boss.tick();
        check(planesSky.keySet().stream().filter(k->k.toString().startsWith("fx:lance:")).count()==3,"lance attack fires three visible horizontal rays");
        Object slam=Arrays.stream(attack.getEnumConstants()).filter(e->e.toString().equals("SLAM")).findFirst().orElseThrow();
        outsider.setHealth(80);outsider.setNoDamageTicks(0);
        begin.invoke(boss,run,slam,List.of(a));
        Location mark=((List<Location>)field(run,"marks")).getFirst().clone();
        outsider.teleport(mark.clone().add(10,0,0));
        a.teleport(mark.clone().add(0,0,15));
        for(int i=0;i<23;i++)boss.tick();
        check(((List<Location>)field(run,"marks")).getFirst().distanceSquared(mark)<.001&&outsider.getHealth()==80,"larger slam locks its warning position and retains the full escape time");
        boss.tick();check(Math.abs(outsider.getHealth()-7.2)<.02,"red slam deals 30 percent more damage ten blocks from its locked center");
        Object ring=Arrays.stream(attack.getEnumConstants()).filter(e->e.toString().equals("RING")).findFirst().orElseThrow();
        outsider.setHealth(80);outsider.setNoDamageTicks(0);begin.invoke(boss,run,ring,List.of(outsider));
        outsider.teleport(new Location(world,site.x()+.5+(double)field(run,"ringRadius"),101,site.z()+.5));
        for(int i=0;i<20;i++)boss.tick();
        check(Math.abs(outsider.getHealth()-7.2)<.02,"red ring-wave damage also rises by exactly 30 percent");
        outsider.setInvulnerable(true);
        // Drive phase transitions with a burst; no cap configuration is needed.
        for(int i=0;i<10;i++)boss.tick();
        magic.context().damage(a,left,1000000,DamageType.MAGIC);
        check(fight.exposed((long)field(boss,"tick")),"destroying a hand opens the real core");
        check(core.getY()<=102&&core.getSize()==8,"breaking a hand immediately moves the real weakpoint into melee range");
        check(field(run,"attack")==null&&(long)field(run,"nextAttack")-(long)field(boss,"tick")==60,"opening gives three seconds of approach time instead of pausing the whole damage window");
        for(int i=0;i<20;i++)boss.tick();
        check(core.getY()<=102,"exposed core hitbox descends into melee reach");
        ItemDisplay heart=owned.stream().filter(ItemDisplay.class::isInstance).map(ItemDisplay.class::cast).filter(e->e.getItemStack().getItemMeta().getItemModel().getKey().equals("judge_heart")).findFirst().orElseThrow();
        var visualCenter=heart.getLocation().add(0,heart.getTransformation().getTranslation().y(),0);
        check(Math.abs(visualCenter.getY()-core.getBoundingBox().getCenterY())<.05&&visualCenter.distance(core.getLocation().add(0,2.08,0))<.05,"exposed gemstone and its real hitbox share the same center");
        a.teleport(core.getLocation().add(0,0,-4));hp=fight.core();core.setNoDamageTicks(0);
        actors.getFirst().attack(((CraftEntity)core).getHandle());
        check(fight.core()<hp,"sword attack works against the exposed gemstone too");
        hp=fight.core();core.setNoDamageTicks(0);magic.context().damage(a,core,100,DamageType.MAGIC);
        check(Math.abs((hp-fight.core())*fight.scale()-200)<.02,"one broken hand doubles actual wand damage against the exposed core");
        right.setNoDamageTicks(0);magic.context().damage(a,right,1000000,DamageType.MAGIC);
        hp=fight.core();core.setNoDamageTicks(0);magic.context().damage(a,core,100,DamageType.MAGIC);
        check(Math.abs((hp-fight.core())*fight.scale()-300)<.02,"breaking both hands in one opening triples actual wand core damage");
        magic.context().damage(a,core,1000000,DamageType.MAGIC);
        check(fight.phase()==2&&Math.abs(fight.core()/fight.coreMax()-.7)<.001,"a huge spell stops at 70 percent and enters phase two");
        check(left.getY()>=101&&right.getY()>=101,"phase two immediately restores both reachable hand hitboxes");
        magic.context().damage(a,core,1000000,DamageType.MAGIC);
        check(fight.phase()==2,"continued AoE from the same combo cannot skip another sealed phase");
        check((long)field(run,"nextJudgment")-(long)field(boss,"tick")==240,"phase-two first judgment is due twelve seconds after transition");
        Arrow shieldArrow=world.spawn(right.getLocation(),Arrow.class,e->{e.setShooter(a);e.setGravity(false);});
        hp=fight.hand(JudgmentFight.Part.RIGHT);right.setNoDamageTicks(0);
        right.damage(500,org.bukkit.damage.DamageSource.builder(DamageType.ARROW).withCausingEntity(a).withDirectEntity(shieldArrow).build());
        check(fight.hand(JudgmentFight.Part.RIGHT)==hp,"phase-two shield blocks player arrows against hands");
        right.setNoDamageTicks(0);magic.context().damage(a,right,100,DamageType.MAGIC);
        check(Math.abs((hp-fight.hand(JudgmentFight.Part.RIGHT))*fight.scale()-100)<.02,"phase-two shield allows actual wand damage attributed directly to the player");
        hp=fight.hand(JudgmentFight.Part.RIGHT);right.setNoDamageTicks(0);
        var indirectSource=org.bukkit.damage.DamageSource.builder(DamageType.INDIRECT_MAGIC).withCausingEntity(a).withDirectEntity(shieldArrow).build();
        Method classify=boss.getClass().getDeclaredMethod("damageKind",EntityDamageEvent.class);classify.setAccessible(true);
        var indirectEvent=new EntityDamageByEntityEvent(shieldArrow,right,EntityDamageEvent.DamageCause.MAGIC,indirectSource,100);
        check(classify.invoke(boss,indirectEvent)==JudgmentFight.DamageKind.MAGIC,"magic projectile classification takes precedence over physical carriers: "+indirectSource.getDamageType());
        right.damage(100,indirectSource);
        check((hp-fight.hand(JudgmentFight.Part.RIGHT))*fight.scale()>=99.99,"magic projectile bypasses the phase-two shield and retains weapon bonuses");
        shieldArrow.remove();
        begin.invoke(boss,run,judgment,List.of(a,b));
        var seals=world.getEntities().stream().filter(ItemDisplay.class::isInstance).map(ItemDisplay.class::cast).filter(e->e.getItemStack().getType()==Material.PAPER).toList();
        check(seals.stream().filter(a::canSee).count()==8,"judgment shows four green sanctuaries, a floor rim and three spell seals");
        check(seals.stream().filter(e->e.getItemStack().getItemMeta().getItemModel().getKey().equals("judge_seal")).allMatch(e->e.getY()+e.getTransformation().getTranslation().y()>=169),"judgment casting seals sit above the enlarged crown instead of through the face");
        check(seals.stream().noneMatch(outsider::canSee),"clients without the addon do not see missing-texture spell models");
        check(seals.stream().allMatch(e->e.getBrightness().getBlockLight()==15&&e.getBrightness().getSkyLight()==15),"spell circles remain full-bright in the dark temple");
        long now=(long)field(boss,"tick"),resolve=(long)field(run,"resolveAt");
        check(resolve-now==160,"rare one-hit grants the configured full eight-second telegraph");
        check((long)field(run,"nextJudgment")-resolve>=2300&&(long)field(run,"nextJudgment")-resolve<=2700,"one-hit cooldown is 115-135 seconds");
        double safeX=(double)field(run,"safeX"),safeZ=(double)field(run,"safeZ");
        a.teleport(new Location(world,site.x()+.5+safeX,101,site.z()+.5+safeZ));
        outsider.teleport(a.getLocation());
        Player visitor=actor(world,site,"JudgeVisitor");visitor.teleport(new Location(world,site.x()+70.5,101,site.z()+.5));
        b.teleport(new Location(world,site.x()+.5,250,site.z()+.5)); // flight above the arena is not an escape
        b.setInvulnerable(false);b.getAttribute(Attribute.MAX_HEALTH).setBaseValue(500);b.setHealth(500);b.getAttribute(Attribute.MAX_ABSORPTION).setBaseValue(256);b.setAbsorptionAmount(256);
        b.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE,1200,4,false,false));
        for(ItemStack item:gear)item.addUnsafeEnchantment(Enchantment.PROTECTION,10);
        gear[2]=com.example.voidscape.gui.AdminTestGui.createGodChestplate();
        b.getInventory().setArmorContents(gear);b.getInventory().setItemInOffHand(new ItemStack(Material.TOTEM_OF_UNDYING));
        double aHp=a.getHealth(),outsideHp=visitor.getHealth();
        Bukkit.getPluginManager().registerEvents(new org.bukkit.event.Listener(){
            @org.bukkit.event.EventHandler(priority=org.bukkit.event.EventPriority.MONITOR)
            public void audit(EntityDamageEvent e){if(e.getEntity()==b)getLogger().info("ONE HIT AUDIT: raw="+e.getDamage()+" final="+e.getFinalDamage()+" cancelled="+e.isCancelled()+" type="+e.getDamageSource().getDamageType()+" abs="+b.getAbsorptionAmount());}
        },this);
        check(world.getEntities().stream().filter(FallingBlock.class::isInstance).count()==blocks+52,"green sanctuary markers remain visible even with particles disabled");
        for(int i=0;i<79;i++)boss.tick();
        check(b.getHealth()==500&&!b.isDead(),"one-hit deals no damage before the complete warning ends");
        boss.tick();
        check(b.isDead()&&b.getHealth()==0,"live one-hit defeats high HP, absorption, armor, resistance, totem, Phoenix and flight; health="+b.getHealth()+" absorption="+b.getAbsorptionAmount()+" position="+b.getLocation()+" attack="+field(run,"attack")+" tick="+field(boss,"tick"));
        check(a.getHealth()==aHp,"participant inside the green sanctuary takes no judgment damage");
        check(visitor.getHealth()==outsideHp&&!visitor.isDead(),"uninvolved visitor is not struck by the encounter");
        check(!((Map<?,?>)field(garden.abilities(),"phoenixCooldown")).containsKey(b.getUniqueId()),"forced death never activates Phoenix's damage-event rescue");
        check(field(run,"attack")==null,"normal attacks resume only after the rare charge resolves");
        check(world.getEntities().stream().filter(FallingBlock.class::isInstance).count()==blocks,"sanctuary entities are cleaned immediately after judgment");
        check(fight.scale()==2.30,"a death does not lower boss HP or reset the difficulty");
        begin.invoke(boss,run,cross,List.of(a));
        double firstAngle=(double)field(run,"crossAngle");
        check((int)field(run,"pulsesLeft")==2&&(long)field(run,"resolveAt")-(long)field(boss,"tick")==36,"phase two uses two faster cross pulses");
        Object body=field(run,"body"),sigils=field(body,"sigils");
        ItemDisplay lane=(ItemDisplay)((Map<?,?>)field(sigils,"planes")).get("attack:cross-x");
        check(lane.getTransformation().getScale().x()==160&&lane.getTransformation().getScale().y()==11,"rotated lane artwork spans the arena with the same eleven-block width as damage");
        for(int i=0;i<18;i++)boss.tick();
        check(field(run,"attack")==cross&&(int)field(run,"pulsesLeft")==1&&Math.abs((double)field(run,"crossAngle")-firstAngle-Math.PI/6)<1e-9,"second cross pulse rotates by thirty degrees with a fresh warning");
        for(int i=0;i<18;i++)boss.tick();
        check(field(run,"attack")==null&&(long)field(run,"nextAttack")-(long)field(boss,"tick")==56,"phase-two cross combo now has 2.8 seconds of recovery");
        magic.context().damage(a,right,1000000,DamageType.MAGIC);
        for(int i=0;i<10;i++)boss.tick();magic.context().damage(a,core,1000000,DamageType.MAGIC);
        Map<UUID,Mob> summons=(Map<UUID,Mob>)field(run,"summons");
        check(summons.size()==fight.roster().size()&&summons.size()==3,"phase three summons one temple boss per participant including the retained roster");
        check(summons.values().stream().map(Entity::getType).collect(java.util.stream.Collectors.toSet()).size()==3,"all three real temple boss species are summoned");
        check(summons.values().stream().allMatch(m->m.isValid()&&m.getAttribute(Attribute.SCALE).getValue()==1.8&&boss.owns(m)),"summoned bosses have temple scale and encounter ownership");
        for(Mob m:summons.values())m.setAI(false);
        Mob summoned=summons.values().iterator().next();double summonedHp=summoned.getHealth();
        visitor.getInventory().setItemInMainHand(new ItemStack(Material.DIAMOND_SWORD));summoned.setNoDamageTicks(0);summoned.damage(200,visitor);
        check(summonedHp-summoned.getHealth()>100,"temple summons also exceed the removed melee cap after armor modifiers");
        for(int i=0;i<5;i++)boss.tick();
        check(summons.size()==4&&fight.roster().size()==4,"joining phase three by hitting a summon adds exactly one extra temple boss");
        double budgetHp=fight.core();magic.context().damage(visitor,core,1000,DamageType.MAGIC);
        check(fight.core()==budgetHp,"phase-three magic immunity also applies to a late participant");
        var noVisitorTarget=new EntityTargetLivingEntityEvent(summoned,b,EntityTargetEvent.TargetReason.CLOSEST_PLAYER);Bukkit.getPluginManager().callEvent(noVisitorTarget);
        check(noVisitorTarget.isCancelled(),"summoned boss cannot target dead nonplayable participants");
        final boolean[] noLoot={false};
        Bukkit.getPluginManager().registerEvents(new org.bukkit.event.Listener(){
            @org.bukkit.event.EventHandler(priority=org.bukkit.event.EventPriority.MONITOR)
            public void auditDeath(EntityDeathEvent e){if(e.getEntity()==summoned)noLoot[0]=e.getDrops().isEmpty()&&e.getDroppedExp()==0;}
        },this);
        summoned.getEquipment().setItemInMainHandDropChance(1);summoned.setHealth(0);
        check(noLoot[0]&&!summoned.isValid(),"summon death drops no equipment or XP and removes its body");
        for(int i=0;i<5;i++)boss.tick();check(summons.size()==3,"defeated summons are not recreated for an already served participant");
        begin.invoke(boss,run,judgment,List.of(a));check(field(run,"attack")==judgment,"phase three retains the signature forced-death skill");
        check(fight.phase()==3&&Math.abs(fight.core()/fight.coreMax()-.35)<.001,"second opening enters the final always-open heart phase");
        ItemDisplay mask=owned.stream().filter(ItemDisplay.class::isInstance).map(ItemDisplay.class::cast).filter(e->e.getItemStack().getItemMeta().getItemModel().getKey().equals("judge_mask")).findFirst().orElseThrow();
        check(a.canSee(mask)&&a.canSee(heart),"final phase keeps the recognizable face and visible floor heart");
        check(core.getY()<=102&&core.getSize()==8&&fight.exposed((long)field(boss,"tick")),"final heart is immediately reachable and open");
        double finalHp=fight.core();magic.context().damage(a,core,1000000,DamageType.MAGIC);
        check(fight.core()==finalHp,"always-open final heart still rejects wand damage");
        finalHp=fight.core();double credited=fight.contribution(a.getUniqueId());
        core.setNoDamageTicks(0);magic.context().damage(a,core,1000000,DamageType.MAGIC);
        Arrow finalArrow=world.spawn(core.getLocation(),Arrow.class,e->{e.setShooter(a);e.setGravity(false);});
        core.setNoDamageTicks(0);core.damage(1000000,org.bukkit.damage.DamageSource.builder(DamageType.ARROW).withCausingEntity(a).withDirectEntity(finalArrow).build());finalArrow.remove();
        check(fight.core()==finalHp&&fight.contribution(a.getUniqueId())==credited,"open final heart blocks wand and arrow damage without awarding contribution");
        finaleChecks(a,visitor,run,fight,core,world);
        a.teleport(core.getLocation().add(0,0,-4));hp=fight.core();core.setNoDamageTicks(0);
        actors.getFirst().attack(((CraftEntity)core).getHandle());
        check(fight.core()<hp,"a real sword can damage the final heart without waiting for an opening");
        for(int i=0;i<10;i++)boss.tick();
        check(fight.contribution(outsider.getUniqueId())>0&&fight.contribution(outsider.getUniqueId())<fight.coreMax()*.005,"small hand-damage contributor is below the former reward threshold");
        for(int slot=0;slot<outsider.getInventory().getStorageContents().length;slot++)outsider.getInventory().setItem(slot,new ItemStack(Material.STONE,64));
        core.setNoDamageTicks(0);core.damage(1000000,a);
        check(boss.activeCount()==0&&targets.values().stream().noneMatch(Entity::isValid),"victory removes all living hitboxes");
        check(summons.isEmpty(),"victory also removes all surviving summoned temple bosses");
        check(world.getEntities().stream().noneMatch(e->e.getPersistentDataContainer().has(garden.key("world_boss_entity"),PersistentDataType.BYTE)),"victory removes every sculpture and warning entity");
        check(!world.getPluginChunkTickets().values().stream().anyMatch(plugins->plugins.contains(garden)),"victory releases every encounter chunk ticket");
        check(floor.matches(world),"no fight or falling block changes the protected temple floor");
        check(Arrays.stream(a.getInventory().getContents()).filter(Objects::nonNull).filter(garden.relics()::isVoidKey).mapToInt(ItemStack::getAmount).sum()==2,"credited survivor receives configured Evergarden keys");
        check(coreIds(a).size()==1,"victory grants exactly one usable random Advance Magic core to the survivor");
        var ledger=(org.bukkit.configuration.file.YamlConfiguration)field(boss,"ledger");
        String pendingPath="rewards."+outsider.getUniqueId();
        List<String> smallRoll=ledger.getStringList(pendingPath+".cores");
        check(smallRoll.size()==1&&coreIds(outsider).isEmpty(),"any positive hand damage earns one core; a full inventory preserves the reward");
        int savedXp=outsider.getTotalExperience();
        outsider.getInventory().setItem(0,null);boss.claimReward(outsider);
        check(ledger.getStringList(pendingPath+".cores").equals(smallRoll)&&coreIds(outsider).isEmpty()&&outsider.getTotalExperience()==savedXp,"one free slot cannot consume a reward needing both a key and a core slot");
        outsider.getInventory().setItem(1,null);boss.claimReward(outsider);
        check(coreIds(outsider).equals(smallRoll)&&!ledger.contains(pendingPath),"enough inventory space claims the unchanged core roll exactly once");
        boss.claimReward(outsider);boss.claimReward(a);
        check(coreIds(a).size()==1&&coreIds(outsider).equals(smallRoll),"repeated manual claims cannot add extra victory cores");
        String deadPath="rewards."+b.getUniqueId();List<String> deadRoll=ledger.getStringList(deadPath+".cores");
        check(b.isDead()&&deadRoll.size()==1&&coreIds(b).isEmpty(),"dead hand-damage contributor receives one queued core");
        boss.claimReward(b);check(ledger.getStringList(deadPath+".cores").equals(deadRoll),"dead players cannot consume their queued core before respawning");
        check(fight.contribution(visitor.getUniqueId())==0&&!ledger.contains("rewards."+visitor.getUniqueId())&&coreIds(visitor).isEmpty(),"minion-only participation does not earn a main-boss core");
        check(garden.getDataFolder().toPath().resolve("world-bosses.yml").toFile().exists(),"cooldown and pending rewards are journaled");
        check(!boss.start(a,false),"defeated temple refuses an immediate normal restart");
        check(boss.start(a,true),"explicit admin restart is available for testing");boss.stop(a);
        lifetimeChecks(a,world,site);
        adminTestChecks(a,world,site);
        // Preserve a real victory cooldown for the second boot after testing abort cleanup.
        ledger.set("sites."+site.x()+"_"+site.z()+".next-open",System.currentTimeMillis()+14400000);
        ledger.save(garden.getDataFolder().toPath().resolve("world-bosses.yml").toFile());
        check(boss.activeCount()==0&&world.getEntities().stream().noneMatch(e->e.getPersistentDataContainer().has(garden.key("world_boss_entity"),PersistentDataType.BYTE)),"admin abort releases all runtime state");
        Files.writeString(checkpoint,"victory\n"+b.getUniqueId()+"\n"+deadRoll.getFirst());world.save();
    }
    @SuppressWarnings("unchecked") void finaleChecks(Player p,Player safe,Object run,JudgmentFight fight,Slime core,World world)throws Exception{
        Class<?> attack=Class.forName("com.example.voidscape.boss.WorldBossManager$Attack");
        Object rupture=Arrays.stream(attack.getEnumConstants()).filter(e->e.toString().equals("RUPTURE")).findFirst().orElseThrow();
        Method begin=boss.getClass().getDeclaredMethod("begin",run.getClass(),attack,List.class);begin.setAccessible(true);
        begin.invoke(boss,run,rupture,List.of(p,safe));
        long now=(long)field(boss,"tick");
        Map<Integer,Slime> seals=(Map<Integer,Slime>)field(run,"seals");Map<Integer,FallingBlock> markers=(Map<Integer,FallingBlock>)field(run,"sealMarkers");
        check((long)field(run,"resolveAt")-now==160&&seals.size()==2&&markers.size()==2,"rupture has two physical amethyst seals and a full eight-second warning");
        check(seals.values().stream().allMatch(e->boss.owns(e)&&((CraftEntity)e).getHandle().isPickable()&&LevelledMobsCompat.isEvergardenMob(e)),"seal targets are pickable and registered as encounter entities");
        Slime first=seals.get(0);magic.context().damage(p,first,1000,DamageType.MAGIC);
        Arrow arrow=world.spawn(first.getLocation(),Arrow.class,e->e.setShooter(p));
        first.damage(1000,org.bukkit.damage.DamageSource.builder(DamageType.ARROW).withCausingEntity(p).withDirectEntity(arrow).build());arrow.remove();
        check(fight.sealHits(0)==0,"actual wand and arrow damage cannot advance a seal");
        p.getInventory().setItemInMainHand(new ItemStack(Material.DIAMOND_SWORD));
        p.teleport(first.getLocation().add(0,0,-3));
        ServerPlayer handle=actors.stream().filter(a->a.getUUID().equals(p.getUniqueId())).findFirst().orElseThrow();
        handle.attack(((CraftEntity)first).getHandle());
        check(fight.sealHits(0)==1,"actual native sword attack counts one seal strike");
        first.damage(1000000,p);check(fight.sealHits(0)==1,"same-tick bonus damage cannot advance the seal again");
        var sweep=new EntityDamageByEntityEvent(p,first,EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK,
            org.bukkit.damage.DamageSource.builder(DamageType.PLAYER_ATTACK).withCausingEntity(p).withDirectEntity(p).build(),100);
        Bukkit.getPluginManager().callEvent(sweep);check(sweep.isCancelled()&&fight.sealHits(0)==1,"sweep attacks do not count as targeted seal strikes");
        for(int hit=0;hit<2;hit++){for(int i=0;i<4;i++)boss.tick();first.damage(1,p);}
        check(fight.sealHits(0)==3&&!first.isValid()&&seals.size()==1&&markers.size()==1&&!fight.stunned((long)field(boss,"tick")),"one broken seal disappears without interrupting the charge");
        for(int i=0;i<4;i++)boss.tick();Slime second=seals.get(1);second.damage(1,p);second.remove();boss.tick();
        Slime repaired=seals.get(1);check(repaired.isValid()&&repaired!=second&&fight.sealHits(1)==1&&!boss.owns(second),"removed seal target is repaired without resetting its strike progress");
        for(int hit=0;hit<2;hit++){for(int i=0;i<4;i++)boss.tick();repaired.damage(1,p);}
        now=(long)field(boss,"tick");
        check(field(run,"attack")==null&&fight.stunned(now)&&fight.stunnedUntil()-now==140&&seals.isEmpty()&&markers.isEmpty(),"both broken seals cancel the live attack and stun the boss for seven seconds");
        double hp=fight.core();core.setNoDamageTicks(0);core.damage(100,p);
        check(Math.abs((hp-fight.core())*fight.scale()-300)<.02,"interruption grants triple real melee damage to the core");
        for(int i=0;i<69;i++)boss.tick();check(field(run,"attack")==null,"boss makes no attacks before the stun deadline");
        boss.tick();check(field(run,"attack")!=null&&(int)field(run,"pulsesLeft")==3&&fight.coreMultiplier((long)field(boss,"tick"))==1,"normal three-pulse attacks resume exactly when the stun ends");
        double previousMax=p.getAttribute(Attribute.MAX_HEALTH).getBaseValue();p.getAttribute(Attribute.MAX_HEALTH).setBaseValue(200);p.setHealth(200);p.setInvulnerable(false);
        p.teleport(core.getLocation());safe.teleport(core.getLocation().add(21,0,0));safe.setInvulnerable(false);double safeHp=safe.getHealth();
        begin.invoke(boss,run,rupture,List.of(p,safe));
        for(int i=0;i<79;i++)boss.tick();check(p.getHealth()==200,"failed interruption deals no damage before the complete warning");
        boss.tick();check(p.getHealth()>0&&p.getHealth()<200&&safe.getHealth()==safeHp,"failed interruption damages the red circle and preserves players beyond its radius");
        check(seals.isEmpty()&&markers.isEmpty()&&field(run,"attack")==null&&fight.exposed((long)field(boss,"tick")),"failed charge cleans its seals and leaves the core open");
        check((long)field(run,"nextRupture")-(long)field(boss,"tick")==400,"another interruptible charge remains scheduled after minions are gone");
        p.setInvulnerable(true);safe.setInvulnerable(true);p.getAttribute(Attribute.MAX_HEALTH).setBaseValue(previousMax);p.setHealth(p.getAttribute(Attribute.MAX_HEALTH).getValue());
    }
    @SuppressWarnings("unchecked") void adminTestChecks(Player p,World world,WorldBossTempleLayout.Site site)throws Exception{
        var ledger=(org.bukkit.configuration.file.YamlConfiguration)field(boss,"ledger");String before=ledger.saveToString();
        p.setOp(false);check(!boss.startTest(p,3)&&!boss.triggerTestRupture(p)&&!boss.triggerTestBait(p)&&!boss.triggerTestJudgment(p)&&!boss.stopTest(p),"non-admin cannot start, force or stop test fights through the manager API");
        p.setOp(true);p.setGameMode(GameMode.CREATIVE);p.teleport(new Location(Bukkit.getWorlds().getFirst(),.5,100,.5));
        check(boss.startTest(p,3)&&p.getWorld()==world&&p.getGameMode()==GameMode.SURVIVAL,"admin starts phase three from another world with automatic teleport and survival mode");
        Object run=((Map<?,?>)field(boss,"active")).values().iterator().next();JudgmentFight fight=(JudgmentFight)field(run,"fight");
        check((boolean)field(run,"test")&&fight.phase()==3&&fight.core()==3500&&fight.exposed((long)field(boss,"tick")),"test fight starts directly in the real final-phase state");
        check(boss.triggerTestRupture(p)&&((Map<?,?>)field(run,"seals")).size()==2,"admin can force the interruptible charge without waiting");
        List<Entity> old=world.getEntities().stream().filter(e->e.getPersistentDataContainer().has(garden.key("world_boss_entity"),PersistentDataType.BYTE)).toList();
        check(boss.startTest(p,2)&&old.stream().noneMatch(Entity::isValid)&&boss.activeCount()==1,"reset replaces the entire test run including seals, summons and sculpture");
        run=((Map<?,?>)field(boss,"active")).values().iterator().next();fight=(JudgmentFight)field(run,"fight");
        check(fight.phase()==2&&fight.core()==7000&&!fight.accepts(JudgmentFight.DamageKind.PROJECTILE)&&fight.accepts(JudgmentFight.DamageKind.MAGIC),"phase-two shortcut retains real weapon immunity");
        phaseTwoBaitChecks(p,world);
        check(!boss.triggerTestRupture(p),"admin cannot force a phase-three skill into phase two");
        check(boss.startTest(p,3),"reset directly back to phase three");
        run=((Map<?,?>)field(boss,"active")).values().iterator().next();var targets=(Map<JudgmentFight.Part,Slime>)field(run,"targets");
        signatureFinalChecks(p,world,site,run,targets.get(JudgmentFight.Part.CORE));
        boss.triggerTestRupture(p);targets.get(JudgmentFight.Part.CORE).damage(1000000,p);
        check(boss.activeCount()==0&&p.getGameMode()==GameMode.CREATIVE&&ledger.saveToString().equals(before),"test victory restores the original mode and leaves rewards and cooldown ledger unchanged");
        check(world.getEntities().stream().noneMatch(e->e.getPersistentDataContainer().has(garden.key("world_boss_entity"),PersistentDataType.BYTE)),"test victory cleans even an active charge's physical seals");
        check(boss.startTest(p,1),"admin can start the phase-one test shortcut");
        p.teleport(new Location(Bukkit.getWorlds().getFirst(),.5,100,.5));
        check(boss.stopTest(p)&&p.getGameMode()==GameMode.CREATIVE&&ledger.saveToString().equals(before),"test owner can stop after leaving the arena without consuming real cooldowns");
        p.setGameMode(GameMode.SURVIVAL);p.teleport(new Location(world,site.x()+.5,101,site.z()+.5));
        check(boss.start(p,true),"create real encounter to verify test isolation");
        run=((Map<?,?>)field(boss,"active")).values().iterator().next();
        check(!boss.startTest(p,3)&&!boss.stopTest(p)&&!boss.triggerTestRupture(p)&&!boss.triggerTestJudgment(p)&&!boss.triggerTestBait(p)&&((Map<?,?>)field(boss,"active")).values().iterator().next()==run,"test actions never replace, stop or force skills in a real encounter");
        boss.stop(p);before=ledger.saveToString();
        check(boss.startTest(p,3),"test shortcut can bypass real cooldown after an ordinary encounter");
        boss.triggerTestRupture(p);garden.getConfig().set("world-boss.enabled",false);
        try{boss.tick();check(boss.activeCount()==0&&ledger.saveToString().equals(before),"disabling a test fight cleans it without writing a real abort cooldown");}
        finally{garden.getConfig().set("world-boss.enabled",true);}
        garden.testGui().open(p);check(p.getOpenInventory().getTopInventory().getItem(5).getType()==Material.AMETHYST_BLOCK,"Evergarden Test exposes the World Boss submenu");
        Method open=garden.testGui().getClass().getDeclaredMethod("openBossTests",Player.class);open.setAccessible(true);open.invoke(garden.testGui(),p);
        Inventory inv=p.getOpenInventory().getTopInventory();
        check(inv.getSize()==27&&inv.getItem(10).getType()==Material.IRON_SWORD&&inv.getItem(12).getType()==Material.BOW&&inv.getItem(14).getType()==Material.AMETHYST_BLOCK&&inv.getItem(16).getType()==Material.END_CRYSTAL,"test submenu contains phase selectors and immediate charge control");
        check(inv.getItem(9).getType()==Material.GOLD_BLOCK&&inv.getItem(15).getType()==Material.TOTEM_OF_UNDYING,"admin submenu exposes immediate lure and signature judgment controls");
        var bottom=new org.bukkit.event.inventory.InventoryClickEvent(p.getOpenInventory(),org.bukkit.event.inventory.InventoryType.SlotType.CONTAINER,27,org.bukkit.event.inventory.ClickType.SHIFT_LEFT,org.bukkit.event.inventory.InventoryAction.MOVE_TO_OTHER_INVENTORY);
        Bukkit.getPluginManager().callEvent(bottom);check(bottom.isCancelled(),"shift-click from player inventory cannot move items into the test menu");
        p.setOp(false);var denied=new org.bukkit.event.inventory.InventoryClickEvent(p.getOpenInventory(),org.bukkit.event.inventory.InventoryType.SlotType.CONTAINER,14,org.bukkit.event.inventory.ClickType.LEFT,org.bukkit.event.inventory.InventoryAction.PICKUP_ALL);
        Bukkit.getPluginManager().callEvent(denied);check(denied.isCancelled()&&boss.activeCount()==0,"revoked admin permission prevents a test menu action");
        check(!world.getPluginChunkTickets().values().stream().anyMatch(plugins->plugins.contains(garden)),"all test reset, victory and abort paths release chunk tickets");
    }
    @SuppressWarnings("unchecked") void phaseTwoBaitChecks(Player p,World world)throws Exception{
        Object run=((Map<?,?>)field(boss,"active")).values().iterator().next();var fight=(JudgmentFight)field(run,"fight");Location center=(Location)field(run,"center");
        p.teleport(center.clone().add(-14,0,6));check(boss.triggerTestBait(p),"admin can start a real phase-two lure without waiting");
        check(field(run,"baitPart")==JudgmentFight.Part.LEFT&&(long)field(run,"resolveAt")-(long)field(boss,"tick")==100,"left lure gives two seconds of tracking and three seconds of escape");
        p.teleport(center.clone().add(-13,0,6));boss.tick();
        List<Location> marks=(List<Location>)field(run,"marks");check(Math.abs(marks.getFirst().getX()-p.getX())<.001,"lure aim follows its target during the tracking period");
        while((long)field(boss,"tick")<(long)field(run,"aimLocksAt"))boss.tick();Location locked=marks.getFirst().clone();
        p.teleport(center.clone().add(-13,0,22));boss.tick();check(marks.getFirst().distanceSquared(locked)<.001,"lure aim stops following after the lock");
        long due=(long)field(run,"resolveAt");while((long)field(boss,"tick")<due)boss.tick();
        check(fight.hand(JudgmentFight.Part.LEFT)==0&&fight.exposed(due)&&fight.coreMultiplier(due)==2&&fight.contribution(p.getUniqueId())==1500,"landing the left hand on its rune opens the core and credits the lure");
        check(((Map<?,?>)field(run,"runeMarkers")).size()==1,"broken hand's lure rune disappears while the other remains");
        p.teleport(center.clone().add(14,0,6));setField(run,"nextJudgment",Long.MAX_VALUE);
        due=(long)field(run,"nextBait");while((long)field(boss,"tick")<due)boss.tick();
        check(field(run,"attack").toString().equals("BAIT_SLAM")&&field(run,"baitPart")==JudgmentFight.Part.RIGHT,"successful lure schedules the other hand soon enough for the same opening");
        while((long)field(boss,"tick")<(long)field(run,"aimLocksAt"))boss.tick();p.teleport(center.clone().add(14,0,22));
        due=(long)field(run,"resolveAt");while((long)field(boss,"tick")<due)boss.tick();
        check(fight.hand(JudgmentFight.Part.RIGHT)==0&&fight.coreMultiplier(due)==3&&fight.contribution(p.getUniqueId())==3000,"luring both hands in one opening earns triple core damage and credits both efforts");
        check(((Map<?,?>)field(run,"runeMarkers")).isEmpty(),"both successful lures remove their physical markers");
        check(boss.startTest(p,2),"reset phase two for a failed lure");run=((Map<?,?>)field(boss,"active")).values().iterator().next();fight=(JudgmentFight)field(run,"fight");center=(Location)field(run,"center");
        p.teleport(center);boss.triggerTestBait(p);
        while((long)field(boss,"tick")<(long)field(run,"aimLocksAt"))boss.tick();p.teleport(center.clone().add(0,0,25));
        due=(long)field(run,"resolveAt");while((long)field(boss,"tick")<due)boss.tick();
        check(fight.hand(JudgmentFight.Part.LEFT)==1500&&fight.hand(JudgmentFight.Part.RIGHT)==1500&&!fight.exposed(due),"missing the lure rune does not open the core or damage the hands");
        Map<JudgmentFight.Part,Slime> targets=(Map<JudgmentFight.Part,Slime>)field(run,"targets");magic.context().damage(p,targets.get(JudgmentFight.Part.LEFT),100,DamageType.MAGIC);
        check(fight.hand(JudgmentFight.Part.LEFT)==1400,"normal weapon damage remains a valid fallback in phase two");
        p.teleport(center);boss.triggerTestBait(p);check(boss.triggerTestJudgment(p)&&((Map<?,?>)field(run,"runeMarkers")).isEmpty(),"judgment overrides a test lure and hides lure runes so only genuine safe circles remain");
    }
    void signatureFinalChecks(Player safe,World world,WorldBossTempleLayout.Site site,Object run,Slime core)throws Exception{
        check((long)field(run,"nextJudgment")-(long)field(boss,"tick")==400,"phase-three signature is initially scheduled twenty seconds after transition");
        Player victim=actor(world,site,"FinalJudgmentVictim");victim.setInvulnerable(false);victim.getAttribute(Attribute.MAX_HEALTH).setBaseValue(500);victim.setHealth(500);
        victim.getAttribute(Attribute.MAX_ABSORPTION).setBaseValue(256);victim.setAbsorptionAmount(256);victim.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE,1200,4,false,false));
        victim.getInventory().setChestplate(com.example.voidscape.gui.AdminTestGui.createGodChestplate());victim.getInventory().setItemInOffHand(new ItemStack(Material.TOTEM_OF_UNDYING));
        core.damage(1,victim);check(boss.triggerTestJudgment(safe),"admin can force signature judgment in phase three");
        Location center=(Location)field(run,"center");safe.teleport(center.clone().add((double)field(run,"safeX"),0,(double)field(run,"safeZ")));victim.teleport(center);
        double hp=safe.getHealth();long due=(long)field(run,"resolveAt");while((long)field(boss,"tick")<due-2)boss.tick();
        check(!victim.isDead()&&victim.getHealth()==500,"phase-three signature preserves its full eight-second warning");
        boss.tick();check(victim.isDead()&&victim.getHealth()==0&&safe.getHealth()==hp,"phase-three signature kills through high HP, resistance, absorption, totem and Phoenix while protecting the green circle");
        safe.teleport(center);
    }
    record BlockStateSnapshot(List<Material> floor){
        BlockStateSnapshot(World world,int x,int z){this(snapshot(world,x,z));}
        static List<Material> snapshot(World world,int x,int z){var list=new ArrayList<Material>();for(int a=-35;a<=35;a++)for(int b=-35;b<=35;b++)list.add(world.getBlockAt(x+a,100,z+b).getType());return list;}
        boolean matches(World world){var site=((VoidscapePlugin)Bukkit.getPluginManager().getPlugin("Evergarden")).bossTemples().nearest(0,0,12);return floor.equals(snapshot(world,site.x(),site.z()));}
    }
    void balanceConfigChecks(){
        var current=garden.getConfig();
        check(current.getDouble("world-boss.core-health")==10000&&current.getDouble("world-boss.hand-health")==1500&&current.getInt("world-boss.attack-power-percent")==130,"installed config uses reduced core and hand HP across restarts");
        check(current.contains("world-boss.balance-version",true)&&current.getInt("world-boss.balance-version")==4,"balance migration records a persistent revision");
        check(current.getInt("world-boss.fight-timeout-seconds")==0&&current.getInt("world-boss.empty-reset-seconds")==180,"legacy hard timer and empty-arena grace migrate and survive restart");
        var defaults=org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(new java.io.InputStreamReader(garden.getResource("config.yml"),java.nio.charset.StandardCharsets.UTF_8));
        var legacy=new org.bukkit.configuration.file.YamlConfiguration();legacy.setDefaults(defaults);
        legacy.set("world-boss.core-health",40000);legacy.set("world-boss.hand-health",9000);
        JudgeBalance.upgrade(legacy);
        check(legacy.getDouble("world-boss.core-health")==10000&&legacy.getInt("world-boss.attack-power-percent")==130&&legacy.getDouble("world-boss.hand-health")==9000,"old defaults migrate while preserving custom hand health");
        legacy.set("world-boss.core-health",40000);JudgeBalance.upgrade(legacy);
        check(legacy.getDouble("world-boss.core-health")==40000,"a later explicit admin HP edit is not overwritten on another startup");
        var custom=new org.bukkit.configuration.file.YamlConfiguration();custom.set("world-boss.core-health",26000);custom.set("world-boss.attack-power-percent",145);
        JudgeBalance.upgrade(custom);
        check(custom.getDouble("world-boss.core-health")==26000&&custom.getInt("world-boss.attack-power-percent")==145,"custom HP and skill-power settings survive the one-time migration");
        var v1=new org.bukkit.configuration.file.YamlConfiguration();v1.setDefaults(defaults);
        v1.set("world-boss.balance-version",1);v1.set("world-boss.core-health",40000);
        v1.set("world-boss.empty-reset-seconds",30);v1.set("world-boss.fight-timeout-seconds",1800);
        JudgeBalance.upgrade(v1);
        check(v1.getDouble("world-boss.core-health")==40000&&v1.getInt("world-boss.empty-reset-seconds")==180&&v1.getInt("world-boss.fight-timeout-seconds")==0,"lifetime upgrade does not repeat the previous HP migration or overwrite later admin HP edits");
        var customTimers=new org.bukkit.configuration.file.YamlConfiguration();customTimers.set("world-boss.empty-reset-seconds",240);customTimers.set("world-boss.fight-timeout-seconds",3600);
        JudgeBalance.upgrade(customTimers);
        check(customTimers.getInt("world-boss.empty-reset-seconds")==240&&customTimers.getInt("world-boss.fight-timeout-seconds")==3600,"explicit administrator timers survive the lifetime migration");
        customTimers.set("world-boss.empty-reset-seconds",30);customTimers.set("world-boss.fight-timeout-seconds",1800);JudgeBalance.upgrade(customTimers);
        check(customTimers.getInt("world-boss.empty-reset-seconds")==30&&customTimers.getInt("world-boss.fight-timeout-seconds")==1800,"subsequent startup leaves deliberate timer edits unchanged");
        var v3=new org.bukkit.configuration.file.YamlConfiguration();v3.set("world-boss.balance-version",3);v3.set("world-boss.core-health",26000);v3.set("world-boss.hand-health",9000);
        v3.set("world-boss.judgment-cooldown-seconds",130);v3.set("world-boss.judgment-phase-one-delay-seconds",45);JudgeBalance.upgrade(v3);
        check(v3.getInt("world-boss.judgment-cooldown-seconds")==115&&v3.getInt("world-boss.judgment-phase-one-delay-seconds")==40&&v3.getDouble("world-boss.hand-health")==9000&&v3.getDouble("world-boss.core-health")==26000,"revision four migrates only published signature timing defaults and preserves custom HP");
        v3.set("world-boss.judgment-cooldown-seconds",130);JudgeBalance.upgrade(v3);check(v3.getInt("world-boss.judgment-cooldown-seconds")==130,"signature timing migration only runs once");
        var tuned=new org.bukkit.configuration.file.YamlConfiguration();tuned.set("world-boss.balance-version",3);tuned.set("world-boss.judgment-cooldown-seconds",160);tuned.set("world-boss.judgment-phase-one-delay-seconds",60);JudgeBalance.upgrade(tuned);
        check(tuned.getInt("world-boss.judgment-cooldown-seconds")==160&&tuned.getInt("world-boss.judgment-phase-one-delay-seconds")==60,"explicit administrator signature timings survive the migration");
        for(double oldHands:new double[]{8000,8500}){
            var v2=new org.bukkit.configuration.file.YamlConfiguration();v2.setDefaults(defaults);
            v2.set("world-boss.balance-version",2);v2.set("world-boss.core-health",20000);v2.set("world-boss.hand-health",oldHands);
            v2.set("world-boss.damage-caps.melee",100);v2.set("world-boss.empty-reset-seconds",30);
            JudgeBalance.upgrade(v2);
            check(v2.getDouble("world-boss.core-health")==10000&&v2.getDouble("world-boss.hand-health")==1500&&!v2.contains("world-boss.damage-caps",true),"revision two migrates published HP defaults and removes obsolete caps");
            check(v2.getInt("world-boss.empty-reset-seconds")==30,"revision three preserves timers customized after revision two");
            v2.set("world-boss.core-health",20000);v2.set("world-boss.hand-health",8500);JudgeBalance.upgrade(v2);
            check(v2.getDouble("world-boss.core-health")==20000&&v2.getDouble("world-boss.hand-health")==8500,"revision three migration only runs once");
        }
    }
    @SuppressWarnings("unchecked") void lifetimeChecks(Player p,World world,WorldBossTempleLayout.Site site)throws Exception{
        Location center=new Location(world,site.x()+.5,101,site.z()+.5),outside=center.clone().add(100,0,0);p.teleport(center);
        check(boss.start(p,true),"start an isolated long-fight regression encounter");
        Object run=((Map<?,?>)field(boss,"active")).values().iterator().next();
        var fight=(JudgmentFight)field(run,"fight");
        var targets=(Map<JudgmentFight.Part,Slime>)field(run,"targets");
        setField(run,"nextAttack",Long.MAX_VALUE);
        magic.context().damage(p,targets.get(JudgmentFight.Part.LEFT),100,DamageType.MAGIC);
        double hand=fight.hand(JudgmentFight.Part.LEFT),core=fight.core(),contribution=fight.contribution(p.getUniqueId());
        setField(boss,"tick",(long)field(boss,"tick")+20*3600L);boss.tick();
        check(boss.activeCount()==1&&fight.hand(JudgmentFight.Part.LEFT)==hand&&fight.core()==core,"an active hour-long fight survives the former 30-minute cutoff without resetting HP");
        check(!boss.status(p).contains("ไฟต์เหลือ"),"unlimited fights show no misleading timeout countdown");
        Slime removed=targets.get(JudgmentFight.Part.CORE);removed.remove();
        for(int i=0;i<2;i++)boss.tick();Slime replacement=targets.get(JudgmentFight.Part.CORE);
        check(boss.activeCount()==1&&replacement.isValid()&&!replacement.getUniqueId().equals(removed.getUniqueId())&&boss.owns(replacement)&&!boss.owns(removed),"removing a living hitbox recreates its owner mapping instead of deleting the boss");
        check(fight.core()==core&&fight.hand(JudgmentFight.Part.LEFT)==hand&&fight.contribution(p.getUniqueId())==contribution,"hitbox repair preserves encounter HP, phase and player contribution");
        p.teleport(outside);setField(boss,"tick",(long)field(boss,"tick")+20*60L);boss.tick();
        check(boss.activeCount()==1&&boss.status(p).contains("รอผู้เล่นกลับ"),"a participant can spend a minute returning from outside the arena without losing the fight");
        p.teleport(center);boss.tick();
        check((long)field(run,"lastPresent")== (long)field(boss,"tick")&&fight.hand(JudgmentFight.Part.LEFT)==hand,"returning to the arena resets the absence timer and resumes the same damaged boss");
        long lastPresent=(long)field(run,"lastPresent");p.teleport(outside);
        setField(boss,"tick",lastPresent+20*180L-4);boss.tick();check(boss.activeCount()==1,"empty arena survives until the exact three-minute grace boundary");
        boss.tick();var ledger=(org.bukkit.configuration.file.YamlConfiguration)field(boss,"ledger");String stop="sites."+site.x()+"_"+site.z()+".last-stop";
        check(boss.activeCount()==0&&ledger.getString(stop).equals("EMPTY"),"a truly abandoned fight expires after the grace and records its reason");
        check(!world.getPluginChunkTickets().values().stream().anyMatch(plugins->plugins.contains(garden)),"abandoned fights release chunk tickets with the hard timer disabled");
        check(!ledger.contains("rewards."+p.getUniqueId()),"abandoning a long fight grants no victory rewards");
        p.teleport(center);garden.getConfig().set("world-boss.fight-timeout-seconds",5);
        try{
            check(boss.start(p,true),"an administrator can still opt into a finite encounter duration");
            run=((Map<?,?>)field(boss,"active")).values().iterator().next();long started=(long)field(run,"started");
            check(boss.status(p).contains("ไฟต์เหลือ 5 วิ"),"configured finite duration is visible before expiry");
            setField(run,"nextAttack",Long.MAX_VALUE);setField(boss,"tick",started+96);boss.tick();
            check(boss.activeCount()==1,"configured finite duration does not terminate early");
            boss.tick();check(boss.activeCount()==0&&ledger.getString(stop).equals("TIMEOUT"),"configured timeout expires exactly at its boundary and records TIMEOUT");
            check(boss.status(p).contains("ครบเวลาสู้ที่กำหนด"),"cooldown status explains a configured timeout instead of silent disappearance");
        }finally{garden.getConfig().set("world-boss.fight-timeout-seconds",0);}
        check(boss.start(p,true),"boss remains summonable after a configured timeout");boss.stop(p);
        check(ledger.getString(stop).equals("ADMIN"),"manual stop has its own distinct reason");
        check(boss.start(p,true),"start configuration-disable cleanup regression");garden.getConfig().set("world-boss.enabled",false);
        try{boss.tick();check(boss.activeCount()==0&&ledger.getString(stop).equals("DISABLED"),"disabling the feature cleans up with a distinct reason");}
        finally{garden.getConfig().set("world-boss.enabled",true);}
    }
    void seraphPreviewChecks(Player owner,Player visitor,World world)throws Exception{
        check(!boss.spawnSeraphPreview(visitor),"ordinary players cannot spawn the winged model preview");
        owner.setOp(true);
        try{
            check(!boss.spawnSeraphPreview(owner),"Java preview waits for its unique model pack before spawning");
            Bukkit.getPluginManager().callEvent(new PlayerResourcePackStatusEvent(owner,JudgePackService.PACK_ID,PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED));
            check(boss.spawnSeraphPreview(owner),"admin can summon the isolated Seraph model without starting an encounter");
            List<Entity> parts=world.getEntities().stream().filter(e->e.getPersistentDataContainer().has(garden.key("seraph_preview"),PersistentDataType.BYTE)).toList();
            check(parts.size()==3&&parts.stream().allMatch(Entity::isValid),"preview has exactly one Witch anchor and two client-specific model carriers");
            Witch witch=parts.stream().filter(Witch.class::isInstance).map(Witch.class::cast).findFirst().orElseThrow();
            ItemDisplay java=parts.stream().filter(ItemDisplay.class::isInstance).map(ItemDisplay.class::cast).findFirst().orElseThrow();
            ArmorStand bedrock=parts.stream().filter(ArmorStand.class::isInstance).map(ArmorStand.class::cast).findFirst().orElseThrow();
            check(!witch.hasAI()&&witch.hasPotionEffect(PotionEffectType.INVISIBILITY)&&witch.isInvulnerable(),"Witch anchor cannot throw potions or fight while previewing");
            check(java.getItemStack().getItemMeta().getItemModel().getKey().equals("judge_seraph_idle")&&owner.canSee(java)&&!visitor.canSee(java)&&!owner.canSee(bedrock),"accepted Java client sees the custom idle skin without exposing the Bedrock carrier");
            for(int i=0;i<19;i++)boss.tick();
            check(java.getItemStack().getItemMeta().getItemModel().getKey().equals("judge_seraph_cast")&&bedrock.getEquipment().getHelmet().getItemMeta().getItemModel().getKey().equals("judge_seraph_cast"),"both client models move into the spellcasting pose");
            var menu=garden.testGui();var open=menu.getClass().getDeclaredMethod("openBossTests",Player.class);open.setAccessible(true);open.invoke(menu,owner);
            check(owner.getOpenInventory().getTopInventory().getItem(11).getType()==Material.FEATHER&&owner.getOpenInventory().getTopInventory().getItem(13).getType()==Material.SHEARS,"admin World Boss Test menu exposes summon and remove preview controls");
            owner.closeInventory();
            check(boss.removeSeraphPreview(owner)&&parts.stream().noneMatch(Entity::isValid)&&boss.activeCount()==0,"removing preview cleans all entities without changing real encounter state");
        }finally{
            boss.removeSeraphPreview(owner);owner.setOp(false);
            Bukkit.getPluginManager().callEvent(new PlayerResourcePackStatusEvent(owner,JudgePackService.PACK_ID,PlayerResourcePackStatusEvent.Status.FAILED_RELOAD));
        }
    }
    void hotfixChecks(Player p,World world,WorldBossTempleLayout.Site site){
        for(int[] point:new int[][]{{40000,40000},{-40000,40000},{50000,0},{0,-50000},{250000,0},{499999,-499999}}){
            var block=world.getBlockAt(point[0],220,point[1]);
            check(!garden.dungeons().isSpawnIsland(block),"distant coordinates are outside spawn protection: "+Arrays.toString(point));
            var broken=new org.bukkit.event.block.BlockBreakEvent(block,p);Bukkit.getPluginManager().callEvent(broken);
            check(!broken.isCancelled(),"ordinary distant terrain can be mined: "+Arrays.toString(point));
            var placed=new org.bukkit.event.block.BlockPlaceEvent(block,block.getState(),block.getRelative(org.bukkit.block.BlockFace.DOWN),new ItemStack(Material.STONE),p,true,EquipmentSlot.HAND);
            Bukkit.getPluginManager().callEvent(placed);check(!placed.isCancelled(),"ordinary distant terrain allows building: "+Arrays.toString(point));
        }
        for(int[] point:new int[][]{{0,0},{80,0},{-80,0},{0,80},{48,64}}){
            var block=world.getBlockAt(point[0],220,point[1]);
            check(garden.dungeons().isSpawnIsland(block),"spawn center and its exact radius remain protected: "+Arrays.toString(point));
            var broken=new org.bukkit.event.block.BlockBreakEvent(block,p);Bukkit.getPluginManager().callEvent(broken);check(broken.isCancelled(),"spawn boundary still prevents mining");
        }
        check(!garden.dungeons().isSpawnIsland(world.getBlockAt(81,220,0)),"one block beyond the spawn radius is outside protection");
        var temple=world.getBlockAt(site.x(),100,site.z());
        var broken=new org.bukkit.event.block.BlockBreakEvent(temple,p);Bukkit.getPluginManager().callEvent(broken);check(broken.isCancelled(),"actual world-boss temple architecture stays protected");
        var outside=world.getBlockAt(site.x()+121,100,site.z());
        var outsideBreak=new org.bukkit.event.block.BlockBreakEvent(outside,p);Bukkit.getPluginManager().callEvent(outsideBreak);check(!outsideBreak.isCancelled(),"terrain immediately beyond the temple footprint is editable");
        String[] ids={"shulker_levitation","solar_apocalypse","chronos_final_hour","heavens_judgment","verdant_sovereign","lunar_requiem","crimson_oblivion"};
        for(int i=0;i<ids.length;i++){
            int choice=i;
            var random=new Random(0){@Override public int nextInt(int bound){if(bound==10000)return 9900;if(bound==ids.length)return choice;throw new AssertionError("Unexpected Mythic draw bound: "+bound);}};
            ItemStack reward=garden.relics().rollVaultReward(random);
            var spell=magic.wands().coreSpell(reward);
            check(spell!=null&&spell.id().equals(ids[i]),"production Vault roll creates a usable Mythic core: "+ids[i]);
        }
    }
    List<String> coreIds(Player p){
        return Arrays.stream(p.getInventory().getStorageContents()).filter(Objects::nonNull).filter(item->magic.wands().coreSpell(item)!=null)
            .flatMap(item->Collections.nCopies(item.getAmount(),magic.wands().coreSpell(item).id()).stream()).toList();
    }
    Player actor(World world,WorldBossTempleLayout.Site site,String name){return actor(world,site,name,UUID.randomUUID());}
    Player actor(World world,WorldBossTempleLayout.Site site,String name,UUID uuid){
        var server=MinecraftServer.getServer();var level=((CraftWorld)world).getHandle();var profile=new GameProfile(uuid,name);
        ServerPlayer handle=new ServerPlayer(server,level,profile,ClientInformation.createDefault());
        var connection=new Connection(PacketFlow.SERVERBOUND);connection.channel=new EmbeddedChannel();connection.address=new InetSocketAddress("127.0.0.1",1);
        handle.connection=new ServerGamePacketListenerImpl(server,connection,handle,CommonListenerCookie.createInitial(profile,false)){
            @Override public boolean hasClientLoaded(){return true;} // A simulated client has no load-ack packet.
        };
        handle.setPos(site.x()+.5,101,site.z()+.5);server.getPlayerList().getPlayers().add(handle);server.getPlayerList().getPlayersByUUID().put(profile.id(),handle);level.addNewPlayer(handle);
        actors.add(handle);Player p=handle.getBukkitEntity();p.setGravity(false);p.setInvulnerable(true);p.setGameMode(GameMode.SURVIVAL);return p;
    }
    void cleanup(){if(boss!=null)boss.close();for(ServerPlayer handle:actors){var server=MinecraftServer.getServer();server.getPlayerList().getPlayers().remove(handle);server.getPlayerList().getPlayersByUUID().remove(handle.getUUID());handle.discard();}}
}
