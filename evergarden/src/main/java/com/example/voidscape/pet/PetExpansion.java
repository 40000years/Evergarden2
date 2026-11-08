package com.example.voidscape.pet;

import com.example.voidscape.world.BiomeTerrain;
import com.google.gson.*;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Predicate;
import java.util.random.RandomGenerator;

/** Curated food, habitat and real powers for the expanded collection, shared by both clients. */
final class PetExpansion {
    record Boost(String condition,PotionEffectType type,int amplifier,int ticks){}
    record Pulse(String condition,String action,int every,double value){}
    record Hit(String type,int ticks,double chance,int amplifier,int cooldown,double first,double lifesteal){}
    record Aura(String condition,double radius){}
    record Profile(PetCatalog.Species species,Material food,BiomeTerrain.Kind habitat,boolean flies,boolean hover,
                   String design,List<String> growth,PetSkills.Skill skill,List<Boost> boosts,List<Pulse> pulses,
                   double guard,double armor,Hit hit,Aura aura){}
    private static final Map<PetCatalog.Species,Profile> PROFILES=load();

    private static Map<PetCatalog.Species,Profile> load(){
        var result=new EnumMap<PetCatalog.Species,Profile>(PetCatalog.Species.class);
        try(InputStream stream=PetExpansion.class.getResourceAsStream("/pet-expansion.json")){
            if(stream==null)throw new IOException("Missing pet-expansion.json");
            JsonObject root=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();
            for(JsonElement value:root.getAsJsonArray("species")){
                JsonObject e=value.getAsJsonObject();var species=PetCatalog.Species.find(string(e,"id"));
                if(species==null||result.containsKey(species))throw new IOException("Invalid or duplicate expansion species");
                Material food=Material.valueOf(string(e,"food"));
                List<Boost> boosts=new ArrayList<>();List<Pulse> pulses=new ArrayList<>();List<String> growth=new ArrayList<>();
                for(var v:e.getAsJsonArray("boosts")){
                    JsonObject b=v.getAsJsonObject();boosts.add(new Boost(string(b,"condition"),effect(string(b,"type")),b.get("amplifier").getAsInt(),b.get("ticks").getAsInt()));
                }
                for(var v:e.getAsJsonArray("pulses")){
                    JsonObject p=v.getAsJsonObject();pulses.add(new Pulse(string(p,"condition"),string(p,"action"),p.get("every").getAsInt(),p.get("value").getAsDouble()));
                }
                for(var v:e.getAsJsonArray("growth"))growth.add(v.getAsString());
                if(growth.size()!=3||!food.isItem())throw new IOException("Incomplete profile: "+species);
                JsonObject h=e.getAsJsonObject("hit");
                Hit hit=new Hit(string(h,"type"),(int)Math.round(h.get("seconds").getAsDouble()*20),h.get("chance").getAsDouble(),h.get("amplifier").getAsInt(),h.get("cooldown").getAsInt(),h.get("first").getAsDouble(),h.get("lifesteal").getAsDouble());
                Aura aura=null;if(!e.get("aura").isJsonNull()){
                    JsonObject a=e.getAsJsonObject("aura");aura=new Aura(string(a,"condition"),a.get("radius").getAsDouble());
                }
                Profile profile=new Profile(species,food,BiomeTerrain.Kind.valueOf(string(e,"habitat")),e.get("flies").getAsBoolean(),e.get("hover").getAsBoolean(),
                    string(e,"design"),List.copyOf(growth),new PetSkills.Skill(string(e,"passiveName"),string(e,"passive"),string(e,"traitName"),string(e,"trait")),
                    List.copyOf(boosts),List.copyOf(pulses),e.get("guard").getAsDouble(),e.get("armor").getAsDouble(),hit,aura);
                result.put(species,profile);
            }
            if(result.size()!=57)throw new IOException("Expected 57 expansion profiles, got "+result.size());
            return Collections.unmodifiableMap(result);
        }catch(IOException|RuntimeException error){throw new ExceptionInInitializerError(error);}
    }
    private static String string(JsonObject object,String key){return object.get(key).getAsString();}
    private static PotionEffectType effect(String name){
        PotionEffectType value=Registry.MOB_EFFECT.get(NamespacedKey.minecraft(name.toLowerCase(Locale.ROOT)));
        if(value==null)throw new IllegalArgumentException("Unknown pet effect: "+name);
        return value;
    }
    static Profile of(PetCatalog.Species species){return PROFILES.get(species);}
    static Collection<Profile> profiles(){return PROFILES.values();}
    static PetSkills.Skill skill(PetRecord record){
        Profile profile=of(record.species);String passive=profile.skill.passive();
        double cadence=switch(record.stage){case BABY->2;case MEDIUM->1.4;case ADULT->1;};
        passive=java.util.regex.Pattern.compile("ทุก (\\d+) วินาที").matcher(passive)
            .replaceAll(match->"ทุก "+Math.round(Integer.parseInt(match.group(1))*cadence)+" วินาที");
        return new PetSkills.Skill(profile.skill.passiveName(),passive,profile.skill.traitName(),profile.skill.trait());
    }
    private static double maxHealth(Player owner){var attribute=owner.getAttribute(Attribute.MAX_HEALTH);return attribute==null?20:attribute.getValue();}
    private static void heal(Player owner,double amount){if(!owner.isDead())owner.setHealth(Math.min(maxHealth(owner),owner.getHealth()+Math.max(0,amount)));}
    private static boolean around(Player owner,Predicate<Material> wanted,int radius){
        var at=owner.getLocation().getBlock();
        for(int x=-radius;x<=radius;x++)for(int z=-radius;z<=radius;z++)for(int y=-1;y<=1;y++){
            int xx=at.getX()+x,zz=at.getZ()+z;
            if(at.getWorld().isChunkLoaded(xx>>4,zz>>4)&&wanted.test(at.getWorld().getBlockAt(xx,at.getY()+y,zz).getType()))return true;
        }
        return false;
    }
    static boolean condition(Player owner,String condition){
        Material feet=owner.getLocation().getBlock().getType();
        Material below=owner.getLocation().subtract(0,.2,0).getBlock().getType();String floor=below.name();
        return switch(condition){
            case "ALWAYS"->true;
            case "SPRINTING"->owner.isSprinting();case "SNEAKING"->owner.isSneaking();
            case "IN_WATER"->owner.isInWater();case "NEAR_WATER"->owner.isInWater()||around(owner,m->m==Material.WATER,3);
            case "LOW_HEALTH"->owner.getHealth()<maxHealth(owner)*.4;
            case "FULL_HEALTH"->owner.getHealth()>=maxHealth(owner)*.9;
            case "HUNGRY"->owner.getFoodLevel()<20;case "FULL_FOOD"->owner.getFoodLevel()>=20;
            case "ON_GRASS"->below==Material.GRASS_BLOCK;case "ON_STONE"->Tag.BASE_STONE_OVERWORLD.isTagged(below)||below==Material.COBBLESTONE||below==Material.STONE_BRICKS;
            case "ON_SAND"->Tag.SAND.isTagged(below);case "ON_SNOW"->floor.contains("SNOW")||floor.contains("ICE")||feet.name().contains("SNOW");
            case "ON_MOSS"->floor.contains("MOSS")||feet.name().contains("MOSS");case "ON_LOGS"->Tag.LOGS.isTagged(below);
            case "ON_PATH"->below==Material.DIRT_PATH||floor.contains("BRICKS");
            case "NEAR_FLOWERS"->around(owner,m->Tag.FLOWERS.isTagged(m),3);
            case "FOREST"->WildPetSpawns.region(owner.getLocation().getBlock().getBiome())==BiomeTerrain.Kind.ANCIENT_FOREST;
            case "DARK"->{var eyes=owner.getEyeLocation().getBlock();yield eyes.getLightFromSky()<=7&&eyes.getLightFromBlocks()<=7;}
            case "SKY"->owner.getEyeLocation().getBlock().getLightFromSky()>=12;
            case "BURNING"->owner.getFireTicks()>0;case "FALLING"->owner.getFallDistance()>3;
            case "STILL"->!owner.isSprinting()&&!owner.isSneaking()&&owner.getVelocity().lengthSquared()<.01;
            default->throw new IllegalArgumentException("Unknown pet condition: "+condition);
        };
    }
    static void passive(Player owner,PetRecord record,int clock,Predicate<LivingEntity> foe){
        Profile profile=of(record.species);if(profile==null||owner.isDead())return;
        // Periodic care matures with the companion; permanent effect levels stay bounded.
        double cadence=switch(record.stage){case BABY->2;case MEDIUM->1.4;case ADULT->1;};
        for(Boost boost:profile.boosts)if(condition(owner,boost.condition))PetSkills.effect(owner,boost.type,boost.amplifier,boost.ticks);
        for(Pulse pulse:profile.pulses){
            int period=Math.max(20,(int)Math.round(pulse.every*cadence)*20);
            if(clock%period>=20||!condition(owner,pulse.condition))continue;
            switch(pulse.action){
                case "HEAL"->heal(owner,pulse.value);
                case "FOOD"->owner.setFoodLevel(Math.min(20,owner.getFoodLevel()+(int)pulse.value));
                case "SATURATION"->owner.setSaturation(Math.min(owner.getFoodLevel(),owner.getSaturation()+(float)pulse.value));
                case "CLEAR_FIRE"->owner.setFireTicks(0);
                case "CLEANSE_POISON"->owner.removePotionEffect(PotionEffectType.POISON);
                case "CLEANSE_WITHER"->owner.removePotionEffect(PotionEffectType.WITHER);
                case "CLEANSE_BLINDNESS"->owner.removePotionEffect(PotionEffectType.BLINDNESS);
                case "CLEANSE_NAUSEA"->owner.removePotionEffect(PotionEffectType.NAUSEA);
                case "CLEANSE_HUNGER"->owner.removePotionEffect(PotionEffectType.HUNGER);
                case "THAW"->owner.setFreezeTicks(Math.max(0,owner.getFreezeTicks()-(int)pulse.value));
                case "ABSORPTION"->PetSkills.effect(owner,PotionEffectType.ABSORPTION,0,400);
                default->throw new IllegalStateException("Unknown pet pulse: "+pulse.action);
            }
        }
        if(profile.aura!=null&&condition(owner,profile.aura.condition)){
            double radius=profile.aura.radius;
            for(Entity entity:owner.getNearbyEntities(radius,3,radius))if(entity instanceof Monster monster&&foe.test(monster))
                monster.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING,40,0,true,false));
        }
    }
    static void hit(PetRecord record,Player owner,LivingEntity target,double dealt,RandomGenerator random){
        Profile profile=of(record.species);if(profile==null)return;Hit hit=profile.hit;
        // A record owns its timer, so dismiss/reload does not leave an accumulating global map.
        long now=Integer.toUnsignedLong(Bukkit.getCurrentTick());
        if(now<record.traitTick||random.nextDouble()>=hit.chance)return;
        record.traitTick=now+hit.cooldown;
        switch(hit.type){
            case "FIRE"->target.setFireTicks(Math.max(target.getFireTicks(),hit.ticks));
            case "FREEZE"->target.setFreezeTicks(Math.max(target.getFreezeTicks(),140+hit.ticks));
            default->target.addPotionEffect(new PotionEffect(effect(hit.type),hit.ticks,hit.amplifier));
        }
        if(hit.lifesteal>0&&dealt>0)heal(owner,dealt*hit.lifesteal);
        target.getWorld().spawnParticle(record.species.rare()?Particle.ENCHANT:Particle.HAPPY_VILLAGER,
            target.getLocation().add(0,target.getHeight()*.6,0),3,.18,.18,.18,0);
    }
    private PetExpansion(){}
}
