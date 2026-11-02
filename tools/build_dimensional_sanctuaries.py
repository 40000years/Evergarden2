#!/usr/bin/env python3
"""Compile the DimensionalSanctuaries classes over dist/evergarden.jar and package the 4 ODS1 assets."""
from __future__ import annotations

from pathlib import Path
import shutil
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'tools'))
import build_plugin_jars as build  # noqa: E402

OUT = ROOT / 'output/dimensional-sanctuaries-20261029'
OUT.mkdir(parents=True, exist_ok=True)
build.OUT = OUT

SRC = ROOT / 'evergarden/src/main/java'
OWN = (
    'com/example/voidscape/VoidscapePlugin',
    'com/example/voidscape/boss/BossHitbox',
    'com/example/voidscape/boss/RealmBossController',
    'com/example/voidscape/boss/RealmSanctuaryEncounters',
    'com/example/voidscape/boss/SanctuaryEncounters',
    'com/example/voidscape/boss/WorldBossManager',
    'com/example/voidscape/enchant/UniqueAbilityListener',
    'com/example/voidscape/world/OverworldSanctuaries',
    'com/example/voidscape/world/StructureProtection',
    'com/example/voidscape/world/NetherArenaWorldgen',
    'com/example/voidscape/world/DimensionalSanctuaries',
    'com/example/voidscape/world/NoiseProbe',
    'com/example/voidscape/world/VanillaDimensionStructures',
)
SOURCES = [SRC / (p + '.java') for p in OWN]

build.compile_sources(
    'dimensional-sanctuaries',
    SOURCES,
    [str(ROOT / 'dist/evergarden.jar'), str(ROOT / 'dist/advance-magic.jar')] + build.pinned_classpath(build.EVERGARDEN_ARGS),
    OUT / 'classes',
)


def replaced(name: str) -> bool:
    return any(name == p + '.class' or name.startswith(p + '$') for p in OWN)


ASSETS = [
    'dimensional-sanctuaries/nether-arena.ows',
    'dimensional-sanctuaries/v1/crown_fortress.ods',
    'dimensional-sanctuaries/v1/bone_temple.ods',
    'dimensional-sanctuaries/v1/crystal_city.ods',
    'dimensional-sanctuaries/v1/void_ring.ods',
]

candidate = OUT / 'evergarden.jar'
with zipfile.ZipFile(ROOT / 'dist/evergarden.jar') as base, zipfile.ZipFile(candidate, 'w', zipfile.ZIP_DEFLATED) as jar:
    for item in base.infolist():
        if replaced(item.filename) or item.filename in (*ASSETS, 'plugin.yml', 'config.yml'):
            continue
        jar.writestr(item, base.read(item.filename))
    for path in sorted((OUT / 'classes').rglob('*.class')):
        name = path.relative_to(OUT / 'classes').as_posix()
        if not replaced(name):
            raise RuntimeError('Unexpected class ' + name)
        jar.write(path, name)
    plugin_lines = base.read('plugin.yml').decode('utf8').splitlines(keepends=True)
    plugin_lines = ["version: '3.0.0-dimensional-sanctuaries'\n" if line.startswith('version:') else line for line in plugin_lines]
    jar.writestr('plugin.yml', ''.join(plugin_lines).encode('utf8'))
    jar.write(ROOT / 'evergarden/src/main/resources/config.yml', 'config.yml')
    for asset in ASSETS:
        asset_path = ROOT / 'evergarden/src/main/resources' / asset
        if asset_path.exists():
            jar.write(asset_path, asset)
        else:
            raise FileNotFoundError(f'Missing required sanctuary asset: {asset_path}')

build.check_jar(candidate)
dist_jar = ROOT / 'dist/evergarden.jar'
shutil.copy2(candidate, dist_jar)
build.check_jar(dist_jar)
print(dist_jar)
