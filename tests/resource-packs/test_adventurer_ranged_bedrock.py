"""Verify disabled ranged weapons have no Bedrock model or Geyser mapping."""
import argparse
import json
from pathlib import Path
import zipfile

ROOT = Path(__file__).resolve().parents[2]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--pack', type=Path, default=ROOT / 'evergarden/dist/evergarden-bedrock.mcpack')
parser.add_argument('--mappings', type=Path, default=ROOT / 'evergarden/dist/geyser-mappings.json')
parser.add_argument('--baseline-pack', type=Path)
args = parser.parse_args()
checks = 0


def check(condition, label):
    global checks
    assert condition, label
    checks += 1


removed = ('thornwhisper', 'runelock')
with zipfile.ZipFile(args.pack) as pack:
    names = set(pack.namelist())
    for weapon in removed:
        check(not any(weapon in name for name in names), weapon + ': no Bedrock model, attachable, texture, or icon remains')
    atlas = json.loads(pack.read('textures/item_texture.json'))['texture_data']
    check(not any(any(weapon in key for weapon in removed) for key in atlas),
          'removed weapons do not leave Bedrock hotbar icon mappings')
    for path, field in (
        ('animations/adventurer_weapons.animation.json', 'animations'),
        ('render_controllers/adventurer_weapons.render.json', 'render_controllers'),
    ):
        values = json.loads(pack.read(path))[field]
        check(not any(any(weapon in key for weapon in removed) for key in values),
              path + ': no animation/controller remains for the removed Bedrock models')

if args.mappings:
    mappings = json.loads(args.mappings.read_text(encoding='utf-8'))
    entries = [entry for group in mappings['items'].values() for entry in group]
    for weapon in removed:
        check(not any(weapon in entry.get('model', '') or weapon in entry.get('bedrock_identifier', '')
                      for entry in entries), weapon + ': no Geyser mapping remains')

if args.baseline_pack:
    with zipfile.ZipFile(args.pack) as current, zipfile.ZipFile(args.baseline_pack) as baseline:
        old = set(baseline.namelist())
        new = set(current.namelist())
        removed_paths = {name for name in old if any(weapon in name for weapon in removed)}
        check(not removed_paths & new, 'all target-specific Bedrock files are removed')
        allowed_changed = removed_paths | {
            'animations/adventurer_weapons.animation.json',
            'render_controllers/adventurer_weapons.render.json',
            'textures/item_texture.json',
            'manifest.json',
        }
        for name in sorted((old | new) - allowed_changed):
            check(name in old and name in new and baseline.read(name) == current.read(name),
                  'preserve unrelated Bedrock asset: ' + name)

print(f'PASS {checks} checks: disabled Thornwhisper and Runelock assets are absent from Bedrock')
