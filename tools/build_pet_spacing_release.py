"""Build only companion spacing over the current delivered JAR; validate before promotion."""
import build_pet_grounding_release as release

release.OUT = release.ROOT / 'output/pet-spacing-release'
release.VERSION = '3.0.0-e2.197-pet-spacing'
release.OWNED = ('pet/PetCompanions',)
release.PROBE = release.ROOT / 'evergarden/tests/PetSpacingChecks.java'
release.DATE = (2026, 11, 8, 0, 0, 0)

if __name__ == '__main__':
    release.main()
