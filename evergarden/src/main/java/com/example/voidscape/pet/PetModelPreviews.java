package com.example.voidscape.pet;

import com.example.voidscape.VoidscapePlugin;
import com.example.voidscape.boss.JudgePackService;
import com.example.voidscape.compat.LevelledMobsCompat;
import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.event.server.*;
import org.bukkit.event.world.*;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;
import java.util.*;
import java.util.logging.Level;

/** Temporary administrator sculpture previews. No combat, pet progression or mount behavior. */
public final class PetModelPreviews implements Listener, AutoCloseable {
    interface Rig extends AutoCloseable {
        void animate(String clip);
        @Override void close();
    }
    interface Backend extends AutoCloseable {
        boolean ready();
        int imported();
        boolean hasModel(String id);
        String status();
        Rig attach(Slime shell, String modelId);
        @Override void close();
    }
    private record Preview(UUID owner, Slime shell, Rig rig, long expires) {}
    private final VoidscapePlugin plugin;
    private final Map<UUID,Preview> previews=new LinkedHashMap<>();
    private Backend backend;
    private BukkitTask maintenance;
    private String unavailable="ModelEngine ยังไม่พร้อม";
    private boolean closed;

    public PetModelPreviews(VoidscapePlugin plugin) {
        this.plugin=plugin;Bukkit.getPluginManager().registerEvents(this,plugin);reload();
        maintenance=Bukkit.getScheduler().runTaskTimer(plugin,this::tick,20,20);
    }
    public void reload() {
        invalidate();if(backend!=null){backend.close();backend=null;}
        if(!plugin.getConfig().getBoolean("pets.model-preview.enabled",true)) {unavailable="ปิดตัวอย่างสัตว์เลี้ยงใน config แล้ว";return;}
        if(!Bukkit.getPluginManager().isPluginEnabled("ModelEngine")) {unavailable="ต้องติดตั้ง ModelEngine R4 ก่อนดูโมเดลสัตว์เลี้ยง";return;}
        try {
            // Keep ModelEngine classes out of the base plugin's linkage path.
            var type=Class.forName("com.example.voidscape.pet.ModelEnginePetBackend",true,plugin.getClass().getClassLoader());
            backend=(Backend)type.getDeclaredConstructor(VoidscapePlugin.class,PetModelPreviews.class).newInstance(plugin,this);
        } catch(ReflectiveOperationException|LinkageError error) {
            unavailable="เปิด ModelEngine สำหรับสัตว์เลี้ยงไม่สำเร็จ · ดู console";
            plugin.getLogger().log(Level.WARNING,"Pet model preview backend unavailable",error);
        }
    }
    public boolean ready() {return !closed&&backend!=null&&backend.ready();}
    public int importedModels() {return backend==null?0:backend.imported();}
    public int activeCount(UUID owner) {return (int)previews.values().stream().filter(p->p.owner.equals(owner)).count();}
    public String status() {return backend==null?unavailable:backend.status();}
    private boolean admin(CommandSender sender) {return sender.isOp()||sender.hasPermission("evergarden.admin")||sender.hasPermission("voidscape.admin");}
    private int limit() {return plugin.integer("pets.model-preview.max-per-admin",18,1,51);}

    public boolean command(CommandSender sender,String[] args) {
        if(!admin(sender)) {plugin.message(sender,"คำสั่งดูโมเดลสัตว์เลี้ยงสำหรับแอดมินเท่านั้น");return true;}
        String action=args.length<2?"help":args[1].toLowerCase(Locale.ROOT);
        if(action.equals("list")&&args.length==2) {
            plugin.message(sender,"สัตว์เลี้ยง "+PetCatalog.available().size()+" ชนิด × 3 วัย = "+PetCatalog.modelIds().size()+" โมเดล · "+status());
            for(var species:PetCatalog.available())plugin.message(sender,species.id+" · "+species.display+" ("+species.thai+")");
            plugin.message(sender,"เสก: /evergarden pets spawn <id> <baby|medium|adult>");return true;
        }
        if(!(sender instanceof Player player)) {help(sender);plugin.message(sender,"เสก ดูท่าทาง และล้างตัวอย่างด้วยผู้เล่นแอดมินในเกม");return true;}
        if(action.equals("clear")&&args.length==2) {plugin.message(sender,"ล้างโมเดลตัวอย่างแล้ว "+clear(player.getUniqueId())+" ตัว");return true;}
        if(action.equals("animate")&&args.length==3) {
            String clip=args[2].toLowerCase(Locale.ROOT);
            if(!PetCatalog.ANIMATIONS.contains(clip)) {plugin.message(sender,"ท่าที่ใช้ได้: "+String.join(" · ",PetCatalog.ANIMATIONS));return true;}
            int count=0;
            for(Preview preview:new ArrayList<>(previews.values()))if(preview.owner.equals(player.getUniqueId())) {
                try {preview.rig.animate(clip);count++;}
                catch(RuntimeException|LinkageError failure) {remove(preview);plugin.getLogger().log(Level.WARNING,"Cannot animate pet preview",failure);}
            }
            plugin.message(sender,count==0?"ยังไม่มีโมเดลตัวอย่าง · ใช้ /evergarden pets spawn ก่อน":"เล่นท่า "+clip+" ให้โมเดล "+count+" ตัว");return true;
        }
        if(action.equals("spawn")&&args.length==4) {
            var species=PetCatalog.Species.find(args[2]);var stage=PetCatalog.Stage.find(args[3]);
            if(species==null||species.mythical()) {plugin.message(sender,"ไม่พบสัตว์เลี้ยง "+args[2]+" · ใช้ /evergarden pets list");return true;}
            if(stage==null) {plugin.message(sender,"ระบุวัย baby · medium · adult");return true;}
            if(!canPreview(player))return true;
            String id=species.model(stage);
            if(!backend.hasModel(id)) {missing(sender,id);return true;}
            if(activeCount(player.getUniqueId())>=limit()) {plugin.message(sender,"ครบจำนวนตัวอย่าง "+limit()+" ตัวแล้ว · ใช้ /evergarden pets clear");return true;}
            Vector facing=player.getLocation().getDirection().setY(0);
            if(facing.lengthSquared()<.001)facing=new Vector(0,0,1);else facing.normalize();
            Location spot=findSpot(player,player.getLocation().clone().add(facing.multiply(6)));
            if(spot==null) {plugin.message(sender,"ไม่มีพื้นที่ว่างใกล้ตัว · ยืนบนพื้นราบที่โล่งแล้วลองใหม่");return true;}
            if(spawn(player,species,stage,spot))plugin.message(sender,"เสก "+species.display+" · "+stage.thai+" · /evergarden pets animate greet");
            return true;
        }
        if(action.equals("growth")&&args.length==3) {
            var species=PetCatalog.Species.find(args[2]);
            if(species==null||species.mythical()) {plugin.message(sender,"ไม่พบสัตว์เลี้ยง "+args[2]+" · ใช้ /evergarden pets list");return true;}
            if(!canPreview(player))return true;
            if(limit()<3) {plugin.message(sender,"เปรียบเทียบวัยต้องตั้ง pets.model-preview.max-per-admin อย่างน้อย 3");return true;}
            Vector facing=player.getLocation().getDirection().setY(0);
            if(facing.lengthSquared()<.001)facing=new Vector(0,0,1);else facing.normalize();
            Vector right=new Vector(facing.getZ(),0,-facing.getX());List<Location> spots=new ArrayList<>();int index=0;
            for(var stage:PetCatalog.Stage.values()) {
                if(!backend.hasModel(species.model(stage))) {missing(sender,species.model(stage));return true;}
                Location wanted=player.getLocation().clone().add(facing.clone().multiply(8)).add(right.clone().multiply((index++-1)*8));
                Location spot=findSpot(player,wanted);
                if(spot==null) {plugin.message(sender,"เปรียบเทียบวัยต้องมีพื้นราบโล่งประมาณ 24×12 บล็อก · ยังไม่ได้เปลี่ยนตัวอย่างเดิม");return true;}
                spots.add(spot);
            }
            clear(player.getUniqueId());index=0;int count=0;
            for(var stage:PetCatalog.Stage.values())if(spawn(player,species,stage,spots.get(index++)))count++;
            plugin.message(sender,species.display+" · เด็ก → กลาง → โตเต็มวัย · "+count+"/3 ร่าง");return true;
        }
        if(action.equals("gallery")&&args.length==3) {
            var stage=PetCatalog.Stage.find(args[2]);
            if(stage==null) {plugin.message(sender,"ระบุวัย baby · medium · adult");return true;}
            if(!canPreview(player))return true;
            if(limit()<PetCatalog.available().size()) {plugin.message(sender,"แกลเลอรีต้องตั้ง pets.model-preview.max-per-admin อย่างน้อย "+PetCatalog.available().size());return true;}
            for(var species:PetCatalog.available())if(!backend.hasModel(species.model(stage))) {missing(sender,species.model(stage));return true;}
            List<Location> spots=new ArrayList<>();int index=0;
            for(var ignored:PetCatalog.available()) {
                // Lay the 5-wide grid out in front of the player, whichever way they face.
                double facing=Math.toRadians(player.getLocation().getYaw()),across=((index%5)-2)*8,ahead=8+(index/5)*8;
                Location wanted=player.getLocation().clone().add(-Math.sin(facing)*ahead-Math.cos(facing)*across,0,Math.cos(facing)*ahead-Math.sin(facing)*across);index++;
                Location spot=findSpot(player,wanted);
                if(spot==null) {plugin.message(sender,"แกลเลอรีต้องการพื้นราบโล่งประมาณ 40×"+(8+(PetCatalog.available().size()+4)/5*8)+" บล็อก · ยังไม่ได้เปลี่ยนตัวอย่างเดิม");return true;}
                spots.add(spot);
            }
            clear(player.getUniqueId());index=0;int count=0;
            for(var species:PetCatalog.available())if(spawn(player,species,stage,spots.get(index++)))count++;
            plugin.message(sender,"แกลเลอรี "+stage.thai+" · "+count+"/"+PetCatalog.available().size()+" ตัว · ล้างด้วย /evergarden pets clear");return true;
        }
        help(sender);return true;
    }
    private void help(CommandSender sender) {
        plugin.message(sender,"/evergarden pets list | spawn <id> <baby|medium|adult> | growth <id> | gallery <baby|medium|adult> | animate <idle|walk|fly|sit|greet> | clear");
    }
    private void missing(CommandSender sender,String id) {plugin.message(sender,"ยังไม่พบโมเดล "+id+" · รอ ModelEngine นำเข้าเสร็จ หรือใช้ /meg reload แล้วรับ resource pack ใหม่");}
    private boolean canPreview(Player player) {
        if(JudgePackService.bedrock(player)) {plugin.message(player,"ตัวอย่างชุดนี้ใช้ ModelEngine สำหรับ Java Edition · โมเดล Bedrock ยังไม่เปิดใช้งาน");return false;}
        if(!ready()) {plugin.message(player,status());return false;}
        if(player.isDead()) {plugin.message(player,"เกิดใหม่ก่อนเรียกโมเดลตัวอย่าง");return false;}
        plugin.judgePack().offer(player);return true;
    }
    private Location findSpot(Player player,Location wanted) {
        World world=player.getWorld();int x=wanted.getBlockX(),z=wanted.getBlockZ();
        if(!world.isChunkLoaded(x>>4,z>>4)||!world.getWorldBorder().isInside(wanted))return null;
        int start=Math.min(world.getMaxHeight()-6,wanted.getBlockY()+3);
        for(int y=start;y>=Math.max(world.getMinHeight()+1,wanted.getBlockY()-8);y--) {
            if(!world.getBlockAt(x,y-1,z).getType().isSolid())continue;
            boolean clear=true;
            for(int dx=-2;dx<=2&&clear;dx++)for(int dz=-2;dz<=2&&clear;dz++) {
                if(!world.isChunkLoaded((x+dx)>>4,(z+dz)>>4)) {clear=false;break;}
                for(int dy=0;dy<5;dy++)if(!world.getBlockAt(x+dx,y+dy,z+dz).isPassable()) {clear=false;break;}
            }
            if(clear) {
                Location spot=new Location(world,x+.5,y,z+.5);
                if(spot.distanceSquared(player.getLocation())>Math.pow(plugin.integer("pets.model-preview.max-distance",96,16,256),2))return null;
                spot.setYaw((float)Math.toDegrees(Math.atan2(player.getLocation().getZ()-spot.getZ(),player.getLocation().getX()-spot.getX()))-90);return spot;
            }
        }
        return null;
    }
    private boolean spawn(Player player,PetCatalog.Species species,PetCatalog.Stage stage,Location at) {
        Slime shell=null;Rig rig=null;
        try {
            shell=at.getWorld().spawn(at,Slime.class,entity->{
                entity.getPersistentDataContainer().set(plugin.key("pet_model_preview"),PersistentDataType.BYTE,(byte)1);
                LevelledMobsCompat.tagMob(entity,plugin,false);
                entity.setSize(1);entity.setAI(false);entity.setAware(false);entity.setGravity(false);
                entity.setInvulnerable(true);entity.setCollidable(false);entity.setSilent(true);
                entity.setCanPickupItems(false);entity.setPersistent(false);entity.setRemoveWhenFarAway(false);
                entity.addScoreboardTag("evergarden_unique");entity.addScoreboardTag("evergarden_pet_preview");
                entity.addScoreboardTag(species.model(stage));
            });
            if(!shell.isValid())throw new IllegalStateException("Pet preview spawn was cancelled");
            rig=backend.attach(shell,species.model(stage));
            if(rig==null)throw new IllegalStateException("ModelEngine attachment returned no rig");
            // Never leave an invisible vanilla entity behind after a failed attachment.
            shell.setInvisible(true);
            long expires=System.currentTimeMillis()+plugin.integer("pets.model-preview.lifetime-seconds",600,30,3600)*1000L;
            previews.put(shell.getUniqueId(),new Preview(player.getUniqueId(),shell,rig,expires));return true;
        } catch(RuntimeException|LinkageError error) {
            if(rig!=null)try{rig.close();}catch(RuntimeException|LinkageError ignored){}
            if(shell!=null)shell.remove();plugin.getLogger().log(Level.WARNING,"Cannot attach pet preview "+species.model(stage),error);
            plugin.message(player,"เสกโมเดลไม่สำเร็จ · ตรวจ ModelEngine และ resource pack ใน console");return false;
        }
    }
    public List<String> complete(CommandSender sender,String[] args) {
        if(!admin(sender))return List.of();List<String> result=new ArrayList<>();
        if(args.length==2)result.addAll(List.of("list","spawn","growth","gallery","animate","clear"));
        if(args.length==3&&(args[1].equalsIgnoreCase("spawn")||args[1].equalsIgnoreCase("growth")))for(var species:PetCatalog.available())result.add(species.id);
        if((args.length==4&&args[1].equalsIgnoreCase("spawn"))||(args.length==3&&args[1].equalsIgnoreCase("gallery")))for(var stage:PetCatalog.Stage.values())result.add(stage.id);
        if(args.length==3&&args[1].equalsIgnoreCase("animate"))result.addAll(PetCatalog.ANIMATIONS);
        String prefix=args.length==0?"":args[args.length-1].toLowerCase(Locale.ROOT);
        return result.stream().filter(value->value.startsWith(prefix)).toList();
    }
    private void tick() {
        long now=System.currentTimeMillis();double max=plugin.integer("pets.model-preview.max-distance",96,16,256);
        for(Preview preview:new ArrayList<>(previews.values())) {
            Player owner=Bukkit.getPlayer(preview.owner);
            if(!ready()||owner==null||!owner.isOnline()||owner.isDead()||!admin(owner)||!preview.shell.isValid()
                ||now>=preview.expires||owner.getWorld()!=preview.shell.getWorld()
                ||owner.getLocation().distanceSquared(preview.shell.getLocation())>max*max)remove(preview);
        }
    }
    public int clear(UUID owner) {
        int count=0;for(Preview preview:new ArrayList<>(previews.values()))if(preview.owner.equals(owner)){remove(preview);count++;}return count;
    }
    void invalidate() {for(Preview preview:new ArrayList<>(previews.values()))remove(preview);}
    private void remove(Preview preview) {
        previews.remove(preview.shell.getUniqueId());
        try {preview.rig.close();}catch(RuntimeException|LinkageError error){plugin.getLogger().log(Level.FINE,"Cannot close pet rig",error);}
        finally {preview.shell.remove();}
    }
    @EventHandler public void quit(PlayerQuitEvent event) {clear(event.getPlayer().getUniqueId());}
    @EventHandler public void changeWorld(PlayerChangedWorldEvent event) {clear(event.getPlayer().getUniqueId());}
    @EventHandler public void death(PlayerDeathEvent event) {clear(event.getEntity().getUniqueId());}
    @EventHandler(ignoreCancelled=true) public void unload(ChunkUnloadEvent event) {
        for(Preview preview:new ArrayList<>(previews.values())) {
            Location at=preview.shell.getLocation();
            if(at.getWorld()==event.getWorld()&&(at.getBlockX()>>4)==event.getChunk().getX()&&(at.getBlockZ()>>4)==event.getChunk().getZ())remove(preview);
        }
    }
    @EventHandler(ignoreCancelled=true) public void unloadWorld(WorldUnloadEvent event) {
        for(Preview preview:new ArrayList<>(previews.values()))if(preview.shell.getWorld()==event.getWorld())remove(preview);
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void damage(EntityDamageEvent event) {
        if(previews.containsKey(event.getEntity().getUniqueId())||(event instanceof EntityDamageByEntityEvent by&&previews.containsKey(by.getDamager().getUniqueId())))event.setCancelled(true);
    }
    @EventHandler public void target(EntityTargetEvent event) {if(previews.containsKey(event.getEntity().getUniqueId()))event.setCancelled(true);}
    @EventHandler public void split(SlimeSplitEvent event) {if(previews.containsKey(event.getEntity().getUniqueId()))event.setCancelled(true);}
    @EventHandler public void interact(PlayerInteractEntityEvent event) {if(previews.containsKey(event.getRightClicked().getUniqueId()))event.setCancelled(true);}
    @EventHandler public void disable(PluginDisableEvent event) {
        if(event.getPlugin().getName().equalsIgnoreCase("ModelEngine")) {invalidate();if(backend!=null){backend.close();backend=null;}unavailable="ModelEngine ถูกปิด · เปิด ModelEngine แล้วลองใหม่";}
    }
    @EventHandler public void enable(PluginEnableEvent event) {if(event.getPlugin().getName().equalsIgnoreCase("ModelEngine")&&!closed)reload();}
    @Override public void close() {closed=true;invalidate();if(maintenance!=null)maintenance.cancel();if(backend!=null){backend.close();backend=null;}HandlerList.unregisterAll(this);}
}
