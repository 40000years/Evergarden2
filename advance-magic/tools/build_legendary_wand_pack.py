"""Build isolated, deterministic legendary wand art preview packs.

Consumes the three authored 128px PNGs in art/wands; does not edit gameplay,
release packs, their resource URLs/hashes, or the original textures.
Development dependency: Pillow (only for PNG checks and a composed pack icon).
"""
import argparse
import hashlib
import json
import shutil
import tempfile
import uuid
import zipfile
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
ART = ROOT / 'art/wands'
PREVIEW = ART / 'legendary'
VERSION = [0, 1, 0]
JAVA_FILE = 'advance-magic-legendary-java-preview.zip'
BEDROCK_FILE = 'advance-magic-legendary-bedrock-preview.mcpack'
WANDS = (
    ('verdant_sovereign', 'Verdant Sovereign', 'ราชันมังกรพฤกษา', '05',
     'An emerald dragon crown with faceted crystals and antique gold spines.',
     'มงกุฎมังกร มรกตเจียระไน และหนามทองโบราณ'),
    ('lunar_requiem', 'Lunar Requiem', 'บทสวดจันทรา', '06',
     'A silver crescent with pale blue moon crystal and celestial ornaments.',
     'จันทร์เสี้ยวเงิน คริสตัลจันทราสีฟ้า และเครื่องประดับดวงดาว'),
    ('crimson_oblivion', 'Crimson Oblivion', 'หายนะโลหิตทมิฬ', '07',
     'A jagged obsidian crystal crown with a crimson core and dark gold.',
     'มงกุฎคริสตัลออบซิเดียนแหลม แกนพลังสีแดง และทองทมิฬ'),
)


def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n',
                    encoding='utf-8', newline='\n')


def read_json(path):
    return json.loads(path.read_text(encoding='utf-8'))


def require(condition, message):
    if not condition:
        raise ValueError(message)


def check_texture(path):
    with Image.open(path) as image:
        require(image.format == 'PNG' and image.mode == 'RGBA', f'{path}: use an RGBA PNG')
        require(image.size == (128, 128), f'{path}: texture must be 128 x 128')
        image.verify()
    with Image.open(path) as image:
        alpha = image.getchannel('A')
        histogram = alpha.histogram()
        require(set(alpha.tobytes()) == {0, 255},
                f'{path}: alpha must be exactly 0 or 255')
        require(histogram[0] > 128 * 128 * .55, f'{path}: background must be transparent')
        require(histogram[255] > 128 * 128 * .025, f'{path}: missing visible wand')
        for box in ((0, 0, 128, 3), (0, 125, 128, 128),
                    (0, 0, 3, 128), (125, 0, 128, 128)):
            require(alpha.crop(box).getbbox() is None, f'{path}: require a 3px clear border')


def archive(folder, path):
    path.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(path, 'w', compression=zipfile.ZIP_DEFLATED, compresslevel=9) as pack:
        for source in sorted(folder.rglob('*')):
            if source.is_file():
                info = zipfile.ZipInfo(source.relative_to(folder).as_posix(), (2026, 10, 4, 0, 0, 0))
                info.compress_type = zipfile.ZIP_DEFLATED
                info.create_system = 3
                info.external_attr = 0o100644 << 16
                pack.writestr(info, source.read_bytes(), compresslevel=9)


def pack_icon(java, bedrock):
    # Deterministic composition of the finished textures; no regenerated artwork.
    icon = Image.new('RGBA', (256, 256), '#0e1020')
    draw = ImageDraw.Draw(icon)
    draw.rounded_rectangle((5, 5, 250, 250), radius=22, outline='#dcb86b', width=3)
    for index, (name, *_) in enumerate(WANDS):
        with Image.open(ART / f'{name}.png') as source:
            tile = source.resize((96, 96), Image.Resampling.NEAREST)
            icon.alpha_composite(tile, ((80, 14, 147)[index], (22, 140, 140)[index]))
    icon.save(java / 'pack.png', optimize=True)
    icon.save(bedrock / 'pack_icon.png', optimize=True)


def populate(folder):
    java, bedrock = folder / 'java', folder / 'bedrock'
    write_json(java / 'pack.mcmeta', {'pack': {
        'description': 'Advance Magic | Legendary Wands 05–07 | Art Preview',
        'min_format': [75, 0], 'max_format': [88, 0]}})
    write_json(bedrock / 'manifest.json', {
        'format_version': 2,
        'header': {'name': 'Advance Magic | Legendary Wands Preview',
                   'description': 'Original legendary wand textures 05–07; requires Geyser custom mappings.',
                   'uuid': str(uuid.uuid5(uuid.NAMESPACE_URL, 'advance-magic/legendary-wands-preview/resources')),
                   'version': VERSION, 'min_engine_version': [1, 21, 80]},
        'modules': [{'type': 'resources',
                     'uuid': str(uuid.uuid5(uuid.NAMESPACE_URL, 'advance-magic/legendary-wands-preview/module')),
                     'version': VERSION}]})
    java_atlas, bedrock_atlas, definitions = [], {}, []
    english, thai, catalog = {}, {}, []
    for name, title, title_th, number, theme, theme_th in WANDS:
        source = ART / f'{name}.png'
        for destination in (java / f'assets/advance_magic/textures/item/{name}.png',
                            bedrock / f'textures/items/{name}.png'):
            destination.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(source, destination)
        write_json(java / f'assets/advance_magic/models/item/{name}.json', {
            'parent': 'minecraft:item/handheld', 'textures': {'layer0': f'advance_magic:item/{name}'}})
        write_json(java / f'assets/advance_magic/items/{name}.json', {
            'model': {'type': 'minecraft:model', 'model': f'advance_magic:item/{name}'}})
        # magic_java_atlas.py uses this same item atlas in resource format 75+.
        # Singles register exactly our sprites without borrowing unrelated effects.
        java_atlas.append({'type': 'minecraft:single', 'resource': f'advance_magic:item/{name}',
                           'sprite': f'advance_magic:item/{name}'})
        bedrock_atlas[f'advance_magic.{name}'] = {'textures': f'textures/items/{name}'}
        definitions.append({'type': 'definition', 'model': f'advance_magic:{name}',
                            'bedrock_identifier': f'advance_magic:{name}', 'display_name': title,
                            'bedrock_options': {'icon': f'advance_magic.{name}', 'allow_offhand': True,
                                                'display_handheld': True, 'creative_category': 'equipment'}})
        key = f'item.advance_magic.{name}'
        english[key], thai[key] = title, title_th
        catalog.append({'id': name, 'design_number': number, 'name_en': title, 'name_th': title_th,
                        'rarity': 'legendary', 'theme_en': theme, 'theme_th': theme_th,
                        'source_texture': f'../{name}.png', 'texture_sha256': hashlib.sha256(source.read_bytes()).hexdigest(),
                        'texture_size': [128, 128], 'texture_format': 'RGBA, binary alpha',
                        'java_item_model': f'advance_magic:{name}', 'bedrock_identifier': f'advance_magic:{name}',
                        'base_item': 'minecraft:carrot_on_a_stick', 'translation_key': key})
    write_json(java / 'assets/minecraft/atlases/items.json', {'sources': java_atlas})
    write_json(java / 'assets/advance_magic/lang/en_us.json', english)
    write_json(java / 'assets/advance_magic/lang/th_th.json', thai)
    write_json(bedrock / 'textures/item_texture.json', {
        'resource_pack_name': 'advance_magic_legendary_preview', 'texture_name': 'atlas.items',
        'texture_data': bedrock_atlas})
    write_json(bedrock / 'texts/languages.json', ['en_US', 'th_TH'])
    for language, titles in (('en_US', english), ('th_TH', thai)):
        text = '\n'.join(f'item.advance_magic:{name}.name={titles[f"item.advance_magic.{name}"]}' for name, *_ in WANDS) + '\n'
        (bedrock / 'texts' / f'{language}.lang').write_text(text, encoding='utf-8', newline='\n')
    write_json(folder / 'geyser-mappings.json', {
        'format_version': 2, 'items': {'minecraft:carrot_on_a_stick': definitions}})
    write_json(folder / 'catalog.json', {
        'name': 'Advance Magic — Legendary Wand Art 05–07', 'version': VERSION,
        'status': 'art_preview', 'gameplay_registered': False,
        'source_reference': 'Original legendary staff concept, designs 05–07: emerald dragon crown, silver crescent, crimson obsidian crown.',
        'reference_image': 'reference.png', 'texture_preview': 'texture-preview.png',
        'generation_metadata': 'generation-prompts.json',
        'normalization_tool': '../../../tools/prepare_legendary_wand_art.py',
        'java_resource_formats': {'min': [75, 0], 'max': [88, 0]},
        'bedrock_min_engine_version': [1, 21, 80],
        'bedrock_requirement': 'Geyser v2 custom mapping and server-created item_model items; resource pack alone does not add items.',
        'wands': catalog})
    pack_icon(java, bedrock)
    archive(java, folder / 'dist' / JAVA_FILE)
    archive(bedrock, folder / 'dist' / BEDROCK_FILE)
    shutil.copyfile(folder / 'geyser-mappings.json', folder / 'dist/geyser-mappings.json')
    hashes = {}
    for filename in (JAVA_FILE, BEDROCK_FILE):
        data = (folder / 'dist' / filename).read_bytes()
        hashes[filename] = {'sha1': hashlib.sha1(data).hexdigest(),
                            'sha256': hashlib.sha256(data).hexdigest(), 'bytes': len(data)}
    write_json(folder / 'dist/pack-hashes.json', hashes)


def validate(folder):
    java, bedrock = folder / 'java', folder / 'bedrock'
    ids = [row[0] for row in WANDS]
    catalog = read_json(folder / 'catalog.json')
    require([row['id'] for row in catalog['wands']] == ids, 'Catalog IDs disagree')
    require(catalog['gameplay_registered'] is False, 'Preview must stay separate from gameplay')
    pack = read_json(java / 'pack.mcmeta')['pack']
    require(pack['min_format'] == [75, 0] and pack['max_format'] == [88, 0], 'Java resource format changed')
    atlas = read_json(java / 'assets/minecraft/atlases/items.json')['sources']
    require(atlas == [{'type': 'minecraft:single', 'resource': f'advance_magic:item/{name}',
                       'sprite': f'advance_magic:item/{name}'} for name in ids], 'Item atlas IDs disagree')
    for namespace_folder in ('items', 'models/item', 'textures/item'):
        require(not (java / 'assets/minecraft' / namespace_folder).exists(), 'Do not replace vanilla items')
    manifest = read_json(bedrock / 'manifest.json')
    require(manifest['header']['version'] == manifest['modules'][0]['version'] == VERSION, 'Bedrock versions disagree')
    require(manifest['header']['min_engine_version'] == [1, 21, 80], 'Bedrock minimum engine changed')
    require(manifest['header']['uuid'] != manifest['modules'][0]['uuid'], 'Bedrock UUIDs must differ')
    for entry in (manifest['header'], manifest['modules'][0]):
        uuid.UUID(entry['uuid'])
    mapping = read_json(folder / 'geyser-mappings.json')
    require(mapping['format_version'] == 2, 'Use Geyser mapping v2')
    require(set(mapping['items']) == {'minecraft:carrot_on_a_stick'}, 'Unexpected base item mapping')
    definitions = mapping['items']['minecraft:carrot_on_a_stick']
    require([row['model'] for row in definitions] == [f'advance_magic:{name}' for name in ids], 'Geyser model IDs disagree')
    require([row['bedrock_identifier'] for row in definitions] == [f'advance_magic:{name}' for name in ids], 'Geyser item IDs disagree')
    bedrock_atlas = read_json(bedrock / 'textures/item_texture.json')['texture_data']
    require(set(bedrock_atlas) == {f'advance_magic.{name}' for name in ids}, 'Unexpected Bedrock icons')
    for row, (name, title, title_th, *_) in zip(definitions, WANDS):
        source = ART / f'{name}.png'
        check_texture(source)
        require(row['type'] == 'definition' and row['display_name'] == title, 'Geyser name/type disagrees')
        require(row['bedrock_options']['icon'] == f'advance_magic.{name}', 'Geyser icon disagrees')
        require(row['bedrock_options']['display_handheld'] is True, 'Wands need handheld presentation')
        model = read_json(java / f'assets/advance_magic/models/item/{name}.json')
        require(model == {'parent': 'minecraft:item/handheld', 'textures': {'layer0': f'advance_magic:item/{name}'}}, f'{name}: incorrect handheld model')
        definition = read_json(java / f'assets/advance_magic/items/{name}.json')
        require(definition['model'] == {'type': 'minecraft:model', 'model': f'advance_magic:item/{name}'}, f'{name}: incorrect item definition')
        texture_path = bedrock_atlas[f'advance_magic.{name}']['textures'] + '.png'
        for texture in (java / f'assets/advance_magic/textures/item/{name}.png', bedrock / texture_path):
            check_texture(texture)
            require(texture.read_bytes() == source.read_bytes(), f'{name}: texture differs from authored source')
        entry = next(wand for wand in catalog['wands'] if wand['id'] == name)
        require(entry['texture_sha256'] == hashlib.sha256(source.read_bytes()).hexdigest(), f'{name}: stale catalog texture hash')
        for language, expected in (('en_us', title), ('th_th', title_th)):
            require(read_json(java / f'assets/advance_magic/lang/{language}.json')[f'item.advance_magic.{name}'] == expected, f'{name}: incorrect {language} title')
    require(read_json(bedrock / 'texts/languages.json') == ['en_US', 'th_TH'], 'Bedrock language IDs disagree')
    for language, title_column in (('en_US', 1), ('th_TH', 2)):
        expected = '\n'.join(f'item.advance_magic:{row[0]}.name={row[title_column]}' for row in WANDS) + '\n'
        require((bedrock / 'texts' / f'{language}.lang').read_text(encoding='utf-8') == expected, f'Incorrect {language} Bedrock titles')
    hashes = read_json(folder / 'dist/pack-hashes.json')
    for source_folder, filename, metadata in ((java, JAVA_FILE, 'pack.mcmeta'), (bedrock, BEDROCK_FILE, 'manifest.json')):
        data = (folder / 'dist' / filename).read_bytes()
        require(hashes[filename] == {'sha1': hashlib.sha1(data).hexdigest(), 'sha256': hashlib.sha256(data).hexdigest(), 'bytes': len(data)}, f'{filename}: hash or size disagrees')
        with zipfile.ZipFile(folder / 'dist' / filename) as archive_file:
            require(archive_file.testzip() is None, f'{filename}: bad archive CRC')
            names = archive_file.namelist()
            require(metadata in names, f'{filename}: metadata missing at root')
            require(len(names) == len(set(names)), f'{filename}: duplicate archive paths')
            expected = {path.relative_to(source_folder).as_posix() for path in source_folder.rglob('*') if path.is_file()}
            require(set(names) == expected, f'{filename}: archive differs from source tree')
            for name in names:
                require(archive_file.read(name) == (source_folder / name).read_bytes(), f'{filename}: stale {name}')
                if name.endswith(('.json', '.mcmeta')):
                    json.loads(archive_file.read(name))
    require((folder / 'dist/geyser-mappings.json').read_bytes() == (folder / 'geyser-mappings.json').read_bytes(), 'Stale distributed mapping')


def publish(folder):
    # Only replace our generated pack trees, preserving README and prompt/art metadata.
    for name in ('java', 'bedrock'):
        destination = PREVIEW / name
        require(destination.resolve().parent == PREVIEW.resolve()
                and destination.resolve().is_relative_to(ROOT.resolve()),
                'Generated pack destination escaped preview directory')
        if destination.exists():
            shutil.rmtree(destination)
        shutil.copytree(folder / name, destination)
    for name in ('catalog.json', 'geyser-mappings.json'):
        shutil.copyfile(folder / name, PREVIEW / name)
    (PREVIEW / 'dist').mkdir(exist_ok=True)
    for source in sorted((folder / 'dist').iterdir()):
        shutil.copyfile(source, PREVIEW / 'dist' / source.name)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--validate-only', action='store_true', help='Check existing preview files without rebuilding')
    args = parser.parse_args()
    if args.validate_only:
        validate(PREVIEW)
        print('PASS: three authored wand textures, Java/Bedrock references, languages, Geyser v2 mapping, archive CRCs and hashes.')
        return
    for name, *_ in WANDS:
        check_texture(ART / f'{name}.png')
    PREVIEW.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='.legendary-pack-', dir=PREVIEW) as temporary:
        folder = Path(temporary)
        populate(folder)
        validate(folder)
        publish(folder)
    validate(PREVIEW)
    for filename in (JAVA_FILE, BEDROCK_FILE):
        print(f'Built {PREVIEW / "dist" / filename}')
    print('PASS: preview packs validated; release packs and gameplay unchanged.')


if __name__ == '__main__':
    main()
