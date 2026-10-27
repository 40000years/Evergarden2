"""Verify the source generator omits disabled ranged weapons on both platforms."""
import argparse
import importlib.util
import json
from pathlib import Path
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parents[2]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--java-pack', type=Path, default=ROOT / 'evergarden/dist/evergarden-java.zip')
parser.add_argument('--bedrock-pack', type=Path, default=ROOT / 'evergarden/dist/evergarden-bedrock.mcpack')
parser.add_argument('--mappings', type=Path, default=ROOT / 'evergarden/dist/geyser-mappings.json')
parser.add_argument('--generator-only', action='store_true')
args = parser.parse_args()

removed = {'thornwhisper', 'runelock'}
checks = 0


def check(condition, label):
    global checks
    assert condition, label
    checks += 1


def verify_mappings(mappings):
    entries = [entry for group in mappings['items'].values() for entry in group]
    for weapon in removed:
        check(not any(weapon in entry.get('model', '') or weapon in entry.get('bedrock_identifier', '')
                      for entry in entries), weapon + ': no custom Geyser definition remains')


spec = importlib.util.spec_from_file_location('adventurer_weapon_assets', ROOT / 'evergarden/tools/adventurer_weapon_assets.py')
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)
check(module.TEMPORARILY_DISABLED == removed, 'generator declares the two temporary disables')

with tempfile.TemporaryDirectory(prefix='ranged-weapon-disabled-') as directory:
    root = Path(directory)
    java, bedrock = root / 'java', root / 'bedrock'
    module.ART = root / 'art'
    module.CATALOG = tuple(row for row in module.CATALOG if row[0] in removed)
    atlas, mappings = {}, {'format_version': 2, 'items': {}}
    module.register(java, bedrock, atlas, mappings)
    java_names = {path.relative_to(java).as_posix() for path in java.rglob('*') if path.is_file()}
    bedrock_names = {path.relative_to(bedrock).as_posix() for path in bedrock.rglob('*') if path.is_file()}
    for weapon in removed:
        check(not any(weapon in name for name in java_names), weapon + ': generator exports no Java model')
        check(not any(weapon in name for name in bedrock_names), weapon + ': generator exports no Bedrock model')
    verify_mappings(mappings)

if not args.generator_only:
    verify_mappings(json.loads(args.mappings.read_text(encoding='utf-8')))
    for pack_path in (args.java_pack, args.bedrock_pack):
        with zipfile.ZipFile(pack_path) as pack:
            names = set(pack.namelist())
            for weapon in removed:
                check(not any(weapon in name for name in names), f'{weapon}: no model assets in {pack_path.name}')

print(f'PASS {checks} checks: disabled weapon models and mappings are absent on both platforms')
