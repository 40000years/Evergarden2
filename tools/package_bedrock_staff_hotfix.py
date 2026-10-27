"""Package the tested Bedrock-only correction and prove approved Java preservation."""
from pathlib import Path
import argparse
import hashlib
import json
import shutil
import zipfile

ROOT=Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser(description=__doc__)
p.add_argument('--build',type=Path,required=True)
p.add_argument('--runtime-server',type=Path,required=True)
p.add_argument('--integration-server',type=Path,required=True)
p.add_argument('--audit',type=Path,default=ROOT/'output/bedrock-staff-fix-20261017')
a=p.parse_args()
candidate=a.build/'advance-magic.jar'
baseline=json.loads((a.audit/'before-hashes.json').read_text())
reports=json.loads((a.audit/'asset-validation.json').read_text())

def sha(data):return hashlib.sha256(data).hexdigest()
def result(server,name):
    text=(server/name).read_text(encoding='utf8').strip()
    if not text.startswith('PASS'):raise RuntimeError(text)
    if candidate.read_bytes()!=(server/'plugins/advance-magic.jar').read_bytes():raise RuntimeError('Tested binary differs')
    return text

runtime=result(a.runtime_server,'elemental-staff-result.txt')
integration=result(a.integration_server,'elemental-staff-integration-result.txt')
if not reports['checks'] or any(row['exit_code']!=0 for row in reports['checks']):raise RuntimeError('Asset checks did not pass')
for name,digest in reports['sha256'].items():
    if sha((ROOT/name).read_bytes())!=digest:raise RuntimeError('Validated artifact changed: '+name)
preserved=[]
for name,digest in baseline.items():
    if name in ('advance-magic/dist/advance-magic-bedrock.mcpack',
                'advance-magic/art/elemental-staffs/bedrock/animations/elemental_staffs.animation.json'):
        continue
    if sha((ROOT/name).read_bytes())!=digest:raise RuntimeError('Approved artifact changed: '+name)
    preserved.append(name)

with zipfile.ZipFile(candidate) as jar,zipfile.ZipFile(ROOT/'dist/advance-magic.jar') as previous:
    class_names={name for name in jar.namelist() if name.endswith('.class')}
    if class_names!={name for name in previous.namelist() if name.endswith('.class')}:raise RuntimeError('Production class catalog changed')
    changed_classes=sorted(name for name in class_names if jar.read(name)!=previous.read(name))
    if not set(changed_classes)<={'com/example/advancemagic/item/ElementalStaffService.class'}:
        raise RuntimeError('Unrelated production bytecode changed: '+str(changed_classes))
    for name in jar.namelist():
        if name.endswith('.bbmodel') and jar.read(name)!=previous.read(name):raise RuntimeError('Editable blueprint changed: '+name)
    for name in ('advance-magic-java.zip','advance-magic-bedrock.mcpack','geyser-mappings.json','pack-hashes.json'):
        if jar.read('resource-packs/'+name)!=(ROOT/'advance-magic/dist'/name).read_bytes():raise RuntimeError('Bundled artifact differs: '+name)
    java_hash=sha(jar.read('resource-packs/advance-magic-java.zip'))
    if java_hash!=baseline['advance-magic/dist/advance-magic-java.zip']:raise RuntimeError('Java pack changed')
    broken=[name for name in jar.namelist() if name.endswith('.class') and b'Unresolved compilation problem' in jar.read(name)]
    if broken:raise RuntimeError('Compiler stubs: '+str(broken))

before=json.loads((a.audit/'before-bedrock-animation.json').read_text())['animations']
with zipfile.ZipFile(ROOT/'advance-magic/dist/advance-magic-bedrock.mcpack') as pack:
    after=json.loads(pack.read('animations/elemental_staffs.animation.json'))['animations']
    pack_version=json.loads(pack.read('manifest.json'))['header']['version']
for name,clip in before.items():
    if name.endswith(('.first','.third')) and clip!=after[name]:raise RuntimeError('Held pose changed: '+name)

(ROOT/'advance-magic/target').mkdir(exist_ok=True)
for target in (ROOT/'dist/advance-magic.jar',ROOT/'advance-magic/target/advance-magic.jar'):
    shutil.copy2(candidate,target)
files=[ROOT/'dist/advance-magic.jar',ROOT/'docs/bedrock-staff-vfx.md',
       ROOT/'advance-magic/dist/elemental-staff-sources.zip']
manifest={
    'release':'1.12.1-bedrock-staff-vfx','date':'2026-10-19','bedrock_pack':pack_version,
    'scope':'Local workspace hotfix. No production deployment or connected-client visual validation.',
    'change':'Replace Bedrock whole-staff effect sheets with crown-local 3D glints; send ambient dust only to Java viewers.',
    'runtime':runtime,'modelengine_4_1_1_geyser_and_ambient_packets':integration,
    'asset_checks':reports['checks'],'java_pack_sha256_unchanged':java_hash,
    'preserved_artifacts':len(preserved),'all_first_third_person_clips_unchanged':True,
    'changed_production_classes':changed_classes,
    'evergarden_jar_unchanged':baseline['dist/evergarden.jar'],
    'sha256':{path.relative_to(ROOT).as_posix():sha(path.read_bytes()) for path in files},
}
manifest_path=ROOT/'dist/bedrock-staff-vfx-manifest.json'
manifest_path.write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
(ROOT/'dist/elemental-staffs-manifest.json').write_text(manifest_path.read_text(encoding='utf8'),encoding='utf8')
bundle=ROOT/'dist/elemental-staffs-1.12.1.zip'
with zipfile.ZipFile(bundle,'w',zipfile.ZIP_DEFLATED,compresslevel=9) as z:
    for path in [*files,manifest_path]:
        info=zipfile.ZipInfo(path.relative_to(ROOT).as_posix(),(2026,10,19,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED
        z.writestr(info,path.read_bytes())
    z.writestr('README.txt','แก้เอฟเฟกต์คทา Bedrock บังจอ\nแทนที่เฉพาะ dist/advance-magic.jar ขณะปิดเซิร์ฟเวอร์ แล้วเริ่มใหม่\nใช้ evergarden.jar เดิมได้\nให้ Bedrock เชื่อมต่อใหม่เพื่อรับแพ็ก 2.15.1\nไฟล์ Java เดิมไม่เปลี่ยน\n'.encode('utf8'))
with zipfile.ZipFile(bundle) as z:
    if z.testzip() is not None:raise RuntimeError('Corrupt release archive')
print('Packaged:',bundle)
print(runtime+'; '+integration+'; approved Java pack byte-identical; Evergarden unchanged')
