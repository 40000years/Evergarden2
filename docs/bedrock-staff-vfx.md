# Bedrock staff effects hotfix

Advance Magic `1.12.1-bedrock-staff-vfx` · Bedrock pack `2.15.1` · 19 October 2026

The supplied fourteen-second Bedrock recording shows a large purple effect
detached from the Dark staff, covering the middle and upper view. The old
attachable drew three intersecting 32×32 effect sheets across the entire staff.
These used a transparent emissive material with culling disabled, which does not
have the same cutout behavior as the approved Java item renderer.

Bedrock now uses small opaque 3D glints close to the crown. The first-person view
has two; third person has three. There are no full-staff effect sheets or
full-frame RGBA effect sprites in the new Bedrock attachables. Elemental sculpture,
gem animation, native hand contact and camera poses remain unchanged.

Server dust from staff ambient effects is sent to Java viewers only. This avoids
putting additional world particles close to a Bedrock camera, while Java viewers
keep the existing ambient effect, including when looking at a Bedrock holder.

The approved Java pack is checked byte-for-byte against the previous release.
Its models, textures, icons and effects are unchanged. The existing Evergarden
JAR remains unchanged too.

Final checks passed: 246 Paper/Evergarden regressions and 159 actual
Geyser/ModelEngine checks. The latter include eleven outgoing-packet assertions
for unchanged Java color, position, size, count, particle limiter and distance,
and absent server dust for Bedrock owners and observers. Native Bedrock checks
also verify solid crown bounds, two first-person glints and no effect sheets.

## Installation

Replace only `dist/advance-magic.jar` while the server is stopped, then restart.
Keep the installed Evergarden JAR. Reconnect Bedrock clients so they receive pack
version `2.15.1`. External pack hosting must use the new Bedrock archive; Java
resource-pack contents and hash do not change.

The work was verified in the workspace and disposable Paper/Geyser/ModelEngine
servers. It has not been deployed to the player's server, and the revised effects
still need a connected Bedrock-client visual check.

## Rebuild

```powershell
python tools/elemental_staff_assets.py --bedrock-only
python advance-magic/tools/build_packs.py
```

The normal full generator also produces the corrected Bedrock effects. The
Bedrock-only option updates that edition without rewriting the approved Java
exports or the editable solid-model blueprints.
