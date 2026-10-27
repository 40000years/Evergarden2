"""Build Advance Magic 1.33.0 and Evergarden e2.149: one pack load, public copies, stable Judge hash.

* Every Java pack a player needs goes out in one batch, so the client reloads once. Just after a restart
  the batch waits up to 30 seconds for ModelEngine's first import, so Judge is part of it.
* When a download in the batch fails, the client discards the others; the coordinator then sends the
  packs again one at a time, a failed pack from its next address. Only a pack with no address left fails.
* resource-pack.mirrors lists public copies named by a fingerprint of the pack's files. The server
  downloads each once, offers it only when the files match, and keeps its own host as the fallback.
  Outbound HTTPS is all it needs, so packs load on hosts that expose only the game port.
* ModelEngine's custom_model_data fallback maps (renumbered on every start, unused unless
  Force-Custom-Model-Data is set) are left out of the Judge pack, so its hash survives restarts.

Over the delivered JARs (--magic-base, default dist/advance-magic.jar; --garden-base, default
dist/evergarden.jar) this recompiles only the owned classes, adds the mirrors block to each shipped
config.yml, sets each plugin.yml version and keeps every other entry byte-identical.

    python tools/build_pack_mirror_release.py [--magic-base JAR] [--garden-base JAR] [--promote]
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

OUT = ROOT / 'output/pack-mirror-20261026'
DATE = (2026, 10, 26, 0, 0, 0)
DELIVERY = ('com/example/advancemagic/pack/delivery/ResourcePackManager', 'com/example/advancemagic/pack/delivery/PackMirror')
MODULES = {
    'advance-magic': ('1.33.0-pack-mirror', DELIVERY + ('com/example/advancemagic/pack/ResourcePackService',)),
    'evergarden': ('3.0.0-e2.149-pack-mirror', DELIVERY + ('com/example/voidscape/pack/ResourcePackService',
                                                          'com/example/voidscape/boss/JudgePackService')),
}
ANCHOR = '  required-timeout-seconds: 600\n'
MIRRORS = """  # Public copies of the packs, named by a fingerprint of their contents. At startup the
  # server downloads each copy once and offers it only if it holds exactly the same files;
  # players then fetch it there first, with this server's own host as the fallback.
  # Outbound HTTPS is enough, so this works on hosts that expose only the game port.
  # [] = serve the packs only from this server.
  mirrors:
  - https://raw.githubusercontent.com/40000years/Evergarden2/packs/{pack}/{fingerprint}.zip
"""


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


def with_mirrors(text):
    """The shipped config keeps its own contents; only the mirrors block is inserted."""
    if '\n  mirrors:\n' in text:
        raise RuntimeError('config.yml already lists mirrors')
    newline = '\r\n' if ANCHOR.replace('\n', '\r\n') in text else '\n'
    anchor = ANCHOR.replace('\n', newline)
    if text.count(anchor) != 1:
        raise RuntimeError('resource-pack.required-timeout-seconds anchor not found once')
    return text.replace(anchor, anchor + MIRRORS.replace('\n', newline))


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
        config = with_mirrors(jar.read('config.yml').decode('utf8')).encode('utf8')
    replacements = {'plugin.yml': plugin, 'config.yml': config}
    jar_path = work / (name + '.jar')
    with zipfile.ZipFile(copy) as previous, zipfile.ZipFile(jar_path, 'w', zipfile.ZIP_DEFLATED) as result:
        for entry in previous.infolist():
            if replaced(entry.filename, owned) or entry.filename in replacements:
                continue
            result.writestr(entry, previous.read(entry.filename))
        for path in sorted(classes.rglob('*.class')):
            entry = path.relative_to(classes).as_posix()
            if not replaced(entry, owned):
                raise RuntimeError('Unexpected class ' + entry)
            result.writestr(zipfile.ZipInfo(entry, DATE), path.read_bytes(), compress_type=zipfile.ZIP_DEFLATED)
        for entry, data in replacements.items():
            result.writestr(zipfile.ZipInfo(entry, DATE), data, compress_type=zipfile.ZIP_DEFLATED)
    with zipfile.ZipFile(jar_path) as release, zipfile.ZipFile(copy) as previous:
        if release.testzip() is not None or len(release.namelist()) != len(set(release.namelist())):
            raise RuntimeError('Invalid release archive')
        for info in previous.infolist():
            if not replaced(info.filename, owned) and info.filename not in replacements and release.read(info.filename) != previous.read(info.filename):
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
        for name, jar, version in (('advance-magic', magic, magic_version), ('evergarden', garden, garden_version)):
            source_plugin = ROOT / name / 'src/main/resources/plugin.yml'
            source_plugin.write_bytes(with_version(source_plugin.read_bytes().decode('utf8'), version).encode('utf8'))
            for target in (ROOT / 'dist' / (name + '.jar'), ROOT / 'dist' / f'{name}-{version}.jar'):
                staged = target.with_suffix('.jar.staged')
                shutil.copy2(jar, staged)
                os.replace(staged, target)
    (OUT / 'release-build.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf8')
    print(json.dumps(report, indent=2))


if __name__ == '__main__':
    main()
