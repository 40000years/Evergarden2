"""Compile only the shared delivery package and run ResourcePackDeliveryChecks.

run_checks.py builds both whole plugins from source; this needs just the pinned Paper classpath, so it
also works while other plugin sources are mid-change. The package is compiled twice, into two class
directories, for the isolated-classloader checks.

    python tests/resource-packs/run_delivery_checks.py
"""
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'tools'))
import build_plugin_jars as build  # noqa: E402

OUT = ROOT / 'output/pack-delivery-checks'
build.OUT = OUT
OUT.mkdir(parents=True, exist_ok=True)
shared = sorted((ROOT / 'advance-magic/src/main/java/com/example/advancemagic/pack/delivery').glob('*.java'))
classpath = build.pinned_classpath(build.ADVANCE_ARGS)
first, second, checks = OUT / 'delivery-a', OUT / 'delivery-b', OUT / 'checks'
build.compile_sources('delivery-a', shared, classpath, first)
build.compile_sources('delivery-b', shared, classpath, second)
build.compile_sources('delivery-checks', [Path(__file__).with_name('ResourcePackDeliveryChecks.java')], [str(first), *classpath], checks)
# The pinned classpath is longer than a Windows command line, so it goes through a java @argfile.
argfile = OUT / 'run-java.args'
argfile.write_text('-cp\n"' + ';'.join([str(checks), str(first), *classpath]).replace('\\', '/') + '"\n', encoding='utf8')
run = subprocess.run(['java', '@' + str(argfile), 'ResourcePackDeliveryChecks', str(first), str(second)],
                     cwd=ROOT, text=True, capture_output=True)
output = (run.stdout + run.stderr).strip()
print(output[-6000:])
# Kept so a later review can confirm what passed, with the exact sources it ran against.
(OUT / 'result.txt').write_text('exit ' + str(run.returncode) + '\n' + output + '\n', encoding='utf8')
sys.exit(run.returncode)
