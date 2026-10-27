package com.example.voidscape.pet;

import java.util.*;

/** Stable model identifiers; gameplay, ownership and mounts are intentionally separate. */
public final class PetCatalog {
    public enum Species {
        AETHERIUS("Aetherius", "ยูนิคอร์นดาราจักร"),
        IGNIS_DRACONIS("Ignis Draconis", "มังกรเพลิงบรรพกาล"),
        ZEPHYROS("Zephyros", "กริฟฟินวายุอัสนี"),
        SOLARIS("Solaris", "ฟีนิกซ์สุริยะนิรันดร์"),
        FENRIR("Fenrir", "หมาป่าทมิฬกลืนมิติ"),
        ARCANE_NINE_TAILS("Arcane Nine-Tails", "จิ้งจอกเก้าหางมนตรา"),
        FROST_WYVERN("Frost Wyvern", "ไวเวิร์นเกล็ดน้ำแข็ง"),
        ASTRAL_OWL("Astral Owl", "นกฮูกปราชญ์ดารา"),
        CRIMSON_CERBERUS("Crimson Cerberus", "เซอร์เบอรัสสามหัว"),
        SHADOW_PANTHER("Shadow Panther", "เสือดำเขี้ยวเงา"),
        BLOOD_GARGOYLE("Blood Gargoyle", "การ์กอยล์โลหิต"),
        RUNIC_GOLEM("Runic Golem", "โกเลมศิลาจารึก"),
        WORLD_TREE_ENTLING("World-Tree Entling", "ภูตพฤกษาโลก"),
        CRYSTAL_TORTOISE("Crystal Tortoise", "เต่ามรกตปราการ"),
        ABYSSAL_MANTA("Abyssal Manta", "ปลากระเบนมิติ"),
        ALCHEMIST_MIMIC("Alchemist Mimic", "หีบสมบัติจอมตะกละ"),
        CHRONO_FAIRY("Chrono Fairy", "แฟรี่กาลเวลา"),
        // The menagerie: appended so saved ordinals and the mythical five stay put.
        MOCHI_SLIME("Mochi Slime", "สไลม์โมจิซากุระ"),
        CLOUD_LAMB("Cloud Lamb", "แกะน้อยปุยเมฆ"),
        MOON_RABBIT("Moon Rabbit", "กระต่ายจันทร์ตำขนม"),
        BLOOM_HEDGEHOG("Bloom Hedgehog", "เม่นน้อยดอกไม้บาน"),
        CORAL_AXOLOTL("Coral Axolotl", "แอกโซลอเติลปะการัง"),
        CLOVER_BUMBLE("Clover Bumble", "ผึ้งน้อยใบโคลเวอร์"),
        AURORA_PENGUIN("Aurora Penguin", "เพนกวินแสงเหนือ"),
        STORM_RAPTOR("Storm Raptor", "แร็พเตอร์อัสนี"),
        MAGMA_RHINO("Magma Rhino", "แรดเกราะลาวา"),
        ECLIPSE_SCORPION("Eclipse Scorpion", "แมงป่องสุริยคราส"),
        RAIJIN_TIGER("Raijin Tiger", "พยัคฆ์เทพอัสนี"),
        TITAN_BEETLE("Titan Beetle", "ด้วงกว่างไททัน"),
        ANUBIS_JACKAL("Anubis Jackal", "หมาจิ้งจอกอนูบิส"),
        BONE_DRAKE("Bone Drake", "มังกรกระดูกวิญญาณ"),
        JADE_LUNG("Jade Lung", "มังกรหยกเหินฟ้า"),
        CRYSTAL_STAG("Crystal Stag", "กวางเขาผลึกจันทรา"),
        LUNA_MOTH("Luna Moth", "ผีเสื้อราตรีจันทรา"),
        CELESTIAL_KOI("Celestial Koi", "ปลาคาร์ปสวรรค์"),
        STARLIGHT_PEACOCK("Starlight Peacock", "นกยูงแสงดาว"),
        AURORA_JELLY("Aurora Jelly", "แมงกะพรุนออโรร่า");

        public final String id, display, thai;
        Species(String display, String thai) {
            this.id=name().toLowerCase(Locale.ROOT);this.display=display;this.thai=thai;
        }
        public String model(Stage stage) {return "everpet_"+id+"_"+stage.id;}
        public boolean mythical() {return ordinal()<5;}
        public static Species find(String id) {
            if(id==null)return null;
            for(var value:values())if(value.id.equalsIgnoreCase(id))return value;
            return null;
        }
    }
    public enum Stage {
        BABY("ร่างเด็ก"), MEDIUM("ร่างกลาง"), ADULT("ร่างโตเต็มวัย");
        public final String id, thai;
        Stage(String thai) {this.id=name().toLowerCase(Locale.ROOT);this.thai=thai;}
        public static Stage find(String id) {
            if(id==null)return null;
            for(var value:values())if(value.id.equalsIgnoreCase(id))return value;
            return null;
        }
    }
    public static final List<String> ANIMATIONS=List.of("idle","walk","fly","sit","greet");
    /** Species that can turn up in the wild: everything but the mythical five. */
    public static int wildSpecies() {return Species.values().length-5;}
    /**
     * Species that ship models. The mythical five stay in the enum (ordinals and saved records keep
     * their meaning) but have no blueprints until the legendary tier opens.
     */
    public static List<Species> available() {
        return Arrays.stream(Species.values()).filter(species->!species.mythical()).toList();
    }
    public static List<String> modelIds() {
        List<String> ids=new ArrayList<>();
        for(var species:available())for(var stage:Stage.values())ids.add(species.model(stage));
        return List.copyOf(ids);
    }
    private PetCatalog() {}
}
