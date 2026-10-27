"""Bundle tested local JAR, editable weapon sources, documentation and previews."""
import argparse
import hashlib
import json
import re
import shutil
from pathlib import Path
import zipfile

ROOT=Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser(description=__doc__)
p.add_argument('--paper-server',type=Path,required=True)
p.add_argument('--integration-server',type=Path,required=True)
args=p.parse_args()
runtime=(args.paper_server/'legendary-weapons-result.txt').read_text().strip()
integration=(args.integration_server/'legendary-weapon-integration-result.txt').read_text().strip()
assert runtime.startswith('PASS ') and integration.startswith('PASS '),'Both probes must pass'
jar=ROOT/'dist/advance-magic.jar'
with zipfile.ZipFile(jar) as archive:
    version=re.search(r'^version:\s*(\S+)',archive.read('plugin.yml').decode('utf8'),re.M).group(1)
release_number=version.split('-',1)[0]
shutil.copy2(jar,ROOT/f'dist/advance-magic-{release_number}.jar')
assert jar.read_bytes()==(args.paper_server/'plugins/advance-magic.jar').read_bytes(),'Release differs from runtime-tested binary'
assert jar.read_bytes()==(args.integration_server/'plugins/advance-magic.jar').read_bytes(),'Release differs from integration-tested binary'
files=[jar,ROOT/'docs/legendary-weapons.md',
    ROOT/'tools/legendary_weapon_assets.py',ROOT/'tools/legendary_weapon_preview.py',
    *sorted((ROOT/'advance-magic/art/legendary-weapons').glob('*')),
    *sorted((ROOT/'previews/legendary-weapons').glob('*'))]
files=[path for path in files if path.is_file()]
manifest={
    'release':version,'date':'2026-10-16',
    'scope':'Local workspace build only; no production deployment or real game client rendering test.',
    'runtime':runtime,'geyser_modelengine':integration,'asset_checks':225,'native_hand_fit_checks':145,
    'client_behavior':{'java':'3D held model, 8-frame held VFX and animated GUI icon',
        'bedrock':'Native 3D attachable, 8-frame held VFX, static effect-bearing GUI icon'},
    'sha256':{f.relative_to(ROOT).as_posix():hashlib.sha256(f.read_bytes()).hexdigest() for f in files}}
manifest_path=ROOT/'dist/legendary-weapons-manifest.json'
manifest_path.write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
readme=f'''อาวุธตำนานรุ่น {version} — Excalibur / Longinus / Tyrfing / Mjolnir / Vajra

ติดตั้ง dist/advance-magic.jar แทนปลั๊กอินเดิมขณะปิดเซิร์ฟเวอร์ แล้วเริ่มใหม่
แพ็ก Java/Bedrock และ Geyser mappings ฝังใน JAR แล้ว ระบบเดิมจะจัดส่งและติดตั้งให้
ถ้าใช้ CDN ของตนเองต้องอัปโหลด resource pack รุ่นใหม่และปรับ SHA-1

คำสั่งแอดมิน:
  /magic weapons
  /magic weapon give all
  /magic weapon give mjolnir

เปิด previews/legendary-weapons/index.html เพื่อดูโมเดลและภาพเคลื่อนไหว
อ่าน docs/legendary-weapons.md สำหรับรายละเอียดและการตั้งค่า
ไฟล์ .bbmodel อยู่ใน advance-magic/art/legendary-weapons

ภาพตัวอย่างเรนเดอร์จาก asset จริง ไม่ใช่ screenshot เกม ยังต้องตรวจบน Java/Bedrock client
ไอคอน Java ขยับได้; ไอคอน Bedrock มีเอฟเฟกต์ในภาพนิ่ง; เอฟเฟกต์ตอนถือขยับทั้งสองฝั่ง
รุ่นนี้เน้นโมเดลและเอฟเฟกต์ ยังไม่มีสกิลพิเศษ
'''
bundle=ROOT/f'dist/legendary-weapons-{release_number}.zip'
with zipfile.ZipFile(bundle,'w',zipfile.ZIP_DEFLATED,compresslevel=9) as archive:
    for path in [*files,manifest_path]:
        entry=zipfile.ZipInfo(path.relative_to(ROOT).as_posix(),(2026,10,16,0,0,0));entry.compress_type=zipfile.ZIP_DEFLATED
        archive.writestr(entry,path.read_bytes())
    archive.writestr('README.txt',readme.encode('utf8'))
with zipfile.ZipFile(bundle) as archive:assert archive.testzip() is None
print('Packaged:',bundle)
print('Bytes:',bundle.stat().st_size)
