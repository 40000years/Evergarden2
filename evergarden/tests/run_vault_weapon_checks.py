"""Check exact modeled weapon rewards on an isolated cached, localhost-only Paper server."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import uuid
import zipfile

ROOT = Path(__file__).resolve().parents[2]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--source-server', type=Path, required=True)
parser.add_argument('--server', type=Path)
parser.add_argument('--magic-jar', type=Path, required=True, help='Prebuilt Advance Magic')
parser.add_argument('--garden-jar', type=Path, required=True)
parser.add_argument('--probe-source', type=Path, default=Path(__file__).with_name('VaultWeaponChecks.java'))
parser.add_argument('--result-name', default='vault-weapon-result.txt')
parser.add_argument('--disabled-magic', action='store_true',
                    help='Load both plugins, then disable Advance Magic before testing Rare fallback')
parser.add_argument('--timeout', type=int, default=300)
args = parser.parse_args()
source = args.source_server.resolve()
server = (args.server or ROOT / 'output' / ('vault-weapons-' + uuid.uuid4().hex)).resolve()
magic_source, garden_source = args.magic_jar.resolve(), args.garden_jar.resolve()
for required in (magic_source, garden_source, source / 'paper.jar'):
    if not required.is_file():
        parser.error('Missing input: ' + str(required))
if not server.is_relative_to((ROOT / 'output').resolve()):
    parser.error('--server must be a fresh directory under workspace output/')
if server.exists():
    parser.error('Choose a fresh disposable --server directory')
server.mkdir(parents=True)
print('Isolated Vault weapon server:', server, flush=True)
for name in ('libraries', 'versions', 'cache'):
    origin = (source / name).resolve()
    if not origin.is_dir():
        raise RuntimeError('Missing cached Paper directory: ' + str(origin))
    shutil.copytree(origin, server / name)
if (source / 'config').is_dir():
    shutil.copytree(source / 'config', server / 'config')
shutil.copy2(source / 'paper.jar', server / 'paper.jar')

deps = [*(source / 'versions').rglob('*.jar'), *(source / 'libraries').rglob('*.jar')]
adventure = [path for path in deps if '/net/kyori/' in path.as_posix() and '/5.' in path.as_posix()]
deps = adventure + [path for path in deps if path not in adventure]
gsons = [path for path in deps if re.fullmatch(r'gson-[\d.]+\.jar', path.name)]
if gsons:
    newest = max(gsons, key=lambda path: tuple(map(int, re.findall(r'\d+', path.name))))
    deps = [newest] + [path for path in deps if path not in gsons]
# Event registration verifies SeraphShop's form types even without a connected Bedrock
# player. Supply the API-only libraries globally; no Geyser/Floodgate listener is started.
m2 = Path.home() / '.m2/repository'
api_libraries = [m2 / relative for relative in (
    'org/geysermc/cumulus/cumulus/1.1.2/cumulus-1.1.2.jar',
    'org/geysermc/floodgate/api/2.2.5-SNAPSHOT/api-2.2.5-SNAPSHOT.jar',
    'org/geysermc/api/base-api/1.0.3/base-api-1.0.3.jar')]
for library in api_libraries:
    if not library.is_file():
        raise RuntimeError('Missing cached optional-plugin API library: ' + str(library))
api_directory = server / 'probe-api-libraries'
api_directory.mkdir()
runtime_apis = []
for library in api_libraries:
    target = api_directory / library.name
    shutil.copy2(library, target)
    runtime_apis.append(target)
deps += runtime_apis
plugins = server / 'plugins'
plugins.mkdir()
shutil.copy2(garden_source, plugins / 'evergarden.jar')
shutil.copy2(magic_source, plugins / 'advance-magic.jar')
probe = server / 'probe-classes'
probe.mkdir()
options = ['--release', '25', '-proc:none', '-encoding', 'UTF-8', '-cp',
           os.pathsep.join(map(str, [garden_source, magic_source, *deps])), '-d', str(probe),
           str(args.probe_source.resolve())]
argfile = server / 'probe-javac.args'
argfile.write_text('\n'.join('"' + str(value).replace('\\', '/') + '"' for value in options), encoding='utf8')
subprocess.run(['java', str(ROOT / 'tools/CompileJava.java'), str(argfile)], check=True)
(probe / 'plugin.yml').write_text(
    f"name: {args.probe_source.stem}\nversion: '1.0'\nmain: {args.probe_source.stem}\napi-version: '26.2'\n"
    + 'depend: [advance-magic, Evergarden]\n', encoding='utf8')
subprocess.run(['jar', '--create', '--file', str(plugins / 'vault-weapon-checks.jar'), '-C', str(probe), '.'], check=True)
for name in ('advance-magic', 'Evergarden'):
    config = plugins / name
    config.mkdir()
    text = ('resource-pack:\n  enabled: false\n  host:\n    enabled: false\n    bind: 127.0.0.1\n    port: 0\n'
            'elemental-staffs:\n  ambient-effects: false\n')
    if name == 'Evergarden':
        text += ('config-version: 2\ndimension:\n  world-name: vault_weapon_garden\n  seed: 72819345\n'
                 'overworld-sanctuaries:\n  enabled: false\n  treasure:\n    over-cap-chance: 0\n'
                 'world-boss:\n  visuals:\n    pack:\n      enabled: false\n      host:\n        bind: 127.0.0.1\n        port: 0\n')
    (config / 'config.yml').write_text(text, encoding='utf8')
stats = plugins / 'bStats'
stats.mkdir()
(stats / 'config.yml').write_text('enabled: false\n', encoding='utf8')
(server / 'eula.txt').write_text('eula=true\n', encoding='ascii')
(server / 'server.properties').write_text(
    'server-ip=127.0.0.1\nserver-port=0\nonline-mode=false\nenforce-secure-profile=false\n'
    'level-name=vault_weapons\nlevel-type=minecraft:flat\ngenerate-structures=false\n'
    'generator-settings={"layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}],"biome":"minecraft:plains"}\n'
    'view-distance=2\nsimulation-distance=2\nspawn-protection=0\n'
    'pause-when-empty-seconds=-1\nmax-tick-time=180000\n', encoding='ascii')
with zipfile.ZipFile(server / 'paper.jar') as bundled:
    version = bundled.read('META-INF/versions.list').decode().strip().split('\t')[2]
    runtime = [server / 'versions' / version]
    runtime += [server / 'libraries' / line.split('\t')[2]
                for line in bundled.read('META-INF/libraries.list').decode().splitlines() if line.strip()]
runtime += runtime_apis
for library in runtime:
    if not library.is_file():
        raise RuntimeError('Missing cached bootstrap library: ' + str(library))
launch = server / 'server-launch.args'
launch.write_text('\n'.join('"' + str(value).replace('\\', '/') + '"' for value in
                           ['-Xms256M', '-Xmx3G', '-Dterminal.jline=false', '-Dterminal.ansi=false',
                            '-Dvault.disabledMagic=' + str(args.disabled_magic).lower(),
                            '-cp', os.pathsep.join(map(str, runtime)), 'org.bukkit.craftbukkit.Main',
                            '--nogui', '--noconsole']), encoding='utf8')
with (server / 'console.log').open('w', encoding='utf8') as log:
    process = subprocess.Popen(
        ['java', '@' + str(launch)], cwd=server, stdout=log, stderr=subprocess.STDOUT,
        creationflags=subprocess.CREATE_NO_WINDOW if os.name == 'nt' else 0)
    try:
        process.wait(timeout=args.timeout)
    except subprocess.TimeoutExpired:
        process.kill()
        process.wait()
        raise RuntimeError('Vault weapon probe timed out: ' + str(server / 'console.log'))
result = server / args.result_name
if not result.exists():
    raise RuntimeError('Vault weapon probe did not finish: ' + str(server / 'console.log'))
report = result.read_text(encoding='utf8').strip()
print(report, flush=True)
print('Tested garden:', plugins / 'evergarden.jar', flush=True)
print('Tested magic:', str(plugins / 'advance-magic.jar') + (' (disabled before checks)' if args.disabled_magic else ''), flush=True)
print('Server log:', server / 'console.log', flush=True)
console = (server / 'console.log').read_text(encoding='utf8', errors='replace')
for failure in ('Could not pass event', 'Error occurred while enabling advance-magic', 'Error occurred while enabling Evergarden'):
    if failure in console:
        raise RuntimeError('Runtime exception logged (' + failure + '): ' + str(server / 'console.log'))
if report.startswith('PASS'):
    receipt = {'garden_sha256': hashlib.sha256((plugins / 'evergarden.jar').read_bytes()).hexdigest(),
               'magic_sha256': hashlib.sha256((plugins / 'advance-magic.jar').read_bytes()).hexdigest(),
               'probe_sha256': hashlib.sha256(args.probe_source.read_bytes()).hexdigest(),
               'disabled_magic': args.disabled_magic, 'report': report}
    (server / 'vault-weapon-validation.json').write_text(json.dumps(receipt, indent=2) + '\n', encoding='utf8')
raise SystemExit(0 if report.startswith('PASS') else 1)
