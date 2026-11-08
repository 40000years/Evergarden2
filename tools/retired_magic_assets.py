"""Advance Magic pack entries that nothing live renders any more (retired in 1.35.0).

The 21 wand spells were disabled long before 1.35.0 (CastListener.castInput returned false and
legacy wands migrate into elemental staffs). Their engine and art left the plugin in 1.35.0:

* effect planes drawn only by the retired Verdant/Lunar/Crimson/Solar/Chronos/Shulker/Judgment
  spells (the elemental Legendary scenes reuse lunar_seal, lunar_orbit, lunar_constellation,
  crimson_seal, chronos_ripple, solar_corona, verdant_halo, solar_ray and the judgment_seal
  images through judgment_gold_*, so those stay);
* the Longinus projectile, unused since Longinus became a melee weapon.

The six Mythic wand icons (solar_apocalypse ... crimson_oblivion) stay: a legacy wand still
shows them until a player picks it up and it migrates into a staff.

Removal is a reference-checked garbage collection: an item definition named here is dropped,
then each model, texture or Bedrock file it used is dropped only when nothing left refers to it.
"""
import json
import re

EFFECTS = ('chronos_dial', 'chronos_echo', 'chronos_hour', 'chronos_minute', 'chronos_ray',
           'crimson_heart', 'crimson_ray', 'crimson_shard', 'lunar_ray', 'shulker_prism', 'solar_orbit',
           'verdant_beam_core', 'verdant_dragon', 'verdant_ray', 'verdant_seal',
           'judgment_seal_0', 'judgment_seal_1', 'judgment_beam')
PROJECTILES = ('longinus_projectile',)
NAMES = EFFECTS + PROJECTILES
_TEXT = ('.json', '.mcmeta', '.material', '.lang')


def _refs(data):
    """Every advance_magic:<path> resource id and bare path a JSON/text entry mentions."""
    text = data.decode('utf8', 'replace')
    return set(re.findall(r'advance_magic:([a-z0-9_/.-]+)', text)) | set(re.findall(r'"((?:textures|geometry)[a-z0-9_/.-]+)"', text))


def _java_refs(data):
    """(model ids, texture ids) a Java JSON entry uses; a model and a texture may share one id."""
    models, textures = set(), set()

    def walk(node, key=None, in_textures=False):
        if isinstance(node, dict):
            for k, v in node.items():
                walk(v, k, in_textures or k == 'textures')
        elif isinstance(node, list):
            for v in node:
                walk(v, key, in_textures)
        elif isinstance(node, str) and node.startswith('advance_magic:'):
            ref = node.split(':', 1)[1]
            if in_textures or key in ('texture', 'sprite', 'particle'):
                textures.add(ref)
            elif key in ('model', 'parent'):
                models.add(ref)
            else:
                models.add(ref)
                textures.add(ref)
    walk(json.loads(data))
    return models, textures


def java_removals(files, strict=True):
    """files: {path: bytes} of the Java pack. Returns the sorted set of paths to drop."""
    removed = {f'assets/advance_magic/items/{n}.json' for n in NAMES}
    missing = removed - set(files)
    if missing and strict:
        raise RuntimeError('Retired Java item definitions missing: ' + ', '.join(sorted(missing)))
    removed -= missing
    refs = {p: _java_refs(d) for p, d in files.items() if p.endswith('.json')}
    # Models first, then the textures those models used; each only when nothing kept refers to it.
    for index, folder, extension in ((0, 'models', 'json'), (1, 'textures', 'png')):
        candidates = {(ref, f'assets/advance_magic/{folder}/{ref}.{extension}')
                      for path in removed if path in refs for ref in refs[path][index]}
        for ref, target in sorted(candidates):
            if target not in files:
                continue
            kept = [p for p, r in refs.items() if p not in removed and p != target and ref in r[index]]
            if not kept:
                removed.add(target)
                if index == 1 and target + '.mcmeta' in files:
                    removed.add(target + '.mcmeta')
    return sorted(removed)


def bedrock_removals(files, strict=True):
    """files: {path: bytes} of the Bedrock pack. Returns the sorted set of paths to drop."""
    removed = set()
    for name in EFFECTS:
        for suffix in ('', '_flat', '_xy'):
            path = f'particles/{name}{suffix}.particle.json'
            if path in files:
                removed.add(path)
        if strict and f'particles/{name}.particle.json' not in files:
            raise RuntimeError('Retired Bedrock particle missing: ' + name)
    for name in PROJECTILES:
        hits = {p for p in files if re.search(r'(^|/)' + name + r'(_fx)?\.(entity|animation|geo|render)\.json$', p)}
        if not hits and strict:
            raise RuntimeError('Retired Bedrock projectile missing: ' + name)
        removed |= hits
    # Textures (and other files) the dropped definitions used, only when nothing kept refers to them.
    referenced = set()
    for path in removed:
        referenced |= {r for r in _refs(files[path]) if r.startswith('textures/')}
    for ref in sorted(referenced):
        target = next((p for p in (ref + '.png', ref + '.tga', ref) if p in files), None)
        if target is None:
            continue
        kept = [p for p, d in files.items() if p not in removed and p.endswith(_TEXT)
                and re.search(r'"' + re.escape(ref) + r'(\.png)?"', d.decode('utf8', 'replace'))]
        if not kept:
            removed.add(target)
    return sorted(removed)


def dangling(files, removed):
    """Kept text entries that still name a removed resource: must be empty for a valid pack."""
    java = any(p.startswith('assets/') for p in files)
    problems = []
    if java:
        refs = {p: _java_refs(d) for p, d in files.items() if p.endswith('.json') and p not in removed}
        for path in removed:
            match = re.match(r'assets/advance_magic/(models|textures)/(.+)\.(json|png)$', path)
            if not match:
                continue
            index, ref = (0 if match.group(1) == 'models' else 1), match.group(2)
            problems += [f'{p} -> {path}' for p, r in refs.items() if ref in r[index]]
        return problems
    names = set()
    for path in removed:
        data = files[path]
        if path.endswith('.json'):
            names |= set(re.findall(r'"identifier"\s*:\s*"([^"]+)"', data.decode('utf8', 'replace')))
        elif path.endswith('.png'):
            names.add(path[:-4])
    for path, data in files.items():
        if path in removed or not path.endswith(_TEXT):
            continue
        text = data.decode('utf8', 'replace')
        problems += [f'{path} -> {n}' for n in names if re.search(r'"' + re.escape(n) + r'(\.png)?"', text)]
    return problems


def prune(files):
    """For full pack builders: the retired paths in a freshly generated pack (Java or Bedrock)."""
    if any(p.startswith('assets/') for p in files):
        return set(java_removals(files, strict=False))
    return set(bedrock_removals(files, strict=False))


if __name__ == '__main__':
    print(json.dumps({'effects': EFFECTS, 'projectiles': PROJECTILES}, indent=2))
