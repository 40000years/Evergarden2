#!/usr/bin/env python3
"""Run exact terrain checks and optional two-boot Paper integration using cached libraries."""
import argparse
import os
from pathlib import Path
import shutil
import subprocess

ROOT = Path(__file__).resolve().parents[2]


def run():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--garden-jar', type=Path, required=True)
    p.add_argument('--classpath-file', type=Path, required=True)
    p.add_argument('--output', type=Path, default=ROOT / 'output/biome-world')
    p.add_argument('--template', type=Path)
    p.add_argument('--server', type=Path)
    p.add_argument('--timeout', type=int, default=300)
    p.add_argument('--probe', choices=('terrain', 'living', 'rarity'), default='terrain')
    p.add_argument('--baseline-jar', type=Path, help='Previous release for exact scenery migration comparisons')
    args = p.parse_args()
    probe = {'living': 'LivingBiomeProbe', 'terrain': 'BiomeWorldProbe', 'rarity': 'ExplorationRarityChecks'}[args.probe]
    result_name = {'living': 'living-result.txt', 'terrain': 'biome-world-result.txt', 'rarity': 'rarity-result.txt'}[args.probe]
    output = args.output.resolve(); output.mkdir(parents=True, exist_ok=True)
    classes = output / 'check-classes'; classes.mkdir(exist_ok=True)
    classpath = os.pathsep.join([str(args.garden_jar.resolve()), args.classpath_file.read_text(encoding='utf8').strip()])
    options = ['--release', '25', '-proc:none', '-encoding', 'UTF-8', '-cp', classpath, '-d', str(classes),
               str(Path(__file__).with_name('BiomeTerrainChecks.java').resolve())]
    if args.template:
        options.append(str(Path(__file__).with_name(probe + '.java').resolve()))
    argfile = output / 'javac.args'
    argfile.write_text('\n'.join('"' + str(v).replace('\\', '/') + '"' for v in options), encoding='utf8')
    subprocess.run(['javac', '@' + str(argfile)], check=True)
    java_options=['-cp',os.pathsep.join([str(classes),classpath]),'BiomeTerrainChecks',str(output/'samples')]
    java_argfile=output/'java.args'
    java_argfile.write_text('\n'.join('"'+str(v).replace('\\','/')+'"' for v in java_options),encoding='utf8')
    subprocess.run(['java','@'+str(java_argfile)],check=True)
    if not args.template:
        return
    if not args.server:
        p.error('--template requires --server (a new disposable directory)')
    server = args.server.resolve(); server.mkdir(parents=True, exist_ok=False)
    for name in ('libraries', 'versions', 'cache'):
        target = server / name; source = (args.template / name).resolve()
        if not source.is_dir():
            continue
        if os.name == 'nt':
            command = "New-Item -ItemType Junction -Path '" + str(target).replace("'", "''") + "' -Target '" + str(source).replace("'", "''") + "' | Out-Null"
            subprocess.run(['powershell', '-NoProfile', '-Command', command], check=True)
        else:
            target.symlink_to(source, target_is_directory=True)
    shutil.copy2(args.template / 'paper.jar', server / 'paper.jar')
    if args.baseline_jar:
        shutil.copy2(args.baseline_jar, server / 'baseline.jar')
    plugins = server / 'plugins'; plugins.mkdir()
    shutil.copy2(args.garden_jar, plugins / 'evergarden.jar')
    shutil.copy2(ROOT / 'dist/advance-magic.jar', plugins / 'advance-magic.jar')
    for name in ('Evergarden', 'advance-magic'):
        folder = plugins / name; folder.mkdir()
        config = ('config-version: 2\nresource-pack:\n  enabled: false\n  host:\n    enabled: false\n'
                  'world-boss:\n  visuals:\n    pack:\n      host:\n        bind: 127.0.0.1\n        port: 0\n')
        if name == 'Evergarden':
            config += 'dimension:\n  seed: 20261013\n'
            config += ('structures:\n  boss-structures:\n'
                       '    arkhaia-castle:\n      chance: 0.30\n'
                       '    seraphon-valkyrie:\n      chance: 0.30\n'
                       '    seraphon-sanctum:\n      chance: 0.30\n')
        (folder / 'config.yml').write_text(config, encoding='utf8')
    (classes / 'plugin.yml').write_text(f"name: {probe}\nversion: '1'\nmain: {probe}\napi-version: '26.2'\ndepend: [Evergarden]\n", encoding='utf8')
    subprocess.run(['jar', '--create', '--file', str(plugins / 'biome-world-probe.jar'), '-C', str(classes), '.'], check=True)
    (server / 'eula.txt').write_text('eula=true\n')
    (server / 'server.properties').write_text('server-ip=127.0.0.1\nserver-port=0\nonline-mode=false\nlevel-name=biome_base\nlevel-type=minecraft:flat\nview-distance=2\nsimulation-distance=2\nspawn-protection=0\npause-when-empty-seconds=-1\nmax-tick-time=240000\n')
    (server / 'spigot.yml').write_text('settings:\n  timeout-time: 240\n  restart-on-crash: false\n')
    (server / 'fixture-settings.txt').write_text('Disposable test world only. Seed=20261013. Boss-structure chance=0.30 for each kind to cover every existing blueprint; production rarity is unchanged.\n',encoding='utf8')
    for phase in (1, 2):
        log = server / f'console-{phase}.log'
        try:
            with log.open('w', encoding='utf8') as sink:
                subprocess.run(['java', '-Xms512M', '-Xmx3G', '-Dterminal.jline=false', '-Dterminal.ansi=false', '-jar', 'paper.jar', '--nogui', '--noconsole'],
                               cwd=server, stdout=sink, stderr=subprocess.STDOUT, timeout=args.timeout, check=True,
                               creationflags=subprocess.CREATE_NO_WINDOW if os.name == 'nt' else 0)
            result = (server / result_name).read_text(encoding='utf8')
            print(f'Phase {phase}:\n{result}', flush=True)
            (server / result_name).rename(server / f'{Path(result_name).stem}-{phase}.txt')
            if not result.startswith('PASS'):
                raise RuntimeError(result)
            if args.probe == 'living' and phase == 1:
                config_file = plugins / 'Evergarden' / 'config.yml'
                config_file.write_text(config_file.read_text(encoding='utf8').replace('chance: 0.35', 'chance: 0.95'), encoding='utf8')
        except Exception:
            print(log.read_text(errors='replace')[-12000:])
            raise
    print('Disposable Paper server:', server)


if __name__ == '__main__':
    run()
