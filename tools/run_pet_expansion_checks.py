"""Run the full collection behavior probe on an isolated cached Paper server."""
import run_pet_grounding_checks as checks
import sys

checks.OUT=checks.ROOT/'output/pet-expansion-release'
checks.PROBE=checks.ROOT/'evergarden/tests/PetExpansionChecks.java'

if __name__=='__main__':
    if '--garden-jar' not in sys.argv:sys.argv.extend(['--garden-jar',str(checks.OUT/'evergarden.jar')])
    checks.main()
