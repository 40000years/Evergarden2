"""Build Advance Magic 1.35.0: remove the retired wand-spell engine and every pack asset only it used.

The 21 wand spells were disabled long ago (CastListener.castInput returned false; legacy wands migrate
into elemental staffs), yet their code and art still shipped. Over the 1.34.1 JAR this:

* drops 21 classes (41 class files) that only the retired spells or the never-started Longinus
  projectile used: the spell engine (SpellRegistry, Area/Projectile/Mobility/Channel/Mythic/Legendary/
  BasicSpells), their visuals, BasicSpellProfile, Geometry and LonginusProjectileService/BedrockBridge;
* recompiles only AdvanceMagicPlugin, CastListener, MagicContext, ManaService and LegendaryWeaponService,
  which referenced them. CastListener.cast(...) stays as a compatibility entry point; the api package,
  the Spell enum (legacy wand identity and staff migration) and StatusService stay;
* removes the retired-spell config sections (basic-casting, legendary, meteor, damage, follow-up, ...);
* removes the art listed in tools/retired_magic_assets.py from both packs (Bedrock 2.36.0): 53 Java and
  53 Bedrock entries, about 0.93 MB per pack and 41 MiB of decoded RGBA texture per client.

Every other entry stays byte-identical. GeyserPackCleanup's source carries an unreleased edit
(RETIRED_CORE_IDS) that no JAR ever shipped; this release keeps the shipped class.

    python tools/build_unused_cleanup_release.py [--promote]
"""
import argparse
import hashlib
import io
import json
import os
import re
import shutil
import struct
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'tools'))
import build_plugin_jars as build  # noqa: E402
import retired_magic_assets as retired  # noqa: E402

OUT = ROOT / 'output/unused-cleanup-20261027'
BASE = ROOT / 'dist/advance-magic-1.34.1-bedrock-pack-repair.jar'
BASE_SHA256 = 'a15f6dd3d685d57ef8e20979236644110ccc97d53a1e242750795284f7879965'
GARDEN = ROOT / 'dist/evergarden.jar'
VERSION = '1.35.0-unused-cleanup'
BEDROCK_VERSION = [2, 36, 0]
DATE = (2026, 10, 27, 0, 0, 0)
PACKS = ('advance-magic-java.zip', 'advance-magic-bedrock.mcpack', 'geyser-mappings.json', 'pack-hashes.json')
PKG = 'com/example/advancemagic/'
OWNED = tuple(PKG + n for n in ('AdvanceMagicPlugin', 'CastListener', 'spell/MagicContext', 'mana/ManaService',
                                'item/LegendaryWeaponService'))
REMOVED = tuple(PKG + n for n in (
    'spell/SpellRegistry', 'spell/AreaSpells', 'spell/ProjectileSpells', 'spell/MobilitySpells', 'spell/ChannelSpells',
    'spell/MythicSpells', 'spell/LegendarySpells', 'spell/BasicSpells', 'spell/BasicSpellVisuals',
    'spell/BasicSpellProfile', 'spell/LegendarySpellVisuals', 'spell/SolarLineVisuals', 'spell/ChronosLineVisuals',
    'spell/CrystalBeamVisuals', 'spell/JudgmentBeamVisuals', 'spell/JudgmentSealVisuals',
    'spell/LegendaryCastGeometry', 'spell/LegendaryEarthHand', 'effect/Geometry',
    'item/LonginusProjectileService', 'item/LonginusBedrockBridge'))
# Source differs from every shipped JAR (unreleased edit); the shipped class is kept as is.
KNOWN_DRIFT = {PKG + 'pack/GeyserPackCleanup.class'}


def sha256(data):
    return hashlib.sha256(data).hexdigest()


def family(name, families):
    return any(name == f + '.class' or name.startswith(f + '$') for f in families)


def read_zip(data):
    with zipfile.ZipFile(io.BytesIO(data)) as archive:
        infos = [i for i in archive.infolist() if not i.is_dir()]
        return {i.filename: archive.read(i) for i in infos}, infos


def write_zip(files, infos):
    """Rewrite kept entries in their original order and metadata, recompressed like the source packs."""
    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, 'w', zipfile.ZIP_DEFLATED) as out:
        for info in infos:
            if info.filename in files:
                item = zipfile.ZipInfo(info.filename, info.date_time)
                item.external_attr = info.external_attr
                item.create_system = info.create_system
                out.writestr(item, files[info.filename], compress_type=info.compress_type, compresslevel=9)
    return buffer.getvalue()


def clean_pack(name, data):
    files, infos = read_zip(data)
    if name.endswith('.zip'):
        removed = set(retired.java_removals(files))
    else:
        removed = set(retired.bedrock_removals(files))
    problems = retired.dangling(files, removed)
    if problems:
        raise RuntimeError(f'{name}: kept entries still reference removed resources: {problems[:10]}')
    kept = {k: v for k, v in files.items() if k not in removed}
    changed = []
    if name.endswith('.mcpack'):
        manifest = json.loads(kept['manifest.json'])
        if manifest['header']['version'] >= BEDROCK_VERSION:
            raise RuntimeError('Bedrock version must advance from ' + str(manifest['header']['version']))
        manifest['header']['version'] = BEDROCK_VERSION
        for module in manifest['modules']:
            module['version'] = BEDROCK_VERSION
        kept['manifest.json'] = json.dumps(manifest, ensure_ascii=False, separators=(',', ':')).encode('utf8')
        changed.append('manifest.json')
    result = write_zip(kept, infos)
    again, _ = read_zip(result)
    if set(again) != set(kept) or any(again[k] != kept[k] for k in kept):
        raise RuntimeError(name + ': staged pack differs from the intended entries')
    if any(again[k] != files[k] for k in kept if k not in changed):
        raise RuntimeError(name + ': an unrelated entry changed')
    raw = sum(struct.unpack('>II', files[p][16:24])[0] * struct.unpack('>II', files[p][16:24])[1] * 4
              for p in removed if p.endswith('.png'))
    return result, {'removed': sorted(removed), 'modified': changed, 'kept_entries': len(kept),
                    'bytes': {'before': len(data), 'after': len(result)}, 'decoded_rgba_removed': raw}


# --- class-file linkage -------------------------------------------------------------------------

def parse_class(data):
    """(this, super, referenced classes, referenced members, declared members) of one class file."""
    pool, i, count, k = [None], 10, struct.unpack('>H', data[8:10])[0], 1
    while k < count:
        tag = data[i]
        if tag == 1:
            n = struct.unpack('>H', data[i + 1:i + 3])[0]
            pool.append(('utf8', data[i + 3:i + 3 + n].decode('utf8', 'replace'))); i += 3 + n
        elif tag in (3, 4):
            pool.append(('num',)); i += 5
        elif tag in (5, 6):
            pool += [('num',), None]; i += 9; k += 1
        elif tag in (7, 8, 16, 19, 20):
            pool.append((tag, struct.unpack('>H', data[i + 1:i + 3])[0])); i += 3
        elif tag in (9, 10, 11, 12, 17, 18):
            pool.append((tag, *struct.unpack('>HH', data[i + 1:i + 5]))); i += 5
        elif tag == 15:
            pool.append((tag,)); i += 4
        else:
            raise ValueError('Unknown constant tag %d' % tag)
        k += 1
    utf = lambda index: pool[index][1]
    classes = {utf(e[1]) for e in pool if e and e[0] == 7}
    members = set()
    for e in pool:
        if e and e[0] in (9, 10, 11):
            nat = pool[e[2]]
            members.add(('field' if e[0] == 9 else 'method', utf(pool[e[1]][1]), utf(nat[1]), utf(nat[2])))
    this_index, super_index = struct.unpack('>HH', data[i + 2:i + 6])
    this, parent = utf(pool[this_index][1]), (utf(pool[super_index][1]) if super_index else None)
    i += 6
    interfaces = struct.unpack('>H', data[i:i + 2])[0]; i += 2 + 2 * interfaces
    declared = set()
    for kind in ('field', 'method'):
        n = struct.unpack('>H', data[i:i + 2])[0]; i += 2
        for _ in range(n):
            _, name, desc, attrs = struct.unpack('>HHHH', data[i:i + 8]); i += 8
            declared.add((kind, utf(name), utf(desc)))
            for _ in range(attrs):
                length = struct.unpack('>I', data[i + 2:i + 6])[0]; i += 6 + length
    return this, parent, classes, members, declared


def index_classes(classes):
    table = {}
    for data in classes.values():
        this, parent, _, _, declared = parse_class(data)
        table[this] = (parent, declared)
    return table


def resolves(table, owner, kind, name, desc):
    """True when the member is declared by `owner` or a plugin superclass of it."""
    while owner in table:
        parent, declared = table[owner]
        if (kind, name, desc) in declared:
            return True
        owner = parent
    return False


def linkage(before, after, consumers):
    """References into the plugin that resolved in the base JAR but no longer resolve in the release."""
    problems = []
    for source, data in consumers.items():
        _, _, classes, members, _ = parse_class(data)
        for cls in classes:
            target = cls.lstrip('[').removeprefix('L').removesuffix(';')
            if target.startswith(PKG) and target in before and target not in after:
                problems.append(f'{source} -> removed class {target}')
        for kind, owner, name, desc in members:
            if owner.startswith(PKG) and resolves(before, owner, kind, name, desc) and not resolves(after, owner, kind, name, desc):
                problems.append(f'{source} -> removed {kind} {owner}.{name}{desc}')
    return problems


# --- build --------------------------------------------------------------------------------------

def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--promote', action='store_true')
    args = parser.parse_args()
    OUT.mkdir(parents=True, exist_ok=True)
    release_dir = OUT / 'release'
    release_dir.mkdir(exist_ok=True)
    jar_path = release_dir / 'advance-magic.jar'
    if args.promote:
        return promote(jar_path)
    base = BASE.read_bytes()
    if sha256(base) != BASE_SHA256:
        raise RuntimeError('Base JAR changed: ' + str(BASE))
    if sha256((ROOT / 'dist/advance-magic.jar').read_bytes()) != BASE_SHA256:
        raise RuntimeError('dist/advance-magic.jar is no longer 1.34.1; rebase this release first')
    base_files, base_infos = read_zip(base)

    # Packs.
    report = {'version': VERSION, 'base_sha256': BASE_SHA256, 'packs': {}}
    packs = {}
    for name in PACKS[:2]:
        packs[name], report['packs'][name] = clean_pack(name, base_files['resource-packs/' + name])
    mappings = json.loads(base_files['resource-packs/geyser-mappings.json'])
    ids = {'advance_magic:' + n for n in retired.NAMES}
    stale = [row for rows in mappings.get('items', {}).values() for row in rows
             if row.get('model') in ids or row.get('bedrock_identifier') in ids]
    if stale:
        raise RuntimeError('Geyser mappings still define retired items: ' + str(stale))
    packs['geyser-mappings.json'] = base_files['resource-packs/geyser-mappings.json']
    hashes_raw = base_files['resource-packs/pack-hashes.json']
    hashes = json.loads(hashes_raw)
    for name in PACKS[:2]:
        hashes[name] = hashlib.sha1(packs[name]).hexdigest()
    packs['pack-hashes.json'] = (json.dumps(hashes, indent=2) + ('\n' if hashes_raw.endswith(b'\n') else '')).encode()

    # Classes: compile the whole tree so a dangling reference fails here, then take only owned classes.
    classes_dir = OUT / 'classes'
    build.OUT = OUT
    sources = sorted((ROOT / 'advance-magic/src/main/java').rglob('*.java'))
    build.compile_sources('advance-magic', sources, build.pinned_classpath(build.ADVANCE_ARGS), classes_dir)
    compiled = {p.relative_to(classes_dir).as_posix(): p.read_bytes() for p in classes_dir.rglob('*.class')}
    base_classes = {n: d for n, d in base_files.items() if n.endswith('.class')}
    if any(family(n, REMOVED) for n in compiled):
        raise RuntimeError('A removed class is still compiled from source')
    gone = sorted(n for n in base_classes if family(n, REMOVED))
    for name, data in compiled.items():
        if family(name, OWNED):
            continue
        if name not in base_classes:
            raise RuntimeError('Unowned class appeared in source: ' + name)
        if data != base_classes[name] and name not in KNOWN_DRIFT:
            raise RuntimeError('Unowned class differs from the shipped JAR: ' + name)
    missing = sorted(n for n in base_classes if not family(n, REMOVED) and not family(n, OWNED) and n not in compiled)
    if missing:
        raise RuntimeError('Shipped classes without a source: ' + ', '.join(missing))
    owned = {n: d for n, d in compiled.items() if family(n, OWNED)}

    # Resources.
    plugin = base_files['plugin.yml'].decode('utf8').splitlines(keepends=True)
    index = next(i for i, line in enumerate(plugin) if line.startswith('version: '))
    plugin[index] = f"version: '{VERSION}'" + plugin[index][len(plugin[index].rstrip('\r\n')):]
    config = (ROOT / 'advance-magic/src/main/resources/config.yml').read_bytes()
    if b'basic-casting:' in config or b'\ndamage:' in config or b'mythic-terrain:' not in config:
        raise RuntimeError('Unexpected release config.yml')
    replacements = {'plugin.yml': ''.join(plugin).encode('utf8'), 'config.yml': config}
    replacements.update({'resource-packs/' + n: packs[n] for n in PACKS})

    # Assemble over the base JAR order.
    with zipfile.ZipFile(jar_path, 'w', zipfile.ZIP_DEFLATED) as out:
        written = set()
        for info in base_infos:
            name = info.filename
            if family(name, REMOVED) or family(name, OWNED):
                continue
            if name in replacements:
                out.writestr(zipfile.ZipInfo(name, DATE), replacements[name],
                             compress_type=zipfile.ZIP_STORED if name.endswith('.mcpack') else zipfile.ZIP_DEFLATED)
            else:
                out.writestr(info, base_files[name])
            written.add(name)
        for name in sorted(owned):
            out.writestr(zipfile.ZipInfo(name, DATE), owned[name])
    build.check_jar(jar_path)
    release, _ = read_zip(jar_path.read_bytes())
    for name, data in base_files.items():
        if family(name, REMOVED):
            if name in release:
                raise RuntimeError('Removed class shipped: ' + name)
        elif family(name, OWNED) or name in replacements:
            continue
        elif release.get(name) != data:
            raise RuntimeError('Unrelated entry changed: ' + name)
    release_classes = {n: d for n, d in release.items() if n.endswith('.class')}
    before, after = index_classes(base_classes), index_classes(release_classes)
    problems = linkage(before, after, release_classes)
    with zipfile.ZipFile(GARDEN) as garden:
        garden_classes = {'evergarden:' + n: garden.read(n) for n in garden.namelist() if n.endswith('.class')}
    problems += linkage(before, after, garden_classes)
    if problems:
        raise RuntimeError('Linkage problems:\n' + '\n'.join(problems[:40]))
    report.update({
        'sha256': sha256(jar_path.read_bytes()), 'jar_bytes': {'before': len(base), 'after': jar_path.stat().st_size},
        'bedrock_version': BEDROCK_VERSION, 'removed_classes': gone, 'owned_classes': sorted(owned),
        'kept_shipped_drift': sorted(KNOWN_DRIFT), 'evergarden_sha256': sha256(GARDEN.read_bytes()),
        'linkage': {'advance_magic_classes': len(release_classes), 'evergarden_classes': len(garden_classes), 'problems': 0},
        'sources': {p.relative_to(ROOT).as_posix(): sha256(p.read_bytes()) for p in sources}, 'promoted': False})
    (OUT / 'release-build.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf8')
    summary = {k: report[k] for k in ('version', 'sha256', 'jar_bytes', 'bedrock_version')}
    summary['packs'] = {n: {'removed': len(v['removed']), **v['bytes']} for n, v in report['packs'].items()}
    summary['removed_class_files'] = len(gone)
    print(json.dumps(summary, indent=2))


def promote(jar_path):
    report = json.loads((OUT / 'release-build.json').read_text(encoding='utf8'))
    data = jar_path.read_bytes()
    if sha256(data) != report['sha256']:
        raise RuntimeError('Release candidate changed since it was built')
    if sha256((ROOT / 'dist/advance-magic.jar').read_bytes()) != BASE_SHA256:
        raise RuntimeError('Concurrent release changed dist/advance-magic.jar')
    for path, digest in report['sources'].items():
        if sha256((ROOT / path).read_bytes()) != digest:
            raise RuntimeError('Source changed since the build: ' + path)
    gates = json.loads((OUT / 'gates.json').read_text(encoding='utf8'))
    if gates.get('candidate_sha256') != report['sha256'] or not gates.get('passed'):
        raise RuntimeError('Gates were not recorded as passing for this candidate')
    dist = ROOT / 'advance-magic/dist'
    with zipfile.ZipFile(jar_path) as release:
        for name in PACKS:
            staged = dist / (name + '.staged')
            staged.write_bytes(release.read('resource-packs/' + name))
            os.replace(staged, dist / name)
    source = ROOT / 'advance-magic/src/main/resources/plugin.yml'
    text = source.read_bytes().decode('utf8').splitlines(keepends=True)
    k = next(j for j, line in enumerate(text) if line.startswith('version: '))
    text[k] = f"version: '{VERSION}'" + text[k][len(text[k].rstrip('\r\n')):]
    source.write_bytes(''.join(text).encode('utf8'))
    for target in (ROOT / 'dist/advance-magic.jar', ROOT / 'dist' / f'advance-magic-{VERSION}.jar'):
        staged = target.with_suffix('.jar.staged')
        shutil.copy2(jar_path, staged)
        os.replace(staged, target)
    report['promoted'] = True
    (OUT / 'release-build.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf8')
    print('Promoted', VERSION, report['sha256'])


if __name__ == '__main__':
    main()
