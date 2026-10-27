"""Build Advance Magic 1.34.0 and Evergarden e2.152: published pack copies are embedded in the JAR.

A JAR now carries pack-mirror.properties (written by tools/publish_pack_mirror.py --release): the
fingerprint and SHA-1 of every published copy. A server whose pack matches a line offers that copy at
once, without waiting for or needing a network check, so packs come from the copy even on hosts that
block outbound HTTPS. A background HEAD only drops a copy that is definitely gone (404/410). Packs
without a line are checked over the network as in 1.33.1 / e2.150.

Over the delivered JARs this recompiles only PackMirror and MirrorLookup and sets each plugin.yml version;
Evergarden e2.152 was built over e2.151-dungeon-balance (another session's release, kept intact). Run
tools/publish_pack_mirror.py --release on the promoted JARs to embed the copies.

    python tools/build_pack_embedded_release.py [--magic-base JAR] [--garden-base JAR] [--promote]
"""
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'tools'))
import build_pack_mirror_release as release  # noqa: E402
import build_pack_mirror_wait_release as wait  # noqa: E402,F401  (installs the config check)

release.OUT = ROOT / 'output/pack-embedded-20261026'
owned = ('com/example/advancemagic/pack/delivery/PackMirror', 'com/example/advancemagic/pack/delivery/MirrorLookup')
release.MODULES = {
    'advance-magic': ('1.34.0-pack-embedded', owned),
    'evergarden': ('3.0.0-e2.152-pack-embedded', owned),
}

if __name__ == '__main__':
    release.main()
