import com.example.advancemagic.AdvanceMagicPlugin;
import com.example.advancemagic.elemental.ElementalSpell;
import com.example.advancemagic.elemental.ElementalSpell.Rarity;
import com.example.advancemagic.elemental.SpellLevels;
import com.example.advancemagic.elemental.StaffSpellService;
import com.example.advancemagic.elemental.StaffSpellService.Tier;
import com.example.advancemagic.item.ElementalStaffService.Element;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.Connection;
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
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import java.nio.file.*;
import java.util.*;

/** Spell levels I–VII on actual Paper: storage, rarity gating, imprint upgrades, fusion, casting and loot. */
public final class SpellLevelChecks extends JavaPlugin implements Listener {
    int passed;
    AdvanceMagicPlugin magic;
    StaffSpellService service;
    World world;
    Player caster;
    ServerPlayer handle;
    final Map<UUID,Double> damage=new HashMap<>();
    final List<LivingEntity> mobs=new ArrayList<>();

    void check(boolean condition,String label){if(!condition)throw new AssertionError(label);passed++;getLogger().info("PASS "+label);}
    void near(double actual,double expected,double tolerance,String label){check(Math.abs(actual-expected)<=tolerance,label+" actual="+actual+" expected="+expected);}

    @Override public void onEnable(){
        Bukkit.getPluginManager().registerEvents(this,this);
        Bukkit.getScheduler().runTaskLater(this,()->{try{run();gui();}catch(Throwable error){finish(error);}},20);
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void damage(EntityDamageByEntityEvent event){damage.merge(event.getEntity().getUniqueId(),event.getDamage(),Double::sum);}

    Player actor(String name,Location at){
        var server=MinecraftServer.getServer();var level=((CraftWorld)world).getHandle();
        var profile=new GameProfile(UUID.randomUUID(),name);handle=new ServerPlayer(server,level,profile,ClientInformation.createDefault());
        var connection=new Connection(PacketFlow.SERVERBOUND);connection.channel=new EmbeddedChannel();connection.address=new java.net.InetSocketAddress("127.0.0.1",1);
        handle.connection=new ServerGamePacketListenerImpl(server,connection,handle,CommonListenerCookie.createInitial(profile,false)){@Override public boolean hasClientLoaded(){return true;}};
        handle.setPos(at.getX(),at.getY(),at.getZ());server.getPlayerList().getPlayers().add(handle);server.getPlayerList().getPlayersByUUID().put(profile.id(),handle);level.addNewPlayer(handle);
        Player player=handle.getBukkitEntity();player.setGameMode(GameMode.SURVIVAL);player.setGravity(false);player.teleport(at);
        player.getAttribute(Attribute.MAX_HEALTH).setBaseValue(1000);player.setHealth(1000);return player;
    }
    LivingEntity mob(double x,double z){
        var mob=world.spawn(new Location(world,x,100,z),Vindicator.class);mob.setAI(false);mob.setGravity(false);
        mob.getAttribute(Attribute.MAX_HEALTH).setBaseValue(1000);mob.setHealth(1000);mob.setMaximumNoDamageTicks(0);mobs.add(mob);return mob;
    }
    void ticks(int count){for(int i=0;i<count;i++){magic.effects().tick();magic.elemental().tick();}}
    void reset(){
        magic.elemental().close();magic.effects().close();
        for(LivingEntity mob:mobs)mob.remove();mobs.clear();damage.clear();
        caster.closeInventory();caster.getInventory().clear();caster.teleport(new Location(world,.5,100,.5,0,0));
        var account=magic.mana().account(caster);account.setMaxMana(200);account.setMana(200);
        for(var spell:ElementalSpell.values())account.restore("elemental_"+spell.id(),0);
        for(String name:List.of("elemental_gcd","elemental_ultimate","elemental_regen_after"))caster.getPersistentDataContainer().remove(magic.mana().key(name));
    }
    ItemStack held(){return caster.getInventory().getItemInMainHand();}
    /** A staff holding one spell at a level, imprinted through the real service. */
    void staff(Tier tier,Element element,ElementalSpell spell,int level){
        caster.getInventory().clear();caster.getInventory().setHeldItemSlot(0);
        caster.getInventory().setItem(0,tier.wooden()?service.createWoodenStaff(tier):service.createStaff(element,tier));
        caster.getInventory().setItem(1,service.createScroll(spell,level));
        check(service.imprint(caster,0,held().clone(),1,caster.getInventory().getItem(1).clone(),0).success(),"imprint "+SpellLevels.label(spell,level)+" on "+tier);
    }
    int count(ElementalSpell spell,int level){
        int total=0;for(int slot=0;slot<36;slot++){ItemStack item=caster.getInventory().getItem(slot);if(service.scroll(item)==spell&&service.scrollLevel(item)==level)total+=item.getAmount();}return total;
    }

    void run(){
        magic=(AdvanceMagicPlugin)Bukkit.getPluginManager().getPlugin("advance-magic");
        check(magic!=null&&magic.isEnabled(),"production plugin boots");
        service=magic.staffSpells();world=Bukkit.getWorlds().getFirst();
        for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)world.getChunkAt(x,z).setForceLoaded(true);
        for(int x=-16;x<=16;x++)for(int z=-16;z<=16;z++)world.getBlockAt(x,99,z).setType(Material.STONE);
        caster=actor("LevelCaster",new Location(world,.5,100,.5,0,0));
        formulas();storage();gating();upgrades();fusion();casting();loot();
    }

    void formulas(){
        near(SpellLevels.damage(5),1,1e-9,"level V is today's damage");
        near(SpellLevels.damage(4),.8,1e-9,"one level down deals ×0.8");
        near(SpellLevels.damage(1),.4096,1e-9,"level I deals 0.8^4");
        near(SpellLevels.damage(7),1.5625,1e-9,"level VII deals 1.25^2");
        near(SpellLevels.cooldown(5),1,1e-9,"level V is today's cooldown");
        near(SpellLevels.cooldown(7),2/3.0,1e-9,"level VII cooldown is 1/1.5 of today's");
        near(SpellLevels.cooldown(1),5/3.0,1e-9,"level I cooldown is 2.5/1.5 of today's");
        for(int level=1;level<7;level++)near(SpellLevels.cooldown(level)-SpellLevels.cooldown(level+1),.25*SpellLevels.cooldown(7),1e-9,"each level below VII adds +0.25 of the VII cooldown ("+level+")");
        near(SpellLevels.mana(1),.6,1e-9,"level I mana ×0.6");near(SpellLevels.mana(7),1.2,1e-9,"level VII mana ×1.2");
        int leveled=0;
        for(ElementalSpell spell:ElementalSpell.values()){
            if(spell.ultimate()){
                check(!SpellLevels.leveled(spell)&&SpellLevels.rarity(spell,1)==spell.rarity()&&SpellLevels.damageFactor(spell,7)==1,"Ultimate "+spell.id()+" has a single level");
                continue;
            }
            leveled++;
            check(SpellLevels.rarity(spell,7)==Rarity.EPIC,"level VII of "+spell.id()+" is Epic");
            check(SpellLevels.rarity(spell,5)==spell.rarity(),"level V of "+spell.id()+" keeps its published rarity "+spell.rarity());
            for(int level=1;level<7;level++)check(SpellLevels.rarity(spell,level).ordinal()<=SpellLevels.rarity(spell,level+1).ordinal(),"rarity never drops as "+spell.id()+" levels up");
            check(SpellLevels.mana(spell,5)==spell.mana()&&SpellLevels.cooldown(spell,5)==spell.cooldown()&&SpellLevels.multiplier(spell,5)==spell.multiplier(),"level V of "+spell.id()+" equals the published spell");
        }
        check(leveled==48,"48 non-Ultimate spells have seven levels ("+leveled+")");
    }

    void storage(){
        reset();
        ItemStack seven=service.createScroll(ElementalSpell.FIREBALL,7);
        check(service.scroll(seven)==ElementalSpell.FIREBALL&&service.scrollLevel(seven)==7,"scroll stores its level");
        check(seven.getItemMeta().getDisplayName().contains("VII")&&seven.getType()==Material.PAPER,"scroll name shows level VII on the same paper item");
        check(Objects.equals(seven.getItemMeta().getItemModel(),service.createScroll(ElementalSpell.FIREBALL,1).getItemMeta().getItemModel()),"every level shares the spell's existing scroll texture");
        check(service.createScroll(ElementalSpell.FIREBALL).isSimilar(service.createScroll(ElementalSpell.FIREBALL,5)),"plain createScroll is level V (shop and vault drops)");
        check(!service.createScroll(ElementalSpell.FIREBALL,3).isSimilar(service.createScroll(ElementalSpell.FIREBALL,4)),"different levels never stack");
        // A scroll from before levels: no level key and the old lore.
        ItemStack legacy=service.createScroll(ElementalSpell.FROST_BOLT,5);var meta=legacy.getItemMeta();
        meta.getPersistentDataContainer().remove(new NamespacedKey("advance_magic","spell_level"));meta.setLore(List.of("old lore"));legacy.setItemMeta(meta);legacy.setAmount(7);
        check(service.scroll(legacy)==ElementalSpell.FROST_BOLT&&service.scrollLevel(legacy)==5,"a scroll from before levels reads as level V");
        caster.getInventory().setItem(3,legacy);service.discover(caster);
        ItemStack migrated=caster.getInventory().getItem(3);
        check(migrated.isSimilar(service.createScroll(ElementalSpell.FROST_BOLT,5))&&migrated.getAmount()==7,"old scrolls are rewritten as stackable level V on join/menu");
        check(service.scrollIcon(ElementalSpell.FIREBALL,7).getItemMeta().getPersistentDataContainer().getKeys().stream().noneMatch(key->key.getKey().startsWith("spell_")),"menu icons carry no scroll data");
        // A staff written before levels.
        staff(Tier.MYTHIC,Element.FIRE,ElementalSpell.FIREBALL,5);
        String raw=held().getItemMeta().getPersistentDataContainer().get(new NamespacedKey("advance_magic","staff_spells"),PersistentDataType.STRING);
        check("fireball".equals(raw),"level V stays a bare id, so existing staffs keep identical data ("+raw+")");
        staff(Tier.MYTHIC,Element.FIRE,ElementalSpell.FIREBALL,7);
        raw=held().getItemMeta().getPersistentDataContainer().get(new NamespacedKey("advance_magic","staff_spells"),PersistentDataType.STRING);
        check("fireball@7".equals(raw)&&service.level(held(),ElementalSpell.FIREBALL)==7,"other levels are stored as id@level ("+raw+")");
        check(String.join("\n",held().getItemMeta().getLore()).contains("Fireball VII"),"staff lore names the level");
        ItemStack copy=ItemStack.deserializeBytes(held().serializeAsBytes());
        check(service.level(copy,ElementalSpell.FIREBALL)==7,"level survives item serialization");
    }

    void gating(){
        reset();
        caster.getInventory().setItem(0,service.createWoodenStaff(Tier.WOODEN));caster.getInventory().setHeldItemSlot(0);
        caster.getInventory().setItem(1,service.createScroll(ElementalSpell.FIREBALL,7));
        check(!service.imprint(caster,0,held().clone(),1,caster.getInventory().getItem(1).clone(),0).success(),"wooden staff refuses an Epic level VII");
        caster.getInventory().setItem(1,service.createScroll(ElementalSpell.FIREBALL,6));
        check(service.imprint(caster,0,held().clone(),1,caster.getInventory().getItem(1).clone(),0).success(),"wooden staff takes a Rare level VI");
        caster.getInventory().setItem(0,service.createWoodenStaff(Tier.WOODEN));caster.getInventory().setItem(1,service.createScroll(ElementalSpell.MAGMA_ERUPTION,4));
        check(service.imprint(caster,0,held().clone(),1,caster.getInventory().getItem(1).clone(),0).success(),"a weakened Epic spell (IV, Rare) fits a wooden staff");
        caster.getInventory().setItem(0,service.createWoodenStaff(Tier.WOODEN));caster.getInventory().setItem(1,service.createScroll(ElementalSpell.MAGMA_ERUPTION,5));
        check(!service.imprint(caster,0,held().clone(),1,caster.getInventory().getItem(1).clone(),0).success(),"Magma Eruption V stays Epic, as before levels");
        caster.getInventory().setItem(0,service.createStaff(Element.FIRE,Tier.ARCANE));caster.getInventory().setItem(1,service.createScroll(ElementalSpell.SCORCHING_VORTEX,7));
        check(service.imprint(caster,0,held().clone(),1,caster.getInventory().getItem(1).clone(),0).success(),"an Arcane staff takes level VII");
    }

    void upgrades(){
        reset();staff(Tier.MYTHIC,Element.FIRE,ElementalSpell.FIREBALL,3);
        caster.getInventory().setItem(1,service.createScroll(ElementalSpell.FIREBALL,5));
        check(!service.imprint(caster,0,held().clone(),1,caster.getInventory().getItem(1).clone(),1).success(),"a second copy of the same spell is never added");
        var result=service.swap(caster,0,held().clone(),1,caster.getInventory().getItem(1).clone(),0);
        check(result.success()&&service.level(held(),ElementalSpell.FIREBALL)==5&&service.spells(held()).equals(List.of(ElementalSpell.FIREBALL)),"a higher level replaces the imprinted spell in place: "+result.message());
        check(count(ElementalSpell.FIREBALL,3)==1&&count(ElementalSpell.FIREBALL,5)==0,"the old level III comes back as a scroll and the V scroll is used");
        caster.getInventory().setItem(2,service.createScroll(ElementalSpell.FIREBALL,4));
        check(!service.swap(caster,0,held().clone(),2,caster.getInventory().getItem(2).clone(),0).success()&&service.level(held(),ElementalSpell.FIREBALL)==5,"a lower level cannot replace a higher one");
        var extracted=service.extract(caster,0,held().clone(),0);
        check(extracted.success()&&count(ElementalSpell.FIREBALL,5)==1&&service.spells(held()).isEmpty(),"extracting returns the spell at its level");
    }

    void fusion(){
        reset();
        ItemStack two=service.createScroll(ElementalSpell.WIND_CUTTER,2);two.setAmount(5);caster.getInventory().setItem(4,two);
        check(service.fuse(caster,ElementalSpell.WIND_CUTTER,2,1).success()&&count(ElementalSpell.WIND_CUTTER,2)==3&&count(ElementalSpell.WIND_CUTTER,3)==1,"fusing once: 2 × II → 1 × III");
        check(service.fuse(caster,ElementalSpell.WIND_CUTTER,2,99).success()&&count(ElementalSpell.WIND_CUTTER,2)==1&&count(ElementalSpell.WIND_CUTTER,3)==2,"fuse all uses every pair and leaves the odd one");
        check(!service.fuse(caster,ElementalSpell.WIND_CUTTER,2,1).success()&&count(ElementalSpell.WIND_CUTTER,2)==1,"one scroll cannot fuse");
        ItemStack sevens=service.createScroll(ElementalSpell.WIND_CUTTER,7);sevens.setAmount(2);caster.getInventory().setItem(5,sevens);
        check(!service.fuse(caster,ElementalSpell.WIND_CUTTER,7,1).success()&&count(ElementalSpell.WIND_CUTTER,7)==2,"level VII is the top");
        ItemStack ultimate=service.createScroll(ElementalSpell.METEOR_RAIN);ultimate.setAmount(2);caster.getInventory().setItem(6,ultimate);
        check(!service.fuse(caster,ElementalSpell.METEOR_RAIN,5,1).success(),"Ultimates do not fuse");
        // Full inventory: the two inputs share one stack and the result needs its own slot.
        reset();
        for(int slot=0;slot<36;slot++)caster.getInventory().setItem(slot,new ItemStack(Material.COBBLESTONE,64));
        ItemStack pair=service.createScroll(ElementalSpell.AQUA_BULLET,1);pair.setAmount(3);caster.getInventory().setItem(10,pair);
        check(!service.fuse(caster,ElementalSpell.AQUA_BULLET,1,1).success()&&count(ElementalSpell.AQUA_BULLET,1)==3,"no room for the result changes nothing");
        pair.setAmount(2);caster.getInventory().setItem(10,pair);
        check(service.fuse(caster,ElementalSpell.AQUA_BULLET,1,1).success()&&count(ElementalSpell.AQUA_BULLET,2)==1,"the slot freed by the inputs takes the result");
    }

    /** Casts Flame Burst at a level and returns {damage, mana spent, cooldown ms}. */
    double[] cast(int level){
        reset();staff(Tier.MYTHIC,Element.FIRE,ElementalSpell.FLAME_BURST,level);caster.getInventory().setItem(1,null);
        LivingEntity target=mob(2.5,.5);
        double before=magic.mana().account(caster).manaExact();
        check(magic.elemental().cast(caster),"cast Flame Burst "+SpellLevels.roman(level));
        long cooldown=magic.mana().elementalRemaining(caster,ElementalSpell.FLAME_BURST,System.currentTimeMillis());
        ticks(40);
        return new double[]{damage.getOrDefault(target.getUniqueId(),0.0),before-magic.mana().account(caster).manaExact(),cooldown};
    }
    void casting(){
        double[] one=cast(1),five=cast(5),seven=cast(7);
        check(five[0]>0,"Flame Burst V damages its target ("+five[0]+")");
        near(one[0]/five[0],SpellLevels.damage(1),.02,"level I deals 0.41 of level V in a real cast");
        near(seven[0]/five[0],SpellLevels.damage(7),.02,"level VII deals 1.56 of level V in a real cast");
        near(five[1],ElementalSpell.FLAME_BURST.mana(),.001,"level V costs the published mana");
        near(one[1],SpellLevels.mana(ElementalSpell.FLAME_BURST,1),.001,"level I costs less mana");
        near(seven[1],SpellLevels.mana(ElementalSpell.FLAME_BURST,7),.001,"level VII costs more mana");
        // Remaining cooldown is read a few wall-clock milliseconds after the cast.
        double base=ElementalSpell.FLAME_BURST.cooldown()*1000;
        near(five[2],base,300,"level V keeps the published cooldown");
        near(one[2],base*SpellLevels.cooldown(1),300,"level I cooldown is longer");
        near(seven[2],base*SpellLevels.cooldown(7),300,"level VII cooldown is shorter");
    }

    void loot(){
        var acquisition=magic.acquisition();Random random=new Random(26);
        Map<String,double[]> stats=new LinkedHashMap<>();
        for(String profile:List.of("exploration","landmark","boss")){
            int[] counts=new int[8];int n=20000;
            for(int i=0;i<n;i++)counts[acquisition.rollLevel(profile,random)]++;
            double mean=0;for(int l=1;l<=7;l++)mean+=l*counts[l]/(double)n;
            stats.put(profile,new double[]{mean,counts[1],counts[7]});
        }
        check(stats.get("exploration")[0]<2.8,"ordinary structures mostly give low levels (mean "+stats.get("exploration")[0]+")");
        check(stats.get("landmark")[0]>stats.get("exploration")[0]&&stats.get("boss")[0]>stats.get("landmark")[0],"landmarks, then bosses and sanctuaries, give higher levels");
        check(stats.get("boss")[1]==0&&stats.get("boss")[2]>0,"bosses never give level I and can give VII");
        int leveledScrolls=0,levels=0;
        for(int i=0;i<4000;i++){
            ItemStack scroll=acquisition.rollScroll("boss",random);if(scroll==null)continue;
            ElementalSpell spell=service.scroll(scroll);check(spell!=null,"boss chest roll yields a real scroll");
            if(SpellLevels.leveled(spell)){leveledScrolls++;levels|=1<<service.scrollLevel(scroll);}
        }
        check(leveledScrolls>1000&&(levels&(1<<7))!=0&&(levels&(1<<2))!=0,"chest scrolls carry rolled levels");
    }

    void gui(){
        reset();
        ItemStack three=service.createScroll(ElementalSpell.SPARK_SHOT,3);three.setAmount(4);caster.getInventory().setItem(9,three);
        magic.getCommand("spell").execute(caster,"spell",new String[]{"fuse"});
        Inventory top=caster.getOpenInventory().getTopInventory();
        check(top.getHolder()!=null&&top.getHolder().getClass().getSimpleName().equals("Fusion")&&top.getSize()==54,"/spell fuse opens the fusion altar without a staff");
        check(new NamespacedKey("advance_magic","menu_upgrade").equals(top.getItem(22).getItemMeta().getItemModel()),"the fuse button is ready with a fusable pair selected");
        check(top.getItem(13).getItemMeta().getDisplayName().contains("Spark Shot IV"),"the altar previews the next level");
        check(new NamespacedKey("advance_magic","skilltree_level_3").equals(top.getItem(29).getItemMeta().getItemModel())
            &&new NamespacedKey("advance_magic","skilltree_level_4").equals(top.getItem(12).getItemMeta().getItemModel()),"level badges show III → IV");
        InventoryView view=caster.getOpenInventory();
        Bukkit.getPluginManager().callEvent(new InventoryClickEvent(view,InventoryType.SlotType.CONTAINER,22,ClickType.SHIFT_LEFT,InventoryAction.MOVE_TO_OTHER_INVENTORY));
        Bukkit.getScheduler().runTaskLater(this,()->{try{
            check(count(ElementalSpell.SPARK_SHOT,3)==0&&count(ElementalSpell.SPARK_SHOT,4)==2,"shift-clicking the altar fuses every pair (4 × III → 2 × IV)");
            Inventory after=caster.getOpenInventory().getTopInventory();
            check(after.getItem(13).getItemMeta().getDisplayName().contains("Spark Shot V"),"the altar follows the new scroll to the next step");
            caster.closeInventory();
            staff(Tier.MYTHIC,Element.LIGHTNING,ElementalSpell.SPARK_SHOT,2);
            ItemStack four=service.createScroll(ElementalSpell.SPARK_SHOT,4);caster.getInventory().setItem(5,four);
            magic.getCommand("spell").execute(caster,"spell",new String[]{});
            Inventory bench=caster.getOpenInventory().getTopInventory();
            check(bench.getHolder()!=null&&bench.getHolder().getClass().getSimpleName().equals("Workbench"),"/spell opens the staff workbench");
            check(new NamespacedKey("advance_magic","menu_swap").equals(bench.getItem(2).getItemMeta().getItemModel()),"the workbench has a fusion button");
            String entry=String.join("\n",bench.getItem(37).getItemMeta().getLore());
            check(entry.contains("อัประดับ"),"a higher level of the imprinted spell is offered as an upgrade");
            finish(null);
        }catch(Throwable error){finish(error);}},3);
    }

    void finish(Throwable error){
        if(error!=null)getLogger().log(java.util.logging.Level.SEVERE,"SPELL LEVELS FAILED after "+passed,error);
        try{if(magic!=null){magic.elemental().close();magic.effects().close();}}catch(Throwable ignored){}
        for(LivingEntity mob:mobs)mob.remove();
        try{Files.writeString(Path.of("spell-level-result.txt"),(error==null?"PASS "+passed:"FAIL after "+passed+": "+error)+"\n");}catch(Exception ignored){}
        Bukkit.getScheduler().runTaskLater(this,Bukkit::shutdown,2);
    }
}
