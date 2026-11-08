"""Build the Boss-temple reward patch over the exact delivered baseline.

Preserves all unrelated classes, resource packs and embedded mirror manifests.
Build, validate the candidate on isolated Paper, then --promote.
"""
import argparse
import hashlib
import json
import os
import re
import shutil
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'tools'))
import build_plugin_jars as build  # noqa: E402

OUT = ROOT / 'output/temple-rewards-20261029'
VERSION = '3.0.0-e2.158-temple-rewards'
PACKAGE = 'com/example/voidscape/'
OWNED = ('boss/WorldBossManager',)
PROBE = ROOT / 'evergarden/tests/TempleRewardChecks.java'
RECEIPT = 'temple-reward-validation.json'
DATE = (2026, 10, 29, 0, 0, 0)


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
    """Pin the current delivered JARs once, refusing subsequent baseline drift."""
    before = out / 'before'
    before.mkdir(parents=True, exist_ok=True)
    metadata = before / 'baseline.json'
    names = ('evergarden.jar', 'advance-magic.jar')
    if not metadata.exists():
        if any((before / name).exists() for name in names):
            raise RuntimeError('Incomplete baseline snapshot; use a fresh --output directory')
        for original, name in ((garden, names[0]), (magic, names[1])):
            shutil.copy2(original, before / name)
        hashes = {name: sha((before / name).read_bytes()) for name in names}
        metadata.write_text(json.dumps(hashes, indent=2) + '\n', encoding='utf8')
    hashes = json.loads(metadata.read_text(encoding='utf8'))
    if set(hashes) != set(names):
        raise RuntimeError('Invalid baseline snapshot metadata')
    for name in names:
        if sha((before / name).read_bytes()) != hashes[name]:
            raise RuntimeError('Frozen baseline changed: ' + name)
    if sha(garden.read_bytes()) != hashes[names[0]]:
        raise RuntimeError('Requested Evergarden baseline differs from the frozen snapshot')
    if sha(magic.read_bytes()) != hashes[names[1]]:
        raise RuntimeError('Advance Magic changed since the baseline snapshot')
    return before, hashes


def reward_bounds(config):
    """Locate only the final nested world-boss.rewards block, never root rewards."""
    world = list(re.finditer(r'(?m)^world-boss:\r?$', config))
    root_rewards = list(re.finditer(r'(?m)^rewards:\r?$', config))
    if len(world) != 1 or len(root_rewards) != 1 or world[0].start() >= root_rewards[0].start():
        raise RuntimeError('Expected unique world-boss and top-level rewards sections')
    end = root_rewards[0].start()
    section = config[world[0].end():end]
    hits = list(re.finditer(r'(?m)^  rewards:\r?$', section))
    if len(hits) != 1:
        raise RuntimeError('Expected one nested world-boss.rewards section')
    start = world[0].end() + hits[0].start()
    for line in config[start:end].splitlines()[1:]:
        if line.strip() and not line.lstrip().startswith('#') and not line.startswith('    '):
            raise RuntimeError('Unexpected sibling after world-boss.rewards; review config patch')
    return start, end


def patch_config(config, source_config):
    start, end = reward_bounds(config)
    source_start, source_end = reward_bounds(source_config)
    block = source_config[source_start:source_end].replace('\r\n', '\n')
    if '\r\n' in config:
        block = block.replace('\n', '\r\n')
    return config[:start] + block + config[end:]


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--base', type=Path, default=ROOT / 'dist/evergarden.jar')
    parser.add_argument('--magic-base', type=Path, default=ROOT / 'dist/advance-magic.jar')
    parser.add_argument('--output', type=Path, default=OUT)
    parser.add_argument('--probe', type=Path, default=PROBE,
                        help='Paper probe source whose hash must match the validation receipt')
    parser.add_argument('--validation-server', type=Path,
                        help='Isolated Paper directory containing the matching receipt')
    parser.add_argument('--promote', action='store_true', help='Promote only the matching Paper-tested candidate')
    parser.add_argument('--versioned-only', action='store_true', help='Write only the numbered release JAR')
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

    families = tuple(PACKAGE + name for name in OWNED)

    def replaced(name):
        return any(name == family + '.class' or name.startswith(family + '$') for family in families)

    work = out / 'build'
    work.mkdir(parents=True, exist_ok=True)
    classes = work / 'classes'
    build.OUT = work
    sources = [source(name) for name in OWNED]
    source_hashes = {path.relative_to(ROOT).as_posix(): sha(path.read_bytes()) for path in sources}
    classpath = [base.as_posix(), (before / 'advance-magic.jar').as_posix()]
    classpath += build.pinned_classpath(build.EVERGARDEN_ARGS)
    build.compile_sources('temple-rewards', sources, classpath, classes)
    if any(sha(path.read_bytes()) != source_hashes[path.relative_to(ROOT).as_posix()] for path in sources):
        raise RuntimeError('Temple reward sources changed during compilation')

    with zipfile.ZipFile(base) as previous:
        replacements = {'plugin.yml': with_version(previous.read('plugin.yml').decode('utf8')).encode('utf8')}
        source_config = (ROOT / 'evergarden/src/main/resources/config.yml').read_bytes().decode('utf8')
        replacements['config.yml'] = patch_config(previous.read('config.yml').decode('utf8'), source_config).encode('utf8')
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

    preserved = {}
    with zipfile.ZipFile(candidate) as release, zipfile.ZipFile(base) as previous:
        if release.testzip() is not None or len(release.namelist()) != len(set(release.namelist())):
            raise RuntimeError('Invalid release archive')
        for family in families:
            if family + '.class' not in release.namelist():
                raise RuntimeError('Missing owned primary class: ' + family)
        for info in previous.infolist():
            if not replaced(info.filename) and info.filename not in replacements:
                old = previous.read(info.filename)
                if release.read(info.filename) != old:
                    raise RuntimeError('Unrelated entry changed: ' + info.filename)
                preserved[info.filename] = sha(old)
    build.check_jar(candidate)
    digest = sha(candidate.read_bytes())
    (out / 'asset-preservation.json').write_text(json.dumps({
        'baseline_sha256': baseline['evergarden.jar'], 'candidate_sha256': digest,
        'preserved_entries': preserved, 'count': len(preserved),
    }, indent=2) + '\n', encoding='utf8')
    if args.promote:
        delivered = ROOT / 'dist/evergarden.jar'
        if not delivered.is_file() or sha(delivered.read_bytes()) != baseline['evergarden.jar']:
            raise RuntimeError('Delivered Evergarden baseline changed')
        validation = (args.validation_server or out / 'paper').resolve()
        if not validation.is_relative_to((ROOT / 'output').resolve()):
            raise ValueError('Validation server must stay under workspace output/')
        receipt = json.loads((validation / RECEIPT).read_text(encoding='utf8'))
        if receipt['garden_sha256'] != digest or not receipt['report'].startswith('PASS '):
            raise RuntimeError('Candidate lacks matching Paper validation')
        if receipt['magic_sha256'] != baseline['advance-magic.jar']:
            raise RuntimeError('Advance Magic differs from the Paper validation')
        if receipt['probe_sha256'] != sha(args.probe.read_bytes()):
            raise RuntimeError('Paper probe changed after validation')
        plugin = ROOT / 'evergarden/src/main/resources/plugin.yml'
        plugin.write_bytes(with_version(plugin.read_bytes().decode('utf8')).encode('utf8'))
        targets = [ROOT / 'dist' / f'evergarden-{VERSION}.jar']
        if not args.versioned_only:
            targets.append(delivered)
        for target in targets:
            staged = target.with_suffix('.jar.staged')
            shutil.copy2(candidate, staged)
            os.replace(staged, target)
    report = {'version': VERSION, 'sha256': digest, 'baseline_sha256': baseline['evergarden.jar'],
              'magic_sha256': baseline['advance-magic.jar'], 'owned': OWNED,
              'sources': source_hashes, 'promoted': args.promote}
    (out / 'release-build.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf8')
    print(json.dumps(report, indent=2))


if __name__ == '__main__':
    main()
