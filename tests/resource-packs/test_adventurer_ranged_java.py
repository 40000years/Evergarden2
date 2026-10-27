"""Verify disabled Adventurer ranged weapons are absent from the Java pack."""
from __future__ import annotations

import argparse
from pathlib import Path
import zipfile

ROOT = Path(__file__).resolve().parents[2]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--pack', type=Path, default=ROOT / 'evergarden/dist/evergarden-java.zip')
parser.add_argument('--baseline-pack', type=Path)
args = parser.parse_args()

removed = ('thornwhisper', 'runelock')
checks = 0
with zipfile.ZipFile(args.pack) as pack:
    names = set(pack.namelist())
    for weapon in removed:
        matches = sorted(name for name in names if weapon in name)
        assert not matches, (weapon, matches)
        checks += 1

if args.baseline_pack:
    with zipfile.ZipFile(args.pack) as current, zipfile.ZipFile(args.baseline_pack) as baseline:
        old, new = set(baseline.namelist()), set(current.namelist())
        removed_paths = {name for name in old if any(weapon in name for weapon in removed)}
        assert removed_paths and not (removed_paths & new), 'remove every target-specific Java model, item definition, and texture'
        changed = {name for name in old & new if baseline.read(name) != current.read(name)}
        assert not changed, ('unrelated Java pack entries changed', sorted(changed))
        checks += 2

print(f'PASS {checks} checks: Thornwhisper and Runelock have no Java model assets')
