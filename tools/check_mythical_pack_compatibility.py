"""Snapshot and verify immutable old pack URLs and old local releases around e2.177 publication.

Fetch origin/packs first. --capture records that baseline; default verifies it was preserved.
--verify-public additionally downloads every previous pack and checks its original SHA-1.
"""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import zipfile

import publish_pack_mirror as mirror

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'output/mythical-pets-release'
SNAPSHOT = OUT / 'mirror-before.json'


def sha(path):
    digest = hashlib.sha256()
    with path.open('rb') as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b''):
            digest.update(chunk)
    return digest.hexdigest()


def remote_bytes(revision, name):
    return subprocess.run(['git', 'show', revision + ':' + name], cwd=mirror.REPO,
                          check=True, capture_output=True).stdout


def capture():
    if SNAPSHOT.exists():
        raise RuntimeError('Keep the original pre-publication snapshot; it already exists')
    revision = mirror.git('rev-parse', 'origin/packs').stdout.strip()
    files = mirror.tree('origin/packs')
    rows = []
    for name in files:
        if not name.endswith('.zip'):
            continue
        pack, filename = name.split('/')
        key = filename.removesuffix('.zip')
        data = remote_bytes(revision, name)
        assert mirror.fingerprint(data) == key, 'Previously published fingerprint mismatch: ' + name
        rows.append(dict(pack=pack, fingerprint=key, sha1=hashlib.sha1(data).hexdigest(), bytes=len(data),
                         source='origin/packs:' + name, url=mirror.RAW.format(pack=pack, fingerprint=key)))
    current = {sha(ROOT / 'dist' / (n + '.jar')) for n in ('evergarden', 'advance-magic')}
    old_jars = {}
    manifests = {}
    known = {(r['pack'] + '/' + r['fingerprint']): r['sha1'] for r in rows}
    for pattern in ('evergarden-*.jar', 'advance-magic-*.jar'):
        for jar in sorted((ROOT / 'dist').glob(pattern)):
            digest = sha(jar)
            if digest not in current:
                old_jars[jar.relative_to(ROOT).as_posix()] = digest
            with zipfile.ZipFile(jar) as archive:
                if mirror.MANIFEST not in archive.namelist():
                    continue
                entries = {}
                for line in archive.read(mirror.MANIFEST).decode('ascii').splitlines():
                    if not line or line.startswith('#'):
                        continue
                    key, expected = line.split('=', 1)
                    # java.util.Properties uses the last value of a duplicate key in older manifests.
                    entries[key] = expected
                for key, expected in entries.items():
                    assert known.get(key) == expected, f'{jar.name}: published SHA-1 differs for {key}'
                manifests[jar.name] = entries
    snapshot = dict(revision=revision, files=files, packs=rows, old_jars=old_jars, manifests=manifests)
    SNAPSHOT.write_text(json.dumps(snapshot, indent=2) + '\n', encoding='utf8')
    print(f'Captured {len(rows)} existing packs, {len(old_jars)} older JARs, '
          f'{len(manifests)} release manifests; every recorded SHA-1 matches the existing published ZIP.')


def verify(public):
    before = json.loads(SNAPSHOT.read_text(encoding='utf8'))
    mirror.require_preserved(before['files'], mirror.tree('origin/packs'))
    for path, digest in before['old_jars'].items():
        assert sha(ROOT / path) == digest, 'Older release JAR changed: ' + path
    if public:
        mirror.verify(before['packs'])
    report = dict(baseline=before['revision'], current=mirror.git('rev-parse', 'origin/packs').stdout.strip(),
                  preserved_published_files=len(before['files']), old_pack_sha1s_preserved=len(before['packs']),
                  older_jars_unchanged=len(before['old_jars']), old_release_manifests_checked=len(before['manifests']),
                  public_downloads_verified=public, result='PASS')
    report_name = 'mirror-public-downloads.json' if public else 'mirror-compatibility.json'
    (OUT / report_name).write_text(json.dumps(report, indent=2) + '\n', encoding='utf8')
    print(json.dumps(report, indent=2))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--capture', action='store_true')
    parser.add_argument('--verify-public', action='store_true')
    args = parser.parse_args()
    capture() if args.capture else verify(args.verify_public)
