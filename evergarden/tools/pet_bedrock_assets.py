"""Native Bedrock entities for the 32 wild pets (96 growth variants), and form portraits.

Reuses the boss exporter: a Java carrier is replaced by Geyser with the custom
entity ``voidscape:everpet_<species>_<stage>``. The server only names the clip
(idle/walk/fly/sit/greet) through an entity property when it changes; unlike the
bosses, pets need no frame sync, so each client plays and loops the clip on its
own clock (scaled by the 'rate' property, sent only when the pace changes)
and no property traffic flows while a pet keeps doing the same thing.
Each growth stage has its own hand-painted texture (pet_paint). Pet forms show
textures/menu/pet_<species>_<stage>; the menagerie species' portraits come from
pet_portraits (run it first), the original ones from Advance Magic's pack.

Run directly to patch evergarden/dist/evergarden-bedrock.mcpack in place (other
entries untouched, content version bumped); judge_assets.build() also includes
these files on a full rebuild.
"""
import hashlib
import json
from pathlib import Path
import zipfile

from arkhaia_bedrock_assets import export_model
from pet_portraits import bedrock_textures

ROOT = Path(__file__).resolve().parents[2]
PETS = ROOT / 'evergarden/src/main/resources/modelengine/pets'
DIST = ROOT / 'evergarden/dist'
MYTHICAL = {'aetherius', 'ignis_draconis', 'zephyros', 'solaris', 'fenrir'}
CLIPS = ['idle', 'walk', 'fly', 'sit', 'greet']


def encode(value):
    return (json.dumps(value, separators=(',', ':')) + '\n').encode()


def assets():
    files = {}
    for path in sorted(PETS.glob('everpet_*.bbmodel')):
        model = json.loads(path.read_text(encoding='utf8'))
        species, stage = model['meta']['evergarden_pet_species'], model['meta']['evergarden_pet_stage']
        if species in MYTHICAL:
            continue
        # The controller maps property index -> clip by position; the server uses this order.
        assert [clip['name'] for clip in model['animations']] == CLIPS, path.name
        model_id = f'everpet_{species}_{stage}'
        exported = export_model(model, model_id, 1)
        texture = f'textures/entity/everpet_{species}_{stage}'
        for name, data in exported.items():
            if name.endswith('.png'):
                files[texture + '.png'] = data
                continue
            value = json.loads(data)
            if name.startswith('entity/'):
                description = value['minecraft:client_entity']['description']
                description['textures']['default'] = texture
                # Authored clips remain client-timed; a separate additive head
                # layer follows the live attention properties without restarting them.
                scripts = description['scripts']
                scripts['initialize'] = ['v.look_yaw = 0;', 'v.look_pitch = 0;']
                scripts['pre_animation'] = [
                    "v.look_yaw = v.look_yaw + (q.property('voidscape:look_yaw') - v.look_yaw) * math.min(q.delta_time * 12, 1);",
                    "v.look_pitch = v.look_pitch + (q.property('voidscape:look_pitch') - v.look_pitch) * math.min(q.delta_time * 12, 1);",
                ]
            elif name.startswith('models/'):
                geometry = value['minecraft:geometry'][0]['description']
                geometry.update(texture_width=model['resolution']['width'], texture_height=model['resolution']['height'],
                                visible_bounds_width=4, visible_bounds_height=3, visible_bounds_offset=[0, 1, 0])
            elif name.startswith('animations/'):
                look=f'animation.voidscape.{model_id}.look'
                heads={animator['name'] for animator in model['animations'][0]['animators'].values()
                       if animator['name'] in ('head','head_l','head_r')}
                value['animations'][look]=dict(loop=True,bones={
                    name:dict(rotation=['v.look_pitch','v.look_yaw',0]) for name in sorted(heads)})
                for animation_id,animation in value['animations'].items():
                    if animation_id==look:continue
                    # Client clock, scaled by the pace the server sends when the speed changes.
                    animation['anim_time_update'] = "q.anim_time + q.delta_time * q.property('voidscape:rate')"
            files[name] = encode(value)
    files.update(bedrock_textures())
    return files


def patch():
    pack = DIST / 'evergarden-bedrock.mcpack'
    with zipfile.ZipFile(pack) as source:
        files = {n: source.read(n) for n in source.namelist() if not n.endswith('/')}
    new = assets()
    files = {n: d for n, d in files.items() if not (('everpet_' in n or n.startswith('textures/menu/pet_')) and n not in new)}
    files.update(new)
    manifest = json.loads(files['manifest.json'])
    version = manifest['header']['version']
    version = [version[0], version[1] + 1, 0]
    manifest['header']['version'] = version
    for module in manifest['modules']:
        module['version'] = version
    files['manifest.json'] = json.dumps(manifest, indent=2).encode() + b'\n'
    with zipfile.ZipFile(pack, 'w', zipfile.ZIP_DEFLATED, compresslevel=9) as out:
        for name, data in sorted(files.items()):
            info = zipfile.ZipInfo(name, (2026, 10, 21, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            out.writestr(info, data)
    hashes_path = DIST / 'pack-hashes.json'
    hashes = json.loads(hashes_path.read_text())
    hashes['evergarden-bedrock.mcpack'] = hashlib.sha1(pack.read_bytes()).hexdigest()
    hashes_path.write_text(json.dumps(hashes, indent=2) + '\n')
    entities = sum(1 for n in new if n.startswith('entity/'))
    print(f'Bedrock pet entities: {entities}; textures: {sum(1 for n in new if n.endswith(".png"))};'
          f' content version {".".join(map(str, version))}; {pack.stat().st_size} bytes')
    return version


if __name__ == '__main__':
    patch()
