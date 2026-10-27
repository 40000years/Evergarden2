"""Compile/run a real Paper 26.2 wand regression probe in a fresh disposable server.

Example: python tests/wand-security/run.py --name baseline --magic dist/advance-magic.jar --garden dist/evergarden.jar
No network client/hardware or deployed player server is used. Production JARs are copied verbatim.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--name', required=True)
    parser.add_argument('--magic', default='dist/advance-magic.jar')
    parser.add_argument('--garden', default='dist/evergarden.jar')
    parser.add_argument('--template', default='.audit-plugins/vault-security-fixed-20261003')
    parser.add_argument('--timeout', type=int, default=240)
    args = parser.parse_args()
    if not args.name.replace('-', '').replace('_', '').isalnum():
        parser.error('--name must contain only letters, numbers, hyphens or underscores')
    template = ROOT / args.template
    server = ROOT / '.audit-plugins' / ('wand-security-' + args.name)
    if server.exists():
        parser.error(f'Disposable target already exists: {server}; choose a new --name')
    server.mkdir(parents=True)
    for folder in ('libraries', 'versions', 'cache'):
        source = template / folder
        if source.exists():
            shutil.copytree(source, server / folder)
    shutil.copy2(template / 'paper.jar', server / 'paper.jar')
    (server / 'eula.txt').write_text('eula=true\n', encoding='ascii')
    (server / 'server.properties').write_text('server-ip=127.0.0.1\nserver-port=0\nonline-mode=false\nenforce-secure-profile=false\nlevel-name=probe_base\nlevel-type=minecraft:flat\ngenerate-structures=false\nview-distance=2\nsimulation-distance=2\nspawn-protection=0\nmax-tick-time=240000\npause-when-empty-seconds=-1\n', encoding='ascii')
    plugins = server / 'plugins'
    plugins.mkdir()
    magic = ROOT / args.magic
    garden = ROOT / args.garden
    shutil.copy2(magic, plugins / 'advance-magic.jar')
    shutil.copy2(garden, plugins / 'evergarden.jar')
    (server / 'probe-inputs.json').write_text(json.dumps({
        'magic': {'source': str(magic), 'sha256': hashlib.sha256((plugins / 'advance-magic.jar').read_bytes()).hexdigest()},
        'garden': {'source': str(garden), 'sha256': hashlib.sha256((plugins / 'evergarden.jar').read_bytes()).hexdigest()},
        'probe_sha256': hashlib.sha256(Path(__file__).with_name('WandSecurityProbe.java').read_bytes()).hexdigest(),
        'paper': '26.2',
        'scope': 'Disposable server actor and Java-server packet transactions; no Bedrock client or production player data.'
    }, indent=2), encoding='utf8')
    for name in ('advance-magic', 'Evergarden'):
        config = plugins / name
        config.mkdir()
        (config / 'config.yml').write_text('resource-pack:\n  enabled: false\n  host:\n    enabled: false\n', encoding='ascii')
    repository = Path.home() / '.m2' / 'repository'
    gson = sorted((repository / 'com/google/code/gson/gson').glob('*/gson-*.jar'), key=lambda p: tuple(map(int, p.parent.name.split('.'))), reverse=True)
    deps = [*gson, *(p for p in repository.rglob('*.jar') if p not in gson), *(server / 'libraries').rglob('*.jar'), server / 'versions/26.2/paper-26.2.jar', magic, garden]
    classes = server / 'probe-classes'
    classes.mkdir()
    compiler_args = ['--release', '25', '-proc:none', '-encoding', 'UTF-8', '-cp', os.pathsep.join(map(str, deps)), '-d', str(classes), str(Path(__file__).with_name('WandSecurityProbe.java'))]
    argfile = server / 'probe-javac.args'
    argfile.write_text('\n'.join('"' + str(v).replace('\\', '/') + '"' for v in compiler_args), encoding='utf8')
    subprocess.run(['javac', '@' + str(argfile)], check=True)
    shutil.copy2(Path(__file__).with_name('plugin.yml'), classes / 'plugin.yml')
    subprocess.run(['jar', '--create', '--file', str(plugins / 'wand-security-probe.jar'), '-C', str(classes), '.'], check=True)
    print(f'Probe server: {server}', flush=True)
    with (server / 'console.log').open('w', encoding='utf8') as log:
        process = subprocess.Popen(['java', '-Xms512M', '-Xmx1536M', '-jar', 'paper.jar', '--nogui'], cwd=server, stdin=subprocess.PIPE, stdout=log, stderr=subprocess.STDOUT, text=True,
                                   creationflags=subprocess.CREATE_NO_WINDOW if os.name == 'nt' else 0)
        try:
            process.wait(timeout=args.timeout)
        except subprocess.TimeoutExpired:
            process.stdin.write('stop\n')
            process.stdin.flush()
            try:
                process.wait(timeout=30)
            except subprocess.TimeoutExpired:
                process.kill()
            raise RuntimeError(f'Probe timed out; inspect {server / "console.log"}')
    result = server / 'wand-security-result.txt'
    if not result.exists():
        print((server / 'console.log').read_text(encoding='utf8')[-10000:])
        raise RuntimeError('Paper probe did not write a result')
    summary = result.read_text(encoding='utf8')
    print(summary)
    return 0 if summary.startswith('PASS') else 1


if __name__ == '__main__':
    sys.exit(main())
