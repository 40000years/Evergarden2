"""Build Advance Magic 1.32.1 and Evergarden e2.147: every pack download on the game port is logged.

The game-port pack host answered requests silently, so a client FAILED_DOWNLOAD could not be told apart from a
request that never reached the server. Each answered request now logs its path, client address and status, then
whether the whole body was sent or the connection broke off; an unfinished request head is logged when it times
out. Pack failure warnings name the URL the client was given.

Over the delivered JARs (--magic-base, default dist/advance-magic.jar; --garden-base, default dist/evergarden.jar)
this recompiles only the owned classes, sets each plugin.yml version and keeps every other entry byte-identical.

    python tools/build_pack_log_release.py [--magic-base JAR] [--garden-base JAR] [--promote]
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

OUT = ROOT / 'output/pack-log-20261026'
DATE = (2026, 10, 26, 0, 0, 0)
HOST = 'com/example/advancemagic/pack/delivery/ServerPortPackHost'
MODULES = {
    'advance-magic': ('1.32.1-pack-log', (HOST, 'com/example/advancemagic/pack/ResourcePackService')),
    'evergarden': ('3.0.0-e2.147-pack-log', (HOST, 'com/example/voidscape/pack/ResourcePackService')),
}


def sha(data):
    return hashlib.sha256(data).hexdigest()


def source(path):
    tree = 'advance-magic' if path.startswith('com/example/advancemagic') else 'evergarden'
    return ROOT / tree / 'src/main/java' / (path + '.java')


def replaced(name, owned):
    return any(name == p + '.class' or name.startswith(p + '$') for p in owned)


def with_version(text, version):
    lines = text.splitlines(keepends=True)
    i = next(k for k, line in enumerate(lines) if line.startswith('version: '))
    quoted = lines[i][len('version: '):].strip().startswith("'")
    value = f"'{version}'" if quoted else version
    lines[i] = 'version: ' + value + lines[i][len(lines[i].rstrip('\r\n')):]
    return ''.join(lines)


def build_one(name, base, extra_classpath):
    version, owned = MODULES[name]
    work = OUT / name
    shutil.rmtree(work, ignore_errors=True)
    work.mkdir(parents=True)
    copy = work / 'base.jar'
    shutil.copy2(base, copy)
    classes = work / 'classes'
    build.OUT = work
    args = build.ADVANCE_ARGS if name == 'advance-magic' else build.EVERGARDEN_ARGS
    build.compile_sources(name, [source(p) for p in owned], [copy.as_posix(), *extra_classpath] + build.pinned_classpath(args), classes)
    with zipfile.ZipFile(copy) as jar:
        plugin = with_version(jar.read('plugin.yml').decode('utf8'), version).encode('utf8')
    jar_path = work / (name + '.jar')
    with zipfile.ZipFile(copy) as previous, zipfile.ZipFile(jar_path, 'w', zipfile.ZIP_DEFLATED) as result:
        for entry in previous.infolist():
            if replaced(entry.filename, owned) or entry.filename == 'plugin.yml':
                continue
            result.writestr(entry, previous.read(entry.filename))
        for path in sorted(classes.rglob('*.class')):
            entry = path.relative_to(classes).as_posix()
            if not replaced(entry, owned):
                raise RuntimeError('Unexpected class ' + entry)
            result.writestr(zipfile.ZipInfo(entry, DATE), path.read_bytes(), compress_type=zipfile.ZIP_DEFLATED)
        result.writestr(zipfile.ZipInfo('plugin.yml', DATE), plugin, compress_type=zipfile.ZIP_DEFLATED)
    with zipfile.ZipFile(jar_path) as release, zipfile.ZipFile(copy) as previous:
        if release.testzip() is not None or len(release.namelist()) != len(set(release.namelist())):
            raise RuntimeError('Invalid release archive')
        for info in previous.infolist():
            if not replaced(info.filename, owned) and info.filename != 'plugin.yml' and release.read(info.filename) != previous.read(info.filename):
                raise RuntimeError('Unrelated entry changed: ' + info.filename)
    build.check_jar(jar_path)
    return jar_path, version, sha(copy.read_bytes())


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--magic-base', type=Path, default=ROOT / 'dist/advance-magic.jar')
    parser.add_argument('--garden-base', type=Path, default=ROOT / 'dist/evergarden.jar')
    parser.add_argument('--promote', action='store_true')
    args = parser.parse_args()
    OUT.mkdir(parents=True, exist_ok=True)
    magic, magic_version, magic_base = build_one('advance-magic', args.magic_base, [])
    garden, garden_version, garden_base = build_one('evergarden', args.garden_base, [magic.as_posix()])
    report = {'advance-magic': {'version': magic_version, 'sha256': sha(magic.read_bytes()), 'baseline_sha256': magic_base},
              'evergarden': {'version': garden_version, 'sha256': sha(garden.read_bytes()), 'baseline_sha256': garden_base},
              'promoted': args.promote}
    if args.promote:
        for name, jar, version, plugin in (('advance-magic', magic, magic_version, 'advance-magic'), ('evergarden', garden, garden_version, 'evergarden')):
            source_plugin = ROOT / plugin / 'src/main/resources/plugin.yml'
            source_plugin.write_bytes(with_version(source_plugin.read_bytes().decode('utf8'), version).encode('utf8'))
            for target in (ROOT / 'dist' / (name + '.jar'), ROOT / 'dist' / f'{name}-{version}.jar'):
                staged = target.with_suffix('.jar.staged')
                shutil.copy2(jar, staged)
                os.replace(staged, target)
    (OUT / 'release-build.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf8')
    print(json.dumps(report, indent=2))


if __name__ == '__main__':
    main()
