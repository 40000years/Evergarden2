"""Menu portraits for every pet species and growth stage, shipped in Evergarden's own packs.

Java: pet-portraits.zip, merged into the Judge pack beside native-pets.zip, carries the
paper item models evergarden:menu_pet_<species>_<stage> and a dimmed _dim copy for pets
that cannot be picked. Bedrock forms load textures/menu/pet_<species>_<stage>; the
menagerie species are added to the Evergarden Bedrock pack by pet_bedrock_assets (the
original seventeen already ship in Advance Magic's pack under the same paths).

Each portrait renders the shipped blueprint with the preview camera and frames it with
Advance Magic's menu plaque (menu_gui_assets.portrait), so old and new pets match.
Portraits already in the archive are kept byte-for-byte (the slim build palette-compressed
them); only missing species are rendered, then compressed the same way.

    python evergarden/tools/pet_portraits.py
"""
import io
import json
import sys
import zipfile
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'tools'))
import menu_gui_assets as menu  # noqa: E402
import preview_pet_models as previews  # noqa: E402
from pet_model_engine import MENAGERIE, SPECIES, STAGES  # noqa: E402
from pet_expansion_catalog import SPECIES as EXPANSION

OUT = ROOT / 'evergarden/src/main/resources/pet-portraits.zip'


def _crop(frame):
    """The pet on a transparent square, keyed off the flat preview backdrop."""
    im = np.asarray(frame.convert('RGB')).astype(float)
    alpha = np.clip((np.abs(im - im[0, 0]).sum(-1) - 6) / 30, 0, 1)
    ys, xs = np.nonzero(alpha > .5)
    cy, cx = (ys.min() + ys.max()) // 2, (xs.min() + xs.max()) // 2
    side = max(ys.max() - ys.min(), xs.max() - xs.min()) + 10
    rgba = Image.fromarray(np.dstack([im, alpha * 255]).astype(np.uint8), 'RGBA')
    return rgba.crop((cx - side // 2, cy - side // 2, cx + side // 2, cy + side // 2))


def _png(image):
    buffer = io.BytesIO()
    image.save(buffer, format='PNG')
    return buffer.getvalue()


def portraits(species_list=SPECIES):
    """name -> PNG bytes, for these species in every stage, bright and dimmed."""
    out = {}
    for species in species_list:
        span = previews.span_for(species)
        for stage in STAGES:
            render = _crop(previews.frame(species, stage, span))
            out[f'pet_{species}_{stage}'] = _png(menu.portrait(render, stage).image())
            out[f'pet_{species}_{stage}_dim'] = _png(menu.portrait(render, stage, True).image())
    return out


def _json(value):
    return (json.dumps(value, separators=(',', ':')) + '\n').encode()


def build():
    entries = {}
    if OUT.exists():
        with zipfile.ZipFile(OUT) as archive:
            entries = {name: archive.read(name) for name in archive.namelist()}
    missing = [s for s in SPECIES if f'assets/evergarden/textures/item/menu/pet_{s}_adult.png' not in entries]
    import build_evergarden_slim_packs as slim  # noqa: E402  (imagequant, only when rendering)
    stats = {'pinned': 0, 'kept': 0, 'lossless': 0, 'palette': 0}
    for name, data in portraits(missing).items():
        data = slim.compress(data, set(), stats)
        entries[f'assets/evergarden/textures/item/menu/{name}.png'] = data
        entries[f'assets/evergarden/models/item/menu/{name}.json'] = _json(
            {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'evergarden:item/menu/{name}'}})
        entries[f'assets/evergarden/items/menu_{name}.json'] = _json(
            {'model': {'type': 'minecraft:model', 'model': f'evergarden:item/menu/{name}'}})
    with zipfile.ZipFile(OUT, 'w', zipfile.ZIP_DEFLATED) as archive:
        for name, data in sorted(entries.items()):
            archive.writestr(zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0)), data, compress_type=zipfile.ZIP_DEFLATED)
    print(f'{len(entries) // 3} portraits for {len(SPECIES)} species ({len(missing)} rendered: {stats}) -> {OUT.name} ({OUT.stat().st_size} bytes)')


def bedrock_textures():
    """Bedrock form images for the menagerie species, read from the built Java portraits."""
    files = {}
    with zipfile.ZipFile(OUT) as archive:
        for species in MENAGERIE + EXPANSION:
            for stage in STAGES:
                name = f'pet_{species}_{stage}'
                files[f'textures/menu/{name}.png'] = archive.read(f'assets/evergarden/textures/item/menu/{name}.png')
    return files


if __name__ == '__main__':
    build()
