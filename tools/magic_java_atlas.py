"""Register spell textures with Java's item atlas (resource format 75+)."""
import json


def register(java, write_json):
    # Keeping the sprite IDs also repairs models supplied by an older server pack.
    effects = {'type': 'minecraft:directory', 'source': 'effect', 'prefix': 'effect/'}
    beam = {'type': 'minecraft:single',
            'resource': 'minecraft:entity/beacon/beacon_beam',
            'sprite': 'minecraft:entity/beacon_beam'}
    atlas = 'assets/minecraft/atlases/items.json'
    path = java / atlas
    existing = json.loads(path.read_text()) if path.exists() else {'sources': []}
    for source in (effects, beam):
        if source not in existing['sources']:
            existing['sources'].append(source)
    write_json(path, existing)

    metadata = json.loads((java / 'pack.mcmeta').read_text())
    minimum = metadata['pack']['min_format']
    if (minimum[0] if isinstance(minimum, list) else minimum) > 76:
        return

    # The vanilla beacon PNG moved in 26.1 snapshot 2 (resource format 77).
    # Overlays replace the atlas file inside this pack, avoiding missing-file
    # warnings and keeping the declared 1.21.11 compatibility.
    legacy = json.loads(json.dumps(existing))
    for source in legacy['sources']:
        if source == beam:
            source['resource'] = 'minecraft:entity/beacon_beam'
    write_json(java / 'legacy_beacon' / atlas, legacy)
    entry = {'directory': 'legacy_beacon', 'min_format': [75, 0], 'max_format': 76}
    entries = metadata.setdefault('overlays', {}).setdefault('entries', [])
    if entry not in entries:
        entries.append(entry)
    write_json(java / 'pack.mcmeta', metadata)
