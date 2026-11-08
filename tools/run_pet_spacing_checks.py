"""Run pair overlap, following, flight and rescue regressions on disposable cached Paper."""
import run_pet_grounding_checks as checks

checks.OUT = checks.ROOT / 'output/pet-spacing-release'
checks.PROBE = checks.ROOT / 'evergarden/tests/PetSpacingChecks.java'

if __name__ == '__main__':
    checks.main()
