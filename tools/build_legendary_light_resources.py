"""Patch only Legendary Light artwork and definitions into the current packs.

Unlike the full pack builder, this preserves the bytes of every existing
unrelated resource, all 58 authored scroll icons, and the Geyser mappings.
"""
from __future__ import annotations

import ast
import hashlib
import json
import shutil
import tempfile
import zipfile
from pathlib import Path

from legendary_light_assets import (PLANES, RAYS, TEXTURES, MOTE_TEXTURE, MOTE_PARTICLE,
                                   MOTES_PER_FRAME, PARTICLE_LIFETIME, register_bedrock,
                                   register_java, source_texture)

ROOT = Path(__file__).resolve().parents[1]
DIST = ROOT / 'advance-magic/dist'
OUTPUT = ROOT / 'output/legendary-light-effects'
PACKS = {'java': 'advance-magic-java.zip', 'bedrock': 'advance-magic-bedrock.mcpack'}


def encode_json(value):
    return (json.dumps(value, ensure_ascii=False, separators=(',', ':')) + '\n').encode('utf8')


def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(encode_json(value))


def read_zip(path):
    with zipfile.ZipFile(path) as archive:
        names = archive.namelist()
        if len(names) != len(set(names)):
            raise ValueError(f'Duplicate ZIP entries: {path}')
        if (bad := archive.testzip()) is not None:
            raise ValueError(f'Corrupt ZIP entry {bad}: {path}')
        return {name: archive.read(name) for name in names}


def stage_zip(path, values):
    temporary = path.with_name(path.name + '.legendary-light.tmp')
    with zipfile.ZipFile(path) as old, zipfile.ZipFile(temporary, 'w', zipfile.ZIP_DEFLATED, compresslevel=9) as new:
        existing = set()
        for info in old.infolist():
            new.writestr(info, values[info.filename], compress_type=zipfile.ZIP_DEFLATED, compresslevel=9)
            existing.add(info.filename)
        for name in sorted(set(values) - existing):
            info = zipfile.ZipInfo(name, (2026, 10, 23, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            new.writestr(info, values[name], compress_type=zipfile.ZIP_DEFLATED, compresslevel=9)
    if read_zip(temporary) != values:
        temporary.unlink(missing_ok=True)
        raise ValueError(f'Pack verification failed: {path}')
    return temporary


def desired_version():
    tree = ast.parse((ROOT / 'advance-magic/tools/build_packs.py').read_text(encoding='utf8'))
    for node in tree.body:
        if isinstance(node, ast.Assign) and any(isinstance(target, ast.Name) and target.id == 'BEDROCK_PACK_VERSION'
                                               for target in node.targets):
            version = ast.literal_eval(node.value)
            if len(version) != 3 or version < [2, 28, 0]:
                raise ValueError(f'Unexpected Legendary Light resource version: {version}')
            return version
    raise ValueError('The pack builder does not declare BEDROCK_PACK_VERSION')


def expected_paths(side):
    paths = set()
    for name in PLANES + RAYS:
        if side == 'java':
            paths.update((f'assets/advance_magic/textures/effect/{name}.png',
                          f'assets/advance_magic/models/effect/{name}.json',
                          f'assets/advance_magic/items/{name}.json'))
        else:
            paths.update((f'textures/particle/{name}.png', f'particles/{name}.particle.json'))
            if name in PLANES:
                paths.add(f'particles/{name}_flat.particle.json')
    if side == 'bedrock':
        paths.update((f'textures/particle/{MOTE_TEXTURE}.png', f'particles/{MOTE_PARTICLE}.particle.json'))
    return paths


def validate_generated(java, bedrock, sources):
    for name, metadata in sources.items():
        texture = metadata['bytes']
        if name != MOTE_TEXTURE and java[f'assets/advance_magic/textures/effect/{name}.png'] != texture:
            raise ValueError(f'Java source texture changed: {name}')
        if bedrock[f'textures/particle/{name}.png'] != texture:
            raise ValueError(f'Bedrock source texture changed: {name}')
        if name == MOTE_TEXTURE:
            continue
        model = json.loads(java[f'assets/advance_magic/models/effect/{name}.json'])
        definition = json.loads(java[f'assets/advance_magic/items/{name}.json'])
        element = model['elements'][0]
        faces = {'north', 'south'} if name in PLANES else {'north', 'south', 'east', 'west'}
        if (model['ambientocclusion'] or element['shade'] or set(element['faces']) != faces
                or model['textures']['line'] != f'advance_magic:effect/{name}'
                or definition['model']['model'] != f'advance_magic:effect/{name}'):
            raise ValueError(f'Java double-sided plane definition mismatch: {name}')
        for suffix in (('', '_flat') if name in PLANES else ('',)):
            effect = json.loads(bedrock[f'particles/{name}{suffix}.particle.json'])['particle_effect']
            description, components = effect['description'], effect['components']
            billboard = components['minecraft:particle_appearance_billboard']
            uv = billboard['uv']
            if description['identifier'] != f'advance_magic:{name}{suffix}':
                raise ValueError(f'Bedrock particle identifier mismatch: {name}{suffix}')
            if description['basic_render_parameters'] != {
                'material': 'particles_blend' if name in PLANES else 'particles_add',
                'texture': f'textures/particle/{name}',
            }:
                raise ValueError(f'Bedrock blend material mismatch: {name}{suffix}')
            expected_uv = {'texture_width': metadata['width'], 'texture_height': metadata['height'],
                           'uv': [0, 0], 'uv_size': [metadata['width'], metadata['height']]}
            if uv != expected_uv:
                raise ValueError(f'Bedrock texture UV dimensions mismatch: {name}{suffix}')
            expected_mode = 'emitter_transform_xz' if suffix else ('direction_z' if name in PLANES else 'direction_y')
            if (billboard['facing_camera_mode'] != expected_mode
                    or components['minecraft:particle_lifetime_expression']['max_lifetime'] != PARTICLE_LIFETIME):
                raise ValueError(f'Bedrock plane orientation or lifetime mismatch: {name}{suffix}')
            if suffix and 'direction' in billboard:
                raise ValueError(f'Flat particle still has ambiguous up-vector projection: {name}')
            if components['minecraft:particle_initial_spin'] != {
                'rotation': 'variable.line_rotation' if name in PLANES else 0,
                'rotation_rate': 'variable.line_rotation_rate' if name in PLANES else 0,
            }:
                raise ValueError(f'Bedrock continuous spin variables mismatch: {name}{suffix}')
    mote = json.loads(bedrock[f'particles/{MOTE_PARTICLE}.particle.json'])['particle_effect']
    components = mote['components']
    billboard = components['minecraft:particle_appearance_billboard']
    if (mote['description']['identifier'] != f'advance_magic:{MOTE_PARTICLE}'
            or mote['description']['basic_render_parameters'] != {
                'material': 'particles_blend', 'texture': f'textures/particle/{MOTE_TEXTURE}'}
            or components['minecraft:emitter_rate_instant']['num_particles'] != MOTES_PER_FRAME
            or components['minecraft:particle_lifetime_expression']['max_lifetime'] != '0.65 + variable.particle_random_4 * 0.4'
            or billboard['facing_camera_mode'] != 'lookat_xyz'
            or billboard['uv'] != {'texture_width': 64, 'texture_height': 64, 'uv': [0, 0], 'uv_size': [64, 64]}):
        raise ValueError('Bedrock sparse mote emitter contract mismatch')
    positions = components['minecraft:particle_motion_parametric']['relative_position']
    for variable in ('line_offset_x', 'line_offset_y', 'line_offset_z', 'line_width', 'line_height', 'particle_age'):
        if f'variable.{variable}' not in ' '.join(positions):
            raise ValueError(f'Bedrock mote field coordinates missing variable.{variable}')
    if positions[1] != 'variable.line_offset_y + 0.25 + variable.particle_random_3 * variable.line_height + variable.particle_age * 0.42':
        raise ValueError('Bedrock mote origin must be the floor, with no half-height subtraction')
    if 'variable.light_intensity' not in json.dumps(components['minecraft:particle_appearance_tinting']):
        raise ValueError('Bedrock mote intensity must follow the runtime opening and closing')


def main():
    sources = {}
    for name in TEXTURES:
        path, width, height = source_texture(name)
        data = path.read_bytes()
        sources[name] = {'width': width, 'height': height, 'sha256': hashlib.sha256(data).hexdigest(), 'bytes': data}
    if len({data['sha256'] for data in sources.values()}) != len(TEXTURES):
        raise ValueError('All Legendary Light textures must be distinct')
    version = desired_version()
    mappings_path = DIST / 'geyser-mappings.json'
    mappings_before = mappings_path.read_bytes()
    OUTPUT.mkdir(parents=True, exist_ok=True)
    report = {'bedrock_version': version, 'artwork': {
        name: {key: value for key, value in data.items() if key != 'bytes'} for name, data in sources.items()
    }, 'particle_lifetime_seconds': PARTICLE_LIFETIME, 'rotation_rate_unit': 'degrees/second',
        'motes': {'identifier': MOTE_PARTICLE, 'origin': 'floor', 'line_width': 'full diameter',
                  'line_height': 'upward field height', 'particles_per_frame': MOTES_PER_FRAME,
                  'lifetime_seconds': [.65, 1.05], 'palette_alpha': 'runtime light_intensity with short fade'},
        'packs': {}}
    # Render this effect collection only, never regenerate existing packs.
    target = ROOT / 'advance-magic/target'
    target.mkdir(exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='legendary-light-', dir=target) as temp:
        folders = {side: Path(temp) / side for side in PACKS}
        register_java(folders['java'], write_json)
        register_bedrock(folders['bedrock'], write_json)
        generated = {side: {path.relative_to(folder).as_posix(): path.read_bytes()
                            for path in folder.rglob('*') if path.is_file()}
                     for side, folder in folders.items()}
    for side, values in generated.items():
        if set(values) != expected_paths(side):
            raise ValueError(f'Unexpected generated {side} resource files')
    validate_generated(generated['java'], generated['bedrock'], sources)
    pending = []
    for side, filename in PACKS.items():
        path = DIST / filename
        old = read_zip(path)
        for name in PLANES:
            texture_path = (f'assets/advance_magic/textures/effect/{name}.png' if side == 'java'
                            else f'textures/particle/{name}.png')
            if old.get(texture_path) != sources[name]['bytes']:
                raise ValueError(f'Accepted original Legendary Light artwork must stay byte-identical: {side}/{name}')
        new = {**old, **generated[side]}
        allowed = expected_paths(side)
        if side == 'bedrock':
            manifest = json.loads(old['manifest.json'])
            old_versions = [manifest['header']['version']] + [module['version'] for module in manifest['modules']]
            if any(current > version for current in old_versions):
                raise ValueError(f'Bedrock version cannot decrease: {old_versions} -> {version}')
            manifest['header']['version'] = version
            for module in manifest['modules']:
                module['version'] = version
            new['manifest.json'] = encode_json(manifest)
            allowed.add('manifest.json')
        untouched = set(old) - allowed
        if not set(old).issubset(new) or any(old[name] != new[name] for name in untouched):
            raise ValueError(f'Unrelated {side} pack files changed')
        prefix = 'assets/advance_magic/textures/item/scroll_' if side == 'java' else 'textures/items/scroll_'
        scrolls = {name: data for name, data in old.items() if name.startswith(prefix) and name.endswith('.png')}
        if len(scrolls) != 58 or any(new[name] != data for name, data in scrolls.items()):
            raise ValueError(f'The 58 authored scroll textures must be preserved: {side}')
        report['packs'][side] = {
            'file': filename, 'unchanged_other_entries': len(untouched), 'preserved_scroll_textures': len(scrolls),
            'preserved_accepted_light_textures': len(PLANES),
            'added_entries': sorted(set(new) - set(old)),
            'replaced_entries': sorted(name for name in allowed & set(old) if old[name] != new[name]),
        }
        pending.append((path, new))
    staged = []
    try:
        # Stage and verify both complete archives before replacing either one.
        for path, values in pending:
            staged.append((path, stage_zip(path, values)))
        baseline = OUTPUT / 'baseline'
        baseline.mkdir(exist_ok=True)
        for path in [DIST / name for name in PACKS.values()] + [mappings_path, DIST / 'pack-hashes.json']:
            backup = baseline / path.name
            if not backup.exists():
                shutil.copy2(path, backup)
        for path, temporary in staged:
            temporary.replace(path)
    finally:
        for _, temporary in staged:
            temporary.unlink(missing_ok=True)
    if mappings_path.read_bytes() != mappings_before:
        raise ValueError('Geyser mappings changed unexpectedly')
    actual = {side: read_zip(DIST / filename) for side, filename in PACKS.items()}
    validate_generated(actual['java'], actual['bedrock'], sources)
    report['geyser_mappings_unchanged'] = True
    report['sha1'] = {filename: hashlib.sha1((DIST / filename).read_bytes()).hexdigest() for filename in PACKS.values()}
    (DIST / 'pack-hashes.json').write_text(json.dumps(report['sha1'], indent=2) + '\n', encoding='utf8')
    (OUTPUT / 'resource-validation.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf8')
    print(f'Validated {len(TEXTURES)} active RGBA textures, Java planes/rays and {len(PLANES)*2+len(RAYS)+1} dimension-matched Bedrock particles.')
    print('Both packs preserve every unrelated resource and all 58 scroll textures; Geyser mappings unchanged.')
    print('Bedrock version: ' + '.'.join(map(str, version)))
    print(json.dumps(report['sha1'], indent=2))


if __name__ == '__main__':
    main()
