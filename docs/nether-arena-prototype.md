# Nether arena prototype

An experimental Nether hall based on the supplied reference: dark masonry,
basalt pillars, red geometric floor inlays, cyan alcoves and warm lights. The
authored hall is 105 × 105 blocks and 48 blocks high, with a clear central
court. It contains no boss or boss encounter.

The [overview](../previews/dimensional-sanctuaries/nether-arena-cutaway.png) and
[front view](../previews/dimensional-sanctuaries/nether-arena-front-cutaway.png)
are renders of the actual block asset. The roof and front wall are removed in
the images only; these are not screenshots from a game client.

## Trying it in a test world

The prototype JAR is built at `output/nether-arena-prototype-20261029/evergarden.jar`.
It is separate from `dist/evergarden.jar`. Use it with the existing paired
Advance Magic JAR in an isolated test server. In that server's
`plugins/Evergarden/config.yml`, enable the generator and restrict it to the
test Nether's exact Bukkit world name:

```yaml
nether-arena:
  enabled: true
  worlds:
    - world_nether
```

Restart the test server after changing the configuration. As an administrator
in that Nether, use `/sanctuary locate nether-arena` or
`/sanctuary tp nether-arena`. The teleport checks its landing floor and
headroom before placing the player. The optional final argument is a search
radius in 1–32 placement cells; each cell is 1,024 blocks wide.

This is a visual and world-generation prototype, disabled by default. It
excavates an enclosed chamber at a fixed Y, and the outer shell prevents lava
entering. It does not yet find a natural cave, create a walkable connection
from outside, or check for Nether fortresses and bastions. Use a new test
world: the first enable records existing region files and loaded chunks as
excluded, but disabling the generator while a site is only partially generated
can still leave an incomplete building if those chunks generate before it is
re-enabled. Do not use this prototype as production world generation.
The initial cleanup also repairs newly generated chunks for up to ten minutes
while the same server process is running;
ordinary player block edits are detected and left alone, but edits made directly
by another plugin or command without a corresponding block event may be
overwritten during that window.

## Rebuilding and verification

Run `python tools/build_nether_arena.py` to regenerate the immutable geometry
and preview images. Run `python tools/build_nether_arena_prototype.py` from the
repository root to compile the three affected class families over the current
`dist/evergarden.jar` baseline and create the candidate JAR under `output/`.
Neither build copies a JAR to `dist/` or a server.

The Paper probe copies an existing disposable Paper world into a new isolated
fixture and runs two boots. Supply that world with `--old-world` and choose a
new, unused `--server` directory under `output/`:

```text
python tests/nether-arena/run.py --jar output/nether-arena-prototype-20261029/evergarden.jar --old-world output/nether-arena-probe-isolated-20261029/arena_probe --server output/nether-arena-probe-recheck
```

See the fixture's `nether-arena-result-*.txt` files for the actual result.

The candidate with SHA-256
`6f9e39c32efeb6e209deef0dbb29735025e08b4a0b47fcc2ff9aa208d29a87aa`
passed two boots on Paper 26.3 in `output/nether-arena-probe-final2-20261029/`.
The probe checked the old-region exclusion, deterministic negative-coordinate
placement, arena clearance, authored materials in unedited chunks, the sealed
boundary, sampled lava and roof bedrock, unloading and reloading a fresh chunk,
and preserved edits after restart. The player-place handler was exercised
directly; no connected game client or player-issued teleport was tested.
