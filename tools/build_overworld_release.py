"""Compile current Evergarden sources; package separately without changing a live server."""
from pathlib import Path
import os, shutil, subprocess
ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'output/overworld-sanctuaries-habitat-build-20261015'
def main():
    OUT.mkdir(parents=True,exist_ok=True)
    old=[s.strip().strip('"') for s in (ROOT/'output/arkhaia-aggro-feedback-build-20261014/evergarden-javac.args').read_text().splitlines()]
    cp=old[old.index('-cp')+1]
    classes=OUT/'classes';classes.mkdir(exist_ok=True)
    sources=sorted((ROOT/'evergarden/src/main/java').rglob('*.java'))
    sources+=sorted((ROOT/'advance-magic/src/main/java/com/example/advancemagic/pack/delivery').rglob('*.java'))
    options=['--release','25','-proc:none','-encoding','UTF-8','-cp',cp,'-d',str(classes),*map(str,sources)]
    argfile=OUT/'javac.args';argfile.write_text('\n'.join('"'+s.replace('\\','/')+'"' for s in options))
    subprocess.run(['javac','@'+str(argfile)],check=True)
    shutil.copytree(ROOT/'evergarden/src/main/resources',classes,dirs_exist_ok=True)
    with __import__('zipfile').ZipFile(ROOT/'dist/evergarden.jar') as previous:
        # Preserve separately managed pack delivery payloads exactly as delivered.
        for name in previous.namelist():
            if name.startswith(('resource-packs/','geyser/')) and not name.endswith('/'):
                target=classes/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(previous.read(name))
    subprocess.run(['jar','--create','--file',str(OUT/'evergarden.jar'),'-C',str(classes),'.'],check=True)
    (OUT/'classpath.txt').write_text(os.pathsep.join([str(OUT/'evergarden.jar'),cp]))
    print('Compiled',len(sources),'sources;',OUT/'evergarden.jar')
if __name__=='__main__':main()
