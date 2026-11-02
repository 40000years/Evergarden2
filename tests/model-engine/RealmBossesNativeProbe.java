import com.example.voidscape.VoidscapePlugin;
import com.example.voidscape.boss.ArkhaiaBedrockSupport;
import com.example.voidscape.boss.JudgePackService;
import com.example.voidscape.boss.RealmBosses;
import com.example.voidscape.boss.RealmBossController;
import com.example.voidscape.world.DimensionalSanctuaries;
import com.ticxo.modelengine.api.entity.CullType;
import com.mojang.authlib.GameProfile;
import com.ticxo.modelengine.api.ModelEngineAPI;
import com.ticxo.modelengine.api.animation.handler.IStateMachineHandler;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.server.network.*;
import org.bukkit.*;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.entity.*;
import org.bukkit.damage.DamageType;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import java.net.InetSocketAddress;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;

/** Real Paper + ModelEngine 4.1.1 (the capped build: twelve models in all). All four realm rigs ship in ONE
 *  merged blueprint, so with the main bosses, the river rigs and internal_fire the total stays at twelve; the
 *  per-rig blueprint an e2.159 server installed is retired. Every rig then spawns from the admin showcase without a
 *  restart, keeps only its own bones, opens with its prefixed spawn clip, loops its aura lane, follows the pose menu,
 *  registers a native Bedrock carrier when Geyser is ready, and clears without leftovers. */
public final class RealmBossesNativeProbe extends JavaPlugin {
    VoidscapePlugin garden;World world;Player admin;int checks,waited,index;final List<String> lines=new ArrayList<>();boolean finished;
    void check(boolean ok,String label){if(!ok)throw new AssertionError(label);checks++;lines.add("PASS: "+label);getLogger().info("PASS: "+label);}
    void later(long ticks,Runnable work){Bukkit.getScheduler().runTaskLater(this,()->{try{work.run();}catch(Throwable error){finish(error);}},ticks);}
    @Override public void onEnable(){later(1,()->{
        garden=(VoidscapePlugin)Bukkit.getPluginManager().getPlugin("Evergarden");
        check(garden!=null&&garden.isEnabled(),"Evergarden enables");
        await();
    });}
    void await(){
        boolean ready=garden.arkhaiaModels()!=null&&garden.arkhaiaModels().ready()&&RealmBosses.MODELS.stream().allMatch(id->garden.arkhaiaModels().ready(id));
        if(!ready){
            if((waited+=10)>2400)throw new AssertionError("realm rigs never became ready: "+(garden.arkhaiaModels()==null?"no ModelEngine":garden.arkhaiaModels().modelInfo()));
            later(10,this::await);return;
        }
        run();
    }
    void run(){
        check(ModelEngineAPI.getBlueprint("internal_fire")!=null&&garden.arkhaiaModels().ready(),"internal_fire and every main/river rig still import");
        check(garden.arkhaiaModels().ready("arkhaia")&&garden.arkhaiaModels().ready("seraphon"),"Arkhaia and Seraphon stay ready beside the realm rigs");
        Path engine=Bukkit.getPluginManager().getPlugin("ModelEngine").getDataFolder().toPath();
        var merged=ModelEngineAPI.getBlueprint(RealmBosses.BLUEPRINT);
        check(merged!=null&&Files.exists(engine.resolve("blueprints/"+RealmBosses.BLUEPRINT+".bbmodel")),"merged realm blueprint installed and imported");
        for(String id:RealmBosses.MODELS){
            check(!Files.exists(engine.resolve("blueprints/"+id+".bbmodel"))&&ModelEngineAPI.getBlueprint(id)==null,id+": no separate blueprint (e2.159 leftover retired)");
            var clips=merged.getAnimations().keySet();
            check(RealmBosses.CLIPS.stream().allMatch(c->clips.contains(id+"_"+c))&&clips.contains(id+"_aura"),id+": every showcase clip and its aura imported");
            check(ArkhaiaBedrockSupport.clips(id).equals(RealmBosses.CLIPS),id+": Bedrock clip table follows the blueprint order");
        }
        try(var files=Files.list(engine.resolve("blueprints"))){
            long count=files.filter(f->f.toString().endsWith(".bbmodel")).count();
            check(count==11,"eleven Evergarden blueprints in ModelEngine (+ internal_fire = twelve): "+count);
        }catch(java.io.IOException error){throw new java.io.UncheckedIOException(error);}
        try(var zip=new ZipFile(engine.resolve("resource pack.zip").toFile())){
            long models=zip.stream().filter(e->e.getName().startsWith("assets/modelengine/models/"+RealmBosses.BLUEPRINT+"/")).count();
            check(models>0,"ModelEngine generated "+models+" item models for the merged rigs");
        }catch(java.io.IOException error){throw new java.io.UncheckedIOException(error);}
        world=Bukkit.getWorlds().getFirst();
        platform(256,256,40,30);
        admin=fake(new Location(world,256.5,120,236.5,0,0),"RealmAdmin").getBukkitEntity();admin.setOp(true);
        Bukkit.getPluginManager().callEvent(new PlayerResourcePackStatusEvent(admin,JudgePackService.PACK_ID,PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED));
        RealmEndAntiAirProbe.run(world,admin,this::check);
        RealmEndAntiAirProbe.controller(garden,world,admin,this::check);
        RealmPerchProbe.run(world,admin,this::check);
        RealmPerchProbe.structure(garden,world,admin,this::check);
        // Its own player: the corridor test leaves ground-contact state that the later enchant audit must not inherit
        RealmPerchProbe.corridor(garden,world,fake(new Location(world,256.5,120,236.5,0,0),"RealmRaider").getBukkitEntity(),this::check);
        later(5,this::next);
    }
    void next(){
        if(index>=RealmBosses.MODELS.size()){encounter(0);return;}
        String id=RealmBosses.MODELS.get(index);
        check(garden.worldBoss().spawnRealmBoss(admin,id),id+": showcase spawns in front of the admin");
        later(8,()->inspect(id));
    }
    void encounter(int which){
        if(which==2){verifyRewards();verifyMoves();finish(null);return;}
        var kind=which==0?DimensionalSanctuaries.Kind.CRYSTAL_CITY:DimensionalSanctuaries.Kind.VOID_RING;
        var site=new DimensionalSanctuaries.Site(kind,256,120,256,0,0,1);
        var arena=new DimensionalSanctuaries.Arena(256,120,256,30,20);
        // A solid arena several blocks thick, with the real End boss anchor below its surface.
        for(int x=226;x<=286;x++)for(int z=226;z<=286;z++)for(int y=110;y<120;y++)
            world.getBlockAt(x,y,z).setType(Material.STONE,false);
        var boss=new RealmBossController(garden,site,arena,new Location(world,256.5,120,256.5),p->true,d->{});
        boss.spawnWaiting(1);
        later(8,()->{
            var anchor=world.getEntitiesByClass(Slime.class).stream().filter(e->e.getPersistentDataContainer().has(garden.key("realm_sanctuary_boss"),PersistentDataType.BYTE)).findFirst().orElseThrow();
            check(anchor.getY()<120,kind+": encounter anchor is inside the solid arena floor");
            var modeled=ModelEngineAPI.getModeledEntity(anchor.getUniqueId());
            var data=modeled.getBase().getData();
            check(Boolean.FALSE.equals(data.getBlockedCull()),kind+": encounter disables blocked-view culling");
            data.setCullInterval(1);data.setBackCull(false);data.setVerticalCull(false);
            data.setBlockedCullIgnoreRadius(5.0);data.setBlockedCullType(CullType.CULLED);
            data.getMutableTracking().put(admin.getUniqueId(),CullType.NO_CULL);
            data.setBlockedCull(true);for(int n=0;n<8;n++)data.cullUpdate();
            getLogger().info(kind+": cull diagnostics location="+data.getLocation()+" bounding="+anchor.getBoundingBox()+" hitbox="+data.getCullHitbox()+" blocked="+data.blockedCull()+" result="+data.getMutableTracking().get(admin.getUniqueId()));
            check(data.getMutableTracking().get(admin.getUniqueId())==CullType.CULLED,kind+": buried anchor is culled with blocked-view checking enabled");
            data.setBlockedCull(false);for(int n=0;n<8;n++)data.cullUpdate();
            check(data.getMutableTracking().get(admin.getUniqueId())==CullType.NO_CULL,kind+": accepted Java viewer is not culled with blocked-view checking disabled");
            if(which==0)verifyBossDamage(boss);
            boss.close();encounter(which+1);
        });
    }
    void verifyBossDamage(RealmBossController boss){
        Difficulty oldDifficulty=world.getDifficulty();
        String key="dimensional-sanctuaries.bosses.damage-percent";
        Object oldScale=garden.getConfig().get(key);
        try {
            world.setDifficulty(Difficulty.NORMAL);
            admin.setInvulnerable(false);admin.setGameMode(GameMode.SURVIVAL);
            Material[] armor={Material.DIAMOND_BOOTS,Material.DIAMOND_LEGGINGS,Material.DIAMOND_CHESTPLATE,Material.DIAMOND_HELMET};
            ItemStack[] protectedArmor=new ItemStack[4];
            for(int i=0;i<armor.length;i++){protectedArmor[i]=new ItemStack(armor[i]);protectedArmor[i].addUnsafeEnchantment(Enchantment.PROTECTION,4);}
            admin.getInventory().setArmorContents(protectedArmor);
            garden.getConfig().set(key,100);
            // A deep health pool so the stronger hits are measured whole instead of stopping at death.
            admin.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).setBaseValue(100);
            var attack=RealmBossController.class.getDeclaredMethod("dealBossDamage",Player.class,double.class,DamageType.class);
            var hazard=RealmBossController.class.getDeclaredMethod("dealLingeringDamage",Player.class,double.class,DamageType.class);
            var heavy=RealmBossController.class.getDeclaredMethod("dealHeavyDamage",Player.class,double.class,DamageType.class);
            var repeated=RealmBossController.class.getDeclaredMethod("dealRepeatedDamage",Player.class,double.class,DamageType.class);
            attack.setAccessible(true);hazard.setAccessible(true);heavy.setAccessible(true);repeated.setAccessible(true);
            // Magic is reduced to 36% by Protection IV x4; the sonic rend slice is not.
            check(Math.abs(measureDamage(attack,boss,10,DamageType.INDIRECT_MAGIC)-15.08)<.02,
                "regular 10 HP attack: 28 magic (10.08 through Protection IV) + 5 Protection-piercing rend");
            check(Math.abs(measureDamage(attack,boss,12.5,DamageType.INDIRECT_MAGIC)-18.85)<.02,
                "regular 12.5 HP attack deals 9.4 hearts through four Protection IV pieces");
            check(Math.abs(measureDamage(heavy,boss,13,DamageType.INDIRECT_MAGIC)-22.308)<.02,
                "failed 13 HP ultimate deals 11.2 hearts through four Protection IV pieces");
            check(Math.abs(measureDamage(repeated,boss,11,DamageType.INDIRECT_MAGIC)-11.22)<.02,
                "repeated prism beam tick: 22 magic + 3.3 rend");
            check(Math.abs(measureDamage(hazard,boss,3.5,DamageType.INDIRECT_MAGIC)-2.415)<.02,
                "lingering 3.5 HP hazard tick: 5.25 magic + 0.525 rend");
            check(Math.abs(measureDamage(attack,boss,13.5,DamageType.SONIC_BOOM)-13.5)<.02,
                "armor and enchantment bypassing sonic pulse deals its base damage without an extra rend");
            brands(boss).clear();admin.setHealth(100);admin.setNoDamageTicks(0);attack.invoke(boss,admin,10,DamageType.INDIRECT_MAGIC);
            admin.setNoDamageTicks(0);double before=admin.getHealth();attack.invoke(boss,admin,10,DamageType.INDIRECT_MAGIC);
            check(Math.abs(before-admin.getHealth()-16.588)<.02,"a second landed hit carries one Realm Brand stack (+10%)");
            brands(boss).clear();admin.setHealth(100);admin.setNoDamageTicks(0);
            for(int i=0;i<8;i++){admin.setHealth(100);admin.setNoDamageTicks(0);attack.invoke(boss,admin,10,DamageType.INDIRECT_MAGIC);}
            admin.setHealth(100);admin.setNoDamageTicks(0);attack.invoke(boss,admin,10,DamageType.INDIRECT_MAGIC);
            check(Math.abs(100-admin.getHealth()-22.62)<.02,"Realm Brand caps at five stacks (+50%)");
            brands(boss).clear();admin.setHealth(100);admin.setNoDamageTicks(0);
            hazard.invoke(boss,admin,3.5,DamageType.INDIRECT_MAGIC);admin.setNoDamageTicks(0);hazard.invoke(boss,admin,3.5,DamageType.INDIRECT_MAGIC);
            check(brands(boss).isEmpty(),"lingering hazards never add Realm Brand stacks");
            String rend="dimensional-sanctuaries.bosses.rend-percent";Object oldRend=garden.getConfig().get(rend);
            garden.getConfig().set(rend,0);
            check(Math.abs(measureDamage(attack,boss,10,DamageType.INDIRECT_MAGIC)-10.08)<.02,"rend-percent 0 turns the Protection-piercing slice off");
            garden.getConfig().set(rend,oldRend);
            garden.getConfig().set(key,50);
            check(Math.abs(measureDamage(attack,boss,10,DamageType.INDIRECT_MAGIC)-7.54)<.02,
                "configured 50 percent damage scales both the magic hit and the rend");
        }catch(ReflectiveOperationException error){throw new RuntimeException(error);}
        finally{
            garden.getConfig().set(key,oldScale);world.setDifficulty(oldDifficulty);
            admin.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).setBaseValue(20);
            admin.getInventory().setArmorContents(new ItemStack[4]);admin.setHealth(20);admin.setInvulnerable(true);admin.setGameMode(GameMode.CREATIVE);
        }
    }
    @SuppressWarnings("unchecked")
    Map<UUID,?> brands(RealmBossController boss)throws ReflectiveOperationException{
        var field=RealmBossController.class.getDeclaredField("brands");field.setAccessible(true);return (Map<UUID,?>)field.get(boss);
    }
    double measureDamage(java.lang.reflect.Method method,RealmBossController boss,double amount,DamageType type)throws ReflectiveOperationException{
        brands(boss).clear();admin.setHealth(100);admin.setNoDamageTicks(0);method.invoke(boss,admin,amount,type);return 100-admin.getHealth();
    }
    /** A RandomGenerator whose draws are fixed, so each reward branch can be forced. */
    static java.util.random.RandomGenerator fixed(double draw){
        return new java.util.random.RandomGenerator(){
            @Override public long nextLong(){return 0;}
            @Override public double nextDouble(){return draw;}
            @Override public int nextInt(int bound){return 0;}
        };
    }
    /** Textured cores, the Seraph's core exchange, per-player spoils and the over-cap enchant books on a real anvil. */
    void verifyRewards(){
        try{
            var cores=com.example.voidscape.boss.RealmCores.class;
            for(var kind:RealmBosses.Kind.values()){
                ItemStack core=com.example.voidscape.boss.RealmCores.create(garden,kind);
                var meta=core.getItemMeta();
                check(core.getType()==Material.HEART_OF_THE_SEA&&new NamespacedKey("voidscape","realm_core_"+kind.model).equals(meta.getItemModel())
                    &&meta.getCustomModelDataComponent().getStrings().equals(List.of("voidscape:realm_core_"+kind.model))
                    &&com.example.voidscape.boss.RealmCores.identify(garden,core)==kind,kind+": core carries its texture model, selector and identity");
                // The untextured Soul Orb an e2.163 victory dropped.
                ItemStack legacy=new ItemStack(Material.HEART_OF_THE_SEA,2);var old=legacy.getItemMeta();
                old.displayName(net.kyori.adventure.text.Component.text(kind.title+" Soul Orb"));
                old.getPersistentDataContainer().set(garden.key("boss_orb"),PersistentDataType.STRING,kind.model);
                old.getPersistentDataContainer().set(garden.key("realm_boss_orb"),PersistentDataType.STRING,kind.model);
                legacy.setItemMeta(old);
                check(com.example.voidscape.boss.RealmCores.migrate(garden,legacy)&&legacy.isSimilar(core)&&legacy.getAmount()==2
                    &&!com.example.voidscape.boss.RealmCores.migrate(garden,legacy),kind+": an old Soul Orb becomes the textured core, stack kept");
                ItemStack renamed=core.clone();var renamedMeta=renamed.getItemMeta();
                renamedMeta.displayName(net.kyori.adventure.text.Component.text("My trophy"));((org.bukkit.inventory.meta.Repairable)renamedMeta).setRepairCost(3);renamed.setItemMeta(renamedMeta);
                check(com.example.voidscape.boss.RealmCores.migrate(garden,renamed)&&renamed.isSimilar(core),kind+": an anvil-renamed core (repair cost 3) is restored so the Seraph still takes it");
                ItemStack fake=new ItemStack(Material.HEART_OF_THE_SEA);var fakeMeta=fake.getItemMeta();fakeMeta.displayName(meta.displayName());fake.setItemMeta(fakeMeta);
                check(com.example.voidscape.boss.RealmCores.identify(garden,fake)==null,kind+": a renamed Heart of the Sea is not a core");
            }
            check(cores!=null,"core class loaded");
            var shop=garden.seraphTrader().shop();
            var method=shop.getClass().getDeclaredMethod("realmCoreOffers");method.setAccessible(true);
            List<?> offers=(List<?>)method.invoke(shop);
            check(offers.size()==4,"the Seraph lists one core exchange per realm boss");
            for(Object offer:offers){
                var first=offer.getClass().getDeclaredMethod("first");first.setAccessible(true);
                var result=offer.getClass().getDeclaredMethod("result");result.setAccessible(true);
                var second=offer.getClass().getDeclaredMethod("second");second.setAccessible(true);
                ItemStack price=(ItemStack)first.invoke(offer);
                ItemStack paid=((java.util.function.Supplier<ItemStack>)result.invoke(offer)).get();
                var kind=com.example.voidscape.boss.RealmCores.identify(garden,price);
                check(kind!=null&&price.getAmount()==1&&second.invoke(offer)==null,"exchange asks for exactly one core: "+kind);
                check(com.example.voidscape.trade.SeraphFeathers.isGolden(garden,paid)&&paid.getAmount()==5,"one "+kind+" core pays five Golden Seraph Feathers");
                var recipe=new org.bukkit.inventory.MerchantRecipe(paid,0,9999,false,0,0,0,0,true);recipe.addIngredient(price);
                ItemStack dropped=com.example.voidscape.boss.RealmCores.create(garden,kind);
                check(org.bukkit.craftbukkit.inventory.CraftMerchantRecipe.fromBukkit(recipe).toMinecraft().satisfiedBy(
                    org.bukkit.craftbukkit.inventory.CraftItemStack.asNMSCopy(dropped),net.minecraft.world.item.ItemStack.EMPTY),
                    "a core dropped by a victory is accepted by the native trade: "+kind);
            }
            // Per-player spoils, forced through each branch.
            var rewards=com.example.voidscape.boss.RealmRewards.class;
            for(var kind:List.of(RealmBosses.Kind.CINDER_COLOSSUS,RealmBosses.Kind.SOUL_HOUND)){
                List<ItemStack> both=com.example.voidscape.boss.RealmRewards.roll(garden,kind,fixed(.1));
                check(both.size()==2&&both.get(0).getItemMeta().getPersistentDataContainer().has(garden.key("adventurer_weapon"))
                    &&both.get(1).getType()==Material.NETHERITE_INGOT&&both.get(1).getAmount()==4,kind+": below 25% grants a rare weapon and four Netherite Ingots");
                List<ItemStack> weapon=com.example.voidscape.boss.RealmRewards.roll(garden,kind,fixed(.4));
                check(weapon.size()==1&&weapon.getFirst().getItemMeta().getPersistentDataContainer().has(garden.key("adventurer_weapon")),kind+": between 25% and 50% grants only the rare weapon");
                check(com.example.voidscape.boss.RealmRewards.roll(garden,kind,fixed(.6)).isEmpty(),kind+": above 50% grants nothing extra");
            }
            for(var kind:List.of(RealmBosses.Kind.CRYSTAL_MOTH,RealmBosses.Kind.UNLIT_SUN)){
                List<ItemStack> book=com.example.voidscape.boss.RealmRewards.roll(garden,kind,fixed(.3));
                check(book.size()==1&&book.getFirst().getType()==Material.ENCHANTED_BOOK,kind+": below 50% grants one enchanted book");
                check(com.example.voidscape.boss.RealmRewards.roll(garden,kind,fixed(.6)).isEmpty(),kind+": above 50% grants no book");
            }
            check(rewards!=null,"rewards class loaded");
            Map<org.bukkit.enchantments.Enchantment,Integer> expected=Map.of(org.bukkit.enchantments.Enchantment.PROTECTION,10,
                org.bukkit.enchantments.Enchantment.SHARPNESS,10,org.bukkit.enchantments.Enchantment.UNBREAKING,8,
                org.bukkit.enchantments.Enchantment.LOOTING,7,org.bukkit.enchantments.Enchantment.EFFICIENCY,10,
                org.bukkit.enchantments.Enchantment.POWER,10,org.bukkit.enchantments.Enchantment.FORTUNE,7,
                org.bukkit.enchantments.Enchantment.FEATHER_FALLING,9);
            ItemStack protectionBook=null;int multi=0;Set<org.bukkit.enchantments.Enchantment> seen=new HashSet<>();
            var random=new java.util.Random(4242);
            for(int i=0;i<300;i++){
                ItemStack book=com.example.voidscape.boss.RealmRewards.book(garden,RealmBosses.Kind.UNLIT_SUN,random);
                var stored=((org.bukkit.inventory.meta.EnchantmentStorageMeta)book.getItemMeta()).getStoredEnchants();
                check(!stored.isEmpty()&&stored.size()<=3,"book "+i+" holds one to three enchantments: "+stored);
                int cost=0;
                for(var e:stored.entrySet()){
                    seen.add(e.getKey());
                    check(e.getValue()>e.getKey().getMaxLevel()&&e.getKey().getMaxLevel()>1&&e.getKey()!=org.bukkit.enchantments.Enchantment.QUICK_CHARGE,
                        "book "+i+": "+e.getKey().getKey()+" "+e.getValue()+" is an over-cap level of a multi-level enchantment");
                    if(expected.containsKey(e.getKey()))check(e.getValue().equals(expected.get(e.getKey())),"book "+i+": "+e.getKey().getKey()+" sits two levels above boss treasure ("+e.getValue()+")");
                    for(var other:stored.keySet())if(other!=e.getKey())check(!e.getKey().conflictsWith(other),"book "+i+": no conflicting pair");
                    cost+=Math.max(1,e.getKey().getAnvilCost()/2)*e.getValue();
                }
                check(cost<=30,"book "+i+" costs at most 30 levels on a fresh item ("+cost+")");
                check(garden.bossEnchants().authorized(book).equals(stored),"book "+i+" carries a boss enchant entitlement for every level");
                if(stored.size()>1)multi++;
                if(protectionBook==null&&stored.size()==1&&stored.containsKey(org.bukkit.enchantments.Enchantment.PROTECTION))protectionBook=book;
            }
            check(multi>40&&seen.size()>=10&&protectionBook!=null,"300 books vary: "+multi+" multi-enchant, "+seen.size()+" enchantments seen");
            // The real anvil applies the over-cap book like boss treasure.
            admin.setGameMode(GameMode.SURVIVAL);admin.setInvulnerable(false);admin.setLevel(100);
            var view=(org.bukkit.inventory.view.AnvilView)admin.openAnvil(null,true);
            try{
                view.getTopInventory().setItem(0,new ItemStack(Material.DIAMOND_CHESTPLATE));
                view.getTopInventory().setItem(1,protectionBook);
                ItemStack out=view.getTopInventory().getItem(2);
                check(out!=null&&out.getType()==Material.DIAMOND_CHESTPLATE&&out.getEnchantmentLevel(org.bukkit.enchantments.Enchantment.PROTECTION)==10
                    &&garden.bossEnchants().authorized(out).getOrDefault(org.bukkit.enchantments.Enchantment.PROTECTION,0)==10,
                    "the anvil puts Protection 10 on a diamond chestplate and keeps its entitlement: "+out);
                check(view.getRepairCost()<40,"the combine stays under the anvil's 40-level ceiling ("+view.getRepairCost()+")");
            }finally{view.getTopInventory().clear();admin.closeInventory();admin.setLevel(0);admin.setGameMode(GameMode.CREATIVE);admin.setInvulnerable(true);}
            verifyBootsBook();
            verifyOverCapEffects();
            verifyNoAnvilClimb();
            verifyEnchantAudit();
        }catch(ReflectiveOperationException error){throw new RuntimeException(error);}
    }
    ItemStack overCap(Material type,Map<org.bukkit.enchantments.Enchantment,Integer> levels){
        ItemStack item=new ItemStack(type);var meta=item.getItemMeta();
        levels.forEach((e,l)->{if(meta instanceof org.bukkit.inventory.meta.EnchantmentStorageMeta book)book.addStoredEnchant(e,l,true);else meta.addEnchant(e,l,true);});
        garden.bossEnchants().markGenerated(meta);item.setItemMeta(meta);return item;
    }
    /** A boots book never lands on a sword, in survival (vanilla rule) or creative (which skips it natively). */
    void verifyBootsBook(){
        var protection=org.bukkit.enchantments.Enchantment.PROTECTION;var falling=org.bukkit.enchantments.Enchantment.FEATHER_FALLING;
        ItemStack book=overCap(Material.ENCHANTED_BOOK,Map.of(protection,10,falling,9));
        ItemStack mixed=overCap(Material.ENCHANTED_BOOK,Map.of(protection,10,falling,9,org.bukkit.enchantments.Enchantment.UNBREAKING,8));
        admin.setInvulnerable(false);admin.setLevel(100);
        try{
            for(GameMode mode:List.of(GameMode.SURVIVAL,GameMode.CREATIVE)){
                admin.setGameMode(mode);
                var view=(org.bukkit.inventory.view.AnvilView)admin.openAnvil(null,true);
                try{
                    view.getTopInventory().setItem(0,new ItemStack(Material.DIAMOND_SWORD));view.getTopInventory().setItem(1,book.clone());
                    ItemStack out=view.getTopInventory().getItem(2);
                    check(out==null||out.isEmpty(),mode+": Protection X + Feather Falling IX book gives no result on a sword: "+out);
                    view.getTopInventory().setItem(1,mixed.clone());out=view.getTopInventory().getItem(2);
                    check(out!=null&&out.getType()==Material.DIAMOND_SWORD&&out.getEnchantmentLevel(protection)==0&&out.getEnchantmentLevel(falling)==0
                        &&out.getEnchantmentLevel(org.bukkit.enchantments.Enchantment.UNBREAKING)==8,mode+": from a mixed book a sword takes only Unbreaking VIII: "+out);
                    view.getTopInventory().setItem(0,new ItemStack(Material.DIAMOND_BOOTS));view.getTopInventory().setItem(1,book.clone());out=view.getTopInventory().getItem(2);
                    check(out!=null&&out.getEnchantmentLevel(protection)==10&&out.getEnchantmentLevel(falling)==9
                        &&garden.bossEnchants().authorized(out).equals(Map.of(protection,10,falling,9)),mode+": the same book goes on boots with both entitlements: "+out);
                }finally{view.getTopInventory().clear();admin.closeInventory();}
            }
        }finally{admin.setLevel(0);admin.setGameMode(GameMode.CREATIVE);admin.setInvulnerable(true);}
    }
    ItemStack unstamped(Material type,Map<org.bukkit.enchantments.Enchantment,Integer> levels){
        ItemStack item=new ItemStack(type);var meta=item.getItemMeta();
        levels.forEach((e,l)->{if(meta instanceof org.bukkit.inventory.meta.EnchantmentStorageMeta book)book.addStoredEnchant(e,l,true);else meta.addEnchant(e,l,true);});
        item.setItemMeta(meta);return item;
    }
    /** One real survival anvil combine; null when the anvil offers nothing. */
    ItemStack combine(ItemStack left,ItemStack right){
        var view=(org.bukkit.inventory.view.AnvilView)admin.openAnvil(null,true);
        try{
            view.getTopInventory().setItem(0,left.clone());view.getTopInventory().setItem(1,right.clone());
            ItemStack out=view.getTopInventory().getItem(2);
            return out==null||out.isEmpty()?null:out.clone();
        }finally{view.getTopInventory().clear();admin.closeInventory();}
    }
    int level(ItemStack item,org.bukkit.enchantments.Enchantment enchant){
        if(item==null)return 0;
        return item.getItemMeta() instanceof org.bukkit.inventory.meta.EnchantmentStorageMeta book?book.getStoredEnchantLevel(enchant):item.getEnchantmentLevel(enchant);
    }
    /** Boss levels transfer as awarded but never grow: no anvil route reaches a boss tier from below. */
    @SuppressWarnings("unchecked")
    void verifyNoAnvilClimb(){
        var protection=org.bukkit.enchantments.Enchantment.PROTECTION;var sharpness=org.bukkit.enchantments.Enchantment.SHARPNESS;
        var looting=org.bukkit.enchantments.Enchantment.LOOTING;var efficiency=org.bukkit.enchantments.Enchantment.EFFICIENCY;
        admin.setGameMode(GameMode.SURVIVAL);admin.setLevel(100);
        try{
            ItemStack p8=overCap(Material.ENCHANTED_BOOK,Map.of(protection,8)),p10=overCap(Material.ENCHANTED_BOOK,Map.of(protection,10));
            check(level(combine(p8,p8),protection)==8,"boss Protection VIII + VIII books stay VIII (no IX)");
            check(level(combine(p10,p10),protection)==10,"realm Protection X + X books stay X (no XI)");
            check(level(combine(overCap(Material.DIAMOND_CHESTPLATE,Map.of(protection,8)),p8),protection)==8,"a Protection VIII chestplate + VIII book stays VIII");
            check(level(combine(overCap(Material.DIAMOND_CHESTPLATE,Map.of(protection,8)),p10),protection)==10,"only the awarded Protection X book raises a VIII chestplate to X");
            ItemStack vanilla=unstamped(Material.ENCHANTED_BOOK,Map.of(protection,4));
            check(level(combine(vanilla,vanilla),protection)==4,"vanilla Protection IV + IV books stay IV");
            check(level(combine(overCap(Material.DIAMOND_CHESTPLATE,Map.of(protection,8)),vanilla),protection)==8,"a vanilla IV book never lowers or raises a boss VIII chestplate");
            check(level(combine(overCap(Material.DIAMOND_SWORD,Map.of(sharpness,8)),overCap(Material.ENCHANTED_BOOK,Map.of(sharpness,8))),sharpness)==8,"Sharpness VIII + VIII stays VIII");
            check(level(combine(overCap(Material.DIAMOND_SWORD,Map.of(looting,7)),overCap(Material.ENCHANTED_BOOK,Map.of(looting,7))),looting)==7,"Looting VII + VII stays VII");
            check(level(combine(overCap(Material.DIAMOND_PICKAXE,Map.of(efficiency,10)),overCap(Material.ENCHANTED_BOOK,Map.of(efficiency,10))),efficiency)==10,"Efficiency X + X stays X");
            ItemStack sword5=unstamped(Material.DIAMOND_SWORD,Map.of(sharpness,5));
            check(level(combine(sword5,unstamped(Material.ENCHANTED_BOOK,Map.of(sharpness,5))),sharpness)==5,"vanilla Sharpness V + V stays V");
            // A forged book with an over-cap level but no award stamp gains nothing.
            ItemStack forged=unstamped(Material.ENCHANTED_BOOK,Map.of(protection,12));
            ItemStack out=combine(overCap(Material.DIAMOND_CHESTPLATE,Map.of(protection,8)),forged);
            check(level(out,protection)<=8&&garden.bossEnchants().level(out,protection)<=8,"an unstamped Protection XII book cannot lift a boss VIII chestplate: "+level(out,protection));
            out=combine(new ItemStack(Material.DIAMOND_CHESTPLATE),forged);
            check(level(out,protection)<=4&&garden.bossEnchants().level(out,protection)==0,"an unstamped Protection XII book gives at most the vanilla IV and no award: "+level(out,protection));
            // With anvil-transfer off, awarded levels are clamped like vanilla.
            var field=garden.bossEnchants().getClass().getDeclaredField("config");field.setAccessible(true);
            var config=(org.bukkit.configuration.file.YamlConfiguration)field.get(garden.bossEnchants());
            config.set("mechanics.anvil-transfer",false);
            try{check(level(combine(new ItemStack(Material.DIAMOND_CHESTPLATE),p10),protection)<=4,"mechanics.anvil-transfer false: a Protection X book applies only the vanilla IV");}
            finally{config.set("mechanics.anvil-transfer",true);}
        }catch(ReflectiveOperationException error){throw new RuntimeException(error);}
        finally{admin.setLevel(0);admin.setGameMode(GameMode.CREATIVE);}
    }
    final class Tally implements org.bukkit.event.Listener {
        int flesh,diamonds;
        @org.bukkit.event.EventHandler(priority=org.bukkit.event.EventPriority.MONITOR)
        public void death(org.bukkit.event.entity.EntityDeathEvent event){
            for(ItemStack drop:event.getDrops())if(drop.getType()==Material.ROTTEN_FLESH)flesh+=drop.getAmount();
            event.getDrops().clear();
        }
        @org.bukkit.event.EventHandler(priority=org.bukkit.event.EventPriority.MONITOR)
        public void drop(org.bukkit.event.block.BlockDropItemEvent event){
            for(Item item:event.getItems())if(item.getItemStack().getType()==Material.DIAMOND)diamonds+=item.getItemStack().getAmount();
            event.getItems().clear();
        }
    }
    double attribute(org.bukkit.attribute.Attribute attribute,org.bukkit.inventory.EquipmentSlot slot,ItemStack item){
        admin.getInventory().setItem(slot,item);((org.bukkit.craftbukkit.entity.CraftPlayer)admin).getHandle().detectEquipmentUpdates();
        double value=admin.getAttribute(attribute).getValue();
        admin.getInventory().setItem(slot,null);((org.bukkit.craftbukkit.entity.CraftPlayer)admin).getHandle().detectEquipmentUpdates();
        return value;
    }
    /** Every enchantment a realm book can roll, measured with the game's own code at the old boss level and at +2. */
    void verifyEnchantAudit(){
        var E=new Object(){org.bukkit.enchantments.Enchantment of(String id){return org.bukkit.Registry.ENCHANTMENT.get(NamespacedKey.minecraft(id));}};
        var level=((CraftWorld)world).getHandle();var me=((org.bukkit.craftbukkit.entity.CraftPlayer)admin).getHandle();
        Location spot=new Location(world,256.5,120,240.5);
        Zombie zombie=world.spawn(spot,Zombie.class,z->{z.setAI(false);z.setSilent(true);z.setPersistent(false);z.setAdult();});
        Guardian guardian=world.spawn(spot.clone().add(4,0,0),Guardian.class,g->{g.setAI(false);g.setSilent(true);g.setPersistent(false);});
        Arrow arrow=world.spawn(spot.clone().add(0,3,0),Arrow.class,a->{a.setGravity(false);a.setPersistent(false);});
        Tally tally=new Tally();Bukkit.getPluginManager().registerEvents(tally,this);
        admin.setGameMode(GameMode.SURVIVAL);
        try{
            var target=((org.bukkit.craftbukkit.entity.CraftEntity)zombie).getHandle();
            var fish=((org.bukkit.craftbukkit.entity.CraftEntity)guardian).getHandle();
            var shot=level.damageSources().arrow((net.minecraft.world.entity.projectile.arrow.AbstractArrow)((org.bukkit.craftbukkit.entity.CraftEntity)arrow).getHandle(),me);
            var melee=level.damageSources().playerAttack(me);var smash=level.damageSources().mace(me);
            java.util.function.BiFunction<Material,Map<org.bukkit.enchantments.Enchantment,Integer>,net.minecraft.world.item.ItemStack> nms=
                (type,levels)->org.bukkit.craftbukkit.inventory.CraftItemStack.asNMSCopy(overCap(type,levels));
            // Damage: Sharpness (melee), Power (arrow), Impaling (aquatic), Density per fallen block, Breach, Punch
            float s8=net.minecraft.world.item.enchantment.EnchantmentHelper.modifyDamage(level,nms.apply(Material.DIAMOND_SWORD,Map.of(E.of("sharpness"),8)),target,melee,0f);
            float s10=net.minecraft.world.item.enchantment.EnchantmentHelper.modifyDamage(level,nms.apply(Material.DIAMOND_SWORD,Map.of(E.of("sharpness"),10)),target,melee,0f);
            check(Math.abs(s10-s8-1f)<1e-3,"Sharpness X adds +"+s10+" melee damage vs +"+s8+" at VIII");
            float p8=net.minecraft.world.item.enchantment.EnchantmentHelper.modifyDamage(level,nms.apply(Material.BOW,Map.of(E.of("power"),8)),target,shot,2f);
            float p10=net.minecraft.world.item.enchantment.EnchantmentHelper.modifyDamage(level,nms.apply(Material.BOW,Map.of(E.of("power"),10)),target,shot,2f);
            check(p10>p8,"Power X arrow damage "+p10+" > Power VIII "+p8);
            float i8=net.minecraft.world.item.enchantment.EnchantmentHelper.modifyDamage(level,nms.apply(Material.TRIDENT,Map.of(E.of("impaling"),8)),fish,melee,0f);
            float i10=net.minecraft.world.item.enchantment.EnchantmentHelper.modifyDamage(level,nms.apply(Material.TRIDENT,Map.of(E.of("impaling"),10)),fish,melee,0f);
            check(i10>i8&&i8>0,"Impaling X adds +"+i10+" vs aquatic, VIII +"+i8);
            float d7=net.minecraft.world.item.enchantment.EnchantmentHelper.modifyFallBasedDamage(level,nms.apply(Material.MACE,Map.of(E.of("density"),7)),target,smash,0f);
            float d9=net.minecraft.world.item.enchantment.EnchantmentHelper.modifyFallBasedDamage(level,nms.apply(Material.MACE,Map.of(E.of("density"),9)),target,smash,0f);
            check(d9>d7&&d7>0,"Density IX adds "+d9+" per fallen block, VII "+d7);
            float b5=net.minecraft.world.item.enchantment.EnchantmentHelper.modifyArmorEffectiveness(level,nms.apply(Material.MACE,Map.of(E.of("breach"),5)),target,smash,1f);
            float b7=net.minecraft.world.item.enchantment.EnchantmentHelper.modifyArmorEffectiveness(level,nms.apply(Material.MACE,Map.of(E.of("breach"),7)),target,smash,1f);
            check(b7<b5,"Breach VII leaves target armor at "+b7+" effectiveness (V: "+b5+"; below 0 counts as none)");
            float k3=net.minecraft.world.item.enchantment.EnchantmentHelper.modifyKnockback(level,nms.apply(Material.BOW,Map.of(E.of("punch"),3)),target,shot,0f);
            float k5=net.minecraft.world.item.enchantment.EnchantmentHelper.modifyKnockback(level,nms.apply(Material.BOW,Map.of(E.of("punch"),5)),target,shot,0f);
            check(k5>k3,"Punch V knockback "+k5+" > Punch III "+k3);
            int[] fire=new int[2];int[] fa={3,5};
            for(int i=0;i<2;i++){zombie.setFireTicks(0);
                net.minecraft.world.item.enchantment.EnchantmentHelper.doPostAttackEffectsWithItemSource(level,target,melee,nms.apply(Material.DIAMOND_SWORD,Map.of(E.of("fire_aspect"),fa[i])));
                fire[i]=zombie.getFireTicks();}
            zombie.setFireTicks(0);
            check(fire[1]>fire[0]&&fire[0]>0,"Fire Aspect V sets "+fire[1]+" burning ticks, III "+fire[0]);
            // Durability: Unbreaking VIII skips 8 of 9 uses
            var u6=nms.apply(Material.DIAMOND_SWORD,Map.of(E.of("unbreaking"),6));var u8=nms.apply(Material.DIAMOND_SWORD,Map.of(E.of("unbreaking"),8));
            int used6=0,used8=0;
            for(int n=0;n<20000;n++){used6+=net.minecraft.world.item.enchantment.EnchantmentHelper.processDurabilityChange(level,u6,1);used8+=net.minecraft.world.item.enchantment.EnchantmentHelper.processDurabilityChange(level,u8,1);}
            check(Math.abs(used8/20000.0-1/9.0)<.012&&Math.abs(used6/20000.0-1/7.0)<.012,"Unbreaking VIII loses durability "+used8+"/20000 times (expected 1/9), VI "+used6+" (1/7)");
            // Projectiles and tridents
            var arrowItem=org.bukkit.craftbukkit.inventory.CraftItemStack.asNMSCopy(new ItemStack(Material.ARROW));
            int pc6=net.minecraft.world.item.enchantment.EnchantmentHelper.getPiercingCount(level,nms.apply(Material.CROSSBOW,Map.of(E.of("piercing"),6)),arrowItem);
            int pc8=net.minecraft.world.item.enchantment.EnchantmentHelper.getPiercingCount(level,nms.apply(Material.CROSSBOW,Map.of(E.of("piercing"),8)),arrowItem);
            check(pc8==8&&pc6==6,"Piercing VIII passes through "+pc8+" mobs, VI "+pc6);
            int l4=net.minecraft.world.item.enchantment.EnchantmentHelper.getTridentReturnToOwnerAcceleration(level,nms.apply(Material.TRIDENT,Map.of(E.of("loyalty"),4)),me);
            int l6=net.minecraft.world.item.enchantment.EnchantmentHelper.getTridentReturnToOwnerAcceleration(level,nms.apply(Material.TRIDENT,Map.of(E.of("loyalty"),6)),me);
            check(l6>l4,"Loyalty VI returns at "+l6+", IV at "+l4);
            float r4=net.minecraft.world.item.enchantment.EnchantmentHelper.getTridentSpinAttackStrength(nms.apply(Material.TRIDENT,Map.of(E.of("riptide"),4)),me);
            float r6=net.minecraft.world.item.enchantment.EnchantmentHelper.getTridentSpinAttackStrength(nms.apply(Material.TRIDENT,Map.of(E.of("riptide"),6)),me);
            check(r6>r4,"Riptide VI launches at "+r6+", IV at "+r4);
            // Attributes granted while equipped
            double sw5=attribute(org.bukkit.attribute.Attribute.SWEEPING_DAMAGE_RATIO,org.bukkit.inventory.EquipmentSlot.HAND,overCap(Material.DIAMOND_SWORD,Map.of(E.of("sweeping_edge"),5)));
            double sw7=attribute(org.bukkit.attribute.Attribute.SWEEPING_DAMAGE_RATIO,org.bukkit.inventory.EquipmentSlot.HAND,overCap(Material.DIAMOND_SWORD,Map.of(E.of("sweeping_edge"),7)));
            check(Math.abs(sw7-7/8.0)<1e-6&&Math.abs(sw5-5/6.0)<1e-6,"Sweeping Edge VII sweeps "+sw7+" of the hit, V "+sw5);
            double e8=attribute(org.bukkit.attribute.Attribute.MINING_EFFICIENCY,org.bukkit.inventory.EquipmentSlot.HAND,overCap(Material.DIAMOND_PICKAXE,Map.of(E.of("efficiency"),8)));
            double e10=attribute(org.bukkit.attribute.Attribute.MINING_EFFICIENCY,org.bukkit.inventory.EquipmentSlot.HAND,overCap(Material.DIAMOND_PICKAXE,Map.of(E.of("efficiency"),10)));
            check(e10==101&&e8==65,"Efficiency X adds "+e10+" mining speed, VIII "+e8);
            double o5=attribute(org.bukkit.attribute.Attribute.OXYGEN_BONUS,org.bukkit.inventory.EquipmentSlot.HEAD,overCap(Material.DIAMOND_HELMET,Map.of(E.of("respiration"),5)));
            double o7=attribute(org.bukkit.attribute.Attribute.OXYGEN_BONUS,org.bukkit.inventory.EquipmentSlot.HEAD,overCap(Material.DIAMOND_HELMET,Map.of(E.of("respiration"),7)));
            check(o7==7&&o5==5,"Respiration VII oxygen bonus "+o7+", V "+o5);
            double ds5=attribute(org.bukkit.attribute.Attribute.WATER_MOVEMENT_EFFICIENCY,org.bukkit.inventory.EquipmentSlot.FEET,overCap(Material.DIAMOND_BOOTS,Map.of(E.of("depth_strider"),5)));
            double ds7=attribute(org.bukkit.attribute.Attribute.WATER_MOVEMENT_EFFICIENCY,org.bukkit.inventory.EquipmentSlot.FEET,overCap(Material.DIAMOND_BOOTS,Map.of(E.of("depth_strider"),7)));
            var effects=garden.bossEnchantEffects();
            check(ds5==1&&ds7==1&&effects.depthMultiplier(7)>effects.depthMultiplier(5),"Depth Strider saturates natively at III ("+ds5+"/"+ds7+"); Evergarden's water speed bonus VII ×"+effects.depthMultiplier(7)+" vs V ×"+effects.depthMultiplier(5));
            double ss5=attribute(org.bukkit.attribute.Attribute.SNEAKING_SPEED,org.bukkit.inventory.EquipmentSlot.LEGS,overCap(Material.DIAMOND_LEGGINGS,Map.of(E.of("swift_sneak"),5)));
            double ss7=attribute(org.bukkit.attribute.Attribute.SNEAKING_SPEED,org.bukkit.inventory.EquipmentSlot.LEGS,overCap(Material.DIAMOND_LEGGINGS,Map.of(E.of("swift_sneak"),7)));
            check(ss5==1&&ss7==1&&effects.swiftMultiplier(7)>effects.swiftMultiplier(5),"Swift Sneak saturates natively at V ("+ss5+"/"+ss7+"); Evergarden's sneak speed bonus VII ×"+effects.swiftMultiplier(7)+" vs V ×"+effects.swiftMultiplier(5));
            // Location effects: Soul Speed on soul sand, Frost Walker over water
            Location feet=admin.getLocation();org.bukkit.block.Block below=feet.getBlock().getRelative(org.bukkit.block.BlockFace.DOWN);
            below.setType(Material.SOUL_SAND,false);me.setOnGround(true);
            double[] speed=new double[2];int[] soul={5,7};
            for(int i=0;i<2;i++){
                admin.getInventory().setBoots(overCap(Material.DIAMOND_BOOTS,Map.of(E.of("soul_speed"),soul[i])));me.detectEquipmentUpdates();
                net.minecraft.world.item.enchantment.EnchantmentHelper.runLocationChangedEffects(level,me);
                speed[i]=admin.getAttribute(org.bukkit.attribute.Attribute.MOVEMENT_SPEED).getValue();
                admin.getInventory().setBoots(null);me.detectEquipmentUpdates();net.minecraft.world.item.enchantment.EnchantmentHelper.stopLocationBasedEffects(me);
            }
            below.setType(Material.STONE,false);
            check(speed[1]>speed[0],"Soul Speed VII on soul sand: movement "+speed[1]+" vs V "+speed[0]);
            int[] frozen=new int[2];int[] fw={3,5};
            for(int i=0;i<2;i++){
                for(int x=-10;x<=10;x++)for(int z=-10;z<=10;z++)below.getRelative(x,0,z).setType(Material.WATER,false);
                admin.getInventory().setBoots(overCap(Material.DIAMOND_BOOTS,Map.of(E.of("frost_walker"),fw[i])));me.detectEquipmentUpdates();me.setOnGround(true);
                net.minecraft.world.item.enchantment.EnchantmentHelper.runLocationChangedEffects(level,me);
                for(int x=-10;x<=10;x++)for(int z=-10;z<=10;z++)if(below.getRelative(x,0,z).getType()==Material.FROSTED_ICE)frozen[i]++;
                admin.getInventory().setBoots(null);me.detectEquipmentUpdates();
            }
            for(int x=-10;x<=10;x++)for(int z=-10;z<=10;z++)below.getRelative(x,0,z).setType(Material.STONE,false);
            check(frozen[1]>frozen[0]&&frozen[0]>0,"Frost Walker V freezes "+frozen[1]+" water blocks, III "+frozen[0]);
            // Fortune: real breaks of diamond ore
            org.bukkit.block.Block ore=feet.getBlock().getRelative(2,0,0);double[] mined=new double[3];int[] fortune={0,5,7};
            for(int i=0;i<3;i++){
                admin.getInventory().setItemInMainHand(fortune[i]==0?new ItemStack(Material.DIAMOND_PICKAXE):overCap(Material.DIAMOND_PICKAXE,Map.of(E.of("fortune"),fortune[i])));
                tally.diamonds=0;
                for(int n=0;n<300;n++){ore.setType(Material.DIAMOND_ORE,false);admin.breakBlock(ore);}
                mined[i]=tally.diamonds/300.0;
            }
            ore.setType(Material.AIR,false);admin.getInventory().setItemInMainHand(null);
            check(Math.abs(mined[0]-1)<.01&&mined[2]>mined[1]+.3&&mined[1]>2.6&&mined[2]>3.6,
                "Fortune VII mines "+mined[2]+" diamonds per ore (expected ~4.1), V "+mined[1]+" (~3.1), none "+mined[0]);
            // Looting: real kills of adult zombies
            java.lang.reflect.Field ticker=null;
            for(Class<?> type=me.getClass();type!=null&&ticker==null;type=type.getSuperclass())for(var f:type.getDeclaredFields())if(f.getName().equals("attackStrengthTicker")){ticker=f;f.setAccessible(true);}
            double[] flesh=new double[3];int[] looting={0,5,7};
            for(int i=0;i<3;i++){
                admin.getInventory().setItemInMainHand(looting[i]==0?new ItemStack(Material.DIAMOND_SWORD):overCap(Material.DIAMOND_SWORD,Map.of(E.of("looting"),looting[i])));me.detectEquipmentUpdates();
                tally.flesh=0;int kills=0;
                for(int n=0;n<250;n++){
                    Zombie victim=world.spawn(spot.clone().add(0,0,2),Zombie.class,z->{z.setAI(false);z.setSilent(true);z.setAdult();z.getEquipment().clear();});
                    victim.setHealth(1);if(ticker!=null)ticker.setInt(me,100);
                    admin.attack(victim);if(victim.isDead())kills++;else victim.remove();
                }
                flesh[i]=tally.flesh/(double)Math.max(1,kills);
                check(kills==250,"looting "+looting[i]+": every test zombie died to the sword ("+kills+")");
            }
            admin.getInventory().setItemInMainHand(null);
            check(Math.abs(flesh[0]-1)<.25&&flesh[2]>flesh[1]+.5&&flesh[1]>3&&flesh[2]>4,
                "Looting VII drops "+flesh[2]+" rotten flesh per zombie (expected ~4.5), V "+flesh[1]+" (~3.5), none "+flesh[0]+" (~1)");
        }catch(ReflectiveOperationException error){throw new RuntimeException(error);}
        finally{
            org.bukkit.event.HandlerList.unregisterAll(tally);zombie.remove();guardian.remove();arrow.remove();
            for(Item item:world.getEntitiesByClass(Item.class))item.remove();
            admin.getInventory().clear();admin.setGameMode(GameMode.CREATIVE);admin.setInvulnerable(true);
        }
    }
    double hurt(DamageType type){
        admin.setHealth(100);admin.setNoDamageTicks(0);
        admin.damage(20,org.bukkit.damage.DamageSource.builder(type).build());return 100-admin.getHealth();
    }
    /** What the over-cap levels really do: only worn armor counts, and each level past the native cap still helps. */
    void verifyOverCapEffects(){
        var protection=org.bukkit.enchantments.Enchantment.PROTECTION;var falling=org.bukkit.enchantments.Enchantment.FEATHER_FALLING;
        Difficulty oldDifficulty=world.getDifficulty();
        try{
            world.setDifficulty(Difficulty.NORMAL);admin.setGameMode(GameMode.SURVIVAL);admin.setInvulnerable(false);
            admin.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).setBaseValue(100);
            Material[] armor={Material.DIAMOND_BOOTS,Material.DIAMOND_LEGGINGS,Material.DIAMOND_CHESTPLATE,Material.DIAMOND_HELMET};
            double bare=hurt(DamageType.INDIRECT_MAGIC);
            double[] taken=new double[2];int[] levels={8,10};
            for(int i=0;i<2;i++){
                ItemStack[] set=new ItemStack[4];for(int s=0;s<4;s++)set[s]=overCap(armor[s],Map.of(protection,levels[i]));
                admin.getInventory().setArmorContents(set);taken[i]=hurt(DamageType.INDIRECT_MAGIC);
            }
            admin.getInventory().setArmorContents(new ItemStack[4]);
            // Native protection caps at 80%; each authorized point past it removes 4% of what is left.
            check(Math.abs(bare-20)<.02&&Math.abs(taken[0]-20*.2*Math.pow(.96,12))<.02&&Math.abs(taken[1]-20*.2*Math.pow(.96,20))<.02,
                "Protection X armor takes "+taken[1]+" of 20 magic, Protection VIII "+taken[0]+", none "+bare);
            admin.getInventory().setItemInMainHand(overCap(Material.DIAMOND_SWORD,Map.of(protection,10)));
            check(Math.abs(hurt(DamageType.INDIRECT_MAGIC)-20)<.02,"Protection X held on a sword protects nothing");
            admin.getInventory().setItemInMainHand(null);
            double[] fall=new double[2];int[] ff={7,9};
            for(int i=0;i<2;i++){admin.getInventory().setBoots(overCap(Material.DIAMOND_BOOTS,Map.of(falling,ff[i])));fall[i]=hurt(DamageType.FALL);}
            admin.getInventory().setBoots(null);
            check(Math.abs(fall[0]-20*.2*.96)<.02&&Math.abs(fall[1]-20*.2*Math.pow(.96,7))<.02,
                "Feather Falling IX boots take "+fall[1]+" of 20 fall damage, Feather Falling VII "+fall[0]);
        }finally{
            world.setDifficulty(oldDifficulty);admin.getInventory().setArmorContents(new ItemStack[4]);admin.getInventory().setItemInMainHand(null);
            admin.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).setBaseValue(20);admin.setHealth(20);
            admin.setGameMode(GameMode.CREATIVE);admin.setInvulnerable(true);
        }
    }
    /** Drives every move of every realm boss to completion in phase 1 and the final phase, far from any viewer. */
    void verifyMoves(){
        // Both fake players stay beyond particle and entity-tracking range, so thousands of ticks queue no packets.
        for(int x:new int[]{256+1400,256-1400}){world.getChunkAt(x>>4,256>>4);world.addPluginChunkTicket(x>>4,256>>4,this);}
        Player mover=fake(new Location(world,256.5+1400,200,256.5),"RealmMover").getBukkitEntity();
        admin.teleport(new Location(world,256.5-1400,200,256.5));
        Map<DimensionalSanctuaries.Kind,List<String>> expected=new LinkedHashMap<>();
        expected.put(DimensionalSanctuaries.Kind.CROWN_FORTRESS,List.of("COLOSSUS_FIST","COLOSSUS_FISSURE","COLOSSUS_RUBBLE","COLOSSUS_ERUPTION","COLOSSUS_QUAKE"));
        expected.put(DimensionalSanctuaries.Kind.BONE_TEMPLE,List.of("HOUND_FANG","HOUND_BURROW","HOUND_PREY_POUNCE","HOUND_HOWL","HOUND_REND"));
        expected.put(DimensionalSanctuaries.Kind.CRYSTAL_CITY,List.of("MOTH_WING_SLASH","MOTH_SCALE_RAIN","MOTH_PRISM_CRYSTALS","MOTH_CHRYSALIS","MOTH_SHARD_NOVA"));
        expected.put(DimensionalSanctuaries.Kind.VOID_RING,List.of("SUN_SOLAR_GAZE","SUN_RING_STORM","SUN_MONOLITHS","SUN_TOTAL_ECLIPSE","SUN_FALLING_STARS"));
        try{
            var action=RealmBossController.class.getDeclaredField("action");action.setAccessible(true);
            var two=RealmBossController.class.getDeclaredField("phaseTwo");two.setAccessible(true);
            var three=RealmBossController.class.getDeclaredField("phaseThree");three.setAccessible(true);
            var hidden=RealmBossController.class.getDeclaredField("hiddenUnderground");hidden.setAccessible(true);
            var core=RealmBossController.class.getDeclaredField("coreExposed");core.setAccessible(true);
            var health=RealmBossController.class.getDeclaredField("health");health.setAccessible(true);
            long now=10_000;
            for(var entry:expected.entrySet()){
                var site=new DimensionalSanctuaries.Site(entry.getKey(),256,120,256,0,0,1);
                var boss=new RealmBossController(garden,site,new DimensionalSanctuaries.Arena(256,120,256,30,20),new Location(world,256.5,120,256.5),p->p==mover,d->{});
                try{
                    boss.spawnWaiting(now);boss.awaken(List.of(mover));
                    for(boolean finalPhase:new boolean[]{false,true}){
                        two.setBoolean(boss,finalPhase);three.setBoolean(boss,finalPhase);
                        for(int slot=0;slot<5;slot++){
                            String name=entry.getValue().get(slot),label=entry.getKey()+" "+(finalPhase?"final phase ":"phase 1 ")+name;
                            boss.forceSkill(slot);
                            check(action.get(boss).toString().equals(name),label+": skill slot "+slot+" starts it");
                            int ticks=0;
                            while(!action.get(boss).toString().equals("NONE")&&ticks<400){ticks++;boss.tick(++now);}
                            check(ticks<400,label+": completes in "+ticks+" ticks");
                            check(!hidden.getBoolean(boss)&&!core.getBoolean(boss),label+": leaves the boss surfaced with its core closed");
                        }
                    }
                    two.setBoolean(boss,true);three.setBoolean(boss,false);health.setDouble(boss,boss.maxHealth()*0.25);boss.tick(++now);
                    check(boss.isPhaseThree()&&action.get(boss).toString().equals("TRANSITION"),entry.getKey()+": 30% HP opens the final phase");
                }finally{boss.close();}
            }
        }catch(ReflectiveOperationException error){throw new RuntimeException(error);}
        finally{
            mover.setGameMode(GameMode.SPECTATOR);
            admin.teleport(new Location(world,256.5,120,236.5,0,0));
        }
    }
    com.ticxo.modelengine.api.generator.blueprint.ModelBlueprint merged(){return ModelEngineAPI.getBlueprint(RealmBosses.BLUEPRINT);}
    Entity tagged(Class<? extends Entity> type){
        var key=garden.key("boss_showcase");
        return world.getEntitiesByClass(type).stream().filter(e->e.getPersistentDataContainer().has(key,PersistentDataType.BYTE)).findFirst().orElse(null);
    }
    void inspect(String id){
        Entity anchor=tagged(Slime.class);
        check(anchor!=null,id+": invisible showcase anchor exists");
        var modeled=ModelEngineAPI.getModeledEntity(anchor.getUniqueId());
        check(modeled!=null&&modeled.getModel(RealmBosses.BLUEPRINT).isPresent(),id+": merged rig attached to the anchor");
        var data=modeled.getBase().getData();
        boolean aerial=id.equals("crystal_moth")||id.equals("unlit_sun");
        check(aerial?Boolean.FALSE.equals(data.getBlockedCull()):data.getBlockedCull()==null,
            id+": blocked-view culling "+(aerial?"disabled for the buried arena anchor":"inherits server settings"));
        var model=modeled.getModel(RealmBosses.BLUEPRINT).get();
        var bones=model.getBones().keySet();
        long own=bones.stream().filter(b->RealmBosses.owns(id,b)).count();
        check(own>0&&own==bones.size(),id+": the other rigs' bones are removed ("+own+" own bones remain of "+bones.size()+")");
        String head=RealmBosses.Kind.of(id).code;
        check(bones.contains(head)||bones.contains("hi_"+head),id+": keeps its head tracker");
        int expected=(int)merged().getFlatMap().keySet().stream().filter(b->RealmBosses.owns(id,b)).count();
        check(bones.size()==expected,id+": every one of its "+expected+" bones survives the trim ("+bones.size()+")");
        var handler=(IStateMachineHandler)model.getAnimationHandler();
        check(handler.getAnimation(1,id+"_aura")!=null,id+": aura loops on its own lane");
        check(handler.getAnimation(10,id+"_spawn")!=null,id+": the showcase opens with its spawn clip");
        double ahead=anchor.getLocation().toVector().setY(0).distance(admin.getLocation().toVector().setY(0));
        check(ahead>=15&&ahead<=21,id+": spawned "+String.format(Locale.ROOT,"%.1f",ahead)+" blocks ahead");
        Entity carrier=tagged(ArmorStand.class);
        if(garden.arkhaiaBedrock()!=null&&garden.arkhaiaBedrock().ready())
            check(carrier!=null&&garden.arkhaiaBedrock().registered(carrier.getUniqueId()),id+": native Bedrock carrier registered");
        else if(index==0)lines.add("NOTE: Geyser entity API not ready in this trial; Bedrock carrier not exercised");
        garden.worldBoss().bossShowcaseMode(admin,"ultimate");
        later(3,()->{
            check(handler.getAnimation(10,id+"_ultimate_windup")!=null,id+": the pose menu switches to the ultimate windup");
            garden.worldBoss().bossShowcaseMode(admin,"attack");
            later(3,()->{
                check(handler.getAnimation(10,id+"_attack_windup")!=null,id+": then to the attack windup");
                check(garden.worldBoss().removeFactionPreviews(admin),id+": clear removes the showcase");
                later(3,()->{
                    check(!anchor.isValid()&&ModelEngineAPI.getModeledEntity(anchor.getUniqueId())==null&&tagged(Slime.class)==null&&tagged(ArmorStand.class)==null,
                        id+": anchor, carrier and rig are gone");
                    index++;next();
                });
            });
        });
    }
    void platform(int cx,int cz,int radius,int height){
        for(int x=cx-radius;x<=cx+radius;x++)for(int z=cz-radius;z<=cz+radius;z++){
            world.addPluginChunkTicket(x>>4,z>>4,this);world.getBlockAt(x,119,z).setType(Material.STONE,false);
            for(int y=120;y<120+height;y++)world.getBlockAt(x,y,z).setType(Material.AIR,false);
        }
    }
    ServerPlayer fake(Location at,String name){
        var server=MinecraftServer.getServer();var level=((CraftWorld)world).getHandle();
        var profile=new GameProfile(UUID.randomUUID(),name);var handle=new ServerPlayer(server,level,profile,ClientInformation.createDefault());
        var connection=new Connection(PacketFlow.SERVERBOUND);connection.channel=new EmbeddedChannel();connection.address=new InetSocketAddress("127.0.0.1",1);
        handle.connection=new ServerGamePacketListenerImpl(server,connection,handle,CommonListenerCookie.createInitial(profile,false)){
            @Override public boolean hasClientLoaded(){return true;}
        };
        handle.setPos(at.getX(),at.getY(),at.getZ());handle.setYRot(at.getYaw());
        server.getPlayerList().getPlayers().add(handle);server.getPlayerList().getPlayersByUUID().put(profile.id(),handle);level.addNewPlayer(handle);
        var bukkit=handle.getBukkitEntity();bukkit.setGravity(false);bukkit.setInvulnerable(true);bukkit.setGameMode(GameMode.CREATIVE);
        return handle;
    }
    void finish(Throwable error){
        if(finished)return;finished=true;
        if(error!=null){error.printStackTrace();lines.add("ERROR: "+error);}
        lines.add(0,(error==null?"PASS":"FAIL")+": realm-boss showcase / "+checks+" checks");
        try{Files.write(Path.of("model-engine-result.txt"),lines);}catch(Exception e){getLogger().warning(e.toString());}
        Bukkit.getScheduler().runTaskLater(this,Bukkit::shutdown,5);
    }
}
