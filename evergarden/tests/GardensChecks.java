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

public final class GardensChecks extends JavaPlugin {
    static final class TestConnection extends Connection {
        TestConnection(){super(PacketFlow.SERVERBOUND);channel=new EmbeddedChannel();address=new InetSocketAddress("127.0.0.1",1);}
        @Override public void send(Packet<?> packet){}
        @Override public void send(Packet<?> packet,ChannelFutureListener listener){}
        @Override public void send(Packet<?> packet,ChannelFutureListener listener,boolean flush){}
        @Override public boolean isConnected(){return true;}
    }

    com.example.voidscape.VoidscapePlugin voids;
    AdvanceMagicPlugin magic;
    Player actor; ServerPlayer handle; int passed; World world;
    final List<int[]> chunks=new ArrayList<>();
    void check(boolean value,String message){if(!value)throw new AssertionError(message);passed++;getLogger().info("PASS "+message);}
    @Override public void onEnable(){Bukkit.getScheduler().runTaskLater(this,()->{
        try {
            voids=(com.example.voidscape.VoidscapePlugin)Bukkit.getPluginManager().getPlugin("Evergarden");
            magic=(AdvanceMagicPlugin)Bukkit.getPluginManager().getPlugin("advance-magic");
            check(voids!=null&&voids.isEnabled()&&magic!=null&&magic.isEnabled(),"both release plugins boot");
            world=voids.world();
            for(int x=-18;x<=18;x++)for(int z=-18;z<=13;z++)chunks.add(new int[]{x,z});
            load(0);
        }catch(Throwable t){finish(t);}
    },20);}
    void load(int start) {
        if(start>=chunks.size()){try{checks();finish(null);}catch(Throwable t){finish(t);}return;}
        var jobs=new ArrayList<java.util.concurrent.CompletableFuture<?>>();
        for(int n=start;n<Math.min(start+32,chunks.size());n++){
            int[] c=chunks.get(n);jobs.add(world.getChunkAtAsync(c[0],c[1],true));
        }
        java.util.concurrent.CompletableFuture.allOf(jobs.toArray(java.util.concurrent.CompletableFuture[]::new)).whenComplete((v,t)->Bukkit.getScheduler().runTask(this,()->{
            if(t!=null){finish(t);return;}
            for(int n=start;n<Math.min(start+32,chunks.size());n++){int[] c=chunks.get(n);world.getChunkAt(c[0],c[1]).addPluginChunkTicket(this);}
            load(start+32);
        }));
    }
    void checks() throws Exception {
        check(world.getBlockAt(0,96,0).getType()==Material.SEA_LANTERN,"spawn floor and travel height");
        check(world.getBlockAt(0,97,0).isPassable()&&world.getBlockAt(0,98,0).isPassable(),"safe spawn body space");
        check(world.getBlockAt(0,97,4).getType()==Material.LECTERN,"guide lectern");
        check(world.getBlockAt(0,98,-5).getType()==Material.NETHER_PORTAL,"return portal");
        check(world.getBlockAt(43,95,33).getType()==Material.WATER,"spring pool generated");
        check(world.getBlockAt(55,95,-33).getType()==Material.GRASS_BLOCK,"home meadow ready to build");
        check(world.getTime()==13000,"twilight atmosphere");
        for(var site:com.example.voidscape.world.DungeonLayout.STARTER_SITES) {
            check(world.getBlockAt(site.x(),97,site.z()+8).getType()==Material.LODESTONE,"altar "+site.kind());
            check(world.getBlockAt(site.x(),97,site.z()-16).getType()==Material.VAULT,"vault "+site.kind());
            check(world.getBlockAt(site.x(),110,site.z()).isPassable(),"open-air temple "+site.kind());
            int endZ=site.z()+(site.z()<0?28:-28);
            for(int n=20;n<=500;n++){
                int x=(int)Math.round(site.x()*n/500.0),z=(int)Math.round(endZ*n/500.0);
                if(Math.hypot(x,z)<14)continue;
                if(world.getBlockAt(x,95,z).isPassable()||!world.getBlockAt(x,97,z).isPassable()||!world.getBlockAt(x,98,z).isPassable())throw new AssertionError("Blocked causeway at "+x+","+z);
            }
            check(true,"walkable causeway "+site.kind());
        }
        var server=MinecraftServer.getServer();var level=((CraftWorld)world).getHandle();
        GameProfile profile=new GameProfile(UUID.randomUUID(),"GardenTestActor");
        handle=new ServerPlayer(server,level,profile,ClientInformation.createDefault());
        handle.connection=new ServerGamePacketListenerImpl(server,new TestConnection(),handle,CommonListenerCookie.createInitial(profile,false));
        handle.setPos(0.5,97,0.5);server.getPlayerList().getPlayers().add(handle);server.getPlayerList().getPlayersByUUID().put(profile.id(),handle);
        level.addNewPlayer(handle);actor=handle.getBukkitEntity();actor.setOp(true);actor.setGravity(false);
        for(Spell spell:Spell.values()) {
            ItemStack core=voids.relics().createMagicCore(spell.id());
            check(magic.wands().coreSpell(core)==spell,"vault core recognized "+spell.id());
            for(int mode=0;mode<3;mode++) {
                ItemStack[] grid=grid(core,mode);
                var result=Bukkit.craftItemResult(grid,world,actor);
                check(magic.wands().spell(result.getResult())==spell,"actual server crafting "+spell.id()+" material mode "+mode);
                check(Arrays.stream(result.getResultingMatrix()).allMatch(i->i==null||i.getType().isAir()),"consumes exactly one full recipe "+spell.id()+" mode "+mode);
            }
            var meta=core.getItemMeta();meta.setDisplayName("Renamed legacy core");meta.setLore(List.of("Old lore"));core.setItemMeta(meta);
            check(magic.wands().spell(Bukkit.craftItem(grid(core,2),world,actor))==spell,"renamed core crafts "+spell.id());
        }
        check(Bukkit.craftItem(grid(new ItemStack(Material.HEART_OF_THE_SEA),0),world,actor).getType().isAir(),"ordinary heart rejected");
        ItemStack broken=voids.relics().createMagicCore("frost_nova");var bm=broken.getItemMeta();bm.getPersistentDataContainer().set(new NamespacedKey(magic,"core"),org.bukkit.persistence.PersistentDataType.STRING,"shadow_step");broken.setItemMeta(bm);
        check(Bukkit.craftItem(grid(broken,0),world,actor).getType().isAir(),"conflicting core tags rejected");
        ItemStack[] missing=grid(voids.relics().createMagicCore("frost_nova"),0);missing[8]=null;
        check(Bukkit.craftItem(missing,world,actor).getType().isAir(),"incomplete ring rejected");
        actor.setOp(false);var permission=actor.addAttachment(this,"advance-magic.craft",false);
        check(Bukkit.craftItem(grid(voids.relics().createMagicCore("frost_nova"),0),world,actor).getType().isAir(),"craft permission enforced");actor.removeAttachment(permission);
        var machineBlock=world.getBlockAt(-10,97,0);
        var previous=machineBlock.getBlockData();
        try {
            machineBlock.setType(Material.CRAFTER,false);
            var machine=(org.bukkit.block.Crafter)machineBlock.getState();
            for(Spell spell:Spell.values()) {
                ItemStack[] grid=grid(voids.relics().createMagicCore(spell.id()),2);
                machine.getInventory().setContents(grid);
                var recipe=(CraftingRecipe)Bukkit.getCraftingRecipe(grid,world);
                var event=new org.bukkit.event.block.CrafterCraftEvent(machineBlock,recipe,recipe.getResult());
                Bukkit.getPluginManager().callEvent(event);
                check(!event.isCancelled()&&magic.wands().spell(event.getResult())==spell,"Crafter event routes correct core "+spell.id());
            }
            ItemStack[] grid=grid(new ItemStack(Material.HEART_OF_THE_SEA),0);
            machine.getInventory().setContents(grid);
            var recipe=(CraftingRecipe)Bukkit.getCraftingRecipe(grid,world);
            var event=new org.bukkit.event.block.CrafterCraftEvent(machineBlock,recipe,recipe.getResult());
            Bukkit.getPluginManager().callEvent(event);
            check(event.isCancelled(),"automated crafter cannot bypass tagged core validation");
            machine.getInventory().clear();
        } finally {machineBlock.setBlockData(previous,false);}
        evergardenChecks();
        renderMap();
    }
    Object field(Object target,String name)throws Exception {
        var field=target.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(target);
    }
    @SuppressWarnings("unchecked") void evergardenChecks() throws Exception {
        check(voids.getName().equals("Evergarden"),"plugin renamed to Evergarden");
        check(voids.key("void_key").toString().equals("voidscape:void_key"),"legacy item identity preserved");
        check(voids.getCommand("evergarden")!=null&&Bukkit.getPluginCommand("void")!=null,"new command and legacy alias");
        check(voids.dungeons().waveCount()==5,"default five guardian waves");
        ItemStack legacy=voids.relics().createVoidKey();var meta=legacy.getItemMeta();
        meta.setItemModel(new NamespacedKey("voidscape","void_key"));meta.setCustomModelDataComponent(null);meta.setDisplayName("My old key");legacy.setItemMeta(meta);
        check(voids.relics().migrate(legacy)&&voids.relics().isVoidKey(legacy),"old key migrates without losing identity");
        check(!legacy.getItemMeta().hasItemModel()&&legacy.getItemMeta().getCustomModelDataComponent().getStrings().equals(List.of("voidscape:void_key")),"key uses fallback-safe model selector");
        check(legacy.getItemMeta().getDisplayName().equals("My old key")&&!voids.relics().migrate(legacy),"migration preserves custom name and is idempotent");
        var packs=voids.packs();var host=(com.example.voidscape.pack.PackHttpServer)field(packs,"http");
        check(host!=null,"Evergarden Java pack host started");
        var request=java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://127.0.0.1:"+host.port()+host.path())).build();
        var response=java.net.http.HttpClient.newHttpClient().send(request,java.net.http.HttpResponse.BodyHandlers.ofByteArray());
        check(response.statusCode()==200&&Arrays.equals(response.body(),java.nio.file.Files.readAllBytes(voids.getDataFolder().toPath().resolve("resource-packs/evergarden-java.zip"))),"HTTP serves exact embedded Evergarden pack");
        actor.setGameMode(GameMode.SURVIVAL);actor.setInvulnerable(true);
        Map<String,Object> active=(Map<String,Object>)field(voids.dungeons(),"active");
        var ledger=(org.bukkit.configuration.file.YamlConfiguration)field(voids.dungeons(),"ledger");
        for(var site:com.example.voidscape.world.DungeonLayout.STARTER_SITES) {
            ledger.set("sites."+site.id()+".next-open",0);
            actor.teleport(new Location(world,site.x()+0.5,97,site.z()+8.5));
            var altar=world.getBlockAt(site.x(),97,site.z()+8);
            voids.dungeons().interact(new org.bukkit.event.player.PlayerInteractEvent(actor,org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK,new ItemStack(Material.AIR),altar,org.bukkit.block.BlockFace.UP,EquipmentSlot.HAND));
            Object encounter=active.get(site.id());check(encounter!=null,"encounter starts "+site.kind());
            for(int wave=1;wave<=5;wave++) {
                check((Integer)field(encounter,"wave")==wave&&!(Boolean)field(encounter,"bossStarted"),"guardian wave "+wave+" "+site.kind());
                var mobs=(Map<UUID,?>)field(encounter,"mobs");check(!mobs.isEmpty()&&mobs.size()<=8,"bounded wave population");
                for(UUID id:new ArrayList<>(mobs.keySet())) {
                    Mob mob=(Mob)Bukkit.getEntity(id);
                    check(mob.getHealth()==(mobs.get(id).toString().equals("MINION")?135:80),"buffed guardian health");
                    if(mob.getAttribute(Attribute.ATTACK_DAMAGE)!=null)
                        check(mob.getAttribute(Attribute.ATTACK_DAMAGE).getBaseValue()==16,"buffed guardian attack");
                    check(mob.getEquipment().getHelmet().getItemMeta().getCustomModelDataComponent().getStrings().getFirst().endsWith("_mask"),"guardian equipped custom mask");
                    check(mob.getEquipment().getHelmetDropChance()==0,"cosmetic mask cannot drop");
                    mob.setHealth(0);
                }
            }
            check((Boolean)field(encounter,"bossStarted"),"boss only after five waves "+site.kind());
            var mobs=(Map<UUID,?>)field(encounter,"mobs");check(mobs.size()==1,"exactly one boss");
            var boss=(Mob)Bukkit.getEntity(mobs.keySet().iterator().next());
            check(boss.getHealth()==1024,"buffed boss reaches supported health cap");
            check(boss.getAttribute(Attribute.ATTACK_DAMAGE).getBaseValue()==27,"buffed boss attack");
            check(boss.getEquipment().getHelmet().getItemMeta().getCustomModelDataComponent().getStrings().getFirst().endsWith("_crown"),"boss crown equipped");
            if(boss instanceof PiglinAbstract piglin)check(piglin.isImmuneToZombification(),"chrono boss cannot transform and stall combat");
            int keys=Arrays.stream(actor.getInventory().getContents()).filter(voids.relics()::isVoidKey).mapToInt(ItemStack::getAmount).sum();
            boss.setHealth(0);
            check(!active.containsKey(site.id()),"completed encounter cleaned up");
            check(Arrays.stream(actor.getInventory().getContents()).filter(voids.relics()::isVoidKey).mapToInt(ItemStack::getAmount).sum()==keys+1,"one legacy-compatible key awarded");
        }
    }
    ItemStack[] grid(ItemStack core,int mode){
        ItemStack[] items=new ItemStack[9];for(int i=0;i<9;i++)items[i]=new ItemStack(mode==1||(mode==2&&i%2==0)?Material.NETHER_STAR:Material.NETHERITE_INGOT);items[4]=core.clone();return items;
    }
    int color(Material m) {
        String n=m.name();
        if(n.contains("CHERRY_LEAVES"))return 0xEFB5D2;
        if(n.contains("LEAVES")||n.contains("AZALEA"))return 0x467D6C;
        if(n.contains("GRASS")||n.contains("MOSS"))return 0x719867;
        if(n.contains("WATER"))return 0x43A6B5;
        if(n.contains("LANTERN")||n.contains("FROGLIGHT"))return 0xE4EDB4;
        if(n.contains("QUARTZ")||n.contains("CALCITE"))return 0xE4DED1;
        if(n.contains("AMETHYST")||n.contains("PURPUR"))return 0xA78BC6;
        if(n.contains("COPPER"))return 0x619F96;
        if(n.contains("GLASS"))return 0x768AB9;
        if(n.contains("END_STONE"))return 0xC7C9AE;
        if(n.contains("OBSIDIAN")||n.contains("PORTAL"))return 0x5F4278;
        if(n.contains("LOG")||n.contains("DIRT"))return 0x75634E;
        if(n.contains("TULIP")||n.contains("ALLIUM"))return 0xD6A6CF;
        if(n.contains("BLUE")||n.contains("ORCHID"))return 0xADCDE0;
        return 0x485466;
    }
    void renderMap()throws Exception {
        int size=1184;var img=new java.awt.image.BufferedImage(size,size,java.awt.image.BufferedImage.TYPE_INT_RGB);var g=img.createGraphics();
        g.setColor(new java.awt.Color(0x101C2C));g.fillRect(0,0,size,size);
        for(int x=-288;x<304;x++)for(int z=-288;z<224;z++){
            int y=world.getHighestBlockYAt(x,z);if(y<0)continue;
            Material m=world.getBlockAt(x,y,z).getType();int rgb=color(m);double factor=Math.clamp(0.85+(y-95)*0.012,0.65,1.2);
            int r=Math.min(255,(int)(((rgb>>16)&255)*factor)),b=Math.min(255,(int)((rgb&255)*factor)),green=Math.min(255,(int)(((rgb>>8)&255)*factor));
            g.setColor(new java.awt.Color(r,green,b));g.fillRect((x+288)*2,(z+288)*2,2,2);
        }
        g.setColor(new java.awt.Color(0xE4DED1));g.setFont(new java.awt.Font("SansSerif",java.awt.Font.BOLD,26));
        g.drawString("VOIDSCAPE / TWILIGHT GARDENS",32,1070);g.setFont(new java.awt.Font("SansSerif",0,18));
        g.drawString("Actual generated blocks - top view - north is up",32,1104);g.drawString("Spawn (0,0) | Dark (0,-250) | Astral (220,130) | Time (-220,130)",32,1136);
        g.dispose();javax.imageio.ImageIO.write(img,"png",new java.io.File("gardens-map.png"));
    }
    void finish(Throwable error) {
        try{java.nio.file.Files.writeString(java.nio.file.Path.of("gardens-result.txt"),error==null?"PASS "+passed:"FAIL after "+passed+": "+error);}catch(Exception ignored){}
        if(error!=null)getLogger().log(java.util.logging.Level.SEVERE,"GARDENS FAILED",error);else getLogger().info("GARDENS COMPLETE: "+passed+" checks");
        if(handle!=null){MinecraftServer.getServer().getPlayerList().getPlayers().remove(handle);MinecraftServer.getServer().getPlayerList().getPlayersByUUID().remove(handle.getUUID());handle.discard();}
        if(world!=null)world.removePluginChunkTickets(this);Bukkit.getScheduler().runTaskLater(this,Bukkit::shutdown,5);
    }
}
