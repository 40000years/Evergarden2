package com.example.voidscape.pet;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import java.util.*;

/** A player's owned pets, which one follows them, and their breeding nest. */
final class PetStable {
    final UUID owner;
    int slots;
    UUID active;
    /** The owner wants the active pet out; it returns after relogging or a suspension. */
    boolean out;
    boolean hideOthers;
    UUID nestA,nestB;
    long nestReady;
    long eggDay;
    int eggsToday;
    final LinkedHashMap<UUID,PetRecord> pets=new LinkedHashMap<>();
    boolean dirty;
    /** Its file exists but could not be read: shown empty, never saved over the original. */
    boolean locked;

    PetStable(UUID owner,int slots){this.owner=owner;this.slots=slots;}

    boolean full(){return pets.size()>=slots;}
    PetRecord get(UUID id){return id==null?null:pets.get(id);}
    PetRecord activeRecord(){return get(active);}
    boolean nesting(UUID id){return id!=null&&(id.equals(nestA)||id.equals(nestB));}
    boolean nestBusy(){return nestA!=null&&nestB!=null;}
    void clearNest(){nestA=null;nestB=null;nestReady=0;dirty=true;}
    /** Eggs collected today (UTC day), reset lazily when the day changes. */
    int eggsToday(long now){long day=Math.floorDiv(now,86_400_000L);if(day!=eggDay){eggDay=day;eggsToday=0;}return eggsToday;}
    void add(PetRecord record){pets.put(record.id,record);dirty=true;}
    void remove(UUID id){pets.remove(id);if(id.equals(active)){active=null;out=false;}if(nesting(id))clearNest();dirty=true;}

    String serialize(){
        YamlConfiguration file=new YamlConfiguration();
        file.set("version",1);file.set("slots",slots);
        file.set("active",active==null?null:active.toString());file.set("out",out);file.set("hide-others",hideOthers);
        if(nestA!=null&&nestB!=null){file.set("nest.a",nestA.toString());file.set("nest.b",nestB.toString());file.set("nest.ready",nestReady);}
        file.set("eggs.day",eggDay);file.set("eggs.today",eggsToday);
        for(PetRecord record:pets.values())record.save(file.createSection("pets."+record.id));
        return file.saveToString();
    }
    static PetStable load(UUID owner,YamlConfiguration file,int defaultSlots){
        PetStable stable=new PetStable(owner,Math.clamp(file.getInt("slots",defaultSlots),1,28));
        stable.out=file.getBoolean("out");stable.hideOthers=file.getBoolean("hide-others");
        stable.eggDay=file.getLong("eggs.day");stable.eggsToday=Math.max(0,file.getInt("eggs.today"));
        ConfigurationSection pets=file.getConfigurationSection("pets");
        if(pets!=null)for(String key:pets.getKeys(false)){
            UUID id=uuid(key);ConfigurationSection section=pets.getConfigurationSection(key);
            if(id==null||section==null)continue;
            PetRecord record=PetRecord.load(id,section);if(record!=null)stable.pets.put(id,record);
        }
        stable.active=uuid(file.getString("active"));
        if(!stable.pets.containsKey(stable.active)){stable.active=null;stable.out=false;}
        UUID a=uuid(file.getString("nest.a")),b=uuid(file.getString("nest.b"));
        if(a!=null&&b!=null&&stable.pets.containsKey(a)&&stable.pets.containsKey(b)){stable.nestA=a;stable.nestB=b;stable.nestReady=file.getLong("nest.ready");}
        return stable;
    }
    static UUID uuid(String value){
        if(value==null)return null;
        try{return UUID.fromString(value);}catch(IllegalArgumentException invalid){return null;}
    }
}
