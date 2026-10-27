# Advance Magic 1.19.1 — Retired recipe cleanup

ข้อความ `Tried to load unrecognized recipe: advance-magic:elemental_staff_*`
เกิดจากสมุดสูตรของผู้เล่นยังมีสูตรคทาธาตุเก่าทั้งแปดที่ยกเลิกไปแล้ว
การล้างด้วย PlayerJoinEvent เกิดหลัง Minecraft อ่านไฟล์และแสดง ERROR จึงช้าเกินไป

รุ่นนี้ล้างชื่อสูตรดังกล่าวจาก `recipeBook.recipes` และ `recipeBook.toBeDisplayed`
ตั้งแต่ onLoad ก่อนโหลดโลก/ผู้เล่น และตรวจอีกครั้งก่อนล็อกอิน
ครอบคลุมไฟล์ผู้เล่น `.dat` และ `.dat_old` สำรองต้นฉบับที่แก้ไขไว้ใน
`plugins/advance-magic/retired-recipe-backups/<world>/`
สูตรคทาไม้ สูตร vanilla ข้อมูลกระเป๋าและแท็กอื่นไม่ถูกล้าง

ติดตั้ง: หยุดเซิร์ฟเวอร์ แทน `plugins/advance-magic.jar` ด้วย JAR รุ่นนี้ แล้วเปิดใหม่
เมื่อพบไฟล์เก่าจะมีข้อความ INFO แจ้งจำนวนไฟล์ที่ย้ายและสำรองไว้

ตรวจด้วย NBT fixture อิสระผ่าน 5 ข้อ และทดสอบ startup บน Paper จริง:
ไฟล์หลัก/สำรองเก่าถูกย้าย สำเนาต้นฉบับตรงทุกไบต์ ข้อมูลกระเป๋า/ชื่อ/แท็กอื่นตรงเดิม
และไม่มี unrecognized-recipe ERROR ใน log ระบบเวทผ่าน regression 2,366 ข้อ
