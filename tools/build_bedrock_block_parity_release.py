"""Build full-pose Bedrock Legendary cubes over the delivered 1.29.2 release.

Java pack and unrelated JAR/Bedrock entries are retained byte-for-byte. Candidate
only: promotion is separate from this build and follows runtime/geometry checks.
"""
import argparse
import copy
import hashlib
import io
import json
import shutil
from pathlib import Path
import zipfile
import build_plugin_jars as build
import legendary_block_assets as assets

ROOT=Path(__file__).resolve().parents[1]
BASE=ROOT/'output/legendary-bedrock-parity-20261025/before/dist/advance-magic.jar'
BASE_SHA='910b7cc139e841432a1e1209a719d395634c2c3d4ac6ec41343b6bb3d7210d8a'
VERSION='1.29.3-bedrock-block-parity'
OWNED=tuple('com/example/advancemagic/'+s for s in ('AdvanceMagicPlugin','spell/BedrockBlockModels','spell/MythicVisuals','spell/MythicLineVisuals','spell/ElementalVfx'))
sha=lambda b:hashlib.sha256(b).hexdigest()


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output',type=Path,default=ROOT/'output/legendary-bedrock-parity-20261025/build-v1')
    args=parser.parse_args();out=args.output.resolve()
    if out.exists() or not out.is_relative_to((ROOT/'output').resolve()):parser.error('Choose a fresh output/ build folder')
    assert sha(BASE.read_bytes())==BASE_SHA
    out.mkdir(parents=True)
    sources=[];hashes={}
    for prefix in OWNED:
        path=ROOT/'advance-magic/src/main/java'/(prefix+'.java');snapshot=out/'source'/(prefix+'.java')
        snapshot.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(path,snapshot);sources.append(snapshot)
        hashes[path.relative_to(ROOT).as_posix()]=sha(snapshot.read_bytes())
    build.OUT=out
    build.compile_sources('bedrock-block-parity',sources,[BASE.as_posix()]+build.pinned_classpath(build.ADVANCE_ARGS),out/'classes')
    assets.write_assets(out/'bedrock')
    with zipfile.ZipFile(BASE) as old:
        original_pack=old.read('resource-packs/advance-magic-bedrock.mcpack')
        pack_io=io.BytesIO()
        with zipfile.ZipFile(io.BytesIO(original_pack)) as previous,zipfile.ZipFile(pack_io,'w') as pack:
            manifest=json.loads(previous.read('manifest.json'));manifest['header']['version']=[2,32,0]
            for module in manifest['modules']:module['version']=[2,32,0]
            for entry in previous.infolist():
                if entry.filename!='manifest.json':pack.writestr(copy.copy(entry),previous.read(entry.filename))
            pack.writestr('manifest.json',json.dumps(manifest,separators=(',',':')),compress_type=zipfile.ZIP_DEFLATED)
            for path in sorted((out/'bedrock').rglob('*')):
                if path.is_file():pack.write(path,path.relative_to(out/'bedrock').as_posix(),compress_type=zipfile.ZIP_DEFLATED)
        bedrock=pack_io.getvalue();(out/'advance-magic-bedrock.mcpack').write_bytes(bedrock)
        pack_hashes=json.loads(old.read('resource-packs/pack-hashes.json'))
        pack_hashes['advance-magic-bedrock.mcpack']=hashlib.sha1(bedrock).hexdigest()
        resources={name:(ROOT/'advance-magic/src/main/resources'/name).read_bytes() for name in ('plugin.yml','legendary-blocks.json')}
        assert ('version: '+VERSION).encode() in resources['plugin.yml']
        resources.update({'resource-packs/advance-magic-bedrock.mcpack':bedrock,'resource-packs/pack-hashes.json':(json.dumps(pack_hashes,indent=2)+'\n').encode()})
        replaced=lambda n:any(n==p+'.class' or n.startswith(p+'$') for p in OWNED)
        jar=out/'advance-magic.jar'
        with zipfile.ZipFile(jar,'w') as release:
            for entry in old.infolist():
                if entry.filename not in resources and not replaced(entry.filename):release.writestr(copy.copy(entry),old.read(entry.filename))
            for path in sorted((out/'classes').rglob('*.class')):release.write(path,path.relative_to(out/'classes').as_posix(),compress_type=zipfile.ZIP_DEFLATED)
            for name,data in resources.items():release.writestr(name,data,compress_type=zipfile.ZIP_DEFLATED)
        with zipfile.ZipFile(jar) as release:
            changed=sorted(n for n in release.namelist() if n not in old.namelist() or release.read(n)!=old.read(n))
            assert all(n in resources or replaced(n) for n in changed)
            assert release.read('resource-packs/advance-magic-java.zip')==old.read('resource-packs/advance-magic-java.zip')
            assert not release.testzip() and len(release.namelist())==len(set(release.namelist()))
    build.check_jar(jar)
    report={'version':VERSION,'sha256':sha(jar.read_bytes()),'baseline_sha256':BASE_SHA,'source_sha256':hashes,'changed_entries':changed,
            'bedrock_pack_version':[2,32,0],'java_pack_unchanged':True,'copied_to_dist':False}
    (out/'build-manifest.json').write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(report,indent=2))


if __name__=='__main__':main()
