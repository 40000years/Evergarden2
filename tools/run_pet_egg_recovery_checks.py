"""Run the egg recovery regressions on the existing isolated Paper pet fixture."""
import hashlib
import json
import os
import shutil
import subprocess
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'output/pet-egg-recovery-release'
SERVER = ROOT / 'output/pet-audit-paper-20261029-final'


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    assert SERVER.resolve().is_relative_to((ROOT / 'output').resolve())
    assert (SERVER / 'paper.jar').is_file()
    report = json.loads((OUT / 'build-report.json').read_text(encoding='utf8'))
    candidate = OUT / 'evergarden.jar'
    if sha(candidate) != report['candidate_sha256']:
        raise RuntimeError('Candidate changed after build')
    if sha(ROOT / 'dist/advance-magic.jar') != report['magic_sha256']:
        raise RuntimeError('Advance Magic changed after build')
    plugins = SERVER / 'plugins'
    shutil.copy2(candidate, plugins / 'evergarden.jar')
    shutil.copy2(ROOT / 'dist/advance-magic.jar', plugins / 'advance-magic.jar')
    classes = OUT / ('probe-classes-' + str(time.time_ns()))
    classes.mkdir()
    classpath = os.pathsep.join(str(p) for folder in (SERVER / 'libraries', SERVER / 'versions', plugins)
                                for p in folder.rglob('*.jar') if folder != plugins or p.parent == plugins)
    source = ROOT / 'tests/model-engine/PetSystemNativeProbe.java'
    source_hash = sha(source)
    options = ['--release', '25', '-proc:none', '-encoding', 'UTF-8', '-cp', classpath,
               '-d', str(classes), str(source)]
    argfile = OUT / 'probe-javac.args'
    argfile.write_text('\n'.join('"' + value.replace('\\', '/') + '"' for value in options), encoding='utf8')
    subprocess.run(['java', str(ROOT / 'tools/CompileJava.java'), str(argfile)], check=True)
    if sha(source) != source_hash:
        raise RuntimeError('Probe changed during compilation')
    (classes / 'plugin.yml').write_text("name: ModelEngineTrialProbe\nversion: 'egg-recovery'\nmain: PetSystemNativeProbe\napi-version: '26.2'\nsoftdepend: [ModelEngine, Evergarden, advance-magic, LevelledMobs, Geyser-Spigot, floodgate]\n", encoding='utf8')
    subprocess.run(['jar', '--create', '--file', str(plugins / 'model-engine-trial-probe.jar'), '-C', str(classes), '.'], check=True)
    result = SERVER / 'model-engine-result.txt'
    if result.exists():
        result.rename(SERVER / ('model-engine-result-before-egg-' + str(time.time_ns()) + '.txt'))
    log = OUT / ('paper-console-' + str(time.time_ns()) + '.log')
    print('Starting isolated Paper egg recovery regression', flush=True)
    with log.open('w', encoding='utf8') as console:
        proc = subprocess.Popen(['java', '-Xms256M', '-Xmx3072M', '-Dterminal.jline=false',
                                 '-Dterminal.ansi=false', '-jar', 'paper.jar', '--nogui', '--noconsole'],
                                cwd=SERVER, stdout=console, stderr=subprocess.STDOUT,
                                creationflags=subprocess.CREATE_NO_WINDOW if os.name == 'nt' else 0)
        try:
            code = proc.wait(timeout=240)
        except subprocess.TimeoutExpired:
            proc.kill()
            proc.wait()
            raise RuntimeError('Isolated fixture timed out; inspect ' + str(log))
    if code or not result.exists() or not result.read_text(encoding='utf8').startswith('PASS'):
        raise RuntimeError('Paper regression failed: ' + (result.read_text(encoding='utf8') if result.exists() else str(log)))
    text = result.read_text(encoding='utf8')
    print(text.splitlines()[0], flush=True)
    receipt = {'result': text.splitlines()[0], 'evergarden_sha256': sha(candidate),
               'magic_sha256': report['magic_sha256'], 'probe_sha256': source_hash,
               'probe_jar_sha256': sha(plugins / 'model-engine-trial-probe.jar'), 'log': str(log),
               'report': str(result)}
    (OUT / 'test-receipt.json').write_text(json.dumps(receipt, indent=2) + '\n', encoding='utf8')
    (OUT / 'pet-result.txt').write_text(text, encoding='utf8')
    print('Receipt:', OUT / 'test-receipt.json', flush=True)


if __name__ == '__main__':
    main()
