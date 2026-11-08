"""Build Advance Magic 1.41.0-heal-support over the delivered JAR (1.40.0-dragon-powers).

Heal and support spells worked in tests but failed in play (measured 2026-11-07 on a real Paper server):
- heals, shields and buffs only reached players on the caster's vanilla scoreboard team, so friends got nothing;
- Healing Spring / Holy Sanctuary opened where the caster aimed (up to 24 blocks away), so aiming at a mob, the
  horizon or the sky healed nobody, yet the cast still cost mana and cooldown;
- the 60% per-cast cap made level V and level VII heal the same, and staffs never strengthened heals;
- Life Drain returned at most 20% max HP (5% from bosses) instead of the 40% of damage its scroll promises.

ElementalSpellService (and its nested classes), config.yml (new elemental-combat.support-all-players) and
plugin.yml change; every other entry, the packs included, stays byte-identical.

    python tools/build_heal_support_release.py [--validate] [--promote]

--validate runs the elemental, levels, spectacle and blocks-bedrock Paper probes on the candidate and writes a
receipt; --promote only ships the candidate that receipt names.
"""
import argparse
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'tools'))
import build_plugin_jars as build  # noqa: E402

OUT = ROOT / 'output/heal-support-20261107'
VERSION = '1.41.0-heal-support'
BASE_VERSION = '1.40.0-dragon-powers'
BASE_SHA256 = '2b8a31e518814b707b89420bf28d3dbb4f8c27145ced9e3ac2ccc624b2f415cd'
OWNED = ('com/example/advancemagic/elemental/ElementalSpellService',)
RESOURCES = ('config.yml',)
SOURCE_SERVER = ROOT / 'output/entry-items-check-20261031'
TESTS = ROOT / 'advance-magic/tests'
PROBE_FILES = ['run_elemental_system_checks.py', 'ElementalSystemChecks.java', 'SpellLevelChecks.java',
               'LegendarySpectacleChecks.java', 'LegendaryBlockBedrockChecks.java']
# Probe -> (result file, passing rule). ElementalSystemChecks halts at its last section, gui(), which tests a
# long-replaced menu and already failed before this release; every earlier section must pass.
GATES = {
    'elemental': 'PASS cancelled native regain respects heal protection while Phoenix leaves one health',
    'levels': None,
    'spectacle': None,
    'blocks-bedrock': None,
}
RECEIPT = 'heal-support-validation.json'
DATE = (2026, 11, 7, 0, 0, 0)


def sha(data):
    return hashlib.sha256(data).hexdigest()


def owned(name):
    return name in RESOURCES or any(name == family + '.class' or name.startswith(family + '$') for family in OWNED)


def with_version(text):
    text, count = re.subn(r"(?m)^version:[^\r\n]*", "version: '" + VERSION + "'", text)
    if count != 1:
        raise RuntimeError('plugin.yml must contain exactly one version')
    return text


def run_gate(probe, candidate, digest):
    server = OUT / f'{probe}-{digest[:8]}'
    if server.exists():
        shutil.rmtree(server)
    run = subprocess.run([sys.executable, str(TESTS / 'run_elemental_system_checks.py'), '--probe', probe, '--source-server',
                          str(SOURCE_SERVER), '--server', str(server), '--plugin-jar', str(candidate), '--timeout', '600'],
                         capture_output=True, text=True, encoding='utf8', errors='replace')
    lines = [line for line in run.stdout.splitlines() if line.startswith(('PASS', 'FAIL'))]
    report = lines[-1] if lines else 'FAIL ' + (run.stdout + run.stderr)[-1500:]
    console = (server / 'console.log').read_text(encoding='utf8', errors='replace') if (server / 'console.log').exists() else ''
    required = GATES[probe]
    if required is None:
        ok = run.returncode == 0 and report.startswith('PASS')
    else:
        # The known gui() failure is allowed only when every check before it passed and nothing else broke.
        ok = required in console and 'Runtime exception logged' not in run.stdout + run.stderr
    return {'report': report, 'ok': ok, 'server': server.relative_to(ROOT).as_posix()}


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--validate', action='store_true')
    parser.add_argument('--promote', action='store_true')
    parser.add_argument('--gate', action='append', choices=sorted(GATES), help='Validate only these probes (receipt still needs all)')
    args = parser.parse_args()
    OUT.mkdir(parents=True, exist_ok=True)
    delivered = ROOT / 'dist/advance-magic.jar'
    baseline = OUT / 'baseline-advance-magic.jar'
    if not baseline.exists():
        if sha(delivered.read_bytes()) != BASE_SHA256:
            raise RuntimeError('dist/advance-magic.jar is not the expected ' + BASE_VERSION)
        shutil.copy2(delivered, baseline)
    if sha(baseline.read_bytes()) != BASE_SHA256:
        raise RuntimeError('Pinned baseline is not ' + BASE_VERSION)
    with zipfile.ZipFile(baseline) as jar:
        plugin = jar.read('plugin.yml').decode('utf8')

    classes = OUT / 'classes'
    build.OUT = OUT
    sources = [ROOT / 'advance-magic/src/main/java' / (family + '.java') for family in OWNED]
    resources = {name: ROOT / 'advance-magic/src/main/resources' / name for name in RESOURCES}
    source_hashes = {p.relative_to(ROOT).as_posix(): sha(p.read_bytes()) for p in [*sources, *resources.values()]}
    build.compile_sources('heal-support', sources, [baseline.as_posix(), (ROOT / 'dist/evergarden.jar').as_posix()]
                          + build.pinned_classpath(build.ADVANCE_ARGS), classes)
    if any(sha((ROOT / p).read_bytes()) != h for p, h in source_hashes.items()):
        raise RuntimeError('Owned sources changed during compilation')

    candidate = OUT / 'advance-magic.jar'
    with zipfile.ZipFile(baseline) as previous, zipfile.ZipFile(candidate, 'w', zipfile.ZIP_DEFLATED) as result:
        for info in previous.infolist():
            if not owned(info.filename) and info.filename != 'plugin.yml':
                result.writestr(info, previous.read(info.filename))
        for path in sorted(classes.rglob('*.class')):
            name = path.relative_to(classes).as_posix()
            if not owned(name):
                raise RuntimeError('Unexpected compiled class: ' + name)
            result.writestr(zipfile.ZipInfo(name, DATE), path.read_bytes(), compress_type=zipfile.ZIP_DEFLATED)
        for name, path in resources.items():
            result.writestr(zipfile.ZipInfo(name, DATE), path.read_bytes(), compress_type=zipfile.ZIP_DEFLATED)
        result.writestr(zipfile.ZipInfo('plugin.yml', DATE), with_version(plugin).encode('utf8'), compress_type=zipfile.ZIP_DEFLATED)
    preserved = 0
    with zipfile.ZipFile(candidate) as release, zipfile.ZipFile(baseline) as previous:
        if release.testzip() is not None or len(release.namelist()) != len(set(release.namelist())):
            raise RuntimeError('Invalid release archive')
        for info in previous.infolist():
            if not owned(info.filename) and info.filename != 'plugin.yml':
                if release.read(info.filename) != previous.read(info.filename):
                    raise RuntimeError('Unrelated entry changed: ' + info.filename)
                preserved += 1
        missing = {n for n in previous.namelist() if owned(n)} - set(release.namelist())
        if missing:
            raise RuntimeError('Owned entries vanished: ' + ', '.join(sorted(missing)))
    build.check_jar(candidate)
    digest = sha(candidate.read_bytes())
    probe_hashes = {name: sha((TESTS / name).read_bytes()) for name in PROBE_FILES}

    receipt_path = OUT / RECEIPT
    if args.validate:
        receipt = json.loads(receipt_path.read_text(encoding='utf8')) if receipt_path.exists() else {}
        if receipt.get('sha256') != digest or receipt.get('probes') != probe_hashes:
            receipt = {'sha256': digest, 'probes': probe_hashes, 'gates': {}}
        for probe in args.gate or GATES:
            receipt['gates'][probe] = run_gate(probe, candidate, digest)
            print(probe + ':', receipt['gates'][probe]['report'], '->', 'ok' if receipt['gates'][probe]['ok'] else 'FAILED')
            receipt_path.write_text(json.dumps(receipt, indent=2) + '\n', encoding='utf8')
        if not all(receipt['gates'].get(p, {}).get('ok') for p in args.gate or GATES):
            raise RuntimeError('A Paper gate failed')
    if args.promote:
        receipt = json.loads(receipt_path.read_text(encoding='utf8'))
        if receipt['sha256'] != digest or receipt['probes'] != probe_hashes or not all(receipt['gates'].get(p, {}).get('ok') for p in GATES):
            raise RuntimeError('Candidate lacks matching Paper validation for every gate')
        if sha(delivered.read_bytes()) != BASE_SHA256:
            raise RuntimeError('dist/advance-magic.jar changed since the baseline was pinned')
        source_plugin = ROOT / 'advance-magic/src/main/resources/plugin.yml'
        source_plugin.write_text(with_version(source_plugin.read_text(encoding='utf8')), encoding='utf8')
        for target in (ROOT / 'dist' / f'advance-magic-{VERSION}.jar', delivered):
            staged = target.with_suffix('.jar.staged')
            shutil.copy2(candidate, staged)
            os.replace(staged, target)
        if sha(delivered.read_bytes()) != digest:
            raise RuntimeError('Promoted JAR differs from the validated candidate')
    report = {'version': VERSION, 'from': BASE_VERSION, 'sha256': digest, 'baseline_sha256': BASE_SHA256,
              'owned': [*OWNED, *RESOURCES], 'sources': source_hashes, 'preserved_entries': preserved, 'promoted': args.promote}
    (OUT / 'release-build.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf8')
    print(json.dumps(report, indent=2))


if __name__ == '__main__':
    main()
