"""Pack the local-only build and test dependencies into one zip for moving to another machine.

GitHub holds the project itself. The compile classpath and the Paper test servers also need
third-party files that must not go into the public repository: the Paper server and its libraries,
the licensed ModelEngine jar, Geyser/Floodgate, and the jars the pinned javac argument files list.
This collects exactly those files, keeping their paths relative to the repository root, so the zip
unpacks over a fresh clone. See docs/new-machine-setup.md.

    python tools/make_buildkit.py [--out output/buildkit-YYYYMMDD.zip]
"""
import argparse
import datetime
import re
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CLASSPATHS = [
    'output/banshee-hunt-build-v1-20261015/evergarden-javac.args',     # tools/build_plugin_jars.py (Evergarden)
    'output/boss-rewards-build-v3-20261021/advance-magic-javac.args',  # tools/build_plugin_jars.py (Advance Magic)
    'output/cocytus-balance-20261025/build/classpath.txt',             # tests/arkhaia/run.py --classpath-file
]
TEMPLATE = 'output/arkhaia-aggro-feedback-me-v4-20261014'               # tests/arkhaia/run.py --template
NATIVE = 'output/evergarden-bosses-me-v6-20261022/plugins'              # ModelEngine/Geyser/Floodgate for native probes


def classpath_entries(path):
    text = path.read_text(encoding='utf8')
    if '"-cp"' in text:
        lines = text.splitlines()
        text = lines[lines.index('"-cp"') + 1].strip('"')
    for entry in re.split(r'[;\n]', text):
        entry = entry.strip().strip('"')
        if entry:
            yield Path(entry)


def collect():
    files = set()
    for name in CLASSPATHS:
        path = ROOT / name
        files.add(path)
        for entry in classpath_entries(path):
            entry = entry.resolve()
            # dist/ jars come from git; only local-only output/ files belong in the kit.
            if entry.is_file() and entry.is_relative_to((ROOT / 'output').resolve()):
                files.add(entry)
    template = ROOT / TEMPLATE
    files.add(template / 'paper.jar')
    for folder in ('libraries', 'versions', 'cache'):
        files.update(p for p in (template / folder).rglob('*') if p.is_file())
    for name in ('ModelEngine.jar', 'Geyser-Spigot.jar', 'floodgate-spigot.jar'):
        files.add(ROOT / NATIVE / name)
    return sorted(p.resolve() for p in files)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--out', type=Path, default=ROOT / f"output/buildkit-{datetime.date.today():%Y%m%d}.zip")
    args = parser.parse_args()
    files = collect()
    missing = [p for p in files if not p.is_file()]
    if missing:
        raise SystemExit('Missing: ' + ', '.join(map(str, missing)))
    args.out.parent.mkdir(parents=True, exist_ok=True)
    total = 0
    with zipfile.ZipFile(args.out, 'w', zipfile.ZIP_DEFLATED) as kit:
        listing = []
        for path in files:
            rel = path.relative_to(ROOT.resolve()).as_posix()
            # Jars are already compressed; storing them keeps packing fast.
            kit.write(path, rel, compress_type=zipfile.ZIP_STORED if rel.endswith('.jar') else zipfile.ZIP_DEFLATED)
            total += path.stat().st_size
            listing.append(f'{path.stat().st_size}\t{rel}')
        kit.writestr('BUILDKIT-CONTENTS.txt', '\n'.join(listing) + '\n')
    print(f'{args.out}: {len(files)} files, {total / 2**20:.0f} MiB')


if __name__ == '__main__':
    main()
