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
        hotfixChecks(a,world,site);
        outsider.teleport(new Location(world,site.x()+70.5,101,site.z()+.5));
        BlockStateSnapshot floor=new BlockStateSnapshot(world,site.x(),site.z());
        var altar=new PlayerInteractEvent(a,Action.RIGHT_CLICK_BLOCK,null,world.getBlockAt(site.x(),100,site.z()),org.bukkit.block.BlockFace.UP,EquipmentSlot.HAND);
        Bukkit.getPluginManager().callEvent(altar);
        check(altar.isCancelled()&&boss.activeCount()==1,"ordinary player summons at the existing amethyst altar with an empty hand");
        check(boss.inCombat(a)&&!boss.inCombat(outsider),"only actual participants enter combat; nearby visitors do not scale HP");
        Object run=((Map<?,?>)field(boss,"active")).values().iterator().next();
        JudgmentFight fight=(JudgmentFight)field(run,"fight");
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
        check(garden.judgePack().modelInfo().contains("cathedral-wings-v2")&&garden.judgePack().modelInfo().contains("3.19.0"),"pack diagnostics identify the bundled grand model revision and Bedrock version");
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
        double hp=fight.hand(JudgmentFight.Part.LEFT);
        double actual=magic.context().damage(a,left,1000,DamageType.MAGIC);
        check(Math.abs(fight.hand(JudgmentFight.Part.LEFT)-(hp-240))<.01,"a real Advance Magic damage call is capped to 240 final damage");
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
        check(Math.abs(titanDamage-80)<.02,"actual Titan Hunter dev bow including Max HP bonus is capped at 80 per arrow: "+titanDamage);titan.remove();
        double rightHp=fight.hand(JudgmentFight.Part.RIGHT);magic.context().damage(a,right,1000,DamageType.MAGIC);
        check(Math.abs(rightHp-fight.hand(JudgmentFight.Part.RIGHT)-160)<.02,"arrow and magic share the remaining 160 budget across both hands");
        hp=fight.hand(JudgmentFight.Part.LEFT);magic.context().damage(a,left,1000,DamageType.MAGIC);
        check(fight.hand(JudgmentFight.Part.LEFT)==hp,"multi-hit spells cannot exceed the shared 240 damage per rolling second");
        magic.context().damage(b,right,1650,DamageType.MAGIC);
        check(fight.roster().size()==2&&Math.abs(fight.scale()-1.65)<.001,"second real attacker increases effective HP exactly once");
        check(!fight.roster().contains(outsider.getUniqueId()),"uninvolved visitor remains absent from the roster");
        for(int i=0;i<10;i++)boss.tick();
        hp=fight.hand(JudgmentFight.Part.LEFT);left.setNoDamageTicks(0);left.damage(1000,a);
        check(Math.abs((hp-fight.hand(JudgmentFight.Part.LEFT))*fight.scale()-100)<.02,"real melee damage has its own 100 final-damage cap");
        for(int i=0;i<10;i++)boss.tick();
        TNTPrimed damageOrigin=world.spawn(left.getLocation(),TNTPrimed.class,e->e.setFuseTicks(100000));
        hp=fight.hand(JudgmentFight.Part.LEFT);left.setNoDamageTicks(0);
        left.damage(1000,org.bukkit.damage.DamageSource.builder(DamageType.PLAYER_EXPLOSION).withCausingEntity(a).withDirectEntity(damageOrigin).build());damageOrigin.remove();
        check(Math.abs((hp-fight.hand(JudgmentFight.Part.LEFT))*fight.scale()-80)<.02,"player-attributed explosion uses the other-damage cap rather than melee");
        // Schedule the exact live rare attack, not a hand-built damage event.
        Class<?> attack=Class.forName("com.example.voidscape.boss.WorldBossManager$Attack");
        Object judgment=Arrays.stream(attack.getEnumConstants()).filter(e->e.toString().equals("JUDGMENT")).findFirst().orElseThrow();
        Method begin=boss.getClass().getDeclaredMethod("begin",run.getClass(),attack,List.class);begin.setAccessible(true);
        begin.invoke(boss,run,judgment,List.of(a,b));
        check(field(run,"attack")!=judgment,"phase one refuses the forced-death skill");
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
        boss.tick();check(outsider.getHealth()>0&&outsider.getHealth()<80,"ordinary attack hurts through good armor while leaving room to recover");
        Object sky=Arrays.stream(attack.getEnumConstants()).filter(e->e.toString().equals("SKY_BEAMS")).findFirst().orElseThrow();
        outsider.setHealth(80);outsider.setNoDamageTicks(0);
        begin.invoke(boss,run,sky,List.of(outsider));
        List<Location> skyMarks=new ArrayList<>((List<Location>)field(run,"marks"));
        check(skyMarks.size()==6,"phase-one aerial volley creates multiple separate magic circles");
        for(int i=0;i<skyMarks.size();i++)for(int j=i+1;j<skyMarks.size();j++)check(skyMarks.get(i).distanceSquared(skyMarks.get(j))>=400,"aerial circle centers leave a gap between their eighteen-block diameters");
        Object bodySky=field(run,"body");Map<?,?> planesSky=(Map<?,?>)field(field(bodySky,"sigils"),"planes");
        check(planesSky.entrySet().stream().filter(e->e.getKey().toString().startsWith("attack:sky-cast:")).map(e->(ItemDisplay)e.getValue()).allMatch(e->Math.abs(e.getY()+e.getTransformation().getTranslation().y()-161.1)<.01),"aerial circles share one height above the whole crown rather than stacking");
        outsider.teleport(skyMarks.getFirst());
        for(int i=0;i<21;i++)boss.tick();check(outsider.getHealth()==80,"aerial beams wait the full warning");
        boss.tick();check(outsider.getHealth()<=20&&outsider.getHealth()>0,"sky beams inflict strong damage through armor without forced death; HP="+outsider.getHealth());
        for(int i=0;i<2;i++)boss.tick();check(planesSky.keySet().stream().filter(k->k.toString().startsWith("fx:cast:")).count()==6,"sky beams keep every aerial casting circle visible while firing");
        for(int i=0;i<24;i++)boss.tick();check(field(run,"attack")!=sky,"attack dispatcher never repeats the previous pattern consecutively");
        Object lances=Arrays.stream(attack.getEnumConstants()).filter(e->e.toString().equals("LANCES")).findFirst().orElseThrow();
        outsider.setHealth(80);outsider.setNoDamageTicks(0);outsider.teleport(new Location(world,site.x()+.5,101,site.z()+.5));
        begin.invoke(boss,run,lances,List.of(outsider));
        check(planesSky.keySet().stream().filter(k->k.toString().startsWith("attack:lance-lane:")).count()==3,"lance attack warns three separate parallel corridors");
        for(int i=0;i<20;i++)boss.tick();
        check(outsider.getHealth()<=25&&outsider.getHealth()>0,"lance damage matches the marked corridor through armor");
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
        boss.tick();check(outsider.getHealth()<80&&outsider.getHealth()>0,"larger slam actually damages ten blocks from its center, beyond the old eight-block radius");
        outsider.setInvulnerable(true);
        // Only after testing real weapon caps, raise limits to drive phase transitions quickly in this fixture.
        for(String key:List.of("melee","projectile","magic","other","per-player-per-second"))garden.getConfig().set("world-boss.damage-caps."+key,1000000);
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
        magic.context().damage(a,core,1000000,DamageType.MAGIC);
        check(fight.phase()==2&&Math.abs(fight.core()/fight.coreMax()-.7)<.001,"a huge spell stops at 70 percent and enters phase two");
        check(left.getY()>=101&&right.getY()>=101,"phase two immediately restores both reachable hand hitboxes");
        magic.context().damage(a,core,1000000,DamageType.MAGIC);
        check(fight.phase()==2,"continued AoE from the same combo cannot skip another sealed phase");
        check((long)field(run,"nextJudgment")-(long)field(boss,"tick")==240,"phase-two first judgment is due twelve seconds after transition");
        begin.invoke(boss,run,judgment,List.of(a,b));
        var seals=world.getEntities().stream().filter(ItemDisplay.class::isInstance).map(ItemDisplay.class::cast).filter(e->e.getItemStack().getType()==Material.PAPER).toList();
        check(seals.stream().filter(a::canSee).count()==8,"judgment shows four green sanctuaries, a floor rim and three spell seals");
        check(seals.stream().filter(e->e.getItemStack().getItemMeta().getItemModel().getKey().equals("judge_seal")).allMatch(e->e.getY()+e.getTransformation().getTranslation().y()>=169),"judgment casting seals sit above the enlarged crown instead of through the face");
        check(seals.stream().noneMatch(outsider::canSee),"clients without the addon do not see missing-texture spell models");
        check(seals.stream().allMatch(e->e.getBrightness().getBlockLight()==15&&e.getBrightness().getSkyLight()==15),"spell circles remain full-bright in the dark temple");
        long now=(long)field(boss,"tick"),resolve=(long)field(run,"resolveAt");
        check(resolve-now==160,"rare one-hit grants the configured full eight-second telegraph");
        check((long)field(run,"nextJudgment")-resolve>=2600,"rare one-hit cooldown is at least 130 seconds");
        double safeX=(double)field(run,"safeX"),safeZ=(double)field(run,"safeZ");
        a.teleport(new Location(world,site.x()+.5+safeX,101,site.z()+.5+safeZ));
        outsider.teleport(a.getLocation());
        Player visitor=actor(world,site,"JudgeVisitor");visitor.teleport(new Location(world,site.x()+70.5,101,site.z()+.5));
        b.teleport(new Location(world,site.x()+.5,250,site.z()+.5)); // flight above the arena is not an escape
        b.setInvulnerable(false);b.getAttribute(Attribute.MAX_HEALTH).setBaseValue(500);b.setHealth(500);b.getAttribute(Attribute.MAX_ABSORPTION).setBaseValue(256);b.setAbsorptionAmount(256);
        b.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE,1200,4,false,false));
        for(ItemStack item:gear)item.addUnsafeEnchantment(Enchantment.PROTECTION,10);
        gear[2]=com.example.voidscape.gui.AdminTestGui.createGodChestplate();
        var food=garden.crops().factory().createFood(com.example.voidscape.crop.CropType.SOUL_WARD_BULB,1);
        Bukkit.getPluginManager().callEvent(new org.bukkit.event.player.PlayerItemConsumeEvent(b,food,EquipmentSlot.HAND));
        check(((Set<?>)field(garden.cropBuffs(),"soulWardActive")).contains(b.getUniqueId()),"real life-saving vegetable is active before judgment");
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
        check(b.isDead()&&b.getHealth()==0,"live one-hit defeats high HP, absorption, armor, resistance, totem, Phoenix, vegetable and flight; health="+b.getHealth()+" absorption="+b.getAbsorptionAmount()+" position="+b.getLocation()+" attack="+field(run,"attack")+" tick="+field(boss,"tick"));
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
        check(field(run,"attack")==null&&(long)field(run,"nextAttack")-(long)field(boss,"tick")==36,"cross combo finishes with a shorter 1.8-second recovery");
        magic.context().damage(a,right,1000000,DamageType.MAGIC);
        for(int i=0;i<10;i++)boss.tick();magic.context().damage(a,core,1000000,DamageType.MAGIC);
        Map<UUID,Mob> summons=(Map<UUID,Mob>)field(run,"summons");
        check(summons.size()==fight.roster().size()&&summons.size()==3,"phase three summons one temple boss per participant including the retained roster");
        check(summons.values().stream().map(Entity::getType).collect(java.util.stream.Collectors.toSet()).size()==3,"all three real temple boss species are summoned");
        check(summons.values().stream().allMatch(m->m.isValid()&&m.getAttribute(Attribute.SCALE).getValue()==1.8&&boss.owns(m)),"summoned bosses have temple scale and encounter ownership");
        for(Mob m:summons.values())m.setAI(false);
        for(String key:List.of("melee","projectile","magic","other","per-player-per-second"))garden.getConfig().set("world-boss.damage-caps."+key,null);
        Mob summoned=summons.values().iterator().next();double summonedHp=summoned.getHealth();
        visitor.getInventory().setItemInMainHand(new ItemStack(Material.DIAMOND_SWORD));summoned.setNoDamageTicks(0);summoned.damage(1000,visitor);
        check(summonedHp-summoned.getHealth()>0&&summonedHp-summoned.getHealth()<=100.01,"temple summon also obeys final melee cap after its armor modifiers");
        for(int i=0;i<5;i++)boss.tick();
        check(summons.size()==4&&fight.roster().size()==4,"joining phase three by hitting a summon adds exactly one extra temple boss");
        double budgetHp=fight.core();magic.context().damage(visitor,core,1000,DamageType.MAGIC);
        check(fight.core()==budgetHp,"sealed core still rejects a late participant's damage");
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
        for(String key:List.of("melee","projectile","magic","other","per-player-per-second"))garden.getConfig().set("world-boss.damage-caps."+key,1000000);
        begin.invoke(boss,run,judgment,List.of(a));check(field(run,"attack")!=judgment,"phase three refuses the forced-death skill");
        check(fight.phase()==3&&Math.abs(fight.core()/fight.coreMax()-.35)<.001,"second opening enters the final sealed-heart phase");
        ItemDisplay mask=owned.stream().filter(ItemDisplay.class::isInstance).map(ItemDisplay.class::cast).filter(e->e.getItemStack().getItemMeta().getItemModel().getKey().equals("judge_mask")).findFirst().orElseThrow();
        check(a.canSee(mask)&&a.canSee(heart),"final phase keeps the recognizable face and visible floor heart");
        check(core.getY()<=102&&core.getSize()==8&&core.getCustomName().contains("8"),"sealed final heart has a reachable real hitbox and eight-second countdown");
        double finalHp=fight.core();magic.context().damage(a,core,1000000,DamageType.MAGIC);
        check(fight.core()==finalHp,"visible sealed heart still rejects early damage");
        long openingDelay=fight.openingIn((long)field(boss,"tick"));for(int i=0;i<openingDelay/2;i++)boss.tick();check(fight.exposed((long)field(boss,"tick")),"final heart opens on its own for a solo survivor");
        for(int i=0;i<30;i++)boss.tick();
        check(fight.exposed((long)field(boss,"tick"))&&field(run,"attack")!=null&&(int)field(run,"pulsesLeft")==3,"final phase resumes a three-pulse attack after the approach grace while the heart is still damageable");
        for(int i=0;i<90;i++)boss.tick();
        check(!fight.exposed((long)field(boss,"tick"))&&a.canSee(heart)&&core.getY()<=102,"missing a final damage window preserves a visible heart on the floor");
        check(fight.openingIn((long)field(boss,"tick"))==200,"resealed final heart announces a precise ten-second wait");
        for(int i=0;i<100;i++)boss.tick();check(fight.exposed((long)field(boss,"tick")),"final heart reliably reopens after a missed window");
        a.teleport(core.getLocation().add(0,0,-4));hp=fight.core();core.setNoDamageTicks(0);
        actors.getFirst().attack(((CraftEntity)core).getHandle());
        check(fight.core()<hp,"a real sword can damage the final-phase heart after resealing");
        for(int i=0;i<10;i++)boss.tick();
        check(fight.contribution(outsider.getUniqueId())>0&&fight.contribution(outsider.getUniqueId())<fight.coreMax()*.005,"small hand-damage contributor is below the former reward threshold");
        for(int slot=0;slot<outsider.getInventory().getStorageContents().length;slot++)outsider.getInventory().setItem(slot,new ItemStack(Material.STONE,64));
        magic.context().damage(a,core,1000000,DamageType.MAGIC);
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
        // Preserve a real victory cooldown for the second boot after testing abort cleanup.
        ledger.set("sites."+site.x()+"_"+site.z()+".next-open",System.currentTimeMillis()+14400000);
        ledger.save(garden.getDataFolder().toPath().resolve("world-bosses.yml").toFile());
        check(boss.activeCount()==0&&world.getEntities().stream().noneMatch(e->e.getPersistentDataContainer().has(garden.key("world_boss_entity"),PersistentDataType.BYTE)),"admin abort releases all runtime state");
        Files.writeString(checkpoint,"victory\n"+b.getUniqueId()+"\n"+deadRoll.getFirst());world.save();
    }
    record BlockStateSnapshot(List<Material> floor){
        BlockStateSnapshot(World world,int x,int z){this(snapshot(world,x,z));}
        static List<Material> snapshot(World world,int x,int z){var list=new ArrayList<Material>();for(int a=-35;a<=35;a++)for(int b=-35;b<=35;b++)list.add(world.getBlockAt(x+a,100,z+b).getType());return list;}
        boolean matches(World world){var site=((VoidscapePlugin)Bukkit.getPluginManager().getPlugin("Evergarden")).bossTemples().nearest(0,0,12);return floor.equals(snapshot(world,site.x(),site.z()));}
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
        for(int i=0;i<4;i++){
            int choice=i;
            var random=new Random(0){@Override public int nextInt(int bound){if(bound==10000)return 9900;if(bound==4)return choice;throw new AssertionError("Unexpected Mythic draw bound: "+bound);}};
            ItemStack reward=garden.relics().rollVaultReward(random);
            String[] ids={"shulker_levitation","solar_apocalypse","chronos_final_hour","heavens_judgment"};
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
