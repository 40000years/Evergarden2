"""Build Evergarden e2.153: Malefeasance remodel, slower blinks, lighter Arkhaia ritual, mace-friendly vaults.

* Malefeasance, both forms, re-sculpted by evergarden/tools/malefeasance_v4.py on the same bones and
  17 clips (Bedrock content 3.61.0 via tools/update_river_bedrock.py).
* Malefeasance blinks after a 16-tick portal warning, then waits 150 ticks (was 6 and 42).
* Arkhaia's river ritual summons spirits at 50% HP and 75% damage (arkhaia.ritual.minion-health /
  minion-damage); Phlegethon's same-weapon adaptation stops at half damage there. Sanctuary bosses
  are unchanged. Scaled HP keeps Acheron splits, Styx enrage and Malefeasance's bar proportional.
* Vault temples no longer teleport players back to the floor: jumping, wind charges and maces are
  allowed anywhere above the floor inside the ring. Players on pillars/arches, under the floor or
  outside radius 21.5 cannot hurt the encounter, and it resets after 15 seconds with nobody inside
  (structures.minor.leave-reset-seconds). Mobs leaving the ring still return to the centre.

Over dist/evergarden.jar this recompiles only the owned classes and replaces the two Malefeasance
blueprints, the Bedrock pack, pack-hashes.json, judge-design.json and plugin.yml.

    python tools/build_boss_balance_release.py [--base JAR] [--promote]
"""
import argparse
import hashlib
import json
import os
import shutil
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'tools'))
import build_plugin_jars as build  # noqa: E402

OUT = ROOT / 'output/boss-balance-20261026'
VERSION = '3.0.0-e2.153-boss-balance'
DATE = (2026, 10, 26, 0, 0, 0)
OWNED = ('com/example/voidscape/boss/MalefeasanceEncounter', 'com/example/voidscape/boss/ArkhaiaMinions',
         'com/example/voidscape/boss/ArkhaiaBoss', 'com/example/voidscape/boss/RiverTrials',
         'com/example/voidscape/dungeon/DungeonManager')
RES = ROOT / 'evergarden/src/main/resources'
FILES = {
    'modelengine/rivers/arkhaia_malefeasance.bbmodel': RES / 'modelengine/rivers/arkhaia_malefeasance.bbmodel',
    'modelengine/rivers/arkhaia_malefeasance_phase2.bbmodel': RES / 'modelengine/rivers/arkhaia_malefeasance_phase2.bbmodel',
    'resource-packs/evergarden-bedrock.mcpack': ROOT / 'evergarden/dist/evergarden-bedrock.mcpack',
    'resource-packs/pack-hashes.json': ROOT / 'evergarden/dist/pack-hashes.json',
    'judge-design.json': RES / 'judge-design.json',
}


def sha(data):
    return hashlib.sha256(data).hexdigest()


def replaced(name):
    return any(name == p + '.class' or name.startswith(p + '$') for p in OWNED)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--base', type=Path, default=ROOT / 'dist/evergarden.jar')
    parser.add_argument('--promote', action='store_true')
    args = parser.parse_args()
    OUT.mkdir(parents=True, exist_ok=True)
    base = OUT / 'base.jar'
    shutil.copy2(args.base, base)
    work = OUT / 'build'
    shutil.rmtree(work, ignore_errors=True)
    work.mkdir(parents=True)
    build.OUT = work
    classes = work / 'classes'
    sources = [ROOT / 'evergarden/src/main/java' / (p + '.java') for p in OWNED]
    build.compile_sources('boss-balance', sources, [base.as_posix(), (ROOT / 'dist/advance-magic.jar').as_posix()]
                          + build.pinned_classpath(build.EVERGARDEN_ARGS), classes)
    with zipfile.ZipFile(base) as jar:
        lines = jar.read('plugin.yml').decode('utf8').splitlines(keepends=True)
        names = set(jar.namelist())
    if not all(name in names for name in FILES):
        raise RuntimeError('Base JAR lacks an expected entry')
    i = next(k for k, line in enumerate(lines) if line.startswith('version: '))
    lines[i] = f"version: '{VERSION}'" + lines[i][len(lines[i].rstrip('\r\n')):]
    replacements = {name: path.read_bytes() for name, path in FILES.items()}
    replacements['plugin.yml'] = ''.join(lines).encode('utf8')
    design = json.loads(replacements['judge-design.json'])
    with zipfile.ZipFile(io_bytes(replacements['resource-packs/evergarden-bedrock.mcpack'])) as pack:
        if '.'.join(map(str, json.loads(pack.read('manifest.json'))['header']['version'])) != design['bedrock-version']:
            raise RuntimeError('judge-design.json and the Bedrock pack disagree on the version')
    jar_path = work / 'evergarden.jar'
    with zipfile.ZipFile(base) as previous, zipfile.ZipFile(jar_path, 'w', zipfile.ZIP_DEFLATED) as result:
        for entry in previous.infolist():
            if replaced(entry.filename) or entry.filename in replacements:
                continue
            result.writestr(entry, previous.read(entry.filename))
        for path in sorted(classes.rglob('*.class')):
            name = path.relative_to(classes).as_posix()
            if not replaced(name):
                raise RuntimeError('Unexpected class ' + name)
            result.writestr(zipfile.ZipInfo(name, DATE), path.read_bytes(), compress_type=zipfile.ZIP_DEFLATED)
        for name, data in replacements.items():
            result.writestr(zipfile.ZipInfo(name, DATE), data,
                            compress_type=zipfile.ZIP_STORED if name.endswith(('.zip', '.mcpack')) else zipfile.ZIP_DEFLATED)
    with zipfile.ZipFile(jar_path) as release, zipfile.ZipFile(base) as previous:
        if release.testzip() is not None or len(release.namelist()) != len(set(release.namelist())):
            raise RuntimeError('Invalid release archive')
        for info in previous.infolist():
            if not replaced(info.filename) and info.filename not in replacements and release.read(info.filename) != previous.read(info.filename):
                raise RuntimeError('Unrelated entry changed: ' + info.filename)
    build.check_jar(jar_path)
    digest = sha(jar_path.read_bytes())
    if args.promote:
        source = RES / 'plugin.yml'
        text = source.read_bytes().decode('utf8').splitlines(keepends=True)
        k = next(j for j, line in enumerate(text) if line.startswith('version: '))
        text[k] = f"version: '{VERSION}'" + text[k][len(text[k].rstrip('\r\n')):]
        source.write_bytes(''.join(text).encode('utf8'))
        for target in (ROOT / 'dist/evergarden.jar', ROOT / 'dist' / ('evergarden-' + VERSION + '.jar')):
            staged = target.with_suffix('.jar.staged')
            shutil.copy2(jar_path, staged)
            os.replace(staged, target)
    report = {'version': VERSION, 'sha256': digest, 'baseline_sha256': sha(base.read_bytes()),
              'bedrock_version': design['bedrock-version'], 'promoted': args.promote}
    (OUT / 'release-build.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf8')
    print(json.dumps(report, indent=2))


def io_bytes(data):
    import io
    return io.BytesIO(data)


if __name__ == '__main__':
    main()
