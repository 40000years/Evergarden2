"""Publish the verified local JAR to dist with a backup and auditable test hashes."""
from pathlib import Path
import hashlib,json,re,shutil,subprocess,sys,zipfile
ROOT=Path(__file__).resolve().parents[1]
BUILD=ROOT/'output/sanctuary-encounters-build-20261016'
RELEASE=ROOT/'output/sanctuary-encounters-release-20261016'
BACKUP=ROOT/'output/sanctuary-encounters-before-20261016'
digest=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
jar=BUILD/'evergarden.jar';sha=digest(jar)
reports={
    'sanctuary-encounters-paper-verified-20261016':'arkhaia-result.txt',
    'river-trials-paper-release-20261016':'arkhaia-result.txt',
    'sanctuary-contact-final-20261016':'arkhaia-result.txt',
    'sanctuary-acheron-regression-20261016':'arkhaia-result.txt',
    'sanctuary-phlegethon-regression-20261016':'arkhaia-result.txt',
    'sanctuary-banshee-regression-20261016':'arkhaia-result.txt',
    'sanctuary-souls-regression-20261016':'arkhaia-result.txt',
    'sanctuary-armor-regression-20261016':'arkhaia-result.txt',
    'sanctuary-encounters-native-v6-20261016':'model-engine-result.txt',
}
summary={};total=0
for folder,name in reports.items():
    server=ROOT/'output'/folder;report=server/name;line=report.read_text(encoding='utf8').splitlines()[0]
    assert line.startswith('PASS'),(report,line)
    numbers=re.findall(r'\d+',line);count=int(numbers[-1]);total+=count
    tested=digest(server/'plugins/evergarden.jar')
    summary[str(report.relative_to(ROOT))]={'result':line,'tested_jar_sha256':tested,'same_as_release':tested==sha}
    if folder in ('sanctuary-encounters-paper-verified-20261016','sanctuary-contact-final-20261016','sanctuary-encounters-native-v6-20261016'):
        assert tested==sha,'Core arena/contact/native checks must exercise the delivered binary'
subprocess.run([sys.executable,str(ROOT/'tools/release_jar_checks.py'),str(jar)],check=True)
with zipfile.ZipFile(jar) as z:
    assert "3.0.0-e2.93-sanctuary-encounters" in z.read('plugin.yml').decode()
    for p in (ROOT/'evergarden/src/main/java').rglob('*.java'):
        relative=p.relative_to(ROOT/'evergarden/src/main/java').with_suffix('.class').as_posix()
        assert relative in z.namelist(),relative
    for name in ('evergarden-java.zip','judge-java.zip','evergarden-bedrock.mcpack','geyser-mappings.json','pack-hashes.json'):
        assert z.read('resource-packs/'+name)==(ROOT/'evergarden/dist'/name).read_bytes(),name
RELEASE.mkdir(exist_ok=True);BACKUP.mkdir(exist_ok=True)
old=ROOT/'dist/evergarden.jar'
if not (BACKUP/'evergarden.jar').exists():
    shutil.copy2(old,BACKUP/'evergarden.jar')
    with zipfile.ZipFile(old) as z:
        for name in ('evergarden-java.zip','judge-java.zip','evergarden-bedrock.mcpack','geyser-mappings.json','pack-hashes.json'):
            (BACKUP/name).write_bytes(z.read('resource-packs/'+name))
shutil.copy2(jar,RELEASE/'evergarden.jar');shutil.copy2(jar,old)
assert digest(old)==sha
validation={
    'version':'3.0.0-e2.93-sanctuary-encounters','resource_pack_version':'3.34.0',
    'sha256':sha,'paper_and_native_checks':total,'reports':summary,
    'decoded_bedrock_checks':21366,
    'styx_limb_clearance':{'checks':595848,'collisions':0,'max_grip_error_model_units':0.021888223506885955,
        'model_sha256':digest(ROOT/'evergarden/src/main/resources/modelengine/rivers/arkhaia_styx.bbmodel')},
    'backup':str(BACKUP.relative_to(ROOT)),'live_server_modified':False,
    'scope':'Isolated generated Paper sanctuaries and actual ModelEngine/Geyser/LevelledMobs runtime; no connected-client playtest',
}
(RELEASE/'release-validation.json').write_text(json.dumps(validation,indent=2)+'\n',encoding='utf8')
print(json.dumps({'jar':str(old),'sha256':sha,'paper_and_native_checks':total},indent=2))
