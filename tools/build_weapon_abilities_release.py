"""Build Evergarden e2.137: Rare Adventurer weapon powers (19 weapons; Bramblepike unchanged).

Over a delivered Evergarden JAR (--base, default the current dist/evergarden.jar) this recompiles
AdventurerWeaponService and the new AdventurerWeaponAbilities, sets the plugin.yml version, and keeps
every other JAR entry (resource packs included) byte-identical. No pack changes: the powers and their
lore are server-side.

    python tools/build_weapon_abilities_release.py [--base JAR] [--promote]
"""
import argparse
import hashlib
import json
import os
import shutil
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'tools'))
import build_plugin_jars as build  # noqa: E402

OUT = ROOT / 'output/weapon-abilities-20261025'
VERSION = '3.0.0-e2.137-weapon-abilities'
OWNED = ('com/example/voidscape/item/AdventurerWeaponService', 'com/example/voidscape/item/AdventurerWeaponAbilities')
DATE = (2026, 10, 25, 0, 0, 0)


def sha(data):
    return hashlib.sha256(data).hexdigest()


def replaced(name):
    return any(name == p + '.class' or name.startswith(p + '$') for p in OWNED)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--base', type=Path, default=ROOT / 'dist/evergarden.jar')
    parser.add_argument('--promote', action='store_true', help='Copy the JAR to dist/ and set the source plugin.yml version')
    args = parser.parse_args()
    OUT.mkdir(parents=True, exist_ok=True)
    base = OUT / 'base.jar'
    shutil.copy2(args.base, base)
    work = OUT / 'build'
    shutil.rmtree(work, ignore_errors=True)
    classes = work / 'classes'
    build.OUT = work
    sources = [ROOT / 'evergarden/src/main/java' / (p + '.java') for p in OWNED]
    classpath = [base.as_posix(), (ROOT / 'dist/advance-magic.jar').as_posix()] + build.pinned_classpath(build.EVERGARDEN_ARGS)
    build.compile_sources('weapon-abilities', sources, classpath, classes)

    with zipfile.ZipFile(base) as jar:
        lines = jar.read('plugin.yml').decode('utf8').splitlines(keepends=True)
    if not lines[1].startswith('version: '):
        raise RuntimeError('Unexpected plugin.yml layout')
    lines[1] = f"version: '{VERSION}'" + lines[1][len(lines[1].rstrip('\r\n')):]
    plugin = ''.join(lines).encode('utf8')

    jar_path = work / 'evergarden.jar'
    with zipfile.ZipFile(base) as previous, zipfile.ZipFile(jar_path, 'w', zipfile.ZIP_DEFLATED) as result:
        for entry in previous.infolist():
            if replaced(entry.filename) or entry.filename == 'plugin.yml':
                continue
            result.writestr(entry, previous.read(entry.filename))
        for path in sorted(classes.rglob('*.class')):
            name = path.relative_to(classes).as_posix()
            if not replaced(name):
                raise RuntimeError('Unexpected class ' + name)
            result.writestr(zipfile.ZipInfo(name, DATE), path.read_bytes(), compress_type=zipfile.ZIP_DEFLATED)
        result.writestr(zipfile.ZipInfo('plugin.yml', DATE), plugin, compress_type=zipfile.ZIP_DEFLATED)
    with zipfile.ZipFile(jar_path) as release, zipfile.ZipFile(base) as previous:
        if release.testzip() is not None or len(release.namelist()) != len(set(release.namelist())):
            raise RuntimeError('Invalid release archive')
        for info in previous.infolist():
            if not replaced(info.filename) and info.filename != 'plugin.yml' and release.read(info.filename) != previous.read(info.filename):
                raise RuntimeError('Unrelated entry changed: ' + info.filename)
    build.check_jar(jar_path)
    digest = sha(jar_path.read_bytes())
    if args.promote:
        source_plugin = ROOT / 'evergarden/src/main/resources/plugin.yml'
        text = source_plugin.read_bytes().decode('utf8').splitlines(keepends=True)
        text[1] = f"version: '{VERSION}'" + text[1][len(text[1].rstrip('\r\n')):]
        source_plugin.write_bytes(''.join(text).encode('utf8'))
        for target in (ROOT / 'dist/evergarden.jar', ROOT / 'dist' / ('evergarden-' + VERSION + '.jar')):
            staged = target.with_suffix('.jar.staged')
            shutil.copy2(jar_path, staged)
            os.replace(staged, target)
    report = {'version': VERSION, 'sha256': digest, 'baseline_sha256': sha(base.read_bytes()), 'promoted': args.promote}
    (OUT / 'release-build.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf8')
    print(json.dumps(report, indent=2))


if __name__ == '__main__':
    main()
