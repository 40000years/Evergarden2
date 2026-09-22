#!/usr/bin/env python3
"""Run the landmark probe in a disposable copy of Paper; never copy a live world."""
import argparse
import os
from pathlib import Path
import shutil
import subprocess
import tempfile


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--paper-template', type=Path, required=True)
    parser.add_argument('--paper-jar', default='paper.jar')
    parser.add_argument('--java-home', type=Path, required=True)
    parser.add_argument('--timeout', type=int, default=300)
    args = parser.parse_args()
    repo = Path(__file__).resolve().parents[2]
    server = Path(tempfile.mkdtemp(prefix='evergarden2-landmark-probe-'))
    print(f'Disposable server: {server}', flush=True)
    for name in ['libraries', 'versions', 'cache']:
        source = args.paper_template / name
        if source.is_dir():
            shutil.copytree(source, server / name)
    shutil.copy2(args.paper_template / args.paper_jar, server / 'paper.jar')
    plugins = server / 'plugins'
    plugins.mkdir()
    for name in ['advance-magic.jar', 'evergarden.jar']:
        shutil.copy2(repo / 'dist' / name, plugins / name)
    classes = server / 'probe-classes'
    classes.mkdir()
    exe = '.exe' if os.name == 'nt' else ''
    java, javac, jar = [str(args.java_home / 'bin' / (name + exe)) for name in ['java', 'javac', 'jar']]
    cp = os.pathsep.join(str(p) for p in [*plugins.glob('*.jar'), *(server / 'libraries').rglob('*.jar')])
    source = Path(__file__).resolve().parent
    subprocess.run([javac, '--release', '25', '-proc:none', '-encoding', 'UTF-8', '-cp', cp,
                    '-d', str(classes), str(source / 'SkyLandmarkProbe.java')], check=True)
    shutil.copy2(source / 'plugin.yml', classes / 'plugin.yml')
    subprocess.run([jar, '--create', '--file', str(plugins / 'landmark-probe.jar'), '-C', str(classes), '.'], check=True)
    (server / 'eula.txt').write_text('eula=true\n')
    (server / 'server.properties').write_text(
        'server-ip=127.0.0.1\nserver-port=0\nonline-mode=false\nview-distance=2\nsimulation-distance=2\n'
        'spawn-protection=0\npause-when-empty-seconds=-1\nmax-tick-time=240000\n'
        'level-name=probe_base\nlevel-type=minecraft:flat\ngenerate-structures=false\n')
    for phase in [1, 2]:
        log = server / f'probe-console-phase{phase}.log'
        try:
            with log.open('w') as output:
                subprocess.run([java, '-Xms512M', '-Xmx3G', '-Dterminal.jline=false', '-Dterminal.ansi=false',
                                '-jar', 'paper.jar', '--nogui'], cwd=server, stdin=subprocess.DEVNULL,
                               stdout=output, stderr=subprocess.STDOUT, timeout=args.timeout, check=True,
                               creationflags=subprocess.CREATE_NO_WINDOW if os.name == 'nt' else 0)
        except (subprocess.TimeoutExpired, subprocess.CalledProcessError):
            print(log.read_text(errors='replace')[-10000:])
            raise
        result = server / 'landmark-probe-result.txt'
        if not result.is_file():
            print(log.read_text(errors='replace')[-10000:])
            raise SystemExit('Probe did not produce a result')
        print(f'Phase {phase}:\n{result.read_text()}', flush=True)
        if not result.read_text().startswith('PASS\n'):
            print(log.read_text(errors='replace')[-10000:])
            raise SystemExit(1)
        result.rename(server / f'landmark-probe-result-phase{phase}.txt')


if __name__ == '__main__':
    main()
