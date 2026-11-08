"""Build the enchantment-audit release over the delivered Evergarden JAR.

The audit (RealmBossesNativeProbe) measures every over-cap enchantment a realm book can roll with the
game's own code, proves the anvil cannot climb to a boss tier, and checks Fortune / Looting on real
breaks and kills. The only code change: a realm core renamed on an anvil keeps a repair cost, which
stopped the Seraph's exact-match trade from taking it; RealmCores now clears it when it normalises a core.
Only that class and plugin.yml change; every other entry stays byte-identical.

    python tools/build_enchant_audit_release.py [--validate] [--promote] [--rebase]
"""
import argparse
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
from build_realm_bosses_release import MODEL_ENGINE, SERVER, sha, snapshot, with_version  # noqa: E402

OUT = ROOT / 'output/enchant-audit-20261029'
SLUG = 'enchant-audit'
FIRST_FREE = 169
OWNED = ('com/example/voidscape/boss/RealmCores',)
RECEIPT = 'enchant-audit-validation.json'
PROBE = ROOT / 'tests/model-engine/RealmBossesNativeProbe.java'
TRIALS = (('realm', '--realm-bosses'), ('readiness', '--boss-readiness'))
DATE = (2026, 10, 29, 0, 0, 0)


def owned(name):
    return any(name == f + '.class' or name.startswith(f + '$') for f in OWNED)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--base', type=Path, default=ROOT / 'dist/evergarden.jar')
    parser.add_argument('--magic-base', type=Path, default=ROOT / 'dist/advance-magic.jar')
    parser.add_argument('--validate', action='store_true')
    parser.add_argument('--promote', action='store_true')
    parser.add_argument('--rebase', action='store_true')
    args = parser.parse_args()
    OUT.mkdir(parents=True, exist_ok=True)
    if args.rebase and (OUT / 'before').exists():
        shutil.rmtree(OUT / 'before')
    before, baseline = snapshot(OUT, args.base.resolve(), args.magic_base.resolve())
    base = before / 'evergarden.jar'
    with zipfile.ZipFile(base) as archive:
        plugin = archive.read('plugin.yml').decode('utf8')
    current = re.search(r"(?m)^version: '([^']+)'", plugin).group(1)
    version = f"3.0.0-e2.{max(int(re.search(r'-e2\.(\d+)', current).group(1)) + 1, FIRST_FREE)}-{SLUG}"
    work = OUT / 'build'
    if (work / 'classes').exists():
        shutil.rmtree(work / 'classes')
    work.mkdir(parents=True, exist_ok=True)
    build.OUT = work
    sources = [ROOT / 'evergarden/src/main/java' / (f + '.java') for f in OWNED]
    hashes = {p.relative_to(ROOT).as_posix(): sha(p.read_bytes()) for p in sources}
    build.compile_sources(SLUG, sources, [base.as_posix(), (before / 'advance-magic.jar').as_posix()] + build.pinned_classpath(build.EVERGARDEN_ARGS),
                          work / 'classes')
    if any(sha(p.read_bytes()) != hashes[p.relative_to(ROOT).as_posix()] for p in sources):
        raise RuntimeError('Owned sources changed during compilation')
    candidate = work / 'evergarden.jar'
    with zipfile.ZipFile(base) as previous, zipfile.ZipFile(candidate, 'w', zipfile.ZIP_DEFLATED) as result:
        for info in previous.infolist():
            if not owned(info.filename) and info.filename != 'plugin.yml':
                result.writestr(info, previous.read(info.filename))
        for path in sorted((work / 'classes').rglob('*.class')):
            name = path.relative_to(work / 'classes').as_posix()
            if not owned(name):
                raise RuntimeError('Unexpected compiled class: ' + name)
            result.writestr(zipfile.ZipInfo(name, DATE), path.read_bytes(), compress_type=zipfile.ZIP_DEFLATED)
        result.writestr(zipfile.ZipInfo('plugin.yml', DATE), with_version(plugin, version).encode('utf8'), compress_type=zipfile.ZIP_DEFLATED)
    preserved = 0
    with zipfile.ZipFile(candidate) as release, zipfile.ZipFile(base) as previous:
        if release.testzip() is not None or len(release.namelist()) != len(set(release.namelist())):
            raise RuntimeError('Invalid release archive')
        for info in previous.infolist():
            if not owned(info.filename) and info.filename != 'plugin.yml':
                if release.read(info.filename) != previous.read(info.filename):
                    raise RuntimeError('Unrelated entry changed: ' + info.filename)
                preserved += 1
    build.check_jar(candidate)
    digest = sha(candidate.read_bytes())
    if args.validate:
        reports, failed = {}, []
        for label, flag in TRIALS:
            trial = OUT / f'paper-{digest[:10]}-{label}'
            if trial.exists():
                shutil.rmtree(trial)
            run = subprocess.run([sys.executable, str(ROOT / 'tools/try_model_engine.py'), flag, '--source-server', str(SERVER), '--plugin', str(MODEL_ENGINE),
                                  '--garden-jar', str(candidate), '--magic-jar', str(before / 'advance-magic.jar'), '--test-directory', str(trial)],
                                 capture_output=True, text=True)
            result = trial / 'model-engine-result.txt'
            report = result.read_text(encoding='utf8') if result.exists() else (run.stdout + run.stderr)[-3000:]
            print(f'--- {label}\n{report}')
            reports[label] = report.splitlines()[0] if report else 'FAIL'
            if run.returncode or not reports[label].startswith('PASS'):
                failed.append(label)
        receipt = {'garden_sha256': digest, 'reports': reports, 'probe_sha256': sha(PROBE.read_bytes()),
                   'report': 'FAIL ' + ', '.join(failed) if failed else 'PASS ' + '; '.join(f'{k}: {v}' for k, v in reports.items())}
        (OUT / RECEIPT).write_text(json.dumps(receipt, indent=2) + '\n', encoding='utf8')
        if failed:
            raise RuntimeError('Paper validation failed: ' + ', '.join(failed))
    if args.promote:
        delivered = ROOT / 'dist/evergarden.jar'
        if sha(delivered.read_bytes()) != baseline['evergarden.jar']:
            raise RuntimeError('Delivered Evergarden JAR changed since the baseline was pinned; rerun with --rebase --validate')
        receipt = json.loads((OUT / RECEIPT).read_text(encoding='utf8'))
        if receipt['garden_sha256'] != digest or not receipt['report'].startswith('PASS') or receipt['probe_sha256'] != sha(PROBE.read_bytes()):
            raise RuntimeError('Candidate lacks matching Paper validation')
        source_plugin = ROOT / 'evergarden/src/main/resources/plugin.yml'
        source_plugin.write_bytes(with_version(source_plugin.read_bytes().decode('utf8'), version).encode('utf8'))
        for target in (ROOT / 'dist' / f'evergarden-{version}.jar', delivered):
            staged = target.with_suffix('.jar.staged')
            shutil.copy2(candidate, staged)
            os.replace(staged, target)
    report = {'version': version, 'from': current, 'sha256': digest, 'baseline_sha256': baseline['evergarden.jar'], 'sources': hashes,
              'preserved_entries': preserved, 'promoted': args.promote}
    (OUT / 'release-build.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf8')
    print(json.dumps(report, indent=2))


if __name__ == '__main__':
    main()
