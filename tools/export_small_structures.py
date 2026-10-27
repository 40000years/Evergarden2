"""Export verified small destinations with live block states from an existing disposable probe server."""
import argparse
import os
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--server', type=Path, required=True)
parser.add_argument('--garden-jar', type=Path, required=True)
parser.add_argument('--classpath-file', type=Path, required=True)
args = parser.parse_args()
server = args.server.resolve()
if not server.is_relative_to(ROOT / 'output') or not (server / 'living-checkpoint.txt').is_file():
    raise SystemExit('Use a previously verified disposable LivingBiomeProbe server under output.')
classes = server / 'preview-classes'; classes.mkdir(exist_ok=True)
classpath = os.pathsep.join([str(args.garden_jar.resolve()), args.classpath_file.read_text(encoding='utf8').strip()])
options = ['--release', '25', '-proc:none', '-encoding', 'UTF-8', '-cp', classpath, '-d', str(classes),
    str(ROOT / 'tests/biome-world/LivingBiomeProbe.java')]
argfile = server / 'preview-javac.args'
argfile.write_text('\n'.join('"' + str(v).replace('\\', '/') + '"' for v in options), encoding='utf8')
subprocess.run(['javac', '@' + str(argfile)], check=True)
(classes / 'plugin.yml').write_text("name: LivingBiomeProbe\nversion: 'preview'\nmain: LivingBiomeProbe\napi-version: '26.2'\ndepend: [Evergarden]\n", encoding='utf8')
subprocess.run(['jar', '--create', '--file', str(server / 'plugins/biome-world-probe.jar'), '-C', str(classes), '.'], check=True)
(server / 'living-export-only').write_text('Preview export only; keep the recorded validation results unchanged.\n', encoding='utf8')
with (server / 'console-preview.log').open('w', encoding='utf8') as log:
    subprocess.run(['java', '-Xms512M', '-Xmx3G', '-Dterminal.jline=false', '-Dterminal.ansi=false', '-jar', 'paper.jar', '--nogui', '--noconsole'],
        cwd=server, stdout=log, stderr=subprocess.STDOUT, timeout=300, check=True,
        creationflags=subprocess.CREATE_NO_WINDOW if os.name == 'nt' else 0)
result = (server / 'living-result.txt').read_text(encoding='utf8')
if not result.startswith('PASS'):
    raise RuntimeError(result)
print(result, server / 'small-structures')
