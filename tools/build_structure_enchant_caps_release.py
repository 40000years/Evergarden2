"""Cap enchant levels in boss structure chests (Arkhaia, Valkyrie, Seraphon) over the e2.174 delivery.

    python tools/build_structure_enchant_caps_release.py              build the candidate
    python tools/build_structure_enchant_caps_release.py --validate   isolated Paper checks
    python tools/build_structure_enchant_caps_release.py --promote    replace dist/evergarden.jar

Only BossLootTable, the bundled boss-treasure.yml and plugin.yml change; every other JAR entry,
including the resource packs, stays byte-identical to the baseline. Validation runs
evergarden/tests/StructureEnchantCapChecks.java three times on a copy of an isolated Paper fixture:
the baseline JAR (chest fingerprints), the candidate on the server's old boss-treasure.yml (default
caps are added), and the candidate with edited caps. Candidate fingerprints must equal the baseline:
the caps change levels only, never which items a chest holds.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import time
import zipfile
import build_plugin_jars as build

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'output/structure-enchant-caps'
VERSION = '3.0.0-e2.176-structure-enchant-caps'
FAMILIES = ('com/example/voidscape/world/BossLootTable',)
RESOURCES = {'boss-treasure.yml': ROOT / 'evergarden/src/main/resources/boss-treasure.yml'}
PROBE = ROOT / 'evergarden/tests/StructureEnchantCapChecks.java'
FIXTURE_SOURCE = ROOT / 'output/craft-check-paper231'
RECEIPT = 'structure-caps-validation.json'


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def owned(name):
    return name in RESOURCES or any(name == f + '.class' or name.startswith(f + '$') for f in FAMILIES)


def version(data):
    text, count = re.subn(r'(?m)^version:[^\r\n]*', "version: '" + VERSION + "'", data.decode('utf8'))
    if count != 1:
        raise RuntimeError('Expected one plugin version')
    return text.encode('utf8')


def candidate_build():
    garden, magic = ROOT / 'dist/evergarden.jar', ROOT / 'dist/advance-magic.jar'
    candidate, baseline = OUT / 'evergarden.jar', OUT / 'baseline-evergarden.jar'
    if not baseline.exists():
        shutil.copy2(garden, baseline)
    if sha(garden) != sha(baseline):
        raise RuntimeError('Delivery baseline changed; use a new release builder')
    sources = [ROOT / 'evergarden/src/main/java' / (f + '.java') for f in FAMILIES] + list(RESOURCES.values())
    hashes = {p.relative_to(ROOT).as_posix(): sha(p) for p in sources}
    build.OUT = OUT
    classes = OUT / 'classes'
    shutil.rmtree(classes, ignore_errors=True)
    build.compile_sources('structure-enchant-caps', [p for p in sources if p.suffix == '.java'],
                          [str(baseline), str(magic)] + build.pinned_classpath(build.EVERGARDEN_ARGS), classes)
    if any(sha(ROOT / p) != d for p, d in hashes.items()):
        raise RuntimeError('Sources changed during compilation')
    compiled = {p.relative_to(classes).as_posix(): p.read_bytes() for p in classes.rglob('*.class')}
    if not compiled or any(not owned(n) for n in compiled) or any(f + '.class' not in compiled for f in FAMILIES):
        raise RuntimeError('Unexpected compiler output: ' + ', '.join(sorted(compiled)))
    date = (2026, 10, 31, 0, 0, 0)
    with zipfile.ZipFile(baseline) as old, zipfile.ZipFile(candidate, 'w', zipfile.ZIP_DEFLATED) as new:
        for entry in old.infolist():
            if not owned(entry.filename) and entry.filename != 'plugin.yml':
                new.writestr(entry, old.read(entry.filename))
        replaced = {'plugin.yml': version(old.read('plugin.yml')), **compiled,
                    **{name: path.read_bytes() for name, path in RESOURCES.items()}}
        for name, data in replaced.items():
            new.writestr(zipfile.ZipInfo(name, date), data, compress_type=zipfile.ZIP_DEFLATED)
    with zipfile.ZipFile(baseline) as old, zipfile.ZipFile(candidate) as new:
        if new.testzip() or len(new.namelist()) != len(set(new.namelist())):
            raise RuntimeError('Invalid candidate archive')
        preserved = [n for n in old.namelist() if not owned(n) and n != 'plugin.yml']
        if any(old.read(n) != new.read(n) for n in preserved):
            raise RuntimeError('Unrelated JAR entry changed')
        if set(new.namelist()) != set(old.namelist()) | set(compiled):
            raise RuntimeError('Unexpected entry set')
    build.check_jar(candidate)
    report = {'version': VERSION, 'sources': hashes, 'baseline_sha256': sha(baseline),
              'candidate_sha256': sha(candidate), 'magic_sha256': sha(magic),
              'preserved_entries': len(preserved), 'replaced': sorted(replaced), 'promoted': False}
    (OUT / 'build-report.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf8')
    print(json.dumps(report, indent=2))


def run_server(server, timeout=420):
    (server / 'structure-caps-result.txt').unlink(missing_ok=True)
    log = server / ('console-' + str(time.time_ns()) + '.log')
    with log.open('w', encoding='utf8') as console:
        process = subprocess.Popen(['java', '-Xms256M', '-Xmx3072M', '-Dterminal.jline=false', '-Dterminal.ansi=false',
                                    '-jar', 'paper.jar', '--nogui', '--noconsole'], cwd=server, stdout=console,
                                   stderr=subprocess.STDOUT,
                                   creationflags=subprocess.CREATE_NO_WINDOW if os.name == 'nt' else 0)
        try:
            process.wait(timeout=timeout)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait()
            raise RuntimeError('Paper timed out: ' + str(log))
    result = server / 'structure-caps-result.txt'
    if not result.exists():
        raise RuntimeError('No probe result; see ' + str(log))
    return result.read_text(encoding='utf8')


def validate():
    report = json.loads((OUT / 'build-report.json').read_text(encoding='utf8'))
    candidate, baseline, magic = OUT / 'evergarden.jar', OUT / 'baseline-evergarden.jar', ROOT / 'dist/advance-magic.jar'
    if sha(candidate) != report['candidate_sha256'] or sha(magic) != report['magic_sha256']:
        raise RuntimeError('Candidate or Advance Magic changed after build')
    server = OUT / 'paper'
    if not server.exists():
        server.mkdir(parents=True)
        for name in ('paper.jar', 'libraries', 'versions', 'cache', 'config', 'bukkit.yml', 'spigot.yml', 'commands.yml',
                     'server.properties', 'eula.txt', 'model_engine_trial'):
            source = FIXTURE_SOURCE / name
            (shutil.copytree if source.is_dir() else shutil.copy2)(source, server / name)
        (server / 'model_engine_trial/session.lock').unlink(missing_ok=True)
        (server / 'plugins').mkdir()
        for jar in FIXTURE_SOURCE.glob('plugins/*.jar'):
            if 'probe' not in jar.name:
                shutil.copy2(jar, server / 'plugins' / jar.name)
        for folder in (FIXTURE_SOURCE / 'plugins').iterdir():
            if folder.is_dir():
                shutil.copytree(folder, server / 'plugins' / folder.name)
    plugins = server / 'plugins'
    data = plugins / 'Evergarden/boss-treasure.yml'
    pristine = OUT / 'pristine-boss-treasure.yml'
    if not pristine.exists():
        shutil.copy2(data, pristine)
    if b'structure-level-caps' in pristine.read_bytes():
        raise RuntimeError('Fixture boss-treasure.yml must predate the caps')
    shutil.copy2(magic, plugins / 'advance-magic.jar')
    classes = OUT / 'probe-classes'
    shutil.rmtree(classes, ignore_errors=True)
    classes.mkdir()
    classpath = os.pathsep.join(str(p) for p in [candidate, magic, *(server / 'libraries').rglob('*.jar'),
                                                  *(server / 'versions').rglob('*.jar')])
    probe_hash = sha(PROBE)
    args = OUT / 'probe-javac.args'
    args.write_text('\n'.join('"' + v.replace('\\', '/') + '"' for v in
                              ['--release', '25', '-proc:none', '-encoding', 'UTF-8', '-cp', classpath, '-d', str(classes), str(PROBE)]),
                    encoding='utf8')
    subprocess.run(['java', str(ROOT / 'tools/CompileJava.java'), str(args)], check=True)
    if sha(PROBE) != probe_hash:
        raise RuntimeError('Probe changed during compilation')
    (classes / 'plugin.yml').write_text("name: StructureEnchantCapChecks\nversion: '1'\nmain: StructureEnchantCapChecks\n"
                                        "api-version: '26.2'\ndepend: [Evergarden, advance-magic]\n", encoding='utf8')
    subprocess.run(['jar', '--create', '--file', str(plugins / 'structure-caps-probe.jar'), '-C', str(classes), '.'], check=True)

    runs = {}
    shutil.copy2(baseline, plugins / 'evergarden.jar')
    shutil.copy2(pristine, data)
    runs['baseline'] = run_server(server)
    shutil.copy2(candidate, plugins / 'evergarden.jar')
    shutil.copy2(pristine, data)
    runs['candidate'] = run_server(server)
    upgraded = data.read_text(encoding='utf8')
    (OUT / 'upgraded-boss-treasure.yml').write_text(upgraded, encoding='utf8')
    # Edited caps must be honoured: Valkyrie gets main +2, others +1.
    edited = re.sub(r'(seraphon-valkyrie:\s*\n\s*main:\s*)\d+(\s*\n\s*others:\s*)\d+', r'\g<1>2\g<2>1', upgraded)
    if edited == upgraded:
        raise RuntimeError('Could not edit the Valkyrie caps in the upgraded file')
    data.write_text(edited, encoding='utf8')
    shutil.copy2(server / 'structure-caps-fingerprint-candidate.txt', OUT / 'fingerprint-candidate-default.txt')
    runs['edited'] = run_server(server)
    shutil.copy2(pristine, data)
    same = ((server / 'structure-caps-fingerprint-baseline.txt').read_bytes()
            == (OUT / 'fingerprint-candidate-default.txt').read_bytes()
            == (server / 'structure-caps-fingerprint-candidate.txt').read_bytes())
    for name, text in runs.items():
        (OUT / f'result-{name}.txt').write_text(text, encoding='utf8')
        print(f'--- {name}\n{text}')
    ok = runs['baseline'].startswith('PASS baseline') and runs['candidate'].startswith('PASS candidate') \
        and runs['edited'].startswith('PASS candidate') and same
    receipt = {'garden_sha256': sha(candidate), 'baseline_sha256': sha(baseline), 'magic_sha256': sha(magic),
               'probe_sha256': probe_hash, 'fingerprints_identical': same,
               'report': ('PASS ' if ok else 'FAIL ') + ' | '.join(t.splitlines()[0] for t in runs.values())}
    (OUT / RECEIPT).write_text(json.dumps(receipt, indent=2) + '\n', encoding='utf8')
    print(json.dumps(receipt, indent=2))
    if not ok:
        raise SystemExit(1)


def promote():
    report = json.loads((OUT / 'build-report.json').read_text(encoding='utf8'))
    receipt = json.loads((OUT / RECEIPT).read_text(encoding='utf8'))
    garden, magic, candidate = ROOT / 'dist/evergarden.jar', ROOT / 'dist/advance-magic.jar', OUT / 'evergarden.jar'
    for path, digest in report['sources'].items():
        if sha(ROOT / path) != digest:
            raise RuntimeError('Source changed after validation: ' + path)
    if sha(garden) != report['baseline_sha256']:
        raise RuntimeError('dist/evergarden.jar is no longer the validated baseline')
    if sha(candidate) != report['candidate_sha256'] or receipt['garden_sha256'] != sha(candidate):
        raise RuntimeError('Candidate differs from Paper validation')
    if sha(magic) != report['magic_sha256'] or receipt['magic_sha256'] != sha(magic):
        raise RuntimeError('Advance Magic differs from Paper validation')
    if receipt['probe_sha256'] != sha(PROBE) or not receipt['report'].startswith('PASS '):
        raise RuntimeError('Missing matching successful validation')
    plugin = ROOT / 'evergarden/src/main/resources/plugin.yml'
    plugin.write_bytes(version(plugin.read_bytes()))
    shutil.copy2(candidate, ROOT / f'dist/evergarden-{VERSION}.jar')
    staged = garden.with_suffix('.jar.staged')
    shutil.copy2(candidate, staged)
    os.replace(staged, garden)
    if sha(garden) != report['candidate_sha256']:
        raise RuntimeError('Promoted JAR differs from the candidate')
    report['promoted'] = True
    report['validation'] = receipt
    (OUT / 'build-report.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf8')
    print('Promoted ' + VERSION + ' ' + sha(garden))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--validate', action='store_true')
    parser.add_argument('--promote', action='store_true')
    options = parser.parse_args()
    OUT.mkdir(parents=True, exist_ok=True)
    promote() if options.promote else validate() if options.validate else candidate_build()
