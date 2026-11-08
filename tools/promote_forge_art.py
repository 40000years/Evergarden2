"""Promote the resource-only Forge 0.5.1 after matching Paper/core/Geyser validation."""
from pathlib import Path
import hashlib,io,json,os,shutil,subprocess,sys,zipfile

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'output/forge-art-051'
VERSION='0.5.1-forge-art'

def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()

def main():
    candidate=ROOT/'output/forge-build/evergarden-forge.jar';digest=sha(candidate)
    baseline=json.loads((OUT/'baseline.json').read_text())
    assert all(sha(ROOT/p)==h for p,h in baseline.items()),'An existing release changed during artwork work'
    folder=ROOT/'output/forge-probe/paper-art-051-final'
    receipt=json.loads((folder/'forge-test-receipt.json').read_text())
    assert receipt['result'].startswith('PASS') and receipt['audit'] and receipt['with_core'] and receipt['with_geyser']
    assert receipt['candidate_sha256']==digest
    assert all(sha(ROOT/p)==h for p,h in receipt['sources'].items()),'Tested source changed'
    subprocess.run([sys.executable,str(ROOT/'tests/forge/check_resources.py'),str(candidate)],check=True)
    art=json.loads((ROOT/'evergarden-forge/art/generated-v2/manifest.json').read_text())
    assert art['version']==[0,5,1] and art['texture_size']==64 and len(art['items'])==46
    with zipfile.ZipFile(candidate) as new,zipfile.ZipFile(ROOT/'dist/evergarden-forge-0.5.0.jar') as old:
        assert new.testzip() is None and set(new.namelist())==set(old.namelist())
        assert ("version: '"+VERSION+"'").encode() in new.read('plugin.yml')
        unchanged=[p for p in new.namelist() if p!='plugin.yml' and not p.startswith('resource-packs/')]
        assert all(new.read(p)==old.read(p) for p in unchanged),'Unexpected gameplay/config change'
        assert new.read('resource-packs/geyser-mappings.json')==old.read('resource-packs/geyser-mappings.json')
        with zipfile.ZipFile(io.BytesIO(new.read('resource-packs/forge-java.zip'))) as java:
            for row in art['items']:
                assert sha(ROOT/row['source'])==row['source_sha256']
                key=row['model'].split(':',1)[1]
                png=java.read('assets/evergarden_forge/textures/item/'+key+'.png')
                assert hashlib.sha256(png).hexdigest()==row['texture_sha256']
        for pack in ('forge-java.zip','forge-bedrock.mcpack'):
            with zipfile.ZipFile(io.BytesIO(new.read('resource-packs/'+pack))) as a,zipfile.ZipFile(io.BytesIO(old.read('resource-packs/'+pack))) as b:
                pngs=[p for p in a.namelist() if p.endswith('.png')]
                assert len(pngs)==46 and all(a.read(p)!=b.read(p) for p in pngs),'An old icon remains'
        hashes=json.loads(new.read('resource-packs/pack-hashes.json'))
        external=ROOT/'dist/forge-resource-packs-0.5.1';external.mkdir(exist_ok=True)
        for name in ('forge-java.zip','forge-bedrock.mcpack','geyser-mappings.json','pack-hashes.json'):
            (external/name).write_bytes(new.read('resource-packs/'+name))
    versioned=ROOT/'dist/evergarden-forge-0.5.1.jar'
    assert not versioned.exists() or sha(versioned)==digest,'Refusing to replace a different 0.5.1 release'
    shutil.copy2(candidate,versioned)
    target=ROOT/'dist/evergarden-forge.jar';staged=target.with_suffix('.jar.staged')
    shutil.copy2(candidate,staged);os.replace(staged,target)
    assert sha(target)==sha(versioned)==digest
    assert all(sha(ROOT/p)==h for p,h in baseline.items() if p!='dist/evergarden-forge.jar')
    shutil.copy2(folder/'forge-result.txt',OUT/'integration-result.txt')
    shutil.copy2(folder/'forge-test-receipt.json',OUT/'integration-receipt.json')
    report=dict(result='PASS',version=VERSION,sha256=digest,bytes=target.stat().st_size,
        integration=receipt['result'],icons=46,texture_size=64,pack_sha1=hashes,
        all_gameplay_classes_and_config_identical_to_050=True,model_ids_unchanged=True,
        all_46_icons_replaced_on_both_editions=True,older_releases_unchanged=True,
        public_github_packs_changed=False,source_hashes=receipt['sources'])
    (OUT/'delivery-report.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf8')
    print('Promoted Forge '+VERSION+' '+digest)

if __name__=='__main__':main()
