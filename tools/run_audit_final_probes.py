"""Rerun final JARs against already-isolated Paper fixtures; never touch live server."""
import hashlib
import os
import shutil
import subprocess
import time
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'output/audit-guide-release'
CASES=[('pet',ROOT/'output/pet-audit-paper-20261029-final'),
       ('without-floodgate',ROOT/'output/optional-floodgate-proof/paper')]

def sha(p): return hashlib.sha256(p.read_bytes()).hexdigest()

for label,server in CASES:
    assert server.resolve().is_relative_to((ROOT/'output').resolve())
    assert (server/'paper.jar').is_file()
    plugins=server/'plugins'
    if label=='without-floodgate':
        assert not any('floodgate' in p.name.lower() or 'geyser' in p.name.lower() for p in plugins.glob('*.jar'))
    else:
        assert (plugins/'model-engine-trial-probe.jar').exists()
    for module in ('evergarden','advance-magic'):
        src=OUT/(module+'.jar');dst=plugins/(module+'.jar')
        shutil.copy2(src,dst)
        assert sha(src)==sha(dst)
    result=server/'model-engine-result.txt'
    if label=='pet' and result.exists(): result.rename(server/'model-engine-result-before-last-text-fix.txt')
    log=server/'console-final-audit.log'
    print('Starting',label,'Paper fixture',flush=True)
    with log.open('w',encoding='utf8') as output:
        heap='-Xmx3072M' if label=='pet' else '-Xmx1400M'
        command=['java','-Xms256M',heap,'-Dterminal.jline=false','-Dterminal.ansi=false','-jar','paper.jar','--nogui']
        proc=subprocess.Popen(command,cwd=server,stdin=subprocess.PIPE,stdout=output,stderr=subprocess.STDOUT,
                              creationflags=subprocess.CREATE_NO_WINDOW if os.name=='nt' else 0)
        started=time.monotonic()
        while proc.poll() is None and time.monotonic()-started < 210:
            time.sleep(0.5)
            if label=='without-floodgate' and log.exists() and 'Done (' in log.read_text(encoding='utf8',errors='replace'):
                proc.stdin.write(b'stop\n');proc.stdin.flush();break
        try: code=proc.wait(timeout=50)
        except subprocess.TimeoutExpired:
            proc.kill();proc.wait();raise RuntimeError(label+' did not stop')
    text=log.read_text(encoding='utf8',errors='replace')
    if code: raise RuntimeError(label+' exited '+str(code)+': '+text[-2000:])
    if label=='pet':
        if not result.exists() or not result.read_text(encoding='utf8').startswith('PASS'):
            raise RuntimeError('pet probe failed: '+(result.read_text(encoding='utf8') if result.exists() else text[-2500:]))
        print(result.read_text(encoding='utf8').splitlines()[0],flush=True)
    else:
        prior_stop=text.split('Stopping server')[0]
        if 'Done (' not in prior_stop or '[Evergarden] Enabling Evergarden' not in prior_stop or '[Evergarden] Disabling Evergarden' in prior_stop:
            raise RuntimeError('standalone Evergarden did not remain enabled: '+prior_stop[-4000:])
        print('PASS: Paper startup without Floodgate/Geyser; Evergarden stays enabled',flush=True)
    (OUT/(label+'-result.txt')).write_text('PASS\never-garden='+sha(OUT/'evergarden.jar')+'\nmagic='+sha(OUT/'advance-magic.jar')+'\n'+str(log)+'\n',encoding='utf8')
