"""Validate particle direction modes against Mojang's engine schema snapshot."""
import json
from pathlib import Path
import zipfile


SCHEMA = Path(__file__).with_name('schemas') / 'bedrock_particle_direction_mode.json'


def check_direction_modes(path):
    allowed = json.loads(SCHEMA.read_text(encoding='utf8'))['enum']
    checked = 0
    with zipfile.ZipFile(path) as pack:
        for name in pack.namelist():
            if not name.startswith('particles/') or not name.endswith('.json'):
                continue
            components = json.loads(pack.read(name))['particle_effect']['components']
            billboard = components.get('minecraft:particle_appearance_billboard', {})
            direction = billboard.get('direction')
            if direction is None:
                continue
            mode = direction.get('mode', 'derive_from_velocity')
            assert mode in allowed, f'{path}: {name}: invalid direction mode {mode!r}; Mojang allows {allowed}'
            if mode == 'custom':
                vector = direction.get('custom_direction')
                assert isinstance(vector, list) and len(vector) == 3, f'{name}: custom direction requires a 3D vector'
            checked += 1
    return checked


if __name__ == '__main__':
    import sys
    for path in sys.argv[1:]:
        print(f'PASS: {check_direction_modes(path)} directional emitters in {path}')
