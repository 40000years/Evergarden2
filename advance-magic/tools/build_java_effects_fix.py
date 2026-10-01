"""Build an additive client pack that repairs the currently published Java packs.

Atlas sources merge across packs, so an older server pack cannot hide this fix.
No plugin, server configuration, textures, or gameplay settings are replaced.
"""
import json
import sys
import tempfile
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT.parent / 'tools'))
import magic_java_atlas


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + '\n', encoding='utf8')


def main():
    output = ROOT / 'dist/advance-magic-java-effects-fix.zip'
    with tempfile.TemporaryDirectory() as temp:
        root = Path(temp)
        write_json(root / 'pack.mcmeta', {'pack': {
            'description': 'Advance Magic | Java spell texture atlas fix',
            'min_format': [75, 0], 'max_format': [97, 1]}})
        magic_java_atlas.register(root, write_json)
        with zipfile.ZipFile(output, 'w', zipfile.ZIP_DEFLATED) as archive:
            for path in sorted(root.rglob('*')):
                if path.is_file():
                    info = zipfile.ZipInfo(path.relative_to(root).as_posix(), (2026, 10, 1, 0, 0, 0))
                    info.compress_type = zipfile.ZIP_DEFLATED
                    archive.writestr(info, path.read_bytes())
    print(output)


if __name__ == '__main__':
    main()
