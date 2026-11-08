"""Publish the delivered resource packs as a GitHub release on 40000years/Evergarden2.

The packs are taken byte-for-byte from the promoted plugin JARs (resource-packs/*), so the release holds exactly
what servers send. Pass --include-jars when publishing the plugin binaries is authorized. The tag points at the workstation
branch; re-running with the same tag only uploads assets that are missing, then every asset is downloaded back
and compared by SHA-1.

    python tools/publish_github_release.py [--dry-run] [--include-jars]
"""
import argparse
import hashlib
import json
import re
import subprocess
import sys
import urllib.error
import urllib.request
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
REPO = '40000years/Evergarden2'
BRANCH = 'workstation-2026-10-27'
API = 'https://api.github.com/repos/' + REPO
# (jar, entry inside the jar, published asset name)
ASSETS = (('evergarden.jar', 'resource-packs/evergarden-java.zip', 'evergarden-java.zip'),
          ('evergarden.jar', 'resource-packs/evergarden-bedrock.mcpack', 'evergarden-bedrock.mcpack'),
          ('evergarden.jar', 'resource-packs/geyser-mappings.json', 'evergarden-geyser-mappings.json'),
          ('evergarden.jar', 'resource-packs/judge-java.zip', 'judge-java.zip'),
          ('advance-magic.jar', 'resource-packs/advance-magic-java.zip', 'advance-magic-java.zip'),
          ('advance-magic.jar', 'resource-packs/advance-magic-bedrock.mcpack', 'advance-magic-bedrock.mcpack'),
          ('advance-magic.jar', 'resource-packs/geyser-mappings.json', 'advance-magic-geyser-mappings.json'))
TYPES = {'.zip': 'application/zip', '.mcpack': 'application/zip', '.jar': 'application/java-archive', '.json': 'application/json', '.txt': 'text/plain'}


def token():
    out = subprocess.run(['git', 'credential', 'fill'], input=f'protocol=https\nhost=github.com\npath={REPO}.git\n\n',
                         capture_output=True, text=True, timeout=60, check=True)
    fields = dict(line.split('=', 1) for line in out.stdout.splitlines() if '=' in line)
    if not fields.get('password'):
        raise RuntimeError('No stored GitHub credential for ' + REPO)
    return fields['password']


def call(method, url, secret, data=None, content_type='application/json'):
    request = urllib.request.Request(url, data=data, method=method, headers={
        'Authorization': 'Bearer ' + secret, 'Accept': 'application/vnd.github+json',
        'X-GitHub-Api-Version': '2022-11-28', 'User-Agent': 'evergarden-release', 'Content-Type': content_type})
    with urllib.request.urlopen(request, timeout=300) as response:
        body = response.read()
    return json.loads(body) if body else None


def version(jar):
    with zipfile.ZipFile(jar) as z:
        return re.search(r"(?m)^version: '([^']+)'", z.read('plugin.yml').decode('utf8')).group(1)


def main():
    sys.stdout.reconfigure(line_buffering=True)
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--dry-run', action='store_true', help='Stage the assets and notes without touching GitHub')
    parser.add_argument('--include-jars', action='store_true', help='Also publish the current plugin JARs (public repository)')
    parser.add_argument('--notes-file', type=Path, help='Append release-specific Markdown notes from a local file')
    parser.add_argument('--with-forge', action='store_true', help='With --include-jars, also attach dist/evergarden-forge.jar')
    args = parser.parse_args()
    garden, magic = version(ROOT / 'dist/evergarden.jar'), version(ROOT / 'dist/advance-magic.jar')
    number = re.search(r'-e2\.(\d+)', garden).group(1)
    tag = f'resources-e2.{number}-am{magic.split("-")[0]}'
    stage = ROOT / 'output' / ('github-release-' + tag)
    stage.mkdir(parents=True, exist_ok=True)
    files = {}
    jar_names = ('evergarden.jar', 'advance-magic.jar') + (('evergarden-forge.jar',) if args.with_forge else ())
    jar_bytes = {name: (ROOT / 'dist' / name).read_bytes() for name in jar_names}
    for jar, entry, name in ASSETS:
        with zipfile.ZipFile(ROOT / 'dist' / jar) as z:
            data = z.read(entry)
        (stage / name).write_bytes(data)
        files[name] = data
    if args.include_jars:
        for name, data in jar_bytes.items():
            (stage / name).write_bytes(data)
            files[name] = data
    with zipfile.ZipFile(ROOT / 'dist/evergarden.jar') as z:
        bedrock = json.loads(zipfile.ZipFile(__import__('io').BytesIO(z.read('resource-packs/evergarden-bedrock.mcpack'))).read('manifest.json'))['header']['version']
    with zipfile.ZipFile(ROOT / 'dist/advance-magic.jar') as z:
        magic_bedrock = json.loads(zipfile.ZipFile(__import__('io').BytesIO(z.read('resource-packs/advance-magic-bedrock.mcpack'))).read('manifest.json'))['header']['version']
    sums = ''.join(f'{hashlib.sha1(data).hexdigest()}  {name}\n' for name, data in files.items())
    (stage / 'SHA1SUMS.txt').write_text(sums, encoding='utf8')
    files['SHA1SUMS.txt'] = sums.encode('utf8')
    sha256s = ''.join(f'{hashlib.sha256(data).hexdigest()}  {name}\n' for name, data in files.items() if name != 'SHA1SUMS.txt')
    (stage / 'SHA256SUMS.txt').write_text(sha256s, encoding='utf8')
    files['SHA256SUMS.txt'] = sha256s.encode('utf8')
    rows = '\n'.join(f'| `{name}` | {len(data) / 1048576:.1f} MB | `{hashlib.sha1(data).hexdigest()[:12]}` |'
                     for name, data in files.items() if not name.endswith('SUMS.txt'))
    body = (f'Resource packs shipped inside **Evergarden {garden}** and **Advance Magic {magic}**, copied byte-for-byte from the plugin JARs.\n\n'
            f'| File | Size | SHA-1 |\n|---|---|---|\n{rows}\n\n'
            f'- Evergarden Bedrock pack **{".".join(map(str, bedrock))}**, Advance Magic Bedrock pack **{".".join(map(str, magic_bedrock))}**\n'
            '- Java players get the Java packs from the server automatically (public copies on the `packs` branch).\n'
            '- Bedrock: put the two `.mcpack` files in `plugins/Geyser-Spigot/packs/` and the two Geyser mapping files in '
            '`plugins/Geyser-Spigot/custom_mappings/`, then restart.\n'
            '- `judge-java.zip` is the bundled Judge pack; a server whose ModelEngine setup differs builds its own at startup.\n\n'
            'แพ็กทรัพยากรล่าสุด (คัดลอกจากไฟล์ปลั๊กอินที่ปล่อยแล้วทุกไบต์) · ฝั่ง Java เซิร์ฟเวอร์ส่งให้อัตโนมัติ · '
            'ฝั่ง Bedrock ใส่ .mcpack ใน Geyser packs และไฟล์ mapping ใน custom_mappings แล้วรีสตาร์ต\n')
    if args.include_jars:
        body += ('\n## Plugin update\n\n'
                 + ('- Includes the current `evergarden.jar`, `advance-magic.jar` and `evergarden-forge.jar` (Forge '
                    + version(ROOT / 'dist/evergarden-forge.jar') + ') from the validated local delivery. Install all three under the server plugins directory and restart.\n'
                    if args.with_forge else
                    '- Includes the current `evergarden.jar` and `advance-magic.jar` from the validated local delivery. Install both under the server plugins directory and restart.\n')
                 + '- Binary artifacts were built from the local working tree; the workstation tag is not a complete source snapshot of those changes.\n')
    if args.notes_file:
        body += '\n' + args.notes_file.read_text(encoding='utf8').strip() + '\n'
    (stage / 'RELEASE.md').write_text(body, encoding='utf8')
    print('tag', tag, '-', len(files), 'assets staged in', stage)
    if args.dry_run:
        return
    secret = token()
    # Refuse to publish a mixture if another build changed dist during staging.
    if any((ROOT / 'dist' / name).read_bytes() != data for name, data in jar_bytes.items()):
        raise RuntimeError('dist changed while staging the release')
    try:
        release = call('GET', f'{API}/releases/tags/{tag}', secret)
    except urllib.error.HTTPError as error:
        if error.code != 404:
            raise
        # A draft may not yet have a public tag and GET /releases/tags can return 404.
        matches = [item for item in call('GET', f'{API}/releases?per_page=100', secret)
                   if item['draft'] and item['tag_name'] == tag]
        if len(matches) > 1:
            raise RuntimeError('Multiple matching drafts; refusing to guess which release to update')
        if matches:
            release = matches[0]
            print('resuming draft', release['id'])
        else:
            release = call('POST', f'{API}/releases', secret, json.dumps({
                'tag_name': tag, 'target_commitish': BRANCH, 'name': f'{"Plugins + resources" if args.include_jars else "Resource packs"} · Evergarden {garden} · Advance Magic {magic}',
                'body': body, 'draft': True, 'prerelease': False}).encode('utf8'))
            print('created draft', release['id'])
    present = {asset['name']: asset for asset in release.get('assets', [])}
    upload = release['upload_url'].split('{')[0]
    for name, data in files.items():
        if name in present and present[name]['size'] == len(data):
            continue
        if name in present:
            raise RuntimeError('Existing asset has a different size; refusing to overwrite: ' + name)
        asset = call('POST', f'{upload}?name={name}', secret, data, TYPES[Path(name).suffix])
        print('uploaded', name, asset['size'], 'bytes')
    release = call('GET', release['url'], secret)
    verified = []
    assets = {asset['name']: asset for asset in release['assets']}
    if not set(files).issubset(assets):
        raise RuntimeError('Release is missing staged assets')
    for name, expected in files.items():
        asset = assets[name]
        # Draft assets require an authenticated API download until the release is published.
        url = asset['url'] if release['draft'] else asset['browser_download_url']
        headers = {'User-Agent': 'evergarden-release'}
        if release['draft']:
            headers.update({'Authorization': 'Bearer ' + secret, 'Accept': 'application/octet-stream'})
        with urllib.request.urlopen(urllib.request.Request(url, headers=headers), timeout=300) as r:
            downloaded = r.read()
        ok = hashlib.sha256(downloaded).digest() == hashlib.sha256(expected).digest()
        print('OK  ' if ok else 'BAD ', asset['name'], asset['browser_download_url'])
        if not ok:
            raise RuntimeError('Downloaded asset differs: ' + asset['name'])
        verified.append({'name': name, 'size': len(expected), 'sha256': hashlib.sha256(expected).hexdigest()})
    if release['draft']:
        release = call('PATCH', release['url'], secret, json.dumps({'draft': False, 'make_latest': 'true', 'body': body}).encode('utf8'))
        print('published release', release['html_url'])
    (stage / 'publish-receipt.json').write_text(json.dumps({'url': release['html_url'], 'tag': tag,
        'draft': release['draft'], 'versions': {'evergarden': garden, 'advance-magic': magic},
        'verified_assets': verified}, indent=2) + '\n', encoding='utf8')
    print(release['html_url'])


if __name__ == '__main__':
    main()
