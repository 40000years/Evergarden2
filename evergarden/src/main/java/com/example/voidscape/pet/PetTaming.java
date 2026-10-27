package com.example.voidscape.pet;

import com.example.voidscape.VoidscapePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import java.time.Duration;
import java.util.*;
import java.util.random.RandomGenerator;

/**
 * Befriending wild pets with their favourite food. Trust rises per feeding, slower for older
 * forms; the first player to feed a pet holds it for a minute so nobody can snatch it. Older
 * forms also need a Lead to bring home. Works the same from Java and Bedrock: a right-click.
 */
final class PetTaming {
    private static final Map<PetCatalog.Species,Material> DEFAULT_FOOD=new EnumMap<>(PetCatalog.Species.class);
    static {
        DEFAULT_FOOD.put(PetCatalog.Species.ARCANE_NINE_TAILS,Material.SWEET_BERRIES);
        DEFAULT_FOOD.put(PetCatalog.Species.FROST_WYVERN,Material.COD);
        DEFAULT_FOOD.put(PetCatalog.Species.ASTRAL_OWL,Material.GLOW_BERRIES);
        DEFAULT_FOOD.put(PetCatalog.Species.CRIMSON_CERBERUS,Material.CRIMSON_FUNGUS);
        DEFAULT_FOOD.put(PetCatalog.Species.SHADOW_PANTHER,Material.MUTTON);
        DEFAULT_FOOD.put(PetCatalog.Species.BLOOD_GARGOYLE,Material.REDSTONE);
        DEFAULT_FOOD.put(PetCatalog.Species.RUNIC_GOLEM,Material.LAPIS_LAZULI);
        DEFAULT_FOOD.put(PetCatalog.Species.WORLD_TREE_ENTLING,Material.BONE_MEAL);
        DEFAULT_FOOD.put(PetCatalog.Species.CRYSTAL_TORTOISE,Material.SEAGRASS);
        DEFAULT_FOOD.put(PetCatalog.Species.ABYSSAL_MANTA,Material.PRISMARINE_CRYSTALS);
        DEFAULT_FOOD.put(PetCatalog.Species.ALCHEMIST_MIMIC,Material.GOLD_NUGGET);
        DEFAULT_FOOD.put(PetCatalog.Species.CHRONO_FAIRY,Material.HONEY_BOTTLE);
        DEFAULT_FOOD.put(PetCatalog.Species.MOCHI_SLIME,Material.SUGAR);
        DEFAULT_FOOD.put(PetCatalog.Species.CLOUD_LAMB,Material.WHEAT);
        DEFAULT_FOOD.put(PetCatalog.Species.MOON_RABBIT,Material.CARROT);
        DEFAULT_FOOD.put(PetCatalog.Species.BLOOM_HEDGEHOG,Material.BROWN_MUSHROOM);
        DEFAULT_FOOD.put(PetCatalog.Species.CORAL_AXOLOTL,Material.TROPICAL_FISH);
        DEFAULT_FOOD.put(PetCatalog.Species.CLOVER_BUMBLE,Material.DANDELION);
        DEFAULT_FOOD.put(PetCatalog.Species.AURORA_PENGUIN,Material.SALMON);
        DEFAULT_FOOD.put(PetCatalog.Species.STORM_RAPTOR,Material.CHICKEN);
        DEFAULT_FOOD.put(PetCatalog.Species.MAGMA_RHINO,Material.MAGMA_CREAM);
        DEFAULT_FOOD.put(PetCatalog.Species.ECLIPSE_SCORPION,Material.SPIDER_EYE);
        DEFAULT_FOOD.put(PetCatalog.Species.RAIJIN_TIGER,Material.PORKCHOP);
        DEFAULT_FOOD.put(PetCatalog.Species.TITAN_BEETLE,Material.MELON_SLICE);
        DEFAULT_FOOD.put(PetCatalog.Species.ANUBIS_JACKAL,Material.ROTTEN_FLESH);
        DEFAULT_FOOD.put(PetCatalog.Species.BONE_DRAKE,Material.BONE);
        DEFAULT_FOOD.put(PetCatalog.Species.JADE_LUNG,Material.DRIED_KELP);
        DEFAULT_FOOD.put(PetCatalog.Species.CRYSTAL_STAG,Material.APPLE);
        DEFAULT_FOOD.put(PetCatalog.Species.LUNA_MOTH,Material.LILY_OF_THE_VALLEY);
        DEFAULT_FOOD.put(PetCatalog.Species.CELESTIAL_KOI,Material.BREAD);
        DEFAULT_FOOD.put(PetCatalog.Species.STARLIGHT_PEACOCK,Material.PUMPKIN_SEEDS);
        DEFAULT_FOOD.put(PetCatalog.Species.AURORA_JELLY,Material.GLOW_INK_SAC);
        // Legendary companions only arrive by other means but still eat.
        DEFAULT_FOOD.put(PetCatalog.Species.AETHERIUS,Material.GOLDEN_CARROT);
        DEFAULT_FOOD.put(PetCatalog.Species.IGNIS_DRACONIS,Material.BLAZE_POWDER);
        DEFAULT_FOOD.put(PetCatalog.Species.ZEPHYROS,Material.FEATHER);
        DEFAULT_FOOD.put(PetCatalog.Species.SOLARIS,Material.GLOWSTONE_DUST);
        DEFAULT_FOOD.put(PetCatalog.Species.FENRIR,Material.BEEF);
    }
    private final VoidscapePlugin plugin;
    private final WildPetSpawns wild;
    private final PetCompanions companions;
    private final PetBodies bodies;
    private final RandomGenerator random=new Random();
    private final Map<UUID,Long> feedingNotice=new HashMap<>();

    PetTaming(VoidscapePlugin plugin,WildPetSpawns wild,PetCompanions companions,PetBodies bodies){
        this.plugin=plugin;this.wild=wild;this.companions=companions;this.bodies=bodies;
    }
    static Material defaultFood(PetCatalog.Species species){return DEFAULT_FOOD.get(species);}
    Material food(PetCatalog.Species species){
        String configured=plugin.getConfig().getString("pets.taming.food."+species.id);
        Material material=configured==null?null:Material.matchMaterial(configured);
        return material!=null&&material.isItem()?material:DEFAULT_FOOD.get(species);
    }
    static String foodName(Material material){
        String raw=material.name().toLowerCase(Locale.ROOT).replace('_',' ');
        StringBuilder name=new StringBuilder();
        for(String word:raw.split(" "))name.append(name.isEmpty()?"":" ").append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        return name.toString();
    }
    static String foodNameThai(Material material){
        if(material==null)return "ไม่มี";
        return switch(material){
            case SWEET_BERRIES -> "สวีทเบอร์รี่ (Sweet Berries)";
            case COD -> "ปลาค็อดดิบ (Raw Cod)";
            case GLOW_BERRIES -> "โกลว์เบอร์รี่ (Glow Berries)";
            case CRIMSON_FUNGUS -> "เห็ดคริมสัน (Crimson Fungus)";
            case MUTTON -> "เนื้อแกะดิบ (Raw Mutton)";
            case REDSTONE -> "ผงเรดสโตน (Redstone Dust)";
            case LAPIS_LAZULI -> "ลาพิส ลาซูลี (Lapis Lazuli)";
            case BONE_MEAL -> "ผงกระดูก (Bone Meal)";
            case SEAGRASS -> "หญ้าทะเล (Seagrass)";
            case PRISMARINE_CRYSTALS -> "คริสตัลปริซมารีน (Prismarine Crystals)";
            case GOLD_NUGGET -> "เม็ดทองคำ (Gold Nugget)";
            case HONEY_BOTTLE -> "ขวดน้ำผึ้ง (Honey Bottle)";
            case GOLDEN_CARROT -> "แครอททองคำ (Golden Carrot)";
            case BLAZE_POWDER -> "ผงเบลซ (Blaze Powder)";
            case FEATHER -> "ขนนก (Feather)";
            case GLOWSTONE_DUST -> "ผงโกลว์สโตน (Glowstone Dust)";
            case BEEF -> "เนื้อวัวดิบ (Raw Beef)";
            case SUGAR -> "น้ำตาล (Sugar)";
            case WHEAT -> "ข้าวสาลี (Wheat)";
            case CARROT -> "แครอท (Carrot)";
            case BROWN_MUSHROOM -> "เห็ดสีน้ำตาล (Brown Mushroom)";
            case TROPICAL_FISH -> "ปลาเขตร้อน (Tropical Fish)";
            case DANDELION -> "ดอกแดนดิไลออน (Dandelion)";
            case SALMON -> "ปลาแซลมอนดิบ (Raw Salmon)";
            case CHICKEN -> "เนื้อไก่ดิบ (Raw Chicken)";
            case MAGMA_CREAM -> "ครีมแมกมา (Magma Cream)";
            case SPIDER_EYE -> "ตาแมงมุม (Spider Eye)";
            case PORKCHOP -> "เนื้อหมูดิบ (Raw Porkchop)";
            case MELON_SLICE -> "แตงโมชิ้น (Melon Slice)";
            case ROTTEN_FLESH -> "เนื้อเน่า (Rotten Flesh)";
            case BONE -> "กระดูก (Bone)";
            case DRIED_KELP -> "สาหร่ายอบแห้ง (Dried Kelp)";
            case APPLE -> "แอปเปิล (Apple)";
            case LILY_OF_THE_VALLEY -> "ดอกลิลลี่ออฟเดอะวัลเลย์ (Lily of the Valley)";
            case BREAD -> "ขนมปัง (Bread)";
            case PUMPKIN_SEEDS -> "เมล็ดฟักทอง (Pumpkin Seeds)";
            case GLOW_INK_SAC -> "ถุงหมึกเรืองแสง (Glow Ink Sac)";
            default -> foodName(material);
        };
    }
    /** Plain, unmodified items only: never a renamed, enchanted or plugin-tagged item on the same base. */
    static boolean plain(ItemStack item){return item!=null&&!item.getType().isAir()&&item.isSimilar(new ItemStack(item.getType()));}
    /** Eats one; honey leaves its glass bottle behind, as when a player drinks it. */
    static void consume(Player player,ItemStack hand){
        if(player.getGameMode()==GameMode.CREATIVE)return;
        Material type=hand.getType();hand.setAmount(hand.getAmount()-1);
        if(type==Material.HONEY_BOTTLE)player.getInventory().addItem(new ItemStack(Material.GLASS_BOTTLE))
            .values().forEach(left->player.getWorld().dropItemNaturally(player.getLocation(),left));
    }
    boolean isFood(Material material){
        for(var species:PetCatalog.Species.values())if(food(species)==material)return true;
        return false;
    }
    /** A player holding this pet's favourite food. */
    boolean tempted(PetBody body,Player player){ItemStack hand=player.getInventory().getItemInMainHand();return hand.getType()==food(body.species)&&plain(hand);}
    private int setting(String key,int fallback,int min,int max){return plugin.integer("pets.taming."+key,fallback,min,max);}

    /** Handles a right-click on a wild pet; false lets the pet simply greet. */
    boolean feed(PetBody body,Player player){
        if(!body.wild())return false;
        long now=System.currentTimeMillis();
        boolean mine=player.getUniqueId().equals(body.tamer)&&now<body.tamerUntil;
        if(mine&&body.trust>=100){bringHome(body,player);return true;}
        ItemStack hand=player.getInventory().getItemInMainHand();
        Material wanted=food(body.species);
        String wantedNameThai=foodNameThai(wanted);
        if(hand.getType()==wanted&&!plain(hand)){plugin.message(player,"สัตว์กินได้เฉพาะ "+wantedNameThai+" ธรรมดา ไม่ใช่ไอเทมพิเศษ");return true;}
        if(hand.getType()!=wanted){
            if(body.tamer!=null&&!mine&&now<body.tamerUntil){
                Player other=Bukkit.getPlayer(body.tamer);
                String otherName=other!=null?other.getName():"ผู้เล่นอื่น";
                plugin.message(player,"มีผู้เล่น ("+otherName+") กำลังผูกมิตรกับตัวนี้อยู่ · รอสักครู่");
                player.sendActionBar(Component.text(body.species.thai+" · กำลังผูกมิตรกับ "+otherName,NamedTextColor.YELLOW));
                player.playSound(body.location(),Sound.ENTITY_VILLAGER_NO,.6f,1.4f);
                return true;
            }
            if(isFood(hand.getType())){
                plugin.message(player,body.species.thai+" ("+body.species.display+") ไม่สนใจสิ่งนี้ · ชอบ "+wantedNameThai);
                player.sendActionBar(Component.text(body.species.thai+" ชอบ "+wantedNameThai,NamedTextColor.YELLOW));
                player.playSound(body.location(),Sound.ENTITY_VILLAGER_NO,.6f,1.4f);
                return true;
            }
            showTamingInfo(body,player,wanted,wantedNameThai);
            return true;
        }
        if(body.tamer!=null&&!mine&&now<body.tamerUntil){plugin.message(player,"มีผู้เล่นอื่นกำลังผูกมิตรกับตัวนี้อยู่ · รอสักครู่");return true;}
        long wait=setting("feed-cooldown-ms",2500,500,30000)-(now-body.lastFeed);
        if(wait>0){
            feedingNotice.values().removeIf(sent->sent<now-30_000);
            if(now-feedingNotice.getOrDefault(player.getUniqueId(),0L)>=1000){
                feedingNotice.put(player.getUniqueId(),now);
                player.sendActionBar(Component.text(body.species.thai+" กำลังกิน · ป้อนได้อีกใน "+String.format(Locale.ROOT,"%.1f",wait/1000.0)+" วินาที",NamedTextColor.YELLOW));
            }
            return true;
        }
        PetStable stable=companions.stable(player);
        if(stable.locked){plugin.message(player,"อ่านข้อมูลคอกสัตว์ของคุณไม่ได้ · แจ้งแอดมิน");return true;}
        if(stable.full()){plugin.message(player,"คอกสัตว์เต็ม ("+stable.pets.size()+"/"+stable.slots+") · ปล่อยตัวหนึ่งใน /pet ก่อน");return true;}
        feedingNotice.remove(player.getUniqueId());
        consume(player,hand);
        body.tamer=player.getUniqueId();body.tamerUntil=now+60_000;body.lastFeed=now;
        Location at=body.location().add(0,1,0);
        boolean picky=body.stage!=PetCatalog.Stage.BABY&&random.nextDouble()<.25;
        if(picky){
            at.getWorld().spawnParticle(Particle.SMOKE,at,8,.3,.3,.3,.01);
            player.playSound(at,Sound.ENTITY_FOX_SNIFF,.8f,.8f);
            if(body.stage==PetCatalog.Stage.ADULT&&random.nextDouble()<.08){flee(body,player);return true;}
        }else{
            int gain=switch(body.stage){case BABY->18+random.nextInt(13);case MEDIUM->9+random.nextInt(8);case ADULT->4+random.nextInt(5);};
            body.trust=Math.min(100,body.trust+gain);
            at.getWorld().spawnParticle(Particle.HEART,at,1+gain/8,.35,.3,.35,0);
            player.playSound(at,Sound.ENTITY_GENERIC_EAT,.7f,1.3f);
            // A happy wild pet sometimes sheds a Seraph Feather (daily-capped).
            double drop=plugin.getConfig().getDouble("seraph-trader.feathers.wild-feed-chance",.2);
            if(plugin.seraphTrader()!=null&&random.nextDouble()<drop)plugin.seraphTrader().feathers().grant(player,1,body.species.display+" ทำขนนกร่วง",true);
        }
        body.updateNametag();
        wild.greet(body,player,true);
        player.sendActionBar(Component.text(body.species.display+" · ความไว้ใจ "+bar(body.trust)+" "+body.trust+"%",NamedTextColor.LIGHT_PURPLE));
        if(body.trust>=100)bringHome(body,player);
        return true;
    }
    void showTamingInfo(PetBody body,Player player,Material wanted,String wantedNameThai){
        player.playSound(body.location(),Sound.ENTITY_FOX_SNIFF,.8f,1.2f);
        Location at=body.location().add(0,1,0);
        at.getWorld().spawnParticle(Particle.HAPPY_VILLAGER,at,4,.3,.3,.3,0);
        player.sendActionBar(Component.text("✦ " + body.species.thai + " · อาหารที่ชอบ: " + wantedNameThai, NamedTextColor.GOLD));
        player.sendMessage(Component.empty());
        player.sendMessage(Component.text("✦ สัตว์เลี้ยงป่า: ", NamedTextColor.GOLD)
            .append(Component.text(body.species.thai + " (" + body.species.display + ")", NamedTextColor.AQUA))
            .append(Component.text(" · " + body.stage.thai, NamedTextColor.LIGHT_PURPLE)));
        player.sendMessage(Component.text(" • อาหารที่ชอบ: ", NamedTextColor.GRAY)
            .append(Component.text(wantedNameThai, NamedTextColor.YELLOW)));
        player.sendMessage(Component.text(" • ความไว้ใจ: ", NamedTextColor.GRAY)
            .append(Component.text(bar(body.trust) + " " + body.trust + "%", NamedTextColor.LIGHT_PURPLE)));
        if(body.stage == PetCatalog.Stage.BABY){
            player.sendMessage(Component.text(" • วิธีเทม: ", NamedTextColor.GRAY)
                .append(Component.text("ถือ " + wantedNameThai + " ในมือหลัก แล้วคลิกขวาเพื่อป้อนอาหารจนไว้ใจครบ 100%", NamedTextColor.WHITE)));
        } else {
            player.sendMessage(Component.text(" • วิธีเทม: ", NamedTextColor.GRAY)
                .append(Component.text("ถือ " + wantedNameThai + " แล้วคลิกขวาเพื่อป้อนจนครบ 100% ", NamedTextColor.WHITE))
                .append(Component.text("(ตัวโตแล้วต้องใช้เชือกจูง Lead 1 เส้นเพื่อพากลับ)", NamedTextColor.YELLOW)));
        }
        player.sendMessage(Component.empty());
        wild.greet(body,player,true);
    }
    static String bar(int value){int filled=Math.round(value/10f);return "▮".repeat(filled)+"▯".repeat(10-filled);}
    private void flee(PetBody body,Player player){
        Location at=body.location().add(0,.6,0);
        at.getWorld().spawnParticle(Particle.CLOUD,at,24,.5,.4,.5,.02);
        player.playSound(at,Sound.ENTITY_RABBIT_JUMP,1,.8f);
        plugin.message(player,body.species.display+" ตกใจแล้วหนีไป · ตัวโตเต็มวัยไว้ใจคนยาก");
        wild.release(body);bodies.remove(body);
    }
    private void bringHome(PetBody body,Player player){
        PetStable stable=companions.stable(player);
        if(stable.full()){plugin.message(player,"คอกสัตว์เต็ม · ปล่อยตัวหนึ่งใน /pet ก่อนพากลับ");return;}
        if(body.stage!=PetCatalog.Stage.BABY&&player.getGameMode()!=GameMode.CREATIVE){
            int slot=-1;ItemStack[] contents=player.getInventory().getStorageContents();
            for(int i=0;i<contents.length&&slot<0;i++)if(contents[i]!=null&&contents[i].getType()==Material.LEAD&&plain(contents[i]))slot=i;
            if(slot<0){plugin.message(player,body.species.display+" ไว้ใจคุณแล้ว · ต้องมีเชือกจูง (Lead) 1 เส้นเพื่อพาตัวที่โตแล้วกลับ แล้วคลิกอีกครั้ง");return;}
            ItemStack lead=player.getInventory().getItem(slot);lead.setAmount(lead.getAmount()-1);
        }
        PetRecord record=PetRecord.tamed(body.species,body.stage,random);
        record.growth=companions.growthFloor(body.stage);
        Location at=body.location().add(0,1,0);
        wild.release(body);
        companions.adopt(player,record,body);
        at.getWorld().spawnParticle(Particle.HEART,at,12,.6,.5,.6,0);
        at.getWorld().spawnParticle(Particle.END_ROD,at,20,.5,.6,.5,.03);
        player.playSound(at,Sound.UI_TOAST_CHALLENGE_COMPLETE,.8f,1.3f);
        player.showTitle(Title.title(Component.text("ผูกมิตรสำเร็จ!",NamedTextColor.GOLD),
            Component.text(body.species.display+" · "+body.stage.thai,NamedTextColor.AQUA),Title.Times.times(Duration.ofMillis(200),Duration.ofSeconds(2),Duration.ofMillis(500))));
        plugin.getLogger().info("Pet tamed: "+player.getName()+" "+body.species.id+"/"+body.stage.id+" "+record.id);
        if(plugin.seraphTrader()!=null)plugin.seraphTrader().feathers().firstTime(player,"tame."+body.species.id,
            plugin.integer("seraph-trader.feathers.first-tame",5,0,64),"เทม "+body.species.display+" ครั้งแรก");
    }
}
