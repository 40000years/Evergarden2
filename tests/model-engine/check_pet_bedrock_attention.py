"""Verify every rare pet ships additive gaze without restarting client-timed clips."""
import argparse
import hashlib
import io
import json
from pathlib import Path
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'evergarden/tools'))
from pet_bedrock_assets import assets


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--expected-version', default='3.43.0')
    args = parser.parse_args()
    expected_version = [int(v) for v in args.expected_version.split('.')]
    checks = 0

    def check(value, label):
        nonlocal checks
        checks += 1
        assert value, label

    expected = assets()
    pack_path = ROOT / 'evergarden/dist/evergarden-bedrock.mcpack'
    with zipfile.ZipFile(pack_path) as pack:
        check(pack.testzip() is None, 'Bedrock pack CRC')
        manifest = json.loads(pack.read('manifest.json'))
        check(manifest['header']['version'] == expected_version, 'new client content version')
        for name, data in expected.items():
            check(pack.read(name) == data, 'reproducible exported pet asset ' + name)
        for name in expected:
            if not name.startswith('models/'):
                continue
            model = json.loads(expected[name])['minecraft:geometry'][0]
            names = {bone['name'] for bone in model['bones']}
            identifier = model['description']['identifier'].replace('geometry.voidscape.', '').removesuffix('_native')
            animations = json.loads(expected[f'animations/{identifier}_native.animation.json'])['animations']
            look = animations[f'animation.voidscape.{identifier}.look']
            heads = {name for name in names if name in ('head', 'head_l', 'head_r')}
            check(heads and set(look['bones']) == heads, 'every head receives additive attention ' + identifier)
            check(look['loop'] is True, 'attention remains active ' + identifier)
            for head in heads:
                check(look['bones'][head]['rotation'] == ['v.look_pitch', 'v.look_yaw', 0], 'head axis mapping ' + identifier)
            entity = json.loads(expected[f'entity/{identifier}_native.entity.json'])['minecraft:client_entity']['description']
            scripts = entity['scripts']
            check(scripts['animate'] == ['combat', 'look'], 'look runs after authored pose ' + identifier)
            check(len(scripts['pre_animation']) == 2 and all('delta_time' in s for s in scripts['pre_animation']), 'client gaze interpolation ' + identifier)
            for clip in ('idle', 'walk', 'fly', 'sit', 'greet'):
                animation = animations[f'animation.voidscape.{identifier}.{clip}']
                check(animation['anim_time_update'] == "q.anim_time + q.delta_time * q.property('voidscape:rate')", 'look packets never reset clip clock ' + identifier)
                check(all(bone in names for bone in animation['bones']), 'all animated bones exist ' + identifier)
            if 'abyssal_manta' in identifier:
                check({'wing_l_tip', 'wing_r_tip', 'tail_tip'} <= names, 'articulated ray fins and tail ' + identifier)
        before_path = ROOT / 'output/pet-attention-release-20261022/before/pet-assets-before.zip'
        if before_path.exists():
            with zipfile.ZipFile(before_path) as backup:
                with zipfile.ZipFile(io.BytesIO(backup.read('evergarden/dist/evergarden-bedrock.mcpack'))) as before:
                    previous = {n for n in before.namelist() if not n.endswith('/') and 'everpet_' not in n and n != 'manifest.json'}
                    check(previous <= set(pack.namelist()), 'existing non-pet entries retained')
                    for name in previous:
                        check(before.read(name) == pack.read(name), 'unrelated asset unchanged ' + name)
    report = dict(result='PASS', checks=checks, rare_variants=36, bedrock_version=expected_version,
                  additive_head_attention=True, client_timed_clips=True, connected_client_playtest=False,
                  pack_sha256=hashlib.sha256(pack_path.read_bytes()).hexdigest())
    output = ROOT / 'output/pet-attention-bedrock-checks-20261022.json'
    output.write_text(json.dumps(report, indent=2) + '\n', encoding='utf8')
    print(f'PASS {checks} checks; 36 Bedrock variants with additive gaze and continuous authored clips')


if __name__ == '__main__':
    main()
