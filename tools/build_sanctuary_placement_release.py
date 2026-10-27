"""Build Evergarden e2.144: overworld sanctuaries that can be found.

Fresh overworld cells give each kind its own grid of regions (2-7 cells a side, smaller for rarer biomes) with at
most one site per region, about 3-5 of each kind per 15000 x 15000 blocks. A region searches all its cells (a 7 x 7
grid per cell, sliding inwards from biome edges) for its biome or a close relative (eroded badlands, ice spikes,
mangrove swamp, old-growth birch); rarer kinds claim a contested cell first. Sites keep clear of where
vanilla villages and pillager outposts can start. Cells that already had terrain when the plugin upgrades keep the
old one-roll placement, so no half-built sanctuary appears in an existing world.

Over a delivered Evergarden JAR (--base, default dist/evergarden.jar) this recompiles only the owned classes, sets
the plugin.yml version, adds overworld-sanctuaries.region-cells to config.yml and keeps every other entry
byte-identical.

    python tools/build_sanctuary_placement_release.py [--base JAR] [--promote]
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

OUT = ROOT / 'output/sanctuary-placement-20261026'
VERSION = '3.0.0-e2.144-sanctuary-placement'
OWNED = tuple('com/example/voidscape/world/' + p for p in ('OverworldSanctuaries', 'VanillaSettlements'))
REGION_BLOCK = [
    '  # Fresh cells: each kind has its own grid of regions N x N cells (N = 2-8) and at most one sanctuary per region,',
    '  # clear of vanilla villages and outposts. Smaller N = more of that kind. About 3-5 of each per 15000 x 15000 blocks.',
    '  # Saved per world on first start; cells that already had terrain keep the one-roll placement above.',
    '  region-cells:',
    '    acheron: 5',
    '    phlegethon: 2',
    '    cocytus: 6',
    '    banshee: 6',
    '    malefeasance: 7',
    '    styx: 7',
]
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


def add_region_cells(text):
    if 'region-cells:' in text:
        return text
    nl = chr(13) + chr(10) if chr(13) + chr(10) in text else chr(10)
    anchor = '  chance: 0.65' + nl
    if text.count(anchor) != 1:
        raise RuntimeError('overworld-sanctuaries.chance line not found')
    return text.replace(anchor, anchor + nl.join(REGION_BLOCK) + nl)


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
    build.compile_sources('sanctuary-placement', sources, classpath, classes)

    with zipfile.ZipFile(base) as jar:
        plugin = set_line(jar.read('plugin.yml').decode('utf8'), 'version: ', f"'{VERSION}'").encode('utf8')
        config = add_region_cells(jar.read('config.yml').decode('utf8')).encode('utf8')
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
        source = ROOT / 'evergarden/src/main/resources/plugin.yml'
        source.write_bytes(set_line(source.read_bytes().decode('utf8'), 'version: ', f"'{VERSION}'").encode('utf8'))
        source = ROOT / 'evergarden/src/main/resources/config.yml'
        source.write_bytes(add_region_cells(source.read_bytes().decode('utf8')).encode('utf8'))
        for target in (ROOT / 'dist/evergarden.jar', ROOT / 'dist' / ('evergarden-' + VERSION + '.jar')):
            staged = target.with_suffix('.jar.staged')
            shutil.copy2(jar_path, staged)
            os.replace(staged, target)
    report = {'version': VERSION, 'sha256': digest, 'baseline_sha256': sha(base.read_bytes()), 'promoted': args.promote}
    (OUT / 'release-build.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf8')
    print(json.dumps(report, indent=2))


if __name__ == '__main__':
    main()
