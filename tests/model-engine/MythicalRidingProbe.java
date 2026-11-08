import com.example.voidscape.pet.*;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.world.entity.player.Input;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.entity.*;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionEffect;
import java.util.*;

/** Existing 519 pet/egg checks plus real mounted movement and lifecycle tests for every mythical. */
public class MythicalRidingProbe extends MythicalPetProbe {
    @Override String suiteName(){return "pets, eggs, flying mounts, Fenrir ground physics and Aetherius blessing";}
    @Override void mythicalChecks()throws Exception{super.mythicalChecks();ridingChecks();}
    void input(boolean f,boolean b,boolean l,boolean r,boolean jump,boolean sprint){
        actorA.connection.handlePlayerInput(new ServerboundPlayerInputPacket(new Input(f,b,l,r,jump,false,sprint)));
    }
    void drive(Object riding,Object body,int ticks)throws Exception{
        for(int i=0;i<ticks;i++){
            Object previous=session(riding);Entity previousSeat=previous==null?null:(Entity)get(previous,"seat");
            String before="sameBody="+(previous!=null&&get(previous,"body")==body)+", seatValid="+(previousSeat!=null&&previousSeat.isValid())
                +", vehicleMatches="+(tamer.getVehicle()==previousSeat)+", sameWorld="+(previousSeat!=null&&tamer.getWorld()==previousSeat.getWorld());
            boolean active=(boolean)call(riding,"tick",tamer,body);
            check(active,"mounted tick remains active (before="+before+", session="+session(riding)+", vehicle="+tamer.getVehicle()+", body="+call(body,"valid")+
                ", enabled="+garden.getConfig().getBoolean("pets.riding.enabled")+", dead="+tamer.isDead()+", mode="+tamer.getGameMode()+")");
            Entity seat=tamer.getVehicle();
            ((CraftEntity)seat).getHandle().positionRider(actorA);
        }
    }
    Object session(Object riding)throws Exception{return ((Map<?,?>)get(riding,"sessions")).get(tamer.getUniqueId());}
    void ridingChecks()throws Exception{
        Object riding=call(companions,"riding"),stable=stable(tamer),menu=get(pets,"menu");
        put(stable,"slots",28);tamer.getInventory().clear();tamer.teleport(home);
        // The old flight fixture only cleared eight blocks above the floor.
        // A six-block jump also needs the mounted rider's three-block headroom.
        for(int x=home.getBlockX()-5;x<=home.getBlockX()+5;x++)for(int z=home.getBlockZ()-5;z<=home.getBlockZ()+55;z++)
            for(int y=128;y<=143;y++)world.getBlockAt(x,y,z).setType(Material.AIR,false);
        var console=Bukkit.getConsoleSender();
        for(var species:WildPetSpawns.mythicalSpecies())garden.pets().command(console,new String[]{"pets","give",tamer.getName(),species.id,"adult"});
        PetRecord ordinary=records(tamer).values().stream().filter(r->!r.species().mythical()&&r.stage()==PetCatalog.Stage.ADULT).findFirst().orElseThrow();
        PetRecord baby=records(tamer).values().stream().filter(r->r.species().mythical()&&r.stage()==PetCatalog.Stage.BABY).findFirst().orElseThrow();
        check(call(riding,"start",tamer,ordinary.id())!=null&&!tamer.isInsideVehicle(),"ordinary pets cannot be mounted");
        check(call(riding,"start",tamer,baby.id())!=null&&!tamer.isInsideVehicle(),"mythical babies must grow before mounting");
        boolean flight=tamer.getAllowFlight();magic.mana().account(tamer).setMana(0);
        List<PetRecord> adults=records(tamer).values().stream().filter(r->r.species().mythical()&&r.stage()==PetCatalog.Stage.ADULT).toList();
        check(adults.size()==5,"all five mythical adult mounts are in the fixture");
        int teleportsBefore=teleports;
        Bukkit.getPluginManager().registerEvents(new org.bukkit.event.Listener(){
            @org.bukkit.event.EventHandler public void teleported(PlayerTeleportEvent event){if(event.getPlayer()==tamer){teleports++;getLogger().info("Ride fixture teleport "+event.getCause());}}
        },this);
        for(PetRecord adult:adults){
            input(false,false,false,false,false,false);tamer.teleport(home);tamer.setRotation(0,0);
            check(selectForMount(adult)==null,"summon "+adult.species().id+" for mounting");
            check(call(riding,"start",rival,adult.id())!=null,"another player cannot mount "+adult.species().id);
            check(call(riding,"start",tamer,adult.id())==null,"mount "+adult.species().id+" with zero mana");
            Object body=body(tamer);Entity seat=tamer.getVehicle();
            check(seat instanceof ArmorStand&&(boolean)get(body,"ridden"),"real invisible seat carries the owner of "+adult.species().id);
            check(!seat.isPersistent()&&!seat.hasGravity(),"ride seat is transient and has no gravity");
            Object mounted=session(riding);
            ((CraftEntity)seat).getHandle().positionRider(actorA);
            check(Math.abs(tamer.getY()-((Rabbit)get(body,"shell")).getY()-(double)get(mounted,"lift"))<.02,
                "rider feet align with the configured back height on "+adult.species().id);
            Location from=((Rabbit)get(body,"shell")).getLocation();int before=teleports;
            input(true,false,false,false,false,false);
            check(tamer.getVehicle()==seat&&session(riding)!=null,"input leaves the mounted session intact");
            drive(riding,body,14);
            Location moved=((Rabbit)get(body,"shell")).getLocation();
            check(moved.getZ()>from.getZ()+4,"forward input flies "+adult.species().id);
            check(teleports==before,"ordinary mounted movement fires no player teleport events");
            boolean fenrir=adult.species()==PetCatalog.Species.FENRIR;
            check((fenrir?"walk":"fly").equals(get(body,"clip")),"mounted "+adult.species().id+" uses its movement animation");
            input(false,false,false,false,true,false);double y=moved.getY();drive(riding,body,8);
            check(((Rabbit)get(body,"shell")).getY()>y+1,"jump lifts "+adult.species().id);
            if(fenrir){
                double peak=((Rabbit)get(body,"shell")).getY();
                drive(riding,body,8);peak=Math.max(peak,((Rabbit)get(body,"shell")).getY());
                input(false,false,false,false,false,false);drive(riding,body,1);
                input(false,false,false,false,true,false);double falling=((Rabbit)get(body,"shell")).getY();drive(riding,body,7);
                check(((Rabbit)get(body,"shell")).getY()<falling,"Fenrir cannot jump again in midair");
                check(peak-y>5&&peak-y<7,"Fenrir high jump peaks between five and seven blocks (rise="+(peak-y)+", base="+y+", peak="+peak+")");
                input(false,false,false,false,false,false);drive(riding,body,30);
                double floor=((Rabbit)get(body,"shell")).getY();drive(riding,body,15);
                check(Math.abs(((Rabbit)get(body,"shell")).getY()-floor)<.1&&floor<y+.2,"Fenrir lands and never hovers");
                check("idle".equals(get(body,"clip"))&&!(boolean)get(body,"airborne"),"stationary Fenrir returns to grounded idle");
                tamer.setRotation(0,-60);input(true,false,false,false,false,true);
                var motion=(org.bukkit.util.Vector)call(riding,"input",tamer,mounted);
                check(motion.getY()==0&&Math.abs(motion.length()-1.05)<1e-6,"Fenrir sprint is 21 blocks/second and looking up cannot fly");
                tamer.setRotation(0,60);motion=(org.bukkit.util.Vector)call(riding,"input",tamer,mounted);
                check(motion.getY()==0,"looking down does not make Fenrir fly down");
                groundObstacles(riding,body);
            }else{
                tamer.setRotation(0,60);input(true,false,false,false,false,false);y=((Rabbit)get(body,"shell")).getY();drive(riding,body,8);
                check(((Rabbit)get(body,"shell")).getY()<y-.5,"looking down while moving forward descends on "+adult.species().id);
            }
            input(false,false,false,false,false,false);drive(riding,body,12);
            check(magic.mana().account(tamer).manaExact()==0,"flight on "+adult.species().id+" never spends mana and works at zero");
            check(tamer.getAllowFlight()==flight,"mount does not grant creative flight on "+adult.species().id);
            call(companions,"follow");
            check(tamer.getVehicle()==seat&&(boolean)get(body,"ridden"),"normal companion AI leaves the active mount under rider control");
            call(riding,"stop",tamer.getUniqueId(),true);
            check(!tamer.isInsideVehicle()&&!seat.isValid()&&!(boolean)get(body,"ridden"),"dismount removes the seat and resumes "+adult.species().id);
            check(tamer.hasPotionEffect(PotionEffectType.SLOW_FALLING),"air dismount grants safe descent on "+adult.species().id);
        }
        blessingChecks(riding,adults);
        input(false,false,false,false,false,false);tamer.teleport(home);tamer.setRotation(0,0);
        PetRecord mount=adults.getFirst();selectForMount(mount);
        call(menu,"chestPet",tamer,mount.id());check(actions().containsKey(16),"mythical pet card offers the saddle action");
        actions().get(16).run();check(tamer.isInsideVehicle(),"saddle button boards a real mount");
        Object body=body(tamer),mounted=session(riding);
        input(true,false,false,false,false,false);org.bukkit.util.Vector normal=(org.bukkit.util.Vector)call(riding,"input",tamer,mounted);
        input(true,false,false,false,false,true);org.bukkit.util.Vector sprint=(org.bukkit.util.Vector)call(riding,"input",tamer,mounted);
        check(Math.abs(sprint.length()/normal.length()-1.5)<1e-9,"sprint gives the configured balanced speed boost");
        input(true,false,false,false,false,false);
        ((Map<UUID,Long>)get(companions,"moved")).put(tamer.getUniqueId(),System.currentTimeMillis()-6*60_000L);
        drive(riding,body,1);call(companions,"maintain");check(tamer.isInsideVehicle(),"active rider input prevents false AFK suspension");

        Location at=((Rabbit)get(body,"shell")).getLocation();int wallZ=at.getBlockZ()+3;
        Map<Block,BlockData> blocks=new HashMap<>();
        try{
            for(int x=at.getBlockX()-3;x<=at.getBlockX()+3;x++)for(int y=at.getBlockY();y<=at.getBlockY()+5;y++){
                Block block=world.getBlockAt(x,y,wallZ);blocks.put(block,block.getBlockData());block.setType(Material.STONE,false);
            }
            drive(riding,body,24);
            check(((Rabbit)get(body,"shell")).getZ()<wallZ-.6,"swept flight stops before a wall and never clips the rider through it");
        }finally{blocks.forEach((block,data)->block.setBlockData(data,false));}
        Location unknown=at.clone().add(100000,0,100000);int chunks=world.getLoadedChunks().length;
        check(!(boolean)call(riding,"clear",unknown,1d,tamer)&&world.getLoadedChunks().length==chunks,"flight bounds reject unloaded terrain without loading chunks");

        Entity seat=tamer.getVehicle();seat.removePassenger(tamer);
        call(riding,"tick",tamer,body);
        check(!(boolean)call(riding,"riding",tamer.getUniqueId())&&!seat.isValid(),"a natural sneak dismount cleans the session and seat");
        java.lang.reflect.Field platformField=com.example.voidscape.boss.JudgePackService.class.getDeclaredField("PLATFORMS");platformField.setAccessible(true);
        Map<UUID,Object> platforms=(Map<UUID,Object>)platformField.get(null);
        var platform=Class.forName("com.example.voidscape.boss.JudgePackService$Platform").getDeclaredConstructor(boolean.class,long.class);platform.setAccessible(true);
        platforms.put(tamer.getUniqueId(),platform.newInstance(true,System.currentTimeMillis()));
        try{
            input(false,false,false,false,false,false);tamer.teleport(home);selectForMount(mount);
            check(call(riding,"start",tamer,mount.id())==null,"Bedrock platform can mount with zero mana");
            mounted=session(riding);input(true,false,false,false,false,false);tamer.setRotation(0,-60);
            check(((org.bukkit.util.Vector)call(riding,"input",tamer,mounted)).getY()>.2,"Bedrock camera-up fallback ascends without jump");
            tamer.setRotation(0,60);
            check(((org.bukkit.util.Vector)call(riding,"input",tamer,mounted)).getY()<-.2,"Bedrock camera-down fallback descends smoothly");
            call(riding,"stop",tamer.getUniqueId(),true);
            var fenrir=adults.stream().filter(p->p.species()==PetCatalog.Species.FENRIR).findFirst().orElseThrow();
            tamer.teleport(home);selectForMount(fenrir);call(riding,"start",tamer,fenrir.id());
            input(true,false,false,false,true,true);tamer.setRotation(0,-60);
            check(((org.bukkit.util.Vector)call(riding,"input",tamer,session(riding))).getY()==0,"Bedrock camera and jump input cannot make Fenrir fly");
            call(riding,"stop",tamer.getUniqueId(),true);
        }finally{platforms.remove(tamer.getUniqueId());}
        input(false,false,false,false,false,false);tamer.teleport(home);selectForMount(mount);call(riding,"start",tamer,mount.id());
        seat=tamer.getVehicle();
        check(tamer.teleport(home.clone().add(15,0,0))&&!seat.isValid()&&!tamer.isInsideVehicle(),"a real player teleport dismounts cleanly without being redirected");
        tamer.teleport(home);selectForMount(mount);call(riding,"start",tamer,mount.id());
        body=body(tamer);seat=tamer.getVehicle();garden.getConfig().set("pets.riding.enabled",false);call(riding,"tick",tamer,body);
        check(!seat.isValid()&&!tamer.isInsideVehicle(),"disabling mounts during a flight performs safe cleanup");garden.getConfig().set("pets.riding.enabled",true);

        tamer.teleport(home);selectForMount(mount);call(riding,"start",tamer,mount.id());
        body=body(tamer);seat=tamer.getVehicle();mounted=session(riding);
        Location voidAt=new Location(world,9000.5,200,9000.5);
        for(int x=voidAt.getBlockX()-2;x<=voidAt.getBlockX()+2;x++)for(int z=voidAt.getBlockZ()-2;z<=voidAt.getBlockZ()+2;z++)world.getChunkAt(x>>4,z>>4).addPluginChunkTicket(this);
        for(int y=world.getMinHeight();y<=202;y++)world.getBlockAt(9000,y,9000).setType(Material.AIR,false);
        call(body,"move",voidAt);((CraftEntity)seat).getHandle().setPos(voidAt.getX(),voidAt.getY()+(double)get(mounted,"lift")-(double)get(mounted,"attachment"),voidAt.getZ());
        ((CraftEntity)seat).getHandle().positionRider(actorA);call(riding,"stop",tamer.getUniqueId(),true);
        check(tamer.getLocation().distanceSquared(home)<36&&!seat.isValid(),"dismount over genuine void returns to the safe boarding point");
        call(companions,"dismiss",tamer);tamer.teleport(home);input(false,false,false,false,false,false);
        check(((Map<?,?>)get(riding,"sessions")).isEmpty(),"mount suite leaves no ride sessions behind");
        check(magic.mana().account(tamer).manaExact()==0,"all mount and cleanup paths leave the mana account untouched");
    }
    void blessingChecks(Object riding,List<PetRecord> adults)throws Exception{
        var mount=adults.stream().filter(p->p.species()==PetCatalog.Species.AETHERIUS).findFirst().orElseThrow();
        var types=List.of(PotionEffectType.NIGHT_VISION,PotionEffectType.REGENERATION,PotionEffectType.STRENGTH,PotionEffectType.SPEED,PotionEffectType.ABSORPTION);
        call(companions,"dismiss",tamer);for(var type:types)tamer.removePotionEffect(type);
        Object blessing=get(riding,"blessing");((Map<?,?>)get(blessing,"shieldReady")).clear();
        input(false,false,false,false,false,false);tamer.teleport(home);selectForMount(mount);
        check(call(riding,"start",tamer,mount.id())==null,"Aetherius mounts for blessing verification");
        for(var type:types)check(tamer.hasPotionEffect(type)&&tamer.getPotionEffect(type).getAmplifier()==0,"Aetherius grants "+type.getKey()+" I to its rider");
        check(tamer.getAbsorptionAmount()>0&&tamer.getAbsorptionAmount()<=4,"Aetherius grants two absorption hearts");
        tamer.setAbsorptionAmount(1);drive(riding,body(tamer),45);
        check(tamer.getAbsorptionAmount()==1,"rider ticks never refill consumed absorption");
        // Force the owned effect's expiry window without consuming real minutes.
        put(blessing,"applying",true);tamer.removePotionEffect(PotionEffectType.ABSORPTION);tamer.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION,200,0,true,false,true));put(blessing,"applying",false);
        tamer.setAbsorptionAmount(.5);call(blessing,"tick",tamer);
        check(tamer.getAbsorptionAmount()==.5&&tamer.getPotionEffect(PotionEffectType.ABSORPTION).getDuration()>400,"absorption duration refresh preserves the consumed shield");
        call(riding,"stop",tamer.getUniqueId(),false);
        for(var type:types)check(!tamer.hasPotionEffect(type),"dismount removes owned "+type.getKey());
        check(tamer.getAbsorptionAmount()==0,"dismount removes the remaining mount shield");
        check(call(riding,"start",tamer,mount.id())==null,"Aetherius can be remounted during shield cooldown");
        check(!tamer.hasPotionEffect(PotionEffectType.ABSORPTION),"rapid dismount/remount cannot farm absorption hearts");
        tamer.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH,600,2,false,true,true));
        call(riding,"stop",tamer.getUniqueId(),false);
        check(tamer.getPotionEffect(PotionEffectType.STRENGTH).getAmplifier()==2,"external stronger buff applied during a ride survives dismount");
        tamer.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION,900,0,false,true,true));
        call(riding,"start",tamer,mount.id());call(riding,"stop",tamer.getUniqueId(),false);
        check(tamer.getPotionEffect(PotionEffectType.STRENGTH).getAmplifier()==2&&tamer.hasPotionEffect(PotionEffectType.NIGHT_VISION),"existing potion effects are preserved when boarding and dismounting");
        for(var type:types)tamer.removePotionEffect(type);call(companions,"dismiss",tamer);
    }
    /** Select a primary mount explicitly now that summoning a second pet no longer replaces the first. */
    Object selectForMount(PetRecord record)throws Exception{
        Object stable=stable(tamer);
        if((boolean)get(stable,"out")&&record.id().equals(get(stable,"active"))&&body(tamer)!=null)return null;
        call(companions,"dismiss",tamer);
        return call(companions,"summon",tamer,record.id());
    }
    int teleports;
    void groundObstacles(Object riding,Object body)throws Exception{
        input(false,false,false,false,false,false);drive(riding,body,10);tamer.setRotation(0,0);
        Location at=((Rabbit)get(body,"shell")).getLocation();int floor=(int)Math.round(at.getY()),wallZ=at.getBlockZ()+3;
        Map<Block,BlockData> blocks=new HashMap<>();
        try{
            for(int x=at.getBlockX()-3;x<=at.getBlockX()+3;x++)for(int y=floor;y<=floor+6;y++){
                Block b=world.getBlockAt(x,y,wallZ);blocks.put(b,b.getBlockData());b.setType(Material.STONE,false);
            }
            input(true,false,false,false,false,true);drive(riding,body,20);
            check(((Rabbit)get(body,"shell")).getZ()<wallZ-.6,"fast Fenrir stops before a tall wall without tunnelling or climbing it");
            for(Block b:blocks.keySet())if(b.getY()>floor)b.setType(Material.AIR,false);
            drive(riding,body,8);
            check(((Rabbit)get(body,"shell")).getZ()>wallZ+1,"Fenrir steps over a one-block obstacle");
        }finally{blocks.forEach((block,data)->block.setBlockData(data,false));}
        input(false,false,false,false,false,false);drive(riding,body,12);
    }
}
