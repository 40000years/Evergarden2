"""Re-export river rigs as native Bedrock entities inside evergarden/dist/evergarden-bedrock.mcpack.

Patches the pack in place, like update_evergarden_bosses.py: unrelated entries stay
byte-identical. Each named model is exported from its rivers/ blueprint at the scale
RiverVisual renders it (arkhaia_river_assets.bedrock_scale). The content version only
moves forward; pass --version to skip numbers other release pipelines may already use.

    python tools/update_river_bedrock.py arkhaia_banshee_wail arkhaia_malefeasance_phase2 --version 3.49.0
"""
from pathlib import Path
import argparse
import hashlib
import json
import re
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'evergarden/tools'))
from arkhaia_bedrock_assets import export_model
from arkhaia_river_assets import bedrock_scale


def json_bytes(value):
    return (json.dumps(value, ensure_ascii=False, separators=(',', ':')) + '\n').encode()


def write(path, files):
    temporary = path.with_name(path.name + '.next')
    with zipfile.ZipFile(temporary, 'w', zipfile.ZIP_DEFLATED, compresslevel=9) as z:
        for entry, data in sorted(files.items()):
            info = zipfile.ZipInfo(entry, (2026, 10, 22, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            z.writestr(info, data)
    temporary.replace(path)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('models', nargs='+', help='River model ids, e.g. arkhaia_banshee_wail')
    parser.add_argument('--version', help='Bedrock content version to publish (must exceed the current one)')
    args = parser.parse_args()
    dist = ROOT / 'evergarden/dist'
    pack = dist / 'evergarden-bedrock.mcpack'
    with zipfile.ZipFile(pack) as z:
        bedrock = {n: z.read(n) for n in z.namelist() if not n.endswith('/')}
    changed = []
    for model_id in args.models:
        source = ROOT / f'evergarden/src/main/resources/modelengine/rivers/{model_id}.bbmodel'
        native = export_model(json.loads(source.read_text(encoding='utf8')), model_id, bedrock_scale(model_id))
        if any(bedrock.get(path) != data for path, data in native.items()):
            changed.append(model_id)
            bedrock.update(native)
    if not changed:
        print('River entities already current, pack untouched')
        return
    manifest = json.loads(bedrock['manifest.json'])
    current = manifest['header']['version']
    version = [int(v) for v in args.version.split('.')] if args.version else [current[0], current[1] + 1, 0]
    # Clients cache by UUID + version: new content always needs a higher number.
    assert len(version) == 3 and version > current, f'version {version} must exceed {current}'
    manifest['header']['version'] = version
    for module in manifest['modules']:
        module['version'] = version
    bedrock['manifest.json'] = json_bytes(manifest)
    write(pack, bedrock)
    hashes = json.loads((dist / 'pack-hashes.json').read_text())
    hashes[pack.name] = hashlib.sha1(pack.read_bytes()).hexdigest()
    (dist / 'pack-hashes.json').write_text(json.dumps(hashes, indent=2) + '\n')
    # Only the version string changes; the hand-formatted file keeps its layout and line endings.
    design_path = ROOT / 'evergarden/src/main/resources/judge-design.json'
    design = re.sub(r'("bedrock-version":\s*)"[\d.]+"', lambda m: m.group(1) + '"' + '.'.join(map(str, version)) + '"',
                    design_path.read_bytes().decode('utf8'), count=1)
    design_path.write_bytes(design.encode('utf8'))
    print(f"Re-exported {', '.join(changed)}; Bedrock content {'.'.join(map(str, current))} -> {'.'.join(map(str, version))}")


if __name__ == '__main__':
    main()
