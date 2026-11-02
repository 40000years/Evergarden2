package com.example.voidscape.guide;

import java.util.List;

/** Short player guides shared by written books and the Java/Bedrock readers. */
public final class GuideData {
    private GuideData() {}

    public static final List<GuidePage> RELICS_PAGES = List.of(
        new GuidePage("เดินทางสู่ Evergarden",
            "§1§lเริ่มเดินทาง§r\n\nสร้างกรอบ Block of Quartz ขนาด 4×5 แล้วโยนดอกไม้เข้ากลางกรอบเพื่อเปิดประตู\n\nขากลับใช้ประตูที่เกาะกลาง"),
        new GuidePage("สำรวจและต่อสู้",
            "§1§lวิหารและบอส§r\n\nสำรวจวิหารใน Overworld, Nether และ The End รวมถึงบอสใน Evergarden\n\nดูสัญญาณเตือน หลบ แล้วโจมตีตอนบอสเปิดช่อง"),
        new GuidePage("นางฟ้าและแผนที่",
            "§1§lร้านนางฟ้า§r\n\nเก็บขนนกจากการสำรวจและกิจกรรม แล้วนำไปแลกของหรือแผนที่\n\nเลือกปลายทางและจ่ายก่อนรับแผนที่จริง"),
        new GuidePage("สมบัติและเอนชานต์",
            "§1§lสมบัติ§r\n\nกุญแจจากการต่อสู้ใช้เปิด Vault เพื่อรับของ\n\nสมุดบอสอาจมีเอนชานต์เกินระดับปกติ ใช้กับอุปกรณ์ที่รองรับบนทั่ง"),
        new GuidePage("สัตว์เลี้ยง",
            "§1§lสัตว์เลี้ยง§r\n\nให้อาหารที่สัตว์ชอบเพื่อผูกมิตร ดูรายการอาหารและจัดการคอกด้วย /pets\n\nเลือกตัวเพื่อเรียก เปลี่ยนโหมด หรือตั้งชื่อ"),
        new GuidePage("รังและการพักฟื้น",
            "§1§lดูแลเพื่อน§r\n\nเปิดรังใน /pets เพื่อเลือกสัตว์โตชนิดเดียวกันมาผสมพันธุ์\n\nสัตว์ที่ช่วยสู้จนเลือดหมดจะสลบและต้องพักฟื้น"),
        new GuidePage("คู่มือและแพ็กภาพ",
            "§1§lอ่านเพิ่มเติม§r\n\n/evergarden guide เปิดคู่มืออีกครั้ง\n\nหากแพ็กภาพโหลดไม่สำเร็จ ไอเทมหรือบอสบางตัวอาจแสดงไม่ครบ ติดต่อแอดมินได้")
    );

    public static final List<GuidePage> MAGIC_PAGES = List.of(
        new GuidePage("เริ่มต้นใช้เวท",
            "§1§lเริ่มใช้เวท§r\n\nคราฟต์คทาไม้เริ่มต้นได้ ดูสูตรด้วย /staff recipe wood\n\nคทาธาตุหาได้จากสมบัติหรือร้านค้า"),
        new GuidePage("ประทับคัมภีร์",
            "§1§lใส่เวทในคทา§r\n\nถือคทา เปิด /spell แล้วเลือกคัมภีร์ที่ใช้ได้เพื่อประทับลงช่อง\n\nคัมภีร์ถูกใช้เมื่อประทับสำเร็จ ตรวจช่องและเวทเดิมก่อนยืนยัน"),
        new GuidePage("ร่ายและเลือกเวท",
            "§1§lร่ายเวท§r\n\nถือคทาในมือหลักแล้วใช้ไอเท็มเพื่อร่าย\n\nย่อตัวและใช้ไอเท็มเพื่อเปลี่ยนเวท หรือเลือกช่องใน /spell"),
        new GuidePage("พัฒนาตัวละคร",
            "§1§lSkill Tree§r\n\nเปิด /skilltree เพื่อใช้แต้มพัฒนาพลัง มานา และธาตุ\n\nสำรวจ ต่อสู้ และเปิดสมบัติเพื่อสะสมความก้าวหน้า"),
        new GuidePage("มานาและคทา",
            "§1§lดูแลมานา§r\n\nการร่ายใช้มานาและมีเวลาพัก มานาฟื้นเองเมื่อเวลาผ่านไป\n\nเปิด /spell เพื่อดูเวทและจัดการคทา"),
        new GuidePage("การฟื้นฟู",
            "§1§lซ่อมคทา§r\n\nตามหาแท่นฟื้นฟูใน Whale, Garden หรือ Observatory\n\nถือ Core แตะแท่น แล้วถือคทาชำรุดแตะอีกครั้ง รอพิธีจนเสร็จ")
    );

    /** Legacy callers use the first guide. */
    public static final List<GuidePage> PAGES = RELICS_PAGES;

    public static List<GuidePage> pagesFor(GuideBookType type) {
        if (type == null) return RELICS_PAGES;
        return switch (type) {
            case RELICS -> RELICS_PAGES;
            case MAGIC -> MAGIC_PAGES;
        };
    }
}
