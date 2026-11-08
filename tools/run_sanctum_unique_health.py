"""Run evergarden/tests/SanctumUniqueHealthChecks.java on an isolated Paper copy of the craft-check fixture.

    python tools/run_sanctum_unique_health.py --name before --garden dist/evergarden.jar --forge dist/evergarden-forge.jar

Each --name gets a fresh server under output/sanctum-unique-health/<name>/ (the fixture is never modified).
Prints the probe result and writes <name>/receipt.json with the JAR and probe hashes.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import time

ROOT = Path(__file__).resolve().parents[1]
FIXTURE = ROOT / 'output/craft-check-paper231'
PROBE = ROOT / 'evergarden/tests/SanctumUniqueHealthChecks.java'
RESULT = 'sanctum-unique-result.txt'


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--name', required=True)
    parser.add_argument('--garden', type=Path, default=ROOT / 'dist/evergarden.jar')
    parser.add_argument('--forge', type=Path, default=ROOT / 'dist/evergarden-forge.jar')
    parser.add_argument('--magic', type=Path, default=ROOT / 'dist/advance-magic.jar')
    parser.add_argument('--timeout', type=int, default=420)
    args = parser.parse_args()
    if not args.name.replace('-', '').replace('_', '').isalnum():
        parser.error('--name: letters, numbers, - and _ only')
    out = ROOT / 'output/sanctum-unique-health' / args.name
    if out.exists():
        parser.error(f'{out} exists; use a fresh --name')
    server = out / 'paper'
    server.mkdir(parents=True)
    for name in ('paper.jar', 'libraries', 'versions', 'cache', 'config', 'bukkit.yml', 'spigot.yml', 'commands.yml',
                 'server.properties', 'eula.txt', 'model_engine_trial'):
        source = FIXTURE / name
        if source.exists():
            (shutil.copytree if source.is_dir() else shutil.copy2)(source, server / name)
    (server / 'model_engine_trial/session.lock').unlink(missing_ok=True)
    plugins = server / 'plugins'
    plugins.mkdir()
    for jar in FIXTURE.glob('plugins/*.jar'):
        if 'probe' not in jar.name:
            shutil.copy2(jar, plugins / jar.name)
    for folder in (FIXTURE / 'plugins').iterdir():
        if folder.is_dir():
            shutil.copytree(folder, plugins / folder.name)
    inputs = {'garden': args.garden.resolve(), 'forge': args.forge.resolve(), 'magic': args.magic.resolve()}
    shutil.copy2(inputs['magic'], plugins / 'advance-magic.jar')
    shutil.copy2(inputs['forge'], plugins / 'evergarden-forge.jar')
    shutil.copy2(inputs['garden'], plugins / 'evergarden.jar')
    classes = out / 'probe-classes'
    classes.mkdir()
    classpath = os.pathsep.join(str(p) for p in [inputs['garden'], inputs['magic'], *(server / 'libraries').rglob('*.jar'),
                                                  *(server / 'versions').rglob('*.jar')])
    argfile = out / 'probe-javac.args'
    argfile.write_text('\n'.join('"' + v.replace('\\', '/') + '"' for v in
                                 ['--release', '25', '-proc:none', '-encoding', 'UTF-8', '-cp', classpath, '-d', str(classes), str(PROBE)]),
                       encoding='utf8')
    subprocess.run(['java', str(ROOT / 'tools/CompileJava.java'), str(argfile)], check=True)
    (classes / 'plugin.yml').write_text("name: SanctumUniqueHealthChecks\nversion: '1'\nmain: SanctumUniqueHealthChecks\n"
                                        "api-version: '26.2'\ndepend: [Evergarden, advance-magic, EvergardenForge]\n", encoding='utf8')
    subprocess.run(['jar', '--create', '--file', str(plugins / 'sanctum-unique-probe.jar'), '-C', str(classes), '.'], check=True)
    log = server / 'console.log'
    with log.open('w', encoding='utf8') as console:
        process = subprocess.Popen(['java', '-Xms256M', '-Xmx3072M', '-Dterminal.jline=false', '-Dterminal.ansi=false',
                                    '-jar', 'paper.jar', '--nogui', '--noconsole'], cwd=server, stdout=console,
                                   stderr=subprocess.STDOUT, creationflags=subprocess.CREATE_NO_WINDOW if os.name == 'nt' else 0)
        try:
            process.wait(timeout=args.timeout)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait()
            raise SystemExit('Paper timed out: ' + str(log))
    result = server / RESULT
    text = result.read_text(encoding='utf8') if result.exists() else 'FAIL no probe result; see ' + str(log)
    errors = [line for line in log.read_text(encoding='utf8', errors='replace').splitlines() if 'Error occurred while enabling' in line]
    receipt = {key + '_sha256': sha(path) for key, path in inputs.items()}
    receipt.update(probe_sha256=sha(PROBE), report=text.splitlines()[0], enable_errors=errors)
    (out / 'receipt.json').write_text(json.dumps(receipt, indent=2) + '\n', encoding='utf8')
    print(text.encode('ascii', 'replace').decode())
    if errors:
        print('\n'.join(errors))
    raise SystemExit(0 if text.startswith('PASS') and not errors else 1)


if __name__ == '__main__':
    main()
