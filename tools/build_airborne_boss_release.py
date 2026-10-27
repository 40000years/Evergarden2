"""Compile the airborne boss fix and patch only its class families into the current release."""
from pathlib import Path
import argparse
import hashlib
import json
import os
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--base-jar', type=Path, default=ROOT / 'output/airborne-boss-release-20261023/evergarden-before.jar')
parser.add_argument('--output', type=Path, default=ROOT / 'output/airborne-boss-release-20261023/build')
args = parser.parse_args()
build = args.output.resolve()
build.mkdir(parents=True, exist_ok=True)
classes = build / 'classes'
classes.mkdir(exist_ok=True)
boss = 'com/example/voidscape/boss/'
families = [boss + name for name in ('ArkhaiaMinions', 'RiverTrials', 'CocytusEncounter', 'MalefeasanceEncounter', 'SanctuaryEncounters', 'AerialCounter')]
families.append('com/example/voidscape/world/SanctuaryArena')
sources = [ROOT / 'evergarden/src/main/java' / (name + '.java') for name in families]
pinned = (ROOT / 'output/banshee-hunt-build-v1-20261015/evergarden-javac.args').read_text(encoding='utf8').splitlines()[6].strip('"')
classpath = os.pathsep.join((str(args.base_jar.resolve()), pinned))
(build / 'classpath.txt').write_text(classpath, encoding='utf8')
options = ['--release', '25', '-proc:none', '-encoding', 'UTF-8', '-cp', classpath, '-d', str(classes), *map(str, sources)]
argfile = build / 'javac.args'
argfile.write_text('\n'.join('"' + value.replace('\\', '/') + '"' for value in options), encoding='utf8')
subprocess.run(['java', str(ROOT / 'tools/CompileJava.java'), str(argfile)], check=True)

def belongs(name):
    return any(name == family + '.class' or name.startswith(family + '$') for family in families)

with zipfile.ZipFile(args.base_jar) as old:
    original = {name: old.read(name) for name in old.namelist() if not name.endswith('/')}
entries = {name: data for name, data in original.items() if not belongs(name)}
for file in classes.rglob('*.class'):
    name = file.relative_to(classes).as_posix()
    if not belongs(name):
        raise RuntimeError('Unexpected compiled class: ' + name)
    entries[name] = file.read_bytes()
entries['plugin.yml'] = (ROOT / 'evergarden/src/main/resources/plugin.yml').read_bytes()
jar = build / 'evergarden.jar'
with zipfile.ZipFile(jar, 'w', zipfile.ZIP_DEFLATED) as archive:
    for name, data in entries.items():
        archive.writestr(name, data)
with zipfile.ZipFile(jar) as archive:
    if archive.testzip() is not None:
        raise RuntimeError('Corrupt release archive')
    actual = {name: archive.read(name) for name in archive.namelist()}
changed = sorted(name for name in set(original) | set(actual) if original.get(name) != actual.get(name))
assert all(name == 'plugin.yml' or belongs(name) for name in changed)
manifest = {
    'artifact': str(jar),
    'base_sha256': hashlib.sha256(args.base_jar.read_bytes()).hexdigest(),
    'sha256': hashlib.sha256(jar.read_bytes()).hexdigest(),
    'changed_entries': changed,
    'sources': {str(file.relative_to(ROOT)): hashlib.sha256(file.read_bytes()).hexdigest() for file in sources},
    'unrelated_entries_preserved': True,
}
(build / 'build-manifest.json').write_text(json.dumps(manifest, indent=2), encoding='utf8')
print(f'PASS: compiled {len(sources)} sources; patched {len(changed)} entries; unrelated classes and resource packs preserved')
print(jar)
