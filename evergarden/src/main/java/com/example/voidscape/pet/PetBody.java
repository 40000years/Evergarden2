package com.example.voidscape.pet;

import com.example.voidscape.VoidscapePlugin;
import com.example.voidscape.compat.LevelledMobsCompat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.FluidCollisionMode;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.Rabbit;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;
import java.util.*;
import java.util.logging.Level;

/**
 * One pet in the world, wild or companion: a hidden, harmless receiver that takes clicks,
 * the Java display rig riding it, and the Bedrock carrier Geyser swaps for the native model.
 * Brains ({@link WildPetSpawns}, {@link PetCompanions}) choose a mode; the body moves and
 * shows the matching authored clip on both platforms.
 */
final class PetBody {
    enum Mode {IDLE,WALK,REST,GREET,FLY,HOVER,LAND}
    static final List<String> CLIPS=List.of("idle","walk","fly","sit","greet");
    final VoidscapePlugin plugin;
    final Rabbit shell;
    final ArmorStand carrier;
    final NativePetRenderer.Rig rig;
    final TextDisplay nametag;
    final PetCatalog.Species species;
    final PetCatalog.Stage stage;
    /** A small body that sits on its owner's head; its receiver is never shown, so it cannot block clicks. */
    boolean perched;
    /** Assist mode: what it is fighting, when it may bite again and whether this target was struck yet. */
    org.bukkit.entity.LivingEntity target;
    int nextStrike,struck;
    boolean ambushed;
    /** Companion identity; both null while the pet is wild. */
    UUID owner;
    PetRecord record;
    final Set<UUID> bedrockViewers=new HashSet<>();
    Mode mode=Mode.IDLE;
    int modeUntil,greetReady,airborneSince,stuck;
    boolean airborne;
    /** Ground pets use the same custom motion as their rig, including unsupported falls. */
    boolean falling;
    private double fallSpeed;
    double hover;
    float yaw,targetYaw,pitch,roll;
    /** Independent head attention; yaw uses Bukkit-relative degrees and positive pitch looks up. */
    float headYaw,headPitch;
    UUID attention;
    /** Current ground or air speed in blocks per decision step; eased up and down, never snapped. */
    double speed;
    String clip="idle";
    float rate=1;
    private String bedrockClip="";
    private float bedrockRate=1;
    private float bedrockHeadYaw=Float.NaN,bedrockHeadPitch=Float.NaN;
    private Location glideFrom,glideTo;
    private int glideStep,glideSteps;
    // Wild habitat and taming progress.
    Location home;
    /** When a wild pet may leave, and the latest it lingers while someone is close enough to watch it go. */
    long expires,deadline;
    UUID tamer;
    long tamerUntil,lastFeed;
    int trust;

    private PetBody(VoidscapePlugin plugin,Rabbit shell,ArmorStand carrier,NativePetRenderer.Rig rig,TextDisplay nametag,PetCatalog.Species species,PetCatalog.Stage stage){
        this.plugin=plugin;this.shell=shell;this.carrier=carrier;this.rig=rig;this.nametag=nametag;this.species=species;this.stage=stage;
        home=shell.getLocation();yaw=targetYaw=home.getYaw();
    }
    boolean wild(){return owner==null;}
    boolean valid(){return shell.isValid()&&!shell.isDead()&&(carrier==null||carrier.isValid());}
    Location location(){return shell.getLocation();}

    /** A clicked or befriending player keeps the pet's attention while they remain in sight. */
    void notice(Player player){attention=player==null?null:player.getUniqueId();}
    Player watch(Player preferred,Player nearest,double range){
        if(watchable(preferred,range)){notice(preferred);return preferred;}
        // Companions attend to their owner; wild pets retain one observer rather than flickering
        // between several players as their distances cross.
        if(owner!=null){attention=null;return null;}
        Player previous=attention==null?null:Bukkit.getPlayer(attention);
        if(watchable(previous,range))return previous;
        if(watchable(nearest,range)){notice(nearest);return nearest;}
        Player chosen=null;double distance=range*range;
        Location at=location();
        for(Player player:shell.getWorld().getPlayers()){
            if(!watchable(player,range))continue;
            double candidate=player.getLocation().distanceSquared(at);
            if(candidate<distance){distance=candidate;chosen=player;}
        }
        notice(chosen);return chosen;
    }
    private boolean watchable(Player player,double range){
        return player!=null&&player.isOnline()&&!player.isDead()&&player.getGameMode()!=GameMode.SPECTATOR
            &&player.getWorld()==shell.getWorld()&&player.getLocation().distanceSquared(location())<=range*range
            &&shell.hasLineOfSight(player);
    }
    /** The head acquires a live target first; the body catches up without twisting its neck around. */
    void steer(Player look,boolean moving,int ticks,float bodyLimit){
        Location at=location();float lookYaw=yaw,lookPitch=0;
        if(look!=null){
            Location eyes=look.getEyeLocation();double dx=eyes.getX()-at.getX(),dz=eyes.getZ()-at.getZ();
            double horizontal=Math.sqrt(dx*dx+dz*dz);
            if(horizontal>.05)lookYaw=WildPetSpawns.yawTowards(dx,dz);
            double headHeight=switch(stage){case BABY->.35;case MEDIUM->.55;case ADULT->.75;};
            if(record!=null)headHeight*=record.size;
            lookPitch=PetAttention.targetPitch(eyes.getY()-(at.getY()+headHeight),horizontal)-pitch;
            if(!moving)targetYaw=lookYaw;
        }
        float before=yaw,difference=PetAttention.wrap(targetYaw-yaw);
        float bodyEase=(float)(1-Math.pow(.65,ticks/2.0));
        yaw=PetAttention.approachYaw(yaw,targetYaw,Math.min(bodyLimit,Math.abs(difference)*bodyEase));
        // Preserve the head's world heading while its body turns beneath it.
        // Some heads are most of the animal (a jelly's bell, a koi's front): they barely turn.
        float headLimit=switch(species){case ASTRAL_OWL->60;case ABYSSAL_MANTA,CELESTIAL_KOI->20;case AURORA_JELLY->15;default->45;};
        if(moving)headLimit=Math.min(headLimit,45);
        headYaw=Math.clamp(headYaw-PetAttention.wrap(yaw-before),-headLimit,headLimit);
        float desiredYaw=look==null?0:Math.clamp(PetAttention.headYaw(lookYaw,yaw,moving),-headLimit,headLimit);
        float pitchLimit=switch(species){case ABYSSAL_MANTA,AURORA_JELLY,CELESTIAL_KOI->12;default->25;};
        float desiredPitch=look==null?0:Math.clamp(lookPitch,-pitchLimit,pitchLimit);
        float headEase=(float)(1-Math.pow(.25,ticks/2.0));
        headYaw+=(desiredYaw-headYaw)*headEase;headPitch+=(desiredPitch-headPitch)*headEase;
    }

    /** Spawns receiver, rig and (with Geyser's entity API) the Bedrock carrier, or nothing at all. */
    static PetBody spawn(VoidscapePlugin plugin,NativePetRenderer renderer,PetCatalog.Species species,PetCatalog.Stage stage,
                         Location at,PetRecord record,UUID owner){
        return spawn(plugin,renderer,species,stage,at,record,owner,0);
    }
    /** {@code perch} above zero builds the small head-sitting body at that fraction of its size. */
    static PetBody spawn(VoidscapePlugin plugin,NativePetRenderer renderer,PetCatalog.Species species,PetCatalog.Stage stage,
                         Location at,PetRecord record,UUID owner,float perch){
        Rabbit shell=null;NativePetRenderer.Rig rig=null;ArmorStand carrier=null;TextDisplay nametag=null;
        boolean bedrock=plugin.arkhaiaBedrock()!=null&&plugin.arkhaiaBedrock().ready();
        try{
            shell=at.getWorld().spawn(at,Rabbit.class,CreatureSpawnEvent.SpawnReason.CUSTOM,entity->{
                var data=entity.getPersistentDataContainer();
                data.set(plugin.key("pet_body"),PersistentDataType.BYTE,(byte)1);
                if(owner==null)data.set(plugin.key("wild_pet"),PersistentDataType.BYTE,(byte)1);
                else data.set(plugin.key("pet_owner"),PersistentDataType.STRING,owner.toString());
                data.set(plugin.key("wild_pet_species"),PersistentDataType.STRING,species.id);
                data.set(plugin.key("wild_pet_stage"),PersistentDataType.STRING,stage.id);
                LevelledMobsCompat.tagMob(entity,plugin,false);
                entity.addScoreboardTag("evergarden_unique");entity.addScoreboardTag(owner==null?"evergarden_wild_pet":"evergarden_companion");
                entity.setAI(false);entity.setAware(false);entity.setGravity(false);
                entity.setInvulnerable(true);entity.setCollidable(false);entity.setSilent(true);
                entity.setCanPickupItems(false);entity.setPersistent(false);entity.setRemoveWhenFarAway(false);
                entity.setBreed(false);entity.setAgeLock(true);entity.setAdult();
                // Each viewer is shown or culled by PetBodies.
                entity.setVisibleByDefault(false);
                entity.customName(Component.text((record!=null?record.name()+" · ":"")+species.display+" · "+stage.thai,NamedTextColor.AQUA));
                entity.setCustomNameVisible(false);
            });
            if(!shell.isValid()){shell.remove();return null;}
            float size=record!=null?record.size:1;
            rig=perch>0?renderer.attach(shell,species,stage,size*perch,false):renderer.attach(shell,species,stage,size);
            if(rig==null)throw new IllegalStateException("Native pet renderer is not ready");
            // Each Java viewer seats a perched body on the owner's head itself (PetBodies.seat).
            if(perch>0)rig.carry();
            shell.setInvisible(true);
            if(bedrock){
                UUID receiver=shell.getUniqueId();String model=species.model(stage);
                // Geyser swaps this carrier for the native Bedrock pet; Java clients never receive it.
                carrier=at.getWorld().spawn(at,ArmorStand.class,CreatureSpawnEvent.SpawnReason.CUSTOM,e->{
                    e.setPersistent(false);e.setGravity(false);e.setInvulnerable(true);e.setSilent(true);e.setVisibleByDefault(false);
                    e.setVisible(true);e.setBasePlate(false);e.setCollidable(false);
                    e.getPersistentDataContainer().set(plugin.key("pet_body"),PersistentDataType.BYTE,(byte)1);
                    if(owner==null)e.getPersistentDataContainer().set(plugin.key("wild_pet"),PersistentDataType.BYTE,(byte)1);
                    e.getPersistentDataContainer().set(plugin.key("wild_pet_visual"),PersistentDataType.STRING,receiver.toString());
                    float clickWidth=switch(stage){case BABY->.9f;case MEDIUM->1.4f;case ADULT->1.8f;};
                    float clickHeight=switch(stage){case BABY->1.05f;case MEDIUM->1.5f;case ADULT->1.9f;};
                    plugin.arkhaiaBedrock().register(e.getUniqueId(),model,
                        perch>0?0:clickWidth*size,perch>0?0:clickHeight*size);
                    for(var slot:EquipmentSlot.values())e.addEquipmentLock(slot,ArmorStand.LockType.ADDING_OR_CHANGING);
                    var scale=e.getAttribute(Attribute.SCALE);
                    if(perch>0&&scale!=null)scale.setBaseValue(perch);
                });
                if(!carrier.isValid())throw new IllegalStateException("Bedrock pet carrier was cancelled");
            }
            final UUID shellId=shell.getUniqueId();
            if(perch<=0){
                float tagHeight=(switch(stage){case BABY->0.75f;case MEDIUM->1.15f;case ADULT->1.55f;})*size+0.15f;
                nametag=at.getWorld().spawn(at,TextDisplay.class,e->{
                    e.setPersistent(false);e.setBillboard(Display.Billboard.CENTER);
                    e.setAlignment(TextDisplay.TextAlignment.CENTER);
                    e.setViewRange(0.8f);e.setShadowed(true);e.setDefaultBackground(true);
                    e.setVisibleByDefault(false);
                    e.getPersistentDataContainer().set(plugin.key("pet_body"),PersistentDataType.BYTE,(byte)1);
                    e.getPersistentDataContainer().set(plugin.key("wild_pet_visual"),PersistentDataType.STRING,shellId.toString());
                    e.setTransformation(new Transformation(
                        new Vector3f(0,tagHeight,0),new Quaternionf(),new Vector3f(0.85f),new Quaternionf()
                    ));
                });
                shell.addPassenger(nametag);
            }
            PetBody body=new PetBody(plugin,shell,carrier,rig,nametag,species,stage);
            body.owner=owner;body.record=record;body.perched=perch>0;
            body.updateNametag();
            return body;
        }catch(RuntimeException|LinkageError error){
            if(nametag!=null)nametag.remove();
            if(rig!=null)try{rig.close();}catch(RuntimeException|LinkageError ignored){}
            if(carrier!=null){plugin.arkhaiaBedrock().unregister(carrier.getUniqueId());carrier.remove();}
            if(shell!=null)shell.remove();
            plugin.getLogger().log(Level.WARNING,"Cannot spawn pet "+species.model(stage),error);return null;
        }
    }
    /** Jumps straight there (relocations); the rig rides along. */
    void move(Location to){glideTo=null;falling=false;fallSpeed=0;place(to,yaw);}
    /**
     * Perched, every tick: the hidden receiver and the Bedrock carrier sit on the head. Java
     * parts are carried by each viewer's client, so the server moves them only when they drift.
     */
    void perchAt(Location head){
        glideTo=null;
        Location receiver=head.clone();receiver.setYaw(shell.getLocation().getYaw());receiver.setPitch(0);
        shell.teleport(receiver,io.papermc.paper.entity.TeleportFlag.EntityState.RETAIN_PASSENGERS);
        Location to=head.clone();to.setYaw(yaw);to.setPitch(0);
        if(carrier!=null)carrier.teleport(to);
        rig.place(to);
    }
    /**
     * Travels to {@code to} over {@code ticks}, one small teleport per tick. Mobs only smooth a
     * move over three client ticks, so one big step every few ticks looks like stop-and-go.
     */
    void glide(Location to,int ticks){
        glideFrom=location();glideTo=to.clone();
        if(!airborne&&!perched){
            // Planners use block Y; partial blocks need their actual collision top.
            var floor=support(glideTo,1.08);
            if(floor!=null)glideTo.setY(floor.getHitPosition().getY());
        }
        glideStep=0;glideSteps=Math.max(1,ticks);
        tickGlide();
    }
    /** Where the body is heading: decisions build on this, not on a half-finished glide. */
    Location destination(){return glideTo!=null?glideTo.clone():location();}
    /**
     * Gravity for bodies whose native physics are disabled. Inspect collision shapes each tick,
     * so idle, resting and befriending pets fall as soon as their support is removed. Deliberate
     * flight and head perches retain their own motion. Both renderers move with the receiver.
     */
    boolean tickGrounding(){
        if(perched||airborne||!valid()){falling=false;fallSpeed=0;return false;}
        Location at=location();
        if(!PetTerrain.loadedFootprint(at.getWorld(),at.getBlockX(),at.getBlockZ()))return falling;
        // Short authored steps can lift the feet above the old floor. Let a valid step finish,
        // but invalidate it when its destination no longer has support.
        if(!falling&&glideTo!=null&&Math.abs(glideTo.getY()-at.getY())<=1.05
            &&glideTo.distanceSquared(at)<=2.25&&support(glideTo,.08)!=null)return false;
        var floor=support(at,falling?Math.min(3.92,fallSpeed+.08)+.06:.08);
        if(!falling&&floor!=null){fallSpeed=0;return false;}
        falling=true;glideTo=null;speed=0;pitch=roll=0;
        fallSpeed=Math.min(3.92,(fallSpeed+.08)*.98);
        // Trace the entire downward step; a narrow ledge or slab cannot be skipped at speed.
        floor=support(at,fallSpeed+.06);
        Location next=at.clone();
        if(floor!=null){
            next.setY(floor.getHitPosition().getY());falling=false;fallSpeed=0;
        }else next.setY(Math.max(at.getWorld().getMinHeight()+1,at.getY()-fallSpeed));
        place(next,yaw);return falling;
    }
    private static org.bukkit.util.RayTraceResult support(Location at,double depth){
        return at.getWorld().rayTraceBlocks(at.clone().add(0,.03,0),new org.bukkit.util.Vector(0,-1,0),
            depth,FluidCollisionMode.NEVER,true);
    }
    void tickGlide(){
        if(glideTo==null)return;
        double k=Math.min(1,(double)++glideStep/glideSteps);
        Location at=glideFrom.clone().add(glideTo.clone().subtract(glideFrom).toVector().multiply(k));
        // Facing is authoritative even if the brain turned after this stride was planned.
        place(at,yaw);
        if(k>=1)glideTo=null;
    }
    private void place(Location to,float facing){
        // Teleporting a vehicle with a new yaw also rotates its passengers on Paper.
        // The rig already applies facing in its transformation, so translate at the
        // receiver's current rotation and turn it separately to keep display yaw zero.
        Location receiver=to.clone(),previous=shell.getLocation();
        receiver.setYaw(previous.getYaw());receiver.setPitch(previous.getPitch());
        shell.teleport(receiver,io.papermc.paper.entity.TeleportFlag.EntityState.RETAIN_PASSENGERS);
        shell.setRotation(facing,0);
        to.setYaw(facing);to.setPitch(0);
        if(carrier!=null)carrier.teleport(to);
        rig.place(to);
    }
    /** Plays a clip and attention pose; Bedrock look updates are quantized to avoid tiny resends. */
    void present(String next){present(next,1);}
    void present(String next,float pace){
        clip=next;rate=Math.round(Math.clamp(pace,.25f,2f)*4)/4f;
        // Stationary animals also rotate their receiver/carrier, rather than waiting for a glide.
        if(Math.abs(PetAttention.wrap(shell.getLocation().getYaw()-yaw))>.25f)shell.setRotation(yaw,0);
        if(carrier!=null&&Math.abs(PetAttention.wrap(carrier.getLocation().getYaw()-yaw))>.25f)carrier.setRotation(yaw,0);
        rig.tick(next,yaw,pitch,roll,pace,headYaw,headPitch);
        float lookYaw=Math.round(headYaw/2)*2f,lookPitch=Math.clamp(Math.round(headPitch/2)*2f,-25,25);
        if(carrier==null||next.equals(bedrockClip)&&rate==bedrockRate&&lookYaw==bedrockHeadYaw&&lookPitch==bedrockHeadPitch)return;
        bedrockClip=next;bedrockRate=rate;bedrockHeadYaw=lookYaw;bedrockHeadPitch=lookPitch;
        List<Player> viewers=new ArrayList<>();
        for(UUID id:bedrockViewers){Player p=plugin.getServer().getPlayer(id);if(p!=null)viewers.add(p);}
        if(!viewers.isEmpty())plugin.arkhaiaBedrock().petFrame(carrier.getUniqueId(),next,0,rate,lookYaw,lookPitch,viewers);
    }
    /** A Bedrock viewer that just started tracking the carrier receives the current clip. */
    void syncBedrock(Player viewer){
        if(carrier!=null&&plugin.arkhaiaBedrock()!=null)plugin.arkhaiaBedrock().petFrame(carrier.getUniqueId(),clip,0,rate,
            Math.round(headYaw/2)*2f,Math.clamp(Math.round(headPitch/2)*2f,-25,25),List.of(viewer));
    }
    void remove(){
        try{rig.close();}
        finally{
            if(nametag!=null)nametag.remove();
            shell.remove();
            if(carrier!=null){
                if(plugin.arkhaiaBedrock()!=null)plugin.arkhaiaBedrock().unregister(carrier.getUniqueId());
                carrier.remove();
            }
            bedrockViewers.clear();
        }
    }
    /** Hands a tamed wild body to its new owner without respawning any part. */
    void adopt(UUID newOwner,PetRecord newRecord){
        owner=newOwner;record=newRecord;tamer=null;trust=0;
        var data=shell.getPersistentDataContainer();
        data.remove(plugin.key("wild_pet"));data.set(plugin.key("pet_owner"),PersistentDataType.STRING,newOwner.toString());
        shell.removeScoreboardTag("evergarden_wild_pet");shell.addScoreboardTag("evergarden_companion");
        shell.customName(Component.text(newRecord.name()+" · "+species.display+" · "+stage.thai,NamedTextColor.AQUA));
        if(carrier!=null)carrier.getPersistentDataContainer().remove(plugin.key("wild_pet"));
        updateNametag();
    }
    void updateNametag(){
        Component title;
        if(wild()){
            TextColor titleColor=species.mythical()?NamedTextColor.GOLD:NamedTextColor.AQUA;
            Component line1=Component.text("✦ "+species.thai+" · "+stage.thai,titleColor)
                .append(Component.text(" ("+species.display+")",NamedTextColor.GRAY));
            Component line2;
            if(trust>=100){
                if(stage==PetCatalog.Stage.BABY)line2=Component.text("✔ พร้อมผูกมิตร! (คลิกขวา)",NamedTextColor.GREEN);
                else line2=Component.text("✔ ไว้ใจแล้ว! (ต้องใช้เชือกจูง Lead พากลับ)",NamedTextColor.YELLOW);
            }else if(trust>0){
                Player tamerPlayer=tamer!=null?Bukkit.getPlayer(tamer):null;
                String tamerName=tamerPlayer!=null?tamerPlayer.getName():"ผู้เล่นอื่น";
                line2=Component.text("ความไว้ใจ: ",NamedTextColor.LIGHT_PURPLE)
                    .append(Component.text(PetTaming.bar(trust)+" "+trust+"%",NamedTextColor.WHITE))
                    .append(Component.text(" ("+tamerName+")",NamedTextColor.GRAY));
            }else{
                Material food=PetTaming.defaultFood(species);
                String foodStr=PetTaming.foodNameThai(food);
                line2=Component.text("อาหาร: ",NamedTextColor.GOLD)
                    .append(Component.text(foodStr,NamedTextColor.YELLOW))
                    .append(Component.text(" · คลิกขวาดูวิธีเทม",NamedTextColor.GRAY));
            }
            title=line1.append(Component.newline()).append(line2);
            if(carrier!=null&&carrier.isValid()){
                carrier.customName(Component.text("✦ "+species.thai+" · "+stage.thai+" ("+PetTaming.foodNameThai(PetTaming.defaultFood(species))+")",NamedTextColor.AQUA));
                carrier.setCustomNameVisible(true);
            }
        }else{
            String petName=record!=null?record.name():species.thai;
            TextColor nameColor=record!=null&&record.aura?NamedTextColor.GOLD:NamedTextColor.AQUA;
            Component line1=Component.text(petName,nameColor).append(Component.text(" · "+stage.thai,NamedTextColor.GRAY));
            Player ownerPlayer=owner!=null?Bukkit.getPlayer(owner):null;
            String ownerName=ownerPlayer!=null?ownerPlayer.getName():"ผู้เล่น";
            String modeName=record!=null?PetSkills.modeName(record.mode):"เดินตาม";
            Component line2=Component.text("สัตว์เลี้ยงของ ",NamedTextColor.DARK_AQUA)
                .append(Component.text(ownerName,NamedTextColor.WHITE))
                .append(Component.text(" · "+modeName,NamedTextColor.YELLOW));
            title=line1.append(Component.newline()).append(line2);
            if(carrier!=null&&carrier.isValid()){
                carrier.customName(Component.text(petName+" · สัตว์เลี้ยงของ "+ownerName,NamedTextColor.AQUA));
                carrier.setCustomNameVisible(true);
            }
        }
        if(nametag!=null&&nametag.isValid())nametag.text(title);
    }
}
