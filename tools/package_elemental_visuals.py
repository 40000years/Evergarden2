"""Build elemental VFX, or package an unchanged, already validated JAR."""
from pathlib import Path
import argparse
import hashlib
import json
import shutil
import subprocess
import zipfile
import uuid

ROOT=Path(__file__).resolve().parents[1]
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--package-only',action='store_true',help='Reuse the exact JAR already used by both Paper probes')
parser.add_argument('--version',default='1.16.0',help='Release version shared by the JAR and validation directories')
parser.add_argument('--label',default='rare-spell-vfx',help='Plugin version suffix')
parser.add_argument('--gallery',type=Path,help='Optional authored artwork contact sheet')
args=parser.parse_args()
if not args.version or any(c not in '0123456789.' for c in args.version):parser.error('--version must contain only digits and dots')
if not args.label or any(c not in 'abcdefghijklmnopqrstuvwxyz0123456789-' for c in args.label):parser.error('--label must be a lowercase filename-safe label')
release=args.version+'-'+args.label
jar=ROOT/'dist'/('advance-magic-'+release+'.jar')
gallery=args.gallery or ROOT/'advance-magic/art/elemental-vfx'/('rare-gallery-'+args.version+'.png')
packnames=('advance-magic-java.zip','advance-magic-bedrock.mcpack','geyser-mappings.json','pack-hashes.json')
def digest(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def sources():
    paths=[p for p in (ROOT/'advance-magic/src/main').rglob('*') if p.is_file()]
    paths += [ROOT/'advance-magic/dist'/name for name in packnames]
    paths += list((ROOT/'advance-magic/art/elemental-staffs').glob('*.bbmodel'))
    return {p.relative_to(ROOT).as_posix():digest(p) for p in sorted(paths)}
snapshot=ROOT/'dist'/('elemental-vfx-build-sources-'+args.version+'.json')
plugin_yml=(ROOT/'advance-magic/src/main/resources/plugin.yml').read_text(encoding='utf8')
if 'version: '+release not in plugin_yml.splitlines():raise RuntimeError('plugin.yml version does not match requested release '+release)
if not args.package_only:
    before=sources()
    out=ROOT/'output'/('elemental-vfx-build-'+uuid.uuid4().hex[:8]);out.mkdir(parents=True)
    prior=ROOT/'output/elemental-system-checks-20261019-release/magic-classes-javac.args'
    if not prior.is_file():raise SystemExit('Missing cached Java 25 Paper compile arguments.')
    previous=[json.loads(line) for line in prior.read_text(encoding='utf8').splitlines()]
    classes=out/'classes';classes.mkdir()
    options=['--release','25','-proc:none','-encoding','UTF-8','-cp',previous[previous.index('-cp')+1],'-d',str(classes)]
    options += [str(p) for p in sorted((ROOT/'advance-magic/src/main/java').rglob('*.java'))]
    argfile=out/'javac.args';argfile.write_text('\n'.join(json.dumps(a) for a in options),encoding='utf8')
    subprocess.run(['java',str(ROOT/'tools/CompileJava.java'),str(argfile)],check=True)
    shutil.copytree(ROOT/'advance-magic/src/main/resources',classes,dirs_exist_ok=True)
    packs=classes/'resource-packs';packs.mkdir(exist_ok=True)
    for name in packnames:shutil.copy2(ROOT/'advance-magic/dist'/name,packs/name)
    models=classes/'modelengine/elemental-staffs';models.mkdir(parents=True)
    for source in sorted((ROOT/'advance-magic/art/elemental-staffs').glob('*.bbmodel')):shutil.copy2(source,models/source.name)
    subprocess.run(['jar','--create','--file',str(jar),'-C',str(classes),'.'],check=True)
    if before!=sources():raise RuntimeError('Production source changed during compilation')
    snapshot.write_text(json.dumps(before,indent=2)+'\n',encoding='utf8')
elif snapshot.is_file() and json.loads(snapshot.read_text(encoding='utf8'))!=sources():
    raise RuntimeError('Production sources or packs changed since the recorded build')

reports={}
for directory,report in [('elemental-vfx-checks-'+args.version,'elemental-system-result.txt'),('elemental-vfx-geyser-checks-'+args.version,'elemental-treasure-result.txt')]:
    path=ROOT/'output'/directory/report
    if not path.is_file():
        if args.package_only:raise RuntimeError('Missing validation report: '+str(path))
        continue
    text=path.read_text(encoding='utf8').strip()
    if args.package_only:
        if not text.startswith('PASS'):raise RuntimeError('Validation failed: '+text)
        if digest(path.parent/'plugins/advance-magic.jar')!=digest(jar):raise RuntimeError('Probe used another JAR: '+str(path))
    reports[directory]=text
if args.package_only and not snapshot.is_file():snapshot.write_text(json.dumps(sources(),indent=2)+'\n',encoding='utf8')
with zipfile.ZipFile(jar) as z:
    for name in packnames:
        if z.read('resource-packs/'+name)!=(ROOT/'advance-magic/dist'/name).read_bytes():raise RuntimeError('Embedded pack differs: '+name)
shutil.copy2(jar,ROOT/'dist/advance-magic.jar')
art=json.loads((ROOT/'advance-magic/art/elemental-vfx/manifest.json').read_text(encoding='utf8'))
hashes=json.loads((ROOT/'advance-magic/dist/pack-hashes.json').read_text(encoding='utf8'))
manifest={'version':release,'plugin':{'bytes':jar.stat().st_size,'sha256':digest(jar)},
          'java_pack_sha1':hashes['advance-magic-java.zip'],'bedrock_pack_sha1':hashes['advance-magic-bedrock.mcpack'],
          'scroll_icons':58,'spell_vfx_signatures':len(art['spells']),'effect_textures':len(art['assets']),
          'rare_effect_textures':sum(a['id'].startswith('elemental_rare_') for a in art['assets']),
          'ultimate_heroes':sum(a['shape'].startswith('rare_hero_') for a in art['assets']),
          'validation_reports':reports,'client_visual_playtest':False,'deployment':'workspace artifacts only'}
manifest_path=ROOT/'dist/elemental-spell-visuals-manifest.json'
manifest_path.write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
bundle=ROOT/'dist'/('elemental-spell-visuals-'+args.version+'.zip')
files=[(jar,'advance-magic.jar'),(ROOT/'docs/elemental-vfx-release.md','ติดตั้ง-visuals.md'),(manifest_path,manifest_path.name),
       (gallery,'elemental-vfx-shapes.png'),
       (ROOT/'advance-magic/art/elemental-vfx/manifest.json','elemental-vfx-assets.json')]
files += [(ROOT/'advance-magic/dist'/name,name) for name in packnames]
with zipfile.ZipFile(bundle,'w',zipfile.ZIP_DEFLATED,compresslevel=9) as z:
    for path,name in files:z.write(path,name)
print(json.dumps({'plugin':str(jar),'bundle':str(bundle),'manifest':manifest},ensure_ascii=False,indent=2))
