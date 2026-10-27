"""Exercise Vault economy and actual survival anvils on isolated cached Paper.

--prepared refreshes the owned probe and candidate jars in a disposable output/
server after a failed check. It never starts or modifies the source server.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'output/vault-economy-20261026'
PROBE = ROOT / 'evergarden/tests/VaultEconomyChecks.java'
RESULT = 'vault-economy-result.txt'
RECEIPT = 'vault-economy-validation.json'


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def prepared(args, server):
    for name in ('server-launch.args', 'probe-javac.args', 'paper.jar'):
        if not (server / name).is_file():
            raise RuntimeError('Missing prepared Paper file: ' + str(server / name))
    for name in (RESULT, RECEIPT, 'vault-weapon-validation.json'):
        (server / name).unlink(missing_ok=True)
    for origin, target in ((args.garden_jar, 'evergarden.jar'), (args.magic_jar, 'advance-magic.jar')):
        shutil.copy2(origin, server / 'plugins' / target)
    options = (server / 'probe-javac.args').read_text(encoding='utf8').splitlines()
    index = options.index('"-cp"') + 1
    dependencies = options[index].strip('"').split(os.pathsep)
    dependencies[:2] = [args.garden_jar.resolve().as_posix(), args.magic_jar.resolve().as_posix()]
    options[index] = '"' + os.pathsep.join(dependencies) + '"'
    options[-1] = '"' + args.probe_source.as_posix() + '"'
    (server / 'probe-javac.args').write_text('\n'.join(options), encoding='utf8')
    subprocess.run(['java', str(ROOT / 'tools/CompileJava.java'), str(server / 'probe-javac.args')], check=True)
    (server / 'probe-classes/plugin.yml').write_text(
        "name: VaultEconomyChecks\nversion: '1.0'\nmain: VaultEconomyChecks\napi-version: '26.2'\n"
        'depend: [advance-magic, Evergarden]\n', encoding='utf8')
    subprocess.run(['jar', '--create', '--file', str(server / 'plugins/vault-weapon-checks.jar'),
                    '-C', str(server / 'probe-classes'), '.'], check=True)
    with (server / 'console.log').open('w', encoding='utf8') as log:
        process = subprocess.Popen(['java', '@' + str(server / 'server-launch.args')], cwd=server,
                                   stdout=log, stderr=subprocess.STDOUT,
                                   creationflags=subprocess.CREATE_NO_WINDOW if os.name == 'nt' else 0)
        try:
            process.wait(timeout=args.timeout)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait()
            raise RuntimeError('Vault economy probe timed out: ' + str(server / 'console.log'))


def fresh(args, server):
    harness = ROOT / 'evergarden/tests/run_vault_weapon_checks.py'
    sys.argv = [str(harness), '--source-server', str(args.source_server), '--server', str(server),
                '--magic-jar', str(args.magic_jar), '--garden-jar', str(args.garden_jar),
                '--probe-source', str(args.probe_source), '--result-name', RESULT, '--timeout', str(args.timeout)]
    try:
        exec(compile(harness.read_text(encoding='utf8'), str(harness), 'exec'),
             {'__name__': '__main__', '__file__': str(harness)})
    except SystemExit as error:
        if error.code not in (None, 0):
            raise


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--source-server', type=Path, default=ROOT / 'output/dungeon-balance-20261026/paper')
    parser.add_argument('--server', type=Path, default=OUT / 'paper')
    parser.add_argument('--garden-jar', type=Path, default=OUT / 'build/evergarden.jar')
    parser.add_argument('--magic-jar', type=Path,
                        default=ROOT / 'dist/advance-magic-1.34.1-bedrock-pack-repair.jar')
    parser.add_argument('--prepared', action='store_true')
    parser.add_argument('--timeout', type=int, default=360)
    args = parser.parse_args()
    server = args.server.resolve()
    if not server.is_relative_to((ROOT / 'output').resolve()):
        parser.error('--server must stay under workspace output/')
    if server == args.source_server.resolve():
        parser.error('The cached source server and disposable server must differ')
    for source in (PROBE, args.garden_jar, args.magic_jar):
        if not source.is_file():
            parser.error('Missing input: ' + str(source))
    inputs = {'garden_sha256': sha(args.garden_jar), 'magic_sha256': sha(args.magic_jar), 'probe_sha256': sha(PROBE)}
    args.probe_source = server.parent / 'probe-source/VaultEconomyChecks.java'
    args.probe_source.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(PROBE, args.probe_source)
    if args.prepared:
        prepared(args, server)
    else:
        fresh(args, server)
    result = server / RESULT
    if not result.is_file():
        raise RuntimeError('Probe did not finish: ' + str(server / 'console.log'))
    report = result.read_text(encoding='utf8').splitlines()[0]
    console = (server / 'console.log').read_text(encoding='utf8', errors='replace')
    for failure in ('Could not pass event', 'Error occurred while enabling advance-magic',
                    'Error occurred while enabling Evergarden', 'Error occurred while enabling VaultEconomyChecks'):
        if failure in console:
            raise RuntimeError('Runtime failure (' + failure + '): ' + str(server / 'console.log'))
    if not report.startswith('PASS '):
        raise RuntimeError(report)
    receipt = {'garden_sha256': sha(server / 'plugins/evergarden.jar'),
               'magic_sha256': sha(server / 'plugins/advance-magic.jar'),
               'probe_sha256': sha(args.probe_source), 'report': report}
    if any(receipt[key] != digest for key, digest in inputs.items()) or sha(PROBE) != inputs['probe_sha256']:
        raise RuntimeError('Probe or plugin inputs changed while Paper ran; rerun against frozen inputs')
    (server / RECEIPT).write_text(json.dumps(receipt, indent=2) + '\n', encoding='utf8')
    print(report, flush=True)
    print('Release receipt:', server / RECEIPT, flush=True)


if __name__ == '__main__':
    main()
