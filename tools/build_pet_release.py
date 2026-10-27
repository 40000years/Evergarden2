"""Compile the complete current Evergarden sources and bundle the pet collection.

Uses the pinned local R4/Paper dependencies from the preceding verified release.
No deployment or copying to dist occurs until a separate validated publish step.
"""
import argparse
import os
from pathlib import Path
import subprocess
import zipfile

ROOT=Path(__file__).resolve().parents[1]


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--output',type=Path,required=True)
    p.add_argument('--classpath',type=Path,default=ROOT/'output/boss-rewards-build-v3-20261021/classpath.txt')
    args=p.parse_args();build=args.output.resolve();build.mkdir(parents=True,exist_ok=False)
    classes=build/'classes';classes.mkdir()
    deps=[ROOT/'dist/advance-magic.jar']
    deps.extend(Path(v) for v in args.classpath.read_text(encoding='utf8').split(os.pathsep)
                if Path(v).name!='advance-magic.jar')
    assert all(v.is_file() for v in deps),'Pinned dependencies must exist locally'
    sources=sorted((ROOT/'evergarden/src/main/java').rglob('*.java'))
    sources+=sorted((ROOT/'advance-magic/src/main/java/com/example/advancemagic/pack/delivery').glob('*.java'))
    options=['--release','25','-proc:none','-encoding','UTF-8','-cp',os.pathsep.join(map(str,deps)),
             '-d',str(classes),*map(str,sources)]
    argfile=build/'javac.args';argfile.write_text('\n'.join('"'+str(v).replace('\\','/')+'"' for v in options),encoding='utf8')
    subprocess.run(['java',str(ROOT/'tools/CompileJava.java'),str(argfile)],check=True)
    (build/'classpath.txt').write_text(os.pathsep.join(map(str,deps)),encoding='utf8')
    jar=build/'evergarden.jar'
    with zipfile.ZipFile(jar,'w',zipfile.ZIP_DEFLATED) as archive:
        for directory in (classes,ROOT/'evergarden/src/main/resources'):
            for source in sorted(directory.rglob('*')):
                if source.is_file():archive.write(source,source.relative_to(directory).as_posix())
        for name in ('evergarden-java.zip','judge-java.zip','evergarden-bedrock.mcpack','geyser-mappings.json','pack-hashes.json'):
            archive.write(ROOT/'evergarden/dist'/name,'resource-packs/'+name)
    with zipfile.ZipFile(jar) as archive:
        assert len([n for n in archive.namelist() if n.startswith('modelengine/pets/') and n.endswith('.bbmodel')])==51
        assert archive.read('plugin.yml')==(ROOT/'evergarden/src/main/resources/plugin.yml').read_bytes()
        assert not [n for n in archive.namelist() if n.endswith('.class') and b'Unresolved compilation problem' in archive.read(n)]
    print('Built:',jar)


if __name__=='__main__':main()
