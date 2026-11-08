"""Add the EvergardenForge item page (gems, gem dust, salvage materials) to /evergarden test, over e2.179.

    python tools/build_forge_test_menu_release.py              build the candidate
    python tools/build_forge_test_menu_release.py --validate   isolated Paper checks (with EvergardenForge + LevelledMobs)
    python tools/build_forge_test_menu_release.py --promote    replace dist/evergarden.jar

Only AdminTestGui (and its nested classes) and plugin.yml change; every other JAR entry, including the
resource packs, stays byte-identical to the baseline. Evergarden does not compile against the forge: the
page reaches com.example.forge.ForgeApi by reflection and shows a message when the forge is absent.
Validation runs evergarden/tests/ForgeTestMenuChecks.java on a copy of the craft-check Paper fixture with
the candidate, dist/advance-magic.jar and dist/evergarden-forge.jar.
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
OUT = ROOT / 'output/forge-test-menu'
VERSION = '3.0.0-e2.180-forge-test-menu'
BASE_VERSION = '3.0.0-e2.179-mount-refinement'
FAMILIES = ('com/example/voidscape/gui/AdminTestGui',)
PROBE = ROOT / 'evergarden/tests/ForgeTestMenuChecks.java'
FIXTURE_SOURCE = ROOT / 'output/craft-check-paper231'
RECEIPT = 'forge-test-menu-validation.json'


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def owned(name):
    return any(name == f + '.class' or name.startswith(f + '$') for f in FAMILIES)


def version(data):
    text, count = re.subn(r'(?m)^version:[^\r\n]*', "version: '" + VERSION + "'", data.decode('utf8'))
    if count != 1:
        raise RuntimeError('Expected one plugin version')
    return text.encode('utf8')


def candidate_build():
    garden, magic = ROOT / 'dist/evergarden.jar', ROOT / 'dist/advance-magic.jar'
    candidate, baseline = OUT / 'evergarden.jar', OUT / 'baseline-evergarden.jar'
    if not baseline.exists():
        if sha(garden) != sha(ROOT / f'dist/evergarden-{BASE_VERSION}.jar'):
            raise RuntimeError('dist/evergarden.jar is not ' + BASE_VERSION)
        shutil.copy2(garden, baseline)
    if sha(garden) != sha(baseline):
        raise RuntimeError('Delivery baseline changed; use a new release builder')
    sources = [ROOT / 'evergarden/src/main/java' / (f + '.java') for f in FAMILIES]
    hashes = {p.relative_to(ROOT).as_posix(): sha(p) for p in sources}
    build.OUT = OUT
    classes = OUT / 'classes'
    shutil.rmtree(classes, ignore_errors=True)
    build.compile_sources('forge-test-menu', sources, [str(baseline), str(magic)] + build.pinned_classpath(build.EVERGARDEN_ARGS), classes)
    if any(sha(ROOT / p) != d for p, d in hashes.items()):
        raise RuntimeError('Sources changed during compilation')
    compiled = {p.relative_to(classes).as_posix(): p.read_bytes() for p in classes.rglob('*.class')}
    if not compiled or any(not owned(n) for n in compiled) or any(f + '.class' not in compiled for f in FAMILIES):
        raise RuntimeError('Unexpected compiler output: ' + ', '.join(sorted(compiled)))
    date = (2026, 11, 1, 0, 0, 0)
    with zipfile.ZipFile(baseline) as old, zipfile.ZipFile(candidate, 'w', zipfile.ZIP_DEFLATED) as new:
        for entry in old.infolist():
            if not owned(entry.filename) and entry.filename != 'plugin.yml':
                new.writestr(entry, old.read(entry.filename))
        replaced = {'plugin.yml': version(old.read('plugin.yml')), **compiled}
        for name, data in replaced.items():
            new.writestr(zipfile.ZipInfo(name, date), data, compress_type=zipfile.ZIP_DEFLATED)
    with zipfile.ZipFile(baseline) as old, zipfile.ZipFile(candidate) as new:
        if new.testzip() or len(new.namelist()) != len(set(new.namelist())):
            raise RuntimeError('Invalid candidate archive')
        preserved = [n for n in old.namelist() if not owned(n) and n != 'plugin.yml']
        if any(old.read(n) != new.read(n) for n in preserved):
            raise RuntimeError('Unrelated JAR entry changed')
        removed = [n for n in old.namelist() if owned(n) and n not in compiled]
        if removed:
            raise RuntimeError('Owned classes disappeared: ' + ', '.join(removed))
    build.check_jar(candidate)
    report = {'version': VERSION, 'sources': hashes, 'baseline_sha256': sha(baseline),
              'candidate_sha256': sha(candidate), 'magic_sha256': sha(magic),
              'preserved_entries': len(preserved), 'replaced': sorted(replaced), 'promoted': False}
    (OUT / 'build-report.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf8')
    print(json.dumps(report, indent=2))


def run_server(server, timeout=420):
    (server / 'forge-test-menu-result.txt').unlink(missing_ok=True)
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
    result = server / 'forge-test-menu-result.txt'
    if not result.exists():
        raise RuntimeError('No probe result; see ' + str(log))
    return result.read_text(encoding='utf8'), log


def validate():
    report = json.loads((OUT / 'build-report.json').read_text(encoding='utf8'))
    candidate, magic, forge = OUT / 'evergarden.jar', ROOT / 'dist/advance-magic.jar', ROOT / 'dist/evergarden-forge.jar'
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
    shutil.copy2(magic, plugins / 'advance-magic.jar')
    shutil.copy2(forge, plugins / 'evergarden-forge.jar')
    shutil.copy2(candidate, plugins / 'evergarden.jar')
    classes = OUT / 'probe-classes'
    shutil.rmtree(classes, ignore_errors=True)
    classes.mkdir()
    classpath = os.pathsep.join(str(p) for p in [candidate, magic, *(server / 'libraries').rglob('*.jar'), *(server / 'versions').rglob('*.jar')])
    probe_hash = sha(PROBE)
    args = OUT / 'probe-javac.args'
    args.write_text('\n'.join('"' + v.replace('\\', '/') + '"' for v in
                              ['--release', '25', '-proc:none', '-encoding', 'UTF-8', '-cp', classpath, '-d', str(classes), str(PROBE)]),
                    encoding='utf8')
    subprocess.run(['java', str(ROOT / 'tools/CompileJava.java'), str(args)], check=True)
    if sha(PROBE) != probe_hash:
        raise RuntimeError('Probe changed during compilation')
    (classes / 'plugin.yml').write_text("name: ForgeTestMenuChecks\nversion: '1'\nmain: ForgeTestMenuChecks\n"
                                        "api-version: '26.2'\ndepend: [Evergarden, advance-magic, EvergardenForge]\n", encoding='utf8')
    subprocess.run(['jar', '--create', '--file', str(plugins / 'forge-test-menu-probe.jar'), '-C', str(classes), '.'], check=True)
    text, log = run_server(server)
    console = log.read_text(encoding='utf8', errors='replace')
    errors = [line for line in console.splitlines() if 'Error occurred while enabling' in line]
    ok = text.startswith('PASS') and not errors
    (OUT / 'result.txt').write_text(text, encoding='utf8')
    print(text)
    if errors:
        print('\n'.join(errors))
    receipt = {'garden_sha256': sha(candidate), 'magic_sha256': sha(magic), 'forge_sha256': sha(forge),
               'probe_sha256': probe_hash, 'report': ('PASS ' if ok else 'FAIL ') + text.splitlines()[0]}
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
