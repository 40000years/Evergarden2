"""Build the elemental system and run real Paper regression probes in a fresh localhost server."""
import argparse
import hashlib
import io
import json
import os
import re
from pathlib import Path
import shutil
import subprocess
import time
import uuid
import zipfile

ROOT = Path(__file__).resolve().parents[2]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--source-server', type=Path, required=True)
parser.add_argument('--server', type=Path)
parser.add_argument('--plugin-jar', type=Path, help='Test an already built release instead of compiling production')
parser.add_argument('--recipe-data-fixture', type=Path, help='Gzipped offline player NBT fixture to migrate before world/player loading')
parser.add_argument('--timeout', type=int, default=240)
parser.add_argument('--probe', choices=('elemental', 'heavens', 'heavens-bedrock', 'spectacle', 'blocks-bedrock', 'levels'), default='elemental',
                    help='Run the full regression probe or the focused Legendary light lifecycle probe')
args = parser.parse_args()
started = time.monotonic()
probe_name = {'blocks-bedrock': 'LegendaryBlockBedrockChecks', 'heavens': 'HeavensDescentVisualChecks', 'heavens-bedrock': 'HeavensDescentBedrockChecks',
              'spectacle': 'LegendarySpectacleChecks', 'levels': 'SpellLevelChecks'}.get(args.probe, 'ElementalSystemChecks')
result_name = {'blocks-bedrock': 'legendary-spectacle-result.txt', 'heavens': 'heavens-descent-visual-result.txt', 'heavens-bedrock': 'heavens-descent-bedrock-result.txt',
               'spectacle': 'legendary-spectacle-result.txt', 'levels': 'spell-level-result.txt'}.get(args.probe, 'elemental-system-result.txt')
source = args.source_server.resolve()
server = (args.server or ROOT / 'output' / ('elemental-system-' + uuid.uuid4().hex)).resolve()
if server.exists():
    parser.error('Choose a fresh disposable --server directory')
if not source.joinpath('paper.jar').is_file():
    parser.error('Source server must contain cached paper.jar, libraries, versions, and cache')
server.mkdir(parents=True)
print('Isolated elemental system server:', server, flush=True)
for name in ('libraries', 'versions', 'cache'):
    origin = (source / name).resolve()
    if not origin.is_dir():
        raise RuntimeError('Missing cached Paper directory: ' + str(origin))
    # Keep Paperclip ZipFS off Windows junctions; recent JDKs reject closing
    # archives through reparse points under the restricted process token.
    shutil.copytree(origin, server / name)
if (source / 'config').is_dir():
    shutil.copytree(source / 'config', server / 'config')
shutil.copy2(source / 'paper.jar', server / 'paper.jar')

cached = source / 'compile-dependencies'
if not cached.is_dir():
    cached = ROOT / 'output/resource-pack-refactor-20261013/dependencies'
compile_deps = list(cached.glob('*.jar'))
if not compile_deps:
    compile_deps = list((Path.home() / '.m2/repository').rglob('*.jar'))
deps = [*compile_deps, *(source / 'versions').rglob('*.jar'), *(source / 'libraries').rglob('*.jar')]
gsons = [path for path in deps if re.fullmatch(r'gson-[\d.]+\.jar', path.name)]
if gsons:
    newest = max(gsons, key=lambda path: tuple(map(int, re.findall(r'\d+', path.name))))
    deps = [newest] + [path for path in deps if path not in gsons]


def compile_sources(sources, classes, extra=()):
    classes.mkdir()
    options = ['--release', '25', '-proc:none', '-encoding', 'UTF-8', '-cp',
               os.pathsep.join(map(str, [*extra, *deps])), '-d', str(classes), *map(str, sources)]
    argfile = classes.parent / (classes.name + '-javac.args')
    argfile.write_text('\n'.join('"' + str(value).replace('\\', '/') + '"' for value in options), encoding='utf8')
    # Short-lived JavaCompiler process avoids JDK 25 Windows ZipFS close errors.
    subprocess.run(['java', str(ROOT / 'tools/CompileJava.java'), str(argfile)], check=True)


plugins = server / 'plugins'
plugins.mkdir()
if args.probe in ('heavens-bedrock', 'blocks-bedrock'):
    geyser_jar = source / 'plugins/Geyser-Spigot.jar'
    if not geyser_jar.is_file():
        raise RuntimeError('Bedrock probe source server must contain plugins/Geyser-Spigot.jar')
    shutil.copy2(geyser_jar, plugins / geyser_jar.name)
    deps.append(plugins / geyser_jar.name)
    geyser = plugins / 'Geyser-Spigot'
    geyser.mkdir()
    (geyser / 'config.yml').write_text(
        'bedrock:\n  address: 127.0.0.1\n  port: 0\nremote:\n  address: auto\n  auth-type: offline\n'
        'java:\n  address: auto\n  auth-type: offline\nmetrics:\n  enabled: false\n', encoding='utf8')
magic = plugins / 'advance-magic.jar'
def source_hashes():
    paths = [*(ROOT / 'advance-magic/src/main').rglob('*'),
             *(ROOT / 'advance-magic/art/elemental-staffs').glob('*.bbmodel')]
    paths += [ROOT / 'advance-magic/dist' / name for name in
              ('advance-magic-java.zip', 'advance-magic-bedrock.mcpack', 'geyser-mappings.json', 'pack-hashes.json')]
    return {path.relative_to(ROOT).as_posix(): hashlib.sha256(path.read_bytes()).hexdigest()
            for path in sorted(paths) if path.is_file()}

build_sources = source_hashes() if not args.plugin_jar else None
if args.plugin_jar:
    shutil.copy2(args.plugin_jar, magic)
else:
    classes = server / 'magic-classes'
    compile_sources(sorted((ROOT / 'advance-magic/src/main/java').rglob('*.java')), classes)
    shutil.copytree(ROOT / 'advance-magic/src/main/resources', classes, dirs_exist_ok=True)
    packs = classes / 'resource-packs'
    packs.mkdir(exist_ok=True)
    for name in ('advance-magic-java.zip', 'advance-magic-bedrock.mcpack', 'geyser-mappings.json', 'pack-hashes.json'):
        shutil.copy2(ROOT / 'advance-magic/dist' / name, packs / name)
    models = classes / 'modelengine/elemental-staffs'
    models.mkdir(parents=True)
    for path in sorted((ROOT / 'advance-magic/art/elemental-staffs').glob('*.bbmodel')):
        shutil.copy2(path, models / path.name)
    subprocess.run(['jar', '--create', '--file', str(magic), '-C', str(classes), '.'], check=True)
    subprocess.run(['python', str(ROOT / 'tools/release_jar_checks.py'), str(magic)], check=True)
    (server / 'build-source-manifest.json').write_text(json.dumps(build_sources, indent=2) + '\n', encoding='utf8')

if args.probe in ('heavens', 'heavens-bedrock'):
    # A focused v3 test must exercise the corrected release, including the pack
    # actually shipped inside that JAR, rather than an older file beside it.
    with zipfile.ZipFile(magic) as release:
        java_bytes = release.read('resource-packs/advance-magic-java.zip')
        bedrock_bytes = release.read('resource-packs/advance-magic-bedrock.mcpack')
    with zipfile.ZipFile(io.BytesIO(bedrock_bytes)) as bedrock:
        focused_pack_version = json.loads(bedrock.read('manifest.json'))['header']['version']
    if focused_pack_version != [2, 28, 0]:
        raise RuntimeError('Focused beacon/orbital-circle probe requires bundled Bedrock pack 2.28.0, found ' + str(focused_pack_version))
    # The existing large rings were approved in game. Pin their original PNG
    # bytes so a runtime correction cannot quietly redraw or flatten that art.
    preserved_light_art = {
        'sigil': '99cfd268fdcc39c2726eff4541bfe4c09ebd7b3e585c0f373b740a7f39860da8',
        'halo': '9f3b88a4796d5074b81790dbe3696a50eec7f551324e434fb5cb281cf660f4f6',
        'mist': '9f85c08388564a5ad58e5c04e82e3601717384d47b35368bb6bf9d1c7509a11f',
    }
    with zipfile.ZipFile(io.BytesIO(java_bytes)) as java_pack, zipfile.ZipFile(io.BytesIO(bedrock_bytes)) as bedrock_pack:
        for suffix, expected_hash in preserved_light_art.items():
            name = 'elemental_legendary_light_' + suffix
            png = java_pack.read(f'assets/advance_magic/textures/effect/{name}.png')
            if hashlib.sha256(png).hexdigest() != expected_hash or png != bedrock_pack.read(f'textures/particle/{name}.png'):
                raise RuntimeError('The approved v2 source art was not preserved identically on both clients: ' + name)
        for suffix in ('beacon', 'shot'):
            name = 'elemental_legendary_light_' + suffix
            png = java_pack.read(f'assets/advance_magic/textures/effect/{name}.png')
            source_png = ROOT / f'advance-magic/art/effects/legendary-light/{name}.png'
            if png != bedrock_pack.read(f'textures/particle/{name}.png') or png != source_png.read_bytes():
                raise RuntimeError('The bundled beam artwork differs between Java, Bedrock or source: ' + name)

probe = server / 'probe-classes'
probe_sources=[Path(__file__).with_name(probe_name + '.java')]
if args.probe=='blocks-bedrock':probe_sources.append(Path(__file__).with_name('LegendarySpectacleChecks.java'))
compile_sources(probe_sources, probe, [magic])
(probe / 'plugin.yml').write_text(
    f"name: {probe_name}\nversion: '1.0'\nmain: {probe_name}\napi-version: '26.2'\ndepend: [advance-magic{', Geyser-Spigot' if args.probe in ('heavens-bedrock','blocks-bedrock') else ''}]\n",
    encoding='utf8')
subprocess.run(['jar', '--create', '--file', str(plugins / 'elemental-system-checks.jar'), '-C', str(probe), '.'], check=True)
config = plugins / 'advance-magic'
config.mkdir()
(config / 'config.yml').write_text(
    'resource-pack:\n  enabled: false\n  host:\n    enabled: false\nelemental-staffs:\n  ambient-effects: false\n', encoding='utf8')
stats = plugins / 'bStats'
stats.mkdir()
(stats / 'config.yml').write_text('enabled: false\n', encoding='utf8')
if args.recipe_data_fixture:
    fixture = args.recipe_data_fixture.resolve()
    if not re.fullmatch(r'[0-9a-fA-F-]{36}\.dat', fixture.name):
        raise ValueError('Recipe fixture must have a UUID.dat filename')
    player_data = server / 'elemental_test/playerdata'
    player_data.mkdir(parents=True, exist_ok=True)
    shutil.copy2(fixture, player_data / fixture.name)
    shutil.copy2(fixture, player_data / (fixture.name + '_old'))
(server / 'eula.txt').write_text('eula=true\n', encoding='ascii')
(server / 'server.properties').write_text(
    'server-ip=127.0.0.1\nserver-port=0\nonline-mode=false\nenforce-secure-profile=false\n'
    'level-name=elemental_test\nlevel-type=minecraft:flat\ngenerate-structures=false\n'
    'view-distance=2\nsimulation-distance=2\nspawn-protection=0\n'
    'pause-when-empty-seconds=-1\nmax-tick-time=180000\n', encoding='ascii')
with zipfile.ZipFile(server / 'paper.jar') as bundled:
    # The cached version is already patched. Launch the same server/library
    # classpath Paperclip declares, without reopening the Mojang patch archive.
    version = bundled.read('META-INF/versions.list').decode().strip().split('\t')[2]
    runtime = [server / 'versions' / version]
    runtime += [server / 'libraries' / line.split('\t')[2]
                for line in bundled.read('META-INF/libraries.list').decode().splitlines() if line.strip()]
for library in runtime:
    if not library.is_file():
        raise RuntimeError('Missing cached bootstrap library: ' + str(library))
launch = server / 'server-launch.args'
launch.write_text('\n'.join('"' + str(value).replace('\\', '/') + '"' for value in
                           ['-Xms256M', '-Xmx2G', '-Dterminal.jline=false', '-Dterminal.ansi=false',
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
        raise RuntimeError('Elemental probe timed out: ' + str(server / 'console.log'))
result = server / result_name
if not result.exists():
    raise RuntimeError('Probe did not finish: ' + str(server / 'console.log'))
report = result.read_text(encoding='utf8').strip()
print(report, flush=True)
print('Tested release:', magic, flush=True)
print('Server log:', server / 'console.log', flush=True)
console = (server / 'console.log').read_text(encoding='utf8', errors='replace')
for failure in ('Spell effect failed', 'Could not pass event', 'Error occurred while enabling advance-magic', 'Elemental cast ', 'Elemental VFX disabled for failed scene'):
    if failure in console:
        raise RuntimeError('Runtime exception logged (' + failure + '): ' + str(server / 'console.log'))
if build_sources is not None and build_sources != source_hashes():
    raise RuntimeError('Production source or asset changed during build/validation; validate the final source again')
if args.probe in ('heavens', 'heavens-bedrock'):
    validation = {
        'probe': probe_name,
        'result': report,
        'release_sha256': hashlib.sha256(magic.read_bytes()).hexdigest(),
        'probe_source_sha256': hashlib.sha256(Path(__file__).with_name(probe_name + '.java').read_bytes()).hexdigest(),
        'bedrock_pack_version': focused_pack_version,
        'elapsed_seconds': round(time.monotonic() - started, 2),
        'server_exit_code': process.returncode,
        'scope': 'Real Paper lifecycle and Geyser packet contracts; no GPU or live-client visual/FPS verification',
    }
    (server / ('heavens-descent-' + ('bedrock' if args.probe == 'heavens-bedrock' else 'visual') + '-validation.json')).write_text(
        json.dumps(validation, indent=2) + '\n', encoding='utf8')
raise SystemExit(0 if report.startswith('PASS') else 1)
