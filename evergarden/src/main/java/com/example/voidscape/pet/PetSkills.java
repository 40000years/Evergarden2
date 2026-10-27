package com.example.voidscape.pet;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Tag;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import java.util.*;
import java.util.function.Predicate;
import java.util.random.RandomGenerator;

/**
 * What each species does for its owner. A passive skill works while the companion is out
 * (any mode); a battle trait triggers on its hits in assist mode. Stats from {@link PetRecord}
 * set health, damage and attack speed; growth stages matter most.
 */
final class PetSkills {
    static final List<String> MODES=List.of("follow","stay","perch","assist");
    record Skill(String passiveName,String passive,String traitName,String trait){}
    private static final Map<PetCatalog.Species,Skill> SKILLS=new EnumMap<>(PetCatalog.Species.class);
    static {
        put(PetCatalog.Species.ARCANE_NINE_TAILS,"โชคจิ้งจอก","เจ้าของได้ Luck I (ของจากหีบดีขึ้น)","ไฟจิ้งจอก","เผาศัตรู 3 วินาที");
        put(PetCatalog.Species.FROST_WYVERN,"ปีกหิมะ","ร่อนลงช้าเมื่อเจ้าของตกจากที่สูง","ลมหายใจเยือกแข็ง","ศัตรูช้าลง (Slowness II) 3 วินาที");
        put(PetCatalog.Species.ASTRAL_OWL,"ตาดารา","เจ้าของมองเห็นในที่มืด (Night Vision)","แสงดาวชี้เป้า","ศัตรูเรืองแสงให้เห็นทะลุกำแพง 5 วินาที");
        put(PetCatalog.Species.CRIMSON_CERBERUS,"หนังเพลิงนรก","เจ้าของต้านไฟ (Fire Resistance)","สามหัวขย้ำ","ดาเมจ +50%");
        put(PetCatalog.Species.SHADOW_PANTHER,"ฝีเท้าเงา","เจ้าของวิ่งเร็วขึ้น (Speed I)","ซุ่มโจมตี","ครั้งแรกที่ตีเป้าหมายใหม่ ดาเมจ ×2");
        put(PetCatalog.Species.BLOOD_GARGOYLE,"ผิวศิลา","เลือดเจ้าของต่ำกว่า 40% ได้ Resistance I","ดูดโลหิต","ฟื้นเลือดเจ้าของ 20% ของดาเมจที่ทำ");
        put(PetCatalog.Species.RUNIC_GOLEM,"อักขระช่าง","เจ้าของขุดเร็วขึ้น (Haste I)","ปราการศิลา","เจ้าของรับดาเมจลด 15% เมื่อโกเลมอยู่ใกล้");
        put(PetCatalog.Species.WORLD_TREE_ENTLING,"ใบไม้ฟื้นฟู","เจ้าของได้ Regeneration ทุก 30 วินาที","รากพันธนาการ","ตรึงศัตรู (Slowness IV) 1.5 วินาที");
        put(PetCatalog.Species.CRYSTAL_TORTOISE,"เกราะผลึก","เจ้าของได้โล่ Absorption ทุก 45 วินาที","กระดองมรกต","เต่ารับดาเมจลดครึ่งหนึ่ง");
        put(PetCatalog.Species.ABYSSAL_MANTA,"เกลียวน้ำ","หายใจใต้น้ำและว่ายเร็ว","คลื่นมิติ","ศัตรูอ่อนแรง (Weakness) 4 วินาที");
        put(PetCatalog.Species.ALCHEMIST_MIMIC,"พ่อค้าจอมตะกละ","ส่วนลดเมื่อแลกของกับ villager","กัดเป็นทอง","8% ทำให้ศัตรูทำ Gold Nugget ร่วง");
        put(PetCatalog.Species.CHRONO_FAIRY,"ก้าวเหนือกาล","เจ้าของกระโดดสูงขึ้น (Jump Boost I)","หยุดเวลา","ศัตรูแทบหยุดนิ่ง (Slowness III) 1 วินาที");
        put(PetCatalog.Species.AETHERIUS,"ฝีเท้าดาราจักร","เจ้าของวิ่งเร็วมาก (Speed II)","เขาแสง","ดาเมจ +25%");
        put(PetCatalog.Species.IGNIS_DRACONIS,"เกล็ดเพลิง","เจ้าของต้านไฟ (Fire Resistance)","เพลิงบรรพกาล","เผาศัตรู 5 วินาที");
        put(PetCatalog.Species.ZEPHYROS,"ปีกวายุ","กระโดดสูงมากและร่อนลงช้า","อัสนีกรงเล็บ","ดาเมจ +25%");
        put(PetCatalog.Species.SOLARIS,"เปลวสุริยะ","Regeneration ทุก 30 วินาทีและมองเห็นในที่มืด","ไฟฟีนิกซ์","เผาศัตรู 4 วินาที");
        put(PetCatalog.Species.FENRIR,"จ่าฝูงเงา","เจ้าของได้ Strength I","เขี้ยวกลืนมิติ","ดาเมจ +25%");
        put(PetCatalog.Species.MOCHI_SLIME,"ขนมหวานแบ่งกัน","ฟื้นความหิว 1 ช่องทุก 60 วินาที","เหนียวหนึบ","ศัตรูช้าลง (Slowness II) 2 วินาที");
        put(PetCatalog.Species.CLOUD_LAMB,"ไออุ่นปุยเมฆ","ฟื้นเลือดเจ้าของ 1 หัวใจทุก 20 วินาที","ลอยขึ้นฟ้า","ศัตรูลอยขึ้น (Levitation) 1 วินาที");
        put(PetCatalog.Species.MOON_RABBIT,"ราตรีจันทร์เต็มดวง","กลางคืนเจ้าของวิ่งเร็ว (Speed I) และกระโดดสูง (Jump Boost I)","ค้อนตำโมจิ","25% ตีหนัก ดาเมจ ×2");
        put(PetCatalog.Species.BLOOM_HEDGEHOG,"ขดตัวกลม","เจ้าของย่อตัวแล้วได้ Resistance I","หนามดอกไม้พิษ","ศัตรูติดพิษ (Poison I) 3 วินาที");
        put(PetCatalog.Species.CORAL_AXOLOTL,"พรปะการัง","ในน้ำได้ Conduit Power (หายใจใต้น้ำ ขุดเร็ว มองชัด)","เยียวยาปะการัง","ฟื้นเลือดเจ้าของครึ่งหัวใจทุกครั้งที่กัด");
        put(PetCatalog.Species.CLOVER_BUMBLE,"น้ำผึ้งล้างพิษ","ล้างพิษ ความหิวโหย และอาการคลื่นไส้ทุก 5 วินาที","ละอองเกสร","ศัตรูตาพร่า (Blindness) 2 วินาที");
        put(PetCatalog.Species.AURORA_PENGUIN,"สไลด์น้ำแข็ง","บนน้ำแข็งหรือหิมะเจ้าของวิ่งเร็วมาก (Speed II)","ลมหนาวขั้วโลก","ศัตรูแข็งเป็นน้ำแข็ง 7 วินาที");
        put(PetCatalog.Species.STORM_RAPTOR,"ฝีเท้านักล่า","เจ้าของวิ่งเร็วมาก (Speed II) ขณะวิ่ง","ล่าเหยื่อบาดเจ็บ","ดาเมจ +60% ใส่ศัตรูที่เลือดต่ำกว่าครึ่ง");
        put(PetCatalog.Species.MAGMA_RHINO,"เลือดนักสู้","เลือดเจ้าของต่ำกว่า 50% ได้ Strength I","พุ่งชนลาวา","ดาเมจ +20% ศัตรูกระเด็น · แรดรับดาเมจลด 30%");
        put(PetCatalog.Species.ECLIPSE_SCORPION,"ซ่อนในเงาจันทร์","เจ้าของล่องหนเมื่อย่อตัว","เหล็กในสุริยคราส","ศัตรูติดพิษรุนแรง (Poison II) 3 วินาที");
        put(PetCatalog.Species.RAIJIN_TIGER,"พลังอัสนี","เจ้าของวิ่งเร็ว (Speed I) และขุดเร็ว (Haste I)","สายฟ้าช็อต","ดาเมจ +30% ศัตรูชะงักครึ่งวินาที");
        put(PetCatalog.Species.TITAN_BEETLE,"ปีกแข็งร่อนลม","ขุดเร็ว (Haste I) และร่อนลงช้าเมื่อตก","เขาเหวี่ยง","ศัตรูถูกเหวี่ยงลอย · ด้วงรับดาเมจลด 40%");
        put(PetCatalog.Species.ANUBIS_JACKAL,"ตาชั่งวิญญาณ","ได้ค่าประสบการณ์ 3 แต้มทุก 60 วินาที","คำพิพากษา","ดาเมจ ×2 ใส่ซอมบี้ โครงกระดูก และอันเดด");
        put(PetCatalog.Species.BONE_DRAKE,"เงาราตรี","กลางคืนเจ้าของได้ Resistance I","ไฟวิญญาณ","ศัตรูเหี่ยวเฉา (Wither I) 3 วินาที");
        put(PetCatalog.Species.JADE_LUNG,"ไข่มุกมังกรหยก","ในน้ำหรือกลางสายฝนได้ Resistance I และหายใจใต้น้ำ","ม่านน้ำหยก","ศัตรูอ่อนแรงหนัก (Weakness II) 3 วินาที");
        put(PetCatalog.Species.CRYSTAL_STAG,"ย่างก้าวแสงจันทร์","เจ้าของวิ่งเร็ว (Speed I) และร่อนลงช้าเมื่อตก","เขาผลึกเจาะเกราะ","ดาเมจ +35%");
        put(PetCatalog.Species.LUNA_MOTH,"ละอองจันทร์","กลางคืนเจ้าของได้ Night Vision และ Luck I","ผงปีกหลับใหล","ศัตรูง่วงงุน (Slowness III + Weakness) 2 วินาที");
        put(PetCatalog.Species.CELESTIAL_KOI,"ปลาคาร์ปนำโชค","เจ้าของได้ Luck I และว่ายเร็วในน้ำ","ฟองสายน้ำ","ศัตรูช้าลงและอ่อนแรง 3 วินาที");
        put(PetCatalog.Species.STARLIGHT_PEACOCK,"แพนหางสะกดใจ","มอนสเตอร์รอบตัว 6 บล็อกเคลื่อนช้าลง","ดวงตาหางนกยูง","ศัตรูเรืองแสงและช้าลง 3 วินาที");
        put(PetCatalog.Species.AURORA_JELLY,"ลอยละล่อง","หายใจใต้น้ำ และร่อนลงช้าเมื่อตก","ช็อตแสงเหนือ","ศัตรูชะงัก (Slowness III) 1 วินาที และเรืองแสง");
    }
    private static void put(PetCatalog.Species species,String passiveName,String passive,String traitName,String trait){
        SKILLS.put(species,new Skill(passiveName,passive,traitName,trait));
    }
    static Skill of(PetCatalog.Species species){return SKILLS.get(species);}
    static String modeName(String mode){
        return switch(mode){case "stay"->"อยู่กับที่";case "perch"->"เกาะบนหัว";case "assist"->"ช่วยสู้";default->"ติดตาม";};
    }

    // ---- battle numbers ----
    static double maxHealth(PetRecord r){return switch(r.stage){case BABY->12;case MEDIUM->24;case ADULT->40;}+r.vitality;}
    static double damage(PetRecord r){return switch(r.stage){case BABY->2;case MEDIUM->4;case ADULT->6;}*(1+r.affinity/62.0);}
    /** Ticks between bites: agile pets attack more often. */
    static int attackTicks(PetRecord r){return Math.max(14,30-r.agility/2);}
    static double damageTaken(PetRecord r,double raw){
        return switch(r.species){case CRYSTAL_TORTOISE->raw*.5;case TITAN_BEETLE->raw*.6;case MAGMA_RHINO->raw*.7;default->raw;};
    }
    private static boolean night(Player owner){long time=owner.getWorld().getTime();return time>=13000&&time<23000;}
    private static boolean frosty(Material type){String name=type.name();return name.contains("ICE")||name.contains("SNOW");}
    private static double maxHealth(Player owner){var max=owner.getAttribute(Attribute.MAX_HEALTH);return max==null?20:max.getValue();}

    /**
     * Refreshes the passive effects; called about once a second while the companion is out.
     * {@code foe} is the companion's own target rule, so an aura never touches players, pets, bosses or NPCs.
     */
    static void passive(Player owner,PetRecord r,int clock,Predicate<LivingEntity> foe){
        switch(r.species){
            case ARCANE_NINE_TAILS->effect(owner,PotionEffectType.LUCK,0,60);
            case FROST_WYVERN->{if(owner.getFallDistance()>3)effect(owner,PotionEffectType.SLOW_FALLING,0,40);}
            case ASTRAL_OWL->effect(owner,PotionEffectType.NIGHT_VISION,0,600);
            case CRIMSON_CERBERUS,IGNIS_DRACONIS->effect(owner,PotionEffectType.FIRE_RESISTANCE,0,60);
            case SHADOW_PANTHER->effect(owner,PotionEffectType.SPEED,0,60);
            case BLOOD_GARGOYLE->{
                var max=owner.getAttribute(Attribute.MAX_HEALTH);
                if(max!=null&&owner.getHealth()<max.getValue()*.4)effect(owner,PotionEffectType.RESISTANCE,0,60);
            }
            case RUNIC_GOLEM->effect(owner,PotionEffectType.HASTE,0,60);
            case WORLD_TREE_ENTLING->{if(clock%600<20)effect(owner,PotionEffectType.REGENERATION,0,100);}
            case CRYSTAL_TORTOISE->{if(clock%900<20)effect(owner,PotionEffectType.ABSORPTION,0,900);}
            case ABYSSAL_MANTA->{effect(owner,PotionEffectType.WATER_BREATHING,0,60);if(owner.isInWater())effect(owner,PotionEffectType.DOLPHINS_GRACE,0,60);}
            case ALCHEMIST_MIMIC->effect(owner,PotionEffectType.HERO_OF_THE_VILLAGE,0,60);
            case CHRONO_FAIRY->effect(owner,PotionEffectType.JUMP_BOOST,0,60);
            case AETHERIUS->effect(owner,PotionEffectType.SPEED,1,60);
            case ZEPHYROS->{effect(owner,PotionEffectType.JUMP_BOOST,1,60);if(owner.getFallDistance()>3)effect(owner,PotionEffectType.SLOW_FALLING,0,40);}
            case SOLARIS->{effect(owner,PotionEffectType.NIGHT_VISION,0,600);if(clock%600<20)effect(owner,PotionEffectType.REGENERATION,0,100);}
            case FENRIR->effect(owner,PotionEffectType.STRENGTH,0,60);
            case MOCHI_SLIME->{if(clock%1200<20&&owner.getFoodLevel()<20)owner.setFoodLevel(Math.min(20,owner.getFoodLevel()+2));}
            case CLOUD_LAMB->{if(clock%400<20&&!owner.isDead())owner.setHealth(Math.min(maxHealth(owner),owner.getHealth()+2));}
            case MOON_RABBIT->{if(night(owner)){effect(owner,PotionEffectType.SPEED,0,60);effect(owner,PotionEffectType.JUMP_BOOST,0,60);}}
            case BLOOM_HEDGEHOG->{if(owner.isSneaking())effect(owner,PotionEffectType.RESISTANCE,0,30);}
            case CORAL_AXOLOTL->{if(owner.isInWater())effect(owner,PotionEffectType.CONDUIT_POWER,0,60);}
            case CLOVER_BUMBLE->{
                if(clock%100<20)for(var type:List.of(PotionEffectType.POISON,PotionEffectType.HUNGER,PotionEffectType.NAUSEA))owner.removePotionEffect(type);
            }
            case AURORA_PENGUIN->{
                Location feet=owner.getLocation();
                if(frosty(feet.getBlock().getType())||frosty(feet.clone().subtract(0,.2,0).getBlock().getType()))effect(owner,PotionEffectType.SPEED,1,30);
            }
            case STORM_RAPTOR->{if(owner.isSprinting())effect(owner,PotionEffectType.SPEED,1,30);}
            case MAGMA_RHINO->{if(owner.getHealth()<maxHealth(owner)*.5)effect(owner,PotionEffectType.STRENGTH,0,60);}
            case ECLIPSE_SCORPION->{if(owner.isSneaking())effect(owner,PotionEffectType.INVISIBILITY,0,30);}
            case RAIJIN_TIGER->{effect(owner,PotionEffectType.SPEED,0,60);effect(owner,PotionEffectType.HASTE,0,60);}
            case TITAN_BEETLE->{effect(owner,PotionEffectType.HASTE,0,60);if(owner.getFallDistance()>3)effect(owner,PotionEffectType.SLOW_FALLING,0,40);}
            case ANUBIS_JACKAL->{if(clock%1200<20)owner.giveExp(3);}
            case BONE_DRAKE->{if(night(owner))effect(owner,PotionEffectType.RESISTANCE,0,60);}
            case JADE_LUNG->{if(owner.isInWaterOrRain()){effect(owner,PotionEffectType.RESISTANCE,0,60);effect(owner,PotionEffectType.WATER_BREATHING,0,60);}}
            case CRYSTAL_STAG->{effect(owner,PotionEffectType.SPEED,0,60);if(owner.getFallDistance()>3)effect(owner,PotionEffectType.SLOW_FALLING,0,40);}
            case LUNA_MOTH->{if(night(owner)){effect(owner,PotionEffectType.NIGHT_VISION,0,600);effect(owner,PotionEffectType.LUCK,0,60);}}
            case CELESTIAL_KOI->{effect(owner,PotionEffectType.LUCK,0,60);if(owner.isInWater())effect(owner,PotionEffectType.DOLPHINS_GRACE,0,60);}
            case STARLIGHT_PEACOCK->{
                for(Entity near:owner.getNearbyEntities(6,3,6))
                    if(near instanceof Monster monster&&foe.test(monster))monster.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,30,0,true,true));
            }
            case AURORA_JELLY->{effect(owner,PotionEffectType.WATER_BREATHING,0,60);if(owner.getFallDistance()>3)effect(owner,PotionEffectType.SLOW_FALLING,0,40);}
        }
    }
    private static void effect(Player owner,PotionEffectType type,int amplifier,int ticks){
        PotionEffect current=owner.getPotionEffect(type);
        // Never shorten or weaken an effect the player got elsewhere.
        if(current!=null&&(current.getAmplifier()>amplifier||current.getAmplifier()==amplifier&&current.getDuration()>ticks/2&&current.getDuration()!=PotionEffect.INFINITE_DURATION))return;
        if(current!=null&&current.getDuration()==PotionEffect.INFINITE_DURATION)return;
        owner.addPotionEffect(new PotionEffect(type,ticks,amplifier,true,false,true));
    }
    /** Damage multiplier from the species trait; the panther's ambush doubles a first strike. */
    static double strikeMultiplier(PetRecord r,boolean firstOnTarget,LivingEntity target,RandomGenerator random){
        return switch(r.species){
            case CRIMSON_CERBERUS->1.5;
            case SHADOW_PANTHER->firstOnTarget?2:1;
            case AETHERIUS,ZEPHYROS,FENRIR->1.25;
            case MOON_RABBIT->random.nextDouble()<.25?2:1;
            case STORM_RAPTOR->{var max=target.getAttribute(Attribute.MAX_HEALTH);yield max!=null&&target.getHealth()<max.getValue()*.5?1.6:1;}
            case MAGMA_RHINO->1.2;
            case RAIJIN_TIGER->1.3;
            case CRYSTAL_STAG->1.35;
            case ANUBIS_JACKAL->Tag.ENTITY_TYPES_SENSITIVE_TO_SMITE.isTagged(target.getType())?2:1;
            default->1;
        };
    }
    /** Effects of a landed hit. */
    static void onHit(PetRecord r,Player owner,LivingEntity target,double dealt,RandomGenerator random){
        Location at=target.getLocation().add(0,target.getHeight()*.6,0);
        switch(r.species){
            case ARCANE_NINE_TAILS->target.setFireTicks(Math.max(target.getFireTicks(),60));
            case IGNIS_DRACONIS->target.setFireTicks(Math.max(target.getFireTicks(),100));
            case SOLARIS->target.setFireTicks(Math.max(target.getFireTicks(),80));
            case FROST_WYVERN->target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,60,1));
            case ASTRAL_OWL->target.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING,100,0));
            case BLOOD_GARGOYLE->{
                var max=owner.getAttribute(Attribute.MAX_HEALTH);
                if(max!=null)owner.setHealth(Math.min(max.getValue(),owner.getHealth()+dealt*.2));
                at.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR,at,3,.2,.2,.2,0);
            }
            case WORLD_TREE_ENTLING->target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,30,3));
            case ABYSSAL_MANTA->target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS,80,0));
            case ALCHEMIST_MIMIC->{if(random.nextDouble()<.08)target.getWorld().dropItemNaturally(target.getLocation(),new ItemStack(Material.GOLD_NUGGET));}
            case CHRONO_FAIRY->target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,20,2));
            case MOCHI_SLIME->target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,40,1));
            case CLOUD_LAMB->target.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION,20,0));
            case BLOOM_HEDGEHOG->target.addPotionEffect(new PotionEffect(PotionEffectType.POISON,60,0));
            case CORAL_AXOLOTL->{if(!owner.isDead())owner.setHealth(Math.min(maxHealth(owner),owner.getHealth()+1));}
            case CLOVER_BUMBLE->target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS,40,0));
            case AURORA_PENGUIN->{
                target.setFreezeTicks(Math.max(target.getFreezeTicks(),300));
                at.getWorld().spawnParticle(Particle.SNOWFLAKE,at,8,.3,.3,.3,.02);
            }
            case MAGMA_RHINO->{
                Vector push=target.getLocation().toVector().subtract(owner.getLocation().toVector()).setY(0);
                if(push.lengthSquared()>.01)target.setVelocity(target.getVelocity().add(push.normalize().multiply(.9).setY(.35)));
                at.getWorld().spawnParticle(Particle.LAVA,at,3,.2,.2,.2,0);
            }
            case ECLIPSE_SCORPION->target.addPotionEffect(new PotionEffect(PotionEffectType.POISON,60,1));
            case RAIJIN_TIGER->{
                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,10,3));
                at.getWorld().spawnParticle(Particle.ELECTRIC_SPARK,at,10,.3,.4,.3,.05);
            }
            case TITAN_BEETLE->target.setVelocity(target.getVelocity().setY(Math.max(target.getVelocity().getY(),.55)));
            case BONE_DRAKE->target.addPotionEffect(new PotionEffect(PotionEffectType.WITHER,60,0));
            case JADE_LUNG->target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS,60,1));
            case LUNA_MOTH->{
                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,40,2));
                target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS,40,0));
            }
            case CELESTIAL_KOI->{
                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,60,0));
                target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS,60,0));
            }
            case STARLIGHT_PEACOCK->{
                target.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING,60,0));
                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,60,0));
            }
            case AURORA_JELLY->{
                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,20,2));
                target.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING,40,0));
            }
            default->{}
        }
    }
    /** The golem's guard: lighter hits on its owner while it is close. */
    static double guard(PetRecord r){return r.species==PetCatalog.Species.RUNIC_GOLEM?.85:1;}
    private PetSkills(){}
}
