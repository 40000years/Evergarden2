import com.example.sevensins.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.Damageable;
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
        check(plugin.getConfig().getDouble("wrath.health")==1800,"Old default 600 HP is migrated to 1800 without deleting config");
        check(plugin.getConfig().getDouble("wrath.damage-multiplier")==3&&plugin.getConfig().getDouble("wrath.arena-radius")==140,
                "Old default damage and arena are upgraded to 3x damage and giant arena");
        check(boss.entity().isValid() && boss.entity().isInvisible() && boss.health()==1800&&boss.entity().getHealth()<=1024,"Boss has configured HP while respecting Minecraft's native health cap");
        @SuppressWarnings("unchecked") List<Entity> visuals=(List<Entity>)call(boss,"visuals",new Class<?>[0]);
        check(visuals.size()==10 && visuals.stream().filter(e->e instanceof ItemDisplay).count()==9,"Nine live model bones plus one armor fallback");
        check(visuals.stream().allMatch(e->!e.isPersistent()&&!e.isVisibleByDefault()),"Temporary visuals are hidden until viewer selection");
        check(boss.scale()==5&&Math.abs(boss.entity().getAttribute(Attribute.SCALE).getValue()-8.25)<0.001,
                "Native hitbox is five times the previous base scale");
        check(visuals.stream().filter(e->e instanceof ItemDisplay).allMatch(e->{
            ItemDisplay display=(ItemDisplay)e;
            return display.getTransformation().getScale().x==5&&display.getDisplayHeight()>=40;
        }),"Every custom bone and its culling bounds are scaled five times");
        check(visuals.stream().filter(e->e instanceof ArmorStand).allMatch(e->((ArmorStand)e).getAttribute(Attribute.SCALE).getValue()==7.5),
                "No-pack fallback uses the same fivefold enlargement");
        check(Arrays.stream(boss.entity().getEquipment().getArmorContents()).allMatch(i->i==null||i.getType().isAir()),
                "Invisible native base carries no visible armor inside the custom model");
        check(visuals.stream().filter(e->e instanceof ArmorStand).allMatch(e->!((ArmorStand)e).isVisible()),
                "Fallback stand body is invisible beneath its armor");
        animationFrames();
        boss.entity().damage(20);
        check(boss.health()==1800,"Arrival damage is cancelled by actual Bukkit event dispatch");
        for(int i=0;i<41;i++) boss.tick();
        check(boss.state()==WrathBoss.State.CHASE,"Arrival ends and encounter begins");
        boss.entity().setNoDamageTicks(0); boss.entity().damage(12);
        check(Math.abs(boss.health()-1791.6)<0.01 && boss.rage()>0,"Normal hits are reduced 30% and build rage");
        call(boss,"stagger",new Class<?>[0]);
        boss.entity().setNoDamageTicks(0); double hp=boss.health(); boss.entity().damage(10);
        check(Math.abs(hp-boss.health()-10.5)<0.01 && boss.rage()==0,"Stagger clears rage and increases incoming damage 50% over normal guard");
        // Check animations and every telegraph/impact path against real Paper entities.
        for(WrathBoss.Attack attack:WrathBoss.Attack.values()) {
            call(boss,"startAttack",new Class<?>[]{WrathBoss.Attack.class,Location.class},attack,home.clone().add(0,0,8));
            call(boss,"warning",new Class<?>[0]);
            if(attack!=WrathBoss.Attack.CHARGE) call(boss,"executeImpact",new Class<?>[0]);
        }
        check(true,"All six attacks including the normal stomp execute on Paper");
        basicDamageChecks(boss,home);
        Field bladeField=WrathBoss.class.getDeclaredField("blades");bladeField.setAccessible(true);
        Object blades=bladeField.get(boss);
        @SuppressWarnings("unchecked") List<Entity> bladeEntities=(List<Entity>)call(blades,"entities",new Class<?>[0]);
        check(bladeEntities.stream().anyMatch(e->e instanceof EvokerFangs)&&bladeEntities.stream().anyMatch(e->e instanceof ItemDisplay),
                "Distant attack creates hidden Evoker fangs and custom sword displays");
        check(bladeEntities.stream().allMatch(e->!e.isPersistent()&&!e.isVisibleByDefault()),"Ground swords are temporary and native fangs are never visible");
        // The swept charge detects a one-block wall without breaking it.
        world.getBlockAt(0,home.getBlockY(),1).setType(Material.STONE,false);
        call(boss,"charge",new Class<?>[]{List.class},List.of());
        check(boss.state()==WrathBoss.State.STAGGER && world.getBlockAt(0,home.getBlockY(),1).getType()==Material.STONE,
                "Charging into a one-block wall staggers without breaking blocks");
        world.getBlockAt(0,home.getBlockY(),1).setType(Material.AIR,false);
        boss.entity().setHealth(1200.0*1024/1800);boss.entity().setNoDamageTicks(0);boss.entity().damage(9999);
        check(Math.abs(boss.health()-900)<0.01,"Massive burst cannot kill phase one or skip the transition");
        boss.tick();
        check(boss.enraged() && boss.state()==WrathBoss.State.TRANSITION,"50% HP triggers phase two and protected transition");
        boss.entity().setNoDamageTicks(0); boss.entity().damage(30);
        check(Math.abs(boss.health()-900)<0.01,"Phase transition cannot be skipped by burst damage");
        check(bladeEntities.stream().noneMatch(Entity::isValid),"Phase change cancels every pending sword and fang");
        absorptionChecks(boss,world,home);
        for(Entity e:visuals) if(e instanceof ItemDisplay display)
            check(Float.isFinite(display.getTransformation().getTranslation().y)&&display.getLocation().getYaw()==0&&display.getDisplayHeight()>=4,
                    "Stable zero-yaw anchor and full model culling bounds "+display.getItemStack().getItemMeta().getItemModel());

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
        check(visibility.getFirst().equals("hideEntity:ARMOR_STAND"),"Fallback is hidden before custom bones are shown");
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

        armorChecks(plugin,world,home);

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
        world.getChunkAt(10,0);
        boolean teleported=boss.entity().teleport(home.clone().add(160,0,0)); boss.tick();
        check(teleported&&boss.entity().isValid()&&boss.entity().getLocation().distanceSquared(home)<1&&boss.health()==1800,
                "Arena leash resets boss without editing terrain: valid="+boss.entity().isValid()+" state="+boss.state()+" location="+boss.entity().getLocation());
        @SuppressWarnings("unchecked") List<Entity> shutdownVisuals=(List<Entity>)call(boss,"visuals",new Class<?>[0]);
        Bukkit.getPluginManager().disablePlugin(plugin);
        check(!boss.entity().isValid()&&shutdownVisuals.stream().noneMatch(Entity::isValid),"Plugin disable cleans every encounter");
        check(world.getEntities().stream().noneMatch(e->e instanceof org.bukkit.entity.Item),"Admin cleanup awards no loot");
    }

    private void animationFrames() throws Exception {
        List<Object> frames=new ArrayList<>();
        for(String clip:List.of("walk","slam","stomp","sweep")) for(int tick=0;tick<=96;tick+=2) {
            WrathBoss.Attack attack=clip.equals("stomp")?WrathBoss.Attack.STOMP:clip.equals("sweep")?WrathBoss.Attack.SWEEP:WrathBoss.Attack.SLAM;
            WrathBoss.State state=clip.equals("walk")?WrathBoss.State.CHASE:tick<44?WrathBoss.State.WINDUP:tick<54?WrathBoss.State.STRIKE:WrathBoss.State.RECOVERY;
            double progress=tick<44?tick/44.0:tick<54?(tick-44)/10.0:Math.min(1,(tick-54)/36.0);
            Map<String,Object> bones=new LinkedHashMap<>();
            WrathAnimation.sample(state,attack,progress,tick*0.16,clip.equals("walk")?1:0,tick,false).forEach((id,pose)->{
                var p=pose.position();var q=pose.rotation();
                bones.put(id,Map.of("position",List.of(p.x,p.y,p.z),"rotation",List.of(q.x,q.y,q.z,q.w)));
            });
            frames.add(Map.of("clip",clip,"tick",tick,"bones",bones));
        }
        Files.writeString(getServer().getWorldContainer().toPath().resolve("wrath-animation-poses.json"),new com.google.gson.Gson().toJson(frames));
        check(true,"Exported runtime skeletal poses for walk, slam, stomp and sweep visual review");
    }

    private void basicDamageChecks(WrathBoss boss,Location home) throws Exception {
        call(boss,"startAttack",new Class<?>[]{WrathBoss.Attack.class,Location.class},WrathBoss.Attack.STOMP,home.clone().add(0,0,2));
        UUID id=UUID.randomUUID(); double[] health={100}; int[] hits={0};
        Player player=(Player)Proxy.newProxyInstance(Player.class.getClassLoader(),new Class<?>[]{Player.class},(p,m,a)->switch(m.getName()) {
            case "getUniqueId" -> id;
            case "getHealth" -> health[0];
            case "getAbsorptionAmount" -> 0.0;
            case "damage" -> { hits[0]++;health[0]-=(double)a[0];call(boss,"observeHit",new Class<?>[]{Player.class,boolean.class,double.class},p,true,a[0]);yield null; }
            case "getLocation" -> home.clone().add(0,0,2);
            case "isDead" -> false;
            case "setVelocity" -> null;
            // Any armor/inventory access fails: the ordinary attack must never apply armor break.
            default -> throw new UnsupportedOperationException("Normal stomp accessed "+m.getName());
        });
        call(boss,"hurt",new Class<?>[]{Player.class,double.class,double.class},player,10.0,0.2);
        call(boss,"hurt",new Class<?>[]{Player.class,double.class,double.class},player,10.0,0.2);
        check(hits[0]==1&&health[0]==70,"Normal stomp hits once for 30 damage without armor debuff or special durability loss");
    }

    private void absorptionChecks(WrathBoss boss,World world,Location home) throws Exception {
        while(boss.state()==WrathBoss.State.TRANSITION) boss.tick();
        check(boss.state()==WrathBoss.State.ABSORB&&boss.absorptionSecondsLeft()==15&&!boss.entity().hasAI(),
                "Phase two begins a stationary 15-second absorption window");
        Husk attacker=world.spawn(home.clone().add(10,0,0),Husk.class,e->{e.setAI(false);e.setSilent(true);});
        org.bukkit.event.Listener protection=new org.bukkit.event.Listener() {};
        Bukkit.getPluginManager().registerEvent(org.bukkit.event.entity.EntityDamageByEntityEvent.class,protection,
                org.bukkit.event.EventPriority.HIGH,(listener,event)->{
                    var hit=(org.bukkit.event.entity.EntityDamageByEntityEvent)event;
                    if(hit.getEntity().equals(boss.entity()))hit.setCancelled(true);
                },this,false);
        double before=boss.health();
        boss.entity().setNoDamageTicks(0);boss.entity().damage(40,attacker);
        check(boss.health()==before,"Protected or cancelled attacks do not heal the absorbing boss");
        org.bukkit.event.HandlerList.unregisterAll(protection);
        double[] observed={0};
        org.bukkit.event.Listener observer=new org.bukkit.event.Listener() {};
        Bukkit.getPluginManager().registerEvent(org.bukkit.event.entity.EntityDamageByEntityEvent.class,observer,
                org.bukkit.event.EventPriority.MONITOR,(listener,event)->{
                    var hit=(org.bukkit.event.entity.EntityDamageByEntityEvent)event;
                    if(hit.getEntity().equals(boss.entity()))observed[0]=hit.getFinalDamage();
                },this,false);
        boss.entity().setNoDamageTicks(0);boss.entity().damage(40,attacker);
        org.bukkit.event.HandlerList.unregisterAll(observer);
        check(boss.health()>before&&Math.abs(boss.health()-before-observed[0]*1800/1024)<0.01,
                "Incoming effective damage becomes the same amount of encounter HP");
        call(boss,"absorbDamage",new Class<?>[]{double.class},Double.MAX_VALUE);
        check(boss.health()==1800,"Absorbed damage is capped at maximum HP");
        boss.entity().setHealth(700*1024.0/1800);
        for(int i=0;i<149;i++)boss.tick();
        check(boss.absorbing()&&boss.absorptionSecondsLeft()==1,"Absorption remains active through tick 298");
        boss.tick();
        check(!boss.absorbing()&&boss.state()==WrathBoss.State.CHASE,"Absorption expires exactly after 300 server ticks");
        boss.entity().setNoDamageTicks(0);before=boss.health();boss.entity().damage(40,attacker);
        check(boss.health()<before&&boss.enraged(),"Attacks damage again after expiry and phase two remains active");
        call(boss,"absorbDamage",new Class<?>[]{double.class},100.0);
        check(boss.health()<before,"Healing above half HP does not restart absorption or phase one");
        attacker.remove();
    }

    private void armorChecks(SevenSinsPlugin plugin,World world,Location home) throws Exception {
        Husk attributes=world.spawn(home.clone().add(10,0,0),Husk.class,e->{e.setAI(false);e.setSilent(true);});
        attributes.getAttribute(Attribute.ARMOR).setBaseValue(20);
        attributes.getAttribute(Attribute.ARMOR_TOUGHNESS).setBaseValue(8);
        ItemStack[][] equipment={new ItemStack[]{new ItemStack(Material.NETHERITE_BOOTS),new ItemStack(Material.NETHERITE_LEGGINGS),
                new ItemStack(Material.NETHERITE_CHESTPLATE),new ItemStack(Material.NETHERITE_HELMET)}};
        Damageable boots=(Damageable)equipment[0][0].getItemMeta();boots.setDamage(Material.NETHERITE_BOOTS.getMaxDurability()-10);equipment[0][0].setItemMeta(boots);
        Damageable leggings=(Damageable)equipment[0][1].getItemMeta();leggings.setMaxDamage(100);equipment[0][1].setItemMeta(leggings);
        equipment[0][2].addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.UNBREAKING,3);
        var helmet=equipment[0][3].getItemMeta();helmet.setUnbreakable(true);equipment[0][3].setItemMeta(helmet);
        PlayerInventory inventory=(PlayerInventory)Proxy.newProxyInstance(PlayerInventory.class.getClassLoader(),new Class<?>[]{PlayerInventory.class},(p,m,a)->{
            if(m.getName().equals("getArmorContents"))return equipment[0].clone();
            if(m.getName().equals("setArmorContents")){equipment[0]=((ItemStack[])a[0]).clone();return null;}
            throw new UnsupportedOperationException(m.getName());
        });
        UUID id=UUID.randomUUID();
        Player player=(Player)Proxy.newProxyInstance(Player.class.getClassLoader(),new Class<?>[]{Player.class},(p,m,a)->switch(m.getName()){
            case "getUniqueId" -> id;
            case "getAttribute" -> attributes.getAttribute((Attribute)a[0]);
            case "getInventory" -> inventory;
            case "isDead" -> false;
            case "getWorld" -> world;
            case "getLocation" -> home.clone();
            case "sendActionBar" -> null;
            case "getName", "toString" -> "ArmorProbe";
            case "hashCode" -> id.hashCode();
            case "equals" -> p==a[0];
            default -> throw new UnsupportedOperationException(m.getName());
        });
        WrathArmorBreak.Trial cancelled=plugin.armorBreak().begin(player);
        check(Math.abs(attributes.getAttribute(Attribute.ARMOR).getValue()-8)<0.001
                &&Math.abs(attributes.getAttribute(Attribute.ARMOR_TOUGHNESS).getValue()-3.2)<0.001,"Armor and toughness reduced 60% by transient modifiers");
        plugin.armorBreak().finish(cancelled,false);
        check(attributes.getAttribute(Attribute.ARMOR).getValue()==20&&((Damageable)equipment[0][2].getItemMeta()).getDamage()==0,
                "Cancelled hit rolls back armor debuff and causes no extra durability loss");
        for(int cast=1;cast<=4;cast++) {
            plugin.armorBreak().finish(plugin.armorBreak().begin(player),true);
            check(Math.abs(attributes.getAttribute(Attribute.ARMOR).getValue()-8)<0.001,"Repeated cast refreshes armor reduction without stacking: "+cast);
            if(cast<4)check(((Damageable)equipment[0][2].getItemMeta()).getDamage()==148*cast
                    &&((Damageable)equipment[0][1].getItemMeta()).getDamage()==25*cast,"Exactly 25% max durability removed; custom durability respected: "+cast);
        }
        check(equipment[0][0]==null&&equipment[0][1]==null&&equipment[0][2]==null,"Depleted armor breaks and is removed from its slot");
        check(((Damageable)equipment[0][3].getItemMeta()).getDamage()==0,"Unbreakable armor remains intact");
        plugin.armorBreak().clear(player);
        check(attributes.getAttribute(Attribute.ARMOR).getValue()==20&&attributes.getAttribute(Attribute.ARMOR_TOUGHNESS).getValue()==8,
                "Cleanup restores original armor and toughness without changing base values");
        attributes.remove();
    }
}
