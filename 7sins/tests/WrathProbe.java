import com.example.sevensins.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.plugin.java.JavaPlugin;
import java.lang.reflect.*;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.util.*;

/** Real Paper lifecycle, damage dispatch, model poses and pack hosting in a disposable world. */
public final class WrathProbe extends JavaPlugin {
    private final List<String> checks = new ArrayList<>();
    private void check(boolean value, String message) {
        if (!value) throw new AssertionError(message); checks.add(message); getLogger().info(message);
    }
    private static Object call(Object object, String method, Class<?>[] types, Object... args) throws Exception {
        Method m = object.getClass().getDeclaredMethod(method, types); m.setAccessible(true); return m.invoke(object, args);
    }
    @Override public void onEnable() {
        Bukkit.getScheduler().runTaskLater(this, () -> {
            String result="PASS";
            try { probe(); }
            catch (Throwable error) { result="FAIL " + error; getLogger().log(java.util.logging.Level.SEVERE,"Wrath probe",error); }
            try { Files.writeString(getServer().getWorldContainer().toPath().resolve("wrath-probe-result.txt"),result+"\n"+String.join("\n",checks)); }
            catch (Exception error) { getLogger().severe(error.toString()); }
            Bukkit.shutdown();
        }, 5);
    }
    private void probe() throws Exception {
        SevenSinsPlugin plugin=(SevenSinsPlugin) Bukkit.getPluginManager().getPlugin("7sins");
        check(plugin!=null && plugin.isEnabled(),"7sins loads on Paper 26.2 without ModelEngine or Floodgate");
        World world=Bukkit.getWorlds().getFirst(); world.setTime(6000);
        Location home=new Location(world,0.5,world.getHighestBlockYAt(0,0)+1,0.5);
        for(int x=-12;x<=12;x++)for(int z=-12;z<=12;z++)
            world.getBlockAt(x,home.getBlockY()-1,z).setType(Material.STONE,false);
        List<Material> before=new ArrayList<>();
        for(int x=-10;x<=10;x++)for(int z=-10;z<=10;z++) before.add(world.getBlockAt(x,home.getBlockY()-1,z).getType());
        WrathBoss boss=plugin.spawnWrath(home);
        check(boss.entity().isValid() && boss.entity().isInvisible() && boss.entity().getHealth()==600,"Invisible Husk has configured HP");
        @SuppressWarnings("unchecked") List<Entity> visuals=(List<Entity>)call(boss,"visuals",new Class<?>[0]);
        check(visuals.size()==10 && visuals.stream().filter(e->e instanceof ItemDisplay).count()==9,"Nine live model bones plus one armor fallback");
        check(visuals.stream().allMatch(e->!e.isPersistent()&&!e.isVisibleByDefault()),"Temporary visuals are hidden until viewer selection");
        boss.entity().damage(20);
        check(boss.entity().getHealth()==600,"Arrival damage is cancelled by actual Bukkit event dispatch");
        for(int i=0;i<41;i++) boss.tick();
        check(boss.state()==WrathBoss.State.CHASE,"Arrival ends and encounter begins");
        boss.entity().setNoDamageTicks(0); boss.entity().damage(12);
        check(boss.entity().getHealth()<600 && boss.rage()>0,"Normal hits damage the boss and build rage");
        call(boss,"stagger",new Class<?>[0]);
        boss.entity().setNoDamageTicks(0); double hp=boss.entity().getHealth(); boss.entity().damage(10);
        check(Math.abs(hp-boss.entity().getHealth()-15)<0.01 && boss.rage()==0,"Stagger clears rage and increases incoming damage 50%");
        // Check animations and every telegraph/impact path against real Paper entities.
        for(WrathBoss.Attack attack:WrathBoss.Attack.values()) {
            call(boss,"startAttack",new Class<?>[]{WrathBoss.Attack.class,Location.class},attack,home.clone().add(0,0,8));
            call(boss,"warning",new Class<?>[0]);
            if(attack!=WrathBoss.Attack.CHARGE) call(boss,"executeImpact",new Class<?>[0]);
        }
        check(true,"All four telegraphs and impact effects execute on Paper");
        // The swept charge detects a one-block wall without breaking it.
        world.getBlockAt(0,home.getBlockY(),1).setType(Material.STONE,false);
        call(boss,"charge",new Class<?>[]{List.class},List.of());
        check(boss.state()==WrathBoss.State.STAGGER && world.getBlockAt(0,home.getBlockY(),1).getType()==Material.STONE,
                "Charging into a one-block wall staggers without breaking blocks");
        world.getBlockAt(0,home.getBlockY(),1).setType(Material.AIR,false);
        boss.entity().setHealth(299); boss.tick();
        check(boss.enraged() && boss.state()==WrathBoss.State.TRANSITION,"50% HP triggers phase two and protected transition");
        boss.entity().setNoDamageTicks(0); boss.entity().damage(30);
        check(boss.entity().getHealth()==299,"Phase transition cannot be skipped by burst damage");
        for(Entity e:visuals) if(e instanceof ItemDisplay display)
            check(Float.isFinite(display.getTransformation().getTranslation().y),"Valid animated transform "+display.getItemStack().getItemMeta().getItemModel());

        UUID viewerId=UUID.randomUUID(); List<String> visibility=new ArrayList<>();
        Player viewer=(Player)Proxy.newProxyInstance(Player.class.getClassLoader(),new Class<?>[]{Player.class},(proxy,method,args)->{
            return switch(method.getName()) {
                case "getUniqueId" -> viewerId;
                case "showEntity", "hideEntity" -> {visibility.add(method.getName()+":"+((Entity)args[1]).getType()); yield null;}
                case "getName" -> "WrathPackProbe";
                case "toString" -> "WrathPackProbe";
                case "hashCode" -> viewerId.hashCode();
                case "equals" -> proxy==args[0];
                default -> throw new UnsupportedOperationException(method.getName());
            };
        });
        call(boss,"refresh",new Class<?>[]{Player.class},viewer);
        check(visibility.stream().filter(s->s.startsWith("showEntity")).toList().equals(List.of("showEntity:ARMOR_STAND")),
                "No-pack viewer sees only the fallback");
        visibility.clear();
        plugin.packs().status(new PlayerResourcePackStatusEvent(viewer,BossPacks.PACK_ID,PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED));
        check(plugin.packs().loaded(viewer)&&visibility.stream().filter(s->s.equals("showEntity:ITEM_DISPLAY")).count()==9,
                "Successful pack load switches the viewer to nine custom bones");
        plugin.packs().status(new PlayerResourcePackStatusEvent(viewer,UUID.randomUUID(),PlayerResourcePackStatusEvent.Status.FAILED_DOWNLOAD));
        check(plugin.packs().loaded(viewer),"Another plugin's pack failure does not hide Wrath models");
        plugin.packs().status(new PlayerResourcePackStatusEvent(viewer,BossPacks.PACK_ID,PlayerResourcePackStatusEvent.Status.FAILED_DOWNLOAD));
        check(!plugin.packs().loaded(viewer),"Failed boss pack restores the fallback");

        String url=plugin.packs().url(viewer);
        HttpClient client=HttpClient.newHttpClient();
        HttpResponse<byte[]> zip=client.send(HttpRequest.newBuilder(URI.create(url)).build(),HttpResponse.BodyHandlers.ofByteArray());
        check(zip.statusCode()==200 && zip.body().length>10000 && zip.body()[0]=='P' && zip.body()[1]=='K',"Bundled HTTP host serves the actual ZIP");
        HttpResponse<Void> missing=client.send(HttpRequest.newBuilder(URI.create(url.replace("/7sins/","/private/"))).build(),HttpResponse.BodyHandlers.discarding());
        check(missing.statusCode()==404,"HTTP host does not expose unrelated paths");
        HttpResponse<Void> cached=client.send(HttpRequest.newBuilder(URI.create(url)).header("If-None-Match",zip.headers().firstValue("ETag").orElseThrow()).build(),HttpResponse.BodyHandlers.discarding());
        check(cached.statusCode()==304,"Resource pack supports immutable checksum caching");

        int index=0; for(int x=-10;x<=10;x++)for(int z=-10;z<=10;z++)
            if(world.getBlockAt(x,home.getBlockY()-1,z).getType()!=before.get(index++))throw new AssertionError("Terrain changed");
        check(true,"Attack effects preserve all 441 arena floor blocks");
        try {plugin.spawnWrath(home.clone().add(10,0,0));throw new AssertionError("Overlapping spawn permitted");}
        catch(IllegalStateException expected) {check(true,"Overlapping encounters are rejected");}
        boss.remove();
        check(!boss.entity().isValid()&&visuals.stream().noneMatch(Entity::isValid),"Removal cleans base and every model entity");
        // Allow manager to retire removed boss before a new spawn.
        Field manager=SevenSinsPlugin.class.getDeclaredField("bosses");manager.setAccessible(true);
        ((Map<?,?>)manager.get(plugin)).clear();
        boss=plugin.spawnWrath(home);
        world.getChunkAt(2,0);
        boolean teleported=boss.entity().teleport(home.clone().add(40,0,0)); boss.tick();
        check(teleported&&boss.entity().isValid()&&boss.entity().getLocation().distanceSquared(home)<1&&boss.entity().getHealth()==600,
                "Arena leash resets boss without editing terrain: valid="+boss.entity().isValid()+" state="+boss.state()+" location="+boss.entity().getLocation());
        @SuppressWarnings("unchecked") List<Entity> shutdownVisuals=(List<Entity>)call(boss,"visuals",new Class<?>[0]);
        Bukkit.getPluginManager().disablePlugin(plugin);
        check(!boss.entity().isValid()&&shutdownVisuals.stream().noneMatch(Entity::isValid),"Plugin disable cleans every encounter");
        check(world.getEntities().stream().noneMatch(e->e instanceof org.bukkit.entity.Item),"Admin cleanup awards no loot");
    }
}
