"""Run flying-staff control and lifecycle probes on a fresh localhost Paper/Geyser server."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import socket
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[2]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--source-server', type=Path, required=True)
parser.add_argument('--server', type=Path, required=True)
parser.add_argument('--magic-jar', type=Path, default=ROOT / 'dist/advance-magic.jar')
parser.add_argument('--compile-service', action='store_true', help='Compile the flight fix classes and config into the supplied base release')
parser.add_argument('--reuse-disposable', action='store_true', help='Reuse only a disposable probe directory created by this script')
parser.add_argument('--timeout', type=int, default=180)
args = parser.parse_args()
source, server = args.source_server.resolve(), args.server.resolve()
server.relative_to(ROOT / 'output')
reuse = args.reuse_disposable and (server / 'artifact-manifest.json').is_file()
if args.reuse_disposable and not reuse:
    parser.error('Reuse requires this runner\'s artifact-manifest.json')
server.mkdir(parents=True, exist_ok=reuse)
print('Disposable server:', server, flush=True)
for name in ('libraries', 'versions', 'cache', 'config'):
    if (source / name).is_dir() and not reuse:
        shutil.copytree(source / name, server / name)
shutil.copy2(source / 'paper.jar', server / 'paper.jar')
plugins = server / 'plugins'
plugins.mkdir(exist_ok=reuse)
for name in ('Geyser-Spigot.jar', 'floodgate-spigot.jar'):
    shutil.copy2(source / 'plugins' / name, plugins / name)
magic = plugins / 'advance-magic.jar'
shutil.copy2(args.magic_jar, magic)
deps = [*plugins.glob('*.jar'), *(server / 'versions').rglob('*.jar'), *(server / 'libraries').rglob('*.jar')]
def source_hashes():
    paths = [*(ROOT / 'advance-magic/src/main').rglob('*'), *(ROOT / 'advance-magic/tests').glob('*.java')]
    return {str(p.relative_to(ROOT)): hashlib.sha256(p.read_bytes()).hexdigest() for p in paths
            if p.is_file() and p.suffix in ('.java', '.yml')}
source_snapshot = source_hashes()
gsons = [p for p in deps if re.fullmatch(r'gson-[\d.]+\.jar', p.name)]
if gsons:
    newest = max(gsons, key=lambda p: tuple(map(int, re.findall(r'\d+', p.name))))
    deps = [newest] + [p for p in deps if p not in gsons]


def compile_sources(sources, classes):
    classes.mkdir(exist_ok=reuse)
    options = ['--release', '25', '-proc:none', '-encoding', 'UTF-8', '-cp',
               os.pathsep.join(map(str, deps)), '-d', str(classes), *map(str, sources)]
    argfile = classes.with_name(classes.name + '-javac.args')
    argfile.write_text('\n'.join('"' + str(v).replace('\\', '/') + '"' for v in options), encoding='utf8')
    subprocess.run(['java', str(ROOT / 'tools/CompileJava.java'), str(argfile)], check=True)


if args.compile_service:
    classes = server / 'service-classes'
    java = ROOT / 'advance-magic/src/main/java/com/example/advancemagic'
    compile_sources([java / name for name in ('item/FlyingStaffService.java', 'item/FlyingStaffBedrockInput.java',
                                             'CastListener.java', 'item/RestorationService.java')], classes)
    revised = magic.with_suffix('.next.jar')
    replacements = {p.relative_to(classes).as_posix(): p.read_bytes() for p in classes.rglob('*.class')}
    replacements.update({name: (ROOT / 'advance-magic/src/main/resources' / name).read_bytes()
                         for name in ('config.yml', 'plugin.yml')})
    with zipfile.ZipFile(magic) as old, zipfile.ZipFile(revised, 'w') as new:
        for info in old.infolist():
            new.writestr(info, replacements.pop(info.filename, None) or old.read(info.filename))
        for name, data in replacements.items():
            new.writestr(name, data)
    revised.replace(magic)
probe = server / 'probe-classes'
sources = [Path(__file__).with_name(name) for name in
           ('FlyingStaffRuntimeChecks.java', 'FlyingStaffControlChecks.java', 'GeyserGamepadChecks.java', 'FlyingStaffAdditionalChecks.java')]
compile_sources(sources, probe)
(probe / 'plugin.yml').write_text(
    "name: GrimAC\nversion: '0-test'\nmain: FlyingStaffRuntimeChecks\napi-version: '26.2'\n"
    'depend: [advance-magic, Geyser-Spigot]\n', encoding='utf8')
with zipfile.ZipFile(plugins / 'flying-staff-probe.jar', 'w', zipfile.ZIP_DEFLATED) as archive:
    for path in probe.rglob('*'):
        if path.is_file():
            archive.write(path, path.relative_to(probe).as_posix())
config = plugins / 'advance-magic'
config.mkdir(exist_ok=reuse)
(config / 'config.yml').write_text(
    'resource-pack:\n  enabled: false\n  host:\n    enabled: false\n'
    'flying-staff:\n  diagnostics:\n    enabled: false\n', encoding='utf8')
geyser = plugins / 'Geyser-Spigot'
geyser.mkdir(exist_ok=reuse)
with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as sock:
    sock.bind(('127.0.0.1', 0))
    port = sock.getsockname()[1]
(geyser / 'config.yml').write_text(
    f'bedrock:\n  address: 127.0.0.1\n  port: {port}\nremote:\n  address: auto\n  auth-type: floodgate\n'
    'metrics:\n  enabled: false\n', encoding='utf8')
stats = plugins / 'bStats'
stats.mkdir(exist_ok=reuse)
(stats / 'config.yml').write_text('enabled: false\n', encoding='utf8')
(server / 'eula.txt').write_text('eula=true\n', encoding='ascii')
(server / 'server.properties').write_text(
    'server-ip=127.0.0.1\nserver-port=0\nonline-mode=false\nenforce-secure-profile=false\n'
    'allow-flight=false\nlevel-name=flight_test\nlevel-type=minecraft:flat\ngenerate-structures=false\n'
    'view-distance=2\nsimulation-distance=2\nspawn-protection=0\n'
    'pause-when-empty-seconds=-1\nmax-tick-time=180000\n', encoding='ascii')
with zipfile.ZipFile(server / 'paper.jar') as bundled:
    version = bundled.read('META-INF/versions.list').decode().strip().split('\t')[2]
    runtime = [server / 'versions' / version]
    runtime += [server / 'libraries' / line.split('\t')[2]
                for line in bundled.read('META-INF/libraries.list').decode().splitlines() if line.strip()]
launch = server / 'server-launch.args'
launch.write_text('\n'.join('"' + str(v).replace('\\', '/') + '"' for v in
                           ['-Xms256M', '-Xmx2G', '-Dterminal.jline=false', '-Dterminal.ansi=false',
                            '-cp', os.pathsep.join(map(str, runtime)), 'org.bukkit.craftbukkit.Main',
                            '--nogui', '--noconsole']), encoding='utf8')
(server / 'artifact-manifest.json').write_text(json.dumps({
    'base_jar': str(args.magic_jar.resolve()), 'service_compiled_from_workspace': args.compile_service,
    'source_sha256': source_snapshot,
    'sha256': {p.name: hashlib.sha256(p.read_bytes()).hexdigest() for p in plugins.glob('*.jar')},
    'limits': 'Simulated actors and packets, not a physical controller or a rendered Bedrock client.'
}, indent=2) + '\n', encoding='utf8')
result = server / 'flying-staff-controls-result.json'
if result.is_file():
    result.unlink()
with (server / 'console.log').open('w', encoding='utf8') as log:
    process = subprocess.Popen(['java', '@' + str(launch)], cwd=server, stdout=log, stderr=subprocess.STDOUT,
                               creationflags=subprocess.CREATE_NO_WINDOW if os.name == 'nt' else 0)
    try:
        process.wait(timeout=args.timeout)
    except subprocess.TimeoutExpired:
        process.kill()
        process.wait()
        raise RuntimeError('Probe timed out: ' + str(server / 'console.log'))
if not result.is_file():
    raise RuntimeError('Probe did not finish: ' + str(server / 'console.log'))
report = json.loads(result.read_text(encoding='utf8'))
console = (server / 'console.log').read_text(encoding='utf8', errors='replace')
runtime_errors = [line for line in console.splitlines() if any(marker in line for marker in
                 ('Could not pass event', 'Error occurred while enabling', 'Error occurred while disabling'))]
report['runtime_errors'] = runtime_errors
current_sources = source_hashes()
report['sources_changed_during_run'] = [name for name in sorted(set(source_snapshot) | set(current_sources))
                                         if source_snapshot.get(name) != current_sources.get(name)]
result.write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', encoding='utf8')
print(json.dumps({key: value for key, value in report.items() if key != 'results'}, ensure_ascii=False, indent=2), flush=True)
print('Full results:', result, flush=True)
raise SystemExit(0 if not report['failures'] and not runtime_errors and not report['sources_changed_during_run'] else 1)
