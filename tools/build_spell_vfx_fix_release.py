"""Build Advance Magic 1.37.1: no block-break particles on Bedrock.

Players still saw a flood of dirt and froze on Bedrock while casting earth spells in 1.37.0. Geyser
plays every BLOCK particle (and BLOCK_CRUMBLE / DUST_PILLAR / DUST_PLUME) as a whole block-break burst,
so one 90-count Legendary earth burst became 90 crack-block events. Over the 1.37.0 JAR this:

* recompiles NativeElementalVfx, ElementalVfx and ElementalSpellService: both clients now draw a few
  falling-dust chips of the same block instead, and the Legendary scenes cap emitter-type particles
  (cloud, explosion, gust, ...) at two per call for Bedrock viewers.

Packs, resources and every other class stay byte-identical (the embedded pack mirror stays valid).

    python tools/build_spell_vfx_fix_release.py [--promote]
"""
import argparse
import json
import os
import shutil
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'tools'))
import build_plugin_jars as build  # noqa: E402
import build_unused_cleanup_release as previous  # noqa: E402
from build_spell_vfx_release import DATE, sha256  # noqa: E402

OUT = ROOT / 'output/spell-vfx-fix-20261029'
BASE = ROOT / 'dist/advance-magic-1.37.0-spell-vfx.jar'
BASE_SHA256 = 'c402e6837df9824253584bc7757f6f4cd0135d827ac54bc64b73155f641b0f20'
GARDEN = ROOT / 'dist/evergarden.jar'
VERSION = '1.37.1-bedrock-dust'
PKG = previous.PKG
OWNED = tuple(PKG + n for n in ('elemental/ElementalSpellService', 'spell/ElementalVfx', 'spell/NativeElementalVfx'))
KNOWN_DRIFT = previous.KNOWN_DRIFT


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--promote', action='store_true')
    args = parser.parse_args()
    release_dir = OUT / 'release'
    release_dir.mkdir(parents=True, exist_ok=True)
    jar_path = release_dir / 'advance-magic.jar'
    if args.promote:
        return promote(jar_path)
    base = BASE.read_bytes()
    if sha256(base) != BASE_SHA256:
        raise RuntimeError('Base JAR changed: ' + str(BASE))
    if sha256((ROOT / 'dist/advance-magic.jar').read_bytes()) != BASE_SHA256:
        raise RuntimeError('dist/advance-magic.jar is no longer 1.37.0; rebase this release first')
    base_files, base_infos = previous.read_zip(base)

    classes_dir = OUT / 'classes'
    if classes_dir.exists():
        shutil.rmtree(classes_dir)
    build.OUT = OUT
    sources = sorted((ROOT / 'advance-magic/src/main/java').rglob('*.java'))
    build.compile_sources('advance-magic', sources, build.pinned_classpath(build.ADVANCE_ARGS), classes_dir)
    compiled = {p.relative_to(classes_dir).as_posix(): p.read_bytes() for p in classes_dir.rglob('*.class')}
    base_classes = {n: d for n, d in base_files.items() if n.endswith('.class')}
    for name, data in compiled.items():
        if previous.family(name, OWNED):
            continue
        if name not in base_classes:
            raise RuntimeError('Unowned class appeared in source: ' + name)
        if data != base_classes[name] and name not in KNOWN_DRIFT:
            raise RuntimeError('Unowned class differs from the shipped JAR: ' + name)
    missing = sorted(n for n in base_classes if not previous.family(n, OWNED) and n not in compiled)
    if missing:
        raise RuntimeError('Shipped classes without a source: ' + ', '.join(missing))
    owned = {n: d for n, d in compiled.items() if previous.family(n, OWNED)}
    for name in ('config.yml', 'legendary-blocks.json'):
        if (ROOT / 'advance-magic/src/main/resources' / name).read_bytes() != base_files[name]:
            raise RuntimeError(name + ' changed since 1.37.0; this release does not ship resource changes')

    plugin = base_files['plugin.yml'].decode('utf8').splitlines(keepends=True)
    index = next(i for i, line in enumerate(plugin) if line.startswith('version: '))
    plugin[index] = f"version: '{VERSION}'" + plugin[index][len(plugin[index].rstrip('\r\n')):]
    replacements = {'plugin.yml': ''.join(plugin).encode('utf8')}

    with zipfile.ZipFile(jar_path, 'w', zipfile.ZIP_DEFLATED) as out:
        for info in base_infos:
            name = info.filename
            if previous.family(name, OWNED):
                continue
            if name in replacements:
                out.writestr(zipfile.ZipInfo(name, DATE), replacements[name])
            else:
                out.writestr(info, base_files[name])
        for name in sorted(owned):
            out.writestr(zipfile.ZipInfo(name, DATE), owned[name])
    build.check_jar(jar_path)
    release, _ = previous.read_zip(jar_path.read_bytes())
    for name, data in base_files.items():
        if previous.family(name, OWNED) or name in replacements:
            continue
        if release.get(name) != data:
            raise RuntimeError('Unrelated entry changed: ' + name)
    release_classes = {n: d for n, d in release.items() if n.endswith('.class')}
    before, after = previous.index_classes(base_classes), previous.index_classes(release_classes)
    problems = previous.linkage(before, after, release_classes)
    with zipfile.ZipFile(GARDEN) as garden:
        garden_classes = {'evergarden:' + n: garden.read(n) for n in garden.namelist() if n.endswith('.class')}
    problems += previous.linkage(before, after, garden_classes)
    if problems:
        raise RuntimeError('Linkage problems:\n' + '\n'.join(problems[:40]))
    report = {'version': VERSION, 'base_sha256': BASE_SHA256, 'sha256': sha256(jar_path.read_bytes()),
              'changed_classes': sorted(n for n in owned if base_classes.get(n) != owned[n]),
              'sources': {p.relative_to(ROOT).as_posix(): sha256(p.read_bytes()) for p in sources}, 'promoted': False}
    (OUT / 'release-build.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf8')
    print(json.dumps({k: report[k] for k in ('version', 'sha256', 'changed_classes')}, indent=2))


def promote(jar_path):
    report = json.loads((OUT / 'release-build.json').read_text(encoding='utf8'))
    if sha256(jar_path.read_bytes()) != report['sha256']:
        raise RuntimeError('Release candidate changed since it was built')
    if sha256((ROOT / 'dist/advance-magic.jar').read_bytes()) != BASE_SHA256:
        raise RuntimeError('Concurrent release changed dist/advance-magic.jar')
    for path, digest in report['sources'].items():
        if sha256((ROOT / path).read_bytes()) != digest:
            raise RuntimeError('Source changed since the build: ' + path)
    gates = json.loads((OUT / 'gates.json').read_text(encoding='utf8'))
    if gates.get('candidate_sha256') != report['sha256'] or not gates.get('passed'):
        raise RuntimeError('Gates were not recorded as passing for this candidate')
    source = ROOT / 'advance-magic/src/main/resources/plugin.yml'
    text = source.read_bytes().decode('utf8').splitlines(keepends=True)
    k = next(j for j, line in enumerate(text) if line.startswith('version: '))
    text[k] = f"version: '{VERSION}'" + text[k][len(text[k].rstrip('\r\n')):]
    source.write_bytes(''.join(text).encode('utf8'))
    for target in (ROOT / 'dist/advance-magic.jar', ROOT / 'dist' / f'advance-magic-{VERSION}.jar'):
        staged = target.with_suffix('.jar.staged')
        shutil.copy2(jar_path, staged)
        os.replace(staged, target)
    report['promoted'] = True
    (OUT / 'release-build.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf8')
    print('Promoted', VERSION, report['sha256'])


if __name__ == '__main__':
    main()
