"""Build the sanctum balance patch over the current delivered JAR; preserve every unrelated entry.

Run without --promote, test on isolated Paper, then run with --promote.
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

OUT = ROOT / 'output/dungeon-balance-20261026'
VERSION = '3.0.0-e2.151-dungeon-balance'
OWNED = tuple('com/example/voidscape/dungeon/' + p for p in ('DungeonManager', 'DungeonBalance'))
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
    parser.add_argument('--promote', action='store_true', help='Promote the Paper-tested JAR to dist/')
    args = parser.parse_args()
    OUT.mkdir(parents=True, exist_ok=True)
    base = OUT / 'before/evergarden.jar'
    if not base.exists():
        base.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(args.base, base)
    work = OUT / 'build'
    work.mkdir(parents=True, exist_ok=True)
    classes = work / 'classes'
    build.OUT = work
    sources = [ROOT / 'evergarden/src/main/java' / (p + '.java') for p in OWNED]
    classpath = [base.as_posix(), (ROOT / 'dist/advance-magic.jar').as_posix()] + build.pinned_classpath(build.EVERGARDEN_ARGS)
    build.compile_sources('dungeon-balance', sources, classpath, classes)

    with zipfile.ZipFile(base) as jar:
        plugin = set_line(jar.read('plugin.yml').decode('utf8'), 'version: ', f"'{VERSION}'").encode('utf8')
    replacements = {'plugin.yml': plugin}

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
        if sha((ROOT / 'dist/evergarden.jar').read_bytes()) != sha(base.read_bytes()):
            raise RuntimeError('Delivered baseline changed')
        receipt = json.loads((OUT / 'paper/dungeon-balance-validation.json').read_text(encoding='utf8'))
        if receipt['garden_sha256'] != digest or not receipt['report'].startswith('PASS'):
            raise RuntimeError('Candidate lacks matching Paper validation')
        if receipt['magic_sha256'] != sha((ROOT / 'dist/advance-magic.jar').read_bytes()):
            raise RuntimeError('Advance Magic changed after Paper validation')
        if receipt['probe_sha256'] != sha((ROOT / 'evergarden/tests/DungeonBalanceChecks.java').read_bytes()):
            raise RuntimeError('Probe changed after Paper validation')
        source = ROOT / 'evergarden/src/main/resources/plugin.yml'
        source.write_bytes(set_line(source.read_bytes().decode('utf8'), 'version: ', f"'{VERSION}'").encode('utf8'))
        for target in (ROOT / 'dist/evergarden.jar', ROOT / 'dist' / ('evergarden-' + VERSION + '.jar')):
            staged = target.with_suffix('.jar.staged')
            shutil.copy2(jar_path, staged)
            os.replace(staged, target)
    report = {'version': VERSION, 'sha256': digest, 'baseline_sha256': sha(base.read_bytes()),
              'sources': {p.relative_to(ROOT).as_posix(): sha(p.read_bytes()) for p in sources},
              'promoted': args.promote}
    (OUT / 'release-build.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf8')
    print(json.dumps(report, indent=2))


if __name__ == '__main__':
    main()
