"""Build Advance Magic 1.33.1 and Evergarden e2.150: packs wait for the public-copy check.

1.33.0 / e2.149 started checking a public copy in the background and offered the pack at once. Anyone
offered a pack before the check finished got only the server's own address, which a tunnel or proxy
may not carry. When ModelEngine finished importing, Judge always went out that way. MirrorLookup now holds the
player's batch while the check runs (up to 30 seconds), then offers the copy first; after the limit
the server's address alone is used, as before.

Over the delivered 1.33.0 / e2.149 JARs this recompiles only the owned classes and sets each plugin.yml
version; their config.yml already lists the mirrors. Every other entry stays byte-identical.

    python tools/build_pack_mirror_wait_release.py [--magic-base JAR] [--garden-base JAR] [--promote]
"""
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'tools'))
import build_pack_mirror_release as release  # noqa: E402

release.OUT = ROOT / 'output/pack-mirror-wait-20261026'
delivery = release.DELIVERY + ('com/example/advancemagic/pack/delivery/MirrorLookup',)
release.MODULES = {
    'advance-magic': ('1.33.1-pack-mirror-wait', delivery + ('com/example/advancemagic/pack/ResourcePackService',)),
    'evergarden': ('3.0.0-e2.150-pack-mirror-wait', delivery + ('com/example/voidscape/pack/ResourcePackService',
                                                               'com/example/voidscape/boss/JudgePackService')),
}


def keep_config(text):
    if '\n  mirrors:\n' not in text.replace('\r\n', '\n'):
        raise RuntimeError('The base JAR predates the mirrors config; build 1.33.0 / e2.149 first')
    return text


release.with_mirrors = keep_config

if __name__ == '__main__':
    release.main()
