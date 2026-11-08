"""Build two optional-pack JARs over frozen delivered baselines; never deploy."""
import hashlib
import json
import re
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'tools'))
import build_plugin_jars as build

OUT = ROOT / 'output/optional-resource-packs'
SHARED = 'com/example/advancemagic/pack/delivery/'
COMMON = (SHARED+'ResourcePackManager', SHARED+'RequiredPackSettings')
MAGIC = ('com/example/advancemagic/pack/ResourcePackService',)
GARDEN = ('com/example/voidscape/pack/ResourcePackService', 'com/example/voidscape/boss/JudgePackService',
          'com/example/voidscape/pet/PetMenu', 'com/example/voidscape/trade/SanctuaryMaps')
VERSIONS = {'evergarden': '3.0.0-e2.168-packs-pets-map', 'advance-magic': '1.38.1-optional-packs'}
PET_ASSETS = ROOT / 'output/pets-bedrock-menu-assets'
PET_ENTRIES = {'resource-packs/evergarden-bedrock.mcpack': 'evergarden-bedrock.mcpack',
               'resource-packs/geyser-mappings.json': 'geyser-mappings.json',
               'resource-packs/pack-hashes.json': 'pack-hashes.json', 'judge-design.json': 'judge-design.json'}


def sha(data): return hashlib.sha256(data).hexdigest()


def main():
    for module in VERSIONS:
        base=OUT/('baseline-'+module+'.jar')
        if sha(base.read_bytes()) != sha((ROOT/'dist'/(module+'.jar')).read_bytes()):
            raise RuntimeError('dist changed: '+module)
    garden_base=OUT/'baseline-evergarden.jar'; magic_base=OUT/'baseline-advance-magic.jar'
    paths = [ROOT/'advance-magic/src/main/java'/(f+'.java') for f in COMMON+MAGIC]
    paths += [ROOT/'evergarden/src/main/java'/(f+'.java') for f in GARDEN]
    paths += [ROOT/m/'src/main/resources/config.yml' for m in VERSIONS]
    paths += [PET_ASSETS/name for name in PET_ENTRIES.values()]
    hashes={str(p):sha(p.read_bytes()) for p in paths}
    build.OUT=OUT
    classpath=[str(garden_base),str(magic_base)]+build.pinned_classpath(build.EVERGARDEN_ARGS)
    build.compile_sources('optional-packs', paths[:len(COMMON)+len(MAGIC)+len(GARDEN)], classpath, OUT/'classes')
    if any(sha(p.read_bytes())!=hashes[str(p)] for p in paths): raise RuntimeError('source changed during compilation')
    compiled={p.relative_to(OUT/'classes').as_posix():p.read_bytes() for p in (OUT/'classes').rglob('*.class')}
    def is_owned(name, families):
        return any(name == f+'.class' or name.startswith(f+'$') for f in families)
    if any(not is_owned(n,COMMON+MAGIC+GARDEN) for n in compiled): raise RuntimeError('unexpected classes')
    checks={}
    for module in VERSIONS:
        base=OUT/('baseline-'+module+'.jar'); families=COMMON+(MAGIC if module=='advance-magic' else GARDEN)
        with zipfile.ZipFile(base) as z:
            names=z.namelist()
            if z.testzip() or len(names)!=len(set(names)): raise RuntimeError('invalid baseline')
            files={n:z.read(n) for n in names}
        plugin,count=re.subn(r'(?m)^version:.*$', "version: '"+VERSIONS[module]+"'",files['plugin.yml'].decode('utf8'))
        if count!=1: raise RuntimeError('bad plugin version')
        output=OUT/(module+'.jar')
        assets = {name:(PET_ASSETS/path).read_bytes() for name,path in PET_ENTRIES.items()} if module=='evergarden' else {}
        if assets:
            with zipfile.ZipFile(OUT/'baseline-evergarden.jar') as original:
                for name, expected in {'resource-packs/evergarden-bedrock.mcpack':'3ea269839b3eb76390ab828d1168022b4ce23bf1689f6d6847c20ceb37118c55',
                                       'resource-packs/geyser-mappings.json':'c1fab8ecd70ad3b9e5153f1b7703718b7de5736521d83c76b703408216fb7bf7',
                                       'pet-portraits.zip':'b17f334967b9c42ad7fda60012133646b8adbadf49af62b7d926a315ef718090'}.items():
                    if sha(original.read(name)) != expected: raise RuntimeError('pet assets built from a different baseline: '+name)
                for path,name in [('evergarden/dist/evergarden-bedrock.mcpack','resource-packs/evergarden-bedrock.mcpack'),
                                  ('evergarden/dist/geyser-mappings.json','resource-packs/geyser-mappings.json'),
                                  ('evergarden/dist/pack-hashes.json','resource-packs/pack-hashes.json'),
                                  ('evergarden/src/main/resources/judge-design.json','judge-design.json')]:
                    if (ROOT/path).read_bytes()!=original.read(name): raise RuntimeError('pack baseline changed: '+path)
            pack_hash=json.loads(assets['resource-packs/pack-hashes.json'])['evergarden-bedrock.mcpack']
            if hashlib.sha1(assets['resource-packs/evergarden-bedrock.mcpack']).hexdigest()!=pack_hash:
                raise RuntimeError('pet pack SHA1 mismatch')
        with zipfile.ZipFile(output,'w',zipfile.ZIP_DEFLATED) as z:
            for n,data in files.items():
                if not is_owned(n,families) and n not in ('plugin.yml','config.yml') and n not in assets: z.writestr(n,data)
            z.writestr('plugin.yml',plugin.encode('utf8'))
            z.writestr('config.yml',(ROOT/module/'src/main/resources/config.yml').read_bytes())
            for n,data in assets.items(): z.writestr(n,data)
            for n,data in compiled.items():
                if is_owned(n,families): z.writestr(n,data)
        with zipfile.ZipFile(output) as z:
            if z.testzip() or len(z.namelist())!=len(set(z.namelist())): raise RuntimeError('invalid candidate')
            for n,data in files.items():
                if not is_owned(n,families) and n not in ('plugin.yml','config.yml') and n not in assets and z.read(n)!=data:
                    raise RuntimeError('unrelated entry changed: '+n)
        build.check_jar(output)
        checks[module]={'baseline_sha256':sha(base.read_bytes()),'candidate_sha256':sha(output.read_bytes()),'version':VERSIONS[module]}
    (OUT/'build-report.json').write_text(json.dumps({'sources':hashes,'jars':checks},indent=2)+'\n',encoding='utf8')
    print(json.dumps(checks,indent=2))


if __name__=='__main__': main()
