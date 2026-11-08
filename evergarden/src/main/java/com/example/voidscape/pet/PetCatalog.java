package com.example.voidscape.pet;

import java.util.*;

/** Stable model identifiers; gameplay, ownership and mounts are intentionally separate. */
public final class PetCatalog {
    public enum Rarity {
        COMMON("ปกติ"), RARE("หายาก"), MYTHICAL("ตำนาน");
        public final String thai;
        Rarity(String thai){this.thai=thai;}
    }
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
        AURORA_JELLY("Aurora Jelly", "แมงกะพรุนออโรร่า"),
        // BEGIN EXPANSION SPECIES
        PRISM_KIRIN("Prism Kirin", "กิเลนปริซึม", Rarity.RARE),
        CLOCKWORK_RED_PANDA("Clockwork Red Panda", "แพนด้าแดงจักรกล", Rarity.RARE),
        NEBULA_WHALE("Nebula Whale", "วาฬเนบิวลา", Rarity.RARE),
        EMBER_SALAMANDER("Ember Salamander", "ซาลาแมนเดอร์ถ่านเพลิง", Rarity.RARE),
        MOONSTONE_SPHINX("Moonstone Sphinx", "สฟิงซ์มูนสโตน", Rarity.RARE),
        THUNDER_QUETZAL("Thunder Quetzal", "เควตซัลสายฟ้า", Rarity.RARE),
        SAKURA_BAKU("Sakura Baku", "บาคุซากุระ", Rarity.RARE),
        OBSIDIAN_BASILISK("Obsidian Basilisk", "บาซิลิสก์ออบซิเดียน", Rarity.RARE),
        GLACIER_NARWHAL("Glacier Narwhal", "นาร์วาฬธารน้ำแข็ง", Rarity.RARE),
        LOTUS_MANTIS("Lotus Mantis", "ตั๊กแตนตำข้าวบัวแก้ว", Rarity.RARE),
        RUNE_ARMADILLO("Rune Armadillo", "อาร์มาดิลโลอักขระ", Rarity.RARE),
        ASTRAL_SEAHORSE("Astral Seahorse", "ม้าน้ำดารา", Rarity.RARE),
        SUNFORGE_LION("Sunforge Lion", "สิงโตสุริยันหลอมทอง", Rarity.RARE),
        PAPER_CRANE("Paper Crane", "กระเรียนกระดาษวิญญาณ", Rarity.RARE),
        OPAL_CHIMERA("Opal Chimera", "ไคเมราโอปอล", Rarity.RARE),
        TWILIGHT_MOTH_DRAGON("Twilight Moth Dragon", "มังกรผีเสื้อสนธยา", Rarity.RARE),
        VERDANT_CAPRICORN("Verdant Capricorn", "แคปริคอร์นพฤกษา", Rarity.RARE),
        STRAWBERRY_HAMSTER("Strawberry Hamster", "แฮมสเตอร์สตรอว์เบอร์รี"),
        COCOA_CAPYBARA("Cocoa Capybara", "คาปิบาราโกโก้"),
        HONEY_OTTER("Honey Otter", "นากน้ำผึ้ง"),
        PEACH_SHIBA("Peach Shiba", "ชิบะพีช"),
        BLUEBERRY_BAT("Blueberry Bat", "ค้างคาวบลูเบอร์รี"),
        MINT_FERRET("Mint Ferret", "เฟอเร็ตมินต์"),
        MAPLE_RACCOON("Maple Raccoon", "แร็กคูนเมเปิล"),
        LAVENDER_CAT("Lavender Cat", "แมวลาเวนเดอร์"),
        ACORN_SQUIRREL("Acorn Squirrel", "กระรอกโอ๊ก"),
        BUTTERCREAM_CORGI("Buttercream Corgi", "คอร์กีบัตเตอร์ครีม"),
        PEBBLE_PANGOLIN("Pebble Pangolin", "ลิ่นกรวดมน"),
        MOSS_TOAD("Moss Toad", "คางคกมอส"),
        SUNFLOWER_DUCK("Sunflower Duck", "เป็ดทานตะวัน"),
        TEACUP_CHICK("Teacup Chick", "ลูกเจี๊ยบถ้วยชา"),
        PUMPKIN_FOX("Pumpkin Fox", "จิ้งจอกฟักทอง"),
        RAIN_FROG("Rain Frog", "กบฝนฟองใส"),
        SLEEPY_SLOTH("Sleepy Sloth", "สลอธขี้เซา"),
        CHERRY_RED_PANDA("Cherry Red Panda", "แพนด้าแดงเชอร์รี"),
        COTTON_CHINCHILLA("Cotton Chinchilla", "ชินชิลลาสำลี"),
        BUBBLE_SEAL("Bubble Seal", "แมวน้ำฟองนุ่ม"),
        SEASHELL_CRAB("Seashell Crab", "ปูเปลือกหอย"),
        KELP_TURTLE("Kelp Turtle", "เต่าสาหร่าย"),
        TANGERINE_GECKO("Tangerine Gecko", "ตุ๊กแกส้มหวาน"),
        DANDELION_MEERKAT("Dandelion Meerkat", "เมียร์แคตแดนดิไลออน"),
        SNOWDROP_ERMINE("Snowdrop Ermine", "เออร์มินสโนว์ดรอป"),
        CINNAMON_ALPACA("Cinnamon Alpaca", "อัลปากาอบเชย"),
        LOTUS_SNAIL("Lotus Snail", "หอยทากบัวบาน"),
        PEBBLE_PIKA("Pebble Pika", "พิก้าก้อนกรวด"),
        CORAL_SHRIMP("Coral Shrimp", "กุ้งปะการัง"),
        DEW_DRAGONFLY("Dew Dragonfly", "แมลงปอน้ำค้าง"),
        STARLING_SPARROW("Starling Sparrow", "นกกระจอกประกายดาว"),
        APRICOT_TAPIR("Apricot Tapir", "สมเสร็จแอปริคอต"),
        SAGE_KOALA("Sage Koala", "โคอาลาใบเสจ"),
        CHESTNUT_BOAR("Chestnut Boar", "หมูป่าเกาลัด"),
        PAPAYA_TOUCAN("Papaya Toucan", "ทูแคนมะละกอ"),
        LANTERN_FIREFLY("Lantern Firefly", "หิ่งห้อยโคมอุ่น"),
        PEARL_OYSTER("Pearl Oyster", "หอยมุกยิ้ม"),
        CLOVER_FAWN("Clover Fawn", "กวางน้อยโคลเวอร์"),
        DUSK_JERBOA("Dusk Jerboa", "เจอร์บัวสนธยา"),
        RIBBON_LEMUR("Ribbon Lemur", "ลีเมอร์หางริบบิ้น");
        // END EXPANSION SPECIES

        public final String id, display, thai;
        public final Rarity rarity;
        Species(String display, String thai) {
            this(display,thai,Rarity.COMMON);
        }
        Species(String display,String thai,Rarity rarity){
            this.id=name().toLowerCase(Locale.ROOT);this.display=display;this.thai=thai;
            this.rarity=ordinal()<5?Rarity.MYTHICAL:rarity;
        }
        public String model(Stage stage) {return "everpet_"+id+"_"+stage.id;}
        public boolean mythical() {return ordinal()<5;}
        public boolean rare() {return rarity==Rarity.RARE;}
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
    /** Ordinary wild species; mythical encounters have their own much rarer roll. */
    public static int wildSpecies() {return Species.values().length-5;}
    /** Every species has native Java/Bedrock models, including the mythical five. */
    public static List<Species> available() {
        return List.of(Species.values());
    }
    public static List<String> modelIds() {
        List<String> ids=new ArrayList<>();
        for(var species:available())for(var stage:Stage.values())ids.add(species.model(stage));
        return List.copyOf(ids);
    }
    private PetCatalog() {}
}
