"""Build Evergarden e2.142 (over e2.141-staff-gems): fresh-world generation fixes.

* Large structures claim space first: sanctum dungeons (DungeonLayout.reserve) step aside for boss
  structures, sky whales, landmarks and ancient temples instead of blocking them.
* No bare ring around spawn: new biome scenery starts at half spawn blend, where the old garden trees stop.
* Dense ground cover per biome (grass, flower meadows, petals, ferns, leaf litter, moss, eyeblossom).
* Glacier gets spruce groves below the treeline; exploration cells try four spots instead of one, and
  glacier sites accept 3 more blocks of slope.
* Biomes arrive sooner: a warped ~700-block garden heartland and biome-size 2048 (config default and
  the VoidscapePlugin fallback used when a server config has no biome-size).

Only a fresh world (new world folder and world-layout.yml) picks up the new layout.
Over a delivered Evergarden JAR (--base, default dist/evergarden.jar) this recompiles only the owned world
classes, updates plugin.yml and config.yml, and keeps every other entry (packs included) byte-identical.

    python tools/build_worldgen_release.py [--base JAR] [--promote]
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

OUT = ROOT / 'output/worldgen-20261026'
VERSION = '3.0.0-e2.142-worldgen'
OWNED = tuple('com/example/voidscape/world/' + p for p in (
    'DungeonLayout', 'SkyWhaleLayout', 'SkyLandmarkLayout', 'WorldBossTempleLayout', 'BossStructureLayout',
    'BiomeTerrain', 'VoidGenerator', 'BiomeScenery', 'BiomeDecorations', 'ExplorationLayout')) + ('com/example/voidscape/VoidscapePlugin',)
DATE = (2026, 10, 26, 0, 0, 0)


def sha(data):
    return hashlib.sha256(data).hexdigest()


def replaced(name):
    return any(name == p + '.class' or name.startswith(p + '$') for p in OWNED)


def set_line(text, prefix, value):
    lines = text.splitlines(keepends=True)
    hits = [i for i, line in enumerate(lines) if line.lstrip().startswith(prefix)]
    if len(hits) != 1:
        raise RuntimeError(f'{prefix!r} found {len(hits)} times')
    i = hits[0]
    indent = lines[i][:len(lines[i]) - len(lines[i].lstrip())]
    lines[i] = indent + prefix + value + lines[i][len(lines[i].rstrip('\r\n')):]
    return ''.join(lines)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--base', type=Path, default=ROOT / 'dist/evergarden.jar')
    parser.add_argument('--promote', action='store_true', help='Copy the JAR to dist/ and update source plugin.yml/config.yml')
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
    build.compile_sources('worldgen', sources, classpath, classes)

    with zipfile.ZipFile(base) as jar:
        plugin = set_line(jar.read('plugin.yml').decode('utf8'), 'version: ', f"'{VERSION}'").encode('utf8')
        config = set_line(jar.read('config.yml').decode('utf8'), 'biome-size: ', '2048').encode('utf8')
    replacements = {'plugin.yml': plugin, 'config.yml': config}

    jar_path = work / 'evergarden.jar'
    with zipfile.ZipFile(base) as previous, zipfile.ZipFile(jar_path, 'w', zipfile.ZIP_DEFLATED) as result:
        for entry in previous.infolist():
            if replaced(entry.filename) or entry.filename in replacements:
                continue
            result.writestr(entry, previous.read(entry.filename))
        for path in sorted(classes.rglob('*.class')):
            name = path.relative_to(classes).as_posix()
            if not replaced(name):
                raise RuntimeError('Unexpected class ' + name)
            result.writestr(zipfile.ZipInfo(name, DATE), path.read_bytes(), compress_type=zipfile.ZIP_DEFLATED)
        for name, data in replacements.items():
            result.writestr(zipfile.ZipInfo(name, DATE), data, compress_type=zipfile.ZIP_DEFLATED)
    with zipfile.ZipFile(jar_path) as release, zipfile.ZipFile(base) as previous:
        if release.testzip() is not None or len(release.namelist()) != len(set(release.namelist())):
            raise RuntimeError('Invalid release archive')
        for info in previous.infolist():
            if not replaced(info.filename) and info.filename not in replacements and release.read(info.filename) != previous.read(info.filename):
                raise RuntimeError('Unrelated entry changed: ' + info.filename)
    build.check_jar(jar_path)
    digest = sha(jar_path.read_bytes())
    if args.promote:
        for name, data in (('plugin.yml', plugin), ('config.yml', config)):
            source = ROOT / 'evergarden/src/main/resources' / name
            prefix, value = ('version: ', f"'{VERSION}'") if name == 'plugin.yml' else ('biome-size: ', '2048')
            source.write_bytes(set_line(source.read_bytes().decode('utf8'), prefix, value).encode('utf8'))
        for target in (ROOT / 'dist/evergarden.jar', ROOT / 'dist' / ('evergarden-' + VERSION + '.jar')):
            staged = target.with_suffix('.jar.staged')
            shutil.copy2(jar_path, staged)
            os.replace(staged, target)
    report = {'version': VERSION, 'sha256': digest, 'baseline_sha256': sha(base.read_bytes()), 'promoted': args.promote}
    (OUT / 'release-build.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf8')
    print(json.dumps(report, indent=2))


if __name__ == '__main__':
    main()
