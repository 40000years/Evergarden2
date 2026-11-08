"""No-network regressions for immutable published packs and their exact download SHA-1s."""
import contextlib
import hashlib
import importlib.util
import io
import json
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch
import zipfile

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location('publish_pack_mirror', ROOT / 'tools/publish_pack_mirror.py')
mirror = importlib.util.module_from_spec(spec)
spec.loader.exec_module(mirror)


def zipped(label, compression):
    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, 'w') as archive:
        for name, data in [('pack.mcmeta', b'{"pack":{"description":"test","pack_format":34}}'),
                           ('assets/test/payload.txt', (label * 100).encode())]:
            archive.writestr(zipfile.ZipInfo(name, (2026, 1, 1, 0, 0, 0)), data, compress_type=compression)
    return buffer.getvalue()


def git(cwd, *args, binary=False):
    return subprocess.run(['git', *args], cwd=cwd, check=True, capture_output=True, text=not binary).stdout


class PublicationChecks(unittest.TestCase):
    def setUp(self):
        self.root = Path(tempfile.mkdtemp(prefix='mirror-compat-', dir=ROOT / 'output'))
        self.main, self.repo, self.stage, self.remote = [self.root / n for n in ('main', 'publisher', 'stage', 'remote.git')]
        self.main.mkdir(); self.stage.mkdir()
        git(self.root, 'init', '--bare', str(self.remote))
        git(self.main, 'init')
        git(self.main, 'config', 'user.name', 'Pack test')
        git(self.main, 'config', 'user.email', 'pack-test@example.invalid')
        git(self.main, 'remote', 'add', 'origin', str(self.remote))
        git(self.main, 'checkout', '--orphan', 'packs')
        self.old = zipped('old', zipfile.ZIP_STORED)
        self.old_key = mirror.fingerprint(self.old)
        self.old_name = 'judge/' + self.old_key + '.zip'
        (self.main / 'judge').mkdir()
        (self.main / self.old_name).write_bytes(self.old)
        (self.main / 'README.md').write_text('Existing published packs\n', encoding='utf8')
        git(self.main, 'add', '.')
        git(self.main, 'commit', '-m', 'Old published release')
        git(self.main, 'push', 'origin', 'packs')
        git(self.root, 'clone', '--branch', 'packs', str(self.remote), str(self.repo))
        git(self.repo, 'config', 'user.name', 'Pack test')
        git(self.repo, 'config', 'user.email', 'pack-test@example.invalid')
        self.paths = patch.multiple(mirror, ROOT=self.main, REPO=self.repo, STAGE=self.stage)
        self.paths.start()

    def tearDown(self):
        self.paths.stop()
        # Keep these tiny Git fixtures in output for inspection, like the Paper release fixtures.

    def row(self, data):
        key = mirror.fingerprint(data)
        path = self.stage / 'judge' / (key + '.zip')
        path.parent.mkdir(exist_ok=True)
        path.write_bytes(data)
        return dict(pack='judge', fingerprint=key, sha1=hashlib.sha1(data).hexdigest(), bytes=len(data),
                    source='test', url='https://example.invalid/judge/' + key + '.zip')

    def test_old_zip_and_its_hash_survive_a_rebuilt_equivalent_and_new_pack(self):
        rebuilt = zipped('old', zipfile.ZIP_DEFLATED)
        self.assertEqual(mirror.fingerprint(rebuilt), self.old_key)
        self.assertNotEqual(hashlib.sha1(rebuilt).digest(), hashlib.sha1(self.old).digest())
        old_row, new_row = self.row(rebuilt), self.row(zipped('new', zipfile.ZIP_DEFLATED))
        before = mirror.tree('HEAD')
        mirror.push([old_row, new_row])
        self.assertEqual(git(self.remote, 'show', 'packs:' + self.old_name, binary=True), self.old)
        self.assertEqual((self.repo / self.old_name).read_bytes(), self.old)
        self.assertEqual(old_row['sha1'], hashlib.sha1(self.old).hexdigest())
        self.assertEqual((self.stage / self.old_name).read_bytes(), self.old)
        mirror.require_preserved(before, mirror.tree('HEAD'))
        report = json.loads((self.stage / 'publication.json').read_text(encoding='utf8'))
        self.assertTrue(report['published'])
        self.assertEqual(report['preserved_files'], 2)
        self.assertEqual(report['added_files'], ['judge/' + new_row['fingerprint'] + '.zip'])
        manifest = mirror.manifest(mirror.published_copies([old_row, new_row])).decode()
        self.assertIn('judge/' + self.old_key + '=' + hashlib.sha1(self.old).hexdigest(), manifest)
        index = json.loads((self.stage / 'index.json').read_text(encoding='utf8'))
        self.assertEqual(index[0]['sha1'], hashlib.sha1(self.old).hexdigest())
        with patch.object(mirror.urllib.request, 'urlopen', side_effect=lambda *a, **k: io.BytesIO(self.old)):
            mirror.verify([old_row], attempts=1)

    def test_cannot_publish_without_verifying_the_remote_baseline(self):
        git(self.repo, 'remote', 'set-url', 'origin', str(self.root / 'missing.git'))
        before = git(self.remote, 'rev-parse', 'packs').strip()
        with self.assertRaisesRegex(RuntimeError, 'Cannot verify the current published baseline'):
            mirror.push([self.row(zipped('new', zipfile.ZIP_DEFLATED))])
        self.assertEqual(git(self.remote, 'rev-parse', 'packs').strip(), before)

    def test_guard_rejects_both_modified_and_deleted_published_files(self):
        for after in ({'old.zip': 'changed'}, {}):
            with self.assertRaisesRegex(RuntimeError, 'previously published files changed or disappeared'):
                mirror.require_preserved({'old.zip': 'original'}, after)

    def test_same_pack_contents_with_wrong_zip_sha_cannot_pass_verification(self):
        row = self.row(self.old)
        rebuilt = zipped('old', zipfile.ZIP_DEFLATED)
        with patch.object(mirror.urllib.request, 'urlopen', side_effect=lambda *a, **k: io.BytesIO(rebuilt)):
            with contextlib.redirect_stdout(io.StringIO()), self.assertRaises(SystemExit):
                mirror.verify([row], attempts=1)

    def test_embedding_updates_only_the_current_jar_and_its_identical_twin(self):
        dist = self.main / 'dist'; dist.mkdir()
        def jar(version):
            data = io.BytesIO()
            with zipfile.ZipFile(data, 'w') as archive:
                archive.writestr('plugin.yml', 'version: ' + version)
                archive.writestr('test.class', b'unchanged plugin class')
                archive.writestr(mirror.MANIFEST, b'old/key=oldhash\n')
            return data.getvalue()
        current, twin, older = [dist / n for n in ('evergarden.jar', 'evergarden-new.jar', 'evergarden-old.jar')]
        current.write_bytes(jar('new')); twin.write_bytes(current.read_bytes()); older.write_bytes(jar('old'))
        old_bytes, tested = older.read_bytes(), current.read_bytes()
        mirror.embed([current], b'old/key=oldhash\nnew/key=newhash\n')
        self.assertEqual(older.read_bytes(), old_bytes)
        self.assertEqual(twin.read_bytes(), current.read_bytes())
        with zipfile.ZipFile(io.BytesIO(tested)) as before, zipfile.ZipFile(current) as after:
            self.assertEqual(set(before.namelist()), set(after.namelist()))
            for name in before.namelist():
                if name != mirror.MANIFEST:
                    self.assertEqual(before.read(name), after.read(name))


if __name__ == '__main__':
    unittest.main(verbosity=2)
