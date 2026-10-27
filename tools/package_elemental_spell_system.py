"""Package only passing, matching builds of the elemental system; never deploy a server."""
from pathlib import Path
import argparse
import hashlib
import json
import shutil
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--system-server', type=Path, required=True)
parser.add_argument('--treasure-server', type=Path, required=True)
args = parser.parse_args()
system, treasure = args.system_server.resolve(), args.treasure_server.resolve()

def passed(directory, name):
    report = (directory / name).read_text(encoding='utf8').strip()
    if not report.startswith('PASS'):
        raise RuntimeError('Failed or missing validation: ' + report)
    return report

reports = {
    'system': passed(system, 'elemental-system-result.txt'),
    'treasure': passed(treasure, 'elemental-treasure-result.txt'),
}
magic = system / 'plugins/advance-magic.jar'
garden = treasure / 'plugins/evergarden.jar'
other_magic = treasure / 'plugins/advance-magic.jar'

def entries(path):
    with zipfile.ZipFile(path) as z:
        return {n: hashlib.sha256(z.read(n)).hexdigest() for n in z.namelist()
                if not n.endswith('/') and not n.startswith('META-INF/')}

if entries(magic) != entries(other_magic):
    raise RuntimeError('The two probes used different Advance Magic class/resource contents. Revalidate the final source.')
subprocess.run(['python', str(ROOT / 'tools/release_jar_checks.py'), str(magic), str(garden)], check=True)
dest = ROOT / 'dist'
versions = {'advance-magic': '1.13.0-elemental-spell-system', 'evergarden': '3.0.0-e2.95-elemental-scroll-treasure'}
for name, origin in [('advance-magic', magic), ('evergarden', garden)]:
    shutil.copy2(origin, dest / (name + '.jar'))
    shutil.copy2(origin, dest / (name + '-' + versions[name] + '.jar'))

def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

manifest = {
    'date': '2026-10-19', 'versions': versions,
    'reports': reports,
    'test_servers': {'system': str(system.relative_to(ROOT)), 'treasure': str(treasure.relative_to(ROOT))},
    'artifacts': {name: {'sha256': digest(dest / name), 'bytes': (dest / name).stat().st_size}
                  for name in ('advance-magic.jar', 'evergarden.jar')},
    'sources': {str(p.relative_to(ROOT)).replace('\\', '/'): digest(p)
                for module in ('advance-magic', 'evergarden')
                for p in sorted((ROOT / module / 'src/main').rglob('*'))
                if p.is_file()},
    'client_visual_test': False,
    'deployment': 'workspace artifacts only; no running player server modified',
}
manifest_path = dest / 'elemental-spell-system-manifest.json'
manifest_path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + '\n', encoding='utf8')
bundle = dest / 'elemental-spell-system-1.13.0.zip'
with zipfile.ZipFile(bundle, 'w', zipfile.ZIP_DEFLATED) as z:
    for name in ('advance-magic.jar', 'evergarden.jar'):
        z.write(dest / name, name)
    z.write(ROOT / 'docs/elemental-spell-system.md', 'elemental-spell-system.md')
    z.write(manifest_path, manifest_path.name)
print('Packaged:', bundle)
print(json.dumps({'reports': reports, 'artifacts': manifest['artifacts']}, ensure_ascii=False, indent=2))
