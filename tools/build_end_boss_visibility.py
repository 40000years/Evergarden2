"""Build a narrowly scoped End-boss visibility candidate from the delivered JAR.

This creates a candidate under output/; it never deploys to a server or dist/.
"""
import hashlib
import json
import re
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'tools'))
import build_plugin_jars as build

OUT = ROOT / 'output/end-boss-visibility-release'
BASE = ROOT / 'dist/evergarden.jar'
PACK_BEFORE = ROOT / 'output/bedrock-readability-probe/before.mcpack'
PACK_AFTER = ROOT / 'output/bedrock-readability-probe/candidate.mcpack'
SOURCE = ROOT / 'evergarden/src/main/java/com/example/voidscape/boss/ModelEngineArkhaia.java'
PREFIX = 'com/example/voidscape/boss/ModelEngineArkhaia'
VERSION = '3.0.0-e2.161-end-boss-visibility'


def digest(data):
    return hashlib.sha256(data).hexdigest()


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    baseline = BASE.read_bytes()
    (OUT / 'baseline-evergarden.jar').write_bytes(baseline)
    build.OUT = OUT
    build.compile_sources('end-boss-visibility', [SOURCE],
                          [str(BASE), str(ROOT / 'dist/advance-magic.jar')] + build.pinned_classpath(build.EVERGARDEN_ARGS),
                          OUT / 'classes')
    compiled = {p.relative_to(OUT / 'classes').as_posix(): p.read_bytes() for p in (OUT / 'classes').rglob('*.class')}
    if not compiled or any(not (n == PREFIX + '.class' or n.startswith(PREFIX + '$')) for n in compiled):
        raise RuntimeError('Unexpected compiled classes')
    with zipfile.ZipFile(BASE) as archive:
        names = archive.namelist()
        if len(names) != len(set(names)) or archive.testzip():
            raise RuntimeError('Invalid baseline archive')
        files = {name: archive.read(name) for name in names}
    pack_key = 'resource-packs/evergarden-bedrock.mcpack'
    if files[pack_key] != PACK_BEFORE.read_bytes():
        raise RuntimeError('Bedrock candidate was not built from this JAR')
    plugin, count = re.subn(r"(?m)^version:.*$", "version: '" + VERSION + "'", files['plugin.yml'].decode('utf8'))
    if count != 1:
        raise RuntimeError('Expected one plugin version')
    hashes = json.loads(files['resource-packs/pack-hashes.json'])
    pack = PACK_AFTER.read_bytes()
    hashes['evergarden-bedrock.mcpack'] = hashlib.sha1(pack).hexdigest()
    design = json.loads(files['judge-design.json'])
    with zipfile.ZipFile(PACK_AFTER) as archive:
        version = json.loads(archive.read('manifest.json'))['header']['version']
    design['bedrock-version'] = '.'.join(map(str, version))
    replacements = {'plugin.yml': plugin.encode('utf8'), pack_key: pack,
                    'resource-packs/pack-hashes.json': (json.dumps(hashes, indent=2) + '\n').encode(),
                    'judge-design.json': (json.dumps(design, ensure_ascii=False, indent=2) + '\n').encode()}
    def owned(name):
        return name == PREFIX + '.class' or name.startswith(PREFIX + '$')
    old_classes = {n for n in files if owned(n)}
    if old_classes != set(compiled):
        raise RuntimeError('Compiled class family differs from baseline: ' + str(old_classes ^ set(compiled)))
    candidate = OUT / 'evergarden-end-boss-visibility.jar'
    with zipfile.ZipFile(candidate, 'w', zipfile.ZIP_DEFLATED) as output:
        for name, content in files.items():
            output.writestr(name, compiled[name] if owned(name) else replacements.get(name, content))
    with zipfile.ZipFile(candidate) as result:
        if result.testzip() or set(result.namelist()) != set(files):
            raise RuntimeError('Invalid candidate archive')
        for name, content in files.items():
            if not owned(name) and name not in replacements and result.read(name) != content:
                raise RuntimeError('Unrelated entry changed: ' + name)
    build.check_jar(candidate)
    print(json.dumps({'candidate': str(candidate), 'version': VERSION, 'baseline_sha256': digest(baseline),
                      'candidate_sha256': digest(candidate.read_bytes()), 'bedrock_sha1': hashes['evergarden-bedrock.mcpack'],
                      'preserved_entries': len(files) - len(old_classes) - len(replacements)}, indent=2))


if __name__ == '__main__':
    main()
