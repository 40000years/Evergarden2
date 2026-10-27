"""Compile against the existing pinned Paper/ModelEngine dependencies, package resources."""
from pathlib import Path
import argparse, shutil, subprocess, zipfile
ROOT=Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser();p.add_argument('--output',type=Path,default=ROOT/'output/sanctuary-encounters-build-20261016');a=p.parse_args()
build=a.output.resolve();build.mkdir(parents=True,exist_ok=True)
old=(ROOT/'output/banshee-hunt-build-v1-20261015/evergarden-javac.args').read_text(encoding='utf8').splitlines()
cp=old[6].strip('"');(build/'classpath.txt').write_text(cp,encoding='utf8')
classes=build/'classes';classes.mkdir(exist_ok=True)
sources=sorted((ROOT/'evergarden/src/main/java').rglob('*.java'))+sorted((ROOT/'advance-magic/src/main/java/com/example/advancemagic/pack/delivery').rglob('*.java'))
args=['--release','25','-proc:none','-encoding','UTF-8','-cp',cp,'-d',str(classes),*map(str,sources)]
argfile=build/'javac.args';argfile.write_text('\n'.join('"'+v.replace('\\','/')+'"' for v in args),encoding='utf8')
subprocess.run(['javac','@'+str(argfile)],check=True)
jar=build/'evergarden.jar'
with zipfile.ZipFile(jar,'w',zipfile.ZIP_DEFLATED) as z:
    for base in (classes,ROOT/'evergarden/src/main/resources'):
        for f in base.rglob('*'):
            if f.is_file():z.write(f,f.relative_to(base).as_posix())
    for name in ('evergarden-java.zip','judge-java.zip','evergarden-bedrock.mcpack','geyser-mappings.json','pack-hashes.json'):
        z.write(ROOT/'evergarden/dist'/name,'resource-packs/'+name)
print(jar)
