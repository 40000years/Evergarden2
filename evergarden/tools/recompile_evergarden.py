"""Recompile evergarden.jar with latest Java sources, resources, and resource packs."""
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parents[2]

def main():
    build = Path(tempfile.mkdtemp(prefix='evergarden-build-'))
    classes = build / 'classes'
    classes.mkdir(parents=True, exist_ok=True)

    classpath_file = ROOT / 'output/boss-rewards-build-v3-20261021/classpath.txt'
    deps = [ROOT / 'dist/advance-magic.jar']
    deps.extend(Path(v) for v in classpath_file.read_text(encoding='utf8').split(os.pathsep)
                if Path(v).name != 'advance-magic.jar')
    # Pack delivery on the Minecraft port compiles against the Netty that Paper bundles.
    netty = next(ROOT.glob('output/*/libraries/io/netty'), None)
    if netty:
        deps.extend(sorted(netty.glob('netty-*/*/netty-*.jar')))

    sources = sorted((ROOT / 'evergarden/src/main/java').rglob('*.java'))
    delivery_sources = sorted((ROOT / 'advance-magic/src/main/java/com/example/advancemagic/pack/delivery').glob('*.java'))
    sources += delivery_sources

    options = ['--release', '25', '-proc:none', '-encoding', 'UTF-8', '-cp', os.pathsep.join(map(str, deps)),
               '-d', str(classes), *map(str, sources)]
    argfile = build / 'javac.args'
    argfile.write_text('\n'.join('"' + str(v).replace('\\', '/') + '"' for v in options), encoding='utf8')

    print(f"Compiling {len(sources)} Java sources...")
    subprocess.run(['java', str(ROOT / 'tools/CompileJava.java'), str(argfile)], check=True)
    print("Compilation passed cleanly!")

    jar = build / 'evergarden.jar'
    with zipfile.ZipFile(jar, 'w', zipfile.ZIP_DEFLATED) as archive:
        for directory in (classes, ROOT / 'evergarden/src/main/resources'):
            for source in sorted(directory.rglob('*')):
                if source.is_file():
                    archive.write(source, source.relative_to(directory).as_posix())
        for name in ('evergarden-java.zip', 'judge-java.zip', 'evergarden-bedrock.mcpack', 'geyser-mappings.json', 'pack-hashes.json'):
            pack_file = ROOT / 'evergarden/dist' / name
            if pack_file.exists():
                archive.write(pack_file, 'resource-packs/' + name)

    # Copy to dist/evergarden.jar
    dist_jar = ROOT / 'dist/evergarden.jar'
    shutil.copy2(jar, dist_jar)
    print(f"Updated {dist_jar} ({dist_jar.stat().st_size} bytes)")

    # Copy to server plugins directory
    serva_plugin = Path('C:/Users/User/Desktop/Serva/plugins/evergarden.jar')
    if serva_plugin.parent.exists():
        shutil.copy2(jar, serva_plugin)
        print(f"Updated Serva plugin {serva_plugin} ({serva_plugin.stat().st_size} bytes)")

    # Copy river blueprints to Serva
    serva_bp_dir = Path('C:/Users/User/Desktop/Serva/plugins/ModelEngine/blueprints')
    if serva_bp_dir.exists():
        for bp in ['arkhaia_acheron.bbmodel', 'arkhaia_acheron_echo.bbmodel', 'arkhaia_banshee_wail.bbmodel',
                   'arkhaia_cocytus.bbmodel', 'arkhaia_phlegethon.bbmodel', 'arkhaia_malefeasance.bbmodel',
                   'arkhaia_malefeasance_phase2.bbmodel', 'arkhaia_styx.bbmodel']:
            src = ROOT / 'evergarden/src/main/resources/modelengine/rivers' / bp
            if src.exists():
                shutil.copy2(src, serva_bp_dir / bp)
                print(f"Updated Serva blueprint {bp}")

if __name__ == '__main__':
    main()
