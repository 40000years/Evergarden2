"""Promote Forge 0.5.0 only with matching standalone and core/Geyser audit receipts."""
from pathlib import Path
import hashlib,json,os,shutil,subprocess,sys,zipfile

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'output/forge-audit'
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def main():
    candidate=ROOT/'output/forge-build/evergarden-forge.jar';digest=sha(candidate)
    reports=[]
    for name,core in [('audit-050-final',False),('audit-050-core-final',True)]:
        folder=ROOT/'output/forge-probe'/('paper-'+name)
        receipt=json.loads((folder/'forge-test-receipt.json').read_text())
        assert receipt['result'].startswith('PASS') and receipt['audit'] and receipt['with_core']==core
        if core:assert receipt['with_geyser']
        assert receipt['candidate_sha256']==digest
        assert all(sha(ROOT/path)==value for path,value in receipt['sources'].items()),'Validated source changed'
        reports.append(receipt)
        shutil.copy2(folder/'forge-result.txt',OUT/(name+'-result.txt'))
        shutil.copy2(folder/'forge-test-receipt.json',OUT/(name+'-receipt.json'))
    before=json.loads((OUT/'baseline.json').read_text())
    assert all(sha(ROOT/path)==value for path,value in before.items()),'Existing delivered JAR changed'
    subprocess.run([sys.executable,str(ROOT/'tests/forge/check_resources.py'),str(candidate)],check=True)
    with zipfile.ZipFile(candidate) as z:
        assert b"version: '0.5.0-forge-audit-resources'" in z.read('plugin.yml')
        assert z.testzip() is None
    versioned=ROOT/'dist/evergarden-forge-0.5.0.jar'
    assert not versioned.exists() or sha(versioned)==digest
    shutil.copy2(candidate,versioned)
    target=ROOT/'dist/evergarden-forge.jar';staged=target.with_suffix('.jar.staged')
    shutil.copy2(candidate,staged);os.replace(staged,target)
    assert sha(target)==sha(versioned)==digest
    assert all(sha(ROOT/path)==value for path,value in before.items() if Path(path).name!='evergarden-forge.jar')
    report=dict(result='PASS',version='0.5.0-forge-audit-resources',sha256=digest,bytes=target.stat().st_size,
        standalone=reports[0]['result'],core_geyser=reports[1]['result'],matching_java_bedrock_icons=46,
        evergarden_and_magic_unchanged=True,old_forge_releases_unchanged=True,public_packs_changed=False,
        source_hashes=reports[0]['sources'])
    (OUT/'delivery-report.json').write_text(json.dumps(report,indent=2)+'\n')
    print('Promoted Forge 0.5.0 '+digest)
if __name__=='__main__':main()
