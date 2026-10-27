"""Run the dungeon probe using the cached Paper harness, or resume a prepared test server."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--prepared-server', type=Path)
parser.add_argument('--source-server', type=Path)
parser.add_argument('--server', type=Path, default=ROOT / 'output/dungeon-balance-20261026/paper')
args = parser.parse_args()
server = (args.prepared_server or args.server).resolve()
if not server.is_relative_to((ROOT / 'output').resolve()):
    parser.error('Test server must stay inside output/')
if args.prepared_server:
    (server / 'dungeon-balance-result.txt').unlink(missing_ok=True)
    (server / 'dungeon-balance-validation.json').unlink(missing_ok=True)
    with (server / 'console.log').open('w', encoding='utf8') as log:
        process = subprocess.Popen(['java', '@' + str(server / 'server-launch.args')], cwd=server,
                                   stdout=log, stderr=subprocess.STDOUT,
                                   creationflags=subprocess.CREATE_NO_WINDOW if os.name == 'nt' else 0)
        try:
            process.wait(timeout=300)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait()
            raise RuntimeError('Paper timed out: ' + str(server / 'console.log'))
else:
    if not args.source_server:
        parser.error('--source-server is required for a fresh server')
    subprocess.run([sys.executable, str(ROOT / 'evergarden/tests/run_vault_weapon_checks.py'),
                    '--source-server', str(args.source_server), '--server', str(server),
                    '--magic-jar', str(ROOT / 'dist/advance-magic.jar'),
                    '--garden-jar', str(ROOT / 'output/dungeon-balance-20261026/build/evergarden.jar'),
                    '--probe-source', str(ROOT / 'evergarden/tests/DungeonBalanceChecks.java'),
                    '--result-name', 'dungeon-balance-result.txt'], check=True)
report = (server / 'dungeon-balance-result.txt').read_text(encoding='utf8').strip()
print(report)
if not report.startswith('PASS'):
    raise RuntimeError('Dungeon balance probe failed')
console = (server / 'console.log').read_text(encoding='utf8', errors='replace')
for failure in ('Could not pass event', 'Error occurred while enabling advance-magic', 'Error occurred while enabling Evergarden'):
    if failure in console:
        raise RuntimeError('Plugin exception: ' + failure)
def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()
receipt = {'garden_sha256': sha(server / 'plugins/evergarden.jar'),
           'magic_sha256': sha(server / 'plugins/advance-magic.jar'),
           'probe_sha256': sha(ROOT / 'evergarden/tests/DungeonBalanceChecks.java'), 'report': report}
(server / 'dungeon-balance-validation.json').write_text(json.dumps(receipt, indent=2) + '\n', encoding='utf8')
