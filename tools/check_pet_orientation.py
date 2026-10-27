"""Check real native pet display transforms and attention without starting a server.

Requires the locally pinned Java/Paper dependencies and a built Evergarden JAR.
An optional previous JAR must fail the geometric assertion, proving the regression.
"""
import argparse
import json
import os
from pathlib import Path
import re
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]


def argfile(path, arguments):
    path.write_text('\n'.join('"' + str(a).replace('\\', '/') + '"' for a in arguments), encoding='utf8')
    return '@' + str(path)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--garden-jar', type=Path, default=ROOT / 'output/pet-attention-build-20261022/evergarden.jar')
    parser.add_argument('--classpath', type=Path, default=ROOT / 'output/pet-attention-build-20261022/classpath.txt')
    parser.add_argument('--output', type=Path, default=ROOT / 'output/pet-attention-checks-20261022')
    parser.add_argument('--baseline-jar', type=Path)
    parser.add_argument('--orientation-only', action='store_true', help='Skip the new attention checks when testing an older renderer')
    args = parser.parse_args()
    build = args.output.resolve()
    build.mkdir(parents=True, exist_ok=True)
    classes = build / 'classes'
    classes.mkdir(exist_ok=True)
    dependencies = args.classpath.read_text(encoding='utf8').strip().split(os.pathsep)
    assert args.garden_jar.is_file() and all(Path(p).is_file() for p in dependencies), 'Local JARs must exist'
    with zipfile.ZipFile(args.garden_jar) as bundle:
        for resource in ('native-pets.json', 'native-pets.zip'):
            assert bundle.read(resource) == (ROOT / 'evergarden/src/main/resources' / resource).read_bytes(), 'Built native assets are stale: ' + resource
    cp = os.pathsep.join([str(args.garden_jar.resolve()), *dependencies])
    sources = ['NativePetOrientationChecks.java', 'NativePetGazeChecks.java', 'PetAttentionChecks.java']
    options = ['--release', '25', '-proc:none', '-encoding', 'UTF-8', '-cp', cp, '-d', str(classes),
               *(str(ROOT / 'tests/model-engine' / name) for name in sources)]
    compiler_args = build / 'javac.args'
    argfile(compiler_args, options)
    subprocess.run(['java', str(ROOT / 'tools/CompileJava.java'), str(compiler_args)], check=True)

    def run(jar, label, test='NativePetOrientationChecks'):
        runtime_cp = os.pathsep.join([str(jar.resolve()), str(classes), *dependencies])
        test_args = [str(build / (label + '.json'))]
        if test != 'PetAttentionChecks':
            test_args.insert(0, str(ROOT))
        command = argfile(build / (label + '-java.args'), [
            '--sun-misc-unsafe-memory-access=allow', '-cp', runtime_cp, test, *test_args])
        result = subprocess.run(['java', command], capture_output=True, text=True, encoding='utf8', errors='replace')
        (build / (label + '.log')).write_text(result.stdout + result.stderr, encoding='utf8')
        return result

    current = run(args.garden_jar, 'current')
    print(current.stdout, end='')
    if current.returncode:
        raise RuntimeError(current.stderr)

    # Passenger displays keep their spawn entity rotation: the shell's heading is
    # already present in every emitted transform, so spawn must have neutral angles.
    source = (ROOT / 'evergarden/src/main/java/com/example/voidscape/pet/NativePetRenderer.java').read_text(encoding='utf8')
    constructor = source[source.index('private Rig('):source.index('public int partCount(')]
    spawn_at = re.search(r'\.spawn\((\w+),\s*ItemDisplay\.class', constructor)
    assert spawn_at, 'display spawn must use an explicit neutral Location'
    name = re.escape(spawn_at.group(1))
    before_spawn = constructor[:spawn_at.start()]
    assert re.search(r'Location\s+' + name + r'\s*=\s*entity\.getLocation\(\)', before_spawn), 'spawn location must come from the shell'
    for axis in ('Yaw', 'Pitch'):
        assert re.search(name + r'\.set' + axis + r'\(0\)', before_spawn), 'display spawn must reset ' + axis
    report_path = build / 'current.json'
    report = json.loads(report_path.read_text(encoding='utf8'))
    report['spawn_yaw_pitch_source_contract'] = True
    report['packaged_native_assets_match_source'] = True
    if not args.orientation_only:
        for name, test in [('gaze', 'NativePetGazeChecks'), ('attention-math', 'PetAttentionChecks')]:
            result = run(args.garden_jar, name, test)
            print(result.stdout, end='')
            if result.returncode:
                raise RuntimeError(result.stderr)
            report[name] = json.loads((build / (name + '.json')).read_text(encoding='utf8'))
    if args.baseline_jar:
        baseline = run(args.baseline_jar, 'baseline')
        if baseline.returncode:
            assert 'cube corner orientation' in baseline.stderr, 'Previous JAR must fail actual emitted cube geometry'
            report['baseline_geometry_regression_detected'] = True
            print('PASS previous JAR fails actual emitted cube geometry')
        else:
            assert not args.orientation_only, 'Previous JAR passes geometry; attention checks are required to prove this regression'
            baseline_gaze = run(args.baseline_jar, 'baseline-gaze', 'NativePetGazeChecks')
            assert baseline_gaze.returncode and any(message in baseline_gaze.stderr for message in (
                'Rig.emit lacks head attention', 'head looks away from requested world direction',
                'emitted gaze geometry', 'settled pose ignores new gaze')), 'Previous JAR must fail actual emitted head attention'
            report['baseline_gaze_regression_detected'] = True
            print('PASS previous JAR fails actual emitted head attention')
        report['baseline_jar'] = str(args.baseline_jar)
    report_path.write_text(json.dumps(report, indent=2) + '\n', encoding='utf8')
    print('Report:', report_path)


if __name__ == '__main__':
    main()
