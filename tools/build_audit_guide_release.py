"""Build scoped audit and guide candidates on frozen delivery baselines; no deployment."""
import hashlib
import json
import re
import sys
import zipfile
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'tools'))
import build_plugin_jars as build

OUT=ROOT/'output/audit-guide-release'
MAGIC=tuple('com/example/advancemagic/'+p for p in ('mana/ManaService','elemental/ElementalSpellService','elemental/StaffSpellService'))
GARDEN=tuple('com/example/voidscape/'+p for p in ('pet/PetStore','pet/PetStable','pet/PetBreeding','pet/PetMenu',
    'pet/PetTaming','pet/PetSystem','pet/PetCompanions','pet/PetNameForm',
    'trade/SeraphShop','trade/SeraphShopBedrockForm','guide/GuideData','guide/GuideBookType','guide/GuideMenuGui','gui/AdminTestGui'))
VERSIONS={'evergarden':'3.0.0-e2.170-audit-guide','advance-magic':'1.38.2-spell-audit'}

def sha(data): return hashlib.sha256(data).hexdigest()
def owns(name,families): return any(name==family+'.class' or name.startswith(family+'$') for family in families)

def main():
    for module in VERSIONS:
        baseline=OUT/('baseline-'+module+'.jar')
        if sha(baseline.read_bytes())!=sha((ROOT/'dist'/(module+'.jar')).read_bytes()):
            raise RuntimeError('dist changed since snapshot: '+module)
    paths={module:[ROOT/module/'src/main/java'/(name+'.java') for name in families]
           for module,families in [('advance-magic',MAGIC),('evergarden',GARDEN)]}
    source_hashes={str(p):sha(p.read_bytes()) for sources in paths.values() for p in sources}
    build.OUT=OUT
    baseline_garden=OUT/'baseline-evergarden.jar';baseline_magic=OUT/'baseline-advance-magic.jar'
    build.compile_sources('spell-audit',paths['advance-magic'],[str(baseline_magic),str(baseline_garden)]+build.pinned_classpath(build.ADVANCE_ARGS),OUT/'magic-classes')
    build.compile_sources('garden-audit',paths['evergarden'],[str(OUT/'magic-classes'),str(baseline_garden),str(baseline_magic)]+build.pinned_classpath(build.EVERGARDEN_ARGS),OUT/'garden-classes')
    if any(sha(Path(p).read_bytes())!=h for p,h in source_hashes.items()): raise RuntimeError('source changed during compilation')
    jars={}
    for module,families in [('advance-magic',MAGIC),('evergarden',GARDEN)]:
        baseline=OUT/('baseline-'+module+'.jar'); classes=OUT/('magic-classes' if module=='advance-magic' else 'garden-classes')
        compiled={p.relative_to(classes).as_posix():p.read_bytes() for p in classes.rglob('*.class')}
        if not compiled or any(not owns(n,families) for n in compiled): raise RuntimeError('unexpected compiled classes: '+module)
        for family in families:
            if family+'.class' not in compiled: raise RuntimeError('missing class '+family)
        with zipfile.ZipFile(baseline) as z:
            names=z.namelist()
            if z.testzip() or len(names)!=len(set(names)): raise RuntimeError('invalid baseline')
            files={n:z.read(n) for n in names}
        plugin,n=re.subn(r'(?m)^version:.*$',"version: '"+VERSIONS[module]+"'",files['plugin.yml'].decode('utf8'))
        if n!=1:raise RuntimeError('invalid plugin version')
        output=OUT/(module+'.jar')
        with zipfile.ZipFile(output,'w',zipfile.ZIP_DEFLATED) as z:
            for name,data in files.items():
                if not owns(name,families) and name!='plugin.yml': z.writestr(name,data)
            z.writestr('plugin.yml',plugin.encode('utf8'))
            for name,data in compiled.items():z.writestr(name,data)
        with zipfile.ZipFile(output) as z:
            if z.testzip() or len(z.namelist())!=len(set(z.namelist())): raise RuntimeError('invalid candidate')
            for name,data in files.items():
                if not owns(name,families) and name!='plugin.yml' and z.read(name)!=data:
                    raise RuntimeError('unrelated entry changed: '+name)
        build.check_jar(output)
        jars[module]={'baseline_sha256':sha(baseline.read_bytes()),'candidate_sha256':sha(output.read_bytes()),'version':VERSIONS[module]}
    (OUT/'build-report.json').write_text(json.dumps({'sources':source_hashes,'jars':jars},indent=2)+'\n',encoding='utf8')
    print(json.dumps(jars,indent=2))

if __name__=='__main__':main()
