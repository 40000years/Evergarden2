"""Build a realm combat candidate over the frozen delivered JAR without deploying."""
import hashlib
import json
import re
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'tools'))
import build_plugin_jars as build

OUT = ROOT / 'output/realm-hardmode-update'
BASE = OUT / 'baseline-evergarden.jar'
MAGIC = OUT / 'baseline-advance-magic.jar'
PACKAGE = 'com/example/voidscape/boss/'
FAMILIES = tuple(PACKAGE + name for name in ('RealmBossController', 'EndAntiAir', 'NetherAntiAir'))
VERSION = '3.0.0-e2.163-realm-challenge'


def sha(data): return hashlib.sha256(data).hexdigest()


def owned(name):
    return any(name == f + '.class' or name.startswith(f + '$') for f in FAMILIES)


def main():
    if sha((ROOT / 'dist/evergarden.jar').read_bytes()) != sha(BASE.read_bytes()):
        raise RuntimeError('dist changed since baseline; rebase before promotion')
    if sha((ROOT / 'dist/advance-magic.jar').read_bytes()) != sha(MAGIC.read_bytes()):
        raise RuntimeError('advance-magic changed since baseline')
    sources = [ROOT / 'evergarden/src/main/java' / (f + '.java') for f in FAMILIES]
    hashes = {str(p): sha(p.read_bytes()) for p in sources}
    build.OUT = OUT
    build.compile_sources('realm-challenge', sources, [str(BASE), str(MAGIC)] + build.pinned_classpath(build.EVERGARDEN_ARGS), OUT / 'classes')
    if any(sha(p.read_bytes()) != hashes[str(p)] for p in sources):
        raise RuntimeError('source changed during build')
    compiled = {p.relative_to(OUT / 'classes').as_posix(): p.read_bytes() for p in (OUT / 'classes').rglob('*.class')}
    if not compiled or any(not owned(n) for n in compiled): raise RuntimeError('unexpected class')
    with zipfile.ZipFile(BASE) as z:
        names = z.namelist()
        if len(names) != len(set(names)) or z.testzip(): raise RuntimeError('invalid baseline')
        files = {n: z.read(n) for n in names}
    plugin, count = re.subn(r"(?m)^version:.*$", "version: '" + VERSION + "'", files['plugin.yml'].decode('utf8'))
    if count != 1: raise RuntimeError('expected one plugin version')
    candidate = OUT / 'evergarden.jar'
    with zipfile.ZipFile(candidate, 'w', zipfile.ZIP_DEFLATED) as z:
        for n, data in files.items():
            if not owned(n) and n != 'plugin.yml': z.writestr(n, data)
        z.writestr('plugin.yml', plugin.encode('utf8'))
        for n, data in compiled.items(): z.writestr(n, data)
    with zipfile.ZipFile(candidate) as z:
        if z.testzip() or len(z.namelist()) != len(set(z.namelist())): raise RuntimeError('invalid candidate')
        for n, data in files.items():
            if not owned(n) and n != 'plugin.yml' and z.read(n) != data: raise RuntimeError('unrelated entry changed: ' + n)
    build.check_jar(candidate)
    report = {'candidate_sha256': sha(candidate.read_bytes()), 'baseline_sha256': sha(BASE.read_bytes()),
              'source_sha256': hashes, 'version': VERSION, 'compiled_entries': len(compiled)}
    (OUT / 'build-report.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf8')
    print(json.dumps(report, indent=2))


if __name__ == '__main__': main()
