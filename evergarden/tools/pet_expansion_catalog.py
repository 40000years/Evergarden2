"""Source roster for the 57 original companions added with the three rarity tiers."""
import json
from pathlib import Path

HERE = Path(__file__).resolve().parent
GROUPS = ('rare', 'common_a', 'common_b')
ENTRIES = tuple(entry for group in GROUPS
                for entry in json.loads((HERE / f'pet_expansion_{group}.json').read_text(encoding='utf8')))
SPECIES = tuple(entry['id'] for entry in ENTRIES)
RARE = tuple(entry['id'] for entry in ENTRIES if entry['rarity'] == 'rare')
COMMON = tuple(entry['id'] for entry in ENTRIES if entry['rarity'] == 'common')
TITLES = tuple(entry['display'] for entry in ENTRIES)
HOVERING = tuple(entry['id'] for entry in ENTRIES if entry['hover'])


def build(species, stage):
    # Pet Atelier (2026-11-08) rebuilt all 57 as clean blocks with painted coats; the slab sculpts below are kept
    # for reference only.
    from pet_atelier_species import build as atelier
    return atelier(species, stage)


def build_slab_sculpt(species, stage):
    if species in RARE:
        from pet_expansion_rare_sculpt import build as sculpt
    elif species in SPECIES[17:37]:
        from pet_expansion_common_a_sculpt import build as sculpt
    elif species in SPECIES[37:]:
        from pet_expansion_common_b_sculpt import build as sculpt
    else:
        raise ValueError(species)
    return sculpt(species, stage)
