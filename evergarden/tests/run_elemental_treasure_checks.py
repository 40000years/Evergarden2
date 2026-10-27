"""Exercise scrolls, loot-only staff acquisition and structure/Vault integration on isolated cached Paper."""
import argparse
import os
from pathlib import Path
import re
import shutil
import socket
import subprocess
import uuid
import zipfile

ROOT = Path(__file__).resolve().parents[2]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--source-server', type=Path, required=True)
parser.add_argument('--server', type=Path)
parser.add_argument('--magic-jar', type=Path)
parser.add_argument('--garden-jar', type=Path)
parser.add_argument('--compat-server', type=Path, help='Also load cached Geyser and Floodgate from this isolated template')
parser.add_argument('--timeout', type=int, default=300)
args = parser.parse_args()
source = args.source_server.resolve()
server = (args.server or ROOT / 'output' / ('elemental-treasure-' + uuid.uuid4().hex)).resolve()
if server.exists():
    parser.error('Choose a fresh disposable --server directory')
server.mkdir(parents=True)
print('Isolated treasure test server:', server, flush=True)
for name in ('libraries', 'versions', 'cache'):
    origin = (source / name).resolve()
    if not origin.is_dir():
        raise RuntimeError('Missing cached Paper directory: ' + str(origin))
    # Paperclip rewrites patched cache jars. Physical copies isolate that mutation and
    # avoid the Windows ZipFS close failure through directory junctions.
    shutil.copytree(origin, server / name)
if (source / 'config').is_dir():
    shutil.copytree(source / 'config', server / 'config')
shutil.copy2(source / 'paper.jar', server / 'paper.jar')

deps = [*(source / 'versions').rglob('*.jar'), *(source / 'libraries').rglob('*.jar')]
# The cached runtime also retains obsolete Adventure 4 jars. Resolve current APIs first.
adventure = [path for path in deps if '/net/kyori/' in path.as_posix() and '/5.' in path.as_posix()]
deps = adventure + [path for path in deps if path not in adventure]
gsons = [path for path in deps if re.fullmatch(r'gson-[\d.]+\.jar', path.name)]
if gsons:
    newest = max(gsons, key=lambda path: tuple(map(int, re.findall(r'\d+', path.name))))
    deps = [newest] + [path for path in deps if path not in gsons]
m2 = Path.home() / '.m2/repository'
deps.extend((m2 / 'com/ticxo/modelengine/ModelEngine/R4.1.1').glob('*.jar'))
for path in ('org/geysermc/floodgate/api/2.2.5-SNAPSHOT/api-2.2.5-SNAPSHOT.jar',
             'org/geysermc/cumulus/cumulus/1.1.2/cumulus-1.1.2.jar',
             'org/geysermc/api/base-api/1.0.3/base-api-1.0.3.jar'):
    if (m2 / path).is_file():
        deps.append(m2 / path)


def compile_sources(sources, classes, extra=()):
    classes.mkdir()
    options = ['--release', '25', '-proc:none', '-encoding', 'UTF-8', '-cp',
               os.pathsep.join(map(str, [*extra, *deps])), '-d', str(classes), *map(str, sources)]
    argfile = classes.parent / (classes.name + '-javac.args')
    argfile.write_text('\n'.join('"' + str(value).replace('\\', '/') + '"' for value in options), encoding='utf8')
    subprocess.run(['java', str(ROOT / 'tools/CompileJava.java'), str(argfile)], check=True)


plugins = server / 'plugins'
plugins.mkdir()
if args.compat_server:
    for name in ('Geyser-Spigot.jar', 'floodgate-spigot.jar'):
        shutil.copy2(args.compat_server.resolve() / 'plugins' / name, plugins / name)
    with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as binding:
        binding.bind(('127.0.0.1', 0))
        bedrock_port = binding.getsockname()[1]
    geyser = plugins / 'Geyser-Spigot'
    geyser.mkdir()
    (geyser / 'config.yml').write_text(
        'bedrock:\n  address: 127.0.0.1\n  port: ' + str(bedrock_port) +
        '\n  clone-remote-port: false\nremote:\n  address: auto\n  auth-type: floodgate\n'
        'metrics:\n  enabled: false\nconfig-version: 4\n', encoding='utf8')
magic, garden = plugins / 'advance-magic.jar', plugins / 'evergarden.jar'
for module, target, prebuilt in (('advance-magic', magic, args.magic_jar), ('evergarden', garden, args.garden_jar)):
    if prebuilt:
        shutil.copy2(prebuilt, target)
        continue
    classes = server / (module + '-classes')
    sources = sorted((ROOT / module / 'src/main/java').rglob('*.java'))
    if module == 'evergarden':
        sources += sorted((ROOT / 'advance-magic/src/main/java/com/example/advancemagic/pack/delivery').glob('*.java'))
    compile_sources(sources, classes, [magic] if module == 'evergarden' else [])
    shutil.copytree(ROOT / module / 'src/main/resources', classes, dirs_exist_ok=True)
    packs = classes / 'resource-packs'
    packs.mkdir(exist_ok=True)
    names = ('advance-magic-java.zip', 'advance-magic-bedrock.mcpack', 'geyser-mappings.json', 'pack-hashes.json') if module == 'advance-magic' else (
        'evergarden-java.zip', 'judge-java.zip', 'evergarden-bedrock.mcpack', 'geyser-mappings.json', 'pack-hashes.json')
    for name in names:
        shutil.copy2(ROOT / module / 'dist' / name, packs / name)
    if module == 'advance-magic':
        authored = classes / 'modelengine/elemental-staffs'
        authored.mkdir(parents=True)
        for path in (ROOT / 'advance-magic/art/elemental-staffs').glob('*.bbmodel'):
            shutil.copy2(path, authored / path.name)
    subprocess.run(['jar', '--create', '--file', str(target), '-C', str(classes), '.'], check=True)

probe = server / 'probe-classes'
compile_sources([Path(__file__).with_name('ElementalTreasureChecks.java')], probe, [magic, garden])
(probe / 'plugin.yml').write_text(
    "name: ElementalTreasureChecks\nversion: '1.0'\nmain: ElementalTreasureChecks\napi-version: '26.2'\ndepend: [advance-magic, Evergarden]\n", encoding='utf8')
subprocess.run(['jar', '--create', '--file', str(plugins / 'elemental-treasure-checks.jar'), '-C', str(probe), '.'], check=True)
for name in ('advance-magic', 'Evergarden'):
    config = plugins / name
    config.mkdir()
    text = ('resource-pack:\n  enabled: false\n  host:\n    enabled: false\n    bind: 127.0.0.1\n    port: 0\n'
            'elemental-staffs:\n  ambient-effects: false\n')
    if name == 'Evergarden':
        text += ('config-version: 2\ndimension:\n  world-name: elemental_garden\n  seed: 72819345\n'
                 'overworld-sanctuaries:\n  enabled: false\n  treasure:\n    over-cap-chance: 0\n'
                 'world-boss:\n  visuals:\n    pack:\n      enabled: false\n      host:\n        bind: 127.0.0.1\n        port: 0\n')
    (config / 'config.yml').write_text(text, encoding='utf8')
stats = plugins / 'bStats'
stats.mkdir()
(stats / 'config.yml').write_text('enabled: false\n', encoding='utf8')
(server / 'eula.txt').write_text('eula=true\n', encoding='ascii')
(server / 'server.properties').write_text(
    'server-ip=127.0.0.1\nserver-port=0\nonline-mode=false\nenforce-secure-profile=false\n'
    'level-name=elemental_treasure\nlevel-type=minecraft:flat\ngenerate-structures=false\n'
    'generator-settings={"layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}],"biome":"minecraft:plains"}\n'
    'view-distance=2\nsimulation-distance=2\nspawn-protection=0\n'
    'pause-when-empty-seconds=-1\nmax-tick-time=180000\n', encoding='ascii')
with zipfile.ZipFile(server / 'paper.jar') as bundled:
    version = bundled.read('META-INF/versions.list').decode().strip().split('\t')[2]
    runtime = [server / 'versions' / version]
    runtime += [server / 'libraries' / line.split('\t')[2]
                for line in bundled.read('META-INF/libraries.list').decode().splitlines() if line.strip()]
for library in runtime:
    if not library.is_file():
        raise RuntimeError('Missing cached bootstrap library: ' + str(library))
launch = server / 'server-launch.args'
launch.write_text('\n'.join('"' + str(value).replace('\\', '/') + '"' for value in
                           ['-Xms256M', '-Xmx3G', '-Dterminal.jline=false', '-Dterminal.ansi=false',
                            '-Delemental.compat=' + str(bool(args.compat_server)).lower(),
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
        raise RuntimeError('Treasure probe timed out: ' + str(server / 'console.log'))
result = server / 'elemental-treasure-result.txt'
if not result.exists():
    raise RuntimeError('Treasure probe did not finish: ' + str(server / 'console.log'))
report = result.read_text(encoding='utf8').strip()
print(report, flush=True)
print('Tested magic:', magic, flush=True)
print('Tested garden:', garden, flush=True)
print('Server log:', server / 'console.log', flush=True)
console = (server / 'console.log').read_text(encoding='utf8', errors='replace')
for failure in ('Could not pass event', 'Error occurred while enabling advance-magic', 'Error occurred while enabling Evergarden'):
    if failure in console:
        raise RuntimeError('Runtime exception logged (' + failure + '): ' + str(server / 'console.log'))
raise SystemExit(0 if report.startswith('PASS') else 1)
