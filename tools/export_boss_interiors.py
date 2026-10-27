"""Export the furnished Java blueprints for the interior cutaway renderer."""
import argparse
import os
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]
p = argparse.ArgumentParser(description=__doc__)
p.add_argument('--build-directory', type=Path, required=True)
p.add_argument('--output-dir', type=Path, required=True)
args = p.parse_args()
build = args.build_directory.resolve()
classes = build / 'interior-export-classes'
classes.mkdir(exist_ok=True)
cp = os.pathsep.join([str(build / 'evergarden.jar'), (build / 'probe-classpath.txt').read_text(encoding='utf8')])
subprocess.run(['javac', '--release', '25', '-proc:none', '-encoding', 'UTF-8', '-cp', cp,
                '-d', str(classes), str(ROOT / 'tools/ExportBossInteriors.java')], check=True)
subprocess.run(['java', '-cp', os.pathsep.join([str(classes), cp]),
                'ExportBossInteriors', str(args.output_dir.resolve())], check=True)
