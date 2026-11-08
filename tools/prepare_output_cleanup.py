"""Inventory disposable output, archive small evidence, and retain the working build/test kit.

This script prepares a manifest only. Deletion is done by clean_output.ps1, which validates
the workspace boundary again and never follows junctions or symbolic links.
"""
import hashlib
import json
import os
from pathlib import Path
import shutil
import stat
import sys
import time
import zipfile

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / 'output'
STATE = OUTPUT / 'output-cleanup-20261029'
sys.path.insert(0, str(ROOT / 'tools'))
import make_buildkit

KEEP_TREES = [
    'pet-audit-paper-20261029-final', 'optional-floodgate-proof',
    'pet-egg-recovery-release', 'audit-guide-release',
    'github-release-resources-e2.171-am1.38.2', 'workspace-disk-audit-20261029',
    'output-cleanup-20261029',
    'distinct-basics-final-validation-20261013/libraries',
    'distinct-basics-final-validation-20261013/versions',
    'distinct-basics-final-validation-20261013/cache',
    'weapon-remodel-20261023/before', 'malefeasance-remodel-20261026/before',
    'adventurer-ranged-assets-20261022/art', 'light-reference-v3', 'imagegen', 'faction-bosses',
]
EXCLUDED_ARCHIVE_PARTS = {'plugins', 'libraries', 'versions', 'cache', '.git', '.paper-remapped',
                          'assets', 'resource pack', 'resource-packs', 'region', 'entities', 'poi',
                          'playerdata', 'stats', 'advancements', 'datapacks', 'data', 'node_modules',
                          'site-packages', '__pycache__', 'venv', '.venv'}
TEXT_SUFFIXES = {'.py', '.java', '.js', '.mjs', '.ps1', '.sh', '.md', '.txt', '.log', '.args',
                 '.json', '.yml', '.yaml', '.properties'}


def sha(path):
    digest = hashlib.sha256()
    with path.open('rb') as source:
        while chunk := source.read(1048576):
            digest.update(chunk)
    return digest.hexdigest()


def main():
    sys.stdout.reconfigure(line_buffering=True)
    if OUTPUT.is_symlink() or OUTPUT.resolve() != ROOT / 'output':
        raise RuntimeError('output itself must be a real directory inside this workspace')
    STATE.mkdir(exist_ok=True)
    manifest_path = STATE / 'manifest.json'
    if manifest_path.exists():
        raise RuntimeError('A cleanup manifest already exists; inspect it rather than replacing it')
    started = time.time()
    free_before = shutil.disk_usage(ROOT).free
    required = make_buildkit.collect()
    if any(not path.is_file() for path in required):
        raise RuntimeError('Required build-kit files are missing')
    kit = OUTPUT / 'buildkit-20261027.zip'
    with zipfile.ZipFile(kit) as archive:
        entries = set(archive.namelist())
        if any(path.relative_to(ROOT).as_posix() not in entries for path in required):
            raise RuntimeError('Existing buildkit does not cover the required dependencies')
        if archive.testzip():
            raise RuntimeError('Buildkit failed its CRC check')
    keep_files = set(required)
    # Small loose diagnostic/source files and the private kit do not justify aggressive pruning.
    keep_files.update(path for path in OUTPUT.iterdir() if path.is_file() and not path.is_symlink())
    keep_trees = {OUTPUT / name for name in KEEP_TREES}
    keep_trees.update(path for path in OUTPUT.iterdir() if path.is_dir() and 'clip-review' in path.name)
    # A locked world may belong to an active test whose command line uses relative paths.
    audit = json.loads((OUTPUT / 'workspace-disk-audit-20261029/summary.json').read_text(encoding='utf8'))
    locked = []
    import msvcrt
    for row in audit['fixture_roots']:
        fixture = ROOT / row['path']
        if not fixture.is_dir() or fixture.is_symlink():
            continue
        for lock in fixture.glob('*/session.lock'):
            if lock.parent.is_symlink():
                continue
            try:
                with lock.open('r+b') as handle:
                    msvcrt.locking(handle.fileno(), msvcrt.LK_NBLCK, 1)
                    msvcrt.locking(handle.fileno(), msvcrt.LK_UNLCK, 1)
            except OSError:
                top = OUTPUT / lock.relative_to(OUTPUT).parts[0]
                keep_trees.add(top)
                locked.append(str(lock.relative_to(ROOT)))
    ancestors = {OUTPUT}
    for path in keep_files | keep_trees:
        parent = path.parent
        while parent != OUTPUT:
            if not parent.is_relative_to(OUTPUT):
                raise RuntimeError('Keep path escaped output: ' + str(path))
            ancestors.add(parent)
            parent = parent.parent
    targets = []
    errors = []
    archive_records = []
    total_bytes = delete_bytes = files = links = 0
    stack = [(OUTPUT, False, False)]
    last = time.monotonic()
    evidence = STATE / 'historical-evidence.zip'
    with zipfile.ZipFile(evidence, 'w', zipfile.ZIP_DEFLATED, compresslevel=6) as saved:
        while stack:
            directory, disposable, inherited_keep = stack.pop()
            protected = inherited_keep or directory in keep_trees
            try:
                with os.scandir(directory) as entries:
                    for entry in entries:
                        path = Path(entry.path)
                        try:
                            metadata = entry.stat(follow_symlinks=False)
                            reparse = stat.S_ISLNK(metadata.st_mode) or bool(getattr(metadata, 'st_file_attributes', 0) & stat.FILE_ATTRIBUTE_REPARSE_POINT)
                            retained = protected or path in keep_files or path in keep_trees
                            remove = not retained and (disposable or path not in ancestors)
                            if remove and not disposable:
                                targets.append(str(path.relative_to(ROOT)))
                            if reparse:
                                links += 1
                                continue
                            if stat.S_ISDIR(metadata.st_mode):
                                if path != STATE:
                                    stack.append((path, remove, retained))
                                continue
                            if not stat.S_ISREG(metadata.st_mode):
                                continue
                            files += 1
                            total_bytes += metadata.st_size
                            if not remove:
                                continue
                            delete_bytes += metadata.st_size
                            relative = path.relative_to(OUTPUT)
                            parts = {part.lower() for part in relative.parts[:-1]}
                            if path.suffix.lower() in TEXT_SUFFIXES and metadata.st_size <= 16 * 1048576 and not parts.intersection(EXCLUDED_ARCHIVE_PARTS) and not any('classes' in part for part in parts):
                                try:
                                    data = path.read_bytes()
                                    name = path.relative_to(ROOT).as_posix()
                                    saved.writestr(name, data)
                                    archive_records.append({'path': name, 'bytes': len(data), 'sha256': hashlib.sha256(data).hexdigest()})
                                except OSError as error:
                                    errors.append({'path': str(path.relative_to(ROOT)), 'error': str(error)})
                        except OSError as error:
                            errors.append({'path': str(path.relative_to(ROOT)), 'error': str(error)})
            except OSError as error:
                errors.append({'path': str(directory.relative_to(ROOT)), 'error': str(error)})
            if time.monotonic() - last > 10:
                print(f'Inventoried {files:,} files; {delete_bytes / 2**30:.2f} GiB removable; archived {len(archive_records):,} reports/sources', flush=True)
                last = time.monotonic()
        saved.writestr('ARCHIVE-INDEX.json', json.dumps(archive_records, indent=2))
    with zipfile.ZipFile(evidence) as archive:
        if archive.testzip():
            raise RuntimeError('Historical evidence archive failed its CRC check')
    critical = {str(path.relative_to(ROOT)): sha(path) for path in required + [kit, ROOT / 'dist/evergarden.jar', ROOT / 'dist/advance-magic.jar']}
    manifest = {'workspace': str(ROOT), 'output': str(OUTPUT), 'created_unix': time.time(),
                'scan_started_unix': started, 'free_bytes_before': free_before,
                'scanned_bytes': total_bytes, 'delete_bytes_estimate': delete_bytes,
                'files_scanned': files, 'links_not_followed': links,
                'keep_files': sorted(str(path.relative_to(ROOT)) for path in keep_files),
                'keep_trees': sorted(str(path.relative_to(ROOT)) for path in keep_trees if path.exists()),
                'protected_sha256': critical, 'delete_targets': sorted(targets),
                'historical_archive': str(evidence.relative_to(ROOT)), 'archive_files': len(archive_records),
                'archive_sha256': sha(evidence), 'locked_worlds_retained': locked, 'scan_errors': errors}
    manifest_path.write_text(json.dumps(manifest, indent=2) + '\n', encoding='utf8')
    print(json.dumps({'manifest': str(manifest_path), 'delete_targets': len(targets),
                      'estimated_GiB': round(delete_bytes / 2**30, 3), 'historical_reports_sources': len(archive_records),
                      'archive_MiB': round(evidence.stat().st_size / 2**20, 2), 'locked_worlds': len(locked),
                      'scan_errors': len(errors)}, indent=2))


if __name__ == '__main__':
    main()
