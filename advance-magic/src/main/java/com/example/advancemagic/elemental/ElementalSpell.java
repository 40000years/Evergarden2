package com.example.advancemagic.elemental;

import com.example.advancemagic.item.ElementalStaffService.Element;
import java.util.*;

/** Stable, item-persisted spell identifiers. Multipliers describe the entire cast, not each tick. */
public enum ElementalSpell {
    FIREBALL("Fireball",Element.FIRE,12,4,1.2,Rarity.BASIC,false,"ระเบิดรัศมี 2.5 บล็อก และสะสม Burn"),
    FLAME_BURST("Flame Burst",Element.FIRE,18,8,1,Rarity.BASIC,false,"ระเบิดรอบตัวและผลักศัตรู"),
    BURNING_GROUND("Burning Ground",Element.FIRE,25,14,2,Rarity.RARE,false,"พื้นที่ไฟ 5 วินาที"),
    FLAME_DASH("Flame Dash",Element.FIRE,18,12,.8,Rarity.RARE,false,"พุ่ง 6 บล็อกและทิ้งทางไฟ"),
    INFERNO_SPEAR("Inferno Spear",Element.FIRE,30,10,2.4,Rarity.RARE,false,"หอกไฟทะลุสูงสุด 4 เป้าหมาย"),
    MAGMA_ERUPTION("Magma Eruption",Element.FIRE,35,18,3,Rarity.EPIC,false,"ลาวาปะทุหลังเตือน 0.8 วินาที"),
    HELLFIRE_CHAIN("Hellfire Chain",Element.FIRE,28,13,2.2,Rarity.RARE,false,"ไฟชิ่งสูงสุด 5 เป้าหมาย"),
    SCORCHING_VORTEX("Scorching Vortex",Element.FIRE,42,22,3.6,Rarity.EPIC,false,"พายุไฟดูดศัตรู 5 วินาที"),
    METEOR_RAIN("Meteor Rain",Element.FIRE,65,45,16,Rarity.LEGENDARY,true,"?????????????? 6 ??? ?????????????????????"),
    PHOENIX_REBIRTH("Phoenix Rebirth",Element.FIRE,80,120,2.5,Rarity.LEGENDARY,true,"ป้องกันความตายอัตโนมัติเมื่อถือคทาที่ประทับไว้"),
    AQUA_BULLET("Aqua Bullet",Element.WATER,10,3,.9,Rarity.BASIC,false,"กระสุนน้ำและ Wet 5 วินาที"),
    WATER_BLADE("Water Blade",Element.WATER,16,6,1.4,Rarity.BASIC,false,"คมมีดน้ำโจมตีแนวตรง"),
    HEALING_SPRING("Healing Spring",Element.WATER,25,16,1.2,Rarity.RARE,false,"บ่อฮีลพันธมิตร 5 ครั้งใน 5 วินาที"),
    BUBBLE_PRISON("Bubble Prison",Element.WATER,30,20,.5,Rarity.RARE,false,"กักศัตรูทั่วไป 2 วินาที"),
    TIDAL_PUSH("Tidal Push",Element.WATER,24,10,1.2,Rarity.RARE,false,"คลื่นน้ำผลักศัตรู"),
    AQUA_SHIELD("Aqua Shield",Element.WATER,28,18,1.8,Rarity.RARE,false,"โล่ป้องกัน 6 วินาที"),
    RAIN_BLESSING("Rain Blessing",Element.WATER,42,30,2,Rarity.EPIC,false,"ฝนฮีลรอบตัว 8 วินาที"),
    WHIRLPOOL("Whirlpool",Element.WATER,35,19,2.2,Rarity.EPIC,false,"วังน้ำวนดูดศัตรู 4 วินาที"),
    TSUNAMI("Tsunami",Element.WATER,58,40,4.5,Rarity.LEGENDARY,true,"คลื่นใหญ่ผลักและทำให้ Wet"),
    LEVIATHANS_WRATH("Leviathan's Wrath",Element.WATER,75,65,16,Rarity.LEGENDARY,true,"???????????? 5 ????? ???????????????????"),
    FROST_BOLT("Frost Bolt",Element.ICE,12,4,1,Rarity.BASIC,false,"Slow 20% นาน 3 วินาที"),
    ICE_SPIKE("Ice Spike",Element.ICE,22,8,1.8,Rarity.RARE,false,"หนามน้ำแข็งจากพื้น"),
    FROZEN_ARMOR("Frozen Armor",Element.ICE,26,20,0,Rarity.RARE,false,"ลดความเสียหาย 20% นาน 7 วินาที"),
    GLACIAL_PATH("Glacial Path",Element.ICE,20,12,.6,Rarity.RARE,false,"ทางน้ำแข็งยาว 8 บล็อก"),
    CRYSTAL_PRISON("Crystal Prison",Element.ICE,38,26,1,Rarity.EPIC,false,"กักศัตรูทั่วไป 2.5 วินาที"),
    BLIZZARD("Blizzard",Element.ICE,46,26,3.5,Rarity.EPIC,false,"พายุหิมะรัศมี 6 บล็อก 6 วินาที"),
    ABSOLUTE_ZERO("Absolute Zero",Element.ICE,80,75,16,Rarity.LEGENDARY,true,"???? ??????? ???????????????????????"),
    WIND_CUTTER("Wind Cutter",Element.WIND,10,3,.9,Rarity.BASIC,false,"ใบมีดลมระยะ 18 บล็อก"),
    GALE_STEP("Gale Step",Element.WIND,14,8,0,Rarity.BASIC,false,"พุ่ง 7 บล็อก"),
    UPDRAFT("Updraft",Element.WIND,22,14,.8,Rarity.RARE,false,"ยกศัตรูทั่วไปขึ้น"),
    WIND_BARRIER("Wind Barrier",Element.WIND,25,18,0,Rarity.RARE,false,"ลดความเสียหายกระสุน 35% นาน 5 วินาที"),
    CYCLONE_TRAP("Cyclone Trap",Element.WIND,35,20,2,Rarity.EPIC,false,"ดูดศัตรูเข้าศูนย์กลาง 4 วินาที"),
    THOUSAND_WIND_BLADES("Thousand Wind Blades",Element.WIND,42,24,3.6,Rarity.EPIC,false,"ใบมีดลม 12 ใบ แบ่งความเสียหายรวม"),
    TEMPEST_DOMINION("Tempest Dominion",Element.WIND,75,65,16,Rarity.LEGENDARY,true,"????????????????????????? 6 ?????"),
    STONE_BULLET("Stone Bullet",Element.EARTH,12,4,1.2,Rarity.BASIC,false,"กระสุนหินผลักเป้าหมาย"),
    EARTH_SPIKE("Earth Spike",Element.EARTH,22,9,1.9,Rarity.RARE,false,"หนามหินจากพื้น"),
    STONE_WALL("Stone Wall",Element.EARTH,26,22,0,Rarity.RARE,false,"กำแพงจำลองกั้นกระสุน 6 วินาที"),
    IRON_SKIN("Iron Skin",Element.EARTH,28,24,0,Rarity.RARE,false,"ลดความเสียหาย 25% นาน 7 วินาที"),
    QUICKSAND("Quicksand",Element.EARTH,26,16,.8,Rarity.RARE,false,"Slow 35% นาน 5 วินาที"),
    SEISMIC_SLAM("Seismic Slam",Element.EARTH,36,18,2.8,Rarity.EPIC,false,"กระแทกพื้นและสะสม Stagger"),
    GOLEM_GUARDIAN("Golem Guardian",Element.EARTH,50,45,3,Rarity.EPIC,false,"ผู้พิทักษ์โจมตี 15 วินาที"),
    TITANS_JUDGMENT("Titan's Judgment",Element.EARTH,80,70,16,Rarity.LEGENDARY,true,"????????????? ?????????????????????????"),
    RADIANT_BOLT("Radiant Bolt",Element.LIGHT,12,4,1.1,Rarity.BASIC,false,"กระสุนแสงและ Radiance Mark"),
    HOLY_SANCTUARY("Holy Sanctuary",Element.LIGHT,40,28,2,Rarity.EPIC,false,"เขตฮีลและลดความเสียหาย 10% นาน 6 วินาที"),
    PURIFICATION("Purification",Element.LIGHT,25,22,0,Rarity.RARE,false,"ล้างสถานะเสียที่อนุญาตสูงสุด 2 ชนิด"),
    DIVINE_JUDGMENT("Divine Judgment",Element.LIGHT,46,25,3.8,Rarity.EPIC,false,"เสาแสงโจมตีและระเบิด Radiance Mark"),
    HEAVENS_DESCENT("Heaven's Descent",Element.LIGHT,85,90,16,Rarity.LEGENDARY,true,"ลำแสงพิพากษาสีทองยิงต่อเนื่อง บังคับทิศทางได้ ไกล 48 บล็อก ฮีลพันธมิตรในแนวลำแสง"),
    SHADOW_BOLT("Shadow Bolt",Element.DARK,12,4,1,Rarity.BASIC,false,"กระสุนเงาและ Curse Stack"),
    LIFE_DRAIN("Life Drain",Element.DARK,28,12,2,Rarity.RARE,false,"ฟื้นเลือดจากความเสียหายจริง 20% ภายใต้เพดาน"),
    CURSED_MARK("Cursed Mark",Element.DARK,24,18,0,Rarity.RARE,false,"เพิ่มความเสียหายที่เป้าหมายรับ 8% นาน 6 วินาที"),
    SHADOW_DOPPELGANGER("Shadow Doppelganger",Element.DARK,45,35,3,Rarity.EPIC,false,"ร่างเงาโจมตี 8 วินาที ไม่คัดลอก Ultimate"),
    ABYSSAL_COLLAPSE("Abyssal Collapse",Element.DARK,80,75,16,Rarity.LEGENDARY,true,"??????????????????????????????? Curse Stack"),
    SPARK_SHOT("Spark Shot",Element.LIGHTNING,11,3,1,Rarity.BASIC,false,"กระสุนไฟฟ้าและ Shock Stack"),
    LIGHTNING_CHAIN("Lightning Chain",Element.LIGHTNING,25,9,2,Rarity.RARE,false,"สายฟ้าชิ่งสูงสุด 4 เป้าหมาย"),
    THUNDER_DASH("Thunder Dash",Element.LIGHTNING,20,11,1.2,Rarity.RARE,false,"พุ่ง 7 บล็อกและโจมตีระหว่างทาง"),
    STATIC_FIELD("Static Field",Element.LIGHTNING,32,17,2.5,Rarity.EPIC,false,"สนามไฟฟ้า 5 วินาที"),
    THUNDER_SPEAR("Thunder Spear",Element.LIGHTNING,40,18,3,Rarity.EPIC,false,"หอกสายฟ้าทะลวงเป้าหมาย"),
    WRATH_OF_RAIJIN("Wrath of Raijin",Element.LIGHTNING,80,70,16,Rarity.LEGENDARY,true,"???????????????????????? ????????????????");

    public enum Rarity { BASIC, RARE, EPIC, LEGENDARY }
    private final String title,description;
    private final Element element;
    private final int mana;
    private final double cooldown,multiplier;
    private final Rarity rarity;
    private final boolean ultimate;
    ElementalSpell(String title,Element element,int mana,double cooldown,double multiplier,Rarity rarity,boolean ultimate,String description){
        this.title=title;this.element=element;this.mana=mana;this.cooldown=cooldown;
        this.multiplier=multiplier;this.rarity=rarity;this.ultimate=ultimate;this.description=description;
    }
    public String id(){return name().toLowerCase(Locale.ROOT);}
    public String title(){return title;}
    public Element element(){return element;}
    public int mana(){return mana;}
    public double cooldown(){return cooldown;}
    public double multiplier(){return multiplier;}
    public boolean ultimate(){return ultimate;}
    public Rarity rarity(){return rarity;}
    public String description(){return description;}
    public static ElementalSpell parse(String value){
        if(value==null)return null;
        String normalized=value.toLowerCase(Locale.ROOT).replace("'","").replace(' ','_').replace('-','_');
        for(ElementalSpell spell:values())if(spell.id().equals(normalized))return spell;
        return null;
    }
}
