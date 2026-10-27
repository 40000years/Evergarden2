import com.example.voidscape.VoidscapePlugin;
import com.example.voidscape.item.AdventurerWeaponService.AdventurerWeapon;
import com.example.voidscape.item.VaultLootTable;
import com.example.voidscape.item.VaultLootTable.WeaponReward;
import com.example.voidscape.world.DungeonLayout;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import com.example.voidscape.enchant.BossEnchantService;
import org.bukkit.plugin.java.JavaPlugin;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Production factories and tiered Vault list, running only in an isolated Paper server. */
public final class VaultWeaponChecks extends JavaPlugin {
    private VoidscapePlugin garden;
    private Object legendary;
    private Object staffs;
    private Method identifyLegendary;
    private Method isStaff;
    private int passed;
    private boolean withoutMagic;
    @Override public void onEnable(){Bukkit.getScheduler().runTaskLater(this,()->{
        try{run();finish(null);}catch(Throwable error){finish(error);}
    },20);}
    private void check(boolean value,String message){if(!value)throw new AssertionError(message);passed++;}
    private void finish(Throwable error){
        if(error!=null)getLogger().log(java.util.logging.Level.SEVERE,"VAULT WEAPON CHECK FAILED",error);
        String report=(error==null?"PASS ":"FAIL ")+passed+" checks; actual Paper tiered Vault; "+
            (withoutMagic?"Advance Magic disabled; ":"Rare and Legendary factories; ")+
            (error==null?"exact tier odds, all weapon/staff factories, early books and native survival anvil transactions":error.toString());
        try{Files.writeString(Path.of("vault-weapon-result.txt"),report);}catch(Exception failure){throw new RuntimeException(failure);}
        getLogger().info(report);Bukkit.getScheduler().runTaskLater(this,Bukkit::shutdown,1);
    }
    private void run()throws Exception {
        garden=(VoidscapePlugin)Bukkit.getPluginManager().getPlugin("Evergarden");
        withoutMagic=Boolean.getBoolean("vault.disabledMagic");
        check(garden!=null&&garden.isEnabled(),"Evergarden enabled");
        var magic=Bukkit.getPluginManager().getPlugin("advance-magic");
        check(magic!=null&&magic.isEnabled(),"Advance Magic initially enabled for existing Evergarden API references");
        if(withoutMagic)Bukkit.getPluginManager().disablePlugin(magic);
        check(withoutMagic?!magic.isEnabled():magic.isEnabled(),"requested Advance Magic availability");
        if(!withoutMagic){
            legendary=magic.getClass().getMethod("legendaryWeapons").invoke(magic);
            identifyLegendary=legendary.getClass().getMethod("identify",ItemStack.class);
            staffs=magic.getClass().getMethod("staffs").invoke(magic);
            isStaff=staffs.getClass().getMethod("isStaff",ItemStack.class);
            check(legendary!=null,"Legendary factory available");
        }
        exactTable();earlyTiers();factories();productionLists();lowerTierBalance();bookFactories();
        if(!withoutMagic)staffBonuses();
        nativeAnvils();
    }
    private static DungeonLayout.Site site(DungeonLayout.Kind kind,int tier){return new DungeonLayout.Site(kind,500,500,1L,tier);}
    private void exactTable(){
        for(int tier=1;tier<=5;tier++){
            int rare=0,legendaryCount=0,none=0;
            for(int ticket=0;ticket<10000;ticket++){
                WeaponReward reward=VaultLootTable.weaponReward(tier,ticket);
                int chance=tier==2?25:tier<3?0:(tier-2)*500;
                int legendaryChance=tier==2?0:chance/5;
                WeaponReward expected=ticket<legendaryChance?WeaponReward.LEGENDARY:ticket<chance?WeaponReward.RARE:WeaponReward.NONE;
                check(reward==expected,"tier "+tier+" ticket "+ticket);
                check(VaultLootTable.enchantBookReward(tier,ticket)==(ticket<(tier==1?3500:tier==2?4500:0)),"book rate T"+tier+" ticket "+ticket);
                switch(reward){case RARE->rare++;case LEGENDARY->legendaryCount++;case NONE->none++;}
            }
            int chance=tier==2?25:tier<3?0:(tier-2)*500, legends=tier==2?0:chance/5;
            check(rare==chance-legends&&legendaryCount==legends&&none==10000-chance,"exact weapon pool and overall odds Tier "+tier);
            check(VaultLootTable.staffChanceBps(tier)==new int[]{0,0,25,1000,1500,2000}[tier],"staff rate T"+tier);
        }
        for(int ticket:new int[]{-1,10000,Integer.MIN_VALUE,Integer.MAX_VALUE}){
            try{VaultLootTable.weaponReward(3,ticket);throw new AssertionError("invalid ticket accepted "+ticket);}
            catch(IllegalArgumentException expected){passed++;}
            try{VaultLootTable.enchantBookReward(1,ticket);throw new AssertionError("invalid book ticket accepted "+ticket);}
            catch(IllegalArgumentException expected){passed++;}
        }
    }
    private void earlyTiers(){
        Random noDraw=new Random(){@Override public int nextInt(int bound){throw new AssertionError("ineligible bonus consumed RNG");}
            @Override public double nextDouble(){throw new AssertionError("ineligible bonus consumed RNG");}};
        check(garden.relics().rollVaultWeaponBonus(null,noDraw)==null,"null site has no weapon bonus");
        check(garden.relics().rollVaultStaffBonus(null,noDraw)==null,"null site has no staff bonus");
        for(var kind:DungeonLayout.Kind.values()){
            check(garden.relics().rollVaultWeaponBonus(site(kind,1),noDraw)==null,"Tier 1 no weapon bonus for "+kind);
            check(garden.relics().rollVaultStaffBonus(site(kind,1),noDraw)==null,"Tier 1 no staff bonus for "+kind);
        }
    }
    private static final class Scripted extends Random {
        private final int[][] draws;private int index;
        Scripted(int[]...draws){this.draws=draws;}
        @Override public int nextInt(int bound){
            if(index>=draws.length)throw new AssertionError("unexpected RNG draw bound="+bound);
            int[] draw=draws[index++];if(draw[0]!=bound||draw[1]<0||draw[1]>=bound)
                throw new AssertionError("RNG expected "+Arrays.toString(draw)+" got bound="+bound);
            return draw[1];
        }
        @Override public double nextDouble(){throw new AssertionError("unexpected double draw");}
        void done(){if(index!=draws.length)throw new AssertionError("unused scripted draws "+(draws.length-index));}
    }
    private Object legendaryType(ItemStack item)throws Exception{return legendary==null?null:identifyLegendary.invoke(legendary,item);}
    private boolean weapon(ItemStack item)throws Exception{return garden.adventurerWeapons().identify(item)!=null||legendaryType(item)!=null;}
    private void completeItem(ItemStack item,NamespacedKey model,String label){
        check(item!=null&&item.getAmount()==1,label+" single item");
        var meta=item.getItemMeta();
        check(meta!=null&&model.equals(meta.getItemModel()),label+" correct item model");
        check(meta.hasDisplayName()&&!meta.getPersistentDataContainer().isEmpty(),label+" authenticated named item");
        if(item.getType()==org.bukkit.Material.BOW||item.getType()==org.bukkit.Material.CROSSBOW)
            check(meta.hasEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING),label+" ranged factory durability retained");
        else check(meta.getAttributeModifiers(Attribute.ATTACK_DAMAGE)!=null&&!meta.getAttributeModifiers(Attribute.ATTACK_DAMAGE).isEmpty(),label+" combat damage attributes retained");
    }
    private void factories()throws Exception {
        var enabled=AdventurerWeapon.enabledWeapons();
        check(enabled.size()==20,"20 enabled Rare weapons");
        check(enabled.contains(AdventurerWeapon.THORNWHISPER)&&enabled.contains(AdventurerWeapon.RUNELOCK),"restored ranged items remain in current release loot pool");
        Set<String> rareIds=new HashSet<>(),legendaryIds=new HashSet<>();
        String[] legendaryNames={"EXCALIBUR","LONGINUS","TYRFING","MJOLNIR","VAJRA"};
        for(var kind:DungeonLayout.Kind.values())for(int tier=2;tier<=5;tier++){
            int legendTickets=tier==2?0:(tier-2)*100;
            for(int index=0;index<enabled.size();index++){
                var expected=enabled.get(index);
                Scripted random=new Scripted(new int[]{10000,legendTickets},new int[]{100,99},new int[]{enabled.size(),index});
                ItemStack item=garden.relics().rollVaultWeaponBonus(site(kind,tier),random);random.done();
                check(garden.adventurerWeapons().identify(item)==expected,"Rare identity "+expected+" "+kind+" T"+tier);
                completeItem(item,new NamespacedKey("voidscape",expected.modelKey()),expected.id());rareIds.add(expected.id());
            }
            if(!withoutMagic&&tier>=3)for(int index=0;index<legendaryNames.length;index++){
                Scripted random=new Scripted(new int[]{10000,0},new int[]{5,index});
                ItemStack item=garden.relics().rollVaultWeaponBonus(site(kind,tier),random);random.done();
                Object actual=legendaryType(item);
                check(actual instanceof Enum<?> e&&e.name().equals(legendaryNames[index]),"Legendary identity "+legendaryNames[index]+" "+kind+" T"+tier);
                completeItem(item,new NamespacedKey("advance_magic","legendary_"+legendaryNames[index].toLowerCase(Locale.ROOT)),legendaryNames[index]);
                legendaryIds.add(legendaryNames[index]);
            }
            if(withoutMagic&&tier>=3){
                Scripted random=new Scripted(new int[]{10000,0},new int[]{100,99},new int[]{enabled.size(),0});
                ItemStack item=garden.relics().rollVaultWeaponBonus(site(kind,tier),random);random.done();
                check(garden.adventurerWeapons().identify(item)==enabled.getFirst(),"unavailable Legendary branch preserves drop with Rare fallback "+kind+" T"+tier);
            }
            Scripted miss=new Scripted(new int[]{10000,tier==2?25:(tier-2)*500});
            check(garden.relics().rollVaultWeaponBonus(site(kind,tier),miss)==null,"exact chance boundary misses "+kind+" T"+tier);miss.done();
        }
        check(rareIds.size()==enabled.size(),"all enabled Rare items obtainable");
        if(!withoutMagic)check(legendaryIds.size()==5,"all five Legendary items obtainable");
    }
    /** Force ordinary base rewards so the final bonus draw cannot be confused with earlier rolls. */
    private static final class VaultRandom extends Random {
        final int majorRolls,majorDraws,ticket;int largeDraws;
        VaultRandom(int tier,int ticket){majorRolls=tier==5?2:1;majorDraws=tier==2?2:majorRolls;this.ticket=ticket;}
        @Override public double nextDouble(){return .99;}
        @Override public boolean nextBoolean(){return false;}
        @Override public int nextInt(int bound){
            if(bound==10000){largeDraws++;return largeDraws==majorDraws+1?ticket:9999;}
            if(bound==9250)return 0; // Limit Break scroll, unchanged by the weapon addition.
            if(bound==100)return 99; // Non-themed base scroll, potion supply, non-themed Rare.
            return 0;
        }
    }
    private void productionLists()throws Exception {
        for(var kind:DungeonLayout.Kind.values())for(int tier=2;tier<=5;tier++){
            var site=site(kind,tier);int chance=tier==2?25:(tier-2)*500,legendChance=tier==2?0:chance/5;
            VaultRandom baselineRandom=new VaultRandom(tier,9999);
            List<ItemStack> baseline=garden.relics().rollVaultRewards(site,baselineRandom);
            check(baseline.size()==(tier==5?3:tier>=3?2:1),"base major and supply count "+kind+" T"+tier);
            int expectedDraws=baselineRandom.majorDraws+1+(withoutMagic?0:1);
            check(baselineRandom.largeDraws==expectedDraws,"one weapon and one available staff attempt on miss "+kind+" T"+tier);
            for(ItemStack item:baseline)check(!weapon(item),"ordinary rewards no longer leak modeled weapons "+kind+" T"+tier);
            for(int ticket:new int[]{0,Math.max(0,legendChance-1),legendChance,chance-1,chance,9999}){
                VaultRandom random=new VaultRandom(tier,ticket);
                List<ItemStack> actual=garden.relics().rollVaultRewards(site,random);
                boolean wins=ticket<chance;
                check(actual.size()==baseline.size()+(wins?1:0),"exactly one extra weapon or no extra "+kind+" T"+tier+" ticket "+ticket);
                check(random.largeDraws==expectedDraws,"one attempt per bonus after major rolls "+kind+" T"+tier+" ticket "+ticket);
                for(int index=0;index<baseline.size();index++)check(baseline.get(index).equals(actual.get(index)),"base reward preserved before appended weapon "+kind+" T"+tier+" slot "+index);
                int weapons=0;for(ItemStack item:actual)if(weapon(item))weapons++;
                check(weapons==(wins?1:0),"at most one modeled weapon for Vault opening "+kind+" T"+tier);
                if(wins){ItemStack last=actual.getLast();
                    boolean expectedLegend=!withoutMagic&&ticket<legendChance;
                    check(expectedLegend?legendaryType(last)!=null:garden.adventurerWeapons().identify(last)!=null,"correct bonus rarity at boundary "+kind+" T"+tier+" ticket "+ticket);
                }
            }
        }
    }
    private boolean anyStaff(ItemStack item){return item.hasItemMeta()&&item.getItemMeta().getPersistentDataContainer().getKeys().stream()
        .anyMatch(key->Set.of("staff_element","staff_kind","flying_staff").contains(key.getKey()));}
    private void lowerTierBalance()throws Exception {
        // Exercise each original category edge, both mythic branches and both book boundaries.
        for(var kind:DungeonLayout.Kind.values())for(int tier=1;tier<=2;tier++)
            for(int category:new int[]{0,1499,1500,2699,2700,3199,3200,9049,9050,9199,9200,9249})
            for(boolean book:new boolean[]{true,false})for(boolean mythic:new boolean[]{true,false}){
                final int t=tier;
                Random random=new Random(){int draws;
                    @Override public boolean nextBoolean(){return false;}
                    @Override public int nextInt(int bound){
                        if(bound==10000){draws++;if(draws==1)return (t==1?3500:4500)-(book?1:0);
                            if(t==2&&draws==2&&!book)return mythic?0:9999;return 9999;}
                        if(bound==9250)return category;
                        if(bound==100)return 99;
                        return 0;
                    }
                };
                var result=garden.relics().rollVaultRewards(site(kind,tier),random);
                check(result.size()==1,"early tier retains one major when bonuses miss");
                var item=result.getFirst();
                check((item.getType()==Material.ENCHANTED_BOOK)==book,"book boundary selects the right major T"+tier);
                check(!weapon(item)&&!anyStaff(item),"no hidden early weapon/staff path "+kind+" T"+tier+" category "+category);
            }
        Random preview=new Random(){@Override public int nextInt(int bound){return 0;}};
        check(garden.relics().rollVaultReward(preview).getType()==Material.ENCHANTED_BOOK,"untiered preview uses Tier 1 books");
    }
    private static final Enchantment[] BOOK_ENCHANTS={Enchantment.SHARPNESS,Enchantment.PROTECTION,Enchantment.LOOTING,
        Enchantment.FORTUNE,Enchantment.EFFICIENCY,Enchantment.UNBREAKING};
    private ItemStack book(int index,boolean upper){
        Scripted random=new Scripted(new int[]{6,index},new int[]{100,upper?0:99});
        ItemStack result=garden.relics().createVaultEnchantBook(2,random);random.done();return result;
    }
    private int bookLevel(int index,boolean upper){return (index==0||index==1||index==4?6:4)+(upper?1:0);}
    private void bookFactories(){
        for(int index=0;index<6;index++){
            Scripted lowerRandom=new Scripted(new int[]{6,index});
            var lower=garden.relics().createVaultEnchantBook(1,lowerRandom);lowerRandom.done();
            check(BossEnchantService.enchants(lower).equals(Map.of(BOOK_ENCHANTS[index],bookLevel(index,false))),"T1 lower level only "+index);
            for(boolean upper:new boolean[]{false,true}){
                var item=book(index,upper);var enchant=BOOK_ENCHANTS[index];int level=bookLevel(index,upper);
                check(item.getType()==Material.ENCHANTED_BOOK&&item.getAmount()==1,"single native enchant book");
                check(item.getItemMeta() instanceof EnchantmentStorageMeta,"stored enchant metadata");
                check(BossEnchantService.enchants(item).equals(Map.of(enchant,level)),"exactly the awarded enchant and level");
                check(garden.bossEnchants().authorized(item).equals(Map.of(enchant,level)),"factory grants scoped anvil entitlement");
                var restored=ItemStack.deserializeBytes(item.serializeAsBytes());
                check(garden.bossEnchants().authorized(restored).equals(Map.of(enchant,level)),"book entitlement survives serialization");
            }
            for(int draw:new int[]{29,30}){
                Scripted random=new Scripted(new int[]{6,index},new int[]{100,draw});
                check(BossEnchantService.enchants(garden.relics().createVaultEnchantBook(2,random)).get(BOOK_ENCHANTS[index])==bookLevel(index,draw==29),"30% upper-level boundary");random.done();
            }
        }
    }
    private void staffBonuses()throws Exception {
        for(var kind:DungeonLayout.Kind.values())for(int tier=2;tier<=5;tier++){
            int chance=VaultLootTable.staffChanceBps(tier);
            for(int ticket:new int[]{0,chance-1,chance,9999}){
                if(ticket<chance)for(int element=0;element<8;element++){
                    Scripted random=new Scripted(new int[]{10000,ticket},new int[]{8,element});
                    var item=garden.relics().rollVaultStaffBonus(site(kind,tier),random);random.done();
                    check(item!=null&&Boolean.TRUE.equals(isStaff.invoke(staffs,item)),"staff factory succeeds "+kind+" T"+tier);
                    check(item.getItemMeta().getItemModel().getKey().equals("staff_"+new String[]{"fire","wind","earth","ice","water","lightning","light","dark"}[element]),"uniform elemental selection");
                }else{
                    Scripted random=new Scripted(new int[]{10000,ticket});
                    check(garden.relics().rollVaultStaffBonus(site(kind,tier),random)==null,"staff miss boundary "+kind+" T"+tier);random.done();
                }
            }
            final int currentTier=tier;
            class BothBonusRandom extends Random {
                int draws;final int majors=currentTier==5?2:1;final int baseDraws=currentTier==2?2:majors;
                @Override public int nextInt(int bound){
                    if(bound==10000){draws++;if(draws<=baseDraws)return 9999;return draws==baseDraws+1?(currentTier==2?0:(currentTier-2)*100):0;}
                    if(bound==9250)return 0;if(bound==100)return 99;return 0;
                }
            }
            BothBonusRandom random=new BothBonusRandom();var items=garden.relics().rollVaultRewards(site(kind,tier),random);
            check(items.size()==(tier==5?5:tier>=3?4:3),"base majors, supply, weapon and staff all delivered");
            check(random.draws==random.baseDraws+2,"one draw per bonus even for Astral or double major");
            check(garden.adventurerWeapons().identify(items.get(items.size()-2))!=null&&anyStaff(items.getLast()),"weapon and staff coexist without replacing majors");
        }
        garden.getConfig().set("staff-loot.enabled",false);
        try{
            Random noDraw=new Random(){@Override public int nextInt(int bound){throw new AssertionError("disabled staff loot consumed RNG");}};
            check(garden.relics().rollVaultStaffBonus(site(DungeonLayout.Kind.SANCTUM_ASTRAL,5),noDraw)==null,"staff loot master switch retained");
        }finally{garden.getConfig().set("staff-loot.enabled",true);}
    }
    private net.minecraft.server.level.ServerPlayer actor;
    private net.minecraft.server.level.ServerLevel level;
    private net.minecraft.world.inventory.AnvilMenu menu(ItemStack left,ItemStack right){
        var menu=new net.minecraft.world.inventory.AnvilMenu(1,actor.getInventory(),net.minecraft.world.inventory.ContainerLevelAccess.create(level,new net.minecraft.core.BlockPos(2,100,0)));
        menu.checkReachable=false;actor.containerMenu=menu;
        menu.getSlot(0).set(org.bukkit.craftbukkit.inventory.CraftItemStack.asNMSCopy(left));
        menu.getSlot(1).set(org.bukkit.craftbukkit.inventory.CraftItemStack.asNMSCopy(right));
        menu.slotsChanged(menu.getSlot(0).container);return menu;
    }
    private ItemStack result(net.minecraft.world.inventory.AnvilMenu menu){var raw=menu.getSlot(2).getItem();return raw.isEmpty()?null:org.bukkit.craftbukkit.inventory.CraftItemStack.asBukkitCopy(raw);}
    private void nativeAnvils(){
        check(garden.bossEnchants().anvilEnabled(),"existing mechanics.anvil-transfer defaults enabled");
        var server=net.minecraft.server.MinecraftServer.getServer();var world=org.bukkit.Bukkit.getWorlds().getFirst();
        level=((org.bukkit.craftbukkit.CraftWorld)world).getHandle();
        var profile=new com.mojang.authlib.GameProfile(UUID.randomUUID(),"VaultBookTester");
        actor=new net.minecraft.server.level.ServerPlayer(server,level,profile,net.minecraft.server.level.ClientInformation.createDefault());
        var connection=new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        connection.channel=new io.netty.channel.embedded.EmbeddedChannel();connection.address=new java.net.InetSocketAddress("127.0.0.1",1);
        actor.connection=new net.minecraft.server.network.ServerGamePacketListenerImpl(server,connection,actor,
                net.minecraft.server.network.CommonListenerCookie.createInitial(profile,false)){
            @Override public boolean hasClientLoaded(){return true;}
        };
        actor.setPos(.5,100,.5);server.getPlayerList().getPlayers().add(actor);server.getPlayerList().getPlayersByUUID().put(profile.id(),actor);level.addNewPlayer(actor);
        var player=actor.getBukkitEntity();player.setGravity(false);player.setGameMode(org.bukkit.GameMode.SURVIVAL);player.setLevel(1000);
        world.getBlockAt(0,99,0).setType(Material.STONE);world.getBlockAt(2,100,0).setType(Material.ANVIL);
        Material[] targets={Material.DIAMOND_SWORD,Material.DIAMOND_CHESTPLATE,Material.DIAMOND_SWORD,Material.DIAMOND_PICKAXE,Material.DIAMOND_PICKAXE,Material.DIAMOND_PICKAXE};
        try{
            for(int index=0;index<6;index++)for(boolean upper:new boolean[]{false,true}){
                var book=book(index,upper);var enchant=BOOK_ENCHANTS[index];int n=bookLevel(index,upper);
                var menu=menu(new ItemStack(targets[index]),book);var output=result(menu);
                check(output!=null&&BossEnchantService.enchants(output).get(enchant)==n,"native anvil transfers actual Vault book "+enchant.getKey()+" "+n);
                check(garden.bossEnchants().authorized(output).get(enchant)==n,"native result retains entitlement");
                check(!menu.bypassEnchantmentLevelRestriction,"scoped anvil bypass reset");
                int xp=player.getLevel(),cost=menu.getCost();check(cost>0&&cost<40,"survival XP cost payable");
                menu.clicked(2,0,net.minecraft.world.inventory.ContainerInput.PICKUP,actor);
                check(player.getLevel()==xp-cost&&menu.getSlot(0).getItem().isEmpty()&&menu.getSlot(1).getItem().isEmpty(),"real survival click consumes both inputs and pays XP");
                check(BossEnchantService.enchants(org.bukkit.craftbukkit.inventory.CraftItemStack.asBukkitCopy(menu.getCarried())).get(enchant)==n,"survival click delivers full level");
                menu.clicked(2,0,net.minecraft.world.inventory.ContainerInput.PICKUP,actor);check(player.getLevel()==xp-cost,"cannot replay result payment");
                menu.setCarried(net.minecraft.world.item.ItemStack.EMPTY);
                check(BossEnchantService.enchants(result(menu(output,book))).get(enchant)==n,"equal high-level merge cannot grow enchant");
                check(BossEnchantService.enchants(result(menu(book,book))).get(enchant)==n,"book plus book cannot grow enchant");
            }
            check(result(menu(new ItemStack(Material.DIAMOND_SWORD),book(1,true)))==null,"Protection book remains incompatible with swords");
            var fortune=result(menu(new ItemStack(Material.DIAMOND_PICKAXE),book(3,true)));
            var silk=new ItemStack(Material.ENCHANTED_BOOK);var meta=(EnchantmentStorageMeta)silk.getItemMeta();meta.addStoredEnchant(Enchantment.SILK_TOUCH,1,true);silk.setItemMeta(meta);
            check(result(menu(fortune,silk))==null,"Fortune remains incompatible with Silk Touch");
        }finally{
            garden.bossEnchantEffects().clear(player);actor.discard();server.getPlayerList().getPlayers().remove(actor);server.getPlayerList().getPlayersByUUID().remove(profile.id());
        }
    }
}
