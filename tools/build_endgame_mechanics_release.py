"""Build Evergarden e2.131 (endgame mechanics for Arkhaia and Seraphon) over the delivered e2.130 JAR.

Only the Arkhaia/Seraphon encounter classes, config.yml and plugin.yml change; every other e2.130
entry (models, packs, other bosses) stays byte-identical.

    python tools/build_endgame_mechanics_release.py [--no-copy]
"""
import argparse
import hashlib
import json
import os
import shutil
import subprocess
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'output/boss-mechanics-20261025'
BASE = OUT / 'before/evergarden.jar'
BASE_SHA256 = 'e6d6e9d38c88f98720fc387c2216d840a17018c6edc691de495a9fa7e0d3525e'
VERSION = '3.0.0-e2.131-endgame-mechanics'
FAMILIES = ['ArkhaiaBoss', 'ArkhaiaSovereign', 'ArkhaiaRules', 'ArkhaiaPattern', 'ArkhaiaEffects',
            'SeraphonBoss', 'SeraphonRules', 'SeraphonEffects']
RESOURCES = {'config.yml', 'plugin.yml'}


def sha(data):
    return hashlib.sha256(data).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--no-copy', action='store_true', help='Leave the JAR in output/ instead of replacing dist/')
    args = parser.parse_args()
    if sha(BASE.read_bytes()) != BASE_SHA256:
        raise RuntimeError('The e2.130 baseline backup changed unexpectedly.')
    build = OUT / 'release'
    shutil.rmtree(build, ignore_errors=True)
    classes = build / 'classes'
    classes.mkdir(parents=True)
    families = ['com/example/voidscape/boss/' + name for name in FAMILIES]
    sources = [ROOT / 'evergarden/src/main/java' / (name + '.java') for name in families]
    pinned = (ROOT / 'output/banshee-hunt-build-v1-20261015/evergarden-javac.args').read_text(encoding='utf8').splitlines()[6].strip('"')
    classpath = os.pathsep.join((str(BASE), pinned))
    options = ['--release', '25', '-proc:none', '-encoding', 'UTF-8', '-cp', classpath, '-d', str(classes), *map(str, sources)]
    argfile = build / 'javac.args'
    argfile.write_text('\n'.join('"' + value.replace('\\', '/') + '"' for value in options), encoding='utf8')
    subprocess.run(['java', str(ROOT / 'tools/CompileJava.java'), str(argfile)], check=True)

    def belongs(name):
        return any(name == family + '.class' or name.startswith(family + '$') for family in families)

    resources = {name: (ROOT / 'evergarden/src/main/resources' / name).read_bytes() for name in RESOURCES}
    if ("version: '" + VERSION + "'").encode() not in resources['plugin.yml']:
        raise RuntimeError('plugin.yml is not ' + VERSION)
    jar = build / 'evergarden.jar'
    preserved = 0
    with zipfile.ZipFile(BASE) as previous, zipfile.ZipFile(jar, 'w', zipfile.ZIP_DEFLATED) as result:
        for entry in previous.infolist():
            if belongs(entry.filename) or entry.filename in RESOURCES:
                continue
            result.writestr(entry, previous.read(entry.filename))
            preserved += 1
        for path in sorted(classes.rglob('*.class')):
            name = path.relative_to(classes).as_posix()
            if not belongs(name):
                raise RuntimeError('Unexpected compiled class: ' + name)
            result.write(path, name)
        for name, data in sorted(resources.items()):
            result.writestr(zipfile.ZipInfo(name, date_time=(2026, 10, 25, 0, 0, 0)), data, compress_type=zipfile.ZIP_DEFLATED)
    with zipfile.ZipFile(jar) as release, zipfile.ZipFile(BASE) as previous:
        names = release.namelist()
        if release.testzip() is not None or len(names) != len(set(names)):
            raise RuntimeError('Invalid release archive')
        for entry in previous.infolist():
            if not belongs(entry.filename) and entry.filename not in RESOURCES and release.read(entry.filename) != previous.read(entry.filename):
                raise RuntimeError('Unrelated entry changed: ' + entry.filename)
        changed = sorted(n for n in names if belongs(n) or n in RESOURCES)
    digest = sha(jar.read_bytes())
    if not args.no_copy:
        for target in (ROOT / 'dist/evergarden.jar', ROOT / 'dist' / ('evergarden-' + VERSION + '.jar')):
            staged = target.with_suffix('.jar.staged')
            shutil.copy2(jar, staged)
            os.replace(staged, target)
    report = {
        'version': VERSION, 'sha256': digest, 'size': jar.stat().st_size,
        'baseline': BASE.relative_to(ROOT).as_posix(), 'baseline_sha256': BASE_SHA256,
        'preserved_baseline_entries': preserved, 'changed_entries': changed,
        'sources': {str(s.relative_to(ROOT).as_posix()): sha(s.read_bytes()) for s in sources},
        'copied_to_dist': not args.no_copy,
    }
    (OUT / 'release-build.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf8')
    print(json.dumps({k: v for k, v in report.items() if k not in ('changed_entries', 'sources')}, indent=2))


if __name__ == '__main__':
    main()
