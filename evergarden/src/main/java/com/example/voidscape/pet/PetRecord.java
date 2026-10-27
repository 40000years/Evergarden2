package com.example.voidscape.pet;

import org.bukkit.configuration.ConfigurationSection;
import java.util.*;
import java.util.random.RandomGenerator;

/** One owned pet, stored as data. Only a summoned companion has entities in the world. */
public final class PetRecord {
    public static final int STAT_MAX=31,BOND_MAX=100;
    final UUID id;
    final PetCatalog.Species species;
    PetCatalog.Stage stage;
    String name;
    int bond,growth,vitality,agility,affinity,generation;
    float size=1;
    boolean aura;
    List<UUID> parents=List.of();
    long born,bred,fed;
    /** Minutes spent out with an active owner, for the slow bond gain. */
    int minutes;
    /** follow, stay, perch (on the owner's head) or assist (fights beside the owner). */
    String mode="follow";
    /** Server game tick until which a companion knocked out in a fight rests in the stable. */
    long faintedUntil;
    /** Health in fights; never saved, so it is full after a restart. Ticks are the server's current tick. */
    double health=-1;
    transient long combatTick,healthTick;

    PetRecord(UUID id,PetCatalog.Species species,PetCatalog.Stage stage){this.id=id;this.species=species;this.stage=stage;}

    public UUID id(){return id;}
    public PetCatalog.Species species(){return species;}
    public PetCatalog.Stage stage(){return stage;}
    public String name(){return name==null||name.isBlank()?species.display:name;}
    public int bond(){return bond;}
    public int growth(){return growth;}

    /** A wild pet just befriended: modest random stats, stronger for older forms. */
    static PetRecord tamed(PetCatalog.Species species,PetCatalog.Stage stage,RandomGenerator random){
        PetRecord record=new PetRecord(UUID.randomUUID(),species,stage);
        int floor=switch(stage){case BABY->4;case MEDIUM->6;case ADULT->8;},span=17;
        record.vitality=floor+random.nextInt(span);record.agility=floor+random.nextInt(span);record.affinity=floor+random.nextInt(span);
        record.bond=10;record.born=System.currentTimeMillis();
        record.aura=random.nextDouble()<.01;
        return record;
    }
    /** Offspring inherit each stat from either parent with a small drift and rare mutation. */
    static PetRecord hatch(PetCatalog.Species species,PetRecord a,PetRecord b,RandomGenerator random){
        PetRecord child=new PetRecord(UUID.randomUUID(),species,PetCatalog.Stage.BABY);
        child.vitality=inherit(a.vitality,b.vitality,random);child.agility=inherit(a.agility,b.agility,random);child.affinity=inherit(a.affinity,b.affinity,random);
        child.size=(float)Math.clamp((a.size+b.size)/2+(random.nextDouble()-.5)*.08,.85,1.15);
        child.aura=(a.aura||b.aura)?random.nextDouble()<.25:random.nextDouble()<.02;
        child.generation=Math.max(a.generation,b.generation)+1;
        child.parents=List.of(a.id,b.id);child.bond=20;child.born=System.currentTimeMillis();
        return child;
    }
    private static int inherit(int a,int b,RandomGenerator random){
        int value=(random.nextBoolean()?a:b)+random.nextInt(-2,4);
        if(random.nextDouble()<.05)value+=5;
        return Math.clamp(value,0,STAT_MAX);
    }

    void save(ConfigurationSection section){
        section.set("species",species.id);section.set("stage",stage.id);section.set("name",name);
        section.set("bond",bond);section.set("growth",growth);section.set("minutes",minutes);
        section.set("vitality",vitality);section.set("agility",agility);section.set("affinity",affinity);
        section.set("size",(double)size);section.set("aura",aura);section.set("generation",generation);
        section.set("parents",parents.stream().map(UUID::toString).toList());
        section.set("born",born);section.set("bred",bred);section.set("fed",fed);
        section.set("mode",mode);section.set("fainted-until",faintedUntil);
    }
    static PetRecord load(UUID id,ConfigurationSection section){
        var species=PetCatalog.Species.find(section.getString("species"));
        var stage=PetCatalog.Stage.find(section.getString("stage"));
        if(species==null||stage==null)return null;
        PetRecord record=new PetRecord(id,species,stage);
        record.name=section.getString("name");
        record.bond=Math.clamp(section.getInt("bond"),0,BOND_MAX);record.growth=Math.max(0,section.getInt("growth"));
        record.minutes=Math.max(0,section.getInt("minutes"));
        record.vitality=Math.clamp(section.getInt("vitality"),0,STAT_MAX);record.agility=Math.clamp(section.getInt("agility"),0,STAT_MAX);
        record.affinity=Math.clamp(section.getInt("affinity"),0,STAT_MAX);
        double size=section.getDouble("size",1);record.size=Double.isFinite(size)?(float)Math.clamp(size,.85,1.15):1;
        record.aura=section.getBoolean("aura");record.generation=Math.max(0,section.getInt("generation"));
        List<UUID> parents=new ArrayList<>();
        for(String value:section.getStringList("parents"))try{parents.add(UUID.fromString(value));}catch(IllegalArgumentException ignored){}
        record.parents=List.copyOf(parents);
        record.born=section.getLong("born");record.bred=section.getLong("bred");record.fed=section.getLong("fed");
        String mode=section.getString("mode","follow");record.mode=PetSkills.MODES.contains(mode)?mode:"follow";
        record.faintedUntil=Math.max(0,section.getLong("fainted-until"));
        return record;
    }
}
