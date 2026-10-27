"""Build the current Evergarden sources and integrated preview packs using cached dependencies."""
import argparse
import os
from pathlib import Path
import shutil
import subprocess
import uuid
import zipfile

ROOT=Path(__file__).resolve().parents[1]
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--build-directory',type=Path)
parser.add_argument('--compiler-options',type=Path,default=ROOT/'output/distinct-basics-final-validation-20261013/magic-classes-javac.args')
parser.add_argument('--model-engine-api',type=Path,default=ROOT/'output/model-engine-trial-20261014/ModelEngine-4.1.1.jar')
args=parser.parse_args()
build=(args.build_directory or ROOT/'output'/('faction-preview-release-'+uuid.uuid4().hex[:8])).resolve()
build.mkdir(parents=True,exist_ok=False)
old=[line.strip().strip('"') for line in args.compiler_options.read_text(encoding='utf8').splitlines()]
deps=[Path(p) for p in old[old.index('-cp')+1].split(os.pathsep)]
classpath=os.pathsep.join(map(str,[ROOT/'dist/advance-magic.jar',args.model_engine_api.resolve(),*deps]))
classes=build/'evergarden-classes';classes.mkdir()
sources=sorted((ROOT/'evergarden/src/main/java').rglob('*.java'))
sources+=sorted((ROOT/'advance-magic/src/main/java/com/example/advancemagic/pack/delivery').glob('*.java'))
options=['--release','25','-proc:none','-encoding','UTF-8','-cp',classpath,'-d',str(classes),*map(str,sources)]
argfile=build/'evergarden-javac.args'
argfile.write_text('\n'.join('"'+str(v).replace('\\','/')+'"' for v in options),encoding='utf8')
subprocess.run(['javac','@'+str(argfile)],check=True)
shutil.copytree(ROOT/'evergarden/src/main/resources',classes,dirs_exist_ok=True)
pack_names=('evergarden-java.zip','judge-java.zip','evergarden-bedrock.mcpack','geyser-mappings.json','pack-hashes.json')
for folder,names in (('resource-packs',pack_names),('geyser',('evergarden-bedrock.mcpack','geyser-mappings.json'))):
    target=classes/folder;target.mkdir(exist_ok=True)
    for name in names:shutil.copy2(ROOT/'evergarden/dist'/name,target/name)
jar=build/'evergarden.jar'
subprocess.run(['jar','--create','--file',str(jar),'-C',str(classes),'.'],check=True)
subprocess.run(['python',str(ROOT/'tools/release_jar_checks.py'),str(jar)],check=True)
with zipfile.ZipFile(jar) as release:
    for name in pack_names:
        assert release.read('resource-packs/'+name)==(ROOT/'evergarden/dist'/name).read_bytes(), 'Embedded pack mismatch: '+name
    assert 'com/example/voidscape/boss/SeraphPreview.class' in release.namelist()
(build/'probe-classpath.txt').write_text(classpath,encoding='utf8')
print('Built:',jar,flush=True)
