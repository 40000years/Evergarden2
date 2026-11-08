"""Build/validate/promote mythical riding over e2.177. All resource-pack entries stay identical.

python tools/build_mythical_riding_release.py [--validate|--promote]
"""
import argparse
import build_mythical_pet_release as release

release.OUT = release.ROOT / 'output/mythical-riding'
release.VERSION = '3.0.0-e2.178-mythical-riding'
release.BASE_SHA = '5c23ac74901fe7f9c3a3bb3c17dc2941df027b0da91aced68974f74221871474'
release.FAMILIES = tuple('com/example/voidscape/pet/' + n for n in ('PetRiding', 'PetBody', 'PetBodies', 'PetCompanions', 'PetMenu'))
release.RESOURCES = {'config.yml': release.RES / 'config.yml'}
release.PROBES = [release.ROOT / 'tests/model-engine' / n for n in (
    'PetSystemNativeProbe.java', 'MythicalPetProbe.java', 'MythicalRidingProbe.java')]
release.PROBE_MAIN = 'MythicalRidingProbe'
release.check_assets = lambda old: None  # Candidate build preserves every asset entry from the baseline byte-for-byte.

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--validate', action='store_true')
    parser.add_argument('--promote', action='store_true')
    args = parser.parse_args()
    release.promote() if args.promote else release.validate() if args.validate else release.candidate_build()
