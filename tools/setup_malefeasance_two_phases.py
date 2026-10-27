"""Setup two-phase Malefeasance:
Phase 1: arkhaia_malefeasance.bbmodel (Classic Warlock, Red & Purple magic)
Phase 2: arkhaia_malefeasance_phase2.bbmodel (Astral Arch-Sorceress, Cyan & Gold magic, 50% HP)
"""
from pathlib import Path
import shutil
import json

ROOT = Path(__file__).resolve().parents[1]

# 1. Prepare Model Files in evergarden resources
rivers_dir = ROOT / "evergarden/src/main/resources/modelengine/rivers"
bak_path = rivers_dir / "arkhaia_malefeasance.bbmodel.bak"
phase1_path = rivers_dir / "arkhaia_malefeasance.bbmodel"
phase2_path = rivers_dir / "arkhaia_malefeasance_phase2.bbmodel"

# Restore Phase 1 model from backup (Classic Malefeasance)
print("Restoring Phase 1 model from backup...")
shutil.copyfile(bak_path, phase1_path)

# Phase 2 model: load current newly generated Astral Sorceress and set model_identifier = 'arkhaia_malefeasance_phase2'
print("Configuring Phase 2 model...")
# Let's read the current model (which is the Astral Sorceress)
with open(phase2_path if phase2_path.exists() else phase1_path, 'r', encoding='utf-8') as f:
    # Actually let's load from the recently built sorceress file
    pass

# We will regenerate Phase 2 properly using a modified generator or script.
