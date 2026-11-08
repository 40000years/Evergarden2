"""Package the editable collection and generate the Thai roster from shipped data."""
import hashlib
import json
from pathlib import Path
import zipfile

ROOT=Path(__file__).resolve().parents[1]
RES=ROOT/'evergarden/src/main/resources'
OUT=ROOT/'output/pet-expansion-release'
HABITAT={'GARDENS':'สวน Evergarden','GLACIER':'เทือกเขาธารน้ำแข็ง','ANCIENT_FOREST':'ป่าโบราณ','LUNAR_CITY':'นครจันทราที่สาบสูญ'}


def main():
    roster=json.loads((RES/'pet-expansion.json').read_text(encoding='utf8'))['species']
    report=json.loads((OUT/'sculpt-report.json').read_text(encoding='utf8'))
    build_report=json.loads((OUT/'build-report.json').read_text(encoding='utf8')) if (OUT/'build-report.json').exists() else {}
    validation=build_report.get('validation',{})
    lines=['# Evergarden · สัตว์เลี้ยงชุดใหม่ 57 ชนิด','',
        'รุ่น `3.0.0-e2.198-pet-collection` เพิ่มสัตว์ปกติ 40 ชนิดและหายาก 17 ชนิด รวมสัตว์ทั้งหมด 94 ชนิด (ปกติ 72 / หายาก 17 / ตำนาน 5) ทุกชนิดมีร่างเด็ก กลาง และโตเต็มวัย รวมทั้งเกม 282 ร่าง','',
        f'ชุดใหม่มี 171 โมเดลที่ปั้นแยกตามวัย รวม {report["parts"]:,} ชิ้นส่วน เทกเจอร์วาดวัสดุและแสงเงา 1024×1024 พร้อมท่า idle / walk / fly / sit / greet ข้อต่อเดินตามหัวและหางใช้ได้ทั้ง Java และ Bedrock','',
        'ดูภาพจริงได้ที่ [แกลเลอรีสามวัย](../previews/pet-collection/index.html) ภาพเป็นการเรนเดอร์โมเดลและเทกเจอร์ที่บรรจุจริง ไม่ใช่ภาพจากไคลเอนต์เกม','',
        'สัตว์หายากรวมกันมีโอกาสเริ่มต้น 8% ของการพบที่ไม่ใช่ตำนาน ปรับที่ `pets.rare.chance` ในถิ่นของแต่ละชนิดมีน้ำหนักการพบเพิ่ม 3 เท่า และไว้ใจจากอาหารช้ากว่าสัตว์ปกติ 25% สัตว์ตำนานยังใช้ระบบเฉพาะของตัวเอง','',
        'บัฟทำงานจากสัตว์ตัวแรกในระยะ 32 บล็อก ตัวที่สองเป็นเพื่อนเดินตาม รอบฟื้นฟูที่ระบุด้านล่างเป็นค่าร่างโตเต็มวัย ร่างกลางมีรอบนานขึ้น 1.4 เท่า (ปัดเป็นวินาที) ร่างเด็ก 2 เท่า สกิลโจมตีใช้เฉพาะโหมดช่วยสู้ คูลดาวน์ 4 วินาที','',
        'ติดตั้ง JAR แทน Evergarden ตัวเดิมและรีสตาร์ตเซิร์ฟเวอร์ แพ็กสำหรับสัตว์ใหม่รวมอยู่ใน JAR แล้ว ผู้เล่นต้องรับแพ็กใหม่ แพ็กจะใช้แหล่งดาวน์โหลดตามการตั้งค่าของเซิร์ฟเวอร์','']
    for tier,title in (('rare','ระดับหายาก 17 ชนิด'),('common','ระดับปกติใหม่ 40 ชนิด')):
        lines+=['## '+title,'']
        for entry in (e for e in roster if e['rarity']==tier):
            lines+=['### '+entry['thai']+' · '+entry['display'],'',entry['design'],'',
                    '- **บัฟ · '+entry['passiveName']+':** '+entry['passive'],
                    '- **ช่วยสู้ · '+entry['traitName']+':** '+entry['trait'],
                    '- **อาหาร:** `'+entry['food']+'` · **ถิ่น:** '+HABITAT[entry['habitat']],
                    '- **สามวัย:** '+' → '.join(entry['growth']),
                    '- **ตัวอย่างแอดมิน:** `/evergarden pets wild spawn '+entry['id']+' baby`','']
    lines+=['## การตรวจและขอบเขต','',
            'ตรวจ blueprint ทุกชิ้น เทกเจอร์ UV กระดูกและท่าทาง ตรวจการส่งออก Java จากพิกัดต้นฉบับ และรายการ Bedrock/รูปเมนู รายงานอยู่ใน `output/pet-expansion-release` การทดสอบ Paper ใช้ผู้เล่นจำลอง ยังไม่ได้ตรวจภาพด้วยไคลเอนต์ Java/Bedrock ที่เชื่อมต่อจริง','',
            'โมเดลเดิม 111 ร่างและเนื้อหาแพ็กเดิมที่ไม่เกี่ยวกับรายการ/เวอร์ชันมีไบต์เดิม ชุดแก้คู่หูเว้นระยะ e2.197 รวมอยู่ในรุ่นนี้','']
    if validation:lines += ['ผล Paper ของ JAR ที่ส่ง: `'+validation['report']+'`','',
                           'ตรวจส่งออก Java 1,965,829 ข้อ · Bedrock ใหม่ 605,225 ข้อ · ภาพเมนูตรงกัน 564 รายการ','']
    (ROOT/'docs/pet-collection.md').write_text('\n'.join(lines),encoding='utf8')
    target=ROOT/'dist/evergarden-pet-collection-models.zip'
    with zipfile.ZipFile(target,'w',zipfile.ZIP_DEFLATED) as archive:
        archive.write(ROOT/'docs/pet-collection.md','README-th.md');archive.write(RES/'pet-expansion.json','pet-expansion.json')
        for entry in roster:
            for stage in ('baby','medium','adult'):
                model=f'everpet_{entry["id"]}_{stage}'
                archive.write(RES/'modelengine/pets'/(model+'.bbmodel'),'models/'+model+'.bbmodel')
                archive.write(ROOT/'evergarden/art/pets'/(model+'.png'),'textures/'+model+'.png')
        for name in ('pet_expansion_catalog.py','pet_expansion_rare_sculpt.py','pet_expansion_common_a_sculpt.py','pet_expansion_common_b_sculpt.py','pet_expansion_rare.json','pet_expansion_common_a.json','pet_expansion_common_b.json','pet_rare_sculpt.py','pet_menagerie_sculpt.py','pet_mythical_sculpt.py','pet_model_engine.py','pet_paint.py'):
            archive.write(ROOT/'evergarden/tools'/name,'authoring/'+name)
    artifact=dict(version='3.0.0-e2.198-pet-collection',new_species=57,rare_added=17,common_added=40,total_species=94,
                  total_variants=282,new_variants=171,sculpted_parts=report['parts'],texture_size=[1024,1024],
                  collection_sha256=hashlib.sha256(target.read_bytes()).hexdigest(),species=roster,connected_client_playtest=False)
    if validation:artifact.update(jar_sha256=build_report['candidate_sha256'],validation=validation)
    (ROOT/'dist/pet-collection-manifest.json').write_text(json.dumps(artifact,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
    print(f'Packaged {len(roster)} species / 171 editable models; {target.stat().st_size:,} bytes')


if __name__=='__main__':main()
