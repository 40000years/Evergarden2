"""Verify removal of only the 21 retired wand-crafting core asset families.

Checks current module packs and explicit candidate JARs against an immutable before
snapshot. Preserves every unrelated entry, including weapon, spell and active core art.
"""
from __future__ import annotations
import argparse
import copy
import hashlib
import io
import json
from pathlib import Path
import zipfile

ROOT = Path(__file__).resolve().parents[1]
RETIRED_SUFFIXES = (
    'lightning_strike', 'frost_nova', 'shadow_step', 'natures_bloom', 'earth_wall',
    'dragons_breath', 'void_pull', 'sonic_boom', 'blaze_barrage', 'wither_ray',
    'shulker_levitation', 'meteor_strike', 'iron_armor', 'vex_legion', 'guardian_beam',
    'solar_apocalypse', 'chronos_final_hour', 'heavens_judgment', 'verdant_sovereign',
    'lunar_requiem', 'crimson_oblivion',
)
RETIRED_IDS = frozenset('advance_magic:core_' + name for name in RETIRED_SUFFIXES)
RETIRED_ICONS = frozenset('advance_magic.core_' + name for name in RETIRED_SUFFIXES)
JAVA_RETIRED = frozenset(
    f'assets/advance_magic/{folder}/core_{name}.{extension}'
    for name in RETIRED_SUFFIXES
    for folder, extension in (('items', 'json'), ('models/item', 'json'), ('textures/item', 'png'))
)
BEDROCK_RETIRED = frozenset(f'textures/items/core_{name}.png' for name in RETIRED_SUFFIXES)
SELECTOR = 'assets/minecraft/items/heart_of_the_sea.json'


def strings(value):
    if isinstance(value, str):
        yield value
    elif isinstance(value, list):
        for entry in value:
            yield from strings(entry)
    elif isinstance(value, dict):
        for key, entry in value.items():
            yield key
            yield from strings(entry)


def assert_no_retired(pack: zipfile.ZipFile):
    names = set(pack.namelist())
    assert not names & (JAVA_RETIRED | BEDROCK_RETIRED), ('Retired core files remain', names & (JAVA_RETIRED | BEDROCK_RETIRED))
    for name in names:
        if not name.endswith(('.json', '.mcmeta')):
            continue
        values = set(strings(json.loads(pack.read(name))))
        assert not values & (RETIRED_IDS | RETIRED_ICONS), ('Retired core JSON route/atlas entry remains', name, values & (RETIRED_IDS | RETIRED_ICONS))


def assert_mapping_cleanup(mapping):
    values = set(strings(mapping))
    assert not values & (RETIRED_IDS | RETIRED_ICONS), ('Retired Geyser identifiers remain', values & (RETIRED_IDS | RETIRED_ICONS))


def trimmed_mapping(mapping):
    result = copy.deepcopy(mapping)
    result['items'] = {
        base: kept for base, rows in result['items'].items()
        if (kept := [row for row in rows if row.get('model') not in RETIRED_IDS and row.get('bedrock_identifier') not in RETIRED_IDS])
    }
    return result


def without_versions(manifest):
    result = copy.deepcopy(manifest)
    result['header'].pop('version', None)
    for module in result['modules']:
        module.pop('version', None)
    return result


def trimmed_selector(document):
    result = copy.deepcopy(document)
    cases = result['model']['cases']
    result['model']['cases'] = [case for case in cases if not set(strings(case.get('when'))) & RETIRED_IDS]
    return result


def pack_comparison(before_data: bytes, after_data: bytes, bedrock: bool):
    with zipfile.ZipFile(io.BytesIO(before_data)) as before, zipfile.ZipFile(io.BytesIO(after_data)) as after:
        assert before.testzip() is None and after.testzip() is None
        assert_no_retired(after)
        old, new = set(before.namelist()), set(after.namelist())
        retired = BEDROCK_RETIRED if bedrock else JAVA_RETIRED
        assert retired <= old, ('Immutable baseline must contain all intended retired assets', retired - old)
        assert new == old - retired, ('Unexpected pack entry changes', 'removed', old - retired - new, 'added', new - old)
        allowed = {'manifest.json', 'textures/item_texture.json'} if bedrock else {SELECTOR}
        unchanged = 0
        for name in new - allowed:
            assert before.read(name) == after.read(name), ('Unrelated pack entry changed', name)
            unchanged += 1
        if bedrock:
            old_atlas = json.loads(before.read('textures/item_texture.json'))
            new_atlas = json.loads(after.read('textures/item_texture.json'))
            old_atlas['texture_data'] = {key: row for key, row in old_atlas['texture_data'].items() if key not in RETIRED_ICONS}
            assert new_atlas == old_atlas, 'Only the 21 retired atlas entries may change'
            old_manifest = json.loads(before.read('manifest.json'))
            new_manifest = json.loads(after.read('manifest.json'))
            assert tuple(new_manifest['header']['version']) > tuple(old_manifest['header']['version']), 'Bedrock cache version must increase'
            assert all(module['version'] == new_manifest['header']['version'] for module in new_manifest['modules'])
            assert without_versions(old_manifest) == without_versions(new_manifest), 'Pack UUIDs and all non-version manifest data must remain unchanged'
            version = new_manifest['header']['version']
        else:
            if SELECTOR in old:
                assert json.loads(after.read(SELECTOR)) == trimmed_selector(json.loads(before.read(SELECTOR))), 'Keep every active Heart of the Sea selector case unchanged'
            else:
                assert SELECTOR not in new, 'Do not introduce a new vanilla item override'
            version = None
        return {'removed_entries': len(old - new), 'byte_identical_entries': unchanged, 'bedrock_version': version}


def active_routes(module, mapping, java, bedrock):
    if module == 'advance-magic':
        models = ['advance_magic:' + name for name in (
            'restoration_core', 'restoration_wand', 'wand_repair', 'wand_damage', 'wand_cooldown',
            'skilltree_tab_core', 'skilltree_tab_core_on')]
    else:
        models = ['voidscape:' + name for name in ('core_of_legend', 'core_of_seraph', 'core_of_revenant')]
        models += ['voidscape:boss_orb_' + name for name in ('acheron', 'phlegethon', 'cocytus', 'banshee', 'malefeasance', 'styx')]
    definitions = {row.get('model'): row for rows in mapping['items'].values() for row in rows}
    atlas = json.loads(bedrock.read('textures/item_texture.json'))['texture_data']
    for model in models:
        assert model in definitions, ('Active core/utility/UI mapping removed', model)
        row = definitions[model]
        namespace, item = model.split(':')
        assert f'assets/{namespace}/items/{item}.json' in java.namelist(), ('Active direct Java item missing', model)
        icon = row['bedrock_options']['icon']
        assert icon in atlas, ('Active Bedrock icon missing', model)
        textures = atlas[icon]['textures']
        for texture in textures if isinstance(textures, list) else [textures]:
            assert texture + '.png' in bedrock.namelist(), ('Active Bedrock texture missing', model, texture)
    return len(models)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--before', type=Path, default=ROOT / 'output/retired-core-cleanup-20261025/before')
    parser.add_argument('--magic-jar', type=Path, default=ROOT / 'dist/advance-magic.jar')
    parser.add_argument('--garden-jar', type=Path, default=ROOT / 'dist/evergarden.jar')
    parser.add_argument('--report', type=Path)
    args = parser.parse_args()
    before = args.before.resolve()
    manifest = json.loads((before / 'manifest.json').read_text(encoding='utf8'))
    for relative, expected in manifest.items():
        if relative.startswith(('advance-magic/dist/', 'evergarden/dist/', 'dist/')):
            assert hashlib.sha256((before / relative).read_bytes()).hexdigest() == expected, ('Immutable baseline changed', relative)
    report = {'retired_ids': sorted(RETIRED_IDS), 'modules': {}}
    for module, jar in (('advance-magic', args.magic_jar), ('evergarden', args.garden_jar)):
        directory = ROOT / module / 'dist'
        old = before / module / 'dist'
        java_name, bedrock_name = module + '-java.zip', module + '-bedrock.mcpack'
        current_mapping = json.loads((directory / 'geyser-mappings.json').read_text(encoding='utf8'))
        prior_mapping = json.loads((old / 'geyser-mappings.json').read_text(encoding='utf8'))
        assert_mapping_cleanup(current_mapping)
        assert trimmed_mapping(prior_mapping) == trimmed_mapping(current_mapping), 'Unrelated Geyser definitions changed in ' + module
        if module == 'evergarden':
            assert (old / 'geyser-mappings.json').read_bytes() == (directory / 'geyser-mappings.json').read_bytes(), 'Evergarden owns no retired mappings and must remain byte-identical'
        packs = {name: pack_comparison((old / name).read_bytes(), (directory / name).read_bytes(), name.endswith('.mcpack'))
                 for name in (java_name, bedrock_name)}
        hashes = json.loads((directory / 'pack-hashes.json').read_text(encoding='utf8'))
        for name, digest in hashes.items():
            assert hashlib.sha1((directory / name).read_bytes()).hexdigest() == digest, ('Stale pack hash', module, name)
        with zipfile.ZipFile(directory / java_name) as java, zipfile.ZipFile(directory / bedrock_name) as bedrock:
            preserved_routes = active_routes(module, current_mapping, java, bedrock)
        with zipfile.ZipFile(jar) as embedded, zipfile.ZipFile(before / 'dist' / (module + '.jar')) as previous_jar:
            assert embedded.testzip() is None
            for name in (*hashes, 'geyser-mappings.json', 'pack-hashes.json'):
                assert embedded.read('resource-packs/' + name) == (directory / name).read_bytes(), ('Stale embedded artifact', module, name)
            for name in hashes:
                if name not in (java_name, bedrock_name):
                    assert previous_jar.read('resource-packs/' + name) == embedded.read('resource-packs/' + name), ('Unrelated embedded pack changed', module, name)
        report['modules'][module] = {'packs': packs, 'active_core_utility_ui_routes': preserved_routes,
            'jar': str(jar), 'jar_sha256': hashlib.sha256(jar.read_bytes()).hexdigest()}
    if args.report:
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(json.dumps(report, indent=2) + '\n', encoding='utf8')
    print('PASS: 21 retired core IDs absent from both module packs/mappings and embedded JARs; all unrelated pack entries byte-identical; active core/utility/UI routes and monotonic cache versions preserved')
    print(json.dumps(report['modules'], indent=2))


if __name__ == '__main__':
    main()
