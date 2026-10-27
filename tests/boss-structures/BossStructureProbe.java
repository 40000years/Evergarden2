import com.example.voidscape.VoidscapePlugin;
import com.example.voidscape.world.*;
import com.example.advancemagic.AdvanceMagicPlugin;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.server.network.*;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.entity.Player;
import org.bukkit.event.block.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import java.net.InetSocketAddress;
import java.nio.file.*;
import java.util.*;

/** Real Paper generation, rewards, protection, traps and a second boot. Never shipped. */
public final class BossStructureProbe extends JavaPlugin {
    VoidscapePlugin garden;World world;Player player;ServerPlayer actor;
    final List<String> checks=new ArrayList<>();
    void check(boolean ok,String label){if(!ok)throw new AssertionError(label);checks.add(label);getLogger().info("PASS: "+label);}
    @Override public void onEnable(){Bukkit.getScheduler().runTaskLater(this,()->{
        String report="PASS";
        try{
            garden=(VoidscapePlugin)Bukkit.getPluginManager().getPlugin("Evergarden");
            check(garden!=null&&garden.isEnabled(),"release boots with three new structures");world=garden.world();
            world.setGameRule(GameRule.RANDOM_TICK_SPEED,0);
            if(Files.exists(Path.of("phase-one-sites.txt")))restart();else first();
        }catch(Throwable error){report="FAIL "+error;getLogger().log(java.util.logging.Level.SEVERE,"STRUCTURE PROBE",error);}
        finally{
            if(actor!=null){actor.discard();var list=MinecraftServer.getServer().getPlayerList();list.getPlayers().remove(actor);list.getPlayersByUUID().remove(actor.getUUID());}
            try{Files.writeString(Path.of("boss-structure-result.txt"),report+"\n"+String.join("\n",checks));}catch(Exception ignored){}
            // Let this deliberately heavy probe tick complete before stopping Paper.
            Bukkit.getScheduler().runTaskLater(this,Bukkit::shutdown,3);
        }
    },20);}
    void createPlayer(){
        var server=MinecraftServer.getServer();var level=((CraftWorld)world).getHandle();
        var profile=new GameProfile(UUID.randomUUID(),"StructureTester");actor=new ServerPlayer(server,level,profile,ClientInformation.createDefault());
        var connection=new Connection(PacketFlow.SERVERBOUND);connection.channel=new EmbeddedChannel();connection.address=new InetSocketAddress("127.0.0.1",1);
        actor.connection=new ServerGamePacketListenerImpl(server,connection,actor,CommonListenerCookie.createInitial(profile,false)){
            @Override public boolean hasClientLoaded(){return true;}
        };
        actor.setPos(.5,200,.5);server.getPlayerList().getPlayers().add(actor);server.getPlayerList().getPlayersByUUID().put(profile.id(),actor);level.addNewPlayer(actor);
        player=actor.getBukkitEntity();player.setGravity(false);player.setGameMode(GameMode.SURVIVAL);
    }
    void first()throws Exception {
        layoutChecks();createPlayer();var locations=new ArrayList<String>();
        lootTable();
        var magic=(AdvanceMagicPlugin)Bukkit.getPluginManager().getPlugin("advance-magic");
        for(var kind:BossStructureLayout.Kind.values()){
            var site=garden.bossStructures().nearest(kind,0,0,20);check(site!=null,"locates "+kind.id+" "+site);
            locations.add(kind.id+","+site.x()+","+site.z());geometry(site);
            // Inspect actual generated walls, not just standalone blueprint rendering.
            var b=site.blueprint();
            for(var p:List.of(new BossStructure.Point(0,kind.floorY,0),new BossStructure.Point(10,kind.floorY+20,kind.chestZ-10),
                    new BossStructure.Point(4,kind.floorY,kind.chestZ),new BossStructure.Point(0,kind.floorY,kind.arrivalZ))){
                var block=world.getBlockAt(site.x()+p.x(),p.y(),site.z()+p.z());
                check(block.getType()==b.at(p.x(),p.y(),p.z()),kind.id+": actual world matches sample "+p);
            }
            for(int z=kind.chestZ-4;z<=kind.arrivalZ+2;z++){
                if(!world.getBlockAt(site.x(),kind.floorY,site.z()+z).getType().isSolid()
                        ||!world.getBlockAt(site.x(),kind.floorY+1,site.z()+z).getType().isAir()
                        ||!world.getBlockAt(site.x(),kind.floorY+2,site.z()+z).getType().isAir())throw new AssertionError("Blocked processional path "+kind+" z="+z);
            }
            check(true,kind.id+": continuous safe arrival-to-treasury path in actual chunks");
            var floor=world.getBlockAt(site.x(),kind.floorY,site.z());
            check(garden.structureProtection().protects(floor),kind.id+": arena protected");
            var mine=new BlockBreakEvent(floor,player);Bukkit.getPluginManager().callEvent(mine);check(mine.isCancelled(),kind.id+": survival cannot mine arena");
            player.setOp(true);player.setGameMode(GameMode.CREATIVE);mine=new BlockBreakEvent(floor,player);Bukkit.getPluginManager().callEvent(mine);
            check(!mine.isCancelled(),kind.id+": creative admin can maintain structure");player.setGameMode(GameMode.SURVIVAL);player.setOp(false);
            check(!garden.structureProtection().protects(Bukkit.getWorlds().getFirst().getBlockAt(site.x(),kind.floorY,site.z())),kind.id+": no protection outside Evergarden");
            check(site.blueprint().treasures().size()==8,kind.id+": eight decorated chest locations");
            int upgrades=0,staffs=0,cores=0;
            var chunk=world.getChunkAt(Math.floorDiv(site.x()+4,16),Math.floorDiv(site.z()+kind.chestZ,16));
            for(var spot:site.blueprint().treasures()){
                var p=spot.point();var state=world.getBlockAt(site.x()+p.x(),p.y(),site.z()+p.z()).getState();
                check(state instanceof Chest,kind.id+": chest generated at "+p);
                var contents=Arrays.stream(((Chest)state).getBlockInventory().getContents()).filter(Objects::nonNull).toList();
                check(contents.stream().anyMatch(this::enchanted),kind.id+": native enchanted equipment in each chest");
                check(contents.stream().allMatch(item->com.example.voidscape.enchant.BossEnchantService.enchants(item).entrySet().stream()
                    .allMatch(e->e.getValue()<=e.getKey().getMaxLevel()||garden.bossEnchants().level(item,e.getKey())==e.getValue())),kind.id+": every over-cap chest enchant has its own exact transfer entitlement");
                check(world.getBlockAt(site.x()+p.x(),p.y()+1,site.z()+p.z()).getType().isAir(),kind.id+": chest lid has free headroom");
                for(var item:((Chest)state).getBlockInventory().getContents())if(item!=null){
                    if(item.getItemMeta().getPersistentDataContainer().has(new NamespacedKey("advance_magic","wand_upgrade"),PersistentDataType.STRING))upgrades++;
                    if(magic.flyingStaff().isStaff(item))staffs++;
                    if(magic.restoration().isCore(item))cores++;
                }
            }
            check(upgrades==1&&staffs==1&&cores==1,kind.id+": exploration bonuses drawn once across all eight chests");
            var changes=BossInteriorService.changes(kind).values().stream().flatMap(Collection::stream).toList();
            check(changes.size()>500&&changes.stream().allMatch(c->BossInteriorDesignForTest.inside(kind,c.x(),c.y(),c.z())),kind.id+": furnishings alter only interior rooms");
            for(var display:site.blueprint().displays()){
                var p=display.point();var loaded=world.getChunkAt(Math.floorDiv(site.x()+p.x(),16),Math.floorDiv(site.z()+p.z(),16));
                long statues=Arrays.stream(loaded.getEntities()).filter(e->e.getPersistentDataContainer().has(garden.key("boss_interior_display"),PersistentDataType.STRING)).count();
                check(statues>=1,kind.id+": protected equipment display generated");
                int before=loaded.getEntities().length;new WhaleTreasure(garden).populate(loaded);
                check(loaded.getEntities().length==before,kind.id+": display does not duplicate on chunk population");
            }
            world.getBlockAt(site.x()+4,kind.floorY+1,site.z()+kind.chestZ).setType(Material.AIR,false);
            var remaining=(Chest)world.getBlockAt(site.x()+6,kind.floorY+1,site.z()+kind.chestZ).getState();remaining.getBlockInventory().clear();
            new WhaleTreasure(garden).populate(chunk);
            check(world.getBlockAt(site.x()+4,kind.floorY+1,site.z()+kind.chestZ).getType()==Material.AIR&&remaining.getBlockInventory().isEmpty(),kind.id+": chest removal and empty inventory cannot reroll");
            if(kind==BossStructureLayout.Kind.ARKHAIA)traps(site);
        }
        // Change configured placement before the second boot; persisted placement must win.
        garden.getConfig().set("structures.boss-structures.spacing-chunks",64);
        garden.getConfig().set("structures.boss-structures.minimum-gap",128);
        for(var kind:BossStructureLayout.Kind.values())garden.getConfig().set("structures.boss-structures."+kind.id+".chance",0);
        garden.saveConfig();world.save();Files.write(Path.of("phase-one-sites.txt"),locations);
    }
    boolean enchanted(org.bukkit.inventory.ItemStack item){
        return item.getType()==Material.ENCHANTED_BOOK||!item.getEnchantments().isEmpty();
    }
    void lootTable(){
        var table=new BossLootTable(garden);check(table.equipmentProfiles()==29,"config loads 29 distinct equipment profiles");
        var random=new Random(7272);var types=new HashSet<Material>();int swords=0,fortune=0,armor=0,books=0;
        for(int i=0;i<1500;i++)for(var item:table.roll(random,BossStructureLayout.Kind.ARKHAIA)){
            var meta=item.getItemMeta();Map<org.bukkit.enchantments.Enchantment,Integer> enchants=meta instanceof org.bukkit.inventory.meta.EnchantmentStorageMeta book?book.getStoredEnchants():item.getEnchantments();
            for(var a:enchants.keySet())for(var b:enchants.keySet())if(a!=b&&(a.conflictsWith(b)||b.conflictsWith(a)))throw new AssertionError("Incompatible loot enchants "+a+" "+b);
            if(!enchants.isEmpty())types.add(item.getType());
            if(item.getType()==Material.DIAMOND_SWORD){int level=item.getEnchantmentLevel(org.bukkit.enchantments.Enchantment.SHARPNESS);if(level<6||level>8)throw new AssertionError("Sword ceiling");swords++;}
            if(item.getType()==Material.DIAMOND_PICKAXE&&item.containsEnchantment(org.bukkit.enchantments.Enchantment.FORTUNE)){
                int level=item.getEnchantmentLevel(org.bukkit.enchantments.Enchantment.FORTUNE);if(level<4||level>5)throw new AssertionError("Fortune ceiling");fortune++;
            }
            if(item.getType().name().startsWith("DIAMOND_")&&item.getType().name().matches(".*(HELMET|CHESTPLATE|LEGGINGS|BOOTS)")){
                int level=item.getEnchantmentLevel(org.bukkit.enchantments.Enchantment.PROTECTION);if(level<6||level>8)throw new AssertionError("Armor ceiling");armor++;
            }
            if(item.getType()==Material.ENCHANTED_BOOK)books++;
            var original=item.clone();garden.relics().migrate(item);if(!item.equals(original))throw new AssertionError("Relic migration alters native boss gear");
        }
        check(swords>50&&fortune>50&&armor>100&&books>50,"native over-cap swords, fortune tools, armor and books verified in 1500 rolls");
        check(types.containsAll(List.of(Material.DIAMOND_AXE,Material.DIAMOND_HOE,Material.BOW,Material.CROSSBOW,Material.TRIDENT,Material.MACE,Material.SHIELD)),"loot covers additional weapon and tool families");
        check(true,"all generated enchants are compatible and survive existing relic migration");
    }
    /** Independent room boundaries also used by the full exterior-preservation check. */
    static final class BossInteriorDesignForTest {
        static boolean inside(BossStructureLayout.Kind k,int x,int y,int z){
            if(y<k.floorY||Math.hypot(x,z)<=k.arenaRadius)return false;
            return switch(k){case ARKHAIA->Math.abs(x)<=27&&z>=-91&&z<=-50&&y<=k.floorY+88||Math.abs(x)>=60&&Math.abs(x)<=76&&z>=-40&&z<=39&&y<=k.floorY+32;
                case VALKYRIE->x*x/289.0+Math.pow((z+62)/14.0,2)<=1&&y<=k.floorY+44;
                case SERAPHON->x*x/729.0+Math.pow((z+89)/23.0,2)<=1&&y<=k.floorY+62;};
        }
    }
    void geometry(BossStructureLayout.Site site){
        var kind=site.kind();int width=kind.radius*2+1,height=Math.min(kind.maxY+16,world.getMaxHeight()-1)-BossStructure.MIN_Y+1;
        var expected=new short[width*width*height];var materials=Material.values();
        for(var b:site.blueprint().boxes()){
            short material=(short)(b.material()==Material.AIR?0:b.material().ordinal()+1);
            for(int x=b.x1();x<=b.x2();x++)for(int z=b.z1();z<=b.z2();z++){
                int start=((x+kind.radius)*width+z+kind.radius)*height;
                Arrays.fill(expected,start+b.y1()-BossStructure.MIN_Y,start+b.y2()-BossStructure.MIN_Y+1,material);
            }
        }
        var generator=new VoidGenerator(world.getSeed(),garden.layout(),garden.skyWhales(),true,garden.skyLandmarks(),true,true,
            garden.restorationLayout(),garden.bossTemples(),true,garden.bossStructures());
        long count=0;
        for(int cx=Math.floorDiv(site.x()-kind.radius,16);cx<=Math.floorDiv(site.x()+kind.radius,16);cx++)
            for(int cz=Math.floorDiv(site.z()-kind.radius,16);cz<=Math.floorDiv(site.z()+kind.radius,16);cz++){
                var raw=Bukkit.getServer().createChunkData(world);generator.generateNoise(world,new Random(99),cx,cz,raw);
                for(int lx=0;lx<16;lx++)for(int lz=0;lz<16;lz++){
                    int x=cx*16+lx-site.x(),z=cz*16+lz-site.z();if(!site.contains(cx*16+lx,cz*16+lz,0))continue;
                    int start=((x+kind.radius)*width+z+kind.radius)*height;
                    for(int dy=0;dy<height;dy++){
                        var material=expected[start+dy]==0?Material.AIR:materials[expected[start+dy]-1];
                        if(raw.getType(lx,BossStructure.MIN_Y+dy,lz)!=material)throw new AssertionError(kind.id+" chunk mismatch: "+x+","+(BossStructure.MIN_Y+dy)+","+z);
                        count++;
                    }
                }
            }
        check(count==(long)expected.length,kind.id+": "+count+" blocks/air cells match chunk generation, including seams and headroom");
        for(int x=-kind.arenaRadius;x<=kind.arenaRadius;x++)for(int z=-kind.arenaRadius;z<=kind.arenaRadius;z++)if(Math.hypot(x,z)<=kind.arenaRadius){
            int start=((x+kind.radius)*width+z+kind.radius)*height;
            for(int y=kind.floorY+1;y<=kind.floorY+28;y++)if(expected[start+y-BossStructure.MIN_Y]!=0)throw new AssertionError("Arena obstruction "+kind+" "+x+","+y+","+z);
        }
        check(true,kind.id+": full combat disc has at least 28 blocks of unobstructed height");
    }
    void traps(BossStructureLayout.Site site){
        var p=site.blueprint().traps().getFirst();var block=world.getBlockAt(site.x()+p.x(),p.y(),site.z()+p.z());
        check(block.getType()==Material.POLISHED_BLACKSTONE_PRESSURE_PLATE,"Arkhaia ward exists in actual chunk");
        var rose=world.getBlockAt(site.x()-35,site.kind().floorY+4,site.z()+68).getBlockData();
        check(rose instanceof org.bukkit.block.data.Bisected plant&&plant.getHalf()==org.bukkit.block.data.Bisected.Half.TOP,"Arkhaia roses have valid upper block states");
        actor.setPos(block.getX()+.5,block.getY(),block.getZ()+.5);player.setHealth(20);player.setNoDamageTicks(0);
        double health=player.getHealth();physical(block);check(player.getHealth()<health,"protected ward plate still damages survival player");
        health=player.getHealth();player.setNoDamageTicks(0);physical(block);check(player.getHealth()==health,"ward cannot hit repeatedly inside two-second cooldown");
        player.setGameMode(GameMode.CREATIVE);physical(block);check(player.getHealth()==health,"creative inspection does not trigger ward");player.setGameMode(GameMode.SURVIVAL);
    }
    void physical(Block block){Bukkit.getPluginManager().callEvent(new PlayerInteractEvent(player,Action.PHYSICAL,null,block,BlockFace.UP,EquipmentSlot.HAND));}
    void layoutChecks(){
        var original=garden.bossStructures();
        var emptyDungeons=new DungeonLayout(world.getSeed(),18,0);
        var emptyWhales=new SkyWhaleLayout(world.getSeed(),emptyDungeons,32,0);
        var emptyLandmarks=new SkyLandmarkLayout(world.getSeed(),emptyDungeons,emptyWhales);
        var emptyAncient=new WorldBossTempleLayout(world.getSeed(),emptyDungeons,emptyWhales,emptyLandmarks,32,0,Set.of());
        var dense=new BossStructureLayout(world.getSeed(),emptyDungeons,emptyWhales,emptyLandmarks,emptyAncient,32,128,
            Map.of(BossStructureLayout.Kind.ARKHAIA,1.0,BossStructureLayout.Kind.VALKYRIE,1.0,BossStructureLayout.Kind.SERAPHON,1.0),EnumSet.allOf(BossStructureLayout.Kind.class),Set.of());
        var sites=new ArrayList<BossStructureLayout.Site>();
        for(int gx=-7;gx<=7;gx++)for(int gz=-7;gz<=7;gz++)sites.addAll(dense.cell(gx,gz));
        check(sites.size()>10,"dense placement exercised positive/negative cell coordinates");
        for(int i=0;i<sites.size();i++)for(int j=i+1;j<sites.size();j++){
            var a=sites.get(i);var b=sites.get(j);int separation=a.kind().radius+b.kind().radius+dense.minimumGap();
            if(Math.abs(a.x()-b.x())<=separation&&Math.abs(a.z()-b.z())<=separation)throw new AssertionError("Boss structures touch "+a+" "+b);
        }
        check(true,"all kinds and adjacent cells retain configured minimum gap under 100% rarity");
        // Enumerate existing dungeons independently through their original nearby API.
        var tightDungeons=new DungeonLayout(world.getSeed(),14,.35);
        var tightWhales=new SkyWhaleLayout(world.getSeed(),tightDungeons,32,0);
        var tightLandmarks=new SkyLandmarkLayout(world.getSeed(),tightDungeons,tightWhales);
        var tightAncient=new WorldBossTempleLayout(world.getSeed(),tightDungeons,tightWhales,tightLandmarks,32,0,Set.of());
        var tight=new BossStructureLayout(world.getSeed(),tightDungeons,tightWhales,tightLandmarks,tightAncient,32,128,
            Map.of(BossStructureLayout.Kind.ARKHAIA,1.0,BossStructureLayout.Kind.VALKYRIE,1.0,BossStructureLayout.Kind.SERAPHON,1.0),EnumSet.allOf(BossStructureLayout.Kind.class),Set.of());
        var allDungeons=new HashSet<DungeonLayout.Site>();
        for(int x=-21;x<=21;x++)for(int z=-21;z<=21;z++)allDungeons.addAll(tightDungeons.nearby(x*224,z*224));
        int tightCount=0;
        for(int gx=-7;gx<=7;gx++)for(int gz=-7;gz<=7;gz++)for(var placed:tight.cell(gx,gz)){
            tightCount++;
            for(var dungeon:allDungeons){
                int separation=placed.kind().radius+dungeon.radius()+128;
                if(Math.abs(placed.x()-dungeon.x())<=separation&&Math.abs(placed.z()-dungeon.z())<=separation)throw new AssertionError("Narrow-grid dungeon overlap");
            }
        }
        check(tightCount>10,"large reservations also avoid dungeons on the narrowest configured grid");
        int actualSites=0;
        for(int gx=-12;gx<=12;gx++)for(int gz=-12;gz<=12;gz++)for(var placed:original.cell(gx,gz)){
            actualSites++;
            for(var dungeon:garden.layout().nearby(placed.x(),placed.z())){
                int separation=placed.kind().radius+dungeon.radius()+original.minimumGap();
                if(Math.abs(placed.x()-dungeon.x())<=separation&&Math.abs(placed.z()-dungeon.z())<=separation)throw new AssertionError("Dungeon overlap");
            }
            int reach=placed.kind().radius+120+original.minimumGap();
            for(int a=Math.floorDiv(placed.x()-reach,512);a<=Math.floorDiv(placed.x()+reach,512);a++)
                for(int b=Math.floorDiv(placed.z()-reach,512);b<=Math.floorDiv(placed.z()+reach,512);b++){
                    var whale=garden.skyWhales().cell(a,b);
                    if(whale!=null&&Math.abs(placed.x()-whale.x())<=placed.kind().radius+115+64&&Math.abs(placed.z()-whale.z())<=placed.kind().radius+65+64)throw new AssertionError("Whale overlap");
                    for(var landmark:garden.skyLandmarks().cell(a,b)){
                        int separation=placed.kind().radius+landmark.kind().radius+64;
                        if(Math.abs(placed.x()-landmark.x())<=separation&&Math.abs(placed.z()-landmark.z())<=separation)throw new AssertionError("Landmark overlap");
                    }
                    var ancient=garden.bossTemples().cell(a,b);
                    if(ancient!=null&&Math.abs(placed.x()-ancient.x())<=placed.kind().radius+120+64&&Math.abs(placed.z()-ancient.z())<=placed.kind().radius+120+64)throw new AssertionError("Ancient temple overlap");
                }
        }
        check(actualSites>5,"actual candidates retain their gap from dungeons, whales, gardens, observatories and ancient temples");
        var site=original.nearest(BossStructureLayout.Kind.ARKHAIA,0,0,20);
        int gx=Math.floorDiv(site.x(),original.spacingChunks()*16),gz=Math.floorDiv(site.z(),original.spacingChunks()*16);
        var excluded=new BossStructureLayout(world.getSeed(),garden.layout(),garden.skyWhales(),garden.skyLandmarks(),garden.bossTemples(),32,64,
            Map.of(BossStructureLayout.Kind.ARKHAIA,.3,BossStructureLayout.Kind.VALKYRIE,.3,BossStructureLayout.Kind.SERAPHON,.3),EnumSet.allOf(BossStructureLayout.Kind.class),Set.of(SkyPlacementHistory.key(gx,gz)));
        check(excluded.cell(gx,gz).isEmpty(),"explored cells never receive a partial new build");
        var legacy=new BossStructureLayout(world.getSeed(),garden.layout(),garden.skyWhales(),garden.skyLandmarks(),garden.bossTemples(),32,64,
            Map.of(BossStructureLayout.Kind.ARKHAIA,.3,BossStructureLayout.Kind.VALKYRIE,.3,BossStructureLayout.Kind.SERAPHON,.3),EnumSet.allOf(BossStructureLayout.Kind.class),Set.of(),Set.of(SkyPlacementHistory.key(gx,gz)));
        var preserved=legacy.cell(gx,gz).stream().filter(s->s.kind()==site.kind()).findFirst().orElseThrow();
        check(preserved.x()==site.x()&&preserved.z()==site.z()&&preserved.legacyDesign(),"architecture revision retains exact candidates and the old design for explored cells");
        check(preserved.blueprint().boxes().size()!=site.blueprint().boxes().size(),"old and new cells resolve to distinct geometry");
        var disabled=new BossStructureLayout(world.getSeed(),garden.layout(),garden.skyWhales(),garden.skyLandmarks(),garden.bossTemples(),32,64,
            Map.of(BossStructureLayout.Kind.ARKHAIA,.3,BossStructureLayout.Kind.VALKYRIE,.3,BossStructureLayout.Kind.SERAPHON,.3),Set.of(),Set.of());
        check(disabled.cell(gx,gz).equals(original.cell(gx,gz)),"enabled toggles do not move another kind's candidate");
        var generator=new VoidGenerator(world.getSeed(),garden.layout(),garden.skyWhales(),true,garden.skyLandmarks(),true,true,
            garden.restorationLayout(),garden.bossTemples(),true,disabled);
        boolean land=false;for(int x=-50;x<=50;x+=5)for(int z=-50;z<=50;z+=5)land|=generator.surface(site.x()+x,site.z()+z).land();
        check(land,"disabled structures release their terrain reservation");
    }
    void restart()throws Exception {
        check(garden.bossStructures().spacingChunks()==32&&garden.bossStructures().minimumGap()==64,"restart retains saved grid and gap despite config edits");
        for(var line:Files.readAllLines(Path.of("phase-one-sites.txt"))){
            var parts=line.split(",");var kind=BossStructureLayout.Kind.fromId(parts[0]);int x=Integer.parseInt(parts[1]),z=Integer.parseInt(parts[2]);
            var site=garden.bossStructures().nearest(kind,0,0,20);
            check(site.x()==x&&site.z()==z&&garden.bossStructures().chance(kind)==.3,kind.id+": exact site and rarity survive real restart");
            check(world.getBlockAt(x+4,kind.floorY+1,z+kind.chestZ).getType()==Material.AIR,kind.id+": removed chest stays removed after restart");
            var chest=(Chest)world.getBlockAt(x+6,kind.floorY+1,z+kind.chestZ).getState();
            check(chest.getBlockInventory().isEmpty(),kind.id+": looted chest remains empty after restart");
        }
    }
}
