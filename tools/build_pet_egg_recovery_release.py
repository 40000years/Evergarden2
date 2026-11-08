"""Build the egg-delivery fix on the current dist baseline without changing other features."""
import hashlib
import json
import re
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'tools'))
import build_plugin_jars as build

OUT = ROOT / 'output/pet-egg-recovery-release'
VERSION = '3.0.0-e2.171-egg-recovery'
FAMILIES = tuple('com/example/voidscape/pet/' + name for name in
                 ('PetStore', 'PetStable', 'PetBreeding', 'PetSystem', 'PetMenu', 'PetEggInventorySave'))


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def owns(name):
    return any(name == family + '.class' or name.startswith(family + '$') for family in FAMILIES)


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    dist = ROOT / 'dist/evergarden.jar'
    baseline = OUT / 'baseline-evergarden.jar'
    if not baseline.exists():
        baseline.write_bytes(dist.read_bytes())
    if sha(baseline) != sha(dist):
        raise RuntimeError('dist changed since the baseline snapshot')
    sources = [ROOT / 'evergarden/src/main/java' / (family + '.java') for family in FAMILIES]
    hashes = {str(path): sha(path) for path in sources}
    build.OUT = OUT
    classes = OUT / 'classes'
    build.compile_sources('egg-recovery', sources, [str(baseline), str(ROOT / 'dist/advance-magic.jar')]
                          + build.pinned_classpath(build.EVERGARDEN_ARGS), classes)
    if any(sha(Path(path)) != expected for path, expected in hashes.items()):
        raise RuntimeError('Source changed during compilation')
    compiled = {path.relative_to(classes).as_posix(): path.read_bytes() for path in classes.rglob('*.class')}
    if not compiled or any(not owns(name) for name in compiled):
        raise RuntimeError('Unexpected compiled class')
    if any(family + '.class' not in compiled for family in FAMILIES):
        raise RuntimeError('Missing compiled family')
    with zipfile.ZipFile(baseline) as archive:
        if archive.testzip() or len(archive.namelist()) != len(set(archive.namelist())):
            raise RuntimeError('Invalid baseline JAR')
        files = {name: archive.read(name) for name in archive.namelist()}
    plugin, count = re.subn(r'(?m)^version:.*$', "version: '" + VERSION + "'", files['plugin.yml'].decode('utf8'))
    if count != 1:
        raise RuntimeError('Invalid plugin version')
    candidate = OUT / 'evergarden.jar'
    with zipfile.ZipFile(candidate, 'w', zipfile.ZIP_DEFLATED) as archive:
        for name, content in files.items():
            if not owns(name) and name != 'plugin.yml':
                archive.writestr(name, content)
        archive.writestr('plugin.yml', plugin.encode('utf8'))
        for name, content in compiled.items():
            archive.writestr(name, content)
    with zipfile.ZipFile(candidate) as archive:
        if archive.testzip() or len(archive.namelist()) != len(set(archive.namelist())):
            raise RuntimeError('Invalid candidate JAR')
        for name, content in files.items():
            if not owns(name) and name != 'plugin.yml' and archive.read(name) != content:
                raise RuntimeError('Unrelated JAR entry changed: ' + name)
    build.check_jar(candidate)
    report = {'version': VERSION, 'sources': hashes, 'baseline_sha256': sha(baseline),
              'candidate_sha256': sha(candidate), 'magic_sha256': sha(ROOT / 'dist/advance-magic.jar')}
    (OUT / 'build-report.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf8')
    print(json.dumps(report, indent=2))


if __name__ == '__main__':
    main()
