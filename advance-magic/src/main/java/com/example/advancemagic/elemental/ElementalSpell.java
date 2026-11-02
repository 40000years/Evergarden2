package com.example.advancemagic.elemental;

import com.example.advancemagic.item.ElementalStaffService.Element;
import java.util.*;

/** Stable, item-persisted spell identifiers. Multipliers describe the entire cast, not each tick. */
public enum ElementalSpell {
    FIREBALL("Fireball",Element.FIRE,12,4,1.2,Rarity.BASIC,false,"ระเบิดรัศมี 2.5 บล็อก และสะสม Burn"),
    FLAME_BURST("Flame Burst",Element.FIRE,18,8,1.3,Rarity.BASIC,false,"ระเบิดเปลวรอบตัวรัศมี 4 ผลักศัตรูและติด Burn 2 ชั้น"),
    BURNING_GROUND("Burning Ground",Element.FIRE,25,14,2,Rarity.RARE,false,"พื้นที่ไฟ 5 วินาที"),
    FLAME_DASH("Flame Dash",Element.FIRE,18,12,1.4,Rarity.RARE,false,"พุ่ง 6 บล็อกทิ้งทางเพลิง 3 วินาที ศัตรูบนทางโดนเผา เพื่อนที่เหยียบทางได้พลังโจมตี +15% และ Speed"),
    INFERNO_SPEAR("Inferno Spear",Element.FIRE,30,10,2.4,Rarity.RARE,false,"หอกไฟทะลุสูงสุด 4 เป้าหมาย"),
    MAGMA_ERUPTION("Magma Eruption",Element.FIRE,35,18,3,Rarity.EPIC,false,"ลาวาปะทุหลังเตือน 0.8 วินาที"),
    HELLFIRE_CHAIN("Hellfire Chain",Element.FIRE,28,13,2.2,Rarity.RARE,false,"ไฟชิ่งสูงสุด 5 เป้าหมาย"),
    SCORCHING_VORTEX("Scorching Vortex",Element.FIRE,42,22,3.6,Rarity.EPIC,false,"พายุไฟดูดศัตรู 5 วินาที"),
    METEOR_RAIN("Meteor Rain",Element.FIRE,65,45,16,Rarity.LEGENDARY,true,"อุกกาบาตเล็ก 6 ลูกตกรอบวง แล้วลูกใหญ่กระแทกกลาง รัศมี 6"),
    PHOENIX_REBIRTH("Phoenix Rebirth",Element.FIRE,80,120,2.5,Rarity.LEGENDARY,true,"ป้องกันความตายอัตโนมัติเมื่อถือคทาที่ประทับไว้"),
    AQUA_BULLET("Aqua Bullet",Element.WATER,10,3,.9,Rarity.BASIC,false,"กระสุนน้ำและ Wet 5 วินาที"),
    WATER_BLADE("Water Blade",Element.WATER,16,6,1.4,Rarity.BASIC,false,"คมมีดน้ำโจมตีแนวตรง"),
    HEALING_SPRING("Healing Spring",Element.WATER,25,16,2.5,Rarity.RARE,false,"บ่อน้ำศักดิ์สิทธิ์รัศมี 5 ฮีลพันธมิตร 5 ครั้งใน 5 วินาที และล้าง Burn/Slow"),
    BUBBLE_PRISON("Bubble Prison",Element.WATER,30,20,1.0,Rarity.RARE,false,"ขังศัตรูในฟอง 3 วินาที (ผู้เล่น 1.5 วินาที) ฟองแตกทำดาเมจซ้ำและติด Wet"),
    TIDAL_PUSH("Tidal Push",Element.WATER,24,10,1.5,Rarity.RARE,false,"คลื่นกรวย 8 บล็อก ผลักแรงและติด Wet ดับไฟและเร่งความเร็วเพื่อนในแนวคลื่น"),
    AQUA_SHIELD("Aqua Shield",Element.WATER,32,18,1.8,Rarity.RARE,false,"โล่น้ำให้ตัวเองและพันธมิตรรัศมี 6 นาน 8 วินาที"),
    RAIN_BLESSING("Rain Blessing",Element.WATER,42,30,3.5,Rarity.EPIC,false,"ฝนศักดิ์สิทธิ์รอบตัวรัศมี 7 ฮีลพันธมิตรต่อเนื่อง 8 วินาที"),
    WHIRLPOOL("Whirlpool",Element.WATER,35,19,2.2,Rarity.EPIC,false,"วังน้ำวนดูดศัตรู 4 วินาที"),
    TSUNAMI("Tsunami",Element.WATER,58,40,9,Rarity.LEGENDARY,true,"คลื่นยักษ์ยาว 16 บล็อก ผลักศัตรูและติด Wet"),
    LEVIATHANS_WRATH("Leviathan's Wrath",Element.WATER,75,65,16,Rarity.LEGENDARY,true,"คลื่นมังกรน้ำ 5 ระลอก ยาว 24 บล็อก ผลักและทำให้ช้า"),
    FROST_BOLT("Frost Bolt",Element.ICE,12,4,1,Rarity.BASIC,false,"Slow 20% นาน 3 วินาที"),
    ICE_SPIKE("Ice Spike",Element.ICE,22,8,1.8,Rarity.RARE,false,"หนามน้ำแข็งจากพื้น"),
    FROZEN_ARMOR("Frozen Armor",Element.ICE,26,20,0,Rarity.RARE,false,"เกราะน้ำแข็งให้ทีมรัศมี 6 ลดดาเมจ 20% นาน 8 วินาที ผู้ที่ตีเข้ามาติด Frost"),
    GLACIAL_PATH("Glacial Path",Element.ICE,20,12,1.0,Rarity.RARE,false,"ทางน้ำแข็ง 10 บล็อก 5 วินาที ศัตรูบนทางช้าลงและติด Frost เพื่อนบนทางวิ่งเร็วขึ้น"),
    CRYSTAL_PRISON("Crystal Prison",Element.ICE,38,26,1.5,Rarity.EPIC,false,"ขังศัตรูในผลึก 3 วินาที (ผู้เล่น 1.5 วินาที) ผลึกแตกเป็นระเบิดน้ำแข็งรอบตัว"),
    BLIZZARD("Blizzard",Element.ICE,46,26,3.5,Rarity.EPIC,false,"พายุหิมะรัศมี 6 บล็อก 6 วินาที"),
    ABSOLUTE_ZERO("Absolute Zero",Element.ICE,80,75,16,Rarity.LEGENDARY,true,"สนามเยือกแข็งรัศมี 6 ชะลอ แช่แข็ง แล้วระเบิดแรงขึ้นตามชั้น Frost"),
    WIND_CUTTER("Wind Cutter",Element.WIND,10,3,.9,Rarity.BASIC,false,"ใบมีดลมระยะ 18 บล็อก"),
    GALE_STEP("Gale Step",Element.WIND,14,8,.4,Rarity.BASIC,false,"พุ่ง 7 บล็อก ปล่อยลมผลักศัตรูรอบจุดเดิม และได้ Speed II 3 วินาที"),
    UPDRAFT("Updraft",Element.WIND,22,14,1.2,Rarity.RARE,false,"ยกศัตรูรัศมี 5 ขึ้นฟ้าและติด Air 2 ชั้น"),
    WIND_BARRIER("Wind Barrier",Element.WIND,25,18,0,Rarity.RARE,false,"ม่านลมให้ทีมรัศมี 8 กันดาเมจกระสุน 50% และ Speed นาน 8 วินาที พร้อมผลักศัตรูใกล้ตัว"),
    CYCLONE_TRAP("Cyclone Trap",Element.WIND,35,20,2,Rarity.EPIC,false,"ดูดศัตรูเข้าศูนย์กลาง 4 วินาที"),
    THOUSAND_WIND_BLADES("Thousand Wind Blades",Element.WIND,42,24,3.6,Rarity.EPIC,false,"ใบมีดลม 12 ใบ แบ่งความเสียหายรวม"),
    TEMPEST_DOMINION("Tempest Dominion",Element.WIND,75,65,16,Rarity.LEGENDARY,true,"พายุรัศมี 7 นาน 6 วินาที ตีทุกวินาทีและเหวี่ยงศัตรูวนรอบ"),
    STONE_BULLET("Stone Bullet",Element.EARTH,12,4,1.2,Rarity.BASIC,false,"กระสุนหินผลักเป้าหมาย"),
    EARTH_SPIKE("Earth Spike",Element.EARTH,22,9,1.9,Rarity.RARE,false,"หนามหินจากพื้น"),
    STONE_WALL("Stone Wall",Element.EARTH,26,22,0,Rarity.RARE,false,"กำแพงจำลองกั้นกระสุน 6 วินาที"),
    IRON_SKIN("Iron Skin",Element.EARTH,28,24,0,Rarity.RARE,false,"ผิวเหล็กให้ทีมรัศมี 6 ลดดาเมจ 25% และกันแรงผลักนาน 8 วินาที"),
    QUICKSAND("Quicksand",Element.EARTH,26,16,1.2,Rarity.RARE,false,"ทรายดูดรัศมี 4 ดึงศัตรูลงพื้น Slow 50% นาน 5 วินาที"),
    SEISMIC_SLAM("Seismic Slam",Element.EARTH,36,18,2.8,Rarity.EPIC,false,"กระแทกพื้นและสะสม Stagger"),
    GOLEM_GUARDIAN("Golem Guardian",Element.EARTH,50,45,3,Rarity.EPIC,false,"โกเลมผู้พิทักษ์เดินไล่ตีศัตรู ดึงความสนใจมอนสเตอร์จากทีม 15 วินาที"),
    TITANS_JUDGMENT("Titan's Judgment",Element.EARTH,80,70,16,Rarity.LEGENDARY,true,"หินยักษ์ตกกระแทกรัศมี 6 ผลักศัตรูและทำให้ช้า"),
    RADIANT_BOLT("Radiant Bolt",Element.LIGHT,12,4,1.1,Rarity.BASIC,false,"กระสุนแสงและ Radiance Mark"),
    HOLY_SANCTUARY("Holy Sanctuary",Element.LIGHT,40,28,2.5,Rarity.EPIC,false,"เขตศักดิ์สิทธิ์รัศมี 6 ฮีลและลดดาเมจทีม 15% นาน 6 วินาที"),
    PURIFICATION("Purification",Element.LIGHT,25,18,1.5,Rarity.RARE,false,"ชำระล้างทีมรัศมี 8 ล้างสถานะเสียทั้งหมด ฮีล และลดดาเมจ 20% นาน 3 วินาที"),
    DIVINE_JUDGMENT("Divine Judgment",Element.LIGHT,46,25,3.8,Rarity.EPIC,false,"เสาแสงโจมตีและระเบิด Radiance Mark"),
    HEAVENS_DESCENT("Heaven's Descent",Element.LIGHT,85,90,16,Rarity.LEGENDARY,true,"ลำแสงพิพากษาสีทองยิงต่อเนื่อง บังคับทิศทางได้ ไกล 48 บล็อก ฮีลพันธมิตรในแนวลำแสง"),
    SHADOW_BOLT("Shadow Bolt",Element.DARK,12,4,1,Rarity.BASIC,false,"กระสุนเงาและ Curse Stack"),
    LIFE_DRAIN("Life Drain",Element.DARK,28,12,2,Rarity.RARE,false,"ดูดเลือด 40% ของดาเมจจริง แบ่งครึ่งหนึ่งฮีลเพื่อนรัศมี 6"),
    CURSED_MARK("Cursed Mark",Element.DARK,24,18,0,Rarity.RARE,false,"สาปศัตรูรัศมี 3 รอบเป้า รับดาเมจเพิ่ม 15% นาน 8 วินาที และติด Curse 3 ชั้น"),
    SHADOW_DOPPELGANGER("Shadow Doppelganger",Element.DARK,45,35,3,Rarity.EPIC,false,"ร่างเงาคัดลอกเวท 50% นาน 8 วินาที ไม่คัดลอก Ultimate"),
    ABYSSAL_COLLAPSE("Abyssal Collapse",Element.DARK,80,75,16,Rarity.LEGENDARY,true,"หลุมมิติรัศมี 6 แช่แข็งศัตรูแล้วยุบระเบิด แรงขึ้นตามชั้น Curse"),
    SPARK_SHOT("Spark Shot",Element.LIGHTNING,11,3,1,Rarity.BASIC,false,"กระสุนไฟฟ้าและ Shock Stack"),
    LIGHTNING_CHAIN("Lightning Chain",Element.LIGHTNING,25,9,2,Rarity.RARE,false,"สายฟ้าชิ่งสูงสุด 4 เป้าหมาย"),
    THUNDER_DASH("Thunder Dash",Element.LIGHTNING,20,11,1.2,Rarity.RARE,false,"พุ่ง 7 บล็อกและโจมตีระหว่างทาง"),
    STATIC_FIELD("Static Field",Element.LIGHTNING,32,17,2.5,Rarity.EPIC,false,"สนามไฟฟ้า 5 วินาที ช็อตศัตรู เพื่อนในสนามได้ Speed และพลังโจมตี +10%"),
    THUNDER_SPEAR("Thunder Spear",Element.LIGHTNING,40,18,3,Rarity.EPIC,false,"หอกสายฟ้าทะลวงเป้าหมาย"),
    WRATH_OF_RAIJIN("Wrath of Raijin",Element.LIGHTNING,80,70,16,Rarity.LEGENDARY,true,"พายุสายฟ้ารัศมี 6 ฟ้าผ่าชิ่งทุกวินาที ปิดท้ายด้วยเสาสายฟ้า");

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
