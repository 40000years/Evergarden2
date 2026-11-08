"""Store each pet texture once in the Evergarden JAR.

Every bundled pet blueprint (modelengine/pets/*.bbmodel) embeds its PNG as a base64 data URI, and the
same PNG bytes already ship in native-pets.zip. `slim` swaps each data URI for a short token and writes
modelengine/pets/blueprint-textures.json, which maps every token back to its native-pets.zip entry and
records the original blueprint's size and SHA-256. The plugin (PetBlueprints.java) restores the exact
original bytes only when it really has to write a blueprint, and checks them against that SHA-256.

`restore` is the Python twin of the Java restore. `slim` runs it on every blueprint and refuses to
return anything that does not round-trip byte for byte.
"""
import base64
import hashlib
import io
import json
import re
import zipfile

INDEX = 'modelengine/pets/blueprint-textures.json'
ARCHIVE = 'native-pets.zip'
PREFIX = 'modelengine/pets/'
TOKEN = 'evergarden-texture:'
DATA_URI = re.compile(rb'data:image/png;base64,([A-Za-z0-9+/]*={0,2})')


def _sha(data):
    return hashlib.sha256(data).hexdigest()


def _blueprint(name):
    return name.startswith(PREFIX) and name.endswith('.bbmodel') and '/' not in name[len(PREFIX):]


def _pngs(native):
    with zipfile.ZipFile(io.BytesIO(native)) as archive:
        return {_sha(data): (name, data) for name in archive.namelist() if name.endswith('.png')
                for data in [archive.read(name)]}


def restore(slim_bytes, textures, pngs_by_path):
    """Rebuild an original blueprint from its slim bytes; textures maps token -> native-pets.zip path."""
    data = slim_bytes
    for token, path in textures.items():
        uri = b'data:image/png;base64,' + base64.b64encode(pngs_by_path[path])
        if data.count(token.encode('ascii')) < 1:
            raise ValueError('Token missing from blueprint: ' + token)
        data = data.replace(token.encode('ascii'), uri)
    return data


def slim(entries):
    """entries: {jar entry name: bytes}. Returns (replacements, stats); replacements include the index."""
    if INDEX in entries:
        raise ValueError('JAR already carries ' + INDEX)
    pngs = _pngs(entries[ARCHIVE])
    by_path = {path: data for path, data in pngs.values()}
    models, replacements, saved = {}, {}, 0
    for name in sorted(n for n in entries if _blueprint(n)):
        original = entries[name]
        textures = {}

        def swap(match):
            png = base64.b64decode(match.group(1), validate=True)
            if base64.b64encode(png) != match.group(1):
                raise ValueError(f'{name}: non-canonical base64 cannot be restored exactly')
            digest = _sha(png)
            if digest not in pngs:
                raise ValueError(f'{name}: embedded texture {digest} is not in {ARCHIVE}')
            token = TOKEN + digest
            textures[token] = pngs[digest][0]
            return token.encode('ascii')

        reduced = DATA_URI.sub(swap, original)
        if not textures:
            continue
        if restore(reduced, textures, by_path) != original:
            raise ValueError(f'{name}: slim blueprint does not restore byte for byte')
        model = name[len(PREFIX):-len('.bbmodel')]
        models[model] = {'sha256': _sha(original), 'size': len(original), 'textures': textures}
        replacements[name] = reduced
        saved += len(original) - len(reduced)
    index = {'format': 1, 'archive': ARCHIVE, 'models': models}
    replacements[INDEX] = (json.dumps(index, indent=1, sort_keys=True) + '\n').encode('utf8')
    return replacements, {'blueprints': len(models), 'textures': sum(len(m['textures']) for m in models.values()),
                          'uncompressed_bytes_saved': saved}


def verify(entries):
    """Check a slim JAR's entries: every indexed blueprint restores to its recorded size and SHA-256."""
    index = json.loads(entries[INDEX])
    by_path = {path: data for path, data in _pngs(entries[ARCHIVE]).values()}
    for model, info in index['models'].items():
        data = restore(entries[PREFIX + model + '.bbmodel'], info['textures'], by_path)
        if len(data) != info['size'] or _sha(data) != info['sha256']:
            raise ValueError(model + ' does not restore to its recorded original')
    return len(index['models'])
