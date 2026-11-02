"""Build the merged realm-boss release over the delivered e2.159 baseline.

e2.159 loaded the realm rigs one at a time, because the user's ModelEngine build imports twelve
models in all and the main bosses with internal_fire already take eleven. This release ships all
four rigs in ONE blueprint (modelengine/realm/realm_bosses.bbmodel, a single import slot); each
showcase spawn keeps only its rig's bones, so every rig spawns from /evergarden test without a
restart. The per-rig blueprint an e2.159 server installed is retired on startup.

Only the owned class families, the merged blueprint and plugin.yml change; the Bedrock pack, the
per-rig blueprints and every other entry stay byte-identical.

    python evergarden/tools/realm_bosses.py --merge-only
    python tools/build_realm_bosses_merged_release.py [--validate] [--promote]
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
from build_realm_bosses_release import OWNED, PACKAGE, MODEL_ENGINE, SERVER, REALM, MODELS, sha, snapshot, with_version  # noqa: E402

OUT = ROOT / 'output/realm-bosses-merged-20261029'
SLUG = 'realm-bosses-merged'
RECEIPT = 'realm-merged-validation.json'
PROBE = ROOT / 'tests/model-engine/RealmBossesNativeProbe.java'
DATE = (2026, 10, 29, 0, 0, 0)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--base', type=Path, default=ROOT / 'dist/evergarden.jar')
    parser.add_argument('--magic-base', type=Path, default=ROOT / 'dist/advance-magic.jar')
    parser.add_argument('--output', type=Path, default=OUT)
    parser.add_argument('--validate', action='store_true', help='Run the realm-boss and boss-readiness Paper probes and record a receipt')
    parser.add_argument('--promote', action='store_true', help='Promote only the matching Paper-validated candidate')
    args = parser.parse_args()
    out = args.output.resolve()
    if not out.is_relative_to((ROOT / 'output').resolve()):
        raise ValueError('Build directory must stay under workspace output/')
    out.mkdir(parents=True, exist_ok=True)
    before, baseline = snapshot(out, args.base.resolve(), args.magic_base.resolve())
    base = before / 'evergarden.jar'
    with zipfile.ZipFile(base) as archive:
        names = set(archive.namelist())
        plugin = archive.read('plugin.yml').decode('utf8')
        for model_id in MODELS:
            for suffix in ('.bbmodel', '-manifest.json'):
                entry = f'modelengine/realm/{model_id}{suffix}'
                if entry not in names or archive.read(entry) != (REALM / f'{model_id}{suffix}').read_bytes():
                    raise RuntimeError('Per-rig blueprint differs from the delivered one (rebuild the rigs only in a full realm release): ' + entry)
    current = re.search(r"(?m)^version: '([^']+)'", plugin).group(1)
    if not current.endswith('-realm-bosses'):
        raise RuntimeError('Expected the e2.159 realm-bosses baseline, found ' + current)
    number = int(re.search(r'-e2\.(\d+)', current).group(1))
    version = f'3.0.0-e2.{number + 1}-{SLUG}'

    families = tuple(PACKAGE + name for name in OWNED)

    def replaced(name):
        return any(name == family + '.class' or name.startswith(family + '$') for family in families)

    work = out / 'build'
    work.mkdir(parents=True, exist_ok=True)
    build.OUT = work
    sources = [ROOT / 'evergarden/src/main/java' / (PACKAGE + name + '.java') for name in OWNED]
    source_hashes = {p.relative_to(ROOT).as_posix(): sha(p.read_bytes()) for p in sources}
    classpath = [base.as_posix(), (before / 'advance-magic.jar').as_posix()] + build.pinned_classpath(build.EVERGARDEN_ARGS)
    build.compile_sources('realm-bosses-merged', sources, classpath, work / 'classes')
    if any(sha(p.read_bytes()) != source_hashes[p.relative_to(ROOT).as_posix()] for p in sources):
        raise RuntimeError('Owned sources changed during compilation')

    replacements = {'plugin.yml': with_version(plugin, version).encode('utf8')}
    for suffix in ('.bbmodel', '-manifest.json'):
        replacements[f'modelengine/realm/realm_bosses{suffix}'] = (REALM / f'realm_bosses{suffix}').read_bytes()
    gate = subprocess.run([sys.executable, str(ROOT / 'tests/model-engine/check_realm_bosses.py'), '--pack',
                           str(ROOT / 'evergarden/dist/evergarden-bedrock.mcpack')], capture_output=True, text=True)
    print(gate.stdout.strip() or gate.stderr.strip()[-2000:])
    if gate.returncode:
        raise RuntimeError('Realm-boss asset gate failed')

    candidate = work / 'evergarden.jar'
    with zipfile.ZipFile(base) as previous, zipfile.ZipFile(candidate, 'w', zipfile.ZIP_DEFLATED) as result:
        for info in previous.infolist():
            if not replaced(info.filename) and info.filename not in replacements:
                result.writestr(info, previous.read(info.filename))
        for path in sorted((work / 'classes').rglob('*.class')):
            name = path.relative_to(work / 'classes').as_posix()
            if not replaced(name):
                raise RuntimeError('Unexpected compiled class: ' + name)
            result.writestr(zipfile.ZipInfo(name, DATE), path.read_bytes(), compress_type=zipfile.ZIP_DEFLATED)
        for name, data in replacements.items():
            result.writestr(zipfile.ZipInfo(name, DATE), data, compress_type=zipfile.ZIP_DEFLATED)
    preserved = {}
    with zipfile.ZipFile(candidate) as release, zipfile.ZipFile(base) as previous:
        if release.testzip() is not None or len(release.namelist()) != len(set(release.namelist())):
            raise RuntimeError('Invalid release archive')
        for info in previous.infolist():
            if not replaced(info.filename) and info.filename not in replacements:
                old = previous.read(info.filename)
                if release.read(info.filename) != old:
                    raise RuntimeError('Unrelated entry changed: ' + info.filename)
                preserved[info.filename] = sha(old)
    build.check_jar(candidate)
    digest = sha(candidate.read_bytes())
    (out / 'asset-preservation.json').write_text(json.dumps({'baseline_sha256': baseline['evergarden.jar'], 'candidate_sha256': digest,
                                                             'count': len(preserved), 'preserved_entries': preserved}, indent=2) + '\n',
                                                 encoding='utf8')
    if args.validate:
        reports, failed = {}, []
        for label, flag in (('realm', '--realm-bosses'), ('readiness', '--boss-readiness')):
            trial = out / f'paper-{digest[:10]}-{label}'
            if trial.exists():
                shutil.rmtree(trial)
            run = subprocess.run([sys.executable, str(ROOT / 'tools/try_model_engine.py'), flag, '--source-server', str(SERVER),
                                  '--plugin', str(MODEL_ENGINE), '--garden-jar', str(candidate), '--magic-jar', str(before / 'advance-magic.jar'),
                                  '--test-directory', str(trial)], capture_output=True, text=True)
            result = trial / 'model-engine-result.txt'
            report = result.read_text(encoding='utf8') if result.exists() else (run.stdout + run.stderr)[-3000:]
            print(f'--- {label}\n{report}')
            reports[label] = report.splitlines()[0] if report else 'FAIL'
            if run.returncode or not reports[label].startswith('PASS'):
                failed.append(label)
        receipt = {'garden_sha256': digest, 'magic_sha256': baseline['advance-magic.jar'], 'reports': reports,
                   'report': 'FAIL ' + ', '.join(failed) if failed else 'PASS ' + '; '.join(f'{k}: {v}' for k, v in reports.items()),
                   'probe_sha256': sha(PROBE.read_bytes())}
        (out / RECEIPT).write_text(json.dumps(receipt, indent=2) + '\n', encoding='utf8')
        if failed:
            raise RuntimeError('Paper probe failed: ' + ', '.join(failed))
    if args.promote:
        delivered = ROOT / 'dist/evergarden.jar'
        if not delivered.is_file() or sha(delivered.read_bytes()) != baseline['evergarden.jar']:
            raise RuntimeError('Delivered Evergarden baseline changed; rebuild over the new one')
        receipt = json.loads((out / RECEIPT).read_text(encoding='utf8'))
        if receipt['garden_sha256'] != digest or not receipt['report'].startswith('PASS') or receipt['probe_sha256'] != sha(PROBE.read_bytes()):
            raise RuntimeError('Candidate lacks matching Paper validation')
        source_plugin = ROOT / 'evergarden/src/main/resources/plugin.yml'
        source_plugin.write_bytes(with_version(source_plugin.read_bytes().decode('utf8'), version).encode('utf8'))
        for target in (ROOT / 'dist' / f'evergarden-{version}.jar', delivered):
            staged = target.with_suffix('.jar.staged')
            shutil.copy2(candidate, staged)
            os.replace(staged, target)
    report = {'version': version, 'from': current, 'sha256': digest, 'baseline_sha256': baseline['evergarden.jar'], 'owned': OWNED,
              'sources': source_hashes, 'promoted': args.promote}
    (out / 'release-build.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf8')
    print(json.dumps(report, indent=2))


if __name__ == '__main__':
    main()
