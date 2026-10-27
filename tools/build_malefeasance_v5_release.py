"""Build Evergarden e2.156: Malefeasance redrawn as anime sorceresses (no code changes).

* Phase 1, the Sin Witch: a black-haired sorceress after the Seven Deadly Sins' Merlin - towering
  wine-lined collar, violet cape, amber eyes with a sly smirk, crescent staff, grimoire and orb.
* Phase 2, the Star Imp: a cute, smug witch - huge curled hat, lilac twin tails, starry cyan eyes
  with a fang grin, frilled star-print dress, striped stockings, bat wings, devil tail, star wand.
* Both come from evergarden/tools/malefeasance_v5.py (painted with anime_forge.py) on the same
  bones and 17 clips; Bedrock content 3.62.0 via tools/update_river_bedrock.py.

Over e2.154-pet-grounding this replaces only the two Malefeasance blueprints, the Bedrock pack,
pack-hashes.json, judge-design.json and plugin.yml; every other entry stays byte-identical.

    python tools/build_malefeasance_v5_release.py [--base JAR] [--promote]
"""
import argparse
import hashlib
import io
import json
import os
import shutil
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'tools'))
import build_plugin_jars as build  # noqa: E402

OUT = ROOT / 'output/malefeasance-v5-20261027'
VERSION = '3.0.0-e2.156-malefeasance-v5'
BASE = ROOT / 'dist/evergarden-3.0.0-e2.154-pet-grounding.jar'
BASE_SHA = 'e02c41b46c90d5ffb37e80b010edc058a086de03bdbbdcf627e3a6a3e0084345'
DATE = (2026, 10, 27, 0, 0, 0)
RES = ROOT / 'evergarden/src/main/resources'
FILES = {
    'modelengine/rivers/arkhaia_malefeasance.bbmodel': RES / 'modelengine/rivers/arkhaia_malefeasance.bbmodel',
    'modelengine/rivers/arkhaia_malefeasance_phase2.bbmodel': RES / 'modelengine/rivers/arkhaia_malefeasance_phase2.bbmodel',
    'resource-packs/evergarden-bedrock.mcpack': ROOT / 'evergarden/dist/evergarden-bedrock.mcpack',
    'resource-packs/pack-hashes.json': ROOT / 'evergarden/dist/pack-hashes.json',
    'judge-design.json': RES / 'judge-design.json',
}


def sha(data):
    return hashlib.sha256(data).hexdigest()


def with_version(text):
    lines = text.splitlines(keepends=True)
    i = next(k for k, line in enumerate(lines) if line.startswith('version: '))
    lines[i] = f"version: '{VERSION}'" + lines[i][len(lines[i].rstrip('\r\n')):]
    return ''.join(lines)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--base', type=Path, default=BASE)
    parser.add_argument('--promote', action='store_true')
    args = parser.parse_args()
    OUT.mkdir(parents=True, exist_ok=True)
    base = OUT / 'base.jar'
    shutil.copy2(args.base, base)
    if args.base == BASE and sha(base.read_bytes()) != BASE_SHA:
        raise RuntimeError('e2.154 changed since this release was prepared')
    work = OUT / 'build'
    shutil.rmtree(work, ignore_errors=True)
    work.mkdir(parents=True)
    with zipfile.ZipFile(base) as jar:
        names = set(jar.namelist())
        plugin = jar.read('plugin.yml').decode('utf8')
    if not all(name in names for name in FILES):
        raise RuntimeError('Base JAR lacks an expected entry')
    replacements = {name: path.read_bytes() for name, path in FILES.items()}
    replacements['plugin.yml'] = with_version(plugin).encode('utf8')
    design = json.loads(replacements['judge-design.json'])
    with zipfile.ZipFile(io.BytesIO(replacements['resource-packs/evergarden-bedrock.mcpack'])) as pack:
        if '.'.join(map(str, json.loads(pack.read('manifest.json'))['header']['version'])) != design['bedrock-version']:
            raise RuntimeError('judge-design.json and the Bedrock pack disagree on the version')
    jar_path = work / 'evergarden.jar'
    with zipfile.ZipFile(base) as previous, zipfile.ZipFile(jar_path, 'w', zipfile.ZIP_DEFLATED) as result:
        for entry in previous.infolist():
            if entry.filename not in replacements:
                result.writestr(entry, previous.read(entry.filename))
        for name, data in replacements.items():
            result.writestr(zipfile.ZipInfo(name, DATE), data,
                            compress_type=zipfile.ZIP_STORED if name.endswith(('.zip', '.mcpack')) else zipfile.ZIP_DEFLATED)
    with zipfile.ZipFile(jar_path) as release, zipfile.ZipFile(base) as previous:
        if release.testzip() is not None or len(release.namelist()) != len(set(release.namelist())):
            raise RuntimeError('Invalid release archive')
        for info in previous.infolist():
            if info.filename not in replacements and release.read(info.filename) != previous.read(info.filename):
                raise RuntimeError('Unrelated entry changed: ' + info.filename)
    build.check_jar(jar_path)
    digest = sha(jar_path.read_bytes())
    if args.promote:
        delivered = ROOT / 'dist/evergarden.jar'
        # Another session may have released meanwhile: never overwrite a delivered JAR we did not build on.
        if delivered.exists() and sha(delivered.read_bytes()) != sha(base.read_bytes()):
            raise RuntimeError('dist/evergarden.jar is not the base this release was built on')
        source = RES / 'plugin.yml'
        source.write_bytes(with_version(source.read_bytes().decode('utf8')).encode('utf8'))
        for target in (delivered, ROOT / 'dist' / ('evergarden-' + VERSION + '.jar')):
            staged = target.with_suffix('.jar.staged')
            shutil.copy2(jar_path, staged)
            os.replace(staged, target)
    report = {'version': VERSION, 'sha256': digest, 'baseline_sha256': sha(base.read_bytes()),
              'bedrock_version': design['bedrock-version'], 'promoted': args.promote}
    (OUT / 'release-build.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf8')
    print(json.dumps(report, indent=2))


if __name__ == '__main__':
    main()
