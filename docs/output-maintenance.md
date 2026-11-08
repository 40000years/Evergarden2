# Local output retained after cleanup

The cleanup on 2026-10-29 removes old isolated Paper servers, copied plugin data,
generated resource packs, compiler output and historical release staging. These are
reproducible and are not required to fix the current plugin source. The source tree,
`dist/`, `.git`, `.audit-plugins` and the real server are outside this cleanup.

Retained locally:

- `output/buildkit-20261027.zip` and its exact extracted dependency files: the pinned
  compiler argument files, Paper/dependency JARs, one base Paper runtime, and the
  official ModelEngine/Geyser/Floodgate dependencies. The kit is local only.
- `output/pet-audit-paper-20261029-final` and
  `output/optional-floodgate-proof/paper`: the current full integration fixture and
  the small fixture without optional plugins. The latter's runtime junction targets
  in `distinct-basics-final-validation-20261013` are also retained.
- `output/pet-egg-recovery-release`, `output/audit-guide-release` and the current
  GitHub release stage: recent candidates, rollback baselines and hash receipts.
- Earlier original weapon/model designs in `weapon-remodel-20261023/before`,
  `malefeasance-remodel-20261026/before`, and a small selection of source/reference art.
- `output/output-cleanup-20261029/historical-evidence.zip`: historical test reports,
  logs, argument files and loose source scripts from removed directories. Paths and
  SHA-256 hashes are listed in `ARCHIVE-INDEX.json` inside the archive. Referenced
  historical reports can be read from this ZIP even when their original directory
  was removed. Historical candidate JARs and generated worlds are not all retained.

Build the current source without replacing delivery JARs:

```powershell
python tools/build_plugin_jars.py --no-copy --output output/manual-build
```

Rerun the egg/pet regression on the retained isolated fixture:

```powershell
python tools/run_pet_egg_recovery_checks.py
```

If the extracted compiler dependencies are accidentally removed later, restore them
from the buildkit at their original paths. A new machine also needs Java/Python/Git;
see `docs/new-machine-setup.md`. The private dependency kit is not part of the public
GitHub binary release.

`tools/prepare_output_cleanup.py` creates the retention manifest and evidence archive;
`tools/clean_output.ps1` validates it and defaults to a dry run. Applying it requires
`-Execute`. The cleaner checks all protected hashes and output boundaries, unlinks
junctions without traversing them, and retains files modified after its manifest.
The manifest and deletion receipt document exactly what happened. Never use a blanket
recursive deletion of `output`: some build dependencies still live there.
