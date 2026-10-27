"""Compile only boss hit detection classes and preserve the current release's assets."""
from pathlib import Path
import argparse
import hashlib
import json
import os
import shutil
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]
RELEASE = ROOT / 'output/boss-hitbox-release-20261023'
DEFAULT_FAMILIES = ['BossHitbox', 'ArkhaiaMinions', 'ArkhaiaBoss', 'SanctuaryEncounters', 'RiverVisual']
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--base-jar', type=Path, default=RELEASE / 'evergarden-before.jar')
parser.add_argument('--output', type=Path, default=RELEASE / 'build')
parser.add_argument('--family', action='append', help='Boss class family to compile (repeat to replace the defaults)')
args = parser.parse_args()
base = args.base_jar.resolve()
if not base.exists():
    if base != (RELEASE / 'evergarden-before.jar').resolve():
        raise FileNotFoundError(base)
    base.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(ROOT / 'dist/evergarden.jar', base)
build = args.output.resolve()
build.mkdir(parents=True, exist_ok=True)
classes = build / 'classes'
classes.mkdir(exist_ok=False)
families = ['com/example/voidscape/boss/' + name for name in (args.family or DEFAULT_FAMILIES)]
sources = [ROOT / 'evergarden/src/main/java' / (name + '.java') for name in families]
pinned = (ROOT / 'output/banshee-hunt-build-v1-20261015/evergarden-javac.args').read_text(encoding='utf8').splitlines()[6].strip('"')
classpath = os.pathsep.join((str(base), pinned))
(build / 'classpath.txt').write_text(classpath, encoding='utf8')
options = ['--release', '25', '-proc:none', '-encoding', 'UTF-8', '-cp', classpath, '-d', str(classes), *map(str, sources)]
argfile = build / 'javac.args'
argfile.write_text('\n'.join('"' + value.replace('\\', '/') + '"' for value in options), encoding='utf8')
subprocess.run(['java', str(ROOT / 'tools/CompileJava.java'), str(argfile)], check=True)


def belongs(name):
    return any(name == family + '.class' or name.startswith(family + '$') for family in families)


with zipfile.ZipFile(base) as archive:
    original = {name: archive.read(name) for name in archive.namelist() if not name.endswith('/')}
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
if not all(name == 'plugin.yml' or belongs(name) for name in changed):
    raise RuntimeError('Release changed entries outside the approved class families')
assets = {name: hashlib.sha256(data).hexdigest() for name, data in original.items()
          if name.startswith(('modelengine/', 'resource-packs/'))}
if not all(actual.get(name) == original[name] for name in assets):
    raise RuntimeError('Boss models or embedded resource packs changed')
manifest = {
    'artifact': str(jar),
    'base_jar': str(base),
    'base_sha256': hashlib.sha256(base.read_bytes()).hexdigest(),
    'sha256': hashlib.sha256(jar.read_bytes()).hexdigest(),
    'class_families': families,
    'changed_entries': changed,
    'sources': {str(file.relative_to(ROOT)): hashlib.sha256(file.read_bytes()).hexdigest() for file in sources},
    'unrelated_entries_preserved': True,
    'models_and_resource_packs_byte_identical': True,
    'asset_sha256': assets,
}
(build / 'build-manifest.json').write_text(json.dumps(manifest, indent=2), encoding='utf8')
print(f'PASS: compiled {len(sources)} sources; patched {len(changed)} entries; {len(assets)} model/pack entries byte-identical')
print(jar)
