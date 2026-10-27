"""Build Advance Magic 1.30.0: spell levels I–VII and scroll fusion.

Every non-Ultimate elemental spell gets levels I–VII; today's spells are level V. Each level down deals ×0.8,
cooldown runs +0.25 of the level-VII cooldown per level below VII, and mana scales 0.6–1.2. Level VII is
always Epic; level V keeps each spell's published rarity, so existing staffs and scrolls keep working.
Evergarden structure chests roll levels by chest profile; /spell fuse merges two equal scrolls into the next
level.

Over a delivered JAR (--base, default dist/advance-magic.jar) this recompiles only the owned classes, sets
the plugin.yml version and keeps every other entry (resource packs included) byte-identical.

    python tools/build_spell_levels_release.py [--base JAR] [--promote]
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

OUT = ROOT / 'output/spell-levels-20261026'
VERSION = '1.30.0-spell-levels'
OWNED = tuple('com/example/advancemagic/' + p for p in (
    'elemental/SpellLevels', 'elemental/StaffSpellService', 'elemental/ElementalSpellService', 'elemental/SpellCommand',
    'elemental/SpellMenu', 'elemental/ElementalAcquisitionService', 'CastListener', 'mana/ManaService'))
DATE = (2026, 10, 26, 0, 0, 0)


def sha(data):
    return hashlib.sha256(data).hexdigest()


def replaced(name):
    return any(name == p + '.class' or name.startswith(p + '$') for p in OWNED)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--base', type=Path, default=ROOT / 'dist/advance-magic.jar')
    parser.add_argument('--promote', action='store_true', help='Copy the JAR to dist/ and set the source plugin.yml version')
    args = parser.parse_args()
    OUT.mkdir(parents=True, exist_ok=True)
    base = OUT / 'base.jar'
    shutil.copy2(args.base, base)
    work = OUT / 'build'
    shutil.rmtree(work, ignore_errors=True)
    classes = work / 'classes'
    build.OUT = work
    sources = [ROOT / 'advance-magic/src/main/java' / (p + '.java') for p in OWNED]
    build.compile_sources('spell-levels', sources, [base.as_posix()] + build.pinned_classpath(build.ADVANCE_ARGS), classes)

    with zipfile.ZipFile(base) as jar:
        lines = jar.read('plugin.yml').decode('utf8').splitlines(keepends=True)
    index = next(i for i, line in enumerate(lines) if line.startswith('version: '))
    lines[index] = f'version: {VERSION}' + lines[index][len(lines[index].rstrip('\r\n')):]
    plugin = ''.join(lines).encode('utf8')

    jar_path = work / 'advance-magic.jar'
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
        source_plugin = ROOT / 'advance-magic/src/main/resources/plugin.yml'
        text = source_plugin.read_bytes().decode('utf8').splitlines(keepends=True)
        i = next(k for k, line in enumerate(text) if line.startswith('version: '))
        text[i] = f'version: {VERSION}' + text[i][len(text[i].rstrip('\r\n')):]
        source_plugin.write_bytes(''.join(text).encode('utf8'))
        for target in (ROOT / 'dist/advance-magic.jar', ROOT / 'dist' / ('advance-magic-' + VERSION + '.jar')):
            staged = target.with_suffix('.jar.staged')
            shutil.copy2(jar_path, staged)
            os.replace(staged, target)
    report = {'version': VERSION, 'sha256': digest, 'baseline_sha256': sha(base.read_bytes()), 'promoted': args.promote}
    (OUT / 'release-build.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf8')
    print(json.dumps(report, indent=2))


if __name__ == '__main__':
    main()
