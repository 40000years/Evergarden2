package com.example.voidscape.pet;

import com.example.voidscape.VoidscapePlugin;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import java.util.*;

/** Builds and connects the pet services; also the admin {@code /evergarden pets perf|give|egg} tools. */
public final class PetSystem implements AutoCloseable {
    private final VoidscapePlugin plugin;
    private final NativePetRenderer renderer;
    private final PetBodies bodies;
    private final PetStore store;
    private final WildPetSpawns wild;
    private final PetCompanions companions;
    private final PetTaming taming;
    private final PetBreeding breeding;
    private final PetMenu menu;
    private final BukkitTask sampler;
    private long sampledTransforms,sampledBusy;
    private int sampledTick;
    private double transformRate,millisPerTick;

    public PetSystem(VoidscapePlugin plugin){
        this.plugin=plugin;
        renderer=new NativePetRenderer(plugin);
        PetTerrain terrain=new PetTerrain(plugin);
        bodies=new PetBodies(plugin);store=new PetStore(plugin);
        wild=new WildPetSpawns(plugin,renderer,bodies,terrain);
        companions=new PetCompanions(plugin,renderer,bodies,terrain,store);
        taming=new PetTaming(plugin,wild,companions,bodies);wild.taming(taming);companions.taming(taming);
        breeding=new PetBreeding(plugin,store,companions,taming);
        menu=new PetMenu(plugin,companions,breeding,taming);companions.menu(menu);
        sampledTick=Bukkit.getCurrentTick();
        sampler=Bukkit.getScheduler().runTaskTimer(plugin,this::sample,100,100);
    }
    public WildPetSpawns wild(){return wild;}
    public PetMenu menu(){return menu;}
    public void reload(){wild.reload();}
    private long busy(){return bodies.busy()+wild.busy()+companions.busy();}
    private void sample(){
        int tick=Bukkit.getCurrentTick();long transforms=renderer.transformsSent(),spent=busy();
        double seconds=Math.max(1,tick-sampledTick)/20.0;
        transformRate=(transforms-sampledTransforms)/seconds;
        millisPerTick=(spent-sampledBusy)/1e6/Math.max(1,tick-sampledTick);
        sampledTick=tick;sampledTransforms=transforms;sampledBusy=spent;
    }
    public String perf(){
        return String.format(Locale.ROOT,"สัตว์ในโลก %d (ป่า %d · ติดตาม %d) · ผู้ชม-สัตว์ที่ส่งข้อมูล %d คู่ · ท่าที่ส่ง %.0f/วินาที · %.3f ms/tick",
            bodies.count(),wild.count(),companions.count(),bodies.pairs(),transformRate,millisPerTick);
    }
    private boolean admin(CommandSender sender){return sender.isOp()||sender.hasPermission("evergarden.admin")||sender.hasPermission("voidscape.admin");}
    /** {@code /evergarden pets perf|give <player> <species> <stage>|egg <player> <species>|rush <player>}. */
    public boolean command(CommandSender sender,String[] args){
        if(!admin(sender)){plugin.message(sender,"คำสั่งนี้สำหรับแอดมินเท่านั้น");return true;}
        String action=args[1].toLowerCase(Locale.ROOT);
        if(action.equals("perf")){plugin.message(sender,perf());return true;}
        Player target=args.length>2?Bukkit.getPlayerExact(args[2]):null;
        if(target==null){plugin.message(sender,"/evergarden pets give <ผู้เล่นออนไลน์> <species> <baby|medium|adult> · egg <ผู้เล่น> <species> · rush <ผู้เล่น>");return true;}
        if(action.equals("rush")){
            int changed=breeding.rush(target);
            plugin.message(sender,changed==0?target.getName()+" ไม่มีรังหรือไข่ที่ต้องเร่ง"
                :"เร่งรังและไข่ของ "+target.getName()+" ให้พร้อมแล้ว ("+changed+" รายการ) · สำหรับทดสอบ");
            return true;
        }
        var species=args.length>3?PetCatalog.Species.find(args[3]):null;
        if(species==null||!renderer.hasModel(species,PetCatalog.Stage.BABY)){plugin.message(sender,"ระบุชนิดที่มีโมเดล เช่น frost_wyvern");return true;}
        PetStable stable=companions.stable(target);
        if(action.equals("give")){
            var stage=args.length>4?PetCatalog.Stage.find(args[4]):PetCatalog.Stage.BABY;
            if(stage==null){plugin.message(sender,"ระบุวัย baby · medium · adult");return true;}
            if(stable.full()){plugin.message(sender,"คอกของ "+target.getName()+" เต็ม");return true;}
            PetRecord record=PetRecord.tamed(species,stage,new Random());record.bond=60;record.growth=companions.growthFloor(stage);stable.add(record);
            plugin.message(sender,"มอบ "+species.display+" · "+stage.thai+" ให้ "+target.getName()+" แล้ว");return true;
        }
        if(action.equals("egg")){
            if(target.getInventory().firstEmpty()<0){plugin.message(sender,"กระเป๋าของ "+target.getName()+" เต็ม");return true;}
            Random random=new Random();
            PetRecord a=PetRecord.tamed(species,PetCatalog.Stage.ADULT,random),b=PetRecord.tamed(species,PetCatalog.Stage.ADULT,random);
            PetRecord child=PetRecord.hatch(species,a,b,random);
            String id=UUID.randomUUID().toString();
            ConfigurationSection egg=store.eggs.createSection("eggs."+id);
            egg.set("owner",target.getUniqueId().toString());egg.set("species",species.id);egg.set("created",System.currentTimeMillis());
            egg.set("parents","แอดมิน");egg.set("incubated",breeding.incubateMillis());egg.set("hatched",false);
            child.save(egg.createSection("pet"));egg.set("pet-id",child.id.toString());
            store.eggsDirty=true;store.saveEggs();
            target.getInventory().addItem(breeding.egg(id,species,"แอดมิน",100));
            plugin.message(sender,"มอบไข่ "+species.display+" พร้อมฟักให้ "+target.getName()+" แล้ว");return true;
        }
        plugin.message(sender,"/evergarden pets perf|give|egg|rush");return true;
    }
    public List<String> complete(String[] args){
        List<String> values=new ArrayList<>();
        if(args.length==3)Bukkit.getOnlinePlayers().forEach(p->values.add(p.getName()));
        if(args.length==4)for(var species:PetCatalog.Species.values())if(renderer.hasModel(species,PetCatalog.Stage.BABY))values.add(species.id);
        if(args.length==5&&args[1].equalsIgnoreCase("give"))for(var stage:PetCatalog.Stage.values())values.add(stage.id);
        String prefix=args[args.length-1].toLowerCase(Locale.ROOT);
        return values.stream().filter(v->v.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }
    @Override public void close(){
        sampler.cancel();
        // Companions despawn first so their stables save with the final state.
        menu.close();breeding.close();companions.close();wild.close();bodies.close();store.close();
    }
}
