"""Exercise validated pack-delivery JARs in fresh local Paper servers; no deployment."""
import argparse
import os
from pathlib import Path
import shutil
import subprocess
import uuid

ROOT = Path(__file__).resolve().parents[2]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--build-directory', type=Path, required=True,
                    help='Successful output directory from run_checks.py')
parser.add_argument('--source-server', type=Path, required=True, help='Cached Paper server files')
parser.add_argument('--server-directory', type=Path)
parser.add_argument('--timeout', type=int, default=240)
args = parser.parse_args()
build = args.build_directory.resolve()
assert (build / 'result.txt').read_text(encoding='utf8').startswith('PASS'), 'Delivery checks have not passed'
old = [line.strip().strip('"') for line in (build / 'check-classes-javac.args').read_text(encoding='utf8').splitlines()]
classpath = old[old.index('-cp') + 1]
classes = build / 'paper-check-classes'
classes.mkdir(exist_ok=True)
options = ['--release', '25', '-proc:none', '-encoding', 'UTF-8', '-cp', classpath,
           '-d', str(classes), str(Path(__file__).with_name('ResourcePackPaperChecks.java'))]
argfile = build / 'paper-check-javac.args'
argfile.write_text('\n'.join('"' + value.replace('\\', '/') + '"' for value in options), encoding='utf8')
subprocess.run(['javac', '@' + str(argfile)], check=True)
(classes / 'plugin.yml').write_text(
    "name: ResourcePackPaperChecks\nversion: '1.0'\nmain: ResourcePackPaperChecks\napi-version: '26.2'\n"
    "depend: [Evergarden]\nsoftdepend: [advance-magic]\n", encoding='utf8')
probe = build / 'resource-pack-paper-checks.jar'
subprocess.run(['jar', '--create', '--file', str(probe), '-C', str(classes), '.'], check=True)
parent = (args.server_directory or ROOT / 'output' / ('pack-paper-' + uuid.uuid4().hex[:8])).resolve()
parent.mkdir(parents=True, exist_ok=False)
results = []
for mode in ('together', 'standalone'):
    server = parent / mode
    server.mkdir()
    for name in ('libraries', 'versions', 'cache', 'config'):
        source = args.source_server / name
        if source.is_dir():
            shutil.copytree(source, server / name)
    shutil.copyfile(args.source_server / 'paper.jar', server / 'paper.jar')
    plugins = server / 'plugins'
    plugins.mkdir()
    shutil.copyfile(probe, plugins / probe.name)
    for module, folder in [('evergarden', 'Evergarden'), *([('advance-magic', 'advance-magic')] if mode == 'together' else [])]:
        shutil.copyfile(build / (module + '.jar'), plugins / (module + '.jar'))
        config = plugins / folder
        config.mkdir()
        (config / 'config.yml').write_text(
            'config-version: 2\nresource-pack:\n  enabled: true\n  required: false\n  url: ""\n  sha1: ""\n'
            '  host:\n    enabled: true\n    bind: 127.0.0.1\n    port: 0\n    public-host: 127.0.0.1\n'
            '  geyser:\n    auto-install: false\n'
            'world-boss:\n  visuals:\n    pack:\n      enabled: true\n      url: ""\n'
            '      host:\n        bind: 127.0.0.1\n        port: 0\n        public-host: 127.0.0.1\n', encoding='utf8')
    (server / 'eula.txt').write_text('eula=true\n', encoding='ascii')
    (server / 'server.properties').write_text(
        'server-ip=127.0.0.1\nserver-port=0\nonline-mode=false\nenforce-secure-profile=false\n'
        'level-name=pack_probe\nlevel-type=minecraft:flat\ngenerate-structures=false\n'
        'view-distance=2\nsimulation-distance=2\nspawn-protection=0\n'
        'pause-when-empty-seconds=-1\nmax-tick-time=240000\n', encoding='ascii')
    print('Starting isolated Paper:', server, flush=True)
    with (server / 'console.log').open('w', encoding='utf8') as log:
        process = subprocess.Popen(['java', '-Xms384M', '-Xmx2G', '-Dterminal.jline=false', '-Dterminal.ansi=false',
                                    '-jar', 'paper.jar', '--nogui', '--noconsole'], cwd=server,
                                   stdout=log, stderr=subprocess.STDOUT,
                                   creationflags=subprocess.CREATE_NO_WINDOW if os.name == 'nt' else 0)
        try:
            process.wait(timeout=args.timeout)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait()
            raise RuntimeError('Paper checks timed out: ' + str(server / 'console.log'))
    report = server / 'resource-pack-result.txt'
    if not report.exists():
        raise RuntimeError('Paper probe did not complete: ' + str(server / 'console.log'))
    result = report.read_text(encoding='utf8')
    print(mode + ': ' + result, flush=True)
    assert result.startswith('PASS'), result
    results.append(mode + ': ' + result)
(build / 'paper-result.txt').write_text('\n'.join(results), encoding='utf8')
print('Both Paper deployment modes passed', flush=True)
