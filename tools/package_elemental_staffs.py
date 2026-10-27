"""Publish only the exact locally tested staff-model binaries to workspace dist/."""
from pathlib import Path
import argparse
import hashlib
import json
import re
import shutil
import subprocess
import zipfile

ROOT=Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser(description=__doc__)
p.add_argument('--build',type=Path,required=True)
p.add_argument('--runtime-server',type=Path,required=True)
p.add_argument('--integration-server',type=Path,required=True)
a=p.parse_args();build=a.build.resolve()

def passed(path):
    result=path.read_text(encoding='utf8').strip()
    if not result.startswith('PASS'):raise RuntimeError('Failed probe: '+str(path))
    return result

runtime=passed(a.runtime_server/'elemental-staff-result.txt')
integration=passed(a.integration_server/'elemental-staff-integration-result.txt')
for module in ('advance-magic','evergarden'):
    candidate=build/(module+'.jar')
    tested=a.runtime_server/'plugins'/(module+'.jar')
    if candidate.read_bytes()!=tested.read_bytes():raise RuntimeError('Runtime binary differs: '+module)
if (build/'advance-magic.jar').read_bytes()!=(a.integration_server/'plugins/advance-magic.jar').read_bytes():
    raise RuntimeError('ModelEngine/Geyser-tested binary differs')

reports={}
for script,args in (
    ('advance-magic/tests/check_packs.py',['--assets-only']),
    ('advance-magic/tests/check_legendary_assets.py',['--assets-only']),
    ('advance-magic/tests/check_elemental_staff_assets.py',[]),
    ('advance-magic/tests/check_elemental_staff_camera_frame.py',[]),
    ('advance-magic/tests/check_legendary_weapons.py',[]),
    ('tools/release_jar_checks.py',[str(build/'advance-magic.jar'),str(build/'evergarden.jar')])):
    run=subprocess.run(['python',str(ROOT/script),*args],cwd=ROOT,text=True,encoding='utf8',capture_output=True)
    if run.returncode:
        raise RuntimeError(script+' failed:\n'+run.stdout+'\n'+run.stderr)
    reports[script]=run.stdout.strip()
    print(script+': '+run.stdout.strip().splitlines()[-1],flush=True)

# Preservation promises are checked against files recorded before final exports.
baseline=json.loads((ROOT/'output/elemental-staff-audit-20261017/preserved-artwork.json').read_text())
for name,expected in baseline.items():
    if hashlib.sha256((ROOT/name).read_bytes()).hexdigest()!=expected:
        raise RuntimeError('Preserved artwork changed: '+name)

for module in ('advance-magic','evergarden'):
    jar=build/(module+'.jar')
    (ROOT/module/'target').mkdir(exist_ok=True)
    shutil.copy2(jar,ROOT/module/'target'/jar.name)
    shutil.copy2(jar,ROOT/'dist'/jar.name)
with zipfile.ZipFile(build/'advance-magic.jar') as z:
    version=re.search(r'^version:\s*(\S+)',z.read('plugin.yml').decode('utf8'),re.M).group(1)

files=[ROOT/'dist/advance-magic.jar',ROOT/'dist/evergarden.jar',
    ROOT/'docs/elemental-staffs.md',ROOT/'docs/elemental-staff-cleanup.md',ROOT/'docs/elemental-staff-cleanup.json',
    ROOT/'advance-magic/ELEMENTAL_STAFF_MIGRATION.md',ROOT/'advance-magic/dist/elemental-staff-sources.zip',
    ROOT/'tools/elemental_staff_assets.py',ROOT/'tools/elemental_staff_preview.py',
    *sorted((ROOT/'previews/elemental-staffs').glob('*'))]
files=[path for path in files if path.is_file()]
manifest={
    'release':version,'date':'2026-10-17','bedrock_pack':[2,15,0],
    'scope':'Local workspace release only; no production deployment or connected-client visual test.',
    'phase':'Eight staff models, elemental catalog, native VFX and migration. Skill loadouts and elemental damage multipliers are not implemented.',
    'elements':['fire','wind','earth','ice','water','lightning','light','dark'],
    'runtime':runtime,'modelengine_4_1_1_geyser':integration,'asset_checks':reports,
    'preserved_legendary_weapon_and_flying_sources':len(baseline),
    'binary_match':'Both JARs identical to runtime-tested files; Magic JAR identical to Geyser/ModelEngine-tested file.',
    'sha256':{path.relative_to(ROOT).as_posix():hashlib.sha256(path.read_bytes()).hexdigest() for path in files},
}
manifest_path=ROOT/'dist/elemental-staffs-manifest.json'
manifest_path.write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
readme='''คทาธาตุใหม่ 8 ธาตุ — โมเดลรุ่นแรก

ติดตั้ง dist/advance-magic.jar และ dist/evergarden.jar แทนไฟล์เดิมขณะปิดเซิร์ฟเวอร์
แล้วเริ่มใหม่ แพ็ก Java/Bedrock และ mappings ฝังอยู่ใน JAR แล้ว
หลังอัปเดตให้ผู้เล่นเชื่อมต่อใหม่เพื่อรับแพ็กใหม่

/magic staffs
/magic staff give all
/magic staff give fire

ภาพรวมและภาพเคลื่อนไหว: previews/elemental-staffs/index.html
ต้นฉบับ ModelEngine 4.1.1: advance-magic/dist/elemental-staff-sources.zip
รายละเอียดการย้ายคทาเก่าและไฟล์ที่เก็บไว้: docs/elemental-staffs.md

รอบนี้ทำโมเดลและเอฟเฟกต์ก่อน ระบบหลายสกิลยังไม่เปิดใช้
พรีวิวเรนเดอร์จาก asset จริง ต้องตรวจมุมมองและตำแหน่งถือบน client อีกครั้ง
'''
bundle=ROOT/'dist/elemental-staffs-1.12.0.zip'
with zipfile.ZipFile(bundle,'w',zipfile.ZIP_DEFLATED,compresslevel=9) as z:
    for path in [*files,manifest_path]:
        info=zipfile.ZipInfo(path.relative_to(ROOT).as_posix(),(2026,10,17,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED
        z.writestr(info,path.read_bytes())
    z.writestr('README.txt',readme.encode('utf8'))
with zipfile.ZipFile(bundle) as z:
    if z.testzip() is not None:raise RuntimeError('Corrupt release archive')
print('Packaged:',bundle,flush=True)
