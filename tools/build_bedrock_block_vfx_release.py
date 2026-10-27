"""Build the Legendary Bedrock block VFX fix over the delivered 1.29.1 JAR.

Only the three visual class families and plugin.yml are replaced. All client
packs and unrelated code stay byte-identical to the backed-up release. This
builds a candidate under output/; it does not deploy or replace dist/.
"""
import argparse
import hashlib
import json
import shutil
import zipfile
from pathlib import Path

import build_plugin_jars as build

ROOT = Path(__file__).resolve().parents[1]
BASE = ROOT / 'output/legendary-bedrock-blocks-20261025/before/advance-magic.jar'
BASE_SHA256 = '83e5b595517bf170997fa7a06ff9e885afb230a2e6bfc450f66d71ff8312ca57'
VERSION = '1.29.2-bedrock-block-vfx'
OWNED = tuple('com/example/advancemagic/spell/' + name for name in
              ('MythicLineVisuals', 'MythicVisuals', 'ElementalVfx'))


def sha(data):
    return hashlib.sha256(data).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path,
                        default=ROOT / 'output/legendary-bedrock-blocks-20261025/rebuild')
    args = parser.parse_args()
    out = args.output.resolve()
    if not out.is_relative_to((ROOT / 'output').resolve()) or out.exists():
        parser.error('Choose a fresh build directory inside workspace output/')
    if sha(BASE.read_bytes()) != BASE_SHA256:
        raise RuntimeError('The backed-up 1.29.1 baseline changed')
    out.mkdir(parents=True)
    sources, hashes = [], {}
    for prefix in OWNED:
        original = ROOT / 'advance-magic/src/main/java' / (prefix + '.java')
        snapshot = out / 'source' / (prefix + '.java')
        snapshot.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(original, snapshot)
        sources.append(snapshot)
        hashes[original.relative_to(ROOT).as_posix()] = sha(snapshot.read_bytes())
    metadata = (ROOT / 'advance-magic/src/main/resources/plugin.yml').read_bytes()
    if ('version: ' + VERSION).encode() not in metadata:
        raise RuntimeError('Source plugin version differs from this release')
    hashes['advance-magic/src/main/resources/plugin.yml'] = sha(metadata)
    build.OUT = out
    classes = out / 'classes'
    build.compile_sources('bedrock-block-vfx', sources,
                          [BASE.as_posix()] + build.pinned_classpath(build.ADVANCE_ARGS), classes)
    replaced = lambda name: any(name == prefix + '.class' or name.startswith(prefix + '$') for prefix in OWNED)
    jar = out / 'advance-magic.jar'
    with zipfile.ZipFile(BASE) as previous, zipfile.ZipFile(jar, 'w') as release:
        for entry in previous.infolist():
            if entry.filename != 'plugin.yml' and not replaced(entry.filename):
                release.writestr(entry, previous.read(entry.filename))
        for path in sorted(classes.rglob('*.class')):
            release.write(path, path.relative_to(classes).as_posix(), compress_type=zipfile.ZIP_DEFLATED)
        release.writestr('plugin.yml', metadata, compress_type=zipfile.ZIP_DEFLATED)
    build.check_jar(jar)
    with zipfile.ZipFile(BASE) as previous, zipfile.ZipFile(jar) as release:
        if release.testzip() or len(release.namelist()) != len(set(release.namelist())):
            raise RuntimeError('Invalid release archive')
        if set(previous.namelist()) - set(release.namelist()):
            raise RuntimeError('Unexpected removed baseline entries')
        changed = sorted(name for name in release.namelist()
                         if name not in previous.namelist() or release.read(name) != previous.read(name))
        if any(name != 'plugin.yml' and not replaced(name) for name in changed):
            raise RuntimeError('Unexpected replacement outside the visual fix')
        packs = {name: sha(release.read(name)) for name in release.namelist() if name.startswith('resource-packs/')}
        assert all(digest == sha(previous.read(name)) for name, digest in packs.items())
    report = {'version': VERSION, 'sha256': sha(jar.read_bytes()), 'baseline_sha256': BASE_SHA256,
              'changed_entries': changed, 'source_sha256': hashes,
              'resource_pack_sha256': packs, 'copied_to_dist': False}
    (out / 'build-manifest.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf8')
    print(json.dumps(report, indent=2))


if __name__ == '__main__':
    main()
