"""Verify with an installed, unobfuscated Java 26.x client, without launching it."""
import argparse
import json
import os
import subprocess
from pathlib import Path

parser = argparse.ArgumentParser(__doc__)
parser.add_argument('--minecraft', type=Path, default=Path(os.environ.get('APPDATA', Path.home())) / '.minecraft')
parser.add_argument('--version', default='26.2')
parser.add_argument('--expect-missing', action='store_true')
parser.add_argument('packs', nargs='+', type=Path, help='ZIP packs, from lowest to highest priority')
args = parser.parse_args()
root = Path(__file__).resolve().parents[1]
version = args.minecraft / 'versions' / args.version
metadata = json.loads((version / f'{args.version}.json').read_text())
client = version / f'{args.version}.jar'
jars = [client]
for library in metadata['libraries']:
    artifact = library.get('downloads', {}).get('artifact')
    if artifact:
        path = args.minecraft / 'libraries' / artifact['path']
        if path.is_file():
            jars.append(path)
output = root / 'target' / f'java-atlas-check-{args.version}'
output.mkdir(parents=True, exist_ok=True)
classpath = os.pathsep.join(map(str, jars))
compile_result = subprocess.run(['javac', '-proc:none', '-encoding', 'UTF-8', '-cp', classpath, '-d', str(output),
                str(root / 'tests/JavaEffectAtlasChecks.java')])
if compile_result.returncode:
    raise SystemExit(compile_result.returncode)
result = subprocess.run(['java', '-Djava.awt.headless=true', '-cp', str(output) + os.pathsep + classpath,
                'JavaEffectAtlasChecks', 'missing' if args.expect_missing else 'resolved',
                str(client), *(str(pack.resolve()) for pack in args.packs)], cwd=output)
raise SystemExit(result.returncode)
