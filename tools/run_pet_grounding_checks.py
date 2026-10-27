"""Run removed-footing and model-click regressions on isolated cached Paper.

Reuses the maintained Vault probe's cache/bootstrap setup, enables the pet Java
pack only inside the disposable server, and emits a hash-bound release receipt.
Use --prepared to retry an existing disposable server after a probe/candidate fix.
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

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'output/pet-grounding-20261026-v3'
PROBE = ROOT / 'evergarden/tests/PetGroundingChecks.java'
RESULT = 'pet-grounding-result.txt'
RECEIPT = 'pet-grounding-validation.json'


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def enable_pet_pack(server):
    config = server / 'plugins/Evergarden/config.yml'
    value = config.read_text(encoding='utf8')
    stack, output, found = [], [], False
    for line in value.splitlines(keepends=True):
        match = re.match(r'^( *)([\w-]+):(?:\s*(.*))?$', line.rstrip('\r\n'))
        if match:
            indent, key = len(match[1]), match[2]
            while stack and stack[-1][0] >= indent:
                stack.pop()
            if [name for _, name in stack] == ['world-boss', 'visuals', 'pack'] and key == 'enabled':
                line = match[1] + 'enabled: true\n'
                found = True
            if not (match[3] or '').strip():
                stack.append((indent, key))
        output.append(line)
    if not found:
        raise RuntimeError('Unexpected disposable pack configuration: ' + str(config))
    config.write_text(''.join(output), encoding='utf8')


def prepared(args, server):
    for name in ('server-launch.args', 'probe-javac.args', 'paper.jar'):
        if not (server / name).is_file():
            raise RuntimeError('Missing prepared Paper file: ' + str(server / name))
    for name in (RESULT, RECEIPT, 'vault-weapon-validation.json'):
        (server / name).unlink(missing_ok=True)
    for origin, target in ((args.garden_jar, 'evergarden.jar'), (args.magic_jar, 'advance-magic.jar')):
        shutil.copy2(origin, server / 'plugins' / target)
    enable_pet_pack(server)
    # Reuse the exact cached dependency resolution while refreshing owned inputs.
    options = (server / 'probe-javac.args').read_text(encoding='utf8').splitlines()
    index = options.index('"-cp"') + 1
    dependency_paths = options[index].strip('"').split(os.pathsep)
    dependency_paths[:2] = [args.garden_jar.resolve().as_posix(), args.magic_jar.resolve().as_posix()]
    options[index] = '"' + os.pathsep.join(dependency_paths) + '"'
    options[-1] = '"' + args.probe_source.as_posix() + '"'
    (server / 'probe-javac.args').write_text('\n'.join(options), encoding='utf8')
    subprocess.run(['java', str(ROOT / 'tools/CompileJava.java'), str(server / 'probe-javac.args')], check=True)
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
            raise RuntimeError('Pet grounding probe timed out: ' + str(server / 'console.log'))


def fresh(args, server):
    harness = ROOT / 'evergarden/tests/run_vault_weapon_checks.py'
    script = harness.read_text(encoding='utf8')
    # PetBody.spawn requires NativePetRenderer.ready(), which verifies the pack.
    # Keep the generic reward harness's no-pack default unchanged for other tests.
    marker = 'world-boss:\\n  visuals:\\n    pack:\\n      enabled: false'
    if script.count(marker) != 1:
        raise RuntimeError('Vault harness pack setup changed; review the pet wrapper')
    script = script.replace(marker, marker.replace('false', 'true'))
    sys.argv = [str(harness), '--source-server', str(args.source_server), '--server', str(server),
                '--magic-jar', str(args.magic_jar), '--garden-jar', str(args.garden_jar),
                '--probe-source', str(args.probe_source), '--result-name', RESULT, '--timeout', str(args.timeout)]
    try:
        exec(compile(script, str(harness), 'exec'), {'__name__': '__main__', '__file__': str(harness)})
    except SystemExit as error:
        if error.code not in (None, 0):
            raise


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--source-server', type=Path, default=ROOT / 'output/dungeon-balance-20261026/paper')
    parser.add_argument('--server', type=Path, default=OUT / 'paper')
    parser.add_argument('--garden-jar', type=Path, default=OUT / 'build/evergarden.jar')
    parser.add_argument('--magic-jar', type=Path, default=ROOT / 'dist/advance-magic.jar')
    parser.add_argument('--prepared', action='store_true')
    parser.add_argument('--timeout', type=int, default=360)
    args = parser.parse_args()
    server = args.server.resolve()
    if not server.is_relative_to((ROOT / 'output').resolve()):
        parser.error('--server must stay under workspace output/')
    for source in (PROBE, args.garden_jar, args.magic_jar):
        if not source.is_file():
            parser.error('Missing input: ' + str(source))
    inputs = {'garden_sha256': sha(args.garden_jar), 'magic_sha256': sha(args.magic_jar), 'probe_sha256': sha(PROBE)}
    args.probe_source = server.parent / 'probe-source/PetGroundingChecks.java'
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
                    'Error occurred while enabling Evergarden', 'Cannot update wild pet', 'Cannot update companion'):
        if failure in console:
            raise RuntimeError('Runtime failure (' + failure + '): ' + str(server / 'console.log'))
    if not report.startswith('PASS '):
        raise RuntimeError(report)
    receipt = {'garden_sha256': sha(server / 'plugins/evergarden.jar'),
               'magic_sha256': sha(server / 'plugins/advance-magic.jar'),
               'probe_sha256': sha(args.probe_source), 'report': report}
    if any(receipt[key] != digest for key, digest in inputs.items()) or sha(PROBE) != inputs['probe_sha256']:
        raise RuntimeError('Probe or plugin inputs changed while the server ran; rerun against frozen inputs')
    (server / RECEIPT).write_text(json.dumps(receipt, indent=2) + '\n', encoding='utf8')
    print(report, flush=True)
    print('Release receipt:', server / RECEIPT, flush=True)


if __name__ == '__main__':
    main()
