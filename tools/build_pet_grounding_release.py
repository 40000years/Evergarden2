"""Build the pet grounding/click patch over the current delivered Evergarden JAR.

Only the owned class families and plugin.yml's version are replaced. All other
entries, including the shipped packs and pack-mirror.properties, stay byte-identical.
The full-source pet builders predate embedded mirrors and must not be used here.

Run without --promote, validate the candidate on isolated Paper, then --promote.
No packs are rebuilt or published and nothing is deployed to a live server.
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

OUT = ROOT / 'output/pet-grounding-20261026-v3'
VERSION = '3.0.0-e2.154-pet-grounding'
PACKAGE = 'com/example/voidscape/'
OWNED = ('pet/PetBody', 'pet/WildPetSpawns', 'pet/PetCompanions', 'pet/PetBodies',
         'pet/PetTaming', 'pet/PetAim', 'boss/ArkhaiaBedrockSupport')
PROBE = ROOT / 'evergarden/tests/PetGroundingChecks.java'
DATE = (2026, 10, 26, 0, 0, 0)


def sha(data):
    return hashlib.sha256(data).hexdigest()


def with_version(text):
    lines = text.splitlines(keepends=True)
    hits = [i for i, line in enumerate(lines) if line.startswith('version: ')]
    if len(hits) != 1:
        raise RuntimeError('plugin.yml must contain exactly one version')
    i = hits[0]
    newline = lines[i][len(lines[i].rstrip('\r\n')):]
    lines[i] = f"version: '{VERSION}'" + newline
    return ''.join(lines)


def source(name):
    return ROOT / 'evergarden/src/main/java' / (PACKAGE + name + '.java')


def snapshot(out, garden, magic):
    """Pin both delivered JARs once; never refresh a previously captured baseline."""
    before = out / 'before'
    before.mkdir(parents=True, exist_ok=True)
    metadata = before / 'baseline.json'
    if not metadata.exists():
        # Refuse to silently adopt leftovers from an interrupted first snapshot.
        if any((before / name).exists() for name in ('evergarden.jar', 'advance-magic.jar')):
            raise RuntimeError('Incomplete baseline snapshot; use a fresh --output directory')
        for original, name in ((garden, 'evergarden.jar'), (magic, 'advance-magic.jar')):
            shutil.copy2(original, before / name)
        hashes = {name: sha((before / name).read_bytes())
                  for name in ('evergarden.jar', 'advance-magic.jar')}
        metadata.write_text(json.dumps(hashes, indent=2) + '\n', encoding='utf8')
    hashes = json.loads(metadata.read_text(encoding='utf8'))
    for name, digest in hashes.items():
        if sha((before / name).read_bytes()) != digest:
            raise RuntimeError('Frozen baseline changed: ' + name)
    if sha(garden.read_bytes()) != hashes['evergarden.jar']:
        raise RuntimeError('Requested Evergarden baseline differs from the frozen snapshot')
    if sha(magic.read_bytes()) != hashes['advance-magic.jar']:
        raise RuntimeError('Advance Magic changed since the baseline snapshot')
    return before, hashes


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--base', type=Path, default=ROOT / 'dist/evergarden.jar')
    parser.add_argument('--magic-base', type=Path, default=ROOT / 'dist/advance-magic.jar')
    parser.add_argument('--output', type=Path, default=OUT)
    parser.add_argument('--extra-owned', action='append', default=[], metavar='PACKAGE/CLASS',
                        help='Additional class below com/example/voidscape to compile and replace')
    parser.add_argument('--probe', type=Path, default=PROBE,
                        help='Paper probe source whose hash must match the validation receipt')
    parser.add_argument('--validation-server', type=Path,
                        help='Prepared isolated Paper directory containing the matching receipt')
    parser.add_argument('--promote', action='store_true', help='Promote only the matching Paper-tested candidate to dist/')
    parser.add_argument('--versioned-only', action='store_true', help='Write only the numbered release JAR when promoting')
    args = parser.parse_args()
    out = args.output.resolve()
    if not out.is_relative_to((ROOT / 'output').resolve()):
        raise ValueError('Build directory must stay under workspace output/')
    out.mkdir(parents=True, exist_ok=True)
    before, baseline = snapshot(out, args.base.resolve(), args.magic_base.resolve())
    base = before / 'evergarden.jar'
    with zipfile.ZipFile(base) as archive:
        if 'pack-mirror.properties' not in archive.namelist():
            raise RuntimeError('Baseline predates embedded public pack copies')

    owned = list(OWNED)
    owned.extend(args.extra_owned)
    owned = tuple(dict.fromkeys(owned))
    for name in owned:
        if not all(part.isidentifier() for part in name.split('/')) or not source(name).is_file():
            raise ValueError('Invalid or missing owned source: ' + name)
    families = tuple(PACKAGE + name for name in owned)

    def replaced(name):
        return any(name == family + '.class' or name.startswith(family + '$') for family in families)

    work = out / 'build'
    work.mkdir(parents=True, exist_ok=True)
    classes = work / 'classes'
    build.OUT = work
    sources = [source(name) for name in owned]
    source_hashes = {path.relative_to(ROOT).as_posix(): sha(path.read_bytes()) for path in sources}
    classpath = [base.as_posix(), (before / 'advance-magic.jar').as_posix()]
    classpath += build.pinned_classpath(build.EVERGARDEN_ARGS)
    build.compile_sources('pet-grounding', sources, classpath, classes)
    if any(sha(path.read_bytes()) != source_hashes[path.relative_to(ROOT).as_posix()] for path in sources):
        raise RuntimeError('Pet sources changed during compilation')

    with zipfile.ZipFile(base) as previous:
        replacements = {'plugin.yml': with_version(previous.read('plugin.yml').decode('utf8')).encode('utf8')}
    candidate = work / 'evergarden.jar'
    with zipfile.ZipFile(base) as previous, zipfile.ZipFile(candidate, 'w', zipfile.ZIP_DEFLATED) as result:
        for info in previous.infolist():
            if not replaced(info.filename) and info.filename not in replacements:
                result.writestr(info, previous.read(info.filename))
        for path in sorted(classes.rglob('*.class')):
            name = path.relative_to(classes).as_posix()
            if not replaced(name):
                raise RuntimeError('Unexpected compiled class: ' + name)
            result.writestr(zipfile.ZipInfo(name, DATE), path.read_bytes(), compress_type=zipfile.ZIP_DEFLATED)
        for name, data in replacements.items():
            result.writestr(zipfile.ZipInfo(name, DATE), data, compress_type=zipfile.ZIP_DEFLATED)

    with zipfile.ZipFile(candidate) as release, zipfile.ZipFile(base) as previous:
        if release.testzip() is not None or len(release.namelist()) != len(set(release.namelist())):
            raise RuntimeError('Invalid release archive')
        for info in previous.infolist():
            if not replaced(info.filename) and info.filename not in replacements:
                if release.read(info.filename) != previous.read(info.filename):
                    raise RuntimeError('Unrelated entry changed: ' + info.filename)
    build.check_jar(candidate)
    digest = sha(candidate.read_bytes())
    if args.promote:
        delivered = ROOT / 'dist/evergarden.jar'
        if delivered.exists() and sha(delivered.read_bytes()) != baseline['evergarden.jar']:
            raise RuntimeError('Delivered Evergarden baseline changed')
        validation = (args.validation_server or out / 'paper').resolve()
        if not validation.is_relative_to((ROOT / 'output').resolve()):
            raise ValueError('Validation server must stay under workspace output/')
        receipt = json.loads((validation / 'pet-grounding-validation.json').read_text(encoding='utf8'))
        if receipt['garden_sha256'] != digest or not receipt['report'].startswith('PASS'):
            raise RuntimeError('Candidate lacks matching Paper validation')
        if receipt['magic_sha256'] != baseline['advance-magic.jar']:
            raise RuntimeError('Advance Magic differs from the Paper validation')
        if receipt['probe_sha256'] != sha(args.probe.read_bytes()):
            raise RuntimeError('Paper probe changed after validation')
        # Source resources are updated only after every receipt and baseline check passes.
        plugin = ROOT / 'evergarden/src/main/resources/plugin.yml'
        plugin.write_bytes(with_version(plugin.read_bytes().decode('utf8')).encode('utf8'))
        targets = [ROOT / 'dist' / f'evergarden-{VERSION}.jar']
        if not args.versioned_only:
            targets.append(ROOT / 'dist/evergarden.jar')
        for target in targets:
            staged = target.with_suffix('.jar.staged')
            shutil.copy2(candidate, staged)
            os.replace(staged, target)
    report = {'version': VERSION, 'sha256': digest, 'baseline_sha256': baseline['evergarden.jar'],
              'magic_sha256': baseline['advance-magic.jar'], 'owned': owned,
              'sources': source_hashes, 'promoted': args.promote}
    (out / 'release-build.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf8')
    print(json.dumps(report, indent=2))


if __name__ == '__main__':
    main()
