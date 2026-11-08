"""Evergarden e2.203: new Bodiless Judge rewards.

    python tools/build_judge_rewards_release.py --srccheck   committed WorldBossManager source == the classes shipped in e2.202?
    python tools/build_judge_rewards_release.py              build the candidate over the released e2.202
    python tools/build_judge_rewards_release.py --promote    replace dist/evergarden.jar (after validation.json says PASS)

Each player credited with damage now gets one Tier VII and one Tier VI Vault key, one pet egg (baby form; every species
equally likely, the five mythical ones included), one Legendary spell scroll, and the unchanged XP, skill point and
Seraph feathers. Tier V keys queued by older victories stay claimable. Changes against the e2.202 baseline:
WorldBossManager, config.yml (world-boss.rewards) and plugin.yml. Every other entry stays byte-identical.
Nothing is published or deployed.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parent))
import build_plugin_jars as build  # noqa: E402

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'output/judge-rewards-release'
VERSION = '3.0.1-e2.203-judge-rewards'
BASE_VERSION = '3.0.1-e2.202-death-messages'
BASE_SHA256 = '1de8ee9c041dca3f2593feed8b25e851c061fa2859435effe566e08e9fc2b641'   # released, with pack-mirror.properties
DATE = (2026, 11, 8, 0, 0, 0)
BOSS = 'com/example/voidscape/boss/'
FAMILIES = (BOSS + 'WorldBossManager',)
SHIPPED = FAMILIES[:1]
SOURCES = ROOT / 'evergarden/src/main/java'
CONFIG = ROOT / 'evergarden/src/main/resources/config.yml'


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def owned(name, families=FAMILIES):
    return any(name == f + '.class' or name.startswith(f + '$') for f in families)


def version(data):
    text, count = re.subn(r'(?m)^version:[^\r\n]*', "version: '" + VERSION + "'", data.decode('utf8'))
    if count != 1:
        raise RuntimeError('Expected one plugin version')
    return text.encode('utf8')


def baseline():
    OUT.mkdir(parents=True, exist_ok=True)
    garden, jar = ROOT / 'dist/evergarden.jar', OUT / 'baseline-evergarden.jar'
    if not jar.exists():
        if sha(garden) != BASE_SHA256:
            raise RuntimeError('dist/evergarden.jar is not the released ' + BASE_VERSION)
        shutil.copy2(garden, jar)
    if sha(jar) != BASE_SHA256:
        raise RuntimeError('Baseline copy changed')
    return jar


def compile_into(jar, sources, classes):
    build.OUT = OUT
    build.compile_sources('judge-rewards', sources,
                          [str(jar), str(ROOT / 'dist/advance-magic.jar')] + build.pinned_classpath(build.EVERGARDEN_ARGS), classes)
    return {p.relative_to(classes).as_posix(): p.read_bytes() for p in classes.rglob('*.class')}


def srccheck():
    jar = baseline()
    committed = OUT / 'committed-src' / (SHIPPED[0] + '.java')
    committed.parent.mkdir(parents=True, exist_ok=True)
    rel = 'evergarden/src/main/java/' + SHIPPED[0] + '.java'
    committed.write_bytes(subprocess.run(['git', 'show', 'HEAD:' + rel], cwd=ROOT, capture_output=True, check=True).stdout)
    compiled = compile_into(jar, [committed], OUT / 'srccheck-classes')
    with zipfile.ZipFile(jar) as old:
        shipped = {n: old.read(n) for n in old.namelist() if owned(n, SHIPPED)}
    differs = sorted(n for n in set(shipped) | set(compiled) if shipped.get(n) != compiled.get(n))
    print(json.dumps({'srccheck': 'DIFFERENT' if differs else 'IDENTICAL', 'classes': len(compiled), 'differs': differs}, indent=2))
    if differs:
        raise SystemExit(1)


def candidate():
    jar = baseline()
    sources = [SOURCES / (f + '.java') for f in FAMILIES]
    hashes = {p.relative_to(ROOT).as_posix(): sha(p) for p in [*sources, CONFIG]}
    compiled = compile_into(jar, sources, OUT / 'classes')
    if any(sha(ROOT / p) != d for p, d in hashes.items()):
        raise RuntimeError('Sources changed during compilation')
    if any(not owned(n) for n in compiled) or any(f + '.class' not in compiled for f in FAMILIES):
        raise RuntimeError('Unexpected compiler output: ' + ', '.join(sorted(compiled)))
    target = OUT / 'evergarden.jar'
    with zipfile.ZipFile(jar) as old:
        names = old.namelist()
        stale = [n for n in names if owned(n) and n not in compiled]
        replaced = {'plugin.yml': version(old.read('plugin.yml')), 'config.yml': CONFIG.read_bytes(), **compiled}
        with zipfile.ZipFile(target, 'w', zipfile.ZIP_DEFLATED) as new:
            for info in old.infolist():
                if info.filename not in replaced and info.filename not in stale:
                    new.writestr(info, old.read(info.filename))
            for name, data in sorted(replaced.items()):
                new.writestr(zipfile.ZipInfo(name, DATE), data, compress_type=zipfile.ZIP_DEFLATED)
    with zipfile.ZipFile(jar) as old, zipfile.ZipFile(target) as new:
        if new.testzip() or len(new.namelist()) != len(set(new.namelist())):
            raise RuntimeError('Invalid candidate archive')
        preserved = [n for n in names if n not in replaced and n not in stale]
        if any(old.read(n) != new.read(n) for n in preserved):
            raise RuntimeError('Unrelated JAR entry changed')
    build.check_jar(target)
    report = {'version': VERSION, 'sources': hashes, 'baseline_sha256': sha(jar), 'candidate_sha256': sha(target),
              'magic_sha256': sha(ROOT / 'dist/advance-magic.jar'), 'preserved_entries': len(preserved),
              'replaced': sorted(replaced), 'removed_stale': stale, 'promoted': False}
    (OUT / 'build-report.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf8')
    print(json.dumps({k: v for k, v in report.items() if k != 'sources'}, indent=2))


def promote():
    report = json.loads((OUT / 'build-report.json').read_text(encoding='utf8'))
    receipt = json.loads((OUT / 'validation.json').read_text(encoding='utf8'))
    if receipt.get('result') != 'PASS':
        raise RuntimeError('Validation did not pass')
    if not sha(OUT / 'evergarden.jar') == report['candidate_sha256'] == receipt['garden_sha256']:
        raise RuntimeError('Candidate changed after validation')
    if sha(ROOT / 'dist/evergarden.jar') != report['baseline_sha256']:
        raise RuntimeError('dist/evergarden.jar is no longer the baseline')
    if any(sha(ROOT / p) != d for p, d in report['sources'].items()):
        raise RuntimeError('Sources changed after validation')
    for path in (ROOT / 'dist/evergarden.jar', ROOT / f'dist/evergarden-{VERSION}.jar'):
        staged = path.with_suffix('.jar.staged')
        shutil.copy2(OUT / 'evergarden.jar', staged)
        os.replace(staged, path)
    plugin = ROOT / 'evergarden/src/main/resources/plugin.yml'
    plugin.write_text(re.sub(r"(?m)^version:[^\r\n]*", "version: '" + VERSION + "'", plugin.read_text(encoding='utf8')),
                      encoding='utf8')
    report.update(promoted=True, validation=receipt)
    (OUT / 'build-report.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf8')
    print('Promoted ' + VERSION)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    group = parser.add_mutually_exclusive_group()
    group.add_argument('--srccheck', action='store_true')
    group.add_argument('--promote', action='store_true')
    args = parser.parse_args()
    srccheck() if args.srccheck else promote() if args.promote else candidate()


if __name__ == '__main__':
    main()
