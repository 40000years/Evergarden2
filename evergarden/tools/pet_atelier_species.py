"""Dispatch for the 57 Pet Atelier companions (the expansion roster in pet_expansion_*.json).

Each species is a function named after its id (with a leading underscore) in one
of the atelier modules. The 37 original species are built elsewhere and never here.
"""
from __future__ import annotations

import importlib

from pet_expansion_catalog import SPECIES as ROSTER

MODULES = ('pet_atelier_quadrupeds', 'pet_atelier_small', 'pet_atelier_critters', 'pet_atelier_tiny',
           'pet_atelier_rare', 'pet_atelier_rare_beasts', 'pet_atelier_rare_sky')
_BUILDERS = None


def builders():
    global _BUILDERS
    if _BUILDERS is None:
        found = {}
        for name in MODULES:
            try:
                module = importlib.import_module(name)
            except ModuleNotFoundError as error:
                if error.name != name:
                    raise
                continue
            for key, value in vars(module).items():
                if key.startswith('_') and not key.startswith('__') and callable(value) and key[1:] in ROSTER:
                    found[key[1:]] = value
        _BUILDERS = found
    return _BUILDERS


def available():
    return tuple(s for s in ROSTER if s in builders())


def build(species, stage):
    return builders()[species](stage)
