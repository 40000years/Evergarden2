"""Verify Bukkit item models against the real local Geyser matcher on isolated Paper."""
import argparse
import os
from pathlib import Path
import shutil
import socket
import subprocess
import uuid
import struct
import gzip

p = argparse.ArgumentParser()
p.add_argument('--source-server', type=Path, required=True)
p.add_argument('--garden-jar', type=Path)
p.add_argument('--magic-jar', type=Path)
p.add_argument('--server', type=Path)
args = p.parse_args()
root = Path(__file__).resolve().parents[2]
server = (args.server or root / '.audit-plugins' / ('bedrock-items-' + uuid.uuid4().hex)).resolve()
server.mkdir(parents=True, exist_ok=True)
for name in ('libraries', 'versions', 'cache'):
    target = server / name
    if not target.exists():
        source = (args.source_server / name).resolve()
        subprocess.run(['powershell', '-NoProfile', '-Command',
                        "New-Item -ItemType Junction -Path '" + str(target).replace("'", "''")
                        + "' -Target '" + str(source).replace("'", "''") + "' | Out-Null"], check=True)
plugins = server / 'plugins'
plugins.mkdir(exist_ok=True)
shutil.copy2(args.garden_jar or root / 'dist/evergarden.jar', plugins / 'evergarden.jar')
shutil.copy2(args.magic_jar or root / 'dist/advance-magic.jar', plugins / 'advance-magic.jar')
for name in ('Geyser-Spigot.jar',):
    source = args.source_server / 'plugins' / name
    shutil.copy2(source, plugins / name)
for name in ('Evergarden', 'advance-magic'):
    folder = plugins / name
    folder.mkdir(exist_ok=True)
    (folder / 'config.yml').write_text('config-version: 2\nresource-pack:\n  enabled: false\n  host:\n    enabled: false\n  geyser:\n    auto-install: true\nworld-boss:\n  visuals:\n    pack:\n      host:\n        bind: 127.0.0.1\n        port: 0\n', encoding='utf-8')
geyser = plugins / 'Geyser-Spigot'
geyser.mkdir(exist_ok=True)
with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as s:
    s.bind(('127.0.0.1', 0))
    udp_port = s.getsockname()[1]
(geyser / 'config.yml').write_text('bedrock:\n  address: 127.0.0.1\n  port: ' + str(udp_port)
                                + '\n  clone-remote-port: false\nremote:\n  auth-type: offline\n'
                                + 'gameplay:\n  enable-custom-content: true\n  force-resource-packs: true\n', encoding='utf-8')
classes = server / 'check-classes'
classes.mkdir(exist_ok=True)
deps = [*plugins.glob('*.jar'), *(server / 'versions').rglob('*.jar'), *(server / 'libraries').rglob('*.jar'),
        *Path.home().joinpath('.m2/repository/org/jetbrains/annotations').rglob('*.jar')]
subprocess.run(['javac', '-proc:none', '-encoding', 'UTF-8', '-cp', os.pathsep.join(map(str, deps)),
                '-d', str(classes), str(Path(__file__).with_name('BedrockItemChecks.java')),
                str(Path(__file__).with_name('PortalTravelChecks.java')),
                str(Path(__file__).with_name('RetiredRelicChecks.java')),
                str(Path(__file__).with_name('RecipeMenuChecks.java')),
                str(Path(__file__).with_name('MythicSpellChecks.java')),
                str(Path(__file__).with_name('LegendaryBedrockChecks.java')),
                str(root / 'tests/ancient-judge/JudgeBedrockChecks.java')], check=True)
(classes / 'plugin.yml').write_text("name: BedrockItemChecks\nversion: '1.0'\nmain: BedrockItemChecks\napi-version: '26.2'\ndepend: [Evergarden, advance-magic, Geyser-Spigot]\n", encoding='utf-8')
subprocess.run(['jar', '--create', '--file', str(plugins / 'bedrock-item-checks.jar'), '-C', str(classes), '.'], check=True)
(server / 'eula.txt').write_text('eula=true\n')
(server / 'server.properties').write_text('server-ip=127.0.0.1\nserver-port=0\nonline-mode=false\nlevel-name=bedrock_test\nlevel-type=minecraft:flat\nview-distance=2\nsimulation-distance=2\nspawn-protection=0\nmax-tick-time=120000\n')
paper = next(args.source_server.glob('paper-*.jar'), args.source_server / 'paper.jar')
shutil.copy2(paper, server / 'paper.jar')
result = server / 'bedrock-items-result.txt'
if result.exists():
    result.unlink()
print('Isolated server:', server, flush=True)
# Existing player recipe books must be migrated during onLoad, before any login deserialization.
def utf(text):
    encoded=text.encode('utf8')
    return struct.pack('>H',len(encoded))+encoded
def tag(kind,name,payload):
    return bytes([kind])+utf(name)+payload
def strings(values):
    return bytes([8])+struct.pack('>i',len(values))+b''.join(utf(value) for value in values)
removed=[namespace+':'+name for namespace in ('voidscape','evergarden') for name in ('craft_repair_stone','craft_void_key','craft_void_key_shapeless','craft_void_elixir')]
keep=['minecraft:crafting_table','another_plugin:craft_void_key']
def fixture(legacy):
    book=tag(9,'recipes',strings(keep+(removed if legacy else [])))+tag(9,'toBeDisplayed',strings(keep+(removed if legacy else [])))+tag(1,'isGuiOpen',b'\1')+b'\0'
    opaque=bytes(range(256))
    body=tag(7,'inventory-fixture',struct.pack('>i',len(opaque))+opaque)+tag(6,'max_mana',struct.pack('>d',172.0))+tag(10,'recipeBook',book)+tag(8,'other-data',utf('unchanged'))+b'\0'
    return tag(10,'Player',body)
data=server/'bedrock_test/playerdata'
data.mkdir(parents=True,exist_ok=True)
for suffix in ('.dat','.dat_old'):
    (data/('12345678-1234-1234-1234-123456789abc'+suffix)).write_bytes(gzip.compress(fixture(True)))
(server/'recipe-fixture-expected.nbt').write_bytes(fixture(False))
with (server / 'console.log').open('w', encoding='utf-8') as log:
    subprocess.run(['java', '-Xms256M', '-Xmx1536M', '-Dterminal.jline=false', '-Dterminal.ansi=false', '-jar', 'paper.jar', '--nogui', '--noconsole'],
                   cwd=server, stdout=log, stderr=subprocess.STDOUT, timeout=240, check=True,
                   creationflags=subprocess.CREATE_NO_WINDOW if os.name == 'nt' else 0)
report = result.read_text(encoding='utf-8')
print(report)
print('Server log:', server / 'console.log')
raise SystemExit(1 if report.startswith('FAIL') else 0)
