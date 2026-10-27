"""Stage, and with --push publish, the Java packs as public copies named by content fingerprint.

Servers look for <pack>/<fingerprint>.zip under each resource-pack.mirrors template (default: the
`packs` branch of this repository on raw.githubusercontent.com). The fingerprint covers the pack's
files, not its ZIP bytes, so a copy also matches the Judge pack that each server assembles itself.
Servers download a copy once and use it only when its files match exactly.

Packs: advance-magic and evergarden come from the release JARs. Judge is assembled at runtime
(bundled models plus ModelEngine's rigs). Without --judge, a local Paper server with the official
ModelEngine starts once (tests/arkhaia/run.py --pack-download) and the Judge pack it writes is
published; --judge ZIP publishes a judge-java.zip from elsewhere, such as a live server whose
ModelEngine setup differs.

After promoting a release, one command does everything: publish the packs, check them, and write
pack-mirror.properties (<pack>/<fingerprint> = <sha1> of every published copy) into both JARs, so
servers use the copies at once, without network checks, even where outbound HTTPS is blocked:

    python tools/publish_pack_mirror.py --release
    python tools/publish_pack_mirror.py [--judge ZIP ...] [--magic-jar JAR] [--garden-jar JAR] [--push] [--verify] [--embed]

--embed rewrites only that one entry; in dist/ the version-named copy with the same bytes is updated too.

--push commits the staged files to the `packs` branch (an orphan branch with only pack files) in
output/pack-mirror-repo and pushes it to origin. Files are only ever added, never replaced, so
servers running older releases keep finding their packs.
"""
import argparse
import datetime
import hashlib
import io
import json
import shutil
import subprocess
import sys
import time
import urllib.request
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
STAGE = ROOT / 'output/pack-mirror-stage'
REPO = ROOT / 'output/pack-mirror-repo'
BRANCH = 'packs'
RAW = 'https://raw.githubusercontent.com/40000years/Evergarden2/packs/{pack}/{fingerprint}.zip'
ENTRIES = {'advance-magic': 'resource-packs/advance-magic-java.zip', 'evergarden': 'resource-packs/evergarden-java.zip'}


def fingerprint(data):
    """Same as PackMirror.fingerprint: file entries in UTF-8 name order, name NUL length NUL bytes."""
    entries = {}
    with zipfile.ZipFile(io.BytesIO(data)) as archive:
        for info in archive.infolist():
            if info.is_dir():
                continue
            if info.filename in entries:
                raise ValueError('Duplicate pack entry ' + info.filename)
            entries[info.filename] = archive.read(info)
    digest = hashlib.sha1()
    for name in sorted(entries, key=lambda n: n.encode('utf8')):
        body = entries[name]
        digest.update(f'{name}\0{len(body)}\0'.encode('utf8'))
        digest.update(body)
    return digest.hexdigest()


def bundled_judge(garden_jar):
    """The Judge pack a server without ModelEngine serves: JudgePackService's bundled merge, rebuilt here."""
    entries = {}
    with zipfile.ZipFile(garden_jar) as jar:
        for part, addon in (('resource-packs/judge-java.zip', False), ('native-pets.zip', True), ('pet-portraits.zip', True)):
            if part not in jar.namelist():
                continue
            with zipfile.ZipFile(io.BytesIO(jar.read(part))) as archive:
                for info in archive.infolist():
                    name = info.filename
                    if info.is_dir() or addon and (not name.startswith('assets/') or name.startswith('assets/minecraft/shaders/')):
                        continue
                    body = archive.read(info)
                    if addon and name in entries and entries[name] != body:
                        raise ValueError('Graphics addon collides with an existing asset: ' + name)
                    entries[name] = body
    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, 'w', zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for name in sorted(entries):
            archive.writestr(zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0)), entries[name])
    return buffer.getvalue()


def runtime_judge(magic_jar, garden_jar):
    """Starts a throwaway Paper server with these JARs and ModelEngine; returns the Judge pack it serves."""
    server = ROOT / 'output' / ('pack-publish-' + datetime.datetime.now().strftime('%Y%m%d-%H%M%S'))
    command = [sys.executable, str(ROOT / 'tests/arkhaia/run.py'), '--template', str(ROOT / 'output/arkhaia-aggro-feedback-me-v4-20261014'),
               '--garden-jar', str(garden_jar), '--magic-jar', str(magic_jar), '--server', str(server),
               '--classpath-file', str(ROOT / 'output/cocytus-balance-20261025/build/classpath.txt'), '--direct-launch', '--pack-download']
    log = server.with_suffix('.log')
    with open(log, 'w', encoding='utf8') as out:
        subprocess.run(command, cwd=ROOT, stdout=out, stderr=subprocess.STDOUT, check=False)
    result = server / 'arkhaia-result.txt'
    judge = server / 'plugins/Evergarden/resource-packs/judge-java.zip'
    if not result.is_file() or not result.read_text(encoding='utf8').startswith('PASS') or not judge.is_file():
        raise RuntimeError(f'The pack server did not finish; see {log}')
    print('Judge pack from', judge.relative_to(ROOT))
    return judge


def git(*args, cwd=REPO, check=True):
    return subprocess.run(['git', *args], cwd=cwd, check=check, text=True, capture_output=True)


def stage(jars, judges):
    shutil.rmtree(STAGE, ignore_errors=True)
    rows = []
    packs = [(pack, jars[pack], entry) for pack, entry in ENTRIES.items()] + [('judge', jars['evergarden'], 'bundled')]
    packs += [('judge', path, None) for path in judges]
    for pack, path, entry in packs:
        if entry == 'bundled':
            data = bundled_judge(path)
        elif entry:
            with zipfile.ZipFile(path) as jar:
                data = jar.read(entry)
        else:
            data = Path(path).read_bytes()
        with zipfile.ZipFile(io.BytesIO(data)) as archive:
            if archive.testzip() is not None or 'pack.mcmeta' not in archive.namelist():
                raise ValueError(f'{path}: not a resource pack')
        key = fingerprint(data)
        target = STAGE / pack / (key + '.zip')
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(data)
        rows.append({'pack': pack, 'fingerprint': key, 'sha1': hashlib.sha1(data).hexdigest(), 'bytes': len(data),
                     'source': str(Path(path).relative_to(ROOT)) if Path(path).is_relative_to(ROOT) else str(path),
                     'url': RAW.format(pack=pack, fingerprint=key)})
    (STAGE / 'index.json').write_text(json.dumps(rows, indent=2) + '\n', encoding='utf8')
    return rows


def push(rows):
    origin = git('remote', 'get-url', 'origin', cwd=ROOT).stdout.strip()
    if not (REPO / '.git').exists():
        REPO.mkdir(parents=True, exist_ok=True)
        git('init')
        git('remote', 'add', 'origin', origin)
    if git('fetch', 'origin', BRANCH, check=False).returncode == 0:
        git('checkout', '-B', BRANCH, 'origin/' + BRANCH)
    elif git('rev-parse', '--verify', BRANCH, check=False).returncode != 0:
        git('checkout', '--orphan', BRANCH)
    added = []
    for row in rows:
        target = REPO / row['pack'] / (row['fingerprint'] + '.zip')
        if target.exists():
            if fingerprint(target.read_bytes()) != row['fingerprint']:
                raise RuntimeError(f'{target} exists with other contents')
            continue
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(STAGE / row['pack'] / target.name, target)
        added.append(target.relative_to(REPO).as_posix())
    readme = REPO / 'README.md'
    if not readme.exists():
        readme.write_text('# Evergarden pack mirror\n\nJava resource packs named by content fingerprint '
                          '(`<pack>/<fingerprint>.zip`). Servers verify every copy before offering it. '
                          'Written by `tools/publish_pack_mirror.py` on the main branch; files are only added.\n', encoding='utf8')
        added.append('README.md')
    if not added:
        print('Every staged pack is already published.')
        return
    git('add', *added)
    message = ('Add pack copies: ' + ', '.join(f"{r['pack']} {r['fingerprint'][:12]}" for r in rows)
               + '\n\nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>\n')
    git('commit', '-m', message)
    result = git('push', 'origin', BRANCH, check=False)
    if result.returncode:
        raise RuntimeError('git push failed:\n' + result.stderr)
    print('Pushed', len(added), 'files to', origin, BRANCH)


MANIFEST = 'pack-mirror.properties'


def published_copies(rows):
    """Every copy on the packs branch (from the publishing clone), plus this run's."""
    copies = {(row['pack'], row['fingerprint'], row['sha1']) for row in rows}
    if (REPO / '.git').exists():
        for path in REPO.glob('*/*.zip'):
            copies.add((path.parent.name, path.stem, hashlib.sha1(path.read_bytes()).hexdigest()))
    return sorted(copies)


def manifest(copies):
    lines = ['# Public copies published for these plugins: <pack>/<fingerprint>=<sha1> of the copy.',
             '# A server whose pack matches a line offers that copy first, without checking the network;',
             '# the client verifies the SHA-1. Written by tools/publish_pack_mirror.py --embed.']
    lines += [f'{pack}/{key}={sha1}' for pack, key, sha1 in copies]
    return ('\n'.join(lines) + '\n').encode('ascii')


def with_manifest(data, text):
    """The same JAR with only pack-mirror.properties replaced or added."""
    source = zipfile.ZipFile(io.BytesIO(data))
    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, 'w', zipfile.ZIP_DEFLATED) as out:
        for info in source.infolist():
            if info.filename != MANIFEST:
                out.writestr(info, source.read(info))
        out.writestr(zipfile.ZipInfo(MANIFEST, (2026, 1, 1, 0, 0, 0)), text, compress_type=zipfile.ZIP_DEFLATED)
    # writestr() moved each source ZipInfo's header_offset into the new archive, so compare with a fresh reader.
    result, original = zipfile.ZipFile(io.BytesIO(buffer.getvalue())), zipfile.ZipFile(io.BytesIO(data))
    for info in original.infolist():
        if info.filename != MANIFEST and result.read(info.filename) != original.read(info):
            raise RuntimeError('Entry changed while embedding: ' + info.filename)
    return buffer.getvalue()


def embed(jars, text):
    for jar in jars:
        jar = Path(jar).resolve()
        data = jar.read_bytes()
        with zipfile.ZipFile(io.BytesIO(data)) as archive:
            if MANIFEST in archive.namelist() and archive.read(MANIFEST) == text:
                print('Already embedded in', jar.name)
                continue
        twins = [path for path in jar.parent.glob(jar.stem + '-*.jar') if path.read_bytes() == data] if jar.parent == (ROOT / 'dist').resolve() else []
        updated = with_manifest(data, text)
        for target in [jar, *twins]:
            staged = target.with_suffix('.jar.staged')
            staged.write_bytes(updated)
            staged.replace(target)
            print('Embedded', MANIFEST, 'in', target.relative_to(ROOT), hashlib.sha256(updated).hexdigest()[:16])


def verify(rows, attempts=7):
    # raw.githubusercontent.com can answer 404 for a minute or two after a push, so failures are retried.
    pending = list(rows)
    for attempt in range(attempts):
        if attempt:
            time.sleep(min(60, 10 * 2 ** (attempt - 1)))
        failed = []
        for row in pending:
            try:
                request = urllib.request.Request(row['url'] + ('?retry=%d' % attempt if attempt else ''),
                                                 headers={'User-Agent': 'Evergarden-pack-mirror', 'Cache-Control': 'no-cache'})
                with urllib.request.urlopen(request, timeout=60) as response:
                    data = response.read()
                ok = fingerprint(data) == row['fingerprint']
            except OSError as error:
                ok, data = False, str(error).encode()
            if not ok:
                failed.append(row)
            if ok or attempt == attempts - 1:
                print(('OK   ' if ok else 'FAIL ') + row['url'] + ('' if ok else ' ' + data[:80].decode('utf8', 'replace')))
            else:
                print('WAIT ' + row['url'] + ' ' + data[:80].decode('utf8', 'replace'))
        pending = failed
        if not pending:
            return
    sys.exit(1)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--judge', type=Path, action='append', default=[],
                        help='A judge-java.zip written by a server (default: start a local test server and use its pack)')
    parser.add_argument('--magic-jar', type=Path, default=ROOT / 'dist/advance-magic.jar')
    parser.add_argument('--garden-jar', type=Path, default=ROOT / 'dist/evergarden.jar')
    parser.add_argument('--push', action='store_true', help='Commit to the packs branch and push it to origin')
    parser.add_argument('--verify', action='store_true', help='Download every staged pack from raw.githubusercontent.com')
    parser.add_argument('--embed', action='store_true', help='Write pack-mirror.properties into both JARs')
    parser.add_argument('--release', action='store_true', help='--push --verify --embed')
    args = parser.parse_args()
    if args.release:
        args.push = args.verify = args.embed = True
    judges = args.judge or [runtime_judge(args.magic_jar.resolve(), args.garden_jar.resolve())]
    rows = stage({'advance-magic': args.magic_jar, 'evergarden': args.garden_jar}, judges)
    for row in rows:
        print(f"{row['pack']:13} {row['fingerprint']}  sha1 {row['sha1']}  {row['bytes']:>9} bytes  {row['source']}")
    if args.push:
        push(rows)
    if args.verify:
        verify(rows)
    if args.embed:
        embed([args.magic_jar, args.garden_jar], manifest(published_copies(rows)))


if __name__ == '__main__':
    main()
