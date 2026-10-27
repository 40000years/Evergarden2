"""Build dist/evergarden.jar for the pet menagerie (twenty new wild species).

Only Evergarden is compiled: against the Advance Magic JAR built beside the e2.127
release (output/pet-review-20261024/build-release) and the pinned dependency classpath,
using build_plugin_jars' compile and packaging steps. Advance Magic is not rebuilt.
Run after the asset steps:

    python evergarden/tools/pet_model_engine.py
    python tools/export_native_pet_models.py
    python evergarden/tools/pet_portraits.py
    python evergarden/tools/pet_bedrock_assets.py
    python tools/build_pet_menagerie_release.py [--no-copy]
"""
import argparse
import hashlib
import shutil
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'tools'))
import build_plugin_jars as build  # noqa: E402

ADVANCE_JAR = ROOT / 'output/pet-review-20261024/build-release/advance-magic.jar'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--no-copy', action='store_true', help='Leave the JAR in output/ instead of replacing dist/')
    parser.add_argument('--output', type=Path, default=ROOT / 'output/pet-menagerie-20261024/build')
    args = parser.parse_args()
    out = args.output.resolve()
    if not out.is_relative_to((ROOT / 'output').resolve()):
        raise ValueError('Build directory must stay under workspace output/')
    out.mkdir(parents=True, exist_ok=True)
    build.OUT = out
    assert ADVANCE_JAR.is_file(), f'Missing {ADVANCE_JAR}'

    sources = sorted((ROOT / 'evergarden/src/main/java').rglob('*.java'))
    sources += sorted((ROOT / 'advance-magic/src/main/java/com/example/advancemagic/pack/delivery').glob('*.java'))
    classpath = [ADVANCE_JAR.as_posix()] + build.pinned_classpath(build.EVERGARDEN_ARGS)
    build.compile_sources('evergarden', sources, classpath, out / 'evergarden-classes')
    jar = out / 'evergarden.jar'
    build.package('evergarden', out / 'evergarden-classes', ROOT / 'evergarden', build.EVERGARDEN_PACKS, jar)

    with zipfile.ZipFile(jar) as archive:
        names = archive.namelist()
        version = [line for line in archive.read('plugin.yml').decode('utf8').splitlines() if line.startswith('version')][0]
        blueprints = [n for n in names if n.startswith('modelengine/pets/') and n.endswith('.bbmodel')]
        assert len(blueprints) == 111, len(blueprints)
        for resource in ('native-pets.json', 'native-pets.zip', 'pet-portraits.zip', 'judge-design.json'):
            assert archive.read(resource) == (ROOT / 'evergarden/src/main/resources' / resource).read_bytes(), resource
        for pack in build.EVERGARDEN_PACKS:
            assert archive.read('resource-packs/' + pack) == (ROOT / 'evergarden/dist' / pack).read_bytes(), pack
    print(f'{jar.name}: {version}, {len(names)} entries, {jar.stat().st_size} bytes, sha256 {hashlib.sha256(jar.read_bytes()).hexdigest()}')

    if not args.no_copy:
        target = ROOT / 'dist/evergarden.jar'
        if target.exists():
            previous = out / 'previous'
            previous.mkdir(exist_ok=True)
            shutil.copy2(target, previous / target.name)
        shutil.copy2(jar, target)
        print('Replaced', target)


if __name__ == '__main__':
    main()
